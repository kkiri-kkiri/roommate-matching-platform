import { useState } from "react";
import { ArrowLeft } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { login } from "../../api/authApi";
import * as S from "./LoginPageCss";

const REMEMBERED_LOGIN_ID_KEY = "rememberedLoginId";
function getRememberedLoginId() {
  if (typeof window === "undefined") return "";

  try {
    return window.localStorage.getItem(REMEMBERED_LOGIN_ID_KEY) || "";
  } catch {
    return "";
  }
}

const LoginPage = ({ onLogin }) => {
  const [loginId, setLoginId] = useState(getRememberedLoginId);
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberId, setRememberId] = useState(() =>
    Boolean(getRememberedLoginId()),
  );
  const [feedback, setFeedback] = useState(null);
  const navigate = useNavigate();

  const handleSubmit = async (event) => {
    event.preventDefault();
    const normalizedLoginId = loginId.trim();

    if (!normalizedLoginId || !password) {
      setFeedback({
        tone: "error",
        reason: "required",
        text: "아이디와 비밀번호를 모두 입력해 주세요.",
      });
      return;
    }

    try {
      const response = await login(normalizedLoginId, password);

      try {
        window.localStorage.setItem("accessToken", response.accessToken);
        if (rememberId) {
          window.localStorage.setItem(
            REMEMBERED_LOGIN_ID_KEY,
            normalizedLoginId,
          );
        } else {
          window.localStorage.removeItem(REMEMBERED_LOGIN_ID_KEY);
        }
      } catch {
        // 저장소 접근이 제한된 환경에서도 로그인 흐름은 계속 진행합니다.
      }

      onLogin?.({
        id: response.user.id,
        loginId: response.user.loginId,
        displayName: response.user.nickname,
      });
      navigate("/", { replace: true });
    } catch (error) {
      setFeedback({
        tone: "error",
        reason: "credentials",
        text: error.message,
      });
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
              <S.Eyebrow>ROOMMATE MATCHING</S.Eyebrow>
              <S.Title id="login-title">다시 만나 반가워요</S.Title>
              <S.Description>
                로그인하고 나와 잘 맞는 룸메이트를 찾아보세요.
              </S.Description>
            </S.Header>

            <S.Form onSubmit={handleSubmit} noValidate>
              <S.FieldGroup>
                <S.Label htmlFor="login-id">아이디</S.Label>
                <S.Input
                  id="login-id"
                  name="loginId"
                  type="text"
                  value={loginId}
                  onChange={(event) => {
                    setLoginId(event.target.value);
                    clearFeedback();
                  }}
                  placeholder="아이디를 입력해 주세요"
                  autoComplete="username"
                  required
                  aria-invalid={
                    feedback?.tone === "error" &&
                    (feedback.reason === "credentials" || !loginId.trim())
                  }
                />
              </S.FieldGroup>

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
                      (feedback.reason === "credentials" || !password)
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

              <S.OptionRow>
                <S.CheckLabel>
                  <S.Checkbox
                    type="checkbox"
                    checked={rememberId}
                    onChange={(event) => setRememberId(event.target.checked)}
                  />
                  아이디 저장
                </S.CheckLabel>

                <S.UtilityLinks aria-label="계정 찾기">
                  <S.UtilityLink to="/find-id">아이디 찾기</S.UtilityLink>
                  <S.LinkDivider aria-hidden="true" />
                  <S.UtilityLink to="/find-password">
                    비밀번호 찾기
                  </S.UtilityLink>
                </S.UtilityLinks>
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

              <S.SubmitButton type="submit">로그인</S.SubmitButton>
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
