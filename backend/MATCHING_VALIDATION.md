# Matching 결함 5건 수정 및 검증 보고

검증일: 2026-10-17 (Asia/Seoul).

## 1. 최종 판정

**코드 검증 통과 / 운영 검증 필요.** 결함 5건의 수정과 최종 자동 검증을 완료했다.

운영 배포·운영/개발 DB 접속·마이그레이션·commit/push/merge는 수행하지 않았다. 작업 전 존재한 `MyPage.js`의 UI 변경을 보존하면서 결함 부분을 수정했다. 원본은 Git 제외 경로 `build/matching-validation/MyPage.before.js`에 보관했다. 기존 `backend.zip`, `frontend/src.zip`은 수정하지 않았다.

## 2. 문제별 수정 결과

| 번호 | 심각도 | 문제 | 수정 결과 | 검증 |
|---|---|---|---|---|
| 1 | High | 프로필 설문 서버 연동 | 기존 GET/PUT 함수 복구, 공통 설문 정의·meal 사용, completed/answers/최상위 introduction을 서버 응답에서 반영. 로딩/오류/재시도·초안 보존·중복 저장 차단·계정 전환 격리 | 마이페이지 14건, survey API 7건, 기존 Survey/프로필 조회 테스트 |
| 2 | Medium | 전공 없는 학부 변경 | 전공이 없는 학부에 `major: "세부전공 없음"` 명시. 백엔드 기존 계약·잘못된 소속 거절 유지 | 전공 해제·전공 선택 요구·다른 필드 수정 검증, 기존 UserService 8건 |
| 3 | Medium | 비활성 사용자 목록 노출 | 점수 준비 후 공개 조건으로 페이지/count 재조회. 최종 프로필에도 eligible 적용. 최종 페이지·count·DTO는 REPEATABLE READ 읽기 스냅샷 사용 | H2 정지/탈퇴 Latch 경합, 추천 상태 재검증, 마지막 페이지, MySQL 목록 경합 |
| 4 | Medium | AI 호출량·동시 실행 제한 | 실제 Principal ID별 MySQL 공유 quota, auth와 다른 키, 인스턴스별 semaphore, 초과 429/503, finally 자원 반환, Gateway 동안 트랜잭션 중단 | AI 관련 27건: MVC 7, limiter 통합 5, 트랜잭션 통합 1, Gateway 8, Service 6 |
| 5 | Medium | 추천 필터 전체 조회 | 추천 상위 9명을 먼저 선정하고 해당 ID에만 DB 필터 적용. 프론트 전체 목록 반복 요청 제거, 기존 signal-only API 유지 | 상위 9명/동점/복수 필터/빈 결과/119명 bounded 조회, 프론트 요청 수·취소·빠른 변경, MySQL 실제 조회 |

UI 레이아웃·필터 모달·전체 목록 size=9·기존 매칭 산식과 좋아요 정렬은 유지했다. 저장 실패·불완전 응답을 로컬 저장 성공으로 대체하지 않는다. 레거시 자기소개는 조회 시 그대로 보존하며 새 저장에만 200자 제한을 적용한다.

## 3. 변경 파일

경로는 저장소 루트 기준이다. API·Entity 스키마와 무관한 파일은 수정하지 않았다.

### 프론트엔드

| 파일 | 변경 내용·이유 | 영향 기능 |
|---|---|---|
| `frontend/src/pages/mypage/MyPage.js` | 중복 설문·localStorage 기본값 제거, 기존 서버 API·자기소개 복구, 공통 선택지 검증, 서버 완료 상태, 사용자 ID별 remount, 전공 해제값 전송 | 계정정보·두 설문·완료 현황·자기소개 |
| `frontend/src/pages/mypage/MyPage.test.js` | 기존 10건 보존, 계정 전환 중 저장 응답·중복 저장·잘못된 선택지·전공 선택·닉네임 독립 수정 검증 | 설문·회원정보 회귀 |
| `frontend/src/pages/roommate/RoommateSearchPage.js` | 필터를 추천 API로 전달하고 전체 페이지 순회 제거. AbortController 유지 | 추천 필터·요청 취소 |
| `frontend/src/pages/roommate/RoommateSearchPage.test.js` | 요청 수 고정, 지연 응답 무시, 필터 초기화/취소, 빈 결과·게스트·전체 size 검증 | 검색 회귀·요청 규모 |
| `frontend/src/api/roommateApi.js` | `getFilteredRecommendedRoommates(filters, signal)` 추가, 기존 `getRecommendedRoommates(signal)` 유지 | 검색·메인 페이지 호환성 |
| `frontend/src/api/roommateApi.test.js` | 반복 Query Parameter·인증·취소 signal·기존 시그니처 검증 | 추천 HTTP 계약 |

