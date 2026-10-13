package com.kkirikkiri.survey.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.survey.dto.RoommatePreferenceRequest;
import com.kkirikkiri.survey.dto.RoommatePreferenceResponse;
import com.kkirikkiri.survey.entity.PreferenceCriterion;
import com.kkirikkiri.survey.entity.PreferenceCriterionCode;
import com.kkirikkiri.survey.entity.RoommatePreference;
import com.kkirikkiri.survey.entity.RoommatePreferenceOption;
import com.kkirikkiri.survey.repository.PreferenceCriterionRepository;
import com.kkirikkiri.survey.repository.RoommatePreferenceOptionRepository;
import com.kkirikkiri.survey.repository.RoommatePreferenceRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.repository.UserRepository;

/**
 * userId에는 인증된 사용자의 ID를 전달한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoommatePreferenceService {

	private static final String NO_PREFERENCE = "상관없음";

	private static final Set<String> DEPARTMENT_OPTIONS = Set.of(
			"같은 전공", "같은 학부의 다른 전공", "다른 학부", NO_PREFERENCE);
	private static final Set<String> AGE_OPTIONS = Set.of("연하", "동갑", "연상", NO_PREFERENCE);
	private static final Set<String> GRADE_OPTIONS = Set.of(
			"1학년", "2학년", "3학년", "4학년", "5학년", NO_PREFERENCE);
	private static final Set<String> BEDTIME_OPTIONS = Set.of(
			"22시 이전", "22시 ~ 23시", "23시 ~ 24시", "24시 ~ 01시", "01시 ~ 02시", "02시 ~ 03시", "03시 이후",
			NO_PREFERENCE);
	private static final Set<String> WAKE_TIME_OPTIONS = Set.of(
			"07시 이전", "07시 ~ 08시", "08시 ~ 09시", "09시 ~ 10시", "10시 이후", "수업 시간에 따라 유동적",
			NO_PREFERENCE);
	private static final Set<String> SLEEP_HABIT_OPTIONS = Set.of(
			"없음만 가능", "적음까지 괜찮음", "보통까지 괜찮음", NO_PREFERENCE);
	private static final Set<String> SMOKING_OPTIONS = Set.of("비흡연자만", "전자담배까지 허용", NO_PREFERENCE);
	private static final Set<String> LIGHTING_OPTIONS = Set.of("밝음 선호", "어두움 선호", NO_PREFERENCE);
	private static final Set<String> SHOWER_TIME_OPTIONS = Set.of("아침", "저녁", NO_PREFERENCE);
	private static final Set<String> SHOWER_DURATION_OPTIONS = Set.of(
			"10분 이하", "20분 이하", "30분 이하", "40분 이하", NO_PREFERENCE);
	private static final Set<String> CLEANING_FREQUENCY_OPTIONS = Set.of(
			"안함", "주 1회", "주 2회", "주 3회", "주 4회", "주 5회", "매일", "유동적", NO_PREFERENCE);
	private static final Set<String> CLEANING_STYLE_OPTIONS = Set.of(
			"같이 청소하는 사람", "각자 영역만 청소하는 사람", "상황에 따라 협의하는 사람", NO_PREFERENCE);
	private static final Set<String> INTERACTION_OPTIONS = Set.of(
			"친하게 지내고 싶은 사람", "적당히 대화하고 싶은 사람", "필요한 대화만 하고 싶은 사람", NO_PREFERENCE);
	private static final Set<String> MEAL_OPTIONS = Set.of(
			"같이 먹고 싶음", "각자 먹고 싶음", "시간 될 때만 같이 먹고 싶음", NO_PREFERENCE);
	private static final Set<String> SHARED_ITEMS_OPTIONS = Set.of(
			"적극적으로 공유하는 사람", "일부만 공유하는 사람", "각자 사용하는 사람", NO_PREFERENCE);
	private static final Set<String> TEMPERATURE_OPTIONS = Set.of(
			"추위를 타는 사람", "더위를 타는 사람", "보통인 사람", NO_PREFERENCE);

	private static final Map<String, PreferenceCriterionCode> CRITERION_CODES = Map.ofEntries(
			Map.entry("department", PreferenceCriterionCode.DEPARTMENT),
			Map.entry("age", PreferenceCriterionCode.AGE),
			Map.entry("grade", PreferenceCriterionCode.GRADE),
			Map.entry("bedtime", PreferenceCriterionCode.BEDTIME),
			Map.entry("wakeTime", PreferenceCriterionCode.WAKE_TIME),
			Map.entry("sleepTalking", PreferenceCriterionCode.SLEEP_TALKING),
			Map.entry("snoring", PreferenceCriterionCode.SNORING),
			Map.entry("teethGrinding", PreferenceCriterionCode.TEETH_GRINDING),
			Map.entry("smoking", PreferenceCriterionCode.SMOKING),
			Map.entry("lightingPreference", PreferenceCriterionCode.LIGHTING),
			Map.entry("showerTime", PreferenceCriterionCode.SHOWER_TIME),
			Map.entry("showerDuration", PreferenceCriterionCode.SHOWER_DURATION),
			Map.entry("cleaningFrequency", PreferenceCriterionCode.CLEANING_FREQUENCY),
			Map.entry("cleaningStyle", PreferenceCriterionCode.CLEANING_STYLE),
			Map.entry("interaction", PreferenceCriterionCode.INTERACTION),
			Map.entry("meal", PreferenceCriterionCode.MEAL),
			Map.entry("sharedItems", PreferenceCriterionCode.SHARED_ITEMS),
			Map.entry("temperature", PreferenceCriterionCode.TEMPERATURE));

	private final UserRepository userRepository;
	private final RoommatePreferenceRepository roommatePreferenceRepository;
	private final RoommatePreferenceOptionRepository roommatePreferenceOptionRepository;
	private final PreferenceCriterionRepository preferenceCriterionRepository;
	private final Validator validator;
    private final org.springframework.context.ApplicationEventPublisher events;

	public RoommatePreferenceResponse getMyRoommatePreference(Long userId) {
		validateUserId(userId);
		return roommatePreferenceRepository.findByUser_UserId(userId)
				.map(preference -> RoommatePreferenceResponse.from(
						preference,
						roommatePreferenceOptionRepository
								.findAllByRoommatePreference_PreferenceIdOrderByPreferenceOptionIdAsc(
										preference.getPreferenceId()),
						preferenceCriterionRepository.findAllByRoommatePreference_PreferenceId(
								preference.getPreferenceId())))
				.orElseGet(() -> RoommatePreferenceResponse.from(null, List.of(), List.of()));
	}

	@Transactional
	public void saveOrUpdateMyRoommatePreference(Long userId, RoommatePreferenceRequest request) {
		validateUserId(userId);
		validateRequest(request);

		// 프로필 설문과 같은 사용자 잠금을 사용하고 선택지·조건 교체까지 유지한다.
		User user = userRepository.findByIdForSurveyUpdate(userId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		RoommatePreference preference = roommatePreferenceRepository.findByUser_UserId(userId).orElse(null);
		if (preference == null) {
			preference = createPreference(user, request);
		} else {
			updatePreference(preference, request);
		}

		roommatePreferenceRepository.save(preference);
		replaceOptions(preference, request);
		replaceCriteria(preference, request);
        events.publishEvent(new com.kkirikkiri.matching.service.MatchingInputsChanged(userId, false, true));
	}

	private RoommatePreference createPreference(User user, RoommatePreferenceRequest request) {
		return new RoommatePreference(user, request.department(), request.sleepTalking(), request.snoring(),
				request.teethGrinding(), request.smoking(), request.lightingPreference(), request.showerTime(),
				request.showerDuration(), request.cleaningStyle(), request.interaction(), request.meal(),
				request.sharedItems(), request.temperature(), true);
	}

	private void updatePreference(RoommatePreference preference, RoommatePreferenceRequest request) {
		preference.updateAnswers(request.department(), request.sleepTalking(), request.snoring(),
				request.teethGrinding(), request.smoking(), request.lightingPreference(), request.showerTime(),
				request.showerDuration(), request.cleaningStyle(), request.interaction(), request.meal(),
				request.sharedItems(), request.temperature(), true);
	}

	private void replaceOptions(RoommatePreference preference, RoommatePreferenceRequest request) {
		List<RoommatePreferenceOption> existingOptions = roommatePreferenceOptionRepository
				.findAllByRoommatePreference_PreferenceIdOrderByPreferenceOptionIdAsc(preference.getPreferenceId());
		roommatePreferenceOptionRepository.deleteAll(existingOptions);
		roommatePreferenceOptionRepository.flush();

		List<RoommatePreferenceOption> options = new ArrayList<>();
		addOptions(options, preference, PreferenceCriterionCode.AGE, request.age());
		addOptions(options, preference, PreferenceCriterionCode.GRADE, request.grade());
		addOptions(options, preference, PreferenceCriterionCode.BEDTIME, request.bedtime());
		addOptions(options, preference, PreferenceCriterionCode.WAKE_TIME, request.wakeTime());
		addOptions(options, preference, PreferenceCriterionCode.CLEANING_FREQUENCY, request.cleaningFrequency());
		roommatePreferenceOptionRepository.saveAll(options);
	}

	private void addOptions(List<RoommatePreferenceOption> options, RoommatePreference preference,
			PreferenceCriterionCode criterionCode, List<String> values) {
		values.forEach(value -> options.add(new RoommatePreferenceOption(preference, criterionCode, value)));
	}

	private void replaceCriteria(RoommatePreference preference, RoommatePreferenceRequest request) {
		List<PreferenceCriterion> existingCriteria = preferenceCriterionRepository
				.findAllByRoommatePreference_PreferenceId(preference.getPreferenceId());
		preferenceCriterionRepository.deleteAll(existingCriteria);
		preferenceCriterionRepository.flush();

		List<PreferenceCriterion> criteria = CRITERION_CODES.entrySet().stream()
				.map(entry -> new PreferenceCriterion(preference, entry.getValue(),
						request.weights().get(entry.getKey())))
				.toList();
		preferenceCriterionRepository.saveAll(criteria);
	}

	private void validateRequest(RoommatePreferenceRequest request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}

		validateOption(request.department(), DEPARTMENT_OPTIONS);
		validateMultiOptions(request.age(), AGE_OPTIONS);
		validateMultiOptions(request.grade(), GRADE_OPTIONS);
		validateMultiOptions(request.bedtime(), BEDTIME_OPTIONS);
		validateMultiOptions(request.wakeTime(), WAKE_TIME_OPTIONS);
		validateOption(request.sleepTalking(), SLEEP_HABIT_OPTIONS);
		validateOption(request.snoring(), SLEEP_HABIT_OPTIONS);
		validateOption(request.teethGrinding(), SLEEP_HABIT_OPTIONS);
		validateOption(request.smoking(), SMOKING_OPTIONS);
		validateOption(request.lightingPreference(), LIGHTING_OPTIONS);
		validateOption(request.showerTime(), SHOWER_TIME_OPTIONS);
		validateOption(request.showerDuration(), SHOWER_DURATION_OPTIONS);
		validateMultiOptions(request.cleaningFrequency(), CLEANING_FREQUENCY_OPTIONS);
		validateOption(request.cleaningStyle(), CLEANING_STYLE_OPTIONS);
		validateOption(request.interaction(), INTERACTION_OPTIONS);
		validateOption(request.meal(), MEAL_OPTIONS);
		validateOption(request.sharedItems(), SHARED_ITEMS_OPTIONS);
		validateOption(request.temperature(), TEMPERATURE_OPTIONS);

		if (!request.weights().keySet().equals(CRITERION_CODES.keySet())
				|| request.weights().values().stream().anyMatch(weight -> weight == null || weight < 1 || weight > 5)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateOption(String value, Set<String> allowedValues) {
		if (!allowedValues.contains(value)) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateMultiOptions(List<String> values, Set<String> allowedValues) {
		if (new HashSet<>(values).size() != values.size()
				|| !allowedValues.containsAll(values)
				|| values.contains(NO_PREFERENCE) && values.size() != 1) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}
}
