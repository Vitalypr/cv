import { Page } from '@playwright/test';

export const APP_KEY = 'vitaly_fitness_app_v1';
export const START_DATE = '2026-06-29'; // program start; day 1 / week 1

export interface GotoOpts {
  state?: unknown;        // seed localStorage state
  date?: string;          // fix the clock, e.g. '2026-06-29T08:00:00'
  breakClone?: boolean;   // simulate old WebView without structuredClone
  offline?: boolean;      // abort all network
}

export async function gotoApp(page: Page, opts: GotoOpts = {}) {
  if (opts.date) await page.clock.install({ time: new Date(opts.date) });
  if (opts.offline) await page.route('**/*', (r) => {
    const u = r.request().url();
    if (u.startsWith('http://127.0.0.1')) return r.continue();
    return r.abort();
  });
  if (opts.breakClone) {
    await page.addInitScript(() => {
      try { Object.defineProperty(window, 'structuredClone', { value: undefined, configurable: true }); } catch { /* noop */ }
    });
  }
  if (opts.state !== undefined) {
    await page.addInitScript(([k, v]) => { localStorage.setItem(k, v); }, [APP_KEY, JSON.stringify(opts.state)] as const);
  }
  await page.goto('/index.html');
}

export async function switchTab(page: Page, view: string) {
  await page.locator(`.tab[data-view="${view}"]`).click();
  await page.locator(`#${view}.view.active`).waitFor();
}

/** Minimal valid state with one custom exercise + one logged set, for round-trip tests. */
export function sampleState() {
  return {
    meta: { createdAt: '2026-06-01T00:00:00.000Z', version: 1 },
    selectedDay: 0,
    logs: { workouts: {}, nutrition: {}, body: {}, performance: {} },
    customExercises: {},
    settings: { calorieTarget: 2250, proteinTarget: 160, carbTarget: 220, fatTarget: 65, waterTarget: 2.5 },
  };
}
