import { useEffect, useRef, useState } from "react";
import { Check, ChevronDown } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { FACULTY_OPTIONS } from "../../data/facultyOptions";
import {
  sendEmailVerification,
  checkSignupAvailability,
  signup,
  verifyEmailVerification,
} from "../../api/authApi";
import {
  meetsPasswordRequirements,
  PASSWORD_REQUIREMENTS_MESSAGE,
} from "../../utils/passwordValidation";
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
const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,8}$/;

const findFaculty = (name) =>
  FACULTY_OPTIONS.find((faculty) => faculty.name === name);

const SignupPage = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationSent, setVerificationSent] = useState(false);
  const [isSendingVerification, setIsSendingVerification] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const authInFlight = useRef(false);
  const emailVersion = useRef(0);
  const [isVerified, setIsVerified] = useState(false);
  const [verificationToken, setVerificationToken] = useState("");
  const [availability, setAvailability] = useState(null);
  const [availabilityError, setAvailabilityError] = useState(false);
  const [nicknameCheck, setNicknameCheck] = useState(null);
  const [nicknameCheckError, setNicknameCheckError] = useState("");
  const [isCheckingNickname, setIsCheckingNickname] = useState(false);
  const navigate = useNavigate();

  const passwordMeetsRequirements = meetsPasswordRequirements(form.password);
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
  const nicknameIsValid = NICKNAME_PATTERN.test(form.nickname);
  const schoolEmailIdIsValid = SCHOOL_EMAIL_ID_PATTERN.test(
    form.schoolEmailId.trim(),
  );
  const verificationCodeIsValid = /^\d{6}$/.test(verificationCode);

  useEffect(() => {
    const schoolEmail = schoolEmailIdIsValid
      ? `${form.schoolEmailId.trim()}${SCHOOL_EMAIL_DOMAIN}`
      : "";

    if (!schoolEmail) {
      setAvailability(null);
      setAvailabilityError(false);
      return undefined;
    }

    const timer = window.setTimeout(async () => {
      try {
        const result = await checkSignupAvailability({ schoolEmail });
        setAvailability(result);
        setAvailabilityError(false);
      } catch {
        setAvailability(null);
        setAvailabilityError(true);
      }
    }, 400);

    return () => window.clearTimeout(timer);
  }, [form.schoolEmailId, schoolEmailIdIsValid]);

  const setFormValue = (name, value) => {
    setForm((current) => {
      const nextForm = { ...current, [name]: value };

      if (name === "faculty") {
        const faculty = findFaculty(value);
        nextForm.major = faculty?.majors.length === 0 ? "세부전공 없음" : "";
      }

      return nextForm;
    });
    if (["nickname", "schoolEmailId"].includes(name)) {
      setAvailability(null);
      setAvailabilityError(false);
    }
    if (name === "nickname") {
      setNicknameCheck(null);
      setNicknameCheckError("");
    }
  };

  const handleInputChange = (event) => {
    const { name, value } = event.target;
    let nextValue = value;

    if (name === "schoolEmailId") {
      emailVersion.current += 1;
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
    }
  };

  const handleSendCode = async () => {
    if (authInFlight.current) return;
    const schoolEmailId = form.schoolEmailId.trim();

    if (!schoolEmailId) {
      window.alert("학교 이메일을 입력해 주세요.");
      return;
    }

    if (!SCHOOL_EMAIL_ID_PATTERN.test(schoolEmailId)) {
      return;
    }

    const schoolEmail = `${schoolEmailId}${SCHOOL_EMAIL_DOMAIN}`;
    const version = emailVersion.current;
    authInFlight.current = true;
    setIsSendingVerification(true);

    try {
      await sendEmailVerification(schoolEmail);
      if (version !== emailVersion.current) return;
      setVerificationCode("");
      setVerificationSent(true);
      setIsVerified(false);
      setVerificationToken("");
      window.alert("인증번호가 전송되었습니다.");
    } catch (error) {
      if (version !== emailVersion.current) return;
      if (error.message.includes("인증번호는 잠시 후 다시 요청해 주세요")) {
        window.alert(error.message);
        return;
      }
      if (error.message.includes("이미 가입된 학교 이메일")) {
        window.alert(error.message);
        return;
      }
      window.alert(error.message || "학교 이메일 중복 확인에 실패했습니다.");
    } finally {
      authInFlight.current = false;
      setIsSendingVerification(false);
    }
  };

  const handleVerifyCode = async () => {
    if (authInFlight.current) return;
    if (!/^\d{6}$/.test(verificationCode)) {
      window.alert("인증번호는 숫자 6자리로 입력해 주세요.");
      return;
    }

    const version = emailVersion.current;
    authInFlight.current = true;
    setIsVerifying(true);
    try {
      const token = await verifyEmailVerification(
        `${form.schoolEmailId.trim()}${SCHOOL_EMAIL_DOMAIN}`,
        verificationCode,
      );
      if (version !== emailVersion.current) return;
      setVerificationToken(token);
      setIsVerified(true);
      window.alert("재학생 인증이 완료되었습니다.");
    } catch (error) {
      if (version !== emailVersion.current) return;
      window.alert(error.message);
    } finally {
      authInFlight.current = false;
      setIsVerifying(false);
    }
  };

  const handleCheckNickname = async () => {
    const nickname = form.nickname.trim();

    if (!nicknameIsValid) {
      setNicknameCheck(null);
      setNicknameCheckError("");
      return;
    }

    setIsCheckingNickname(true);
    setNicknameCheck(null);
    setNicknameCheckError("");

    try {
      const result = await checkSignupAvailability({ nickname });
      setNicknameCheck({ nickname, available: result.nicknameAvailable });
    } catch {
      setNicknameCheckError(nickname);
    } finally {
      setIsCheckingNickname(false);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (authInFlight.current) return;

    const requiredValues = [
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
      window.alert("모든 항목을 작성해주세요.");
      return;
    }

    if (nicknameCheck?.nickname !== form.nickname.trim()) {
      window.alert("닉네임 중복 확인을 완료해 주세요.");
      return;
    }

    if (!nicknameCheck.available) {
      window.alert("이미 사용 중인 닉네임이에요. 다른 닉네임을 입력해 주세요.");
      return;
    }

    if (!passwordMeetsRequirements || !passwordMatches) {
      window.alert("비밀번호 조건과 비밀번호 확인을 다시 확인해 주세요.");
      return;
    }

    if (!SCHOOL_EMAIL_ID_PATTERN.test(form.schoolEmailId.trim())) {
      window.alert("학교 이메일 형식을 다시 확인해 주세요.");
      return;
    }

    if (!isVerified) {
      window.alert("학교 이메일 재학생 인증을 완료해 주세요.");
      return;
    }

    if (MBTI_DIMENSIONS.some((dimension) => !form[dimension.id])) {
      window.alert("MBTI 네 가지 기준을 모두 선택해 주세요.");
      return;
    }

    if (
      !selectedFaculty ||
      (selectedFaculty.majors.length > 0 &&
        !selectedFaculty.majors.includes(form.major))
    ) {
      window.alert("학부와 전공을 다시 확인해 주세요.");
      return;
    }

    if (!birthYearIsValid) {
      window.alert(`출생연도는 1900년부터 ${currentYear}년 사이의 네 자리 숫자로 입력해 주세요.`);
      return;
    }

    if (!studentIdIsValid) {
      window.alert("학번은 숫자 두 자리로 입력해 주세요.");
      return;
    }

    authInFlight.current = true;
    setIsSubmitting(true);
    try {
      await signup({
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
      window.alert("회원가입이 완료되었습니다. 로그인해 주세요.");
      navigate("/login");
    } catch (error) {
      window.alert(error.message);
    } finally {
      authInFlight.current = false;
      setIsSubmitting(false);
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
          <S.FormSection aria-labelledby="verification-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">1</S.SectionNumber>
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
                <S.EmailInputGroup
                  $readOnly={isVerified}
                  $tone={
                    form.schoolEmailId
                      ? schoolEmailIdIsValid
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
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
                    aria-invalid={Boolean(
                      form.schoolEmailId && !schoolEmailIdIsValid,
                    )}
                    $tone={
                      form.schoolEmailId
                        ? schoolEmailIdIsValid
                          ? "success"
                          : "error"
                        : "neutral"
                    }
                  />
                  <S.EmailDomain id="school-email-domain">
                    {SCHOOL_EMAIL_DOMAIN}
                  </S.EmailDomain>
                </S.EmailInputGroup>
                <S.OutlineButton
                  type="button"
                  onClick={handleSendCode}
                  disabled={isVerified || isSendingVerification || isVerifying || isSubmitting}
                >
                  {isVerified
                    ? "인증 완료"
                    : isSendingVerification
                      ? "전송 요청 중..."
                      : verificationSent
                        ? "재전송"
                        : "인증번호 전송"}
                </S.OutlineButton>
              </S.ActionRow>
              <S.FieldFeedback
                $tone={
                  form.schoolEmailId
                    ? schoolEmailIdIsValid && availability
                      ? availability.schoolEmailAvailable
                        ? "success"
                        : "error"
                      : schoolEmailIdIsValid
                        ? "neutral"
                        : "error"
                    : "neutral"
                }
              >
                {form.schoolEmailId
                  ? schoolEmailIdIsValid
                    ? availabilityError
                      ? "중복 확인에 실패했어요. 잠시 후 다시 입력해 주세요."
                      : availability
                        ? availability.schoolEmailAvailable
                          ? ""
                          : "이미 가입된 학교 이메일이에요."
                        : ""
                    : "이메일 아이디에는 영문, 숫자와 일부 기호만 입력할 수 있어요."
                  : ""}
              </S.FieldFeedback>
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
                    }}
                    placeholder="숫자 6자리"
                    autoComplete="one-time-code"
                    aria-invalid={Boolean(
                      verificationCode && !verificationCodeIsValid,
                    )}
                    $tone={
                      verificationCode
                        ? verificationCodeIsValid
                          ? "success"
                          : "error"
                        : "neutral"
                    }
                  />
                  <S.OutlineButton type="button" onClick={handleVerifyCode} disabled={isVerifying || isSendingVerification || isSubmitting}>
                    인증 확인
                  </S.OutlineButton>
                </S.ActionRow>
                <S.FieldFeedback
                  $tone={
                    verificationCode
                      ? verificationCodeIsValid
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
                  {verificationCode
                    ? verificationCodeIsValid
                      ? ""
                      : "인증번호는 숫자 6자리로 입력해 주세요."
                    : ""}
                </S.FieldFeedback>
              </S.FieldGroup>
            )}

          </S.FormSection>

          <S.Divider />

          <S.FormSection aria-labelledby="account-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">2</S.SectionNumber>
              <S.SectionTitle id="account-section-title">
                계정 정보
              </S.SectionTitle>
            </S.SectionHeader>

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
                    placeholder="비밀번호 입력"
                    autoComplete="new-password"
                    required
                    aria-describedby="password-guide"
                    aria-invalid={Boolean(
                      form.password && !passwordMeetsRequirements,
                    )}
                    $hasAction
                    $tone={
                      form.password
                        ? passwordMeetsRequirements
                          ? "success"
                          : "error"
                        : "neutral"
                    }
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
                      ? passwordMeetsRequirements
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
                  {form.password
                    ? passwordMeetsRequirements
                      ? "사용할 수 있는 비밀번호예요."
                      : PASSWORD_REQUIREMENTS_MESSAGE
                    : PASSWORD_REQUIREMENTS_MESSAGE}
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
                    placeholder="비밀번호 확인"
                    autoComplete="new-password"
                    required
                    aria-describedby="password-confirm-guide"
                    aria-invalid={Boolean(
                      form.passwordConfirm && !passwordMatches,
                    )}
                    $hasAction
                    $tone={
                      form.passwordConfirm
                        ? passwordMatches
                          ? "success"
                          : "error"
                        : "neutral"
                    }
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
              <S.ActionRow>
                <S.Input
                  id="signup-nickname"
                  name="nickname"
                  type="text"
                  value={form.nickname}
                  onChange={handleInputChange}
                  placeholder="닉네임 입력"
                  autoComplete="nickname"
                  maxLength={8}
                  required
                  aria-invalid={Boolean(form.nickname && !nicknameIsValid)}
                  $tone={
                    form.nickname
                      ? !nicknameIsValid
                        ? "error"
                        : nicknameCheck?.nickname === form.nickname.trim()
                          ? nicknameCheck.available
                            ? "success"
                            : "error"
                          : "neutral"
                      : "neutral"
                  }
                />
                <S.OutlineButton
                  type="button"
                  onClick={handleCheckNickname}
                  disabled={isCheckingNickname}
                >
                  {isCheckingNickname ? "확인 중..." : "중복 확인"}
                </S.OutlineButton>
              </S.ActionRow>
              <S.FieldFeedback
                $tone={
                  form.nickname
                    ? nicknameIsValid
                      ? nicknameCheckError === form.nickname.trim()
                        ? "error"
                        : nicknameCheck?.nickname === form.nickname.trim()
                          ? nicknameCheck.available
                            ? "success"
                            : "error"
                          : "neutral"
                      : "error"
                    : "neutral"
                }
              >
                {form.nickname
                  ? nicknameIsValid
                    ? nicknameCheckError === form.nickname.trim()
                      ? "중복 확인에 실패했어요. 다시 시도해 주세요."
                      : nicknameCheck?.nickname === form.nickname.trim()
                        ? nicknameCheck.available
                          ? "사용할 수 있는 닉네임이에요."
                          : "이미 사용 중인 닉네임이에요."
                        : "중복 확인 버튼을 눌러 주세요."
                    : "닉네임은 한글, 영문, 숫자만 사용해 2~8자로 입력해 주세요."
                  : "한글, 영문, 숫자만 사용해 2~8자로 입력해 주세요."}
              </S.FieldFeedback>
            </S.FieldGroup>
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
                <S.SelectShell $tone={form.faculty ? "success" : "neutral"}>
                  <S.Select
                    id="signup-faculty"
                    name="faculty"
                    value={form.faculty}
                    onChange={handleInputChange}
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
              </S.FieldGroup>

              <S.FieldGroup>
                <S.Label htmlFor="signup-major">나의 전공</S.Label>
                <S.SelectShell
                  $disabled={!selectedFaculty || hasNoMajor}
                  $tone={form.major ? "success" : "neutral"}
                >
                  <S.Select
                    id="signup-major"
                    name="major"
                    value={form.major}
                    onChange={handleInputChange}
                    disabled={!selectedFaculty || hasNoMajor}
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
                  $tone={
                    form.birthYear
                      ? birthYearIsValid
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                />
                <S.FieldFeedback
                  id="birth-year-guide"
                  $tone={
                    form.birthYear
                      ? birthYearIsValid
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
                  {form.birthYear
                    ? birthYearIsValid
                      ? ""
                      : "출생연도를 확인해 주세요."
                    : ""}
                </S.FieldFeedback>
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
                  $tone={
                    form.studentId
                      ? studentIdIsValid
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                />
                <S.FieldFeedback
                  id="student-id-guide"
                  $tone={
                    form.studentId
                      ? studentIdIsValid
                        ? "success"
                        : "error"
                      : "neutral"
                  }
                >
                  {form.studentId
                    ? studentIdIsValid
                      ? ""
                      : "학번 형식을 확인해 주세요."
                    : ""}
                </S.FieldFeedback>
              </S.FieldGroup>
            </S.TwoColumnFields>

            {renderChoiceField("grade", "학년", GRADE_OPTIONS)}

            <S.MbtiFieldset>
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
            </S.MbtiFieldset>
          </S.FormSection>

          <S.SubmitButton type="submit" disabled={isSubmitting || isSendingVerification || isVerifying}>
            {isSubmitting ? "가입 중..." : "회원가입"}
          </S.SubmitButton>
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
