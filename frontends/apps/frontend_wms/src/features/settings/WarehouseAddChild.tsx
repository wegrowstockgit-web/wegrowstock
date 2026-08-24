import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { TenantLocation } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { extractApiError } from '@/lib/apiClient';
import { CHILD_TYPE } from '@/features/settings/warehouseTree';

export function AddChildInline({
  parent,
  onDone,
}: {
  parent: TenantLocation;
  onDone: () => void;
}) {
  const queryClient = useQueryClient();
  const childType = parent.type in CHILD_TYPE ? CHILD_TYPE[parent.type] : 'ZONE';
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async () => {
      if (!childType) throw new Error('Leaf location');
      const path = `${parent.path}/${code.trim().toUpperCase()}`;
      await apiClient.post('/api/v1/locations', {
        parentLocationId: parent.id,
        type: childType,
        code: code.trim().toUpperCase(),
        name: name.trim() || code.trim().toUpperCase(),
        path,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['locations'] });
      void queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      onDone();
    },
    onError: (err) =>
      setError(extractApiError(err, 'Could not create location. Check code uniqueness.')),
  });

  if (!childType) return null;

  return (
    <form
      className="space-y-2 rounded-md border border-border-strong bg-surface-raised p-3"
      data-testid="warehouse-add-child"
      onSubmit={(e) => {
        e.preventDefault();
        setError('');
        mutation.mutate();
      }}
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-text-muted">
        Add {childType.toLowerCase()} under {parent.code}
      </p>
      <div className="grid gap-2 sm:grid-cols-2">
        <Input
          label="Code"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          placeholder={childType === 'BIN' ? 'B01' : 'A'}
          required
          autoFocus
        />
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Optional label"
        />
      </div>
      {error && <p className="text-xs text-danger">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button type="button" size="sm" variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" size="sm" loading={mutation.isPending}>
          Add {childType.toLowerCase()}
        </Button>
      </div>
    </form>
  );
}
