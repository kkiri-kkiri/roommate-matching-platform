// Explicit local verification only; not part of the normal E2E runner. No credentials/tokens are logged.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const { chromium, expect } = require('@playwright/test');
const root = path.resolve(__dirname, '../..');
const origin = 'http://127.0.0.1:13000';
const schema = process.env.DEMO_TEST_SCHEMA || '';
const localApplyState = process.env.DEMO_LOCAL_APPLY_STATE;
let appliedLocal = false;
if (schema === 'kkirikkiri' && localApplyState) {
  const record = JSON.parse(fs.readFileSync(path.join(localApplyState, 'apply.json'), 'utf8').replace(/^\uFEFF/, ''));
  assert.equal(record.status, 'DB_APPLIED');
  assert.equal(record.database, 'localhost:3306/kkirikkiri');
  assert.equal(record.userAuthorizedPersonalLocal, true);
  assert.equal(record.sha256, '7977fc06f3d45f846c2e38005cad475e17450d29cce1fb110a9c6ac4c2f1f737');
  assert.match(record.run, /^[0-9]{8}_[a-zA-Z0-9_]{1,16}$/);
  assert.equal(path.resolve(localApplyState), path.join(root, 'backend/local-data/demo50/local-apply-' + record.run));
  appliedLocal = true;
}
if (!appliedLocal && !/^kkirikkiri_demo50_runtime_[a-zA-Z0-9_]+$/.test(schema)) throw Error('Verified local apply receipt or isolated runtime schema required');
const api = appliedLocal ? 'http://127.0.0.1:8080/api' : 'http://127.0.0.1:18082/api';
const state = process.env.DEMO_MERGE_STATE;
if (!state || !process.env.DEMO_NEW_LOGIN_PASSWORD || !process.env.DEMO_TEST_JWT_SECRET) throw Error('Private runtime verification settings required');
const projection = JSON.parse(fs.readFileSync(path.join(state, 'ui-projection.json'), 'utf8'));
const output = path.join(root, 'backend/build/demo50', appliedLocal ? 'local-browser-' + path.basename(localApplyState) : 'merge-browser');
const build = path.join(root, 'backend/build', appliedLocal ? 'demo-local-ui' : 'demo-merge-ui');
fs.mkdirSync(output, { recursive: true });
const initializer = fs.readFileSync(path.join(root, 'backend/src/main/java/com/kkirikkiri/global/init/DevDataInitializer.java'), 'utf8');
const known = [...initializer.matchAll(/createUserIfNotExists\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*UserRole\.(USER|ADMIN)/g)]
  .map(m => ({ password: m[1], email: m[2], role: m[3] }));
