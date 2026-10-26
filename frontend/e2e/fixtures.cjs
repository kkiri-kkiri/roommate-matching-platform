const { test: base, expect } = require('@playwright/test');
const { database } = require('./lib/database.cjs');
const { writeFileSync } = require('node:fs');
const path = require('node:path');
const { sanitize, retentionEnabled } = require('./run.cjs');

async function restrict(context) {
  await context.route('**/*', route => {
    const url = new URL(route.request().url());
    if (['http://127.0.0.1:13000', 'http://127.0.0.1:18080'].includes(url.origin)) return route.continue();
    return route.abort('blockedbyclient');
  });
}

const test = base.extend({
  // New browser context per test; reset all data only inside the guarded disposable schema.
  isolatedDatabase: [async ({}, use, testInfo) => {
    process.env.E2E_CURRENT_TEST_ID = process.env.E2E_MC_P0 === '1'
      ? process.env.E2E_MC_PHASE + '-' + testInfo.testId : testInfo.testId;
    // Retained cases own their row IDs; the runner seeds its empty schema once.
    const retainResources = retentionEnabled();
    if (!retainResources) database('reset');
    try { await use(); } finally { if (!retainResources) database('clean'); }
  }, { auto: true, timeout: 120_000 }],
  context: async ({ context }, use) => {
    await restrict(context);
    await use(context);
  },
  peerPage: async ({ browser, evidence }, use) => {
    const context = await browser.newContext({ baseURL: 'http://127.0.0.1:13000' });
    await restrict(context); const page = await context.newPage(); evidence.watch(page, 'B');
    try { await use(page); } finally { await context.close(); }
  },
  adminPage: async ({ browser, evidence }, use) => {
    const context = await browser.newContext({ baseURL: 'http://127.0.0.1:13000' });
    await restrict(context); const page = await context.newPage(); evidence.watch(page, 'admin');
    try { await use(page); } finally { await context.close(); }
  },
  evidence: [async ({ context }, use, testInfo) => {
    const requests = [], sockets = [], observations = [];
    const watched = new WeakSet();
    const watch = (page, actor) => {
      if (watched.has(page)) return; watched.add(page);
      page.on('response', response => {
        const url = new URL(response.url());
        if (url.origin === 'http://127.0.0.1:18080') requests.push({ actor, method: response.request().method(), path: url.pathname, status: response.status() });
      });
      page.on('websocket', socket => {
        const item = { actor, url: socket.url(), sent: [], received: [], closed: false }; sockets.push(item);
        socket.on('framesent', frame => { const command = String(frame.payload).split('\n')[0].trim(); if (command) item.sent.push(command); });
        socket.on('framereceived', frame => {
          const payload = String(frame.payload), command = payload.split('\n')[0].trim();
          if (command === 'MESSAGE') {
            const text = payload.split(/\r?\n\r?\n/).slice(1).join('\n\n').replace(/\0$/, '');
            try { item.received.push({ command, id: JSON.parse(text).id, text: JSON.parse(text).text }); }
            catch { item.received.push({ command, invalidJson: true }); }
          } else if (command) item.received.push({ command });
        });
        socket.on('close', () => { item.closed = true; });
      });
    };
    context.on('page', page => watch(page, 'A')); context.pages().forEach(page => watch(page, 'A'));
    try { await use({ watch, requests, sockets, note: (name, data) => observations.push({ name, data }) }); }
    finally {
      // Sanitize before Playwright's artifact teardown writes error-context.md.
      const secrets = ['E2E_LOGIN_PASSWORD', 'E2E_DB_PASSWORD', 'E2E_JWT_SECRET', 'MYSQL_ROOT_PASSWORD', 'E2E_CONTROL_KEY']
        .map(key => process.env[key]).filter(Boolean);
      for (const error of testInfo.errors) {
        for (const key of ['message', 'stack', 'errorContext', 'value']) {
          if (typeof error[key] === 'string') error[key] = sanitize(error[key], secrets);
        }
      }
      const phase = process.env.E2E_MC_P0 === '1' || process.env.E2E_MC_P1 === '1' ? process.env.E2E_MC_PHASE : null;
      if (phase && !/^[a-z0-9-]{1,50}$/.test(phase)) throw new Error('Unsafe MC phase');
      const name = (phase ? phase + '-' : '') + testInfo.testId.replace(/[^a-z0-9-]/gi, '_');
      writeFileSync(path.join(process.env.E2E_EVIDENCE_DIR, `browser-${name}.json`), JSON.stringify({ test: testInfo.title, requests, sockets, observations }, null, 2));
    }
  }, { auto: true }],
});
module.exports = { test, expect, database };
