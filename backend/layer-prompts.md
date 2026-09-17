# Spring Boot 레이어별 Codex 재사용 프롬프트

아래 프롬프트는 팀원들이 각자 담당 레이어를 작업할 때 `{도메인명}`과 `{기능명}`을 해당 작업에 맞게 바꿔서 사용한다.

모든 프롬프트는 프로젝트 루트의 `agent.md`를 공통 작업 지침으로 참고하도록 구성한다.

---

## 1. Entity

현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}`에 필요한 Entity 레이어를 구현해줘.

파일 경로

- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member\user

작업 기준:

- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`에서 `{기능명}`에 필요한 데이터 확인
- 해당 데이터와 연결된 `ERD.md` 테이블 확인
- 직접 대상 테이블뿐 아니라 필요한 데이터를 위해 FK로 연결된 관련 테이블까지 연관관계 확인 목적으로 확인
- ERD와 기존 패키지 구조를 기준으로 현재 `{도메인명}`이 소유하는 Entity 범위를 확인
- 현재 도메인 소유 Entity와 필요한 ERD 구조를 비교하여 누락된 Entity, 필드, 연관관계가 있는지 확인
- 다른 도메인이 소유하는 Entity는 기존 Entity를 재사용하고 현재 도메인에 중복 생성하지 않음
- 기존 Entity, BaseEntity, enum 확인 후 재사용
- ERD에 정의된 범위만 구현

작업 내용:

- `{기능명}` 구현에 필요한 Entity 생성 또는 수정
- 컬럼명, 타입, nullable, unique, 기본키, 연관관계 반영
- 필요한 도메인 상태 변경 메서드만 구현
- 기존 네이밍과 패키지 규칙 유지

완료 전 검증:

- `{기능명}`에서 필요한 데이터를 현재 Entity 구조로 모두 표현할 수 있는지 확인
- 현재 도메인이 소유하는 필요한 ERD 테이블, 컬럼, FK, 연관관계가 누락되지 않았는지 확인
- 현재 도메인의 Entity 레이어 내부 누락이 있으면 보완한 후 완료
- 다른 도메인 Entity 누락은 임의로 생성하지 않고 `주의 사항`에 보고

제한:

- `entity` 패키지 외 파일 생성·수정 금지
- ERD에 없는 필드 임의 추가 금지
- Repository, DTO, Service, Controller 생성·수정 금지
- 불필요한 양방향 연관관계 금지
- 요청 범위 밖 리팩터링 금지
- 다른 레이어 변경이 필요하면 수정하지 말고 `주의 사항`에 보고

완료 후 `agent.md`의 완료 보고 형식에 따라 간단히 보고해줘.

---

## 2. Repository

현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}`에 필요한 Repository 레이어를 구현해줘.

파일 경로

- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member\Repository

작업 기준:

- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`의 `{기능명}`에서 필요한 조회·저장 작업 확인
- `{기능명}` 구현에 필요한 Entity, 필드, 연관관계가 모두 존재하는지 확인
- 선행 Entity가 불완전하면 해당 부분을 우회 구현하지 말고 `주의 사항`에 보고
- 이미 존재하는 Entity만 재사용
- 기존 Repository 작성 방식 유지
- 현재 `{도메인명}`이 소유하는 Entity에 대해서만 Repository를 생성·수정

작업 내용:

- `{기능명}`에 실제 필요한 Repository 구현
- 필요한 조회/존재 확인 메서드만 추가
- Spring Data JPA 메서드 네이밍 우선
- 복잡한 조회만 기존 방식에 맞춰 @Query 등 사용
- 반환 타입은 Optional, List, Page 등을 목적에 맞게 선택

완료 전 검증:

- `{기능명}`에서 필요한 조회·존재 확인·저장 작업을 현재 Repository로 모두 수행할 수 있는지 확인
- 현재 도메인이 소유하는 Entity에 필요한 Repository 또는 조회 메서드가 빠져 있으면 보완한 후 완료
- 다른 도메인이 소유하는 Repository가 필요한 경우 기존 Repository를 재사용
- 필요한 다른 도메인 Repository가 존재하지 않으면 임의로 생성하지 않고 `주의 사항`에 보고
- Entity 누락 때문에 구현할 수 없는 부분은 임의로 구현하지 말고 `주의 사항`에 보고

제한:

- `repository` 패키지 외 파일 생성·수정 금지
- Entity 생성·수정 금지
- DTO, Service, Controller 생성·수정 금지
- 비즈니스 로직 작성 금지
- 사용하지 않는 조회 메서드 선제 구현 금지
- 필요한 Entity가 없으면 생성하지 말고 `주의 사항`에 보고

완료 후 `agent.md`의 완료 보고 형식에 따라 간단히 보고해줘.

---

## 3. DTO

현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}`에 필요한 DTO 레이어를 구현해줘.

파일 경로

- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member\dto

작업 기준:

- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`에서 `{기능명}` 관련 Request/Response만 확인
- 관련 Entity와 기존 DTO 확인
- 기존 DTO 네이밍과 패키지 규칙 유지
- `{기능명}`의 Request/Response 필드가 기존 DTO로 모두 표현되는지 확인

