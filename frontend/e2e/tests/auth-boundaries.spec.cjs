const { test, expect, database } = require('../fixtures.cjs');
const { responseTo, login, logout, control, browserRequest, communityRoom, send, probe, probeState, probeSend, closeProbe } = require('../lib/flows.cjs');

async function memberStatus(adminPage, id, next, email) {
  await adminPage.goto('/admin?view=members');
  await adminPage.getByRole('button', { name: email, exact: true }).click();
  const name = next === 'SUSPENDED' ? '이용 정지' : '이용 정지 해제';
  await adminPage.getByRole('dialog').getByRole('button', { name, exact: true }).click();
  await responseTo(adminPage, 'PATCH', `/admin/members/${id}/status`, () => adminPage.getByRole('dialog').getByRole('button', { name, exact: true }).click());
  const close = adminPage.getByRole('dialog').getByRole('button', { name: '닫기', exact: true });
  if (await close.isVisible()) await close.click();
}

test('G: unauthenticated, suspended and naturally expired real login sessions fail closed across domains', async ({ page, peerPage, adminPage, evidence }) => {
  test.setTimeout(240_000);
  await login(page, process.env.E2E_LOGIN_EMAIL); await login(peerPage, process.env.E2E_SECOND_EMAIL);
  await login(adminPage, process.env.E2E_ADMIN_EMAIL);
  const initial = database('snapshot'), a = initial.profiles.find(p => p.nickname === 'E2E사용자').user_id,
    b = initial.profiles.find(p => p.nickname === 'E2E상대').user_id;
  const { roomId, postId } = await communityRoom(page, peerPage, 'E2E 인증 경계 출처');
  let changedLifetime = false;
  try {
    await test.step('Anonymous protected writes/read requests return 401 without business mutations', async () => {
      const before = database('snapshot');
      for (const [method, path, data] of [
        ['POST', `/roommates/${a}/likes`], ['GET', '/users/me/profile-survey'], ['GET', '/chat/rooms'],
        ['POST', `/posts/${postId}/comments`, { content: 'anonymous' }],
        ['POST', '/calendar/categories/custom', { label: 'anonymous', color: '#006a65' }],
        ['POST', '/reports', { targetType: 'POST', targetId: postId, reason: '도배·스팸' }],
        ['GET', '/admin/dashboard'],
      ]) { const result = await browserRequest(page, method, path, data, { anonymous: true }); expect(result.status).toBe(401); }
      expect(database('snapshot')).toEqual(before);
    });
    await test.step('Admin suspension invalidates an existing JWT and an established STOMP subscription', async () => {
      await probe(peerPage, roomId); await send(page, 'E2E 정지 전 양성 대조');
      await expect.poll(async () => (await probeState(peerPage)).texts.includes('E2E 정지 전 양성 대조')).toBe(true);
      await memberStatus(adminPage, b, 'SUSPENDED', process.env.E2E_SECOND_EMAIL);
      expect(database('snapshot').users.find(u => u.user_id === b).status).toBe('SUSPENDED');
      const before = database('snapshot');
      for (const [method, path, data] of [['GET', '/auth/me'], ['POST', `/roommates/${a}/likes`], ['POST', `/posts/${postId}/comments`, { content: 'suspended' }]]) {
        expect((await browserRequest(peerPage, method, path, data)).status).toBe(401);
      }
      await send(page, 'E2E 정지 후 전달 차단');
      await probeSend(peerPage, 'E2E 정지 후 금지 SEND');
      await expect.poll(async () => { const state = await probeState(peerPage); return state.error || state.closed; }).toBe(true);
      expect((await probeState(peerPage)).texts).not.toContain('E2E 정지 후 전달 차단');
      const after = database('snapshot');
      expect(after.messages.some(m => m.content === 'E2E 정지 후 금지 SEND')).toBe(false);
      expect(after.likes).toEqual(before.likes); expect(after.comments).toEqual(before.comments);
      await closeProbe(peerPage);
      await memberStatus(adminPage, b, 'ACTIVE', process.env.E2E_SECOND_EMAIL);
      expect(database('snapshot').users.find(u => u.user_id === b).status).toBe('ACTIVE');
      await login(peerPage, process.env.E2E_SECOND_EMAIL); await peerPage.goto(`/chat?room=${roomId}`);
      expect((await browserRequest(peerPage, 'GET', '/auth/me')).status).toBe(200);
    });
    await test.step('A real login-issued short-lived JWT expires naturally; HTTP and existing STOMP sessions reject it', async () => {
      changedLifetime = true;
      expect((await control('restart', { ttlSeconds: 8 })).restarted).toBe(true);
      // A's already-issued long-lived token remains valid. Wait for its actual UI socket reconnect first.
      await page.locator('#chat-message').fill('E2E 만료 전 양성 대조');
      await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeEnabled({ timeout: 20_000 });
      await login(peerPage, process.env.E2E_SECOND_EMAIL); await peerPage.goto(`/chat?room=${roomId}`);
      expect((await browserRequest(peerPage, 'GET', '/auth/me')).status).toBe(200);
      await probe(peerPage, roomId); await send(page, 'E2E 만료 전 양성 대조');
      await expect.poll(async () => (await probeState(peerPage)).texts.includes('E2E 만료 전 양성 대조')).toBe(true);
      await expect.poll(async () => (await browserRequest(peerPage, 'GET', '/auth/me')).status,
        { timeout: 15_000, intervals: [100, 200, 500] }).toBe(401);
      const before = database('snapshot');
      expect((await browserRequest(peerPage, 'POST', `/roommates/${a}/likes`)).status).toBe(401);
      await send(page, 'E2E 만료 후 전달 차단');
      await probeSend(peerPage, 'E2E 만료 후 금지 SEND');
      await expect.poll(async () => { const state = await probeState(peerPage); return state.error || state.closed; }).toBe(true);
      expect((await probeState(peerPage)).texts).not.toContain('E2E 만료 후 전달 차단');
      const after = database('snapshot');
      expect(after.messages.some(m => m.content === 'E2E 만료 후 금지 SEND')).toBe(false);
      expect(after.likes).toEqual(before.likes);
      evidence.note('expiry', { tokenSource: 'real UI login', naturalExpiry: true, expiredHTTP: 401, expiredSENDStored: false, expiredSubscriptionDelivered: false });
    });
    await test.step('Withdrawn accounts cannot use a previously valid JWT or log in again; denied writes preserve DB rows', async () => {
      await control('restart', { ttlSeconds: 3600 }); changedLifetime = false;
      await login(peerPage, process.env.E2E_SECOND_EMAIL);
      await adminPage.goto('/admin?view=members');
      await adminPage.getByRole('button', { name: process.env.E2E_SECOND_EMAIL, exact: true }).click();
      await adminPage.getByRole('dialog').getByRole('button', { name: '회원 탈퇴 처리', exact: true }).click();
      await responseTo(adminPage, 'PATCH', `/admin/members/${b}/status`, () => adminPage.getByRole('dialog').getByRole('button', { name: '회원 탈퇴', exact: true }).click());
      const before = database('snapshot');
      expect(before.users.find(u => u.user_id === b).status).toBe('WITHDRAWN');
      expect((await browserRequest(peerPage, 'GET', '/auth/me')).status).toBe(401);
      expect((await browserRequest(peerPage, 'POST', `/roommates/${a}/likes`)).status).toBe(401);
      expect((await browserRequest(peerPage, 'POST', '/auth/login', { email: process.env.E2E_SECOND_EMAIL, password: process.env.E2E_LOGIN_PASSWORD }, { anonymous: true })).status).toBe(401);
      expect(database('snapshot')).toEqual(before);
    });
  } finally {
    await closeProbe(peerPage);
    if (changedLifetime) await control('restart', { ttlSeconds: 3600 });
  }
});

