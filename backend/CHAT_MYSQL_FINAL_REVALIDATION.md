# 채팅 문제 10·11 MySQL 최종 재검증

검증일: 2026-10-20 (Asia/Seoul). 브랜치 `test/chat-validation`.
기존 미커밋 변경을 보존했다. 기존 보고서 3개·인덱스 수동 절차·검증 도구와 Git 상태를 확인한 뒤 실행했다.
운영 코드 수정·새 DB/계정·초기화·비밀번호/권한 변경·Docker 조작·commit/push/merge는 하지 않았다.

## 1. 작업 결과

- **문제 10: 실제 성능 개선 확인, 완전 해결 판정은 부분 완료.** 같은 데이터의 이전/수정 서비스
  중앙값은 60개 방에서 **1,904.2ms → 54.4ms(97.14% 감소)**다. 수정 방식 100개 방도 83.1ms로
  통과했다. 다만 MySQL 계획에는 방별 메시지 범위 정렬과 scalar subquery의 반복 평가가 남아 있어
  “방별 역방향 인덱스 1행 읽기·불필요 정렬 완전 제거”를 달성했다고 보고하지 않는다.
- **문제 11: 이번 격리 DB 조건에서 검증 통과.** FK 지원 복원→안전한 DROP→재적용·멱등 재실행,
  중간 실패 복구, FK 참조 검증과 기존 데이터 해시 보존을 확인했다.
- MySQL 회귀는 **19/19 PASS**. 전체 실행 이력은 사전/사후 확인·반복 비교를 포함해
  **47 case / 46 PASS / 1 FAIL**이며, FAIL은 이전 anti-join의 100개 방 첫 조회 3024다.
  이를 최종 성공으로 지우지 않는다. 수정 100개 방은 별도 실행에서 통과했다.
- **전체 무실패 통합 검증/운영 배포 승인 판정은 보류.** 잔여 계획 비용과 단일 JVM 실제
  WebSocket/프록시·브라우저 검증을 아래에서 구분한다. 새 결함을 이유로 운영 코드를 수정하지 않았다.

## 2. 성능 비교

### 조건과 비교 경계

- MySQL 8.0.44/InnoDB, REPEATABLE-READ, 127.0.0.1:3306, UUID
  `fc8cfa74-c4e9-11f0-bf28-00a554698b2d`, JVM Asia/Seoul, heap 512MiB, 풀 최대 4, 작업 스레드 2.
- 같은 서버를 사용하는 작업은 순차 실행했다. 일반 SELECT 세션 제한 3,000ms, InnoDB/metadata
  lock wait 각각 3초, latch 8초, future 15초. 성능 타임아웃은 늘리지 않았다.
- 각 성공 규모는 예열 후 **5회**, 기본 페이지 크기 50. HTTP·JWT·네트워크·JSON 직렬화 시간은 제외한
  서비스 호출 시간이다. 실제 ORM SQL·바인딩·EXPLAIN/ANALYZE를 별도 저장했다.
- 테스트 전용 Repository 대역에서 findRoomPage만 과거 anti-join HQL로 대체하고 같은 ChatService
  생성자/나머지 의존성과 read-only 트랜잭션을 사용했다. 수정 서비스와 전체 DTO 응답 동등성을 확인했다.
  운영 Repository·Service 파일을 바꾸거나 현재 쿼리를 과거 쿼리로 되돌리지 않았다.
- **과거 1.76초와 직접 개선율을 계산하지 않는다.** 과거는 방별 200개×60개였으나 현재 보존된
  60개 방 중 59개는 200개, 교체된 1개는 1개다(총 11,801, 평균 196.6833). 또한 독립 100개 방을
  추가해 전체 DB의 메시지/통계·캐시 상태가 달라졌다. 아래 전후는 이번 동일 데이터끼리의 비교다.
- 1/10개 방은 기존 방별 200개 데이터를 보존했다. 100개 방은 충돌하지 않는 새 사용자/방으로
  방별 200개·총 20,000개 메시지를 추가했다. 기존 60개 방을 변경해 과거 분포를 만들지 않았다.

### 같은 조건의 서비스 호출 중앙값

