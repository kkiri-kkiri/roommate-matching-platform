const { test, expect, database, responseTo, login, logout, control, browserRequest,
  actor, peer, profile, link, article, ids, get, navigate, calendar, range, edit, applySleep, assertCards } = require('../lib/mc-flows.cjs');

test.setTimeout(120_000);
test.beforeEach(async ({ page }) => { page.on('dialog', d => d.accept()); });

test('C04 P0: title-only editing preserves existing all-day partial and exact-minute schedules', async ({ page, evidence }) => {
  await login(page, actor());
  const initial = await calendar(page), failures = [];
  for (const title of ['MC정상','MC종일','MC시작만','MC종료만','MC비정각']) {
    const original = initial.find(e => e.title === title);
    expect(original).toBeTruthy();
    const before = database('snapshot').events.find(e => e.event_id === original.id);
    const dialog = await edit(page, title);
    const fields = { start: await dialog.locator('#calendar-startTime').inputValue(), end: await dialog.locator('#calendar-endTime').inputValue() };
    await dialog.locator('#calendar-title').fill(title + ' 수정');
    const waiting = page.waitForResponse(r => r.url() === `http://127.0.0.1:18080/api/calendar/events/${original.id}` && r.request().method() === 'PATCH', { timeout: 2500 }).catch(() => null);
    await dialog.getByRole('button', { name: '변경 내용 저장', exact: true }).click();
    const response = await waiting;
    const after = database('snapshot').events.find(e => e.event_id === original.id);
    const fresh = (await get(page, range(initial))).find(e => e.id === original.id);
    const note = { title, original, fields, before, after, fresh, patch: response ? { status: response.status(), payload: response.request().postDataJSON(), data: (await response.json()).data } : null };
    if (!response) { note.validation = await dialog.getByRole('alert').allTextContents(); failures.push(title + ': UI blocked PATCH'); expect(after).toEqual(before); await dialog.getByRole('button', { name: '취소', exact: true }).click(); }
    else {
      if (response.status() !== 200 || fresh.title !== title + ' 수정') failures.push(title + ': HTTP/save failed');
      expect({ ...after, title: before.title }).toEqual(before);
      expect({ ...fresh, title: original.title }).toEqual(original);
    }
    evidence.note('C04 title-only attempt', note);
  }
  expect(failures, 'Every existing time shape must permit a title-only edit without changing its times').toEqual([]);
});

test('R01 P0: full list pages match real HTTP and a fresh GET', async ({ page, evidence }) => {
  await login(page, actor());
  const first = await navigate(page, '/roommates', '/roommates'); await assertCards(page, first);
  expect(first.size).toBe(9); expect(first.totalElements).toBeGreaterThanOrEqual(12);
  const secondResponse = await responseTo(page, 'GET', '/roommates', () => page.getByRole('button', { name: '2페이지', exact: true }).click());
  const second = (await secondResponse.json()).data; await assertCards(page, second);
  expect(ids(second).some(id => ids(first).includes(id))).toBe(false);
  expect(second).toEqual(await get(page, '/roommates?page=1&size=9'));
  expect(first).toEqual(await get(page, '/roommates?page=0&size=9'));
  evidence.note('R01 pages', { first, second, fixtureCandidateCount: database('snapshot').profiles.filter(p => p.nickname.startsWith('MC후보')).length });
});

test('R02 P0: recommended UI shows only the real first nine and a distinct empty state', async ({ page, evidence }) => {
  await login(page, actor());
  const data = await navigate(page, '/roommates?tab=recommended', '/roommates/recommended');
  expect(data.totalElements).toBeGreaterThanOrEqual(10); expect(data.content).toHaveLength(9);
  await assertCards(page, data);
  expect(data).toEqual(await get(page, '/roommates/recommended?page=0&size=9'));
  evidence.note('R02 recommendations', data);
  // A filter outside the ranked top nine verifies the empty UI without changing preferences or recalculating weights.
  const empty = await applySleep(page, '23시 ~ 24시');
  expect(empty.content).toEqual([]);
  await expect(page.getByText('조건에 맞는 추천 프로필이 없어요', { exact: true })).toBeVisible();
  const reset = await responseTo(page, 'GET', '/roommates/recommended', () => page.getByRole('button', { name: '필터 모두 해제', exact: true }).click());
  expect(new URL(reset.url()).searchParams.has('sleepTime')).toBe(false);
  const restored = (await reset.json()).data;
  expect(restored).toEqual(data); await assertCards(page, restored);
  await expect(page.getByRole('button', { name: / 필터 취소$/ })).toHaveCount(0);
  evidence.note('R02 empty and restored', { empty, restored });
});

