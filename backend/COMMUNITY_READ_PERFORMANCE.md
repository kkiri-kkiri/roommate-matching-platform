# 커뮤니티 7·8번: 페이지 상한과 조회 성능

2026-10-19 작업 결과: 완료. 7번 목록 상한과 8번 목록/상세 조회를 보완했다.
기존 1·2·3·4·5번과 이전 미커밋 변경을 보존했다.

## 원인과 적용 범위

- 공개 목록 DTO에는 size 최소값만 있었고 내 댓글 목록도 최대값 검사가 없었다.
  관리자 게시글·댓글·신고 DTO에도 별도 상한 계약이 없었으므로 같은 100을 적용했다.
- 좋아요는 이미 페이지 단위 집계였지만 프로필·활성 댓글 수·이미지 존재 여부는 매 게시글마다
  조회했다. 로그인 목록은 신고 여부도 매번 조회했다. 생활관/전공 등 lazy 연관관계도 추가 SQL의
  원인이었다. 관리자는 프로필만 배치이고 집계·신고 여부에 반복 조회가 남아 있었다.
- 상세는 첫 SELECT FOR UPDATE에서 게시글을 잠근 후 이미지·댓글·프로필·집계를 읽었다.
  일반 댓글 작성자별 프로필 조회가 잠금 구간에 포함됐다.

## 페이지 크기 계약

| 대상 | 계약 |
| --- | --- |
| 공개·검색 `/api/posts` | page 기본 0, size 기본 6, size 1~100 |
| 내 게시글 `/api/users/me/posts` | 동일, Principal의 본인 글만 조회 |
| 내 댓글·답글 `/api/users/me/comments` | 동일, 기존 최신순·답글 표시 유지 |
| 관리자 `/api/admin/posts`, `/comments`, `/reports` | 동일, 관리자 권한과 기존 필터 유지 |

DTO에 `@Max(100)`을 추가하고 Service 검증도 재사용한다. 내 댓글 목록은 기존 수동 검증에서
100 초과도 거부한다. 관리자 세 목록에는 Integer offset 범위 검사를 추가했다.
유효한 정수의 범위 위반과 page*size>Integer.MAX_VALUE는 데이터 조회 전에
400 VALIDATION_ERROR다. 비정수·Integer 범위 초과 입력은 기존 MVC 바인딩 경로의 400이며,
바인딩 방식에 따라 기존 INVALID_REQUEST 또는 VALIDATION_ERROR를 사용한다.
크기를 잘라서 성공 처리하지 않는다. 관리자 권한 검증용 USER 읽기는 대량 데이터 조회와 구분한다.
관리자 회원·일정, 채팅·룸메이트 등 다른 도메인의 페이지 정책은 변경하지 않았다.

현재 커뮤니티와 관련 관리자 프론트는 모두 기본 size=6이므로 추가 프론트 구현 변경이 없다.

## 일괄 조회

- DB에서 선택한 최대 100개 게시글 ID로 좋아요·활성 댓글 수, 이미지 존재, 조회자의 좋아요·신고를
  각각 일괄 조회한다. 익명 게시글을 제외한 고유 작성자의 프로필을 기존 fetch graph로 읽는다.
- 일반 목록·내 글은 to-one 작성자/생활관 graph를 사용한다. native LIKES 조회는 DB 페이징 결과의
  to-one 연관관계만 추가 SELECT 한 번으로 초기화하며 원래 페이지 순서를 바꾸지 않는다.
  컬렉션 fetch join이나 메모리 페이지네이션을 사용하지 않는다.
- 관리자의 게시글 좋아요·활성 댓글 수와 전체 신고 여부, 댓글의 전체 신고 여부도 배치한다.
  공개 응답의 신고자는 현재 사용자/POST 유형으로 한정하고, 관리자는 기존 전체 신고 의미를 유지한다.
- 빈 페이지는 추가 배치를 생략한다. 없는 집계는 0, 없는 이미지·좋아요·신고는 false다.
  정상 프로필이 없는 일반 작성자는 기존 ID/null 프로필 fallback을 유지한다.
- 내 댓글 목록의 원 게시글은 to-one fetch graph로 함께 읽는다.
- 상세도 일반 댓글·답글과 일반 게시글 작성자의 고유 ID로 프로필을 한 번에 읽는다.
  익명 작성자의 실제 ID·학과는 응답에 포함하지 않고 mine은 내부 작성자와 Principal로 계산한다.
  익명 번호, 삭제 부모와 답글의 기존 정책, 작성 후 익명 여부 고정은 유지한다.

## 상세 트랜잭션과 응답 일관성

