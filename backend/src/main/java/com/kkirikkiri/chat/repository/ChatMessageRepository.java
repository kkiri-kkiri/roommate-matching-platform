package com.kkirikkiri.chat.repository;

import java.util.Optional;
import java.util.List;
import java.util.Collection;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** Current read after the sender membership lock, including under MySQL repeatable read. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select m from ChatMessage m where m.chatRoom.chatRoomId = :roomId
			and m.sender.userId = :userId and m.clientMessageId = :clientId
			""")
	Optional<ChatMessage> findClientMessageForUpdate(@Param("roomId") Long roomId,
			@Param("userId") Long userId, @Param("clientId") String clientId);

	Optional<ChatMessage> findByMessageIdAndChatRoom_ChatRoomId(Long messageId, Long roomId);

	@Query("""
			select m from ChatMessage m where m.chatRoom.chatRoomId = :roomId
			and (:pattern is null or m.content like :pattern escape '!')
			and (m.sentAt < :sentAt or (m.sentAt = :sentAt and m.messageId < :id))
			""")
	Page<ChatMessage> findBefore(@Param("roomId") Long roomId, @Param("sentAt") java.time.LocalDateTime sentAt,
			@Param("id") Long id, @Param("pattern") String pattern, Pageable pageable);

	@Query("""
			select m from ChatMessage m where m.chatRoom.chatRoomId = :roomId
			and (:pattern is null or m.content like :pattern escape '!')
			and (m.sentAt > :sentAt or (m.sentAt = :sentAt and m.messageId > :id))
			""")
	Page<ChatMessage> findAfter(@Param("roomId") Long roomId, @Param("sentAt") java.time.LocalDateTime sentAt,
			@Param("id") Long id, @Param("pattern") String pattern, Pageable pageable);

	@Query("""
			select new com.kkirikkiri.chat.dto.ChatMessageReadStatusResponse(m.messageId, m.read)
			from ChatMessage m where m.chatRoom.chatRoomId = :roomId and m.messageId in :ids
			""")
	List<com.kkirikkiri.chat.dto.ChatMessageReadStatusResponse> findReadStatuses(
			@Param("roomId") Long roomId, @Param("ids") Collection<Long> ids);

	/**
	 * 메시지 목록의 정렬은 Pageable로 전달한다.
	 * 최신순 조회 시 sentAt, messageId 내림차순으로 지정한다.
	 */
	Page<ChatMessage> findAllByChatRoom_ChatRoomId(Long roomId, Pageable pageable);

	/**
	 * 메시지 목록의 q가 있을 때 본문 부분 일치 검색에 사용한다.
	 */
	Page<ChatMessage> findAllByChatRoom_ChatRoomIdAndContentContaining(
			Long roomId, String keyword, Pageable pageable);

	/**
	 * 목록의 최근 메시지 표시 및 서비스의 채팅방 정렬에 사용한다.
	 * 메시지가 없는 방은 Optional.empty()를 반환한다.
	 */
	Optional<ChatMessage> findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(Long roomId);

	/**
	 * 읽음 처리할 상대 메시지의 최종 시각을 조회한다.
	 * 서비스에서 조회한 sentAt을 참여자의 markRead에 전달한다.
	 */
	Optional<ChatMessage> findFirstByChatRoom_ChatRoomIdAndSender_UserIdNotOrderBySentAtDescMessageIdDesc(
			Long roomId, Long userId);

	/** Exact receipts; a delayed commit or equal sentAt must not inherit another message's receipt. */
	@Query("""
			select count(m) from ChatMessage m
			where m.chatRoom.chatRoomId = :roomId
			  and m.sender.userId <> :userId
			  and m.read = false
			""")
	long countUnreadMessages(@Param("roomId") Long roomId, @Param("userId") Long userId);

	@Query("""
			select new com.kkirikkiri.chat.repository.ChatUnreadCount(m.chatRoom.chatRoomId, count(m))
			from ChatMessage m where m.chatRoom.chatRoomId in :roomIds
			and m.sender.userId <> :userId and m.read = false
			group by m.chatRoom.chatRoomId
			""")
	List<ChatUnreadCount> countUnreadForRooms(@Param("roomIds") Collection<Long> roomIds, @Param("userId") Long userId);

	/** Current/locking read also works when an enclosing RR transaction already has a snapshot. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select m from ChatMessage m
			where m.chatRoom.chatRoomId = :roomId and m.sender.userId <> :userId
			  and m.messageId in :messageIds
			order by m.messageId
			""")
	List<ChatMessage> findIncomingForRead(@Param("roomId") Long roomId, @Param("userId") Long userId,
			@Param("messageIds") Collection<Long> messageIds);
}
