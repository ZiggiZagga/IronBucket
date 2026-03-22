import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Tenant CRUD E2E — covers every Tenant operation in schema.graphqls:
 *   Queries:   getTenant, tenant (alias), getTenantById, listTenants
 *   Mutations: createTenant, addTenant, updateTenant, deleteTenant
 */
test('ui proves e2e coverage of all Tenant GraphQL methods', async ({ page }) => {
  await page.goto('/e2e-tenant');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full Tenant scenario' }).click();

  const success = page.getByText(/All Tenant GraphQL methods verified for actor: /);
  await expect(success).toBeVisible({ timeout: 60_000 });

  const resultCard = page.getByTestId('tenant-scenario-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('allVerified: true');
  await expect(resultCard).toContainText('createTenant: true');
  await expect(resultCard).toContainText('addTenant: true');
  await expect(resultCard).toContainText('getTenantById: true');
  await expect(resultCard).toContainText('getTenant: true');
  await expect(resultCard).toContainText('tenantAlias: true');
  await expect(resultCard).toContainText('listTenants: true');
  await expect(resultCard).toContainText('updateTenant: true');
  await expect(resultCard).toContainText('deleteTenant: true');

  const cardText = (await resultCard.textContent()) ?? '';
  const traceIdMatch = cardText.match(/traceId:\s*([a-f0-9]{32})/i);
  expect(traceIdMatch?.[1]?.trim().length ?? 0).toBe(32);

  const screenshotBuffer = await page.screenshot({ fullPage: true, animations: 'disabled' });

  const preferredOutDir = '/workspaces/IronBucket/test-results/ui-e2e-traces';
  const fallbackOutDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  const outDir = await fs
    .access('/workspaces/IronBucket/test-results')
    .then(() => preferredOutDir)
    .catch(() => fallbackOutDir);
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(path.join(outDir, 'tenant-e2e-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'tenant-e2e.json'),
    JSON.stringify({
      generatedAt: new Date().toISOString(),
      actor: 'alice',
      traceId: traceIdMatch?.[1]?.trim() ?? '',
      screenshotProof: 'tenant-e2e-proof.png',
      checks: {
        createTenant: true, addTenant: true, getTenantById: true, getTenant: true,
        tenantAlias: true, listTenants: true, updateTenant: true, deleteTenant: true
      }
    }, null, 2),
    'utf-8'
  );
});
