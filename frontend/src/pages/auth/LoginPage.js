import { useRef, useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { checkLoginEmail, login } from "../../api/authApi";
import * as S from "./LoginPageCss";

const REMEMBERED_EMAIL_KEY = "rememberedEmail";
const SCHOOL_EMAIL_DOMAIN = "@hknu.ac.kr";

function getEmailLocalPart(value) {
  return value.trim().replace(/@hknu\.ac\.kr$/i, "");
}

function getRememberedEmail() {
  if (typeof window === "undefined") return "";

  try {
    window.localStorage.removeItem("rememberedLoginId");
    return getEmailLocalPart(
      window.localStorage.getItem(REMEMBERED_EMAIL_KEY) || "",
    );
  } catch {
    return "";
  }
}

const LoginPage = ({ onLogin }) => {
  const [emailLocalPart, setEmailLocalPart] = useState(getRememberedEmail);
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberEmail, setRememberEmail] = useState(() =>
    Boolean(getRememberedEmail()),
  );
  const [feedback, setFeedback] = useState(null);
  const navigate = useNavigate();
  const [emailChecked, setEmailChecked] = useState(false);
  const [pending, setPending] = useState(false);
  const requestVersion = useRef(0);
  const inFlight = useRef(false);
  const emailInvalid =
    feedback?.tone === "error" &&
    (feedback.reason === "email" || feedback.reason === "credentials");

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (inFlight.current) return;
    const normalizedEmail = `${emailLocalPart.trim()}${SCHOOL_EMAIL_DOMAIN}`;

    if (!/^[^@\s]{1,64}@hknu\.ac\.kr$/.test(normalizedEmail)) {
      setFeedback({
        tone: "error",
        reason: "email",
        text: "학교 이메일의 @ 앞부분을 입력해 주세요.",
      });
      return;
    }

    if (emailChecked && !password) {
      setFeedback({
        tone: "error",
        reason: "password",
        text: "비밀번호를 입력해 주세요.",
      });
      return;
    }

    const version = requestVersion.current;
    inFlight.current = true;
    setPending(true);
    setFeedback(null);
    try {
      if (!emailChecked) {
        const result = await checkLoginEmail(normalizedEmail);
        if (version !== requestVersion.current) return;
        if (result.registered) {
          setEmailChecked(true);
        } else {
          setFeedback({
            tone: "error",
            reason: "email",
            text: "가입된 이메일을 확인해 주세요.",
          });
        }
        return;
      }
      const response = await login(normalizedEmail, password);

      if (version !== requestVersion.current) return;
      try {
        window.localStorage.setItem("accessToken", response.accessToken);
        if (rememberEmail) {
          window.localStorage.setItem(REMEMBERED_EMAIL_KEY, normalizedEmail);
        } else {
          window.localStorage.removeItem(REMEMBERED_EMAIL_KEY);
        }
      } catch {
        // 저장소 접근이 제한된 환경에서도 로그인 흐름은 계속 진행합니다.
      }

      onLogin?.({
        id: response.user.id,
        schoolEmail: response.user.schoolEmail,
        displayName: response.user.nickname,
        role: response.user.role,
      });
      navigate(response.user.role === "ADMIN" ? "/admin" : "/", {
        replace: true,
      });
    } catch (error) {
      if (version !== requestVersion.current) return;
      setFeedback({
        tone: "error",
        reason: "credentials",
        text: error.message,
      });
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };

  const clearFeedback = () => {
    if (feedback) {
      setFeedback(null);
    }
  };

  return (
    <>
      <S.PageGlobalStyle />
      <S.Page>
        <S.PageInner>
          <S.BackLink to="/">
            <ArrowLeft aria-hidden="true" />
            메인으로 돌아가기
          </S.BackLink>

          <S.LoginCard aria-labelledby="login-title">
            <S.Header>
              <S.Title id="login-title">로그인</S.Title>
            </S.Header>

            <S.Form onSubmit={handleSubmit} noValidate>
              <S.FieldGroup>
                <S.Label htmlFor="login-email">학교 이메일</S.Label>
                <S.EmailField $invalid={emailInvalid}>
                  <S.EmailInput
                    id="login-email"
                    name="email"
                    type="text"
                    inputMode="email"
                    value={emailLocalPart}
                    onChange={(event) => {
                      requestVersion.current += 1;
                      setEmailLocalPart(getEmailLocalPart(event.target.value));
                      setEmailChecked(false);
                      setPassword("");
                      setShowPassword(false);
                      clearFeedback();
                    }}
                    placeholder="이메일 아이디"
                    autoComplete="username"
                    autoCapitalize="none"
                    spellCheck={false}
                    required
                    aria-invalid={emailInvalid}
                    aria-describedby="login-email-domain"
                  />
                  <S.EmailDomain id="login-email-domain">
                    {SCHOOL_EMAIL_DOMAIN}
                  </S.EmailDomain>
                </S.EmailField>
              </S.FieldGroup>

              {emailChecked && (
                <S.FieldGroup>
                  <S.Label htmlFor="login-password">비밀번호</S.Label>
                  <S.PasswordField>
                    <S.Input
                      id="login-password"
                      name="password"
                      type={showPassword ? "text" : "password"}
                      value={password}
                      onChange={(event) => {
                        setPassword(event.target.value);
                        clearFeedback();
                      }}
                      placeholder="비밀번호를 입력해 주세요"
                      autoComplete="current-password"
                      required
                      aria-invalid={
                        feedback?.tone === "error" &&
                        (feedback.reason === "password" ||
                          feedback.reason === "credentials")
                      }
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
                </S.FieldGroup>
              )}

              <S.OptionRow>
                <S.CheckLabel>
                  <S.Checkbox
                    type="checkbox"
                    checked={rememberEmail}
                    onChange={(event) => setRememberEmail(event.target.checked)}
                  />
                  이메일 저장
                </S.CheckLabel>

                {emailChecked && (
                  <S.UtilityLinks aria-label="계정 찾기">
                    <S.UtilityLink to="/find-password">
                      비밀번호 찾기
                    </S.UtilityLink>
                  </S.UtilityLinks>
                )}
              </S.OptionRow>

              {feedback && (
                <S.Feedback
                  role={feedback.tone === "error" ? "alert" : "status"}
                  aria-live={feedback.tone === "error" ? "assertive" : "polite"}
                  $tone={feedback.tone}
                >
                  {feedback.text}
                </S.Feedback>
              )}

              <S.SubmitButton type="submit" disabled={pending}>
                {emailChecked ? "로그인" : "다음"}
              </S.SubmitButton>
            </S.Form>

            <S.SignupPrompt>
              <S.SignupText>아직 계정이 없으신가요?</S.SignupText>
              <S.SignupLink to="/signup">회원가입</S.SignupLink>
            </S.SignupPrompt>
          </S.LoginCard>
        </S.PageInner>
      </S.Page>
    </>
  );
};

export default LoginPage;
