ERD

기준: Notion ERD 페이지
프로젝트: 낄E낄I 기숙사 룸메이트 매칭 플랫폼
수정 기준: API 명세와의 불일치 및 백엔드 구현 선행조건 보완

1. 사용자·학적 정보

사용자 (USER)

컬럼명

타입

키

설명

user_id

BIGINT

PK

사용자 고유 ID

password

VARCHAR(255)

암호화된 비밀번호

school_email

VARCHAR(100)

UNIQUE

학교 이메일

email_verified

BOOLEAN

학교 이메일 인증 완료 여부

role

VARCHAR(20)

USER / ADMIN

status

VARCHAR(20)

ACTIVE(정상) / SUSPENDED(이용 정지) / WITHDRAWN(탈퇴)

created_at

DATETIME

가입 일시

updated_at

DATETIME

계정 수정 일시

school_email에는 UNIQUE 제약을 적용한다.

회원 탈퇴 시 실제 사용자 행을 삭제하지 않고 status = WITHDRAWN으로 처리하여 게시글·댓글·신고·채팅 등의 참조 관계를 유지한다.

비밀번호는 평문이 아닌 BCrypt 등의 단방향 해시 결과만 저장한다.

관리자는 별도 관리자 테이블을 생성하지 않고 role = ADMIN으로 구분한다.

사용자 프로필 (USER_PROFILE)

컬럼명

타입

키

설명

user_profile_id

BIGINT

PK

사용자 프로필 고유 ID

user_id

BIGINT

FK → USER, UNIQUE

사용자

nickname

VARCHAR(20)

UNIQUE

닉네임

gender

VARCHAR(10)

남자 / 여자

dormitory_id

BIGINT

FK → DORMITORY

생활관

faculty_id

BIGINT

FK → FACULTY

소속 학부

major_id

BIGINT

FK → MAJOR, nullable

소속 전공

birth_year

SMALLINT

출생연도

admission_year

CHAR(2)

입학연도/학번 표시값 예: 24

grade

VARCHAR(20)

1학년 ~ 5학년

mbti

CHAR(4)

MBTI 예: INFJ

introduction

VARCHAR(255)

nullable

자기소개

profile_image_url

VARCHAR(500)

nullable

프로필 이미지 경로

created_at

DATETIME

프로필 생성 일시

updated_at

DATETIME

프로필 수정 일시

USER와 USER_PROFILE은 1:1 관계이며 user_id에 UNIQUE 제약을 적용한다.

회원가입 화면에서 MBTI는 E/I, N/S, F/T, P/J를 각각 선택하지만 DB에는 최종 조합인 CHAR(4) 형태로 저장한다.

현재 프론트에서 학번 입력값은 24학번처럼 두 자리 입학연도 형태이므로 admission_year로 설계한다.

향후 실제 전체 학번을 수집한다면 student_no VARCHAR(...) 컬럼을 별도로 추가하는 것이 적절하다.

생활관 (DORMITORY)

컬럼명

타입

키

설명

dormitory_id

BIGINT

PK

생활관 고유 ID

dormitory_code

VARCHAR(20)

UNIQUE

생활관 식별 코드

dormitory_name

VARCHAR(30)

UNIQUE

생활관명

is_active

BOOLEAN

현재 사용 여부

프론트 코드에 비룡, 비봉 표기가 혼재하므로 생활관명을 사용자 테이블의 ENUM으로 고정하지 않고 기준정보 테이블로 분리한다.

최종 생활관 명칭을 확정한 뒤 기준정보만 수정할 수 있도록 한다.

학부 (FACULTY)

컬럼명

타입

키

설명

faculty_id

BIGINT

PK

학부 고유 ID

faculty_name

VARCHAR(100)

UNIQUE

학부명

is_active

BOOLEAN

현재 사용 여부

전공 (MAJOR)

컬럼명

타입

키

설명

major_id

BIGINT

PK

전공 고유 ID

faculty_id

BIGINT

FK → FACULTY

소속 학부

major_name

VARCHAR(150)

전공명

is_active

BOOLEAN

현재 사용 여부

하나의 학부에 동일한 전공이 중복 등록되지 않도록 (faculty_id, major_name) 조합에 UNIQUE 제약을 적용한다.

FACULTY 1개에 여러 MAJOR가 연결된다.

이메일 인증 (EMAIL_VERIFICATION)

컬럼명

타입

키

설명

verification_id

BIGINT

PK

인증 요청 고유 ID

user_id

BIGINT

FK → USER, nullable

기존 회원 인증인 경우 사용자

email

VARCHAR(100)

