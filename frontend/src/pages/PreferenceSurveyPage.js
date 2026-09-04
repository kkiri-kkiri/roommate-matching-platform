import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  Clock3,
  Info,
  ShieldCheck,
  Sparkles,
} from "lucide-react";

import * as S from "./PreferenceSurveyPageCss";

const SURVEY_STEPS = [
  {
    id: "basic",
    shortTitle: "기본·학적",
    title: "기본·학적 정보",
    description: "함께 생활하고 싶은 룸메이트의 기본 조건을 선택해 주세요.",
    fields: [
      {
        id: "department",
        label: "학과·학부",
        type: "single",
        options: [
          "같은 전공",
          "같은 학부의 다른 전공",
          "다른 학부",
          "상관없음",
        ],
      },
      {
        id: "age",
        label: "나이",
        type: "multi",
        options: ["연하", "동갑", "연상", "나이 상관없음"],
        exclusiveOptions: ["나이 상관없음"],
      },
      {
        id: "grade",
        label: "학년",
        type: "multi",
        options: [
          "1학년",
          "2학년",
          "3학년",
          "4학년",
          "5학년",
          "6학년 이상",
          "학년 상관없음",
        ],
        exclusiveOptions: ["학년 상관없음"],
      },
    ],
  },
  {
    id: "sleep",
    shortTitle: "수면·생활",
    title: "수면·생활 패턴",
    description: "서로 편안하게 쉴 수 있도록 선호하는 수면 습관을 알려주세요.",
    fields: [
      {
        id: "bedtime",
        label: "선호하는 취침시간",
        type: "multi",
        options: [
          "22시 이전",
          "23시",
          "자정",
          "1시",
          "2시",
          "3시 이후",
          "상관없음",
        ],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "wakeTime",
        label: "선호하는 기상시간",
        type: "multi",
        options: [
          "6시 이전",
          "6시 ~ 7시",
          "7시 ~ 8시",
          "8시 ~ 9시",
          "9시",
          "10시 이후",
          "수업 시간에 따라 유동적",
          "상관없음",
        ],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "sleepTalking",
        label: "잠꼬대 허용 정도",
        type: "single",
        options: [
          "없음만 가능",
          "적음까지 괜찮음",
          "보통까지 괜찮음",
          "상관없음",
        ],
      },
      {
        id: "snoring",
        label: "코골이 허용 정도",
        type: "single",
        options: [
          "없음만 가능",
          "적음까지 괜찮음",
          "보통까지 괜찮음",
          "상관없음",
        ],
      },
      {
        id: "teethGrinding",
        label: "이갈이 허용 정도",
        type: "single",
        options: [
          "없음만 가능",
          "적음까지 괜찮음",
          "보통까지 괜찮음",
          "상관없음",
        ],
      },
    ],
  },
  {
    id: "habits",
    shortTitle: "청소·습관",
    title: "청소·생활 습관",
    description:
      "공용 공간을 함께 관리할 룸메이트의 생활 습관을 선택해 주세요.",
    fields: [
      {
        id: "cleaningFrequency",
        label: "선호하는 청소 주기",
        type: "multi",
        options: [
          "안함",
          "주 1회",
          "주 2회",
          "주 3회",
          "주 4회",
          "주 5회",
          "매일",
          "유동적",
          "상관없음",
        ],
        exclusiveOptions: ["상관없음"],
      },
      {
        id: "cleaningStyle",
        label: "청소 방식",
        type: "single",
        options: [
          "같이 청소하는 사람",
          "각자 영역만 청소하는 사람",
          "상황에 따라 협의하는 사람",
          "상관없음",
        ],
      },
      {
        id: "smoking",
        label: "흡연 여부",
        type: "single",
        options: ["비흡연자만", "전자담배까지 허용", "흡연 여부 상관없음"],
      },
      {
        id: "temperature",
        label: "온도 선호",
        type: "single",
        options: [
          "추위를 타는 사람",
          "더위를 타는 사람",
          "보통인 사람",
          "상관없음",
        ],
      },
    ],
  },
  {
    id: "dormitory",
    shortTitle: "기숙사 생활",
    title: "기숙사 생활",
    description: "룸메이트와 어느 정도 일상을 나누고 싶은지 알려주세요.",
    fields: [
      {
        id: "interaction",
        label: "룸메이트와의 교류 정도",
        type: "single",
        options: [
          "친하게 지내고 싶은 사람",
          "적당히 대화하고 싶은 사람",
          "필요한 대화만 하고 싶은 사람",
          "상관없음",
        ],
      },
      {
        id: "meal",
        label: "기숙사 식사",
        type: "single",
        options: [
          "같이 먹고 싶음",
          "각자 먹고 싶음",
          "시간 될 때만 같이 먹고 싶음",
          "상관없음",
        ],
      },
      {
        id: "sharedItems",
        label: "공용물품 사용",
        type: "single",
        options: [
          "적극적으로 공유하는 사람",
          "일부만 공유하는 사람",
          "각자 사용하는 사람",
          "상관없음",
        ],
      },
    ],
  },
  {
    id: "requirements",
    shortTitle: "필수조건",
    title: "필수조건 설정",
    description:
      "매칭할 때 반드시 만족해야 하는 조건을 마지막으로 확인해 주세요.",
    fields: [
      {
        id: "requiredConditions",
        label: "반드시 만족해야 하는 조건",
        type: "multi",
        options: [
          "학과·학부",
          "나이",
          "학년",
          "취침시간",
          "기상시간",
          "잠꼬대",
          "코골이",
          "이갈이",
          "청소 주기",
          "청소 방식",
          "흡연 여부",
          "온도 선호",
          "룸메이트와의 교류 정도",
          "기숙사 식사",
          "공용물품 사용",
          "필수조건 없음",
        ],
        exclusiveOptions: ["필수조건 없음"],
      },
    ],
  },
];

