package com.kkirikkiri.chat.dto;

/** 메시지 목록 조회 조건. */
public record ChatMessageListRequest(Integer page, Integer size, String q) {
}
