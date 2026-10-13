package com.kkirikkiri.community.comment.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.comment.dto.CommunityCommentCreateRequest;
import com.kkirikkiri.community.comment.dto.CommunityCommentUpdateRequest;
import com.kkirikkiri.community.comment.dto.MyCommunityCommentResponse;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.entity.CommentLike;
import com.kkirikkiri.community.comment.repository.CommentLikeRepository;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

/** userId에는 요청 본문이 아닌 인증된 사용자의 ID를 전달한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentService {

	private final CommunityCommentRepository communityCommentRepository;
	private final CommunityPostRepository communityPostRepository;
	private final UserRepository userRepository;
	private final Validator validator;
	private final CommentLikeRepository commentLikeRepository;
	private final CommunityAnonymousAuthorService anonymousAuthorService;

	/** 댓글 또는 답글에 인증 사용자의 좋아요를 등록한다. 이미 좋아요한 경우 변경 없이 완료한다. */
	@Transactional
	public void likeComment(Long userId, Long commentId) {
		validateUserId(userId);
		CommunityComment comment = findCommentForLikeUpdate(commentId);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (!commentLikeRepository.existsByComment_CommentIdAndUser_UserId(commentId, userId)) {
			commentLikeRepository.save(new CommentLike(comment, user));
		}
	}

	/** 댓글 또는 답글의 본인 좋아요만 취소한다. 좋아요가 없는 경우 변경 없이 완료한다. */
	@Transactional
	public void unlikeComment(Long userId, Long commentId) {
		validateUserId(userId);
		findCommentForLikeUpdate(commentId);
		userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		commentLikeRepository.findByComment_CommentIdAndUser_UserId(commentId, userId)
				.ifPresent(commentLikeRepository::delete);
	}

	/** 활성 게시글에 댓글을 작성하고 생성된 댓글 ID를 반환한다. */
	@Transactional
	public Long createComment(Long userId, Long postId, CommunityCommentCreateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		validateId(postId);
		CommunityPost post = communityPostRepository.findByPostIdAndStatus(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		return saveComment(userId, post, null, request);
	}

	/** 활성 일반 댓글에만 답글을 작성하고 생성된 답글 ID를 반환한다. */
	@Transactional
	public Long createReply(Long userId, Long commentId, CommunityCommentCreateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		CommunityComment parent = findActiveComment(commentId);
		if (parent.getParentComment() != null) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return saveComment(userId, parent.getPost(), parent, request);
	}

	/** 인증 사용자 본인의 댓글 또는 답글 내용을 수정한다. */
	@Transactional
	public void updateComment(Long userId, Long commentId, CommunityCommentUpdateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		findOwnedComment(userId, commentId).updateContent(request.content());
	}

	/** 신고 및 부모 댓글 참조를 보존하도록 대상 댓글 또는 답글만 소프트 삭제한다. */
	@Transactional
	public void deleteComment(Long userId, Long commentId) {
		validateUserId(userId);
		findOwnedComment(userId, commentId).delete();
	}

	/** 본인의 활성 댓글과 답글을 최신순으로 조회한다. 기본 page=0, size=6. */
	public RoommatePageResponse<MyCommunityCommentResponse> getMyComments(Long userId, Integer page,
			Integer size) {
		validateUserId(userId);
		int pageNumber = page == null ? 0 : page;
		int pageSize = size == null ? 6 : size;
		if (pageNumber < 0 || pageSize < 1 || (long) pageNumber * pageSize > Integer.MAX_VALUE) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		PageRequest pageable = PageRequest.of(pageNumber, pageSize,
				Sort.by(Sort.Direction.DESC, "createdAt", "commentId"));
		return RoommatePageResponse.from(communityCommentRepository.findAllByAuthor_UserIdAndStatus(
				userId, CommunityContentStatus.ACTIVE, pageable).map(MyCommunityCommentResponse::from));
	}

	private Long saveComment(Long userId, CommunityPost post, CommunityComment parent,
			CommunityCommentCreateRequest request) {
		User author = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		CommunityComment comment = new CommunityComment(post, author, parent, request.content());
		comment.changeAnonymous(request.anonymous());
		if (comment.isAnonymous()) {
			anonymousAuthorService.resolveNumbers(post.getPostId(), author);
		}
		return communityCommentRepository.save(comment).getCommentId();
	}

	private CommunityComment findCommentForLikeUpdate(Long commentId) {
		validateId(commentId);
		// MySQL REPEATABLE READ의 일반 조회 스냅샷이 만들어지기 전에 댓글 행을 잠근다.
		// 게시글/부모 댓글에는 잠금을 추가하지 않아 서로 다른 댓글의 요청을 직렬화하지 않는다.
		CommunityComment comment = communityCommentRepository
				.findForLikeUpdate(commentId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (comment.getPost().getStatus() != CommunityContentStatus.ACTIVE) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return comment;
	}

	private CommunityComment findActiveComment(Long commentId) {
		validateId(commentId);
		CommunityComment comment = communityCommentRepository
				.findByCommentIdAndStatus(commentId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (comment.getPost().getStatus() != CommunityContentStatus.ACTIVE) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return comment;
	}

	private CommunityComment findOwnedComment(Long userId, Long commentId) {
		CommunityComment comment = findActiveComment(commentId);
		if (!userId.equals(comment.getAuthor().getUserId())) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return comment;
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
	}

	private void validateId(Long id) {
		if (id == null || id <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
