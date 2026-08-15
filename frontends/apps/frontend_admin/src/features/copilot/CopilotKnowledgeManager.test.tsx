import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CopilotKnowledgeManager } from './CopilotKnowledgeManager';

vi.mock('./api', () => ({
  fetchKnowledgeDocuments: vi.fn(),
  ingestKnowledgeDocument: vi.fn(),
  deleteKnowledgeDocument: vi.fn(),
}));

vi.mock('@invsys/shared-ui', async () => {
  const actual = await vi.importActual<typeof import('@invsys/shared-ui')>('@invsys/shared-ui');
  return {
    ...actual,
    useToast: () => ({
      success: vi.fn(),
      danger: vi.fn(),
      info: vi.fn(),
      warning: vi.fn(),
    }),
  };
});

import { fetchKnowledgeDocuments } from './api';

function renderManager() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CopilotKnowledgeManager />
    </QueryClientProvider>,
  );
}

describe('CopilotKnowledgeManager', () => {
  beforeEach(() => {
    vi.mocked(fetchKnowledgeDocuments).mockReset();
    vi.mocked(fetchKnowledgeDocuments).mockResolvedValue([]);
  });

  it('renders drop zone and heading', async () => {
    renderManager();
    expect(screen.getByTestId('copilot-knowledge')).toBeTruthy();
    expect(screen.getByTestId('knowledge-dropzone')).toBeTruthy();
    expect(await screen.findByText(/No documents ingested yet/i)).toBeTruthy();
  });
});