test('R03 P0: existing filters chips reset and filtered all-list pagination match GET', async ({ page, evidence }) => {
  await login(page, actor()); const initial = await navigate(page, '/roommates', '/roommates');
  const filtered = await applySleep(page, '22시 이전'); await assertCards(page, filtered);
  expect(filtered.totalElements).toBeGreaterThan(9);
  expect(filtered).toEqual(await get(page, '/roommates?page=0&size=9&sleepTime=' + encodeURIComponent('22시 이전')));
  const next = await responseTo(page, 'GET', '/roommates', () => page.getByRole('button', { name: '2페이지', exact: true }).click());
  const second = (await next.json()).data; await assertCards(page, second);
  expect(second.content.length).toBeGreaterThan(0);
  expect(new URL(next.url()).searchParams.get('page')).toBe('1');
  expect(new URL(next.url()).searchParams.get('sleepTime')).toBe('22시 이전');
  expect(second).toEqual(await get(page, '/roommates?page=1&size=9&sleepTime=' + encodeURIComponent('22시 이전')));
  const removed = await responseTo(page, 'GET', '/roommates', () => page.getByRole('button', { name: '취침시간 22시 이전 필터 취소', exact: true }).click());
  expect(new URL(removed.url()).searchParams.has('sleepTime')).toBe(false);
  expect(new URL(removed.url()).searchParams.get('page')).toBe('0');
  expect((await removed.json()).data).toEqual(initial); await assertCards(page, initial);
  await expect(page.getByRole('button', { name: / 필터 취소$/ })).toHaveCount(0);
  await applySleep(page, '22시 이전');
  const reset = await responseTo(page, 'GET', '/roommates', () => page.getByRole('button', { name: '필터 초기화', exact: true }).click());
  expect(new URL(reset.url()).searchParams.has('sleepTime')).toBe(false);
  expect((await reset.json()).data).toEqual(initial); await assertCards(page, initial);
  await expect(page.getByRole('button', { name: / 필터 취소$/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '필터 초기화', exact: true })).toBeDisabled();
  await navigate(page, '/roommates?tab=recommended', '/roommates/recommended');
  const recommended = await applySleep(page, '22시 이전'); await assertCards(page, recommended);
  expect(recommended).toEqual(await get(page, '/roommates/recommended?page=0&size=9&sleepTime=' + encodeURIComponent('22시 이전')));
  evidence.note('R03 filtered results', { filtered, second, recommended });
});

test('R04 P0: same candidate score profile and survey agree across card detail GET and DB', async ({ page, evidence }) => {
  await login(page, actor()); const data = await navigate(page, '/roommates?tab=recommended', '/roommates/recommended');
  const target = data.content.find(p => p.nickname.startsWith('MC후보'));
  const r = await responseTo(page, 'GET', `/roommates/${target.id}`, () => link(page,target.nickname).click());
  const detail = (await r.json()).data;
  const dialog = page.getByRole('dialog', { name: target.nickname, exact: true });
  const panel = dialog.getByRole('tabpanel', { name: '생활 패턴', exact: true });
  for (const key of ['id','nickname','matchScore','introduction','mbti']) expect(detail[key]).toEqual(target[key]);
  await expect(dialog.getByRole('meter', { name: '나와의 매칭 점수' })).toHaveAttribute('aria-valuenow', String(target.matchScore));
  await expect(dialog.getByText(target.introduction, { exact: true })).toBeVisible();
  await expect(panel.getByText(detail.answers.sleepTime, { exact: true })).toBeVisible();
  await expect(panel).toContainText(detail.answers.dormStayDays.join(' · '));
  expect(detail).toEqual(await get(page, `/roommates/${target.id}`));
  const snapshot = database('snapshot'), me = profile(snapshot,'E2E사용자').user_id;
  expect(snapshot.matches.find(m => m.requester_user_id === me && m.target_user_id === target.id).match_score).toBe(target.matchScore);
  expect(snapshot.surveys.find(s => s.user_id === target.id).sleep_time).toBe(detail.answers.sleepTime);
  await dialog.getByRole('button', { name: '상세 프로필 닫기', exact: true }).click();
  await expect(dialog).toHaveCount(0); await expect(link(page,target.nickname)).toBeVisible();
  evidence.note('R04 agreement', { card: target, detail });
});

