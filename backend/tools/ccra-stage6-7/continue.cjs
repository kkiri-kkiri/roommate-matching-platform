// Continue only this session's newly created, retained stack. This does not
// provision, reset, stop, or remove a Docker/DB resource, or change shared tools.
const fs = require('node:fs');
const path = require('node:path');
const { spawn, spawnSync } = require('node:child_process');
const { randomBytes, createHash } = require('node:crypto');
const { safeEnvironment, assertFree, redactOutput } = require('../../../frontend/e2e/run.cjs');
const root = path.resolve(__dirname, '../../..'), frontend = path.join(root, 'frontend');
const parentRunId = process.argv[2];
if (parentRunId !== '82cdabce6030') throw new Error('Only the newly owned current-session run is allowed');
const only07 = process.argv[3] === '--only-07';
if (process.argv[3] && !only07) throw new Error('Unsupported continuation selection');
const priorDirectory = path.join(root, 'backend/build/full-service-e2e', parentRunId);
const prior = JSON.parse(fs.readFileSync(path.join(priorDirectory, 'run.json'), 'utf8'));
if (!prior.retention?.enabled || prior.containerRemoved || !prior.ownedProcessesStopped || prior.id !== parentRunId) throw new Error('Prior run not safely retained/stopped');
const id = randomBytes(6).toString('hex');
const directory = path.join(root, 'backend/build/full-service-e2e', id);
fs.mkdirSync(directory, { recursive: true });
const run = { id, parentRunId, database: prior.database, startedAt: new Date().toISOString(), gitSha: prior.gitSha,
  retention: { ...prior.retention, creationAttempted: false, continuation: true }, containerRemoved: false,
  fixtureTablesEmpty: false, testArguments: ['ccra-p0.spec.cjs', only07 ? '--grep=P0-07' : '--grep=P0-(01|02|04|07)'] };
