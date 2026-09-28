package com.kkirikkiri.survey.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.survey.entity.ProfileSurvey;

public interface ProfileSurveyRepository extends JpaRepository<ProfileSurvey, Long> {

	Optional<ProfileSurvey> findByUser_UserId(Long userId);
    java.util.List<ProfileSurvey> findAllByUser_UserIdIn(java.util.Collection<Long> ids);
}