하나의 기존 쓰기 트랜잭션에서 다음 순서를 사용한다.

1. 잠금 없는 활성 게시글 조회, 이미지·댓글·집계·프로필 읽기를 수행한다.
2. 게시글 메타데이터와 이미지·일반 집계를 immutable 응답 스냅샷으로 만든다.
3. `WHERE post_id=? AND status='ACTIVE'` 조건으로 `view_count=view_count+1`, `updated_at=?`만 UPDATE한다.
   이때 게시글 행 쓰기 잠금을 획득한다. 갱신 0행이면 기존 INVALID_REQUEST로 거부한다.
4. Entity를 refresh하고 기존 익명 번호 처리의 게시글 → 매핑/과거 댓글 잠금을 사용한다.
5. DB 추가 프로필 조회 없이 댓글 DTO를 조합하고, 스냅샷에 이 요청의 views를 적용한다.
6. 트랜잭션 종료 시 조회수와 익명 번호 변경을 함께 커밋하거나 롤백한다.

본문·상태 등 다른 컬럼을 UPDATE에 포함하지 않아 과거 Entity 값으로 수정/삭제를 되돌리지 않는다.
기존 수정 경로의 잠금·refresh와 계정 → 게시글 → 댓글 순서는 그대로다. 일반 읽기는 잠금을
획득하지 않으므로 댓글을 먼저 읽는 것이 쓰기 잠금 순서 역전을 만들지 않는다.
번호 초기화는 별도 REQUIRES_NEW나 선행 커밋으로 분리하지 않았다.

응답의 제목·본문·이미지·댓글·일반 집계는 선행 읽기 값이고 views는 해당 요청의 원자 증가 결과다.
DB 격리 수준이 RR이면 선행 일관 읽기 스냅샷을 사용하고, RC이면 문장별 읽기다.
동시 수정이 커밋되어도 선행 읽기의 정상 내용을 반환할 수 있으며, 최신 본문을 다시 읽어
과거 이미지와 섞지 않는다. 최종 UPDATE는 활성 상태를 재검증하므로 삭제가 먼저 이기면 실패한다.
실제 MySQL RR 동작은 별도 격리 환경 검증이 필요하다.

읽기 준비 실패는 조회수를 올리지 않는다. 증가 후 번호 초기화/DTO 구성 실패는 전체 롤백한다.
서비스 커밋 후 HTTP 직렬화·전송 실패는 기존과 같이 조회수가 이미 반영될 수 있다.
외부 호출자가 더 큰 트랜잭션을 사용하면 잠금은 그 외부 트랜잭션 끝까지 유지된다.

## 실제 SQL 측정

환경: Windows, Java 21, 저장소의 Spring Boot 4.1.1/Hibernate, Gradle 강제 test 프로필,
H2 메모리 DB MySQL 모드. Hibernate `getPrepareStatementCount()`와 StatementInspector를 함께 사용했다.
fixture 생성은 별도 트랜잭션에서 완료하고, 각 측정 직전에 Statistics와 SQL 기록을 비웠다.
서비스 요청마다 새 persistence context를 사용하며 fixture SQL은 제외했다.

목록 fixture는 프로필이 서로 다른 작성자 100명과 일반 게시글 100개다. 동일 전공·학부·생활관을
사용하며 목록 조건은 category=자유게시판, q=perf-list, dormitory=나래다.
상세는 서로 다른 일반 작성자의 댓글 1/10/50/100개를 가진 별도 게시글로 측정했다.
집계가 있는 데이터의 정확성은 별도 실제 API 계약 테스트로 검증했다.

수정 전 원본 코드에 측정 테스트를 먼저 실행해 값을 확보했고, 이후 같은 목록/일반 댓글 조건으로
수정 후 측정했다. 관리자와 번호 초기화 완료 익명 댓글은 수정 후 수치만 측정했다.
로컬 원시 기록은 `build/reports/community-read-before.json`, `community-read-after.json`과
`build/test-results/test/TEST-com.kkirikkiri.community.CommunityReadPerformanceIntegrationTest.xml`에 있다.
build 산출물과 무관하게 아래 실측값을 문서에 보존한다.

### 목록 SQL 수: 1 / 10 / 50 / 100개

| 경로 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 비로그인 LATEST/VIEWS | 8 / 35 / 155 / 305 | 6 / 6 / 6 / 6 |
| 로그인 LATEST/VIEWS | 10 / 46 / 206 / 406 | 8 / 8 / 8 / 8 |
| 비로그인 LIKES | 8 / 35 / 155 / 305 | 7 / 7 / 7 / 7 |
| 로그인 LIKES | 10 / 46 / 206 / 406 | 9 / 9 / 9 / 9 |
| 관리자 게시글 | 전후 비교 불가: 이전 미측정 | 7 / 7 / 7 / 7 |
| 관리자 댓글 | 전후 비교 불가: 이전 미측정 | 5 / 5 / 5 / 5 |

