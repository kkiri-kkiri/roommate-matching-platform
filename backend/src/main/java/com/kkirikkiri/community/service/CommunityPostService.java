package com.kkirikkiri.community.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.dto.PostImagesRequest;
import com.kkirikkiri.community.entity.CommunityComment;
import com.kkirikkiri.community.entity.CommunityContentStatus;
import com.kkirikkiri.community.entity.CommunityPost;
import com.kkirikkiri.community.entity.PostImage;
import com.kkirikkiri.community.repository.CommunityCommentRepository;
import com.kkirikkiri.community.repository.CommunityPostRepository;
import com.kkirikkiri.community.repository.PostImageRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.repository.UserProfileRepository;

/**
 * userId에는 인증된 사용자의 ID를 전달한다.
 * 좋아요/신고 조회 및 댓글 응답 DTO가 없어 목록과 상세 조회는 아직 제공하지 않는다.
 * 파일 저장 기능이 없어 새 이미지 업로드는 지원하지 않으며, 전달되면 저장 전에 거절한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostService {

	private final CommunityPostRepository communityPostRepository;
	private final CommunityCommentRepository communityCommentRepository;
	private final PostImageRepository postImageRepository;
	private final UserProfileRepository userProfileRepository;
	private final Validator validator;

	/** 새 이미지가 없는 게시글을 작성하고 생성된 게시글 ID를 반환한다. images는 생략 가능하다. */
	@Transactional
	public Long createPost(Long userId, CommunityPostCreateRequest request, PostImagesRequest images) {
		validateUserId(userId);
		validateRequest(request);
		validateNoNewImages(images);
		UserProfile profile = userProfileRepository.findByUser_UserId(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		CommunityPost post = new CommunityPost(profile.getUser(), profile.getDormitory(),
				request.category(), request.title(), request.content());
		return communityPostRepository.save(post).getPostId();
	}

	/** 본문과 기존 이미지의 유지/삭제를 처리한다. 새 이미지 추가는 선행 기능 보완이 필요하다. */
	@Transactional
	public void updatePost(Long userId, Long postId, CommunityPostUpdateRequest request,
			PostImagesRequest images) {
		validateUserId(userId);
		validateRequest(request);
		validateNoNewImages(images);
		CommunityPost post = findOwnedPost(userId, postId);
		List<PostImage> existingImages = postImageRepository
				.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(postId);
		Set<Long> keepIds = new HashSet<>(request.keepImageIds());
		Set<Long> existingIds = existingImages.stream()
				.map(PostImage::getPostImageId).collect(Collectors.toSet());
		if (keepIds.size() != request.keepImageIds().size() || !existingIds.containsAll(keepIds)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		post.updateContent(request.category(), request.title(), request.content());
		postImageRepository.deleteAll(existingImages.stream()
				.filter(image -> !keepIds.contains(image.getPostImageId())).toList());
		// 유지한 이미지는 기존 표시 순서를 보존한다.
		int sortOrder = 0;
		for (PostImage image : existingImages) {
			if (keepIds.contains(image.getPostImageId())) {
				image.changeSortOrder(sortOrder++);
			}
		}
	}

	/** 신고 참조를 보존하도록 게시글과 활성 댓글/답글을 같은 트랜잭션에서 소프트 삭제한다. */
	@Transactional
	public void deletePost(Long userId, Long postId) {
		validateUserId(userId);
		CommunityPost post = findOwnedPost(userId, postId);
		communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
				postId, CommunityContentStatus.ACTIVE).forEach(CommunityComment::delete);
		post.delete();
	}

	private CommunityPost findOwnedPost(Long userId, Long postId) {
		if (postId == null || postId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		CommunityPost post = communityPostRepository.findByPostIdAndStatus(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		if (!userId.equals(post.getAuthor().getUserId())) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return post;
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateNoNewImages(PostImagesRequest images) {
		if (images != null) {
			validateRequest(images);
			if (!images.images().isEmpty()) {
				throw new CustomException(ErrorCode.INVALID_REQUEST);
			}
		}
	}
}
