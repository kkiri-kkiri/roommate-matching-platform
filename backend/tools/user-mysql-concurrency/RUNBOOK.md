# User 실제 MySQL 정합성·동시성 검증

실제 User·Admin·비밀번호 재설정·매칭 서비스의 정합성과 동시성을 단일 JVM과 격리 MySQL에서 검증하는 opt-in 도구다. 정상 Gradle/H2 테스트와 별개이며, 실패·불충분 판정은 종료 코드 1이다. 과거 실행 보고서는 정리했고 재실행에 필요한 조건과 절차는 아래에 유지한다.


## 빌드 산출물을 정리한 뒤 재실행

backend/build와 backend/bin은 재생성 산출물이므로 정리했다. 아래 명령의 Gradle test/bootJar 빌드를 먼저 수행해 현재 클래스와 JAR을 준비한 후 -PrepareOnly 또는 MySQL 실행을 진행한다. 폐기한 auth-test 수동 검증 SQL은 사용하지 않으며 운영 migration SQL과 실행기 자체의 격리 guard는 유지한다.

## 격리 대상과 실행 전제

- 전용 컨테이너: `kkiri-user-mysql-test`, 이미지 `mysql:8.0.46-oraclelinux9`.
- 고정 연결: `127.0.0.1:3308/kkiriusertest`, 사용자 `kkiri_user_test`.
- Auth 컨테이너/DB/증거를 사용하거나 초기화하지 않는다. 기존 운영·개발 DB 연결 옵션은 없다.
- 스키마 이름에 wildcard 문자가 없다. `SHOW GRANTS`는 전역 USAGE와 정확히 이 스키마 권한만 허용한다.
- 컨테이너 label/이미지/실행 상태/loopback 포트와 `auto.cnf` UUID를 확인한 후,
  **각 JDBC 연결 대여 때** DB·계정·버전·UUID·hostname·내부 포트·UTC·read-only 상태를 재확인한다.
- 모든 테이블이 비어 있어야 시작한다. 남은 데이터가 있으면 자동 삭제/재생성하지 않는다.
- 비밀번호는 생성 및 Docker metadata 읽기 후 메모리/자식 JVM 환경변수로만 전달한다.
  `docker inspect` 전체 결과와 비밀번호를 콘솔·문서·증거에 출력하지 않는다.
- Java 21/Javac, PowerShell, Docker, 현재 backend bootJar와 빌드 클래스가 필요하다.
  기본 Gradle 테스트의 H2 강제 및 안전 검사는 변경하지 않는다.

## 명령

저장소 루트에서 실행한다. 최초 컨테이너 생성 단계는 이미 같은 이름의 컨테이너가 있으면 중단한다.
`-Initialize`는 완전히 빈 **User 전용 schema**에서만 전체 Entity 스키마를 `create-only`로 한 번 생성한다.
기존 테이블이 있으면 거절한다. DROP/update/create-drop은 사용하지 않는다.

```powershell
Push-Location backend
try {
    .\gradlew.bat --offline test --rerun bootJar
    if ($LASTEXITCODE -ne 0) { throw 'Build/test failed; stop.' }
} finally { Pop-Location }
node backend/tools/sync-contract-docs.cjs --check
.\backend\tools\user-mysql-concurrency\run.ps1 -PrepareOnly
# 새 환경에서만 한 번씩:
.\backend\tools\user-mysql-concurrency\run.ps1 -Provision
.\backend\tools\user-mysql-concurrency\run.ps1 -Initialize
# 이미 초기화되고 비어 있는 환경:
.\backend\tools\user-mysql-concurrency\run.ps1 -FormerFailuresOnly
.\backend\tools\user-mysql-concurrency\run.ps1
.\backend\tools\user-mysql-concurrency\run.ps1 -AdditionalOnly
```

`-FormerFailuresOnly`는 최초 실패 7개만 각각 실행한다.
기존 `-ReproduceOnly`는 비밀번호/재설정 대 상태 변경 8개와 stale managed entity 1개를 그대로 실행한다.
기본 전체 실행은 기존 29개다. `-AdditionalOnly`는 잠금 재호출의 변경 보존/롤백,
기존 관리 엔티티 재적재 SQL, 신규 참조의 첫 SQL이 잠금 조회인지, 다른 회원 8명의
독립 트랜잭션 동시 잠금·혼합 변경을 별도 5개로 검증한다.
기존 실패 assertion은 유지되며 수정 전 코드에서는 실패한다. 수정 후에는 모두 통과해야 한다.
중간 불충분·실패 결과를 덮어쓰지 않고 `results/<서울날짜>-<run>/`에 매번 새로 저장한다.
도구 변경으로 원본 H2 테스트나 기존 Auth 시나리오 assertion을 수정하지 않는다.

## 실제로 검증하는 경로

