import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Info,
  LockKeyhole,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";

import * as S from "./SurveyPageCss";
import {
  FACULTY_OPTIONS,
  INITIAL_SURVEY_VALUES,
  SURVEY_STEPS,
} from "./surveyData";

const REVIEW_STEP = {
  id: "review",
  title: "답변 확인",
  shortTitle: "답변 확인",
  description: "입력한 내용을 확인하고 설문을 완료해주세요.",
};

const FLOW_STEPS = [...SURVEY_STEPS, REVIEW_STEP];

const FIELD_HINTS = {
  faculty: "학부를 먼저 선택해주세요",
  major: "선택한 학부의 전공만 표시돼요",
  studentId: "숫자만 입력해주세요",
  mbti: "네 가지 기준을 모두 선택해주세요",
  dormStayDays: "여러 요일을 선택할 수 있어요",
  mealPlan: "여러 끼니를 선택할 수 있어요",
  roommateAgePreference: "원하는 범위를 모두 선택할 수 있어요",
  roommateGradePreference: "원하는 학년을 모두 선택할 수 있어요",
  roommateOtherWishes: "선택 입력 · 최대 300자",
};

const FRIENDLY_FIELD_LABELS = {
  sleepTime: "취침시간",
  wakeTime: "기상시간",
  sleepTalking: "잠꼬대",
  snoring: "코골이",
  teethGrinding: "이갈이",
  sleepSensitivity: "잠귀",
  lightingPreference: "조명 환경",
  phoneCall: "전화",
  dormStayDays: "상주 요일",
  hairDryerInRoom: "방 안 드라이기",
  cleaningFrequency: "청소 빈도",
  cleaningStyle: "청소 방식",
  indoorEating: "실내 취식",
  smoking: "흡연 여부",
  mediaSound: "음악·영상 소리",
  showerTime: "샤워 시간대",
  showerDuration: "샤워 소요시간",
  temperaturePreference: "온도 선호",
  roommateInteraction: "교류 정도",
  mealPlan: "신청 식사",
  bugHandling: "벌레 처리",
  scentProductUse: "향 제품",
  desktopUse: "데스크탑",
  sharedItems: "공용물품",
  roommateAcademicPreference: "학과·학부",
  roommateAgePreference: "나이",
  roommateGradePreference: "학년",
  roommateSleepHabitPreference: "잠버릇",
  roommateSmokingPreference: "흡연 여부",
  roommateMealPreference: "기숙사 식사",
  roommateOtherWishes: "그 외에 원하는 점",
};

function findFaculty(facultyName) {
  return FACULTY_OPTIONS.find((faculty) => faculty.name === facultyName);
}

function getMbti(values) {
  return [
    values.mbtiEnergy,
    values.mbtiInformation,
    values.mbtiDecision,
    values.mbtiLifestyle,
  ].join("");
}

function isEmptyValue(value) {
  return Array.isArray(value) ? value.length === 0 : !String(value || "").trim();
}

function validateStep(step, values) {
  const nextErrors = {};
  const selectedFaculty = findFaculty(values.faculty);

  step.fields.forEach((field) => {
    if (field.type === "mbti") {
      const hasAllDimensions = field.dimensions.every(
        (dimension) => values[dimension.id],
      );

      if (!hasAllDimensions) {
        nextErrors[field.id] = "MBTI 네 가지 기준을 모두 선택해주세요.";
      }
      return;
    }

    if (
      field.id === "major" &&
      selectedFaculty &&
      selectedFaculty.majors.length === 0
    ) {
      return;
    }

    if (field.required && isEmptyValue(values[field.id])) {
      nextErrors[field.id] = `${field.label} 항목을 선택하거나 입력해주세요.`;
      return;
    }

    if (field.id === "birthYear" && values.birthYear) {
      const currentYear = new Date().getFullYear();
      const birthYear = Number(values.birthYear);

      if (!/^\d{4}$/.test(values.birthYear) || birthYear < 1900 || birthYear > currentYear) {
        nextErrors.birthYear = `1900년부터 ${currentYear}년 사이의 연도를 입력해주세요.`;
      }
    }

    if (field.id === "studentId" && values.studentId && !/^\d+$/.test(values.studentId)) {
      nextErrors.studentId = "학번은 숫자로만 입력해주세요.";
    }
  });

  return nextErrors;
}

