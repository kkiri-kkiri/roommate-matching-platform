# 채팅 조회 인덱스 수동 검토·적용

이 파일과 SQL은 자동 실행하지 않는다. 2026-10-20에는 전용 계정의 인증 거절(1045)로 DB 검증을 보류했다.
추가 인덱스는 조회 지원 후보이며 실제 MySQL 운영 부하 확인이 끝나지 않았다.
기본/prod의 ddl-auto=validate, SQL 초기화 금지를 유지한다. Entity 컬럼/제약은 이번 조회 작업에서 바꾸지 않는다.

후속 실제 검증: [최종 MySQL 재검증](../CHAT_MYSQL_FINAL_REVALIDATION.md)에서 legacy DB의
메시지 FK 지원 복원→후보 3개 DROP→재적용/멱등·부분 실패 복구와 기존 행 해시 보존을 확인했다.
member FK의 기존 복구 인덱스는 재사용했다. 운영의 대형 DDL/복제/백업 전체 복구 검증을 뜻하지 않는다.
최신 메시지 쿼리는 실측 개선됐지만 실제 계획에 방별 Sort와 방당 3회 scalar 평가가 남았다.
아래의 역방향 탐색 설명은 설계 의도이며 실제 1행 역방향 읽기가 확인됐다는 뜻이 아니다.

1. 격리된 MySQL에서 먼저 검증한다. DB 이름/접속 대상/테이블 대소문자와 MySQL 버전,
   `SHOW CREATE TABLE`, `SHOW INDEX`를 읽기 전용으로 확인한다. 운영 데이터/백업을 별도 절차 없이 변경하지 않는다.
2. 이전 [읽음 확인 전환](20261019_chat_message_receipts.md)의 is_read 컬럼과 데이터 전환 완료를 확인한다.
   같은 선두 컬럼을 가진 동등 인덱스가 이미 있으면 중복 생성하지 않는다. FK 보조 인덱스를 임의로 삭제하지 않는다.
3. 대표 데이터(사용자별 방 수, 방별 메시지 수, unread 비율, 필터 선택도)를 준비하고 변경 전
   최신 메시지 anti-NOT EXISTS/정렬, count DISTINCT, unread GROUP BY, 카테고리 집계, 메시지 페이지의
   실제 생성 SQL에 EXPLAIN/지원되는 버전의 EXPLAIN ANALYZE를 실행한다. ANALYZE는 실제 쿼리를 실행하므로
   우선 격리 DB에서 한다. 실행시간, 읽은 행 수, 임시 테이블/filesort, 인덱스 선택, 큰 offset 비용을 기록한다.
4. 백업·복구 확인과 변경 승인을 받은 운영자가 [SQL](20261019_chat_query_indexes.sql)의 각 문장을 검토한다.
   인덱스 크기/여유 디스크, 메타데이터 잠금, 동시 INSERT/읽음 UPDATE 유지 비용, 복제 지연과 작업 시간을 확인한다.
   버전·환경에 맞는 DDL 옵션/점검 시간을 결정한 후 한 문장씩 수동 적용한다. 실패 시 완료된 DDL부터 확인한다.
5. 같은 데이터/SQL/조건으로 적용 전후 실행계획과 지연시간을 비교한다. DB LIMIT/OFFSET,
   고정·최근순·roomId 보조 정렬, 마지막 메시지 동률, 나간 방 제외, 익명 검색 및 is_read 집계를 확인한다.
   lower(nickname) + 앞쪽 % 부분 검색은 이번 인덱스로 선택적 탐색을 보장하지 않는다.
6. 서버와 프론트의 페이지 응답 전환을 함께 배포한다. 목록 `data`가 배열인 기존 클라이언트는 호환되지 않는다.
   마지막 페이지까지 발견 가능한지, 검색/필터/고정/새 메시지/나가기 후 첫 페이지 갱신과 선택한 방 단건 조회를 확인한다.

롤백은 아래의 **FK 지원 복원 절차를 먼저 수행한 뒤** 작업 기록에 있는 후보만 제거한다.
DROP도 DDL 잠금/암묵 커밋 영향을 검토한다. 이번 인덱스는 비고유이며 데이터 변환은 없다.
이 작업의 롤백을 이유로 이전 is_read 컬럼이나 읽음 확인 데이터를 삭제하지 않는다.
API 롤백 시 서버/클라이언트 응답 형식을 함께 되돌려야 한다.

