// Reuse exactly the user-approved retained CCRA resource. Never provisions or removes resources.
const fs = require('node:fs');
const path = require('node:path');
const { spawn, spawnSync } = require('node:child_process');
const { randomBytes, createHash } = require('node:crypto');
const { safeEnvironment, assertFree, redactOutput } = require('../../../frontend/e2e/run.cjs');
const root = path.resolve(__dirname, '../../..'), frontend = path.join(root, 'frontend');
const owner = '82cdabce6030';
const argument = process.argv[2];
if (argument && !/^--migration-from=[a-f0-9]{12}$/.test(argument)) throw new Error('Unsupported argument');
const onlyRegression = process.argv[3] === '--only-regression';
if (process.argv[3] && !onlyRegression) throw new Error('Unsupported test selection');
if (onlyRegression && !argument) throw new Error('Regression continuation requires migration evidence');
const migrationFrom = argument?.split('=')[1];
const prior = JSON.parse(fs.readFileSync(path.join(root, 'backend/build/full-service-e2e', owner, 'run.json'), 'utf8'));
if (prior.id !== owner || !prior.retention?.enabled || prior.containerRemoved || !prior.ownedProcessesStopped
  || prior.database !== 'kkiri_e2e_' + owner || prior.retention.containerName !== 'kkiri-e2e-' + owner
  || prior.retention.account !== 'e2e_' + owner
  || prior.retention.serverUuid !== 'f8a3a22f-ce99-11f1-bd9a-621e38ee5f3d') throw new Error('Retained resource metadata mismatch');
const id = randomBytes(6).toString('hex');
const directory = path.join(root, 'backend/build/full-service-e2e', id);
fs.mkdirSync(directory, { recursive: true });
const env = { ...safeEnvironment(process.env) }, children = [];
let secrets = [], stopping = false;
const run = { id, parentRunId: owner, startedAt: new Date().toISOString(), database: prior.database,
  purpose: 'D1/D2 fix revalidation and scoped Community/Chat regression',
  retention: { ...prior.retention, creationAttempted: false, continuation: true }, containerRemoved: false,
  testArguments: ['ccra-revalidation.spec.cjs', ...(onlyRegression ? ['--grep=RV-06'] : [])], migrationFrom, productionCodeChanged: false, p1Executed: false };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const save = () => fs.writeFileSync(path.join(directory, 'run.json'), JSON.stringify(run, null, 2));
