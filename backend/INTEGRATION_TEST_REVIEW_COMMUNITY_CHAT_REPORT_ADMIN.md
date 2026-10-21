# community · chat · report · admin 통합 테스트 검토 (2026-10-21)

## 검토 기준과 기존 범위

`INTEGRATION_TEST_GUIDE.md`, `API.md` 7~10·13절, `ERD.md`, `DEPLOYMENT.md`,
`build.gradle`, `ApiIntegrationTest`, `TestUsers`와 관련 Controller/DTO/Service/Repository/Entity를 비교했다.
기존 담당 도메인 통합 테스트 20개 클래스는 그대로 유지했다.

| 도메인 | 기존 통합 테스트 | 충분한 기존 검증 / 추가 판단 |
| --- | --- | --- |
| community (13개 클래스) | `Community*IntegrationTest`, `CommunityAnonymousAuthorIntegrationTest`, `PostLikeListIntegrationTest`, `PostNullableDormitoryIntegrationTest` | 게시글 작성·수정·삭제, 익명성·소유권, 목록·정렬·집계, 좋아요, 이미지 수명주기, 계정/콘텐츠/생성 동시성을 재사용한다. 일반 댓글·답글 HTTP 쓰기와 유효성·인증 거부 후 DB 보존을 보완한다. |
| chat (4개 클래스) | `ChatMessageCursorIntegrationTest`, `ChatReadConcurrencyIntegrationTest`, `ChatRoomReadPerformanceIntegrationTest`, `SharedAccountConcurrencyIntegrationTest` | 커서·검색·페이지·읽음·고정 상한·동시성은 기존 검증을 유지한다. 방 생성/재사용의 실제 HTTP 커밋·생성 중 롤백, 개인 설정 null 의미, 나간 사용자/외부인 차단, HTTP 읽음과 조회 전용 polling 연결을 보완한다. 커뮤니티 테스트의 익명 채팅도 중복하지 않는다. |
| report (1개 클래스) | `ReportRegressionIntegrationTest`; chat의 공유 계정/중복 신고 테스트 | USER 신고의 상세 사유 경계·Principal, 초 단위 시각, 공유 잠금은 기존 검증을 유지한다. POST/COMMENT의 실제 대상 작성자/FK, 처리 상태별 중복 거절, 다른 신고자의 독립성, 없는 대상과 잘못된 enum 값을 HTTP→DB로 검증한다. |
| admin (2개 클래스) | `AdminListPaginationIntegrationTest`, `AdminReportConcurrencyIntegrationTest`; community의 관리자 삭제·개인정보 테스트 | 목록 페이징/권한·검색, 신고 처리 경합·잠금·롤백, 게시글 이미지 삭제를 재사용한다. 댓글 신고 제재·감사 이력·대시보드 연계, 회원 상태와 기존 JWT의 효력, 탈퇴 복구 거부, 관리 쓰기 인가/검증을 보완한다. |

`CommunityPostServiceTest`, `CommunityCommentServiceTest`, `ChatServiceTest`, `ReportServiceTest`,
`AdminServiceTest`는 내부 의존성을 mock하는 단위 테스트다. DTO/Entity 테스트도 DB 통합 검증으로 세지 않았다.
`ChatReadControllerTest`/`ChatQueryControllerTest`는 Service와 UserRepository를 mock하는 `@WebMvcTest`,
`CommunityPostUploadTest`는 Service를 mock하는 standalone MockMvc,
`ChatWebSocketControllerTest`는 메시지 저장/발행 호출의 단위 테스트다.
이들의 HTTP 바인딩/호출 검증을 실제 저장·커밋·전달 검증과 구분했다.

## 추가 파일과 시나리오

모두 `src/test/java/com/kkirikkiri/integration/<도메인>/`에 추가했다.

