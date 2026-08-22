import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import {
  Button,
  PageSkeleton,
  SlideOutDrawer,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useToast,
} from '@invsys/shared-ui';
import { PageHeader } from '@/features/layout/PageHeader';
import {
  PAGE_HELP_CATEGORIES,
  createPageHelp,
  deletePageHelp,
  fetchPageHelp,
  updatePageHelp,
  type MistakeFix,
  type PageHelpRecord,
  type PageHelpWritePayload,
} from './pageHelpApi';
import { PageHelpPreview } from './PageHelpPreview';

const emptyDraft = (): PageHelpWritePayload => ({
  routePattern: '/',
  category: 'Core',
  title: '',
  summary: '',
  rolePrivileges: '',
  keyActions: [''],
  commonMistakes: [{ mistake: '', solution: '', requiredRole: 'WAREHOUSE_MANAGER' }],
  proTip: '',
});

function toDraft(row: PageHelpRecord): PageHelpWritePayload {
  return {
    routePattern: row.routePattern,
    category: row.category,
    title: row.title,
    summary: row.summary,
    rolePrivileges: row.rolePrivileges,
    keyActions: row.keyActions.length ? [...row.keyActions] : [''],
    commonMistakes: row.commonMistakes.length
      ? row.commonMistakes.map((item) => ({ ...item }))
      : [{ mistake: '', solution: '', requiredRole: 'WAREHOUSE_MANAGER' }],
    proTip: row.proTip ?? '',
  };
}

function cleaned(draft: PageHelpWritePayload): PageHelpWritePayload {
  return {
    ...draft,
    routePattern: draft.routePattern.trim(),
    category: draft.category.trim(),
    title: draft.title.trim(),
    summary: draft.summary.trim(),
    rolePrivileges: draft.rolePrivileges.trim(),
    keyActions: draft.keyActions.map((item) => item.trim()).filter(Boolean),
    commonMistakes: draft.commonMistakes
      .map((item) => ({
        mistake: item.mistake.trim(),
        solution: item.solution.trim(),
        requiredRole: item.requiredRole.trim() || 'WAREHOUSE_MANAGER',
      }))
      .filter((item) => item.mistake && item.solution),
    proTip: draft.proTip?.trim() || null,
  };
}

