# 기본 DB 병합 리허설 및 최종 적용 절차

이 문서는 리허설 단계의 기록이다. 이후 사용자가 실제 개인 로컬 기본 DB 적용을 승인했고,
[DEMO_LOCAL_APPLY_20261101.md](DEMO_LOCAL_APPLY_20261101.md)의 절차·검증으로 적용을 완료했다.
아래 미적용/개인 설정 미변경 표시는 **리허설 당시**의 상태다.

2026-11-01 KST / MySQL 8.0.44 / Java 21. **기본 `kkirikkiri`에는 적용하지 않았다.**
회원 정책은 기존 33명의 ID·이메일·비밀번호 해시·권한을 유지하는 것으로 확정했다.
IntelliJ 실행 설정, `.env`, 공통 local 설정도 변경하지 않았다.

## 실제 병합 결과

- 읽기 전용 원본: `kkirikkiri`, 최신 50명 원본 `kkirikkiri_demo50_20261101`.
- 실제 병합 대상: `kkirikkiri_demo50_merge_20261101`.
- 적용 전 백업 복원: `kkirikkiri_demo50_restore_20261101`.
- API·브라우저용 병합 후 백업 복원: `kkirikkiri_demo50_runtime_20261101`.
- 최종 SQL 패치 재생: `kkirikkiri_demo50_replay_v4_20261101`. 실패한 초안 파일과 복제본도 보존한다.

| 데이터 | 기존 | 추가 | 변경 | 삭제 | 병합 후 |
|---|---:|---:|---:|---:|---:|
| 회원 | 33 | 21 | 0 | 0 | 54 |
| 프로필 | 33 | 21 | 30 | 0 | 54 |
| 생활 설문 | 30 | 20 | 30 | 0 | 50 |
| 생활 설문 선택 옵션 | 240 | 174 | 0 | 68 | 346 |
| 선호 설문 | 1 | 50 | 0 | 0 | 51 |
| 선호 선택 옵션 | 5 | 250 | 0 | 0 | 255 |
| 조건별 중요도 | 18 | 900 | 0 | 0 | 918 |
| 방향별 매칭 | 30 | 2,450 | 0 | 0 | 2,480 |
| 게시글 | 93 | 0 | 80 | 0 | 93 |
| 댓글·대댓글 | 43 | 160 | 0 | 0 | 203 |
| 게시글 좋아요 | 2 | 200 | 0 | 0 | 202 |
| 댓글 좋아요 | 5 | 80 | 0 | 0 | 85 |
| 익명 작성자 대응 | 8 | 80 | 0 | 0 | 88 |
| 채팅방 | 11 | 8 | 0 | 0 | 19 |
| 채팅 참여 행 | 22 | 16 | 0 | 0 | 38 |
| 채팅 메시지 | 20 | 33 | 0 | 0 | 53 |
| 일정 카테고리 / 설정 | 3 / 3 | 3 / 3 | 0 | 0 | 6 / 6 |
| 일정 | 62 | 12 | 0 | 0 | 74 |
| 신고 | 1 | 3 | 0 | 0 | 4 |

신규 21명은 일반 회원 20명과 별도 전시 관리자 1명이다. 일반 시드 회원은 정확히 50명이다.
일반 31~50번의 실제 회원 ID는 34~53, 전시 관리자는 ID 54다. 기존 ID 33은 일반 USER로 유지했다.
`admin/moderator`와 `user/31`의 시나리오 키를 분리하고 모든 참조를 실제 ID 대응표로 변환했다.
기존 계정의 해시를 전시회 원본으로 덮어쓰지 않았다.

## 변경 출처와 보존

회원 이메일만으로 기존 데이터를 변경할 권한을 인정하지 않는다. 기존 30명의 과거 생성 프로필
(닉네임·전공·학년·MBTI·기숙사·소개·이미지), 생활 설문 전체 필드와 옵션 집합, 계정 상태가
과거 생성 코드 서명과 일치하고 미확인 선호 설문이 없는 경우에만 최신 프로필·설문을 반영한다.
게시글 80개는 과거 제목·본문·작성자·게시판·상태·익명성을 모두 확인했다.
기존 게시글 ID·작성자·작성 시각·기숙사·상태·익명성은 유지하고 제목·본문만 바꿨다.
예를 들어 최신 원본의 `post/자유게시판/1` ID 1은 기본 DB ID 4에 대응한다.

- 기존 33개 계정의 **전체 행**을 보존했다. 기존 인증정보 변경은 0건이다.
- 기존 수동/출처 불명 13개 게시글, 43개 댓글, 모든 좋아요, 11개 방/22개 참여/20개 메시지,
  62개 일정, 1개 신고, 기존 익명 대응, 수동 선호 설문·중요도·매칭을 보존했다.
