# Matching · Calendar 6·7단계 P0 수정 및 실패 사례 재검증 결과

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar`,
HEAD `011606cbbf37e5340aea66db48c282cfb94d6350` 유지.
최초 실행 **`2f55ee3d8226`**, 재검증 실행 **`2d19b8963e9a`**.

**실패했던 5개를 각 1회 실제 Playwright로 재검증하여 5 PASS / 0 FAIL이다.**
최초 PASS 6개와 연결한 P0 최종 상태는 **11 PASS / 0 FAIL / 0 BLOCKED / 0 NOT RUN**이다.
11개를 이번 실행에서 모두 다시 실행했다는 의미가 아니다.
[최초 6 PASS / 5 FAIL 보고서](MATCHING_CALENDAR_E2E67_P0_EXECUTION_20261026.md)와 원본은 그대로 보존했다.

[재검증 결과·원본 SHA-256 JSON](validation/matching-calendar-e2e67-p0-recheck-20261026-2d19b8963e9a.json)
· [원본 집계 도구](tools/matching-calendar-e2e67-p0-recheck-report.cjs)

## 1. 사례별 최종 상태

| ID | 최초 | 이번 재검증 | 최종 | 근거 |
| --- | --- | --- | --- | --- |
| R01 | PASS | NOT RUN | PASS | 최초 전체 목록·페이지·새 GET 검증 유지 |
| R02 | FAIL | PASS | PASS | 추천 첫 9개, 보이는 label 선택·checked, 실제 query·칩·카드 수, 빈 추천 및 해제 후 복구 |
| R03 | FAIL | PASS | PASS | 필터 적용·2페이지·새 GET, 칩 취소·초기화 후 원래 목록 복구, 추천 필터 |
| R04 | FAIL | PASS | PASS | 상세 dialog/생활 패턴 tabpanel 안의 점수·소개·설문, 후속 GET·독립 JDBC·상세 닫기 |
| R05 | PASS | NOT RUN | PASS | 최초 FixedAi 분석·점수 검증 유지 |
| R06 | PASS | NOT RUN | PASS | 최초 좋아요·관심 목록·MySQL 검증 유지 |
| C01 | PASS | NOT RUN | PASS | 최초 CRUD 검증 유지. 수정 영향은 관련 Jest와 C04의 정상 시간 실제 PATCH로 별도 확인 |
| C02 | FAIL | PASS | PASS | PUT→GET→새로고침→독립 JDBC, 기본 이름 보존과 사용자 표시 이름·색·표시 설정 확인 |
| C03 | PASS | NOT RUN | PASS | 최초 공개/개인·소유권 403·DB 보존 검증 유지 |
| C04 | FAIL | PASS | PASS | 5종 모두 제목만 PATCH 200, 새 GET·독립 JDBC로 원본 보존 |
| X01 | PASS | NOT RUN | PASS | 최초 인증 만료·계정 전환·격리 검증 유지 |

기존 PASS 6개, P1, AUS·CCRA 및 전체 E2E suite는 실제 재실행하지 않았다.

## 2. C04 수정과 실제 저장 결과

`CalendarPage.js`는 편집 중 원본 시간 쌍이 그대로일 때만 기존 nullable·한쪽·비정각 값을 허용한다.
비정각 값도 select option으로 표시한다. 사용자가 시간을 바꾸면 두 값 모두 기존 15분 선택지 검증을 받는다.
날짜·시간 순서 검증과 신규 등록 검증은 유지했다.

`CalendarDataContext.js`는 편집 PATCH에서 변경하지 않은 필드를 생략한다.
이에 따라 null 시간, 원래 설명의 공백, 날짜·분류 등은 서버에 그대로 남는다.
시간 자동 채움·반올림·시간 삭제·PATCH null 의미를 추가하지 않았다.
백엔드 API·DTO·Entity·Service·DB 스키마는 수정하지 않았다.

| 원본 시간 | 최초 실행 | 수정 후 실제 PATCH | 새 GET·독립 MySQL 결과 |
| --- | --- | --- | --- |
| 09:00~10:00 | PATCH 200 | 1건 / 200 | 제목만 변경, 09:00:00 / 10:00:00 보존 |
| null/null 종일 | PATCH 전 차단 | 1건 / 200 | 제목만 변경, null/null·종일 보존 |
| 10:00/null | PATCH 전 차단 | 1건 / 200 | 제목만 변경, 10:00:00/null 보존 |
| null/11:00 | PATCH 전 차단 | 1건 / 200 | 제목만 변경, null/11:00:00 보존 |
| 09:07~10:22 | PATCH 전 차단 | 1건 / 200 | 제목만 변경, 09:07:00 / 10:22:00 보존 |

다섯 요청은 각각 `{ "title": "<원본 제목> 수정" }`만 전송했다.
각 before/after JDBC 행과 원본/새 GET DTO 전체를 제목을 제외하고 비교하여 동일함을 확인했다.
날짜·시간·설명·카테고리·소유자·출처 등 변경하지 않은 필드를 함께 검사했다.
5종의 편집창 입력값·payload·응답·GET·JDBC before/after는 결과 JSON의 `c04`에 있다.
최초 실행에서 차단됐던 4종의 원본은 별도로 유지한다.

## 3. 테스트 및 실행기 보완

- R02·R03: 숨김 input 직접 check를 보이는 label 문구 클릭으로 바꾸고 checked를 확인했다.
  force 클릭·DOM 강제 클릭·checked 직접 변경은 없다. 실제 응답 URL의 page/size/sleepTime을 검사하고,
  칩·카드 ID/점수·카드 수와 비교했다. 취소·초기화 후 page=0·원래 결과 복구도 확인했다.
  화면에 없는 전체 결과 개수 표시를 새로 요구하지 않았다.
- R04: 대상 닉네임으로 이름이 정해지는 상세 dialog와 `생활 패턴` tabpanel에 locator를 한정했다.
  소개의 first()도 제거했다. 기존 비교와 후속 새 GET·매칭 점수/설문 JDBC 검증·닫기를 모두 실행했다.
- C02: 생성한 category_id=31의 `default_name=MC새분류` 유지와 별도 설정 행의
  `display_name=MC새이름`, `color=#abcdef`, `is_enabled=false`를 독립 JDBC로 확인했다.
  최초 실행의 ID 55를 재사용하거나 하드코딩하지 않았다.