const env = { ...safeEnvironment(process.env) }, children = [];
let secrets = [], stopping = false;
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
function manifest() {
  const files = {};
  for (const file of ['frontend/e2e/lib/ccra-p0.cjs', 'frontend/e2e/tests/ccra-p0.spec.cjs',
    'frontend/e2e/run.cjs', 'frontend/e2e/fixtures.cjs', 'backend/src/e2e/java/e2e/E2eDatabase.java',
    'backend/tools/ccra-stage6-7/continue.cjs']) files[file] = createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
  return files;
}
const before = manifest();
fs.writeFileSync(path.join(directory, 'source-before.json'), JSON.stringify(before, null, 2));
function start(command, args, cwd = frontend) {
  if (stopping) throw new Error('Continuation stopping');
  const child = spawn(command, args, { cwd, env, windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
  child.output = '';
  for (const stream of [child.stdout, child.stderr]) stream.on('data', chunk => {
    child.output = (child.output + String(chunk)).slice(-100000);
  });
  child.done = new Promise(resolve => { child.once('error', () => resolve(-1)); child.once('close', resolve); });
  children.push(child); return child;
}
async function completed(child, label) { const code = await child.done; if (code !== 0) throw new Error(`${label} failed (${code})`); }
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
  for (const [index, child] of children.entries()) fs.writeFileSync(path.join(directory, `process-${index}.log`), redactOutput(child.output, secrets));
  const after = manifest(); fs.writeFileSync(path.join(directory, 'source-after.json'), JSON.stringify(after, null, 2));
  run.sourceUnchangedDuringRun = JSON.stringify(before) === JSON.stringify(after);
  run.ownedProcessesStopped = children.every(c => c.exitCode !== null || c.signalCode !== null);
  run.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(directory, 'run.json'), JSON.stringify(run, null, 2));
}
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => {
  run.interrupted = signal; void stop().finally(() => process.exit(130));
});
async function main() {
  console.log(`[ccra] Continuation evidence: backend/build/full-service-e2e/${id}`);
  const listed = spawnSync(process.execPath, [path.join(frontend, 'node_modules/@playwright/test/cli.js'),
    'test', ...run.testArguments, '--list', '--reporter=list'], { cwd: frontend, env, encoding: 'utf8', windowsHide: true });
  if (listed.status !== 0 || !listed.stdout.includes(only07 ? 'Total: 1 test' : 'Total: 4 tests')) throw new Error('P0 continuation selection must match before using resources');
  await assertFree(13000); await assertFree(18080);
  // Inspect exactly the container created in this session. Never enumerate Docker resources.
  const inspected = spawnSync('docker', ['inspect', prior.retention.containerName], { encoding: 'utf8', windowsHide: true, env });
  if (inspected.status !== 0) throw new Error('Cannot inspect the current-session owned container');
  const info = JSON.parse(inspected.stdout)[0];
  if (info.Name !== '/' + prior.retention.containerName || info.Config.Labels['kkiri.e2e.owner'] !== parentRunId || !info.State.Running) throw new Error('Owned container identity/state mismatch');
  const containerEnv = Object.fromEntries(info.Config.Env.map(value => { const at = value.indexOf('='); return [value.slice(0, at), value.slice(at + 1)]; }));
  if (containerEnv.MYSQL_DATABASE !== prior.database || containerEnv.MYSQL_USER !== prior.retention.account) throw new Error('Owned database mismatch');
  Object.assign(env, { E2E_MANAGED: '1', E2E_RETAIN_RESOURCES: '1', E2E_DATABASE: prior.database,
    E2E_DB_USER: prior.retention.account, E2E_DB_PASSWORD: containerEnv.MYSQL_PASSWORD,
    E2E_DB_UUID: prior.retention.serverUuid, E2E_JWT_SECRET: randomBytes(48).toString('base64'),
    E2E_LOGIN_PASSWORD: `E2e!${randomBytes(18).toString('hex')}`,
    E2E_LOGIN_EMAIL: `p0a${id}@hknu.ac.kr`, E2E_SECOND_EMAIL: `p0b${id}@hknu.ac.kr`, E2E_ADMIN_EMAIL: `p0admin${id}@hknu.ac.kr`,
    E2E_CCRA_NICK_SUFFIX: 'R' + id.slice(-4), E2E_CCRA_ATTEMPT: 'continuation-' + id.slice(-4),
    E2E_IMAGE_DIRECTORY: prior.retention.imageDirectory, E2E_EVIDENCE_DIR: directory, E2E_GIT_SHA: prior.gitSha,
    HOST: '127.0.0.1', PORT: '13000', BROWSER: 'none', CI: 'true',
    REACT_APP_API_BASE_URL: 'http://127.0.0.1:18080/api', WDS_SOCKET_HOST: '127.0.0.1', WDS_SOCKET_PORT: '13000' });
  secrets = [env.E2E_LOGIN_PASSWORD, env.E2E_DB_PASSWORD, env.E2E_JWT_SECRET, containerEnv.MYSQL_ROOT_PASSWORD];
  const classpath = fs.readFileSync(path.join(root, 'backend/build/e2e/classpath.txt'), 'utf8');
  // Insert only three fresh application actors; retain all original fixtures and business rows.
  const source = path.join(directory, 'ContinuationFixtures.java');
  fs.writeFileSync(source, `
import e2e.E2eDatabase;
import com.kkirikkiri.user.entity.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class ContinuationFixtures {
 public static void main(String[] args) throws Exception {
  var connection = E2eDatabase.class.getDeclaredMethod("guardedConnection"); connection.setAccessible(true);
  long dormId, facultyId;
  try (var c = (java.sql.Connection) connection.invoke(null); var s=c.createStatement(); var r=s.executeQuery("SELECT dormitory_id,faculty_id FROM user_profile ORDER BY user_id LIMIT 1")) {
   if (!r.next()) throw new IllegalStateException("Missing owned reference fixtures"); dormId=r.getLong(1); facultyId=r.getLong(2);
  }
  var method = E2eDatabase.class.getDeclaredMethod("factory", String.class); method.setAccessible(true);
  var factory=(LocalContainerEntityManagerFactoryBean)method.invoke(null,"validate");
  try (var em=factory.getObject().createEntityManager()) {
   var tx=em.getTransaction(); tx.begin();
   try {
    var dorm=em.find(Dormitory.class,dormId); var faculty=em.find(Faculty.class,facultyId);
    String[] emails={"E2E_LOGIN_EMAIL","E2E_SECOND_EMAIL","E2E_ADMIN_EMAIL"}; String[] names={"E2E사용자","E2E상대","E2E관리자"};
    for(int i=0;i<3;i++) {
     var u=new User(new BCryptPasswordEncoder().encode(System.getenv("E2E_LOGIN_PASSWORD")),System.getenv(emails[i]),i==2?UserRole.ADMIN:UserRole.USER);
     u.verifyEmail(); em.persist(u);
     em.persist(new UserProfile(u,names[i]+System.getenv("E2E_CCRA_NICK_SUFFIX"),"남자",dorm,faculty,null,(short)2000,"20","1학년","INTJ"));
    }
    tx.commit();
   } catch(Throwable error) { if(tx.isActive())tx.rollback();throw error; }
  } finally { factory.destroy(); }
  System.out.println("P0_CONTINUATION_FIXTURES_OK");
 }
}`);
  await completed(start('java', ['--class-path', classpath, source]), 'Append continuation fixture actors');
  run.fixtureActorsAdded = 3;
  const api = start('java', ['-cp', classpath, 'e2e.E2eServer']);
  async function ready(child, condition) {
    const deadline = Date.now() + 300000;
    while (Date.now() < deadline) {
      if (child.exitCode !== null) throw new Error('Continuation server exited');
      if (await condition()) return;
      await pause(500);
    }
    throw new Error('Continuation server timeout');
  }
  await ready(api, async () => api.output.includes('E2E_BACKEND_READY'));
  const react = start(process.execPath, [require.resolve(path.join(frontend, 'node_modules/react-scripts/scripts/start.js'))]);
  await ready(react, async () => { try { return (await fetch('http://127.0.0.1:13000/login', { signal: AbortSignal.timeout(2000) })).ok; } catch { return false; } });
  const playwright = start(process.execPath, [path.join(frontend, 'node_modules/@playwright/test/cli.js'), 'test', ...run.testArguments, '--output=' + path.join(directory, 'browser-output')]);
  // Forward only sanitized complete lines; never print authentication/configuration bodies.
  let pending = '';
  playwright.stdout.on('data', chunk => { pending += chunk; const lines = pending.split(/\r?\n/); pending = lines.pop(); for (const line of lines) console.log(redactOutput(line, secrets)); });
  await playwright.done;
  run.playwrightExitCode = playwright.exitCode;
  if (playwright.exitCode !== 0) process.exitCode = 1;
}
main().catch(error => { console.error('[ccra] ' + redactOutput(error.message, secrets)); run.error = redactOutput(error.message, secrets); process.exitCode = 1; })
  .finally(stop);
