package com.kkirikkiri.matching.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.matching.entity.ProfileLike;

public interface ProfileLikeRepository extends JpaRepository<ProfileLike, Long> {

	boolean existsByUser_UserIdAndTargetUser_UserId(Long userId, Long targetUserId);

	void deleteByUser_UserIdAndTargetUser_UserId(Long userId, Long targetUserId);

	Page<ProfileLike> findAllByUser_UserId(Long userId, Pageable pageable);
}
