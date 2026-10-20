# MySQL 채팅 조회·인덱스 롤백 후속 작업

검토일: 2026-10-20 (Asia/Seoul). 브랜치: `test/chat-validation`, 시작 시 작업 트리 clean.
`AGENTS.md`, `backend/agent.md`, API/ERD/DEPLOYMENT, 이전 검증 결과·실행 JSON을 대조했다.

후속 실제 MySQL 결과는 [최종 재검증 보고서](CHAT_MYSQL_FINAL_REVALIDATION.md)를 따른다.
동일 조건 60개 방의 이전/수정 서비스 중앙값은 1,904.2→54.4ms, 수정 100개 방은 83.1ms였으며,
FK 지원 복원/롤백/재적용·원래 데이터 보존은 통과했다. 다만 실제 계획의 방별 정렬/반복 평가가 남아
문제 10 완전 해결은 보류한다. 아래 미측정/인증 보류는 앞선 코드 개선 작업 당시 기록으로 유지한다.

## 1. 작업 결과

**부분 완료. 운영 배포 보류.**

- 문제 10: 최신 메시지 anti-join을 인덱스 단건 탐색 형태로 교체했다. H2 회귀 및 빌드 결과는
  아래에 구분한다. 실제 MySQL 시간/EXPLAIN 재측정이 없어 성능 문제 해결 판정은 보류한다.
- 문제 11: FK 지원 복원·동등 인덱스/소유 판정·재실행·부분 실패 안내를 보완했다.
  이번 문서 버전의 MySQL 적용/롤백/재적용은 미실행이므로 실검증 완료로 판정하지 않는다.
- API/DTO·Entity·기존 인덱스 열 구성은 유지했다. 프론트·다른 도메인 코드 변경은 없다.
- DB 변경·초기화·계정 변경·서버 설정·Docker 조작·commit/push/merge는 수행하지 않았다.
- 배포 결정 추가 반영: 2026-10-20 사용자가 팀원 확인 후 Spring Boot 백엔드 1개(단일 JVM)
  계획을 확정했다. **문제 2는 단일 JVM 조건부 위험 수용**이며 Simple Broker를 유지한다.
  Redis·RabbitMQ 등 외부 브로커는 도입하지 않는다. **실제 단일 JVM WebSocket 전달은 미검증**이다.
  이번 추가 반영은 문서만 수정했으며 아래 1,222개 테스트/빌드는 앞선 조회 개선 작업의 결과다.

## 2. 원인 분석

이전 MySQL EXPLAIN ANALYZE는 60개 방×200개 메시지에서 메시지 후보 12,000행마다
더 최신 메시지를 찾는 anti-join의 내부 탐색 loops=12,000, 평균 약 101행 탐색을 기록했다.
목록 SQL 하나의 ANALYZE 약 1,233ms이며 LIMIT 이전 정렬도 있었다. SQL이 6~7개라는
사실만으로 SQL 내부의 반복 탐색 비용이 줄었다고 볼 수 없다.

InnoDB는 복합 인덱스를 FK 지원으로 채택하면서 기존 자동 생성 인덱스를 제거할 수 있다.
이전 legacy 실행에서 후보 DROP이 1553으로 거절됐다. 단순 DROP 목록에는 원래 FK 지원의
존재/복원과 모든 DROP 이후 잔존 집합 검토가 빠져 있었다. 이번에는 그 분기를 문서화했다.
MySQL 근거: [FK 공식 문서](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html).

## 3. 수정 파일

