import { useRef, useState } from "react";
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

import * as S from "./ProfileSurveyPageCss";

const SURVEY_STEPS = [
  {
    id: "lifePattern",
    shortTitle: "수면 패턴",
    title: "수면 패턴",
    description: "서로 편안한 생활을 위해 수면과 방 사용 습관을 알려주세요.",
    fields: [
      {
        id: "sleepTime",
        label: "취침시간",
        type: "single",
        options: [
          "22시 이전",
          "23시 ~ 24시",
          "24시 ~ 01시",
          "01시 ~ 02시",
          "02시 ~ 03시",
          "03시 이후",
        ],
        required: true,
      },
      {
        id: "wakeTime",
        label: "기상시간",
        type: "single",
        options: [
          "06시 이전",
          "07시 ~ 08시",
          "08시 ~ 09시",
          "09시 ~ 10시",
          "10시 이후",
          "수업 시간에 따라 유동적",
        ],
        required: true,
      },
      {
        id: "sleepTalking",
        label: "잠꼬대",
        type: "single",
        options: ["없음", "적음", "보통", "심함"],
        required: true,
      },
      {
        id: "snoring",
        label: "코골이",
        type: "single",
        options: ["없음", "적음", "보통", "심함"],
        required: true,
      },
      {
        id: "teethGrinding",
        label: "이갈이",
        type: "single",
        options: ["없음", "적음", "보통", "심함"],
        required: true,
      },
      {
        id: "sleepSensitivity",
        label: "잠귀",
        type: "single",
        options: ["밝음 (소리에 잘 깨요)", "보통", "어두움 (잘 깨지 않아요)"],
        required: true,
      },
    ],
  },
  {
    id: "livingHabits",
    shortTitle: "생활 습관",
    title: "생활 습관",
    description: "공용 공간을 함께 사용하는 나의 생활 방식을 선택해주세요.",
    fields: [
      {
        id: "indoorEating",
        label: "실내 취식",
        type: "single",
        options: ["가능", "불가능", "냄새 안나는 음식 가능"],
        required: true,
      },
      {
        id: "smoking",
        label: "흡연 여부",
        type: "single",
        options: ["비흡연", "일반담배", "전자담배"],
        required: true,
      },
      {
        id: "lightingPreference",
        label: "조명 환경",
        type: "single",
        options: ["밝음 선호", "어두움 선호", "상관 없음"],
        required: true,
      },
      {
        id: "phoneCall",
        label: "전화",
        type: "single",
        options: ["항상 밖에서", "방에서는 짧게만", "상관없음"],
        required: true,
      },
      {
        id: "mediaSound",
        label: "소리(음악, OTT 등)",
        type: "single",
        options: [
          "항상 이어폰 사용해주세요",
          "공부·과제할 때 아니면 상관없음",
          "소리만 작게 해주세요",
          "자유롭게 하세요",
        ],
        required: true,
      },
      {
        id: "showerTime",
        label: "샤워 시간대",
        type: "single",
        options: ["아침", "저녁", "유동적"],
        required: true,
      },
      {
        id: "showerDuration",
        label: "샤워 소요시간",
        type: "single",
        options: ["10분", "20분", "30분", "40분", "그 이상"],
        required: true,
      },
    ],
  },
  {
    id: "dormLife",
    shortTitle: "청소 및 공동생활",
    title: "청소 및 공동생활",
    description: "룸메이트와 공유하게 될 생활 방식에 대해 알려주세요.",
    fields: [
      {
        id: "cleaningFrequency",
        label: "청소",
        type: "single",
        options: [
          "안함",
          "주 1회",
          "주 2회",
          "주 3회",
          "주 4회",
          "주 5회",
          "매일",
          "유동적",
        ],
        required: true,
      },
      {
        id: "cleaningStyle",
        label: "청소 방식",
        type: "single",
        options: ["같이 청소", "각자 영역만 청소", "상황에 따라 협의"],
        required: true,
      },
      {
        id: "roommateInteraction",
        label: "룸메이트와의 교류 정도",
        type: "single",
        options: ["친하게 지내고 싶음", "적당히 대화", "필요한 대화만"],
        required: true,
      },
      {
        id: "mealPlan",
        label: "신청한 기숙사 식사",
        type: "multi",
        options: ["조식", "중식", "석식", "신청 안 함"],
        exclusiveOptions: ["신청 안 함"],
        required: true,
      },
      {
        id: "dormStayDays",
        label: "기숙사 상주 주기",
        type: "multi",
        options: ["월", "화", "수", "목", "금", "토", "일"],
        allShortcut: true,
        required: true,
      },
      {
        id: "temperaturePreference",
        label: "온도 선호",
        type: "single",
        options: ["추위 타요", "더위 타요", "보통"],
        required: true,
      },
      {
        id: "bugHandling",
        label: "벌레",
        type: "single",
        options: ["잘 잡음", "못 잡음", "작은 건 잡음"],
        required: true,
      },
      {
        id: "scentProductUse",
        label: "향수·디퓨저 등 향 제품 사용·허용",
        type: "single",
        options: ["자주 사용", "가끔 사용", "사용 안 함", "상관없음"],
        required: true,
      },
      {
        id: "desktopUse",
        label: "데스크탑 사용·허용",
        type: "single",
        options: ["사용해요", "사용하지 않아요", "특정 시간 제외하면 상관없음"],
        required: true,
      },
      {
        id: "sharedItems",
        label: "공용물품 사용",
        type: "single",
        options: ["적극적으로 공유", "일부만 공유", "각자 사용"],
        required: true,
      },
    ],
  },
];