| 방 수 | 이전 SQL 수 | 수정 SQL 수 | 이전 중앙값 | 수정 중앙값 | 반복 |
| --- | --- | --- | --- | --- | --- |
| 1 | 6 | 6 | 37.8830ms | 29.5630ms | 각각 5회 |
| 10 | 6 | 6 | 202.5058ms | 26.8979ms | 각각 5회 |
| 60 | 7 | 7 | 1,904.2147ms | 54.4145ms | 각각 5회 |
| 100 | 정상 응답 미완료 | 7 | 첫 baseline 조회 3초 제한/3024, 중앙값 없음 | 83.1005ms | 이전은 첫 실패 후 중단, 수정 5회 |

60개 방 원시 시간(ms): 이전 1925.7272 / 1660.8509 / 1931.5704 / 1904.2147 / 1670.1521,
수정 76.7923 / 52.2064 / 51.2081 / 61.5743 / 54.4145.
100개 방 수정: 66.7430 / 83.1005 / 102.8717 / 75.4523 / 86.9106.
SQL 수 감소를 성능 향상 근거로 사용하지 않았다. 두 방식 모두 6~7회지만 실행 비용은 크게 다르다.

목록 data-query만의 별도 중앙값은 1개 방 20.1023→8.2208ms,
10개 방 198.9264→10.5023ms, 60개 방 1,639.8926→27.3848ms다. 서비스 전체 시간과 혼동하지 않는다.
독립 100개 방 추가 전의 최초 읽기 전용 비교도 1/10/60개 모두 통과했으며 해당 별도 결과는 아래 실행표로 구분한다.

### EXPLAIN ANALYZE와 잔여 비용

- 이전 60개 방: Nested loop antijoin, 내부 covering lookup **loops=11,801 / 평균 약 101행**.
  최초 읽기 전용 실행의 목록 ANALYZE 약 1,655ms. 과거 12,000 loops 문제와 같은 탐색 구조를 재현했다.
- 수정 60개 방: 최신 메시지 PRIMARY 단건 join, anti-join 제거. 목록 ANALYZE 약 **21.1ms**.
  `idx_chat_message_room_latest`를 사용하지만 scalar subquery는 **loops=180**, 각 loop에서 평균 약
  197개 범위를 읽고 `Sort ... LIMIT 1`을 수행했다. 역방향 1행 index scan으로 설명하면 틀리다.
- 수정 100개 방: 목록 ANALYZE 약 **42ms**, 방별 lookup **loops=300**, loop당 200행과 Top-1 Sort.
  100개 방의 이전 방식은 3024 이후 재실행/ANALYZE하지 않았다. 저장한 실제 baseline SQL의 owner
  바인딩만 100개 방 소유자로 바꾼 **비실행 EXPLAIN**을 별도 수집했다.
- 미읽음: 60개 방의 반환 페이지 50개 ID에 대해 `idx_chat_message_room_unread` covering range scan,
  2,500행·group 결과 50개·ANALYZE 약 **1.64ms**. 상대 발신·is_read=false의 개별 기록을 집계한다.
- 방 목록의 pin/최신시각/roomId Top-50 정렬은 계약상 필요하므로 남는다. 방별 메시지 Top-1 정렬과
  3회 반복 평가는 추가 최적화 대상이다. 단순히 모든 Sort/작은 기준 테이블 scan을 오류로 세지 않는다.

페이지/최근 메시지/미읽음: 1/10/60의 old/new projection과 전체 응답, 다음 페이지 동등성을 확인했다.
100개 방의 두 페이지 50+50개·100개 고유 ID·순서·metadata, 최신 synthetic-199·미읽음 50·점수 80도 확인했다.
익명성/검색/카테고리는 새 독립 fixture의 회귀 검사에서 확인했다.

## 3. 인덱스 적용·롤백·재적용 검증

대상은 **기존 legacy DB만**이다. 후보 이름/열/비고유/가시성/BTREE/전체 열 인덱스를 검사하고
과거 `run-de097.../kkirichatlegacy202610191135f9f3.indexes.json`의 소유 기록과 대조했다.
FK/PK/UNIQUE를 제거하거나 FOREIGN_KEY_CHECKS를 끄지 않았다. 불명확한 다른 인덱스는 삭제하지 않았다.