남은 운영 검증: MySQL의 테이블/컬럼 호환, 정렬 Collation, correlated subquery의 실제 비용,
많은 메시지를 가진 방의 최신 메시지 탐색, 집계/정렬 실행계획, p95/p99 지연·메모리,
동시 생성/전송/읽음/고정/나가기 및 기존 잠금 경로의 다중 JVM 정합성.
서로 다른 페이지 사이에 데이터가 변하면 offset 페이지가 이동하는 특성은 유지하며 snapshot 보장은 제공하지 않는다.

## 2026-10-20 보완: 실행 기록·재실행·FK 롤백

과거 1553 재현은 [이전 보고서](../CHAT_MYSQL_INTEGRATION_VALIDATION.md), 이번 구현·미검증은
[후속 보고서](../CHAT_MYSQL_QUERY_INDEX_IMPROVEMENT.md)를 따른다. 보완 직후의 미실행 상태와
이후 최종 재검증 결과를 구분한다. 과거 도구의 일부 성공을 이 절차 전체의 통과로 해석하지 않는다.

### 접속 확인과 변경 전 기록

- 승인된 격리 DB와 전용 계정으로 DB명·서버 UUID·호스트/버전·계정·역할·권한을 비교한다.
  `kkirikkiri`, `kkiri_kkiri`에는 실행하지 않는다. 이름/권한을 확인할 수 없으면 중단한다.
  관리자 계정으로 대신 실행하거나 계정 비밀번호를 자동 재설정하지 않는다.
- `SELECT DATABASE(), CURRENT_USER(), CURRENT_ROLE(), @@server_uuid, VERSION(); SHOW GRANTS;`
  및 세 채팅 테이블의 SHOW CREATE TABLE/SHOW INDEX를 적용 전·후·롤백 후·재적용 후 저장한다.
  실제 테이블 대소문자와 InnoDB를 확인한다. information_schema 비교도 실제 이름을 사용한다.
- is_read 전환 완료 상태를 확인하되 이번 인덱스 작업을 이유로 ADD COLUMN/읽음 변환을 반복하지 않는다.
  기존/테스트 데이터 초기화·삭제·재생성, 서버 GLOBAL 설정·Docker 변경은 이 절차에 포함하지 않는다.
- 아래 메타데이터와 데이터 snapshot/hash를 별도 작업 기록으로 보관한다. CARDINALITY 등의
  통계는 변할 수 있으므로 FK 정의·인덱스 정의·데이터 보존을 각각 판정한다.

```sql
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME,
       SUB_PART, COLLATION, INDEX_TYPE, IS_VISIBLE, EXPRESSION
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('CHAT_ROOM', 'CHAT_ROOM_MEMBER', 'CHAT_MESSAGE')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

SELECT TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION, COLUMN_NAME,
       REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE CONSTRAINT_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
  AND TABLE_NAME IN ('CHAT_ROOM', 'CHAT_ROOM_MEMBER', 'CHAT_MESSAGE')
ORDER BY TABLE_NAME, CONSTRAINT_NAME, ORDINAL_POSITION;

SELECT TABLE_NAME, CONSTRAINT_NAME, UPDATE_RULE, DELETE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('CHAT_ROOM', 'CHAT_ROOM_MEMBER', 'CHAT_MESSAGE')
ORDER BY TABLE_NAME, CONSTRAINT_NAME;
```

### 적용·재실행 판정

| 후보 이름 | 열 순서 | 용도 |
| --- | --- | --- |
| idx_chat_message_room_latest | chat_room_id, sent_at, message_id | 최신 시각/ID 역방향 단건 탐색, 메시지 커서 |
| idx_chat_message_room_unread | chat_room_id, is_read, sender_id | 현재 페이지의 정확한 메시지별 미읽음 집계 |
| idx_chat_member_user_active_room | user_id, is_left, chat_room_id | 사용자별 활성 참여 방 탐색 |

- 같은 이름이면 열 순서·접두 길이·표현식·형식·가시성을 비교한다. 다른 정의면 중단하며
  기존 인덱스를 DROP/재생성하지 않는다. 다른 이름의 동등 인덱스가 있으면 중복 생성하지 않는다.
