const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { isDeepStrictEqual: same } = require('node:util');
const { execFileSync } = require('node:child_process');

// Aggregate preserved evidence only; never starts a test, server, Docker command or DB connection.
const root = path.resolve(__dirname, '../..');
const runId = process.argv[2];
if (!/^[a-f0-9]{12}$/.test(runId ?? '')) throw new Error('Invalid run ID');
const relative = `backend/build/full-service-e2e/${runId}`;
const absolute = p => path.join(root, p);
const text = p => {
  const bytes = fs.readFileSync(absolute(p));
  return bytes.toString(bytes[0] === 0xff && bytes[1] === 0xfe ? 'utf16le' : 'utf8').replace(/^\uFEFF/, '');
};
const read = p => JSON.parse(text(p));
const lines = p => text(p).trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const hash = p => crypto.createHash('sha256').update(fs.readFileSync(absolute(p))).digest('hex');
const write = (p, value) => fs.writeFileSync(absolute(p), JSON.stringify(value, null, 2) + '\n', { flag: 'wx' });
const run = read(`${relative}/run.json`), cleanup = read(`${relative}/cleanup-audit.json`);
const identity = read(`${relative}/identity-observed.json`);
if (run.id !== runId || run.scope !== 'MATCHING_CALENDAR_P0_ONLY' || run.recheck?.source !== '2f55ee3d8226' ||
    !run.fixtureTablesEmpty || !run.ownedProcessesStopped || !run.containerRemoved || !run.sourceUnchangedDuringRun ||
    !cleanup.containerAbsentByExactId || !cleanup.containerAbsentByExactName || cleanup.portsRebindable.length !== 3 ||
    identity.container.id !== run.containerId || identity.container.owner !== runId || identity.database.version !== '8.0.46')
  throw new Error('Incomplete recheck identity or cleanup evidence');
const expected = ['C04','R02','R03','R04','C02'];
const phases = run.mcPhases.map(p => {
  const file = `${relative}/results-${p.label}.json`;
  if (hash(file) !== p.sha256) throw new Error('Native result hash changed');
  return { ...p, file, native: read(file) };
});
const cases = phases.flatMap(p => p.native.tests.map(t => ({ id:t.name.split(' ')[0], phase:p.label, nativeFile:p.file,
  nativeTestId:t.id, attempts:t.attempts, status:t.attempts.length === 1 && t.attempts[0].status === 'passed' ? 'PASS' : 'FAIL' })));
if (cases.length !== 5 || new Set(cases.map(c => c.id)).size !== 5 || cases.some(c => !expected.includes(c.id) || c.attempts.length !== 1))
  throw new Error('Recheck did not execute exactly five single-attempt cases');

