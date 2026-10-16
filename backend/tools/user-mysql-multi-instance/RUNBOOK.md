# User 실제 Spring Boot 2개 / MySQL / HTTP 검증

실제 BackendApplication·Tomcat을 독립 JVM 두 개에서 실행하고 격리 MySQL을 공유하는 User HTTP 검증 도구다. 정상 Gradle/H2 테스트와 별개이며, 실패·불충분 판정은 종료 코드 1이다. 과거 실행 보고서는 정리했고 재실행에 필요한 조건과 절차는 아래에 유지한다.


## 빌드 산출물을 정리한 뒤 재실행

저장소에는 소스·실행기만 유지한다. backend/build·backend/bin·frontend/build는 재생성 가능하다. 이 실행기를 사용하려면 먼저 저장소 루트에서 다음 빌드로 현재 bootJar와 main 클래스를 준비한다. 이 단계는 test 프로필·H2 메모리 DB를 사용하며 MySQL 테스트를 실행하지 않는다.

```powershell
Push-Location backend
try {
    .\gradlew.bat --offline test bootJar
    if ($LASTEXITCODE -ne 0) { throw 'Build failed; stop.' }
} finally { Pop-Location }
```

이후 아래의 -PrepareOnly 또는 실제 MySQL 실행 절차를 따른다. 로컬 Gradle/의존성 캐시가 없다면 다운로드 준비가 별도로 필요하다. 폐기한 auth-test 수동 baseline/검증 SQL은 사용하지 않으며 운영 migration SQL과 실행기 자체의 격리 guard는 유지한다.

## 실행

Java/Javac 21, Docker, 현재 backend bootJar/클래스가 필요하다. 저장소 루트에서:

```powershell
.\backend\tools\user-mysql-multi-instance\run.ps1 -PrepareOnly
.\backend\tools\user-mysql-multi-instance\run.ps1
# 특정 사례도 새 스키마·계정·두 JVM에서 실행한다.
.\backend\tools\user-mysql-multi-instance\run.ps1 -Cases @('two-real-jvms-shared-db-and-jwt')
# 이전 실패한 풀 고갈만 재검증(기존 500 assertion 유지):
.\backend\tools\user-mysql-multi-instance\run.ps1 -Cases @('pool-exhaustion-and-recovery')
# 추가 JWT/ERROR dispatch 보안 회귀(기존 27개와 별도 집계):
.\backend\tools\user-mysql-multi-instance\run.ps1 -Cases @('jwt-infrastructure-security-regression')
```

8081·8082가 이미 사용 중이면 중단한다. 다른 프로세스를 종료하지 않는다.
실패/검증 불충분은 종료 코드 1이다. 실패한 사례도 보존하고 나머지 독립 사례를 실행한다.

## DB·외부 서비스 안전 조건

- 컨테이너 `kkiri-user-mysql-test`, 이미지 `mysql:8.0.46-oraclelinux9`, 지정 ID/UUID/hostname과 3308 매핑을 확인한다.
- 새 DB `kkiri_user_mi_<12자리 hex>`, 새 계정 `kkiri_umi_<동일 hex>`를 생성한다. 기존 이름이 있으면 중단한다.
- root는 해당 컨테이너의 로컬 socket에서 새 schema/account 준비에만 사용한다. 두 Boot JVM에는 전달하지 않는다.
- 애플리케이션 계정은 전역 USAGE와 정확한 리터럴 새 schema 권한만 갖는다. grant의 underscore를 escape한다.
- JDBC는 TCP `127.0.0.1:3308/<새 schema>`만 허용한다. 연결 대여 때 DB/계정/MySQL 8.0.46/UUID/hostname/포트/UTC/read-only를 검사한다.
- 기존 `kkiriusertest`는 원래 전용 계정의 read-only 연결로 30개 테이블 행 수·SHOW CREATE TABLE을 전후 비교한다.
  기존 Auth DB와 `kkiri_kkiri`에는 연결하지 않는다.
