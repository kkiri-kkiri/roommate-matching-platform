# 채팅 상태 동기화·메시지 페이지·Unicode 검증

검증일: 2026-10-19 (Asia/Seoul). 브랜치 `test/chat-validation`.
사용자 요청의 문제 6·7·8만 작업했다. 이전 문제 1·3·4·5의 미커밋 변경을 보존했다.
운영/공유 DB 접속·변경, Docker 변경, 마이그레이션 실행, Git commit/push/merge는 하지 않았다.

## 1. 작업 결과

완료(요청한 코드 수정·H2/프론트 자동 검증 기준). 문제 6은 제한적인 polling,
문제 7은 선택적 키셋 커서와 추가 조회 UI, 문제 8은 프론트 코드 포인트 검증으로 구현했다.
백엔드 1,220개, 프론트 232개와 양쪽 빌드가 통과했다. 실행 조건·실패 이력은 5장에 기록한다.
MySQL·브라우저 간 E2E·다중 JVM 검증은 이번에 실행하지 않았다.

## 2. 기존 문제 원인

- 선택 방만 STOMP로 구독하고 다른 방/새 방/상대방 읽음 상태를 다시 읽는 주기가 없었다.
  재연결은 선택 방 첫 이력만 요청했고 읽음 상태를 확정하는 별도 조회가 없었다.
- 메시지와 검색 결과는 page=0/size=50에 고정되어 다음 페이지가 화면에 연결되지 않았다.
  새 메시지 삽입 중 offset 증가와 중복 제거만으로는 안전한 경계를 확보할 수 없었다.
- textarea maxLength와 text.length는 UTF-16 코드 유닛을 세어 서버의 2,000 코드 포인트와 달랐다.
  기존 표시 메시지 전체의 자동 확인도 과거 페이지 추가 시 보이지 않는 메시지를 확인할 위험이 있었다.

## 3. 생성·수정 파일

M은 backend/src/main/java/com/kkirikkiri/, T는 backend/src/test/java/com/kkirikkiri/ 기준이다.
이 표는 이번 작업 파일이며 이전 미커밋 변경 전체 목록을 뜻하지 않는다.

