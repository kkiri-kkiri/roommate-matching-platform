# 끼리끼리 1~3단계 종합 검증

검증일: 2026-10-18 (Asia/Seoul). 검증 대상은 현재 작업 트리의 구현된 기능이다.
기존 보고서의 결론을 그대로 승계하지 않고, 테스트·빌드·MySQL·실제 HTTP/STOMP를 재실행했다.

## 1. 최종 판정

**수정 후 재검증 필요.**

요청한 매칭 중심의 필수 동시성 10항목은 실제 MySQL·두 JVM에서 통과했고,
확장한 22개 시나리오도 새 DB·새 PID로 두 번 연속 통과했다.
발견한 동작/보안 결함 4건을 수정하고 백엔드 673건·프론트 154건·양쪽 빌드를 확인했다.
그러나 요청이 전체 도메인의 1~3단계 완료 판정인 만큼, 다음을 완료로 포함하지 않는다.

1. 인스턴스별 Simple Broker는 서로 다른 JVM에 연결된 채팅 사용자 간 메시지 중계를 제공하지 않는다.
   공유 중계/연결 라우팅 요구사항을 결정하고 구현한 뒤 별도 검증해야 한다.
2. community/chat 목록의 행별 추가 조회가 남아 있고 전체 도메인의 실제 규모 성능 검증은 수행하지 않았다.
3. auth/user/calendar의 전체 다중 프로세스 장애·강제종료·커밋 응답 유실 행렬은 재실행하지 않았다.
   이번 검증의 정상 처리/권한/경합/lock timeout/제약 롤백 성공으로 대체하지 않는다.

따라서 **매칭 필수 검증 및 수정 회귀는 통과**, **전 도메인 1~3단계 일괄 완료 선언은 보류**다.
환경은 실제 MySQL·두 JVM 검증이 가능했으므로 “검증 환경 문제로 완료 불가” 판정도 아니다.

운영 배포, 운영 DB·`kkiri_kkiri` 개발 DB 접속/변경, 실제 AI Gateway/SMTP 호출,
Git commit/push/merge는 수행하지 않았다. 정상 UI를 변경하지 않았다.

## 2. 사전 분석 및 기존 변경 보존

- 루트 AGENTS.md, backend/agent.md, API.md, ERD.md, DEPLOYMENT.md,
  기존 MATCHING_VALIDATION.md 및 기존 도메인별 MySQL 도구를 확인했다.
- 시작 시 이미 수정된 추적 파일 17개와 미추적 파일 11개가 있었다.
  기존 변경에는 matching의 결함 5건, AI limiter/회귀 테스트, MyPage와 검색 UI 수정이 포함돼 있었다.
  이 변경들을 초기화하거나 덮어쓰지 않았다. matching 서비스에 추가한 사용자 잠금은 그 위의 변경이다.
- 시작 상태: `build/matching-validation/before-status.txt`,
  추적 파일의 기존 diff: `build/matching-validation/before.diff`.
  미추적 파일도 작업 트리에 보존했다. 이 문서는 이번 세션의 추가 변경과 기존 변경을 구분한다.
- 기존 보고서는 MySQL quota 검증이 **단일 JVM의 독립 객체**였다고 명시했다.
  이를 다중 JVM 통과 증거로 계산하지 않았다.
- 기존의 “MySQL 21개”는 **업무 시나리오 21개**다. 측정/정리 검사 2개를 포함하면
  evidence.json의 PASS 기록은 23개다. 23개 업무 테스트로 부풀리지 않는다.
- Gradle test는 test 프로필/H2 메모리 DB를 강제한다. 이 안전 설정을 바꾸지 않았다.
  Testcontainers 의존성은 없고, 전용 Docker MySQL + opt-in Java/PowerShell 도구가 있었다.
- 예전 auth/user/calendar 다중 JVM 도구 중에는 이전 컨테이너 ID·UUID·포트에 고정된 것이 있다.
  현재 컨테이너로 바꿔 끼워 기존 검증을 통과 처리하지 않았다.
  새 도구는 현재 확인한 Survey 전용 컨테이너의 격리 DB를 사용한다.