| 파일 | 변경 내용 |
| --- | --- |
| [ChatRoomRepository.java](src/main/java/com/kkirikkiri/chat/repository/ChatRoomRepository.java) | 최신 메시지 ID의 정렬/1개 제한 scalar subquery + PK join |
| [ChatRoomReadPerformanceIntegrationTest.java](src/test/java/com/kkirikkiri/chat/service/ChatRoomReadPerformanceIntegrationTest.java) | 100개 방의 SQL 상한 및 timestamp/ID 동률·페이지·ID별 읽음 회귀 추가 |
| [인덱스 안내](migrations/20261019_chat_query_indexes.md) | 메타데이터·동등/소유 판정·FK 복원·롤백·부분 실패·재적용 |
| [인덱스 SQL](migrations/20261019_chat_query_indexes.sql) | 수동 정의 유지, FK/소유/롤백 주석 추가 |
| [ChatMysqlQueryComparison.java](tools/chat-mysql-validation/ChatMysqlQueryComparison.java) | 보존 fixture의 읽기 전용 baseline/optimized 비교·계획·unread·페이지 동등성 검사 준비 |
| [ChatMysqlValidation.java](tools/chat-mysql-validation/ChatMysqlValidation.java) | validate 재사용·readOnly 풀·Inspect/QueryComparison/ExistingRegression 분기 |
| [run.ps1](tools/chat-mysql-validation/run.ps1) | 재사용 기본 Inspect, 명시적 모드 선택 |
| [RUNBOOK.md](tools/chat-mysql-validation/RUNBOOK.md) | 기존 자원/자격증명 재사용, 각 모드의 읽기/쓰기 범위와 실행 한계 |
| [이전 보고서](CHAT_MYSQL_INTEGRATION_VALIDATION.md), 이 문서 | 과거 결과 보존 및 후속 검증 상태 연결 |

## 4. 해결 방법

목록 HQL의 `latest.chatRoom = r AND NOT EXISTS(newer...)`를 다음 형태로 바꿨다.

```sql
LEFT JOIN CHAT_MESSAGE latest ON latest.message_id = (
  SELECT newest.message_id FROM CHAT_MESSAGE newest
  WHERE newest.chat_room_id = room.chat_room_id
  ORDER BY newest.sent_at DESC, newest.message_id DESC LIMIT 1
)
```

