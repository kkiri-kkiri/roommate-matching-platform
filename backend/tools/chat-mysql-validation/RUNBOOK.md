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
`Stop`/프로세스 종료 시 환경변수를 제거한다. DB·사용자는 남고 비밀번호 파일은 없다.
종료 후 재접속에는 사용자가 별도로 관리하는 해당 테스트 계정 자격증명이 필요하다.
비밀번호를 자동 재설정하거나 현재 환경 대신 새 환경을 몰래 생성하지 않는다.

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
