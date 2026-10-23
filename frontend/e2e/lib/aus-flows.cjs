const { expect } = require('@playwright/test');
const { responseTo, control } = require('./flows.cjs');

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
  const data = (await result.json()).data;
  expect(data.introduction).toBe(introduction);
  await expect(page.getByText(introduction, { exact: true })).toBeVisible();
  return data;
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
  const response = await responseTo(page, 'PUT', '/users/me/roommate-preferences', () => page.getByRole('button', { name: '저장', exact: true }).click());
  await expect(page.getByRole('button', { name: '설문 수정', exact: true })).toBeVisible();
  return response.request().postDataJSON();
}


async function prepareSignup(page, email, nickname = 'E2E신규') {
    await page.goto('/signup');
    await page.locator('#signup-school-email').fill(email.split('@')[0]);
    await page.locator('#signup-password').fill(process.env.E2E_LOGIN_PASSWORD);
    await page.locator('#signup-password-confirm').fill(process.env.E2E_LOGIN_PASSWORD);
    await page.locator('#signup-nickname').fill(nickname);
    await responseTo(page, 'GET', '/auth/signup/availability', () => page.getByRole('button', { name: '중복 확인', exact: true }).click());
    await page.locator('label[for="signup-gender-0"]').click();
    await page.locator('label[for="signup-dormitory-0"]').click();
    await page.locator('#signup-faculty').selectOption('HK자율전공학부');
    await page.locator('#signup-birth-year').fill('2003');
    await page.locator('#signup-student-id').fill('22');
    await page.locator('label[for="signup-grade-0"]').click();
    for (const label of ['에너지 방향 I', '인식 방식 N', '판단 방식 T', '생활 양식 J']) await page.getByRole('button', { name: label, exact: true }).click();
}
async function verifySignup(page, email) {
    await responseTo(page, 'POST', '/auth/email-verifications/send', () => page.getByRole('button', { name: '인증번호 전송', exact: true }).click());
    const { code } = await control('mail', { email });
    if (!/^\d{6}$/.test(code)) throw new Error('Memory SMTP did not capture a verification code');
    await page.locator('#signup-verification-code').fill(code);
    await responseTo(page, 'POST', '/auth/email-verifications/verify', () => page.getByRole('button', { name: '인증 확인', exact: true }).click());
}
async function signup(page, email, nickname) {
    await prepareSignup(page, email, nickname);
    await verifySignup(page, email);
    await responseTo(page, 'POST', '/auth/signup', () => page.getByRole('button', { name: '회원가입', exact: true }).click());
}
module.exports = { saveSurvey, preference, prepareSignup, verifySignup, signup };
