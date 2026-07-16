import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/Table';

describe('Table corporate header skin', () => {
  it('renders opaque blue header cells with white labels', () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>SKU</TableHead>
            <TableHead>Name</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>A-1</TableCell>
            <TableCell>Alpha</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    );

    const sku = screen.getByRole('columnheader', { name: /sku/i });
    expect(sku.className).toMatch(/table-head-cell/);
    expect(sku.className).toMatch(/sticky/);
    expect(sku.className).toMatch(/top-0/);
    expect(sku.className).toMatch(/bg-\[var\(--color-table-header\)\]/);
    expect(screen.getByText('SKU')).toBeVisible();
    expect(screen.getByText('Name')).toBeVisible();
  });

  it('uses overflow-x only so vertical sticky headers are not trapped', () => {
    const { container } = render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>SKU</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>A-1</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    );
    const shell = container.firstElementChild as HTMLElement;
    const classes = shell.className.split(/\s+/);
    expect(classes).toContain('overflow-x-auto');
    expect(classes).not.toContain('overflow-auto');
  });

  it('invokes onSort from sortable headers', () => {
    const onSort = vi.fn();
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead sortable sortKey="name" sort={null} onSort={onSort}>
              Name
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow>
            <TableCell>x</TableCell>
          </TableRow>
        </TableBody>
      </Table>,
    );

    fireEvent.click(screen.getByRole('button', { name: /name/i }));
    expect(onSort).toHaveBeenCalledWith('name');
  });
});
