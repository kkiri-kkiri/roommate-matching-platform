# 채팅 전송 ID 수동 마이그레이션

`20261023_chat_send_idempotency.sql`은 승인된 DB에 수동 적용하는 배포 선행 조건이다.
이번 수정에서는 DB에 연결하거나 이 SQL을 실행하지 않았다. 보존 E2E 자원에도 적용하지 않는다.
MySQL ALTER TABLE의 잠금/온라인 DDL 조건은 대상 환경에서 별도로 검토한다.

`CHAT_MESSAGE.client_message_id`는 nullable VARCHAR(36)이다. 기존 행과 기존 `{text}` 전송은
NULL을 사용한다. `(chat_room_id, sender_id, client_message_id)` UNIQUE는 같은 발신자가 같은 방에
보낸 동일 요청의 중복 저장을 막는다. 다른 본문으로 같은 키를 재사용하면 INVALID_REQUEST다.
참여자 잠금은 기존 USER → membership 순서를 재사용하고, 키 조회는 MySQL current read를 사용한다.
응답 유실 후 같은 키를 다시 보내도 기존 행을 반환하며 컨트롤러는 서비스 트랜잭션 커밋 후 방송한다.

배포 순서는 수동 SQL → 백엔드 → 프론트다. 기존 클라이언트와 기존 행은 계속 지원한다.
새 프론트를 구 백엔드와 조합하면 저장 확인 ID가 없어 대기 상태가 유지되므로 함께 배포해야 한다.
자동 rollback/컬럼 삭제 스크립트는 제공하지 않는다. 이미 사용한 키를 삭제하면 재시도의 중복 방지가 사라진다.

실제 MySQL의 동시 동일 키 전송, 커밋 후 방송 유실/서버 재시작 및 재시도는 별도 승인된
새 격리 자원에서 추가 E2E 검증해야 한다. 이번 mock 단위 검증은 물리 UNIQUE/잠금 검증을 대체하지 않는다.