인증 대상 학교 이메일

purpose

VARCHAR(30)

SIGNUP

verification_code

VARCHAR(255)

해시 처리된 인증번호

expires_at

DATETIME

인증번호 만료 일시

verified_at

DATETIME

nullable

인증번호 확인 완료 일시

verification_token_hash

VARCHAR(255)

nullable

인증 완료 후 발급한 Verification Token 해시

verification_token_expires_at

DATETIME

nullable

Verification Token 만료 일시

consumed_at

DATETIME

nullable

회원가입에서 Token 사용 완료 일시

created_at

DATETIME

인증 요청 일시

회원가입에서 사용하는 학교 이메일 인증을 처리한다.

인증번호와 Verification Token은 평문 저장을 피하고 해시 처리한다.

email + purpose 기준으로 최신 유효 인증 요청을 조회한다.

Verification Token은 일회용으로 사용하며 consumed_at이 존재하면 다시 사용할 수 없다.

인증번호를 Redis로 관리하기로 결정한다면 인증번호 관련 저장 구조는 Redis로 대체할 수 있다.

비밀번호 재설정 토큰 (PASSWORD_RESET_TOKEN)

컬럼명

타입

키

설명

password_reset_token_id

BIGINT

PK

비밀번호 재설정 토큰 고유 ID

user_id

BIGINT

FK → USER

비밀번호를 재설정할 사용자

token_hash

VARCHAR(255)

UNIQUE

재설정 Token의 해시값

expires_at

DATETIME

Token 만료 일시

used_at

DATETIME

nullable

Token 사용 완료 일시

created_at

DATETIME

Token 발급 일시

비밀번호 재설정 Token은 평문으로 DB에 저장하지 않고 해시값만 저장한다.

Token은 일회용으로 사용한다.

expires_at이 지난 Token은 사용할 수 없다.

used_at이 존재하는 Token은 다시 사용할 수 없다.

비밀번호 변경 성공 후 used_at을 기록한다.

2. 내 프로필 설문

내 프로필 설문 (PROFILE_SURVEY)

컬럼명

타입

키

설명

profile_survey_id

BIGINT

PK

프로필 설문 고유 ID

user_id

BIGINT

FK → USER, UNIQUE

설문 작성 사용자

sleep_time

VARCHAR(50)

취침시간

wake_time

VARCHAR(50)

기상시간

sleep_talking

VARCHAR(20)

잠꼬대 정도

snoring

VARCHAR(20)

코골이 정도

teeth_grinding

VARCHAR(20)

이갈이 정도

sleep_sensitivity

VARCHAR(50)

잠귀

indoor_eating

VARCHAR(50)

실내 취식

smoking

VARCHAR(30)

흡연 여부

lighting_preference

VARCHAR(30)

조명 환경

phone_call

VARCHAR(50)

방 안 전화 방식

media_sound

VARCHAR(100)

음악·OTT 등 소리 사용 방식

shower_time

VARCHAR(30)

샤워 시간대

shower_duration

VARCHAR(30)

샤워 소요시간

hair_dryer_use

VARCHAR(30)

방 안에서 드라이기 사용 여부

cleaning_frequency

VARCHAR(30)

청소 주기

cleaning_style

VARCHAR(50)

청소 방식

meal

VARCHAR(60)

룸메이트와 식사 방식

roommate_interaction

VARCHAR(50)

룸메이트와의 교류 정도

temperature_preference

VARCHAR(30)

온도 선호

bug_handling

VARCHAR(30)

벌레 처리 가능 정도

scent_product_use

VARCHAR(50)

향 제품 사용 여부

desktop_use

VARCHAR(50)

데스크탑 사용 여부

shared_items

VARCHAR(50)

공용물품 사용 방식

completed

BOOLEAN

작성 완료 여부

created_at

DATETIME

최초 작성 일시

updated_at

DATETIME

마지막 수정 일시

사용자당 하나의 현재 프로필 설문을 사용하므로 user_id에 UNIQUE 제약을 적용한다.

completed = false는 미작성/작성 중, completed = true는 작성 완료를 의미한다.

프로필 수정 시 새로운 설문 행을 생성하지 않고 기존 값을 수정한다.

매칭 계산에서 상대방의 실제 생활 패턴을 나타내는 데이터로 사용한다.

프로필 설문 다중선택 (PROFILE_SURVEY_OPTION)

컬럼명

타입

키

설명

profile_survey_option_id

BIGINT

PK

다중선택 행 고유 ID

profile_survey_id

BIGINT

FK → PROFILE_SURVEY

대상 설문

field_code

VARCHAR(30)

