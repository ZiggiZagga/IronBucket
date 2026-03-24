import { expect, test } from '@playwright/test';

test('policy engine list and editor flow are mapped', async ({ page }) => {
  await page.goto('/policies/alice');

  await expect(page.getByTestId('policy-list-page')).toBeVisible();
  await page.getByRole('link', { name: 'New policy' }).click();

  await expect(page.getByTestId('policy-editor-page')).toBeVisible();
  await expect(page.getByLabel('Policy source')).toBeVisible();

  await page.getByRole('button', { name: 'Save + validate' }).click();
  await page.getByRole('button', { name: 'Save policy' }).click();

  // Intentionally red for next sprint integration completion: Git-backed persistence marker.
  await expect(page.getByTestId('policy-git-backed-ready')).toBeVisible();
});
