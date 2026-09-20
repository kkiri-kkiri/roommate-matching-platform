package com.kkirikkiri.chat.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.chat.entity.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

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

	/**
	 * lastReadAt이 null이면 상대 메시지 전체를 미읽음으로 센다.
	 */
	@Query("""
			select count(m) from ChatMessage m
			where m.chatRoom.chatRoomId = :roomId
			  and m.sender.userId <> :userId
			  and (:lastReadAt is null or m.sentAt > :lastReadAt)
			""")
	long countUnreadMessages(@Param("roomId") Long roomId, @Param("userId") Long userId,
			@Param("lastReadAt") LocalDateTime lastReadAt);
}
