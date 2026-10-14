# user 도메인 운영 준비

이 문서는 user 안정화 변경의 배포 조건이다. 다른 도메인의 배포 적합성이나 실제 운영 성공을 보장하지 않는다.

## 프로필과 초기화

| 환경 | Hibernate DDL | SQL 초기화 | 개발 계정·시드 |
| --- | --- | --- | --- |
| 기본 / prod | validate | never | 빈 자체가 등록되지 않음 |
| local | update | data-common.sql | 명시적으로 허용 |
| test | H2 메모리 create-drop | never | 금지 |

- 운영은 `SPRING_PROFILES_ACTIVE=prod`를 사용한다. prod와 local/test, local과 test의 조합은 DB 연결 전에 거절된다.
- 안전 검사는 Spring의 실제 프로필 판정을 따른다. 활성 프로필이 없으면 `spring.profiles.default`에도 동일한 충돌·테스트 DB 검사를 적용한다. 활성 프로필이 있으면 비활성 기본 프로필은 혼합하지 않는다.
- 기본/prod에서 DDL/SQL 초기화를 위험한 값으로 재정의하면 시작을 거절한다. APP_SEED_MODE가 전달되어도 운영에서 개발 시드 빈은 생성되지 않는다.
- `.env` import는 local에만 있다. 운영 DB/JWT/SMTP/AI 설정은 배포 환경의 비밀 관리 기능으로 전달한다. 실제 비밀 값을 문서·Git·로그에 넣지 않는다.
- local 실행은 기존처럼 `SPRING_PROFILES_ACTIVE=local`로 시작한다. 기준정보 INSERT는 local 전용이며 여러 local 인스턴스의 최초 초기화는 동시에 실행하지 않는다.
- 시드 출처 파일은 기본 `local-data/seed-users.properties`이며 Git에서 제외한다. 새 시드 회원의 DB 커밋 후 ID·이메일·자격증명 지문을 기록한다. 원문 비밀번호와 BCrypt 해시는 기록하지 않는다.
- 시드 생성은 기존 계정을 임의로 채택하지 않는다. 삭제도 출처 기록과 현재 계정이 일치할 때만 허용한다. 기록 유실·지문 변경·기존 레거시 시드는 보존한다. 파일을 다른 DB에 복사하거나 수동으로 출처를 만들지 않는다.
- 알려진 시드에도 다른 기능의 참조가 있으면 FK 오류로 삭제 전체가 롤백될 수 있다. 이를 우회하기 위해 실제 회원이나 참조 데이터를 일괄 삭제하지 않는다.

## 스키마 변경 절차

이번 변경에는 Entity 스키마 변경이 없어 자동/수동 DDL 마이그레이션을 추가하지 않았다.

1. 대상 MySQL 버전·schema·물리 테이블 이름과 `SHOW CREATE TABLE`을 읽기 전용으로 확인한다.
2. USER/USER_PROFILE 및 기준정보의 PK/FK/NOT NULL/UNIQUE, 중복·고아 참조와 기존 MBTI 형식을 확인한다.
3. 필요한 기존 스키마 보완이나 기준정보 적재는 별도로 검토한 SQL로 준비한다. 운영 시작 실패를 `ddl-auto=update`로 우회하지 않는다.
4. 백업·복구 가능성과 격리된 동일 버전 MySQL에서의 적용/롤백을 확인한다. 기존 마이그레이션은 `migrations/README.md`의 개별 조건도 따른다.
5. 운영자의 검토 후 필요한 SQL만 수동 적용하고 validate/SQL never로 시작한다. 애플리케이션 시작만으로 SQL을 적용하지 않는다.

## 테스트와 실제 운영 검증

- `gradlew.bat test bootJar`는 test 프로필·H2 메모리 URL·SQL/시드/수집 비활성을 강제한다. 기존 DB 환경변수는 실제 연결 설정으로 사용하지 않는다. prod 요청은 테스트 JVM 실행 전에 거절한다.
- IDE 직접 테스트도 test 프로필을 사용한다. 실제 연결 설정이 H2 메모리가 아니거나 초기화가 켜져 있으면 ApplicationContextInitializer가 빈 생성 전에 거절한다.
- `node tools/sync-contract-docs.cjs --check`는 문서에 연결된 REST 선언·record DTO 필드/타입·Entity 컬럼 선언 및 ErrorCode 표를 읽기 전용으로 대조한다. 클래스 DTO의 동작, DB의 물리 제약과 서비스 검증 전체를 대체하지 않는다.
- H2는 MySQL 잠금·교착 상태·실행계획·물리 DDL 검증을 대체하지 않는다. MySQL 테스트는 URL·대상 schema·일회용 데이터가 확인된 별도 환경에서만 수행하고 기본 테스트의 안전 검사를 완화하지 않는다.
- 격리 MySQL에서 비밀번호 변경/재설정/관리자 정지의 두 커밋 순서, 동시 닉네임 UNIQUE 충돌·전체 롤백, USER → TOKEN 잠금 순서와 잠금 시간 제한을 확인한다. 잠금 실패는 409 ACCOUNT_UPDATE_CONFLICT로 응답한다. JPA 잠금 타임아웃 힌트 지원 여부와 DB의 실제 lock wait timeout을 함께 확인한다.
- 매칭 갱신은 최대 2개 작업 스레드·100개 대기 작업으로 제한된다. 큐 포화·계산 실패·프로세스 종료 후에는 기존 matching 조회가 필요한 결과를 다시 계산한다. 대표 회원 수에서 저장 응답 시간, 큐 포화 로그, 배치당 잠금 시간, 추천 조회 시간을 측정한다.
- 운영 API 주소·CORS origin·HTTPS/JWT 키·SMTP 연결·시간대도 실제 배포 환경에서 확인한다. 비밀번호 변경 후 JWT/기존 재설정 토큰 폐기 정책은 이번 변경에서 새로 도입하지 않았다.
