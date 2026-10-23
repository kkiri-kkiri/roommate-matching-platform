# Auth · User · Survey 6·7단계 P0 검증 결과

2026-10-23 Asia/Seoul. **최종 P0 9 PASS, FAIL/BLOCKED/NOT RUN 각 0. P1 6개는 NOT RUN이며 실행하지 않았다.**
최초 브라우저 실행의 5 PASS/4 FAIL과 HTTP 없는 실행기 실패도 그대로 보존했다.
실패한 4개만 재검증했고 이미 PASS한 5개는 반복하지 않았다. 커밋은 수행하지 않았다.

- [준비 계획·P1 B10~B15 전체 범위](AUTH_USER_SURVEY_E2E67_PLAN.md)
- [최종 사례·실행·근거 SHA-256·정리 JSON](validation/auth-user-survey-e2e67-20261023.json)
- [읽기 전용 결과 집계 도구](tools/auth-user-survey-e2e67-report.cjs)

## P0별 결과

| ID | 상태 | 실제 확인 |
| --- | --- | --- |
| B1 | PASS | 메모리 메일의 실제 가입 인증번호 → React 인증·가입 → MySQL 계정/프로필·email_verified → 로그인·내 계정 GET. 세부전공 없음은 null |
| B2 | PASS | 기존 login.spec.cjs 그대로 실행. 이메일 확인·로그인·새로고침 후 실제 `/auth/me`와 MySQL 계정 확인 |
| B3 | PASS | E2E 가입 토큰 TTL 8초, 실제 9.2초 경과 후 400 EMAIL_VERIFICATION_TOKEN_INVALID·계정 미생성. 입력 유지·재인증 활성화. 실제 60초 cooldown 경과 후 재발송·재인증·동일 폼 가입·로그인 복구 |
| B4 | PASS | 닉네임·생활관·학년·학부/전공 이동 4회·전공 해제·MBTI. PATCH 6회 후 독립 JDBC와 GET·새로고침·재로그인 일치 |
| B5 | PASS | 화면 비밀번호 변경 PATCH → 로그아웃 → 기존 비밀번호 401 → 새 비밀번호 로그인·내 계정 재조회 |
| B6 | PASS | 실제 재설정 요청 → 서비스가 만든 토큰을 메모리 메일/비공개 IPC로 수신 → React 입력·확인 → DB used 상태 → 기존 비밀번호 401·새 로그인 성공 |
| B7 | PASS | 기존 설문 입력 helper 재사용. 프로필 23개 답변·다중선택·소개글을 독립 JDBC와 비교하고 GET·재로그인 후 동일 응답·화면 확인 |
| B8 | PASS | 선호 scalar/multi·18개 가중치를 실제 PUT 제출값과 독립 JDBC로 비교. Void PUT 계약 보존. 이후 GET·재로그인·화면 가중치 일치 |
| B9 | PASS | 같은 브라우저 A→로그아웃→B 전환 시 계정·프로필/선호 격리. 실제 로그인 JWT 8초 자연 만료 전 200/후 401·화면 인증 해제. A 재로그인 후 본인 설문 복원·업무 DB 행 보존 |

B2는 기존 로그인 테스트이며 나머지 8개는 자체 fixture를 갖는 독립 테스트다.
기존 `user-workflow`의 가입/설문 동작을 공통 helper로 이동하고 기존 테스트도 이를 사용하도록 했다.
`auth-boundaries`가 쓰는 기존 로그인·로그아웃·제어 helper를 재사용했다.
기존 `test.step`을 단독 실행 가능한 테스트로 취급하지 않았다.

## 브라우저와 HTTP 집계

실제 경로는 Chromium → React 127.0.0.1:13000 → HTTP 18080 → Spring Security·Controller·Service·JPA → 신규 격리 MySQL 13316이다.
인증 응답 모킹·JWT 주입·MockMvc/H2를 브라우저 검증으로 집계하지 않았다.

| 실행 ID — `backend/build/full-service-e2e/` 아래 | 브라우저 테스트 | 브라우저 API 응답 | 서버 API 완료 |
| --- | --- | ---: | ---: |
| `8f703b4e60ae` | Docker 권한 preflight 실패, 미실행 | 0 | 0 |
| `bc2479b6524d` | 최초 9회: 5 PASS, 4 FAIL | 168 | 169 |
| `bac96aefd975` | 재검증 제목 필터 오류, No tests found | 0 | 0 |
| `a7650271b288` | B3/B4/B5/B8만 4회 재검증 PASS | 88 | 88 |
| 합계 | **고유 P0 9개, 테스트 실행 시도 13회** | **256** | **257** |

