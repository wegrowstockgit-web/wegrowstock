import { expect, test } from '../fixtures/roleFixture';
import { contextForRole } from './helpers';

/**
 * Journey 33 — Clickable product thumbnails open a responsive image preview toast.
 */
test.describe('Journey 33: Product image preview toast', () => {
  test.setTimeout(180_000);

  test('products and production orders open image preview on thumb click', async ({
    browser,
  }) => {
    const owner = await contextForRole(browser, 'owner');
    try {
      await owner.page.goto('/products');
      await expect(owner.page.getByRole('heading', { name: 'Products', exact: true })).toBeVisible({
        timeout: 30_000,
      });

      const productThumb = owner.page.getByTestId('variant-thumb-preview').first();
      await expect(productThumb).toBeVisible({ timeout: 20_000 });
      await productThumb.click();
      const productPreview = owner.page.getByTestId('image-preview-toast');
      await expect(productPreview).toBeVisible();
      await expect(productPreview).toHaveCount(1);
      await owner.page.getByTestId('image-preview-close').click();
      await expect(owner.page.getByTestId('image-preview-toast')).toHaveCount(0);

      await owner.page.goto('/manufacturing/orders');
      await expect(
        owner.page.getByRole('heading', { name: 'Production Orders', exact: true }),
      ).toBeVisible({ timeout: 30_000 });

      const moThumb = owner.page.getByTestId('variant-thumb-preview').first();
      await expect(moThumb).toBeVisible({ timeout: 20_000 });
      await moThumb.click();
      await expect(owner.page.getByTestId('image-preview-toast')).toBeVisible();
      await expect(owner.page.getByTestId('image-preview-toast')).toHaveCount(1);
      await owner.page.keyboard.press('Escape');
      await expect(owner.page.getByTestId('image-preview-toast')).toHaveCount(0);
    } finally {
      await owner.close();
    }
  });
});
