import { PREFERENCE_STEPS } from "../../data/preferenceSurvey";
import { getMyPreferences, saveMyPreferences, getMyProfileSurvey, saveMyProfileSurvey } from "../../api/surveyApi";
import { getMyInfo, updateMyInfo, changePassword } from "../../api/userApi";
import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowRight,
  BadgeCheck,
  Building2,
  Check,
  ChevronRight,
  ClipboardList,
  Heart,
  LockKeyhole,
  Moon,
  Pencil,
  Settings2,
  ShieldCheck,
  Sparkles,
  UserRound,
  Users,
} from "lucide-react";

import { FACULTY_OPTIONS } from "../../data/facultyOptions";
import Pagination from "../../components/common/Pagination/Pagination";
import ProfileImage from "../../components/common/ProfileImage";
import ProfileCard from "../../components/common/ProfileCard/ProfileCard";
import * as S from "./MyPageCss";
import { getMyLikedRoommates, toRoommateProfile } from "../../api/roommateApi";
import {
  meetsPasswordRequirements,
  PASSWORD_REQUIREMENTS_MESSAGE,
} from "../../utils/passwordValidation";

import { PROFILE_SECTIONS, createEmptyProfileAnswers } from "../../data/profileSurvey";

const MBTI_DIMENSIONS = [
  { id: "mbtiEnergy", label: "에너지 방향", options: ["E", "I"] },
  { id: "mbtiInformation", label: "인식 방식", options: ["N", "S"] },
  { id: "mbtiDecision", label: "판단 방식", options: ["F", "T"] },
  { id: "mbtiLifestyle", label: "생활 양식", options: ["P", "J"] },
];

const PREFERENCE_WEIGHT_OPTIONS = [
  { value: 1, label: "매우 낮음" },
  { value: 2, label: "낮음" },
  { value: 3, label: "보통" },
  { value: 4, label: "높음" },
  { value: 5, label: "매우 높음" },
];

const DEFAULT_PREFERENCE_WEIGHT = 3;

const getPreferenceWeightLabel = (value) =>
  PREFERENCE_WEIGHT_OPTIONS.find((option) => option.value === value)?.label ||
  "보통";

const getPreferenceWeight = (answers, id) =>
  answers.weights?.[id] ?? DEFAULT_PREFERENCE_WEIGHT;

const getInternationalAge = (birthYear) => {
  const parsedBirthYear = Number.parseInt(birthYear, 10);
  if (!Number.isInteger(parsedBirthYear)) return null;
  return new Date().getFullYear() - parsedBirthYear;
};

const MENU_ITEMS = [
  { id: "profile", label: "프로필", icon: UserRound },
  { id: "survey", label: "내 설문", icon: ClipboardList },
  { id: "preferences", label: "선호 룸메 설문", icon: Users },
  { id: "likes", label: "관심 프로필", icon: Heart },
  { id: "account", label: "계정 정보", icon: Settings2 },
];

