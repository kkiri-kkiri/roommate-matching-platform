# Matching · Calendar 6·7단계 P0 실제 E2E 결과

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar`,
HEAD `011606cbbf37e5340aea66db48c282cfb94d6350`.
신규 실행 ID **`2f55ee3d8226`**.

**실제 Playwright 11개를 각 1회 실행했고 6 PASS / 5 FAIL이다.**
실패는 C04 프론트 제품 결함 1개와 테스트 상호작용·선택자·기대값 문제 4개로 구분한다.
제품 코드와 E2E 코드는 수정하지 않았고 재시도·추가 컨테이너 생성도 하지 않았다.
실패를 PASS로 바꾸거나 과거 실행의 PASS를 합산하지 않았다.

[사례별 결과·원본 SHA-256 JSON](validation/matching-calendar-e2e67-p0-execution-20261026-2f55ee3d8226.json)
· [읽기 전용 집계 도구](tools/matching-calendar-e2e67-p0-execution-report.cjs)

## 1. P0 11개 최종 판정

| ID | 판정 | 실제 관찰 / 미완료 범위 |
| --- | --- | --- |
| R01 | PASS | 전체 목록 두 페이지·카드 ID/점수·새 GET 일치, fixture 후보 수 확인 |
| R02 | FAIL — 테스트 상호작용 | 실제 추천 첫 9개·점수·새 GET은 확인. 빈 추천을 만들 필터의 숨김 checkbox 직접 check에서 pointer interception으로 중단 |
| R03 | FAIL — 테스트 상호작용 | 같은 숨김 checkbox 직접 check 실패. 필터 적용·칩·초기화·필터 후 페이지 분기 미완료 |
| R04 | FAIL — 테스트 선택자 | 카드/상세의 ID·닉네임·점수·소개·meter 비교 후, 배경 필터와 상세에 중복된 문구가 strict locator에 걸림. 후속 새 GET/DB 비교·닫기 미완료 |
| R05 | PASS | 실제 Spring 분석 POST의 FixedAi 설명 표시, 대상/점수·새 GET의 점수 일치 |
| R06 | PASS | 카드 등록·상세 취소·관심 개수·마지막 페이지 7명→6명 복귀와 MySQL 일치 |
| C01 | PASS | 개인 일정 생성·수정·재조회·삭제, 새 GET 및 MySQL 일치 |
| C02 | FAIL — 테스트 기대값 | 카테고리 생성·명시적 삭제·이름/색/표시 PUT·GET·새로고침은 확인. DB의 default_name 변경을 잘못 요구해 실패. 실제 설정 행은 정상 저장 |
| C03 | PASS | A/B/게스트 공개·개인 분리, editable 권한, 타인 PATCH/DELETE 실제 403 및 DB 보존 |
| C04 | FAIL — 프론트 제품 결함 | 정상 시간만 제목 수정 성공. 종일·시작만·종료만·비정각 4종은 프론트 시간 검증으로 PATCH 차단 |
| X01 | PASS | A/B 추천·관심·일정·설정 격리, 실제 8초 JWT 만료 후 401, 재로그인 복구·DB 보존 |

집계: PASS 6 / FAIL 5 / BLOCKED 0 / NOT RUN 0.
이는 시나리오 실행 결과이며, 테스트 코드 문제로 실패한 4개를 서비스 결함 4개로 해석하지 않는다.
반대로 미완료 assertion을 정적 검토로 대체하거나 해당 사례를 PASS로 승격하지도 않는다.

## 2. 실제 실행과 중단 후 재개

컨테이너 생성 전에 `gradlew.bat --no-daemon prepareE2e`로 E2E 전용 소스 컴파일을 확인했다.
초기 샌드박스 연결 거절은 승인된 권한 재실행으로 해결했고 BUILD SUCCESSFUL(40초)을 확인했다.
이는 `test`나 다른 도메인 E2E 실행이 아니다.

관리형 실행기는 기존 `node e2e/run.cjs --mc-p0`를 사용했다.
이미지·브라우저·포트와 정확한 신규 이름 충돌, 실제 native --list의 P0 11개를 다시 확인한 뒤 생성했다.

| 단계 | native 결과 | 시각 KST | 원본 |
| --- | --- | --- | --- |
| c04-original | 1 실행 / 1 FAIL | 11:42:35~11:43:24 | results-c04-original.json |
| p0-remaining | 10 실행 / 6 PASS / 4 FAIL | 11:44:18~11:47:20 | results-p0-remaining.json |

workers=1, retries=0, P0 spec만 선택했다. P1/AUS/CCRA/login 전체 suite를 의존 실행하지 않았다.
각 테스트는 별도 context와 전용 DB reset/seed/clean을 사용했다.

**테스트 종료 후 실행기 대기 timeout은 별도 오류다.**
모든 11개 native 결과가 저장된 다음 사용량 중단으로 후속 finish 명령을 전달하지 못했다.
실행기는 다음 scoped 명령을 1시간 기다린 후 `MC session command timeout`으로 종료 코드 1을 반환했다.
finally에서 데이터 0행 감사·프로세스 종료·소유 컨테이너 삭제는 완료했다(12:47:23 KST).
테스트가 진행 중 끊긴 것이 아니며 native interrupted/skipped/notExecuted는 모두 0이다.

재개 시 git diff는 없었고, 11개 결과와 정리 완료를 확인했다. 이미 완료한 테스트나 자원 생성을 반복하지 않았다.
원본 mc-session.json은 마지막 `AWAITING_COMMAND` 상태가 남아 있지만 수정하지 않았다.
종료 상태는 run.json과 독립 cleanup-audit.json으로 확인했다.

## 3. C04 5종 최초 재현

5종 모두 동일 소유자의 2026-10-26 일정이며 제목만 바꿨다.
성공을 위해 시간을 채우거나 반올림하거나 임의 날짜로 바꾸지 않았다.

| 원본 | 편집창 시간 | PATCH | 제목 수정 | 새 GET·독립 MySQL |
| --- | --- | --- | --- | --- |
| 09:00~10:00 | 09:00 / 10:00 | 1건, HTTP 200 | 성공 | 제목만 변경, 날짜·시간·설명·카테고리 등 나머지 동일 |
| 종일 null/null | 빈 값 / 빈 값 | 0건 | 실패 | 제목·null/null·종일·날짜·나머지 값 원본 유지 |
| 시작만 10:00/null | 10:00 / 빈 값 | 0건 | 실패 | 제목·10:00/null·날짜·나머지 값 원본 유지 |
| 종료만 null/11:00 | 빈 값 / 11:00 | 0건 | 실패 | 제목·null/11:00·날짜·나머지 값 원본 유지 |
| 09:07~10:22 | 빈 값 / 빈 값 | 0건 | 실패 | 제목·09:07/10:22·날짜·나머지 값 원본 유지 |

종일/비정각에는 시작·종료 시간 모두, 시작만에는 종료, 종료만에는 시작에 대해
`15분 단위로 선택해 주세요`라는 프론트 검증 메시지가 표시됐다.
네 실패는 HTTP 4xx/5xx나 서버 롤백이 아니라 **실제 PATCH가 발생하기 전의 프론트 차단**이다.

정상 대조군의 실제 요청:

```json
{
  "title": "MC정상 수정",
  "startDate": "2026-10-26",
  "endDate": "2026-10-26",
  "startTime": "09:00",
  "endTime": "10:00",
  "categoryId": 3,
  "description": "MC 원본 설명"
}
```

응답은 HTTP 200, id=1이며 새 GET과 동일했다.
나머지 4종의 payload/응답은 존재하지 않아 null로 기록했다.
원본 DTO·편집창 입력값·validation·PATCH·새 GET·MySQL before/after 전체는 결과 JSON의 c04에 있다.
최초 실패의 Playwright 원본과 SHA-256도 그대로 보존했다.

확인된 원인은 CalendarPage.js의 validateDraft가 기존 시간 형태까지 두 값 모두 15분 선택지에 있어야 통과시키는 점이다.
비정각 값은 해당 select option이 없어 편집창의 DOM 값도 비어 보인다.
조회/DB에는 정상 원본이 존재하므로 이번 근거로 백엔드 저장 결함을 주장하지 않는다.

## 4. 다른 실패의 원인 구분

### R02·R03: 숨김 input을 직접 누르는 helper

`mc-flows.cjs:45`는 `getByRole('checkbox').check()`를 직접 호출한다.
제품의 FilterChoice는 label이고 HiddenInput은 1px·clip으로 시각적으로 숨겨져 있다.
실제 오류는 주변 div가 pointer event를 가로챈다는 10초 timeout이다.
제품을 고치거나 force click하지 않았다. 보이는 label/키보드 경로로 helper를 보완한 뒤 재검증해야 한다.
해당 경로는 이번 실행에서 검증되지 않았으므로 필터 기능 자체를 정상 또는 결함으로 확정하지 않는다.

### R04: 상세와 배경을 구분하지 않는 locator

`mc-p0.spec.cjs:86`의 전체 페이지 `getByText('22시 이전')`가 배경 필터 span과 상세 tabpanel의 dd 두 개에 일치했다.
응답 불일치가 아니라 strict locator 오류다. 상세 영역에 한정한 선택자가 필요하다.
그 뒤의 설문 배열·새 GET·DB assertion은 실행되지 않았다.

### C02: 설정 저장과 기본 이름을 혼동한 DB 기대값

실제 POST/DELETE/PUT/GET은 모두 200이고, 새로고침 후 변경 이름을 설정창에서 확인했다.
테스트는 `default_name === 'MC새이름'`을 요구한 161행에서 실패했다.

독립 MySQL snapshot의 실제 값:

| 위치 | category_id | 값 |
| --- | ---: | --- |
| calendar_category.default_name | 55 | MC새분류 |
| calendar_category_setting.display_name | 55 | MC새이름 |
| calendar_category_setting.color / is_enabled | 55 | #abcdef / false |

PUT /calendar/categories는 사용자 설정을 저장하며, CalendarService.saveCategorySettings는 설정 행을 갱신한다.
default_name까지 바꾸는 동작을 새 계약으로 요구하지 않는다.
162행 assertion 자체는 앞선 실패 때문에 실행되지 않았지만, 보존된 snapshot에 그 기대 설정 값은 실제 존재한다.
이 사후 검토를 native PASS로 바꾸지 않았다. 서비스 수정 대신 테스트 기대값을 보완할 대상이다.

확인된 프론트 제품 결함은 C04 1개, 확인된 백엔드 결함은 0개다.
정책 미확정 때문에 보류한 사례는 없으며, 미검증 경로까지 무결함이라고 주장하지 않는다.

## 5. 브라우저·서버 HTTP 및 MySQL

| 관찰 | 수 |
| --- | ---: |
| 브라우저 API 요청 시작 | 179 |
| 브라우저 API 응답 관찰 | 160 |
| 브라우저 requestfailed 관찰 | 15 |
| 시작 대비 응답/실패 미대응 기록 | 4 |
| 서버 API HTTP 완료 관찰(OPTIONS 제외) | 165 |
| 서버 상태 | 200=162, 401=1, 403=2 |
| 독립 JDBC snapshot | 22 |
| 테스트별 reset / clean / clean 0행 확인 | 11 / 11 / 11 |

브라우저 traffic JSON과 즉시 기록한 JSONL의 건수를 대조해 모두 일치함을 확인했다.
브라우저 시작은 서버 수신과 동일하지 않고, 이동·취소·teardown 경계의 관찰을 포함한다.
4개의 미대응 시작을 임의로 성공/실패에 채우지 않았다.
서버 수와 브라우저 수가 일치한다고 주장하지 않으며 401/403은 각각 X01 만료·C03 소유권 검증의 예상 응답이다.
관찰된 요청에서 서버 5xx는 없었다.

C04는 10개의 독립 snapshot으로 5종 before/after를 확인했다.
R06은 관심 행·개수, C01은 생성/수정 후 값과 삭제 후 부재, C03은 타인 변경 거절 후 보존,
X01은 재시작·만료·재로그인 전후 관심/일정 보존을 실제 MySQL로 확인했다.
snapshot을 실행하지 못한 실패 사례에 DB 검증 완료를 부여하지 않았다.

AI는 실제 Spring endpoint와 E2eServer.FixedAi를 사용했다. Gateway/학교/실제 SMTP 요청은 0건이다.
학교 일정은 합성 fixture이며 실제 수집 성공 근거가 아니다.

## 6. 신규 자원 생성과 정리

| 항목 | 확인값 |
| --- | --- |
| 신규 컨테이너 | kkiri-e2e-2f55ee3d8226, 총 1개 |
| 컨테이너 ID | f406c1e78dcbca1c5722f051ae1e4116d2fd6cd37c2e0537315992ab0ad7c749 |
| 소유 label | kkiri.e2e.owner=2f55ee3d8226 |
| 이미지 | 기존 mysql:8.0.46-oraclelinux9, --pull=never |
| schema / 계정 | kkiri_e2e_2f55ee3d8226 / e2e_2f55ee3d8226@% |
| MySQL / server UUID | 8.0.46 / 8c139453-d0e6-11f1-bdd9-ba57fcc278d2 |
| 저장 | /var/lib/mysql tmpfs 1GiB, Mounts=[], 볼륨 0개 |
| 데이터 정리 | 각 테스트와 최종 clean에서 guarded DB의 전체 테이블 0행 검사 완료 |
| 컨테이너 정리 | 소유 label·기록한 ID 확인 후 삭제. run.json containerRemoved=true |
| 프로세스 | run.json ownedProcessesStopped=true |
| 독립 사후 감사 | 정확한 새 ID·이름 모두 부재, 13000/18080/13316 재바인딩 가능 |

MySQL 신원 관찰과 사후 감사는 이 신규 자원만 대상으로 했다.
기존 보존 DB·컨테이너·볼륨에는 접근하지 않았다. 새 이미지·브라우저 다운로드, prune, 기존 프로세스 종료도 하지 않았다.
추가 컨테이너를 만들지 않았으며 승인 예산 1개는 사용·정리 완료했다.

## 7. 원본·지문·변경 파일

원본 경로: `backend/build/full-service-e2e/2f55ee3d8226/`.

- results-c04-original.json / results-p0-remaining.json / test-attempts.jsonl: 원래 11개 결과·오류·시각.
- browser-*.json / traffic-*.json·jsonl / server-http.jsonl / database-*.jsonl: UI·HTTP·JDBC 관찰.
- logs/MC-c04-original.log / logs/MC-p0-remaining.log: native Playwright 출력·최초 실패.
- preflight.json / storage-observed.json / resource-identity.json / cleanup-audit.json / run.json: 생성·신원·정리.
- source-before.json / source-after.json / protection-audit.json / evidence-manifest.json: 소스·보호 파일·원본 해시.

native 결과 SHA-256:

```text
c04-original: 753e1246d2a17c5666886f5bc582721349f8cf94657532f409462be1c1056e9c
p0-remaining: 627b8db19fc46b221820e65770ed29e56a14a3fa689652e16512d4668baeb148
```

집계 시 native 결과 해시를 실행기의 mcPhases 값과 대조했고, 원본 70개 파일의 크기·해시를 결과 JSON/manifest에 남겼다.
JWT·생성 비밀번호 형태의 값이 원본에 없는지도 출력 없이 검사했다.
원래 Playwright 결과를 재작성하지 않았다.

보호 대상 **685개 파일의 SHA-256이 모두 동일**하다.
이전 a0ba0c8152c6 원본, 기존 P0/P1 계획·환경 차단 보고·설치 결과, 제품·E2E 코드와 잠금 파일을 보존했다.
sourceUnchangedDuringRun=true이며 브랜치/HEAD도 동일하다.

이번 신규 Git 대상은 이 최종 보고서, 별도 execution 결과 JSON, 읽기 전용 집계 도구 3개다.
이전부터 있던 미추적 보고서/JSON도 그대로 두었다. Git commit/Push/PR/Merge는 하지 않았다.

## 8. 수정 후보와 영향 범위 — 이번에는 미수정

| 파일 | 필요한 후속 검토 |
| --- | --- |
| frontend/src/calendar/CalendarPage.js | C04에서 제목만 바꿀 때 nullable/한쪽/비정각 원본 시간을 보존하도록 편집 검증·select 표시 검토 |
| frontend/src/calendar/CalendarDataContext.js | 위 수정 시 nullable 시간의 전송·생략 의미를 함께 검토. 현재 이 파일의 결함이 재현됐다는 뜻은 아님 |
| frontend/src/calendar/CalendarPage.test.js | 정상·종일·한쪽·비정각 제목 편집과 날짜/시간 보존 회귀 |
| frontend/e2e/lib/mc-flows.cjs | R02/R03에서 실제 label 또는 키보드 상호작용으로 선택하고 checked 상태 확인 |
| frontend/e2e/tests/mc-p0.spec.cjs | R04를 상세 영역에 한정, C02를 settings 행의 계약에 맞게 검증 |

API/DTO/Entity/DB 스키마 또는 서버 Service 변경이 필요하다는 근거는 확보되지 않았다.
시간 자동 채움·반올림·PATCH null 의미 변경으로 해결하지 않는다.
수정과 재검증은 별도 결정하며, 추가 신규 컨테이너가 필요한 재실행은 새 승인이 필요하다.
현재 실행은 결과 보고와 정리까지 완료했고 여기서 중단한다. P1은 실행하지 않았다.
