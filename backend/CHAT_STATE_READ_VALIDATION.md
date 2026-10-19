# 채팅 참여 상태·읽음 동시성 수정 검증

검증일: 2026-10-19 (Asia/Seoul). 범위는 기존 점검의 문제 1과 3이다.
운영/개발 DB 접속·변경, Docker/인프라 변경, Git commit/push/merge는 수행하지 않았다.

## 1. 작업 결과

완료(코드 수정·H2 회귀 검증 기준). 문제 1·3을 수정했고 최종 전체 테스트·빌드가 통과했다.
실제 MySQL·두 JVM·수동 전환 검증은 미실행이므로 운영 배포 승인을 뜻하지 않는다.
문제 2·4·5·6·7·8·9의 별도 수정은 포함하지 않는다.

## 2. 확인한 원인

- 읽음·설정·나가기가 같은 참여자에 대해 공통 잠금 없이 오래된 managed 상태를 변경하여
  전체 컬럼 UPDATE가 is_left/left_at/last_read_at/설정을 덮어썼다.
- 읽음 판정은 sent_at과 참여자의 last_read_at 비교였다. 생성 시각과 커밋·전달 순서가 달라지거나
  동일 DATETIME 값이 저장되면 수신하지 않은 메시지까지 읽음으로 집계됐다.
- 종전 프론트는 이력 응답 전에 본문 없는 /read를 호출했다. 서버는 클라이언트가 실제 받은 메시지를 구분할 수 없었다.

## 3. 생성·수정 파일

경로의 기준은 저장소 루트다. 기존 UI/CSS, 다른 도메인의 제품 코드는 변경하지 않았다.

| 파일 | 변경 |
|---|---|
| backend/src/main/java/com/kkirikkiri/chat/service/ChatService.java | 참여자 공통 잠금, 정확한 메시지 ID 확인, 미읽음 집계/응답의 receipt 사용 |
| backend/src/main/java/com/kkirikkiri/chat/entity/ChatMessage.java | is_read, 단조 true 전환, 읽음 컬럼만 갱신하는 DynamicUpdate |
| backend/src/main/java/com/kkirikkiri/chat/repository/ChatMemberLockRepository.java | 참여자 잠금/refresh 계약 추가 |
| backend/src/main/java/com/kkirikkiri/chat/repository/ChatMemberLockRepositoryImpl.java | 오래된 영속 상태를 잠금 획득과 함께 갱신, 재진입 시 보류 변경 보존 |
| backend/src/main/java/com/kkirikkiri/chat/repository/ChatRoomMemberRepository.java | 현재 pin 목록 잠금 조회, DB 조건부 읽음 시각 UPDATE |
| backend/src/main/java/com/kkirikkiri/chat/repository/ChatMessageRepository.java | 정확한 수신 메시지 ID 잠금 조회와 is_read 미읽음 집계 |
| backend/src/main/java/com/kkirikkiri/chat/dto/ChatReadRequest.java | 실제 수신한 1~100개 양수 messageIds 본문 |
| backend/src/main/java/com/kkirikkiri/chat/controller/ChatController.java | 기존 /read 경로에 검증된 본문 전달 |
| backend/src/test/java/com/kkirikkiri/chat/service/ChatReadConcurrencyIntegrationTest.java | latch/독립 JPA 트랜잭션 기반 회귀, DB 상태·읽음·롤백 검증 |
| backend/src/test/java/com/kkirikkiri/chat/service/SharedAccountConcurrencyIntegrationTest.java | pin 개수 조회 뒤 오래된 다른 참여자 설정 덮어쓰기 회귀 추가 |
| backend/src/test/java/com/kkirikkiri/chat/service/ChatServiceTest.java | 기존 권한/방/상한 검증을 유지하며 메시지별 읽음 계약에 맞게 수정 |
| backend/src/test/java/com/kkirikkiri/chat/controller/ChatReadControllerTest.java | JWT/Principal, body/container 검증, 기존 envelope/403/401 검증 |
| frontend/src/api/chatApi.js | 정확한 messageIds 본문 전송 |
| frontend/src/pages/chat/useChat.js | 표시된 상대 메시지만 확인, 늦은 응답/신규 수신 구분, 성공 후 현재 방 미읽음 재조회 |
| frontend/src/api/chatApi.test.js | 실제 요청 형식·인증·응답 해제 검증 추가 |
| frontend/src/pages/chat/useChat.test.js | 수신/조회 경계·실패·동시 도착·미확인 메시지 보존 검증 |
| frontend/src/pages/chat/ChatPage.test.js | 새 본문 검증, 읽음 재조회와 프로필 열기/닫기의 요청을 구분하여 기존 화면 상태 검증 유지 |
| backend/API.md, backend/ERD.md, backend/DEPLOYMENT.md | 이번 읽음 모델·호환성·배포 조건 갱신 |
| backend/migrations/README.md | 수동 전환 안내 연결 |
| backend/migrations/20261019_chat_message_receipts.sql | 컬럼 추가 및 종전 표시 결과 보존용 수동 SQL |
| backend/migrations/20261019_chat_message_receipts.md | 선택 근거, 사전 점검·적용·롤백 한계·격리 MySQL 검증 계획 |
| backend/CHAT_STATE_READ_VALIDATION.md | 이번 수정·실행 증거·미검증 범위 |

