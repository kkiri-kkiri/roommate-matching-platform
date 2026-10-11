import { PREFERENCE_STEPS } from "../../data/preferenceSurvey";
import { getMyPreferences, saveMyPreferences } from "../../api/surveyApi";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  Clock3,
  Info,
  ShieldCheck,
  Sparkles,
} from "lucide-react";

import * as S from "./PreferenceSurveyPageCss";

const SURVEY_STEPS = PREFERENCE_STEPS.map((step) => ({
  ...step, shortTitle: step.title,
  description: "함께 생활하고 싶은 룸메이트의 조건을 선택해 주세요.",
}));

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
      stepErrors[field.id] = `${field.label} 항목을 선택해 주세요.`;
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
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [revision, setRevision] = useState(0);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError("");
    getMyPreferences().then((data) => {
      if (!cancelled) setValues(data.answers || createInitialValues());
    }).catch((error) => { if (!cancelled) setLoadError(error.message); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [revision]);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [errors, setErrors] = useState({});
  const [showValidation, setShowValidation] = useState(false);
  const titleRef = useRef(null);
  const stepListRef = useRef(null);
  const stepButtonRefs = useRef([]);

  const currentStep = SURVEY_STEPS[currentStepIndex];
  const answeredFieldCount = useMemo(
    () => ALL_FIELDS.filter((field) => isAnswered(field, values)).length,
    [values],
  );
  const progress = Math.round((answeredFieldCount / ALL_FIELDS.length) * 100);

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
  };

  const goToStep = (stepIndex) => {
    if (saving) return;
    if (stepIndex < 0 || stepIndex >= SURVEY_STEPS.length) return;
    setCurrentStepIndex(stepIndex);
    setErrors({});
    setShowValidation(false);
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

  const handleNext = async () => {
    if (saving || loading || loadError) return;
    const currentErrors = validateStep(currentStep, values);

    if (Object.keys(currentErrors).length > 0) {
      setErrors(currentErrors);
      setShowValidation(true);
      window.alert("아직 응답하지 않은 문항이 있어요. 표시된 문항을 확인해 주세요.");
      focusFirstError(currentErrors);
      return;
    }

    setErrors({});
    setShowValidation(false);

    if (currentStepIndex < SURVEY_STEPS.length - 1) {
      setCurrentStepIndex((stepIndex) => stepIndex + 1);
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
      window.alert("아직 응답하지 않은 문항이 있어요. 표시된 문항을 확인해 주세요.");
      focusAndScrollTitle(firstFieldId);
      return;
    }

    setSaving(true);
    try {
      await saveMyPreferences({ ...values, weights: Object.fromEntries(
        ALL_FIELDS.map((field) => [field.id, values.weights?.[field.id] ?? 3]),
      ) });
    } catch (error) {
      window.alert(error.message);
      return;
    } finally {
      setSaving(false);
    }
    window.alert("선호 설문을 저장했어요. 추천 프로필에서 결과를 확인해 주세요.");
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
            <S.LabelText>{field.label}</S.LabelText>
            <S.FieldHint id={hintId}>
              {isMultiple ? "복수 선택 가능" : "하나만 선택"}
            </S.FieldHint>
          </S.LabelRow>
        </S.Legend>

        {isMultiple && (
          <S.SelectionGuide>
            <Check size={14} aria-hidden="true" />
            <span>
              여러 항목을 선택할 수 있어요. ‘상관없음’은
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
      </S.Fieldset>
    );
  };

  if (loading) return <S.Page><p role="status">선호 설문을 불러오는 중이에요.</p></S.Page>;
  if (loadError) return <S.Page><p role="alert">{loadError}</p><button onClick={() => setRevision((value) => value + 1)}>다시 시도</button></S.Page>;

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
              <ShieldCheck size={15} aria-hidden="true" /> 총 {ALL_FIELDS.length}개 문항
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
            <fieldset disabled={saving} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
            <S.FormHeader>
              <S.StepKicker>
                STEP {currentStepIndex + 1} OF {SURVEY_STEPS.length}
              </S.StepKicker>
              <S.FormTitle ref={titleRef} tabIndex="-1">
                {currentStep.title}
              </S.FormTitle>
              <S.FormDescription>{currentStep.description}</S.FormDescription>
            </S.FormHeader>

            <S.FormBody>
              {currentStep.fields.map(renderChoiceField)}
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
            </fieldset>
          </S.FormCard>
        </S.SurveyLayout>
      </S.Content>
    </S.Page>
  );
}

export default RoommatePreferenceSurvey;
