# Auth · User · Survey 5단계 실제 HTTP 검증 완료

2026-10-22 (Asia/Seoul). **승인된 59개 시나리오 완료: P0 13 PASS, P1 46 PASS.**
실제 TCP HTTP는 **183건**이며 fixture 준비와 초기 실행기 오류 요청까지 포함한 수다.
서비스 결함으로 확정한 항목은 없다. 초기 fixture 실패 2회와 P0 검토 파일 전달 오류 1회는 아래에 별도로 남겼다.
처음부터 모든 실행이 성공했다는 의미가 아니다.

- [사례별 결과·실행별 요청 수·근거 SHA-256·정리 결과 JSON](validation/auth-user-survey-http5-20261022.json)
- [도구와 보존 환경 안내](tools/auth-user-survey-http5/README.md)
- [승인 전 계획 JSON — 당시 NOT RUN 상태 보존](validation/auth-user-survey-http5-plan-20261022.json)
- [기존 1,502 HTTP 검증 보고서](AUTH_USER_SURVEY_MYSQL_VALIDATION.md)

## 1. 실행 결과

| 구분 | 실제 완료 사례 | PASS | FAIL | BLOCKED | NOT RUN |
| --- | ---: | ---: | ---: | ---: | ---: |
| P0 | 13 | 13 | 0 | 0 | 0 |
| P1 | 46 | 46 | 0 | 0 | 0 |
| 합계 | 59 | 59 | 0 | 0 | 0 |

표는 유효한 fixture로 완료한 **59개 논리 시나리오의 최종 결과**다.
동일 만료 사례의 무효 fixture 시도 2회를 포함하면 사례 실행 시도는 61회다.
별도로 P0 검토 JSON 전달 중 실행기 오류가 1회 있었다. 원본 FAIL/INCOMPLETE 결과를 수정하거나 삭제하지 않았다.

| 기능 | 완료 | 실제 확인 결과 |
| --- | ---: | --- |
| availability | 8 | 생략/빈 값/단독·조합, 이메일·닉네임 가용/중복, 한글 쿼리, 기존 형식 미검증 계약 모두 HTTP 200 |
| 만료 인증번호 | 1 | HTTP 400 EMAIL_VERIFICATION_EXPIRED, data=null, 토큰·인증 완료·소비 미생성 |
| 가입 만료/다른 이메일 토큰 | 2 | HTTP 400 EMAIL_VERIFICATION_TOKEN_INVALID, 회원·프로필 미생성, 토큰 미소비 |
| 재설정 미등록/만료 토큰 | 2 | HTTP 400 PASSWORD_RESET_TOKEN_INVALID / PASSWORD_RESET_TOKEN_EXPIRED, 계정·토큰 행 동일, 기존 비밀번호 로그인 성공 |
| 비밀번호 경계·확인·현재 값 | 24 | 가입/로그인/변경/재설정 ASCII·UTF-8 72바이트 허용, 73바이트 거절, 확인 불일치·현재 비밀번호 오류, 거절 후 상태 보존 |
| 선호 가중치 | 9 | 18개 모두 1 저장·조회; 0/6/null 값, 17/19개 키, 빈/null/생략 map 거절 및 기존 설문 보존 |
| 선호 옵션 | 10 | 빈/null/공백/중복/미지원/길이 초과 거절 및 기존 설문 보존; 단독 상관없음 저장·조회 |
| 불완전한 저장 프로필 설문 | 3 | 완료 플래그 false/잘못된 답변/옵션 없음에 false·기존 답변·소개 반환; 반복 GET 전후 DB 동일 |

비밀번호 변경 성공의 data=null, DTO 오류 400 VALIDATION_ERROR, 현재 비밀번호 오류
400 INVALID_REQUEST 등 기존 API/오류 코드를 유지했다. 서비스 코드를 수정하지 않았다.

P0 13개의 결과·오류 코드·만료 fixture 상태를 읽어 검토한 뒤 P1을 실행했다.
검토 파일 전달 오류 후에도 완료한 P0를 반복하지 않고 기록된 검토를 사용해 P1만 이어갔다.
최종 실행은 P0 13개를 근거와 함께 승계하고 P1 46개를 새로 실행했다.

