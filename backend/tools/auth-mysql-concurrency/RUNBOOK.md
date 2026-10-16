# Auth 실제 MySQL 동시성 테스트 실행 안내

실제 Auth 서비스·Repository·트랜잭션을 단일 JVM과 격리 MySQL에서 검증하는 opt-in 도구다. 정상 Gradle/H2 테스트와 별개이며, 실패·불충분 판정은 종료 코드 1이다. 과거 실행 보고서는 정리했고 재실행에 필요한 조건과 절차는 아래에 유지한다.


## 빌드 산출물을 정리한 뒤 재실행

backend/build와 backend/bin은 재생성 산출물이므로 정리했다. 아래 명령의 Gradle test/bootJar 빌드를 먼저 수행해 현재 클래스와 JAR을 준비한 후 -PrepareOnly 또는 MySQL 실행을 진행한다. 폐기한 auth-test 수동 검증 SQL은 사용하지 않으며 운영 migration SQL과 실행기 자체의 격리 guard는 유지한다.

## 실행 범위와 사전 조건

- Windows PowerShell, Java/Javac 21, Docker CLI, 현재 백엔드 bootJar가 필요하다.
- 허용 대상은 `kkiri-auth-mysql-test`, TCP `127.0.0.1:3307/kkiri_auth_test`, 계정 `kkiri_test`, MySQL 8.0.46뿐이다.
  컨테이너 ID와 서버 UUID도 코드에 고정되어 있다. 컨테이너 재생성으로 값이 달라지면 중단한다.
  다른 호스트/포트/DB를 지정하는 옵션은 없다. 운영 DB와 `kkiri_kkiri`를 조회하지 않는다.
- 최신 동시성 테스트는 관련 9개 테이블이 모두 0행이어야 한다. 데이터가 있으면 수정/정리 전에 중단한다.
  GET_LOCK으로 이 도구의 중복 실행을 막는다. 다른 앱에서 이 테스트 DB를 동시에 사용하면 안 된다.
- 기존 마이그레이션 SQL과 안내는 수정하지 않는다. 같은 테스트 DB에서 일회용 데이터를 만들고 소유한 데이터만 지운다.
  DB의 AUTO_INCREMENT 다음 값은 정상적으로 증가하며 되돌리지 않는다. 다음 값까지 보존해야 한다면 이 도구를 실행하지 말고
  별도 격리 DB 구성과 guard 변경을 먼저 검토해야 한다.
- `KKIRI_AUTH_TEST_DB_PASSWORD`를 프로세스 환경변수로 주입할 수 있다. 없으면 지정 테스트 컨테이너의
  MYSQL_PASSWORD를 메모리에서 읽어 자식 JVM 환경변수로만 전달하고 종료 후 제거한다.
  비밀번호를 명령 인자/코드/문서/파일에 기록하지 않는다. Docker inspect 전체 JSON은 출력하지 않는다.

## 재현 명령

저장소 루트에서 실행한다. 첫 명령은 기존 H2 테스트와 최신 운영 클래스/라이브러리 빌드이고,
두 번째 명령은 DB에 접속하지 않는 테스트 도구 컴파일이다. 마지막 명령에서만 MySQL 테스트가 실행된다.
기존 8개 테이블 상태에서 새 테이블/인덱스가 아직 없다면 별도 단계로
`run.ps1 -Migrate`를 한 번 실행한다. 이미 적용한 DB에는 재실행하지 않는다.
마이그레이션 대상/제약/보존 주의는 [수동 SQL 안내](../../migrations/20261015_auth_email_identity_indexes.md)를 따른다.
현재 지정 DB에는 새 마이그레이션이 적용되어 있다.

```powershell
Push-Location backend
try {
    .\gradlew.bat --offline test bootJar
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; stop.' }
} finally { Pop-Location }
.\backend\tools\auth-mysql-concurrency\run.ps1 -PrepareOnly
.\backend\tools\auth-mysql-concurrency\run.ps1 -FormerFailuresOnly
.\backend\tools\auth-mysql-concurrency\run.ps1
```

로컬에 Gradle/의존성 캐시가 없다면 다운로드 준비가 별도로 필요하다.
빌드 실패를 무시하고 오래된 클래스로 실행하지 않는다. `run.ps1`은 bootJar의 라이브러리를
SHA-256별 build 디렉터리에 추출하고 현재 `build/classes/java/main`의 실제 클래스를 사용한다.
`AuthProtectionService`나 `AuthLimitStore` 로직을 테스트 파일에 복제하지 않는다.

결과는 `results/<서울 날짜>-<실행 ID>/summary.json` 및 `cases.json`, `environment.json`에 저장된다.
예외는 타입/SQL 코드/안전한 코드 위치만 기록하며 비밀번호·메일 본문·인증번호·토큰·토큰 해시는 출력하지 않는다.

## 테스트 컨텍스트와 설정

- 단일 JVM의 AnnotationConfigApplicationContext에 관련 9개 Entity, 선택한 실제 Repository와
  AuthService/EmailVerificationService/PasswordResetService/AuthTransactions를 등록한다.
