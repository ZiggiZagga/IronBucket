import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

async function resolveOutputDir() {
  const preferredOutDir = '/workspaces/IronBucket/test-results/ui-e2e-traces';
  const fallbackOutDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  return fs
    .access('/workspaces/IronBucket/test-results')
    .then(() => preferredOutDir)
    .catch(() => fallbackOutDir);
}

test('cinematic showcase captures the refreshed shell and live object browser', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/');
  await expect(page.getByText('IronBucket now looks like a control plane, not a demo page.')).toBeVisible({ timeout: 45_000 });
  const overviewScreenshot = await page.screenshot({ fullPage: true, animations: 'disabled' });

  await page.goto('/e2e-object-browser');
  await expect(page.getByRole('heading', { name: 'Object Browser Baseline Scenario' })).toBeVisible({ timeout: 45_000 });
  await page.getByLabel('Active user').selectOption('alice');
  await page.waitForTimeout(2000);
  const browserScreenshot = await page.screenshot({ fullPage: true, animations: 'disabled' });

  const outDir = await resolveOutputDir();
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(path.join(outDir, 'ui-cinematic-overview-proof.png'), overviewScreenshot);
  await fs.writeFile(path.join(outDir, 'ui-cinematic-object-browser-proof.png'), browserScreenshot);
  await fs.writeFile(
    path.join(outDir, 'ui-cinematic-showcase.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        captures: [
          {
            route: '/',
            file: 'ui-cinematic-overview-proof.png'
          },
          {
            route: '/e2e-object-browser',
            file: 'ui-cinematic-object-browser-proof.png'
          }
        ]
      },
      null,
      2
    ),
    'utf-8'
  );
});