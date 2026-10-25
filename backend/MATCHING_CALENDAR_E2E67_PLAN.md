# Matching · Calendar 6·7단계 통합/E2E 승인 전 계획

2026-10-23 Asia/Seoul. 현재 브랜치 `test/frontend-backend-maching-calendar`, 조사 기준 HEAD `7dee1f9`.
이번 작업은 저장소·로컬 Git 이력 읽기와 이 계획 문서 작성뿐이다.
테스트·빌드·서버 기동·DB 접속·Docker 조회/생성/삭제는 하지 않았다. 아래 실행은 모두 미승인이다.

## 기준과 기존 검증 구분

6단계 사전 분석은 이전 ChatGPT 대화에 있었으며 저장소에 별도 원본이 없다는 사용자 설명을 기준으로 한다.
새 원본 경로를 추측하지 않는다. 사용자가 확정한 P0 11개(R01~R06, C01~C04, X01),
P1 5개(R07, R08, C05~C07)를 아래에 연결했다. 번호별 P0 작업명은 이번 요청의 범위를 분해한 것이다.

- [5단계 HTTP 결과](MATCHING_CALENDAR_HTTP5_VALIDATION.md): 29개 논리 PASS와 실제 HTTP 212건은 과거 결과다.
  이 실행기나 기존 MySQL 점수 계산·경합·DDL·실행계획 검증을 반복하지 않는다.
- [Matching 검증](MATCHING_VALIDATION.md), [관심 목록 검증](../frontend/PROFILE_LIKE_VALIDATION.md),
  [API](API.md), [ERD](ERD.md), [배포 지침](DEPLOYMENT.md)을 현행 코드와 대조했다.
- Auth/Survey 및 Community/Chat/Report/Admin의 기존 결과·원본·전용 테스트는 보호한다.
  인증 로그인과 완성 설문은 담당 화면에 필요한 fixture이며 별도 Auth/Survey 검증으로 집계하지 않는다.
- 현재 상태: P0 11개 NOT RUN, P1 5개 NOT RUN. 실제 AI Gateway·학교 크롤링은 승인 환경 부재로 BLOCKED.

## P0 실행 범위 — 11개 유지

| ID | 실행 범위와 판정 근거 | 재사용 / 중복 제외 |
| --- | --- | --- |
| R01 | 전체 목록 첫·다음 페이지(size=9), 후보 ID·순서·페이지 메타데이터·UI 카드와 새 GET 일치 | user-workflow 목록 진입과 RoommateSearchPage 테스트; 서버 정렬 산식 재검증 제외 |
| R02 | 추천 탭의 실제 ID·숫자 점수·최대 9개 표시, 빈 결과의 별도 안내 | 기존 추천 GET assertion; 추천 UI는 page=0,size=9이며 새 페이지 UI 추가 없음 |
| R03 | 기존 설문 필터 적용·칩 제거·초기화, 전체 목록 필터 후 페이지, 추천 필터 요청·결과 일치 | RoommateSearchPage 및 roommateApi 테스트; 추천은 상위 9명 안에서 필터하고 10위 이하 보충 없음 |
| R04 | 같은 대상의 카드·상세·새 GET에서 점수·닉네임·소개·설문 배열 일치, 상세 닫기/목록 복귀 | user-workflow 상세 assertion; 100→17→83 재계산·가중치 변경 테스트는 실행하지 않음 |
| R05 | 추천 카드 hover/focus로 실제 Spring AI 분석 POST, targetUserId·matchScore·고정 설명과 오버레이 일치 | 기존 E2eServer.FixedAi 재사용. Gateway 자체 HTTP/모델 품질 검증은 아님 |
| R06 | 카드 등록·상세 취소·관심 목록/개수/하트 연동, 7명에서 마지막 페이지 1명 취소 후 6명·이전 페이지 복귀 | user-workflow C 및 ProfileLikes 테스트; 정상 좋아요는 이 사례에서만 집계 |
| C01 | 정상 15분 단위 개인 일정 생성→상세→제목 수정→재조회→삭제 | user-workflow D1/D2; 기존 Auth 가입·설문·JVM 재시작 step은 제외 |
| C02 | 사용자 카테고리 생성·이름/색상/표시 설정·재조회, 비활성 설정 보존·명시적 빈 카테고리 삭제 | user-workflow 카테고리 흐름과 CalendarDataContext 테스트; 삭제와 숨김 구분 |
| C03 | 게스트/A/B에서 합성 공개 일정 표시·타인 개인 일정 제외, editable=false 조작 차단, 타인 변경 403·데이터 보존 | user-workflow E; UI 정상 가시성과 최소 소유권 대조만 수행 |
| C04 | 기존 종일·시작만·종료만·15분 외 일정의 제목만 수정, 정상 일정 대조군 포함 | 실제 브라우저 재현을 먼저 수행. 저장 전후 날짜·시간·null·기간·설명·카테고리 보존 검사 |
| X01 | 동일 브라우저 A→로그아웃→B, 관심·추천 점수·개인 일정·카테고리 격리 및 실제 JWT 만료 후 재로그인 복구 | 기존 login/logout/control(restart, ttlSeconds=8)와 세션 helper; 토큰 위조로 만료 대체하지 않음 |

