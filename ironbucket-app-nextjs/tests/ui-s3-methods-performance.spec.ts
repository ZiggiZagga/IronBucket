import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

function parseMetric(text: string, label: string): number {
  const pattern = new RegExp(`${label}:\\s*([0-9]+(?:\\.[0-9]+)?)`);
  const match = text.match(pattern);
  if (!match?.[1]) {
    throw new Error(`Missing metric: ${label}`);
  }
  return Number(match[1]);
}

test('ui captures MinIO operation latency and throughput for full S3 method flow', async ({ page }) => {
  await page.goto('/e2e-s3-methods');

  await page.getByLabel('Active user').selectOption('alice');
  await page.getByRole('button', { name: 'Run full S3 method scenario' }).click();

  const success = page.getByText(/All S3 GraphQL methods verified for /);
  await expect(success).toBeVisible({ timeout: 60_000 });

  const resultCard = page.getByTestId('s3-methods-result');
  await expect(resultCard).toBeVisible();

  const cardText = (await resultCard.textContent()) || '';
  const minioOperationCount = parseMetric(cardText, 'minioOperationCount');
  const minioTotalOperationTimeMs = parseMetric(cardText, 'minioTotalOperationTimeMs');
  const minioOperationsPerSecond = parseMetric(cardText, 'minioOperationsPerSecond');

  expect(minioOperationCount).toBeGreaterThanOrEqual(11);
  expect(minioTotalOperationTimeMs).toBeGreaterThan(0);
  expect(minioOperationsPerSecond).toBeGreaterThan(0);

  const expectedOps = [
    'createBucket',
    'listBuckets',
    'getBucket',
    'uploadObject',
    'listObjects',
    'getObject',
    'downloadObject',
    'deleteObject',
    'listObjectsAfterDelete',
    'deleteBucket',
    'listBucketsAfterDelete'
  ];

  const operationLatencies: Record<string, number> = {};
  for (const op of expectedOps) {
    const value = parseMetric(cardText, `operation\\.${op}\\.ms`);
    expect(value).toBeGreaterThanOrEqual(0);
    operationLatencies[op] = value;
  }

  const preferredOutDir = '/workspaces/IronBucket/test-results/ui-e2e-traces';
  const fallbackOutDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  const outDir = await fs
    .access('/workspaces/IronBucket/test-results')
    .then(() => preferredOutDir)
    .catch(() => fallbackOutDir);

  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(
    path.join(outDir, 'ui-s3-methods-performance.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        actor: 'alice',
        minioOperationCount,
        minioTotalOperationTimeMs,
        minioOperationsPerSecond,
        operationLatenciesMs: operationLatencies
      },
      null,
      2
    ),
    'utf-8'
  );
});
