# Community · Chat · Report · Admin MySQL 검증 — 환경 차단

후속 결과: 사용자가 별도 승인한 **MySQL 8.0.44 경로는 최종 24/24 PASS**했다.
[호환성 검토와 실제 실행 보고서](CCRA_MYSQL844_VALIDATION.md)를 따른다.
아래는 8.0.46 이미지의 시작 실패 당시 기록이며 실패/미실행 이력을 그대로 보존한다.

검증일: 2026-10-21, Asia/Seoul.
기준 Git HEAD: `305a184f8a552897d21614f868fda71ac63aeb7e`.
시작 시 작업 트리는 깨끗했다. 이번 도구/보고서는 미커밋 변경이며 commit/push/merge는 하지 않았다.

**판정: 실제 MySQL 검증 미완료, 다음 단계 승인 불가.**
Docker MySQL이 `exec format error`로 종료돼 JDBC 연결·DB/계정 생성·업무 테스트에
진입하지 못했다. 사용자가 Docker Desktop 재시작을 보류하고 차단 근거와 준비한 도구 보고를
요청하여 추가 환경 변경을 중단했다. 컴파일 성공을 MySQL 통과로 처리하지 않는다.

## 사전 비교와 우선순위

`API.md`, `ERD.md`, `DEPLOYMENT.md`, `INTEGRATION_TEST_GUIDE.md`,
`STAGES_1_3_VALIDATION.md`, `tools/`, `migrations/`, 관련 Controller/DTO/Service/Repository/Entity,
최신 H2 보강 보고서와 기존 MySQL 보고서를 확인했다.

| 범위 | 기존 근거와 이번 우선순위 |
| --- | --- |
| Community | `STAGES_1_3_VALIDATION.md`의 MySQL 좋아요/소유권 검증은 재작성하지 않는다. 최신 `INTEGRATION_TEST_REVIEW_COMMUNITY_CHAT_REPORT_ADMIN.md`의 댓글/답글 HTTP 흐름·Principal·오류 시 DB 보존을 MySQL에서 확인하도록 준비했다. 익명 번호 동시 생성과 커뮤니티 수동 DDL도 포함한다. |
| Chat | `CHAT_MYSQL_FINAL_REVALIDATION.md`의 19/19 회귀와 receipt·인덱스 cycle·성능 근거를 보존한다. 기존 전체 47회 중 baseline 성능 실패 1건도 통과로 덮어쓰지 않는다. 이번 우선순위는 실제 HTTP 생성 커밋/실패 롤백·설정 null 의미 및 미검증으로 남은 단일 JVM 양방향 WebSocket이다. |
| Report | `REPORT_ADMIN_VALIDATION.md`는 실제 MySQL 미실행을 명시한다. 상세 사유 UTF-16 길이, DATETIME(0), POST/COMMENT 작성자 FK·nullable 참조, 처리 상태별 중복과 다른 신고자의 독립성을 우선한다. |
| Admin | 최근 REPORT 단일 행 잠금/refresh 변경의 실제 InnoDB RR 경합·선행 롤백·timeout·제재 원자성, 댓글 삭제 연계·감사 이력·상태 전환을 우선한다. H2의 `BLOCKER_ID` 검사를 MySQL 근거로 대체하지 않는다. |

커밋 제목 `12e0581`에는 MySQL 검증이라고 적혀 있으나 해당 변경의 보고서와 테스트는
H2이며 실제 MySQL 미실행을 명시한다. 제목만으로 기존 MySQL 통과 근거로 채택하지 않았다.
이번에는 기존 보고서의 결과를 현재 SHA의 실행 결과로 합산하지 않는다.

## 실제 실행과 차단 근거

사용자가 승인한 생성/검증 후 삭제 대상:

