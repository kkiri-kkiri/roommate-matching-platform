# 커뮤니티 9번 이미지 파일 수명주기

## 정책과 원인

기존 WebConfig는 `uploads/community`를 `/api/post-images/**`에 직접 연결했다.
게시글 수정은 POST_IMAGE 행만 지웠고 저장소는 신규 업로드 롤백만 정리했다.
따라서 연결을 제거하거나 부모를 소프트 삭제해도 파일과 URL이 계속 남았다.

새 공개 GET은 POST_IMAGE.image_url과 ACTIVE 부모를 확인한다. 기존 URL과 생성/수정 DTO는 유지한다.
비로그인도 정상 공개 이미지는 조회할 수 있다. 연결 없음·삭제 부모·없는 파일은 404 빈 응답이다.
다른 ACTIVE 게시글에 같은 URL이 연결된 레거시 파일은 그 유효한 공개 연결로 조회할 수 있다.
관리자에게 공개 URL의 예외를 부여하지 않는다. 현재 관리자 상세/신고 응답에는 보존 이미지 열람
기능이 없으며, 별도 관리자 열람/복구 기능이나 보존 기간은 이번에 만들지 않는다.

개별 제거는 기존 계정 → 게시글 → 이미지 행 잠금을 유지하며, 연결 제거와 정리 작업을 함께 커밋한다.
그 뒤 별도 READ COMMITTED 트랜잭션에서 작업 행만 잠그고 모든 POST_IMAGE 참조를 확인한다.
DELETED 부모의 참조도 보존 대상으로 취급한다. 참조가 있으면 파일은 보존하고 작업만 종료한다.
공개 API는 기존 URL을 새 게시글에 연결할 수 없고 신규 업로드는 UUID + CREATE_NEW를 사용한다.
다른 파일을 덮어쓰거나 파일명 충돌 실패 후 기존 파일을 삭제하지 않는다.
DB 밖의 직접 참조 삽입·복구 작업은 이 프로토콜을 우회하므로 작업자 정지/별도 조정 없이 수행하지 않는다.

게시글 전체 삭제는 기존 소프트 삭제·댓글 연쇄 삭제·이미지 연결·파일 보존을 유지한다.
사용자/관리자/신고 경로에서 공개 GET만 함께 차단된다. 새로운 파일 정리 작업을 만들지 않는다.

## 커밋과 장애

- 롤백: 연결 제거와 작업 행이 같이 롤백된다. 기존 파일은 유지하고 신규 파일은 기존 업로드 롤백
  콜백으로 정리한다. 파일 업로드 후 프로세스 강제 종료로 롤백 콜백이 유실되는 과거 고아 파일 문제는
  이 개별 제거 큐가 자동 복구하지 않는다. 과거/업로드 중 고아 파일을 임의로 일괄 삭제하지 않는다.
- 커밋: AFTER_COMMIT에서 즉시 작업을 처리한다. 콜백 전에 종료되어도 작업 행이 남는다.
- 실패: 파일 삭제 오류는 attempts와 다음 시각을 저장한다. DB 오류는 작업을 롤백하고 다음 배치에서
  재시도한다. URL 접근은 이미 DB 상태로 차단된다. 내부 경로나 예외 원문은 정리 로그에 남기지 않는다.
- 중복/종료: 작업 행 잠금으로 같은 작업을 직렬화하고 없는 파일은 성공으로 처리한다.
  파일 삭제 후 작업 완료 커밋 전에 종료되어도 재시도 가능하다. 서로 다른 중복 작업도 멱등적이다.
- 재시작: 활성화된 스케줄러가 기본 60초 뒤/간격으로 기한이 지난 작업 최대 100개를 재시도한다.
  실패 지연은 1분이다. 백로그 및 권한/마운트 장애는 운영 감시가 필요하다.

파일 IO는 DB 트랜잭션에 참여하지 않는다. 삭제 성공 후 DB 장애가 나면 작업은 남을 수 있지만
원본 연결은 이미 제거되어 정상 게시글이 깨지지 않는다. 정리 worker는 계정/게시글 잠금을 잡지 않아
기존 계정 → 게시글 → 댓글 순서를 역전하지 않는다. 동기 콜백은 별도 풀 연결 및 파일 I/O를 사용한다.

## 경로와 캐시

영문/숫자/밑줄/하이픈 basename과 jpg/jpeg/png/webp/gif 확장자만 허용한다.
기존 서버 생성 UUID 이름 및 안전한 과거 basename은 지원한다. `/`, `\\`, `%`, `:`, 점 경로,
잘못된 확장자는 파일 읽기/삭제 전에 거부한다. 정규화한 실제 저장 루트의 직계 파일만 대상으로 하며
심볼릭 링크와 디렉터리는 거부한다. 저장소 부모/루트 교체 권한은 신뢰된 운영자에게만 허용해야 한다.
실제 Windows junction/재분석 지점 및 공격적인 로컬 파일 교체 경합은 별도 환경 검증이 필요하다.
정적 핸들러는 제거하며 SecurityConfig의 공개 GET 규칙은 상태 검사를 수행하는 Controller에 도달한다.
클래스패스 static 경로나 웹서버 alias로 업로드 폴더를 다시 공개하면 안 된다.

