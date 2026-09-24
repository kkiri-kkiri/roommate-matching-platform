package com.kkirikkiri.survey.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.survey.entity.ProfileSurveyOption;

public interface ProfileSurveyOptionRepository extends JpaRepository<ProfileSurveyOption, Long> {

	List<ProfileSurveyOption> findAllByProfileSurvey_ProfileSurveyIdOrderByProfileSurveyOptionIdAsc(Long profileSurveyId);
}
