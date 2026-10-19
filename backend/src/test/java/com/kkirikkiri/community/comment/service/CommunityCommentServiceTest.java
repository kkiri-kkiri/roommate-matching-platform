package com.kkirikkiri.community.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kkirikkiri.community.comment.dto.*;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.entity.CommentLike;
import com.kkirikkiri.community.comment.repository.CommentLikeRepository;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CommunityCommentServiceTest {
    @Mock CommunityCommentRepository communityCommentRepository;
    @Mock CommunityPostRepository communityPostRepository;
    @Mock UserRepository userRepository;
    @Mock Validator validator;
    @Mock ConstraintViolation<Object> violation;
    @Mock CommentLikeRepository commentLikeRepository;
    @Mock CommunityAnonymousAuthorService anonymousAuthorService;
    @Mock jakarta.persistence.EntityManager entityManager;
    @InjectMocks CommunityCommentService service;
    User author;
    CommunityPost activePost;
    CommunityComment activeComment;

    @BeforeEach
    void setUp() {
        author = mock(User.class);
        lenient().when(userRepository.findByIdForAccountUpdate(anyLong())).thenReturn(Optional.of(author));
        lenient().when(author.getStatus()).thenReturn(com.kkirikkiri.user.entity.UserStatus.ACTIVE);
        activePost = new CommunityPost(author, null, "자유게시판", "제목", "내용");
        activeComment = new CommunityComment(activePost, author, null, "댓글");
        ReflectionTestUtils.setField(activePost, "postId", 1L);
        ReflectionTestUtils.setField(activeComment, "commentId", 2L);
        ReflectionTestUtils.setField(service, "commentWriteService",
                new CommunityCommentWriteService(communityPostRepository, communityCommentRepository, entityManager));
        lenient().when(communityCommentRepository.findPostIdByCommentId(2L)).thenReturn(Optional.of(1L));
        lenient().when(communityPostRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(activePost));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void persistsAnonymousChoiceForCommentsAndReplies(boolean anonymous) {
        User user = mock(User.class);
        CommunityPost post = new CommunityPost(user, null, "자유게시판", "제목", "내용");
        ReflectionTestUtils.setField(post, "postId", 1L);
        CommunityComment parent = new CommunityComment(post, user, null, "부모");
        when(validator.validate(any())).thenReturn(Set.of());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(communityPostRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(post));
        when(communityCommentRepository.findForUpdate(2L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(parent));
        when(communityCommentRepository.save(any())).thenAnswer(invocation -> {
            CommunityComment saved = invocation.getArgument(0);
            assertEquals(anonymous, saved.isAnonymous());
            assertSame(user, saved.getAuthor());
            assertEquals("내용", saved.getContent());
            return saved;
        });
        service.createComment(7L, 1L, new CommunityCommentCreateRequest("내용", anonymous));
        service.createReply(7L, 2L, new CommunityCommentCreateRequest("내용", anonymous));
        verify(communityCommentRepository, times(2)).save(any());
        if (anonymous) verify(anonymousAuthorService, times(2)).resolveNumbers(1L, user);
        else verifyNoInteractions(anonymousAuthorService);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void doesNotSaveCommentOrReplyWhenAnonymousNumberAllocationFails(boolean reply) {
        // given
        ReflectionTestUtils.setField(activePost, "postId", 1L);
        if (reply) stubActiveComment();
        else when(communityPostRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(activePost));
        when(userRepository.findById(7L)).thenReturn(Optional.of(author));
        var failure = new CustomException(ErrorCode.INVALID_REQUEST);
        when(anonymousAuthorService.resolveNumbers(1L, author)).thenThrow(failure);
        var request = new CommunityCommentCreateRequest("익명 댓글", true);
        // when / then
        assertSame(failure, assertThrows(CustomException.class, () -> {
            if (reply) service.createReply(7L, 2L, request);
            else service.createComment(7L, 1L, request);
        }));
        verify(communityCommentRepository, never()).save(any());
    }

    @Test
    void masksAnonymousProfileAndPreservesChoiceOnContentEdit() {
        User user = mock(User.class);
        CommunityPost post = new CommunityPost(user, null, "자유게시판", "제목", "내용");
        CommunityComment comment = new CommunityComment(post, user, null, "댓글");
        var author = new CommunityAuthorResponse(7L, "닉네임", "소프트웨어융합전공");
        assertFalse(new CommunityCommentCreateRequest("내용", null).anonymous());
        assertFalse(comment.isAnonymous());
        assertEquals(author, CommunityCommentResponse.from(comment, author, 0, false, null, null).author());
        comment.changeAnonymous(true);
        comment.updateContent("수정");
        var response = CommunityCommentResponse.from(comment, author, 0, false, 1L, null);
        assertTrue(response.anonymous());
        assertNull(response.author().id());
        assertEquals("익명1", response.author().nickname());
        assertEquals(1L, response.anonymousNumber());
        assertFalse(response.mine());
        assertNull(response.author().major());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesCommentOrReplyWithCorrectParentAndReturnsId(boolean reply) {
        // given
        if (reply) stubActiveComment();
        else when(communityPostRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(activePost));
        when(userRepository.findById(7L)).thenReturn(Optional.of(author));
        when(communityCommentRepository.save(any())).thenAnswer(invocation -> {
            CommunityComment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 42L);
            return saved;
        });
        // when
        Long id = reply ? service.createReply(7L, 2L, createRequest())
                : service.createComment(7L, 1L, createRequest());
        // then
        assertEquals(42L, id);
        var captor = ArgumentCaptor.forClass(CommunityComment.class);
        verify(communityCommentRepository).save(captor.capture());
        assertSame(activePost, captor.getValue().getPost());
        assertSame(reply ? activeComment : null, captor.getValue().getParentComment());
    }

    @Test
    void rejectsReplyToAnotherReplyBeforeSaving() {
        // given
        CommunityComment reply = new CommunityComment(activePost, author, activeComment, "답글");
        when(communityCommentRepository.findPostIdByCommentId(3L)).thenReturn(Optional.of(1L));
        when(communityCommentRepository.findForUpdate(3L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(reply));
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createReply(7L, 3L, createRequest()));
        verify(communityCommentRepository, never()).save(any());
        verify(userRepository).findByIdForAccountUpdate(7L);
    }

    @Test
    void rejectsCommentCreationOnMissingOrInactivePost() {
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createComment(7L, 99L, createRequest()));
        verify(communityCommentRepository, never()).save(any());
    }

    @Test
    void rejectsCommentAndReplyCreationWhenUserNoLongerExists() {
        // given
        stubActiveComment();
        when(communityPostRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(activePost));
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createComment(7L, 1L, createRequest()));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createReply(7L, 2L, createRequest()));
        verify(communityCommentRepository, never()).save(any());
    }

    @Test
    void updatesOwnedCommentContentWithoutChangingAnonymousChoice() {
        // given
        stubOwnedComment();
        activeComment.changeAnonymous(true);
        // when
        service.updateComment(7L, 2L, new CommunityCommentUpdateRequest("수정 내용"));
        // then
        assertEquals("수정 내용", activeComment.getContent());
        assertTrue(activeComment.isAnonymous());
        assertEquals(CommunityContentStatus.ACTIVE, activeComment.getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void softDeletesOwnedThreadOrOnlySelectedReply(boolean deleteReply) {
        // given
        when(author.getUserId()).thenReturn(7L);
        CommunityComment reply = new CommunityComment(activePost, author, activeComment, "답글");
        CommunityComment target = deleteReply ? reply : activeComment;
        when(communityCommentRepository.findForUpdate(2L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(target));
        if (!deleteReply) {
            when(communityCommentRepository.findRepliesForUpdate(2L, CommunityContentStatus.ACTIVE))
                    .thenReturn(List.of(reply));
        }
        // when
        service.deleteComment(7L, 2L);
        // then
        assertEquals(CommunityContentStatus.DELETED, target.getStatus());
        assertEquals(deleteReply ? CommunityContentStatus.ACTIVE : CommunityContentStatus.DELETED,
                (deleteReply ? activeComment : reply).getStatus());
        assertEquals(CommunityContentStatus.ACTIVE, activePost.getStatus());
        assertSame(activeComment, reply.getParentComment());
        verify(communityCommentRepository, never()).delete(any(CommunityComment.class));
    }

    @Test
    void forbidsUpdatingOrDeletingAnotherUsersComment() {
        // given
        stubOwnedComment();
        // when / then
        assertError(ErrorCode.FORBIDDEN, () -> service.updateComment(8L, 2L, new CommunityCommentUpdateRequest("수정")));
        assertError(ErrorCode.FORBIDDEN, () -> service.deleteComment(8L, 2L));
        assertEquals("댓글", activeComment.getContent());
        assertEquals(CommunityContentStatus.ACTIVE, activeComment.getStatus());
    }

    @Test
    void rejectsReplyEditDeleteAndLikesWhenContainingPostIsDeleted() {
        // given
        activePost.delete();
        stubActiveComment();
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createReply(7L, 2L, createRequest()));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.updateComment(7L, 2L, new CommunityCommentUpdateRequest("수정")));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.deleteComment(7L, 2L));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.likeComment(7L, 2L));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.unlikeComment(7L, 2L));
        assertEquals("댓글", activeComment.getContent());
        assertEquals(CommunityContentStatus.ACTIVE, activeComment.getStatus());
        verify(communityCommentRepository, never()).save(any());
        verifyNoInteractions(commentLikeRepository);
        verify(userRepository, atLeastOnce()).findByIdForAccountUpdate(7L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1, 99})
    void rejectsInvalidOrMissingCommentIds(Long id) {
        // given: positive IDs are absent from the mocked repository.
        // when / then
        assertError(ErrorCode.INVALID_REQUEST, () -> service.createReply(7L, id, createRequest()));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.updateComment(7L, id, new CommunityCommentUpdateRequest("수정")));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.deleteComment(7L, id));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.likeComment(7L, id));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.unlikeComment(7L, id));
        verify(communityCommentRepository, never()).save(any());
        verifyNoInteractions(commentLikeRepository);
        verify(userRepository, atLeastOnce()).findByIdForAccountUpdate(7L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsUnauthenticatedCommentOperations(Long userId) {
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createComment(userId, 1L, createRequest()));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.createReply(userId, 2L, createRequest()));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.updateComment(userId, 2L, new CommunityCommentUpdateRequest("수정")));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.deleteComment(userId, 2L));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.likeComment(userId, 2L));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.unlikeComment(userId, 2L));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.getMyComments(userId, null, null));
        verifyNoInteractions(communityCommentRepository, communityPostRepository, commentLikeRepository);
    }

    @Test
    void rejectsNullCommentRequestsBeforeRepositoryAccess() {
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createComment(7L, 1L, null));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createReply(7L, 2L, null));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.updateComment(7L, 2L, null));
        verifyNoInteractions(communityCommentRepository, communityPostRepository);
    }

    @Test
    void rejectsConstraintViolationsBeforeSavingOrUpdatingComment() {
        // given
        when(validator.validate(any())).thenReturn(Set.of(violation));
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createComment(7L, 1L, createRequest()));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.createReply(7L, 2L, createRequest()));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.updateComment(7L, 2L, new CommunityCommentUpdateRequest(" ")));
        verifyNoInteractions(communityCommentRepository, communityPostRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void savesCommentLikeOnlyWhenNotAlreadyLiked(boolean alreadyLiked) {
        // given
        stubCommentForLike();
        when(commentLikeRepository.existsByComment_CommentIdAndUser_UserId(2L, 7L)).thenReturn(alreadyLiked);
        // when
        service.likeComment(7L, 2L);
        // then
        if (alreadyLiked) verify(commentLikeRepository, never()).save(any());
        else {
            var captor = ArgumentCaptor.forClass(CommentLike.class);
            verify(commentLikeRepository).save(captor.capture());
            assertSame(activeComment, captor.getValue().getComment());
            assertSame(author, captor.getValue().getUser());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removesOnlyExistingCommentLikeForCurrentUser(boolean alreadyLiked) {
        // given
        stubCommentForLike();
        CommentLike like = new CommentLike(activeComment, author);
        when(commentLikeRepository.findByComment_CommentIdAndUser_UserId(2L, 7L))
                .thenReturn(alreadyLiked ? Optional.of(like) : Optional.empty());
        // when
        service.unlikeComment(7L, 2L);
        // then
        if (alreadyLiked) verify(commentLikeRepository).delete(like);
        else verify(commentLikeRepository, never()).delete(any(CommentLike.class));
    }

    @Test
    void rejectsCommentLikesWhenUserNoLongerExists() {
        // given
        when(communityCommentRepository.findForUpdate(2L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(activeComment));
        // when / then
        assertError(ErrorCode.UNAUTHORIZED, () -> service.likeComment(7L, 2L));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.unlikeComment(7L, 2L));
        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    void readsOwnActiveCommentsAndRepliesWithDefaultPaginationAndLatestSort() {
        // given
        CommunityComment reply = new CommunityComment(activePost, author, activeComment, "답글");
        ReflectionTestUtils.setField(activePost, "postId", 1L);
        PageRequest pageable = PageRequest.of(0, 6, Sort.by(Sort.Direction.DESC, "createdAt", "commentId"));
        when(communityCommentRepository.findAllByAuthor_UserIdAndStatus(7L, CommunityContentStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(reply, activeComment), pageable, 2));
        // when
        var result = service.getMyComments(7L, null, null);
        // then
        assertEquals(0, result.page());
        assertEquals(6, result.size());
        assertEquals(2, result.totalElements());
        assertEquals(List.of("답글", "댓글"), result.content().stream().map(MyCommunityCommentResponse::content).toList());
        assertTrue(result.content().getFirst().isReply());
        assertFalse(result.content().getLast().isReply());
        assertEquals(1L, result.content().getFirst().postId());
    }

    @ParameterizedTest
    @CsvSource({"-1,6", "0,0", "0,-1", "2147483647,2"})
    void rejectsInvalidCommentPaginationBeforeQuerying(int page, int size) {
        // when / then
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.getMyComments(7L, page, size));
        verifyNoInteractions(communityCommentRepository);
    }

    @Test
    void acceptsMaximumCommentOffsetAndReturnsEmptyPage() {
        // given
        PageRequest pageable = PageRequest.of(Integer.MAX_VALUE, 1, Sort.by(Sort.Direction.DESC, "createdAt", "commentId"));
        when(communityCommentRepository.findAllByAuthor_UserIdAndStatus(7L, CommunityContentStatus.ACTIVE, pageable))
                .thenReturn(org.springframework.data.domain.Page.empty(pageable));
        // when
        var result = service.getMyComments(7L, Integer.MAX_VALUE, 1);
        // then
        assertTrue(result.content().isEmpty());
        assertEquals(Integer.MAX_VALUE, result.page());
        assertEquals(0, result.totalElements());
    }

    private void stubActiveComment() {
        when(communityCommentRepository.findForUpdate(2L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(activeComment));
    }

    private void stubOwnedComment() {
        when(communityCommentRepository.findForUpdate(2L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(activeComment));
        when(author.getUserId()).thenReturn(7L);
    }

    private void stubCommentForLike() {
        when(communityCommentRepository.findForUpdate(2L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(activeComment));
        when(userRepository.findById(7L)).thenReturn(Optional.of(author));
    }

    private CommunityCommentCreateRequest createRequest() {
        return new CommunityCommentCreateRequest("내용");
    }

    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(CustomException.class, action).getErrorCode());
    }
}
