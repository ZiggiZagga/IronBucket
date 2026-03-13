import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

type OperationEvidence = {
  operationName: string;
  actorHeader: string;
  traceparentHeader: string;
  variables?: Record<string, unknown>;
};

test('object browser baseline: buckets, objects, upload, download, delete, search, sort, trace headers', async ({ page }) => {
  const operations: OperationEvidence[] = [];
  const featureCoverage = new Set<string>();

  const objectStore: Record<string, Array<{ key: string; size: number; lastModified: string }>> = {
    'alpha-bucket': [
      { key: 'zeta-report.pdf', size: 200, lastModified: '2026-03-13T01:00:00Z' },
      { key: 'alpha-notes.txt', size: 120, lastModified: '2026-03-13T02:00:00Z' }
    ],
    'beta-bucket': [{ key: 'beta-log.txt', size: 80, lastModified: '2026-03-13T03:00:00Z' }]
  };

  await page.route('**/graphql', async (route) => {
    const request = route.request();
    const headers = request.headers();
    const body = request.postDataJSON() as {
      operationName?: string;
      variables?: {
        bucket?: string;
        query?: string;
        sortBy?: string;
        sortDirection?: string;
        key?: string;
      };
    };

    if (body.operationName) {
      operations.push({
        operationName: body.operationName,
        actorHeader: headers['x-ironbucket-actor'] ?? '',
        traceparentHeader: headers.traceparent ?? '',
        variables: body.variables
      });
    }

    if (body.operationName === 'GetBuckets') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            listBuckets: [
              { name: 'alpha-bucket', creationDate: '2026-03-13T00:00:00Z', ownerTenant: 'tenant-a' },
              { name: 'beta-bucket', creationDate: '2026-03-13T00:00:00Z', ownerTenant: 'tenant-b' }
            ]
          }
        })
      });
      return;
    }

    if (body.operationName === 'ListObjects') {
      const bucket = body.variables?.bucket ?? 'alpha-bucket';
      const q = (body.variables?.query ?? '').toLowerCase();
      const objects = objectStore[bucket] ?? [];
      const filtered = objects.filter((item) => item.key.toLowerCase().includes(q));
      const sorted = [...filtered].sort((a, b) => a.key.localeCompare(b.key));
      const direction = body.variables?.sortDirection ?? 'asc';
      const finalList = direction === 'desc' ? sorted.reverse() : sorted;

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            listObjects: finalList
          }
        })
      });
      return;
    }

    if (body.operationName === 'UploadObject') {
      const bucket = body.variables?.bucket ?? 'alpha-bucket';
      const key = body.variables?.key ?? 'uploaded.txt';
      const exists = (objectStore[bucket] ?? []).some((item) => item.key === key);
      if (!exists) {
        objectStore[bucket] = [
          ...(objectStore[bucket] ?? []),
          { key, size: 16, lastModified: '2026-03-13T04:00:00Z' }
        ];
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            uploadObject: {
              key,
              bucket,
              size: 16,
              __typename: 'UploadObjectResult'
            }
          }
        })
      });
      return;
    }

    if (body.operationName === 'DeleteObject') {
      const bucket = body.variables?.bucket ?? 'alpha-bucket';
      const key = body.variables?.key ?? '';
      objectStore[bucket] = (objectStore[bucket] ?? []).filter((item) => item.key !== key);

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ data: { deleteObject: true } })
      });
      return;
    }

    if (body.operationName === 'DownloadObject') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            downloadObject: {
              url: 'https://example.invalid/download/alpha-notes.txt'
            }
          }
        })
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ data: {} })
    });
  });

  await page.goto('/e2e-object-browser');

  await page.getByLabel('Active user').selectOption('alice');
  featureCoverage.add('actor-switch');

  await expect(page.getByRole('heading', { name: 'Object Browser Baseline Scenario' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'alpha-bucket' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'beta-bucket' })).toBeVisible();

  await page.getByRole('button', { name: 'alpha-bucket' }).click();
  featureCoverage.add('bucket-selection');

  await expect(page.getByText('zeta-report.pdf', { exact: true })).toBeVisible();
  await expect(page.getByText('alpha-notes.txt', { exact: true })).toBeVisible();
  featureCoverage.add('object-list');

  await page.getByLabel('Search objects').fill('alpha');
  await page.getByRole('button', { name: 'Apply search' }).click();
  await expect(page.getByText('alpha-notes.txt', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download zeta-report.pdf' })).toHaveCount(0);
  featureCoverage.add('search');

  await page.getByLabel('Search objects').fill('');
  await page.getByRole('button', { name: 'Apply search' }).click();

  await page.getByLabel('Sort order').selectOption('desc');
  await page.getByRole('button', { name: 'Apply sort' }).click();
  await expect(page.locator('ul li span').first()).toHaveText('zeta-report.pdf');
  featureCoverage.add('sort');

  await page.setInputFiles('#upload-file-input', {
    name: 'alice-from-ui.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('alice upload payload')
  });
  await page.getByRole('button', { name: 'Upload' }).click();
  await expect(page.getByText('Upload successful: alice-from-ui.txt')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download alice-from-ui.txt' })).toBeVisible();
  featureCoverage.add('upload');

  await page.getByRole('button', { name: 'Download alice-from-ui.txt' }).click();
  await expect(page.getByText('Download URL ready for alice-from-ui.txt')).toBeVisible();
  featureCoverage.add('download');

  await page.getByRole('button', { name: 'Delete alice-from-ui.txt' }).click();
  await expect(page.getByText('Deleted alice-from-ui.txt')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Download alice-from-ui.txt' })).toHaveCount(0);
  featureCoverage.add('delete');

  await page.getByRole('button', { name: 'beta-bucket' }).click();
  await expect(page.getByText('beta-log.txt', { exact: true })).toBeVisible();

  await page.getByLabel('Search objects').fill('not-found-key');
  await page.getByRole('button', { name: 'Apply search' }).click();
  await expect(page.getByText('No objects found.')).toBeVisible();
  featureCoverage.add('empty-state');

  await page.getByLabel('Active user').selectOption('bob');
  await page.evaluate(() => {
    window.localStorage.setItem('ironbucket.e2e.actor', 'bob');
  });
  await page.getByRole('button', { name: 'alpha-bucket' }).click();
  featureCoverage.add('trace-with-actor');

  const tracePattern = /^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/;
  const requiredOps = ['GetBuckets', 'ListObjects', 'UploadObject', 'DownloadObject', 'DeleteObject'];

  for (const op of requiredOps) {
    const seen = operations.find((item) => item.operationName === op);
    expect(seen, `missing operation ${op}`).toBeTruthy();
    expect(seen?.actorHeader).toBe('alice');
    expect(seen?.traceparentHeader ?? '').toMatch(tracePattern);
  }

  const bobListCall = operations.find(
    (item) => item.operationName === 'ListObjects' && item.actorHeader === 'bob'
  );
  expect(bobListCall, 'missing ListObjects call for bob actor').toBeTruthy();
  expect(bobListCall?.traceparentHeader ?? '').toMatch(tracePattern);

  const searchListCall = operations.find(
    (item) => item.operationName === 'ListObjects' && item.variables?.query === 'alpha'
  );
  expect(searchListCall, 'missing ListObjects call with search query').toBeTruthy();

  const sortListCall = operations.find(
    (item) => item.operationName === 'ListObjects' && item.variables?.sortDirection === 'desc'
  );
  expect(sortListCall, 'missing ListObjects call with desc sort').toBeTruthy();

  const expectedCoverage = [
    'actor-switch',
    'bucket-selection',
    'object-list',
    'search',
    'sort',
    'upload',
    'download',
    'delete',
    'empty-state',
    'trace-with-actor'
  ];

  for (const coveredFeature of expectedCoverage) {
    expect(featureCoverage.has(coveredFeature), `feature not covered: ${coveredFeature}`).toBeTruthy();
  }

  const outDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(
    path.join(outDir, 'object-browser-baseline-trace.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        operations,
        expectedCoverage,
        coveredFeatures: Array.from(featureCoverage)
      },
      null,
      2
    ),
    'utf-8'
  );
});
