# Auth · User · Survey 실제 MySQL 검증

검증일: 2026-10-21 (Asia/Seoul). **143개 실행·143개 통과·실패 0·스킵 0.**
수치는 JUnit 개수나 서로 다른 API 개수가 아니라 기존 독립 실행기와 보완 검증의 사례 수다.
초기 준비/실행기 오류는 최종 기능 결과와 구분해 아래에 기록했다.

- 기준 Git SHA: `305a184f8a552897d21614f868fda71ac63aeb7e`.
  검증 도구·보고서는 이 SHA 위의 미커밋 변경이다. 커밋·푸시·병합하지 않았다.
- JAR SHA-256: `A6FEF54637F9C392FA76B46227F706A9EC70FA0CD3513360C730909E323CEF79`.
  JAR 내 main 클래스 292개와 실행 클래스의 불일치 0개, 운영 소스/리소스 파일 265개의 전후 지문 동일.
- 영구 근거: [실행별 사례·결과·DB 최종 상태 JSON](validation/auth-user-survey-mysql-20261021.json).
  원본 근거는 아래 로컬 경로에 보존한다. `build/`, 기존 도구의 `results/`는 Git 제외 대상이다.

## 1. 실제 환경과 최종 상태

사용자가 승인한 신규 자원만 생성·사용했다. 기존 개발/운영 DB, 다른 팀원 DB에는 접속하지 않았다.

| 항목 | 실제 확인값 |
| --- | --- |
| 컨테이너 / 전용 볼륨 | `kkiri-aus-mysql-20261021` / `kkiri-aus-mysql-data-20261021` |
| 이미지 | `mysql:8.0.46-oraclelinux9` |
| 이미지 ID | `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b` |
| MySQL 버전 / 서버 UUID | `8.0.46` / `8faf7a66-cd1b-11f1-868c-2665e0263516` |
| 연결 / 호스트명 | `127.0.0.1:13326` → `3306` / `kkiri-aus-mysql-20261021` |
| 문자셋·정렬 / JDBC 시간대 | `utf8mb4`, `utf8mb4_0900_ai_ci` / UTC |
| HTTP 서버 | loopback `18091`, `18092`; 검증 후 모두 종료 |

| DB | 전용 계정 | 최종 테이블 / 행 수 |
| --- | --- | --- |
| `kkiriausauth20261021` | `ausauth20261021` | 9 / 0 |
| `kkiriaususer20261021` | `aususer20261021` | 31 / 0 |
| `kkiriaussurvey20261021` | `aussurvey20261021` | 31 / 0 |
| `kkiri_auth_mi_a51021202601` | `kkiri_mi_a51021202601` | 31 / 0 |
| `kkiri_user_mi_a51021202602` | `kkiri_umi_a51021202602` | 31 / 0 |

각 DB 생성 시 빈 DB임을 확인했고 실행마다 버전·UUID·호스트·DB·계정을 검사했다.
계정별 권한은 USAGE와 해당 DB만의 권한 2개이며 전역 데이터 권한·GRANT OPTION이 없다.
테스트 데이터만 정리한 후 별도 JDBC 연결로 **133개 테이블 모두 0행**임을 확인했다.
컨테이너는 실행 상태, 볼륨·5개 DB·5개 계정·DDL은 보존했다. 삭제에는 별도 승인이 필요하다.
기존 4개 컨테이너는 모두 정지 상태로 유지했다. 비밀번호·JWT 키는 Git/출력에 기록하지 않았다.

## 2. 도메인별 결과와 검증 기능

| 도메인 | 서비스 실행기 | 실제 HTTP 실행기 | 별도 SQL | 실행 | 통과 | 실패 | 스킵 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Auth | 30 | 32 | 1 | 63 | 63 | 0 | 0 |
| User | 29 + 추가 회귀 5 | 27 + 선택 보안 회귀 1 | 기존 사례에 포함 | 62 | 62 | 0 | 0 |
| Survey | 12 | HTTP 5 + 같은 실행기 내 SQL 1 | 왼쪽에 포함 | 18 | 18 | 0 | 0 |
| 합계 | 76 | 66 | 1 | 143 | 143 | 0 | 0 |

Survey 정리 성공 기록 1개, 마이그레이션·환경/권한·최종 감사는 기능 사례 수에서 제외했다.
실제 TCP HTTP는 Auth 1,233건 + User/Survey 243건 + 선택 보안 회귀 26건 = **1,502건**이다.
MockMvc/H2가 아니다. 서비스 실행기는 실제 Spring Service → Repository → MySQL이며 HTTP 서버를 거치지 않는다.
HTTP 실행기는 실제 Boot 서버 → 보안 필터 → Controller → Service → Repository → MySQL을 통과한다.
기존 도구의 두 JVM 사례를 재사용했으며 단일 JVM 배포에 다중 JVM 지원을 필수 요구사항으로 추가하지 않았다.

