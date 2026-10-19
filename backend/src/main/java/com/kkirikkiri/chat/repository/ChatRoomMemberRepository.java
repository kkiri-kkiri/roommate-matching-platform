package com.kkirikkiri.chat.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.chat.entity.ChatRoomCategory;
import com.kkirikkiri.chat.entity.ChatRoomMember;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long>, ChatMemberLockRepository {

	/**
	 * ALL 목록의 현재 사용자 참여 정보를 조회한다. 나간 방은 제외한다.
	 * 상대 프로필 검색과 고정 여부/최근 메시지 기준 최종 정렬은 서비스에서 처리한다.
	 */
	List<ChatRoomMember> findAllByUser_UserIdAndLeftFalse(Long userId);

	List<ChatRoomMember> findAllByUser_UserIdAndLeftFalseAndChatRoom_Category(
			Long userId, ChatRoomCategory category);

	/**
	 * 읽음/설정/나가기 대상 조회 및 기존 참여자의 나가기 상태 확인에 사용한다.
	 */
	Optional<ChatRoomMember> findByChatRoom_ChatRoomIdAndUser_UserId(Long roomId, Long userId);

	boolean existsByChatRoom_ChatRoomIdAndUser_UserIdAndLeftFalse(Long roomId, Long userId);

	/**
	 * 나간 상대방도 포함하여 상대 사용자와 마지막 읽음 시각을 확인한다.
	 */
	List<ChatRoomMember> findAllByChatRoom_ChatRoomId(Long roomId);

	long countByUser_UserIdAndPinnedTrueAndLeftFalse(Long userId);

	/**
	 * Current read rather than a potentially pre-lock MySQL RR COUNT snapshot.
	 * READ locks must not masquerade as the refresh/WRITE lock acquired by findMemberForUpdate.
	 */
	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select m from ChatRoomMember m where m.user.userId = :userId and m.pinned = true and m.left = false order by m.chatRoomMemberId")
	List<ChatRoomMember> findPinnedMembersForUpdate(@Param("userId") Long userId);

	/** Flush receipts first; the caller also synchronizes the locked managed membership afterward. */
	@Modifying(flushAutomatically = true)
	@Query("""
			update ChatRoomMember m set m.lastReadAt = :readAt
			where m.chatRoomMemberId = :memberId and m.left = false
			  and (m.lastReadAt is null or m.lastReadAt < :readAt)
			""")
	int advanceReadTime(@Param("memberId") Long memberId, @Param("readAt") LocalDateTime readAt);

	long countByChatRoom_ChatRoomIdAndLeftFalse(Long roomId);
}