function manifest() {
  const result = {};
  const visit = relative => {
    const p = path.join(root, relative), stat = fs.lstatSync(p);
    if (stat.isSymbolicLink()) throw new Error('Source symlink');
    if (stat.isDirectory()) fs.readdirSync(p).sort().forEach(name => visit(path.join(relative, name)));
    else result[relative.replaceAll('\\', '/')] = createHash('sha256').update(fs.readFileSync(p)).digest('hex');
  };
  ['frontend/src', 'backend/src/main', 'frontend/e2e', 'backend/src/e2e',
    'backend/tools/ccra-stage6-7', 'frontend/playwright.config.cjs'].forEach(visit);
  return result;
}
const before = manifest();
fs.writeFileSync(path.join(directory, 'source-before.json'), JSON.stringify(before, null, 2));
function uploads() {
  const location = path.resolve(prior.retention.imageDirectory);
  if (location !== path.join(root, 'backend/build/e2e', owner, 'uploads')) throw new Error('Upload directory mismatch');
  return fs.readdirSync(location).sort().map(name => {
    const p = path.join(location, name);
    if (!fs.lstatSync(p).isFile()) throw new Error('Unexpected upload entry');
    return { name, bytes: fs.statSync(p).size, sha256: createHash('sha256').update(fs.readFileSync(p)).digest('hex') };
  });
}
function start(command, args, cwd = root) {
  if (stopping) throw new Error('Stopping');
  const child = spawn(command, args, { cwd, env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  child.output = '';
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => { child.output = (child.output + String(chunk)).slice(-3000000); });
  child.done = new Promise(resolve => { child.once('error', () => resolve(-1)); child.once('close', resolve); });
  children.push(child); return child;
}
async function complete(child, label) {
  const code = await child.done;
  if (code !== 0) throw new Error(`${label} failed (${code}). See owned process log ${children.indexOf(child)}.`);
  return child.output;
}
async function db(mode, ...args) {
  const output = await complete(start('java', ['-cp', env.E2E_CCRA_DB_CLASSPATH, 'CcraRevalidationDatabase', mode, ...args]), 'Guarded database ' + mode);
  const line = output.split(/\r?\n/).find(s => s.startsWith('CCRA_DB '));
  if (!line) throw new Error('Missing guarded DB confirmation');
  const result = JSON.parse(line.slice(8));
  fs.writeFileSync(path.join(directory, mode + '.json'), JSON.stringify(result, null, 2));
  return result;
}
async function ready(child, condition) {
  const deadline = Date.now() + 300000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) throw new Error('Owned server exited before readiness');
    if (await condition()) return;
    await pause(500);
  }
  throw new Error('Owned server readiness timeout');
}
async function stop() {
  if (stopping) return; stopping = true;
  for (const child of [...children].reverse()) if (child.pid && child.exitCode === null) {
    child.stdin.end();
    await Promise.race([child.done, pause(3000)]);
    if (child.exitCode === null) {
      const killer = spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
      await new Promise(resolve => { killer.once('close', resolve); killer.once('error', resolve); });
      await Promise.race([child.done, pause(3000)]);
    }
  }
  for (const [i, child] of children.entries()) fs.writeFileSync(path.join(directory, `process-${i}.log`), redactOutput(child.output, secrets));
  const after = manifest(); fs.writeFileSync(path.join(directory, 'source-after.json'), JSON.stringify(after, null, 2));
  run.sourceUnchangedDuringRun = JSON.stringify(before) === JSON.stringify(after);
  run.ownedProcessesStopped = children.every(c => c.exitCode !== null || c.signalCode !== null);
  if (run.uploadsBefore) { run.uploadsAfter = uploads(); run.uploadsUnchanged = JSON.stringify(run.uploadsBefore) === JSON.stringify(run.uploadsAfter); }
  run.finishedAt = new Date().toISOString(); save();
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => { run.interrupted = signal; void stop().finally(() => process.exit(130)); });
async function main() {
  console.log('[ccra] Revalidation evidence: backend/build/full-service-e2e/' + id);
  const listed = spawnSync(process.execPath, [path.join(frontend, 'node_modules/@playwright/test/cli.js'), 'test', ...run.testArguments, '--list', '--reporter=list'],
    { cwd: frontend, env, windowsHide: true, encoding: 'utf8' });
  if (listed.status !== 0 || !listed.stdout.includes(onlyRegression ? 'Total: 1 test' : 'Total: 9 tests')) throw new Error('Scoped test selection mismatch before resource access');
  await assertFree(13000); await assertFree(18080);
  const inspected = spawnSync('docker', ['inspect', prior.retention.containerName], { env, encoding: 'utf8', windowsHide: true });
  if (inspected.status !== 0) throw new Error('Cannot inspect the approved Docker resource');
  const info = JSON.parse(inspected.stdout)[0];
  if (info.Name !== '/kkiri-e2e-' + owner || info.Config.Labels['kkiri.e2e.owner'] !== owner || !info.State.Running
    || info.Config.Image !== 'mysql:8.0.46-oraclelinux9'
    || JSON.stringify(info.NetworkSettings.Ports['3306/tcp']) !== JSON.stringify([{ HostIp: '127.0.0.1', HostPort: '13316' }])) throw new Error('Docker ownership/state/port mismatch');
  const values = Object.fromEntries(info.Config.Env.map(value => { const p = value.indexOf('='); return [value.slice(0, p), value.slice(p + 1)]; }));
  if (values.MYSQL_DATABASE !== prior.database || values.MYSQL_USER !== prior.retention.account) throw new Error('Docker schema/account mismatch');
  run.containerIdentity = { name: info.Name.slice(1), owner: info.Config.Labels['kkiri.e2e.owner'], state: info.State.Status, image: info.Config.Image, binding: info.NetworkSettings.Ports['3306/tcp'] };
  Object.assign(env, { E2E_MANAGED: '1', E2E_RETAIN_RESOURCES: '1', E2E_DATABASE: prior.database,
    E2E_DB_USER: prior.retention.account, E2E_DB_PASSWORD: values.MYSQL_PASSWORD, E2E_DB_UUID: prior.retention.serverUuid,
    E2E_JWT_SECRET: randomBytes(48).toString('base64'), E2E_LOGIN_PASSWORD: `E2e!${randomBytes(18).toString('hex')}`,
    E2E_LOGIN_EMAIL: `rva${id}@hknu.ac.kr`, E2E_SECOND_EMAIL: `rvb${id}@hknu.ac.kr`, E2E_ADMIN_EMAIL: `rvadmin${id}@hknu.ac.kr`,
    E2E_CCRA_NICK_SUFFIX: 'V' + id.slice(-5), E2E_CCRA_ATTEMPT: 'revalidation-' + id,
    E2E_IMAGE_DIRECTORY: prior.retention.imageDirectory, E2E_EVIDENCE_DIR: directory, E2E_GIT_SHA: prior.gitSha,
    HOST: '127.0.0.1', PORT: '13000', BROWSER: 'none', CI: 'true', REACT_APP_API_BASE_URL: 'http://127.0.0.1:18080/api', WDS_SOCKET_HOST: '127.0.0.1', WDS_SOCKET_PORT: '13000' });
  secrets = [env.E2E_DB_PASSWORD, env.E2E_LOGIN_PASSWORD, env.E2E_JWT_SECRET, values.MYSQL_ROOT_PASSWORD];
  const classpath = fs.readFileSync(path.join(root, 'backend/build/e2e/classpath.txt'), 'utf8');
  const classes = path.join(directory, 'java-classes'); fs.mkdirSync(classes);
  await complete(start('javac', ['-encoding', 'UTF-8', '-cp', classpath, '-d', classes, path.join(__dirname, 'CcraRevalidationDatabase.java')]), 'Compile scoped guarded helper');
  env.E2E_CCRA_DB_CLASSPATH = classes + path.delimiter + classpath;
  run.uploadsBefore = uploads(); save();
  if (migrationFrom) {
    const parent = path.join(root, 'backend/build/full-service-e2e', migrationFrom);
    const migration = JSON.parse(fs.readFileSync(path.join(parent, 'migrate.json'), 'utf8'));
    if (!migration.applied || migration.identity[0].db !== prior.database || migration.identity[0].uuid !== prior.retention.serverUuid) throw new Error('Invalid migration evidence');
    fs.copyFileSync(path.join(parent, 'original-row-fingerprints.json'), path.join(directory, 'original-row-fingerprints.json'));
    run.migrationAlreadyApplied = true;
  } else {
    const migration = await db('migrate'); run.migrationApplied = migration.applied;
    console.log('[ccra] Migration applied; legacy rows unchanged; prior message rows=' + migration.legacyMessages[0].total);
  }
  save();
  run.fixtureResult = await db('actors'); run.fixtureActorsAdded = 3; save();
  const api = start('java', ['-cp', classpath, 'e2e.E2eServer']);
  await ready(api, async () => api.output.includes('E2E_BACKEND_READY'));
  const react = start(process.execPath, [path.join(frontend, 'node_modules/react-scripts/scripts/start.js')], frontend);
  await ready(react, async () => { try { return (await fetch('http://127.0.0.1:13000/login', { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; } });
  const playwright = start(process.execPath, [path.join(frontend, 'node_modules/@playwright/test/cli.js'), 'test', ...run.testArguments,
    '--output=' + path.join(directory, 'browser-output')], frontend);
  let pending = '';
  playwright.stdout.on('data', chunk => { pending += chunk; const lines = pending.split(/\r?\n/); pending = lines.pop(); lines.forEach(line => console.log(redactOutput(line, secrets))); });
  await playwright.done; run.playwrightExitCode = playwright.exitCode;
  await db('preservation');
  if (playwright.exitCode !== 0) process.exitCode = 1;
}
main().catch(error => { run.error = redactOutput(error.message, secrets); console.error('[ccra] ' + run.error); process.exitCode = 1; }).finally(stop);
