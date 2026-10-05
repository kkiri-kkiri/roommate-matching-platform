package com.kkirikkiri.community.post.dto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class PostImagesRequestTest {

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "image/gif"})
    void acceptsSupportedImageTypes(String type) {
        // given
        var file = new MockMultipartFile("images", "photo", type, new byte[]{1});
        // when / then
        assertTrue(new PostImagesRequest(List.of(file)).isImagesValid());
    }

    @ParameterizedTest
    @CsvSource({"0,false", "1,true", "5242880,true", "5242881,false"})
    void enforcesPositiveSizeAndFiveMegabyteLimit(long size, boolean valid) {
        // given
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(size);
        if (valid) when(file.getContentType()).thenReturn("image/png");
        // when / then
        assertEquals(valid, new PostImagesRequest(List.of(file)).isImagesValid());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"text/plain", "image/svg+xml"})
    void rejectsMissingOrUnsupportedTypeAmongValidImages(String type) {
        // given
        var valid = new MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
        var invalid = new MockMultipartFile("images", "photo", type, new byte[]{1});
        // when / then
        assertFalse(new PostImagesRequest(List.of(valid, invalid)).isImagesValid());
    }

    @Test
    void allowsOmittedImagesButRejectsNullFileEntry() {
        // when / then
        assertTrue(new PostImagesRequest(null).isImagesValid());
        assertTrue(new PostImagesRequest(List.of()).isImagesValid());
        assertFalse(new PostImagesRequest(Collections.singletonList(null)).isImagesValid());
    }
}
