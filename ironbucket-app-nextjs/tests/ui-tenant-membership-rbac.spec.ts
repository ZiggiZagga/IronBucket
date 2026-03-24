import { expect, test } from '@playwright/test';

// Intentionally failing baseline for Sprint follow-up work: RBAC + membership sync against
// Keycloak and tenant service needs dedicated backend fixture orchestration.
test('tenant membership and RBAC mapping is captured for next sprint', async ({ page }) => {
  await page.goto('/tenants');
  await page.getByRole('button', { name: 'Create tenant' }).click();

  await page.getByLabel('Tenant name').fill(`playwright-tenant-${Date.now()}`);
  await page.getByRole('button', { name: 'Create tenant' }).click();

  const firstMembershipLink = page.getByRole('link', { name: 'Members' }).first();
  await firstMembershipLink.click();

  await expect(page.getByTestId('tenant-detail-page')).toBeVisible();
  await expect(page.getByLabel('User ID')).toBeVisible();

  // Deliberate red test: expected Keycloak-synced member row is not present yet.
  await expect(page.getByTestId('keycloak-membership-sync-ready')).toBeVisible();
});
