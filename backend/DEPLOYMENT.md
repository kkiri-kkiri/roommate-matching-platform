# user / auth 도메인 운영 준비

## Admin·Report 결함 보완 (2026-10-20)

신고 처리에 REPORT 단일 행의 비관적 쓰기 잠금·최신 상태 재검사를 추가한다. 상세 사유는
500 UTF-16 단위이며 신규 생성·처리 시각은 기존 DATETIME에 맞춰 초 단위로 절삭한다.
스키마 변경·과거 이력 수정·자동 마이그레이션은 없다. Hibernate 잠금 timeout 힌트는
5초지만 실제 MySQL/드라이버의 적용 여부와 DB lock wait 설정은 별도 확인해야 한다.
JVM 시계가 생성 시각보다 이전 초라면 기존 시간 검증으로 트랜잭션을 거절하므로,
인스턴스의 시간대·NTP·JDBC 설정을 배포 환경에서 확인한다.
이번 보완의 실행 검증은 H2에 한정하며 MySQL에 접속하지 않는다.
[회귀 검증 및 별도 MySQL 승인 계획](REPORT_ADMIN_VALIDATION.md)을 따른다.

## 채팅 단일 JVM 배포 및 문제 2 위험 수용 (2026-10-20)

사용자가 팀원 확인 후 졸업작품의 배포 계획을 **Spring Boot 백엔드 인스턴스 1개(단일 JVM)**로
확정했다. 문제 2(다중 JVM WebSocket 전달)는 이 배포 조건에 따른 **위험 수용**으로 처리한다.
현재 `enableSimpleBroker("/sub")` 구조를 유지하고 Redis·RabbitMQ 등 외부 메시지 브로커를 도입하지 않는다.
의존성·설정·인프라·운영 코드를 변경하는 결정은 포함하지 않는다.

수용 범위와 배포 조건:

1. 모든 채팅 WebSocket 연결·구독·SEND가 동일한 하나의 활성 백엔드 JVM에 도착해야 한다.
   replica는 1개로 유지하고 자동 수평 확장·두 프로세스 동시 처리를 사용하지 않는다.
   호스트가 하나여도 활성 JVM이 둘이면 이 조건을 만족하지 않는다.
2. 재배포 시 기존 JVM의 채팅 연결/처리를 종료한 뒤 새 JVM으로 전환한다. rolling/blue-green 중
   구·신 JVM에 활성 연결이 나뉘는 중첩을 허용하지 않는다. 연결 종료·일시 중단과 재접속이 필요하다.
3. 단일 장애점·단일 프로세스의 용량 한계·재시작 시 메모리 구독 소멸을 수용한다. 클라이언트는
   재접속·재구독 후 저장된 메시지를 REST로 복구한다. DB 커밋과 push 사이 장애의 전달 유실 및
   즉시 전달·무손실 재전송을 보장하지 않는 한계는 남는다.
4. 실제 배포의 프로세스 수·replica/전환 설정과 기본 /ws/chat, WSS/TLS, 정확한 CORS Origin,
   프록시 Upgrade/Connection·idle timeout, 재연결·REST 복구는 배포 시 확인한다.
   사용자 확인은 배포 계획의 확정이며 실제 인프라 설정 점검을 완료했다는 뜻은 아니다.

**실제 단일 JVM WebSocket 메시지 전달 검증은 미실시**다. 기존 Controller의 mock
SimpMessagingTemplate 호출·STOMP 인가/H2 회귀는 실제 socket/broker 전달 성공의 증거가 아니다.
배포 전 한 JVM에 실제 두 클라이언트를 연결해 같은 방을 구독하고 양방향 SEND→수신,
익명 마스킹·다른 방 격리·나가기/정지/토큰 만료 후 차단, 재시작/재접속·REST 복구를 확인한다.
이번 문서 작업에서는 서버·클라이언트를 실행하거나 DB에 접속하지 않았다.

향후 다중 JVM/자동 확장/구·신 JVM 동시 운영이 필요해지면 이 위험 수용의 적용 범위를 벗어난다.
Simple Broker 사이 중계는 여전히 미구현이며 공유 DB·sticky session·15초 polling으로 대체할 수 없다.
그때 별도 설계로 공유 중계, 커밋 이후 발행·장애 복구/중복·순서 정책, 기존 JWT/참여 인가·익명성
보존을 검토하고 실제 두 JVM의 양방향 전달·권한 변경·장애/재연결 검증을 수행한다.
공유 STOMP relay·앱 fan-out 등의 후보는 [채팅 배포·문서 점검 보고서](CHAT_DEPLOYMENT_API_VALIDATION.md)를
참고하되 이번에는 선택·도입하지 않는다. 문제 2 위험 수용과 별개로 실제 단일 JVM 전달과
브라우저 검증은 남아 있다. [최종 MySQL 재검증](CHAT_MYSQL_FINAL_REVALIDATION.md)에서
인덱스 롤백/재적용·데이터 보존은 통과했고 조회 성능도 크게 개선됐지만 방별 정렬/반복 평가가
남아 전체 운영 배포 승인으로 해석하지 않는다.

