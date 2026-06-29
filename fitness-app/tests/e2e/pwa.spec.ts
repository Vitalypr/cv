import { test, expect } from '@playwright/test';
import { gotoApp } from '../helpers';

// PWA-SW — a service worker registers and controls the page; manifest + theme-color present.
test('@eval PWA-SW registers, controls page, manifest + theme-color present', async ({ page }) => {
  await gotoApp(page);
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.reload();
  const controlled = await page.evaluate(() => !!navigator.serviceWorker.controller);
  expect(controlled).toBeTruthy();

  await expect(page.locator('link[rel="manifest"]')).toHaveAttribute('href', 'manifest.webmanifest');
  const theme = await page.locator('meta[name="theme-color"]').getAttribute('content');
  expect(theme).toBe('#070b12');

  // Manifest is valid JSON with the install-critical fields.
  const mf = await page.evaluate(async () => {
    const r = await fetch('manifest.webmanifest');
    return r.json();
  });
  expect(mf.name).toBeTruthy();
  expect(mf.display).toBe('standalone');
  expect(mf.icons.some((i: any) => i.sizes === '192x192')).toBeTruthy();
  expect(mf.icons.some((i: any) => i.sizes === '512x512')).toBeTruthy();
});

// PWA-OFFLINE — the cached app shell loads with the network fully offline.
test('@eval PWA-OFFLINE app shell loads with network offline', async ({ page, context }) => {
  await gotoApp(page);
  await page.evaluate(() => navigator.serviceWorker.ready);
  // Wait until the shell is actually cached.
  await page.waitForFunction(async () => (await caches.keys()).length > 0);
  await page.evaluate(async () => {
    const c = await caches.open('fitness-app-v1');
    return c.match('./index.html');
  });

  await context.setOffline(true);
  await page.reload();
  await expect(page.getByTestId('exercise-row').first()).toBeVisible();
  await expect(page.locator('#daily')).toContainText('יום');
  await context.setOffline(false);
});
