package com.kkirikkiri.community.comment.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import com.kkirikkiri.community.comment.dto.*;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.*;
import com.kkirikkiri.community.post.dto.CommunityAuthorResponse;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

@DataJpaTest(properties = {
		"spring.datasource.url=${ANONYMOUS_TEST_DB_URL:jdbc:h2:mem:anonymous;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1}",
		"spring.datasource.driver-class-name=${ANONYMOUS_TEST_DB_DRIVER:org.h2.Driver}",
		"spring.datasource.username=${ANONYMOUS_TEST_DB_USER:sa}", "spring.datasource.password=${ANONYMOUS_TEST_DB_PASSWORD:}",
		"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CommunityAnonymousAuthorService.class, CommunityCommentService.class, CommunityCommentWriteService.class,
		CommunityAnonymousAuthorIntegrationTest.ValidationConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommunityAnonymousAuthorIntegrationTest {
	@TestConfiguration
	static class ValidationConfig {
		@Bean Validator validator() { return Validation.buildDefaultValidatorFactory().getValidator(); }
	}
	@Autowired CommunityAnonymousAuthorService numbers;
	@Autowired CommunityCommentService comments;
	@Autowired CommunityAnonymousAuthorRepository mappings;
	@Autowired CommunityCommentRepository commentRepository;
	@Autowired CommunityPostRepository posts;
	@Autowired UserRepository users;
	@Autowired PlatformTransactionManager transactionManager;
	@Autowired JdbcTemplate jdbc;

	private <T> T tx(java.util.function.Supplier<T> action) {
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		return transaction.execute(status -> action.get());
	}
	private User user() { return users.save(new User("encoded", UUID.randomUUID() + "@test.invalid")); }
	private CommunityPost post(User author) {
		return posts.save(new CommunityPost(author, null, "자유게시판", "제목", "내용"));
	}
	private Map<Long, Long> read(Long postId) { return tx(() -> numbers.resolveNumbers(postId, null)); }
	private Long create(User user, CommunityPost post, boolean anonymous) {
		return comments.createComment(user.getUserId(), post.getPostId(), new CommunityCommentCreateRequest("댓글", anonymous));
	}

	@Test
	void sharesNumbersAcrossRepliesAndPreservesThemAfterAllCommentsAreDeleted() {
		User a = user(), b = user(), c = user(), d = user();
		CommunityPost post = post(b), other = post(b);
		Long first = create(a, post, true);
		Long publicComment = create(b, post, false);
		Long reply = comments.createReply(c.getUserId(), publicComment, new CommunityCommentCreateRequest("답글", true));
		Long aReply = comments.createReply(a.getUserId(), publicComment, new CommunityCommentCreateRequest("답글", true));
		create(d, post, true);
		create(c, post, true);
		Map<Long, Long> expected = Map.of(a.getUserId(), 1L, c.getUserId(), 2L, d.getUserId(), 3L);
		assertEquals(expected, read(post.getPostId()));
		comments.updateComment(a.getUserId(), first, new CommunityCommentUpdateRequest("수정"));
		comments.deleteComment(a.getUserId(), first);
		comments.deleteComment(a.getUserId(), aReply);
		assertEquals(expected, read(post.getPostId()));
		create(a, post, true);
		assertEquals(expected, read(post.getPostId()));
		create(c, other, true);
		assertEquals(Map.of(c.getUserId(), 1L), read(other.getPostId()));
		// 새 트랜잭션에서 역순 조회해도 DTO 번호와 신원 마스킹은 동일하다.
		tx(() -> {
			var entries = new ArrayList<>(commentRepository.findAllByPost_PostIdAndStatusOrderByCreatedAtAscCommentIdAsc(
					post.getPostId(), com.kkirikkiri.community.common.entity.CommunityContentStatus.ACTIVE));
			Collections.reverse(entries);
			for (var entry : entries) {
				var dto = CommunityCommentResponse.from(entry, new CommunityAuthorResponse(entry.getAuthor().getUserId(), "공개닉네임", "학과"),
						0, false, expected.get(entry.getAuthor().getUserId()), c.getUserId());
				assertEquals(entry.getAuthor().getUserId().equals(c.getUserId()), dto.mine());
				if (entry.isAnonymous()) {
					assertNull(dto.author().id());
					assertNull(dto.author().major());
					assertEquals("익명" + expected.get(entry.getAuthor().getUserId()), dto.author().nickname());
				} else {
					assertNull(dto.anonymousNumber());
					assertEquals("공개닉네임", dto.author().nickname());
				}
			}
			return null;
		});
		assertNotNull(reply);
	}

	@Test
	void backfillsDeletedHistoryAndBreaksTimestampTiesByCommentIdBeforeNewParticipation() {
		User a = user(), b = user(), c = user();
		CommunityPost post = post(a);
		tx(() -> {
			CommunityComment parent = commentRepository.saveAndFlush(new CommunityComment(post, c, null, "공개"));
			CommunityComment first = new CommunityComment(post, a, parent, "과거 익명 답글");
			first.changeAnonymous(true);
			first.delete();
			commentRepository.saveAndFlush(first);
			CommunityComment second = new CommunityComment(post, b, null, "과거 익명 댓글");
			second.changeAnonymous(true);
			commentRepository.saveAndFlush(second);
			jdbc.update("update community_comment set created_at = ? where post_id = ?",
					java.sql.Timestamp.valueOf("2026-01-01 00:00:00"), post.getPostId());
			return null;
		});
		create(c, post, true);
		assertEquals(Map.of(a.getUserId(), 1L, b.getUserId(), 2L, c.getUserId(), 3L), read(post.getPostId()));
		create(a, post, true);
		assertEquals(1L, read(post.getPostId()).get(a.getUserId()));
	}

	@Test
	void concurrentSameAndDifferentAuthorsReceiveOneStableUniqueNumberEach() throws Exception {
		User owner = user();
		CommunityPost post = post(owner);
		List<User> participants = List.of(user(), user(), user(), user());
		int workers = 8;
		CountDownLatch ready = new CountDownLatch(workers), start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(workers)) {
			List<Future<Long>> futures = new ArrayList<>();
			for (int i = 0; i < workers; i++) {
				User participant = participants.get(i % participants.size());
				futures.add(executor.submit(() -> {
					ready.countDown();
					assertTrue(start.await(10, TimeUnit.SECONDS));
					return create(participant, post, true);
				}));
			}
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			start.countDown();
			for (var future : futures) assertNotNull(future.get(30, TimeUnit.SECONDS));
		}
		var actual = read(post.getPostId());
		assertEquals(4, actual.size());
		assertEquals(Set.of(1L, 2L, 3L, 4L), new HashSet<>(actual.values()));
		assertEquals(actual, read(post.getPostId()));
	}
}