## 커뮤니티 9번 이미지 수명주기 보완 (2026-10-19)

- `/api/post-images/{filename}`은 DB 연결 및 ACTIVE 부모를 확인하는 바이너리 Controller가 처리한다.
  업로드 폴더 직접 공개를 제거한다. 개별 제거는 커밋 후 영속 작업으로 정리하고 실패 시 재시도한다.
  사용자·관리자·신고의 게시글 전체 삭제는 파일/연결을 보존하며 공개 접근을 차단한다.
- 배포 전에 [수동 마이그레이션](migrations/20261019_post_image_cleanup.md)의 신규 작업 테이블과
  URL 인덱스를 검토·적용해야 한다. 이 작업에서 운영 DB나 업로드 파일을 변경하지 않았다.
  기본/prod validate 및 SQL 초기화 금지는 유지한다. 과거 고아 파일을 자동 정리하지 않는다.
- 모든 구버전 정적 제공 노드와 프록시 alias를 제거하고 CDN 캐시를 무효화한다. 새 응답은 no-store다.
  과거 브라우저 사본과 진행 중 응답은 회수할 수 없다. 관리자 전용 보존 이미지 열람 API는 없다.
- 이미지 디렉터리는 영속 비공개 저장소여야 한다. 같은 DB의 모든 작업자는 같은 파일을 볼 수 있어야 한다.
  저장소가 노드별로 분리되면 다른 노드의 작업을 안전하게 처리할 수 없다. 저장소에는 Docker/compose/
  프록시 구성 파일이 없어 실제 볼륨, 재시작, 백업, 권한은 별도 검증한다.
- 기본 60초 간격/100개 배치 재시도와 1분 실패 지연을 사용한다. test에서는 스케줄을 비활성화하고,
  Gradle 테스트는 임시 기본 이미지 저장 경로를 강제한다.
  작업 적체와 DB 연결 풀·파일 I/O 지연을 감시한다. 실제 MySQL/다중 JVM/공유 볼륨은 미검증이다.
  [파일별 변경·검증·제한](COMMUNITY_IMAGE_LIFECYCLE.md)을 함께 확인한다.

## 커뮤니티 7·8번 목록 제한·조회 성능 보완 (2026-10-19)

- 공개·검색·내 활동과 관리자 게시글·댓글·신고 목록은 size=1~100, 기본 6이다.
  프론트의 기존 size=6은 유지한다. 관리자 회원·일정 API는 이번 변경 범위가 아니다.
- 목록 및 상세 작성자 프로필과 목록 집계는 페이지/작성자 ID로 일괄 조회한다.
  컬렉션 fetch join, 메모리 페이지네이션, 스키마·인덱스·자동 마이그레이션 변경은 없다.
- 상세의 선행 읽기 후 ACTIVE 조건 UPDATE로 조회수·수정 시각만 갱신한다.
  조회수와 익명 이력 초기화는 하나의 기존 트랜잭션이며 사후 실패 시 함께 롤백한다.
  DTO 스냅샷과 이 요청의 최신 views를 구분하며 동시 수정 전 내용이 응답될 수 있다.
- 기존 계정 → 게시글 → 댓글 쓰기 잠금, 익명 상태 고정, 이미지 제한·롤백은 유지한다.
  신규 프로필 일괄 조회와 원자 증가를 포함해 모든 서버에 적용한다.
- 실제 MySQL/InnoDB RR, 실행계획·인덱스, 100개 IN 조건, 잠금 대기·교착 및 다중 JVM은
  격리 환경에서 추가 검증한다. 운영·공유 DB로 검증하거나 데이터를 자동 변경하지 않는다.
  상세 댓글의 전체 반환과 과거 익명 번호 최초 초기화 비용은 남아 있다.
  [실측·일관성·검증 안내](COMMUNITY_READ_PERFORMANCE.md)를 함께 확인한다.

## 커뮤니티 3번 익명 게시글 응답 보완 (2026-10-19)

- 익명 게시글 author.id는 null이며 게시글 목록·상세에 mine을 추가한다.
  COMMUNITY POST 채팅은 targetUserId 없이 sourcePostId로 작성자를 조회한다.
  nullable ID와 mine을 지원하는 프론트·백엔드를 함께 배포한다.
- 스키마·운영 설정·기존 잠금 변경과 자동 데이터 처리는 없다.
  기존 익명 방 키의 적용 여부는 기존 수동 마이그레이션 절차를 확인한다.
- 추가 확정 정책: 작성 후 일반 ↔ 익명 전환을 양방향 금지한다. 수정의 anonymous는
  생략/null이면 유지, 동일 값은 허용, 다른 값은 400 VALIDATION_ERROR다.
  새 프론트는 수정 화면에서 선택을 잠그고 해당 필드를 보내지 않는다. 구버전 프론트의
  전환 요청은 서버에서 거부한다. 구버전 수정 요청·진행 중 트랜잭션을 종료한 뒤
  모든 서버에 적용해야 구버전의 변경 경로가 남지 않는다.