- 확장 인덱스는 필요한 열이 같은 순서의 선두인지, 정렬 방향·covering·실제 계획을 확인한다.
  `(chat_room_id, is_read, ...)`만으로 최신 sent_at 정렬도 지원한다고 판단하지 않는다.
  `(chat_room_id)` 단일 인덱스와 복합 인덱스는 조회 기능이 다르며 PK/UNIQUE는 업무 제약이다.
- SQL 파일은 후보 정의이며 재실행 가능한 배치가 아니다. 필요한 CREATE만 한 문장씩 실행하고
  **실제로 생성한 이름/정의**를 기록한다. 기존 동등 인덱스는 이번 마이그레이션 소유가 아니다.
- 문장마다 SHOW INDEX를 저장해 자동 FK 지원 인덱스가 없어졌는지 변경 전 목록과 비교한다.
  1061 등 오류 시 중단하고 완료된 DDL부터 확인한다. DDL은 암묵 커밋되며 ROLLBACK으로 취소되지 않는다.

새 목록 쿼리는 Hibernate HQL의 제한된 scalar subquery로 방별
`ORDER BY sent_at DESC, message_id DESC LIMIT 1` ID를 고르고 PK로 메시지를 연결한다.
필터에 맞는 모든 방의 최신 시각은 정렬에 필요하지만 메시지 후보 전체의 anti-join은 제거했다.
프로필·설문·점수·unread는 반환 페이지에 한정한다. 기존 latest 인덱스를 역방향 읽는 설계이며
별도 DESC 인덱스를 무조건 추가하지 않는다. 실제 MySQL 계획/지연시간 검증은 남아 있다.

### FK 지원을 보존하는 롤백

InnoDB는 자식 FK 열이 같은 순서로 **인덱스의 선두에** 있어야 한다. 없으면 지원 인덱스를
자동 생성하며, 다른 사용 가능한 인덱스가 생기면 자동 생성 인덱스를 조용히 제거할 수 있다.
복합 인덱스 DROP 때 원래 지원 인덱스가 자동 복원된다고 가정하면 안 된다. 유일한 FK 지원을
DROP하면 1553으로 거절된다. 근거는 [MySQL 8.0 FK 공식 문서](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html)다.

1. 이번에 실제 생성한 후보만 롤백 대상으로 정한다. 사전 존재한 동등 인덱스는 제외한다.
   PK·UNIQUE·FK 제약을 제거하거나 `FOREIGN_KEY_CHECKS=0`으로 우회하지 않는다.
2. 현재 메타데이터를 읽고 **DROP 예정 후보를 모두 제외한 최종 집합**에서 각 FK의 선두
   열 지원을 검토한다. 후보끼리 서로 지원한다고 판단하면 안 된다. 접두 길이·표현식·invisible
   인덱스를 정상 FK 지원으로 간주하지 않는다.
3. 이번 후보와 직접 관련된 분기는 아래와 같다. 원래 이름의 인덱스가 남았다고 가정하지 않는다.

| 테이블 / FK 열 | DROP 후 남을 수 있는 지원 | 없을 때만 먼저 복원 |
| --- | --- | --- |
| CHAT_MESSAGE / chat_room_id | chat_room_id 선두의 잔존 인덱스 | 전체 열 `(chat_room_id)` 비고유 BTREE |
| CHAT_ROOM_MEMBER / user_id | user_id 선두의 잔존 인덱스 | 전체 열 `(user_id)` 비고유 BTREE |
| CHAT_MESSAGE / sender_id | 기존 sender_id 지원 | 후보로 대체되지 않음. 없다면 중단·조사 |
| CHAT_ROOM_MEMBER / chat_room_id | 기존 `(chat_room_id,user_id)` UNIQUE 등 | 없다면 중단·조사 |

4. 누락된 두 분기에서만 변경 전 기록의 원래 이름·정의를 먼저 복원한다. 이름 충돌 시
   덮어쓰지 않는다. 원래 이름을 쓸 수 없다면 승인·기록한 복구 이름을 사용한다. 아래는
   **조건 확인 후 선택하는 예시**이며 일괄 실행하지 않는다.