위 SQL은 설계 설명이며 이번 MySQL에서 수집한 SQL이 아니다. 실제 실행한 H2 생성 SQL은
테스트 로그에 있다. Hibernate HQL의 ORDER BY/LIMIT 지원을 사용하며 새 라이브러리는 없다.
[Hibernate HQL 문서](https://docs.jboss.org/hibernate/orm/7.0/querylanguage/html_single/Hibernate_Query_Language.html)를 참고했다.

`(chat_room_id,sent_at,message_id)`로 방을 동등 탐색한 뒤 역방향 첫 ID를 고르는 설계다.
sent_at 동률이면 message_id가 큰 메시지이며, MAX(message_id)로 대체하지 않았다.
메시지가 없는 방은 LEFT JOIN의 null을 유지한다. 필터에 맞는 방들의 최신 시각은 전체 정렬에
필요하지만 각 방의 메시지 후보를 모두 anti-join하지 않는다. 반환 페이지의 profile/survey/score,
unread batch만 조회하는 기존 Service는 변경하지 않아 N+1 경로를 추가하지 않았다.

미읽음은 기존 `(chat_room_id,is_read,sender_id)`와 개별 is_read를 사용한다.
읽음 처리·나가기·고정의 잠금/트랜잭션, 익명 판정·검색, count/filter/order/page 계약은 유지했다.
동일 시각/큰 ID의 과거 시각 메시지/빈 방/고정/다음 페이지·단건 읽음 결과를 H2에서 검증한다.

롤백은 이번에 생성한 후보만 대상으로 삼고, **DROP 예정 후보 전체를 제외한 잔존 인덱스**가
FK 선두 열을 지원하는지 먼저 확인한다. 없을 때만 기존 정의 또는 기록된 복구 이름의
`(chat_room_id)`/`(user_id)`를 먼저 복원한다. 지원 존재를 다시 확인한 뒤 하나씩 제거한다.
1061·1553·부분 CREATE/DROP 실패 후에는 완료 상태를 다시 기록하며 무조건 재실행하지 않는다.

## 5. MySQL 성능 비교

**이번 실행의 MySQL 측정값은 없다. 성능 향상률을 계산하지 않는다.**

| 규모 | 과거 인덱스 전 | 과거 인덱스 후 중앙값 | 과거 완료 SQL 수 | 이번 변경 후 |
| --- | --- | --- | --- | --- |
| 1개 방 | 105.8ms | 48.8ms | 6 | 미측정 |
| 10개 방 | 700.2ms | 228.0ms | 6 | 미측정 |
| 60개 방 | 첫 요청 3,010ms/3024, 중앙값 없음 | 1,760.6ms | 7 | 미측정 |
| 100개 방 | 미측정 | 미측정 | 미측정 | MySQL 미측정, H2 회귀 대상 추가 |

과거 값은 방별 200개 합성 메시지, 기본 페이지 크기 50, 최대 5회 서비스 호출의 기록이다.
HTTP/인증 시간은 제외하며 새 코드와 같은 시점/통계에서 비교한 값이 아니다.
과거 latest ANALYZE/covering unread 계획과 이번 변경 후 계획을 비교하는 단계가 남아 있다.

준비한 QueryComparison은 같은 기존 데이터·인덱스에서 old/new 목록 data-query를 각각
1회 예열·5회 측정하며 중앙값·실제 SQL/바인딩·EXPLAIN ANALYZE를 기록한다. 새 서비스 전체
시간/SQL 수는 별도 기록하고 data-query 시간과 혼동하지 않는다. fixture를 만들거나 인덱스를
변경하지 않는다. 기존 100개 방 fixture가 없으면 미실행으로 기록한다. 3초 제한도 유지한다.
이 도구는 컴파일만 검증했으며 실제 MySQL 계획·동등성 결과는 아직 없다.

## 6. 마이그레이션 안전성 및 환경 상태

2026-10-20 인증 후속 갱신: [인증 복구 보고서](CHAT_MYSQL_AUTH_RECOVERY.md)에 따라 사용자 승인 후
해당 테스트 계정 비밀번호만 재설정하고 두 테스트 DB 로그인·권한 확인을 완료했다. DPAPI 파일은
실제 Windows 사용자 임예명에게 묶여 있다. 아래 인증 거절·보류는 조회 개선 작업 당시 이력이다.
이번 인증 복구에서도 성능/마이그레이션은 실행하지 않아 아래 단계별 미검증 판정은 유지한다.

- 보존 manifest: `build/chat-mysql-validation/202610191135f9f3.environment.json`.
  기록상 current=`kkirichatvalidation202610191135f9f3`, legacy=`kkirichatlegacy202610191135f9f3`,
  계정=`kkirichattest_1135f9f3@127.0.0.1`, UUID=`fc8cfa74-c4e9-11f0-bf28-00a554698b2d`.
- 이전 보고서·실행 디렉터리 3개·도구 Java 4개/runner가 남아 있음을 확인했다. 별도의 미완료
  manifest/credential 파일도 남아 있지만 승인 완료 환경의 대체물로 채택하거나 삭제하지 않았다.
- 제공된 자격증명으로 전용 계정 사전 접속을 시도했으나 최종 MySQL 1045로 인증 거절됐다.
  따라서 현재 DB/계정 실존·권한·인덱스·데이터 현황을 재확인하지 못했다. manifest를 현재
  연결 성공의 증거로 사용하지 않는다. 사용자가 자격증명 확인 불가·MySQL 보류를 선택했다.
- 비밀번호는 출력·저장하지 않았고 개발/관리자 계정으로 대신 접속하지 않았다. 계정 reset,
  신규 DB/계정 생성·테스트 데이터 초기화·삭제·재생성은 수행하지 않았다.

| 단계 | 이번 결과 |
| --- | --- |
| 변경 전 실제 인덱스 목록 | 미확인: 인증 거절 |
| 적용 후 | 미실행 |
| FK 지원 복원/롤백 후 | 미실행 |
| 재적용 후 | 미실행 |
| FK 참조 무결성·기존 데이터 보존 | 이번 재검증 미실행. DB 변경 명령도 실행하지 않음 |
| 부분 실패/복구 | 절차 보완, 이번 실제 재현 미실행 |

## 7. 테스트 결과와 과거 7회 실패

이번 실행 결과:

- `cd backend; .\gradlew.bat test bootJar --console=plain` → BUILD SUCCESSFUL, 1분 43초.
  **71 suite / 1,222개, 실패·오류·skip 0**. 7 tasks 중 2 executed/5 up-to-date이며 bootJar는
  앞선 변경 후 빌드가 성공한 최신 산출물을 재사용했다. XML의 실제 개수를 집계했다.
- 위 전체 실행에 chat 212개 + ChatStompInterceptorTest 36개 = **248개 통과**가 포함된다.
  ChatRoomReadPerformanceIntegrationTest 9개에는 새 회귀 2개가 포함된다.
  읽음/나가기/고정/지연 커밋·rollback·cursor·HTTP/STOMP 계약의 기존 검사도 유지했다.
- H2에서 방 수 1/10/60/100개의 SQL 수는 **6/6/7/7**이며 반환 항목 수는 1/10/50/50이다.
  Entity load 0 및 DB 정렬/페이지·unread/점수 mapping의 기존 상한 검사도 통과했다.
  이 값은 H2의 SQL 수이며 MySQL 지연시간·인덱스 효과를 입증하지 않는다.
- 첫 변경 직후 해당 통합 suite 7개와 bootJar도 통과했다. 최종 전체 실행 1,222개와 중복 합산하지 않는다.
- `node backend/tools/sync-contract-docs.cjs --check` → 73 endpoints / 75 record DTOs / 30 entities 일치.
- `powershell -NoProfile -File backend/tools/chat-mysql-validation/run.ps1 -PrepareOnly` → Java 5개
  컴파일 성공, DB 접속 없음. MySQL 실행/쿼리 계획 검증 성공을 뜻하지 않는다.
- `git diff --check` 성공. 테스트/문서는 삭제·비활성화하지 않았다.

증거: `build/test-results/test/TEST-*.xml`, 특히
`TEST-com.kkirikkiri.chat.service.ChatRoomReadPerformanceIntegrationTest.xml`의
`CHAT_READ_PERF` 4행과 개별 testcase, `build/reports/tests/test/index.html`,
`build/libs/backend-0.0.1-SNAPSHOT.jar` 및
`build/chat-query-index-improvement/verification-summary.json`. build 아래 증거는 Git 추적 대상이 아니다.

프론트 수정이 없어 프론트 테스트/빌드는 실행 대상에서 제외했다. HTTP/STOMP 계약은
기존 Controller/DTO/STOMP 테스트와 문서 스냅샷으로 확인하며 실제 네트워크 E2E와 구분한다.

과거 `results.json` 3개를 직접 확인했다. 49 case 실행=42 PASS/7 FAIL 이력은 유지한다.

| 원인 | 과거 실패 수 | 과거 최종 재확인 | 이번 상태 |
| --- | --- | --- | --- |
| SELECT 3초가 lock wait를 먼저 중단(3024) | 1 | 의도적 대기 연결만 SELECT 가드 해제 후 1205/409·rollback PASS | MySQL 미실행 |
| 인덱스 전 목록/측정 시간 초과 | 3 | 인덱스 후 측정/목록은 PASS이나 60개 방 약 1.76초 병목 잔존 | 쿼리 교체, 실제 성능 미측정 |
| FK 오류를 1452로만 예상, 실제 1216 | 2 | 1216/1452 및 1217/1451·SQLState 검증으로 최종 물리 제약 PASS | 기존 수정 보존, 재실행 안 함 |
| FK 지원 후보 DROP 1553 | 1 | 테스트 도구의 지원 복원 후 DROP/re-create PASS | 문서 보완, 이번 전체 cycle 미실행 |

이전 최종 실행은 **5/5 PASS**이며 49개 모두 최종 재실행했다는 뜻이 아니다.
당시 인덱스 전 실패·수정 전 성능은 소급하여 PASS로 바꾸지 않는다.

## 8. 남은 문제와 다음 검증

1. 기존 전용 계정의 올바른 자격증명을 안전하게 공급하고 Inspect로 두 DB·계정/권한·현재
   인덱스/데이터를 먼저 확인한다. 없으면 신규 자원·권한 변경을 자동 수행하지 않는다.
2. QueryComparison으로 1/10/60개 기존 fixture의 old/new 행 동등성·페이지·unread·계획/시간을
   확인한다. latest 단건 인덱스 사용/실제 loops와 unread 비용을 분리한다. 100개 fixture 추가는
   별도 승인된 데이터 추가 범위를 확인한 뒤 진행하며 기존 fixture를 초기화하지 않는다.
3. legacy DB에서 소유 인덱스·FK/전체 데이터 snapshot을 보관하고 적용/지원 복원/롤백/재적용,
   참조 제약·부분 실패 복구를 수동 검증한다. 검증 전 안전하지 않은 DROP 배치를 실행하지 않는다.
4. 승인된 작은 합성 fixture 추가 범위에서 ExistingRegression의 독립 연결 동시성 검사를 실행한다.
   기존 데이터 삭제·Full 재실행으로 대신하지 않는다. 다중 프로세스 MySQL 결과와 구분한다.
5. 실제 배포 DDL/권한·Linux 대소문자·백업 전체 복구·대형 DDL 잠금/복제·장기 p95/p99,
   단일 JVM의 실제 WebSocket 양방향 전달·재연결/재구독·브라우저 E2E는 별도다.
   단일 JVM 계획과 문제 2 위험 수용은 확정했으며 모든 연결/SEND를 같은 활성 JVM에 모으고
   재배포 시에도 구·신 JVM의 활성 연결을 겹치지 않아야 한다. 단일 장애점·용량·재시작 한계를
   수용한다. 실제 배포 설정은 미확인이다. [배포 조건](DEPLOYMENT.md)을 따른다.
6. JVM 간 WebSocket 중계는 미구현이다. 다중 JVM/자동 확장/동시 활성 전환이 필요해지면
   현재 위험 수용을 재검토하고 공유 중계·커밋/장애 복구·중복/순서·기존 인가/익명성 보존을
   설계한 뒤 실제 두 JVM 전달을 검증한다. 현 단일 JVM 배포의 필수 검증과 구분한다.

추후 Community·Chat 테스트 정리 시 아래 의미의 핵심 검증과 도구를 보존한다.

- Chat: ChatReadConcurrencyIntegrationTest(leave/settings/read 경합·시각 역행·지연 커밋·pin·rollback),
  ChatMessageCursorIntegrationTest, ChatRoomReadPerformanceIntegrationTest, ChatServiceTest,
  ChatQueryControllerTest/ChatReadControllerTest/ChatWebSocketControllerTest/ChatStompInterceptorTest.
- Community: Account/Content/DetailRead/CreationAndImageConcurrencyIntegrationTest,
  PostPrivacy/PostAnonymityPolicy/AnonymousAuthorIntegrationTest, ImageLifecycle/ImageHttpIntegrationTest,
  ReadContract/ReadPerformanceIntegrationTest. 공개/익명 계약·권한·이미지 참조/rollback·쿼리 상한을 보존한다.
- 도구: chat-mysql-validation의 run.ps1·Java 기존 4개+새 QueryComparison·RUNBOOK,
  수동 migration SQL/안내, sync-contract-docs.cjs와 기존 Community MySQL 도구/실행 안내.
  fixture·plan·실패 JSON/소스 hash가 어떤 실행의 증거인지 구분하는 기록도 유지한다.

## 9. 최종 판정

코드·회귀·안내 보완과 실제 MySQL 성능/마이그레이션 검증은 구분한다. 현재 두 Medium의
**실제 MySQL 해결 확인은 보류**이며 운영 배포도 보류한다. 새 DDL 설계나 API 정책 결정은
필요하지 않았다. MySQL 재검증 후 추가 쿼리/인덱스 수정이 필요한지는 실행계획으로 판단한다.

문제 2의 최신 판정은 **단일 JVM 배포 조건에 따른 위험 수용**이다. 외부 브로커 도입 없이
현재 Simple Broker 구조를 유지한다. 이 결정으로 배포 JVM 수 미정이라는 보류 사유는 해소됐다.
실제 단일 JVM의 두 클라이언트 간 전달은 미검증이며 MySQL 성능/롤백 보류도 유지한다.
향후 다중 JVM 확장은 별도 개선·검증 대상이다. 세부 수용 조건/검증 계획은
[배포 최종 보고서](CHAT_DEPLOYMENT_API_VALIDATION.md)를 따른다.

추가로 전용 계정의 1045 인증 장애는 [승인된 인증 복구](CHAT_MYSQL_AUTH_RECOVERY.md)로 해소했다.
이는 문제 10·11의 MySQL 재측정/인덱스 cycle 통과를 뜻하지 않으며 해당 검증은 다음 작업이다.
