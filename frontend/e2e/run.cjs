const { spawn, spawnSync } = require('node:child_process');
const { randomBytes, createHash } = require('node:crypto');
const { readFileSync, mkdirSync, writeFileSync, readdirSync, lstatSync, existsSync } = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const http = require('node:http');

const frontend = path.resolve(__dirname, '..');
const backend = path.resolve(frontend, '../backend');
const ports = [13000, 18080, 13316];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function redactOutput(output, secrets = []) {
  for (const secret of secrets.filter(Boolean)) output = output.split(secret).join('[REDACTED]');
  return output.replace(/^E2E_PRIVATE .*$/gm, '[PRIVATE IPC OMITTED]')
    .replace(/E2e![a-f0-9]{36}/g, '[REDACTED]')
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[REDACTED TOKEN]')
    .replace(/\b\d{6}\b/g, '[REDACTED SIX-DIGIT VALUE]')
    .replace(/Using generated security password: \S+/g, 'Using generated security password: [REDACTED]');
}
function sanitize(value, secrets = []) {
  if (typeof value === 'string') return redactOutput(value, secrets);
  if (Array.isArray(value)) return value.map(item => sanitize(item, secrets));
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, sanitize(item, secrets)]));
  return value;
}
function sourceManifest() {
  const root = path.dirname(frontend), files = {};
  function visit(relative) {
    const file = path.join(root, relative);
    if (lstatSync(file).isSymbolicLink()) throw new Error('Source manifest does not follow links');
    if (lstatSync(file).isDirectory()) for (const name of readdirSync(file).sort()) visit(path.join(relative, name));
    else files[relative.replaceAll('\\', '/')] = createHash('sha256').update(readFileSync(file)).digest('hex');
  }
  for (const directory of ['backend/src/main', 'backend/src/e2e', 'frontend/src', 'frontend/e2e']) visit(directory);
  for (const file of ['backend/build.gradle', 'frontend/playwright.config.cjs', 'frontend/package.json', 'frontend/yarn.lock']) visit(file);
  const git = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8', windowsHide: true });
  const dirty = spawnSync('git', ['status', '--short'], { cwd: root, encoding: 'utf8', windowsHide: true });
  if (git.status !== 0 || dirty.status !== 0) throw new Error('Cannot capture E2E source identity');
  return { gitSha: git.stdout.trim(), worktree: dirty.stdout.trim().split(/\r?\n/), files };
}

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

// Only the CLI selects retention; ambient E2E settings are filtered out.
function retentionEnabled(environment = process.env) {
  const value = environment.E2E_RETAIN_RESOURCES;
  if (value !== undefined && value !== '1') throw new Error('Invalid E2E retention setting');
  return value === '1';
}
function parseRunArguments(args) {
  let retainResources = false;
  const playwrightArgs = [];
  for (const arg of args) {
    if (arg === '--retain-resources') {
      if (retainResources) throw new Error('Duplicate --retain-resources');
      retainResources = true;
    } else {
      if (arg.startsWith('--retain-resources=')) throw new Error('Use --retain-resources without a value');
      playwrightArgs.push(arg);
    }
  }
  return { retainResources, playwrightArgs };
}

