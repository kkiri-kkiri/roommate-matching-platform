# 6·7단계 Community · Chat · Report · Admin 통합/E2E 사전 계획

기준: 2026-10-23 (Asia/Seoul), HEAD `0b3247b`. **분석·계획만 수행했다. 테스트·빌드 실행, DB 접속, Docker 조회/생성/삭제, 서버 기동은 하지 않았다.**
이번 변경은 이 계획 문서 하나다. 아래 변경·자원·실행은 제안이며 아직 실행하지 않았다.

## 1. 기존 분석 복원 결과와 근거

- 현재 추적 문서, 관련 테스트/호출부, `API.md`·`ERD.md`·`DEPLOYMENT.md`를 읽었다.
- 로컬에서 접근 가능한 `git log --all`의 커밋 메시지, Markdown 추가/삭제 경로,
  `6단계|6 단계|P1|사전.?분석` 변경 이력과 E2E 경로 이력을 확인했다. 원격 fetch는 하지 않았다.
- `989dc4f`: 격리 Playwright 실행기·E2E Java source set·로그인 테스트 도입.
- `01c56d5`: 현재 community-report/chat/auth-boundaries 테스트, A/B/admin fixture,
  실제 STOMP 프레임·DB snapshot·증거 수집 도입. 이 테스트들의 경로 이력은 해당 커밋으로 이어진다.
- `051caca`: 5단계 CCRA HTTP 검증 문서·도구. 문서의 19개 PASS/240 HTTP는 선행 기록이며 이번 결과가 아니다.
- **6단계 사전 분석 원문과 기존 P1 6개 명단은 복원하지 못했다.**
  아래 P0 8개는 사용자 지정 항목이고 P1 6개는 현재 코드/문서에 근거한 새 제안이다.
  기존 P1과 동일하다고 주장하지 않으며 원문 발견 시 이름·범위를 대조한다.

## 2. 기존 테스트 재사용 범위

| 기존 파일 | 그대로 활용할 기반 | 보완/분리할 부분 |
| --- | --- | --- |
| `frontend/e2e/tests/community-report.spec.cjs` | 실제 multipart 게시글/이미지 등록·조회·수정·삭제, 일반 댓글 CRUD, 게시글/댓글 좋아요, 타인 수정/삭제 403, 신고 UI→관리자 반려, DB 확인 | 반려 저장 기대값 수정. 이미지 유지/교체/개별 제거·비로그인 공개 GET, 익명 댓글/답글·내 활동, 신고의 나머지 처리·사용자 반영, 관리자 직접 삭제·통계 추가 |
| `frontend/e2e/tests/chat.spec.cjs` | Community 출처 방 생성, 참여자 2명, A/B 실제 SEND/MESSAGE·DB 저장, reload 이력, 비참여자 REST/SUBSCRIBE 거절, 나가기 후 기존 구독/SEND 차단 | 양방향 프레임을 메시지 ID로 연결. 목록·설정·읽음·강제 연결 단절·재구독·polling 단독 복구·다중 페이지 이력 추가 |
| `frontend/e2e/tests/auth-boundaries.spec.cjs` | 실제 로그인, 관리자 회원 상태 변경, 기존 JWT·STOMP 정지/만료 경계, 로그아웃 UI 소켓 정리 | 현재 큰 테스트는 Matching/Survey/Calendar도 호출하고 TTL 변경으로 JVM을 재시작한다. 담당 도메인 경계만 독립 실행 가능하게 분리하고 인증 담당자와 조율. 로그아웃의 retained probe 기대 수정 |
| `frontend/e2e/fixtures.cjs`, `lib/flows.cjs`, `lib/database.cjs` | 테스트별 새 context, A/B/admin, UI 로그인, 실제 요청 대기, 방 생성, STOMP probe, 보호된 DB snapshot | 다중 이미지·익명/답글·처리 fixture와 프레임/파일 관찰 보완. 네트워크 오류는 실제 쓰기 전달 후 지정한 조회만 실패시키도록 제어 |
| `backend/src/e2e/java/e2e/E2eDatabase.java`, `E2eServer.java` | 전용 새 DB 신원 검증, 최소 계정/기준정보, 별도 JDBC 조회, validate/SQL never/seed NONE, mock SMTP·AI, 수집 차단 | 댓글 anonymous/익명 번호, 처리 시각·생성 시각·파일 정리 작업 등 필요한 읽기 전용 projection 보완 |

