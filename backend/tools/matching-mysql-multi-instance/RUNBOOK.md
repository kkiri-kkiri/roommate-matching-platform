# 실제 MySQL·두 JVM 회귀 검증

저장소 루트의 Windows PowerShell에서 실행한다. Java/Javac 21, Node/npm,
현재 프로젝트 의존성, 실행 중인 Docker Desktop이 필요하다.

```powershell
Set-Location C:\dev\project\roommate-matching-platform
docker version
docker ps

Push-Location backend
.\gradlew.bat test bootJar
if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed' }
Pop-Location

Push-Location frontend
$env:CI = 'true'
npm.cmd test -- --watch=false --runInBand
if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
Pop-Location

node backend/tools/sync-contract-docs.cjs --check
if ($LASTEXITCODE -ne 0) { throw 'Contract check failed' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace check failed' }

# 기존 전용 컨테이너가 없을 때만 최초 생성한다. 기존 컨테이너 교체는 거절한다.
# powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -Provision

# 컴파일만 먼저 확인할 수 있다. DB 접근은 하지 않는다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\matching-mysql-multi-instance\run.ps1 -PrepareOnly
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed' }

# 기존 21개 업무 시나리오 + 측정/정리 검사 2개
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -IncludeMatching
if ($LASTEXITCODE -ne 0) { throw 'Existing MySQL suite failed' }

# 새 실제 HTTP/STOMP + MySQL 검증. 내부에서 서버 두 개를 시작/종료한다.
# JVM 1 = 127.0.0.1:18081, JVM 2 = 127.0.0.1:18082
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\matching-mysql-multi-instance\run.ps1
if ($LASTEXITCODE -ne 0) { throw 'First multi-JVM run failed' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\matching-mysql-multi-instance\run.ps1
if ($LASTEXITCODE -ne 0) { throw 'Repeat multi-JVM run failed' }

$runs = Get-ChildItem backend/build/matching-mysql-multi-instance -Directory -Filter 'run-*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 2
foreach ($run in $runs) {
    Get-Content -LiteralPath (Join-Path $run.FullName 'summary.json') -Encoding UTF8
    Get-Content -LiteralPath (Join-Path $run.FullName 'isolation-cleanup.json') -Encoding UTF8
}
git status --short
```

`-Provision` 직후에는 MySQL이 연결을 받을 때까지 기다린 뒤 검증을 실행한다.
포트가 사용 중이거나 전용 컨테이너의 라벨/이미지/바인딩/UUID/DB 계정이
일치하지 않으면 검증을 거절한다. URL을 임의 변경하거나 안전 검사를 해제하지 않는다.

## 격리와 실행 증거

- `kkiri-survey-mysql-test`, `mysql:8.0.46-oraclelinux9`,
  `kkiri.validation=survey-only`, `127.0.0.1:3311`만 허용한다.
- 컨테이너 메타데이터와 자격증명은 메모리에만 유지한다. `.env`를 읽지 않는다.
- root 연결은 확인된 전용 컨테이너에서 새 `kkiri_matching_mi_<12자리>` DB와
  그 DB만 허용된 `kkiri_kmi_<12자리>` 계정을 생성/회수하는 데만 사용한다.
- 새 빈 DB에서만 Entity 기반 `create-only` DDL과 quota 키의 ascii_bin을 준비한다.
  실제 서버는 `matching-multi-validation`, `validate`, SQL `never`, 시드 `NONE`이다.
  일반 Gradle test의 H2 안전 검사는 변경하지 않는다.
- Java ProcessBuilder로 서로 다른 OS 프로세스를 시작한다. PID, 포트,
  `DATABASE()`, server UUID, JDBC connection ID, 설정을 ready 이벤트에 남긴다.
- 실제 production Controller/Security/Service/Repository를 사용한다. 테스트 API는 추가하지 않는다.
  JDBC wrapper는 SQL 관찰과 명시적 경합 조정에만 사용하며 결과를 바꾸지 않는다.
- AI는 로컬 HTTP stub만 호출한다. 실제 Gateway client의 JSON/HTTP 처리를 그대로 통과한다.
  SMTP는 네트워크 전송 불가능한 대체 빈이며 수집 스케줄은 제거한다.
- `cases.json`: 실행 전 기대 조건, 실제 판정, 실패 위치와 수행 시간.
  `requests.json`: 각 HTTP 포트/상태/오류 코드/지연. 토큰과 본문은 저장하지 않는다.
  `servers.json`: 서버별 ready/HTTP/SQL 실패. SQL 오류는 vendor code/SQLState/유형만 저장한다.
- `cleanup.json`은 서버 종료 후 모든 테이블의 0행 확인 결과다.
  바깥 조정 프로세스가 자신이 생성한 DB/계정만 회수하고
  `isolation-cleanup.json`을 기록한다. 이미 있던 DB를 정리 대상으로 채택하지 않는다.
- 실패 run도 보존한다. `summary.json`의 PASS, 두 PID, 정리 결과를 함께 확인한다.

## 부하와 해석

단일 Windows 호스트, 동일 MySQL 컨테이너, 각 JVM의 Hikari 최대 16 연결에서
설문 2/4/8, 관심 등록 2/8/32, 등록·취소 32, 매칭 16, quota 32,
로그인 24 요청의 시작을 latch로 맞춘다. 장시간 처리량·운영 SLA 검증은 아니다.
lock wait는 3초, 요청 timeout은 35초다. 유발한 lock timeout은 HTTP409와
DB 스냅샷 보존/다음 요청 복구를 확인한다. UNIQUE/FK 실패와 전체 롤백도 확인한다.

AI quota는 검증 중 창 경계의 영향을 없애기 위해 **12회/3600초**로 고정한다.
운영 기본은 12회/60초다. 승인 뒤 계산/동시 용량 실패도 quota를 소비한다.
따라서 32개 요청에서 200+503=12, 429=20, DB request_count=12가 조건이다.
AI 동시 상한은 **JVM당 2**다. stub을 잠시 보류해 두 JVM 합산 4개를 관찰하고
각 JVM의 추가 요청이 503인지 확인한다. 전체 인스턴스 공통 상한 2라고 주장하지 않는다.

STOMP 검사는 실제 WebSocket 연결로 활성 구독의 전달, 탈퇴한 참여자의 기존 구독 차단,
토큰 만료 후 전달 차단, 정지된 계정의 전달/SEND 차단을 검사한다.
각 방의 두 연결은 같은 JVM을 사용하며, 별도 JVM의 REST로 권한을 변경한다.
현재 Simple Broker의 **서로 다른 JVM 사이 메시지 중계**를 검증하거나 구현한 것은 아니다.