| 파일 | 실행 수 | 핵심 시나리오 |
| --- | ---: | --- |
| `community/CommunityCommentApiIntegrationTest.java` | 10 | 1,000자 댓글·답글 작성/수정/내 활동/스레드 삭제, Principal 작성자, 공백·1,001자 쓰기 거부, 타인 수정·삭제 및 미인증 쓰기 차단, 실패 후 DB 보존 |
| `chat/ChatRoomApiIntegrationTest.java` | 9 | 양방향 생성 재사용·참여자 2명 커밋, 프로필 누락 시 생성 전체 롤백, 설정 생략/null/빈 객체와 사용자 격리, 나가기 후 메시지 보존·접근/재입장 차단, 외부인 차단, 조회 전용 polling·중복 ID 읽음·미확인 메시지 보존, 자기 자신/정지 대상/잘못된 출처 거부 |
| `report/ReportTargetApiIntegrationTest.java` | 11 | POST/COMMENT × 대기/완료/반려 중복 거부, 실제 작성자/FK 및 nullable 참조, 다른 신고자 허용, 관리자 상세·처리 이력, 제재 없는 처리의 콘텐츠/계정 보존, 없는 대상 3종, 잘못된 대상 종류·사유 |
| `admin/AdminModerationApiIntegrationTest.java` | 11 | 댓글 신고 여부 필터·삭제의 답글 연쇄/형제 보존·감사 이력·대시보드 집계, 신고 검색·종류·상태 필터, 작성자 정지와 기존 토큰 차단, 정지/해제/탈퇴 및 회원 상세 활동 집계, 탈퇴 복구 거부, USER 신고 삭제 거부의 미처리 이력 보존, 미인증/일반회원/정지 관리자 쓰기 차단, 잘못된 상태/액션 거부 |

공통 `@ApiIntegrationTest`/`TestUsers`를 사용하며 내부 Service/Repository는 mock하지 않는다.
동기 테스트는 기본 롤백과 `flush()`/`clear()` 후 재조회로 저장 상태를 확인한다.
채팅은 생성 API의 `NOT_SUPPORTED`/`REQUIRES_NEW` 때문에 테스트도 `NOT_SUPPORTED`로 실행한다.
fixture/검증/정리는 `TransactionTemplate`로 수행하고 `@AfterEach`에서 해당 테스트의 메시지→참여자→방→
프로필→사용자→기숙사/학부만 정리한다. `@DirtiesContext(AFTER_CLASS)`도 적용했다.
운영 코드, 기존 테스트, 공통 환경, Gradle/E2E 설정과 의존성은 변경하지 않았다.

## 검증 결과

| 단계 | 실행 | 통과 | 실패/오류 | 스킵 |
| --- | ---: | ---: | ---: | ---: |
| 신규·수정 클래스 개별 검증 (최종 시나리오 기준) | 41 | 41 | 0 | 0 |
| 담당 4개 도메인 전체 (24개 클래스) | 650 | 650 | 0 | 0 |
| 전체 `integrationTest` (32개 클래스) | 732 | 732 | 0 | 0 |

신규 4개 클래스를 `integrationTest --tests <각 클래스의 전체 이름>`으로 선택해 40개를 검증한 뒤,
추가 변경한 `AdminModerationApiIntegrationTest` 11개를 개별 재실행했다. 위 개별 집계는
재실행 중복을 제외한 최종 41개다. 최종 도메인·전체 실행에서도 이 41개가 모두 통과했다.

최종 도메인 명령은 다음과 같다. 이후 필터 없이 `.\gradlew.bat integrationTest`를 실행했다.

```powershell
.\gradlew.bat integrationTest --tests '*community.*IntegrationTest' --tests '*chat.*IntegrationTest' --tests '*report.*IntegrationTest' --tests '*admin.*IntegrationTest'
```

`node tools/sync-contract-docs.cjs --check`도 통과했다(73 endpoints / 75 record DTOs / 30 entities).
운영 코드·공통 환경 변경이 없어 중복되는 전체 `test`/`bootJar`는 실행하지 않았다.
보강 과정에서 JSONPath wildcard 배열 단언 1건이 실패했으며, 정상 JSON 응답과 DB를 확인한 뒤
각 배열 항목의 `read=false`를 명시적으로 검증하도록 수정해 재통과했다. 검증 조건은 유지했다.

## 결함·미검증·기존 테스트 품질

- 현재 추가 시나리오에서 운영 코드의 기능 결함은 발견하지 않았다.
- H2 검증이다. 실제 MySQL 잠금·DDL·실행계획, 실제 STOMP 소켓/브로커 전달 및 배포 검증은 포함하지 않는다.
  동기 테스트의 flush/재조회는 커밋 성공 검증과 구분하며, 채팅 테스트는 실제 커밋 후 별도 트랜잭션에서 검증한다.
- 기존 `ReportRegressionIntegrationTest`, `ChatMessageCursorIntegrationTest`는 독립 커밋 fixture를
  메서드별 `@AfterEach`에서 정리하지 않는다. 전자는 클래스 종료 시 컨텍스트를 폐기하지만
  메서드 간 정리를 대체하지 않는다. 신규 테스트는 이 패턴을 복제하지 않았으며 기존 파일은 수정하지 않았다.
- 확정되지 않은 신고 대상 활성 상태/자기 신고 등의 정책은 새 기대값으로 고정하거나 변경하지 않았다.
