# 5단계 Community · Chat · Report · Admin 추가 HTTP 검증

기준일: 2026-10-22, Asia/Seoul.

**최종 고유 19개 시나리오 PASS / 서비스 FAIL 0 / 배포 환경 BLOCKED 1.**
실제 TCP HTTP → Spring Boot → MySQL 8.0.46 경로로 REST·이미지 HTTP **240건**을 실행했다.
초기 188건과 도구 보완 후 4개 사례 재실행 52건의 합이다. 240건 중 현재 계약 경로는 238건이며,
초기 도구가 잘못 호출한 미제공 경로 2건(403)을 별도 구분한다. WebSocket handshake는 이 수에 포함하지 않는다.
기존 MySQL 8.0.46 24개/HTTP 162건과 이미지 Worker 13/13은 사용자 제공 선행 결과로만
취급하고, 이번 실행 수에 더하거나 재실행하지 않았다.

## 1. 생성·수정 파일

신규 생성 파일 5개이며 서비스·기존 테스트·기존 실행기·API/ERD/배포/마이그레이션은 변경하지 않았다.

| 파일 | 내용 |
| --- | --- |
| tools/ccra-stage5-http/Stage5Http.java | 추가 업무 19개, 실제 multipart/바이너리 GET·HTTP 응답/후속 DB·파일·STOMP 증거 |
| tools/ccra-stage5-http/run.ps1 | DB 없는 준비, 정확한 자원 계획, 승인 실행, 전용 포트·스토리지, 소유권 정리 |
| tools/ccra-stage5-http/RUNBOOK.md | 재사용 도구·실행·판정·정리·증거 안내 |
| CCRA_STAGE5_HTTP_VALIDATION.md | 이 보고서 |
| validation/ccra-stage5-http-20261022.json | 19개 최종 결과·실행별 결과·HTTP 분포·환경·도구 해시·정리 증거 요약 |

기존 CCRA·Chat 소스를 읽기 전용으로 재사용하며 build/generated에만 adapter를 생성한다.
기존 이미지 HTTP 테스트의 판정 기준을 추가 multipart/GET 도구에 적용했다.

## 2. 준비 검사와 현재 환경

| 검사 | 결과 | 근거/한계 |
| --- | --- | --- |
| 현재 소스 bootJar | PASS | Gradle BUILD SUCCESSFUL, 기존 테스트 재실행 없음 |
| 추가 Java 실행기 컴파일 | PASS | Java 21, compileExit=0 |
| 19개 사례 카탈로그·이름 유일성 | PASS | PREPARE는 JDBC/HTTP 접속 없음 |
| 계약 스냅샷 | PASS | node tools/sync-contract-docs.cjs --check, 73 endpoints / 75 record DTOs / 30 entities |
| Docker·이름·포트·로컬 이미지 | PASS | 2026-10-22 17:41:39 KST, 읽기 전용 READY |
| 신규 자원 생성·실행·정리 | PASS | 사용자 명시 승인, 신규 자원에서 2회 업무 실행, 1회 준비 실패; 생성 컨테이너 3개 모두 ID/name/owner 검사 후 정리 |
| 실제 배포 WSS/TLS/Origin/재접속 | BLOCKED | 대상 배포 URL·프록시·TLS 환경 미제공 |

초기 Docker 조회는 엔진 파이프가 없어 실패했으나, 마지막 읽기 전용 재확인은 READY다.
보존 DB·운영 DB·사용자 업로드 파일에 접속·변경·삭제하지 않았다.
검증 서버 PID 20172 / 19608은 각각 단일 JVM 실행이며 둘 다 종료했다.
로컬 테스트 파일·원본 증거는 보존했다. 컨테이너와 내부 전용 DB/계정/tmpfs만 정리했다.

검증용 JAR SHA-256:
19F69E2A0533B5FBC022E940B0FFEA51ACD1E37E076DC4C886125DD315168077.

## 3. 추가 실제 HTTP 검증 범위와 현재 상태

아래는 실제 응답 및 독립 JDBC·후속 HTTP·파일 검사 결과다.
각 요청·응답·후속 상태는 build/ccra-stage5-http/run-Owner의 requests.json/responses.json/cases.json에 있다.
최종 고유 사례 결과는 [기계 판독 요약](validation/ccra-stage5-http-20261022.json)에 보존한다.