- 과거 전환된 게시글·채팅방은 소급 수정하지 않는다. 기존 공개 신원과 출처의 연결 가능성은
  잔여 위험으로 남긴다. [익명성 검증 안내](COMMUNITY_POST_PRIVACY.md)를 함께 확인한다.

## 커뮤니티 2번 계정 동시성 보완 (2026-10-19)

- 일반 사용자 쓰기는 공통 USER 잠금 → 필요한 게시글 → 댓글 순서를 사용한다.
  관리자 정지·WITHDRAWN 전환은 기존 대상 USER 잠금을 공유한다. 스키마 변경은 없다.
- 같은 사용자의 서로 다른 게시글 쓰기도 직렬화된다. 이미지 I/O를 포함한 잠금 유지 시간과
  기존 5초 잠금 힌트의 DB 지원 여부를 격리 환경에서 확인한다.
- 구버전 쓰기와 진행 중인 트랜잭션을 종료하고 모든 인스턴스를 같은 잠금 규칙으로 전환한다.
- MySQL·다중 JVM 검증은 미실행이다. InnoDB RR, FK/인덱스 잠금, 관리자·신고 처리,
  익명 이력 초기화와의 교착/timeout 및 롤백 검증은 배포 전 필요하다.
- 경로 감사·테스트 범위는 [계정 동시성 안내](COMMUNITY_ACCOUNT_CONCURRENCY.md)를 따른다.

## 커뮤니티 4·5번 동시성 보완 (2026-10-19)

- 댓글/대댓글 작성과 일반·관리자·신고 댓글 삭제도 게시글 → 댓글 잠금을 사용한다.
  부모 댓글 삭제는 활성 대댓글도 소프트 삭제한다. 기존 데이터의 일괄 변경은 실행하지 않는다.
- 이미지 수정은 1번의 게시글 잠금과 최대 5장 계약을 유지하고, 이미지 목록은 잠금 읽기와
  refresh로 조회한다. 파일 저장 후 트랜잭션 실패 시 기존 신규 파일 롤백 정리를 사용한다.
  당시 제외했던 연결 제거 파일/URL 잔존은 위 9번 보완에서 처리한다.
- 스키마·설정·API 형식 변경은 없다. 구버전 댓글 작성/삭제 경로와 진행 중 트랜잭션을
  종료한 뒤 전체 인스턴스에 적용해야 잠금 규칙이 일치한다.
- H2 READ COMMITTED에서 최종 관계/이미지 정합성과 실제 파일 롤백을 검사한다. H2 RR은
  변경된 행의 충돌 롤백만 검증한다. 오래된 H2 RR 스냅샷 이후 삽입 행의 목록 조회는 InnoDB의
  current read와 다르므로 그 성공 사례를 H2로 입증하지 않는다. 실제 격리 MySQL RR·FK/범위
  잠금·교착/timeout·다중 JVM 검증은 미실행이며 배포 전 필요하다.
- [4·5번 원인·검증 안내](COMMUNITY_CREATION_IMAGE_CONCURRENCY.md)를 함께 확인한다.

## 커뮤니티 1번 동시성 보완 (2026-10-19)

- 스키마·운영 설정 변경과 자동 마이그레이션은 없다. 기존 `ddl-auto=validate`를 유지한다.
- 같은 게시글/댓글의 수정·삭제와 조회수 처리는 DB 행 잠금으로 직렬화한다. 전체 삭제는
  게시글 → 댓글 ID 순서로 잠근다. 기존 서버가 혼재하면 잠금 없는 수정 경로가 남으므로
  구버전의 커뮤니티 쓰기 요청과 진행 중인 트랜잭션을 종료한 뒤 전 인스턴스에 적용해야 한다.
- H2의 정상 잠금 대기와 REPEATABLE READ 충돌 롤백을 구분해 검증한다. 실제 MySQL/InnoDB의
  REPEATABLE READ, 잠금 대기/timeout, FK·실행계획 및 다중 JVM 검증은 별도 격리 DB에서 필요하다.
  운영·공유 DB로 검증하지 않는다. 이번 작업은 실제 MySQL 또는 다중 JVM 검증을 수행하지 않았다.
- 자세한 원인·잠금 순서·검증 시나리오는 [동시성 검증 안내](COMMUNITY_CONCURRENCY.md)를 따른다.

2026-10-18 공통 동시성 보완: 관심 프로필 등록/취소, 채팅 설정, 신고 생성도 기존
User 계정 잠금을 첫 DB 읽기로 사용한다. 기존 API·Entity·DDL은 변경하지 않는다.
STOMP SEND/SUBSCRIBE와 기존 구독의 실제 전달 직전에 인증/활성 계정/참여 권한을
재검증한다. 전달 검증의 DB 장애도 차단하며 세션 종료 시 메모리의 토큰 참조를 정리한다.
JVM 간 메시지 중계를 제공하는 broker relay는 추가하지 않았다. 여러 서버에 나뉜
채팅 연결의 메시지 전달 정책/인프라는 4단계 통합 검증에서 별도로 결정·검증해야 한다.
로컬 격리 MySQL·두 실제 JVM의 검증 방법은
[새 검증 도구](tools/matching-mysql-multi-instance/RUNBOOK.md), 결과는
[1~3단계 종합 보고서](STAGES_1_3_VALIDATION.md)를 따른다. 운영 배포 승인이 아니다.