test('E/G: logout blocks UI and tokenless HTTP; retained authenticated socket must also stop sending and receiving', async ({ page, peerPage, evidence }) => {
  test.setTimeout(120_000);
  await login(page, process.env.E2E_LOGIN_EMAIL); await login(peerPage, process.env.E2E_SECOND_EMAIL);
  const { roomId } = await communityRoom(page, peerPage, 'E2E 로그아웃 철회 출처');
  try {
    await probe(page, roomId);
    await send(peerPage, 'E2E 로그아웃 전 양성 대조');
    await expect.poll(async () => (await probeState(page)).texts.includes('E2E 로그아웃 전 양성 대조')).toBe(true);
    await logout(page);
    await expect.poll(() => evidence.sockets.filter(s => s.actor === 'A' && !s.url.includes('e2eProbe')).every(s => s.closed)).toBe(true);
    expect((await browserRequest(page, 'GET', '/chat/rooms')).status).toBe(401);
    // Keep the pre-existing authenticated socket deliberately, without reading/injecting a token.
    // This requirement is checked strictly; a stateless logout limitation is reported as a failure.
    await send(peerPage, 'E2E 로그아웃 후 수신 금지');
    await expect.poll(async () => { const s = await probeState(page); return s.error || s.closed || s.texts.includes('E2E 로그아웃 후 수신 금지'); },
      { timeout: 3000 }).toBe(true).catch(() => {});
    const received = (await probeState(page)).texts.includes('E2E 로그아웃 후 수신 금지');
    await probeSend(page, 'E2E 로그아웃 후 발신 금지');
    await expect.poll(async () => {
      const s = await probeState(page);
      return s.error || s.closed || database('snapshot').messages.some(m => m.content === 'E2E 로그아웃 후 발신 금지');
    }, { timeout: 5000 }).toBe(true).catch(() => {});
    const stored = database('snapshot').messages.some(m => m.content === 'E2E 로그아웃 후 발신 금지');
    evidence.note('logout retained-session audit', { tokenlessHTTP: 401, frontendSocketClosed: true,
      retainedSocketReceived: received, retainedSocketStoredMessage: stored,
      classification: received || stored ? 'unsupported server-side logout revocation: client-only stateless logout' : 'blocked within bounded observation' });
    expect.soft(received, 'An already authenticated socket still received after logout').toBe(false);
    expect.soft(stored, 'An already authenticated socket still committed a SEND after logout').toBe(false);
    await closeProbe(page);
    await page.goto('/chat'); await expect(page.locator('#chat-message')).toHaveCount(0);
  } finally { await closeProbe(page); }
});