- 추가 댓글의 부모, 작성자, 좋아요 대상, 신고 대상·처리자, 채팅 출처와 참여자를 ID 대응표로 연결했다.
  채팅 `room_key`와 익명 번호는 기존 서비스 규칙으로 생성했다. 기존 방에 임의로 메시지를 합치지 않았다.
- 기존 데이터의 자동 중복 제거는 하지 않았다. 기존 댓글과 최신 댓글이 같은 글에 함께 존재할 수 있다.
- 현재 데이터에서는 출처 불명/충돌로 제외된 항목은 **0건**이다.
  미래 복제본에서 서명이 다르면 해당 항목과 종속 데이터를 제외하거나 적용을 차단한다.
- 삭제하는 데이터는 출처가 검증된 기존 시드 생활 설문의 **오래된 선택 옵션 68행**뿐이다.
  최신 응답에도 있는 자연키 옵션은 기존 ID로 재사용하고, 더 이상 선택되지 않은 옵션만 제거한다.
  개인 응답을 임의로 정리하지 않는다. 정확한 PK·설문 ID·필드·과거 값은 비공개 패치의
  `.deleted-options.json`에 있으며 적용 전 전체 dump에도 원본이 보존돼 있다.
- 회원·게시글·댓글·좋아요·채팅·일정·신고 삭제는 0건이다.

개인 증거 경로는 `backend/local-data/demo50/kkirikkiri_demo50_merge_20261101/`다.
`merge.json`은 원본·마이그레이션 후·병합 완료의 모든 테이블/PK/전체 열 지문,
`id-map.json`은 실제 대응표, `skipped.json`은 제외 목록이다. 이 파일들은 Git에서 제외한다.

이번 데이터에서 제거할 `profile_survey_option.profile_survey_option_id`는 아래 68개다.
기존 30명 출처 검증 및 최신 옵션과 자연키 비교로 결정했으며 임의 수동 응답은 포함하지 않는다.

```text
6,7,13,14,21,22,24,31,37,38,39,45,46,47,55,62,63,69,70,71,72,78,85,87,
94,95,96,102,103,109,110,117,118,120,127,133,134,135,141,142,143,151,158,
159,165,166,167,168,174,181,183,190,191,192,198,199,205,206,213,214,216,
223,229,230,231,237,238,239
```

## 검증 범위

- 기존 33명 보존과 수동 데이터 보호를 전체 행 지문으로 비교했다.
- 기존 30명과 신규 20명 모두 최신 닉네임·프로필·두 설문을 조회했다.
  중요도 900개와 모든 2,450개 점수를 기존 `RoommateMatchCalculator`로 재검산했다.
  기존 수동 회원의 매칭 30개는 변경하지 않았다. 임의 점수나 AI 호출을 사용하지 않았다.
- MySQL에서 COMPLETE 상태에 같은 Apply를 다시 실행해 데이터 변경 0건을 확인했다.
- 기존 계정 비밀번호를 변경하지 않고 알려진 기존 개발 USER/ADMIN 2개의 실제 로그인을 확인했다.
  신규 일반 회원 20명 모두 실제 `/auth/login`에 성공했다.
- 별도 비공개 자격증명 파일은 없었다. 기존 시드 30명과 수동 회원 1명의 원문 비밀번호는 몰라
  실제 로그인 성공을 주장하지 않는다. 해당 31명도 저장 인증정보·권한은 변경되지 않았다.
- 전시회 실행기가 아닌 **정상 `com.kkirikkiri.BackendApplication` main**으로 runtime 복제본을 실행했다.
  local/NONE/validate/SQL never, 포트 18082, 수집·이미지 정리 비활성, 임시 로컬 JWT 키를 사용했다.
  IntelliJ GUI의 Run 버튼을 자동 클릭한 것은 아니다. 동일 main의 실행·DB validate·실제 HTTP를 검증했다.
- 실제 빌드한 프론트와 headless Chromium에서 신규 회원 로그인 → 룸메이트 목록 →
  게시글 검색/상세/댓글·대댓글 → 채팅과 기존 관리자 로그인까지 확인했다. 런타임 오류 0건이다.
- 공개 룸메이트 50명, 신규 회원의 매칭 49명, 최신 글 80개 제목, 메시지 33개 문구를 API에서 확인했다.
  기존 시드 회원의 채팅 조회는 **검증 서버의 임시 키로 서명한 테스트 세션**을 사용했다.
  이를 기존 시드 비밀번호 로그인 검증으로 간주하지 않는다. 키·토큰을 출력하거나 보관하지 않았다.