## 2. 실제 HTTP 요청 수와 원본 근거

모든 요청은 실제 java.net.http.HttpClient → 127.0.0.1:18093 → Spring Boot 보안 필터·Controller·Service → MySQL 경로다.
각 실행의 서버 httpFinished 관찰 수도 클라이언트 요청 수와 일치했다. MockMvc/H2/직접 Service 호출을 집계하지 않았다.

| 실행 ID (backend/build/auth-user-survey-http5/runs/ 아래) | 새 시도 | 원본 상태 | 실제 HTTP |
| --- | --- | --- | ---: |
| 2026-10-22-82bee9f0fc29 | availability 8 PASS + 무효 만료 fixture 1 | INCOMPLETE, 사례 1 FAIL 보존 | 11 |
| 2026-10-22-32da878213d5 | 만료 fixture 사전 검사 실패 1, 검증 HTTP 전 중단 | INCOMPLETE, 사례 1 FAIL 보존 | 2 |
| 2026-10-22-a0115b20ea32 | 남은 P0 5 PASS, P0 합계 13 검토 후 전달 오류 | INCOMPLETE, 서비스 사례 FAIL 0 | 16 |
| 2026-10-22-9a38b19aadb1 | P1 46 PASS, P0 13 승계 | PASS, 최종 논리 사례 59 PASS | 154 |
| 합계 | 유효 사례 59 + 무효 fixture 시도 2 | 검토 전달 오류 별도 1 | **183** |

183건에는 실행마다 필요한 fixture 관리자 로그인 4건과 무효 fixture 관련 요청 3건도 포함한다.
나머지 176건도 각 시나리오의 토큰 발급·로그인·정상 저장·결과 조회를 포함한 수이며, 서로 다른 API 수나 시나리오 수가 아니다.
기존 1,502건은 재실행하거나 이번 183건에 합산하지 않았다.

각 디렉터리의 requests.json, servers.json, cases.json, summary.json, cleanup.json을 보존했다.
최종 outcomes.json에는 59개 모두의 상태와 실제 실행 근거가 있다.
응답 본문·비밀번호·JWT·인증/재설정 토큰·메일 내용은 원본 근거에도 저장하지 않았다.
원본은 Git 제외이며, Git용 결과 JSON에 사례 결과와 근거 파일의 SHA-256을 보존했다.

## 3. MySQL 버전과 실제 환경

| 항목 | 실제 확인값 |
| --- | --- |
| 컨테이너 | kkiri-aus-http5-mysql-20261022 |
| 컨테이너 ID | 5c9d0a527a056c25ba44105caf281a845bacfe800498ab3b4cc5c627cc0a6bcc |
| 볼륨 | kkiri-aus-http5-data-20261022 |
| DB / 전용 계정 | kkiriaus5http20261022 / aus5http20261022@% |
| MySQL | **8.0.46**, mysql:8.0.46-oraclelinux9 |
| 서버 UUID | cae1bfa1-cdea-11f1-a074-76682699272e |
| hostname | kkiri-aus-http5-mysql-20261022 |
| MySQL 바인딩 | 127.0.0.1:13327 → 3306, JDBC 세션 +00:00 |
| 문자셋 / 정렬 | utf8mb4 / utf8mb4_0900_ai_ci |
| 이미지 ID | sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b |
| HTTP | 127.0.0.1:18093, 매번 JVM 1개만 기동, 최종 종료·listener 0개 |
| 애플리케이션 | 별도 검증 프로필, ddl-auto=validate, SQL init never, 시드·수집·스케줄 비활성 |
| 메일 | 실제 JavaMail 서비스가 호출하는 sender만 메모리 대체, 인증된 loopback IPC로 관찰, 실제 SMTP 연결 0 |
| 권한 | USAGE + 해당 DB만 ALL PRIVILEGES, 전역 데이터 권한·GRANT OPTION 없음 |
| Java / Node | Java 21.0.9 / Node v24.14.1 |
| 기준 Git | 4643a222851a2a2692ed2a39ba837c194cc498a8 위 미커밋 도구·보고서 |
| JAR SHA-256 | 19F69E2A0533B5FBC022E940B0FFEA51ACD1E37E076DC4C886125DD315168077 |

