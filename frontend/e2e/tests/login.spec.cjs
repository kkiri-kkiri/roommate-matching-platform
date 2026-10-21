const { test, expect, database } = require('../fixtures.cjs');

test('React → Spring Boot → MySQL: real login and JWT session restoration', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: '로그인', exact: true })).toBeVisible();
  await page.getByLabel('학교 이메일', { exact: true }).fill(process.env.E2E_LOGIN_EMAIL.split('@')[0]);
  const checked = page.waitForResponse(response =>
    response.url() === 'http://127.0.0.1:18080/api/auth/login/email-check' && response.request().method() === 'POST');
  await page.getByRole('button', { name: '다음', exact: true }).click();
  const emailResponse = await checked;
  expect(emailResponse.status()).toBe(200);
  expect((await emailResponse.json()).data.registered).toBe(true);

  await page.getByLabel('비밀번호', { exact: true }).fill(process.env.E2E_LOGIN_PASSWORD);
  const loggedIn = page.waitForResponse(response =>
    response.url() === 'http://127.0.0.1:18080/api/auth/login' && response.request().method() === 'POST');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  const loginResponse = await loggedIn;
  expect(loginResponse.status()).toBe(200);
  // Never attach the login payload: it contains an access token.
  expect((await loginResponse.json()).data.user.schoolEmail).toBe(process.env.E2E_LOGIN_EMAIL);
  await expect(page).toHaveURL('http://127.0.0.1:13000/');

  const restored = page.waitForResponse(response =>
    response.url() === 'http://127.0.0.1:18080/api/auth/me' && response.request().method() === 'GET');
  await page.reload();
  const sessionResponse = await restored;
  expect(sessionResponse.status()).toBe(200);
  expect((await sessionResponse.json()).data.schoolEmail).toBe(process.env.E2E_LOGIN_EMAIL);
  await expect(page).toHaveURL('http://127.0.0.1:13000/');
  await expect(page.getByRole('button', { name: '프로필 메뉴 열기', exact: true })).toContainText('E2E사용자');
  database('verify');
});
