# 전시회 데이터 말투 갱신 (2026-10-31)

대상은 `kkirikkiri_exhibition_20261031`이다. 기존 `kkirikkiri`에는 쓰지 않는다.
최초 읽기 전용 조회에서 기존 시나리오 137개가 모두 KEEP인 것을 확인했다.
일반 preview의 KEEP은 기록된 카탈로그와 DB 지문이 일치한다는 의미다.
새 문구 적용에는 별도로 검토한 `--refresh-text` 계획을 사용한다.

## 콘텐츠와 생성 경로

- `src/exhibition/java/exhibition/ExhibitionCatalog.java`: 30개 닉네임과 33개 채팅 메시지.
- `ExhibitionCampusCopy.java`: 게시판별 20개씩, 총 80개 제목·본문과 160개 댓글·대댓글.
- 기존 `CommunitySeedDataService.legacyPosts()`는 최초 출처 검증용 원문이다. 변경하지 않는다.
- 새로운 전시회 DB를 구성하는 기존 생성 경로도 같은 카탈로그를 사용한다.
- 닉네임은 기존 DTO의 2~8자, 완성형 한글·영문·숫자 규칙을 따른다.
  예시의 `ㅇㅅㅇ` 같은 자모 닉네임을 허용하도록 공개 계약을 바꾸지 않았다.
- 기존 8개 채팅방 중 6개는 룸메이트 문의, 2개는 게시글·익명 댓글에서 이어진 대화다.
  이 출처와 발신자 순서도 보존하므로 나눔·산책 문의의 맥락을 유지한다.

## 갱신 보호

`TextPreview` / `TextApply`는 기존 전시회 CLI와 원본 SeedUserManifest, ExhibitionJournal을 함께 사용한다.
원본 회원 출처가 없거나 바뀐 경우, 전시회 출처가 없는 경우, 전체 기록 지문이 달라진 경우 SKIP한다.
이메일이나 닉네임만으로 출처를 만들거나 현재 DB를 임의로 새 기준에 편입하지 않는다.
참여자·출처 게시글이 SKIP이면 관련 게시글·채팅도 제외한다.
기존 읽음 상태 변경도 채팅 지문을 바꾸므로 해당 방을 SKIP한다. 이를 자동 재서명하지 않는다.

적용은 정확한 preview 토큰과 기존 오프라인·쓰기 허용 가드를 요구한다.
DB 서버 UUID/schema에 묶인 출처, 출처 파일 잠금, DB GET_LOCK을 그대로 사용한다.
행 잠금 후 같은 계획인지 재검사하고, 기존 값·소유자·부모를 조건으로 UPDATE한다.
사용자 프로필의 nickname, 게시글 title/content, 댓글 content, 메시지 content만 수정한다.
서버용 수정 API가 없는 메시지 내용은 이 별도 오프라인 도구의 바인딩된 SQL로만 갱신한다.
새 API·스키마·일반 서버 자동 갱신은 추가하지 않는다.

INSERT/DELETE, 프로필 전체 저장, 설문 저장, 매칭 재계산을 호출하지 않는다.
ID, 이메일, 비밀번호, 프로필의 나머지 필드, 설문 부모/선택지/가중치, 매칭 점수·관계,
좋아요, 익명 번호, 댓글 부모, 방·멤버·발신자·client_message_id, 읽음 상태, 시각을 보존한다.
수정 허용 텍스트 열을 제외한 전체 행 지문이 트랜잭션 전후 같아야 커밋한다.
DB 수정은 하나의 트랜잭션이다. 오류는 전체 롤백하며 APPLYING 출처는 재시도를 거절한다.
DB 커밋 후 출처 파일 기록 전에 중단된 경우에도 DB/출처 백업을 한 쌍으로 복원해야 한다.

## 실행 순서

저장소 루트에서 수행한다. 재실행 전에 현재 전시회 서버 상태를 먼저 확인한다.

```powershell
cd backend
.\gradlew.bat test bootJar prepareExhibition
.\tools\exhibition-local.ps1 -Action Status
.\tools\exhibition-local.ps1 -Action Stop
.\tools\exhibition-local.ps1 -Action Backup
.\tools\exhibition-local.ps1 -Action TextPreview
# 변경 전 문구, Text catalog의 변경 후 문구, UPDATE/SKIP 사유를 검토한다.
# 출력된 정확한 Preview token을 지정한다.
.\tools\exhibition-local.ps1 -Action TextApply -ExpectedToken '<검토한 64자리 토큰>'
.\tools\exhibition-local.ps1 -Action TextPreview
.\tools\exhibition-local.ps1 -Action Verify
.\tools\exhibition-local.ps1 -Action Start
.\tools\exhibition-local.ps1 -Action ApiCheck
.\tools\exhibition-local.ps1 -Action BrowserCheck
```

정상 첫 계획은 UPDATE 118(회원30/글80/방8), KEEP 19, CREATE/SKIP 0이다.
실제 수정 행은 닉네임30/글80/댓글160/메시지33, 총303개다.
반복 계획은 137 KEEP이며 쓰기를 수행하지 않는다.
UI가 이후 읽음 상태를 바꿨다면 일부 채팅 SKIP은 수동 상태 보존에 따른 결과다.

