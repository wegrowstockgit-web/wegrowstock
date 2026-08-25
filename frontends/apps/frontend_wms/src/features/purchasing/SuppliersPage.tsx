import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Globe, MoreHorizontal, Network, Plus, Star, Truck } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { Supplier } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Modal } from '@/components/ui/Modal';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';
import { EntityMobileCard } from '@/components/layout/EntityMobileCard';
import { ListPageState } from '@/components/layout/ListPageState';
import { DataListToolbar } from '@/components/ui/DensityToggle';
import { DebouncedSearchInput } from '@/components/ui/DebouncedSearchInput';
import { Pagination } from '@/components/ui/Pagination';
import { TableDensityScope } from '@/hooks/useDensity';
import { useClientSort } from '@/hooks/useClientSort';
import { useCapMobilePageSize } from '@/hooks/useCapMobilePageSize';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import { useServerTableQuery } from '@/hooks/useServerTable';
import { useSessionStore } from '@/stores/session';
import { listSuppliers } from '@/api/operational';
import { FEATURE_MODULE_FLAGS } from '@/lib/featureFlags';
import { cn } from '@/lib/utils';
import { useToast } from '@/components/ui/Toast';

const CLASS_LABEL: Record<string, string> = {
  RAW_MATERIALS: 'Raw Materials',
  PACKAGING: 'Packaging',
  FREIGHT: 'Freight/Logistics',
  FINISHED_GOODS: 'Finished Goods',
};

type DirectoryHit = {
  tenantId: string;
  name: string;
  slug: string;
  verified: boolean;
  catalogPublished: boolean;
};

function StarRating({ value }: { value?: number | null }) {
  const rating = Number(value ?? 0);
  return (
    <span className="inline-flex items-center gap-0.5" data-testid="supplier-rating" aria-label={`${rating} of 5`}>
      {Array.from({ length: 5 }, (_, i) => (
        <Star
          key={i}
          className={cn('h-3.5 w-3.5', i < Math.round(rating) ? 'fill-warning text-warning' : 'text-text-muted')}
          aria-hidden
        />
      ))}
    </span>
  );
}

