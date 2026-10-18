# Like 최종 통합·브라우저 E2E 검증

검증일: 2026-10-18 (Asia/Seoul). **전체 판정: 수정 후 배포.**
최신 소스의 핵심 Like 브라우저 흐름은 통과했지만, nullable 생활관 게시글의 목록 누락(N1)을 발견했다.
제품 코드는 수정하지 않았다. 과거 실행 결과는 이번 통과 수에 합산하지 않았다.

## A. 환경과 소스 보존

| 항목 | 이번 실행 |
|---|---|
| Git revision | `d211da735d04e0fb4cf717e98798dfe10f6a102e` |
| 시작 작업 트리 | clean. 추적 파일 786개의 SHA-256을 저장하고 종료 시 대조 |
| Backend | Temurin Java 21.0.9, Spring Boot 4.1.1, Gradle Wrapper 9.7.1 |
| Frontend | Node 24.11.0, React 19.2.8, Axios 1.18.1, CRA production build |
| 브라우저 | Chrome 155.0.8059.80, 별도 Edge 155.0.4283.62 프로세스, headless |
| E2E 도구 | Playwright Core 1.64.1, `tmp/like-final/browser/node_modules`에만 설치 |
| 접속 | React `http://localhost:3000`, Boot `127.0.0.1:18081` / `:18082` |
| MySQL | 8.0.46 / InnoDB / REPEATABLE READ, JDBC UTC, JVM Asia/Seoul |
| 격리 서버 | `kkiri-survey-mysql-test`, image `mysql:8.0.46-oraclelinux9`, label `kkiri.validation=survey-only`, `127.0.0.1:3311` |
| 서버 identity | UUID `a087ec36-ca16-11f1-b5e2-de405dffbee0`와 hostname/DB/current_user를 연결마다 검증 |

기존 `AGENTS.md`, `agent.md`, API/ERD/DEPLOYMENT/마이그레이션 안내와 관련 보고서를 읽고,
Like Controller/Service/Repository/Entity/DTO, 실제 프론트 호출·인증·상태 관리 경로를 대조했다.
Survey/Matching 전체 설계 재분석이나 제품 로직 변경은 하지 않았다.

`clean` 전 기존 `build/like-validation`, `build/profile-like-fix`, `build/post-like-validation`을
`../tmp/like-final/archive`로 복사하고 clean 후 원래 경로에도 복원했다.
과거 REPRODUCED 기록이나 성능 보고서는 덮어쓰지 않았다.

각 MySQL 실행은 identity/label/port 확인 후 새 UUID 기반 DB와 해당 DB 전용 계정을 만든다.
새 빈 스키마에서만 Entity create-only DDL을 만들고, 실제 두 Boot JVM은 **validate / SQL never / seed NONE**이다.
EnvironmentSafetyInitializer를 유지했다. 기본/prod/local/test 설정을 우회해 기존 DB에 연결하지 않았다.
개발·운영 DB에 접속하거나 운영 migration/인덱스를 적용하지 않았다.

API·Service·인증·JPA·MySQL은 실제 구현이다. API 응답 mock은 없다.
외부 SMTP는 호출 시 실패하는 검증 전용 transport로 대체하고, AI는 로컬 stub으로 격리했다.
SMTP·AI 호출은 0이며 스케줄러/시드/크롤링은 실행하지 않았다.
관찰용 JDBC proxy와 검증용 제어 서버는 제품 API 밖에 있으며, 생성한 DB만 접근한다.

브라우저는 실제 로그인 UI의 이메일 확인·로그인 API를 사용한다. A/B/C는 ACTIVE·완료된 프로필/설문/선호를 갖는다.
D는 프로필 설문 미완료, E는 SUSPENDED다. 일반/익명 댓글과 답글, 일반/익명 게시글 및 삭제 대상도 준비했다.
최종 브라우저 DB에는 페이지 이동용 생활관 보유 게시글 9개와 nullable 경계 게시글 1개를 준비했다.

이 프론트 build는 검증 API 주소 `http://127.0.0.1:18081/api`를 사용한다.
실제 배포 산출물은 운영 API 주소로 별도 build해야 한다.