- 실행기: `--mc-p0-recheck 2f55ee3d8226` 모드를 추가했다.
  원본 해시·11개 native 결과·이전 정리 완료를 읽어 승인된 실패 5개만 선택한다.
  C04→나머지 4개를 각 1회 실행하고 자동 종료한다. 별도 ledger로 신규 컨테이너를 최대 1개로 제한한다.
  기존 MC 전체 실행 및 다른 도메인의 선택·retention 흐름은 유지했다.

| 확인 | 결과 |
| --- | --- |
| 관련 Jest 4 suites | 29 PASS: CalendarPage, CalendarDataContext, calendarDates, RoommateSearchPage |
| MC 실행기 안전 단위 검사 | 6 PASS: 선택·증거 변조·미정리·재검증 연결·단일 자원 예산 등 |
| 공통 실행기 단위 검사 | 31 PASS / 3 SKIP. Docker/Java/React/Playwright를 모의한 검사이며 다른 도메인 E2E 실행이 아님 |
| 프런트 빌드 | `npm run build`, Compiled successfully / exit 0 |
| E2E 전용 소스 컴파일 | `gradlew.bat --offline --no-daemon prepareE2e`, BUILD SUCCESSFUL |
| 계약 문서 검사 | 73 endpoints / 75 record DTOs / 30 entities 일치 |
| native Playwright 사전 목록 | 5 tests / 1 file, `mc-p0`, `--no-deps` |

공통 실행기 검사의 3 SKIP은 선택적으로 활성화하는 Java guard probe이며 실제 P0 테스트의 skip이 아니다.
이번 backend `test bootJar`는 NOT RUN이다. 백엔드 제품 변경은 없고 E2E 소스 컴파일만 실행했다.

## 4. 실제 실행·HTTP·MySQL 근거

| phase | KST | native 결과 |
| --- | --- | --- |
| c04-recheck | 14:29:58~14:30:28 | 1 실행 / 1 PASS |
| failed-recheck | 14:30:29~14:31:17 | 4 실행 / 4 PASS |

workers=1, retries=0, skipped/interrupted/notExecuted=0.
테스트마다 별도 context와 신규 소유 schema의 reset/seed/clean을 사용했다.
관리형 실행기 exit 0, Playwright exit 0, session=FINISHED, evidenceErrors=[]이다.

| 관찰 | 수 |
| --- | ---: |
| 브라우저 API 요청 시작 / 응답 / requestfailed | 69 / 64 / 5 |
| 시작 대비 미대응 기록 | 0 |
| 서버 HTTP 완료(OPTIONS 제외) | 65, 모두 200 |
| 독립 JDBC snapshot | 12: C04 10, R04 1, C02 1 |
| 사례별 reset / clean / 0행 확인 | 5 / 5 / 5 |

브라우저 실패 5건은 모두 `net::ERR_ABORTED`이다. 브라우저 시작·취소와 서버 완료는 서로 다른 관찰이므로
브라우저와 서버 건수를 같게 맞추지 않았다. traffic JSON과 즉시 기록한 JSONL의 집계도 대조했다.
R02·R03은 실제 Spring 응답·새 GET·화면을 비교했으며, 별도 matching SQL snapshot 검증을 했다고 주장하지 않는다.
AI는 FixedAi, 학교 수집은 DisabledCrawler, 메일은 MemoryMail이다. 실제 Gateway·학교·SMTP 요청은 없다.