### 상세 SQL 수: 댓글 1 / 10 / 50 / 100개

| 경로 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 비로그인, 일반 댓글 | 13 / 22 / 62 / 112 | 11 / 11 / 11 / 11 |
| 로그인, 일반 댓글 | 16 / 25 / 65 / 115 | 14 / 14 / 14 / 14 |
| 로그인, 번호 초기화 완료 익명 댓글 | 전후 비교 불가: 이전 미측정 | 12 / 12 / 12 / 12 |

### 실제 잠금 구간

수정 전 일반 댓글 상세는 첫 SQL에서 게시글을 잠갔다. 댓글 100개에서 이후 SQL은
비로그인 111개·로그인 114개였다. 수정 후 첫 게시글 쓰기 SQL은 비로그인 7번째·로그인 10번째이며,
그 뒤 SQL은 각각 4개다. 이 구간은 Entity refresh, 번호 처리의 게시글/매핑/필요한 과거 댓글 읽기다.
번호 초기화 완료 익명 댓글은 증가 뒤 SQL 3개이며 프로필 SQL은 없다.

SQL 순서 외에도 실제 동시성으로 확인했다. 프로필 SELECT를 멈춘 동안 수정·일반/관리자 삭제·
댓글 작성·좋아요가 완료됐다. 번호 조회 직전(원자 UPDATE 이후)에 멈추면 댓글 쓰기가 실제 H2
blocker_id 대기 상태가 되고 커밋 후 완료됐다. 16개 동시 상세 요청의 최종 조회수는 16이다.
번호 두 개를 최초 복구하는 중 두 번째 INSERT를 실패시켜 조회수와 첫 매핑 INSERT가 함께
롤백되는 것도 확인했다. 임의의 지연 시간이나 추정 처리량 수치를 성능 향상으로 제시하지 않는다.
처리 시간·TPS·운영 잠금 유지 시간(ms)의 전후 비교는 하지 않았다.

## 최종 테스트 결과

| 검증 | 결과 |
| --- | --- |
| `gradlew.bat test bootJar` | 성공, 1,103개 통과: 기존 975 + 신규 128 |
| 신규 `CommunityReadContractIntegrationTest` | 116개 통과 |
| 신규 `CommunityReadPerformanceIntegrationTest` | 4개 통과 |
| 신규 `CommunityDetailReadConcurrencyIntegrationTest` | 8개 통과 |
| 기존 1 / 2 / 4·5번 동시성 | 47 / 115 / 72개 통과 |
| 기존 3번 ID 마스킹 / 익명 상태 고정 통합 | 24 / 23개 통과 |
| 프론트 전체 `npm test -- --watchAll=false --runInBand` | 19개 suite, 기존 190개 통과 |
| 신규 프론트 테스트 | 0개: 프론트 구현 변경 없음, 기존 목록·검색·내 활동·관리자·채팅 회귀 실행 |
| 프론트 `npm run build` | 성공 |
| 문서 계약 검사 | 70 endpoints / 71 record DTOs / 29 entities 통과 |
| `git diff --check` | 통과 |

최종 실패·오류·건너뜀은 0개다. 실제 MySQL·다중 JVM·실브라우저·외부 연결은 미검증이다.
초기 측정 fixture의 기준정보 중복과, 조회 방식 변경에 따라 달라진 기존 mock 설정은 수정했고
최종 전체 실행에서 재검증했다. 기존 테스트를 삭제하거나 검증 조건을 무효화하지 않았다.

## 이번 작업의 변경 파일

저장소 루트 기준 23개 파일이다. 이전 작업 파일의 기존 변경은 보존했다.

