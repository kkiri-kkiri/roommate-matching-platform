const { expect } = require('@playwright/test');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { database } = require('./database.cjs');
const flows = require('./flows.cjs');
const API = 'http://127.0.0.1:18080/api';
const title = name => `${process.env.E2E_DATABASE}${process.env.E2E_CCRA_ATTEMPT ? ' ' + process.env.E2E_CCRA_ATTEMPT : ''} ${name}`;
const nickname = name => name + (process.env.E2E_CCRA_NICK_SUFFIX || '');
const pixel = name => ({ name, mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=', 'base64') });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const snapshot = () => database('snapshot');
async function actors(page, peerPage, adminPage) {
  for (const actor of [page, peerPage, adminPage].filter(Boolean)) actor.on('dialog', dialog => dialog.accept());
  await flows.login(page, process.env.E2E_LOGIN_EMAIL);
  if (peerPage) await flows.login(peerPage, process.env.E2E_SECOND_EMAIL);
  if (adminPage) await flows.login(adminPage, process.env.E2E_ADMIN_EMAIL);
  const db = snapshot();
  return { a: db.profiles.find(p => p.nickname === nickname('E2E사용자')).user_id,
    b: db.profiles.find(p => p.nickname === nickname('E2E상대')).user_id,
    admin: db.profiles.find(p => p.nickname === nickname('E2E관리자')).user_id };
}
async function post(page, name, { images = [], anonymous = false } = {}) {
  const heading = title(name);
  if (!images.length && !anonymous) return flows.createPost(page, heading);
  await page.goto('/community/posts/new');
  await page.locator('#community-post-title').fill(heading);
  await page.locator('#community-post-content').fill(title(`${name} 본문`));
  if (anonymous) await page.getByRole('checkbox').check();
  if (images.length) await page.getByLabel('첨부할 이미지 선택').setInputFiles(images.map(pixel));
  const response = await flows.responseTo(page, 'POST', '/posts', () => page.getByRole('button', { name: '게시글 등록', exact: true }).click());
  const id = (await response.json()).data;
  await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  return id;
}
async function comment(page, postId, content, { anonymous = false, parentId } = {}) {
  await page.goto(`/community/posts/${postId}`);
  const inputId = parentId ? `reply-to-${parentId}` : 'community-new-comment';
  if (parentId) await page.locator(`#comment-${parentId}`).getByRole('button', { name: '답글', exact: true }).click();
  const input = page.locator(`#${inputId}`), form = input.locator('..');
  await input.fill(content);
  if (anonymous) await form.getByRole('checkbox').check();
  const response = await flows.responseTo(page, 'POST', parentId ? `/comments/${parentId}/replies` : `/posts/${postId}/comments`,
    () => form.getByRole('button', { name: parentId ? '답글 등록' : '댓글 등록', exact: true }).click());
  const id = (await response.json()).data;
  await expect(page.locator(`#comment-${id}`)).toContainText(content);
  return id;
}
async function isolatedRoom(page, peerPage, name) {
  // Normal COMMUNITY rooms are shared by the user pair. An anonymous source
  // has its own contractual room key, so a leave test cannot poison later cases.
  const postId = await post(page, name, { anonymous: true });
  await peerPage.goto(`/community/posts/${postId}`);
  const response = await flows.responseTo(peerPage, 'POST', '/chat/rooms',
    () => peerPage.getByRole('button', { name: '익명님에게 채팅 보내기', exact: true }).click());
  const roomId = (await response.json()).data.id;
  await page.goto(`/chat?room=${roomId}`);
  await expect(peerPage).toHaveURL(new RegExp(`/chat\\?room=${roomId}$`));
  return { postId, roomId };
}
async function editComment(page, postId, id, content) {
  await page.goto(`/community/posts/${postId}`);
  const entry = page.locator(`#comment-${id}`);
  await entry.getByRole('button', { name: '수정', exact: true }).click();
  await entry.locator(`#edit-${id}`).fill(content);
  await flows.responseTo(page, 'PATCH', `/comments/${id}`, () => entry.getByRole('button', { name: '수정 완료', exact: true }).click());
  await expect(entry).toContainText(content);
}
async function deleteComment(page, postId, id) {
  await page.goto(`/community/posts/${postId}`);
  await page.locator(`#comment-${id}`).getByRole('button', { name: '삭제', exact: true }).click();
  await flows.responseTo(page, 'DELETE', `/comments/${id}`, () => page.getByRole('dialog').getByRole('button', { name: '삭제하기', exact: true }).click());
}
async function deletePost(page, id) {
  await page.goto(`/community/posts/${id}`);
  await page.getByRole('button', { name: '삭제', exact: true }).first().click();
  await flows.responseTo(page, 'DELETE', `/posts/${id}`, () => page.getByRole('dialog').getByRole('button', { name: '삭제하기', exact: true }).click());
}
async function request(page, method, url, data, options) {
  const result = await flows.browserRequest(page, method, url, data, options);
  expect(result.status, `${method} ${url}`).toBe(200);
  return result.data;
}
async function report(page, postId, commentId) {
  await page.goto(`/community/posts/${postId}`);
  if (commentId) await page.locator(`#comment-${commentId}`).getByRole('button', { name: '신고', exact: true }).click();
  else await page.getByRole('button', { name: '게시글 신고', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByText('기타', { exact: true }).click();
  await dialog.locator('#community-other-reason').fill(title('검토 사유'));
  const response = await flows.responseTo(page, 'POST', '/reports', () => dialog.getByRole('button', { name: '신고하기', exact: true }).click());
  await expect(dialog).toHaveCount(0);
  return (await response.json()).data;
}
async function processReport(adminPage, id, actionLabel) {
  await adminPage.goto(`/admin?view=reports&q=${id}`);
  await adminPage.getByRole('button', { name: `#${id}`, exact: true }).click();
  await adminPage.getByRole('dialog').getByRole('button', { name: actionLabel, exact: true }).click();
  return flows.responseTo(adminPage, 'PATCH', `/admin/reports/${id}`, () => adminPage.getByRole('dialog').getByRole('button', { name: actionLabel, exact: true }).click());
}
async function memberStatus(adminPage, id, status) {
  await adminPage.goto(`/admin?view=members&q=${encodeURIComponent(process.env.E2E_SECOND_EMAIL)}`);
  await adminPage.getByRole('button', { name: process.env.E2E_SECOND_EMAIL, exact: true }).click();
  const label = status === 'ACTIVE' ? '이용 정지 해제' : '이용 정지';
  await adminPage.getByRole('dialog').getByRole('button', { name: label, exact: true }).click();
  await flows.responseTo(adminPage, 'PATCH', `/admin/members/${id}/status`, () => adminPage.getByRole('dialog').getByRole('button', { name: label, exact: true }).click());
}
function fileState(url) {
  const directory = path.resolve(process.env.E2E_IMAGE_DIRECTORY);
  const expected = path.resolve(__dirname, '../../../backend/build/e2e', process.env.E2E_DATABASE.slice('kkiri_e2e_'.length), 'uploads');
  if (directory !== expected || !/^\/api\/post-images\/[A-Za-z0-9_-]+\.png$/.test(url)) throw new Error('Unsafe isolated image path');
  const file = path.join(directory, path.basename(url));
  const exists = fs.existsSync(file);
  return { file, exists, ...(exists ? { bytes: fs.statSync(file).size, sha256: createHash('sha256').update(fs.readFileSync(file)).digest('hex') } : {}) };
}
async function imageGet(page, url) {
  return page.evaluate(async url => {
    const response = await fetch('http://127.0.0.1:18080' + url, { cache: 'no-store' });
    const bytes = await response.arrayBuffer();
    return { status: response.status, type: response.headers.get('content-type'), cache: response.headers.get('cache-control'), bytes: bytes.byteLength };
  }, url);
}
function observeSockets(page, actor, output, { dropSend } = {}) {
  return page.routeWebSocket('**/ws/chat*', async ws => {
    const server = ws.connectToServer();
    const parse = (message, direction) => {
      const text = String(message), command = text.split(/\r?\n/)[0];
      const row = { actor, direction, command };
      if (command === 'MESSAGE') {
        try { row.message = JSON.parse(text.split(/\r?\n\r?\n/).slice(1).join('\n\n').replace(/\0$/, '')); } catch { row.invalidJson = true; }
      }
      output.push(row);
      return command;
    };
    ws.onMessage(message => { const command = parse(message, 'sent'); if (!(command === 'SEND' && dropSend?.())) server.send(message); });
    server.onMessage(message => { parse(message, 'received'); ws.send(message); });
  });
}
function caseEvidence(evidence, name, data) { evidence.note(name, data); }
module.exports = { ...flows, API, title, nickname, pause, pixel, actors, post, isolatedRoom, comment, editComment, deleteComment, deletePost,
  request, report, processReport, memberStatus, snapshot, fileState, imageGet, observeSockets, caseEvidence };
