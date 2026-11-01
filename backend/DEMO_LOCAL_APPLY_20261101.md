# 개인 로컬 기본 DB 50명 병합 적용 결과

2026-11-01 KST. 사용자 승인 후 **실제 `localhost:3306/kkirikkiri`에 적용 완료**했다.
공용/운영 DB는 접속하거나 수정하지 않았다. 원본 `kkirikkiri_exhibition_20261031`과
최신 50명 원본 `kkirikkiri_demo50_20261101`도 수정·삭제하지 않았다.

## 사전 안전 조건과 실제 적용

- 3306 리스너의 소유 프로세스가 이 PC의 `mysqld`이며 MySQL hostname도 현재 PC와 같음을 확인했다.
  JDBC 서버 UUID/schema identity는 리허설 기록과 일치했다. 접속 주소는 코드에서 loopback:3306으로 고정했다.
- 기존 IntelliJ `.env`의 DB 주소가 localhost/127.0.0.1:3306/kkirikkiri임을 비공개로 검사했다.
  원격 주소/다른 포트/다른 DB라면 적용 도구가 거절한다.
- 프로젝트 백엔드/데이터 도구 실행 프로세스와 다른 DB 세션, 활성 MySQL 이벤트가 없었다.
  중지할 실행 백엔드가 없었으며 다른 프로그램이나 MySQL 자체는 종료하지 않았다.
- 기본 DB 전체 행, 물리 스키마와 AUTO_INCREMENT 상태를 과거 복원본/리허설 기록에 대조했다.
  원본 전시회·최신 50명 DB의 전체 행과 카탈로그·출처 검증도 통과했다.
- 기본 DB/원본 전시회/최신 50명 DB를 **새 파일로 백업하고 각각 새 격리 DB에 복원**했다.
  모든 행·엔진/콜레이션·컬럼·인덱스·FK·CHECK·뷰/트리거/루틴/이벤트·자동 증가 상태가 원본과 같았다.
- 원래 백업/출처/암호화 자격증명 파일은 유지했다. 적용 전 출처 파일을 추가 복사하고 해시로 고정했다.
- 검증된 패치는 변경하지 않았다.
  `reviewed-merge-708964627961413581e99641535d7185.sql`
  SHA-256 `7977fc06f3d45f846c2e38005cad475e17450d29cce1fb110a9c6ac4c2f1f737`.
- 수동 레거시 컬럼 마이그레이션 → 기존 검증 패치 → 같은 패치 재실행 순서로 실제 기본 DB에 실행했다.
  결과는 `Reviewed patch verified and committed` 및 `Already complete: 0 DML statements`였다.
  완료 직후 기본 DB의 모든 행·물리 스키마·자동 증가 상태가 기준 병합 DB와 정확히 일치했다.
  기존 격리용 생성/병합 도구의 기본 DB 쓰기 거절 규칙은 해제하지 않았다.

## 실제 데이터 변경

| 대상 | 적용 전 | 추가 | 변경 | 삭제 | 적용 후 |
|---|---:|---:|---:|---:|---:|
| 회원 | 33 | 21 | 0 | 0 | 54 |
| 프로필 | 33 | 21 | 30 | 0 | 54 |
| 생활 설문 | 30 | 20 | 30 | 0 | 50 |
| 생활 설문 옵션 | 240 | 174 | 0 | 68 | 346 |
| 선호 설문 | 1 | 50 | 0 | 0 | 51 |
| 선호 옵션 | 5 | 250 | 0 | 0 | 255 |
| 조건별 중요도 | 18 | 900 | 0 | 0 | 918 |
| 방향별 매칭 | 30 | 2,450 | 0 | 0 | 2,480 |
| 게시글 | 93 | 0 | 80 | 0 | 93 |
| 댓글·대댓글 | 43 | 160 | 0 | 0 | 203 |
| 게시글 좋아요 | 2 | 200 | 0 | 0 | 202 |
| 댓글 좋아요 | 5 | 80 | 0 | 0 | 85 |
| 익명 작성자 대응 | 8 | 80 | 0 | 0 | 88 |
| 채팅방 | 11 | 8 | 0 | 0 | 19 |
| 채팅 참여 행 | 22 | 16 | 0 | 0 | 38 |
| 메시지 | 20 | 33 | 0 | 0 | 53 |
| 일정 카테고리 / 설정 | 3 / 3 | 3 / 3 | 0 | 0 | 6 / 6 |
| 일정 | 62 | 12 | 0 | 0 | 74 |
| 신고 | 1 | 3 | 0 | 0 | 4 |
| 레거시 값 보관 | 없음 | 30 | 0 | 0 | 30 |

