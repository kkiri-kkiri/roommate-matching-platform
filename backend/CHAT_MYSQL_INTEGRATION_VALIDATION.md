# 채팅 최종 MySQL 통합 검증 결과

검증일: 2026-10-19~20 (Asia/Seoul). 브랜치: `test/chat-validation`.
사용자가 승인한 신규 DB 2개·전용 계정만 생성해 검증했다. **운영 배포 판정은 보류**다.
실제 MySQL의 핵심 정합성·회귀는 확인했지만 목록 조회 성능 및 인덱스 롤백 안내에 보완이 필요하다.

2026-10-20 후속 코드/롤백 안내 보완과 이번 검증 상태는
[후속 보고서](CHAT_MYSQL_QUERY_INDEX_IMPROVEMENT.md)를 따른다. 아래 결과는 당시 실행의
기록이며, 후속 변경의 MySQL 통과 결과로 대체하지 않는다.

2026-10-20 배포 결정 갱신: 사용자가 팀원 확인 후 Spring Boot 백엔드 1개(단일 JVM) 배포를
확정해 문제 2는 **단일 JVM 조건부 위험 수용**으로 처리한다. Simple Broker를 유지하며 외부
브로커는 도입하지 않는다. **실제 단일 JVM WebSocket 전달은 미검증**이다. 세부 조건·향후
다중 JVM 확장 개선은 [배포 보고서](CHAT_DEPLOYMENT_API_VALIDATION.md)를 따른다. 아래 MySQL
실행 이력은 보존하며 이번 배포 결정 갱신에서 SQL이나 메시지 전달 검증을 실행하지 않았다.

2026-10-20 인증 후속: [1045 진단·복구 보고서](CHAT_MYSQL_AUTH_RECOVERY.md)에 사용자 승인에 따른
테스트 전용 계정 비밀번호 재설정·DPAPI 보관 및 두 테스트 DB 로그인/권한 확인을 기록했다.
아래 원래 실행의 memory-only 보관·테스트 결과는 과거 기록으로 유지한다. 이번 인증 복구에서
성능 테스트나 마이그레이션을 재실행하지 않았다.

이후 별도 요청으로 수행한 실제 MySQL 성능/인덱스 cycle/회귀의 최신 결과는
[최종 재검증](CHAT_MYSQL_FINAL_REVALIDATION.md)에 기록했다. 아래 49회 실행·7 FAIL은 당시
기록이며 이번 47회 실행·46 PASS/1 FAIL과 합쳐 전부 통과로 표시하지 않는다.

## 1. 작업 결과

- **작업 결과: 완료** — 승인된 환경 생성·검증 실행·결과 보고를 마쳤다.
  **검증 판정: 전체 통과 아님** — 성능 실패와 아래의 보완 항목을 유지하며 운영 코드 수정은 별도 승인 대상이다.
- 실제 MySQL 8.0.44/InnoDB/RR에서 현재 ChatService·Repository·Entity의 트랜잭션을 실행했다.
- 참여 상태 덮어쓰기, 정확한 메시지 ID 읽음, 지연 커밋, pin 2개 제한, rollback·lock timeout을 검증했다.
- 메시지/방 페이지, 커서, Unicode, 익명성, 제약조건, 구 스키마 마이그레이션·실패 복구를 검증했다.
- 후보 인덱스 전후 동일 합성 데이터의 SQL·시간·EXPLAIN을 비교했다. 인덱스 전 성능 실패는 그대로 남겼다.
- 운영 코드, 기존 마이그레이션 SQL·설명, API/DB 계약은 이번에 변경하지 않았다.
  최초 MySQL 실행의 hash와 대조한 채팅 main 소스·마이그레이션 **44개 파일이 동일**하다.
- 새 파일: 이 보고서와 `tools/chat-mysql-validation/`의 `run.ps1`, Java 4개, `RUNBOOK.md`.
  기존 미커밋 변경을 유지했으며 commit/push/merge는 수행하지 않았다.

