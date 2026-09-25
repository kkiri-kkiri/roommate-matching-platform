package com.kkirikkiri.report.repository;

import java.util.List;
import java.util.Optional;

import com.kkirikkiri.global.repository.AdminQuerySupport;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;

@Repository
@Transactional(readOnly = true)
public class AdminReportQueryRepository extends AdminQuerySupport {
	public AdminReportQueryRepository(EntityManager entityManager) {
		super(entityManager);
	}

	private static final String FROM = " from Report r left join r.post p left join r.comment c ";
	private static final String SELECT = """
			select r from Report r
			join fetch r.reporter join fetch r.targetUser
			left join fetch r.post p left join fetch r.comment c
			left join fetch r.processedBy
			""";
	private static final String FILTER = """
			where (:type is null or r.reportType = :type)
			  and (:status is null or r.status = :status)
			  and (:q is null or locate(:q, cast(r.reportId as String)) > 0
			       or locate(:q, p.title) > 0 or locate(:q, p.content) > 0
			       or locate(:q, c.content) > 0 or locate(:q, r.reason) > 0
			       or exists (select up.userProfileId from UserProfile up
			                  where (up.user = r.reporter or up.user = r.targetUser)
			                    and locate(:q, up.nickname) > 0))
			""";

	/** 신고 번호는 Hibernate CAST와 바인딩된 LOCATE로 부분 검색한다. 삭제된 대상의 신고도 포함한다. */
	public Page<Report> searchReports(String q, ReportType type, ReportStatus status, Pageable pageable) {
		return page(SELECT + FILTER + "order by r.createdAt desc, r.reportId desc",
				"select count(r)" + FROM + FILTER, Report.class,
				parameters("q", keyword(q), "type", type, "status", status), pageable);
	}

	public long countReports() {
		return count("select count(r) from Report r", parameters());
	}

	public long countPendingReports() {
		return count("select count(r) from Report r where r.status = :status",
				parameters("status", ReportStatus.PENDING));
	}

	public List<Report> findRecentReports() {
		return recent(SELECT + "order by r.createdAt desc, r.reportId desc", Report.class, parameters());
	}

	public long countReportsReceivedByUserId(Long userId) {
		return count("select count(r) from Report r where r.targetUser.userId = :id",
				parameters("id", userId));
	}

	public Optional<Report> findReportById(Long reportId) {
		return one(SELECT + "where r.reportId = :id", Report.class, parameters("id", reportId));
	}
}
