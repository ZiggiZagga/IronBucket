import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Policy CRUD E2E — covers every Policy operation in schema.graphqls:
 *   Queries:   getPolicy, getPolicyById, listPolicies, searchPolicies, evaluatePolicy
 *   Mutations: createPolicy, addPolicy, validatePolicy, updatePolicy, deletePolicy
 */
test('ui proves e2e coverage of all Policy GraphQL methods', async ({ page }) => {
  await page.goto('/e2e-policy');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full Policy scenario' }).click();

  const success = page.getByText(/All Policy GraphQL methods verified for actor: /);
  await expect(success).toBeVisible({ timeout: 60_000 });

  const resultCard = page.getByTestId('policy-scenario-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('allVerified: true');
  await expect(resultCard).toContainText('createPolicy: true');
  await expect(resultCard).toContainText('addPolicy: true');
  await expect(resultCard).toContainText('listPolicies: true');
  await expect(resultCard).toContainText('searchPolicies: true');
  await expect(resultCard).toContainText('getPolicy: true');
  await expect(resultCard).toContainText('getPolicyById: true');
  await expect(resultCard).toContainText('validatePolicy: true');
  await expect(resultCard).toContainText('evaluatePolicy: true');
  await expect(resultCard).toContainText('updatePolicy: true');
  await expect(resultCard).toContainText('deletePolicy: true');

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
  await fs.writeFile(path.join(outDir, 'policy-e2e-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'policy-e2e.json'),
    JSON.stringify({
      generatedAt: new Date().toISOString(),
      actor: 'alice',
      traceId: traceIdMatch?.[1]?.trim() ?? '',
      screenshotProof: 'policy-e2e-proof.png',
      checks: {
        createPolicy: true, addPolicy: true, listPolicies: true, searchPolicies: true,
        getPolicy: true, getPolicyById: true, validatePolicy: true, evaluatePolicy: true,
        updatePolicy: true, deletePolicy: true
      }
    }, null, 2),
    'utf-8'
  );
});
