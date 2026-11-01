// Local, real HTTP verification. Never persists credentials, bearer tokens or raw responses.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { catalog, journal } = require('./exhibition-copy.cjs');
if (process.env.EXHIBITION_ENVIRONMENT !== 'local-exhibition' ||
    process.env.EXHIBITION_API_CONFIRMED_SCHEMA !== 'kkirikkiri_exhibition_20261031' ||
    !process.env.EXHIBITION_DEMO_PASSWORD) throw new Error('Run through exhibition-local.ps1 ApiCheck after its server ownership check');
const base = 'http://127.0.0.1:8080/api';
let requests = 0;
const tokens = new Map();
const rooms = new Map();
const messages = new Map();
const checks = [];
async function request(route, token, payload, expected = 200) {
  const response = await fetch(base + route, {
    method: payload ? 'POST' : 'GET',
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(payload ? { 'Content-Type': 'application/json' } : {}) },
    body: payload ? JSON.stringify(payload) : undefined,
    signal: AbortSignal.timeout(30000),
  });
  requests++;
  assert.equal(response.status, expected, `HTTP status ${route}`);
  const envelope = await response.json();
  if (expected === 200) assert.equal(envelope.success, true, `API envelope ${route}`);
  return envelope.data;
}
async function login(email) {
  const result = await request('/auth/login', null, { email, password: process.env.EXHIBITION_DEMO_PASSWORD });
  assert.equal(result.user.schoolEmail, email, 'Actual login identity');
  assert.ok(result.accessToken, 'Login must issue its own token');
  return result.accessToken;
}
(async () => {
  const names = new Set(), dorms = new Set(), majors = new Set(), sleepTimes = new Set();
  let ownCalendarEvents = 0;
  for (let slot = 1; slot <= 30; slot++) {
    const email = `seed_user${String(slot).padStart(2, '0')}@hknu.ac.kr`;
    const token = await login(email); tokens.set(slot, token);
    const user = await request('/users/me', token);
    assert.equal(user.schoolEmail, email);
    assert.ok(!user.nickname.startsWith('테스트룸메'));
    names.add(user.nickname); dorms.add(user.dormitory); majors.add(user.major);
    assert.equal(user.nickname, catalog.students.find(student => student.key === `user/${slot}`).nickname);
    const survey = await request('/users/me/profile-survey', token);
    assert.equal(survey.completed, true); sleepTimes.add(survey.answers.sleepTime);
    const preferences = await request('/users/me/roommate-preferences', token);
    assert.equal(preferences.completed, true);
    assert.equal(Object.keys(preferences.answers.weights).length, 18);
    const memberRooms = await request('/chat/rooms?size=100', token);
    for (const room of memberRooms.content) rooms.set(room.id, { room, token });
    if ([1, 7, 19].includes(slot)) {
      const events = await request('/calendar/events?from=2026-10-01&to=2026-12-31', token);
      assert.equal(events.length, 4); ownCalendarEvents += events.length;
    }
  }
  assert.equal(names.size, 30); assert.equal(dorms.size, 4); assert.equal(majors.size, 6);
  assert.ok(sleepTimes.size >= 4);
  checks.push('30 real password logins and own profiles, completed surveys/preferences, 18 weights each');
  const token = tokens.get(1);
  const posts = await request('/posts?size=100', token);
  assert.equal(posts.totalElements, 80);
  const categories = {};
  for (const post of posts.content) {
    assert.ok(!post.title.startsWith('[SEED]'));
    categories[post.category] = (categories[post.category] || 0) + 1;
  }
  assert.equal(Object.keys(categories).length, 4);
  assert.ok(Object.values(categories).every(count => count === 20));
  for (const expected of catalog.posts) {
    const owned = journal.owned[`post/${expected.category}/${expected.slot}`];
    const actual = await request(`/posts/${owned.id}`, token);
    assert.equal(actual.title, expected.title); assert.equal(actual.content, expected.content);
    assert.equal(actual.comments.length, 2);
    const comment = actual.comments.find(c => c.id === owned.children.comment);
    const reply = actual.comments.find(c => c.id === owned.children.reply);
    assert.equal(comment.content, expected.comment); assert.equal(comment.parentCommentId, null);
    assert.equal(reply.content, expected.reply); assert.equal(reply.parentCommentId, comment.id);
    assert.equal(actual.likes, 1 + expected.slot % 4); assert.equal(comment.likes, 1);
  }
  const post = await request('/posts/1', token);
  assert.equal(post.comments.length, 2);
  assert.ok(post.comments.some(comment => comment.parentCommentId !== null));
  assert.ok(post.comments.every(comment => comment.anonymous && comment.author.id === null && comment.anonymousNumber >= 1));
  checks.push('80 exhibition posts, 4 boards, comments/replies and anonymous author privacy');
  const matches = await request('/roommates?size=100', token);
  assert.ok(matches.content.some(user => user.nickname === catalog.students[1].nickname));
  assert.ok(matches.content.every(user => Number.isInteger(user.matchScore) && user.matchScore >= 0 && user.matchScore <= 100));
  assert.ok(new Set(matches.content.map(user => user.matchScore)).size > 1);
  const recommended = await request('/roommates/recommended?size=100', token);
  assert.ok(recommended.content.length > 0);
  checks.push('Roommate list/recommendations return varied scores calculated by actual services');
  let read = 0, unread = 0, anonymousRoom = false;
  assert.equal(rooms.size, 8);
  for (const { room, token: memberToken } of rooms.values()) {
    const result = await request(`/chat/rooms/${room.id}/messages?size=100`, memberToken);
    assert.equal(result.content.length, result.totalElements);
    const expected = catalog.conversations.find(c => journal.owned[c.key].id === room.id);
    assert.ok(expected);
    assert.deepEqual([...result.content].sort((a, b) => a.id - b.id).map(m => m.text), expected.messages);
    for (const message of result.content) {
      assert.ok(message.text.length > 0);
      messages.set(message.id, message);
      if (message.read) read++; else unread++;
    }
    if (room.counterpart.nickname === '익명') {
      anonymousRoom = true; assert.equal(room.counterpart.id, null);
      assert.ok(result.content.some(message => message.senderId === null));
    }
  }
  assert.equal(messages.size, 33); assert.ok(read > 0 && unread > 0); assert.ok(anonymousRoom);
  assert.equal(ownCalendarEvents, 12);
  checks.push('8 accessible participant chats, 33 messages, read/unread and anonymous counterpart privacy; 12 own events');
  const admin = await login('exhibition_admin@hknu.ac.kr');
  const reports = await request('/admin/reports?size=100', admin);
  assert.equal(reports.totalElements, 3);
  assert.deepEqual([...new Set(reports.content.map(report => report.status))].sort(), ['COMPLETED', 'PENDING', 'REJECTED']);
  await request('/admin/reports?size=100', token, null, 403);
  const anotherRoom = [...rooms.values()].find(value => value.room.counterpart.nickname !== '익명');
  await request(`/chat/rooms/${anotherRoom.room.id}/messages`, tokens.get(30), null, 403);
  checks.push('3 admin report states; normal user admin access and outsider chat access denied');
  const summary = {
    schema: process.env.EXHIBITION_API_CONFIRMED_SCHEMA, requests, success: true, checks,
    seedLogins: 30, nicknameExamples: catalog.students.slice(0, 2).map(s => s.nickname), dormitories: dorms.size, majors: majors.size,
    exactCatalogVerification: { nicknames: 30, posts: 80, comments: 160, messages: 33 },
    posts: posts.totalElements, chats: rooms.size, messages: messages.size, calendarEvents: ownCalendarEvents,
    reports: reports.totalElements, firstUserRecommendations: recommended.content.length,
    firstUserScoreRange: [Math.min(...matches.content.map(user => user.matchScore)), Math.max(...matches.content.map(user => user.matchScore))],
  };
  const output = JSON.stringify(summary, null, 2);
  fs.writeFileSync(path.join(__dirname, '../build/exhibition-live/api-verification.json'), output + '\n');
  process.stdout.write(output + '\n');
})().catch(error => { process.stderr.write(`Exhibition API check stopped: ${error.message}\n`); process.exitCode = 1; });
