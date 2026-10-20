# 채팅 배포 구조·API 문서 점검

검증일: 2026-10-19 (Asia/Seoul), 브랜치 `test/chat-validation`.
배포 결정 갱신: 2026-10-20. 사용자가 팀원 확인 후 백엔드 단일 JVM 계획과 문제 2 위험 수용을 확정했다.
아래 5장의 실행 결과는 2026-10-19 기록이며 이번 문서 갱신에서 테스트/빌드를 재실행한 결과가 아니다.
범위는 요청의 문제 2(다중 JVM 전달)와 문제 9(채팅 문서)다.
이전 문제 1·3·4·5·6·7·8의 미커밋 변경을 보존했다. 이번에는 문서만 수정했다.
운영/공유 DB 접근·수정, Docker/인프라 변경, 마이그레이션 실행, Git commit/push/merge는 하지 않았다.

## 1. 작업 결과

- 문제 2: **단일 JVM 배포 조건에 따른 위험 수용**. 2026-10-20 사용자가 졸업작품의
  Spring Boot 백엔드 인스턴스 1개 계획을 확정하고 위험 수용을 요청했다. Simple Broker를 유지하며
  Redis·RabbitMQ 등 외부 메시지 브로커는 도입하지 않는다. JVM 간 중계 기능은 미구현으로 남는다.
  실제 단일 JVM WebSocket 메시지 전달은 미검증이며 위험 수용을 실검증 통과로 기록하지 않는다.
- 문제 9: 현재 채팅 Controller/Service/DTO·프론트에 맞춰 API.md와 중복된 채팅 지침을 정정했다.
  배포 계획은 단일 JVM으로 갱신하되 실제 인프라 설정/전달 검증은 남긴다. 문서 수정은 실행 계약 변경이나 운영 승인과 다르다.
- 이번 자동 검증 결과는 5장에 기록한다. 과거 검증 결과를 이번 실행 결과로 대체하지 않는다.

## 2. 배포 구조

### 확인한 근거

| 항목 | 저장소에서 확인한 사실 | 확인하지 못한 사실 |
|---|---|---|
| 실행 버전 | build.gradle의 Java 21/Spring Boot 4.1.1. bootJar 내부 spring-core/messaging/websocket은 7.0.9 | 실제 운영 아티팩트/프로세스 버전 |
| 패키징 | Gradle bootJar로 실행 jar 생성. 사용자 확인 배포 계획은 백엔드 단일 JVM | 실제 프로세스 수·replica 설정·배포 명령·실행 플랫폼 |
| Docker/CI/CD | 숨김·ignore 대상도 포함한 파일명 점검에서 Dockerfile/Compose, Docker/CI/CD YAML, Jenkinsfile/Procfile, Kubernetes/Terraform/서비스·nginx 배포 설정을 찾지 못함 | 실제 컨테이너·서비스·LB 존재 여부. 파일 부재가 인프라 부재를 뜻하지 않음 |
| 문서/예제 | 루트 README는 프로젝트 소개, DEPLOYMENT.md는 운영 검증 지침. .env.example에는 DB/SMTP/JWT/AI 예제 키가 있고 broker/replica 설정은 없음 | 단일 서버 확약, 확장 규모·HA/SLA·무중단 배포 요구 |
| WebSocket | WebSocketConfig의 application prefix /pub, enableSimpleBroker("/sub"). 기본 endpoint /ws/chat. SockJS 없음 | 실제 WSS/TLS/프록시 Upgrade·timeout·LB 라우팅 |
| 인스턴스 간 전달 | 채팅 발행은 ChatWebSocketController의 SimpMessagingTemplate.convertAndSend. relay/Redis 수신/공유 fan-out 없음 | 향후 공유 브로커 선택·관리 주체 |
| 프론트 | chatSocket.js는 API base로 ws/wss URL 생성, 선택 방 1개 구독·재연결 복구. useChatSync는 보이는 탭 15초 REST polling | 운영 origin/base URL/백그라운드 타이머·브라우저 네트워크 동작 |
| 기존 두 JVM 도구 | tools/matching-mysql-multi-instance/RUNBOOK.md는 방의 두 연결을 같은 JVM에 두고 다른 JVM REST로 권한을 변경한다고 명시 | 서로 다른 JVM에 연결된 두 사용자의 채팅 중계 성공 증거는 아님 |
| 사용자 확인 | 2026-10-20 팀원 확인 후 Spring Boot 백엔드 1개로 배포, 문제 2 위험 수용 확정 | 실제 LB/전환/replica 설정, 단일 JVM 두 클라이언트 전달 결과 |

