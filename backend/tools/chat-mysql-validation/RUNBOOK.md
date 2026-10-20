# 채팅 MySQL 검증 도구

운영 서버용 실행 스크립트가 아니다. 신규 테스트 DB 2개·전용 계정 생성에 대한 사용자의 명시적
승인이 있어야 `-Provision`을 사용할 수 있다. 이미 실행한 환경의 이름과 결과는
[`../../CHAT_MYSQL_INTEGRATION_VALIDATION.md`](../../CHAT_MYSQL_INTEGRATION_VALIDATION.md)에 있다.
기존 테스트 자원도 자동 삭제하지 않는다.

## 사전 조건과 준비

- Windows PowerShell / Java 21 / MySQL CLI / 현재 소스의 `backend/build/libs/backend-0.0.1-SNAPSHOT.jar`가 필요하다.
- 기본 Gradle test는 H2 전용이다. 이 도구는 별도 `AnnotationConfigApplicationContext`에서 실제 ChatService,
  Repository, EntityManager 및 트랜잭션을 사용하며 Boot 앱, HTTP, STOMP 서버, 시드·스케줄러·SMTP는 시작하지 않는다.
- `chat-mysql-validation`만 활성화한다. test/local/prod 또는 운영 데이터소스 설정을 가져오지 않는다.
- `-PrepareOnly`는 JAR 라이브러리 추출 및 Java 소스 컴파일만 수행한다. DB 접속·생성은 하지 않는다.

```powershell
powershell -NoProfile -File backend/tools/chat-mysql-validation/run.ps1 -PrepareOnly
```

## 승인 후 생성·실행

2026-10-19 승인 대상인 로컬 `127.0.0.1:3306`, MySQL 8.0.44, DESKTOP-8K385BA를 검사한다.
다른 서버/버전으로 재사용하려면 임의로 보호 검사를 제거하지 말고 먼저 별도 환경 확인·승인을 받는다.

```powershell
# 새로운 DB 두 개와 계정 생성이 승인된 경우에만 실행한다.
powershell -NoProfile -File backend/tools/chat-mysql-validation/run.ps1 -Provision -KeepSession
```

1. DB명은 `kkirichatvalidation<날짜><임의id>`, `kkirichatlegacy<날짜><임의id>`다.
   영문 소문자·숫자만 사용해 MySQL 스키마 권한의 `%`/`_` 패턴 문제를 피한다.
2. 계정명은 `kkirichattest_<임의id>@127.0.0.1`이다. 이름을 출력하고 DB/계정의 부재를 먼저 조회한다.
   기존 이름이면 중단한다. `IF NOT EXISTS`, 기존 자원 재설정·교체, 기존 사용자 수정은 하지 않는다.
3. 관리자 자격증명은 `.env`에서 메모리로 읽어 고정된 localhost 접속에서 이름 검사·CREATE/GRANT에만 쓴다.
   이 연결로 채팅 fixture·마이그레이션을 실행하지 않는다. 비밀번호를 명령 인자/파일/보고서에 쓰지 않는다.
4. 새 계정에는 새 두 DB의 SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES만 부여한다.
   전역 권한·역할·GRANT OPTION·다른 DB 권한을 허용하지 않는다. DB명에는 wildcard가 없다.
5. Java는 새 계정으로 서버 UUID/버전/host/port/선택 schema/current_user/current_role/SHOW GRANTS를 확인한다.
   보호 DB의 `SELECT ... LIMIT 0`이 권한 오류로 거절되는지도 확인하며 보호 DB에 쓰기 검사를 시도하지 않는다.
6. Full 최초 실행은 테이블이 하나라도 있으면 초기화를 거절한다. CREATE-ONLY는 검증된 빈 신규 DB에만 쓴다.
   종료 시 validate만 수행한다. DROP DATABASE/USER, create-drop, FK 검사 해제는 하지 않는다.

