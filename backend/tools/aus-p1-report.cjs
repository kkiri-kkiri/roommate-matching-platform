// Read-only aggregation of preserved P1 evidence. Never edits the source runs or P0.
const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const build = path.join(root, 'backend/build/full-service-e2e');
const ids = ['9ce742e1e7dc', '9f5147ba9e76'];
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const digest = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const recovered = read(path.join(build, ids[0], 'interruption-recovery.json'));
const native = read(path.join(build, ids[1], 'results.json'));
const finalTests = new Map(recovered.tests.filter(t => t.status === 'ASSERTIONS_COMPLETED').map(t => [t.id, {
  id: t.id, evidenceStatus: 'ASSERTIONS_COMPLETED', nativeReporterStatus: null,
  source: ids[0], browserFile: t.browserFile,
}]));
for (const t of native.tests) {
  const id = t.name.split(':')[0], last = t.attempts.at(-1);
  finalTests.set(id, { id, evidenceStatus: last?.status === 'passed' ? 'NATIVE_PASS' : 'UNRESOLVED',
    nativeReporterStatus: last?.status ?? 'notRun', source: ids[1] });
}
const runs = ids.map(id => {
  const dir = path.join(build, id), files = fs.readdirSync(dir);
  const browsers = files.filter(f => f.startsWith('browser-')).map(f => read(path.join(dir, f)));
  const requests = files.filter(f => /^p1-.*\.json$/.test(f)).map(f => read(path.join(dir, f)));
  const cleanup = read(path.join(dir, id === ids[0] ? 'interruption-cleanup.json' : 'run.json'));
  const databaseFiles = files.filter(f => f.startsWith('database-'));
  const testsStartedByFixture = databaseFiles.filter(f => fs.readFileSync(path.join(dir, f), 'utf8').split(/\r?\n/)
    .filter(Boolean).map(JSON.parse).some(e => e.operation === 'reset' && e.completed)).length;
  const server = fs.readFileSync(path.join(dir, 'server-http.jsonl'), 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
  const hashes = {};
  function visit(relative) {
    for (const entry of fs.readdirSync(path.join(dir, relative), { withFileTypes: true })) {
      const f = path.join(relative, entry.name);
      if (entry.isSymbolicLink()) throw new Error('Evidence symlink rejected');
      if (entry.isDirectory()) visit(f); else hashes[f.replaceAll('\\', '/')] = digest(path.join(dir, f));
    }
  }
  visit('');
  return { id, testsStartedByFixture, browserTeardowns: browsers.length,
    browserRequestStartsRecorded: requests.reduce((n, e) => n + e.started.length, 0),
    browserResponsesRecorded: browsers.reduce((n, e) => n + e.requests.length, 0),
    browserFailedRequestsRecorded: requests.reduce((n, e) => n + e.failed.length, 0),
    serverHTTPObservations: server.length, serverOptions: server.filter(e => e.method === 'OPTIONS').length,
    browserTestTeardownsComplete: id !== ids[0],
    containerRemoved: cleanup.containerRemoved, fixtureTablesEmpty: cleanup.fixtureTablesEmpty,
    ownedProcessesStopped: cleanup.ownedProcessesStopped, sha256: hashes };
});
const protectedFiles = read(path.join(root, 'backend/build/aus-p1-preparation/p0-protected.json'));
const changedProtected = Object.entries(protectedFiles.files).filter(([file, hash]) => digest(path.join(root, file)) !== hash).map(([file]) => file);
if (changedProtected.length) throw new Error('P0 protected evidence changed: ' + changedProtected.join(', '));
const tests = [...finalTests.values()].sort((a,b) => a.id.localeCompare(b.id));
if (tests.length !== 23) throw new Error('P1 evidence must cover exactly 23 unique tests');
const expectedCounts = [4,5,4,2,6,2];
const scenarios = Array.from({ length: 6 }, (_, i) => {
  const id = 'B' + (i + 10), children = tests.filter(t => t.id.startsWith(id + '-'));
  if (children.length !== expectedCounts[i]) throw new Error('P1 catalog mismatch: ' + id);
  return { id, status: children.every(t => ['ASSERTIONS_COMPLETED','NATIVE_PASS'].includes(t.evidenceStatus)) ? 'PASS' : 'FAIL',
    tests: children.length, nativePass: children.filter(t => t.evidenceStatus === 'NATIVE_PASS').length,
    recoveredAssertionsCompleted: children.filter(t => t.evidenceStatus === 'ASSERTIONS_COMPLETED').length };
});
const result = { scope: 'Auth/User/Survey P1 B10-B15 only', baseCommit: 'f5186d7',
  qualification: 'First run was interrupted before reporter onEnd. Sixteen cases retain final body assertions/notes and successful fixture cleanup, but have no native Playwright status. They are never rewritten as native PASS. Logical PASS uses those recovered assertions and subsequent native results.',
  scenarios, tests, runs,
  totals: { uniqueTests: tests.length, browserTestsStarted: runs.reduce((n,r) => n+r.testsStartedByFixture,0),
    browserRequestStartsRecorded: runs.reduce((n,r) => n+r.browserRequestStartsRecorded,0),
    browserResponsesRecorded: runs.reduce((n,r) => n+r.browserResponsesRecorded,0),
    browserFailedRequestsRecorded: runs.reduce((n,r) => n+r.browserFailedRequestsRecorded,0),
    serverHTTPObservations: runs.reduce((n,r) => n+r.serverHTTPObservations,0),
    browserRecordCaveat: 'First run has 19 browser teardown records and a twentieth interrupted fixture. Browser HTTP counts are recorded lower bounds, not a complete or server-matched total.' },
  postCleanupAudit: read(path.join(root, 'backend/build/aus-p1-preparation/post-cleanup-audit.json')),
  p0Protection: { count: Object.keys(protectedFiles.files).length, changed: changedProtected },
  localChecks: read(path.join(root, 'backend/build/aus-p1-preparation/local-checks.json')) };
fs.mkdirSync(path.join(root, 'backend/validation'), { recursive: true });
fs.writeFileSync(path.join(root, 'backend/validation/auth-user-survey-e2e67-p1-20261023.json'), JSON.stringify(result, null, 2) + '\n');
const rows = scenarios.map(s => `| ${s.id} | ${s.status} | ${s.tests} | ${s.recoveredAssertionsCompleted} | ${s.nativePass} |`).join('\n');
fs.writeFileSync(path.join(root, 'backend/AUTH_USER_SURVEY_E2E67_P1_VALIDATION.md'), `# Auth · User · Survey P1 6·7단계 검증

2026-10-23 Asia/Seoul. 현재 브랜치 test/frontend-backend-auth-user-survey, P0 기준 커밋 f5186d7.
커밋·Push·PR·Merge는 수행하지 않았다. P0와 다른 도메인 E2E는 실행하지 않았다.

| 사례 | 최종 논리 상태 | 독립 테스트 | 중단 원본 검증 구문 완료 근거 | 후속 Playwright PASS |
| --- | --- | ---: | ---: | ---: |
${rows}

첫 실행 9ce742e1e7dc는 사용량 제한으로 소유 프로세스가 중단되어 results.json/run.json이 없다.
브라우저·DB 근거에서 마지막 검증 관찰과 성공한 fixture 정리가 남은 16개를 ASSERTIONS_COMPLETED로 복구했다.
이 16개의 Playwright 원래 상태를 PASS로 생성하거나 원본을 덮어쓰지 않았다.
최종 논리 상태는 이 근거와 두 번째 실행 9f5147ba9e76의 미완료 7개 결과를 합친 판정이다.
최초 실패/미완료와 20번째 진행 중 중단 기록을 모두 보존했다. 처음부터 전부 통과한 실행이라는 뜻은 아니다.

[사례·실행별 집계와 원본 SHA-256](validation/auth-user-survey-e2e67-p1-20261023.json),
[P1 계획](AUTH_USER_SURVEY_E2E67_P1_PLAN.md), [집계 도구](tools/aus-p1-report.cjs).
원본은 Git 제외 backend/build/full-service-e2e/ 아래 두 실행 ID에 있다.

브라우저 테스트 시작은 ${result.totals.browserTestsStarted}회(첫 실행 20회 중 1회 진행 중 중단, 후속 7회), 고유 테스트 23개다.
보존된 브라우저 요청 시작 ${result.totals.browserRequestStartsRecorded}건, 응답 ${result.totals.browserResponsesRecorded}건,
실패 요청 이벤트 ${result.totals.browserFailedRequestsRecorded}건, 서버 HTTP 관찰 ${result.totals.serverHTTPObservations}건이다.
첫 실행 20번째의 브라우저 teardown이 없어 브라우저 수는 보존 기록의 하한이며 서버 수와 일치한다고 주장하지 않는다.
route.fetch의 실제 서버 요청과 의도적 abort/응답 유실도 집계 차이를 만든다. OPTIONS는 서버 집계에서 제외한다.
브라우저 요청 시작에는 TCP 전달 전 취소와 teardown 시 진행 중이던 요청도 있으므로 모두 서버 수신 건수라는 뜻은 아니다.
화면 자동 조회로 호출된 게시글·추천·일정은 해당 도메인 검증 사례로 집계하지 않았다.

| 실행 | 테스트 시작 | 브라우저 요청 시작 기록 | 응답 기록 | 실패 이벤트 | 서버 HTTP |
| --- | ---: | ---: | ---: | ---: | ---: |
${runs.map(r => `| ${r.id} | ${r.testsStartedByFixture} | ${r.browserRequestStartsRecorded} | ${r.browserResponsesRecorded} | ${r.browserFailedRequestsRecorded} | ${r.serverHTTPObservations} |`).join('\n')}

## 결함과 실행기 오류

- 프론트 결함: 선호 GET 실패가 Promise.all의 계정 조회까지 실패시켰다. 첫 브라우저 실행과 새 단위 회귀 테스트에서 재현했다.
  계정·선호 조회 상태를 분리하고 선호 영역의 오류·재시도를 제공했다. 조회 실패를 미작성으로 표시하지 않는다.
- 프론트 결함: 계정 A의 PATCH가 커밋된 뒤 응답을 지연시키고 B로 전환하면 늦은 onUserUpdated가 A를 복원할 수 있었다.
  새 단위 테스트에서 재현했고 기존 인증 세대 검사로 늦은 콜백과 저장 알림을 차단했다.
  후속 B14-late-patch-success는 실제 HTTP와 B DB 불변을 확인한다. 서버 소유권 결함으로 보고하지 않는다.
- 실행기 보완: 이메일 자동 availability와 닉네임 응답을 잘못 매칭할 수 있어 nickname query를 특정했다.
  cooldown 복구 대기는 발송 응답 후 62초를 기준으로 보완했다. 최초 마지막 재전송 429의 상세 오류가 유실되어
  정확한 원인을 확정하지 않으며 서비스 결함으로 분류하지 않는다.
- 중단 복구: 최초 네이티브 최종 보고 유실을 별도 interruption-recovery/cleanup 파일로 기록하고,
  후속 실행은 중단/미완료 7개만 선택했다. 두 번째부터 테스트 종료별 상태도 즉시 보존한다.
  새 단위 테스트 준비 중 중복 닉네임 DOM selector와 PowerShell 문자열 인코딩을 보정한 이력도 local-checks에 구분했다.

## 검증 및 자원

새 단위 회귀 2개 PASS, 마스킹/선택 안전 검사 2개 PASS, 프론트 빌드 성공.
E2E 전용 Java 컴파일과 구문 검사 PASS. 계약 스냅샷 73 endpoints / 75 record DTOs / 30 entities 일치.
운영 설정·공개 API·Entity DDL·서비스 구현은 변경하지 않았다. 전체 기존 테스트는 반복하지 않았다.

인증번호/재설정 토큰 TTL 8초의 실제 만료를 기다리고, 인증 실패 제한 15초 후 실제 MySQL 복구를 검사한다.
가입 인증 토큰 만료 P0 B3은 반복하지 않는다. 실제 cooldown 60초와 제한 행은 사례 중 우회·삭제하지 않았다.
경계 UI 차단은 요청 0건, 서버 경계는 별도 브라우저 fetch의 400/DB 보존으로 구분했다.
B12의 지연·실패는 route 주입이며 실제 서비스 장애가 아니다. 응답 유실은 커밋 후 DB와 수동 GET을 확인한다.

신규 컨테이너는 승인된 두 개를 순차 사용했다. tmpfs, named volume 0, 기존 이미지 --pull=never,
React13000/Spring18080/MySQL13316을 사용했다. 이미지·브라우저 다운로드와 기존 프로세스 종료는 없었다.
실행별 UUID/이름/소유 라벨을 검증한 신규 대상만 정리했다. 첫 중단 자원은 31개 테이블 0행 감사 후 삭제했다.
각 실행의 삭제·fixture 0행·프로세스 종료 여부는 JSON에 있다. 기존 보존 DB·컨테이너·볼륨에는 접근하지 않았다.
별도 정리 감사에서 신규 두 이름의 부재와 13000/18080/13316 포트의 재바인딩 성공을 확인했다.
P0 보호 파일/원본 ${Object.keys(protectedFiles.files).length}개의 SHA-256이 모두 동일하다.
인증번호·비밀번호·JWT·재설정 토큰·메일 원문은 근거에 저장하지 않는다. trace/screenshot/video/실패 DOM snapshot은 끈다.

## 범위와 파일

B11 필수값의 라디오·MBTI 개별 누락 조합 전수, B12 모든 endpoint/서버 장애/timeout 조합,
실제 SMTP·운영 배포·다른 도메인 전체 검증은 수행하지 않았다. 6개 논리 PASS를 모든 입력 검증으로 일반화하지 않는다.

변경은 E2eDatabase.java, E2eServer.java, e2e/run.cjs, evidence-reporter.cjs, playwright.config.cjs,
MyPage.js와 신규 P1 계획/보고서/JSON/집계 도구, aus-p1.spec.cjs, aus-p1-flows.cjs,
aus-p1-safety.test.cjs, MyPageP1Regression.test.js다. 공통 실행기는 팀원 A 범위에서만 보완했다.
기존 P0 전용 파일·검증 결과·커밋은 변경하지 않았다.

Git 변경 대상은 기존 6개와 신규 8개, 총 14개다.

- backend/src/e2e/java/e2e/E2eDatabase.java
- backend/src/e2e/java/e2e/E2eServer.java
- frontend/e2e/evidence-reporter.cjs
- frontend/e2e/run.cjs
- frontend/playwright.config.cjs
- frontend/src/pages/mypage/MyPage.js
- backend/AUTH_USER_SURVEY_E2E67_P1_PLAN.md
- backend/AUTH_USER_SURVEY_E2E67_P1_VALIDATION.md
- backend/tools/aus-p1-report.cjs
- backend/validation/auth-user-survey-e2e67-p1-20261023.json
- frontend/e2e/aus-p1-safety.test.cjs
- frontend/e2e/lib/aus-p1-flows.cjs
- frontend/e2e/tests/aus-p1.spec.cjs
- frontend/src/pages/mypage/MyPageP1Regression.test.js

커밋 권장: fix(auth): 계정 응답 격리 및 설문 조회 복구와 P1 E2E 검증
`, 'utf8');
console.log(JSON.stringify({ scenarios, totals: result.totals, p0Protected: result.p0Protection }, null, 2));