## 2. 환경 및 데이터 보호

| 항목 | 확인 결과 |
| --- | --- |
| 서버 | DESKTOP-8K385BA / 127.0.0.1:3306 / MySQL 8.0.44 |
| server UUID | fc8cfa74-c4e9-11f0-bf28-00a554698b2d |
| 현재 스키마 검증 DB | `kkirichatvalidation202610191135f9f3` |
| 구 스키마 검증 DB | `kkirichatlegacy202610191135f9f3` |
| 전용 계정 | `kkirichattest_1135f9f3@127.0.0.1` |
| 생성 시각 | 2026-10-19 23:59:29 +09:00 |
| 문자셋 / collation | utf8mb4 / utf8mb4_0900_ai_ci |
| 엔진 / 격리 | InnoDB / REPEATABLE-READ |
| SQL mode | ONLY_FULL_GROUP_BY, STRICT_TRANS_TABLES, NO_ZERO_IN_DATE, NO_ZERO_DATE, ERROR_FOR_DIVISION_BY_ZERO, NO_ENGINE_SUBSTITUTION |
| 실행 | Java 21.0.11 / Windows / JVM에서 보이는 논리 프로세서 16개 |
| 부하 제한 | 풀 최대 4개 / 동시 작업 2개 / JVM heap 최대 512 MiB |
| 테스트 세션 제한 | 일반 SELECT 3초, InnoDB lock wait 3초, latch 8초, future 15초 |
| 종료 후 서버 GLOBAL 값 | lock wait 50초 / max_execution_time 0 / REPEATABLE-READ |
| 디스크 | 시작·종료 조회 시 C: 약 8.4 GiB 여유 |
| Docker | 엔진 연결 실패. 기존 컨테이너 존재·상태 확인 불가. 시작·변경하지 않음 |

생성 전 DB 두 이름과 계정명의 부재를 조회했다. CREATE에 IF NOT EXISTS를 사용하지 않아 충돌 시 덮어쓰지 않는다.
DB명은 영문 소문자·숫자로만 만들어 GRANT의 `_`/`%` 패턴 문제를 피했다.

관리자 연결은 이름 확인 및 승인된 CREATE DATABASE/USER·GRANT에만 쓰고,
업무 fixture·마이그레이션·동시성 검사는 **새 계정**으로 수행했다.
전용 계정의 실제 JDBC URL/선택 schema/current_user/current_role/서버 UUID/SHOW GRANTS를
각 컨텍스트의 쓰기 전에 검증했다. 역할은 NONE이고, 두 DB 각각에
SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES만 있다.
전역 USAGE는 전역 데이터 접근 권한이 아니다. GRANT OPTION·기존 DB 권한은 없다.
보호 DB 두 곳의 `SELECT ... LIMIT 0`이 권한 오류로 거절되는 것도 확인했다. 쓰기로 권한을 시험하지 않았다.

기존 `kkirikkiri`, `kkiri_kkiri`에는 서버·테이블·컬럼·인덱스 메타데이터 조회만 수행했다.
기존 업무 데이터를 복사하지 않았고 DML/DDL/초기화/마이그레이션을 실행하지 않았다.
종료 후에도 기존 DB의 후보 복합 인덱스 3개는 없다. 기존 사용자·GLOBAL 설정·Docker를 변경하지 않았다.
테스트의 timeout 설정은 신규 계정의 세션에만 적용했고 모든 검증 연결을 종료했다.

**신규 DB와 계정은 삭제하지 않고 보존했다.** 현재 DB에는 합성 메시지 17,430개·방 98개·사용자 122명,
legacy DB에는 메시지 4개·방 3개가 남는다. 비밀번호는 파일·명령 인자·보고서에 저장하지 않았으며
검증 프로세스 종료 시 메모리 환경변수를 제거했다. 재접속을 위한 비밀번호 자동 재설정도 하지 않았다.

