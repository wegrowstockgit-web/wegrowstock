import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DataListToolbar, DensityToggle } from './DensityToggle';
import { ColumnVisibilityMenu } from './ColumnVisibilityMenu';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { selectGridLayout, useGridColumnStore } from '@/stores/gridColumnStore';

const GRID = 'products';

describe('DensityToggle', () => {
  beforeEach(() => {
    localStorage.clear();
    usePreferencesStore.setState({ densityMode: 'cozy', tableDensityById: {} });
    useGridColumnStore.setState({
      layouts: {
        [GRID]: {
          columnVisibility: { sku: true, barcode: true },
          pinnedColumns: ['sku'],
          columnOrder: ['sku', 'barcode'],
        },
      },
    });
  });

  it('keeps density on the table gridId and does not change other tables', () => {
    render(
      <div>
        <DensityToggle gridId="purchase-orders" />
        <DensityToggle gridId="suppliers" />
      </div>,
    );
    const toggles = screen.getAllByTestId('density-toggle');
    fireEvent.click(toggles[0]);
    fireEvent.click(screen.getByTestId('density-option-compact'));
    expect(usePreferencesStore.getState().tableDensityById['purchase-orders']).toBe('compact');
    expect(usePreferencesStore.getState().tableDensityById.suppliers).toBeUndefined();
    expect(usePreferencesStore.getState().densityMode).toBe('cozy');
  });

  it('without a gridId, still updates the global profile default', () => {
    render(<DensityToggle />);
    fireEvent.click(screen.getByTestId('density-toggle'));
    fireEvent.click(screen.getByTestId('density-option-compact'));
    expect(usePreferencesStore.getState().densityMode).toBe('compact');
  });

  it('closes the menu on outside click', async () => {
    render(
      <div>
        <DensityToggle />
        <button type="button">Outside</button>
      </div>,
    );
    fireEvent.click(screen.getByTestId('density-toggle'));
    expect(screen.getByTestId('density-option-spacious')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }));
    await waitFor(() => {
      expect(screen.queryByTestId('density-option-spacious')).not.toBeInTheDocument();
    });
  });

  it('renders column visibility toggle in DataListToolbar and toggles columns', async () => {
    const user = userEvent.setup();
    render(
      <DataListToolbar
        gridId={GRID}
        columnItems={[
          { id: 'sku', label: 'SKU' },
          { id: 'barcode', label: 'Barcode' },
        ]}
      />,
    );
    expect(screen.getByTestId('column-visibility-toggle')).toBeInTheDocument();

    await user.click(screen.getByTestId('column-visibility-toggle'));
    await waitFor(() => {
      expect(screen.getByText('Toggle columns')).toBeInTheDocument();
    });
    await user.click(screen.getByTestId('column-visibility-barcode'));
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).columnVisibility.barcode).toBe(
      false,
    );
  });
});

describe('ColumnVisibilityMenu', () => {
  beforeEach(() => {
    useGridColumnStore.setState({
      layouts: {
        [GRID]: {
          columnVisibility: { sku: true, barcode: true },
          pinnedColumns: ['sku'],
          columnOrder: ['sku', 'barcode'],
        },
      },
    });
  });

  it('pins and unpins from the menu', async () => {
    const user = userEvent.setup();
    render(
      <ColumnVisibilityMenu
        gridId={GRID}
        columns={[
          { id: 'sku', label: 'SKU' },
          { id: 'barcode', label: 'Barcode' },
        ]}
      />,
    );
    await user.click(screen.getByTestId('column-visibility-toggle'));
    expect(screen.getByTestId('column-visibility-menu')).toBeInTheDocument();
    expect(screen.getByTestId('column-visibility-menu').className).toMatch(/overflow-y-auto/);
    await user.click(screen.getByTestId('column-pin-barcode'));
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).pinnedColumns).toContain(
      'barcode',
    );
    await user.click(screen.getByTestId('column-pin-barcode'));
    expect(selectGridLayout(useGridColumnStore.getState(), GRID).pinnedColumns).not.toContain(
      'barcode',
    );
  });
});