MEAL_PLAN / DORM_STAY_DAY

option_value

VARCHAR(30)

조식, 중식, 석식 또는 월~일 등의 선택값

신청한 기숙사 식사, 기숙사 상주 주기는 복수 선택이므로 별도 테이블로 관리한다.

(profile_survey_id, field_code, option_value) 조합에 UNIQUE 제약을 적용한다.

field_code = MEAL_PLAN일 때 option_value는 조식/중식/석식/신청 안 함 등을 저장한다.

field_code = DORM_STAY_DAY일 때 월~일을 저장한다.

3. 선호 룸메이트 설문·매칭

선호 룸메이트 설문 (ROOMMATE_PREFERENCE)

컬럼명

타입

키

설명

preference_id

BIGINT

PK

선호 룸메이트 설문 고유 ID

user_id

BIGINT

FK → USER, UNIQUE

설문 작성 사용자

department_preference

VARCHAR(60)

같은 전공 / 같은 학부 다른 전공 / 다른 학부 / 상관없음

sleep_talking_allowance

VARCHAR(50)

잠꼬대 허용 정도

snoring_allowance

VARCHAR(50)

코골이 허용 정도

teeth_grinding_allowance

VARCHAR(50)

이갈이 허용 정도

smoking_preference

VARCHAR(50)

흡연 선호 조건

lighting_preference

VARCHAR(30)

선호 조명 환경

shower_time_preference

VARCHAR(30)

선호 샤워 시간대

shower_duration_preference

VARCHAR(30)

선호 샤워 소요시간

cleaning_style_preference

VARCHAR(60)

선호 청소 방식

interaction_preference

VARCHAR(60)

선호 교류 정도

meal_preference

VARCHAR(60)

선호 기숙사 식사 방식

shared_items_preference

VARCHAR(60)

선호 공용물품 사용 방식

temperature_preference

VARCHAR(50)

선호 온도 성향

completed

BOOLEAN

작성 완료 여부

created_at

DATETIME

최초 작성 일시

updated_at

DATETIME

마지막 수정 일시

사용자 1명당 현재 적용되는 선호 설문은 1개이므로 user_id에 UNIQUE 제약을 적용한다.

completed = false는 미작성/작성 중, completed = true는 작성 완료를 의미한다.

다중 선택이 필요한 나이, 학년, 취침시간, 기상시간, 청소 주기는 별도 테이블에서 관리한다.

선호 설문 다중선택 (ROOMMATE_PREFERENCE_OPTION)

컬럼명

타입

키

설명

preference_option_id

BIGINT

PK

선호 조건 선택 행 고유 ID

preference_id

BIGINT

FK → ROOMMATE_PREFERENCE

대상 선호 설문

criterion_code

VARCHAR(40)

AGE / GRADE / BEDTIME / WAKE_TIME / CLEANING_FREQUENCY

option_value

VARCHAR(60)

실제 선택값

복수 선택 가능한 조건들을 하나의 다중선택 테이블에서 관리한다.

(preference_id, criterion_code, option_value) 조합에 UNIQUE 제약을 적용한다.

상관없음을 선택한 조건은 다른 선택값과 동시에 저장하지 않도록 백엔드에서 검증한다.

선호 조건 중요도 (PREFERENCE_CRITERION)

컬럼명

타입

키

설명

preference_criterion_id

BIGINT

PK

선호 조건 설정 고유 ID

preference_id

BIGINT

FK → ROOMMATE_PREFERENCE

대상 선호 설문

criterion_code

VARCHAR(40)

조건 식별값

weight

TINYINT

중요도 1~5

선호 설문의 항목별 가중치를 관리한다.

(preference_id, criterion_code) 조합에 UNIQUE 제약을 적용한다.

weight는 1~5 범위만 허용한다.

가중치의 의미는 다음과 같이 사용한다.

1: 매우 낮음

2: 낮음

3: 보통

4: 높음

5: 매우 높음

예시 criterion_code: DEPARTMENT, AGE, GRADE, BEDTIME, WAKE_TIME, SLEEP_TALKING, SNORING, TEETH_GRINDING, SMOKING, LIGHTING, SHOWER_TIME, SHOWER_DURATION, CLEANING_FREQUENCY, CLEANING_STYLE, INTERACTION, MEAL, SHARED_ITEMS, TEMPERATURE.

룸메이트 매칭 결과 (ROOMMATE_MATCH)

컬럼명

타입

키

설명

match_id

BIGINT

PK

매칭 결과 고유 ID

requester_user_id

BIGINT

FK → USER

매칭을 조회하는 사용자

target_user_id