Matching 보완(2026-10-17): 사용자별 AI 요청량은 기존 auth_limit_bucket의 별도 SHA-256 키를 사용한다. 신규 DDL은 없으며 모든 인스턴스가 같은 DB와 AI_ANALYSIS_MAX_REQUESTS(12), AI_ANALYSIS_WINDOW_SECONDS(60)를 사용해야 한다. 실패 요청도 승인한 quota를 환불하지 않고 DB 장애 시 제한을 우회하지 않는다. AI_ANALYSIS_MAX_CONCURRENT(2)는 인스턴스별 semaphore 상한이다. 여러 인스턴스의 합산 동시 상한은 Gateway/ingress에서 별도 설정해야 한다. 새 인프라는 추가하지 않았다. 실제 Gateway는 테스트에서 호출하지 않으므로 운영 모델·접근·시간 초과·요금 설정은 별도 확인한다.

목록 최종 조회는 명시적인 REPEATABLE READ 스냅샷을 사용한다. 점수 준비 이전의 ID 페이지를 그대로 응답하지 않으며 공개 조건·페이지/count·프로필·설문을 최종 읽기 트랜잭션에서 조회한다. 스냅샷 이후의 정지/탈퇴 커밋과 HTTP 전송까지의 완전한 원자성은 보장하지 않는다. 격리 MySQL 검증은 [Survey/Matching 실행 안내](tools/survey-mysql-concurrency/RUNBOOK.md)의 -IncludeMatching을 사용한다. 실제 운영 DDL/실행계획·부하 검증을 대체하지 않는다.

이 문서는 user 안정화·auth 보호 및 Calendar 보완의 배포 조건이다. 다른 도메인의 배포 적합성이나 실제 운영 성공을 보장하지 않는다.

## Calendar 변경의 운영 확인

- Calendar의 DATE/TIME은 한국 현지 일정 값이며, DATETIME(`SCHEDULE_CRAWL_RUN.started_at/completed_at`, `CALENDAR_EVENT.collected_at`, `CALENDAR_CATEGORY.created_at`)은 서울 현지 벽시계 값이다. API 타입과 nullable 시간 계약을 유지한다. 이 필드만 Hibernate 직접 Java-time JDBC 매핑을 사용하므로 JVM 기본 시간대나 JDBC `connectionTimeZone`/MySQL session `time_zone`에 의해 값이 이동하지 않는다. 크롤링·카테고리 생성 시각은 명시적으로 Asia/Seoul에서 생성한다. TIMESTAMP/Instant로의 전환이나 다른 도메인의 시간 정책 변경은 포함하지 않는다.
- 업그레이드 전에 기존 Calendar DATETIME의 작성 환경·배포 시점·원시값과 당시 로그를 대조한다. 이전 Timestamp 매핑에서 서울 JVM/UTC JDBC로 쓴 값은 서울 값보다 9시간 작게 저장됐을 수 있으며, 새 매핑은 그 원시값을 그대로 읽는다. 서울 JDBC로 쓴 값과 섞였을 수 있어 일괄 +9시간 UPDATE는 금지한다. 백업 후 작성 환경이 입증된 실행 ID/행 범위만 변환 계획을 검토하고 started/completed/collected 시각의 연관성과 날짜 경계를 검증한다. 입증되지 않은 과거 행의 해석은 배포 전 보류 사항이다. 자동 데이터 변환은 없다.
- `migrations/20261016_calendar_time_indexes.sql`의 비고유 복합 인덱스 2개는 운영자가 기존 인덱스/Collation/중복·행 수·DDL 잠금 영향을 확인한 뒤 수동 적용한다. 기본/prod validate는 인덱스를 생성하지 않으며 Hibernate validate만으로 인덱스 존재를 보장할 수 없다. 동일 데이터의 EXPLAIN ANALYZE와 실제 인덱스 정의를 별도로 대조한다. 추가 인덱스의 INSERT/UPDATE 유지 비용도 대표 수집량에서 확인한다.
- 학교 수집은 RUNNING 시작 이력을 먼저 독립 커밋한다. 일정과 COMPLETED 이력은 같은 트랜잭션에서 커밋하며, 실패하면 일정 전체 롤백 후 FAILED/0건을 독립 커밋한다. 이미 COMPLETED인 실행은 커밋 응답 유실을 이유로 FAILED로 덮어쓰지 않는다. 시작 이력 저장이 실패하면 외부 요청을 시작하지 않는다.
- 프로세스 강제 종료·DB 전체 장애로 RUNNING이 남을 수 있다. 실행 ID별 소유 인스턴스/작업 종료와 DB 트랜잭션 종료를 운영 로그·프로세스·DB에서 확인하기 전에는 시간 경과나 다른 인스턴스 재시작만으로 실패 처리하지 않는다. 운영자가 해당 작업 종료를 확인한 뒤 백업·변경 검토를 거쳐 RUNNING인 그 ID만 FAILED/0건 및 안전한 중단 분류로 수동 정리한다. 애플리케이션은 운영 데이터 복구 SQL을 자동 실행하지 않는다. 새 실행은 별도 ID로 기록한다. 자동 만료·heartbeat 정책은 이번 범위에서 도입하지 않았다.
- 실패 이력 저장 자체가 DB 장애로 실패하면 원래 오류를 유지하고 runId·오류 유형만 로그에 남긴다. DB 복구 후 미종료 이력을 확인해야 한다. 외부 예외 원문을 이력·스케줄러 로그에 저장하지 않는다.
- 카테고리/설정과 사용자 일정 쓰기는 기존 User 공통 계정 잠금을 첫 DB 조회로 사용한다. USER → Calendar 행 순서이며 잠금 후 기존 설정과 이름을 검증한다. 카테고리 삭제와 일정 등록도 같은 사용자 잠금을 사용한다. 이름의 기존 Java 문자열 비교와 현재 UNIQUE(user_id, category_id)를 유지하며 새 스키마/Collation 변경은 없다.
- 기본 자동 검증은 test 프로필·H2 메모리 DB와 고정 HTML fixture를 사용한다. 별도 `tools/calendar-mysql-concurrency/run.ps1 -Stage All`은 새 Calendar 격리 DB에서 실제 MySQL DDL·REPEATABLE READ·InnoDB 잠금/timeout·UNIQUE·카테고리 삭제 경합·크롤링 원자적 커밋/롤백과 실행계획을 검사한다. 서울 JVM/UTC JDBC, 서울 JVM/서울 JDBC, UTC JVM/UTC JDBC는 각각 별도 JVM에서 검사한다. 짧은 2초 잠금 대기 한도를 사용하는 검증이므로 빌드 등 동시 부하 중 timeout 발생도 실패로 기록하고 독립 실행 결과와 구분한다. 실제 서비스 다중 JVM/HTTP 부하와 커밋 응답 유실은 추가 격리 검증 대상이다. 실제 학교 사이트 호출이나 운영/개발 DB 변경으로 대체하지 않는다.

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

