# PostLike M3 수정 및 회귀 검증

검증일: 2026-10-18 (Asia/Seoul). 대상은 게시글 목록의 PostLike 반복 조회다.
기존 ProfileLike M1·M2의 미커밋 소스·테스트·문서는 보존했다. 프론트 파일의 시작 시 SHA-256을
`build/post-like-validation/frontend-before.json`에 기록하고 완료 시 대조한다.
Git commit/push/merge, 개발·운영 DB 접속, 운영 스키마 변경은 수행하지 않았다.

## 원인과 변경

기존 `CommunityPostService.readPosts`는 DB에서 정렬·필터·페이징한 Entity 페이지를
`posts.map(toListResponse)`으로 변환하면서 각 게시글마다 native `countLikes(postId)`와
`isLiked(postId, viewerId)`를 호출했다. 로그인 시 페이지 N행에 Like 조회 2N회,
게스트는 count N회가 추가됐다. 작성자·댓글 수·이미지 여부·신고 상태의 별도 조회도 존재한다.

변경 파일:

| 파일 | 위치와 변경 |
|---|---|
| `src/main/java/com/kkirikkiri/community/post/repository/PostLikeRepository.java:16` | `LikeCount` projection, 페이지 ID에 대한 GROUP BY count, viewer ID에 대한 liked postId 일괄 조회 |
| `src/main/java/com/kkirikkiri/community/post/service/CommunityPostService.java:165` | 선택된 페이지의 postId만 수집, count Map/liked Set 구성 후 DTO에 전달 |
| `src/test/java/com/kkirikkiri/community/post/service/CommunityPostServiceTest.java:426` | 실제 조회 Entity와 같은 ID를 갖는 fixture 보완, 일괄 집계 mock과 단일행 Like 조회 금지 검증 |
| `src/test/java/com/kkirikkiri/community/post/service/PostLikeListIntegrationTest.java` | H2의 실제 Service/Repository/Entity·Hibernate Statistics를 사용하는 신규 11건 |
| `API.md` 7.2 목록 설명 | 현재 페이지 일괄 Like 조회와 기존 정렬/반환 규칙 명시 |

SQL 형태:

```sql
select post_id, count(*) from post_like
where post_id in (:pagePostIds) group by post_id;

select post_id from post_like
where post_id in (:pagePostIds) and user_id = :viewerId;
```

실제 구현은 JPQL projection이며 입력값은 바인딩한다. 없는 count 항목은 `0L`,
없는 liked 항목은 false로 매핑한다. `likes`는 기존 long, `liked`는 boolean을 유지한다.
게스트는 두 번째 조회를 하지 않고 빈 페이지는 둘 다 생략한다. 전체 목록을 메모리에서
정렬/페이징하거나 전체 POST_LIKE를 조회하지 않는다.

공개 목록과 `/users/me/posts` 모두 같은 readPosts를 사용하므로 같은 최적화를 적용한다.
Controller의 Principal 전달, POST/DELETE의 잠금·멱등성, 상세 조회, 댓글 좋아요,
익명 게시글 author.id 유지/nickname·major 마스킹, DTO·HTTP 상태는 변경하지 않았다.
ProfileLike 소스 및 프론트 UI/API 코드는 수정하지 않았다.

## SQL 측정 방법

과거 Like 검증의 1행10/6행40 SQL, 6행 Like 조회12회는 **기존 이력**이다.
이번에는 변경 전 Service 소스를 build 아래에 보존한 뒤 현재 의존성으로 별도 컴파일하고
동일 서버에서 classpath 우선순위를 지정해 실행한다. 제품 소스를 되돌리지 않는다.
수정 전후 각각 새 DB·계정·두 OS JVM을 생성하며 같은 규칙으로 게시글 1,000개와
Like 3,500개, 작성자 2명·생활관 2개·동점 created_at 등을 준비한다.
baseline은 수정 전 Service의 행별 native Like 경로, after는 제품 Service의 새 JPQL 경로를 사용한다.

실제 HTTP GET에서 JDBC PreparedStatement execute를 관찰해 실행 SQL과 횟수를 기록한다.
로그인 요청의 JWT 계정 조회도 전체 SQL 수에 포함한다. 보호 DB 확인용 connection guard SQL은
업무 SQL wrapper 바깥에 있으므로 측정에 포함하지 않는다. 각 요청의 SQL template과 Like SQL 수를
`measurements.json`에 보존한다. Like 수치에는 LIKES 정렬 자체의 1개 SQL이 포함될 수 있어
응답 조립용 count/liked 조회와 구분한다. query/parameter 결과를 바꾸는 wrapper가 아니다.

