const { test, expect } = require('../fixtures.cjs');
const h = require('../lib/ccra-p0.cjs');

// Independent cases in declaration order, retaining all rows and evidence.
// Service defects are asserted without changing production code.
test('P0-01 Community post/image CRUD, public bytes and distinct deletion policies', async ({ page, peerPage, evidence }) => {
  test.setTimeout(180_000);
  await h.actors(page, peerPage);
  const id = await h.post(page, 'P01 image post', { images: ['p0-first.png', 'p0-second.png'] });
  let images = h.snapshot().images.filter(i => i.post_id === id);
  expect(images).toHaveLength(2);
  await page.goto(`/community/posts/${id}/edit`);
  await page.locator('#community-post-title').fill(h.title('P01 edited'));
  await h.responseTo(page, 'PATCH', `/posts/${id}`, () => page.getByRole('button', { name: '수정 완료', exact: true }).click());
  expect(h.snapshot().images.filter(i => i.post_id === id)).toEqual(images);
  const remove = images[0];
  await page.goto(`/community/posts/${id}/edit`);
  // Existing image DTOs expose id/url, not the historical original filename.
  await page.getByRole('button', { name: '이미지 1 삭제', exact: true }).click();
  await page.getByLabel('첨부할 이미지 선택').setInputFiles(h.pixel('p0-third.png'));
  await h.responseTo(page, 'PATCH', `/posts/${id}`, () => page.getByRole('button', { name: '수정 완료', exact: true }).click());
  images = h.snapshot().images.filter(i => i.post_id === id);
  expect(images).toHaveLength(2); expect(images.some(i => i.post_image_id === remove.post_image_id)).toBe(false);
  await expect.poll(() => h.fileState(remove.image_url).exists).toBe(false);
  expect((await h.imageGet(peerPage, remove.image_url)).status).toBe(404);
  const retained = images.map(i => h.fileState(i.image_url));
  for (const image of images) {
    expect(await h.imageGet(peerPage, image.image_url)).toEqual(expect.objectContaining({ status: 200, type: 'image/png', bytes: h.pixel('').buffer.length, cache: 'no-store' }));
  }
  await expect.poll(() => page.locator('img[alt*="첨부 이미지"]').first().evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
  const denied = await h.browserRequest(peerPage, 'DELETE', `/posts/${id}`);
  expect(denied.status).toBe(403);
  await h.deletePost(page, id);
  const db = h.snapshot();
  expect(db.posts.find(p => p.post_id === id).status).toBe('DELETED');
  expect(db.images.filter(i => i.post_id === id)).toEqual(images);
  for (let i = 0; i < images.length; i++) {
    expect(h.fileState(images[i].image_url)).toEqual(retained[i]);
    expect((await h.imageGet(peerPage, images[i].image_url)).status).toBe(404);
  }
  expect((await h.browserRequest(peerPage, 'GET', `/posts/${id}`)).status).toBe(400);
  await peerPage.goto('/community');
  await expect(peerPage.getByRole('heading', { name: h.title('P01 edited'), exact: true })).toHaveCount(0);
  h.caseEvidence(evidence, 'P01', { postId: id, individualRemoval: h.fileState(remove.image_url), retainedFiles: retained, publicAfterDelete: 404 });
});

test('P0-02 Normal/anonymous comments, replies, likes, own activity and post-commit refresh failure', async ({ page, peerPage, evidence }) => {
  test.setTimeout(240_000);
  const { a, b } = await h.actors(page, peerPage);
  const postId = await h.post(page, 'P02 comments');
  const normal = await h.comment(peerPage, postId, h.title('P02 normal'));
  const anonymous = await h.comment(peerPage, postId, h.title('P02 anonymous'), { anonymous: true });
  const reply = await h.comment(page, postId, h.title('P02 reply'), { parentId: normal });
  const anonymousReply = await h.comment(page, postId, h.title('P02 anonymous reply'), { parentId: anonymous, anonymous: true });
  const detail = await h.request(peerPage, 'GET', `/posts/${postId}`);
  for (const id of [anonymous, anonymousReply]) {
    const item = detail.comments.find(c => c.id === id);
    expect(item.author.id).toBeNull(); expect(item.anonymousNumber).toBeGreaterThan(0);
  }
  expect(detail.comments.find(c => c.id === anonymous).mine).toBe(true);
  expect(detail.comments.find(c => c.id === anonymousReply).mine).toBe(false);
  await h.editComment(peerPage, postId, normal, h.title('P02 normal edited'));
  await h.editComment(peerPage, postId, anonymous, h.title('P02 anonymous edited'));
  await h.editComment(page, postId, reply, h.title('P02 reply edited'));
  await h.editComment(page, postId, anonymousReply, h.title('P02 anonymous reply edited'));
  await peerPage.goto(`/community/posts/${postId}`);
  await h.responseTo(peerPage, 'POST', `/posts/${postId}/likes`, () => peerPage.getByRole('button', { name: '좋아요 0개', exact: true }).click());
  await page.goto(`/community/posts/${postId}`);
  await h.responseTo(page, 'POST', `/comments/${anonymous}/likes`, () => page.locator(`#comment-${anonymous}`).getByRole('button', { name: '댓글 좋아요 0개', exact: true }).click());
  await h.responseTo(page, 'POST', `/comments/${normal}/likes`, () => page.locator(`#comment-${normal}`).getByRole('button', { name: '댓글 좋아요 0개', exact: true }).click());
  let db = h.snapshot();
  expect(db.postLikes).toContainEqual({ post_id: postId, user_id: b });
  expect(db.commentLikes).toContainEqual({ comment_id: anonymous, user_id: a });
  expect((await h.browserRequest(page, 'PATCH', `/comments/${normal}`, { content: 'foreign' })).status).toBe(403);
  expect((await h.browserRequest(peerPage, 'DELETE', `/comments/${reply}`)).status).toBe(403);
  await page.goto('/community/my-posts'); await expect(page.getByRole('heading', { name: h.title('P02 comments'), exact: true })).toBeVisible();
  await peerPage.goto('/community/my-comments'); await expect(peerPage.getByText(h.title('P02 anonymous edited'), { exact: true })).toBeVisible();
  await page.goto('/community/my-comments'); await expect(page.getByText(h.title('P02 anonymous reply edited'), { exact: true })).toBeVisible();
  await expect(page.getByText(h.title('P02 anonymous edited'), { exact: true })).toHaveCount(0);
  await peerPage.goto(`/community/posts/${postId}`);
  await h.responseTo(peerPage, 'DELETE', `/posts/${postId}/likes`, () => peerPage.getByRole('button', { name: '좋아요 1개', exact: true }).click());
  await page.goto(`/community/posts/${postId}`);
  for (const id of [normal, anonymous]) await h.responseTo(page, 'DELETE', `/comments/${id}/likes`, () => page.locator(`#comment-${id}`).getByRole('button', { name: '댓글 좋아요 1개', exact: true }).click());
  await h.deleteComment(page, postId, reply); await h.deleteComment(page, postId, anonymousReply);
  await h.deleteComment(peerPage, postId, normal); await h.deleteComment(peerPage, postId, anonymous);
  db = h.snapshot();
  expect(db.comments.filter(c => [normal, anonymous, reply, anonymousReply].includes(c.comment_id)).every(c => c.status === 'DELETED')).toBe(true);
  h.caseEvidence(evidence, 'P02 normal flows', { postId, normal, anonymous, reply, anonymousReply, CRUD: 'PASS', ownership: 'PASS', likes: 'PASS', activities: 'PASS' });

  // Forward the actual POST, fail only its follow-up GET, then act as a user retrying.
  await peerPage.goto(`/community/posts/${postId}`);
  await expect(peerPage.getByRole('heading', { name: h.title('P02 comments'), exact: true })).toBeVisible();
  const content = h.title('P02 committed refresh failed');
  const route = `${h.API}/posts/${postId}`;
  await peerPage.route(route, r => r.request().method() === 'GET'
    ? r.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ success: false, errorCode: 'INTERNAL_SERVER_ERROR', message: 'P0 injected read failure', data: null }) }) : r.continue(), { times: 1 });
  await peerPage.locator('#community-new-comment').fill(content);
  const failedRefresh = peerPage.waitForResponse(r => r.url() === route && r.request().method() === 'GET' && r.status() === 503);
  await h.responseTo(peerPage, 'POST', `/posts/${postId}/comments`, () => peerPage.getByRole('button', { name: '댓글 등록', exact: true }).click());
  await failedRefresh;
  await expect(peerPage.getByText('처리하고 있어요.', { exact: true })).toHaveCount(0);
  const draftAfterCommitted = await peerPage.locator('#community-new-comment').inputValue();
  const committed = h.snapshot().comments.filter(c => c.post_id === postId && c.content === content);
  expect(committed).toHaveLength(1);
  if (draftAfterCommitted === content) {
    await h.responseTo(peerPage, 'POST', `/posts/${postId}/comments`, () => peerPage.getByRole('button', { name: '댓글 등록', exact: true }).click());
  }
  const duplicateRows = h.snapshot().comments.filter(c => c.post_id === postId && c.content === content);
  h.caseEvidence(evidence, 'P02 refresh defect', { postId, draftAfterCommitted, firstCommittedIds: committed.map(c => c.comment_id), finalIds: duplicateRows.map(c => c.comment_id), injectedGET: 503 });
  expect.soft(duplicateRows.length, 'A failed follow-up GET must not invite a duplicate committed comment').toBe(1);
});

