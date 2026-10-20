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
import org.springframework.dao.DataAccessResourceFailureException;
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
        lenient().when(userRepository.findByIdForAccountUpdate(7L)).thenReturn(Optional.of(user));
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
            assertFalse(definition.isReadOnly());
            return creation;
        }).thenReturn(recovery);
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
        boolean fromComment = scenario.contains("Comment") && !scenario.equals("extraComment");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
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
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
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
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
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
        // DB ordering/filtering is verified with real JPA in ChatRoomReadPerformanceIntegrationTest.
        // The service must preserve the page order and batch only its counterpart IDs.
        stubPage(7L, null, null, List.of(row(11L, 8L, false, true, SENT_AT.minusDays(1)),
                row(13L, 8L, false, false, SENT_AT), row(12L, 8L, false, false, SENT_AT),
                row(14L, 8L, false, false, null)));
        stubPageProfile(target, "Target");
        // when
        var result = service.getRooms(7L, null).content();
        // then
        assertEquals(List.of(11L, 13L, 12L, 14L), result.stream().map(ChatRoomResponse::id).toList());
        assertNull(result.getLast().lastMessage());
        verify(chatRoomMemberRepository, never()).findAllByChatRoom_ChatRoomId(15L);
    }

    @Test
    void filtersCategoryAndMatchesTrimmedNicknameIgnoringCase() {
        stubPage(7L, ChatRoomCategory.ROOMMATE, "%target%", List.of(row(11L, 8L, false, false, null)));
        stubPageProfile(target, "Target");
        // when
        var result = service.getRooms(7L, new ChatRoomListRequest("ROOMMATE", "  tArGeT  ")).content();
        // then
        assertEquals(List.of(11L), result.stream().map(ChatRoomResponse::id).toList());
        verify(chatRoomMemberRepository, never()).findAllByUser_UserIdAndLeftFalse(any());
    }

    @Test
    void returnsEmptyRoomListForAllCategory() {
        // when / then
        stubPage(7L, null, null, List.of());
        assertTrue(service.getRooms(7L, new ChatRoomListRequest("ALL", " ")).content().isEmpty());
        verifyNoInteractions(chatRoomMemberRepository, chatMessageRepository, userProfileRepository,
                profileSurveyRepository, roommateMatchRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void includesOnlyCompletedSurveyTagsAndOptionalMatchScore(boolean completed) {
        // given
        stubPage(7L, null, null, List.of(row(10L, 8L, false, false, null)));
        stubPageProfile(target, "Target");
        ProfileSurvey survey = mock(ProfileSurvey.class);
        when(survey.getUser()).thenReturn(target);
        when(survey.isCompleted()).thenReturn(completed);
        when(profileSurveyRepository.findAllByUser_UserIdIn(List.of(8L))).thenReturn(List.of(survey));
        if (completed) {
            when(survey.getSmoking()).thenReturn("non-smoker");
            when(survey.getCleaningFrequency()).thenReturn("daily");
            RoommateMatch match = mock(RoommateMatch.class);
            when(match.getTargetUser()).thenReturn(target);
            when(match.getMatchScore()).thenReturn(87);
            when(roommateMatchRepository.findAllByRequesterUser_UserIdAndTargetUser_UserIdIn(7L, List.of(8L)))
                    .thenReturn(List.of(match));
        }
        member.markRead(SENT_AT);
        when(chatMessageRepository.countUnreadForRooms(List.of(10L), 7L)).thenReturn(List.of(new ChatUnreadCount(10L, 3)));
        // when
        var result = service.getRooms(7L, null).content().getFirst();
        // then
        assertEquals(completed ? List.of("non-smoker", "daily") : List.of(), result.counterpart().tags());
        assertEquals(completed ? Integer.valueOf(87) : null, result.counterpart().matchScore());
        assertEquals(3, result.unreadCount());
    }

    @Test
    void rejectsRoomListingWhenCounterpartProfileIsMissing() {
        // given
        stubPage(7L, null, null, List.of(row(10L, 8L, false, false, null)));
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.getRooms(7L, null));
    }

    @ParameterizedTest
    @CsvSource({"-1,true", "0,false", "1,true"})
    void determinesReadStatusFromExactReceiptRegardlessOfRecipientsTimestamp(int seconds, boolean read) {
        // given
        stubActiveMember();
        stubCounterpart();
        counterpart.markRead(SENT_AT.plusSeconds(seconds));
        member.markRead(SENT_AT.minusSeconds(1));
        PageRequest pageable = messagePage(0, 50);
        var sent = message(room, user, SENT_AT);
        if (read) sent.markRead();
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, pageable)).thenReturn(
                new PageImpl<>(List.of(sent, message(room, target, SENT_AT)), pageable, 2));
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
    @CsvSource({"-1,50", "0,0", "0,-1", "2147483647,2", "0,101", "0,2147483647", "1073741824,2"})
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

    @Test
    void acceptsMaximumMessagePageSizeWithoutChangingOrderOrShape() {
        stubActiveMember(); stubCounterpart();
        var pageable = messagePage(0, 100);
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, pageable)).thenReturn(Page.empty(pageable));
        var response = service.getMessages(7L, 10L, new ChatMessageListRequest(0, 100, null));
        assertEquals(100, response.size()); assertTrue(response.content().isEmpty());
        verify(chatMessageRepository).findAllByChatRoom_ChatRoomId(10L, pageable);
    }

    @ParameterizedTest
    @CsvSource({"-1,50", "0,0", "0,-1", "0,101", "0,2147483647", "2147483647,2"})
    void rejectsInvalidRoomPaginationBeforeAnyListQuery(int page, int size) {
        assertError(ErrorCode.VALIDATION_ERROR,
                () -> service.getRooms(7L, new ChatRoomListRequest("ALL", null, page, size)));
        verifyNoInteractions(chatRoomRepository, chatRoomMemberRepository, chatMessageRepository,
                userProfileRepository, profileSurveyRepository, roommateMatchRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesNewMessageUnreadEvenWhenRecipientHasALaterLegacyReadTime(boolean read) {
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
        assertFalse(result.read());
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
        when(chatMessageRepository.findIncomingForRead(10L, 7L, List.of(42L)))
                .thenReturn(List.of(message(room, target, SENT_AT.plusSeconds(seconds))));
        // when
        service.markRead(7L, 10L, new ChatReadRequest(List.of(42L)));
        // then
        assertEquals(seconds > 0 ? SENT_AT.plusSeconds(seconds) : SENT_AT, member.getLastReadAt());
    }

    @Test
    void initializesReadTimestampFromLatestIncomingMessage() {
        // given
        stubActiveMember();
        when(chatMessageRepository.findIncomingForRead(10L, 7L, List.of(42L)))
                .thenReturn(List.of(message(room, target, SENT_AT)));
        // when
        service.markRead(7L, 10L, new ChatReadRequest(List.of(42L)));
        // then
        assertEquals(SENT_AT, member.getLastReadAt());
        assertNull(counterpart.getLastReadAt());
    }

    @Test
    void rejectsUnknownIncomingReceiptWithoutChangingReadTimestamp() {
        // given
        stubActiveMember();
        // when
        assertError(ErrorCode.INVALID_REQUEST, () -> service.markRead(7L, 10L, new ChatReadRequest(List.of(42L))));
        // then
        assertNull(member.getLastReadAt());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2, 3})
    void enforcesTwoPinnedRoomsBeforeChangingEitherSetting(long count) {
        // given
        stubActiveMember();
        when(chatRoomMemberRepository.findPinnedMembersForUpdate(7L))
                .thenReturn(java.util.Collections.nCopies((int) count, member));
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
        verify(chatRoomMemberRepository, never()).findPinnedMembersForUpdate(any());
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
        assertError(ErrorCode.FORBIDDEN, () -> service.markRead(7L, 10L, new ChatReadRequest(List.of(42L))));
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

    @Test
    void regularPostReentryUpdatesLatestSourceAndPreservesMessages() {
        stubUsers();
        CommunityPost first = post(20L, target);
        CommunityPost second = post(21L, target);
        useCommunityRoom(first);
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(first));
        when(communityPostRepository.findByPostIdAndStatus(21L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(second));
        when(chatRoomRepository.findByRoomKey("COMMUNITY:7:8")).thenReturn(Optional.of(room));
        stubActiveMember();
        stubResponse(member, counterpart, "Target");
        ChatMessage history = message(room, target, SENT_AT);
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, messagePage(0, 50)))
                .thenReturn(new PageImpl<>(List.of(history)));

        for (long postId : new long[]{20L, 21L, 20L}) {
            var result = service.createOrGetRoom(7L, postRequest(postId));
            assertEquals(10L, result.id());
            assertEquals(postId, result.sourcePost());
            assertEquals("Target", result.counterpart().nickname());
        }
        assertEquals("hello", service.getMessages(7L, 10L, null).content().getFirst().text());
        assertSame(first, room.getSourcePost());
        when(chatMessageRepository.save(any())).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "sentAt", SENT_AT);
            return saved;
        });
        service.sendMessage(7L, 10L, new ChatMessageSendRequest("new message"));
        when(chatMessageRepository.findIncomingForRead(10L, 7L, List.of(42L))).thenReturn(List.of(history));
        service.markRead(7L, 10L, new ChatReadRequest(List.of(42L)));
        stubPage(7L, null, null, List.of(new ChatRoomListRow(10L, ChatRoomCategory.COMMUNITY, 8L, 20L,
                false, false, false, null, null, 2)));
        stubPageProfile(target, "Target");
        assertEquals(20L, service.getRooms(7L, null).content().getFirst().sourcePost());
        assertSame(first, room.getSourcePost());
        verify(chatRoomRepository, never()).save(any());
        verify(chatMessageRepository, never()).delete(any(ChatMessage.class));
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anonymousAndRegularPostsHaveSeparateRoomsInEitherOrder(boolean anonymousFirst) {
        stubUsers();
        CommunityPost regular = post(20L, target);
        CommunityPost anonymous = post(21L, target, true);
        CommunityPost another = post(22L, target, true);
        for (CommunityPost post : List.of(regular, anonymous, another)) {
            when(communityPostRepository.findByPostIdAndStatus(post.getPostId(), CommunityContentStatus.ACTIVE))
                    .thenReturn(Optional.of(post));
        }
        var saved = new java.util.HashMap<String, ChatRoom>();
        when(chatRoomRepository.findByRoomKey(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get(invocation.getArgument(0))));
        when(chatRoomRepository.save(any())).thenAnswer(invocation -> {
            ChatRoom created = invocation.getArgument(0);
            ReflectionTestUtils.setField(created, "chatRoomId", (long) saved.size() + 10);
            saved.put(created.getRoomKey(), created);
            return created;
        });
        when(chatRoomMemberRepository.findAllByChatRoom_ChatRoomId(any())).thenAnswer(invocation -> {
            ChatRoom found = saved.values().stream().filter(r -> r.getChatRoomId().equals(invocation.getArgument(0))).findFirst().orElseThrow();
            return List.of(new ChatRoomMember(found, user), new ChatRoomMember(found, target));
        });
        when(chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(any(), any())).thenAnswer(invocation -> {
            ChatRoom found = saved.values().stream().filter(r -> r.getChatRoomId().equals(invocation.getArgument(0))).findFirst().orElseThrow();
            return Optional.of(new ChatRoomMember(found, user));
        });
        UserProfile profile = new UserProfile(target, "Target", "F", mock(Dormitory.class), mock(Faculty.class), null,
                (short) 2000, "20", "1", "INTJ");
        when(userProfileRepository.findByUser_UserId(8L)).thenReturn(Optional.of(profile));

        var first = service.createOrGetRoom(7L, postRequest(anonymousFirst ? 21L : 20L));
        var second = service.createOrGetRoom(7L, postRequest(anonymousFirst ? 20L : 21L));
        var otherAnonymous = service.createOrGetRoom(7L, postRequest(22L));
        var reentry = service.createOrGetRoom(7L, postRequest(21L));
        assertNotEquals(first.id(), second.id());
        assertNotEquals(otherAnonymous.id(), reentry.id());
        assertEquals((anonymousFirst ? first : second).id(), reentry.id());
        assertEquals("익명", reentry.counterpart().nickname());
        assertNull(reentry.counterpart().id());
        assertEquals(Set.of("COMMUNITY:7:8", "COMMUNITY:7:8:POST:21", "COMMUNITY:7:8:POST:22"), saved.keySet());
        verify(chatRoomRepository, times(3)).save(any());
    }

    @Test
    void anonymousIdentityIsHiddenInListHistoryAndBroadcastEvenAfterPostEditOrDeletion() {
        CommunityPost post = post(20L, target, true);
        useCommunityRoom(post);
        // 방 생성 당시 익명성을 유지한다.
        // Simulate a legacy row changed before anonymity became immutable.
        ReflectionTestUtils.setField(post, "anonymous", false);
        post.delete();
        stubActiveMember();
        stubCounterpart();
        stubPage(7L, null, null, List.of(new ChatRoomListRow(10L, ChatRoomCategory.COMMUNITY, 8L, 20L,
                true, false, false, null, null, 2)));
        stubPage(7L, null, "%target%", List.of());
        var profile = service.getRooms(7L, null).content().getFirst().counterpart();
        assertEquals(new ChatRoomResponse.CounterpartResponse(null, "익명", null, null, null, null, null, null, List.of()), profile);
        assertTrue(service.getRooms(7L, new ChatRoomListRequest(null, "Target")).content().isEmpty());
        ChatMessage message = message(room, target, SENT_AT);
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, messagePage(0, 50)))
                .thenReturn(new PageImpl<>(List.of(message)));
        var history = service.getMessages(7L, 10L, null).content().getFirst();
        assertNull(history.senderId());
        assertEquals("OTHER", history.senderType());
        assertNull(ChatMessageBroadcastResponse.from(message, false).senderId());
        assertEquals(7L, ChatMessageBroadcastResponse.from(message(room, user, SENT_AT), false).senderId());
        verifyNoInteractions(userProfileRepository, profileSurveyRepository, roommateMatchRepository);
    }

    @Test
    void concurrentRegularCreationRecoveryStillUpdatesLatestPost() {
        stubUsers();
        CommunityPost first = post(20L, target);
        CommunityPost latest = post(21L, target);
        useCommunityRoom(first);
        when(communityPostRepository.findByPostIdAndStatus(21L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(latest));
        when(chatRoomRepository.findByRoomKey("COMMUNITY:7:8")).thenReturn(Optional.empty(), Optional.of(room));
        when(chatRoomRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        stubActiveMember();
        stubResponse(member, counterpart, "Target");
        assertEquals(21L, service.createOrGetRoom(7L, postRequest(21L)).sourcePost());
        assertSame(latest, room.getSourcePost());
    }

    @Test
    void anonymousCommentHasItsOwnKeyAndDoesNotHideOtherPostAuthors() {
        CommunityPost post = post(20L, user);
        CommunityComment comment = new CommunityComment(post, target, null, "comment");
        ReflectionTestUtils.setField(comment, "commentId", 30L);
        comment.changeAnonymous(true);
        ChatRoom commentRoom = new ChatRoom(7L, 8L, ChatRoomCategory.COMMUNITY, null, comment);
        assertEquals("COMMUNITY:7:8:COMMENT:30", commentRoom.getRoomKey());
        assertTrue(commentRoom.isAnonymousAuthor(8L));
        assertFalse(commentRoom.isAnonymousAuthor(7L));
    }

    @Test
    void concurrentAnonymousCreationReusesThePostSpecificWinner() {
        stubUsers();
        CommunityPost anonymous = post(20L, target, true);
        useCommunityRoom(anonymous);
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(anonymous));
        when(chatRoomRepository.findByRoomKey("COMMUNITY:7:8:POST:20")).thenReturn(Optional.empty(), Optional.of(room));
        when(chatRoomRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        stubActiveMember();
        stubCounterpart();
        var result = service.createOrGetRoom(7L, postRequest(20L));
        assertEquals(10L, result.id());
        assertEquals(20L, result.sourcePost());
        assertNull(result.counterpart().id());
        verify(chatRoomRepository, never()).findByRoomKey("COMMUNITY:7:8");
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @Test
    void anonymousAuthorStillSeesTheOtherParticipantsOrdinaryProfileAndOwnMessageDirection() {
        CommunityPost anonymous = post(20L, target, true);
        useCommunityRoom(anonymous);
        stubPage(8L, null, null, List.of(new ChatRoomListRow(10L, ChatRoomCategory.COMMUNITY, 7L, 20L,
                false, false, false, null, null, 2)));
        when(chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(10L, 8L)).thenReturn(Optional.of(counterpart));
        stubCounterpart();
        stubPageProfile(user, "Visitor");
        var response = service.getRooms(8L, null).content().getFirst();
        assertEquals(7L, response.counterpart().id());
        assertEquals("Visitor", response.counterpart().nickname());
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, messagePage(0, 50)))
                .thenReturn(new PageImpl<>(List.of(message(room, target, SENT_AT))));
        var ownMessage = service.getMessages(8L, 10L, null).content().getFirst();
        assertNull(ownMessage.senderId());
        assertEquals("ME", ownMessage.senderType());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsSourceRemovedDuringConcurrentCreationRecovery(boolean fromComment) {
        // given
        stubUsers();
        CommunityPost post = post(20L, target);
        var postLookup = when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post));
        if (fromComment) {
            CommunityComment comment = new CommunityComment(post, target, null, "comment");
            when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE))
                    .thenReturn(Optional.of(comment), Optional.empty());
        } else {
            postLookup.thenReturn(Optional.empty());
        }
        when(chatRoomRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate room key"));

        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L, communityRequest(fromComment)));
        verify(chatRoomRepository, times(1)).findByRoomKey("COMMUNITY:7:8");
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @CsvSource({"7,UNAUTHORIZED", "8,INVALID_REQUEST"})
    void rejectsParticipantSuspendedDuringConcurrentCreationRecovery(long suspendedId, ErrorCode expected) {
        // given
        stubUsers();
        User suspended = suspendedId == 7 ? user : target;
        when(chatRoomRepository.save(any())).thenAnswer(invocation -> {
            suspended.changeStatus(UserStatus.SUSPENDED);
            throw new DataIntegrityViolationException("duplicate room key");
        });

        // when / then
        assertError(expected, () -> service.createOrGetRoom(7L, roommateRequest()));
        verify(chatRoomRepository, times(1)).findByRoomKey("ROOMMATE:7:8");
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @Test
    void rollsBackRoomCreationAndPropagatesParticipantStorageFailure() {
        // given
        stubUsers();
        stubNewRoomSave();
        var transaction = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        var failure = new DataAccessResourceFailureException("storage unavailable");
        when(chatRoomMemberRepository.saveAll(any())).thenThrow(failure);

        // when / then
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.createOrGetRoom(7L, roommateRequest())));
        verify(transactionManager).rollback(transaction);
        verify(transactionManager, never()).commit(any());
        verify(chatRoomRepository, times(1)).findByRoomKey("ROOMMATE:7:8");
        verifyNoInteractions(chatMessageRepository, userProfileRepository);
    }

    @Test
    void preservesAnonymousCommentIdentityInHistoryAndSendingAfterCommentChanges() {
        // given
        CommunityPost post = post(20L, user);
        CommunityComment comment = new CommunityComment(post, target, null, "comment");
        ReflectionTestUtils.setField(comment, "commentId", 30L);
        comment.changeAnonymous(true);
        room = new ChatRoom(7L, 8L, ChatRoomCategory.COMMUNITY, null, comment);
        ReflectionTestUtils.setField(room, "chatRoomId", 10L);
        member = new ChatRoomMember(room, user);
        counterpart = new ChatRoomMember(room, target);
        comment.changeAnonymous(false);
        comment.delete();
        stubPage(7L, null, null, List.of(new ChatRoomListRow(10L, ChatRoomCategory.COMMUNITY, 8L, 20L,
                true, false, false, null, null, 2)));
        stubActiveMember();
        stubCounterpart();
        when(chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(10L, 8L))
                .thenReturn(Optional.of(counterpart));
        when(chatMessageRepository.findAllByChatRoom_ChatRoomId(10L, messagePage(0, 50)))
                .thenReturn(new PageImpl<>(List.of(message(room, target, SENT_AT))));
        when(chatMessageRepository.save(any())).thenAnswer(invocation -> {
            ChatMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "sentAt", SENT_AT);
            return saved;
        });

        // when
        var listed = service.getRooms(7L, null).content().getFirst();
        var history = service.getMessages(7L, 10L, null).content().getFirst();
        var broadcast = service.sendMessage(8L, 10L, new ChatMessageSendRequest("hello"));

        // then
        assertEquals(20L, listed.sourcePost());
        assertEquals(new ChatRoomResponse.CounterpartResponse(null, "익명", null, null, null, null, null, null, List.of()),
                listed.counterpart());
        assertNull(history.senderId());
        assertEquals("OTHER", history.senderType());
        assertNull(broadcast.senderId());
        assertEquals("hello", broadcast.text());
        verifyNoInteractions(userProfileRepository, profileSurveyRepository, roommateMatchRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {7, 8, 999})
    void resolvesAnonymousCommentTargetWithoutExposingOrTestingSuppliedIdentity(Long suppliedTarget) {
        stubUsers();
        CommunityPost post = post(20L, user);
        CommunityComment comment = new CommunityComment(post, target, null, "anonymous");
        ReflectionTestUtils.setField(comment, "commentId", 30L);
        comment.changeAnonymous(true);
        room = new ChatRoom(7L, 8L, ChatRoomCategory.COMMUNITY, null, comment);
        ReflectionTestUtils.setField(room, "chatRoomId", 10L);
        member = new ChatRoomMember(room, user);
        counterpart = new ChatRoomMember(room, target);
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
        when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(comment));
        stubNewRoomSave();
        stubCounterpart();
        var result = service.createOrGetRoom(7L,
                new ChatRoomCreateRequest(suppliedTarget, ChatRoomCategory.COMMUNITY, 20L, 30L, "COMMENT"));
        assertNull(result.counterpart().id());
        assertEquals("익명", result.counterpart().nickname());
        verifyNoInteractions(userProfileRepository);
        verify(userRepository, never()).findById(999L);
    }

    @Test
    void resolvesRegularCommentAuthorWhenTargetIsOmitted() {
        // given
        stubUsers();
        CommunityPost post = post(20L, user);
        CommunityComment comment = new CommunityComment(post, target, null, "comment");
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post));
        when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));
        when(chatRoomRepository.save(any())).thenAnswer(invocation -> {
            ChatRoom saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "chatRoomId", 10L);
            return saved;
        });
        stubResponse(member, counterpart, "Target");

        // when
        var result = service.createOrGetRoom(7L,
                new ChatRoomCreateRequest(null, ChatRoomCategory.COMMUNITY, 20L, 30L, "COMMENT"));

        // then
        assertEquals(8L, result.counterpart().id());
        assertEquals(20L, result.sourcePost());
        var saved = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(saved.capture());
        assertEquals("COMMUNITY:7:8", saved.getValue().getRoomKey());
        assertSame(comment, saved.getValue().getSourceComment());
        assertNull(saved.getValue().getSourcePost());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsChatWithOwnCommentAfterResolvingAuthor(boolean anonymous) {
        // given
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        CommunityPost post = post(20L, target);
        CommunityComment comment = new CommunityComment(post, user, null, "my comment");
        comment.changeAnonymous(anonymous);
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post));
        when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));
        var request = new ChatRoomCreateRequest(anonymous ? 999L : null,
                ChatRoomCategory.COMMUNITY, 20L, 30L, "COMMENT");

        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L, request));
        verifyNoInteractions(chatRoomRepository, chatRoomMemberRepository);
        verify(userRepository, never()).findById(999L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "SUSPENDED", "WITHDRAWN"})
    void rejectsUnavailableAnonymousCommentAuthorEvenWhenSuppliedTargetIsActive(String status) {
        // given
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        CommunityPost post = post(20L, user);
        CommunityComment comment = new CommunityComment(post, target, null, "anonymous comment");
        comment.changeAnonymous(true);
        when(communityPostRepository.findByPostIdAndStatus(20L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post));
        when(communityCommentRepository.findByCommentIdAndStatus(30L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(comment));
        if (!status.equals("missing")) {
            target.changeStatus(UserStatus.valueOf(status));
            when(userRepository.findById(8L)).thenReturn(Optional.of(target));
        }
        var request = new ChatRoomCreateRequest(7L, ChatRoomCategory.COMMUNITY, 20L, 30L, "COMMENT");

        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createOrGetRoom(7L, request));
        verifyNoInteractions(chatRoomRepository, chatRoomMemberRepository);
    }

    @Test
    void replacesRegularCommentSourceWithPostWhenReusingRoom() {
        // given
        stubUsers();
        CommunityComment comment = new CommunityComment(post(20L, user), target, null, "comment");
        room = new ChatRoom(7L, 8L, ChatRoomCategory.COMMUNITY, null, comment);
        ReflectionTestUtils.setField(room, "chatRoomId", 10L);
        member = new ChatRoomMember(room, user);
        counterpart = new ChatRoomMember(room, target);
        CommunityPost latest = post(21L, target);
        when(communityPostRepository.findByPostIdAndStatus(21L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(latest));
        when(chatRoomRepository.findByRoomKey("COMMUNITY:7:8")).thenReturn(Optional.of(room));
        stubActiveMember();
        stubResponse(member, counterpart, "Target");

        // when
        var result = service.createOrGetRoom(7L, postRequest(21L));

        // then
        assertEquals(10L, result.id());
        assertEquals(21L, result.sourcePost());
        assertSame(latest, room.getSourcePost());
        assertNull(room.getSourceComment());
        verify(chatRoomRepository, never()).save(any());
        verify(chatRoomMemberRepository, never()).saveAll(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1999, 2000, 2001})
    void validatesEmojiLengthBeforeSavingMessageWithRealValidator(int count) {
        // given
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var request = new ChatMessageSendRequest("😀".repeat(count));
            when(validator.validate(request)).thenReturn(factory.getValidator().validate(request));
            if (count > 2000) {
                // when / then
                assertError(ErrorCode.VALIDATION_ERROR, () -> service.sendMessage(7L, 10L, request));
                verifyNoInteractions(chatRoomMemberRepository, chatMessageRepository);
            } else {
                stubActiveMember();
                stubCounterpart();
                when(chatMessageRepository.save(any())).thenAnswer(invocation -> {
                    ChatMessage saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "sentAt", SENT_AT);
                    return saved;
                });

                // when
                var response = service.sendMessage(7L, 10L, request);

                // then
                assertEquals(request.text(), response.text());
                assertEquals(10L, response.roomId());
                assertEquals(7L, response.senderId());
                verify(chatMessageRepository).save(any(ChatMessage.class));
            }
        }
    }

    private ChatRoomCreateRequest postRequest(Long id) {
        return new ChatRoomCreateRequest(8L, ChatRoomCategory.COMMUNITY, id, null, "POST");
    }

    private void useCommunityRoom(CommunityPost post) {
        room = new ChatRoom(7L, 8L, ChatRoomCategory.COMMUNITY, post, null);
        ReflectionTestUtils.setField(room, "chatRoomId", 10L);
        member = new ChatRoomMember(room, user);
        counterpart = new ChatRoomMember(room, target);
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
        return post(id, author, false);
    }

    private CommunityPost post(Long id, User author, boolean anonymous) {
        CommunityPost post = new CommunityPost(author, null, "자유게시판", "title", "content", anonymous);
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
        lenient().when(chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(10L, 7L)).thenReturn(Optional.of(member));
        lenient().when(chatRoomMemberRepository.findMemberForUpdate(10L, 7L)).thenReturn(Optional.of(member));
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

    private ChatRoomListRow row(Long id, Long targetId, boolean anonymous, boolean pinned, LocalDateTime sentAt) {
        return new ChatRoomListRow(id, ChatRoomCategory.ROOMMATE, targetId, null, anonymous, pinned, false,
                sentAt == null ? null : "hello", sentAt, 2);
    }

    private void stubPage(Long userId, ChatRoomCategory category, String pattern, List<ChatRoomListRow> rows) {
        when(chatRoomRepository.findRoomPage(userId, category, pattern, PageRequest.of(0, 50)))
                .thenReturn(new PageImpl<>(rows, PageRequest.of(0, 50), rows.size()));
    }

    private void stubPageProfile(User other, String nickname) {
        var profile = new UserProfile(other, nickname, "F", mock(Dormitory.class), mock(Faculty.class), null,
                (short) 2000, "20", "1", "INTJ");
        when(userProfileRepository.findAllByUser_UserIdIn(List.of(other.getUserId()))).thenReturn(List.of(profile));
    }

    private PageRequest messagePage(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt", "messageId"));
    }

    private void assertError(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(CustomException.class, action).getErrorCode());
    }
}
