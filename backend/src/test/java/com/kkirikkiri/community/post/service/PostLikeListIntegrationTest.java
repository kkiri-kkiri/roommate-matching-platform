package com.kkirikkiri.community.post.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.kkirikkiri.community.post.dto.*;
import com.kkirikkiri.community.post.entity.*;
import com.kkirikkiri.community.comment.entity.*;
import com.kkirikkiri.community.comment.repository.CommentLikeRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class PostLikeListIntegrationTest {
    @Autowired EntityManager em;
    @Autowired CommunityPostService service;
    @Autowired CommentLikeRepository commentLikes;
    User a, b;
    Dormitory dorm, otherDorm;
    final List<Long> ids = new ArrayList<>();

    @BeforeEach void fixtures() {
        String suffix = UUID.randomUUID().toString();
        a = new User("test-hash", suffix + "a@test.invalid");
        b = new User("test-hash", suffix + "b@test.invalid");
        em.persist(a); em.persist(b);
        dorm = new Dormitory("pl-a-" + suffix.substring(0, 8), "나래", true);
        otherDorm = new Dormitory("pl-b-" + suffix.substring(0, 8), "호연", true);
        em.persist(dorm); em.persist(otherDorm);
        for (int i = 0; i < 24; i++) {
            var p = new CommunityPost(i % 2 == 0 ? a : b, i % 2 == 0 ? dorm : otherDorm,
                    i % 3 == 0 ? "나눔게시판" : "자유게시판", "게시글 " + i, "검색 본문 " + i);
            p.changeAnonymous(i == 0);
            ReflectionTestUtils.setField(p, "viewCount", i % 4);
            em.persist(p);
            // Equal timestamps verify the final postId DESC tie-breaker as well.
            em.createNativeQuery("update community_post set created_at=:at where post_id=:id")
                    .setParameter("at", LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(i / 3))
                    .setParameter("id", p.getPostId()).executeUpdate();
            ids.add(p.getPostId());
            if (i % 3 >= 1) em.persist(new PostLike(p, a));
            if (i % 3 == 2) em.persist(new PostLike(p, b));
        }
        em.flush(); em.clear();
    }

    @ParameterizedTest
    @CsvSource({"1,false", "6,false", "20,false", "1,true", "6,true", "20,true"})
    void pageLikeQueriesStayConstantAndViewerStateIsCorrect(int size, boolean guest) {
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var page = service.getPosts(guest ? null : a.getUserId(), new CommunityPostListRequest(0, size, null, null, "LATEST"));
        assertEquals(size, page.content().size());
        assertEquals(24, page.totalElements());
        assertEquals((24 + size - 1) / size, page.totalPages());
        assertTrue(page.first()); assertFalse(page.last());
        for (var row : page.content()) {
            int fixtureIndex = ids.indexOf(row.id());
            assertEquals(fixtureIndex % 3, row.likes());
            assertEquals(!guest && fixtureIndex % 3 > 0, row.liked());
        }
        long likeQueries = Arrays.stream(stats.getQueries()).filter(q -> q.contains("from PostLike l"))
                .mapToLong(q -> stats.getQueryStatistics(q).getExecutionCount()).sum();
        assertEquals(guest ? 1 : 2, likeQueries, "Page size must not multiply count/liked statements");
        assertFalse(Arrays.stream(stats.getQueries()).anyMatch(q -> q.startsWith("select count(*) from post_like")),
                "List must not call the single-post count/isLiked path");
    }

    @ParameterizedTest @ValueSource(strings = {"LATEST", "VIEWS", "LIKES"})
    void sortingPaginationAndFiltersPreserveDatabaseOrder(String sort) {
        String order = switch (sort) {
            case "VIEWS" -> "p.viewCount desc,p.createdAt desc,p.postId desc";
            case "LIKES" -> "(select count(l) from PostLike l where l.post=p) desc,p.createdAt desc,p.postId desc";
            default -> "p.createdAt desc,p.postId desc";
        };
        var expected = em.createQuery("select p.postId from CommunityPost p order by " + order, Long.class).getResultList();
        var first = service.getPosts(a.getUserId(), new CommunityPostListRequest(0, 6, null, null, sort));
        var second = service.getPosts(a.getUserId(), new CommunityPostListRequest(1, 6, null, null, sort));
        assertEquals(expected.subList(0, 6), first.content().stream().map(CommunityPostResponse::id).toList());
        assertEquals(expected.subList(6, 12), second.content().stream().map(CommunityPostResponse::id).toList());
        assertEquals(24, second.totalElements()); assertFalse(second.first()); assertFalse(second.last());
        var filtered = service.getPosts(null, new CommunityPostListRequest(0, 6, "나눔게시판", "검색 본문", "나래관", sort));
        assertEquals(4, filtered.totalElements()); assertEquals(1, filtered.totalPages()); assertTrue(filtered.last());
        for (var row : filtered.content()) {
            assertEquals("나눔게시판", row.category()); assertEquals("나래", row.dormitory()); assertFalse(row.liked());
        }
        var mine = service.getMyPosts(a.getUserId(), 0, 20, sort);
        assertEquals(12, mine.totalElements()); assertTrue(mine.first()); assertTrue(mine.last());
        assertTrue(mine.content().stream().allMatch(row -> row.author().id().equals(a.getUserId())));
    }

    @Test void writesRefreshListsAndDetailWithoutMixingUsersOrCommentLikes() {
        long id = ids.getFirst();
        var post = em.find(CommunityPost.class, id);
        var comment = new CommunityComment(post, a, null, "독립 댓글"); em.persist(comment);
        var commentLike = new CommentLike(comment, b); em.persist(commentLike); em.flush();
        service.likePost(a.getUserId(), id); service.likePost(a.getUserId(), id);
        service.likePost(b.getUserId(), id);
        var rowA = listRow(a.getUserId(), id); var rowB = listRow(b.getUserId(), id);
        assertEquals(2, rowA.likes()); assertTrue(rowA.liked()); assertTrue(rowB.liked());
        assertFalse(listRow(null, id).liked());
        assertTrue(rowA.anonymous()); assertEquals("익명", rowA.author().nickname());
        assertEquals(a.getUserId(), rowA.author().id()); assertNull(rowA.author().major());
        var detail = service.getPost(a.getUserId(), id);
        assertEquals(2, detail.likes()); assertTrue(detail.liked());
        service.unlikePost(a.getUserId(), id); service.unlikePost(a.getUserId(), id);
        assertEquals(1, listRow(a.getUserId(), id).likes()); assertFalse(listRow(a.getUserId(), id).liked());
        assertTrue(listRow(b.getUserId(), id).liked());
        assertTrue(commentLikes.existsByComment_CommentIdAndUser_UserId(comment.getCommentId(), b.getUserId()));
    }

    @Test void deletedPostsAndEmptyPagesSkipLikeReadsAndPreserveMetadata() {
        long id = ids.getFirst(); service.deletePost(a.getUserId(), id);
        em.flush();
        var page = service.getPosts(null, new CommunityPostListRequest(0, 30, null, null, "LIKES"));
        assertEquals(23, page.totalElements()); assertTrue(page.content().stream().noneMatch(p -> p.id() == id));
        var error = assertThrows(CustomException.class, () -> service.likePost(b.getUserId(), id));
        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var empty = service.getPosts(a.getUserId(), new CommunityPostListRequest(0, 6, null, "no-such-content", "LIKES"));
        assertTrue(empty.content().isEmpty()); assertEquals(0, empty.totalElements()); assertEquals(0, empty.totalPages());
        assertTrue(empty.first()); assertTrue(empty.last());
        assertFalse(Arrays.stream(stats.getQueries()).anyMatch(q -> q.contains("from PostLike l")));
    }

    private CommunityPostResponse listRow(Long viewer, long id) {
        return service.getPosts(viewer, new CommunityPostListRequest(0, 30, null, null, "LATEST"))
                .content().stream().filter(row -> row.id() == id).findFirst().orElseThrow();
    }
}
