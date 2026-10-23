# 6·7단계 Community·Chat 결함 수정 및 회귀 검증

검증일: 2026-10-23. 이전 실서버 근거는
[P0 검증 보고서](CCRA_STAGE6_7_P0_E2E_VALIDATION.md)와
[P0 근거 JSON](validation/ccra-stage6-7-p0-20261023.json)을 참조했다.
이번 수정의 테스트별 결과와 수정 파일 SHA-256은
[수정 검증 JSON](validation/ccra-stage6-7-fix-20261023.json)에 기록했다.

두 결함의 구현 수정과 아래 회귀 검증은 완료했다. 실제 서버·DB E2E는 실행하지 않았다.
기존 P0의 6 PASS / 2 FAIL 기록은 과거 실행 결과로 유지하며 이번 단위 테스트로 덮어쓰지 않는다.

## 원인과 수정

### 댓글·답글 중복 등록 — 이전 보고서 D2

재현: 댓글 POST 200/DB 저장 후 상세 GET만 503으로 대체하면 초안이 남고 재등록 시 별도 댓글이 저장됐다.
기존 `addComment`가 POST와 `onRefresh()`를 같은 실패 경로에 묶어 GET 실패도 false로 반환했기 때문이다.
답글도 같은 경로를 사용했다.

`CommunityPostPage`에서 댓글 변경 후 조회를 별도로 처리한다. POST가 성공하면 GET 실패와 관계없이
작성 결과는 성공으로 종료되어 댓글 초안/익명 선택이 초기화되고 답글 작성기가 닫힌다.
조회 실패는 “댓글 변경은 저장되었지만 목록을 새로고침하지 못했어요”로 안내한다.
“댓글 목록 새로고침”은 GET만 재시도하며 POST를 다시 호출하지 않는다.
POST 자체 실패는 기존 오류 안내와 초안/익명 선택을 유지하며 GET을 호출하지 않는다.
같은 원칙을 해당 화면의 댓글 수정·삭제 후 조회에도 적용했다.
게시글·이미지 API, 댓글 API 및 익명성 계약은 변경하지 않았다.

### 채팅 저장 확인 전 초안 유실 — 이전 보고서 D1

재현: 실제 STOMP 연결을 유지한 채 SEND만 차단하면 기존 화면은 publish 직후 입력을 비웠지만 DB에는 저장되지 않았다.
`useChat.send`의 true가 동기 transport 호출 성공을 의미했는데 `ChatPage`가 이를 저장 성공으로 해석했다.
기존 요청/응답/DB에 전송 요청을 식별하는 값도 없어 본문 비교만으로는 커밋 후 응답 유실과 미저장을 구분할 수 없었다.

선택적 소문자 UUID v4 `clientMessageId`를 전송 본문, DB, STOMP/REST 메시지 응답에 추가했다.
서버는 기존 USER → membership 잠금 순서를 재사용하고 키를 current read로 조회한다.
같은 방·인증 발신자·전송 ID가 있으면 기존 메시지를 반환하며 다시 INSERT하지 않는다.
같은 ID에 다른 본문은 INVALID_REQUEST로 거절한다. 서로 다른 ID는 본문이 같아도 별개 메시지다.
DB UNIQUE는 `(chat_room_id,sender_id,client_message_id)`이며 기존 NULL 키는 유지한다.
재시도도 현재 활성 계정·참여 권한을 검사한다. 공통 인증 코드는 변경하지 않았다.

컨트롤러의 기존 구조는 유지했다. 트랜잭션 서비스가 정상 반환된 뒤 방송하므로 새 프론트는
자신의 같은 방·같은 전송 ID·저장 메시지 ID를 가진 STOMP 메시지 또는 REST 이력 항목을 확인한 뒤 입력을 비운다.
publish 반환, transport receipt, 같은 본문/시간만으로 성공을 추측하지 않는다.

대기 내용과 ID는 전송 전에 계정별 sessionStorage에 기록한다. 확인 전에는 입력을 읽기 전용으로 유지하고
일반 전송/연속 Enter를 차단한다. 저장소 기록에 실패하면 publish하지 않는다.
10초 확인 지연이나 연결 종료는 실패 확정과 구분하여 결과 미확정으로 표시한다.
전송 예외·응답 유실 시에도 내용을 보관하고 명시적 재시도에 같은 ID/본문을 사용한다.
새로고침·화면 재진입 후에도 해당 탭의 대기 내용을 복원한다. 자동 재전송은 하지 않는다.
REST polling·재연결·이력 병합은 기존 경로를 재사용한다.

