const { test } = require('node:test');
const assert = require('node:assert/strict');
const net = require('node:net');
const { assertFree, safeEnvironment, redactOutput, sanitize } = require('./run.cjs');
const { database } = require('./lib/database.cjs');
const { retentionEnabled, parseRunArguments } = require('./run.cjs');
const { readFileSync, mkdirSync, mkdtempSync, writeFileSync } = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const { spawnSync } = require('node:child_process');

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

test('retention requires the explicit CLI flag and never forwards it to Playwright', () => {
  const args = ['chat.spec.cjs', '--headed', '--grep', 'P0'];
  assert.deepEqual(parseRunArguments(args), { retainResources: false, playwrightArgs: args });
  assert.deepEqual(parseRunArguments(['--retain-resources', ...args]), { retainResources: true, playwrightArgs: args });
  assert.equal(retentionEnabled({}), false);
  assert.equal(retentionEnabled({ E2E_RETAIN_RESOURCES: '1' }), true);
  for (const value of ['0', 'false', 'true', '']) assert.throws(() => retentionEnabled({ E2E_RETAIN_RESOURCES: value }), /Invalid/);
  assert.throws(() => parseRunArguments(['--retain-resources=false']), /without a value/);
  assert.throws(() => parseRunArguments(['--retain-resources', '--retain-resources']), /Duplicate/);
  assert.deepEqual(safeEnvironment({ E2E_RETAIN_RESOURCES: '1', PATH: '/bin' }), { PATH: '/bin' });
});

