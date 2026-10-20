package com.kkirikkiri.chat.repository;

import java.util.Optional;

import com.kkirikkiri.chat.entity.ChatRoomMember;

public interface ChatMemberLockRepository {
    /** USER must already be locked. Reload stale managed state with a locking/current read. */
    Optional<ChatRoomMember> findMemberForUpdate(Long roomId, Long userId);
}
