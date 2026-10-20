package com.kkirikkiri.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.repository.ChatMessageRepository;
import com.kkirikkiri.chat.repository.ChatRoomMemberRepository;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.user.entity.User;

/** Independent real JPA transactions. H2 evidence does not establish MySQL/InnoDB behavior. */
@SpringBootTest
class ChatReadConcurrencyIntegrationTest {
    @Autowired ChatService chat;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatRoomMemberRepository members;
    @MockitoBean(name = "matchingRefreshExecutor") Executor refreshExecutor;

    private TransactionTemplate tx;
    private ExecutorService workers;
    private long readerId, writerId, roomId, memberId;

    @BeforeEach
    void fixtures() {
        tx = new TransactionTemplate(manager);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        tx.executeWithoutResult(status -> {
            var reader = new User("test-hash", UUID.randomUUID() + "@test.invalid");
            var writer = new User("test-hash", UUID.randomUUID() + "@test.invalid");
            em.persist(reader);
            em.persist(writer);
            var room = new ChatRoom(reader.getUserId(), writer.getUserId(), ChatRoomCategory.ROOMMATE, null, null);
            em.persist(room);
            var member = new ChatRoomMember(room, reader);
            em.persist(member);
            em.persist(new ChatRoomMember(room, writer));
            readerId = reader.getUserId();
            writerId = writer.getUserId();
            roomId = room.getChatRoomId();
            memberId = member.getChatRoomMemberId();
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM chat_message WHERE chat_room_id=?", roomId);
            jdbc.update("DELETE FROM chat_room_member WHERE chat_room_id=?", roomId);
            jdbc.update("DELETE FROM chat_room WHERE chat_room_id=?", roomId);
            jdbc.update("DELETE FROM `user` WHERE user_id IN (?,?)", readerId, writerId);
        });
    }

    @Test
    void staleManagedReadCannotRestoreMembershipAfterLeave() throws Exception {
        long incoming = persistMessage("incoming", null);
        staleMemberAfterLeave(ignored -> acknowledge(incoming));
    }

    @Test
    void staleManagedSettingsCannotRestoreMembershipAfterLeave() throws Exception {
        staleMemberAfterLeave(ignored -> chat.updateSettings(readerId, roomId, new ChatRoomSettingsRequest(true, true)));
    }

