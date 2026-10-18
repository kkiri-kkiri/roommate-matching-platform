# ProfileLike M1·M2 수정 및 회귀 검증

검증일: 2026-10-18 (Asia/Seoul). 대상은 기존 Like 배포 검증의 M1·M2다.
과거 재현 증거인 `backend/build/like-validation/REPORT.md`, `frontend-probes.json`은 이력으로 보존했다.
현재 작업 시작 시 Git 상태는 깨끗했다. Git commit/push/merge 및 DB 작업은 수행하지 않았다.

## 수정 내용

**M1: 카드와 상세의 성공 처리를 하나로 통합했다.**

- `src/components/common/ProfileCard/profileLikes.js:29`에서 현재 세션의 성공한 backend Like 변경만 구독한다. 요청 시작/종료의 pending 알림과 API 실패는 목록 갱신으로 처리하지 않는다.
- `src/pages/mypage/MyPage.js:967`에서 이 성공 알림을 구독해 서버 목록을 다시 조회한다. 카드 전용 onToggleLike 재조회 경로를 제거해 성공 한 번에 한 번만 조회한다.
- `getMyLikedRoommates`가 반환한 content·totalElements·totalPages·first·last를 함께 반영한다. 마지막 페이지가 사라지면 기존 페이지 보정 규칙으로 유효한 이전 페이지를 다시 읽는다. 클라이언트에서 인원수만 임의 증감하지 않는다.
- 쓰기 실패에는 성공 알림이 없으므로 하트·목록·개수를 유지하고 오류와 재시도를 제공한다. 카드와 상세 모두 같은 훅과 성공 이벤트를 사용한다.

**M2: 기존 인증 세션 모듈에서 사용자와 세션 세대를 함께 관리한다.**

- `src/auth/session.js:19`: 토큰 변경, 로그인 저장, 로그아웃, 다른 탭의 토큰 storage 이벤트에서 세션 세대를 갱신한다. 동일 사용자·동일 JWT로 로그인해도 `storeAccessToken`은 새 세대를 만든다. 검증된 사용자 메타데이터 수정이나 focus 재검증만으로는 세대를 변경하지 않는다.
- `src/pages/auth/LoginPage.js:96`: 기존 토큰 저장을 이 함수로 연결한다. 별도 인증 저장소·Provider·의존성을 추가하지 않았다. JWT의 userId 읽기는 클라이언트 상태 구분용이며 서버 인증 검증을 대체하지 않는다.
- `src/components/common/ProfileCard/profileLikes.js:39`: 요청 시작 세션을 캡처한다. pending 키에 세대·사용자·대상을 포함하고, 완료 시 같은 세션이면서 해당 훅이 살아 있을 때만 성공/오류를 반영한다. 이전 요청의 finally도 새 세션의 pending을 해제하지 못한다.
- `src/components/common/ProfileCard/DetailedProfile.js:244`: 세션이 바뀌면 상세를 다시 조회하고 이전 응답/오류를 무시한다. 이전 세션 상세는 새 상세 응답이 오기 전에 표시하지 않는다.
- `src/pages/roommate/RoommateSearchPage.js:28`과 `MyPage.js:1270`: 같은 사용자 재로그인도 검색·관심 목록을 서버에서 새로 읽으며 이전 세션의 목록을 새 카드 데이터로 사용하지 않는다.
- `src/api/roommateApi.js:17`, `src/api/apiClient.js:21`: Axios 내부 설정에 요청 세대 숫자를 기록한다. 이전 세대의 401은 같은 JWT로 재로그인한 현재 인증을 해제하거나 새 세션에서 guest 재시도를 시작하지 못한다. 현재 세대의 401 처리는 유지한다. 이 값은 HTTP 헤더/본문이나 Like API 계약에 추가한 필드가 아니다.
- 이미 시작한 Like POST/DELETE는 AbortController로 취소하지 않으며 다시 보내지 않는다. A의 서버 작업은 A의 원래 인증으로 완료될 수 있다. 새 세션에서는 그 결과를 적용하지 않고 해당 계정의 서버 조회 결과를 사용한다. 이전 상세/목록 GET만 기존 AbortSignal로 정리한다.