## 4. 해결 방식과 한계

- 참여자 쓰기는 기존 USER 잠금 → 자신의 CHAT_ROOM_MEMBER 잠금/refresh → 메시지 ID 오름차순이다.
  나간 상태는 갱신 전에 확인하여 403으로 거절한다. pin 최대 2개는 USER 잠금과 current read로 유지한다.
  pin 개수 조회의 READ 잠금을 실제 참여자 refresh/WRITE 잠금으로 오인하지 않는다.
- 읽음 시각은 Java 단조 증가뿐 아니라 DB의 `last_read_at IS NULL OR last_read_at < :readAt` 조건으로도 역행을 거절한다.
  조건부 UPDATE 후 managed 상태를 동기화하여 같은 트랜잭션의 후속 설정/나가기도 이전 시각을 복원하지 않는다.
- is_read는 확인한 개별 메시지만 true로 전환한다. 최대 ID/시각 범위 추론이나 방 전체 송신 직렬화가 필요 없다.
  A 미커밋 중 B만 확인해도 A는 나중에 미읽음으로 커밋된다. 전달 순서 역전과 같은 시각도 다른 ID에 영향을 주지 않는다.
- 낙관적 잠금은 별도 참여자 version 컬럼과 재시도가 필요하고, 부분 UPDATE만으로는 오래된 권한/상한 검사를 보호하지 못한다.
  기존 계정 잠금과 current refresh를 재사용하고 읽음 시각에만 조건부 UPDATE를 추가했다.
- 사람의 실제 독서를 증명하는 기능은 아니다. 클라이언트는 자신이 조회·수신하여 표시한 메시지 ID만 보낸다.
  고빈도 읽음 확인의 잠금/현재 방 미읽음 재조회 비용은 실제 MySQL 부하에서 별도 측정해야 한다.

## 5. 테스트 결과

- 수정 전: 새 회귀 3건을 먼저 실행하여 모두 실패 확인. 오래된 읽음/설정 요청이 나가기 후 허용됐고 지연 커밋의 미읽음은 0이었다.
  이 실행의 XML은 로컬 build/tmp/chat-audit-before.xml에 보관했다(빌드 산출물, Git 추적 제외).
