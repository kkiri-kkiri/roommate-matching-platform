const { randomUUID } = require('node:crypto');
const { test, expect } = require('../fixtures.cjs');
const h = require('../lib/ccra-revalidation.cjs');

// Only these nine cases run. Shared fixtures are explicitly in retained mode.
for (const reply of [false, true]) for (const anonymous of [false, true]) {
  test(`RV-01 ${reply ? 'reply' : 'comment'} anonymous=${anonymous}: committed POST, failed GET, read-only retry`, async ({ page, evidence }) => {
    test.setTimeout(150000);
    await h.actors(page);
    const postId = await h.post(page, `RV01 ${reply} ${anonymous}`);
    const parentId = reply ? await h.comment(page, postId, h.title('RV01 parent')) : null;
    if (reply) await page.locator(`#comment-${parentId}`).getByRole('button', { name: '답글', exact: true }).click();
    const input = page.locator(reply ? `#reply-to-${parentId}` : '#community-new-comment');
    const content = h.title(`RV01 committed ${reply} ${anonymous}`);
    const dialogs = []; page.on('dialog', dialog => dialogs.push(dialog.message()));
    const writes = [];
    page.on('request', request => {
      if (request.method() === 'POST' && /\/(comments|replies)$/.test(new URL(request.url()).pathname)
        && request.postDataJSON()?.content === content) writes.push(request.url());
    });
    await input.fill(content);
    if (anonymous) await input.locator('..').getByRole('checkbox').check();
    const detailUrl = `${h.API}/posts/${postId}`;
    await page.route(detailUrl, route => route.fulfill({ status: 503, contentType: 'application/json',
      body: JSON.stringify({ success: false, message: 'CCRA injected follow-up GET failure', data: null, errorCode: 'INTERNAL_SERVER_ERROR' }) }), { times: 1 });
    const failure = page.waitForResponse(response => response.url() === detailUrl && response.status() === 503);
    const response = await h.responseTo(page, 'POST', reply ? `/comments/${parentId}/replies` : `/posts/${postId}/comments`,
      () => input.locator('..').getByRole('button', { name: reply ? '답글 등록' : '댓글 등록', exact: true }).click());
    const id = (await response.json()).data;
    await failure;
    await expect(page.getByRole('alert')).toContainText('댓글 변경은 저장되었지만');
    if (reply) await expect(input).toHaveCount(0); else await expect(input).toHaveValue('');
    const storedBeforeRetry = h.snapshot().comments.filter(row => row.post_id === postId && row.content === content);
    expect(storedBeforeRetry).toHaveLength(1);
    expect(storedBeforeRetry[0]).toMatchObject({ comment_id: id, anonymous, parent_comment_id: parentId });
    await h.responseTo(page, 'GET', `/posts/${postId}`, () => page.getByRole('button', { name: '댓글 목록 새로고침', exact: true }).click());
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page.locator(`#comment-${id}`)).toContainText(content);
    const storedAfterRetry = h.snapshot().comments.filter(row => row.post_id === postId && row.content === content);
    expect(storedAfterRetry).toEqual(storedBeforeRetry); expect(writes).toHaveLength(1); expect(dialogs).toEqual([]);
    const detail = await h.request(page, 'GET', `/posts/${postId}`);
    const item = detail.comments.find(comment => comment.id === id);
    expect(item.anonymous).toBe(anonymous); expect(item.mine).toBe(true);
    if (anonymous) expect(item.author.id).toBeNull();
    evidence.note('RV01 saved-vs-refresh', { postId, id, reply, anonymous, postStatus: response.status(), injectedGetStatus: 503,
      postRequests: writes.length, refreshRetryStatus: 200, storedBeforeRetry, storedAfterRetry, dialogs });
  });
}

