import { test, expect } from '@playwright/test';
import { gotoApp, switchTab, START_DATE } from '../helpers';

// P3-IMAGE — exercise guide shows real photos, and daily rows show a thumbnail.
test('@eval P3-IMAGE exercise guide + daily rows show bundled photos', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });

  // Daily: first exercise (Bench Press, icon "bench") has a thumbnail image.
  const firstThumb = page.getByTestId('exercise-row').first().locator('.exThumb img');
  await expect(firstThumb).toHaveAttribute('src', /assets\/exercises\/bench\.jpg/);
  await expect(firstThumb).toBeVisible();
  // The image actually loaded (naturalWidth > 0), i.e. not a broken link.
  await expect.poll(() => firstThumb.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);

  // Exercise guide: the Bench Press card shows a photo.
  await switchTab(page, 'exercises');
  const benchCard = page.locator('#guideGrid .legendCard').filter({ hasText: 'Bench Press' }).first();
  await expect(benchCard.locator('img.exImg')).toHaveAttribute('src', /assets\/exercises\/bench\.jpg/);
});

// P3-ATTRIB — attribution/license for the bundled media is present.
test('@eval P3-ATTRIB media attribution + license shown in settings', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await switchTab(page, 'settings');
  const credit = page.getByTestId('attribution');
  await expect(credit).toContainText('CC BY-SA');
  await expect(credit).toContainText('Free Exercise DB');
});

// P3-FALLBACK — an exercise icon without a bundled photo still shows the SVG line-art.
test('@eval P3-FALLBACK icons without photos fall back to SVG', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await switchTab(page, 'exercises');
  // "Zone 2" guide uses icon "walk" (has photo) — pick an icon with no media instead:
  // cardio/breath/checkin have no bundled image, so their card renders an <svg>.
  const cardioCard = page.locator('#guideGrid .legendCard').filter({ hasText: 'Zone 2' }).first();
  // Zone 2 maps to "walk" which DOES have media; assert the breath/checkin-type via אינטרוולים (cardio).
  const intervalsCard = page.locator('#guideGrid .legendCard').filter({ hasText: 'אינטרוולים' }).first();
  await expect(intervalsCard.locator('svg')).toBeVisible();
});