### 백엔드 구현·설정

| 파일 | 변경 내용·이유 | 영향 기능 |
|---|---|---|
| `backend/src/main/java/com/kkirikkiri/matching/service/RoommateMatchingService.java` | 페이지/count 최종 재조회·eligible 프로필 조회·REPEATABLE READ·상위 9명 이후 필터. 게스트의 불필요한 사전 페이지 조회 생략 | 전체·추천·관심·상세 읽기 |
| `backend/src/main/java/com/kkirikkiri/matching/controller/RoommateMatchingController.java` | 추천 요청의 검증된 RoommateFilterRequest 전달 | 추천 API |
| `backend/src/main/java/com/kkirikkiri/matching/dto/RoommateFilterRequest.java` | 필터 적용 여부 판별 메서드 추가. 공개 필드·타입 유지 | 필터 없는 호출 호환성 |
| `backend/src/main/java/com/kkirikkiri/matching/service/AiAnalysisRateLimiter.java` | AuthLimitStore의 원자적 공유 예약 재사용, 별도 SHA-256 namespace·DB 시각·양수 설정 검증 | 사용자별 AI quota |
| `backend/src/main/java/com/kkirikkiri/matching/service/AiProfileAnalysisService.java` | quota 후 tryAcquire, NOT_SUPPORTED, 예외에도 permit 반환 | AI 호출 보호 |
| `backend/src/main/java/com/kkirikkiri/global/exception/ErrorCode.java` | AI_ANALYSIS_RATE_LIMITED(429), AI_ANALYSIS_BUSY(503) 추가 | 기존 공통 오류 응답 |
| `backend/src/main/resources/application.properties` | AI quota·window·동시 실행 환경설정 | 운영 설정 |
| `backend/.env.example` | 비밀이 아닌 제한값 예시 추가 | 환경설정 안내 |

기존 `AiGatewayClient`, `GlobalExceptionHandler`, `MatchingCandidates`, `surveyApi.js`, `profileSurvey.js`, UserService 및 Entity는 재사용했다. 새 Entity/DDL/인덱스/외부 인프라는 추가하지 않았다.

### 백엔드 테스트·격리 검증 도구·문서

| 파일 | 변경 내용·이유 | 영향 기능 |
|---|---|---|
| `backend/src/test/java/com/kkirikkiri/matching/service/RoommateGuestReadTest.java` | 공개 조건을 포함한 Repository 호출로 mock 변경, 기존 결과 assertion 유지 | 게스트·자기소개·관심 |
| `backend/src/test/java/com/kkirikkiri/matching/service/MatchingVisibilityIntegrationTest.java` | 실제 H2 Repository·별도 트랜잭션·Latch로 상태 변경, count, 상위 9명 필터·bounded 조회 검증 | 목록 무결성·추천 |
| `backend/src/test/java/com/kkirikkiri/matching/controller/AiProfileAnalysisControllerTest.java` | 인증·Principal·공통 429/503/502/409 계약 검증 | AI HTTP 계약 |
| `backend/src/test/java/com/kkirikkiri/matching/service/AiAnalysisRateLimiterIntegrationTest.java` | 독립 limiter 공유·서로 다른 사용자·auth 분리·병렬 예약·롤백·시간 복구·DB 장애 검증 | AI quota |
| `backend/src/test/java/com/kkirikkiri/matching/service/AiAnalysisTransactionIntegrationTest.java` | Gateway 동안 호출자 트랜잭션 중단·복원, 외부 롤백 뒤 quota 유지 검증 | 연결/트랜잭션 수명 |
| `backend/src/test/java/com/kkirikkiri/matching/service/AiProfileAnalysisServiceTest.java` | 병렬 상한·즉시 거절·오류 후 permit 반환·계산 불가·quota 차단 검증 | AI 자원 보호 |
| `backend/src/test/java/com/kkirikkiri/matching/service/AiGatewayClientTest.java` | MockRestServiceServer로 정상/타임아웃/HTTP 실패/잘못된 JSON·content 검증 | 외부 오류 계약 |
| `backend/tools/survey-mysql-concurrency/MatchingMysqlChecks.java` | 실제 서비스·Repository 기반 매칭/설문/상태 경합·PK/FK/UNIQUE/롤백·추천 필터·공유 quota·인덱스/대표 EXPLAIN 기록 | 격리 MySQL 검증 |
| `backend/tools/survey-mysql-concurrency/SurveyMysqlConcurrency.java` | Matching 옵션 등록, 소유 행 정리 확장 및 최초 빈 schema의 정리 후 전체 빈 상태 확인 | 테스트 DB 보호 |
| `backend/tools/survey-mysql-concurrency/run.ps1` | IncludeMatching 옵션·검증 클래스 컴파일 추가, 기존 고정 대상·접근 검사 유지 | 안전한 선택 실행 |
| `backend/tools/survey-mysql-concurrency/RUNBOOK.md` | 추가 검증·측정 범위·물리 테이블명 adapter·다중 프로세스 한계 설명 | 재현 절차 |
| `backend/API.md` | 추천 필터·페이징/격리·AI 제한값·오류 계약 명시 | API 사용처 |
| `backend/DEPLOYMENT.md` | 공유 quota 설정·인스턴스별 동시 상한·운영 검증 조건 | 배포 준비 |
| `backend/MATCHING_VALIDATION.md` | 변경/검증 증거·잔여 위험 기록 | 최종 검토 |