비밀번호는 검증 프로세스 메모리에만 둔다. `-KeepSession`에서 `Regression`은 현재 소유 DB의
잠금 타임아웃·물리 제약·인덱스·기존 합성 메시지 보존을 재검사한다. 새 DB를 만들거나 기존 스키마를 초기화하지 않는다.
`Stop`/프로세스 종료 시 환경변수를 제거한다. 이 최초 생성 경로는 메모리만 사용하므로 비밀번호 파일을 만들지 않는다.
종료 후 재접속에는 사용자가 별도로 관리하는 해당 테스트 계정 자격증명이 필요하다.
비밀번호를 자동 재설정하거나 현재 환경 대신 새 환경을 몰래 생성하지 않는다.

## 1045 진단·승인된 인증 복구 (2026-10-20)

[실제 진단·복구 보고서](../../CHAT_MYSQL_AUTH_RECOVERY.md)를 따른다. 성공 계정 1135f9f3의
기존 비밀번호는 memory-only 종료 후 유실됐다. 남아 있던 5a7b340c 파일은 다른 계정용이며
Password가 없는 미완성 CLIXML이다. XML import 성공을 비밀번호 복호화 성공으로 취급하지 않는다.

사용자가 **기존 전용 계정 비밀번호만 재설정**을 승인한 뒤 정확한 @127.0.0.1 계정 하나만 변경했다.
Host/plugin/권한·DB·서버/Docker는 변경하지 않았다. 성능/마이그레이션 검증은 실행하지 않았다.
복구된 credential은 아래 파일에 Windows DPAPI로 암호화돼 있고 소유자 전용 ACL을 적용했다.

- `build/chat-mysql-validation/202610191135f9f3.credential.clixml`
- 생성 사용자: `DESKTOP-8K385BA\임예명`, SID 끝 1001. 같은 컴퓨터·Windows 사용자로 실행한다.
- `202610191135f9f3.credential-metadata.json`은 생성 사용자/SID·검증 상태만 기록한다.
- 이 파일들은 Git 제외 build 산출물이다. 내용을 출력·Git 추가·채팅 복사하지 않는다.
- sandbox 사용자(SID 끝 1005)는 DPAPI export에서 실패했다. 생성 사용자 환경에서 실행하며
  실패 시 비밀번호를 다시 reset하거나 다른 계정의 파일을 사용하지 않는다.

인증만 재확인하는 안전한 기본 명령(실제 생성 사용자 환경):

```powershell
powershell -NoProfile -File backend/tools/chat-mysql-validation/recover-auth.ps1
```

이 기본 경로는 DPAPI import→두 테스트 DB 로그인/접속 대상/SHOW GRANTS→보호 DB LIMIT 0 거절만
확인한다. 애플리케이션 컨텍스트/fixture/성능·EXPLAIN·마이그레이션을 실행하지 않는다.
`-ResetApprovedTestPassword`는 별도 명시적 사용자 승인이 있을 때만 쓰는 복구 옵션이다.
현재 credential 파일이 이미 있으므로 재실행해 반복 reset하지 않는다. 파일 존재 시 중단한다.

`diagnose-auth.ps1` 기본은 로컬 파일 구조·Windows 사용자/PowerShell 진단이다.
`-ReadServerMetadata`를 명시하면 .env의 DB 연결 항목을 메모리에서만 사용해 서버 UUID 확인 후
테스트 계정의 Host/plugin/lock/expiry·SHOW GRANTS·역할·테스트 schema 목록만 읽는다.
비밀번호·인증 해시·메일/JWT/AI 값은 출력/조회하지 않는다. 원문 예외도 억제한다.

향후 별도로 요청된 기존 검증의 runner에는 `-CredentialFile`을 명시할 수 있다. 그 경우 Import-Clixml
결과가 PSCredential이고 manifest 계정과 정확히 일치해야만 KKIRI_CHAT_TEST_PASSWORD로 메모리 전달한다.
환경변수와 파일을 동시에 지정하면 중단하고, 종료 시 환경변수를 제거한다. 파일 복호화/타입/계정
불일치 시 DB 접속 전에 중단한다. 원래 manifest의 memory-only 기록은 당시 이력으로 유지했다.