기존 community-report는 Matching 관심·설문·프로필 수정까지 한 테스트에 결합되어 있다.
이를 통째로 실행하면 다른 담당자의 범위가 중복되므로 공통 계정 fixture만 활용하고 CCRA 부분을 독립 테스트로 나누는 안을 우선한다.
테스트 내부 `test.step`은 CLI 선택 단위가 아니므로 `--grep`만으로 도메인 부분을 골라 실행할 수 없다.
기존 전체 흐름을 다른 담당자가 사용하면 기존 테스트를 보존하고 CCRA 전용 사례를 같은 helper로 추가하는 안을 조율한다.

## 3. 기대값 오류와 구현 위험 구분

| 항목 | 정적 검토 결론 | 승인 후 검증·처리 방향 |
| --- | --- | --- |
| 신고 반려 `REJECT`/`NONE` | **테스트 기대 오류 확정.** AdminService는 요청 action=REJECT → status=REJECTED, resolution_action=NONE으로 저장한다. 현재 spec 103행은 resolution_action=REJECT를 기대한다 | 요청 REJECT 유지, 저장 기대 NONE으로 수정. 처리자/처리시각과 대상 콘텐츠·계정 불변, 사용자 신고 표시를 함께 검사 |
| 로그아웃 후 유지한 별도 probe 소켓 | **현재 인증 계약과 기대 불일치.** API 2.2는 JWT의 클라이언트 삭제다. 기존 인증 소켓은 세션 토큰의 유효성·계정 활성·참여를 다시 검증하지만 클라이언트 로그아웃 사실을 서버에 전달하는 철회 계약은 없다 | 앱 소켓 종료·토큰 없는 REST 401·채팅 UI 차단은 필수 assertion. 별도 retained probe는 계약 한계 관찰로 분리. 서버 철회 정책은 임의 도입하지 않는다. 정지/만료/나가기는 계속 필수 차단 |
| 저장 확정 전 채팅 초안 제거 | **구현 위험 경로 확인, 실제 재현 미실행.** ChatPage 250~255행은 send=true 즉시 초안을 비운다. useChat.send의 true는 동기 publish 성공이며 DB 저장 응답이 아니다 | 연결 후 SEND 지연/차단 또는 서버 거절을 주입하여 초안·오류·자기 MESSAGE·DB를 별도 관찰. 미확정 전송 상태와 저장 확정을 분리하는 최소 수정 검토. 자동 재전송·새 멱등성 계약은 임의 추가하지 않는다 |
| 저장 성공 뒤 상세 재조회 실패 | **댓글·답글의 결함 경로 확인, 실제 재현 미실행.** CommunityPostPage.addComment는 생성 뒤 onRefresh까지 runAction으로 묶는다. GET 실패 시 false로 끝나 composer가 본문을 남겨 재등록할 수 있다. 좋아요는 오래된 liked로 재요청, 삭제는 성공 대상을 다시 삭제할 위험이 있다 | 실제 POST/PATCH/DELETE 200과 별도 JDBC 커밋 확인 뒤 후속 GET 1회만 실패. 새 댓글/답글이 1행인지, 조회 재시도에 쓰기가 없는지 검사. 쓰기 성공·조회 실패 상태를 분리하는 최소 수정 검토 |
| 관리자 처리 뒤 재조회 | **같은 결함으로 단정하지 않음.** AdminPage.applyAction은 쓰기 성공 후 확인창을 닫고 revision으로 별도 조회한다 | 후속 GET 실패가 쓰기 실패로 표시되거나 동일 PATCH/DELETE 재전송으로 이어지는지 회귀 검사 |