검토 출력은 Git 제외 `build/exhibition-live/text-preview-latest.log`에 UTF-8로 기록된다.
API/브라우저 검증은 여기의 **컴파일된 Java 카탈로그**를 읽어 기대 문구로 사용한다.
API는 30개 닉네임, 80개 제목·본문, 160개 댓글·부모, 33개 메시지 전체를 비교한다.
브라우저는 실제 폼 로그인 후 프로필·룸메이트, 네 게시판의 제목·본문·댓글·대댓글,
룸메이트 채팅 문구, 캘린더를 확인한다. 토큰 주입이나 요청 모킹은 하지 않는다.

## 실제 적용 증거

적용 전 DB/출처/암호화 자격증명 백업:
`local-data/exhibition-20261031/backups/20261031-013938/`.
`hashes.json`에 각 파일 SHA-256을 보관했다. 자격증명이나 DB 덤프는 Git에 넣지 않는다.

수정 전 비텍스트 전체 행 지문:
`9ad435fd7b767db0bc19990d2ff1108c975f1d3116dd6452027a72074aaa10f7`.

기존 `kkirikkiri`의 수정 전 읽기 전용 데이터 지문:
`3df53b4e1954e98b58aedbbefe98c6efb59505b80dd21724f8cf96108962d617`.

## 최종 적용·검증 결과

2026-10-31 KST, MySQL 8.0.44에 실제 적용했다. 전시회 백엔드는 8080에서
`APP_SEED_MODE=NONE / ddl-auto=validate / SQL never`로 재개했으며 프론트는 3000을 사용한다.

| 항목 | 결과 |
| --- | --- |
| 변경된 행 | 닉네임 30, 게시글 80, 댓글·대댓글 160, 메시지 33: 합계 303 |
| 적용 시나리오 | UPDATE 118, KEEP 19, CREATE/SKIP 0 |
| 실제 반복 적용 | 137 KEEP, 쓰기 없음, 계획 토큰 동일 |
| API·UI 검증 후 최종 preview | 137 KEEP, CREATE/UPDATE/SKIP 0 |
| 보존 지문 | 비텍스트 지문이 적용 전후 동일: 위의 `9ad435…10f7` |
| 원본 회원 출처 | `seed-users.properties` SHA-256 동일: `543eedffe9f1eb6562e634ac005f523ff28010c7da2ec32a439aa3ba40a000ae` |
| 기존 DB | `kkirikkiri` 전체 데이터 지문 동일: 위의 `3df53b…617` |
| 백엔드 전체 | `gradlew.bat test bootJar prepareExhibition` 성공, 88클래스/1,505테스트, 실패·오류·건너뜀 0 |
| 최종 코드 추가 회귀 | 출처 누락·수동 편집·오래된 토큰·ID/관계/시각 보존·반복 적용·부분 오류 롤백 검증, 5테스트 통과 |
| 프론트 전체 | 28묶음/266테스트 통과 (`--runInBand`), `npm run build` 성공 |
| 실제 API | 249요청 성공, 30명 정상 로그인 및 303개 텍스트 전체 카탈로그 일치 |
| 실제 브라우저 | headless Chromium 폼 로그인, 프로필·룸메이트·네 게시판 글/댓글/대댓글·룸메이트 채팅·캘린더 통과 |
| 계약/코드 점검 | 73 endpoints/75 DTO/30 entities 스냅샷 일치, `git diff --check` 통과 |

좋아요 200/80, 매칭 870(13~100점), 채팅방/멤버/메시지 8/16/33,
내 설문/선호 설문 각30, 중요도540, 일정12, 신고3을 보존했다.
게시글 부모가 없는 댓글과 방이 없는 메시지는 각0이다.
제목80개와 댓글·대댓글160개의 문구는 각각 중복이 없고, 닉네임30개는 중복·DTO 위반이 없다.

전시회 출처는 COMPLETE 상태로 성공한 변경 항목만 갱신했다.
기존 회원/글/댓글/메시지/방 ID, 댓글 자식 ID 기록, 나머지 시나리오 출처를 유지했다.
완료 직후·UI 검증 전 DB/출처 추가 백업은
`local-data/exhibition-20261031/backups/20261031-015718/`이다.

검증 증거는 Git 제외 `build/exhibition-live/`에 있다:

- `text-review-before.log`, `text-review-final.log`, `text-apply.log`
- `text-post-apply-preview.log`, `text-repeat-apply.log`, `text-final-preview.log`
- `text-original-before.txt`, `text-original-after.txt`, `text-database-verification.txt`
- `text-full-test-summary.json`, `text-regression-summary.json`, `api-verification.json`
- `browser/result.json`, `browser/profile.png`, `browser/roommates.png`, `browser/community.png`, `browser/chat.png`, `browser/calendar.png`

전체 백엔드 로그는 `build/exhibition-text-full-tests.log`, 추가 회귀 로그는
`build/exhibition-text-regression.log`다. 프론트 순차 테스트/빌드 로그는
`frontend/build-exhibition-text-tests-serial.log`, `frontend/build-exhibition-text-build.log`다.
첫 프론트 병렬 실행에서 시간 초과가 있어 순차 전체 실행으로 확인했으며 애플리케이션 코드는 바꾸지 않았다.

미검증: 이번 백업의 격리 DB 복원 리허설, 실제 다중 프로세스 MySQL 경합은 별도로 수행하지 않았다.
백업 파일 체크섬과 덤프 완료 표시는 확인했다. H2 회귀와 실제 MySQL 적용 결과를 구분한다.
공개 API/스키마/익명성 정책 변경이나 새 정책 보류 사항은 없다.

백업 복원 절차와 전시회 전용 서버 실행 조건은 [EXHIBITION_LOCAL_RUN.md](EXHIBITION_LOCAL_RUN.md)를 따른다.