```sql
-- DROP 예정 후보를 제외하면 chat_room_id 선두 지원이 없고 이 이름도 없을 때만:
CREATE INDEX idx_chat_message_fk_restore ON CHAT_MESSAGE (chat_room_id);
-- DROP 예정 후보를 제외하면 user_id 선두 지원이 없고 이 이름도 없을 때만:
CREATE INDEX idx_chat_member_fk_restore ON CHAT_ROOM_MEMBER (user_id);
```

5. CREATE 후 SHOW INDEX로 지원이 **실제로 존재**하는지 재확인한다. 보존된 테스트 DB에는
   위 복구 인덱스가 이미 있을 수 있으므로 중복 CREATE하지 않는다.
6. 작업 기록에서 소유가 확인된 후보만 한 문장씩 제거하고 매번 FK·잔존 지원을 재검사한다.
   예시 순서는 unread → latest → member다. 순서 자체가 지원 복원 검사를 대체하지 않는다.
   사전 확인 없이 실행 가능한 DROP 배치는 제공하지 않는다.
7. 롤백 후 FK 열/참조/UPDATE·DELETE 규칙이 변경 전과 같아야 한다. 승인된 격리 DB에서만
   정상 참조 입력, 잘못된 자식 참조 거절, 참조 중 부모 삭제 거절을 확인한다. DML 검사는
   별도 트랜잭션에서 ROLLBACK하고 기존 행·합성 데이터를 초기화하지 않는다.
   FK 오류 1216/1452, 1217/1451과 SQLState 23000을 구분해 기록한다.
8. 행 수 외에 ID·본문·시각·sender·방·is_read·참여 설정/나가기 상태 snapshot/hash도 비교한다.
   FK 정의만 같다는 이유로 데이터 보존·참조 무결성 통과를 선언하지 않는다.

복구 인덱스는 롤백의 FK 지원이므로 유지한다. 재적용 후 완전 동일 인덱스와 선두 열만
겹치는 인덱스를 구분한다. 겹침을 없애기 위해 무조건 DROP하지 않으며, 다음 롤백 복원 비용과
쓰기 부하를 확인한 운영자가 별도 제거를 판단한다. 컬럼·API·데이터 변환 롤백은 별도 작업이다.

### 부분 실패 복구

| 중단 지점 | 복구 절차 |
| --- | --- |
| CREATE 일부만 완료 | 메타데이터/작업 기록 확인 후 미완료 후보만 생성. 완료 CREATE 반복 금지 |
| 동일 이름 1061 | 같은 정의면 완료로 기록하고 skip. 다른 정의면 중단, DROP으로 해소하지 않음 |
| 지원 복원 CREATE 실패 | 후보 DROP 금지. 충돌/권한/디스크/metadata lock 확인, 기존 후보 유지 |
| 후보 DROP 1553 | 더 DROP하지 않고 잔존·예정 집합을 재계산해 누락 지원부터 복원 |
| 후보 일부 DROP 후 중단 | 완료 DROP 반복 금지. 상태 재기록 후 롤백 계속 또는 필요한 후보만 재적용하는 forward recovery 선택 |
| 재적용 일부 실패 | 복구 FK 인덱스를 유지하고 누락 후보만 추가. FK/데이터 대조 후 판단 |

부분 실패 실험은 존재하는 후보 CREATE의 1061처럼 데이터 보존이 가능한 실패로 재현한다.
필요 인덱스를 무방비 DROP하거나 FK를 비활성화하지 않는다. 실제 전체 검증은 적용 전 → 적용 후 →
지원 복원/롤백 후 → 재적용 후 각 단계의 인덱스/FK·정상/비정상 참조·동일 데이터 보존을 확인한다.

같은 데이터/인덱스/통계/페이지 크기의 anti-join과 단건 탐색을 1/10/60개 방, 가능하면
100개 이상에서 비교한다. SQL 수·5회 반복/중앙값·실제 행/loops·인덱스·latest/unread 비용·페이지
metadata를 기록한다. ANALYZE는 실제 실행이다. 3초 제한 초과 시 반복을 중단하고 계획을
먼저 분석하며 타임아웃을 올려 통과시키지 않는다. 운영 백업 전체 복구·대형 DDL·복제·p95/p99,
다중 JVM·브라우저 E2E는 별도 검증이다.
