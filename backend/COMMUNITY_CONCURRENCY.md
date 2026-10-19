# 커뮤니티 1번 문제: 삭제 상태 복구와 조회수 유실

검토일: 2026-10-19. 이 문서는 1번 게시글·댓글 수정/삭제와 조회수 보완의 기준이다.
이후 4·5번 댓글 작성/이미지 동시성 보완은 [별도 안내](COMMUNITY_CREATION_IMAGE_CONCURRENCY.md)에
기록한다. 아래 1번 검증 기록을 유지하며, 현재 댓글 삭제의 게시글 선행 잠금과 대댓글 소프트 삭제는
4번 보완 내용을 함께 따른다. 이후 2번 계정 선행 잠금과 현재 전체 잠금 순서는
[계정 동시성 안내](COMMUNITY_ACCOUNT_CONCURRENCY.md)를 따른다. 아래 실행 결과는 1번 작업 당시 기록이다.

## 원인과 선택한 방식

CommunityPost/CommunityComment에는 `@Version`과 `@DynamicUpdate`가 없다. 수정/삭제 서비스에는
이미 쓰기 `@Transactional`이 있었지만, 일반 조회로 로딩한 ACTIVE Entity의 상태 검사와 나중의
커밋 사이에 다른 트랜잭션이 끼어들 수 있었다. Hibernate의 dirty checking은 변경된 Entity에
전체 쓰기 가능 컬럼 UPDATE를 생성하므로, 제목/내용만 수정해도 과거 status와 view_count가
포함된다. 삭제 커밋 뒤 과거 ACTIVE를 저장하거나 증가한 조회수를 이전 값으로 저장할 수 있었다.

공개 상세 조회는 이미 `findDetailForUpdate(PESSIMISTIC_WRITE)` 뒤 Entity의 조회수를 증가시키고,
증가 후 DTO를 반환했다. 따라서 여러 상세 조회끼리는 보호되어 있었지만 잠금 없는 수정과
관리자 삭제가 그 값을 다시 덮어쓸 수 있었다. 관리자 직접 삭제의 조회와 신고 대상 fetch join도
잠금이 없었다. 별도 게시글/댓글 복구 기능은 현재 서비스에 없다.

기존 상세 조회·좋아요·익명 번호 부여의 DB 행 잠금과 수정/삭제의 잠금을 통일한다.
같은 행을 수정하는 모든 해당 경로가 커밋까지 배타 잠금을 유지하므로, 선행 수정이 끝난 뒤
삭제되거나 선행 삭제 뒤 후행 수정이 거절된다. 조회수 증가 뒤 수정은 최신 조회수로 작업하고,
수정 뒤 조회는 최신 본문으로 증가시킨다. 조회수의 별도 bulk UPDATE나 카운트 정책 변경은 없다.

`@Version`은 기존 테이블의 신규 컬럼·기존 행 초기화·혼합 배포 검토가 필요하고 조회수 증가도
충돌 요인이 된다. 조건부 UPDATE는 게시글 이미지와 삭제 대상 댓글 처리까지 조건/영속성 컨텍스트
동기화를 확장해야 한다. `@DynamicUpdate`만으로는 삭제 후 수정의 상태·권한 검증을 보장하지 못한다.
이미 존재하는 비관적 잠금의 재사용이 이번 범위에서 가장 작은 호환성 변경이다.

## 경로와 잠금 순서

| 경로 | 잠금 및 검증 |
| --- | --- |
| 게시글 수정/일반 삭제 | ACTIVE 게시글 잠금 조회 → 작성자 ID 검증 → 변경 |
| 관리자 게시글 직접 삭제 | 기존 관리자 검증 → ACTIVE 게시글 잠금 조회 → 댓글 잠금 → 삭제 |
| 상세 조회/게시글 좋아요 | 기존 게시글 잠금 유지 |
| 댓글·대댓글 수정 | ACTIVE 댓글 잠금 조회 → 기존 게시글 상태 검증 → 작성자 ID 검증 → 변경 |
| 일반/관리자 댓글 삭제 | 4번 보완: 게시글 → 대상 댓글 → 활성 대댓글 ID 순 잠금, 일반 삭제는 상태/작성자 검증 |
| 댓글 좋아요 | 기존 댓글 잠금을 공통 `findForUpdate`로 이름 변경, 동작 유지 |
| 게시글 전체 삭제 | 게시글 잠금 → ACTIVE 댓글을 commentId 오름차순 잠금 → 댓글/게시글 소프트 삭제 |
| 신고 DELETE_TARGET | 기존 관리자/신고 상태 검증 → `refresh(target, PESSIMISTIC_WRITE)` → 삭제 및 신고 처리 |

