# Admin·Report 결함 수정 및 회귀 검증 (2026-10-20)

## 범위와 안전 경계

- 작업 시작 브랜치: `test/report-validation`. 시작 시 미커밋 변경 없음.
- 운영 코드는 AdminService와 report DTO/Entity/Repository에 한정한다. Community 운영 코드와
  프론트 코드는 변경하지 않는다. 관련 회귀 테스트와 API/ERD/배포 안내를 함께 갱신한다.
- MySQL 서버에 접속하지 않으며 DB·계정·인증 파일·Docker·마이그레이션을 변경하지 않는다.
  `.env`를 읽거나 비밀값을 출력하지 않는다. Git commit/push/merge는 수행하지 않는다.
- 실행 테스트는 Gradle이 강제하는 test 프로필·H2 메모리 DB다. SQL/시드/수집/이미지 정리는
  비활성 상태이며 실제 DB와 SMTP를 사용하지 않는다. 이 결과는 운영 배포 승인이 아니다.
- 추가 API 검사는 MockMvc로 실제 보안 필터·Controller·Service를 통과한다. TCP 네트워크나
  브라우저 E2E 검증은 아니다.

### 변경 파일

| 경로 | 목적 |
| --- | --- |
| [AdminService.java](src/main/java/com/kkirikkiri/admin/service/AdminService.java) | 처리 상태 검사 전 잠금 조회 사용 |
| [AdminReportQueryRepository.java](src/main/java/com/kkirikkiri/report/repository/AdminReportQueryRepository.java) | 단일 신고 쓰기 잠금·최신 값 refresh·재진입 보호 |
| [ReportCreateRequest.java](src/main/java/com/kkirikkiri/report/dto/ReportCreateRequest.java) | 상세 사유 @Size(max=500) |
| [Report.java](src/main/java/com/kkirikkiri/report/entity/Report.java) | 팩토리 길이 검증·신규 시각 초 단위 절삭 |
| [AdminServiceTest.java](src/test/java/com/kkirikkiri/admin/service/AdminServiceTest.java) | 읽기와 처리 잠금 조회의 mock 분리, 기존 assertion 유지 |
| [ReportCreateRequestTest.java](src/test/java/com/kkirikkiri/report/dto/ReportCreateRequestTest.java) | 실제 Bean Validation·UTF-16/공백 경계 |
| [ReportTest.java](src/test/java/com/kkirikkiri/report/entity/ReportTest.java) | 세 팩토리의 길이 제한·시간 정밀도 경계 |
| [AdminReportConcurrencyIntegrationTest.java](src/test/java/com/kkirikkiri/admin/service/AdminReportConcurrencyIntegrationTest.java) | 실제 병렬 잠금·롤백·재진입·누락 신고·HTTP 타임아웃 |
| [ReportRegressionIntegrationTest.java](src/test/java/com/kkirikkiri/report/ReportRegressionIntegrationTest.java) | 실제 HTTP/Service/DB 길이·인증·시간·목록 정렬 |
| [API.md](API.md) | 길이·오류·동시 처리·시간 계약과 DTO 스냅샷 |
| [ERD.md](ERD.md) | 기존 스키마 유지·업무 제약·잠금/시간 정책 |
| [DEPLOYMENT.md](DEPLOYMENT.md) | timeout 힌트·시계·MySQL 미검증 경계 |
| 이 문서 | 수정 근거·검증 결과·별도 승인 MySQL 계획 |

다른 도메인의 운영 파일은 수정하지 않는다. 기존 Community 삭제·익명성·조회·이미지와
공유 계정 잠금의 신고 생성 회귀 테스트를 실행해 연결된 기능의 영향을 확인한다.

## 1. 같은 신고의 관리자 동시 처리

### 구현과 선택 근거

`AdminReportQueryRepository.findReportByIdForUpdate`가 REPORT 참조를 얻고
`refresh(PESSIMISTIC_WRITE)`로 행 잠금과 최신 값 재적재를 수행한다. 기존 상세·검색의
fetch join 조회는 그대로 두며 쓰기 잠금을 걸 때는 그 조회를 사용하지 않는다.
이미 이 트랜잭션이 쓰기 잠금을 보유하면 refresh를 생략하여 미커밋 결정을 보존한다.
없는 신고는 기존 400 INVALID_REQUEST다.