일반 더미 회원은 정확히 **50명**이다. 보존한 다른 계정들과 별도 전시 관리자를 포함한 전체 회원은 54명이다.
신규 일반 회원은 ID 34~53, 전시 관리자는 ID 54다. 기존 회원 ID 33은 USER 그대로다.
`user/31`과 `admin/moderator`를 분리했다. 기존 33명의 **전체 계정 행**을 적용 전후와 화면 검증 후에도 대조했다.
ID·이메일·비밀번호 해시·권한·상태 등을 덮어쓰지 않았다.

검증 패치의 업무 DML은 INSERT 4,484 / UPDATE 140 / DELETE 68이다.
별도 마이그레이션으로 과거 설문 값 30행을 보관했다. 삭제한 데이터는 출처 검증된 시드 설문의
오래된 선택 옵션 68개뿐이며 PK 목록·사유는 [리허설 보고서](DEMO_MERGE_VALIDATION.md)에 있다.
`bug_handling`, `hair_dryer_use`는 PK·회원 ID·원래 값 30행을 보관하고 비교한 뒤 제거했다.
회원·게시글·댓글·좋아요·채팅·일정·신고 삭제는 0건이다.

기존 수동/출처 불명 13개 게시글과 기존 댓글 43개, 모든 기존 좋아요·채팅·일정·신고,
수동 선호/중요도/매칭을 보존했다. 변경 글 80개의 ID·작성자·익명성·기숙사·상태·작성 시각은 유지했다.
신규 데이터의 부모 댓글·작성자·좋아요 대상·채팅 출처/참여자·신고 대상/처리자는 검증 대응표로 연결했다.
출처 문제나 충돌로 제외된 항목은 0건이다.

## 일반 실행·API·화면·테스트

- IntelliJ 기존 `BackendApplication` / local / seed NONE을 유지했다.
  개인 `.idea/workspace.xml`의 해당 Run 설정에만 다음 인자를 추가했다.

  ```text
  --spring.jpa.hibernate.ddl-auto=validate
  --spring.sql.init.mode=never
  --spring.jpa.defer-datasource-initialization=false
  --app.seed.mode=NONE
  --app.calendar.crawl-on-startup=false
  --app.community.image-cleanup.enabled=false
  --logging.level.root=INFO
  --logging.level.org.springframework.web=INFO
  --logging.level.org.springframework.security=INFO
  --logging.level.org.hibernate.SQL=OFF
  --logging.level.org.hibernate.orm.jdbc.bind=OFF
  --spring.jpa.show-sql=false
  ```

  공통 application/local 설정과 비밀 `.env`는 수정하지 않았다. 개인 실행 설정은 Git에 넣지 않는다.
  이미지 정리·시작 수집과 요청/바인딩 디버그 로그를 끈 것은 기존 파일과 인증정보를 보호하기 위한 개인 옵션이다.
- 이 설정을 실제로 읽어 **정상 `com.kkirikkiri.BackendApplication`**을 8080에서 실행했다.
  DB·JWT는 기존 개인 로컬 환경을 사용했다. 별도의 전시회 서버/전시회 main을 사용하지 않았다.
  검증 전용 loopback 바인딩·프론트 origin 13000의 CORS·로그 억제만 해당 검증 프로세스에 추가했다.
  IntelliJ GUI의 Run 버튼을 자동 클릭한 것은 아니며, 저장된 동일 main/프로필/인자로 실행을 검증했다.
- 기존 개발 USER/ADMIN 2개, 신규 일반 회원 **20개 전원**, 신규 관리자 ID 54의 실제 로그인 성공.
- 공개 룸메이트 50명과 최신 닉네임, 신규 회원의 두 설문 completed, 본인 제외 매칭 49명 확인.
- 실제 API에서 최신 게시글 80개 제목과 8개 방의 최신 메시지 33개 문구 확인.
- 빌드한 실제 React 화면과 headless Chromium으로 신규 로그인 → 룸메이트 목록 →
  게시글 검색/상세/댓글·대댓글 → 채팅 → 기존 관리자 로그인을 확인했다. 런타임 오류 0건, API 확인 36건.
