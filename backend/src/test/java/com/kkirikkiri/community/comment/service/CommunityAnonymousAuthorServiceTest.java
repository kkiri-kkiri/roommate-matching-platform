package com.kkirikkiri.community.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.kkirikkiri.community.comment.entity.CommunityAnonymousAuthor;
import com.kkirikkiri.community.comment.repository.CommunityAnonymousAuthorRepository;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CommunityAnonymousAuthorServiceTest {
    @Mock CommunityPostRepository postRepository;
    @Mock CommunityCommentRepository commentRepository;
    @Mock CommunityAnonymousAuthorRepository anonymousAuthorRepository;
    @InjectMocks CommunityAnonymousAuthorService service;

    @Test
    void rejectsMissingOrInactivePostBeforeReadingOrSavingNumbers() {
        // when / then
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(CustomException.class,
                () -> service.resolveNumbers(1L, null)).getErrorCode());
        verifyNoInteractions(commentRepository, anonymousAuthorRepository);
    }

    @Test
    void returnsEmptyNumbersWithoutCreatingParticipantOnRead() {
        // given
        CommunityPost post = new CommunityPost(mock(User.class), null, "자유게시판", "제목", "내용");
        when(postRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post));
        // when
        var result = service.resolveNumbers(1L, null);
        // then
        assertTrue(result.isEmpty());
        verify(anonymousAuthorRepository, never()).save(any());
    }

    @Test
    void assignsNumberAboveStoredMaximumEvenWhenNumbersHaveGapsAndAreUnordered() {
        // given
        User first = user(7L), second = user(8L), newcomer = user(9L);
        CommunityPost post = new CommunityPost(first, null, "자유게시판", "제목", "내용");
        when(postRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE))
                .thenReturn(Optional.of(post));
        when(anonymousAuthorRepository.findAllForUpdate(1L)).thenReturn(List.of(
                new CommunityAnonymousAuthor(post, first, 5L),
                new CommunityAnonymousAuthor(post, second, 2L)));
        // when
        var result = service.resolveNumbers(1L, newcomer);
        // then
        assertEquals(Map.of(7L, 5L, 8L, 2L, 9L, 6L), result);
        var captor = ArgumentCaptor.forClass(CommunityAnonymousAuthor.class);
        verify(anonymousAuthorRepository).save(captor.capture());
        assertSame(post, captor.getValue().getPost());
        assertSame(newcomer, captor.getValue().getAuthor());
        assertEquals(6L, captor.getValue().getAnonymousNumber());
        verifyNoInteractions(commentRepository);
    }

    private User user(Long id) {
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(id);
        return user;
    }
}