- 전체 30개 Entity의 합성 DDL을 DB 연결 없이 생성한 후 새 빈 schema에만 적용한다.
  새 schema의 auth bucket key에는 ASCII/ascii_bin 물리 계약도 적용한다. 기존 테이블/마이그레이션 증거를 수정하지 않는다.
- 두 JVM은 `user-multi-validation`, Hibernate validate, SQL init never, seed NONE, crawl false로 실행한다.
  스케줄 처리기/calendar scheduler를 테스트 BeanFactoryPostProcessor로 제거하고 ApplicationRunner가 없음을 확인한다.
- SMTP transport가 없는 JavaMailSender mock을 사용한다. 메일 내용·토큰은 인증된 loopback IPC로 부모 메모리에만 전달한다.
- AI endpoint 및 SMTP fallback은 loopback 포트 1로 고정한다. AI 요청을 사용하는 endpoint는 시험하지 않는다.
- root/테스트 DB 비밀번호/JWT/IPC 인증값은 메모리와 프로세스 환경변수로만 전달한다.
  Docker inspect 전체 출력, 자격증명, 비밀번호, JWT, 토큰, 메일 본문은 파일/콘솔에 기록하지 않는다.

## 실제 처리 경로와 제어

모든 업무 검증은 실제 HTTP Controller→Service→Repository를 사용한다. JWT는 실제 로그인 API로 발급하며
다른 인스턴스에 전달한다. DB fixture와 물리 제약 확인은 전용 계정 JDBC를 사용하고 HTTP 검증과 구분한다.
학부/전공/기숙사 및 synthetic 회원·설문·선호는 명시적 fixture이며 개발용 자동 시드가 아니다.

계정·프로필 경합은 테스트 DataSource의 PreparedStatement observer가 **실제 SQL 실행을 마친 뒤**
USER 잠금 또는 프로필 flush를 잠시 유지한다. stdin ARM/RELEASE 명령으로 커밋 순서를 제어한다.
운영 Controller/Service/Repository의 잠금·검증·응답 코드는 교체하지 않는다.
매칭 observer도 실제 matching batch의 사용자 잠금 획득 뒤에만 대기한다.

잠금 timeout과 매칭 실패는 실제 외부 JDBC row lock으로 유발한다. 강제 교착은 외부 tx가 토큰과
20개 fixture 변경을 유지하고 HTTP reset이 USER를 잠근 상태에서 역순 대기를 만들어 유발한다.
HTTP가 가벼운 tx이므로 1213 피해자가 되어 409 응답·전체 롤백할 것을 검증한다.
정상 경로의 자연 교착과 구분한다. 풀 고갈은 서버 stdin으로 12개 연결을 대여해 의도적으로 유발한다.

DB 기본 격리는 REPEATABLE-READ, matching batch는 운영 코드의 READ_COMMITTED다.
서버당 풀 최대 12/대여 timeout 5초/세션 InnoDB lock wait 2초다. 운영 timeout 검증을 뜻하지 않는다.
Auth 한도는 기존 기본값으로 고정한다. synthetic IP 구분을 위한 신뢰 proxy는 loopback 127.0.0.1/32뿐이며
서버의 자동 forwarded header 처리는 NONE이다. 실제 ingress 검증은 아니다.

## 시나리오와 증거

전체 27개: 두 JVM·JWT, 비밀번호/재설정 대 정지/탈퇴의 양 커밋 순서 8개,
비활성 계정의 기존 JWT/로그인/재설정 2개, Principal/권한, 동일 회원 부분 수정, 닉네임 UNIQUE,
학적/NULL/롤백, 물리 UNIQUE/FK/NOT NULL, 매칭·실패 복구·120명 batch,
독립 사용자 행 잠금, HTTP timeout 2개, 강제 교착, 같은 현재 비밀번호 12개,
풀 고갈, 서버 종료/재시작, HTTP 반복 부하를 검증한다.

