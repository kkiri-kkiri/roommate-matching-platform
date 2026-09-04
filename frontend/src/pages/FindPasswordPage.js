import { useRef, useState } from "react";
import {
  ArrowLeft,
  CircleAlert,
  CircleCheck,
  KeyRound,
  Mail,
  RotateCcw,
  ShieldCheck,
  Smartphone,
} from "lucide-react";
import * as S from "./FindPasswordPageCss";

const DEMO_ACCOUNT = {
  userId: "roommate01",
  phone: "01012345678",
  email: "roommate@hknu.ac.kr",
};

function FindPasswordPage() {
  const [method, setMethod] = useState("phone");
  const [userId, setUserId] = useState("");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [lookupResult, setLookupResult] = useState("idle");
  const [stage, setStage] = useState("lookup");
  const [newPassword, setNewPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [passwordErrorField, setPasswordErrorField] = useState("");
  const phoneTabRef = useRef(null);
  const emailTabRef = useRef(null);

  const resetFlow = () => {
    setLookupResult("idle");
    setStage("lookup");
    setNewPassword("");
    setPasswordConfirm("");
    setPasswordError("");
    setPasswordErrorField("");
  };

  const changeMethod = (nextMethod, shouldFocus = false) => {
    setMethod(nextMethod);
    resetFlow();

    if (shouldFocus) {
      const nextTabRef = nextMethod === "phone" ? phoneTabRef : emailTabRef;
      nextTabRef.current?.focus();
    }
  };

  const handleTabKeyDown = (event) => {
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      changeMethod(method === "phone" ? "email" : "phone", true);
    }
  };

  const handleLookupFieldChange = (setter) => (event) => {
    setter(event.target.value);
    resetFlow();
  };

  const isCorrectId =
    userId.trim().toLowerCase() === DEMO_ACCOUNT.userId.toLowerCase();

  const handlePhoneSubmit = (event) => {
    event.preventDefault();
    const normalizedPhone = phone.replace(/\D/g, "");

    if (isCorrectId && normalizedPhone === DEMO_ACCOUNT.phone) {
      setLookupResult("success");
      setStage("reset");
      return;
    }

    setLookupResult("empty");
  };

  const handleEmailSubmit = (event) => {
    event.preventDefault();
    const normalizedEmail = email.trim().toLowerCase();

    if (isCorrectId && normalizedEmail === DEMO_ACCOUNT.email) {
      setLookupResult("success");
      setStage("emailSent");
      return;
    }

    setLookupResult("empty");
  };

  const handleResetSubmit = (event) => {
    event.preventDefault();

    if (newPassword.length < 8) {
      setPasswordError("새 비밀번호는 8자 이상으로 입력해 주세요.");
      setPasswordErrorField("newPassword");
      return;
    }

    if (newPassword !== passwordConfirm) {
      setPasswordError("새 비밀번호가 서로 일치하지 않아요.");
      setPasswordErrorField("passwordConfirm");
      return;
    }

    setPasswordError("");
    setPasswordErrorField("");
    setStage("complete");
  };

  const renderLookupForm = () => (
    <>
      <S.SectionHeading>
        {method === "phone"
          ? "아이디와 휴대폰 번호를 입력해 주세요"
          : "아이디와 가입 이메일을 입력해 주세요"}
      </S.SectionHeading>
      <S.SectionCopy>
        {method === "phone"
          ? "가입 정보 확인 후 본인인증을 거쳐 비밀번호를 재설정할 수 있어요."
          : "가입 정보와 일치하면 비밀번호 재설정 안내를 이메일로 보내 드려요."}
      </S.SectionCopy>

      <S.Form
        onSubmit={method === "phone" ? handlePhoneSubmit : handleEmailSubmit}
      >
        <S.Field>
          <S.Label htmlFor={`find-password-id-${method}`}>아이디</S.Label>
          <S.InputWrap>
            <S.InputIcon>
              <KeyRound aria-hidden="true" />
            </S.InputIcon>
            <S.Input
              id={`find-password-id-${method}`}
              name="userId"
              type="text"
              autoComplete="username"
              placeholder="아이디를 입력해 주세요"
              value={userId}
              onChange={handleLookupFieldChange(setUserId)}
              required
            />
          </S.InputWrap>
        </S.Field>

        {method === "phone" ? (
          <S.Field>
            <S.Label htmlFor="find-password-phone">휴대폰 번호</S.Label>
            <S.InputWrap>
              <S.InputIcon>
                <Smartphone aria-hidden="true" />
              </S.InputIcon>
              <S.Input
                id="find-password-phone"
                name="phone"
                type="tel"
                inputMode="numeric"
                autoComplete="tel"
                placeholder="010-1234-5678"
                pattern="01[0-9]-?[0-9]{3,4}-?[0-9]{4}"
                title="010-1234-5678 형식으로 입력해 주세요."
                value={phone}
                onChange={handleLookupFieldChange(setPhone)}
                required
              />
            </S.InputWrap>
          </S.Field>
        ) : (
          <S.Field>
            <S.Label htmlFor="find-password-email">이메일</S.Label>
            <S.InputWrap>
              <S.InputIcon>
                <Mail aria-hidden="true" />
              </S.InputIcon>
              <S.Input
                id="find-password-email"
                name="email"
                type="email"
                autoComplete="email"
                placeholder="name@hknu.ac.kr"
                value={email}
                onChange={handleLookupFieldChange(setEmail)}
                required
              />
            </S.InputWrap>
          </S.Field>
        )}

        <S.PrimaryButton type="submit">
          {method === "phone" ? (
            <ShieldCheck aria-hidden="true" />
          ) : (
            <Mail aria-hidden="true" />
          )}
          {method === "phone"
            ? "본인인증하고 비밀번호 찾기"
            : "비밀번호 재설정 이메일 보내기"}
        </S.PrimaryButton>
      </S.Form>

      <S.DemoNote>
        <S.DemoBadge>UI 데모</S.DemoBadge>
        <span>
          아이디 <strong>roommate01</strong>과{" "}
          {method === "phone" ? (
            <strong>010-1234-5678</strong>
          ) : (
            <strong>roommate@hknu.ac.kr</strong>
          )}
          을 입력하면 계정 있음 상태를 확인할 수 있어요. 다른 유효한 정보는 계정
          없음 상태로 전환됩니다.
        </span>
      </S.DemoNote>

      {lookupResult === "empty" && (
        <S.ResultPanel $tone="empty" role="alert" aria-live="polite">
          <S.ResultIcon $tone="empty">
            <CircleAlert aria-hidden="true" />
          </S.ResultIcon>
          <S.ResultContent>
            <S.ResultTitle>일치하는 계정을 찾지 못했어요</S.ResultTitle>
            <S.ResultDescription>
              아이디와 {method === "phone" ? "휴대폰 번호" : "이메일"}을 다시
              확인하거나 다른 방법을 이용해 주세요.
            </S.ResultDescription>
            <S.TextButton type="button" onClick={() => setLookupResult("idle")}>
              <RotateCcw aria-hidden="true" />
              다시 입력하기
            </S.TextButton>
          </S.ResultContent>
        </S.ResultPanel>
      )}
    </>
  );

  const renderEmailSent = () => (
    <S.StepPanel role="status" aria-live="polite">
      <S.LargeStatusIcon>
        <Mail aria-hidden="true" />
      </S.LargeStatusIcon>
      <S.StepTitle>재설정 이메일을 보냈어요</S.StepTitle>
      <S.StepDescription>
        <strong>{email.trim()}</strong>으로 비밀번호 재설정 안내를 전송했어요.
        메일함을 확인해 주세요.
      </S.StepDescription>
      <S.InfoBox>
        메일이 보이지 않으면 스팸 메일함을 확인하거나 입력한 이메일이 맞는지
        확인해 주세요.
      </S.InfoBox>
      <S.PrimaryButton type="button" onClick={() => setStage("reset")}>
        <KeyRound aria-hidden="true" />
        데모로 비밀번호 재설정 계속하기
      </S.PrimaryButton>
      <S.TextButton type="button" onClick={resetFlow}>
        이메일 다시 입력하기
      </S.TextButton>
    </S.StepPanel>
  );

  const renderResetForm = () => (
    <S.ResetSection>
      <S.VerifiedBadge role="status" aria-live="polite">
        <CircleCheck aria-hidden="true" />
        {method === "phone" ? "본인인증" : "이메일 인증"}이 완료됐어요
      </S.VerifiedBadge>
      <S.SectionHeading>새 비밀번호를 설정해 주세요</S.SectionHeading>
      <S.SectionCopy>
        다른 서비스에서 사용하지 않는 안전한 비밀번호를 권장해요.
      </S.SectionCopy>

      <S.Form onSubmit={handleResetSubmit}>
        <S.Field>
          <S.Label htmlFor="new-password">새 비밀번호</S.Label>
          <S.InputWrap>
            <S.InputIcon>
              <KeyRound aria-hidden="true" />
            </S.InputIcon>
            <S.Input
              id="new-password"
              name="newPassword"
              type="password"
              autoComplete="new-password"
              placeholder="8자 이상 입력해 주세요"
              minLength="8"
              value={newPassword}
              onChange={(event) => {
                setNewPassword(event.target.value);
                setPasswordError("");
                setPasswordErrorField("");
              }}
              aria-invalid={passwordErrorField === "newPassword"}
              aria-describedby={
                passwordErrorField === "newPassword"
                  ? "password-requirement password-error"
                  : "password-requirement"
              }
              required
            />
          </S.InputWrap>
          <S.FieldHint id="password-requirement">
            영문, 숫자, 특수문자를 조합하면 더 안전해요.
          </S.FieldHint>
        </S.Field>

        <S.Field>
          <S.Label htmlFor="new-password-confirm">새 비밀번호 확인</S.Label>
          <S.InputWrap>
            <S.InputIcon>
              <ShieldCheck aria-hidden="true" />
            </S.InputIcon>
            <S.Input
              id="new-password-confirm"
              name="newPasswordConfirm"
              type="password"
              autoComplete="new-password"
              placeholder="한 번 더 입력해 주세요"
              minLength="8"
              value={passwordConfirm}
              onChange={(event) => {
                setPasswordConfirm(event.target.value);
                setPasswordError("");
                setPasswordErrorField("");
              }}
              aria-invalid={passwordErrorField === "passwordConfirm"}
              aria-describedby={
                passwordErrorField === "passwordConfirm"
                  ? "password-error"
                  : undefined
              }
              required
            />
          </S.InputWrap>
        </S.Field>

        {passwordError && (
          <S.ErrorMessage id="password-error" role="alert">
            <CircleAlert aria-hidden="true" />
            {passwordError}
          </S.ErrorMessage>
        )}

        <S.PrimaryButton type="submit">
          <ShieldCheck aria-hidden="true" />
          비밀번호 변경하기
        </S.PrimaryButton>
      </S.Form>

      <S.TextButton type="button" onClick={resetFlow}>
        다른 계정 정보 입력하기
      </S.TextButton>
    </S.ResetSection>
  );

  const renderComplete = () => (
    <S.StepPanel role="status" aria-live="polite">
      <S.LargeStatusIcon $complete>
        <CircleCheck aria-hidden="true" />
      </S.LargeStatusIcon>
      <S.StepTitle>비밀번호가 변경됐어요</S.StepTitle>
      <S.StepDescription>
        새 비밀번호로 로그인할 수 있어요. 안전을 위해 다른 기기의 로그인 정보도
        다시 확인해 주세요.
      </S.StepDescription>
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
    <S.Page>
      <S.PageInner>
        <S.BackLink to="/login">
          <ArrowLeft aria-hidden="true" />
          로그인으로 돌아가기
        </S.BackLink>

        <S.Card>
          <S.Intro>
            <S.Eyebrow>
              <KeyRound aria-hidden="true" />
              ACCOUNT RECOVERY
            </S.Eyebrow>
            <S.Title>비밀번호 찾기</S.Title>
            <S.Description>
              가입 정보를 확인하고 새로운 비밀번호를 설정해 보세요.
            </S.Description>
          </S.Intro>

          <S.Tabs role="tablist" aria-label="비밀번호 찾기 방법">
            <S.Tab
              ref={phoneTabRef}
              id="find-password-phone-tab"
              type="button"
              role="tab"
              aria-selected={method === "phone"}
              aria-controls="find-password-panel"
              tabIndex={method === "phone" ? 0 : -1}
              $active={method === "phone"}
              onClick={() => changeMethod("phone")}
              onKeyDown={handleTabKeyDown}
            >
              <Smartphone aria-hidden="true" />
              휴대폰 본인인증
            </S.Tab>
            <S.Tab
              ref={emailTabRef}
              id="find-password-email-tab"
              type="button"
              role="tab"
              aria-selected={method === "email"}
              aria-controls="find-password-panel"
              tabIndex={method === "email" ? 0 : -1}
              $active={method === "email"}
              onClick={() => changeMethod("email")}
              onKeyDown={handleTabKeyDown}
            >
              <Mail aria-hidden="true" />
              이메일
            </S.Tab>
          </S.Tabs>

          <S.Panel
            id="find-password-panel"
            role="tabpanel"
            aria-labelledby={`find-password-${method}-tab`}
          >
            {renderPanelContent()}
          </S.Panel>

          <S.CardFooter>
            <span>아이디가 기억나지 않나요?</span>
            <S.FooterLink to="/find-id">아이디 찾기</S.FooterLink>
          </S.CardFooter>
        </S.Card>
      </S.PageInner>
    </S.Page>
  );
}

export default FindPasswordPage;
