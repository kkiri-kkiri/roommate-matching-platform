# Community / Chat / Report / Admin 전용 MySQL 검증

현재 상태는 **MySQL 8.0.44 최종 24/24 PASS, HTTP 162건**이다.
8.0.46 이미지의 `exec format error` 이력은 [이전 차단 보고서](../../CCRA_MYSQL_VALIDATION.md)에
보존했다. 호환성 검토 및 실제 실행 결과는 [8.0.44 후속 보고서](../../CCRA_MYSQL844_VALIDATION.md)를 따른다.
8.0.44 결과를 8.0.46의 버그 수정·실행계획·운영 배포 검증으로 대체하지 않는다.

## 기존 도구 재사용

`../matching-mysql-multi-instance/`의 `MatchingMultiInstance`에 있는 실제 HTTP,
별도 JVM 시작/종료, JWT, 요청/오류 기록과 동시 요청 도구를 상속한다.
`MatchingExtraChecks.SocketClient`의 실제 STOMP 클라이언트와 메시지 저장 대기를 재사용한다.
기존 매칭·설문·인증·달력 시나리오 및 기존 정리 함수는 호출하지 않는다.

기존 소스는 수정하지 않는다. `build/ccra-mysql-validation/generated`에서만
`MatchingMultiSupport`의 고정 포트와 schema/account 접두사를 치환하고,
정확한 GRANT 검사와 서버의 AI/crawler 대역 및 이미지 전용 경로를 추가한다.
명시적인 `-Mysql844` 경로에서만 생성본의 버전 검사를 정확한 `8.0.44`로 고정한다.
기존 E2E/도메인 실행기 및 이 실행기의 기본 8.0.46 설정은 유지한다.
재사용 코드가 달라지면 adapter와 기대값을 먼저 검토한다.
Gradle test/H2 설정과 운영 소스는 변경하지 않는다.

## 실행

저장소 루트의 PowerShell에서 Java/Javac 21 및 최신 `bootJar`가 필요하다.

```powershell
Push-Location backend
.\gradlew.bat bootJar
Pop-Location

# DB/Docker 접근 없는 컴파일
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-mysql-validation/run.ps1 -PrepareOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-mysql-validation/run.ps1 -PrepareOnly -Mysql844

# 아래 실행은 새 컨테이너/DB/계정 생성과 해당 자원 삭제가 승인된 경우에만 사용한다.
# 이번에 승인·실행한 8.0.44 경로. Docker 복구/재시작은 하지 않는다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-mysql-validation/run.ps1 -Mysql844 -ApprovedProvisionAndDelete
```

`-Mysql844`의 정확한 대상은 **새** `kkiri-ccra-mysql844-20261021`, `mysql:8.0.44`,
`127.0.0.1:13326`이다. 이번 관측 이미지 digest는
`sha256:9c3380eac945af0736031b200027f581925927c81e010056214a4bd6b6693714`다.
결과/생성 소스는 `build/ccra-mysql844-validation/`에 분리한다.

선택하지 않은 기본 경로는 `kkiri-ccra-mysql-20261021`, 공식 `mysql:8.0.46-oraclelinux9`의 amd64 digest
`mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b`를
고정한다. 기존 태그를 바꾸지 않으며 자동 pull은 하지 않는다.
Docker는 로컬 named pipe만 허용한다. 대상 이름 또는 HTTP `18081`/DB `13326` 포트가
점유됐으면 실패한다. 기존 컨테이너를 재사용하거나 종료하지 않는다.

MySQL 데이터는 새 컨테이너의 768MiB tmpfs, 메모리 제한 1GiB, CPU 제한 2개다.
새 schema `kkiriccra<12자리 hex>`, 계정 `ccra_<동일 hex>@%`를 매번 생성한다.
계정은 정확한 새 schema의 권한과, 경합 관찰용
`performance_schema.data_lock_waits` SELECT만 가진다. root는 이 새 컨테이너에서
schema/account 생성에만 사용한다. 실행 프로세스에 root 자격증명을 전달하지 않는다.
비밀번호/JWT 키는 임의 생성하고 출력하거나 파일에 저장하지 않는다. `.env`를 읽지 않는다.

