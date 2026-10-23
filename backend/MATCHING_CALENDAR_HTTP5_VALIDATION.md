# Matching · Calendar 5단계 실제 HTTP 검증 완료

검증: 2026-10-22 (Asia/Seoul). 최종 보고 갱신: 2026-10-23.
**승인된 29개 논리 시나리오 완료: P0 17 PASS, P1 12 PASS. 실제 애플리케이션 TCP HTTP 212건.**
실행기 오류로 남은 최초 서버 시작 실패와 Calendar 사례 FAIL 1회를 보존했다.
처음부터 모든 실행이 성공했다는 뜻은 아니다. 확정된 서비스 결함은 없다.

- [최종 사례·실행별 요청 수·원본 근거 SHA-256·정리 결과 JSON](validation/matching-calendar-http5-20261022.json)
- [실행 도구·보존 환경 안내](tools/matching-calendar-http5/RUNBOOK.md)
- [승인 전 NOT RUN 계획 JSON — 당시 상태 보존](validation/matching-calendar-http5-plan-20261022.json)

## 1. 최종 P0 / P1 결과

| 구분 | PASS | FAIL | BLOCKED | NOT RUN |
| --- | ---: | ---: | ---: | ---: |
| P0 Matching | 8 | 0 | 0 | 0 |
| P0 Calendar | 9 | 0 | 0 | 0 |
| P1 Matching | 6 | 0 | 0 | 0 |
| P1 Calendar | 6 | 0 | 0 | 0 |
| 합계 | 29 | 0 | 0 | 0 |

유효한 최종 결과 기준이다. 최초 Calendar `patch-null-merge` FAIL을 포함한 논리 사례 실행은
30회이며, 서버 시작 실패 1회와 HTTP 없는 진단 기동 2회는 별도다.
P0 17개·124 HTTP의 사례 결과·오류 코드·서버 관찰·정리를 읽어 검토한 뒤,
지문으로 고정한 `p0-reviewed.json`을 생성하고 P1을 시작했다.
최초 preflight 연결 실패 기록과 조건부 승인 후 BLOCKED 결과도 로컬 history에 보존했다.

| 기능 | 최종 사례 | 실제 확인 |
| --- | ---: | --- |
| 추천 경계·페이지 | 1 | 실제 설문으로 59/60 계산, 60 포함·59 제외, 동점 userId ASC, 명시적 matchScore DESC 수용, size=10→9, 다음 페이지 |
| 복합 필터·상위 9명·필터 페이지 | 3 | scalar/multi 정상 조합, 10위만 필터에 맞아도 보충하지 않음, 필터 후 total=9·페이지 합집합 일치 |
| 가중치 갱신 연결 | 1 | HTTP PUT 후 조회로 복구하기 전에 독립 JDBC에서 17→83 갱신 관찰, 추천 포함/제외·상세 점수 일치 |
| AI 로컬 계약 | 3 | 입력·응답 점수 83 일치, 설명의 999로 점수 변경 없음, malformed→502 AI_GATEWAY_INVALID_RESPONSE, upstream503→503 AI_GATEWAY_UNAVAILABLE, 다음 요청 복구 |
| Matching query·sort·계산 불가·반올림 | 6 | 반복/comma query 일치, 공백·길이 거절/미지원 값 빈 결과, unsupported sort 거절, 선호 없음/상관없음 null·빈 추천, 분석 불가409, 반올림14/13/17 |
| 일정 소유자·타인·시스템 보호 | 3 | 소유자 PATCH→GET→DELETE, 타인 일정 변경/삭제 거절, 시스템 카테고리 변경/삭제 거절 |
| 사용자 카테고리 | 2 | GET 읽기 전용, PATCH·재조회·DELETE·설정 삭제, 타인 카테고리 조회 제외와 변경 거절 |
| 수집 일정·가시성·기간/카테고리 | 3 | 일반/관리자의 수집 일정 PATCH·DELETE 거절, 공개 학교62·본인 포함63·타인 개인 일정 제외, 기간 양끝 포함 겹침·코드 필터 |
| 로컬 수집→공개→관리자 상태 | 1 | 저장소 HTML fixture62개와 COMPLETED/count 일치, 반복 수집 신규0·총62 |
| Calendar 입력·PATCH 병합 | 6 | 잘못된 날짜·시각·병합 역전 거절 및 DB 보존, 자정 허용, 생략/null 전체 DTO·시간·설명 유지, 빈 설명 저장, 카테고리 null 유지 |