## 변경 파일

| 파일 | 목적 |
|---|---|
| `src/auth/session.js` | 공통 세션 식별·구독·로그인 세대 갱신 |
| `src/pages/auth/LoginPage.js` | 실제 로그인 토큰 저장을 공통 세대 갱신 경로에 연결 |
| `src/components/common/ProfileCard/profileLikes.js` | 공통 Like 훅, 세션별 pending, 성공 알림과 완료 결과 검증 |
| `src/components/common/ProfileCard/ProfileCard.js` | 공통 훅 사용 및 카드 전용 중복 콜백 제거, 기존 export 호환 유지 |
| `src/components/common/ProfileCard/DetailedProfile.js` | 공통 훅 사용, 세션별 상세 재조회·늦은 응답 차단 |
| `src/pages/mypage/MyPage.js` | 성공 알림에 따른 서버 페이지/개수 갱신, 세션별 재마운트 |
| `src/pages/roommate/RoommateSearchPage.js` | 세션별 검색 재조회와 이전 목록 표시 차단 |
| `src/api/roommateApi.js`, `src/api/apiClient.js` | 이전 세대 401의 현재 로그인 해제 방지 |
| `src/components/common/ProfileCard/ProfileLikes.test.js` | 실제 카드·상세·마이페이지·검색·라우터를 사용하는 회귀 |
| `src/auth/session.test.js` | 세션 세대, 같은 JWT 로그인, focus 및 탭 이벤트 회귀 |
| `src/api/roommateApi.test.js` | API 계약 보존, 세션별 지연 401 및 쓰기 재시도 방지 회귀 |

UI 디자인·CSS·배치, Like API 경로와 응답, survey·매칭 점수 계산, PostLike·CommentLike 구현, backend 소스는 변경하지 않았다.

## 테스트와 결과

`ProfileLikes.test.js`는 실제 컴포넌트와 공통 훅·인증 모듈·MemoryRouter를 사용하며 API 응답만 mock한다. 다음을 검사한다.

1. 카드 관심 추가 → 마이페이지 content·totalElements 갱신, 정확히 한 번 재조회.
2. 상세 취소 → 관심 목록 제거·개수 갱신, 상세 하트 변경.
3. 7명 목록의 마지막 2페이지에서 마지막 프로필 취소 → 요청 페이지 0/1/1/0으로 보정.
4. 마지막 한 명 취소 → 빈 상태와 0명 표시.
5. 실제 카드 링크로 상세를 열고 취소·닫기 → 갱신된 배경 마이페이지 유지.
6. 카드·상세 pending 공유, 연속 클릭·중복 요청 차단, 성공 후 양쪽 하트 일치.
7. API 실패 → 하트·목록·개수 유지와 재시도.
8. A 성공/실패를 각각 지연하고 B 요청을 진행 → A의 완료가 B 하트·오류·pending에 영향 없음.
9. 같은 사용자·동일 JWT 재로그인 → 이전 세대 결과 차단.
10. 요청 컴포넌트 unmount 후 새 화면 mount → 같은 세션/다른 세션 모두 늦은 성공 차단.
11. A 상세 응답 지연 후 B 세션 상세 조회 → A 응답 무시, B 서버 데이터 반영.
12. A 성공 지연 후 B 마이페이지 조회 → B 목록/개수 불변, 불필요한 재조회 없음.
13. 상세 unmount/remount → 서버 저장값 복구. 실제 브라우저 새로고침 E2E와 구분한다.
14. 검색 화면 계정 전환 → 새 서버 liked 반영.
15. StrictMode의 effect 재설치에서도 쓰기 1회, 성공 후 재조회 1회.

추가로 `session.test.js` 3건은 focus/사용자 표시 정보 수정 시 세대 유지, 동일 JWT 로그인 시 새 세대, 탭 storage 이벤트를 여러 구독자에게 한 번씩 전달하는 것을 검사한다. `roommateApi.test.js`에는 4건을 추가해 POST/DELETE 경로·메서드 보존, 이전 세대의 쓰기/상세 401 무시, 현재 세대의 401 인증 해제·쓰기 무재시도를 검사한다.

