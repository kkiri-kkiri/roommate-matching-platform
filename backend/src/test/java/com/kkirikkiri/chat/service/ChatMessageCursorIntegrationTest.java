package com.kkirikkiri.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.user.entity.User;

@SpringBootTest
class ChatMessageCursorIntegrationTest {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired ChatService chat;
    record Fixture(long owner, long sender, long outsider, long room, List<Long> ids) { }

    Fixture fixture(int count) {
        return new TransactionTemplate(manager).execute(status -> {
            String key = UUID.randomUUID().toString();
            var owner = new User("test-hash", key + "owner@test.invalid");
            var sender = new User("test-hash", key + "sender@test.invalid");
            var outsider = new User("test-hash", key + "other@test.invalid");
            em.persist(owner); em.persist(sender); em.persist(outsider);
            var room = new ChatRoom(owner.getUserId(), sender.getUserId(), ChatRoomCategory.ROOMMATE, null, null);
            em.persist(room); em.persist(new ChatRoomMember(room, owner)); em.persist(new ChatRoomMember(room, sender));
            var ids = new ArrayList<Long>();
            for (int i = 0; i < count; i++) {
                var message = new ChatMessage(room, sender, i % 3 == 0 ? "other" : "needle%_!" + i);
                em.persist(message); ids.add(message.getMessageId());
            }
            em.flush();
            em.createNativeQuery("UPDATE chat_message SET sent_at=:time WHERE chat_room_id=:id")
                    .setParameter("time", LocalDateTime.of(2026, 1, 1, 12, 0)).setParameter("id", room.getChatRoomId()).executeUpdate();
            return new Fixture(owner.getUserId(), sender.getUserId(), outsider.getUserId(), room.getChatRoomId(), ids);
        });
    }

    @Test void walksOlderHistoryWithoutMovingBoundariesWhenNewMessagesArrive() {
        var f = fixture(151);
        var page = chat.getMessages(f.owner, f.room, new ChatMessageListRequest(0, 50, null));
        var ids = new ArrayList<>(page.content().stream().map(ChatMessageResponse::id).toList());
        long newest = ids.getFirst();
        var inserted = chat.sendMessage(f.sender, f.room, new ChatMessageSendRequest("new arrival"));
        while (!page.last()) {
            page = chat.getMessages(f.owner, f.room, new ChatMessageListRequest(0, 50, null, ids.getLast(), null));
            ids.addAll(page.content().stream().map(ChatMessageResponse::id).toList());
        }
        assertEquals(f.ids.reversed(), ids);
        assertEquals(151, new HashSet<>(ids).size());
        var after = chat.getMessages(f.owner, f.room, new ChatMessageListRequest(0, 50, null, null, newest));
        assertEquals(List.of(inserted.id()), after.content().stream().map(ChatMessageResponse::id).toList());
        for (int i = 0; i < 75; i++) chat.sendMessage(f.sender, f.room, new ChatMessageSendRequest("catch-up" + i));
        var first = chat.getMessages(f.owner, f.room, new ChatMessageListRequest(0, 50, null, null, newest));
        assertEquals(50, first.content().size()); assertFalse(first.last());
        var second = chat.getMessages(f.owner, f.room,
                new ChatMessageListRequest(0, 50, null, null, first.content().getLast().id()));
        assertEquals(26, second.content().size()); assertTrue(second.last());
        var forward = new ArrayList<>(first.content().stream().map(ChatMessageResponse::id).toList());
        forward.addAll(second.content().stream().map(ChatMessageResponse::id).toList());
        assertEquals(forward.stream().sorted().toList(), forward);
        assertEquals(76, new HashSet<>(forward).size());
    }

    @Test void pagesSearchResultsWithLiteralWildcardsAndKeepsReceiptsUnchanged() {
        var f = fixture(180);
        var first = chat.getMessages(f.owner, f.room, new ChatMessageListRequest(0, 50, "needle%_!"));
        assertEquals(120, first.totalElements());
        var collected = new ArrayList<>(first.content());
        var next = first;
        while (!next.last()) {
            next = chat.getMessages(f.owner, f.room,
                    new ChatMessageListRequest(0, 50, "needle%_!", collected.getLast().id(), null));
            collected.addAll(next.content());
        }
        assertEquals(120, collected.size());
        assertEquals(120, collected.stream().map(ChatMessageResponse::id).distinct().count());
        assertTrue(collected.stream().allMatch(message -> message.text().startsWith("needle%_!") && !message.read()));
        assertTrue(chat.getMessages(f.owner, f.room,
                new ChatMessageListRequest(0, 50, null, f.ids.getFirst(), null)).content().isEmpty());
    }

    @Test void authorizesCursorsAndReadStatusWithoutLeakingAnotherRoom() {
        var f = fixture(2); var other = fixture(1);
        var request = new ChatReadRequest(f.ids);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class,
                () -> chat.getReadStatuses(f.outsider, f.room, request)).getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(CustomException.class, () -> chat.getMessages(f.owner, f.room,
                new ChatMessageListRequest(0, 50, null, other.ids.getFirst(), null))).getErrorCode());
        assertEquals(ErrorCode.INVALID_REQUEST, assertThrows(CustomException.class,
                () -> chat.getReadStatuses(f.owner, f.room, new ChatReadRequest(other.ids))).getErrorCode());
        assertTrue(chat.getReadStatuses(f.sender, f.room, request).stream().noneMatch(ChatMessageReadStatusResponse::read));
        chat.markRead(f.owner, f.room, new ChatReadRequest(List.of(f.ids.getFirst())));
        var states = chat.getReadStatuses(f.sender, f.room, request);
        assertTrue(states.stream().filter(state -> state.id().equals(f.ids.getFirst())).findFirst().orElseThrow().read());
        assertFalse(states.stream().filter(state -> state.id().equals(f.ids.getLast())).findFirst().orElseThrow().read());
        chat.leaveRoom(f.owner, f.room);
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(CustomException.class,
                () -> chat.getReadStatuses(f.owner, f.room, request)).getErrorCode());
    }

    @Test void pollingNeverObservesRolledBackSendsOrReceipts() {
        var f = fixture(1);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            chat.sendMessage(f.sender, f.room, new ChatMessageSendRequest("rollback"));
            em.flush(); throw new IllegalStateException("test rollback");
        }));
        assertTrue(chat.getMessages(f.owner, f.room,
                new ChatMessageListRequest(0, 50, null, null, f.ids.getFirst())).content().isEmpty());
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            chat.markRead(f.owner, f.room, new ChatReadRequest(f.ids));
            em.flush(); throw new IllegalStateException("test rollback");
        }));
        assertFalse(chat.getReadStatuses(f.sender, f.room, new ChatReadRequest(f.ids)).getFirst().read());
    }
}