`AdminService.processReport`는 관리자 인증·요청/ID 검증 후 위 잠금을 획득하고 PENDING을
검사한다. 후속 회원 정지·게시글/댓글 삭제와 처리 상태·담당자·시각 저장은 기존 한
트랜잭션 안에서 진행한다. 선행 커밋 이후 대기 요청은 최신 완료/반려 상태를 보고
400 INVALID_REQUEST로 거절된다. 선행 롤백이면 대기 요청이 PENDING을 처리할 수 있다.
충돌·타임아웃은 기존 공통 처리의 409 ACCOUNT_UPDATE_CONFLICT를 유지한다.

프로젝트가 계정·콘텐츠 쓰기에 사용하는 비관적 잠금을 선택했다. 낙관적 잠금은 version
컬럼의 수동 마이그레이션이 필요하고 제재 이후 충돌을 발견한다. 이번 방식은 스키마
변경 없이 상태 검사 전에 직렬화한다. 다른 신고 전체를 잠그는 JVM mutex는 사용하지 않는다.

### 잠금 순서 검토

현재 신고 처리 진입점은 `REPORT → 대상 USER` 또는
`REPORT → POST → COMMENT/답글(ID 순)`이다. 관리자 권한 조회는 쓰기 잠금이 아니다.
Community의 기존 게시글 → 댓글 순서는 유지한다. 일반 계정 변경/콘텐츠 쓰기는 기존
신고 행을 쓰기 잠금으로 조회하지 않고, 신고 생성은 신고자 USER를 잠근 뒤 새 REPORT를
INSERT하므로 기존 신고 행의 명시적인 역순 잠금 경로를 추가하지 않는다.

API는 한 트랜잭션당 한 신고를 처리한다. 향후 기존 잠금을 가진 외부 트랜잭션에서
여러 업무를 합치거나 여러 신고를 역순 처리하는 기능은 별도 잠금 순서 검토가 필요하다.
이 검토는 InnoDB FK/보조 인덱스 잠금·교착 가능성까지 없음을 입증하지 않는다.
5초 JPA timeout 힌트는 실제 MySQL/드라이버에서의 적용 보장이 아니며 후속 실측 대상이다.

### 회귀 검사

`AdminReportConcurrencyIntegrationTest`는 독립 트랜잭션과 실제 H2의
`INFORMATION_SCHEMA.SESSIONS.BLOCKER_ID`를 관찰한다. 단순 sleep으로 대기를 추정하지 않는다.

- 정지/정지, 정지/반려 양 순서, 게시글·댓글 삭제/반려 양 순서에서 한 요청만 성공.
- 후속 중복 요청은 INVALID_REQUEST이며 최초 처리 담당자·결정·제재를 보존.
- 선행 정지·게시글/댓글 삭제를 flush한 뒤 강제 롤백하면 대기 요청만 반려를 커밋.
- 이미 로딩된 PENDING Entity를 최신 상태로 갱신하고 동일 트랜잭션의 재처리도 거절.
- 시간 검증 실패 시 제재·콘텐츠 삭제·신고 이력 전체 롤백.
- 실제 HTTP 잠금 시간 초과는 409이며 데이터가 유지되고 잠금 해제 후 처리 가능.
- 존재하지 않거나 잘못된 신고 ID는 기존 비즈니스 오류로 처리.

## 2. 상세 사유 최대 길이

DTO에 `@Size(max = 500)`를 추가했다. ReportService의 기존 실제 Validator 호출로 서비스
직접 호출도 저장 전에 400 VALIDATION_ERROR가 된다. 세 Entity 팩토리가 공유하는 생성자도
String.length() > 500이면 거절한다. 기존 프론트 `maxLength={500}`은 유지한다.

길이는 Java String.length / Bean Validation / JS 문자열과 HTML maxLength의 UTF-16 코드
단위다. 한글 1자는 1, 보조 평면 이모지 1개는 2를 사용한다. 화면에서 보이는 글자 수
(grapheme cluster)로 변경하지 않는다. 유효한 utf8mb4 문자열의 보수적 상한은 2,000바이트
이내이므로 기존 TEXT의 65,535바이트 한도에 충분하다. 물리 DB의 실제 문자셋은 미확인이다.

null·빈 문자열·공백을 그대로 보존한다. 기타일 때만 비어 있지 않은 description을
요구하는 정책과 trim하지 않는 정책은 유지한다. ASCII·한글·이모지의 499/500/501/70,000
UTF-16 단위를 DTO, Entity 세 팩토리, 실제 서비스와 HTTP 요청에서 검증한다.

## 3. 생성·처리 시각 정밀도

Entity의 created_at/processed_at 선언은 기존 DATETIME을 유지한다. 신규 생성 시
LocalDateTime.now()를, 처리 시 입력 시각을 `truncatedTo(ChronoUnit.SECONDS)`로 절삭한 뒤
저장한다. 같은 초는 허용하고 저장된 생성 시각보다 이전 초인 처리는 계속 거절한다.