## B. 자동화 결과

| 검증 | 통과 | 실패 | skip/미실행 | 기준 |
|---|---:|---:|---:|---|
| Backend 전체 | 684 | 0 | 0 | 55 suites, H2/test, clean 실행 |
| Frontend 전체 | 178 | 0 | 0 | 19 suites, JSDOM/API mock 포함 |
| Backend build | 1 | 0 | 0 | bootJar 성공 |
| Frontend build | 1 | 0 | 0 | Compiled successfully |
| ProfileLike API 전용 그룹 | 11 | 0 | 0 | 아래 47개 중 일부 |
| PostLike API 전용 그룹 | 11 | 0 | 0 | 아래 47개 중 일부 |
| CommentLike·답글 API 전용 그룹 | 20 | 0 | 0 | 아래 47개 중 일부 |
| 공통 인증·제약·조회 그룹 | 5 | 0 | 0 | 전용 그룹과 합쳐 47개 |
| MySQL 동시성 | 26 | 0 | 0 | 47개 중 병렬 요청/삭제·상태 경합 그룹, 별도 합산 금지 |
| MySQL M3·정렬·빈 목록 | 6 | 0 | 0 | 별도 새 DB, 27개 목록 측정과 빈 목록 6조합 포함 |
| 실제 브라우저 E2E | 19 | 1 | 0 | 핵심 19건 PASS, 추가 N1 경계 검사 FAIL |
| API 계약 스냅샷 | 1 | 0 | 0 | 70 endpoints / 71 record DTOs / 29 entities |

M3 추가 SQL·정렬·빈 목록 검증 결과는 아래 성능 절에 기록했다.
표의 MySQL 동시성은 API 그룹의 부분집합이다. HTTP 요청 수, 하위 assertion, 브라우저 재실행을 테스트 수에 중복 합산하지 않는다.
단위/컴포넌트 테스트를 실제 브라우저 E2E로 계산하지 않았다.

Backend 명령은 `cd backend; .\gradlew.bat clean test bootJar`였다.
Frontend는 CI=true로 `npm.cmd test -- --watch=false --runInBand --json`과 `npm.cmd run build`를 실행했다.
계약 검사는 루트의 `node backend/tools/sync-contract-docs.cjs --check`를 사용했다.
Node의 fs.F_OK deprecation 안내는 있었으나 production build는 성공했다.

전체 테스트/빌드 증거: `../tmp/like-final/backend.log`, `backend-summary.json`,
`frontend-results.json`, `frontend-tests.log`, `frontend-build.log`, `frontend-exit.json`.
Git·버전·리소스·실행별 집계는 같은 경로의 `summary.json`, `source-before.json`, `source-check.json`,
`docker-identity.json`, `browser-cleanup.json`을 따른다.

## C. 도메인별 결과

### ProfileLike — 배포 가능 (이번 격리 환경·현재 계약 범위)

- M1 재발 없음: 카드 등록 → 상세 하트 → 관심 탭과 실제 DB 1행을 확인했다.
  상세 취소 성공 후 목록 제거·0명·DB 0행, **관심 목록 재조회 1회**를 확인했다.
  7명/2페이지에서 마지막 항목 취소 후 6명/유효한 1페이지로 보정되고 빈 요청 페이지와 보정 페이지를 각각 한 번 조회했다.
- M2 재발 없음: A의 실제 쓰기 응답을 보류한 채 UI 로그아웃/C 로그인 후 원본 응답을 전달했다.
  A의 관계만 저장되고 C의 하트·목록·개수는 변하지 않았다.
  A를 검증 DB에서 잠시 비활성화해 얻은 **실제 401 원본 응답**도 새 로그인을 해제하지 않았다.
- 같은 사용자 UI 재로그인 후 새 세션에서 취소한 상태에 이전 성공이 덮어쓰지 않았다.
  **동일 JWT**도 별도로 검사했다: 실제 로그인 발급 토큰을 UI 로그아웃 후 다른 탭 storage 이벤트로 복원하고,
  서버의 실제 사용자 검증을 거친 새 세션에서 이전 성공/401을 각각 전달해도 현재 상태·인증을 유지했다.
  로그인 API 응답을 임의의 동일 JWT mock으로 바꾸지 않았다. 일반 UI 재로그인과 storage를 통한 동일 JWT 재사용을 구분한다.
