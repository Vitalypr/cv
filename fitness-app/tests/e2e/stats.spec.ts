import { test, expect } from '@playwright/test';
import { gotoApp, switchTab, START_DATE } from '../helpers';

function workout(date: string, sets: any[], extra: any = {}) {
  return { done: {}, notes: '', cardioDone: false, completed: true, sets, ...extra };
}
function benchSet(date: string, weight: string, reps: string) {
  return { id: 's_' + date, date, exerciseId: 'd1:b0', exercise: 'Bench Press', weight, reps, rpe: '8' };
}

// P4-1RM — per-exercise estimated-1RM series is computed and charted (uPlot).
test('@eval P4-1RM charts an estimated-1RM progression', async ({ page }) => {
  const workouts: Record<string, any> = {
    '2026-06-15_d1': workout('2026-06-15', [benchSet('2026-06-15', '55', '8')]),
    '2026-06-22_d1': workout('2026-06-22', [benchSet('2026-06-22', '57.5', '8')]),
    '2026-06-26_d1': workout('2026-06-26', [benchSet('2026-06-26', '60', '8')]),
  };
  const state = { meta: { createdAt: '2026-06-01', version: 1 }, selectedDay: 0,
    logs: { workouts, nutrition: {}, body: {}, performance: {} }, customExercises: {},
    settings: { calorieTarget: 2250, proteinTarget: 160, carbTarget: 220, fatTarget: 65, waterTarget: 2.5, restDefaultSec: 90 } };
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state });
  await switchTab(page, 'tracking');

  // uPlot rendered a canvas inside the stat chart.
  await expect(page.getByTestId('stat-chart').locator('canvas').first()).toBeVisible();
  // The computed series is exposed for verification: 3 increasing points.
  const stat = await page.evaluate(() => (window as any).__stat);
  expect(stat.points.length).toBe(3);
  expect(stat.points[2].e).toBeGreaterThan(stat.points[0].e);
});

// P4-STREAK — current streak with single-day forgiveness; workouts-this-week count.
test('@eval P4-STREAK computes streak and weekly count', async ({ page }) => {
  const workouts: Record<string, any> = {
    '2026-06-29_d1': workout('2026-06-29', [benchSet('2026-06-29', '60', '5')]),
    '2026-06-28_d2': workout('2026-06-28', [], { cardioDone: true }),
    '2026-06-27_d3': workout('2026-06-27', [benchSet('2026-06-27', '58', '5')]),
  };
  const state = { meta: { createdAt: '2026-06-01', version: 1 }, selectedDay: 0,
    logs: { workouts, nutrition: {}, body: {}, performance: {} }, customExercises: {},
    settings: { calorieTarget: 2250, proteinTarget: 160, carbTarget: 220, fatTarget: 65, waterTarget: 2.5, restDefaultSec: 90 } };
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state });
  await switchTab(page, 'tracking');
  await expect(page.getByTestId('streak')).toHaveText('3');
  await expect(page.getByTestId('week-count')).toHaveText('3');
});

// P4-PR — personal-records table lists best lift per exercise.
test('@eval P4-PR lists personal records', async ({ page }) => {
  const workouts: Record<string, any> = {
    '2026-06-22_d1': workout('2026-06-22', [benchSet('2026-06-22', '57.5', '8')]),
    '2026-06-26_d1': workout('2026-06-26', [benchSet('2026-06-26', '60', '8')]),
  };
  const state = { meta: { createdAt: '2026-06-01', version: 1 }, selectedDay: 0,
    logs: { workouts, nutrition: {}, body: {}, performance: {} }, customExercises: {},
    settings: { calorieTarget: 2250, proteinTarget: 160, carbTarget: 220, fatTarget: 65, waterTarget: 2.5, restDefaultSec: 90 } };
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state });
  await switchTab(page, 'tracking');
  const pr = page.getByTestId('pr-list');
  await expect(pr).toContainText('Bench Press');
  await expect(pr).toContainText('60'); // best weight
});
