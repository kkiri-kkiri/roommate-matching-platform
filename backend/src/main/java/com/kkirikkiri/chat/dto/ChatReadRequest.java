package com.kkirikkiri.chat.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Only acknowledge incoming messages actually returned to this client. Not a high-water mark. */
public record ChatReadRequest(
        @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> messageIds) {
}
