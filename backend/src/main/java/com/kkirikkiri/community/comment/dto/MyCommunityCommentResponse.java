package com.kkirikkiri.community.comment.dto;

import java.time.LocalDateTime;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.CommunityPost;

/** 내가 작성한 댓글/답글 목록 항목. 원 게시글 이동에 필요한 정보를 포함한다. */
public record MyCommunityCommentResponse(
		Long id,
		String content,
		LocalDateTime createdAt,
		boolean isReply,
		Long postId,
		String postTitle,
		String category) {

	public static MyCommunityCommentResponse from(CommunityComment comment) {
		CommunityPost post = comment.getPost();
		return new MyCommunityCommentResponse(
				comment.getCommentId(), comment.getContent(), comment.getCreatedAt(),
				comment.getParentComment() != null,
				post.getPostId(), post.getTitle(), post.getCategory());
	}
}