| 파일 | 변경 내용 |
|---|---|
| M/chat/dto/ChatMessageListRequest.java | 선택적 beforeId/afterId, 양수·상호 배타·page=0 검증; 기존 3인자 생성자 유지 |
| M/chat/dto/ChatMessageReadStatusResponse.java (생성) | ID/read만 반환하는 receipt 조회 DTO |
| M/chat/repository/ChatMessageRepository.java | 같은 방 커서 기준 조회, 앞/뒤 키셋 조회, scalar receipt 조회 |
| M/chat/service/ChatService.java | 기존 offset 보존, 커서/방 권한 검사, 읽음 상태 읽기 전용 조회 |
| M/chat/controller/ChatController.java | read-status POST 조회 추가, 기존 Principal 사용 |
| T/chat/controller/ChatQueryControllerTest.java | 커서 검증 및 receipt 인증/본문 상한/최소 응답 |
| T/chat/dto/ChatMessageSendRequestTest.java | 복합 이모지/결합 문자 코드 포인트 경계 추가 |
| T/chat/service/ChatMessageCursorIntegrationTest.java (생성) | 151개 이력/180개 검색, 삽입 경합, 76개 전방 복구, 권한·롤백 |
| frontend/src/api/chatApi.js | 선택적 커서/receipt 조회, 기존 오류 메시지 유지 및 HTTP 상태 보존 |
| frontend/src/api/chatApi.test.js | 기존 offset 요청과 선택적 커서/receipt 본문 계약 |
| frontend/src/api/chatSocket.js | 이전 구독 해제 콜백이 새 구독을 끊지 않게 보호 |
| frontend/src/api/chatSocket.test.js (생성) | 재연결 구독 복구, 오래된 콜백/cleanup 및 disconnect 검증 |
| frontend/src/pages/chat/chatMessages.js (생성) | 코드 포인트 검증, ID 병합/읽음 단조 증가, 나노초 문자열 정렬 |
| frontend/src/pages/chat/chatMessages.test.js (생성) | 일반/혼합/이모지/결합 문자 경계, 오래된 read=false 보호 |
| frontend/src/pages/chat/useChatMessages.js (생성) | 과거/전방 커서, 검색 페이지, 요청 경합/재시도, 보이는 자기 receipt 조회 |
| frontend/src/pages/chat/useChatMessages.test.js (생성) | 다음 페이지/중복/도착 순서/재시도/전환/receipt/나간 방 |
| frontend/src/pages/chat/useChatSync.js (생성) | 보이는 탭 15초 polling, 재연결/복귀/이벤트 coalescing, cleanup |
| frontend/src/pages/chat/useChatSync.test.js (생성) | 숨김/로그아웃, 중복·역순 알림, 단일 실행과 전방 추가 복구 |
| frontend/src/pages/chat/useChatRooms.js | bounded background 갱신, 첫 페이지 안정 시 추가 페이지 유지 |
| frontend/src/pages/chat/useChatRooms.test.js | prefix 유지/변경 재동기화, 새·미선택 방 preview/unread 갱신 |
| frontend/src/pages/chat/useChat.js | 새 훅 연결, 정확한 viewport ID 읽음, 늦은 unread 응답 보호, 전송 검증 |
| frontend/src/pages/chat/useChat.test.js | viewport 밖 이력 확인 방지, 역순 읽음 재조회 회귀 추가 |
| frontend/src/pages/chat/ChatPage.js | 이전 메시지/검색 버튼, viewport 확인, scroll anchor, Unicode/IME, 계정별 상태 초기화 |
| frontend/src/pages/chat/ChatPage.test.js | Unicode·붙여넣기·IME, scroll, 계정 교체, 모달/숨긴 탭 확인 방지 |
| backend/API.md | 실제 새 커서/receipt API, polling·읽음/Unicode 동작만 문서화 |
| backend/DEPLOYMENT.md | 이번 검증과 운영 추가 점검 연결 |
| backend/CHAT_SYNC_PAGINATION_UNICODE_VALIDATION.md (생성) | 이번 보고서 |

다른 도메인의 제품 코드, CSS, DB Entity/DDL/마이그레이션, application.properties,
서버 Simple Broker·STOMP 권한 검사 코드는 변경하지 않았다.

## 4. 해결 방법

### 실시간 상태와 재연결

기존 인증 REST를 재사용하는 polling을 선택했다. 사용자별 새 구독 경로·ID 라우팅·서버 이벤트를
추가하지 않아 익명 ID 노출과 새 구독 인가 경로가 없다. Simple Broker 교체나 다중 JVM 설계 변경도 없다.

- 보이는 탭에서 15초마다 목록 첫 페이지 50개, 선택 방의 이후 메시지 최대 50개,
  viewport 안의 미확인 자기 메시지 receipt 최대 100개를 요청한다. 선택 방이 없으면 방 목록만 조회한다.
- 재연결·window focus·visibility 복귀 및 선택 방 broadcast는 100ms로 합쳐 재조회한다.
  동기화는 한 번에 하나이며 도중 알림은 한 번의 후속 실행으로 합친다.
  이후 메시지가 50개를 넘으면 1초 뒤 다음 50개를 요청한다. 무제한 단일 응답/전수 방 조회가 아니다.
- 첫 페이지 ID 순서/전체 결과 수가 같으면 이미 추가로 불러온 목록 페이지를 유지한다.
  달라지면 첫 페이지로 안전하게 되돌리고 “더 보기”를 제공한다. 새/미선택 방의 preview/unread/order를
  서버 응답으로 교체하며 로컬 unread++는 하지 않는다. 같은 응답을 받아도 중복 증가하지 않는다.
