# agent.md

## 목적

이 문서는 팀원들이 Codex로 Spring Boot 백엔드를 개발할 때 공통으로 참고하는 작업 지침이다.

현재 확정 요구사항, 실제 코드·호출부·테스트, `ERD.md`, `API.md`를 함께 비교하여 판단한다. 오래된 문서를 이유로 정상 API나 스키마를 임의 변경하지 않는다. 이 파일은 백엔드 상세 지침이며 파일명을 유지한다. 루트 `../AGENTS.md`도 존재하지만 그 운영·테스트 조건이 실제 설정에 구현되어 있는지는 별도로 확인한다.

검토 기준일: 2026-10-14. 문서의 **현재 구현**, **배포 전 필요한 보완**, **미확정 정책**을 구분한다. 이번 문서 수정은 코드·설정·DB 결함의 해결을 의미하지 않는다.

---

## 기술 스택

- Java 21
- Spring Boot 4.1.1 (정확한 버전은 build.gradle 기준)
- Spring Data JPA, Spring Security, Bean Validation, Jackson 3(tools.jackson), Lombok
- MySQL, Stateless JWT, SMTP, WebSocket/STOMP, Jsoup
- 프론트: React 19, Axios, React Query, React Router, styled-components, FullCalendar (버전은 ../frontend/package.json 기준)

---

## 기본 패키지 구조

프로젝트의 기존 구조를 우선한다.

기본적으로 다음 레이어를 사용한다.

```text
{domain}/
├─ entity/
├─ repository/
├─ dto/
├─ service/
└─ controller/
```

기존 프로젝트에 다른 구조가 이미 있으면 새 구조를 임의로 만들지 않는다.

백엔드 루트는 `src/main/java/com/kkirikkiri`이다. community는 post/comment 하위 도메인, calendar는 crawler/scheduler 등 기존 보조 패키지를 사용한다. 공통 인증·응답·예외·설정은 global, 프론트 API 호출은 ../frontend/src/api를 재사용한다.

---

## 작업 전 확인 순서

Codex는 전체 프로젝트를 불필요하게 분석하지 말고 아래 순서로 필요한 파일만 확인한다.

1. 작업 대상 도메인의 기존 파일
2. `ERD.md`의 관련 테이블
3. `API.md`의 관련 API
4. 필요한 `global` 코드
5. 직접 연관된 다른 도메인 코드
6. 현재 레이어 구현에 필요한 선행 레이어의 클래스, 필드, 연관관계, 메서드 확인

---

## 공통 원칙

- 기존 코드를 먼저 확인하고 가능한 경우 재사용한다.
- 같은 역할의 클래스, 메서드, DTO를 중복 생성하지 않는다.
- 요청받은 범위만 수정한다.
- 요청하지 않은 리팩터링이나 기능 추가를 하지 않는다.
- 기존 네이밍과 코드 스타일을 유지한다.
- `ERD.md`, `API.md`와 구현 내용이 일치하도록 한다.
- 다른 팀원이 재사용할 수 있도록 공개 메서드와 DTO 계약을 명확하게 유지한다.
- 레이어별 작업 요청에서는 지정된 레이어의 파일만 생성·수정한다.
- 사용자가 작업을 특정 레이어로 한정한 경우에만 다른 레이어의 생성·수정을 제한한다. 일반 기능 수정에서는 요청 달성에 필요한 연관 코드·테스트·문서를 함께 검토한다.
- 특정 레이어로 한정된 작업에서 필요한 선행 Entity, Repository, DTO, Service 등이 없으면 대신 만들지 않고 완료 보고의 `주의 사항`에 누락 파일과 필요한 이유를 작성한다.
- 특정 레이어 작업에서 선행 코드 보완이 범위를 넘으면 누락과 이유를 보고한다. 문서 검토·수정만 요청한 작업은 코드 수정 없이 문제를 보고한다.
- 기존 코드에 영향이 큰 변경은 임의로 진행하지 않는다.

---

## 레이어 역할

### Entity

- DB 테이블과 도메인 상태를 표현한다.
- 확정된 저장 계약과 실제 Entity를 비교하여 필드, 타입, 제약조건, 연관관계를 정의한다. ERD의 Java 검증과 DB 제약을 구분한다.
- 필요한 도메인 상태 변경 메서드만 포함한다.
- API 응답 용도로 직접 사용하지 않는다.

