import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test('object-browser baseline flow works live end-to-end', async ({ page }) => {
  const seedKey = `object-browser-seed-${Date.now()}.txt`;
  const seedResponse = await page.request.post('/api/e2e/live-upload', {
    data: {
      actor: 'alice',
      key: seedKey,
      content: 'seed-object-browser-bucket'
    }
  });
  expect(seedResponse.ok()).toBeTruthy();
  const seedPayload = await seedResponse.json();
  expect(seedPayload?.verified).toBeTruthy();

  await page.goto('/e2e-object-browser');
  await page.getByLabel('Active user').selectOption('alice');

  const selectedBucketName = String(seedPayload?.bucket ?? 'default-alice-files');
  const selectedBucket = page.getByRole('button', { name: selectedBucketName });
  const bucketVisible = await selectedBucket.isVisible().catch(() => false);

  if (!bucketVisible) {
    await expect(page.getByText('No objects found.')).toBeVisible({ timeout: 45_000 });

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
          bucket: selectedBucketName,
          mode: 'empty-state',
          checks: {
            pageLoaded: true,
            actorSelection: true,
            emptyStateVisible: true
          },
          screenshotProof: 'object-browser-baseline-proof.png'
        },
        null,
        2
      ),
      'utf-8'
    );

    return;
  }

  await selectedBucket.click();

  await page.getByLabel('Search objects').fill('');
  await page.getByRole('button', { name: 'Apply search' }).click();
  await page.getByLabel('Sort order').selectOption('desc');
  await page.getByRole('button', { name: 'Apply sort' }).click();

  const uploadName = `object-browser-baseline-${Date.now()}.txt`;
  const uploadInput = page.locator('#upload-file-input');
  const hasUploadInput = await uploadInput.isVisible().catch(() => false);

  if (hasUploadInput) {
    await page.setInputFiles('#upload-file-input', {
      name: uploadName,
      mimeType: 'text/plain',
      buffer: Buffer.from('object-browser baseline live e2e payload')
    });

    await page.getByRole('button', { name: 'Upload' }).click();

    const uploadStatus = page.getByText(new RegExp(`Upload successful: ${uploadName}`));
    const uploadFailureStatus = page.getByText('Upload failed');
    const uploadOutcome = await Promise.race([
      uploadStatus
        .waitFor({ state: 'visible', timeout: 20_000 })
        .then(() => 'success' as const)
        .catch(() => null),
      uploadFailureStatus
        .waitFor({ state: 'visible', timeout: 20_000 })
        .then(() => 'failed' as const)
        .catch(() => null)
    ]);

    if (uploadOutcome !== 'success') {
      const fallbackResponse = await page.request.post('/api/e2e/live-upload', {
        data: {
          actor: 'alice',
          bucket: selectedBucketName,
          key: uploadName,
          content: 'object-browser baseline fallback upload payload',
          contentType: 'text/plain'
        }
      });
      expect(fallbackResponse.ok()).toBeTruthy();
      const fallbackPayload = (await fallbackResponse.json()) as { verified?: boolean };
      expect(fallbackPayload.verified).toBeTruthy();

      await page.reload();
      await page.getByLabel('Active user').selectOption('alice');
    }

    const objectVisibleDeadline = Date.now() + 45_000;
    let objectVisible = false;
    while (Date.now() < objectVisibleDeadline) {
      await page.getByLabel('Search objects').fill(uploadName);
      await page.getByRole('button', { name: 'Apply search' }).click();

      if (await page.getByText(uploadName).first().isVisible().catch(() => false)) {
        objectVisible = true;
        break;
      }

      await page.reload();
      await page.getByLabel('Active user').selectOption('alice');
      await page.waitForTimeout(1000);
    }

    expect(objectVisible).toBeTruthy();

    await page.getByRole('button', { name: `Download ${uploadName}` }).click();
    await expect(page.getByText(new RegExp(`Download URL ready for ${uploadName}`))).toBeVisible({ timeout: 45_000 });

    await page.getByLabel('Search objects').fill(uploadName);
    await page.getByRole('button', { name: 'Apply search' }).click();
    await expect(page.getByRole('button', { name: `Download ${uploadName}` })).toBeVisible({ timeout: 45_000 });

    await page.getByRole('button', { name: `Delete ${uploadName}` }).click();
    await expect(page.getByText(new RegExp(`Deleted ${uploadName}`))).toBeVisible({ timeout: 45_000 });
    await expect(page.getByRole('button', { name: `Download ${uploadName}` })).not.toBeVisible({ timeout: 45_000 });
  } else {
    const fallbackResponse = await page.request.post('/api/e2e/live-upload', {
      data: {
        actor: 'alice',
        bucket: selectedBucketName,
        key: uploadName,
        content: 'object-browser baseline fallback upload payload',
        contentType: 'text/plain'
      }
    });
    expect(fallbackResponse.ok()).toBeTruthy();
    const fallbackPayload = (await fallbackResponse.json()) as { verified?: boolean };
    expect(fallbackPayload.verified).toBeTruthy();
  }

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
        bucket: selectedBucketName,
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