## 회귀 검증 결과

모든 최종 실행 결과는 PASS이며 FAIL은 0개다. 중간에 보완한 테스트 fixture/테스트 환경 문제는 서비스 결함으로 집계하지 않는다.

| 검증 | 결과 | 근거/범위 |
| --- | --- | --- |
| 관련 프론트 테스트 14 suites | PASS, 154 tests | Community·Chat 화면/익명 댓글/CRUD/API/STOMP wrapper/이력/읽음/polling/재연결/방 목록 |
| ChatServiceTest | PASS, 121 tests | Mockito; 기존 동작과 저장 실패/동일 ID 재시도/본문 충돌/권한/별개 전송 |
| ChatMessageSendRequestTest | PASS, 31 tests | Validator; UUID 선택성·형식 및 기존 공백/2,000 코드 포인트 경계 |
| ChatWebSocketControllerTest | PASS, 6 tests | Mockito; 서비스 성공 후 방송, 저장 실패·권한 오류 시 방송 없음 |
| ChatMessageTest | PASS, 6 tests | 기존 Entity 단위 검증 |
| `npm run build` | PASS | Compiled successfully |
| 선택 Chat 단위 테스트와 `bootJar` | PASS | Gradle BUILD SUCCESSFUL; 백엔드 합계 164 tests, failures/errors 0 |
| 계약 스냅샷 검사 | PASS | 73 endpoints / 75 record DTOs / 30 entities |
| `git diff --check` | PASS | 공백 오류 없음 |

추가한 회귀 사례는 프론트 17개, 백엔드 11개다.

- `CommunityPostPage.recovery.test.js`: 일반/익명 댓글·답글의 POST 성공/GET 실패 4개,
  POST 실패 시 초안/익명 유지 1개, 익명 댓글 수정·삭제 성공/GET 실패 2개.
- `useChatSend.test.js`: publish만 성공한 대기 유지/연속 전송 차단, 확인 지연 반복 재시도의 동일 키,
  본인·방·키가 일치하는 저장 메시지만 확인, 전송 예외/연결 종료/재마운트 복구,
  계정 격리, 저장소 기록 실패 시 publish 차단 6개.
- `ChatPage.test.js`: SEND 유실 시 실제 입력 유지/정확한 STOMP 확인, 방송 유실 후 REST 복구,
  재마운트 후 같은 ID 재시도 3개. 기존 길이·IME 테스트도 실제 저장 확인을 모사하도록 유지했다.
- `chatSocket.test.js`: 실제 publish body의 전송 ID 전달 1개.
- `ChatServiceTest`: 저장 후 확인 유실 재시도, 같은 키/다른 본문 거절, 현재 참여 권한 확인,
  저장 오류 후 재시도, 같은 본문/다른 키의 의도적 별도 전송 5개.
- `ChatMessageSendRequestTest`: 선택적 ID 2개, 잘못된 ID 4개.

프론트 실행:

```text
npm test -- --watchAll=false --runInBand --testPathPattern="pages/community|pages/chat|api/chatSocket.test|api/chatApi.test|api/communityApi.test" --json --outputFile=ccra-fix-test-results.json
npm run build
```

백엔드 실행:

```text
gradlew.bat test --tests com.kkirikkiri.chat.service.ChatServiceTest --tests com.kkirikkiri.chat.controller.ChatWebSocketControllerTest --tests com.kkirikkiri.chat.dto.ChatMessageSendRequestTest --tests com.kkirikkiri.chat.entity.ChatMessageTest bootJar
node backend/tools/sync-contract-docs.cjs --check
```

Gradle은 전역 캐시 잠금 접근에 대한 실행 권한을 받아 수행했다. 지정한 테스트는 Spring 컨텍스트나
DB 연결을 생성하지 않는 Mockito/Validator/Entity 단위 테스트다. 전체 인증·Calendar 테스트는 최종 실행 대상에 포함하지 않았다.
초기 프론트 실행의 필터 옵션을 수정해 위 명령으로 범위를 확정했으며 중단한 실행은 결과 합계에 포함하지 않았다.
초기 회귀 fixture의 필수 조회수 누락과 구형 JSDOM의 Web Crypto 부재는 fixture 및 테스트 전용 Web Crypto 주입으로 보완했다.
Node deprecation, Java unchecked/기존 deprecation 경고는 있으나 빌드 실패는 없다.