test('P0-03 Report intake/admin actions/user effects and REJECT versus NONE', async ({ page, peerPage, adminPage, evidence }) => {
  test.setTimeout(240_000);
  const { b, admin } = await h.actors(page, peerPage, adminPage);
  const postId = await h.post(page, 'P03 report reject');
  const reportId = await h.report(peerPage, postId);
  expect((await h.browserRequest(peerPage, 'PATCH', `/admin/reports/${reportId}`, { action: 'COMPLETE' })).status).toBe(403);
  await h.processReport(adminPage, reportId, '신고 반려');
  let row = h.snapshot().reports.find(r => r.report_id === reportId);
  expect(row).toEqual(expect.objectContaining({ status: 'REJECTED', resolution_action: 'NONE', processed_by: admin }));
  expect(row.processed_at).toBeTruthy();
  await peerPage.goto(`/community/posts/${postId}`); await expect(peerPage.getByRole('button', { name: '신고 완료', exact: true })).toBeDisabled();
  expect((await h.browserRequest(adminPage, 'PATCH', `/admin/reports/${reportId}`, { action: 'COMPLETE' })).status).toBe(400);
  expect(h.snapshot().reports.find(r => r.report_id === reportId)).toEqual(row);
  const completedPost = await h.post(page, 'P03 complete');
  const completed = await h.report(peerPage, completedPost);
  await h.processReport(adminPage, completed, '처리 완료');
  expect(h.snapshot().reports.find(r => r.report_id === completed)).toEqual(expect.objectContaining({ status: 'COMPLETED', resolution_action: 'NONE' }));
  const deletedPost = await h.post(page, 'P03 delete via report', { images: ['p03.png'] });
  const postReport = await h.report(peerPage, deletedPost);
  const image = h.snapshot().images.find(i => i.post_id === deletedPost);
  await h.processReport(adminPage, postReport, '게시글 삭제');
  expect(h.snapshot().posts.find(p => p.post_id === deletedPost).status).toBe('DELETED');
  expect(h.fileState(image.image_url).exists).toBe(true);
  expect((await h.imageGet(peerPage, image.image_url)).status).toBe(404);
  await peerPage.goto(`/community/posts/${deletedPost}`); await expect(peerPage.getByRole('heading', { name: '게시글을 찾을 수 없어요', exact: true })).toBeVisible();
  const commentPost = await h.post(page, 'P03 comment report');
  const commentId = await h.comment(peerPage, commentPost, h.title('P03 report target comment'));
  const commentReport = await h.report(page, commentPost, commentId);
  await h.processReport(adminPage, commentReport, '댓글 삭제');
  expect(h.snapshot().comments.find(c => c.comment_id === commentId).status).toBe('DELETED');
  await peerPage.goto('/community/my-comments'); await expect(peerPage.getByText(h.title('P03 report target comment'), { exact: true })).toHaveCount(0);
  const userReport = await h.request(page, 'POST', '/reports', { targetType: 'USER', targetId: b, reason: '도배·스팸' });
  await h.processReport(adminPage, userReport, '회원 이용 정지');
  expect(h.snapshot().users.find(u => u.user_id === b).status).toBe('SUSPENDED');
  expect((await h.browserRequest(peerPage, 'GET', '/chat/rooms')).status).toBe(401);
  expect(h.snapshot().reports.find(r => r.report_id === userReport)).toEqual(expect.objectContaining({ resolution_action: 'SUSPEND_USER', status: 'COMPLETED' }));
  await h.memberStatus(adminPage, b, 'ACTIVE');
  h.caseEvidence(evidence, 'P03', { reportId, completed, postReport, commentReport, userReport, rejection: row, originalREJECTExpectation: 'TEST_EXPECTATION_ERROR' });
});