function createAvatar(background, shirt, hair = "#363a46") {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160">
    <rect width="160" height="160" rx="24" fill="${background}"/>
    <circle cx="80" cy="66" r="31" fill="#f2c5a7"/>
    <path d="M47 66c0-27 15-43 36-43 23 0 34 17 34 39-11-2-21-8-28-18-8 12-22 20-42 22Z" fill="${hair}"/>
    <path d="M35 160c3-35 20-53 45-53s42 18 45 53H35Z" fill="${shirt}"/>
    <circle cx="68" cy="67" r="2.4" fill="#3d3532"/><circle cx="92" cy="67" r="2.4" fill="#3d3532"/>
    <path d="M71 83c6 5 12 5 18 0" fill="none" stroke="#9b6254" stroke-width="2.5" stroke-linecap="round"/>
  </svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

const MY_PHOTO = createAvatar("#e8eef9", "#0c3273");
const LIKES_PER_PAGE = 6;

const asArray = (value) => (Array.isArray(value) ? value : [value]);
const hasAnswer = (value) =>
  asArray(value).some((item) => String(item || "").trim());
const displayAnswer = (value) =>
  Array.isArray(value) ? value.join(" · ") : value;
const getMbti = (account) =>
  MBTI_DIMENSIONS.map(({ id }) => account[id]).join("");

function EmptyState({ type, onStart }) {
  const isLikes = type === "likes";
  const isPreference = type === "preferences";
  const Icon = isLikes ? Heart : isPreference ? Users : ClipboardList;
  return (
    <S.EmptyCard>
      <S.EmptyIcon>
        <Icon size={30} aria-hidden="true" />
      </S.EmptyIcon>
      <S.Badge $muted>{isLikes ? "좋아요 0명" : "미작성"}</S.Badge>
      <S.EmptyTitle>
        {isLikes
          ? "아직 마음에 담은 룸메이트가 없어요"
          : isPreference
            ? "어떤 룸메이트와 함께하고 싶나요?"
            : "아직 나의 설문을 작성하지 않았어요"}
      </S.EmptyTitle>
      {isLikes && (
        <S.EmptyText>
          마음이 맞을 것 같은 룸메이트를 찾아보세요. 좋아요한 프로필을 여기서
          모아볼 수 있어요.
        </S.EmptyText>
      )}
      <S.PrimaryButton
        {...(isLikes
          ? { as: Link, to: "/roommates" }
          : { type: "button", onClick: onStart })}
      >
        {isLikes
          ? "룸메이트 둘러보기"
          : isPreference
            ? "선호 룸메 설문 작성하기"
            : "내 설문 작성하기"}
        <ArrowRight size={16} aria-hidden="true" />
      </S.PrimaryButton>
    </S.EmptyCard>
  );
}

function SurveyContent({ type, answers, isEmpty, onSave }) {
  const isPreference = type === "preferences";
  const sections = isPreference ? PREFERENCE_STEPS : PROFILE_SECTIONS;
  const [draft, setDraft] = useState(null);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState({});
  const formRef = useRef(null);
  const titleRef = useRef(null);
  const values = draft || answers;

  const updateValue = (field, option) => {
    let value = option;
    if (field.type === "multi") {
      const selected = draft[field.id];
      const exclusive = field.exclusiveOptions || [];
      const regular = selected.filter((item) => !exclusive.includes(item));
      value = selected.includes(option)
        ? selected.filter((item) => item !== option)
        : exclusive.includes(option)
          ? [option]
          : [...regular, option];
    }
    setDraft((current) => ({
      ...current,
      [field.id]: value,
    }));
    setErrors((current) => ({ ...current, [field.id]: undefined }));
  };

  const save = async (event) => {
    event.preventDefault();
    if (saving) return;
    if (!isPreference && (draft.introduction || "").length > 200) {
      setErrors({ introduction: "나의 한마디는 200자 이하여야 합니다." });
      return;
    }
    const missing = sections
      .flatMap((section) => section.fields)
      .filter((field) => !hasAnswer(draft[field.id]));
    if (missing.length) {
      setErrors(
        Object.fromEntries(
          missing.map((field) => [
            field.id,
            `${field.label} 항목을 선택해 주세요.`,
          ]),
        ),
      );
      requestAnimationFrame(() =>
        formRef.current?.querySelector('[aria-invalid="true"]')?.focus(),
      );
      return;
    }
    const saved = isPreference
      ? {
          ...draft,
          weights: Object.fromEntries(
            sections.flatMap((section) =>
              section.fields.map((field) => [
                field.id,
                getPreferenceWeight(draft, field.id),
              ]),
            ),
          ),
        }
      : draft;
    setSaving(true);
    try {
      if ((await onSave(saved)) === false) return;
    } catch (error) {
      window.alert(error.message);
      return;
    } finally {
      setSaving(false);
    }
    setDraft(null);
    setErrors({});
    requestAnimationFrame(() => {
      window.scrollTo(0, 0);
      titleRef.current?.focus({ preventScroll: true });
    });
  };

  const startEditing = () => {
    const emptyAnswers = Object.fromEntries(
      sections.flatMap((section) =>
        section.fields.map((field) => [
          field.id,
          field.type === "multi" ? [] : "",
        ]),
      ),
    );
    const defaultWeights = Object.fromEntries(
      sections.flatMap((section) =>
        section.fields.map((field) => [field.id, DEFAULT_PREFERENCE_WEIGHT]),
      ),
    );
    setDraft(
      isEmpty
        ? {
          ...emptyAnswers,
          ...answers,
          ...(isPreference ? { weights: defaultWeights } : {}),
        }
        : isPreference
          ? { ...answers, weights: { ...defaultWeights, ...answers.weights } }
          : { ...answers },
    );
    setErrors({});
    requestAnimationFrame(() =>
      formRef.current?.querySelector("select, input, button")?.focus(),
    );
  };

  return (
    <S.Stack>
      <S.SectionHeading>
        <div>
          <S.SectionTitle ref={titleRef} tabIndex={-1}>
            {isPreference ? "선호 룸메 설문" : "내 설문"}
          </S.SectionTitle>
          <S.Description>
            {!isPreference && "나의 하루를 담은 생활 습관이에요."}
          </S.Description>
          {isPreference && (
            <S.WeightNotice>
              <S.WeightNoticeLabel>가중치 안내</S.WeightNoticeLabel>
              <span>
                중요도를 매우 낮음부터 매우 높음까지 설정할 수 있어요.
              </span>
            </S.WeightNotice>
          )}
        </div>
        {!isEmpty && !draft && (
          <S.SecondaryButton onClick={() => startEditing()}>
            <Pencil size={16} aria-hidden="true" /> 설문 수정
          </S.SecondaryButton>
        )}
      </S.SectionHeading>
      {isEmpty && !draft ? (
        <EmptyState type={type} onStart={() => startEditing()} />
      ) : (
        <>
          <S.Stack as="form" ref={formRef} onSubmit={save} noValidate>
            <fieldset
              disabled={saving}
              style={{
                border: 0,
                padding: 0,
                margin: 0,
                minWidth: 0,
                display: "grid",
                gap: 24,
              }}
            >
              {sections.map((section, index) => (
                <S.Card key={section.id}>
                  <S.CardHeader>
                    <S.CardTitle>
                      {String(index + 1).padStart(2, "0")} · {section.title}
                    </S.CardTitle>
                  </S.CardHeader>
                  {draft ? (
                    <S.FormGrid>
                      {section.fields.map((field) => (
                        <S.Field key={field.id}>
                          {field.type === "multi" ? (
                            <S.Fieldset>
                              <S.Legend>
                                <span>{field.label}</span>
                                <S.MultiSelectHint>
                                  복수 선택 가능
                                </S.MultiSelectHint>
                              </S.Legend>
                              <S.ChoiceGroup>
                                {field.options.map((option) => (
                                  <S.ChoiceButton
                                    key={option}
                                    type="button"
                                    $selected={draft[field.id].includes(option)}
                                    aria-pressed={draft[field.id].includes(
                                      option,
                                    )}
                                    aria-invalid={Boolean(errors[field.id])}
                                    aria-describedby={
                                      errors[field.id]
                                        ? `${type}-${field.id}-error`
                                        : undefined
                                    }
                                    onClick={() => updateValue(field, option)}
                                  >
                                    {draft[field.id].includes(option) && (
                                      <Check size={14} aria-hidden="true" />
                                    )}
                                    {option}
                                  </S.ChoiceButton>
                                ))}
                              </S.ChoiceGroup>
                            </S.Fieldset>
                          ) : (
                            <>
                              <S.Label htmlFor={`${type}-${field.id}`}>
                                {field.label}
                              </S.Label>
                              <S.Select
                                id={`${type}-${field.id}`}
                                value={draft[field.id]}
                                onChange={(event) =>
                                  updateValue(field, event.target.value)
                                }
                                aria-invalid={Boolean(errors[field.id])}
                                aria-describedby={
                                  errors[field.id]
                                    ? `${type}-${field.id}-error`
                                    : undefined
                                }
                              >
                                <option value="">선택해 주세요</option>
                                {field.options.map((option) => (
                                  <option key={option} value={option}>
                                    {option}
                                  </option>
                                ))}
                              </S.Select>
                            </>
                          )}
                          {isPreference && (
                            <S.WeightControl>
                              <S.WeightValue>
                                {getPreferenceWeightLabel(
                                  getPreferenceWeight(draft, field.id),
                                )}
                              </S.WeightValue>
                              <S.WeightSlider
                                type="range"
                                id={`${type}-${field.id}-weight`}
                                $value={getPreferenceWeight(draft, field.id)}
                                min="1"
                                max="5"
                                step="1"
                                aria-label={`${field.label} 가중치`}
                                value={getPreferenceWeight(draft, field.id)}
                                onChange={(event) => {
                                  const weight = Number(event.target.value);
                                  setDraft((current) => ({
                                    ...current,
                                    weights: {
                                      ...current.weights,
                                      [field.id]: weight,
                                    },
                                  }));
                                }}
                              />
                            </S.WeightControl>
                          )}
                          {errors[field.id] && (
                            <S.FieldError
                              id={`${type}-${field.id}-error`}
                              role="alert"
                            >
                              {errors[field.id]}
                            </S.FieldError>
                          )}
                        </S.Field>
                      ))}
                    </S.FormGrid>
                  ) : (
                    <S.SurveyGrid>
                      {section.fields.map((field) => (
                        <S.SurveyRow key={field.id}>
                          <dt>{field.label}</dt>
                          <dd>
                            {displayAnswer(values[field.id])}
                            {isPreference && (
                              <S.WeightBadge>
                                {getPreferenceWeightLabel(
                                  getPreferenceWeight(values, field.id),
                                )}
                              </S.WeightBadge>
                            )}
                          </dd>
                        </S.SurveyRow>
                      ))}
                    </S.SurveyGrid>
                  )}
                </S.Card>
              ))}
              {!isPreference && (
                <S.Card>
                  <S.CardHeader><S.CardTitle>나의 한마디</S.CardTitle></S.CardHeader>
                  <S.Description id="introduction-help">함께 생활할 룸메이트에게 나를 소개해 보세요.</S.Description>
                  {draft ? (
                    <S.Field>
                      <S.Label htmlFor="survey-introduction">나의 한마디 (선택)</S.Label>
                      <S.IntroductionInput
                        id="survey-introduction"
                        rows={4}
                        maxLength={200}
                        value={draft.introduction || ""}
                        placeholder="안녕하세요! 저는 조용한 환경을 좋아하고 서로 배려하면서 편안하게 생활할 룸메이트를 찾고 있어요."
                        aria-describedby="introduction-help introduction-count introduction-error"
                        aria-invalid={Boolean(errors.introduction)}
                        onChange={(event) => {
                          const value = event.target.value;
                          if (value.length <= 200 || value.length < (draft.introduction || "").length) {
                            updateValue({ id: "introduction" }, value);
                          }
                        }}
                      />
                      <S.Description id="introduction-count">{(draft.introduction || "").length} / 200</S.Description>
                      <S.FieldError id="introduction-error" role={errors.introduction ? "alert" : undefined}>{errors.introduction}</S.FieldError>
                    </S.Field>
                  ) : (
                    <S.IntroductionText>{values.introduction?.trim() ? values.introduction : "아직 한마디를 작성하지 않았어요."}</S.IntroductionText>
                  )}
                </S.Card>
              )}
              {draft && (
                <S.FormActions>
                  <S.GhostButton
                    type="button"
                    onClick={() => {
                      setDraft(null);
                      setErrors({});
                      requestAnimationFrame(() => titleRef.current?.focus());
                    }}
                  >
                    취소
                  </S.GhostButton>
                  <S.PrimaryButton type="submit">
                    <Check size={17} aria-hidden="true" />{" "}
                    {saving ? "저장 중…" : "저장"}
                  </S.PrimaryButton>
                </S.FormActions>
              )}
            </fieldset>
          </S.Stack>
        </>
      )}
    </S.Stack>
  );
}

function AccountContent({ account, onSave }) {
  const [saving, setSaving] = useState(false);
  const pendingRef = useRef(false);
  const [draft, setDraft] = useState({
    ...account,
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [editingFields, setEditingFields] = useState({});
  const [passwordEditing, setPasswordEditing] = useState(false);
  const [errors, setErrors] = useState({});
  const formRef = useRef(null);
  const faculty = FACULTY_OPTIONS.find((item) => item.name === draft.faculty);
  const noMajor = Boolean(faculty && faculty.majors.length === 0);
  const basicFields = ["nickname"];
  const academicFields = ["dormitory", "grade", "faculty", "major"];
  const mbtiFields = MBTI_DIMENSIONS.map(({ id }) => id);
  const basicEditing = basicFields.some((id) => editingFields[id]);
  const academicEditing = academicFields.some((id) => editingFields[id]);
  const mbtiEditing = mbtiFields.some((id) => editingFields[id]);

  const nicknameIsValid = /^[가-힣A-Za-z0-9]{2,8}$/.test(draft.nickname);

  const update = (id, value) => {
    setDraft((current) => ({
      ...current,
      [id]: value,
      ...(id === "faculty"
        ? {
            major:
              FACULTY_OPTIONS.find((item) => item.name === value)?.majors
                .length === 0
                ? "세부전공 없음"
                : "",
          }
        : {}),
    }));
    setErrors((current) => ({
      ...current,
      [id]: undefined,
      ...(id === "faculty" ? { major: undefined } : {}),
    }));
  };

  const finishSection = async (ids) => {
    if (pendingRef.current) return;
    const nextErrors = {};
    ids.forEach((id) => {
      if (!(id === "major" && noMajor) && !String(draft[id] || "").trim())
        nextErrors[id] = `${id} 항목을 입력하거나 선택해 주세요.`;
    });
    if (ids.includes("nickname") && !nicknameIsValid) {
      nextErrors.nickname =
        "닉네임은 한글, 영문, 숫자만 사용해 2~8자로 입력해 주세요.";
    }
    if (
      ids.includes("faculty") &&
      faculty?.majors.length &&
      !faculty.majors.includes(draft.major)
    ) {
      nextErrors.faculty = "선택한 학부에 해당하는 전공도 확인해 주세요.";
    }
    if (ids.some((id) => MBTI_DIMENSIONS.some((item) => item.id === id))) {
      MBTI_DIMENSIONS.forEach(({ id, label, options }) => {
        if (ids.includes(id) && !options.includes(draft[id]))
          nextErrors[id] = `${label}을 선택해 주세요.`;
      });
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    pendingRef.current = true;
    setSaving(true);
    try {
      const payload = Object.fromEntries(
        ids
          .filter((id) => !(id === "major" && noMajor))
          .map((id) => [id, draft[id]]),
      );
      const saved = await onSave(payload);
      setDraft((current) => ({ ...current, ...saved }));
      window.alert("회원정보를 저장했어요.");
    } catch (error) {
      window.alert(error.message);
      return;
    } finally {
      pendingRef.current = false;
      setSaving(false);
    }
    setEditingFields((current) =>
      Object.fromEntries(Object.keys(current).map((id) => [id, false])),
    );
  };

  const setSectionEditing = (ids, value) => {
    setEditingFields((current) => ({
      ...current,
      ...Object.fromEntries(ids.map((id) => [id, value])),
    }));
    setErrors({});
  };

  const finishPassword = async () => {
    if (pendingRef.current) return;
    const hasPasswordInput = Boolean(
      draft.currentPassword || draft.newPassword || draft.confirmPassword,
    );
    if (!hasPasswordInput) {
      setErrors({});
      setPasswordEditing(false);
      return;
    }
    const nextErrors = {};
    if (!draft.currentPassword.trim())
      nextErrors.currentPassword = "현재 비밀번호를 입력해 주세요.";
    if (!draft.newPassword.trim())
      nextErrors.newPassword = "변경할 비밀번호를 입력해 주세요.";
    else if (!meetsPasswordRequirements(draft.newPassword))
      nextErrors.newPassword = PASSWORD_REQUIREMENTS_MESSAGE;
    if (!draft.confirmPassword)
      nextErrors.confirmPassword = "변경할 비밀번호를 한 번 더 입력해 주세요.";
    else if (draft.newPassword !== draft.confirmPassword)
      nextErrors.confirmPassword = "새 비밀번호가 일치하지 않아요.";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) return;
    pendingRef.current = true;
    setSaving(true);
    try {
      await changePassword(
        draft.currentPassword,
        draft.newPassword,
        draft.confirmPassword,
      );
      window.alert("비밀번호를 변경했어요.");
    } catch (error) {
      window.alert(error.message);
      return;
    } finally {
      pendingRef.current = false;
      setSaving(false);
    }
    setPasswordEditing(false);
    setDraft((current) => ({
      ...current,
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    }));
  };

  const save = (event) => {
    event.preventDefault();
    if (passwordEditing) finishPassword();
  };

  const renderField = (
    id,
    label,
    { options, readOnly, type = "text", helper, displayValue } = {},
  ) => {
    const isPassword = type === "password";
    const isEditing = isPassword ? passwordEditing : editingFields[id];
    return (
      <S.Field key={id}>
        <S.Label htmlFor={`account-${id}`}>
          {label}
          {!readOnly && !isPassword && " *"}
          {readOnly && <LockKeyhole size={12} aria-hidden="true" />}
        </S.Label>
        {readOnly || !isEditing ? (
          <S.ReadonlyValue>
            {isPassword ? "••••••••" : (displayValue ?? draft[id]) || "-"}
          </S.ReadonlyValue>
        ) : options ? (
          <S.Select
            id={`account-${id}`}
            value={draft[id]}
            disabled={id === "major" && (!faculty || noMajor)}
            aria-required="true"
            aria-invalid={Boolean(errors[id])}
            aria-describedby={`account-${id}-help`}
            onChange={(event) => update(id, event.target.value)}
          >
            {noMajor && id === "major" ? (
              <option value="세부전공 없음">세부전공 없음</option>
            ) : (
              <>
                <option value="">
                  {id === "major" && !faculty
                    ? "학부를 먼저 선택해 주세요"
                    : `${label} 선택`}
                </option>
                {options.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </>
            )}
          </S.Select>
        ) : (
          <S.Input
            id={`account-${id}`}
            type={type}
            value={draft[id]}
            maxLength={id === "nickname" ? 8 : undefined}
            autoComplete={
              type === "password"
                ? id === "currentPassword"
                  ? "current-password"
                  : "new-password"
                : "off"
            }
            aria-required={!readOnly && type !== "password"}
            aria-invalid={Boolean(errors[id])}
            aria-describedby={`account-${id}-help`}
            onChange={(event) => update(id, event.target.value)}
          />
        )}
        {errors[id] ? (
          <S.FieldError id={`account-${id}-help`} role="alert">
            {errors[id]}
          </S.FieldError>
        ) : (
          <S.Helper id={`account-${id}-help`}>{helper || ""}</S.Helper>
        )}
      </S.Field>
    );
  };

  return (
    <S.Stack
      as="form"
      ref={formRef}
      onSubmit={save}
      noValidate
      aria-busy={saving}
    >
      <fieldset
        disabled={saving}
        style={{
          border: 0,
          padding: 0,
          margin: 0,
          minWidth: 0,
          display: "flex",
          flexDirection: "column",
          gap: 24,
        }}
      >
        <S.SectionHeading>
          <div>
            <S.SectionTitle>계정 정보</S.SectionTitle>
            <S.Description>
              나의 정보를 최신 상태로 관리해 주세요.
            </S.Description>
          </div>
        </S.SectionHeading>
        <S.Card>
          <S.CardHeader>
            <S.CardTitle>
              <UserRound size={19} aria-hidden="true" /> 기본 정보
            </S.CardTitle>
            <S.SecondaryButton
              type="button"
              onClick={() =>
                basicEditing
                  ? finishSection(basicFields)
                  : setSectionEditing(basicFields, true)
              }
            >
              {basicEditing ? (
                <>
                  <Check size={16} aria-hidden="true" /> 완료
                </>
              ) : (
                <>
                  <Pencil size={16} aria-hidden="true" /> 수정
                </>
              )}
            </S.SecondaryButton>
          </S.CardHeader>
          <S.FormGrid $compact>
            {renderField("nickname", "닉네임")}
            {renderField("schoolEmail", "학교 이메일", { readOnly: true })}
            {renderField("gender", "성별", { readOnly: true })}
            {renderField("birthYear", "만 나이", {
              readOnly: true,
              displayValue: `${getInternationalAge(draft.birthYear) ?? "-"}세`,
            })}
          </S.FormGrid>
        </S.Card>
        <S.Card>
          <S.CardHeader>
            <S.CardTitle>
              <Building2 size={19} aria-hidden="true" /> 생활관 및 학적 정보
            </S.CardTitle>
            <S.SecondaryButton
              type="button"
              onClick={() =>
                academicEditing
                  ? finishSection(academicFields)
                  : setSectionEditing(academicFields, true)
              }
            >
              {academicEditing ? (
                <>
                  <Check size={16} aria-hidden="true" /> 완료
                </>
              ) : (
                <>
                  <Pencil size={16} aria-hidden="true" /> 수정
                </>
              )}
            </S.SecondaryButton>
          </S.CardHeader>
          <S.FormGrid $compact>
            {renderField("dormitory", "생활관", {
              options: ["나래", "호연", "비봉", "창조"],
            })}
            {renderField("studentId", "학번", {
              readOnly: true,
              displayValue: draft.studentId,
            })}
            {renderField("grade", "학년", {
              options: ["1학년", "2학년", "3학년", "4학년", "5학년"],
            })}
            {renderField("faculty", "나의 학부", {
              options: FACULTY_OPTIONS.map((item) => item.name),
            })}
            {renderField("major", "나의 전공", {
              options: faculty?.majors || [],
              helper: noMajor ? "선택한 학부에는 세부전공이 없어요." : "",
            })}
          </S.FormGrid>
        </S.Card>
        <S.Card>
          <S.CardHeader>
            <S.CardTitle>
              <Sparkles size={19} aria-hidden="true" /> MBTI
              <S.MbtiValue>{getMbti(draft)}</S.MbtiValue>
            </S.CardTitle>
            <S.CardHeaderActions>
              <S.SecondaryButton
                type="button"
                onClick={() =>
                  mbtiEditing
                    ? finishSection(mbtiFields)
                    : setSectionEditing(mbtiFields, true)
                }
              >
                {mbtiEditing ? (
                  <>
                    <Check size={16} aria-hidden="true" /> 완료
                  </>
                ) : (
                  <>
                    <Pencil size={16} aria-hidden="true" /> 수정
                  </>
                )}
              </S.SecondaryButton>
            </S.CardHeaderActions>
          </S.CardHeader>
          <S.MbtiGrid>
            {MBTI_DIMENSIONS.map(({ id, label, options }) => (
              <S.Fieldset key={id}>
                <S.Legend>{label} *</S.Legend>
                {!editingFields[id] ? (
                  <S.ReadonlyValue>{draft[id]}</S.ReadonlyValue>
                ) : (
                  <>
                    <S.ChoiceGroup>
                      {options.map((option) => (
                        <S.ChoiceButton
                          key={option}
                          type="button"
                          $selected={draft[id] === option}
                          aria-pressed={draft[id] === option}
                          aria-invalid={Boolean(errors[id])}
                          aria-describedby={
                            errors[id] ? `${id}-error` : undefined
                          }
                          onClick={() => update(id, option)}
                        >
                          {option}
                        </S.ChoiceButton>
                      ))}
                    </S.ChoiceGroup>
                  </>
                )}
                {errors[id] && (
                  <S.FieldError id={`${id}-error`} role="alert">
                    {errors[id]}
                  </S.FieldError>
                )}
              </S.Fieldset>
            ))}
          </S.MbtiGrid>
        </S.Card>
        <S.Card>
          <S.CardHeader>
            <S.CardTitle>
              <LockKeyhole size={19} aria-hidden="true" /> 비밀번호 변경
            </S.CardTitle>
            <S.SecondaryButton
              type="button"
              onClick={() =>
                passwordEditing ? finishPassword() : setPasswordEditing(true)
              }
            >
              {passwordEditing ? (
                <>
                  <Check size={16} aria-hidden="true" /> 완료
                </>
              ) : (
                <>
                  <Pencil size={16} aria-hidden="true" /> 수정
                </>
              )}
            </S.SecondaryButton>
          </S.CardHeader>
          {!passwordEditing && (
            <S.PasswordSummary>
              <LockKeyhole size={18} aria-hidden="true" />
              <span>
                비밀번호는 안전하게 설정되어 있어요. 변경이 필요할 때 수정해
                주세요.
              </span>
            </S.PasswordSummary>
          )}
          {passwordEditing && (
            <>
              <S.Helper>
                변경을 원할 때만 세 항목을 모두 입력해 주세요.
              </S.Helper>
              <S.FormGrid $compact>
                {renderField("currentPassword", "현재 비밀번호", {
                  type: "password",
                })}
                {renderField("newPassword", "변경할 비밀번호", {
                  type: "password",
                  helper: PASSWORD_REQUIREMENTS_MESSAGE,
                })}
                {renderField("confirmPassword", "변경할 비밀번호 확인", {
                  type: "password",
                })}
              </S.FormGrid>
            </>
          )}
        </S.Card>
      </fieldset>
    </S.Stack>
  );
}

function MyPage({ currentUser, onUserUpdated }) {
  const [searchParams] = useSearchParams();
  const activeTab = MENU_ITEMS.some(
    (item) => item.id === searchParams.get("tab"),
  )
    ? searchParams.get("tab")
    : "profile";

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [activeTab]);

  const [account, setAccount] = useState(null);
  const [accountLoading, setAccountLoading] = useState(true);
  const [accountError, setAccountError] = useState("");
  const [accountRevision, setAccountRevision] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setAccountLoading(true);
    setAccountError("");
    setAccount(null);
    Promise.all([getMyInfo(), getMyPreferences()])
      .then(([user, preference]) => {
        if (!cancelled) {
          setAccount({
            ...user,
            major: user.major || "",
            studentId: user.admissionYear,
          });
          setPreferences(preference.answers || {});
          setPreferencesCompleted(preference.completed);
        }
      })
      .catch((error) => {
        if (!cancelled) setAccountError(error.message);
      })
      .finally(() => {
        if (!cancelled) setAccountLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [currentUser?.id, accountRevision]);
  const saveAccount = async (payload) => {
    const user = await updateMyInfo(payload);
    const saved = {
      ...user,
      major: user.major || "",
      studentId: user.admissionYear,
    };
    setAccount(saved);
    onUserUpdated?.(user);
    return saved;
  };
  const [profileAnswers, setProfileAnswers] = useState(createEmptyProfileAnswers);
  const [surveyState, setSurveyState] = useState({ status: "loading", completed: false, error: "" });
  const [surveyRevision, setSurveyRevision] = useState(0);
  const surveyRequestVersion = useRef(0);
  useEffect(() => {
    surveyRequestVersion.current += 1;
    const controller = new AbortController();
    setSurveyState({ status: "loading", completed: false, error: "" });
    setProfileAnswers(createEmptyProfileAnswers());
    getMyProfileSurvey(controller.signal).then((survey) => {
      if (controller.signal.aborted) return;
      setProfileAnswers({ ...createEmptyProfileAnswers(), ...survey.answers, introduction: survey.introduction ?? "" });
      setSurveyState({ status: "success", completed: survey.completed, error: "" });
    }).catch((error) => {
      if (!controller.signal.aborted) setSurveyState({ status: "error", completed: false, error: error.message });
    });
    return () => {
      controller.abort();
      surveyRequestVersion.current += 1;
    };
  }, [currentUser?.id, activeTab, surveyRevision]);
  const [preferences, setPreferences] = useState({});
  const [preferencesCompleted, setPreferencesCompleted] = useState(false);
  const hasSurveyAnswers = {
    survey: surveyState.status === "success" && surveyState.completed,
    preferences: preferencesCompleted,
  };
  const [likesPage, setLikesPage] = useState(1);
  const likesTitleRef = useRef(null);
  const [likesRevision, setLikesRevision] = useState(0);
  const [likedResults, setLikedResults] = useState({
    status: "loading",
    profiles: [],
    totalElements: 0,
    totalPages: 0,
    page: 0,
    first: true,
    last: true,
    error: "",
  });
  const completeCount = Object.values(hasSurveyAnswers).filter(Boolean).length;

  const handleLikeChange = () => {
    setLikedResults((current) => ({ ...current, status: "loading" }));
    setLikesRevision((value) => value + 1);
    requestAnimationFrame(() =>
      likesTitleRef.current?.focus({ preventScroll: true }),
    );
  };

  useEffect(() => {
    if (activeTab !== "likes") return;
    const controller = new AbortController();
    setLikedResults((current) => ({
      ...current,
      status: "loading",
      error: "",
    }));
    getMyLikedRoommates(
      { page: likesPage - 1, size: LIKES_PER_PAGE },
      controller.signal,
    )
      .then((page) => {
        if (controller.signal.aborted) return;
        if (
          !Array.isArray(page?.content) ||
          !Number.isInteger(page.totalPages) ||
          page.totalPages < 0 ||
          !Number.isInteger(page.totalElements) ||
          page.totalElements < 0 ||
          page.page !== likesPage - 1 ||
          typeof page.first !== "boolean" ||
          typeof page.last !== "boolean"
        ) {
          throw new Error("관심 프로필 목록 응답을 확인할 수 없습니다.");
        }
        // A deleted last page is corrected only toward a smaller page.
        const lastPage = Math.max(1, page.totalPages);
        if (likesPage > lastPage) {
          setLikesPage(lastPage);
          return;
        }
        setLikedResults({
          ...page,
          profiles: page.content.map(toRoommateProfile),
          status: "success",
          error: "",
        });
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setLikedResults((current) => ({
            ...current,
            status: "error",
            error: error.message,
          }));
        }
      });
    return () => controller.abort();
  }, [activeTab, likesPage, likesRevision, currentUser?.id]);

  if (accountLoading)
    return (
      <S.Page>
        <p role="status">내 정보를 불러오는 중이에요.</p>
      </S.Page>
    );
  if (accountError || !account)
    return (
      <S.Page>
        <p role="alert">{accountError || "내 정보를 불러오지 못했어요."}</p>
        <button onClick={() => setAccountRevision((value) => value + 1)}>
          다시 시도
        </button>
      </S.Page>
    );

  const detailItems = [
    ["닉네임", account.nickname],
    ["성별", account.gender],
    ["만 나이", `${getInternationalAge(account.birthYear) ?? "-"}세`],
    ["생활관", `${account.dormitory}관`],
    ["학년", account.grade],
    ["나의 학부", account.faculty],
    ["나의 전공", account.major],
    ["MBTI", getMbti(account)],
  ];

  return (
    <S.Page>
      <S.Container>
        <S.Layout>
          <S.Sidebar>
            <S.SidebarProfile>
              <ProfileImage
                as={S.Avatar}
                major={account.major}
                fallbackSrc={MY_PHOTO}
                alt=""
              />
              <div>
                <S.SidebarName>{account.nickname}</S.SidebarName>
                <S.Muted>
                  {account.dormitory}관 · {getMbti(account)}
                </S.Muted>
              </div>
            </S.SidebarProfile>
            <S.Menu aria-label="마이페이지 메뉴">
              {MENU_ITEMS.map(({ id, label, icon: Icon }) => (
                <S.MenuLink
                  key={id}
                  to={id === "profile" ? "/profile" : `/profile?tab=${id}`}
                  $active={activeTab === id}
                  aria-current={activeTab === id ? "page" : undefined}
                >
                  <Icon size={19} aria-hidden="true" />
                  <span>{label}</span>
                  {activeTab === id && (
                    <ChevronRight size={16} aria-hidden="true" />
                  )}
                </S.MenuLink>
              ))}
            </S.Menu>
            <S.SideNote>
              <ShieldCheck size={20} aria-hidden="true" />
              <strong>서로를 알아가는 첫걸음</strong>
              <S.Muted>
                나의 정보와 설문을 채우고 더 편안한 기숙사 생활을 준비해요.
              </S.Muted>
            </S.SideNote>
          </S.Sidebar>
          <S.Content
            aria-label={MENU_ITEMS.find(({ id }) => id === activeTab).label}
          >
            {activeTab === "profile" && (
              <S.Stack>
                <S.SectionHeading>
                  <div>
                    <S.SectionTitle>내 프로필</S.SectionTitle>
                    <S.Description>
                      룸메이트에게 보여줄 나의 기본 정보예요.
                    </S.Description>
                  </div>
                  <S.SecondaryButton as={Link} to="/profile?tab=account">
                    <Pencil size={16} aria-hidden="true" /> 프로필 수정
                  </S.SecondaryButton>
                </S.SectionHeading>
                <S.Card>
                  <S.ProfileHero>
                    <ProfileImage
                      as={S.Avatar}
                      major={account.major}
                      $large
                      fallbackSrc={MY_PHOTO}
                      alt={`${account.nickname} 프로필`}
                    />
                    <div>
                      <S.Badge>
                        <BadgeCheck size={14} aria-hidden="true" />{" "}
                        한경국립대학교
                      </S.Badge>
                      <S.ProfileName>{account.nickname}</S.ProfileName>
                      <S.Muted>
                        {account.major} · {account.grade}
                      </S.Muted>
                      <S.TagRow>
                        <S.Tag>{account.dormitory}관</S.Tag>
                        <S.Tag>{getMbti(account)}</S.Tag>
                      </S.TagRow>
                    </div>
                  </S.ProfileHero>
                  <S.DetailGrid>
                    {detailItems.map(([label, value]) => (
                      <S.DetailItem key={label}>
                        <dt>{label}</dt>
                        <dd>{value}</dd>
                      </S.DetailItem>
                    ))}
                  </S.DetailGrid>
                </S.Card>
                <S.Card>
                  <S.CardHeader>
                    <S.CardTitle>
                      <ClipboardList size={19} aria-hidden="true" /> 나의 설문
                      현황
                    </S.CardTitle>
                    <S.Badge>{surveyState.status === "success" ? `${completeCount} / 2 완료` : surveyState.status === "error" ? "설문 조회 실패" : "설문 확인 중"}</S.Badge>
                  </S.CardHeader>
                  <S.Helper>
                    두 가지 설문으로 나와 잘 맞는 룸메이트를 더 가까이
                    만나보세요.
                  </S.Helper>
                  <S.ProgressTrack
                    role="progressbar"
                    aria-label="설문 작성 현황"
                    aria-valuemin={0}
                    aria-valuemax={2}
                    aria-valuenow={surveyState.status === "success" ? completeCount : undefined}
                  >
                    <S.ProgressFill $value={completeCount * 50} />
                  </S.ProgressTrack>
                  <S.SummaryGrid>
                    {[
                      {
                        id: "survey",
                        icon: Moon,
                        title: "내 설문",
                        text: "나의 수면 패턴과 생활 습관",
                      },
                      {
                        id: "preferences",
                        icon: Users,
                        title: "선호 룸메 설문",
                        text: "함께하고 싶은 룸메이트의 모습",
                      },
                    ].map(({ id, icon: Icon, title, text }) => (
                      <S.SummaryCard
                        as={Link}
                        key={id}
                        to={`/profile?tab=${id}`}
                        onClick={() => window.scrollTo(0, 0)}
                      >
                        <S.CardHeader>
                          <Icon size={23} aria-hidden="true" />
                          <S.Badge $muted={!hasSurveyAnswers[id]}>
                            {id === "survey" && surveyState.status !== "success"
                              ? (surveyState.status === "error" ? "조회 실패" : "확인 중")
                              : hasSurveyAnswers[id] ? "작성 완료" : "미작성"}
                          </S.Badge>
                        </S.CardHeader>
                        <S.SummaryTitle>{title}</S.SummaryTitle>
                        <S.SummaryText>{text}</S.SummaryText>
                        <S.SummaryFooter>
                          {hasSurveyAnswers[id]
                            ? "답변 확인하기"
                            : "설문 알아보기"}
                          <ArrowRight size={16} aria-hidden="true" />
                        </S.SummaryFooter>
                      </S.SummaryCard>
                    ))}
                  </S.SummaryGrid>
                </S.Card>
              </S.Stack>
            )}
            {(activeTab === "survey" || activeTab === "preferences") && (
              <>
                {activeTab === "survey" && surveyState.status !== "success" ? (
                  <S.Card>
                    <p role={surveyState.status === "error" ? "alert" : "status"}>
                      {surveyState.status === "error" ? surveyState.error : "내 설문을 불러오는 중이에요."}
                    </p>
                    {surveyState.status === "error" && <S.SecondaryButton onClick={() => setSurveyRevision((value) => value + 1)}>다시 시도</S.SecondaryButton>}
                  </S.Card>
                ) : <SurveyContent
                  key={activeTab}
                  type={activeTab}
                  answers={
                    activeTab === "survey" ? profileAnswers : preferences
                  }
                  isEmpty={!hasSurveyAnswers[activeTab]}
                  onSave={async (savedAnswers) => {
                    if (activeTab === "survey") {
                      const version = surveyRequestVersion.current;
                      const survey = await saveMyProfileSurvey(savedAnswers);
                      if (version !== surveyRequestVersion.current) return false;
                      setProfileAnswers({ ...createEmptyProfileAnswers(), ...survey.answers, introduction: survey.introduction ?? "" });
                      setSurveyState({ status: "success", completed: survey.completed, error: "" });
                    } else {
                      await saveMyPreferences(savedAnswers);
                      setPreferences(savedAnswers);
                      setPreferencesCompleted(true);
                    }
                  }}
                />}
              </>
            )}
            {activeTab === "likes" && (
              <S.Stack>
                <S.SectionHeading>
                  <div>
                    <S.SectionTitle ref={likesTitleRef} tabIndex={-1}>
                      좋아요{" "}
                      <S.Badge>
                        {likedResults.status === "success"
                          ? `${likedResults.totalElements}명`
                          : "—"}
                      </S.Badge>
                    </S.SectionTitle>
                    <S.Description>
                      마음에 담아둔 룸메이트를 다시 만나보세요.
                    </S.Description>
                  </div>
                  <S.SubtleLink to="/roommates">
                    룸메이트 둘러보기{" "}
                    <ArrowRight size={16} aria-hidden="true" />
                  </S.SubtleLink>
                </S.SectionHeading>
                {likedResults.status !== "success" ? (
                  <S.EmptyCard
                    role={likedResults.status === "error" ? "alert" : "status"}
                  >
                    <S.EmptyTitle>
                      {likedResults.status === "loading"
                        ? "관심 프로필을 불러오고 있어요"
                        : "관심 프로필을 불러오지 못했어요"}
                    </S.EmptyTitle>
                    <S.EmptyText>
                      {likedResults.error || "잠시만 기다려 주세요."}
                    </S.EmptyText>
                    {likedResults.status === "error" && (
                      <button type="button" onClick={handleLikeChange}>
                        다시 시도
                      </button>
                    )}
                  </S.EmptyCard>
                ) : likedResults.totalElements === 0 ? (
                  <EmptyState type="likes" />
                ) : (
                  <>
                    <S.LikeGrid>
                      {likedResults.profiles.map((profile) => (
                        <ProfileCard
                          key={profile.id}
                          profile={profile}
                          activeTab="all"
                          onToggleLike={handleLikeChange}
                          showOverlay={false}
                        />
                      ))}
                    </S.LikeGrid>

                    <Pagination
                      currentPage={likedResults.page + 1}
                      totalPages={likedResults.totalPages}
                      onPageChange={(nextPage) => {
                        if (
                          (likedResults.first &&
                            nextPage < likedResults.page + 1) ||
                          (likedResults.last &&
                            nextPage > likedResults.page + 1) ||
                          nextPage === likedResults.page + 1
                        )
                          return;
                        setLikedResults((current) => ({
                          ...current,
                          status: "loading",
                        }));
                        setLikesPage(nextPage);
                        window.scrollTo({ top: 0, behavior: "smooth" });
                      }}
                    />
                  </>
                )}
              </S.Stack>
            )}
            {activeTab === "account" && (
              <AccountContent account={account} onSave={saveAccount} />
            )}
          </S.Content>
        </S.Layout>
      </S.Container>
    </S.Page>
  );
}

export default MyPage;
