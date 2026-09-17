package com.kkirikkiri.survey.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.survey.entity.ProfileSurvey;

public interface ProfileSurveyRepository extends JpaRepository<ProfileSurvey, Long> {

	Optional<ProfileSurvey> findByMember_UserId(Long userId);
}