- 기존 시드 30명과 수동 회원 1명은 원문 비밀번호가 없어 실제 로그인 성공을 주장하지 않는다.
  비밀번호를 재설정하지 않았다. 채팅 문구 조회는 개인 로컬 키로 서명한 시드 조회 세션을 사용했으며,
  이를 해당 계정의 비밀번호 로그인 검증으로 간주하지 않는다. 키/토큰/비밀번호는 출력·Git 저장하지 않았다.
- 테스트 스크립트의 Windows UTF-8 BOM 읽기를 보완했다. 첫 화면 검증은 API 로그인 전에 중단됐고,
  DB 적용은 재실행하지 않았다. 보완 후 실제 전체 화면 검증을 통과했다.
- 백엔드 `gradlew.bat test bootJar prepareDemoData`: **1,510 tests / 실패 0 / 오류 0**, BUILD SUCCESSFUL.
  테스트 DB는 H2 메모리로 강제됐다. 프론트 관련 **7 suites / 39 tests**, `npm run build` 성공.
  검증 빌드는 `backend/build/demo-local-ui`, API 주소는 localhost 8080의 loopback 주소로 지정했다.
- 계약 스냅샷: 73 endpoints / 75 record DTOs / 30 entities 일치. Node 구문·PowerShell 파싱 검사 통과.
  오프라인 도구는 bootJar에 포함되지 않는다.

화면 검증 뒤 전체 데이터를 다시 비교한 결과 추가 차이는 **auth_limit_bucket 49행 추가/2행 갱신**과
**검증한 시드 게시글 1개의 조회수·updated_at**뿐이었다. 채팅의 저장 값은 바뀌지 않았다.
그 밖의 계정·수동 데이터·콘텐츠·설문·매칭·관계는 적용 직후 체크포인트와 동일했다.
이 차이는 실제 로그인과 상세 조회의 정상 동작이며 임의로 지우거나 되돌리지 않았다.
따라서 패치의 DML 0건 재실행 검증은 이 화면 조회 **이전**에 완료했다.
실제 사용 이후에는 전체 체크포인트 보호 조건 때문에 같은 패치를 무조건 재적용하지 않는다.

검증용 JVM/프론트 서버는 종료했다. 종료 확인 시 8080/13000은 비어 있어 IntelliJ 일반 Run으로 실행할 수 있다.

## 백업과 개인 증거

루트: `backend/local-data/demo50/local-apply-20261101_final01/` (Git 제외).

| 파일 | 용도 |
|---|---|
| `base-before.sql` + `.sha256` | 실제 적용 직전 회원 33명/과거 컬럼을 포함한 전체 기본 DB |
| `exhibition-before.sql` + `.sha256` | 원본 전시회 DB 새 전체 백업 |
| `latest50-before.sql` + `.sha256` | 최신 50명 소스 DB 새 전체 백업 |
| `base-after-before-api.sql` + `.sha256` | 병합·재적용 0건·전체 비교 직후 기본 DB |
| `base-after-runtime.sql` + `.sha256` | 실제 로그인·화면 및 최종 보존 검사 후 기본 DB |
| `source-files/`, `provenance.json` | 원본 출처·암호화 자격증명·영수증·카탈로그·마이그레이션 추가 복사/해시 |
| `intellij-workspace-before.xml` | 개인 Run 설정 변경 전 복사본 |
| `apply.json`, `apply-result.log`, `preflight-*.json`, `restore-*.json`, `after-*.json` | 단계/모든 테이블 건수/보존 및 복원 검증 기록 |

새 복원 검증 DB도 삭제하지 않았다.

```text
kkirikkiri_demo50_final_restore_base_20261101_final01
kkirikkiri_demo50_final_restore_exhibition_20261101_final01
kkirikkiri_demo50_final_restore_latest_20261101_final01
```

기존 리허설 state·ID 대응표·검증 패치·신규 계정의 DPAPI 자격증명은 기존
`backend/local-data/demo50/kkirikkiri_demo50_merge_20261101/`에 유지했다.
화면/HTTP 증거는 `backend/build/demo50/local-browser-local-apply-20261101_final01/`의
`result.json`, `roommates.png`, `community.png`, `chat.png`다. 각 백업의 SHA-256을 확인했다.

