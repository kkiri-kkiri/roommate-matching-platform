const { spawnSync } = require('node:child_process');
const { readFileSync, mkdirSync, writeFileSync, appendFileSync } = require('node:fs');
const path = require('node:path');

function database(operation) {
  if (process.env.E2E_MANAGED !== '1' || !process.env.E2E_DATABASE?.match(/^kkiri_e2e_[a-f0-9]{12}$/)) {
    throw new Error('E2E requires its managed stack. Run npm run test:e2e.');
  }
  const root = path.resolve(__dirname, '../../..');
  const classpath = readFileSync(path.join(root, 'backend/build/e2e/classpath.txt'), 'utf8');
  const result = spawnSync('java', ['-cp', classpath, 'e2e.E2eDatabase', operation], {
    env: process.env, encoding: 'utf8', timeout: 60_000, windowsHide: true,
  });
  if (result.status !== 0) {
    const directory = path.join(root, 'frontend/.cache/e2e', process.env.E2E_DATABASE.slice('kkiri_e2e_'.length));
    mkdirSync(directory, { recursive: true });
    let diagnostic = (result.stdout || '') + '\n' + (result.stderr || '');
    for (const key of ['E2E_DB_PASSWORD', 'E2E_LOGIN_PASSWORD', 'E2E_JWT_SECRET', 'MYSQL_ROOT_PASSWORD']) {
      if (process.env[key]) diagnostic = diagnostic.split(process.env[key]).join('[REDACTED]');
    }
    const file = path.join(directory, `database-${operation}.log`);
    writeFileSync(file, diagnostic);
    throw new Error(`E2E database ${operation} failed (${result.error?.code || result.status}). Diagnostic: ${path.relative(root, file)}`);
  }
  if (!result.stdout.includes(`E2E_DATABASE_${operation.toUpperCase()}_OK`)) {
    throw new Error(`E2E database ${operation} did not confirm completion.`);
  }
  if (operation === 'snapshot') {
    const line = result.stdout.split(/\r?\n/).find(line => line.startsWith('E2E_SNAPSHOT '));
    if (!line) throw new Error('Missing read-only business-data snapshot');
    const snapshot = JSON.parse(line.slice('E2E_SNAPSHOT '.length));
    record({ operation, snapshot });
    return snapshot;
  }
  record({ operation, completed: true, allTablesEmpty: operation === 'clean' });
}
function record(data) {
  const directory = process.env.E2E_EVIDENCE_DIR;
  const expected = path.resolve(__dirname, '../../../backend/build/full-service-e2e');
  if (!directory || path.dirname(directory) !== expected || !/^[a-f0-9]{12}$/.test(path.basename(directory))) throw new Error('Unsafe E2E evidence path');
  const id = (process.env.E2E_CURRENT_TEST_ID || 'runner').replace(/[^a-z0-9-]/gi, '_');
  appendFileSync(path.join(directory, `database-${id}.jsonl`), JSON.stringify({ time: new Date().toISOString(), ...data }) + '\n');
}
module.exports = { database };