생성 전에 새 자원 이름 부재·고정 포트 bind 가능·로컬 이미지 지문·로컬 Linux 엔진을 확인했다.
연결마다 DB/계정/버전/UUID/hostname/UTC/JDBC URL을 검사했다.
첫 실행에서만 빈 DB에 31개 테이블을 생성했다. 이후 도구 오류 복구는 31개 테이블 0행과 DDL 동일을 확인하고
**DDL 재적용·스키마 재생성 없이** 동일 신규 자원을 사용했다. 실행 이력/일회용 marker는 삭제하거나 덮어쓰지 않았다.
기존 보존 DB·컨테이너·운영 자원에 접속하거나 기존 계정 자격증명을 읽지 않았다.

## 4. 만료 검증의 범위와 발견한 오류

만료 3개는 **fixture 만료 시각 조정 검증 PASS**다. 실제 TTL 경과는 **NOT RUN**이다.
각각 새 DB의 해당 합성 행 1개만 변경하고, HTTP 전에 Hibernate가 해석한 시각이 과거인지 확인했다.
저장된 DB 값 1999-12-31 15:00:00과 유효 애플리케이션 값 2000-01-01T00:00을 비밀 없는 근거로 남겼다.
실제 300초 대기나 배포 서버 간 시계 동기화를 검증했다는 뜻이 아니다.

확정된 **서비스 결함은 없다**. 발견한 실행기 오류는 다음과 같다.

1. **만료 fixture의 JDBC/Hibernate 시간 바인딩 차이.** UTC JDBC에서 setObject(LocalDateTime)으로 넣은 값은
   Hibernate의 Timestamp 조회·변환 경로에서 9시간 뒤로 해석됐다. 첫 “1분 전” fixture는 실질적으로 미래여서
   verify가 200을 반환했다. 읽기 전용 재현: 2000-01-02T03:04:05를 바인딩하면 DB/direct JDBC는 같은 값,
   getTimestamp + LocalDateTimeJavaType.wrap은 2000-01-02T12:04:05다.
   이를 서비스 만료 오류로 판정하지 않았다. 명시적 setTimestamp와 고정된 과거 초를 사용하고 유효 시각을 확인해 해결했다.
2. **첫 보정 후 과도하게 엄격한 만료 사전 검사.** 만료 여부 외에 반올림 전 target 이하까지 요구한 비교에서 중단했다.
   verify HTTP는 보내지 않았다. 당시 원시 시각이 저장되지 않아 구체적인 소수초 반올림 원인은 확정하지 않는다.
   고정된 과거 초와 실제 필요한 “유효 시각 < 현재” 조건으로 바꾸고, 실패 시에도 진단을 먼저 저장하도록 보완했다.
3. **P0 검토 JSON의 UTF-8 BOM.** PowerShell Set-Content -Encoding UTF8 파일을 Files.readString →
   Jackson readTree(String)으로 읽으면서 StreamReadException이 발생했다. P0 13개는 이미 PASS였고 P1은 미실행이었다.
   JSON 바이트 입력으로 BOM을 처리하고 기존 검토 결과를 재사용해 해결했다.

모든 보정은 새 검증 도구에 한정했다. 서비스·API·스키마 정책·오류 코드를 수정하지 않았다.
원본 오류와 각 단계의 정리 결과는 유지했다. 새 자원/환경 변경이나 삭제는 필요하지 않았다.

## 5. 데이터 정리·자원 보존 및 미검증

각 실행에서 자신이 만든 HTTP JVM의 종료와 pool close를 먼저 확인한 뒤 새 DB의 fixture 행만 FK 역순으로 DELETE했다.
최종 정리 후 별도 읽기 전용 JDBC 감사에서도 **31개 테이블 모두 0행**을 확인했다.
최초 DDL과 최종 감사 DDL은 AUTO_INCREMENT 진행값을 제외하고 동일했다.
컨테이너는 **running**, 볼륨·DB·계정·테이블·스키마는 보존했다. DROP/TRUNCATE/자원 삭제는 실행하지 않았다.
HTTP 18093 listener는 0개이며 승인된 MySQL 127.0.0.1:13327 바인딩은 유지했다.