이미지 정책도 기대값을 구분한다. 개별 제거는 POST_IMAGE 연결 제거 및 커밋 후 미참조 파일 정리이며,
게시글 전체 소프트 삭제는 연결·실물 파일을 보존하고 공개 이미지 GET을 404로 막는다.
일반 삭제 대상의 모든 API를 404로 통일하지 않는다. 기존 서비스의 400 INVALID_REQUEST 계약을 따른다.
신고 반려가 기존 신고 이력/사용자의 reported 표시를 지운다고 가정하지 않는다.

## 4. 필요한 fixture와 증거

- **행위자:** 기존 ACTIVE 일반 A/B와 ADMIN 사용. 관리자도 비참여자 역할로 재사용한다.
  정지/탈퇴 같은 파괴적 상태 변경은 독립 사례 끝에 수행하며 다음 테스트의 보호된 reset으로 격리한다.
  JWT 주입/인증 응답 모킹 대신 기존 UI login을 사용한다.
- **Community:** 일반/익명 게시글, 일반/익명 댓글·답글과 안정된 ID, 두 계정 좋아요·내 활동,
  두 개 이상의 서로 다른 유효 이미지. 이미지 최대 장수·크기·MIME 오류는 별도 경계 fixture로 준비한다.
- **Report/Admin:** USER/POST/COMMENT별 서로 다른 대상의 PENDING 신고를 만들어
  COMPLETE/REJECT/SUSPEND_USER/DELETE_TARGET을 독립 검증한다. 이미 처리한 신고 재처리 거절·불변도 확인한다.
  직접 삭제용 별도 게시글/댓글을 준비하며 신고 경유 삭제와 구분한다.
- **Chat:** 기본 2인 방, 다른 출처/다른 방, 미열람 메시지, 선택 방/비선택 방,
  50개를 넘는 이력 fixture. 정확한 메시지 ID·본문·송신자·방으로 UI/REST/DB를 연결한다.
  대량 이력은 소규모 실제 UI 송수신 검증과 구분하여 테스트 전용 fixture로 준비할 수 있다.
- **네트워크:** 브라우저별 실제 WS 단절·재연결·프레임 관찰/차단, REST는 유지한 채 WS만 차단,
  쓰기 성공 뒤 지정 GET 1회 실패, 늦은 조회 응답. 전체 offline은 polling 단독 검증에 사용하지 않는다.
  `CONNECTED`만으로 구독 성공을 선언하지 않고 구독 후 양성 메시지 수신으로 준비 완료를 확인한다.
- **읽음:** `chat_message.is_read`와 REST unreadCount/read-status를 확인한다.
  별도 receipt 테이블을 가정하지 않는다. 보이는 상대 메시지 ID만 읽고 숨김 탭·뷰포트 밖·실패 조회는 읽지 않는지 확인한다.
- **파일:** 실제 저장은 `backend/build/e2e/<새 id>/uploads`에 한정한다.
  경로·바이트 수/해시·존재 여부와 DB 연결/정리 작업을 읽기 전용으로 기록한다.
  이미지 정리 스케줄은 E2eServer에서 꺼져 있지만 개별 제거의 afterCommit worker 호출은 별도다.
  정상 개별 제거를 확인하되 기존 Worker 13개 시나리오는 반복하지 않는다.
