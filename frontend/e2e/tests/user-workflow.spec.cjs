const { test, expect, database } = require('../fixtures.cjs');

const { responseTo, control, login, logout } = require('../lib/flows.cjs');
const { saveSurvey, preference, signup } = require('../lib/aus-flows.cjs');

test('A–C/F: real signup, surveys, exact weighted matching, likes, calendar, ownership and restart persistence', async ({ page, peerPage }) => {
  test.setTimeout(300_000);
  page.on('dialog', dialog => dialog.accept());
  peerPage.on('dialog', dialog => dialog.accept());
  const email = 'new' + process.env.E2E_DATABASE.slice(-12) + '@hknu.ac.kr';
  let me, target, eventId;
  const score = () => database('snapshot').matches.find(row => row.requester_user_id === me && row.target_user_id === target)?.match_score;
  await test.step('A: real email verification and signup; profile survey survives logout and login', async () => {
    await signup(page, email);
    await login(page, email);
    await saveSurvey(page, 'E2E 실제 DB 설문');
    const snapshot = database('snapshot');
    me = snapshot.profiles.find(p => p.nickname === 'E2E신규').user_id;
    target = snapshot.profiles.find(p => p.nickname === 'E2E사용자').user_id;
    expect(snapshot.surveys.find(s => s.user_id === me).sleep_time).toBe('22시 이전');
    await logout(page); await login(page, email);
    await page.goto('/profile?tab=survey');
    await expect(page.getByText('E2E 실제 DB 설문', { exact: true })).toBeVisible();
  });
  await test.step('B: committed target survey and weights produce independently defined 100 → 17 → 83 in MySQL before GET', async () => {
    await login(peerPage, process.env.E2E_LOGIN_EMAIL);
    await saveSurvey(peerPage, 'E2E 대상 소개');
    await preference(page);
    await expect.poll(score, { timeout: 20_000 }).toBe(100);
    const initialCards = await responseTo(page, 'GET', '/roommates/recommended', () => page.goto('/roommates?tab=recommended'));
    expect((await initialCards.json()).data.content.find(p => p.id === target)).toEqual(expect.objectContaining({ matchScore: 100, introduction: 'E2E 대상 소개' }));
    await responseTo(page, 'GET', `/roommates/${target}`, () => page.getByRole('link', { name: 'E2E사용자 상세 프로필 보기', exact: true }).click());
    await expect(page.getByText('E2E 대상 소개', { exact: true }).first()).toBeVisible();
    await page.goto('/profile?tab=preferences');
    await saveSurvey(peerPage, 'E2E 변경한 대상 소개', '22시 ~ 23시');
    // No roommate/recommendation GET before checking AFTER_COMMIT refresh in the independent JDBC connection.
    await expect.poll(score, { timeout: 20_000 }).toBe(17);
    await preference(page, true);
    await expect.poll(score, { timeout: 20_000 }).toBe(83);
    const cards = await responseTo(page, 'GET', '/roommates/recommended', () => page.goto('/roommates?tab=recommended'));
    expect((await cards.json()).data.content.find(p => p.id === target).matchScore).toBe(83);
    await expect(page.getByLabel('매칭점수 83점')).toBeVisible();
    await responseTo(page, 'GET', `/roommates/${target}`, () => page.getByRole('link', { name: 'E2E사용자 상세 프로필 보기', exact: true }).click());
    await expect(page.getByText('E2E 변경한 대상 소개', { exact: true }).first()).toBeVisible();
  });
  await test.step('C: like creation, MyPage retrieval, cancellation and committed DB deletion', async () => {
    await page.goto('/roommates');
    await responseTo(page, 'POST', `/roommates/${target}/likes`, () => page.getByRole('button', { name: 'E2E사용자 프로필 좋아요', exact: true }).click());
    expect(database('snapshot').likes).toEqual([{ user_id: me, target_user_id: target }]);
    await page.goto('/profile?tab=likes');
    await expect(page.getByRole('link', { name: 'E2E사용자 상세 프로필 보기', exact: true })).toBeVisible();
    await responseTo(page, 'DELETE', `/roommates/${target}/likes`, () => page.getByRole('button', { name: 'E2E사용자 프로필 좋아요 취소', exact: true }).click());
    expect(database('snapshot').likes).toEqual([]);
  });
  await test.step('D1: custom category and event create/update commit through React', async () => {
    await page.goto('/calendar');
    await page.getByRole('button', { name: '카테고리 설정', exact: true }).click();
    let dialog = page.getByRole('dialog', { name: '카테고리 설정', exact: true });
    await dialog.getByRole('button', { name: '카테고리 추가', exact: true }).click();
    await dialog.locator('input[id^="category-name-custom-"]').fill('E2E분류');
    await responseTo(page, 'POST', '/calendar/categories/custom', () => dialog.getByRole('button', { name: '설정 저장', exact: true }).click());
    await expect(dialog).not.toBeVisible();
    expect(database('snapshot').categories.some(c => c.owner_user_id === me && c.default_name === 'E2E분류')).toBe(true);
    await page.getByRole('button', { name: '일정 등록', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '새 일정 등록', exact: true });
    const date = await page.evaluate(() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; });
    await dialog.locator('#calendar-title').fill('E2E 일정');
    await dialog.locator('#calendar-date').fill(date); await dialog.locator('#calendar-endDate').fill(date);
    await dialog.locator('#calendar-startTime').selectOption('09:00'); await dialog.locator('#calendar-endTime').selectOption('10:00');
    await dialog.locator('#calendar-category').selectOption({ label: 'E2E분류' });
    const created = await responseTo(page, 'POST', '/calendar/events', () => dialog.getByRole('button', { name: '일정 등록', exact: true }).click());
    eventId = (await created.json()).data.id;
    expect(database('snapshot').events).toContainEqual(expect.objectContaining({ event_id: eventId, owner_user_id: me, title: 'E2E 일정', start_date: date, start_time: '09:00:00' }));
    await page.getByRole('button', { name: /E2E 일정,.*상세 보기/ }).click();
    await page.getByRole('button', { name: '일정 수정', exact: true }).click();
    await page.locator('#calendar-title').fill('E2E 수정 일정');
    await responseTo(page, 'PATCH', `/calendar/events/${eventId}`, () => page.getByRole('button', { name: '변경 내용 저장', exact: true }).click());
    expect(database('snapshot').events.find(e => e.event_id === eventId).title).toBe('E2E 수정 일정');
  });
  await test.step('Restart: same MySQL rows and authenticated UI survive a real backend JVM restart', async () => {
    const before = database('snapshot');
    expect((await control('restart')).restarted).toBe(true);
    expect(database('snapshot')).toEqual(before);
    await page.reload();
    await expect(page.getByRole('button', { name: /E2E 수정 일정,.*상세 보기/ })).toBeVisible();
    await page.goto('/profile?tab=survey');
    await expect(page.getByText('E2E 실제 DB 설문', { exact: true })).toBeVisible();
  });
  await test.step('E: other account sees no private event; foreign PATCH is forbidden and preserves DB', async () => {
    const before = database('snapshot');
    const events = await responseTo(peerPage, 'GET', '/calendar/events', () => peerPage.goto('/calendar'));
    expect((await events.json()).data.some(e => e.id === eventId)).toBe(false);
    await expect(peerPage.getByRole('button', { name: /E2E 수정 일정,.*상세 보기/ })).toHaveCount(0);
    const rejected = await peerPage.evaluate(async id => {
      const r = await fetch(`http://127.0.0.1:18080/api/calendar/events/${id}`, {
        method: 'PATCH', headers: { Authorization: `Bearer ${localStorage.getItem('accessToken')}`, 'Content-Type': 'application/json' }, body: JSON.stringify({ title: 'foreign' }),
      });
      return { status: r.status, code: (await r.json()).errorCode };
    }, eventId);
    expect(rejected.status).toBe(403);
    expect(rejected.code).toBe('FORBIDDEN');
    const deleted = await peerPage.evaluate(async id => {
      const r = await fetch(`http://127.0.0.1:18080/api/calendar/events/${id}`, {
        method: 'DELETE', headers: { Authorization: `Bearer ${localStorage.getItem('accessToken')}` },
      });
      return { status: r.status, code: (await r.json()).errorCode };
    }, eventId);
    expect(deleted).toEqual({ status: 403, code: 'FORBIDDEN' });
    expect(database('snapshot')).toEqual(before);
  });
  await test.step('D2: owner deletes event and category through UI; independent DB connection confirms removal', async () => {
    await logout(page); await login(page, email); await page.goto('/calendar');
    await page.getByRole('button', { name: /E2E 수정 일정,.*상세 보기/ }).click();
    await responseTo(page, 'DELETE', `/calendar/events/${eventId}`, () => page.getByRole('dialog').getByRole('button', { name: '삭제', exact: true }).click());
    expect(database('snapshot').events).toEqual([]);
    const category = database('snapshot').categories.find(c => c.default_name === 'E2E분류');
    await page.getByRole('button', { name: '카테고리 설정', exact: true }).click();
    await page.getByRole('button', { name: 'E2E분류 삭제', exact: true }).click();
    await responseTo(page, 'DELETE', `/calendar/categories/${category.category_id}`, () => page.getByRole('button', { name: '설정 저장', exact: true }).click());
    expect(database('snapshot').categories.some(c => c.category_id === category.category_id)).toBe(false);
  });
});