동일 fixture의 실제 LATEST HTTP 조회 전후:

| API / 인증 | 페이지 행 수 | 전체 SQL 전 → 후 | Like count/liked SQL 전 → 후 |
|---|---:|---:|---:|
| `/api/posts` 로그인 | 1 | 10 → 10 | 2 → 2 |
| `/api/posts` 로그인 | 6 | 41 → 31 | 12 → 2 |
| `/api/posts` 로그인 | 20 | 125 → 87 | 40 → 2 |
| `/api/posts` 게스트 | 1 | 7 → 7 | 1 → 1 |
| `/api/posts` 게스트 | 6 | 28 → 23 | 6 → 1 |
| `/api/posts` 게스트 | 20 | 84 → 65 | 20 → 1 |
| `/api/users/me/posts` 로그인 | 1 | 10 → 10 | 2 → 2 |
| `/api/users/me/posts` 로그인 | 6 | 40 → 30 | 12 → 2 |
| `/api/users/me/posts` 로그인 | 20 | 124 → 86 | 40 → 2 |

과거의 6행40과 이번 공개 목록41을 동일한 데이터 측정으로 섞지 않는다.
이번 공개 목록은 작성자/생활관이 각각 두 종류다. 실제 남은 생활관 lazy 조회가 포함된다.
전후 동일 조건에서 줄어든 10/38회가 Like 행별 조회 제거분이며, 나머지 조회가 모두 고정된 것은 아니다.

LIKES 정렬도 별도로 측정했다. 로그인 공개 목록의 전체 SQL은 1행10→10, 6행40→30,
20행124→86이고, post_like를 포함하는 SQL은 3→3 / 13→3 / 41→3이다.
후자의 고정3회는 **정렬 SQL 1회 + 응답 조립용 일괄 조회2회**다.
VIEWS도 포함해 공개 로그인/게스트·본인 목록의 총27개 조합을 검증했다.
전체 측정값과 SQL template은 `build/post-like-validation/query-comparison.json` 및 각 run의
`measurements.json`에 있다. 단일 행은 조회 횟수가 같으며 모든 페이지 크기에서 총 SQL이
상수로 바뀌었다고 주장하지 않는다.

## LIKES 정렬 검토

현재 SQL은 WHERE에서 ACTIVE/카테고리/검색/생활관/작성자를 거른 후보를 좋아요 count,
created_at DESC, post_id DESC로 정렬하고 DB LIMIT/OFFSET으로 페이지를 선택한다.
COUNT용 SQL도 기존 필터를 사용한다. MySQL은 정렬값인 집계 결과에 대한 사전 정렬 인덱스가
없어 filesort를 사용할 수 있다. filesort 자체를 모든 데이터에서 디스크 정렬로 해석하지 않는다.

이번 검증은 기존 상관 count 서브쿼리와 `(post_id, count(*)) GROUP BY` derived table을
LEFT JOIN하는 대안을 같은 격리 데이터에서 비교한다. 기존 UNIQUE `(post_id,user_id)`의
왼쪽 post_id를 count/IN 조회에 사용할 수 있다. 사용자별 liked는 동일 복합 인덱스 또는
user_id FK 인덱스를 사용할 수 있다. 새 인덱스나 마이그레이션을 자동 추가하지 않는다.

정렬 SQL은 **이번 수정에서 유지**한다. 단순 전역 derived-table 집계가 모든 필터에 더 유리하지 않았다.
변경 전 run에서 무필터 EXPLAIN ANALYZE는 상관 서브쿼리 7.54ms, JOIN 집계안 4.27ms였다.
반면 작성자·카테고리·생활관 필터에서 기존 1.15ms, 집계안 1.75ms였다.
이는 한 번의 로컬 실행 관측값이며 운영 지연 개선률이나 SLA로 일반화하지 않는다.
수정 후 새 DB에서 같은 두 SQL을 다시 측정했을 때는 무필터 기존5.49ms/집계안7.59ms,
필터 기존1.35ms/집계안2.50ms였다. 무필터의 상대 결과도 달라져 JOIN 대안의 일관된
우위를 확인하지 못했다. 따라서 이번에 모든 요청의 정렬 SQL을 교체하지 않았다.
실제 제품의 LIKES 정렬은 전후 같은 SQL·인덱스 경로이며 dependent subquery와 filesort가 남는다.

- 기존 무필터 계획: 게시글 1,000행 scan → ACTIVE 999행 → count 서브쿼리 약1,001회
  covering index lookup → 좋아요수·created_at·post_id 정렬 → LIMIT20.