- **대시보드:** UI·응답 DTO·별도 JDBC 결과를 비교한다. totalMembers는 ADMIN/정지/탈퇴를 포함한 전체 User,
  totalPosts는 ACTIVE 게시글, totalComments는 ACTIVE 댓글·답글 행, totalReports는 전체 신고,
  pendingReports는 PENDING이다. 최근 항목 3개의 정렬은 각 repository 기준으로 검증한다.
  기존 빈 일정 fixture에서는 학교/기숙사 건수 0·최근 목록 빈 배열을 확인한다.
  비어 있지 않은 일정 통계 검증은 Calendar 담당자의 fixture/결과와 연계하고 크롤러/전체 달력 테스트를 실행하지 않는다.
- **결과:** HTTP method/path/status, STOMP command/방/메시지 ID, DB·파일 결과, 사례별 판정을 저장한다.
  인증 프레임 원문·토큰·메일 코드·비밀번호는 기록하지 않고 trace/screenshot/video 기본 비활성화를 유지한다.

## 5. 승인 후 P0 실행 순서

| 순서 | 사용자 P0 | 실행·통과 기준 |
| --- | --- | --- |
| 1 | ① 게시글·이미지 CRUD/공개 조회 | 등록·수정 시 기존 이미지 유지/추가/개별 제거, 무인증 이미지 200·바이너리·캐시 헤더, 소프트 삭제 뒤 연결/파일 보존·공개 404, UI/DB/파일 일치 |
| 2 | ② 댓글·답글·좋아요/내 활동 | 일반·익명 작성/수정/삭제·번호/마스킹/mine, 답글 연결, 양 계정 좋아요/취소·활동 격리, 저장 후 GET 실패의 중복 등록 회귀 |
| 3 | ③ 신고→관리자 처리→사용자 반영 | 접수·중복·조회·각 처리와 처리자/시각, 반려 NONE, 삭제/제재 효과를 사용자 재조회·활동/출처 화면에서 확인. 반려 자체에 콘텐츠 삭제를 기대하지 않음 |
| 4 | ④ 채팅방 생성·목록·설정·나가기 | 출처별 방 생성/재사용·목록, pinned/muted 사용자별 독립성·reload 보존, leave 이후 목록/접근·기존 구독 차단 |
| 5 | ⑤ 두 브라우저 실제 STOMP | 동일 단일 JVM A/B 각각 CONNECTED/SUBSCRIBE/SEND/MESSAGE 확인, 양방향 동일 message ID의 UI 수신·DB 1행. REST를 막아도 이미 연결된 정상 STOMP 수신을 확인하는 독립 사례 |
| 6 | ⑥ 읽음·재연결·이력 복구 | 읽음/미읽음 일치, reload 외 실제 WS 단절→재연결/재구독, 끊긴 동안 저장 이력 afterId 복구·중복 없음. WS 차단 지속/REST 허용 상태에서 15초 polling 복구 별도 확인. SEND 미확정 초안 사례 포함 |
| 7 | ⑦ 관리자 권한·제재·직접 삭제 | 일반 사용자 UI 차단 및 실제 관리자 API 403/DB 불변, 정지→유효 JWT의 REST/SEND/기존 구독 전달 차단, 직접 post/comment 삭제와 사용자 반영. 인증 전체 검증은 제외 |
| 8 | ⑧ 대시보드 일치 | 신규 사례의 처리 전후 통계·최근 항목을 UI/DTO/DB 대조. 정지/탈퇴가 전체 회원 수를 줄인다고 기대하지 않음 |

각 항목은 독립 fixture를 사용한다. 앞 사례가 남긴 전역 행 수/닉네임에 의존하지 않는다.
⑤의 STOMP 성공, ⑥의 REST polling 성공, DB 커밋 성공은 **각각 독립 판정**한다.
P0 결과를 검토한 뒤 P1을 시작하며 실패 원본·진단·fixture 문제·계약 기대 오류·서비스 결함을 분리한다.
수정 후 관련 실패/회귀만 다시 실행하고 선행 성공 수를 재실행 수에 합산하지 않는다.

## 6. P1 6개 새 제안 — 기존 분석 복원본 아님

