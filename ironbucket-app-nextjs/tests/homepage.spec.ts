import { test, expect } from '@playwright/test';

test.describe('Homepage', () => {
  test('should show 404 if no homepage exists', async ({ page }) => {
    const response = await page.goto('http://localhost:3000/');
    expect(response?.status()).toBe(404);
    await expect(page).toHaveTitle(/404/);
  });
});