## 2. 실제 HTTP 요청 수와 근거

모든 212건은 `java.net.http.HttpClient → 127.0.0.1:18095/18096 → Spring Boot 보안 필터·Controller·Service → MySQL` 경로다.
클라이언트 요청 수와 서버 `http` 관찰 수가 실행별로 모두 일치했다.
MockMvc/H2/직접 Service 호출은 포함하지 않았다.

| 실행 (backend/build/matching-calendar-http5/ 아래) | 사례 결과 | HTTP |
| --- | --- | ---: |
| P0/matching | 서버 시작 INCOMPLETE, 논리 사례 미실행, 원본 보존 | 0 |
| diagnostic-20261022T144748Z | 실패 빈 타입 진단, JVM 종료 | 0 |
| diagnostic-20261022T145132Z | 클래스 경로 분리 후 정상 기동 확인, JVM 종료 | 0 |
| P0-continue1/matching | P0 8 PASS | 41 |
| P0/calendar | P0 9 PASS | 83 |
| P1/matching | P1 6 PASS | 38 |
| P1/calendar | P1 5 PASS + 실행기 기대값 오류 1 FAIL 보존 | 42 |
| P1-null-recovery/calendar | 실패한 null 병합 1개만 재실행 PASS | 8 |
| 합계 | 최종 고유29 PASS, 사례 실행30회 | **212** |

정상 로그인·유효 설문 저장·일정 생성·재조회 등 fixture 요청과 최초 실패한 PATCH도 포함한 수다.
서로 다른 API 수나 신규 기능 수가 아니다. AI stub 하위 HTTP 4건과 HTML fixture 하위 HTTP 4건은
위 212건에 중복 합산하지 않았다. 수집 기동은 제어 IPC이며 HTTP API 호출로 집계하지 않았다.
실제 AI Gateway·학교 홈페이지·SMTP 연결은 0건이다.

cases/requests/servers/summary/cleanup 및 schema·감사 파일을 보존했다.
응답 본문·비밀번호·JWT·메일 원문·외부 자격증명은 저장하지 않았다.
사례 요약에는 합성 ID·선택된 비밀 없는 결과·오류 코드만 남긴다.
Git 제외 원본을 최종 JSON의 SHA-256과 대조할 수 있다.

## 3. 사용한 실제 MySQL 환경

| 항목 | 확인값 |
| --- | --- |
| MySQL | **8.0.46**, mysql:8.0.46-oraclelinux9 |
| 컨테이너 / hostname | kkiri-mc-http5-mysql-20261022 |
| 컨테이너 ID | 9fd6a36a8fbbfc5a297e13571b17c571bf0eca22c53c2063d52ce0629432738f |
| 볼륨 | kkiri-mc-http5-data-20261022 |
| DB / 전용 계정 | kkirimc5http20261022 / mc5http20261022@% |
| 서버 UUID | 078257da-ce27-11f1-94ba-eea983ee4696 |
| 바인딩 / 세션 | 127.0.0.1:13348 → 3306 / JDBC +00:00 |
| 문자셋·정렬 | utf8mb4 / utf8mb4_0900_ai_ci |
| 이미지 ID | sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b |
| 권한 | USAGE + 이 DB만 ALL PRIVILEGES, 전역 데이터 권한·GRANT OPTION 없음 |
| JAR SHA-256 | 19F69E2A0533B5FBC022E940B0FFEA51ACD1E37E076DC4C886125DD315168077 |