기존 Gradle test의 H2 강제, test의 외부 DB 차단, prod validate/SQL never 설정은 유지했다.
MySQL 도구는 별도 최소 Spring 컨텍스트와 `chat-mysql-validation` 프로필을 사용한다.
애플리케이션 시드·스케줄러·SMTP·HTTP 서버를 시작하지 않았다.

## 3. 마이그레이션 검증

| 검사 | 실제 결과 |
| --- | --- |
| 빈 신규 DB Entity 스키마 생성 | 두 DB에서 성공. FK 의존 Entity 포함, InnoDB 확인 |
| 최신 Entity에 receipt ADD COLUMN 재실행 | 1060 충돌 확인. 이미 컬럼이 있는 DB에 반복 적용하면 안 됨 |
| 구 스키마 재현 | legacy DB 내부에서만 is_read 제거, 합성 데이터 보관 |
| `20261019_chat_message_receipts.sql` | 실제 파일 적용 성공 |
| 변환 경계값 | 상대 last_read_at 이전·같은 시각=true, 이후=false, 수신자 last_read_at NULL=false |
| 컬럼 | tinyint(1), NOT NULL, DEFAULT 0 확인 |
| 메시지 보존 | ID·본문(emoji 포함)·시각·방·sender 및 행 수 보존 |
| receipt 재실행 | 1060 거절, 기존 값 보존 |
| DDL 암묵 커밋 | INSERT→ALTER→ROLLBACK에서도 선행 INSERT와 새 컬럼이 남는 것을 확인 |
| UPDATE 부분 실패 | 고의 1054 오류 후 ROLLBACK, is_read 기본값만 유지·업무 데이터 보존 |
| forward recovery | 이미 완료한 ADD를 반복하지 않고 UPDATE 트랜잭션만 다시 적용해 판정 복구 |
| 익명 키 SQL | `20261011_chat_anonymous_keys.sql`의 POST/COMMENT 전환 및 두 번째 실행 불변 확인 |
| PK/UNIQUE/NOT NULL/FK | 실제 위반 요청 거절. FK 6개 확인, 참조 중 방 삭제 거절 |
| 인덱스 3개 | 현재·legacy에서 생성 및 이름/열 순서 확인. 동일 이름 재실행은 1061 |
| 인덱스 부분 실패·복구 | 완료 인덱스는 유지, 나머지 적용 가능. FK 지원 복원 후 DROP/re-create 성공 |

FK 위반은 이 계정에서 1216/1217(SQLState 23000)로 반환됐다.
최초 도구는 1452/1451만 예상해 실패했으며, 같은 의미의 구체적인 MySQL 코드 쌍을 허용하도록
검증 도구만 수정했다. 무관한 SQL 오류를 성공으로 처리하지 않는다.

**롤백 발견 사항:** 복합 인덱스가 FK 지원을 맡으면 DROP INDEX가 1553으로 거절된다.
legacy에서 실제 재현했고 `(user_id)` 지원 인덱스를 먼저 생성한 뒤 rollback을 성공시켰다.
메시지 `(chat_room_id)` 지원 인덱스도 동일 데이터 비교를 위한 후보 인덱스 제거 전에 복원했다.
보존된 테스트 DB에는 복구 확인용 선두 컬럼 인덱스가 남아 있다. 동일 열 구성의 완전 중복은 없지만
복합 인덱스와 선두 컬럼이 겹치는 항목이 있으므로 이것을 운영 권장 최종 구성으로 보지 않는다.

전체 DB 백업/복원, 대형 ALTER의 metadata lock·로그·복제 영향은 검증하지 않았다.
실제 기존 DB의 is_read는 NOT NULL이나 기본값이 미지정이다. 이 사실만으로 구현 결함이라 판정하지 않았다.

## 4. 동시성 검증

