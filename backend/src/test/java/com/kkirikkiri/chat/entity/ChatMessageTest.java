package com.kkirikkiri.chat.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.kkirikkiri.user.entity.User;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChatMessageTest {
    private final User sender = new User("encoded", "sender@example.com");
    private final ChatRoom room = new ChatRoom(7L, 8L, ChatRoomCategory.ROOMMATE, null, null);

    @ParameterizedTest
    @CsvSource({"a,1999", "a,2000", "😀,1999", "😀,2000"})
    void acceptsContentUpToTwoThousandCodePoints(String character, int count) {
        // given
        String content = character.repeat(count);

        // when
        ChatMessage message = new ChatMessage(room, sender, content);

        // then
        assertEquals(content, message.getContent());
    }

    @ParameterizedTest
    @CsvSource({"a,2001", "😀,2001"})
    void rejectsContentExceedingTwoThousandCodePoints(String character, int count) {
        // given
        String content = character.repeat(count);

        // when / then
        assertThrows(IllegalArgumentException.class, () -> new ChatMessage(room, sender, content));
    }
}