BIGINT

FK → USER

상대 사용자

match_score

INT

0~100 매칭 점수

recommendation_reason

TEXT

nullable

추천 이유 자연어

(requester_user_id, target_user_id) 조합에 UNIQUE 제약을 적용한다.

자기 자신과의 매칭 결과는 생성하지 않는다.

매칭은 방향성이 있으므로 A → B 점수와 B → A 점수는 서로 다를 수 있다.

추천 프로필 화면에서는 match_score >= 60인 결과를 조회한다.

매칭점수는 사용자의 ROOMMATE_PREFERENCE와 상대방의 USER_PROFILE, PROFILE_SURVEY를 비교하여 Spring Boot에서 계산한다.

추천 이유에 AI API를 사용할 경우에도 점수 자체는 백엔드에서 계산하고, recommendation_reason만 AI로 생성하여 저장할 수 있다.

설문이 변경되면 기존 매칭 결과를 재계산하거나 무효화한다.

계산 정책은 API.md 6장의 공통 계산·조회 정책을 따른다.
계산 가능한 기존 쌍은 동일 행의 점수를 갱신하고, 없는 쌍은 생성한다.
계산 불가가 된 기존 행만 제거한다. match_score에 임시 0점이나 null을 저장하지 않으며,
API는 계산 불가 상태를 matchScore: null로 표현한다.

입력 변경 이벤트는 입력 트랜잭션 커밋 후 처리한다. 계산은 별도 READ_COMMITTED 트랜잭션에서
관련 USER 행을 user_id ASC로 잠근 뒤 최신 입력을 일괄 조회한다. survey 저장과 계산 입력 프로필 변경도
같은 USER 잠금을 사용한다. 동일 쌍의 생성·갱신을 직렬화하고 일시적 잠금 실패는 최대 3회 시도한다.
입력 저장 중에는 상대 USER 잠금을 추가로 획득하지 않는다.
커밋 후 처리 실패는 로그에 남기고 다음 matching 조회에서 필요한 범위의 결과를 다시 준비한다.
전체·관심 목록은 페이지 대상, 상세는 한 쌍, 추천은 요청자의 후보 범위만 계산하며 전체 사용자 쌍을 재계산하지 않는다.

관심 프로필 (PROFILE_LIKE)

컬럼명

타입

키

설명

profile_like_id

BIGINT

PK

관심 프로필 고유 ID

user_id

BIGINT

FK → USER

좋아요를 누른 사용자

target_user_id

BIGINT

FK → USER

관심 프로필 사용자

(user_id, target_user_id) 조합에 UNIQUE 제약을 적용한다.

자기 자신의 프로필에는 좋아요를 등록할 수 없도록 한다.

마이페이지의 관심 프로필 목록은 이 테이블을 기준으로 조회한다.

관심 관계는 ROOMMATE_MATCH 존재 여부와 독립적이다. 매칭 갱신·계산 불가 처리에도 관계를 유지한다.
조회 시 ACTIVE 사용자와 유효한 프로필·완료된 프로필 설문 조건을 count 쿼리에도 적용하며,
기본 정렬은 profile_like_id DESC이다.

4. 커뮤니티

커뮤니티 게시글 (COMMUNITY_POST)

컬럼명

타입

키

설명

post_id

BIGINT

PK

게시글 고유 ID

author_id

BIGINT

FK → USER

작성자

dormitory_id

BIGINT

FK → DORMITORY, nullable

관련 생활관

category

VARCHAR(30)

자유게시판 / 나눔게시판 / 택배게시판 / 민원게시판

title

VARCHAR(100)

게시글 제목

content

TEXT

게시글 내용

view_count

INT

조회수

status

VARCHAR(20)

ACTIVE / DELETED

created_at

DATETIME

작성 일시

updated_at

DATETIME

수정 일시

view_count는 게시글 상세 조회 시 증가시킨다.

좋아요 수와 댓글 수는 각각 POST_LIKE, COMMUNITY_COMMENT에서 계산한다.

최신순, 조회순, 좋아요순 정렬을 지원한다.

게시글 이미지 (POST_IMAGE)

컬럼명

타입

키

설명

post_image_id

BIGINT

PK

게시글 이미지 고유 ID

post_id

BIGINT

FK → COMMUNITY_POST

대상 게시글

image_url

VARCHAR(500)

이미지 저장 경로

original_name

VARCHAR(255)

nullable

원본 파일명

sort_order

INT

이미지 표시 순서

created_at

DATETIME

등록 일시

게시글 하나에 여러 이미지가 연결될 수 있다.

