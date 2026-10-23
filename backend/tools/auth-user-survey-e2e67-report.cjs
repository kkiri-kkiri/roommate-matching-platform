// Read-only aggregation of the explicit AUS E2E evidence directories. Never starts tests or DB resources.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const base = path.join(root, 'backend/build/full-service-e2e');
const ids = process.argv.slice(2);
assert(ids.length && ids.every(id => /^[a-f0-9]{12}$/.test(id)), 'Pass only explicit owned run IDs');
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const evidence = [], latest = new Map();
const read = (directory, name) => JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8'));
const inventory = directory => {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    assert(!entry.isSymbolicLink(), 'Evidence links are not followed');
    if (entry.isDirectory()) inventory(file);
    else evidence.push({ path: path.relative(root, file).replaceAll('\\', '/'), sha256: sha(fs.readFileSync(file)) });
  }
};
const signature = r => `${r.method} ${r.path} ${r.status}`;
let attempts = 0;
const runs = ids.map(id => {
  const directory = path.join(base, id), run = read(directory, 'run.json');
  assert.equal(run.id, id); assert.equal(run.scope, 'AUS_P0_ONLY');
  assert(run.ownedProcessesStopped && run.sourceUnchangedDuringRun && !run.evidenceErrors.length);
  if (run.containerId) assert(run.containerRemoved && run.fixtureTablesEmpty);
  const result = fs.existsSync(path.join(directory, 'results.json')) ? read(directory, 'results.json') : { tests: [], counts: { executed: 0 } };
  const browser = fs.readdirSync(directory).filter(n => /^browser-.+\.json$/.test(n))
    .flatMap(n => read(directory, n).requests);
  const serverFile = path.join(directory, 'server-http.jsonl');
  const server = fs.existsSync(serverFile) ? fs.readFileSync(serverFile, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse) : [];
  const difference = new Map();
  for (const record of server) difference.set(signature(record), (difference.get(signature(record)) || 0) + 1);
  for (const record of browser) difference.set(signature(record), (difference.get(signature(record)) || 0) - 1);
  for (const test of result.tests) {
    const caseId = /login\.spec\.cjs$/.test(test.file) ? 'B2' : test.name.match(/^(B[1-9]) P0:/)?.[1];
    assert(caseId, 'Unexpected test outside approved P0');
    assert(test.attempts.length === 1, 'Automatic retries were not authorized');
    attempts++;
    latest.set(caseId, { id: caseId, priority: 'P0', status: test.attempts[0].status === 'passed' ? 'PASS' : 'FAIL',
      title: test.name, run: id, evidence: `backend/build/full-service-e2e/${id}/results.json` });
  }
  if (run.recheck) {
    assert.equal(sha(fs.readFileSync(path.join(base, run.recheck.source, 'results.json'))), run.recheck.resultsSha256);
  }
  inventory(directory);
  return { id, startedAt: run.startedAt, finishedAt: run.finishedAt, counts: result.counts,
    browserHttpResponses: browser.length, serverCompletedHttp: server.length,
    serverMinusBrowser: Object.fromEntries([...difference].filter(([, count]) => count)),
    container: run.preflight?.container ?? null, containerId: run.containerId ?? null,
    containerRemoved: run.containerRemoved ?? false, fixtureTablesEmpty: run.fixtureTablesEmpty,
    ownedProcessesStopped: run.ownedProcessesStopped, recheck: run.recheck ?? null };
});
const cases = [...latest.values()].sort((a, b) => Number(a.id.slice(1)) - Number(b.id.slice(1)));
assert.equal(cases.length, 9, 'Nine P0 results are required');
const auditFile = path.join(base, 'aus67-final-audit-20261023.json');
const audit = JSON.parse(fs.readFileSync(auditFile, 'utf8'));
assert(audit.containers.every(c => c.absent) && audit.ports.every(p => p.free));
assert.equal(audit.artifactScan.generatedCredentialPatternMatches.length, 0);
const originalSources = read(path.join(base, 'bc2479b6524d'), 'source-before.json').files;
const allowedFrontendChanges = new Set(['frontend/src/api/authApi.js', 'frontend/src/api/authApi.test.js',
  'frontend/src/pages/auth/SignupPage.js', 'frontend/src/pages/auth/AuthLoading.test.js']);