## 3. 도메인별 코드 검토와 현재 검증

전 도메인의 파일/테스트 구성을 확인하고 Controller/DTO 계약, 주요 Service/Repository,
인증 설정 및 매칭에 직접 연결된 호출부를 대조했다. 행별 전수 감사나 모든 API 조합의 E2E 완료를 뜻하지 않는다.

| 도메인 | 검토 내용·판정 | 현재 실행 증거 / 다음 단계의 경계 |
|---|---|---|
| auth | Principal 기반 식별, JWT 활성 사용자 확인, DB 공유 제한, 독립 quota 트랜잭션, 메일 오류 분류 확인. 예전 agent.md의 “인증 시도 제한 없음”은 과거 이력으로 현재 구현과 다름 | 37건; 두 JVM 로그인 토큰 공유·24개 동시 실패 요청. 실제 SMTP·프록시 장애/복구 전체 행렬은 별도 |
| user/profile | 사용자 잠금 후 최신 프로필 검증, PATCH null 유지, 명시적 전공 해제, 학부/전공 소속, 변경 후 MatchingInputsChanged 확인 | 19건; 설문/회원 경합 H2·기존 MySQL 및 두 JVM 닉네임 변경 후 상세/관심 조회 |
| survey | 사용자 공통 잠금을 첫 DB 읽기로 획득, parent/option 대체 저장, 자기소개 null/빈 문자열 의미, AFTER_COMMIT 갱신 확인 | 66건; MySQL 부모 UNIQUE·옵션 무결성·flush 후 롤백·상태 변경 경합·두 JVM 동시 저장 |
| matching | 점수 준비/읽기 트랜잭션 분리, 정렬된 사용자 잠금, 최신 입력 계산, 제한된 배치·일괄 조회, 최종 페이지/count/DTO 스냅샷 확인 | 62건에 AI 포함; 동시 조회·설문 커밋 겹침·페이지/비활성 필터·추천 상위 9명 필터 MySQL |
| profile like | UNIQUE만으로 막히지 않던 취소 경합 발견. 등록·취소 양쪽에서 요청자 계정 잠금을 선행하도록 수정 | 두 JVM 2/8/32 중복 등록 및 32개 등록·취소 경합; 최종 행 수와 양쪽 조회의 liked 확인 |
| AI | 사용자별 MySQL quota와 JVM별 semaphore 범위 분리, Gateway 동안 DB 트랜잭션 중단, 실패에도 permit 반환·quota 보존 확인 | H2/MVC/Gateway 테스트 및 실제 로컬 HTTP stub; 공유 승인 12/32, 각 JVM 용량 2, 합산 4 |
| chat/WebSocket | 고정 개수의 검사/갱신 경합, 연결 후 인증 재검증·기존 구독 전달 검사 누락 수정 | chat 145건 + global 인터셉터 회귀; MySQL pin 경합; 실제 STOMP 활성/탈퇴/만료/정지 검증 |
| community post/comment | DTO/소유권, 활성 콘텐츠, 좋아요 잠금, 익명 작성자 처리, 이미지 유지 규칙 확인. 공개 익명성 계약을 임의 변경하지 않음 | 122건; MySQL 16개 중복 좋아요·댓글 수정·타인 삭제 차단. 대규모 목록/이미지 스토리지 부하는 별도 |
| report | 선조회 중복 검사와 INSERT 사이의 경합 발견. 신고자 계정 잠금 후 중복 검사/저장하도록 수정 | 50건 + 새 통합 테스트; MySQL 16개 동일 신고에서 한 행, 다른 신고자는 독립 허용 |
| admin | 서비스 관리자 검증, 회원 상태 변경·신고 처리와 관련 조회 확인 | 77건; 전체 관리자 화면/실제 배포 역할 전환 E2E는 4단계 |
| calendar/crawler | USER→Calendar 잠금 순서, 소유권, 시간 검증, 독립 실행 이력/업무 롤백, 비활성 스케줄 설정 확인 | 30건; MySQL 두 JVM private 일정·설정 8개 경합. 실제 학교 수집·장애/강제종료 행렬은 별도 |
| global/config | 기본/prod validate·SQL never, local/test 분리, 테스트 H2 강제, Validation/Jackson/공통 오류 응답 확인 | global 64건 + application 1건; 문서 선언 일치 검사; 실제 프록시/운영 DDL은 미접속 |

