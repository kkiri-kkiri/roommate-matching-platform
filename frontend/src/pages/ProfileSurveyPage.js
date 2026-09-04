import { useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Info,
  ShieldCheck,
  Sparkles,
} from "lucide-react";

import * as S from "./ProfileSurveyPageCss";

const FACULTY_OPTIONS = [
  {
    name: "인문융합공공인재학부",
    majors: [
      "문예창작미디어콘텐츠홍보전공",
      "영미언어문화전공",
      "행정학전공",
      "노동복지전공",
    ],
  },
  { name: "법경영학부", majors: ["법학전공", "경영학전공"] },
  {
    name: "웰니스산업융합학부",
    majors: ["의류산업학전공", "복지상담학전공", "식품영양학전공"],
  },
  {
    name: "식물자원조경학부",
    majors: ["식물생명환경전공", "조경학전공"],
  },
  {
    name: "동물생명융합학부",
    majors: ["동물생명과학전공", "동물응용과학전공"],
  },
  {
    name: "생명공학부",
    majors: ["원예생명공학전공", "응용생명공학전공"],
  },
  {
    name: "건설환경공학부",
    majors: ["토목공학전공", "환경공학전공"],
  },
  {
    name: "사회안전시스템공학부",
    majors: ["안전공학전공", "지역자원시스템공학전공"],
  },
  {
    name: "식품생명화학공학부",
    majors: ["식품생명공학전공", "화학공학전공"],
  },
  {
    name: "컴퓨터응용수학부",
    majors: [
      "소프트웨어&서비스컴퓨팅전공",
      "소프트웨어융합전공",
      "정보보안전공",
      "응용수학전공",
    ],
  },
  {
    name: "ICT로봇기계공학부",
    majors: ["ICT로봇공학전공", "기계공학전공"],
  },
  {
    name: "전자전기공학부",
    majors: ["전자공학전공", "전기공학전공"],
  },
  {
    name: "건축융합학부",
    majors: ["건축학전공(5년제)", "건축공학전공"],
  },
  {
    name: "디자인예술스포츠학부",
    majors: [
      "스포츠과학전공",
      "시각미디어디자인전공",
      "제품공간디자인전공",
      "실용음악전공",
      "귀금속보석공예전공",
    ],
  },
  {
    name: "복지융합학부",
    majors: [
      "사회복지학전공",
      "한국수어교육전공",
      "유아특수보육학전공",
      "의료재활공학전공",
    ],
  },
  {
    name: "사회통합학부",
    majors: ["공공행정전공", "상담심리교육전공", "특수체육학전공"],
  },
  { name: "AI반도체융합학부", majors: ["AI반도체융합전공"] },
  { name: "HK자율전공학부", majors: [] },
  {
    name: "글로벌창의인재학부",
    majors: [
      "글로벌스포츠매니지먼트전공",
      "글로벌경영전공",
      "글로벌인공지능IT전공",
      "식물스마트팜전공",
      "식물생의학전공",
      "바이오에너지전공",
      "아시아문화경영전공",
      "Bio-Mobility전공",
      "English Language, Literature and Language Technology전공",
    ],
  },
  {
    name: "미래융합학부",
    majors: [
      "노동복지전공",
      "행정관리전공",
      "레저스포츠매니지먼트전공",
      "융합레포츠전공",
    ],
  },
  {
    name: "AI융합학부",
    majors: [
      "AI스마트팜융합전공(융합전공)",
      "스마트헬스케어전공(융합전공)",
      "AI빅데이터융합전공(융합전공)",
      "공공헬스케어IT융합전공(융합전공)",
      "반도체융합전공(융합전공)",
      "스마트애그리푸드시스템전공(융합전공)",
      "탄소중립바이오시스템융합전공(융합전공)",
    ],
  },
];