| 단계 | 실제 결과 |
| --- | --- |
| 현재 상태 | 후보 3개 존재. member user_id의 복구 인덱스는 존재, message chat_room_id의 단일 지원은 없음 |
| 적용 | 정확한 기존 정의는 skip. 무조건 CREATE하지 않음 |
| FK 지원 복원 | DROP 예정 3개를 모두 제외한 집합을 검토. message `(chat_room_id)`만 먼저 생성, member 복구는 재사용 |
| 롤백 | unread→latest→member 후보만 한 문장씩 DROP, 매번 FK 정의·지원·데이터 해시 검사 PASS |
| 롤백 후 | FK 선두 열 지원 유지, 정상 참조 입력·잘못된 자식 참조 1216·참조 중 부모 삭제 1217 확인 |
| 재적용 중 부분 실패 | latest 생성 후 고의 중복 CREATE 1061, 완료 인덱스/지원/FK/원래 데이터 유지 확인 |
| 복구·재적용 | 완료 latest는 skip하고 나머지만 생성, FK 참조 검사 PASS |
| 재실행 | 다시 apply해 인덱스 목록 불변. 완전 동일 인덱스 중복 없음 |
| 기존 데이터 | legacy 32개 테이블·원래 17행, 모든 단계에서 원래 PK 경계/내용 SHA-256 동일 |

완료 DDL은 총 7문장이다: message 복구 인덱스 1개 생성, 후보 3개 DROP, 후보 3개 CREATE.
최종적으로 legacy의 `idx_chat_message_fk_restore(chat_room_id)` **1개만 추가**됐다.
기존 PK·UNIQUE·sender_id 지원·member 복구 인덱스는 유지했다. 복합 인덱스와 선두 열이 겹치는
복구 인덱스는 완전 중복과 구분하며 이번에 불필요하다고 삭제하지 않았다. 반복 apply도 누적 생성하지 않는다.
이번 cycle에서 누락 복원을 실제 재현한 분기는 message다. member의 지원이 없는 상태를 새로
만들기 위해 기존 복구 인덱스를 제거하지 않았으며, 해당 분기는 기존 지원 재사용으로 검증했다.

FK DML 검사는 매번 **그 트랜잭션 안에서 새 사용자/방/참여자/메시지**를 만들고 정상/위반 참조를
확인한 뒤 전체 ROLLBACK했다. 기존 행에 DELETE/UPDATE를 시도하지 않았다. AUTO_INCREMENT 예약은
되돌아가지 않을 수 있지만 기존 행 수/ID/내용·FK 정의는 보존됐다. DDL은 자동 ROLLBACK을 기대하지 않고
단계별 상태/완료 SQL/해시를 파일에 남겼다.

## 4. 회귀 테스트와 실패 구분

### 이번 실행 이력 (JUnit 개수와 별도)

| 실행 | case / PASS / FAIL | 내용 |
| --- | --- | --- |
| 사전 Inspect | 3 / 3 / 0 | 두 DB·JDBC·권한·인덱스·FK |
| 최초 QueryComparison | 5 / 5 / 0 | 보존 1/10/60, data-query 비교 및 수정 서비스 |
| FinalPerformance | 7 / 6 / 1 | 독립 100개 방 추가, 같은 데이터의 full-service 비교. 이전 100 첫 조회 3024 |
| IndexCycle | 7 / 7 / 0 | 소유 확인·FK 복원·롤백·부분 실패 복구·재실행 |
| Optimized100 | 3 / 3 / 0 | 이전 100 재실행 없이 수정 100 검증·계획 |
| ExistingRegression | 19 / 19 / 0 | 독립 합성 fixture의 기능/동시성 및 원래 행 보존 |
| 최종 Inspect | 3 / 3 / 0 | 최종 두 DB 상태·권한·GLOBAL 확인 |
| 합계 | **47 / 46 / 1** | FAIL 1건을 보존 |

회귀 19개는 safety 1 + 기능/동시성 17 + 원래 데이터 보존 1이다.
나가기 후 stale read/settings 거절·반대 커밋 순서, last_read_at 역행 방지,
RR snapshot 후 current read, 지연 커밋 미읽음·동률/중복 receipts·읽는 중 INSERT,
잘못된 배치·강제 rollback, 동시 pin 최대 2개/leave 슬롯 해제,
양쪽 참여자 읽음, lock timeout/409·rollback/retry, 별도 인위적 교착,
ROOMMATE/COMMUNITY 생성/재사용·익명 POST/COMMENT 프로필/REST/broadcast senderId와 익명 검색,
Unicode/인가, 메시지 기본 50/최대 100·offset/양방향 cursor·literal 검색/read-status를 검증했다.
기존 검증 데이터를 수정하는 과거 `ChatMysqlPerformance.rooms()/followUp()`은 실행하지 않았다.

