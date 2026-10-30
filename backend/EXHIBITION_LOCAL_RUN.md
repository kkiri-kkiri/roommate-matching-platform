# 전시회 전용 로컬 DB 실행 및 실제 적용 결과

현재 닉네임·대화 문구 및 기존 ID를 보존하는 갱신 절차/결과는
[EXHIBITION_TEXT_REFRESH.md](EXHIBITION_TEXT_REFRESH.md)를 따른다. 아래 최초 적용 기록의 이전 닉네임은 이력이다.

2026-10-31 KST. 현재 시연 환경은 로컬 MySQL 8.0.44의 `kkirikkiri_exhibition_20261031`이다.
기존 `kkirikkiri`를 초기화·삭제·복사하지 않았다. `.env`, Docker, MySQL 서버 설정을 변경하지 않았다.

## 현재 실행 방법

전시회 백엔드는 8080, 기존 React 개발 서버는 3000이다.
현재 백엔드는 이미 실행 중이다. 먼저 Status를 확인하고, 중지 상태일 때만 Start한다.
일반 IDE 실행과 전시회 실행을 동시에 켜지 않는다. 같은 8080 포트를 사용한다.

```powershell
cd C:\dev\roommate-matching-platform\backend
.\tools\exhibition-local.ps1 -Action Status
# 중지 상태일 때:
.\tools\exhibition-local.ps1 -Action Start
# 중지:
.\tools\exhibition-local.ps1 -Action Stop
```

Start는 local / APP_SEED_MODE=NONE / ddl-auto=validate / SQL never를 고정한다.
Bootstrap만 신규 빈 전시회 DB에서 CREATE/update/always를 사용했다. Bootstrap은 재실행하지 않는다.
서버는 `backend-exhibition-20261031` 이름과 전용 계정/출처 경로를 사용한다.
전용 계정 `kkiri_exh_20261031`@`127.0.0.1`은 전시회 schema에만 권한이 있다.
기존 DB SELECT 거부를 실제 검증했으며 기존 DB로 잘못 연결되는 쓰기를 권한으로 차단했다.
Start가 포트 점유/기록된 기존 서버를 발견하면 종료하거나 덮어쓰지 않고 거절한다.

프론트엔드는 변경하지 않았다. 기존 실행 중인 http://localhost:3000 을 사용한다.
중지했다면 별도 터미널에서 다음을 실행한다.

```powershell
cd C:\dev\roommate-matching-platform\frontend
$env:REACT_APP_API_BASE_URL = 'http://localhost:8080/api'
npm start
```

기존 로그인은 로그아웃한 뒤 다시 로그인한다. 전시회는 기존 DB와 다른 JWT 키를 사용한다.
일반 IDE 실행은 여전히 기존 `.env`의 `kkirikkiri`를 사용하므로 전시회에는 위 전용 실행기를 쓴다.
전시회 서버를 멈추고 IDE의 기존 실행 구성을 사용하면 기존 DB로 돌아간다.
기존 DB에서 기존 개발 시드를 다시 CREATE할 필요는 없다.

## 시연 로그인

일반 계정은 `seed_user01@hknu.ac.kr`부터 `seed_user30@hknu.ac.kr`까지다.
로그인 화면은 도메인이 붙어 있으므로 `seed_user01`처럼 아이디 부분만 입력하고 '다음'을 누른다.
관리자 화면은 `exhibition_admin@hknu.ac.kr` 계정을 사용한다.
이번 새 DB의 일반 시드와 전시 관리자는 같은 임의 생성 시연 비밀번호를 사용한다.
실제 비밀번호는 소스·Git·보고서에 넣지 않았다.

비밀번호는 현재 Windows 사용자에게 귀속된 DPAPI 파일에 암호화했다.
같은 Windows 사용자 세션에서 아래 명령으로 클립보드에 복사한 뒤 로그인 폼에 붙여넣는다.
콘솔에는 출력하지 않는다. 다른 Windows 계정/장치에서는 이 파일을 복사해도 복호화할 수 없다.

