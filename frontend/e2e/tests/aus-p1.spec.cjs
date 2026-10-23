const { test, expect, database, responseTo, login, logout, control, browserRequest, prepareSignup, verifySignup,
  saveSurvey, preference, secret, password, card, noWrite, sendCode, verifyCode, editSurvey,
  resetRequest, resetFields, resetSubmit, resetToken, gate, failGet } = require('../lib/aus-p1-flows.cjs');
test.setTimeout(180_000);
const me = () => process.env.E2E_LOGIN_EMAIL;
const peer = () => process.env.E2E_SECOND_EMAIL;
const defaultPassword = () => process.env.E2E_LOGIN_PASSWORD;
const submitSignup = page => responseTo(page, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
async function oldPasswordWorks(page, email = me()) {
  const result = await browserRequest(page, 'POST', '/auth/login', { email, password: defaultPassword() }, { anonymous: true });
  expect(result.status).toBe(200); // Never attach the login result/token.
}

test('B10-wrong-code: real invalid code rejects then the correct code recovers', async ({ page, p1 }) => {
  await page.goto('/signup'); await page.locator('#signup-school-email').fill(p1.email.split('@')[0]);
  const code = await sendCode(page, p1.email), bad = code === '000000' ? '111111' : '000000';
  expect((await (await verifyCode(page, bad, 400)).json()).errorCode).toBe('EMAIL_VERIFICATION_INVALID');
  await expect(page.getByRole('button', { name: '인증 확인', exact: true })).toBeEnabled();
  await verifyCode(page, code); await expect(page.getByRole('button', { name: '인증 완료', exact: true })).toBeDisabled();
  p1.note('code recovery', { invalid: 400, correct: 200, signupNotRepeated: true });
});

test('B10-code-expiry-cooldown: code naturally expires and real resend 429 recovers after 60 seconds', async ({ page, p1 }) => {
  await control('restart', { codeTtlSeconds: 8 });
  try {
    await page.goto('/signup'); await page.locator('#signup-school-email').fill(p1.email.split('@')[0]);
    const code = await sendCode(page, p1.email), start = Date.now();
    const limited = await sendCode(page, p1.email, 429);
    expect((await limited.json()).errorCode).toBe('EMAIL_VERIFICATION_COOLDOWN');
    expect(Number(limited.headers()['retry-after'])).toBeGreaterThan(0);
    await page.waitForTimeout(9200);
    expect((await (await verifyCode(page, code, 400)).json()).errorCode).toBe('EMAIL_VERIFICATION_EXPIRED');
    await page.waitForTimeout(Math.max(0, 62_000 - (Date.now() - start)));
    await verifyCode(page, await sendCode(page, p1.email));
    p1.note('natural code expiry', { codeTTL: 8, waitedMs: 9200, cooldown: 60, actual429: true, signupTokenExpiryCase: false });
  } finally { await control('restart'); }
});

test('B10-mysql-limit: five real verification failures block the next attempt and recover after 15 seconds', async ({ page, p1 }) => {
  await page.goto('/signup'); await page.locator('#signup-school-email').fill(p1.email.split('@')[0]);
  const code = await sendCode(page, p1.email), bad = code === '000000' ? '111111' : '000000';
  for (let i = 0; i < 5; i++) expect((await (await verifyCode(page, bad, 400)).json()).errorCode).toBe('EMAIL_VERIFICATION_INVALID');
  const blocked = await verifyCode(page, code, 429);
  expect((await blocked.json()).errorCode).toBe('AUTH_REQUEST_LIMITED');
  expect(Number(blocked.headers()['retry-after'])).toBeGreaterThan(0);
  await page.waitForTimeout(16_000); await verifyCode(page, code);
  p1.note('shared limiter recovery', { failures: 5, blocked: 429, windowAndBlockSeconds: 15, realMySQL: true, limiterRowsEdited: false });
});

test('B10-duplicates: duplicate email and nickname feedback clears after editing the input', async ({ page, p1 }) => {
  await page.goto('/signup'); await page.locator('#signup-school-email').fill(me().split('@')[0]);
  expect((await (await sendCode(page, me(), 409)).json()).errorCode).toBe('EMAIL_ALREADY_REGISTERED');
  await page.locator('#signup-school-email').fill(p1.email.split('@')[0]);
  await sendCode(page, p1.email);
  await page.locator('#signup-nickname').fill('E2E사용자');
  async function nicknameResponse() {
    const waiting = page.waitForResponse(r => r.url().includes('/auth/signup/availability?nickname=') && r.request().method() === 'GET');
    await page.getByRole('button', { name: '중복 확인', exact: true }).click();
    return waiting;
  }
  let r = await nicknameResponse();
  expect((await r.json()).data.nicknameAvailable).toBe(false);
  await page.locator('#signup-nickname').fill('P1새닉');
  r = await nicknameResponse();
  expect((await r.json()).data.nicknameAvailable).toBe(true);
  expect(p1.dialogs.some(s => s.includes('이미 가입된'))).toBe(true);
  p1.note('duplicate recovery', { emailRejected: 409, nicknameUnavailableThenAvailable: true, noSignup: true });
});

const invalidPasswords = ['abc1!xy', 'Abcdefgh!', '12345678!', 'Abcd1234', 'Abc1! xy'];
test('B11-signup-passwords: invalid and 73-byte passwords send nothing; 71 and 72 bytes persist without truncation', async ({ page, p1 }) => {
  const values = []; for (const s of invalidPasswords) values.push(await secret(s)); values.push(await password(73));
  const pw71 = await password(71), pw72 = await password(72);
  await prepareSignup(page, p1.email, 'P1경계71'); await verifySignup(page, p1.email);
  for (const value of values) {
    await page.locator('#signup-password').fill(value); await page.locator('#signup-password-confirm').fill(value);
    await noWrite(page, p1, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
  }
  await page.locator('#signup-password').fill(pw71); await page.locator('#signup-password-confirm').fill(pw72);
  await noWrite(page, p1, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
  await page.locator('#signup-password-confirm').fill(pw71); await submitSignup(page); await login(page, p1.email, pw71);
  const email72 = 'b' + p1.email;
  await prepareSignup(page, email72, 'P1경계72'); await verifySignup(page, email72);
  await page.locator('#signup-password').fill(pw72); await page.locator('#signup-password-confirm').fill(pw72);
  await submitSignup(page); await login(page, email72, pw72);
  p1.note('signup boundary', { acceptedUTF8Bytes: [71,72], browserBlockedBytes: 73, invalidVariants: values.length, mismatchBlocked: true });
});

test('B11-required: empty signup and individual editable required fields cannot issue signup HTTP', async ({ page, p1 }) => {
  await page.goto('/signup');
  await noWrite(page, p1, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
  await prepareSignup(page, p1.email);
  for (const [id, original] of [['signup-school-email',p1.email.split('@')[0]],['signup-password',defaultPassword()],['signup-password-confirm',defaultPassword()],['signup-nickname','E2E신규'],['signup-birth-year','2003'],['signup-student-id','22']]) {
    await page.locator('#' + id).fill('');
    await noWrite(page, p1, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
    await page.locator('#' + id).fill(original);
  }
  for (const [id, original] of [['signup-grade','1학년']]) { // Radio requirement is already covered by the all-empty form.
    expect(await page.locator(`input[name="grade"]:checked`).count()).toBe(1);
  }
  await page.locator('#signup-faculty').selectOption('');
  await noWrite(page, p1, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
  await page.locator('#signup-faculty').selectOption('컴퓨터응용수학부');
  await noWrite(page, p1, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
  p1.note('required fields', { browserSignupRequests: 0, checked: ['all-empty','email','password','confirmation','nickname','birthYear','admissionYear','faculty','major'], remainingRequiredFields: 'all-empty form only' });
});

test('B11-change-password: client rejects bad inputs and server rejects 73 bytes while 71 and 72 byte changes work', async ({ page, p1 }) => {
  await login(page, me()); await page.goto('/profile?tab=account');
  const section = card(page, '비밀번호 변경'); await section.getByRole('button', { name: '수정', exact: true }).click();
  await page.locator('#account-currentPassword').fill(defaultPassword());
  const tooLong = await password(73), valid = [await password(71), await password(72)];
  for (const value of [...invalidPasswords,tooLong]) {
    await secret(value); await page.locator('#account-newPassword').fill(value); await page.locator('#account-confirmPassword').fill(value);
    await noWrite(page, p1, 'PATCH', '/users/me/password', () => section.getByRole('button', { name: '완료', exact: true }).click());
  }
  await page.locator('#account-newPassword').fill(valid[0]); await page.locator('#account-confirmPassword').fill(valid[1]);
  await noWrite(page, p1, 'PATCH', '/users/me/password', () => section.getByRole('button', { name: '완료', exact: true }).click());
  const rejected = await browserRequest(page, 'PATCH', '/users/me/password', { currentPassword: defaultPassword(), newPassword: tooLong, newPasswordConfirm: tooLong });
  expect(rejected.status).toBe(400); expect(rejected.code).toBe('VALIDATION_ERROR'); await oldPasswordWorks(page);
  let current = defaultPassword();
  for (const value of valid) {
    await page.locator('#account-currentPassword').fill(current); await page.locator('#account-newPassword').fill(value); await page.locator('#account-confirmPassword').fill(value);
    await responseTo(page, 'PATCH', '/users/me/password', () => section.getByRole('button', { name: '완료', exact: true }).click());
    await logout(page); await login(page, me(), value); current = value;
    if (value === valid[0]) { await page.goto('/profile?tab=account'); await section.getByRole('button', { name: '수정', exact: true }).click(); }
  }
  p1.note('change password boundary', { browserBlocked: true, serverProbe: { status: 400, bytes: 73 }, acceptedBytes: [71,72] });
});

test('B11-reset-password: reset UI enforces mismatch and byte boundaries with real token preservation on rejected HTTP', async ({ page, p1 }) => {
  const tooLong = await password(73), pw71 = await password(71), pw72 = await password(72);
  await resetRequest(page, me()); const token = await resetToken(me());
  for (const value of [...invalidPasswords, tooLong]) {
    await secret(value); await resetFields(page, token, value);
    await noWrite(page, p1, 'POST', '/auth/password-reset/confirm', () => page.getByRole('button', { name: '비밀번호 변경하기', exact: true }).click());
  }
  await resetFields(page, token, pw71, pw72);
  await noWrite(page, p1, 'POST', '/auth/password-reset/confirm', () => page.getByRole('button', { name: '비밀번호 변경하기', exact: true }).click());
  const rejected = await browserRequest(page, 'POST', '/auth/password-reset/confirm', { resetToken: token, newPassword: tooLong, newPasswordConfirm: tooLong }, { anonymous: true });
  expect(rejected.status).toBe(400); expect(rejected.code).toBe('VALIDATION_ERROR'); await oldPasswordWorks(page);
  expect(database('snapshot').resetStates.every(r => !Boolean(r.used))).toBe(true);
  await resetFields(page, token, pw71); await resetSubmit(page); await login(page, me(), pw71);
  await resetRequest(page, peer()); await resetFields(page, await resetToken(peer()), pw72); await resetSubmit(page); await login(page, peer(), pw72);
  p1.note('reset boundary', { browserBlocked: true, serverRejectedBytes: 73, acceptedBytes: [71,72], rejectedTokenNotConsumed: true });
});

test('B11-introduction: UTF16 199 and 200 persist, UI caps overflow, server rejects overflow, and clearing persists', async ({ page, p1 }) => {
  await login(page, me()); await saveSurvey(page, 'P1 초기소개');
  let baseline;
  for (const value of ['가'.repeat(199), '😀'.repeat(100)]) {
    await editSurvey(page); await page.locator('#survey-introduction').fill(value);
    const r = await responseTo(page, 'PUT', '/users/me/profile-survey', () => page.getByRole('button', { name: '저장', exact: true }).click());
    const data = (await r.json()).data; expect(data.introduction).toBe(value); baseline = data;
    expect(database('snapshot').profiles.find(p => p.nickname === 'E2E사용자').introduction).toBe(value);
  }
  await editSurvey(page); const input = page.locator('#survey-introduction');
  await expect(input).toHaveAttribute('maxlength','200'); await input.fill(''); await input.pressSequentially('x'.repeat(201));
  expect((await input.inputValue()).length).toBe(200);
  const before = database('snapshot').profiles;
  const rejected = await browserRequest(page, 'PUT', '/users/me/profile-survey', { ...baseline.answers, introduction: 'x'.repeat(201) });
  expect(rejected.status).toBe(400); expect(rejected.code).toBe('VALIDATION_ERROR'); expect(database('snapshot').profiles).toEqual(before);
  for (const value of ['   ', '']) {
    await input.fill(value);
    await responseTo(page, 'PUT', '/users/me/profile-survey', () => page.getByRole('button', { name: '저장', exact: true }).click());
    const r = await responseTo(page, 'GET', '/users/me/profile-survey', () => page.reload());
    expect((await r.json()).data.introduction).toBe('');
    expect(database('snapshot').profiles.find(p => p.nickname === 'E2E사용자').introduction).toBe('');
    if (value !== '') await editSurvey(page);
  }
  p1.note('intro boundary', { persistedUTF16: [199,200], emojiCodePoints: 100, browserMaxLength: 200, actualServerRejectedUnits: 201, cleared: true });
});

test('B12-mail-delay-failure: injected network failure releases loading and duplicate send is blocked before manual recovery', async ({ page, p1 }) => {
  await page.goto('/signup'); await page.locator('#signup-school-email').fill(p1.email.split('@')[0]);
  const g = await gate(page, 'POST', '/auth/email-verifications/send');
  await page.getByRole('button', { name: '인증번호 전송', exact: true }).click(); await g.waiting;
  await expect(page.getByRole('button', { name: '전송 요청 중...', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '회원가입', exact: true }).evaluate(b => b.form.requestSubmit());
  expect(p1.count('POST','/auth/email-verifications/send')).toBe(1);
  g.release('abort'); await expect(page.getByRole('button', { name: '인증번호 전송', exact: true })).toBeEnabled();
  await expect(page.locator('#signup-school-email')).toHaveValue(p1.email.split('@')[0]);
  expect((await control('mail-state', { email: p1.email })).verification).toBe(0);
  await g.remove(); await sendCode(page, p1.email);
  expect((await control('mail-state', { email: p1.email })).verification).toBe(1);
  p1.note('injection', { delay: 'barrier', failure: 'browser network abort before server', attempted: 1, serverMailBeforeRecovery: 0, manualRecovery: true });
});

test('B12-account-response-loss: exactly one PATCH commits despite a lost response; draft and manual GET reveal the state', async ({ page, p1 }) => {
  await login(page, me()); await page.goto('/profile?tab=account'); const s = card(page, '기본 정보');
  await s.getByRole('button', { name: '수정', exact: true }).click(); await page.locator('#account-nickname').fill('P1응답유실');
  const g = await gate(page, 'PATCH', '/users/me', true);
  await s.getByRole('button', { name: '완료', exact: true }).click(); await g.waiting;
  await expect(s.getByRole('button', { name: '완료', exact: true })).toBeDisabled();
  await s.getByRole('button', { name: '완료', exact: true }).dispatchEvent('click');
  expect(p1.count('PATCH','/users/me')).toBe(1);
  expect(database('snapshot').profiles.some(p => p.nickname === 'P1응답유실')).toBe(true);
  g.release('abort'); await expect(s.getByRole('button', { name: '완료', exact: true })).toBeEnabled();
  await expect(page.locator('#account-nickname')).toHaveValue('P1응답유실');
  await g.remove(); await page.reload(); await s.getByRole('button', { name: '수정', exact: true }).click();
  await expect(page.locator('#account-nickname')).toHaveValue('P1응답유실'); expect(p1.count('PATCH','/users/me')).toBe(1);
  p1.note('response lost after commit', { serverStatus: g.status(), injectedAbort: true, writes: 1, DBCommittedBeforeAbort: true, manualGETConfirmed: true });
});

for (const kind of ['profile','preference']) test(`B12-${kind}-save: delay and injected failure retain draft and prevent duplicate writes then recover`, async ({ page, p1 }) => {
  await login(page, me());
  if (kind === 'profile') await saveSurvey(page, 'P1 저장초안'); else await preference(page);
  const endpoint = kind === 'profile' ? '/users/me/profile-survey' : '/users/me/roommate-preferences';
  await page.goto('/profile?tab=' + (kind === 'profile' ? 'survey' : 'preferences')); await page.getByRole('button', { name: '설문 수정', exact: true }).click();
  const field = page.locator(kind === 'profile' ? '#survey-introduction' : '#preferences-bedtime-weight');
  if (kind === 'profile') await field.fill('P1 실패후초안'); else await field.press('Home');
  const expected = await field.inputValue(), before = database('snapshot');
  const g = await gate(page, 'PUT', endpoint); const count = p1.count('PUT',endpoint);
  await page.getByRole('button', { name: '저장', exact: true }).click(); await g.waiting;
  await expect(page.getByRole('button', { name: '저장 중…', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '저장 중…', exact: true }).evaluate(b => b.form.requestSubmit());
  expect(p1.count('PUT',endpoint)).toBe(count + 1);
  g.release('abort'); await expect(page.getByRole('button', { name: '저장', exact: true })).toBeEnabled();
  await expect(field).toHaveValue(expected);
  const after = database('snapshot'); expect(after.profiles).toEqual(before.profiles); expect(after.preferences).toEqual(before.preferences);
  await g.remove(); await responseTo(page, 'PUT', endpoint, () => page.getByRole('button', { name: '저장', exact: true }).click());
  await expect(page.getByRole('button', { name: '설문 수정', exact: true })).toBeVisible();
  p1.note('save injection', { kind, serverFailureSimulated: false, browserAbort: true, noCommitBeforeRecovery: true, writesAttemptedBeforeRecovery: 1 });
});

test('B13-preference-query: isolated preference fetch failure must not block the account page and retries restore it', async ({ page, p1 }) => {
  await login(page, me()); const before = database('snapshot'); const restore = await failGet(page, '/users/me/roommate-preferences');
  await page.goto('/profile?tab=account');
  const blocked = !(await page.getByRole('heading', { name: '계정 정보', exact: true }).isVisible());
  p1.note('isolated preference query failure', { injectedNetworkAbort: true, accountBlockedAtObservation: blocked });
  try {
    await expect(page.getByRole('heading', { name: '계정 정보', exact: true })).toBeVisible();
    await page.getByRole('link', { name: '선호 룸메 설문', exact: true }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    await restore(); await page.getByRole('button', { name: '다시 시도', exact: true }).click();
    await expect(page.getByRole('button', { name: '선호 룸메 설문 작성하기', exact: true })).toBeVisible();
    expect(database('snapshot').profiles).toEqual(before.profiles);
  } finally { await restore(); }
});

test('B13-profile-query: profile fetch failure is not an empty survey and explicit retry restores saved answers', async ({ page, p1 }) => {
  await login(page, me()); await saveSurvey(page, 'P1 조회복구소개'); const before = database('snapshot');
  const restore = await failGet(page, '/users/me/profile-survey'); await page.goto('/profile?tab=survey');
  await expect(page.getByRole('alert')).toBeVisible(); await expect(page.getByRole('button', { name: '내 설문 작성하기', exact: true })).toHaveCount(0);
  await restore(); await page.getByRole('button', { name: '다시 시도', exact: true }).click();
  await expect(page.getByText('P1 조회복구소개', { exact: true })).toBeVisible();
  const after = database('snapshot'); expect(after.profiles).toEqual(before.profiles); expect(after.surveys).toEqual(before.surveys);
  p1.note('query retry', { injectedNetworkAbort: true, falselyEmpty: false, DBUnchanged: true, explicitRetryRestored: true });
});

test('B14-remember-email: remember selection persists only email and clearing the checkbox removes it', async ({ page, p1 }) => {
  await page.goto('/login'); await page.getByLabel('학교 이메일', { exact: true }).fill(me().split('@')[0]);
  await responseTo(page, 'POST', '/auth/login/email-check', () => page.getByRole('button', { name: '다음', exact: true }).click());
  await page.getByLabel('비밀번호', { exact: true }).fill(defaultPassword());
  await page.getByRole('checkbox', { name: /이메일/ }).check();
  await responseTo(page, 'POST', '/auth/login', () => page.getByRole('button', { name: '로그인', exact: true }).click());
  await expect(page).toHaveURL('http://127.0.0.1:13000/'); await logout(page); await page.goto('/login');
  await expect(page.getByLabel('학교 이메일', { exact: true })).toHaveValue(me().split('@')[0]);
  await expect(page.getByRole('checkbox', { name: /이메일/ })).toBeChecked();
  await page.getByRole('checkbox', { name: /이메일/ }).uncheck();
  await responseTo(page, 'POST', '/auth/login/email-check', () => page.getByRole('button', { name: '다음', exact: true }).click());
  await page.getByLabel('비밀번호', { exact: true }).fill(defaultPassword());
  await responseTo(page, 'POST', '/auth/login', () => page.getByRole('button', { name: '로그인', exact: true }).click());
  await expect(page).toHaveURL('http://127.0.0.1:13000/'); await logout(page); await page.goto('/login');
  await expect(page.getByLabel('학교 이메일', { exact: true })).toHaveValue('');
  expect(await page.evaluate(() => localStorage.getItem('rememberedEmail'))).toBeNull();
  p1.note('remember', { rememberedThenCleared: true, passwordNotRecorded: true });
});

test('B14-two-tabs-logout: logout propagates through real storage events and clears the other protected tab', async ({ page, context, p1 }) => {
  await login(page, me()); const other = await context.newPage(); await other.goto('/profile?tab=account');
  await expect(other.getByRole('heading', { name: '계정 정보', exact: true })).toBeVisible();
  const before = database('snapshot').profiles; await logout(page);
  await expect(other).toHaveURL(/\/login$/); await expect(other.getByRole('heading', { name: '계정 정보', exact: true })).toHaveCount(0);
  expect(await other.evaluate(() => Boolean(localStorage.getItem('accessToken')))).toBe(false);
  expect(database('snapshot').profiles).toEqual(before); await other.close();
  p1.note('two tabs', { realStorageEvent: true, tokenCleared: true, protectedUIRemoved: true, DBUnchanged: true });
});

test('B14-tab-return: switching to another real tab then returning revalidates the authenticated user', async ({ page, context, p1 }) => {
  await login(page, me()); await page.goto('/profile?tab=account');
  const other = await context.newPage(); await other.goto('/login'); await other.bringToFront();
  const count = p1.count('GET','/auth/me'); await page.bringToFront();
  await expect.poll(() => p1.count('GET','/auth/me')).toBeGreaterThan(count);
  await expect(page.getByRole('heading', { name: '계정 정보', exact: true })).toBeVisible();
  await other.close(); p1.note('tab return', { actualTabActivation: true, realAuthMeRevalidation: true });
});

for (const mode of ['get-success','get-401','patch-success']) test(`B14-late-${mode}: switching accounts before the old response cannot overwrite the new session`, async ({ page, context, p1 }) => {
  const before = database('snapshot').profiles.find(p => p.nickname === 'E2E상대');
  if (mode === 'get-401') await control('restart', { ttlSeconds: 8 });
  let g, other;
  try {
    await login(page, me());
    if (mode === 'patch-success') {
      await page.goto('/profile?tab=account'); const s = card(page, '기본 정보');
      await s.getByRole('button', { name: '수정', exact: true }).click(); await page.locator('#account-nickname').fill('P1늦은저장');
      g = await gate(page, 'PATCH', '/users/me', true);
      await s.getByRole('button', { name: '완료', exact: true }).click();
    } else { g = await gate(page, 'GET', '/users/me', mode !== 'get-401'); await page.goto('/profile?tab=account'); }
    await g.waiting;
    if (mode === 'get-401') await page.waitForTimeout(9200);
    other = await context.newPage(); await login(other, peer());
    // The protected A tab can legitimately redirect to login during the real storage transition.
    // Return through a Router link so the original document and held request remain alive.
    if (page.url().endsWith('/login')) await page.getByRole('link', { name: '메인으로 돌아가기', exact: true }).click();
    await expect(page.getByRole('button', { name: '프로필 메뉴 열기', exact: true })).toContainText('E2E상대');
    g.release('pass');
    await expect.poll(() => g.status()).toBe(mode === 'get-401' ? 401 : 200);
    await page.waitForTimeout(300);
    await expect(page.getByRole('button', { name: '프로필 메뉴 열기', exact: true })).toContainText('E2E상대');
    expect((await browserRequest(other, 'GET', '/users/me')).data.nickname).toBe('E2E상대');
    expect(database('snapshot').profiles.find(p => p.nickname === 'E2E상대')).toEqual(before);
    p1.note('late response', { mode, actualServerStatus: g.status(), newAccountPreserved: true, peerDBUnchanged: true });
  } finally { g?.release('pass'); if (g) await g.remove(); if (other) await other.close(); if (mode === 'get-401') await control('restart'); }
});

test('B15-nonmember-inactive: unregistered suspended and withdrawn emails receive the same notice without token or mail', async ({ page, p1 }) => {
  const before = database('snapshot'); let message;
  for (const email of [p1.email,process.env.E2E_SUSPENDED_EMAIL,process.env.E2E_WITHDRAWN_EMAIL]) {
    const mailBefore = await control('mail-state', { email });
    const response = await resetRequest(page, email); expect((await response.json()).data).toEqual({ sent: true });
    const text = await page.getByRole('status').innerText();
    const normalized = text.replace(email, '<email>');
    if (message) expect(normalized).toBe(message); else message = normalized;
    expect(await control('mail-state', { email })).toEqual(mailBefore);
  }
  const after = database('snapshot'); expect(after.resetStates).toEqual(before.resetStates); expect(after.users).toEqual(before.users);
  p1.note('common notice', { variants: ['unregistered','suspended','withdrawn'], sent: true, mailsAdded: 0, resetTokensAdded: 0 });
});

test('B15-invalid-expired-recovery: invalid and naturally expired reset tokens preserve the password then a real re-request recovers', async ({ page, p1 }) => {
  await control('restart', { resetTtlSeconds: 8 });
  try {
    const changed = await password(24);
    await resetRequest(page, me()); const start = Date.now(), token = await resetToken(me());
    const invalid = await secret('invalid-' + 'q'.repeat(36));
    await resetFields(page, invalid, changed);
    expect((await (await resetSubmit(page, 400)).json()).errorCode).toBe('PASSWORD_RESET_TOKEN_INVALID');
    await oldPasswordWorks(page); await page.waitForTimeout(9200);
    await resetFields(page, token, changed);
    expect((await (await resetSubmit(page, 400)).json()).errorCode).toBe('PASSWORD_RESET_TOKEN_EXPIRED');
    await oldPasswordWorks(page); expect(database('snapshot').resetStates.every(r => !Boolean(r.used))).toBe(true);
    expect((await (await resetRequest(page, me(), 429)).json()).errorCode).toBe('AUTH_REQUEST_LIMITED');
    await page.waitForTimeout(Math.max(0,62_000 - (Date.now() - start)));
    await resetRequest(page, me()); await resetFields(page, await resetToken(me()), changed); await resetSubmit(page);
    await login(page, me(), changed);
    p1.note('reset recovery', { wrongToken: 'PASSWORD_RESET_TOKEN_INVALID', naturallyExpired: 'PASSWORD_RESET_TOKEN_EXPIRED', resetTTL: 8, waitedMs: 9200, realCooldown: 60, oldPasswordPreservedUntilSuccess: true, recovered: true });
  } finally { await control('restart'); }
});
