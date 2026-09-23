package com.kkirikkiri.chat.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kkirikkiri.chat.entity.ChatRoom;
import com.kkirikkiri.chat.entity.ChatRoomMember;
import com.kkirikkiri.chat.entity.ChatRoomStatus;
import com.kkirikkiri.chat.repository.ChatRoomMemberRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMembershipTest {

	@Mock
	private ChatRoomMemberRepository members;

	@InjectMocks
	private ChatService service;

	@Test
	void allowsActiveMember() {
		var member = mock(ChatRoomMember.class);
		var room = mock(ChatRoom.class);
		when(members.findByChatRoom_ChatRoomIdAndUser_UserId(15L, 1L)).thenReturn(Optional.of(member));
		when(member.getChatRoom()).thenReturn(room);
		when(room.getStatus()).thenReturn(ChatRoomStatus.ACTIVE);
		assertDoesNotThrow(() -> service.validateActiveMember(1L, 15L));
	}

	@Test
	void rejectsNonMember() {
		when(members.findByChatRoom_ChatRoomIdAndUser_UserId(15L, 1L)).thenReturn(Optional.empty());
		assertForbidden();
	}

	@Test
	void rejectsMemberWhoLeft() {
		var member = mock(ChatRoomMember.class);
		when(members.findByChatRoom_ChatRoomIdAndUser_UserId(15L, 1L)).thenReturn(Optional.of(member));
		when(member.isLeft()).thenReturn(true);
		assertForbidden();
	}

	@Test
	void rejectsClosedRoom() {
		var member = mock(ChatRoomMember.class);
		var room = mock(ChatRoom.class);
		when(members.findByChatRoom_ChatRoomIdAndUser_UserId(15L, 1L)).thenReturn(Optional.of(member));
		when(member.getChatRoom()).thenReturn(room);
		when(room.getStatus()).thenReturn(ChatRoomStatus.CLOSED);
		assertForbidden();
	}

	private void assertForbidden() {
		var exception = assertThrows(CustomException.class, () -> service.validateActiveMember(1L, 15L));
		assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
	}
}