R01의 페이지는 전체 목록 UI, R02의 추천은 최대 9개 UI다. 추천 API의 별도 페이지/정렬 HTTP 행렬은 반복하지 않는다.
R03은 정상 필터 기능, R07은 전환 중 경합에만 배정한다. R06은 정상 저장, R08은 실패/응답 유실에만 배정한다.
C01은 정상 일정 편집, C04는 기존 시간 형태 보존, C05는 표시 경계, C06/C07은 복구 경로에만 배정한다.
X01의 정상 계정 전환과 만료는 다른 사례에서 다시 독립 집계하지 않는다.

### C04 브라우저 재현 기준

같은 소유자의 일정 5개를 준비한다: 정상 09:00~10:00, 종일 null/null,
시작만 10:00/null, 종료만 null/11:00, 비정각 09:07~10:22.
상세에서 수정 창을 열고 제목만 바꾼 뒤 저장한다. 각 사례에서 다음을 남긴다.

1. 최초 HTTP GET과 DB 원본, 편집창에 들어간 날짜·시간·선택값.
2. 저장 클릭 후 검증 메시지와 PATCH 발생 여부. UI 차단이면 요청 0건으로 기록한다.
3. PATCH가 발생하면 비밀 없는 payload 필드·응답 상태와 전체 일정 DTO, 별도의 새 GET과 DB 대조.
4. 실패·차단·시간 변형을 서비스와 프론트 중 어디서 발생했는지 구분하고 최초 원본을 보존한다.

정적 조사상 CalendarPage.validateDraft는 두 시간 모두 CALENDAR_TIME_OPTIONS(15분 간격)에 있어야 통과한다.
API는 nullable/한쪽 시간/임의 분을 허용하므로 불일치 가능성이 있지만, 브라우저 재현 전 결함 확정이나 수정을 하지 않는다.
제목만 바꾸기 위해 시간을 채우거나 반올림하여 테스트를 통과시키지 않는다.
PATCH의 null은 기존 값 유지 계약이다. 시간 제거 등 새 편집 정책은 추가하지 않는다.

## P1 실행 범위 — 사용자 확정 5개

| ID | 입력 / 관찰 | P0와 구분 |
| --- | --- | --- |
| R07 | 필터·탭·페이지 빠른 전환, 필터 취소, 뒤로가기, 이전 실제 GET 응답 지연 후 해제. 현재 URL/화면/최신 응답 ID가 일치하고 이전 응답이 덮어쓰지 않는지 확인 | 정상 목록·필터 assertions 반복 제외. 필터 취소는 칩 제거와 적용 전 모달 취소를 구분 |
| R08 | AI 오류·429 AI_ANALYSIS_RATE_LIMITED·503 AI_ANALYSIS_BUSY 안내 후 사용자가 재선택해 복구. 좋아요 POST/DELETE 실패 및 서버 커밋 후 응답 유실, 로딩 해제·성공 오표시·자동 중복 요청 여부·수동 GET 확인 | 정상 AI/좋아요는 fixture. route 주입과 실제 서버 제한·장애를 명확히 구분하며 quota/동시성 MySQL 재검증 제외 |
| C05 | 월말·연말·기간 양끝·자정·여러 날 일정이 전체 달력/일정 목록/메인 미니 달력에 같은 날짜·제목·시간 의미로 표시되는지 확인 | C04의 제목 수정 반복 제외. 각 UI가 제공하는 표시 항목을 비교하며 없는 시간 문구를 새로 요구하지 않음 |
| C06 | 카테고리 POST/DELETE 일부 완료 후 PUT 실패·재시도, 완료된 생성/삭제 중복 전송 방지, 다른 달에 일정이 있는 카테고리 삭제 거절 및 새 GET과 화면 일치 | 정상 설정은 C02. 현재 조회 월에 일정이 없는 것과 서버 전체 기간 참조를 구분 |
| C07 | 일정 GET·카테고리 GET 실패/지연, 일정 저장 POST/PATCH/DELETE 실패/지연, 중복 클릭·초안 보존·로딩 해제·명시적 재시도 복구 | 카테고리 다단계 저장은 C06, 정상 CRUD는 C01. 복구용 최소 성공 요청은 중복 사례로 세지 않음 |

