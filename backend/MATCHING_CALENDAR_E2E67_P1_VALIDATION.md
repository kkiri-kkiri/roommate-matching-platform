# Matching · Calendar 6·7단계 P1 검증 결과

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar` 유지.
P0 커밋 **`5c9f08cb4ff7c9d19467fffa447b3678bc7a6515`**.
P1 실행 ID **`5cefde0224ad`**.

**P1 독립 사례 31개 최종 PASS, FAIL/BLOCKED/NOT RUN 각각 0개다.**
최초 실행은 27 PASS / 4 FAIL이며, 최초 실패를 보존한 뒤 같은 컨테이너에서 해당 4개만 재검증해 통과했다.
실제 native 실행은 총 35회(31+수동 재검증 4), workers=1, 자동 retries=0이다.
처음부터 31개 모두 통과했다고 기록하지 않는다.

[상세 결과 JSON·SHA-256](validation/matching-calendar-e2e67-p1-20261026-5cefde0224ad.json)
· [읽기 전용 집계 도구](tools/matching-calendar-e2e67-p1-report.cjs)
· [보존한 P1 준비안](MATCHING_CALENDAR_E2E67_P1_PLAN.md)

## 1. P0 커밋과 보존

요청한 메시지로 P0 관련 **21개 파일만** 로컬 커밋했다.

```text
test(e2e): complete matching calendar P0 validation and fix event editing
```

제품/회귀 3개, P0 E2E 및 실행기 5개, P0 보고서·JSON·집계 도구 13개다.
P1 준비안과 원본 로그는 커밋하지 않았다. 커밋 직후 작업 트리는 다음 한 항목뿐이었다.

```text
?? backend/MATCHING_CALENDAR_E2E67_P1_PLAN.md
```

이 상태와 HEAD는 `backend/build/mc-p1-preparation/20261026/p0-protection.json`에 기록했다.
이전 차단/최초 실행/재검증 원본, P0 보고서·JSON과 P1 준비안 등 **151개 파일의 지문이 동일**하다.
P0 최초 실행 `2f55ee3d8226`, 재검증 `2d19b8963e9a` 및 차단 기록 `a0ba0c8152c6`은 수정하지 않았다.
P0 11개와 타 도메인 E2E는 이번 실행에서 0개다. P0의 기존 최종 11 PASS 근거를 재작성하지 않았다.
현재 HEAD도 위 P0 커밋이며, P1 변경은 staging/커밋하지 않았다.

## 2. 논리 그룹 및 31개 독립 결과

| 그룹 | PASS | FAIL | BLOCKED | NOT RUN | 최종 |
| --- | ---: | ---: | ---: | ---: | --- |
| R07 | 5 | 0 | 0 | 0 | PASS |
| R08 | 7 | 0 | 0 | 0 | PASS |
| C05 | 5 | 0 | 0 | 0 | PASS |
| C06 | 4 | 0 | 0 | 0 | PASS |
| C07 | 10 | 0 | 0 | 0 | PASS |

| 독립 ID | 최초 → 최종 | 실제 검증 |
| --- | --- | --- |
| R07-01 | PASS → PASS | 필터 A 실제 응답 보류, B 적용, A 해제 및 미적용 선택 닫기 |
| R07-02 | PASS → PASS | 추천 응답 보류 중 전체 탭 복귀, 최신 URL/탭/카드 유지 |
| R07-03 | PASS → PASS | 2페이지 응답 보류 후 추천 page=0 이동 |
| R07-04 | PASS → PASS | 상세 진입·back·forward와 탭 URL·새 GET 일치 |
| R07-05 | PASS → PASS | 이전 mount 요청을 남기고 이동·back 재진입 후 응답 해제 |
| R08-01 | PASS → PASS | 합성 Gateway 503 안내·로딩 해제, 명시적 FixedAi 재시도 |
| R08-02 | PASS → PASS | 합성 429 안내, 요청 폭주 없이 명시적 복구 |
| R08-03 | PASS → PASS | 합성 busy 503 안내·로딩 해제·복구 |
| R08-04 | PASS → PASS | 좋아요 POST 전달 전 실패, UI/DB 유지 및 수동 재시도 |
| R08-05 | PASS → PASS | 좋아요 DELETE 전달 전 실패, UI/DB 유지 및 수동 재시도 |
| R08-06 | PASS → PASS | POST 커밋 확인 후 응답 유실, 재전송 없이 새 GET/재진입 복구 |
| R08-07 | PASS → PASS | DELETE 커밋 확인 후 응답 유실, 재전송 없이 새 GET/재진입 복구 |
| C05-01 | FAIL → PASS | 1/31~2/1 경계, 전체 달력·일 목록·메인 달력 |
| C05-02 | PASS → PASS | 2028 윤일·3월 경계 및 2026 비윤년 셀 대조 |
| C05-03 | PASS → PASS | 12/31~다음 해 1/2 표시·조회·메인 달력 |
| C05-04 | PASS → PASS | 23:59/00:00 등록 기본값, AM 12:00, 자정 일정과 DB 보존 |
| C05-05 | FAIL → PASS | 10/30~11/2, 양 경계·중간 날짜·한쪽 시간 문구·DB null 보존 |
| C06-01 | PASS → PASS | 두 번째 POST 실패 후 첫 serverId·이름/색/표시 초안 보존 |
| C06-02 | PASS → PASS | POST/DELETE 완료 후 PUT 지연 실패·중복 입력 차단·PUT만 재시도 |
| C06-03 | FAIL → PASS | 두 번째 DELETE와 복구 GET 실패 후 완료 DELETE 재전송 방지 |
| C06-04 | FAIL → PASS | 현재 조회 범위 밖 11/15 참조 카테고리의 실제 삭제 400·DB 보존 |
| C07-01 | PASS → PASS | 일정 GET 오류와 명시적 재시도 |
| C07-02 | PASS → PASS | 설정 GET 오류와 실제 설정/일정 복구 |
| C07-03 | PASS → PASS | 이전 달 지연 응답이 최신 달을 덮어쓰지 않음 |
| C07-04 | PASS → PASS | 설정 지연 중 달 이동 후 최신 월·설정 수렴 |
| C07-05 | PASS → PASS | POST 실패 중 초안·DB 유지, 수동 재시도 1행 생성 |
| C07-06 | PASS → PASS | PATCH 실패 중 변경 초안·원본 유지, 재시도 후 해당 ID만 변경 |
| C07-07 | PASS → PASS | DELETE 실패 중 상세·DB 유지, 실제 성공 후에만 삭제 |
| C07-08 | PASS → PASS | POST 실제 응답 보류 중 더블클릭/Enter, 쓰기·생성·성공 안내 각 1회 |
| C07-09 | PASS → PASS | PATCH 실제 응답 보류 중 중복 입력, 쓰기·성공 안내 각 1회 |
| C07-10 | PASS → PASS | DELETE 실제 응답 보류 중 중복 입력, 쓰기·성공 안내 각 1회 |

첫 PASS 27개는 반복하지 않았다. 각 원본/재검증 native 결과·오류·fixture ID·날짜·주입 계획·조회 oracle·JDBC 경로를 JSON에 연결했다.

## 3. 발견·수정한 결함과 테스트 문제

**제품 결함 1개: C06-03의 완료된 DELETE 재전송.**
최초 실행에서 빈 카테고리 A DELETE 200과 DB 삭제가 완료된 뒤, B DELETE를 합성 500으로 막았다.
catch의 복구 GET도 합성 500이었다. 재시도 시 로컬 설정에 A가 남아 A DELETE가 두 번째로 전송됐고,
실제 Spring은 400을 반환했다. JDBC에는 A 부재, B 존재, 앞서 생성한 새 카테고리의 존재가 확인됐다.

`CalendarDataContext.js`에서 각 DELETE 성공 직후 해당 serverId를 로컬 카테고리 상태에서 제거하도록
**주석 포함 2줄만 추가**했다. 다음 쓰기·복구 GET이 실패해도 이미 커밋된 삭제 사실이 유지된다.
서버 트랜잭션을 되돌렸다고 가정하거나 null·API·스키마 계약을 바꾸지 않았다.

같은 이중 실패를 재현하는 Jest를 추가했고 수정 전 **1 FAIL / 8 PASS**, 수정 후 관련 회귀는 모두 통과했다.
실제 재검증에서는 A DELETE 1회, 새 POST 1회가 유지되고 B만 재시도한 뒤 PUT·새 GET·JDBC 설정이 일치했다.
최초 native 실패·HTTP·JDBC와 수정 전 소스를 `original-preservation.json`, `source-original/`에 보존했다.

테스트 문제 3개도 원본을 남기고 실제 재검증했다.

- C05-01/C05-05: 주가 끝나는 월의 다음 달 셀이 현재 화면에 없는데 바로 클릭했다.
  목표 월로 정상 이동한 뒤 날짜를 선택하도록 helper를 수정했다. C05-05는 중간 10/31·11/1의
  시작만/종료만 시간 문구를 전체 달력의 일 목록과 메인 달력에서 함께 확인했다.
- C06-04: 기대한 실제 400을 기본 성공 응답 helper가 200으로 단정했다.
  해당 요청을 직접 기다려 400/INVALID_REQUEST, 독립 DB 보존, 설정 재열기를 검사하도록 수정했다.

확인된 백엔드 제품 결함은 0개다. 백엔드 API·Service·DTO·Entity·DB 스키마는 변경하지 않았다.

## 4. 환경·독립 fixture·사전 검사

기존 관리형 실행기에 `--mc-p1` 분기를 추가했다. P1 전용 allowlist/ledger/phase 관리와 helper/spec을 사용한다.
처음에는 정확히 31개만, 후속에는 저장된 실패 ID만 선택할 수 있고 새 컨테이너 예산은 1개다.
기존 MC P0 및 AUS 선택·retention 동작은 공통 실행기 모의 단위 검사로 확인했다.

각 테스트는 별도 browser context와 reset/seed/clean을 사용한다. caseId를 reset 전에 JS·Java에서 검증한다.
실제 Java entrypoint가 잘못된 caseId를 datasource 생성 전에 거절하는 안전 검사도 실행했다.
숫자 PK를 하드코딩하지 않고 fixture manifest의 실제 ID를 사용했다.
후보는 유효 프로필·설문·선호로 생성했으며 매칭 점수를 직접 삽입하거나 산식을 복제하지 않았다.

날짜 경계는 브라우저의 인자 없는 Date 생성자만 고정했다. 명시적 날짜 파싱·Date.now·타이머·JVM/DB/JWT 시계는 실제 값을 유지했다.
미래 날짜 때문에 인증이 만료되는 오인을 피하고, 실제 JWT 서버 검증은 그대로 거쳤다.

| 검사 | 결과 |
| --- | --- |
| Node 구문·git diff 공백 검사 | 통과 |
| Java E2E 컴파일 | `gradlew.bat --offline --no-daemon prepareE2e`, BUILD SUCCESSFUL |
| 실행기 안전/회귀 단위 검사 | 42 PASS / 3 SKIP(기존 선택적 Java guard probe), 실패 0 |
| 실제 Playwright --list | P1 31개, 1 file, mc-p1, --no-deps, workers=1/retries=0 |
| 관련 Jest | CalendarPage/CalendarDataContext/calendarDates 3 suites, 26 PASS |
| 프런트 빌드 | `npm run build`, Compiled successfully / exit 0 |
| 계약 문서 검사 | 73 endpoints / 75 record DTOs / 30 entities 일치 |

backend `test bootJar`는 NOT RUN이다. 백엔드 제품 변경은 없고 E2E 소스 컴파일을 수행했다.
기존 전체 E2E나 P0 정상 흐름을 의존 실행하지 않았다.

## 5. 실제 HTTP·MySQL과 합성 주입

| 관찰 | 건수 |
| --- | ---: |
| 브라우저 요청 시작 / 응답 / requestfailed | 511 / 467 / 39 |
| 응답·실패와 미대응인 시작 기록 | 5 |
| 브라우저 실패 사유 | ERR_ABORTED 37, 주입 유실 ERR_FAILED 2 |
| 실제 서버 HTTP 완료(OPTIONS 제외) | 477: 200=474, 400=3 |
| 브라우저 합성 응답 | 16: 429=1, 500=13, 503=2 |
| route.fetch 실제 upstream 완료 | 11 |
| 실제 커밋 후 주입 응답 유실 | 2 |
| 독립 JDBC snapshot | 127 |
| reset / fixture manifest / clean / 0행 확인 | 각 35 |

실제 서버 400 세 건은 C06-03 최초 중복 DELETE 1건과 C06-04 최초/재검증의 예상 거절 각 1건이다.
서버 5xx는 관찰되지 않았다. 합성 429/500/503을 실제 서버 오류·quota·semaphore 검증으로 합산하지 않는다.
실제 응답 지연은 서버 완료와 브라우저 해제 시점을 기록했고, 좋아요 유실은 MySQL 커밋 확인 후 abort했다.
진단용 새 GET 자체가 React 상태를 갱신한다고 가정하지 않고 재진입/사용자 재시도 후 화면도 대조했다.

브라우저와 서버 건수는 같은 집계가 아니다. 미대응 시작 5건을 임의 성공/실패로 채우지 않았다.
세 사례의 요약 JSON 저장 뒤 JSONL에 응답 2건·요청 시작 2건이 추가됐다.
집계 시 요약이 JSONL의 정확한 prefix임을 대조하고 **JSONL 전체를 기준**으로 계산했다.
집계 도구의 최초 동등성 검사는 이 차이를 거절했으며, 원인을 확인한 뒤 prefix 검증으로 바로잡았다.
원본 JSON/JSONL은 수정하지 않았다. 이는 native 테스트 실패나 테스트 재실행이 아니다.

AI는 E2eServer.FixedAi, 수집기는 DisabledCrawler, 메일은 MemoryMail이다.
실제 AI Gateway·학교 홈페이지·SMTP 요청은 없다. 공개 일정은 합성 CRAWLED fixture다.

## 6. 신규 자원과 정리

| 항목 | 확인 |
| --- | --- |
| 컨테이너 | kkiri-e2e-5cefde0224ad, 신규 총 1개 |
| ID | 28471273f6c5bb8c2c07ffabf2f5cbaab278a94489b8e15ce0dcfdad73b87468 |
| label | kkiri.e2e.owner=5cefde0224ad |
| schema / 계정 | kkiri_e2e_5cefde0224ad / e2e_5cefde0224ad@% |
| MySQL / UUID | 8.0.46 / d2c36584-d10b-11f1-b9b7-b21e3ac3fce9 |
| 이미지 ID | sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b |
| 저장 | /var/lib/mysql tmpfs 1GiB, Mounts=[], named/anonymous volume 0 |
| 사전 검사 | Linux 엔진·기존 이미지/Chromium·포트·정확한 신규 이름 충돌 검사 통과 |
| 데이터 정리 | 35회 사례별 clean 및 최종 runner clean에서 전체 fixture 테이블 0행 확인 |
| 종료·삭제 | 소유 label·기록 ID 대조 후 삭제, ownedProcessesStopped=true |
| 독립 사후 감사 | 정확한 ID·이름 부재, 13000/18080/13316 재바인딩 가능 |

기존 보존 컨테이너·DB·볼륨은 접근하지 않았다. 이미지/브라우저 다운로드, 기존 자원 재사용·종료, prune도 없다.
같은 신규 자원 안에서 실패 4개만 재검증했고 두 번째 컨테이너를 만들지 않았다.
관리형 실행기와 최종 Playwright exit 0, session=FINISHED, evidenceErrors=[]이다.

## 7. 원본·SHA-256·소스 변경 시점

원본: `backend/build/full-service-e2e/5cefde0224ad/`.
준비·단위·빌드 로그: `backend/build/mc-p1-preparation/20261026/`.

| phase | KST | 결과 |
| --- | --- | --- |
| p1-original | 16:08:26~16:15:28 | 31 실행, 27 PASS / 4 FAIL |
| p1-recheck-1 | 16:18:36~16:19:33 | 실패 4개만 4 PASS |

```text
results-p1-original.json: 7cc1611b0f414b5fdca6b264df54ce47544e1784970d45f37112623954b3b38b
results-p1-recheck-1.json: 4e9dd260176769988b38a633a4739846d44185398c99b8fc5a9c0e098d3bef29
최종 결과 JSON: b104bab03599be47a80d995ea06f22600057e750c33980fb4fa05996c303beae
```

native 결과, 즉시 test-attempts, 브라우저/주입 JSONL, JDBC, 서버 HTTP,
fixture manifest, 자원 신원·정리, source-before/after 및 phase별 소스 지문을 보존했다.
`evidence-manifest.json`은 원본·준비 파일 181개의 크기·SHA-256과 결과 JSON을 연결한다.
보고서·집계 도구·최종 Git 상태는 `report-supplement.json`으로 추가 연결한다.
UTF-8/PowerShell UTF-16LE를 구분하여 JWT·생성 비밀번호 형태의 저장 여부를 출력 없이 검사했다.

최초 31개 실행 중에는 소스를 고정했다. 이후 승인된 C06 제품 수정/회귀와 실패 테스트 helper 보완으로
전체 세션의 sourceUnchangedDuringRun은 **false**다. phase 사이 달라진 파일은 정확히
CalendarDataContext.js, CalendarDataContext.test.js, mc-p1.spec.cjs 3개이며 수정 전 파일도 보존했다.
이를 원인 불명의 실행 중 변경이나 최초부터 같은 소스로 통과한 결과로 표현하지 않는다.

## 8. 변경 파일과 후속 커밋 후보

- 제품: `frontend/src/calendar/CalendarDataContext.js`, `CalendarDataContext.test.js`.
- 기존 공통 E2E: `backend/src/e2e/java/e2e/E2eDatabase.java`,
  `frontend/playwright.config.cjs`, `frontend/e2e/run.cjs`, `fixtures.cjs`, `evidence-reporter.cjs`, `lib/database.cjs`.
- 신규 전용 E2E: `backend/src/e2e/java/e2e/McP1Fixtures.java`,
  `frontend/e2e/mc-p1-session.cjs`, `mc-p1-safety.test.cjs`, `lib/mc-p1-flows.cjs`, `tests/mc-p1.spec.cjs`.
- 신규 최종 자료: 이 보고서, 별도 결과 JSON, `backend/tools/matching-calendar-e2e67-p1-report.cjs`.
- 기존 P1 준비안은 내용·해시를 보존한 미추적 파일이다. 이후 문서 커밋에 포함할 후보로만 남긴다.

권장 P1 커밋 메시지(이번에는 커밋하지 않음):

```text
test(e2e): validate matching calendar P1 recovery and response races
fix(calendar): retain committed category deletions across retry failures
```

P1 31개와 허용된 최소 수정·관련 회귀·자원 정리·결과 보고를 완료했다.
운영 DB, 실제 외부 AI/학교/SMTP, 모든 오류 코드·timeout·날짜 조합은 검증 범위가 아니다.
미확정 자정 자동 갱신·필터 영구 보존·시간 제거 정책을 새로 구현하지 않았다.
P1 변경 커밋, Push·PR·Merge 없이 여기서 중단한다.