- 최종 전체 백엔드: `gradlew.bat test bootJar --console=plain` 성공(2026-10-19 21:04 KST, 5m 57s).
  1,178개, 실패/오류/skip 0. 최종 변경 후 test와 bootJar를 실제 재실행했고 캐시 통과로 대체하지 않았다.
  chat 패키지 168개, STOMP 인터셉터 36개, 커뮤니티 익명성 통합 24개가 전체 결과에 포함된다.
  새 JPA 회귀는 ChatReadConcurrencyIntegrationTest 12개와 SharedAccountConcurrencyIntegrationTest 추가 1개로 13개,
  새 읽음 API 검증은 ChatReadControllerTest 10개다.
- 프론트: chatApi/useChat/ChatPage 3개 suite, 49개 통과. npm run build 성공.
- 문서: sync-contract-docs.cjs --check 통과(71 endpoints, 72 record DTOs, 30 entities).
- 새 JPA 검증: 나가기/읽음·설정 경합, 오래된 managed 참여자, 역순 완료, 나간 사용자 거절,
  지연 커밋, 동일 시각·정렬·검색, 읽음 중 신규 메시지, 중복/자기/잘못된 ID, 원자적 rollback·재시도,
  DB 읽음 시각 조건과 pin 상한/상태 보존을 검증한다. latch를 사용하며 Thread.sleep에 의존하지 않는다.
- H2 RR의 선행 snapshot 뒤 삽입 행 비가시성은 명시적으로 거절·롤백·새 트랜잭션 재시도를 검증한다.
  MySQL locking/current read의 실제 동작을 이 결과로 대신하지 않는다.
- 실제 MySQL, 두 JVM, 수동 SQL 적용, 실제 프록시/브라우저 간 E2E는 이번에 실행하지 않았다.

## 6. 기존 기능 영향

- 성공/실패 envelope, HTTP 상태 정책과 목록·메시지·STOMP 응답 필드는 유지한다.
  읽음 요청 본문만 필수로 변경되며 본문 없는 구버전 요청은 400이다. 관련 프론트 호출부를 함께 수정했다.
- DB 변경은 CHAT_MESSAGE.is_read 한 컬럼이다. 기존 메시지/방/참여자 ID·본문·원시 시각은 수정하지 않는다.
  수동 전환 SQL은 종전 화면의 읽음 판정을 보존한다. 과거의 잘못된 읽음을 실제로 복원한 것은 아니다.
- ROOMMATE/COMMUNITY 구분·방 재사용·익명 출처/마스킹·나가기/재진입 정책·고정 2개·2,000 코드 포인트 제한을 유지한다.
- 읽음은 표시된 메시지 집합에만 적용하므로 아직 조회하지 않은 과거/늦은 메시지는 미읽음으로 남는다.
  기존 UI의 과거 50개 이전 이력 접근 문제는 이번에 변경하지 않았다.

## 7. 남은 검증

수동 마이그레이션 안내의 격리 MySQL/InnoDB RR·잠금 대기/timeout·두 JVM·DDL/전환·복구 검증이 배포 전에 필요하다.
구버전 쓰기/연결을 drain하고 새 서버와 관련 프론트를 함께 전환해야 한다. 혼재 배포·자동 마이그레이션은 허용하지 않는다.
메시지별 확인 집합은 timestamp 하나로 손실 없이 되돌릴 수 없으므로 새 쓰기 이후 구버전 복귀는 별도 검토가 필요하다.
Simple Broker의 JVM 간 전달과 기존 점검의 4~9번은 이번 해결 범위가 아니다.
과거에 이미 덮어써진 나가기 상태나 잘못된 읽음을 사실 확인 없이 자동 복구하지 않는다. 별도 감사 증거가 필요한 데이터 복구다.

## 8. 최종 판정

두 결함의 서버/클라이언트 수정과 H2 회귀 검증은 완료했다. 현재 테스트에서 추가 실패는 없다.
운영 배포 판정은 **보류**다. 실제 InnoDB/RR·두 JVM·DDL/데이터 전환·복구를 격리 환경에서 확인한 뒤,
구버전 drain과 새 프론트/서버 동시 전환 조건을 충족해야 한다. 이 검증 없이 실제 운영 안전성을 확정하지 않는다.
