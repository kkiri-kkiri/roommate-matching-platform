const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

// Read preserved native evidence only. Never starts a server, browser, DB or Docker command.
const root = path.resolve(__dirname, '../..');
const runId = process.argv[2] || '2f55ee3d8226';
if (!/^[a-f0-9]{12}$/.test(runId)) throw new Error('Invalid run ID');
const relative = `backend/build/full-service-e2e/${runId}`;
const directory = path.join(root, relative);
const read = p => JSON.parse(fs.readFileSync(path.join(root, p), 'utf8').replace(/^\uFEFF/, ''));
const lines = p => fs.readFileSync(path.join(root, p), 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const hash = p => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, p))).digest('hex');
const write = (p, value) => fs.writeFileSync(path.join(root, p), value, { flag: 'wx' });
const json = (p, value) => write(p, JSON.stringify(value, null, 2) + '\n');
const run = read(`${relative}/run.json`);
const identity = read(`${relative}/resource-identity.json`);
const cleanup = read(`${relative}/cleanup-audit.json`);
if (run.scope !== 'MATCHING_CALENDAR_P0_ONLY' || run.id !== runId ||
    run.containerId !== identity.container.id || !run.containerRemoved ||
    !run.fixtureTablesEmpty || !run.ownedProcessesStopped ||
    !cleanup.containerAbsentByExactId || !cleanup.containerAbsentByExactName) {
  throw new Error('Unexpected run or incomplete cleanup');
}

const catalog = ['R01', 'R02', 'R03', 'R04', 'R05', 'R06', 'C01', 'C02', 'C03', 'C04', 'X01'];
const phases = run.mcPhases.map(phase => {
  const file = `${relative}/results-${phase.label}.json`;
  if (hash(file) !== phase.sha256) throw new Error('Native result hash changed');
  return { ...phase, file, native: read(file) };
});
const cases = phases.flatMap(p => p.native.tests.map(t => ({
  id: t.name.split(' ')[0], title: t.name, phase: p.label, nativeFile: p.file,
  nativeTestId: t.id, attempts: t.attempts,
  status: t.attempts.length === 1 && t.attempts[0].status === 'passed' ? 'PASS' : 'FAIL',
})));
if (cases.length !== 11 || new Set(cases.map(c => c.id)).size !== 11 ||
    cases.some(c => !catalog.includes(c.id) || c.attempts.length !== 1)) throw new Error('Unexpected native selection or retries');

const browser = new Map();
for (const name of fs.readdirSync(directory).filter(n => /^browser-.*\.json$/.test(n))) {
  const data = read(`${relative}/${name}`);
  const id = data.test.split(' ')[0];
  const databaseFile = `${relative}/${name.replace(/^browser-/, 'database-').replace(/\.json$/, '.jsonl')}`;
  browser.set(id, { file: `${relative}/${name}`, data, databaseFile, database: lines(databaseFile) });
}
const traffic = [];
for (const name of fs.readdirSync(directory).filter(n => /^traffic-.*\.json$/.test(n))) {
  const data = read(`${relative}/${name}`);
  const journal = lines(`${relative}/${name.replace(/\.json$/, '.jsonl')}`);
  const counts = { request: 0, response: 0, failure: 0 };
  for (const row of journal) counts[row.kind]++;
  if (counts.request !== data.starts.length || counts.response !== data.responses.length ||
      counts.failure !== data.failures.length) throw new Error('Traffic journal/summary mismatch');
  traffic.push({ id: data.test.split(' ')[0], file: `${relative}/${name}`, ...counts });
}
if (browser.size !== 11 || traffic.length !== 11) throw new Error('Incomplete browser evidence');
const totalTraffic = traffic.reduce((a, x) => ({ request: a.request + x.request,
  response: a.response + x.response, failure: a.failure + x.failure }), { request: 0, response: 0, failure: 0 });
const server = lines(`${relative}/server-http.jsonl`);
const serverStatuses = {};
for (const event of server) serverStatuses[event.status] = (serverStatuses[event.status] || 0) + 1;
const dbCounts = { snapshot: 0, reset: 0, clean: 0, cleanZeroConfirmed: 0 };
for (const item of browser.values()) for (const row of item.database) {
  if (row.operation in dbCounts) dbCounts[row.operation]++;
  if (row.operation === 'clean' && row.allTablesEmpty === true) dbCounts.cleanZeroConfirmed++;
}

const c04 = browser.get('C04').data.observations.map(o => {
  const v = o.data;
  const sameDbExceptTitle = JSON.stringify({ ...v.after, title: v.before.title }) === JSON.stringify(v.before);
  const sameDtoExceptTitle = JSON.stringify({ ...v.fresh, title: v.original.title }) === JSON.stringify(v.original);
  return { title: v.title, status: v.patch?.status === 200 && sameDbExceptTitle && sameDtoExceptTitle ? 'PASS' : 'FAIL',
    original: v.original, editorFields: v.fields, patchCount: v.patch ? 1 : 0,
    patch: v.patch, validationMessages: v.validation || [], freshGet: v.fresh,
    mysqlBefore: v.before, mysqlAfter: v.after, nonTitleFieldsPreserved: sameDbExceptTitle && sameDtoExceptTitle,
    classification: v.patch ? 'NORMAL_CONTROL' : 'FRONTEND_VALIDATION_BLOCKED_PATCH' };
});
if (c04.length !== 5 || c04.filter(c => c.status === 'PASS').length !== 1 ||
    c04.filter(c => c.patchCount === 0).length !== 4) throw new Error('Unexpected C04 evidence');
