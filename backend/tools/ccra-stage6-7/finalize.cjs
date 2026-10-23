const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { assertFree } = require('../../../frontend/e2e/run.cjs');
const root = path.resolve(__dirname, '../../..');
const owner = '82cdabce6030';
const ids = [owner, 'c6f4e65335f7', '81b2aaea5032', '5fa01fae60e4'];
const directory = id => path.join(root, 'backend/build/full-service-e2e', id);
const json = (id, file) => JSON.parse(fs.readFileSync(path.join(directory(id), file), 'utf8'));
const runs = ids.map(id => ({ run: json(id, 'run.json'), summary: json(id, 'ccra-p0-summary.json') }));
for (const { run } of runs) if ((run.parentRunId || run.id) !== owner || run.database !== `kkiri_e2e_${owner}`
  || run.containerRemoved !== false || run.retention?.enabled !== true || !run.ownedProcessesStopped) throw new Error('Unexpected run identity/resource state');
const selected = { '01': ids[2], '02': ids[2], '03': owner, '04': ids[2], '05': owner, '06': owner, '07': ids[3], '08': owner };
const cases = Object.entries(selected).map(([number, id]) => {
  const item = runs.find(r => r.run.id === id).summary.cases.find(c => c.name.startsWith('P0-' + number));
  if (!item || item.attempts.length !== 1) throw new Error('Missing final case');
  return { ...item, number, evidenceRun: id, finalStatus: item.attempts[0].status === 'passed' ? 'PASS' : 'FAIL' };
});
const total = key => runs.reduce((sum, r) => sum + (r.summary[key] || 0), 0);
async function main() {
  await assertFree(13000); await assertFree(18080);
  // Never enumerate or inspect any other Docker resource.
  const inspected = spawnSync('docker', ['inspect', '--format',
    '{{.Name}}|{{index .Config.Labels "kkiri.e2e.owner"}}|{{.State.Status}}|{{.Config.Image}}', `kkiri-e2e-${owner}`],
  { encoding: 'utf8', windowsHide: true });
  if (inspected.status !== 0) throw new Error('Owned container state inspection failed');
  const fields = inspected.stdout.trim().split('|');
  if (fields[0] !== '/kkiri-e2e-' + owner || fields[1] !== owner || fields[2] !== 'running') throw new Error('Owned container state mismatch');
  const snapshots = fs.readdirSync(directory(ids[3])).filter(file => /^database-.*jsonl$/.test(file)).flatMap(file =>
    fs.readFileSync(path.join(directory(ids[3]), file), 'utf8').trim().split(/\r?\n/).map(JSON.parse)).filter(row => row.snapshot);
  const last = snapshots.sort((a, b) => a.time.localeCompare(b.time)).at(-1);
  const final = { date: '2026-10-23', timezone: 'Asia/Seoul', owner, database: `kkiri_e2e_${owner}`,
    logicalResults: { executed: 8, passed: cases.filter(c => c.finalStatus === 'PASS').length, failed: cases.filter(c => c.finalStatus === 'FAIL').length },
    evidenceTotals: { playwrightAttempts: runs.reduce((sum, r) => sum + r.summary.counts.executed, 0),
      browserApiResponses: total('httpResponses'), backendApiResponses: total('backendHttpResponses'),
      injected503Responses: total('injectedHttpResponses'), databaseSnapshots: total('databaseSnapshots') },
    runs: runs.map(({ run, summary }) => ({ id: run.id, parentRunId: run.parentRunId, counts: summary.counts,
      httpResponses: summary.httpResponses, backendHttpResponses: summary.backendHttpResponses,
      databaseSnapshots: summary.databaseSnapshots, httpByStatus: summary.httpByStatus,
      stompByDirectionAndCommand: summary.stompByDirectionAndCommand, sourceUnchanged: run.sourceUnchangedDuringRun,
      fixtureActorsAdded: run.fixtureActorsAdded || (run.id === owner ? 3 : 0) })),
    cases, resources: { container: fields[0].slice(1), state: fields[2], image: fields[3], serverUuid: runs[0].run.retention.serverUuid,
      testAccount: runs[0].run.retention.account, containerRemoved: false, appProcessesStopped: true, ports13000And18080Free: true,
      imageDirectory: runs[0].run.retention.imageDirectory, lastSnapshotAt: last.time,
      rows: Object.fromEntries(Object.entries(last.snapshot).map(([table, rows]) => [table, rows.length])) },
    productionCodeChanged: false, p1Executed: false,
    limitations: ['Counts include fixture login and background reads; injected 503 responses are subtracted from backend API responses.',
      'Application fixture users accumulate without cleanup; they are not additional MySQL accounts.',
      'Original failed attempts and the zero-test grep failure remain preserved; final logical results select the evidenced completed attempt.',
      'STOMP delivery, REST polling recovery and DB commits were verified independently. WSS/proxy/durable storage are not verified.'] };
  const output = path.join(root, 'backend/validation/ccra-stage6-7-p0-20261023.json');
  fs.writeFileSync(output, JSON.stringify(final, null, 2));
  fs.writeFileSync(path.join(directory(owner), 'resource-final.json'), JSON.stringify(final.resources, null, 2));
  console.log(JSON.stringify({ logicalResults: final.logicalResults, evidenceTotals: final.evidenceTotals,
    resources: final.resources, cases: cases.map(c => ({ number: c.number, status: c.finalStatus })) }, null, 2));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