| 제안 순서 | 논리 시나리오 | 근거/범위 |
| --- | --- | --- |
| P1-A | 검색·필터·정렬·페이지/빈 결과 | Community/내 활동, Chat 목록, Admin 목록의 서버 요청 조건·합계·마지막 페이지 이동. 현재 CommunityPage/AdminPage/ChatPage 단위 테스트를 E2E 관찰 기준으로 재사용 |
| P1-B | 입력 경계·실패 시 데이터 불변 | 이미지 장수/크기/MIME, 댓글 길이, 메시지 공백/2,000 Unicode 코드 포인트·IME. 기존 API/컴포넌트 검증 범위를 확인하고 브라우저 경계만 추가 |
| P1-C | 익명 출처 채팅 분리·마스킹·삭제 출처 | 일반/익명 POST·COMMENT 출처별 방 재사용/분리, 실제 사용자 ID/프로필 비노출, 출처 삭제 뒤 보존 채팅 이력과 안내 |
| P1-D | 긴 이력·검색·키셋·레이아웃 복구 | 50개 초과 beforeId/afterId, 메시지 검색/해제, prepend 중 화면 앵커·읽음, 중복/역순 수신 병합. 기존 useChatMessages/useChatSync 검증을 실제 브라우저로 확장 |
| P1-E | 담당 도메인 인증 경계·복귀 | 비참여자·나간 사용자·정지/해제·자연 만료·로그아웃 앱 정리. P0와 겹치는 필수 차단은 승계하고 추가 복귀/경계만 실행. TTL 재시작이 필요한 자연 만료는 인증 담당자와 분담 |
| P1-F | 늦은 응답·재조회 오류·중복 조작 | 방/필터/계정 전환 중 늦은 응답, 설정/관리자 처리 뒤 GET 실패, 중복 클릭·조회 재시도 시 추가 쓰기 없음. P0의 초안 유실/댓글 중복 재현은 승계 |

기존 P1이 복원되면 이 제안을 원래 목록으로 대체/대조한다. 새 6개를 실행 대상으로 채택하는 결정도 승인 범위에 명시한다.

## 7. 예상 변경 파일과 협업 범위

| 구분 | 예상 파일 | 목적 |
| --- | --- | --- |
| 테스트 | `frontend/e2e/tests/community-report.spec.cjs`, `chat.spec.cjs`, `auth-boundaries.spec.cjs` | 기존 흐름 재사용·CCRA 선택 실행·기대값 수정·P0/P1 추가. 독립 admin spec 추가 여부는 테스트 분할 시 결정 |
| 테스트 지원 | `frontend/e2e/lib/flows.cjs`, `fixtures.cjs`, `lib/database.cjs`, 필요 시 CCRA 전용 helper | 이미지/신고/읽음·프레임 관찰·지정 실패·파일 증거. 공통 fixture 변경도 소비자와 조율 |
| Java E2E | `backend/src/e2e/java/e2e/E2eDatabase.java` | credential 없는 snapshot/필요 fixture 확장. 서버 설정 변경이 확인될 때만 E2eServer.java 검토 |
| 결함 재현 후 최소 수정 후보 | `frontend/src/pages/chat/ChatPage.js`, `useChat.js`, `frontend/src/api/chatSocket.js`, `frontend/src/pages/community/CommunityPostPage.js` 및 해당 기존 테스트 | 전송 확정 전 초안 유실·쓰기 성공/재조회 실패 분리. 최종 수정 범위는 재현 근거로 결정 |
| 보고/계약 | 이 문서, 추후 실행 결과 문서; 필요할 때 `backend/API.md`, `ERD.md`, `DEPLOYMENT.md` | 실제 검증 결과/미검증 갱신. enum/스키마/로그아웃 정책을 기대값에 맞춰 바꾸지 않음 |
| 공통 실행기 변경 후보 | `frontend/e2e/run.cjs`, `runner.test.cjs`, `playwright.config.cjs`, `backend/build.gradle` | 현재는 변경 필요 확정 아님. 고정 포트/경로 분리 등이 꼭 필요하면 담당자와 변경·실행 범위 조율 후 수정 |

