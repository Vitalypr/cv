import { test, expect } from '@playwright/test';
import { gotoApp, START_DATE } from '../helpers';

// Build a state with prior Bench Press (d1:b0) sessions before "today" (START_DATE).
function stateWithBenchHistory(sessions: Array<{ date: string; weight: string; reps: string; rpe?: string }>) {
  const workouts: Record<string, any> = {};
  for (const s of sessions) {
    workouts[`${s.date}_d1`] = {
      done: {}, notes: '', cardioDone: false, completed: true,
      sets: [{ id: 's_' + s.date, date: s.date, exerciseId: 'd1:b0', exercise: 'Bench Press', weight: s.weight, reps: s.reps, rpe: s.rpe || '' }],
    };
  }
  return {
    meta: { createdAt: '2026-06-01T00:00:00.000Z', version: 1 },
    selectedDay: 0,
    logs: { workouts, nutrition: {}, body: {}, performance: {} },
    customExercises: {},
    settings: { calorieTarget: 2250, proteinTarget: 160, carbTarget: 220, fatTarget: 65, waterTarget: 2.5, restDefaultSec: 90 },
  };
}

// P2-PREVHINT — previous session's numbers appear as ghost placeholders + a "last time" line.
test('@eval P2-PREVHINT shows previous session as placeholder + hint', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state: stateWithBenchHistory([{ date: '2026-06-26', weight: '60', reps: '8', rpe: '8' }]) });
  // First exercise of day 1 is Bench Press, selected by default in the set logger.
  await expect(page.locator('#setWeight')).toHaveAttribute('placeholder', '60');
  await expect(page.locator('#setReps')).toHaveAttribute('placeholder', '8');
  await expect(page.getByTestId('last-session')).toContainText('60');
  await expect(page.getByTestId('last-session')).toContainText('8');
});

// P2-HISTORY — per-exercise history lists prior sessions for the selected exercise.
test('@eval P2-HISTORY lists prior sessions for the selected exercise', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state: stateWithBenchHistory([
    { date: '2026-06-22', weight: '57.5', reps: '8' },
    { date: '2026-06-26', weight: '60', reps: '8' },
  ]) });
  const hist = page.getByTestId('exercise-history');
  await expect(hist).toContainText('2026-06-22');
  await expect(hist).toContainText('2026-06-26');
});

// P2-OVERLOAD — a set that beats last session is flagged; one that doesn't, isn't.
test('@eval P2-OVERLOAD flags a progressive-overload set', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00`, state: stateWithBenchHistory([{ date: '2026-06-26', weight: '60', reps: '8' }]) });
  // Beat it: 62 x 8.
  await page.locator('#setWeight').fill('62');
  await page.locator('#setReps').fill('8');
  await page.locator('#addSet').click();
  await expect(page.getByTestId('set-row').filter({ hasText: '62' }).getByTestId('pr-arrow')).toBeVisible();
  // Don't beat it: 50 x 5.
  await page.locator('#setWeight').fill('50');
  await page.locator('#setReps').fill('5');
  await page.locator('#addSet').click();
  await expect(page.getByTestId('set-row').filter({ hasText: '50' }).getByTestId('pr-arrow')).toHaveCount(0);
});

// P2-RESTTIMER — rest timer auto-starts on set add; +15 adjusts; skip dismisses.
test('@eval P2-RESTTIMER auto-starts, adjusts and skips', async ({ page }) => {
  await gotoApp(page, { date: `${START_DATE}T08:00:00` });
  await page.locator('#setWeight').fill('40');
  await page.locator('#setReps').fill('10');
  await page.locator('#addSet').click();

  const timer = page.getByTestId('rest-timer');
  await expect(timer).toBeVisible();
  await expect(page.getByTestId('rest-remaining')).toHaveText('01:30');
  await page.getByTestId('rest-plus').click();
  await expect(page.getByTestId('rest-remaining')).toHaveText('01:45');
  // Advance the (faked) clock by 5s and confirm it counts down.
  await page.clock.runFor(5000);
  await expect(page.getByTestId('rest-remaining')).toHaveText('01:40');
  await page.getByTestId('rest-skip').click();
  await expect(timer).toBeHidden();
});
