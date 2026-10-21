const { test, expect, database } = require('../fixtures.cjs');
const { responseTo, login, browserRequest, communityRoom, send, probe, probeState, probeSend, closeProbe } = require('../lib/flows.cjs');

test('E: Community → Chat, real two-browser STOMP delivery, reconnect history and revoked membership', async ({ page, peerPage, adminPage, evidence }) => {
  test.setTimeout(150_000);
  await login(page, process.env.E2E_LOGIN_EMAIL); await login(peerPage, process.env.E2E_SECOND_EMAIL);
  let roomId;
  try {
    await test.step('Create a community-sourced room through React with exactly two committed members', async () => {
      const created = await communityRoom(page, peerPage, 'E2E 채팅 출처 게시글'); roomId = created.roomId;
      const snapshot = database('snapshot');
      expect(snapshot.rooms).toEqual([expect.objectContaining({ chat_room_id: roomId, source_post_id: created.postId, category: 'COMMUNITY', status: 'ACTIVE' })]);
      expect(snapshot.members.filter(m => m.chat_room_id === roomId)).toHaveLength(2);
      expect(snapshot.members.every(m => m.is_left === false)).toBe(true);
    });
    await test.step('A/B UI messages traverse actual WebSocket SEND/MESSAGE and commit once per message', async () => {
      await send(page, 'E2E A 실시간 메시지');
      await expect(peerPage.getByText('E2E A 실시간 메시지', { exact: true }).first()).toBeVisible();
      await send(peerPage, 'E2E B 실시간 답장');
      await expect(page.getByText('E2E B 실시간 답장', { exact: true }).first()).toBeVisible();
      expect(evidence.sockets.some(s => s.actor === 'A' && s.sent.includes('SEND'))).toBe(true);
      expect(evidence.sockets.some(s => s.actor === 'B' && s.received.some(m => m.text === 'E2E A 실시간 메시지'))).toBe(true);
      const snapshot = database('snapshot'), a = snapshot.profiles.find(p => p.nickname === 'E2E사용자').user_id,
        b = snapshot.profiles.find(p => p.nickname === 'E2E상대').user_id;
      expect(snapshot.messages.map(m => ({ sender: m.sender_id, text: m.content }))).toEqual([
        { sender: a, text: 'E2E A 실시간 메시지' }, { sender: b, text: 'E2E B 실시간 답장' },
      ]);
    });
    await test.step('Reload reconnects STOMP and obtains the same committed history over HTTP', async () => {
      await responseTo(peerPage, 'GET', `/chat/rooms/${roomId}/messages`, () => peerPage.reload());
      await expect(peerPage.getByText('E2E A 실시간 메시지', { exact: true }).first()).toBeVisible();
      await expect(peerPage.getByText('E2E B 실시간 답장', { exact: true }).first()).toBeVisible();
      await send(peerPage, 'E2E 재접속 후 메시지');
      await expect(page.getByText('E2E 재접속 후 메시지', { exact: true }).first()).toBeVisible();
      expect(database('snapshot').messages).toHaveLength(3);
    });
    await test.step('Authenticated non-member is denied HTTP reads and a real STOMP subscription', async () => {
      await login(adminPage, process.env.E2E_ADMIN_EMAIL);
      const before = database('snapshot');
      for (const path of [`/chat/rooms/${roomId}`, `/chat/rooms/${roomId}/messages`]) {
        const result = await browserRequest(adminPage, 'GET', path);
        expect(result.status).toBe(403); expect(result.code).toBe('FORBIDDEN');
      }
      await probe(adminPage, roomId);
      await expect.poll(async () => { const state = await probeState(adminPage); return state.error || state.closed; }).toBe(true);
      expect(database('snapshot')).toEqual(before);
      await closeProbe(adminPage);
    });
    await test.step('Leaving through UI revokes an already subscribed session and rejects SEND without DB writes', async () => {
      await probe(peerPage, roomId);
      await send(page, 'E2E 권한 철회 전 양성 대조');
      await expect.poll(async () => (await probeState(peerPage)).texts.includes('E2E 권한 철회 전 양성 대조')).toBe(true);
      await peerPage.getByRole('button', { name: 'E2E사용자 채팅방 옵션', exact: true }).click();
      await peerPage.getByRole('button', { name: '방 나가기', exact: true }).click();
      await responseTo(peerPage, 'DELETE', `/chat/rooms/${roomId}/members/me`, () => peerPage.getByRole('dialog').getByRole('button', { name: '나가기', exact: true }).click());
      const left = database('snapshot');
      const b = left.profiles.find(p => p.nickname === 'E2E상대').user_id;
      expect(left.members.find(m => m.user_id === b && m.chat_room_id === roomId).is_left).toBe(true);
      await send(page, 'E2E 탈퇴 후 전달 차단');
      await probeSend(peerPage, 'E2E 탈퇴 후 금지 SEND');
      await expect.poll(async () => { const state = await probeState(peerPage); return state.error || state.closed; }).toBe(true);
      expect((await probeState(peerPage)).texts).not.toContain('E2E 탈퇴 후 전달 차단');
      const after = database('snapshot');
      expect(after.messages.some(m => m.content === 'E2E 탈퇴 후 금지 SEND')).toBe(false);
      expect(after.messages.filter(m => m.content === 'E2E 탈퇴 후 전달 차단')).toHaveLength(1);
      const denied = await browserRequest(peerPage, 'GET', `/chat/rooms/${roomId}/messages`);
      expect(denied.status).toBe(403);
      evidence.note('revocation', { HTTP: denied.status, forbiddenMessageStored: false, oldSubscriptionDelivered: false });
    });
  } finally { await closeProbe(peerPage); await closeProbe(adminPage); }
});
