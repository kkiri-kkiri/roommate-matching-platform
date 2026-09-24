package com.kkirikkiri.survey.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.survey.dto.ProfileSurveyRequest;
import com.kkirikkiri.survey.dto.ProfileSurveyResponse;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.entity.ProfileSurveyFieldCode;
import com.kkirikkiri.survey.entity.ProfileSurveyOption;
import com.kkirikkiri.survey.repository.ProfileSurveyOptionRepository;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

/**
 * userId에는 인증된 사용자의 ID를 전달한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileSurveyService {

	private static final String NO_PREFERENCE = "상관없음";

	private static final Set<String> SLEEP_TIMES = Set.of(
			"22시 ~ 23시", "23시 ~ 24시", "24시 ~ 01시", "01시 ~ 02시", "02시 ~ 03시", "03시 이후");
	private static final Set<String> WAKE_TIMES = Set.of(
			"07시 이전", "07시 ~ 08시", "08시 ~ 09시", "09시 ~ 10시", "10시 이후", "수업 시간에 따라 유동적");
	private static final Set<String> SLEEP_HABIT_LEVELS = Set.of("없음", "적음", "보통", "심함");
	private static final Set<String> SLEEP_SENSITIVITIES = Set.of(
			"밝음 (소리에 잘 깨요)", "보통", "어두움 (잘 깨지 않아요)");
	private static final Set<String> INDOOR_EATING_OPTIONS = Set.of("가능", "불가능", "냄새 안나는 음식 가능");
	private static final Set<String> SMOKING_OPTIONS = Set.of("비흡연", "일반담배", "전자담배");
	private static final Set<String> LIGHTING_OPTIONS = Set.of("밝음 선호", "어두움 선호", NO_PREFERENCE);
	private static final Set<String> PHONE_CALL_OPTIONS = Set.of("항상 밖에서", "방에서는 짧게만", NO_PREFERENCE);
	private static final Set<String> MEDIA_SOUND_OPTIONS = Set.of(
			"항상 이어폰 사용해주세요", "공부·과제할 때 아니면 상관없음", "소리만 작게 해주세요", "자유롭게 하세요");
	private static final Set<String> SHOWER_TIME_OPTIONS = Set.of("아침", "저녁", "유동적");
	private static final Set<String> SHOWER_DURATION_OPTIONS = Set.of("10분", "20분", "30분", "40분", "그 이상");
	private static final Set<String> HAIR_DRYER_OPTIONS = Set.of("사용", "미사용");
	private static final Set<String> BUG_HANDLING_OPTIONS = Set.of("잘 잡음", "못 잡음", "작은 벌레는 가능");
	private static final Set<String> CLEANING_FREQUENCY_OPTIONS = Set.of(
			"안함", "주 1회", "주 2회", "주 3회", "주 4회", "주 5회", "매일", "유동적");
	private static final Set<String> CLEANING_STYLE_OPTIONS = Set.of("같이 청소", "각자 영역만 청소", "상황에 따라 협의");
	private static final Set<String> DORM_STAY_DAY_OPTIONS = Set.of("월", "화", "수", "목", "금", "토", "일");
	private static final Set<String> MEAL_PLAN_OPTIONS = Set.of("조식", "중식", "석식", "신청 안 함");
	private static final Set<String> MEAL_OPTIONS = Set.of(
			"같이 먹고 싶음", "각자 먹고 싶음", "시간 될 때만 같이 먹고 싶음", NO_PREFERENCE);
	private static final Set<String> ROOMMATE_INTERACTION_OPTIONS = Set.of(
			"친하게 지내고 싶음", "적당히 대화", "필요한 대화만");
	private static final Set<String> TEMPERATURE_OPTIONS = Set.of("추위 타요", "더위 타요", "보통");
	private static final Set<String> SCENT_PRODUCT_OPTIONS = Set.of("자주 사용", "가끔 사용", "사용 안 함", NO_PREFERENCE);
	private static final Set<String> DESKTOP_USE_OPTIONS = Set.of(
			"사용해요", "사용하지 않아요", "특정 시간 제외하면 상관없음");
	private static final Set<String> SHARED_ITEMS_OPTIONS = Set.of("적극적으로 공유", "일부만 공유", "각자 사용");

	private final UserRepository userRepository;
	private final ProfileSurveyRepository profileSurveyRepository;
	private final ProfileSurveyOptionRepository profileSurveyOptionRepository;
	private final Validator validator;

	public ProfileSurveyResponse getMyProfileSurvey(Long userId) {
		validateUserId(userId);
		return profileSurveyRepository.findByUser_UserId(userId)
				.map(survey -> ProfileSurveyResponse.from(survey,
						profileSurveyOptionRepository.findAllByProfileSurvey_ProfileSurveyIdOrderByProfileSurveyOptionIdAsc(
								survey.getProfileSurveyId())))
				.orElseGet(() -> ProfileSurveyResponse.from(null, List.of()));
	}

	@Transactional
	public void saveOrUpdateMyProfileSurvey(Long userId, ProfileSurveyRequest request) {
		validateUserId(userId);
		validateRequest(request);

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		ProfileSurvey survey = profileSurveyRepository.findByUser_UserId(userId).orElse(null);
		if (survey == null) {
			survey = createSurvey(user, request);
		} else {
			updateSurvey(survey, request);
		}
		profileSurveyRepository.save(survey);
		replaceOptions(survey, request);
	}

	private ProfileSurvey createSurvey(User user, ProfileSurveyRequest request) {
		return new ProfileSurvey(user, request.sleepTime(), request.wakeTime(), request.sleepTalking(),
				request.snoring(), request.teethGrinding(), request.sleepSensitivity(), request.indoorEating(),
				request.smoking(), request.lightingPreference(), request.phoneCall(), request.mediaSound(),
				request.showerTime(), request.showerDuration(), request.hairDryer(), request.cleaningFrequency(),
				request.cleaningStyle(), request.meal(), request.roommateInteraction(),
				request.temperaturePreference(), request.bugHandling(), request.scentProductUse(),
				request.desktopUse(), request.sharedItems(), true);
	}

	private void updateSurvey(ProfileSurvey survey, ProfileSurveyRequest request) {
		survey.updateAnswers(request.sleepTime(), request.wakeTime(), request.sleepTalking(), request.snoring(),
				request.teethGrinding(), request.sleepSensitivity(), request.indoorEating(), request.smoking(),
				request.lightingPreference(), request.phoneCall(), request.mediaSound(), request.showerTime(),
				request.showerDuration(), request.hairDryer(), request.cleaningFrequency(), request.cleaningStyle(),
				request.meal(), request.roommateInteraction(), request.temperaturePreference(),
				request.bugHandling(), request.scentProductUse(), request.desktopUse(), request.sharedItems(), true);
	}

	private void replaceOptions(ProfileSurvey survey, ProfileSurveyRequest request) {
		List<ProfileSurveyOption> existingOptions = profileSurveyOptionRepository
				.findAllByProfileSurvey_ProfileSurveyIdOrderByProfileSurveyOptionIdAsc(survey.getProfileSurveyId());
		profileSurveyOptionRepository.deleteAll(existingOptions);
		profileSurveyOptionRepository.flush();

		List<ProfileSurveyOption> options = new ArrayList<>();
		request.dormStayDays().forEach(value -> options.add(
				new ProfileSurveyOption(survey, ProfileSurveyFieldCode.DORM_STAY_DAY, value)));
		request.mealPlan().forEach(value -> options.add(
				new ProfileSurveyOption(survey, ProfileSurveyFieldCode.MEAL_PLAN, value)));
		profileSurveyOptionRepository.saveAll(options);
	}

	private void validateRequest(ProfileSurveyRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}

		validateOption(request.sleepTime(), SLEEP_TIMES);
		validateOption(request.wakeTime(), WAKE_TIMES);
		validateOption(request.sleepTalking(), SLEEP_HABIT_LEVELS);
		validateOption(request.snoring(), SLEEP_HABIT_LEVELS);
		validateOption(request.teethGrinding(), SLEEP_HABIT_LEVELS);
		validateOption(request.sleepSensitivity(), SLEEP_SENSITIVITIES);
		validateOption(request.indoorEating(), INDOOR_EATING_OPTIONS);
		validateOption(request.smoking(), SMOKING_OPTIONS);
		validateOption(request.lightingPreference(), LIGHTING_OPTIONS);
		validateOption(request.phoneCall(), PHONE_CALL_OPTIONS);
		validateOption(request.mediaSound(), MEDIA_SOUND_OPTIONS);
		validateOption(request.showerTime(), SHOWER_TIME_OPTIONS);
		validateOption(request.showerDuration(), SHOWER_DURATION_OPTIONS);
		validateOption(request.hairDryer(), HAIR_DRYER_OPTIONS);
		validateOption(request.bugHandling(), BUG_HANDLING_OPTIONS);
		validateOption(request.cleaningFrequency(), CLEANING_FREQUENCY_OPTIONS);
		validateOption(request.cleaningStyle(), CLEANING_STYLE_OPTIONS);
		validateOptions(request.dormStayDays(), DORM_STAY_DAY_OPTIONS);
		validateOptions(request.mealPlan(), MEAL_PLAN_OPTIONS);
		validateOption(request.meal(), MEAL_OPTIONS);
		validateOption(request.roommateInteraction(), ROOMMATE_INTERACTION_OPTIONS);
		validateOption(request.temperaturePreference(), TEMPERATURE_OPTIONS);
		validateOption(request.scentProductUse(), SCENT_PRODUCT_OPTIONS);
		validateOption(request.desktopUse(), DESKTOP_USE_OPTIONS);
		validateOption(request.sharedItems(), SHARED_ITEMS_OPTIONS);

		if (request.mealPlan().contains("신청 안 함") && request.mealPlan().size() != 1) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateOption(String value, Set<String> allowedValues) {
		if (!allowedValues.contains(value)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateOptions(List<String> values, Set<String> allowedValues) {
		if (new HashSet<>(values).size() != values.size() || !allowedValues.containsAll(values)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}
}