### Repository

- Entity 조회와 저장을 담당한다.
- Spring Data JPA를 우선 사용한다.
- 필요한 조회 메서드만 작성한다.
- 비즈니스 로직을 작성하지 않는다.

### DTO

- Controller와 Service 사이의 요청/응답 데이터를 전달한다.
- Entity를 외부에 직접 노출하지 않는다.
- `API.md`의 Request/Response 형식을 따른다.
- 요청 DTO는 필요한 Validation을 적용한다.

### Service

- 비즈니스 로직을 담당한다.
- Repository를 통해 데이터를 조회/저장한다.
- 트랜잭션, 검증, 예외 처리를 담당한다.
- Controller의 HTTP 처리 로직을 포함하지 않는다.

### Controller

- HTTP 요청과 응답을 담당한다.
- Service를 호출한다.
- Repository를 직접 호출하지 않는다.
- 비즈니스 로직을 작성하지 않는다.
- `API.md`의 Method, URL, Request, Response를 따른다.

---

## 의존 방향

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Entity
```

DTO는 Controller와 Service 사이에서 사용한다.

---

## 선행 레이어 누락 및 완료 검증 (특정 레이어로 한정된 작업)

- 파일이 존재한다는 이유만으로 선행 레이어가 완료된 것으로 판단하지 않는다.
- 현재 기능 구현에 필요한 클래스, 필드, 연관관계, 메서드가 모두 존재하는지 확인한다.
- 선행 레이어의 누락이나 불일치로 현재 레이어를 정상적으로 구현할 수 없는 경우 임의로 우회하거나 다른 레이어에서 대신 구현하지 않는다.
- 이 경우 현재 레이어에서 문제없이 구현 가능한 범위만 작업하고, 누락된 파일 또는 항목과 필요한 이유를 완료 보고의 `주의 사항`에 작성한다.
- 선행 레이어가 보완된 후 현재 레이어를 다시 검토한다.

### 선행 레이어 기준

- Entity: 선행 레이어 없음
- Repository: Entity
- DTO: API.md와 필요한 Entity
- Service: Entity, Repository, DTO
- Controller: Service, DTO

현재 도메인과 직접 연관된 다른 도메인의 코드가 필요한 경우 기존 코드를 참조·재사용하되,
해당 파일을 현재 도메인에 중복 생성하지 않는다.
다른 도메인의 필요한 코드가 존재하지 않으면 `주의 사항`에 보고한다.

### 레이어별 완료 기준

- Entity: `{기능명}`에서 필요한 데이터를 `ERD.md` 기준 Entity 구조로 모두 표현할 수 있어야 한다.
- Repository: `{기능명}`에서 필요한 조회·저장 작업을 기존 Entity 기준으로 모두 수행할 수 있어야 한다.
- DTO: `{기능명}`의 `API.md` Request/Response 계약을 모두 표현할 수 있어야 한다.
- Service: `{기능명}`의 비즈니스 로직을 기존 Entity, Repository, DTO를 사용하여 모두 처리할 수 있어야 한다.
- Controller: `{기능명}`의 Endpoint를 기존 Service와 DTO를 사용하여 처리할 수 있어야 한다.

현재 레이어 내부에서 누락을 발견한 경우 해당 레이어 범위 안에서 보완한 후 완료한다.
선행 레이어의 누락을 발견한 경우 해당 레이어를 임의로 수정하지 않고 `주의 사항`에 보고한다.

---

## 레이어 재사용 규칙

### Entity 담당자

다른 레이어가 그대로 사용할 수 있도록 다음 계약을 안정적으로 유지한다.

- Entity 클래스명
- 필드명과 타입
- 연관관계
- 주요 도메인 메서드

### Repository 담당자

Service가 그대로 사용할 수 있도록 조회 메서드의 역할과 시그니처를 명확하게 유지한다.

### DTO 담당자

Controller와 Service가 공통으로 사용할 수 있도록 Request/Response 필드와 변환 방식을 일관되게 유지한다.

### Service 담당자

Controller가 그대로 호출할 수 있도록 공개 메서드 시그니처를 명확하게 유지한다.

### Controller 담당자

기존 Service와 DTO를 그대로 사용하고 같은 로직을 다시 작성하지 않는다.

---

## 금지 사항

별도 요청이 없다면 다음 작업을 하지 않는다.

- 전체 프로젝트 리팩터링
- 패키지 구조 변경
- DB 스키마 임의 변경
- API 명세 임의 변경
- 라이브러리 임의 추가
- 특정 레이어로 한정된 요청에서 범위 밖 레이어의 임의 생성·수정
- 사용하지 않는 기능 선제 구현
- 테스트용 임시 API 추가
- Entity 직접 API 반환
- Controller에서 Repository 직접 호출

---

## 문서와 코드가 다른 경우

### ERD와 Entity가 다른 경우

실제 Entity, 최신 확정 요구사항, 저장·삭제 사용처를 비교한다. 문서 오류는 문서를 수정하고 코드 결함은 코드 보완 대상으로 보고한다. 운영 DB의 실제 DDL을 확인하지 않고 스키마 일치를 선언하지 않는다.

### API와 Controller가 다른 경우

실제 Controller·DTO·ErrorCode, 보안 설정, 프론트 호출을 대조한다. 정상 공개 계약은 유지하고 미구현 설계는 별도로 표시한다. 코드의 보안·동시성 결함을 문서 수정으로 해결된 것으로 처리하지 않는다.

### 최신 기준이 불명확한 경우

기존 동작을 임의로 깨지 말고 최소 변경만 수행한다.

### 다른 레이어 수정이 필요한 경우

현재 요청 범위에 필요한 레이어 변경이 포함되는지 판단한다. 문서만 또는 특정 레이어만 수정하도록 제한된 경우에는 범위 밖 변경을 하지 않고 필요한 보완과 이유를 보고한다.

---

## Codex 토큰 절약 기준

- 작업 대상과 직접 관련된 파일만 읽는다.
- 매 작업마다 전체 `src`를 다시 분석하지 않는다.
- 이미 존재하는 코드를 다시 길게 설명하지 않는다.
- 작업 결과에서 전체 파일 내용을 반복 출력하지 않는다.
- 변경 파일과 핵심 내용만 간단히 보고한다.

완료 후 아래 형식대로 보고해줘

완료 보고 형식:

작업 상태

- 완료 / 부분 완료 / 선행 레이어 보완 필요

변경 파일

- ...

구현 내용

- ...

주의 사항

- ...

- 선행 레이어 누락으로 요청한 기능 전체를 구현하지 못한 경우 `완료`로 보고하지 않는다.
- 선행 레이어 보완이 필요한 경우 필요한 레이어와 파일/필드/메서드를 구체적으로 작성한다.
- 주의 사항이 없으면 생략한다.

---

## 최종 기준

### 보안·설정·테스트 기준과 현재 미해결 상태

- 인증 사용자 ID는 Principal에서 얻는다. 서버에서 소유권·활성 상태·입력 조건을 검증하고 기존 ApiResponse/ErrorCode를 사용한다. 토큰·비밀키·외부 오류 원문을 응답이나 로그에 노출하지 않는다. Entity 직접 반환 금지 규칙을 유지한다.
- `.env`와 실제 비밀 값은 출력·커밋하지 않는다. 배포 설정에는 DB/SMTP/JWT/AI, CORS origin, 이미지 영구 경로, 프론트 API 주소와 시간대 확인이 필요하다.
- **현재 설정:** application.properties는 ddl-auto=update, SQL 초기화 always 및 .env import를 사용한다. application-prod.properties는 없고 local 설정도 update/always다. 운영 validate/SQL never 구성은 **미해결 #3**이며 문서의 요구 기준일 뿐 현재 보장되지 않는다.
- SeedDataInitializer는 프로필 제한 없이 APP_SEED_MODE(NONE/CREATE/DELETE)를 처리한다. DevDataInitializer는 local에서 고정 계정을 생성한다. 운영에서 local 활성화·시드 변경·고정 계정 사용을 피해야 하며 서버 측 방어 보완은 별도 작업이다. 이 문서가 초기화 설정을 변경하지 않는다.
- **현재 테스트:** build.gradle은 JUnit Platform만 지정하며 테스트 DB URL·프로필 격리를 강제하지 않는다. BackendApplicationTests.contextLoads는 별도 프로필 없는 SpringBootTest다. DB 격리는 **미해결 #4**다. 격리 DB·SQL/시드 비활성을 확인하기 전 DB 접속 테스트나 전체 애플리케이션을 실행하지 않는다.
- 운영 스키마 변경·시드·파괴적 마이그레이션을 자동 실행하지 않는다. JVM/DB의 서울 시간대와 백업·프록시·저장소를 실제 환경에서 검증한다. Jackson 시간대 설정만으로 LocalDateTime.now의 시간대가 고정되는 것은 아니다.
- 변경에 맞는 인증·소유권·동시성·실패/롤백 검증이 필요하다. 컴파일만으로 완료를 선언하지 않으며 테스트 환경이 없으면 미검증으로 보고한다. 이번 문서 수정에서는 테스트/빌드를 실행하지 않는다.
- 파일명 변경은 이번에 하지 않는다. 루트 AGENTS.md 및 README.md를 제외하는 .gitignore 규칙은 **미해결 #1**이다. 표준 지침·배포 안내의 추적 정책 정리는 별도 제안이며 이 파일이나 Git 규칙을 이미 변경했다고 보고하지 않는다.
- API.md의 19장과 ERD.md의 10장은 현재 선언의 상세 대조 기준이다. 실제 DB와 동작 검증을 대체하지 않는다.

배포 위험 확인 근거와 남은 작업:

| 대상 | 현재 근거 | 필요한 후속 작업 |
| --- | --- | --- |
| #3 운영 설정 | [application.properties](src/main/resources/application.properties), [SeedDataInitializer](src/main/java/com/kkirikkiri/global/init/SeedDataInitializer.java), [DevDataInitializer](src/main/java/com/kkirikkiri/global/init/DevDataInitializer.java) | 운영 전용 validate/SQL never, 시드·고정 계정 차단과 필수 설정 검증. 현재 구현 완료로 취급하지 않음 |
| #4 테스트 격리 | [build.gradle](build.gradle), [BackendApplicationTests](src/test/java/com/kkirikkiri/BackendApplicationTests.java) | 격리 DB·프로필을 강제하고 운영/개발 DB 접속 차단을 검증 |
| #12 WebSocket | [ChatStompInterceptor](src/main/java/com/kkirikkiri/global/config/ChatStompInterceptor.java), [WebSocketConfig](src/main/java/com/kkirikkiri/global/config/WebSocketConfig.java), [ChatService](src/main/java/com/kkirikkiri/chat/service/ChatService.java) | 만료·계정 상태·참여 철회 후 송신 및 기존 구독 수신 차단과 테스트 |
| #13 고정 개수 | [ChatService.updateSettings](src/main/java/com/kkirikkiri/chat/service/ChatService.java) | 동시 고정 요청에 사용자별 최대 2개 제한 보장 |
| #14 실행 이력 | [CalendarCrawlingService](src/main/java/com/kkirikkiri/calendar/service/CalendarCrawlingService.java), [ScheduleCrawlRun](src/main/java/com/kkirikkiri/calendar/entity/ScheduleCrawlRun.java) | 실행 시작·완료·건수·실패를 저장하고 일정 롤백과 독립된 실패 이력 보존 |

- SMTP는 STARTTLS/발신자·자격 증명이 필요한 실제 외부 의존성이고, AI_API_KEY/AI_BASE_URL/AI_MODEL 및 DB/JWT는 placeholder 의존성이 있다. JWT 키 조건, CORS의 정확한 운영 origin, 이메일 링크의 프론트 주소, 외부 연결·오류 처리는 배포 환경에서 검증한다. 실제 `.env` 값을 이 문서에 복사하지 않는다.
- 현재 이미지 경로는 상대 경로 `uploads/community` 기본값이다. 쓰기 권한·영구 볼륨·백업·공개 URL을 검증해야 한다. WebSocket은 `/ws/chat`의 Upgrade/Connection 전달·TLS·타임아웃과 STOMP 도착 여부를 프록시에서 확인한다. Simple broker의 다중 인스턴스 전달은 별도 환경 검증 대상이다.
- 익명 게시글/댓글은 [CommunityAuthorResponse](src/main/java/com/kkirikkiri/community/post/dto/CommunityAuthorResponse.java)에서 실제 author.id를 유지한다. 표시 익명과 사용자 식별자 비공개의 정책 차이를 결정해야 하며 계약을 임의 변경하지 않는다.
- [EmailVerificationService.verify](src/main/java/com/kkirikkiri/auth/service/EmailVerificationService.java)는 6자리 코드·만료·해시를 확인하지만 실패 시도 횟수/검증 요청 제한은 없다. 재발송 cooldown은 검증 시도 제한이 아니다. 추측 공격·BCrypt 반복 처리 부하를 줄이는 제한 정책과 구현, 외부 프록시 제한 여부 확인이 필요하다.
- [migrations/README.md](migrations/README.md)는 존재하지만 현재 Git에서 추적되지 않는다. 또한 운영 prod 구성이 있다고 가정하거나 없는 DEPLOYMENT.md를 참조하는 문구가 남아 있어 그대로 배포 승인 기준으로 사용할 수 없다. 이번 범위에서 수정하지 않는다. 익명 키 적용은 ERD.md의 사전 점검·백업·수동 적용·롤백·검증 기준과 함께 별도 검토한다.

### 기존 18건의 재확인 결과

| 번호 | 심각도 | 현재 판정 |
|---|---|---|
| 1 | Medium | 명칭 유지·상세 지침 정리. 파일명/추적 정책 정비는 범위 밖으로 보류 |
| 2 | High | 문서 우선 강제와 레이어 제한 모순 수정 |
| 3 | High | 현재 위험한 설정과 운영 필요 조건을 명시. 설정/코드 보완 미해결 |
| 4 | High | 격리 확인 전 DB 테스트 금지 명시. 테스트 설정 보완 미해결 |
| 5 | Low | 현재 프론트 연동 상태와 미구현 기능 구분 |
| 6 | High | 23개 설문 기준으로 두 과거 필드 지시 제거 |
| 7 | Medium | 룸메이트 Public/선택 인증과 게스트 응답 명시 |
| 8 | Medium | 회원 검증·전공 변환·PATCH null 유지 보완 |
| 9 | Medium | 현재 70개 REST의 반환 data, DTO 선언, nullable, HTTP/ErrorCode 보완 |
| 10 | Medium | 게시글 anonymous를 API와 ERD에 추가 |
| 11 | High | keepImageIds의 제거 의미 명시, 프론트 유지 ID 전달 코드 대조 |
| 12 | High | CONNECT 이후 권한 재검증/기존 구독 수신 제한은 코드 미해결 |
| 13 | Medium | 동시 고정 2개 제한은 코드 미해결 |
| 14 | High | 실제 크롤링의 실행 이력 저장은 코드 미해결 |
| 15 | High | DORMITORY 수집 미구현 표시, 임의 구현 없음 |
| 16 | High | 현재 프론트 점수 계산 유지, 서버 API를 예정 설계로 분리 |
| 17 | Medium | Entity의 NULL/DEFAULT/PK/FK/UNIQUE와 애플리케이션 검증 구분. 물리 DB는 미검증 |
| 18 | Medium | 같은 날짜의 두 시간만 비교하는 기간 일정 규칙 정정 |

문서 수정으로 해결한 번호는 2, 5~11, 15~18(12건)이다. 1, 3, 4, 12~14(6건)는 지침/위험 설명을 보완했지만 실제 문제를 해결한 것으로 집계하지 않는다. 익명 작성자 ID 재식별과 이메일 인증번호 시도 제한 정책은 추가 확인 대상이다. 이전 작업의 테스트 통과 보고를 현재 소스의 실행 결과로 사용하지 않는다.

모든 구현은 다음 조건을 만족해야 한다.

- 현재 존재하는 선행 레이어를 기준으로 컴파일 가능한 코드
- 특정 레이어로 한정된 작업에서 선행 코드가 없어 정상 구현/컴파일이 불가능하면 범위 밖 코드를 임의 생성하지 않고 주의 사항에 보고
- 기존 프로젝트 구조와 코드 스타일 준수
- 레이어 책임 분리
- 기존 코드 재사용
- 중복 구현 최소화
- 확정 계약과 현재 구현을 대조하여 정리한 ERD/API 기준 준수 (미구현·미해결을 구현 완료로 취급하지 않음)
- 다른 팀원이 이어서 작업할 수 있는 구조
- 요청 범위 밖 변경 최소화
