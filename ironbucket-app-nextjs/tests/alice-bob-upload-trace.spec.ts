import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

type UploadEvidence = {
  actorHeader: string;
  traceparentHeader: string;
  operationName: string;
  key: string;
  bucket: string;
};

test('alice and bob upload files through UI with trace headers', async ({ page }) => {
  const uploads: UploadEvidence[] = [];

  await page.route('**/graphql', async (route) => {
    const request = route.request();
    const headers = request.headers();
    const body = request.postDataJSON() as {
      operationName?: string;
      variables?: {
        key?: string;
        bucket?: string;
      };
    };

    if (body.operationName === 'UploadObject') {
      uploads.push({
        actorHeader: headers['x-ironbucket-actor'] ?? '',
        traceparentHeader: headers.traceparent ?? '',
        operationName: body.operationName,
        key: body.variables?.key ?? '',
        bucket: body.variables?.bucket ?? ''
      });

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            uploadObject: {
              key: body.variables?.key ?? 'uploaded.txt',
              bucket: body.variables?.bucket ?? 'default-bucket',
              size: 16,
              __typename: 'UploadObjectResult'
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

  await page.goto('/e2e-upload');

  await page.getByLabel('Active user').selectOption('alice');
  await page.setInputFiles('#upload-file-input', {
    name: 'alice-ui-upload.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('alice-upload')
  });
  await page.getByRole('button', { name: 'Upload' }).click();
  await expect(page.getByText('alice uploaded alice-ui-upload.txt')).toBeVisible();

  await page.getByLabel('Active user').selectOption('bob');
  await page.setInputFiles('#upload-file-input', {
    name: 'bob-ui-upload.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('bob-upload')
  });
  await page.getByRole('button', { name: 'Upload' }).click();
  await expect(page.getByText('bob uploaded bob-ui-upload.txt')).toBeVisible();

  expect(uploads).toHaveLength(2);

  expect(uploads[0].actorHeader).toBe('alice');
  expect(uploads[1].actorHeader).toBe('bob');

  const traceparentPattern = /^00-[0-9a-f]{32}-[0-9a-f]{16}-01$/;
  for (const upload of uploads) {
    expect(upload.traceparentHeader).toMatch(traceparentPattern);
  }

  const outDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(
    path.join(outDir, 'alice-bob-upload-trace.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        uploads
      },
      null,
      2
    ),
    'utf-8'
  );
});
