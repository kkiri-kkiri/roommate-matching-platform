package com.kkirikkiri.chat.repository;

import com.kkirikkiri.chat.entity.ChatRoomCategory;

public record ChatRoomCategoryCount(ChatRoomCategory category, long total, long pinned) {
}
