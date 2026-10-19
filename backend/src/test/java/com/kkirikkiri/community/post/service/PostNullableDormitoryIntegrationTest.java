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
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.user.entity.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Transactional
class PostNullableDormitoryIntegrationTest {
    @Autowired EntityManager em;
    @Autowired CommunityPostService service;
    User a, b;
    final List<Row> rows = new ArrayList<>();
    record Row(long id, long author, String dorm, String category, String title, String body,
               LocalDateTime created, int views, int likes, boolean active) {}
    record Filter(String category, String keyword, String dorm) {}

    @BeforeEach void fixtures() {
        String suffix = UUID.randomUUID().toString();
        a = new User("test-hash", suffix + "a@test.invalid");
        b = new User("test-hash", suffix + "b@test.invalid");
        em.persist(a); em.persist(b);
        var dorm = new Dormitory("n1-a-" + suffix.substring(0, 8), "나래", true);
        var other = new Dormitory("n1-b-" + suffix.substring(0, 8), "호연", true);
        em.persist(dorm); em.persist(other);
        for (int i = 0; i < 24; i++) {
            var owner = i % 2 == 0 ? a : b;
            var location = i % 5 == 0 || i == 23 ? null : i % 2 == 0 ? dorm : other;
            String category = i % 3 == 0 ? "나눔게시판" : "자유게시판";
            String title = i == 0 ? "target-null-only" : "N1 post " + i;
            String body = (i % 2 == 0 ? "needle" : "other") + " body " + i;
            var p = new CommunityPost(owner, location, category, title, body, i == 0);
            ReflectionTestUtils.setField(p, "viewCount", i % 4);
            if (i == 23) p.delete();
            em.persist(p);
            var created = LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(i / 3);
            em.createNativeQuery("update community_post set created_at=:at where post_id=:id")
                    .setParameter("at", created).setParameter("id", p.getPostId()).executeUpdate();
            if (i % 3 > 0) em.persist(new PostLike(p, a));
            if (i % 3 == 2) em.persist(new PostLike(p, b));
            rows.add(new Row(p.getPostId(), owner.getUserId(), location == null ? null : location.getDormitoryName(),
                    category, title, body, created, i % 4, i % 3, i != 23));
        }
        em.flush(); em.clear();
    }

    List<Row> expected(String sort, Filter filter, Long author) {
        Comparator<Row> order = Comparator.comparing(Row::created).reversed()
                .thenComparing(Comparator.comparingLong(Row::id).reversed());
        if (sort.equals("VIEWS")) order = Comparator.comparingInt(Row::views).reversed().thenComparing(order);
        if (sort.equals("LIKES")) order = Comparator.comparingInt(Row::likes).reversed().thenComparing(order);
        return rows.stream().filter(Row::active).filter(r -> author == null || r.author == author)
                .filter(r -> filter.category == null || filter.category.equals(r.category))
                .filter(r -> filter.dorm == null || filter.dorm.equals(r.dorm))
                .filter(r -> filter.keyword == null || r.title.contains(filter.keyword) || r.body.contains(filter.keyword))
                .sorted(order).toList();
    }