의도적인 lock-timeout 검사만 기존 도구와 같이 해당 연결의 SELECT 가드를 잠시 해제하고 즉시
복원해 InnoDB 3초 timeout 1205를 관측했다. 성능 SELECT 3초·InnoDB/latch/future 제한은 늘리지 않았다.
의도한 교착 희생 TX는 롤백됐다. 이를 서비스의 교착 부재나 다중 JVM 통과로 해석하지 않는다.

**과거 7 FAIL은 별도다:** SELECT/lock-wait 가드 간섭 1, 인덱스 전 목록/측정 3,
FK 오류 코드 예상 문제 2, FK 지원 후보 DROP 1553 1. 과거 49회=42 PASS/7 FAIL 및 최종 5/5 기록을
덮어쓰지 않는다. 이번 안전한 롤백에서는 1553이 재발하지 않았으며 물리 제약의 1216/1217을 정확히 판정했다.
이번 새 실패 1건은 수정 전 anti-join의 100개 방 3024이며 수정 방식의 기능/100개 측정 실패는 없다.

미실행: 이번 H2/전체 백엔드·프론트 빌드는 재실행하지 않았다(운영/프론트 코드 변경 없음).
앞선 1,222개 H2 통과를 이번 MySQL 개수에 합산하지 않는다. 실제 HTTP/STOMP 네트워크·브라우저 E2E,
실제 단일 JVM 양방향 socket 전달, p95/p99·장기 동시 부하·운영 DDL/전체 백업 복원도 미검증이다.

## 5. DB 안전성

- DPAPI 인증 파일을 실제 생성 Windows 사용자 임예명으로 복호화해 전용 계정만 사용했다.
  관리자/.env 자격증명을 이번 MySQL 실행에 사용하지 않았다. 비밀번호·인증 해시·암호문 출력 없음.
- JDBC URL은 두 승인 schema 중 하나만 허용하며 UUID/호스트/버전/CURRENT_USER/CURRENT_ROLE/
  정확한 3개 SHOW GRANTS를 검사했다. 보호 DB 두 곳 LIMIT 0 SELECT의 권한 거절도 재확인했다.
- **kkirikkiri / kkiri_kkiri 쓰기 없음.** 계정 자체가 해당 DB 권한을 갖지 않는다.
  기존 서버/계정/비밀번호/권한·Docker를 변경하지 않았다. CREATE DATABASE/USER·GRANT·초기화·DROP TABLE 없음.
- current DB: 방 **98→221**, 참여자 **196→442**, 메시지 **17,430→37,659**.
  독립 100개 방/20,000개 메시지와 회귀의 새 23개 방/229개 메시지 및 관련 새 사용자/프로필/출처 등만 추가했다.
  회귀 쓰기는 해당 검사에서 새로 만든 행에만 적용됐다. 데이터/테스트 자원은 삭제하지 않고 보존했다.
- 원래 데이터는 table별 원래 PK 상한과 전체 컬럼 SHA-256으로 보호했다. 100개 fixture 추가 전후 원래 행이
  같고, 회귀에서는 100개 fixture까지 포함한 **31개 테이블·38,944개 원래 행**이 모두 같았다.
  ID가 없는/복합 PK 테이블은 전체 해시를 비교했다. 새 행 추가가 기존 행 갱신/삭제를 가리지 않게 했다.
- legacy: 방 3→3, 참여자 2→2, 메시지 4→4. 기존 32개 테이블/17행 보존, 후보 3개는 재적용 상태이며
  FK 지원 인덱스 1개가 추가됐다. 테이블 생성/삭제나 기존 데이터 변환은 없었다.
- GLOBAL InnoDB lock wait=50초, max_execution_time=0, metadata lock wait=31,536,000초,
  isolation=REPEATABLE-READ를 최종 확인했다. 세션에만 workload/lock 제한을 설정했다.
- 실행 전후 채팅 main 소스 **29개 SHA-256 동일**. 변경은 검증 도구/보고서이며 운영 코드를 고치지 않았다.

## 6. 발견 사항과 후속 수정 방향

1. **잔여 최신 메시지 정렬/반복 평가:** 수정 SQL은 큰 anti-join 비용을 줄였지만 MySQL이 최신 인덱스를
   사용하면서도 방별 전체 범위를 Top-1 정렬하고 scalar lookup을 방당 3회 평가했다. 메시지가 훨씬
   많은 방에서 비용이 더 커질 수 있으나 그 규모의 지연은 이번에 측정하지 않았다. 후속 별도 작업에서
   조회 구조/정렬 방향/실제 MySQL 계획을 비교해 역방향 제한 탐색을 유도할 방법을 검토하고
   익명 검색·pin·count·페이지/동률·unread 동등성을 다시 검증해야 한다. 이번 운영 코드 수정 없음.
