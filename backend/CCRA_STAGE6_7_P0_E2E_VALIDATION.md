# 6·7단계 Community · Chat · Report · Admin P0 실제 통합/E2E 결과

2026-10-23, Asia/Seoul. 기준 커밋 `0b3247b`, 각 실행의 수정 포함 소스 지문은 실행별 증거에 보존했다.

**P0 고유 항목 8개 검증 완료: 6 PASS / 2 FAIL. 실제 서비스 결함 2건을 재현했으며 운영 코드는 수정하지 않았다. P1은 실행하지 않았다.**

## 1. 최종 P0 판정

| P0 | 결과 | 실제 검증 근거 |
| --- | --- | --- |
| ① 게시글·이미지 CRUD/공개 조회 | PASS | postId=12. 실제 multipart 등록·제목 수정·기존 이미지 유지·1장 제거/1장 추가. 무인증 바이너리 GET 200/image/png/68 bytes/no-store, 브라우저 decode 성공. 개별 제거는 연결·실물 파일 제거 및 GET 404. 게시글 소프트 삭제는 DELETED·이미지 연결/파일 보존·공개 GET 404, 일반 상세는 기존 400 계약. 타인 DELETE 403 |
| ② 일반·익명 댓글/답글·좋아요·내 활동 | **FAIL** | 정상 CRUD·익명 번호/마스킹/mine·타인 권한·좋아요/취소·내 활동 격리는 PASS. 후속 GET 실패 시 중복 작성 재현: postId=13, 첫 저장 commentId=13, 동일 초안 재등록 후 commentId=14 추가 |
| ③ 신고→관리자 처리→사용자 반영 | PASS | reportId=1~5. 실제 UI 접수/상세/반려/완료/게시글 삭제/댓글 삭제/회원 정지, 처리자/처리시각·DB·사용자 화면 확인. 비관리자 PATCH 403, 재처리 400/처리 행 불변. 반려는 REJECTED/NONE. 삭제 이미지 보존·공개 404 및 내 댓글 제거 확인 |
| ④ 채팅방 생성·목록·설정·나가기 | PASS | roomId=5/postId=14. 같은 출처 방 재사용·목록, 사용자 7의 pinned/muted=true와 상대 8의 false가 독립 보존. 나가기 후 is_left=true·목록 제외·REST 403, 기존 구독 메시지 비전달·금지 SEND 미저장. 비참여 관리자 이력 GET 403 |
| ⑤ 두 브라우저 실제 STOMP | PASS | roomId=2, messageId=3/4. 독립 A/B context의 실제 CONNECTED/SUBSCRIBE/SEND/MESSAGE, 양방향 같은 ID/방/본문을 UI와 MySQL 행에 대조. 이미 연결된 상태에서 Chat REST를 차단해도 전달되어 polling과 구분 |
| ⑥ 읽음·재연결·이력 복구 | **FAIL** | 정상 경로 PASS: roomId=3, messageId=5 읽음 false→true·unread 1→0·송신 UI 읽음 표시. 실제 WS 단절→새 연결→누락 이력/추가 송신 복구. WS만 지속 차단하고 REST를 유지해 15초 polling 복구(messageId=8)·DB 1행 확인. 별도 SEND 차단 시 미저장 초안 유실 재현 |
| ⑦ 관리자 권한·제재·직접 삭제 | PASS | 최종 roomId=7/postId=16/commentId=16. 일반 계정 관리자 API 403·UI 접근 제한, 관리자 정지 후 기존 JWT의 REST 401·기존 구독 비전달·SEND 미저장, 해제 후 로그인 복귀. 관리자 직접 댓글/게시글 DELETE 200·DELETED·사용자 화면 반영. 로그아웃 앱 소켓 종료와 별도 retained probe의 계속된 유효성을 분리 확인 |
| ⑧ 관리자 대시보드 일치 | PASS | UI/응답 DTO/별도 JDBC 대조. 최초 회원3/활성글8/활성댓글1/신고5/대기0 → 생성 후 글9/댓글2/신고6/대기1 → 반려 후 대기0 → 삭제 후 글8/댓글1. 정지는 전체 회원 수를 줄이지 않음. 최근 회원/게시글/신고 3개 정렬과 비어 있는 일정 집계 일치 |

