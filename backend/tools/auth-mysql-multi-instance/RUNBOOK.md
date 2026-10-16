# 실제 Spring Boot 2개 / MySQL HTTP 검증

실제 BackendApplication·Tomcat을 독립 JVM 두 개에서 실행하고 격리 MySQL을 공유하는 Auth HTTP 검증 도구다. 정상 Gradle/H2 테스트와 별개이며, 실패·불충분 판정은 종료 코드 1이다. 과거 실행 보고서는 정리했고 재실행에 필요한 조건과 절차는 아래에 유지한다.


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

저장소 루트 PowerShell에서:

```powershell
& backend/tools/auth-mysql-multi-instance/run.ps1 -PrepareOnly
& backend/tools/auth-mysql-multi-instance/run.ps1
# 특정 사례만 재현(각 실행도 새 DB·두 서버를 만든다):
& backend/tools/auth-mysql-multi-instance/run.ps1 -Cases @('verification-send-ip-quota-shared')
```

사전 완료된 `backend/build/libs/backend-0.0.1-SNAPSHOT.jar`, Java/Javac 21, Docker 접근이 필요하다.
최초 검증은 기존 531개 테스트의 artifact를 사용했다. 수정 검증에서는 먼저 현재 소스의
`backend/gradlew.bat test bootJar`를 실행한 뒤 새 artifact를 사용한다. CLI 실행은 명시적인 MySQL 테스트 opt-in이다.

실행기는 지정 컨테이너의 ID·running·이미지·3307 매핑과 UUID 파일을 검사한다.
컨테이너 로컬 socket의 root는 별도 테스트 DB·계정 준비 및 해당 schema의 잠금 메타데이터 조회에만 사용하고, 서버에는 전달하지 않는다.
root 비밀번호는 지정 컨테이너 환경에서 메모리로 선별하며 출력/파일 저장하지 않는다.
전용 DB 비밀번호·JWT·메일 IPC 인증값은 실행 중 무작위로 만들고 자식 프로세스 환경변수로 전달한다.
SMTP 자격증명과 기존 DB 계정은 사용하지 않는다.

DB명은 `kkiri_auth_mi_<12자리 hex>`, 계정은 `kkiri_mi_<동일 hex>`로 새로 만든다.
이미 존재하면 중단하며, schema grant의 underscore는 escape해 wildcard 권한을 주지 않는다.
일반 JDBC endpoint는 TCP `127.0.0.1:3307/<새 DB>`로 고정된다. 각 연결에서 DB/계정/버전/UUID/hostname/내부 포트를 검사한다.
전역 USAGE와 해당 리터럴 schema의 ALL PRIVILEGES 두 grant만 허용한다.
기존 `kkiri_auth_test`, `kkiri_kkiri`, 운영 DB에 연결/USE/DDL/DML하지 않는다.

## 스키마·서버 안전 조건

- Hibernate 메타데이터로 **DB 동작 없이** 전체 합성 DDL을 파일에 생성한다. 허용하는 CREATE/ALTER만 검사한 뒤 새 빈 DB에 적용한다.
- 전체 매핑은 현재 30개 테이블이다. `auth_limit_bucket.bucket_key`는 별도 테스트 DDL로 ASCII/ascii_bin 계약을 유지한다.
- 두 서버는 `auth-multi-validation` 프로필, `ddl-auto=validate`, SQL init never, seed NONE, startup crawl false다.
- 테스트 BeanFactoryPostProcessor가 calendar scheduler 및 ScheduledAnnotationBeanPostProcessor를 기동 전에 제거한다.
  실제 컨텍스트에서 스케줄 처리 빈·calendar scheduler·ApplicationRunner가 없음을 확인한다.
- 실제 JavaMailSender 대신 transport가 없는 mock을 주입한다. mock 수신자/본문/인증번호/토큰은 인증된 loopback IPC로 부모 메모리에만 전달한다.
  SMTP fallback host도 `127.0.0.1:1`로 고정하며 실제 transport는 없다.
- HTTP는 loopback 8081/8082만 사용한다. 이미 사용 중이면 중단한다. JWT는 두 JVM에서 동일하다.
- 모든 Auth 한도·window·block·cooldown은 application.properties의 기본값으로 고정한다. 부모 shell 설정으로 바뀌지 않는다.
- 테스트 IP 분리를 위해 신뢰 proxy는 **127.0.0.1/32만** 지정하고 synthetic XFF를 보낸다. 자동 forwarded-header 처리는 NONE이다.
  실제 ingress 보안 검증을 의미하지 않는다.
- JDBC UTC / JVM Asia/Seoul. 서버당 Hikari 최대 12, 대여 timeout 5초, 세션 InnoDB lock wait timeout 2초다.
  빠른 잠금 오류 관측을 위한 **시험값**이며 운영 기본 timeout을 검증했다는 의미는 아니다.

## 시나리오와 판정

로그인 실패/이메일·IP 요청 한도, 이메일 변형(NFC/NFD·악센트·대소문자), 발송 cooldown,
동일 코드·가입 인증 토큰·재설정 토큰 동시 사용, 다른 계정 독립성, 최초 행 생성,
다른 이메일 잠금 격리, 실제 잠금 대기·timeout·강제 교착, mock 발송 실패 롤백,
서버 A 종료·재시작 후 제한 유지, 반복 HTTP 후 풀 반환을 확인한다.

