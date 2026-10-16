# 저장소 정리 기록

기준: 2026-10-16 (Asia/Seoul). 종료된 수동 검증 자료와 재생성 가능한 산출물을 삭제했다. 앞서 선택한 실행기·정상 테스트 코드·운영 문서는 유지한다.

## 제거한 파일

이번에 **1,411개 / 454,429,007 bytes**를 삭제했다. Git 추적 파일 삭제는 없다.

수동 검증용 파일 6개:

- backend/migrations/auth-test/00_target_guard.sql
- backend/migrations/auth-test/01_inspect.sql
- backend/migrations/auth-test/10_baseline.sql
- backend/migrations/auth-test/20_before_migration.sql
- backend/migrations/auth-test/30_verify.sql
- backend/migrations/auth-test/README.md

재생성 가능한 빌드·캐시 파일 1,405개:

- backend/bin/: IDE 컴파일 출력
- backend/build/classes/, resources/, libs/, generated/: 클래스·리소스 복사본·JAR·생성 출력
- backend/build/auth-mysql-concurrency/, auth-mysql-multi-instance/, user-mysql-concurrency/, user-mysql-multi-instance/: 테스트 실행기 클래스와 추출 라이브러리
- backend/build/resolvedMainClassName 및 crawl-check/classes/RunAcademicCrawl.class
- frontend/build/: 프론트 배포 번들·복사한 정적 자원

이전 정리에서는 과거 결과 JSON·TXT·TSV·보고서·ZIP·비교용 복사본·임시 로그를 제거했다.

## 수정한 파일과 내용

- [AuthMultiInstance.java](auth-mysql-multi-instance/AuthMultiInstance.java), [UserMysqlConcurrency.java](user-mysql-concurrency/UserMysqlConcurrency.java): 삭제한 수동 검증 폴더 대신 backend/migrations 전체를 지문 수집 대상으로 사용한다. 운영 SQL 변경 감지를 유지하고 범위를 넓혔다. 업무·권한·동시성·롤백 assertion과 격리 DB guard는 바꾸지 않았다.
- [.gitignore](../../.gitignore): 삭제한 수동 검증 README 예외와 해당 결과 경로 규칙만 제거했다. 네 실행기의 새 results 제외 규칙은 유지한다.
- 네 RUNBOOK: 빌드 산출물 정리 후에는 먼저 backend에서 `gradlew.bat --offline test bootJar`로 클래스·JAR을 생성한 뒤 실행하도록 안내했다.
- 이 문서: 삭제 목록과 변경·검증 결과를 통합했다. 실제 운영 소스와 API·DB 계약은 변경하지 않았다.

## 유지한 파일

- 운영 migration SQL 4개와 [migration 안내](../migrations/README.md), [DEPLOYMENT](../DEPLOYMENT.md), AGENTS·API·ERD 등 기준 문서
- backend/src/test와 frontend/src의 정상 테스트, 운영 소스·설정·Gradle wrapper·의존성 잠금 파일
- run.ps1 4개와 연결된 Java 코드 및 RUNBOOK
- 설치된 node_modules, Gradle 캐시·IDE 설정, 고유한 설계·화면 명세·재사용 프롬프트
- backend/build/crawl-check의 RunAcademicCrawl.java·run.gradle: 검증 assertion이 있는 별도 테스트 소스이므로 유지하고 컴파일 클래스만 삭제

| 재실행 안내 | 역할 |
| --- | --- |
| [Auth 단일 JVM](auth-mysql-concurrency/RUNBOOK.md) | 서비스·MySQL 동시성 |
| [Auth 다중 JVM](auth-mysql-multi-instance/RUNBOOK.md) | 실제 HTTP·공유 제한 |
| [User 단일 JVM](user-mysql-concurrency/RUNBOOK.md) | 계정·프로필·매칭 동시성 |
| [User 다중 JVM](user-mysql-multi-instance/RUNBOOK.md) | JWT·권한·HTTP 경합 |

현재 빌드 출력은 삭제했으므로 `-PrepareOnly`도 backend를 먼저 빌드해야 한다. 프론트 배포 번들은 frontend에서 `npm run build`로 생성한다. 실행 중인 앱·빌드가 없음을 확인한 뒤 산출물을 삭제했다.

## 검증

- 수동 SQL 폴더 삭제 후, 빌드 출력이 아직 있던 상태에서 네 실행기의 `-PrepareOnly` 컴파일 통과. 이후 검증에 사용한 클래스·JAR도 삭제했다.
- 독립 Java 검사 통과: 폐기한 폴더 없이 Auth/User 지문 수집 성공, 운영 SQL 포함, SQL 변경 감지, 필수 migration 디렉터리 부재 시 실패. DB·HTTP·SMTP·Docker 호출 없음.
- 문서 계약 검사: 70 endpoints / 71 record DTOs / 29 entities 통과.
- 보호 파일 756개 SHA-256 동일. 운영 코드·정상 테스트·운영 SQL·기존 사용자 변경 보존을 확인했다.
- 문서 상대 링크 17개 유효, 삭제된 수동 검증 경로의 외부 참조 0건. PowerShell AST·계약 검사기 구문·git diff --check 통과.
- 전체 백엔드 H2/bootJar·프론트 테스트/build·실제 MySQL 및 다중 JVM HTTP는 이번에 재실행하지 않았다. 운영·일반 테스트 코드는 변경하지 않았고, 수정한 실행기는 컴파일과 독립 지문 회귀로 확인했다.
- 운영 데이터 변경·SQL 실행·Docker·볼륨 정리는 수행하지 않았다.

## 전후 집계

일반 파일·논리 bytes 기준이며 .git·심볼릭 링크와 임시 감사 디렉터리를 제외한다. 감사용 소스·클래스·fixture·plan은 종료 전에 제거한다.

| 범위 | 정리 전 | 정리 후 |
| --- | ---: | ---: |
| 파일 수 | 47,306 | 45,895 |
| bytes | 806,301,467 | 351,875,041 |

순감소: 454,426,426 bytes (433.37 MiB).
