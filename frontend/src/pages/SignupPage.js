import { useState } from "react";
import * as S from "./SignupPageCss";

const INITIAL_FORM = {
  loginId: "",
  password: "",
  passwordConfirm: "",
  nickname: "",
  schoolEmailId: "",
};

const SCHOOL_EMAIL_DOMAIN = "@hknu.ac.kr";
const SCHOOL_EMAIL_ID_PATTERN =
  /^[A-Za-z0-9_%+-]+(?:\.[A-Za-z0-9_%+-]+)*$/;

const SignupPage = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationSent, setVerificationSent] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [verificationFeedback, setVerificationFeedback] = useState(null);
  const [submitFeedback, setSubmitFeedback] = useState(null);

  const passwordIsLongEnough = form.password.length >= 8;
  const passwordMatches =
    form.passwordConfirm.length > 0 && form.password === form.passwordConfirm;

  const handleInputChange = (event) => {
    const { name, value } = event.target;
    const nextValue =
      name === "schoolEmailId"
        ? value.split("@")[0].replace(/\s/g, "").slice(0, 64)
        : value;

    setForm((current) => ({ ...current, [name]: nextValue }));
    setSubmitFeedback(null);

    if (name === "schoolEmailId") {
      setVerificationCode("");
      setVerificationSent(false);
      setIsVerified(false);
      setVerificationFeedback(null);
    }
  };

  const handleSendCode = () => {
    const schoolEmailId = form.schoolEmailId.trim();

    if (!SCHOOL_EMAIL_ID_PATTERN.test(schoolEmailId)) {
      setVerificationFeedback({
        tone: "error",
        text: "학교 이메일의 @ 앞부분을 정확히 입력해 주세요.",
      });
      return;
    }

    setVerificationCode("");
    setVerificationSent(true);
    setIsVerified(false);
    setVerificationFeedback({
      tone: "info",
      text: `${schoolEmailId}${SCHOOL_EMAIL_DOMAIN}로 인증번호를 전송했어요. 화면 확인용으로 숫자 6자리를 입력해 주세요.`,
    });
  };

  const handleVerifyCode = () => {
    if (!/^\d{6}$/.test(verificationCode)) {
      setVerificationFeedback({
        tone: "error",
        text: "인증번호 숫자 6자리를 입력해 주세요.",
      });
      return;
    }

    setIsVerified(true);
    setVerificationFeedback({
      tone: "success",
      text: "재학생 인증이 완료되었습니다.",
    });
    setSubmitFeedback(null);
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    const requiredValues = [
      form.loginId,
      form.password,
      form.passwordConfirm,
      form.nickname,
      form.schoolEmailId,
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

    setSubmitFeedback({
      tone: "success",
      text: "가입 정보를 모두 확인했어요. 현재 화면에서는 실제 회원가입이 진행되지 않습니다.",
    });
  };

  return (
    <S.Page>
      <S.SignupCard aria-labelledby="signup-title">
        <S.Header>
          <S.Eyebrow>JOIN OUR COMMUNITY</S.Eyebrow>
          <S.Title id="signup-title">룸메이트 찾기를 시작해요</S.Title>
          <S.Description>
            기본 정보를 입력하면 나와 잘 맞는 룸메이트를 만날 준비가
            완료됩니다.
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
                    onClick={() =>
                      setShowPassword((current) => !current)
                    }
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
                <S.Label htmlFor="signup-verification-code">
                  인증번호
                </S.Label>
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
              role={
                verificationFeedback?.tone === "error" ? "alert" : "status"
              }
              aria-live={
                verificationFeedback?.tone === "error" ? "assertive" : "polite"
              }
              $tone={verificationFeedback?.tone || "neutral"}
            >
              {verificationFeedback?.text ||
                "학교 이메일 입력 후 인증번호를 받아 주세요."}
            </S.VerificationStatus>
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