신고 조회는 대상 Entity를 이미 fetch join한다. 잠금 쿼리만 다시 호출하면 1차 캐시의 오래된
Entity가 재사용될 수 있어 `refresh`로 잠금과 최신 상태 재로딩을 함께 수행한다. 이미 삭제된 신고
대상을 다시 삭제하는 기존 멱등 동작도 유지한다. 신고 처리 이력과 대상 변경은 한 트랜잭션이다.

일반 수정/삭제의 ACTIVE 조건과 작성자 검증은 잠금 획득 뒤의 대상에 적용된다. 잠금 실패를
서비스가 삼키거나 부분 커밋하지 않는다. 기존 공통 예외 처리가 잠금/동시성 실패를
`409 ACCOUNT_UPDATE_CONFLICT`로 반환하며, 이미 삭제된 대상은 `400 INVALID_REQUEST`,
다른 작성자는 `403 FORBIDDEN`을 유지한다.

단일 댓글 수정·좋아요는 댓글을 잠근 뒤 게시글/부모 댓글의 **잠금**을 추가로 요청하지 않는다.
게시글 상태를 읽는 일반 SELECT는 유지한다. 게시글 → 댓글과 역방향 잠금 간 순환을 새로
만들지 않는다. 익명 번호는 기존 게시글 → 익명 번호 매핑 → 과거 익명 댓글 순서를 유지하고,
게시글 잠금이 같은 게시글의 전체 삭제와 익명 초기화를 직렬화한다. 게시글 이미지 쓰기도
게시글 잠금 이후이며 같은 게시글의 여러 수정이 직렬화된다.

향후 공통 계정 잠금을 적용할 때는 필요한 계정 행들을 정해진 ID 순서로 먼저 잠근 뒤
게시글 → 익명 매핑(필요 시) → 댓글 순으로 확장해야 한다. 댓글 잠금 뒤 계정/게시글을
잠그는 역방향 경로를 추가하지 않는다. 이 계정/댓글 작성 보완을 이번에 구현한 것은 아니다.
실제 InnoDB의 인덱스·범위/FK 잠금 및 실행계획에 따른 교착 가능성은 H2로 배제할 수 없다.

## 자동 검증

`CommunityContentConcurrencyIntegrationTest`는 실제 Spring 서비스와 JPA, 독립 트랜잭션을 사용한다.
CountDownLatch로 첫 트랜잭션을 커밋 직전에 유지하고, H2 INFORMATION_SCHEMA.SESSIONS의
BLOCKER_ID로 후행 트랜잭션의 실제 잠금 대기를 확인한 다음 첫 커밋을 허용한다.
Thread.sleep이나 우연한 스레드 실행 순서로 경합을 가정하지 않는다. 조회수 다중 요청은
시작 barrier로 맞추고 각 응답이 1~8의 서로 다른 증가값을 반환하는지 확인한다.

| 신규 테스트 메서드 | 검증 |
| --- | --- |
| postEditAndUserOrAdminDeleteSerializeInBothOrders | 일반/관리자 삭제와 수정의 양쪽 선행 순서 |
| rejectsPostEditAfterDeletionHasAlreadyCommitted | 삭제 커밋 후 수정/상세 조회 거절 |
| deletesAfterEditHasAlreadyCommitted | 수정 커밋 후 삭제 |
| postEditAndViewPreserveBothContentAndCount | 수정/조회 양쪽 순서에서 본문과 조회수 보존 |
| postDeletionAndViewPreserveDeletionAndCommittedViewCount | 일반/관리자 삭제와 조회 양쪽 순서에서 삭제·조회수 보존 |
| simultaneousViewsEachIncrementExactlyOnceAndReturnIncrementedCounts | 8개 독립 조회의 정확한 누적과 응답 |
| commentOrReplyEditAndUserOrAdminDeleteSerialize | 댓글/대댓글 수정과 일반/관리자 삭제 양쪽 순서 |
| commentOrReplyEditCannotUndoContainingPostDeletion | 게시글 전체 삭제와 댓글/대댓글 수정 양쪽 순서 |
| rejectsEditsOfPreviouslyDeletedCommentsAndReplies | 이미 삭제된 댓글/대댓글 수정 거절 |
| crudPermissionsAnonymousLikesAndViewPolicyRemainUnchanged | CRUD·비작성자/비관리자 차단·익명·좋아요·증가 정책 |
| reportedTargetIsRefreshedUnderLockAfterEarlierEntityLoad | 미리 로딩한 신고 대상도 최신 본문/조회수로 삭제 |
| h2RepeatableReadConflictRollsBackWithoutResurrectionOrLostViews | H2 RR 변경된 snapshot의 잠금 충돌 시 보존 |
| h2RepeatableReadStaleReportTargetConflictLeavesReportPending | H2 RR refresh 충돌 시 대상/신고 처리 롤백 |
| lockTimeoutRollsBackEarlierChangesInLosingTransaction | 실제 잠금 timeout 후 앞서 flush한 변경 롤백 및 재사용 |
| failedPostEditRollsBackContentAnonymousFlagAndImageDeletion | flush 이후 예외에서 본문·익명·이미지 삭제 롤백 |
| reportedTargetAndProcessingHistoryRollBackTogether | 삭제 및 신고 처리 flush 이후 예외에서 전체 롤백 |