const FIELD_HINTS = {
  dormStayDays: "복수 선택 가능",
  mealPlan: "복수 선택 가능",
};

function createInitialValues() {
  return SURVEY_STEPS.flatMap((step) => step.fields).reduce((values, field) => {
    values[field.id] = field.type === "multi" ? [] : "";
    return values;
  }, {});
}

function isEmpty(value) {
  return Array.isArray(value)
    ? value.length === 0
    : !String(value || "").trim();
}

function validateStep(step, values) {
  const nextErrors = {};

  step.fields.forEach((field) => {
    if (field.required && isEmpty(values[field.id])) {
      nextErrors[field.id] = `${field.label} 항목을 선택하거나 입력해주세요.`;
    }
  });

  return nextErrors;
}

const isFieldAnswered = (field, values) => !isEmpty(values[field.id]);

function getChoiceColumns(field) {
  const longestOption = Math.max(
    ...field.options.map((option) => option.length),
  );
  if (longestOption >= 16) return 2;
  if (field.options.length <= 4) return field.options.length;
  if (field.options.length <= 7 && longestOption <= 5)
    return field.options.length;
  return 4;
}

function SurveyPage() {
  const [values, setValues] = useState(createInitialValues);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [errors, setErrors] = useState({});
  const titleRef = useRef(null);

  const currentStep = SURVEY_STEPS[currentStepIndex];
  const allFields = SURVEY_STEPS.flatMap((step) => step.fields);
  const answeredFieldCount = allFields.filter((field) =>
    isFieldAnswered(field, values),
  ).length;
  const progress = Math.round((answeredFieldCount / allFields.length) * 100);

  const clearError = (fieldId) => {
    setErrors((currentErrors) => {
      if (!currentErrors[fieldId]) return currentErrors;
      const nextErrors = { ...currentErrors };
      delete nextErrors[fieldId];
      return nextErrors;
    });
  };

  const setFieldValue = (fieldId, value) => {
    setValues((currentValues) => ({ ...currentValues, [fieldId]: value }));
    clearError(fieldId);
  };

  const toggleMultiValue = (field, option) => {
    const selectedValues = Array.isArray(values[field.id])
      ? values[field.id]
      : [];
    const exclusiveOptions = field.exclusiveOptions || [];
    let nextValues;

    if (exclusiveOptions.includes(option)) {
      nextValues = selectedValues.includes(option) ? [] : [option];
    } else {
      const regularValues = selectedValues.filter(
        (value) => !exclusiveOptions.includes(value),
      );
      nextValues = regularValues.includes(option)
        ? regularValues.filter((value) => value !== option)
        : [...regularValues, option];
    }
    setFieldValue(field.id, nextValues);
  };

  const toggleEveryDay = (field) => {
    const selectedValues = values[field.id];
    const everyDaySelected = field.options.every((option) =>
      selectedValues.includes(option),
    );
    setFieldValue(field.id, everyDaySelected ? [] : [...field.options]);
  };

  const focusTitle = () => {
    window.requestAnimationFrame(() =>
      titleRef.current?.focus({ preventScroll: true }),
    );
  };

  const goToStep = (stepIndex) => {
    setCurrentStepIndex(stepIndex);
    setErrors({});
    focusTitle();
  };

  const focusFirstError = (nextErrors) => {
    const firstFieldId = Object.keys(nextErrors)[0];
    if (!firstFieldId) return;
    window.requestAnimationFrame(() => {
      const control = document.querySelector(
        `[data-field-id="${firstFieldId}"] input, [data-field-id="${firstFieldId}"] select, [data-field-id="${firstFieldId}"] button`,
      );
      control?.focus();
    });
  };

  const moveNext = () => {
    const nextErrors = validateStep(currentStep, values);
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      window.alert("아직 입력하지 않은 항목이 있어요. 표시된 항목을 확인해 주세요.");
      focusFirstError(nextErrors);
      return;
    }

    setErrors({});

    if (currentStepIndex < SURVEY_STEPS.length - 1) {
      setCurrentStepIndex((index) => index + 1);
      focusTitle();
      return;
    }

    for (let stepIndex = 0; stepIndex < SURVEY_STEPS.length; stepIndex += 1) {
      const sectionErrors = validateStep(SURVEY_STEPS[stepIndex], values);
      if (Object.keys(sectionErrors).length > 0) {
        setCurrentStepIndex(stepIndex);
        setErrors(sectionErrors);
        window.alert("아직 입력하지 않은 항목이 있어요. 표시된 항목을 확인해 주세요.");
        focusFirstError(sectionErrors);
        return;
      }
    }

    window.alert("모든 답변을 확인했어요. 현재 화면은 UI 확인용이며 실제 저장은 진행되지 않습니다.");
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    moveNext();
  };

  const renderLabel = (field) => (
    <S.LabelRow>
      <S.LabelText>{field.label}</S.LabelText>
      {FIELD_HINTS[field.id] && (
        <S.FieldHint>{FIELD_HINTS[field.id]}</S.FieldHint>
      )}
    </S.LabelRow>
  );

  const renderError = (field) =>
    errors[field.id] ? (
      <S.ErrorText id={`${field.id}-error`} role="alert">
        <AlertCircle size={14} aria-hidden="true" />
        {errors[field.id]}
      </S.ErrorText>
    ) : null;

  const renderChoiceField = (field) => {
    const multiple = field.type === "multi";
    const selectedValues = multiple ? values[field.id] : [];
    const everyDaySelected = field.allShortcut
      ? field.options.every((option) => selectedValues.includes(option))
      : false;

    return (
      <S.Fieldset
        key={field.id}
        data-field-id={field.id}
        aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
      >
        <S.Legend>{renderLabel(field)}</S.Legend>
        {field.allShortcut && (
          <S.QuickSelectRow>
            <S.QuickSelectButton
              type="button"
              aria-pressed={everyDaySelected}
              onClick={() => toggleEveryDay(field)}
            >
              <Check size={14} aria-hidden="true" />
              {everyDaySelected ? "매일 선택 해제" : "매일 상주"}
            </S.QuickSelectButton>
          </S.QuickSelectRow>
        )}
        <S.ChoiceGrid $columns={getChoiceColumns(field)}>
          {field.options.map((option, optionIndex) => {
            const selected = multiple
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
                  type={multiple ? "checkbox" : "radio"}
                  name={field.id}
                  value={option}
                  checked={selected}
                  onChange={() =>
                    multiple
                      ? toggleMultiValue(field, option)
                      : setFieldValue(field.id, option)
                  }
                />
                <S.ChoiceIndicator $selected={selected} $multiple={multiple}>
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

  return (
    <S.Page>
      <S.SurveyPageReset />
      <S.Content>
        <S.Intro>
          <S.Eyebrow>
            <Sparkles size={15} aria-hidden="true" /> ROOMMATE PROFILE
          </S.Eyebrow>
          <S.PageTitle>나와 잘 맞는 룸메이트를 찾아볼까요?</S.PageTitle>
          <S.PageDescription>
            생활 습관을 솔직하게 알려주면 서로 더 편안하게 지낼 수 있는
            룸메이트를 찾는 데 도움이 돼요.
          </S.PageDescription>
          <S.IntroMeta>
            <span>
              <Clock3 size={15} aria-hidden="true" /> 약 3분 소요
            </span>
            <span>
              <ShieldCheck size={15} aria-hidden="true" /> 매칭 목적으로만 활용
            </span>
          </S.IntroMeta>
        </S.Intro>

        <S.SurveyLayout>
          <S.StepPanel aria-label="설문 진행 단계">
            <S.ProgressHeader>
              <span>작성 진행률</span>
              <strong>{progress}%</strong>
            </S.ProgressHeader>
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
              {SURVEY_STEPS.map((step, stepIndex) => {
                const active = currentStepIndex === stepIndex;
                const answeredCount = step.fields.filter((field) =>
                  isFieldAnswered(field, values),
                ).length;
                const complete =
                  Object.keys(validateStep(step, values)).length === 0;
                return (
                  <li key={step.id}>
                    <S.StepButton
                      type="button"
                      $active={active}
                      onClick={() => goToStep(stepIndex)}
                      aria-current={active ? "step" : undefined}
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
                          {answeredCount} / {step.fields.length} 입력
                        </span>
                      </S.StepCopy>
                    </S.StepButton>
                  </li>
                );
              })}
            </S.StepList>
            <S.SideNote>
              <Info size={17} aria-hidden="true" />
              <span>단계를 눌러 언제든 다른 항목으로 이동할 수 있어요.</span>
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
                <ArrowLeft size={17} aria-hidden="true" /> 이전
              </S.BackButton>
              <S.NextButton type="submit">
                {currentStepIndex === SURVEY_STEPS.length - 1
                  ? "입력 완료"
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

export default SurveyPage;