const protectedSources = Object.keys(originalSources).filter(file =>
  (file.startsWith('backend/src/main/') || file.startsWith('frontend/src/')) && !allowedFrontendChanges.has(file));
for (const file of protectedSources) assert.equal(sha(fs.readFileSync(path.join(root, file))), originalSources[file], 'Protected source changed: ' + file);
evidence.push({ path: path.relative(root, auditFile).replaceAll('\\', '/'), sha256: sha(fs.readFileSync(auditFile)) });
const report = {
  date: '2026-10-23', timezone: 'Asia/Seoul', scope: 'Auth/User/Survey P0 only; P1 not executed',
  finalStatus: cases.every(c => c.status === 'PASS') ? 'PASS' : 'FAIL',
  p0: { pass: cases.filter(c => c.status === 'PASS').length, fail: cases.filter(c => c.status === 'FAIL').length, blocked: 0, notRun: 0 },
  p1: { pass: 0, fail: 0, blocked: 0, notRun: 6, ids: ['B10', 'B11', 'B12', 'B13', 'B14', 'B15'], executionApproved: false },
  browserTestAttempts: attempts,
  browserHttpResponses: runs.reduce((sum, run) => sum + run.browserHttpResponses, 0),
  serverCompletedHttp: runs.reduce((sum, run) => sum + run.serverCompletedHttp, 0),
  httpCountDefinition: 'Counts API requests excluding OPTIONS. Browser response events and server completions are separate observations, never added together. Includes automatic UI queries and fixture login HTTP.',
  cases, runs,
  finalResourceAudit: audit,
  protectedSources: { files: protectedSources.length, unchanged: true,
    definition: 'All backend/src/main and frontend/src except the four explicit signup fix/regression files' },
  frontendDefects: [{ id: 'expired-signup-verification-lock', firstRun: 'bc2479b6524d', case: 'B3',
    evidence: 'Actual signup 400 EMAIL_VERIFICATION_TOKEN_INVALID, no account created, verification button remains unavailable. SignupPage catch retained isVerified=true.',
    fix: 'Preserve signup errorCode in authApi and clear verification state only for EMAIL_VERIFICATION_TOKEN_INVALID. Preserve the signup draft.' }],
  runnerErrors: [
    { run: '8f703b4e60ae', kind: 'Docker named-pipe permission', tests: 0, http: 0, resourceCreated: false },
    { run: 'bc2479b6524d', cases: ['B4', 'B5'], kind: 'Selector matched outer section and four edit buttons', fix: 'Select nearest section of the exact heading' },
    { run: 'bc2479b6524d', cases: ['B8'], kind: 'Runner expected DTO from existing Void PUT', fix: 'Compare submitted preference values with independent JDBC and subsequent GET; preserve API contract' },
    { run: 'bac96aefd975', kind: 'Anchored grep did not match Playwright full test title', tests: 0, http: 0, resourceCreated: true, resourceRemoved: true,
      fix: 'Correct grep and require exact --list test count before resource creation' },
    { kind: 'New Jest UI regression exceeded default 5-second limit', finalDurationMs: 5312, fix: '20-second limit for this form-completion test; rerun passed' },
  ],
  checks: { e2eJavaCompilation: 'PASS prepareE2e', contract: 'PASS 73 endpoints / 75 record DTOs / 30 entities',
    frontendRegression: { uniqueTests: 29, finalPassed: 29, attempts: 34, initialTimeouts: 1 }, runnerSafety: '6 PASS',
    frontendBuild: 'PASS', backendTestBootJar: 'BUILD SUCCESSFUL; test and bootJar UP-TO-DATE, no fresh H2 execution' },
  limits: ['No real SMTP, AI, school network, HTTPS/ingress or production verification',
    'No other-domain E2E cases executed; automatic homepage queries are included in HTTP counts only',
    'No P1 browser execution or full repetition of previously passed P0 cases',
    'Stateless logout contract unchanged; retained-token/socket server revocation is outside this scope'],
  evidence,
};
const output = path.join(root, 'backend/validation/auth-user-survey-e2e67-20261023.json');
fs.writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({ output: path.relative(root, output), status: report.finalStatus, p0: report.p0,
  browserTestAttempts: attempts, browserHttpResponses: report.browserHttpResponses, serverCompletedHttp: report.serverCompletedHttp, evidenceFiles: evidence.length }));