```powershell
$exhibitionCredentials = Import-Clixml 'C:\dev\roommate-matching-platform\backend\local-data\exhibition-20261031\credentials.clixml'
$exhibitionCredentials.Demo.GetNetworkCredential().Password | Set-Clipboard
```

기본 시연 동선:

1. seed_user01(걍집가고싶다)로 로그인 → `/profile`: 변경된 프로필·내 설문·선호 룸메 설문.
2. `/roommates`: 말차라떼 등 다양한 닉네임, 전공·생활관·매칭 점수.
3. `/community`: 네 게시판의 글·댓글·대댓글·좋아요. `/community/posts/1`에서 익명 번호 확인.
4. `/chat`: 말차라떼와 룸메이트 문의 대화. 읽음 표시는 실제 서비스 정책대로 바뀐다.
5. `/calendar`: 2026-10-31 전후 개인 일정. 다른 시드의 개인 일정은 그 사용자 로그인으로 확인.
6. 관리자 계정 로그인 → `/admin`: 신고 대기·완료·반려 각 1개.

seed_user07(아침못먹음), seed_user19(실습끝)도 개인 일정과 룸메이트 채팅이 있다.
seed_user04(냥냥펀치)의 커뮤니티 채팅에서는 익명 댓글 작성자 비노출을 확인할 수 있다.

## 실제 수행 순서와 건수

1. 같은 로컬 MySQL에 새 빈 schema와 범위를 제한한 계정 생성. 기존 DB 접근 거부 확인.
2. 기존 local 초기화 기능으로 Entity 테이블·data-common.sql 기준정보 구성.
3. 기존 SeedDataService로 새 회원 30명·설문 30개·원문 게시글 80개 생성.
   원본 SeedUserManifest가 트랜잭션 커밋 후 정상 출처 항목 30개를 생성했다.
   기본 DevDataInitializer의 가상 개발 계정 2명도 새로 생성됐다.
4. 초기화 서버 중지 및 적용 전 DB/출처 백업.
5. 공식 preview: UPDATE 110(회원30/글80), CREATE 27(관리자1/채팅8/카테고리3/일정12/신고3), SKIP 0.
   30개 회원 출처 지문 및 80개 글의 작성자/원문/무관계 조건을 통과했다.
6. 기존 백엔드가 유휴 상태임을 확인하고 중지, 기존 DB의 전체 데이터 덤프 지문 기록.
7. 검토 토큰을 지정한 공식 apply --reports 성공. 원래 시드 회원/글 ID를 유지해 갱신했다.
8. 일반 서버 재개 전 반복 preview: 137개 전부 KEEP, CREATE/UPDATE/SKIP 0. journal=COMPLETE.
9. 완료 DB/출처 백업, 기존 DB 지문 재비교, 전시회 backend를 8080/NONE/validate/never로 시작.
10. 정상 비밀번호 로그인, 실제 API와 기존 프론트 브라우저 검증.

| 데이터 | 실제 최종 건수 |
| --- | ---: |
| 회원 / 프로필 | 33 / 33 (일반 시드30 + 기본 개발2 + 전시 관리자1) |
| 내 설문 / 선호 설문 | 30 / 30 |
| 조건 중요도 | 540 |
| 게시글 | 80 (자유/나눔/택배/민원 각20, 과거 [SEED] 제목 0) |
| 댓글·대댓글 | 160 |
| 게시글 좋아요 / 댓글 좋아요 | 200 / 80 |
| 채팅방 / 참여 행 / 메시지 | 8 / 16 / 33 |
| 개인 카테고리 / 일정 | 3 / 12 (USER 출처만 존재) |
| 신고 | 3 (PENDING/COMPLETED/REJECTED 각1, 제재 없음) |
| 시드 간 방향별 매칭 | 870, 실제 계산 점수 13~100 |

댓글/메시지의 부모가 없는 행 0. 서로 다른 시드 닉네임 30개, 생활관 4개, 전공 6개를 확인했다.
기존 생성기가 만든 회원을 공식 도구로 갱신했으므로 30명의 기존 성별·출생·입학연도는 유지된다.
기존 시드 기본값이 남성이므로 이 DB의 일반 시드 성별도 모두 남성이다. 성별 수정 정책을 우회하지 않았다.