⑤의 실제 STOMP 전달, ⑥의 REST polling 복구, 별도 JDBC의 DB 커밋은 독립적으로 확인했다.
두 브라우저는 Chromium의 서로 다른 BrowserContext/인증 세션이다. 여러 브라우저 엔진의 호환성 검증은 아니다.
각 항목은 최초 실행에서 ①→⑧ 순서로 수행했고, 하네스 오류가 있던 미완료 항목만 후속 실행했다.

## 2. 우선 문제 4개 판정

| 우선 문제 | 실제 관찰 | 분류/수정 필요 |
| --- | --- | --- |
| 신고 반려 REJECT 기대 | 요청 action=REJECT, reportId=1의 실제 저장은 status=REJECTED/resolution_action=NONE, 처리자3·처리시각 존재 | **기존 테스트 기대값 오류.** 서비스 수정 불필요. 기존 spec은 보존하고 P0 전용 spec에서 현재 계약을 검증 |
| 로그아웃 후 별도 소켓 철회 | 로그아웃 후 현재 앱 소켓은 DISCONNECT/종료, 토큰 없는 REST는 401. 기존 JWT로 연결한 별도 probe는 계속 수신하고 SEND도 DB에 저장됨 | **인증 정책과 기대 불일치.** 현재 클라이언트 JWT 삭제 계약의 한계. 서버 철회 기능은 임의 구현하지 않음 |
| 저장 확정 전 초안 삭제 | 클라이언트 SEND만 전송 경로에서 차단. publish 호출 뒤 입력="", 자기 저장 확정 없음, DB 행 0 | **실제 서비스 결함 D1.** 사용자 입력 복구 수단 없이 내용 유실. 수정 필요 |
| 저장 성공 뒤 재조회 실패/중복 | 실제 댓글 POST 200·DB 첫 행 저장 뒤 상세 GET 1회만 503 주입. 초안이 남고 재등록하면 실제 POST 200·별도 행 추가 | **실제 서비스 결함 D2.** 쓰기 성공을 조회 실패로 취급해 중복 등록 유도. 수정 필요 |

### D1: 채팅 미확정 전송의 초안 유실

재현 절차:

1. 실제 UI로 로그인하여 채팅방에 연결·구독한다.
2. 브라우저→서버의 SEND 프레임만 차단하고 연결 상태·REST는 유지한다.
3. 텍스트를 입력해 전송한다.
4. 입력값이 빈 문자열로 바뀌고, 자기 MESSAGE/DB 저장은 없음을 확인한다.

원인: `frontend/src/pages/chat/ChatPage.js`의 sendMessage는 useChat.send가 true를 반환하면 즉시 초안을 비운다.
`useChat.js`의 true는 `chatSocket.js`의 동기 publish 성공이며 업무 트랜잭션 커밋 확인이 아니다.

영향: 전송 이후 네트워크 단절/거절 등 저장 확정이 없는 상황에서 작성 내용이 사라질 수 있다.
이번에 실제 재현한 조건은 SEND 차단이며 모든 네트워크/서버 오류를 실행한 것은 아니다.
수정 후보는 ChatPage/useChat/chatSocket과 해당 기존 단위 테스트다. 미확정 입력을 보관하고 저장 확인/실패를 구분해야 한다.
동일 본문 동시 송신의 정확한 상관관계·전송 확정 기준이 필요하면 별도 계약 검토가 필요하며 자동 재전송은 임의 도입하지 않는다.

### D2: 댓글 커밋 후 상세 조회 실패가 재작성으로 이어짐

재현 절차:

1. 게시글의 초기 상세 조회가 완료된 뒤 댓글을 작성한다.
2. 실제 POST를 그대로 서버에 전달하고 HTTP 200 및 MySQL commentId=13 저장을 확인한다.
3. 쓰기 다음의 상세 GET만 503으로 대체한다. 실제 서버에서 발생한 503으로 집계하지 않는다.
4. 초안이 남아 있는 상태에서 다시 등록한다.
5. 같은 postId=13/본문에 commentId=13,14 두 행이 커밋된다.

원인: `CommunityPostPage.js`의 addComment가 생성과 `await onRefresh()`를 같은 runAction에 묶는다.
조회 실패도 false로 반환하므로 CommentComposer가 초안을 정리하지 않고 다시 전송할 수 있다.

