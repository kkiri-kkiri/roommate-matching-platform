package com.kkirikkiri.global.init;

import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunitySeedDataServiceTest {

    @Mock CommunityPostRepository communityPostRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks CommunitySeedDataService service;

    @Test
    void createsTwentyPostsPerBoardAndOnlyFillsMissingPostsOnRerun() {
        Dormitory dormitory = new Dormitory("NARAE", "나래", true);
        List<CommunityPost> savedPosts = new ArrayList<>();
        Set<List<Object>> existingPosts = new HashSet<>();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> existingPosts.contains(List.of(
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4))) ? 1 : 0);
        when(communityPostRepository.save(any(CommunityPost.class))).thenAnswer(invocation -> {
            CommunityPost post = invocation.getArgument(0);
            savedPosts.add(post);
            existingPosts.add(List.of(post.getAuthor().getUserId(), post.getCategory(), post.getTitle()));
            return post;
        });
        List<User> authors = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            User author = mock(User.class);
            if (i <= 20) {
                when(author.getUserId()).thenReturn((long) i);
            }
            authors.add(author);
            assertEquals(i <= 20 ? 4 : 0, service.createPosts(author, dormitory, i));
        }

        assertEquals(80, savedPosts.size());
        assertEquals(Set.of("자유게시판", "나눔게시판", "택배게시판", "민원게시판"),
                savedPosts.stream().map(CommunityPost::getCategory).collect(Collectors.toSet()));
        savedPosts.stream().collect(Collectors.groupingBy(CommunityPost::getCategory)).values()
                .forEach(posts -> {
                    assertEquals(20, posts.size());
                    assertEquals(20, posts.stream().map(CommunityPost::getTitle).distinct().count());
                    assertEquals(20, posts.stream().map(CommunityPost::getContent).distinct().count());
                });
        for (CommunityPost post : savedPosts) {
            assertSame(dormitory, post.getDormitory());
            assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
            assertTrue(post.getTitle().startsWith("[SEED]"));
            assertTrue(post.getTitle().length() <= 100);
            assertFalse(post.getContent().isBlank());
            assertTrue(authors.contains(post.getAuthor()));
        }

        for (int i = 1; i <= 30; i++) {
            assertEquals(0, service.createPosts(authors.get(i - 1), dormitory, i));
        }
        assertEquals(80, savedPosts.size());

        CommunityPost missingPost = savedPosts.getFirst();
        existingPosts.remove(List.of(missingPost.getAuthor().getUserId(), missingPost.getCategory(), missingPost.getTitle()));
        assertEquals(1, service.createPosts(authors.getFirst(), dormitory, 1));
        assertEquals(81, savedPosts.size());
        assertEquals(missingPost.getTitle(), savedPosts.getLast().getTitle());
    }

    @Test
    void deletesOnlySelectedAuthorsPostsAfterCleaningReferencesAndReplies() {
        when(jdbcTemplate.queryForList(
                "SELECT post_id FROM community_post WHERE author_id IN (?,?)", Long.class, 1L, 2L))
                .thenReturn(List.of(10L, 20L));
        when(jdbcTemplate.queryForList(
                "SELECT comment_id FROM community_comment WHERE post_id IN (?,?)", Long.class, 10L, 20L))
                .thenReturn(List.of(30L, 40L));

        service.deletePosts(List.of(1L, 2L));

        var ordered = inOrder(jdbcTemplate);
        ordered.verify(jdbcTemplate).queryForList(
                "SELECT post_id FROM community_post WHERE author_id IN (?,?)", Long.class, 1L, 2L);
        ordered.verify(jdbcTemplate).queryForList(
                "SELECT comment_id FROM community_comment WHERE post_id IN (?,?)", Long.class, 10L, 20L);
        ordered.verify(jdbcTemplate).update("DELETE FROM report WHERE comment_id IN (?,?)", 30L, 40L);
        ordered.verify(jdbcTemplate).update("DELETE FROM report WHERE post_id IN (?,?)", 10L, 20L);
        ordered.verify(jdbcTemplate).update(
                "UPDATE chat_room SET source_comment_id = NULL WHERE source_comment_id IN (?,?)", 30L, 40L);
        ordered.verify(jdbcTemplate).update(
                "UPDATE chat_room SET source_post_id = NULL WHERE source_post_id IN (?,?)", 10L, 20L);
        ordered.verify(jdbcTemplate).update("DELETE FROM comment_like WHERE comment_id IN (?,?)", 30L, 40L);
        ordered.verify(jdbcTemplate).update(
                "DELETE FROM community_comment WHERE post_id IN (?,?) AND parent_comment_id IS NOT NULL", 10L, 20L);
        ordered.verify(jdbcTemplate).update("DELETE FROM community_comment WHERE post_id IN (?,?)", 10L, 20L);
        ordered.verify(jdbcTemplate).update("DELETE FROM post_like WHERE post_id IN (?,?)", 10L, 20L);
        ordered.verify(jdbcTemplate).update("DELETE FROM post_image WHERE post_id IN (?,?)", 10L, 20L);
        ordered.verify(jdbcTemplate).update("DELETE FROM community_post WHERE post_id IN (?,?)", 10L, 20L);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void skipsCleanupWhenSeedUsersOrTheirPostsAreAbsent() {
        service.deletePosts(List.of());
        verifyNoInteractions(jdbcTemplate);

        when(jdbcTemplate.queryForList(
                "SELECT post_id FROM community_post WHERE author_id IN (?)", Long.class, 1L))
                .thenReturn(List.of());
        service.deletePosts(List.of(1L));
        verify(jdbcTemplate).queryForList(
                "SELECT post_id FROM community_post WHERE author_id IN (?)", Long.class, 1L);
        verifyNoMoreInteractions(jdbcTemplate);
    }
}