- read-status는 기존 참여자/인증 검사와 messageIds 1~100개 검증을 재사용한다.
  한 ID라도 해당 방에 없으면 전체 거절하며 응답은 id/read뿐이다. 조회가 읽음 확인을 수행하지 않는다.
  확인은 기존 POST /read의 정확한 상대 ID로만 수행한다. read=true는 늦은 false 응답으로 되돌리지 않는다.
- 입력·설정·나가기 경합은 기존 참여자 쓰기 잠금을 유지한다. 늦은 목록/단건 unread 응답은
  요청 세대·로컬 revision으로 무시한다. 타 클라이언트에서 나간 선택 방은 403 재조회 시 캐시에서 제거한다.
- 계정 ID가 바뀌면 채팅 workspace를 새로 만들어 메시지/목록 캐시, 소켓, 타이머와 구독을 정리한다.
  이전 방/검색의 늦은 HTTP 결과와 오래된 소켓 콜백은 새 선택에 반영하지 않는다.

새 상태 이벤트를 발행하지 않으므로 rollback을 확정 상태 알림으로 전달하는 경로도 추가하지 않았다.
읽기 전용 REST 재조회가 업무 트랜잭션의 커밋 상태를 반영한다. H2에서 send/read rollback 후
조회에 변경이 남지 않는 것을 검증했다. 기존 메시지 broadcast는 서비스 트랜잭션 반환 뒤 Controller가 호출하는
경로를 유지했다. STOMP 목적지·payload·인증·현재 구독 전달 권한 검사와 기존 테스트는 그대로다.
다른 방/상대 receipt의 반영은 정상 네트워크에서 최대 약 15초 지연될 수 있다. 즉시 push 보장은 아니다.

### 메시지·검색 페이지

- 기존 GET /messages의 offset 호출·응답·기본 50/최대 100은 그대로다.
  선택적 beforeId/afterId만 추가했다. 커서는 같은 방의 저장된 sentAt과 messageId를 기준으로 한다.
- 초기/이전 조회는 DESC, beforeId는 해당 튜플보다 작은 항목만 조회한다.
  이후 누락 복구는 afterId보다 큰 항목을 ASC로 가져온다. 클라이언트는 오름차순으로 병합한다.
  새 메시지 삽입이 과거 커서 경계를 밀지 않는다. 동률 시각은 ID로 구분한다.
  JS Date가 잃는 밀리초 이하 정밀도도 LocalDateTime 문자열을 정규화해 비교한다.
- “이전 메시지 보기”/“이전 검색 결과 보기”와 실패 재시도를 제공한다.
  한 번에 한 과거 요청만 수행하고 성공한 경우에만 커서를 이동한다. last=true면 멈춘다.
  방/검색 변경은 커서·오류·검색 결과를 초기화하고 이전 응답을 무시한다.
- 서버 이력 기준으로만 전방 커서를 전진시킨다. 더 최신 WebSocket 메시지가 먼저 도착해도
  그 ID로 REST 커서를 건너뛰지 않는다. ID 중복 제거와 read 단조 증가를 함께 적용한다.
- 이전 조회 시작 시 보이는 메시지의 DOM ID/상대 위치를 기억하고 prepend 후 같은 위치로 보정한다.
  과거를 읽는 중 새 메시지가 와도 하단으로 강제 이동하지 않는다. 선택/검색 변경 시 anchor는 폐기한다.
- 실제 읽음 확인은 보이는 탭의 message scroller와 겹친 상대 ID만 보낸다.
  화면 밖 과거 이력·숨긴 탭·프로필/상세/나가기 모달 뒤의 메시지는 추가 조회만으로 확인하지 않는다.
  viewport는 화면 노출의 근사치이며 사람의 실제 독서를 증명하는 기능은 아니다.

키셋은 여러 HTTP 요청에 걸친 DB snapshot이 아니다. 이미 순회한 구간에 나중에 커밋된 행은
재진입/검색 재실행으로 해당 구간을 다시 조회해야 발견될 수 있다. 임의로 오래 지연된 커밋까지
정확한 snapshot/변경 로그 보장을 주장하지 않는다. 정확한 ID receipt는 이런 행을 자동으로 읽음 처리하지 않는다.
MySQL의 실제 지연 커밋/격리 수준·브라우저 간 순서 역전은 최종 통합 검증으로 남긴다.