- **Auth:** 코드 동시 검증, 가입/재설정 토큰 일회 소비, 재전송 cooldown, 이메일 collation/Unicode 별칭,
  계정·IP별 제한/첫 identity·bucket 경쟁/계정 독립성, 미등록 계정 응답 비공개성, 전달 IP/IPv6,
  업무 롤백과 독립적인 실패 기록, 모의 메일 실패 롤백, 행 잠금·1205/1213·재시도/HTTP 매핑,
  연결 풀 고갈/복구·반복 경합·재시작 후 제한 유지.
  새 SQL 묶음 1개는 identity UNIQUE, bucket PK/NOT NULL, 인증 코드 FK/NOT NULL,
  reset token UNIQUE/FK/NOT NULL의 8개 거절과 전체 롤백을 확인했다.
- **User:** 내 프로필 조회/부분 수정, Principal 소유권·관리자 권한·JWT, 정지/탈퇴 차단,
  학부·전공 관계/null/잘못된 참조, 닉네임 UNIQUE/대소문자 동치 경쟁의 패자 전체 롤백,
  동일 사용자 부분 수정·비밀번호/재설정과 상태 변경 경합·stale 컨텍스트·토큰 일회 소비,
  재진입 잠금 시 미반영 변경 보존·최초 locking read·서로 다른 사용자 병렬 커밋,
  1205/1213·HTTP 409·풀 복구/반환·재시작.
  프로필 커밋 이후 matching 갱신·점수 보존/복구·잠금 순서의 연계만 포함하며 Matching 전체 검증으로 집계하지 않는다.
  기존 선택 사례 `jwt-infrastructure-security-regression`은 별도로 1개만 실행했다.
- **Survey:** 기존 12개는 프로필/선호 각각 최초 저장 경쟁·flush 후 롤백·정지/탈퇴와 저장 선후 경합/stale 컨텍스트다.
  새 HTTP 5개는 저장/조회·부모 ID 유지·옵션 교체·18개 가중치·위조 userId 소유권 차단,
  소개 누락 보존/공백 초기화, 잘못된 옵션·가중치 키·201자 소개·필수 null 거절과 전체 상태 보존,
  JWT 누락/정지/탈퇴 GET·PUT 차단을 확인했다.
  새 SQL 1개는 부모 PK/사용자 UNIQUE/FK, 세 자식 테이블 UNIQUE/FK/NOT NULL의 15개 거절과 전체 롤백이다.

## 3. 재사용 도구와 원본 근거

아래 경로는 `backend/` 기준이다. 사례별 이름·상태는 영구 요약 JSON에도 보존했다.

| 실행 | 원본 근거 디렉터리 | 결과 |
| --- | --- | --- |
| Auth 서비스 | `tools/auth-mysql-concurrency/results/2026-10-21-am9ea0cb66bdf7` | 30 PASS |
| User 서비스 | `tools/user-mysql-concurrency/results/2026-10-21-um97e00db206` | 29 PASS |
| User 추가 회귀 | `tools/user-mysql-concurrency/results/2026-10-21-um63739faae5` | 5 PASS |
| Survey 서비스 | `build/auth-user-survey-mysql/survey-service/run-d13dd0f5-25a9-4bc9-89e4-3d9a6717bfbc` | 12 PASS + 정리 1 |
| Auth HTTP | `tools/auth-mysql-multi-instance/results/2026-10-21-f8b5c7920ad2` | 32 PASS |
| User/Survey HTTP | `tools/user-mysql-multi-instance/results/2026-10-21-c3074f61e998` | 27 + 6 PASS |
| User 선택 보안 회귀 | `tools/user-mysql-multi-instance/results/2026-10-21-13e4eb2af21b` | 1 PASS |
| Auth 물리 제약 | `build/auth-user-survey-mysql/auth-constraints.json` | 1 PASS |
| Auth 수동 마이그레이션 | `tools/auth-mysql-concurrency/results/index-migration-2026-10-21-1ca2c360-c562-4a1f-88ea-484000917c2a/migration.json` | PASS |
| 최종 감사 | `build/auth-user-survey-mysql/database-audit.json`, `resource-retention.json` | 5 DB 0행·자원 보존 |