test('R05 P0: real analysis endpoint renders the fixed AI explanation without changing score', async ({ page, evidence }) => {
  await login(page, actor()); const data = await navigate(page, '/roommates?tab=recommended', '/roommates/recommended');
  const target = data.content.find(p => p.nickname.startsWith('MC후보'));
  // Keyboard focus reuses the production overlay trigger and avoids triggering every card by pointer travel.
  const r = await responseTo(page, 'POST', `/roommates/${target.id}/ai-analysis`, () => link(page,target.nickname).focus());
  const analysis = (await r.json()).data;
  expect(analysis).toEqual({ targetUserId: target.id, matchScore: target.matchScore, analysis: 'E2E 테스트용 고정 분석입니다.' });
  await expect(article(page,target.nickname).getByRole('status')).toHaveText(analysis.analysis);
  expect((await get(page, `/roommates/${target.id}`)).matchScore).toBe(analysis.matchScore);
  evidence.note('R05 local replacement only', analysis);
});

test('R06 P0: card detail and seven-person liked list stay synchronized through cancellation', async ({ page, evidence }) => {
  await login(page, actor()); await navigate(page, '/roommates', '/roommates');
  const snapshot = database('snapshot'), target = profile(snapshot,'MC후보08');
  const me = profile(snapshot,'E2E사용자').user_id;
  await responseTo(page,'POST',`/roommates/${target.user_id}/likes`,() => page.getByRole('button',{name:'MC후보08 프로필 좋아요',exact:true}).click());
  expect(database('snapshot').likes.filter(l => l.user_id === me)).toHaveLength(8);
  const liked = await navigate(page,'/profile?tab=likes','/users/me/liked-roommates'); expect(liked.totalElements).toBe(8);
  await responseTo(page,'GET',`/roommates/${target.user_id}`,() => link(page,'MC후보08').click());
  await responseTo(page,'DELETE',`/roommates/${target.user_id}/likes`,() => page.getByRole('button',{name:'MC후보08님 좋아요 취소',exact:true}).click());
  await expect(page.getByText('7명',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'상세 프로필 닫기',exact:true}).click();
  await responseTo(page,'GET','/users/me/liked-roommates',() => page.getByRole('button',{name:'2페이지',exact:true}).click());
  const oldest = profile(snapshot,'MC후보01');
  await responseTo(page,'DELETE',`/roommates/${oldest.user_id}/likes`,() => page.getByRole('button',{name:'MC후보01 프로필 좋아요 취소',exact:true}).click());
  await expect(page.getByText('6명',{exact:true})).toBeVisible(); await expect(link(page,'MC후보07')).toBeVisible();
  expect(await get(page,'/users/me/liked-roommates?page=0&size=6')).toMatchObject({ totalElements:6, page:0, totalPages:1 });
  expect(database('snapshot').likes.filter(l => l.user_id === me)).toHaveLength(6);
  evidence.note('R06 committed likes', { me, final: await get(page,'/users/me/liked-roommates?page=0&size=6') });
});