N+1 검토: matching은 페이지 대상 프로필/설문/점수/관심을 일괄 조회하고,
추천 필터는 상위 9명에 한정한다. 기존 H2 bounded 조회 검증과 실제 MySQL 인덱스/대표 EXPLAIN을 재실행했다.
반면 community의 목록 DTO 조립과 chat의 방별 상대 정보에는 행별 추가 조회가 남아 있다.
이를 전체 도메인 N+1 해소 또는 운영 성능 검증 완료라고 주장하지 않는다.
4단계에서 실제 목록 크기의 SQL 수·지연을 측정하고 필요한 최적화 범위를 정해야 한다.

## 4. 새로 확인하여 수정한 결함과 변경 파일

| 결함 | 원인·영향 | 수정 | 회귀 근거 |
|---|---|---|---|
| 관심 등록/취소 동시 경합 | 같은 관심 행을 여러 취소 요청이 읽고 delete. 첫 취소 후 다른 flush가 stale row로 HTTP409. POST와 DELETE의 처리 순서도 원자적이지 않음 | RoommateMatchingService의 두 쓰기 경로에서 `findByIdForAccountUpdate`를 첫 읽기로 호출. 관계 없음의 기존 400 유지 | 실제 첫 MySQL run에서 409 재현, 수정 후 동일 32개 요청 시나리오 통과 |
| 채팅 동시 고정 상한 초과 | 여러 방이 count<2를 동시에 읽고 각각 pinned=true 저장 가능 | ChatService.updateSettings에서 첫 조회 전에 사용자 계정 잠금·활성 상태 확인 | H2 3개 병렬 핀에서 2개 성공/1개 거절·실패한 mute 롤백, MySQL 두 JVM 동일 조건 |
| 중복 신고 동시 저장 | exists 검사 뒤 INSERT 사이의 경합. report 테이블에는 해당 복합 UNIQUE가 없음 | ReportService가 신고자 계정을 먼저 잠그고 활성 검증 후 기존 중복 검사 재사용. 스키마 변경 없음 | H2 8개, 두 JVM MySQL 16개에서 정확히 한 행·다른 신고자 독립 |
| 연결 이후 STOMP 권한 만료/철회 | CONNECT의 Authentication을 계속 신뢰하고 기존 구독 전달에는 권한 확인이 없음 | ChatStompInterceptor가 세션 토큰을 서버 메모리에서 관리, SEND/SUBSCRIBE 재인증, outbound executor의 실제 handler 직전 재인증/참여 검사, 실패 시 수신자 전달 차단. WebSocketConfig outbound 등록 | 단위 6건 추가; 실제 WebSocket에서 탈퇴/만료/계정 정지 후 구독 전달 차단, 정지 후 SEND 미저장 |

이번 세션에서 수정한 제품 파일:

- `src/main/java/com/kkirikkiri/matching/service/RoommateMatchingService.java`
- `src/main/java/com/kkirikkiri/chat/service/ChatService.java`
- `src/main/java/com/kkirikkiri/report/service/ReportService.java`
- `src/main/java/com/kkirikkiri/global/config/ChatStompInterceptor.java`
- `src/main/java/com/kkirikkiri/global/config/WebSocketConfig.java`

테스트/도구/문서:

- `src/test/java/com/kkirikkiri/chat/service/SharedAccountConcurrencyIntegrationTest.java` (4건 추가)
- 기존 ChatServiceTest·ReportServiceTest의 잠금 조회 mock 조정. 기존 assertion을 제거하지 않음
- `src/test/java/com/kkirikkiri/global/config/ChatStompInterceptorTest.java` (6건 추가·연결 fixture 보완)
- `tools/matching-mysql-multi-instance/`의 runner·Support·Server·Instance·ExtraChecks·RUNBOOK
- API.md, DEPLOYMENT.md, 이 보고서와 검증 증거 요약