const c02Snapshot = browser.get('C02').database.find(r => r.operation === 'snapshot').snapshot;
const renamedSetting = c02Snapshot.categorySettings.find(s => s.display_name === 'MC새이름');
const category = c02Snapshot.categories.find(c => c.category_id === renamedSetting?.category_id);
if (category?.default_name !== 'MC새분류' || renamedSetting?.color !== '#abcdef' || renamedSetting?.is_enabled !== false)
  throw new Error('C02 stored values do not support the report');

const classifications = {
  R01: 'VERIFIED',
  R02: 'HARNESS_INTERACTION',
  R03: 'HARNESS_INTERACTION',
  R04: 'HARNESS_LOCATOR',
  R05: 'VERIFIED',
  R06: 'VERIFIED',
  C01: 'VERIFIED',
  C02: 'HARNESS_EXPECTATION',
  C03: 'VERIFIED',
  C04: 'FRONTEND_DEFECT',
  X01: 'VERIFIED',
};
// The report is Korean; retain concise machine classifications separately from native errors.
const reasons = {
  R01: '전체 목록 두 페이지·새 GET·fixture 후보 수 일치',
  R02: '시각적으로 숨긴 checkbox에 직접 check하여 pointer interception. 추천 빈 결과 분기 미완료',
  R03: '같은 숨김 checkbox 상호작용 실패. 필터 적용 이후 분기 미완료',
  R04: '배경 필터와 상세의 같은 문구가 strict locator에 중복. 이후 DB 비교 미완료',
  R05: '실제 분석 endpoint의 FixedAi 응답 표시·점수 보존',
  R06: '좋아요 등록·상세 취소·관심 7명→6명·MySQL 일치',
  C01: '개인 일정 생성·수정·삭제와 새 GET·MySQL 일치',
  C02: 'PUT 설정 저장 후 default_name 변경을 잘못 요구. display_name·색·표시는 DB에 저장됨',
  C03: '공개/개인 분리·편집 권한·타인 변경 403·DB 보존',
  C04: '정상 대조군 외 4종이 프론트 시간 검증에 막혀 PATCH 미발생',
  X01: 'A/B 전환·실제 8초 JWT 만료 401·재로그인 복구·데이터 격리',
};
for (const c of cases) {
  c.classification = classifications[c.id]; c.reason = reasons[c.id];
  c.browserEvidence = browser.get(c.id).file; c.databaseEvidence = browser.get(c.id).databaseFile;
  c.traffic = traffic.find(t => t.id === c.id);
  c.databaseOperations = browser.get(c.id).database.map(r => ({ operation: r.operation,
    ...(r.operation === 'clean' ? { allTablesEmpty: r.allTablesEmpty } : {}) }));
}

const preparation = 'backend/build/mc-p0-execution/20261026-bf7e4b48';
const protectionBefore = read(`${preparation}/protection-before.json`);
const changed = [];
for (const [p, beforeHash] of Object.entries(protectionBefore.protectedFiles))
  if (!fs.existsSync(path.join(root, p)) || hash(p) !== beforeHash) changed.push(p);
const branch = execFileSync('git', ['branch', '--show-current'], { cwd: root, windowsHide: true }).toString().trim();
const head = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, windowsHide: true }).toString().trim();
if (changed.length || branch !== protectionBefore.branch || head !== protectionBefore.gitSha)
  throw new Error('Protected source/evidence or branch changed');
json(`${relative}/protection-audit.json`, { checkedAt: new Date().toISOString(),
  comparedFiles: Object.keys(protectionBefore.protectedFiles).length, changed, branch, gitSha: head,
  baseline: `${preparation}/protection-before.json`, sourceUnchangedDuringRun: run.sourceUnchangedDuringRun });

const rawFiles = [];
function visit(d) {
  for (const e of fs.readdirSync(path.join(root, d), { withFileTypes: true })) {
    const p = `${d}/${e.name}`;
    if (e.isSymbolicLink()) throw new Error('Evidence links are not allowed');
    if (e.isDirectory()) visit(p);
    else rawFiles.push({ path: p, size: fs.statSync(path.join(root, p)).size, sha256: hash(p) });
  }
}
visit(relative);
const secretMatches = [];
for (const f of rawFiles) {
  const s = fs.readFileSync(path.join(root, f.path), 'utf8');
  if (/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b|E2e![a-f0-9]{36}/.test(s)) secretMatches.push(f.path);
}
if (secretMatches.length) throw new Error('Credential-shaped value in evidence; review without printing it');
const counts = { PASS: cases.filter(c => c.status === 'PASS').length,
  FAIL: cases.filter(c => c.status === 'FAIL').length, BLOCKED: 0, NOT_RUN: 0 };