### Unicode

Array.from(text).length로 코드 포인트를 센다. textarea maxLength를 제거하고 입력은 자르지 않는다.
2,001개 이상이면 counter/aria-invalid로 표시하고 버튼·Enter·useChat.send에서 전송을 막는다.
IME 조합 중 전송을 막고 붙여넣기/서로게이트/결합 문자를 보존한다. 공백 메시지는 기존대로 거절한다.
복합 이모지/결합 문자를 grapheme 단위로 재정의하지 않으며 서버 DTO/Entity의 2,000 코드 포인트 정책은 유지한다.

## 5. 테스트 결과

이전 턴의 통과 결과를 이번 소스 결과로 대체하지 않는다.

- 백엔드 관련 실행: `gradlew.bat test --tests 'com.kkirikkiri.chat.*' --tests com.kkirikkiri.global.config.ChatStompInterceptorTest --console=plain`
  성공(1m 51s). 새 서버 테스트와 기존 회귀를 실제로 실행했다.
- 백엔드 최종 전체: `gradlew.bat test bootJar --console=plain` 성공(2026-10-19 23:07 KST, 5m 50s).
  XML 합계 1,220개, 실패/오류/skip 0. chat 210개 및 STOMP 36개를 포함한다.
  ChatMessageCursorIntegrationTest 4개, ChatQueryControllerTest 19개, Unicode DTO 25개,
  기존 ChatReadConcurrencyIntegrationTest 12개/SharedAccountConcurrencyIntegrationTest 5개/
  ChatRoomReadPerformanceIntegrationTest 7개가 모두 통과했다. test와 bootJar를 실제 실행했다.
- 프론트 최종 소스의 `npm run build` 성공(Compiled successfully), ESLint 경고 없음.
  빌드 도구의 Node fs.F_OK deprecation 경고는 남는다.
- 프론트 최종 전체: `npm test -- --watchAll=false --maxWorkers=2 --json --outputFile=../backend/build/chat-live-frontend-workers.json`
  성공(2026-10-19 23:13 KST, 29.745s). 25 suites / 232개, 실패/skip 0.
  ChatPage 43, useChat 16, useChatMessages 5, useChatRooms 7, useChatSync 3,
  chatMessages 7, chatApi 4, chatSocket 2로 채팅 관련 87개를 포함한다.
- 실패 이력: 같은 최종 소스의 `--runInBand` 전체 실행 2회에서 채팅 87개는 통과했지만
  룸메이트 검색 3개가 5초 timeout으로 실패했다(229/232 통과). 해당 파일 단독 실행은 4/4 통과했다.
  코드·기본 5초 제한·assertion을 바꾸지 않고 worker 2개 전체 실행으로 위 성공을 확인했다.
  실패 원인은 확정하지 않았으며 이 실행들을 통과로 계산하지 않는다. 관련 JSON/로그는 로컬 build에 남겼다.
- `node backend/tools/sync-contract-docs.cjs --check` 성공: 73 endpoints / 75 record DTOs / 30 entities.
  `git diff --check` 성공. PowerShell stderr 리디렉션에 표시된 NativeCommandError 텍스트는
  컴파일/JVM 경고를 포함하며 프로세스의 실제 종료 코드와 테스트 XML/JSON으로 판정했다.

- 신규 서버 검증: 151건의 과거 순회 중 삽입, 76건의 전방 누락 50+26 복구,
  180건 중 120건 검색 페이지·리터럴 %, _, !, 동일 시각/ID 순서, 다른 방 커서/타인/나간 사용자,
  send/read rollback, 상태 조회의 최소 필드/인증/상한, 복합 Unicode 경계.
