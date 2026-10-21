# 기존 MySQL 8.0.44 검증의 MySQL 8.0.46 재실행

검증일: 2026-10-21, Asia/Seoul. 종료 시각: 19:39:13 KST.
기준 Git SHA: `305a184f8a552897d21614f868fda71ac63aeb7e`.

**최종 결과: 기존 24개 항목을 MySQL 8.0.46에서 모두 재현했다. 24 PASS / 0 FAIL /
0 미완료 / 0 SKIP, 실제 HTTP 162건. 단일 JVM의 실제 양방향 WebSocket 검증도 통과했다.**

## 1. 테스트 환경

| 항목 | 실제 확인 결과 |
| --- | --- |
| Docker 이미지 | 요청한 `mysql:8.0.46-oraclelinux9`, Linux/amd64 |
| 이미지 ID | `sha256:6cd09145362dfe6831b14545de3d5fd6cc75c37cfd6ef8561429c1fc73518b39` |
| RepoDigest | `mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b` |
| 실제 JDBC VERSION() | **8.0.46** |
| Docker 컨텍스트 | 기존 `desktop-linux`, 로컬 named pipe |
| 새 컨테이너 | `kkiri-ccra-mysql-20261021` |
| 컨테이너 ID | `d4a0dd9fb38bebb519bdfdeabb9672666c2500cf255edb58c4d76b58497d687d` |
| 실행별 owner | `77f606a55c23442e9da4aa6ebb334063` |
| 접속 | TCP/JDBC `127.0.0.1:13326 → 3306`, HTTP `127.0.0.1:18081` |
| 새 DB | `kkiriccra77f606a55c23` |
| 서버 UUID | `84be4353-cd3b-11f1-bcc2-6a3c5933a786` |
| 서버 hostname | `kkiri-ccra` |
| 엔진/격리 | 전 테이블 InnoDB / REPEATABLE-READ |
| 문자셋/collation | 새 DB `utf8mb4 / utf8mb4_0900_ai_ci` |
| 세션/JVM | DB 세션 `+00:00`, JVM Asia/Seoul |
| 테이블 이름 | `lower_case_table_names=0`, 기존 8.0.44와 동일 |
| 신규 데이터 공간 | 생성 옵션 `--tmpfs /var/lib/mysql:rw,size=768m`; 기존 볼륨 연결 없음 |
| 제한 | 컨테이너 메모리 1GiB/CPU 2, Java heap 최대 512MiB, 앱 풀 최대 16 |
| 앱 서버 | PID 7556, 실제 Spring MVC/Security/JWT/Service/Repository/JPA |
| 실행 설정 | 빈 새 DB에서만 Entity `create-only`; 앱은 `validate`, SQL `never`, seed `NONE` |
| 외부 연동 | 기존 SMTP/AI 대역 재사용, 수집·이미지 정리 스케줄 비활성 |

DDL/업무 쓰기 전에 VERSION/UUID/hostname/schema/account/role/GRANT를 확인했다.
업무 계정은 이번 새 schema의 권한 및 잠금 관찰용 `performance_schema.data_lock_waits`
SELECT만 갖는다. root는 이번 컨테이너의 새 DB/계정 생성에만 사용한다.
기존 DB/데이터 파일을 재사용하거나 개발·운영 DB에 접속하지 않았다.
비밀번호/JWT 키는 실행 중 임의 생성하며 보고서·증거 JSON·Git에 저장하지 않았다.

### 동일 실행을 유지한 근거

이전 [8.0.44 보고서](CCRA_MYSQL844_VALIDATION.md), RUNBOOK, 원본 실행 로그·`cases.json`,
현재 실행기/Java 소스/관련 설정을 먼저 대조했다. 비교 기준은 8.0.44 최종 통과 실행
`run-7880125a1d1a4e7eb1953c8371a43534`이다. 초기 도구 오류가 있었던 22 PASS/2 FAIL 실행을
최종 기준으로 쓰지 않았으며 그 이력도 삭제하지 않았다.

- 원본 `run.ps1` SHA-256: `ed34e546329ad3e583835fbb67daa10b3c00e90c238fac265539ae62bd739047`
- 테스트 Java SHA-256: `b724104af2ca2ef6e51a842f1ef7feaffa8ee3d79ffa669d156a617cef928a67`
- 앱 JAR SHA-256: `a6fef54637f9c392fa76b46227f706a9ec70fa0cd3513360c730909e323cef79`
- 담당 도메인·마이그레이션·재사용 도구 **125개 파일 hash가 8.0.44 실행과 동일**하다.

