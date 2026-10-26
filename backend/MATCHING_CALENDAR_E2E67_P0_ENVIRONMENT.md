# Matching · Calendar P0 실행 환경 설치·사전 검사

2026-10-26 Asia/Seoul. 브랜치 `test/frontend-backend-maching-calendar`,
HEAD `011606cbbf37e5340aea66db48c282cfb94d6350`.
설치 근거 ID `20261026-3cb8839a`.

**승인된 설치와 사전 검사 6개 항목을 모두 완료했다. 컨테이너 생성과 실제 P0 브라우저 테스트는 실행하지 않았다.**
이 문서는 새로운 환경 준비 결과다. 기존 P0 BLOCKED 보고·원본과 P1 준비안은 변경하지 않았다.

[설치·검사 결과 JSON](validation/matching-calendar-e2e67-p0-environment-20261026.json)

## 완료 조건

| 항목 | 결과 | 확인 근거 |
| --- | --- | --- |
| Docker Linux 엔진 | PASS | `docker info --format '{{.OSType}}'` → linux |
| 정확한 MySQL 이미지 | PASS | Docker Hub 공식 `mysql:8.0.46-oraclelinux9` pull 성공, linux/amd64, MYSQL_VERSION=8.0.46-1.el9 |
| Playwright 버전·로딩 | PASS | @playwright/test / playwright / playwright-core 모두 1.64.1. test API와 chromium API 정상 로딩 |
| Chromium 실행 파일 | PASS | revision 1248 설치, Chrome 실행 파일 존재 및 Windows ProductVersion=156.0.8078.4 |
| React/Spring/MySQL 포트 | PASS | 127.0.0.1:13000/18080/13316 모두 확인 시 바인딩 가능 |
| 실제 P0 선택 목록 | PASS | native Playwright --list 성공, mc-p0 project의 11개/1개 파일만 선택, --no-deps |

이미지 ID와 공식 repository digest:

```text
Image ID: sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b
Repo digest: mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b
```

패키지 설치 경로는 `frontend/node_modules/@playwright/test`, `frontend/node_modules/playwright`,
`frontend/node_modules/playwright-core`다. 각 package.json의 버전과 실제 resolve 경로는 결과 JSON에 기록했다.
Node v24.15.0, Yarn 1.22.22, Java 21.0.12.1도 확인했다.

실제 관리형 설정이 사용하는 Chromium 경로:

```text
C:\Users\sneij\AppData\Local\ms-playwright\chromium-1248\chrome-win64\chrome.exe
```

해당 실행 파일의 ProductVersion/FileVersion은 모두 `156.0.8078.4`이며
설치된 Playwright browsers.json의 browserVersion과 일치한다.
Chromium Headless Shell 1248도 존재한다. 그 실행 파일에는 Windows 버전 리소스가 없어 버전을 별도로 추정하지 않았다.
브라우저 프로세스를 실행하거나 실제 페이지를 열지는 않았다.

## 수행한 설치

1. `docker pull mysql:8.0.46-oraclelinux9` — 종료 코드 0.
2. frontend에서 `yarn.cmd install --frozen-lockfile --ignore-scripts` — 종료 코드 0, 44.21초.
3. frontend에서 설치된 `node node_modules/playwright/cli.js install chromium` — 종료 코드 0.

Chromium 설치에는 이 버전 CLI가 요구하는 Headless Shell 1248, FFmpeg 1013, Winldd 1007도 포함됐다.
Firefox/WebKit이나 다른 Playwright 버전으로 대체하지 않았다.
`PLAYWRIGHT_SKIP_BROWSER_GC=1`로 기존 브라우저 캐시 자동 삭제를 비활성화했다.

Yarn은 기존 package-lock.json 공존 경고와 Babel 플러그인/TypeScript peer dependency 경고를 출력했다.
설치는 성공했으며 경고 해소를 위한 패키지 추가·버전 변경·잠금 파일 삭제는 하지 않았다.
`--ignore-scripts`에 따른 lifecycle script 생략도 로그에 남았다.
강제 재설치·캐시 전체 삭제는 수행하지 않았다.

