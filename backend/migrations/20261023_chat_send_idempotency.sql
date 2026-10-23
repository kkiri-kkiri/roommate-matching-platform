-- MANUAL ONLY. Apply to an explicitly approved database before deploying the new backend.
-- No startup runner executes this file. Existing messages retain NULL and remain readable.
ALTER TABLE CHAT_MESSAGE
    ADD COLUMN client_message_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_chat_message_client UNIQUE (chat_room_id, sender_id, client_message_id);
