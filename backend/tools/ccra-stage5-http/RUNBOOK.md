# 5단계 Community · Chat · Report · Admin 추가 HTTP 검증

현재 결과는 [검증 보고서](../../CCRA_STAGE5_HTTP_VALIDATION.md)를 따른다.
2026-10-22 최종 결과는 고유 19개 PASS(첫 실행 15개 + 도구 보완 후 4개),
REST·이미지 HTTP 240건, 배포 환경 1개 BLOCKED다.
이 실행기는 기존 CCRA 24개/162건과 이미지 Worker 13개 검증을 호출하지 않는다.
각 추가 사례에 필요한 게시글·댓글·방·신고 생성은 fixture이며 기존 사례 재실행으로 집계하지 않는다.
운영 Service/Controller/DTO/Entity는 수정하지 않는다.

## 실행 경계와 재사용

- CCRA의 계정·JWT fixture, 신규 DB 준비, 동일 schema/UUID/hostname/version/GRANT 확인을 재사용한다.
- MatchingMultiInstance의 TCP HTTP·자식 JVM 수명·정제된 오류 기록과
  MatchingExtraChecks.SocketClient의 실제 STOMP 클라이언트를 재사용한다.
- CommunityImageHttpIntegrationTest의 바이트 동일성·no-store·삭제 후 404·파일 보존/제거 판정 방식을
  실제 multipart/HTTP 호출에 적용한다. 기존 H2 테스트 메서드는 실행하지 않는다.
- 기존 실행기 소스는 읽기 전용이다. 포트·schema/account 접두사·자식 진입점 및
  AI/SMTP/crawler 대역은 build/generated 사본에서만 변환한다.
- JAR의 클래스와 라이브러리를 함께 추출해 사용한다. build/classes의 오래된 클래스와 섞지 않는다.
  소스 변경 후에는 먼저 bootJar를 빌드한다.
- 새 빈 schema에만 Entity create-only를 적용하고 HTTP 서버는 validate/SQL never/seed NONE이다.
  이미지 정리 Scheduler와 모든 수집 Scheduler를 끈다. 개별 이미지 제거의 원래 afterCommit 정리는 실행된다.
  Worker 전용 13개 시나리오를 실행하는 것은 아니다.
- 백엔드 서버는 단일 JVM, HTTP는 127.0.0.1:18091, MySQL은 127.0.0.1:13346이다.
  SMTP/AI 외부 네트워크 호출과 .env 읽기, 운영 업로드 디렉터리 접근을 하지 않는다.
- 기존 DB·컨테이너·볼륨은 재사용·초기화·정리하지 않는다. 충돌 시 중단한다.

## 준비 및 승인 실행

저장소 루트 PowerShell, Java/Javac 21 및 현재 소스를 반영한 bootJar가 필요하다.

```powershell
# 기존 24개/H2/Worker 13개 테스트를 실행하지 않는 빌드
Push-Location backend
.\gradlew.bat bootJar
Pop-Location

# 컴파일·19개 이름의 유일성 확인. JDBC/HTTP/DB/컨테이너 변경 없음.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-stage5-http/run.ps1 -PrepareOnly -Owner 9099e664a657468d9a40766501667590

# Docker, 로컬 이미지, 이름, 전용 포트를 읽기 전용 확인
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-stage5-http/run.ps1 -PrepareOnly -CheckEnvironment -Owner 9099e664a657468d9a40766501667590

# 아래 자원의 생성/실행 후 삭제를 사용자가 명시적으로 승인한 뒤에만 실행
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-stage5-http/run.ps1 -ApprovedProvisionAndDelete -Owner 9099e664a657468d9a40766501667590
```

승인 대상:

| 자원 | 대상 |
| --- | --- |
| 새 컨테이너 | kkiri-ccra-stage5-mysql-20261022 |
| 로컬 이미지 | mysql:8.0.46-oraclelinux9, linux/amd64 |
| DB | kkiriccra59099e664a657 |
| 전용 계정 | ccra5_9099e664a657@% |
| DB / HTTP 포트 | 127.0.0.1:13346 / 127.0.0.1:18091 |
| 이미지 경로 | backend/build/ccra-stage5-http/images-9099e664a657468d9a40766501667590 |
| 데이터 영역 | 새 tmpfs /var/lib/mysql 768MiB, 기존 볼륨 연결 없음 |
| 자원 상한 | MySQL 메모리 1GiB/CPU 2개, 검증 JVM heap 512MiB |
| 삭제 범위 | 생성된 컨테이너의 ID/name/owner가 모두 일치할 때 해당 컨테이너만 삭제 |

DB·계정·tmpfs는 컨테이너 삭제와 함께 사라진다. 로컬 증거와 남아 있는 테스트 이미지 파일은 보존한다.
별도 SQL DROP이나 저장소 재귀 삭제는 실행하지 않는다. 강제 종료로 finally가 실행되지 않으면
기존 자원과 구별하여 소유권을 확인한 뒤 정리해야 한다. 승인받지 않은 자동 정리는 하지 않는다.