실제 이미지 파일 자체를 MySQL에 저장하지 않고 파일 서버 또는 Object Storage의 URL을 저장하는 구조를 권장한다.

게시글 좋아요 (POST_LIKE)

컬럼명

타입

키

설명

post_like_id

BIGINT

PK

게시글 좋아요 고유 ID

post_id

BIGINT

FK → COMMUNITY_POST

대상 게시글

user_id

BIGINT

FK → USER

좋아요 사용자

(post_id, user_id) 조합에 UNIQUE 제약을 적용한다.

동일 사용자는 동일 게시글에 한 번만 좋아요를 등록할 수 있다.

댓글·답글 (COMMUNITY_COMMENT)

컬럼명

타입

키

설명

comment_id

BIGINT

PK

댓글 고유 ID

post_id

BIGINT

FK → COMMUNITY_POST

대상 게시글

author_id

BIGINT

FK → USER

작성자

parent_comment_id

BIGINT

FK → COMMUNITY_COMMENT, nullable

부모 댓글 ID

content

TEXT

댓글 내용

status

VARCHAR(20)

ACTIVE / DELETED

created_at

DATETIME

작성 일시

updated_at

DATETIME

수정 일시

일반 댓글은 parent_comment_id = NULL로 저장한다.

답글은 부모 댓글의 comment_id를 parent_comment_id로 저장한다.

현재 프론트는 댓글 → 답글 1단계 구조이므로 답글에 다시 답글을 등록하는 것은 백엔드에서 제한한다.

신고 이력 유지를 위해 댓글도 소프트 삭제한다.

댓글 좋아요 (COMMENT_LIKE)

컬럼명

타입

키

설명

comment_like_id

BIGINT

PK

댓글 좋아요 고유 ID

comment_id

BIGINT

FK → COMMUNITY_COMMENT

대상 댓글 또는 답글

user_id

BIGINT

FK → USER

좋아요 사용자

(comment_id, user_id) 조합에 UNIQUE 제약을 적용한다.

답글 역시 COMMUNITY_COMMENT에 저장되므로 동일 테이블로 좋아요를 처리한다.

신고 (REPORT)

컬럼명

타입

키

설명

report_id

BIGINT

PK

신고 고유 ID

reporter_id

BIGINT

FK → USER

신고자

target_user_id

BIGINT

FK → USER

신고 대상 사용자

post_id

BIGINT

FK → COMMUNITY_POST, nullable

신고 대상 게시글

comment_id

BIGINT

FK → COMMUNITY_COMMENT, nullable

신고 대상 댓글

report_type

VARCHAR(20)

USER / POST / COMMENT

reason

VARCHAR(100)

신고 사유

description

TEXT

nullable

기타 신고 내용

status

VARCHAR(20)

PENDING / COMPLETED / REJECTED

resolution_action

VARCHAR(30)

nullable

NONE / SUSPEND_USER / DELETE_TARGET 등

processed_by

BIGINT

FK → USER, nullable

처리 관리자

created_at

DATETIME

신고 접수 일시

processed_at

DATETIME

nullable

처리 완료 일시

사용자, 게시글, 댓글 신고를 하나의 테이블로 관리한다.

report_type = USER이면 post_id, comment_id는 NULL이다.

report_type = POST이면 post_id를 저장한다.

report_type = COMMENT이면 comment_id를 저장한다.

게시글 또는 댓글 신고에서도 실제 신고 대상 작성자를 target_user_id에 함께 저장한다.

관리자가 회원 이용 정지, 게시글 삭제, 댓글 삭제, 신고 반려, 처리 완료 등을 수행한 결과를 저장한다.

5. 채팅

채팅방 (CHAT_ROOM)

컬럼명

타입

키

설명

chat_room_id

BIGINT

PK

채팅방 고유 ID

room_key

VARCHAR(100)

UNIQUE

동일 사용자 간 중복 채팅방 방지용 키

category

VARCHAR(20)

ROOMMATE / COMMUNITY

source_post_id

BIGINT

FK → COMMUNITY_POST, nullable

게시글에서 시작된 채팅의 원본 게시글

source_comment_id

BIGINT

FK → COMMUNITY_COMMENT, nullable

댓글에서 시작된 채팅의 원본 댓글

status

VARCHAR(20)

ACTIVE / CLOSED

created_at

DATETIME

채팅방 생성 일시

현재 프론트 구조는 1:1 채팅을 기준으로 한다.

룸메이트 프로필에서 시작된 채팅은 category = ROOMMATE로 저장한다.

게시글·댓글에서 시작된 채팅은 category = COMMUNITY로 저장한다.