기존 matching 결함 5건과 프론트 수정 파일은 MATCHING_VALIDATION.md에 구분돼 있으며,
이번 세션에서 해당 프론트 UI/로직을 새로 수정하지 않았다. 새 DTO/API/Entity/마이그레이션은 없다.

## 5. 단위·통합 테스트와 빌드

| 실행 | 명령 | 결과 |
|---|---|---|
| 최초 백엔드 재현 | backend에서 `.\gradlew.bat test bootJar --rerun-tasks` | 53개 suite, **663건**, 실패/오류/skip 0; bootJar 성공 |
| 수정 후 최종 백엔드 | backend에서 `.\gradlew.bat test bootJar` | 54개 suite, **673건**, 실패/오류/skip 0; bootJar 성공 |
| 프론트 전체 | frontend에서 `$env:CI='true'; npm.cmd test -- --watch=false --runInBand` | 17개 suite, **154건**, 실패 0 |
| 프론트 빌드 | frontend에서 `npm.cmd run build` | Compiled successfully |
| 계약 | 루트에서 `node backend/tools/sync-contract-docs.cjs --check` | **70 endpoints, 71 record DTOs, 29 entities** |
| diff 검사 | 루트에서 `git diff --check` | 성공 |

최종 JUnit XML을 집계했다. 도메인별 건수는 위 표의 77+37+30+145+122+64+62+50+66+19+1=673이다.
단위·MVC·H2 통합이 혼재한 건수이며 MySQL 건수로 취급하지 않는다.
신규 H2 통합은 실제 트랜잭션과 병렬 작업을 사용하고, 업무 롤백 뒤 report/settings가 남지 않는 것과
정지된 사용자의 쓰기 거절도 검사한다. 테스트 비활성화/skip/검증 완화는 하지 않았다.

로그: `build/matching-validation/stages-backend.log`, `stages-backend-final.log`,
`stages-frontend.log`, `stages-frontend-build.log`.
Windows PowerShell은 native stderr의 컴파일 안내/테스트 PASS 문구를 NativeCommandError 형식으로
로그에 감싸기도 한다. 이 문구만으로 판정하지 않고 프로세스 exit code·BUILD SUCCESSFUL·JUnit/Jest 결과를 확인했다.

## 6. 실제 MySQL 환경과 두 JVM 증거

Docker에서 실행 중인 `kkiri-survey-mysql-test`의 지정 이미지/라벨/loopback 포트/UUID를 확인했다.
실제 엔진은 **MySQL 8.0.46 / InnoDB**, REPEATABLE READ 기본 격리다.
JDBC 세션은 UTC이며, 실제 서버 JVM은 Asia/Seoul이다.
새 검증은 별도 DB `kkiri_matching_mi_<nonce>`와 그 DB만 허용한 계정을 실행마다 새로 생성한다.
기존 `kkirisurveytest`는 기존 도구의 재현에만 사용하고, 운영/개발 DB를 대상으로 선택하는 옵션은 없다.

새 빈 DB에만 Entity 기반 create-only DDL과 auth quota 키 ascii_bin을 준비한다.
두 Boot 서버는 별도 Java OS 프로세스이며 **18081 / 18082**를 사용한다.
서버는 validate·SQL never·시드 NONE으로 시작하고 수집 스케줄을 제거한다.
테스트 프로필의 외부 DB 금지 검사를 완화하지 않고 별도 opt-in 실행 경로를 사용한다.
실제 Gateway client는 로컬 HTTP stub을 호출한다. 실제 SMTP는 호출 불가능한 대체 빈이다.

첫 전체 성공 run:

