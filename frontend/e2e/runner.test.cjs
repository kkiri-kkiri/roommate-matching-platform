const { test } = require('node:test');
const assert = require('node:assert/strict');
const net = require('node:net');
const { assertFree, safeEnvironment } = require('./run.cjs');
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
