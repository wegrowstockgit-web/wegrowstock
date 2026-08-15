import { beforeAll, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ImagePreviewToast } from './ImagePreviewToast';

vi.mock('@/components/ui/AuthenticatedImage', () => ({
  AuthenticatedImage: ({ alt }: { alt: string }) => (
    // eslint-disable-next-line @next/next/no-img-element
    <img alt={alt} data-testid="preview-img" />
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

describe('ImagePreviewToast', () => {
  it('shows dialog content when open and closes via button', () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <ImagePreviewToast open onClose={onClose} url="/api/v1/media/x/content" alt="Box" caption="BOX-LRG" />,
    );

    const dialog = screen.getByTestId('image-preview-toast') as HTMLDialogElement;
    expect(dialog.open).toBe(true);
    expect(screen.getByTestId('preview-img')).toBeInTheDocument();
    expect(screen.getByText('BOX-LRG')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('image-preview-close'));
    expect(onClose).toHaveBeenCalled();

    rerender(
      <ImagePreviewToast open={false} onClose={onClose} url="/api/v1/media/x/content" alt="Box" />,
    );
    expect(screen.queryByTestId('image-preview-toast')).not.toBeInTheDocument();
  });
});