## 복구 절차

1. 모든 백엔드/스케줄/채팅 쓰기를 중지하고, 복구 시점의 현재 DB와 보관 테이블도 새 파일로 백업한다.
   실제 사용 이후 새 수동 데이터가 있으면 적용 전 시점으로 단순 회귀하지 않고 먼저 별도 보존한다.
2. 위 `base-before.sql`의 체크섬을 확인하고 **새 이름의 빈 격리 DB**에 복원한다.
   과거 컬럼 두 개·회원 33명·모든 행/컬럼/인덱스/FK·자동 증가 상태를 원본 before 체크포인트에 비교한다.
   이번 작업에서 이 복원과 비교를 실제로 통과했다.
3. 실제 `kkirikkiri` 복구 대상과 데이터 손실 범위를 검토한 뒤, 검증한 before 복원본의 전체 상태로 복구한다.
   기존 54명 스키마 위에 before dump만 겹쳐 import하면 새 보관 테이블 등이 남을 수 있으므로
   **원래 스키마 전체를 정확하게 복원하는 절차**가 필요하다. 원본 DB DROP/초기화를 자동 수행하지 않는다.
   파괴적 교체가 필요하면 당시의 새 데이터와 대상 테이블 목록을 제시하고 별도로 검토한다.
4. DDL은 암묵 커밋이므로 `ROLLBACK`만으로 레거시 컬럼 제거까지 복구됐다고 판단하지 않는다.
   신규 20명의 과거 컬럼 값을 임의 생성하는 역마이그레이션도 실행하지 않는다.
5. 복원 후 normal main을 validate/SQL never/NONE으로 시작해 로그인·수동 데이터·API를 확인한다.

이번 실제 적용에는 오류/범위 밖 DB 변경이 없었으므로 기본 DB 복구는 수행하지 않았다.

## Git 공유 범위

커밋은 실행하지 않았다. 작업 브랜치의 기존 변경도 유지했다. 공유할 파일은 다음과 같다.

- `backend/src/exhibition/java/exhibition/`: 50명 카탈로그, 기존 생성 도구 보완,
  `DemoDataSnapshot`, `DemoDataExpansion`, `DemoDataTool`, `DemoDataMerge`, `DemoMergeRows`,
  `DemoMergeSqlExport`, `DemoMergeTool`, 새 읽기 전용 `DemoLocalVerification`.
- `backend/tools/`: `demo-data.ps1`, `demo-native.ps1`, `demo-merge.ps1`, `demo-merge-check.ps1`,
  새 `apply-demo-local.ps1`, `check-demo-local.ps1`.
- `backend/src/test/java/exhibition/`: 50명/관리자 분리 보완과 `DemoDataMergeIntegrationTest`.
- `frontend/e2e/demo-merge-check.cjs`: 격리 검증 또는 승인/완료 영수증이 있는 개인 로컬 DB만 허용.
- `backend/build.gradle`, `backend/src/main/java/com/kkirikkiri/global/init/SeedDataService.java`
  (기존 생성 값을 순수 함수로 추출한 변경), 관련 DEMO/EXHIBITION/DEPLOYMENT 문서 및 수동 마이그레이션/안내.

개인 로컬 적용 도구는 이번에 승인한 fixed endpoint·서버 identity·리허설·패치 해시·단계 기록에 묶인다.
팀원/운영 DB에 사용할 일반 배포 도구로 간주하지 않는다. 안전 조건 불일치나 APPLYING 단계에서는
강행/자동 재시도하지 않는다. 데이터 준비 도구와 검증 도구는 서버 실행 의존성이 아니다.

Git에서 제외할 것은 `.env`, `.idea/`, `backend/local-data/` 전체, 모든 dump/해시 영수증/검증 패치,
자격증명 `.clixml`/출처 manifest, `backend/build/`와 로그/스크린샷이다. 실제로 ignore되는 것을 확인했다.
공통 설정/API/Entity/제품 프론트 코드는 바꾸지 않았다.
팀원의 빈 MySQL 재현은 [DEMO_DATA.md](DEMO_DATA.md)를 따른다. 원본 보존 익명성과 빈 DB 카탈로그 기본값,
개인 수동 데이터·숫자 ID·BCrypt salt의 차이는 해당 문서에 구분했다.