H2의 READ COMMITTED에서 대기 후 최신 값을 읽고 정상 처리되는 경우를 검증한다.
H2 REPEATABLE READ는 선행 커밋으로 행이 바뀌면 잠금 조회/refresh가 직렬화 충돌로 실패할 수
있으므로, 별도 테스트에서 실제 충돌과 전체 롤백을 검증한다. 이를 MySQL REPEATABLE READ의
동작 검증으로 간주하지 않는다. 운영의 격리 수준이나 자동 재시도 정책은 변경하지 않는다.
신고 fixture의 생성 시각은 과거 고정값으로 두어 기존 시간 검증과 동시성 검증을 분리한다.

InnoDB의 FOR UPDATE는 최신 값을 읽는 잠금 읽기이며, 같은 행의 잠금 해제까지 기다리는
규칙은 [MySQL 공식 문서](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)를
참고했다. H2의 격리 수준/MVCC 일반 설명은
[H2 공식 문서](https://www.h2database.com/html/advanced.html#transaction_isolation)를 따르며,
위 RR 변경 snapshot 충돌은 이번 H2 테스트에서 관찰·검증한 결과다. 문서 확인은 실제 DB 실행 검증이 아니다.

실행 명령(Windows):

```powershell
cd backend
.\gradlew.bat test bootJar
cd ..
node backend/tools/sync-contract-docs.cjs --check
```

Gradle은 test 프로필·H2 메모리 DB·SQL/시드/수집 비활성화를 강제한다. 외부 MySQL URL로
이 테스트를 실행하지 않는다. 별도로 확인된 격리 MySQL DB가 제공되지 않아 실제 MySQL 및
다중 JVM 테스트는 **미실행**이다. InnoDB REPEATABLE READ에서 같은 시나리오의 잠금 대기,
최신 상태/조회수, timeout/교착 롤백, 다중 서버 동시 요청을 배포 전에 추가 검증해야 한다.

## 1번 작업 당시 실행 결과 (2026-10-19)

| 항목 | 결과 |
| --- | --- |
| `gradlew.bat test bootJar` | 성공 |
| 기존 테스트 | 694개 통과 |
| 신규 실제 JPA 통합 테스트 | 47개 통과 (매개변수별 실행 포함) |
| 전체 | 57개 테스트 클래스, 741개 통과, 실패/오류/건너뜀 0개 |
| 문서 스냅샷 검사 | 통과: Controller endpoint 70개, record DTO 71개, Entity 29개 |
| `git diff --check` | 통과 |
| 실제 MySQL / 다중 JVM | 미실행 |

게시글/댓글/대댓글의 수정과 삭제 양쪽 순서에서 최종 DELETED를 확인했다. 수정/조회와
삭제/조회 경합에서도 커밋된 조회수를 보존하고, 8개 동시 조회는 최종 8회와 응답 1~8을
확인했다. H2 RR 충돌과 timeout에서는 일부 변경만 커밋되지 않고 롤백되는 것을 확인했다.

초기 테스트 fixture의 생활관 이름 UNIQUE 중복을 제거했다. H2 RR의 변경 snapshot 충돌을
정상 대기/성공으로 기대했던 테스트도 RC의 정상 처리와 RR의 충돌/롤백 검증으로 분리했다.
두 문제를 분석·수정한 뒤 위 최종 소스로 전체 테스트와 빌드를 다시 실행했다.

## 호환성과 배포

Controller/DTO/Entity 선언과 스키마는 변경하지 않는다. 추가 DDL·version 초기화·마이그레이션은
없다. 기존 익명 번호, 좋아요, 이미지 처리, 조회수 응답과 증가 정책을 유지한다.
부모 댓글 삭제 시 대댓글도 함께 사라지는 기존 표시 정책은 수정하지 않는다.

같은 행을 다루는 요청의 잠금 대기는 늘 수 있다. 운영 DB의 timeout과 게시글별 요청량을
확인해야 한다. 구버전에는 잠금 없는 수정 경로가 있으므로, 구버전 커뮤니티 쓰기 요청과
진행 중 트랜잭션을 종료하고 전체 인스턴스에 적용한다. 운영/공유 DB 데이터 변경은 수행하지 않는다.