test('RV-02 Existing comment/reply CRUD and anonymity remain compatible', async ({ page, peerPage, evidence }) => {
  test.setTimeout(180000);
  await h.actors(page, peerPage);
  const postId = await h.post(page, 'RV02 CRUD');
  const normal = await h.comment(peerPage, postId, h.title('RV02 normal'));
  const anonymous = await h.comment(peerPage, postId, h.title('RV02 anonymous'), { anonymous: true });
  const reply = await h.comment(page, postId, h.title('RV02 reply'), { parentId: normal });
  const anonymousReply = await h.comment(page, postId, h.title('RV02 anonymous reply'), { parentId: anonymous, anonymous: true });
  for (const [actor, id, name] of [[peerPage, normal, 'normal'], [peerPage, anonymous, 'anonymous'], [page, reply, 'reply'], [page, anonymousReply, 'anonymous reply']]) {
    await h.editComment(actor, postId, id, h.title('RV02 edited ' + name));
  }
  const detail = await h.request(peerPage, 'GET', `/posts/${postId}`);
  expect(detail.comments.find(row => row.id === anonymous).author.id).toBeNull();
  expect(detail.comments.find(row => row.id === anonymousReply).author.id).toBeNull();
  expect((await h.browserRequest(page, 'PATCH', `/comments/${normal}`, { content: 'forbidden' })).status).toBe(403);
  // User-requested CRUD: only the four freshly created rows receive application soft deletion.
  await h.deleteComment(page, postId, reply); await h.deleteComment(page, postId, anonymousReply);
  await h.deleteComment(peerPage, postId, normal); await h.deleteComment(peerPage, postId, anonymous);
  const rows = h.snapshot().comments.filter(row => [normal, anonymous, reply, anonymousReply].includes(row.comment_id));
  expect(rows).toHaveLength(4); expect(rows.every(row => row.status === 'DELETED')).toBe(true);
  const after = await h.request(page, 'GET', `/posts/${postId}`); expect(after.comments).toEqual([]);
  evidence.note('RV02 CRUD', { postId, ids: [normal, anonymous, reply, anonymousReply], http: { post: 200, patch: 200, delete: 200, foreignPatch: 403 }, rows });
});

test('RV-03 D1 dropped SEND keeps draft; same-key retry commits once and clears on echo', async ({ page, peerPage, evidence }) => {
  test.setTimeout(150000);
  const frames = [], policy = { dropSend: false };
  const gate = h.gateway(page, 'A', frames, policy); await gate.attached;
  await h.gateway(peerPage, 'B', frames).attached;
  const { a } = await h.actors(page, peerPage);
  const { roomId } = await h.isolatedRoom(page, peerPage, 'RV03 source');
  const draft = h.title('RV03 unconfirmed');
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeDisabled();
  await page.locator('#chat-message').fill(draft);
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeEnabled();
  policy.dropSend = true;
  await page.getByRole('button', { name: '메시지 전송', exact: true }).click();
  const pending = await h.pending(page, a, roomId); expect(pending.text).toBe(draft);
  await h.pause(400);
  const beforeRetry = h.db('snapshot').messages.filter(row => row.client_message_id === pending.clientMessageId);
  expect(beforeRetry).toHaveLength(0);
  await expect(page.locator('#chat-message')).toHaveValue(draft);
  await expect(page.locator('#chat-message')).toHaveAttribute('readonly', '');
  await expect(page.getByRole('button', { name: '메시지 저장 확인·재시도', exact: true })).toBeVisible({ timeout: 15000 });
  policy.dropSend = false;
  await page.getByRole('button', { name: '메시지 저장 확인·재시도', exact: true }).click();
  await expect(page.locator('#chat-message')).toHaveValue('');
  await expect(peerPage.getByText(draft, { exact: true }).first()).toBeVisible();
  const stored = h.db('snapshot').messages.filter(row => row.client_message_id === pending.clientMessageId);
  expect(stored).toHaveLength(1);
  const sends = frames.filter(row => row.command === 'SEND' && row.payload?.clientMessageId === pending.clientMessageId);
  expect(sends).toHaveLength(2); expect(sends.map(row => row.dropped)).toEqual([true, false]);
  expect(await h.pending(page, a, roomId)).toBeNull();
  evidence.note('RV03 draft preservation', { roomId, pending, beforeRetry, stored, frames, clearedAfterCommittedEcho: true });
});