    void assertPage(RoommatePageResponse<CommunityPostResponse> actual, List<Row> expected,
                    int page, int size, Long viewer) {
        int from = Math.min(page * size, expected.size()), to = Math.min(from + size, expected.size());
        var selected = expected.subList(from, to);
        assertEquals(selected.stream().map(Row::id).toList(), actual.content().stream().map(CommunityPostResponse::id).toList());
        assertEquals(expected.size(), actual.totalElements());
        assertEquals((expected.size() + size - 1) / size, actual.totalPages());
        assertEquals(page, actual.page()); assertEquals(size, actual.size());
        assertEquals(page == 0, actual.first());
        assertEquals(page >= actual.totalPages() - 1, actual.last());
        for (int i = 0; i < selected.size(); i++) {
            var row = selected.get(i); var response = actual.content().get(i);
            assertEquals(row.dorm, response.dormitory()); assertEquals(row.likes, response.likes());
            boolean liked = viewer != null && (viewer.equals(a.getUserId()) ? row.likes > 0 : row.likes == 2);
            assertEquals(liked, response.liked());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"LATEST", "VIEWS", "LIKES"})
    void publicListsKeepNullableDormitoryAcrossFiltersAndPages(String sort) {
        var noFilter = new Filter(null, null, null);
        var expected = expected(sort, noFilter, null);
        assertEquals(23, expected.size());
        for (Long viewer : Arrays.asList(null, a.getUserId(), b.getUserId())) {
            for (int size : List.of(1, 6)) {
                var allIds = new HashSet<Long>();
                int pages = (expected.size() + size - 1) / size;
                for (int page = 0; page <= pages; page++) {
                    var result = service.getPosts(viewer, new CommunityPostListRequest(page, size, null, null, sort));
                    assertPage(result, expected, page, size, viewer);
                    for (var row : result.content()) assertTrue(allIds.add(row.id()), "Duplicate across pages");
                }
                assertEquals(new HashSet<>(expected.stream().map(Row::id).toList()), allIds);
            }
            for (var filter : List.of(new Filter("나눔게시판", null, null), new Filter(null, "needle", null),
                    new Filter(null, null, "나래"), new Filter(null, null, "호연"),
                    new Filter("나눔게시판", "needle", "나래"), new Filter(null, "no-such-post", null))) {
                var result = service.getPosts(viewer, new CommunityPostListRequest(0, 6, filter.category, filter.keyword, filter.dorm, sort));
                assertPage(result, expected(sort, filter, null), 0, 6, viewer);
            }
        }
        for (var owner : List.of(a, b)) assertPage(service.getMyPosts(owner.getUserId(), 0, 20, sort),
                expected(sort, noFilter, owner.getUserId()), 0, 20, owner.getUserId());
    }

    @ParameterizedTest @CsvSource({"1,false", "6,false", "20,false", "1,true", "6,true", "20,true"})
    void nullablePagesKeepLikeBatchQueriesBounded(int size, boolean guest) {
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        Long viewer = guest ? null : a.getUserId();
        var page = service.getPosts(viewer, new CommunityPostListRequest(0, size, null, null, "LATEST"));
        assertPage(page, expected("LATEST", new Filter(null, null, null), null), 0, size, viewer);
        long queries = Arrays.stream(stats.getQueries()).filter(q -> q.contains("from PostLike l"))
                .mapToLong(q -> stats.getQueryStatistics(q).getExecutionCount()).sum();
        assertEquals(guest ? 1 : 2, queries);
        assertFalse(Arrays.stream(stats.getQueries()).anyMatch(q -> q.startsWith("select count(*) from post_like")));
        stats.clear();
        assertTrue(service.getPosts(viewer, new CommunityPostListRequest(0, size, null, "no-such-post", "LATEST")).content().isEmpty());
        assertFalse(Arrays.stream(stats.getQueries()).anyMatch(q -> q.contains("from PostLike l")));
    }

    @Test void nullablePostLikesAndDetailKeepViewerAndAnonymousContracts() {
        long id = rows.getFirst().id;
        for (int i = 0; i < 2; i++) service.likePost(a.getUserId(), id);
        service.likePost(b.getUserId(), id);
        for (Long viewer : Arrays.asList(null, a.getUserId(), b.getUserId())) {
            var detail = service.getPost(viewer, id);
            assertNull(detail.dormitory()); assertEquals(2, detail.likes()); assertEquals(viewer != null, detail.liked());
            assertNull(detail.author().id()); assertEquals("익명", detail.author().nickname());
            assertEquals(a.getUserId().equals(viewer), detail.mine());
            for (String sort : List.of("LATEST", "VIEWS", "LIKES")) {
                var page = service.getPosts(viewer, new CommunityPostListRequest(0, 6, null, "target-null-only", sort));
                assertEquals(1, page.totalElements());
                assertEquals(id, page.content().getFirst().id()); assertEquals(2, page.content().getFirst().likes());
                assertEquals(viewer != null, page.content().getFirst().liked());
            }
        }
        for (int i = 0; i < 2; i++) service.unlikePost(a.getUserId(), id);
        assertEquals(1, service.getPost(a.getUserId(), id).likes());
        assertFalse(service.getPost(a.getUserId(), id).liked()); assertTrue(service.getPost(b.getUserId(), id).liked());
        var failure = assertThrows(CustomException.class, () -> service.likePost(a.getUserId(), rows.getLast().id));
        assertEquals(ErrorCode.INVALID_REQUEST, failure.getErrorCode());
    }
}