async function main(args = process.argv.slice(2)) {
  const { retainResources, playwrightArgs } = parseRunArguments(args);
  const id = randomBytes(6).toString('hex');
  const name = `kkiri-e2e-${id}`;
  const env = {
    ...safeEnvironment(process.env), E2E_MANAGED: '1', E2E_DATABASE: `kkiri_e2e_${id}`,
    E2E_DB_USER: `e2e_${id}`, E2E_DB_PASSWORD: randomBytes(24).toString('hex'),
    E2E_JWT_SECRET: randomBytes(48).toString('base64'),
    E2E_LOGIN_EMAIL: `e2e${id}@hknu.ac.kr`, E2E_LOGIN_PASSWORD: `E2e!${randomBytes(18).toString('hex')}`,
    E2E_SECOND_EMAIL: `peer${id}@hknu.ac.kr`, E2E_ADMIN_EMAIL: `admin${id}@hknu.ac.kr`,
    E2E_IMAGE_DIRECTORY: path.join(backend, 'build/e2e', id, 'uploads'),
    E2E_EVIDENCE_DIR: path.join(backend, 'build/full-service-e2e', id),
    ...(retainResources ? { E2E_RETAIN_RESOURCES: '1' } : {}),
  };
  Object.assign(env, { MYSQL_DATABASE: env.E2E_DATABASE, MYSQL_USER: env.E2E_DB_USER,
    MYSQL_PASSWORD: env.E2E_DB_PASSWORD, MYSQL_ROOT_PASSWORD: randomBytes(24).toString('hex') });
  const children = new Set();
  const launched = [];
  mkdirSync(env.E2E_EVIDENCE_DIR, { recursive: true });
  const before = sourceManifest();
  env.E2E_GIT_SHA = before.gitSha;
  writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'source-before.json'), JSON.stringify(before, null, 2));
  const run = { id, startedAt: new Date().toISOString(), mysql: '8.0.46', database: env.E2E_DATABASE,
    frontend: 'http://127.0.0.1:13000', backend: 'http://127.0.0.1:18080', node: process.version,
    gitSha: before.gitSha, testArguments: playwrightArgs, fixtureTablesEmpty: false };
  if (retainResources) run.retention = { enabled: true, automaticDeletion: false,
    containerName: name, database: env.E2E_DATABASE, account: env.E2E_DB_USER,
    imageDirectory: env.E2E_IMAGE_DIRECTORY, serverUuid: null,
    creationAttempted: false, containerCreated: false, containerStarted: false,
    schemaPrepared: false, seedCompleted: false };
  const stage = value => { if (retainResources) run.phase = value; };
  console.log(`[e2e] Evidence: ${path.relative(path.dirname(frontend), env.E2E_EVIDENCE_DIR)}`);
  let ownsContainer = false;
  let stopping = false;
  let api;
  let react;
  let control;
  let restarting = false;
  const pendingMail = new Map();
  function failureLog(child, label) {
    const directory = path.join(env.E2E_EVIDENCE_DIR, 'logs');
    mkdirSync(directory, { recursive: true });
    const output = redactOutput(child.output + '\n' + child.errors,
      ['E2E_DB_PASSWORD', 'E2E_LOGIN_PASSWORD', 'E2E_JWT_SECRET', 'MYSQL_ROOT_PASSWORD'].map(key => env[key]));
    const file = path.join(directory, label.replace(/[^a-z0-9]+/gi, '-') + '.log');
    writeFileSync(file, output);
    return path.relative(frontend, file);
  }
  function start(command, args, cwd = frontend, extraEnv = {}) {
    if (retainResources && stopping) throw new Error('Retained E2E execution is stopping');
    const child = spawn(command, args, { cwd, env: { ...env, ...extraEnv }, windowsHide: true,
      detached: process.platform !== 'win32', stdio: ['pipe', 'pipe', 'pipe'] });
    child.output = ''; child.errors = '';
    let buffer = '';
    child.stdout.on('data', data => {
      buffer += data;
      const lines = buffer.split(/\r?\n/); buffer = lines.pop();
      for (const line of lines) {
        if (line.startsWith('E2E_PRIVATE ')) {
          const [, requestId, code = ''] = line.split(' ');
          pendingMail.get(requestId)?.(code); pendingMail.delete(requestId);
        } else child.output = (child.output + line + '\n').slice(-100_000);
      }
    });
    child.once('close', () => { if (!buffer.startsWith('E2E_PRIVATE ')) child.output += buffer; });
    child.stderr.on('data', data => { child.errors = (child.errors + data).slice(-100_000); });
    child.on('error', error => { child.spawnError = error; });
    children.add(child);
    launched.push(child);
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
    if (control) await new Promise(resolve => control.close(resolve));
    for (const child of [...children].reverse()) await stop(child);
    // Evidence is written directly outside the allowlisted disposable E2E directories.
    // Retention never performs Docker cleanup, including on errors and signals.
    const evidenceErrors = [];
    try {
      if (api) failureLog(api, 'Spring-Boot-final');
      if (react) failureLog(react, 'React-final');
      const after = sourceManifest();
      writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'source-after.json'), JSON.stringify(after, null, 2));
      run.sourceUnchangedDuringRun = JSON.stringify(before.files) === JSON.stringify(after.files);
    } catch { evidenceErrors.push('Could not finish source/server evidence'); }
    if (ownsContainer && !retainResources) {
      const container = await docker(['ps', '-aq', '--filter', `name=^/${name}$`, '--filter', `label=kkiri.e2e.owner=${id}`], 'Find owned E2E container');
      if (container) {
        const owner = await docker(['inspect', '--format', '{{index .Config.Labels "kkiri.e2e.owner"}}', container], 'Verify container ownership');
        if (owner !== id) throw new Error('Refusing to remove a container with a different owner.');
        await docker(['rm', '--force', '--volumes', container], 'Remove owned E2E MySQL');
        run.containerRemoved = true;
        console.log('[e2e] Owned MySQL container and disposable data removed.');
      }
    }
    if (retainResources) {
      run.containerRemoved = false;
      console.log('[e2e] Retention enabled: no container stop/removal or database cleanup performed.');
    }
    run.finishedAt = new Date().toISOString();
    run.ownedProcessesStopped = launched.every(child => child.exitCode !== null || child.signalCode !== null || child.spawnError);
    run.evidenceErrors = evidenceErrors;
    writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'run.json'), JSON.stringify(run, null, 2));
    if (evidenceErrors.length || !run.ownedProcessesStopped) throw new Error('E2E cleanup/evidence incomplete; inspect run.json');
  }
  const interrupted = signal => {
    if (retainResources) { run.interrupted = signal; run.failureStage = run.phase; }
    void cleanup().then(() => process.exit(130), error => { console.error(error.message); process.exit(1); });
  };
  const onSigint = () => interrupted('SIGINT'), onSigterm = () => interrupted('SIGTERM');
  process.once('SIGINT', onSigint); process.once('SIGTERM', onSigterm);
  try {
    stage('preflight');
    for (const port of ports) await assertFree(port);
    const endpoint = await docker(['context', 'inspect', '--format', '{{.Endpoints.docker.Host}}'], 'Read Docker endpoint');
    if (!/^(npipe:\/\/|unix:\/\/)/.test(endpoint)) throw new Error('E2E requires a local Docker engine.');
    const engine = await docker(['info', '--format', '{{.OSType}}'], 'Docker engine availability');
    if (engine !== 'linux') throw new Error('E2E requires Docker Linux containers. Start Docker Desktop.');
    console.log('[e2e] Starting isolated MySQL 8.0.46 on 127.0.0.1:13316.');
    // Fixed name is unique per run; no existing container or volume is reused.
    stage('container-create');
    if (retainResources) run.retention.creationAttempted = true;
    ownsContainer = true;
    await docker(['create', '--name', name, '--hostname', 'kkiri-e2e', '--label', `kkiri.e2e.owner=${id}`,
      '--publish', '127.0.0.1:13316:3306', '--tmpfs', '/var/lib/mysql:rw,size=1073741824',
      '--env', 'MYSQL_DATABASE', '--env', 'MYSQL_USER', '--env', 'MYSQL_PASSWORD', '--env', 'MYSQL_ROOT_PASSWORD',
      'mysql:8.0.46-oraclelinux9', '--lower-case-table-names=1', '--character-set-server=utf8mb4',
      '--collation-server=utf8mb4_0900_ai_ci'], 'Create E2E MySQL', 300_000);
    if (retainResources) run.retention.containerCreated = true;
    stage('container-start');
    await docker(['start', name], 'Start E2E MySQL');
    if (retainResources) run.retention.containerStarted = true;
    stage('mysql-ready');
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
    if (retainResources) run.retention.serverUuid = env.E2E_DB_UUID;
    console.log(`[e2e] Target: ${env.E2E_DATABASE}; account: ${env.E2E_DB_USER}; server UUID verified before DDL.`);
    stage('compile-e2e');
    const gradle = process.platform === 'win32'
      ? start('cmd.exe', ['/d', '/s', '/c', 'gradlew.bat --no-daemon prepareE2e'], backend)
      : start('./gradlew', ['--no-daemon', 'prepareE2e'], backend);
    await complete(gradle, 'Compile E2E-only backend source set', 300_000);
    const classpath = readFileSync(path.join(backend, 'build/e2e/classpath.txt'), 'utf8');
    stage('schema-prepare');
    await complete(start('java', ['-cp', classpath, 'e2e.E2eDatabase', 'prepare']), 'Guard and prepare E2E schema');
    if (retainResources) {
      run.retention.schemaPrepared = true;
      stage('seed');
      await complete(start('java', ['-cp', classpath, 'e2e.E2eDatabase', 'seed']), 'Seed empty retained E2E schema');
      run.retention.seedCompleted = true;
    }
    mkdirSync(env.E2E_IMAGE_DIRECTORY, { recursive: true });
    stage('backend-start');
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
    // Available only to the Node test process; never passed to React or exposed as an app API.
    const controlKey = randomBytes(32).toString('hex');
    control = http.createServer(async (req, res) => {
      if (req.method !== 'POST' || req.headers.authorization !== `Bearer ${controlKey}`) { res.writeHead(403).end(); return; }
      try {
        let body = ''; for await (const chunk of req) { body += chunk; if (body.length > 500) throw new Error('Oversized control request'); }
        const input = JSON.parse(body);
        if (req.url === '/mail' && /^[a-z0-9]+@hknu\.ac\.kr$/.test(input.email)) {
          const requestId = randomBytes(12).toString('hex');
          const code = await new Promise((resolve, reject) => {
            const timer = setTimeout(() => { pendingMail.delete(requestId); reject(new Error('Mail IPC timeout')); }, 5000);
            pendingMail.set(requestId, code => { clearTimeout(timer); resolve(code); });
            api.stdin.write(`MAIL ${requestId} ${input.email}\n`);
          });
          res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ code }));
        } else if (req.url === '/restart') {
          const ttl = input.ttlSeconds ?? 3600;
          if (![8, 3600].includes(ttl)) throw new Error('Unsupported test lifetime');
          restarting = true;
          const previous = api; const closed = new Promise(resolve => previous.once('close', resolve));
          previous.stdin.end(); await Promise.race([closed, pause(15_000)]);
          if (previous.exitCode === null) throw new Error('Backend did not stop cleanly');
          api = start('java', ['-cp', classpath, 'e2e.E2eServer'], frontend, { E2E_ACCESS_TTL: String(ttl) });
          await ready(api, 'Restarted E2E Spring Boot', async () => api.output.includes('E2E_BACKEND_READY'));
          api.once('close', crash); restarting = false;
          res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ restarted: true }));
        } else res.writeHead(404).end();
      } catch { restarting = false; res.writeHead(500).end('E2E control operation failed'); }
    });
    await new Promise(resolve => control.listen(0, '127.0.0.1', resolve));
    stage('react-start');
    react = start(process.execPath, [require.resolve('react-scripts/scripts/start.js')], frontend, {
      HOST: '127.0.0.1', PORT: '13000', BROWSER: 'none', CI: 'true',
      REACT_APP_API_BASE_URL: 'http://127.0.0.1:18080/api',
      WDS_SOCKET_HOST: '127.0.0.1', WDS_SOCKET_PORT: '13000',
    });
    await ready(react, 'E2E React', async () => {
      if (api.exitCode !== null) throw new Error(`E2E Spring Boot stopped during React startup. Diagnostic: ${failureLog(api, 'E2E-Spring-Boot')}`);
      try { return (await fetch('http://127.0.0.1:13000/login', { signal: AbortSignal.timeout(2000) })).ok; }
      catch { return false; }
    }, 300_000);
    console.log('[e2e] React and Spring Boot ready; scheduling, production seeds, SMTP and AI network transport disabled.');
    stage('playwright');
    const playwright = start(process.execPath, [require.resolve('@playwright/test/cli'), 'test', ...playwrightArgs], frontend, {
      E2E_CONTROL_URL: `http://127.0.0.1:${control.address().port}`, E2E_CONTROL_KEY: controlKey,
    });
    const secrets = [env.E2E_LOGIN_PASSWORD, env.E2E_DB_PASSWORD, env.E2E_JWT_SECRET, env.MYSQL_ROOT_PASSWORD];
    // Buffer complete lines so credentials split across transport chunks are still redacted.
    const forward = (stream, write) => {
      let pending = '';
      stream.on('data', data => { pending += data; const lines = pending.split(/\r?\n/); pending = lines.pop();
        for (const line of lines) write(redactOutput(line, secrets) + '\n'); });
      stream.on('end', () => { if (pending) write(redactOutput(pending, secrets)); });
    };
    forward(playwright.stdout, text => process.stdout.write(text));
    forward(playwright.stderr, text => process.stderr.write(text));
    // Do not allow a crashed server to leave the test waiting until its own timeout.
    const crash = () => { if (!stopping && !restarting) { console.error('[e2e] A managed server stopped unexpectedly.'); void stop(playwright); } };
    api.once('close', crash); react.once('close', crash);
    try { await complete(playwright, 'Playwright', 1_200_000); run.playwrightExitCode = 0; }
    catch (error) { run.playwrightExitCode = playwright.exitCode; throw error; }
    finally {
      failureLog(playwright, 'Playwright');
      if (!retainResources) {
        await complete(start('java', ['-cp', classpath, 'e2e.E2eDatabase', 'clean']), 'Final E2E data cleanup');
        run.fixtureTablesEmpty = true;
        console.log('[e2e] All fixture tables verified empty.');
      }
    }
    api.removeListener('close', crash); react.removeListener('close', crash);
    api.stdin.end();
    await Promise.race([new Promise(resolve => api.once('close', resolve)), pause(10_000)]);
    stage('completed');
  } catch (error) {
    if (retainResources && !run.failureStage) run.failureStage = run.phase;
    for (const [child, label] of [[api, 'E2E-Spring-Boot'], [react, 'E2E-React']]) {
      if (child) console.error(`[e2e] Server diagnostic: ${failureLog(child, label)}`);
    }
    throw error;
  } finally {
    await cleanup();
    process.removeListener('SIGINT', onSigint); process.removeListener('SIGTERM', onSigterm);
  }
}
if (require.main === module) main().catch(error => { console.error(`[e2e] ${error.message}`); process.exitCode = 1; });
module.exports = { assertFree, safeEnvironment, redactOutput, sanitize, retentionEnabled, parseRunArguments, main };
