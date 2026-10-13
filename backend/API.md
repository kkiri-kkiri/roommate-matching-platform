API 명세서

상태: Frontend 기반 수정본
기준: 첨부된 React src 전체 구조와 현재 화면 동작
목적: Spring Boot 백엔드 구현 시 Controller / Service / Repository / DTO 작업의 공통 계약으로 사용
수정 기준: ERD와의 불일치, 인증 선행조건, 설문/채팅/캘린더 구현 누락 보완

0. 문서 사용 기준

현재 프론트엔드는 실제 API 대신 Mock Data, localStorage, sessionStorage, React state를 사용하고 있다. 이 문서는 해당 화면에서 실제로 필요한 서버 기능을 추출해 REST API 계약으로 정리한 것이다.

저장 구조는 ERD.md를 기준으로 한다.

화면 동작과 API 요청/응답 계약은 이 문서를 기준으로 한다.

ERD.md와 API.md가 충돌하면 임의로 구현하지 말고 팀에서 먼저 결정한다.

프론트에서 사용하지 않는 기능을 임의로 추가하지 않는다.

아래의 확인 필요 항목은 구현 전에 팀에서 확정한다.

확인한 주요 프론트 화면

App.js

pages/auth/\*

pages/main/MainPage.js

pages/mypage/MyPage.js

pages/roommate/RoommateSearchPage.js

components/common/ProfileCard/\*

pages/community/\*

pages/chat/ChatPage.js

calendar/\*

pages/admin/AdminPage.js

1. 공통 규칙

1.1 Base URL

/api

예:

POST /api/auth/login
GET /api/users/me
GET /api/posts

1.2 인증

초기 인증 방식은 Stateless JWT Access Token 방식을 사용한다.

인증이 필요한 API는 아래 Header를 사용한다.

Authorization: Bearer {accessToken}

인증 기준:

로그인 성공 시 서버가 JWT Access Token을 발급한다.

Access Token에는 최소 userId, role을 포함한다.

서버는 Access Token을 DB나 메모리에 별도로 저장하지 않는다.

서버는 JWT 서명과 만료시간을 검증한 뒤 인증 사용자 정보를 생성한다.

프론트의 토큰 저장 키는 accessToken으로 통일한다.

초기 버전에서는 Refresh Token을 사용하지 않는다.

앱 진입 및 장시간 비활성 후 복귀 시 GET /api/auth/me로 저장된 Access Token을 검증한다.
만료되거나 서버가 401을 반환한 토큰은 삭제하고 비로그인 상태로 전환한다.
서버 재시작 자체는 유효한 JWT를 만료시키지 않는다. 동일한 JWT_SECRET과 활성 계정이 유지되면 만료시간까지 사용할 수 있다.

Refresh Token 및 재발급 기능은 필요 시 추후 추가한다.

1.3 공통 성공 응답

global.response.ApiResponse<T> 사용을 전제로 한다.

{
"success": true,
"message": "요청이 성공했습니다.",
"data": {}
}

데이터가 없는 경우:

{
"success": true,
"message": "처리가 완료되었습니다.",
"data": null
}

이하 각 API의 Response 예시는 ApiResponse.data 내부 데이터만 표기한다. 실제 HTTP 응답에서는 ApiResponse<T> 형식으로 감싸서 반환한다.

1.4 공통 실패 응답

{
"success": false,
"message": "요청을 처리할 수 없습니다.",
"data": null,
"errorCode": "INVALID_REQUEST"
}

Validation 오류는 필드별 오류를 추가할 수 있다.

{
"success": false,
"message": "입력값을 확인해 주세요.",
"data": null,
"errorCode": "VALIDATION_ERROR",
"fieldErrors": {
"title": "제목을 입력해 주세요."
}
}

1.5 페이징

Spring Data Pageable 기준으로 서버 API는 0-based page를 사용한다.

?page=0&size=6

응답 예:

{
"content": [],
"page": 0,
"size": 6,
"totalElements": 31,
"totalPages": 6,
"first": true,
"last": false
}

프론트의 Pagination은 1부터 표시하므로 호출 시 화면 페이지 - 1로 변환한다.

1.6 날짜 / 시간

날짜: YYYY-MM-DD
시간: HH:mm
일시: ISO-8601 또는 YYYY-MM-DDTHH:mm:ss

서버 기준 시간대는 Asia/Seoul을 사용한다.

2. 인증 / 회원가입

2.1 로그인

POST /api/auth/login

Public

Request:

{
"email": "test01@hknu.ac.kr",
"password": "test1234"
}

Response:

{
"accessToken": "...",
"user": {
"id": 1,
"schoolEmail": "test01@hknu.ac.kr",
"nickname": "차분한하루",
"role": "USER"
}
}

Validation:

학교 이메일(@hknu.ac.kr) 필수

password 필수

이메일/비밀번호 불일치 시 동일한 인증 실패 메시지 반환

2.1.1 현재 인증 사용자 확인

GET /api/auth/me

Authorization: Bearer {accessToken} 필수. 서명·만료시간·활성 계정을 검증하고 아래 데이터를 반환한다.
프로필이 없는 계정은 nickname에 학교 이메일을 반환한다.

```json
{
  "id": 1,
  "schoolEmail": "student@hknu.ac.kr",
  "nickname": "학생",
  "role": "USER"
}
```

토큰 누락·만료·잘못된 서명 또는 비활성/삭제된 계정은 401 UNAUTHORIZED를 반환한다.

2.2 로그아웃

초기 버전에서는 별도의 서버 Logout API를 사용하지 않는다.

클라이언트가 저장하고 있는 JWT Access Token을 삭제하여 로그아웃한다.

Refresh Token을 도입할 경우 서버 Logout API와 Refresh Token 무효화 정책을 추가한다.

2.3 학교 이메일 인증번호 전송

POST /api/auth/email-verifications/send

Public

Request:

{
"email": "student@hknu.ac.kr",
"purpose": "SIGNUP"
}

purpose:

SIGNUP

Response:

{
"expiresInSeconds": 300
}

Validation:

@hknu.ac.kr 이메일만 허용

이메일 @ 앞부분은 프론트 기준 최대 64자

purpose 필수

과도한 재전송 방지 필요

2.4 학교 이메일 인증번호 확인

POST /api/auth/email-verifications/verify

Public

Request:

{
"email": "student@hknu.ac.kr",
"code": "123456",
"purpose": "SIGNUP"
}

Response:

{
"verified": true,
"verificationToken": "..."
}

Validation:

인증번호 숫자 6자리

인증번호 만료 여부 확인

인증 요청의 email, purpose가 일치해야 함

이미 사용된 Verification Token은 재사용할 수 없음

회원가입 실행 시 인증 완료 여부를 서버에서도 다시 검증

2.5 회원가입

POST /api/auth/signup

Public

Request:

{
"password": "password123!",
"passwordConfirm": "password123!",
"nickname": "차분한하루",
"schoolEmail": "roomie24@hknu.ac.kr",
"emailVerificationToken": "...",
"gender": "여자",
"dormitory": "나래",
"faculty": "컴퓨터응용수학부",
"major": "소프트웨어융합전공",
"birthYear": 2002,
"admissionYear": "24",
"grade": "3학년",
"mbtiEnergy": "I",
"mbtiInformation": "N",
"mbtiDecision": "F",
"mbtiLifestyle": "J"
}

Response:

{
"userId": 1,
"nickname": "차분한하루"
}

Validation:

모든 필드 필수

비밀번호 8자 이상

비밀번호/확인 일치

학교 이메일 인증 완료

출생연도: 1900 ~ 현재 연도

입학연도: 현재 프론트 기준 숫자 2자리

학년: 1학년 ~ 5학년

MBTI 4개 축 모두 선택

학부와 전공의 조합 검증

nickname, schoolEmail 중복 검증

GET /api/auth/signup/availability (Public)

Query: schoolEmail, nickname

Response data: { "schoolEmailAvailable": true, "nicknameAvailable": true }

가입 시에도 중복 여부를 다시 확인하며 충돌은 409 Conflict로 처리한다.

2.6 로그인 이메일 확인

POST /api/auth/login/email-check

Public

Request:

{ "email": "roomie24@hknu.ac.kr" }

Response data:

