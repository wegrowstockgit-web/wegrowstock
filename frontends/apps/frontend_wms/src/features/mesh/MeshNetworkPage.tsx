import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Network } from 'lucide-react';
import {
  approveMeshConnection,
  fetchMeshDiscover,
  fetchMeshNetwork,
  fetchMeshSharedCatalog,
  requestMeshConnection,
  updateMeshListing,
} from '@/api/mesh';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { cn } from '@/lib/utils';

type MeshTab = 'discover' | 'network' | 'catalog';

export function MeshNetworkPage() {
  const [tab, setTab] = useState<MeshTab>('discover');

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-col" data-testid="mesh-network-page">
      <div className="flex shrink-0 items-center justify-between gap-4 border-b border-border/60 px-6 py-4">
        <div>
          <h1 className="text-2xl font-bold text-text">Mesh Network</h1>
          <p className="mt-1 text-sm text-text-muted">
            Discover partner catalogs, approve connections, and publish your wholesale list
          </p>
        </div>
      </div>

      <div className="mb-4 flex shrink-0 gap-2 px-6 pt-4" role="tablist" aria-label="Mesh network views">
        <TabButton active={tab === 'discover'} onClick={() => setTab('discover')} testId="mesh-tab-discover">
          Discover
        </TabButton>
        <TabButton active={tab === 'network'} onClick={() => setTab('network')} testId="mesh-tab-network">
          My Network
        </TabButton>
        <TabButton active={tab === 'catalog'} onClick={() => setTab('catalog')} testId="mesh-tab-catalog">
          Shared Catalog
        </TabButton>
      </div>

      <div className="min-h-0 flex-1 overflow-auto px-6 pb-6">
        {tab === 'discover' && <DiscoverGrid />}
        {tab === 'network' && <NetworkTable />}
        {tab === 'catalog' && <SharedCatalogTable />}
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  testId,
  children,
}: {
  active: boolean;
  onClick: () => void;
  testId: string;
  children: string;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      data-testid={testId}
      className={cn(
        'rounded-md px-3 py-1.5 text-sm font-medium',
        active ? 'bg-accent-muted text-accent' : 'text-text-muted hover:bg-surface-overlay',
      )}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function DiscoverGrid() {
  const queryClient = useQueryClient();
  const { data = [], isLoading } = useQuery({
    queryKey: ['mesh', 'discover'],
    queryFn: fetchMeshDiscover,
  });
  const request = useMutation({
    mutationFn: (variantId: string) => requestMeshConnection({ variantId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['mesh'] });
    },
  });

  if (isLoading) {
    return <p className="text-sm text-text-muted">Loading published partner products…</p>;
  }
  if (data.length === 0) {
    return (
      <Card className="flex flex-col items-center gap-2 py-12 text-center">
        <Network className="h-8 w-8 text-text-muted" aria-hidden />
        <p className="text-sm text-text-muted">No partner products are published to the network yet.</p>
      </Card>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3" data-testid="mesh-discover-grid">
      {data.map((item) => (
        <Card key={item.variantId} className="flex flex-col gap-3" data-testid="mesh-discover-card">
          <div className="aspect-[4/3] overflow-hidden rounded-md bg-surface-overlay">
            {item.imageUrl ? (
              <img src={item.imageUrl} alt="" className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full items-center justify-center text-xs text-text-muted">No image</div>
            )}
          </div>
          <div>
            <h2 className="text-base font-semibold text-text">{item.productName}</h2>
            <p className="mt-1 text-sm text-text-muted">{item.sellerName}</p>
          </div>
          <Button
            size="sm"
            onClick={() => request.mutate(item.variantId)}
            disabled={request.isPending}
          >
            Request Connection
          </Button>
        </Card>
      ))}
    </div>
  );
}

function NetworkTable() {
  const queryClient = useQueryClient();
  const { data = [], isLoading } = useQuery({
    queryKey: ['mesh', 'network'],
    queryFn: fetchMeshNetwork,
  });
  const approve = useMutation({
    mutationFn: approveMeshConnection,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['mesh'] });
    },
  });

  if (isLoading) {
    return <p className="text-sm text-text-muted">Loading network…</p>;
  }

  return (
    <div data-testid="mesh-network-table">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Partner</TableHead>
          <TableHead>Role</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Action</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.length === 0 ? (
          <TableRow>
            <TableCell colSpan={4} className="text-text-muted">
              No pending, requested, or connected partners yet.
            </TableCell>
          </TableRow>
        ) : (
          data.map((row) => (
            <TableRow key={row.id}>
              <TableCell>{row.partnerName}</TableCell>
              <TableCell className="text-text-muted">{row.role}</TableCell>
              <TableCell>
                <StatusBadge status={row.displayStatus} />
              </TableCell>
              <TableCell>
                {row.canApprove ? (
                  <Button size="sm" onClick={() => approve.mutate(row.id)} disabled={approve.isPending}>
                    Approve
                  </Button>
                ) : (
                  <span className="text-xs text-text-muted">—</span>
                )}
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
    </div>
  );
}

function SharedCatalogTable() {
  const queryClient = useQueryClient();
  const { data = [], isLoading } = useQuery({
    queryKey: ['mesh', 'catalog'],
    queryFn: fetchMeshSharedCatalog,
  });
  const upsert = useMutation({
    mutationFn: (row: { variantId: string; published: boolean; meshWholesalePrice?: number | null }) =>
      updateMeshListing(row.variantId, row.published, row.meshWholesalePrice),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['mesh', 'catalog'] });
    },
  });

  if (isLoading) {
    return <p className="text-sm text-text-muted">Loading catalog…</p>;
  }

  return (
    <div data-testid="mesh-shared-catalog">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Product</TableHead>
          <TableHead>SKU</TableHead>
          <TableHead>Publish to Network</TableHead>
          <TableHead>Mesh Wholesale Price</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {data.length === 0 ? (
          <TableRow>
            <TableCell colSpan={4} className="text-text-muted">
              Add products to publish them to the mesh.
            </TableCell>
          </TableRow>
        ) : (
          data.map((row) => (
            <TableRow key={row.variantId}>
              <TableCell>{row.productName}</TableCell>
              <TableCell mono>{row.sku}</TableCell>
              <TableCell>
                <label className="inline-flex items-center gap-2 text-sm text-text">
                  <input
                    type="checkbox"
                    checked={row.published}
                    aria-label={`Publish ${row.sku} to network`}
                    onChange={(event) =>
                      upsert.mutate({
                        variantId: row.variantId,
                        published: event.target.checked,
                        meshWholesalePrice: row.meshWholesalePrice,
                      })
                    }
                  />
                  Publish to Network
                </label>
              </TableCell>
              <TableCell>
                <Input
                  type="number"
                  step="0.01"
                  min="0"
                  defaultValue={row.meshWholesalePrice ?? ''}
                  aria-label={`Mesh wholesale price for ${row.sku}`}
                  onBlur={(event) => {
                    const value = event.target.value;
                    upsert.mutate({
                      variantId: row.variantId,
                      published: row.published,
                      meshWholesalePrice: value === '' ? null : Number(value),
                    });
                  }}
                />
              </TableCell>
            </TableRow>
          ))
        )}
      </TableBody>
    </Table>
    </div>
  );
}
