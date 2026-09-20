package com.kkirikkiri.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.dao.DataIntegrityViolationException;

import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.repository.*;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.matching.repository.RoommateMatchRepository;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {

	private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
	private final LocalDateTime sentAt = LocalDateTime.of(2026, 9, 20, 12, 0);
	private ChatRoomRepository rooms;
	private ChatRoomMemberRepository members;
	private ChatMessageRepository messages;
	private UserRepository users;
	private UserProfileRepository profiles;
	private CommunityPostRepository posts;
	private CommunityCommentRepository comments;
	private PlatformTransactionManager transactions;
	private ChatService service;
	private User me;
	private User other;
	private ChatRoom room;
	private ChatRoomMember mine;
	private ChatRoomMember theirs;

	@BeforeEach
	void setUp() {
		rooms = mock(ChatRoomRepository.class);
		members = mock(ChatRoomMemberRepository.class);
		messages = mock(ChatMessageRepository.class);
		users = mock(UserRepository.class);
		profiles = mock(UserProfileRepository.class);
		posts = mock(CommunityPostRepository.class);
		comments = mock(CommunityCommentRepository.class);
		transactions = mock(PlatformTransactionManager.class);
		when(transactions.getTransaction(any(TransactionDefinition.class))).thenAnswer(call -> mock(TransactionStatus.class));
		service = new ChatService(rooms, members, messages, users, profiles,
				mock(ProfileSurveyRepository.class), mock(RoommateMatchRepository.class),
				posts, comments,
				VALIDATORS.getValidator(), transactions);
		me = new User("me", "hash", "me@example.com");
		other = new User("other", "hash", "other@example.com");
		ReflectionTestUtils.setField(me, "userId", 1L);
		ReflectionTestUtils.setField(other, "userId", 2L);
		room = new ChatRoom(1L, 2L, ChatRoomCategory.ROOMMATE, null, null);
		ReflectionTestUtils.setField(room, "chatRoomId", 10L);
		mine = new ChatRoomMember(room, me);
		theirs = new ChatRoomMember(room, other);
		when(members.findByChatRoom_ChatRoomIdAndUser_UserId(10L, 1L)).thenReturn(Optional.of(mine));
		when(members.findAllByChatRoom_ChatRoomId(10L)).thenReturn(List.of(mine, theirs));
		when(users.findById(1L)).thenReturn(Optional.of(me));
		when(users.findById(2L)).thenReturn(Optional.of(other));
		UserProfile profile = mock(UserProfile.class);
		Dormitory dormitory = mock(Dormitory.class);
		when(profile.getNickname()).thenReturn("상대");
		when(profile.getDormitory()).thenReturn(dormitory);
		when(profiles.findByUser_UserId(2L)).thenReturn(Optional.of(profile));
	}

	@AfterAll
	static void closeValidation() {
		VALIDATORS.close();
	}

	@Test
	void rejectsNonMemberAndLeftMemberBeforeSending() {
		assertThrows(CustomException.class, () -> service.sendMessage(3L, 10L, new ChatMessageSendRequest("hello")));
		mine.leave();
		assertThrows(CustomException.class, () -> service.sendMessage(1L, 10L, new ChatMessageSendRequest("hello")));
		verifyNoInteractions(messages);
	}

	@Test
	void validatesMessageAndReturnsPersistedBroadcast() {
		assertThrows(CustomException.class, () -> service.sendMessage(1L, 10L, new ChatMessageSendRequest(" ")));
		assertThrows(CustomException.class, () -> service.sendMessage(1L, 10L, new ChatMessageSendRequest("a".repeat(2001))));
		when(messages.save(any())).thenAnswer(call -> {
			ChatMessage message = call.getArgument(0);
			ReflectionTestUtils.setField(message, "messageId", 30L);
			ReflectionTestUtils.setField(message, "sentAt", sentAt);
			return message;
		});
		var response = service.sendMessage(1L, 10L, new ChatMessageSendRequest("hello"));
		assertEquals(1L, response.senderId());
		assertEquals(10L, response.roomId());
		assertEquals(30L, response.id());
		assertEquals(sentAt, response.sentAt());
		assertFalse(response.read());
	}

	@Test
	void messageReadStateUsesRecipientAndIncludesBoundary() {
		mine.markRead(sentAt);
		theirs.markRead(sentAt.minusSeconds(1));
		when(messages.findAllByChatRoom_ChatRoomId(eq(10L), any()))
				.thenReturn(new PageImpl<>(List.of(message(me), message(other))));
		var response = service.getMessages(1L, 10L, null);
		assertEquals("ME", response.content().get(0).senderType());
		assertFalse(response.content().get(0).read());
		assertEquals("OTHER", response.content().get(1).senderType());
		assertTrue(response.content().get(1).read());
	}

	@Test
	void searchAndPaginationUseRepository() {
		when(messages.findAllByChatRoom_ChatRoomIdAndContentContaining(eq(10L), eq("청소"), any()))
				.thenReturn(new PageImpl<>(List.of()));
		service.getMessages(1L, 10L, new ChatMessageListRequest(0, 50, " 청소 "));
		verify(messages).findAllByChatRoom_ChatRoomIdAndContentContaining(eq(10L), eq("청소"),
				argThat(page -> page.getPageSize() == 50 && page.getSort().getOrderFor("sentAt").isDescending()));
		assertThrows(CustomException.class,
				() -> service.getMessages(1L, 10L, new ChatMessageListRequest(-1, 50, null)));
	}

	@Test
	void marksOnlyLatestIncomingMessageAndDoesNotMoveBackwards() {
		when(messages.findFirstByChatRoom_ChatRoomIdAndSender_UserIdNotOrderBySentAtDescMessageIdDesc(10L, 1L))
				.thenReturn(Optional.of(message(other)));
		service.markRead(1L, 10L);
		assertEquals(sentAt, mine.getLastReadAt());
		mine.markRead(sentAt.plusSeconds(10));
		service.markRead(1L, 10L);
		assertEquals(sentAt.plusSeconds(10), mine.getLastReadAt());
	}

	@Test
	void pinLimitRejectsNewPinButAllowsExistingPinAndPartialSettings() {
		when(members.countByUser_UserIdAndPinnedTrueAndLeftFalse(1L)).thenReturn(2L);
		assertThrows(CustomException.class,
				() -> service.updateSettings(1L, 10L, new ChatRoomSettingsRequest(true, true)));
		assertFalse(mine.isMuted());
		mine.changeSettings(true, false);
		service.updateSettings(1L, 10L, new ChatRoomSettingsRequest(true, true));
		service.updateSettings(1L, 10L, new ChatRoomSettingsRequest(null, false));
		assertTrue(mine.isPinned());
		assertFalse(mine.isMuted());
	}

	@Test
	void leavingKeepsOtherMemberAndMessages() {
		service.leaveRoom(1L, 10L);
		assertTrue(mine.isLeft());
		assertNotNull(mine.getLeftAt());
		assertFalse(theirs.isLeft());
		verifyNoInteractions(rooms, messages);
	}

	@Test
	void existingRoomIsReusedAndLeftRequesterIsRejected() {
		when(rooms.findByRoomKey("ROOMMATE:1:2")).thenReturn(Optional.of(room));
		var request = new ChatRoomCreateRequest(2L, ChatRoomCategory.ROOMMATE, null, null, null);
		assertEquals(10L, service.createOrGetRoom(1L, request).id());
		verify(rooms, never()).save(any());
		mine.leave();
		assertThrows(CustomException.class, () -> service.createOrGetRoom(1L, request));
	}

	@Test
	void createsRoomWithBothMembers() {
		when(rooms.save(any())).thenReturn(room);
		assertEquals(10L, service.createOrGetRoom(1L,
				new ChatRoomCreateRequest(2L, ChatRoomCategory.ROOMMATE, null, null, null)).id());
		verify(members).saveAll(argThat(saved -> {
			var iterator = saved.iterator();
			return iterator.next().getUser().equals(me) && iterator.next().getUser().equals(other) && !iterator.hasNext();
		}));
		verify(transactions).commit(any());
	}

	@Test
	void uniqueCollisionIsRolledBackBeforeExistingRoomLookup() {
		when(rooms.findByRoomKey("ROOMMATE:1:2")).thenReturn(Optional.empty(), Optional.of(room));
		when(rooms.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
		assertEquals(10L, service.createOrGetRoom(1L,
				new ChatRoomCreateRequest(2L, ChatRoomCategory.ROOMMATE, null, null, null)).id());
		var order = inOrder(transactions, rooms);
		order.verify(transactions).getTransaction(any());
		order.verify(rooms).findByRoomKey("ROOMMATE:1:2");
		order.verify(rooms).save(any());
		order.verify(transactions).rollback(any());
		order.verify(transactions).getTransaction(any());
		order.verify(rooms).findByRoomKey("ROOMMATE:1:2");
		order.verify(transactions).commit(any());
	}

	@Test
	void rejectsSelfAndInvalidRoommateSource() {
		assertThrows(CustomException.class, () -> service.createOrGetRoom(1L,
				new ChatRoomCreateRequest(1L, ChatRoomCategory.ROOMMATE, null, null, null)));
		assertThrows(CustomException.class, () -> service.createOrGetRoom(1L,
				new ChatRoomCreateRequest(2L, ChatRoomCategory.ROOMMATE, 3L, null, "POST")));
		verify(rooms, never()).save(any());
	}

	@Test
	void roomListSearchAndUnreadCountUseCurrentMember() {
		mine.markRead(sentAt);
		when(members.findAllByUser_UserIdAndLeftFalse(1L)).thenReturn(List.of(mine));
		when(messages.countUnreadMessages(10L, 1L, sentAt)).thenReturn(3L);
		assertEquals(3L, service.getRooms(1L, new ChatRoomListRequest("ALL", "상대")).get(0).unreadCount());
		assertTrue(service.getRooms(1L, new ChatRoomListRequest(null, "없는 이름")).isEmpty());
	}

	@Test
	void communityPostMustBelongToTargetAuthor() {
		CommunityPost post = mock(CommunityPost.class);
		when(post.getAuthor()).thenReturn(me);
		when(posts.findByPostIdAndStatus(31L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
		var request = new ChatRoomCreateRequest(2L, ChatRoomCategory.COMMUNITY, 31L, null, "POST");
		assertThrows(CustomException.class, () -> service.createOrGetRoom(1L, request));
		verify(rooms, never()).save(any());
		when(post.getAuthor()).thenReturn(other);
		when(rooms.save(any())).thenReturn(room);
		service.createOrGetRoom(1L, request);
		verify(rooms).save(argThat(created -> created.getSourcePost() == post && created.getSourceComment() == null));
	}

	@Test
	void communityCommentRequiresMatchingPostAndAuthor() {
		CommunityPost post = mock(CommunityPost.class);
		when(post.getPostId()).thenReturn(31L);
		CommunityPost differentPost = mock(CommunityPost.class);
		when(differentPost.getPostId()).thenReturn(99L);
		CommunityComment comment = mock(CommunityComment.class);
		when(comment.getPost()).thenReturn(differentPost);
		when(comment.getAuthor()).thenReturn(other);
		when(posts.findByPostIdAndStatus(31L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
		when(comments.findByCommentIdAndStatus(52L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(comment));
		var request = new ChatRoomCreateRequest(2L, ChatRoomCategory.COMMUNITY, 31L, 52L, "COMMENT");
		assertThrows(CustomException.class, () -> service.createOrGetRoom(1L, request));
		when(comment.getPost()).thenReturn(post);
		when(comment.getAuthor()).thenReturn(me);
		assertThrows(CustomException.class, () -> service.createOrGetRoom(1L, request));
		verify(rooms, never()).save(any());
		when(comment.getAuthor()).thenReturn(other);
		when(rooms.save(any())).thenAnswer(call -> {
			ChatRoom created = call.getArgument(0);
			ReflectionTestUtils.setField(created, "chatRoomId", 10L);
			return created;
		});
		assertEquals(31L, service.createOrGetRoom(1L, request).sourcePost());
		verify(rooms).save(argThat(created -> created.getSourcePost() == null && created.getSourceComment() == comment));
	}

	@Test
	void listSortsPinnedFirstThenLatestMessageWithEmptyRoomsLast() {
		ChatRoom recent = new ChatRoom(1L, 2L, ChatRoomCategory.COMMUNITY, mock(CommunityPost.class), null);
		ReflectionTestUtils.setField(recent, "chatRoomId", 11L);
		ChatRoom empty = new ChatRoom(1L, 2L, ChatRoomCategory.COMMUNITY, mock(CommunityPost.class), null);
		ReflectionTestUtils.setField(empty, "chatRoomId", 12L);
		ChatRoomMember recentMember = new ChatRoomMember(recent, me);
		ChatRoomMember emptyMember = new ChatRoomMember(empty, me);
		when(members.findAllByChatRoom_ChatRoomId(11L)).thenReturn(List.of(recentMember, new ChatRoomMember(recent, other)));
		when(members.findAllByChatRoom_ChatRoomId(12L)).thenReturn(List.of(emptyMember, new ChatRoomMember(empty, other)));
		when(members.findAllByUser_UserIdAndLeftFalse(1L)).thenReturn(List.of(emptyMember, mine, recentMember));
		ChatMessage latest = message(other);
		ReflectionTestUtils.setField(latest, "sentAt", sentAt.plusSeconds(1));
		when(messages.findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(10L)).thenReturn(Optional.of(message(other)));
		when(messages.findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(11L)).thenReturn(Optional.of(latest));
		assertEquals(List.of(11L, 10L, 12L), service.getRooms(1L, null).stream().map(ChatRoomResponse::id).toList());
		mine.changeSettings(true, null);
		assertEquals(List.of(10L, 11L, 12L), service.getRooms(1L, null).stream().map(ChatRoomResponse::id).toList());
	}

	private ChatMessage message(User sender) {
		ChatMessage message = new ChatMessage(room, sender, "hello");
		ReflectionTestUtils.setField(message, "sentAt", sentAt);
		return message;
	}
}
