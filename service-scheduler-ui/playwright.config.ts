import { defineConfig } from '@playwright/test';

/**
 * The Phase 1 suite runs at the API level. The backend runs against the
 * Docker Compose PostgreSQL and Redis services, started with the test
 * profile. The suite therefore needs no browser and no web server.
 */
const baseURL = process.env.BASE_URL ?? 'http://localhost:8080';

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['list'],
    ['junit', { outputFile: 'playwright-results.xml' }],
    ['json', { outputFile: 'playwright-results.json' }]
  ],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    actionTimeout: 15000,
    navigationTimeout: 30000
  }
});
