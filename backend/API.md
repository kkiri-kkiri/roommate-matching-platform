API 명세서

상태: 현재 구현 대조본 (2026-10-14, 문서만 수정)
기준: 실제 Controller / DTO / Service / SecurityConfig와 현재 프론트 호출
목적: 도메인별 배포 검증의 기준 계약. 구현 확인과 실행 검증을 구분한다.
수정 기준: 과거 설계·누락된 명세 정리, 미구현 기능·미해결 결함 분리

0. 문서 사용 기준

인증·회원정보·설문·룸메이트·커뮤니티·게시글/댓글 출처 채팅·캘린더·관리자는 실제 API를 호출한다. 기숙사 점수는 MainPage의 프론트 내부 계산이며 서버 계산 API와 기숙사 일정 수집기는 미구현이다. 인증 토큰·화면 상태 저장을 API 미연동의 근거로 삼지 않는다.

19장은 현재 제공 중인 REST 반환 타입·DTO 선언의 상세 기준이다. 문서의 예시는 전체 필드 표가 아니므로 19장 및 아래 nullable·검증 규칙을 함께 따른다. 운영 설정과 테스트 격리(#3/#4)는 코드로 보완했으며 실제 운영 검증은 DEPLOYMENT.md를 따른다. 다른 도메인의 미해결 항목은 별도 검토 대상이다. 이전 테스트 성공 보고를 현재 코드의 실행 검증으로 사용하지 않는다.

저장 구조는 실제 Entity와 대조한 ERD.md를 기준으로 한다. 운영 DB의 실제 DDL 일치는 별도 검증 대상이다.

화면 동작과 요청/응답 계약은 확정 요구사항과 현재 코드·호출부를 비교하여 이 문서를 사용한다. 예정 설계와 미해결 결함을 구현 보장으로 취급하지 않는다.

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

현재 REST 성공은 생성·변경·삭제를 포함해 HTTP 200이다. Void 반환은 data=null이며 201/204로 임의 변경하지 않는다. success/message/data는 공통이며 성공의 errorCode/fieldErrors는 생략한다. DTO 필드 검증은 400 VALIDATION_ERROR와 fieldErrors를 제공할 수 있지만 서비스·객체·메서드 검증에서는 fieldErrors가 없을 수 있다. 인증 401 UNAUTHORIZED, 권한·소유권 403 FORBIDDEN, 일반 대상/잘못된 요청 400 INVALID_REQUEST, 가입 충돌 409, 이메일 재전송 제한 429, 내부 오류 500이다. 상세 ErrorCode는 19장을 따른다. 잘못된 JSON·타입 등 MVC 오류는 공통 envelope를 사용하되 405/413/415 등 실제 HTTP 상태를 유지한다. 공개되지 않은 경로는 SecurityConfig에서 먼저 차단될 수 있다. STOMP 제어/ERROR 프레임은 REST envelope가 아니다.

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

목표 표시 기준은 Asia/Seoul이다. 현재 Jackson 설정과 이메일 인증/재설정 Clock, 학사 수집·스케줄은 서울 시간대를 지정하지만 여러 Entity의 LocalDateTime.now는 JVM 기본 시간대를 따른다. JVM·DB 전체의 시간대 통일은 현재 보장되지 않으며 배포 환경에서 확인해야 한다. DATETIME과 LocalDateTime 응답에는 UTC offset이 없다.

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
로그인 응답에도 같은 fallback을 사용한다. 프로필이 없는 관리자 계정에 프로필을 임의 생성하지 않는다.

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

nickname은 한글·영문·숫자만 허용하며 2~8자이다. 비밀번호는 영문·숫자·특수문자를 포함하고 공백을 금지한다. gender는 남자/여자, MBTI는 네 축의 정의된 값만 허용한다.
major 요청 문자열 `세부전공 없음`은 major_id=NULL로 저장한다. 그 외 전공은 선택한 활성 학부에 속한 활성 전공이어야 한다. dormitory/faculty/major의 최대 길이는 30/100/150자이며 학교 이메일 아이디는 최대 64자다.

GET /api/auth/signup/availability (Public)

Query: schoolEmail, nickname

두 query는 선택이며 생략하면 빈 문자열이다. 현재 availability 조회 자체에는 가입 DTO의 형식 검증을 적용하지 않고 존재 여부만 확인한다. 이 응답이 실제 가입 검증 성공을 보장하지 않는다.

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

존재하지 않거나 비활성인 계정도 현재 sent=true를 반환한다. 실제 토큰/메일은 ACTIVE 계정에만 발급한다. SMTP 발송 실패는 500 INTERNAL_SERVER_ERROR이며 토큰 저장을 롤백한다.

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

2.9 Auth 요청 제한 및 메일 장애 응답

- 이메일 인증 검증: 기본 이메일당 10회/60초·IP당 60회/60초. 실패는 이메일 5회·IP 20회/300초,
  상한에 도달하면 해당 실패 bucket을 300초 차단한다. 진행 중인 검증도 예약 횟수에 포함한다.
- 로그인: 기본 계정당 20회/60초·IP당 120회/60초. 실패는 계정 10회·IP 30회/600초,
  상한에 도달하면 60초 차단한다. 실패는 401 UNAUTHORIZED, 제한은 429 AUTH_REQUEST_LIMITED이다.
  성공 시 계정 실패 횟수만 초기화한다. IP 누적 실패는 성공으로 지우지 않는다.
  차단 중 요청으로 차단을 연장하지 않으며 영구 계정 잠금을 도입하지 않는다.
- 로그인 이메일 확인도 별도 이메일/IP 요청 bucket에 로그인 요청 기본 한도를 적용한다.
  기존 registered 공개 계약은 유지하므로 계정 존재 여부가 비공개인 API로 간주하지 않는다.
- 인증번호 발송·비밀번호 재설정 요청은 각각 이메일당 5회/3600초·IP당 20회/3600초.
  두 기능의 이메일 cooldown은 기본 60초다. 인증번호 재전송 cooldown 오류는 기존
  EMAIL_VERIFICATION_COOLDOWN(429)을 유지한다. 모든 auth 메일 합계는 기본 100회/3600초다.
- 제한 응답은 기존 ApiResponse envelope와 Retry-After(초)를 사용한다. 정상 성공 경로/DTO/HTTP 200은 유지한다.
  로그인·재설정의 기존 회원 제한은 DB에서 조회한 user_id로 통합한다. 비등록 계정 및 가입 전 인증은
  각각 USER.school_email 및 EMAIL_VERIFICATION.email과 같은 Collation의 DB UNIQUE 식별자로 통합한다.
  대소문자·악센트·Unicode 변형은 해당 DB가 동일 이메일로 비교하는 경우에만 같은 제한을 공유한다.
  Java에서 임의 정규화하거나 이메일 입력 정책/기존 Collation을 바꾸지 않는다. IP는 직접 연결 주소 또는 명시된 신뢰 프록시 체인에서 얻는다.
- 재설정은 존재하지 않거나 비활성인 계정에도 기존 sent=true를 반환하며 동일 요청/cooldown 제한을 적용한다.
  실제 메일이 필요한 계정만 전체 메일 quota를 소비한다. 기존 SMTP 500 응답은 유지한다.
  비등록 계정의 응답 시간과 SMTP 장애 응답까지 완전히 동일하게 만드는 정책은 도입하지 않았다.
- 제한 예약·실패 갱신은 독립 트랜잭션으로 남는다. SMTP 실패 시 인증/재설정 token 저장은 롤백하지만,
  요청/cooldown/발송 quota는 환불하지 않는다. 일부 전송 후 타임아웃의 중복 발송과 실패 반복 악용을 방지한다.
  JDBC 제한 저장의 MySQL 1213/1205만 설정된 횟수 내에서 새 트랜잭션으로 재시도한다.
  인증 업무와 SMTP는 자동 재실행하지 않으며, DB 오류를 정상 제한 429로 변환하지 않는다.
- SMTP 로그는 authentication/connection/timeout/configuration/other 분류와 서버 생성 requestId만 포함한다.
  auth 응답의 X-Request-ID로 추적하며 원문 예외/수신자/본문/비밀번호/코드/토큰을 기록하지 않는다.
- 프론트 auth 요청은 기본 15초, 메일 요청은 30초다. 네트워크/시간 초과는 처리 결과가 불확실함을 안내하고
  버튼을 복구한다. 쓰기 요청은 자동 재시도하지 않는다. 사용자는 기존 메일/처리 결과를 확인한 뒤 다시 요청한다.
- 비밀번호 변경/재설정 후 기존 JWT·다른 미사용 재설정 토큰 폐기는 별도 정책 결정 사항으로 유지한다.

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

생략/null은 기존 값을 유지한다. nickname은 가입과 같은 2~8자·한글/영문/숫자 제한이다. 변경 학부·전공은 활성 기준정보와 소속 일치를 검증한다. 학부만 변경해 기존 전공과 불일치하면 거절한다. 전공을 해제하려면 가입과 동일하게 major="세부전공 없음"을 명시한다. 이 경우 major를 null로 저장하며 세부전공 없는 학부로도 변경할 수 있다. major=null은 계속 유지 의미다. MBTI 일부 축은 기존 값과 합친다. 중복 nickname(동시 변경의 UNIQUE 충돌 포함)/잘못된 기준정보 등 서비스 검증 실패는 400 INVALID_REQUEST이다. Response data는 갱신된 3.1의 UserResponse다.

회원정보·비밀번호 변경은 USER 잠금 획득 후 ACTIVE 상태를 다시 확인한다. 정지/탈퇴된 계정의 변경은 401 UNAUTHORIZED다. 잠금 충돌/시간 초과는 409 ACCOUNT_UPDATE_CONFLICT로 응답한다.

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

영문·숫자·특수문자를 포함하고 공백을 금지한다. BCrypt를 사용하는 가입·변경·재설정의 비밀번호는 UTF-8 기준 최대 72바이트이며 확인 비밀번호에도 적용한다. 로그인/현재 비밀번호도 같은 바이트 상한을 검사한다. 초과 시 400 VALIDATION_ERROR로 처리하고 잘라 저장하지 않는다. 변경/재설정 성공의 data는 null이다.

변경·재설정·관리자 상태 변경은 동일 USER 행을 비관적으로 잠근다. 재설정은 토큰 소유자 ID만 먼저 조회한 뒤 USER → PASSWORD_RESET_TOKEN 순으로 잠그고 계정 상태·토큰 사용/만료 여부를 검증한다. 비밀번호와 토큰 사용 처리는 함께 커밋/롤백한다.

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
현재 요청/응답은 ProfileSurveyRequest 기준 23개 필드다. 과거의 hairDryer/bugHandling은 현재 계약에 포함하지 않으며 저장 컬럼을 추가하지 않는다.

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
- 복수 선택은 하나라도 일치하면 충족한다. 잠꼬대·코골이·이갈이는 없음 <적음 <보통 <심함 순서로 허용 상한을 비교한다.
- 샤워 소요시간은 선호 상한 이하일 때 충족한다. “그 이상”은 40분 이하 조건도 충족하지 않는다.
- “비흡연자만”은 비흡연만, “전자담배까지 허용”은 비흡연과 전자담배를 허용한다.
- 학부·전공은 ID로 비교한다. “같은 전공”과 “같은 학부의 다른 전공”은 양쪽 전공이 있어야 충족한다. 나이는 출생연도로 연하·동갑·연상을 비교하며 학년은 상대 학년과 일치 여부를 비교한다.
- 청소·교류·공용물품·온도는 선호 문구와 실제 답변의 의미를 매핑한다. 그 외 항목은 선택값 일치를 사용하며 “유동적”은 별도 선택값이다.
- 선호 미작성, 계산 입력 누락, 적용 가중치 합이 0인 경우 점수는 null이며 추천하지 않는다. 임시 점수를 저장하지 않는다.
- 프로필 설문 커밋 후 다른 사용자 → 나, 선호 설문 커밋 후 나 → 다른 사용자를 갱신한다. 학부·전공 변경은 양방향, 학년 변경은 다른 사용자 → 나를 갱신한다. 저장 응답은 계산 완료를 기다리지 않는다. 전용 작업 큐(최대 100개 대기, 작업 스레드 최대 2개)에서 처리하고 대상 ID를 100명씩 조회·계산한다. 각 계산 트랜잭션의 USER 잠금은 대상 100명과 기준 사용자 1명 이내이며 ID 오름차순이다.
- 갱신 실패·큐 포화는 로그에 기록한다. 작업 큐는 메모리 기반이며 프로세스 종료로 사라진 작업도 전체·관심 목록의 해당 페이지, 상세의 해당 상대, 추천의 해당 요청자 후보 점수를 조회 전에 다시 준비하여 복구한다. 추천 후보 재계산도 100명씩 별도 트랜잭션에서 수행한다.
- 공개 대상은 자기 자신을 제외한 ACTIVE 사용자 중 USER_PROFILE과 completed=true인 PROFILE_SURVEY가 있는 사용자다. 필수 프로필 값이 없는 대상도 제외하며 major, introduction, profileImageUrl의 null은 허용한다.
- 제외 조건과 설문 필터는 페이징·count 이전 DB 쿼리에 적용한다. 카드 프로필·설문·관심 여부 및 계산 입력은 대상 ID 기준 일괄 조회한다.
- 전체·관심 목록은 점수 준비 뒤 공개 조건으로 페이지와 count를 다시 조회한다. 최종 페이지 선택·count·공개 프로필 재조회·DTO 구성은 하나의 REPEATABLE READ 읽기 트랜잭션에서 수행한다. 추천도 점수 준비 뒤 같은 방식으로 조회한다. 마지막 페이지가 사라지면 요청 page를 유지한 빈 content와 최신 totalElements/totalPages를 반환하며 프론트가 이전 페이지로 이동한다.
- 공개 여부는 최종 읽기 스냅샷 기준이다. 그 스냅샷 이후 커밋되는 계정 상태 변경까지 차단하는 원자적 전송을 보장하지 않는다. 전체·관심 목록에서 준비한 페이지가 이동하면 새로 포함된 대상의 점수는 기존 저장값 또는 null일 수 있으며 다음 조회/비동기 갱신으로 복구한다.

6.1 전체 프로필 조회

GET /api/roommates

Public / 인증 선택. 게스트 matchScore=null, liked=false. 유효하지 않은 Bearer를 보낸 요청은 401이며 프론트는 공개 GET만 토큰 제거 후 비로그인으로 한 번 재시도한다.

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

추천 기준 matchScore>= 60

요청당 최대 9개 표시. size가 9보다 크면 9로 제한한다.
명시적 sort는 matchScore,desc만 허용한다.

선택 설문 필터는 전체 목록과 같은 RoommateFilterRequest 키/AND·OR 규칙을 지원한다.
필터가 있으면 먼저 공개 가능한 matchScore>=60 상위 9명을 확정한 뒤, 해당 ID에 한정한 DB 쿼리로 필터를 적용한다. 순위 10위 이하에서 보충하지 않는다. 필터 결과(최대 9명)에 page/size를 적용하며 totalElements/totalPages도 이 결과 기준이다. 필터가 없으면 기존 추천 페이징·count 계약을 유지한다.
프론트 추천 탭은 필터를 포함한 추천 요청 한 번으로 결과를 표시하며 전체 목록의 모든 페이지를 조회하지 않는다. 기존 요청자의 점수 준비는 요청당 한 번 수행하며 후보 수에 따른 계산 비용은 남는다.

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
- ai.api-key, ai.base-url, ai.model은 AI_API_KEY/AI_BASE_URL/AI_MODEL 환경 설정에 의존한다. 운영 모델·Gateway 접근·타임아웃·응답 형식은 배포 환경 확인 대상이다.
- POST {AI_BASE_URL}/chat/completions/ (Bearer 인증), choices[0].message.content 사용.
- 연결 제한 5초, 응답 제한 45초. 자동 재시도 없음. 인증키와 Gateway 오류 본문은 로그/응답에 노출하지 않는다.
- 400 INVALID_REQUEST: 자기 자신, 유효하지 않거나 공개 불가능한 대상.
- 401 UNAUTHORIZED: 인증 없음/실패.
- 409 MATCH_ANALYSIS_UNAVAILABLE: 선호 미작성 등으로 점수 계산 불가.
- 503 AI_GATEWAY_UNAVAILABLE: 설정 누락, Gateway HTTP 오류, 연결/시간 초과.
- 502 AI_GATEWAY_INVALID_RESPONSE: 잘못된 JSON, choices/content 누락, 빈 문자열 또는 문자열이 아닌 content.
- 429 AI_ANALYSIS_RATE_LIMITED: 인증 사용자별 기본 60초 동안 12회 초과. 최초 승인부터 고정 window이며 auth와 다른 키를 사용해 기존 MySQL auth_limit_bucket에서 원자적으로 예약한다. 업무 실패·Gateway 실패·용량 초과도 승인된 호출량을 환불하지 않는다. 제한 저장 장애 시 우회하지 않는다.
- 503 AI_ANALYSIS_BUSY: 인스턴스당 동시 분석 기본 2건 초과. 대기 없이 거절하며 예외/타임아웃에도 permit을 반환한다. Gateway 호출 중 DB 트랜잭션/잠금을 유지하지 않는다.
- 설정: ai.analysis.max-requests / window-seconds / max-concurrent (환경변수 AI_ANALYSIS_MAX_REQUESTS / AI_ANALYSIS_WINDOW_SECONDS / AI_ANALYSIS_MAX_CONCURRENT). 기본값 12/60/2는 추천 카드 9개 확인과 소수의 재시도를 허용하면서 외부 호출 비용을 제한하는 초기값이며 운영 부하에 맞춰 조정한다.
- 사용자별 quota는 동일 DB·동일 설정을 쓰는 다중 인스턴스에서 공유된다. 동시 실행 semaphore는 인스턴스별이므로 N개 인스턴스의 합산 상한은 N×max-concurrent다. 전체 배포 공통 동시 상한이 필요하면 Gateway/기존 ingress에서 별도 제한해야 한다. 신규 Redis는 추가하지 않았다.

6.3 룸메이트 상세 프로필

GET /api/roommates/{userId}

Public / 인증 선택. 게스트 matchScore=null, liked=false. 추천 조회·관심 등록·AI 분석은 인증 필수다.

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

중복 등록 요청은 현재 멱등 처리한다.

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

7·8번 보완(2026-10-19): 공개·검색·내 게시글·내 댓글/답글 및 관리자 게시글·댓글·신고 목록의
size는 1~100이며 생략 시 기존 6을 유지한다. 유효한 정수의 범위 위반·offset 초과는
400 VALIDATION_ERROR로 데이터 조회 전에 거부한다. 비정수·Integer 범위 초과는 기존 MVC
바인딩 오류 정책으로 400을 반환한다. 초과 크기를 잘라서 성공 처리하지 않는다.
관리자 회원·일정 등 다른 도메인의 목록 계약은 이번 변경 범위가 아니다.
목록의 작성자·활성 댓글 수·이미지 존재·좋아요·신고 정보는 페이지 ID 기준 일괄 조회한다.
상세의 일반 읽기는 게시글 쓰기 잠금 전에 끝내고, ACTIVE 조건의 조회수 원자 증가와
익명 번호 초기화를 같은 트랜잭션의 마지막 구간에서 수행한다.
실측 SQL 수·응답 일관성·롤백·MySQL 검증 한계는 [조회 성능 검증 안내](COMMUNITY_READ_PERFORMANCE.md)를 따른다.

2번 계정 동시성 보완(2026-10-19): 일반 사용자 게시글·댓글·답글 쓰기(좋아요 취소와 이미지 변경 포함)는
쓰기 트랜잭션에서 계정 행을 먼저 잠그고 ACTIVE를 검증한다. 정지·탈퇴가 먼저 커밋되면 UNAUTHORIZED,
쓰기 선점 시 상태 변경은 쓰기 완료까지 대기한다. API 형식·소유권·공개 조회수 정책은 유지한다.
잠금 충돌은 기존 409 ACCOUNT_UPDATE_CONFLICT 정책을 따른다.
자세한 경로·잠금 순서·검증 한계는 [계정 동시성 안내](COMMUNITY_ACCOUNT_CONCURRENCY.md)를 따른다.

2026-10-19 4·5번 보완: 일반/익명 댓글 작성과 댓글 삭제는 게시글 행 잠금을 공유한다.
대댓글 작성은 게시글 → 부모 댓글 순서로 잠그고 최신 활성 상태와 기존 1단계 제한을 검증한다.
부모 댓글 삭제는 일반·관리자·신고 DELETE_TARGET 모두 활성 대댓글을 함께 소프트 삭제하여
삭제된 부모 아래 ACTIVE 대댓글을 남기지 않는다. 기존 화면에서 함께 사라지는 정책은 유지한다.
이미지 수정은 기존 게시글 잠금을 재사용하고 잠금 이미지 목록과 캐시를 새로 읽은 뒤
유지 목록+신규 파일 합계 5장 검증·연결 변경·순서 부여를 한 트랜잭션에서 수행한다.
keepImageIds는 유지할 전체 ID 목록이며 누락 ID의 연결 제거 계약을 유지한다.
자세한 범위·파일 롤백·H2/MySQL 차이는 [4·5번 검증 안내](COMMUNITY_CREATION_IMAGE_CONCURRENCY.md)를 따른다.

2026-10-19 동시성 보완(1번 문제): 게시글 수정·일반/관리자 삭제는 상세 조회의 조회수 증가·좋아요와
같은 게시글 행 잠금을 사용한다. 댓글·대댓글 수정·일반/관리자 삭제·좋아요는 같은 댓글 행 잠금을
사용하며, 일반 수정·삭제의 작성자 검증은 잠금 조회 뒤에 수행한다. 삭제된 대상은 기존
INVALID_REQUEST로 거부한다. 관리자 신고 DELETE_TARGET도 대상 행을 잠그고 새로 읽는다.
요청·응답·익명 처리·부모 댓글 삭제 시 대댓글이 함께 사라지는 표시 정책은 유지한다.
상세 조회는 인증 여부·반복 여부와 관계없이 성공한 요청마다 1회 증가하며 목록·관리자 상세는
증가하지 않는다. 잠금 충돌은 기존 공통 예외 처리(409 ACCOUNT_UPDATE_CONFLICT)를 따른다.
잠금 순서·H2/MySQL 검증 범위는 [동시성 검증 안내](COMMUNITY_CONCURRENCY.md)를 따른다.

7.1 게시글 목록

GET /api/posts

Public 조회 / 인증 선택

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
"dormitory": "나래",
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
"dormitory": "나래",
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

게시글 상세 조회 시 조회수 증가 정책은 백엔드에서 처리한다. 단순 읽기로 게시글·이미지·댓글·
집계·프로필 스냅샷을 먼저 준비한 뒤 ACTIVE 조건 UPDATE로 view_count만 1 증가시키고
updated_at을 갱신한다. 관리 Entity를 refresh하여 같은 트랜잭션의 후속 저장이 오래된 값을
덮어쓰지 않게 한다. 익명 번호 초기화와 DTO 구성도 같은 트랜잭션에 포함하며 실패 시 함께 롤백한다.
게시글 삭제가 먼저 커밋되면 증가 대상이 없어 기존 INVALID_REQUEST로 거부한다.
응답 본문·이미지·댓글·일반 집계는 선행 읽기의 값이고 views는 이 요청의 원자 증가 결과다.
읽기 일관성은 DB 격리 수준을 따른다. MySQL RR은 선행 일관 읽기 스냅샷,
READ COMMITTED는 문장별 읽기이며 동시 수정 직전의 정상 게시글 내용이 응답될 수 있다.
조회수 증가 후 서비스 내부 실패는 롤백하지만 서비스 커밋 후 HTTP 직렬화·전송 실패의 조회수는
기존과 같이 이미 반영될 수 있다. 번호 초기화를 별도 커밋하지 않는다.

목록·상세는 공개 조회이며 likes는 전체 좋아요 수, liked는 인증 사용자 본인의 좋아요 여부다.
비로그인 요청의 liked는 false다.

목록과 내가 작성한 게시글은 DB에서 선택한 페이지의 게시글 ID에 한정하여 좋아요·활성 댓글 수를
GROUP BY로 일괄 조회한다. 이미지 존재·현재 사용자의 좋아요/신고 여부도 페이지 ID로 조회하고,
일반 작성자 프로필은 고유 작성자 ID로 일괄 조회한다. 익명 작성자의 실제 ID·전공은 공개하지 않는다.
빈 페이지는 추가 조회를 생략하며 좋아요·댓글이 없는 항목의 집계는 0이다.
목록의 to-one 작성자·생활관만 함께 읽으며 컬렉션 fetch join이나 메모리 페이지네이션은 사용하지 않는다.
LIKES 정렬·필터·count·페이징은 계속 DB에서 처리하고 동점은 createdAt DESC, postId DESC다.
상세의 comments는 댓글·답글을 parentCommentId로 연결하는 기존 배열 구조를 유지한다.
각 항목은 id, parentCommentId(일반 댓글은 null), content, author, createdAt,
likes(전체 좋아요 수, 없으면 0), liked(본인 좋아요 여부, 비로그인은 false), anonymous,
anonymousNumber(일반 댓글은 null), mine(인증된 본인 댓글 여부, 비로그인은 false)를 포함한다.
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
"content": "게시글 내용",
"anonymous": false
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

유지할 기존 이미지 ID와 신규 파일을 함께 전달한다. keepImageIds의 생략/null/빈 배열은 기존 이미지 연결을 모두 제거한다. 본문만 수정해도 유지할 ID를 명시해야 한다. 유지 ID는 해당 게시글 소유이며 중복을 금지하고 유지+신규 합계는 최대 5개다. 유지 이미지의 기존 순서 다음에 신규 이미지를 추가한다. 프론트 CommunityPage.savePost는 남긴 기존 이미지 ID와 신규 File을 분리하여 전송한다. 개별 연결 제거는 같은 트랜잭션에 영속 정리 작업을 기록하고 커밋 후 파일을 정리한다. 롤백 시 기존 파일은 유지한다.

현재 Request JSON Part 예:

{
"category": "자유게시판",
"title": "수정 제목",
"content": "수정 내용",
"keepImageIds": [1, 2]
}

게시글 작성 anonymous는 boolean이며 기본 false다. 작성 시 선택한 익명 여부는 작성 후 변경할 수 없다. 수정 anonymous는 호환성을 위해 Boolean으로 유지한다. 생략과 명시적 null은 저장된 값을 유지하고, 동일 값은 허용하며, 다른 값은 400 VALIDATION_ERROR로 전체 요청을 거부한다. 계정 → 게시글 잠금 및 최신 활성 상태·작성자 검증 후 익명 여부를 비교하며 제목·본문·이미지 변경 전에 거부한다. 프론트 수정 화면은 익명 여부를 읽기 전용으로 표시하고 수정 요청에는 anonymous를 보내지 않는다. 관리자 예외나 별도 익명 변경 API는 없다.

목록·상세·검색·내 게시글 응답에는 anonymous와 mine을 포함하며 author는 id/nickname/major다. 2026-10-19 커뮤니티 3번 요구사항에 따라 익명 게시글은 author.id=null, author.nickname="익명", author.major=null이다. 일반 게시글의 작성자 정보는 유지한다. mine은 인증 Principal과 내부 작성자를 비교하며 비로그인은 false다. 수정·삭제 권한은 내부 작성자로 검증하며 요청의 ID나 mine을 신뢰하지 않는다. 관리자 전용 API의 작성자 정보와 접근 통제는 유지한다.

작성 성공 data는 postId(number), 수정 성공 data는 null이다. 이미지 URL은 /api/post-images/{파일명} 경로를 유지하며 프론트는 API origin 기준으로 해석한다. 파일은 비공개 app.community.image-directory(기본 uploads/community)에 저장한다.

9번 보완(2026-10-19): GET /api/post-images/{filename}은 PostImageController.getImage가 처리하는 바이너리 응답이다.
비로그인도 해당 URL의 POST_IMAGE 연결 및 부모 ACTIVE 상태를 검증한 뒤 200 image/jpeg|png|webp|gif를 받는다.
UUID 파일명 및 과거 안전한 영문/숫자/밑줄/하이픈 basename과 허용 확장자를 지원한다.
연결 없음·DELETED·없는 파일·허용되지 않은 파일명은 404 빈 본문이다. 경로 탐색은 방화벽/라우팅에서
400/403/404로 차단될 수도 있다. 내부 경로는 반환하지 않는다. 직접 정적 폴더 공개는 제거한다.
정상/404 이미지 응답은 Cache-Control: no-store, Pragma: no-cache, Expires: 0이다.
ETag/Last-Modified/304를 사용하지 않으며 Range도 전체 응답 또는 거부로 처리한다.
개별 제거 커밋 후 파일 삭제에 실패해도 URL은 차단되며 DB 작업으로 재시도한다. 다른 연결(삭제 게시글 포함)이
하나라도 있으면 파일을 보존한다. 같은 파일이 다른 ACTIVE 게시글에도 연결되어 있으면 그 공개 연결은 유지한다.
사용자·관리자·신고에 의한 게시글 전체 삭제는 기존 소프트 삭제 및 이미지 연결·실물 파일을 보존하고
공개 GET만 차단한다. 관리자에게도 이 공개 URL의 예외는 없으며 별도 보존 이미지 열람 API는 없다.
진행 중 응답이나 이미 저장된 사본을 회수하지는 않는다. 구버전 서버·CDN·직접 볼륨 공개를 함께 제거해야 한다.
[파일 수명주기·배포 안내](COMMUNITY_IMAGE_LIFECYCLE.md)를 따른다.

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

댓글과 답글의 anonymous는 생략/null이면 false(닉네임 공개)이다. 익명인 경우 author.nickname은 "익명1"처럼 게시글별 번호를 포함하고 author.id와 author.major는 null이다. anonymousNumber는 해당 게시글에서 처음 익명으로 참여한 순서대로 배정하며 댓글과 답글이 공유한다. 일반 댓글의 anonymousNumber는 null이며 작성자 ID·닉네임·학과는 기존대로 반환한다. 내용 수정·소프트 삭제·재작성은 기존 번호를 유지한다.

mine은 서버 Principal에서 얻은 인증 사용자 ID와 댓글·답글의 내부 작성자 ID를 비교해 계산하며, 비로그인은 false다. 익명 여부나 응답 author.id로 계산하지 않는다. 수정·삭제 UI는 실제 작성자 ID 비교 대신 mine을 사용한다. 서버의 권한 검증은 인증 사용자와 내부 작성자 관계로 계속 수행한다. 익명 댓글에는 게시글의 실제 작성자와 연결되는 작성자 배지를 표시하지 않는다. author.id=null과 mine 사용은 현재 구현에 반영된 계약이며, 과거 클라이언트를 배포할 때는 이 nullable 계약을 함께 지원해야 한다. 익명 게시글에도 ID 마스킹과 mine을 적용한다(7.4).

8.3 댓글/답글 수정

PATCH /api/comments/{commentId}

Auth / 작성자만 가능

댓글과 답글은 현재 CommunityComment Entity로 통합되어 동일 수정 Endpoint를 사용한다.

Request:

{
"content": "수정한 댓글"
}

8.4 댓글/답글 삭제

DELETE /api/comments/{commentId}

Auth / 작성자만 가능

서버는 parentCommentId를 가진 평면 배열을 반환하고 프론트에서 comments[].replies[]로 구성한다. 댓글/답글 작성 성공 data는 commentId(number), 수정·삭제·좋아요 성공 data는 null이다.

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

현재 프론트는 REST 조회/설정과 WebSocket(STOMP) 송수신을 사용한다. 아래 내용은 현재
Controller/Service/DTO와 프론트 호출의 계약이다. 이번 최신화는 API나 실행 코드를 변경하지 않는다.
REST는 인증된 Principal의 사용자 ID를 사용하며 성공은 공통 ApiResponse/HTTP 200이다.
현재 참여자 검사가 필요한 방 단건·메시지·읽음·설정·나가기에는 관리자 우회 권한이 없다.
연결 이후 권한 재검증은 10.4, 실제 배포 구조와 JVM 간 전달 제한은 10.8을 따른다.

10.1 채팅방 목록

GET /api/chat/rooms

Auth

Query:

?category=ALL&q=포근한구름&page=0&size=50

Category:

ALL
ROOMMATE
COMMUNITY

응답 `data`는 종전 배열에서 다음 페이지 객체로 변경되었다. 서버와 해당 프론트를 함께 배포한다.
기존 `ChatRoomResponse` 항목 필드는 유지한다. 전수 조회를 남기는 호환 경로는 두지 않으며
다른 클라이언트도 `data.content`와 `last`를 사용하도록 먼저 전환해야 한다.

```json
{
  "content": [],
  "page": 0,
  "size": 50,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "counts": { "all": 0, "roommate": 0, "community": 0, "pinned": 0 }
}
```

`page` 기본값 0, `size` 기본값 50 / 최대 100이다. page<0, size<1 또는 >100,
page×size>Integer.MAX_VALUE는 400 VALIDATION_ERROR다. 정수 바인딩 범위를 넘는 값도 400이다.
`totalElements`는 category/q가 적용된 전체 결과 수다. `counts`는 현재 필터·페이지와 무관하게
내가 나가지 않은 ACTIVE 방 전체의 카테고리/고정 개수다. 클라이언트는 `last=false`일 때 다음
페이지를 요청한다. 프론트는 “채팅방 더 보기”와 재시도를 제공하고 검색/필터 변경 시 첫 페이지부터 조회한다.

Response content item:

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

고정 우선 → 마지막 메시지 sentAt 내림차순(NULL 마지막) → roomId 내림차순이다.
마지막 메시지는 sentAt, messageId 내림차순으로 결정한다. q는 trim한 상대 표시 닉네임의
대소문자 무시 부분 일치이며 %, _, !는 문자 그대로 검색한다. 익명 상대는 “익명”으로만 검색되며
실제 닉네임을 검색 조건에 노출하지 않는다. 검색/정렬/페이지 제한은 DB에서 수행한다.
동일 정렬값은 roomId로 고정하지만 서로 다른 페이지 요청 사이에 메시지/고정/나가기 변경이
있으면 offset 위치도 바뀔 수 있다. 클라이언트가 아는 변경 후에는 첫 페이지를 새로 조회하고 ID를 중복 제거한다.

선택한 방이 현재 페이지·필터에 없어도 `GET /api/chat/rooms/{roomId}`로 단건 조회한다.
인증된 현재 참여자만 가능하며 `data`는 같은 ChatRoomResponse다. 나간/존재하지 않는/타인 방은
기존 403 FORBIDDEN, 잘못된 roomId는 400이다. 읽음 성공 후 미읽음 수 갱신도 이 단건 경로를 사용한다.

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

* `targetUserId`: DTO에 @NotNull/@Positive는 없다. ROOMMATE는 서비스에서 양수·필수를 검사한다. COMMUNITY / POST·COMMENT는 생략 또는 null 가능하며 서버가 출처 작성자를 조회한다.
* `category`: `ROOMMATE` 또는 `COMMUNITY`
* `sourcePostId`: 커뮤니티에서 시작한 경우 원 게시글 ID
* `sourceCommentId`: 댓글에서 시작한 경우 원 댓글 ID
* `sourceFrom`: 커뮤니티 채팅의 시작 위치 (`POST` 또는 `COMMENT`)

Validation:

* `category`는 DTO @NotNull이다. `sourceFrom`의 DTO @Pattern은 null을 허용하며 출처별 필수 조건은 서비스가 검사한다.
* `ROOMMATE` 채팅의 `sourcePostId`, `sourceCommentId`, `sourceFrom`은 모두 생략/null이어야 하며 전달하면 400 VALIDATION_ERROR다.
* `COMMUNITY` 채팅에서는 `sourceFrom`이 필수이다.
* `sourceFrom = POST`인 경우 `sourcePostId`가 필수이며 `sourceCommentId`는 사용하지 않는다.
* `sourceFrom = COMMENT`인 경우 `sourcePostId`, `sourceCommentId`가 모두 필수이다.
* `sourceCommentId`는 해당 `sourcePostId`에 속한 댓글이어야 한다.
* 일반 게시글·댓글에서 전달한 `targetUserId`는 작성자와 일치해야 한다. 익명 게시글·댓글에서는 작성자 ID 추측 검증에 악용되지 않도록 전달된 값을 무시하고 출처의 실제 작성자를 사용한다. 위조 ID로 다른 상대를 지정할 수 없으며 ID 일치에 따른 성공/실패 차이도 제공하지 않는다.
* 자기 자신을 대상으로 채팅방을 생성할 수 없다. ROOMMATE의 잘못된 targetUserId, COMMUNITY의 누락/잘못된 출처 ID와 존재하지 않는 대상은 서비스의 400 INVALID_REQUEST다. annotation 검증 실패는 400 VALIDATION_ERROR다.

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
* 성공 data는 ChatRoomResponse이며 신규 생성과 기존 방 재사용 모두 HTTP 200이다. 이미 나간 본인의 재입장은 허용하지 않고 403 FORBIDDEN을 반환한다.

기존 데이터 적용 절차는 `migrations/README.md`를 참고한다.


10.3 메시지 목록

GET /api/chat/rooms/{roomId}/messages

Auth / 채팅방 참여자만 가능

Query:

?page=0&size=50&q=청소

기본 page=0 / size=50, 허용 size=1~100이다. Controller Bean Validation과 Service에서 함께
검사한다. 음수 page, 허용 범위 밖 size, page×size>Integer.MAX_VALUE는 400 VALIDATION_ERROR이며
정수 바인딩 범위 밖 값도 400이다. 최대 100은 기존 기본값의 두 배로 조회·직렬화량을 제한한다.
응답은 기존 페이지 객체(content/page/size/totalElements/totalPages/first/last)를 유지하며
sentAt DESC, messageId DESC 정렬, 빈 페이지, 참여자 권한, q 본문 검색 동작은 그대로다.

과거 메시지/검색 결과의 추가 조회에는 선택적 `beforeId`를 사용한다.
`?page=0&size=50&q=청소&beforeId=1001`은 같은 방의 기준 메시지보다
`(sentAt,messageId)`가 작은 항목을 DESC로 조회한다. 신규 삽입에 의한 offset 이동을 피한다.
재연결/polling 누락 복구에는 `afterId`를 사용하며 기준보다 큰 항목을 ASC로 조회한다.
예: `?page=0&size=50&afterId=1001`. 클라이언트는 마지막 반환 ID로 다음 묶음을 요청한다.
동률 시각은 ID로 구분하며 클라이언트가 임의 시각을 전달하지 않는다.
커서는 양수이고 beforeId/afterId는 동시에 사용할 수 없으며 page는 0 또는 생략이어야 한다(400 VALIDATION_ERROR).
기준 메시지가 해당 방에 없으면 400 INVALID_REQUEST, 타인/나간 방은 기존 403이다.
커서 사용 시에도 기존 페이지 객체를 반환하되 totalElements는 해당 커서 조건 아래의 결과 수이고
page=0이다. 클라이언트는 last로 추가 조회 종료를 판단한다. 커서 없는 기존 offset 호출은 유지한다.
이는 여러 HTTP 요청에 걸친 DB snapshot은 아니다. 이미 지나간 구간의 늦은 커밋은
재진입·검색 재실행으로 해당 구간을 다시 조회해야 확인할 수 있다. 실제 MySQL 지연 커밋은 통합 검증 대상이다.

읽음 상태 조회(조회 전용): `POST /api/chat/rooms/{roomId}/messages/read-status`

인증된 현재 참여자만 가능하다. 본문은 `{ "messageIds": [1001, 1002] }`이며
기존 ChatReadRequest의 1~100개 양수 ID 검증을 재사용하고 중복은 제거한다.
응답 data는 `[{ "id": 1001, "read": true }, { "id": 1002, "read": false }]`다.
senderId/프로필/사용자 ID를 포함하지 않으며 순서는 보장하지 않는다. 다른 방/존재하지 않는 ID가
하나라도 있으면 400 INVALID_REQUEST다. 이 조회는 읽음/last_read_at을 변경하지 않는다.
실제 읽음 확인은 기존 POST /read와 정확한 messageIds 본문을 계속 사용한다.

프론트는 보이는 동안 15초마다 방 목록 첫 페이지와 선택 방의 이후 메시지·화면에 보이는
미확인 자기 메시지의 receipt(최대 100개)를 조회한다. 재연결/포커스/visibility 복귀에도 재조회한다.
한 번에 한 동기화만 실행하고 중복 수신은 ID로 병합한다. 50개를 넘는 전방 누락은 1초 간격의
추가 50개 묶음으로 따라잡는다. 목록 첫 페이지 ID/총수가 같으면 추가로 불러온 페이지를 유지하고,
변경되면 첫 페이지로 재동기화한다. 매번 방 전체를 조회하지 않는다.
선택 방 STOMP payload/목적지와 Simple Broker를 사용하며 인증·구독·전달 권한 검사는 10.4를 따른다.
별도 상태 이벤트를 발행하지 않으며 REST는 커밋된 상태를 다시 읽어 롤백된 변경을 확정 알림으로 취급하지 않는다.
polling 주기는 15초이며 미선택 방·상대 읽음 상태에 즉시 push를 보장하지 않는다.
응답 대기·브라우저 타이머 제한·복구 묶음 수에 따라 반영은 15초보다 늦어질 수 있다.

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

메시지 최대 2,000 Unicode 코드 포인트다. 프론트는 Array.from(text).length로 표시/검사하며
UTF-16 maxLength는 사용하지 않는다. 초과 입력은 자르지 않고 유지하되 전송을 막는다.
IME 조합 중 Enter 전송을 막고 공백 메시지는 기존대로 거절한다. 복합 이모지/결합 문자는
grapheme 1개라도 여러 코드 포인트로 계산한다. 추가로 로딩한 이력 전체를 자동 읽음 처리하지 않고
보이는 탭의 메시지 viewport와 겹친 상대 메시지 ID만 확인한다. 프로필/상세/나가기 대화상자가
열린 동안에는 뒤의 메시지를 확인하지 않는다. 서버 전송 DTO는 @NotBlank와
@CodePointLength(max=ChatMessage.MAX_CONTENT_LENGTH=2000)를 사용하고 Entity도 코드 포인트를 검사한다.
문자열에 대한 @Size(max=2000) 검증이 아니다. 사람의 실제 독서를 증명하는 계약은 아니다.

10.4 메시지 전송 - WebSocket

현재 STOMP Contract:

기본 연결은 /ws/chat이며 SockJS를 사용하지 않는다. 실제 등록 경로는
application.properties의 app.chat.websocket.endpoint다. 프론트는 API base URL의 /api를
/ws/chat으로 바꾸어 ws/wss로 연결하므로 서버 endpoint나 프록시 경로를 바꾸면 함께 확인해야 한다.
허용 Origin은 WebSocketConfig의 app.cors.allowed-origins를 따른다. HTTP handshake 허용은
STOMP 인증을 대신하지 않는다.

현재 ChatStompInterceptor/JwtAuthenticationProvider는 다음을 수행한다.

* CONNECT: Authorization: Bearer {accessToken} 하나와 sessionId를 요구한다. JWT 서명·만료와 DB의 ACTIVE 계정을 검증하고 세션별 토큰 참조를 보관한다.
* SEND: 목적지 형식과 인증 Principal을 확인하고 저장된 세션 토큰의 JWT·ACTIVE 계정을 다시 검증한다. 실제 방 참여/나가기/ACTIVE 방 여부는 ChatService.sendMessage가 검사한다.
* SUBSCRIBE: /sub/chat/rooms/{양수 roomId}만 허용하며 JWT·ACTIVE 계정·Principal 일치와 현재 활성 참여를 다시 검사한다. 다른 사용자 ID를 구독하는 별도 경로는 없다.
* 기존 구독의 실제 MESSAGE 전달 직전: outbound executor의 beforeHandle에서 JWT·ACTIVE 계정·활성 참여를 다시 검사한다. 실패나 DB 장애면 해당 수신자 메시지를 null로 차단한다. 세션 종료/DISCONNECT 시 토큰 참조를 제거한다.

따라서 과거의 “CONNECT 이후 재검증하지 않음/기존 구독 전달 검증 없음” 설명(#12)은 현재 코드와 다르다.
이는 매 프레임/전달 시점의 검증이며, 정지 즉시 모든 소켓을 강제 종료하거나 구독 레코드를 삭제하는
기능을 뜻하지 않는다. 판정 이후의 동시 권한 변경과 네트워크 전송까지 완전한 원자성도 보장하지 않는다.
근거: [WebSocketConfig](src/main/java/com/kkirikkiri/global/config/WebSocketConfig.java),
[ChatStompInterceptor](src/main/java/com/kkirikkiri/global/config/ChatStompInterceptor.java),
[JwtAuthenticationProvider](src/main/java/com/kkirikkiri/global/security/JwtAuthenticationProvider.java).

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

REST 메시지 전송 API는 제공하지 않는다. STOMP text는 null/빈 문자열/공백만인 문자열을 거절하며
최대 2,000 Unicode 코드 포인트다. 중간 공백을 금지하지 않는다. STOMP 오류는 HTTP/ApiResponse가 아니다.
ChatWebSocketController는 트랜잭션 서비스 호출이 정상 반환된 후 DTO를 Simple Broker로 전달한다.
DB 커밋과 WebSocket 전달은 하나의 원자적 작업이 아니므로 커밋 후 프로세스 장애/전달 실패 가능성은 남는다.
브로커 전달 확인·영속 재시도/outbox·JVM 간 중계가 구현됐다는 뜻은 아니다. REST 재조회가 복구를 보조한다.

10.5 읽음 처리

POST /api/chat/rooms/{roomId}/read

Auth

조회·WebSocket 수신 결과가 화면에 반영된 뒤 상대방 메시지의 정확한 ID만 읽음 확인한다.
시간이나 최대 ID 이하를 일괄 처리하지 않으므로 지연 커밋·동일 시각·전달 순서 역전이 다른 메시지의 읽음에 영향을 주지 않는다.

Request (필수):

```json
{ "messageIds": [1001, 1002] }
```

1~100개 양수 ID를 받으며 중복은 제거한다. 해당 방의 상대 메시지만 허용하고 존재하지 않는 ID·다른 방·자기 메시지가
하나라도 있으면 전체 요청을 400 INVALID_REQUEST로 거절한다. 빈/누락/잘못된 본문은 400이며 DTO 검증은 VALIDATION_ERROR다.
나간 참여자는 기존 403 FORBIDDEN, 비활성 계정은 401 UNAUTHORIZED다. 성공 응답은 기존 HTTP 200 / data=null이다.
본문 없는 종전 호출은 최신 메시지를 임의로 읽음 처리하지 않고 거절하므로 관련 클라이언트와 함께 배포해야 한다.

참여자 쓰기(읽음/설정/나가기)는 USER → CHAT_ROOM_MEMBER → 확인할 메시지 ID 오름차순으로 DB 잠금을 획득한다.
현재 참여자 상태를 잠금과 함께 다시 읽고, last_read_at은 DB 조건부 UPDATE로도 역행을 막는 보조 정보로 유지한다.
실제 read·unreadCount는 CHAT_MESSAGE.is_read를 사용한다. 조회에 실패하거나 수신하지 않은 ID는 클라이언트가 전송하지 않는다.
기존 메시지 정렬, 익명성, 응답 구조와 고정 최대 2개 정책은 유지한다.
스키마·기존 읽음 데이터 전환 및 격리 수준의 검증 범위는
[수동 배포 안내](migrations/20261019_chat_message_receipts.md)를 따른다.

10.6 채팅방 설정 변경

PATCH /api/chat/rooms/{roomId}/settings

Auth

Request 예:

{
"pinned": true,
"muted": false
}

사용자별 나가지 않은 고정 방은 최대 2개이며 초과하는 새 고정은 400 INVALID_REQUEST다.
updateSettings는 USER 계정의 PESSIMISTIC_WRITE/활성 검사 → 자신의 CHAT_ROOM_MEMBER 잠금/refresh
순으로 진행한다. 새 고정이면 findPinnedMembersForUpdate의 PESSIMISTIC_READ로 현재 고정 행을
읽어 상한을 검사한다. 이미 고정된 방을 다시 true로 설정하는 요청은 새 고정으로 세지 않는다.
고정 행 목록의 READ 잠금과 대상 참여자의 refresh/WRITE 잠금은 다르다.
따라서 과거의 “사용자 단위 잠금 없음” 설명(#13)은 현재 코드와 다르다.
pinned/muted는 Boolean이며 생략/null은 기존 값 유지다. 잘못된 상태의 참여자/방은 403이며,
H2 회귀 통과와 실제 MySQL의 current read·잠금 대기·교착 검증은 구분한다.

10.7 채팅방 나가기

DELETE /api/chat/rooms/{roomId}/members/me

Auth

채팅방 자체 삭제가 아니라 현재 사용자의 is_left/left_at을 변경한다. 메시지를 삭제하거나 상대방을
함께 나가게 하지 않는다. USER → 자신의 CHAT_ROOM_MEMBER 잠금/refresh로 오래된 읽음/설정 요청의
덮어쓰기를 막는다. 성공은 HTTP 200/data=null이다. 이미 나간 사용자·타인/존재하지 않는 방은
403 FORBIDDEN이며 다시 나가기/읽음/설정/메시지 접근·재입장을 허용하지 않는다.
클라이언트는 성공 후 목록을 재조회하고 선택/작성 중 초안을 정리한다.

### 10.8 배포 구성과 JVM 간 전달 제한

현재 WebSocketConfig는 /pub application prefix와 JVM 내부 enableSimpleBroker("/sub")를 사용한다.
동일 JVM의 목적지 구독자에게 전달하며 서로 다른 JVM의 Simple Broker 사이 중계는 없다.
공유 MySQL·JWT 키·클라이언트 sticky session만으로 이 중계가 추가되지 않는다.
15초 polling은 DB의 목록/이후 메시지/읽음 상태를 다시 읽는 복구 경로이며 인스턴스 간 STOMP 실시간 전달의 해결책이 아니다.

2026-10-20 사용자가 팀원 확인 후 졸업작품은 Spring Boot 백엔드 1개(단일 JVM)로 배포한다고
확정했다. 문제 2는 **단일 JVM 배포 조건에 따른 위험 수용**으로 처리하며 현재 Simple Broker를
유지한다. Redis·RabbitMQ 등 외부 메시지 브로커는 도입하지 않는다. 모든 연결/SEND를 하나의
활성 JVM에 모으고 재배포 중에도 구·신 JVM에 연결이 나뉘지 않아야 한다.
실제 단일 JVM의 두 클라이언트 간 WebSocket 전달은 미검증이며 mock/H2 성공과 구분한다.
향후 다중 JVM 확장 시에는 공유 중계와 별도 인가/익명성·장애 복구·두 JVM 전달 검증이 필요하다.
단일 JVM의 용량/장애·재시작 수용 범위, 배포 전환 조건과 향후 개선/검증 계획은
[채팅 배포·문서 점검 보고서](CHAT_DEPLOYMENT_API_VALIDATION.md)와 [DEPLOYMENT.md](DEPLOYMENT.md)를 따른다.

11. 캘린더

사용자 일정 DATE/TIME은 한국 현지 일정 값이며 시간대 오프셋을 추가하지 않는다. 수집 이력과 수집·카테고리 생성 DATETIME은 서울 현지 벽시계 값이다. Calendar의 직접 Java-time JDBC 매핑은 JVM/JDBC 시간대에 따른 값 이동을 방지한다. DTO 타입·nullable 시간·응답 형식은 유지하며 기존 데이터 확인과 수동 인덱스 적용은 [DEPLOYMENT.md](DEPLOYMENT.md)를 따른다.

학교 학사일정만 현재 자동 수집한다. 기숙사 수집기는 미구현이며 출처·규칙 확정 전 임의 구현하지 않는다.

한경국립대학교 학사일정(https://www.hknu.ac.kr/kor/646/subview.do)은 매일 04:00(Asia/Seoul)에 현재 표시된 연간 일정을 수집한다.
기존 SCHOOL 시스템 카테고리가 없으면 수집 저장을 중단하며 카테고리를 자동 생성하지 않는다.
수집 일정은 ownerUser=null, sourceType=CRAWLED, sourceName="한경국립대학교 학사일정"인 종일 일정이다.
sourceType/sourceName/title/startDate/endDate가 모두 같으면 추가 저장하지 않는다. 원문의 제목이나 날짜가 변경되면 새 일정으로 저장되며 기존 일정은 자동 수정·삭제하지 않는다.
수집 전용 API는 제공하지 않으며 기존 일정 조회 API에서 조회한다.

수집 시작 이력(RUNNING)은 HTTP 요청 전에 독립 트랜잭션으로 저장한다. 일정과 성공 이력(COMPLETED·완료 시각·신규 저장 건수)은 하나의 저장 트랜잭션에서 함께 커밋한다. 중복 일정만 있는 실행도 COMPLETED/0건으로 기록한다. 요청·파싱·저장·커밋 실패는 일정 롤백 후 별도 트랜잭션에서 FAILED/0건으로 기록한다. 실패 사유는 안전한 FETCH/STORE 분류 코드이며 외부 응답·예외 원문을 저장하지 않는다. 프로세스 종료나 이력 DB 장애로 남은 RUNNING의 운영 확인 절차는 [DEPLOYMENT.md](DEPLOYMENT.md)의 Calendar 항목을 따른다.

SCHOOL = 학교 일정
DORMITORY = 기숙사 일정
CUSTOM-* = 사용자가 직접 생성한 카테고리

시스템 고정 카테고리는 SCHOOL(학교), DORMITORY(기숙사)이며 ownerUser=null, isSystem=true다. 사용자는 시스템 카테고리를 수정·삭제하거나 직접 일정을 등록할 수 없다. 학교 수집은 SCHOOL을 사용한다. DORMITORY 저장·조회 모델이 있다는 사실은 자동 수집기 구현을 의미하지 않는다.

현재 프론트는 서버 editable 값으로 수정/삭제를 제어하고 서버도 소유권·sourceType·시스템 카테고리를 검사한다.

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

조회 기간과 일정 기간이 겹치면 반환한다(startDate <= to AND endDate>= from, 경계일 포함).

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

같은 날짜이며 시작/종료 시간이 모두 존재하면 종료시간> 시작시간. 서로 다른 날짜에는 시각 값만 비교하지 않는다. 두 시간 모두 null이면 종일이며 한쪽 시간만 지정하는 현재 계약도 허용한다.

프론트는 한쪽 시간 일정도 시작일~종료일의 날짜 범위에 표시하고, 지정된 시간과 `시작 시간 미정` 또는 `종료 시간 미정`을 명시한다. 누락 시간을 생성하거나 저장 값을 종일로 변경하지 않는다. FullCalendar의 날짜 배치에만 allDay 플래그를 사용한다.

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

생성·이름 변경·설정 저장·삭제는 공통 사용자 행 잠금을 첫 DB 접근으로 획득한 뒤 기존 이름·설정을 재조회한다. USER → Calendar 행 순서를 사용하며 서로 다른 사용자는 별도 행을 잠근다. 이름 비교는 기존 Java 문자열 일치 기준을 유지하고 새 UNIQUE/Collation 정책을 도입하지 않는다. PUT에서 생략된 설정은 유지한다. 프론트의 표시 여부 변경은 enabled에만 반영하며, 명시적으로 삭제한 사용자 카테고리만 기존 DELETE API로 요청한다.

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

이 장은 미구현 서버 이전 설계다. 현재 MainPage.calculateDormitoryScore가 프론트에서 계산하며 아래 경로는 제공 중인 API가 아니다. 계산 정책·priorityType·검증·인증 정책이 미확정이므로 이번 작업에서 서버 기능을 추가하지 않는다.

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

현재 프론트 계산을 유지한다. 서버 이전은 별도 정책 확정 후 작업한다.

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

CalendarCrawlingService는 실행마다 ScheduleCrawlRun을 기록하며 상태 API는 시작 시각·ID 기준 최신 실행, 별도의 최근 성공·실패를 조회한다. 성공 이력과 일정은 같은 커밋으로 확정하고 실패 이력은 일정 롤백과 독립적으로 저장한다. 이력이 없는 카테고리는 status/lastRunAt/lastCompletedAt/lastFailure=null, recentCollectedCount=0이며 totalCollectedCount는 실제 수집 일정 집계다. DORMITORY 자동 수집기는 계속 미구현이다.

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

위 서버 점수 경로는 예정 설계이며 현재 메인페이지는 호출하지 않는다.

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

17. 현재 프론트 연동 상태와 정책 결정 항목

17.1 생활관 명칭 불일치

현재 파일별로 다음 값이 혼재한다.

회원가입: 나래 / 호연 / 비봉 / 창조

마이페이지 수정: 나래 / 호연 / 비봉 / 창조

커뮤니티/관리자: 나래관 / 호연관 / 창조관 / 비봉관

채팅 샘플: 비봉

DB의 dormitory_code는 NARAE / HOYEON / BIBONG / CHANGJO다. 가입·내 정보·응답은 나래 / 호연 / 비봉 / 창조 이름을 사용한다. 커뮤니티 목록 필터는 관 접미사도 정규화한다. 표시명 차이를 이유로 공개 필드 값을 코드로 임의 전환하지 않는다.

17.2 내 프로필 설문 필드 통일 기준

현재 설문은 23개 필드이며 과거 hairDryer/bugHandling은 API·화면·Entity에 없다. 현재 저장 계약과 필터에 추가하지 않는다.

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

현재 CommunityPostPage.startChat은 게시글·댓글·답글의 출처 ID로 방 생성 API를 직접 호출하고 반환 roomId로 이동한다. 익명 출처는 targetUserId=null이며 서버가 작성자를 결정한다. useChat의 기존 communityChat Router state 경로도 작성자 ID 없이 POST를 지원한다. 댓글 버튼은 해당 Router state 경로를 사용하지 않는다.

17.5 학교/기숙사 일정 수정 기준

현재 Calendar UI는 API의 editable 값으로 수정/삭제를 제한한다.

현재 권한 기준은 다음과 같다. 기숙사 자동 수집은 미구현이다:

SCHOOL, DORMITORY: 조회 전용

본인 소유 CUSTOM-* 사용자 카테고리: 사용자 CRUD 가능

구조로 맞추는 것을 권장한다.

18. 참고: 초기 구현 순서 (현재 완료 상태 목록이 아님)

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

## 19. 현재 REST·DTO 상세 계약 (2026-10-14)

### 19.1 제공 중인 REST 엔드포인트

성공은 HTTP 200이며 아래 반환 타입은 공통 `ApiResponse<T>`의 **data** 타입이다. `Void`는 null, `Long`은 JSON 숫자이며 `{id: ...}` 객체가 아니다. 경로 ID는 Long이다. JSON은 기본 요청 형식이며 게시글 생성/수정만 JSON request 파트와 images 파일의 multipart를 사용한다. 조회 Request DTO는 query 바인딩이다. 자료형의 List/Map/Enum/LocalDate/LocalDateTime은 각각 JSON 배열/객체/문자열/날짜/날짜시간으로 표현한다. 상세 검증·인증·소유권은 각 본문과 아래 규칙을 함께 적용한다. 예외로 이미지 파일의 byte[]는 위 9번 계약의 ResponseEntity<byte[]> 바이너리 본문이며 ApiResponse로 감싸지 않는다.

| Method | 경로 | data 타입 (이미지는 바이너리 본문) | Controller·메서드 |
| --- | --- | --- | --- |
| GET | /api/post-images/{filename} | `byte[]` | [PostImageController.getImage](src/main/java/com/kkirikkiri/community/post/controller/PostImageController.java) |
| GET | /api/admin/dashboard | `AdminDashboardResponse` | [AdminController.getDashboard](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/members | `RoommatePageResponse<AdminMemberResponse>` | [AdminController.getMembers](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/members/{memberId} | `AdminMemberDetailResponse` | [AdminController.getMember](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| PATCH | /api/admin/members/{memberId}/status | `Void` | [AdminController.updateMemberStatus](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/posts | `RoommatePageResponse<AdminPostResponse>` | [AdminController.getPosts](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/posts/{postId} | `AdminPostDetailResponse` | [AdminController.getPost](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| DELETE | /api/admin/posts/{postId} | `Void` | [AdminController.deletePost](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/comments | `RoommatePageResponse<AdminCommentResponse>` | [AdminController.getComments](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/comments/{commentId} | `AdminCommentResponse` | [AdminController.getComment](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| DELETE | /api/admin/comments/{commentId} | `Void` | [AdminController.deleteComment](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/reports | `RoommatePageResponse<AdminReportResponse>` | [AdminController.getReports](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/reports/{reportId} | `AdminReportDetailResponse` | [AdminController.getReport](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| PATCH | /api/admin/reports/{reportId} | `Void` | [AdminController.processReport](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/schedule-crawls/status | `List<AdminScheduleCrawlStatusResponse>` | [AdminController.getScheduleCrawlStatuses](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/crawled-schedules | `RoommatePageResponse<AdminCrawledScheduleResponse>` | [AdminController.getCrawledSchedules](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| GET | /api/admin/crawled-schedules/{scheduleId} | `AdminCrawledScheduleResponse` | [AdminController.getCrawledSchedule](src/main/java/com/kkirikkiri/admin/controller/AdminController.java) |
| POST | /api/auth/signup | `SignupResponse` | [AccountController.signup](src/main/java/com/kkirikkiri/auth/controller/AccountController.java) |
| GET | /api/auth/signup/availability | `SignupAvailabilityResponse` | [AccountController.checkSignupAvailability](src/main/java/com/kkirikkiri/auth/controller/AccountController.java) |
| POST | /api/auth/password-reset/request | `PasswordResetResponse` | [AccountController.requestPasswordReset](src/main/java/com/kkirikkiri/auth/controller/AccountController.java) |
| POST | /api/auth/password-reset/confirm | `Void` | [AccountController.resetPassword](src/main/java/com/kkirikkiri/auth/controller/AccountController.java) |
| GET | /api/auth/me | `LoginResponse.LoginUser` | [AuthController.getAuthenticatedUser](src/main/java/com/kkirikkiri/auth/controller/AuthController.java) |
| POST | /api/auth/login/email-check | `LoginEmailCheckResponse` | [AuthController.checkEmail](src/main/java/com/kkirikkiri/auth/controller/AuthController.java) |
| POST | /api/auth/login | `LoginResponse` | [AuthController.login](src/main/java/com/kkirikkiri/auth/controller/AuthController.java) |
| POST | /api/auth/email-verifications/send | `EmailVerificationSendResponse` | [EmailVerificationController.send](src/main/java/com/kkirikkiri/auth/controller/EmailVerificationController.java) |
| POST | /api/auth/email-verifications/verify | `EmailVerificationVerifyResponse` | [EmailVerificationController.verify](src/main/java/com/kkirikkiri/auth/controller/EmailVerificationController.java) |
| GET | /api/calendar/events | `List<CalendarEventResponse>` | [CalendarController.getEvents](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| POST | /api/calendar/events | `CalendarEventResponse` | [CalendarController.createEvent](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| PATCH | /api/calendar/events/{eventId} | `CalendarEventResponse` | [CalendarController.updateEvent](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| DELETE | /api/calendar/events/{eventId} | `Void` | [CalendarController.deleteEvent](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| GET | /api/calendar/categories | `List<CalendarCategoryResponse>` | [CalendarController.getCategories](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| PUT | /api/calendar/categories | `List<CalendarCategoryResponse>` | [CalendarController.saveCategorySettings](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| POST | /api/calendar/categories/custom | `CalendarCategoryResponse` | [CalendarController.createCategory](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| PATCH | /api/calendar/categories/{categoryId} | `CalendarCategoryResponse` | [CalendarController.updateCategory](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| DELETE | /api/calendar/categories/{categoryId} | `Void` | [CalendarController.deleteCategory](src/main/java/com/kkirikkiri/calendar/controller/CalendarController.java) |
| GET | /api/chat/rooms | `ChatRoomPageResponse` | [ChatController.getRooms](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| GET | /api/chat/rooms/{roomId} | `ChatRoomResponse` | [ChatController.getRoom](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| POST | /api/chat/rooms | `ChatRoomResponse` | [ChatController.createOrGetRoom](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| GET | /api/chat/rooms/{roomId}/messages | `RoommatePageResponse<ChatMessageResponse>` | [ChatController.getMessages](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| POST | /api/chat/rooms/{roomId}/messages/read-status | `List<ChatMessageReadStatusResponse>` | [ChatController.getReadStatuses](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| POST | /api/chat/rooms/{roomId}/read | `Void` | [ChatController.markRead](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| PATCH | /api/chat/rooms/{roomId}/settings | `Void` | [ChatController.updateSettings](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| DELETE | /api/chat/rooms/{roomId}/members/me | `Void` | [ChatController.leaveRoom](src/main/java/com/kkirikkiri/chat/controller/ChatController.java) |
| POST | /api/posts/{postId}/comments | `Long` | [CommunityCommentController.createComment](src/main/java/com/kkirikkiri/community/comment/controller/CommunityCommentController.java) |
| POST | /api/comments/{commentId}/replies | `Long` | [CommunityCommentController.createReply](src/main/java/com/kkirikkiri/community/comment/controller/CommunityCommentController.java) |
| PATCH | /api/comments/{commentId} | `Void` | [CommunityCommentController.updateComment](src/main/java/com/kkirikkiri/community/comment/controller/CommunityCommentController.java) |
| DELETE | /api/comments/{commentId} | `Void` | [CommunityCommentController.deleteComment](src/main/java/com/kkirikkiri/community/comment/controller/CommunityCommentController.java) |
| POST | /api/comments/{commentId}/likes | `Void` | [CommunityCommentController.likeComment](src/main/java/com/kkirikkiri/community/comment/controller/CommunityCommentController.java) |
| DELETE | /api/comments/{commentId}/likes | `Void` | [CommunityCommentController.unlikeComment](src/main/java/com/kkirikkiri/community/comment/controller/CommunityCommentController.java) |
| GET | /api/users/me/comments | `RoommatePageResponse<MyCommunityCommentResponse>` | [MyCommunityCommentController.getMyComments](src/main/java/com/kkirikkiri/community/comment/controller/MyCommunityCommentController.java) |
| GET | /api/posts | `RoommatePageResponse<CommunityPostResponse>` | [CommunityPostController.getPosts](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| GET | /api/posts/{postId} | `CommunityPostDetailResponse<CommunityCommentResponse>` | [CommunityPostController.getPost](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| POST | /api/posts | `Long` | [CommunityPostController.createPost](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| PATCH | /api/posts/{postId} | `Void` | [CommunityPostController.updatePost](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| DELETE | /api/posts/{postId} | `Void` | [CommunityPostController.deletePost](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| POST | /api/posts/{postId}/likes | `Void` | [CommunityPostController.likePost](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| DELETE | /api/posts/{postId}/likes | `Void` | [CommunityPostController.unlikePost](src/main/java/com/kkirikkiri/community/post/controller/CommunityPostController.java) |
| GET | /api/users/me/posts | `RoommatePageResponse<CommunityPostResponse>` | [MyCommunityPostController.getMyPosts](src/main/java/com/kkirikkiri/community/post/controller/MyCommunityPostController.java) |
| POST | /api/roommates/{userId}/ai-analysis | `AiProfileAnalysisResponse` | [AiProfileAnalysisController.analyze](src/main/java/com/kkirikkiri/matching/controller/AiProfileAnalysisController.java) |
| GET | /api/users/me/liked-roommates | `RoommatePageResponse<RoommateProfileResponse>` | [MyLikedRoommateController.getLikedRoommates](src/main/java/com/kkirikkiri/matching/controller/MyLikedRoommateController.java) |
| GET | /api/roommates | `RoommatePageResponse<RoommateProfileResponse>` | [RoommateMatchingController.getRoommates](src/main/java/com/kkirikkiri/matching/controller/RoommateMatchingController.java) |
| GET | /api/roommates/recommended | `RoommatePageResponse<RoommateProfileResponse>` | [RoommateMatchingController.getRecommendedRoommates](src/main/java/com/kkirikkiri/matching/controller/RoommateMatchingController.java) |
| GET | /api/roommates/{userId} | `RoommateDetailResponse` | [RoommateMatchingController.getRoommateDetail](src/main/java/com/kkirikkiri/matching/controller/RoommateMatchingController.java) |
| POST | /api/roommates/{userId}/likes | `Void` | [RoommateMatchingController.addProfileLike](src/main/java/com/kkirikkiri/matching/controller/RoommateMatchingController.java) |
| DELETE | /api/roommates/{userId}/likes | `Void` | [RoommateMatchingController.cancelProfileLike](src/main/java/com/kkirikkiri/matching/controller/RoommateMatchingController.java) |
| POST | /api/reports | `Long` | [ReportController.createReport](src/main/java/com/kkirikkiri/report/controller/ReportController.java) |
| GET | /api/users/me/profile-survey | `ProfileSurveyResponse` | [ProfileSurveyController.getMyProfileSurvey](src/main/java/com/kkirikkiri/survey/controller/ProfileSurveyController.java) |
| PUT | /api/users/me/profile-survey | `ProfileSurveyResponse` | [ProfileSurveyController.saveOrUpdateMyProfileSurvey](src/main/java/com/kkirikkiri/survey/controller/ProfileSurveyController.java) |
| GET | /api/users/me/roommate-preferences | `RoommatePreferenceResponse` | [RoommatePreferenceController.getMyRoommatePreference](src/main/java/com/kkirikkiri/survey/controller/RoommatePreferenceController.java) |
| PUT | /api/users/me/roommate-preferences | `Void` | [RoommatePreferenceController.saveOrUpdateMyRoommatePreference](src/main/java/com/kkirikkiri/survey/controller/RoommatePreferenceController.java) |
| GET | /api/users/me | `UserResponse` | [UserController.getMyInfo](src/main/java/com/kkirikkiri/user/controller/UserController.java) |
| PATCH | /api/users/me | `UserResponse` | [UserController.updateMyInfo](src/main/java/com/kkirikkiri/user/controller/UserController.java) |
| PATCH | /api/users/me/password | `Void` | [UserController.changePassword](src/main/java/com/kkirikkiri/user/controller/UserController.java) |

### 19.2 DTO 필드와 Java 자료형

다음은 DTO record 선언의 전체 필드이며 본문 JSON 예시에 생략된 필드도 계약에 포함된다. 이름 뒤 괄호는 선언된 요청 제약이고 `없음`은 DTO annotation 없음이며 서비스 무검증을 뜻하지 않는다. 일반 Size/Pattern은 null을 거부하지 않으므로 필수 여부는 NotNull/NotBlank/NotEmpty와 서비스 검증으로 판단한다. 컬렉션 원소 annotation은 해당 원소의 제약이다. primitive boolean/int/long과 boxed Boolean/Integer/Long의 null 가능성을 구분한다. Bean Validation의 객체 검증 및 PATCH null 병합 규칙은 19.3과 각 도메인 본문을 따른다. `AiProfileAnalysisInput`은 Gateway 내부 입력으로 공개 요청 DTO가 아니다. 중첩 record는 상위 DTO 이름으로 구분한다.

| DTO (선언 근거) | 필드: Java 자료형 / 요청 annotation |
| --- | --- |
| [AdminCommentListRequest](src/main/java/com/kkirikkiri/admin/dto/AdminCommentListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1), Max(100))<br>`q: String` (없음)<br>`reported: Boolean` (없음) |
| [AdminCommentResponse](src/main/java/com/kkirikkiri/admin/dto/AdminCommentResponse.java) | `id: Long`<br>`content: String`<br>`author: CommunityAuthorResponse`<br>`createdAt: LocalDateTime`<br>`postId: Long`<br>`postTitle: String`<br>`reported: boolean` |
| [AdminCrawledScheduleListRequest](src/main/java/com/kkirikkiri/admin/dto/AdminCrawledScheduleListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1))<br>`q: String` (없음)<br>`category: String` (Pattern(regexp = "SCHOOL&#124;DORMITORY")) |
| [AdminCrawledScheduleResponse](src/main/java/com/kkirikkiri/admin/dto/AdminCrawledScheduleResponse.java) | `id: Long`<br>`title: String`<br>`date: LocalDate`<br>`content: String`<br>`category: String`<br>`source: String`<br>`collectedAt: LocalDateTime` |
| [AdminDashboardResponse](src/main/java/com/kkirikkiri/admin/dto/AdminDashboardResponse.java) | `stats: Stats`<br>`recentMembers: List<MemberSummary>`<br>`recentPosts: List<PostSummary>`<br>`recentReports: List<ReportSummary>`<br>`recentSchoolSchedules: List<ScheduleSummary>`<br>`recentDormitorySchedules: List<ScheduleSummary>` |
| [AdminDashboardResponse.Stats](src/main/java/com/kkirikkiri/admin/dto/AdminDashboardResponse.java) | `totalMembers: long`<br>`totalPosts: long`<br>`totalComments: long`<br>`totalReports: long`<br>`pendingReports: long`<br>`schoolSchedules: long`<br>`dormitorySchedules: long` |
| [AdminDashboardResponse.MemberSummary](src/main/java/com/kkirikkiri/admin/dto/AdminDashboardResponse.java) | `id: Long`<br>`nickname: String`<br>`major: String`<br>`dormitory: String`<br>`createdAt: LocalDate`<br>`status: UserStatus` |
| [AdminDashboardResponse.PostSummary](src/main/java/com/kkirikkiri/admin/dto/AdminDashboardResponse.java) | `id: Long`<br>`title: String`<br>`category: String`<br>`author: CommunityAuthorResponse`<br>`createdAt: LocalDateTime` |
| [AdminDashboardResponse.ReportSummary](src/main/java/com/kkirikkiri/admin/dto/AdminDashboardResponse.java) | `id: Long`<br>`status: ReportStatus`<br>`type: ReportType`<br>`reason: String`<br>`createdAt: LocalDateTime` |
| [AdminDashboardResponse.ScheduleSummary](src/main/java/com/kkirikkiri/admin/dto/AdminDashboardResponse.java) | `id: Long`<br>`title: String`<br>`date: LocalDate`<br>`collectedAt: LocalDateTime` |
| [AdminMemberDetailResponse](src/main/java/com/kkirikkiri/admin/dto/AdminMemberDetailResponse.java) | `id: Long`<br>`nickname: String`<br>`schoolEmail: String`<br>`gender: String`<br>`dormitory: String`<br>`faculty: String`<br>`major: String`<br>`admissionYear: String`<br>`grade: String`<br>`mbti: String`<br>`createdAt: LocalDate`<br>`status: UserStatus`<br>`activity: Activity` |
| [AdminMemberDetailResponse.Activity](src/main/java/com/kkirikkiri/admin/dto/AdminMemberDetailResponse.java) | `postCount: long`<br>`commentCount: long`<br>`reportsReceived: long` |
| [AdminMemberListRequest](src/main/java/com/kkirikkiri/admin/dto/AdminMemberListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1))<br>`q: String` (없음)<br>`status: String` (Pattern(regexp = "ACTIVE&#124;SUSPENDED&#124;WITHDRAWN"))<br>`dormitory: String` (없음) |
| [AdminMemberResponse](src/main/java/com/kkirikkiri/admin/dto/AdminMemberResponse.java) | `id: Long`<br>`nickname: String`<br>`schoolEmail: String`<br>`dormitory: String`<br>`major: String`<br>`createdAt: LocalDate`<br>`status: UserStatus` |
| [AdminMemberStatusUpdateRequest](src/main/java/com/kkirikkiri/admin/dto/AdminMemberStatusUpdateRequest.java) | `status: String` (NotBlank, Pattern(regexp = "ACTIVE&#124;SUSPENDED&#124;WITHDRAWN")) |
| [AdminPostDetailResponse](src/main/java/com/kkirikkiri/admin/dto/AdminPostDetailResponse.java) | `id: Long`<br>`category: String`<br>`title: String`<br>`author: CommunityAuthorResponse`<br>`createdAt: LocalDateTime`<br>`views: long`<br>`likes: long`<br>`commentCount: long`<br>`reported: boolean`<br>`content: String` |
| [AdminPostListRequest](src/main/java/com/kkirikkiri/admin/dto/AdminPostListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1), Max(100))<br>`q: String` (없음)<br>`category: String` (Pattern(regexp = "자유게시판&#124;나눔게시판&#124;택배게시판&#124;민원게시판"))<br>`reported: Boolean` (없음)<br>`sort: String` (Pattern(regexp = "LATEST&#124;VIEWS&#124;LIKES")) |
| [AdminPostResponse](src/main/java/com/kkirikkiri/admin/dto/AdminPostResponse.java) | `id: Long`<br>`category: String`<br>`title: String`<br>`author: CommunityAuthorResponse`<br>`createdAt: LocalDateTime`<br>`views: long`<br>`likes: long`<br>`commentCount: long`<br>`reported: boolean` |
| [AdminReportDetailResponse](src/main/java/com/kkirikkiri/admin/dto/AdminReportDetailResponse.java) | `id: Long`<br>`type: ReportType`<br>`targetId: Long`<br>`targetUser: TargetUser`<br>`targetContent: String`<br>`reporter: Reporter`<br>`reason: String`<br>`description: String`<br>`createdAt: LocalDateTime`<br>`status: ReportStatus` |
| [AdminReportDetailResponse.Reporter](src/main/java/com/kkirikkiri/admin/dto/AdminReportDetailResponse.java) | `id: Long`<br>`nickname: String`<br>`schoolEmail: String` |
| [AdminReportDetailResponse.TargetUser](src/main/java/com/kkirikkiri/admin/dto/AdminReportDetailResponse.java) | `id: Long`<br>`nickname: String`<br>`schoolEmail: String`<br>`status: UserStatus` |
| [AdminReportListRequest](src/main/java/com/kkirikkiri/admin/dto/AdminReportListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1), Max(100))<br>`q: String` (없음)<br>`type: String` (Pattern(regexp = "USER&#124;POST&#124;COMMENT"))<br>`status: String` (Pattern(regexp = "PENDING&#124;COMPLETED&#124;REJECTED")) |
| [AdminReportProcessRequest](src/main/java/com/kkirikkiri/admin/dto/AdminReportProcessRequest.java) | `action: String` (NotBlank, Pattern(regexp = "COMPLETE&#124;REJECT&#124;SUSPEND_USER&#124;DELETE_TARGET")) |
| [AdminReportResponse](src/main/java/com/kkirikkiri/admin/dto/AdminReportResponse.java) | `id: Long`<br>`type: ReportType`<br>`targetContent: String`<br>`reporter: CommunityAuthorResponse`<br>`targetUser: CommunityAuthorResponse`<br>`reason: String`<br>`createdAt: LocalDateTime`<br>`status: ReportStatus` |
| [AdminScheduleCrawlStatusResponse](src/main/java/com/kkirikkiri/admin/dto/AdminScheduleCrawlStatusResponse.java) | `category: ScheduleCrawlCategory`<br>`status: ScheduleCrawlStatus`<br>`lastRunAt: LocalDateTime`<br>`recentCollectedCount: long`<br>`totalCollectedCount: long`<br>`lastCompletedAt: LocalDateTime`<br>`lastFailure: LastFailure` |
| [AdminScheduleCrawlStatusResponse.LastFailure](src/main/java/com/kkirikkiri/admin/dto/AdminScheduleCrawlStatusResponse.java) | `occurredAt: LocalDateTime`<br>`message: String` |
| [EmailVerificationSendRequest](src/main/java/com/kkirikkiri/auth/dto/EmailVerificationSendRequest.java) | `email: String` (NotBlank, Email, Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr"))<br>`purpose: String` (NotBlank, Pattern(regexp = "SIGNUP")) |
| [EmailVerificationSendResponse](src/main/java/com/kkirikkiri/auth/dto/EmailVerificationSendResponse.java) | `expiresInSeconds: long` |
| [EmailVerificationVerifyRequest](src/main/java/com/kkirikkiri/auth/dto/EmailVerificationVerifyRequest.java) | `email: String` (NotBlank, Email, Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr"))<br>`code: String` (NotBlank, Pattern(regexp = "[0-9]{6}"))<br>`purpose: String` (NotBlank, Pattern(regexp = "SIGNUP")) |
| [EmailVerificationVerifyResponse](src/main/java/com/kkirikkiri/auth/dto/EmailVerificationVerifyResponse.java) | `verified: boolean`<br>`verificationToken: String` |
| [LoginEmailCheckRequest](src/main/java/com/kkirikkiri/auth/dto/LoginEmailCheckRequest.java) | `email: String` (NotBlank, Email, Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr")) |
| [LoginEmailCheckResponse](src/main/java/com/kkirikkiri/auth/dto/LoginEmailCheckResponse.java) | `registered: boolean` |
| [LoginRequest](src/main/java/com/kkirikkiri/auth/dto/LoginRequest.java) | `email: String` (NotBlank, Email, Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr"))<br>`password: String` (PasswordByteLength, NotBlank) |
| [LoginResponse](src/main/java/com/kkirikkiri/auth/dto/LoginResponse.java) | `accessToken: String`<br>`user: LoginUser` |
| [LoginResponse.LoginUser](src/main/java/com/kkirikkiri/auth/dto/LoginResponse.java) | `id: Long`<br>`schoolEmail: String`<br>`nickname: String`<br>`role: String` |
| [PasswordResetConfirmRequest](src/main/java/com/kkirikkiri/auth/dto/PasswordResetConfirmRequest.java) | `resetToken: String` (NotBlank)<br>`newPassword: String` (PasswordByteLength, NotBlank, Size(min = 8), Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$"))<br>`newPasswordConfirm: String` (PasswordByteLength, NotBlank) |
| [PasswordResetRequest](src/main/java/com/kkirikkiri/auth/dto/PasswordResetRequest.java) | `email: String` (NotBlank, Email, Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr")) |
| [PasswordResetResponse](src/main/java/com/kkirikkiri/auth/dto/PasswordResetResponse.java) | `sent: boolean` |
| [SignupAvailabilityResponse](src/main/java/com/kkirikkiri/auth/dto/SignupAvailabilityResponse.java) | `schoolEmailAvailable: boolean`<br>`nicknameAvailable: boolean` |
| [SignupRequest](src/main/java/com/kkirikkiri/auth/dto/SignupRequest.java) | `password: String` (PasswordByteLength, NotBlank, Size(min = 8), Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$"))<br>`passwordConfirm: String` (PasswordByteLength, NotBlank)<br>`nickname: String` (NotBlank, Size(min = 2, max = 8), Pattern(regexp = "[가-힣A-Za-z0-9]+"))<br>`schoolEmail: String` (NotBlank, Email, Pattern(regexp = "[^@\\s]{1,64}@hknu\\.ac\\.kr"))<br>`emailVerificationToken: String` (NotBlank)<br>`gender: String` (NotBlank, Pattern(regexp = "남자&#124;여자"))<br>`dormitory: String` (NotBlank, Size(max = 30))<br>`faculty: String` (NotBlank, Size(max = 100))<br>`major: String` (NotBlank, Size(max = 150))<br>`birthYear: Short` (NotNull, Min(value = 1900))<br>`admissionYear: String` (NotBlank, Pattern(regexp = "[0-9]{2}"))<br>`grade: String` (NotBlank, Pattern(regexp = "[1-5]학년"))<br>`mbtiEnergy: String` (NotBlank, Pattern(regexp = "[EI]"))<br>`mbtiInformation: String` (NotBlank, Pattern(regexp = "[NS]"))<br>`mbtiDecision: String` (NotBlank, Pattern(regexp = "[FT]"))<br>`mbtiLifestyle: String` (NotBlank, Pattern(regexp = "[PJ]")) |
| [SignupResponse](src/main/java/com/kkirikkiri/auth/dto/SignupResponse.java) | `userId: Long`<br>`nickname: String` |
| [CalendarCategoryCreateRequest](src/main/java/com/kkirikkiri/calendar/dto/CalendarCategoryCreateRequest.java) | `label: String` (NotBlank, Size(min = 1, max = 30))<br>`color: String` (NotBlank, Pattern(regexp = "#[0-9a-fA-F]{6}")) |
| [CalendarCategoryResponse](src/main/java/com/kkirikkiri/calendar/dto/CalendarCategoryResponse.java) | `id: Long`<br>`code: String`<br>`label: String`<br>`color: String`<br>`fixed: boolean`<br>`enabled: boolean` |
| [CalendarCategorySettingRequest](src/main/java/com/kkirikkiri/calendar/dto/CalendarCategorySettingRequest.java) | `id: Long` (NotNull, Positive)<br>`label: String` (NotBlank, Size(min = 1, max = 30))<br>`color: String` (NotBlank, Pattern(regexp = "#[0-9a-fA-F]{6}"))<br>`enabled: Boolean` (NotNull) |
| [CalendarCategoryUpdateRequest](src/main/java/com/kkirikkiri/calendar/dto/CalendarCategoryUpdateRequest.java) | `label: String` (Pattern(regexp = "(?s).*\\S.*"), Size(min = 1, max = 30))<br>`color: String` (Pattern(regexp = "#[0-9a-fA-F]{6}"))<br>`enabled: Boolean` (없음) |
| [CalendarEventCreateRequest](src/main/java/com/kkirikkiri/calendar/dto/CalendarEventCreateRequest.java) | `title: String` (NotBlank, Size(max = 80))<br>`startDate: LocalDate` (NotNull)<br>`endDate: LocalDate` (NotNull)<br>`startTime: String` (Pattern(regexp = "([01][0-9]&#124;2[0-3]):[0-5][0-9]"))<br>`endTime: String` (Pattern(regexp = "([01][0-9]&#124;2[0-3]):[0-5][0-9]"))<br>`categoryId: Long` (NotNull, Positive)<br>`description: String` (Size(max = 2000)) |
| [CalendarEventListRequest](src/main/java/com/kkirikkiri/calendar/dto/CalendarEventListRequest.java) | `from: LocalDate` (NotNull, DateTimeFormat(iso = DateTimeFormat.ISO.DATE))<br>`to: LocalDate` (NotNull, DateTimeFormat(iso = DateTimeFormat.ISO.DATE))<br>`categories: List< String>` (NotBlank) |
| [CalendarEventResponse](src/main/java/com/kkirikkiri/calendar/dto/CalendarEventResponse.java) | `id: Long`<br>`title: String`<br>`startDate: LocalDate`<br>`endDate: LocalDate`<br>`startTime: LocalTime`<br>`endTime: LocalTime`<br>`category: String`<br>`categoryId: Long`<br>`description: String`<br>`editable: boolean`<br>`source: String` |
| [CalendarEventUpdateRequest](src/main/java/com/kkirikkiri/calendar/dto/CalendarEventUpdateRequest.java) | `title: String` (Pattern(regexp = "(?s).*\\S.*"), Size(max = 80))<br>`startDate: LocalDate` (없음)<br>`endDate: LocalDate` (없음)<br>`startTime: String` (Pattern(regexp = "([01][0-9]&#124;2[0-3]):[0-5][0-9]"))<br>`endTime: String` (Pattern(regexp = "([01][0-9]&#124;2[0-3]):[0-5][0-9]"))<br>`categoryId: Long` (Positive)<br>`description: String` (Size(max = 2000)) |
| [ChatMessageBroadcastResponse](src/main/java/com/kkirikkiri/chat/dto/ChatMessageBroadcastResponse.java) | `id: Long`<br>`roomId: Long`<br>`senderId: Long`<br>`text: String`<br>`sentAt: LocalDateTime`<br>`read: boolean` |
| [ChatMessageListRequest](src/main/java/com/kkirikkiri/chat/dto/ChatMessageListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1), Max(100))<br>`q: String` (없음)<br>`beforeId: Long` (Positive)<br>`afterId: Long` (Positive) |
| [ChatMessageReadStatusResponse](src/main/java/com/kkirikkiri/chat/dto/ChatMessageReadStatusResponse.java) | `id: Long`<br>`read: boolean` |
| [ChatMessageResponse](src/main/java/com/kkirikkiri/chat/dto/ChatMessageResponse.java) | `id: Long`<br>`senderId: Long`<br>`senderType: String`<br>`text: String`<br>`sentAt: LocalDateTime`<br>`read: boolean` |
| [ChatMessageSendRequest](src/main/java/com/kkirikkiri/chat/dto/ChatMessageSendRequest.java) | `text: String` (NotBlank, CodePointLength(max = ChatMessage.MAX_CONTENT_LENGTH = 2000); message는 Size 검증 메시지 키를 재사용) |
| [ChatReadRequest](src/main/java/com/kkirikkiri/chat/dto/ChatReadRequest.java) | `messageIds: List<Long>` (NotEmpty, Size(max = 100); 각 원소 NotNull, Positive) |
| [ChatRoomCreateRequest](src/main/java/com/kkirikkiri/chat/dto/ChatRoomCreateRequest.java) | `targetUserId: Long` (annotation 없음; ROOMMATE 필수·양수는 서비스 검증)<br>`category: ChatRoomCategory` (NotNull)<br>`sourcePostId: Long` (없음)<br>`sourceCommentId: Long` (없음)<br>`sourceFrom: String` (Pattern(regexp = "POST&#124;COMMENT")) |
| [ChatRoomListRequest](src/main/java/com/kkirikkiri/chat/dto/ChatRoomListRequest.java) | `category: String` (Pattern(regexp = "ALL&#124;ROOMMATE&#124;COMMUNITY"))<br>`q: String` (없음)<br>`page: Integer` (Min(0))<br>`size: Integer` (Min(1), Max(100)) |
| [ChatRoomCountsResponse](src/main/java/com/kkirikkiri/chat/dto/ChatRoomCountsResponse.java) | `all: long`<br>`roommate: long`<br>`community: long`<br>`pinned: long` |
| [ChatRoomPageResponse](src/main/java/com/kkirikkiri/chat/dto/ChatRoomPageResponse.java) | `content: List<ChatRoomResponse>`<br>`page: int`<br>`size: int`<br>`totalElements: long`<br>`totalPages: int`<br>`first: boolean`<br>`last: boolean`<br>`counts: ChatRoomCountsResponse` |
| [ChatRoomResponse](src/main/java/com/kkirikkiri/chat/dto/ChatRoomResponse.java) | `id: Long`<br>`category: ChatRoomCategory`<br>`counterpart: CounterpartResponse`<br>`sourcePost: Long`<br>`unreadCount: long`<br>`pinned: boolean`<br>`muted: boolean`<br>`lastMessage: LastMessageResponse` |
| [ChatRoomResponse.CounterpartResponse](src/main/java/com/kkirikkiri/chat/dto/ChatRoomResponse.java) | `id: Long`<br>`nickname: String`<br>`profileImageUrl: String`<br>`dormitory: String`<br>`admissionYear: String`<br>`major: String`<br>`matchScore: Integer`<br>`introduction: String`<br>`tags: List<String>` |
| [ChatRoomResponse.LastMessageResponse](src/main/java/com/kkirikkiri/chat/dto/ChatRoomResponse.java) | `text: String`<br>`sentAt: LocalDateTime` |
| [ChatRoomSettingsRequest](src/main/java/com/kkirikkiri/chat/dto/ChatRoomSettingsRequest.java) | `pinned: Boolean` (없음)<br>`muted: Boolean` (없음) |
| [CommunityCommentCreateRequest](src/main/java/com/kkirikkiri/community/comment/dto/CommunityCommentCreateRequest.java) | `content: String` (NotBlank, Size(max = 1000))<br>`anonymous: Boolean` (없음) |
| [CommunityCommentResponse](src/main/java/com/kkirikkiri/community/comment/dto/CommunityCommentResponse.java) | `id: Long`<br>`parentCommentId: Long`<br>`content: String`<br>`author: CommunityAuthorResponse`<br>`createdAt: LocalDateTime`<br>`likes: long`<br>`liked: boolean`<br>`anonymous: boolean`<br>`anonymousNumber: Long`<br>`mine: boolean` |
| [CommunityCommentUpdateRequest](src/main/java/com/kkirikkiri/community/comment/dto/CommunityCommentUpdateRequest.java) | `content: String` (NotBlank, Size(max = 1000)) |
| [MyCommunityCommentResponse](src/main/java/com/kkirikkiri/community/comment/dto/MyCommunityCommentResponse.java) | `id: Long`<br>`content: String`<br>`createdAt: LocalDateTime`<br>`isReply: boolean`<br>`postId: Long`<br>`postTitle: String`<br>`category: String` |
| [CommunityAuthorResponse](src/main/java/com/kkirikkiri/community/post/dto/CommunityAuthorResponse.java) | `id: Long`<br>`nickname: String`<br>`major: String` |
| [CommunityPostCreateRequest](src/main/java/com/kkirikkiri/community/post/dto/CommunityPostCreateRequest.java) | `category: String` (NotBlank, Pattern(regexp = "자유게시판&#124;나눔게시판&#124;택배게시판&#124;민원게시판"))<br>`title: String` (NotBlank, Size(max = 100))<br>`content: String` (NotBlank, Size(max = 5000))<br>`anonymous: boolean` (없음) |
| [CommunityPostDetailResponse](src/main/java/com/kkirikkiri/community/post/dto/CommunityPostDetailResponse.java) | `id: Long`<br>`category: String`<br>`dormitory: String`<br>`title: String`<br>`content: String`<br>`author: CommunityAuthorResponse`<br>`anonymous: boolean`<br>`createdAt: LocalDateTime`<br>`views: int`<br>`likes: long`<br>`liked: boolean`<br>`reported: boolean`<br>`images: List<PostImageResponse>`<br>`comments: List<T>`<br>`mine: boolean` |
| [CommunityPostListRequest](src/main/java/com/kkirikkiri/community/post/dto/CommunityPostListRequest.java) | `page: Integer` (Min(0))<br>`size: Integer` (Min(1), Max(100))<br>`category: String` (Pattern(regexp = "자유게시판&#124;나눔게시판&#124;택배게시판&#124;민원게시판"))<br>`q: String` (없음)<br>`dormitory: String` (Pattern(regexp = "나래&#124;호연&#124;비봉&#124;창조"))<br>`sort: String` (Pattern(regexp = "LATEST&#124;VIEWS&#124;LIKES")) |
| [CommunityPostResponse](src/main/java/com/kkirikkiri/community/post/dto/CommunityPostResponse.java) | `id: Long`<br>`category: String`<br>`dormitory: String`<br>`title: String`<br>`contentPreview: String`<br>`author: CommunityAuthorResponse`<br>`anonymous: boolean`<br>`createdAt: LocalDateTime`<br>`views: int`<br>`likes: long`<br>`commentCount: long`<br>`hasImages: boolean`<br>`liked: boolean`<br>`reported: boolean`<br>`mine: boolean` |
| [CommunityPostUpdateRequest](src/main/java/com/kkirikkiri/community/post/dto/CommunityPostUpdateRequest.java) | `category: String` (NotBlank, Pattern(regexp = "자유게시판&#124;나눔게시판&#124;택배게시판&#124;민원게시판"))<br>`title: String` (NotBlank, Size(max = 100))<br>`content: String` (NotBlank, Size(max = 5000))<br>`keepImageIds: List< Long>` (Size(max = 5), NotNull, Positive)<br>`anonymous: Boolean` (없음) |
| [PostImageResponse](src/main/java/com/kkirikkiri/community/post/dto/PostImageResponse.java) | `id: Long`<br>`url: String` |
| [PostImagesRequest](src/main/java/com/kkirikkiri/community/post/dto/PostImagesRequest.java) | `images: List< MultipartFile>` (Size(max = 5), NotNull) |
| [AiProfileAnalysisInput](src/main/java/com/kkirikkiri/matching/dto/AiProfileAnalysisInput.java) | `matchScore: int`<br>`matchedItems: List<PreferenceCriterionCode>`<br>`mismatchedItems: List<PreferenceCriterionCode>` |
| [AiProfileAnalysisResponse](src/main/java/com/kkirikkiri/matching/dto/AiProfileAnalysisResponse.java) | `targetUserId: Long`<br>`matchScore: int`<br>`analysis: String` |
| [RoommateDetailResponse](src/main/java/com/kkirikkiri/matching/dto/RoommateDetailResponse.java) | `id: Long`<br>`nickname: String`<br>`admissionYear: String`<br>`grade: String`<br>`faculty: String`<br>`major: String`<br>`dormitory: String`<br>`mbti: String`<br>`introduction: String`<br>`tags: List<String>`<br>`profileImageUrl: String`<br>`matchScore: Integer`<br>`liked: boolean`<br>`answers: Answers` |
| [RoommateDetailResponse.Answers](src/main/java/com/kkirikkiri/matching/dto/RoommateDetailResponse.java) | `sleepTime: String`<br>`wakeTime: String`<br>`sleepTalking: String`<br>`snoring: String`<br>`teethGrinding: String`<br>`sleepSensitivity: String`<br>`indoorEating: String`<br>`smoking: String`<br>`lightingPreference: String`<br>`phoneCall: String`<br>`mediaSound: String`<br>`showerTime: String`<br>`showerDuration: String`<br>`cleaningFrequency: String`<br>`cleaningStyle: String`<br>`meal: String`<br>`roommateInteraction: String`<br>`temperaturePreference: String`<br>`scentProductUse: String`<br>`desktopUse: String`<br>`sharedItems: String`<br>`dormStayDays: List<String>`<br>`mealPlan: List<String>`<br>`completed: boolean` |
| [RoommateFilterRequest](src/main/java/com/kkirikkiri/matching/dto/RoommateFilterRequest.java) | `sleepTime: List< String>` (NotBlank, Size(max = 50))<br>`wakeTime: List< String>` (NotBlank, Size(max = 50))<br>`sleepTalking: List< String>` (NotBlank, Size(max = 20))<br>`snoring: List< String>` (NotBlank, Size(max = 20))<br>`teethGrinding: List< String>` (NotBlank, Size(max = 20))<br>`sleepSensitivity: List< String>` (NotBlank, Size(max = 50))<br>`indoorEating: List< String>` (NotBlank, Size(max = 50))<br>`smoking: List< String>` (NotBlank, Size(max = 30))<br>`lightingPreference: List< String>` (NotBlank, Size(max = 30))<br>`phoneCall: List< String>` (NotBlank, Size(max = 50))<br>`mediaSound: List< String>` (NotBlank, Size(max = 100))<br>`showerTime: List< String>` (NotBlank, Size(max = 30))<br>`showerDuration: List< String>` (NotBlank, Size(max = 30))<br>`cleaningFrequency: List< String>` (NotBlank, Size(max = 30))<br>`cleaningStyle: List< String>` (NotBlank, Size(max = 50))<br>`dormStayDays: List< String>` (NotBlank, Size(max = 30))<br>`mealPlan: List< String>` (NotBlank, Size(max = 30))<br>`meal: List< String>` (NotBlank, Size(max = 60))<br>`roommateInteraction: List< String>` (NotBlank, Size(max = 50))<br>`temperaturePreference: List< String>` (NotBlank, Size(max = 30))<br>`scentProductUse: List< String>` (NotBlank, Size(max = 50))<br>`desktopUse: List< String>` (NotBlank, Size(max = 50))<br>`sharedItems: List< String>` (NotBlank, Size(max = 50)) |
| [RoommatePageResponse](src/main/java/com/kkirikkiri/matching/dto/RoommatePageResponse.java) | `content: List<T>`<br>`page: int`<br>`size: int`<br>`totalElements: long`<br>`totalPages: int`<br>`first: boolean`<br>`last: boolean` |
| [RoommateProfileResponse](src/main/java/com/kkirikkiri/matching/dto/RoommateProfileResponse.java) | `id: Long`<br>`nickname: String`<br>`admissionYear: String`<br>`grade: String`<br>`faculty: String`<br>`major: String`<br>`dormitory: String`<br>`mbti: String`<br>`introduction: String`<br>`tags: List<String>`<br>`profileImageUrl: String`<br>`matchScore: Integer`<br>`liked: boolean` |
| [ReportCreateRequest](src/main/java/com/kkirikkiri/report/dto/ReportCreateRequest.java) | `targetType: String` (NotBlank, Pattern(regexp = "USER&#124;POST&#124;COMMENT"))<br>`targetId: Long` (NotNull, Positive)<br>`reason: String` (NotBlank, Pattern(regexp = "욕설·비방&#124;광고·홍보&#124;음란성 콘텐츠&#124;개인정보 노출&#124;도배·스팸&#124;기타"))<br>`description: String` (없음) |
| [ProfileSurveyRequest](src/main/java/com/kkirikkiri/survey/dto/ProfileSurveyRequest.java) | `sleepTime: String` (NotBlank, Size(max = 50))<br>`wakeTime: String` (NotBlank, Size(max = 50))<br>`sleepTalking: String` (NotBlank, Size(max = 20))<br>`snoring: String` (NotBlank, Size(max = 20))<br>`teethGrinding: String` (NotBlank, Size(max = 20))<br>`sleepSensitivity: String` (NotBlank, Size(max = 50))<br>`indoorEating: String` (NotBlank, Size(max = 50))<br>`smoking: String` (NotBlank, Size(max = 30))<br>`lightingPreference: String` (NotBlank, Size(max = 30))<br>`phoneCall: String` (NotBlank, Size(max = 50))<br>`mediaSound: String` (NotBlank, Size(max = 100))<br>`showerTime: String` (NotBlank, Size(max = 30))<br>`showerDuration: String` (NotBlank, Size(max = 30))<br>`cleaningFrequency: String` (NotBlank, Size(max = 30))<br>`cleaningStyle: String` (NotBlank, Size(max = 50))<br>`dormStayDays: List< String>` (NotEmpty, NotBlank, Size(max = 30))<br>`mealPlan: List< String>` (NotEmpty, NotBlank, Size(max = 30))<br>`meal: String` (NotBlank, Size(max = 60))<br>`roommateInteraction: String` (NotBlank, Size(max = 50))<br>`temperaturePreference: String` (NotBlank, Size(max = 30))<br>`scentProductUse: String` (NotBlank, Size(max = 50))<br>`desktopUse: String` (NotBlank, Size(max = 50))<br>`sharedItems: String` (NotBlank, Size(max = 50))<br>`introduction: String` (Size(max = 200)) |
| [ProfileSurveyResponse](src/main/java/com/kkirikkiri/survey/dto/ProfileSurveyResponse.java) | `completed: boolean`<br>`answers: Answers`<br>`introduction: String` |
| [ProfileSurveyResponse.Answers](src/main/java/com/kkirikkiri/survey/dto/ProfileSurveyResponse.java) | `sleepTime: String`<br>`wakeTime: String`<br>`sleepTalking: String`<br>`snoring: String`<br>`teethGrinding: String`<br>`sleepSensitivity: String`<br>`indoorEating: String`<br>`smoking: String`<br>`lightingPreference: String`<br>`phoneCall: String`<br>`mediaSound: String`<br>`showerTime: String`<br>`showerDuration: String`<br>`cleaningFrequency: String`<br>`cleaningStyle: String`<br>`dormStayDays: List<String>`<br>`mealPlan: List<String>`<br>`meal: String`<br>`roommateInteraction: String`<br>`temperaturePreference: String`<br>`scentProductUse: String`<br>`desktopUse: String`<br>`sharedItems: String` |
| [RoommatePreferenceRequest](src/main/java/com/kkirikkiri/survey/dto/RoommatePreferenceRequest.java) | `department: String` (NotBlank, Size(max = 60))<br>`age: List< String>` (NotEmpty, NotBlank, Size(max = 60))<br>`grade: List< String>` (NotEmpty, NotBlank, Size(max = 60))<br>`bedtime: List< String>` (NotEmpty, NotBlank, Size(max = 60))<br>`wakeTime: List< String>` (NotEmpty, NotBlank, Size(max = 60))<br>`sleepTalking: String` (NotBlank, Size(max = 50))<br>`snoring: String` (NotBlank, Size(max = 50))<br>`teethGrinding: String` (NotBlank, Size(max = 50))<br>`smoking: String` (NotBlank, Size(max = 50))<br>`lightingPreference: String` (NotBlank, Size(max = 30))<br>`showerTime: String` (NotBlank, Size(max = 30))<br>`showerDuration: String` (NotBlank, Size(max = 30))<br>`cleaningFrequency: List< String>` (NotEmpty, NotBlank, Size(max = 60))<br>`cleaningStyle: String` (NotBlank, Size(max = 60))<br>`interaction: String` (NotBlank, Size(max = 60))<br>`meal: String` (NotBlank, Size(max = 60))<br>`sharedItems: String` (NotBlank, Size(max = 60))<br>`temperature: String` (NotBlank, Size(max = 50))<br>`weights: Map< String, Integer>` (NotEmpty, Size(min = 18, max = 18), NotBlank, Size(max = 40), NotNull, Min(value = 1), Max(value = 5)) |
| [RoommatePreferenceResponse](src/main/java/com/kkirikkiri/survey/dto/RoommatePreferenceResponse.java) | `completed: boolean`<br>`answers: Answers` |
| [RoommatePreferenceResponse.Answers](src/main/java/com/kkirikkiri/survey/dto/RoommatePreferenceResponse.java) | `department: String`<br>`age: List<String>`<br>`grade: List<String>`<br>`bedtime: List<String>`<br>`wakeTime: List<String>`<br>`sleepTalking: String`<br>`snoring: String`<br>`teethGrinding: String`<br>`smoking: String`<br>`lightingPreference: String`<br>`showerTime: String`<br>`showerDuration: String`<br>`cleaningFrequency: List<String>`<br>`cleaningStyle: String`<br>`interaction: String`<br>`meal: String`<br>`sharedItems: String`<br>`temperature: String`<br>`weights: Map<String, Integer>` |
| [PasswordChangeRequest](src/main/java/com/kkirikkiri/user/dto/PasswordChangeRequest.java) | `currentPassword: String` (PasswordByteLength, NotBlank)<br>`newPassword: String` (PasswordByteLength, NotBlank, Size(min = 8), Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{8,}$"))<br>`newPasswordConfirm: String` (PasswordByteLength, NotBlank) |
| [UserResponse](src/main/java/com/kkirikkiri/user/dto/UserResponse.java) | `id: Long`<br>`nickname: String`<br>`schoolEmail: String`<br>`gender: String`<br>`dormitory: String`<br>`faculty: String`<br>`major: String`<br>`birthYear: Short`<br>`admissionYear: String`<br>`grade: String`<br>`mbtiEnergy: String`<br>`mbtiInformation: String`<br>`mbtiDecision: String`<br>`mbtiLifestyle: String` |
| [UserUpdateRequest](src/main/java/com/kkirikkiri/user/dto/UserUpdateRequest.java) | `nickname: String` (Size(min = 2, max = 8), Pattern(regexp = "[가-힣A-Za-z0-9]+"))<br>`dormitory: String` (Size(max = 30), Pattern(regexp = "(?s).*\\S.*"))<br>`grade: String` (Pattern(regexp = "[1-5]학년"))<br>`faculty: String` (Size(max = 100), Pattern(regexp = "(?s).*\\S.*"))<br>`major: String` (Size(max = 150), Pattern(regexp = "(?s).*\\S.*"))<br>`mbtiEnergy: String` (Pattern(regexp = "[EI]"))<br>`mbtiInformation: String` (Pattern(regexp = "[NS]"))<br>`mbtiDecision: String` (Pattern(regexp = "[FT]"))<br>`mbtiLifestyle: String` (Pattern(regexp = "[PJ]")) |

### 19.3 nullable·서비스 검증과 오류 처리

- 설문 미저장 GET은 answers=null, 선호 조건 미저장도 해당 DTO 계약을 따른다. 저장된 프로필 설문은 현재 23개 필드다. 선택지·배열 중복·가중치 key 18개·상관없음 조합은 annotation 외 서비스 검증도 적용한다. hairDryer/bugHandling은 현재 요청·응답·Entity 필드가 아니다.
- UserResponse.major, 룸메이트 목록·상세의 major/introduction/profileImageUrl/matchScore는 null일 수 있다. 비로그인 전체/상세 조회는 매칭 점수 null·liked=false다. recommended 필드는 반환하지 않는다. 로그인해도 계산 불가한 점수는 null일 수 있다. JWT가 제시된 요청의 유효성 검사와 공개 접근 허용은 별개다.
- ChatRoomResponse.sourcePost/lastMessage는 null일 수 있다. 익명 상대 CounterpartResponse는 nickname="익명", tags=[], 그 외 필드 null이다. 익명 메시지 senderId는 null이다. 연결 후 권한은 10.4의 실제 재검증 경로를 따르며, JVM 간 중계 미지원은 10.8의 별도 제한이다.
- CalendarEventResponse.startTime/endTime/description/source는 null일 수 있다. source는 Entity의 source_name이며 sourceUrl/collectedAt는 이 DTO에 없다. AdminScheduleCrawlStatusResponse의 실행 시각·상태·실패 값은 이력 없음 시 null이다. 학교 수집은 실행별 이력을 저장하며 기숙사 수집기는 미구현이다. 관리자 응답에서 프로필이 없는 회원의 nickname/major/dormitory 등 프로필 필드도 null일 수 있다. 신고 상세 description은 선택값이다. 처리 시각·담당자는 현재 신고 응답 필드가 아니다.
- SignupRequest와 비밀번호 변경/재설정의 확인 비밀번호 일치, 출생연도 현재 연도 이하, 캘린더 날짜 범위·동일 날짜 시간 순서 등 @AssertTrue 객체 검증을 적용한다. 회원정보 PATCH의 null은 유지이며 세부전공 없음 선택은 null 저장, 학부·전공 활성 여부/소속과 중복 닉네임은 서비스에서 검증한다. 프로필 설문·선호 저장은 전체 입력이며 부분 PATCH처럼 처리하지 않는다.
- PostImage 유지 정책은 keepImageIds 생략/null/[] 모두 기존 이미지 제거다. 현재 프론트가 유지할 ID 목록을 전달한다. 익명 게시글 author.id는 null이며 목록·상세에 mine을 제공한다. 클라이언트는 author.id 비교 대신 mine을 사용하고 채팅은 출처 ID로 연결해야 한다.
- CommunityCommentResponse의 익명 댓글·답글은 author.id=null, author.major=null, author.nickname="익명{anonymousNumber}"다. mine은 서버 인증 사용자와 내부 작성자의 일치 여부이며 비로그인은 false다. 일반 댓글·답글의 작성자 정보와 게시글별 익명 번호는 유지한다.
- REST 오류는 success=false/message/data=null/errorCode, DTO 필드 오류에만 fieldErrors가 추가될 수 있다. CustomException은 아래 ErrorCode와 기본 메시지로 반환하며 내부 예외 메시지를 공개하지 않는다. 대상 없음이 모두 404인 계약은 아니며 일반 서비스 오류는 INVALID_REQUEST 400이다. MVC의 405/413/415 등은 handler의 실제 상태를 유지한다. STOMP 오류는 REST HTTP/envelope가 아니다.

| ErrorCode | 기본 HTTP 상태 | 기본 메시지 |
| --- | --- | --- |
| MATCH_ANALYSIS_UNAVAILABLE | 409 | 매칭 결과를 계산할 수 없습니다. 프로필과 선호 설문을 확인해 주세요. |
| AI_GATEWAY_UNAVAILABLE | 503 | AI 분석 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요. |
| AI_GATEWAY_INVALID_RESPONSE | 502 | AI 분석 응답을 확인할 수 없습니다. 다시 시도해 주세요. |
| UNAUTHORIZED | 401 | 인증 정보가 유효하지 않습니다. |
| FORBIDDEN | 403 | 접근 권한이 없습니다. |
| INVALID_REQUEST | 400 | 요청을 처리할 수 없습니다. |
| PASSWORD_RESET_TOKEN_INVALID | 400 | 재설정 토큰이 올바르지 않습니다. |
| PASSWORD_RESET_TOKEN_EXPIRED | 400 | 재설정 토큰이 만료되었습니다. 이메일을 다시 요청해 주세요. |
| PASSWORD_RESET_TOKEN_ALREADY_USED | 400 | 이미 사용된 재설정 토큰입니다. 이메일을 다시 요청해 주세요. |
| PASSWORD_RESET_ACCOUNT_INACTIVE | 400 | 계정이 활성 상태가 아니어서 비밀번호를 변경할 수 없습니다. |
| VALIDATION_ERROR | 400 | 입력값을 확인해 주세요. |
| AUTH_REQUEST_LIMITED | 429 | 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. |
| AI_ANALYSIS_RATE_LIMITED | 429 | AI 분석 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. |
| AI_ANALYSIS_BUSY | 503 | AI 분석 요청이 몰리고 있습니다. 잠시 후 다시 시도해 주세요. |
| EMAIL_VERIFICATION_COOLDOWN | 429 | 인증번호는 잠시 후 다시 요청해 주세요. |
| EMAIL_VERIFICATION_NOT_FOUND | 400 | 먼저 인증번호를 요청해 주세요. |
| EMAIL_VERIFICATION_INVALID | 400 | 인증번호가 올바르지 않습니다. |
| EMAIL_VERIFICATION_EXPIRED | 400 | 인증번호가 만료되었습니다. 새 인증번호를 요청해 주세요. |
| EMAIL_VERIFICATION_ALREADY_USED | 400 | 이미 사용한 인증번호입니다. 새 인증번호를 요청해 주세요. |
| EMAIL_VERIFICATION_TOKEN_INVALID | 400 | 이메일 인증이 만료되었거나 유효하지 않습니다. 다시 인증해 주세요. |
| EMAIL_ALREADY_REGISTERED | 409 | 이미 가입된 학교 이메일입니다. 다른 이메일을 사용해 주세요. |
| DUPLICATE_ACCOUNT_VALUE | 409 | 학교 이메일 또는 닉네임이 이미 사용 중입니다. |
| DORMITORY_NOT_FOUND | 400 | 선택한 생활관을 사용할 수 없습니다. 다시 선택해 주세요. |
| FACULTY_NOT_FOUND | 400 | 선택한 학부를 사용할 수 없습니다. 다시 선택해 주세요. |
| MAJOR_NOT_FOUND | 400 | 선택한 전공을 사용할 수 없습니다. 다시 선택해 주세요. |
| ACCOUNT_SAVE_CONFLICT | 409 | 입력한 회원정보가 이미 사용 중입니다. 다른 값으로 시도해 주세요. |
| ACCOUNT_UPDATE_CONFLICT | 409 | 다른 변경 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요. |
| INTERNAL_SERVER_ERROR | 500 | 서버 오류가 발생했습니다. |

근거: [ApiResponse](src/main/java/com/kkirikkiri/global/response/ApiResponse.java), [GlobalExceptionHandler](src/main/java/com/kkirikkiri/global/exception/GlobalExceptionHandler.java), [ErrorCode](src/main/java/com/kkirikkiri/global/exception/ErrorCode.java), [SecurityConfig](src/main/java/com/kkirikkiri/global/config/SecurityConfig.java). 이 표는 코드 대조이며 SMTP/AI/프록시/운영 DB 응답을 실행 검증한 결과가 아니다.

### 19.4 문서 수정으로 해결되지 않는 보안·배포 위험

2026-10-18 검증 보완: 관심 프로필 등록/취소, 채팅 설정, 신고 생성은 기존 사용자 행의
DB 잠금을 첫 읽기 전에 획득하여 동일 사용자 요청을 인스턴스 간 직렬화한다.
관심 취소의 관계 없음은 기존 400 INVALID_REQUEST를 유지한다.
STOMP는 SEND/SUBSCRIBE 때 JWT·활성 계정을 다시 확인하며 기존 구독의 실제 메시지
전달 직전에도 JWT·계정·방 참여 권한을 확인한다. 실패 시 해당 수신자 전달을 차단한다.
Simple Broker의 서로 다른 JVM 사이 메시지 중계는 제공하지 않는다.
실행 범위와 잔여 검증은 [1~3단계 종합 보고서](STAGES_1_3_VALIDATION.md)를 따른다.

- #3 기본/prod의 DDL validate·SQL never·local 전용 시드와 #4 Gradle 테스트 H2 격리·연결 전 안전 검사를 구현했다. 적용 조건과 실제 운영에서 확인할 사항은 [DEPLOYMENT.md](DEPLOYMENT.md)를 따른다. 채팅 #12/#13의 현재 구현은 10.4/10.6으로 대조했으며 과거 미해결 설명을 현재 결함으로 재사용하지 않는다. 이 문서 점검은 다른 도메인이나 실제 운영의 배포 승인이 아니다.
- Auth의 이메일 인증·로그인·메일 요청 제한은 아래 운영 정책을 따른다. 공유 저장소/실제 MySQL 검증은 DEPLOYMENT.md를 함께 확인한다.
- 커뮤니티 3번 보완으로 익명 게시글 author.id와 author.major는 null이고 서버 계산 mine을 제공한다. 채팅도 출처로 작성자를 결정하며 기존 상대 프로필·메시지 ID 마스킹과 방 분리 규칙을 재사용한다. 추가 확정 정책으로 작성 후 일반 ↔ 익명 전환은 양방향 금지한다. 과거 전환 이력이 있는 게시글·방은 소급 수정하지 않으며 기존 공개 신원이 연결될 수 있다. 과거 응답·브라우저 캐시의 ID와 본문·첨부 내용의 식별 정보도 회수하지 않는다. [익명성 검증 안내](COMMUNITY_POST_PRIVACY.md)를 따른다.
- 이미지 쓰기/영구 저장·시간대·CORS·SMTP·AI·필수 환경변수·WebSocket Upgrade와 프록시 타임아웃·다중 인스턴스 전달은 실제 환경 검증 대상이다. 기존 익명 방 키 적용 절차는 ERD.md의 마이그레이션 검증 기준을 따른다. 운영 DB·외부 연결을 이번에 검증하지 않았다.
