import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Select } from '@/components/ui/Select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { TableSkeleton } from '@/components/ui/Skeleton';
import { useToast } from '@/components/ui/Toast';

interface MeshPartner {
  meshPartnerId: string;
  partnerTenantId: string;
  supplierId: string;
  customerId: string;
  connectionStatus: string;
}

interface PartnerSku {
  variantId: string;
  sku: string;
  productName: string;
}

interface CatalogMapping {
  localVariantId: string;
  localSku: string;
  partnerVariantId: string | null;
  partnerSku: string | null;
  externalId: string | null;
}

export function PartnerCatalogMappingPanel() {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [partnerTenantId, setPartnerTenantId] = useState('');
  const [draft, setDraft] = useState<Record<string, string>>({});

  const partnersQuery = useQuery({
    queryKey: ['mesh-partners'],
    queryFn: async () => (await apiClient.get<MeshPartner[]>('/api/v1/settings/mesh/partners')).data,
  });

  const selectedPartner = partnerTenantId || partnersQuery.data?.[0]?.partnerTenantId || '';

  const catalogQuery = useQuery({
    queryKey: ['mesh-partner-catalog', selectedPartner],
    enabled: !!selectedPartner,
    queryFn: async () =>
      (await apiClient.get<PartnerSku[]>(`/api/v1/settings/mesh/partners/${selectedPartner}/catalog`))
        .data,
  });

  const mappingsQuery = useQuery({
    queryKey: ['mesh-partner-mappings', selectedPartner],
    enabled: !!selectedPartner,
    queryFn: async () =>
      (
        await apiClient.get<CatalogMapping[]>(
          `/api/v1/settings/mesh/partners/${selectedPartner}/mappings`
        )
      ).data,
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      const body = Object.entries(draft).map(([localVariantId, partnerVariantId]) => ({
        localVariantId,
        partnerVariantId: partnerVariantId ? partnerVariantId : null,
      }));
      await apiClient.put(`/api/v1/settings/mesh/partners/${selectedPartner}/mappings`, body);
    },
    onSuccess: async () => {
      setDraft({});
      await queryClient.invalidateQueries({ queryKey: ['mesh-partner-mappings', selectedPartner] });
      toast('Catalog mappings saved', { tone: 'success' });
    },
    onError: () => toast('Could not save catalog mappings', { tone: 'danger' }),
  });

  const rows = useMemo(() => mappingsQuery.data ?? [], [mappingsQuery.data]);
  const partnerOptions = catalogQuery.data ?? [];
  const dirty = Object.keys(draft).length > 0;

  function currentPartnerVariantId(row: CatalogMapping): string {
    if (row.localVariantId in draft) {
      return draft[row.localVariantId];
    }
    return row.partnerVariantId ?? '';
  }

  if (partnersQuery.isLoading) {
    return <TableSkeleton rows={4} cols={3} />;
  }

  if (!partnersQuery.data?.length) {
    return (
      <Card>
        <CardHeader
          title="Partner catalog mapping"
          description="Connect a mesh trading partner to map buyer SKUs to seller catalog variants."
        />
        <p className="text-sm text-text-muted">
          No CONNECTED mesh partners yet. Pair a supplier to a partner tenant to enable catalog
          translation.
        </p>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader
        title="Partner catalog mapping"
        description="Link your internal variants to a mesh partner's published SKUs (stored in external_references / MESH_NETWORK)."
        action={
          <Button
            type="button"
            disabled={!dirty || saveMutation.isPending || !selectedPartner}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? 'Saving…' : 'Save mappings'}
          </Button>
        }
      />

      <div className="mb-4 max-w-md">
        <Select
          label="Mesh partner"
          value={selectedPartner}
          onChange={(e) => {
            setPartnerTenantId(e.target.value);
            setDraft({});
          }}
        >
          {partnersQuery.data.map((p) => (
            <option key={p.partnerTenantId} value={p.partnerTenantId}>
              Partner {p.partnerTenantId.slice(0, 8)}… ({p.connectionStatus})
            </option>
          ))}
        </Select>
      </div>

      {mappingsQuery.isLoading || catalogQuery.isLoading ? (
        <TableSkeleton rows={6} cols={3} />
      ) : (
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Your SKU</TableHead>
                <TableHead>Partner catalog SKU</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => {
                const selected = currentPartnerVariantId(row);
                const mapped = !!selected;
                return (
                  <TableRow key={row.localVariantId}>
                    <TableCell className="font-medium text-text">{row.localSku}</TableCell>
                    <TableCell>
                      <Select
                        aria-label={`Map ${row.localSku}`}
                        value={selected}
                        onChange={(e) =>
                          setDraft((prev) => ({
                            ...prev,
                            [row.localVariantId]: e.target.value,
                          }))
                        }
                      >
                        <option value="">— Unmapped —</option>
                        {partnerOptions.map((sku) => (
                          <option key={sku.variantId} value={sku.variantId}>
                            {sku.sku} — {sku.productName}
                          </option>
                        ))}
                      </Select>
                    </TableCell>
                    <TableCell>
                      <span
                        className={
                          mapped
                            ? 'text-xs font-medium text-success'
                            : 'text-xs font-medium text-warning'
                        }
                      >
                        {mapped ? 'Mapped' : 'Needs mapping'}
                      </span>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}
    </Card>
  );
}