const ALL_FIELDS = SURVEY_STEPS.flatMap((step) => step.fields);

function createInitialValues() {
  return ALL_FIELDS.reduce((initialValues, field) => {
    initialValues[field.id] = field.type === "multi" ? [] : "";
    return initialValues;
  }, {});
}

function isAnswered(field, values) {
  const value = values[field.id];
  return Array.isArray(value) ? value.length > 0 : Boolean(value);
}

function validateStep(step, values) {
  return step.fields.reduce((stepErrors, field) => {
    if (!isAnswered(field, values)) {
      stepErrors[field.id] =
        field.id === "requiredConditions"
          ? "필수조건을 선택하거나 ‘필수조건 없음’을 선택해 주세요."
          : `${field.label} 항목을 선택해 주세요.`;
    }
    return stepErrors;
  }, {});
}

function getChoiceColumns(field) {
  const longestOption = Math.max(
    ...field.options.map((option) => option.length),
  );

  if (field.options.length > 12) return 4;
  if (longestOption >= 13) return 2;
  if (field.options.length <= 4) return field.options.length;
  return Math.min(field.options.length, 4);
}

function prefersReducedMotion() {
  return (
    typeof window !== "undefined" &&
    typeof window.matchMedia === "function" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

function RoommatePreferenceSurvey() {
  const [values, setValues] = useState(createInitialValues);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [errors, setErrors] = useState({});
  const [showValidation, setShowValidation] = useState(false);
  const [showComplete, setShowComplete] = useState(false);
  const titleRef = useRef(null);
  const completeRef = useRef(null);
  const stepListRef = useRef(null);
  const stepButtonRefs = useRef([]);

  const currentStep = SURVEY_STEPS[currentStepIndex];
  const answeredFieldCount = useMemo(
    () => ALL_FIELDS.filter((field) => isAnswered(field, values)).length,
    [values],
  );
  const progress = Math.round((answeredFieldCount / ALL_FIELDS.length) * 100);
  const selectedRequirements = values.requiredConditions;
  const hasNoRequiredCondition = selectedRequirements.includes("필수조건 없음");

  useEffect(() => {
    if (!showValidation) return;
    setErrors(validateStep(currentStep, values));
  }, [currentStep, showValidation, values]);

  useEffect(() => {
    if (
      typeof window === "undefined" ||
      typeof window.matchMedia !== "function" ||
      !window.matchMedia("(max-width: 820px)").matches
    ) {
      return;
    }

    const stepList = stepListRef.current;
    const activeButton = stepButtonRefs.current[currentStepIndex];
    if (!stepList || !activeButton) return;

    const stepListRect = stepList.getBoundingClientRect();
    const activeButtonRect = activeButton.getBoundingClientRect();
    const targetLeft =
      stepList.scrollLeft +
      activeButtonRect.left -
      stepListRect.left -
      (stepListRect.width - activeButtonRect.width) / 2;
    stepList.scrollTo?.({
      left: Math.max(0, targetLeft),
      behavior: prefersReducedMotion() ? "auto" : "smooth",
    });
  }, [currentStepIndex]);

  const focusAndScrollTitle = (fieldId) => {
    window.requestAnimationFrame(() => {
      const title = titleRef.current;
      title?.scrollIntoView?.({
        behavior: prefersReducedMotion() ? "auto" : "smooth",
        block: "start",
      });
      title?.focus({ preventScroll: true });

      if (!fieldId) return;
      window.requestAnimationFrame(() => {
        const firstInput = document.querySelector(
          `[data-field-id="${fieldId}"] input`,
        );
        firstInput?.focus();
      });
    });
  };

  const selectSingleValue = (fieldId, option) => {
    setValues((currentValues) => ({
      ...currentValues,
      [fieldId]: option,
    }));
    setShowComplete(false);
  };

  const toggleMultiValue = (field, option) => {
    setValues((currentValues) => {
      const selectedValues = Array.isArray(currentValues[field.id])
        ? currentValues[field.id]
        : [];
      const exclusiveOptions = field.exclusiveOptions || [];
      let nextValues;

      if (selectedValues.includes(option)) {
        nextValues = selectedValues.filter(
          (selectedOption) => selectedOption !== option,
        );
      } else if (exclusiveOptions.includes(option)) {
        nextValues = [option];
      } else {
        nextValues = [
          ...selectedValues.filter(
            (selectedOption) => !exclusiveOptions.includes(selectedOption),
          ),
          option,
        ];
      }

      return {
        ...currentValues,
        [field.id]: nextValues,
      };
    });
    setShowComplete(false);
  };

  const goToStep = (stepIndex) => {
    if (stepIndex < 0 || stepIndex >= SURVEY_STEPS.length) return;
    setCurrentStepIndex(stepIndex);
    setErrors({});
    setShowValidation(false);
    setShowComplete(false);
    focusAndScrollTitle();
  };

  const focusFirstError = (nextErrors) => {
    const firstFieldId = Object.keys(nextErrors)[0];
    if (!firstFieldId) return;

    window.requestAnimationFrame(() => {
      document
        .querySelector(`[data-field-id="${firstFieldId}"] input`)
        ?.focus();
    });
  };

  const handleNext = () => {
    const currentErrors = validateStep(currentStep, values);

    if (Object.keys(currentErrors).length > 0) {
      setErrors(currentErrors);
      setShowValidation(true);
      setShowComplete(false);
      focusFirstError(currentErrors);
      return;
    }

    setErrors({});
    setShowValidation(false);

    if (currentStepIndex < SURVEY_STEPS.length - 1) {
      setCurrentStepIndex((stepIndex) => stepIndex + 1);
      setShowComplete(false);
      focusAndScrollTitle();
      return;
    }

    const firstInvalidStep = SURVEY_STEPS.map((step, stepIndex) => ({
      stepIndex,
      stepErrors: validateStep(step, values),
    })).find(({ stepErrors }) => Object.keys(stepErrors).length > 0);

    if (firstInvalidStep) {
      const firstFieldId = Object.keys(firstInvalidStep.stepErrors)[0];
      setCurrentStepIndex(firstInvalidStep.stepIndex);
      setErrors(firstInvalidStep.stepErrors);
      setShowValidation(true);
      setShowComplete(false);
      focusAndScrollTitle(firstFieldId);
      return;
    }

    setShowComplete(true);
    window.requestAnimationFrame(() => {
      completeRef.current?.scrollIntoView?.({
        behavior: prefersReducedMotion() ? "auto" : "smooth",
        block: "nearest",
      });
      completeRef.current?.focus({ preventScroll: true });
    });
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    handleNext();
  };

  const renderError = (field) =>
    errors[field.id] ? (
      <S.ErrorText id={`${field.id}-error`}>
        <AlertCircle size={14} aria-hidden="true" />
        {errors[field.id]}
      </S.ErrorText>
    ) : null;

  const renderChoiceField = (field) => {
    const isMultiple = field.type === "multi";
    const selectedValues = isMultiple ? values[field.id] : [];
    const hasError = Boolean(errors[field.id]);
    const hintId = `${field.id}-hint`;
    const describedBy = hasError ? `${hintId} ${field.id}-error` : hintId;

    return (
      <S.Fieldset
        key={field.id}
        data-field-id={field.id}
        aria-invalid={hasError}
        aria-describedby={describedBy}
      >
        <S.Legend>
          <S.LabelRow>
            <S.LabelText>
              {field.label}
              <S.RequiredBadge>필수</S.RequiredBadge>
            </S.LabelText>
            <S.FieldHint id={hintId}>
              {isMultiple ? "복수 선택 가능" : "하나만 선택"}
            </S.FieldHint>
          </S.LabelRow>
        </S.Legend>

        {isMultiple && (
          <S.SelectionGuide>
            <Check size={14} aria-hidden="true" />
            <span>
              여러 항목을 선택할 수 있어요. ‘상관없음’ 또는 ‘필수조건 없음’은
              다른 선택과 함께 적용되지 않습니다.
            </span>
          </S.SelectionGuide>
        )}

        <S.ChoiceGrid $columns={getChoiceColumns(field)}>
          {field.options.map((option, optionIndex) => {
            const selected = isMultiple
              ? selectedValues.includes(option)
              : values[field.id] === option;
            const inputId = `${field.id}-${optionIndex}`;

            return (
              <S.ChoiceLabel
                key={option}
                htmlFor={inputId}
                $selected={selected}
              >
                <S.HiddenInput
                  id={inputId}
                  type={isMultiple ? "checkbox" : "radio"}
                  name={field.id}
                  value={option}
                  checked={selected}
                  aria-invalid={hasError}
                  aria-describedby={describedBy}
                  onChange={() => {
                    if (isMultiple) {
                      toggleMultiValue(field, option);
                    } else {
                      selectSingleValue(field.id, option);
                    }
                  }}
                />
                <S.ChoiceIndicator $selected={selected} $multiple={isMultiple}>
                  {selected && <Check size={13} aria-hidden="true" />}
                </S.ChoiceIndicator>
                <span>{option}</span>
              </S.ChoiceLabel>
            );
          })}
        </S.ChoiceGrid>

        {renderError(field)}

        {field.id === "requiredConditions" && (
          <S.RequirementSummary aria-live="polite">
            <S.SummaryHeader>
              <strong>선택한 필수조건</strong>
              <span>
                {hasNoRequiredCondition ? 0 : selectedRequirements.length}개
                선택
              </span>
            </S.SummaryHeader>
            {selectedRequirements.length === 0 ? (
              <S.SummaryEmpty>아직 선택한 필수조건이 없습니다.</S.SummaryEmpty>
            ) : hasNoRequiredCondition ? (
              <S.SummaryEmpty>
                필수조건 없이 전체 선호도를 기준으로 매칭해요.
              </S.SummaryEmpty>
            ) : (
              <S.SummaryChips aria-label="선택한 필수조건 목록">
                {selectedRequirements.map((requirement) => (
                  <S.SummaryChip key={requirement}>{requirement}</S.SummaryChip>
                ))}
              </S.SummaryChips>
            )}
          </S.RequirementSummary>
        )}
      </S.Fieldset>
    );
  };

  return (
    <S.Page>
      <S.PageReset />
      <S.Content>
        <S.Intro>
          <S.Eyebrow>
            <Sparkles size={15} aria-hidden="true" />
            ROOMMATE PREFERENCE
          </S.Eyebrow>
          <S.PageTitle>내가 원하는 룸메이트</S.PageTitle>
          <S.PageDescription>
            함께 지낼 룸메이트에게 바라는 생활 기준을 알려주세요. 응답은 더 잘
            맞는 룸메이트를 찾는 데 활용됩니다.
          </S.PageDescription>
          <S.IntroMeta>
            <span>
              <Clock3 size={15} aria-hidden="true" /> 약 5분 소요
            </span>
            <span>
              <ShieldCheck size={15} aria-hidden="true" /> 총 20개 문항
            </span>
          </S.IntroMeta>
        </S.Intro>

        <S.SurveyLayout>
          <S.StepPanel aria-label="설문 진행 단계">
            <S.ProgressHeader>
              <span>응답 진행률</span>
              <strong>{progress}%</strong>
            </S.ProgressHeader>
            <S.ProgressTrack
              role="progressbar"
              aria-label="설문 응답 진행률"
              aria-valuemin="0"
              aria-valuemax="100"
              aria-valuenow={progress}
            >
              <S.ProgressFill $progress={progress} />
            </S.ProgressTrack>

            <S.StepList ref={stepListRef}>
              {SURVEY_STEPS.map((step, stepIndex) => {
                const active = stepIndex === currentStepIndex;
                const answeredCount = step.fields.filter((field) =>
                  isAnswered(field, values),
                ).length;
                const complete = answeredCount === step.fields.length;

                return (
                  <li key={step.id}>
                    <S.StepButton
                      ref={(button) => {
                        stepButtonRefs.current[stepIndex] = button;
                      }}
                      type="button"
                      $active={active}
                      aria-current={active ? "step" : undefined}
                      aria-label={`${stepIndex + 1}단계 ${step.title}, ${answeredCount}/${step.fields.length} 응답`}
                      onClick={() => goToStep(stepIndex)}
                    >
                      <S.StepMarker $active={active} $complete={complete}>
                        {complete ? (
                          <Check size={15} aria-hidden="true" />
                        ) : (
                          stepIndex + 1
                        )}
                      </S.StepMarker>
                      <S.StepCopy>
                        <strong>{step.shortTitle}</strong>
                        <span>
                          {answeredCount} / {step.fields.length} 응답
                        </span>
                      </S.StepCopy>
                    </S.StepButton>
                  </li>
                );
              })}
            </S.StepList>

            <S.SideNote>
              <Info size={17} aria-hidden="true" />
              <span>단계를 눌러 언제든 원하는 항목으로 이동할 수 있어요.</span>
            </S.SideNote>
          </S.StepPanel>

          <S.FormCard as="form" onSubmit={handleSubmit} noValidate>
            <S.FormHeader>
              <S.StepKicker>
                STEP {currentStepIndex + 1} OF {SURVEY_STEPS.length}
              </S.StepKicker>
              <S.FormTitle ref={titleRef} tabIndex="-1">
                {currentStep.title}
              </S.FormTitle>
              <S.FormDescription>{currentStep.description}</S.FormDescription>
              <S.RequiredNotice>
                <span>필수</span> 모든 문항에 응답해 주세요.
              </S.RequiredNotice>
            </S.FormHeader>

            <S.FormBody>
              {showValidation && Object.keys(errors).length > 0 && (
                <S.ValidationBanner role="alert">
                  <AlertCircle size={18} aria-hidden="true" />
                  <div>
                    <strong>아직 응답하지 않은 문항이 있어요.</strong>
                    <span>표시된 필수 문항을 확인해 주세요.</span>
                  </div>
                </S.ValidationBanner>
              )}

              {currentStep.id === "requirements" && (
                <S.ConditionNotice>
                  <Info size={17} aria-hidden="true" />
                  <span>
                    아래에서 선택한 항목은 단순 점수 계산이 아니라 필수조건으로
                    적용합니다.
                  </span>
                </S.ConditionNotice>
              )}

              {currentStep.fields.map(renderChoiceField)}

              {showComplete && (
                <S.CompleteBanner ref={completeRef} tabIndex="-1">
                  <CheckCircle2 size={21} aria-hidden="true" />
                  <div>
                    <strong>원하는 룸메이트 조건 입력을 완료했어요.</strong>
                    <span>
                      현재 화면은 UI 확인용이며 실제 데이터는 제출되지 않습니다.
                    </span>
                  </div>
                </S.CompleteBanner>
              )}
            </S.FormBody>

            <S.FormFooter>
              <S.BackButton
                type="button"
                disabled={currentStepIndex === 0}
                onClick={() => goToStep(currentStepIndex - 1)}
              >
                <ArrowLeft size={17} aria-hidden="true" />
                이전
              </S.BackButton>
              <S.NextButton type="submit">
                {currentStepIndex === SURVEY_STEPS.length - 1
                  ? "설문 완료"
                  : "다음"}
                {currentStepIndex === SURVEY_STEPS.length - 1 ? (
                  <Check size={17} aria-hidden="true" />
                ) : (
                  <ArrowRight size={17} aria-hidden="true" />
                )}
              </S.NextButton>
            </S.FormFooter>
          </S.FormCard>
        </S.SurveyLayout>
      </S.Content>
    </S.Page>
  );
}

export default RoommatePreferenceSurvey;
