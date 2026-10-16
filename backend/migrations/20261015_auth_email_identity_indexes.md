# 이메일 동치 식별 및 인증 조회 인덱스 수동 마이그레이션

대상: [20261015_auth_email_identity_indexes.sql](20261015_auth_email_identity_indexes.sql).
20261014 공유 제한 테이블 마이그레이션 이후에 검토·수동 적용한다. Spring SQL 초기화로 실행하지 않는다.
이번 실행의 대상은 Docker `127.0.0.1:3307/kkiri_auth_test`뿐이며 운영/기존 개발 DB에 적용하지 않았다.

## 변경과 원인

MySQL이 같은 계정으로 조회하는 악센트/대소문자/Unicode 변형을 Java lower-case+문자열 해시가
서로 다른 제한 대상으로 처리하던 문제를 수정한다. 기존 회원의 로그인/재설정은 user_id를 사용한다.
회원이 없는 계정과 가입 전 인증은 새 AUTH_EMAIL_IDENTITY의 UNIQUE 동치 식별자를 사용한다.
임의 Unicode folding이나 입력 정책 변경을 하지 않으며, DB Collation을 변경하지 않는다.

식별 테이블의 두 이메일 컬럼은 각 원본의 실제 타입/문자셋/Collation을 따로 복사한다.
MySQL 비교 규칙에는 대소문자/악센트/Unicode 동치 및 PAD 정책 차이가 있으므로 기본 schema Collation으로
가정하거나 Java Normalizer로 대체하지 않는다. 두 컬럼의 Collation이 다르면 해당 도메인의 식별자도 별도로 결정된다.
키에는 목적과 `user:<id>` 또는 이메일 동치 식별 ID의 SHA-256만 저장한다.
이메일 원문은 새 식별 테이블에 저장되므로 개인정보로 취급하고 API/로그로 노출하지 않는다.

EMAIL_VERIFICATION의 최신 인증/토큰 소비 조회는 비관적 잠금을 유지하고 아래 인덱스를 추가한다.

- `idx_email_verification_latest(email, purpose, created_at DESC, verification_id DESC)`
- `idx_email_verification_token(email, purpose, verification_token_hash)`

## 적용 전 확인과 순서

1. 실제 schema/서버 버전/물리 테이블명과 대소문자/InnoDB, 권한, 백업·복구를 확인한다.
   기존 user 및 email_verification의 SHOW CREATE TABLE과 EMAIL 컬럼의 타입/Collation을 보관한다.
   지원 전제는 두 컬럼 모두 VARCHAR(100), MySQL 8/InnoDB다. 다른 타입이면 진행하지 않는다.
2. AUTH_EMAIL_IDENTITY와 같은 이름의 인덱스가 이미 있으면 재실행하지 말고 부분 적용 상태를 조사한다.
   SQL은 IF NOT EXISTS나 DROP으로 기존 객체를 덮어쓰지 않는다. DDL은 암묵적 커밋이며 실패 시 전체 자동 롤백되지 않는다.
3. SQL은 원본 메타데이터의 안전한 식별자만 사용해 CREATE TABLE 문장을 구성한다.
   기존 회원/인증/토큰 데이터 DML, Collation 변경, 자동 시드, 데이터 이전은 없다.
4. 테스트 DB에서는 고정 대상/UUID/권한/빈 상태 검사 도구인
   [run.ps1 -Migrate](../tools/auth-mysql-concurrency/run.ps1)를 사용한다. 기존 8개 빈 테이블 조건에서 한 번만 실행한다.
   이 단계는 기존 CLI Inspect/Baseline/Migrate 절차와 다르며 새 인덱스 적용 후 9개 테이블이 된다.
5. SHOW CREATE, UNIQUE/인덱스 방향/컬럼 순서/문자셋/Collation을 확인하고 실제 Hibernate 잠금 SQL의
   EXPLAIN 및 서로 다른 이메일의 잠금 간섭/동일 이메일 원자성을 검증한다.
6. 애플리케이션은 validate/SQL never로 실행한다. MySQL에서 식별 테이블과 원본 컬럼의 비교 규칙이
   다르면 AuthLimitStore 초기화가 실패한다. 실패를 update로 우회하지 않는다.

기존 제한 bucket은 삭제하지 않는다. 새 key 체계는 이전 문자열 해시 기반의 현재 제한 기록을 자동 승계하지 않는다.
전환 시 공개 Auth 요청을 구버전으로 보내지 않고, 외부 제한과 트래픽 차단/점진 재개로 이 경계를 보완해야 한다.
새 계정 생성으로 비등록 이메일 대상이 user_id 대상으로 전환되는 생명주기 경계도 자동 이력 병합 대상이 아니다.
회원 이메일 변경 API는 이번 범위에 추가하지 않았다.

## 복구와 보존

부분 DDL 실패 시 이미 적용된 객체를 보존하고 SHOW CREATE를 대조한 별도 복구 SQL을 검토한다.
이전 코드로 되돌리더라도 제한 우회가 다시 열리므로 외부 대체 보호 없이 공개 트래픽을 재개하지 않는다.
즉시 테이블/인덱스를 DROP하거나 identity_id를 다시 생성하지 않는다.

새 식별 테이블은 보존 기간/삭제 정책을 임의 확정하지 않았으며 자동 청소 작업이 없다.
활성 bucket의 식별자를 지우면 요청 제한이 초기화되므로 기존 bucket 및 처리 중 요청과 함께 보존을 검토해야 한다.
기존 만료 인증 행 정리 정책과 같은 정책으로 취급해 일괄 삭제하지 않는다.

실제 기존 DB의 비어 있지 않은 데이터/온라인 인덱스 잠금 시간, 운영 Collation 조합, 다중 인스턴스 배포는
이번 격리 DB 동시성 검증과 구분해서 확인한다.