{ "registered": true }

학교 이메일 형식을 검증하고 계정 존재 여부만 반환한다. JWT는 발급하지 않는다.
로그인 화면은 등록된 이메일을 확인한 뒤 비밀번호 입력을 표시한다.
이메일을 수정하면 확인 상태와 비밀번호를 초기화한다.

2.7 비밀번호 재설정 요청

POST /api/auth/password-reset/request

Public

Request:

{
"email": "roomie24@hknu.ac.kr"
}

Response:

{
"sent": true
}

동작:

schoolEmail로 ACTIVE 상태의 USER 조회

유효한 사용자라면 일회용 Password Reset Token 생성

DB에는 Token 원문이 아닌 해시값 저장

Token 만료시간 저장

사용자의 학교 이메일로 재설정 링크 또는 Token 전송

계정 존재 여부 노출을 줄이기 위해 존재하지 않는 계정이어도 동일한 응답 형태 사용 권장

2.8 비밀번호 재설정

POST /api/auth/password-reset/confirm

Public

Request:

{
"resetToken": "...",
"newPassword": "newPassword123!",
"newPasswordConfirm": "newPassword123!"
}

Validation:

Reset Token 존재 여부 확인

Reset Token 만료 여부 확인

이미 사용된 Token인지 확인

비밀번호 8자 이상

새 비밀번호 확인 일치

비밀번호 변경 성공 후 Token 사용 완료 처리

3. 사용자 / 마이페이지

3.1 내 정보 조회

GET /api/users/me

Auth

Response:

{
"id": 1,
"nickname": "차분한하루",
"schoolEmail": "roomie24@hknu.ac.kr",
"gender": "여자",
"dormitory": "나래",
"faculty": "컴퓨터응용수학부",
"major": "소프트웨어융합전공",
"birthYear": 2002,
"admissionYear": "24",
"grade": "3학년",
"mbtiEnergy": "I",
"mbtiInformation": "N",
"mbtiDecision": "F",
"mbtiLifestyle": "J"
}

프론트에서 Read-only로 표시하는 값:

schoolEmail

gender

birthYear

admissionYear

3.2 내 정보 수정

PATCH /api/users/me

Auth

현재 프론트에서 수정 가능한 필드만 허용한다.

Request 예:

{
"nickname": "차분한하루",
"dormitory": "나래",
"grade": "3학년",
"faculty": "컴퓨터응용수학부",
"major": "소프트웨어융합전공",
"mbtiEnergy": "I",
"mbtiInformation": "N",
"mbtiDecision": "F",
"mbtiLifestyle": "J"
}

부분 수정 허용.

3.3 비밀번호 변경

PATCH /api/users/me/password

Auth

Request:

{
"currentPassword": "oldPassword",
"newPassword": "newPassword123!",
"newPasswordConfirm": "newPassword123!"
}

Validation:

현재 비밀번호 일치

새 비밀번호 8자 이상

새 비밀번호 확인 일치

4. 내 프로필 설문

4.1 내 프로필 설문 조회

GET /api/users/me/profile-survey

Auth

미작성 상태 예:

{
"completed": false,
"answers": null
}

작성 상태 예:

{
"completed": true,
"answers": {
"sleepTime": "23시 ~ 24시",
"wakeTime": "07시 ~ 08시",
"sleepTalking": "없음",
"snoring": "없음",
"teethGrinding": "없음",
"sleepSensitivity": "보통",
"indoorEating": "냄새 안나는 음식 가능",
"smoking": "비흡연",
"lightingPreference": "어두움 선호",
"phoneCall": "항상 밖에서",
"mediaSound": "항상 이어폰 사용해주세요",
"showerTime": "저녁",
"showerDuration": "20분",
"cleaningFrequency": "주 2회",
"cleaningStyle": "상황에 따라 협의",
"dormStayDays": ["월", "화", "수", "목", "금"],
"mealPlan": ["조식", "석식"],
"meal": "시간 될 때만 같이 먹고 싶음",
"roommateInteraction": "적당히 대화",
"temperaturePreference": "보통",
"hairDryer": "사용",
"bugHandling": "작은 벌레는 가능",
"scentProductUse": "사용 안 함",
"desktopUse": "사용하지 않아요",
"sharedItems": "일부만 공유"
}
}

4.2 내 프로필 설문 저장/수정

PUT /api/users/me/profile-survey

Auth

Request:

PUT 요청 본문은 answers로 감싸지 않고 아래 객체를 그대로 전송한다.

```json
{
"sleepTime": "23시 ~ 24시",
"wakeTime": "07시 ~ 08시",
"sleepTalking": "없음",
"snoring": "없음",
"teethGrinding": "없음",
"sleepSensitivity": "보통",
"indoorEating": "냄새 안나는 음식 가능",
"smoking": "비흡연",
"lightingPreference": "어두움 선호",
"phoneCall": "항상 밖에서",
"mediaSound": "항상 이어폰 사용해주세요",
"showerTime": "저녁",
"showerDuration": "20분",
"cleaningFrequency": "주 2회",
"cleaningStyle": "상황에 따라 협의",
"dormStayDays": ["월", "화", "수", "목", "금"],
"mealPlan": ["조식", "석식"],
"meal": "시간 될 때만 같이 먹고 싶음",
"roommateInteraction": "적당히 대화",
"temperaturePreference": "보통",
"hairDryer": "사용",
"bugHandling": "작은 벌레는 가능",
"scentProductUse": "사용 안 함",
"desktopUse": "사용하지 않아요",
"sharedItems": "일부만 공유"
}
```

현재 MyPage 저장 로직에서는 필수 설문 항목에 답변이 있어야 저장된다. 마지막의 `나의 한마디`는 선택 항목이다.

PUT 본문에는 선택 필드 `introduction`을 함께 보낸다. 저장 위치는 기존 `USER_PROFILE.introduction`이며
PROFILE_SURVEY에 복제하지 않는다. GET/PUT 응답은 `{ "completed": true, "answers": { ... }, "introduction": "안녕하세요\n반가워요" }`이다.
설문이 없을 때도 `answers: null`과 별도로 기존 프로필의 `introduction`을 반환한다.
JWT 사용자 자신의 프로필과 설문을 같은 트랜잭션에서 수정한다. 다른 사용자의 ID를 본문으로 지정할 수 없다.

- 최대 200 UTF-16 코드 단위: Java String.length/Bean Validation Size와 JavaScript length/textarea maxLength 기준을 맞춘다.
- 200자 초과는 400 Validation 오류이며 저장하지 않는다. 한글과 개행, 내용이 있는 문자열의 앞뒤 공백은 보존한다.
- 빈 문자열 또는 공백만 있는 문자열은 빈 문자열로 저장하여 삭제한다.
- null 또는 필드 생략은 기존 소개를 유지한다. 이전 클라이언트의 설문 저장으로 기존 소개가 지워지지 않는다.
- 기존 201~255자 소개는 조회 시 자르지 않는다. 새로 저장할 소개는 200자 이하로 수정해야 한다.
- introduction은 설문 완료 판정 및 매칭 점수 계산 대상이 아니다. 설문 답변 변경 시 기존 매칭 갱신 이벤트를 유지한다.
- PUT은 기존과 같이 필수 설문 답변 전체를 요구한다. 소개만 있는 불완전한 요청은 거부하여 설문 유실을 방지한다.
- 룸메찾기 목록/상세/관심 프로필은 기존 USER_PROFILE.introduction을 매번 조회한다.

Response data는 GET과 동일한 `{ "completed": true, "answers": { ... } }` 형식이다.
검증 후 JWT 사용자에 해당하는 현재 설문을 생성/수정하고, 저장한 단일·복수 응답을 반환한다.
프론트는 이 응답으로 화면을 갱신한다. 필수 `meal`은 룸메이트와의 식사 방식이며, 복수 선택 `mealPlan`과 별개다.
GET의 completed는 DB 완료 플래그와 필수 응답의 유효성을 함께 확인한다. 설문이 없으면 false/null,
저장된 응답이 불완전하면 false/기존 응답을 반환하며 조회만으로 DB를 수정하지 않는다.
localStorage 설문은 조회·저장·완료 판정에 사용하지 않고 자동으로 DB에 이전하지 않는다.
현재 구현의 요청 필드는 ProfileSurveyRequest 기준 필수 설문 23개와 선택 introduction이며, 기존 명세 예시의 과거 필드 hairDryer/bugHandling은
현재 DTO와 Entity에 존재하지 않는다. 이번 변경에서 해당 필드를 추가하거나 스키마를 변경하지 않는다.

