import { defineConfig, devices } from '@playwright/test';
import { existsSync } from 'node:fs';

// Use the container-provided Chromium if the bundled one isn't present.
const SYSTEM_CHROMIUM = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const executablePath = existsSync(SYSTEM_CHROMIUM) ? SYSTEM_CHROMIUM : undefined;

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5180',
    trace: 'retain-on-failure',
    locale: 'he-IL',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], launchOptions: { executablePath } },
    },
  ],
  webServer: {
    command: 'python3 -m http.server 5180 --bind 127.0.0.1',
    url: 'http://127.0.0.1:5180/index.html',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
