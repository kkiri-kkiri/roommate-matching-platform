package com.kkirikkiri.community.comment.service;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.kkirikkiri.community.comment.entity.CommunityAnonymousAuthor;
import com.kkirikkiri.community.comment.repository.CommunityAnonymousAuthorRepository;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.user.entity.User;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CommunityAnonymousAuthorService {
	private final CommunityPostRepository postRepository;
	private final CommunityCommentRepository commentRepository;
	private final CommunityAnonymousAuthorRepository anonymousAuthorRepository;

	/** 게시글 잠금을 댓글 저장/상세 조회 트랜잭션 종료까지 유지한다. author는 신규 작성 시에만 전달한다. */
	public Map<Long, Long> resolveNumbers(Long postId, User author) {
		var post = postRepository.findDetailForUpdate(postId, CommunityContentStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		Map<Long, Long> numbers = new LinkedHashMap<>();
		anonymousAuthorRepository.findAllForUpdate(postId)
				.forEach(entry -> numbers.put(entry.getAuthor().getUserId(), entry.getAnonymousNumber()));
		// 첫 접근에서 삭제/숨김 상태를 포함한 전체 과거 익명 참여 이력을 한 번에 초기화한다.
		if (numbers.isEmpty()) {
			for (var comment : commentRepository.findAnonymousHistory(postId)) {
				Long authorId = comment.getAuthor().getUserId();
				if (!numbers.containsKey(authorId)) {
					long number = numbers.size() + 1L;
					anonymousAuthorRepository.save(new CommunityAnonymousAuthor(post, comment.getAuthor(), number));
					numbers.put(authorId, number);
				}
			}
		}
		if (author != null && !numbers.containsKey(author.getUserId())) {
			long number = numbers.values().stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
			anonymousAuthorRepository.save(new CommunityAnonymousAuthor(post, author, number));
			numbers.put(author.getUserId(), number);
		}
		return numbers;
	}
}
