import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Truck, Plus } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Supplier } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { Pagination } from '@/components/ui/Pagination';
import { TableDensityScope } from '@/hooks/useDensity';
import { useClientSort } from '@/hooks/useClientSort';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { listSuppliers } from '@/api/operational';

function SuppliersTable({ items }: { items: Supplier[] }) {
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      name: (s) => s.name,
      email: (s) => s.contactEmail ?? '',
      terms: (s) => s.paymentTerms ?? '',
    },
    { key: 'name', dir: 'asc' },
  );
  return (
    <div className="min-w-0 overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead sortable sortKey="name" sort={sort} onSort={toggle}>
              Name
            </TableHead>
            <TableHead sortable sortKey="email" sort={sort} onSort={toggle}>
              Contact email
            </TableHead>
            <TableHead sortable sortKey="terms" sort={sort} onSort={toggle}>
              Terms
            </TableHead>
            <TableHead>Lead time</TableHead>
            <TableHead>Rating</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((s) => (
            <TableRow key={s.id} data-testid={`supplier-row-${s.id}`}>
              <TableCell>{s.name}</TableCell>
              <TableCell>{s.contactEmail ?? '—'}</TableCell>
              <TableCell>{s.paymentTerms ?? '—'}</TableCell>
              <TableCell>
                {s.defaultLeadTimeDays != null ? `${s.defaultLeadTimeDays}d` : '—'}
              </TableCell>
              <TableCell>{s.supplierRating != null ? String(s.supplierRating) : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function AddSupplierModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [paymentTerms, setPaymentTerms] = useState('NET30');
  const [taxId, setTaxId] = useState('');
  const [businessReg, setBusinessReg] = useState('');
  const [iban, setIban] = useState('');
  const [routing, setRouting] = useState('');
  const [leadTime, setLeadTime] = useState('');
  const [moq, setMoq] = useState('');
  const [rating, setRating] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/suppliers', {
        name,
        contact: email ? { email } : {},
        paymentTerms,
        taxId: taxId || undefined,
        businessRegistration: businessReg || taxId || undefined,
        bankAccountIban: iban || undefined,
        routingNumber: routing || undefined,
        defaultLeadTimeDays: leadTime ? Number(leadTime) : undefined,
        minimumOrderQuantityValue: moq ? Number(moq) : undefined,
        supplierRating: rating ? Number(rating) : undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      setName('');
      setEmail('');
      setPaymentTerms('NET30');
      setTaxId('');
      setBusinessReg('');
      setIban('');
      setRouting('');
      setLeadTime('');
      setMoq('');
      setRating('');
      onClose();
    },
    onError: () => setError('Could not create supplier. Check the fields and try again.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add supplier" description="Vendor master — terms, lead time, and remittance">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
        data-testid="add-supplier-form"
      >
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          <Input
            label="Contact email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Select label="Payment terms" value={paymentTerms} onChange={(e) => setPaymentTerms(e.target.value)}>
            <option value="NET30">Net 30</option>
            <option value="NET60">Net 60</option>
            <option value="DUE_ON_RECEIPT">Due on receipt</option>
          </Select>
          <Input label="Tax ID" value={taxId} onChange={(e) => setTaxId(e.target.value)} />
          <Input
            label="Business registration"
            value={businessReg}
            onChange={(e) => setBusinessReg(e.target.value)}
          />
          <Input
            label="Bank IBAN (masked on save)"
            value={iban}
            onChange={(e) => setIban(e.target.value)}
            autoComplete="off"
          />
          <Input
            label="Routing number (masked on save)"
            value={routing}
            onChange={(e) => setRouting(e.target.value)}
            autoComplete="off"
          />
          <Input
            label="Default lead time (days)"
            type="number"
            min={0}
            value={leadTime}
            onChange={(e) => setLeadTime(e.target.value)}
          />
          <Input
            label="Minimum order value"
            type="number"
            min={0}
            value={moq}
            onChange={(e) => setMoq(e.target.value)}
          />
          <Input
            label="Supplier rating (0–5)"
            type="number"
            min={0}
            max={5}
            step="0.1"
            value={rating}
            onChange={(e) => setRating(e.target.value)}
          />
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} data-testid="add-supplier-submit">
            Add supplier
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function SuppliersPage() {
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const [modalOpen, setModalOpen] = useState(false);

  const table = useServerTableQuery<Supplier>({
    queryKey: 'suppliers',
    path: '/api/v1/suppliers',
    defaultSort: 'name,asc',
    fetcher: listSuppliers,
  });
  const { items, isLoading, isError, error, refetch, search } = table;

  return (
    <TableDensityScope gridId="suppliers">
    <div className="mx-auto min-h-0 w-full max-w-7xl overflow-y-auto overscroll-contain p-4 sm:p-6">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-text">Suppliers</h1>
          <p className="mt-1 text-sm text-text-muted">Vendor accounts and procurement terms</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            Add supplier
          </Button>
        )}
      </div>

      <DataListToolbar gridId="suppliers">
        <DebouncedSearchInput
          value={search}
          onDebouncedChange={table.setSearch}
          placeholder="Search suppliers…"
        />
      </DataListToolbar>

      <ListPageState
        isLoading={isLoading && items.length === 0}
        isError={isError}
        error={error}
        data={items}
        refetch={refetch}
        emptyIcon={Truck}
        emptyTitle={search ? 'No matching suppliers' : 'No suppliers yet'}
        emptyDescription={
          search
            ? 'Try a different name or payment terms.'
            : canCreate
            ? 'Add suppliers to create purchase orders.'
            : 'Suppliers will appear here once added by a manager.'
        }
        emptyAction={
          canCreate ? (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              Add supplier
            </Button>
          ) : undefined
        }
      >
        {(rows) => (
          <>
            <SuppliersTable items={rows} />
            <Pagination
              page={table.page}
              totalPages={table.totalPages}
              totalElements={table.totalElements}
              size={table.size}
              onPageChange={table.setPage}
              onSizeChange={table.setSize}
            />
          </>
        )}
      </ListPageState>

      <AddSupplierModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
    </TableDensityScope>
  );
}
