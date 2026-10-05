package com.kkirikkiri.community.post.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.Validator;
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
}