5. 선호 룸메이트 설문

5.1 선호 설문 조회

GET /api/users/me/roommate-preferences

Auth

Response:

{
"completed": true,
"answers": {
"department": "상관없음",
"age": ["상관없음"],
"grade": ["상관없음"],
"bedtime": ["23시 ~ 24시", "24시 ~ 01시"],
"wakeTime": ["07시 ~ 08시", "08시 ~ 09시"],
"sleepTalking": "적음까지 괜찮음",
"snoring": "없음만 가능",
"teethGrinding": "적음까지 괜찮음",
"smoking": "비흡연자만",
"lightingPreference": "어두움 선호",
"showerTime": "저녁",
"showerDuration": "20분 이하",
"cleaningFrequency": ["주 1회", "주 2회"],
"cleaningStyle": "상황에 따라 협의하는 사람",
"interaction": "적당히 대화하고 싶은 사람",
"meal": "시간 될 때만 같이 먹고 싶음",
"sharedItems": "일부만 공유하는 사람",
"temperature": "보통인 사람",
"weights": {
"department": 3,
"age": 3,
"grade": 3,
"bedtime": 3,
"wakeTime": 3,
"sleepTalking": 3,
"snoring": 3,
"teethGrinding": 3,
"smoking": 3,
"lightingPreference": 3,
"showerTime": 3,
"showerDuration": 3,
"cleaningFrequency": 3,
"cleaningStyle": 3,
"interaction": 3,
"meal": 3,
"sharedItems": 3,
"temperature": 3
}
}
}

weights는 각 설문 항목별 1 ~ 5 값이며 현재 기본값은 3이다.

5.2 선호 설문 저장/수정

PUT /api/users/me/roommate-preferences

Auth

Request:

PUT 요청 본문은 answers로 감싸지 않고 아래 객체를 그대로 전송한다.
weights에는 지원하는 18개 키를 모두 포함한다.

```json
{
"department": "상관없음",
"age": ["상관없음"],
"grade": ["상관없음"],
"bedtime": ["23시 ~ 24시", "24시 ~ 01시"],
"wakeTime": ["07시 ~ 08시", "08시 ~ 09시"],
"sleepTalking": "적음까지 괜찮음",
"snoring": "없음만 가능",
"teethGrinding": "적음까지 괜찮음",
"smoking": "비흡연자만",
"lightingPreference": "어두움 선호",
"showerTime": "저녁",
"showerDuration": "20분 이하",
"cleaningFrequency": ["주 1회", "주 2회"],
"cleaningStyle": "상황에 따라 협의하는 사람",
"interaction": "적당히 대화하고 싶은 사람",
"meal": "시간 될 때만 같이 먹고 싶음",
"sharedItems": "일부만 공유하는 사람",
"temperature": "보통인 사람",
"weights": {
"department": 3,
"age": 3,
"grade": 3,
"bedtime": 3,
"wakeTime": 3,
"sleepTalking": 3,
"snoring": 3,
"teethGrinding": 3,
"smoking": 3,
"lightingPreference": 3,
"showerTime": 3,
"showerDuration": 3,
"cleaningFrequency": 3,
"cleaningStyle": 3,
"interaction": 3,
"meal": 3,
"sharedItems": 3,
"temperature": 3
}
}
```

Validation:

화면에 노출된 모든 선호 항목 답변 필수

multi 항목에서 상관없음은 다른 값과 동시 선택 불가

가중치: 1 ~ 5

모든 선호 항목에 가중치 존재

6. 룸메이트 찾기

공통 계산·조회 정책 (2026-09-28 확정):

- A → B는 A의 완료된 선호 설문과 A/B의 프로필, B의 완료된 프로필 설문으로 계산한다.
- 점수 = round(100 × 충족 항목 가중치 합 / 적용 항목 가중치 합). 가중치는 1~5이며 항목 충족은 0 또는 1이다. 소수점 첫째 자리에서 반올림한다.
- 선호의 “상관없음”은 분자·분모에서 제외한다.
- 복수 선택은 하나라도 일치하면 충족한다. 잠꼬대·코골이·이갈이는 없음 < 적음 < 보통 < 심함 순서로 허용 상한을 비교한다.
- 샤워 소요시간은 선호 상한 이하일 때 충족한다. “그 이상”은 40분 이하 조건도 충족하지 않는다.
- “비흡연자만”은 비흡연만, “전자담배까지 허용”은 비흡연과 전자담배를 허용한다.
- 학부·전공은 ID로 비교한다. “같은 전공”과 “같은 학부의 다른 전공”은 양쪽 전공이 있어야 충족한다. 나이는 출생연도로 연하·동갑·연상을 비교하며 학년은 상대 학년과 일치 여부를 비교한다.
- 청소·교류·공용물품·온도는 선호 문구와 실제 답변의 의미를 매핑한다. 그 외 항목은 선택값 일치를 사용하며 “유동적”은 별도 선택값이다.
- 선호 미작성, 계산 입력 누락, 적용 가중치 합이 0인 경우 점수는 null이며 추천하지 않는다. 임시 점수를 저장하지 않는다.
- 프로필 설문 커밋 후 다른 사용자 → 나, 선호 설문 커밋 후 나 → 다른 사용자를 갱신한다. 학부·전공 변경은 양방향, 학년 변경은 다른 사용자 → 나를 갱신한다.
- 갱신 실패는 로그에 기록한다. 전체·관심 목록은 해당 페이지, 상세는 해당 상대, 추천은 해당 요청자의 후보 점수를 조회 전에 다시 준비하여 기존 데이터와 실패한 갱신을 복구한다.
- 공개 대상은 자기 자신을 제외한 ACTIVE 사용자 중 USER_PROFILE과 completed=true인 PROFILE_SURVEY가 있는 사용자다. 필수 프로필 값이 없는 대상도 제외하며 major, introduction, profileImageUrl의 null은 허용한다.
- 제외 조건과 설문 필터는 페이징·count 이전 DB 쿼리에 적용한다. 카드 프로필·설문·관심 여부 및 계산 입력은 대상 ID 기준 일괄 조회한다.

6.1 전체 프로필 조회

GET /api/roommates

Auth

Query 예:

?page=0
&size=9
&sleepTime=23시%20~%2024시
&smoking=비흡연
&cleaningFrequency=주%202회

필터 키는 PROFILE_SECTIONS의 설문 키를 그대로 사용한다.

매칭 결과 행이 없어도 유효한 후보를 조회한다. 기본 정렬은 상대 userId ASC이다.
명시적 sort는 userId,asc 또는 userId,desc만 지원하고 그 외 필드는 INVALID_REQUEST로 처리한다.
서로 다른 필터 항목은 AND, 같은 항목의 선택값들은 OR이다.
matchScore는 계산할 수 없을 때 null이다.

멀티 선택 필터는 동일 Query Parameter를 반복하거나 comma-separated 방식 중 하나로 통일한다.

권장:

?smoking=비흡연&smoking=전자담배

Response item:

{
"id": 2,
"nickname": "포근한구름",
"admissionYear": "24",
"grade": "3학년",
"faculty": "컴퓨터응용수학부",
"major": "소프트웨어융합전공",
"dormitory": "나래",
"mbti": "INFJ",
"introduction": "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
"tags": ["비흡연", "아침형", "주 1회 청소"],
"profileImageUrl": null,
"matchScore": 94,
"liked": false
}

introduction, profileImageUrl은 USER_PROFILE 기준으로 사용한다. tags는 별도 저장 테이블을 두지 않고 프로필/설문 값을 기반으로 서버에서 표시용으로 생성한다.

6.2 추천 프로필 조회

GET /api/roommates/recommended

Auth

Query:

?page=0&size=9

추천 정렬 기준:

matchScore DESC, 상대 userId ASC (동점 보조 정렬)

추천 기준 matchScore >= 60

