# 백엔드 통합 테스트 작성 안내

## 1. 단위 테스트와 통합 테스트

- 단위 테스트는 Mockito 등으로 협력 객체를 대체하고 한 클래스의 로직을 확인한다.
- API 통합 테스트는 실제 Spring 컨텍스트, 보안 필터, Controller → Service → Repository → H2 연결을 확인한다. Service/Repository를 mock으로 대체하지 않는다.
- 이 환경은 Spring Boot 4.1.1 / Java 21과 기존 JUnit 5, MockMvc, H2 의존성을 사용한다. 새 의존성이나 별도 소스 세트는 추가하지 않는다.

## 2. 위치와 이름

새 테스트는 `src/test/java/com/kkirikkiri/integration/<도메인>/`에 두고 `*IntegrationTest.java`로 명명한다.
도메인은 auth, user, survey, matching, calendar, community, chat, report, admin 중 필요한 것만 생성한다.
기존 도메인 경로에 있는 통합 테스트는 그대로 유지하며 같은 이름 규칙이면 `integrationTest`에서도 실행된다.
공통 코드는 `integration/support/`에 둔다. 동작하는 예시는 [UserProfileIntegrationTest](src/test/java/com/kkirikkiri/integration/user/UserProfileIntegrationTest.java)를 참고한다.

## 3. 공통 코드 사용

상속 없이 클래스에 `@ApiIntegrationTest`를 붙인다. 이 애너테이션은 다음을 묶는다.

- `@SpringBootTest`, Spring Boot 4의 `@AutoConfigureMockMvc` (보안 필터 포함)
- `@ActiveProfiles("test")`, `@Transactional` (테스트 메서드마다 기본 롤백)
- 외부 접점 `JavaMailSender`, `AiGatewayClient`, `HknuAcademicCalendarCrawler`의 `@MockitoBean`
- 회원 fixture `TestUsers`의 명시적 import. 다른 기존 테스트에는 자동 등록되지 않는다.

```java
@ApiIntegrationTest
class ExampleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;

    @Test
    void readsMyProfile() throws Exception {
        var user = users.createUser();
        users.createProfile(user);
        mvc.perform(get("/api/users/me").with(users.authenticated(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getUserId()));
    }
}
```

위 예시의 `get`, `status`, `jsonPath`는 MockMvc 정적 import를 사용한다.
`authenticated(user)`는 실제 `JwtTokenProvider`로 Bearer JWT를 발급한다. 실제 서명 검증과 DB의 활성 계정 확인을 거치며 로그인 API 자체의 검증을 대체하지 않는다.
관리자는 `users.createUser(UserRole.ADMIN)`으로 생성한다. 로그인 테스트용 원문 비밀번호는 `TestUsers.PASSWORD`이며 저장값은 실제 PasswordEncoder로 해시한다.
`createProfile(user)`는 프로필이 필요한 경우에만 호출한다. 호출마다 고유한 기숙사/학부와 닉네임을 생성하며, 같은 회원에는 한 번만 호출한다.
설문 fixture는 기존 `com.kkirikkiri.survey.SurveyTestData`를 재사용한다.

외부 mock은 기본 Mockito 응답을 반환하며 테스트 메서드마다 초기화된다. 응답이 필요한 테스트는 `@Autowired AiGatewayClient ai`처럼 주입받아 `when(ai.analyze(any())).thenReturn("테스트 분석")` 등으로 설정한다.
메일 발송자는 테스트용 주소가 설정되어 있고 실제 SMTP는 호출하지 않는다. 네트워크 클라이언트를 직접 생성하지 않는다.

## 4. 데이터 생성과 격리

