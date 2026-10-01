package com.kkirikkiri.user.repository;

import java.util.List;
import java.util.Optional;

import com.kkirikkiri.global.repository.AdminQuerySupport;

import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.Tuple;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserStatus;

@Repository
@Transactional(readOnly = true)
public class AdminMemberQueryRepository extends AdminQuerySupport {
	public AdminMemberQueryRepository(EntityManager entityManager) {
		super(entityManager);
	}

	private static final String FROM = """
			from User u left join UserProfile p on p.user = u
			left join p.dormitory d
			""";
	private static final String SELECT = """
			select u as member, p as profile
			""" + FROM + """
			left join fetch p.dormitory left join fetch p.faculty left join fetch p.major
			""";
	private static final String FILTER = """
			where (:q is null or locate(:q, u.schoolEmail) > 0
			       or locate(:q, p.nickname) > 0)
			  and (:status is null or u.status = :status)
			  and (:dormitory is null or d.dormitoryName = :dormitory)
			""";

	/** Tuple aliases: member(User), profile(UserProfile 또는 null). ADMIN 및 프로필 없는 회원도 포함한다. */
	public Page<Tuple> searchMembers(String q, UserStatus status, String dormitory, Pageable pageable) {
		return page(SELECT + FILTER + " order by u.createdAt desc, u.userId desc",
				"select count(u) " + FROM + FILTER, Tuple.class,
				parameters("q", keyword(q), "status", status, "dormitory", dormitory), pageable);
	}

	public long countMembers() {
		return count("select count(u) from User u", parameters());
	}

	public List<Tuple> findRecentMembers() {
		return recent(SELECT + " order by u.createdAt desc, u.userId desc", Tuple.class, parameters());
	}

	public Optional<Tuple> findMemberById(Long memberId) {
		return one(SELECT + " where u.userId = :id", Tuple.class, parameters("id", memberId));
	}

	/** 게시글·댓글·신고 응답의 닉네임을 사용자 ID 목록으로 일괄 조회한다. 프로필 없는 사용자는 결과에 없다. */
	public List<UserProfile> findProfilesByUserIds(List<Long> userIds) {
		if (userIds.isEmpty()) {
			return List.of();
		}
		return query("""
				select p from UserProfile p join fetch p.user
				join fetch p.dormitory join fetch p.faculty left join fetch p.major
				where p.user.userId in :ids
				""", UserProfile.class, parameters("ids", userIds)).getResultList();
	}
}
