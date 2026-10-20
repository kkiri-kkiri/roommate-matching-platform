# MySQL 테스트 계정 1045 진단·승인된 복구 결과

검증일: 2026-10-20 (Asia/Seoul), 브랜치 `test/chat-validation`.
이 인증 작업 이후 별도 요청으로 실행한 성능/인덱스 검증은
[최종 재검증 보고서](CHAT_MYSQL_FINAL_REVALIDATION.md)를 따른다. 아래 미실행은 인증 단계의 범위다.
범위는 인증 복구와 로그인/접속 대상/권한 확인이다. 성능·EXPLAIN·마이그레이션·동시성 검사는 실행하지 않았다.
기존 미커밋 코드/문서 변경은 보존했다. AGENTS.md, backend/agent.md, 이전 MySQL/조회 후속 보고서와
검증 스크립트·manifest를 먼저 확인했다. 비밀번호·암호문·인증 해시는 출력하거나 보고서에 기록하지 않았다.

## 1. 인증 오류의 원인과 확인 범위

확인된 재접속 장애 원인은 **완료된 테스트 계정의 유효한 비밀번호가 보존되지 않은 상태**였다.
`202610191135f9f3.environment.json`은 credentialStorage=process-memory-only이며 credentialFile이 없다.
기존 run.ps1은 난수 비밀번호를 생성해 KKIRI_CHAT_TEST_PASSWORD로 Java에 전달하고 종료 시 제거한다.
초기 진단 시 환경변수와 해당 계정의 credential 파일 모두 없었다. 이전 제공 값으로는 1045가 발생했다.
그 값과 서버 인증 해시를 비교하거나 원래 난수 비밀번호를 알아낸 것은 아니므로 비밀번호의 정확한
원래 값·과거 파일 생성 실패의 원인을 소급 확정하지 않는다.

| 진단 항목 | 실제 결과 |
| --- | --- |
| 기존 CLIXML | `202610195a7b340c.credential.clixml` 존재. 성공 계정이 아닌 `kkirichattest_5a7b340c`용 |
| 파일 내용의 구조 | Password 속성·SS 요소·DPAPI payload marker 없음. import는 성공하지만 usable PSCredential이 아닌 PSObject |
| 경로 | earlier manifest의 credentialFile이 현재 파일로 정상 resolve됨. 경로 변경 증거 없음 |
| earlier 환경 | 해당 계정/DB 두 개는 현재 서버에 없고 manifest도 생성 도중 상태. 성공한 환경으로 대체 사용하지 않음 |
| 현재 PowerShell | sandbox/실제 사용자 모두 Windows PowerShell 5.1.26100.9711 Desktop, 64bit. pwsh 없음 |
| sandbox Windows 사용자 | DESKTOP-8K385BA\\CodexSandboxOffline, SID 끝 1005. 새 DPAPI Export에서 CryptographicException 재현 |
| 실제 Windows 사용자 | DESKTOP-8K385BA\\임예명, SID 끝 1001. DPAPI Export/Import 및 비밀번호 왕복 일치 확인 |
| 원래 파일의 암호화 사용자 | 생성 사용자 기록이 없어 확정 불가. 소유자는 sandbox 사용자이나 파일 소유권만으로 암호화 주체를 입증하지 않음 |
| Host·계정 상태 | 대상 계정 Host=127.0.0.1, caching_sha2_password, locked=N, passwordExpired=N |
| JDBC/CLI 주소 | 기존 JDBC도 127.0.0.1:3306 고정, 이번 CLI도 TCP/127.0.0.1:3306. CURRENT_USER()는 대상 @127.0.0.1로 확인 |
| 이름 해석 | USER()의 @localhost 표시는 관측됐지만 CURRENT_USER()는 정확히 @127.0.0.1. Host 변경이 필요하지 않았음 |
| 자격증명 전달 | 기존 runner는 process 환경변수만 사용. 이번에는 명시적 -CredentialFile의 타입/계정 검사 후 메모리 전달을 추가 |

