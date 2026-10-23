# Matching · Calendar 추가 실제 HTTP 검증

검증 실행: 2026-10-22 (Asia/Seoul). 보고 갱신: 2026-10-23.
**최종 29개 PASS: P0 17개, P1 12개. 애플리케이션 TCP HTTP 212건.**
완료한 검증을 반복하지 않는다. 이 고정 대상의 Provision/P0/P1/역사적 복구 명령은 재실행하지 않는다.

- [최종 보고서](../../MATCHING_CALENDAR_HTTP5_VALIDATION.md)
- [최종 사례·실행별 요청 수·근거 SHA-256·정리 결과](../../validation/matching-calendar-http5-20261022.json)
- [승인 전 NOT RUN 계획 — 당시 상태 보존](../../validation/matching-calendar-http5-plan-20261022.json)

## 승인 범위와 보존된 대상

| 자원 | 실제 대상 |
| --- | --- |
| 컨테이너 / hostname | `kkiri-mc-http5-mysql-20261022` |
| 컨테이너 ID | `9fd6a36a8fbbfc5a297e13571b17c571bf0eca22c53c2063d52ce0629432738f` |
| 볼륨 | `kkiri-mc-http5-data-20261022` |
| DB / 전용 계정 | `kkirimc5http20261022` / `mc5http20261022@%` |
| MySQL | `8.0.46`, `127.0.0.1:13348`, `mysql:8.0.46-oraclelinux9` |
| 이미지 ID | `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b` |
| 서버 UUID | `078257da-ce27-11f1-94ba-eea983ee4696` |
| HTTP | Matching `127.0.0.1:18095`, Calendar `127.0.0.1:18096` |
| 최종 상태 | HTTP JVM 종료, 31개 테이블 0행, 컨테이너 running, 볼륨·DB·계정·DDL 보존 |

처음 두 번의 환경 확인은 Docker Linux 엔진 연결 실패로 중단됐다.
이후 READY와 생성 직전 이름·이미지·포트 검사를 통과한 뒤 지정 자원만 생성했다.
이미지 pull·추가 컨테이너·기존 자원 시작/정지·DROP/TRUNCATE/자원 삭제는 수행하지 않았다.

이 도구는 고정 대상의 역사적 검증을 보존한다. 독립된 새 실행은 새 대상과 충돌 확인·승인이 필요하며,
현재 자원의 일회용 marker를 지우거나 원본 결과를 덮어써 재실행하는 방식으로 사용하지 않는다.

## 실제 실행 순서와 오류 복구

기존 Matching/Calendar 도구의 서버·HttpClient·fixture·정리·assertion을 재사용했다.
서비스·공개 API·스키마 정책·원본 도구는 수정하지 않았다.

1. `Prepare`: 기존 JAR/main class 일치, 실행기 컴파일, 29개 카탈로그 확인. DB/HTTP 접근 없음.
2. `preflight.ps1` 및 `provision.ps1`: 로컬 엔진·새 이름·고정 이미지·포트 재검사 후 승인된 자원만 생성.
3. `P0`: 최초 Matching 서버가 공유 클래스 경로의 Calendar 테스트 설정 자동 스캔 충돌로 시작 실패.
   HTTP 0건·INCOMPLETE와 정리를 보존했다. 진단 2회는 각각 HTTP 0건이며 JVM 종료를 확인했다.
4. `PrepareRecovery`: 테스트 클래스 경로를 `classes/matching`과 `classes/calendar`로 분리했다.
   `ContinueP0`에서 같은 빈 스키마를 validate로 사용해 Matching 8개·Calendar 9개를 완료했다.
5. 실제 P0 결과·오류 코드·124 HTTP·서버 관찰·정리를 읽어 검토했다.
   `report.cjs --review-p0`가 SHA-256으로 고정한 `p0-reviewed.json`을 생성한 뒤 P1을 시작했다.
6. `P1`: Matching 6개 PASS, Calendar 5개 PASS + null 병합의 실행기 시간 형식 기대값 오류 1 FAIL.
   기존 DTO의 HH:mm 계약에 맞추고 전체 DTO/GET/DB 보존 assertion을 보강했다.
   재검증 gate의 PowerShell JSON 배열 전달 오류도 HTTP 전 단계에서 보정했다.
7. `RecoverPatchNull`: 이미 PASS한 28개를 반복하지 않고 실패한 1개만 8 HTTP로 재실행해 PASS했다.
8. `Audit`: 새 DB만 읽기 전용으로 감사해 identity·권한·31개 테이블 0행·동일 DDL·감사 자신만의 연결을 확인했다.
   별도 OS 프로세스/포트 검사와 새 컨테이너·볼륨 조회로 종료·보존 상태를 확인했다.

