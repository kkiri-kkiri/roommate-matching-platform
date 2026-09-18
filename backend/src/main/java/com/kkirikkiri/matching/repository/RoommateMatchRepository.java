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

	Page<RoommateMatch> findAllByRequesterUser_UserIdAndMatchScoreGreaterThanEqualAndRequiredConditionsMetTrueOrderByMatchScoreDesc(
			Long requesterUserId, int minimumMatchScore, Pageable pageable);

	Optional<RoommateMatch> findByRequesterUser_UserIdAndTargetUser_UserId(
			Long requesterUserId, Long targetUserId);
}