- run ID: `run-9ed92cce-c9ca-4f03-8453-a01bc9390207`
- DB: `kkiri_matching_mi_cd3129ccd232`
- JVM 1: PID **34380**, port **18081**, JDBC ready connection **124**
- JVM 2: PID **5556**, port **18082**, JDBC ready connection **141**
- 양쪽 server UUID: `a087ec36-ca16-11f1-b5e2-de405dffbee0`
- **22개 PASS, HTTP 278개**, 프로세스 모두 종료, 전체 테이블 0행, DB/계정 회수 성공

부하 조건: Windows 단일 호스트·같은 Docker MySQL, JVM당 Hikari 최대 16개,
latch로 동시 요청 시작, MySQL lock wait 3초, HTTP timeout 35초.
설문 2/4/8, 관심 중복 등록 2/8/32, 혼합 등록·취소 32, 매칭 16,
quota 32, 로그인 24, 신고 16, 채팅 pin 3, 일정 설정 8개 병렬 요청을 사용했다.
짧은 로컬 경합 검증이며 장시간 처리량·최대 수용량·운영 SLA 시험이 아니다.

AI는 창 경계로 결과가 변하지 않도록 검증에서 **12회/3600초**, JVM당 동시 **2개**로 고정했다.
운영 기본은 기존 **12회/60초**다. 503 용량 거절도 이미 예약된 quota를 소비한다.
200+503=12, 429=20, DB request_count=12를 함께 확인했다.
실제 stub에서 동시 4개를 관찰했다. 배포 전체 공통 상한 2라고 주장하지 않는다.

## 7. 시나리오별 기대 조건과 판정

아래 조건은 도구의 각 test 호출에 실행 전에 선언돼 있다. HTTP 상태뿐 아니라 DB 상태/후속 조회를 검사한다.

| 사용자 필수 항목 | 기대 결과 / 객관적 검사 | 첫 전체 성공 run |
|---|---|---|
| 1. 동일 사용자 설문 동시 수정 | 2/4/8개 모두 200; parent 1개; 옵션 수 3 또는 4; 답변/소개가 한 요청과 완전히 일치 | PASS |
| 2. 관심 중복 등록 | 2/8/32개 모두 200; UNIQUE 대상 행 정확히 1개 | PASS |
| 3. 관심 등록·취소 경합 | POST 200, DELETE 200 또는 관계 없음 400; 409/5xx 없음; 최종 0/1행과 두 서버 liked 일치 | PASS, 최종 1행 |
| 4. 양쪽 JVM 매칭 조회 | 16개 200; 점수 모두 동일; DB pair 1행·점수 일치 | PASS, 점수 82 |
| 5. 설문 수정·매칭 조회 겹침 | A 설문 flush 후 gate에서 B 조회가 대기; commit 후 답변/소개/점수가 최신 일치 | PASS, 점수 82→71 |
| 6. 사용자별 AI quota 경합 | 32개 중 승인 12, 429 20; DB request_count=12 | PASS, 200 2·503 10·429 20 |
| 7. 다중 JVM 동일 사용자 AI 동시 요청 | JVM당 2개 실행, 합산 4개; 각 JVM 추가 요청 503; 예약량 6 | PASS |
| 8. DB 충돌·롤백 | 의도한 사용자 lock timeout은 HTTP409; 프로필/설문/옵션 snapshot 불변; 다음 요청 200; UNIQUE/FK 실패 후 score 보존 | PASS, SQL1205 1회·제약 실패 2회 |
| 9. 페이지·비활성 필터 | guest 13명/member 12명; 4개씩 페이지 이동, 중복 없음, total 일치, 정지/탈퇴 제외 | PASS |
| 10. 실패 후 무결성 | 잘못된 입력/자기 관심/없는 상대 거절; Gateway 형식 오류 502 후 양쪽 다음 요청 성공; quota는 4 유지 | PASS |

전체 22개에는 교차 JVM 로그인/Principal, invalid/missing/self 입력,
auth 공유 실패 제한, community/일정, report/pin 경합과 실제 STOMP 회귀도 포함된다.
정확한 케이스 이름/기대값/실제값/수행 시간은 `cases.json`에 있다.