- 상세/채팅 조회의 조회수·읽음·인증 제한 기록 등은 runtime 복제본에만 발생했다.
  기준 병합 DB와 원본 DB에서는 조회 테스트를 실행하지 않았다. 검증용 JVM·프론트 서버는 종료했다.
- 화면·HTTP 증거: Git 제외 `backend/build/demo50/merge-browser/`.

H2 통합 테스트는 정상 병합·기존 계정/관계 보존·반복 적용·출처가 변경된 회원의 제외,
후반 채팅 저장 실패 시 핵심 트랜잭션 전체 롤백과 APPLYING 재시도 차단을 검증한다.
핵심 병합과 매칭 서비스의 여러 트랜잭션을 합쳐 단일 트랜잭션이라고 주장하지 않는다.
파일/DB 잠금, 입력 DB 읽기 전용 세션, 잠금 후 baseline 재검사와 APPLYING 영수증을 사용한다.
중단된 병합은 영수증 삭제/재서명으로 재시도하지 않고 적용 전 dump에서 **새 이름의 복제본**을 구성한다.

## 스키마 마이그레이션과 복구

[수동 SQL](migrations/20261101_profile_survey_legacy_columns.sql)로 복제본의 과거
`bug_handling`, `hair_dryer_use` 30행을 `profile_survey_legacy_20261101`에 보관하고
PK·회원 ID·두 값을 원본과 비교한 뒤 두 컬럼을 제거했다. 다른 설문 값과 관계는 그대로였다.
기본 DB의 두 컬럼은 아직 남아 있다. 인덱스/FK/뷰/트리거/루틴/생성 컬럼/CHECK 의존성은 없었다.
보관 테이블을 삭제하지 않고, 기존 코드/API에 과거 필드를 되살리지 않았다.

적용 전 `base-before.sql`을 별도 restore DB로 복원해 **모든 행, 컬럼 정의, 인덱스, FK와 참조 규칙**을
원본과 비교했다. 과거 두 컬럼과 값도 복원됐다. 병합 후 `merged-after.sql`도 별도 runtime DB에
복원해 병합 완료 지문·물리 스키마와 완전히 같음을 확인했다. 각 dump는 SHA-256을 확인했다.
DDL은 암묵 커밋이므로 전체 마이그레이션이 ROLLBACK만으로 복구되지는 않는다.
50명 적용 후 과거 필수 컬럼에 신규 값을 꾸며 넣는 역마이그레이션은 제공하지 않는다.
장애 시 쓰기를 중지한 채 검증된 **적용 전 전체 백업**으로 복원하는 절차를 사용한다.

## 최종 패치·테스트 기록

- 검증 패치: 개인 state의 `reviewed-merge-708964627961413581e99641535d7185.sql`.
  SHA-256: `7977fc06f3d45f846c2e38005cad475e17450d29cce1fb110a9c6ac4c2f1f737`.
  `reviewed-patch-file.txt`가 이 파일을 가리킨다. 새로 내보내면 검증 전에는 최종 패치로 취급하지 않는다.
- 패치 DML: INSERT 4,484 / UPDATE 140 / DELETE 68. 기존 user UPDATE/DELETE 0.
  별도 기본 DB 복제본에 수동 컬럼 마이그레이션 → 패치 적용 → 같은 패치 재실행을 수행했다.
  재실행 결과 `Already complete: 0 DML statements`였고, 모든 행·물리 스키마가 기준 병합 DB와 같았다.
- 초안의 사전 상태/자식 FK 실패는 데이터 적용 전 거절 또는 트랜잭션 롤백됐다.
  원래 33개 계정이 유지되는 실패 복제본과 초안 파일은 삭제하지 않았다.
  `VerifyFailedReplay`로 FK 실패 복제본의 **모든 행·컬럼 정의·인덱스·FK**가 마이그레이션 후
  병합 전 baseline과 같음을 확인했다. 실패 프로시저는 남아 있을 수 있고, AUTO_INCREMENT 소비까지
  되돌린다고 보장하지 않는다. 실패 복제본을 자동 재사용하지 않는다.
  최종 내보내기는 프로시저 변수와 컬럼 이름 충돌을 피하고, 숫자 ID로 정렬하며 부모 댓글을 먼저 삽입한다.
  SQL 실패 위치는 테이블/PK만으로 진단하고 자격증명을 출력하지 않는다.