- 새로고침, 별도 Edge 로그인, 게스트 liked=false, 다른 탭의 계정 변경, pending 연속 클릭 차단,
  네트워크 차단 후 오류·하트/DB 유지와 재시도도 통과했다.
- 실제 API에서 본인/미완료/비활성 대상 거절, 등록 멱등성, 없는 관계 DELETE 400을 확인했다.
  점수 null 상태의 유효한 관계 보존·숨김 대상 count 제외와 두 JVM 조회 일치는 API 그룹에서 검사했다.
- 프론트 전체에는 상세의 늦은 GET, 세션 전환/unmount, StrictMode, 동일 JWT 세대 및 지연 401 회귀가 포함된다.
  이 컴포넌트 테스트의 세부 조합을 모두 브라우저에서 반복한 것으로 보고하지 않는다.

### PostLike — 수정 후 배포

- 일반 작성 경로와 같은 생활관 보유 fixture에서 등록/취소, 상세·목록 likes/liked·DB 일치,
  A/B 관계 독립, 게스트 liked=false, 실제 새로고침을 통과했다.
- 실제 브라우저 LIKES 정렬의 항목 순서를 API와 비교하고 1→2페이지 이동 시 순서·중복 없음 및 서버 페이지 일치를 확인했다.
  DB 대조 API 검증에서 동점 createdAt DESC/postId DESC, 검색·카테고리·생활관 필터와 본인 목록을 검사했다.
- 중복 POST, 없는 관계 DELETE 200, 무인증/위조 토큰 거절, 삭제 대상 거절,
  타인 관계 보존과 동시 쓰기/삭제의 롤백은 MySQL API 그룹에서 통과했다.
- M3의 행별 countLikes/isLiked 재발은 없었다. 공개/본인 목록의 실제 SQL 측정에서도 일괄 조회를 유지했다.
  **N1: nullable 생활관 게시글의 목록 누락은 해결되지 않았으므로 PostLike 연관 목록의 무조건 배포 승인은 하지 않는다.**

### CommentLike·답글 — 배포 가능 (이번 격리 환경·현재 계약 범위)

- 실제 UI에서 일반 댓글 등록·새로고침·취소와 DB 집계가 일치했다.
  답글의 정확한 commentId로 요청하며 원 댓글 관계가 변하지 않고 답글만 취소됐다.
- 익명 댓글·답글에서도 likes/liked가 정확하고 author.id=null, author.major=null,
  게시글별 익명 번호와 인증 사용자 기준 mine을 유지했다. 사용자 변경 후 liked=false와 번호 보존을 확인했다.
  Like 요청은 댓글/답글 ID를 사용하고 성공 data는 null이며 익명 작성자 ID를 요구하지 않는다.
- 중복/없는 관계 DELETE, A/B 관계·집계, 두 JVM 중복 등록, 댓글/원 게시글 삭제 경합,
  삭제된 원 게시글의 댓글 등록 거절과 제약·롤백을 실제 MySQL에서 확인했다.
- L1 문서 충돌 재발 없음. 익명 게시글은 현재 계약대로 실제 author.id를 유지한다.
  댓글의 비식별 처리를 미해결 결함으로 분류하지 않는다.

### SQL·실행계획 (M3)

성능 실행은 별도의 새 DB에서 게시글 1,000개와 좋아요 3,500개를 준비했다.
실제 HTTP 요청의 JDBC execute SQL을 수집하며, 연결 identity 검사 SQL은 애플리케이션 SQL 수에 포함하지 않는다.
공개/본인 목록, LATEST/VIEWS/LIKES, 1/6/20행, 가능한 게스트 조합의 **27개 측정 모두**
로그인 조립용 Like SQL 2회, 게스트 1회였다. 추가한 공개/본인 빈 목록·세 정렬 6조합은 조립용 0회였다.
LIKES 정렬 native SQL 1회는 조립용 SQL과 구분했다. M3 재발 없음이다.