게시글에서 시작하면 source_post_id, 댓글에서 시작하면 source_comment_id를 저장한다.

room_key는 사용자 두 명의 ID와 채팅 분류를 조합하여 생성함으로써 동일한 채팅방이 중복 생성되는 것을 방지할 수 있다.

채팅방 참여자 (CHAT_ROOM_MEMBER)

컬럼명

타입

키

설명

chat_room_member_id

BIGINT

PK

채팅방 참여자 고유 ID

chat_room_id

BIGINT

FK → CHAT_ROOM

채팅방

user_id

BIGINT

FK → USER

참여 사용자

is_pinned

BOOLEAN

채팅방 고정 여부

is_muted

BOOLEAN

알림 끄기 여부

is_left

BOOLEAN

채팅방 나가기 여부

joined_at

DATETIME

참여 일시

last_read_at

DATETIME

nullable

마지막으로 채팅방을 읽은 시각

left_at

DATETIME

nullable

채팅방 나가기 일시

(chat_room_id, user_id) 조합에 UNIQUE 제약을 적용한다.

채팅방 고정과 알림 끄기는 사용자마다 다른 설정이므로 CHAT_ROOM이 아닌 CHAT_ROOM_MEMBER에 저장한다.

프론트의 읽지 않은 메시지 수는 last_read_at보다 이후에 수신된 상대방 메시지 개수로 계산한다.

채팅방 나가기 시 메시지 데이터를 즉시 삭제하지 않고 left_at을 기록한다.

채팅 메시지 (CHAT_MESSAGE)

컬럼명

타입

키

설명

message_id

BIGINT

PK

메시지 고유 ID

chat_room_id

BIGINT

FK → CHAT_ROOM

대상 채팅방

sender_id

BIGINT

FK → USER

발신자

content

VARCHAR(2000)

메시지 내용

sent_at

DATETIME

전송 일시

현재 프론트의 메시지 최대 길이 2,000자에 맞춰 VARCHAR(2000)으로 설계한다.

현재 완료된 프론트에서는 텍스트 메시지를 사용하고 있으므로 이미지·파일 첨부용 테이블은 포함하지 않는다.

메시지의 읽음 여부는 상대 참여자의 CHAT_ROOM_MEMBER.last_read_at과 비교하여 판단한다.

채팅 목록의 최근 메시지와 최근 메시지 시간은 가장 최근 CHAT_MESSAGE를 조회하여 표시한다.

6. 캘린더·학교/생활관 일정

캘린더 카테고리 (CALENDAR_CATEGORY)

컬럼명

타입

키

설명

category_id

BIGINT

PK

캘린더 카테고리 고유 ID

category_code

VARCHAR(50)

UNIQUE

SCHOOL / DORMITORY / CUSTOM-\*

default_name

VARCHAR(50)

기본 카테고리명

owner_user_id

BIGINT

FK → USER, nullable

사용자 추가 카테고리의 소유자

name_locked

BOOLEAN

카테고리 이름 변경 제한 여부

is_system

BOOLEAN

기본 시스템 카테고리 여부

created_at

DATETIME

생성 일시

시스템 고정 카테고리는 SCHOOL(학교), DORMITORY(기숙사) 두 개이며 owner_user_id = NULL, is_system = true이다.

학교·기숙사 카테고리는 name_locked = true이며 사용자 수정·삭제 및 직접 일정 등록을 금지한다.

사용자가 직접 추가한 CUSTOM-\* 카테고리는 owner_user_id를 설정하고 is_system = false, name_locked = false로 저장한다.

학교·기숙사 카테고리는 모든 사용자에게 공통으로 사용한다.

사용자별 캘린더 카테고리 설정 (CALENDAR_CATEGORY_SETTING)

컬럼명

타입

키

설명

category_setting_id

BIGINT

PK

카테고리 설정 고유 ID

user_id

BIGINT

FK → USER

사용자

category_id

BIGINT

FK → CALENDAR_CATEGORY

대상 카테고리

display_name

VARCHAR(30)

사용자에게 표시할 카테고리명

color

CHAR(7)

HEX 색상 예: #006A65

is_enabled

BOOLEAN

해당 카테고리 사용 여부

(user_id, category_id) 조합에 UNIQUE 제약을 적용한다.

학교 일정과 기숙사 일정은 이름 변경은 불가능하지만 색상 변경은 가능하다.

룸메찾기, 개인 일정 및 사용자 추가 카테고리는 이름과 색상을 변경할 수 있다.

프론트에서 현재 localStorage로 관리하는 카테고리 설정을 백엔드 연동 시 이 테이블로 이전한다.