- Spring의 실제 @Transactional 프록시, JpaTransactionManager, AuthLimitStore의 REQUIRES_NEW/READ_COMMITTED를 사용한다.
- Hibernate는 관련 Entity만 `validate`하고 소문자 physical naming 전략을 명시한다. 자동 DDL/SQL 초기화는 없다.
  전체 BackendApplication/웹 서버를 시작하지 않으므로 다른 도메인 스키마는 필요하지 않다.
- 기존 Boot test 프로필의 H2 안전 검사는 그대로 둔다. 이 독립 도구에는 자체 JDBC 고정 대상 guard가 있으며
  풀 연결을 빌릴 때도 DB/계정/버전/UUID/호스트명/내부 포트/UTC/read-only 상태를 확인한다.
- SMTP는 네트워크 기능이 없는 메모리 JavaMailSender mock이다. 실 서비스의 메일 조립/실패 처리를 통과하지만 전송하지 않는다.
- DB 시각은 실제 AuthLimitStore.now()를 사용한다. BCrypt도 실제 encoder를 사용하며 자격증명은 실행 중 생성한다.
- 기본 한도는 실제 AuthProtectionProperties 기본값이다. 요청 한도만 분리해 검증하는 2개 사례는
  로그인 이메일 실패 상한을 100, 검증 IP 실패 상한을 1000으로 올려 in_flight 상한의 간섭을 제거한다.
  이 설정은 테스트 객체에만 적용하며 결과 JSON에 표시한다.
- 복구 사례는 테스트 객체에서 로그인 실패 상한 2/차단 1초로만 재현하고 실제 DB 시간으로 기다린다.
  운영 기본값·설정은 바꾸지 않는다. NFC/NFD·악센트·대소문자 사례는 실제 Validator와 DB 조회를 통과시킨다.
- 최신/토큰 소비의 실제 Hibernate SELECT FOR UPDATE를 파라미터 없이 수집해 EXPLAIN한다.
  파라미터 값/원문 이메일/인증 토큰·해시는 결과에 출력하지 않는다.
- 일반 풀: 최대 16, connectionTimeout 10초, 세션 innodb_lock_wait_timeout 2초.
  의도적 고갈 풀: 최대 4, connectionTimeout 500ms. 글로벌 MySQL 설정은 변경하지 않는다.

## 데이터 정리와 실패 시 처리

이메일은 실행 ID를 포함하고, 생성 시도한 bucket key를 메모리에 추적한다.
finally에서 해당 이메일의 인증 행/재설정 토큰/회원/새 식별 행과 추적한 bucket만 매개변수 DELETE로 정리한다.
전체 테이블 DELETE, TRUNCATE, DROP, ALTER, FK 비활성화는 하지 않는다.

마지막에 9개 테이블 0행과 구조 불변을 확인한다. SHOW CREATE TABLE에서 정상적으로 증가하는
`AUTO_INCREMENT=<다음 값>` 테이블 옵션만 제외하고 비교한다. 컬럼의 AUTO_INCREMENT 속성은 제외하지 않는다.
다른 데이터가 섞여 있거나 정리에 실패하면 전체 삭제로 우회하지 말고 결과를 확인한다.
강제 종료/프로세스 kill은 finally를 보장하지 않는다. 다음 실행은 잔여 데이터가 있으면 중단한다.

## 해석 범위

강제 교착은 두 실제 AuthLimitStore 트랜잭션의 callback에서 반대쪽 행을 갱신하여 의도적으로 만든다.
재시도 도입 후 이 기존 피해자/전체 rollback/409 assertion은 maxAttempts=1로 재시도 소진을 검증하며 그대로 유지한다.
기본 3회 정책에서 실제 MySQL 1213/1205 이후 성공 복구는 별도의 두 JVM HTTP 사례가 검증한다.
정상적인 정렬 다중 행 잠금 사례는 별도로 테스트한다. 강제 교착이 정상 경로에서 자연 발생했다는 뜻이 아니다.
409는 실제 예외 처리 메서드를 직접 호출해 확인하며 HTTP 요청 전체를 검증한 것은 아니다.

이 도구는 장기 부하/성능 기준, 글로벌 MySQL 운영 설정, 실제 SMTP, 프록시 ingress,
두 Spring Boot 서버/다중 프로세스 및 실제 기존 DB 업그레이드를 검증하지 않는다.
동시성 테스트 자체는 데이터를 생성·정리할 뿐 DDL을 실행하지 않는다. 명시적인 `-Migrate` 단계만
검토한 수동 SQL을 지정 테스트 DB에 적용한다. 기존 마이그레이션 SQL과 안내는 수정하지 않는다.

## 실행 결과 보관

`results/`는 실행 때 생성되는 로컬 산출물이며 Git에서 제외한다. 과거 JSON·TXT·TSV·합성 DDL과 전용 감사기는 저장소에 보관하지 않는다. 새 실행은 과거 결과 없이 재현할 수 있다.

실행 종료, 자식 JVM 종료, 해당 실행의 DB fixture 정리 및 성공/실패 판정을 확인한 뒤 결과 파일을 삭제해도 된다. 실행이 실패·중단됐다면 원인과 잔여 자원을 먼저 확인한다. 파일 삭제는 DB·컨테이너·볼륨 정리를 대신하지 않는다. 보고서의 과거 결과는 기록 요약이다.
