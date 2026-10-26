const { spawn, spawnSync } = require('node:child_process');
const { randomBytes, createHash } = require('node:crypto');
const { readFileSync, mkdirSync, writeFileSync, appendFileSync, readdirSync, lstatSync, existsSync } = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const http = require('node:http');

const frontend = path.resolve(__dirname, '..');
const backend = path.resolve(frontend, '../backend');
const ports = [13000, 18080, 13316];
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const privateValues = new Set();
function registerSecret(value) {
  if (typeof value !== 'string' || value.length < 6 || value.length > 200) throw new Error('Invalid private test value');
  privateValues.add(value);
}
function redactOutput(output, secrets = []) {
  for (const secret of [...secrets, ...privateValues].filter(Boolean).sort((a,b) => b.length-a.length)) output = output.split(secret).join('[REDACTED]');
  return output.replace(/^E2E_PRIVATE .*$/gm, '[PRIVATE IPC OMITTED]')
    .replace(/E2e![a-f0-9]{36}/g, '[REDACTED]')
    .replace(/(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])/g, '[REDACTED OPAQUE VALUE]')
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

async function main(rawArgs = process.argv.slice(2)) {
  const { retainResources, playwrightArgs: args } = parseRunArguments(rawArgs);
  const mcRechecking = args[0] === '--mc-p0-recheck';
  const mcP0 = args[0] === '--mc-p0' || mcRechecking;
  if (mcP0 && (args.length !== (mcRechecking ? 2 : 1) || retainResources)) throw new Error('MC P0 accepts only its approved disposable session');
  if (args.some(arg => arg.startsWith('--mc-') && !['--mc-p0','--mc-p0-recheck'].includes(arg))) throw new Error('MC P1 is not approved');
  const mcRecheck = mcRechecking ? require('./mc-session.cjs').recheckSource(path.join(backend, 'build/full-service-e2e'), args[1]) : null;
  if (retainResources && args.some(arg => /^--aus-p[01](?:-recheck)?$/.test(arg))) {
    throw new Error('AUS P0/P1 requires disposable resources; retention is not supported');
  }
  const ausP1 = ['--aus-p1', '--aus-p1-recheck'].includes(args[0]);
  let p1Recheck;
  if (ausP1) {
    if (args[0] === '--aus-p1' && args.length !== 1) throw new Error('P1 selectors are fixed');
    if (args[0] === '--aus-p1-recheck') {
      if (args.length !== 2 || !/^[a-f0-9]{12}$/.test(args[1])) throw new Error('Invalid P1 source');
      const directory = path.join(backend, 'build/full-service-e2e', args[1]);
      const recovered = !existsSync(path.join(directory, 'run.json')) && existsSync(path.join(directory, 'interruption-cleanup.json'));
      const previous = JSON.parse(readFileSync(path.join(directory, recovered ? 'interruption-cleanup.json' : 'run.json')));
      const bytes = readFileSync(path.join(directory, recovered ? 'interruption-recovery.json' : 'results.json'));
      if ((!recovered && previous.scope !== 'AUS_P1_ONLY') || !previous.containerRemoved || !previous.fixtureTablesEmpty || !previous.ownedProcessesStopped) throw new Error('P1 cleanup not verified');
      const tests = recovered ? [] : JSON.parse(bytes).tests.filter(t => t.attempts.some(a => ['failed','timedOut'].includes(a.status)));
      const ids = recovered ? JSON.parse(bytes).remaining : tests.map(t => t.name.match(/^(B1[0-5]-[a-z0-9-]+):/)?.[1]);
      if (!ids.length || ids.some(id => !id)) throw new Error('Only failed P1 tests may be selected');
      if (ids.some(id => !/^B1[0-5]-[a-z0-9-]+$/.test(id))) throw new Error('Invalid P1 recovery selector');
      p1Recheck = { source: args[1], ids, recoveredInterruption: recovered, sha256: createHash('sha256').update(bytes).digest('hex') };
    }
  }
  const rechecking = args[0] === '--aus-p0-recheck';
  const recheckId = rechecking ? args[1] : null;
  const ausP0 = args[0] === '--aus-p0' || rechecking;
  if (rechecking && (args.length !== 2 || !/^[a-f0-9]{12}$/.test(recheckId ?? ''))) throw new Error('Invalid P0 recheck source.');
  if (ausP0 && !rechecking && args.length !== 1) throw new Error('AUS P0 accepts no additional test selectors.');
  let recheck;
  if (recheckId) {
    if (args.length !== 2 || !/^[a-f0-9]{12}$/.test(recheckId)) throw new Error('Invalid P0 recheck source.');
    const directory = path.join(backend, 'build/full-service-e2e', recheckId);
    const previous = JSON.parse(readFileSync(path.join(directory, 'run.json'), 'utf8'));
    const resultsFile = readFileSync(path.join(directory, 'results.json'));
    const results = JSON.parse(resultsFile);
    if (previous.scope !== 'AUS_P0_ONLY' || !previous.containerRemoved || !previous.ownedProcessesStopped || !previous.fixtureTablesEmpty)
      throw new Error('Previous P0 must have verified cleanup before recheck.');
    const failed = results.tests.filter(t => t.attempts.some(a => a.status === 'failed' || a.status === 'timedOut'));
    const ids = failed.map(t => t.name.match(/^(B[13456789]) P0:/)?.[1]);
    if (!ids.length || ids.some(id => !id)) throw new Error('Only failed AUS P0 tests can be rechecked.');
    recheck = { source: recheckId, resultsSha256: createHash('sha256').update(resultsFile).digest('hex'), ids };
  }
  const testArgs = ausP1 ? ['aus-p1.spec.cjs', '--project=aus-p1', '--no-deps', ...(p1Recheck ? ['--grep','(' + p1Recheck.ids.join('|') + '):'] : [])]
    : recheck ? ['aus-p0.spec.cjs', '--project=aus-p0', '--no-deps', '--grep', '(' + recheck.ids.join('|') + ') P0:']
    : ausP0 ? ['login.spec.cjs', 'aus-p0.spec.cjs'] : mcP0 ? require('./mc-session.cjs').selection(mcRecheck?.ids ?? require('./mc-session.cjs').CATALOG) : args;
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
    ...(ausP1 ? { E2E_AUS_P1: '1', E2E_SUSPENDED_EMAIL: `suspended${id}@hknu.ac.kr`, E2E_WITHDRAWN_EMAIL: `withdrawn${id}@hknu.ac.kr` } : {}),
    ...(retainResources ? { E2E_RETAIN_RESOURCES: '1' } : {}),
    ...(mcP0 ? { E2E_MC_P0: '1' } : {}),
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
    gitSha: before.gitSha, testArguments: testArgs, scope: ausP1 ? 'AUS_P1_ONLY' : ausP0 ? 'AUS_P0_ONLY' : 'existing selectors', recheck: mcRecheck ?? p1Recheck ?? recheck, fixtureTablesEmpty: false };
  if (retainResources) run.retention = { enabled: true, automaticDeletion: false,
    containerName: name, database: env.E2E_DATABASE, account: env.E2E_DB_USER,
    imageDirectory: env.E2E_IMAGE_DIRECTORY, serverUuid: null,
    creationAttempted: false, containerCreated: false, containerStarted: false,
    schemaPrepared: false, seedCompleted: false };
  if (mcP0) run.scope = 'MATCHING_CALENDAR_P0_ONLY';
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
        } else if (line.startsWith('E2E_HTTP ')) {
          const event = JSON.parse(line.slice(9));
          appendFileSync(path.join(env.E2E_EVIDENCE_DIR, 'server-http.jsonl'), JSON.stringify({ pid: child.pid, ...event }) + '\n');
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
        if (owner !== id || (mcP0 && container !== run.containerId.slice(0, container.length))) throw new Error('Refusing to remove a container with a different owner or ID.');
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
    const browserPath = require('playwright-core').chromium.executablePath();
    if (!existsSync(browserPath)) throw new Error('Existing Chromium is missing; download is not authorized.');
    if (ausP0 || ausP1 || mcP0) {
      const listing = await complete(start(process.execPath,
        [require.resolve('@playwright/test/cli'), 'test', ...testArgs, '--list', '--reporter=list'], frontend,
        { E2E_AUS_P0: ausP0 ? '1' : '0', E2E_AUS_P1: ausP1 ? '1' : '0', E2E_MC_P0: mcP0 ? '1' : '0' }), 'Enumerate approved tests before resource creation');
      const count = Number(listing.match(/Total: (\d+) tests? in/)?.[1]);
      if (mcP0) require('./mc-session.cjs').verifyListing(listing, mcRecheck?.ids);
      if (count !== (mcP0 ? (mcRecheck ? mcRecheck.ids.length : 11) : ausP1 ? (p1Recheck ? p1Recheck.ids.length : 23) : recheck ? recheck.ids.length : 9)) throw new Error('Approved test count mismatch.');
      run.selectedTestCount = count;
      writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'test-selection.txt'), listing + '\n');
    }
    const imageId = await docker(['image', 'inspect', 'mysql:8.0.46-oraclelinux9', '--format', '{{.Id}}'], 'Existing MySQL image');
    if (!/^sha256:[a-f0-9]{64}$/.test(imageId)) throw new Error('Existing MySQL image is missing.');
    const collision = await docker(['ps', '-aq', '--filter', `name=^/${name}$`], 'New E2E name collision check');
    if (collision) throw new Error('E2E resource name already exists; refusing access.');
    run.preflight = { endpoint, engine, imageId, browserPath, portsFree: ports, container: name, namedVolumes: 0 };
    writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'preflight.json'), JSON.stringify(run.preflight, null, 2));
    console.log(`[e2e] New target ${name}; schema ${env.E2E_DATABASE}; image ${imageId}; ports ${ports.join('/')}.`);
    console.log('[e2e] Starting isolated MySQL 8.0.46 on 127.0.0.1:13316.');
    // Fixed name is unique per run; no existing container or volume is reused.
    if (ausP1) {
      const ledgerFile = path.join(backend, 'build/aus-p1-preparation/resource-ledger.json');
      const ledger = existsSync(ledgerFile) ? JSON.parse(readFileSync(ledgerFile)) : [];
      if (ledger.length >= 2) throw new Error('Two-container P1 approval budget exhausted');
      for (const prior of ledger) {
        const dir = path.join(backend, 'build/full-service-e2e', prior.id);
        const finished = JSON.parse(readFileSync(path.join(dir, existsSync(path.join(dir,'run.json')) ? 'run.json' : 'interruption-cleanup.json')));
        if (!finished.containerRemoved || !finished.ownedProcessesStopped) throw new Error('Previous P1 resource is not cleaned');
      }
      ledger.push({ id, name, attemptedAt: new Date().toISOString() });
      mkdirSync(path.dirname(ledgerFile), { recursive: true });
      writeFileSync(ledgerFile, JSON.stringify(ledger, null, 2));
    }
    stage('container-create');
    if (mcP0) {
      const ledgerDirectory = mcRecheck ? path.join(backend, 'build/mc-p0-rechecks', mcRecheck.source) : path.join(backend, 'build/mc-p0-preparation');
      if (mcRecheck && existsSync(path.join(ledgerDirectory, 'additional-resource-approval.json'))) throw new Error('Only one approved recheck container is allowed');
      require('./mc-session.cjs').reserveResource(ledgerDirectory,
        path.join(backend, 'build/full-service-e2e'), { id, name, attemptedAt: new Date().toISOString() });
    }
    if (retainResources) run.retention.creationAttempted = true;
    ownsContainer = true;
    run.containerId = await docker(['create', '--pull=never', '--name', name, '--hostname', 'kkiri-e2e', '--label', `kkiri.e2e.owner=${id}`,
      '--publish', '127.0.0.1:13316:3306', '--tmpfs', '/var/lib/mysql:rw,size=1073741824',
      '--env', 'MYSQL_DATABASE', '--env', 'MYSQL_USER', '--env', 'MYSQL_PASSWORD', '--env', 'MYSQL_ROOT_PASSWORD',
      'mysql:8.0.46-oraclelinux9', '--lower-case-table-names=1', '--character-set-server=utf8mb4',
      '--collation-server=utf8mb4_0900_ai_ci'], 'Create E2E MySQL', 300_000);
    if (retainResources) run.retention.containerCreated = true;
    stage('container-start');
    await docker(['start', name], 'Start E2E MySQL');
    if (retainResources) run.retention.containerStarted = true;
    if (mcP0) {
      const storage = JSON.parse(await docker(['inspect', '--format', '{"Mounts":{{json .Mounts}},"HostConfig":{"Tmpfs":{{json .HostConfig.Tmpfs}}}}', name], 'Verify new MC storage'));
      writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'storage-observed.json'), JSON.stringify(storage, null, 2));
      run.storage = require('./mc-session.cjs').verifyStorage(storage);
    }
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
    if (mcRecheck) {
      run.databaseIdentity = { database: env.E2E_DATABASE, account: env.E2E_DB_USER, serverUuid: env.E2E_DB_UUID };
      writeFileSync(path.join(env.E2E_EVIDENCE_DIR, 'resource-identity.json'), JSON.stringify({ runId: id,
        container: name, containerId: run.containerId, ownerLabel: id, ...run.databaseIdentity, storage: run.storage }, null, 2));
    }
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
        if (ausP1 && req.url === '/secret') {
          registerSecret(input.value); res.setHeader('Content-Type','application/json'); res.end('{}');
        } else if ((req.url === '/mail' || (ausP1 && req.url === '/mail-state')) && /^[a-z0-9]+@hknu\.ac\.kr$/.test(input.email) && [undefined, 'verification', 'reset'].includes(input.kind)) {
          const requestId = randomBytes(12).toString('hex');
          const code = await new Promise((resolve, reject) => {
            const timer = setTimeout(() => { pendingMail.delete(requestId); reject(new Error('Mail IPC timeout')); }, 5000);
            pendingMail.set(requestId, code => { clearTimeout(timer); resolve(code); });
            api.stdin.write(`MAIL ${requestId} ${req.url === '/mail-state' ? 'state:' : input.kind === 'reset' ? 'reset:' : ''}${input.email}\n`);
          });
          res.setHeader('Content-Type', 'application/json'); res.end(req.url === '/mail-state' ? code : JSON.stringify({ code }));
        } else if (req.url === '/restart') {
          const ttl = input.ttlSeconds ?? 3600;
          if (![8, 3600].includes(ttl)) throw new Error('Unsupported test lifetime');
          const signupTtl = input.signupTtlSeconds ?? 300;
          if (![8, 300].includes(signupTtl)) throw new Error('Unsupported signup lifetime');
          const codeTtl = input.codeTtlSeconds ?? 300, resetTtl = input.resetTtlSeconds ?? 300;
          if (![8,300].includes(codeTtl) || ![8,300].includes(resetTtl) || (!ausP1 && (codeTtl !== 300 || resetTtl !== 300))) throw new Error('Unsupported P1 lifetime');
          restarting = true;
          const previous = api; const closed = new Promise(resolve => previous.once('close', resolve));
          previous.stdin.end(); await Promise.race([closed, pause(15_000)]);
          if (previous.exitCode === null) throw new Error('Backend did not stop cleanly');
          api = start('java', ['-cp', classpath, 'e2e.E2eServer'], frontend, { E2E_ACCESS_TTL: String(ttl), E2E_SIGNUP_TTL: String(signupTtl), E2E_CODE_TTL: String(codeTtl), E2E_RESET_TTL: String(resetTtl) });
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
    const launchPlaywright = () => start(process.execPath, [require.resolve('@playwright/test/cli'), 'test', ...testArgs], frontend, {
      E2E_CONTROL_URL: `http://127.0.0.1:${control.address().port}`, E2E_CONTROL_KEY: controlKey,
      E2E_AUS_P0: ausP0 ? '1' : '0',
      // Playwright otherwise captures a DOM snapshot on failure, including plain-text reset tokens.
      PLAYWRIGHT_NO_COPY_PROMPT: '1',
    });
    const secrets = [env.E2E_LOGIN_PASSWORD, env.E2E_DB_PASSWORD, env.E2E_JWT_SECRET, env.MYSQL_ROOT_PASSWORD];
    // Buffer complete lines so credentials split across transport chunks are still redacted.
    const forward = (stream, write) => {
      let pending = '';
      stream.on('data', data => { pending += data; const lines = pending.split(/\r?\n/); pending = lines.pop();
        for (const line of lines) write(redactOutput(line, secrets) + '\n'); });
      stream.on('end', () => { if (pending) write(redactOutput(pending, secrets)); });
    };
    let playwright;
    // Do not allow a crashed server to leave the test waiting until its own timeout.
    const crash = () => { if (!stopping && !restarting) { console.error('[e2e] A managed server stopped unexpectedly.'); if (playwright) void stop(playwright); } };
    api.once('close', crash); react.once('close', crash);
    try {
      if (mcP0) {
        await require('./mc-session.cjs').session({ env: { ...env, E2E_CONTROL_URL: `http://127.0.0.1:${control.address().port}`, E2E_CONTROL_KEY: controlKey }, start, complete,
          forward: (stream, write) => forward(stream, write), failureLog, run, recheck: mcRecheck });
        if (mcRecheck && run.playwrightExitCode !== 0) throw new Error('MC recheck contains a failed or incomplete test');
      }
      else {
        playwright = launchPlaywright();
        forward(playwright.stdout, text => process.stdout.write(text));
        forward(playwright.stderr, text => process.stderr.write(text));
        await complete(playwright, 'Playwright', 1_200_000); run.playwrightExitCode = 0;
      }
    }
    catch (error) { run.playwrightExitCode = playwright?.exitCode ?? 1; throw error; }
    finally {
      if (playwright) failureLog(playwright, 'Playwright');
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
module.exports = { assertFree, safeEnvironment, redactOutput, sanitize, registerSecret, retentionEnabled, parseRunArguments, main };