캘린더 일정 (CALENDAR_EVENT)

컬럼명

타입

키

설명

event_id

BIGINT

PK

일정 고유 ID

owner_user_id

BIGINT

FK → USER, nullable

개인 일정 소유자

category_id

BIGINT

FK → CALENDAR_CATEGORY

일정 카테고리

title

VARCHAR(80)

일정 제목

start_date

DATE NOT NULL

일정 시작일

end_date

DATE NOT NULL

일정 종료일

start_time

TIME

nullable

시작 시간

end_time

TIME

nullable

종료 시간

is_all_day

BOOLEAN

종일 일정 여부

description

TEXT

nullable

일정 내용

source_type

VARCHAR(20)

USER / CRAWLED

source_name

VARCHAR(255)

nullable

일정 출처

source_url

VARCHAR(500)

nullable

원본 공지 URL

collected_at

DATETIME

nullable

크롤링 수집 일시

단일 일정은 start_date = end_date로 저장한다. 기간 일정은 start_date <= end_date여야 한다.

사용자가 직접 등록한 룸메찾기·개인 일정은 owner_user_id를 저장한다.

학교 일정과 기숙사 일정처럼 모든 사용자에게 제공되는 크롤링 일정은 owner_user_id = NULL로 저장한다.

학교·기숙사 일정은 source_type = CRAWLED로 저장한다.

관리자 페이지의 일정 수집 현황은 source_type = CRAWLED인 데이터를 조회하여 구현한다.

크롤링 일정은 관리자 페이지에서 직접 생성·수정하기 위한 데이터가 아니라 수집된 결과를 확인하는 용도로 사용한다.

한경국립대학교 학사일정은 source_type, source_name, title, start_date, end_date 조합으로 중복을 확인한다. 새 식별 컬럼은 추가하지 않는다.
기존 SCHOOL 시스템 카테고리 행의 쓰기 잠금 안에서 중복 확인과 저장을 수행하며 기존 일정을 전체 삭제하지 않는다.

일정 크롤링 실행 이력 (SCHEDULE_CRAWL_RUN)

컬럼명

타입

키

설명

crawl_run_id

BIGINT

PK

크롤링 실행 고유 ID

category

VARCHAR(20)

SCHOOL / DORMITORY

status

VARCHAR(20)

RUNNING / COMPLETED / FAILED

collected_count

INT

해당 실행에서 수집된 일정 수

error_message

TEXT

nullable

실패 사유

started_at

DATETIME

실행 시작 시각

completed_at

DATETIME

nullable

실행 완료 시각

관리자 페이지의 크롤링 상태, 최근 실행 시각, 최근 실패 사유는 이 테이블을 기준으로 조회한다.

수집된 실제 일정 데이터는 CALENDAR_EVENT에 저장한다.

7. 별도 테이블을 두지 않는 데이터

매칭 추천 프로필

추천 프로필 자체를 별도 테이블로 관리하지 않는다.

ROOMMATE_MATCH에서 다음 조건으로 조회한다.

match_score >= 60

따라서 추천 프로필은 데이터 자체가 아니라 매칭 결과에 대한 조회 조건이다.

기숙사 예상 합격 점수 계산기

현재 프론트에서는 지역, 직전 학기 성적, 우선선발 점수 등을 이용해 결과를 계산한다.

해당 계산 규칙을 사용자가 수정하거나 관리자가 변경하는 기능은 현재 프론트에 없으므로 별도의 DB 테이블을 만들지 않고 Spring Boot 내부의 계산 정책으로 구현한다.

향후 연도별 배점이나 정책을 관리자 페이지에서 수정할 필요가 생길 경우 그때 DORM_SCORE_POLICY, REGION_SCORE_POLICY 등의 테이블로 분리한다.

관리자

별도의 ADMIN 테이블을 만들지 않는다.

USER.role = ADMIN인 사용자가 관리자 기능을 사용하며 다음 데이터를 관리한다.

회원 관리: USER, USER_PROFILE

커뮤니티 관리: COMMUNITY_POST, COMMUNITY_COMMENT

신고 관리: REPORT

일정 수집 현황: SCHEDULE_CRAWL_RUN, CALENDAR_EVENT

8. 주요 관계·무결성 규칙

USER 1개는 최대 1개의 USER_PROFILE을 가진다. (USER 1:1 USER_PROFILE)

USER 1개에 여러 PASSWORD_RESET_TOKEN이 발급될 수 있다.

DORMITORY 1개에 여러 사용자 프로필이 연결될 수 있다. (DORMITORY 1 : N USER_PROFILE)

