const { test, expect, database } = require('../fixtures.cjs');
const { responseTo, login, browserRequest, createPost, completeSurvey } = require('../lib/flows.cjs');

test('C/D + User/Report/Admin: isolated likes, community CRUD, ownership and report review commit to MySQL', async ({ page, peerPage, adminPage }) => {
  test.setTimeout(180_000);
  for (const actor of [page, peerPage, adminPage]) actor.on('dialog', dialog => dialog.accept());
  await login(page, process.env.E2E_LOGIN_EMAIL); await login(peerPage, process.env.E2E_SECOND_EMAIL);
  let snapshot = database('snapshot');
  const a = snapshot.profiles.find(p => p.nickname === 'E2E사용자').user_id;
  const b = snapshot.profiles.find(p => p.nickname === 'E2E상대').user_id;
  let postId, commentId, reportId;

  await test.step('C: independent A/B browser sessions preserve the other account interest relation', async () => {
    await completeSurvey(page); await completeSurvey(peerPage);
    await page.goto('/roommates'); await peerPage.goto('/roommates');
    await responseTo(page, 'POST', `/roommates/${b}/likes`, () => page.getByRole('button', { name: 'E2E상대 프로필 좋아요', exact: true }).click());
    await responseTo(peerPage, 'POST', `/roommates/${a}/likes`, () => peerPage.getByRole('button', { name: 'E2E사용자 프로필 좋아요', exact: true }).click());
    expect(database('snapshot').likes).toEqual([{ user_id: a, target_user_id: b }, { user_id: b, target_user_id: a }]);
    await page.goto('/profile?tab=likes'); await peerPage.goto('/profile?tab=likes');
    await expect(page.getByRole('link', { name: 'E2E상대 상세 프로필 보기', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'E2E사용자 상세 프로필 보기', exact: true })).toHaveCount(0);
    await responseTo(page, 'DELETE', `/roommates/${b}/likes`, () => page.getByRole('button', { name: 'E2E상대 프로필 좋아요 취소', exact: true }).click());
    expect(database('snapshot').likes).toEqual([{ user_id: b, target_user_id: a }]);
    await peerPage.reload();
    await expect(peerPage.getByRole('link', { name: 'E2E사용자 상세 프로필 보기', exact: true })).toBeVisible();
  });
  await test.step('User profile change is reflected in the next community author response', async () => {
    await page.goto('/profile?tab=account');
    await page.getByRole('button', { name: '수정', exact: true }).first().click();
    await page.locator('#account-nickname').fill('E2E작성자');
    await responseTo(page, 'PATCH', '/users/me', () => page.getByRole('button', { name: '완료', exact: true }).click());
    expect(database('snapshot').profiles.find(p => p.user_id === a).nickname).toBe('E2E작성자');
  });
  await test.step('D: real multipart post/image, list/detail, comment and two distinct like domains', async () => {
    postId = await createPost(page, 'E2E 도메인 연결 게시글', 'E2E 작성한 본문', true);
    const image = page.getByRole('img', { name: 'E2E 도메인 연결 게시글 첨부 이미지 1', exact: true });
    await expect.poll(() => image.evaluate(img => img.complete && img.naturalWidth > 0)).toBe(true);
    await peerPage.goto('/community');
    await peerPage.getByRole('heading', { name: 'E2E 도메인 연결 게시글', exact: true }).click();
    await expect(peerPage.getByText('E2E 작성한 본문', { exact: true })).toBeVisible();
    await expect(peerPage.getByRole('button', { name: 'E2E작성자님에게 채팅 보내기', exact: true })).toBeVisible();
    await peerPage.locator('#community-new-comment').fill('E2E 상대 댓글');
    const comment = await responseTo(peerPage, 'POST', `/posts/${postId}/comments`, () => peerPage.getByRole('button', { name: '댓글 등록', exact: true }).click());
    commentId = (await comment.json()).data;
    await responseTo(peerPage, 'POST', `/posts/${postId}/likes`, () => peerPage.getByRole('button', { name: '좋아요 0개', exact: true }).click());
    await page.reload();
    await responseTo(page, 'POST', `/comments/${commentId}/likes`, () => page.locator(`#comment-${commentId}`).getByRole('button', { name: '댓글 좋아요 0개', exact: true }).click());
    snapshot = database('snapshot');
    expect(snapshot.posts.find(p => p.post_id === postId)).toEqual(expect.objectContaining({ author_id: a, title: 'E2E 도메인 연결 게시글', content: 'E2E 작성한 본문', status: 'ACTIVE' }));
    expect(snapshot.images).toContainEqual(expect.objectContaining({ post_id: postId, original_name: 'e2e-pixel.png' }));
    expect(snapshot.comments.find(c => c.comment_id === commentId)).toEqual(expect.objectContaining({ author_id: b, post_id: postId, content: 'E2E 상대 댓글', status: 'ACTIVE' }));
    expect(snapshot.postLikes).toEqual([{ post_id: postId, user_id: b }]);
    expect(snapshot.commentLikes).toEqual([{ comment_id: commentId, user_id: a }]);
    expect(snapshot.likes).toEqual([{ user_id: b, target_user_id: a }]);
  });
  await test.step('Foreign post/comment update and deletion are forbidden and leave business rows unchanged', async () => {
    const before = database('snapshot');
    await peerPage.goto(`/community/posts/${postId}/edit`);
    await expect(peerPage.getByRole('heading', { name: '내가 작성한 글만 수정할 수 있어요', exact: true })).toBeVisible();
    for (const result of [
      await browserRequest(peerPage, 'PATCH', `/posts/${postId}`, { title: 'foreign', content: 'foreign', category: '자유게시판', keepImageIds: [] }, { multipart: true }),
      await browserRequest(peerPage, 'DELETE', `/posts/${postId}`),
      await browserRequest(page, 'PATCH', `/comments/${commentId}`, { content: 'foreign' }),
      await browserRequest(page, 'DELETE', `/comments/${commentId}`),
    ]) { expect(result.status).toBe(403); expect(result.code).toBe('FORBIDDEN'); }
    expect(database('snapshot')).toEqual(before);
  });
  await test.step('Owners edit post/comment and cancel their own likes; API and DB agree', async () => {
    await page.getByRole('button', { name: '수정', exact: true }).first().click();
    await page.locator('#community-post-title').fill('E2E 수정 게시글');
    await responseTo(page, 'PATCH', `/posts/${postId}`, () => page.getByRole('button', { name: '수정 완료', exact: true }).click());
    await peerPage.goto(`/community/posts/${postId}`);
    const comment = peerPage.locator(`#comment-${commentId}`);
    await comment.getByRole('button', { name: '수정', exact: true }).click();
    await comment.locator(`#edit-${commentId}`).fill('E2E 수정 댓글');
    await responseTo(peerPage, 'PATCH', `/comments/${commentId}`, () => comment.getByRole('button', { name: '수정 완료', exact: true }).click());
    await responseTo(peerPage, 'DELETE', `/posts/${postId}/likes`, () => peerPage.getByRole('button', { name: '좋아요 1개', exact: true }).click());
    await responseTo(page, 'DELETE', `/comments/${commentId}/likes`, () => page.locator(`#comment-${commentId}`).getByRole('button', { name: '댓글 좋아요 1개', exact: true }).click());
    snapshot = database('snapshot');
    expect(snapshot.posts.find(p => p.post_id === postId).title).toBe('E2E 수정 게시글');
    expect(snapshot.comments.find(c => c.comment_id === commentId).content).toBe('E2E 수정 댓글');
    expect(snapshot.postLikes).toEqual([]); expect(snapshot.commentLikes).toEqual([]);
    expect(snapshot.likes).toEqual([{ user_id: b, target_user_id: a }]);
  });
  await test.step('Report from real UI reaches admin review, rejects non-admin access and commits resolution', async () => {
    await peerPage.getByRole('button', { name: '게시글 신고', exact: true }).click();
    let dialog = peerPage.getByRole('dialog', { name: '게시글 신고', exact: true });
    await dialog.getByText('기타', { exact: true }).click(); await dialog.locator('#community-other-reason').fill('E2E 신고 검토 사유');
    const created = await responseTo(peerPage, 'POST', '/reports', () => dialog.getByRole('button', { name: '신고하기', exact: true }).click());
    reportId = (await created.json()).data;
    const pending = database('snapshot');
    expect(pending.reports.find(r => r.report_id === reportId)).toEqual(expect.objectContaining({ reporter_id: b, target_user_id: a, post_id: postId, status: 'PENDING', description: 'E2E 신고 검토 사유' }));
    expect((await browserRequest(peerPage, 'PATCH', `/admin/reports/${reportId}`, { action: 'COMPLETE' })).status).toBe(403);
    expect(database('snapshot')).toEqual(pending);
    await login(adminPage, process.env.E2E_ADMIN_EMAIL);
    await responseTo(adminPage, 'GET', '/admin/dashboard', () => adminPage.goto('/admin'));
    await responseTo(adminPage, 'GET', '/admin/reports', () => adminPage.getByRole('link', { name: '신고 관리', exact: true }).click());
    await adminPage.getByRole('button', { name: `#${reportId}`, exact: true }).click();
    dialog = adminPage.getByRole('dialog');
    await expect(dialog.getByText('E2E 신고 검토 사유', { exact: true })).toBeVisible();
    await dialog.getByRole('button', { name: '신고 반려', exact: true }).click();
    await responseTo(adminPage, 'PATCH', `/admin/reports/${reportId}`, () => adminPage.getByRole('dialog').getByRole('button', { name: '신고 반려', exact: true }).click());
    expect(database('snapshot').reports.find(r => r.report_id === reportId)).toEqual(expect.objectContaining({ status: 'REJECTED', resolution_action: 'REJECT' }));
  });
  await test.step('Owner deletes comment and post; soft deletion preserves the report reference', async () => {
    await peerPage.locator(`#comment-${commentId}`).getByRole('button', { name: '삭제', exact: true }).click();
    await responseTo(peerPage, 'DELETE', `/comments/${commentId}`, () => peerPage.getByRole('dialog').getByRole('button', { name: '삭제하기', exact: true }).click());
    await page.getByRole('button', { name: '삭제', exact: true }).first().click();
    await responseTo(page, 'DELETE', `/posts/${postId}`, () => page.getByRole('dialog').getByRole('button', { name: '삭제하기', exact: true }).click());
    snapshot = database('snapshot');
    expect(snapshot.posts.find(p => p.post_id === postId).status).toBe('DELETED');
    expect(snapshot.comments.find(c => c.comment_id === commentId).status).toBe('DELETED');
    expect(snapshot.reports.find(r => r.report_id === reportId).post_id).toBe(postId);
    await peerPage.goto('/community');
    await expect(peerPage.getByRole('link', { name: 'E2E 수정 게시글', exact: true })).toHaveCount(0);
  });
});
