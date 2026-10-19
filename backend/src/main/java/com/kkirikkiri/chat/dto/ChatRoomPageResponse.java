package com.kkirikkiri.chat.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Room item fields are unchanged. Metadata makes every remaining room discoverable. */
public record ChatRoomPageResponse(
        List<ChatRoomResponse> content, int page, int size, long totalElements, int totalPages,
        boolean first, boolean last, ChatRoomCountsResponse counts) {
    public ChatRoomPageResponse { content = List.copyOf(content); }

    public static ChatRoomPageResponse from(Page<ChatRoomResponse> page, ChatRoomCountsResponse counts) {
        return new ChatRoomPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), counts);
    }
}