첫 성공 run의 실제 STOMP는 한 JVM에 접속한 방의 두 연결로 정상 전달을 먼저 확인한 뒤,
다른 JVM의 REST/공유 DB에서 참여 탈퇴·계정 정지를 반영하여 **기존 구독**의 전달 차단을 확인했다.
별도로 연결 시 유효했던 JWT가 만료된 뒤 수신 차단도 확인했다.
두 JVM 사이의 메시지 중계 성공을 증명하는 테스트는 아니다.

## 8. 실패 기록과 반복 재현성

- 최초 신규 도구 실행의 mode 인자 분기 오류는 도구 오류였다. 서버 구동 전 실패했고 생성한 DB/계정은 회수했다.
- 다음 run에서는 없는 API의 403/404 기대 차이, quota 컬럼명(request_count),
  비동기 갱신이 끝나기 전 score 비교, 자기 참조 FK 정리 순서라는 **검증 도구 결함**도 확인해 수정했다.
- 같은 run에서 관심 취소의 stale row/409는 **제품 결함**으로 구분하여 수정했다.
- 중간 컴파일/단위 회귀 실패도 최종 성공과 구분한다. 검증 기준을 완화해서 통과시키지 않았다.
  없는 라우트는 실제 SecurityConfig의 denyAll에 따라 403이 맞아 기대 계약을 바로잡았다.
- SQL 로그의 vendorCode/SQLState/예외 유형을 수집했다.
  첫 전체 성공 run의 서버 SQL 실패는 의도한 lock timeout 1205 1회다. 데드락 1213은 관찰하지 않았다.
  “어떠한 부하에서도 데드락 없음” 또는 전 DB 장애/커밋 응답 유실 복구를 증명하는 결과가 아니다.

반복 run: `run-f6f9c89f-86fe-47ce-a1a4-c9e6ca5ea6d4`,
DB `kkiri_matching_mi_7e6bbfa0087d`, PID **42948 / 43692**.
같은 server UUID에서 새 DB/계정과 새 Java 프로세스로 **22개 PASS, HTTP 278개**를 재현했다.
두 프로세스 종료, 전체 테이블 0행, 생성한 DB/계정 회수를 다시 확인했다.
SQL 실패는 의도한 1205 한 건이며, 1213은 관찰하지 않았다.

최종 기존 MySQL run: `run-1d596ec6-6a3a-441e-9767-b4bf0bc76906`.
업무 21개+측정/정리 2개, **23 PASS / failed 0**, `passed=true`.
120명 추가 상태에서 추천 필터 단일 호출은 321ms였다. 첫 재현은 261ms였다.
로컬의 개별 측정이며 운영 SLA나 반복 부하 통계로 일반화하지 않는다.

실행 결과는 [증거 요약](validation/stages-1-3-evidence.json)에 저장했다.
각 run의 기대값/실제값, 포트별 HTTP 결과, SQL 실패 집계, 정리 결과,
최종 제품 소스·테스트·도구·로그의 SHA-256을 포함한다.

## 9. 미검증·잔여 위험·정책 보류

