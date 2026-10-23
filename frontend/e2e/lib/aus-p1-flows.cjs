const { randomBytes, createHash } = require('node:crypto');
const { test: base, expect, database } = require('../fixtures.cjs');
const { responseTo, login, logout, control, browserRequest } = require('./flows.cjs');
const { prepareSignup, verifySignup, saveSurvey, preference } = require('./aus-flows.cjs');
const { registerSecret } = require('../run.cjs');
const fs = require('node:fs'), path = require('node:path');
const origin = 'http://127.0.0.1:18080/api';
const test = base.extend({
  p1: [async ({ context, page, evidence }, use, info) => {
    const started = [], failed = [], dialogs = [];
    const watch = p => {
      p.on('request', r => { if (r.url().startsWith(origin + '/')) started.push({ method: r.method(), path: new URL(r.url()).pathname }); });
      p.on('requestfailed', r => { if (r.url().startsWith(origin + '/')) failed.push({ method: r.method(), path: new URL(r.url()).pathname, reason: r.failure()?.errorText }); });
      p.on('dialog', async dialog => { dialogs.push(dialog.message()); await dialog.accept(); });
    };
    context.pages().forEach(watch); context.on('page', watch);
    const label = createHash('sha256').update(info.title).digest('hex').slice(0, 8);
    const state = { started, failed, dialogs, email: `p${label}${process.env.E2E_DATABASE.slice(-12)}@hknu.ac.kr`,
      note: evidence.note, count: (method, endpoint) => started.filter(r => r.method === method && r.path === '/api' + endpoint).length };
    try { await use(state); }
    finally {
      fs.writeFileSync(path.join(process.env.E2E_EVIDENCE_DIR, 'p1-' + info.testId.replace(/[^a-z0-9-]/gi,'_') + '.json'),
        JSON.stringify({ test: info.title, started, failed, observationsOnly: true }, null, 2));
    }
  }, { auto: true }],
});
async function secret(value) { registerSecret(value); await control('secret', { value }); return value; }
async function password(bytes) {
  const prefix = 'A1!' + randomBytes(4).toString('hex'), rest = bytes - Buffer.byteLength(prefix);
  return secret(prefix + '가'.repeat(Math.floor(rest / 3)) + 'x'.repeat(rest % 3));
}
const card = (page, name) => page.getByRole('heading', { name, exact: typeof name === 'string' }).locator('xpath=ancestor::section[1]');
async function noWrite(page, p1, method, endpoint, action) {
  const count = p1.count(method, endpoint); await action(); await page.waitForTimeout(200);
  expect(p1.count(method, endpoint), 'Browser prevented the invalid request').toBe(count);
}
async function sendCode(page, email, status = 200) {
  const r = await responseTo(page, 'POST', '/auth/email-verifications/send', () => page.getByRole('button', { name: /^(인증번호 전송|재전송)$/ }).click(), status);
  if (status !== 200) return r;
  const { code } = await control('mail', { email });
  if (!/^\d{6}$/.test(code)) throw new Error('Verification mail missing');
  return code;
}
async function verifyCode(page, code, status = 200) {
  await page.locator('#signup-verification-code').fill(code);
  return responseTo(page, 'POST', '/auth/email-verifications/verify', () => page.getByRole('button', { name: '인증 확인', exact: true }).click(), status);
}
async function editSurvey(page) {
  await page.goto('/profile?tab=survey');
  await page.getByRole('button', { name: '설문 수정', exact: true }).click();
}
async function resetRequest(page, email, status = 200) {
  await page.goto('/find-password'); await page.locator('#find-password-email').fill(email.split('@')[0]);
  return responseTo(page, 'POST', '/auth/password-reset/request', () => page.getByRole('button', { name: '비밀번호 재설정 이메일 보내기', exact: true }).click(), status);
}
async function resetFields(page, token, value, confirm = value) {
  const enter = page.getByRole('button', { name: '메일의 재설정 토큰 입력하기', exact: true });
  if (await enter.isVisible()) await enter.click();
  await page.locator('#reset-token').fill(token); await page.locator('#new-password').fill(value); await page.locator('#new-password-confirm').fill(confirm);
}
async function resetSubmit(page, status = 200) {
  return responseTo(page, 'POST', '/auth/password-reset/confirm', () => page.getByRole('button', { name: '비밀번호 변경하기', exact: true }).click(), status);
}
async function resetToken(email) {
  const { code } = await control('mail', { email, kind: 'reset' });
  if (!/^[A-Za-z0-9_-]{43}$/.test(code)) throw new Error('Reset mail missing');
  return code;
}
async function gate(page, method, endpoint, afterCommit = false) {
  let release, entered; const hold = new Promise(r => { release = r; }), waiting = new Promise(r => { entered = r; });
  let used = false, responseStatus;
  const handler = async route => {
    if (route.request().method() !== method || used) return route.continue();
    used = true;
    const response = afterCommit ? await route.fetch() : null;
    if (response) responseStatus = response.status();
    entered(); const mode = await hold;
    if (mode === 'abort') return route.abort('failed').catch(() => {});
    if (response) return route.fulfill({ response }).catch(() => {});
    const actual = await route.fetch(); responseStatus = actual.status();
    return route.fulfill({ response: actual }).catch(() => {});
  };
  await page.route(origin + endpoint, handler);
  return { waiting, release, status: () => responseStatus, remove: () => page.unroute(origin + endpoint, handler) };
}
async function failGet(page, endpoint) {
  const handler = route => route.request().method() === 'GET' ? route.abort('failed') : route.continue();
  await page.route(origin + endpoint, handler);
  return () => page.unroute(origin + endpoint, handler);
}
module.exports = { test, expect, database, responseTo, login, logout, control, browserRequest,
  prepareSignup, verifySignup, saveSurvey, preference, secret, password, card, noWrite,
  sendCode, verifyCode, editSurvey, resetRequest, resetFields, resetSubmit, resetToken, gate, failGet };
