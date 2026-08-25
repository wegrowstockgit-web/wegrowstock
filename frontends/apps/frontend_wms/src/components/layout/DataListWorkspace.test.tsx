import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { DataListWorkspace } from './DataListWorkspace';

describe('DataListWorkspace', () => {
  it('constrains the content pane so virtualized tables cannot grow unbounded', () => {
    render(
      <DataListWorkspace toolbar={<span>Filters</span>} data-layout="desktop">
        <div data-testid="virtual-child">grid</div>
      </DataListWorkspace>,
    );

    const shell = screen.getByTestId('data-list-workspace');
    expect(shell.className).toContain('min-h-0');
    expect(shell.className).toContain('flex-1');
    expect(shell.className).toContain('overflow-hidden');
    expect(shell).toHaveAttribute('data-layout', 'desktop');

    const content = screen.getByTestId('data-list-workspace-content');
    expect(content.className).toMatch(/min-h-0/);
    expect(content.className).toMatch(/flex-1/);
    expect(screen.getByTestId('virtual-child')).toBeTruthy();
    expect(screen.getByText('Filters')).toBeTruthy();
  });
});