로컬 상세 근거: `frontend/ccra-fix-tests.log`, `frontend/ccra-fix-build.log`,
`backend/build/ccra-fix-unit.log`, `backend/build/ccra-fix-frontend-results.json`,
`backend/build/test-results/test/TEST-com.kkirikkiri.chat.*.xml`.
프론트 JSON 원본은 실행 후 `backend/build/ccra-fix-frontend-results.json`으로 이동해 보관했다.

## 생성·수정 파일

서비스/테스트/계약/마이그레이션 22개와 이 보고서·요약 JSON 2개다.

- 프론트 수정: `src/pages/community/CommunityPostPage.js`, `src/api/chatSocket.js`, `src/api/chatSocket.test.js`,
  `src/pages/chat/ChatPage.js`, `ChatPage.test.js`, `useChat.js`, `useChat.test.js`.
- 프론트 생성: `src/pages/community/CommunityPostPage.recovery.test.js`, `src/pages/chat/useChatSend.js`, `useChatSend.test.js`.
- 백엔드 수정: `chat/dto/ChatMessageSendRequest.java`, `ChatMessageBroadcastResponse.java`, `ChatMessageResponse.java`,
  `chat/entity/ChatMessage.java`, `chat/repository/ChatMessageRepository.java`, `chat/service/ChatService.java`,
  테스트의 `chat/service/ChatServiceTest.java`, `chat/dto/ChatMessageSendRequestTest.java`.
- 문서 수정: `backend/API.md`, `backend/ERD.md`의 Chat 계약 부분.
- 생성: `backend/migrations/20261023_chat_send_idempotency.sql`, `.md`,
  `backend/CCRA_STAGE6_7_DEFECT_FIX_VALIDATION.md`, `backend/validation/ccra-stage6-7-fix-20261023.json`.

기존 작업의 공유 E2E 실행기 4개 파일과 이전 검증 문서/도구/원본 근거는 이번 수정에서 변경하지 않았다.
다른 팀의 운영 코드·공통 인증·캘린더 코드도 변경하지 않았다.

## 호환성·미검증·자원 상태

- 기존 `{text}` 요청과 기존 null 전송 ID 행을 지원한다. 기존 REST 경로·STOMP 목적지·본문 길이·익명성·읽음 의미는 유지한다.
  전송 ID를 생략하는 구 클라이언트는 새 중복 방지 혜택을 받지 않는다.
- 새 프론트는 새 백엔드와 함께 배포해야 한다. 구 서버는 전송 ID를 보장하지 못하므로 혼재 조합에서 재시도의 안전성을 주장하지 않는다.
- **수동 마이그레이션은 필수 배포 선행 조건이며 아직 실행하지 않았다.**
  SQL → 백엔드 → 프론트 순서를 따른다. [마이그레이션 안내](migrations/20261023_chat_send_idempotency.md) 참조.
- 대기 보관 범위는 해당 브라우저 탭의 sessionStorage다. 탭 종료·사용자의 저장소 삭제 이후 보존까지 보장하지 않는다.
- 기존 polling/재연결/이력 기능은 관련 프론트 회귀 테스트로 확인했다. 이번 수정 후의 실제 HTTP·STOMP·DB 검증은 하지 않았다.
  실제 MySQL의 물리 DDL/UNIQUE/잠금·동시 동일 키, 커밋 후 방송 유실/프로세스 재시작·동일 ID 재시도는 별도 승인된 새 격리 자원이 필요하다.
- 기존 보존 DB·Docker·업로드·테스트 데이터에 접근하거나 변경하지 않았다. 새 DB/Docker 생성·삭제, SQL 적용,
  Spring Boot/React 서버 기동 모두 0회다. 일반 테스트·컴파일·빌드 산출물만 생성했다. P1은 실행하지 않았다.
- 다른 기능에 대한 수정은 추가하지 않았다. 이번 두 결함의 범위 안에서 미해결로 남겨둔 구현 항목은 없으나,
  실서버 E2E와 DB 마이그레이션 적용 확인은 완료로 간주하지 않는다.
