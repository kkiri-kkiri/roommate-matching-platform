package com.kkirikkiri.community.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.community.entity.CommunityContentStatus;
import com.kkirikkiri.community.entity.CommunityPost;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

	/**
	 * category와 keyword가 null 또는 빈 문자열이면 해당 필터를 적용하지 않는다.
	 * 최신순/조회순은 Pageable의 createdAt/viewCount 내림차순으로 지정한다.
	 */
	@Query("""
			select p from CommunityPost p
			where p.status = :status
			  and (:category is null or :category = '' or p.category = :category)
			  and (:keyword is null or :keyword = ''
			       or locate(:keyword, p.title) > 0
			       or locate(:keyword, p.content) > 0)
			""")
	Page<CommunityPost> searchPosts(@Param("status") CommunityContentStatus status,
			@Param("category") String category, @Param("keyword") String keyword, Pageable pageable);

	Optional<CommunityPost> findByPostIdAndStatus(Long postId, CommunityContentStatus status);
}
