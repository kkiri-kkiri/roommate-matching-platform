# Community · Chat · Report · Admin — MySQL 8.0.44 후속 검증

검증일: 2026-10-21, Asia/Seoul.
기준 Git HEAD: `305a184f8a552897d21614f868fda71ac63aeb7e`.
운영 코드·기존 E2E/도메인 실행기·공통 Gradle 설정은 변경하지 않았다.
이번 전용 도구/보고서는 미커밋 상태이며 commit/push/merge는 수행하지 않았다.

**실제 실행: 완료. 최종 24/24 PASS, 실패·오류·스킵 0, HTTP 162건.**
8.0.44에서 실행 가능한 호환성을 확인하고 매 실행 새 Docker 컨테이너/DB/계정으로
담당 4개 도메인의 우선 범위를 검증했다. 8.0.46 이미지 오류와 기존 미실행 이력은
[이전 보고서](CCRA_MYSQL_VALIDATION.md)에 보존한다.

## 1. 기존 이미지/버전 요구사항 검토

| 경로 | 기존 조건 | 변경 없이 8.0.44 직접 실행 |
| --- | --- | --- |
| `frontend/e2e/run.cjs` | 생성 이미지 `mysql:8.0.46-oraclelinux9`, DB 포트 13316, 새 E2E 컨테이너 | 불가: 생성 이미지가 하드코딩됨 |
| `backend/src/e2e/java/e2e/E2eDatabase.java` | JDBC 버전은 `8.0.*` 허용, E2E 전용 이름/계정/UUID/GRANT/hostname 검사 | JDBC 자체는 허용하나 위 생성 실행기를 교체 없이 우회하지 않음 |
| auth/user/calendar/survey MySQL 실행기 | 이미지 8.0.46 및 전용 컨테이너·라벨·일부 고정 UUID/host/포트/계정 조건 | 불가: 해당 안전 조건을 변경하지 않음 |
| matching 다중 JVM 도구 | 이미지 8.0.46, 3311, `survey-only`; JDBC도 정확히 8.0.46 검사 | 불가: 원본의 버전 검사를 완화하지 않음 |
| chat MySQL 도구 | 정확히 8.0.44 검사, 과거 승인한 로컬 3306 및 전용 DB/계정/UUID | 버전은 호환하나 이번에는 그 서버/DB에 접근하지 않음 |
| 이번 CCRA 도구 | 기본 8.0.46 경로를 유지하고 명시적 `-Mysql844` 경로만 추가 | 가능: 별도 컨테이너/결과 디렉터리 및 생성본에 정확한 8.0.44 검사 |

즉, 기존 E2E와 여러 MySQL 실행기는 8.0.46을 **대상 신원으로 고정**하지만, 해당 사실만으로
애플리케이션 기능이 8.0.46 전용이라는 의미는 아니다. 공통 설정을 일괄 8.0.44로 변경하지 않았다.
전체 브라우저 E2E는 이번 실행 범위에 포함하지 않는다.

## 2. 실행 전 호환성 판단

