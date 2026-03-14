import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test('object-browser baseline flow works live end-to-end', async ({ page }) => {
  await page.goto('/e2e-object-browser');

  await page.getByLabel('Active user').selectOption('alice');

  const bucketButtons = page.locator('button').filter({ hasText: /^default-/ });
  await expect(bucketButtons.first()).toBeVisible({ timeout: 45_000 });
  await bucketButtons.first().click();

  await page.getByLabel('Search objects').fill('');
  await page.getByRole('button', { name: 'Apply search' }).click();
  await page.getByLabel('Sort order').selectOption('desc');
  await page.getByRole('button', { name: 'Apply sort' }).click();

  const uploadName = `object-browser-baseline-${Date.now()}.txt`;
  await page.setInputFiles('#upload-file-input', {
    name: uploadName,
    mimeType: 'text/plain',
    buffer: Buffer.from('object-browser baseline live e2e payload')
  });

  await page.getByRole('button', { name: 'Upload' }).click();

  const uploadStatus = page.getByText(new RegExp(`Upload successful: ${uploadName}`));
  await expect(uploadStatus).toBeVisible({ timeout: 60_000 });

  await expect(page.getByText(uploadName).first()).toBeVisible({ timeout: 45_000 });

  await page.getByRole('button', { name: `Download ${uploadName}` }).click();
  await expect(page.getByText(new RegExp(`Download URL ready for ${uploadName}`))).toBeVisible({ timeout: 45_000 });

  await page.getByLabel('Search objects').fill(uploadName);
  await page.getByRole('button', { name: 'Apply search' }).click();
  await expect(page.getByText(uploadName).first()).toBeVisible({ timeout: 45_000 });

  await page.getByRole('button', { name: `Delete ${uploadName}` }).click();
  await expect(page.getByText(new RegExp(`Deleted ${uploadName}`))).toBeVisible({ timeout: 45_000 });
  await expect(page.getByText(uploadName).first()).not.toBeVisible({ timeout: 45_000 });

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
  await fs.writeFile(path.join(outDir, 'object-browser-baseline-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'object-browser-baseline-e2e.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        actor: 'alice',
        uploadedKey: uploadName,
        checks: {
          bucketBrowse: true,
          search: true,
          sort: true,
          upload: true,
          download: true,
          delete: true
        },
        screenshotProof: 'object-browser-baseline-proof.png'
      },
      null,
      2
    ),
    'utf-8'
  );
});
