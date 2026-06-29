import { test, expect } from '@playwright/test';
import { gotoApp, switchTab, START_DATE } from '../helpers';

// G1 — App opens straight to today's scheduled workout (week/day computed from startDate).
test('@eval G1 today: auto-selects scheduled day and shows week/day label', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  // On the program start date we expect week 1 / day 1.
  const label = page.getByTestId('week-day-label');
  await expect(label).toBeVisible();
  await expect(label).toContainText('שבוע 1');
  await expect(label).toContainText('יום 1');
  // The first day chip is the active one.
  await expect(page.locator('.dayChip').first()).toHaveClass(/active/);
});

// G2 — Log a set; it appears and persists across reload.
test('@eval G2 log a set persists', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await page.locator('#setWeight').fill('60');
  await page.locator('#setReps').fill('8');
  await page.locator('#setRpe').fill('8');
  await page.locator('#addSet').click();
  const rows = page.getByTestId('set-row');
  await expect(rows).toHaveCount(1);
  await page.reload();
  await expect(page.getByTestId('set-row')).toHaveCount(1);
});

// G3 — Typing notes then doing another action must NOT lose the notes.
test('@eval G3 notes survive a re-render', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  const notes = page.locator('#workoutNotes');
  await notes.fill('כתף ימין הציקה קצת');
  // Trigger a re-render via a different interaction (tick first exercise).
  await page.getByTestId('exercise-row').first().locator('.check').click();
  await expect(page.locator('#workoutNotes')).toHaveValue('כתף ימין הציקה קצת');
});

// G4 — Nutrition template updates totals and logs a meal.
test('@eval G4 nutrition template updates totals', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await switchTab(page, 'nutrition');
  const calBefore = Number(await page.getByTestId('cal-total').textContent());
  await page.locator('[data-meal="0"]').click();
  await expect.poll(async () => Number(await page.getByTestId('cal-total').textContent())).toBeGreaterThan(calBefore);
  await expect(page.getByTestId('nutrition-log').locator('tbody tr')).toHaveCount(1);
});

// G5 — Two dated body weights render a chart with date-proportional spacing.
test('@eval G5 body chart spaces points by date', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await switchTab(page, 'tracking');
  // First measurement
  await page.locator('#bodyDate').fill('2026-06-01');
  await page.locator('#bodyWeight').fill('77');
  await page.locator('#saveBody').click();
  // Second measurement 10 days later
  await page.locator('#bodyDate').fill('2026-06-11');
  await page.locator('#bodyWeight').fill('76');
  await page.locator('#saveBody').click();
  await expect(page.getByTestId('body-table').locator('tbody tr')).toHaveCount(2);
  // Chart exposes its mapped points for testability.
  const pts = await page.evaluate(() => (window as any).__chartPoints?.weightChart);
  expect(Array.isArray(pts)).toBeTruthy();
  expect(pts.length).toBe(2);
  // Only two points → they sit at the horizontal extremes regardless of gap, but the
  // x coordinate must derive from the date, not the index. With two points the test
  // mainly proves the debug hook + date mapping exist; the 3-point spacing is unit-tested.
  expect(pts[1].x).toBeGreaterThan(pts[0].x);
});

// G6 — Exercise search is case-insensitive.
test('@eval G6 exercise search is case-insensitive', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await switchTab(page, 'exercises');
  await page.locator('#exSearch').fill('bench'); // lowercase; data has "Bench Press"
  await expect(page.locator('#guideGrid .legendCard').first()).toBeVisible();
  await expect(page.locator('#guideGrid')).toContainText(/Bench/i);
});

// G7 — Export then import round-trips the state.
test('@eval G7 backup export/import round-trip', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  // Create a distinctive bit of state: log a set.
  await page.locator('#setWeight').fill('99');
  await page.locator('#setReps').fill('3');
  await page.locator('#addSet').click();
  await expect(page.getByTestId('set-row')).toHaveCount(1);

  await switchTab(page, 'settings');
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.locator('#exportData').click(),
  ]);
  const path = await download.path();

  // Reset (confirm dialog) then import.
  page.once('dialog', (d) => d.accept());
  await page.locator('#resetData').click();

  await page.locator('#importFile').setInputFiles(path!);
  await page.waitForTimeout(200);

  await switchTab(page, 'daily');
  await expect(page.getByTestId('set-row')).toHaveCount(1);
  await expect(page.getByTestId('set-row').first()).toContainText('99');
});
