const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const { readFileSync, mkdirSync, writeFileSync } = require('node:fs');
const path = require('node:path');
const net = require('node:net');

const frontend = path.resolve(__dirname, '..');
const backend = path.resolve(frontend, '../backend');
const ports = [13000, 18080, 13316];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));

function safeEnvironment(source) {
  // Ambient DB, JWT, SMTP, AI, Spring, Java and Node options cannot select another environment.
  const allowed = /^(PATH|PATHEXT|SYSTEMROOT|WINDIR|COMSPEC|TEMP|TMP|TMPDIR|JAVA_HOME|USERPROFILE|HOME|APPDATA|LOCALAPPDATA|PROGRAMDATA|PROGRAMFILES|PROGRAMFILES\(X86\)|DOCKER_CONFIG|CI|TERM|LANG|LC_ALL)$/i;
  return Object.fromEntries(Object.entries(source).filter(([key]) => allowed.test(key)));
}
async function assertFree(port) {
  await new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', () => reject(new Error(`E2E port ${port} is occupied; refusing to reuse or stop an existing server.`)));
    server.listen(port, '127.0.0.1', () => server.close(resolve));
  });
}

async function main() {
  const id = randomBytes(6).toString('hex');
  const name = `kkiri-e2e-${id}`;
  const env = {
    ...safeEnvironment(process.env), E2E_MANAGED: '1', E2E_DATABASE: `kkiri_e2e_${id}`,
    E2E_DB_USER: `e2e_${id}`, E2E_DB_PASSWORD: randomBytes(24).toString('hex'),
    E2E_JWT_SECRET: randomBytes(48).toString('base64'),
    E2E_LOGIN_EMAIL: `e2e${id}@hknu.ac.kr`, E2E_LOGIN_PASSWORD: `E2e!${randomBytes(18).toString('hex')}`,
    E2E_IMAGE_DIRECTORY: path.join(backend, 'build/e2e', id, 'uploads'),
  };
  Object.assign(env, { MYSQL_DATABASE: env.E2E_DATABASE, MYSQL_USER: env.E2E_DB_USER,
    MYSQL_PASSWORD: env.E2E_DB_PASSWORD, MYSQL_ROOT_PASSWORD: randomBytes(24).toString('hex') });
  const children = new Set();
  let ownsContainer = false;
  let stopping = false;
  let api;
  let react;
  function failureLog(child, label) {
    const directory = path.join(frontend, '.cache/e2e', id);
    mkdirSync(directory, { recursive: true });
    let output = child.output + '\n' + child.errors;
    for (const key of ['E2E_DB_PASSWORD', 'E2E_LOGIN_PASSWORD', 'E2E_JWT_SECRET', 'MYSQL_ROOT_PASSWORD']) {
      output = output.split(env[key]).join('[REDACTED]');
    }
    const file = path.join(directory, label.replace(/[^a-z0-9]+/gi, '-') + '.log');
    writeFileSync(file, output);
    return path.relative(frontend, file);
  }
  function start(command, args, cwd = frontend, extraEnv = {}) {
    const child = spawn(command, args, { cwd, env: { ...env, ...extraEnv }, windowsHide: true,
      detached: process.platform !== 'win32', stdio: ['pipe', 'pipe', 'pipe'] });
    child.output = ''; child.errors = '';
    child.stdout.on('data', data => { child.output = (child.output + data).slice(-100_000); });
    child.stderr.on('data', data => { child.errors = (child.errors + data).slice(-100_000); });
    child.on('error', error => { child.spawnError = error; });
    children.add(child);
    child.once('close', () => children.delete(child));
    return child;
  }
  async function complete(child, label, timeout = 120_000) {
    const result = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${label} timed out. Diagnostic: ${failureLog(child, label)}`)), timeout);
      child.once('error', error => { clearTimeout(timer); reject(error); });
      child.once('close', code => { clearTimeout(timer); resolve(code); });
    });
    if (result !== 0) throw new Error(`${label} failed (exit ${result}). Diagnostic: ${failureLog(child, label)}`);
    return child.output.trim();
  }
  const docker = (args, label, timeout) => complete(start('docker', args), label, timeout);
  async function stop(child) {
    if (!child.pid || child.exitCode !== null) return;
    const closed = new Promise(resolve => child.once('close', resolve));
    if (process.platform === 'win32') {
      await new Promise(resolve => {
        const killer = spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
        killer.once('error', resolve); killer.once('close', resolve);
      });
    } else { try { process.kill(-child.pid, 'SIGTERM'); } catch {} }
    await Promise.race([closed, pause(5000)]);
    if (child.exitCode === null && process.platform !== 'win32') {
      try { process.kill(-child.pid, 'SIGKILL'); } catch {}
    }
  }
  async function cleanup() {
    if (stopping) return;
    stopping = true;
    for (const child of [...children].reverse()) await stop(child);
    if (ownsContainer) {
      const container = await docker(['ps', '-aq', '--filter', `name=^/${name}$`, '--filter', `label=kkiri.e2e.owner=${id}`], 'Find owned E2E container');
      if (container) {
        const owner = await docker(['inspect', '--format', '{{index .Config.Labels "kkiri.e2e.owner"}}', container], 'Verify container ownership');
        if (owner !== id) throw new Error('Refusing to remove a container with a different owner.');
        await docker(['rm', '--force', '--volumes', container], 'Remove owned E2E MySQL');
        console.log('[e2e] Owned MySQL container and disposable data removed.');
      }
    }
  }
  const interrupted = () => { void cleanup().then(() => process.exit(130), error => { console.error(error.message); process.exit(1); }); };
  process.once('SIGINT', interrupted); process.once('SIGTERM', interrupted);
  try {
    for (const port of ports) await assertFree(port);
    const endpoint = await docker(['context', 'inspect', '--format', '{{.Endpoints.docker.Host}}'], 'Read Docker endpoint');
    if (!/^(npipe:\/\/|unix:\/\/)/.test(endpoint)) throw new Error('E2E requires a local Docker engine.');
    const engine = await docker(['info', '--format', '{{.OSType}}'], 'Docker engine availability');
    if (engine !== 'linux') throw new Error('E2E requires Docker Linux containers. Start Docker Desktop.');
    console.log('[e2e] Starting isolated MySQL 8.0.46 on 127.0.0.1:13316.');
    // Fixed name is unique per run; no existing container or volume is reused.
    ownsContainer = true;
    await docker(['create', '--name', name, '--hostname', 'kkiri-e2e', '--label', `kkiri.e2e.owner=${id}`,
      '--publish', '127.0.0.1:13316:3306', '--tmpfs', '/var/lib/mysql:rw,size=1073741824',
      '--env', 'MYSQL_DATABASE', '--env', 'MYSQL_USER', '--env', 'MYSQL_PASSWORD', '--env', 'MYSQL_ROOT_PASSWORD',
      'mysql:8.0.46-oraclelinux9', '--lower-case-table-names=1', '--character-set-server=utf8mb4',
      '--collation-server=utf8mb4_0900_ai_ci'], 'Create E2E MySQL', 300_000);
    await docker(['start', name], 'Start E2E MySQL');
    const deadline = Date.now() + 120_000;
    while (true) {
      try {
        env.E2E_DB_UUID = await docker(['exec', name, 'sh', '-c',
          'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT @@server_uuid"'], 'MySQL readiness');
        if (/^[0-9a-f-]{36}$/.test(env.E2E_DB_UUID)) break;
      } catch { /* MySQL initializes in the fresh tmpfs. */ }
      if (Date.now() > deadline) throw new Error('E2E MySQL did not become ready within 120 seconds.');
      await pause(1000);
    }
    console.log(`[e2e] Target: ${env.E2E_DATABASE}; account: ${env.E2E_DB_USER}; server UUID verified before DDL.`);
    const gradle = process.platform === 'win32'
      ? start('cmd.exe', ['/d', '/s', '/c', 'gradlew.bat --no-daemon prepareE2e'], backend)
      : start('./gradlew', ['--no-daemon', 'prepareE2e'], backend);
    await complete(gradle, 'Compile E2E-only backend source set', 300_000);
    const classpath = readFileSync(path.join(backend, 'build/e2e/classpath.txt'), 'utf8');
    await complete(start('java', ['-cp', classpath, 'e2e.E2eDatabase', 'prepare']), 'Guard and prepare E2E schema');
    mkdirSync(env.E2E_IMAGE_DIRECTORY, { recursive: true });
    api = start('java', ['-cp', classpath, 'e2e.E2eServer']);
    async function ready(child, label, condition, timeout = 120_000) {
      const deadline = Date.now() + timeout;
      while (Date.now() < deadline) {
        if (child.exitCode !== null || child.spawnError) throw new Error(`${label} exited before readiness. Diagnostic: ${failureLog(child, label)}`);
        if (await condition()) return;
        await pause(500);
      }
      throw new Error(`${label} readiness timed out. Diagnostic: ${failureLog(child, label)}`);
    }
    await ready(api, 'E2E Spring Boot', async () => api.output.includes('E2E_BACKEND_READY'));
    react = start(process.execPath, [require.resolve('react-scripts/scripts/start.js')], frontend, {
      HOST: '127.0.0.1', PORT: '13000', BROWSER: 'none', CI: 'true',
      REACT_APP_API_BASE_URL: 'http://127.0.0.1:18080/api',
      WDS_SOCKET_HOST: '127.0.0.1', WDS_SOCKET_PORT: '13000',
    });
    await ready(react, 'E2E React', async () => {
      if (api.exitCode !== null) throw new Error(`E2E Spring Boot stopped during React startup. Diagnostic: ${failureLog(api, 'E2E-Spring-Boot')}`);
      try { return (await fetch('http://127.0.0.1:13000/login', { signal: AbortSignal.timeout(2000) })).ok; }
      catch { return false; }
    });
    console.log('[e2e] React and Spring Boot ready; scheduling, production seeds, SMTP and AI network transport disabled.');
    const playwright = start(process.execPath, [require.resolve('@playwright/test/cli'), 'test', ...process.argv.slice(2)]);
    playwright.stdout.on('data', data => process.stdout.write(data));
    playwright.stderr.on('data', data => process.stderr.write(data));
    // Do not allow a crashed server to leave the test waiting until its own timeout.
    const crash = () => { if (!stopping) { console.error('[e2e] A managed server stopped unexpectedly.'); void stop(playwright); } };
    api.once('close', crash); react.once('close', crash);
    await complete(playwright, 'Playwright', 600_000);
    api.removeListener('close', crash); react.removeListener('close', crash);
    await complete(start('java', ['-cp', classpath, 'e2e.E2eDatabase', 'clean']), 'Final E2E data cleanup');
    console.log('[e2e] All fixture tables verified empty.');
    api.stdin.end();
    await Promise.race([new Promise(resolve => api.once('close', resolve)), pause(10_000)]);
  } catch (error) {
    for (const [child, label] of [[api, 'E2E-Spring-Boot'], [react, 'E2E-React']]) {
      if (child) console.error(`[e2e] Server diagnostic: ${failureLog(child, label)}`);
    }
    throw error;
  } finally {
    await cleanup();
    process.removeListener('SIGINT', interrupted); process.removeListener('SIGTERM', interrupted);
  }
}
if (require.main === module) main().catch(error => { console.error(`[e2e] ${error.message}`); process.exitCode = 1; });
module.exports = { assertFree, safeEnvironment };