    private void staleMemberAfterLeave(Consumer<ChatRoomMember> mutation) throws Exception {
        var loaded = new CountDownLatch(1);
        var left = new CountDownLatch(1);
        var pending = workers.submit(() -> tx.execute(status -> {
            var stale = em.find(ChatRoomMember.class, memberId);
            assertFalse(stale.isLeft());
            loaded.countDown();
            await(left);
            try {
                mutation.accept(stale);
                return null;
            } catch (CustomException failure) {
                status.setRollbackOnly();
                return failure.getErrorCode();
            }
        }));
        try {
            await(loaded);
            chat.leaveRoom(readerId, roomId);
        } finally {
            left.countDown();
        }
        assertEquals(ErrorCode.FORBIDDEN, pending.get(15, TimeUnit.SECONDS));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_left FROM chat_room_member WHERE chat_room_member_id=?", Boolean.class, memberId));
        assertNotNull(jdbc.queryForObject("SELECT left_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
    }

    @Test
    void lateCommitRemainsUnreadWhenOnlyVisibleMessageWasAcknowledged() throws Exception {
        var inserted = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var first = workers.submit(() -> tx.execute(status -> {
            long id = persistMessage("A-late-commit", null);
            inserted.countDown();
            await(finish);
            return id;
        }));
        try {
            await(inserted);
            long second = persistMessage("B-first-commit", null);
            var visible = chat.getMessages(readerId, roomId, null);
            assertEquals(List.of(second), visible.content().stream().map(ChatMessageResponse::id).toList());
            acknowledge(second);
        } finally {
            finish.countDown();
        }
        first.get(15, TimeUnit.SECONDS);
        long unread = messages.countUnreadMessages(roomId, readerId);
        assertEquals(1, unread, "A was not visible and must remain unread after its delayed commit");
    }

    private void acknowledge(Long... ids) {
        chat.markRead(readerId, roomId, new ChatReadRequest(List.of(ids)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "settings"})
    void membershipWriteAndLeaveCommitWithoutRestoringAnyState(String action) throws Exception {
        long incoming = persistMessage("incoming", null);
        var changed = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var write = workers.submit(() -> tx.executeWithoutResult(status -> {
            if (action.equals("read")) acknowledge(incoming);
            else chat.updateSettings(readerId, roomId, new ChatRoomSettingsRequest(true, true));
            changed.countDown();
            await(commit);
        }));
        await(changed);
        var enteredLeave = new CountDownLatch(1);
        var leave = workers.submit(() -> {
            enteredLeave.countDown();
            chat.leaveRoom(readerId, roomId);
        });
        try {
            await(enteredLeave);
        } finally {
            commit.countDown();
        }
        write.get(15, TimeUnit.SECONDS);
        leave.get(15, TimeUnit.SECONDS);
        assertTrue(jdbc.queryForObject("SELECT is_left FROM chat_room_member WHERE chat_room_member_id=?", Boolean.class, memberId));
        assertNotNull(jdbc.queryForObject("SELECT left_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
        assertEquals(action.equals("settings"), jdbc.queryForObject("SELECT is_muted FROM chat_room_member WHERE chat_room_member_id=?", Boolean.class, memberId));
    }

    @Test
    void reverseCompletionOfReadRequestsCannotMoveReadTimeBackwards() throws Exception {
        var earlier = LocalDateTime.of(2026, 1, 1, 12, 0);
        var later = earlier.plusMinutes(1);
        long older = persistMessage("older", earlier);
        long newer = persistMessage("newer", later);
        var loaded = new CountDownLatch(1);
        var continueOld = new CountDownLatch(1);
        var oldRequest = workers.submit(() -> tx.executeWithoutResult(status -> {
            assertNull(em.find(ChatRoomMember.class, memberId).getLastReadAt());
            loaded.countDown();
            await(continueOld);
            acknowledge(older);
        }));
        try {
            await(loaded);
            acknowledge(newer);
        } finally {
            continueOld.countDown();
        }
        oldRequest.get(15, TimeUnit.SECONDS);
        assertEquals(later, jdbc.queryForObject("SELECT last_read_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
        assertEquals(0, messages.countUnreadMessages(roomId, readerId));
        int changed = tx.execute(status -> members.advanceReadTime(memberId, earlier));
        assertEquals(0, changed);
        assertEquals(later, jdbc.queryForObject("SELECT last_read_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
    }

    @Test
    void equalTimestampsHaveIndependentReceiptsAndKeepExistingSortAndSearch() {
        var sameTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        long first = persistMessage("first", sameTime);
        long second = persistMessage("second", sameTime);
        var page = chat.getMessages(readerId, roomId, new ChatMessageListRequest(0, 1, null));
        assertEquals(List.of(second), page.content().stream().map(ChatMessageResponse::id).toList());
        acknowledge(second);
        var history = chat.getMessages(readerId, roomId, null).content();
        assertEquals(List.of(second, first), history.stream().map(ChatMessageResponse::id).toList());
        assertTrue(history.getFirst().read());
        assertFalse(history.getLast().read());
        assertEquals(1, messages.countUnreadMessages(roomId, readerId));
        assertEquals(List.of(first), chat.getMessages(readerId, roomId, new ChatMessageListRequest(0, 50, "first"))
                .content().stream().map(ChatMessageResponse::id).toList());
        assertTrue(chat.getMessages(writerId, roomId, null).content().getFirst().read());
    }

    @Test
    void newMessageDuringReadTransactionDoesNotInheritReceipt() throws Exception {
        var sameTime = LocalDateTime.of(2026, 1, 1, 12, 0);
        long seen = persistMessage("seen", sameTime);
        var acknowledged = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        var pending = workers.submit(() -> tx.executeWithoutResult(status -> {
            acknowledge(seen);
            acknowledged.countDown();
            await(commit);
        }));
        long unseen;
        try {
            await(acknowledged);
            unseen = persistMessage("unseen", sameTime);
        } finally {
            commit.countDown();
        }
        pending.get(15, TimeUnit.SECONDS);
        assertEquals(1, messages.countUnreadMessages(roomId, readerId));
        assertFalse(chat.getMessages(readerId, roomId, null).content().stream().filter(m -> m.id() == unseen).findFirst().orElseThrow().read());
    }

    @Test
    void rollbackPreservesMembershipReceiptsAndTimeAndAllowsRetry() {
        long incoming = persistMessage("incoming", null);
        tx.executeWithoutResult(status -> {
            acknowledge(incoming);
            chat.updateSettings(readerId, roomId, new ChatRoomSettingsRequest(true, true));
            chat.leaveRoom(readerId, roomId);
            em.flush();
            status.setRollbackOnly();
        });
        assertFalse(jdbc.queryForObject("SELECT is_left FROM chat_room_member WHERE chat_room_member_id=?", Boolean.class, memberId));
        assertFalse(jdbc.queryForObject("SELECT is_pinned FROM chat_room_member WHERE chat_room_member_id=?", Boolean.class, memberId));
        assertNull(jdbc.queryForObject("SELECT last_read_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
        assertEquals(1, messages.countUnreadMessages(roomId, readerId));
        acknowledge(incoming);
        assertEquals(0, messages.countUnreadMessages(roomId, readerId));
    }

    @Test
    void leavingForbidsReadAndSettingsAndDoesNotChangeReceipts() {
        long incoming = persistMessage("incoming", null);
        chat.leaveRoom(readerId, roomId);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class, () -> acknowledge(incoming)).getErrorCode());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class,
                () -> chat.updateSettings(readerId, roomId, new ChatRoomSettingsRequest(true, true))).getErrorCode());
        assertEquals(1, messages.countUnreadMessages(roomId, readerId));
    }

    @Test
    void invalidOrOwnMessageIdsRejectWholeReceiptBatch() {
        long incoming = persistMessage("incoming", null);
        long own = tx.execute(status -> {
            var message = new ChatMessage(em.getReference(ChatRoom.class, roomId), em.getReference(User.class, readerId), "own");
            em.persist(message);
            return message.getMessageId();
        });
        for (long invalid : new long[]{own, Long.MAX_VALUE}) {
            assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(CustomException.class, () -> acknowledge(incoming, invalid)).getErrorCode());
            assertEquals(1, messages.countUnreadMessages(roomId, readerId));
            assertNull(jdbc.queryForObject("SELECT last_read_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
        }
        acknowledge(incoming, incoming);
        acknowledge(incoming);
        assertEquals(0, messages.countUnreadMessages(roomId, readerId));
    }

    @Test
    void h2RepeatableReadRejectsAnInvisibleReceiptAndFreshTransactionCanRetry() {
        var rr = new TransactionTemplate(manager);
        rr.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var incomingId = new java.util.concurrent.atomic.AtomicLong();
        rr.executeWithoutResult(status -> {
            assertTrue(chat.getMessages(readerId, roomId, null).content().isEmpty());
            long incoming;
            try {
                incoming = workers.submit(() -> persistMessage("committed-after-RR-snapshot", null)).get(15, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            incomingId.set(incoming);
            // H2 RR does not expose this inserted row even to SELECT ... FOR UPDATE.
            // MySQL/InnoDB current reads differ and require isolated MySQL validation.
            assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(CustomException.class, () -> acknowledge(incoming)).getErrorCode());
            status.setRollbackOnly();
        });
        assertEquals(1, messages.countUnreadMessages(roomId, readerId));
        assertNull(jdbc.queryForObject("SELECT last_read_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
        acknowledge(incomingId.get());
        chat.updateSettings(readerId, roomId, new ChatRoomSettingsRequest(null, true));
        assertEquals(0, messages.countUnreadMessages(roomId, readerId));
        assertTrue(jdbc.queryForObject("SELECT is_muted FROM chat_room_member WHERE chat_room_member_id=?", Boolean.class, memberId));
        assertNotNull(jdbc.queryForObject("SELECT last_read_at FROM chat_room_member WHERE chat_room_member_id=?", LocalDateTime.class, memberId));
    }

    private long persistMessage(String text, LocalDateTime sentAt) {
        return tx.execute(status -> {
            var message = new ChatMessage(em.getReference(ChatRoom.class, roomId), em.getReference(User.class, writerId), text);
            em.persist(message);
            em.flush();
            if (sentAt != null) {
                jdbc.update("UPDATE chat_message SET sent_at=? WHERE message_id=?", sentAt, message.getMessageId());
            }
            return message.getMessageId();
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Controlled transaction did not reach the expected boundary");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