if (known.length !== 2) throw Error('Cannot identify local development login fixtures');
const observations = [];
async function request(method, route, token, body) {
  const headers = {}; if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  const response = await fetch(api + route, { method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(15000) });
  observations.push({ method, path: route.split('?')[0], status: response.status });
  if (!response.ok) throw Error(`API ${method} ${route.split('?')[0]} status=${response.status}`);
  const result = await response.json(); assert.equal(result.success, true); return result.data;
}
async function login(email, password) { return request('POST', '/auth/login', null, { email, password }); }
function seedToken(userId) {
  assert(projection.students.some(s => s.id === userId && s.slot <= 30));
  const now = Math.floor(Date.now() / 1000);
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({ userId, role: 'USER', iat: now, exp: now + 600 })).toString('base64url');
  const value = `${header}.${payload}`;
  return `${value}.${crypto.createHmac('sha256', Buffer.from(process.env.DEMO_TEST_JWT_SECRET, 'base64')).update(value).digest('base64url')}`;
}
function staticServer() {
  const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.json': 'application/json' };
  return http.createServer((req, res) => {
    let file;
    try { file = path.resolve(build, '.' + decodeURIComponent(new URL(req.url, origin).pathname)); } catch { res.writeHead(400).end(); return; }
    if (file !== build && !file.startsWith(build + path.sep)) { res.writeHead(403).end(); return; }
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(build, 'index.html');
    res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
}
async function browserLogin(page, account) {
  await page.goto(origin + '/login');
  await page.getByLabel('학교 이메일', { exact: true }).fill(account.email.split('@')[0]);
  const checking = page.waitForResponse(r => r.url() === api + '/auth/login/email-check' && r.request().method() === 'POST');
  await page.getByRole('button', { name: '다음', exact: true }).click(); assert.equal((await checking).status(), 200);
  await page.getByLabel('비밀번호', { exact: true }).fill(account.password);
  const logging = page.waitForResponse(r => r.url() === api + '/auth/login' && r.request().method() === 'POST');
  await page.getByRole('button', { name: '로그인', exact: true }).click(); assert.equal((await logging).status(), 200);
  await expect(page).toHaveURL(origin + (account.role === 'ADMIN' ? '/admin' : '/'));
}
async function main() {
  const existing = [];
  for (const account of known) {
    const data = await login(account.email, account.password); assert.equal(data.user.role, account.role);
    existing.push({ role: account.role, ok: true });
  }
  let newSession;
  for (const student of projection.students.filter(s => s.slot > 30)) {
    const data = await login(student.email, process.env.DEMO_NEW_LOGIN_PASSWORD);
    assert.equal(data.user.id, student.id); assert.equal(data.user.role, 'USER');
    if (student.slot === 31) newSession = data;
  }
  let newAdminLogin = false;
  if (appliedLocal) {
    const admin = await login('exhibition_admin@hknu.ac.kr', process.env.DEMO_NEW_LOGIN_PASSWORD);
    assert.equal(admin.user.id, 54); assert.equal(admin.user.role, 'ADMIN'); newAdminLogin = true;
  }
  const publicProfiles = await request('GET', '/roommates?page=0&size=100');
  assert.equal(publicProfiles.totalElements, 50);
  assert.equal(publicProfiles.content.length, 50);
  assert.deepEqual(publicProfiles.content.map(p => p.nickname).sort(), projection.students.map(s => s.nickname).sort());
  const survey = await request('GET', '/users/me/profile-survey', newSession.accessToken);
  const preference = await request('GET', '/users/me/roommate-preferences', newSession.accessToken);
  assert.equal(survey.completed, true); assert.equal(preference.completed, true);
  const matches = await request('GET', '/roommates?page=0&size=100', newSession.accessToken);
  assert.equal(matches.totalElements, 49);
  const list = await request('GET', '/posts?page=0&size=100');
  assert.equal(list.content.length, list.totalElements);
  for (const post of projection.posts) {
    const actual = list.content.find(p => p.id === post.id); assert(actual, 'Expected mapped post'); assert.equal(actual.title, post.title);
  }
  let checkedMessages = 0;
  for (const chat of projection.chats) {
    const messages = await request('GET', `/chat/rooms/${chat.id}/messages?page=0&size=100`, seedToken(chat.caller));
    assert.deepEqual(messages.content.slice().sort((a, b) => a.id - b.id).map(m => m.text), chat.messages);
    checkedMessages += messages.content.length;
  }
  assert.equal(checkedMessages, 33);
  const server = staticServer();
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(13000, '127.0.0.1', resolve); });
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
    const page = await browser.newPage(); page.on('dialog', d => d.accept());
    const pageErrors = []; page.on('pageerror', error => pageErrors.push(error.name));
    const new31 = projection.students.find(s => s.slot === 31);
    await browserLogin(page, { email: new31.email, password: process.env.DEMO_NEW_LOGIN_PASSWORD, role: 'USER' });
    const roomResponse = page.waitForResponse(r => r.url().startsWith(api + '/roommates?') && r.status() === 200);
    await page.goto(origin + '/roommates'); assert.equal((await (await roomResponse).json()).data.totalElements, 49);
    await expect(page.locator('body')).toContainText('룸메');
    await page.screenshot({ path: path.join(output, 'roommates.png'), fullPage: true });
    const post = projection.posts.find(p => p.key === 'post/자유게시판/1');
    await page.goto(origin + '/community?q=' + encodeURIComponent(post.title));
    await expect(page.getByText(post.title, { exact: true }).first()).toBeVisible();
    await page.getByText(post.title, { exact: true }).first().click();
    await expect(page.locator('body')).toContainText(post.content);
    await expect(page.locator('body')).toContainText(post.comment);
    await expect(page.locator('body')).toContainText(post.reply);
    await page.screenshot({ path: path.join(output, 'community.png'), fullPage: true });
    // Unknown old seed passwords are never reset. The chat UI uses a signed local test session only.
    const chat = projection.chats.find(c => c.key === 'chat/morning');
    await page.evaluate(token => { localStorage.clear(); localStorage.setItem('accessToken', token); }, seedToken(chat.caller));
    await page.goto(origin + '/chat?room=' + chat.id);
    await expect(page.locator('body')).toContainText(chat.messages[0], { timeout: 15000 });
    await expect(page.locator('body')).toContainText(chat.messages.at(-1));
    await page.screenshot({ path: path.join(output, 'chat.png'), fullPage: true });
    await page.evaluate(() => localStorage.clear());
    await browserLogin(page, known.find(a => a.role === 'ADMIN'));
    assert.equal(pageErrors.length, 0, 'Browser runtime errors');
    const summary = { schema, existingKnownLogins: existing, newLogins: 20, newAdminLogin, preservedAccountHashesCheckedSeparately: 33,
      publicProfiles: publicProfiles.totalElements, authenticatedMatches: matches.totalElements,
      latestPostsChecked: projection.posts.length, latestMessagesChecked: checkedMessages,
      browser: { realNewLogin: true, roommatePage: true, communityAndReplies: true, chatWithLocalTestSession: true, realExistingAdminLogin: true, runtimeErrors: 0 },
      unknownExistingSeedPasswordsTested: false, requests: observations };
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(summary, null, 2));
    console.log(JSON.stringify({ ...summary, requests: observations.length }));
  } finally { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error('Isolated merge UI check failed: ' + error.message); process.exitCode = 1; });
