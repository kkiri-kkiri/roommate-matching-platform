ERD

기준: 현재 Entity 선언·서비스 및 API.md 대조 (2026-10-14)
프로젝트: 낄E낄I 기숙사 룸메이트 매칭 플랫폼
수정 기준: 과거 설계 정리, 선언된 DB 제약과 애플리케이션 검증·미구현 기능 구분

10장의 Entity별 표를 컬럼 자료형·NULL·PK/FK·UNIQUE·DEFAULT 선언의 상세 기준으로 사용한다. 본문은 도메인 설명이며 운영 DB의 실제 DDL을 확인한 결과가 아니다. PK는 Long/GenerationType.IDENTITY이고 물리 테이블 이름·FK 삭제 동작·인덱스·CHECK는 운영 DB 추가 확인이 필요하다. Java 초기값과 DTO/서비스 검증은 DB DEFAULT/CHECK가 아니다.

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

USER의 비밀번호 변경·재설정·관리자 상태 변경은 같은 USER 행의 PESSIMISTIC_WRITE 잠금을 사용한다.
잠금 획득 후 관리 엔티티를 refresh하여 이전 조회의 상태·비밀번호를 덮어쓰지 않는다.
재설정은 USER → PASSWORD_RESET_TOKEN 순으로 잠그며 스키마/버전 컬럼 추가는 없다.
비밀번호 입력은 UTF-8 72바이트 이하로 검증한다. 저장된 해시 컬럼 길이는 변경하지 않는다.

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

자기소개. 마이페이지 내 설문의 선택 항목 '나의 한마디'와 룸메찾기 목록/상세/관심 프로필이 함께 사용하는 단일 저장 위치.
신규 입력은 API에서 최대 200 UTF-16 코드 단위로 검증한다. 기존 데이터 보존을 위해 컬럼 길이는 255로 유지한다.
별도 컬럼이나 마이그레이션은 필요하지 않다.

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

생활관은 DORMITORY 기준정보 FK로 저장한다. 현재 API 명칭은 나래·비봉·창조이며 내부 코드는 NARAE·BIBONG·CHANGJO다. 과거 비룡 표기를 현재 계약으로 사용하지 않는다. 활성 기준정보와 사용자 성별 등 서비스 검증을 함께 적용한다.

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

현재 API의 설문 입력은 23개 필드다. 단일값 21개는 PROFILE_SURVEY의 대응 snake_case 컬럼에 저장하고, dormStayDays/mealPlan 두 배열은 PROFILE_SURVEY_OPTION의 DORM_STAY_DAY/MEAL_PLAN 행으로 저장한다. completed와 PK/FK·시각은 이 23개 사용자 입력 필드에 포함하지 않는다. 과거 hairDryer/bugHandling에 대응하는 입력·저장 컬럼은 현재 Entity에 없다.

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

현재 AI 분석은 별도 API로 반환하며 recommendation_reason에 결과를 저장하지 않는다. 이 nullable 컬럼은 Entity에 남아 있으나 현재 분석 계약에서 저장을 보장하지 않는다. 삭제나 자동 저장은 별도 변경이다.

설문이 변경되면 기존 매칭 결과를 재계산하거나 무효화한다.

계산 정책은 API.md 6장의 공통 계산·조회 정책을 따른다.
계산 가능한 기존 쌍은 동일 행의 점수를 갱신하고, 없는 쌍은 생성한다.
계산 불가가 된 기존 행만 제거한다. match_score에 임시 0점이나 null을 저장하지 않으며,
API는 계산 불가 상태를 matchScore: null로 표현한다.

입력 변경 이벤트는 입력 트랜잭션 커밋 후 처리한다. 계산은 별도 READ_COMMITTED 트랜잭션에서
관련 USER 행을 user_id ASC로 잠근 뒤 최신 입력을 일괄 조회한다. survey 저장과 계산 입력 프로필 변경도
같은 USER 잠금을 사용한다. 동일 쌍의 생성·갱신을 직렬화하고 일시적 잠금 실패는 최대 3회 시도한다.
입력 저장 중에는 상대 USER 잠금을 추가로 획득하지 않는다.
커밋 후 이벤트는 제한된 전용 큐에서 비동기로 처리한다. 입력 저장 응답은 계산을 기다리지 않는다.
ID 기반 페이지로 상대 100명씩 조회하고 각 배치의 별도 트랜잭션에서 USER 최대 101행을 잠근다.
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

2번 보완(2026-10-19): 일반 사용자 쓰기는 기존 USER 행의 공통 계정 잠금과 ACTIVE 검증 후
게시글·댓글 잠금을 획득한다. 계정 상태 변경과 같은 행을 공유하며 Entity/DDL/마이그레이션 변경은 없다.
상세 범위는 [계정 동시성 안내](COMMUNITY_ACCOUNT_CONCURRENCY.md)를 따른다.

4·5번 보완(2026-10-19): 댓글 작성과 부모 삭제도 게시글 잠금을 공유하며 부모 삭제 시
활성 대댓글의 status를 함께 DELETED로 변경한다. 물리 삭제·FK·Entity/DDL 변경은 없다.
이미지 연결 변경은 기존 게시글 잠금 뒤 최신 이미지 행을 잠가 처리한다. 최대 5장과
sort_order 정책은 서비스 검증이며 새 DB 제약·인덱스·마이그레이션을 추가하지 않는다.

