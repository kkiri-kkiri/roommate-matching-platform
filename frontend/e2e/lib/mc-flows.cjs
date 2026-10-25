const fs = require('node:fs');
const path = require('node:path');
const { test: base, expect, database } = require('../fixtures.cjs');
const { responseTo, login, logout, control, browserRequest } = require('./flows.cjs');
const origin = 'http://127.0.0.1:18080/api';
const test = base.extend({
  mcTraffic: [async ({ context }, use, info) => {
    const starts = [], responses = [], failures = [];
    const journal = path.join(process.env.E2E_EVIDENCE_DIR, `traffic-${process.env.E2E_MC_PHASE}-${info.title.split(' ')[0]}.jsonl`);
    const record = (kind, event, list) => { list.push(event); fs.appendFileSync(journal, JSON.stringify({ kind, time:new Date().toISOString(), ...event }) + '\n'); };
    context.on('request', r => { if (r.url().startsWith(origin)) record('request', { method: r.method(), path: new URL(r.url()).pathname }, starts); });
    context.on('response', r => { if (r.url().startsWith(origin)) record('response', { method: r.request().method(), path: new URL(r.url()).pathname, status: r.status() }, responses); });
    context.on('requestfailed', r => { if (r.url().startsWith(origin)) record('failure', { method: r.method(), path: new URL(r.url()).pathname, reason: r.failure()?.errorText }, failures); });
    try { await use({ starts, responses, failures }); }
    finally {
      const name = `${process.env.E2E_MC_PHASE}-${info.title.split(' ')[0]}`;
      fs.writeFileSync(path.join(process.env.E2E_EVIDENCE_DIR, `traffic-${name}.json`), JSON.stringify({ test: info.title, starts, responses, failures }, null, 2));
    }
  }, { auto: true }],
});
const actor = () => process.env.E2E_LOGIN_EMAIL;
const peer = () => process.env.E2E_SECOND_EMAIL;
const profile = (snapshot, nickname) => snapshot.profiles.find(p => p.nickname === nickname);
const link = (page, name) => page.getByRole('link', { name: `${name} 상세 프로필 보기`, exact: true });
const article = (page, name) => link(page, name).locator('xpath=ancestor::article[1]');
const ids = data => data.content.map(p => p.id);
async function get(page, url) { const r = await browserRequest(page, 'GET', url); expect(r.status).toBe(200); return r.data; }
async function navigate(page, url, endpoint) { const r = await responseTo(page, 'GET', endpoint, () => page.goto(url)); return (await r.json()).data; }
async function calendar(page) {
  const events = await navigate(page, '/calendar', '/calendar/events');
  await page.getByRole('button', { name: '이번 달 전체', exact: true }).click();
  return events;
}
function range(events) {
  const dates = events.flatMap(e => [e.startDate, e.endDate]).sort();
  return `/calendar/events?from=${dates[0]}&to=${dates.at(-1)}`;
}
async function edit(page, title) {
  await page.getByRole('button', { name: new RegExp('^' + title + ',.*상세 보기$') }).click();
  await page.getByRole('button', { name: '일정 수정', exact: true }).click();
  return page.getByRole('dialog', { name: '일정 수정', exact: true });
}
async function applySleep(page, value) {
  await page.getByRole('button', { name: '필터 열기', exact: true }).click();
  await page.getByRole('checkbox', { name: value, exact: true }).check();
  const endpoint = new URL(page.url()).searchParams.get('tab') === 'recommended' ? '/roommates/recommended' : '/roommates';
  const r = await responseTo(page, 'GET', endpoint, () => page.getByRole('button', { name: '적용', exact: true }).click());
  return (await r.json()).data;
}
async function assertCards(page, data) {
  await expect(page.getByRole('link', { name: /상세 프로필 보기$/ })).toHaveCount(data.content.length);
  for (const p of data.content) { await expect(link(page, p.nickname)).toBeVisible(); if (p.matchScore !== null) await expect(article(page,p.nickname).getByLabel(`매칭점수 ${p.matchScore}점`)).toBeVisible(); }
}
module.exports = { test, expect, database, responseTo, login, logout, control, browserRequest, actor, peer, profile, link, article, ids, get, navigate, calendar, range, edit, applySleep, assertCards };
