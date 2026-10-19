package com.kkirikkiri.community.comment.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;

/** 댓글 작성/스레드 삭제가 게시글 → 댓글 순서의 잠금을 호출자 트랜잭션 종료까지 공유한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CommunityCommentWriteService {

	private final CommunityPostRepository posts;
	private final CommunityCommentRepository comments;
	private final EntityManager entityManager;

	public CommunityPost lockPost(Long postId, boolean requireActive) {
		CommunityPost post = posts.findByIdForUpdate(postId).orElseThrow(this::invalidRequest);
		// 잠금 쿼리도 1차 캐시의 Entity를 재사용할 수 있으므로 최신 상태를 다시 읽는다.
		entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
		if (requireActive && post.getStatus() != CommunityContentStatus.ACTIVE) {
			throw invalidRequest();
		}
		return post;
	}

	public CommunityComment lockActiveComment(Long commentId) {
		CommunityComment comment = comments.findForUpdate(commentId, CommunityContentStatus.ACTIVE)
				.orElseThrow(this::invalidRequest);
		entityManager.refresh(comment, LockModeType.PESSIMISTIC_WRITE);
		if (comment.getStatus() != CommunityContentStatus.ACTIVE) {
			throw invalidRequest();
		}
		return comment;
	}

	public CommunityComment lockForDeletion(Long commentId) {
		Long postId = comments.findPostIdByCommentId(commentId).orElseThrow(this::invalidRequest);
		lockPost(postId, false);
		return lockActiveComment(commentId);
	}

	/** 신고 대상의 기존 멱등 삭제와 refresh 잠금을 유지한다. */
	public void deleteReported(CommunityComment comment) {
		lockPost(comment.getPost().getPostId(), false);
		entityManager.refresh(comment, LockModeType.PESSIMISTIC_WRITE);
		deleteLocked(comment);
	}

	/** 게시글/대상 댓글을 먼저 잠근 호출자만 사용한다. 부모 삭제는 활성 답글도 소프트 삭제한다. */
	public void deleteLocked(CommunityComment comment) {
		if (comment.getParentComment() == null) {
			for (CommunityComment reply : comments.findRepliesForUpdate(comment.getCommentId(),
					CommunityContentStatus.ACTIVE)) {
				entityManager.refresh(reply, LockModeType.PESSIMISTIC_WRITE);
				reply.delete();
			}
		}
		comment.delete();
	}

	private CustomException invalidRequest() {
		return new CustomException(ErrorCode.INVALID_REQUEST);
	}
}
