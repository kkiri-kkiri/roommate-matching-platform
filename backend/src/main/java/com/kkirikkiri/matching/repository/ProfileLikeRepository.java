package com.kkirikkiri.matching.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.matching.entity.ProfileLike;

public interface ProfileLikeRepository extends JpaRepository<ProfileLike, Long> {

	boolean existsByUser_UserIdAndTargetUser_UserId(Long userId, Long targetUserId);

	/** 서비스 트랜잭션에서 조회한 관계를 상속받은 delete로 삭제한다. */
	Optional<ProfileLike> findByUser_UserIdAndTargetUser_UserId(Long userId, Long targetUserId);

	Page<ProfileLike> findAllByUser_UserId(Long userId, Pageable pageable);

	/** 페이지의 대상 ID들을 일괄 조회하고 targetUser.userId로 liked 여부를 계산한다. */
	List<ProfileLike> findAllByUser_UserIdAndTargetUser_UserIdIn(
			Long userId, Collection<Long> targetUserIds);
}