코드 근거: [WebSocketConfig](src/main/java/com/kkirikkiri/global/config/WebSocketConfig.java),
[ChatWebSocketController](src/main/java/com/kkirikkiri/chat/controller/ChatWebSocketController.java),
[chatSocket.js](../frontend/src/api/chatSocket.js), [useChatSync](../frontend/src/pages/chat/useChatSync.js).
프론트의 localhost 개발 기본 주소나 한 번의 jar 실행은 운영 JVM 수의 증거로 사용하지 않았다.
기존 [종합 보고서](STAGES_1_3_VALIDATION.md)의 과거 MySQL/두 JVM 실행은 이번에 재실행하지 않았다.

현재 구조는 같은 JVM에 연결된 참여자 간 전달 경로를 제공한다. 서로 다른 JVM은 각자의 구독을
메모리에 보유하므로 공유 DB/JWT 키만으로 다른 JVM의 구독자에게 전달하지 않는다.
Simple Broker의 메모리 구독 구조와 cluster 제한은 Spring의
[Simple Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-simple-broker.html),
[External Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html) 문서와도 대조했다.
15초 polling은 저장된 데이터를 재조회할 뿐 STOMP의 JVM 간 push를 구현하지 않는다.
사용자별 sticky session도 두 참여자가 서로 다른 서버로 고정되면 같은 문제를 남긴다.

### 확정된 단일 JVM 배포의 수용 조건

현재 사용 명령·목적지는 같은 JVM Simple Broker로 처리한다. 사용자 결정에 따라 아래 조건에서
문제 2를 위험 수용으로 처리한다. 실제 배포 설정과 전달 검증은 별도로 확인한다.

1. 모든 채팅 연결/SEND가 하나의 활성 채팅 JVM에 도착하도록 실제 replica·LB·라우팅 구성을 확인한다.
   한 물리 서버라도 JVM이 둘이면 이 조건을 만족하지 않는다. LB를 두는 경우에도 활성 대상은
   한 JVM이어야 하며 복수 JVM 분산/자동 확장을 단일 JVM으로 취급하지 않는다.
2. 재배포는 기존 JVM의 채팅 연결/처리를 종료한 뒤 새 JVM으로 전환한다. rolling/blue-green 중
   구·신 JVM이 동시에 활성 연결을 받지 않도록 한다. 연결 종료·재접속에 따른 일시 중단,
   단일 장애점·용량 제한·재시작 시 메모리 구독 소멸은 이번 단일 JVM 선택의 수용 범위다.
3. /ws/chat 경로, 정확한 CORS Origin, WSS/TLS, 프록시 Upgrade/Connection·idle timeout,
   재연결 및 REST 복구를 실제 프록시/브라우저에서 검증한다. endpoint 재정의 시 프론트 경로도 확인한다.
4. 실제 부하·장애·재시작 및 전달 검증은 남아 있다. 이번 사용자 결정으로 **위험 수용 상태는
   확정**했지만 기술적인 JVM 간 중계 구현이나 전체 운영 배포 승인을 뜻하지 않는다.

### 실제 단일 JVM WebSocket 전달 검증 상태

**미실시/미검증.** ChatWebSocketControllerTest는 mock SimpMessagingTemplate의 목적지/호출과
저장 실패 시 미발행을 검사한다. 기존 STOMP 인가/H2 테스트도 실제 WebSocket 두 클라이언트가
같은 Simple Broker를 통해 메시지를 받았다는 증거가 아니다. 이번에는 서버·클라이언트·DB를 실행하지 않았다.

배포 전 하나의 JVM에 실제 두 참여자 클라이언트를 연결하고 같은 방을 SUBSCRIBE한 뒤
A→B/B→A SEND·수신을 확인한다. 다른 방 격리·익명 senderId/프로필 마스킹,
나가기/계정 정지/토큰 만료 후 기존 구독 차단, 서버 재시작→재연결/재구독·REST 복구도 확인한다.
운영 프록시/WSS/CORS·idle timeout을 포함한 결과를 별도로 기록하며 polling 성공을 push 성공으로 세지 않는다.

### 다중 JVM이 필요해질 때의 후보 비교 — 미선정·미구현

아래는 향후 단일 JVM 조건을 벗어날 때의 별도 설계 후보다. 현재는 외부 브로커를 도입하지 않는다.

