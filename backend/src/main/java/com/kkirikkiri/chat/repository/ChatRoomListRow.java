package com.kkirikkiri.chat.repository;

import java.time.LocalDateTime;
import com.kkirikkiri.chat.entity.ChatRoomCategory;

/** Internal scalar projection. Real counterpart IDs must never be returned directly for anonymous rows. */
public record ChatRoomListRow(Long roomId, ChatRoomCategory category, Long counterpartId, Long sourcePost,
        boolean anonymous, boolean pinned, boolean muted, String lastText, LocalDateTime lastSentAt, long memberCount) {
}