요청당 최대 9개 표시. size가 9보다 크면 9로 제한한다.
명시적 sort는 matchScore,desc만 허용한다.

실제 추천 점수 계산은 백엔드에서 처리한다.

Response item은 전체 프로필 조회와 동일하며 추천 결과의 matchScore는 항상 숫자다.

프론트 추천 탭은 page=0, size=9로 이 API를 호출한다. 응답 id는 실제 userId이며
카드의 userId, 상세 경로, AI 분석 요청에 동일하게 사용한다. 설문 필터와 상세 표시에 필요한
answers는 기존 GET /api/roommates/{userId}에서 조회한다. 전체 탭의 예시 데이터는 추천에 섞지 않는다.
카드에 마우스를 올리거나 키보드로 선택하면 AI 분석을 요청하고 기존 오버레이에 표시한다.
요청 중 중복 호출을 방지하며, 카드가 화면에서 제거될 때 진행 중 요청을 취소한다. 실패 후 재선택 시 다시 요청한다.
추천 조회/AI 분석 실패와 빈 추천 목록은 각각 별도 상태로 표시한다.

6.2.1 AI 프로필 분석

POST /api/roommates/{userId}/ai-analysis

Auth. 요청 본문 없음. 경로 userId는 분석 대상이며 요청자는 인증 토큰에서 결정한다.

Response (공통 ApiResponse.data):

```json
{
  "targetUserId": 2,
  "matchScore": 75,
  "analysis": "AI가 생성한 매칭 결과 설명"
}
```

- 기존 공개 후보 검증 및 매칭 갱신/계산을 재사용한다. 점수 산식과 추천 기준은 변경하지 않는다.
- 저장하는 점수와 동일한 계산 결과에서 일치/불일치 항목을 추출한다. 상관없음 항목은 제외한다.
- AI에는 matchScore, matchedItems, mismatchedItems만 전달한다. 항목은 PreferenceCriterionCode이며 사용자 ID, 이름, 이메일, 설문 원본은 전달하지 않는다.
- AI는 한국어 자연어 설명만 생성한다. API의 matchScore는 AI 응답에서 추출하지 않는다.
- 분석은 요청 시 생성하여 반환하며 DB에 저장하지 않는다. Gateway 대기 중에는 매칭 트랜잭션을 유지하지 않는다.
- ai.api-key, ai.base-url, ai.model 설정 사용. 현재 AI_MODEL=gpt-5.6-luna.
- POST {AI_BASE_URL}/chat/completions/ (Bearer 인증), choices[0].message.content 사용.
- 연결 제한 5초, 응답 제한 45초. 자동 재시도 없음. 인증키와 Gateway 오류 본문은 로그/응답에 노출하지 않는다.
- 400 INVALID_REQUEST: 자기 자신, 유효하지 않거나 공개 불가능한 대상.
- 401 UNAUTHORIZED: 인증 없음/실패.
- 409 MATCH_ANALYSIS_UNAVAILABLE: 선호 미작성 등으로 점수 계산 불가.
- 503 AI_GATEWAY_UNAVAILABLE: 설정 누락, Gateway HTTP 오류, 연결/시간 초과.
- 502 AI_GATEWAY_INVALID_RESPONSE: 잘못된 JSON, choices/content 누락, 빈 문자열 또는 문자열이 아닌 content.

6.3 룸메이트 상세 프로필

GET /api/roommates/{userId}

Auth

Response:

{
"id": 2,
"nickname": "포근한구름",
"admissionYear": "24",
"grade": "3학년",
"faculty": "컴퓨터응용수학부",
"major": "소프트웨어융합전공",
"dormitory": "나래",
"mbti": "INFJ",
"introduction": "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
"tags": ["비흡연", "아침형", "주 1회 청소"],
"profileImageUrl": null,
"matchScore": 94,
"liked": false,
"answers": {
"sleepTime": "23시 ~ 24시",
"wakeTime": "07시 ~ 08시"
}
}

상세 조회는 대상의 공개 가능 여부를 먼저 검사한다. 유효한 대상의 매칭 행이 없으면 계산을 시도하며,
계산 불가 상태는 matchScore: null로 응답한다. 자기 자신·없는 사용자·비활성·불완전한 대상은 INVALID_REQUEST이다.

answers에는 단일 응답과 함께 `dormStayDays` 및 `mealPlan` 배열을 반환한다.
배열은 대상 사용자의 PROFILE_SURVEY_OPTION에서 조회하며, `meal`은 별도의 단일 응답이다.
기존 공개 후보 조건과 개인정보 공개 범위를 유지한다. 게스트 조회에는 개인화 점수/관심 여부를 제공하지 않는다.

6.4 관심 프로필 추가

POST /api/roommates/{userId}/likes

Auth

중복 요청은 멱등 처리 권장.

대상의 공개 가능 여부로 등록을 판단하며 매칭 결과 행을 요구하지 않는다.
동시 중복 등록도 저장 트랜잭션 롤백 후 존재 여부를 확인하여 멱등 처리한다.

6.5 관심 프로필 취소

DELETE /api/roommates/{userId}/likes

본인의 관심 관계만 취소하며 이미 없는 관계는 기존 정책대로 INVALID_REQUEST이다.

Auth

6.6 내가 관심 등록한 프로필 조회

GET /api/users/me/liked-roommates

Auth

Query:

?page=0&size=6

Response item은 룸메이트 카드 응답과 동일.

관심 목록은 유효한 대상만 count와 페이지 조회에 포함하고 기본 정렬은 profileLikeId DESC이다.
명시적 sort는 profileLikeId,asc 또는 profileLikeId,desc만 허용한다.
매칭 행 누락·계산 불가 대상도 조회할 수 있으며 matchScore는 null일 수 있다.
매칭 결과의 갱신·제거는 관심 관계에 영향을 주지 않는다.

7. 커뮤니티

7.1 게시글 목록

GET /api/posts

Public 조회 가능 권장

Query:

?page=0
&size=6
&category=자유게시판
&q=룸메이트
&dormitory=나래관
&sort=LATEST

dormitory: 생략하면 전체 생활관을 조회한다. 나래관/호연관/비봉관/창조관 또는 DB 기준정보 이름인 나래/호연/비봉/창조를 받으며, 모든 정렬 방식에 적용한다.

sort:

LATEST
VIEWS
LIKES

프론트 검색 대상:

제목

내용

Response item:

{
"id": 1,
"category": "자유게시판",
"dormitory": "나래관",
"title": "룸메이트와 함께 정한 작은 생활 규칙",
"contentPreview": "이번 학기에...",
"author": {
"id": 10,
"nickname": "포근한구름"
},
"createdAt": "2026-09-14T12:00:00",
"views": 120,
"likes": 8,
"commentCount": 3,
"hasImages": true,
"liked": false,
"reported": false
}

7.2 게시글 상세

GET /api/posts/{postId}

Response:

{
"id": 1,
"category": "자유게시판",
"dormitory": "나래관",
"title": "...",
"content": "...",
"author": {
"id": 10,
"nickname": "포근한구름"
},
"createdAt": "2026-09-14T12:00:00",
"views": 121,
"likes": 8,
"liked": false,
"reported": false,
"images": [
{
"id": 1,
"url": "https://..."
}
],
"comments": []
}

게시글 상세 조회 시 조회수 증가 정책은 백엔드에서 처리한다.

목록·상세는 공개 조회이며 likes는 전체 좋아요 수, liked는 인증 사용자 본인의 좋아요 여부다.
비로그인 요청의 liked는 false다.
상세의 comments는 댓글·답글을 parentCommentId로 연결하는 기존 배열 구조를 유지한다.
각 항목은 id, parentCommentId(일반 댓글은 null), content, author, createdAt,
likes(전체 좋아요 수, 없으면 0), liked(본인 좋아요 여부, 비로그인은 false)를 포함한다.
댓글·답글의 좋아요 상태는 표시할 댓글 ID 기준으로 일괄 조회한다.

7.3 게시글 작성

POST /api/posts

Auth

Content-Type:

multipart/form-data

Parts:

request: application/json
images: file[] (optional)

request:

{
"category": "자유게시판",
"title": "게시글 제목",
"content": "게시글 내용"
}

Validation:

category 필수