- 신규 프론트 검증: 숨김/복귀/재연결, 중복 알림 coalescing/단일 실행, 로그아웃 cleanup,
  새·미선택 방 갱신, 이전 구독의 늦은 콜백, 페이지 경합/재시도/선택·검색 변경,
  viewport receipt/역순 unread snapshot/숨김·모달 확인 방지, scroll anchor,
  2,000/2,001 일반·이모지, 혼합/가족 이모지/결합 문자, 붙여넣기/IME/공백.
- 기존 문제 1·3·4·5, STOMP 인증·구독·전달 권한, 커뮤니티 익명성 회귀를 전체 실행에 포함한다.

## 6. 기존 기능 영향

기존 offset 클라이언트는 그대로 사용할 수 있다. 메시지 응답 envelope와 항목 필드,
정확한 messageIds 읽음, 미읽음 is_read 집계, 참여자 쓰기 잠금, 기본 50/최대 100,
방 목록 content/메타데이터·DB 검색·정렬·페이지 및 더 보기/재시도를 유지했다.
beforeId/afterId 및 POST read-status만 추가했다. 커서 조회의 totalElements는 해당 커서 조건의 남은 수다.
서버를 먼저 배포한 뒤 새 프론트를 전환할 수 있다. 새 프론트의 cursor/read-status를 구버전 서버에 보내면 안 된다.
STOMP 계약과 DB 스키마는 변경하지 않았다. 이전 is_read 전환/조회 인덱스 수동 절차는 여전히 별도다.

ROOMMATE/COMMUNITY 분리·방 재사용·익명 게시글/댓글 마스킹·고정 2개·나간 방 제외·프로필/출처를 유지한다.
API 문서는 이번 실제 계약 변경만 반영했다. 문제 9의 전반적 문서 정리는 하지 않았다.

## 7. 남은 문제와 추가 검증

- 문제 2의 다중 JVM Simple Broker 전달 설계는 변경하지 않았다. REST 복구가 브로커의 인스턴스 간 push를 대신 보장하지 않는다.
- 문제 9의 오래된 문서/전체 계약 정리는 별도 작업이다. 본 작업 범위 밖 문구를 일괄 수정하지 않았다.
- 실제 MySQL: before/after 튜플 탐색·count 실행계획, 기존 인덱스 효과, timestamp 정밀도,
  대량 이력/검색/polling 부하와 p95/p99, InnoDB 지연 커밋·잠금/rollback·두 JVM.
- 실제 브라우저 두 사용자: 다른 방/새 방/읽음 갱신 지연, 재연결/백그라운드 제한/복귀,
  빠른 방 전환·로그아웃, 모바일 가시성/모달, 실제 레이아웃의 prepend 및 동시 append,
  IME·붙여넣기·복합 이모지. DOM mock 테스트는 브라우저 레이아웃/E2E를 대체하지 않는다.
- polling은 활성 채팅 화면당 추가 REST 부하가 생긴다. 현재 단일 응답·주기·동시 실행을 제한했지만
  운영 사용량에서 15초 주기와 batch 크기의 적정성은 미측정이다.
- 키셋 live 조회의 지연 커밋 한계는 4장과 API 문서를 따른다. 전체 snapshot/변경 로그 필요성은 별도 설계 대상이다.

## 8. 최종 판정

문제 6·7·8의 구현과 요청한 자동 검증은 완료했다. 최종 전체 백엔드/프론트 테스트·빌드가 통과했고
이전 문제 1·3·4·5 및 STOMP 권한 회귀를 보존했다. 현재 최종 실행에 실패한 assertion은 없다.
배포 판정은 **배포 보류**다. 실제 MySQL·브라우저 E2E·다중 JVM·운영 배포 성공은 미검증이며,
문제 2·9의 별도 작업과 7장의 운영 검증이 남는다. 15초 polling 지연/부하와 키셋 live 조회의
지연 커밋 한계도 운영 판단에 포함해야 한다. 실제 검증 없이 snapshot/즉시 push 보장을 선언하지 않는다.