원본 기본 실행기는 과거 진단용 이미지 digest를 고정하고 있어, 원본 파일을 수정하지 않고
빌드 폴더의 사본에서 요청한 이미지 태그를 사용했다. 사본 이동에 따른 도구 경로 지정과
신규 컨테이너의 읽기 전용 격리 증거 기록만 추가했다. 테스트 본문·fixture·기대값·timeout·
안전 검사와 운영 코드는 바꾸지 않았다. `-Mysql844`를 사용하지 않아 실제 버전 검사는
정확히 **8.0.46**을 요구한다.

실제 실행 명령(저장소 루트):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/build/ccra-mysql-validation/revalidate-mysql846.ps1 -ApprovedProvisionAndDelete
```

실행 사본 SHA-256:
`3e3adf24748bc308e39df14d038f4a9335c4856bec63009b4da6c88f632ed8ca`.
생성 내역은 `build/ccra-mysql-validation/revalidation-846-plan.json`에 기록했다.
원본 8.0.44 `cases.json`의 24개 이름을 읽어 실제 실행 결과와 대조했다.

## 2. 테스트 결과

| 종류 | 실행 | 성공 | 실패 | 미완료 | 스킵 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 실제 MySQL 연결·신원·초기 schema 및 커뮤니티 DDL | 2 | 2 | 0 | 0 | 0 |
| Community HTTP CRUD/댓글·답글/익명 동시성 | 3 | 3 | 0 | 0 | 0 |
| Report 대상·중복·UTF-16·시각·잘못된 요청 | 5 | 5 | 0 | 0 | 0 |
| Admin 권한·제재·감사·경합·롤백·timeout·회원 상태 | 10 | 10 | 0 | 0 | 0 |
| Chat HTTP 및 실제 WebSocket 연계 | 3 | 3 | 0 | 0 | 0 |
| 물리 PK/FK/UNIQUE/NOT NULL·선행 변경 롤백 | 1 | 1 | 0 | 0 | 0 |
| **합계** | **24** | **24** | **0** | **0** | **0** |

HTTP는 위 case 내부에서 **162건** 실행했다. 독립 case 162개로 부풀려 집계하지 않는다.
채팅 3개 case 중 1개에서 실제 두 WebSocket 클라이언트를 연결해 다음을 검증했다.

- 동일 JVM에서 STOMP 구독 후 양방향 SEND/수신과 MySQL 메시지 커밋.
- HTTP polling으로 읽음 상태가 변경되지 않는 것과 정확한 메시지 ID의 읽음 커밋.
- 잘못된 배치 거절, 비참여자 접근 거절, 나간 참여자의 기존 구독 전달/HTTP 접근 차단.
- 나간 뒤에도 상대가 보낸 메시지와 기존 이력이 DB에 보존되는 것.

나머지 채팅 case에서는 생성/재사용·두 참여자 커밋, 프로필 누락 전체 롤백,
설정 null/빈 객체 의미, 익명 POST/COMMENT 출처 분리·실제 작성자 FK를 검증했다.

관리자 경합에서는 실제 MySQL `data_lock_waits`를 관찰하고 한 처리만 커밋하는 것을 확인했다.
선행 롤백 후 후속 처리, RR stale snapshot refresh, 재진입 미커밋 결정 보존도 통과했다.
세션 InnoDB 3초 timeout은 실제 **1205 / SQLState 40001 → HTTP409**로 관측됐고,
이력 보존 후 재시도가 성공했다. 이번 경과는 **3,644ms**였다.
JPA의 5초 힌트 자체가 적용됐다는 증거로 해석하지 않는다.

물리 제약 거절 8건의 vendor code는
`1062, 1048, 1452, 1451, 1062, 1062, 1452, 1048`이다.
의도적으로 유발한 FK 실패 전에 실행한 UPDATE도 rollback되어 원래 신고 행이 보존됐다.
미래 생성 시각을 주입한 내부 오류의 HTTP500과 제재/이력 전체 rollback도 예상대로 확인했다.
이러한 기대한 거절·강제 예외는 성공한 negative case이며 새 기능 실패로 세지 않는다.

최종 JDBC 재조회 결과:

| 사용자 | 게시글 | 댓글 | 익명 매핑 | 신고 | 채팅방 | 참여자 | 메시지 | 이미지 정리 작업 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 46 | 16 | 28 | 10 | 26 | 4 | 8 | 3 | 0 |

신고 reporter/target 고아 0, 신고 상태·담당자·처리 시각 감사 불변식 위반 0이다.
각 case의 HTTP 결과뿐 아니라 새 JDBC 연결에서 확인한 최종 DB 상태를 함께 기록했다.

## 3. 이전 MySQL 8.0.44 결과와 비교

| 비교 | 8.0.44 최종 | 이번 8.0.46 | 판정 |
| --- | --- | --- | --- |
| 고유 case | 24 PASS | 24 PASS | 이름·순서 동일, 누락/추가 0 |
| 실제 HTTP | 162건 | 162건 | case/메서드/숫자 ID 정규화 경로/상태/오류 코드별 분포 동일 |
| WebSocket | 단일 JVM 두 클라이언트 | 동일 | 양방향 전달·읽음·탈퇴 후 차단 통과 |
| 담당 코드·마이그레이션·재사용 도구 | 기준 125개 파일 | hash 동일 | 운영/검증 로직 변경 없음 |
| 앱 JAR/테스트 Java | 기준 hash | 동일 | 같은 구현·기대값 실행 |
| timeout | 1205/HTTP409, 3,484ms | 1205/HTTP409, 3,644ms | 동일 거절·rollback·재시도 성공 |
| 최종 행 수 | 위 9개 테이블 | 동일 | FK 고아/감사 불변식 위반도 0 |
| 새 실패 | — | 0 | 없음 |
| 기존 범위 중 미검증 | — | 0 | 기존 24개 전부 수행 |

커뮤니티 익명 매핑/이미지 정리 SQL 2개도 기존과 같은 방법으로 재실행했다.
이번에 생성한 빈 schema에서만 이전의 빈 테이블 상태를 재현하고 실제 SQL 파일을 적용했다.
Linux의 lowercase 물리 이름에 맞춰 테이블 식별자만 변환하고 원문 SHA-256/실행 SQL을
`migrations.json`에 기록했다. 원문 SQL 파일의 무수정 실행 검증과는 구분한다.

Playwright는 사용자 요청에 따라 **미실행**이다. 기존 24개에 포함되지 않은 H2 전체 테스트,
과거 별도 채팅 성능/index cycle·다중 JVM 검증도 이번 재실행으로 합산하지 않았다.
이는 기존 8.0.44 범위의 축소나 실패 항목 스킵이 아니다.

## 4. 발견된 문제와 원인 분류

| 분류 | 이번 관찰 | 수정 필요 여부 |
| --- | --- | --- |
| Docker 실행 환경 | 요청한 8.0.46 이미지가 정상 시작하고 JDBC 검증 통과. 이전 exec format error 재발 없음 | 이번 테스트에서 환경 설정 변경 불필요. Docker 설정 조작 없음 |
| MySQL 버전 차이 | 기존 24개에서 새 실패·HTTP 분포 차이 없음 | 변경 불필요. 모든 버그/실행계획의 버전 동등성까지 주장하지 않음 |
| DB 설정/schema | 새 빈 schema 생성, 앱 validate, DDL 2개와 제약·잠금 검사 통과 | 변경 불필요 |
| 테스트 코드 | 이전 최종 통과 소스 그대로 실행. 기대값/timeout 완화 없음 | 변경 불필요 |
| 실제 백엔드 기능 | 기존 기능·예외·rollback·WebSocket 검증 모두 통과 | 운영 코드 수정 없음 |

로그 첫 부분의 javac unchecked 안내가 PowerShell `NativeCommandError` 형식으로 감싸져 있으나,
컴파일/전체 프로세스 exit code는 0이고 실제 case 결과도 24 PASS다. 이 안내를 테스트 실패로
판정하지 않았으며 오류를 숨기기 위해 테스트나 예외 처리를 바꾸지 않았다.

현재 Docker 저장 방식에서는 보호 대상 두 컨테이너가 **실행 전부터 조회되지 않았다**.
따라서 이번 활성 컨텍스트에서 그 두 ID의 상태를 직접 대조했다고 보고하지 않는다.
이것은 테스트 실패나 이번 작업의 삭제를 뜻하지 않는다. 해당 이름의 컨테이너에 시작/중지/
삭제/재생성 작업을 하지 않았고, 저장 방식도 변경하지 않았다.

## 5. 최종 판단과 남은 위험

**MySQL 8.0.44에서 통과했던 기존 24개 검증을 팀 테스트 기준 MySQL 8.0.46에서 정상 재현했다.**
검증한 실제 DB 연결·저장·조회·HTTP API·단일 JVM WebSocket·제약·커밋·rollback·권한·동시성
범위에서 문제를 발견하지 않았다. H2 결과로 실제 MySQL 검증을 대신하지 않았다.

전체 운영 배포, 실제 프록시/TLS·브라우저, 재시작/재연결, 다중 JVM 메시지 중계,
장기 부하/성능, 운영 DDL/백업 복원은 기존 범위 밖이며 이번 통과로 보장하지 않는다.
백업 파일의 hash 보존 확인은 백업 복원 검증과 다르다.
보호 대상 컨테이너의 현재 ID/상태 확인에는 위 Docker 컨텍스트 가시성 제한이 남아 있다.

## 6. 데이터 보존·자원 정리·증거

시작 전에 정리 대상과 영향을 안내했고, 이전에 승인된 새 컨테이너 정리 범위 안에서
실행기가 name/ID/owner를 확인한 후 이번 생성 컨테이너 하나만 삭제했다.
그 안의 신규 DB/계정 및 일회용 tmpfs 데이터만 제거됐다. 앱 PID 종료도 확인했다.
기존 Docker 볼륨을 새 컨테이너에 연결하거나 삭제하지 않았다.

- 실행 전후 컨테이너 목록: 현재 컨텍스트에서 모두 0개.
- 실행 전후 기존 Docker 볼륨 목록: 추가 0 / 제거 0, 동일.
- 기존 데이터 볼륨 `a42846842dbeeef69230f71776cb6e78f08b461f8ab498a918ff431d08acf8ff`도
  전후 목록에 존재한다. 내용에 접근하거나 초기화하지 않았다.
- 보호 백업 `C:\docker-backup\mysql-backup-20261021.tar.gz`: 전후 크기 **5,564,055 bytes**,
  LastWriteTimeUtc 및 SHA-256 동일.
- 백업 SHA-256: `3adda20420aed894c4470b1ffbfc32b56a9b8cc06e09c5374f84c30c36ae410b`.
- Docker Desktop 재시작/설정 변경, 기존 DB 접속/초기화, 브랜치 변경/commit/push/merge 없음.

원본 실행 증거:

- `build/ccra-mysql-validation/revalidation-846-execution.log`
- `build/ccra-mysql-validation/run-77f606a55c23442e9da4aa6ebb334063/`
  내 `artifact.json`, `environment.json`, `grants.json`, `schema.json`, `source-hashes.json`,
  `migrations.json`, `cases.json`, `requests.json`, `servers.json`, `constraints.json`,
  `final-state.json`, `summary.json`.
- 같은 build 경로의 `container-77f606a55c23442e9da4aa6ebb334063.json`,
  `isolation-77f606a55c23442e9da4aa6ebb334063.json`,
  `cleanup-77f606a55c23442e9da4aa6ebb334063.json`,
  `revalidation-846-plan.json`, Docker 전후 목록, 백업 사후 hash, 비교 결과 JSON.

Git 제외 build 결과의 주요 근거와 원본 hash는
[mysql846-evidence.json](tools/ccra-mysql-validation/mysql846-evidence.json)에 보존한다.
증거에는 실제 비밀번호/토큰이나 백업 내용이 없다.
원본 실행기/테스트/운영 코드·설정은 수정하지 않았으며 저장소 추가 파일은 이 보고서와
비밀값 없는 증거 JSON이다. 이전부터 존재한 미커밋 도구/보고서는 그대로 보존했다.

Controller/DTO/Entity 계약 검사도 별도로 통과했다(73 endpoints / 75 record DTOs / 30 entities).
이번에는 동일 JAR/소스로 기존 실제 MySQL 검증을 재실행했으며 불필요한 전체 빌드·H2·
프론트 테스트를 반복하지 않았다.