test('RV-04 committed send with lost confirmation survives reload and retry never inserts twice', async ({ page, peerPage, evidence }) => {
  test.setTimeout(150000);
  const frames = [], policy = { dropEcho: false };
  await h.gateway(page, 'A', frames, policy).attached;
  const { a } = await h.actors(page, peerPage);
  const { roomId } = await h.isolatedRoom(page, peerPage, 'RV04 source');
  await expect(page.locator('#chat-message')).toBeVisible();
  // Block only sender REST confirmation; the real SEND still reaches the server and commits.
  const pattern = `${h.API}/chat/rooms/${roomId}/messages*`;
  await page.route(pattern, route => route.fulfill({ status: 503, contentType: 'application/json',
    body: JSON.stringify({ success: false, data: null, errorCode: 'INTERNAL_SERVER_ERROR', message: 'CCRA sender confirmation unavailable' }) }));
  policy.dropEcho = true;
  const text = h.title('RV04 saved ack lost');
  await page.locator('#chat-message').fill(text);
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '메시지 전송', exact: true }).click();
  const pending = await h.pending(page, a, roomId);
  await expect(peerPage.getByText(text, { exact: true }).first()).toBeVisible();
  const before = h.db('snapshot').messages.filter(row => row.client_message_id === pending.clientMessageId);
  expect(before).toHaveLength(1); await expect(page.locator('#chat-message')).toHaveValue(text);
  await page.reload();
  await expect(page.locator('#chat-message')).toHaveValue(text);
  const retry = page.getByRole('button', { name: '메시지 저장 확인·재시도', exact: true });
  await expect(retry).toBeEnabled();
  policy.dropEcho = false;
  await retry.click();
  await expect(page.locator('#chat-message')).toHaveValue('');
  const after = h.db('snapshot').messages.filter(row => row.client_message_id === pending.clientMessageId);
  expect(after.map(row => row.message_id)).toEqual(before.map(row => row.message_id));
  const sends = frames.filter(row => row.command === 'SEND' && row.payload?.clientMessageId === pending.clientMessageId);
  expect(sends).toHaveLength(2); expect(sends[0].payload).toEqual(sends[1].payload);
  const rebroadcast = frames.filter(row => row.command === 'MESSAGE' && row.payload?.clientMessageId === pending.clientMessageId);
  expect(rebroadcast.some(row => row.dropped)).toBe(true); expect(rebroadcast.some(row => !row.dropped)).toBe(true);
  evidence.note('RV04 lost confirmation', { roomId, before, after, pending, reloadRestored: true, frames });
});

test('RV-05 MySQL concurrent same-ID sends and physical UNIQUE enforcement', async ({ page, peerPage, evidence }) => {
  test.setTimeout(180000);
  await h.actors(page, peerPage);
  const { roomId } = await h.isolatedRoom(page, peerPage, 'RV05 source');
  const clientMessageId = randomUUID(), text = h.title('RV05 concurrent');
  const concurrent = await h.rawBurst(page, roomId, Array.from({ length: 8 }, () => ({ text, clientMessageId })), randomUUID());
  expect(concurrent.errors).toBe(0); expect(concurrent.connections).toBe(8);
  expect(new Set(concurrent.received.map(row => row.id)).size).toBe(1);
  const rows = h.db('snapshot').messages.filter(row => row.client_message_id === clientMessageId);
  expect(rows).toHaveLength(1);
  const retry = await h.rawBurst(page, roomId, Array.from({ length: 8 }, () => ({ text, clientMessageId })), randomUUID());
  expect(new Set(retry.received.map(row => row.id))).toEqual(new Set([rows[0].message_id]));
  expect(h.db('snapshot').messages.filter(row => row.client_message_id === clientMessageId)).toHaveLength(1);
  const constraint = h.db('constraint', rows[0].message_id);
  expect(constraint).toMatchObject({ sqlState: '23000', vendorCode: 1062, rolledBack: true });
  const distinctRequests = Array.from({ length: 2 }, () => ({ text, clientMessageId: randomUUID() }));
  const distinct = await h.rawBurst(page, roomId, distinctRequests, randomUUID());
  expect(new Set(distinct.received.map(row => row.id)).size).toBe(2);
  const legacy = await h.rawBurst(page, roomId, [{ text: h.title('RV05 legacy') }, { text: h.title('RV05 legacy') }], randomUUID());
  expect(new Set(legacy.received.map(row => row.id)).size).toBe(2);
  const legacyRows = h.db('snapshot').messages.filter(row => row.chat_room_id === roomId && row.content === h.title('RV05 legacy'));
  expect(legacyRows).toHaveLength(2); expect(legacyRows.every(row => row.client_message_id == null)).toBe(true);
  evidence.note('RV05 concurrency UNIQUE', { roomId, rows, concurrent, retry, constraint, distinct, legacy, legacyRows });
});

