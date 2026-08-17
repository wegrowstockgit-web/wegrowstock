import { expect, test } from './fixtures/roleFixture';

test.describe('i18n persistence and B2B/Mesh office navigation', () => {
  test('owner sidebar shows Mesh and B2B office routes, never /showroom', async ({ ownerPage }) => {
    await ownerPage.goto('/dashboard');
    await expect(ownerPage.getByTestId('app-shell')).toBeVisible({ timeout: 20_000 });
    await expect(ownerPage.getByTestId('nav-mesh-network')).toBeVisible({ timeout: 15_000 });

    await ownerPage.getByTestId('nav-category-outbound').click();
    await expect(ownerPage.getByRole('link', { name: 'Sales Orders', exact: true })).toBeVisible();
    await expect(ownerPage.getByRole('link', { name: 'Customers', exact: true })).toBeVisible();
    await expect(ownerPage.getByTestId('nav-b2b-rfqs')).toHaveCount(0);
    await expect(ownerPage.getByTestId('nav-b2b-customers')).toHaveCount(0);

    await expect(ownerPage.locator('a[href^="/showroom"]')).toHaveCount(0);

    await ownerPage.getByTestId('nav-mesh-network').click();
    await expect(ownerPage).toHaveURL(/\/mesh-network/, { timeout: 15_000 });
    await expect(ownerPage.getByTestId('mesh-network-page')).toBeVisible();

    await ownerPage.getByTestId('nav-category-outbound').click();
    await ownerPage.getByRole('link', { name: 'Customers', exact: true }).click();
    await expect(ownerPage).toHaveURL(/\/customers/, { timeout: 15_000 });
    await expect(ownerPage.getByTestId('pending-applications-tab')).toBeVisible();
  });

  test('profile language change updates the whole UI without reload', async ({ ownerPage }) => {
    const restore = async () => {
      await ownerPage.request.patch('/api/v1/users/me/profile', {
        data: { preferredLanguage: 'en', localeLanguage: 'en' },
      });
    };

    try {
      await restore();
      await ownerPage.goto('/settings/profile');
      await expect(ownerPage.getByTestId('profile-settings-page')).toBeVisible({ timeout: 20_000 });
      const form = ownerPage.getByTestId('personal-profile-form');
      await form.getByTestId('language-select').selectOption('es');
      const save = ownerPage.waitForResponse(
        (r) => r.url().includes('/api/v1/users/me/profile') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await ownerPage.getByTestId('save-personal-settings').click();
      expect((await save).ok()).toBeTruthy();
      await expect(ownerPage.getByTestId('personal-profile-form').getByTestId('language-select')).toHaveValue('es');
      await expect(ownerPage.getByTestId('save-personal-settings')).toHaveText(
        /Guardar ajustes personales/i,
      );
      await expect(ownerPage.getByTestId('nav-mesh-network')).toHaveAttribute(
        'aria-label',
        /Red Mesh/i,
      );
    } finally {
      await restore();
    }
  });

  test('workspace language save applies immediately and toasts', async ({ ownerPage }) => {
    try {
      await ownerPage.goto('/settings?tab=profile');
      await expect(ownerPage.getByTestId('settings-page')).toBeVisible({ timeout: 20_000 });
      await ownerPage.getByTestId('org-locale-language').selectOption('fr');
      const patch = ownerPage.waitForResponse(
        (r) => r.url().includes('/api/v1/settings') && r.request().method() === 'PATCH',
        { timeout: 30_000 },
      );
      await ownerPage.getByTestId('save-workspace-language').click();
      expect((await patch).ok()).toBeTruthy();
      await expect(ownerPage.getByTestId('app-toast')).toContainText(
        /Langue par défaut de l’espace de travail mise à jour|Workspace default language updated/i,
      );
      await expect(ownerPage.getByRole('heading', { name: 'Paramètres', exact: true })).toBeVisible({
        timeout: 10_000,
      });
    } finally {
      await ownerPage.request.patch('/api/v1/settings', {
        data: { locale_language: 'en' },
      });
      await ownerPage.request.patch('/api/v1/users/me/profile', {
        data: { preferredLanguage: 'en', localeLanguage: 'en' },
      });
    }
  });
});
