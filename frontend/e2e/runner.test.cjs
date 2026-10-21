const { test } = require('node:test');
const assert = require('node:assert/strict');
const net = require('node:net');
const { assertFree, safeEnvironment, redactOutput, sanitize } = require('./run.cjs');
const { database } = require('./lib/database.cjs');

test('ambient credentials and startup overrides cannot enter E2E child processes', () => {
  assert.deepEqual(safeEnvironment({ PATH: '/bin', JAVA_HOME: '/java', CI: 'true',
    DB_URL: 'production', SMTP_PASSWORD: 'secret', AI_BASE_URL: 'external',
    SPRING_PROFILES_ACTIVE: 'prod', JAVA_TOOL_OPTIONS: '-Dspring.profiles.active=prod',
    NODE_OPTIONS: '--require=external', DOCKER_HOST: 'tcp://external:2375',
    E2E_DATABASE: 'kkiri_kkiri', REACT_APP_API_BASE_URL: 'external' }),
  { PATH: '/bin', JAVA_HOME: '/java', CI: 'true' });
});
test('an occupied port is rejected without stopping its owner', async () => {
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    await assert.rejects(assertFree(server.address().port), /occupied/);
    assert.equal(server.listening, true);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
test('fixture operations cannot run outside the managed stack', () => {
  assert.throws(() => database('reset'), /managed stack/);
});
test('diagnostics omit known credentials, private mail IPC and generated Spring passwords', () => {
  const output = redactOutput('failure secret-value\nE2E_PRIVATE request 123456\nUsing generated security password: generated-value', ['secret-value']);
  for (const secret of ['secret-value', '123456', 'generated-value']) assert.equal(output.includes(secret), false);
});
test('structured evidence preserves numeric IDs and timings while removing secret strings', () => {
  const original = { id: 123456, duration: 234567, rows: [{ note: 'token test-secret / 123456' }] };
  assert.deepEqual(sanitize(original, ['test-secret']), { id: 123456, duration: 234567,
    rows: [{ note: 'token [REDACTED] / [REDACTED SIX-DIGIT VALUE]' }] });
  assert.equal(original.rows[0].note.includes('test-secret'), true);
});
