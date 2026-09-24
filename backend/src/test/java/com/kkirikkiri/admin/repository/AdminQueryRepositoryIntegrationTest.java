package com.kkirikkiri.admin.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.kkirikkiri.admin.entity.ScheduleCrawlCategory;
import com.kkirikkiri.calendar.entity.CalendarCategory;
import com.kkirikkiri.calendar.entity.CalendarEvent;
import com.kkirikkiri.calendar.entity.CalendarEventSourceType;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.entity.PostLike;
import com.kkirikkiri.report.entity.Report;
import com.kkirikkiri.report.entity.ReportStatus;
import com.kkirikkiri.report.entity.ReportType;
import com.kkirikkiri.user.entity.*;

import static org.junit.jupiter.api.Assertions.*;

/** 기존 DB의 스키마/초기 데이터를 변경하지 않으며 테스트 데이터는 트랜잭션 종료 시 롤백한다. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=none", "spring.sql.init.mode=never"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdminMemberQueryRepository.class, AdminPostQueryRepository.class,
		AdminCommentQueryRepository.class, AdminReportQueryRepository.class,
		AdminCrawledScheduleQueryRepository.class})
class AdminQueryRepositoryIntegrationTest {
	@Autowired EntityManager em;
	@Autowired AdminMemberQueryRepository members;
	@Autowired AdminPostQueryRepository posts;
	@Autowired AdminCommentQueryRepository comments;
	@Autowired AdminReportQueryRepository reports;
	@Autowired AdminCrawledScheduleQueryRepository schedules;

	private final String token = UUID.randomUUID().toString().replace("-", "");

	private <T> T persist(T entity) {
		em.persist(entity);
		return entity;
	}

	private User user(String suffix, UserRole role) {
		return persist(new User(token + suffix, "encoded", token + suffix + "@test.invalid", role));
	}

	@Test
	void membersIncludeAdminsAndMissingProfilesAndApplyCombinedFilters() {
		User admin = user("admin", UserRole.ADMIN);
		User regular = user("member", UserRole.USER);
		var dormitory = persist(new Dormitory(token.substring(0, 15), token.substring(0, 25), true));
		var faculty = persist(new Faculty(token, true));
		String nickname = token.substring(0, 18);
		persist(new UserProfile(regular, nickname, "여자", dormitory, faculty, null,
				(short) 2002, "24", "3학년", "INFJ"));
		em.flush();
		em.clear();

		var all = members.searchMembers(token, null, null, PageRequest.of(0, 1));
		assertEquals(2, all.getTotalElements());
		assertEquals(1, all.getContent().size());
		assertEquals(2, members.searchMembers(token, null, null, PageRequest.of(1, 1)).getTotalElements());
		assertNull(members.findMemberById(admin.getUserId()).orElseThrow().get("profile"));
		var found = members.searchMembers(nickname, UserStatus.ACTIVE, dormitory.getDormitoryName(),
				PageRequest.of(0, 10));
		assertEquals(1, found.getTotalElements());
		assertEquals(regular.getUserId(), found.getContent().getFirst().get("member", User.class).getUserId());
		assertEquals(members.countMembers(), members.searchMembers(" \t ", null, null,
				PageRequest.of(0, 1)).getTotalElements());
		assertEquals(1, members.findProfilesByUserIds(List.of(admin.getUserId(), regular.getUserId())).size());
		assertTrue(members.findProfilesByUserIds(List.of()).isEmpty());
		assertTrue(members.findRecentMembers().size() <= 3);
	}

	@Test
	void postsAndCommentsUseExistsWithoutDuplicatesAndKeepSoftDeletedReports() {
		User author = user("author", UserRole.USER);
		User reporter = user("reporter", UserRole.USER);
		User other = user("other", UserRole.USER);
		var first = persist(new CommunityPost(author, null, "자유게시판", token + " first", "content"));
		var second = persist(new CommunityPost(author, null, "자유게시판", token + " second", "content"));
		second.increaseViewCount();
		persist(new PostLike(first, reporter));
		persist(new PostLike(first, other));
		persist(Report.forPost(reporter, first, "욕설·비방", null));
		persist(Report.forPost(other, first, "욕설·비방", null));
		var comment = persist(new CommunityComment(first, author, null, token));
		var reply = persist(new CommunityComment(first, author, comment, token));
		persist(Report.forComment(reporter, reply, "욕설·비방", null));
		persist(Report.forComment(other, reply, "욕설·비방", null));
		em.flush();

		assertEquals(2, posts.searchPosts(token, null, false, "LATEST", PageRequest.of(0, 1)).getTotalElements());
		var reported = posts.searchPosts(token, "자유게시판", true, "LIKES", PageRequest.of(0, 1));
		assertEquals(1, reported.getTotalElements());
		assertEquals(first.getPostId(), reported.getContent().getFirst().getPostId());
		assertEquals(first.getPostId(), posts.searchPosts(token, null, null, "LIKES",
				PageRequest.of(0, 1)).getContent().getFirst().getPostId());
		assertEquals(second.getPostId(), posts.searchPosts(token, null, null, "VIEWS",
				PageRequest.of(0, 1)).getContent().getFirst().getPostId());
		assertEquals(1, comments.searchComments(token, true, PageRequest.of(0, 1)).getTotalElements());
		assertEquals(2, comments.countActiveCommentsByUserId(author.getUserId()));
		assertTrue(comments.findCommentById(reply.getCommentId()).isPresent());
		assertTrue(posts.findPostById(first.getPostId()).isPresent());
		first.delete();
		comment.delete();
		reply.delete();
		em.flush();
		assertTrue(posts.findPostById(first.getPostId()).isEmpty());
		assertTrue(comments.findCommentById(reply.getCommentId()).isEmpty());
		assertEquals(1, posts.countActivePostsByUserId(author.getUserId()));
		assertEquals(0, comments.countActiveCommentsByUserId(author.getUserId()));
		assertEquals(2, reports.searchReports(token, ReportType.POST, ReportStatus.PENDING,
				PageRequest.of(0, 1)).getTotalElements());
		assertEquals(4, reports.countReportsReceivedByUserId(author.getUserId()));
		assertTrue(posts.countActivePosts() >= 1);
		assertTrue(comments.countActiveComments() >= 0);
		assertTrue(posts.findRecentPosts().size() <= 3);
	}

	@Test
	void reportsSearchIdAsSubstringAndTreatWildcardsLiterally() {
		User target = user("target", UserRole.USER);
		User reporter = user("reporter", UserRole.USER);
		var report = persist(Report.forUser(reporter, target, "기타", token));
		em.flush();
		String id = report.getReportId().toString();
		var result = reports.searchReports(id.substring(id.length() - 1), ReportType.USER,
				ReportStatus.PENDING, PageRequest.of(0, 10000));
		assertTrue(result.stream().anyMatch(r -> r.getReportId().equals(report.getReportId())));
		assertEquals(0, reports.searchReports(token + "%_'", null, null,
				PageRequest.of(0, 10)).getTotalElements());
		assertTrue(reports.findReportById(report.getReportId()).isPresent());
		assertEquals(reports.countReports(), reports.searchReports("  ", null, null,
				PageRequest.of(0, 1)).getTotalElements());
		assertTrue(reports.countPendingReports() >= 1);
		assertTrue(reports.findRecentReports().size() <= 3);
	}

	private CalendarCategory category(String code) {
		return em.createQuery("select c from CalendarCategory c where c.categoryCode = :code",
				CalendarCategory.class).setParameter("code", code).getResultList().stream().findFirst()
				.orElseGet(() -> persist(new CalendarCategory(code, code, null, true, true)));
	}

	@Test
	void schedulesExcludePersonalEventsAndFilterSearchAndCategory() {
		User owner = user("owner", UserRole.USER);
		var school = category("SCHOOL");
		var dormitory = category("DORMITORY");
		var custom = persist(new CalendarCategory("CUSTOM-" + token, "custom", owner, false, false));
		LocalDate date = LocalDate.of(2026, 9, 25);
		var schoolEvent = persist(new CalendarEvent(null, school, token, date, date, null, null,
				true, "description", CalendarEventSourceType.CRAWLED, "source", null, LocalDateTime.now()));
		persist(new CalendarEvent(null, dormitory, token, date, date, null, null,
				true, "description", CalendarEventSourceType.CRAWLED, "source", null, LocalDateTime.now()));
		var personal = persist(new CalendarEvent(owner, custom, token, date, date, null, null,
				true, null, CalendarEventSourceType.USER, null, null, null));
		em.flush();
		assertEquals(2, schedules.searchSchedules(token, null, PageRequest.of(0, 1)).getTotalElements());
		assertEquals(1, schedules.searchSchedules(token, ScheduleCrawlCategory.SCHOOL,
				PageRequest.of(0, 1)).getTotalElements());
		assertTrue(schedules.findScheduleById(personal.getEventId()).isEmpty());
		assertTrue(schedules.findScheduleById(schoolEvent.getEventId()).isPresent());
		for (var code : ScheduleCrawlCategory.values()) {
			assertTrue(schedules.countSchedules(code) >= 1);
			assertTrue(schedules.findRecentSchedules(code).size() <= 3);
		}
		assertEquals(schedules.countSchedules(null), schedules.searchSchedules("  ", null,
				PageRequest.of(0, 1)).getTotalElements());
	}
}