이번 sandbox DPAPI 실패 때도 Password 없는 미완성 파일이 만들어졌다. 원래 파일과 같은 구조의
실패를 재현했지만, 원래 파일이 반드시 같은 이유로 불완전해졌다고 단정하지 않는다.
원래 파일에는 복호화할 비밀번호 자체가 없어 다른 사용자/PowerShell로 import하는 것만으로 복구할 수 없었다.
Windows CLIXML의 DPAPI 암호화는 같은 컴퓨터·Windows 사용자에 묶인다는
[Microsoft Import-Clixml 문서](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/import-clixml?view=powershell-7.6)와 대조했다.
TCP 연결 주소 확인은 [MySQL 연결 옵션 문서](https://dev.mysql.com/doc/refman/8.0/en/connecting.html)를 따른다.

## 2. 확인한 테스트 환경

| 항목 | 현재 확인 결과 |
| --- | --- |
| 서버 | 127.0.0.1:3306 / DESKTOP-8K385BA / MySQL 8.0.44 |
| UUID | fc8cfa74-c4e9-11f0-bf28-00a554698b2d |
| 계정 | kkirichattest_1135f9f3@127.0.0.1 |
| 현재 스키마 DB | kkirichatvalidation202610191135f9f3 |
| legacy DB | kkirichatlegacy202610191135f9f3 |
| 역할 | CURRENT_ROLE()=NONE, mandatory_roles 빈 값, 부여된 역할/기본 역할 0개 |
| 두 DB의 권한 | 각각 SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER |
| 전역/기타 권한 | 전역 USAGE만 있으며 GRANT OPTION·기존 DB·다른 스키마 권한 없음 |
| 보호 DB 접근 검사 | kkirikkiri / kkiri_kkiri의 chat_message LIMIT 0 SELECT 모두 1142 거절 |

서버 메타데이터는 로컬 .env의 DB 연결 항목만 메모리로 읽은 진단 연결에서 SELECT/SHOW로
확인했다. SMTP/JWT/AI 값·인증 해시는 출력/조회하지 않았다. 복구 후에는 **테스트 전용 계정**으로
각 테스트 DB를 선택해 로그인·UUID/계정/역할·SHOW GRANTS를 다시 확인했다.
보호 DB에 쓰기를 시도하여 권한을 시험하지 않았다.

## 3. 인증 복구 성공 여부

**성공. 원래 비밀번호 복원은 불가능했으며, 사용자 승인 후 해당 전용 계정의 비밀번호만 재설정했다.**

1. 정확한 대상/변경 범위를 보고했고 사용자가 “승인 — 해당 계정 비밀번호만 재설정”을 선택했다.
2. OS 암호학적 난수로 새 값을 만들고 기존 credential 경로 충돌 시 중단하도록 했다.
3. sandbox에서는 DPAPI Export가 실패해 **ALTER USER 전에 중단**했다. 새 미완성 파일은
   failed-sandbox 이름으로 보존했고 이전 파일/DB/계정은 삭제하지 않았다.
4. 실제 사용자 임예명 환경에서 새 DPAPI 파일 저장·소유자 전용 ACL·Import 왕복 일치를 먼저 확인했다.
5. 다음 작업을 딱 한 번 성공시켰다. 실제 비밀값은 stdin/프로세스 메모리에만 사용했다.

```sql
-- 사용자 승인 후 수행한 작업의 형태. 아래 placeholder는 실행용 비밀번호가 아니다.
ALTER USER 'kkirichattest_1135f9f3'@'127.0.0.1'
  IDENTIFIED WITH caching_sha2_password BY '<새 난수 비밀번호>';
```

6. 최초 후속 검사는 CURRENT_ROLE()의 MySQL JSON 바이너리 표현 때문에 값 비교가 실패했다.
   실제 로그인은 성공했다. CAST(... AS CHAR CHARACTER SET utf8mb4)로 검증 쿼리만 고친 뒤,
   **비밀번호를 다시 변경하지 않고** 보존 파일에서 복호화하여 두 DB 로그인/권한 검사를 통과했다.

현재 유효한 파일은 `build/chat-mysql-validation/202610191135f9f3.credential.clixml`이다.
실제 생성 사용자 `DESKTOP-8K385BA\\임예명`과 같은 컴퓨터에서만 사용한다. 비밀번호를 채팅에
보내거나 .env/문서에 복사하지 않는다. `202610191135f9f3.credential-metadata.json`에 소유 SID와
검증 완료 상태를 기록했다. 두 파일은 build 아래 Git 제외 대상이다.
원래 manifest의 과거 memory-only 기록과 실패한 earlier 파일은 덮어쓰지 않았다.

## 4. 기존 DB 및 계정 변경 여부

- **변경:** 승인된 `kkirichattest_1135f9f3@127.0.0.1` 비밀번호만 한 번 재설정했다.
- **미변경:** Host·인증 플러그인·잠금/만료 상태·기존 권한. 변경 전후 메타데이터가 동일하다.
- **미변경:** 두 테스트 DB, kkirikkiri, kkiri_kkiri의 스키마/업무 데이터. DML/테이블 DDL/초기화/삭제를 실행하지 않았다.
- 서비스/관리자 비밀번호 변경, 신규 DB/계정 생성, GRANT/REVOKE, 서버 설정·Docker 조작 없음.
- 운영 코드 변경·Git commit/push/merge 없음. 테이블 데이터 전체 snapshot/hash 검사는 이번 범위가 아니다.

추가/변경 파일:

- `tools/chat-mysql-validation/diagnose-auth.ps1`: 기본은 로컬 구조 진단, -ReadServerMetadata에서만 제한된 메타데이터 조회.
- `tools/chat-mysql-validation/recover-auth.ps1`: 기본은 DPAPI 로그인/권한 확인만 수행. 명시적 승인용 switch를 줘야 reset을 시도하며 기존 파일이 있으면 반복 reset을 거절.
- `tools/chat-mysql-validation/run.ps1`: 명시적 -CredentialFile 입력·PSCredential/계정 일치 검사·환경변수 정리. 이번에는 runner를 실행하지 않음.
- RUNBOOK·이 문서·이전 두 MySQL 보고서: 인증 복구의 현재 상태와 과거 실패 구분.

## 5. 추가 승인 필요 여부

이번 대상의 비밀번호 변경 승인은 받았고 승인 범위 안에서 완료했다. 현재 로그인/권한 확인에
추가 계정·Host·권한 변경은 필요하지 않다. 추가 비밀번호 변경·새 계정·권한 변경은 별도 승인 대상이다.
기존 파일이 있으므로 reset switch를 반복 실행하지 않는다. 사용자를 바꾸어 DPAPI 복호화가 실패하면
해당 생성 사용자 환경에서 실행하며 다른 계정의 파일이나 추측한 비밀번호로 우회하지 않는다.

## 6. MySQL 재검증 진행 가능 여부 및 이번 검증

**인증 장애는 해소돼 같은 Windows 사용자에서 재검증 준비가 가능하다.** 이번 요청에 따라
문제 10·11의 성능/EXPLAIN·인덱스 적용/롤백/재적용·동시성은 실행하지 않았다. 해당 통과 판정과
운영 배포 보류는 그대로다. 다음 단계는 별도 요청 범위에 따라 필요한 검사만 실행한다.

| 검사 | 결과 |
| --- | --- |
| 실제 사용자 DPAPI export/import 왕복 | PASS |
| 두 테스트 DB의 전용 계정 로그인/선택 DB/UUID/역할 | PASS |
| 필요한 권한 및 기존 DB 쓰기 권한 부재 | 정확한 SHOW GRANTS·역할 검사 PASS |
| 기존 DB 읽기 접근 제한 | 두 곳 모두 SQL 1142, PASS |
| Host/plugin/lock/expiry/grants·DB 목록 전후 대조 | 동일 |
| PowerShell 스크립트 3개 구문 검사 | PASS |
| 계약/변경 형식 검사 | 73 endpoints / 75 record DTOs / 30 entities 일치, git diff --check PASS |
| credential/상태/진단 산출물 Git 제외 | git check-ignore로 확인 |
| H2/전체 빌드·성능·마이그레이션 | 이번 단계 미실행 |
| JDBC 실제 로그인 | 이번에는 CLI로만 확인. JDBC 주소/환경변수 사용 코드를 대조했으며 runner 실행은 하지 않음 |

증거는 Git 제외 `build/chat-mysql-validation/` 아래에 있다. 파일에는 비밀번호가 없다.

- `auth-diagnosis-20261020.json`, `auth-diagnosis-after-20261020.json`: 메타데이터/DPAPI 구조 전후.
- `auth-check-820326a1359342bbb3dfd5b8d6581938.json`: sandbox DPAPI 실패, reset 미시도.
- `auth-check-df8a89cfe8b94a778fb8b9a072411117.json`: 실제 사용자 DPAPI 및 reset 성공, 후속 비교 실패.
- `auth-check-2a29f9b07db04f65acd366d3561615f8.json`: reset 없는 재로그인 성공·역할 표현 비교 실패.
- `auth-check-a1d0bc6947a84596ae97c82ca8152915.json`: reset 없이 최종 두 DB·권한·보호 DB 차단 모두 PASS.
- `auth-check-8cdd4ab94e5147c5ace1c7da9fd337b8.json`: 최종 도구 버전에서 mandatory_roles 빈 값도
  전용 계정으로 확인, 같은 두 DB/권한/차단 검사 PASS. 비밀번호 추가 변경 없음.

과거 1045·DPAPI 실패·검증 도구 실패를 최종 PASS로 소급 변경하지 않는다.
기존 기능 변경 파일 5개(조회 Repository/회귀 Java/인덱스 SQL/기존 검증 Java/QueryComparison)의
SHA-256이 이전 작업 기록과 동일함도 확인했다. 이번 수정 범위는 인증 도구·연결 전달·문서다.
