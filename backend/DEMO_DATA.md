# 최신 더미데이터 50명 준비 및 기본 DB 전환

2026-11-01 사용자 승인 후 개인 `localhost:3306/kkirikkiri` 적용을 완료했다.
최종 건수·인증정보 보존·8080 API/브라우저·백업·일반 Run 설정은
[실제 기본 DB 적용 보고서](DEMO_LOCAL_APPLY_20261101.md)를 따른다. 공용/운영 DB 적용을 뜻하지 않는다.

일반 서버는 `com.kkirikkiri.BackendApplication`이며 IntelliJ 실행 버튼을 그대로 사용한다.
데이터 준비 명령은 서버 실행기가 아니고 bootJar에도 포함되지 않는다.
기본 `kkirikkiri`와 원본 `kkirikkiri_exhibition_20261031`에는 이 명령으로 쓰지 못한다.
과거 `EXHIBITION_*` 문서는 2026-10-31의 30명 구성 기록으로 보존한다.

## 구성과 보존 범위

- 단일 콘텐츠 원본: `ExhibitionCatalog.java`, `ExhibitionCampusCopy.java`.
- 일반 회원: `user/1`~`user/50`, 이메일 `seed_user01`~`seed_user50`.
- 관리자: `admin/moderator`, 이메일 `exhibition_admin@hknu.ac.kr`. 일반 31번과 별개다.
- 신규 20명: 6개 전공 조합, 4개 학년, 10개 MBTI, 5개 생활 유형, 18개 중요도.
- 실제 기존 30명은 모두 남자, 출생연도 2000~2004, 입학연도 22~25다.
  과거 카탈로그 예시와 실제 DB가 달랐으므로 빈 DB 재현도 실제 값에 맞췄다.
  기존 생활·선호 응답, 소개, 닉네임과 커뮤니티·채팅 문구는 유지한다.
- 기존 댓글·좋아요 참여자 공식 `%30`은 유지한다. `%50`으로 바꾸면 기존 관계가 바뀐다.
- 복제 확장은 기존 870개 매칭을 건드리지 않고 신규 회원이 포함된 1,580개만 추가한다.
  `MatchingRefreshService`로 계산하고 2,450개를 `RoommateMatchCalculator`와 다시 비교한다.

## 공통 준비

MySQL 8.0, JDK 21, PowerShell 및 PATH의 `mysql`, `mysqldump`가 필요하다.
Windows 고정 설치 경로를 쓰지 않는다. `JAVA_HOME` 또는 PATH의 Java 21을 사용한다.
새 로컬 `kkirikkiri_demo50_*` 스키마만 허용하며 `-Port`로 MySQL 포트를 지정할 수 있다.
기존 DB를 덮어쓰거나 자동 삭제하지 않는다.

```powershell
cd backend
.\gradlew.bat prepareDemoData
# DEMO_DB_USERNAME, DEMO_DB_PASSWORD를 각자의 비공개 환경에 설정한다.
# 선택: DEMO_NEW_USER_PASSWORD를 최초 생성 전에 비공개 설정한다.
```

비밀번호 미지정 시 신규 계정별 무작위 값을 사용한다. 기존 계정 비밀번호는 변경하지 않는다.
내용·관계·기준일을 재현하며 DB별 숫자 ID나 BCrypt 해시까지 같게 만들지는 않는다.
예시 토큰 대신 해당 preview의 실제 토큰을 사용한다.

## 기존 최신 30명을 정확히 보존하며 확장

```powershell
# DEMO_SOURCE_USERNAME, DEMO_SOURCE_PASSWORD도 비공개 설정한다.
.\tools\demo-data.ps1 -Action Clone -Schema kkirikkiri_demo50_review -SourceJournal '<source-journal-path>'
.\tools\demo-data.ps1 -Action Preview -Schema kkirikkiri_demo50_review
.\tools\demo-data.ps1 -Action Apply -Schema kkirikkiri_demo50_review -ExpectedToken <preview-token>
.\tools\demo-data.ps1 -Action Verify -Schema kkirikkiri_demo50_review
.\tools\demo-data.ps1 -Action VerifySource -Schema kkirikkiri_demo50_review
```

