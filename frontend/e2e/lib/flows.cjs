const { expect } = require('@playwright/test');
const origin = 'http://127.0.0.1:18080';

async function responseTo(page, method, path, action, status = 200) {
  const waiting = page.waitForResponse(r => r.url().split('?')[0] === origin + '/api' + path && r.request().method() === method);
  try { await action(); } catch (error) { waiting.catch(() => {}); throw error; }
  const response = await waiting; expect(response.status()).toBe(status); return response;
}
async function login(page, email, password = process.env.E2E_LOGIN_PASSWORD) {
  await page.goto('/login');
  await page.getByLabel('학교 이메일', { exact: true }).fill(email.split('@')[0]);
  await responseTo(page, 'POST', '/auth/login/email-check', () => page.getByRole('button', { name: '다음', exact: true }).click());
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  const response = await responseTo(page, 'POST', '/auth/login', () => page.getByRole('button', { name: '로그인', exact: true }).click());
  const user = (await response.json()).data.user;
  expect(user.schoolEmail).toBe(email);
  await expect(page).toHaveURL('http://127.0.0.1:13000' + (user.role === 'ADMIN' ? '/admin' : '/'));
}
async function logout(page) {
  await page.getByRole('button', { name: '프로필 메뉴 열기', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await expect.poll(() => page.evaluate(() => Boolean(localStorage.getItem('accessToken')))).toBe(false);
}
async function control(operation, data = {}) {
  const response = await fetch(process.env.E2E_CONTROL_URL + '/' + operation, {
    method: 'POST', headers: { Authorization: `Bearer ${process.env.E2E_CONTROL_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(data), signal: AbortSignal.timeout(120_000),
  });
  if (!response.ok) throw new Error('Managed E2E control failed');
  return response.json();
}
async function browserRequest(page, method, path, data, { multipart = false, anonymous = false } = {}) {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error('Only local API paths allowed');
  return page.evaluate(async ({ method, path, data, multipart, anonymous }) => {
    const headers = {}, token = localStorage.getItem('accessToken');
    if (!anonymous && token) headers.Authorization = `Bearer ${token}`;
    let body;
    if (data !== undefined) {
      if (multipart) { body = new FormData(); body.append('request', new Blob([JSON.stringify(data)], { type: 'application/json' })); }
      else { headers['Content-Type'] = 'application/json'; body = JSON.stringify(data); }
    }
    const response = await fetch('http://127.0.0.1:18080/api' + path, { method, headers, body });
    const json = await response.json();
    return { status: response.status, code: json.errorCode, data: json.data };
  }, { method, path, data, multipart, anonymous });
}
async function createPost(page, title, content = 'E2E 실제 MySQL 본문', image = false) {
  await page.goto('/community/posts/new');
  await page.locator('#community-post-title').fill(title); await page.locator('#community-post-content').fill(content);
  if (image) await page.getByLabel('첨부할 이미지 선택').setInputFiles({ name: 'e2e-pixel.png', mimeType: 'image/png',
    buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=', 'base64') });
  const response = await responseTo(page, 'POST', '/posts', () => page.getByRole('button', { name: '게시글 등록', exact: true }).click());
  const id = (await response.json()).data;
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible(); return id;
}
async function completeSurvey(page) {
  await page.goto('/profile?tab=survey');
  await page.getByRole('button', { name: '내 설문 작성하기', exact: true }).click();
  for (const select of await page.locator('select[id^="survey-"]').all()) await select.selectOption({ index: 1 });
  for (const group of await page.locator('fieldset:has(> legend)').all()) await group.getByRole('button').first().click();
  await responseTo(page, 'PUT', '/users/me/profile-survey', () => page.getByRole('button', { name: '저장', exact: true }).click());
  await expect(page.getByRole('button', { name: '설문 수정', exact: true })).toBeVisible();
}
async function communityRoom(page, peerPage, title) {
  const postId = await createPost(page, title);
  await peerPage.goto(`/community/posts/${postId}`);
  const response = await responseTo(peerPage, 'POST', '/chat/rooms', () => peerPage.getByRole('button', { name: 'E2E사용자님에게 채팅 보내기', exact: true }).click());
  const roomId = (await response.json()).data.id;
  await page.goto(`/chat?room=${roomId}`);
  await expect(peerPage).toHaveURL(new RegExp(`/chat\\?room=${roomId}$`));
  return { postId, roomId };
}
async function send(page, text) {
  await page.locator('#chat-message').fill(text);
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '메시지 전송', exact: true }).click();
  await expect(page.getByText(text, { exact: true }).first()).toBeVisible();
}
async function probe(page, roomId) {
  await page.evaluate(room => {
    const state = { connected: false, error: false, closed: false, texts: [] };
    const socket = new WebSocket('ws://127.0.0.1:18080/ws/chat?e2eProbe=1', ['v12.stomp']);
    socket.onopen = () => socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${localStorage.getItem('accessToken')}\nheart-beat:0,0\n\n\0`);
    socket.onmessage = ({ data }) => {
      if (data.startsWith('CONNECTED')) { state.connected = true; socket.send(`SUBSCRIBE\nid:probe\ndestination:/sub/chat/rooms/${room}\n\n\0`); }
      if (data.startsWith('ERROR')) state.error = true;
      if (data.startsWith('MESSAGE')) { try { state.texts.push(JSON.parse(data.split(/\r?\n\r?\n/).slice(1).join('\n\n').replace(/\0$/, '')).text); } catch {} }
    };
    socket.onclose = () => { state.closed = true; };
    socket.onerror = () => { state.error = true; };
    window.__e2eProbe = { state, send: text => socket.readyState === 1 && socket.send(`SEND\ndestination:/pub/chat/rooms/${room}/messages\ncontent-type:application/json\n\n${JSON.stringify({ text })}\0`), close: () => socket.close() };
  }, roomId);
  await expect.poll(() => page.evaluate(() => { const s = window.__e2eProbe.state; return s.connected || s.error || s.closed; })).toBe(true);
}
const probeState = page => page.evaluate(() => window.__e2eProbe.state);
const probeSend = (page, text) => page.evaluate(text => window.__e2eProbe.send(text), text);
const closeProbe = page => page.evaluate(() => window.__e2eProbe?.close()).catch(() => {});

module.exports = { responseTo, login, logout, control, browserRequest, createPost, completeSurvey, communityRoom, send, probe, probeState, probeSend, closeProbe };