이번 실제 측정값:

| 경로 / 정렬 / 인증 | 행 수 1 / 6 / 20의 전체 SQL | 조립용 Like SQL | 별도 Like 정렬 SQL |
|---|---|---|---|
| 공개 / LATEST / 로그인 | 10 / 31 / 87 | 2 / 2 / 2 | 0 |
| 공개 / LATEST / 게스트 | 7 / 23 / 65 | 1 / 1 / 1 | 0 |
| 본인 / LATEST / 로그인 | 10 / 30 / 86 | 2 / 2 / 2 | 0 |
| 공개 / LIKES / 로그인 | 10 / 30 / 86 | 2 / 2 / 2 | 1 / 1 / 1 |

예를 들어 로그인 공개 LATEST 20행의 87회는 Like 조립 2, 페이지/count 2, 인증 사용자 1,
작성자 프로필 20, 댓글 수 20, 이미지 여부 20, 신고 여부 20, 생활관 lazy 조회 2다.
나머지 행별 SQL을 Like SQL로 계산하거나 전체 N+1을 제거했다고 보고하지 않는다.
과거 125→87 비교를 이번 baseline으로 재사용하지 않았으며, 이번에는 최신 소스만 측정했다.

SHOW CREATE/INDEX, EXPLAIN JSON 및 ANALYZE도 다시 저장했다.
현재 LIKES는 게시글 ALL scan → ACTIVE 필터 → indexed correlated count → filesort → LIMIT이며,
count는 기존 `(post_id,user_id)` UNIQUE의 post_id prefix를 covering lookup으로 사용한다.
정렬 때문에 dependent=true/cacheable=false와 using_filesort=true가 남는다. filesort만으로 디스크 정렬이라고 단정하지 않는다.
후속 기능 검증 후 999 ACTIVE/총 1,000행에서 count probe는 실제 1,001 loops,
author/category/dorm 필터에서는 author FK 인덱스 500행을 읽어 후보 166행/166 count loops였다.
로컬 단회 ANALYZE의 Limit 시간은 각각 약 2.92ms/0.869ms이며 운영 SLA/p95로 일반화하지 않는다.
기존 도구의 집계 JOIN 대안도 읽기 전용으로 순서·계획을 비교했지만 제품 정렬 SQL이나 인덱스는 변경하지 않았다.
계획·측정 원문은 성능 run의 `likes-sort-plans.json`, `measurements.json`, `empty-query-counts.json`,
분류 집계는 `../tmp/like-final/summary.json`의 sql 배열에 있다.

## D. 발견한 제품 문제 N1

**Medium — 생활관이 null인 ACTIVE 게시글이 LATEST/VIEWS 공개 목록에서 누락된다.**

