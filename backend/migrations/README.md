# 익명 채팅 키 마이그레이션

Auth 공유 제한 테이블의 별도 절차는 [20261014_auth_limits.md](20261014_auth_limits.md)를 따른다.
이메일 변형 제한 우회·인증 잠금 간섭 수정은 그 후
[20261015_auth_email_identity_indexes.md](20261015_auth_email_identity_indexes.md)를 따른다.

대상: `20261011_chat_anonymous_keys.sql`. 운영에서 자동 실행하지 않는다.
컬럼 추가나 방/참여자/메시지 삭제 없이 기존 일반 COMMUNITY 키 중 현재 익명 출처를 가리키는 키에
`:POST:{id}` 또는 `:COMMENT:{id}`를 붙인다. 이미 변환된 키는 다시 바꾸지 않는다.

## 사전 점검

1. 운영자에게 DB 버전·대상 schema·테이블 물리 이름(대소문자)을 확인받는다.
   Entity의 대문자 이름과 실제 물리 이름은 다를 수 있으므로 스크립트를 그대로 실행하기 전에 대조한다.
2. `SHOW CREATE TABLE`로 CHAT_ROOM.room_key 길이 100/UNIQUE, 출처 FK,
   COMMUNITY_POST/COMMUNITY_COMMENT의 anonymous NOT NULL DEFAULT FALSE를 확인한다.
   누락 컬럼은 별도로 검토한 스키마 변경이 선행되어야 한다. 이 SQL은 DDL을 수행하지 않는다.
3. 모든 구버전·신버전 쓰기 인스턴스와 채팅 연결을 중지한다. 마이그레이션 동안 방 생성/출처 수정 금지.
4. 현재 출처의 anonymous가 과거 방/메시지의 익명성 의도와 일치하는지 확인한다.
   이미 출처를 덮어쓴 일반 방이나 삭제/수정된 출처의 과거 익명성은 이 SQL로 복원할 수 없다.
   source_post_id와 source_comment_id가 모두 있는 비정상 방, 누락 참조는 수동 검토 후 진행한다.
5. 적용 후보의 old_key/new_key 목록을 export하여 row ID와 함께 보관한다.
   proposed new_key 중복, 기존 room_key와의 충돌, 길이 초과가 있으면 중지한다.
   충돌 해결을 위해 방/메시지를 삭제하거나 자동 병합하지 않는다.

아래 SQL은 읽기 전용 후보 점검 예시다. 실제 테이블명으로 조정한다.

```sql
SELECT r.chat_room_id, r.room_key AS old_key,
       CASE WHEN p.anonymous = TRUE THEN CONCAT(r.room_key, ':POST:', p.post_id)
            WHEN c.anonymous = TRUE THEN CONCAT(r.room_key, ':COMMENT:', c.comment_id)
            ELSE r.room_key END AS new_key,
       r.source_post_id, r.source_comment_id
FROM CHAT_ROOM r
LEFT JOIN COMMUNITY_POST p ON p.post_id = r.source_post_id
LEFT JOIN COMMUNITY_COMMENT c ON c.comment_id = r.source_comment_id
WHERE r.category = 'COMMUNITY'
  AND r.room_key REGEXP '^COMMUNITY:[0-9]+:[0-9]+$';
```

## 백업과 적용

1. 쓰기 중지 상태에서 전체 DB의 일관된 백업과 별도 `CHAT_ROOM(chat_room_id, room_key)` export를 생성한다.
   백업 복구를 격리 DB에서 확인한다. 백업에는 민감 정보가 있으므로 접근을 제한한다.
2. 격리된 동일 버전 MySQL에서 실제 데이터 복사본에 SQL을 적용한다.
   후보 충돌·참조·행 수·키 변화와 실패 시 롤백을 확인한다.
3. 운영 적용은 운영자가 stop-on-error 방식의 SQL 클라이언트로 수행한다.
   스크립트의 두 UPDATE를 하나의 트랜잭션으로 적용한다. UPDATE 오류가 발생하면 COMMIT하지 말고 ROLLBACK한다.
   실패 후 남은 명령을 계속 실행하는 클라이언트 설정을 사용하지 않는다.
4. 변환 결과와 export의 예상 new_key를 대조한 뒤 신버전만 시작한다.
   prod는 validate/SQL never이고 이 스크립트를 자동 실행하지 않는다.

## 롤백

- COMMIT 전 실패: 같은 세션에서 ROLLBACK하고 후보 충돌 원인을 확인한다.
- COMMIT 후, 재기동 전 실패: 모든 쓰기가 멈춘 상태에서 백업의 정확한 chat_room_id→old_key만
  별도 검토한 트랜잭션으로 복원한다. 키의 접미사를 문자열 규칙으로 일괄 제거하면 UNIQUE 충돌/의도 오염이 가능하므로 금지한다.
- 신버전이 이미 신규 방/메시지를 쓴 경우: 즉시 쓰기를 중지하고 신규 방과 기존 방 충돌을 점검한다.
  키만 무조건 복원하거나 전체 DB 백업을 덮어쓰지 않는다. 메시지 유실 없이 복구하는 별도 계획이 필요하다.
- 구버전은 새로운 익명 방 키와 맞지 않으므로 키 복구 없이 구버전을 재기동하지 않는다.

## 검증

- CHAT_ROOM/CHAT_ROOM_MEMBER/CHAT_MESSAGE 행 수와 ID/FK/메시지 본문이 변경되지 않았는지 확인한다.
- 변환 대상 키가 export 예상과 같고 길이·UNIQUE 위반이 없는지 확인한다.
- 익명 게시글/댓글 방의 분리·재사용, 일반 방 출처 갱신, 과거 메시지의 익명 senderId/상대 프로필 마스킹을 확인한다.
- 서버 재기동 후 REST 조회·STOMP CONNECT/SEND/SUBSCRIBE, 나가기·정지·만료 후 송수신 차단을 검증한다.
- 현재 출처만으로 복원할 수 없는 과거 채팅방 익명성은 별도 검토 대상이다. 실제 author.id 공개에 따른 재식별 정책 보류는 익명 게시글을 가리킨다([API.md 19.4](../API.md#194-문서-수정으로-해결되지-않는-보안배포-위험)). 익명 댓글·답글의 author.id와 author.major는 현재 응답에서 null이며 이 정책 보류와 구분한다.