OPTIONS는 제외한다. 화면의 자동 조회, 정상 로그인·가입·설문 준비 요청 및 최초 거절 요청을 포함한다.
두 열은 같은 요청에 대한 서로 다른 관찰이므로 합산하지 않는다. 서버 1건 차이는
최초 실행의 `GET /api/roommates/recommended 200`이다. 브라우저 response 이벤트에는 대응 기록이 없다.
화면 전환/종료 경합에 따른 응답 관찰 누락으로 추정하며, 요청 단위 correlation ID로 원인을 확정한 것은 아니다.
따라서 브라우저와 서버 수가 모두 일치했다고 주장하지 않는다.

메인 화면의 Calendar/Community/Matching 자동 GET도 위 수에 포함되지만 해당 도메인의 E2E 사례로 집계하지 않는다.
실제 SMTP·AI·학교 네트워크 호출은 없으며, 제어 IPC·메일 수신용 loopback 제어 HTTP·React 준비 polling은 애플리케이션 API 수에 포함하지 않는다.

## 발견한 결함과 실행기 오류

**재현·수정한 프론트 결함 1개**: 가입 인증 토큰이 만료되면 서버가 정상 거절하지만
SignupPage가 `isVerified=true`와 기존 토큰을 유지했다. 이메일 입력은 readOnly이고 인증 버튼은 비활성인 채
‘인증 완료’로 남아 폼 내 재인증 복구를 막았다. 최초 B3의 실제 400·오류 코드·계정 미생성과 실패 assertion이 근거다.

`authApi.signup`이 서버 errorCode를 전달하고, `EMAIL_VERIFICATION_TOKEN_INVALID`일 때만
SignupPage가 인증 토큰/코드/발송/완료 상태를 해제하도록 고쳤다. 비밀번호·닉네임·학적·MBTI 등 가입 초안은 유지한다.
공개 API·오류 코드·백엔드 서비스·운영 TTL·메일 설정·스키마는 바꾸지 않았다.

실행기/fixture 오류는 다음처럼 별도로 기록한다.

1. `8f703b4e60ae`: Docker named pipe 권한 오류. 자원 생성·DB 접속·브라우저·HTTP 모두 0건이다.
   승인된 Docker 접근으로 이어갔고 기존 실패는 보존했다.
2. 최초 B4/B5: 상위 section까지 일치하는 선택자가 편집 버튼 4개를 잡았다.
   해당 제목의 가장 가까운 section으로 범위를 좁혔다. 화면은 수정하지 않았다.
3. 최초 B8: 기존 선호 PUT은 Void인데 실행기가 DTO를 기대했다.
   실제 제출값 → 독립 JDBC → 후속 GET을 비교하도록 고쳤으며 API 계약은 유지했다.
4. `bac96aefd975`: `--grep`의 시작 anchor가 Playwright의 프로젝트·파일 포함 전체 제목과 맞지 않았다.
   실제 테스트·API HTTP는 0건이다. 필터 수정 후 네 개 목록을 확인했고,
   이후에는 새 자원 생성 전 `--list` 결과 수가 승인 선택 수와 같은지 강제한다.
5. 새 Jest 폼 복구 회귀 1개가 기본 5초 제한을 넘었다. 해당 테스트만 20초로 조정했고
   최종 5.312초로 통과했다. 이 최초 실패도 최종 JSON에 명시했다.

## 환경과 정리

기존 로컬 Docker Linux 엔진, Java 21.0.9, Node 24.14.1, Playwright 1.64.1의 기존 Chromium을 사용했다.
이미지는 `mysql:8.0.46-oraclelinux9`, ID는
`sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b`이다.
이미지·브라우저 다운로드는 하지 않았다. 이미지 생성에는 `--pull=never`를 적용했다.

실제 생성한 컨테이너는 아래 **3개이며 모두 삭제 완료**다. 이름·소유 라벨을 확인한 자기 자원만 정리했다.

- `kkiri-e2e-bc2479b6524d`: 최초 9개 실행
- `kkiri-e2e-bac96aefd975`: No tests found 실행
- `kkiri-e2e-a7650271b288`: 실패 4개 재검증

각 컨테이너는 실행 ID별 신규 스키마·전용 계정과 tmpfs 데이터를 사용했으며 named volume을 만들지 않았다.
JDBC guard가 DB명·계정·UUID·hostname·MySQL 버전·UTC 세션·해당 스키마 한정 권한을 검사했다.
새 빈 스키마만 Entity DDL을 준비하고 서버는 validate/SQL never/seed NONE을 유지했다.
생성한 각 스키마의 잔존 행 0을 확인한 뒤 컨테이너와 임시 데이터를 삭제했다.

