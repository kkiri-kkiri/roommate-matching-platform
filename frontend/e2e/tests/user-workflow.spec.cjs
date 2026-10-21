const { test, expect, database } = require('../fixtures.cjs');

const apiOrigin = 'http://127.0.0.1:18080';
async function responseTo(page, method, path, action) {
  const response = page.waitForResponse(r => r.url().split('?')[0] === apiOrigin + '/api' + path && r.request().method() === method);
  await action();
  const result = await response;
  expect(result.status()).toBe(200);
  return result;
}
async function control(operation, data = {}) {
  const response = await fetch(process.env.E2E_CONTROL_URL + '/' + operation, {
    method: 'POST', headers: { Authorization: `Bearer ${process.env.E2E_CONTROL_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(data), signal: AbortSignal.timeout(120_000),
  });
  if (!response.ok) throw new Error('Managed E2E control failed');
  return response.json();
}
async function login(page, email) {
  await page.goto('/login');
  await page.getByLabel('학교 이메일', { exact: true }).fill(email.split('@')[0]);
  await responseTo(page, 'POST', '/auth/login/email-check', () => page.getByRole('button', { name: '다음', exact: true }).click());
  await page.getByLabel('비밀번호', { exact: true }).fill(process.env.E2E_LOGIN_PASSWORD);
  await responseTo(page, 'POST', '/auth/login', () => page.getByRole('button', { name: '로그인', exact: true }).click());
  await expect(page).toHaveURL('http://127.0.0.1:13000/');
}
async function logout(page) {
  await page.getByRole('button', { name: '프로필 메뉴 열기', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await expect.poll(() => page.evaluate(() => Boolean(localStorage.getItem('accessToken')))).toBe(false);
}
async function saveSurvey(page, introduction, sleep = '22시 이전') {
  await page.goto('/profile?tab=survey');
  await page.getByRole('button', { name: /내 설문 작성하기|설문 수정/, exact: true }).click();
  for (const select of await page.locator('select[id^="survey-"]').all()) await select.selectOption({ index: 1 });
  for (const group of await page.locator('fieldset:has(> legend)').all()) {
    const first = group.getByRole('button').first();
    if (await first.getAttribute('aria-pressed') !== 'true') await first.click();
  }
  await page.locator('#survey-sleepTime').selectOption(sleep);
  await page.locator('#survey-wakeTime').selectOption('07시 이전');
  await page.locator('#survey-introduction').fill(introduction);
  const result = await responseTo(page, 'PUT', '/users/me/profile-survey', () => page.getByRole('button', { name: '저장', exact: true }).click());
  expect((await result.json()).data.introduction).toBe(introduction);
  await expect(page.getByText(introduction, { exact: true })).toBeVisible();
}
async function preference(page, reverse = false) {
  await page.goto('/profile?tab=preferences');
  await page.getByRole('button', { name: /선호 룸메 설문 작성하기|설문 수정/, exact: true }).click();
  if (!reverse) {
    for (const select of await page.locator('select[id^="preferences-"]').all()) await select.selectOption('상관없음');
    for (const group of await page.locator('fieldset:has(> legend)').all()) await group.getByRole('button', { name: '상관없음', exact: true }).click();
    await page.getByRole('group', { name: '선호하는 취침시간 복수 선택 가능', exact: true }).getByRole('button', { name: '22시 이전', exact: true }).click();
    await page.getByRole('group', { name: '선호하는 기상시간 복수 선택 가능', exact: true }).getByRole('button', { name: '07시 이전', exact: true }).click();
  }
  await page.locator('#preferences-bedtime-weight').press(reverse ? 'Home' : 'End');
  await page.locator('#preferences-wakeTime-weight').press(reverse ? 'End' : 'Home');
  await responseTo(page, 'PUT', '/users/me/roommate-preferences', () => page.getByRole('button', { name: '저장', exact: true }).click());
  await expect(page.getByRole('button', { name: '설문 수정', exact: true })).toBeVisible();
}

test('A–C/F: real signup, surveys, exact weighted matching, likes, calendar, ownership and restart persistence', async ({ page, peerPage }) => {
  test.setTimeout(300_000);
  page.on('dialog', dialog => dialog.accept());
  peerPage.on('dialog', dialog => dialog.accept());
  const email = 'new' + process.env.E2E_DATABASE.slice(-12) + '@hknu.ac.kr';
  let me, target, eventId;
  const score = () => database('snapshot').matches.find(row => row.requester_user_id === me && row.target_user_id === target)?.match_score;
  await test.step('A: real email verification and signup; profile survey survives logout and login', async () => {
    await page.goto('/signup');
    await page.locator('#signup-school-email').fill(email.split('@')[0]);
    await responseTo(page, 'POST', '/auth/email-verifications/send', () => page.getByRole('button', { name: '인증번호 전송', exact: true }).click());
    const { code } = await control('mail', { email });
    if (!/^\d{6}$/.test(code)) throw new Error('Memory SMTP did not capture a verification code');
    await page.locator('#signup-verification-code').fill(code);
    await responseTo(page, 'POST', '/auth/email-verifications/verify', () => page.getByRole('button', { name: '인증 확인', exact: true }).click());
    await page.locator('#signup-password').fill(process.env.E2E_LOGIN_PASSWORD);
    await page.locator('#signup-password-confirm').fill(process.env.E2E_LOGIN_PASSWORD);
    await page.locator('#signup-nickname').fill('E2E신규');
    await responseTo(page, 'GET', '/auth/signup/availability', () => page.getByRole('button', { name: '중복 확인', exact: true }).click());
    await page.locator('label[for="signup-gender-0"]').click();
    await page.locator('label[for="signup-dormitory-0"]').click();
    await page.locator('#signup-faculty').selectOption('HK자율전공학부');
    await page.locator('#signup-birth-year').fill('2003');
    await page.locator('#signup-student-id').fill('22');
    await page.locator('label[for="signup-grade-0"]').click();
    for (const label of ['에너지 방향 I', '인식 방식 N', '판단 방식 T', '생활 양식 J']) await page.getByRole('button', { name: label, exact: true }).click();
    await responseTo(page, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
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