// Execute the actual runner with in-memory files and simulated child processes.
// No Docker CLI, Java, Gradle, React, Playwright or network is started by this harness.
async function simulateRun({ retained = true, failure, signal, evidenceFailure = false, platform = 'linux' } = {}) {
  const calls = [], files = new Map(), children = new Map(), logs = [];
  const fakeProcess = new EventEmitter();
  Object.assign(fakeProcess, { env: { PATH: '/test', E2E_RETAIN_RESOURCES: '1' },
    platform, execPath: '/fake/node', version: process.version,
    stdout: { write() {} }, stderr: { write() {} } });
  let finishSignal;
  const signalFinished = new Promise(resolve => { finishSignal = resolve; });
  fakeProcess.exit = code => { fakeProcess.exitCode = code; finishSignal(); };
  fakeProcess.kill = pid => children.get(Math.abs(pid))?.finish(0);
  const disk = {
    readFileSync: file => String(file).endsWith('classpath.txt') ? 'fake-classpath' : 'source',
    mkdirSync() {}, existsSync: () => false,
    lstatSync: file => ({ isSymbolicLink: () => false,
      isDirectory: () => ['backend/src/main', 'backend/src/e2e', 'frontend/src', 'frontend/e2e']
        .some(dir => String(file).replaceAll('\\', '/').endsWith(dir)) }),
    readdirSync: () => ['source.js'],
    writeFileSync: (file, text) => {
      if (evidenceFailure && String(file).endsWith('source-after.json')) throw new Error('Simulated evidence failure');
      files.set(String(file), text);
    },
  };
  const spawn = (command, args, options) => {
    calls.push({ command, args, env: options.env });
    const child = new EventEmitter();
    Object.assign(child, { pid: children.size + 100, exitCode: null, signalCode: null,
      stdout: new EventEmitter(), stderr: new EventEmitter() });
    child.finish = code => {
      if (child.exitCode !== null) return;
      child.exitCode = code; child.emit('close', code);
    };
    child.stdin = { write() {}, end: () => setImmediate(() => child.finish(0)) };
    children.set(child.pid, child);
    setImmediate(() => {
      let output = '', code = 0, running = false;
      if (command === 'docker') {
        if (args[0] === 'context') output = 'unix:///fake-docker';
        else if (args[0] === 'info') output = 'linux';
        else if (args[0] === 'create') { output = 'new-owned-container'; if (failure === 'create') code = 1; }
        else if (args[0] === 'exec') output = '11111111-2222-3333-4444-555555555555';
        else if (args[0] === 'ps') output = 'new-owned-container';
        else if (args[0] === 'inspect') output = calls.find(c => c.command === 'docker' && c.args[0] === 'create')
          .args.find(a => a.startsWith('kkiri.e2e.owner=')).split('=')[1];
        if (failure === 'start' && args[0] === 'start') code = 1;
      } else if (command === 'taskkill') {
        children.get(Number(args[1]))?.finish(0);
      } else if (args.includes('e2e.E2eDatabase')) {
        if (failure === args.at(-1)) code = 1;
      } else if (args.includes('e2e.E2eServer')) {
        output = 'E2E_BACKEND_READY'; running = failure !== 'backend'; code = running ? 0 : 1;
      } else if (args[0] === 'react-scripts/scripts/start.js') {
        running = failure !== 'react'; code = running ? 0 : 1;
      }
      else if (args[0] === '@playwright/test/cli') {
        if (signal) { running = true; fakeProcess.emit(signal); }
        else if (failure === 'playwright') code = 1;
      } else if (failure === 'compile') code = 1;
      if (output) child.stdout.emit('data', output + '\n');
      if (!running) child.finish(code);
    });
    return child;
  };
  const modules = {
    'node:child_process': { spawn, spawnSync: () => ({ status: 0, stdout: 'fake-sha' }) },
    'node:fs': disk,
    'node:http': { createServer: () => ({ listen: (_port, _host, cb) => cb(),
      address: () => ({ port: 19000 }), close: cb => cb() }) },
    'node:net': { createServer: () => {
      const server = new EventEmitter();
      server.listen = (_port, _host, cb) => { if (failure === 'preflight') server.emit('error'); else cb(); };
      server.close = cb => cb(); return server;
    } },
  };
  const fakeRequire = name => modules[name] || require(name);
  fakeRequire.resolve = name => name;
  const module = { exports: {} };
  const context = { require: fakeRequire, module, __dirname, process: fakeProcess,
    console: { log: text => logs.push(text), error: text => logs.push(text) },
    fetch: async () => { if (failure === 'react') throw new Error('Simulated React startup failure'); return { ok: true }; },
    AbortSignal, clearTimeout,
    setTimeout: (fn, ms) => { const timer = setTimeout(fn, ms === 500 ? 1 : ms); timer.unref(); return timer; } };
  vm.runInNewContext(readFileSync(path.join(__dirname, 'run.cjs'), 'utf8'), context);
  let error, timedOut = false;
  const watchdog = setTimeout(() => {
    timedOut = true;
    fakeProcess.emit('SIGTERM');
  }, 5000);
  try { await module.exports.main([...(retained ? ['--retain-resources'] : []), 'chat.spec.cjs']); }
  catch (caught) { error = caught; }
  finally { clearTimeout(watchdog); }
  assert.equal(timedOut, false, 'Simulated runner must finish within five seconds');
  if (signal) await signalFinished;
  const reportEntry = [...files].find(([file]) => file.endsWith('run.json'));
  return { calls, logs, children: [...children.values()], error, exitCode: fakeProcess.exitCode,
    report: reportEntry && JSON.parse(reportEntry[1]) };
}

for (const scenario of [{}, { failure: 'preflight' }, { failure: 'create' }, { failure: 'start' },
  { failure: 'compile' }, { failure: 'prepare' }, { failure: 'seed' }, { failure: 'backend' },
  { failure: 'react' }, { failure: 'playwright' }, { signal: 'SIGINT' }, { signal: 'SIGTERM' },
  { platform: 'win32', signal: 'SIGINT' }, { platform: 'win32', signal: 'SIGTERM' }, { evidenceFailure: true }]) {
  test(`retained runner preserves resources: ${JSON.stringify(scenario)}`, async () => {
    const result = await simulateRun(scenario);
    const dockerCalls = result.calls.filter(c => c.command === 'docker');
    assert.equal(dockerCalls.some(c => ['rm', 'stop', 'restart', 'kill', 'prune', 'ps', 'inspect'].includes(c.args[0])), false);
    const operations = result.calls.filter(c => c.args.includes('e2e.E2eDatabase')).map(c => c.args.at(-1));
    assert.equal(operations.some(op => ['reset', 'clean'].includes(op)), false);
    assert.ok(result.children.every(child => child.exitCode !== null), 'owned helper/app processes stop');
    assert.equal(result.report.containerRemoved, false);
    assert.equal(result.report.retention.automaticDeletion, false);
    assert.equal(result.report.fixtureTablesEmpty, false);
    assert.equal(result.report.ownedProcessesStopped, true);
    for (const call of result.calls.filter(c => c.env)) assert.equal(call.env.E2E_RETAIN_RESOURCES, '1');
    if (scenario.failure) { assert.ok(result.error); assert.ok(result.report.failureStage); }
    if (scenario.signal) { assert.equal(result.report.interrupted, scenario.signal); assert.equal(result.exitCode, 130); }
    if (scenario.evidenceFailure) { assert.ok(result.error); assert.equal(result.report.evidenceErrors.length, 1); }
    if (!Object.keys(scenario).length) {
      assert.ifError(result.error);
      assert.deepEqual(operations, ['prepare', 'seed']);
      assert.equal(result.report.retention.containerCreated, true);
      assert.equal(result.report.retention.containerStarted, true);
      assert.equal(result.report.retention.seedCompleted, true);
      assert.equal(result.report.retention.serverUuid, '11111111-2222-3333-4444-555555555555');
      assert.equal(result.report.phase, 'completed');
      const playwright = result.calls.find(c => c.args[0] === '@playwright/test/cli');
      assert.equal(playwright.args.includes('--retain-resources'), false);
    }
  });
}

