package com.kkirikkiri.community.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.comment.repository.CommentLikeRepository;
import com.kkirikkiri.community.post.entity.PostImage;
import com.kkirikkiri.community.post.entity.PostLike;
import com.kkirikkiri.community.post.repository.PostLikeRepository;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.dto.*;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.community.post.repository.CommunityPostReadRepository;
import com.kkirikkiri.community.post.repository.PostImageRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.user.entity.Major;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.repository.UserProfileRepository;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

	@Mock CommunityPostRepository communityPostRepository;
	@Mock CommunityPostReadRepository communityPostReadRepository;
	@Mock PostImageRepository postImageRepository;
	@Mock PostImageStorage postImageStorage;
	@Mock UserProfileRepository userProfileRepository;
	@Mock Validator validator;
	@Mock ConstraintViolation<Object> violation;
	@Mock ConstraintViolation<PostImagesRequest> imageViolation;
	@Mock CommunityCommentRepository communityCommentRepository;
	@Mock CommentLikeRepository commentLikeRepository;
	@Mock com.kkirikkiri.community.comment.service.CommunityAnonymousAuthorService anonymousAuthorService;
	@Mock PostLikeRepository postLikeRepository;
	@Mock UserRepository userRepository;
	@InjectMocks CommunityPostService service;
	User user;
	CommunityPost post;

	@BeforeEach
	void setUp() {
		user = mock(User.class);
		post = new CommunityPost(user, null, "자유게시판", "제목", "내용");
	}

	@ParameterizedTest
	@CsvSource({"나래관,나래", "호연관,호연", "비봉관,비봉", "창조관,창조"})
	void filtersEverySortUsingStoredDormitoryName(String requested, String stored) {
		when(validator.validate(any(CommunityPostListRequest.class))).thenReturn(Set.of());
		when(communityPostRepository.searchPosts(eq(CommunityContentStatus.ACTIVE), eq("자유게시판"),
				eq("검색"), eq(stored), any(Pageable.class))).thenReturn(Page.empty());
		when(communityPostReadRepository.findByLikes(eq("자유게시판"), eq("검색"), eq(stored),
				isNull(), any(Pageable.class))).thenReturn(Page.empty());
		for (String sort : List.of("LATEST", "VIEWS", "LIKES")) {
			service.getPosts(null, new CommunityPostListRequest(0, 6, "자유게시판", "검색", requested, sort));
		}
		verify(communityPostRepository, times(2)).searchPosts(eq(CommunityContentStatus.ACTIVE),
				eq("자유게시판"), eq("검색"), eq(stored), any(Pageable.class));
		verify(communityPostReadRepository).findByLikes(eq("자유게시판"), eq("검색"), eq(stored),
				isNull(), any(Pageable.class));
	}

	@Test
	void rejectsMoreThanFiveCombinedImagesBeforeSavingFiles() {
		when(user.getUserId()).thenReturn(7L);
		when(communityPostRepository.findByPostIdAndStatus(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(Optional.of(post));
		var file = new org.springframework.mock.web.MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
		assertThrows(CustomException.class, () -> service.updatePost(7L, 1L,
				new CommunityPostUpdateRequest("자유게시판", "제목", "내용", List.of(1L, 2L, 3L, 4L, 5L), false),
				new PostImagesRequest(List.of(file))));
		verifyNoInteractions(postImageStorage);
		verify(postImageRepository, never()).save(any());
	}

	@Test
	void createsPostWithFiveImagesInOrder() {
		UserProfile profile = mock(UserProfile.class);
		when(profile.getUser()).thenReturn(user);
		when(userProfileRepository.findByUser_UserId(7L)).thenReturn(Optional.of(profile));
		when(communityPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(postImageStorage.store(any())).thenReturn("/api/post-images/photo.png");
		var file = new org.springframework.mock.web.MockMultipartFile("images", "photo.png", "image/png", new byte[]{1});
		service.createPost(7L, new CommunityPostCreateRequest("자유게시판", "제목", "내용", false),
				new PostImagesRequest(java.util.Collections.nCopies(5, file)));
		var captor = org.mockito.ArgumentCaptor.forClass(com.kkirikkiri.community.post.entity.PostImage.class);
		verify(postImageRepository, times(5)).save(captor.capture());
		assertEquals(List.of(0, 1, 2, 3, 4), captor.getAllValues().stream().map(image -> image.getSortOrder()).toList());
		assertTrue(captor.getAllValues().stream().allMatch(image -> image.getImageUrl().equals("/api/post-images/photo.png")));
	}

	@Test
	void createsAnonymousPostWithActualAuthor() {
		UserProfile profile = mock(UserProfile.class);
		when(profile.getUser()).thenReturn(user);
		when(userProfileRepository.findByUser_UserId(7L)).thenReturn(Optional.of(profile));
		when(validator.validate(any(CommunityPostCreateRequest.class))).thenReturn(Set.of());
		when(communityPostRepository.save(any())).thenAnswer(invocation -> {
			CommunityPost saved = invocation.getArgument(0);
			assertTrue(saved.isAnonymous());
			assertSame(user, saved.getAuthor());
			return saved;
		});
		service.createPost(7L, new CommunityPostCreateRequest("자유게시판", "제목", "내용", true), null);
		verify(communityPostRepository).save(any(CommunityPost.class));
	}

	@Test
	void updatePreservesOmittedFlagAndAllowsChangingIt() {
		post.changeAnonymous(true);
		when(user.getUserId()).thenReturn(7L);
		when(validator.validate(any(CommunityPostUpdateRequest.class))).thenReturn(Set.of());
		when(communityPostRepository.findByPostIdAndStatus(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(Optional.of(post));
		when(postImageRepository.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(1L)).thenReturn(List.of());
		service.updatePost(7L, 1L, new CommunityPostUpdateRequest("자유게시판", "수정", "내용", List.of(), null), null);
		assertTrue(post.isAnonymous());
		service.updatePost(7L, 1L, new CommunityPostUpdateRequest("자유게시판", "수정", "내용", List.of(), false), null);
		assertFalse(post.isAnonymous());
		assertSame(user, post.getAuthor());
	}

	@Test
	void anonymousPostStillRejectsUpdatesAndDeletesByAnotherUser() {
		post.changeAnonymous(true);
		when(user.getUserId()).thenReturn(7L);
		when(validator.validate(any(CommunityPostUpdateRequest.class))).thenReturn(Set.of());
		when(communityPostRepository.findByPostIdAndStatus(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(Optional.of(post));
		assertThrows(CustomException.class, () -> service.updatePost(8L, 1L,
				new CommunityPostUpdateRequest("자유게시판", "수정", "내용", List.of(), false), null));
		assertThrows(CustomException.class, () -> service.deletePost(8L, 1L));
		assertTrue(post.isAnonymous());
		assertEquals(CommunityContentStatus.ACTIVE, post.getStatus());
	}

	@Test
	void responseDefaultsToPublicAndMasksAnonymousAuthorWithoutRemovingId() {
		var author = new CommunityAuthorResponse(7L, "닉네임", "소프트웨어융합전공");
		assertFalse(post.isAnonymous());
		var publicResponse = CommunityPostResponse.from(post, author, "내용", 0, 0, false, false, false);
		assertFalse(publicResponse.anonymous());
		assertEquals(author, publicResponse.author());
		post.changeAnonymous(true);
		var list = CommunityPostResponse.from(post, author, "내용", 0, 0, false, false, false);
		var detail = CommunityPostDetailResponse.from(post, author, 0, false, false, List.of(), List.of());
		assertTrue(list.anonymous());
		assertTrue(detail.anonymous());
		assertEquals(7L, list.author().id());
		assertEquals("익명", list.author().nickname());
		assertNull(list.author().major());
		assertEquals(list.author(), detail.author());
		assertSame(user, post.getAuthor());
	}

	@Test
	void authorIncludesMajorAndAllowsMissingMajor() {
		UserProfile profile = mock(UserProfile.class);
		when(profile.getUser()).thenReturn(user);
		when(user.getUserId()).thenReturn(7L);
		when(profile.getNickname()).thenReturn("닉네임");
		assertNull(CommunityAuthorResponse.from(profile).major());
		when(profile.getMajor()).thenReturn(new Major(null, "소프트웨어융합전공", true));
		assertEquals("소프트웨어융합전공", CommunityAuthorResponse.from(profile).major());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void savesPostLikeOnlyWhenNotAlreadyLiked(boolean alreadyLiked) {
		// given
		stubPostForLike();
		when(postLikeRepository.findByPost_PostIdAndUser_UserId(1L, 7L))
				.thenReturn(alreadyLiked ? Optional.of(new PostLike(post, user)) : Optional.empty());
		// when
		service.likePost(7L, 1L);
		// then
		if (alreadyLiked) {
			verify(postLikeRepository, never()).save(any());
		} else {
			var captor = ArgumentCaptor.forClass(PostLike.class);
			verify(postLikeRepository).save(captor.capture());
			assertSame(post, captor.getValue().getPost());
			assertSame(user, captor.getValue().getUser());
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void removesOnlyExistingPostLikeForCurrentUser(boolean alreadyLiked) {
		// given
		stubPostForLike();
		PostLike like = new PostLike(post, user);
		when(postLikeRepository.findByPost_PostIdAndUser_UserId(1L, 7L))
				.thenReturn(alreadyLiked ? Optional.of(like) : Optional.empty());
		// when
		service.unlikePost(7L, 1L);
		// then
		if (alreadyLiked) verify(postLikeRepository).delete(like);
		else verify(postLikeRepository, never()).delete(any(PostLike.class));
	}

	@Test
	void rejectsPostLikesWhenUserNoLongerExists() {
		// given
		when(communityPostRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(Optional.of(post));
		// when / then
		assertError(ErrorCode.UNAUTHORIZED, () -> service.likePost(7L, 1L));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.unlikePost(7L, 1L));
		verifyNoInteractions(postLikeRepository);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {0, -1})
	void rejectsUnauthenticatedWritesAndPersonalPostReads(Long userId) {
		// when / then
		assertError(ErrorCode.UNAUTHORIZED, () -> service.createPost(userId, createRequest(), null));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.updatePost(userId, 1L, updateRequest(List.of()), null));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.deletePost(userId, 1L));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.likePost(userId, 1L));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.unlikePost(userId, 1L));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.getMyPosts(userId, 0, 6, "LATEST"));
		verifyNoInteractions(communityPostRepository, postLikeRepository, userProfileRepository, postImageStorage);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {0, -1, 99})
	void rejectsInvalidOrMissingPostWithoutChangingData(Long postId) {
		// given: positive IDs are absent from the mocked repository.
		// when / then
		assertError(ErrorCode.INVALID_REQUEST, () -> service.getPost(null, postId));
		assertError(ErrorCode.INVALID_REQUEST, () -> service.updatePost(7L, postId, updateRequest(List.of()), null));
		assertError(ErrorCode.INVALID_REQUEST, () -> service.deletePost(7L, postId));
		assertError(ErrorCode.INVALID_REQUEST, () -> service.likePost(7L, postId));
		assertError(ErrorCode.INVALID_REQUEST, () -> service.unlikePost(7L, postId));
		verifyNoInteractions(postImageStorage, postImageRepository, communityCommentRepository, postLikeRepository);
	}

	@ParameterizedTest
	@ValueSource(longs = {0, -1})
	void rejectsInvalidOptionalViewerId(Long userId) {
		// when / then
		assertError(ErrorCode.UNAUTHORIZED, () -> service.getPosts(userId, new CommunityPostListRequest(null, null, null, null, null)));
		assertError(ErrorCode.UNAUTHORIZED, () -> service.getPost(userId, 1L));
		verifyNoInteractions(communityPostRepository, communityPostReadRepository);
	}

	@Test
	void rejectsNullPostRequestsBeforeRepositoryAccess() {
		// when / then
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.createPost(7L, null, null));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.updatePost(7L, 1L, null, null));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.getPosts(null, null));
		verifyNoInteractions(communityPostRepository, userProfileRepository, postImageStorage);
	}

	@Test
	void rejectsConstraintViolationsBeforeRepositoryAccess() {
		// given
		when(validator.validate(any())).thenReturn(Set.of(violation));
		// when / then
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.createPost(7L, createRequest(), null));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.updatePost(7L, 1L, updateRequest(List.of()), null));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.getPosts(null, new CommunityPostListRequest(-1, 6, null, null, null)));
		verifyNoInteractions(communityPostRepository, userProfileRepository, postImageStorage);
	}

	@Test
	void rejectsInvalidImageRequestBeforeSavingPostOrFiles() {
		// given
		var images = new PostImagesRequest(List.of(new MockMultipartFile("images", new byte[0])));
		when(validator.validate(any())).thenReturn(Set.of());
		when(validator.validate(images)).thenReturn(Set.of(imageViolation));
		// when / then
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.createPost(7L, createRequest(), images));
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.updatePost(7L, 1L, updateRequest(List.of()), images));
		verifyNoInteractions(communityPostRepository, userProfileRepository, postImageStorage);
	}

	@Test
	void rejectsPostCreationWhenAuthorProfileIsMissing() {
		// when / then
		assertError(ErrorCode.INVALID_REQUEST, () -> service.createPost(7L, createRequest(), null));
		verify(communityPostRepository, never()).save(any());
		verifyNoInteractions(postImageStorage);
	}

	@Test
	void createsPostUsingProfileDormitoryAndReturnsSavedId() {
		// given
		UserProfile profile = mock(UserProfile.class);
		Dormitory dormitory = new Dormitory("NARAE", "나래", true);
		when(profile.getUser()).thenReturn(user);
		when(profile.getDormitory()).thenReturn(dormitory);
		when(userProfileRepository.findByUser_UserId(7L)).thenReturn(Optional.of(profile));
		when(communityPostRepository.save(any())).thenAnswer(invocation -> {
			CommunityPost saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "postId", 42L);
			return saved;
		});
		// when
		Long id = service.createPost(7L, createRequest(), null);
		// then
		assertEquals(42L, id);
		var captor = ArgumentCaptor.forClass(CommunityPost.class);
		verify(communityPostRepository).save(captor.capture());
		assertSame(dormitory, captor.getValue().getDormitory());
		assertEquals("제목", captor.getValue().getTitle());
		assertEquals("내용", captor.getValue().getContent());
		verifyNoInteractions(postImageStorage, postImageRepository);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void rejectsForeignOrDuplicateKeptImagesBeforeEditingPost(boolean duplicate) {
		// given
		stubOwnedPost();
		when(postImageRepository.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(1L))
				.thenReturn(List.of(image(10L, 0)));
		List<Long> keepIds = duplicate ? List.of(10L, 10L) : List.of(99L);
		// when / then
		assertError(ErrorCode.INVALID_REQUEST, () -> service.updatePost(7L, 1L, updateRequest(keepIds), null));
		assertEquals("제목", post.getTitle());
		assertEquals("내용", post.getContent());
		verify(postImageRepository, never()).deleteAll(any());
		verifyNoInteractions(postImageStorage);
	}

	@Test
	void updatesContentAndPreservesExistingImageOrderBeforeAppendingUpToFiveImages() {
		// given
		stubOwnedPost();
		PostImage first = image(10L, 2);
		PostImage removed = image(11L, 3);
		PostImage last = image(12L, 4);
		when(postImageRepository.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(1L))
				.thenReturn(List.of(first, removed, last));
		var file = new MockMultipartFile("images", "a".repeat(256), "image/png", new byte[]{1});
		when(postImageStorage.store(file)).thenReturn("/api/post-images/new.png");
		// when
		service.updatePost(7L, 1L, updateRequest(List.of(12L, 10L)),
				new PostImagesRequest(java.util.Collections.nCopies(3, file)));
		// then
		assertEquals("수정 제목", post.getTitle());
		assertEquals("수정 내용", post.getContent());
		assertEquals("나눔게시판", post.getCategory());
		assertEquals(0, first.getSortOrder());
		assertEquals(1, last.getSortOrder());
		verify(postImageRepository).deleteAll(List.of(removed));
		var captor = ArgumentCaptor.forClass(PostImage.class);
		verify(postImageRepository, times(3)).save(captor.capture());
		assertEquals(List.of(2, 3, 4), captor.getAllValues().stream().map(PostImage::getSortOrder).toList());
		assertTrue(captor.getAllValues().stream().allMatch(saved -> saved.getPost() == post
				&& saved.getOriginalName().equals("a".repeat(255))
				&& saved.getImageUrl().equals("/api/post-images/new.png")));
	}

	@Test
	void removesAllImagesWhenNoneAreKept() {
		// given
		stubOwnedPost();
		var images = List.of(image(10L, 0), image(11L, 1));
		when(postImageRepository.findAllByPost_PostIdOrderBySortOrderAscPostImageIdAsc(1L)).thenReturn(images);
		// when
		service.updatePost(7L, 1L, updateRequest(List.of()), null);
		// then
		verify(postImageRepository).deleteAll(images);
		verifyNoInteractions(postImageStorage);
	}

	@Test
	void softDeletesPostTogetherWithCommentsAndReplies() {
		// given
		stubOwnedPost();
		CommunityComment comment = new CommunityComment(post, user, null, "댓글");
		CommunityComment reply = new CommunityComment(post, user, comment, "답글");
		when(communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(List.of(comment, reply));
		// when
		service.deletePost(7L, 1L);
		// then
		assertEquals(CommunityContentStatus.DELETED, post.getStatus());
		assertEquals(CommunityContentStatus.DELETED, comment.getStatus());
		assertEquals(CommunityContentStatus.DELETED, reply.getStatus());
		assertSame(comment, reply.getParentComment());
		verify(communityPostRepository, never()).delete(any(CommunityPost.class));
		verify(communityCommentRepository, never()).delete(any(CommunityComment.class));
	}

	@ParameterizedTest
	@ValueSource(ints = {99, 100, 101})
	void truncatesPreviewAtOneHundredUnicodeCodePoints(int length) {
		// given
		String content = "😀".repeat(length);
		post.updateContent("자유게시판", "제목", content);
		when(user.getUserId()).thenReturn(7L);
		when(communityPostRepository.searchPosts(eq(CommunityContentStatus.ACTIVE), isNull(), isNull(), isNull(), any()))
				.thenReturn(new PageImpl<>(List.of(post)));
		// when
		var result = service.getPosts(null, new CommunityPostListRequest(null, null, null, null, null));
		// then
		assertEquals("😀".repeat(Math.min(length, 100)) + (length > 100 ? "..." : ""), result.content().getFirst().contentPreview());
		assertEquals(7L, result.content().getFirst().author().id());
		assertNull(result.content().getFirst().author().nickname());
	}

	@ParameterizedTest
	@ValueSource(strings = {"LATEST", "VIEWS", "LIKES"})
	void readsOnlyCurrentUsersActivePostsWithRequestedSort(String sort) {
		// given
		PageRequest expected = PageRequest.of(0, 6, "VIEWS".equals(sort)
				? Sort.by(Sort.Direction.DESC, "viewCount", "createdAt", "postId")
				: Sort.by(Sort.Direction.DESC, "createdAt", "postId"));
		if ("LIKES".equals(sort)) {
			when(communityPostReadRepository.findByLikes(null, null, null, 7L, expected)).thenReturn(Page.empty(expected));
		} else {
			when(communityPostRepository.findAllByAuthor_UserIdAndStatus(7L, CommunityContentStatus.ACTIVE, expected))
					.thenReturn(Page.empty(expected));
		}
		// when
		var result = service.getMyPosts(7L, null, null, sort);
		// then
		assertTrue(result.content().isEmpty());
		assertEquals(0, result.page());
		assertEquals(6, result.size());
		assertEquals(0, result.totalElements());
		verify(communityPostRepository, never()).searchPosts(any(), any(), any(), any(), any());
	}

	@Test
	void rejectsPaginationOffsetOverflowBeforeQueryingPosts() {
		// when / then
		assertError(ErrorCode.VALIDATION_ERROR, () -> service.getPosts(null,
				new CommunityPostListRequest(Integer.MAX_VALUE, 2, null, null, null)));
		verifyNoInteractions(communityPostRepository, communityPostReadRepository);
	}

	@Test
	void acceptsMaximumSupportedPaginationOffset() {
		// given
		var pageable = PageRequest.of(Integer.MAX_VALUE, 1, Sort.by(Sort.Direction.DESC, "createdAt", "postId"));
		when(communityPostRepository.searchPosts(CommunityContentStatus.ACTIVE, null, null, null, pageable))
				.thenReturn(Page.empty(pageable));
		// when
		var result = service.getPosts(null, new CommunityPostListRequest(Integer.MAX_VALUE, 1, null, null, null));
		// then
		assertEquals(Integer.MAX_VALUE, result.page());
	}

	@Test
	void readsGuestDetailAndIncrementsViewsWithoutQueryingCommentLikesForEmptyComments() {
		// given
		when(user.getUserId()).thenReturn(7L);
		when(communityPostRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
		// when
		var result = service.getPost(null, 1L);
		// then
		assertEquals(1, result.views());
		assertEquals(1, post.getViewCount());
		assertTrue(result.comments().isEmpty());
		assertTrue(result.images().isEmpty());
		assertFalse(result.liked());
		verifyNoInteractions(commentLikeRepository);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {8})
	void combinesCommentLikeCountsAndViewerStateInPostDetail(Long viewerId) {
		// given
		when(user.getUserId()).thenReturn(7L);
		CommunityComment comment = new CommunityComment(post, user, null, "댓글");
		CommunityComment reply = new CommunityComment(post, user, comment, "답글");
		ReflectionTestUtils.setField(comment, "commentId", 10L);
		ReflectionTestUtils.setField(reply, "commentId", 11L);
		when(communityPostRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
		when(communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(List.of(comment, reply));
		var count = mock(CommentLikeRepository.LikeCount.class);
		when(count.getCommentId()).thenReturn(10L);
		when(count.getLikes()).thenReturn(3L);
		when(commentLikeRepository.countLikesByCommentIds(List.of(10L, 11L))).thenReturn(List.of(count));
		if (viewerId != null) {
			when(commentLikeRepository.findLikedCommentIds(List.of(10L, 11L), viewerId)).thenReturn(List.of(11L));
		}
		when(communityPostReadRepository.countLikes(1L)).thenReturn(4L);
		when(communityPostReadRepository.isLiked(1L, viewerId)).thenReturn(viewerId != null);
		when(communityPostReadRepository.isReported(1L, viewerId)).thenReturn(viewerId != null);
		// when
		var result = service.getPost(viewerId, 1L);
		// then
		assertEquals(List.of(3L, 0L), result.comments().stream().map(c -> c.likes()).toList());
		assertFalse(result.comments().getFirst().liked());
		assertEquals(viewerId != null, result.comments().getLast().liked());
		assertEquals(10L, result.comments().getLast().parentCommentId());
		assertEquals(4L, result.likes());
		assertEquals(viewerId != null, result.liked());
		assertEquals(viewerId != null, result.reported());
		if (viewerId == null) verify(commentLikeRepository, never()).findLikedCommentIds(any(), any());
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(longs = {7, 8})
	void returnsStoredAnonymousNumberAndViewerOwnershipWithoutRealProfile(Long viewerId) {
		when(user.getUserId()).thenReturn(7L);
		CommunityComment comment = new CommunityComment(post, user, null, "익명 댓글");
		comment.changeAnonymous(true);
		ReflectionTestUtils.setField(comment, "commentId", 10L);
		when(communityPostRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
		when(anonymousAuthorService.resolveNumbers(1L, null)).thenReturn(java.util.Map.of(7L, 4L));
		when(communityCommentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(1L, CommunityContentStatus.ACTIVE))
				.thenReturn(List.of(comment));
		var response = service.getPost(viewerId, 1L).comments().getFirst();
		assertEquals(4L, response.anonymousNumber());
		assertEquals("익명4", response.author().nickname());
		assertNull(response.author().id());
		assertNull(response.author().major());
		assertEquals(Long.valueOf(7).equals(viewerId), response.mine());
		// 게시글 프로필 조회 1회 외에 익명 댓글의 프로필은 조회하지 않는다.
		verify(userProfileRepository, times(1)).findByUser_UserId(7L);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void stopsSavingImageMetadataAndRemainingFilesWhenStorageFails(boolean update) {
		// given
		if (update) {
			stubOwnedPost();
		} else {
			UserProfile profile = mock(UserProfile.class);
			when(profile.getUser()).thenReturn(user);
			when(userProfileRepository.findByUser_UserId(7L)).thenReturn(Optional.of(profile));
			when(communityPostRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		}
		var first = new MockMultipartFile("images", "first.png", "image/png", new byte[]{1});
		var second = new MockMultipartFile("images", "second.png", "image/png", new byte[]{2});
		var failure = new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
		when(postImageStorage.store(first)).thenThrow(failure);
		var images = new PostImagesRequest(List.of(first, second));
		// when / then
		assertSame(failure, assertThrows(CustomException.class, () -> {
			if (update) service.updatePost(7L, 1L, updateRequest(List.of()), images);
			else service.createPost(7L, createRequest(), images);
		}));
		verify(postImageRepository, never()).save(any());
		verify(postImageStorage, never()).store(second);
	}

	@Test
	void combinesCountsAndViewerFlagsForEachPostWithoutMixingResults() {
		// given
		when(user.getUserId()).thenReturn(7L);
		ReflectionTestUtils.setField(post, "postId", 1L);
		CommunityPost other = new CommunityPost(user, null, "자유게시판", "다른 글", "내용");
		ReflectionTestUtils.setField(other, "postId", 2L);
		var pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "createdAt", "postId"));
		when(communityPostRepository.searchPosts(CommunityContentStatus.ACTIVE, null, null, null, pageable))
				.thenReturn(new PageImpl<>(List.of(post, other), pageable, 5));
		when(communityPostReadRepository.countLikes(1L)).thenReturn(3L);
		when(communityPostReadRepository.countLikes(2L)).thenReturn(0L);
		when(communityCommentRepository.countByPost_PostIdAndStatus(1L, CommunityContentStatus.ACTIVE)).thenReturn(0L);
		when(communityCommentRepository.countByPost_PostIdAndStatus(2L, CommunityContentStatus.ACTIVE)).thenReturn(4L);
		when(postImageRepository.existsByPost_PostId(1L)).thenReturn(true);
		when(postImageRepository.existsByPost_PostId(2L)).thenReturn(false);
		when(communityPostReadRepository.isLiked(1L, 8L)).thenReturn(true);
		when(communityPostReadRepository.isLiked(2L, 8L)).thenReturn(false);
		when(communityPostReadRepository.isReported(1L, 8L)).thenReturn(false);
		when(communityPostReadRepository.isReported(2L, 8L)).thenReturn(true);
		// when
		var result = service.getPosts(8L, new CommunityPostListRequest(1, 2, null, null, null));
		// then
		assertEquals(List.of(1L, 2L), result.content().stream().map(CommunityPostResponse::id).toList());
		assertEquals(List.of(3L, 0L), result.content().stream().map(CommunityPostResponse::likes).toList());
		assertEquals(List.of(0L, 4L), result.content().stream().map(CommunityPostResponse::commentCount).toList());
		assertEquals(List.of(true, false), result.content().stream().map(CommunityPostResponse::hasImages).toList());
		assertEquals(List.of(true, false), result.content().stream().map(CommunityPostResponse::liked).toList());
		assertEquals(List.of(false, true), result.content().stream().map(CommunityPostResponse::reported).toList());
		assertEquals(1, result.page());
		assertEquals(2, result.size());
		assertEquals(5, result.totalElements());
	}

	private void stubPostForLike() {
		when(communityPostRepository.findDetailForUpdate(1L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
		when(userRepository.findById(7L)).thenReturn(Optional.of(user));
	}

	private void stubOwnedPost() {
		when(user.getUserId()).thenReturn(7L);
		when(communityPostRepository.findByPostIdAndStatus(1L, CommunityContentStatus.ACTIVE)).thenReturn(Optional.of(post));
	}

	private PostImage image(Long id, int order) {
		PostImage image = new PostImage(post, "/api/post-images/" + id + ".png", "photo.png", order);
		ReflectionTestUtils.setField(image, "postImageId", id);
		return image;
	}

	private CommunityPostCreateRequest createRequest() {
		return new CommunityPostCreateRequest("자유게시판", "제목", "내용", false);
	}

	private CommunityPostUpdateRequest updateRequest(List<Long> keepIds) {
		return new CommunityPostUpdateRequest("나눔게시판", "수정 제목", "수정 내용", keepIds, null);
	}

	private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
		assertEquals(code, assertThrows(CustomException.class, action).getErrorCode());
	}
}