작업 내용:

- API 명세 기준 Request/Response DTO 구현
- API에 필요한 필드만 포함
- Request DTO에 필요한 Validation 적용
- Entity를 API에 직접 노출하지 않도록 구성
- 기존 변환 패턴이 있으면 재사용

완료 전 검증:

- `API.md`의 `{기능명}` Request/Response 필드가 DTO에 빠짐없이 반영됐는지 확인
- DTO 레이어 내부 누락이 있으면 보완한 후 완료
- Entity 등 다른 레이어의 불일치가 발견되면 임의로 수정하지 말고 `주의 사항`에 보고

제한:

- `dto` 패키지 외 파일 생성·수정 금지
- API.md에 없는 필드 임의 추가 금지
- 같은 역할의 DTO 중복 생성 금지
- Entity, Repository, Service, Controller 생성·수정 금지
- 다른 레이어 변경이 필요하면 수정하지 말고 `주의 사항`에 보고

완료 후 `agent.md`의 완료 보고 형식에 따라 간단히 보고해줘.

---

## 4. Service

현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}` Service를 구현해줘.

파일 경로

- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\member\service

작업 기준:

- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`의 `{기능명}` 요구사항 확인
- 이미 존재하는 Entity, Repository, DTO만 재사용
- 기존 global 예외 처리와 트랜잭션 방식 유지
- `{기능명}` 구현에 필요한 Entity, Repository, DTO의 클래스, 필드, 메서드가 모두 존재하는지 작업 전에 확인
- 선행 레이어가 불완전하면 Service에서 우회 구현하지 말고 `주의 사항`에 보고

작업 내용:

- `{기능명}`에 필요한 비즈니스 로직 구현
- 기존 Repository를 통한 데이터 조회, 검증, 저장 처리
- 적절한 @Transactional 적용
- 예외는 기존 예외 처리 방식 사용
- Controller가 호출할 공개 메서드와 반환 타입 구성

완료 전 검증:

- `{기능명}`의 전체 비즈니스 흐름이 기존 Entity, Repository, DTO만으로 처리 가능한지 확인
- Service 레이어 내부에 필요한 공개 메서드나 로직이 빠져 있으면 보완한 후 완료
- 선행 레이어 누락 때문에 구현하지 못한 부분은 `주의 사항`에 명확히 작성

제한:

- `service` 패키지 외 파일 생성·수정 금지
- Entity, Repository, DTO, Controller 생성·수정 금지
- 필요한 선행 파일이 없으면 임의로 만들지 말고 `주의 사항`에 보고
- HTTP 요청/응답 처리 금지
- Repository 역할 중복 구현 금지
- 요청하지 않은 기능 추가 금지
- 요청 범위 밖 리팩터링 금지

완료 후 `agent.md`의 완료 보고 형식에 따라 간단히 보고해줘.

---

## 5. Controller

현재 Spring Boot 프로젝트에서 `{도메인명}`의 `{기능명}` Controller를 구현해줘.

파일 경로

- C:\dev\project\roommate-matching-platform\backend\agent.md
- C:\dev\project\roommate-matching-platform\backend\ERD.md
- C:\dev\project\roommate-matching-platform\backend\API.md
- C:\dev\project\roommate-matching-platform\backend\src\main\java\com\kkirikkiri\{도메인명}\controller

작업 기준:

- 프로젝트의 `agent.md`를 먼저 참고
- `API.md`에서 `{기능명}` Endpoint만 확인
- 이미 존재하는 Service와 Request/Response DTO만 재사용
- 기존 인증 방식과 공통 응답 형식 유지
- `{기능명}` 구현에 필요한 Service 공개 메서드와 Request/Response DTO가 모두 존재하는지 작업 전에 확인
- 선행 Service 또는 DTO가 불완전하면 Controller에서 대신 구현하지 말고 `주의 사항`에 보고

작업 내용:

- API.md의 HTTP Method, URL, Request, Response 반영
- Request DTO Validation 적용
- 기존 Service 공개 메서드 호출
- 인증 사용자 정보가 필요하면 기존 인증 구조 재사용
- 공통 응답 객체가 있으면 재사용

완료 전 검증:

- `API.md`의 `{기능명}` Endpoint가 기존 Service와 DTO를 통해 모두 연결되는지 확인
- Controller 레이어 내부에 필요한 Endpoint가 빠져 있으면 보완한 후 완료
- Service 또는 DTO 누락 때문에 구현하지 못한 Endpoint는 `주의 사항`에 작성

제한:

- `controller` 패키지 외 파일 생성·수정 금지
- Entity, Repository, DTO, Service 생성·수정 금지
- 필요한 Service 또는 DTO가 없으면 임의로 만들지 말고 `주의 사항`에 보고
- Controller 내부 비즈니스 로직 작성 금지
- Repository 직접 호출 금지
- Entity 직접 반환 금지
- API.md에 없는 Endpoint 추가 금지
- Service 로직 중복 구현 금지

완료 후 `agent.md`의 완료 보고 형식에 따라 간단히 보고해줘.