for (const failure of [undefined, 'playwright']) {
  test(`default runner retains original cleanup and ignores ambient retention (${failure || 'success'})`, async () => {
    const result = await simulateRun({ retained: false, failure });
    assert.equal(Boolean(result.error), Boolean(failure));
    assert.equal(result.report.retention, undefined);
    assert.equal(result.report.containerRemoved, true);
    assert.equal(result.report.fixtureTablesEmpty, true);
    assert.equal(result.report.ownedProcessesStopped, true);
    assert.deepEqual(result.calls.filter(c => c.args.includes('e2e.E2eDatabase')).map(c => c.args.at(-1)), ['prepare', 'clean']);
    assert.equal(result.calls.filter(c => c.command === 'docker' && c.args[0] === 'rm').length, 1);
    for (const call of result.calls) assert.equal(call.env.E2E_RETAIN_RESOURCES, undefined);
  });
}

for (const retained of [false, true]) for (const failBody of [false, true]) {
  test(`actual fixture lifecycle: retention=${retained}, bodyFailure=${failBody}`, async () => {
    let definitions;
    const calls = [], environment = retained ? { E2E_RETAIN_RESOURCES: '1' } : {};
    const module = { exports: {} };
    vm.runInNewContext(readFileSync(path.join(__dirname, 'fixtures.cjs'), 'utf8'), {
      module, process: { env: environment },
      require: name => {
        if (name === '@playwright/test') return { test: { extend: value => { definitions = value; } } };
        if (name === './lib/database.cjs') return { database: operation => calls.push(operation) };
        if (name === './run.cjs') return { retentionEnabled: () => retentionEnabled(environment) };
        return require(name);
      },
    });
    const execute = () => definitions.isolatedDatabase[0]({}, async () => {
      calls.push('body'); if (failBody) throw new Error('Simulated test failure');
    }, { testId: 'safety-check' });
    if (failBody) await assert.rejects(execute, /Simulated test failure/); else await execute();
    assert.deepEqual(calls, retained ? ['body'] : ['reset', 'body', 'clean']);
  });
}

