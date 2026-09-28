package com.kkirikkiri.survey.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.survey.entity.PreferenceCriterion;

public interface PreferenceCriterionRepository extends JpaRepository<PreferenceCriterion, Long> {

	List<PreferenceCriterion> findAllByRoommatePreference_PreferenceId(Long preferenceId);
    List<PreferenceCriterion> findAllByRoommatePreference_PreferenceIdIn(java.util.Collection<Long> ids);
}
