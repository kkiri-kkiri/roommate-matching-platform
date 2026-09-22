package com.kkirikkiri.community.post.repository;

import java.util.List;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.kkirikkiri.community.post.entity.CommunityPost;

/** ERD의 POST_LIKE, REPORT를 읽기만 한다. 좋아요/신고 변경 기능은 제공하지 않는다. */
@Repository
@RequiredArgsConstructor
public class CommunityPostReadRepository {
	private final EntityManager entityManager;

	@SuppressWarnings("unchecked")
	public Page<CommunityPost> findByLikes(String category, String keyword, Long authorId, Pageable pageable) {
		String filter = """
				 from community_post p where p.status = 'ACTIVE'
				 and (:category is null or :category = '' or p.category = :category)
				 and (:keyword is null or :keyword = '' or locate(:keyword, p.title) > 0
				      or locate(:keyword, p.content) > 0)
				 and (:authorId is null or p.author_id = :authorId)
				""";
		var count = entityManager.createNativeQuery("select count(*)" + filter)
				.setParameter("category", category).setParameter("keyword", keyword)
				.setParameter("authorId", authorId);
		long total = ((Number) count.getSingleResult()).longValue();
		var query = entityManager.createNativeQuery("select p.*" + filter + """
				 order by (select count(*) from post_like l where l.post_id = p.post_id) desc,
				 p.created_at desc, p.post_id desc
				""", CommunityPost.class)
				.setParameter("category", category).setParameter("keyword", keyword)
				.setParameter("authorId", authorId)
				.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize());
		List<CommunityPost> posts = query.getResultList();
		return new PageImpl<>(posts, pageable, total);
	}

	public long countLikes(Long postId) {
		return ((Number) entityManager.createNativeQuery(
				"select count(*) from post_like where post_id = :postId")
				.setParameter("postId", postId).getSingleResult()).longValue();
	}

	public boolean isLiked(Long postId, Long userId) {
		return userId != null && ((Number) entityManager.createNativeQuery(
				"select count(*) from post_like where post_id = :postId and user_id = :userId")
				.setParameter("postId", postId).setParameter("userId", userId)
				.getSingleResult()).longValue() > 0;
	}

	public boolean isReported(Long postId, Long userId) {
		return userId != null && ((Number) entityManager.createNativeQuery("""
				select count(*) from report
				where post_id = :postId and reporter_id = :userId and report_type = 'POST'
				""").setParameter("postId", postId).setParameter("userId", userId)
				.getSingleResult()).longValue() > 0;
	}
}