test('P0-04 Chat room creation/list/settings/leave and membership revocation', async ({ page, peerPage, adminPage, evidence }) => {
  test.setTimeout(180_000);
  const { a, b } = await h.actors(page, peerPage, adminPage);
  const { roomId, postId } = await h.isolatedRoom(page, peerPage, 'P04 source');
  const again = await h.request(peerPage, 'POST', '/chat/rooms', { category: 'COMMUNITY', sourcePostId: postId, sourceFrom: 'POST', targetUserId: a });
  expect(again.id).toBe(roomId);
  const list = await h.request(peerPage, 'GET', '/chat/rooms?size=50'); expect(list.content.some(r => r.id === roomId)).toBe(true);
  await page.getByRole('button', { name: `${h.nickname('E2E상대')} 채팅방 옵션`, exact: true }).first().click();
  await h.responseTo(page, 'PATCH', `/chat/rooms/${roomId}/settings`, () => page.getByRole('button', { name: '채팅방 고정', exact: true }).click());
  await page.getByRole('button', { name: `${h.nickname('E2E상대')} 채팅방 옵션 (고정됨)`, exact: true }).first().click();
  await h.responseTo(page, 'PATCH', `/chat/rooms/${roomId}/settings`, () => page.getByRole('button', { name: '알림 끄기', exact: true }).click());
  await page.reload();
  expect(await h.request(page, 'GET', `/chat/rooms/${roomId}`)).toEqual(expect.objectContaining({ pinned: true, muted: true }));
  expect(await h.request(peerPage, 'GET', `/chat/rooms/${roomId}`)).toEqual(expect.objectContaining({ pinned: false, muted: false }));
  expect((await h.browserRequest(adminPage, 'GET', `/chat/rooms/${roomId}/messages`)).status).toBe(403);
  await h.probe(peerPage, roomId); await h.send(page, h.title('P04 before leave'));
  await expect.poll(async () => (await h.probeState(peerPage)).texts.includes(h.title('P04 before leave'))).toBe(true);
  await peerPage.getByRole('button', { name: '익명 채팅방 옵션', exact: true }).first().click();
  await peerPage.getByRole('button', { name: '방 나가기', exact: true }).click();
  await h.responseTo(peerPage, 'DELETE', `/chat/rooms/${roomId}/members/me`, () => peerPage.getByRole('dialog').getByRole('button', { name: '나가기', exact: true }).click());
  await h.send(page, h.title('P04 after leave'));
  await h.probeSend(peerPage, h.title('P04 forbidden send'));
  // The contract requires blocked delivery/storage, not immediate socket closure.
  await h.pause(3000);
  expect((await h.probeState(peerPage)).texts).not.toContain(h.title('P04 after leave'));
  const db = h.snapshot();
  expect(db.members.find(m => m.chat_room_id === roomId && m.user_id === b).is_left).toBe(true);
  expect(db.messages.filter(m => m.chat_room_id === roomId && m.content === h.title('P04 forbidden send'))).toHaveLength(0);
  expect((await h.browserRequest(peerPage, 'GET', `/chat/rooms/${roomId}/messages`)).status).toBe(403);
  expect((await h.request(peerPage, 'GET', '/chat/rooms?size=50')).content.some(r => r.id === roomId)).toBe(false);
  h.caseEvidence(evidence, 'P04', { roomId, postId, createdMembers: db.members.filter(m => m.chat_room_id === roomId), unauthorizedREST: 403, forbiddenSendStored: false });
  await h.closeProbe(peerPage);
});