2. **이전 100개 방 timeout:** 과거 구조의 한계가 확인됐다. 3024 이후 중단·비실행 EXPLAIN만 수집하고
   수정 100개 방을 별도로 측정했다. 실패를 숨기거나 SELECT 제한을 늘려 통과시키지 않았다.
3. **인덱스:** 이번 FK 복원 절차에서 1553 재발/참조 손상·완전 중복/기존 행 손실은 관측하지 않았다.
   운영의 대형 테이블 ALTER·복제/backup·metadata 경합은 이 소규모 legacy 결과로 대체할 수 없다.

## 7. 최종 판정과 증거

**부분 완료 / 운영 배포 보류.** 문제 10은 이번 규모의 성능 저하가 크게 개선됐지만 방별 정렬/반복 평가
완전 제거 요구는 남아 있어 완전 해결로 판정하지 않는다. 문제 11은 승인된 테스트 DB에서 검증 통과다.
전체 무실패 MySQL 통합 통과를 선언하지 않는다(이전 방식 100개 방 FAIL 1건 유지).

문제 2는 기존 결정대로 **단일 JVM 조건부 위험 수용**이며 Simple Broker를 유지한다. 외부 브로커를
추가하지 않았다. 다중 JVM 검증은 현재 배포의 선행 조건이 아니지만 실제 단일 JVM WebSocket 전달/
운영 프록시·브라우저 E2E는 여전히 미검증이다. 잔여 최신 조회 계획 보완과 실제 배포 DDL/부하 검증도 남는다.

다음 단계는 잔여 계획 최적화의 별도 수정 범위 결정, 같은 데이터의 재측정, 실제 단일 JVM 전달/E2E다.
이번에 생성한 독립 100개 fixture·회귀 데이터/인덱스를 임의로 초기화하거나 계정을 재설정하지 않는다.
검증 도구는 기존 핵심 기능/동시성·커서·익명 검사와 새 행 보존/인덱스 cycle·소스 hash 증거를 함께 보존한다.

실행 증거는 Git 제외 `build/chat-mysql-validation/` 아래다. 각 run의 source-hashes.json으로 도구 버전을 구분한다.
자동 집계는 `final-revalidation-summary-20261020.json`에 있다. 최종 계약 검사(73 endpoints /
75 record DTOs / 30 entities), runner PowerShell 구문 검사와 `git diff --check`도 통과했다.

| 디렉터리 | 핵심 파일 |
| --- | --- |
| run-394845f5-a249-4c4d-86d7-79b450c1282f | 사전 inventory/safety, 3 PASS |
| run-94b9b202-062e-43c7-9e48-12a7978b20ee | 최초 1/10/60 query-comparison-*.json, 5 PASS |
| run-51125a3e-9f57-4981-a44d-2ed58ecd09ce | full-service 전후 측정·100개 fixture 목록·원래 행 해시·3024, 6 PASS/1 FAIL |
| run-546fdfc5-12ca-4949-b590-27b9b6ccdaa4 | index-cycle 단계별 index/FK·SHA, completed DDL, expected-sql-errors, 7 PASS |
| run-bee7f63f-6b1c-463a-b1fc-e399fe8b22ac | optimized-100.json·기존 100 EXPLAIN·5회 시간, 3 PASS |
| run-53d1d967-e05a-4dcd-813a-93aa64b237d7 | 회귀 결과·독립 connection/order·원래 행 해시, 19 PASS |
| run-a7bdda68-095f-4384-b23b-1c65169c6926 | 최종 inventory/safety/GLOBAL, 3 PASS |

수정 파일: ChatMysqlQueryComparison.java(같은 서비스의 과거 대역·100개 별도 검증),
ChatMysqlPreservation.java(원래 행 SHA), ChatMysqlIndexCycle.java(단계별 FK 안전 cycle),
ChatMysqlFinalPerformance.java(독립 100개 데이터 추가), ChatMysqlValidation.java/run.ps1(명시적 모드·세션 제한·보존),
RUNBOOK 및 관련 보고서/배포·인덱스 안내. Controller/Service/Repository/Entity·프론트 변경 없음.
