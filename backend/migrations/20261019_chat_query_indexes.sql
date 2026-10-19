-- MANUAL ONLY. Read 20261019_chat_query_indexes.md before executing anything.
-- Verify table name case, existing equivalent indexes, MySQL version and DDL lock impact.
-- Requires the earlier chat_message_receipts migration (is_read).
-- Each CREATE INDEX implicitly commits; never rerun blindly after partial failure.
CREATE INDEX idx_chat_message_room_latest ON CHAT_MESSAGE (chat_room_id, sent_at, message_id);
CREATE INDEX idx_chat_message_room_unread ON CHAT_MESSAGE (chat_room_id, is_read, sender_id);
CREATE INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER (user_id, is_left, chat_room_id);
