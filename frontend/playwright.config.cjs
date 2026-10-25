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
    launchOptions: { executablePath: require('playwright-core').chromium.executablePath() },
  },
  projects: process.env.E2E_MC_P0 === '1'
    ? [{ name: 'mc-p0', testMatch: '**/mc-p0.spec.cjs', use: { ...devices['Desktop Chrome'], timezoneId: 'Asia/Seoul' } }]
    : process.env.E2E_AUS_P1 === '1'
    ? [{ name: 'aus-p1', testMatch: '**/aus-p1.spec.cjs', use: { ...devices['Desktop Chrome'] } }]
    : process.env.E2E_AUS_P0 === '1'
    ? [
      { name: 'aus-baseline', testMatch: '**/login.spec.cjs', use: { ...devices['Desktop Chrome'] } },
      { name: 'aus-p0', testMatch: '**/aus-p0.spec.cjs', dependencies: ['aus-baseline'], use: { ...devices['Desktop Chrome'] } },
    ]
    : [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