영향: 실제 재현은 일반 댓글이며, 답글은 동일한 addComment/runAction 경로를 사용하므로 영향 가능성이 있다.
좋아요/삭제의 stale UI 재조작도 같은 갱신 구조를 검토해야 하지만 이번 중복 댓글 결과로 해당 부작용까지 재현했다고 주장하지 않는다.
수정 후보는 CommunityPostPage와 기존 CommunityPage/익명 댓글 관련 테스트다.
쓰기 성공을 확정한 뒤 조회 실패를 별도로 표시하고 조회 재시도가 쓰기를 반복하지 않도록 해야 한다.
백엔드 API/DB 스키마 변경은 현재 근거만으로 필요하지 않다.

두 결함 모두 **원인·범위 보고까지만 수행했으며 수정 승인 전 운영 코드를 변경하지 않았다.**

## 3. 하네스 오류·기대값 보완과 원본 기록

| 실행 증거 ID | 실행 결과 | 해석 |
| --- | --- | --- |
| `82cdabce6030` | 8 attempts: 3 PASS/5 FAIL | ① 이미지 삭제 버튼을 원본 파일명으로 찾은 오류, ② 초기 조회를 실패시킨 주입 시점 오류, ④ 나가기 즉시 ERROR/close를 강제한 오류, ⑦ 모든 과거/HMR 소켓까지 종료를 요구한 관찰 오류. ⑥은 D1 실제 결함 |
| `c6f4e65335f7` | 0 tests, 실행기 실패 | 후속 --grep의 시작 앵커 때문에 수집 0. 업무 PASS/FAIL에 합산하지 않음. 추가 fixture 사용자3명은 보존. 이후 자원 사용 전 정확한 테스트 수집 검사 추가 |
| `81b2aaea5032` | 4 attempts: 2 PASS/2 FAIL | ①④ 완료. ②는 D2 재현. ⑦은 현재 소켓 외 과거 문서의 WebSocket 관찰 객체에 남은 closed=false까지 검사한 오류 |
| `5fa01fae60e4` | 1 attempt PASS | 현재 문서의 앱 소켓만 추적하여 ⑦ 완료. 남아 있는 독립 JWT probe는 정책 관찰로 분리 |

원본 결과·실패 문맥·서버 로그는 수정하거나 삭제하지 않았다. 최종 고유 항목은 각 완료 근거를 선택해 판정한다.
실행된 Playwright attempt 총 13개 중 6 PASS/7 FAIL이다. 이 중 서비스 결함 2회와 하네스 실패 5회를 구분하며,
이를 고유 P0 8개(6 PASS/2 FAIL)와 혼동하지 않는다. 다른 팀의 전체 인증/Calendar/Matching 테스트는 실행하지 않았다.
공유 레이아웃이 자동으로 수행한 인증·일정 등의 조회는 HTTP 계측에 포함될 수 있으며 별도 기능 테스트가 아니다.

## 4. 계측·증거

- 브라우저에서 관찰한 백엔드 API 응답 **1,312건** 중 오류 주입 503 **2건**을 제외한 실제 서버 API 응답 **1,310건**.
  서버 응답 분포는 200=1,261 / 400=16 / 401=8 / 403=21 / 404=4다.
  로그인 fixture·배경 조회·후속 재검증을 포함한 수이며 고유 API 수가 아니다.
  브라우저가 기록하지 않은 CORS preflight, WS handshake, React 준비 확인 요청까지 포함한 전체 wire HTTP 수를 뜻하지 않는다.
- 별도 JDBC DB snapshot **57회**. 매 접속에 새 컨테이너 UUID·DB명·전용 계정/권한·호스트·UTC 검사를 유지했다.
  초기 스키마/fixture·추가 사용자 INSERT의 JDBC 작업은 snapshot 횟수와 구분한다.
- `/ws/chat` 브라우저 기록에 CONNECTED 38, MESSAGE 46 프레임이 있다. 한 메시지가 여러 구독에 전달되므로 고유 저장 건수가 아니다.
  라우팅 프록시의 상세 프레임 관찰은 사례 observation에 별도 보존했으며 중복 합산하지 않는다.
- 모든 실행에서 해당 실행 전후의 기록 대상 소스 지문은 일치한다. 테스트 코드 보완은 실행 사이에만 했다.
- trace/screenshot/video는 끈 상태를 유지했고 토큰·비밀번호·메일 코드·CONNECT 인증 원문은 보고서에 포함하지 않았다.

추적용 요약: [validation/ccra-stage6-7-p0-20261023.json](validation/ccra-stage6-7-p0-20261023.json).
원본은 `build/full-service-e2e/<위 증거 ID>/`의 source-before/after, results.json,
browser-*.json, database-*.jsonl, run.json 및 로그다. 각 실행의 `ccra-p0-summary.json`은 이 원본에서 파생한 집계다.
추가 실행의 process 로그는 마스킹해서 기록했다.

