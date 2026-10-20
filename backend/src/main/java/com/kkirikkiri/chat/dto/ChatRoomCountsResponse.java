package com.kkirikkiri.chat.dto;

/** Counts of all active rooms, independent of the current page/search/category. */
public record ChatRoomCountsResponse(long all, long roommate, long community, long pinned) {
}