// Opt in after `gradlew --offline prepareE2e`. This launches only a small Java
// safety probe, not Spring or a JDBC datasource. Connection objects are proxies
// which reject every operation except the SELECTs used by the empty-table guard.
const javaProbe = `
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import e2e.E2eDatabase;

class RetentionSafetyProbe {
    static final List<String> sql = new ArrayList<>();
    static Object invoke(String name, Class<?>[] types, Object... args) throws Throwable {
        var method = E2eDatabase.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { return method.invoke(null, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
    interface Checked { void run() throws Throwable; }
    static void denied(Checked work, String message) throws Throwable {
        try { work.run(); } catch (IllegalStateException failure) {
            if (failure.getMessage().contains(message)) return;
            throw failure;
        }
        throw new AssertionError("Expected denial: " + message);
    }
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    static ResultSet rows(List<String> names, Long count) {
        int[] row = {-1};
        return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
            case "next" -> ++row[0] < (count == null ? names.size() : 1);
            case "getString" -> names.get(row[0]);
            case "getLong" -> count;
            case "close" -> null;
            default -> throw new AssertionError("Unexpected ResultSet call: " + m.getName());
        });
    }
    static Connection connection(List<String> names, long count) {
        return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
            case "prepareStatement" -> {
                String query = (String) a[0];
                if (!query.startsWith("SELECT table_name FROM information_schema.tables")) throw new AssertionError(query);
                sql.add(query);
                yield proxy(PreparedStatement.class, (sp, sm, sa) -> switch (sm.getName()) {
                    case "setString", "close" -> null;
                    case "executeQuery" -> rows(names, null);
                    default -> throw new AssertionError("Unexpected PreparedStatement call: " + sm.getName());
                });
            }
            case "createStatement" -> proxy(Statement.class, (sp, sm, sa) -> switch (sm.getName()) {
                case "executeQuery" -> {
                    String query = (String) sa[0];
                    if (!query.matches("SELECT COUNT[(][*][)] FROM .[a-z_]+.")) throw new AssertionError(query);
                    sql.add(query); yield rows(List.of(), count);
                }
                case "close" -> null;
                default -> throw new AssertionError("Unexpected Statement call: " + sm.getName());
            });
            default -> throw new AssertionError("Unexpected Connection call: " + m.getName());
        });
    }
    static void emptyGuard(List<String> names, long count) throws Throwable {
        invoke("requireEmptyTables", new Class<?>[]{Connection.class}, connection(names, count));
    }
    public static void main(String[] args) throws Throwable {
        switch (args[0]) {
            case "retained" -> {
                denied(() -> E2eDatabase.main(new String[]{"clean"}), "cannot be cleaned or reset");
                denied(() -> E2eDatabase.main(new String[]{"reset"}), "cannot be cleaned or reset");
                denied(() -> invoke("clean", new Class<?>[]{}), "cannot be cleaned or reset");
                invoke("validateOperation", new Class<?>[]{String.class}, "seed");
                emptyGuard(List.of("user", "community_post"), 0);
                denied(() -> emptyGuard(List.of("user", "community_post"), 1), "non-empty");
                denied(() -> emptyGuard(List.of(), 0), "prepared E2E tables");
                denied(() -> emptyGuard(List.of("user;drop_table"), 0), "Unexpected E2E table name");
                if (sql.size() != 7) throw new AssertionError("Expected only read-only guard queries: " + sql);
            }
            case "default" -> {
                for (String mode : List.of("clean", "reset", "prepare", "verify", "snapshot")) {
                    invoke("validateOperation", new Class<?>[]{String.class}, mode);
                }
                denied(() -> E2eDatabase.main(new String[]{"seed"}), "explicit E2E retention mode");
            }
            case "invalid" -> denied(() -> E2eDatabase.main(new String[]{"clean"}), "Invalid E2E retention setting");
            default -> throw new AssertionError("Unknown probe");
        }
        System.out.println("RETENTION_SAFETY_OK " + args[0]);
    }
}
`;

for (const mode of ['retained', 'default', 'invalid']) {
  test(`Java guard without datasource: ${mode}`, { skip: process.env.E2E_JAVA_SAFETY_CHECKS !== '1' }, () => {
    const root = path.resolve(__dirname, '../..');
    const parent = path.join(root, 'backend/build/e2e-retention-safety');
    mkdirSync(parent, { recursive: true });
    const directory = mkdtempSync(path.join(parent, 'probe-'));
    const source = path.join(directory, 'RetentionSafetyProbe.java');
    writeFileSync(source, javaProbe);
    const classpath = readFileSync(path.join(root, 'backend/build/e2e/classpath.txt'), 'utf8');
    const environment = { ...safeEnvironment(process.env), E2E_DATABASE: 'kkiri_e2e_0123456789ab' };
    if (mode !== 'default') environment.E2E_RETAIN_RESOURCES = mode === 'retained' ? '1' : 'false';
    const result = spawnSync('java', ['--class-path', classpath, source, mode], {
      env: environment, encoding: 'utf8', timeout: 30_000, windowsHide: true,
    });
    assert.equal(result.status, 0, result.error?.message || result.stderr);
    assert.ok(result.stdout.includes('RETENTION_SAFETY_OK ' + mode));
  });
}