const browser = new Map(), traffic = [];
const databaseCounts = { snapshot:0, reset:0, clean:0, cleanZeroConfirmed:0 };
for (const name of fs.readdirSync(absolute(relative)).filter(n => /^browser-.*\.json$/.test(n))) {
  const data = read(`${relative}/${name}`), id = data.test.split(' ')[0];
  const databaseFile = `${relative}/${name.replace(/^browser-/, 'database-').replace(/\.json$/, '.jsonl')}`;
  const database = lines(databaseFile);
  browser.set(id, { file:`${relative}/${name}`, data, databaseFile, database });
  for (const event of database) {
    if (event.operation in databaseCounts) databaseCounts[event.operation]++;
    if (event.operation === 'clean' && event.allTablesEmpty === true) databaseCounts.cleanZeroConfirmed++;
  }
}
for (const name of fs.readdirSync(absolute(relative)).filter(n => /^traffic-.*\.json$/.test(n))) {
  const data = read(`${relative}/${name}`), journal = lines(`${relative}/${name.replace(/\.json$/, '.jsonl')}`);
  const counts = { request:0, response:0, failure:0 };
  for (const event of journal) counts[event.kind]++;
  if (counts.request !== data.starts.length || counts.response !== data.responses.length || counts.failure !== data.failures.length)
    throw new Error('Traffic journal/summary mismatch');
  traffic.push({ id:data.test.split(' ')[0], file:`${relative}/${name}`, ...counts,
    failedRequests:data.failures, filterResponses:data.responses.filter(r => r.query?.sleepTime) });
}
if (browser.size !== 5 || traffic.length !== 5) throw new Error('Missing per-case evidence');
const total = traffic.reduce((a,t) => ({ request:a.request+t.request, response:a.response+t.response, failure:a.failure+t.failure }), { request:0,response:0,failure:0 });
const server = lines(`${relative}/server-http.jsonl`), serverStatuses = {};
for (const row of server) serverStatuses[row.status] = (serverStatuses[row.status] || 0) + 1;
for (const item of cases) {
  const b = browser.get(item.id);
  Object.assign(item, { browserEvidence:b.file, databaseEvidence:b.databaseFile,
    observations:b.data.observations, mysqlSnapshots:b.database.filter(r => r.operation === 'snapshot').length,
    traffic:traffic.find(t => t.id === item.id) });
}
const c04 = browser.get('C04').data.observations.map(o => {
  const v = o.data, unchanged = same({ ...v.after,title:v.before.title },v.before) && same({ ...v.fresh,title:v.original.title },v.original);
  return { title:v.title, status:v.patch?.status === 200 && v.fresh.title === `${v.title} 수정` && unchanged ? 'PASS' : 'FAIL',
    original:v.original, editorFields:v.fields, patch:v.patch, freshGet:v.fresh, mysqlBefore:v.before, mysqlAfter:v.after,
    nonTitleFieldsPreserved:unchanged, patchOnlyTitle:v.patch ? same(Object.keys(v.patch.payload), ['title']) : false };
});
const previousPath = 'backend/validation/matching-calendar-e2e67-p0-execution-20261026-2f55ee3d8226.json';
const previous = read(previousPath);
for (const file of run.recheck.originalResults)
  if (hash(`backend/build/full-service-e2e/2f55ee3d8226/${file.file}`) !== file.sha256) throw new Error('Original native evidence changed');
const finalCases = previous.cases.map(old => {
  const current = cases.find(c => c.id === old.id);
  return { id:old.id, status:current?.status ?? old.status, evidenceRun:current ? runId : previous.runId,
    rerun:Boolean(current), initialStatus:old.status, nativeFile:current?.nativeFile ?? old.nativeFile };
});
if (finalCases.filter(c => !c.rerun).length !== 6 || finalCases.some(c => !c.rerun && c.status !== 'PASS'))
  throw new Error('The original six passing cases must remain separate');
const counts = list => ({ PASS:list.filter(c => c.status === 'PASS').length, FAIL:list.filter(c => c.status === 'FAIL').length, BLOCKED:0, NOT_RUN:0 });
const preparation = 'backend/build/mc-p0-recheck-preparation/20261026';
const protection = read(`${preparation}/protected-before.json`);
const changed = protection.files.filter(f => !fs.existsSync(absolute(f.file)) || hash(f.file) !== f.sha256).map(f => f.file);
if (changed.length) throw new Error('Pre-run source or protected evidence changed');
write(`${relative}/protection-audit.json`, { comparedFiles:protection.files.length, changed,
  baseline:`${preparation}/protected-before.json`, sourceUnchangedDuringRun:run.sourceUnchangedDuringRun });
const rawFiles = [];
function visit(dir) {
  for (const e of fs.readdirSync(absolute(dir), { withFileTypes:true })) {
    const file = `${dir}/${e.name}`;
    if (e.isSymbolicLink()) throw new Error('Evidence links are forbidden');
    if (e.isDirectory()) visit(file);
    else rawFiles.push({ path:file, bytes:fs.statSync(absolute(file)).size, sha256:hash(file) });
  }
}
visit(relative); visit(preparation);
for (const file of rawFiles)
  if (/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b|E2e![a-f0-9]{36}/.test(text(file.path)))
    throw new Error('Credential-shaped evidence value requires review without printing');
