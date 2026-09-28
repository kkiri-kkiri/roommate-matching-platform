package com.kkirikkiri.matching.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.kkirikkiri.matching.entity.RoommateMatch;

public interface RoommateMatchRepository
		extends JpaRepository<RoommateMatch, Long>, JpaSpecificationExecutor<RoommateMatch> {

	Page<RoommateMatch> findAllByRequesterUser_UserId(Long requesterUserId, Pageable pageable);

	Page<RoommateMatch> findAllByRequesterUser_UserIdAndMatchScoreGreaterThanEqualOrderByMatchScoreDesc(
			Long requesterUserId, int minimumMatchScore, Pageable pageable);

	Optional<RoommateMatch> findByRequesterUser_UserIdAndTargetUser_UserId(
			Long requesterUserId, Long targetUserId);
    java.util.List<RoommateMatch> findAllByRequesterUser_UserIdInAndTargetUser_UserIdIn(
            java.util.Collection<Long> requesterIds, java.util.Collection<Long> targetIds);
    java.util.List<RoommateMatch> findAllByRequesterUser_UserIdAndTargetUser_UserIdIn(
            Long requesterId, java.util.Collection<Long> targetIds);
}