- `gradlew.bat test bootJar prepareDemoData`: **1,510 tests / 0 failures / 0 errors**, BUILD SUCCESSFUL.
  최종 오프라인 패치 진단 보완도 `prepareDemoData`로 컴파일하고 실제 MySQL 재생 검증을 통과했다.
- 프론트 관련 테스트: **7 suites / 39 tests 통과**. `npm run build` 성공.
  빌드 시 API 주소만 검증 포트로 지정하고 출력은 Git 제외 `backend/build/demo-merge-ui`에 뒀다.
- 계약 스냅샷: 73 endpoints / 75 record DTOs / 30 entities 일치.
  PowerShell 파싱 및 Node 구문 검사 통과. 제품 API 계약과 Entity 변경은 없다.
- bootJar의 정상 main 1개, 오프라인 exhibition 도구 클래스 0개를 확인했다.
  원래 백업 4세트의 파일 16개도 기존 manifest SHA-256과 모두 일치했다.
- 마무리 시점 원본 기본 DB/최신 원본 및 원래 전시회 DB의 전체 행 무변경 검사를 통과했다.
  8080에는 실행 서버가 없었고, 검증 포트 18082/13000도 종료 상태였다.
  IntelliJ 기존 설정은 `BackendApplication` / local / `APP_SEED_MODE=NONE` 그대로다.

## 기본 DB 최종 적용 절차 — 사용자 검토 후에만 실행

현재 PowerShell/Java 병합 도구는 `kkirikkiri` 쓰기를 거절한다. 이 보호 규칙을 해제해서 실행하지 않는다.
최종 적용은 별도의 비공개 검증 SQL 패치로 수행할 수 있게 준비한다.

1. 이 보고의 건수·소유권·삭제 옵션 68행·계정 유지 정책과 ID 대응표를 검토한다.
2. 사용자의 기본 DB 적용 승인 후 IntelliJ 서버와 다른 쓰기 인스턴스·스케줄·채팅 연결을 중지한다.
   이번 리허설에서는 기존 서버를 종료하지 않았다.
3. 그 시점의 `kkirikkiri` 및 최신 원본·출처 파일을 새 경로에 백업하고 체크섬/복원을 확인한다.
   `AuditSources`가 실패하거나 원본에 새 데이터가 있으면 기존 패치를 강행하지 않는다.
   최신 기본 DB의 **새 복제본**에서 병합·백업 복원·패치 재생 검증을 다시 수행한다.
4. 검증된 개인 state의 `reviewed-patch-file.txt`가 가리키는 SQL과 `.sha256`, 대응표를 확인한다.
   이전 초안/실패 파일을 적용하지 않는다. SQL에는 비밀번호 해시가 있으므로 출력하거나 Git에 넣지 않는다.
5. mysql의 DB를 명시적으로 `kkirikkiri`로 선택하고, 같은 세션에서
   `@kkiri_legacy_survey_approval='BACKUP_VERIFIED_WRITERS_STOPPED'` 설정 후 위 수동 컬럼 마이그레이션을 실행한다.
   `--force` 없이 실행하며 오류 시 중지한다. 아직 현재 단계에서는 기본 DB에 이 명령을 실행하지 않았다.
6. 별도 mysql 세션에서 `@kkiri_demo_merge_approval`에 검토한 preview 토큰을 설정한 뒤 **검증된 패치**를 실행한다.
   서버 UUID·DB 이름·물리 스키마·모든 행의 전 상태를 검증하고, FK를 켠 채 SERIALIZABLE 트랜잭션으로
   검증된 ID의 INSERT/UPDATE/68개 옵션 DELETE만 적용한다. 기존 user UPDATE/DELETE는 없다.
   커밋 전 모든 행의 후 상태가 검증된 병합 복제본과 같아야 한다. 완료 상태 재적용은 DML 0건이다.
   프로시저 생성/제거는 DDL이며, DML 실패 시 데이터 롤백과 프로시저 잔존을 구분한다.
7. 전체 계정 54명/시드 50명, 기존 계정 33명 무변경, 참조·최신 문구·점수·보관 30행을 재확인한다.
8. IntelliJ의 기존 `BackendApplication`을 유지한다. 개인 Run 설정의 DB는 `kkirikkiri`, 시드는 NONE이다.
   local 프로필은 공통 파일이 update/SQL always이므로 개인 실행 인자로 아래 값을 명시한다.

   ```text
   --spring.jpa.hibernate.ddl-auto=validate
   --spring.sql.init.mode=never
   --spring.jpa.defer-datasource-initialization=false
   --app.seed.mode=NONE
   --app.calendar.crawl-on-startup=false
   ```

   비공개 DB/JWT/SMTP 설정은 기존 개인 환경을 사용한다. 공통 application-local 파일은 변경하지 않는다.
   정기 04시 수집은 시작 수집 옵션과 별개이므로 적용·복구 시간에는 모든 앱 쓰기를 중지한다.