## 4. 테스트 결과

| 검증 항목 | 실행 결과 | 상태 |
|---|---|---|
| 백엔드 단위·MVC·전체 테스트 | 최종 53개 스위트, **663건 통과**, 실패/skip 0. 기존 626건 대비 37건 추가 | 성공 |
| 백엔드 H2 통합 테스트 | 전체 실행에 포함. IntegrationTest 이름의 테스트 82건 포함. MySQL 대체로 취급하지 않음 | 성공 |
| 프론트 마이페이지 테스트 | 기존 실패 10건 해소, 총 14건 통과. survey API 7건 통과 | 성공 |
| 프론트 전체 테스트 | **17개 스위트, 154건 통과** | 성공 |
| 프론트 빌드 | `Compiled successfully`. 이후 제품 JS 변경 없음 | 성공 |
| 백엔드 빌드 | 최종 소스의 bootJar 생성 성공 | 성공 |
| 문서 검증 | 70 endpoints, 71 record DTOs, 29 entities | 성공 |
| 격리 MySQL 테스트 | Survey 12건 + Matching 9건 + 측정/정리 검사 2건 = **23개 기록 PASS**, passed=true. 테스트 사용자 149명 정리 후 schema 전체 빈 상태 확인 | 성공 |
| AI 오류·동시성 | 실제 Gateway 대신 mock HTTP/서비스 사용. 정상·429·503·인증·시간 초과·형식 오류·permit 복구·트랜잭션 검증 | 성공 |
| 추천 필터 성능 | 프론트 필터 변경당 추천 요청 1회, 전체 목록 요청 0회. H2 112개 후보에서 DTO entity load<100/query execution<15, 실제 MySQL 측정은 아래 증거 | 성공 |

실행 명령:

```powershell
# backend 디렉터리
.\gradlew.bat test bootJar --rerun-tasks
# 이후 변경된 최종 소스와 추가 테스트를 포함한 최종 실행
.\gradlew.bat test bootJar

# frontend 디렉터리
$env:CI = 'true'
npm.cmd test -- --watch=false --runInBand
npm.cmd run build

# 저장소 루트
node backend/tools/sync-contract-docs.cjs --check
git diff --check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -PrepareOnly -IncludeMatching
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -IncludeMatching
```

최초 재현: 마이페이지 10건 실패, survey API 7건 통과. 수정 후 assertion 삭제·skip·비활성화 없이 통과했다. AI 서비스 오류 복구 테스트의 mock 재설정을 `doReturn`으로 수정했다. MySQL 신규 quota 사례 최초 실패는 JPA 전용 도구의 대문자 물리 테이블과 Boot 소문자 매핑 차이였다. 도구의 JDBC 식별자 adapter만 수정했으며 기존 타입/Collation 검사와 실제 잠금/예약 동작은 유지했다. 실패 실행의 증거도 남아 있다.

로그: `build/matching-validation/backend-final.log`, `frontend-all.log`, `frontend-build.log`, `mysql-final.log`.

최종 MySQL 증거: [environment.json](build/survey-mysql-concurrency/run-3f67015f-4e0c-443b-99c3-78723d7994b8/environment.json), [evidence.json](build/survey-mysql-concurrency/run-3f67015f-4e0c-443b-99c3-78723d7994b8/evidence.json).
MySQL 8.0.46, 전용 `kkirisurveytest`, REPEATABLE-READ, InnoDB lock wait 3초에서 실행했다. 120명 추가 후 측정한 추천 필터 호출은 254ms였다. 기존 후보도 함께 존재하는 단일 로컬 실행값이며 운영 SLA로 일반화하지 않는다. 이 시간은 실제 서비스의 요청자 score 준비와 조회를 포함한다. 실제 PK/UNIQUE/FK·인덱스 메타데이터와 대표 필터 EXPLAIN은 evidence에 보관했다. 대표 EXPLAIN은 Hibernate가 생성한 SQL 원문 전체의 실행계획 검증을 대체하지 않는다.