- 필터 계획: 작성자 FK 인덱스로 500행을 읽고 ACTIVE/카테고리/생활관 필터 후 166행에
  대해서만 기존 count subquery를 수행한다. aggregate 대안은 이때도 전체 Like 3,501행을 읽어
  876개 post_id 그룹을 materialize하고 조인한다. 따라서 이 대안을 일괄 적용하지 않았다.
- 두 계획 모두 집계값 정렬이므로 filesort는 남는다. 기존 count는 `(post_id,user_id)`의
  post_id prefix를 사용하며 Like 행을 위해 별도 전체 테이블 scan을 하지 않는다.
- EXPLAIN은 기능 검증의 등록/취소/삭제 뒤 실행했다. 그러므로 측정 fixture의 최초
  1,000 ACTIVE/3,500 Like와 달리 계획 시점은 총게시글1,000·ACTIVE999·Like3,501이다.
  baseline/after 양쪽에서 같은 순서로 변경하므로 비교 조건은 동일하다.

정렬 전 후보가 큰 경우에는 사전 집계·필터 후보 JOIN 등 추가 방안의 운영 규모 비교가 필요하다.
새 인덱스 또는 마이그레이션은 이번 개선에 필요하지 않다. 운영 인덱스 존재까지 검증한 것은 아니다.

## 기능·회귀 테스트

신규 H2 통합 11건:

- 1·6·20행 × 로그인/게스트 6건: 정확한 count/liked, 페이지 메타데이터,
  Hibernate Statistics에서 Like JPQL 조회 2/1회 고정 및 행별 native 조회 부재.
- LATEST/VIEWS/LIKES 3건: DB 정렬 oracle과 1·2페이지 순서 비교, timestamp/postId 동점,
  카테고리·본문 검색·생활관 필터, 본인 게시글만 반환.
- 등록/취소 멱등성·0/복수 좋아요·A/B/게스트 분리, 목록/상세 반영,
  익명 게시글 계약·CommentLike 독립성 1건.
- 삭제 후 목록 제외·등록 거절, 빈 목록의 content/totalElements/totalPages/first/last와
  빈 ID Like 조회 생략 1건. 현재 콘텐츠 enum은 ACTIVE/DELETED이므로 임의 상태를 추가하지 않았다.

| 실행 | 통과 | 실패/오류/skip | 근거 |
|---|---:|---:|---|
| 백엔드 전체 | 684 (55 suite) | 0/0/0 | `build/post-like-validation/backend-final.log`, `backend-summary.json` |
| 위 전체 중 community | 133 | 0/0/0 | 신규 통합11 + 기존122; 중복 합산하지 않음 |
| 위 전체 중 matching / survey | 62 / 66 | 0/0/0 | 영향 연결 회귀 포함 |
| 백엔드 bootJar | 성공 | 0 | 제품 변경을 포함해 JAR 생성, 최종 실행에서 최신 산출물 확인 |
| 프론트 community API/화면 테스트 | 39 (4 suite) | 0/0/0 | `build/post-like-validation/frontend-compatibility.log` |
| 격리 MySQL 수정 전 | 5 시나리오 그룹, 62 HTTP | 0/0/0 | `mysql-baseline.log`, baseline run의 `summary.json` |
| 격리 MySQL 수정 후 | 5 시나리오 그룹, 62 HTTP | 0/0/0 | `mysql-after.log`, after run의 `summary.json` |
| 계약 스냅샷 / diff 검사 | 통과 | 0 | 70 endpoints, 71 record DTOs, 29 entities 선언 대조 |

프론트 테스트는 실제 컴포넌트와 mock API를 사용한다. 실제 브라우저 E2E와 구분한다.
프론트 소스가 바뀌지 않아 이번에는 production build를 재실행하지 않았다.

실행 이력: 최초 신규 통합 fixture의 dormitory_code가 20자 제한보다 길어 11건이 실패했고
실제 길이 제한에 맞게 fixture를 수정했다. 다음 전체 실행에서 기존 mock 게시글의 ID가
null인 3건이 실패해 실제 조회 Entity처럼 ID를 보완했다. 제품의 NULL/UNIQUE 제약을
완화하거나 검증을 제거하지 않았다. 최종 전체 실행은 684/684다.
검증 도구 컴파일 중 Timestamp import 모호성·UTF-8 BOM·Lombok classpath 문제도
DB 접속 전에 수정했으며 이 준비 실패를 제품 MySQL 기능 실패로 세지 않는다.

MySQL의 5개 그룹은 27개 SQL 측정 조합, 3종 정렬/페이지/필터, 쓰기/상세/댓글 독립,
빈 목록/삭제/인증, 실행계획 비교를 포함한다. 27개를 별도 기능 테스트 27개로 중복 합산하지 않는다.
baseline과 after도 같은 시나리오의 비교 실행이다.