Clone은 원본 전시회 DB를 일관된 dump로 새 스키마에 복제한다. 소스와 대상은 같은 로컬 host/port다.
원본은 읽기만 하며 기존 백업/journal/manifest/자격증명을 수정하지 않는다.
원본 journal의 DB identity, 회원·문구·작성자를 확인하고 두 DB의 모든 행을 대조한 뒤
대상 DB에 묶인 새 영수증을 생성한다. 원본 journal을 복사하거나 재서명하지 않는다.
새 영수증에서만 과거 관리자 `user/31`을 `admin/moderator`로 매핑한다.
원본 30명의 저장 자격증명은 복제본에도 그대로 보존된다.

이 저장소의 기존 로컬 `.env`를 비공개 접속 정보로 사용할 때만 `-UseLocalConfiguration`을 추가한다.
이 옵션도 DB 쓰기는 새 격리 스키마에만 한다.

영수증은 모든 원본 행의 PK/전체 열 지문을 보존한다. 기존 행 UPDATE/DELETE 및 허용한
8개 테이블 밖의 추가를 거절한다. COMPLETE 재실행은 변경 0건이다.
파일 잠금·DB 잠금·정확한 preview 토큰·계정 잠금 후 재검사·APPLYING 장벽을 사용한다.
신규 계정/설문은 한 트랜잭션, 매칭은 기존 서비스의 독립 트랜잭션들이다.
전체 적용이 단일 트랜잭션은 아니다. 중단 시 영수증 삭제·출처 재등록·자동 재시도를 하지 않는다.
원본 dump에서 새 이름의 격리 DB를 다시 구성하고 재검증한다.

## 팀원의 빈 MySQL에서 동일 콘텐츠 생성

원본 DB나 다른 사람의 출처 파일은 필요 없다.

```powershell
.\tools\demo-data.ps1 -Action Bootstrap -Schema kkirikkiri_demo50_team
.\tools\demo-data.ps1 -Action FreshPreview -Schema kkirikkiri_demo50_team -Anchor 2026-10-31
.\tools\demo-data.ps1 -Action FreshApply -Schema kkirikkiri_demo50_team -Anchor 2026-10-31 -ExpectedToken <preview-token>
.\tools\demo-data.ps1 -Action Verify -Schema kkirikkiri_demo50_team
```

Bootstrap은 없는 격리 DB를 만들고 비어 있는지 확인한 뒤 Entity/기준정보를 초기화한다.
FreshApply는 기존 서비스로 일반 50명/관리자 1명과 커뮤니티·채팅·일정·신고를 만든다.
빈 DB는 카탈로그의 익명 글 16개를 포함한다. 기존 원본은 글 80개가 모두 비익명이므로
복제·병합 경로에서는 그 익명성을 그대로 보존한다. 동일 콘텐츠 재현과 원본 상태 복제를 구분한다.
일반 서버 시작 시 자동 생성·갱신하지 않는다. 과거 `APP_SEED_MODE=CREATE/DELETE`를
최신 시나리오 준비/복구에 사용하지 않는다.
개인 dump·영수증·출처는 `local-data/demo50/<schema>/`, 로그는 `build/demo50/<schema>/`에 있다.
dump에는 계정 자격증명 해시가 포함될 수 있다. 이 파일들과 비밀번호는 Git에 공유하지 않는다.
팀에는 Java/PowerShell 소스·테스트·문서·수동 마이그레이션을 공유한다.

## 기본 DB 적용 전 보존·추가·변경 계획

다음은 기본 DB 복제본에서 검증한 뒤 실제 개인 로컬 기본 DB에 적용한 보존·추가·변경 범위다.
공용/운영 DB에서는 이 개인 적용 기록을 자동 적용 승인으로 사용하지 않는다.