서로 다른 JDBC connection_id와 CountDownLatch로 순서를 제어했다. 단일 검증 JVM의 독립 트랜잭션이며
다중 애플리케이션 JVM 실행 결과는 아니다.

| 시나리오 | 결과 |
| --- | --- |
| stale managed read/settings → 다른 연결 leave 커밋 | 403, is_left/left_at 보존, 메시지 미읽음 유지 |
| read/settings 선점 → leave 경합 | 대기 관찰 후 순서대로 커밋, 상태 유실 없음 |
| 최신 읽음 먼저 커밋 → 오래된 managed 읽음 완료 | last_read_at 역행 없음, 조건부 UPDATE도 0행 |
| RR snapshot 이후 다른 연결의 새 메시지 커밋 | 실제 MySQL current read에서 해당 ID 확인 성공 |
| A INSERT 미커밋 → B 커밋·조회·B만 읽음 → A 커밋 | A 미읽음 1개, B 읽음 유지 |
| 같은 sent_at의 서로 다른 메시지 | ID별 읽음 독립, 시각/ID 정렬 유지 |
| 읽음 TX 미커밋 중 새 메시지 INSERT | 새 메시지 미읽음 유지 |
| 자기/다른 방/없는 ID 혼합 및 중복 확인 | 잘못된 배치 전체 거절, 중복은 멱등 |
| read/settings/leave flush 후 강제 rollback | 상태·읽음·시각 복원, 재시도 성공 |
| stale RR snapshot에서 두 방 동시 pin | USER 잠금 대기, 세 번째 pin 거절·mute rollback, 활성 pin 2개 |
| pin 방 leave 후 다른 방 pin | 슬롯 해제 및 최대 2개 유지 |
| 양쪽 참여자 동시 읽음 | 두 독립 연결에서 성공, 해당 실행에 교착 없음 |
| 실제 InnoDB lock timeout | 1205 / SQLState 40001, rollback·재시도 성공 |
| timeout의 오류 응답 매핑 | 실제 JPA 예외를 기존 Handler에 전달해 HTTP 409 확인. 네트워크 E2E는 아님 |
| 인위적으로 역순 USER 잠금 | 1213 교착 희생 TX 1개, 생존 TX 커밋·기존 계정 상태 보존 |

첫 timeout 검사는 일반 SELECT 3초 제한이 lock wait보다 먼저 발동해 3024로 실패했다.
의도적인 잠금 대기 연결에서만 SELECT 제한을 일시 해제하고 즉시 복원했다.
**InnoDB lock wait 3초, latch 8초, future 15초는 늘리지 않았다.** 재검증에서 1205를 확인했다.
인위적 교착 검사는 DB의 rollback 동작을 보여주며 모든 서비스 경로의 교착 부재를 증명하지 않는다.

## 5. 조회 및 성능

동일 합성 데이터의 1/10/60개 방, 방별 200개 메시지에서 서비스 호출을 최대 5회 반복했다.
이 비교는 HTTP·인증 시간을 제외한다. 운영 동시 부하나 p95/p99 검사가 아니다.

| 방 수 | 후보 인덱스 전 중앙값 | 인덱스 후 중앙값 (범위) | 완료 응답 SQL | JSON bytes |
| --- | --- | --- | --- | --- |
| 1 | 105.8 ms | 48.8 ms (32.8~56.6) | 6 | 489 |
| 10 | 700.2 ms | 228.0 ms (206.2~237.6) | 6 | 3,570 |
| 60 | 첫 요청 3,010.0 ms에서 중단, 중앙값 없음 | 1,760.6 ms (1,395.0~2,033.3) | 7 | 17,333 |