독립 신원 감사 스크립트의 첫 읽기 전용 inspect에서 JSON 닫는 괄호 누락으로 파싱이 실패했다.
DB 접근 전에 중단됐고 형식만 바로잡아 관찰을 완료했다. native 테스트·실행기 실패나 재시도가 아니며
`identity-observed.json`에 이 감사 오류를 기록했다.

## 5. 신규 자원 생성·정리

| 항목 | 확인 |
| --- | --- |
| 신규 컨테이너 | kkiri-e2e-2d19b8963e9a, 총 1개 |
| ID | 7aefc45db0f795a3fb4eeca3e0fa392e2ebe972bc8850ec4688354b0108211a4 |
| 소유 label | kkiri.e2e.owner=2d19b8963e9a |
| schema / 계정 | kkiri_e2e_2d19b8963e9a / e2e_2d19b8963e9a@% |
| MySQL / UUID | 8.0.46 / 27e1ed97-d0fe-11f1-960f-5efdbab7ed38 |
| 이미지 ID | sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b |
| 저장 | /var/lib/mysql tmpfs 1GiB, Mounts=[], named/anonymous volume 0 |
| 사전 검사 | Linux 엔진·기존 Chromium 156.0.8078.4·이미지·포트·정확한 신규 이름 충돌 없음 |
| 데이터·프로세스 | 사례별 및 최종 전체 fixture 테이블 0행, 소유 프로세스 종료 확인 |
| 삭제·사후 감사 | 소유 label·기록 ID 대조 후 삭제, 정확한 ID·이름 모두 부재, 13000/18080/13316 재바인딩 가능 |

신규 schema·계정은 삭제한 컨테이너의 tmpfs와 함께 소멸했다.
기존 보존 DB·컨테이너·볼륨, 최초 실행 자원에는 접근하지 않았다. 추가 다운로드·두 번째 컨테이너·prune도 없다.
이번 재검증 승인 예산 1개는 사용·정리 완료했다.

## 6. 원본·지문과 변경 파일

새 원본: `backend/build/full-service-e2e/2d19b8963e9a/`.
준비/단위/빌드 로그: `backend/build/mc-p0-recheck-preparation/20261026/`.
native results, test-attempts.jsonl, browser/traffic/database/server-http,
resource-identity/identity-observed/cleanup-audit/run, source-before/after, protection-audit를 보존했다.
기존 769개 파일의 SHA-256이 모두 동일하다. 이 기준은 이번 승인 수정이 반영된 실행 직전에 확보했으며,
최초 실행·차단 기록·설치 결과·P1 준비안 보존과 실행 중 소스 불변을 확인한다.

```text
results-c04-recheck.json: d7341e1d37ba5ea419949309eb8f2b0d4f80f1ae251d029fc30b2072ba596b79
results-failed-recheck.json: aa2d3c618172e071755e0223895f4069abc9ea8b5d23d6a6ca56ce9020a84e97
결과 JSON: ca0a3f70e7a6a22d048490a109fa66c30f7025897329753deadcd1d63aff2787
```

`evidence-manifest.json`은 원본·준비 파일 50개의 크기·SHA-256과 결과 JSON 지문을 연결한다.
그 뒤 실행한 공통 실행기 단위 검사와 이 보고서 지문은 `verification-supplement.json`에 별도로 기록했다.
JWT·생성 비밀번호 형태의 원본 저장 여부도 출력 없이 검사했다.

변경 소스는 아래 8개다. 다른 팀원의 별도 작업이나 기존 보고서를 덮어쓰지 않았다.

- 제품·회귀: `frontend/src/calendar/CalendarPage.js`, `CalendarDataContext.js`, `CalendarPage.test.js`.
- E2E: `frontend/e2e/lib/mc-flows.cjs`, `tests/mc-p0.spec.cjs`, `mc-session.cjs`, `mc-safety.test.cjs`, `run.cjs`.
- 이번 새 최종 산출물: 이 보고서, 별도 recheck 결과 JSON, 집계 도구.
  이전 미추적 보고서·준비 결과는 그대로 두었다.

권장 커밋 메시지(실제 커밋하지 않음):

```text
fix(calendar): preserve original event times when editing details
test(e2e): repair matching calendar P0 checks and scoped revalidation
docs(validation): record matching calendar P0 recheck evidence
```

## 7. P1 착수 판단

P0 원본 부재와 미해결 실패에 따른 선행 조건은 이번 실제 근거로 해소됐다.
P1 준비안을 검토하고 별도 승인으로 구현·실행에 착수할 수 있다.
기존 P1 준비안은 수정하지 않았고, P1은 NOT RUN이다.
Git 커밋·Push·PR·Merge 없이 결과 보고와 신규 소유 자원 정리를 마쳤으며 여기서 중단한다.