최초 Docker Linux 엔진 연결 실패 뒤 이번 preflight는 READY였다.
생성 직전에 로컬 엔진·이름 부재·고정 이미지 지문·13348/18095/18096 포트를 다시 검사했다.
이미지 다운로드나 추가 컨테이너는 없었다. 새 빈 DB에만 최초 Entity DDL과 quota 키 정렬을 준비했다.
이후 오류 복구·P1·단일 재검증은 31개 테이블 0행과 동일 DDL을 확인하여
**스키마 재생성·DDL 재적용 없이 validate**로 수행했다.
서버는 SQL init never, seed NONE, 자동 수집·스케줄 처리 비활성이다.
Calendar 스케줄러 빈은 제어 IPC로 로컬 HTML 수집만 실행한다.
두 도메인의 테스트 설정은 서로 분리된 클래스 경로와 단계별 JVM 1개를 사용했다.

## 4. 서비스 결함과 실행기 오류 구분

**확정된 서비스 결함은 없다.** 서비스·공개 API·오류 코드·스키마 정책은 수정하지 않았다.
발견한 실행기 오류는 다음과 같다.

1. **공유 클래스 경로의 테스트 설정 자동 스캔 충돌.** 두 실행기를 같은 classes 경로에 넣어
   Matching 기동 시 `com.kkirikkiri.calendar.crawler.CalendarMultiServer.Infrastructure`까지 스캔됐다.
   각각의 Primary DataSource 테스트 설정이 섞였고 `UserAccountLockRepositoryImpl`의
   EntityManager 주입 실패로 서버가 시작하지 못했다. 원본 P0는 HTTP 0건, INCOMPLETE다.
   타입/빈 이름만 남기는 진단에서 `jakarta.persistence.EntityManager` 누락을 확인했다.
   `classes/matching`과 `classes/calendar`를 분리한 뒤 같은 JAR·같은 빈 스키마에서 기동에 성공했다.
   원본 실패·진단 2회·정리 결과를 유지하고 미실행 P0만 이어갔다.
2. **null PATCH 사례의 잘못된 시간 문자열 기대값.** 실행기가 `23:59:00`을 기대했지만 기존
   CalendarEventResponse는 `@JsonFormat(pattern="HH:mm")`이다. 첫 사례 FAIL을 보존했다.
   기존 계약의 `23:59`/`00:00`을 검사하고, 생략·명시적 null 모두 전체 DTO와 GET 응답이
   최초와 같으며 DB snapshot도 동일한지 보강했다. 실패한 1개만 다시 실행해 PASS했다.
   이미 PASS한 28개는 반복하지 않았다.
3. **PowerShell 재검증 gate의 JSON 배열 전달.** pipeline ConvertFrom-Json의 배열 wrapper 때문에
   실패 사례 1개 확인 조건이 먼저 거절됐다. HTTP·JVM·새 marker는 생성되지 않았다.
   InputObject와 object[] 명시 변환으로 고친 뒤 동일한 엄격한 실패 사례/종료/정리 검사를 통과했다.

승인 전 첫 컴파일의 한정되지 않은 CrawledAcademicEvent 타입 오류도 준비 단계 기록으로 구분한다.
모든 보정은 새 검증 도구에 한정했으며 운영 자원 변경이나 추가 삭제가 필요하지 않았다.

## 5. 정리·보존 확인

각 실행의 자신이 만든 HTTP JVM 종료 뒤 새 DB의 합성 행만 FK 역순 DELETE했다.
첫 시작 실패·진단까지 포함한 **HTTP JVM 8개 모두 종료**했으며, 정상 기동한 서버는 pool close도 기록했다.
최종 별도 읽기 전용 JDBC 감사에서 **31개 테이블 모두 0행**을 확인했다.
accountConnections=1은 감사 연결 자신이며 이전 애플리케이션 연결은 없다.
최초 DDL과 최종 감사 DDL은 AUTO_INCREMENT 진행값을 제외하고 동일하다.

컨테이너는 **running**이며 볼륨·DB·계정·테이블·스키마를 보존했다.
HTTP 18095/18096 listener는 각각 0개, 승인된 MySQL 127.0.0.1:13348 바인딩은 유지했다.
DROP/TRUNCATE/컨테이너·볼륨 삭제, 기존 자원 시작·정지·변경은 수행하지 않았다.
기존 자원은 이름 충돌 조회 외에 내부 inspect나 DB 접속을 하지 않았다.
기존 자원의 내부 상태를 접속 감사했다고 주장하지 않는다.