9. 일반 실행 후 실제 JDBC DB명과 8080 사용 프로세스, 실제 로그인·프론트 목록을 확인하고 쓰기를 재개한다.
   복구가 필요하면 먼저 모든 쓰기를 멈추고 당시의 추가 변경 유무를 확인한 뒤 적용 전 백업을 복원한다.
   무조건 원본을 DROP/import하는 명령은 자동 실행하지 않는다.

## 팀 공유와 재현

개인 DB 없이 재현하는 명령은 [DEMO_DATA.md](DEMO_DATA.md)의 Bootstrap → FreshPreview → FreshApply → Verify다.
MySQL 접속 정보와 새 계정의 초기 비밀번호는 각자가 비공개로 설정한다.
동일 기준일/카탈로그로 닉네임·콘텐츠·설문·중요도·계산 점수를 재현한다.
다른 DB의 숫자 ID·BCrypt salt·개인의 기존 수동 데이터까지 같게 만들지는 않는다.
기존 원본에서 보존한 메타데이터와 새 생성의 기본값도 구분한다. 예를 들어 이번 원본의 글 80개는
모두 비익명이며 병합에서도 그대로 유지했다. 빈 DB 생성은 카탈로그의 익명 시나리오 16개를 포함한다.
본문·닉네임·설문·계산 점수의 재현과 원본 DB 전체 상태의 복제를 동일하다고 보고하지 않는다.
익명성·작성 시각까지 원본 그대로 필요하면 승인된 원본 복제와 해당 출처 검증 경로를 사용한다.
각자의 기존 DB 병합은 각자의 출처 검증과 복제 리허설이 필요하다. 이 기기의 비공개 패치는 공유하지 않는다.

Git 공유 대상은 카탈로그/생성·병합·패치 내보내기 Java 소스, PowerShell 도구, H2 테스트,
프론트 검증 스크립트, 안내와 수동 마이그레이션이다. 비밀·덤프·출처·영수증·화면/로그는 제외한다.
공통 기능 영향은 `SeedDataService`의 기존 생성 값을 순수 함수로 노출한 변경과 opt-in 빌드 준비 태스크다.
기존 시드 생성 동작·API/Entity·인증 정책·프론트 제품 코드·공통 설정은 바꾸지 않았다.
오프라인 도구 클래스는 서버 bootJar에 포함되지 않는다. Git 커밋/푸시는 실행하지 않았다.

복제 병합 재현 명령(모두 격리 DB만 쓰기):

```powershell
cd backend
.\gradlew.bat prepareDemoData
# 비공개 DEMO_DB_USERNAME, DEMO_DB_PASSWORD 설정; 이 기기만 -UseLocalConfiguration 사용 가능
.\tools\demo-merge.ps1 -Action CloneBase -Schema kkirikkiri_demo50_merge_review
.\tools\demo-merge.ps1 -Action Preview -Schema kkirikkiri_demo50_merge_review
.\tools\demo-merge.ps1 -Action Apply -Schema kkirikkiri_demo50_merge_review -ExpectedToken <preview-token>
.\tools\demo-merge.ps1 -Action Verify -Schema kkirikkiri_demo50_merge_review
.\tools\demo-merge.ps1 -Action AuditSources -Schema kkirikkiri_demo50_merge_review
.\tools\demo-merge.ps1 -Action RestoreCheck -Schema kkirikkiri_demo50_merge_review -CopySchema kkirikkiri_demo50_restore_review
.\tools\demo-merge.ps1 -Action ExportPatch -Schema kkirikkiri_demo50_merge_review
.\tools\demo-merge.ps1 -Action ReplayCheck -Schema kkirikkiri_demo50_merge_review -CopySchema kkirikkiri_demo50_replay_review
```

CloneBase의 레거시 마이그레이션 이후 DPAPI 자격증명 저장 등이 실패한 경우에만 Seal을 검토한다.
Seal도 사용하지 않은 복제본·원본 백업 체크섬·원본 대비 전체 상태를 검증하며 재생성하지 않는다.
중간 파일/복제본을 지워서 진행하지 않는다. Windows 새 계정 비밀 파일은 해당 사용자 DPAPI로 보호한다.
구체적인 테스트/패치 재생 결과는 최종 검증 기록에 함께 남긴다.
