# Survey 전용 MySQL 경합 검증

Matching까지 검증하려면 `run.ps1 -IncludeMatching`을 사용한다. 기본 12개 Survey 시나리오는 유지하며 실제 Matching 서비스·Repository를 사용하는 9개 동작 시나리오(동시 매칭 생성, 설문 수정/계산 경합, 전체/관심 목록 × 정지/탈퇴, 상위 9명 필터, PK/UNIQUE/FK/롤백, 독립 limiter의 공유 MySQL AI quota)를 추가한다. 필터 사례는 120명을 추가해 실행 시간·실제 인덱스·대표 필터 EXPLAIN을 evidence.json에 남긴다. 대표 EXPLAIN은 Hibernate SQL 원문과 완전히 동일한 실행계획을 주장하지 않는다. H2 테스트의 bounded DTO 조회와 프론트 요청 수 검증은 별도다. 요청자별 점수 준비는 기존대로 후보 수에 비례하며 대규모 운영 부하 결과는 아니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -PrepareOnly -IncludeMatching
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -IncludeMatching
```

기존 컨테이너·schema·계정·UUID·빈 테이블 확인과 advisory lock을 그대로 적용한다. Matching 정리도 해당 실행에서 만든 사용자 ID의 매칭·관심 행에 한정한다. 운영/개발 DB URL 재정의는 허용하지 않는다. 기본 실행의 12개 사례와 선택 Matching 사례의 성공 여부는 evidence.json의 개별 case로 구분한다.

AI quota 검증은 실제 AuthLimitStore와 독립 limiter 2개를 사용한다. 이 JPA 전용 도구의 물리 테이블명은 대문자이며 Boot 기본 매핑은 소문자이므로, 해당 검증의 JDBC adapter에서 bucket 테이블 식별자와 information_schema 테이블명 비교만 맞춘다. 실제 컬럼 타입/Collation 검증·예약 SQL·잠금·DB 시각은 유지하며 DB 객체나 데이터를 변경하여 우회하지 않는다. 이는 단일 JVM의 독립 객체/연결 검증이며 실제 다중 프로세스 검증은 아니다.

기본 Gradle 테스트는 H2만 사용한다. 이 도구는 별도 MySQL 8 InnoDB에서 실제 Survey 서비스,
JPA Repository 및 트랜잭션 프록시를 실행한다. Boot 설정, `.env`, SMTP, 스케줄러를 로드하지 않는다.
관리자 상태 변경은 H2 통합 테스트에서 실제 AdminService로 검증하며, 이 도구에서는 동일한
`findByIdForAccountUpdate`와 `User.changeStatus` 경로를 사용한다.

## 실행

저장소 루트 PowerShell에서 Java 21과 Docker Desktop을 준비한다.

```powershell
cd C:\dev\project\roommate-matching-platform\backend
.\gradlew.bat test bootJar
cd ..
# 컴파일만 수행: Docker 및 DB에 접근하지 않는다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -PrepareOnly
# 최초 1회: 별도 컨테이너와 새 전용 DB 생성. 기존 컨테이너 교체 거부.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1 -Provision
# Docker가 ready for connections 상태가 된 뒤 실행한다.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\backend\tools\survey-mysql-concurrency\run.ps1
```

위 실행 정책 옵션은 해당 PowerShell 프로세스에만 적용되며 시스템 설정을 바꾸지 않는다.
접속 대상은 `kkiri-survey-mysql-test`, `127.0.0.1:3311/kkirisurveytest`, 전용 계정으로 고정된다.
컨테이너 이미지·라벨·포트·서버 UUID·hostname·schema·계정을 확인한다. URL 재정의는 지원하지 않는다.
기존 개발/운영 DB나 다른 검증 컨테이너로 변경하여 실행하지 않는다. 비밀번호는 메모리의
환경변수로만 전달하며 출력하거나 저장하지 않는다.

검증 전에 전용 schema의 모든 테이블이 비었는지 확인하며 데이터가 있으면 쓰기를 거부한다.
테이블이 전혀 없는 새 전용 schema에서만 Hibernate create-only를 사용한다. 이후에는 validate다.
운영 DDL이나 마이그레이션은 변경하지 않는다. 동시 검증 실행은 schema별 advisory lock으로 거절한다.

## 검증 및 기록

12개 시나리오: 두 설문 최초 동시 생성 2개, flush 후 전체 롤백 2개,
두 설문 × SUSPENDED/WITHDRAWN × 상태/설문 선행 커밋 8개.
REPEATABLE READ, 서로 다른 Connection ID, Latch로 커밋 순서를 제어한다.
상태 선행 시에는 먼저 읽은 ACTIVE 객체를 같은 영속성 컨텍스트에 보유한 채 잠금에 대기시켜
최신 상태 재조회와 UNAUTHORIZED를 검증한다. 설문 선행 시에는 상태 변경의 대기를 확인한다.
행 수·전체 응답·하위 테이블·가중치·자기소개·커밋 후 이벤트와 최종 상태를 검증한다.

Connection 획득 5초, JDBC socket 15초, InnoDB lock wait 3초, 트랜잭션 12초,
Latch 8초, Future 15초 제한을 둔다. 교착 상태나 timeout은 실패로 기록하고 자동 재시도하지 않는다.

결과는 `backend/build/survey-mysql-concurrency/run-*/environment.json`과 `evidence.json`에 기록한다.
사례별 Connection ID, 커밋 순서, 성공/실패와 오류 종류를 포함한다. 원문 DB 오류나 자격증명은 기록하지 않는다.
성공한 실행은 `passed=true`이며 검증 12개와 정리 확인이 모두 PASS여야 한다.
컴파일 성공은 MySQL 실행 통과를 의미하지 않는다.

정리는 해당 실행에서 직접 만든 사용자 ID 및 기준정보 ID로만 제한한다. 전체 DELETE, TRUNCATE,
DROP DATABASE, 기존 컨테이너 삭제를 하지 않는다. 비정상 종료 시 남은 데이터 때문에 재실행이
거절될 수 있다. 이 경우 전용 컨테이너의 소유권과 결과를 검토하고 운영자 판단으로 처리한다.
다른 schema의 데이터 삭제로 우회하지 않는다. 이 단일 JVM 검증은 실제 배포의 다중 인스턴스
부하·교착 상태 빈도·운영 DDL/격리 설정 검증을 대신하지 않는다.