## 5. 변경 파일과 검증

이번 실제 실행에서 추가한 파일:

- `frontend/e2e/tests/ccra-p0.spec.cjs`: P0 8개, 기대값 보완과 결함 재현 assertion.
- `frontend/e2e/lib/ccra-p0.cjs`: 기존 flows/fixtures/database 재사용, 실제 UI·바이너리/파일·프레임 관찰.
- `backend/tools/ccra-stage6-7/continue.cjs`: 현재 세션의 새 컨테이너만 대상으로 하는 제한된 후속 검증.
  기존 행을 초기화하지 않고 검증 사용자만 추가, 기존 E2eServer·공통 fixture·Playwright 재사용, Docker 생성/정리 없음.
- `backend/tools/ccra-stage6-7/report.cjs`, `finalize.cjs`: 명시된 이번 실행 증거만 집계·최종 소유 자원 상태 확인.
- 이 문서와 추적용 JSON 요약.

기존 community-report/chat/auth-boundaries spec과 공유 실행기 4개는 이번 실제 검증 단계에서 추가 변경하지 않았다.
공유 4개 파일의 보존 모드 변경은 직전 승인 단계의 변경으로 남아 있다. 서비스·Controller·Entity·공개 계약·마이그레이션 변경은 없다.

- 원래 실행의 `prepareE2e`: 실제 서버 기동 전 컴파일 성공.
- P0 테스트 수집 8개/후속 4개/마지막 1개 확인. P1 미실행.
- 계약 스냅샷: 73 endpoints / 75 record DTOs / 30 entities PASS.
- JavaScript 문법·git diff --check PASS.
- `cd frontend; npm run build`: Compiled successfully, production build PASS.
- 전체 인증/Calendar/Matching 회귀와 전체 `gradlew test bootJar`는 중복 실행하지 않았다.
  이번 결과가 전체 저장소의 모든 테스트 통과를 의미하지 않는다.

## 6. 자원 보존 상태와 한계

새 Docker 컨테이너 **1개**, 스키마 **1개**, 테스트 DB 계정 **1개**만 생성했다.

- 컨테이너 `kkiri-e2e-82cdabce6030`: 최종 `running`, 이미지 `mysql:8.0.46-oraclelinux9`.
- 스키마 `kkiri_e2e_82cdabce6030`, 전용 DB 계정 `e2e_82cdabce6030`.
- 서버 UUID `f8a3a22f-ce99-11f1-bd9a-621e38ee5f3d`.
- 업로드 `backend/build/e2e/82cdabce6030/uploads` 보존. 최종 실물 파일5개/340 bytes 확인. 컨테이너·스키마·DB 계정 삭제 없음.
- Spring/React/자체 기동 자식 프로세스는 종료했고 13000/18080 포트가 비어 있음을 확인했다.
  실행 중 활성 Spring Boot JVM은 항상 1개였으며, 중첩 기동하지 않았다.
- 최종 snapshot: application 사용자/프로필 각12, 게시글16, 이미지 연결5, 댓글16, 방7, 참여14, 메시지21, 신고6,
  익명 번호 연결4, 이미지 정리 대기0. 이 수에는 소프트 삭제 행과 원본 실패/추가 fixture가 포함된다.
  application 사용자12명은 새 검증 fixture 4세트이며 MySQL 계정을 12개 만든 것이 아니다.
- 기존 보존 DB·Docker 자원은 열람·접속·변경하지 않았다. 소유 컨테이너의 최종 상태만 명시적 이름/라벨로 확인했다.
- 별도 승인 전 `test:e2e:clean`, 컨테이너 stop/restart/rm, DB 초기화, 위 후속 실행 도구 재호출을 하지 않는다.
  후속 도구의 하드코딩된 ID는 이번 세션 이력용이며 보존된 자원에서 자동 재실행할 명령이 아니다.

tmpfs이므로 컨테이너를 그대로 유지해도 Docker 엔진/호스트 재시작까지 영속성을 보장하지 않는다.
실제 배포 WSS/TLS·프록시·영속 볼륨·다중 JVM 전달, MySQL 부하/장기 장애, P1은 이번 검증 대상이 아니다.

다음 단계는 D1/D2 수정 범위 승인 후 관련 회귀이며, P1 실행과 자원 삭제도 각각 별도 승인 대상이다.