| 구분 | 남은 항목 | 처리 |
|---|---|---|
| 미검증 | 실운영 DB/기존 데이터·수동 마이그레이션 적용·실제 실행계획·백업/복구 | 요청 제약대로 접속/적용하지 않음. 새 격리 스키마의 결과와 구분 |
| 미검증 | 실제 유료 Gateway·SMTP·메일 도달·외부 장애·프록시/TLS/CORS/스토리지/브라우저 | 외부 transport는 stub/mock. 4단계·운영 준비에서 별도 검증 |
| 구조적 한계 | 현재 Simple Broker는 인스턴스별. 서로 다른 JVM에 연결된 두 사용자의 실시간 채팅 중계 | 공유 broker/라우팅 요구사항 확정과 별도 구현·통합 검증 필요. 이번에 외부 인프라 추가 안 함 |
| 범위 차이 | AI quota는 DB 공유, 동시 실행은 JVM별 | 관찰 합산 4개. 공통 배포 상한은 Gateway/ingress 등에서 결정할 정책 |
| 성능 위험 | community/chat의 행별 추가 조회, 전체 후보에 비례하는 추천 score 준비, 운영 데이터 규모 | 4단계에서 SQL 수·p95 지연·pool/CPU/배치 비용을 측정. 모든 N+1 제거로 보고하지 않음 |
| 미검증 | 프로세스 강제종료 중 quota/업무 처리, DB 전체 장애·네트워크 단절·commit acknowledgement 유실 | 이번 두 JVM의 정상 종료·lock 충돌·제약 롤백과 다른 시험이다. 전체 장애 행렬은 별도 |
| 정책 보류 | 익명 게시글 author.id의 비공개 수준, 기숙사 수집기, 클라이언트 자체 점수 기능의 서버 이관 | 기존 API·미확정 정책을 임의 구현하지 않음. 현재 미구현 설계와 회귀를 구분 |
| 기존 경계 | 최종 읽기 스냅샷 이후 상태 변경, 페이지 이동 중 새 점수의 갱신 | 기존 MATCHING_VALIDATION.md/API.md의 경계를 유지 |

## 10. 4단계 통합 테스트 진입

프론트의 실제 브라우저→두 백엔드→MySQL 흐름, 세션 전환·권한 철회,
설문/프로필 변경→추천/관심/AI 연쇄, 프록시/배포 라우팅 및 실제 규모 성능을 다음 단계로 삼는다.
서로 다른 JVM의 채팅 연결 간 메시지 전달은 현재 Simple Broker로 지원되지 않으므로,
해당 요구사항은 별도 결정/구현 전까지 전체 기능 성공으로 판정할 수 없다.
단위·격리 통합 검증의 성공을 운영 배포 승인이나 모든 E2E 완료로 해석하지 않는다.

**매칭/설문/프로필/관심/AI의 4단계 통합 테스트 준비와 제한된 실행은 가능하다.**
전체 플랫폼의 다중 인스턴스 채팅까지 성공을 전제로 하는 진입 승인은 보류한다.
기존 Simple Broker를 공유 broker로 바꾸거나 새 인프라/라이브러리를 추가하는 정책은 임의 확정하지 않았다.
위 1절의 잔여 구현/검증 범위를 완료한 뒤 전체 1~3단계 판정을 다시 내려야 한다.

## 11. Windows PowerShell 재현 순서

실제 동작 명령과 모든 exit code 확인, 최초 전용 컨테이너 생성 옵션, 로그/증거 확인은
[RUNBOOK](tools/matching-mysql-multi-instance/RUNBOOK.md)의 순서대로 실행한다.
백엔드 전체 → 프론트 전체/빌드 → 계약 검사 → 기존 MySQL → 새 두 JVM 검증 2회 순서다.
서버를 수동으로 별도 띄울 필요 없이 runner가 18081/18082에서 시작하고 정리한다.

## 12. 증거 파일

- 전체 백엔드 JUnit XML: `build/test-results/test/`
- 기존 MySQL 재현: `build/survey-mysql-concurrency/run-9baa08e6-eef1-470e-8b8e-6f9474d4d7ba/`
- 새 두 JVM 첫 전체 성공: `build/matching-mysql-multi-instance/run-9ed92cce-c9ca-4f03-8453-a01bc9390207/`
- 새 두 JVM 반복 성공: `build/matching-mysql-multi-instance/run-f6f9c89f-86fe-47ce-a1a4-c9e6ca5ea6d4/`
- 최종 기존 MySQL 회귀: `build/survey-mysql-concurrency/run-1d596ec6-6a3a-441e-9767-b4bf0bc76906/`
- 실패한 신규 MySQL run: `build/matching-mysql-multi-instance/run-df998ba8-5672-4141-8b97-d93015f93aa1/`
- raw 결과는 기존 정책대로 Git 제외 build 아래 보존한다. 최종 요약/해시는 추적 대상
  [stages-1-3-evidence.json](validation/stages-1-3-evidence.json)에 기록했다.
