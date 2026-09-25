package com.kkirikkiri.community.repository;

import java.util.List;
import java.util.Optional;

import com.kkirikkiri.global.repository.AdminQuerySupport;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.post.entity.CommunityPost;

@Repository
@Transactional(readOnly = true)
public class AdminPostQueryRepository extends AdminQuerySupport {
	public AdminPostQueryRepository(EntityManager entityManager) {
		super(entityManager);
	}

	private static final String ACTIVE = " p.status = com.kkirikkiri.community.common.entity.CommunityContentStatus.ACTIVE ";
	private static final String FROM = " from CommunityPost p ";
	private static final String SELECT = "select p" + FROM + "join fetch p.author left join fetch p.dormitory ";
	private static final String FILTER = " where " + ACTIVE + """
			and (:q is null or locate(:q, p.title) > 0 or locate(:q, p.content) > 0
			     or exists (select up.userProfileId from UserProfile up
			                where up.user = p.author and locate(:q, up.nickname) > 0))
			and (:category is null or p.category = :category)
			""";
	private static final String REPORTED = """
			and exists (select r.reportId from Report r where r.post = p
			            and r.reportType = com.kkirikkiri.report.entity.ReportType.POST)
			""";

	/** reported는 true일 때만 필터한다. sort는 null/LATEST, VIEWS, LIKES를 지원한다. */
	public Page<CommunityPost> searchPosts(String q, String category, Boolean reported,
			String sort, Pageable pageable) {
		String order = switch (sort == null ? "LATEST" : sort) {
			case "LATEST" -> "p.createdAt desc, p.postId desc";
			case "VIEWS" -> "p.viewCount desc, p.createdAt desc, p.postId desc";
			case "LIKES" -> "(select count(l) from PostLike l where l.post = p) desc, p.createdAt desc, p.postId desc";
			default -> throw new IllegalArgumentException("unsupported post sort");
		};
		String filter = FILTER + (Boolean.TRUE.equals(reported) ? REPORTED : "");
		return page(SELECT + filter + " order by " + order, "select count(p)" + FROM + filter,
				CommunityPost.class, parameters("q", keyword(q), "category", keyword(category)), pageable);
	}

	public long countActivePosts() {
		return count("select count(p)" + FROM + "where " + ACTIVE, parameters());
	}

	public List<CommunityPost> findRecentPosts() {
		return recent(SELECT + "where " + ACTIVE + "order by p.createdAt desc, p.postId desc",
				CommunityPost.class, parameters());
	}

	public long countActivePostsByUserId(Long userId) {
		return count("select count(p)" + FROM + "where " + ACTIVE + "and p.author.userId = :id",
				parameters("id", userId));
	}

	public Optional<CommunityPost> findPostById(Long postId) {
		return one(SELECT + "where " + ACTIVE + "and p.postId = :id",
				CommunityPost.class, parameters("id", postId));
	}
}