| 요청 항목 | 추가 검사 | 실제 HTTP / errorCode 및 후속 확인 | 최종 |
| --- | --- | --- | --- |
| 1 이미지 multipart 생성·수정 | PNG 포함 생성, 유지+신규 파일 수정 | 200, postId / 수정 data=null; DB 연결·파일 바이트·상세 일치 | PASS |
| 2 이미지 GET·유지·순서·제거 | 유지 ID 역순·부분 제거·생략/null/[]; 미연결 파일 | GET 200/404 빈 본문, no-store; 기존 순서+신규 추가; 개별 제거 파일 삭제·무관 파일 보존 | PASS |
| 3 개수·크기·타 게시글 이미지 | 정확히 5개/5MiB, 6개/5MiB+1/0/MIME/합계6/타인ID/중복ID | 200 또는 413 INVALID_REQUEST / 400 VALIDATION_ERROR / 400 INVALID_REQUEST; 실패 시 본문·연결·파일 보존 | PASS |
| 4 익명 상태 변경 전체 거부 | 일반→익명, 익명→일반 multipart; null/생략/동일 | 반대 값 400 VALIDATION_ERROR, 요청 전체 미반영; 유지 요청 200 | PASS |
| 5 방 목록·단건 | 페이지 객체·별도 단건·다른 페이지 방 | 200, 목록 항목과 단건 동일; 타인/없는방 403 FORBIDDEN | PASS |
| 6 방 검색·분류·페이지·정렬 | 대소문자/trim/%,_,! 리터럴, category/counts, 고정·최근메시지·NULL, 페이지 합집합 | 200, 정렬ID=[1,2,3], counts=3/2/1/1; 잘못된 입력 400 VALIDATION_ERROR | PASS |
| 7 메시지 커서 | 실제 STOMP 저장, before DESC/after ASC, 과거 페이지 중 새 메시지 삽입, q, 외부 커서 | 200, before=[9..2], after=[3..10] 누락·중복 없음; 잘못된 조합400 VALIDATION_ERROR, 외부ID400 INVALID_REQUEST | PASS |
| 8 읽음 상태·타인 접근 | 중복 제거/조회 무변경/읽음 후 조회/혼합 외부ID/타인·관리자·나간 참여자 | 200 id/read만, false→명시적 읽음 후 true; last_read_at 조회 무변경; 400 INVALID_REQUEST/VALIDATION_ERROR, 401 UNAUTHORIZED, 403 FORBIDDEN | PASS |
| 9 익명 게시글→채팅→조회 | 게시글 author/mine, 생성·목록·단건 counterpart, 실제 닉네임 검색 거부, senderId, 출처 삭제 후 유지 | 200, 익명 필드 null/익명/tags=[]; 실제 신원 검색 결과 없음 | PASS |
| 10 관리자 직접 게시글 삭제 | DELETE /api/admin/posts/{id}, guest/user 권한, 하위 댓글 상태 | 200 / 비로그인401 UNAUTHORIZED·일반403 FORBIDDEN; post/comment DELETED | PASS |
| 11 관리자 직접 댓글 삭제 | DELETE /api/admin/comments/{id}, 답글 연쇄·형제 보존 | 200 / 비로그인401·일반403; 공개 thread에서 대상·답글 제외, 형제 ACTIVE | PASS |
| 12 공개 조회·이미지·감사 | 전체 게시글 삭제 후 상세·이미지, 댓글만 삭제 후 부모·이미지 | 게시글 상세400 INVALID_REQUEST, 이미지404(관리자도 동일), 연결·파일 보존; 댓글만 삭제하면 부모 이미지200; 내용/상태·신고 건수 보존 | PASS(현재 계약), 별도 감사 이력 없음 |
| 13 게시글·내 활동 목록 | category/q, LATEST/VIEWS/LIKES 실제 순서, 페이지 합집합, 내 글/댓글 | 200 페이지 필드·상태·집계·원글 이동 필드, 비로그인401 UNAUTHORIZED | PASS |
| 14 좋아요 취소·댓글 좋아요 | 글 취소, 댓글 등록/취소, 상세·목록 후속 조회 | 200, 최종 post_like=0/comment_like=0, liked=false/likes=0 | PASS |
| 15 reason=기타 | null/빈문자/공백/한글·이모지500/501, 원문 보존 | 유효4건200, 무효5건400 VALIDATION_ERROR 및 DB 미반영 | PASS |
| 16 잘못된 USER 신고 처리 | USER 신고의 DELETE_TARGET | 400 INVALID_REQUEST; 신고·담당자·처리시각·사용자 상태 불변, 관리자 재조회 PENDING | PASS |
| 17 채팅 입력 경계 | 실제 STOMP ASCII·이모지2000/2001, null/빈문자/공백 | HTTP/errorCode N/A; 유효2건 원문 저장, 무효5건 handler 완료 후 DB 미반영·후속 유효메시지 저장; ERROR 프레임 없음 | PASS |

HTTP 분포: 200=181, 이미지404=9, 400/VALIDATION_ERROR=22,
400/INVALID_REQUEST=10, 413/INVALID_REQUEST=1, 403/FORBIDDEN=12(도구 오경로2 포함),
401/UNAUTHORIZED=5. 거절·404는 각 시나리오의 의도된 성공 조건이며 업무 FAIL 건수가 아니다.

