const { test: base, expect } = require('@playwright/test');
const { database } = require('./lib/database.cjs');

const test = base.extend({
  // New browser context per test; reset all data only inside the guarded disposable schema.
  isolatedDatabase: [async ({}, use) => {
    database('reset');
    try { await use(); } finally { database('clean'); }
  }, { auto: true, timeout: 120_000 }],
  context: async ({ context }, use) => {
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (['http://127.0.0.1:13000', 'http://127.0.0.1:18080'].includes(url.origin)) return route.continue();
      return route.abort('blockedbyclient');
    });
    await use(context);
  },
});
module.exports = { test, expect, database };