오류 주입은 기존 page.route/route.fetch로 실제 HTTP 응답을 지연·대체·유실한다.
route.fetch로 서버 커밋을 확인한 뒤 브라우저 응답을 끊는 경우에는 DB 변경과 UI 미확정 상태를 따로 판단한다.
자동 재전송 없이 GET으로 현재 상태를 확인하고 사용자 재시도로 복구한다.
게이트웨이 오류·429·busy의 주입은 UI 계약 검증이며 실제 Gateway 장애/부하 검증으로 보고하지 않는다.
P1 5개는 논리 사례 수다. 구현 시 입력별 독립 테스트 목록과 수를 고정하고, 소속 검증이 모두 완료되어야 논리 PASS로 판정한다.

## fixture 추가 계획

기본 E2eDatabase seed는 일반 사용자 2명·관리자 1명과 기준정보만 만든다.
완료 설문·선호·관심·일정·카테고리는 없으므로 현재 상태로 필요한 페이지/편집 경계를 검증할 수 없다.

| fixture | 추가할 데이터와 확인 |
| --- | --- |
| 계정/후보 | A·B와 별도로 ACTIVE·완료 프로필/완료 설문 후보 최소 12명. A/B 선호를 준비해 각 요청자 기준 60점 이상 후보 10명 이상을 최초 GET으로 확인. 후보별 고유 이름·소개·scalar/multi 답변 구분 |
| 필터/점수 | 기존 유효 설문 fixture를 재사용해 서로 다른 점수·동점·필터 일치/불일치와 상위 9명 밖 일치 후보 준비. 산식 코드 복제·직접 점수 행 주입·점수 재계산 행렬 제외. 실제 API 점수를 UI/상세/새 GET과 비교 |
| 관심 | A→후보 7명 이상, B는 다른 집합 또는 0명. 좋아요 ID DESC에서 페이지당 6명·마지막 1명이 되도록 생성 순서/ID manifest 고정. R06의 등록 대상 1명은 별도로 남김 |
| 카테고리 | SCHOOL/DORMITORY 시스템 2개, A/B별 사용자 카테고리, 빈 삭제용·비활성·다른 달 일정 보유 카테고리. 기본 GET이 시스템 카테고리를 생성한다고 가정하지 않음 |
| 일정 | A/B 개인 일정, 합성 공개 SCHOOL 일정, C04 5종, 월말/연말·자정·기간 경계, 다음 달 카테고리 삭제 방지용 일정 |

대량 데이터와 공개 일정은 신규 소유 DB에 한정한 E2E 전용 fixture로 준비한다.
가능하면 Entity/기존 유효 fixture 생성 함수를 사용하고, 실제 CRUD 검증은 브라우저→HTTP로 수행한다.
공개 일정은 합성 CRAWLED 행이며 실제 수집 성공을 뜻하지 않는다. 기숙사 수집기는 구현하지 않는다.
fixture 준비 전후 ID·개수·공개 가능 조건과 추천 후보 수를 확인하고 부족하면 해당 테스트를 시작하지 않는다.

## 재사용과 공통 코드의 최소 변경

- `frontend/e2e/tests/user-workflow.spec.cjs`: B/C/D/E의 흐름·assertion을 새 담당 helper/독립 spec에서 재사용한다.
  하나의 장기 test에 묶인 원본 전체를 실행하면 금지된 점수/가입 검증까지 반복하므로 원본은 통째로 실행하지 않는다.
