import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Users, Plus } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Customer } from '@/api/types';
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
import { useSessionStore } from '@/stores/session';

function AddCustomerModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/customers', { name, email: email || undefined });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      setName('');
      setEmail('');
      onClose();
    },
    onError: () => setError('Could not create customer. Check the fields and try again.'),
  });

  return (
    <Modal open={open} onClose={onClose} title="Add customer" description="Buyer account for sales orders and invoices">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          mutation.mutate();
        }}
        className="space-y-4"
      >
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        <Input label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Optional" />
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending}>
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

  const { data, isLoading, isError, error, refetch } =
    useListQuery<Customer>(['customers'], '/api/v1/customers');

  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-text">Customers</h1>
          <p className="mt-1 text-sm text-text-muted">Buyer accounts</p>
        </div>
        {canCreate && (
          <Button onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            Add customer
          </Button>
        )}
      </div>

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
        {(items) => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>{c.name}</TableCell>
                  <TableCell>{c.email ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </ListPageState>

      <AddCustomerModal open={modalOpen} onClose={() => setModalOpen(false)} />
    </div>
  );
}
