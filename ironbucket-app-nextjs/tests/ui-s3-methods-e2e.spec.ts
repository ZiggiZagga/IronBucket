import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test('ui proves e2e coverage of all exposed S3 GraphQL object-browser methods', async ({ page }) => {
  await page.goto('/e2e-s3-methods');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full S3 method scenario' }).click();

  const success = page.getByText(/All S3 GraphQL methods verified for /);
  await expect(success).toBeVisible({ timeout: 60_000 });

  const resultCard = page.getByTestId('s3-methods-result');
  await expect(resultCard).toBeVisible();
  await expect(resultCard).toContainText('actor: alice');
  await expect(resultCard).toContainText('bucket: default-alice-methods-');
  await expect(resultCard).toContainText('allMethodsVerified: true');
  await expect(resultCard).toContainText('createBucket: true');
  await expect(resultCard).toContainText('listBuckets: true');
  await expect(resultCard).toContainText('getBucket: true');
  await expect(resultCard).toContainText('uploadObject: true');
  await expect(resultCard).toContainText('listObjects: true');
  await expect(resultCard).toContainText('getObject: true');
  await expect(resultCard).toContainText('getBucketRoutingDecision: true');
  await expect(resultCard).toContainText('downloadObject: true');
  await expect(resultCard).toContainText('deleteObject: true');
  await expect(resultCard).toContainText('deleteBucket: true');

  const cardText = (await resultCard.textContent()) || '';

  const bucketMatch = cardText.match(/bucket:\s*(.*?)\s*key:/s);
  const keyMatch = cardText.match(/key:\s*(.*?)\s*traceId:/s);
  const traceIdMatch = cardText.match(/traceId:\s*([a-f0-9]{32})/i);

  expect(keyMatch?.[1]?.trim().length ?? 0).toBeGreaterThan(0);
  expect(bucketMatch?.[1]?.trim().length ?? 0).toBeGreaterThan(0);
  expect(traceIdMatch?.[1]?.trim().length ?? 0).toBe(32);

  const screenshotBuffer = await page.screenshot({
    fullPage: true,
    animations: 'disabled'
  });

  await page.setInputFiles('[data-testid="screenshot-proof-file"]', {
    name: 'ui-s3-methods-proof.png',
    mimeType: 'image/png',
    buffer: screenshotBuffer
  });

  await page.getByTestId('upload-screenshot-proof').click();

  const proofResult = page.getByTestId('screenshot-proof-result');
  await expect(proofResult).toBeVisible({ timeout: 60_000 });
  await expect(proofResult).toContainText('proofStored: true');
  await expect(proofResult).toContainText('proofBucket: default-alice-proofs');

  const preview = page.getByTestId('screenshot-proof-preview');
  await expect(preview).toBeVisible();

  const preferredOutDir = '/workspaces/IronBucket/test-results/ui-e2e-traces';
  const fallbackOutDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  const outDir = await fs
    .access('/workspaces/IronBucket/test-results')
    .then(() => preferredOutDir)
    .catch(() => fallbackOutDir);
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(path.join(outDir, 'ui-s3-methods-proof.png'), screenshotBuffer);
  await fs.writeFile(
    path.join(outDir, 'ui-s3-methods-e2e.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        actor: 'alice',
        bucket: bucketMatch?.[1]?.trim() ?? '',
        key: keyMatch?.[1]?.trim() ?? '',
        traceId: traceIdMatch?.[1]?.trim() ?? '',
        screenshotProof: {
          file: 'ui-s3-methods-proof.png',
          bucketPrefix: 'default-alice-proofs'
        },
        checks: {
          createBucket: true,
          listBuckets: true,
          getBucket: true,
          uploadObject: true,
          listObjects: true,
          getObject: true,
          getBucketRoutingDecision: true,
          downloadObject: true,
          deleteObject: true,
          deleteBucket: true
        }
      },
      null,
      2
    ),
    'utf-8'
  );
});
