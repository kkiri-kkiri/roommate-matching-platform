import { useRef, useState } from "react";
import {
  ArrowLeft,
  CircleCheck,
  KeyRound,
  Mail,
  RotateCcw,
  ShieldCheck,
} from "lucide-react";
import * as S from "./FindPasswordPageCss";

import { requestPasswordReset, confirmPasswordReset } from "../../api/authApi";
import {
  meetsPasswordRequirements,
  PASSWORD_REQUIREMENTS_MESSAGE,
} from "../../utils/passwordValidation";

const SCHOOL_EMAIL_DOMAIN = "@hknu.ac.kr";

function getEmailLocalPart(value) {
  return value.trim().replace(/@hknu\.ac\.kr$/i, "");
}

function FindPasswordPage() {
  const [email, setEmail] = useState("");
  const [lookupError, setLookupError] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [pending, setPending] = useState(false);
  const inFlight = useRef(false);
  const [stage, setStage] = useState("lookup");
  const [newPassword, setNewPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const passwordMeetsRequirements = meetsPasswordRequirements(newPassword);
  const passwordMatches =
    passwordConfirm.length > 0 && newPassword === passwordConfirm;

  const resetFlow = () => {
    setLookupError("");
    setResetToken("");
    setStage("lookup");
    setNewPassword("");
    setPasswordConfirm("");
    setShowPassword(false);
    setShowPasswordConfirm(false);
  };

  const handleEmailSubmit = async (event) => {
    event.preventDefault();
    if (inFlight.current) return;
    const emailLocalPart = email.trim();
    if (!emailLocalPart) {
      window.alert("이메일을 입력해주세요.");
      return;
    }
    if (!/^[^@\s]{1,64}$/.test(emailLocalPart)) {
      setLookupError("이메일 형식이 잘못되었습니다.");
      return;
    }

    inFlight.current = true;
    setPending(true);
    setLookupError("");
    try {
      await requestPasswordReset(`${emailLocalPart}${SCHOOL_EMAIL_DOMAIN}`);
      setStage("emailSent");
    } catch (error) {
      setLookupError(error.message);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };

  const handleResetSubmit = async (event) => {
    event.preventDefault();
    if (inFlight.current) return;

    if (!resetToken.trim()) {
      window.alert("비밀번호 재설정 토큰을 입력해 주세요.");
      return;
    }

    if (!newPassword || !passwordConfirm) {
      window.alert("모든 항목을 작성해주세요.");
      return;
    }

    if (!passwordMeetsRequirements || !passwordMatches) {
      window.alert("비밀번호 조건과 비밀번호 확인을 다시 확인해 주세요.");
      return;
    }

    inFlight.current = true;
    setPending(true);
    try {
      await confirmPasswordReset(
        resetToken.trim(),
        newPassword,
        passwordConfirm,
      );
      setResetToken("");
      setNewPassword("");
      setPasswordConfirm("");
      setStage("complete");
    } catch (error) {
      window.alert(error.message);
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  };

  const renderLookupForm = () => (
    <>
      <S.Form onSubmit={handleEmailSubmit}>
        <S.Field>
          <S.Label htmlFor="find-password-email">학교 이메일</S.Label>
          <S.InputWrap>
            <S.InputIcon>
              <Mail aria-hidden="true" />
            </S.InputIcon>
            <S.EmailInput
              id="find-password-email"
              name="email"
              type="text"
              inputMode="email"
              autoComplete="username"
              placeholder="이메일 아이디"
              value={email}
              disabled={pending}
              onChange={(event) => {
                setEmail(getEmailLocalPart(event.target.value));
                resetFlow();
              }}
            />
            <S.EmailDomain>{SCHOOL_EMAIL_DOMAIN}</S.EmailDomain>
          </S.InputWrap>
        </S.Field>

        <S.PrimaryButton type="submit" disabled={pending}>
          <Mail aria-hidden="true" />
          {pending ? "요청 중..." : "비밀번호 재설정 이메일 보내기"}
        </S.PrimaryButton>
      </S.Form>

      {lookupError && (
        <S.ResultContent role="alert" aria-live="assertive">
          <S.ResultDescription>{lookupError}</S.ResultDescription>
          <S.TextButton type="button" onClick={() => setLookupError("")}>
            <RotateCcw aria-hidden="true" />
            다시 입력하기
          </S.TextButton>
        </S.ResultContent>
      )}
    </>
  );

  const renderEmailSent = () => (
    <S.StepPanel role="status" aria-live="polite">
      <S.LargeStatusIcon>
        <Mail aria-hidden="true" />
      </S.LargeStatusIcon>
      <S.StepTitle>재설정 이메일을 확인해 주세요</S.StepTitle>
      <S.StepDescription>
        가입된 이메일이면{" "}
        <strong>{`${email.trim()}${SCHOOL_EMAIL_DOMAIN}`}</strong>으로 재설정
        안내를 보냅니다.
      </S.StepDescription>
      <S.PrimaryButton type="button" onClick={() => setStage("reset")}>
        <KeyRound aria-hidden="true" />
        메일의 재설정 토큰 입력하기
      </S.PrimaryButton>
      <S.TextButton type="button" onClick={resetFlow}>
        이메일 다시 입력하기
      </S.TextButton>
    </S.StepPanel>
  );

  const renderResetForm = () => (
    <S.ResetSection>
      <S.Form onSubmit={handleResetSubmit} noValidate>
        <S.Field>
          <S.Label htmlFor="reset-token">재설정 토큰</S.Label>
          <S.InputWrap>
            <S.InputIcon>
              <Mail aria-hidden="true" />
            </S.InputIcon>
            <S.Input
              id="reset-token"
              name="resetToken"
              type="text"
              autoComplete="off"
              placeholder="메일로 받은 재설정 토큰을 붙여 넣어 주세요"
              value={resetToken}
              disabled={pending}
              onChange={(event) => {
                setResetToken(event.target.value);
              }}
            />
          </S.InputWrap>
        </S.Field>
        <S.Field>
          <S.Label htmlFor="new-password">새 비밀번호</S.Label>
          <S.PasswordField>
            <S.InputIcon>
              <KeyRound aria-hidden="true" />
            </S.InputIcon>
            <S.Input
              id="new-password"
              name="newPassword"
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              placeholder="비밀번호 입력"
              value={newPassword}
              disabled={pending}
              onChange={(event) => {
                setNewPassword(event.target.value);
              }}
              required
              aria-describedby="password-guide"
              aria-invalid={Boolean(
                newPassword && !passwordMeetsRequirements,
              )}
              $hasAction
              $tone={
                newPassword
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
              newPassword
                ? passwordMeetsRequirements
                  ? "success"
                  : "error"
                : "neutral"
            }
          >
            {newPassword
              ? passwordMeetsRequirements
                ? "사용할 수 있는 비밀번호예요."
                : PASSWORD_REQUIREMENTS_MESSAGE
              : PASSWORD_REQUIREMENTS_MESSAGE}
          </S.FieldFeedback>
        </S.Field>

        <S.Field>
          <S.Label htmlFor="new-password-confirm">새 비밀번호 확인</S.Label>
          <S.PasswordField>
            <S.InputIcon>
              <ShieldCheck aria-hidden="true" />
            </S.InputIcon>
            <S.Input
              id="new-password-confirm"
              name="newPasswordConfirm"
              type={showPasswordConfirm ? "text" : "password"}
              autoComplete="new-password"
              placeholder="비밀번호 확인"
              value={passwordConfirm}
              disabled={pending}
              onChange={(event) => {
                setPasswordConfirm(event.target.value);
              }}
              required
              aria-describedby="password-confirm-guide"
              aria-invalid={Boolean(passwordConfirm && !passwordMatches)}
              $hasAction
              $tone={
                passwordConfirm
                  ? passwordMatches
                    ? "success"
                    : "error"
                  : "neutral"
              }
            />
            <S.VisibilityButton
              type="button"
              onClick={() => setShowPasswordConfirm((current) => !current)}
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
              passwordConfirm
                ? passwordMatches
                  ? "success"
                  : "error"
                : "neutral"
            }
          >
            {passwordConfirm
              ? passwordMatches
                ? "비밀번호가 일치해요."
                : "비밀번호가 일치하지 않아요."
              : "같은 비밀번호를 입력해 주세요."}
          </S.FieldFeedback>
        </S.Field>

        <S.PrimaryButton type="submit" disabled={pending}>
          <ShieldCheck aria-hidden="true" />
          {pending ? "변경 중..." : "비밀번호 변경하기"}
        </S.PrimaryButton>
      </S.Form>

      <S.TextButton type="button" onClick={resetFlow} disabled={pending}>
        이메일 다시 입력하기
      </S.TextButton>
    </S.ResetSection>
  );

  const renderComplete = () => (
    <S.StepPanel role="status" aria-live="polite">
      <S.LargeStatusIcon $complete>
        <CircleCheck aria-hidden="true" />
      </S.LargeStatusIcon>
      <S.StepTitle>비밀번호를 변경했어요</S.StepTitle>
      <S.LoginButton to="/login">로그인하러 가기</S.LoginButton>
    </S.StepPanel>
  );

  const renderPanelContent = () => {
    if (stage === "emailSent") return renderEmailSent();
    if (stage === "reset") return renderResetForm();
    if (stage === "complete") return renderComplete();
    return renderLookupForm();
  };

  return (
    <>
      <S.PageGlobalStyle />
      <S.Page>
        <S.PageInner>
          <S.BackLink to="/login">
            <ArrowLeft aria-hidden="true" />
            로그인으로 돌아가기
          </S.BackLink>

          <S.Card>
            <S.Intro>
              <S.Title>비밀번호 찾기</S.Title>
            </S.Intro>

            <S.Panel id="find-password-panel">{renderPanelContent()}</S.Panel>
          </S.Card>
        </S.PageInner>
      </S.Page>
    </>
  );
}

export default FindPasswordPage;