`.900` 생성 → `.950` 처리, 즉시 처리, 다음 초 및 여러 초 뒤 처리, DB 커밋 후 별도
영속성 컨텍스트에서 재조회한 Entity를 검증한다. 과거 시각 거절과 제재 롤백도 검사한다.
관리자 목록의 기존 createdAt desc/reportId desc 정렬을 그대로 사용하므로 동일 초의
신고도 reportId 역순으로 안정적으로 정렬된다. DTO 타입과 이력 표시 계약은 유지한다.

과거 행은 보정하지 않는다. 이전 구현이 반올림해 미래로 저장한 생성 시각은 실제 시계가
그 시각에 도달할 때까지 거절될 수 있다. 초 경계를 넘는 인스턴스 간 시계 역전, 시간대/JDBC
설정 불일치는 정밀도 절삭으로 해결하지 않는다. NTP·시간대·DB 세션 설정을 후속 확인한다.
물리 DB가 DATETIME(0)과 다른 정밀도라면 기존 값과 비교 정책의 호환성을 별도 검토한다.

## 4. 실행 결과

최종 전체 실행의 JUnit XML 합계는 **1,299건, 실패 0, 오류 0, 건너뜀 0**이다.
기존 1,222건을 유지하고 신규 77건(파라미터별 실행 포함)을 추가했다.
기존 assertion 삭제·테스트 비활성화·안전 설정 완화는 하지 않았다.

| 검사 | 결과 |
| --- | --- |
| 첫 Admin·Report 선택 실행 | 201건 통과 |
| 누락·잘못된 신고 ID 추가 선택 실행 | 3건 통과 |
| 관련 Community·공유 계정 선택 실행 | 321건 통과 |
| 최종 전체 백엔드 test + bootJar | 1회 실행, 1,299건 통과·빌드 성공(1분 44초) |
| 관련 프론트 | AdminPage + CommunityPage 2개 스위트, 57건 통과 |
| Controller/DTO/Entity 문서 스냅샷 | 73 endpoints / 75 record DTOs / 30 entities 통과 |

최종 전체 실행에 포함된 직접 관련 클래스:

| 클래스 | 기존 | 신규 | 합계 |
| --- | --- | --- | --- |
| AdminServiceTest | 77 | 0 | 77 |
| ReportServiceTest | 15 | 0 | 15 |
| ReportCreateRequestTest | 10 | 16 | 26 |
| ReportTest | 25 | 20 | 45 |
| AdminReportConcurrencyIntegrationTest | 0 | 19 | 19 |
| ReportRegressionIntegrationTest | 0 | 22 | 22 |

실행 명령(Gradle 출력은 build/report-focused.log, report-community.log, report-missing.log,
report-final.log에 보관):

```powershell
# backend 디렉터리
.\gradlew.bat test --tests 'com.kkirikkiri.report.*' --tests '*AdminReportConcurrencyIntegrationTest' --tests '*AdminServiceTest'
.\gradlew.bat test --tests '*CommunityContentConcurrencyIntegrationTest' --tests '*CommunityCreationAndImageConcurrencyIntegrationTest' --tests '*CommunityReadContractIntegrationTest' --tests '*CommunityReadPerformanceIntegrationTest' --tests '*CommunityImageLifecycleIntegrationTest' --tests '*CommunityImageHttpIntegrationTest' --tests '*CommunityPostAnonymityPolicyIntegrationTest' --tests '*CommunityAnonymousAuthorIntegrationTest' --tests '*SharedAccountConcurrencyIntegrationTest'
.\gradlew.bat test --tests '*AdminReportConcurrencyIntegrationTest.missingOrInvalidReportReturnsBusinessErrorWithoutSanctions'
.\gradlew.bat test bootJar

# frontend 디렉터리
npm.cmd test -- --watchAll=false --runInBand --runTestsByPath src/pages/admin/AdminPage.test.js src/pages/community/CommunityPage.test.js

# 저장소 루트
node backend/tools/sync-contract-docs.cjs --check
git diff --check
```

최종 [JUnit HTML 보고서](build/reports/tests/test/index.html)와
[JUnit XML](build/test-results/test/)은 빌드 산출물이다. 프론트 코드는 변경하지 않았으며
프론트 전체 테스트/빌드 및 브라우저 E2E는 실행하지 않았다. MySQL·다중 JVM은 미실행이다.

