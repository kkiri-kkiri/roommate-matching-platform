package com.kkirikkiri.chat.repository;

import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

import com.kkirikkiri.chat.entity.ChatRoomMember;

@RequiredArgsConstructor
public class ChatMemberLockRepositoryImpl implements ChatMemberLockRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<ChatRoomMember> findMemberForUpdate(Long roomId, Long userId) {
        // Membership identities are permanent: leaving never deletes or recreates this row.
        // This lookup can use an RR snapshot; authorization uses the locking refresh below.
        var ids = entityManager.createQuery("""
                select m.chatRoomMemberId from ChatRoomMember m
                where m.chatRoom.chatRoomId = :roomId and m.user.userId = :userId
                """, Long.class).setParameter("roomId", roomId).setParameter("userId", userId).getResultList();
        if (ids.isEmpty()) return Optional.empty();
        try {
            var member = entityManager.getReference(ChatRoomMember.class, ids.getFirst());
            var lockMode = entityManager.getLockMode(member);
            if (lockMode != LockModeType.PESSIMISTIC_WRITE && lockMode != LockModeType.PESSIMISTIC_FORCE_INCREMENT) {
                // Locking find/query alone may return the old first-level-cache state.
                // Refresh while acquiring the lock, as in UserAccountLockRepositoryImpl.
                entityManager.refresh(member, LockModeType.PESSIMISTIC_WRITE,
                        Map.of("jakarta.persistence.lock.timeout", 5000));
            }
            return Optional.of(member);
        } catch (EntityNotFoundException missing) {
            return Optional.empty();
        }
    }
}
