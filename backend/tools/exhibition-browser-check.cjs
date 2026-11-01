// Real existing frontend, isolated browser context, no token injection or request mocking.
const { chromium, expect } = require('../../frontend/node_modules/@playwright/test');
const fs = require('node:fs');
const path = require('node:path');
const { catalog, journal } = require('./exhibition-copy.cjs');
if (process.env.EXHIBITION_ENVIRONMENT !== 'local-exhibition' ||
    process.env.EXHIBITION_API_CONFIRMED_SCHEMA !== 'kkirikkiri_exhibition_20261031' ||
    !process.env.EXHIBITION_DEMO_PASSWORD) throw new Error('Use the guarded exhibition-local.ps1 BrowserCheck command');
const output = path.join(__dirname, '../build/exhibition-live/browser');
fs.mkdirSync(output, { recursive: true });
(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    const page = await context.newPage();
    const failures = [];
    page.on('pageerror', () => failures.push('unhandled browser error'));
    page.on('dialog', dialog => dialog.dismiss());
    await context.route('**/*', route => {
      const url = new URL(route.request().url());
      if (['localhost', '127.0.0.1'].includes(url.hostname) && ['3000', '8080'].includes(url.port)) return route.continue();
      return route.abort();
    });
    await page.goto('http://localhost:3000/login');
    await page.locator('#login-email').fill('seed_user01');
    await page.getByRole('button', { name: '다음', exact: true }).click();
    await page.locator('#login-password').waitFor({ state: 'visible', timeout: 30000 });
    await page.locator('#login-password').fill(process.env.EXHIBITION_DEMO_PASSWORD);
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await page.waitForURL('http://localhost:3000/');
    await expect(page.getByText(catalog.students[0].nickname).first()).toBeVisible({ timeout: 30000 });
    await page.goto('http://localhost:3000/profile');
    await expect(page.getByText(catalog.students[0].nickname).first()).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: path.join(output, 'profile.png'), fullPage: true });
    await page.goto('http://localhost:3000/roommates');
    await expect(page.getByText(catalog.students[1].nickname).first()).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: path.join(output, 'roommates.png'), fullPage: true });
    const postResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/posts/1' && response.status() === 200);
    await page.goto('http://localhost:3000/community/posts/1');
    const post = (await (await postResponse).json()).data;
    await expect(page.getByText(post.title, { exact: true }).first()).toBeVisible();
    await expect(page.getByText('익명1').first()).toBeVisible();
    await page.screenshot({ path: path.join(output, 'community.png'), fullPage: true });
    for (const category of ['자유게시판', '나눔게시판', '택배게시판', '민원게시판']) {
      const expected = catalog.posts.find(p => p.category === category && p.slot === 1);
      const postId = journal.owned[`post/${category}/1`].id;
      await page.goto(`http://localhost:3000/community/posts/${postId}`);
      await expect(page.getByText(expected.title, { exact: true }).first()).toBeVisible();
      await expect(page.getByText(expected.content, { exact: true }).first()).toBeVisible();
      await expect(page.getByText(expected.comment, { exact: true }).first()).toBeVisible();
      await expect(page.getByText(expected.reply, { exact: true }).first()).toBeVisible();
    }
    const roomId = journal.owned['chat/morning'].id;
    await page.goto(`http://localhost:3000/chat?room=${roomId}`);
    const conversation = page.getByRole('region', { name: `${catalog.students[1].nickname}님과의 대화`, exact: true });
    for (const text of catalog.conversations.find(c => c.key === 'chat/morning').messages)
      await expect(conversation.getByText(text, { exact: true })).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: path.join(output, 'chat.png'), fullPage: true });
    await page.goto('http://localhost:3000/calendar');
    await expect(page.locator('.fc')).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: path.join(output, 'calendar.png'), fullPage: true });
    expect(failures).toEqual([]);
    const result = { success: true, frontend: 'http://localhost:3000', schema: process.env.EXHIBITION_API_CONFIRMED_SCHEMA,
      verified: ['real UI email/password login', 'fictional nickname/profile', 'roommate profiles', 'exhibition post/anonymous comments', 'existing chat messages', 'calendar render'],
      screenshots: ['profile.png', 'roommates.png', 'community.png', 'chat.png', 'calendar.png'] };
    fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify(result, null, 2) + '\n');
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
    await context.close();
  } finally { await browser.close(); }
})().catch(error => {
  const message = String(error.message).split(process.env.EXHIBITION_DEMO_PASSWORD).join('<redacted>');
  process.stderr.write(`Exhibition browser check stopped: ${message}\n`); process.exitCode = 1;
});