로컬 근거: build/auth-user-survey-http5/audit-20261022075736.json, resource-retention.json과 각 실행의 cleanup.json.

| 별도 범위 | 상태 | 이유 |
| --- | --- | --- |
| 실제 TTL 경과 | NOT RUN | fixture 시각 조정만 수행; 59개 최종 상태와 별개 |
| 실제 SMTP | BLOCKED | 승인된 배포 메일 환경 없음, 연결 0건 |
| HTTPS | BLOCKED | 승인된 TLS 배포 endpoint 없음, 연결 0건 |
| ingress | BLOCKED | 승인된 ingress 환경 없음, 연결 0건 |

운영 마이그레이션·실제 외부 메일 전달·HTTPS/ingress·실제 TTL을 포함한 배포 검증 완료를 선언하지 않는다.

## 6. 중복 제외·로컬 검사

기존 원본 1,502 HTTP 요청과 summary 해시를 읽어 담당 16개 Controller 메서드 중 15개 근거를 재확인했다.
이번 availability 8개로 누락됐던 1개 메서드의 실제 HTTP 근거를 추가해 **16/16개**가 됐다.
메서드 근거가 모두 있다는 것이 가능한 모든 입력을 검증했다는 뜻은 아니다.

기존 Auth 32개, User 27개, 선택 JWT 보안 1개, Survey 보완 6개는 반복 실행하지 않았다.
weights=2/5(상한 포함), 미지원 키로 18개 중 1개 교체, 상관없음+동갑, 미작성 설문의 false/null,
소개·소유권·인증·동시성·롤백·물리 제약 등 중복 제외 상세는 결과 JSON에 보존했다.
새 경계의 사전조건인 정상 로그인·토큰 발급·저장은 실제 요청 수에만 포함하고 별도 신규 기능으로 세지 않았다.

새 실행기 javac 컴파일, JAR와 main 클래스 292개 일치, PowerShell/Node 구문 검사,
승인 없는 실행 차단, API 계약 스냅샷 검사를 통과했다.
운영 소스/리소스 **265개**의 최초·최종 지문과 JAR 지문이 동일하다.
기존 단위/H2/서비스 MySQL/1,502 HTTP는 요청에 따라 반복하지 않았으며 JAR도 재빌드하지 않았다.

## 7. 생성·수정 파일과 Git 커밋 대상

커밋 대상은 신규 11개와 .gitignore 수정 1개, 총 12개다. 기존 도구·서비스·다른 도메인 파일은 변경하지 않았다.

- .gitignore: 전체 README 제외 규칙에서 이번 도구 안내 파일만 예외 처리하여 Git 누락 방지.
- backend/tools/auth-user-survey-http5/AusHttp5.java: 59개 추가 사례, P0 검토 gate, 진단한 실행기 오류의 한정된 이어 실행.
- backend/tools/auth-user-survey-http5/AusHttp5Audit.java: 새 DB 읽기 전용 identity/권한/행/DDL/시간 변환 감사.
- backend/tools/auth-user-survey-http5/prepare.cjs: 기존 도구를 새 대상의 로컬 생성본으로 준비.
- backend/tools/auth-user-survey-http5/provision.ps1: 승인된 새 자원 생성·충돌 방지.
- backend/tools/auth-user-survey-http5/run.ps1: Prepare/Execute/역사적 Continue/읽기 전용 Audit.
- backend/tools/auth-user-survey-http5/review.cjs: 승인 전 계획 검토. 생성 후 계획 덮어쓰기 차단.
- backend/tools/auth-user-survey-http5/report.cjs: 4회 실행·오류·최종 59개·183 HTTP·정리 근거 대조 및 Git용 결과 생성.
- backend/tools/auth-user-survey-http5/README.md
- backend/AUTH_USER_SURVEY_HTTP5_VALIDATION.md
- backend/validation/auth-user-survey-http5-plan-20261022.json: 승인 전 계획 기록.
- backend/validation/auth-user-survey-http5-20261022.json: 실제 완료 결과.

backend/build/auth-user-survey-http5/는 Git 제외다. 비밀정보·응답 본문·메일 원문을 커밋하지 않는다.
커밋은 수행하지 않았다. 보존 환경에서 전체 Execute/Provision/Continue를 다시 실행하지 않는다.