test('C01 P0: owner creates reads updates and deletes a normal event through React', async ({ page, evidence }) => {
  await login(page, actor()); const initial = await calendar(page); const date = initial.find(e => e.title === 'MC정상').startDate;
  await page.getByRole('button',{name:'일정 등록',exact:true}).click(); const dialog = page.getByRole('dialog',{name:'새 일정 등록',exact:true});
  await dialog.locator('#calendar-title').fill('MC CRUD'); await dialog.locator('#calendar-date').fill(date); await dialog.locator('#calendar-endDate').fill(date);
  await dialog.locator('#calendar-startTime').selectOption('09:00'); await dialog.locator('#calendar-endTime').selectOption('10:00'); await dialog.locator('#calendar-category').selectOption({label:'MC개인A'});
  const created = await responseTo(page,'POST','/calendar/events',() => dialog.getByRole('button',{name:'일정 등록',exact:true}).click()); const item = (await created.json()).data;
  expect((await get(page,range(initial))).find(e => e.id === item.id)).toEqual(item);
  const editing = await edit(page,'MC CRUD'); await editing.locator('#calendar-title').fill('MC CRUD 수정');
  const updated = await responseTo(page,'PATCH',`/calendar/events/${item.id}`,() => editing.getByRole('button',{name:'변경 내용 저장',exact:true}).click());
  const saved = (await updated.json()).data; expect(saved.title).toBe('MC CRUD 수정'); expect((await get(page,range(initial))).find(e => e.id === item.id)).toEqual(saved);
  expect(database('snapshot').events.find(e => e.event_id === item.id).title).toBe(saved.title);
  await page.getByRole('button',{name:/^MC CRUD 수정,.*상세 보기$/}).click();
  await responseTo(page,'DELETE',`/calendar/events/${item.id}`,() => page.getByRole('dialog').getByRole('button',{name:'삭제',exact:true}).click());
  expect((await get(page,range(initial))).some(e => e.id === item.id)).toBe(false); expect(database('snapshot').events.some(e => e.event_id === item.id)).toBe(false);
  evidence.note('C01 HTTP create update', { item, saved, deleted:true });
});

