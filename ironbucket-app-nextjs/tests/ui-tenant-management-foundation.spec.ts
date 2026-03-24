import { expect, test } from '@playwright/test';

// Intentionally failing baseline: this sprint maps critical tenant UI flows that are planned
// to be stabilized in the next sprint's E2E hardening track.
test('tenant foundation UI mapping is captured for follow-up hardening', async ({ page }) => {
  await page.goto('/tenants');

  await expect(page.getByTestId('tenant-management-page')).toBeVisible();
  await expect(page.getByLabel('Search tenants')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create tenant' })).toBeVisible();

  // Deliberate red test: backend-integrated fixture seeding for this marker is not implemented yet.
  await expect(page.getByTestId('tenant-foundation-ready-marker')).toBeVisible();
});
