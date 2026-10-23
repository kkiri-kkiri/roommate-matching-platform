const { randomBytes } = require('node:crypto');
const { test, expect, database } = require('../fixtures.cjs');
const { responseTo, login, logout, control } = require('../lib/flows.cjs');
const { signup, prepareSignup, verifySignup, saveSurvey, preference } = require('../lib/aus-flows.cjs');

// B2 is the unchanged login.spec.cjs baseline. These are independent tests, never selected test.step bodies.
test.setTimeout(120_000);
test.beforeEach(async ({ page }) => { page.on('dialog', dialog => dialog.accept()); });
const email = () => process.env.E2E_LOGIN_EMAIL;
const newEmail = () => 'new' + process.env.E2E_DATABASE.slice(-12) + '@hknu.ac.kr';
const snake = key => key.replace(/[A-Z]/g, c => '_' + c.toLowerCase());
const card = (page, name) => page.getByRole('heading', { name, exact: typeof name === 'string' }).locator('xpath=ancestor::section[1]');
async function get(page, path, navigate) {
  const response = await responseTo(page, 'GET', path, navigate);
  return (await response.json()).data;
}
async function rejectedLogin(page, password) {
  await page.goto('/login');
  await page.getByLabel('학교 이메일', { exact: true }).fill(email().split('@')[0]);
  await responseTo(page, 'POST', '/auth/login/email-check', () => page.getByRole('button', { name: '다음', exact: true }).click());
  await page.getByLabel('비밀번호', { exact: true }).fill(password);
  const response = await responseTo(page, 'POST', '/auth/login', () => page.getByRole('button', { name: '로그인', exact: true }).click(), 401);
  expect((await response.json()).errorCode).toBe('UNAUTHORIZED');
}

test('B1 P0: real email verification and signup commit then account retrieval', async ({ page, evidence }) => {
  await signup(page, newEmail());
  const snapshot = database('snapshot');
  const me = snapshot.profiles.find(p => p.nickname === 'E2E신규');
  expect(snapshot.users.find(u => u.user_id === me.user_id).email_verified).toBe(true);
  expect(me).toMatchObject({ faculty_name: 'HK자율전공학부', major_id: null, mbti: 'INTJ' });
  await login(page, newEmail());
  const account = await get(page, '/users/me', () => page.goto('/profile?tab=account'));
  expect(account).toMatchObject({ id: me.user_id, nickname: 'E2E신규', major: null, faculty: 'HK자율전공학부' });
  await expect(page.getByRole('heading', { name: '계정 정보', exact: true })).toBeVisible();
  evidence.note('signup', { committedUserId: me.user_id, verified: true, accountGET: true });
});

