package com.kkirikkiri.chat.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.kkirikkiri.chat.entity.ChatMessage;
import com.kkirikkiri.chat.entity.ChatRoom;
import com.kkirikkiri.chat.entity.ChatRoomCategory;
import com.kkirikkiri.user.entity.User;

class ChatMessageSendRequestTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"b29b86b5-184a-4c44-a28e-89c62ecc5398"})
    void acceptsOptionalUuidV4SendId(String id) {
        assertTrue(validator.validate(new ChatMessageSendRequest("hello", id)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-a-uuid", "b29b86b5-184a-1c44-a28e-89c62ecc5398"})
    void rejectsMalformedSendIds(String id) {
        assertEquals("clientMessageId", validator.validate(new ChatMessageSendRequest("hello", id))
                .iterator().next().getPropertyPath().toString());
    }
    private static ValidatorFactory factory;
    private static Validator validator;
    private final User sender = new User("encoded", "sender@example.com");
    private final ChatRoom room = new ChatRoom(7L, 8L, ChatRoomCategory.ROOMMATE, null, null);

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"안녕하세요", "Hello roommate", "0123456789", "안녕 Hello 123 😀🚀"})
    void acceptsOrdinaryMessagesWithoutChangingContent(String text) {
        // given
        var request = new ChatMessageSendRequest(text);

        // when / then
        assertTrue(validator.validate(request).isEmpty());
        assertEquals(text, new ChatMessage(room, sender, request.text()).getContent());
    }

    @ParameterizedTest
    @CsvSource({"가,1999", "가,2000", "가,2001", "a,1999", "a,2000", "a,2001",
            "1,1999", "1,2000", "1,2001", "😀,1999", "😀,2000", "😀,2001"})
    void agreesWithEntityAtCodePointLengthBoundary(String character, int count) {
        // given
        String text = character.repeat(count);

        // when / then
        assertLengthValidation(text, count);
    }

    @ParameterizedTest
    @ValueSource(ints = {1999, 2000, 2001})
    void countsMixedTextAndEmojiByCodePointsRatherThanUtf16Length(int count) {
        // given
        String text = "가a1😀".repeat(499) + "😀".repeat(count - 1996);
        assertTrue(text.length() > 2000);
        assertEquals(count, text.codePointCount(0, text.length()));

        // when / then
        assertLengthValidation(text, count);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void stillRejectsMissingOrBlankText(String text) {
        // when
        var violations = validator.validate(new ChatMessageSendRequest(text));

        // then
        assertEquals(1, violations.size());
        assertEquals("text", violations.iterator().next().getPropertyPath().toString());
    }

    private void assertLengthValidation(String text, int count) {
        var violations = validator.validate(new ChatMessageSendRequest(text));
        if (count <= 2000) {
            assertTrue(violations.isEmpty());
            assertEquals(text, new ChatMessage(room, sender, text).getContent());
        } else {
            assertEquals(1, violations.size());
            assertEquals("text", violations.iterator().next().getPropertyPath().toString());
            assertThrows(IllegalArgumentException.class, () -> new ChatMessage(room, sender, text));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"👨‍👩‍👧‍👦", "é"})
    void complexEmojiAndCombiningMarksStillUseCodePoints(String prefix) {
        String text = prefix + "가".repeat(2000 - prefix.codePointCount(0, prefix.length()));
        assertLengthValidation(text, 2000);
        assertLengthValidation(text + "́", 2001);
    }
}