const summary = {
  schemaVersion: 1, date: '2026-10-26', timezone: 'Asia/Seoul', runId, branch, gitSha: head,
  scope: run.scope, counts, nativeCounts: { executed: 11, passed: counts.PASS, failed: counts.FAIL,
    skipped: 0, interrupted: 0, retries: 0, repeatedCases: 0 },
  classificationCounts: { confirmedFrontendDefects: 1, confirmedBackendDefects: 0,
    harnessFailedCases: 4, orchestrationTimeoutAfterAllTests: 1, environmentBlockedCases: 0 },
  cases: catalog.map(id => cases.find(c => c.id === id)),
  c04, c02ContractReview: { category, setting: renamedSetting,
    nativeStatusRemains: 'FAIL', productDefectConfirmed: false,
    explanation: 'PUT /calendar/categories는 사용자 설정을 저장한다. default_name=MC새분류, display_name=MC새이름이 실제 값이다.' },
  observations: { browser: totalTraffic, unmatchedBrowserStarts: totalTraffic.request - totalTraffic.response - totalTraffic.failure,
    serverHttpCompleted: server.length, serverStatuses, mysql: dbCounts, perCase: traffic,
    caveats: ['브라우저 시작은 서버 수신과 같지 않으며 이동/취소/teardown 경계의 이벤트를 포함한다.',
      '서버 관찰은 OPTIONS를 제외하고 완료를 기록한다. 브라우저 수와 동일하다고 주장하지 않는다.',
      '실패로 중단된 R04 등에는 snapshot이 없으며 이후 assertion을 실행한 것으로 처리하지 않는다.'] },
  phases: phases.map(p => ({ label: p.label, file: p.file, exit: p.exit, sha256: p.sha256,
    startedAt: p.native.startedAt, finishedAt: p.native.finishedAt, counts: p.native.counts })),
  orchestration: { runnerExitCode: 1, recordedPlaywrightExitCode: run.playwrightExitCode,
    timeout: 'All eleven native results were saved, then the one-hour scoped command wait expired.',
    originalSessionState: read(`${relative}/mc-session.json`).state,
    sessionStateIsStale: true, cleanupFinishedAt: run.finishedAt,
    nativeResultsAffectedByTimeout: false, originalSessionFileModified: false },
  resources: { createdContainers: 1, createdSchemas: 1, createdAccounts: 1, createdVolumes: 0,
    identity, cleanup, resourceLedger: 'backend/build/mc-p0-preparation/resource-ledger.json',
    additionalContainersCreated: 0, remainingApprovedContainerBudget: 0 },
  protection: { comparedFiles: Object.keys(protectionBefore.protectedFiles).length, changed,
    originalBlockedRun: 'a0ba0c8152c6', originalP0P1AndEnvironmentReportsUnchanged: true,
    productCodeChanged: false, harnessCodeChanged: false, sourceUnchangedDuringRun: run.sourceUnchangedDuringRun,
    secretScan: { jwtOrGeneratedPasswordMatches: 0 } },
  external: { aiMode: 'E2eServer.FixedAi', gatewayRequests: 0, schoolCrawlerMode: 'DisabledCrawler',
    schoolRequests: 0, realSmtpRequests: 0 },
  proposedFiles: [
    { path: 'frontend/src/calendar/CalendarPage.js', reason: 'C04 편집 검증과 기존 원본 시간 선택값 보존' },
    { path: 'frontend/src/calendar/CalendarDataContext.js', reason: '수정 시 nullable/한쪽 시간의 payload 보존 여부를 함께 검토. 현재 제품 결함으로 확정하지 않음' },
    { path: 'frontend/src/calendar/CalendarPage.test.js', reason: '5종 제목 편집 회귀' },
    { path: 'frontend/e2e/lib/mc-flows.cjs', reason: 'R02/R03 숨김 input 직접 check 대신 실제 label 또는 키보드 입력 사용' },
    { path: 'frontend/e2e/tests/mc-p0.spec.cjs', reason: 'R04 상세 locator 한정, C02 settings 기준 DB 기대값' },
  ],
  nextDecision: '제품 수정과 실행기 보완·실패 사례 재검증은 별도 결정. 추가 컨테이너를 생성하지 않았고 P1은 실행하지 않았다.',
  rawFiles,
};
const summaryPath = `backend/validation/matching-calendar-e2e67-p0-execution-20261026-${runId}.json`;
json(summaryPath, summary);
json(`${relative}/evidence-manifest.json`, { runId, summary: { path: summaryPath, sha256: hash(summaryPath) }, files: rawFiles });
console.log(JSON.stringify({ summaryPath, counts, browser: totalTraffic, serverHttp: server.length,
  mysql: dbCounts, protectedFilesUnchanged: summary.protection.comparedFiles, rawFiles: rawFiles.length,
  nativeResultsUnchanged: true, productCodeChanged: false, additionalContainersCreated: 0 }));