test('B3 P0: signup token expires naturally and the same form recovers through re-verification', async ({ page, evidence }) => {
  test.setTimeout(180_000);
  await control('restart', { signupTtlSeconds: 8 });
  try {
    await prepareSignup(page, newEmail());
    const started = Date.now();
    await verifySignup(page, newEmail());
    await page.waitForTimeout(9200);
    const rejected = await responseTo(page, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click(), 400);
    expect((await rejected.json()).errorCode).toBe('EMAIL_VERIFICATION_TOKEN_INVALID');
    expect(database('snapshot').users).toHaveLength(3);
    await expect(page.getByRole('button', { name: '인증번호 전송', exact: true })).toBeEnabled();
    await expect(page.locator('#signup-nickname')).toHaveValue('E2E신규');
    // Keep the real 60-second MySQL cooldown; do not edit limiter rows or skip the real send.
    await page.waitForTimeout(Math.max(0, 61_000 - (Date.now() - started)));
    await verifySignup(page, newEmail());
    await responseTo(page, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
    await login(page, newEmail());
    expect(database('snapshot').profiles.some(p => p.nickname === 'E2E신규')).toBe(true);
    evidence.note('natural signup expiry', { ttlSeconds: 8, elapsedWaitMs: 9200, rejected: 400, recovered: true, cooldownBypassed: false });
  } finally { await control('restart'); }
});

test('B4 P0: account academic and MBTI edits persist through JDBC GET and relogin', async ({ page, evidence }) => {
  await login(page, email());
  await page.goto('/profile?tab=account');
  const basic = card(page, '기본 정보');
  await basic.getByRole('button', { name: '수정', exact: true }).click();
  await page.locator('#account-nickname').fill('E2E변경');
  await responseTo(page, 'PATCH', '/users/me', () => basic.getByRole('button', { name: '완료', exact: true }).click());
  const academic = card(page, '생활관 및 학적 정보');
  async function academicSave(faculty, major) {
    await academic.getByRole('button', { name: '수정', exact: true }).click();
    await page.locator('#account-dormitory').selectOption('나래');
    await page.locator('#account-grade').selectOption('3학년');
    await page.locator('#account-faculty').selectOption(faculty);
    if (major) await page.locator('#account-major').selectOption(major);
    await responseTo(page, 'PATCH', '/users/me', () => academic.getByRole('button', { name: '완료', exact: true }).click());
    const row = database('snapshot').profiles.find(p => p.nickname === 'E2E변경');
    expect(row).toMatchObject({ faculty_name: faculty, major_name: major, dormitory: '나래', grade: '3학년' });
  }
  await academicSave('컴퓨터응용수학부', '소프트웨어융합전공');
  await academicSave('컴퓨터응용수학부', '정보보안전공');
  await academicSave('법경영학부', '법학전공');
  await academicSave('HK자율전공학부', null);
  const mbti = card(page, /^MBTI/);
  await mbti.getByRole('button', { name: '수정', exact: true }).click();
  for (const value of ['E', 'S', 'F', 'P']) await mbti.getByRole('button', { name: value, exact: true }).click();
  await responseTo(page, 'PATCH', '/users/me', () => mbti.getByRole('button', { name: '완료', exact: true }).click());
  expect(database('snapshot').profiles.find(p => p.nickname === 'E2E변경').mbti).toBe('ESFP');
  const after = await get(page, '/users/me', () => page.reload());
  expect(after).toMatchObject({ nickname: 'E2E변경', major: null, faculty: 'HK자율전공학부', mbtiEnergy: 'E', mbtiInformation: 'S', mbtiDecision: 'F', mbtiLifestyle: 'P' });
  await logout(page); await login(page, email());
  const restored = await get(page, '/users/me', () => page.goto('/profile?tab=account'));
  expect(restored).toEqual(after);
  evidence.note('account edits', { patches: 6, academicTransitions: 4, independentJDBC: true, reloginGETEqual: true });
});

test('B5 P0: UI password change rejects the previous password and accepts the new password', async ({ page, evidence }) => {
  const changed = 'E2e!' + randomBytes(18).toString('hex');
  await login(page, email()); await page.goto('/profile?tab=account');
  const password = card(page, '비밀번호 변경');
  await password.getByRole('button', { name: '수정', exact: true }).click();
  await page.locator('#account-currentPassword').fill(process.env.E2E_LOGIN_PASSWORD);
  await page.locator('#account-newPassword').fill(changed);
  await page.locator('#account-confirmPassword').fill(changed);
  await responseTo(page, 'PATCH', '/users/me/password', () => password.getByRole('button', { name: '완료', exact: true }).click());
  await logout(page); await rejectedLogin(page, process.env.E2E_LOGIN_PASSWORD);
  await login(page, email(), changed);
  expect((await get(page, '/users/me', () => page.goto('/profile?tab=account'))).nickname).toBe('E2E사용자');
  database('verify');
  evidence.note('password change', { oldLogin: 401, newLogin: 200, persistedAcrossLogin: true });
});

test('B6 P0: actual reset mail token enters React and commits a password usable for login', async ({ page, evidence }) => {
  const changed = 'E2e!' + randomBytes(18).toString('hex');
  await page.goto('/find-password');
  await page.locator('#find-password-email').fill(email().split('@')[0]);
  await responseTo(page, 'POST', '/auth/password-reset/request', () => page.getByRole('button', { name: '비밀번호 재설정 이메일 보내기', exact: true }).click());
  const { code: token } = await control('mail', { email: email(), kind: 'reset' });
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) throw new Error('Reset mail capture missing');
  const before = database('snapshot'); expect(before.resetStates).toHaveLength(1);
  expect(Boolean(before.resetStates[0].used)).toBe(false);
  await page.getByRole('button', { name: '메일의 재설정 토큰 입력하기', exact: true }).click();
  await page.locator('#reset-token').fill(token);
  await page.locator('#new-password').fill(changed);
  await page.locator('#new-password-confirm').fill(changed);
  await responseTo(page, 'POST', '/auth/password-reset/confirm', () => page.getByRole('button', { name: '비밀번호 변경하기', exact: true }).click());
  expect(Boolean(database('snapshot').resetStates[0].used)).toBe(true);
  await expect(page.getByText('비밀번호를 변경했어요', { exact: true })).toBeVisible();
  await rejectedLogin(page, process.env.E2E_LOGIN_PASSWORD);
  await login(page, email(), changed); database('verify');
  evidence.note('reset', { source: 'real service mail via private memory IPC', tokenStoredInEvidence: false, committedUsed: true, oldLogin: 401, newLogin: 200 });
});

