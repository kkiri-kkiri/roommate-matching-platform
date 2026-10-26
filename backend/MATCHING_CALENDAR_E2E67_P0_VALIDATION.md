# Matching · Calendar 6·7단계 P0 재검증 — 환경 차단

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar`,
HEAD `011606cbbf37e5340aea66db48c282cfb94d6350`.
실행 ID `a0ba0c8152c6`. 사용자 승인 범위는 현재 코드의 P0 11개와 신규 컨테이너 최대 1개다.

**최종 상태: BLOCKED. 최초 관리형 실행은 Docker 엔진 검사에서 중단됐고, 사용자 기동 후 엔진 연결은 해결됐다.
현재는 필요한 기존 MySQL 8.0.46 이미지 부재와 Playwright 의존성 미설치로 실행할 수 없다.**
P0 브라우저 테스트·서버·MySQL은 기동하지 않았다. 따라서 이번 보고는 P0 통과나 C04 결함 재현 완료 보고가 아니다.
과거 실행 ID `0dd4e8c47708`의 결과를 복원·추정하거나 이번 실행과 합산하지 않는다.

[최종 결과 JSON](validation/matching-calendar-e2e67-p0-20261026-final.json),
[최초 환경 차단 시점 JSON — 변경 없이 보존](validation/matching-calendar-e2e67-p0-20261026.json)

## P0 11개 판정

| ID | 범위 | 논리 판정 | 실제 Playwright 실행 |
| --- | --- | --- | --- |
| R01 | 전체 목록·페이지 | BLOCKED | NOT RUN |
| R02 | 추천·빈 결과 | BLOCKED | NOT RUN |
| R03 | 필터·칩·초기화 | BLOCKED | NOT RUN |
| R04 | 카드·상세·점수·설문 일치 | BLOCKED | NOT RUN |
| R05 | 고정 AI 분석 endpoint | BLOCKED | NOT RUN |
| R06 | 좋아요·관심 목록 연동 | BLOCKED | NOT RUN |
| C01 | 개인 일정 CRUD | BLOCKED | NOT RUN |
| C02 | 카테고리 설정·삭제 | BLOCKED | NOT RUN |
| C03 | 공개·개인 일정·소유권 | BLOCKED | NOT RUN |
| C04 | 기존 시간 형태의 제목 편집 | BLOCKED | NOT RUN |
| X01 | 실제 인증 만료·계정 전환·격리 | BLOCKED | NOT RUN |

논리 집계는 PASS 0 / FAIL 0 / BLOCKED 11 / NOT RUN 0이다.
11개 모두 공통 선행 환경 때문에 BLOCKED로 판정했고, 실제 브라우저 실행은 11개 모두 NOT RUN이다.
이 둘은 서로 다른 집계다. assertion 실패를 관찰하지 않았으므로 FAIL로 기록하지 않는다.

## 실제 실행과 사전 검사

2026-10-26 10:13:03.612~10:13:04.581 KST에 `frontend`에서
`node e2e/run.cjs --mc-p0`를 실행했다. 종료 코드는 1이다.

샌드박스에서는 Docker 설정 접근이 거절되어 권한 승인 후 재확인했다.
최초 Docker context endpoint는 `npipe:////./pipe/dockerDesktopLinuxEngine`이며,
엔진 호출은 해당 pipe를 찾지 못해 실패했다. 관리형 실행기의 실패 지점도 `Docker engine availability`다.
제품 assertion이나 실행기 선택 로직의 실패로 분류하지 않는다.
Docker Desktop을 자동 기동하면 기존 보존 컨테이너가 자동 시작될 가능성이 있어 기동하지 않았다.
이후 사용자가 Docker를 켰다고 알려 주어 기존 승인 범위에서 사전 검사를 재개했다.
Linux 엔진 연결은 성공했지만 아래 세 이미지 참조가 모두 없었고,
`reference=*mysql*8.0.46*` 필터의 로컬 이미지 목록도 0개였다.
기존 DB·컨테이너·볼륨 목록이나 상태는 조회하지 않았다.

- `mysql:8.0.46-oraclelinux9`
- `mysql:8.0.46`
- 과거 기록의 `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b`

| 검사 | 관찰 결과 |
| --- | --- |
| 현재 브랜치·HEAD | 지정 브랜치·HEAD 유지 |
| Node / Java | v24.15.0 / Java 21.0.12.1 확인 |
| loopback 포트 | 13000/18080/13316 모두 확인 시 바인딩 가능 |
| Docker Linux 엔진 | 최초 pipe 없음 → 사용자 기동 후 linux 연결 성공 |
| 기존 MySQL 8.0.46 이미지 | 지정 태그·다른 정확한 태그·과거 ID 모두 없음. 버전 필터 결과도 0개. 다운로드 없음 |
| 신규 이름 충돌 | 실행기가 해당 단계에 도달하지 않아 미검사. 생성 없음 |
| 프론트 시작 파일 | 기존 `react-scripts/scripts/start.js` 존재 |
| Playwright 패키지 | 프론트 경로에서 `@playwright/test`, `playwright-core` 모두 MODULE_NOT_FOUND |
| 관리형 Chromium | 기본 `AppData/Local/ms-playwright` 캐시 없음. Playwright executablePath 확인·브라우저 실행 미완료 |
| 기존 시스템 브라우저 | 시스템 Chrome 파일 존재. 자동 대체하거나 실행하지 않음 |
| 의존성·이미지·브라우저 다운로드 | 수행하지 않음. 로컬 캐시만 사용하는 offline 의존성 복구는 실패 |

