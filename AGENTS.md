# 프로젝트 개발 지침

## 범위와 기준
이 파일은 저장소 전체의 공통 지침이다. 백엔드 계약은 `backend/API.md`, 저장 구조는
`backend/ERD.md`, 운영 준비는 `backend/DEPLOYMENT.md`를 함께 확인한다.
문서와 코드가 다르면 최신 확정 요구사항, 실제 사용처, 테스트, 데이터 영향을 비교하여
문서 오류인지 구현 결함인지 판단한다. 오래된 설계를 이유로 정상 API나 스키마를 바꾸지 않는다.
미확정 정책은 구현하지 말고 필요한 결정을 보고한다.

## 기술 스택과 구조
- 백엔드: Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Validation,
  Jackson 3(`tools.jackson`), MySQL, JWT, SMTP, WebSocket/STOMP, Jsoup.
- 프론트: React 19, React Router, Axios, React Query, styled-components, FullCalendar;
  정확한 버전은 `frontend/package.json`과 잠금 파일을 따른다.
- 백엔드 루트: `backend/src/main/java/com/kkirikkiri`.
  도메인별 entity/repository/dto/service/controller와 `global` 공통 코드를 유지한다.
  community의 post/comment 하위 도메인, calendar의 crawler/scheduler 등 기존 예외를 존중한다.
- 프론트 API 호출은 `frontend/src/api`와 기존 인증 세션 처리 방식을 재사용한다.

## 구현 규칙
- 관련 파일과 연결된 호출부만 먼저 확인한다. 요청 범위를 넘어선 리팩터링·기능 추가는 하지 않는다.
- Controller는 HTTP 처리와 DTO 전달, Service는 업무 검증·트랜잭션,
  Repository는 영속화, Entity는 도메인 상태를 담당한다. Entity를 API로 직접 반환하지 않는다.
- DTO의 필드·타입·null 의미, ApiResponse, ErrorCode와 기존 상태 코드를 보존한다.
- 인증 사용자 ID는 서버 Principal에서 얻고 요청 ID를 신뢰하지 않는다.
  인증, 소유권, 활성 상태와 동시 요청의 무결성을 서버에서 검증한다.
- DTO의 Bean Validation, 서비스 검증과 Jackson 설정을 함께 확인한다.
- 사용자가 특정 레이어로 작업을 한정한 경우에만 레이어 수정 제한을 적용한다.
  일반 기능 수정에는 필요한 레이어·테스트·문서의 변경이 포함된다.
  선행 코드가 없으면 중복 구현이나 우회 대신 필요한 보완을 보고한다.
- DB/API 호환성 변경은 영향·마이그레이션을 검토한다. 공개 계약·미확정 익명성 정책을 임의 변경하지 않는다.

## 보안과 운영
- `.env`, 비밀키, SMTP/AI 자격증명을 출력하거나 커밋하지 않는다. `.env.example`에는 예시만 둔다.
- 기본/prod는 `ddl-auto=validate`, SQL 초기화 금지다. 운영 데이터 변경·시드·마이그레이션은 자동 실행하지 않는다.
- local만 개발용 DDL/기준정보 초기화·시드를 허용한다. local의 고정 관리자 계정은 운영에서 사용하지 않는다.
- prod와 local/test를 함께 활성화하지 않는다. 실제 배포 조건은 `backend/DEPLOYMENT.md`를 따른다.
- 토큰·외부 오류 원문을 로그/응답에 노출하지 않는다. 예외는 기존 ErrorCode로 처리하고 필요한 오류를 서버에 기록한다.
- Auth 요청/실패 제한은 MySQL 공유 상태와 원자적 예약으로 처리한다. 실패 기록은 업무 롤백과 분리한다.
  인스턴스별 메모리 카운터로 대체하거나 제한 DB 장애 시 검증을 우회하지 않는다.
  전달 IP는 명시된 신뢰 프록시 체인만 사용하고 서버의 자동 forwarded-header 처리는 끈다.
  SMTP 로그에는 오류 분류·서버 생성 추적 ID만 기록하고 수신자·원문 예외·메일 본문은 기록하지 않는다.
- WebSocket은 연결 시점뿐 아니라 SEND/SUBSCRIBE 처리 및 기존 구독의 실제 메시지 전달 시 권한을 확인한다.

## 테스트와 완료 기준
- 백엔드: `cd backend`, `./gradlew test bootJar` (Windows: `gradlew.bat test bootJar`).
  Gradle test 태스크는 test 프로필과 H2 메모리 DB를 강제한다. SQL/시드/스케줄 수집은 꺼진다.
  IDE 직접 실행은 반드시 test 프로필을 사용한다. 테스트 안전 검사가 외부 DB URL을 거절한다.
- H2 테스트는 MySQL의 잠금·물리 DDL·실행계획 검증을 대체하지 않는다.
  MySQL 검증은 별도 격리 DB가 확인된 경우에만 수행한다.
- Auth 테스트는 실제 SMTP 대신 mock을 사용한다. 공유 제한은 독립 limiter 객체·동시 요청·업무 롤백·복구를 검증하며,
  실제 다중 프로세스 MySQL 검증과 H2 검증을 구분한다. 새 auth DDL은 수동 마이그레이션 파일로만 제공한다.
- 변경과 관련된 인증·권한·동시성·롤백 테스트 및 빌드를 실행한다.
  프론트 수정 시 해당 테스트와 `npm run build`를 실행한다.
- `node backend/tools/sync-contract-docs.cjs --check`로 Controller/DTO/Entity 문서 스냅샷을 확인한다.
- 정상 기능·호환성을 보존하고 문서도 갱신한다. 컴파일 성공만으로 운영 검증 완료를 선언하지 않는다.
- 보고에는 변경 파일·해결한 문제·테스트/빌드 결과·미검증·정책 보류를 구분한다.
- AGENTS/API/ERD/배포/마이그레이션 안내는 Git 추적 대상이다.
