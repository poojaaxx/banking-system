import { defineConfig, devices } from '@playwright/test'

/**
 * Points at whatever is already running -- either the Vite dev server (fast
 * local iteration; requires the backend running separately, see README) or
 * the packaged Docker image (the CI-representative run: `docker compose up`
 * then PLAYWRIGHT_BASE_URL=http://localhost:8080). This config never starts
 * either server itself, so a run always reflects a real, already-serving app.
 */
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://localhost:5174'

export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // tests share backend state; keep deterministic
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 30_000,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    // Reproducible but not run by default (see README for how to enable):
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    // { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    // { name: 'mobile-chrome', use: { ...devices['Pixel 7'] } },
  ],
})
