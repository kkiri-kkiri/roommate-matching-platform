# 커뮤니티 4·5번: 댓글 작성과 이미지 수정 동시성

검토일: 2026-10-19. 현재 브랜치의 미커밋 1번 보완을 유지하고 4·5번에 직접 필요한 변경만
추가한다. 계정 상태 경합(#2), 익명 게시글 ID(#3), 표시 정책 재설계(#6), 페이지 크기(#7),
조회 성능(#8), 성공한 연결 제거 후 물리 파일/URL 잔존(#9)은 변경하지 않는다.

위 범위와 아래 실행 결과는 4·5번 작업 당시 기록이다. 이후 2번에서 추가된 계정 선행 잠금,
같은 계정 쓰기의 직렬화 및 현재 검증 결과는 [계정 동시성 안내](COMMUNITY_ACCOUNT_CONCURRENCY.md)를 따른다.

## 기존 1번 확인과 원인

- 게시글 수정/일반·관리자 삭제/조회수 증가는 기존 PESSIMISTIC_WRITE 게시글 행 잠금을 사용한다.
  댓글 수정/삭제/좋아요의 댓글 잠금과 신고 대상 refresh도 실제 코드에 있었다.
- 전체 삭제는 게시글 → 댓글 ID 순 잠금이며 스키마/version 컬럼 변경이 없다.
  기존 `CommunityContentConcurrencyIntegrationTest` 47개를 변경하지 않고 회귀 검증한다.
- 일반 댓글 작성은 여전히 잠금 없는 게시글 ACTIVE 조회를 사용했다. 공개 대댓글도 부모를
  일반 조회했다. 익명 번호 부여 과정의 게시글 잠금만으로는 공개 작성이나 모든 삭제 경로를
  보호할 수 없었다. 소프트 삭제이므로 FK는 ACTIVE 관계를 보장하지 않는다.
- 부모 삭제는 대상만 DELETED로 바꾸고 자식 행을 ACTIVE로 남겨 두었다. 화면에서 자식이
  함께 사라지는 정책은 유지하되, 이번 완료 조건인 DB의 ACTIVE 고아 방지를 위해 기존 자식과
  동시에 작성된 자식도 같은 트랜잭션에서 소프트 삭제해야 한다.
- 이미지 수정은 **이미 1번 게시글 잠금 이후** 목록/개수 검사와 연결 변경을 하고 있었다.
  따라서 새 잠금 체계를 중복 도입하지 않는다. 추가 보완은 이전 RR 일반 조회 스냅샷과
  1차 캐시가 존재할 때도 최신 게시글/이미지 목록을 사용하는 것이다.

## 구현과 잠금 순서

`CommunityCommentWriteService`는 MANDATORY 전파로 호출자의 쓰기 트랜잭션에 참여한다.
별도 커밋이나 재시도는 하지 않는다. 상태를 제한하지 않는 게시글 잠금 조회를 공통 제공하며,
작성에는 ACTIVE를 요구하고 관리자 삭제는 기존처럼 과거 대상도 정리할 수 있다.

| 경로 | 순서 |
| --- | --- |
| 일반/익명 댓글 작성 | 게시글 잠금 → refresh/ACTIVE 검증 → 기존 작성자 확인·익명 번호 → INSERT |
| 대댓글 작성 | Entity를 로딩하지 않는 게시글 ID 조회 → 게시글 잠금/refresh → 부모 댓글 잠금/refresh → ACTIVE/깊이 검증 → INSERT |
| 일반 댓글 삭제 | 게시글 ID 조회 → 게시글 잠금 → 대상 댓글 잠금/refresh → 기존 게시글 상태·작성자 검증 → 활성 자식 ID 순 잠금/refresh → 소프트 삭제 |
| 관리자 댓글 삭제 | 기존 관리자 검증 → 같은 게시글/대상/자식 잠금 → 소프트 삭제 |
| 신고 댓글 DELETE_TARGET | 기존 관리자·신고 상태 검증 → 게시글 잠금 → 대상 refresh(PESSIMISTIC_WRITE) → 자식 잠금/소프트 삭제 → 신고 처리 |
| 게시글 전체 삭제 | 기존 게시글 잠금 → 활성 댓글 ID 순 잠금 → 전체 소프트 삭제 |
| 이미지 수정 | 기존 게시글 잠금 → 게시글 refresh/작성자 검증 → 이미지 잠금 목록/refresh → 최종 개수·ID 검증 → 연결 제거/재정렬/신규 저장 |

작성자가 부모 댓글 작성자일 필요는 없다는 기존 정책, 대댓글에 다시 대댓글을 작성할 수 없는
1단계 제한, 인증/작성자/관리자 검증과 ErrorCode를 유지한다. 이미 삭제된 게시글/부모는
INVALID_REQUEST이고 비작성자는 FORBIDDEN이다. 잠금 실패는 기존 409 충돌 처리와 전체 롤백을 따른다.

부모 삭제는 활성 대댓글도 DELETED로 바꾼다. 물리 삭제가 아니므로 신고/작성자/부모 참조를
보존한다. 다른 부모의 댓글과 대댓글은 변경하지 않는다. 이전 데이터의 일괄 정리는 실행하지 않는다.
이미 삭제된 신고 대상을 다시 삭제하는 기존 멱등성도 유지한다.

여러 행을 다루는 작성/삭제 경로는 게시글 잠금을 먼저 공유한다. 부모 이후 자식은 ID 순이며,
게시글 전체 삭제도 기존 ID 순서를 유지한다. 익명 번호 부여의 기존 게시글/매핑/이력 잠금은
같은 게시글 선행 잠금 안에서 실행된다. 댓글 수정·좋아요는 기존처럼 단일 댓글만 잠그고
게시글 잠금을 나중에 요청하지 않으므로 역방향 대기를 추가하지 않는다. 같은 게시글의 작성·삭제는
직렬화되지만 다른 게시글의 이미지 수정은 독립적으로 커밋할 수 있음을 테스트한다.
실제 InnoDB 인덱스·범위/FK 잠금과 잠금 획득 물리 순서는 실행계획에 따라 추가 검증이 필요하다.

## 이미지 API와 파일 처리

`keepImageIds`는 유지할 **전체** 기존 이미지 ID 목록이다. 누락한 연결은 제거하고 신규 파일을
그 뒤에 붙이는 계약을 유지한다. 유지 목록의 요청 순서 대신 현재 기존 표시 순서를 보존하고
0부터 연속 순서를 부여한다. 없는/다른 게시글 ID와 중복 ID는 기존 INVALID_REQUEST로 거절한다.

예: 기존 4장 ID를 공유하는 두 요청이 각 1장을 보내면 직렬 처리 후 최종은 5장이다. 후행 요청은
현재 목록을 다시 읽고, 자기 keepImageIds에 없는 선행 신규 이미지의 연결을 제거한다. 각 2장을
추가하는 3장 사례도 최종 5장이다. 새로 보낸 두 요청을 자동 append/병합하거나 무조건 충돌로
거절하는 정책을 새로 도입하지 않는다. 제거된 ID를 계속 유지하려는 후행 요청은 거절된다.

파일 형식·5MB·최대 5장 검증과 PostImageStorage의 기존 transaction synchronization을 유지한다.
한도 위반은 저장 전에 거절한다. 이전 정상 업로드 후 같은 트랜잭션에서 한도 실패가 발생하거나
복사 도중 실패하면 DB 변경 전체와 신규 파일을 롤백한다. 기존 파일은 삭제하지 않는다.
성공적으로 연결을 제거한 물리 파일이 남는 #9 정책은 이번에 구현하지 않는다.

## 신규 실제 JPA 테스트 목록

`CommunityCreationAndImageConcurrencyIntegrationTest`는 실제 Spring 서비스·JPA·H2 독립 트랜잭션,
실제 PostImageStorage와 매 테스트마다 새로운 JUnit TempDir을 사용한다. 업로드 디렉터리는
테스트 fixture에서 TempDir로 바꾸며 실제 uploads/운영 파일을 읽거나 삭제하지 않는다.
Latch로 선행 트랜잭션 커밋을 제어하고 H2 SESSIONS.BLOCKER_ID로 실제 잠금 대기를 확인한다.
Thread.sleep에 의존하지 않는다. 정상 커밋 후 SQL로 최종 상태를 검증한다.

| 4번 테스트 메서드 | 실행 수 | 최종 검증 |
| --- | ---: | --- |
| creationAndPostDeletionLeaveNoActiveComments | 24 | 일반/대댓글, 공개/익명, 일반/관리자/신고 게시글 삭제 양쪽 순서, ACTIVE 0 |
| replyCreationAndParentDeletionSoftDeleteTheEntireThread | 12 | 일반/관리자/신고 부모 삭제 양쪽 순서, 기존/신규 자식 DELETED, 다른 스레드 보존·표시 |
| committedDeletionAndReplyDepthAreRejectedWithoutInserts | 3 | 삭제 완료 후 작성과 잘못된 깊이 거절, INSERT 없음 |
| previouslyLoadedPostAndParentCannotAuthorizeCreationAfterDeletion | 2 | 먼저 로딩한 게시글/부모가 있어도 삭제 후 작성 거절 |
| parentDeletionIncludesRepliesCommittedAfterEarlierEntityAndListReads | 3 | 이전 Entity/목록 조회 이후 커밋된 대댓글도 삭제에 포함 (RC) |
| h2RepeatableReadIsNotAnInnoDbCurrentReadOracleForNewReplyRows | 1 | H2 RR 신규 행 누락을 읽기 전용 진단으로 명시, 삭제/고아를 커밋하지 않음 |
| h2RepeatableReadCreationConflictDoesNotLeaveAnOrphan | 2 | H2 RR 변경된 게시글 충돌 롤백, ACTIVE 고아 없음 |
| creationLockTimeoutRollsBackEarlierInsertAndNewFile | 1 | timeout 전 다른 게시글에 INSERT한 댓글과 신규 파일도 롤백 |
| 합계 | 48 | 활성 게시글/부모/자식 관계 검증 47개 + H2 차이 진단 1개 |

| 5번 테스트 메서드 | 실행 수 | 최종 검증 |
| --- | ---: | --- |
| concurrentImageReplacementsNeverExceedFiveAndUseLatestConnections | 2 | 4+1/3+2 동시 요청, 최종 5장·정확한 ID/순서/연결 |
| fiveKeptImagesPlusOneIsRejectedBeforeAnyFileIsWritten | 1 | 5장+1 거절, DB·파일 변경 없음 |
| concurrentAddAndRemoveValidateAgainstCurrentImageConnections | 2 | 추가/제거 양쪽 순서, 제거된 ID 유지 요청 거절 |
| imageAdditionAndPostDeletionUseTheSamePostLock | 6 | 일반/관리자/신고 삭제와 양쪽 순서, 삭제 후 업로드 없음 |
| imageAdditionAndOrdinaryEditApplySerialReplacementSemantics | 2 | 일반 수정과 직렬 교체, 의도된 최종 목록 유지 |
| limitFailureRollsBackAnEarlierSuccessfulUploadInTheSameTransaction | 1 | 한도 실패 시 앞선 DB·파일 업로드 전체 롤백 |
| copyFailureAfterFirstNewFileRestoresDbAndCleansOnlyNewFiles | 1 | 두 번째 실제 복사 실패 후 첫 신규/부분 파일 제거와 기존 연결/파일 복구 |
| createAndUpdateSupportAllImageCountBoundaries | 6 | 0~5장 작성/수정·순서·익명·상세 응답 회귀 |
| differentPostsCanUploadWhileAnotherPostTransactionIsHeld | 1 | 다른 게시글은 선행 잠금 해제 전 커밋 |
| cachedPostAndImagesAreRefreshedBeforeApplyingTheNextReplacement | 1 | 1차 캐시의 오래된 게시글/이미지 순서 배제, 조회수 보존 |
| imagePermissionAndForeignKeepIdFailuresWriteNoFiles | 1 | 비작성자/외부 이미지 ID 실패 후 DB·파일 보존 |
| 합계 | 24 | 파일 존재·크기, ID/URL 고유성, 연속 순서 검증 포함 |

## H2와 실제 MySQL의 구분

정상 직렬 처리/목록 조회 테스트는 H2 READ COMMITTED에서 수행한다. H2 RR은 변경된 행의 잠금
충돌과 롤백을 검증한다. 추가 조사에서 H2 RR의 이전 스냅샷 이후 삽입된 자식이 잠금 목록에
포함되지 않는 것을 관찰했다. 이 차이는 별도 읽기 전용 진단 테스트로 남겼다. 해당 성공
시나리오를 H2 RR로 검증했다고 주장하지 않고 RC의 캐시 재조회 테스트로 분리했다.
운영 코드나 기본 DB 격리 수준을 H2에 맞춰 바꾸지 않았다.

InnoDB의 FOR UPDATE는 최신 데이터를 읽는다([MySQL 공식 잠금 읽기 문서](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)).
이미지와 삭제 대상 자식 목록을 잠금 쿼리로 바꾼 이유다. 문서 확인은 실제 MySQL 검증이 아니다.
실제 MySQL 및 다중 JVM 검증은 **미실행**이다. 운영/공유 DB를 사용하지 않았으며 Gradle은
test 프로필·H2 메모리 DB·SQL/시드/수집 비활성화를 강제한다.

격리 MySQL에서 배포 전에 다음을 확인해야 한다.

1. REPEATABLE READ에서 scalar ID 조회/관리자 검증/이전 목록 조회로 스냅샷을 연 뒤 작성 커밋을
   끼워 넣어도, 삭제의 잠금 목록에 새 자식이 포함되고 ACTIVE 고아가 남지 않는지 확인한다.
2. 이전 이미지 스냅샷을 가진 수정도 최신 잠금 목록·refresh를 사용하여 5장/ID/순서를 유지하는지 확인한다.
3. 실제 FK·인덱스·실행계획에서 게시글→댓글/이미지 잠금과 timeout/교착 롤백을 확인한다.
4. 두 JVM의 작성/삭제/이미지 요청에서도 최종 관계, 조회수, 롤백 파일 정리가 같은지 확인한다.

## 실행과 배포

최종 실행 결과(2026-10-19):

| 검사 | 결과 |
| --- | --- |
| 기존 테스트 | 741개 통과 (기존 1번 실제 JPA 통합 테스트 47개 포함, 해당 파일 변경 없음) |
| 신규 테스트 | 72개 통과: 4번 정합성 47개 + H2 차이 진단 1개 + 5번 24개 |
| 전체 | 58개 클래스, 813개 통과, 실패/오류/건너뜀 0개 |
| `gradlew.bat test bootJar` | 성공 |
| `sync-contract-docs.cjs --check` | 성공: endpoint 70개, record DTO 71개, Entity 29개 |
| `git diff --check` | 성공 |
| 실제 MySQL / 다중 JVM | 미실행 |

초기 추가 RR 테스트 3건의 실패는 오래된 H2 RR 스냅샷에서 새 자식 행이 잠금 목록에 누락되는
차이였다. 이 결과를 없던 것으로 처리하지 않고 위 별도 진단으로 남겼으며, 정상 관계 검증은
H2 RC로 분리한 뒤 최종 전체 테스트를 재실행했다. MySQL RR의 성공을 H2 결과로 대체하지 않는다.

```powershell
cd backend
.\gradlew.bat test bootJar
cd ..
node backend/tools/sync-contract-docs.cjs --check
```

Entity/Controller/DTO 선언과 DB 스키마는 변경하지 않는다. 마이그레이션·기존 데이터 정리·새 파일
삭제 정책은 없다. 구버전 댓글 작성/삭제 요청과 진행 중인 트랜잭션을 종료한 뒤 전체 서버에
적용한다. 같은 게시글 작성/삭제의 잠금 대기가 늘 수 있으므로 실제 요청량/timeout을 확인한다.
