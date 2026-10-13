package com.kkirikkiri.community.comment.dto;

import java.time.LocalDateTime;
import java.util.Objects;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;

/** 상세 조회의 댓글/답글 목록. parentCommentId로 답글의 부모를 식별한다. */
public record CommunityCommentResponse(Long id, Long parentCommentId, String content,
		CommunityAuthorResponse author, LocalDateTime createdAt, long likes, boolean liked, boolean anonymous,
		Long anonymousNumber, boolean mine) {
	public static CommunityCommentResponse from(CommunityComment comment, CommunityAuthorResponse author,
			long likes, boolean liked, Long anonymousNumber, Long viewerId) {
		Long number = comment.isAnonymous() ? Objects.requireNonNull(anonymousNumber, "anonymousNumber") : null;
		CommunityAuthorResponse display = comment.isAnonymous()
				? new CommunityAuthorResponse(null, "익명" + number, null) : author;
		return new CommunityCommentResponse(comment.getCommentId(),
				comment.getParentComment() == null ? null : comment.getParentComment().getCommentId(),
				comment.getContent(), display, comment.getCreatedAt(), likes, liked,
				comment.isAnonymous(), number, viewerId != null && viewerId.equals(comment.getAuthor().getUserId()));
	}
}