200/404 이미지 응답에 no-store, Pragma: no-cache, Expires: 0을 지정하고 ETag/Last-Modified를 만들지 않는다.
조건부/Range 요청도 DB 검사 후 처리한다. 읽기를 시작한 요청이 변경과 경합하면 이전 바이트가
응답될 수 있으며 전송 중인 응답과 이미 보관된 사본은 회수하지 않는다. 커밋 뒤 새 요청은 차단된다.
구버전 노드·기존 CDN 캐시·별도 공개 스토리지는 운영자가 제거/무효화해야 한다.

## 변경 파일

이번 9번의 30개 파일 목록이다. 이전 1~8번의 미커밋 변경을 포함한 전체 git diff와 구분한다.

| 파일 (backend 기준) | 변경 |
| --- | --- |
| src/main/java/com/kkirikkiri/community/post/controller/PostImageController.java | 기존 URL의 바이너리 응답, 404 및 캐시 |
| src/main/java/com/kkirikkiri/community/post/service/PostImageAccessService.java | 유효 연결/ACTIVE 검사 후 파일 읽기 |
| src/main/java/com/kkirikkiri/community/post/service/PostImageStorage.java | 안전한 읽기/삭제, 충돌 시 기존 파일 보호 |
| src/main/java/com/kkirikkiri/community/post/service/CommunityPostService.java | 기존 잠금 아래 제거 작업 기록 |
| src/main/java/com/kkirikkiri/community/post/service/PostImageCleanupService.java | 트랜잭션 내 enqueue, 커밋 후/배치 실행 |
| src/main/java/com/kkirikkiri/community/post/service/PostImageCleanupWorker.java | 독립 트랜잭션, 참조 검사, 실패 재시도 |
| src/main/java/com/kkirikkiri/community/post/service/PostImageCleanupScheduler.java | 재시작 후 영속 작업 재시도 |
| src/main/java/com/kkirikkiri/community/post/entity/PostImageCleanup.java | 영속 작업 Entity |
| src/main/java/com/kkirikkiri/community/post/entity/PostImage.java | URL 비고유 인덱스 선언 |
| src/main/java/com/kkirikkiri/community/post/repository/PostImageCleanupRepository.java | 기한별 배치/작업 행 잠금 |
| src/main/java/com/kkirikkiri/community/post/repository/PostImageRepository.java | 모든 참조/활성 연결 조회 |
| src/main/java/com/kkirikkiri/global/config/WebConfig.java | 업로드 정적 공개 제거 |
| src/test/resources/application-test.properties | 자동 재시도 스케줄 비활성화, 기본 임시 저장 루트 |
| build.gradle | 테스트 저장 루트/스케줄을 JVM 속성으로 강제해 상속된 운영 경로 배제 |
| src/test/java/com/kkirikkiri/community/CommunityImageLifecycleIntegrationTest.java | H2·실제 임시 파일·보안/MVC 검증 |
| src/test/java/com/kkirikkiri/community/CommunityImageHttpIntegrationTest.java | 임시 포트 내장 서버에 실제 TCP HTTP 요청 |
| src/test/java/com/kkirikkiri/community/CommunityCreationAndImageConcurrencyIntegrationTest.java | 기존 72개 유지, 새 정리 정책의 파일 수 기대값 |
| src/test/java/com/kkirikkiri/community/CommunityPostAnonymityPolicyIntegrationTest.java | 익명 이미지 교체 정리 기대값 |
| src/test/java/com/kkirikkiri/community/post/service/CommunityPostServiceTest.java | 정리 서비스 mock |
| src/test/java/com/kkirikkiri/community/post/service/PostImageStorageTest.java | UUID 충돌 시 기존 파일 보존 검증 |
| migrations/20261019_post_image_cleanup.sql | 수동 신규 테이블/인덱스 |
| migrations/20261019_post_image_cleanup.md, migrations/README.md | 적용·복구·운영 조건 |
| API.md, ERD.md, DEPLOYMENT.md, COMMUNITY_IMAGE_LIFECYCLE.md | 정책/계약/스키마/검증 기록 |
| COMMUNITY_READ_PERFORMANCE.md, COMMUNITY_CREATION_IMAGE_CONCURRENCY.md | 당시 제외한 9번의 후속 구현 안내 |
| tools/sync-contract-docs.cjs | 바이너리 ResponseEntity Controller 계약도 검사 |

프론트의 communityData는 동일 URL을 API origin 기준으로 해석하며, 업로드/keepImageIds·최대 5장·
미리보기·이미지 순서를 바꾸지 않는다. 프론트 소스는 이번 9번에서 수정하지 않는다.
관리자 직접/신고 삭제는 기존 소프트 삭제가 같은 공개 검사를 통과하지 못하므로 별도 예외 경로가 없다.

## 검증 및 운영 한계