export function PageHelpManager() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draft, setDraft] = useState<PageHelpWritePayload>(emptyDraft);

  const { data: rows = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'page-help', search, category],
    queryFn: () => fetchPageHelp(search, category),
  });

  const saveMutation = useMutation({
    mutationFn: () =>
      editingId ? updatePageHelp(editingId, cleaned(draft)) : createPageHelp(cleaned(draft)),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'page-help'] });
      toast.success(`Saved ${saved.routePattern}`);
      setDrawerOpen(false);
    },
    onError: () => {
      toast.danger('Could not save page help. Check the route pattern is unique.');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deletePageHelp,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'page-help'] });
      toast.success('Page help removed');
    },
    onError: () => toast.danger('Could not delete page help.'),
  });

  const categories = useMemo(() => {
    const fromData = new Set(rows.map((row) => row.category));
    return [...PAGE_HELP_CATEGORIES].filter((item) => fromData.has(item) || item === category);
  }, [rows, category]);

  const openCreate = () => {
    setEditingId(null);
    setDraft(emptyDraft());
    setDrawerOpen(true);
  };

  const openEdit = (row: PageHelpRecord) => {
    setEditingId(row.id);
    setDraft(toDraft(row));
    setDrawerOpen(true);
  };

  const setAction = (index: number, value: string) => {
    setDraft((prev) => {
      const next = [...prev.keyActions];
      next[index] = value;
      return { ...prev, keyActions: next };
    });
  };

  const setMistake = (index: number, patch: Partial<MistakeFix>) => {
    setDraft((prev) => {
      const next = prev.commonMistakes.map((item, i) => (i === index ? { ...item, ...patch } : item));
      return { ...prev, commonMistakes: next };
    });
  };

  return (
    <div className="space-y-6" data-testid="page-help-manager">
      <PageHeader
        title="Page help"
        description="Dynamic weGrowStock Page Info (“i”) content. Changes preload into the WMS on next login."
        actions={
          <Button type="button" onClick={openCreate} data-testid="page-help-create">
            <Plus className="h-4 w-4" aria-hidden />
            New route
          </Button>
        }
      />

      <div className="flex flex-wrap gap-3">
        <input
          className="admin-field min-w-[16rem] flex-1"
          placeholder="Search route, title, or category"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          data-testid="page-help-search"
        />
        <select
          className="admin-field w-48"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          data-testid="page-help-category-filter"
        >
          <option value="">All modules</option>
          {PAGE_HELP_CATEGORIES.map((item) => (
            <option key={item} value={item}>
              {item}
            </option>
          ))}
          {categories
            .filter((item) => !(PAGE_HELP_CATEGORIES as readonly string[]).includes(item))
            .map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
        </select>
      </div>

      {isLoading ? (
        <PageSkeleton label="Loading page help…" />
      ) : isError ? (
        <p className="text-sm text-danger">Failed to load page help.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Route</TableHead>
              <TableHead>Title</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Updated</TableHead>
              <TableHead className="text-right"> </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-text-muted">
                  No page help matches.
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => (
                <TableRow key={row.id} data-testid="page-help-row">
                  <TableCell className="font-mono text-xs">{row.routePattern}</TableCell>
                  <TableCell>
                    <button
                      type="button"
                      className="font-medium text-text hover:text-accent"
                      onClick={() => openEdit(row)}
                    >
                      {row.title}
                    </button>
                  </TableCell>
                  <TableCell>
                    <span className="rounded-md bg-accent/15 px-2 py-0.5 text-xs font-medium text-accent">
                      {row.category}
                    </span>
                  </TableCell>
                  <TableCell className="text-text-muted">
                    {row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '—'}
                  </TableCell>
                  <TableCell className="text-right">
                    <button
                      type="button"
                      aria-label={`Delete ${row.title}`}
                      className="inline-flex rounded p-1.5 text-text-muted hover:bg-surface hover:text-danger"
                      disabled={deleteMutation.isPending}
                      onClick={() => deleteMutation.mutate(row.id)}
                    >
                      <Trash2 className="h-4 w-4" aria-hidden />
                    </button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      )}

      <SlideOutDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        title={editingId ? 'Edit page help' : 'New page help'}
        width="lg"
      >
        <form
          className="grid gap-6 lg:grid-cols-2"
          data-testid="page-help-editor"
          onSubmit={(e) => {
            e.preventDefault();
            saveMutation.mutate();
          }}
        >
          <div className="space-y-4">
            <label className="block">
              <span className="text-xs font-medium text-text-muted">Route pattern</span>
              <input
                className="admin-field mt-1 font-mono"
                value={draft.routePattern}
                onChange={(e) => setDraft((prev) => ({ ...prev, routePattern: e.target.value }))}
                required
                data-testid="page-help-route-input"
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-text-muted">Title</span>
              <input
                className="admin-field mt-1"
                value={draft.title}
                onChange={(e) => setDraft((prev) => ({ ...prev, title: e.target.value }))}
                required
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-text-muted">Category</span>
              <select
                className="admin-field mt-1"
                value={draft.category}
                onChange={(e) => setDraft((prev) => ({ ...prev, category: e.target.value }))}
              >
                {PAGE_HELP_CATEGORIES.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-xs font-medium text-text-muted">Summary</span>
              <textarea
                className="admin-field mt-1 min-h-[96px] h-auto py-2"
                value={draft.summary}
                onChange={(e) => setDraft((prev) => ({ ...prev, summary: e.target.value }))}
                required
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-text-muted">Role privileges</span>
              <textarea
                className="admin-field mt-1 min-h-[72px] h-auto py-2"
                value={draft.rolePrivileges}
                onChange={(e) => setDraft((prev) => ({ ...prev, rolePrivileges: e.target.value }))}
                required
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-text-muted">Pro tip</span>
              <textarea
                className="admin-field mt-1 min-h-[64px] h-auto py-2"
                value={draft.proTip ?? ''}
                onChange={(e) => setDraft((prev) => ({ ...prev, proTip: e.target.value }))}
              />
            </label>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-medium text-text-muted">Key actions</p>
                <button
                  type="button"
                  className="text-xs text-accent"
                  onClick={() => setDraft((prev) => ({ ...prev, keyActions: [...prev.keyActions, ''] }))}
                >
                  Add action
                </button>
              </div>
              <div className="space-y-2">
                {draft.keyActions.map((action, index) => (
                  <div key={index} className="flex gap-2">
                    <input
                      className="admin-field flex-1"
                      value={action}
                      onChange={(e) => setAction(index, e.target.value)}
                    />
                    <button
                      type="button"
                      className="rounded p-2 text-text-muted hover:text-danger"
                      onClick={() =>
                        setDraft((prev) => ({
                          ...prev,
                          keyActions: prev.keyActions.filter((_, i) => i !== index),
                        }))
                      }
                      aria-label={`Remove action ${index + 1}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                ))}
              </div>
            </div>

            <div>
              <div className="mb-2 flex items-center justify-between">
                <p className="text-xs font-medium text-text-muted">Common mistakes & fixes</p>
                <button
                  type="button"
                  className="text-xs text-accent"
                  onClick={() =>
                    setDraft((prev) => ({
                      ...prev,
                      commonMistakes: [
                        ...prev.commonMistakes,
                        { mistake: '', solution: '', requiredRole: 'WAREHOUSE_MANAGER' },
                      ],
                    }))
                  }
                >
                  Add mistake
                </button>
              </div>
              <div className="space-y-3">
                {draft.commonMistakes.map((item, index) => (
                  <div key={index} className="space-y-2 rounded-lg border border-border p-3">
                    <input
                      className="admin-field"
                      placeholder="Mistake"
                      value={item.mistake}
                      onChange={(e) => setMistake(index, { mistake: e.target.value })}
                    />
                    <textarea
                      className="admin-field min-h-[64px] h-auto py-2"
                      placeholder="Ledger-safe solution"
                      value={item.solution}
                      onChange={(e) => setMistake(index, { solution: e.target.value })}
                    />
                    <div className="flex gap-2">
                      <input
                        className="admin-field flex-1"
                        placeholder="Required role"
                        value={item.requiredRole}
                        onChange={(e) => setMistake(index, { requiredRole: e.target.value })}
                      />
                      <button
                        type="button"
                        className="rounded p-2 text-text-muted hover:text-danger"
                        onClick={() =>
                          setDraft((prev) => ({
                            ...prev,
                            commonMistakes: prev.commonMistakes.filter((_, i) => i !== index),
                          }))
                        }
                        aria-label={`Remove mistake ${index + 1}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <Button type="submit" disabled={saveMutation.isPending} data-testid="page-help-save">
              {saveMutation.isPending ? 'Saving…' : 'Save page help'}
            </Button>
          </div>
          <PageHelpPreview draft={draft} />
        </form>
      </SlideOutDrawer>
    </div>
  );
}
