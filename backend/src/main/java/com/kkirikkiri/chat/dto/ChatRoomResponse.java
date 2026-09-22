package com.kkirikkiri.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.kkirikkiri.chat.entity.ChatRoom;
import com.kkirikkiri.chat.entity.ChatRoomCategory;

/**
 * 채팅방 목록 항목 및 생성/기존 방 조회에 재사용하는 응답.
 * 상대 프로필, 출처 게시글 ID, 집계, 개인 설정 및 최근 메시지는 서비스에서 전달한다.
 * sourcePost는 Entity가 아닌 게시글 ID이며, 댓글 출처의 게시글도 서비스에서 확인한다.
 */
public record ChatRoomResponse(
		Long id,
		ChatRoomCategory category,
		CounterpartResponse counterpart,
		Long sourcePost,
		long unreadCount,
		boolean pinned,
		boolean muted,
		LastMessageResponse lastMessage) {

	public static ChatRoomResponse from(
			ChatRoom room, CounterpartResponse counterpart, Long sourcePost,
			long unreadCount, boolean pinned, boolean muted, LastMessageResponse lastMessage) {
		return new ChatRoomResponse(room.getChatRoomId(), room.getCategory(), counterpart,
				sourcePost, unreadCount, pinned, muted, lastMessage);
	}

	/** 프로필/설문/매칭 데이터의 조회와 조합은 서비스의 책임이다. */
	public record CounterpartResponse(
			Long id,
			String nickname,
			String profileImageUrl,
			String dormitory,
			String admissionYear,
			String major,
			Integer matchScore,
			String introduction,
			List<String> tags) {
	}

	public record LastMessageResponse(String text, LocalDateTime sentAt) {
	}
}