const MBTI_DIMENSIONS = [
  {
    id: "mbtiEnergy",
    label: "에너지 방향",
    helper: "외향 / 내향",
    options: ["E", "I"],
  },
  {
    id: "mbtiInformation",
    label: "인식 방식",
    helper: "직관 / 감각",
    options: ["N", "S"],
  },
  {
    id: "mbtiDecision",
    label: "판단 방식",
    helper: "감정 / 사고",
    options: ["F", "T"],
  },
  {
    id: "mbtiLifestyle",
    label: "생활 양식",
    helper: "인식 / 판단",
    options: ["P", "J"],
  },
];

const SURVEY_STEPS = [
  {
    id: "basic",
    shortTitle: "기본 정보",
    title: "기본·학적 정보",
    description: "학교 정보와 기본 정보를 입력해주세요.",
    fields: [
      {
        id: "gender",
        label: "성별",
        type: "single",
        options: ["남자", "여자"],
        required: true,
      },
      {
        id: "dormitory",
        label: "생활관",
        type: "single",
        options: ["나래", "호연", "비룡", "창조"],
        required: true,
      },
      { id: "faculty", label: "나의 학부", type: "faculty", required: true },
      { id: "major", label: "나의 전공", type: "major", required: true },
      {
        id: "birthYear",
        label: "출생연도",
        type: "text",
        placeholder: "예: 2005",
        inputMode: "numeric",
        required: true,
      },
      {
        id: "studentId",
        label: "학번",
        type: "text",
        placeholder: "학번을 입력해주세요",
        inputMode: "numeric",
        required: true,
      },
      {
        id: "grade",
        label: "학년",
        type: "single",
        options: ["1학년", "2학년", "3학년", "4학년", "5학년"],
        required: true,
      },
      {
        id: "mbti",
        label: "MBTI",
        type: "mbti",
        dimensions: MBTI_DIMENSIONS,
        required: true,
      },
    ],
  },
  {
    id: "lifePattern",
    shortTitle: "생활 패턴",
    title: "생활 패턴",
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
        id: "dormStayDays",
        label: "기숙사 상주 주기",
        type: "multi",
        options: ["월", "화", "수", "목", "금", "토", "일"],
        allShortcut: true,
        required: true,
      },
      {
        id: "hairDryerInRoom",
        label: "방 안에서 드라이기 사용",
        type: "single",
        options: ["사용해요", "사용하지 않아요"],
        required: true,
      },
    ],
  },
  {
    id: "livingHabits",
    shortTitle: "생활 습관",
    title: "청소·생활 습관",
    description: "공용 공간을 함께 사용하는 나의 생활 방식을 선택해주세요.",
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
      {
        id: "temperaturePreference",
        label: "온도 선호",
        type: "single",
        options: ["추위 타요", "더위 타요", "보통"],
        required: true,
      },
    ],
  },
  {
    id: "dormLife",
    shortTitle: "기숙사 생활",
    title: "기숙사 생활",
    description: "룸메이트와 공유하게 될 생활 방식에 대해 알려주세요.",
    fields: [
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
  faculty: "학부를 먼저 선택해주세요.",
  major: "선택한 학부의 전공만 표시돼요.",
  birthYear: "태어난 연도를 네 자리로 입력해주세요.",
  studentId: "숫자만 입력해주세요.",
  dormStayDays: "복수 선택 가능",
  mealPlan: "복수 선택 가능",
};

function findFaculty(name) {
  return FACULTY_OPTIONS.find((faculty) => faculty.name === name);
}

function createInitialValues() {
  return SURVEY_STEPS.flatMap((step) => step.fields).reduce((values, field) => {
    if (field.type === "mbti") {
      field.dimensions.forEach((dimension) => {
        values[dimension.id] = "";
      });
    } else {
      values[field.id] = field.type === "multi" ? [] : "";
    }
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
  const selectedFaculty = findFaculty(values.faculty);

  step.fields.forEach((field) => {
    if (field.type === "mbti") {
      if (field.dimensions.some((dimension) => !values[dimension.id])) {
        nextErrors[field.id] = "MBTI 네 가지 기준을 모두 선택해주세요.";
      }
      return;
    }

    if (field.id === "major" && selectedFaculty?.majors.length === 0) return;

    if (field.required && isEmpty(values[field.id])) {
      nextErrors[field.id] = `${field.label} 항목을 선택하거나 입력해주세요.`;
      return;
    }

    if (field.id === "birthYear" && values.birthYear) {
      const currentYear = new Date().getFullYear();
      const birthYear = Number(values.birthYear);
      if (
        !/^\d{4}$/.test(values.birthYear) ||
        birthYear < 1900 ||
        birthYear > currentYear
      ) {
        nextErrors.birthYear = `1900년부터 ${currentYear}년 사이의 연도를 입력해주세요.`;
      }
    }

    if (
      field.id === "studentId" &&
      values.studentId &&
      !/^\d+$/.test(values.studentId)
    ) {
      nextErrors.studentId = "학번은 숫자로만 입력해주세요.";
    }
  });

  return nextErrors;
}

function isFieldAnswered(field, values) {
  if (field.type === "mbti") {
    return field.dimensions.every((dimension) => values[dimension.id]);
  }
  return !isEmpty(values[field.id]);
}

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
  const [showValidation, setShowValidation] = useState(false);
  const [showComplete, setShowComplete] = useState(false);
  const titleRef = useRef(null);
  const completeRef = useRef(null);

  const currentStep = SURVEY_STEPS[currentStepIndex];
  const selectedFaculty = useMemo(
    () => findFaculty(values.faculty),
    [values.faculty],
  );
  const mbti = MBTI_DIMENSIONS.map((dimension) => values[dimension.id]).join(
    "",
  );
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
    setValues((currentValues) => {
      const nextValues = { ...currentValues, [fieldId]: value };
      if (fieldId === "faculty") {
        const faculty = findFaculty(value);
        nextValues.major = faculty?.majors.length === 0 ? "세부전공 없음" : "";
      }
      return nextValues;
    });
    clearError(fieldId);
    if (fieldId === "faculty") clearError("major");
    setShowComplete(false);
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
    setShowValidation(false);
    setShowComplete(false);
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
      setShowValidation(true);
      focusFirstError(nextErrors);
      return;
    }

    setErrors({});
    setShowValidation(false);

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
        setShowValidation(true);
        focusFirstError(sectionErrors);
        return;
      }
    }

    setShowComplete(true);
    window.requestAnimationFrame(() => completeRef.current?.focus());
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

  const renderSelectField = (field) => {
    const facultyField = field.type === "faculty";
    const options = facultyField
      ? FACULTY_OPTIONS.map((faculty) => faculty.name)
      : selectedFaculty?.majors || [];
    const hasNoMajor = !facultyField && selectedFaculty?.majors.length === 0;
    const disabled = !facultyField && (!selectedFaculty || hasNoMajor);

    return (
      <S.Field key={field.id} data-field-id={field.id}>
        <S.FieldLabel htmlFor={field.id}>{field.label}</S.FieldLabel>
        <S.SelectShell $hasError={Boolean(errors[field.id])}>
          <S.Select
            id={field.id}
            value={values[field.id]}
            disabled={disabled}
            aria-invalid={Boolean(errors[field.id])}
            aria-describedby={
              errors[field.id] ? `${field.id}-error` : undefined
            }
            onChange={(event) => setFieldValue(field.id, event.target.value)}
          >
            {hasNoMajor ? (
              <option value="세부전공 없음">세부전공 없음</option>
            ) : (
              <option value="">
                {!facultyField && !selectedFaculty
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
          <ChevronDown size={18} aria-hidden="true" />
        </S.SelectShell>
        <S.HelperText>
          {hasNoMajor
            ? "선택한 학부는 별도 세부전공이 없어요."
            : FIELD_HINTS[field.id]}
        </S.HelperText>
        {renderError(field)}
      </S.Field>
    );
  };

  const renderTextField = (field) => (
    <S.Field key={field.id} data-field-id={field.id}>
      <S.FieldLabel htmlFor={field.id}>{field.label}</S.FieldLabel>
      <S.TextInput
        id={field.id}
        type="text"
        inputMode={field.inputMode}
        autoComplete="off"
        value={values[field.id]}
        placeholder={field.placeholder}
        maxLength={field.id === "birthYear" ? 4 : 20}
        aria-invalid={Boolean(errors[field.id])}
        aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
        $hasError={Boolean(errors[field.id])}
        onChange={(event) =>
          setFieldValue(field.id, event.target.value.replace(/\D/g, ""))
        }
      />
      <S.HelperText>{FIELD_HINTS[field.id]}</S.HelperText>
      {renderError(field)}
    </S.Field>
  );

  const renderMbtiField = (field) => (
    <S.Fieldset
      key={field.id}
      data-field-id={field.id}
      aria-describedby={errors[field.id] ? `${field.id}-error` : undefined}
    >
      <S.Legend>{renderLabel(field)}</S.Legend>
      <S.MbtiLayout>
        <S.MbtiGrid>
          {field.dimensions.map((dimension) => (
            <S.MbtiAxis
              key={dimension.id}
              role="group"
              aria-label={dimension.label}
            >
              <S.MbtiAxisText>
                <strong>{dimension.label}</strong>
                <span>{dimension.helper}</span>
              </S.MbtiAxisText>
              <S.MbtiChoices>
                {dimension.options.map((option) => (
                  <S.MbtiButton
                    key={option}
                    type="button"
                    $selected={values[dimension.id] === option}
                    aria-pressed={values[dimension.id] === option}
                    onClick={() => {
                      setFieldValue(dimension.id, option);
                      clearError(field.id);
                    }}
                  >
                    {option}
                  </S.MbtiButton>
                ))}
              </S.MbtiChoices>
            </S.MbtiAxis>
          ))}
        </S.MbtiGrid>
        <S.MbtiPreview aria-live="polite">
          <span>나의 MBTI</span>
          <strong>{mbti.length === 4 ? mbti : "— — — —"}</strong>
          <small>
            {mbti.length === 4 ? "선택 완료" : "네 기준을 선택해주세요"}
          </small>
        </S.MbtiPreview>
      </S.MbtiLayout>
      {renderError(field)}
    </S.Fieldset>
  );

  const renderField = (field) => {
    if (field.type === "single" || field.type === "multi")
      return renderChoiceField(field);
    if (field.type === "faculty" || field.type === "major")
      return renderSelectField(field);
    if (field.type === "text") return renderTextField(field);
    if (field.type === "mbti") return renderMbtiField(field);
    return null;
  };

  const renderFields = () => {
    const blocks = [];
    let compactFields = [];

    const flushCompactFields = () => {
      if (compactFields.length === 0) return;
      blocks.push(
        <S.TextFieldGrid key={`fields-${blocks.length}`}>
          {compactFields.map(renderField)}
        </S.TextFieldGrid>,
      );
      compactFields = [];
    };

    currentStep.fields.forEach((field) => {
      if (["faculty", "major", "text"].includes(field.type)) {
        compactFields.push(field);
      } else {
        flushCompactFields();
        blocks.push(renderField(field));
      }
    });
    flushCompactFields();
    return blocks;
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
              <Clock3 size={15} aria-hidden="true" /> 약 5분 소요
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
              {showValidation && Object.keys(errors).length > 0 && (
                <S.ValidationBanner role="alert">
                  <AlertCircle size={18} aria-hidden="true" />
                  <div>
                    <strong>아직 입력하지 않은 항목이 있어요.</strong>
                  </div>
                </S.ValidationBanner>
              )}
              {renderFields()}
              {showComplete && (
                <S.CompleteBanner ref={completeRef} tabIndex="-1" role="status">
                  <CheckCircle2 size={21} aria-hidden="true" />
                  <div>
                    <strong>모든 답변을 확인했어요.</strong>
                    <span>
                      현재 화면은 UI 확인용이며 실제 저장은 진행되지 않습니다.
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