`require.resolve('react-scripts')`의 최초 실패는 package entry 탐색 결과다.
실제 실행기가 사용하는 `react-scripts/scripts/start.js`는 별도로 확인하여 존재함을 기록했다.
이 항목을 React 설치 부재로 단정하지 않는다.

`yarn.cmd install --frozen-lockfile --ignore-scripts --offline`으로 선언된 의존성을 로컬 캐시에서만 복구하려 했다.
샌드박스의 캐시 쓰기 거절 후 승인된 권한으로 재실행했지만, `playwright-1.64.1.tgz`가 캐시에 없어 실패했다.
사후 확인에서도 `@playwright/test`, `playwright-core`, `playwright`는 resolve되지 않았다.
온라인 다운로드로 전환하지 않았고 package.json/잠금 파일도 변경하지 않았다.

P0 catalog와 인자는 기존 `mc-session.cjs`에서 읽었다.
`mc-p0.spec.cjs --project=mc-p0 --no-deps`와 R01~R06/C01~C04/X01만 선택하는 grep이다.
그러나 실제 Playwright `--list`는 실행하지 못했으므로 native 선택 목록 확인 완료로 보고하지 않는다.

별도로 `node --test e2e/mc-safety.test.cjs`를 실행하여 3개 PASS / 0 FAIL, 종료 코드 0을 확인했다.
이는 P0 catalog의 P1/잘못된 selector 거절, 비밀 마스킹, tmpfs/볼륨 guard에 대한 Node 안전 검사다.
P0 브라우저 11개의 PASS로 합산하지 않는다. AUS/CCRA/P1 및 기존 전체 E2E는 실행하지 않았다.

## C04 5종 재현 상태

| 기존 일정 | 제목 편집·PATCH·응답 | 날짜·시간 보존·새 GET·독립 MySQL |
| --- | --- | --- |
| 정상 09:00~10:00 | BLOCKED / NOT RUN | 미관찰 |
| 종일 null/null | BLOCKED / NOT RUN | 미관찰 |
| 시작만 10:00/null | BLOCKED / NOT RUN | 미관찰 |
| 종료만 null/11:00 | BLOCKED / NOT RUN | 미관찰 |
| 비정각 09:07~10:22 | BLOCKED / NOT RUN | 미관찰 |

브라우저를 실행하지 않았으므로 PATCH 0건이라는 UI 검증 결과를 만들지 않았다.
각 사례의 PATCH 수·payload·응답·보존 여부는 JSON에 null로 기록했다.
C04 최초 실패 원본은 아직 확보하지 못했다. 이번 최초 중단 원본은 Docker 환경 오류 로그다.

## HTTP·MySQL 근거

| 관찰 | 건수 | 의미 |
| --- | ---: | --- |
| 실제 Playwright 테스트 시작/완료 | 0 / 0 | 브라우저 기동 전 중단 |
| 브라우저 HTTP 요청/응답/실패 관찰 | 0 / 0 / 0 | 관찰기 미기동 |
| 서버 HTTP 관찰 | 0 | Spring 미기동 |
| 독립 MySQL snapshot | 0 | DB 접속·schema 준비 없음 |

위 0은 관찰 프로세스가 시작되지 않았다는 뜻이다. 정상 UI의 요청 차단이나 DB 보존을 입증하는 수치가 아니다.
`results-c04-original.json`, 나머지 phase 결과, browser/traffic/database/server-http 원본은 생성되지 않았다.
이를 빈 PASS 결과나 합성된 native Playwright 결과로 대신하지 않았다.

## 발견 사항과 수정 범위

- 확인된 프론트 결함: 없음. 브라우저 미실행으로 판정 불가.
- 확인된 백엔드 결함: 없음. 서버/DB 미실행으로 판정 불가.
- 환경 차단: 최초 Docker 엔진 연결 실패는 해결됐다. 현재는 필요한 기존 MySQL 8.0.46 이미지가 없고,
  선언된 Playwright 패키지도 offline 캐시로 복구할 수 없다. 다운로드 없이 사용할 브라우저 검증도 남아 있다.