- 기존 `application-test.properties`와 Gradle 안전 설정을 재사용한다. `test`/`integrationTest`는 `.env` import를 끄고 `test` 프로필, H2 인메모리 URL/드라이버, `create-drop`을 강제한다. 컨텍스트별 UUID DB 이름으로 서로 다른 컨텍스트도 분리한다.
- SQL 초기화, 시드, 시작 시 수집, 이미지 정리 작업은 비활성이다. 기존 `EnvironmentSafetyInitializer`가 빈 생성 전에 외부/파일 DB 및 위험한 프로필 조합을 거절한다. 별도 DB 안전 검사를 중복 구현하지 않는다.
- 데이터는 각 테스트의 `@BeforeEach` 또는 메서드 안에서 필요한 만큼 생성한다. 공용 시드, 고정 ID, 테스트 실행 순서에 의존하지 않는다. 공통 회원 생성 코드는 호출자의 트랜잭션에서 실행된다.
- 동기 MockMvc 호출은 테스트 트랜잭션에 참여하고 끝나면 fixture와 API 변경 모두 롤백된다. DB 검증 시 `EntityManager.flush()` 후 `clear()`하고 재조회하여 1차 캐시만 검증하는 일을 피한다. 이는 커밋 성공 검증과는 구분한다.
- `REQUIRES_NEW`, auth 제한의 독립 커밋, 비동기/다중 스레드, 실제 서버 요청, `AFTER_COMMIT` 이벤트는 기본 롤백만으로 격리하거나 검증할 수 없다. 이런 테스트는 클래스에 `@Transactional(propagation = Propagation.NOT_SUPPORTED)`를 선언하고 `TransactionTemplate`로 fixture를 커밋한다. 각 메서드의 `@AfterEach`에서 생성한 데이터를 FK 역순으로 정리하고, 클래스에는 `@DirtiesContext(classMode = AFTER_CLASS)`로 컨텍스트도 폐기한다. 독립 커밋된 제한 레코드 등도 정리 대상이다. 기존 도메인의 동시성 테스트 패턴을 참고한다.
- 파일 I/O가 있으면 `@TempDir`과 해당 테스트 전용 경로를 사용한다. 트랜잭션 롤백은 파일을 삭제하지 않는다. 공유 테이블 전체 삭제, FK 검사 해제, `@Commit`으로 격리를 우회하지 않는다.
- 기본 JUnit 순차 실행을 유지한다. 병렬 실행은 별도 DB/컨텍스트 및 mock 격리를 검토한 후 적용한다.

## 5. 실행 명령어

Windows PowerShell에서 `cd backend` 후 실행한다. macOS/Linux에서는 `.\gradlew.bat`를 `./gradlew`로 바꾼다.

```powershell
# 기존 단위 테스트 + 기존/신규 통합 테스트 전체
.\gradlew.bat test

# 이름이 *IntegrationTest인 기존/신규 테스트만
.\gradlew.bat integrationTest

# 특정 통합 테스트 클래스만
.\gradlew.bat integrationTest --tests com.kkirikkiri.integration.user.UserProfileIntegrationTest

# 전체 테스트와 빌드 (bootJar 포함)
.\gradlew.bat build

# 저장소 기본 완료 검사
.\gradlew.bat test bootJar
node tools/sync-contract-docs.cjs --check
```

`test`의 기존 포함 범위를 유지하므로 `build`에서도 신규 통합 테스트가 실행된다.
`integrationTest`는 같은 테스트 소스와 런타임을 사용하는 선택 실행 작업이다. `check`에 중복 연결하지 않았다.
`test integrationTest`를 한 번에 지정하면 통합 테스트가 두 작업에서 각각 실행된다.
`integrationTest`는 애너테이션이나 `integration/` 패키지가 아니라 클래스 이름으로 선택한다. 공통 환경 구축 시점의 대상은 기존 27개 클래스와 신규 샘플 1개이며, 일반 테스트 메서드 143개와 파라미터화 메서드 84개의 입력별 실행 548개를 합쳐 691개가 실행된다. 일반 단위 테스트와 `@WebMvcTest` 클래스는 포함되지 않는다. 기존 JPA 슬라이스 통합 테스트 1개도 포함되므로 전부 HTTP API 테스트인 것은 아니다.
이름이 규칙과 다른 `UserAccountConcurrencyTest`와 컨텍스트 검증용 `BackendApplicationTests`는 `@SpringBootTest`이지만 `integrationTest`에서는 제외되고 `test`에서 실행된다. 따라서 `test`를 단위 테스트 전용 작업으로 해석하거나 `integrationTest`만으로 전체 회귀 검증을 대체하지 않는다.
HTML 보고서는 `build/reports/tests/test/index.html`, `build/reports/tests/integrationTest/index.html`에 각각 생성된다.
IDE에서는 `@ApiIntegrationTest`가 test 프로필을 적용한다. 애너테이션 없이 직접 구성하면 반드시 `@ActiveProfiles("test")`를 명시한다. prod/local 프로필이나 DB URL을 지정하지 않는다.

## 6. 주의사항과 후속 범위

- HTTP 상태, ApiResponse의 JSON 계약, 필요한 DB 상태와 인증/권한 실패를 함께 검증한다. 서비스/저장소는 실제 빈을 유지하고 외부 접점만 mock 처리한다.
- 새 외부 서비스가 추가되면 공통 애너테이션의 mock 대상도 검토한다. 기존 단위 테스트와 기존 통합 테스트의 개별 mock 설정을 일괄 변경하지 않는다.
- `prepareE2e`와 `src/e2e`는 별도 클래스패스/프로필/격리 MySQL을 유지한다. 이 공통 코드는 Playwright E2E에 포함되지 않으며 E2E 설정을 바꾸지 않는다.
- H2의 MySQL 모드는 실제 MySQL 잠금, 물리 DDL, 실행계획을 보장하지 않는다. 필요 시 Testcontainers를 별도 후속 작업으로 검토하고 기본 H2 안전 검사를 완화하지 않는다.