채팅 상태 동기화/메시지·검색 추가 조회/Unicode는
[검증 보고](CHAT_SYNC_PAGINATION_UNICODE_VALIDATION.md)를 따른다. 새 프론트 전환 전에
메시지 beforeId/afterId와 POST /api/chat/rooms/{roomId}/messages/read-status를 제공하는 서버를 배포한다.
기존 offset·STOMP 계약과 DB 스키마는 유지한다. 보이는 탭의 15초 polling은 응답/브라우저 타이머에 따른 반영 지연·추가 REST 부하가 있으므로
대표 사용량과 실제 브라우저/프록시에서 확인한다. 이 보고서 작성 당시 MySQL·브라우저 E2E·다중 JVM은 미실행이었다.
후속 MySQL 실행 결과와 현재 미검증 범위는 아래 최종 재검증 기록을 따른다.

채팅 조회 제한/목록 최적화는 [검증 보고](CHAT_QUERY_PERFORMANCE_VALIDATION.md)와
[조회 인덱스 수동 절차](migrations/20261019_chat_query_indexes.md)를 따른다.
메시지/방 목록은 기본 50, 최대 100이며 목록 `data`가 배열에서 페이지 객체로 변경된다.
관련 프론트 및 다른 클라이언트의 content/last 처리를 함께 배포한다. 단건 GET /api/chat/rooms/{roomId}는
페이지 밖 선택 방과 읽음 수 갱신에 사용한다. 이번 조회 작업은 Entity 컬럼/제약·DDL 설정을 변경하지 않는다.
H2 SQL 수 측정은 MySQL의 최신 메시지 correlated subquery/정렬/집계 비용이나 운영 지연 보장이 아니다.
초기 조회 보고서의 MySQL 실행계획·인덱스 DDL·동시성 미실행 기록은 당시 점검 범위다.

채팅 참여 상태 덮어쓰기와 읽음 정합성 보완에는 CHAT_MESSAGE.is_read 추가 및 기존 표시 결과 전환이 필요하다.
[수동 절차](migrations/20261019_chat_message_receipts.md)를 먼저 따른다. 자동 실행하지 않는다.
읽음 API는 실제 수신한 messageIds 본문이 필요하므로 구버전 쓰기를 drain하고 서버·관련 프론트를 함께 배포한다.
H2 통과와 실제 InnoDB 검증은 구분하며, 어느 쪽도 운영 DB 적용·배포 성공을 보장하지 않는다.

2026-10-20 후속 [최종 MySQL 재검증](CHAT_MYSQL_FINAL_REVALIDATION.md)의 현재 결과는 다음과 같다.