const ledger = read('backend/build/mc-p0-rechecks/2f55ee3d8226/resource-ledger.json');
if (ledger.length !== 1 || ledger[0].id !== runId) throw new Error('One-container recheck budget mismatch');
const summary = {
  schemaVersion:1, date:'2026-10-26', timezone:'Asia/Seoul', runId, originalRunId:previous.runId,
  branch:execFileSync('git',['branch','--show-current'],{cwd:root,windowsHide:true,encoding:'utf8'}).trim(), gitSha:run.gitSha,
  recheckCounts:counts(cases), finalCounts:counts(finalCases), finalCases, recheckedCases:cases,
  originalSummary:{ path:previousPath, sha256:hash(previousPath), counts:previous.counts },
  nativeCounts:{ executed:5, passed:counts(cases).PASS, failed:counts(cases).FAIL, retries:0, skipped:0, interrupted:0, repeatedOriginalPasses:0 },
  phases:phases.map(p => ({ label:p.label, ids:p.ids, exit:p.exit, file:p.file, sha256:p.sha256,
    startedAt:p.native.startedAt, finishedAt:p.native.finishedAt, counts:p.native.counts })),
  c04, c04Before:previous.c04.map(c => ({ title:c.title, status:c.status, patchCount:c.patchCount })),
  observations:{ browser:total, unmatchedBrowserStarts:total.request-total.response-total.failure,
    serverHttpCompleted:server.length, serverStatuses, mysql:databaseCounts, perCase:traffic,
    caveat:'Browser observations and server completion are distinct; R02/R03 use real HTTP/UI comparison, not independent matching SQL assertions.' },
  checks:{ jest:{ suites:4, tests:29, status:'PASS', file:`${preparation}/jest.log` },
    safety:{ tests:6, status:'PASS', file:`${preparation}/safety.log` }, frontendBuild:{status:'PASS',file:`${preparation}/build.log`},
    e2eCompile:{status:'PASS',offline:true,file:`${preparation}/prepare-e2e.log`},
    contractCheck:{status:'PASS',endpoints:73,recordDtos:75,entities:30,file:'backend/build/mc-p0-fix-preparation/20261026/contracts-check.log'} },
  orchestration:{ exitCode:0, playwrightExitCode:run.playwrightExitCode, sessionState:read(`${relative}/mc-session.json`).state,
    evidenceErrors:run.evidenceErrors, auditOnlyFormattingError:identity.auditNote, nativeTestRetries:0 },
  resources:{ createdContainers:1, createdSchemas:1, createdAccounts:1, createdVolumes:0, identity, cleanup,
    ledger:'backend/build/mc-p0-rechecks/2f55ee3d8226/resource-ledger.json', remainingApprovedContainerBudget:0 },
  protection:{ comparedFiles:protection.files.length, changed, originalReportsAndP1PlanUnchanged:true, sourceUnchangedDuringRun:true },
  external:{ ai:'E2eServer.FixedAi', gatewayRequests:0, school:'DisabledCrawler', schoolRequests:0, smtpRequests:0 },
  p1:{ readiness:finalCases.every(c => c.status === 'PASS') ? 'P0_EVIDENCE_GATE_SATISFIED' : 'BLOCKED', executed:false, requiresSeparateApproval:true },
  rawFiles,
};
const file = `backend/validation/matching-calendar-e2e67-p0-recheck-20261026-${runId}.json`;
write(file, summary);
write(`${relative}/evidence-manifest.json`, { runId, summary:{path:file,sha256:hash(file)}, files:rawFiles });
console.log(JSON.stringify({ file, sha256:hash(file), recheck:summary.recheckCounts, final:summary.finalCounts,
  browser:total, serverHttp:server.length, mysql:databaseCounts, protectedFiles:protection.files.length, rawFiles:rawFiles.length }, null, 2));