최초 31개 테이블 DDL 생성 이후 스키마 재생성·DDL 재적용은 없었다.
각 실행이 만든 합성 행만 JVM 종료 후 FK 역순 DELETE했다. 원본 FAIL/INCOMPLETE·실행 marker·이력은 유지했다.

`PrepareRecovery`, `ContinueP0`, `RecoverPatchNull`, `Diagnose`는 위 특정 오류의 역사적 복구 경로다.
완료한 대상에서 다시 사용하지 않는다. 서비스 결함 발견 시 자동 수정하지 않고 재현 결과를 먼저 보고한다.

## 로컬 전송과 실제 외부 서비스 구분

애플리케이션의 실제 Controller·보안 필터·Service·JPA·MySQL을 사용했다.
AI는 원래 `AiGatewayClient`가 loopback stub에 요청하도록 하고 점수·입력 필드·502/503·복구를 확인했다.
실제 Gateway 요청은 0건이다. 로컬 stub 하위 HTTP 4건은 애플리케이션 HTTP 212건에 더하지 않는다.

Calendar는 저장소 HTML을 loopback으로 가져오는 FixtureCrawler가 기존 parser·수집 서비스·이력을 통과한다.
수집 기동은 제어 IPC이며 HTTP API라고 집계하지 않는다. 공개/관리자 조회는 실제 애플리케이션 HTTP다.
학교 홈페이지 요청은 0건이다. HTML 하위 HTTP 4건과 fixture의 기대 일정62개는 실제 외부 수집 성공이 아니다.
SMTP는 네트워크 전송 불가능한 sender이며 실제 SMTP 연결도 0건이다.

기존 Calendar 31개는 원본 근거 부족 상태를 유지한다. 그 전체를 재실행하거나 이번29개와 합산하지 않는다.
재사용한 `cross-server` 사례명은 출처 식별자이며 이번 실행은 단계별 JVM 1개다.
Auth/Survey 만료·입력 경계, 기본 목록·좋아요 경합, 다른 도메인 전체 suite는 반복하지 않았다.

추천 정렬은 60점 동점의 ID 순서와 명시적 DESC 수용을 확인했다.
서로 다른 점수 간 DESC 음성 대조와 복합 필터의 모든 음성 조합은 독립적으로 검증하지 않았다.

## 보존 근거와 재실행 없는 로컬 대조

Git 제외 `backend/build/matching-calendar-http5/`의 주요 근거:

- `P0/matching/`: 최초 HTTP0 서버 시작 실패·정리·최초 DDL.
- `diagnostic-*/`: 두 진단의 구조화된 빈/타입 오류 또는 정상 기동·종료.
- `P0-continue1/matching/`, `P0/calendar/`: 최종 P0 17개·124 HTTP.
- `P1/matching/`, `P1/calendar/`: P1 최초 실행. Calendar FAIL 1개를 수정하지 않고 보존.
- `P1-null-recovery/calendar/`: 최종 단일 사례 PASS·8 HTTP·정리.
- `p0-reviewed.json`: P1 전 검토 시각과 근거 지문.
- `audit-20261022T145812Z.json`, `http-process-audit.json`, `resource-retention.json`: 최종 정리·보존 감사.
- `protected-sources-before/after.json`: 운영 소스·원본 도구·문서·JAR·계획 등284개 파일 지문 동일.
- `history/`, `preflight-*.json`: 최초 BLOCKED·도구 오류·준비 버전의 근거 보존.

새 DB 읽기 전용 Audit를 다시 할 수는 있지만, 완료 보고를 위해 실제 검증을 반복할 필요는 없다.
다음은 로컬 파일·구문 대조만 하는 명령이며 현재의 실행 결과를 다시 만들지 않는다.

```powershell
node --check backend/tools/matching-calendar-http5/prepare.cjs
node --check backend/tools/matching-calendar-http5/review.cjs
node --check backend/tools/matching-calendar-http5/report.cjs
node backend/tools/sync-contract-docs.cjs --check
git diff --check
```

`report.cjs --final`은 로컬 원본·지문을 읽어 최종 JSON을 재생성하며 DB/HTTP에 접근하지 않는다.
`review.cjs`는 승인 전 계획 생성용으로서 실행 시작 후 차단된다. 승인 전 계획을 최신 결과로 덮어쓰지 않는다.

비밀번호·JWT·메일 원문·응답 본문·전체 Docker inspect는 근거로 저장하지 않는다.
root는 새 자원 생성 시 내부 소켓에만 사용했고, 검증은 해당 DB만 허용된 계정으로 수행했다.
기존 보존/운영 DB·자격증명은 읽지 않았다. 커밋 가능한 파일 목록13개는 최종 보고서와 결과 JSON에 있다.
커밋은 수행하지 않았다.