test('B7 P0: profile survey full answers and introduction persist and are retrieved after relogin', async ({ page, evidence }) => {
  await login(page, email());
  const saved = await saveSurvey(page, 'E2E 프로필 저장');
  expect(saved.completed).toBe(true);
  const db = database('snapshot'), row = db.surveys[0];
  for (const [key, value] of Object.entries(saved.answers)) {
    if (Array.isArray(value)) {
      const code = key === 'dormStayDays' ? 'DORM_STAY_DAY' : 'MEAL_PLAN';
      expect(db.surveyOptions.filter(o => o.field_code === code).map(o => o.option_value).sort()).toEqual([...value].sort());
    } else expect(row[snake(key)], key).toBe(value);
  }
  expect(db.profiles.find(p => p.user_id === row.user_id).introduction).toBe(saved.introduction);
  const retrieved = await get(page, '/users/me/profile-survey', () => page.reload());
  expect(retrieved).toEqual(saved);
  await logout(page); await login(page, email());
  expect(await get(page, '/users/me/profile-survey', () => page.goto('/profile?tab=survey'))).toEqual(saved);
  await expect(page.getByText(saved.introduction, { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '설문 수정', exact: true }).click();
  await expect(page.locator('#survey-sleepTime')).toHaveValue(saved.answers.sleepTime);
  evidence.note('profile survey', { fields: Object.keys(saved.answers).length, fullDBProjectionCompared: true, reloadAndReloginEqual: true });
});

test('B8 P0: preference survey scalar multi answers and all 18 weights survive GET and relogin', async ({ page, evidence }) => {
  await login(page, email());
  // PUT intentionally returns Void; compare its submitted values with JDBC and the real subsequent GET.
  const saved = { completed: true, answers: await preference(page) };
  expect(saved.completed).toBe(true);
  const db = database('snapshot'), row = db.preferences[0];
  const scalar = { department: 'department_preference', sleepTalking: 'sleep_talking_allowance', snoring: 'snoring_allowance', teethGrinding: 'teeth_grinding_allowance', smoking: 'smoking_preference', lightingPreference: 'lighting_preference', showerTime: 'shower_time_preference', showerDuration: 'shower_duration_preference', cleaningStyle: 'cleaning_style_preference', interaction: 'interaction_preference', meal: 'meal_preference', sharedItems: 'shared_items_preference', temperature: 'temperature_preference' };
  const code = key => key === 'lightingPreference' ? 'LIGHTING' : snake(key).toUpperCase();
  for (const [key, value] of Object.entries(saved.answers)) {
    if (key === 'weights') {
      expect(db.criteria).toHaveLength(18);
      for (const [k, weight] of Object.entries(value)) expect(db.criteria.find(c => c.criterion_code === code(k)).weight, k).toBe(weight);
    } else if (Array.isArray(value)) expect(db.preferenceOptions.filter(o => o.criterion_code === code(key)).map(o => o.option_value).sort(), key).toEqual([...value].sort());
    else expect(row[scalar[key]], key).toBe(value);
  }
  expect(await get(page, '/users/me/roommate-preferences', () => page.reload())).toEqual(saved);
  await logout(page); await login(page, email());
  expect(await get(page, '/users/me/roommate-preferences', () => page.goto('/profile?tab=preferences'))).toEqual(saved);
  await page.getByRole('button', { name: '설문 수정', exact: true }).click();
  await expect(page.locator('#preferences-bedtime-weight')).toHaveValue('5');
  await expect(page.locator('#preferences-wakeTime-weight')).toHaveValue('1');
  evidence.note('preference survey', { weights: 18, scalarAndMultiCompared: true, reloadAndReloginEqual: true });
});

test('B9 P0: logout account switching and naturally expired JWT isolate account and survey data', async ({ page, peerPage, evidence }) => {
  test.setTimeout(180_000);
  peerPage.on('dialog', dialog => dialog.accept());
  await login(page, email()); await saveSurvey(page, 'E2E 계정A 전용'); await preference(page);
  await login(peerPage, process.env.E2E_SECOND_EMAIL); await saveSurvey(peerPage, 'E2E 계정B 전용');
  const before = database('snapshot');
  await logout(page); await page.goto('/profile?tab=survey'); await expect(page).toHaveURL(/\/login$/);
  await login(page, process.env.E2E_SECOND_EMAIL);
  await page.goto('/profile?tab=survey');
  await expect(page.getByText('E2E 계정B 전용', { exact: true })).toBeVisible();
  await expect(page.getByText('E2E 계정A 전용', { exact: true })).toHaveCount(0);
  await page.goto('/profile?tab=preferences');
  await expect(page.getByRole('button', { name: '선호 룸메 설문 작성하기', exact: true })).toBeVisible();
  const accountB = await get(page, '/users/me', () => page.goto('/profile?tab=account'));
  expect(accountB.nickname).toBe('E2E상대');
  await control('restart', { ttlSeconds: 8 });
  try {
    await login(page, process.env.E2E_SECOND_EMAIL);
    await page.goto('/profile?tab=survey');
    const initial = await page.evaluate(async () => {
      const token = localStorage.getItem('accessToken');
      window.__expiryCheck = async () => (await fetch('http://127.0.0.1:18080/api/auth/me', { headers: { Authorization: 'Bearer ' + token } })).status;
      return window.__expiryCheck();
    });
    expect(initial).toBe(200);
    await expect.poll(() => page.evaluate(() => window.__expiryCheck()), { timeout: 15_000 }).toBe(401);
    await expect.poll(() => page.evaluate(() => Boolean(localStorage.getItem('accessToken')))).toBe(false);
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText('E2E 계정B 전용', { exact: true })).toHaveCount(0);
  } finally { await control('restart'); }
  await login(page, email()); await page.goto('/profile?tab=survey');
  await expect(page.getByText('E2E 계정A 전용', { exact: true })).toBeVisible();
  await expect(page.getByText('E2E 계정B 전용', { exact: true })).toHaveCount(0);
  const after = database('snapshot');
  expect(after.profiles).toEqual(before.profiles); expect(after.surveys).toEqual(before.surveys);
  expect(after.preferences).toEqual(before.preferences);
  evidence.note('session isolation', { sameContextAccountSwitch: true, naturalExpiry: true, realTokenHTTP: [200, 401], restoredOriginalAccount: true, businessRowsUnchanged: true });
});