## 보존된 DB 재사용 (2026-10-20)

`-EnvironmentFile` 재사용의 기본 모드는 이제 **Inspect**다. 이전처럼 Full로 시작하지 않는다.
Full은 빈 DB만 허용하며 보존된 DB에서 실행하지 않는다. 신규 DB/계정 생성도 재사용에 필요하지 않다.
완료 상태 `created-retain-no-automatic-deletion`인 기존 manifest만 허용한다. 생성 도중 상태로
남은 다른 manifest나 다른 계정의 credential 파일을 대신 사용하지 않는다.

비밀번호는 위의 명시적 `-CredentialFile` 또는 `Read-Host -AsSecureString` 등의 로컬 입력으로 현재 실행 프로세스의
`KKIRI_CHAT_TEST_PASSWORD`에만 공급한다. 명령 인자·파일·로그·Git에 쓰지 않는다. 다음 명령에
비밀번호를 삽입하지 않는다. runner 종료 시 환경변수는 제거되므로 다음 실행에는 다시 공급해야 한다.

```powershell
# 매번 실행 전에 현재 프로세스에 전용 계정 비밀번호를 안전하게 공급한다.
powershell -NoProfile -File backend/tools/chat-mysql-validation/run.ps1 `
  -EnvironmentFile backend/build/chat-mysql-validation/202610191135f9f3.environment.json `
  -Mode Inspect

# 기존 합성 데이터와 기존 인덱스를 그대로 쓰는 읽기 전용 비교:
powershell -NoProfile -File backend/tools/chat-mysql-validation/run.ps1 `
  -EnvironmentFile backend/build/chat-mysql-validation/202610191135f9f3.environment.json `
  -Mode QueryComparison
```

- Inspect는 양쪽 DB를 validate로 열어 안전 검사·행 수·SHOW CREATE/INDEX를 저장한다.
  QueryComparison은 current DB만 열어 1/10/60개 방의 기존 perf fixture를 찾는다.
  100개 fixture가 있으면 포함하고, 없으면 coverage에 미실행으로 기록한다. 자동 추가하지 않는다.
- 두 모드의 풀은 readOnly이며 세션 transaction_read_only=1로 시작한다. 시드/fixture DML,
  인덱스 CREATE/DROP, receipt/anonymous 마이그레이션을 실행하지 않는다.
- 실제 `findRoomPage` HQL과 이전 anti-join만 다른 baseline HQL을 같은 데이터·인덱스·페이지
  크기 50으로 비교한다. 한 번 예열 후 각각 5회 측정하며 SQL/바인딩/EXPLAIN/ANALYZE·중앙값을
  기록한다. 전체 페이지 projection 동등성과 페이지 unread 대 단건 unread 집계도 확인한다.
  새 서비스 전체 시간/SQL 수는 **별도로** 기록한다. 목록 data-query 시간을 전체 API 응답 시간으로
  표시하지 않는다. HTTP·인증·JSON 직렬화는 측정 밖이다.
- baseline이 3초 제한을 넘으면 해당 규모를 FAIL로 남기고 반복을 중단한다. 3초 제한을 올리지
  않는다. 원인 클래스/SQL 코드는 results.json과 이전 계획을 함께 확인한다. 앞선 규모의 결과는 보존한다.
- `-Mode ExistingRegression`은 현재 DB를 validate로 열고 기존 동시성/기능 검사를 실행한다.
  **작은 새 합성 fixture를 추가하고 해당 fixture를 변경한다.** DB 초기화·테이블 재생성은 하지
  않지만 읽기 전용은 아니므로 쓰기 검증이 승인된 경우에만 사용한다. 보존된 성능 fixture와
  legacy 데이터를 초기화하는 Full/runBefore/followUp은 재사용 경로에서 호출하지 않는다.