category: 자유게시판 / 나눔게시판 / 택배게시판 / 민원게시판

title: 필수, 최대 100자

content: 필수, 최대 5,000자

이미지 최대 5장

이미지 1장당 5MB 이하, 0 byte 초과

MIME: image/jpeg, image/png, image/webp, image/gif

작성자와 생활관 정보는 인증 사용자 기준으로 서버에서 결정

7.4 게시글 수정

PATCH /api/posts/{postId}

Auth / 작성자만 가능

Content-Type 및 Validation은 작성과 동일.

이미지 유지/삭제/추가 방식은 구현 전에 다음 중 하나로 확정한다.

유지할 기존 이미지 ID + 새 파일 전달

이미지 전체 재설정

권장 Request JSON Part:

{
"category": "자유게시판",
"title": "수정 제목",
"content": "수정 내용",
"keepImageIds": [1, 2]
}

7.5 게시글 삭제

DELETE /api/posts/{postId}

Auth / 작성자만 가능

게시글 삭제 시 해당 게시글 댓글/답글 처리 정책은 ERD의 삭제 전략과 맞춘다.

7.6 게시글 좋아요

POST /api/posts/{postId}/likes

Auth

DELETE /api/posts/{postId}/likes

Auth

게시글 좋아요는 인증 사용자 본인 관계만 등록·취소한다. 반복 등록과 없는 관계의 취소는 성공한다.
비활성·삭제 게시글에는 등록할 수 없다.

8. 댓글 / 답글

8.1 댓글 작성

POST /api/posts/{postId}/comments

Auth

Request:

{
"content": "댓글 내용",
"anonymous": false
}

Validation:

필수

최대 1,000자

8.2 답글 작성

POST /api/comments/{commentId}/replies

Auth

Request:

{
"content": "답글 내용",
"anonymous": false
}

Validation: 댓글과 동일.

댓글과 답글의 anonymous는 생략/null이면 false(닉네임 공개)이다. 상세 조회 댓글 응답에도 anonymous를 포함한다. 익명인 경우 author.nickname은 "익명", author.major는 null로 반환하고 작성자 ID는 유지한다. 공개 댓글은 작성자의 닉네임과 학과를 반환한다. 내용 수정 시 익명 설정은 유지한다.

8.3 댓글/답글 수정

PATCH /api/comments/{commentId}

Auth / 작성자만 가능

답글도 Comment Entity로 통합할 경우 동일 Endpoint 사용 가능.

Request:

{
"content": "수정한 댓글"
}

8.4 댓글/답글 삭제

DELETE /api/comments/{commentId}

Auth / 작성자만 가능

댓글과 답글을 동일 테이블의 parentCommentId 구조로 구현하는 것을 권장한다. 프론트에서는 현재 comments[].replies[] 중첩 구조로 표시한다.

8.5 댓글 좋아요

POST /api/comments/{commentId}/likes

DELETE /api/comments/{commentId}/likes

Auth

댓글과 답글에 동일하게 적용한다. 인증 사용자 본인 관계만 등록·취소한다.
반복·동시 등록은 관계 1개로 유지하며 이미 없는 관계의 취소도 성공한다.
삭제·비활성 댓글 또는 삭제·비활성 게시글의 댓글에는 등록할 수 없다.
상태는 게시글 상세 comments 항목의 likes·liked로 확인한다.

8.6 내가 작성한 게시글

GET /api/users/me/posts

Auth

Query:

?page=0&size=6&sort=LATEST

8.7 내가 작성한 댓글/답글

GET /api/users/me/comments

Auth

Response item에는 원 게시글 이동을 위해 아래 정보가 필요하다.

{
"id": 100,
"content": "댓글 내용",
"createdAt": "2026-09-14T12:00:00",
"isReply": false,
"postId": 1,
"postTitle": "원 게시글 제목",
"category": "자유게시판"
}

9. 신고

9.1 신고 등록

POST /api/reports

Auth

Request:

{
"targetType": "POST",
"targetId": 1,
"reason": "욕설·비방",
"description": null
}

targetType:

USER
POST
COMMENT

현재 일반 사용자 UI에서 노출되는 대상:

POST

COMMENT

신고 사유:

욕설·비방
광고·홍보
음란성 콘텐츠
개인정보 노출
도배·스팸
기타

기타 선택 시 description 필수.

동일 사용자가 동일 대상에 중복 신고하지 않도록 제한 권장.

10. 채팅

현재 프론트는 sessionStorage로 채팅방/메시지를 유지하고 있으므로 실제 백엔드에서는 REST + WebSocket(STOMP) 구조를 권장한다.

10.1 채팅방 목록

GET /api/chat/rooms

Auth

Query:

?category=ALL&q=포근한구름

Category:

ALL
ROOMMATE
COMMUNITY

Response item:

{
"id": 15,
"category": "ROOMMATE",
"counterpart": {
"id": 2,
"nickname": "포근한구름",
"profileImageUrl": null,
"dormitory": "나래",
"admissionYear": "24",
"major": "소프트웨어융합전공",
"matchScore": 94,
"introduction": "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
"tags": ["비흡연", "아침형"]
},
"sourcePost": null,
"unreadCount": 2,
"pinned": false,
"muted": false,
"lastMessage": {
"text": "이번 주에 만나서 이야기 나눠요.",
"sentAt": "2026-09-14T12:00:00"
}
}

정렬:

고정 채팅방

최근 메시지 순

### 10.2 채팅방 생성 또는 기존 방 조회

POST /api/chat/rooms

Auth

룸메이트 프로필에서 시작:

```json
{
  "targetUserId": 2,
  "category": "ROOMMATE"
}
```

커뮤니티 게시글에서 시작:

```json
{
  "targetUserId": 7,
  "category": "COMMUNITY",
  "sourcePostId": 31,
  "sourceCommentId": null,
  "sourceFrom": "POST"
}
```

커뮤니티 댓글에서 시작:

```json
{
  "targetUserId": 7,
  "category": "COMMUNITY",
  "sourcePostId": 31,
  "sourceCommentId": 52,
  "sourceFrom": "COMMENT"
}
```

sourceFrom:

* POST
* COMMENT

필드 기준:

* `targetUserId`: 채팅을 시작할 상대 사용자 ID
* `category`: `ROOMMATE` 또는 `COMMUNITY`
* `sourcePostId`: 커뮤니티에서 시작한 경우 원 게시글 ID
* `sourceCommentId`: 댓글에서 시작한 경우 원 댓글 ID
* `sourceFrom`: 커뮤니티 채팅의 시작 위치 (`POST` 또는 `COMMENT`)

Validation:

* `ROOMMATE` 채팅에서는 `sourcePostId`, `sourceCommentId`, `sourceFrom`을 사용하지 않는다.
* `COMMUNITY` 채팅에서는 `sourceFrom`이 필수이다.
* `sourceFrom = POST`인 경우 `sourcePostId`가 필수이며 `sourceCommentId`는 사용하지 않는다.
* `sourceFrom = COMMENT`인 경우 `sourcePostId`, `sourceCommentId`가 모두 필수이다.
* `sourceCommentId`는 해당 `sourcePostId`에 속한 댓글이어야 한다.
* `targetUserId`는 채팅 시작 대상 게시글 또는 댓글의 작성자와 일치하는지 서버에서 검증한다.
* 자기 자신을 대상으로 채팅방을 생성할 수 없다.

방 재사용 및 출처 표시 규칙:

