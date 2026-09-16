# Spring Boot 레이어별 Codex 재사용 프롬프트

아래 프롬프트는 팀원들이 각자 담당 레이어를 작업할 때 `{}` 부분만 바꿔서 사용한다.

모든 프롬프트는 프로젝트 루트의 `agent.md`를 공통 작업 지침으로 참고하도록 구성한다.

---

## 1. Entity

```text
현재 Spring Boot 프로젝트에서 `survey`의 Entity 레이어를 구현해줘.

파일 경로
- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\survey\entity

작업 기준:
- 프로젝트의 `agent.md`를 먼저 참고
- `ERD.md`에서 `survey` 관련 테이블만 확인
- 기존 `survey` 패키지와 관련 Entity 확인
- 기존 BaseEntity, enum, 연관 Entity가 있으면 재사용

작업 내용:
- ERD 기준으로 Entity 생성 또는 수정
- 컬럼명, 타입, nullable, unique, 기본키, 연관관계 반영
- 필요한 도메인 상태 변경 메서드만 구현
- 다른 Repository/Service에서 재사용하기 쉽게 기존 네이밍 규칙 유지

제한:
- ERD에 없는 필드 임의 추가 금지
- Repository, DTO, Service, Controller 구현 금지
- 불필요한 양방향 연관관계 금지
- 요청 범위 밖 리팩터링 금지

완료 후 변경 파일과 핵심 변경사항만 간단히 알려줘.
```

---

## 2. Repository

```text
현재 Spring Boot 프로젝트에서 `{도메인명}`의 Repository 레이어를 구현해줘.

파일 경로
- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member

작업 기준:
- 프로젝트의 `agent.md`를 먼저 참고
- 기존 `{도메인명}` Entity 확인
- 해당 기능에서 실제로 필요한 조회 조건 확인
- 기존 Repository 작성 방식을 유지

작업 내용:
- 기존 Entity를 재사용하여 JpaRepository 기반 Repository 구현
- 필요한 조회/존재 확인 메서드만 추가
- 메서드 이름으로 표현 가능한 조회는 Spring Data JPA 방식 우선
- 복잡한 조회만 기존 프로젝트 방식에 맞춰 @Query 등 사용
- 반환 타입은 Optional, List, Page 등을 목적에 맞게 선택

제한:
- Entity 구조 변경 금지
- DTO, Service, Controller 구현 금지
- 비즈니스 로직 작성 금지
- 사용하지 않는 조회 메서드 선제 구현 금지

완료 후 추가한 Repository 메서드와 용도만 간단히 알려줘.
```

---

## 3. DTO

```text
현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}`에 필요한 DTO 레이어를 구현해줘.

파일 경로
- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member

작업 기준:
- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`에서 `{기능명}` 관련 Request/Response만 확인
- 관련 Entity와 기존 DTO 확인
- 기존 DTO 네이밍과 패키지 규칙 유지

작업 내용:
- API 명세 기준 Request/Response DTO 구현
- API에 필요한 필드만 포함
- Request DTO에 필요한 Validation 적용
- Entity를 API에 직접 노출하지 않도록 구성
- 기존 변환 패턴이 있으면 그대로 재사용

제한:
- API.md에 없는 필드 임의 추가 금지
- 같은 역할의 DTO 중복 생성 금지
- Repository, Service, Controller 비즈니스 로직 수정 금지
- Entity 구조 임의 변경 금지

완료 후 생성/수정한 DTO와 각각의 용도만 간단히 알려줘.
```

---

## 4. Service

```text
현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}` Service를 구현해줘.

파일 경로
- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member

작업 기준:
- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`의 `{기능명}` 요구사항 확인
- 기존 Entity, Repository, DTO를 우선 재사용
- 기존 global 예외 처리와 트랜잭션 방식을 유지

작업 내용:
- `{기능명}`에 필요한 비즈니스 로직 구현
- 필요한 데이터 조회, 검증, 저장 처리
- 적절한 @Transactional 적용
- 존재하지 않는 데이터, 중복, 권한 등의 예외는 기존 예외 처리 방식 사용
- Controller가 바로 호출할 수 있도록 공개 메서드와 반환 타입을 명확하게 구성

제한:
- HTTP 요청/응답 처리 금지
- Controller 역할 구현 금지
- Repository 로직 중복 구현 금지
- Entity 구조 임의 변경 금지
- 요청하지 않은 기능 추가 금지

완료 후 구현한 Service 메서드와 처리 흐름만 간단히 알려줘.
```

---

## 5. Controller

```text
현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}` Controller를 구현해줘.

파일 경로
- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member

작업 기준:
- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`에서 `{기능명}` Endpoint만 확인
- 기존 Service와 Request/Response DTO를 재사용
- 기존 인증 방식과 공통 응답 형식을 유지

작업 내용:
- API.md의 HTTP Method, URL, Request, Response를 그대로 반영
- Request DTO Validation 적용
- 기존 Service 공개 메서드를 호출
- 인증 사용자 정보가 필요하면 기존 인증 구조 재사용
- 공통 응답 객체가 있으면 그대로 사용

제한:
- Controller 내부 비즈니스 로직 작성 금지
- Repository 직접 호출 금지
- Entity 직접 반환 금지
- API.md에 없는 Endpoint 추가 금지
- Service 로직 중복 구현 금지

완료 후 구현한 Endpoint와 연결된 Service 메서드만 간단히 알려줘.
```
