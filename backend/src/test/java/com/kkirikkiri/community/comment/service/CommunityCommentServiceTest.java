package com.kkirikkiri.community.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kkirikkiri.community.comment.dto.*;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CommunityCommentServiceTest {
    @Mock CommunityCommentRepository communityCommentRepository;
    @Mock CommunityPostRepository communityPostRepository;
    @Mock UserRepository userRepository;
    @Mock Validator validator;
    @InjectMocks CommunityCommentService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void persistsAnonymousChoiceForCommentsAndReplies(boolean anonymous) {
        User user = mock(User.class);
        CommunityPost post = new CommunityPost(user, null, "자유게시판", "제목", "내용");
        CommunityComment parent = new CommunityComment(post, user, null, "부모");
        when(validator.validate(any())).thenReturn(Set.of());
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(communityPostRepository.findByPostIdAndStatus(1L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
        when(communityCommentRepository.findByCommentIdAndStatus(2L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(parent));
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
    }

    @Test
    void masksAnonymousProfileAndPreservesChoiceOnContentEdit() {
        User user = mock(User.class);
        CommunityPost post = new CommunityPost(user, null, "자유게시판", "제목", "내용");
        CommunityComment comment = new CommunityComment(post, user, null, "댓글");
        var author = new CommunityAuthorResponse(7L, "닉네임", "소프트웨어융합전공");
        assertFalse(new CommunityCommentCreateRequest("내용", null).anonymous());
        assertFalse(comment.isAnonymous());
        assertEquals(author, CommunityCommentResponse.from(comment, author, 0, false).author());
        comment.changeAnonymous(true);
        comment.updateContent("수정");
        var response = CommunityCommentResponse.from(comment, author, 0, false);
        assertTrue(response.anonymous());
        assertEquals(7L, response.author().id());
        assertEquals("익명", response.author().nickname());
        assertNull(response.author().major());
    }
}
