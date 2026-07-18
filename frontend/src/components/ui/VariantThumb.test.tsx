import { beforeAll, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { VariantThumb } from './VariantThumb';

vi.mock('@/components/ui/AuthenticatedImage', () => ({
  AuthenticatedImage: ({ alt, src }: { alt: string; src?: string | null }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img alt={alt} src={src ?? ''} data-testid="auth-image" />
  ),
}));

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute('open');
  };
});

describe('VariantThumb', () => {
  it('opens image preview toast when thumbnail is clicked', () => {
    render(
      <VariantThumb
        url="/api/v1/media/abc/content"
        alt="Widget"
        previewCaption="WIDGET-S"
      />,
    );

    fireEvent.click(screen.getByTestId('variant-thumb-preview'));
    expect(screen.getByTestId('image-preview-toast')).toBeInTheDocument();
    expect(screen.getByText('WIDGET-S')).toBeInTheDocument();
  });

  it('opens preview for placeholder when no media url', () => {
    render(<VariantThumb alt="Bolt" previewCaption="BOLT-M8" />);
    fireEvent.click(screen.getByTestId('variant-thumb-preview'));
    expect(screen.getByTestId('image-preview-toast')).toBeInTheDocument();
    expect(screen.getByText('No image available')).toBeInTheDocument();
  });

  it('does not render preview button when previewable is false', () => {
    render(<VariantThumb url="/x" alt="Kit" previewable={false} />);
    expect(screen.queryByTestId('variant-thumb-preview')).not.toBeInTheDocument();
  });
});
