package com.kkirikkiri.community.post.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

	/** Only view metadata is changed; stale managed post fields cannot overwrite concurrent edits/deletes. */
	@Modifying
	@Query("update CommunityPost p set p.viewCount = p.viewCount + 1, p.updatedAt = :now "
			+ "where p.postId = :postId and p.status = :status")
	int incrementActiveViews(@Param("postId") Long postId, @Param("status") CommunityContentStatus status,
			@Param("now") java.time.LocalDateTime now);

	/** 댓글 삭제는 이미 삭제된 게시글의 과거 댓글도 정리할 수 있도록 상태를 제한하지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from CommunityPost p where p.postId = :postId")
	Optional<CommunityPost> findByIdForUpdate(@Param("postId") Long postId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from CommunityPost p where p.postId = :postId and p.status = :status")
	Optional<CommunityPost> findDetailForUpdate(@Param("postId") Long postId,
			@Param("status") CommunityContentStatus status);

	/**
	 * category와 keyword가 null 또는 빈 문자열이면 해당 필터를 적용하지 않는다.
	 * 최신순/조회순은 Pageable의 createdAt/viewCount 내림차순으로 지정한다.
	 */
	@EntityGraph(attributePaths = {"author", "dormitory"})
	@Query("""
			select p from CommunityPost p
			left join p.dormitory d
			where p.status = :status
			  and (:category is null or :category = '' or p.category = :category)
			  and (:dormitory is null or :dormitory = '' or d.dormitoryName = :dormitory)
			  and (:keyword is null or :keyword = ''
			       or locate(:keyword, p.title) > 0
			       or locate(:keyword, p.content) > 0)
			""")
	Page<CommunityPost> searchPosts(@Param("status") CommunityContentStatus status,
			@Param("category") String category, @Param("keyword") String keyword,
			@Param("dormitory") String dormitory, Pageable pageable);

	@EntityGraph(attributePaths = {"author", "dormitory"})
	Optional<CommunityPost> findByPostIdAndStatus(Long postId, CommunityContentStatus status);

	/**
	 * 인증 사용자의 ID와 ACTIVE 상태로 내가 작성한 게시글을 조회한다.
	 * 최신순은 Pageable의 createdAt 내림차순으로 지정한다.
	 */
	@EntityGraph(attributePaths = {"author", "dormitory"})
	Page<CommunityPost> findAllByAuthor_UserIdAndStatus(Long userId, CommunityContentStatus status,
			Pageable pageable);
}
