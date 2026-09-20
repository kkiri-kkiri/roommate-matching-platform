package com.kkirikkiri.chat.dto;

/** 부분 설정 변경 요청. 전달하지 않은 설정은 유지할 수 있도록 Boolean을 사용한다. */
public record ChatRoomSettingsRequest(Boolean pinned, Boolean muted) {
}
