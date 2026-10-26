# Matching · Calendar P0 수정 및 재검증 준비 결과

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar`,
HEAD `011606cbbf37e5340aea66db48c282cfb94d6350` 유지.
최초 실행 `2f55ee3d8226`의 **6 PASS / 5 FAIL을 그대로 보존**한다.
이번에는 C04 수정과 관련 단위 검증, R02·R03 helper 보완 및 선택 목록 확인을 수행했다.
새로운 실제 브라우저·HTTP·MySQL 재검증은 **NOT RUN**이다.

## 변경 내용

| 파일 | 변경과 영향 |
| --- | --- |
| `frontend/src/calendar/CalendarPage.js` | 편집 중 원본 시간 쌍이 그대로인 경우 nullable·한쪽·비정각 시간을 허용한다. 비정각 원본을 실제 select option으로 표시하고 미지정 시간도 표시한다. 시간을 변경하면 양쪽 모두 기존 15분 선택지 검증을 적용하며 날짜·시간 순서 검증도 유지한다. |
| `frontend/src/calendar/CalendarDataContext.js` | 기존 일정 PATCH에서 변경하지 않은 필드를 생략한다. 제목 수정은 `{title}`만 전송하여 null 시간과 설명의 원래 공백 등을 서버에 보존한다. 생성 payload와 PATCH null 의미는 변경하지 않는다. |
| `frontend/src/calendar/CalendarPage.test.js` | 5종 제목 편집·원본 보존, 시간 변경·삭제 시도, 날짜·설명 수정과 날짜 오류, 신규 등록·시간 순서 회귀를 추가했다. API 경계를 mock하므로 실제 HTTP/MySQL 근거로 집계하지 않는다. |
| `frontend/e2e/lib/mc-flows.cjs` | 숨김 checkbox 직접 check 대신 보이는 label 문구를 클릭하고 checked 상태를 확인한다. force 클릭은 사용하지 않는다. 실제 E2E 상호작용은 재검증 전이다. |

백엔드 API·DTO·Entity·Service·DB 스키마, 공통 실행기, 패키지·잠금 파일은 변경하지 않았다.
원본 시간 채움·반올림·시간 삭제 정책을 추가하지 않았다.

## 확인 결과

- 관련 Jest: **3 suites / 25 tests PASS**.
  `CalendarPage.test.js`, `CalendarDataContext.test.js`, `calendarDates.test.js`만 실행했다.
- `npm run build`: **Compiled successfully**, 종료 코드 0.
- `sync-contract-docs.cjs --check`: **73 endpoints / 75 record DTOs / 30 entities**, 종료 코드 0.
- helper `node --check`, `git diff --check`: 통과.
- native Playwright `--list --project=mc-p0 --no-deps --grep '(C04|R02|R03|R04|C02) P0:'`:
  **5 tests / 1 file**. 테스트 실행이나 fixture reset은 수행하지 않았다.

단위 테스트의 mock 저장 모델에서 다섯 시간 형태 모두 제목만 바뀌고 원본 날짜·시간·설명·분류가 보존됐다.
이를 실제 MySQL 재검증 PASS로 승격하지 않는다. 최초 E2E 실패 원본도 대체하지 않는다.
테스트 작성 중 초기 오류 메시지 기대값 한 건을 기존 실제 문구로 정정했으며 초안 로그도 보존했다.

근거 폴더: `backend/build/mc-p0-fix-preparation/20261026/`.
최종 근거는 `regression-final.log`, `frontend-build-final.log`, `contracts-check.log`,
`failed-case-list.log`, `protection-after.json`이다.
[별도 결과·SHA-256 JSON](validation/matching-calendar-e2e67-p0-fix-preparation-20261026.json)에 파일 지문을 기록했다.

## 보존 및 남은 범위

이전 보호 기준 685개 중 의도한 위 4개 소스만 달라졌고 나머지 681개는 동일하다.
이번 수정 전 추가로 지문을 확보한 최초 실행·차단 원본 및 기존 보고서·P1 준비안 83개도 모두 동일하다.
Docker 명령·자원 생성·DB 접속·외부 AI/학교 요청·Git 커밋/Push/PR/Merge는 수행하지 않았다.

사용자 요청이 R02·R03의 `숨겨진 checkbox를 직접 check하`에서 끊겨 나머지 지침을 요청한 상태다.
R04·C02 spec은 아직 수정하지 않았다. 신규 컨테이너 재검증 범위도 확인 전이다.
이전 승인 자원 1개는 이미 사용·정리했으므로 그 실행 ID나 자원을 재사용하지 않는다.
나머지 지침과 신규 자원 승인이 확인되면 필요한 테스트 보완 후 C04 및 실패 사례만 별도 실행 ID로 재검증한다.
기존 PASS 6개, P1 및 다른 도메인의 전체 E2E는 이번 단계에서 실행하지 않았다.
