package com.kkirikkiri.community.comment.repository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.kkirikkiri.community.comment.entity.CommunityAnonymousAuthor;

public interface CommunityAnonymousAuthorRepository extends JpaRepository<CommunityAnonymousAuthor, Long> {
	// Current read: 댓글/사용자 조회로 이미 생성된 MySQL RR 스냅샷을 사용하지 않는다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from CommunityAnonymousAuthor a where a.post.postId = :postId")
	List<CommunityAnonymousAuthor> findAllForUpdate(@Param("postId") Long postId);
}