| 후보 | 범위/장점 | 추가 책임·제한 |
|---|---|---|
| 공유 STOMP broker + Spring relay | 외부 STOMP broker가 각 JVM의 relay 연결을 통해 구독 전달. 기존 STOMP 앱 구조를 활용하는 우선 검토 후보 | 브로커 운영/HA/TLS/자격증명, TCP 연결 수, broker 장애/재접속, 목적지 매핑 검증 필요. relay만 켠다고 현재 /sub 계약과 자동 호환되는 것은 아님 |
| Redis Pub/Sub + 앱 fan-out | 각 JVM이 공유 이벤트를 받아 로컬 Simple Broker에 전파하도록 별도 구현 | 자체 fan-out/중복·순서·루프 방지, 장애 시 손실 복구가 앱 책임. 기존에 운영 중인 Redis가 있다는 증거도 없음 |
| 단일 채팅 JVM 유지 | 외부 브로커를 도입하지 않음 | 다중 JVM 전달을 해결하지 않음. 위 단일 JVM 조건·가용성/용량 제한 수용이 필요 |

relay의 구조는 [Spring External Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay.html),
system/client TCP 연결·broker 자격증명·heartbeat/availability 설정은
[Connecting to a Broker](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/handle-broker-relay-configure.html)를 참고했다.
broker 로그인과 앱 JWT/참여 인가는 별개로 설계해야 한다. 현재 프로젝트의 JWT 검사를 broker 로그인으로 대체하지 않는다.
예를 들어 [RabbitMQ STOMP 목적지 규칙](https://www.rabbitmq.com/docs/stomp)은 별도이므로
현재 /sub/chat/rooms/{roomId}를 보존할 매핑 또는 합의된 계약 전환이 필요하다.
현재 prefix를 문자열 하나만 바꾸는 구현을 제안하지 않는다.
Redis Pub/Sub은 재전송을 보장하지 않는 at-most-once 특성이 있으므로
[Redis 공식 문서](https://redis.io/docs/latest/develop/pubsub/)에 맞춰 손실 복구를 별도로 검토해야 한다.

다중 JVM 요구가 확정될 때의 별도 구현/검증 계획:

1. 실제 JVM/LB/DB·최대 연결·전달 지연·메시지 손실 허용·HA/배포 중단 정책을 확정하고 후보를 선택한다.
2. Boot/BOM 호환 TCP/브로커 클라이언트 의존성, 주소/port/virtual host, system/client 계정,
   TLS·네트워크 ACL·접속 상한·heartbeat·availability/health·재시도 정책을 설계한다.
   브로커는 브라우저가 우회 접속하지 못하도록 보호하고 비밀 값은 배포 비밀 관리로 전달한다.
3. CONNECT/SEND/SUBSCRIBE의 JWT·ACTIVE/Principal 검증과 실제 outbound의 현재 참여 인가를 보존한다.
   목적지 매핑이 현재 interceptor 정규식과 beforeHandle 검증을 우회하거나 모두 차단하지 않는지 확인한다.
   외부 응답에는 기존 익명 senderId/counterpart 마스킹 DTO만 사용한다. 내부 이벤트도 최소 키로 설계한다.
4. 커밋 이후 발행, 롤백 시 미발행을 명시한다. 현재는 트랜잭션 서비스 반환 뒤 발행하지만
   DB 커밋과 전달은 원자적이지 않다. 커밋 직후 프로세스 장애까지 유실 없이 복구해야 한다면
   영속 outbox/재발행·중복 방지 요구와 수동 마이그레이션을 별도 설계한다. 이번에는 추가하지 않는다.
5. 격리 MySQL과 실제 두 JVM에서 A→B/B→A 전달, 중복/역순, commit/rollback·프로세스 종료,
   broker 단절/복구, 느린 수신자·연결 상한, 계정 정지/나가기/토큰 만료 후 기존 구독 차단,
   익명 게시글/댓글, 구·신 노드 전환·polling 복구를 검증한다. 브라우저/프록시 E2E를 별도 수행한다.
   이 검증의 통과 기준을 정하기 전에는 다중 JVM 배포를 승인하지 않는다.

## 3. 수정 파일

| 파일 | 이번 변경 |
|---|---|
| [API.md](API.md) | 채팅 10장, 관련 DTO 표/nullable·위험 설명의 오래된 #12/#13·Size·NotNull 정정. 현재 API·권한·잠금·polling·배포 한계 명시 |
| [DEPLOYMENT.md](DEPLOYMENT.md) | 단일 JVM 확정·문제 2 위험 수용, 배포/재시작 조건·실제 전달 미검증·향후 확장 개선 안내 |
| [agent.md](agent.md) | 채팅 #12/#13 지침·표의 중복된 과거 미해결 설명만 정정. 다른 도메인 지침은 유지 |
| [CHAT_DEPLOYMENT_API_VALIDATION.md](CHAT_DEPLOYMENT_API_VALIDATION.md) | 근거·향후 후보·위험 수용·실제 전달 미검증 및 최종 판정 |

2026-10-20 갱신은 위 배포 문서/API 10.8/agent 지침과 이 보고서, MySQL 통합/조회 후속 보고서의
배포 상태만 맞췄다. 코드·DB·설정·의존성·테스트 파일은 수정하지 않았고 기존 미커밋 변경은 보존했다.

코드/테스트/설정/의존성/인프라/API 계약/DB 스키마는 이번 작업에서 수정하지 않았다.
기존 작업 때문에 Git에 표시되는 그 파일들의 변경은 이전부터 있던 미커밋 변경이다.

## 4. API 문서 검증

| 항목 | 코드/호출 대조 및 문서 처리 |
|---|---|
| 연결 후 권한 | 과거 #12 미해결 문구 제거. CONNECT와 SEND/SUBSCRIBE 재인증, outbound 직전 검증, 장애 시 차단·세션 정리를 현재 구현으로 설명. 즉시 socket 강제 종료로 과장하지 않음 |
| 고정/잠금 | 과거 #13의 사용자 잠금 없음 문구 제거. USER WRITE → 대상 membership refresh/WRITE → 현재 pin 목록 READ와 최대 2개 검사 명시 |
| 전송 길이 DTO | @Size가 아니라 @NotBlank + @CodePointLength(max=2000)임을 수정. Size는 오류 메시지 키 재사용이라는 사실을 구분 |
| targetUserId | DTO @NotNull 없음으로 수정. ROOMMATE 양수/필수는 서비스, COMMUNITY는 출처로 결정·익명 전달 ID 무시. 오류 코드/출처 조건 대조 |
| 생성/단건/나가기 | HTTP 200/DTO·Void, Principal·현재 참여/나간 상태, 재입장 미허용, 삭제 범위와 잠금 설명 보완 |
| 방 목록 | content/page/size/totalElements/totalPages/first/last/counts, 50/100, DB 검색·정렬·escape·category·나간 방 제외·roomId 보조 정렬 대조 |
| 메시지 조회 | 기존 offset 및 beforeId/afterId 양수·상호 배타/page=0, DESC/ASC, 남은 totalElements, cursor 기준 방 검사와 live 조회 한계 대조 |
| 읽음/상태 조회 | 정확한 messageIds 1~100개, 상대 메시지 확인과 read-status 읽기 전용의 차이, is_read 집계·last_read_at 단조 증가·쓰기 잠금 대조 |
| Unicode/프론트 | Array.from·초과 입력 유지/전송 차단, IME, viewport/모달/숨긴 탭 확인 방지 설명 대조 |
| STOMP | /ws/chat 및 origin 설정, /pub SEND·/sub SUBSCRIBE, senderId 마스킹, HTTP와 다른 오류 전달, 커밋/전달 비원자성 대조 |
| 익명성/방 분리 | 카테고리별 키, 익명 POST/COMMENT 키 고정, 공개 counterpart 유지, 익명 senderId와 프로필 마스킹, 출처 갱신 범위 대조 |
| polling | 보이는 탭 15초, focus/visibility/재연결·broadcast 합치기, 50개 전방 묶음 복구·100개 visible receipt, 목록 prefix 유지/안전 초기화 대조. 15초는 최대 전달 지연 보장이 아니라 요청 주기임을 정정 |
| 배포 한계 | 사용자 확정 단일 JVM 계획·위험 수용과 실제 전달 미검증 구분. JVM 내부 broker/중계 없음·polling 역할·향후 확장 조건 명시 |

주요 대조 파일은 ChatController/ChatWebSocketController, ChatService, ChatRoom/ChatRoomMember/ChatMessage,
ChatRoomRepository/ChatMessageRepository/ChatMemberLockRepositoryImpl/ChatRoomMemberRepository,
채팅 DTO 전체, WebSocketConfig/ChatStompInterceptor/JwtAuthenticationProvider/SecurityConfig,
chatApi/chatSocket/useChat/useChatRooms/useChatMessages/useChatSync/ChatPage/chatMessages다.
Controller/DTO 선언 검사는 별도로 실행했다. 검사 도구가 서비스 의미나 모든 annotation을 확인하는 것은 아니므로
CodePointLength와 조건부 targetUserId 등은 위 파일을 직접 대조했다.
현재 요청의 채팅 범위에서 남아 있는 확인된 문서/코드 불일치는 없다. 운영 미확정·기능 한계는
문서 오류와 구분해 명시했다. 다른 도메인의 문서 전체를 검증·정정한 것은 아니다.

## 5. 테스트 결과 (2026-10-19 실행 기록)

이번 작업에서 테스트를 추가·삭제·비활성화하거나 제한 시간을 바꾸지 않았다.

| 검증 | 이번 실제 실행 결과 |
|---|---|
| 백엔드 전체 | `gradlew.bat test bootJar --rerun-tasks --console=plain` 성공, 6m 18s. 7 tasks 모두 실행 |
| 백엔드 XML | 1,220개, 실패/오류/skip 0. chat 패키지 210개 및 STOMP 권한 36개 포함 |
| 주요 회귀 | ChatReadConcurrency 12, SharedAccountConcurrency 5, ChatRoomReadPerformance 7, ChatMessageCursor 4, ChatQueryController 19 모두 통과 |
| 프론트 전체 | `npm test -- --watchAll=false --maxWorkers=2 --json --outputFile=../backend/build/chat-deployment-frontend.json` 성공. 25 suites/232개, 실패/skip 0, 36.947s |
| 채팅 프론트 회귀 | ChatPage/useChat/useChatMessages/useChatRooms/useChatSync/chatMessages/chatApi/chatSocket 합계 87개 통과 |
| 프론트 빌드 | `npm run build` 성공(Compiled successfully). ESLint 경고 없음. 도구의 Node fs.F_OK deprecation 경고는 남음 |
| 계약 검사 | `node backend/tools/sync-contract-docs.cjs --check` 성공: 73 endpoints/75 record DTOs/30 entities |
| 변경 형식 | `git diff --check` 성공. Git의 LF→CRLF 안내는 형식 오류와 구분 |

로그/JSON/XML은 로컬 backend/build/chat-deployment-* 및 build/test-results/test에 남겼다(빌드 산출물).
PowerShell의 stderr 리디렉션에 JVM/Node 경고가 NativeCommandError로 표시됐지만
최종 프로세스 종료 0과 Gradle 성공/테스트 XML·JSON을 확인했다. 과거 결과나 UP-TO-DATE를 통과 증거로 대체하지 않았다.

실행 조건: Gradle test의 test 프로필/H2 메모리 DB·초기화/시드/수집 비활성 강제를 유지했다.
`--rerun-tasks`로 이전 캐시 통과를 새 실행으로 세지 않았다. 프론트는 이전에 전체 성공한
`--maxWorkers=2`와 기본 5초 제한을 사용했다.

과거 타임아웃 3개의 정확한 이름:

- recommended filters use one server request regardless of the total population
- rapid filter changes cancel prior requests and ignore late responses
- empty recommendations stay empty without fetching all profiles

파일은 frontend/src/pages/roommate/RoommateSearchPage.test.js다. 이전 runInBand 전체 실행의
5초 timeout, 단독 4/4 통과, worker 2개 전체 성공 이력은
[이전 보고서](CHAT_SYNC_PAGINATION_UNICODE_VALIDATION.md)에 있다. 이를 이번에 재현했다고 부르지 않는다.
이번 worker 2개 전체 실행에서 해당 3개를 포함한 룸메이트 검색 4개가 모두 통과했고 timeout은 재현되지 않았다.
단일 worker 전체는 이번에 별도 실행하지 않았으므로 그 조건에서도 해결됐다고 보고하지 않는다.
이전 현상의 원인은 확정하지 않았고, 단순 CPU 부하나 채팅 결함으로 단정하지 않는다.

실제 MySQL·다중 JVM·브라우저 E2E·운영 배포는 요청대로 미실행이다.
한 JVM의 mock/H2 회귀가 실제 broker 네트워크 전달 성공이나 운영 승인을 뜻하지 않는다.
2026-10-20 배포 결정 문서 갱신에서도 실제 단일 JVM 전달·MySQL·브라우저 E2E는 실행하지 않았다.
문서 계약 검사와 변경 형식 검사 결과는 아래 최종 판정에 별도로 기록한다.

## 6. 기존 기능 영향

이번 변경은 문서뿐이다. HTTP/STOMP 계약, DTO·Service·Repository·Entity, DB 스키마/마이그레이션,
Docker·의존성·환경변수·CI/CD를 변경하지 않았다. 개별 ID 읽음·참여 상태 잠금·기본 50/최대 100,
DB 목록 페이지·검색/정렬·15초 polling·cursor 추가 조회·Unicode·익명성과 STOMP 권한 검사를 보존했다.
기존 동작을 문서에 맞추려고 되돌리거나 테스트 조건을 완화하지 않았다.

## 7. 남은 위험

- 단일 JVM: 사용자 결정으로 단일 장애점·용량/재시작 한계를 수용했다. 실제 양방향 WebSocket 전달,
  재접속·재구독·REST 복구·운영 프록시 검증은 남아 있다. DB 커밋/전달 사이 장애 유실도 남는다.
- 향후 다중 JVM: 구독 고립을 해소할 공유 중계·중복/순서·장애 복구 설계와 실제 두 JVM 검증이 필요하다.
  현재 위험 수용은 다중 JVM/자동 확장/구·신 JVM 동시 활성 배포에 적용하지 않는다.
- 실제 배포: JVM 수 계획은 확정했지만 실제 LB/replica·전환·Origin/TLS/경로·WebSocket timeout·용량은 미확인이다.
- MySQL: 실제 is_read/room_key 물리 DDL/기존 데이터 전환, USER→membership current read·잠금/교착·rollback,
  cursor/최신 메시지·미읽음·목록 집계 실행계획/인덱스와 대규모 polling 부하를 확인해야 한다.
- 브라우저 E2E: 두 참여자의 전송/읽음·다른 방·재연결/백그라운드 복귀, JWT 만료/정지/나가기,
  익명성·scroll·IME/Unicode 및 실제 프록시 경로를 확인해야 한다.
- 기존 수동 DB 절차: [is_read 전환](migrations/20261019_chat_message_receipts.md),
  [조회 인덱스 검토](migrations/20261019_chat_query_indexes.md), [기존 익명 키 안내](migrations/README.md)를
  실제 DB 적용 상태와 대조해야 한다. 이번 문서 작업의 신규 DDL은 없으며 어떤 SQL도 실행하지 않았다.
- 키셋은 여러 HTTP 요청의 snapshot이 아니며 이미 지난 구간의 늦은 커밋에는 재조회 한계가 남는다.
  15초 polling도 즉시 push·메시지 전달 확인·무손실 재전송을 보장하지 않는다.

## 8. 최종 판정

문제 2는 **단일 JVM 배포 조건에 따른 위험 수용**이다. 2026-10-20 사용자 확인을 근거로
졸업작품은 Spring Boot 백엔드 1개를 유지한다. Simple Broker 구조를 유지하고 외부 브로커는 도입하지 않는다.
JVM 간 중계는 미구현이며 확장 시 별도 개선해야 한다. **실제 단일 JVM WebSocket 전달은 미검증**이다.
문제 9는 **해결(요청한 채팅 문서 범위)**이다. 현재 코드/호출과 대조해 불일치를 정정했고 계약 검사도 통과했다.
전체 백엔드 1,220개/프론트 232개와 빌드가 이번 실행에서 통과했지만 운영 승인 근거로 단독 사용하지 않는다.
전체 채팅의 운영 배포는 **보류**다. 실제 단일 JVM WebSocket 전달/프록시·브라우저 E2E 및
조회의 잔여 정렬/반복 평가 검토가 남아 있다. 후속 [MySQL 최종 재검증](CHAT_MYSQL_FINAL_REVALIDATION.md)에서
인덱스 롤백/재적용·데이터 보존 통과와 조회 성능 개선을 확인했다. 다중 JVM 실검증은 현재 단일 JVM 배포의
선행 조건으로 요구하지 않으며 향후 확장 시 수행한다. MySQL 최신 상태는
[조회·인덱스 후속 보고서](CHAT_MYSQL_QUERY_INDEX_IMPROVEMENT.md)를 따른다.

2026-10-20 문서 갱신 검증: `node backend/tools/sync-contract-docs.cjs --check` 성공
(73 endpoints / 75 record DTOs / 30 entities), `git diff --check` 성공.
작업 시작/종료 시 기존 비문서 변경 파일 6개의 SHA-256이 동일함을 확인했다.
문서만 수정해 테스트/빌드는 재실행하지 않았으며 DB 접속·인프라 조작·Git commit/push/merge도 하지 않았다.