60개 방의 인덱스 전 요청은 `max_execution_time=3000`에서 3024로 실패했다.
보호를 위해 반복을 중단했고, 완료되지 않은 요청의 SQL 1회를 정상 응답의 횟수로 비교하지 않았다.
서버 GLOBAL max_execution_time은 0이므로 이 결과를 “운영 API가 같은 3초에 자동 실패한다”고 해석하지 않는다.
그 환경에서는 제한 없이 더 오래 실행될 수 있다.

실제 JDBC SQL·바인딩과 EXPLAIN/EXPLAIN ANALYZE를 수집했다.
SQL 수는 N+1을 벗어나 6~7회로 제한되지만 **한 SQL 내부의 최신 메시지 anti-join 비용은 크다.**
60개 방의 실측 계획에서 outer 메시지 200행×60, 내부 탐색 loops=12,000,
평균 약 101행 탐색 및 최상위 LIMIT 이전 정렬이 관찰됐다. 해당 SELECT ANALYZE는 약 1,233 ms다.
후보 인덱스로도 이 반복 탐색을 근본적으로 제거하지 못했다.

- 채팅 메시지의 주요 탐색은 인덱스를 사용한다. unread는 covering range scan이다.
- 일부 count/profile/score 및 빈 기준 테이블은 작은 테이블의 ALL scan을 선택했다.
  filesort/temporary도 남는다. 단순히 ALL이라는 이유만으로 추가 결함으로 세지 않았다.
- 후속 동일 데이터 비교는 FK 복구용 단일 컬럼 인덱스도 보존한 상태다. 최초 후보 3개 적용 직후의
  별도 5회 측정에서도 60개 방은 1,187~1,927 ms였다. 운영 최종 인덱스/통계에서 재측정해야 한다.
- 목록에서 ChatRoom/Member Entity load 0, SQL LIMIT/ORDER BY와 일괄 profile/score/unread를 확인했다.
- 완료 응답의 관측 heap 증가 최대는 약 0.5/1/2 MiB다. GC 영향이 포함되며 peak/RSS 보장은 아니다.
- 별도 3,000개 메시지를 before 커서로 31페이지에 걸쳐 조회해 중복·누락·동률 순서를 확인했다.
  1.66초는 페이지 간 20 ms 대기를 포함하며, 관측 JVM heap 사용량 약 194 MiB(상한 512 MiB)다.
- 메시지 SELECT/커서와 동등한 읽기 전용 SQL의 추가 EXPLAIN에서
  `idx_chat_message_room_latest`의 backward index scan/range를 확인했다.
  이 보조 EXPLAIN은 관리자 읽기 전용 연결로 신규 합성 DB에만 실행했고,
  서비스 회귀 자체는 전용 계정으로 실행했다. ORM의 실제 SQL 수집 결과와 구분한다.

과거 H2의 SQL 423→7 결과는 이번 MySQL 측정값이 아니다. 실제 MySQL 결과는 위와 같다.

## 6. 회귀·테스트·빌드

실제 MySQL에서 다음을 확인했다: ROOMMATE/COMMUNITY 별도 방, 생성·재사용·나간 방 거절,
익명 POST/COMMENT 상대 프로필 및 REST/broadcast senderId 마스킹, 익명 ID 추측 무시,
익명 검색의 실명 비노출, 메시지 저장/조회, read-status, 기본 50/최대 100,
초과·offset overflow 거절, before/after 계약, 새 메시지 중 과거 경계 유지,
정방향 추가 조회·literal `%_!` 검색, Unicode 2,000 emoji round-trip·2,001 거절,
빈 본문 및 비참여자 접근 차단, 방 목록 DB 검색·정렬·페이지·pin/leave·JSON 형식.

| 실제 MySQL 실행 | PASS | FAIL | 해석 |
| --- | --- | --- | --- |
| 최초 35 case | 30 | 5 | timeout 가드 간섭, 인덱스 전 목록/측정, FK 코드 예상, FK 인덱스 롤백 |
| 동일 환경 후속 9 case | 7 | 2 | 인덱스 전 성능 실패 재현, FK 코드 예상 실패 지속 |
| 남은 검사를 보완한 최종 5 case | 5 | 0 | timeout 1205/409, 현재 인덱스, 모든 물리 제약, legacy 값·인덱스 보존 |