| 항목 | 요청/관찰 |
| --- | --- |
| 컨테이너 | 새 `kkiri-ccra-mysql-20261021`, 실행별 임의 owner 라벨 |
| 호스트/포트 | 로컬 Docker named pipe, `127.0.0.1:13326 → 3306` |
| 이미지 | 로컬 `mysql:8.0.46-oraclelinux9`; 이후 동일 공식 버전의 amd64 digest도 별도 조회/pull |
| 공식 amd64 manifest | `sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b` |
| 기존 태그 digest | `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b` |
| 아키텍처 메타데이터 | 이미지 `linux/amd64`, Docker 서버 `linux/x86_64` |
| 데이터/제한 | 새 컨테이너 내부 tmpfs 768MiB, 메모리 1GiB, CPU 2개, 기존 볼륨 미사용 |
| 실제 서버 버전 | **미확인**. 이미지 버전은 8.0.46이나 `SELECT VERSION()`까지 접속하지 못함 |
| DB/계정/권한 | **생성 및 JDBC 확인 미실행**. MySQL 시작 전 실패 |
| 최초 진단 | `exec /usr/local/bin/docker-entrypoint.sh: exec format error` |
| 직접 bash 실행 진단 | `exec /bin/bash: exec format error` |
| 파일 진단 | 새 컨테이너에서 복사한 `/usr/bin/bash` 길이 0바이트. 원인이 이미지 계층/스냅샷/복사 경로 중 어디인지는 확정하지 않음 |
| 종료 상태 | 관측한 startup 상태는 exit 255, `OOMKilled=false` |

태그 실행, 상태/로그 진단 보강, 진입 스크립트 복사, bash 직접 실행, 공식 amd64 digest 실행을
합쳐 MySQL 시작 시도 **5회 모두 실패**했다. 별도의 컨테이너 파일 진단 **1회**도 수행했다.
총 생성한 새 컨테이너 **6개 모두** ID/name/owner 확인 후 삭제했다.
첫 진단 과정에서 PowerShell의 native stderr 처리가 원래 오류를 가린 부분은 실행기에서 보완했다.
이를 애플리케이션 결함이나 MySQL 업무 테스트 실패로 집계하지 않는다.

공식 digest pull은 기존 태그/컨테이너를 교체하거나 이미지를 삭제하지 않았다.
pull로 추가된 digest 참조는 유지한다. Docker Desktop 재시작/초기화/설정 변경은 하지 않았다.

정리 후 남은 기존 컨테이너는 시작 전과 동일한 다음 2개이며 중지 상태를 유지한다.

| 기존 컨테이너 | ID | 이미지 |
| --- | --- | --- |
| `kkiri-auth-mysql-test` | `42528a4bbec4` | `mysql:8.0.44` |
| `kkiri-auth-mysql-backup` | `3acf9c77ee56` | `mysql:8.0.46-oraclelinux9` |

개발/운영 DB, 기존 DB/계정/인증 파일, 다른 팀원 컨테이너에 쓰기·초기화·삭제를 수행하지 않았다.
`.env`를 읽지 않았고 임의 생성한 테스트 비밀번호도 출력/파일 저장하지 않았다.

## 실행 개수 및 확인 결과

| 구분 | 실행 | 통과 | 실패 | 스킵 | 설명 |
| --- | ---: | ---: | ---: | ---: | --- |
| 실제 MySQL 업무 case | **0** | **0** | **0** | **0** | 준비한 24개 case는 환경 차단으로 모두 미실행. JUnit skip으로 보고하지 않음 |
| 실제 HTTP 요청 | **0** | — | — | — | Spring 서버 미시작 |
| 실제 WebSocket/STOMP | **0** | — | — | — | 클라이언트 미실행 |
| MySQL 시작 시도 | **5** | **0** | **5** | **0** | 업무 테스트와 별도인 환경 실패 |
| 컨테이너 파일 진단 | **1** | — | — | — | bash 0바이트 관찰; ELF machine 판독 불가 |
| 생성 컨테이너 정리 | **6** | **6** | **0** | **0** | owner 확인 후 해당 생성 자원만 삭제 |

별도 준비/정적 확인:

- `backend`에서 `.\gradlew.bat bootJar`: **BUILD SUCCESSFUL**, 기존 결과 up-to-date.
- `run.ps1 -PrepareOnly`: 최종 Java 실행기 **컴파일 성공**, DB/Docker 접근 없음.
- `node backend/tools/sync-contract-docs.cjs --check`: **73 endpoints / 75 record DTOs / 30 entities 통과**.
- 운영/프론트 코드를 바꾸지 않아 전체 H2 테스트와 프론트 테스트/빌드는 재실행하지 않았다.
  기존 통과 건수는 이번 MySQL 결과에 포함하지 않는다.

