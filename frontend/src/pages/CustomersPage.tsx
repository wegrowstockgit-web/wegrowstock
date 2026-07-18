import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Customer } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
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
import { useClientSort } from '@/hooks/useClientSort';
import { useSessionStore } from '@/stores/session';
import { CustomerDetail } from '@/features/customers/CustomerDetail';

function CustomersTable({
  items,
  onPeek,
}: {
  items: Customer[];
  onPeek: (customer: Customer) => void;
}) {
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      name: (c) => c.name,
      email: (c) => c.email ?? '',
      status: (c) => c.customerStatus ?? '',
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
              Email
            </TableHead>
            <TableHead sortable sortKey="status" sort={sort} onSort={toggle}>
              Status
            </TableHead>
            <TableHead>Terms</TableHead>
            <TableHead>Credit</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((c) => (
            <TableRow
              key={c.id}
              className="cursor-pointer"
              onClick={() => onPeek(c)}
              data-testid={`customer-row-${c.id}`}
            >
              <TableCell>{c.name}</TableCell>
              <TableCell>{c.email ?? '—'}</TableCell>
              <TableCell>{c.customerStatus ?? 'ACTIVE'}</TableCell>
              <TableCell>{c.paymentTerms ?? '—'}</TableCell>
              <TableCell>{c.creditLimit != null ? String(c.creditLimit) : '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function AddCustomerModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [taxId, setTaxId] = useState('');
  const [paymentTerms, setPaymentTerms] = useState('NET30');
  const [creditLimit, setCreditLimit] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [status, setStatus] = useState('ACTIVE');
  const [billStreet, setBillStreet] = useState('');
  const [billCity, setBillCity] = useState('');
  const [billState, setBillState] = useState('');
  const [billPostal, setBillPostal] = useState('');
  const [billCountry, setBillCountry] = useState('US');
  const [shipSame, setShipSame] = useState(true);
  const [shipStreet, setShipStreet] = useState('');
  const [shipCity, setShipCity] = useState('');
  const [shipState, setShipState] = useState('');
  const [shipPostal, setShipPostal] = useState('');
  const [shipCountry, setShipCountry] = useState('US');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      const billingAddress = {
        street: billStreet || undefined,
        city: billCity || undefined,
        state: billState || undefined,
        postalCode: billPostal || undefined,
        country: billCountry || undefined,
      };
      const shippingAddress = shipSame
        ? billingAddress
        : {
            street: shipStreet || undefined,
            city: shipCity || undefined,
            state: shipState || undefined,
            postalCode: shipPostal || undefined,
            country: shipCountry || undefined,
          };
      await apiClient.post('/api/v1/customers', {
        name,
        email: email || undefined,
        taxId: taxId || undefined,
        ein: taxId || undefined,
        paymentTerms,
        creditLimit: creditLimit ? Number(creditLimit) : undefined,
        currencyPreference: currency,
        customerStatus: status,
        billingAddress,
        shippingAddress,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      setName('');
      setEmail('');
      setTaxId('');
      setPaymentTerms('NET30');
      setCreditLimit('');
      setCurrency('USD');
      setStatus('ACTIVE');
      setBillStreet('');
      setBillCity('');
      setBillState('');
      setBillPostal('');
      setBillCountry('US');
      setShipSame(true);
      onClose();
    },
    onError: () => setError('Could not create customer. Check the fields and try again.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add customer" description="ERP buyer master — tax, credit, and addresses">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
        data-testid="add-customer-form"
      >
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
          <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          <Input
            label="Tax ID / EIN"
            value={taxId}
            onChange={(e) => setTaxId(e.target.value)}
            placeholder="XX-XXXXXXX"
          />
          <Select label="Payment terms" value={paymentTerms} onChange={(e) => setPaymentTerms(e.target.value)}>
            <option value="NET30">Net 30</option>
            <option value="NET60">Net 60</option>
            <option value="DUE_ON_RECEIPT">Due on receipt</option>
          </Select>
          <Input
            label="Credit limit"
            type="number"
            min={0}
            value={creditLimit}
            onChange={(e) => setCreditLimit(e.target.value)}
          />
          <Select label="Currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            {['USD', 'EUR', 'GBP', 'CAD'].map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
          <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="ACTIVE">Active</option>
            <option value="HOLD">Hold</option>
            <option value="PROSPECT">Prospect</option>
          </Select>
        </div>
        <fieldset className="space-y-3 rounded-md border border-border p-3">
          <legend className="px-1 text-sm font-medium text-text">Billing address</legend>
          <Input
            id="customer-billing-street"
            name="billingStreet"
            label="Street"
            value={billStreet}
            onChange={(e) => setBillStreet(e.target.value)}
          />
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input
              id="customer-billing-city"
              name="billingCity"
              label="City"
              value={billCity}
              onChange={(e) => setBillCity(e.target.value)}
            />
            <Input
              id="customer-billing-state"
              name="billingState"
              label="State"
              value={billState}
              onChange={(e) => setBillState(e.target.value)}
            />
            <Input
              id="customer-billing-postal"
              name="billingPostal"
              label="Postal"
              value={billPostal}
              onChange={(e) => setBillPostal(e.target.value)}
            />
            <Input
              id="customer-billing-country"
              name="billingCountry"
              label="Country"
              value={billCountry}
              onChange={(e) => setBillCountry(e.target.value)}
            />
          </div>
        </fieldset>
        <label className="flex items-center gap-2 text-sm text-text" htmlFor="customer-ship-same">
          <input
            id="customer-ship-same"
            name="shipSameAsBilling"
            type="checkbox"
            checked={shipSame}
            onChange={(e) => setShipSame(e.target.checked)}
          />
          Shipping same as billing
        </label>
        {!shipSame && (
          <fieldset className="space-y-3 rounded-md border border-border p-3">
            <legend className="px-1 text-sm font-medium text-text">Shipping address</legend>
            <Input
              id="customer-shipping-street"
              name="shippingStreet"
              label="Street"
              value={shipStreet}
              onChange={(e) => setShipStreet(e.target.value)}
            />
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Input
                id="customer-shipping-city"
                name="shippingCity"
                label="City"
                value={shipCity}
                onChange={(e) => setShipCity(e.target.value)}
              />
              <Input
                id="customer-shipping-state"
                name="shippingState"
                label="State"
                value={shipState}
                onChange={(e) => setShipState(e.target.value)}
              />
              <Input
                id="customer-shipping-postal"
                name="shippingPostal"
                label="Postal"
                value={shipPostal}
                onChange={(e) => setShipPostal(e.target.value)}
              />
              <Input
                id="customer-shipping-country"
                name="shippingCountry"
                label="Country"
                value={shipCountry}
                onChange={(e) => setShipCountry(e.target.value)}
              />
            </div>
          </fieldset>
        )}
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} data-testid="add-customer-submit">
            Add customer
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export function CustomersPage() {
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN');
  const [modalOpen, setModalOpen] = useState(false);
  const [peekCustomer, setPeekCustomer] = useState<Customer | null>(null);

  const { data, isLoading, isError, error, refetch } =
    useListQuery<Customer>(['customers'], '/api/v1/customers');

  return (
    <div className="mx-auto min-h-0 w-full max-w-7xl overflow-y-auto overscroll-contain p-4 sm:p-6">
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-text">Customers</h1>
          <p className="mt-1 text-sm text-text-muted">Buyer accounts, credit, and 3PL billing</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            Add customer
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
        emptyIcon={Users}
        emptyTitle="No customers yet"
        emptyDescription={
          canCreate
            ? 'Add customers to create sales orders and invoices.'
            : 'Customers will appear here once added by an admin.'
        }
        emptyAction={
          canCreate ? (
            <Button onClick={() => setModalOpen(true)}>
              <Plus className="h-4 w-4" />
              Add customer
            </Button>
          ) : undefined
        }
      >
        {(items) => <CustomersTable items={items} onPeek={setPeekCustomer} />}
      </ListPageState>

      <AddCustomerModal open={modalOpen} onClose={() => setModalOpen(false)} />

      <RightPeekDrawer
        open={!!peekCustomer}
        onClose={() => setPeekCustomer(null)}
        title={peekCustomer?.name ?? 'Customer'}
        description={peekCustomer?.email ?? 'Customer detail'}
        width="lg"
      >
        {peekCustomer ? <CustomerDetail customer={peekCustomer} /> : null}
      </RightPeekDrawer>
    </div>
  );
}