중간 실패 증거: `build/survey-mysql-concurrency/run-c4342111-2db0-43d5-a20d-eea6a3da3fe3/evidence.json` (AI quota 도구 식별자 차이, 정리 PASS). 이 실패를 숨기거나 성공으로 변경하지 않았으며 수정 후 별도 run의 전체 성공을 확인했다. 기본 Survey-only 실행 경로는 변경된 공통 정리/컴파일을 포함하지만, 이번 최종 실행은 IncludeMatching 경로다.

## 5. 기존 기능 영향

| 기능 | 확인 결과 |
|---|---|
| 회원가입·로그인·JWT | 기존 전체 백엔드/프론트 회귀 통과. auth 제한 키와 AI 키 분리 검증 |
| 회원정보 | 전공 명시 해제·전공 선택 요구·닉네임 단독 수정 검증. 백엔드 기존 검증·갱신 이벤트 유지 |
| 프로필 설문 | 서버 조회/저장·미작성·완료·재조회·실패 초안·계정 전환 검증 |
| 선호 룸메 설문 | 기존 API·가중치·커밋 후 갱신 유지, 기존 테스트 통과 |
| 룸메찾기 | 전체/추천/상세·공개 조건·필터·취소·페이징 회귀 통과 |
| 매칭 점수 | 산식 변경 없음. 설문 수정/계산 경합 및 매칭 UNIQUE 생성 검증 |
| 관심 프로필 | 추가/취소 로직 유지, 최신 소개·공개 대상·상태 변경/페이지 검증 |
| AI 분석 | 기존 응답 구조와 Gateway 오류 유지, quota/동시 상한 추가 |
| 프로필 이미지 | 전공 기반 기존 매핑·ProfileImage 사용 유지. 새 이미지/매핑 변경 없음 |
| 자기소개 | 최상위 서버 필드·줄바꿈·200자·삭제·레거시 값 보존 및 카드/상세/관심 최신 값 검증 |

## 6. 미검증 사항·잔여 위험

| 수준 | 항목 | 범위·제한 |
|---|---|---|
| Medium | 운영 Gateway·배포 환경 | 실제 유료 Gateway/SMTP·운영 DB/DDL·CORS/프록시·복수 브라우저 수동 UI 확인은 수행하지 않음 |
| Medium | 배포 전체 AI 동시 상한 | 사용자별 quota는 같은 MySQL에서 공유하지만 semaphore는 인스턴스별. N개 인스턴스 합산 상한은 N×설정값 |
| Medium | 실제 규모의 추천 계산 비용 | 프론트 전체 페이지 순회는 제거했지만 요청자별 score 준비는 기존대로 후보 수에 비례. 120명 추가한 로컬 측정은 대규모 운영 부하 결과가 아님 |
| Low | 스냅샷 이후 계정 상태 변경 | 최종 REPEATABLE READ 읽기 이후 커밋과 HTTP 전달의 원자성은 보장하지 않음 |
| Low | 페이지 이동 중 점수 | 점수 준비 후 후보가 제외되어 페이지가 채워지면 새 후보는 기존 저장 점수/null일 수 있음. 다음 조회/기존 비동기 갱신으로 복구하며 API.md에 명시 |
| Low | quota 보존·설정 | 승인한 요청은 계산 실패/용량 초과도 quota 소비. 12회/60초·동시 2건은 조정 가능한 초기값. 기존 bucket 보존 정책을 따르며 자동 삭제 추가 없음 |

Critical/High 미해결 코드 결함은 이번 범위의 자동 검증에서 발견되지 않았다. 실제 운영 배포 성공 또는 승인으로 해석하지 않는다. AI quota MySQL 검증은 독립 객체/연결을 사용하는 단일 JVM이며 실제 다중 프로세스 부하를 증명하지 않는다.

## 7. 배포 전 추가 작업

1. 운영 Gateway 설정·접근·응답/시간 초과 및 실제 사용자 흐름을 검증한다.
2. 모든 인스턴스에서 AI quota 설정·공유 DB·UTC 세션을 맞춘다. 배포 전체 동시 상한이 필요하면 기존 Gateway/ingress 제한을 설정한다.
3. 운영 규모 데이터와 실제 DDL·인덱스에서 추천 score 준비 비용·목록 실행계획·연결 사용량을 확인한다.
4. 기존 배포 체크리스트에 따라 CORS/프록시·스토리지·복구 절차를 확인한다. 이번 수정으로 신규 스키마 마이그레이션을 실행할 필요는 없다.