실제 bootJar는 `mysql-connector-j-9.7.0.jar`, `hibernate-core-7.4.5.Final.jar`를 포함한다.
Connector/J 9.7은 MySQL 8.0 이상을 지원한다.
([Oracle 호환성 문서](https://docs.oracle.com/cd/E17952_01/connector-j-en/connector-j-versions.html))
Hibernate 7.4의 MySQLDialect 최소 DB 버전도 8.0이다.
([Hibernate 지원 Dialect](https://docs.hibernate.org/orm/7.4/dialect/))

담당 Entity/Repository/수동 DDL/검증 SQL을 확인한 결과 필요한 것은 InnoDB RR,
`SELECT ... FOR UPDATE`, FK/UNIQUE/NOT NULL, AUTO_INCREMENT, DATETIME(0), utf8mb4,
`utf8mb4_0900_ai_ci`, Performance Schema `data_lock_waits` 등 MySQL 8.0 기능이다.
`data_lock_waits`와 테이블 SELECT 권한 조건도 MySQL 8.0에 제공된다.
([MySQL 문서](https://dev.mysql.com/doc/refman/8.0/en/performance-schema-data-lock-waits-table.html))
이번 범위에서 8.0.45/46에만 도입된 구문이나 기능 의존성은 발견하지 않았다.
기존 채팅 MySQL 보고서에도 8.0.44 실행 근거가 있다.

이 근거로 **이번 기능·제약·트랜잭션 검증의 8.0.44 실행은 호환 가능**하다고 판단했다.
8.0.45/46에는 InnoDB/옵티마이저 등의 수정이 있으므로 버전별 버그·성능·실행계획까지
동일하다고 보장하지 않는다.
([8.0.45 릴리스 노트](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-45.html),
[8.0.46 릴리스 노트](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-46.html))
기존 데이터 파일을 하위 버전으로 여는 downgrade는 수행하지 않았다.

## 3. 실제 격리 환경

| 항목 | 확인 결과 |
| --- | --- |
| Docker | 로컬 named pipe, Linux/x86_64 |
| 새 컨테이너 이름 | `kkiri-ccra-mysql844-20261021`, 실행별 새 ID/owner 라벨 |
| 이미지 | `mysql:8.0.44`, linux/amd64 |
| 이미지 digest | `sha256:9c3380eac945af0736031b200027f581925927c81e010056214a4bd6b6693714` |
| 접속 | `127.0.0.1:13326 → 3306`; 기존 DB 포트 미사용 |
| 실제 SELECT VERSION() | **8.0.44** |
| 엔진/격리 | 전 테이블 InnoDB / REPEATABLE-READ |
| 문자셋/collation | 새 DB `utf8mb4 / utf8mb4_0900_ai_ci` |
| DB 세션/JVM | UTC `+00:00` / JVM Asia/Seoul |
| lower_case_table_names | 0, 기존 서버/시스템 값 변경 없음 |
| 데이터 | 이번 컨테이너 tmpfs 768MiB, 외부 볼륨 미사용 |
| 제한 | 컨테이너 메모리 1GiB/CPU 2, Java heap 최대 512MiB, 서버 풀 최대 16 |
| 검증 앱 | 실제 Spring MVC/Security/JWT/Service/Repository/JPA, `validate`, SQL `never`, seed `NONE` |
| 외부 대역 | SMTP 전송 불가능 대역, 고정 AI 대역, 수집/이미지 정리 스케줄 비활성 |

첫 실행:
`kkiriccrad76678f05e64`, `ccra_d76678f05e64@%`, server UUID
`72c8fbf7-cd1f-11f1-a4c4-5e9493c358db`, 애플리케이션 PID 11280.

최종 실행:
`kkiriccra7880125a1d1a`, `ccra_7880125a1d1a@%`, server UUID
`c020278e-cd1f-11f1-b380-3e9ee0069495`, 애플리케이션 PID 10304.

쓰기 전에 이미지/포트/owner 및 JDBC VERSION/UUID/hostname/schema/account/role/GRANT를
확인했다. 버전 검사는 `8.0.*`로 넓히지 않고 정확히 **8.0.44**를 검사한다.
root는 해당 새 컨테이너의 DB/계정 생성에만 사용하고 업무 프로세스에는 넘기지 않았다.
전용 계정은 정확한 해당 schema의 권한과 `performance_schema.data_lock_waits` SELECT만
가진다. 개발/운영 DB 이름으로 SELECT 권한 탐침도 수행하지 않았다.
`.env`·기존 인증정보는 읽거나 출력하지 않았다.

## 4. 실행 범위와 결과

```powershell
# 저장소 루트. 기존 도구/설정은 유지하고 이번 전용 경로만 선택한다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-mysql-validation/run.ps1 -PrepareOnly -Mysql844
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/tools/ccra-mysql-validation/run.ps1 -Mysql844 -ApprovedProvisionAndDelete
```

두 번째 명령을 첫 실행 및 도구 오류 수정 후의 최종 실행에서 각각 수행했다.

| 실행 | case | PASS | FAIL | 오류/미완료 | SKIP | HTTP |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 첫 실행 | 24 | 22 | 2 | 0 | 0 | 160 |
| 최종 재실행 | **24** | **24** | **0** | **0** | **0** | **162** |
| 전체 실행 이력 | 48 | 46 | 2 | 0 | 0 | 322 |

총 48회는 동일 case의 재실행을 포함한다. 고유 시나리오는 24개이며 H2/JUnit 개수와 합산하지 않는다.
정상 거절을 기대한 HTTP400/401/403/409 및 강제 내부 예외의 HTTP500은 해당 단언을 만족하면
성공한 negative case다. 이를 기능 실패 개수로 집계하지 않는다.

| 최종 범위 | case | 실제 검증 |
| --- | ---: | --- |
| 신원/물리 schema/커뮤니티 DDL | 2 | 빈 DB, 정확한 권한·버전·InnoDB, 익명 매핑/이미지 정리 마이그레이션 2개와 인덱스 |
| Community | 3 | multipart 게시글 CRUD/소프트 삭제, 댓글/답글 1000자·Principal·소유권·거절 시 보존, 8명 동시 익명 번호 고유성과 응답 마스킹 |
| Report | 5 | USER/POST/COMMENT 실제 작성자 FK/nullable 참조, PENDING/COMPLETED/REJECTED 중복, 다른 신고자 독립성, UTF-16 499/500/501/70000 ASCII/한글/이모지, DATETIME(0), 잘못된 요청 |
| Admin | 10 | 권한/계정 상태/잘못된 요청, 댓글/답글 제재·감사·대시보드·검색, 처리 경합 4종, 선행 롤백·stale refresh·재진입 보존, 제재 전체 롤백, timeout/재시도, 기존 JWT 효력 |
| Chat | 3 | HTTP 생성/재사용 커밋·프로필 누락 전체 롤백·설정 null, 단일 JVM 두 실제 WebSocket 양방향/정확 ID 읽음·나가기 후 차단, 익명 POST/COMMENT 출처 분리/FK/대상 결정 |
| 물리 제약/rollback | 1 | PK·FK·UNIQUE·NOT NULL의 실제 SQL 거절 8건과 선행 UPDATE 롤백·원래 행 보존 |

관리자 경합은 `data_lock_waits`에서 두 HTTP 처리기의 실제 대기를 관찰한 뒤 잠금을 해제했다.
한 요청만 성공하고 후속 요청은 INVALID_REQUEST이며 승자의 이력/제재를 보존했다.
timeout은 세션 InnoDB 3초 설정에서 실제 vendor code **1205**와
HTTP409/ACCOUNT_UPDATE_CONFLICT를 관찰했다. 최종 경과는 **3,484ms**이며 이후 요청은 성공했다.
JPA의 5초 힌트가 MySQL에서 적용됐다는 증거로 해석하지 않는다.

미래 생성 시각을 주입한 강제 오류는 현재 처리기의 HTTP500/INTERNAL_SERVER_ERROR와
회원/게시글/댓글/답글/신고 이력 전체 rollback을 함께 검증했다.
일반 정상 처리의 생성·처리 시각은 초 단위로 저장되고 즉시 처리도 성공했다.

첫 실행의 FAIL 2개는 검증 도구 오류였다:

1. 익명 댓글 POST 8건은 모두 200이었으나 검증에서 존재하지 않는
   `GET /api/posts/{id}/comments`를 호출했다. 실제 계약인 게시글 상세의 `comments` 배열로
   수정하고 8건·번호 고유성·작성자 ID null을 명시적으로 단언했다.
2. 대시보드에서 `data.stats` 대신 `data`의 집계 필드를 읽었다. DTO와 기존 H2 검사를
   대조해 JSON 경로를 수정하고 댓글 -2/미처리 -1/전체 신고 이력 유지 단언을 보존했다.

운영 코드를 수정하거나 기대값을 완화하지 않았으며 최초 실패도 별도 run으로 보존했다.
이번 실행 범위에서 새 운영 기능 결함은 발견하지 않았다.

## 5. 마이그레이션·최종 DB 상태

`20261013_community_anonymous_authors.sql`, `20261019_post_image_cleanup.sql`의 적용은
이번 빈 새 schema에서만 이전의 빈 테이블 상태를 재현하여 수행했다.
Linux의 실제 lowercase 물리 이름에 맞춰 테이블 식별자만 변환했고 원문 SHA-256과 실행 SQL을
`migrations.json`에 기록했다. 원문 SQL 파일을 수정하지 않았고 무수정 실행 통과로 보고하지 않는다.
채팅 receipt/익명 key/index cycle은 기존 충분한 근거를 보존하고 중복 재작성하지 않았다.

최종 HTTP case 이후 새 JDBC 연결로 확인한 행 수:

| USER | 게시글 | 댓글 | 익명 매핑 | 신고 | 채팅방 | 참여자 | 메시지 | 이미지 정리 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 46 | 16 | 28 | 10 | 26 | 4 | 8 | 3 | 0 |

신고 reporter/target FK 고아 **0**, PENDING/처리 완료 감사 이력 불변식 위반 **0**을 확인했다.
각 case의 정상 결과와 보존/변경/rollback 상태는 `cases.json`과 `final-state.json`으로 기록했다.
이 행들은 이번 컨테이너 소유 데이터이며 상태 기록 후 컨테이너와 함께 제거했다.

## 6. 보존·정리·변경 파일

두 실행에서 생성한 컨테이너 ID:

- 첫 실행 `1b5fbb3c61f5730d5178307548c2067162828dc087d0a3df9ffce884692d225b`
- 최종 실행 `4302a7f91c6dea8a91ca289356fbc3b52f8deeaa9093bddb15185d95706f1f0e`

각각 name/ID/실행별 owner 라벨을 대조한 뒤 삭제했고 정리 증거를 남겼다.
앱 서버 종료도 확인했다. 기존 컨테이너는 다음 ID와 볼륨 연결로 중지 상태를 유지한다.

- `kkiri-auth-mysql-test`: `42528a4bbec4e8c00efbe1e12bcd0e7eacd019d91b816d08d9d4f529558ae89c`
- `kkiri-auth-mysql-backup`: `3acf9c77ee5687cdbcc1d994ab49a6eed1ec3bb86741994367dd2c9f394c30f3`
- 기존 데이터 볼륨 `a42846842dbeeef69230f71776cb6e78f08b461f8ab498a918ff431d08acf8ff` 연결 유지.
  해당 볼륨을 새 컨테이너에 연결하거나 삭제하지 않았다.

Docker Desktop 재시작, 시스템 설정 변경, 기존 이미지 교체/삭제, 개발/운영 DB 접속은 없었다.
다른 팀원의 브랜치·공통 실행기·E2E·Gradle 설정에도 변경이 없다.

이번 수정은 `tools/ccra-mysql-validation/run.ps1`의 명시적 `-Mysql844` 분기,
`CcraMysqlValidation.java`의 버전 표기/접근 범위/두 검증 경로 수정, RUNBOOK 및 이번 보고서다.
이전 차단 보고서/증거는 보존하고 후속 링크만 추가했다.

재사용: `MatchingMultiInstance`, 생성본 `MatchingMultiServer`/`MatchingMultiSupport`,
`MatchingExtraChecks.SocketClient`. 상세 실행법은
[RUNBOOK](tools/ccra-mysql-validation/RUNBOOK.md)을 따른다.

원본 실행 증거는 `build/ccra-mysql844-validation/run-d76678f05e644f4a9c5ed234372da900/` 및
`run-7880125a1d1a4e7eb1953c8371a43534/`, `execution.log`, `execution-2.log`,
`container-<owner>.json`, `cleanup-<owner>.json`에 있다.
Git 제외 build 산출물의 주요 결과/안전 근거는
[mysql844-evidence.json](tools/ccra-mysql-validation/mysql844-evidence.json)에 따로 보존한다.
실행기 소스 hash와 생성본 hash도 보존하여 기준 SHA 이후의 미커밋 테스트 변경을 구분한다.

컴파일과 계약 검사(73 endpoints / 75 record DTOs / 30 entities)는 통과했다.
운영 코드/프론트 변경이 없어 전체 H2 test/프론트 build는 이번에 반복 실행하지 않았다.

## 7. 진행 판정과 남은 범위

**이번 담당 도메인의 8.0.44 기능·정합성 검증 범위는 통과하여 후속 작업에 사용할 수 있다.**
다만 8.0.46 이미지의 실행 오류 자체는 해결하지 않았고, 해당 버전의 성능/버그 수정 동등성,
기존 전체 E2E, 다중 JVM, 장시간 부하, 프록시/TLS/재시작·재접속, 운영 DDL/백업 복원은
이번 결과에 포함하지 않는다. 현재 결과만으로 8.0.46 전체 검증 완료나 운영 배포 승인을
선언하지 않는다. 미확정 익명성·신고 정책은 그대로 보류한다.
