package com.kkirikkiri.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.matching.entity.RoommateMatch;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.global.exception.*;

/** Cold persistence contexts; fixture SQL is excluded. H2 counts do not establish MySQL latency/plans. */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kkirikkiri.chat.service.ChatRoomReadPerformanceIntegrationTest$SqlCapture"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatRoomReadPerformanceIntegrationTest {
    public static class SqlCapture implements StatementInspector {
        private static final ThreadLocal<List<String>> SQL = ThreadLocal.withInitial(ArrayList::new);
        @Override public String inspect(String sql) { SQL.get().add(sql.toLowerCase(Locale.ROOT)); return sql; }
        static void reset() { SQL.get().clear(); }
        static List<String> statements() { return List.copyOf(SQL.get()); }
    }

    @Autowired EntityManager em;
    @Autowired ChatService chat;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean(name = "matchingRefreshExecutor") Executor refreshExecutor;

    private record Fixture(long owner, List<Long> rooms, List<Long> targets) { }

    private Fixture fixtures(int count) {
        return new TransactionTemplate(manager).execute(status -> {
            String key = UUID.randomUUID().toString();
            var dormitory = new Dormitory("cr-" + key.substring(0, 8), "나래-" + key.substring(0, 8), true);
            var faculty = new Faculty("chat-read-" + key, true);
            em.persist(dormitory); em.persist(faculty);
            var major = new Major(faculty, "전공-" + key.substring(0, 8), true); em.persist(major);
            var owner = new User("test-hash", key + "owner@test.invalid"); em.persist(owner);
            var rooms = new ArrayList<Long>();
            var targets = new ArrayList<Long>();
            for (int i = 0; i < count; i++) {
                var other = new User("test-hash", key + i + "@test.invalid"); em.persist(other);
                em.persist(new UserProfile(other, "cr-" + key.substring(0, 8) + "-" + i,
                        "남성", dormitory, faculty, major, (short) 2004, "24", "2학년", "INTJ"));
                em.persist(new RoommateMatch(owner, other, 80, null));
                var room = new ChatRoom(owner.getUserId(), other.getUserId(), ChatRoomCategory.ROOMMATE, null, null);
                em.persist(room);
                rooms.add(room.getChatRoomId()); targets.add(other.getUserId());
                em.persist(new ChatRoomMember(room, owner));
                em.persist(new ChatRoomMember(room, other));
                em.persist(new ChatMessage(room, other, "incoming"));
                em.persist(new ChatMessage(room, owner, "outgoing"));
                var read = new ChatMessage(room, other, "already-read"); read.markRead(); em.persist(read);
            }
            return new Fixture(owner.getUserId(), rooms, targets);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 60})
    void measuresRoomReadQueryCount(int count) throws Exception {
        long owner = fixtures(count).owner();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear(); SqlCapture.reset();
        var result = chat.getRooms(owner, new ChatRoomListRequest("ALL", null));
        long sql = statistics.getPrepareStatementCount();
        assertEquals(Math.min(count, 50), result.content().size());
        assertEquals(count, result.totalElements());
        assertEquals(count, result.counts().roommate());
        assertTrue(result.content().stream().allMatch(room -> room.unreadCount() == 1 && room.counterpart().matchScore() == 80));
        assertTrue(sql <= 7, "A page must use a bounded number of SQL statements, not one set per room");
        assertTrue(SqlCapture.statements().stream().anyMatch(statement -> statement.contains("order by")
                && (statement.contains("fetch first") || statement.contains("limit"))), "Pagination/order must be in SQL");
        assertEquals(0, statistics.getEntityStatistics(ChatRoom.class.getName()).getLoadCount());
        assertEquals(0, statistics.getEntityStatistics(ChatRoomMember.class.getName()).getLoadCount());
        String evidence = "AFTER,rooms=" + count + ",returned=" + result.content().size() + ",sql=" + sql + System.lineSeparator();
        System.out.print("CHAT_READ_PERF " + evidence);
        var directory = Path.of("build/chat-read-performance"); Files.createDirectories(directory);
        Files.writeString(directory.resolve("after.csv"), evidence, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void messagePagesRespectTheMaximumAndKeepTimestampIdOrderAndAuthorization() {
        var f = fixtures(1);
        long roomId = f.rooms().getFirst();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var room = em.getReference(ChatRoom.class, roomId);
            var sender = em.getReference(User.class, f.targets().getFirst());
            for (int i = 0; i < 125; i++) em.persist(new ChatMessage(room, sender, "message-" + i));
            em.flush();
            em.createNativeQuery("UPDATE chat_message SET sent_at=:time WHERE chat_room_id=:id")
                    .setParameter("time", LocalDateTime.of(2026, 1, 1, 12, 0)).setParameter("id", roomId).executeUpdate();
        });
        var first = chat.getMessages(f.owner(), roomId, new ChatMessageListRequest(0, 100, null));
        var second = chat.getMessages(f.owner(), roomId, new ChatMessageListRequest(1, 100, null));
        assertEquals(100, first.content().size());
        assertEquals(28, second.content().size());
        assertEquals(128, first.totalElements());
        var ids = new ArrayList<Long>();
        ids.addAll(first.content().stream().map(ChatMessageResponse::id).toList());
        ids.addAll(second.content().stream().map(ChatMessageResponse::id).toList());
        assertEquals(ids.stream().sorted(Comparator.reverseOrder()).toList(), ids);
        assertEquals(128, new HashSet<>(ids).size());
        assertEquals(50, chat.getMessages(f.owner(), roomId, null).content().size());
        assertTrue(chat.getMessages(f.owner(), roomId, new ChatMessageListRequest(3, 100, null)).content().isEmpty());
        long outsider = fixtures(0).owner();
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class,
                () -> chat.getMessages(outsider, roomId, null)).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class,
                () -> chat.getRoom(outsider, roomId)).getErrorCode());
    }

    @Test
    void emptyUserAndEmptyPageDoNotLoadUnrelatedProfilesOrMessages() {
        long empty = fixtures(0).owner();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear(); SqlCapture.reset();
        var page = chat.getRooms(empty, null);
        assertTrue(page.content().isEmpty());
        assertEquals(new ChatRoomCountsResponse(0, 0, 0, 0), page.counts());
        assertTrue(statistics.getPrepareStatementCount() <= 3);
        assertEquals(0, statistics.getEntityLoadCount());
        var fixture = fixtures(1);
        var pastEnd = chat.getRooms(fixture.owner(), new ChatRoomListRequest("ALL", null, 8, 1));
        assertTrue(pastEnd.content().isEmpty());
        assertEquals(1, pastEnd.totalElements());
        assertTrue(pastEnd.last());
    }

    @Test
    void pagesHaveStableOrderAndReactToMessagesPinsAndLeave() {
        var f = fixtures(6);
        var time = LocalDateTime.of(2026, 1, 1, 12, 0);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            for (long id : f.rooms()) em.createNativeQuery("UPDATE chat_message SET sent_at=:time WHERE chat_room_id=:id")
                    .setParameter("time", time).setParameter("id", id).executeUpdate();
            em.createNativeQuery("UPDATE chat_message SET sent_at=:time WHERE chat_room_id=:id")
                    .setParameter("time", time.minusDays(1)).setParameter("id", f.rooms().get(0)).executeUpdate();
            em.createNativeQuery("DELETE FROM chat_message WHERE chat_room_id=:id").setParameter("id", f.rooms().get(1)).executeUpdate();
        });
        chat.updateSettings(f.owner(), f.rooms().get(0), new ChatRoomSettingsRequest(true, null));
        chat.updateSettings(f.owner(), f.rooms().get(1), new ChatRoomSettingsRequest(true, null));
        chat.sendMessage(f.targets().get(2), f.rooms().get(2), new ChatMessageSendRequest("newest"));
        var all = new ArrayList<Long>();
        for (int i = 0; i < 3; i++) {
            var page = chat.getRooms(f.owner(), new ChatRoomListRequest("ALL", null, i, 2));
            assertEquals(6, page.totalElements());
            assertEquals(2, page.counts().pinned());
            all.addAll(page.content().stream().map(ChatRoomResponse::id).toList());
        }
        assertEquals(List.of(f.rooms().get(0), f.rooms().get(1), f.rooms().get(2), f.rooms().get(5), f.rooms().get(4), f.rooms().get(3)), all);
        assertEquals(all.size(), new HashSet<>(all).size());
        var page = chat.getRooms(f.owner(), new ChatRoomListRequest("ALL", null, 0, 100));
        assertNull(page.content().get(1).lastMessage());
        assertEquals("newest", page.content().get(2).lastMessage().text());
        assertEquals(2, page.content().get(2).unreadCount());
        assertEquals(page.content().get(2), chat.getRoom(f.owner(), f.rooms().get(2)));
        chat.leaveRoom(f.owner(), f.rooms().get(4));
        chat.updateSettings(f.owner(), f.rooms().get(0), new ChatRoomSettingsRequest(false, null));
        var after = chat.getRooms(f.owner(), null);
        assertEquals(5, after.totalElements());
        assertEquals(5, after.counts().all());
        assertEquals(f.rooms().get(1), after.content().getFirst().id());
        assertFalse(after.content().stream().anyMatch(room -> room.id().equals(f.rooms().get(4))));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class, () -> chat.getRoom(f.owner(), f.rooms().get(4))).getErrorCode());
    }

    @Test
    void databaseSearchMasksAnonymousPostsAndCommentsBeforeFiltering() {
        var f = fixtures(3);
        String nickname = "CASE%_!Public";
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var profile = em.createQuery("from UserProfile p where p.user.userId=:id", UserProfile.class)
                    .setParameter("id", f.targets().get(0)).getSingleResult();
            profile.changeNickname(nickname);
            for (int i = 0; i < 3; i++) {
                var author = em.getReference(User.class, f.targets().get(i));
                var owner = em.getReference(User.class, f.owner());
                var post = new CommunityPost(author, null, "자유게시판", "source", "body", i == 1);
                em.persist(post);
                CommunityComment comment = null;
                if (i == 2) {
                    comment = new CommunityComment(post, author, null, "comment"); comment.changeAnonymous(true); em.persist(comment);
                }
                var room = new ChatRoom(f.owner(), author.getUserId(), ChatRoomCategory.COMMUNITY,
                        comment == null ? post : null, comment);
                em.persist(room); em.persist(new ChatRoomMember(room, owner)); em.persist(new ChatRoomMember(room, author));
            }
            em.persist(new ProfileSurvey(em.getReference(User.class, f.targets().get(0)), "23:00", "07:00", "none",
                    "none", "none", "low", "yes", "non-smoker", "dark", "quiet", "headphones", "morning",
                    "short", "daily", "tidy", "regular", "friendly", "normal", "none", "yes", "ask", true));
        });
        var anonymous = chat.getRooms(f.owner(), new ChatRoomListRequest("COMMUNITY", " 익명 "));
        assertEquals(2, anonymous.totalElements());
        assertEquals(new ChatRoomCountsResponse(6, 3, 3, 0), anonymous.counts());
        assertTrue(anonymous.content().stream().allMatch(room -> room.counterpart().equals(
                new ChatRoomResponse.CounterpartResponse(null, "익명", null, null, null, null, null, null, List.of()))));
        assertTrue(anonymous.content().stream().allMatch(room -> room.sourcePost() != null));
        var publicMatches = chat.getRooms(f.owner(), new ChatRoomListRequest("ALL", "  case%_!public  "));
        assertEquals(2, publicMatches.totalElements());
        assertEquals(Set.of(ChatRoomCategory.COMMUNITY, ChatRoomCategory.ROOMMATE), publicMatches.content().stream()
                .map(ChatRoomResponse::category).collect(java.util.stream.Collectors.toSet()));
        assertTrue(publicMatches.content().stream().allMatch(room -> room.counterpart().nickname().equals(nickname)
                && room.counterpart().tags().equals(List.of("non-smoker", "23:00", "daily"))));
        String hiddenName = new TransactionTemplate(manager).execute(status -> em.createQuery("select p.nickname from UserProfile p where p.user.userId=:id", String.class)
                .setParameter("id", f.targets().get(1)).getSingleResult());
        assertTrue(chat.getRooms(f.owner(), new ChatRoomListRequest("COMMUNITY", hiddenName)).content().isEmpty());
        assertEquals(1, chat.getRooms(f.owner(), new ChatRoomListRequest("ROOMMATE", hiddenName)).totalElements());
    }
}