function SuppliersTable({ items }: { items: Supplier[] }) {
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { sort, toggle, sorted } = useClientSort(
    items,
    {
      name: (s) => s.name,
      class: (s) => s.supplierClass ?? '',
      terms: (s) => s.paymentTerms ?? '',
      lead: (s) => s.defaultLeadTimeDays ?? 0,
      pos: (s) => s.activePoCount ?? 0,
      rating: (s) => s.supplierRating ?? 0,
    },
    { key: 'name', dir: 'asc' },
  );

  const resendInvite = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/suppliers/${id}/portal-invite`),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      toast('Portal invite resent.', { tone: 'success' });
    },
    onError: () => toast('Could not resend the portal invite.', { tone: 'danger' }),
  });

  return (
    <div className="min-w-0 overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead sortable sortKey="name" sort={sort} onSort={toggle}>
              Name
            </TableHead>
            <TableHead sortable sortKey="class" sort={sort} onSort={toggle}>
              Class
            </TableHead>
            <TableHead sortable sortKey="terms" sort={sort} onSort={toggle}>
              Terms
            </TableHead>
            <TableHead sortable sortKey="lead" sort={sort} onSort={toggle}>
              Lead Time
            </TableHead>
            <TableHead sortable sortKey="pos" sort={sort} onSort={toggle}>
              Active POs
            </TableHead>
            <TableHead sortable sortKey="rating" sort={sort} onSort={toggle}>
              Rating
            </TableHead>
            <TableHead align="right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {sorted.map((s) => (
            <TableRow key={s.id} data-testid={`supplier-row-${s.id}`}>
              <TableCell>
                <div className="flex flex-wrap items-center gap-2">
                  <Link
                    to={`/purchasing/suppliers/${s.id}`}
                    className="font-medium text-accent hover:underline"
                    data-testid={`supplier-name-link-${s.id}`}
                  >
                    {s.name}
                  </Link>
                  {s.isMeshPartner ? (
                    <span
                      className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-medium text-success"
                      data-testid="supplier-mesh-badge"
                    >
                      🟢 Mesh Connected
                    </span>
                  ) : null}
                  {s.portalAccess ? (
                    <span
                      className="inline-flex items-center gap-1 rounded-full bg-accent-muted px-2 py-0.5 text-[11px] font-medium text-accent"
                      data-testid="supplier-portal-badge"
                    >
                      <Globe className="h-3 w-3" aria-hidden />
                      Portal
                    </span>
                  ) : null}
                </div>
              </TableCell>
              <TableCell>{s.supplierClass ? CLASS_LABEL[s.supplierClass] ?? s.supplierClass : '—'}</TableCell>
              <TableCell>{s.paymentTerms ?? '—'}</TableCell>
              <TableCell>{s.defaultLeadTimeDays != null ? `${s.defaultLeadTimeDays}d` : '—'}</TableCell>
              <TableCell>
                <button
                  type="button"
                  className="text-accent hover:underline"
                  data-testid="supplier-active-pos"
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate(`/purchasing/suppliers/${s.id}`);
                  }}
                >
                  {s.activePoCount ?? 0}
                </button>
              </TableCell>
              <TableCell>
                <StarRating value={s.supplierRating} />
              </TableCell>
              <TableCell align="right" onClick={(e) => e.stopPropagation()}>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" data-testid="supplier-row-actions" aria-label={`Actions for ${s.name}`}>
                      <MoreHorizontal className="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      data-testid="supplier-open-workspace"
                      onClick={() => navigate(`/purchasing/suppliers/${s.id}`)}
                    >
                      Open Workspace
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      data-testid="supplier-browse-mesh"
                      disabled={!s.isMeshPartner}
                      onClick={() => navigate('/mesh-network')}
                    >
                      Browse Mesh Catalog
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      data-testid="supplier-resend-invite"
                      disabled={!s.portalAccess}
                      onClick={() => resendInvite.mutate(s.id)}
                    >
                      Resend Portal Invite
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function SuppliersMobileCards({ items }: { items: Supplier[] }) {
  const navigate = useNavigate();
  return (
    <div className="space-y-2 pb-2 md:hidden" data-testid="suppliers-mobile-list">
      {items.map((s) => (
        <EntityMobileCard
          key={s.id}
          testId={`supplier-mobile-card-${s.id}`}
          identity={s.name}
          title={s.contactEmail || CLASS_LABEL[s.supplierClass ?? ''] || s.supplierClass || 'Supplier'}
          status={
            s.isMeshPartner ? (
              <span className="inline-flex rounded-full bg-success/10 px-2 py-0.5 text-[11px] font-medium text-success">
                Mesh
              </span>
            ) : s.portalAccess ? (
              <span className="inline-flex rounded-full bg-accent-muted px-2 py-0.5 text-[11px] font-medium text-accent">
                Portal
              </span>
            ) : (
              <span className="inline-flex rounded-full bg-surface-overlay px-2 py-0.5 text-[11px] font-medium text-text-muted">
                Direct
              </span>
            )
          }
          amount={s.paymentTerms ?? '—'}
          date={s.defaultLeadTimeDays != null ? `${s.defaultLeadTimeDays}d lead` : undefined}
          onClick={() => navigate(`/purchasing/suppliers/${s.id}`)}
        />
      ))}
    </div>
  );
}

function AddSupplierModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const meshEnabled = FEATURE_MODULE_FLAGS.mesh;
  const [tab, setTab] = useState<'mesh' | 'manual'>(meshEnabled ? 'mesh' : 'manual');
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
  const [currency, setCurrency] = useState('USD');
  const [supplierClass, setSupplierClass] = useState('RAW_MATERIALS');
  const [incoterms, setIncoterms] = useState('FOB');
  const [invitePortal, setInvitePortal] = useState(false);
  const [meshQuery, setMeshQuery] = useState('');
  const [debouncedMesh, setDebouncedMesh] = useState('');
  const [selectedPartner, setSelectedPartner] = useState<DirectoryHit | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const handle = window.setTimeout(() => setDebouncedMesh(meshQuery.trim()), 250);
    return () => window.clearTimeout(handle);
  }, [meshQuery]);

  const meshSearch = useQuery({
    queryKey: ['mesh-directory', debouncedMesh],
    queryFn: async () =>
      (await apiClient.get<DirectoryHit[]>(`/api/v1/mesh/directory?q=${encodeURIComponent(debouncedMesh)}`)).data,
    enabled: open && tab === 'mesh' && debouncedMesh.trim().length >= 2,
  });

  const createMutation = useMutation({
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
        defaultCurrency: currency,
        supplierClass,
        incoterms,
        inviteToPortal: invitePortal,
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
      setCurrency('USD');
      setSupplierClass('RAW_MATERIALS');
      setIncoterms('FOB');
      setInvitePortal(false);
      onClose();
    },
    onError: () => setError('Could not create supplier. Check the fields and try again.'),
  });

  const handshakeMutation = useMutation({
    mutationFn: async (partnerTenantId: string) =>
      apiClient.post('/api/v1/mesh/handshake/initiate', { partnerTenantId }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['suppliers'] });
      setSelectedPartner(null);
      setMeshQuery('');
      setDebouncedMesh('');
      onClose();
    },
    onError: () => setError('Could not send the mesh handshake. Confirm the tenant code and try again.'),
  });

  const hits = meshSearch.data ?? [];

  return (
    <Modal open={open} onClose={onClose} title="Add supplier" description="Connect a weGrowStock mesh partner or add a manual vendor">
      <div className="mb-4 flex gap-2" data-testid="add-supplier-tabs">
        {meshEnabled ? (
          <Button
            type="button"
            size="sm"
            variant={tab === 'mesh' ? 'primary' : 'secondary'}
            data-testid="add-supplier-tab-mesh"
            onClick={() => setTab('mesh')}
          >
            Connect Mesh Partner
          </Button>
        ) : null}
        <Button
          type="button"
          size="sm"
          variant={tab === 'manual' ? 'primary' : 'secondary'}
          data-testid="add-supplier-tab-manual"
          onClick={() => setTab('manual')}
        >
          Manual Supplier
        </Button>
      </div>

      {tab === 'mesh' && meshEnabled ? (
        <div className="space-y-4" data-testid="mesh-partner-tab">
          <Input
            label="Tenant Code / Domain / Invite Code"
            placeholder="Search slug, company name, or domain…"
            value={meshQuery}
            onChange={(e) => setMeshQuery(e.target.value)}
            data-testid="mesh-directory-search"
          />
          {hits.map((hit) => (
            <button
              key={hit.tenantId}
              type="button"
              className={cn(
                'w-full rounded-lg border border-border p-3 text-left hover:bg-surface-overlay',
                selectedPartner?.tenantId === hit.tenantId && 'border-accent bg-accent-muted/30',
              )}
              data-testid="mesh-partner-card"
              onClick={() => setSelectedPartner(hit)}
            >
              <div className="flex items-center justify-between gap-2">
                <p className="font-medium">{hit.name}</p>
                {hit.verified ? (
                  <span className="text-xs font-medium text-success" data-testid="mesh-verified-badge">
                    Verified
                  </span>
                ) : null}
              </div>
              <p className="mt-1 font-mono text-xs text-text-muted">{hit.slug}</p>
              <p className="mt-1 text-xs text-text-muted">
                {hit.catalogPublished ? 'Catalog published on the mesh' : 'No published catalog yet'}
              </p>
            </button>
          ))}
          {selectedPartner ? (
            <div className="rounded-lg border border-border bg-surface-overlay/40 p-3" data-testid="mesh-partner-preview">
              <p className="font-medium">{selectedPartner.name}</p>
              <p className="text-sm text-text-muted">
                {selectedPartner.verified ? 'Verified tenant' : 'Unverified'}
                {selectedPartner.catalogPublished ? ' · Catalog ready' : ''}
              </p>
            </div>
          ) : null}
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button
              type="button"
              data-testid="send-mesh-handshake"
              disabled={!selectedPartner}
              loading={handshakeMutation.isPending}
              onClick={() => selectedPartner && handshakeMutation.mutate(selectedPartner.tenantId)}
            >
              <Network className="h-4 w-4" aria-hidden />
              Send Mesh Handshake
            </Button>
          </div>
        </div>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            setError('');
            createMutation.mutate();
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
              <option value="NET15">Net 15</option>
              <option value="NET30">Net 30</option>
              <option value="NET60">Net 60</option>
              <option value="DUE_ON_RECEIPT">Due on receipt</option>
            </Select>
            <Select label="Currency" value={currency} onChange={(e) => setCurrency(e.target.value)} data-testid="supplier-currency">
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="MXN">MXN</option>
              <option value="CAD">CAD</option>
              <option value="GBP">GBP</option>
            </Select>
            <Select
              label="Supplier Class"
              value={supplierClass}
              onChange={(e) => setSupplierClass(e.target.value)}
              data-testid="supplier-class"
            >
              <option value="RAW_MATERIALS">Raw Materials</option>
              <option value="PACKAGING">Packaging</option>
              <option value="FREIGHT">Freight/Logistics</option>
              <option value="FINISHED_GOODS">Finished Goods</option>
            </Select>
            <Select label="Incoterms" value={incoterms} onChange={(e) => setIncoterms(e.target.value)} data-testid="supplier-incoterms">
              <option value="FOB">FOB</option>
              <option value="EXW">EXW</option>
              <option value="DDP">DDP</option>
              <option value="CIF">CIF</option>
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
          <label className="flex items-center gap-2 text-sm text-text">
            <input
              type="checkbox"
              checked={invitePortal}
              onChange={(e) => setInvitePortal(e.target.checked)}
              data-testid="invite-supplier-portal"
            />
            Invite to Supplier Portal
          </label>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={createMutation.isPending} data-testid="add-supplier-submit">
              Add supplier
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}

export function SuppliersPage() {
  const hasRole = useSessionStore((s) => s.hasRole);
  const canCreate = hasRole('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER');
  const isMobile = useMediaQuery('(max-width: 767px)');
  const [modalOpen, setModalOpen] = useState(false);

  const table = useServerTableQuery<Supplier>({
    queryKey: 'suppliers',
    path: '/api/v1/suppliers',
    defaultSort: 'name,asc',
    fetcher: listSuppliers,
  });
  const { items, isLoading, isError, error, refetch, search } = table;
  useCapMobilePageSize(isMobile, table.size, table.setSize);

  return (
    <TableDensityScope gridId="suppliers">
    <div
      className="mx-auto min-h-0 w-full max-w-7xl p-4 sm:p-6"
      data-testid="suppliers-page"
    >
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold text-text">Suppliers</h1>
          <p className="mt-1 text-sm text-text-muted">Mesh partners, portal vendors, and procurement terms</p>
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
            ? 'Connect a mesh partner or add a manual supplier to create purchase orders.'
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
            {isMobile ? (
              <SuppliersMobileCards items={rows} />
            ) : (
              <div className="hidden md:block" data-testid="suppliers-table-view">
                <SuppliersTable items={rows} />
              </div>
            )}
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