## 실제 Playwright 선택 검사

기존 `mc-session.cjs`의 `selection(CATALOG)`를 재사용했다.
정리된 자식 환경에 `E2E_MC_P0=1`만 지정하고, 프로젝트에 설치된 CLI로 다음 인자를 실행했다.

```text
test mc-p0.spec.cjs --project=mc-p0 --no-deps
--grep (R01|R02|R03|R04|R05|R06|C01|C02|C03|C04|X01) P0:
--list --reporter=list --output=<이번 설치 근거 디렉터리>/list-output
```

실제 목록 순서는 `C04 → R01 → R02 → R03 → R04 → R05 → R06 → C01 → C02 → C03 → X01`이며,
출력은 **Total: 11 tests in 1 file**이다. 기존 `verifyListing` 검증도 통과했다.
P1/AUS/CCRA/login suite나 기존 전체 E2E는 선택하지 않았다.

`--list`만 사용했으므로 test body, 자동 DB fixture, 브라우저 기동은 실행하지 않았다.
reporter는 list로 한정하고 output 경로도 이번 신규 근거 디렉터리로 지정해 기존 결과를 건드리지 않았다.
컨테이너를 생성하는 관리형 실행기 main은 호출하지 않았다.

## 보호 확인과 원본

작업 전후 **670개 파일의 SHA-256이 모두 일치**한다.
여기에는 기존 소스·공통 E2E 코드·P0/P1 문서/JSON·이전 실행 원본이 포함된다.
package.json과 yarn.lock의 작업 전후 SHA-256도 각각 다음 값으로 동일하다.

| 파일 | SHA-256 |
| --- | --- |
| frontend/package.json | 0b5de5f74eab34893034d6a3a2f4a358e2ae5c255a0ee821c8542490c3ff90af |
| frontend/yarn.lock | a41c89f2b423e41f8e7dd1661602fe48a64f007d77726b6995f86c447b07feb9 |

브랜치·HEAD도 유지했다. 다른 팀원 코드와 기존 package-lock.json 역시 변경하지 않았다.
이번 새 파일은 이 환경 보고서와 환경 결과 JSON이며, 설치 로그/검사 원본은 다음 Git 제외 경로에 보존했다.

`backend/build/mc-p0-environment/20261026-3cb8839a/`

- `mysql-image-pull.log`, `yarn-install.log`, `chromium-install.log`
- `image-verification.json`, `package-verification.json`, `browser-verification.json`
- `playwright-list.txt`, `playwright-list-stderr.txt`, `selection-verification.json`, `port-verification.json`
- `protection-before.json`, `protection-after.json`, `evidence-manifest.json`

이미지 다운로드·설치 메타데이터 외에 기존 컨테이너·DB·볼륨에는 접근하지 않았다.
신규 컨테이너/schema/계정/볼륨 생성 0개, 마이그레이션 0회, 실제 브라우저 테스트 0개다.
Docker prune·이미지 전체 정리·Git commit/Push/PR/Merge도 수행하지 않았다.

## 결과의 한계와 다음 단계

요청된 환경 설치·사전 검사까지 완료했으며 실제 P0 결과는 아직 없다.
기존 BLOCKED 보고서를 PASS로 바꾸지 않는다.
Spring 기동·백엔드 빌드·MySQL 실제 실행/JDBC 신원·fixture 준비·브라우저 동작은 이번 범위에서 검증하지 않았다.
포트 상태는 확인 시점의 결과이므로 실제 실행 전 다시 검사해야 한다.

이번 요청의 경계에 따라 여기서 멈춘다. P0 실행 재개 시 기존 승인 조건과 소유 자원 검증을 적용하고,
신규 격리 컨테이너에서 C04 우선 후 나머지 10개를 실행해야 한다. P1은 계속 보류한다.
