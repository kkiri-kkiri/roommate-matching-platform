# Auth · User · Survey P1 승인 실행 계획

2026-10-23 Asia/Seoul. 현재 브랜치 `test/frontend-backend-auth-user-survey`, 기준 P0 커밋 `f5186d7`.
사용자가 B10~B15 여섯 논리 시나리오와 최대 신규 컨테이너 두 개를 승인했다.
P0 테스트·보고서·결과·원본은 보호 지문으로 유지하며 실행하지 않는다. 공통 실행기/E2E 전용 인프라만 필요한 범위를 확장한다.

| 논리 시나리오 | 독립 브라우저 테스트 | 입력·관찰 |
| --- | ---: | --- |
| B10 | 4 | wrong-code, code-expiry-cooldown, mysql-limit, duplicates |
| B11 | 5 | signup-passwords, required, change-password, reset-password, introduction |
| B12 | 4 | mail-delay-failure, account-response-loss, profile-save, preference-save |
| B13 | 2 | preference-query, profile-query |
| B14 | 6 | remember-email, two-tabs-logout, tab-return, late-get-success, late-get-401, late-patch-success |
| B15 | 2 | nonmember-inactive, invalid-expired-recovery |
| 합계 | 23 | P0 의존성 없음, worker 1, 자동 재시도 0 |

`npm run test:e2e -- --aus-p1`로 이 파일만 선택한다. 자원 생성 전 `--list`에서 23개를 확인한다.
실패 재검증은 `--aus-p1-recheck <첫 실행 ID>`로 최초 results.json의 실패 ID·SHA-256과 정리 완료를 검사한다.
논리 시나리오 PASS는 소속 독립 테스트가 모두 최종 PASS일 때만 부여한다.

실행 중단 복구: 첫 실행의 reporter onEnd/finally가 유실된 경우 원본 results/run을 합성하지 않는다.
마지막 검증 관찰과 DB cleanup이 남은 항목은 ASSERTIONS_COMPLETED로 별도 기록한다.
interruption-cleanup/recovery의 소유 자원 정리와 SHA-256을 검사한 뒤 미완료 항목만 후속 실행한다.
최종 논리 판정에는 복구 근거 사용 여부와 native Playwright 결과 누락을 반드시 표시한다.
두 번째 실행부터는 테스트 종료별 상태를 즉시 JSONL에 보존한다.

인증번호 TTL과 비밀번호 재설정 토큰 TTL은 E2E 제어에서만 8/300초를 허용한다.
기본 300초, 만료 사례만 8초로 서버를 순차 재시작하고 9.2초를 실제 기다린다.
가입 토큰 만료는 P0 B3이므로 재실행하지 않는다. MySQL 검증 실패 상한 5회는 유지하고
E2E에서만 집계/차단 시간을 15초로 설정한다. 16초 후 실제 복구한다.
이메일/재설정 cooldown 60초는 유지하며 제한 행을 사례 중 삭제·갱신하지 않는다.

기존 A/B와 학부/전공 fixture·가입/설문/로그인 helper를 재사용하고 E2E 조건부로 정지/탈퇴 계정을 추가한다.
기존 메모리 메일의 비공개 IPC에 발송 횟수만 조회하는 기능을 보완한다. 토큰·코드·비밀번호·JWT·메일 본문은 근거에 저장하지 않는다.
경계용 비밀번호는 worker와 소유 runner의 메모리에 먼저 등록해 길이와 상관없이 오류·진단에서 마스킹한다.
trace/screenshot/video/실패 DOM snapshot은 비활성이다.

B11의 UI 차단은 실제 쓰기 요청 0건으로 판정한다. change/reset password 73바이트와 소개 201단위는
별도 browser fetch 보조 요청으로 서버 400/DB 보존도 확인하며 UI 경로와 구분한다.
71/72바이트 저장 후 로그인은 해당 경계의 양성 대조이며 P0 재집계가 아니다.
필수값 검사는 전체 공란과 개별 수정 가능한 이메일·비밀번호·확인·닉네임·출생/입학연도·학부/전공 누락이다.
라디오/MBTI의 개별 누락 조합 전수를 수행했다고 주장하지 않는다.

B12는 브라우저 route barrier와 abort로 지연/실패를 주입한다. 서버 오류로 보고하지 않는다.
account-response-loss는 실제 PATCH/DB 커밋 후 응답만 유실시켜 단일 쓰기·초안·수동 재조회 상태를 확인한다.
B13은 GET 하나만 abort하고 정상 계정/다른 설문까지 차단되는지 확인한다. 실패 후 명시적 재시도로 복구한다.
B14는 실제 두 탭의 storage/탭 활성화를 사용한다. 401은 실제 로그인 JWT 만료 후 원래 요청을 전달해 얻는다.
지연된 PATCH는 원래 Principal A에게만 커밋되고 B의 인증/화면/DB를 덮어쓰지 않아야 한다.
B15는 미가입/정지/탈퇴의 동일 안내·토큰/발송 없음, invalid와 expired의 다른 오류 코드,
기존 비밀번호 보존 및 cooldown 뒤 새 토큰 복구를 확인한다.

새 `kkiri-e2e-<12hex>`, 스키마 `kkiri_e2e_<id>`, 계정 `e2e_<id>`만 사용한다.
기존 MySQL 8.0.46 이미지에 `--pull=never`, tmpfs, named volume 없음.
loopback React 13000/Spring 18080/MySQL 13316 및 인증된 임의 제어 포트다.
신규 빈 DB만 준비하고 서버는 validate/SQL never/seed NONE/외부 SMTP·AI·학교·스케줄 비활성이다.
자원 생성 시도는 `build/aus-p1-preparation/resource-ledger.json`에 최대 2개를 강제한다.
두 번째는 첫 실행 소유 자원 정리 확인 뒤에만 허용한다. 엔진 오류·충돌·다운로드 필요 시 중단한다.
기존 보존 자원에는 내부 inspect/DB 접속하지 않으며 기존 프로세스를 종료하지 않는다.

각 실행은 새 ID로 원본 실패와 requests/results/browser/database/server/cleanup 근거를 보존한다.
브라우저 시작 요청·응답·실패와 서버 완료를 구분하고 주입된 응답 유실은 별도 설명한다.
전체 기존 테스트/P0/타 도메인 E2E는 실행하지 않는다. 재현된 담당 프론트 결함만 최소 수정하고 관련 회귀·빌드를 검사한다.
현재 브랜치에서 커밋·Push·PR·Merge하지 않고 최종 결과·자원 감사·Git 변경 목록·권장 커밋 메시지를 보고한다.
