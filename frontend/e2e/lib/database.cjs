const { spawnSync } = require('node:child_process');
const { readFileSync, mkdirSync, writeFileSync } = require('node:fs');
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
}
module.exports = { database };