최종 결과: **수정 완료**. M1·M2는 아래 범위의 회귀를 통과했다.

| 실행 | 통과 | 실패 | skip/미실행 | 증거 |
|---|---:|---:|---:|---|
| 최종 프론트 전체 (19 suite) | 178 | 0 | 0 | `backend/build/profile-like-fix/frontend-final-results.json`, `frontend-final-tests.log` |
| 위 전체 중 신규 실제 컴포넌트/훅 회귀 | 17 | 0 | 0 | `ProfileLikes.test.js` |
| 위 전체 중 신규 세션 회귀 | 3 | 0 | 0 | `session.test.js` |
| 위 전체 중 roommateApi 회귀 | 6 (기존 2 + 신규 4) | 0 | 0 | `roommateApi.test.js` |
| production build | 성공 | 0 | 0 | `backend/build/profile-like-fix/frontend-final-build.log` |
| 계약 스냅샷 / git diff --check | 통과 | 0 | 0 | 70 endpoints / 71 record DTOs / 29 entities 선언 대조 |

신규 테스트는 총 **24건**이며, 기존 154건을 포함한 최종 합계가 178건이다. 표의 하위 테스트를 전체 178건에 중복 합산하지 않는다. 기존 auth·chat·survey·community·calendar·mypage·검색 테스트도 통과했으며 실행한 범위에서 회귀는 발견되지 않았다. build는 `Compiled successfully`였고 Node의 fs.F_OK deprecation 안내만 있었다.

재현 명령 (frontend 디렉터리):

```powershell
$env:CI='true'
npm.cmd test -- --watch=false --runInBand
npm.cmd run build
```

계약 대조는 저장소 루트에서 `node backend/tools/sync-contract-docs.cjs --check`를 실행했다.
처음 신규 suite를 구성할 때 JSDOM의 TextEncoder가 없어 테스트 실행 전에 실패했다. 이 테스트 파일에서만 Node util의 TextEncoder/TextDecoder를 연결했고, 앱 코드는 이 환경 문제 때문에 변경하지 않았다. 이후 15건 대상 실행, 중간 176건 전체 실행, 추가한 실제 라우팅/unmount 회귀를 포함한 최종 178건 실행을 구분해 로그로 보존했다. 과거 `frontend-probes.json`의 REPRODUCED 기록을 덮어쓰지 않았다.

## 미실행 및 남은 확인

- 실제 브라우저 + 실제 서버 E2E는 **미실행**이다. 테스트는 JSDOM/API mock이며 로그인·토큰의 실제 서버 발급, 실제 네트워크에서의 타이밍을 종합 검증한 것은 아니다.
- 수동 확인: 관심 탭에서 상세 취소 후 닫기, 마지막 페이지 삭제, 같은 사용자 재로그인/다른 계정 전환 중 느린 요청, 실제 새로고침 후 저장값 복원, 다른 탭의 로그인 전환을 확인한다. 테스트 기기/브라우저 조합별 개별 건수는 정의하지 않았으므로 임의로 PASS 수에 포함하지 않는다.
- 개발·운영 DB 및 운영 배포는 접속/변경/검증하지 않았다. 프론트 변경이므로 이전 MySQL 47-case 결과를 이번 실행으로 다시 계산하지 않는다. 백엔드 테스트도 이번에 재실행하지 않는다.
- 네트워크 timeout으로 쓰기의 성공 여부를 모르는 경우에는 기존 화면을 성공으로 바꾸지 않는다. 서버가 이미 커밋했을 수 있으므로 서버 재조회가 필요하다. 다른 기기에서의 Like 변경을 실시간으로 전파하는 기능은 추가하지 않았다.
- 기존 보고서의 M3(PostLike 목록 성능), L1(과거 익명 댓글 설명)은 이번 수정 범위가 아니다. M1·M2 수정으로 Like 전체의 배포 준비가 모두 끝났다고 판정하지 않는다.