2026-10-19 동시성 보완은 기존 행의 비관적 잠금만 사용한다. COMMUNITY_POST와
COMMUNITY_COMMENT에 version 컬럼이나 새 제약·인덱스를 추가하지 않으며 수동 마이그레이션도
필요하지 않다. ACTIVE/DELETED 소프트 삭제 및 참조 보존 정책은 그대로다.
잠금 범위와 검증 한계는 [동시성 검증 안내](COMMUNITY_CONCURRENCY.md)를 따른다.

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

anonymous

BOOLEAN NOT NULL DEFAULT FALSE

익명 게시글 여부. 생성은 생략 시 false, 수정은 Boolean null/생략 시 유지한다. 조회에도 anonymous를 제공한다. 익명 작성자 닉네임·전공은 가리지만 author.id는 실제 USER ID이므로 재식별 위험과 익명성 정책 결정이 남아 있다.

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

게시글 수정의 keepImageIds 생략/null/빈 배열은 기존 POST_IMAGE 연결 행을 모두 제거한다. 현재 프론트는 보존할 이미지 ID를 명시하며 추가 파일은 별도 전달한다. 연결 행 삭제가 실제 저장 파일 삭제를 의미하지 않는다. 운영 이미지 영구 경로·파일 정리·백업은 별도 환경 확인 사항이다.

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

anonymous

BOOLEAN NOT NULL DEFAULT FALSE

댓글·답글 익명 여부. 기존 댓글은 닉네임 공개(false).

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

게시글별 익명 댓글 작성자 (COMMUNITY_ANONYMOUS_AUTHOR)

| 컬럼 | 타입 | 제약 / 의미 |
| --- | --- | --- |
| anonymous_author_id | BIGINT | PK, AUTO_INCREMENT |
| post_id | BIGINT | NOT NULL, FK → COMMUNITY_POST |
| author_id | BIGINT | NOT NULL, FK → USER (서버 내부 전용) |
| anonymous_number | BIGINT | NOT NULL, 게시글별 양의 익명 번호 |

UNIQUE(post_id, author_id), UNIQUE(post_id, anonymous_number).
댓글과 답글이 동일 매핑을 공유한다. 게시글 행에 PESSIMISTIC_WRITE 잠금을 획득한 뒤 번호를 배정하고 댓글 저장과 함께 커밋한다.
댓글 수정·삭제 및 회원 탈퇴(소프트 삭제)로 매핑을 삭제하지 않는다. 모든 댓글이 없어도 번호를 보존하며 재사용하지 않는다.
기존 게시글의 매핑이 비어 있으면 상태와 무관한 익명 댓글 전체를 created_at, comment_id 순으로 읽어 초기화한다.
번호는 조회 정렬·페이징과 독립적이며 매핑의 author_id는 일반 사용자 응답에 포함하지 않는다.
COMMUNITY_COMMENT.author_id와 이 매핑의 author_id는 서버 내부의 소유권·번호 관리에 사용한다. API의 익명 댓글·답글 author.id와 author.major는 null이며 nickname은 "익명{anonymousNumber}"다. mine은 서버 인증 사용자와 내부 작성자를 비교해 계산하고 비로그인은 false다. 이는 실제 author.id를 유지하는 익명 게시글 응답과 다르며 API.md 7.4·8장을 따른다.

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

room_key는 정렬한 두 사용자 ID로 생성하며 기존 UNIQUE 및 VARCHAR(100)을 유지한다.

- 룸메이트: `ROOMMATE:{minUserId}:{maxUserId}`
- 일반 커뮤니티: `COMMUNITY:{minUserId}:{maxUserId}`
- 익명 게시글: `COMMUNITY:{minUserId}:{maxUserId}:POST:{postId}`
- 익명 댓글: `COMMUNITY:{minUserId}:{maxUserId}:COMMENT:{commentId}`

익명 접미사는 생성 시점의 익명성을 보존한다. 일반 게시글로 재진입할 때는 기존 일반 방의 source_post_id를 갱신하고 source_comment_id를 비운다. 익명 방의 출처와 기존 메시지/참여자는 유지한다. 컬럼 추가 없이 기존 익명 방의 키만 전환하며 `migrations/README.md`의 배포 절차를 따른다.

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

사용자 소유 CUSTOM-* 카테고리는 이름과 색상을 변경할 수 있다. ROOMMATE/PERSONAL 시스템 카테고리는 현재 선언되어 있지 않다.

현재 프론트는 캘린더 카테고리 설정 API를 호출하고 이 테이블을 사용한다. 화면 상태 저장을 설정 API 미연동으로 해석하지 않는다.

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

현재 학교 수집기는 source_type = CRAWLED로 저장한다. DORMITORY 카테고리·저장 모델은 있으나 기숙사 자동 수집기는 미구현이다. 출처·수집 정책 확정 전 새로운 수집기를 가정하지 않는다.

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

관리자 상태 API는 이 테이블을 조회한다. CalendarCrawlingService는 실행 시작을 독립 커밋하여 RUNNING으로 기록한다. 일정과 COMPLETED·신규 저장 건수·완료 시각은 같은 트랜잭션에서 커밋한다. 실패 시 일정은 전체 롤백하고 별도 트랜잭션으로 FAILED/0건을 기록한다. error_message에는 안전한 FETCH/STORE 분류 코드만 저장한다. 각 실행은 별도 ID를 사용하며 기존 SCHOOL 행 잠금으로 일정 중복 저장을 방지한다. 프로세스 종료나 이력 DB 장애로 남은 RUNNING은 자동 실패 처리하지 않는다([운영 절차](DEPLOYMENT.md)). 이력이 없으면 API의 status/lastRunAt/lastCompletedAt/lastFailure는 null, recentCollectedCount는 0이다. totalCollectedCount는 CALENDAR_EVENT의 실제 수집 일정 집계다.

