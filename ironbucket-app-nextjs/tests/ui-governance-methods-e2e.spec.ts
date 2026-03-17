import { test, expect } from '@playwright/test';

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
});
