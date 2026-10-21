const { defineConfig, devices } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './e2e/tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 45_000,
  expect: { timeout: 10_000 },
  outputDir: './test-results',
  preserveOutput: 'never',
  reporter: [['list'], ['./e2e/evidence-reporter.cjs']],
  use: {
    baseURL: 'http://127.0.0.1:13000',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
    // Auth requests and verification fields contain credentials, including on failures.
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    serviceWorkers: 'block',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