- 위치: [CommunityPostRepository.java:31](src/main/java/com/kkirikkiri/community/post/repository/CommunityPostRepository.java#L31).
  비교 경로는 `CommunityPostReadRepository.findByLikes`와 `findAllByAuthor_UserIdAndStatus`다.
  Entity `CommunityPost.java:45` 및 ERD의 dormitory_id는 nullable이며 DTO도 null을 반환할 수 있다.
- 원인: `p.dormitory.dormitoryName` 경로가 Hibernate SQL의 **INNER JOIN dormitory**를 만든다.
  생활관 필터가 null이어도 JOIN이 먼저 nullable 행을 제외한다. LIKES native SQL과 본인 목록은 이 JOIN이 없다.
- 재현: 새 격리 DB에 ACTIVE 게시글 10개 중 1개의 dormitory_id를 null로 둔다.
  동일한 A 인증·필터 없음으로 공개 목록 LATEST/VIEWS/LIKES, 본인 목록과 해당 상세를 조회한다.
- 실제 결과: 최종 브라우저 DB의 postId=10은 LATEST/VIEWS content에서 빠지고 totalElements=9다.
  LIKES와 A의 본인 목록에는 포함되며 totalElements=10, 상세는 200/dormitory=null이다.
  `D1-null-dormitory-post-list-consistency`를 **FAIL**로 남겼다. N1은 별도 새 DB에서도 반복 재현됐다.
- 증거: 최종 브라우저 run의 `null-dormitory-reproduction.json`, `browser-results.json`,
  `browser-network.json`, 실패 스크린샷과 `servers.json`의 실제 INNER JOIN SQL.
  대표 SQL은 `../tmp/like-final/null-dormitory-sql.txt`에도 보존했다.
- 영향: 해당 게시글과 좋아요 수를 최신순/조회순 목록에서 볼 수 없고 정렬에 따라 목록·인원수가 달라진다.
  좋아요 관계 저장 손실이나 타인 관계 조작은 확인되지 않았다.
- 범위: 일반 신규 작성은 필수 UserProfile의 생활관을 복사하므로 그 경로에서 null 생성은 재현하지 않았다.
  허용된 nullable 기존/이관 데이터에 대한 조회 결함이다. 운영 DB에 실제 해당 행이 있는지는 미검증이다.
  기존 조회 쿼리의 결함이며 M3 일괄 집계 코드가 새로 발생시켰다고 판단할 근거는 없다.
- 수정 제안: 공개 JPQL에서 생활관을 명시적 LEFT JOIN하고 alias로 선택 필터를 적용한다.
  null 생활관의 무필터 노출과 유효 생활관 필터 제외, count·전체 정렬·페이징 회귀를 추가한 뒤 MySQL/UI를 재검증한다.
  이번 작업에서는 Repository·기대값·데이터 정책을 수정해 통과시키지 않았다.

## E. 실행 이력·증거와 정리

| 실행 | 증거 경로 (`backend/` 기준) | 결과 |
|---|---|---|
| 실제 API/두 JVM | `build/like-final/mysql/run-b935c882-e885-4e97-92f8-a4accd45240c` | API 47/47, 745 HTTP. 이어지는 browser 도구 경로 오류로 parent summary는 FAIL_OR_INCOMPLETE |
| 첫 브라우저 | `build/like-final/mysql/run-9361e9f1-fa02-4711-af56-ae20dd3342e3` | 13 PASS/4 FAIL, 선택자·modal·alert 도구 문제와 nullable 게시글 누락 식별 |
| 도구 보완 브라우저 | `build/like-final/mysql/run-597e363b-6a12-4401-9b30-4507996d563a` | 핵심 19 PASS/N1 1 FAIL, 218 브라우저 HTTP |
| 최종 Chrome/Edge·페이지 이동 | `build/like-final/mysql/run-6bc7c7e4-50fa-4b7f-971d-02e989b0a5ff` | 핵심 19 PASS/N1 1 FAIL, 225 브라우저 HTTP |
| 최종 M3·SQL·정렬 | `build/like-final-post/mysql/run-4074e7ee-38b1-485b-ac2b-c4d0d94fb071` | 6/6 PASS, setup 포함 68 HTTP |

최종 브라우저의 Console error 8건은 예상한 400/401 또는 의도한 network abort에 대응한다.
uncaught pageerror와 API 5xx는 0건이다. 성공한 Like 쓰기의 HTTP 200/data=null을 확인했다.
지연 검사는 `route.fetch()`로 실제 서버 응답을 받은 뒤 동일 response를 `route.fulfill({response})`로 전달한다.
응답 JSON/상태 코드를 성공 mock으로 치환하지 않았다. 네트워크 실패 검사는 명시적으로 연결을 차단했다.

첫 브라우저의 페이지 aria-current 선택자 중복, 열린 상세 dialog 뒤 메뉴 클릭,
window.alert를 DOM alert로 오인한 관찰 문제는 검증 도구에서만 수정했다.
정상 생활관 fixture로 핵심 PO1을 재검증하면서 nullable 사례는 삭제하지 않고 별도 FAIL로 유지했다.
최종 browser-results의 20개 실행은 완전히 수행됐다. parent Java runner의 INCOMPLETE 항목은
browser 프로세스의 실패 exit를 집계한 것으로, 실제 20개 브라우저 결과를 대신하지 않는다.

각 run의 `summary.json`, `cases.json`, `requests.json`, `servers.json`, `environment.json`, `artifact.json`,
`cleanup.json`, `isolation-cleanup.json`에 실행·서버 identity·Boot JAR hash·정리 증거가 있다.
브라우저 run에는 `browser-environment.json`, `edge-environment.json`, `browser-results.json`,
`browser-summary.json`, `browser-network.json`, `browser-errors.json`, `browser-dialogs.json`, PNG도 있다.
JWT·DB 비밀번호·로그인 비밀번호는 증거 파일에 저장하지 않았다.

모든 실행에서 두 JVM 종료·생성 DB의 전체 테이블 0행·생성 DB/계정 삭제를 확인했다.
최종 브라우저 DB는 `kkiri_matching_mi_a2ab6bba30a1` (PID 9856/42044),
성능 DB는 `kkiri_matching_mi_86039ec2c4d1` (PID 31172/23472)였으며 둘 다 삭제됐다.
실패한 도구 실행에서 만든 DB/계정도 각 isolation-cleanup.json에서 삭제를 확인했다.
브라우저 종료 후 OS 조회에서도 Playwright Chrome/Edge 프로세스 0개, 프론트 3000 포트 listener 0개였다.
기존 검증 컨테이너 자체와 사용자의 기존 프로세스는 종료하지 않았다. 정리 실패는 없었다.

검증 도구는 `../tmp/like-final`과 `build/like-final`, `build/like-final-post`에 있다.
Git 제외 산출물이므로 새 checkout에 자동 포함되지는 않는다. 보고서의 수치는 이번 실행의 이력이다.
재현 시 새 DB 생성·identity 검사·정리를 유지하고 운영/개발 URL로 대체하지 않는다.

## F. 미검증 영역과 최종 판정

- 실제 운영 DB의 DDL·기존 데이터, nullable 게시글 존재 여부 및 운영 migration 적용.
- 운영 규모 p95/장기 부하·connection pool 포화·대규모 LIKES 정렬 비용.
- 실제 운영 프록시/TLS/CORS, 외부 네트워크와 운영 배포 성공 여부.
- JVM 강제종료/DB 중단/커밋 응답 유실/장기 네트워크 단절. 이번 network abort는 이 전체 장애 검증을 대체하지 않는다.
- 모든 브라우저·기기 조합. 주 E2E는 Chrome이며 Edge는 별도 로그인·관심 저장값/게스트 복원 흐름만 실행했다.
- 익명 게시글의 실제 author.id 공개에 따른 재식별 위험과 미확정 익명성 정책.
- 작성자 프로필·댓글 수·이미지·신고 상태의 행별 SQL 및 LIKES dependent 집계·filesort 비용은 별도 병목이다.

| 영역 | 최종 판정 |
|---|---|
| ProfileLike | **배포 가능** — 현재 계약과 이번 격리 통합/E2E 범위에서 M1·M2 재발 및 차단 결함 없음 |
| PostLike | **수정 후 배포** — 핵심 쓰기/집계/브라우저 흐름은 통과, N1 목록 누락 수정·재검증 필요 |
| CommentLike·답글 | **배포 가능** — 현재 익명 계약과 이번 격리 통합/E2E 범위에서 차단 결함 없음 |
| Like 전체 | **수정 후 배포** — N1을 미해결로 유지. 실제 운영 환경의 배포 승인/성공을 확정하지 않음 |

이번 변경은 이 검증 보고서와 별도 검증 산출물뿐이다. Java/React/기존 테스트/DB 스키마 제품 소스,
Like 계약, UI/CSS, M1·M2·M3 구현을 변경하지 않았으며 commit/push/merge도 수행하지 않았다.
종료 시 기존 추적 파일 **786/786개 SHA-256이 시작 시점과 동일**했고 Git 상태에는 이 새 보고서만 나타났다.
계약 스냅샷과 `git diff --check`를 통과했으며 새 보고서도 trailing whitespace가 없음을 확인했다.
최종 검사·포트 정리 증거는 `../tmp/like-final/final-checks.json`, `report-whitespace.json`, `ports-cleanup.json`에 있다.