* `ROOMMATE`: 사용자 쌍 + category 기준을 유지한다.
* 일반 게시글: 사용자 쌍 + `COMMUNITY` 방을 재사용하고, 이 API를 통한 게시글 진입에서만 `sourcePost`를 최신 게시글로 갱신한다. 기존 메시지는 보존한다.
* 익명 게시글: 사용자 쌍 + `COMMUNITY` + 게시글 ID로 분리한다. 동일 익명 게시글은 재사용하고 다른 익명 게시글 및 일반 방과는 분리한다.
* 익명 여부는 실제 게시글/댓글을 서버에서 조회해 판단한다. 익명 댓글은 댓글 ID별로 분리하며, 일반 댓글 진입은 기존 재사용/출처 유지 동작을 따른다.
* 방 목록 조회, 메시지 조회·전송, 읽음 처리는 출처를 변경하지 않는다.
* 익명 방은 생성 당시의 익명성을 유지한다. 출처 게시글/댓글을 수정하거나 소프트 삭제해도 해제하지 않는다.
* 생성 응답과 방 목록의 익명 작성자 `counterpart`는 `id=null`, `nickname="익명"`, `tags=[]`, 그 외 프로필 필드 `null`이다. 작성자가 보는 상대 참여자의 일반 프로필은 유지한다.
* 메시지 목록 및 WebSocket 응답의 익명 작성자 `senderId`도 `null`이다. REST는 `senderType`으로 송수신을 구분한다. WebSocket은 `senderId`와 방의 `counterpart.id`를 비교하며, 익명 작성자는 둘 모두 `null`로 표현한다.
* UNIQUE 충돌 시 실패한 생성 트랜잭션을 롤백하고 새 쓰기 트랜잭션에서 출처 검증, 기존 방 조회, 최신 출처 갱신을 수행한다. 겹치는 일반 게시글 요청은 마지막으로 DB에 반영된 요청의 출처를 표시한다.

기존 데이터 적용 절차는 `migrations/README.md`를 참고한다.


10.3 메시지 목록

GET /api/chat/rooms/{roomId}/messages

Auth / 채팅방 참여자만 가능

Query:

?page=0&size=50&q=청소

Response item:

{
"id": 1001,
"senderId": 1,
"senderType": "ME",
"text": "안녕하세요!",
"sentAt": "2026-09-14T12:00:00",
"read": true
}

Validation:

메시지 최대 2,000자

10.4 메시지 전송 - WebSocket

권장 STOMP Contract:

SEND /pub/chat/rooms/{roomId}/messages
SUBSCRIBE /sub/chat/rooms/{roomId}

Payload:

{
"text": "안녕하세요!"
}

서버 Broadcast 예:

{
"id": 1002,
"roomId": 15,
"senderId": 1,
"text": "안녕하세요!",
"sentAt": "2026-09-14T12:01:00",
"read": false
}

초기 구현을 단순화해야 하면 REST POST /api/chat/rooms/{roomId}/messages로 먼저 구현한 뒤 WebSocket으로 교체할 수 있다.

10.5 읽음 처리

POST /api/chat/rooms/{roomId}/read

Auth

채팅방 진입 시 상대방이 보낸 미읽음 메시지를 읽음 처리한다.

10.6 채팅방 설정 변경

PATCH /api/chat/rooms/{roomId}/settings

Auth

Request 예:

{
"pinned": true,
"muted": false
}

프론트 기준 고정 채팅방 최대 2개.

10.7 채팅방 나가기

DELETE /api/chat/rooms/{roomId}/members/me

Auth

채팅방 자체 삭제가 아니라 현재 사용자의 채팅방 나가기 상태를 처리한다.

11. 캘린더

학교 일정과 기숙사 일정은 백엔드 크롤링으로 수집한다.