재검증 자원의 별도 환경 조회에서도 MySQL **8.0.46**, UUID `5f99eb78-ce8c-11f1-acb3-5ab823563a92`,
DB `kkiri_e2e_a7650271b288`, 계정 `e2e_a7650271b288@%`, utf8mb4/utf8mb4_0900_ai_ci와 +00:00을 기록했다.
환경 조회는 이 신규 자원에만 수행했다.

최종 별도 감사에서 세 컨테이너 이름 모두 부재와 loopback **13000/18080/13316 포트 반환**을 확인했다.
각 run.json은 생성 프로세스 종료·소스 실행 중 불변·정리 오류 없음도 기록한다.
기존 보존 DB/컨테이너/볼륨에는 접속하거나 내부 inspect·변경하지 않았다.
기존 보존 자원의 내부 상태를 접속 감사한 것은 아니다.

원본 근거는 Git 제외 경로에 보존하고 최종 JSON에 SHA-256을 기록했다.
trace/screenshot/video/DOM snapshot을 끄고 오류를 마스킹했다. runtime 결과·진단 46개 파일의
생성 비밀번호/JWT/43자 재설정 토큰 패턴 검사에서도 일치가 없었다. 인증번호·메일 원문·자격증명은 근거에 남기지 않는다.
공용 `test:e2e:clean`은 호출하지 않았다. 완료한 실행 명령은 반복하지 않는다.

## 회귀·빌드와 미검증

| 검사 | 결과 |
| --- | --- |
| E2E Java `prepareE2e` | 컴파일 PASS |
| Node 구문·P0 선택 목록 | PASS, 재검증 선택 4개 |
| 실행기 안전/비밀 마스킹 | 6 PASS |
| 관련 프론트 Jest | 고유 29 PASS; 최초 timeout 1회 후 해당 파일 재검증 포함 총 34회 |
| `npm run build` | PASS |
| `gradlew.bat test bootJar` | BUILD SUCCESSFUL, 두 태스크 UP-TO-DATE. 새 H2 실행 결과로 집계하지 않음 |
| 계약 스냅샷 | 73 endpoints / 75 record DTOs / 30 entities PASS |
| 보호 소스 | 허용한 프론트 수정/회귀 4개를 제외한 backend/src/main·frontend/src 358개 지문 동일 |

P1 B10~B15, 실제 외부 서비스, HTTPS/ingress·배포 환경, 다른 도메인 E2E와 전체 브라우저 suite는 미실행이다.
기존 stateless 로그아웃 계약을 유지하며 탈취/보유 JWT·기존 소켓의 서버 철회를 검증 완료로 주장하지 않는다.
P0의 한정된 입력을 전체 비밀번호/설문/입력 경계 검증 완료로 일반화하지 않는다.

## 변경 파일

운영 프론트 수정은 `frontend/src/api/authApi.js`, `frontend/src/pages/auth/SignupPage.js` 두 개다.
관련 회귀는 `frontend/src/api/authApi.test.js`, `frontend/src/pages/auth/AuthLoading.test.js`다.
다른 담당 화면 및 backend/src/main은 변경하지 않았다.

팀원 A의 실행기·fixture·재사용 변경:

- `backend/src/e2e/java/e2e/E2eServer.java`
- `backend/src/e2e/java/e2e/E2eDatabase.java`
- `frontend/e2e/run.cjs`
- `frontend/e2e/fixtures.cjs`
- `frontend/e2e/runner.test.cjs`
- `frontend/playwright.config.cjs`
- `frontend/e2e/lib/aus-flows.cjs` — 기존 동작 추출
- `frontend/e2e/tests/user-workflow.spec.cjs` — 공통 helper 사용
- `frontend/e2e/tests/aus-p0.spec.cjs` — 독립 P0, B2는 기존 login 재사용

문서·근거 집계 변경:

- `backend/DEPLOYMENT.md`
- `backend/AUTH_USER_SURVEY_E2E67_PLAN.md`
- `backend/AUTH_USER_SURVEY_E2E67_VALIDATION.md`
- `backend/tools/auth-user-survey-e2e67-report.cjs`
- `backend/validation/auth-user-survey-e2e67-20261023.json`

총 18개 파일이다. P1 실행 전에는 B10~B15의 세부 입력 matrix와 코드/재설정 토큰 만료 fixture,
실패/지연 주입·복구·다중 탭·늦은 응답 격리의 관찰 방법을 준비하고 별도 실행 승인을 받아야 한다.
이번 작업은 P0 최종 보고에서 중단한다.
