import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Identity CRUD E2E — covers every Identity operation in schema.graphqls:
 *   Queries:   getIdentity, identity, getIdentityById, listIdentities, getUserPermissions
 *   Mutations: createIdentity, addUser, updateIdentity, updateUser, deleteIdentity, removeUser
 */
test('ui proves e2e coverage of all Identity GraphQL methods', async ({ page }) => {
  await page.goto('/e2e-identity');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full Identity scenario' }).click();

  const success = page.getByText(/All Identity GraphQL methods verified for actor: /);
  await expect(success).toBeVisible({ timeout: 60_000 });

  const resultCard = page.getByTestId('identity-scenario-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('allVerified: true');
  await expect(resultCard).toContainText('createIdentity: true');
  await expect(resultCard).toContainText('addUser: true');
  await expect(resultCard).toContainText('getIdentityById: true');
  await expect(resultCard).toContainText('identityAlias: true');
  await expect(resultCard).toContainText('getIdentity: true');
  await expect(resultCard).toContainText('listIdentities: true');
  await expect(resultCard).toContainText('getUserPermissions: true');
  await expect(resultCard).toContainText('updateIdentity: true');
  await expect(resultCard).toContainText('updateUser: true');
  await expect(resultCard).toContainText('deleteIdentity: true');
  await expect(resultCard).toContainText('removeUser: true');

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
  await fs.writeFile(path.join(outDir, 'identity-e2e-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'identity-e2e.json'),
    JSON.stringify({
      generatedAt: new Date().toISOString(),
      actor: 'alice',
      traceId: traceIdMatch?.[1]?.trim() ?? '',
      screenshotProof: 'identity-e2e-proof.png',
      checks: {
        createIdentity: true, addUser: true, getIdentityById: true, identityAlias: true,
        getIdentity: true, listIdentities: true, getUserPermissions: true,
        updateIdentity: true, updateUser: true, deleteIdentity: true, removeUser: true
      }
    }, null, 2),
    'utf-8'
  );
});
