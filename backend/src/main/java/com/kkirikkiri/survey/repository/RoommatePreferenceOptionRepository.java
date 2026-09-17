package com.kkirikkiri.survey.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.survey.entity.RoommatePreferenceOption;

public interface RoommatePreferenceOptionRepository extends JpaRepository<RoommatePreferenceOption, Long> {

	List<RoommatePreferenceOption> findByRoommatePreference_PreferenceId(Long preferenceId);
}