예상 업무 오류는 400/401/403, 잠금 오류는 409 ACCOUNT_UPDATE_CONFLICT로 엄격히 검사한다.
의도적 풀 고갈만 서버 장애 응답을 기대하며 정상 업무의 5xx는 실패다.
최초 코드에서는 풀 고갈의 실제 응답이 401이어서 원래 500 assertion이 실패했다. 수정 후에도
같은 assertion을 유지한다. 다른 서버·연결 반환·같은 JWT 복구와 안전한 장애 로그를 함께 검증한다.
관측 필터는 인증 단계 예외를 그대로 재전파하며 응답을 변경하지 않는다. 추가 보안 사례에서만
메모리의 IPC 인증 헤더로 테스트 필터의 sendError(500)를 유도해 실제 컨테이너 ERROR dispatch를 검증한다.
이 테스트 필터와 헤더는 운영 코드·bootJar에 포함되지 않는다.
추가 사례는 양 서버의 정상/만료/변조/없는 계정 JWT, 비활성 계정, 관리자 권한,
일반 /error 접근 차단과 컨테이너 ERROR의 500 보존을 확인한다.
과거 실행 결과와 해당 실행 전용 오프라인 감사기는 저장소에서 정리했다.
비활성 계정의 reset request는 기존 sent=true 계약을 유지하면서 메일/토큰이 생성되지 않아야 한다.
기존 유효 토큰의 confirm은 비활성 오류로 거절되고 소비되지 않아야 한다.

results/<서울날짜>-<run>/에 환경/권한, 합성 DDL·전후 DDL, 기존 User DB 전후 snapshot,
실제 PID/프로필/validate/scheduler/mail/pool 이벤트, HTTP별 method/path/status/errorCode/message/시간,
각 시나리오의 최종 DB 상태와 비밀번호 일치 boolean을 저장한다.
개인정보·인증 자료가 포함된 response data와 request body는 메모리에서만 검사한다.
SQL template와 vendor code/SQLState/스택에는 바인딩 값과 예외 원문을 포함하지 않는다.
test trace ID로 HTTP와 JDBC 이벤트를 연계한다. 매칭 batch 잠금 ID 수와 executor/pool 통계도 저장한다.

## 정리와 범위

finally에서 STOP으로 생성한 JVM만 종료하고 풀 close 이벤트를 확인한다.
중단되지 않으면 해당 자식 프로세스만 종료한다. 서버가 모두 종료된 뒤 새 전용 schema의 fixture를
FK 순서로 정리하며 FK 검사를 끄지 않는다. 전용 schema는 최초 빈 상태이고 그 실행만 소유한다.
30개 테이블 0행과 DDL 불변(AUTO_INCREMENT의 다음 값 증가 제외)을 확인한다.
새 빈 schema/account는 증거 검토를 위해 남기고 다음 실행은 새 이름을 사용한다.
기존 User 스키마의 DDL은 AUTO_INCREMENT까지 정확히 동일해야 한다.

운영 소스·기존 User/Auth 도구 및 증거·마이그레이션의 해시 불변을 확인한다.
OS 강제 종료는 finally를 보장하지 않으므로 중단 시 PID/cleanup 결과를 확인해야 한다.
실제 운영 데이터 마이그레이션·최종 배포·외부 SMTP/AI·ingress·장기 부하는 이번 범위 밖이다.

## 실행 결과 보관

`results/`는 실행 때 생성되는 로컬 산출물이며 Git에서 제외한다. 과거 JSON·TXT·TSV·합성 DDL과 전용 감사기는 저장소에 보관하지 않는다. 새 실행은 과거 결과 없이 재현할 수 있다.

실행 종료, 자식 JVM 종료, 해당 실행의 DB fixture 정리 및 성공/실패 판정을 확인한 뒤 결과 파일을 삭제해도 된다. 실행이 실패·중단됐다면 원인과 잔여 자원을 먼저 확인한다. 파일 삭제는 DB·컨테이너·볼륨 정리를 대신하지 않는다. 보고서의 과거 결과는 기록 요약이다.
