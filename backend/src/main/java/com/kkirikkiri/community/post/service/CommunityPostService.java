package com.kkirikkiri.community.post.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Validator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.post.dto.CommunityPostCreateRequest;
import com.kkirikkiri.community.post.dto.CommunityPostUpdateRequest;
import com.kkirikkiri.community.post.dto.PostImagesRequest;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.service.CommunityAnonymousAuthorService;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.entity.PostImage;
import com.kkirikkiri.community.post.entity.PostLike;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.comment.repository.CommentLikeRepository;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.community.post.repository.PostImageRepository;
import com.kkirikkiri.community.post.repository.PostLikeRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.community.post.dto.CommunityPostListRequest;
import com.kkirikkiri.community.post.dto.CommunityPostResponse;
import com.kkirikkiri.community.post.dto.CommunityPostDetailResponse;
import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.community.post.dto.PostImageResponse;
import com.kkirikkiri.community.comment.dto.CommunityCommentResponse;
import com.kkirikkiri.community.post.repository.CommunityPostReadRepository;
import com.kkirikkiri.matching.dto.RoommatePageResponse;

/**
 * userId에는 인증된 사용자의 ID를 전달한다.
 * 공개 조회의 userId는 비로그인 시 null을 전달한다.
 * 이미지 파일은 저장소에 저장하고 DB에는 URL과 표시 순서만 기록한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostService {

	private final CommunityPostRepository communityPostRepository;
	private final CommunityCommentRepository communityCommentRepository;
	private final PostImageRepository postImageRepository;
	private final PostImageStorage postImageStorage;
	private final UserProfileRepository userProfileRepository;
	private final Validator validator;
	private final CommunityPostReadRepository communityPostReadRepository;
	private final PostLikeRepository postLikeRepository;
	private final UserRepository userRepository;
	private final CommentLikeRepository commentLikeRepository;
	private final CommunityAnonymousAuthorService anonymousAuthorService;
	private final EntityManager entityManager;

	/** 인증 사용자의 좋아요를 등록한다. 이미 좋아요한 경우 변경 없이 완료한다. */
	@Transactional
	public void likePost(Long userId, Long postId) {
		lockActiveUser(userId);
		CommunityPost post = findPostForLikeUpdate(userId, postId);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		if (postLikeRepository.findByPost_PostIdAndUser_UserId(postId, userId).isEmpty()) {
			postLikeRepository.save(new PostLike(post, user));
		}
	}

	/** 인증 사용자 자신의 좋아요만 취소한다. 좋아요가 없는 경우 변경 없이 완료한다. */
	@Transactional
	public void unlikePost(Long userId, Long postId) {
		lockActiveUser(userId);
		findPostForLikeUpdate(userId, postId);
		userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		postLikeRepository.findByPost_PostIdAndUser_UserId(postId, userId)
				.ifPresent(postLikeRepository::delete);
	}

	private CommunityPost findPostForLikeUpdate(Long userId, Long postId) {
		validateUserId(userId);
		if (postId == null || postId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		// 일반 조회보다 먼저 잠금을 획득해 동시 좋아요 추가/취소를 직렬화한다.
		return communityPostRepository.findDetailForUpdate(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

	/** 공개 목록 조회. userId는 좋아요/신고 여부를 표시하는 데만 사용한다. */
	public RoommatePageResponse<CommunityPostResponse> getPosts(Long userId, CommunityPostListRequest request) {
		validateOptionalUserId(userId);
		return readPosts(userId, null, request);
	}

	/** 인증 사용자 본인의 활성 게시글만 조회한다. */
	public RoommatePageResponse<CommunityPostResponse> getMyPosts(Long userId, Integer page, Integer size,
			String sort) {
		validateUserId(userId);
		return readPosts(userId, userId, new CommunityPostListRequest(page, size, null, null, sort));
	}

	/** 활성 게시글에 잠금을 획득하고 조회수를 증가시킨 뒤 상세 DTO를 반환한다. */
	@Transactional
	public CommunityPostDetailResponse<CommunityCommentResponse> getPost(Long userId, Long postId) {
		validateOptionalUserId(userId);
		if (postId == null || postId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		CommunityPost post = communityPostRepository.findDetailForUpdate(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		post.increaseViewCount();
		var anonymousNumbers = anonymousAuthorService.resolveNumbers(postId, null);
		var images = postImageRepository.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(postId)
				.stream().map(PostImageResponse::from).toList();
		var commentEntities = communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
				postId, CommunityContentStatus.ACTIVE);
		var commentIds = commentEntities.stream().map(CommunityComment::getCommentId).toList();
		Map<Long, Long> likeCounts = commentIds.isEmpty() ? Map.of()
				: commentLikeRepository.countLikesByCommentIds(commentIds).stream().collect(Collectors.toMap(
						CommentLikeRepository.LikeCount::getCommentId, CommentLikeRepository.LikeCount::getLikes));
		Set<Long> likedIds = userId == null || commentIds.isEmpty() ? Set.of()
				: new HashSet<>(commentLikeRepository.findLikedCommentIds(commentIds, userId));
		var comments = commentEntities.stream()
				.map(comment -> CommunityCommentResponse.from(comment,
						comment.isAnonymous() ? null : author(comment.getAuthor().getUserId()),
						likeCounts.getOrDefault(comment.getCommentId(), 0L), likedIds.contains(comment.getCommentId()),
						anonymousNumbers.get(comment.getAuthor().getUserId()), userId))
				.toList();
		return CommunityPostDetailResponse.from(post, author(post.getAuthor().getUserId()),
				communityPostReadRepository.countLikes(postId), communityPostReadRepository.isLiked(postId, userId),
				communityPostReadRepository.isReported(postId, userId), images, comments);
	}

	private RoommatePageResponse<CommunityPostResponse> readPosts(Long viewerId, Long authorId,
			CommunityPostListRequest request) {
		validateRequest(request);
		if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "postId");
		if ("VIEWS".equals(request.sort())) {
			sort = Sort.by(Sort.Direction.DESC, "viewCount").and(sort);
		}
		PageRequest pageable = PageRequest.of(request.page(), request.size(), sort);
		Page<CommunityPost> posts;
		if ("LIKES".equals(request.sort())) {
			posts = communityPostReadRepository.findByLikes(request.category(), request.q(), request.dormitory(), authorId, pageable);
		} else if (authorId != null) {
			posts = communityPostRepository.findAllByAuthor_UserIdAndStatus(authorId, CommunityContentStatus.ACTIVE,
					pageable);
		} else {
			posts = communityPostRepository.searchPosts(CommunityContentStatus.ACTIVE, request.category(), request.q(),
					request.dormitory(), pageable);
		}
		var postIds = posts.getContent().stream().map(CommunityPost::getPostId).toList();
		Map<Long, Long> likeCounts = postIds.isEmpty() ? Map.of()
				: postLikeRepository.countLikesByPostIds(postIds).stream().collect(Collectors.toMap(
						PostLikeRepository.LikeCount::getPostId, PostLikeRepository.LikeCount::getLikes));
		Set<Long> likedIds = viewerId == null || postIds.isEmpty() ? Set.of()
				: new HashSet<>(postLikeRepository.findLikedPostIds(postIds, viewerId));
		return RoommatePageResponse.from(posts.map(post -> toListResponse(post, viewerId,
				likeCounts.getOrDefault(post.getPostId(), 0L), likedIds.contains(post.getPostId()))));
	}

	private CommunityPostResponse toListResponse(CommunityPost post, Long userId, long likes, boolean liked) {
		Long postId = post.getPostId();
		String content = post.getContent();
		int previewEnd = content.offsetByCodePoints(0, Math.min(100, content.codePointCount(0, content.length())));
		String preview = content.substring(0, previewEnd) + (previewEnd < content.length() ? "..." : "");
		return CommunityPostResponse.from(post, author(post.getAuthor().getUserId()), preview,
				likes,
				communityCommentRepository.countByPost_PostIdAndStatus(postId, CommunityContentStatus.ACTIVE),
				postImageRepository.existsByPost_PostId(postId), liked,
				communityPostReadRepository.isReported(postId, userId));
	}

	private CommunityAuthorResponse author(Long userId) {
		return userProfileRepository.findByUser_UserId(userId).map(CommunityAuthorResponse::from)
				.orElseGet(() -> new CommunityAuthorResponse(userId, null));
	}

	private void validateOptionalUserId(Long userId) {
		if (userId != null) {
			validateUserId(userId);
		}
	}

	/** 최대 5장의 이미지와 게시글을 작성한다. images는 생략 가능하다. */
	@Transactional
	public Long createPost(Long userId, CommunityPostCreateRequest request, PostImagesRequest images) {
		lockActiveUser(userId);
		validateRequest(request);
		validateImages(images);
		UserProfile profile = userProfileRepository.findByUser_UserId(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		CommunityPost post = new CommunityPost(profile.getUser(), profile.getDormitory(),
				request.category(), request.title(), request.content());
		post.changeAnonymous(request.anonymous());
		post = communityPostRepository.save(post);
		saveImages(post, images, 0);
		return post.getPostId();
	}

	/** 본문과 기존 이미지의 유지/삭제 및 새 이미지 추가를 처리한다. */
	@Transactional
	public void updatePost(Long userId, Long postId, CommunityPostUpdateRequest request,
			PostImagesRequest images) {
		lockActiveUser(userId);
		validateRequest(request);
		validateImages(images);
		CommunityPost post = findOwnedPost(userId, postId);
		List<PostImage> existingImages = postImageRepository
				.findAllForUpdateByPostId(postId);
		existingImages.forEach(image -> entityManager.refresh(image, LockModeType.PESSIMISTIC_WRITE));
		Set<Long> keepIds = new HashSet<>(request.keepImageIds());
		if (keepIds.size() + (images == null ? 0 : images.images().size()) > 5) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		Set<Long> existingIds = existingImages.stream()
				.map(PostImage::getPostImageId).collect(Collectors.toSet());
		if (keepIds.size() != request.keepImageIds().size() || !existingIds.containsAll(keepIds)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		post.updateContent(request.category(), request.title(), request.content());
		if (request.anonymous() != null) {
			post.changeAnonymous(request.anonymous());
		}
		postImageRepository.deleteAll(existingImages.stream()
				.filter(image -> !keepIds.contains(image.getPostImageId())).toList());
		// 유지한 이미지는 기존 표시 순서를 보존한다.
		int sortOrder = 0;
		for (PostImage image : existingImages) {
			if (keepIds.contains(image.getPostImageId())) {
				image.changeSortOrder(sortOrder++);
			}
		}
		saveImages(post, images, sortOrder);
	}

	/** 신고 참조를 보존하도록 게시글과 활성 댓글/답글을 같은 트랜잭션에서 소프트 삭제한다. */
	@Transactional
	public void deletePost(Long userId, Long postId) {
		lockActiveUser(userId);
		CommunityPost post = findOwnedPost(userId, postId);
		communityCommentRepository.findAllForUpdateByPostId(
				postId, CommunityContentStatus.ACTIVE).forEach(CommunityComment::delete);
		post.delete();
	}

	private CommunityPost findOwnedPost(Long userId, Long postId) {
		if (postId == null || postId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		// 상세 조회(조회수)·좋아요·일반/관리자 삭제와 같은 행 잠금을 커밋까지 유지한다.
		CommunityPost post = communityPostRepository.findDetailForUpdate(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		entityManager.refresh(post, LockModeType.PESSIMISTIC_WRITE);
		if (post.getStatus() != CommunityContentStatus.ACTIVE) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		if (!userId.equals(post.getAuthor().getUserId())) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		return post;
	}

	/** Account -> post -> comment; held by the caller's write transaction until completion. */
	private User lockActiveUser(Long userId) {
		validateUserId(userId);
		return userRepository.findByIdForAccountUpdate(userId)
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
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

	private void validateImages(PostImagesRequest images) {
		if (images != null) {
			validateRequest(images);
		}
	}

	private void saveImages(CommunityPost post, PostImagesRequest images, int sortOrder) {
		if (images == null) return;
		for (var file : images.images()) {
			String url = postImageStorage.store(file);
			String name = file.getOriginalFilename();
			if (name != null && name.length() > 255) name = name.substring(0, 255);
			postImageRepository.save(new PostImage(post, url, name, sortOrder++));
		}
	}
}
