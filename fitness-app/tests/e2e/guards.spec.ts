import { test, expect } from '@playwright/test';
import { gotoApp, switchTab, START_DATE } from '../helpers';

// E-XSS — user text with HTML must render inert everywhere it appears.
test('@eval E-XSS stored script in names does not execute', async ({ page }) => {
  const evil = '<img src=x onerror="window.__xss=1">';
  const state = {
    meta: { createdAt: '2026-06-01T00:00:00.000Z', version: 1 },
    selectedDay: 0,
    logs: {
      workouts: { [`${START_DATE}_d1`]: { done: {}, sets: [{ id: 's1', date: START_DATE, exercise: evil, weight: '50', reps: '5', rpe: '7' }], notes: '', cardioDone: false, completed: false } },
      nutrition: { [START_DATE]: { meals: [{ id: 'm1', name: evil, cal: 100, protein: 10, carb: 5, fat: 2 }], water: 0 } },
      body: { [START_DATE]: { weight: '77', waist: '90', steps: '1000', sleep: '7', energy: '7' } },
      performance: {},
    },
    customExercises: { d1: [{ id: 'c1', custom: true, icon: 'custom', name: evil, muscle: evil, sets: '3', reps: '8', note: evil }] },
    settings: { calorieTarget: 2250, proteinTarget: 160, carbTarget: 220, fatTarget: 65, waterTarget: 2.5 },
  };
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state });
  // Visit every tab so all render paths run.
  for (const v of ['daily', 'weekly', 'nutrition', 'tracking', 'exercises', 'settings']) {
    await switchTab(page, v);
  }
  const xss = await page.evaluate(() => (window as any).__xss);
  expect(xss).toBeUndefined();
});

// E-IDENTITY — done-state follows the exercise identity, not the array index.
test('@eval E-IDENTITY done flag survives deleting an earlier custom exercise', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  // Add two custom exercises AAA then BBB.
  for (const name of ['AAA_first', 'BBB_second']) {
    await page.locator('#customName').fill(name);
    await page.locator('#addCustomExercise').click();
  }
  // Tick BBB as done.
  const bbbRow = page.getByTestId('exercise-row').filter({ hasText: 'BBB_second' });
  await bbbRow.locator('.check').click();
  await expect(bbbRow.locator('.check')).toHaveClass(/done/);
  // Delete AAA (first custom) from the manual-exercise table.
  page.once('dialog', (d) => d.accept());
  await page.locator('[data-delcustom="0"]').click();
  // BBB must still be the one marked done.
  const bbbAfter = page.getByTestId('exercise-row').filter({ hasText: 'BBB_second' });
  await expect(bbbAfter.locator('.check')).toHaveClass(/done/);
  // And AAA must be gone.
  await expect(page.getByTestId('exercise-row').filter({ hasText: 'AAA_first' })).toHaveCount(0);
});

// E-CLONE — app must boot without structuredClone (old WebView/Safari <15.4).
test('@eval E-CLONE boots without structuredClone', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, breakClone: true });
  // Daily view must have rendered real content (a day title + exercises).
  await expect(page.getByTestId('exercise-row').first()).toBeVisible();
  await expect(page.locator('#daily')).toContainText('יום 1');
});

// E-OFFLINE — golden basics work with the network down.
test('@eval E-OFFLINE logging works offline', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, offline: true });
  await page.locator('#setWeight').fill('40');
  await page.locator('#setReps').fill('10');
  await page.locator('#addSet').click();
  await expect(page.getByTestId('set-row')).toHaveCount(1);
});

// E-IMPORT-GUARD — a malformed-but-JSON backup must not crash or corrupt; prev snapshot kept.
test('@eval E-IMPORT-GUARD malformed import is handled safely', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  // Seed a known-good set first.
  await page.locator('#setWeight').fill('55');
  await page.locator('#setReps').fill('5');
  await page.locator('#addSet').click();
  await expect(page.getByTestId('set-row')).toHaveCount(1);

  // Import a malformed file (wrong types, out-of-range selectedDay).
  await switchTab(page, 'settings');
  const bad = JSON.stringify({ logs: { workouts: 'not-an-object', nutrition: [], body: 5 }, selectedDay: 999, settings: 'nope' });
  await page.locator('#importFile').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from(bad) });
  await page.waitForTimeout(200);
  // App must still be alive and navigable (no white screen / thrown render).
  await switchTab(page, 'daily');
  await expect(page.getByTestId('exercise-row').first()).toBeVisible();
  // A recoverable previous-state snapshot must exist.
  const hasPrev = await page.evaluate(() => Object.keys(localStorage).some((k) => k.endsWith('_prev')));
  expect(hasPrev).toBeTruthy();
});