- **격리 MySQL 8.0.44 / InnoDB:** 실제 SQL·EXPLAIN/EXPLAIN ANALYZE를 비교하고 수정 조회의
  1/10/60/100개 방 동작을 확인했다. 기능·동시성 및 기존 데이터 보존 회귀는 19/19 PASS,
  인덱스 FK 지원 복원·롤백·부분 실패 복구·재적용은 7/7 PASS이며 FK·기존 행 해시 보존을 확인했다.
- **실패 이력·잔여 비용:** 과거 7 FAIL과 별도로 최종 실행은 47 case / 46 PASS / 1 FAIL이다.
  FAIL은 수정 전 anti-join의 100개 방 조회가 3초 제한으로 중단된 3024이며 수정 조회 100개 방은 통과했다.
  같은 데이터의 60개 방 서비스 중앙값은 1,904.2→54.4ms로 개선됐지만 방별 정렬·scalar 반복 평가가 남아
  문제 10은 부분 완료다. 실행계획 전체 최적화나 전체 무실패 통합 검증으로 판정하지 않는다.
- **운영 미검증:** 기존 `kkirikkiri`·`kkiri_kkiri`의 실제 DDL 적용 여부와 운영 데이터 전환,
  대형 DDL 잠금·쓰기 비용·복제 영향·백업 전체 복구, 큰 offset·p95/p99·장시간 부하는 확인하지 않았다.
  실제 단일 JVM WebSocket 양방향 전달·프록시·브라우저 E2E도 미검증이며 운영 배포는 보류다.

격리 DB 통과를 운영 배포 승인으로 해석하지 않는다. 현재 단일 JVM 배포에서는 위 실제 전달·운영 조건을
확인하며, 다중 JVM 검증은 향후 확장 시 수행할 항목으로 현재 배포의 선행 조건이 아니다.

user 안정화에는 Entity 스키마 변경이 없었다. auth 보호에는 신규 AUTH_LIMIT_BUCKET 테이블만 추가했으며,
[auth 수동 마이그레이션](migrations/20261014_auth_limits.md)을 먼저 따른다. 운영에서 자동 적용하지 않는다.

20261015 이메일 동치 식별·인증 조회 인덱스 수정은
[별도 수동 마이그레이션](migrations/20261015_auth_email_identity_indexes.md)을 추가로 따른다.
AUTH_EMAIL_IDENTITY와 EMAIL_VERIFICATION 인덱스 2개가 필요하며 기존 이메일 Collation/입력 정책은 유지한다.
운영 적용은 이번 테스트 DB 검증에 포함하지 않는다.

## Auth 보호 배포 조건

- Redis를 추가하지 않는다. 모든 인스턴스가 같은 MySQL primary/schema의 AUTH_LIMIT_BUCKET을 사용한다.
  PK upsert 후 정렬된 행 잠금으로 quota 검사/예약/완료를 갱신한다. denied 요청의 신규 bucket은 롤백한다.
  JDBC 갱신은 독립 REQUIRES_NEW/READ_COMMITTED 트랜잭션이며 BCrypt/SMTP 중에는 AUTH_LIMIT_BUCKET 잠금을 잡지 않는다.
  제한 DB 장애는 검증을 우회하지 않고 오류로 종료한다. 업무 트랜잭션이 실패해도 실패/요청 기록은 남는다.
- 기존 회원의 로그인·재설정은 DB에서 조회한 user_id를 해시해 제한을 공유한다. 가입 전 인증과 비등록 계정은
  각 원본 이메일 컬럼의 비교 규칙을 그대로 복사한 AUTH_EMAIL_IDENTITY UNIQUE 식별자로 제한한다.
  두 원본 컬럼의 Collation은 서로 달라도 된다. MySQL 시작 시 해당 식별 컬럼과의 타입/문자셋/Collation 불일치는 거절한다.
  이 테이블은 이메일 원문을 저장하므로 DB 접근/백업에서도 개인정보로 취급한다. API/로그/bucket key에는 원문을 내보내지 않는다.
  자동 정리는 없고, 활성 제한이 남아 있는 식별 행을 삭제하거나 배포 시 초기화하지 않는다.
- 제한 키 체계가 바뀌므로 이전 이메일 문자열 기반 bucket을 새 식별자 bucket으로 자동 이전하지 않는다.
  혼합 버전 요청을 차단하고 배포 전환 동안 외부 제한을 유지해야 한다. 기록 삭제나 자동 reset으로 전환하지 않는다.