## 5. 별도 승인 후 수행할 MySQL 검증 계획

이번 작업에서는 아래 어느 단계도 실행하지 않는다. 기존 일반 테스트의 H2 안전 검사를
우회하거나 test 프로필에 MySQL URL을 주입하지 않는다.

1. **대상과 승인:** 운영과 동일한 MySQL/Connector/J/Hibernate 버전 및 격리 수준을
   확인하고 새 전용 schema·전용 최소 권한 계정을 명시적으로 승인받는다. `kkirikkiri`,
   `kkiri_kkiri`, 기존 테스트 DB/계정/인증 파일과 Docker는 재사용·변경하지 않는다.
   새 schema/계정 생성·테스트 데이터 쓰기·테스트 서버/JVM 포트 사용 범위를 별도 승인한다.
2. **권한:** DBA가 승인된 전용 schema/계정에만 생성·권한 부여를 수행한다. schema DDL은
   전용 schema의 CREATE/ALTER/INDEX/REFERENCES, 실행 계정은 SELECT/INSERT/UPDATE/DELETE를
   사용한다. 운영 권한·전역 관리 권한을 애플리케이션에 넘기지 않는다. 인덱스 변경은
   측정 후 별도 승인하며 자동 실행하지 않는다.
3. **읽기 전용 사전 확인:** 서버 식별/버전, @@transaction_isolation, @@sql_mode,
   @@time_zone, @@innodb_lock_wait_timeout, REPORT의 SHOW CREATE TABLE/SHOW INDEX와
   information_schema를 대조한다. PK·FK·NOT NULL·타입·대소문자·정밀도·utf8mb4,
   기존 중복·고아 참조, 신규 version/인덱스가 필요하지 않은 설계를 확인한다.
4. **전용 fixture와 두 JVM:** 실제 Spring 서비스/JWT로 독립 JVM A/B를 연결하고 동일
   신고의 정지/정지·정지/반려·게시글/댓글 삭제/반려 양 순서를 실행한다. 성공 1건,
   나머지 400, 실제 최종 제재·신고 상태·담당자·이력 일치를 조회한다. USER/POST/COMMENT
   생성 중복 경합과 서로 다른 신고자/신고의 독립 처리도 회귀한다.
5. **실패와 잠금:** 선행 트랜잭션의 flush 후 rollback·연결 종료·JVM 종료 시 대기
   요청이 처리 가능한지 확인한다. 실제 lock wait/timeout 및 교착 오류가 전체 제재를
   롤백하고 기존 409로 매핑되는지 검사한다. 잘못된 재시도로 정지/삭제를 중복 실행하지
   않는다. 알 수 없는 commit 결과는 저장된 최종 이력을 조회하고 무조건 재실행하지 않는다.
6. **문자열과 시간:** 실제 SQL 모드의 엄격/비엄격·TIME_TRUNCATE_FRACTIONAL 설정에 대해
   fixture 저장·재조회, .900/.950·초 경계·과거 시각·두 JVM 시계 차이를 확인한다.
   운영 시계를 바꾸지 않고 전용 검증 clock/fixture를 쓴다. 500 UTF-16 단위의 한글/
   이모지 round-trip과 501/70,000 단위 HTTP 400·미저장을 검사한다.
7. **성능:** PK 잠금 SQL과 기존 신고 검색/페이지네이션/중복 검사에 대표 규모 데이터를
   사용해 EXPLAIN을 수집한다. 필요한 경우 전용 schema에서만 EXPLAIN ANALYZE를 실행한다.
   잠금 대기·교착·연결 풀·지연을 측정하고 근거 없이 운영 인덱스를 추가하지 않는다.
8. **롤백·복구:** DML 실패는 각 트랜잭션 ROLLBACK 후 별도 연결로 불변식을 확인한다.
   MySQL DDL은 트랜잭션 롤백을 가정하지 않는다. 새 자원 이름과 fixture ID를 기록하고
   실패 시 JVM을 종료한 뒤 자원을 보존한다. 삭제·재생성·계정 회수는 별도 승인 절차로
   수행하며 운영/기존 테스트 데이터에 대한 복원·비밀번호 재설정은 하지 않는다.

## 6. 판정 경계

세 결함의 **수정과 H2 회귀 검증 완료**다. 변경 파일은 검토 후 커밋할 수 있는 상태이며
이번 작업에서는 commit/push/merge를 하지 않았다. MySQL 물리 스키마·다중 JVM·실제 배포
환경이 확인되기 전에는 **운영 배포 판정을 보류**한다. H2 통과를 MySQL 검증으로 표현하지 않는다.