FACULTY 1개에 여러 MAJOR가 연결될 수 있다. (FACULTY 1 : N MAJOR)

USER 1개는 최대 1개의 현재 PROFILE_SURVEY를 가진다.

PROFILE_SURVEY 1개에 여러 PROFILE_SURVEY_OPTION이 연결될 수 있다.

USER 1개는 최대 1개의 현재 ROOMMATE_PREFERENCE를 가진다.

ROOMMATE_PREFERENCE 1개에 여러 ROOMMATE_PREFERENCE_OPTION과 PREFERENCE_CRITERION이 연결될 수 있다.

ROOMMATE_MATCH는 사용자와 다른 사용자를 연결하는 방향성 관계이다.

(requester_user_id, target_user_id)는 중복될 수 없다.

자기 자신을 매칭 대상 또는 관심 프로필 대상으로 지정할 수 없다.

PROFILE_LIKE를 통해 USER와 USER의 N 관심 관계를 관리한다.

USER 1명은 여러 COMMUNITY_POST를 작성할 수 있다.

COMMUNITY_POST 1개에 여러 POST_IMAGE, POST_LIKE, COMMUNITY_COMMENT가 연결될 수 있다.

COMMUNITY_COMMENT.parent_comment_id를 이용하여 댓글과 답글을 동일 테이블에서 관리한다.

REPORT는 사용자, 게시글 또는 댓글을 대상으로 생성할 수 있다.

게시글과 댓글은 신고 이력 보존을 위해 소프트 삭제를 기본으로 한다.

CHAT_ROOM과 USER의 N 관계는 CHAT_ROOM_MEMBER로 관리한다.

현재 서비스의 채팅방은 1:1 채팅을 기준으로 하므로 활성 참여자는 최대 2명으로 제한한다.

CHAT_ROOM 1개에 여러 CHAT_MESSAGE가 연결될 수 있다.

CHAT_ROOM_MEMBER.last_read_at을 기준으로 메시지 읽음 여부와 미읽음 수를 계산한다.

CALENDAR_CATEGORY와 USER의 사용자별 표시 설정은 CALENDAR_CATEGORY_SETTING에서 관리한다.

학교·기숙사 크롤링 일정은 CALENDAR_EVENT.owner_user_id = NULL로 저장하여 전체 사용자에게 제공한다.

크롤링 실행 상태와 실패 이력은 SCHEDULE_CRAWL_RUN에서 관리한다.

개인·룸메찾기 일정은 해당 사용자의 owner_user_id를 저장한다.

모든 PK와 해당 FK의 ID 타입은 BIGINT로 통일한다.

match_score는 0 이상 100 이하를 보장한다.

PREFERENCE_CRITERION.weight는 1~5 범위를 보장한다.

view_count는 0 이상을 보장한다.

일정에 시작·종료 시간이 모두 존재하는 경우 end_time > start_time을 보장한다.

9. 핵심 관계 요약

USER
├─ 1:1 USER_PROFILE
├─ 1:N PASSWORD_RESET_TOKEN
├─ 1:1 PROFILE_SURVEY
├─ 1:1 ROOMMATE_PREFERENCE
├─ 1:N PROFILE_LIKE
├─ 1:N ROOMMATE_MATCH
├─ 1:N COMMUNITY_POST
├─ 1:N COMMUNITY_COMMENT
├─ 1:N REPORT
├─ N:M CHAT_ROOM (via CHAT_ROOM_MEMBER)
└─ 1:N CALENDAR_EVENT

PROFILE_SURVEY
└─ 1:N PROFILE_SURVEY_OPTION

ROOMMATE_PREFERENCE
├─ 1:N ROOMMATE_PREFERENCE_OPTION
└─ 1:N PREFERENCE_CRITERION

COMMUNITY_POST
├─ 1:N POST_IMAGE
├─ 1:N POST_LIKE
├─ 1:N COMMUNITY_COMMENT
└─ 1:N REPORT

COMMUNITY_COMMENT
├─ 1:N COMMUNITY_COMMENT (답글)
├─ 1:N COMMENT_LIKE
└─ 1:N REPORT

CHAT_ROOM
├─ 1:N CHAT_ROOM_MEMBER
└─ 1:N CHAT_MESSAGE

CALENDAR_CATEGORY
├─ 1:N CALENDAR_CATEGORY_SETTING
└─ 1:N CALENDAR_EVENT

SCHEDULE_CRAWL_RUN
└─ 수집 결과 → CALENDAR_EVENT

FACULTY
└─ 1:N MAJOR

DORMITORY
└─ 1:N USER_PROFILE
