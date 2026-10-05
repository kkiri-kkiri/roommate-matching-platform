package com.kkirikkiri.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.repository.*;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.entity.RoommateMatch;
import com.kkirikkiri.matching.repository.RoommateMatchRepository;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock UserRepository userRepository;
    @Mock UserProfileRepository userProfileRepository;
    @Mock ProfileSurveyRepository profileSurveyRepository;
    @Mock RoommateMatchRepository roommateMatchRepository;
    @Mock CommunityPostRepository communityPostRepository;
    @Mock CommunityCommentRepository communityCommentRepository;
    @Mock Validator validator;
    @Mock ConstraintViolation<Object> violation;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks ChatService service;

    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 1, 1, 12, 0);
    private User user;
    private User target;
    private ChatRoom room;
    private ChatRoomMember member;
    private ChatRoomMember counterpart;

    @BeforeEach
    void setUp() {
        user = user(7L);
        target = user(8L);
        room = room(10L);
        member = new ChatRoomMember(room, user);
        counterpart = new ChatRoomMember(room, target);
    }

    @Test
    void createsRoomAndBothParticipantsTogether() {
        // given
        stubUsers();
        stubNewRoomSave();
        stubResponse(member, counterpart, "Target");
        // when
        var result = service.createOrGetRoom(7L, roommateRequest());
        // then
        assertEquals(10L, result.id());
        var rooms = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(rooms.capture());
        assertEquals("ROOMMATE:7:8", rooms.getValue().getRoomKey());
        assertNull(rooms.getValue().getSourcePost());
        assertNull(rooms.getValue().getSourceComment());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatRoomMember>> participants = ArgumentCaptor.forClass(List.class);
        verify(chatRoomMemberRepository).saveAll(participants.capture());
        assertEquals(List.of(7L, 8L), participants.getValue().stream().map(m -> m.getUser().getUserId()).toList());
        assertTrue(participants.getValue().stream().allMatch(m -> m.getChatRoom() == room && !m.isLeft()));
    }

    @Test
    void reusesExistingRoomWithoutSavingDuplicates() {
        // given
        stubUsers();
        when(chatRoomRepository.findByRoomKey("ROOMMATE:7:8")).thenReturn(Optional.of(room));
        stubActiveMember();
        stubResponse(member, counterpart, "Target");
        // when
        var result = service.createOrGetRoom(7L, roommateRequest());
        // then
        assertEquals(10L, result.id());
        verify(chatRoomRepository, never()).save(any());
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @Test
    void readsConcurrentWinnerAfterRollingBackDuplicateCreation() {
        // given
        stubUsers();
        var creation = new SimpleTransactionStatus();
        var recovery = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> {
            TransactionDefinition definition = invocation.getArgument(0);
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            return definition.isReadOnly() ? recovery : creation;
        });
        when(chatRoomRepository.findByRoomKey("ROOMMATE:7:8"))
                .thenReturn(Optional.empty(), Optional.of(room));
        when(chatRoomRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate room key"));
        stubActiveMember();
        stubResponse(member, counterpart, "Target");
        // when
        var result = service.createOrGetRoom(7L, roommateRequest());
        // then
        assertEquals(10L, result.id());
        var order = inOrder(transactionManager, chatRoomRepository);
        order.verify(transactionManager).getTransaction(any());
        order.verify(chatRoomRepository).findByRoomKey("ROOMMATE:7:8");
        order.verify(chatRoomRepository).save(any());
        order.verify(transactionManager).rollback(creation);
        order.verify(transactionManager).getTransaction(any());
        order.verify(chatRoomRepository).findByRoomKey("ROOMMATE:7:8");
        order.verify(transactionManager).commit(recovery);
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @Test
    void rejectsIntegrityFailureWhenNoConcurrentRoomExists() {
        // given
        stubUsers();
        when(chatRoomRepository.save(any())).thenThrow(new DataIntegrityViolationException("other constraint"));
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L, roommateRequest()));
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @Test
    void forbidsRejoiningRoomAfterLeaving() {
        // given
        stubUsers();
        member.leave();
        stubActiveMember();
        when(chatRoomRepository.findByRoomKey("ROOMMATE:7:8")).thenReturn(Optional.of(room));
        // when / then
        assertError(ErrorCode.FORBIDDEN, () -> service.createOrGetRoom(7L, roommateRequest()));
        assertTrue(member.isLeft());
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 7})
    void rejectsInvalidTargetOrSelfChatBeforeStartingTransaction(Long targetId) {
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L,
                new ChatRoomCreateRequest(targetId, ChatRoomCategory.ROOMMATE, null, null, null)));
        verifyNoInteractions(transactionManager, chatRoomRepository);
    }

    @ParameterizedTest
    @CsvSource({"7,missing,UNAUTHORIZED", "7,SUSPENDED,UNAUTHORIZED", "7,WITHDRAWN,UNAUTHORIZED",
            "8,missing,INVALID_REQUEST", "8,SUSPENDED,INVALID_REQUEST", "8,WITHDRAWN,INVALID_REQUEST"})
    void rejectsMissingOrInactiveParticipants(long invalidId, String status, ErrorCode expected) {
        // given
        if (invalidId == 8) when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        if (!status.equals("missing")) {
            User invalid = invalidId == 7 ? user : target;
            invalid.changeStatus(UserStatus.valueOf(status));
            when(userRepository.findById(invalidId)).thenReturn(Optional.of(invalid));
        }
        // when / then
        assertError(expected, () -> service.createOrGetRoom(7L, roommateRequest()));
        verify(chatRoomRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"POST,20,", ",20,", ",,30"})
    void rejectsCommunitySourceOnRoommateChat(String source, Long postId, Long commentId) {
        // given
        stubUsers();
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createOrGetRoom(7L,
                new ChatRoomCreateRequest(8L, ChatRoomCategory.ROOMMATE, postId, commentId, source)));
        verify(chatRoomRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void createsCommunityRoomWithOnlySelectedSource(boolean fromComment) {
        // given
        stubUsers();
        CommunityPost post = post(20L, fromComment ? user : target);
        CommunityComment comment = new CommunityComment(post, target, null, "comment");
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
        if (fromComment) when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));
        when(chatRoomRepository.save(any())).thenAnswer(invocation -> {
            ChatRoom saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "chatRoomId", 10L);
            return saved;
        });
        stubResponse(member, counterpart, "Target");
        // when
        var result = service.createOrGetRoom(7L, communityRequest(fromComment));
        // then
        assertEquals(20L, result.sourcePost());
        assertEquals(ChatRoomCategory.COMMUNITY, result.category());
        var captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(captor.capture());
        assertSame(fromComment ? null : post, captor.getValue().getSourcePost());
        assertSame(fromComment ? comment : null, captor.getValue().getSourceComment());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missingPost", "wrongPostAuthor", "extraComment", "missingComment", "wrongCommentAuthor", "wrongCommentPost"})
    void rejectsInvalidCommunitySourceBeforeSaving(String scenario) {
        // given
        stubUsers();
        boolean fromComment = scenario.contains("Comment") && !scenario.equals("extraComment");
        if (!scenario.equals("missingPost")) {
            CommunityPost post = post(20L, scenario.equals("wrongPostAuthor") ? user : target);
            when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
            if (fromComment && !scenario.equals("missingComment")) {
                CommunityComment comment = new CommunityComment(
                        scenario.equals("wrongCommentPost") ? post(21L, target) : post,
                        scenario.equals("wrongCommentAuthor") ? user : target, null, "comment");
                when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE))
                        .thenReturn(Optional.of(comment));
            }
        }
        var request = scenario.equals("extraComment")
                ? new ChatRoomCreateRequest(8L, ChatRoomCategory.COMMUNITY, 20L, 30L, "POST")
                : communityRequest(fromComment);
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L, request));
        verify(chatRoomRepository, never()).save(any());
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @CsvSource({",,POST", "0,,POST", "-1,,POST", "20,,COMMENT", "20,0,COMMENT", "20,-1,COMMENT"})
    void rejectsMissingOrNonPositiveCommunitySourceIds(Long postId, Long commentId, String source) {
        // given
        stubUsers();
        if (postId != null && postId > 0) {
            when(communityPostRepository.findByPostIdAndStatus(postId, CommunityContentStatus.ACTIVE))
                    .thenReturn(Optional.of(post(postId, target)));
        }
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L,
                new ChatRoomCreateRequest(8L, ChatRoomCategory.COMMUNITY, postId, commentId, source)));
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void requiresSourceTypeForCommunityChat() {
        // given
        stubUsers();
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post(20L, target)));
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createOrGetRoom(7L,
                new ChatRoomCreateRequest(8L, ChatRoomCategory.COMMUNITY, 20L, null, null)));
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void reusesSameRoomWhenOtherParticipantStartsChat() {
        // given
        stubUsers();
        when(chatRoomRepository.findByRoomKey("ROOMMATE:7:8")).thenReturn(Optional.of(room));
        when(chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(10L, 8L))
                .thenReturn(Optional.of(counterpart));
        stubResponse(counterpart, member, "Original Sender");
        // when
        var result = service.createOrGetRoom(8L,
                new ChatRoomCreateRequest(7L, ChatRoomCategory.ROOMMATE, null, null, null));
        // then
        assertEquals(10L, result.id());
        assertEquals(7L, result.counterpart().id());
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void sortsActiveRoomsByPinnedThenLatestMessageThenDescendingId() {
        // given
        ChatRoomMember pinned = listMember(11L, true, SENT_AT.minusDays(1));
        ChatRoomMember recent = listMember(12L, false, SENT_AT);
        ChatRoomMember tied = listMember(13L, false, SENT_AT);
        ChatRoomMember empty = listMember(14L, false, null);
        when(chatMessageRepository.findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(14L))
                .thenReturn(Optional.empty());
        ChatRoom closed = room(15L);
        ReflectionTestUtils.setField(closed, "status", ChatRoomStatus.CLOSED);
        when(chatRoomMemberRepository.findAllByUser_UserIdAndLeftFalse(7L))
                .thenReturn(List.of(empty, recent, new ChatRoomMember(closed, user), tied, pinned));
        // when
        var result = service.getRooms(7L, null);
        // then
        assertEquals(List.of(11L, 13L, 12L, 14L), result.stream().map(ChatRoomResponse::id).toList());
        assertNull(result.getLast().lastMessage());
        verify(chatRoomMemberRepository, never()).findAllByChatRoom_ChatRoomId(15L);
    }

    @Test
    void filtersCategoryAndMatchesTrimmedNicknameIgnoringCase() {
        // given
        ChatRoomMember match = listMember(11L, false, null);
        ChatRoomMember other = new ChatRoomMember(room(12L), user);
        stubResponse(other, new ChatRoomMember(other.getChatRoom(), user(9L)), "Someone Else");
        when(chatRoomMemberRepository.findAllByUser_UserIdAndLeftFalseAndChatRoom_Category(7L, ChatRoomCategory.ROOMMATE))
                .thenReturn(List.of(other, match));
        // when
        var result = service.getRooms(7L, new ChatRoomListRequest("ROOMMATE", "  tArGeT  "));
        // then
        assertEquals(List.of(11L), result.stream().map(ChatRoomResponse::id).toList());
        verify(chatRoomMemberRepository, never()).findAllByUser_UserIdAndLeftFalse(any());
    }

    @Test
    void returnsEmptyRoomListForAllCategory() {
        // when / then
        assertTrue(service.getRooms(7L, new ChatRoomListRequest("ALL", " ")).isEmpty());
        verify(chatRoomMemberRepository).findAllByUser_UserIdAndLeftFalse(7L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void includesOnlyCompletedSurveyTagsAndOptionalMatchScore(boolean completed) {
        // given
        when(chatRoomMemberRepository.findAllByUser_UserIdAndLeftFalse(7L)).thenReturn(List.of(member));
        stubResponse(member, counterpart, "Target");
        ProfileSurvey survey = mock(ProfileSurvey.class);
        when(survey.isCompleted()).thenReturn(completed);
        when(profileSurveyRepository.findByUser_UserId(8L)).thenReturn(Optional.of(survey));
        if (completed) {
            when(survey.getSmoking()).thenReturn("non-smoker");
            when(survey.getCleaningFrequency()).thenReturn("daily");
            RoommateMatch match = mock(RoommateMatch.class);
            when(match.getMatchScore()).thenReturn(87);
            when(roommateMatchRepository.findByRequesterUser_UserIdAndTargetUser_UserId(7L, 8L))
                    .thenReturn(Optional.of(match));
        }
        member.markRead(SENT_AT);
        when(chatMessageRepository.countUnreadMessages(10L, 7L, SENT_AT)).thenReturn(3L);
        // when
        var result = service.getRooms(7L, null).getFirst();
        // then
        assertEquals(completed ? List.of("non-smoker", "daily") : List.of(), result.counterpart().tags());
        assertEquals(completed ? Integer.valueOf(87) : null, result.counterpart().matchScore());
        assertEquals(3, result.unreadCount());
    }

    @Test
    void rejectsRoomListingWhenCounterpartProfileIsMissing() {
        // given
        when(chatRoomMemberRepository.findAllByUser_UserIdAndLeftFalse(7L)).thenReturn(List.of(member));
        stubCounterpart();
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.getRooms(7L, null));
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "1,true"})
    void determinesReadStatusUsingRecipientsTimestampIncludingEquality(int seconds, boolean read) {
        // given
        stubActiveMember();
        stubCounterpart();
        counterpart.markRead(SENT_AT.plusSeconds(seconds));
        member.markRead(SENT_AT.minusSeconds(1));
        PageRequest pageable = messagePage(0, 50);
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, pageable)).thenReturn(
                new PageImpl<>(List.of(message(room, user, SENT_AT), message(room, target, SENT_AT)), pageable, 2));
        // when
        var result = service.getMessages(7L, 10L, null);
        // then
        assertEquals(0, result.page());
        assertEquals(50, result.size());
        assertEquals(2, result.totalElements());
        assertEquals("ME", result.content().getFirst().senderType());
        assertEquals(read, result.content().getFirst().read());
        assertEquals("OTHER", result.content().getLast().senderType());
        assertFalse(result.content().getLast().read());
    }

    @Test
    void searchesTrimmedMessageContentWithRequestedPage() {
        // given
        stubActiveMember();
        stubCounterpart();
        PageRequest pageable = messagePage(1, 2);
        when(chatMessageRepository.findAllByChatRoom_ChatRoomIdAndContentContaining(10L, "Hello", pageable))
                .thenReturn(new PageImpl<>(List.of(message(room, target, SENT_AT)), pageable, 3));
        // when
        var result = service.getMessages(7L, 10L, new ChatMessageListRequest(1, 2, "  Hello  "));
        // then
        assertEquals(1, result.page());
        assertEquals(3, result.totalElements());
        assertFalse(result.content().getFirst().read());
        verify(chatMessageRepository, never()).findAllByChatRoom_ChatRoomId(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"-1,50", "0,0", "0,-1", "2147483647,2"})
    void rejectsInvalidPaginationBeforeQueryingMessages(int page, int size) {
        // given
        stubActiveMember();
        stubCounterpart();
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.getMessages(7L, 10L, new ChatMessageListRequest(page, size, null)));
        verifyNoInteractions(chatMessageRepository);
    }

    @Test
    void acceptsMaximumOffsetAndTreatsBlankSearchAsNoFilter() {
        // given
        stubActiveMember();
        stubCounterpart();
        PageRequest pageable = messagePage(Integer.MAX_VALUE, 1);
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, pageable)).thenReturn(Page.empty(pageable));
        // when
        var result = service.getMessages(7L, 10L, new ChatMessageListRequest(Integer.MAX_VALUE, 1, "  "));
        // then
        assertTrue(result.content().isEmpty());
        assertEquals(Integer.MAX_VALUE, result.page());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesMessageWithAuthenticatedSenderAndRecipientReadStatus(boolean read) {
        // given
        stubActiveMember();
        stubCounterpart();
        if (read) counterpart.markRead(SENT_AT);
        when(chatMessageRepository.save(any())).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            assertSame(room, saved.getChatRoom());
            assertSame(user, saved.getSender());
            ReflectionTestUtils.setField(saved, "messageId", 42L);
            ReflectionTestUtils.setField(saved, "sentAt", SENT_AT);
            return saved;
        });
        // when
        var result = service.sendMessage(7L, 10L, new ChatMessageSendRequest(" hello "));
        // then
        assertEquals(42L, result.id());
        assertEquals(10L, result.roomId());
        assertEquals(7L, result.senderId());
        assertEquals(" hello ", result.text());
        assertEquals(read, result.read());
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "one", "three", "missingSelf", "duplicateSelf"})
    void rejectsMalformedParticipantSetBeforeSavingMessage(String scenario) {
        // given
        stubActiveMember();
        List<ChatRoomMember> members = switch (scenario) {
            case "empty" -> List.of();
            case "one" -> List.of(member);
            case "three" -> List.of(member, counterpart, new ChatRoomMember(room, user(9L)));
            case "missingSelf" -> List.of(counterpart, new ChatRoomMember(room, user(9L)));
            default -> List.of(member, member);
        };
        when(chatRoomMemberRepository.findAllByChatRoom_ChatRoomId(10L)).thenReturn(members);
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.sendMessage(7L, 10L, new ChatMessageSendRequest("hello")));
        verify(chatMessageRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"-1", "0", "1"})
    void advancesReadTimestampWithoutMovingBackwards(int seconds) {
        // given
        stubActiveMember();
        member.markRead(SENT_AT);
        when(chatMessageRepository.findFirstByChatRoom_ChatRoomIdAndSender_UserIdNotOrderBySentAtDescMessageIdDesc(10L, 7L))
                .thenReturn(Optional.of(message(room, target, SENT_AT.plusSeconds(seconds))));
        // when
        service.markRead(7L, 10L);
        // then
        assertEquals(seconds > 0 ? SENT_AT.plusSeconds(seconds) : SENT_AT, member.getLastReadAt());
    }

    @Test
    void initializesReadTimestampFromLatestIncomingMessage() {
        // given
        stubActiveMember();
        when(chatMessageRepository.findFirstByChatRoom_ChatRoomIdAndSender_UserIdNotOrderBySentAtDescMessageIdDesc(10L, 7L))
                .thenReturn(Optional.of(message(room, target, SENT_AT)));
        // when
        service.markRead(7L, 10L);
        // then
        assertEquals(SENT_AT, member.getLastReadAt());
        assertNull(counterpart.getLastReadAt());
    }

    @Test
    void leavesReadTimestampUnchangedWhenNoIncomingMessageExists() {
        // given
        stubActiveMember();
        // when
        service.markRead(7L, 10L);
        // then
        assertNull(member.getLastReadAt());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2, 3})
    void enforcesTwoPinnedRoomsBeforeChangingEitherSetting(long count) {
        // given
        stubActiveMember();
        when(chatRoomMemberRepository.countByUser_UserIdAndPinnedTrueAndLeftFalse(7L)).thenReturn(count);
        var request = new ChatRoomSettingsRequest(true, true);
        // when / then
        if (count < 2) {
            service.updateSettings(7L, 10L, request);
            assertTrue(member.isPinned());
            assertTrue(member.isMuted());
        } else {
            assertError(ErrorCode.INVALID_REQUEST, () -> service.updateSettings(7L, 10L, request));
            assertFalse(member.isPinned());
            assertFalse(member.isMuted());
        }
    }

    @ParameterizedTest
    @CsvSource({"true,", "false,", ",false", ","})
    void updatesExistingSettingsWithoutCountingPinsAndPreservesOmittedValues(Boolean pinned, Boolean muted) {
        // given
        stubActiveMember();
        member.changeSettings(true, true);
        // when
        service.updateSettings(7L, 10L, new ChatRoomSettingsRequest(pinned, muted));
        // then
        assertEquals(pinned == null || pinned, member.isPinned());
        assertEquals(muted == null || muted, member.isMuted());
        verify(chatRoomMemberRepository, never()).countByUser_UserIdAndPinnedTrueAndLeftFalse(any());
    }

    @Test
    void leavesOnlyCurrentMembershipAndForbidsFurtherAccess() {
        // given
        stubActiveMember();
        // when
        service.leaveRoom(7L, 10L);
        // then
        assertTrue(member.isLeft());
        assertNotNull(member.getLeftAt());
        assertFalse(counterpart.isLeft());
        assertEquals(ChatRoomStatus.ACTIVE, room.getStatus());
        assertError(ErrorCode.FORBIDDEN, () -> service.validateActiveMember(7L, 10L));
        verify(chatRoomRepository, never()).delete(any(ChatRoom.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "left", "closed"})
    void forbidsRoomOperationsForInactiveMembership(String state) {
        // given
        if (!state.equals("missing")) stubActiveMember();
        if (state.equals("left")) member.leave();
        if (state.equals("closed")) ReflectionTestUtils.setField(room, "status", ChatRoomStatus.CLOSED);
        // when / then
        assertError(ErrorCode.FORBIDDEN, () -> service.validateActiveMember(7L, 10L));
        assertError(ErrorCode.FORBIDDEN, () -> service.getMessages(7L, 10L, null));
        assertError(ErrorCode.FORBIDDEN, () -> service.sendMessage(7L, 10L, new ChatMessageSendRequest("hello")));
        assertError(ErrorCode.FORBIDDEN, () -> service.markRead(7L, 10L));
        assertError(ErrorCode.FORBIDDEN, () -> service.updateSettings(7L, 10L, new ChatRoomSettingsRequest(true, true)));
        assertError(ErrorCode.FORBIDDEN, () -> service.leaveRoom(7L, 10L));
        verifyNoInteractions(chatMessageRepository);
        assertFalse(member.isPinned());
        assertNull(member.getLastReadAt());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsUnauthenticatedUsersAndInvalidRoomIds(Long id) {
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.getRooms(id, null));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createOrGetRoom(id, roommateRequest()));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.validateActiveMember(id, 10L));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.validateActiveMember(7L, id));
        verifyNoInteractions(chatRoomRepository, chatRoomMemberRepository, transactionManager);
    }

    @Test
    void rejectsNullMutationRequestsBeforeRepositoryAccess() {
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createOrGetRoom(7L, null));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.sendMessage(7L, 10L, null));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.updateSettings(7L, 10L, null));
        verifyNoInteractions(chatRoomRepository, chatRoomMemberRepository, chatMessageRepository);
    }

    @Test
    void rejectsConstraintViolationsBeforeReadingOrSaving() {
        // given
        when(validator.validate(any())).thenReturn(Set.of(violation));
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.getRooms(7L, new ChatRoomListRequest("invalid", null)));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createOrGetRoom(7L, roommateRequest()));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.sendMessage(7L, 10L, new ChatMessageSendRequest(" ")));
        verifyNoInteractions(chatRoomRepository, chatRoomMemberRepository, chatMessageRepository);
    }

    private User user(Long id) {
        User result = new User("encoded", id + "@example.com");
        ReflectionTestUtils.setField(result, "userId", id);
        return result;
    }

    private ChatRoom room(Long id) {
        ChatRoom result = new ChatRoom(7L, 8L, ChatRoomCategory.ROOMMATE, null, null);
        ReflectionTestUtils.setField(result, "chatRoomId", id);
        return result;
    }

    private CommunityPost post(Long id, User author) {
        CommunityPost post = new CommunityPost(author, null, "자유게시판", "title", "content");
        ReflectionTestUtils.setField(post, "postId", id);
        return post;
    }

    private ChatMessage message(ChatRoom room, User sender, LocalDateTime sentAt) {
        ChatMessage message = new ChatMessage(room, sender, "hello");
        ReflectionTestUtils.setField(message, "sentAt", sentAt);
        return message;
    }

    private ChatRoomCreateRequest roommateRequest() {
        return new ChatRoomCreateRequest(8L, ChatRoomCategory.ROOMMATE, null, null, null);
    }

    private ChatRoomCreateRequest communityRequest(boolean fromComment) {
        return new ChatRoomCreateRequest(8L, ChatRoomCategory.COMMUNITY, 20L,
                fromComment ? 30L : null, fromComment ? "COMMENT" : "POST");
    }

    private void stubUsers() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.findById(8L)).thenReturn(Optional.of(target));
    }

    private void stubNewRoomSave() {
        when(chatRoomRepository.save(any())).thenReturn(room);
    }

    private void stubActiveMember() {
        when(chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(10L, 7L)).thenReturn(Optional.of(member));
    }

    private void stubCounterpart() {
        when(chatRoomMemberRepository.findAllByChatRoom_ChatRoomId(10L)).thenReturn(List.of(member, counterpart));
    }

    private void stubResponse(ChatRoomMember current, ChatRoomMember other, String nickname) {
        when(chatRoomMemberRepository.findAllByChatRoom_ChatRoomId(current.getChatRoom().getChatRoomId()))
                .thenReturn(List.of(current, other));
        UserProfile profile = new UserProfile(other.getUser(), nickname, "F", mock(Dormitory.class),
                mock(Faculty.class), null, (short) 2000, "20", "1", "INTJ");
        when(userProfileRepository.findByUser_UserId(other.getUser().getUserId())).thenReturn(Optional.of(profile));
    }

    private ChatRoomMember listMember(Long id, boolean pinned, LocalDateTime sentAt) {
        ChatRoom listRoom = room(id);
        ChatRoomMember current = new ChatRoomMember(listRoom, user);
        current.changeSettings(pinned, null);
        stubResponse(current, new ChatRoomMember(listRoom, target), "Target");
        if (sentAt != null) when(chatMessageRepository.findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(id))
                .thenReturn(Optional.of(message(listRoom, target, sentAt)));
        return current;
    }

    private PageRequest messagePage(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt", "messageId"));
    }

    private void assertError(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(CustomException.class, action).getErrorCode());
    }
}
