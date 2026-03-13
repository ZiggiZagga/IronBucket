import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test('live UI upload persists through backend round-trip', async ({ page }) => {
  await page.goto('/e2e-upload-live');

  await page.getByLabel('Active user').selectOption('alice');
  await page.setInputFiles('[data-testid="live-upload-file"]', {
    name: 'alice-live-ui.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('alice live ui persistence payload')
  });

  await page.getByRole('button', { name: 'Upload live' }).click();

  const success = page.getByText(/Live upload verified for /);
  await expect(success).toBeVisible({ timeout: 45_000 });

  const resultCard = page.getByTestId('live-upload-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('bucket: default-alice-files');
  await expect(resultCard).toContainText('verified: true');

  const keyLine = (await resultCard.textContent()) || '';
  const keyMatch = keyLine.match(/key:\s*([^\n\r]+)/);
  expect(keyMatch?.[1]?.trim().length ?? 0).toBeGreaterThan(0);

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
  await fs.writeFile(path.join(outDir, 'ui-live-upload-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'ui-live-upload-persistence.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        actor: 'alice',
        bucket: 'default-alice-files',
        key: keyMatch?.[1]?.trim() ?? '',
        verified: true,
        screenshotProof: 'ui-live-upload-proof.png'
      },
      null,
      2
    ),
    'utf-8'
  );
});