독립된 다음 실행에는 새로운 Owner로 준비·계획 검토·승인을 다시 수행한다.
승인된 동일 대상의 도구 오류 재시도에는 -Attempt로 증거 디렉터리만 구분하고
-OnlyCases로 실패한 추가 사례만 선택할 수 있다. 첫 실행의 정리 owner를 확인한 경우에만
같은 테스트 이미지 루트를 보존한 채 사용한다. 기존 보존/운영 자원 재사용 기능은 아니다.
이번에 실행한 4개 재검증 명령은 아래와 같다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-stage5-http/run.ps1 -ApprovedProvisionAndDelete -Owner 9099e664a657468d9a40766501667590 -Attempt retry1 -OnlyCases chat-read-status-readonly-permissions,post-and-my-activity-list-contract,post-unlike-and-comment-like-state,chat-stomp-text-boundaries
```
이미지 pull, Docker Desktop 시작·재시작, 기존 컨테이너 중지는 실행기가 수행하지 않는다.

## 시나리오와 판정

19개 시나리오의 정확한 이름은 Stage5Http.java의 catalog와 prepare.json에 있다.
사용자 요청 17항목과의 대응 및 상태는 보고서 표에 기록한다.

- 이미지 순서는 API.md의 **유지 이미지의 기존 순서 다음에 신규 파일 추가**가 기준이다.
  keepImageIds의 순서를 뒤집어도 기존 순서를 유지해야 한다. 순서 변경 API를 새로 가정하지 않는다.
- 개별 이미지 제거는 연결 제거 및 커밋 후 파일 정리, 게시글 전체 삭제는 연결·파일 보존 및 공개 GET 차단이다.
- 5MiB 초과 파일은 컨테이너 multipart 제한으로 413/INVALID_REQUEST,
  최대 개수·0 byte·MIME·합계 개수는 400/VALIDATION_ERROR를 기대한다(API.md 공통 MVC 오류 계약).
- 관리자 직접 삭제는 별도 감사 테이블·삭제자·삭제 시각·사유 컬럼이 없다.
  기존 내용/상태/updated_at 및 신고 건수 보존을 확인하고, 별도 감사 정보를 PASS로 만들지 않는다.
- 커서는 실제 STOMP로 저장한 메시지와 독립 JDBC의 sent_at/message_id 정렬을 비교한다.
  과거 조회 중 새 메시지를 삽입해 beforeId 이동을 검사한다. 다중 트랜잭션 snapshot이나
  지연 커밋 전체 문제를 해결했다고 주장하지 않는다.
- STOMP 무효 입력은 기존 앱 메시지 handler의 처리 완료 후 DB 미반영으로 판단하며,
  소켓이 열려 있으면 같은 연결의 유효 후속 전송도 확인한다. 생성 서버 adapter의
  ExecutorChannelInterceptor는 완료 사실과 correlation ID만 관찰하고 검증/예외 처리를 바꾸지 않는다.
  실제 5개 입력은 모두 NO_ERROR_FRAME이었다. HTTP status/errorCode는 N/A이다.
- 업무 시나리오 실패는 FAIL로 보존하고 서비스 코드는 수정하지 않는다.
  배포 WSS/TLS/Origin/재접속은 해당 배포 환경이 제공되지 않으면 BLOCKED다.

## 증거

Git 제외 backend/build/ccra-stage5-http 아래에 준비/실행 증거를 둔다.

- prepare.json, compile.log: DB 없는 컴파일/카탈로그 결과
- provision-plan.json, environment-preflight.json: 정확한 예정 자원과 읽기 전용 환경 검사
- container-Owner.json, cleanup-Owner.json: 컨테이너 실체와 소유권을 확인한 정리
- run-Owner/artifact.json, source-hashes.json, environment.json, grants.json, schema.json
- run-Owner/cases.json: 기대·실제 값, JDBC 후속 상태 또는 실패 assertion
- run-Owner/requests.json, responses.json: HTTP 상태/errorCode, API body 또는 이미지 바이트 hash/헤더
- run-Owner/websocket.json: 입력 길이·저장 ID·처리 완료·ERROR/CLOSED/NO_ERROR_FRAME 분류; 토큰과 원문 프레임은 저장하지 않음
- run-Owner/servers.json, files.json, blocked.json, summary.json

기존 공통 fixture의 HTTP는 requests.json에 기록된다. responses.json은 추가 실행기의 직접 호출만 담는다.
실행 준비만 통과한 것을 실제 HTTP·MySQL PASS로 세지 않는다. 비밀값은 프로세스 메모리에만 두며
JWT·비밀번호·원문 서버 출력은 증거로 기록하지 않는다.
최종 기계 판독 요약은 Git 추적 대상 [ccra-stage5-http-20261022.json](../../validation/ccra-stage5-http-20261022.json)에 있다.
처음 도구 실패와 준비 실패도 삭제하지 않으며, 현재 서비스 결함과 구분한 재현·수정 근거는 보고서 5절을 따른다.