- 전체 현재 Entity 30개/테이블 30개와 FK를 생성하고 실행 컨텍스트에서는 `validate`한다.
  `BackendApplication`, 실제 웹 서버, `.env`, Boot property import, 시드, 수집 스케줄은 시작하지 않는다.
- UserService, AdminService, PasswordResetService의 **확인/소비 메서드**, MatchingRefreshService,
  MatchingRefreshListener/Configuration 및 실제 Repository를 사용한다.
- AdminService의 DB 의존성도 실제 Repository를 주입한다. 비밀번호 재설정 확인에 필요하지 않은
  메일 발송·요청 제한·메일 요청 transaction collaborator는 아예 주입하지 않는다.
  `requestPasswordReset`과 실제 SMTP/외부 서비스는 호출하지 않는다.
- 매칭 이벤트는 실제 `TransactionalEventListenerFactory`와 AFTER_COMMIT listener,
  실제 제한된 executor를 사용한다. 테스트마다 executor와 pool이 비워질 때까지 기다린다.
- 매칭이 먼저인 경합은 실제 refresh 메서드를 호출하고, 사용자 lock 획득 후 프로필 SQL 직전
  StatementInspector의 latch로만 실행을 멈춘다. 계산 로직·잠금 쿼리는 바꾸지 않는다.
- 일반 풀: 최대 16, 대여 timeout 10초, 세션 `innodb_lock_wait_timeout=2`초.
  작은 풀 고갈 사례: 최대 4, 대여 timeout 500ms. 전역 DB 설정은 변경하지 않는다.
  기본 격리는 실제 MySQL REPEATABLE-READ이고 matching batch는 운영 코드의 READ_COMMITTED다.
- JDBC는 UTC, JVM은 Asia/Seoul이다. raw claim connection의 lock wait 기본값 50초와
  pool session의 2초를 환경 증거에 구분한다. 이 테스트의 2초는 운영 설정 변경이 아니다.
- 강제 교착은 테스트가 서로 반대 순서로 USER lock을 요청해서 만든다. 정상 매칭 경로의
  정렬 잠금 경합은 별도 시나리오로 검증한다.
- 409는 실제 GlobalExceptionHandler 메서드를 직접 호출해 응답 코드/본문을 검증한다.
  전체 HTTP 경로를 실행했다는 의미는 아니다.

## 증거와 정리

- cases/summary: 시나리오 판정, 성공/오류 수, 비밀번호 일치 여부 boolean 및 DB 최종 상태.
- account-sql-trace: 계정 변경 실패를 재현하는 SQL 순서/실행 thread. 바인딩 값은 저장하지 않는다.
- sql-templates/database-errors: 실제 Hibernate SQL template와 MySQL 코드/SQLState만 저장한다.
  비밀번호·hash·이메일·토큰·예외 원문을 저장하지 않는다.
- schema-before/after: 실제 30개 SHOW CREATE TABLE. 비교 시 테이블 옵션의 정상 AUTO_INCREMENT
  다음 값 증가만 제외한다. 컬럼 AUTO_INCREMENT 속성은 그대로 비교한다.
- protected-files-before/after: 운영 소스 및 기존 Auth 도구/증거의 SHA-256을 비교한다.
- artifact-sha256: 실행한 main 클래스/bootJar 및 새 도구 소스의 지문이다.

finally에서 생성한 user ID 및 실행 접두사가 일치하는 기준정보만 WHERE 조건으로 삭제한다.
FK는 계속 활성화한다. TRUNCATE/전체 DELETE/DROP/ALTER/AUTO_INCREMENT 초기화는 하지 않는다.
모든 30개 테이블 0행, DDL 유지, pool active/waiting 0, close를 확인한다.
강제 프로세스 종료는 finally를 보장하지 않는다. 잔여 데이터가 있으면 다음 실행은 중단하며
원인 확인 없이 정리 범위를 넓히지 않는다.

운영 데이터 업그레이드, 실제 운영 collation/DDL, 두 서버/다중 프로세스, HTTP/JWT 세션,
프로세스 kill/네트워크 단절, 실제 SMTP/ingress 및 장기 부하는 이 도구의 검증 범위 밖이다.

## 실행 결과 보관

`results/`는 실행 때 생성되는 로컬 산출물이며 Git에서 제외한다. 과거 JSON·TXT·TSV·합성 DDL과 전용 감사기는 저장소에 보관하지 않는다. 새 실행은 과거 결과 없이 재현할 수 있다.

실행 종료, 자식 JVM 종료, 해당 실행의 DB fixture 정리 및 성공/실패 판정을 확인한 뒤 결과 파일을 삭제해도 된다. 실행이 실패·중단됐다면 원인과 잔여 자원을 먼저 확인한다. 파일 삭제는 DB·컨테이너·볼륨 정리를 대신하지 않는다. 보고서의 과거 결과는 기록 요약이다.
