package com.kkirikkiri.community.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.community.entity.CommunityPost;
import com.kkirikkiri.community.entity.CommunityStatus;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

	Optional<CommunityPost> findByPostIdAndStatus(Long postId, CommunityStatus status);

	Page<CommunityPost> findByAuthor_UserIdAndStatus(Long userId, CommunityStatus status, Pageable pageable);

	/**
	 * category, keyword가 null이면 해당 조건을 적용하지 않는다.
	 * 최신순/조회순은 Pageable의 createdAt/viewCount 정렬로 지정한다.
	 */
	@Query("""
			select p from CommunityPost p
			where p.status = :status
			  and (:category is null or p.category = :category)
			  and (:keyword is null
			       or p.title like %:#{escape(#keyword)}% escape :#{escapeCharacter()}
			       or p.content like %:#{escape(#keyword)}% escape :#{escapeCharacter()})
			""")
	Page<CommunityPost> searchPosts(@Param("status") CommunityStatus status,
			@Param("category") String category, @Param("keyword") String keyword, Pageable pageable);

	/**
	 * 좋아요가 없는 게시글도 포함한다. Pageable은 정렬 없이 전달한다.
	 * 동률은 작성일, 게시글 ID 내림차순으로 정렬한다.
	 */
	@Query(value = """
			select p from CommunityPost p
			left join PostLike pl on pl.post = p
			where p.status = :status
			  and (:category is null or p.category = :category)
			  and (:keyword is null
			       or p.title like %:#{escape(#keyword)}% escape :#{escapeCharacter()}
			       or p.content like %:#{escape(#keyword)}% escape :#{escapeCharacter()})
			group by p
			order by count(pl) desc, p.createdAt desc, p.postId desc
			""", countQuery = """
			select count(p) from CommunityPost p
			where p.status = :status
			  and (:category is null or p.category = :category)
			  and (:keyword is null
			       or p.title like %:#{escape(#keyword)}% escape :#{escapeCharacter()}
			       or p.content like %:#{escape(#keyword)}% escape :#{escapeCharacter()})
			""")
	Page<CommunityPost> searchPostsOrderByLikeCountDesc(@Param("status") CommunityStatus status,
			@Param("category") String category, @Param("keyword") String keyword, Pageable pageable);
}