합계는 **49 case 실행, 42 PASS / 7 FAIL 이력**이다. 최종 5/5를 “전체 MySQL 49개 전부 통과”로 보고하지 않는다.
도구 원인의 실패는 후속 결과로 구분했고, 인덱스 전 성능 실패와 rollback 안내의 보완 필요는 남긴다.
검사 케이스는 JUnit test 수와 별도다. 기존 운영 코드는 실패를 해결한다는 이유로 수정하지 않았다.

추가로 이번에 실행한 검사:

- `gradlew.bat test --tests 'com.kkirikkiri.chat.*' --tests 'com.kkirikkiri.global.config.ChatStompInterceptorTest' bootJar --rerun-tasks --console=plain`
  → **11 suite / 246개, 실패·오류·skip 0**, bootJar 성공, 7 tasks 실행, 2분 26초.
  chat 210개 + STOMP interceptor 36개다. H2와 MySQL의 결과를 합치지 않는다.
- 도구 Java 4개 최종 컴파일 성공. 마지막 문서/도구 정리 버전 전체를 새 DB에서 다시 실행한 것은 아니며,
  실제 실행 소스는 각 run의 source-hashes.json으로 구분한다.
- `node backend/tools/sync-contract-docs.cjs --check` 성공: 73 endpoints / 75 record DTOs / 30 entities.
- 이번에는 백엔드 **전체** 1,220개 및 프론트 전체 테스트·빌드를 재실행하지 않았다.
  프론트 코드 변경도 없다. 이전 보고서의 결과를 이번 실행으로 집계하지 않았다.
- 기존 프론트 단일 worker 룸메이트 timeout 3개는 이번 실행 대상이 아니므로 재현/해결했다고 하지 않는다.

## 7. 발견 사항 및 수정 제안

이번 MySQL 검증에서 새로 확인한 항목은 **Critical 0 / High 0 / Medium 2 / Low 0**이다.
당시 별도 High로 기록한 다중 JVM WebSocket 문제 2는 이 개수와 별도이며,
2026-10-20 사용자 결정에 따라 현재는 단일 JVM 조건부 위험 수용으로 분류한다.

### Medium — 최신 메시지 탐색 비용이 메시지 수와 함께 크게 증가

- 위치: `src/main/java/com/kkirikkiri/chat/repository/ChatRoomRepository.java:45`의 latest NOT EXISTS join,
  후보 인덱스 `migrations/20261019_chat_query_indexes.sql:5`.
- 원인: LIMIT 이전에 각 방의 메시지 후보와 더 최신 메시지를 반복 비교한다. SQL 개수 감소가 내부 탐색량 감소를 보장하지 않는다.
- 실제 상황: 60개 방×200개 메시지에서 인덱스 전 3초 제한 초과, 적용 후도 중앙값 1.76초.
  운영 서버 성능/SLO/동시접속 조건은 확인되지 않아 운영 장애나 지연 수치를 단정하지 않는다.
- 수정 제안: `(chat_room_id,sent_at,message_id)` 인덱스로 `ORDER BY sent_at DESC,message_id DESC LIMIT 1`의
  방별 단건 lookup을 사용하는 native 조회 등으로 후보 반복을 제거하는 안을 검토한다.
  익명 검색·pin 정렬·count·페이지 계약을 보존하고 같은 MySQL fixture/계획으로 검증한다.
  이 쿼리 변경은 **별도 운영 코드 수정 승인 후** 수행한다. 인덱스만 적용하면 해결됐다고 보고하지 않는다.

### Medium — 인덱스 rollback 안내에 FK 지원 복원 단계가 없음