- 실행기: 실제 종료 코드 1이지만 엔진 선행 조건 실패이며, 실행기 자체 결함으로 확정하지 않았다.
- 미재현 위험: `frontend/src/calendar/CalendarPage.js:59`의 두 시간에 대한 15분 선택지 검증.
  종일·한쪽 시간·비정각 일정 제목 편집과의 충돌 여부는 실제 C04 재현이 필요하다.
- 정책 미확정: 이번 실행에서 새 정책 판단은 하지 않았다. PATCH null·시간 제거·반올림 정책을 추가하지 않았다.

제품 코드 수정 필요 파일은 아직 확정할 수 없다. C04가 재현되면 `CalendarPage.js`의 편집 검증/시간 선택과
해당 회귀 테스트가 우선 조사 후보이며, DTO·Service·schema 변경 근거는 현재 없다.
검증용 코드·fixture·공통 실행기와 제품 코드는 이번 작업에서 수정하지 않았다.

## 신규 자원과 정리

| 항목 | 실제 결과 |
| --- | --- |
| 컨테이너 생성 시도/생성 | 0 / 0 |
| schema / 전용 계정 / 볼륨 생성 | 0 / 0 / 0 |
| 승인된 컨테이너 예산 | 최대 1개 중 미생성 |
| fixture DELETE·0행 감사 | 대상 DB가 없어 수행하지 않음 |
| 컨테이너 삭제 | 대상이 없어 해당 없음 |
| 실행기 소유 프로세스 종료 | run.json의 ownedProcessesStopped=true. Docker CLI 등 실행기 자식 종료 확인이며 서버/브라우저 기동을 의미하지 않음 |
| 기존 보존 DB·컨테이너·볼륨 | 접속·inspect·기동·종료·변경·삭제 없음 |

`run.json`의 fixtureTablesEmpty=false는 DB 정리 실패를 입증하지 않는다. DB 준비 단계에 도달하지 않은 초기값이다.
containerId와 containerRemoved도 생성되지 않았다. 정리 완료처럼 true 값을 채우지 않았다.
이미지·브라우저 다운로드, 기존 프로세스 종료, Docker prune, 외부 AI·학교·SMTP 요청은 없었다.

## 원본 경로와 보존

실행 원본은 `backend/build/full-service-e2e/a0ba0c8152c6/`다.

- `run.json`: 실제 실행 ID·scope·인자·시각·소유 프로세스 종료 상태.
- `source-before.json`, `source-after.json`: 실행 전후 소스 지문. sourceUnchangedDuringRun=true.
- `logs/Docker-engine-availability.log`: 실제 관리형 실행기의 최초 환경 중단 원본.
- `preflight-observations.json`: 추가 사전 검사 및 안전 검사 결과. native Playwright 원본과 구분한 보조 기록.
- `evidence-manifest.json`: 위 파일과 별도 결과 JSON의 크기·SHA-256.
- `preflight-after-engine-start.json`: 사용자 Docker 기동 후 이미지 확인·offline 의존성 복구 결과의 보조 관찰.
- `evidence-manifest-final.json`: 추가 관찰 및 최종 JSON의 크기·SHA-256. 최초 manifest도 변경 없이 보존.

Git 추적 대상 결과는 이 보고서와 최초/최종 결과 JSON이다.
build 하위 원본은 Git 제외 경로이므로 해당 폴더를 보존해야 한다. 원본 파일은 덮어쓰거나 삭제하지 않았다.

기존 P1 준비안 `MATCHING_CALENDAR_E2E67_P1_PLAN.md`의 작업 전후 SHA-256은
`708222d044925c3560768d0287b8a4d9374152d7c3367591a6d182a1baa94361`로 동일하다.
기존 P0 계획·자료와 타 도메인 결과도 수정하지 않았다. 새 브랜치·commit·Push·PR·Merge는 없다.

## 재개 조건과 남은 승인 범위

Docker Linux 엔진은 현재 준비됐다. 다음은 허용된 기존 MySQL 8.0.46 이미지가 있는 환경이 필요하다.
현재 없는 이미지를 내려받거나 다른 MySQL 버전으로 대체하지 않았다.
선언된 Playwright 1.64.1 의존성과 다운로드 없이 사용할 수 있는 브라우저도 확인해야 한다.
그 뒤 native `--list`의 11개 선택, 포트, 기존 MySQL 이미지 ID와 정확한 신규 이름 충돌 검사를 다시 통과해야 한다.

아직 컨테이너를 만들지 않았으므로 기존 승인 범위의 신규 1개는 사용하지 않았다.
환경이 준비되면 같은 승인 범위에서 C04 우선 → R01~R06/C01~C03/X01 순서로 실행하고,
최초 실패·나머지 결과·실제 HTTP·독립 MySQL 근거와 소유 자원 정리를 새 실행 ID에 보존한다.
이 환경 차단 실행과 후속 실제 실행은 별도 기록으로 남긴다.
제품 결함 수정이나 두 번째 신규 컨테이너가 필요하면 사용자와 별도로 결정한다. P1은 계속 보류한다.