수집된 실제 일정 데이터는 CALENDAR_EVENT에 저장한다.

7. 별도 테이블을 두지 않는 데이터

매칭 추천 프로필

추천 프로필 자체를 별도 테이블로 관리하지 않는다.

ROOMMATE_MATCH에서 다음 조건으로 조회한다.

match_score >= 60

따라서 추천 프로필은 데이터 자체가 아니라 매칭 결과에 대한 조회 조건이다.

기숙사 예상 합격 점수 계산기

현재 프론트에서는 지역, 직전 학기 성적, 우선선발 점수 등을 이용해 결과를 계산한다.

현재 MainPage.calculateDormitoryScore에서 계산하며 서버 계산 API·DTO·서비스는 없다. API.md의 서버 이전 설계는 미구현 제안이며 제공 중인 API가 아니다. 계산 규칙 DB 테이블도 없다. 서버 이전·계산 정책은 별도 확정 사항이다.

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

SCHEDULE_CRAWL_RUN은 학교 수집의 실행 이력을 저장하는 상태 조회 모델이다. DORMITORY 자동 수집은 미구현이다.

개인·룸메찾기 일정은 해당 사용자의 owner_user_id를 저장한다.

모든 PK와 해당 FK의 ID 타입은 BIGINT로 통일한다.

match_score의 0~100 범위는 Java 계산·검증 기준이다. Entity에 해당 DB CHECK 선언은 없다.

PREFERENCE_CRITERION.weight의 1~5 범위는 요청·애플리케이션 검증이다. DB CHECK가 있다고 단정하지 않는다.

view_count는 애플리케이션에서 0으로 시작하고 증가시킨다. 음수 금지 DB CHECK는 Entity에 선언되어 있지 않다.

일정은 start_date <= end_date를 검증한다. 같은 날짜이고 두 시간이 모두 있을 때만 end_time > start_time을 DTO/서비스에서 검증한다. 여러 날인 경우 종료 시간만 비교하지 않으며, 한쪽/양쪽 시간이 null인 경우 시간 순서 검증을 적용하지 않는다. 양쪽 시간 모두 null이면 is_all_day=true로 계산하며 한쪽만 지정하면 false다. 별도 allDay 요청 필드는 없다. PATCH의 null/생략은 서비스 병합에서 기존 값을 유지한다. Entity는 날짜 역전을 검사하지만 시간 순서 DB CHECK는 선언하지 않는다.

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
└─ CALENDAR_EVENT와 직접 FK 관계 없음 (실행별 상태·신규 저장 건수 요약 모델)

FACULTY
└─ 1:N MAJOR

DORMITORY
└─ 1:N USER_PROFILE

## 10. 현재 Entity 선언 상세 (2026-10-15)

대조 범위: 29개 Entity / 226개 컬럼.

다음은 소스 선언 대조 결과로, 운영 DDL 검증 결과가 아니다. `NOT NULL`/`NULL`은 nullable 선언(생략 시 true), PK는 NOT NULL을 뜻한다. AUTH_LIMIT_BUCKET의 String PK를 제외한 PK는 `Long`/`GenerationType.IDENTITY`; MySQL 실제 AUTO_INCREMENT 설정은 별도 확인한다. 자료형의 `columnDefinition`은 그대로 표기하고 미지정 타입은 Java 기본 매핑 기준이며 dialect/DDL에 따라 달라질 수 있다. `DEFAULT 없음`은 명시 선언이 없다는 뜻이다. 생성자·필드 초기화·@PrePersist 시각은 DB DEFAULT가 아니다.

### AUTH_EMAIL_IDENTITY

