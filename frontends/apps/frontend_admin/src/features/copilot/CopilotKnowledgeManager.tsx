import { useCallback, useState, type DragEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileUp, Trash2 } from 'lucide-react';
import {
  PageSkeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  useToast,
} from '@invsys/shared-ui';
import {
  deleteKnowledgeDocument,
  fetchKnowledgeDocuments,
  ingestKnowledgeDocument,
} from './api';

export function CopilotKnowledgeManager() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [dragging, setDragging] = useState(false);

  const { data: documents = [], isLoading, isError } = useQuery({
    queryKey: ['control-plane', 'knowledge'],
    queryFn: fetchKnowledgeDocuments,
  });

  const ingestMutation = useMutation({
    mutationFn: ingestKnowledgeDocument,
    onSuccess: (doc) => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'knowledge'] });
      toast.success(`Ingested ${doc.title} (${doc.chunkCount} chunks)`);
    },
    onError: () => {
      toast.danger('Could not ingest markdown file. Only .md is supported.');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteKnowledgeDocument,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['control-plane', 'knowledge'] });
      toast.success('Document removed');
    },
    onError: () => {
      toast.danger('Could not delete document.');
    },
  });

  const acceptFile = useCallback(
    (file: File | undefined) => {
      if (!file) return;
      if (!file.name.toLowerCase().endsWith('.md')) {
        toast.danger('Only .md files are supported.');
        return;
      }
      ingestMutation.mutate(file);
    },
    [ingestMutation, toast],
  );

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    acceptFile(e.dataTransfer.files?.[0]);
  };

  return (
    <div className="space-y-6" data-testid="copilot-knowledge">
      <div>
        <h2 className="text-lg font-semibold tracking-tight">Copilot knowledge</h2>
        <p className="mt-1 text-sm text-text-muted">
          Upload platform markdown SOPs into the shared knowledge index for Super Admin copilots.
        </p>
      </div>

      <div
        role="button"
        tabIndex={0}
        data-testid="knowledge-dropzone"
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            document.getElementById('knowledge-file-input')?.click();
          }
        }}
        onClick={() => document.getElementById('knowledge-file-input')?.click()}
        className={
          dragging
            ? 'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-accent bg-accent/10 px-6 py-12 text-center'
            : 'flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed border-border bg-surface-raised px-6 py-12 text-center hover:border-accent/50'
        }
      >
        <FileUp className="h-8 w-8 text-text-muted" aria-hidden />
        <p className="text-sm font-medium text-text">Drop a .md file here, or click to browse</p>
        <p className="text-xs text-text-muted">
          {ingestMutation.isPending ? 'Uploading…' : 'Markdown only · chunked into platform knowledge'}
        </p>
        <input
          id="knowledge-file-input"
          type="file"
          accept=".md,text/markdown"
          className="hidden"
          data-testid="knowledge-file-input"
          onChange={(e) => acceptFile(e.target.files?.[0])}
        />
      </div>

      {isLoading ? (
        <PageSkeleton label="Loading documents…" />
      ) : isError ? (
        <p className="text-sm text-danger">Failed to load knowledge documents.</p>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead>Chunks</TableHead>
              <TableHead>Created</TableHead>
              <TableHead className="text-right"> </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {documents.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-text-muted">
                  No documents ingested yet.
                </TableCell>
              </TableRow>
            ) : (
              documents.map((doc) => (
                <TableRow key={doc.id}>
                  <TableCell className="font-medium">{doc.title}</TableCell>
                  <TableCell className="text-text-muted">{doc.slug}</TableCell>
                  <TableCell>{doc.chunkCount}</TableCell>
                  <TableCell className="text-text-muted">
                    {new Date(doc.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell className="text-right">
                    <button
                      type="button"
                      aria-label={`Delete ${doc.title}`}
                      className="inline-flex rounded p-1.5 text-text-muted hover:bg-surface hover:text-danger"
                      disabled={deleteMutation.isPending}
                      onClick={() => deleteMutation.mutate(doc.id)}
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
    </div>
  );
}
