import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test('ui proves e2e coverage of governance GraphQL methods', async ({ page }) => {
  await page.goto('/e2e-governance-methods');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full governance method scenario' }).click();

  const success = page.getByText(/All governance GraphQL methods verified for tenant /);
  await expect(success).toBeVisible({ timeout: 90_000 });

  const resultCard = page.getByTestId('governance-methods-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('allMethodsVerified: true');

  const expectedChecks = [
    'getPolicy',
    'searchPolicies',
    'createTenant',
    'addTenant',
    'listTenants',
    'getTenant',
    'tenant',
    'getTenantById',
    'updateTenant',
    'createIdentity',
    'addUser',
    'listIdentities',
    'identity',
    'getIdentityById',
    'getIdentity',
    'updateIdentity',
    'updateUser',
    'removeUser',
    'getUserPermissions',
    'createPolicy',
    'addPolicy',
    'listPolicies',
    'getPolicyById',
    'updatePolicy',
    'evaluatePolicy',
    'validatePolicy',
    'deletePolicy',
    'getPolicyStatistics',
    'policyStats',
    'getUserActivitySummary',
    'userActivity',
    'getResourceAccessPatterns',
    'resourceAccess',
    'getAuditTrail',
    'getAuditLogs',
    'auditLogs',
    'filterAuditLogs',
    'getAuditLogById',
    'auditLogSubscription',
    'onAuditLog',
    'deleteIdentity',
    'deleteTenant'
  ];

  for (const check of expectedChecks) {
    await expect(resultCard).toContainText(`${check}: true`);
  }

  const screenshotBuffer = await page.screenshot({
    fullPage: true,
    animations: 'disabled'
  });

  const preferredOutDir = '/workspaces/IronBucket/test-results/ui-e2e-traces';
  const fallbackOutDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  const outDir = await fs
    .access('/workspaces/IronBucket/test-results')
    .then(() => preferredOutDir)
    .catch(() => fallbackOutDir);

  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(path.join(outDir, 'ui-governance-methods-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'ui-governance-methods-e2e.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        actor: 'alice',
        allMethodsVerified: true,
        checkCount: expectedChecks.length,
        screenshotProof: 'ui-governance-methods-proof.png'
      },
      null,
      2
    ),
    'utf-8'
  );
});