근거: [AuthEmailIdentity.java](src/main/java/com/kkirikkiri/auth/entity/AuthEmailIdentity.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| identity_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| account_email | VARCHAR(100) | NULL | UNIQUE; DEFAULT 없음 |
| verification_email | VARCHAR(100) | NULL | UNIQUE; DEFAULT 없음 |

기존 회원의 로그인·재설정 제한은 실제 user_id를 사용한다. 비등록 계정과 가입 전 인증의 이메일
동치 식별자는 이 테이블의 서로 독립된 UNIQUE 컬럼으로 결정한다. 수동 DDL은 account_email에
USER.school_email의 문자셋/Collation, verification_email에 EMAIL_VERIFICATION.email의 문자셋/Collation을 복사한다.
기존 이메일 정책과 Collation은 변경하지 않는다. MySQL에서는 시작 시 두 쌍의 타입·문자셋·Collation 불일치를 거절한다.
한 행에는 대상에 해당하는 이메일 컬럼 하나만 입력한다. 이메일 원문은 이 테이블에 보존되므로 개인정보로 취급하며
API/로그/제한 키에 노출하지 않는다. 이메일 식별 생성과 제한 예약은 같은 독립 트랜잭션이며 거절 시 함께 롤백한다.
보존 기간은 미확정으로 자동 정리하지 않는다. 활성 bucket이 참조하는 식별자를 삭제·재생성하면 제한이 초기화되므로
배포 시 비우지 않는다. 운영 절차는 [20261015 마이그레이션](migrations/20261015_auth_email_identity_indexes.md)을 따른다.

### AUTH_LIMIT_BUCKET

근거: [AuthLimitBucket.java](src/main/java/com/kkirikkiri/auth/entity/AuthLimitBucket.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| bucket_key | VARCHAR(64) | NOT NULL | PK; DEFAULT 없음 |
| window_started_at | BIGINT | NOT NULL | DEFAULT 없음 |
| generation | BIGINT | NOT NULL | DEFAULT 없음 |
| request_count | INT | NOT NULL | DEFAULT 없음 |
| failure_count | INT | NOT NULL | DEFAULT 없음 |
| in_flight | INT | NOT NULL | DEFAULT 없음 |
| blocked_until | BIGINT | NOT NULL | DEFAULT 없음 |
| updated_at | BIGINT | NOT NULL | DEFAULT 없음 |

bucket_key는 목적/차원/회원 또는 이메일 동치 식별자, 혹은 정규화 IP를 SHA-256한 값이다. 원문 이메일/IP/인증정보와 USER FK는 없다.
시간은 DB 시각의 epoch milliseconds다. 세대와 window 시작 시각은 늦은 요청 완료가 새 window를 덮어쓰지 않게 한다.
PRIMARY KEY 최초 upsert와 FOR UPDATE를 정렬된 키 순서로 수행하고, 허용된 요청만 원자적으로 예약한다.
in_flight는 검증 실패 가능성을 미리 예약하며 프로세스 종료로 남은 예약은 실패 window 종료 후 복구된다.
수동 MySQL DDL에는 updated_at 정리용 인덱스가 있다. 자동 정리/초기화는 없다.
20261014 변경에서는 기존 USER/EMAIL_VERIFICATION/PASSWORD_RESET_TOKEN 스키마를 변경하지 않았다.
20261015 변경은 AUTH_EMAIL_IDENTITY와 EMAIL_VERIFICATION 조회 인덱스 2개를 추가한다.

### EMAIL_VERIFICATION

근거: [EmailVerification.java](src/main/java/com/kkirikkiri/auth/entity/EmailVerification.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| verification_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NULL | FK → USER; DEFAULT 없음 |
| email | VARCHAR(100) | NOT NULL | DEFAULT 없음 |
| purpose | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| verification_code | VARCHAR(255) | NOT NULL | DEFAULT 없음 |
| expires_at | DATETIME | NOT NULL | DEFAULT 없음 |
| verified_at | DATETIME | NULL | DEFAULT 없음 |
| verification_token_hash | VARCHAR(255) | NULL | DEFAULT 없음 |
| verification_token_expires_at | DATETIME | NULL | DEFAULT 없음 |
| consumed_at | DATETIME | NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

조회 인덱스: `idx_email_verification_latest(email, purpose, created_at DESC, verification_id DESC)`,
`idx_email_verification_token(email, purpose, verification_token_hash)`.
최신 인증·토큰 소비의 PESSIMISTIC_WRITE는 유지한다. 최신 조회의 정렬과 WHERE 선두 컬럼을 인덱스로 지원해
무관한 이메일의 행까지 스캔하며 잠그는 간섭을 줄인다. 실제 MySQL 실행계획·잠금 범위는 별도 검증한다.

### PASSWORD_RESET_TOKEN

근거: [PasswordResetToken.java](src/main/java/com/kkirikkiri/auth/entity/PasswordResetToken.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| password_reset_token_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| token_hash | VARCHAR(255) | NOT NULL | UNIQUE; DEFAULT 없음 |
| expires_at | DATETIME | NOT NULL | DEFAULT 없음 |
| used_at | DATETIME | NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### CALENDAR_CATEGORY

근거: [CalendarCategory.java](src/main/java/com/kkirikkiri/calendar/entity/CalendarCategory.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| category_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| category_code | VARCHAR(50) | NOT NULL | UNIQUE; DEFAULT 없음 |
| default_name | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| owner_user_id | BIGINT | NULL | FK → USER; DEFAULT 없음 |
| name_locked | BOOLEAN | NOT NULL | DEFAULT 없음 |
| is_system | BOOLEAN | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### CALENDAR_CATEGORY_SETTING

근거: [CalendarCategorySetting.java](src/main/java/com/kkirikkiri/calendar/entity/CalendarCategorySetting.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| category_setting_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| category_id | BIGINT | NOT NULL | FK → CALENDAR_CATEGORY; DEFAULT 없음 |
| display_name | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| color | CHAR(7) | NOT NULL | DEFAULT 없음 |
| is_enabled | BOOLEAN | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: (user_id, category_id).

### CALENDAR_EVENT

근거: [CalendarEvent.java](src/main/java/com/kkirikkiri/calendar/entity/CalendarEvent.java)

비고유 인덱스: `idx_calendar_crawl_identity(source_type, source_name, title, start_date, end_date)`,
`idx_calendar_period(end_date, start_date, owner_user_id, source_type)`.
중복 확인의 동등 조건과 기간 조회를 지원한다. 공개/개인 소유권 조건과 기존 Collation/중복 방지 잠금은 유지한다.
수동 적용: [20261016_calendar_time_indexes.sql](migrations/20261016_calendar_time_indexes.sql).
DATE/TIME은 현지 일정 값, collected_at DATETIME은 서울 벽시계 값이며 직접 Java-time JDBC 매핑을 사용한다.

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| event_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| owner_user_id | BIGINT | NULL | FK → USER; DEFAULT 없음 |
| category_id | BIGINT | NOT NULL | FK → CALENDAR_CATEGORY; DEFAULT 없음 |
| title | VARCHAR(80) | NOT NULL | DEFAULT 없음 |
| start_date | DATE | NOT NULL | DEFAULT 없음 |
| end_date | DATE | NOT NULL | DEFAULT 없음 |
| start_time | TIME | NULL | DEFAULT 없음 |
| end_time | TIME | NULL | DEFAULT 없음 |
| is_all_day | BOOLEAN | NOT NULL | DEFAULT 없음 |
| description | TEXT | NULL | DEFAULT 없음 |
| source_type | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| source_name | VARCHAR(255) | NULL | DEFAULT 없음 |
| source_url | VARCHAR(500) | NULL | DEFAULT 없음 |
| collected_at | DATETIME | NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### SCHEDULE_CRAWL_RUN

근거: [ScheduleCrawlRun.java](src/main/java/com/kkirikkiri/calendar/entity/ScheduleCrawlRun.java)

started_at/completed_at DATETIME은 서울 벽시계 값을 직접 JDBC로 저장·조회한다. 타입 변경·자동 과거 데이터 변환은 없다.
CALENDAR_CATEGORY.created_at도 같은 정책을 사용한다. 기존 데이터의 작성 환경 확인 절차는 [DEPLOYMENT.md](DEPLOYMENT.md)를 따른다.

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| crawl_run_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| category | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| status | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| collected_count | INT | NOT NULL | DEFAULT 없음 |
| error_message | TEXT | NULL | DEFAULT 없음 |
| started_at | DATETIME | NOT NULL | DEFAULT 없음 |
| completed_at | DATETIME | NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### CHAT_MESSAGE

근거: [ChatMessage.java](src/main/java/com/kkirikkiri/chat/entity/ChatMessage.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| message_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| chat_room_id | BIGINT | NOT NULL | FK → CHAT_ROOM; DEFAULT 없음 |
| sender_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| content | VARCHAR(2000) | NOT NULL | DEFAULT 없음 |
| sent_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### CHAT_ROOM

근거: [ChatRoom.java](src/main/java/com/kkirikkiri/chat/entity/ChatRoom.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| chat_room_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| room_key | VARCHAR(100) | NOT NULL | UNIQUE; DEFAULT 없음 |
| category | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| source_post_id | BIGINT | NULL | FK → COMMUNITY_POST; DEFAULT 없음 |
| source_comment_id | BIGINT | NULL | FK → COMMUNITY_COMMENT; DEFAULT 없음 |
| status | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### CHAT_ROOM_MEMBER

근거: [ChatRoomMember.java](src/main/java/com/kkirikkiri/chat/entity/ChatRoomMember.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| chat_room_member_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| chat_room_id | BIGINT | NOT NULL | FK → CHAT_ROOM; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| is_pinned | BOOLEAN | NOT NULL | DEFAULT 없음 |
| is_muted | BOOLEAN | NOT NULL | DEFAULT 없음 |
| is_left | BOOLEAN | NOT NULL | DEFAULT 없음 |
| joined_at | DATETIME | NOT NULL | DEFAULT 없음 |
| last_read_at | DATETIME | NULL | DEFAULT 없음 |
| left_at | DATETIME | NULL | DEFAULT 없음 |

복합 UNIQUE: (chat_room_id, user_id).

### COMMENT_LIKE

근거: [CommentLike.java](src/main/java/com/kkirikkiri/community/comment/entity/CommentLike.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| comment_like_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| comment_id | BIGINT | NOT NULL | FK → COMMUNITY_COMMENT; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |

복합 UNIQUE: (comment_id, user_id).

### COMMUNITY_COMMENT

근거: [CommunityComment.java](src/main/java/com/kkirikkiri/community/comment/entity/CommunityComment.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| comment_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| post_id | BIGINT | NOT NULL | FK → COMMUNITY_POST; DEFAULT 없음 |
| author_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| parent_comment_id | BIGINT | NULL | FK → COMMUNITY_COMMENT; DEFAULT 없음 |
| content | TEXT | NOT NULL | DEFAULT 없음 |
| anonymous | BOOLEAN DEFAULT FALSE | NOT NULL | DEFAULT FALSE |
| status | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| updated_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### COMMUNITY_POST

근거: [CommunityPost.java](src/main/java/com/kkirikkiri/community/post/entity/CommunityPost.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| post_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| author_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| dormitory_id | BIGINT | NULL | FK → DORMITORY; DEFAULT 없음 |
| category | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| title | VARCHAR(100) | NOT NULL | DEFAULT 없음 |
| content | TEXT | NOT NULL | DEFAULT 없음 |
| view_count | INT | NOT NULL | DEFAULT 없음 |
| anonymous | BOOLEAN DEFAULT FALSE | NOT NULL | DEFAULT FALSE |
| status | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| updated_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### POST_IMAGE

근거: [PostImage.java](src/main/java/com/kkirikkiri/community/post/entity/PostImage.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| post_image_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| post_id | BIGINT | NOT NULL | FK → COMMUNITY_POST; DEFAULT 없음 |
| image_url | VARCHAR(500) | NOT NULL | DEFAULT 없음 |
| original_name | VARCHAR(255) | NULL | DEFAULT 없음 |
| sort_order | INT | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### POST_LIKE

근거: [PostLike.java](src/main/java/com/kkirikkiri/community/post/entity/PostLike.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| post_like_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| post_id | BIGINT | NOT NULL | FK → COMMUNITY_POST; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |

복합 UNIQUE: (post_id, user_id).

### PROFILE_LIKE

근거: [ProfileLike.java](src/main/java/com/kkirikkiri/matching/entity/ProfileLike.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| profile_like_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| target_user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |

복합 UNIQUE: (user_id, target_user_id).

### ROOMMATE_MATCH

근거: [RoommateMatch.java](src/main/java/com/kkirikkiri/matching/entity/RoommateMatch.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| match_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| requester_user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| target_user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| match_score | INT | NOT NULL | DEFAULT 없음 |
| recommendation_reason | TEXT | NULL | DEFAULT 없음 |

복합 UNIQUE: (requester_user_id, target_user_id).

### REPORT

근거: [Report.java](src/main/java/com/kkirikkiri/report/entity/Report.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| report_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| reporter_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| target_user_id | BIGINT | NOT NULL | FK → USER; DEFAULT 없음 |
| post_id | BIGINT | NULL | FK → COMMUNITY_POST; DEFAULT 없음 |
| comment_id | BIGINT | NULL | FK → COMMUNITY_COMMENT; DEFAULT 없음 |
| report_type | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| reason | VARCHAR(100) | NOT NULL | DEFAULT 없음 |
| description | TEXT | NULL | DEFAULT 없음 |
| status | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| resolution_action | VARCHAR(30) | NULL | DEFAULT 없음 |
| processed_by | BIGINT | NULL | FK → USER; DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| processed_at | DATETIME | NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### PREFERENCE_CRITERION

근거: [PreferenceCriterion.java](src/main/java/com/kkirikkiri/survey/entity/PreferenceCriterion.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| preference_criterion_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| preference_id | BIGINT | NOT NULL | FK → ROOMMATE_PREFERENCE; DEFAULT 없음 |
| criterion_code | VARCHAR(40) | NOT NULL | DEFAULT 없음 |
| weight | TINYINT | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: (preference_id, criterion_code).

### PROFILE_SURVEY

근거: [ProfileSurvey.java](src/main/java/com/kkirikkiri/survey/entity/ProfileSurvey.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| profile_survey_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; UNIQUE; DEFAULT 없음 |
| sleep_time | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| wake_time | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| sleep_talking | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| snoring | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| teeth_grinding | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| sleep_sensitivity | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| indoor_eating | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| smoking | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| lighting_preference | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| phone_call | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| media_sound | VARCHAR(100) | NOT NULL | DEFAULT 없음 |
| shower_time | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| shower_duration | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| cleaning_frequency | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| cleaning_style | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| meal | VARCHAR(60) | NOT NULL | DEFAULT 없음 |
| roommate_interaction | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| temperature_preference | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| scent_product_use | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| desktop_use | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| shared_items | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| completed | BOOLEAN (dialect 확인) | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| updated_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### PROFILE_SURVEY_OPTION

근거: [ProfileSurveyOption.java](src/main/java/com/kkirikkiri/survey/entity/ProfileSurveyOption.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| profile_survey_option_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| profile_survey_id | BIGINT | NOT NULL | FK → PROFILE_SURVEY; DEFAULT 없음 |
| field_code | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| option_value | VARCHAR(30) | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: (profile_survey_id, field_code, option_value).

### ROOMMATE_PREFERENCE

근거: [RoommatePreference.java](src/main/java/com/kkirikkiri/survey/entity/RoommatePreference.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| preference_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; UNIQUE; DEFAULT 없음 |
| department_preference | VARCHAR(60) | NOT NULL | DEFAULT 없음 |
| sleep_talking_allowance | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| snoring_allowance | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| teeth_grinding_allowance | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| smoking_preference | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| lighting_preference | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| shower_time_preference | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| shower_duration_preference | VARCHAR(30) | NOT NULL | DEFAULT 없음 |
| cleaning_style_preference | VARCHAR(60) | NOT NULL | DEFAULT 없음 |
| interaction_preference | VARCHAR(60) | NOT NULL | DEFAULT 없음 |
| meal_preference | VARCHAR(60) | NOT NULL | DEFAULT 없음 |
| shared_items_preference | VARCHAR(60) | NOT NULL | DEFAULT 없음 |
| temperature_preference | VARCHAR(50) | NOT NULL | DEFAULT 없음 |
| completed | BOOLEAN (dialect 확인) | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| updated_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### ROOMMATE_PREFERENCE_OPTION

근거: [RoommatePreferenceOption.java](src/main/java/com/kkirikkiri/survey/entity/RoommatePreferenceOption.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| preference_option_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| preference_id | BIGINT | NOT NULL | FK → ROOMMATE_PREFERENCE; DEFAULT 없음 |
| criterion_code | VARCHAR(40) | NOT NULL | DEFAULT 없음 |
| option_value | VARCHAR(60) | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: (preference_id, criterion_code, option_value).

### DORMITORY

근거: [Dormitory.java](src/main/java/com/kkirikkiri/user/entity/Dormitory.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| dormitory_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| dormitory_code | VARCHAR(20) | NOT NULL | UNIQUE; DEFAULT 없음 |
| dormitory_name | VARCHAR(30) | NOT NULL | UNIQUE; DEFAULT 없음 |
| is_active | BOOLEAN (dialect 확인) | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### FACULTY

근거: [Faculty.java](src/main/java/com/kkirikkiri/user/entity/Faculty.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| faculty_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| faculty_name | VARCHAR(100) | NOT NULL | UNIQUE; DEFAULT 없음 |
| is_active | BOOLEAN (dialect 확인) | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### MAJOR

근거: [Major.java](src/main/java/com/kkirikkiri/user/entity/Major.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| major_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| faculty_id | BIGINT | NOT NULL | FK → FACULTY; DEFAULT 없음 |
| major_name | VARCHAR(150) | NOT NULL | DEFAULT 없음 |
| is_active | BOOLEAN (dialect 확인) | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: (faculty_id, major_name).

### USER

근거: [User.java](src/main/java/com/kkirikkiri/user/entity/User.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| user_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| password | VARCHAR(255) | NOT NULL | DEFAULT 없음 |
| school_email | VARCHAR(100) | NOT NULL | UNIQUE; DEFAULT 없음 |
| email_verified | BOOLEAN (dialect 확인) | NOT NULL | DEFAULT 없음 |
| role | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| status | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| updated_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### USER_PROFILE

근거: [UserProfile.java](src/main/java/com/kkirikkiri/user/entity/UserProfile.java)

| 컬럼 | SQL 선언/기본 매핑 | NULL | 키·기본값 |
| --- | --- | --- | --- |
| user_profile_id | BIGINT | NOT NULL | PK IDENTITY; DEFAULT 없음 |
| user_id | BIGINT | NOT NULL | FK → USER; UNIQUE; DEFAULT 없음 |
| nickname | VARCHAR(20) | NOT NULL | UNIQUE; DEFAULT 없음 |
| gender | VARCHAR(10) | NOT NULL | DEFAULT 없음 |
| dormitory_id | BIGINT | NOT NULL | FK → DORMITORY; DEFAULT 없음 |
| faculty_id | BIGINT | NOT NULL | FK → FACULTY; DEFAULT 없음 |
| major_id | BIGINT | NULL | FK → MAJOR; DEFAULT 없음 |
| birth_year | SMALLINT | NOT NULL | DEFAULT 없음 |
| admission_year | CHAR(2) | NOT NULL | DEFAULT 없음 |
| grade | VARCHAR(20) | NOT NULL | DEFAULT 없음 |
| mbti | CHAR(4) | NOT NULL | DEFAULT 없음 |
| introduction | VARCHAR(255) | NULL | DEFAULT 없음 |
| profile_image_url | VARCHAR(500) | NULL | DEFAULT 없음 |
| created_at | DATETIME | NOT NULL | DEFAULT 없음 |
| updated_at | DATETIME | NOT NULL | DEFAULT 없음 |

복합 UNIQUE: 명시 선언 없음.

### Enum 및 필드 코드

Enum은 `EnumType.STRING` 선언을 사용한다. 아래 Java 값 목록 자체를 DB CHECK 또는 물리 ENUM 제약으로 간주하지 않는다. 설문 선택 문자열은 별도 서비스 허용 목록이며 API.md 15장을 따른다.

| Enum | 선언 값 |
| --- | --- |
| [EmailVerificationPurpose](src/main/java/com/kkirikkiri/auth/entity/EmailVerificationPurpose.java) | SIGNUP, RESET_PASSWORD |
| [CalendarEventSourceType](src/main/java/com/kkirikkiri/calendar/entity/CalendarEventSourceType.java) | USER, CRAWLED |
| [ScheduleCrawlCategory](src/main/java/com/kkirikkiri/calendar/entity/ScheduleCrawlCategory.java) | SCHOOL, DORMITORY |
| [ScheduleCrawlStatus](src/main/java/com/kkirikkiri/calendar/entity/ScheduleCrawlStatus.java) | RUNNING, COMPLETED, FAILED |
| [ChatRoomCategory](src/main/java/com/kkirikkiri/chat/entity/ChatRoomCategory.java) | ROOMMATE, COMMUNITY |
| [ChatRoomStatus](src/main/java/com/kkirikkiri/chat/entity/ChatRoomStatus.java) | ACTIVE, CLOSED |
| [CommunityContentStatus](src/main/java/com/kkirikkiri/community/common/entity/CommunityContentStatus.java) | ACTIVE, DELETED |
| [ReportStatus](src/main/java/com/kkirikkiri/report/entity/ReportStatus.java) | PENDING, COMPLETED, REJECTED |
| [ReportType](src/main/java/com/kkirikkiri/report/entity/ReportType.java) | USER, POST, COMMENT |
| [PreferenceCriterionCode](src/main/java/com/kkirikkiri/survey/entity/PreferenceCriterionCode.java) | DEPARTMENT, AGE, GRADE, BEDTIME, WAKE_TIME, SLEEP_TALKING, SNORING, TEETH_GRINDING, SMOKING, LIGHTING, SHOWER_TIME, SHOWER_DURATION, CLEANING_FREQUENCY, CLEANING_STYLE, INTERACTION, MEAL, SHARED_ITEMS, TEMPERATURE |
| [ProfileSurveyFieldCode](src/main/java/com/kkirikkiri/survey/entity/ProfileSurveyFieldCode.java) | MEAL_PLAN, DORM_STAY_DAY |
| [UserRole](src/main/java/com/kkirikkiri/user/entity/UserRole.java) | USER, ADMIN |
| [UserStatus](src/main/java/com/kkirikkiri/user/entity/UserStatus.java) | ACTIVE, SUSPENDED, WITHDRAWN |

### 제약·삭제 정책과 배포 검증 경계

- CalendarEvent에는 위 비고유 @Index 2개가 있다. @Check, @OnDelete, cascade, orphanRemoval은 명시하지 않는다. UNIQUE는 위 컬럼/복합 선언을 따르며 FK 보조 인덱스와 삭제·갱신 동작은 물리 DB에서 확인해야 한다. 기존 DB에 Java 선언을 추가한 사실만으로 제약이 이미 적용됐다고 판단하지 않는다.
- 사용자 탈퇴는 WITHDRAWN, 게시글·댓글 삭제는 DELETED 상태 변경이다. 현재 서비스에서 설문 옵션 교체·좋아요 해제·게시글 이미지 제거·사용자 일정/카테고리 제거 등에는 물리 행 삭제도 사용한다. 소프트 삭제와 DB ON DELETE CASCADE를 혼동하지 않는다. 신고·채팅 참조가 있는 행의 직접 삭제는 별도 영향 검토가 필요하다.
- 요청의 필수값·길이·가중치·날짜 범위·자기 자신 대상 금지·참여자 수 제한·고정 최대 2개는 DTO/서비스 규칙이다. DB NOT NULL/UNIQUE가 동시 고정 제한(#13)이나 모든 도메인 무결성을 보장하지 않는다.
- 운영의 테이블 대소문자, 실제 컬럼 타입/NULL/DEFAULT, PK/FK/UNIQUE/CHECK/인덱스, 고아 참조·중복, 기존 nullable 컬럼의 데이터는 운영자가 읽기 전용 DDL/데이터 점검으로 확인해야 한다. 이번 작업은 운영 DB에 접근하지 않았다. 기본/prod의 자동 DDL·SQL 초기화를 차단하고 Gradle 테스트는 H2 메모리 DB로 격리했다. 실제 운영 DDL/잠금 동작은 [DEPLOYMENT.md](DEPLOYMENT.md)에 따라 별도 검증한다.

- user 안정화 변경은 Entity의 테이블/컬럼/제약을 변경하지 않는다. 개발 시드 출처는 local 전용 파일에 ID·이메일·저장 자격증명의 SHA-256 지문으로 기록하며 DB 컬럼을 추가하지 않는다. 기록이 없거나 지문이 달라진 기존 사용자는 시드 생성·삭제에서 제외한다. 전공 해제는 요청의 명시적 "세부전공 없음"을 기존 nullable major_id에 저장한다.
- 익명 게시글 anonymous 선언이 있더라도 기존 DB에 컬럼/DEFAULT/NOT NULL이 적용됐는지 검증해야 한다. 익명 게시글의 실제 author.id 공개와 익명 채팅 키 변경은 별도 익명성·마이그레이션 검토 대상이며 문서 수정으로 해결되지 않는다. 익명 댓글·답글의 응답 author.id는 이미 null이며 내부 author_id 저장을 응답 ID 공개로 해석하지 않는다.

### 기존 익명 채팅 키의 수동 변경 검증 기준

[migrations/README.md](migrations/README.md)는 Git 제외를 해제했고 prod 구성과 DEPLOYMENT.md도 추가했다. 기존 마이그레이션 파일은 이번 user 안정화에서 실행하지 않았다. 적용 파일은 운영 DB 구조와 별도로 검토하며 아래 조건을 충족하기 전 실행하지 않는다.

1. **사전 점검:** 운영 DB 버전·schema·물리 테이블명, room_key 길이 100/UNIQUE, 출처 FK와 anonymous 컬럼을 확인한다. old_key/new_key 후보·행 ID를 내보내고 변환 키 충돌/중복·길이 초과·출처 누락·두 출처 동시 지정 행을 점검한다. 현재 출처의 익명성이 과거 방의 의도와 같은지 확인하며 과거 익명성을 복원할 수 없는 행은 별도 검토한다.
2. **백업·격리 검증:** 전체 DB 일관된 백업과 chat_room_id→old_key 목록을 안전하게 보관하고 격리된 동일 버전 DB에서 복구·적용·롤백을 검증한다. 방/참여자/메시지 삭제나 자동 병합으로 충돌을 해소하지 않는다.
3. **수동 적용 순서:** 모든 쓰기 인스턴스·채팅 연결을 중지한다. 운영자가 검토한 키 UPDATE를 하나의 트랜잭션으로 실행하며 오류 발생 시 중지하고 ROLLBACK한다. 예상 키·참조·행 수를 확인한 후 호환 서버만 시작한다. 운영 validate/SQL never 설정은 현재 미구현 #3이므로 이를 선행 조건으로 확인한다. 자동 DDL/SQL 실행으로 대체하지 않는다.
4. **롤백:** COMMIT 전은 ROLLBACK, COMMIT 후 신규 쓰기 전은 중지 상태에서 백업의 정확한 ID→old_key만 복원한다. 접미사를 일괄 제거하지 않는다. 신규 방/메시지가 발생한 뒤에는 쓰기를 중단하고 충돌·신규 데이터 보존을 고려한 별도 복구 계획을 세운다. 키 호환성 확인 없이 구버전을 재기동하지 않는다.
5. **검증:** 방/참여자/메시지의 행 수·ID·FK·본문 보존, 키 길이·UNIQUE, 익명 POST/COMMENT 방 분리·재사용과 마스킹을 확인한다. REST·STOMP 접근 및 만료/정지/나가기 이후 수신 차단을 확인한다. 현재 #12는 코드 미해결이므로 이 조건의 통과를 문서로 보장할 수 없다.