## 격리 환경·실행 증거

- 컨테이너 `kkiri-survey-mysql-test`, image `mysql:8.0.46-oraclelinux9`, label
  `kkiri.validation=survey-only`, loopback `127.0.0.1:3311`을 실행 전에 확인했다.
  UUID/hostname/DB명/계정을 각 JDBC 연결에서 재확인한다. `.env`를 로드하지 않는다.
- MySQL8.0.46 / InnoDB / REPEATABLE READ, JDBC 세션 UTC, 서버 JVM Asia/Seoul.
  새 빈 DB에서만 Entity create-only DDL을 만들고 실제 두 서버는 validate·SQL never·seed NONE이다.
  실제 SMTP는 전송 불가능한 대체 빈이며 외부 AI/수집 호출은 없다.
- root 연결은 확인한 전용 서버에서 이번에 생성하는 DB/계정의 생성·회수에만 사용한다.
  개발/운영 DB 또는 기존 kkirisurveytest 테이블/데이터는 선택하지 않는다.
  두 run 모두 두 JVM 종료·모든 테이블0행·생성한 DB와 계정 회수·보호 대상 DB 접근false를 확인했다.

| 실행 | run 디렉터리 | DB | 두 OS PID |
|---|---|---|---|
| before | `build/post-like-validation/mysql/run-32614d03-34ba-4a50-9caf-ea144c0edfcb` | kkiri_matching_mi_2ec72f017226 | 37932 / 25424 |
| after | `build/post-like-validation/mysql/run-6e304051-0a30-4014-aeb7-70d8f3c4b05c` | kkiri_matching_mi_bbea79506661 | 3576 / 15648 |

각 디렉터리의 `summary.json`, `cases.json`, `requests.json`, `servers.json`,
`environment.json`, `fixtures.json`, `artifact.json`, `measurements.json`, `likes-sort-plans.json`,
`cleanup.json`, `isolation-cleanup.json`에 실행과 정리 증거가 있다. SQL template은 보존하지만
JWT·자격증명·요청 본문은 보존하지 않는다. `likes-sort-plans.json`에 실제 SHOW CREATE/SHOW INDEX,
현재안/대안 SQL 및 EXPLAIN JSON/ANALYZE를 함께 남겼다.

재현 명령은 저장소 루트 기준이다. `build/post-like-validation`은 Git 제외 산출물이므로
다른 checkout에 자동 포함되지 않는다. 지속적인 코드 회귀는 새 Java 통합 테스트로 실행한다.

```powershell
cd backend
.\gradlew.bat test bootJar
cd ..
node backend/tools/sync-contract-docs.cjs --check
node backend/build/post-like-validation/prepare-mysql.cjs
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\build\post-like-validation\run-mysql.ps1 -Baseline
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\build\post-like-validation\run-mysql.ps1
```

전용 컨테이너 identity 검증을 우회하거나 URL을 개발 DB로 바꾸지 않는다.

## 남은 병목과 검증 경계

- 작성자 프로필, 댓글 수, 이미지 여부, 신고 여부 조회가 행마다 남아 있다. 이 작업을 목록 전체 N+1 해소로 보고하지 않는다.
- LIKES 집계 정렬은 필터 후 후보 수/전체 Like 분포에 영향을 받는다. 1,000개 게시글의 EXPLAIN/짧은 실행이 운영 규모의 p95·처리량·pool 포화·장기 부하를 증명하지 않는다.
- size 상한은 기존 계약 그대로다. 최대 page size 정책이나 count 캐시/사전 집계 카운터를 새로 도입하지 않는다.
- H2는 MySQL 실행계획·물리 인덱스 검증을 대신하지 않는다. 운영 DB의 실제 DDL·데이터·백업·마이그레이션 적용은 미검증이다.
- 프론트 실제 브라우저 + 실제 서버 E2E, 운영 배포, DB/네트워크 전체 장애 시험은 미실행이다.
- 이전 M1·M2의 프론트178건 또는 과거 MySQL47-case 결과를 이번에 실행한 건수로 합산하지 않는다.

## 최종 판정

**수정 완료.** PostLike의 목록 응답 조립에서 행별 count/liked 조회를 제거했고,
수정 전후 실제 MySQL SQL 횟수와 H2/HTTP 회귀로 확인했다. 실행한 테스트에서 기존 기능 회귀는
발견되지 않았다. ProfileLike 기존 변경과 프론트 소스 해시는 시작 시와 동일하다.
운영 부하·실제 브라우저·운영 DB 검증까지 완료했다는 판정은 아니다.