| 파일 | 주요 변경 |
| --- | --- |
| `backend/src/main/java/com/kkirikkiri/community/post/dto/CommunityPostListRequest.java` | size 최대 100 |
| `backend/src/main/java/com/kkirikkiri/admin/dto/AdminPostListRequest.java` | 관리자 게시글 최대 100 |
| `backend/src/main/java/com/kkirikkiri/admin/dto/AdminCommentListRequest.java` | 관리자 댓글 최대 100 |
| `backend/src/main/java/com/kkirikkiri/admin/dto/AdminReportListRequest.java` | 관리자 신고 최대 100 |
| `backend/src/main/java/com/kkirikkiri/community/post/service/CommunityPostService.java` | 목록/상세 배치, 선행 스냅샷·후행 원자 조회수 |
| `backend/src/main/java/com/kkirikkiri/community/comment/service/CommunityCommentService.java` | 내 댓글 최대 100 |
| `backend/src/main/java/com/kkirikkiri/admin/service/AdminService.java` | offset 검사, 게시글·댓글 배치 집계 |
| `backend/src/main/java/com/kkirikkiri/community/post/repository/CommunityPostRepository.java` | to-one graph, ACTIVE 조건 원자 증가 |
| `backend/src/main/java/com/kkirikkiri/community/post/repository/CommunityPostReadRepository.java` | 페이지 신고 배치, native 결과 관계 초기화 |
| `backend/src/main/java/com/kkirikkiri/community/comment/repository/CommunityCommentRepository.java` | 페이지 댓글 집계, 내 댓글의 원글 fetch |
| `backend/src/main/java/com/kkirikkiri/community/post/repository/PostImageRepository.java` | 페이지 이미지 존재 배치 |
| `backend/src/main/java/com/kkirikkiri/report/repository/ReportRepository.java` | 관리자 전체 신고 존재 배치 |
| `backend/src/main/java/com/kkirikkiri/community/post/dto/CommunityPostDetailResponse.java` | 응답 필드 유지, 스냅샷에 views/댓글 결합 |
| `backend/src/test/java/com/kkirikkiri/community/post/service/CommunityPostServiceTest.java` | 배치·원자 증가 mock 갱신, 결과 검증 유지 |
| `backend/src/test/java/com/kkirikkiri/admin/service/AdminServiceTest.java` | 배치 의존성과 신고 mock 갱신 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityReadContractIntegrationTest.java` | 신규: 실제 MVC 바인딩·상한·집계·보안 계약 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityReadPerformanceIntegrationTest.java` | 신규: 실제 SQL 수·실행 순서 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityDetailReadConcurrencyIntegrationTest.java` | 신규: 읽기/쓰기 경합·잠금·원자 롤백 |
| `backend/API.md` | 상한·일괄 조회·상세 일관성·DTO 스냅샷 |
| `backend/ERD.md` | 조회수 갱신 설명, 물리 스키마 유지 |
| `backend/DEPLOYMENT.md` | 적용 범위와 MySQL 검증 조건 |
| `backend/COMMUNITY_CONCURRENCY.md` | 초기 1번 구현과 현재 상세 잠금 구간 구분 |
| `backend/COMMUNITY_READ_PERFORMANCE.md` | 신규: 실측·계약·파일·검증·한계 |

## 기존 기능 영향과 남은 한계

- API 응답 필드·페이징 형식·기본값·검색·필터·정렬을 유지한다. 새 요청 제한은 size 최대 100이다.
  공개 신고와 관리자 전체 신고의 서로 다른 의미, 익명 ID 마스킹·mine, 작성 후 익명 상태 고정,
  채팅 연결, 이미지 최대 5장과 롤백, 부모 삭제와 답글 소프트 삭제를 유지한다.
- 일반 프로필 N+1은 제거했지만 상세 댓글은 기존 계약대로 전체 반환한다. 댓글의 데이터량·직렬화·
  큰 IN 조건 비용 자체는 남는다. 댓글 페이지네이션과 큰 상세 IN 조건의 별도 분할은 이번에 도입하지 않았다.
- 과거 익명 번호가 없는 게시글의 최초 초기화는 번호의 정합성을 위해 과거 댓글 잠금과
  작성자 수만큼 INSERT가 필요하다. 이를 독립 커밋으로 분리하지 않았다.
- 쿼리 수 일정은 실행 시간 일정이나 운영 처리량 개선의 보장이 아니다. MySQL의 실제 인덱스·
  통계·EXPLAIN, native LIKES 정렬의 상관 집계 비용, 100개 IN 조건, InnoDB RR의 current read,
  FK/범위 잠금·교착·timeout·다중 JVM은 격리 DB에서 별도 검증해야 한다.
- 관리자 회원·일정과 다른 도메인의 N+1·페이지 정책은 이번 범위가 아니다.
- 7·8번에서는 이미지 연결 제거 후 파일/URL 잔존을 변경하지 않았다. 이후 9번은
  [이미지 수명주기 보완](COMMUNITY_IMAGE_LIFECYCLE.md)에서 처리한다. 성능 개선을 이유로 파일을 삭제하거나
  기존 데이터·채팅방을 재구성하지 않았다. 스키마·운영 설정·마이그레이션 변경도 없다.
- 운영·공유 DB를 변경하지 않았고 브랜치 전환·커밋·푸시·PR 생성도 수행하지 않았다.