2026-10-19 Windows / Java 21에서 최종 `gradlew.bat test bootJar` 성공.
66개 suite, 기존 1,103개 + 신규 52개 = **1,155개 통과**, 실패/오류/건너뜀 모두 0이다.
Gradle이 H2 메모리 DB, test 프로필, 임시 이미지 기본 루트와 스케줄 비활성화를 JVM 속성으로 강제한다.
실제 파일 검증 케이스는 JUnit @TempDir를 사용한다.

| 검증 | 결과 |
| --- | --- |
| CommunityImageLifecycleIntegrationTest | 신규 46개 통과: 실제 JPA·파일·MVC/보안 체인 |
| CommunityImageHttpIntegrationTest | 신규 5개 통과: 내장 서버 임시 포트에 실제 TCP HTTP GET |
| PostImageStorageTest | 14개 통과, 이 중 파일명 충돌 보호 1개 신규 |
| CommunityContentConcurrencyIntegrationTest (#1) | 기존 47개 통과 |
| CommunityAccountConcurrencyIntegrationTest (#2) | 기존 115개 통과 |
| CommunityCreationAndImageConcurrencyIntegrationTest (#4·5) | 기존 72개 통과, 성공 제거 파일 수 기대값만 새 정책 반영 |
| CommunityPostPrivacyIntegrationTest (#3) | 기존 24개 통과 |
| CommunityPostAnonymityPolicyIntegrationTest (#3) | 기존 23개 통과, 익명 이미지 교체 파일 수 기대값 갱신 |
| CommunityAnonymousAuthorIntegrationTest | 기존 3개 통과 |
| CommunityReadContractIntegrationTest (#7·8) | 기존 116개 통과 |
| CommunityReadPerformanceIntegrationTest (#8) | 기존 4개 통과, 목록/상세 쿼리 및 잠금 경계 검증 유지 |
| CommunityDetailReadConcurrencyIntegrationTest (#8) | 기존 8개 통과 |
| 프론트 npm test -- --watchAll=false --runInBand | 기존 19개 suite / 190개 통과 |
| bootJar / npm run build | 모두 성공 (프론트의 기존 Node fs.F_OK deprecation 경고 있음) |
| node tools/sync-contract-docs.cjs --check | 71개 endpoint / 71개 record DTO / 30개 Entity 통과 |
| git diff --check | 성공 (기존 LF/CRLF 변환 경고와 구분) |

실제 HTTP에서 비로그인 정상 파일 200과 개별 제거/사용자·관리자·신고 삭제 후 기존 URL 404를 확인했다.
개별 제거 파일의 실물 삭제와 전체 삭제 파일의 실물 보존을 함께 검사했다. 조건부/Range 요청도 삭제 후
파일을 반환하지 않는다. MVC에서는 탐색·인코딩·정적 우회·응답 헤더/내부 정보 비노출을 검증했다.
정상 JPEG/PNG/WebP/GIF, 익명·복수 이미지, 안전한 과거 basename, 유지 URL/순서, 다른 활성 파일도 검증했다.
커밋 이전 파일 보존·외부 읽기, 롤백의 기존 파일 복구 및 신규 파일 제거, 실제 파일 삭제 실패,
영속 작업 재시도, 파일 삭제 직후 모사 종료와 DB 작업 롤백, 중복 작업/동시 worker, 없는 파일,
다른 ACTIVE/DELETED 게시글의 참조 보호를 검사했다. Thread.sleep은 사용하지 않았다.

초기 회귀의 과거 파일 잔존 기대값을 확정 정책에 맞춰 수정했다. 신규 테스트의 DTO 생성자 인자 순서
컴파일 오류도 수정했다. 첫 전체 실행에서 신고 fixture 2건이 기존 `processedAt >= createdAt` 검증에
걸렸으며, 기존 동시성 테스트와 같이 과거 생성 시각을 명시했다. 기한 즉시 실행 테스트도 예정 시각을
과거로 고정해 DATETIME/시계 경계에 의존하지 않게 했다. 이후 전체 실행과 임시 경로 강제 설정 후
최종 전체 실행이 모두 통과했다. 신고의 업무 시간 검증 자체는 변경하지 않았다.

검증은 H2와 임시 디렉터리에서만 수행한다. 재시작은 새 worker/storage 및 강제 예외로 실패 구간을
모사한다. 실제 프로세스 kill, MySQL/InnoDB, 다중 JVM, 공유 볼륨, CDN, 브라우저 실사용은 검증하지 않았다.
[수동 배포 순서](migrations/20261019_post_image_cleanup.md)를 따른다.

파일 정리는 DB/저장소가 복구되고 worker가 계속 동작한다는 조건으로 재시도된다. 장기 장애·권한 오류·
잘못된 식별자·잘못된 노드별 마운트에서는 물리 정리를 완료할 수 없으므로 적체 감시가 필요하다.
과거 고아 파일/업로드 중 강제 종료 잔존 파일의 보존·정리 대상은 별도 검토 사항이며 자동 삭제하지 않는다.
기존 캐시의 사본은 소급 회수하지 못하므로 CDN 무효화와 구버전 공개 경로 제거가 배포 전 필수다.
운영/공유 DB에 DDL이나 정리 배치를 실행하지 않았으며 브랜치 변경·커밋·푸시·PR 생성도 하지 않았다.
