import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

/**
 * Audit & Stats E2E — covers every Audit/Stats operation in schema.graphqls:
 *   Queries: getAuditTrail, getAuditLogs, auditLogs (alias), filterAuditLogs,
 *            getAuditLogById, getPolicyStatistics, policyStats (alias),
 *            getUserActivitySummary, userActivity (alias),
 *            getResourceAccessPatterns, resourceAccess (alias)
 *   Subscriptions are exercised via manual GraphQL call separately.
 * Audit data is sourced live from Loki (real LGTM stack required).
 */
test('ui proves e2e coverage of all Audit & Stats GraphQL methods', async ({ page }) => {
  await page.goto('/e2e-audit');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full Audit & Stats scenario' }).click();

  const success = page.getByText(/All Audit GraphQL methods verified for actor: /);
  await expect(success).toBeVisible({ timeout: 60_000 });

  const resultCard = page.getByTestId('audit-scenario-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('allVerified: true');
  await expect(resultCard).toContainText('getAuditTrail: true');
  await expect(resultCard).toContainText('getAuditLogs: true');
  await expect(resultCard).toContainText('auditLogsAlias: true');
  await expect(resultCard).toContainText('filterAuditLogs: true');
  await expect(resultCard).toContainText('getAuditLogById: true');
  await expect(resultCard).toContainText('getPolicyStatistics: true');
  await expect(resultCard).toContainText('policyStatsAlias: true');
  await expect(resultCard).toContainText('getUserActivitySummary: true');
  await expect(resultCard).toContainText('userActivityAlias: true');
  await expect(resultCard).toContainText('getResourceAccessPatterns: true');
  await expect(resultCard).toContainText('resourceAccessAlias: true');

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
  await fs.writeFile(path.join(outDir, 'audit-e2e-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'audit-e2e.json'),
    JSON.stringify({
      generatedAt: new Date().toISOString(),
      actor: 'alice',
      traceId: traceIdMatch?.[1]?.trim() ?? '',
      screenshotProof: 'audit-e2e-proof.png',
      checks: {
        getAuditTrail: true, getAuditLogs: true, auditLogsAlias: true,
        filterAuditLogs: true, getAuditLogById: true,
        getPolicyStatistics: true, policyStatsAlias: true,
        getUserActivitySummary: true, userActivityAlias: true,
        getResourceAccessPatterns: true, resourceAccessAlias: true
      }
    }, null, 2),
    'utf-8'
  );
});