인증/Calendar/Matching 전체 스위트, `user-workflow.spec.cjs`, 기본 무필터 `npm run test:e2e`는 실행 대상에서 제외한다.
관련 Jest·선택된 CCRA 백엔드 테스트·frontend build·계약 snapshot check도 실행 승인 후 수행한다.
저장소 공통 완료 기준인 전체 `gradlew.bat test bootJar`는 다른 담당자의 공통 실행 결과와 소스 지문을 대조하여
중복 여부를 조율한다. 전체 검증을 직접 실행하지 않았다면 그 사실을 결과에 표시한다.

## 8. 자원·실행 승인 계획

- 기존 managed runner 재사용 기준 **새 Docker MySQL 8.0.46 컨테이너 1개/새 스키마 1개/전용 계정 1개**.
  이름 `kkiri-e2e-<12 hex>`, DB `kkiri_e2e_<id>`, 계정 `e2e_<id>`, owner 라벨과 서버 UUID로 소유권 확인.
  데이터는 `/var/lib/mysql` tmpfs 1GiB이며 기존 볼륨/보존 DB를 마운트하거나 재사용하지 않는다.
- React 1개(`127.0.0.1:13000`), Spring Boot 활성 JVM 1개(`18080`), MySQL 포트 `13316`,
  Node 제어 서버 loopback 임시 포트 1개. Chromium A/B/admin은 서로 다른 context이며 별도 인증 세션이다.
  서로 다른 브라우저 엔진 간 호환성 검증을 뜻하지 않는다.
- 기존 포트 점유 시 중단한다. 기존 프로세스 종료·컨테이너 열람·기존 DB 접속으로 우회하지 않는다.
  실행기 공통 build/classpath/test-results 경로도 공유하므로 **다른 팀 E2E와 동시 실행하지 않도록 시간을 조율**한다.
- 증거는 새 `backend/build/full-service-e2e/<id>`에 저장한다. 기존 실행별 증거는 열거나 정리하지 않는다.
  새 업로드·임시 산출물은 해당 id 아래만 사용하며 소스 해시를 실행 전후 남긴다.
- 현재 runner는 정상/실패 종료 시 이번 컨테이너와 tmpfs 데이터를 삭제하고 fixture 전체 행을 비운다.
  따라서 실행 승인은 **새 자원 생성뿐 아니라 그 실행이 소유한 자원의 종료 시 삭제까지** 구체적으로 포함해야 한다.
  분석 단계에서는 실행기를 시작하지 않는다.
- `test:e2e:clean`은 여러 실행의 `backend/build/e2e` 등을 통째로 제거하므로 사용하지 않는다.
  업로드/임시 파일을 정리할 경우 이번 새 id의 절대 경로·소유권·workspace 내부 여부를 확인하고 그 범위만 별도 정리한다.
- SMTP/AI는 기존 대체 구현, 자동 수집/시드/스케줄은 비활성, 앱은 `validate`/SQL `never`를 유지한다.
  새 빈 DB의 DDL은 기존 prepare fixture만 사용한다. 외부 브로커·추가 JVM·새 운영 마이그레이션은 계획에 없다.

다음 승인 시에는 CCRA 테스트/결함 수정 범위, P1 새 제안 채택 여부, 위 새 컨테이너·DB 생성/실행/소유 자원 삭제,
브라우저·서버 기동 및 빌드/테스트 실행을 명시한다. 이번 보고 시점에는 자원/실행 승인 요청을 실행하지 않았다.
운영 WSS/TLS·프록시·영속 볼륨·다중 JVM 전달은 이번 로컬 E2E 결과로 완료 선언하지 않는다.