test('P0-05 Two-browser bidirectional real STOMP delivery independently of REST polling', async ({ page, peerPage, evidence }) => {
  test.setTimeout(150_000);
  const frames = [];
  await h.observeSockets(page, 'A', frames); await h.observeSockets(peerPage, 'B', frames);
  await h.actors(page, peerPage);
  const { roomId } = await h.communityRoom(page, peerPage, h.title('P05 source'));
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeDisabled(); // empty draft
  await page.locator('#chat-message').fill(h.title('P05 A to B'));
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeEnabled();
  await expect.poll(() => frames.filter(f => f.command === 'SUBSCRIBE').length).toBeGreaterThanOrEqual(2);
  const block = '**/api/chat/rooms**';
  await page.route(block, r => r.abort()); await peerPage.route(block, r => r.abort());
  await h.send(page, h.title('P05 A to B')); await expect(peerPage.getByText(h.title('P05 A to B'), { exact: true }).first()).toBeVisible();
  await h.send(peerPage, h.title('P05 B to A')); await expect(page.getByText(h.title('P05 B to A'), { exact: true }).first()).toBeVisible();
  const rows = h.snapshot().messages.filter(m => m.chat_room_id === roomId);
  expect(rows).toHaveLength(2);
  for (const actor of ['A', 'B']) {
    expect(frames.some(f => f.actor === actor && f.command === 'CONNECTED')).toBe(true);
    expect(frames.some(f => f.actor === actor && f.command === 'SEND')).toBe(true);
    for (const row of rows) expect(frames.some(f => f.actor === actor && f.command === 'MESSAGE' && f.message.id === row.message_id && f.message.roomId === roomId && f.message.text === row.content)).toBe(true);
  }
  h.caseEvidence(evidence, 'P05', { roomId, restBlocked: true, frames, rows });
  await page.unroute(block); await peerPage.unroute(block);
});