## 준비한 도구와 변경 파일

기존 `tools/matching-mysql-multi-instance/`의 서버·HTTP·JWT·동시 요청·증거 기록과
`MatchingExtraChecks.SocketClient`를 재사용한다. 기존 소스/공통 테스트 설정을 수정하지 않고
빌드 디렉터리의 생성본에서 전용 포트/접두사와 안전 검사·대역만 적용한다.
기존 채팅 성능/인덱스 검증기는 재실행하거나 복제하지 않았다.

| 새 파일 | 목적 |
| --- | --- |
| [run.ps1](tools/ccra-mysql-validation/run.ps1) | 컴파일, 승인 opt-in, 정확한 새 컨테이너 생성/신원 확인/소유권 기반 삭제, 환경 진단 |
| [CcraMysqlValidation.java](tools/ccra-mysql-validation/CcraMysqlValidation.java) | 기존 실행기를 재사용하는 전용 case·fixture·JDBC 상태 검증·증거 기록 |
| [RUNBOOK.md](tools/ccra-mysql-validation/RUNBOOK.md) | 실행 방법·안전 경계·준비 범위·미실행 상태 |
| [blocked-evidence.json](tools/ccra-mysql-validation/blocked-evidence.json) | SHA, 환경 실패/정리 ID, 시작 상태·정제 로그의 비밀값 없는 추적 가능한 요약 |
| 이 보고서 | 기존 근거 비교, 실제 실행/미실행 구분과 판정 |

실행 원본 근거는 `build/ccra-mysql-validation/execution.log`, `execution-2.log`~`execution-5.log`,
`startup-<owner>.json/.log`, `cleanup-<owner>.json`이다. Git 제외 build 산출물이므로 주요 안전한
근거를 위 `blocked-evidence.json`에도 보관한다. 업무 `cases.json`/`summary.json`은 생성되지 않았다.

컴파일만 확인한 실행기는 아직 검증된 MySQL 도구로 승격할 수 없다.
실제 MySQL 환경에서 fixture·GRANT/메타데이터 기대값·물리 SQL 오류·잠금 관찰을 먼저 확인해야 한다.
사용자 지시대로 실패 기대값을 완화하거나 운영 코드를 수정해 통과 처리하지 않았다.

## 미검증과 다음 단계

담당 4개 도메인의 이번 SHA 기준 MySQL CRUD·제약·커밋/롤백·동시성·인가·예외·도메인 연계·
최신 변경 전부가 이번 실행에서는 미검증이다. 새 커뮤니티 마이그레이션도 적용하지 못했다.
기존 채팅 MySQL 기록은 당시 증거로만 유지하며 현재 Docker 환경 검증을 대체하지 않는다.

준비된 커뮤니티 마이그레이션 검사는 Linux 물리 lowercase 이름에 맞춰 **테이블 식별자만**
치환하고 원문 hash/실행 SQL을 남기는 방식이다. 원문 SQL의 무수정 실행 통과와 구분해야 한다.
기존 채팅 receipt/익명 key/index 재실행은 기존 충분한 검증 범위를 중복 작성하지 않았다.
미확정 익명성·신고 정책은 변경하지 않았다.

다른 팀원의 브랜치·운영 소스·마이그레이션·공통 Gradle 설정 변경은 없다.
새 파일은 이번 도메인용 도구/보고서뿐이다. 런타임에 실제 SMTP/AI를 호출하지 않았다.

**진행 조건:** Docker 환경 문제를 별도 승인된 작업으로 해결한 후 이 도구를 실제 실행하고,
HTTP 판정과 `final-state.json`, lock/SQL 오류 근거·정리 결과를 함께 검토해야 한다.
사용자 보류 지시가 유지되는 동안 Docker 재시작이나 추가 컨테이너 시도를 수행하지 않는다.
현재 결과로 다음 검증 단계 완료나 운영 배포 가능을 선언할 수 없다.