test('RV-06 two-way STOMP, REST polling, reconnect and history recovery', async ({ page, peerPage, evidence }) => {
  test.setTimeout(210000);
  const frames = [], peerPolicy = {};
  const gateA = h.gateway(page, 'A', frames); await gateA.attached;
  const gateB = h.gateway(peerPage, 'B', frames, peerPolicy); await gateB.attached;
  await h.actors(page, peerPage);
  const { roomId } = await h.isolatedRoom(page, peerPage, 'RV06 source');
  await expect.poll(() => frames.filter(row => row.actor === 'B' && row.command === 'CONNECTED').length).toBeGreaterThan(0);
  // Once both subscriptions are established, disable message REST so delivery can only be STOMP.
  const pattern = `${h.API}/chat/rooms/${roomId}/messages*`;
  const block = route => route.abort('failed');
  await page.route(pattern, block); await peerPage.route(pattern, block);
  const forward = h.title('RV06 A to B'), backward = h.title('RV06 B to A');
  await h.send(page, forward); await expect(peerPage.getByText(forward, { exact: true }).first()).toBeVisible();
  await expect(page.locator('#chat-message')).toHaveValue('');
  await h.send(peerPage, backward); await expect(page.getByText(backward, { exact: true }).first()).toBeVisible();
  await expect(peerPage.locator('#chat-message')).toHaveValue('');
  await page.unroute(pattern, block); await peerPage.unroute(pattern, block);
  const connections = gateB.links.length;
  peerPolicy.blockConnections = true; await gateB.close();
  expect(await peerPage.evaluate(() => window.__ccraActualSockets.every(socket => socket.readyState === 3))).toBe(true);
  const gap = h.title('RV06 gap history'); await h.send(page, gap);
  peerPolicy.blockConnections = false;
  await expect.poll(() => gateB.links.length, { timeout: 25000 }).toBeGreaterThan(connections);
  await expect(peerPage.getByText(gap, { exact: true }).first()).toBeVisible({ timeout: 25000 });
  const afterReconnect = h.title('RV06 reconnected B send'); await h.send(peerPage, afterReconnect);
  await expect(page.getByText(afterReconnect, { exact: true }).first()).toBeVisible();
  // Close every peer WS and reject every subsequent attempt, leaving REST polling usable.
  peerPolicy.blockConnections = true; await gateB.close();
  peerPolicy.dropEcho = true;
  expect(await peerPage.evaluate(() => window.__ccraActualSockets.every(socket => socket.readyState === 3))).toBe(true);
  const frameBoundary = frames.length;
  const actualMessageBoundary = await peerPage.evaluate(() => window.__ccraActualMessages.length);
  const recovered = h.title('RV06 polling only');
  const polling = peerPage.waitForResponse(response => response.url().includes(`/chat/rooms/${roomId}/messages`)
    && response.status() === 200, { timeout: 40000 });
  await h.send(page, recovered);
  await expect(peerPage.getByText(recovered, { exact: true }).first()).toBeVisible({ timeout: 40000 });
  const response = await polling;
  expect(frames.slice(frameBoundary).some(row => row.actor === 'B' && row.command === 'MESSAGE' && !row.dropped && row.payload?.text === recovered)).toBe(false);
  expect(await peerPage.evaluate(({ index, text }) => window.__ccraActualMessages.slice(index).some(message => message.text === text),
    { index: actualMessageBoundary, text: recovered })).toBe(false);
  const rows = h.db('snapshot').messages.filter(row => row.chat_room_id === roomId);
  for (const text of [forward, backward, gap, afterReconnect, recovered]) expect(rows.filter(row => row.content === text)).toHaveLength(1);
  const received = frames.filter(row => row.command === 'MESSAGE');
  expect(received.some(row => row.actor === 'B' && row.payload?.text === forward)).toBe(true);
  expect(received.some(row => row.actor === 'A' && row.payload?.text === backward)).toBe(true);
  evidence.note('RV06 real regression', { roomId, originalConnections: connections, connectionsAfter: gateB.links.length,
    pollingPath: new URL(response.url()).pathname, pollingStatus: response.status(), rows, frames,
    peerWebSocketBlockedDuringPolling: true, nativePeerMessagesAfterCut: await peerPage.evaluate(index => window.__ccraActualMessages.slice(index), actualMessageBoundary),
    deliveredByStompWhileRestBlocked: [forward, backward] });
});