JDBC로 서버 UUID/hostname/version/port/schema/account/role/GRANT/UTC 세션을 검사한다.
빈 schema에만 Entity `create-only`를 적용한다. 서버는 `validate`, SQL `never`, seed `NONE`,
수집/이미지 정리 스케줄 비활성, SMTP 전송 불가능 대역 및 고정 AI 대역으로 시작한다.
테스트 서버는 **하나의 JVM**이다. 다중 JVM 검증으로 해석하지 않는다.

## 검증 범위와 경계

환경/DDL 2개와 업무 22개, 총 24개 case를 실제 실행했다. 최종 24개 모두 통과했다.

- Community: multipart 게시글 CRUD, 댓글/답글 1000자 경계·Principal·권한·거절 시 보존,
  익명 번호 8개 동시 생성과 매핑 고유성.
- Report: USER/POST/COMMENT 실제 작성자 FK·nullable 참조, 상태별 중복 차단,
  서로 다른 신고자, ASCII/한글/이모지 UTF-16 499/500/501/70000, DATETIME(0), 잘못된 요청.
- Admin: 권한/상태, 댓글 삭제와 답글·감사 이력·대시보드, REPORT 잠금 경합 4종,
  선행 롤백·RR stale refresh·재진입 보존, 제재 후 예외 전체 롤백, HTTP409/복구,
  회원 상태 전환과 기존 JWT 차단.
- Chat: 실제 HTTP 생성/재사용 커밋·프로필 누락 전체 롤백, 설정 null 의미,
  단일 JVM 양방향 실제 WebSocket·HTTP 읽음·나가기 후 전달 차단, 익명 출처 연결.
- 물리 제약: PK/FK/UNIQUE/NOT NULL의 MySQL 오류와 앞선 UPDATE 롤백·최종 행 검사.

관리자 경합은 3초 InnoDB session timeout 전에 `data_lock_waits`를 관찰한다.
JPA의 5초 lock hint가 적용됐다고 주장하지 않는다. 미래 생성 시각을 주입한 내부 오류는
현재 전역 예외 처리에 따라 HTTP500/INTERNAL_SERVER_ERROR를 기대하며 제재 롤백을 검사한다.

커뮤니티의 익명 매핑/이미지 정리 마이그레이션 2개는 새 schema의 해당 빈 테이블에만
구 스키마 상태를 구성하고 실제 SQL 파일을 적용했다. Linux의 관찰된 lowercase
물리 이름에 맞춰 테이블 식별자만 치환하며 원문 SHA-256/실행 SQL을 기록한다.
원문 파일의 무수정 실행 검증과 구분한다. 운영 마이그레이션 파일을 바꾸지 않는다.
기존 채팅 receipt/익명 key/index cycle·대규모 성능 검사는 기존 도구와 보고서를 유지한다.

## 증거 및 정리

서버까지 실행한 `run-<owner>/`에 `artifact.json`(Git SHA/JAR hash),
`source-hashes.json`, `environment.json`, `grants.json`, `schema.json`, `migrations.json`,
`cases.json`, `requests.json`, `servers.json`, `constraints.json`, `final-state.json`,
`summary.json`을 기록했다. 첫 실행 22 PASS/2 FAIL과 최종 24 PASS를 별도 run으로 보존한다.
첫 실패는 잘못된 댓글 GET 경로와 dashboard의 `data.stats` 누락이라는 도구 오류였으며,
응답 경로를 현재 계약에 맞춰 수정했다. 기대값을 완화하지 않았다.

각 case의 HTTP 결과와 새 JDBC 연결에서 조회한 커밋 상태/rollback 결과를 함께 확인한다.
최종 상태를 기록한 뒤 서버를 종료하고, PowerShell의 finally가 container ID/name/owner label을
검사한 후 **이번 생성 컨테이너만** 삭제한다. `cleanup-<owner>.json`으로 삭제를 기록한다.
기존 DB/컨테이너/인증/볼륨을 정리 대상으로 채택하지 않는다.
비정상 강제 종료 시 finally 실행은 보장되지 않으므로 소유권을 확인한 수동 정리가 필요하다.

`-DiagnoseOnly`는 승인된 동일 이름으로 새 컨테이너를 생성해 bash/env ELF 길이와 machine을
확인하고 삭제한다. DB 테스트가 아니다. 시작 실패는 별도의 startup 상태/정제 로그로 보존한다.
Docker Desktop 재시작/설정 변경/이미지 삭제/기존 컨테이너 조작은 수행하지 않는다.
