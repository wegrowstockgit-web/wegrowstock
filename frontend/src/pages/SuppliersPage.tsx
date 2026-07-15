import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Truck, Plus } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Supplier } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { ListPageState, useListQuery } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { useSessionStore } from '@/stores/session';

function AddSupplierModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [paymentTerms, setPaymentTerms] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/suppliers', {
        name,
        contact: email ? { email } : {},
        paymentTerms: paymentTerms || undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      setName('');
      setEmail('');
      setPaymentTerms('');
      onClose();
    },
    onError: () => setError('Could not create supplier. Check the fields and try again.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add supplier" description="Vendor account for purchase orders">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Input label="Contact email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Optional" />
        <Input label="Payment terms" value={paymentTerms} onChange={(e) => setPaymentTerms(e.target.value)} placeholder="e.g. NET30" />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
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

  const { data, isLoading, isError, error, refetch } =
    useListQuery<Supplier>(['suppliers'], '/api/v1/suppliers');

  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Suppliers</h1>
          <p className="mt-1 text-sm text-text-muted">Vendor accounts</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            Add supplier
          </Button>
        )}
      </div>

      <DataListToolbar />

      <ListPageState
        isLoading={isLoading}
        isError={isError}
        error={error}
        data={data}
        refetch={refetch}
        emptyIcon={Truck}
        emptyTitle="No suppliers yet"
        emptyDescription={
          canCreate
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
        {(items) => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Contact email</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((s) => (
                <TableRow key={s.id}>
                  <TableCell>{s.name}</TableCell>
                  <TableCell>{s.contactEmail ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </ListPageState>

      <AddSupplierModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  );
}