인증 IP 요청 상한 시험은 mock 메일 전체 한도와 섞이지 않도록 JDBC로 미가입 인증 fixture 72개를 준비하고,
검증 자체는 실제 HTTP 72개를 8개 동시 묶음으로 실행한다. 동시에 예약한 `in_flight`도 실패 상한 20에 포함되므로
72개를 한 번에 보내면 순수 요청 상한 60과 다른 제한을 함께 시험하게 된다. 총 60 성공/12 제한 및 DB counter=60 assertion은 유지한다.
로그인 합산 시험은 A/B에서 각각 2개 실패를 먼저 기록한 뒤 20개 동시 요청을 더해 총 24개 중 실패 10/제한 14를 확인한다.
나머지 정상 인증번호/재설정 토큰은 실제 Service→mock 메일에서 얻는다.
한도 판정 assertion을 409/500까지 허용하도록 완화하지 않는다. 해당 응답은 예상 429와 별개 실패다.
의도적으로 주입한 mock 메일 실패의 500과 잠금 timeout의 409는 해당 장애 시나리오에서만 기대한다.
강제 교착은 외부 테스트 JDBC와 실제 HTTP 업무 트랜잭션 사이의 cycle이며 자연 교착과 구분한다.

각 실행의 results 디렉터리에 환경/전용 권한/합성 DDL/전후 DDL, PID·validate·scheduler·pool 이벤트,
시나리오별 HTTP 분포와 DB bucket 변화, 요청별 status/errorCode/message/Retry-After/시간, 집계를 기록한다.
JWT·인증/재설정 토큰·코드·응답 data·이메일·메일 본문·비밀번호는 파일에 저장하지 않는다.
시험용 exception observer는 SQLState/vendor code/클래스/스택만 기록하고 실제 운영 예외 처리로 계속 넘긴다.
현재 기본 실행은 기존 24개+추가 8개=32개다. 추가 사례는 동일 이메일 최초 발송 64개,
서로 다른 이메일 24개(서로 다른 IP), identity mutex를 잡아도 cooldown이 대기하지 않음,
실제 1205/1213 피해자인 HTTP의 새 트랜잭션 복구, 풀 고갈/복구,
원래 132개 조건 3라운드, 공유 전체 메일 한도다. 기존 24개 assertion과 요청 수는 유지한다.
풀 고갈 사례만 테스트 stdin으로 양쪽의 12개 연결을 모두 대여해 의도적인 500을 재현한다.
이 500 및 mock SMTP 장애 500 외의 정상 시나리오 500은 실패다. 풀을 반환한 뒤 같은 요청 8개 성공을 확인한다.
각 HTTP의 서버 생성 X-Request-ID, JDBC lease ID 및 안전한 전체 스택을 연계하고 peak active/waiting을 기록한다.
Hibernate/JDBC의 파라미터 없는 실제 SQL, EXPLAIN 및 정리 전 행수/pending은 별도 JSON에 보존한다.
동시 발송 사례는 컨테이너 로컬 root로 `performance_schema.data_locks` 중 해당 새 schema의
3개 Auth 테이블에 대한 잠금 상태·인덱스·mode만 수집한다. LOCK_DATA/SQL 본문/다른 schema 데이터는 수집하지 않는다.

실패해도 나머지 독립 시나리오는 계속 진행하고 실패를 보존한다. 마지막에 자식 stdin STOP→컨텍스트 close,
필요 시 해당 자식 프로세스만 종료하고 FK 순서로 새 전용 DB fixture를 삭제한다. FK 검사를 끄지 않는다.
DB/계정·빈 전체 스키마는 검사할 수 있도록 남긴다. 비밀번호를 복구/문서화하지 않으며 재실행은 새 DB·계정을 만든다.
AUTO_INCREMENT 증가는 되돌리지 않는다. 이전 검증 증거/운영 소스의 해시 불변도 확인한다.

강제 프로세스 kill/OS 종료는 finally를 보장하지 않으므로 해당 실행의 summary/cleanup/서버 PID를 확인해야 한다.
강제 종료한 서버의 예약 in_flight 복구, 네트워크 단절, 실제 SMTP/LB/운영 데이터, 장기 부하는 별도 시험이다.

## 실행 결과 보관

`results/`는 실행 때 생성되는 로컬 산출물이며 Git에서 제외한다. 과거 JSON·TXT·TSV·합성 DDL과 전용 감사기는 저장소에 보관하지 않는다. 새 실행은 과거 결과 없이 재현할 수 있다.

실행 종료, 자식 JVM 종료, 해당 실행의 DB fixture 정리 및 성공/실패 판정을 확인한 뒤 결과 파일을 삭제해도 된다. 실행이 실패·중단됐다면 원인과 잔여 자원을 먼저 확인한다. 파일 삭제는 DB·컨테이너·볼륨 정리를 대신하지 않는다. 보고서의 과거 결과는 기록 요약이다.
