package com.kkirikkiri.survey.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.survey.entity.RoommatePreference;

public interface RoommatePreferenceRepository extends JpaRepository<RoommatePreference, Long> {

	Optional<RoommatePreference> findByUser_UserId(Long userId);
    java.util.List<RoommatePreference> findAllByUser_UserIdIn(java.util.Collection<Long> ids);
}
