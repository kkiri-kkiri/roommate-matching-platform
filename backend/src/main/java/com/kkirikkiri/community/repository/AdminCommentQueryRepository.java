package com.kkirikkiri.community.repository;

import java.util.List;
import java.util.Optional;

import com.kkirikkiri.global.repository.AdminQuerySupport;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.community.comment.entity.CommunityComment;

@Repository
@Transactional(readOnly = true)
public class AdminCommentQueryRepository extends AdminQuerySupport {
	public AdminCommentQueryRepository(EntityManager entityManager) {
		super(entityManager);
	}

	private static final String ACTIVE = " c.status = com.kkirikkiri.community.common.entity.CommunityContentStatus.ACTIVE ";
	private static final String FROM = " from CommunityComment c ";
	private static final String SELECT = "select c" + FROM
			+ "join fetch c.author join fetch c.post left join fetch c.parentComment ";
	private static final String FILTER = " where " + ACTIVE + """
			and (:q is null or locate(:q, c.content) > 0
			     or exists (select up.userProfileId from UserProfile up
			                where up.user = c.author and locate(:q, up.nickname) > 0))
			""";
	private static final String REPORTED = """
			and exists (select r.reportId from Report r where r.comment = c
			            and r.reportType = com.kkirikkiri.report.entity.ReportType.COMMENT)
			""";

	/** 댓글·답글을 함께 조회하며 reported는 true일 때만 필터한다. */
	public Page<CommunityComment> searchComments(String q, Boolean reported, Pageable pageable) {
		String filter = FILTER + (Boolean.TRUE.equals(reported) ? REPORTED : "");
		return page(SELECT + filter + "order by c.createdAt desc, c.commentId desc",
				"select count(c)" + FROM + filter, CommunityComment.class,
				parameters("q", keyword(q)), pageable);
	}

	public long countActiveComments() {
		return count("select count(c)" + FROM + "where " + ACTIVE, parameters());
	}

	public long countActiveCommentsByUserId(Long userId) {
		return count("select count(c)" + FROM + "where " + ACTIVE + "and c.author.userId = :id",
				parameters("id", userId));
	}

	public Optional<CommunityComment> findCommentById(Long commentId) {
		return one(SELECT + "where " + ACTIVE + "and c.commentId = :id",
				CommunityComment.class, parameters("id", commentId));
	}
}
