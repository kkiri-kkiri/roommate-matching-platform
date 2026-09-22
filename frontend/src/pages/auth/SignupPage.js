import { useState } from "react";
import { Check, ChevronDown } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { FACULTY_OPTIONS } from "../../data/facultyOptions";
import {
  sendEmailVerification,
  signup,
  verifyEmailVerification,
} from "../../api/authApi";
import * as S from "./SignupPageCss";

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

const GENDER_OPTIONS = ["남자", "여자"];
const DORMITORY_OPTIONS = ["나래", "호연", "비봉", "창조"];
const GRADE_OPTIONS = ["1학년", "2학년", "3학년", "4학년", "5학년"];

const INITIAL_FORM = {
  loginId: "",
  password: "",
  passwordConfirm: "",
  nickname: "",
  schoolEmailId: "",
  gender: "",
  dormitory: "",
  faculty: "",
  major: "",
  birthYear: "",
  studentId: "",
  grade: "",
  mbtiEnergy: "",
  mbtiInformation: "",
  mbtiDecision: "",
  mbtiLifestyle: "",
};

const SCHOOL_EMAIL_DOMAIN = "@hknu.ac.kr";
const SCHOOL_EMAIL_ID_PATTERN = /^[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*$/;
const STUDENT_ID_PATTERN = /^\d{2}$/;

const findFaculty = (name) =>
  FACULTY_OPTIONS.find((faculty) => faculty.name === name);

const SignupPage = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationSent, setVerificationSent] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [verificationToken, setVerificationToken] = useState("");
  const [verificationFeedback, setVerificationFeedback] = useState(null);
  const [submitFeedback, setSubmitFeedback] = useState(null);
  const navigate = useNavigate();

  const passwordIsLongEnough = form.password.length >= 8;
  const passwordMatches =
    form.passwordConfirm.length > 0 && form.password === form.passwordConfirm;
  const selectedFaculty = findFaculty(form.faculty);
  const majorOptions = selectedFaculty?.majors || [];
  const hasNoMajor = Boolean(selectedFaculty && majorOptions.length === 0);
  const mbti = MBTI_DIMENSIONS.map((dimension) => form[dimension.id]).join("");
  const currentYear = new Date().getFullYear();
  const birthYear = Number(form.birthYear);
  const birthYearIsValid =
    /^\d{4}$/.test(form.birthYear) &&
    birthYear >= 1900 &&
    birthYear <= currentYear;
  const studentIdIsValid = STUDENT_ID_PATTERN.test(form.studentId);

  const setFormValue = (name, value) => {
    setForm((current) => {
      const nextForm = { ...current, [name]: value };

      if (name === "faculty") {
        const faculty = findFaculty(value);
        nextForm.major = faculty?.majors.length === 0 ? "세부전공 없음" : "";
      }

      return nextForm;
    });
    setSubmitFeedback(null);
  };

  const handleInputChange = (event) => {
    const { name, value } = event.target;
    let nextValue = value;

    if (name === "schoolEmailId") {
      nextValue = value.split("@")[0].replace(/\s/g, "").slice(0, 64);
    } else if (name === "birthYear") {
      nextValue = value.replace(/\D/g, "").slice(0, 4);
    } else if (name === "studentId") {
      nextValue = value.replace(/\D/g, "").slice(0, 2);
    }

    setFormValue(name, nextValue);

    if (name === "schoolEmailId") {
      setVerificationCode("");
      setVerificationSent(false);
      setIsVerified(false);
      setVerificationToken("");
      setVerificationFeedback(null);
    }
  };

  const handleSendCode = async () => {
    const schoolEmailId = form.schoolEmailId.trim();

    if (!SCHOOL_EMAIL_ID_PATTERN.test(schoolEmailId)) {
      setVerificationFeedback({
        tone: "error",
        text: "학교 이메일의 @ 앞부분을 정확히 입력해 주세요.",
      });
      return;
    }

    try {
      await sendEmailVerification(`${schoolEmailId}${SCHOOL_EMAIL_DOMAIN}`);
      setVerificationCode("");
      setVerificationSent(true);
      setIsVerified(false);
      setVerificationToken("");
      setVerificationFeedback({
        tone: "info",
        text: `${schoolEmailId}${SCHOOL_EMAIL_DOMAIN}로 인증번호를 전송했어요.`,
      });
    } catch (error) {
      setVerificationFeedback({ tone: "error", text: error.message });
    }
  };

  const handleVerifyCode = async () => {
    if (!/^\d{6}$/.test(verificationCode)) {
      setVerificationFeedback({
        tone: "error",
        text: "인증번호 숫자 6자리를 입력해 주세요.",
      });
      return;
    }

    try {
      const token = await verifyEmailVerification(
        `${form.schoolEmailId.trim()}${SCHOOL_EMAIL_DOMAIN}`,
        verificationCode,
      );
      setVerificationToken(token);
      setIsVerified(true);
      setVerificationFeedback({
        tone: "success",
        text: "재학생 인증이 완료되었습니다.",
      });
      setSubmitFeedback(null);
    } catch (error) {
      setVerificationFeedback({ tone: "error", text: error.message });
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const requiredValues = [
      form.loginId,
      form.password,
      form.passwordConfirm,
      form.nickname,
      form.schoolEmailId,
      form.gender,
      form.dormitory,
      form.faculty,
      form.major,
      form.birthYear,
      form.studentId,
      form.grade,
    ];

    if (requiredValues.some((value) => !value.trim())) {
      setSubmitFeedback({
        tone: "error",
        text: "필수 입력 항목을 모두 작성해 주세요.",
      });
      return;
    }

    if (!passwordIsLongEnough || !passwordMatches) {
      setSubmitFeedback({
        tone: "error",
        text: "비밀번호 조건과 비밀번호 확인을 다시 확인해 주세요.",
      });
      return;
    }

    if (!SCHOOL_EMAIL_ID_PATTERN.test(form.schoolEmailId.trim())) {
      setSubmitFeedback({
        tone: "error",
        text: "학교 이메일 형식을 다시 확인해 주세요.",
      });
      return;
    }

    if (!isVerified) {
      setSubmitFeedback({
        tone: "error",
        text: "학교 이메일 재학생 인증을 완료해 주세요.",
      });
      return;
    }

    if (MBTI_DIMENSIONS.some((dimension) => !form[dimension.id])) {
      setSubmitFeedback({
        tone: "error",
        text: "MBTI 네 가지 기준을 모두 선택해 주세요.",
      });
      return;
    }

    if (
      !selectedFaculty ||
      (selectedFaculty.majors.length > 0 &&
        !selectedFaculty.majors.includes(form.major))
    ) {
      setSubmitFeedback({
        tone: "error",
        text: "학부와 전공을 다시 확인해 주세요.",
      });
      return;
    }

    if (!birthYearIsValid) {
      setSubmitFeedback({
        tone: "error",
        text: `출생연도는 1900년부터 ${currentYear}년 사이의 네 자리 숫자로 입력해 주세요.`,
      });
      return;
    }

    if (!studentIdIsValid) {
      setSubmitFeedback({
        tone: "error",
        text: "학번은 숫자 두 자리로 입력해 주세요.",
      });
      return;
    }

    try {
      await signup({
        loginId: form.loginId.trim(),
        password: form.password,
        passwordConfirm: form.passwordConfirm,
        nickname: form.nickname.trim(),
        schoolEmail: `${form.schoolEmailId.trim()}${SCHOOL_EMAIL_DOMAIN}`,
        emailVerificationToken: verificationToken,
        gender: form.gender,
        dormitory: form.dormitory,
        faculty: form.faculty,
        major: form.major,
        birthYear: Number(form.birthYear),
        admissionYear: form.studentId,
        grade: form.grade,
        mbtiEnergy: form.mbtiEnergy,
        mbtiInformation: form.mbtiInformation,
        mbtiDecision: form.mbtiDecision,
        mbtiLifestyle: form.mbtiLifestyle,
      });
      setSubmitFeedback({
        tone: "success",
        text: "회원가입이 완료되었습니다. 로그인해 주세요.",
      });
      navigate("/login");
    } catch (error) {
      setSubmitFeedback({ tone: "error", text: error.message });
    }
  };

  const renderChoiceField = (name, label, options) => (
    <S.ChoiceFieldset>
      <S.ChoiceLegend>{label}</S.ChoiceLegend>
      <S.ChoiceGrid>
        {options.map((option, optionIndex) => {
          const inputId = `signup-${name}-${optionIndex}`;
          const selected = form[name] === option;

          return (
            <S.ChoiceLabel key={option} htmlFor={inputId} $selected={selected}>
              <S.HiddenChoiceInput
                id={inputId}
                type="radio"
                name={name}
                value={option}
                checked={selected}
                onChange={() => setFormValue(name, option)}
                required
              />
              <S.ChoiceIndicator $selected={selected}>
                {selected && <Check size={13} aria-hidden="true" />}
              </S.ChoiceIndicator>
              <span>{option}</span>
            </S.ChoiceLabel>
          );
        })}
      </S.ChoiceGrid>
    </S.ChoiceFieldset>
  );

  return (
    <S.Page>
      <S.SignupCard aria-labelledby="signup-title">
        <S.Header>
          <S.Eyebrow>JOIN OUR COMMUNITY</S.Eyebrow>
          <S.Title id="signup-title">룸메이트 찾기를 시작해요</S.Title>
          <S.Description>
            기본 정보를 입력하면 나와 잘 맞는 룸메이트를 만날 준비가 완료됩니다.
          </S.Description>
        </S.Header>

        <S.Form onSubmit={handleSubmit} noValidate>
          <S.FormSection aria-labelledby="account-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">1</S.SectionNumber>
              <S.SectionTitle id="account-section-title">
                계정 정보
              </S.SectionTitle>
            </S.SectionHeader>

            <S.FieldGroup>
              <S.Label htmlFor="signup-id">아이디</S.Label>
              <S.Input
                id="signup-id"
                name="loginId"
                type="text"
                value={form.loginId}
                onChange={handleInputChange}
                placeholder="사용할 아이디를 입력해 주세요"
                autoComplete="username"
                required
              />
              <S.HelperText>영문과 숫자를 조합해 입력해 주세요.</S.HelperText>
            </S.FieldGroup>

            <S.TwoColumnFields>
              <S.FieldGroup>
                <S.Label htmlFor="signup-password">비밀번호</S.Label>
                <S.PasswordField>
                  <S.Input
                    id="signup-password"
                    name="password"
                    type={showPassword ? "text" : "password"}
                    value={form.password}
                    onChange={handleInputChange}
                    placeholder="8자 이상 입력"
                    autoComplete="new-password"
                    required
                    aria-describedby="password-guide"
                    aria-invalid={Boolean(
                      form.password && !passwordIsLongEnough,
                    )}
                    $hasAction
                  />
                  <S.VisibilityButton
                    type="button"
                    onClick={() => setShowPassword((current) => !current)}
                    aria-label={
                      showPassword ? "비밀번호 숨기기" : "비밀번호 표시하기"
                    }
                    aria-pressed={showPassword}
                  >
                    {showPassword ? "숨기기" : "보기"}
                  </S.VisibilityButton>
                </S.PasswordField>
                <S.FieldFeedback
                  id="password-guide"
                  aria-live="polite"
                  $tone={
                    form.password
                      ? passwordIsLongEnough
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
                  {form.password
                    ? passwordIsLongEnough
                      ? "사용할 수 있는 비밀번호예요."
                      : "8자 이상 입력해 주세요."
                    : "8자 이상 입력해 주세요."}
                </S.FieldFeedback>
              </S.FieldGroup>

              <S.FieldGroup>
                <S.Label htmlFor="signup-password-confirm">
                  비밀번호 확인
                </S.Label>
                <S.PasswordField>
                  <S.Input
                    id="signup-password-confirm"
                    name="passwordConfirm"
                    type={showPasswordConfirm ? "text" : "password"}
                    value={form.passwordConfirm}
                    onChange={handleInputChange}
                    placeholder="한 번 더 입력"
                    autoComplete="new-password"
                    required
                    aria-describedby="password-confirm-guide"
                    aria-invalid={Boolean(
                      form.passwordConfirm && !passwordMatches,
                    )}
                    $hasAction
                  />
                  <S.VisibilityButton
                    type="button"
                    onClick={() =>
                      setShowPasswordConfirm((current) => !current)
                    }
                    aria-label={
                      showPasswordConfirm
                        ? "비밀번호 확인 값 숨기기"
                        : "비밀번호 확인 값 표시하기"
                    }
                    aria-pressed={showPasswordConfirm}
                  >
                    {showPasswordConfirm ? "숨기기" : "보기"}
                  </S.VisibilityButton>
                </S.PasswordField>
                <S.FieldFeedback
                  id="password-confirm-guide"
                  aria-live="polite"
                  $tone={
                    form.passwordConfirm
                      ? passwordMatches
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
                  {form.passwordConfirm
                    ? passwordMatches
                      ? "비밀번호가 일치해요."
                      : "비밀번호가 일치하지 않아요."
                    : "같은 비밀번호를 입력해 주세요."}
                </S.FieldFeedback>
              </S.FieldGroup>
            </S.TwoColumnFields>

            <S.FieldGroup>
              <S.Label htmlFor="signup-nickname">닉네임</S.Label>
              <S.Input
                id="signup-nickname"
                name="nickname"
                type="text"
                value={form.nickname}
                onChange={handleInputChange}
                placeholder="표시할 닉네임"
                autoComplete="nickname"
                required
              />
            </S.FieldGroup>
          </S.FormSection>

          <S.Divider />

          <S.FormSection aria-labelledby="verification-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">2</S.SectionNumber>
              <S.SectionHeadingGroup>
                <S.SectionTitle id="verification-section-title">
                  재학생 인증
                </S.SectionTitle>
                <S.SectionDescription>
                  한경국립대학교 학교 이메일로 재학생 여부를 확인합니다.
                </S.SectionDescription>
              </S.SectionHeadingGroup>
            </S.SectionHeader>

            <S.FieldGroup>
              <S.Label htmlFor="signup-school-email">학교 이메일</S.Label>
              <S.ActionRow>
                <S.EmailInputGroup $readOnly={isVerified}>
                  <S.EmailLocalInput
                    id="signup-school-email"
                    name="schoolEmailId"
                    type="text"
                    inputMode="email"
                    value={form.schoolEmailId}
                    onChange={handleInputChange}
                    placeholder="학교 이메일 아이디"
                    autoComplete="email"
                    maxLength={64}
                    required
                    readOnly={isVerified}
                    aria-describedby="school-email-domain verification-status"
                  />
                  <S.EmailDomain id="school-email-domain">
                    {SCHOOL_EMAIL_DOMAIN}
                  </S.EmailDomain>
                </S.EmailInputGroup>
                <S.OutlineButton
                  type="button"
                  onClick={handleSendCode}
                  disabled={isVerified}
                >
                  {isVerified
                    ? "인증 완료"
                    : verificationSent
                      ? "재전송"
                      : "인증번호 전송"}
                </S.OutlineButton>
              </S.ActionRow>
              <S.HelperText>
                학교 이메일에서 @ 앞부분만 입력해 주세요.
              </S.HelperText>
            </S.FieldGroup>

            {verificationSent && !isVerified && (
              <S.FieldGroup>
                <S.Label htmlFor="signup-verification-code">인증번호</S.Label>
                <S.ActionRow>
                  <S.Input
                    id="signup-verification-code"
                    name="verificationCode"
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={verificationCode}
                    onChange={(event) => {
                      setVerificationCode(
                        event.target.value.replace(/\D/g, "").slice(0, 6),
                      );
                      setVerificationFeedback({
                        tone: "info",
                        text: "인증번호 숫자 6자리를 입력해 주세요.",
                      });
                    }}
                    placeholder="숫자 6자리"
                    autoComplete="one-time-code"
                  />
                  <S.OutlineButton type="button" onClick={handleVerifyCode}>
                    인증 확인
                  </S.OutlineButton>
                </S.ActionRow>
              </S.FieldGroup>
            )}

            <S.VerificationStatus
              id="verification-status"
              role={verificationFeedback?.tone === "error" ? "alert" : "status"}
              aria-live={
                verificationFeedback?.tone === "error" ? "assertive" : "polite"
              }
              $tone={verificationFeedback?.tone || "neutral"}
            >
              {verificationFeedback?.text ||
                "학교 이메일 입력 후 인증번호를 받아 주세요."}
            </S.VerificationStatus>
          </S.FormSection>

          <S.Divider />

          <S.FormSection aria-labelledby="profile-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">3</S.SectionNumber>
              <S.SectionHeadingGroup>
                <S.SectionTitle id="profile-section-title">
                  내 정보
                </S.SectionTitle>
                <S.SectionDescription>
                  기본 정보와 학적 정보를 입력해 주세요.
                </S.SectionDescription>
              </S.SectionHeadingGroup>
            </S.SectionHeader>

            {renderChoiceField("gender", "성별", GENDER_OPTIONS)}
            {renderChoiceField("dormitory", "생활관", DORMITORY_OPTIONS)}

            <S.TwoColumnFields>
              <S.FieldGroup>
                <S.Label htmlFor="signup-faculty">나의 학부</S.Label>
                <S.SelectShell>
                  <S.Select
                    id="signup-faculty"
                    name="faculty"
                    value={form.faculty}
                    onChange={handleInputChange}
                    aria-describedby="faculty-guide"
                    required
                  >
                    <option value="">학부 선택</option>
                    {FACULTY_OPTIONS.map((faculty) => (
                      <option key={faculty.name} value={faculty.name}>
                        {faculty.name}
                      </option>
                    ))}
                  </S.Select>
                  <ChevronDown size={18} aria-hidden="true" />
                </S.SelectShell>
                <S.HelperText id="faculty-guide">
                  학부를 먼저 선택해 주세요.
                </S.HelperText>
              </S.FieldGroup>

              <S.FieldGroup>
                <S.Label htmlFor="signup-major">나의 전공</S.Label>
                <S.SelectShell $disabled={!selectedFaculty || hasNoMajor}>
                  <S.Select
                    id="signup-major"
                    name="major"
                    value={form.major}
                    onChange={handleInputChange}
                    disabled={!selectedFaculty || hasNoMajor}
                    aria-describedby="major-guide"
                    required
                  >
                    {hasNoMajor ? (
                      <option value="세부전공 없음">세부전공 없음</option>
                    ) : (
                      <option value="">
                        {selectedFaculty
                          ? "전공 선택"
                          : "학부를 먼저 선택해 주세요"}
                      </option>
                    )}
                    {majorOptions.map((major) => (
                      <option key={major} value={major}>
                        {major}
                      </option>
                    ))}
                  </S.Select>
                  <ChevronDown size={18} aria-hidden="true" />
                </S.SelectShell>
                <S.HelperText id="major-guide">
                  {hasNoMajor
                    ? "선택한 학부는 별도 세부전공이 없어요."
                    : "선택한 학부의 전공만 표시돼요."}
                </S.HelperText>
              </S.FieldGroup>
            </S.TwoColumnFields>

            <S.TwoColumnFields>
              <S.FieldGroup>
                <S.Label htmlFor="signup-birth-year">출생연도</S.Label>
                <S.Input
                  id="signup-birth-year"
                  name="birthYear"
                  type="text"
                  inputMode="numeric"
                  value={form.birthYear}
                  onChange={handleInputChange}
                  placeholder="예: 2005"
                  autoComplete="bday-year"
                  maxLength={4}
                  pattern="[0-9]{4}"
                  required
                  aria-invalid={Boolean(form.birthYear && !birthYearIsValid)}
                  aria-describedby="birth-year-guide"
                />
                <S.HelperText id="birth-year-guide">
                  1900년부터 {currentYear}년 사이의 네 자리 숫자를 입력해
                  주세요.
                </S.HelperText>
              </S.FieldGroup>

              <S.FieldGroup>
                <S.Label htmlFor="signup-student-id">학번</S.Label>
                <S.Input
                  id="signup-student-id"
                  name="studentId"
                  type="text"
                  inputMode="numeric"
                  value={form.studentId}
                  onChange={handleInputChange}
                  placeholder="예: 25"
                  autoComplete="off"
                  maxLength={2}
                  pattern={STUDENT_ID_PATTERN.source}
                  required
                  aria-invalid={Boolean(form.studentId && !studentIdIsValid)}
                  aria-describedby="student-id-guide"
                />
                <S.HelperText id="student-id-guide">
                  학번은 숫자 두 자리로 입력해 주세요.
                </S.HelperText>
              </S.FieldGroup>
            </S.TwoColumnFields>

            {renderChoiceField("grade", "학년", GRADE_OPTIONS)}

            <S.MbtiFieldset aria-describedby="mbti-guide">
              <S.ChoiceLegend>MBTI</S.ChoiceLegend>
              <S.MbtiLayout>
                <S.MbtiGrid>
                  {MBTI_DIMENSIONS.map((dimension) => (
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
                        {dimension.options.map((option) => {
                          const selected = form[dimension.id] === option;
                          return (
                            <S.MbtiButton
                              key={option}
                              type="button"
                              $selected={selected}
                              aria-label={`${dimension.label} ${option}`}
                              aria-pressed={selected}
                              onClick={() => setFormValue(dimension.id, option)}
                            >
                              {option}
                            </S.MbtiButton>
                          );
                        })}
                      </S.MbtiChoices>
                    </S.MbtiAxis>
                  ))}
                </S.MbtiGrid>
                <S.MbtiPreview aria-live="polite">
                  <span>나의 MBTI</span>
                  <strong>{mbti.length === 4 ? mbti : "— — — —"}</strong>
                  <small>
                    {mbti.length === 4
                      ? "선택 완료"
                      : "네 기준을 선택해 주세요"}
                  </small>
                </S.MbtiPreview>
              </S.MbtiLayout>
              <S.HelperText id="mbti-guide">
                네 가지 기준을 모두 선택해 주세요.
              </S.HelperText>
            </S.MbtiFieldset>
          </S.FormSection>

          {submitFeedback && (
            <S.SubmitFeedback
              role={submitFeedback.tone === "error" ? "alert" : "status"}
              aria-live={
                submitFeedback.tone === "error" ? "assertive" : "polite"
              }
              $tone={submitFeedback.tone}
            >
              {submitFeedback.text}
            </S.SubmitFeedback>
          )}

          <S.SubmitButton type="submit">회원가입</S.SubmitButton>
        </S.Form>

        <S.LoginPrompt>
          이미 계정이 있으신가요?
          <S.LoginLink to="/login">로그인</S.LoginLink>
        </S.LoginPrompt>
      </S.SignupCard>
    </S.Page>
  );
};

export default SignupPage;
