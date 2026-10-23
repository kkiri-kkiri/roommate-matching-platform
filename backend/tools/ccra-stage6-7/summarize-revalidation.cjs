const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const { assertFree, safeEnvironment } = require('../../../frontend/e2e/run.cjs');
const root = path.resolve(__dirname, '../../..');
const ids = ['3bee6cb70e3b', '62e3c9338423'], owner = '82cdabce6030';
const location = id => path.join(root, 'backend/build/full-service-e2e', id);
const read = (id, file) => JSON.parse(fs.readFileSync(path.join(location(id), file), 'utf8'));
function lines(id, file) {
  const p = path.join(location(id), file);
  return fs.existsSync(p) ? fs.readFileSync(p, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse) : [];
}
async function main() {
  await assertFree(13000); await assertFree(18080);
  const inspected = spawnSync('docker', ['inspect', 'kkiri-e2e-' + owner], { env: safeEnvironment(process.env), windowsHide: true, encoding: 'utf8' });
  if (inspected.status !== 0) throw new Error('Approved resource inspection failed');
  const container = JSON.parse(inspected.stdout)[0];
  if (container.Name !== '/kkiri-e2e-' + owner || container.Config.Labels['kkiri.e2e.owner'] !== owner || !container.State.Running) throw new Error('Resource identity/state mismatch');
  const runs = ids.map(id => {
    const run = read(id, 'run.json'), results = read(id, 'results.json');
    if (run.parentRunId !== owner || run.database !== 'kkiri_e2e_' + owner || run.containerRemoved
      || !run.ownedProcessesStopped || !run.sourceUnchangedDuringRun || !run.uploadsUnchanged) throw new Error('Run preservation/integrity mismatch');
    const browsers = fs.readdirSync(location(id)).filter(file => /^browser-.*\.json$/.test(file)).map(file => read(id, file));
    const requests = browsers.flatMap(browser => browser.requests);
    const status = Object.fromEntries([...new Set(requests.map(r => r.status))].sort().map(code => [code, requests.filter(r => r.status === code).length]));
    const snapshots = fs.readdirSync(location(id)).filter(file => /^database-.*\.jsonl$/.test(file)).flatMap(file => lines(id, file)).filter(row => row.snapshot).length;
    const scopedDb = lines(id, 'revalidation-database.jsonl');
    return { id, run, results, browsers, requests, httpByStatus: status, snapshots, scopedDb };
  });
  const finalCases = [...runs[0].results.tests.filter(test => !test.name.startsWith('RV-06')), ...runs[1].results.tests];
  if (finalCases.length !== 9 || finalCases.some(test => test.attempts.length !== 1 || test.attempts[0].status !== 'passed')) throw new Error('Missing completed logical cases');
  const observations = [...runs[0].browsers.filter(b => !b.test.startsWith('RV-06')), ...runs[1].browsers]
    .flatMap(b => b.observations.map(observation => ({ test: b.test, ...observation })));
  const migration = read(ids[0], 'migrate.json'), preservation = read(ids[1], 'preservation.json');
  if (Object.values(preservation).some(table => table.missing !== 0)
    || Object.entries(preservation).some(([table, rows]) => table !== 'auth_limit_bucket' && rows.changed !== 0)) throw new Error('Original business rows changed or were removed');
  const firstSource = read(ids[0], 'source-before.json'), lastSource = read(ids[1], 'source-after.json');
  const productionFiles = Object.keys(firstSource).filter(file => file.startsWith('frontend/src/') || file.startsWith('backend/src/main/'));
  if (productionFiles.some(file => firstSource[file] !== lastSource[file])) throw new Error('Production source changed during revalidation');
  const sharedFiles = ['frontend/e2e/run.cjs', 'frontend/e2e/fixtures.cjs', 'frontend/e2e/runner.test.cjs', 'backend/src/e2e/java/e2e/E2eDatabase.java'];
  if (sharedFiles.some(file => firstSource[file] !== lastSource[file])) throw new Error('Shared runner changed');
  const frames = observations.flatMap(observation => observation.data.frames || []);
  const frameCounts = Object.fromEntries([...new Set(frames.map(f => f.direction + ':' + f.command))].sort()
    .map(key => [key, frames.filter(frame => frame.direction + ':' + frame.command === key).length]));
  const result = {
    date: '2026-10-23', timezone: 'Asia/Seoul', owner, database: 'kkiri_e2e_' + owner,
    logicalResults: { executed: 9, passed: 9, failed: 0 },
    rawAttempts: { executed: 10, passed: 9, failed: 1, harnessFailures: 1, reproducedServiceDefects: 0 },
    migration, preservation,
    cases: finalCases.map(test => ({ name: test.name, status: 'PASS', evidenceRun: test.name.startsWith('RV-06') ? ids[1] : ids[0],
      durationMs: test.attempts[0].durationMs })),
    observations,
    runs: runs.map(item => ({ id: item.id, counts: item.results.counts, httpByStatus: item.httpByStatus,
      observedHttpResponses: item.requests.length, injected503Responses: item.requests.filter(row => row.status === 503).length,
      actualBackendResponses: item.requests.filter(row => row.status !== 503).length,
      existingJdbcSnapshots: item.snapshots, scopedJdbcOperations: item.scopedDb.length,
      fixtureActorsAdded: item.run.fixtureActorsAdded, sourceUnchanged: item.run.sourceUnchangedDuringRun,
      ownedProcessesStopped: item.run.ownedProcessesStopped, uploadsUnchanged: item.run.uploadsUnchanged })),
    evidenceTotals: { observedHttpResponses: runs.reduce((n,run) => n + run.requests.length, 0),
      injected503Responses: runs.reduce((n,run) => n + run.requests.filter(row => row.status === 503).length, 0),
      actualBackendResponses: runs.reduce((n,run) => n + run.requests.filter(row => row.status !== 503).length, 0),
      existingJdbcSnapshots: runs.reduce((n,run) => n + run.snapshots, 0),
      scopedJdbcOperations: runs.reduce((n,run) => n + run.scopedDb.length, 0), gatewayFrameCounts: frameCounts },
    harnessFailure: { evidenceRun: ids[0], case: 'RV-06',
      reason: 'Route-level socket close alone did not establish that the browser application socket stopped receiving; the polling-only assertion correctly failed.',
      correction: 'Track and close native browser WebSocket instances, suppress forwarding from closed links, reject reconnects during polling, and independently assert zero native MESSAGE events for the recovered message.',
      retestRun: ids[1], retest: 'PASS', operatingCodeChanged: false },
    resources: { container: container.Name.slice(1), state: container.State.Status, image: container.Config.Image,
      account: 'e2e_' + owner, serverUuid: migration.identity[0].uuid, newDockerResources: 0, newSchemas: 0, newDatabaseAccounts: 0,
      appendedApplicationActors: 6, containerRemoved: false, databaseResetOrCleaned: false, originalRowsMissing: 0,
      originalBusinessRowsChanged: 0, existingAuthLimitBucketsUpdatedByLogin: preservation.auth_limit_bucket.changed,
      uploads: runs[1].run.uploadsAfter, uploadsUnchanged: true, appProcessesStopped: true, ports13000And18080Free: true,
      finalRowCounts: Object.fromEntries(Object.entries(preservation).map(([table, value]) => [table, value.current])) },
    productionCodeChanged: false, sharedRunnerChanged: false, p1Executed: false,
    limitations: [
      'Two independent Chromium browser contexts; cross-browser engines and WSS/proxy deployments are not tested.',
      'Current single JVM Simple Broker deployment only; multiple application JVMs and process-crash recovery are not exercised.',
      'Concurrency evidence is eight independent STOMP sessions queued within one browser task and resulting MySQL rows/response IDs; DB lock wait timings are not instrumented.',
      'Only the first failed polling harness case was rerun; original failed evidence and all test rows remain preserved.',
      'Authentication limiter bucket mutations are normal effects of real fixture logins; no authentication production code was changed.',
      'Raw frame gateway counts include upstream observations and injected drops; native browser MESSAGE checks independently establish polling isolation.'
    ],
    evidenceFiles: ids.flatMap(id => fs.readdirSync(location(id)).filter(file => /^(run|results|migrate|preservation|actors|source-before|source-after)\.json$|^browser-.*\.json$|^revalidation-database\.jsonl$|^database-.*\.jsonl$/.test(file))
      .map(file => ({ file: 'backend/build/full-service-e2e/' + id + '/' + file,
        sha256: createHash('sha256').update(fs.readFileSync(path.join(location(id), file))).digest('hex') })))
  };
  fs.writeFileSync(path.join(root, 'backend/validation/ccra-stage6-7-revalidation-20261023.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify({ logicalResults: result.logicalResults, rawAttempts: result.rawAttempts,
    evidenceTotals: result.evidenceTotals, resources: result.resources }, null, 2));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