- 위치: `migrations/20261019_chat_query_indexes.md:24`의 DROP INDEX 안내.
- 원인: MySQL이 새 복합 인덱스를 FK 지원에 사용하면 기존 자동 지원 인덱스가 없어지거나 더 이상 유지되지 않을 수 있다.
- 실제 상황: legacy에서 해당 복합 인덱스 DROP이 1553으로 중단됐다. 데이터 손실은 없었지만 단순 DROP만으로 복귀하지 못했다.
- 수정 제안: SHOW CREATE TABLE/SHOW INDEX로 FK 선두 컬럼의 대체 인덱스를 확인하고,
  필요한 `(user_id)`/`(chat_room_id)` 인덱스를 먼저 복원한 뒤 후보 인덱스를 제거하는 분기·검증·부분 실패 절차를 안내한다.
  새 legacy DB에서 이 복구 순서가 성공했다. 원본 마이그레이션 설명은 이번 범위에서 임의로 변경하지 않았다.

## 8. 최종 판정 및 남은 검증

**운영 배포 보류.** MySQL의 핵심 상태/읽음 정합성과 인덱스 적용 후 기능 회귀는 확인됐으나,
목록 SQL 비용과 안전한 rollback 안내를 보완·재검증해야 한다. 테스트·문서 검사만으로 운영 배포를 승인하지 않는다.
MySQL 통합 검증의 전 항목 통과 판정은 내리지 않았다. 허용된 검증 작업은 완료했으며,
운영 코드·기존 DB를 바꾸어 남은 항목을 해소하는 작업은 이번 승인에 포함하지 않는다.

- 확인하지 못한 사항: 확정된 단일 JVM 계획의 실제 운영 설정·데이터·권한·물리 DDL 호환,
  백업 전체 복원, 대형 DDL 잠금/복제, 장시간 동시 부하·p95/p99·RSS.
- 서버가 Windows/대소문자 비구분 설정인 이번 결과가 Linux의 테이블명·파일시스템 조건을 대신하지 않는다.
- 당시 앱 JVM 두 개를 실행하지 않았다. JVM 간 WebSocket 중계는 미구현이지만,
  현재 문제 2는 단일 JVM 배포 조건에 따른 위험 수용이다. 실제 단일 JVM 전달도 미검증이며,
  두 JVM 전달 검증/공유 중계 개선은 향후 확장 때 필요하다.
- 브라우저 E2E(15초 polling·재연결·scroll/read viewport·익명 UI·실제 STOMP 전달)는 별도다.
- 실제 DB에는 마이그레이션을 적용하지 않았다. 기존 is_read를 보고 ADD/변환을 재실행하면 안 된다.
  운영자가 기존 상태·백업·동등 인덱스를 확인하고 승인된 필요한 단계만 별도 적용해야 한다.
- 신규 DB·계정의 정리/삭제/비밀번호 변경은 이번 작업에서 수행하지 않았다. 후속 작업도 별도 지시가 필요하다.

실행 증거(모두 Git 미추적 build 아래):

- `build/chat-mysql-validation/202610191135f9f3.environment.json`: 신규 자원 metadata, 비밀번호 없음.
- `build/chat-mysql-validation/run-a36ab2da-8439-44a3-890c-bb6bb9cbe053/`: 최초 35 case, 초기 후보 인덱스 후 측정, 3,000개 이력.
- `build/chat-mysql-validation/run-de097290-477d-4018-afa3-646669d8b414/`: 동일 데이터 인덱스 전후 계획/측정, 후속 9 case.
- `build/chat-mysql-validation/run-ac6956ba-2b04-45c0-88bf-7d3717e63a11/`: 최종 5 case, 실제 1205 및 물리 제약 SQL 코드.
- `build/chat-mysql-validation/final-readonly-inventory.txt`, `message-explain-readonly.txt`.
- `build/test-results/test/`, `build/reports/tests/test/index.html`: 이번 H2/STOMP 246개.
