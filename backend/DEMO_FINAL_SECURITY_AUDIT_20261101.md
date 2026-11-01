# Git 커밋 전 최종 보안·오류 검사

2026-11-01 KST. **최종 결과: PASS — 현재 더미데이터 준비·병합 변경의 커밋 전 검사 범위.**
확인된 커밋 차단 결함과 이 변경에서 결정이 필요한 미확정 정책은 없다.
Git stage/commit/push, 서버 실행, DB 접속·적용·복원은 이번 재개 검사에서 수행하지 않았다.

## 완료된 검사 재사용

기존 실행 결과와 현재 파일의 변경 시각·Git 차이를 확인했다. 검사 이후 대상 코드 변경이 없어
아래 테스트·빌드·계약 검사·구문 검사·전체 whitespace 검사는 다시 실행하지 않았다.

| 항목 | 기존 최종 결과와 근거 |
|---|---|
| 백엔드 | `test bootJar --rerun-tasks` 성공. `build/demo-final-audit-backend-fresh.log` 및 `build/test-results/test/TEST-*.xml`: 89 suites, 1,510 tests, 실패 0 / 오류 0 / 건너뜀 0 |
| 프론트 | `frontend/build-demo-final-audit-tests.log`: 28 suites, 266 tests 통과. `frontend/build-demo-final-audit-build.log`: production build 성공 |
| E2E 실행기 안전 검사 | `frontend/build-demo-final-audit-runner.log`: 31 통과, 0 실패, Java guard 3건 SKIP |
| 계약 문서 | [기존 적용 보고서](DEMO_LOCAL_APPLY_20261101.md)의 `sync-contract-docs.cjs --check` 결과: 73 endpoints / 75 record DTOs / 30 entities 일치. Controller/DTO/Entity 및 API/ERD 입력 143개 파일이 HEAD와 같음 |
| 구문·공백 | 기존 적용 보고서의 Node 구문·PowerShell 파싱 통과, `build/demo-final-audit-whitespace.log`에 공백 오류 없음. LF→CRLF 안내만 존재 |
| 로컬 MySQL·브라우저 | 기존 `after-runtime-*.json`과 브라우저 `result.json` 확인. 전체 회원 54명, 기존 33명 계정 행 보존, 원본 DB 무변경. 공개 프로필 50명, 인증 매칭 49명, 게시글 80개·메시지 33개 확인, 브라우저 런타임 오류 0 |

기존 MySQL 검증은 [실제 적용 보고서](DEMO_LOCAL_APPLY_20261101.md)와
[병합 리허설 보고서](DEMO_MERGE_VALIDATION.md)에 기록된 당시 결과다. 이번에 DB를 재조회한 결과가 아니다.

## 이번에 마무리한 검사

- 수정·미추적 커밋 후보 35개 파일을 검토했다. 최종 검사 보고서는 이 검사 후 추가했다.
  변경 범위는 오프라인 Java 도구·PowerShell·검증 스크립트·테스트·문서·수동 SQL 및
  `build.gradle`, `SeedDataService`의 과거 설문 값 추출이다. 제품 프론트·인증/API/Entity·공통 설정 변경은 없다.
- 대상 DB·프로필 제한, preview 토큰, 파일/DB 잠금, 잠금 후 baseline 재검사,
  기존 계정 행 보호, FK 재매핑, APPLYING 중단 장벽을 코드와 기존 테스트에서 확인했다.
  핵심 병합 트랜잭션과 독립 매칭 트랜잭션을 전체 단일 트랜잭션으로 취급하지 않는다.
- 개인 기본 DB 적용 스크립트는 명시적 옵션, 고정 loopback 주소, 검증 패치 해시,
  쓰기 중지 검사, 백업 복원 검증 및 단계 영수증을 요구한다. 일반 데이터 도구의 기본 DB 쓰기 거절은 유지된다.
- 커밋 후보에서 개인키·JWT·알려진 자격증명 접두어·BCrypt 해시와 실제 로컬 비밀 설정값의
  리터럴 포함 여부를 검사했다. 검출 0건이며 값 자체는 출력·보고서에 저장하지 않았다.
- Git 추적 파일에 비공개 산출물 0건. `.env`, IDE 설정, `backend/local-data/`,
  `backend/build/`, 검사 로그의 ignore 적용을 확인했다. dump·DPAPI 자격증명·영수증은 공유 대상에서 제외된다.
- 최종 `backend-0.0.1-SNAPSHOT.jar`의 Start-Class는 `com.kkirikkiri.BackendApplication`이며
  해당 main 클래스는 1개다. exhibition/E2E 클래스·비공개 파일은 0개다.
  JAR SHA-256: `d0753b814d6eb8699c9c9c13156a7c4c84a79c1824a4c7d1cefc1808855864fe`.
- 백엔드 검사 대상 소스는 최종 JAR 생성 이후 변경되지 않았고 프론트 소스는 최종 테스트 이전 상태다.
  후보별 SHA-256, 보안 검사 결과, JAR 구성과 검사 유효성 근거는 Git 제외
  `build/demo-final-audit/`에 보존했다.

## 미검증·적용 범위

- 기존 원문 비밀번호를 모르는 31개 계정의 실제 비밀번호 로그인은 미검증이다.
  기존 전체 계정 행 보존 확인과 비밀번호 로그인 검증을 구분한다.
  채팅 검사의 로컬 서명 세션도 해당 계정의 비밀번호 로그인 성공을 의미하지 않는다.
- E2E 실행기 검사에서 SKIP된 Java guard 3건을 성공으로 계산하지 않았다.
  H2 테스트 성공을 실제 다중 프로세스 MySQL 잠금 검증으로 취급하지 않는다.
- 운영 배포, 최신 의존성 취약점 데이터베이스 조회, 기존 별도 배포 과제의 검증 완료 판정은 이 검사에 포함하지 않는다.
  운영 준비의 남은 항목은 [DEPLOYMENT.md](DEPLOYMENT.md)를 따른다.

이번 재개에서 새로 추가한 공유 파일은 이 보고서뿐이며 제품 코드는 수정하지 않았다.