- 인덱스 cycle은 이 모드에 자동 연결하지 않는다. [수동 절차](../../migrations/20261019_chat_query_indexes.md)의
  소유/동등 인덱스 확인·최종 잔존 집합 기준 FK 지원 복원부터 검토하고 각 단계의 증거를 남긴다.

조회 개선 당시에는 전용 계정 1045와 사용자의 보류 결정으로 이 모드들의 MySQL 실검증을 하지 않았다.
후속 인증 복구에서 로그인/권한은 확인했지만 이 모드들의 실행과 성능/인덱스 cycle은 여전히 미실행이다.
PrepareOnly 컴파일과 H2 회귀를 MySQL 통과로 보고하지 않는다.

## 최종 MySQL 재검증 (인증 복구 이후 별도 요청)

위 미실행은 앞선 작업 당시 기록이다. [최종 보고서](../../CHAT_MYSQL_FINAL_REVALIDATION.md)에
실제 47 case/46 PASS/1 FAIL, 60개 방 서비스 1,904.2→54.4ms, 수정 100개 방 83.1ms,
legacy FK 복원/롤백/재적용·해시 보존과 회귀 19/19 결과를 기록했다. 이전 100개 방 baseline
3024 실패는 유지한다. 실제 계획에 방별 Sort/방당 3회 scalar 평가가 남아 완전 해결로 세지 않는다.

이번에 추가한 명시적 모드는 동일한 -EnvironmentFile/-CredentialFile과 DPAPI 생성 사용자 환경을 사용한다.
기본 모드는 여전히 Inspect이며 Full/Provision을 재사용하지 않는다.

| 모드 | 범위·안전 조건 |
| --- | --- |
| FinalPerformance | current의 기존 행 PK 경계/SHA 기록 후 독립 100개 방×200개 메시지만 추가(존재 시 재생성 안 함), read-only로 재접속해 이전/수정 비교. 기존 데이터 갱신/삭제 없음 |
| Optimized100 | 기존에 추가된 100개 방의 수정 서비스·페이지/정렬/unread와 계획만 읽음. 이미 3024인 baseline은 실행하지 않고 저장한 SQL의 비실행 EXPLAIN만 수집 |
| IndexCycle | legacy만 허용. 실제 후보 정의와 과거 소유 기록 확인→지원 복원→후보 3개만 DROP→부분 실패 1061 복구/재적용→멱등/중복·FK/해시 검사 |
| ExistingRegression | 기존 기능/동시성 17개를 새 독립 fixture에 실행. 실행 전의 모든 행(100개 fixture 포함)의 PK 경계/SHA를 전후 비교 |

IndexCycle은 역사적 소유 증거 `run-de097.../<legacy>.indexes.json`이 없거나 정의/지원/FK/해시가
맞지 않으면 중단한다. DDL 완료 목록과 단계별 상태를 저장하고 실패 후 무조건 CREATE/DROP을
계속하지 않는다. 참조 검사는 그 트랜잭션에서 새로 만든 행만 사용하고 전체 ROLLBACK한다.
테스트 데이터/테이블/계정은 삭제하지 않는다. FK 복구 인덱스는 선두 겹침만으로 제거하지 않는다.

현재 100개 baseline의 3024를 확인했으므로 부하/계획 분석 없이 FinalPerformance/QueryComparison을
그대로 반복해 타임아웃을 재현하지 않는다. 수정 100개만 확인할 때는 Optimized100을 사용한다.
이 모드의 비교 EXPLAIN은 이번 full-service 실행의 보존된 60개 SQL/바인딩 증거를 사용하므로
해당 run 증거도 보존한다. 예전 임의의 환경/계정으로 우회하지 않는다.

일반 SELECT 3초·InnoDB/metadata lock wait 3초는 세션에만 적용한다. GLOBAL 변경 없음.
기존 의도적 lock-timeout 검사만 SELECT 가드를 잠시 해제/즉시 복원해 1205를 확인하며 성능
타임아웃을 올리지 않는다. 예상 밖 오류가 있으면 결과/현재 index 상태를 확인하고 다음 작업을 판단한다.
소스가 실행마다 보완됐으므로 각 run/source-hashes.json을 현재 파일과 구분해 읽는다.