test('P0-06 Read receipts, socket reconnect, REST polling recovery and unconfirmed draft', async ({ page, peerPage, evidence }) => {
  test.setTimeout(240_000);
  const frames = [], cuts = []; let dropping = false, blockPeerSockets = false;
  await h.observeSockets(page, 'A', frames, { dropSend: () => dropping });
  // A controlled actual WebSocket proxy allows independent transport interruption.
  await peerPage.routeWebSocket('**/ws/chat*', ws => {
    if (blockPeerSockets) return ws.close({ code: 1013, reason: 'P0 polling only' });
    const server = ws.connectToServer(); cuts.push({ ws, server });
    ws.onMessage(message => server.send(message)); server.onMessage(message => ws.send(message));
  });
  await h.actors(page, peerPage);
  const { roomId } = await h.isolatedRoom(page, peerPage, 'P06 source');
  await peerPage.goto('/community');
  await h.send(page, h.title('P06 unread'));
  const unread = h.snapshot().messages.find(m => m.chat_room_id === roomId && m.content === h.title('P06 unread'));
  expect(unread.is_read).toBe(false);
  expect((await h.request(peerPage, 'GET', `/chat/rooms/${roomId}`)).unreadCount).toBe(1);
  await peerPage.goto(`/chat?room=${roomId}`);
  await expect(peerPage.getByText(h.title('P06 unread'), { exact: true }).first()).toBeVisible();
  await expect.poll(() => h.snapshot().messages.find(m => m.message_id === unread.message_id).is_read).toBe(true);
  expect((await h.request(peerPage, 'GET', `/chat/rooms/${roomId}`)).unreadCount).toBe(0);
  await expect(page.locator(`[data-message-id="${unread.message_id}"]`)).toContainText('읽음', { timeout: 25_000 });
  const previousConnections = cuts.length;
  const closeCount = cuts.length;
  for (const cut of cuts.slice(0, closeCount)) { await cut.ws.close({ code: 1012, reason: 'P0 reconnect' }); }
  await h.send(page, h.title('P06 reconnect gap'));
  await expect.poll(() => cuts.length, { timeout: 20_000 }).toBeGreaterThan(previousConnections);
  await expect(peerPage.getByText(h.title('P06 reconnect gap'), { exact: true }).first()).toBeVisible({ timeout: 25_000 });
  await h.send(peerPage, h.title('P06 reconnected send'));
  await expect(page.getByText(h.title('P06 reconnected send'), { exact: true }).first()).toBeVisible();
  // Block only WebSocket, keeping HTTP and the visible tab alive for 15-second polling.
  blockPeerSockets = true;
  for (const cut of cuts) await cut.ws.close({ code: 1013, reason: 'P0 polling only' }).catch(() => {});
  const recoveryResponse = peerPage.waitForResponse(r => r.url().includes(`/chat/rooms/${roomId}/messages`) && r.status() === 200, { timeout: 35_000 });
  await h.send(page, h.title('P06 polling recovered'));
  await expect(peerPage.getByText(h.title('P06 polling recovered'), { exact: true }).first()).toBeVisible({ timeout: 35_000 });
  const recovery = await recoveryResponse;
  const recoveredRows = h.snapshot().messages.filter(m => m.chat_room_id === roomId);
  expect(recoveredRows.filter(m => m.content === h.title('P06 polling recovered'))).toHaveLength(1);
  h.caseEvidence(evidence, 'P06 recovery', { roomId, receiptId: unread.message_id, unreadBefore: 1, unreadAfter: 0,
    initialConnections: previousConnections, reconnected: true, pollingPath: new URL(recovery.url()).pathname, committedRows: recoveredRows });
  dropping = true;
  const draft = h.title('P06 not committed');
  await page.locator('#chat-message').fill(draft);
  await expect(page.getByRole('button', { name: '메시지 전송', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '메시지 전송', exact: true }).click();
  await h.pause(300);
  const remaining = await page.locator('#chat-message').inputValue();
  const stored = h.snapshot().messages.filter(m => m.chat_room_id === roomId && m.content === draft);
  expect(stored).toHaveLength(0);
  h.caseEvidence(evidence, 'P06 draft defect', { roomId, draft, draftAfterUnconfirmedPublish: remaining, storedRows: stored.length,
    sendFrameObserved: frames.some(f => f.actor === 'A' && f.command === 'SEND'), injectedFailure: 'client-to-server SEND dropped after publish' });
  expect.soft(remaining, 'Draft must remain recoverable until its message is confirmed stored').toBe(draft);
});

test('P0-07 Admin permissions, sanctions, direct deletion and logout policy boundary', async ({ page, peerPage, adminPage, evidence }) => {
  test.setTimeout(240_000);
  const { b } = await h.actors(page, peerPage, adminPage);
  const { roomId, postId } = await h.isolatedRoom(page, peerPage, 'P07 source');
  const commentId = await h.comment(peerPage, postId, h.title('P07 direct comment'));
  for (const [method, url, data] of [['GET', '/admin/dashboard'], ['PATCH', `/admin/members/${b}/status`, { status: 'SUSPENDED' }], ['DELETE', `/admin/posts/${postId}`], ['DELETE', `/admin/comments/${commentId}`]]) {
    expect((await h.browserRequest(page, method, url, data)).status).toBe(403);
  }
  await page.goto('/admin'); await expect(page.getByRole('heading', { name: '관리자 대시보드', exact: true })).toHaveCount(0);
  await page.goto(`/chat?room=${roomId}`); await peerPage.goto(`/chat?room=${roomId}`);
  await h.probe(peerPage, roomId); await h.send(page, h.title('P07 before suspension'));
  await expect.poll(async () => (await h.probeState(peerPage)).texts.includes(h.title('P07 before suspension'))).toBe(true);
  await h.memberStatus(adminPage, b, 'SUSPENDED');
  expect(h.snapshot().users.find(u => u.user_id === b).status).toBe('SUSPENDED');
  expect((await h.browserRequest(peerPage, 'GET', '/chat/rooms')).status).toBe(401);
  expect((await h.browserRequest(peerPage, 'POST', `/posts/${postId}/comments`, { content: 'forbidden' })).status).toBe(401);
  await h.send(page, h.title('P07 after suspension')); await h.probeSend(peerPage, h.title('P07 forbidden SEND'));
  await expect.poll(async () => { const state = await h.probeState(peerPage); return state.error || state.closed; }).toBe(true);
  expect((await h.probeState(peerPage)).texts).not.toContain(h.title('P07 after suspension'));
  expect(h.snapshot().messages.some(m => m.chat_room_id === roomId && m.content === h.title('P07 forbidden SEND'))).toBe(false);
  await h.closeProbe(peerPage); await h.memberStatus(adminPage, b, 'ACTIVE');
  await h.login(peerPage, process.env.E2E_SECOND_EMAIL);
  await adminPage.goto(`/admin?view=community&tab=comments&q=${encodeURIComponent(h.title('P07 direct comment'))}`);
  await adminPage.getByRole('button', { name: h.title('P07 direct comment'), exact: true }).click();
  await adminPage.getByRole('dialog').getByRole('button', { name: '댓글 삭제', exact: true }).click();
  await h.responseTo(adminPage, 'DELETE', `/admin/comments/${commentId}`, () => adminPage.getByRole('dialog').getByRole('button', { name: '댓글 삭제', exact: true }).click());
  await adminPage.goto(`/admin?view=community&q=${encodeURIComponent(h.title('P07 source'))}`);
  await adminPage.getByRole('button', { name: h.title('P07 source'), exact: true }).click();
  await adminPage.getByRole('dialog').getByRole('button', { name: '게시글 삭제', exact: true }).click();
  await h.responseTo(adminPage, 'DELETE', `/admin/posts/${postId}`, () => adminPage.getByRole('dialog').getByRole('button', { name: '게시글 삭제', exact: true }).click());
  expect(h.snapshot().comments.find(c => c.comment_id === commentId).status).toBe('DELETED');
  expect(h.snapshot().posts.find(p => p.post_id === postId).status).toBe('DELETED');
  await peerPage.goto(`/community/posts/${postId}`); await expect(peerPage.getByRole('heading', { name: '게시글을 찾을 수 없어요', exact: true })).toBeVisible();
  // Logout has no server-side JWT revocation contract. Retained probe is an audit.
  await page.goto(`/chat?room=${roomId}`); await peerPage.goto(`/chat?room=${roomId}`);
  await h.probe(page, roomId); await h.send(peerPage, h.title('P07 before logout'));
  await expect.poll(async () => (await h.probeState(page)).texts.includes(h.title('P07 before logout'))).toBe(true);
  // Navigation can leave historical Page WebSocket objects without a close event.
  // Track the current document's app connection, excluding the deliberate probe.
  const currentAppSocket = evidence.sockets.filter(s => s.actor === 'A' && s.url.includes('/ws/chat') && !s.url.includes('e2eProbe')).at(-1);
  expect(currentAppSocket).toBeTruthy();
  const logoutPage = await page.context().newPage();
  await logoutPage.goto('/community');
  await h.logout(logoutPage);
  await expect.poll(() => currentAppSocket.closed).toBe(true);
  expect((await h.browserRequest(page, 'GET', '/chat/rooms')).status).toBe(401);
  await h.send(peerPage, h.title('P07 retained after logout'));
  await expect.poll(async () => (await h.probeState(page)).texts.includes(h.title('P07 retained after logout'))).toBe(true);
  await h.probeSend(page, h.title('P07 retained SEND after logout'));
  await expect.poll(() => h.snapshot().messages.some(m => m.chat_room_id === roomId && m.content === h.title('P07 retained SEND after logout'))).toBe(true);
  await page.goto('/chat'); await expect(page.locator('#chat-message')).toHaveCount(0);
  h.caseEvidence(evidence, 'P07', { roomId, postId, commentId, deniedAdminHTTP: 403, suspendedHTTP: 401, suspendedSENDStored: false,
    tokenlessHTTP: 401, retainedAuthenticatedSocket: 'STILL_VALID_UNDER_CLIENT_ONLY_LOGOUT_CONTRACT', originalLogoutExpectation: 'POLICY_MISMATCH' });
  await h.closeProbe(page);
  await logoutPage.close();
});

test('P0-08 Admin dashboard counts/recent rows agree with committed database and user effects', async ({ page, peerPage, adminPage, evidence }) => {
  test.setTimeout(180_000);
  const { b } = await h.actors(page, peerPage, adminPage);
  const check = async label => {
    const response = await h.responseTo(adminPage, 'GET', '/admin/dashboard', () => adminPage.goto('/admin'));
    const dto = (await response.json()).data, db = h.snapshot();
    const expected = { totalMembers: db.users.length, totalPosts: db.posts.filter(p => p.status === 'ACTIVE').length,
      totalComments: db.comments.filter(c => c.status === 'ACTIVE').length, totalReports: db.reports.length,
      pendingReports: db.reports.filter(r => r.status === 'PENDING').length, schoolSchedules: 0, dormitorySchedules: 0 };
    expect(dto.stats).toEqual(expected);
    for (const [text, key] of [['전체 회원 수', 'totalMembers'], ['전체 게시글 수', 'totalPosts'], ['전체 댓글 수', 'totalComments'], ['전체 신고 수', 'totalReports'], ['처리 대기 신고 수', 'pendingReports']]) {
      await expect(adminPage.getByRole('link').filter({ has: adminPage.getByText(text, { exact: true }) }).locator('strong')).toHaveText(expected[key].toLocaleString());
    }
    const latest = rows => [...rows].sort((x, y) => String(y.created_at).localeCompare(String(x.created_at)) || (y.post_id ?? y.report_id ?? y.user_id) - (x.post_id ?? x.report_id ?? x.user_id)).slice(0, 3);
    expect(dto.recentPosts.map(p => p.id)).toEqual(latest(db.posts.filter(p => p.status === 'ACTIVE')).map(p => p.post_id));
    expect(dto.recentReports.map(r => r.id)).toEqual(latest(db.reports).map(r => r.report_id));
    expect(dto.recentMembers.map(u => u.id)).toEqual(latest(db.users).map(u => u.user_id));
    expect(dto.recentSchoolSchedules).toEqual([]); expect(dto.recentDormitorySchedules).toEqual([]);
    h.caseEvidence(evidence, label, { expected, stats: dto.stats, recentPosts: dto.recentPosts.map(p => p.id), recentReports: dto.recentReports.map(r => r.id), recentMembers: dto.recentMembers.map(u => u.id) });
    return expected;
  };
  const before = await check('P08 baseline');
  const postId = await h.post(page, 'P08 delta');
  const commentId = await h.comment(peerPage, postId, h.title('P08 delta comment'));
  const reportId = await h.report(peerPage, postId);
  const created = await check('P08 created');
  expect(created.totalPosts).toBe(before.totalPosts + 1); expect(created.totalComments).toBe(before.totalComments + 1);
  expect(created.pendingReports).toBe(before.pendingReports + 1);
  await h.processReport(adminPage, reportId, '신고 반려');
  await h.memberStatus(adminPage, b, 'SUSPENDED');
  const resolved = await check('P08 resolved and suspended');
  expect(resolved.pendingReports).toBe(before.pendingReports); expect(resolved.totalMembers).toBe(before.totalMembers);
  await h.memberStatus(adminPage, b, 'ACTIVE');
  await h.deleteComment(peerPage, postId, commentId); await h.deletePost(page, postId);
  const after = await check('P08 deleted');
  expect(after.totalPosts).toBe(before.totalPosts); expect(after.totalComments).toBe(before.totalComments);
});
