import { test, expect } from '@playwright/test';
import { gotoApp, switchTab, START_DATE } from '../helpers';

// P5-WEB-FALLBACK — on the plain web (no Capacitor), the health sync degrades gracefully:
// the card is present and actions show an "Android app only" status instead of crashing.
test('@eval P5-WEB-FALLBACK health card degrades gracefully on web', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await switchTab(page, 'settings');

  const card = page.getByTestId('health-card');
  await expect(card).toBeVisible();
  await expect(card).toContainText('Health Connect');

  await page.locator('#healthConnect').click();
  await expect(page.getByTestId('health-status')).toContainText('אנדרואיד');

  // Pull/Push must also no-op safely (no thrown page errors, app still navigable).
  await page.locator('#healthIn').click();
  await page.locator('#healthOut').click();
  await switchTab(page, 'daily');
  await expect(page.getByTestId('exercise-row').first()).toBeVisible();

  expect(errors, errors.join('\n')).toHaveLength(0);
});