## 기존 DB 보존과 검증 결과

기존 `kkirikkiri`는 회원33/게시글93/채팅방11/메시지20으로 유지된다.
기존 백엔드 중지 후와 새 DB 적용 후, 전체 테이블 데이터를 읽기 전용 mysqldump로 메모리에서 비교했다.
원문 덤프를 파일/로그에 남기지 않고 SHA-256만 기록했다.

`3df53b4e1954e98b58aedbbefe98c6efb59505b80dd21724f8cf96108962d617`

전후 전체 지문과 건수가 같았다. 기존 출처 파일을 위조하거나 DB 간 회원·게시글을 복사하지 않았다.

| 검증 | 결과 |
| --- | --- |
| gradlew.bat test bootJar prepareExhibition | 성공, 9분28초 |
| 전체 H2 테스트 | 88개 클래스/1,504개, 실패·오류·건너뜀 0 |
| 신규 시드 암호 옵션 | 실제 SignupRequest 제약 재사용, 미지정 시 UUID 유지, 기존 계정 무변경·prod/test 미등록 테스트 통과 |
| MySQL 실제 apply / 완료 상태 | 성공 / COMPLETE |
| 오프라인 반복 preview | 137 KEEP, 중복 생성 없음 |
| 실제 API | 169개 요청 성공. 시드30명 정상 로그인, 프로필/설문/선호, 게시글/익명 댓글, 점수/추천, 채팅/읽음, 일정/신고 |
| API 권한 | 일반 회원의 관리자 조회 및 비참여자의 채팅 조회 403 확인 |
| 실제 프론트 | headless Chromium에서 폼 로그인, 프로필, 룸메이트, 게시글/익명 댓글, 채팅 메시지, 캘린더 표시 성공 |
| 계약 문서 스냅샷 / diff check | 73 endpoints/75 DTO/30 entities 일치 / 통과 |

브라우저 검증은 조회와 실제 읽음 처리만 사용하며 메시지/글을 임의로 추가하지 않았다.
UI의 읽음 처리 이후 채팅 지문이 달라져 후속 preview가 해당 채팅을 SKIP하는 것은 보존 정책에 따른 정상 동작이다.
출처 파일을 현재 읽음 상태에 맞춰 임의로 다시 쓰지 않는다.

증거는 Git 제외 경로 `backend/build/exhibition-live/`에 있다:
`database-verification.txt`, `api-verification.json`, `post-apply-preview.log`,
`original-before.txt`, `original-after-apply.txt`, `browser/result.json` 및 화면 PNG 5개.
전체 테스트 로그는 `backend/build/exhibition-live-full-tests.log`다.

## 백업과 복구

현재 상태/출처/자격증명은 `backend/local-data/exhibition-20261031/`에 분리했다.
`seed-users.properties`, `exhibition.json`, DPAPI `credentials.clixml`을 다른 DB의 출처로 사용하지 않는다.
이 디렉터리와 덤프는 Git에 넣지 않는다. `.env`는 계속 기존 DB용이다.

적용 전 백업: `local-data/exhibition-20261031/backups/20261031-005839/`.
완료 직후·UI 사용 전 복구 기준 백업: `local-data/exhibition-20261031/backups/20261031-010311/`.
덤프는 전시회 DB만 포함하며 USE/CREATE DATABASE를 넣지 않는 단일 schema 덤프다.

새 백업은 서버를 중지하고 DB와 출처 파일을 같은 시점으로 보관한다.

```powershell
cd C:\dev\roommate-matching-platform\backend
.\tools\exhibition-local.ps1 -Action Stop
.\tools\exhibition-local.ps1 -Action Backup
.\tools\exhibition-local.ps1 -Action Start
```

Backup은 mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers --events를
전용 계정으로 실행하고 출처/DPAPI 파일 및 SHA-256 목록을 함께 보관한다.
DPAPI 파일까지 복구하려면 같은 Windows 사용자/장치가 필요하다.