Auth 새 빈 DB에 `migrations/20261014_auth_limits.sql`, `migrations/20261015_auth_email_identity_indexes.sql`을
실제로 적용했고, 기존 검증기로 컬럼/인덱스/정렬·조회 계획·데이터 보존을 확인했다.
다른 4개 DB는 현재 31 Entity의 생성 DDL을 사용했고 HTTP 서버는 `ddl-auto=validate`로 기동했다.
기존 운영 DB의 전체 마이그레이션 경로 검증을 대신하지 않는다.

## 4. 초기 오류와 도구 보완

- User의 오래된 30 Entity/테이블 기대값을 실제 31개(`post_image_cleanup` 추가)로 수정하고,
  현재 AdminService에 필요한 실제 `CommunityCommentWriteService`를 실행기 컨텍스트에 import했다.
  첫 Initialize는 DDL 생성 후 30개 검사에서 업무 사례 실행 전 `INCOMPLETE`로 중단했다.
  DB를 삭제/재생성하지 않고 같은 스키마에서 Verify 29개·Additional 5개를 통과시켰다.
  원본: `tools/user-mysql-concurrency/results/2026-10-21-um34701bd552`.
  기존 결과 필드 `cleanupAll30TablesEmptyAndDdlUnchanged`의 이름은 레거시이며 실제 검사는 31개다.
- Survey/Auth HTTP/User HTTP 준비 시 javac의 informational stderr가 PowerShell 종료 오류로 승격되었다.
  진단을 보존하고 javac 종료 코드로 판단하도록 3개 실행기를 보완했다. 기존 5개 PrepareOnly 모두 성공했다.
- 최초 생성의 MySQL readiness/SQL stdin 확인 오류를 보완하여 승인된 부분 생성만 이어갔다.
  중단 중 Docker 엔진이 멈춘 뒤에는 Docker Desktop과 승인된 컨테이너만 재시작했다.
  기존 자원·DB·계정·비밀번호 초기화는 없었다.
- 최종 감사 식별자 검사가 Survey DDL의 대문자 테이블명을 거부했다.
  대문자 허용과 백틱 인용을 적용한 후 읽기 전용 감사를 다시 수행했다.
- 첫 Auth/User HTTP summary의 `bootJarSha256`가 실제 해시 대신 `Verify`로 기록된 인자 오류가 있다.
  원본은 수정하지 않았다. `artifact-fingerprints.json`의 실제 해시·292개 클래스 일치·운영 파일 전후 지문으로
  보완했으며 인자를 수정한 마지막 선택 보안 실행에는 올바른 해시가 기록되었다.

**확인된 기능 결함은 없다.** 위 환경·도구·메타데이터 오류는 기능 실패와 구분한다.
기대값 완화·실패 사례 비활성화·운영 코드 수정은 하지 않았다.

## 5. 변경 파일·미검증·다음 단계

- 수정: `tools/user-mysql-concurrency/UserMysqlConcurrency.java`;
  `tools/{auth-mysql-multi-instance,user-mysql-multi-instance,survey-mysql-concurrency}/run.ps1`.
- 신규: `tools/auth-user-survey-mysql/{provision.ps1,prepare.cjs,run.ps1,AusChecks.java,AusSurveyHttp.java,README.md}`;
  본 보고서; `validation/auth-user-survey-mysql-20261021.json`.
- 기존 실행기 기본 대상은 유지했다. 전용 실행기는 승인된 identity/대상으로만 `build/`에 소스를 생성하고,
  기존 고정 사례 이름을 대조한다. 기존 시나리오를 중복 구현하지 않았다.
- 운영 코드·Gradle/H2 공통 설정·마이그레이션 원본·다른 담당자 코드는 변경하지 않았다.
  전체 Gradle/H2 테스트·빌드는 반복하지 않았고 기존 산출물을 지문 검증하여 사용했다.
  API 계약 검사와 도구 구문/컴파일 검사를 별도로 확인했다.

실제 SMTP/AI Gateway 연동, 운영 데이터/기존 스키마 업그레이드, 장시간 부하,
강제 kill·커밋 응답 유실 복구는 미검증이다. SMTP는 테스트 대역, AI 등 비담당 외부 접점은 제외했다.
**이번 Auth/User/Survey MySQL 검증은 완료되어 다음 단계로 진행 가능하다.**
다른 담당자 결과와 운영 마이그레이션·외부 연동 준비를 합쳐 최종 배포 여부를 판단한다.
보존 자원을 재생성하거나 전체 HTTP 실행을 반복하지 말고 재실행이 필요하면
[전용 실행 안내](tools/auth-user-survey-mysql/README.md)의 보호 조건을 먼저 확인한다.