test('C02 P0: category settings persist color label visibility and explicit deletion', async ({ page, evidence }) => {
  await login(page,actor()); await calendar(page); await page.getByRole('button',{name:'카테고리 설정',exact:true}).click();
  let d = page.getByRole('dialog',{name:'카테고리 설정',exact:true}); await expect(d.locator('input[value="MC숨김"]')).toBeVisible();
  await d.getByRole('button',{name:'카테고리 추가',exact:true}).click(); await d.locator('input[id^="category-name-custom-"][value=""]').fill('MC새분류');
  await d.getByRole('button',{name:'MC빈분류 삭제',exact:true}).click();
  const response = await responseTo(page,'PUT','/calendar/categories',() => d.getByRole('button',{name:'설정 저장',exact:true}).click());
  let categories = (await response.json()).data; const custom = categories.find(c => c.label === 'MC새분류'); expect(custom).toBeTruthy();
  expect(categories.some(c => c.label === 'MC빈분류')).toBe(false); expect(categories.find(c => c.label === 'MC숨김').enabled).toBe(false);
  await page.getByRole('button',{name:'카테고리 설정',exact:true}).click(); d = page.getByRole('dialog',{name:'카테고리 설정',exact:true});
  await d.locator(`#category-name-${custom.code.toLowerCase()}`).fill('MC새이름'); await d.getByLabel('MC새이름 색상 설정',{exact:true}).fill('#abcdef');
  await d.getByRole('checkbox',{name:'MC새이름 표시',exact:true}).uncheck();
  const saved = await responseTo(page,'PUT','/calendar/categories',() => d.getByRole('button',{name:'설정 저장',exact:true}).click());
  categories = (await saved.json()).data; expect(categories.find(c => c.id === custom.id)).toMatchObject({label:'MC새이름',color:'#abcdef',enabled:false});
  expect(await get(page,'/calendar/categories')).toEqual(categories); await page.reload();
  await expect(page.getByRole('button',{name:'MC새이름',exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'카테고리 설정',exact:true}).click(); await expect(page.locator('input[value="MC새이름"]')).toBeVisible();
  const db = database('snapshot'); expect(db.categories.find(c => c.category_id === custom.id)).toMatchObject({default_name:'MC새분류'});
  expect(db.categorySettings.find(c => c.category_id === custom.id)).toMatchObject({display_name:'MC새이름',color:'#abcdef',is_enabled:false});
  evidence.note('C02 persisted categories', { categories, category:db.categories.find(c => c.category_id === custom.id),
    setting:db.categorySettings.find(c => c.category_id === custom.id) });
});

test('C03 P0: public events remain visible while private events and editing obey ownership', async ({ page, evidence }) => {
  await login(page,actor()); const a = await calendar(page); const privateEvent = a.find(e => e.title === 'MC정상'), publicEvent = a.find(e => e.title === 'MC합성공개');
  expect(privateEvent.editable).toBe(true); expect(publicEvent.editable).toBe(false); expect(a.some(e => e.title === 'MC비공개B')).toBe(false);
  await page.getByRole('button',{name:/^MC합성공개,.*상세 보기$/}).click(); await expect(page.getByRole('button',{name:'일정 수정',exact:true})).toHaveCount(0); await expect(page.getByRole('button',{name:'삭제',exact:true})).toHaveCount(0);
  await page.getByRole('dialog').getByRole('button',{name:'닫기',exact:true}).click(); await logout(page);
  const guest = await calendar(page); expect(guest.map(e => e.id)).toEqual([publicEvent.id]); await expect(page.getByRole('button',{name:/^MC정상,.*상세 보기$/})).toHaveCount(0);
  await login(page,peer()); const b = await calendar(page); expect(b.some(e => e.id === privateEvent.id)).toBe(false); expect(b.some(e => e.id === publicEvent.id)).toBe(true); expect(b.find(e => e.title === 'MC비공개B').editable).toBe(true);
  const before = database('snapshot').events;
  const patched = await browserRequest(page,'PATCH',`/calendar/events/${privateEvent.id}`,{title:'foreign'}); expect(patched).toMatchObject({status:403,code:'FORBIDDEN'});
  const deleted = await browserRequest(page,'DELETE',`/calendar/events/${privateEvent.id}`); expect(deleted).toMatchObject({status:403,code:'FORBIDDEN'});
  expect(database('snapshot').events).toEqual(before); expect(await get(page,range(a))).toEqual(b);
  evidence.note('C03 actors', { a, guest, b, patchedStatus:patched.status, deletedStatus:deleted.status });
});

test('X01 P0: account switch and natural expiry isolate matching likes events and categories', async ({ page, evidence }) => {
  await login(page,actor()); const aRecommended = await navigate(page,'/roommates?tab=recommended','/roommates/recommended');
  const aLikes = await get(page,'/users/me/liked-roommates?page=0&size=6'); const aEvents = await calendar(page); const aCategories = await get(page,'/calendar/categories');
  await logout(page); await login(page,peer()); const bRecommended = await navigate(page,'/roommates?tab=recommended','/roommates/recommended');
  const bLikes = await navigate(page,'/profile?tab=likes','/users/me/liked-roommates'); expect(bLikes.totalElements).toBe(1); await expect(link(page,'MC후보12')).toBeVisible(); await expect(link(page,'MC후보01')).toHaveCount(0);
  const common = aRecommended.content.find(a => bRecommended.content.some(b => b.id === a.id)); expect(bRecommended.content.find(b => b.id === common.id).matchScore).not.toBe(common.matchScore);
  const bEvents = await calendar(page); await expect(page.getByRole('button',{name:/^MC비공개B,.*상세 보기$/})).toBeVisible(); await expect(page.getByRole('button',{name:/^MC정상,.*상세 보기$/})).toHaveCount(0);
  const bCategories = await get(page,'/calendar/categories'); expect(bCategories.some(c => c.label === 'MC개인A')).toBe(false); expect(bCategories.some(c => c.label === 'MC개인B')).toBe(true);
  const before = database('snapshot');
  await control('restart',{ttlSeconds:8});
  try {
    await logout(page); await login(page,peer()); await calendar(page);
    await page.waitForTimeout(9200);
    // Expired token must reach the real server. Browser fetch does not invoke Axios session handling.
    const expired = await browserRequest(page,'GET','/users/me/liked-roommates?page=0&size=6'); expect(expired.status).toBe(401);
    await page.goto('/profile?tab=likes'); await expect.poll(() => page.evaluate(() => Boolean(localStorage.getItem('accessToken')))).toBe(false);
    await expect(link(page,'MC후보12')).toHaveCount(0);
    const guest = await calendar(page); expect(guest.every(e => e.editable === false)).toBe(true); expect(guest.some(e => e.title === 'MC비공개B')).toBe(false);
  } finally { await control('restart'); }
  await login(page,peer()); expect(await get(page,'/users/me/liked-roommates?page=0&size=6')).toEqual(bLikes);
  const restored = await calendar(page); expect(restored).toEqual(bEvents);
  expect(database('snapshot').events).toEqual(before.events); expect(database('snapshot').likes).toEqual(before.likes);
  evidence.note('X01 isolation', { aRecommended, bRecommended, aLikes, bLikes, aEvents, bEvents, aCategories, bCategories, naturalExpirySeconds:8, waitedMs:9200 });
});