한경국립대학교 학사일정(https://www.hknu.ac.kr/kor/646/subview.do)은 매일 04:00(Asia/Seoul)에 현재 표시된 연간 일정을 수집한다.
기존 SCHOOL 시스템 카테고리가 없으면 수집 저장을 중단하며 카테고리를 자동 생성하지 않는다.
수집 일정은 ownerUser=null, sourceType=CRAWLED, sourceName="한경국립대학교 학사일정"인 종일 일정이다.
sourceType/sourceName/title/startDate/endDate가 모두 같으면 추가 저장하지 않는다. 원문의 제목이나 날짜가 변경되면 새 일정으로 저장되며 기존 일정은 자동 수정·삭제하지 않는다.
수집 전용 API는 제공하지 않으며 기존 일정 조회 API에서 조회한다.

SCHOOL = 학교 일정
DORMITORY = 기숙사 일정
CUSTOM-* = 사용자가 직접 생성한 카테고리

시스템 고정 카테고리는 SCHOOL(학교), DORMITORY(기숙사)뿐이며 ownerUser=null, isSystem=true이다. 사용자는 두 카테고리를 수정·삭제하거나 직접 일정을 등록할 수 없다. 학교 크롤링은 SCHOOL, 기숙사 크롤링은 DORMITORY를 사용한다.

현재 프론트 데모는 모든 일정을 수정/삭제할 수 있으므로 API 연동 시 버튼 제어가 필요하다.

11.1 일정 조회

GET /api/calendar/events

Query:

?from=2026-09-01
&to=2026-09-30
&categories=SCHOOL,DORMITORY,CUSTOM-example

Response item:

{
"id": 10,
"title": "생활관 정기 점검",
"startDate": "2026-09-08",
"endDate": "2026-09-08",
"startTime": "18:00",
"endTime": "19:00",
"category": "DORMITORY",
"categoryId": 2,
"description": "공용 공간과 생활관 시설을 점검합니다.",
"editable": false,
"source": "생활관 홈페이지"
}

기간 일정 Response item 예시:

{
"id": 11,
"title": "학사일정",
"startDate": "2026-03-03",
"endDate": "2026-03-20",
"startTime": null,
"endTime": null,
"category": "SCHOOL",
"categoryId": 1,
"description": "기간으로 저장하는 학사일정 예시",
"editable": false,
"source": "한경국립대학교 학사일정"
}

조회 기간과 일정 기간이 겹치면 반환한다(startDate <= to AND endDate >= from, 경계일 포함).

11.2 사용자 일정 등록

POST /api/calendar/events

Auth

Request:

{
"title": "룸메이트와 첫 만남",
"startDate": "2026-09-16",
"endDate": "2026-09-16",
"startTime": "12:00",
"endTime": "13:00",
"categoryId": 3,
"description": "학생회관 1층"
}

Validation:

제목 필수, 최대 80자

startDate, endDate 모두 필수, 각각 1900-01-01 ~ 2100-12-31

startDate <= endDate. 단일 일정은 startDate = endDate로 저장한다.

시작/종료 시간 HH:mm

종료시간 > 시작시간

categoryId는 본인 소유 CUSTOM-* 카테고리의 실제 ID를 사용한다. 예시의 categoryId=3은 사용자가 생성한 카테고리 ID이다.

description 최대 2,000자

시스템 소유 카테고리(SCHOOL, DORMITORY)로 직접 생성 금지

11.3 사용자 일정 수정

PATCH /api/calendar/events/{eventId}

Auth / 본인 일정만 가능

Request 예시(종료일만 변경):

{
"endDate": "2026-09-20"
}

PATCH에서 생략하거나 null인 필드는 기존 값을 유지한다. startDate/endDate 중 일부만 전달해도 기존 값과 병합한 뒤 startDate <= endDate를 검증한다.

전달된 값의 Validation은 등록과 동일하며 필수 입력 제약은 적용하지 않는다.

등록/수정 Response는 11.1의 Response item과 동일하게 startDate, endDate를 반환한다.

11.4 사용자 일정 삭제

DELETE /api/calendar/events/{eventId}

Auth / 본인 일정만 가능

학교/기숙사 크롤링 일정 삭제 금지.

11.5 캘린더 카테고리 설정 조회

GET /api/calendar/categories

Auth

Response:

[
{
"id": 1,
"code": "SCHOOL",
"label": "학교",
"color": "#006A65",
"fixed": true,
"enabled": true
},
{
"id": 4,
"code": "CUSTOM-example",
"label": "개인 일정",
"color": "#123456",
"fixed": false,
"enabled": true
}
]

11.6 캘린더 카테고리 설정 저장

PUT /api/calendar/categories

Auth

Request:

[
{
"id": 1,
"label": "학교",
"color": "#006A65",
"enabled": true
},
{
"id": 4,
"label": "내 일정",
"color": "#123456",
"enabled": true
}
]

Validation:

id는 CALENDAR_CATEGORY.category_id의 BIGINT PK 사용

label 1 ~ 30자

label 중복 금지

색상 #RRGGBB

SCHOOL, DORMITORY 이름 변경 금지

11.7 사용자 카테고리 생성

POST /api/calendar/categories/custom

Auth

Request:

{
"label": "스터디",
"color": "#123456"
}

사용자 소유 CUSTOM-\* 카테고리를 생성한다.

생성 시 현재 사용자에 대한 CALENDAR_CATEGORY_SETTING도 함께 생성한다.

11.8 사용자 카테고리 수정

PATCH /api/calendar/categories/{categoryId}

Auth / 본인 소유 사용자 카테고리만 가능

Request:

{
"label": "전공 스터디",
"color": "#654321",
"enabled": true
}

11.9 사용자 카테고리 삭제

DELETE /api/calendar/categories/{categoryId}

Auth / 본인 소유 사용자 카테고리만 가능

SCHOOL, DORMITORY 시스템 카테고리는 삭제할 수 없다. 그 외 카테고리는 본인 소유 CUSTOM-*만 수정·삭제할 수 있다.

해당 카테고리를 사용하는 일정이 존재하면 삭제를 제한한다.

12. 기숙사 예상 점수 계산기

점수 조건과 계산식은 백엔드에서 관리한다. 관리자 페이지에서는 계산 기준을 수정하지 않는다.

12.1 예상 점수 계산

POST /api/dormitory-scores/calculate

Request:

{
"province": "경기도",
"city": "평택시",
"grade": 4.13,
"priorityType": "NONE"
}

priorityType은 백엔드 최종 기준에 맞춰 Enum 확정.

Response:

{
"totalScore": 78,
"gradeScore": 50,
"regionScore": 20,
"additionalScore": 8,
"regionLabel": "경기도 · 평택시",
"message": "충분히 경쟁력 있는 예상 점수예요"
}

현재 프론트에서는 성적/지역/가산점을 직접 계산하고 있으나 실제 연동 시 계산식을 프론트에서 제거하고 이 API 응답을 사용한다.

13. 관리자

모든 관리자 API는 ADMIN 권한 필수.

13.1 대시보드

GET /api/admin/dashboard

Response:

{
"stats": {
"totalMembers": 100,
"totalPosts": 230,
"totalComments": 910,
"totalReports": 18,
"pendingReports": 5,
"schoolSchedules": 28,
"dormitorySchedules": 16
},
"recentMembers": [],
"recentPosts": [],
"recentReports": [],
"recentSchoolSchedules": [],
"recentDormitorySchedules": []
}

프론트 기준 최근 항목은 각 3개 표시.

13.2 회원 목록

GET /api/admin/members

Query:

?page=0
&size=6
&q=roomie
&status=ACTIVE
&dormitory=나래

Status:

ACTIVE
SUSPENDED
WITHDRAWN

검색 대상:

닉네임

이메일

13.3 회원 상세

GET /api/admin/members/{memberId}

Response:

{
"id": 1001,
"nickname": "포근한구름",
"schoolEmail": "roomie01@hknu.ac.kr",
"gender": "여자",
"dormitory": "나래",
"faculty": "컴퓨터응용수학부",
"major": "소프트웨어융합전공",
"admissionYear": "24",
"grade": "3학년",
"mbti": "INFJ",
"createdAt": "2026-09-14",
"status": "ACTIVE",
"activity": {
"postCount": 4,
"commentCount": 12,
"reportsReceived": 1
}
}

13.4 회원 상태 변경

PATCH /api/admin/members/{memberId}/status

Request:

{
"status": "SUSPENDED"
}

지원:

ACTIVE 이용 정지 해제
SUSPENDED 이용 정지
WITHDRAWN 탈퇴 처리

탈퇴 회원을 다시 정상/정지 상태로 변경하지 않도록 제한.

13.5 관리자 게시글 목록

GET /api/admin/posts

Query:

?page=0
&size=6
&q=검색어
&category=자유게시판
&reported=true
&sort=LATEST

검색 대상:

제목

내용

작성자

Sort:

LATEST
VIEWS
LIKES

13.6 관리자 게시글 상세

GET /api/admin/posts/{postId}

13.7 관리자 게시글 삭제

DELETE /api/admin/posts/{postId}

게시글과 연결된 댓글 처리 정책은 ERD 삭제 전략과 일치시킨다.

13.8 관리자 댓글 목록

GET /api/admin/comments

Query:

?page=0&size=6&q=검색어&reported=true

검색 대상:

댓글 내용

작성자

13.9 관리자 댓글 상세

GET /api/admin/comments/{commentId}

13.10 관리자 댓글 삭제

DELETE /api/admin/comments/{commentId}

13.11 신고 목록

GET /api/admin/reports

Query:

?page=0
&size=6
&q=검색어
&type=POST
&status=PENDING

Type:

USER
POST
COMMENT

Status:

PENDING
COMPLETED
REJECTED

검색 대상:

신고 번호

대상 내용

신고자

신고 대상 사용자

신고 사유

13.12 신고 상세

GET /api/admin/reports/{reportId}

Response 주요 필드:

{
"id": 4001,
"type": "POST",
"targetId": 2001,
"targetUser": {
"id": 1001,
"nickname": "포근한구름"
},
"targetContent": "신고 대상 내용",
"reporter": {
"id": 1007,
"nickname": "새벽별"
},
"reason": "욕설·비방",
"description": "상세 신고 내용",
"createdAt": "2026-09-14T20:10:00",
"status": "PENDING"
}

13.13 신고 처리

PATCH /api/admin/reports/{reportId}

프론트의 네 가지 처리 버튼을 하나의 Action API로 묶는다.

Request:

{
"action": "SUSPEND_USER"
}

Action:

COMPLETE 신고만 처리 완료
REJECT 신고 반려
SUSPEND_USER 신고 대상 회원 이용 정지 + 신고 처리 완료
DELETE_TARGET 신고 대상 게시글/댓글 삭제 + 신고 처리 완료

처리 대기(PENDING) 상태에서만 Action 수행.

13.14 일정 크롤링 상태 조회

GET /api/admin/schedule-crawls/status

Response:

[
{
"category": "SCHOOL",
"status": "COMPLETED",
"lastRunAt": "2026-09-14T06:00:00",
"recentCollectedCount": 3,
"totalCollectedCount": 28,
"lastCompletedAt": "2026-09-14T06:00:00",
"lastFailure": {
"occurredAt": "2026-09-13T05:00:00",
"message": "응답 지연"
}
}
]

Crawl status:

RUNNING
COMPLETED
FAILED

현재 관리자 프론트에는 수동 크롤링 실행 버튼이 없으므로 실행 API는 문서에서 제외한다.
크롤링 실행 상태/실패 이력은 SCHEDULE_CRAWL_RUN, 실제 수집 일정은 CALENDAR_EVENT를 기준으로 조회한다.

13.15 수집 일정 목록

GET /api/admin/crawled-schedules

Query:

?page=0&size=6&q=검색어&category=SCHOOL

검색 대상:

일정 제목

일정 내용

일정 출처

Response item:

{
"id": 5001,
"title": "2학기 수강 정정",
"date": "2026-09-15",
"content": "...",
"category": "SCHOOL",
"source": "학교 홈페이지 · 학사일정",
"collectedAt": "2026-09-14T06:00:00"
}

13.16 수집 일정 상세

GET /api/admin/crawled-schedules/{scheduleId}

관리자가 수집 내용을 확인만 한다.

관리자 직접 등록 없음

관리자 직접 수정 없음

관리자 직접 삭제 없음

14. 메인페이지에서 재사용하는 API

메인페이지 전용 API를 별도로 만들기보다 기존 API에 size를 작게 전달해 재사용한다.

추천 룸메이트

GET /api/roommates/recommended?page=0&size=3

최근/인기 게시글

GET /api/posts?page=0&size=3&sort=LATEST

필요하면 카테고리 Query 추가.

캘린더

GET /api/calendar/events?from={월 시작일}&to={월 종료일}&categories=SCHOOL,DORMITORY

기숙사 예상 점수

POST /api/dormitory-scores/calculate

15. 설문 필드 기준

15.1 내 프로필 설문

수면 패턴

key

형식

현재 선택지

sleepTime

single

22시 이전 / 22시 ~ 23시 / 23시 ~ 24시 / 24시 ~ 01시 / 01시 ~ 02시 / 02시 ~ 03시 / 03시 이후

22시 이전은 신규 선택지다. 기존 22시 ~ 23시를 보존하며 두 값을 변환하거나 같은 시간대로 취급하지 않는다.
선호 설문 bedtime과 룸메찾기 sleepTime 필터에서도 두 값을 별도로 선택한다.

wakeTime

single

07시 이전 / 07시 ~ 08시 / 08시 ~ 09시 / 09시 ~ 10시 / 10시 이후 / 수업 시간에 따라 유동적

sleepTalking

single

없음 / 적음 / 보통 / 심함

snoring

single

없음 / 적음 / 보통 / 심함

teethGrinding

single

없음 / 적음 / 보통 / 심함

sleepSensitivity

single

밝음 (소리에 잘 깨요) / 보통 / 어두움 (잘 깨지 않아요)

생활 습관

key

형식

현재 선택지

indoorEating

single

가능 / 불가능 / 냄새 안나는 음식 가능

smoking

single

비흡연 / 일반담배 / 전자담배

lightingPreference

single

밝음 선호 / 어두움 선호 / 상관없음

phoneCall

single

항상 밖에서 / 방에서는 짧게만 / 상관없음

mediaSound

single

항상 이어폰 사용해주세요 / 공부·과제할 때 아니면 상관없음 / 소리만 작게 해주세요 / 자유롭게 하세요

showerTime

single

아침 / 저녁 / 유동적

showerDuration

single

10분 / 20분 / 30분 / 40분 / 그 이상

hairDryer

single

사용 / 미사용

bugHandling

single

잘 잡음 / 못 잡음 / 작은 벌레는 가능

청소 및 공동생활

key

형식

현재 선택지

cleaningFrequency

single

안함 / 주 1회 / 주 2회 / 주 3회 / 주 4회 / 주 5회 / 매일 / 유동적

cleaningStyle

single

같이 청소 / 각자 영역만 청소 / 상황에 따라 협의

dormStayDays

multi

월 / 화 / 수 / 목 / 금 / 토 / 일

mealPlan

multi

조식 / 중식 / 석식 / 신청 안 함

meal

single

같이 먹고 싶음 / 각자 먹고 싶음 / 시간 될 때만 같이 먹고 싶음 / 상관없음

roommateInteraction

single

친하게 지내고 싶음 / 적당히 대화 / 필요한 대화만

temperaturePreference

single

추위 타요 / 더위 타요 / 보통

scentProductUse

single

자주 사용 / 가끔 사용 / 사용 안 함 / 상관없음

desktopUse

single

사용해요 / 사용하지 않아요 / 특정 시간 제외하면 상관없음

sharedItems

single

적극적으로 공유 / 일부만 공유 / 각자 사용

mealPlan의 신청 안 함은 다른 식사와 동시 선택 불가.

15.2 선호 룸메이트 설문

key

형식

현재 선택지

grade

multi

1학년 / 2학년 / 3학년 / 4학년 / 5학년 / 상관없음

age

multi

연하 / 동갑 / 연상 / 상관없음

department

single

같은 전공 / 같은 학부의 다른 전공 / 다른 학부 / 상관없음

bedtime

multi

프로필 취침시간 선택지 + 상관없음

wakeTime

multi

프로필 기상시간 선택지 + 상관없음

sleepTalking

single

없음만 가능 / 적음까지 괜찮음 / 보통까지 괜찮음 / 상관없음

snoring

single

없음만 가능 / 적음까지 괜찮음 / 보통까지 괜찮음 / 상관없음

teethGrinding

single

없음만 가능 / 적음까지 괜찮음 / 보통까지 괜찮음 / 상관없음

smoking

single

비흡연자만 / 전자담배까지 허용 / 상관없음

lightingPreference

single

밝음 선호 / 어두움 선호 / 상관없음

showerTime

single

아침 / 저녁 / 상관없음

showerDuration

single

10분 이하 / 20분 이하 / 30분 이하 / 40분 이하 / 상관없음

cleaningFrequency

multi

안함 / 주 1회 / 주 2회 / 주 3회 / 주 4회 / 주 5회 / 매일 / 유동적 / 상관없음

cleaningStyle

single

같이 청소하는 사람 / 각자 영역만 청소하는 사람 / 상황에 따라 협의하는 사람 / 상관없음

interaction

single

친하게 지내고 싶은 사람 / 적당히 대화하고 싶은 사람 / 필요한 대화만 하고 싶은 사람 / 상관없음

meal

single

같이 먹고 싶음 / 각자 먹고 싶음 / 시간 될 때만 같이 먹고 싶음 / 상관없음

sharedItems

single

적극적으로 공유하는 사람 / 일부만 공유하는 사람 / 각자 사용하는 사람 / 상관없음

temperature

single

추위를 타는 사람 / 더위를 타는 사람 / 보통인 사람 / 상관없음

모든 항목별 weights[key]는 1 ~ 5.

16. 현재 프론트 기준 API가 필요 없는 데이터

아래 데이터는 현재 프론트에 정적 상수로 존재하므로 초기 백엔드 구현에서는 API로 만들 필요가 없다.

학부/전공 선택지

대한민국 지역 목록 (koreaRegions.js)

게시판 카테고리 목록

설문 선택지 자체

MBTI 선택지

향후 관리자 수정 기능이나 서버 중앙 관리가 필요할 때 Reference API로 분리한다.

17. 구현 전에 반드시 확인할 프론트 불일치

17.1 생활관 명칭 불일치

현재 파일별로 다음 값이 혼재한다.

회원가입: 나래 / 호연 / 비봉 / 창조

마이페이지 수정: 나래 / 호연 / 비봉 / 창조

커뮤니티/관리자: 나래관 / 호연관 / 창조관 / 비봉관

채팅 샘플: 비봉

API/DB 구현 전에 하나로 통일해야 한다.

권장 방식:

DB/API 내부 코드: NARAE / HOYEON / BIBONG / CHANGJO

프론트 표시명은 별도 매핑

17.2 내 프로필 설문 필드 통일 기준

hairDryer, bugHandling을 실제 프로필 설문 항목으로 유지한다.

API 4.1/4.2에 두 필드를 포함한다.

API 15.1에 선택지를 정의한다.

ERD PROFILE_SURVEY에 hair_dryer_use, bug_handling을 저장한다.

17.3 프로필 한마디 / 태그 / 프로필 이미지

룸메이트 카드와 상세 화면에서는 아래 값을 사용한다.

introduction
profileImageUrl
tags

하지만 현재 마이페이지 계정 화면에는 이 값을 수정하는 UI가 없다.

구현 전에 다음을 결정한다.

introduction 수정 UI는 마이페이지 내 설문의 마지막 선택 항목으로 제공한다(4.2 참고). profileImageUrl 수정 UI는 별도 검토한다.

tags는 별도 저장하지 않고 프로필/설문 값에서 서버가 생성

17.4 커뮤니티 → 채팅 연결

CommunityPostPage는 Router state에 communityChat을 전달하지만 현재 ChatPage는 roommateProfile만 처리하고 communityChat을 읽지 않는다.

백엔드의 POST /api/chat/rooms는 COMMUNITY 채팅을 지원하도록 명세했지만, 실제 연동 시 프론트에서도 Community 채팅방 생성/선택 로직을 연결해야 한다.

17.5 학교/기숙사 일정 수정 기준

현재 Calendar UI는 예시 데이터상 학교/기숙사 일정도 수정/삭제할 수 있다.

실제 계획은 학교/기숙사 일정을 크롤링으로 가져오는 것이므로 서버 연동 후에는:

SCHOOL, DORMITORY: 조회 전용

본인 소유 CUSTOM-* 사용자 카테고리: 사용자 CRUD 가능

구조로 맞추는 것을 권장한다.

18. 백엔드 구현 권장 순서

API 전체를 한 번에 구현하지 않고 다음 순서로 진행한다.

1. global 최소 공통 설정
2. auth + user
   - User
   - UserProfile

3. survey
   - ProfileSurvey
   - RoommatePreference

4. matching
   - 룸메이트 조회
   - 매칭
   - 관심 프로필(ProfileLike)

5. community
   - 게시글
   - 댓글
   - PostLike
   - CommentLike
   - 신고
6. calendar + crawler
7. chat
8. admin
9. dormitory score calculator
10. 프론트 전체 API 연동 및 통합 테스트

각 도메인 구현 전 이 API.md, ERD.md, 해당 프론트 코드를 함께 확인한다.