- 모든 인스턴스의 제한 설정과 DB UTC 세션을 통일한다. DB 시각으로 window/block을 판정한다.
  제한 저장은 정렬된 PK bulk upsert/잠금 조회로 SQL 왕복을 줄인다. 기존 이메일 식별자는 변경 없이 읽고,
  최초 생성만 UNIQUE upsert하며 식별 생성과 quota 예약의 원자적 롤백을 유지한다.
  JDBC 전용 제한 트랜잭션은 JDBC transaction manager를 사용한다. 먼저 기존 JPA 업무 자원을 suspend하여
  독립 JDBC commit이 외부 업무를 커밋하지 않게 하며 종료 후 원래 업무를 resume한다.
  이메일 확인의 registered 값은 같은 제한 예약에서 조회한 회원 식별 결과를 사용하여 중복 JPA 조회/대여를 피한다.
  NOT_SUPPORTED Auth 진입점에서 필요한 JPA 조회는 짧은 명시적 읽기 트랜잭션으로 종료한다.
  비트랜잭션 JPA 조회가 연결을 보유한 채 다음 REQUIRES_NEW 연결을 기다리는 풀 잠금을 피하고,
  BCrypt/다음 quota 예약/메일 발송 전에 조회 연결을 반환한다. 풀 크기나 대여/행 잠금 timeout을 늘리는 해결책이 아니다.
  `auth.database-retry`는 이 JDBC 제한 트랜잭션의 MySQL 1213/1205에만 적용한다.
  기본값은 `max-attempts=3`(최초 포함), `initial-delay-millis=10`, `max-delay-millis=100`, `jitter-millis=10`이다.
  각 실패 트랜잭션을 완전히 종료한 뒤 연결을 보유하지 않고 지수 backoff+jitter 후 새 REQUIRES_NEW 트랜잭션을 시작한다.
  최대 시도 횟수는 1~5다. 연결 실패·알 수 없는 commit 결과·업무/SMTP 예외는 자동 재시도하지 않는다.
  재시도 소진 시 기존 DB 오류 처리를 유지하며 오류를 한도 초과 429로 바꾸지 않는다.
  위 값을 Spring property 또는 JVM system property로 재정의할 수 있으며 모든 인스턴스에서 동일하게 설정한다.
  MySQL Connector/J 설정 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`와 실제
  `@@session.time_zone`을 확인한다. 기존 DATETIME/시간대에 미치는 영향도 격리 환경에서 대조한다.
- server.forward-headers-strategy=NONE을 유지한다. 기본은 servlet remote address만 사용한다.
  AUTH_TRUSTED_PROXIES에 실제 ingress/LB의 좁은 IP/CIDR만 지정하고, ingress가 X-Forwarded-For를
  덮어쓰거나 실제 peer를 append하는지 확인한다. 오른쪽부터 신뢰 체인을 제거한 첫 비신뢰 주소가 client IP다.
  0.0.0.0/0·전체 사용자 네트워크를 신뢰하지 않는다. 헤더가 잘못되면 직접 peer로 제한한다.
- 구버전은 이 제한을 수행하지 않는다. 혼합 버전에서 공개 auth 트래픽을 구버전으로 보내지 않는다.
  신규 테이블이 없으면 validate가 실패한다. update나 테스트 설정으로 우회하지 않는다.
- 요청 예약과 SMTP/DB 커밋은 원자적인 외부 전송을 보장하지 않는다. SMTP가 수락한 뒤 응답/DB 커밋이 실패하면
  메일은 도착해도 토큰이 롤백될 수 있다. 자동 재발송·quota 환불 없이 cooldown 후 새 요청으로 복구한다.
  실패·제한 시 토큰 저장이 누적되지 않으며 성공 발급은 요청/전체 quota로 상한을 갖는다.
- 진행 중인 로그인/인증 검증을 실패 상한에 예약한다. 성공은 이메일 실패만 초기화한다.
  비정상 종료의 예약은 실패 window가 끝나면 복구되므로 최대 로그인 600초·인증 300초 동안 접근이 제한될 수 있다.
  60초 로그인 차단은 고의 계정 잠금 영향을 짧게 제한한다. 분산 공격에 의한 반복 방해를 완전히 제거하는 CAPTCHA/
  위험 기반 인증은 추가하지 않았으며 운영 모니터링/프록시 보호가 필요하다.
  인증번호 발송 업무는 이미 예약에서 커밋한 AUTH_EMAIL_IDENTITY의 기존 UNIQUE 행을 먼저 잠근다.
  첫 consistent read 전에 이 대상별 mutex를 획득하고, 발송의 최신 이력 조회는 이 mutex 안에서 수행해
  없는 이력의 FOR UPDATE 범위/GAP 잠금 후 INSERT 경합을 피한다. 검증/토큰 소비의 행 잠금은 유지한다.
  SMTP를 포함한 이 업무 전체는 재시도하지 않는다. 새 테이블/인덱스/격리 수준 변경은 필요하지 않다.
- Auth 메일 지표 `auth.mail.delivery`의 kind/result/reason은 고정 값이며 recipient 등의 고카디널리티 태그가 없다.
  기본 SimpleMeterRegistry는 인스턴스별 메모리 지표다(보안 제한 저장소와 무관).
  운영 집계/알림은 기존 관측 시스템의 MeterRegistry/exporter 또는 안전한 구조화 로그 수집에 연결해야 한다.
  지표 성공은 SMTP send 반환을 뜻하며 실제 수신함 도착을 보장하지 않는다. auth 응답 X-Request-ID를 로그와 연결한다.
- 자동 이력 삭제는 추가하지 않았다. 제한/만료 토큰 이력의 보존·배치 정리는 마이그레이션 문서를 따른다.

### Auth 기본 제한과 설정

아래 설정은 application.properties의 환경변수 placeholder로 재정의할 수 있다. 양수만 허용하며
인스턴스마다 값을 다르게 지정하지 않는다. fixed window quota이며 제한 중 요청은 차단 종료 시각을 연장하지 않는다.

| 기능 | 이메일 / IP 요청 한도 | 실패 한도 및 차단 | 설정 환경변수 |
| --- | --- | --- | --- |
| 로그인 | 20 / 120회, 60초 | 10 / 30회, 600초 누적; 60초 차단 | AUTH_LOGIN_EMAIL_REQUESTS, AUTH_LOGIN_IP_REQUESTS, AUTH_LOGIN_REQUEST_WINDOW_SECONDS, AUTH_LOGIN_EMAIL_FAILURES, AUTH_LOGIN_IP_FAILURES, AUTH_LOGIN_FAILURE_WINDOW_SECONDS, AUTH_LOGIN_BLOCK_SECONDS |
| 인증 검증 | 10 / 60회, 60초 | 5 / 20회, 300초 누적; 300초 차단 | AUTH_VERIFY_EMAIL_REQUESTS, AUTH_VERIFY_IP_REQUESTS, AUTH_VERIFY_REQUEST_WINDOW_SECONDS, AUTH_VERIFY_EMAIL_FAILURES, AUTH_VERIFY_IP_FAILURES, AUTH_VERIFY_FAILURE_WINDOW_SECONDS, AUTH_VERIFY_BLOCK_SECONDS |
| 재설정 요청 | 5 / 20회, 3600초 | 이메일 cooldown 60초 | AUTH_RESET_EMAIL_REQUESTS, AUTH_RESET_IP_REQUESTS, AUTH_RESET_REQUEST_WINDOW_SECONDS, AUTH_RESET_COOLDOWN_SECONDS |
| 인증번호 발송 | 5 / 20회, 3600초 | 이메일 cooldown 60초 | AUTH_SEND_EMAIL_REQUESTS, AUTH_SEND_IP_REQUESTS, AUTH_SEND_REQUEST_WINDOW_SECONDS, EMAIL_RESEND_INTERVAL_SECONDS |
| 전체 auth 메일 | 100회, 3600초 | 실패해도 quota 유지 | AUTH_MAIL_MAX, AUTH_MAIL_WINDOW_SECONDS |

인증 5회는 6자리 추측 가능성을 줄이고, 로그인 10회/짧은 60초 차단은 입력 실수와 계정 잠금 공격 영향을 함께 고려했다.
IP 한도는 학교/NAT 공유 환경을 고려해 이메일보다 높다. 메일 100회/시간은 초기 보수적 값이며 실제 사용자 수·SMTP quota·
신뢰 프록시와 지연 측정 후 운영자가 조정한다. 값을 높이는 것만으로 공격 대응을 대신하지 않는다.

### 추가 검증

H2 테스트는 두 독립 limiter 객체가 같은 DB를 쓰는 원자성·예약·복구·업무 롤백을 확인한다.
두 실제 프로세스의 MySQL PK 최초 경합, InnoDB 잠금/교착/타임아웃, UTC 시각, table DDL/권한,
DB/SMTP 장애 및 응답 유실, connection pool 포화, ingress IP 체인은 운영과 동일한 격리 환경에서 별도로 검증한다.
실제 SMTP/운영 DB에 테스트를 보내지 않는다. 실제 운영 확인 없이 배포 성공을 확정하지 않는다.

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
- 계정 쓰기는 공통 `findByIdForAccountUpdate`를 변경 전에 호출한다. 초기 참조는 DB snapshot을 만들지 않고, 첫 `refresh(PESSIMISTIC_WRITE)`가 잠금과 최신 값 재적재를 함께 수행한다. 같은 트랜잭션의 잠금 재호출은 미반영 변경을 유지한다. Hibernate/DB 변경 시 실제 SQL과 REPEATABLE-READ 회귀를 [User MySQL 도구](tools/user-mysql-concurrency/RUNBOOK.md)로 확인한다. 격리 MySQL 회귀 통과는 운영 데이터·DDL·실제 배포 검증을 대신하지 않는다.
- 매칭 갱신은 최대 2개 작업 스레드·100개 대기 작업으로 제한된다. 큐 포화·계산 실패·프로세스 종료 후에는 기존 matching 조회가 필요한 결과를 다시 계산한다. 대표 회원 수에서 저장 응답 시간, 큐 포화 로그, 배치당 잠금 시간, 추천 조회 시간을 측정한다.
- 운영 API 주소·CORS origin·HTTPS/JWT 키·SMTP 연결·시간대도 실제 배포 환경에서 확인한다. 비밀번호 변경 후 JWT/기존 재설정 토큰 폐기 정책은 이번 변경에서 새로 도입하지 않았다.
