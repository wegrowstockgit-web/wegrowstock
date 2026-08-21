import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { Pagination } from './Pagination';

describe('Pagination', () => {
  it('renders summary, page numbers, and size selector', () => {
    const onPage = vi.fn();
    const onSize = vi.fn();
    render(
      <Pagination
        page={2}
        totalPages={5}
        totalElements={120}
        size={50}
        onPageChange={onPage}
        onSizeChange={onSize}
      />,
    );
    expect(screen.getByTestId('pagination-summary')).toHaveTextContent('Showing 51–100 of 120');
    fireEvent.click(screen.getByTestId('pagination-prev'));
    expect(onPage).toHaveBeenCalledWith(1);
    fireEvent.click(screen.getByTestId('pagination-next'));
    expect(onPage).toHaveBeenCalledWith(3);
    fireEvent.click(screen.getByTestId('pagination-page-5'));
    expect(onPage).toHaveBeenCalledWith(5);
    fireEvent.change(screen.getByTestId('page-size'), { target: { value: '25' } });
    expect(onSize).toHaveBeenCalledWith(25);
  });

  it('disables previous on the first page', () => {
    render(
      <Pagination
        page={1}
        totalPages={1}
        totalElements={0}
        size={50}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    );
    expect(screen.getByTestId('pagination-summary')).toHaveTextContent('No rows');
    expect(screen.getByTestId('pagination-prev')).toBeDisabled();
    expect(screen.getByTestId('pagination-next')).toBeDisabled();
  });

  it('disables next on the last page', () => {
    const onPage = vi.fn();
    render(
      <Pagination
        page={3}
        totalPages={3}
        totalElements={75}
        size={25}
        onPageChange={onPage}
        onSizeChange={vi.fn()}
      />,
    );
    expect(screen.getByTestId('pagination-next')).toBeDisabled();
    expect(screen.getByTestId('pagination-prev')).not.toBeDisabled();
  });

  it('inserts ellipsis for wide page ranges', () => {
    render(
      <Pagination
        page={8}
        totalPages={20}
        totalElements={1000}
        size={50}
        onPageChange={vi.fn()}
        onSizeChange={vi.fn()}
      />,
    );
    expect(screen.getByTestId('pagination-page-1')).toBeInTheDocument();
    expect(screen.getByTestId('pagination-page-20')).toBeInTheDocument();
    expect(screen.getAllByText('…')).toHaveLength(2);
  });
});