근거: `audit-20261022T145812Z.json`, `resource-retention.json`, `http-process-audit.json`, 각 cleanup.json.
운영 소스·원본 Matching/Calendar 도구·계약 문서·JAR·승인 전 계획 등 **284개 보호 파일**의 지문이 동일하다.

## 6. 기존 검증 제외와 미검증

기존 Calendar 31개는 원본 결과를 찾지 못한 **INSUFFICIENT_EVIDENCE** 상태를 유지한다.
이번 29개와 합산하거나 기존 31개 전체 PASS로 보고하지 않는다.
기존 assertion 3개는 출처를 유지해 재사용했다. `cross-server` 사례명은 출처 식별자이며
이번 단계의 다중 JVM 검증을 뜻하지 않는다.

기본 목록 전체 조회·좋아요 경합·기본 일정 생성 독립 사례, Auth/Survey 만료·입력 경계,
기존 Calendar 31개 전체와 다른 도메인 전체 suite는 반복하지 않았다.
유효 설문 PUT과 일정 POST는 추가 경계의 fixture이며 신규 기능 사례로 중복 집계하지 않았다.
관리자 학교 수집 상태 연결은 이 Calendar 검증에만 포함했다.

| 별도 범위 | 상태 | 이유 |
| --- | --- | --- |
| 실제 AI Gateway | BLOCKED | 승인 범위는 로컬 stub만; 실제 endpoint·비용·자격증명 검증 없음 |
| 실제 학교 크롤링 | BLOCKED | 승인 범위는 저장소 HTML fixture만; 학교 네트워크 연결 없음 |
| HTTPS·ingress·운영 자원 | NOT RUN | 승인된 loopback 격리 검증 범위 밖 |
| 기존 Calendar 31개 실행 근거 | INSUFFICIENT_EVIDENCE | 현재 작업공간에서 원본 summary/cases/requests/evidence를 찾지 못함 |
| 기존 단위/H2/다른 도메인 전체 MySQL | NOT RUN | 요청에 따라 반복하지 않음 |

추천 정렬은 60점 동점의 ID 순서와 명시적 DESC 수용을 검증했다.
서로 다른 점수 간 DESC 대조와 복합 필터의 모든 음성 조합을 독립적으로 검증한 것은 아니다.
29개 PASS를 가능한 모든 입력·실제 외부 서비스·운영 배포 검증 완료로 일반화하지 않는다.

## 7. 로컬 검사와 파일 / 커밋 대상

분리한 실행기 javac 컴파일·카탈로그 29개·JAR/main 클래스 일치·Node/PowerShell 구문,
계약 스냅샷(73 endpoints / 75 record DTOs / 30 entities), 보고서 수치·지문 일관성을 확인했다.
서비스/프론트 수정이 없어 전체 Gradle/H2·프론트 테스트/빌드·JAR 재빌드는 반복하지 않았다.

이번 작업의 커밋 대상은 신규 **13개**다. 기존 서비스·원본 검증 도구는 변경하지 않았다.

- `backend/tools/matching-calendar-http5/MatchingHttp5.java`
- `backend/tools/matching-calendar-http5/CalendarHttp5.java`
- `backend/tools/matching-calendar-http5/McHttp5Audit.java`
- `backend/tools/matching-calendar-http5/prepare.cjs`
- `backend/tools/matching-calendar-http5/run.ps1`
- `backend/tools/matching-calendar-http5/provision.ps1`
- `backend/tools/matching-calendar-http5/preflight.ps1`
- `backend/tools/matching-calendar-http5/review.cjs`
- `backend/tools/matching-calendar-http5/report.cjs`
- `backend/tools/matching-calendar-http5/RUNBOOK.md`
- `backend/MATCHING_CALENDAR_HTTP5_VALIDATION.md`
- `backend/validation/matching-calendar-http5-plan-20261022.json`
- `backend/validation/matching-calendar-http5-20261022.json`

`backend/build/matching-calendar-http5/`의 원본·생성 클래스·진단·감사·이력은 Git 제외다.
커밋은 수행하지 않았다. 보존된 대상에서 Provision/P0/P1/역사적 복구 명령을 다시 실행하지 않는다.
