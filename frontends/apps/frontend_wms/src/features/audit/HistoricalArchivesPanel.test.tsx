import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HistoricalArchivesPanel } from './HistoricalArchivesPanel';
import { apiClient } from '@/api/client';

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('HistoricalArchivesPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
  });

  it('renders historical archives controls', () => {
    const { container } = render(<HistoricalArchivesPanel />);
    expect(container.firstChild).not.toBeNull();
    expect(screen.getByTestId('historical-archives-panel')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Historical Archives' })).toBeInTheDocument();
    expect(screen.getByTestId('archive-download-button')).toBeInTheDocument();
  });

  it('downloads archive via authenticated blob fetch', async () => {
    const createObjectURL = vi.fn(() => 'blob:audit-archive');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });

    const click = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(click);

    vi.mocked(apiClient.get).mockResolvedValue({
      data: new Blob(['{"a":1}\n'], { type: 'application/x-ndjson' }),
    } as never);

    render(<HistoricalArchivesPanel />);

    fireEvent.change(screen.getByTestId('archive-start-date'), { target: { value: '2026-01-01' } });
    fireEvent.change(screen.getByTestId('archive-end-date'), { target: { value: '2026-03-31' } });
    fireEvent.click(screen.getByTestId('archive-download-button'));

    await waitFor(() => {
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/office/audit/archives/download',
        expect.objectContaining({
          params: { startDate: '2026-01-01', endDate: '2026-03-31' },
          responseType: 'blob',
        }),
      );
    });

    await waitFor(() => {
      expect(createObjectURL).toHaveBeenCalled();
      expect(click).toHaveBeenCalled();
      expect(revokeObjectURL).toHaveBeenCalledWith('blob:audit-archive');
    });
  });

  it('shows validation error when start is after end', async () => {
    render(<HistoricalArchivesPanel />);
    fireEvent.change(screen.getByTestId('archive-start-date'), { target: { value: '2026-06-01' } });
    fireEvent.change(screen.getByTestId('archive-end-date'), { target: { value: '2026-01-01' } });
    fireEvent.click(screen.getByTestId('archive-download-button'));

    expect(await screen.findByTestId('archive-download-error')).toHaveTextContent(
      'Start date must be on or before end date.',
    );
    expect(apiClient.get).not.toHaveBeenCalled();
  });
});
