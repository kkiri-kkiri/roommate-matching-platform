import { useState } from "react";
import * as S from "./SignupPageCss";

const INITIAL_FORM = {
  loginId: "",
  password: "",
  passwordConfirm: "",
  nickname: "",
  email: "",
  phone: "",
};

const INITIAL_TERMS = {
  age: false,
  service: false,
  privacy: false,
  marketing: false,
};

const SignupPage = () => {
  const [form, setForm] = useState(INITIAL_FORM);
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationSent, setVerificationSent] = useState(false);
  const [isVerified, setIsVerified] = useState(false);
  const [verificationFeedback, setVerificationFeedback] = useState(null);
  const [terms, setTerms] = useState(INITIAL_TERMS);
  const [submitFeedback, setSubmitFeedback] = useState(null);

  const passwordIsLongEnough = form.password.length >= 8;
  const passwordMatches =
    form.passwordConfirm.length > 0 && form.password === form.passwordConfirm;
  const allTermsChecked = Object.values(terms).every(Boolean);
  const requiredTermsChecked = terms.age && terms.service && terms.privacy;

  const handleInputChange = (event) => {
    const { name, value } = event.target;

    setForm((current) => ({ ...current, [name]: value }));
    setSubmitFeedback(null);

    if (name === "phone") {
      setVerificationCode("");
      setVerificationSent(false);
      setIsVerified(false);
      setVerificationFeedback(null);
    }
  };

  const handleSendCode = () => {
    const phoneDigits = form.phone.replace(/\D/g, "");

    if (phoneDigits.length < 10 || phoneDigits.length > 11) {
      setVerificationFeedback({
        tone: "error",
        text: "휴대전화 번호를 정확히 입력해 주세요.",
      });
      return;
    }

    setVerificationCode("");
    setVerificationSent(true);
    setIsVerified(false);
    setVerificationFeedback({
      tone: "info",
      text: "인증번호를 전송했어요. 화면 확인용으로 숫자 6자리를 입력해 주세요.",
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
      text: "본인인증이 완료되었습니다.",
    });
    setSubmitFeedback(null);
  };

  const handleAllTerms = (event) => {
    const { checked } = event.target;
    setTerms({
      age: checked,
      service: checked,
      privacy: checked,
      marketing: checked,
    });
    setSubmitFeedback(null);
  };

  const handleTermChange = (event) => {
    const { name, checked } = event.target;
    setTerms((current) => ({ ...current, [name]: checked }));
    setSubmitFeedback(null);
  };

  const handleSubmit = (event) => {
    event.preventDefault();

    const requiredValues = [
      form.loginId,
      form.password,
      form.passwordConfirm,
      form.nickname,
      form.email,
      form.phone,
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

    if (!/^\S+@\S+\.\S+$/.test(form.email)) {
      setSubmitFeedback({
        tone: "error",
        text: "올바른 이메일 형식으로 입력해 주세요.",
      });
      return;
    }

    if (!isVerified) {
      setSubmitFeedback({
        tone: "error",
        text: "휴대전화 본인인증을 완료해 주세요.",
      });
      return;
    }

    if (!requiredTermsChecked) {
      setSubmitFeedback({
        tone: "error",
        text: "필수 약관에 모두 동의해 주세요.",
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

            <S.TwoColumnFields>
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

              <S.FieldGroup>
                <S.Label htmlFor="signup-email">이메일</S.Label>
                <S.Input
                  id="signup-email"
                  name="email"
                  type="email"
                  value={form.email}
                  onChange={handleInputChange}
                  placeholder="name@example.com"
                  autoComplete="email"
                  required
                />
              </S.FieldGroup>
            </S.TwoColumnFields>
          </S.FormSection>

          <S.Divider />

          <S.FormSection aria-labelledby="verification-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">2</S.SectionNumber>
              <S.SectionHeadingGroup>
                <S.SectionTitle id="verification-section-title">
                  본인인증
                </S.SectionTitle>
                <S.SectionDescription>
                  안전한 이용을 위해 휴대전화 번호를 확인합니다.
                </S.SectionDescription>
              </S.SectionHeadingGroup>
            </S.SectionHeader>

            <S.FieldGroup>
              <S.Label htmlFor="signup-phone">휴대전화 번호</S.Label>
              <S.ActionRow>
                <S.Input
                  id="signup-phone"
                  name="phone"
                  type="tel"
                  inputMode="numeric"
                  value={form.phone}
                  onChange={handleInputChange}
                  placeholder="01012345678"
                  autoComplete="tel"
                  required
                  readOnly={isVerified}
                  aria-describedby="verification-status"
                />
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
                "휴대전화 번호 입력 후 인증번호를 받아 주세요."}
            </S.VerificationStatus>
          </S.FormSection>

          <S.Divider />

          <S.TermsSection aria-labelledby="terms-section-title">
            <S.SectionHeader>
              <S.SectionNumber aria-hidden="true">3</S.SectionNumber>
              <S.SectionTitle id="terms-section-title">약관 동의</S.SectionTitle>
            </S.SectionHeader>

            <S.AllAgreeLabel>
              <S.Checkbox
                type="checkbox"
                checked={allTermsChecked}
                onChange={handleAllTerms}
              />
              <S.AllAgreeText>
                전체 동의
                <S.AllAgreeDescription>
                  선택 항목을 포함한 모든 약관에 동의합니다.
                </S.AllAgreeDescription>
              </S.AllAgreeText>
            </S.AllAgreeLabel>

            <S.TermList>
              <S.TermLabel>
                <S.Checkbox
                  type="checkbox"
                  name="age"
                  checked={terms.age}
                  onChange={handleTermChange}
                  required
                />
                <S.Requirement>필수</S.Requirement>
                만 14세 이상입니다
              </S.TermLabel>
              <S.TermLabel>
                <S.Checkbox
                  type="checkbox"
                  name="service"
                  checked={terms.service}
                  onChange={handleTermChange}
                  required
                />
                <S.Requirement>필수</S.Requirement>
                서비스 이용약관 동의
              </S.TermLabel>
              <S.TermLabel>
                <S.Checkbox
                  type="checkbox"
                  name="privacy"
                  checked={terms.privacy}
                  onChange={handleTermChange}
                  required
                />
                <S.Requirement>필수</S.Requirement>
                개인정보 수집 및 이용 동의
              </S.TermLabel>
              <S.TermLabel>
                <S.Checkbox
                  type="checkbox"
                  name="marketing"
                  checked={terms.marketing}
                  onChange={handleTermChange}
                />
                <S.Requirement $optional>선택</S.Requirement>
                이벤트 및 혜택 알림 수신 동의
              </S.TermLabel>
            </S.TermList>
          </S.TermsSection>

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