## 부하와 데이터 범위

- 풀 최대 4개, 작업 스레드 2개, Java heap 최대 512 MiB.
- 일반 SELECT의 테스트 세션 `max_execution_time=3000`, lock wait=3초, latch=8초, future=15초다.
- 의도적으로 lock wait를 검사할 때만 해당 연결의 SELECT 제한을 잠시 해제하고 즉시 복원한다.
  InnoDB lock wait 3초·테스트 대기 제한은 늘리지 않는다. GLOBAL 변수는 변경하지 않는다.
- 1/10/60개 방 × 방별 200개 메시지, 별도 3,000개 이력 등 합성 데이터만 사용한다.
  운영 데이터 복사·대규모 스트레스는 하지 않는다. 생성 전 C: 3 GiB 미만이면 거절한다.
- 현재/구 스키마는 Entity 기준으로 생성하며 관련 FK 의존 Entity도 포함한다. 다른 도메인 서비스를 실행하지 않는다.

## 검사와 결과 해석

- 실제 독립 JDBC 연결 + latch로 RR snapshot/current read, 나가기/설정/읽음 교차,
  지연 커밋·정확한 ID·동률 시각·신규 전송·pin 제한·rollback/timeout을 검사한다.
- 두 participant의 동시 읽음 경로와 **별도의 인위적 JDBC 교착**을 구분한다.
  후자는 MySQL의 희생 트랜잭션 처리를 확인하며 모든 서비스 경로의 교착 부재를 증명하지 않는다.
- 메시지 기본 50/최대 100, offset/양방향 cursor/search/read-status, Unicode/익명성/인가를 검사한다.
- JDBC 프록시로 실제 JPA SQL과 바인딩을 수집한다. SQL 횟수, 5회 반복 시간, JSON 크기,
  heap 사용량 차이, 실제 SQL의 EXPLAIN/선택된 EXPLAIN ANALYZE를 기록한다.
- 구 스키마는 **신규 legacy DB 내부에서만** `is_read`를 제거하여 재현한다. 더미 데이터 전환,
  중복 실행 오류, DDL 암묵 커밋, 실패한 UPDATE rollback/forward recovery를 확인한다.
- FK 오류는 MySQL의 1216/1452, 1217/1451 및 SQLState 23000으로 확인한다.
- 비고유 복합 인덱스도 FK 지원에 사용되면 DROP이 1553으로 거절될 수 있다.
  legacy 검사에서 이 실패를 재현하고 새 FK 지원 인덱스를 만든 뒤 해당 후보 인덱스를 rollback/re-create한다.
  복구 확인용 선두 컬럼 인덱스는 보존할 수 있으므로 최종 index JSON도 함께 읽는다.

`backend/build/chat-mysql-validation/run-<uuid>`에 환경/권한, 소스 hash, SQL/계획, migration,
실패 원인, `results.partial.json`, `results.json`이 남는다. 이 디렉터리는 Git 추적 대상이 아니다.
계정·DB metadata manifest에는 비밀번호가 없다. DDL/DML의 원문 예외는 로그에 출력하지 않는다.

인덱스 전 성능 검사도 3초를 넘으면 FAIL·비정상 종료 코드로 남긴다. 이 기준을 완화하거나 실패를
통과로 바꾸지 않는다. 후속 실행의 PASS가 앞선 benchmark FAIL이나 배포 승인을 뜻하지 않는다.
수정된 도구의 컴파일 성공과 이전 소스 버전으로 실제 실행한 결과는 각 run의 소스 hash로 구별한다.

현재 도구/결과는 실제 운영 데이터·운영 DDL 적용, 실제 HTTP/브라우저 E2E,
다중 JVM WebSocket, 장시간 부하·백업 전체 복구·복제 환경을 검증하지 않는다.