## 4. 판정 기준과 정책 보류

- API.md 7.4는 유지 이미지의 **기존 순서** 뒤 신규 이미지 추가 계약이다.
  keepImageIds 입력 순서에 따른 재배열 기능은 없다. 역순 입력 시 기존 순서 유지를 검사한다.
- API.md 9번 이미지 보완 및 ERD.md에 따라 개별 연결 제거는 커밋 후 파일 정리,
  게시글 전체 소프트 삭제는 연결·파일 보존 및 공개 접근 차단으로 구별한다.
- 관리자 직접 삭제의 별도 삭제자/시각/사유 감사 필드·테이블·API는 현재 코드에 없다.
  기존 행의 보존 내용·상태·updated_at과 신고 건수 보존을 확인했다.
  별도 감사 기록이 필수인지는 확정이 필요하며 임의 구현하거나 감사 기능 PASS로 표시하지 않는다.
- 이번 추가 검증에서 현재 확정 계약을 위반하는 서비스 결함은 발견하지 않았다.
  직접 삭제 감사 정보 부재와 잘못된 STOMP 입력의 무응답 거절은 현 구현의 한계로 기록한다.
  별도 정책 결정 없이 서비스 코드를 수정하지 않았다.

## 5. 초기 도구 오류와 재현·재검증

첫 실행은 15 PASS / 도구 FAIL 4, HTTP 188건이었다. 원본 cases/requests 및 초기 실행기 소스를
run-9099e664a657468d9a40766501667590에 보존했다. 운영 소스를 바꾸지 않고 아래 도구만 수정했다.

| 도구 문제와 재현 | 수정·최종 근거 |
| --- | --- |
| 읽음 상태 SQL의 is_read를 공통 number()로 읽어 MySQL Boolean→Number ClassCastException | COUNT(*) WHERE is_read=1로 검사; 읽음/권한 전체 사례 PASS |
| 미제공 GET /api/users/me/liked-posts를 호출해 403 두 건 | Controller·API·프론트 확인 후 제거; 실제 내 글/댓글 및 공개 목록/상세 계약 검사 PASS |
| 무효 STOMP SEND가 반드시 ERROR/close를 반환한다고 가정 | 실제 계약은 프레임 보장 없음. 기존 앱 handler의 완료만 관찰하는 테스트 interceptor 추가; 5개 입력 모두 완료·DB 미반영·유효 후속 전송 PASS |
| 재시도 추가 중 PowerShell Attempt와 준비 반복문의 attempt가 같은 변수로 취급됨 | readiness 변수명을 분리. 해당 준비 실패는 HTTP/JDBC 0건이며 생성 컨테이너 정리 완료. 업무 FAIL에 합산하지 않음 |

최종 재검증은 같은 승인 대상 이름·포트·DB·스토리지를 사용하며, 성공한 15개는 반복하지 않고 위 4개만 실행했다.
결과는 run-9099e664a657468d9a40766501667590-retry1의 **4 PASS / 0 FAIL / HTTP 52건**이다.
최종 19 PASS는 첫 실행 15개와 재검증 4개의 합이며 단일 실행 19/19로 오표기하지 않는다.
정리 증거는 cleanup-Owner.json, cleanup-Owner-0.json(준비 실패), cleanup-Owner-retry1.json이다.

## 6. 중복 제외 및 미검증

기존 CCRA 24개/162건, Worker MySQL 13개, 기본 양방향 WebSocket 전달, 신고 기본 처리/경합/물리제약,
기존 H2 전체 테스트와 성능·인덱스 cycle은 재실행하지 않았다. 기본 CRUD는 추가 사례의 fixture로만 사용한다.
다중 JVM 메시지 중계 요구나 외부 브로커를 추가하지 않는다.
이번 결과는 격리된 실제 MySQL·로컬 파일 저장소·단일 JVM에 대한 근거다.
운영 영속 볼륨/백업, 실제 배포 WSS·TLS·Origin·재접속, 지연 커밋 전체 시나리오는 검증하지 않았다.

## 7. 승인 대상과 커밋 가능한 변경

정확한 생성·정리 대상 및 명령은 [실행 안내](tools/ccra-stage5-http/RUNBOOK.md)에 있다.
승인 계획은 build/ccra-stage5-http/provision-plan.json에 보관한다.
실제 DB는 kkiriccra59099e664a657, 컨테이너 이름은 kkiri-ccra-stage5-mysql-20261022였다.
각 실행 직전 환경을 다시 확인했고 각 생성 컨테이너의 ID/name/owner를 검사한 뒤 정리했다.

커밋 가능한 변경은 1절 신규 파일 5개뿐이다. build 아래 JAR·adapter·원본 실행 결과·이미지는 Git 제외다.
이번 작업에서 커밋·푸시·운영 코드 수정은 수행하지 않았다.
