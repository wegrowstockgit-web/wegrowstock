import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SavedFilterViews } from './SavedFilterViews';
import { ToastProvider } from './Toast';

describe('SavedFilterViews', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.stubGlobal('prompt', vi.fn());
  });

  it('saves a named view with toast and without window.prompt', () => {
    const onApply = vi.fn();
    render(
      <ToastProvider>
        <SavedFilterViews
          storageKey="test-filters"
          activeFilters={{ lowStock: '1' }}
          onApply={onApply}
          defaultPresets={[
            { id: 'all', label: 'All', filters: {} },
            { id: 'low', label: 'Low stock', filters: { lowStock: '1' } },
          ]}
        />
      </ToastProvider>
    );

    fireEvent.click(screen.getByRole('button', { name: /Save view/i }));
    fireEvent.change(screen.getByLabelText('Filter view name'), { target: { value: 'Custom low' } });
    fireEvent.click(screen.getByRole('button', { name: /^Save$/i }));

    expect(window.prompt).not.toHaveBeenCalled();
    expect(screen.getByRole('tab', { name: 'Custom low' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/Saved view/);
  });
});
