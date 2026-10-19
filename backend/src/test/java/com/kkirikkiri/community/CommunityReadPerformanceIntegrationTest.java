package com.kkirikkiri.community;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.dto.CommunityPostListRequest;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.service.CommunityPostService;
import com.kkirikkiri.user.entity.*;

/** Cold persistence contexts; fixture SQL is excluded from every measurement. */
@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kkirikkiri.community.CommunityReadPerformanceIntegrationTest$SqlCapture"})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommunityReadPerformanceIntegrationTest {
    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);
        private static final ThreadLocal<java.util.function.Consumer<String>> HOOK = new ThreadLocal<>();
        public String inspect(String sql) {
            String normalized = sql.toLowerCase();
            SQL.get().add(normalized);
            if (HOOK.get() != null) HOOK.get().accept(normalized);
            return sql;
        }
        static void hook(java.util.function.Consumer<String> hook) { HOOK.set(hook); }
        static void unhook() { HOOK.remove(); }
        static void reset() { SQL.get().clear(); }
        static List<String> statements() { return List.copyOf(SQL.get()); }
    }

    @Autowired EntityManager em;
    @Autowired CommunityPostService posts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.kkirikkiri.admin.service.AdminService admins;
    Long viewerId;
    Long adminId;
    final List<Long> details = new ArrayList<>();
    final List<Long> authorIds = new ArrayList<>();
    final int[] sizes = {1, 10, 50, 100};

    @BeforeEach
    void fixtures() {
        if (viewerId != null) return;
        details.clear();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            String key = UUID.randomUUID().toString();
            Dormitory dormitory = new Dormitory("perf-" + key.substring(0, 8), "나래", true);
            Faculty faculty = new Faculty("perf-" + key, true);
            em.persist(dormitory); em.persist(faculty);
            Major major = new Major(faculty, "perf-major", true); em.persist(major);
            List<User> authors = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                User user = new User("hash", key + i + "@test.invalid"); em.persist(user); authors.add(user);
                authorIds.add(user.getUserId());
                em.persist(new UserProfile(user, "perf-" + key.substring(0, 8) + i, "남성", dormitory, faculty,
                        major, (short) 2004, "24", "2학년", "INTJ"));
                em.persist(new CommunityPost(user, dormitory, "자유게시판", "perf-list", "body"));
            }
            viewerId = authors.getFirst().getUserId();
            User admin = new User("hash", key + "admin@test.invalid", UserRole.ADMIN);
            em.persist(admin); adminId = admin.getUserId();
            for (int count : sizes) {
                CommunityPost post = new CommunityPost(authors.getFirst(), dormitory, "나눔게시판", "detail", "body");
                em.persist(post); details.add(post.getPostId());
                for (int i = 0; i < count; i++) em.persist(new CommunityComment(post, authors.get(i), null, "comment " + i));
            }
        });
    }

    private long measure(Runnable query, String label) {
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear(); SqlCapture.reset();
        query.run();
        long count = statistics.getPrepareStatementCount();
        List<String> statements = SqlCapture.statements();
        int firstLock = -1;
        for (int i = 0; i < statements.size(); i++) {
            String sql = statements.get(i);
            if (sql.contains("community_post") && (sql.contains("for update") || sql.startsWith("update "))) { firstLock = i; break; }
        }
        System.out.println("COMMUNITY_PERF AFTER " + label + " sql=" + count
                + " postLockIndex=" + firstLock + " statementsAfterLock=" + (firstLock < 0 ? 0 : statements.size() - firstLock - 1));
        if (label.startsWith("DETAIL")) {
            assertTrue(firstLock > 0);
            assertTrue(statements.subList(firstLock + 1, statements.size()).stream()
                    .noneMatch(sql -> sql.contains("user_profile") || sql.contains("post_image") || sql.contains("comment_like")),
                    "Profile, image and comment-like reads must finish before the post write lock");
        }
        return count;
    }

    @Test
    void listQueryCountIsBoundedForOneTenFiftyAndOneHundredPosts() {
        for (boolean authenticated : List.of(false, true)) {
            for (String sort : List.of("LATEST", "VIEWS", "LIKES")) {
                List<Long> counts = new ArrayList<>();
                for (int size : sizes) {
                    long count = measure(() -> {
                        var page = posts.getPosts(authenticated ? viewerId : null,
                                new CommunityPostListRequest(0, size, "자유게시판", "perf-list", "나래", sort));
                        assertEquals(size, page.content().size());
                        assertEquals(100, page.totalElements());
                    }, "LIST viewer=" + (authenticated ? "USER" : "GUEST") + " sort=" + sort + " n=" + size);
                    counts.add(count);
                }
                {
                    assertTrue(counts.stream().mapToLong(Long::longValue).max().orElseThrow() <= 10, counts.toString());
                    assertTrue(counts.getLast() <= counts.getFirst() + 1, counts.toString());
                }
            }
        }
    }

    @Test
    void detailProfileReadsAndPostLockRegionAreBoundedAsCommentsGrow() {
        for (boolean authenticated : List.of(false, true)) {
            List<Long> counts = new ArrayList<>();
            for (int i = 0; i < sizes.length; i++) {
                int size = sizes[i]; Long postId = details.get(i);
                counts.add(measure(() -> {
                    var detail = posts.getPost(authenticated ? viewerId : null, postId);
                    assertEquals(size, detail.comments().size());
                    assertEquals(authenticated, detail.mine());
                }, "DETAIL viewer=" + (authenticated ? "USER" : "GUEST") + " n=" + size));
            }
            assertTrue(counts.getLast() <= counts.getFirst() + 1, counts.toString());
        }
    }

    @Test
    void adminPostAndCommentListsAlsoKeepQueryCountsBounded() {
        for (int size : sizes) {
            long postCount = measure(() -> assertEquals(size, admins.getPosts(adminId,
                    new com.kkirikkiri.admin.dto.AdminPostListRequest(0, size, "perf-list", null, null, "LATEST")).content().size()),
                    "ADMIN_POST n=" + size);
            long commentCount = measure(() -> assertEquals(size, admins.getComments(adminId,
                    new com.kkirikkiri.admin.dto.AdminCommentListRequest(0, size, null, null)).content().size()),
                    "ADMIN_COMMENT n=" + size);
            assertTrue(postCount <= 8, "post statements=" + postCount);
            assertTrue(commentCount <= 6, "comment statements=" + commentCount);
        }
    }

    @Test
    void warmAnonymousDetailsKeepStableNumbersWithoutProfileNPlusOne() {
        List<Long> counts = new ArrayList<>();
        for (int size : sizes) {
            Long id = new TransactionTemplate(transactions).execute(status -> {
                CommunityPost post = new CommunityPost(em.getReference(User.class, viewerId), null, "민원게시판", "anonymous", "body", true);
                em.persist(post);
                for (int i = 0; i < size; i++) {
                    CommunityComment comment = new CommunityComment(post, em.getReference(User.class, authorIds.get(i)), null, "private");
                    comment.changeAnonymous(true); em.persist(comment);
                }
                return post.getPostId();
            });
            var initialized = posts.getPost(viewerId, id);
            var numbers = initialized.comments().stream().map(comment -> comment.anonymousNumber()).toList();
            counts.add(measure(() -> {
                var detail = posts.getPost(viewerId, id);
                assertEquals(numbers, detail.comments().stream().map(comment -> comment.anonymousNumber()).toList());
                assertTrue(detail.comments().stream().allMatch(comment -> comment.author().id() == null));
                assertNull(detail.author().id());
            }, "DETAIL_ANONYMOUS_WARM viewer=USER n=" + size));
            assertTrue(SqlCapture.statements().stream().noneMatch(sql -> sql.contains("user_profile")));
        }
        assertTrue(counts.getLast() <= counts.getFirst() + 1, counts.toString());
    }
}
