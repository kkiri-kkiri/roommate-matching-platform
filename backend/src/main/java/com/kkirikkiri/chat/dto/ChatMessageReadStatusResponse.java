package com.kkirikkiri.chat.dto;

/** Only receipt state for IDs the authorized room participant already holds. */
public record ChatMessageReadStatusResponse(Long id, boolean read) { }
