# 익명 댓글 번호 (2026-10-13)

`20261013_community_anonymous_authors.sql`은 기존 댓글을 변경하지 않고 영구 매핑 테이블 하나를 추가한다. 운영 DB에는 아직 적용하지 않았다.

1. DB 백업 후 이전 백엔드 인스턴스를 모두 중지한다. 구버전은 매핑 없이 익명 댓글을 저장하므로 혼합 버전 쓰기를 허용하지 않는다.
2. 대상 DB에서 SQL을 실행한다. 현재 개발 설정의 `ddl-auto=update`도 테이블을 생성할 수 있으나 운영에서는 명시적 SQL 적용과 제약 확인을 권장한다. SQL은 자동 실행에 등록하지 않았다.
3. 새 백엔드와 프론트엔드를 함께 배포한다. 익명 댓글의 `author.id`가 null로 변경되므로 UI는 `mine`으로 본인 여부를 판단하고 채팅은 댓글 출처로 대상을 찾는다.
4. 게시글의 첫 상세 조회 또는 새 익명 댓글 작성 시, 게시글 잠금 안에서 기존 익명 댓글/대댓글을 `created_at ASC, comment_id ASC`로 읽어 사용자별 번호를 저장한다. DELETED 등 모든 상태를 포함한다. 매핑이 이미 존재하는 게시글은 다시 번호를 계산하지 않는다. 데이터가 많은 게시글은 첫 접근의 초기화 시간이 추가된다.
5. 댓글 수정·삭제는 매핑을 삭제하지 않는다. 매핑 테이블을 비우거나 일부만 수동 입력하면 번호 보존을 보장할 수 없으므로 댓글 백업과 함께 보존한다. 하드 삭제된 과거 댓글은 원본 이력이 없으므로 최초 참여 순서를 복원할 수 없다.

번호 배정은 게시글 행 잠금으로 직렬화하고, 매핑 조회는 MySQL REPEATABLE READ에서도 최신 커밋을 보는 잠금 조회를 사용한다. `(post_id, author_id)`와 `(post_id, anonymous_number)` UNIQUE 제약도 적용한다. 번호 배정과 댓글 저장은 같은 트랜잭션이므로 실패 시 함께 롤백된다.

롤백 시 테이블을 삭제하지 않는다. 구버전 백엔드는 익명 작성자 ID를 노출하고 새 댓글의 번호를 예약하지 않으므로 단순 구버전 재배포는 안전한 롤백이 아니다. 쓰기를 중지한 뒤 신버전 수정 배포 또는 백업 복구 계획을 검토한다.

테스트용 H2 의존성은 운영 JAR에 포함되지 않는다. 실제 MySQL 운영 DB의 마이그레이션 적용 검증은 배포 단계에서 수행한다.

## 검증 방법과 결과

- `CommunityAnonymousAuthorIntegrationTest`: 첫 번호, 공개 참여 제외, 댓글/답글 공유, 게시글 독립성, 수정·삭제·재작성, 역순 DTO 변환, 삭제 이력과 동일 시각의 ID 순서, 4명/8개 동시 작성 요청을 검증한다.
- 기본 통합 테스트는 독립 H2 메모리 DB를 사용한다. 같은 테스트를 별도 MySQL 8.0.44 / REPEATABLE READ DB에서도 실행하여 통과했다. SQL 신규 적용과 재실행, 두 UNIQUE 인덱스도 확인했다.
- MySQL로 재현하려면 `ANONYMOUS_TEST_DB_URL`, `ANONYMOUS_TEST_DB_DRIVER=com.mysql.cj.jdbc.Driver`, `ANONYMOUS_TEST_DB_USER`, `ANONYMOUS_TEST_DB_PASSWORD`를 설정하고 `gradlew.bat test --tests '*CommunityAnonymousAuthorIntegrationTest'`를 실행한다. 테스트의 `create-drop`이 테이블을 생성·삭제하므로 반드시 별도 테스트 DB만 지정한다.
- 전체 백엔드 테스트 420개 및 배포용 JAR 빌드 통과. 일반 Spring 테스트 컨텍스트도 H2로 격리하여 `gradlew.bat test bootJar`로 수행했다. `SPRING_DATASOURCE_URL=jdbc:h2:mem:fulltest;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1`, 드라이버 `org.h2.Driver`, 사용자 `sa`, 빈 암호, `SPRING_JPA_HIBERNATE_DDL_AUTO=create-drop`, `APP_SEED_MODE=NONE`, `CALENDAR_CRAWL_ON_STARTUP=false`를 사용했다.
- 프론트엔드 신규 익명 댓글 테스트와 기존 데이터 변환·채팅 테스트 49개 통과. `npm run build` 성공.
- 전체 프론트엔드 테스트는 96개 통과, 8개 실패 및 API 테스트 2개 스위트 로드 실패. 변경 전 HEAD의 별도 복사본도 94개 통과, 동일한 8개 실패 및 동일한 2개 로드 실패를 재현했다. 실패는 관리자 화면 테스트 5개, 커뮤니티의 오류 표시 테스트 3개, `communityApi.test.js`/`surveyApi.test.js`의 axios interceptor 모킹이다. 이번 변경과 무관한 코드는 수정하지 않았다.

## 생성·수정 파일

신규:

- `backend/src/main/java/com/kkirikkiri/community/comment/entity/CommunityAnonymousAuthor.java`
- `backend/src/main/java/com/kkirikkiri/community/comment/repository/CommunityAnonymousAuthorRepository.java`
- `backend/src/main/java/com/kkirikkiri/community/comment/service/CommunityAnonymousAuthorService.java`
- `backend/src/test/java/com/kkirikkiri/community/comment/service/CommunityAnonymousAuthorIntegrationTest.java`
- `frontend/src/pages/community/CommunityAnonymousComments.test.js`
- `backend/migrations/20261013_community_anonymous_authors.sql`
- 이 문서

수정:

- `backend/src/main/java/com/kkirikkiri/community/comment/dto/CommunityCommentResponse.java`
- `backend/src/main/java/com/kkirikkiri/community/comment/repository/CommunityCommentRepository.java`
- `backend/src/main/java/com/kkirikkiri/community/comment/service/CommunityCommentService.java`
- `backend/src/main/java/com/kkirikkiri/community/post/service/CommunityPostService.java`
- `backend/src/main/java/com/kkirikkiri/chat/dto/ChatRoomCreateRequest.java`
- `backend/src/main/java/com/kkirikkiri/chat/service/ChatService.java`
- `backend/src/test/java/com/kkirikkiri/community/comment/service/CommunityCommentServiceTest.java`
- `backend/src/test/java/com/kkirikkiri/community/post/service/CommunityPostServiceTest.java`
- `backend/src/test/java/com/kkirikkiri/chat/service/ChatServiceTest.java`
- `frontend/src/pages/community/CommunityPostPage.js`
- `frontend/src/pages/community/communityData.js`
- `backend/build.gradle`, `backend/API.md`, `backend/ERD.md`

