package com.kkirikkiri.integration.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import com.kkirikkiri.chat.entity.ChatMessage;
import com.kkirikkiri.chat.entity.ChatRoom;
import com.kkirikkiri.chat.entity.ChatRoomCategory;
import com.kkirikkiri.chat.entity.ChatRoomMember;
import com.kkirikkiri.chat.repository.ChatRoomMemberRepository;
import com.kkirikkiri.integration.support.ApiIntegrationTest;
import com.kkirikkiri.integration.support.TestUsers;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;

/** Room creation commits REQUIRES_NEW: fixtures and FK-ordered cleanup must also commit. */
@ApiIntegrationTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatRoomApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired EntityManager em;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager manager;
    @Autowired ChatRoomMemberRepository members;
    TransactionTemplate tx;
    User owner, target, outsider;
    final List<Long> userIds = new ArrayList<>();
    final List<Long> dormitoryIds = new ArrayList<>();
    final List<Long> facultyIds = new ArrayList<>();

    @BeforeEach
    void fixtures() {
        tx = new TransactionTemplate(manager);
        tx.executeWithoutResult(s -> {
            owner = createUser(); target = createUser(); outsider = createUser();
            for (User user : List.of(owner, target)) {
                var profile = users.createProfile(user);
                dormitoryIds.add(profile.getDormitory().getDormitoryId());
                facultyIds.add(profile.getFaculty().getFacultyId());
            }
        });
    }

    private User createUser() {
        User user = users.createUser(); userIds.add(user.getUserId()); return user;
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(s -> {
            if (userIds.isEmpty()) return;
            var roomIds = em.createQuery("select distinct m.chatRoom.chatRoomId from ChatRoomMember m where m.user.userId in :ids", Long.class)
                    .setParameter("ids", userIds).getResultList();
            if (!roomIds.isEmpty()) {
                em.createQuery("delete from ChatMessage m where m.chatRoom.chatRoomId in :ids").setParameter("ids", roomIds).executeUpdate();
                em.createQuery("delete from ChatRoomMember m where m.chatRoom.chatRoomId in :ids").setParameter("ids", roomIds).executeUpdate();
                em.createQuery("delete from ChatRoom r where r.chatRoomId in :ids").setParameter("ids", roomIds).executeUpdate();
            }
            em.createQuery("delete from UserProfile p where p.user.userId in :ids").setParameter("ids", userIds).executeUpdate();
            em.createQuery("delete from User u where u.userId in :ids").setParameter("ids", userIds).executeUpdate();
            if (!dormitoryIds.isEmpty()) em.createQuery("delete from Dormitory d where d.dormitoryId in :ids")
                    .setParameter("ids", dormitoryIds).executeUpdate();
            if (!facultyIds.isEmpty()) em.createQuery("delete from Faculty f where f.facultyId in :ids")
                    .setParameter("ids", facultyIds).executeUpdate();
        });
    }

    @Test
    void roommateCreationCommitsExactlyTwoMembersAndReverseRequestReusesRoom() throws Exception {
        long roomId = createRoom(owner, target);
        assertEquals(roomId, createRoom(target, owner));
        tx.executeWithoutResult(s -> {
            assertNotNull(em.find(ChatRoom.class, roomId));
            var saved = members.findAllByChatRoom_ChatRoomId(roomId);
            assertEquals(2, saved.size());
            assertEquals(List.of(owner.getUserId(), target.getUserId()).stream().sorted().toList(),
                    saved.stream().map(m -> m.getUser().getUserId()).sorted().toList());
            assertTrue(saved.stream().noneMatch(ChatRoomMember::isLeft));
            assertEquals(1L, roomCount());
        });
    }

    @Test
    void responseAssemblyFailureRollsBackRoomAndBothMemberships() throws Exception {
        tx.executeWithoutResult(s -> em.createQuery("delete from UserProfile p where p.user.userId = :id")
                .setParameter("id", target.getUserId()).executeUpdate());
        mvc.perform(post("/api/chat/rooms").with(users.authenticated(owner)).contentType("application/json")
                        .content(roomBody(target)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        tx.executeWithoutResult(s -> {
            assertEquals(0L, roomCount());
            assertEquals(0L, em.createQuery("select count(m) from ChatRoomMember m where m.user.userId in :ids", Long.class)
                    .setParameter("ids", userIds).getSingleResult());
        });
    }

    @Test
    void partialSettingsPreserveOmittedValuesAndOnlyChangePrincipalMembership() throws Exception {
        long roomId = createRoom(owner, target);
        for (String body : List.of("{\"pinned\":true,\"muted\":true}", "{\"pinned\":null,\"muted\":false}", "{}")) {
            mvc.perform(patch("/api/chat/rooms/{id}/settings", roomId).with(users.authenticated(owner))
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        }
        tx.executeWithoutResult(s -> {
            var mine = members.findByChatRoom_ChatRoomIdAndUser_UserId(roomId, owner.getUserId()).orElseThrow();
            var other = members.findByChatRoom_ChatRoomIdAndUser_UserId(roomId, target.getUserId()).orElseThrow();
            assertTrue(mine.isPinned()); assertFalse(mine.isMuted());
            assertFalse(other.isPinned()); assertFalse(other.isMuted());
        });
        mvc.perform(get("/api/chat/rooms/{id}", roomId).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.pinned").value(true))
                .andExpect(jsonPath("$.data.muted").value(false));
    }

    @Test
    void leavingPreservesMessagesAndCounterpartButForbidsFurtherAccessAndReentry() throws Exception {
        long roomId = createRoom(owner, target);
        long messageId = incomingMessage(roomId, "history");
        mvc.perform(delete("/api/chat/rooms/{id}/members/me", roomId).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        tx.executeWithoutResult(s -> {
            var mine = members.findByChatRoom_ChatRoomIdAndUser_UserId(roomId, owner.getUserId()).orElseThrow();
            assertTrue(mine.isLeft()); assertNotNull(mine.getLeftAt());
            assertFalse(members.findByChatRoom_ChatRoomIdAndUser_UserId(roomId, target.getUserId()).orElseThrow().isLeft());
            assertNotNull(em.find(ChatMessage.class, messageId));
        });
        mvc.perform(get("/api/chat/rooms").with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        for (var request : protectedRequests(roomId, messageId)) {
            mvc.perform(request.with(users.authenticated(owner)).contentType("application/json"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
        mvc.perform(post("/api/chat/rooms").with(users.authenticated(owner)).contentType("application/json").content(roomBody(target)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mvc.perform(get("/api/chat/rooms/{id}/messages", roomId).with(users.authenticated(target)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].text").value("history"));
    }

    @Test
    void outsiderCannotReadAcknowledgeChangeSettingsOrLeaveAnotherRoom() throws Exception {
        long roomId = createRoom(owner, target);
        long messageId = incomingMessage(roomId, "private");
        for (var request : protectedRequests(roomId, messageId)) {
            mvc.perform(request.with(users.authenticated(outsider)).contentType("application/json"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        }
        tx.executeWithoutResult(s -> {
            assertFalse(em.find(ChatMessage.class, messageId).isRead());
            assertTrue(members.findAllByChatRoom_ChatRoomId(roomId).stream()
                    .noneMatch(m -> m.isLeft() || m.isPinned() || m.isMuted() || m.getLastReadAt() != null));
        });
    }

    @Test
    void httpReadAcknowledgesOnlyVisibleIncomingIdsAndPollingDoesNotAcknowledge() throws Exception {
        long roomId = createRoom(owner, target);
        long first = incomingMessage(roomId, "visible");
        long second = incomingMessage(roomId, "not displayed");
        String both = json.writeValueAsString(Map.of("messageIds", List.of(first, second)));
        mvc.perform(post("/api/chat/rooms/{id}/messages/read-status", roomId).with(users.authenticated(owner))
                        .contentType("application/json").content(both))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[1].read").value(false))
                .andExpect(jsonPath("$.data.length()").value(2));
        tx.executeWithoutResult(s -> {
            assertFalse(em.find(ChatMessage.class, first).isRead());
            assertFalse(em.find(ChatMessage.class, second).isRead());
            assertNull(members.findByChatRoom_ChatRoomIdAndUser_UserId(roomId, owner.getUserId()).orElseThrow().getLastReadAt());
        });
        mvc.perform(post("/api/chat/rooms/{id}/read", roomId).with(users.authenticated(owner))
                        .contentType("application/json").content(json.writeValueAsString(Map.of("messageIds", List.of(first, first)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        tx.executeWithoutResult(s -> {
            assertTrue(em.find(ChatMessage.class, first).isRead());
            assertFalse(em.find(ChatMessage.class, second).isRead());
            assertEquals(em.find(ChatMessage.class, first).getSentAt(), members
                    .findByChatRoom_ChatRoomIdAndUser_UserId(roomId, owner.getUserId()).orElseThrow().getLastReadAt());
        });
        mvc.perform(get("/api/chat/rooms/{id}", roomId).with(users.authenticated(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
        mvc.perform(post("/api/chat/rooms/{id}/messages/read-status", roomId).with(users.authenticated(target))
                        .contentType("application/json").content(both))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == " + first + ")].read").value(true))
                .andExpect(jsonPath("$.data[?(@.id == " + second + ")].read").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELF", "INACTIVE_TARGET", "ROOMMATE_WITH_SOURCE"})
    void invalidCreationDoesNotPersistAnyRoom(String scenario) throws Exception {
        if (scenario.equals("INACTIVE_TARGET")) tx.executeWithoutResult(s ->
                em.find(User.class, target.getUserId()).changeStatus(UserStatus.SUSPENDED));
        String body = scenario.equals("ROOMMATE_WITH_SOURCE")
                ? json.writeValueAsString(Map.of("category", "ROOMMATE", "targetUserId", target.getUserId(), "sourceFrom", "POST"))
                : roomBody(scenario.equals("SELF") ? owner : target);
        mvc.perform(post("/api/chat/rooms").with(users.authenticated(owner)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value(
                        scenario.equals("ROOMMATE_WITH_SOURCE") ? "VALIDATION_ERROR" : "INVALID_REQUEST"));
        tx.executeWithoutResult(s -> assertEquals(0L, roomCount()));
    }

    private List<MockHttpServletRequestBuilder> protectedRequests(long roomId, long messageId) {
        String read = json.writeValueAsString(Map.of("messageIds", List.of(messageId)));
        return List.of(get("/api/chat/rooms/{id}", roomId), get("/api/chat/rooms/{id}/messages", roomId),
                post("/api/chat/rooms/{id}/read", roomId).content(read),
                post("/api/chat/rooms/{id}/messages/read-status", roomId).content(read),
                patch("/api/chat/rooms/{id}/settings", roomId).content("{\"pinned\":true,\"muted\":true}"),
                delete("/api/chat/rooms/{id}/members/me", roomId));
    }

    private long createRoom(User from, User to) throws Exception {
        var result = mvc.perform(post("/api/chat/rooms").with(users.authenticated(from)).contentType("application/json").content(roomBody(to)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.counterpart.id").value(to.getUserId()))
                .andExpect(jsonPath("$.data.category").value("ROOMMATE")).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private String roomBody(User to) {
        return json.writeValueAsString(Map.of("category", "ROOMMATE", "targetUserId", to.getUserId()));
    }

    private long incomingMessage(long roomId, String text) {
        return tx.execute(s -> {
            var message = new ChatMessage(em.find(ChatRoom.class, roomId), em.find(User.class, target.getUserId()), text);
            em.persist(message); em.flush(); return message.getMessageId();
        });
    }

    private long roomCount() {
        // Include a possible orphan from a failed create without counting another test's rooms.
        return em.createQuery("select count(r) from ChatRoom r where r.roomKey = :key", Long.class)
                .setParameter("key", ChatRoom.createRoomKey(owner.getUserId(), target.getUserId(), ChatRoomCategory.ROOMMATE))
                .getSingleResult();
    }
}