- `e2e/fixtures.cjs`, `lib/flows.cjs`, `lib/database.cjs`: 격리 컨텍스트·외부 네트워크 차단·login/logout·responseTo·browserRequest·DB guard 재사용.
  helper는 담당 `lib/mc-flows.cjs`에 두고 공통 helper 변경은 필요한 경우에 한정한다.
- 기존 프론트 테스트: `RoommateSearchPage.test.js`, `roommateApi.test.js`, `ProfileLikes.test.js`,
  `CalendarPage.test.js`, `CalendarDataContext.test.js`, `calendarDates.test.js` 재사용. 이번에는 실행하지 않음.
- `e2e/run.cjs`: 담당 P0/P1 선택 모드·허용 ID/실행 목록·resource 수 상한·결과 scope를 추가할 계획.
  기존 생성/정리/재시작 guard를 재사용하며 AUS/CCRA 전용 경로·환경 플래그는 빌려 쓰지 않는다.
  P0와 P1 사이 결과 검토 gate를 두고, 실패 재실행은 실패·영향 사례만 명시적으로 선택한다.
- `playwright.config.cjs`: 담당 spec만 선택하는 project 추가. workers=1/retries=0 유지,
  다른 도메인·기존 login suite를 dependencies로 자동 실행하지 않는다.
- `E2eDatabase.java`: 담당 모드에서만 후보/일정 seed 및 읽기 전용 상세 snapshot 확장.
  현재 events projection에는 종료일·종료시간·allDay·설명·category/source가 없고,
  categories에는 설정 label/color/enabled가 없어 C04/C06 근거가 부족하다. 기존 모드 snapshot 형태는 보존한다.
- `E2eServer.java`: 기존 FixedAi·DisabledCrawler·MemoryMail·HTTP 관찰·짧은 JWT TTL 재사용. 현재 계획에는 변경 필요 없음.
- `evidence-reporter.cjs`: 담당 모드에도 테스트 종료별 결과를 즉시 보존하는 조건만 확장할 계획.
  요청 시작/실패/선별 응답 관찰은 담당 helper에 둔다. 본문·토큰 전체 수집은 하지 않는다.

## 자원 생성·삭제 승인 범위 제안

| 구분 | 승인 요청할 범위 / 제한 |
| --- | --- |
| P0 1차 실행 | 신규 MySQL 8.0.46 컨테이너 최대 1개, 신규 schema/전용 계정 각 1개. 현재 브랜치에서 담당 테스트만 실행 |
| P1 실행 | P0 결과 검토 뒤 별도 승인 시 신규 컨테이너 최대 1개 제안. 지금 P0 승인만으로 P1을 실행하지 않음 |
| 이미지/저장소 | 기존 `mysql:8.0.46-oraclelinux9`를 `--pull=never`로 사용. 이미지 다운로드·생성 없음. `/var/lib/mysql` tmpfs 1GiB, named/anonymous volume 0 |
| 연결 | React 127.0.0.1:13000, Spring 127.0.0.1:18080, MySQL 127.0.0.1:13316. Node 제어 IPC는 임의 loopback 포트 1개. 포트 점유 시 기존 프로세스를 종료하거나 재사용하지 않고 중단 |
| 신원 | 실행 ID별 `kkiri-e2e-<12hex>` / `kkiri_e2e_<12hex>` / `e2e_<12hex>`, 소유 라벨·컨테이너 ID·UUID·권한·신규 빈 schema 확인 |
| 초기화 | 신규 빈 schema에서만 E2E Entity create-only 후 validate. SQL init never, seed NONE, e2e 단일 프로필·스케줄러 제거. 실제 AI/SMTP/학교 네트워크 연결 없음 |
| 정리 | 승인한 실행이 만든 fixture 행만 정리·0행 확인, 소유 React/Spring/Chromium 프로세스 종료, 소유 라벨+ID 확인한 해당 신규 컨테이너만 삭제. tmpfs DB·계정도 컨테이너와 함께 소멸 |
| 재실행 | 자동 retry=0. 한 번의 실행 승인으로 추가 컨테이너 생성하지 않음. 실패/중단이면 원본·정리 결과를 보고하고 추가 자원이 필요한 재실행은 별도 승인 |

