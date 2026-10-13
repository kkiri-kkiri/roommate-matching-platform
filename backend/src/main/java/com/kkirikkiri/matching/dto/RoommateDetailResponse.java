package com.kkirikkiri.matching.dto;

import java.util.List;

import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.entity.ProfileSurveyOption;
import com.kkirikkiri.survey.dto.ProfileSurveyResponse;
import com.kkirikkiri.user.entity.UserProfile;

public record RoommateDetailResponse(
        Long id,
        String nickname,
        String admissionYear,
        String grade,
        String faculty,
        String major,
        String dormitory,
        String mbti,
        String introduction,
        List<String> tags,
        String profileImageUrl,
        Integer matchScore,
        boolean liked,
        Answers answers
) {

    public RoommateDetailResponse {
        tags = List.copyOf(tags);
    }

    public static RoommateDetailResponse from(
            UserProfile profile,
            ProfileSurvey survey,
            List<ProfileSurveyOption> options,
            List<String> tags,
            Integer matchScore,
            boolean liked
    ) {
        return new RoommateDetailResponse(
                profile.getUser().getUserId(),
                profile.getNickname(),
                profile.getAdmissionYear(),
                profile.getGrade(),
                profile.getFaculty().getFacultyName(),
                profile.getMajor() == null
                        ? null
                        : profile.getMajor().getMajorName(),
                profile.getDormitory().getDormitoryName(),
                profile.getMbti(),
                profile.getIntroduction(),
                tags,
                profile.getProfileImageUrl(),
                matchScore,
                liked,
                Answers.from(survey, options)
        );
    }

    public record Answers(
            String sleepTime,
            String wakeTime,
            String sleepTalking,
            String snoring,
            String teethGrinding,
            String sleepSensitivity,

            String indoorEating,
            String smoking,
            String lightingPreference,
            String phoneCall,
            String mediaSound,
            String showerTime,
            String showerDuration,

            String cleaningFrequency,
            String cleaningStyle,

            String meal,
            String roommateInteraction,
            String temperaturePreference,
            String scentProductUse,
            String desktopUse,
            String sharedItems,
            List<String> dormStayDays,
            List<String> mealPlan,

            boolean completed
    ) {

        public static Answers from(ProfileSurvey survey, List<ProfileSurveyOption> options) {
            var answers = ProfileSurveyResponse.Answers.from(survey, options);

            return new Answers(
                    survey.getSleepTime(),
                    survey.getWakeTime(),
                    survey.getSleepTalking(),
                    survey.getSnoring(),
                    survey.getTeethGrinding(),
                    survey.getSleepSensitivity(),

                    survey.getIndoorEating(),
                    survey.getSmoking(),
                    survey.getLightingPreference(),
                    survey.getPhoneCall(),
                    survey.getMediaSound(),
                    survey.getShowerTime(),
                    survey.getShowerDuration(),

                    survey.getCleaningFrequency(),
                    survey.getCleaningStyle(),

                    survey.getMeal(),
                    survey.getRoommateInteraction(),
                    survey.getTemperaturePreference(),
                    survey.getScentProductUse(),
                    survey.getDesktopUse(),
                    survey.getSharedItems(),
                    answers.dormStayDays(),
                    answers.mealPlan(),

                    survey.isCompleted()
            );
        }
    }
}
