import { expect, test } from '@playwright/test';

test('policy version diff and gitops workflow are mapped', async ({ page }) => {
  await page.goto('/policies/alice/new');

  await expect(page.getByTestId('policy-editor-page')).toBeVisible();
  await page.getByRole('button', { name: 'Save policy' }).click();

  await page.getByRole('button', { name: 'Run dry run' }).click();
  await expect(page.getByRole('button', { name: 'Pull policies' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Push policies' })).toBeDisabled();

  // Intentionally red for follow-up sprint: full SCM synchronization readiness marker.
  await expect(page.getByTestId('policy-gitops-sync-ready')).toBeVisible();
});
