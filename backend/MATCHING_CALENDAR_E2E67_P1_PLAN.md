# Matching · Calendar 6·7단계 P1 검증 준비안

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar`,
조사 HEAD `011606cbbf37e5340aea66db48c282cfb94d6350`.
이번 작업은 저장소·로컬 Git 이력·파일을 읽고 이 준비안을 추가하는 작업이다.
P1 테스트, 기존 테스트, 빌드, 서버 기동, DB 접속, Docker 명령, 외부 AI/학교 요청은 실행하지 않았다.
새 브랜치, 커밋, Push, PR, Merge도 만들지 않았다.

## 1. P0 최종 결과 대조와 커밋 대상

사용자는 P0 완료를 알려 주었다. 그러나 현재 체크아웃에서 해당 최종 결과와 원본을 확인할 수 없다.
이 사실을 P0 실패나 미실행으로 해석하지 않는다.

확인된 자료:

- [기존 6·7단계 계획](MATCHING_CALENDAR_E2E67_PLAN.md)은 P0 11개/P1 5개를 모두 NOT RUN으로 기록한 **실행 전 문서**다. 수정하지 않았다.
- HEAD `011606c`는 계획, `mc-p0.spec.cjs`, `mc-flows.cjs`, `mc-session.cjs`, `McFixtures.java` 및 공통 실행기의 MC P0 분기를 포함한다. 최종 검증 보고/JSON이나 C04 제품 수정은 이 커밋에 없다.
- `backend/build/full-service-e2e/`가 현재 파일시스템에 없다. `mc-session.cjs`에 과거 실행 ID `0dd4e8c47708`이 있지만, ID만으로 결과나 정리를 입증하지 않는다.
- `backend/validation/`의 Matching·Calendar 자료는 [5단계 JSON](validation/matching-calendar-http5-20261022.json)과 계획 JSON이다. [5단계 보고](MATCHING_CALENDAR_HTTP5_VALIDATION.md)의 29개 논리 PASS/HTTP 212건은 P0 11개의 최종 결과로 전용하지 않는다.
- Auth·User·Survey의 P0/P1, CCRA 결과는 별도 도메인 근거다. MC P0 결과로 합산하지 않는다.

| P0 ID | 현재 확인 가능한 마지막 기록 | 최종 PASS/FAIL/BLOCKED/NOT RUN 대조 |
| --- | --- | --- |
| R01 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| R02 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| R03 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| R04 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| R05 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| R06 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| C01 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| C02 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| C03 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |
| C04 | 실행 전 계획: NOT RUN, 브라우저 재현 예정 | BLOCKED — 최초 재현·수정・재검증 근거 미확인 |
| X01 | 실행 전 계획: NOT RUN | BLOCKED — 최종 보고·원본 미확인 |

위 BLOCKED는 **이번 최종 결과 확인 작업의 상태**다. 실제 P0 실행을 11 BLOCKED로 재작성한 것이 아니다.
실제 최종 PASS/FAIL/BLOCKED/NOT RUN 수는 아직 확정할 수 없다. 완료 통보만으로 11 PASS를 기입하지 않는다.
P0를 다시 실행해 누락 근거를 대신 만들지 않는다.

C04 수정 여부도 미확인이다. 현행 `frontend/src/calendar/CalendarPage.js`의 `validateDraft`는
시작/종료 시간이 모두 `CALENDAR_TIME_OPTIONS`에 들어가야 저장을 허용한다.
이는 종일·한쪽 시간·15분 외 기존 일정의 제목 편집과 관련된 기존 의심 조건이다.
실제 브라우저 최초 재현 없이 새 결함을 확정하거나 제품을 고치지 않는다.
P1 C05의 날짜 표시 검사와 C07의 정상 시간 초안 검사는 이 C04 편집 검사를 반복하지 않는다.

승인 전/실행 전 선행 조건은 P0 최종 보고 및 원본 경로 또는 해당 커밋을 확인하는 것이다.
보고의 11개 판정, C04 최초 실패와 수정 파일/커밋, 영향 사례 재검증, HTTP·새 GET·DB 근거,
소유 자원 정리 기록과 SHA-256을 읽기만 하여 연결한다. 원본 부재 시 확인 불가 범위를 그대로 남긴다.
현재 사용자에게 경로/커밋을 요청했으며, 기존 파일·Git 이력을 바꾸어 맞추지 않는다.

착수 시 `git status --porcelain=v1 --untracked-files=all`은 출력이 없었다.
따라서 **기존 미커밋 P0 변경 및 현재 정리할 P0 커밋 대상은 없다.**
이번에 추가한 준비안만 새 변경이다. P0 자료가 다른 위치에 있다면 확인 뒤 별도 커밋 후보 목록만 제시한다.
실제 `git add`/commit은 하지 않는다.

## 2. P1 판정 단위와 독립 fixture

P1은 현재 5개 논리 사례 모두 NOT RUN이다. 아래 **31개 독립 테스트**를 제안한다.
각 행마다 새 브라우저 context와 신규 소유 schema의 reset/seed/clean을 사용한다.
앞 사례의 생성 ID, 좋아요, 카테고리, 초안, route, 브라우저 저장소, 서버 AI 분석 상태에 의존하지 않는다.

| 논리 ID | 독립 ID | 수 |
| --- | --- | ---: |
| R07 | R07-01 ~ R07-05 | 5 |
| R08 | R08-01 ~ R08-07 | 7 |
| C05 | C05-01 ~ C05-05 | 5 |
| C06 | C06-01 ~ C06-04 | 4 |
| C07 | C07-01 ~ C07-10 | 10 |
| 합계 | 고유 ID 31개, workers=1, retries=0 | 31 |

논리 PASS는 소속 독립 사례가 모두 PASS일 때만 부여한다. 실행한 assertion 실패는 FAIL,
환경/선행 조건 때문에 시작하지 못한 사례는 BLOCKED, 선택하지 않았거나 아직 도달하지 않은 사례는 NOT RUN으로 구분한다.
중단·teardown 실패·native 결과 유실을 PASS로 만들지 않는다. 최초 결과와 후속 결과를 따로 보존한다.

모든 사례의 fixture manifest에는 runId/caseId, 실제 DB ID, 최초 HTTP 기대 집합, 날짜/시간,
카테고리 참조 및 주입 계획을 기록한다. 숫자 PK는 하드코딩하지 않는다.
매 reset 전에 허용 caseId를 검증하고 해당 fixture를 선택한다. caseId 설정이 reset 뒤에 적용되지 않도록
기존 자동 fixture의 생성 순서를 지킨다. 미지정·범위 외 ID는 DB 준비 전에 거절한다.

공통 fixture 묶음:

- M: 기존 A/B와 유효 프로필·설문·선호 생성 함수를 재사용한다. R07에는 활성 완료 후보 12명 이상,
  전체 목록 두 페이지, 취침 필터별 서로 다른 ID 집합과 서로 다른 all/recommended 응답을 준비한다.
  추천이 필요한 사례는 실제 GET으로 60점 이상 후보와 대상 노출을 확인한다. 점수 행 직접 삽입·산식 복제는 하지 않는다.
- L0/L1: R08의 대상 한 명만 각각 미등록/등록 상태로 준비하고 최초 관심 개수 N을 기록한다.
  좋아요 전용 fixture로 준비하며 P0 R06의 7명→6명 경계를 다시 만들지 않는다.
- D: 각 C05 행의 고정 날짜에 시스템 SCHOOL/DORMITORY 및 소유 CUSTOM 카테고리, 고유 제목의 개인/합성 공개 일정을 생성한다.
  합성 공개 일정은 CRAWLED 저장 행이며 학교 수집 성공을 뜻하지 않는다. 바깥 날짜 대조 일정도 준비한다.
- K: C06에는 기존 이름/색/표시값이 서로 다른 카테고리, 빈 삭제용 2개, 다른 달에만 쓰이는 카테고리와 일정을 준비한다.
  다른 달 참조는 2026-11-15로 잡고 10월 화면의 실제 from/to 밖인지 먼저 확인한다. FullCalendar가 인접 월 날짜도 조회하므로 단순히 11월 1일을 쓰지 않는다.
  신규 임시 항목 2개의 label/color/enabled 초안을 사례마다 새로 만든다.
- E: C07에는 정상 09:00~10:00 일정과 소유 카테고리, 모든 필드가 채워진 새 초안 하나를 준비한다.
  C04의 종일/한쪽 시간/비정각 편집은 사용하지 않는다.

현재 `McFixtures.seed`는 P0 전용이며 현재 서울 날짜와 C04 데이터를 생성한다.
P1에서 `E2E_MC_P0=1`로 우회 호출하지 않는다. 별도 P1 fixture에서 유효 survey/preferences/category 생성 함수를 필요한 만큼 재사용한다.

### R07 — 전환과 지연 응답, 5개

| 독립 ID / fixture | 입력·주입 | 기대 결과와 근거 |
| --- | --- | --- |
| R07-01 / M | 필터 A GET을 실제 서버에서 받은 뒤 보류, 필터 B 적용 후 최신 GET 완료, A 해제. 이어 미적용 모달 선택을 취소 | 최신 B의 칩·카드 ID·개수 유지, A가 덮어쓰지 않음. 모달 취소는 새 적용 GET 0건이며 적용 필터 보존 |
| R07-02 / M | all → recommended → all을 빠르게 전환, 이전 탭 실제 GET 응답을 역순 해제 | URL의 tab·aria-selected·현재 카드가 최신 탭의 실제 응답과 일치. 오래된 탭 로딩/오류/카드가 남지 않음 |
| R07-03 / M | 전체 목록 2페이지 GET 보류 중 필터 적용 또는 추천 탭 이동 | 최종 탭/필터가 요구하는 page=0 결과 유지. 페이지는 UI 1기반/API 0기반으로 확인. 낡은 2페이지가 복귀하지 않음 |
| R07-04 / M | 탭에서 카드 상세로 이동 후 브라우저 back/forward | 실제 만들어진 history와 tab URL에 맞는 화면·대상 복원, 최신 GET과 일치. 탭 변경 자체는 replace인 현행 동작을 반영 |
| R07-05 / M | 목록 GET 보류 중 다른 페이지로 이동, back으로 재진입하여 새 GET 완료 후 이전 응답 해제 | 이전 mount의 응답이 재진입 화면을 덮어쓰지 않음. 선택한 탭·페이지 UI·최종 GET의 조합이 일치 |

지연은 고정 sleep보다 요청별 barrier로 제어한다. 해제 후 실제 abort/실패 이벤트를 함께 남긴다.
현재 필터와 페이지는 URL에 저장하지 않고 탭만 저장한다. 뒤로가기에서 필터/페이지의 영구 복원을
새 정책으로 강제하지 않는다. 사용자에게 보이는 상태와 실제 요청의 불일치는 결함 후보로 기록한다.
페이지 로딩 중 UI에서 사라진 pagination을 강제 DOM 조작으로 클릭하지 않는다.

### R08 — AI·좋아요 오류와 응답 유실, 7개

| 독립 ID / fixture | 입력·주입 | 기대 결과와 근거 |
| --- | --- | --- |
| R08-01 / M | 대상 AI POST에 합성 503 `AI_GATEWAY_UNAVAILABLE`, 해제 후 focus를 벗어났다가 다시 선택 | 오류 안내와 aria-busy 해제, 점수 불변. 자동 무한 재요청 없이 명시적 재선택으로 고정 AI의 실제 endpoint 응답 표시 |
| R08-02 / M | 합성 429 `AI_ANALYSIS_RATE_LIMITED`, 이후 route 제거·재선택 | 안전한 제한 안내, 요청 폭주·성공 오표시 없음, 재선택으로 복구. 실제 quota 소진/회복 검증으로 집계하지 않음 |
| R08-03 / M | 합성 503 `AI_ANALYSIS_BUSY`, 이후 route 제거·재선택 | busy 안내와 로딩 해제, 고정 AI 실제 응답으로 복구. 실제 semaphore 포화 검증으로 집계하지 않음 |
| R08-04 / L0 | 좋아요 POST를 서버에 보내기 전 합성 500 `INTERNAL_SERVER_ERROR` | 오류 표시, 하트 false/개수 N/DB 원본 유지, pending 해제. 수동 재시도 한 번으로 true/N+1/DB 한 행 |
| R08-05 / L1 | 좋아요 DELETE를 서버에 보내기 전 동일 합성 오류 | 하트 true/개수 N/DB 원본 유지, pending 해제. 수동 재시도 한 번으로 false/N-1/DB 행 제거 |
| R08-06 / L0 | route.fetch로 실제 POST 성공·DB 커밋 확인 후 브라우저 응답 abort | 서버 true/N+1와 UI 미확정을 따로 기록. 성공 안내·자동 POST 재전송 없음. route 제거 후 명시적 새 GET/재진입으로 true/N+1로 수렴, 동일 POST 중복 없음 |
| R08-07 / L1 | 실제 DELETE 성공·DB 커밋 후 브라우저 응답 abort | 서버 false/N-1와 UI 미확정 구분. 자동 DELETE 재전송 없음. 새 GET/재진입으로 false/N-1로 수렴, 동일 DELETE 중복 없음 |

좋아요 복구는 카드·상세 하트와 관심 목록/개수 중 해당 화면을 새로 읽어 확인한다.
진단용 browserRequest GET 자체가 React 상태를 갱신한다고 가정하지 않는다.
커밋 불명확 시 같은 쓰기부터 재전송하지 않는다. POST/DELETE 유실 후 GET 확인과 단순 실패 후 수동 재시도를 구분한다.
AI 원문 오류·외부 응답을 저장하지 않고 API의 공개 ErrorCode/message만 합성한다.

### C05 — 날짜 경계와 메인 달력, 5개

각 행에서 전체 달력·해당 일/월 일정 목록·메인 미니 달력의 날짜 강조와 선택일 제목/시간 문구를 대조한다.
전체 달력의 날짜 셀과 이벤트 ID를 연결해 눈으로 비슷한 제목만 비교하지 않는다.

| 독립 ID / fixture | 고정 날짜·데이터 | 기대 결과 |
| --- | --- | --- |
| C05-01 / D | 서울 2026-01-31, 1/31~2/1 종일 및 각 날짜 단일 일정, 1/30·2/2 대조군 | 이전/다음 달 이동 후 양끝 날짜에 올바른 ID 노출. 하루 밀림·중복 목록 행 없음, 선택일에만 대조군 표시 |
| C05-02 / D | 서울 2028-02-28, 2/28~3/1 종일·2/29 단일 일정 | 윤년 29일 존재, 2/29·3/1 포함 및 범위 밖 제외. 2026년 2월은 28일까지라는 비윤년 셀도 해당 사례에서 대조 |
| C05-03 / D | 서울 2026-12-31, 12/31~2027-01-02 종일 | 연도·월 표시/조회 범위가 함께 이동, 12/31·1/1·1/2 포함, 12/30·1/3 제외 |
| C05-04 / D | 서울 2026-12-31 23:59와 2027-01-01 00:00을 순차 설정. 12/31 23:45~1/1 00:15 및 1/1 00:00~00:15 일정 | 자정이 AM 12:00, 저장/조회 시간은 00:00이며 24:00을 API에 보내지 않음. 23:59에 등록창을 열 때 기본 날짜/시간이 다음 날 00:00~00:15. 창을 새로 열고 00:00의 기본값과 구분. 저장 CRUD는 반복하지 않음 |
| C05-05 / D | 2026-10-30~11/02 종일 + 10/31 23:45~11/01 00:15 + 시작만/종료만 다일 일정 + from/to에 정확히 닿는 행과 하루 바깥 행 | API의 포함 경계/날짜 범위와 UI에 맞는 배치, 기간 양끝 포함. 종일 FullCalendar end는 종료일 다음 날의 exclusive 값. 한쪽 시간은 지정 시간/미정 문구 유지, DB null과 원래 DATE/TIME 불변 |

브라우저만 고정 서울 시각을 사용하고 JVM/DB/인증 JWT 시계를 바꾸지 않는다.
타이머 전체를 멈추지 않는 Date 고정 기능을 우선 사용하며, fixture의 날짜는 브라우저 시계와 같은 manifest에서 정한다.
타이머 API 사용 가능 여부는 승인 후 설치된 Playwright 1.64.1에서 확인한다. 현재 브라우저 준비 완료를 주장하지 않는다.
메인 페이지 날짜는 mount 시 고정되는 현행 코드이므로 23:59→00:00 무조작 실시간 날짜 전환은 별도 요구사항으로 강제하지 않는다.
각 시각에서 새 mount/새 등록창을 기준으로 검사하고, 자동 갱신 필요성은 관찰 사항으로 보고한다.
종료 시각이 정확히 다음 날 00:00일 때의 시간 구간 배치와 날짜별 강조 정책은 임의 통일하지 않는다.
해당 경계의 새 정책이 필요하면 현행 계약/각 UI 의미를 기록하고 결정 요청 대상으로 남긴다.

### C06 — 카테고리 부분 저장과 다른 달 참조, 4개

| 독립 ID / fixture | 입력·주입 | 기대 결과와 근거 |
| --- | --- | --- |
| C06-01 / K | 새 카테고리 2개 중 첫 POST 실제 성공, 두 번째 POST 합성 실패, 수동 재시도 | 첫 생성 serverId가 초안에 남고 첫 POST는 총 1회. 두 번째만 재요청 후 PUT, 이름/색/표시 초안 보존 및 새 GET/DB 일치 |
| C06-02 / K | 새 POST와 빈 카테고리 DELETE 실제 완료 후 PUT을 보류·합성 실패. 저장 버튼 빠른 중복 입력 후 수동 재시도 | 저장 중 중복 요청 차단, 완료 POST/DELETE 각각 1회. 실패 후 draft/serverId와 로딩 해제, PUT만 재시도해 최종 설정 일치 |
| C06-03 / K | 첫 빈 카테고리 DELETE 실제 완료, 두 번째 DELETE 합성 실패, catch의 복구 GET도 1회 실패 | 이미 커밋된 삭제/생성 사실과 남은 초안 보존. 자동 연쇄 재시도 없음. route 해제 후 명시적 재동기화/재시도에서 완료 DELETE 중복 없음, 최종 GET/DB 수렴. UI가 이 복구를 지원하지 못하면 실제 결함 근거로 FAIL 기록 |
| C06-04 / K | 현재 10월의 실제 조회 범위에는 일정이 없고 11/15에만 참조가 있는 카테고리를 설정에서 명시적 삭제 | 실제 DELETE 400 `INVALID_REQUEST`, 해당 카테고리/설정/11월 일정 DB 불변. 실패 안내·재조회/설정 재열기로 보존 상태 확인, 요청 반복·참조 일정 삭제로 우회하지 않음 |

POST→DELETE→PUT은 별도 트랜잭션이며 마지막 실패가 앞선 커밋을 되돌린다고 기대하지 않는다.
실제 서버 실패는 C06-04로 확인하고 C06-01~03의 실패 지점은 브라우저 합성 주입으로 표시한다.
특히 현재 `saveCategories`는 catch의 카테고리 재조회 실패를 삼킨다. C06-03은 이 경로의 가설 검증이며
실행 전 서비스 결함 확정/수정 근거로 사용하지 않는다.

### C07 — 일정·설정 조회/일정 쓰기의 오류·지연, 10개

| 독립 ID / fixture | 입력·주입 | 기대 결과와 근거 |
| --- | --- | --- |
| C07-01 / E | 일정 GET만 합성 오류, 카테고리 GET 실제 성공 | 오류와 다시 시도 표시, 거짓 빈 결과/성공 없음. route 해제·명시적 재시도로 실제 일정 복구 |
| C07-02 / E | 카테고리 GET만 합성 오류, 일정 GET 실제 성공 | 오류와 로딩 해제, 기본 카테고리를 저장 가능한 확정 설정으로 오인하지 않음. 재시도 후 실제 설정/일정 일치 |
| C07-03 / E | 이전 달 일정 GET 실제 응답 보류 → 다음 달 응답 완료 → 이전 해제 | 이전 응답이 현재 월 목록을 덮어쓰지 않음. 최종 월·조회 from/to·UI ID 집합 일치 |
| C07-04 / E | 카테고리 GET을 보류, 일정 GET 먼저 완료. 대기 중 달 이동 후 최종 응답 해제 | 조기 성공/빈 상태 오표시 없이 최신 달과 설정 조합 일치, 로딩 종료 후 재조회 값 일치 |
| C07-05 / E | 일정 POST를 서버 전달 전 합성 오류, 이후 사용자 재시도 | 제목/날짜/시간/분류/설명 초안 유지, 성공 alert 없음, pending 해제, 실패 동안 DB 0행. 재시도에만 실제 1행 생성 |
| C07-06 / E | 정상 시간 일정 PATCH 합성 오류, 이후 사용자 재시도 | 변경 초안 유지, 기존 DB 원본 유지, 성공 오표시 없음. 재시도 후 해당 ID 한 행만 갱신 |
| C07-07 / E | 일정 DELETE 합성 오류, 이후 사용자 재시도 | 상세/일정/DB 유지와 로딩 해제, 실제 성공 후에만 삭제. 자동 재전송 없음 |
| C07-08 / E | 실제 POST 응답 보류, 등록 더블클릭/Enter 등 UI 입력 중복 후 해제 | in-flight 쓰기 1회, 입력/닫기 경로가 저장 중 상태와 일치. 실제 1행 및 성공 안내 1회 |
| C07-09 / E | 실제 PATCH 응답 보류, 저장 중복 입력 후 해제 | PATCH 1회, 원본 ID/시간·기간 등 의도하지 않은 필드 보존, 성공 안내 1회 |
| C07-10 / E | 실제 DELETE 응답 보류, 삭제 중복 입력 후 해제 | DELETE 1회, 404 연쇄/거짓 실패 없음, 삭제 행 0개·성공 안내 1회 |

일정/카테고리는 Promise.all로 읽는 현재 구현을 반영한다. 한쪽 성공만으로 전체 로딩 완료를 기대하지 않는다.
오류 상태에서 새로 쓰는 동작을 자동 실행하지 않는다. 저장 중 disabled UI를 강제로 활성화하지 않는다.
settings PUT의 부분 저장/지연·중복 입력은 C06에만 배정한다. 각 endpoint의 모든 오류코드/timeout 조합을 전수 검증했다고 주장하지 않는다.

## 3. 실제 HTTP·MySQL과 브라우저 주입 증거

| 경로 | 기록·판정 |
| --- | --- |
| 실제 경로 | React → Spring Security/Controller/Service → 신규 MySQL. 선별 HTTP 상태·DTO/허용 payload, 별도의 새 GET, 독립 JDBC snapshot을 연결 |
| 합성 실패 | 정확한 local URL/method/case의 page.route에서 서버 전달 전에 오류 응답. 브라우저 상태는 합성이며 실제 서버 오류/롤백을 입증하지 않음. DB 미변경은 별도 snapshot으로 확인 |
| 실제 응답 지연 | route.fetch로 받은 실제 서버 응답을 barrier 뒤에 전달. 서버 완료 시각과 브라우저 수신 시각 구분 |
| 커밋 후 응답 유실 | upstream 성공 + 새 DB 상태를 확인한 뒤 route.abort. 서버 커밋은 실제, 네트워크 실패는 주입. 브라우저가 성공을 수신했다고 기록하지 않음 |
| 실제 업무 거절 | C06-04의 400/INVALID_REQUEST와 DB 보존은 실제 서버 검증. 브라우저 모킹으로 대체하지 않음 |
| AI 대체 | 실제 AI endpoint라도 `E2eServer.FixedAi` 응답. 외부 Gateway, 모델 품질/비용/진짜 429·503 부하 검증은 아님 |

기존 `mcTraffic`는 pathname만 남겨 query 전환 근거가 부족하다. P1 전용 관찰에서는
requestId와 caseId, 시작/서버 완료/브라우저 완료/실패, 허용 query(page/size/tab/필터/from/to/categories),
주입 방식과 upstream 전송 여부/상태, 대상 ID와 결과 ID 집합을 남긴다.
브라우저 요청 수·서버 HTTP 수·OPTIONS·abort·route.fetch 수를 별도로 집계한다.
캐시/화면 부수 조회를 대상 사례의 PASS로 중복 합산하지 않는다.

오류 주입은 loopback의 정확한 endpoint만 대상으로 한다. redirect를 따라 외부로 나가지 않게
route.fetch의 자동 redirect/retry를 끄고, 기존 외부 네트워크 차단을 유지한다.
barrier는 finally에서 반드시 해제/정리하며 무한 대기로 테스트를 종료시키지 않는다.
JWT/비밀번호/DB 비밀/메일/외부 오류 원문은 저장하지 않는다. trace/screenshot/video/실패 DOM snapshot은 끈다.

새 근거는 `backend/build/full-service-e2e/<새 runId>/`에만 쓴다.
selection, source-before/after, test-attempts.jsonl, native results, browser/traffic/database 기록,
server-http.jsonl, run/cleanup 기록, SHA-256과 파일 존재 여부를 함께 집계한다.
P0 원본은 먼저 읽기 전용 지문을 확보하고 종료 후 동일성을 대조한다. 누락 파일은 복원한 것처럼 만들지 않는다.

## 4. 팀원 A 공통 실행기와 재사용/충돌

팀원 A의 [AUS P1 결과](AUTH_USER_SURVEY_E2E67_P1_VALIDATION.md)와 로컬 Git 이력을 확인했다.
`eab6972`의 AUS P1 변경 뒤 HEAD `011606c`가 MC P0 분기를 추가한 상태다.
현재 미커밋 충돌 표시는 없으나 A가 별도로 진행 중인 작업이나 fetch하지 않은 원격 변경까지 없다고 단정하지 않는다.
이번 작업에서 fetch/merge는 하지 않았다.

| 파일 | 현재 반영된 공유 동작 / P1 변경 경계 |
| --- | --- |
| `frontend/e2e/run.cjs` | AUS P1 23개 고정 선택·실패/중단 재검증, retention, 안전한 env/마스킹/정리. 현재 MC는 P0만 허용하고 P1은 거절. MC P1 전용 인자·scope·31개 allowlist·자원 상한을 분기 추가하며 AUS/retention 흐름을 재작성하지 않음 |
| `frontend/playwright.config.cjs` | MC P0/AUS P1/AUS P0 project 선택 순서 유지. `mc-p1` 전용 project만 추가, --no-deps. 상충 모드는 명시 거절 |
| `frontend/e2e/fixtures.cjs` | 각 테스트 reset/clean, 외부 차단, 비밀 마스킹 유지. P1 caseId/증거 이름 전달이 필요할 때만 작은 분기 추가 |
| `frontend/e2e/evidence-reporter.cjs` | AUS P1/MC P0의 종료별 즉시 JSONL, phase 결과 보존 유지. MC P1에서도 즉시 저장하며 P0 결과 파일명/경로는 보존 |
| `backend/src/e2e/java/e2e/E2eDatabase.java` | guardedConnection/reset/clean 및 기존 snapshot 구조 유지. P1 fixture 선택, 상세 일정·카테고리 설정 projection만 명시 모드에 추가. AUS·retention projection 유지 |
| `backend/src/e2e/java/e2e/E2eServer.java` | FixedAi/DisabledCrawler/MemoryMail/HTTP 관찰 재사용. 현재 계획상 수정 불필요 |

재사용: `lib/flows.cjs`의 login/responseTo/browserRequest, `lib/database.cjs`의 DB identity guard와 기록,
`mc-flows.cjs`의 article/link/calendar/edit/assertCards, 기존 reporter·서버 HTTP 관찰을 사용한다.
경합 요청을 구분해야 할 때는 pathname만 보는 `responseTo`를 그대로 쓰지 않고
P1 전용 helper에서 method+전체 query+대상 ID+요청 순서를 매칭한다.
`mc-session.cjs`의 P0 catalog/첫 C04 실행/resource-ledger/과거 추가 승인 파일은 재사용하거나 확장하지 않는다.
P1 allowlist/ledger/helper는 별도 파일로 둔다. P0의 선택/집계 동작을 그대로 유지한다.

수정 직전 아래 지문과 HEAD/worktree를 다시 확인한다. A 변경이 있으면 최신 내용을 다시 읽고 필요한 분기만 반영한다.
공통 파일을 과거 버전으로 통째로 되돌리거나 A의 결과를 재생성하지 않는다.

| 공통 파일 | 조사 기준 SHA-256 |
| --- | --- |
| `frontend/e2e/run.cjs` | `46A0E041E59342B4F25D8A931EA2EF4053638AE87067406FFDD2DEBFFB3399D2` |
| `frontend/playwright.config.cjs` | `63711961B8FDB3A7F77A79D0AE187114E2B7960AE8B3746DDF568A8DE15CEF8B` |
| `frontend/e2e/fixtures.cjs` | `34F072D1D6D773762D68AF0A539059C4150EC4A3922664BF415DE082A2D2DAEE` |
| `frontend/e2e/evidence-reporter.cjs` | `CFD8134F9568679DFB22A72475AE08AAF11B974BD9800D4EA23073FE0036007E` |
| `backend/src/e2e/java/e2e/E2eDatabase.java` | `A666C6B4FE1AEEDD7AD3C95459150775B10C436962C25E6DE3FF08396D8C98AD` |
| `backend/src/e2e/java/e2e/E2eServer.java` | `7DC015430DD4781B835E35AE00F16D543E108F5B37BC6475CDF4C319ED5E50CF` |

## 5. 필요한 환경과 Docker·MySQL 생성/정리 승인 범위

Java 21, Node, 기존 package/yarn.lock에 맞는 의존성, 기존 Playwright Chromium,
로컬 Docker Linux 엔진과 기존 `mysql:8.0.46-oraclelinux9` 이미지가 필요하다.
현재 가용성은 점검하지 않았다. 승인 후 preflight가 실패하면 중단하며 이미지/브라우저/의존성을 자동 다운로드하지 않는다.

| 항목 | 제안 범위 |
| --- | --- |
| 자원 수 | 신규 MySQL 8.0.46 컨테이너 **최대 1개**, 그 안의 신규 schema/전용 계정 각 1개. 31개 사례가 순차 공유하되 매 사례 데이터는 독립 reset/seed/clean |
| 식별 | `kkiri-e2e-<새 12hex>` / `kkiri_e2e_<새 12hex>` / `e2e_<새 12hex>`, 실행별 소유 label·컨테이너 ID·server UUID. 과거 runId/ledger 재사용 금지 |
| 저장 | 기존 이미지 `--pull=never`, `/var/lib/mysql` tmpfs 1GiB, named/anonymous volume 0. 실제 Mounts와 HostConfig.Tmpfs를 신규 소유 대상에서 검증 |
| 포트 | React 127.0.0.1:13000, Spring 127.0.0.1:18080, MySQL 127.0.0.1:13316, 제어 IPC용 임의 loopback 포트 1개. 점유 시 기존 서버 재사용·종료 없이 중단 |
| DB 가드 | loopback·schema·계정·버전·UUID·hostname·UTC 세션·schema 한정 권한·빈 schema를 확인한 뒤 E2E Entity DDL create-only, 이후 validate. SQL init never, seed NONE, e2e 단일 프로필 |
| 외부 요청 | 기존 FixedAi/MemoryMail/DisabledCrawler 유지, scheduler 제거 및 시작 시 검사. 실제 AI Gateway·학교 홈페이지·SMTP 요청 없음 |
| 정리 | 신규 소유 schema의 fixture 행 DELETE 및 전체 테이블 0행 감사, 소유 React/Spring/Chromium/제어 프로세스 종료. 소유 label+기록한 컨테이너 ID 재검증 후 해당 신규 컨테이너 1개만 삭제. tmpfs DB/계정은 함께 소멸 |
| 보존 | 새 실행 원본과 보고/JSON·해시는 보존. P0·타 도메인 결과, 기존 DB/컨테이너/볼륨/이미지, 수동 마이그레이션은 정리 대상 제외 |
| 실패/중단 | 자동 retry=0, 추가 컨테이너 생성 없음. 정리 불완전이면 실패 항목·신규 소유 식별자만 보고. 추가 자원이 필요한 재실행은 별도 승인 |

이 승인은 생성과 해당 **신규 소유 자원의 정리**를 함께 요청하는 범위다.
기존 보존 자원은 접속/inspect/시작/정지/변경하지 않는다. 신규 이름 충돌 확인은 그 정확한 이름만 조회한다.
전체 컨테이너/볼륨 목록, 기존 보존 DB의 SELECT, Docker prune, 이미지/볼륨 삭제는 범위 밖이다.
과거 5단계의 보존 컨테이너 `kkiri-mc-http5-mysql-20261022`, 볼륨 `kkiri-mc-http5-data-20261022`도 접근하지 않는다.
`test:e2e:clean`은 여러 실행의 공유 임시 경로를 지우므로 호출하지 않는다.
새 자원 삭제에 기존 공통 `--volumes` 동작을 적용하기 전 해당 대상의 volume 0을 확인하며, 소유 확인 실패 시 중단한다.

강제 종료 때문에 finally가 실행되지 않았으면 남은 자원을 새 실행이 자동 탐색/정리하지 않는다.
같은 승인 실행의 소유 ID·UUID·범위를 확인할 수 있는 복구만 수행하고, 입증되지 않으면 보고한다.

## 6. 예상 변경 파일과 승인 후 순서

이번 실제 변경은 **이 문서 한 개**다. P1 spec/helper나 제품 코드, 기존 P0 문서/결과는 변경하지 않았다.

승인 후 예상 신규:

- `frontend/e2e/tests/mc-p1.spec.cjs`: 31개 고정 ID의 독립 테스트.
- `frontend/e2e/lib/mc-p1-flows.cjs`: query별 barrier, 합성 실패/커밋 후 유실, P1 전용 관찰.
- `frontend/e2e/mc-p1-session.cjs`, `frontend/e2e/mc-p1-safety.test.cjs`: 선택·자원 예산·정리/마스킹 안전 검사.
- `backend/src/e2e/java/e2e/McP1Fixtures.java`: case별 합성 데이터 및 날짜 manifest.
- `backend/tools/matching-calendar-e2e67-p1-report.cjs`,
  `backend/MATCHING_CALENDAR_E2E67_P1_VALIDATION.md`,
  `backend/validation/matching-calendar-e2e67-p1-<실행일>.json`: 원본 연결·집계·정리 결과.

공통 수정 후보는 4절의 `run.cjs`, `playwright.config.cjs`, `fixtures.cjs`, `evidence-reporter.cjs`,
`E2eDatabase.java` 다섯 개다. 현재 `E2eServer.java`, `lib/database.cjs`, `lib/flows.cjs`,
기존 `mc-flows.cjs`는 변경 없이 재사용할 계획이다.

서비스/화면 변경은 계획하지 않는다. 실제 결함이 재현되면 원본 UI 상태·선별 요청·응답·새 GET·DB를 먼저 보존하고,
필요한 최소 변경과 계약 영향을 보고한다. 후보는 `RoommateSearchPage.js`, `ProfileCard.js`, `profileLikes.js`,
`CalendarPage.js`, `CalendarDataContext.js`, `CategorySettingsForm.js`, `calendarDates.js`, `MainPage.js` 및 해당 회귀 테스트다.
백엔드 Service/API/Entity는 실제 서버 원인이 입증된 경우에만 검토한다. API·스키마·익명성·시간 편집 정책을 임의 변경하지 않는다.

승인 후 순서:

1. 누락된 P0 최종 원본/수정 근거를 대조하고 보호 목록과 지문을 확정한다. 근거가 없으면 P0 확인 gate는 계속 BLOCKED로 둔다.
2. P1 전용 코드와 위의 최소 공유 분기를 구현한다. P0/AUS/retention 동작을 유지하는 선택·환경·마스킹·자원 상한 안전 검사를 수행한다.
3. 자원 생성 전에 `--list`로 `mc-p1`의 31개 ID/수, dependencies 없음, P0/타 도메인 제외를 검사한다.
   실행 명령은 구현 예정 `npm run test:e2e -- --mc-p1`이며 현재 실행기는 이를 거절한다.
4. preflight 후 새 자원 한 개만 생성하여 fixture manifest 감사, R07 → R08 → C05 → C06 → C07 순차 실행한다.
5. 실패·유실 원본 보존, 실제/주입 근거 구분, 소유 데이터/프로세스/컨테이너 정리 확인 후 최종 보고한다.
   동일 자원 내 수동 재검증도 실패·직접 영향 사례만 명시적으로 선택하고 별도 결과로 남긴다. 자동 전체 재실행은 하지 않는다.
6. 프론트 제품을 수정했다면 관련 기존 회귀와 `npm run build`, 백엔드 제품을 수정했다면 관련 인증/권한/동시성/롤백 회귀 및
   `gradlew.bat test bootJar`를 수행한다. Controller/DTO/Entity 문서는 `node backend/tools/sync-contract-docs.cjs --check`로 확인한다.
   소스 변경 범위와 검사 결과에 따라 필요한 검증만 실행하고 기존 전체 E2E를 추가로 돌리지 않는다.

## 7. 중복 제외와 미검증/정책 보류

- P0 R01~R06/C01~C04/X01의 정상 목록/정렬/추천/필터/상세/고정 AI/좋아요/CRUD/설정/소유권/계정 전환·만료는 다시 실행하지 않는다.
  로그인·최초 GET·복구 후 최소 성공은 fixture/오라클이며 별도 정상 시나리오 PASS로 집계하지 않는다.
- `user-workflow.spec.cjs` 전체, `mc-p0.spec.cjs`, `login.spec.cjs`, AUS/CCRA suite를 dependencies로 실행하지 않는다.
- 기존 MySQL 점수 계산/가중치/경합/DDL·실행계획, AI quota·semaphore의 서버 부하 행렬, Auth 제한/가입/SMTP,
  학교 크롤링·기숙사 수집기는 반복하거나 추가하지 않는다.
- `RoommateSearchPage.test.js`의 지연 취소, `ProfileLikes.test.js`의 pending/쓰기 실패,
  `CalendarDataContext.test.js`의 완료 DELETE+PUT 실패 재시도, `calendarDates.test.js`의 종일/연말 계산은 이미 단위 테스트가 있다.
  같은 mock 기반 단위 사례를 새로 복제하지 않는다. P1에서는 실제 React·HTTP·MySQL/브라우저 경계에서의 연결 결과만 추가 확인한다.
  제품 수정 시 해당 기존 테스트의 필요한 회귀만 사용한다.
- H2 통과를 MySQL 검증으로 대체하지 않는다. 운영 DB/배포, 실제 Gateway/학교/SMTP, 실제 다중 프로세스,
  모든 오류 코드·모든 날짜·모든 timeout 조합은 이번 계획의 검증 대상이 아니다.
- 뒤로가기의 필터/페이지 영구 보존, 자정 무조작 자동 갱신, 정확히 00:00 종료 시 달력별 시각 구간/날짜 강조 통일,
  PATCH null의 시간 제거 의미 등 새 정책을 만들지 않는다. 필요하면 관찰 근거와 정책 결정을 분리해 보고한다.

현재 실행 결과/빌드 결과는 모두 **이번 작업에서 NOT RUN**이다. 정적 문서 검토만 완료했다.
**P0 원본 확인을 선행 조건으로, 이 준비안의 P1 구현·31개 검증과 신규 컨테이너 최대 1개 생성/해당 소유 자원 정리를 승인받은 뒤 진행한다.**