function getChoiceColumns(optionCount) {
  if (optionCount <= 2) return 2;
  if (optionCount === 3) return 3;
  return 4;
}

function SurveyPage() {
  const [values, setValues] = useState(() => ({ ...INITIAL_SURVEY_VALUES }));
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [highestVisitedStep, setHighestVisitedStep] = useState(0);
  const [errors, setErrors] = useState({});
  const [showValidationBanner, setShowValidationBanner] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const headingRef = useRef(null);

  const currentStep = FLOW_STEPS[currentStepIndex];
  const isReviewStep = currentStep.id === REVIEW_STEP.id;
  const progress = Math.round(((currentStepIndex + 1) / FLOW_STEPS.length) * 100);
  const selectedFaculty = useMemo(
    () => findFaculty(values.faculty),
    [values.faculty],
  );
  const mbti = getMbti(values);

  useEffect(() => {
    headingRef.current?.focus({ preventScroll: true });
  }, [currentStepIndex]);

  useEffect(() => {
    if (showValidationBanner && Object.keys(errors).length === 0) {
      setShowValidationBanner(false);
    }
  }, [errors, showValidationBanner]);

  const clearFieldError = (fieldId) => {
    setErrors((currentErrors) => {
      if (!currentErrors[fieldId]) return currentErrors;

      const nextErrors = { ...currentErrors };
      delete nextErrors[fieldId];
      return nextErrors;
    });
  };

  const setFieldValue = (fieldId, value) => {
    setValues((currentValues) => {
      const nextValues = { ...currentValues, [fieldId]: value };

      if (fieldId === "faculty") {
        const faculty = findFaculty(value);
        nextValues.major = faculty?.majors.length === 0 ? "세부전공 없음" : "";

        const academicPreference = nextValues.roommateAcademicPreference;
        const preferenceOnlyForMajors = [
          "같은 전공",
          "같은 학부의 다른 전공",
        ].includes(academicPreference);
        const preferenceOnlyForAutonomousFaculty = academicPreference === "같은 학부";

        if (
          (faculty?.majors.length === 0 && preferenceOnlyForMajors) ||
          (faculty?.majors.length > 0 && preferenceOnlyForAutonomousFaculty)
        ) {
          nextValues.roommateAcademicPreference = "";
        }
      }

      return nextValues;
    });
    clearFieldError(fieldId);

    if (fieldId === "faculty") {
      clearFieldError("major");
    }
  };

  const toggleMultiValue = (field, option) => {
    const currentValues = Array.isArray(values[field.id]) ? values[field.id] : [];
    const exclusiveOptions = field.exclusiveOptions || [];
    let nextValues;

    if (exclusiveOptions.includes(option)) {
      nextValues = currentValues.includes(option) ? [] : [option];
    } else {
      const withoutExclusive = currentValues.filter(
        (value) => !exclusiveOptions.includes(value),
      );
      nextValues = withoutExclusive.includes(option)
        ? withoutExclusive.filter((value) => value !== option)
        : [...withoutExclusive, option];
    }

    setFieldValue(field.id, nextValues);
  };

  const toggleAllOptions = (field) => {
    const hasEveryOption = field.options.every((option) =>
      values[field.id].includes(option),
    );
    setFieldValue(field.id, hasEveryOption ? [] : [...field.options]);
  };

  const focusFirstError = (nextErrors) => {
    const firstFieldId = Object.keys(nextErrors)[0];
    if (!firstFieldId) return;

    window.requestAnimationFrame(() => {
      const fieldElement = document.querySelector(
        `[data-field-id="${firstFieldId}"] input, [data-field-id="${firstFieldId}"] select, [data-field-id="${firstFieldId}"] textarea, [data-field-id="${firstFieldId}"] button`,
      );
      fieldElement?.focus();
    });
  };

  const goToStep = (stepIndex) => {
    if (stepIndex > highestVisitedStep) return;
    setErrors({});
    setShowValidationBanner(false);
    setCurrentStepIndex(stepIndex);
  };

  const moveBack = () => {
    if (currentStepIndex === 0) return;
    setErrors({});
    setShowValidationBanner(false);
    setCurrentStepIndex((index) => index - 1);
  };

  const moveNext = () => {
    if (isReviewStep) {
      for (let stepIndex = 0; stepIndex < SURVEY_STEPS.length; stepIndex += 1) {
        const nextErrors = validateStep(SURVEY_STEPS[stepIndex], values);

        if (Object.keys(nextErrors).length > 0) {
          setCurrentStepIndex(stepIndex);
          setErrors(nextErrors);
          setShowValidationBanner(true);
          focusFirstError(nextErrors);
          return;
        }
      }

      setSubmitted(true);
      return;
    }

    const nextErrors = validateStep(currentStep, values);

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      setShowValidationBanner(true);
      focusFirstError(nextErrors);
      return;
    }

    const nextStepIndex = currentStepIndex + 1;
    setErrors({});
    setShowValidationBanner(false);
    setHighestVisitedStep((highestStep) =>
      Math.max(highestStep, nextStepIndex),
    );
    setCurrentStepIndex(nextStepIndex);
  };

  const handleFormSubmit = (event) => {
    event.preventDefault();
    moveNext();
  };

  const editFromReview = (stepIndex) => {
    setCurrentStepIndex(stepIndex);
    setErrors({});
  };

  const reopenReview = () => {
    setSubmitted(false);
    setCurrentStepIndex(FLOW_STEPS.length - 1);
  };

  const getAcademicOptions = (field) => {
    if (
      field.id === "roommateAcademicPreference" &&
      selectedFaculty?.majors.length === 0
    ) {
      return ["같은 학부", "다른 학부", "상관없음"];
    }

    return field.options;
  };

  const renderFieldLabel = (field) => (
    <S.LabelRow>
      <S.LabelText>
        {field.label}
        {field.required && <S.Required aria-label="필수">필수</S.Required>}
      </S.LabelText>
      {FIELD_HINTS[field.id] && (
        <S.FieldHint>{FIELD_HINTS[field.id]}</S.FieldHint>
      )}
    </S.LabelRow>
  );

  const renderChoiceField = (field) => {
    const options = getAcademicOptions(field);
    const isMultiple = field.type === "multiSelect";
    const selectedValues = isMultiple ? values[field.id] : [];

    return (
      <S.Fieldset
        key={field.id}
        data-field-id={field.id}
        aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
      >
        <S.Legend>{renderFieldLabel(field)}</S.Legend>

        {field.allShortcut && (
          <S.QuickSelectRow>
            <S.QuickSelectButton
              type="button"
              onClick={() => toggleAllOptions(field)}
              aria-pressed={field.options.every((option) =>
                selectedValues.includes(option),
              )}
            >
              {field.options.every((option) => selectedValues.includes(option))
                ? "매일 선택 해제"
                : "매일 상주"}
            </S.QuickSelectButton>
          </S.QuickSelectRow>
        )}

        <S.ChoiceGrid
          $columns={getChoiceColumns(options.length)}
          $mobileColumns={options.length === 3 ? 1 : 2}
        >
          {options.map((option, optionIndex) => {
            const selected = isMultiple
              ? selectedValues.includes(option)
              : values[field.id] === option;
            const inputId = `${field.id}-${optionIndex}`;

            return (
              <S.ChoiceLabel key={option} htmlFor={inputId} $selected={selected}>
                <S.HiddenInput
                  id={inputId}
                  type={isMultiple ? "checkbox" : "radio"}
                  name={field.id}
                  value={option}
                  checked={selected}
                  onChange={() =>
                    isMultiple
                      ? toggleMultiValue(field, option)
                      : setFieldValue(field.id, option)
                  }
                />
                <S.ChoiceIndicator $selected={selected} $multiple={isMultiple}>
                  {selected && <Check aria-hidden="true" />}
                </S.ChoiceIndicator>
                <span>{option}</span>
              </S.ChoiceLabel>
            );
          })}
        </S.ChoiceGrid>

        {errors[field.id] && (
          <S.ErrorText id={`${field.id}-error`} role="alert">
            <AlertCircle size={13} aria-hidden="true" />
            {errors[field.id]}
          </S.ErrorText>
        )}
      </S.Fieldset>
    );
  };

  const renderSelectField = (field) => {
    const isFaculty = field.type === "facultySelect";
    const options = isFaculty
      ? FACULTY_OPTIONS.map((faculty) => faculty.name)
      : selectedFaculty?.majors || [];
    const isNoMajorFaculty = !isFaculty && selectedFaculty?.majors.length === 0;
    const disabled = !isFaculty && (!selectedFaculty || isNoMajorFaculty);

    return (
      <S.Field key={field.id} data-field-id={field.id}>
        <S.FieldLabel htmlFor={field.id}>
          {field.label}
          {field.required && <S.Required aria-label="필수">필수</S.Required>}
        </S.FieldLabel>
        <S.SelectWrap>
          <S.Select
            id={field.id}
            value={values[field.id]}
            disabled={disabled}
            onChange={(event) => setFieldValue(field.id, event.target.value)}
            aria-invalid={Boolean(errors[field.id])}
            aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
            $hasError={Boolean(errors[field.id])}
          >
            {isNoMajorFaculty ? (
              <option value="세부전공 없음">세부전공 없음</option>
            ) : (
              <option value="">
                {!isFaculty && !selectedFaculty
                  ? "학부를 먼저 선택해주세요"
                  : `${field.label} 선택`}
              </option>
            )}
            {options.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </S.Select>
          <ChevronDown size={17} aria-hidden="true" />
        </S.SelectWrap>
        <S.InputHelper>
          {isNoMajorFaculty
            ? "선택한 학부는 별도 세부전공이 없어요."
            : FIELD_HINTS[field.id]}
        </S.InputHelper>
        {errors[field.id] && (
          <S.ErrorText id={`${field.id}-error`} role="alert">
            <AlertCircle size={13} aria-hidden="true" />
            {errors[field.id]}
          </S.ErrorText>
        )}
      </S.Field>
    );
  };

  const renderTextField = (field) => {
    const numericField = field.id === "birthYear" || field.id === "studentId";

    return (
      <S.Field key={field.id} data-field-id={field.id}>
        <S.FieldLabel htmlFor={field.id}>
          {field.label}
          {field.required && <S.Required aria-label="필수">필수</S.Required>}
        </S.FieldLabel>
        <S.TextInput
          id={field.id}
          type="text"
          inputMode={field.inputMode}
          autoComplete="off"
          value={values[field.id]}
          placeholder={field.placeholder}
          maxLength={field.id === "birthYear" ? 4 : 20}
          onChange={(event) => {
            const nextValue = numericField
              ? event.target.value.replace(/\D/g, "")
              : event.target.value;
            setFieldValue(field.id, nextValue);
          }}
          aria-invalid={Boolean(errors[field.id])}
          aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
          $hasError={Boolean(errors[field.id])}
        />
        <S.InputHelper>
          {field.id === "birthYear"
            ? "태어난 연도를 네 자리로 입력해주세요."
            : FIELD_HINTS[field.id]}
        </S.InputHelper>
        {errors[field.id] && (
          <S.ErrorText id={`${field.id}-error`} role="alert">
            <AlertCircle size={13} aria-hidden="true" />
            {errors[field.id]}
          </S.ErrorText>
        )}
      </S.Field>
    );
  };

  const renderMbtiField = (field) => (
    <S.Fieldset
      key={field.id}
      data-field-id={field.id}
      aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
    >
      <S.Legend>{renderFieldLabel(field)}</S.Legend>
      <S.MbtiGrid>
        {field.dimensions.map((dimension) => (
          <S.MbtiAxis key={dimension.id}>
            <S.MbtiAxisLabel>{dimension.label}</S.MbtiAxisLabel>
            <S.MbtiChoices>
              {dimension.options.map((option) => (
                <S.MbtiButton
                  key={option}
                  type="button"
                  $selected={values[dimension.id] === option}
                  aria-pressed={values[dimension.id] === option}
                  onClick={() => {
                    setFieldValue(dimension.id, option);
                    clearFieldError(field.id);
                  }}
                >
                  {option}
                </S.MbtiButton>
              ))}
            </S.MbtiChoices>
          </S.MbtiAxis>
        ))}
      </S.MbtiGrid>
      <S.MbtiResult aria-live="polite">
        현재 MBTI
        <strong>{mbti.length === 4 ? mbti : "— — — —"}</strong>
      </S.MbtiResult>
      {errors[field.id] && (
        <S.ErrorText id={`${field.id}-error`} role="alert">
          <AlertCircle size={13} aria-hidden="true" />
          {errors[field.id]}
        </S.ErrorText>
      )}
    </S.Fieldset>
  );

  const renderTextareaField = (field) => (
    <S.Field key={field.id} data-field-id={field.id} $wide>
      <S.FieldLabel htmlFor={field.id}>{field.label}</S.FieldLabel>
      <S.TextareaWrap>
        <S.Textarea
          id={field.id}
          value={values[field.id]}
          placeholder={field.placeholder}
          maxLength={field.maxLength}
          onChange={(event) => setFieldValue(field.id, event.target.value)}
        />
        <S.CharacterCount aria-live="polite">
          {values[field.id].length} / {field.maxLength}
        </S.CharacterCount>
      </S.TextareaWrap>
    </S.Field>
  );

  const renderField = (field) => {
    if (field.type === "singleSelect" || field.type === "multiSelect") {
      return renderChoiceField(field);
    }

    if (field.type === "facultySelect" || field.type === "majorSelect") {
      return renderSelectField(field);
    }

    if (field.type === "number" || field.type === "text") {
      return renderTextField(field);
    }

    if (field.type === "mbti") {
      return renderMbtiField(field);
    }

    if (field.type === "textarea") {
      return renderTextareaField(field);
    }

    return null;
  };

  const renderStepFields = (step) => {
    const compactTypes = new Set([
      "facultySelect",
      "majorSelect",
      "number",
      "text",
    ]);
    const fieldBlocks = [];
    let compactFields = [];

    const flushCompactFields = () => {
      if (compactFields.length === 0) return;

      fieldBlocks.push(
        <S.TextFieldGrid key={`compact-${fieldBlocks.length}`}>
          {compactFields.map(renderField)}
        </S.TextFieldGrid>,
      );
      compactFields = [];
    };

    step.fields.forEach((field) => {
      if (compactTypes.has(field.type)) {
        compactFields.push(field);
        return;
      }

      flushCompactFields();
      fieldBlocks.push(renderField(field));
    });

    flushCompactFields();
    return fieldBlocks;
  };

  const formatReviewValue = (field) => {
    if (field.type === "mbti") {
      return mbti || "선택 안 함";
    }

    const value = values[field.id];
    if (Array.isArray(value)) return value.join(", ");
    return value || "입력하지 않음";
  };

  const renderReview = () => (
    <S.ReviewBody>
      {SURVEY_STEPS.map((step, stepIndex) => (
        <S.ReviewCard key={step.id}>
          <S.ReviewCardHeader>
            <h3>{step.title}</h3>
            <S.EditButton type="button" onClick={() => editFromReview(stepIndex)}>
              수정
            </S.EditButton>
          </S.ReviewCardHeader>
          <S.ReviewList>
            {step.fields.map((field) => (
              <S.ReviewItem key={field.id}>
                <dt>{FRIENDLY_FIELD_LABELS[field.id] || field.label}</dt>
                <dd>{formatReviewValue(field)}</dd>
              </S.ReviewItem>
            ))}
          </S.ReviewList>
        </S.ReviewCard>
      ))}
    </S.ReviewBody>
  );

  const renderStepper = () => (
    <S.StepPanel aria-label="설문 진행 단계">
      <S.ProgressTop>
        <span>작성 진행률</span>
        <strong>{progress}%</strong>
      </S.ProgressTop>
      <S.ProgressTrack
        role="progressbar"
        aria-label="설문 작성 진행률"
        aria-valuemin="0"
        aria-valuemax="100"
        aria-valuenow={progress}
      >
        <S.ProgressFill $progress={progress} />
      </S.ProgressTrack>
      <S.StepList>
        {FLOW_STEPS.map((step, stepIndex) => {
          const active = currentStepIndex === stepIndex;
          const complete = stepIndex < highestVisitedStep;
          const disabled = stepIndex > highestVisitedStep;

          return (
            <li key={step.id}>
              <S.StepButton
                type="button"
                $active={active}
                disabled={disabled}
                onClick={() => goToStep(stepIndex)}
                aria-current={active ? "step" : undefined}
              >
                <S.StepNumber $active={active} $complete={complete}>
                  {complete ? (
                    <Check aria-hidden="true" />
                  ) : (
                    stepIndex + 1
                  )}
                </S.StepNumber>
                <S.StepLabel $active={active}>{step.shortTitle}</S.StepLabel>
                {active && <S.CurrentDot aria-hidden="true" />}
              </S.StepButton>
            </li>
          );
        })}
      </S.StepList>
      <S.SideNote>
        <Info size={15} aria-hidden="true" />
        <span>입력한 답변은 룸메이트 추천 점수를 계산하는 데 활용돼요.</span>
      </S.SideNote>
    </S.StepPanel>
  );

  if (submitted) {
    return (
      <S.PageShell>
        <S.SurveyGlobalStyle />
        <S.TopBar>
          <S.TopBarInner>
            <S.BrandLink href="/" aria-label="Roomie 홈으로 이동">
              <S.LogoMark>
                <Users size={20} aria-hidden="true" />
              </S.LogoMark>
              <span>Roomie</span>
            </S.BrandLink>
            <S.HeaderStatus>
              <S.SecureText>
                <ShieldCheck size={15} aria-hidden="true" />
                안전한 정보 보호
              </S.SecureText>
            </S.HeaderStatus>
          </S.TopBarInner>
        </S.TopBar>
        <S.Main>
          <S.FormCard>
            <S.CompletePanel>
              <S.CompleteIcon>
                <CheckCircle2 aria-hidden="true" />
              </S.CompleteIcon>
              <S.CompleteEyebrow>SURVEY COMPLETE</S.CompleteEyebrow>
              <S.CompleteTitle>설문이 완료됐어요!</S.CompleteTitle>
              <S.CompleteDescription>
                입력한 생활 습관과 선호 조건을 바탕으로 나와 잘 맞는 룸메이트를
                추천해드릴게요.
              </S.CompleteDescription>
              <S.CompleteSummary aria-label="설문 핵심 정보">
                <span>{values.dormitory} 생활관</span>
                <span>{values.major}</span>
                <span>{mbti}</span>
              </S.CompleteSummary>
              <S.CompleteActions>
                <S.RestartButton type="button" onClick={reopenReview}>
                  답변 다시 보기
                </S.RestartButton>
                <S.BrowseLink href="/roommates">
                  룸메이트 둘러보기
                  <ArrowRight size={15} aria-hidden="true" />
                </S.BrowseLink>
              </S.CompleteActions>
            </S.CompletePanel>
          </S.FormCard>
        </S.Main>
      </S.PageShell>
    );
  }

  return (
    <S.PageShell>
      <S.SurveyGlobalStyle />
      <S.TopBar>
        <S.TopBarInner>
          <S.BrandLink href="/" aria-label="Roomie 홈으로 이동">
            <S.LogoMark>
              <Users size={20} aria-hidden="true" />
            </S.LogoMark>
            <span>Roomie</span>
          </S.BrandLink>
          <S.HeaderStatus>
            <S.SecureText>
              <ShieldCheck size={15} aria-hidden="true" />
              안전한 정보 보호
            </S.SecureText>
            <S.ExitLink href="/">나중에 하기</S.ExitLink>
          </S.HeaderStatus>
        </S.TopBarInner>
      </S.TopBar>

      <S.Main>
        <S.Intro>
          <S.Eyebrow>ROOMMATE MATCHING</S.Eyebrow>
          <S.IntroTitle>나와 잘 맞는 룸메이트를 찾아볼까요?</S.IntroTitle>
          <S.IntroDescription>
            솔직하게 답할수록 생활 패턴이 잘 맞는 룸메이트를 추천받을 수 있어요.
            답변은 언제든 이전 단계에서 수정할 수 있습니다.
          </S.IntroDescription>
          <S.IntroMeta>
            <span>
              <Clock3 size={14} aria-hidden="true" />약 5분 소요
            </span>
            <span>
              <LockKeyhole size={14} aria-hidden="true" />매칭 목적으로만 활용
            </span>
            <span>
              <Sparkles size={14} aria-hidden="true" />맞춤 추천에 반영
            </span>
          </S.IntroMeta>
        </S.Intro>

        <S.SurveyLayout>
          {renderStepper()}

          <S.FormCard as="form" onSubmit={handleFormSubmit} noValidate>
            <S.FormHeader>
              <S.StepKicker>
                STEP {currentStepIndex + 1} OF {FLOW_STEPS.length}
              </S.StepKicker>
              <S.FormTitle ref={headingRef} tabIndex="-1">
                {currentStep.title}
              </S.FormTitle>
              <S.FormDescription>{currentStep.description}</S.FormDescription>
              <S.ScreenReaderOnly aria-live="polite">
                전체 {FLOW_STEPS.length}단계 중 {currentStepIndex + 1}단계
              </S.ScreenReaderOnly>
            </S.FormHeader>

            <S.FormBody>
              {showValidationBanner && (
                <S.ValidationBanner role="alert">
                  <AlertCircle size={17} aria-hidden="true" />
                  필수 항목을 확인해주세요. 첫 번째 미입력 항목으로 이동했습니다.
                </S.ValidationBanner>
              )}
              {isReviewStep ? renderReview() : renderStepFields(currentStep)}
            </S.FormBody>

            <S.FormFooter>
              <S.FooterNote>
                <LockKeyhole size={13} aria-hidden="true" />
                필수 항목 외에는 선택해서 입력할 수 있어요.
              </S.FooterNote>
              <S.ActionGroup>
                <S.BackButton
                  type="button"
                  onClick={moveBack}
                  disabled={currentStepIndex === 0}
                >
                  <ArrowLeft size={15} aria-hidden="true" />
                  이전
                </S.BackButton>
                <S.NextButton type="submit">
                  {isReviewStep ? "설문 완료" : "다음"}
                  {isReviewStep ? (
                    <Check size={15} aria-hidden="true" />
                  ) : (
                    <ArrowRight size={15} aria-hidden="true" />
                  )}
                </S.NextButton>
              </S.ActionGroup>
            </S.FormFooter>
          </S.FormCard>
        </S.SurveyLayout>
      </S.Main>
    </S.PageShell>
  );
}

export default SurveyPage;