기존 보존 컨테이너·볼륨·DB에 접속/inspect/시작/정지/변경하지 않는다.
이름 충돌 확인은 정확한 신규 이름의 존재 여부만 조회한다. Docker prune·기존 볼륨 삭제는 범위 밖이다.
정리 권한도 생성과 함께 명시적으로 승인받아야 한다. 정리 실패 시 광범위 종료/삭제로 대체하지 않는다.
이미지/브라우저 존재와 포트/엔진 상태는 승인 후 preflight에서 확인할 예정이며 현재 READY라고 주장하지 않는다.

## 승인 후 실행 순서와 근거

1. 담당 spec/helper·fixture·선택 gate 구현, 실행 목록 고정. 외부 자원 없이 구문/마스킹/선택 안전 검사.
2. P0 자원 생성·실제 테스트·소유 자원 정리 승인이 확인되면 preflight 후 새 환경 기동 및 fixture 감사.
3. C04를 가장 먼저 실제 브라우저에서 재현하고 정상 C01 대조군을 확인한다.
4. R01→R02→R03→R04→R05→R06→C01의 나머지→C02→C03→X01 순서로 독립 실행한다.
   각 테스트는 자체 fixture를 갖고, X01의 TTL 8초/복구 재시작은 마지막에 둔다.
5. 최초 FAIL·중단 원본을 남기고 원인/레이어/최소 수정 파일·계약 영향을 보고한다.
   이번 준비 승인으로 제품 코드를 미리 수정하지 않는다. 수정 후 관련 회귀만 재실행하며 불필요한 전체 suite는 피한다.
6. P0 결과·HTTP·새 GET·DB·정리를 검토하고 보고한다. P1은 별도 승인 뒤 R07→R08→C05→C06→C07 순서.
7. 프론트 수정 시 관련 기존 테스트+회귀와 `npm run build`. 백엔드 제품 수정이 필요하면
   관련 인증/권한/롤백 회귀 및 AGENTS의 `gradlew.bat test bootJar`, 계약 snapshot 검사를 수행할 계획.
   완료된 별도 MySQL 점수 계산/동시성 검증은 다시 실행하지 않는다.

브라우저→React→실제 Spring Security/Controller/Service→신규 MySQL 경로를 사용한다.
UI 값·선별 실제 HTTP 응답·별도 새 GET·독립 JDBC snapshot을 사례 ID/실행 ID로 연결한다.
브라우저 요청 시작·응답·실패 이벤트와 서버 HTTP 관찰은 따로 집계한다. abort/route.fetch/OPTIONS 차이를 숨기지 않는다.
화면의 자동 Community 조회 등은 부수 트래픽이며 타 도메인 검증으로 집계하지 않는다.
JWT·비밀번호·인증번호·메일·AI 자격증명은 저장하지 않고 trace/screenshot/video/실패 DOM snapshot은 끈다.
원본 `backend/build/full-service-e2e/<runId>/`와 SHA-256, PASS/FAIL/BLOCKED/NOT RUN, 주입 여부·정리 결과를 보존한다.
중단 테스트를 원래 Playwright PASS로 재작성하지 않는다.

## 예상 변경 파일과 현재 변경

예상 신규: `frontend/e2e/tests/mc-p0.spec.cjs`, `mc-p1.spec.cjs`, `frontend/e2e/lib/mc-flows.cjs`,
담당 집계/선택 안전 검사 도구, 최종 validation Markdown/JSON.
예상 공통 수정: `frontend/e2e/run.cjs`, `frontend/playwright.config.cjs`,
`backend/src/e2e/java/e2e/E2eDatabase.java`, `frontend/e2e/evidence-reporter.cjs`의 담당 분기만.

브라우저로 결함이 확인된 경우의 후보 파일은 CalendarPage.js/CalendarDataContext.js/calendarDates.js,
RoommateSearchPage.js/ProfileCard.js/profileLikes.js 및 해당 회귀 테스트다.
MyPage/MainPage/API/service는 연결된 실제 결함 근거가 있을 때만 필요한 최소 범위를 보고한다.
공개 API·스키마·점수 산식·검색/정렬 UI·기숙사 수집기 변경 계획은 없다.

**현재 실제 변경은 이 계획 문서 1개뿐이다.** 테스트/빌드 결과·결함 수정·자원 준비 완료를 주장하지 않는다.
커밋·Push·PR·Merge는 하지 않았다. P0/P1 실행 및 신규 자원 생성·정리는 사용자 별도 승인 대기다.