다음은 **향후 복구가 필요할 때만** 사용할 명령이다. 이번 작업에서 복원을 실행하지 않았다.
복구는 같은 MySQL 서버 UUID와 같은 전시회 schema에 한정한다. 다른 DB/서버로 journal을 복사하지 않는다.
정비 시간에만 수행하고 이후 생긴 시연 기록이 사라지는 것을 먼저 확인한다.

```powershell
cd C:\dev\roommate-matching-platform\backend
.\tools\exhibition-local.ps1 -Action Stop
$restoreDirectory = 'C:\dev\roommate-matching-platform\backend\local-data\exhibition-20261031\backups\20261031-010311'
$restoreCredentials = Import-Clixml (Join-Path $restoreDirectory 'credentials.clixml')
$restoreSql = (Join-Path $restoreDirectory 'exhibition.sql').Replace('\','/')
$env:MYSQL_PWD = $restoreCredentials.Database.GetNetworkCredential().Password
try {
    & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' --protocol=TCP --host=127.0.0.1 --port=3306 '--user=kkiri_exh_20261031' '--database=kkirikkiri_exhibition_20261031' "--execute=source $restoreSql"
    if ($LASTEXITCODE -ne 0) { throw '복구 실패: 서버 재개 중단' }
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
$restoreTarget = 'C:\dev\roommate-matching-platform\backend\local-data\exhibition-20261031'
foreach ($name in @('seed-users.properties','exhibition.json','credentials.clixml')) {
    Copy-Item -LiteralPath (Join-Path $restoreDirectory $name) -Destination (Join-Path $restoreTarget $name)
}
.\tools\exhibition-local.ps1 -Action Verify
.\tools\exhibition-local.ps1 -Action Preview
# 백업/출처 일치와 검증 결과를 확인한 뒤:
.\tools\exhibition-local.ps1 -Action Start
```

복구 전 `hashes.json`과 백업 파일의 SHA-256을 비교한다. 적용 전 백업에는 journal이 없었으므로
그 백업을 복구한다면 당시 '미존재' 상태도 복구해야 한다. 위 예시는 완료 직후 백업용이다.
전용 계정은 기존 kkirikkiri에 권한이 없으므로 이 복구 명령으로 기존 DB를 덮어쓸 수 없다.
실제 복원 리허설, production 배포, 학교 수집·SMTP·AI 외부 호출은 이번 검증에 포함하지 않는다.

## 재검증과 변경 파일

```powershell
.\tools\exhibition-local.ps1 -Action Verify
.\tools\exhibition-local.ps1 -Action ApiCheck
.\tools\exhibition-local.ps1 -Action BrowserCheck
```

ApiCheck/BrowserCheck는 기록된 전시회 프로세스·시작 시각·8080 포트 소유권을 먼저 검사한다.
로그인 토큰/암호와 원본 응답은 저장하지 않는다. BrowserCheck는 설치된 Playwright Chromium을 사용하고 다운로드하지 않는다.
실행기에는 DELETE/초기화 기능이 없으며 Create/Bootstrap은 기존 자원이나 이미 사용한 출처 경로를 재사용하지 않는다.
데이터 변경은 기존 전시회 CLI의 preview/apply와 검토 토큰을 통해서만 수행한다.

이번 추가/변경 파일:
- `global/init/SeedCreationPassword.java`, `SeedDataService.java`: 신규 local 시드의 명시적 비밀번호 옵션.
- `SeedCreationPasswordTest.java`, `SeedDataSafetyTest.java`: 가입 제약/새 계정 한정/기존 출처 보호 회귀.
- `tools/exhibition-local.ps1`, `exhibition-counts.sql`: 분리된 실행 환경·계정·조회·백업·공식 CLI 호출.
- `tools/exhibition-api-check.cjs`, `exhibition-browser-check.cjs`: 실제 로그인/API/기존 UI 검증.
- `EXHIBITION_DATA.md`, `EXHIBITION_VALIDATION.md`, 이 문서: 후속 실제 적용 결과와 운영 절차.

프론트엔드, Controller/DTO/Entity/API, 기존 DB 스키마와 Docker 설정은 변경하지 않았다.
