import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RtlsWorkspacePage } from './RtlsWorkspacePage';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn().mockResolvedValue({ data: [] }),
    post: vi.fn(),
  },
}));

function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('max-width: 767px') ? matches : false,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
    onchange: null,
  }));
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RtlsWorkspacePage />
    </QueryClientProvider>,
  );
}

describe('RtlsWorkspacePage', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    vi.stubGlobal(
      'EventSource',
      class {
        addEventListener() {}
        close() {}
      },
    );
    vi.mocked(apiClient.get).mockResolvedValue({ data: [] });
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it('shows the vector canvas on desktop', () => {
    stubMatchMedia(false);
    renderPage();
    expect(screen.getByTestId('rtls-workspace-page')).toBeInTheDocument();
    expect(screen.getByTestId('rtls-vector-canvas')).toBeInTheDocument();
    expect(screen.queryByTestId('rtls-mobile-empty')).not.toBeInTheDocument();
  });

  it('advises opening the map on a tablet or desktop from a phone', () => {
    stubMatchMedia(true);
    renderPage();
    expect(screen.getByTestId('rtls-mobile-empty')).toHaveTextContent(
      /tablet or desktop workstation/i,
    );
    expect(screen.queryByTestId('rtls-vector-canvas')).not.toBeInTheDocument();
  });
});