| 대상 | 계획 |
|---|---|
| 기존 회원 | 기본 DB 33명 전원 보존. ID 33은 별도 USER이므로 전시 관리자로 덮어쓰지 않음 |
| 기존 시드 30명 | 이메일·작성자·전체 출처를 검토한 대응표로 연결. 최신 프로필/응답은 검토된 필드만 반영 |
| 인증정보 | 사용자 확정 정책: 기존 33명 ID·이메일·비밀번호 해시·권한 유지. 원본 전시회 해시로 덮어쓰지 않음 |
| 신규 회원 | 일반 20명과 별도 전시 관리자 추가. 자동 증가 ID 및 출처 ID 대응표 사용 |
| 게시글 | 93개 전부 보존. 출처 검증된 시드 80개는 기존 ID를 유지하고 최신 텍스트 반영 |
| 댓글·좋아요·채팅 | 기존 43댓글, 2게시글 좋아요, 5댓글 좋아요, 11방/20메시지 보존. 최신 시나리오는 출처별로 추가·매핑 |
| 일정·신고·기타 | 기존 62일정/1신고와 출처 불명 행 보존. 최신 12일정/3신고 추가·매핑 |
| 매칭 | 기존 30행은 시드끼리의 매칭이 아님. 보존하고 시드 50명 사이 2,450행을 계산 |
| 과거 설문 컬럼 | [수동 보관·제거](migrations/20261101_profile_survey_legacy_columns.md). 값은 보관 테이블에 유지 |

기본 시드 글 80개는 작성자·게시판·제목·본문의 과거 생성 코드 서명과 모두 일치한다.
예: `post/자유게시판/1`은 전시회 ID 1, 기본 DB ID 4다. 회원 ID가 같아도 게시글 ID는 다르다.
댓글 부모·좋아요·채팅 출처·신고·익명 작성자까지 ID 대응표로 연결해야 한다.
기본 시드 글에는 기존 댓글 16개와 3개 글을 출처로 한 채팅방 5개가 있다.
현재 동일 문자열 room_key는 없지만, 출처 재매핑 후 UNIQUE/익명성 규칙을 다시 검사한다.
문구 유사성만으로 중복을 제거하지 않는다.

삭제할 회원·게시글·댓글·채팅 데이터는 없다. 과거 컬럼 두 개는 30행 보관 후 제거한다.
기존 시드 응답을 최신화할 때 더 이상 선택되지 않은 출처 검증 옵션 68행은 제거하고,
재사용 가능한 기존 옵션은 유지한다. 구체적인 삭제 대상과 복구는 아래 병합 리허설 보고서를 따른다.
기본 DB와 합치면 총 커뮤니티·채팅 건수는 전시회 전용 건수보다 커진다.
최종 회원도 시드 50명과 보존 계정을 합쳐 현재 조건에서는 54명이 예상된다.

## 최종 기본 DB 적용 순서 — 개인 로컬에서 완료

1. [기본 DB 병합 리허설 보고서](DEMO_MERGE_VALIDATION.md)의 보존·변경·옵션 68행 삭제와 대응표를 검토한다.
2. 복제본에서 병합·반복 실행·전체 백업 복원·정상 main/API/브라우저 검증을 완료했다.
   별도의 검증 SQL 패치도 다른 기본 DB 복제본에서 재생·재적용·전체 행/물리 스키마 비교를 통과했다.
3. 사용자의 최종 적용 승인 후 쓰기 서버/정기 작업을 중지하고 새 백업을 확보한다.
   원본이 리허설 이후 바뀌었으면 새 격리 복제본에서 다시 검증한다. 기존 패치를 강행하지 않는다.
4. 보고서의 절차에 따라 수동 보관 마이그레이션과 검증 SQL 패치만 기본 DB에 적용한다.
   `demo-merge.ps1`과 Java 준비 도구의 기본 DB 쓰기 거절 규칙은 유지한다.
5. IntelliJ `BackendApplication`, DB `kkirikkiri`, `APP_SEED_MODE=NONE`으로 실행한다.
   시연용 개인 설정은 DDL validate, SQL never, defer false, 시작 수집 false로 둔다.
   정기 04시 수집은 시작 수집 옵션과 별개다. 공통 local 설정은 임의 변경하지 않는다.
6. 실제 JDBC 세션 DB명과 API 50명/최신 문구를 확인한다. 장애 시 쓰기를 멈추고 검토된 복원 절차를 따른다.

실제 검증 결과: [DEMO_DATA_VALIDATION.md](DEMO_DATA_VALIDATION.md).
기본 DB 복제 병합·실제 로그인·화면·복구·최종 적용: [DEMO_MERGE_VALIDATION.md](DEMO_MERGE_VALIDATION.md).
실제 적용 결과와 현재 개인 실행 설정: [DEMO_LOCAL_APPLY_20261101.md](DEMO_LOCAL_APPLY_20261101.md).
