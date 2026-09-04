import { useRef, useState } from "react";
import {
  ArrowLeft,
  CircleAlert,
  CircleCheck,
  Mail,
  RotateCcw,
  Search,
  Smartphone,
} from "lucide-react";
import * as S from "./FindIdPageCss";

const DEMO_ACCOUNT = {
  phone: "01012345678",
  email: "roommate@hknu.ac.kr",
  userId: "roommate01",
};

function FindIdPage() {
  const [method, setMethod] = useState("phone");
  const [phone, setPhone] = useState("");
  const [email, setEmail] = useState("");
  const [result, setResult] = useState("idle");
  const phoneTabRef = useRef(null);
  const emailTabRef = useRef(null);

  const resetResult = () => setResult("idle");

  const changeMethod = (nextMethod, shouldFocus = false) => {
    setMethod(nextMethod);
    resetResult();

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

  const handlePhoneSubmit = (event) => {
    event.preventDefault();
    const normalizedPhone = phone.replace(/\D/g, "");
    setResult(normalizedPhone === DEMO_ACCOUNT.phone ? "success" : "empty");
  };

  const handleEmailSubmit = (event) => {
    event.preventDefault();
    const normalizedEmail = email.trim().toLowerCase();
    setResult(normalizedEmail === DEMO_ACCOUNT.email ? "success" : "empty");
  };

  const handlePhoneChange = (event) => {
    setPhone(event.target.value);
    resetResult();
  };

  const handleEmailChange = (event) => {
    setEmail(event.target.value);
    resetResult();
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
              <Search aria-hidden="true" />
              ACCOUNT RECOVERY
            </S.Eyebrow>
            <S.Title>아이디 찾기</S.Title>
            <S.Description>
              가입할 때 등록한 정보로 아이디를 안전하게 확인해 보세요.
            </S.Description>
          </S.Intro>

          <S.Tabs role="tablist" aria-label="아이디 찾기 방법">
            <S.Tab
              ref={phoneTabRef}
              id="find-id-phone-tab"
              type="button"
              role="tab"
              aria-selected={method === "phone"}
              aria-controls="find-id-panel"
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
              id="find-id-email-tab"
              type="button"
              role="tab"
              aria-selected={method === "email"}
              aria-controls="find-id-panel"
              tabIndex={method === "email" ? 0 : -1}
              $active={method === "email"}
              onClick={() => changeMethod("email")}
              onKeyDown={handleTabKeyDown}
            >
              <Mail aria-hidden="true" />
              이메일
            </S.Tab>
          </S.Tabs>

          {method === "phone" ? (
            <S.Panel
              id="find-id-panel"
              role="tabpanel"
              aria-labelledby="find-id-phone-tab"
            >
              <S.SectionHeading>휴대폰 번호를 입력해 주세요</S.SectionHeading>
              <S.SectionCopy>
                가입 정보와 일치하면 본인인증 후 아이디를 바로 안내해 드려요.
              </S.SectionCopy>

              <S.Form onSubmit={handlePhoneSubmit}>
                <S.Field>
                  <S.Label htmlFor="find-id-phone">휴대폰 번호</S.Label>
                  <S.InputWrap>
                    <S.InputIcon>
                      <Smartphone aria-hidden="true" />
                    </S.InputIcon>
                    <S.Input
                      id="find-id-phone"
                      name="phone"
                      type="tel"
                      inputMode="numeric"
                      autoComplete="tel"
                      placeholder="010-1234-5678"
                      pattern="01[0-9]-?[0-9]{3,4}-?[0-9]{4}"
                      title="010-1234-5678 형식으로 입력해 주세요."
                      value={phone}
                      onChange={handlePhoneChange}
                      required
                    />
                  </S.InputWrap>
                  <S.FieldHint>숫자만 입력해도 확인할 수 있어요.</S.FieldHint>
                </S.Field>

                <S.PrimaryButton type="submit">
                  <Search aria-hidden="true" />
                  본인인증하고 아이디 찾기
                </S.PrimaryButton>
              </S.Form>

              <S.DemoNote>
                <S.DemoBadge>UI 데모</S.DemoBadge>
                <span>
                  <strong>010-1234-5678</strong> 입력 시 계정 있음, 다른 유효한
                  번호 입력 시 계정 없음 상태를 확인할 수 있어요.
                </span>
              </S.DemoNote>
            </S.Panel>
          ) : (
            <S.Panel
              id="find-id-panel"
              role="tabpanel"
              aria-labelledby="find-id-email-tab"
            >
              <S.SectionHeading>가입 이메일을 입력해 주세요</S.SectionHeading>
              <S.SectionCopy>
                가입 정보와 일치하면 아이디 안내를 입력한 이메일로 전송해
                드려요.
              </S.SectionCopy>

              <S.Form onSubmit={handleEmailSubmit}>
                <S.Field>
                  <S.Label htmlFor="find-id-email">이메일</S.Label>
                  <S.InputWrap>
                    <S.InputIcon>
                      <Mail aria-hidden="true" />
                    </S.InputIcon>
                    <S.Input
                      id="find-id-email"
                      name="email"
                      type="email"
                      autoComplete="email"
                      placeholder="name@hknu.ac.kr"
                      value={email}
                      onChange={handleEmailChange}
                      required
                    />
                  </S.InputWrap>
                </S.Field>

                <S.PrimaryButton type="submit">
                  <Mail aria-hidden="true" />
                  아이디 안내 이메일 보내기
                </S.PrimaryButton>
              </S.Form>

              <S.DemoNote>
                <S.DemoBadge>UI 데모</S.DemoBadge>
                <span>
                  <strong>roommate@hknu.ac.kr</strong> 입력 시 계정 있음, 다른
                  유효한 이메일 입력 시 계정 없음 상태를 확인할 수 있어요.
                </span>
              </S.DemoNote>
            </S.Panel>
          )}

          {result === "success" && (
            <S.ResultPanel $tone="success" role="status" aria-live="polite">
              <S.ResultIcon $tone="success">
                <CircleCheck aria-hidden="true" />
              </S.ResultIcon>
              <S.ResultContent>
                <S.ResultTitle>가입한 아이디를 찾았어요</S.ResultTitle>
                <S.ResultDescription>
                  {method === "phone"
                    ? "본인인증 정보와 일치하는 계정입니다."
                    : `${email.trim()}로 아이디 안내도 전송했어요.`}
                </S.ResultDescription>
                <S.FoundId>
                  <span>아이디</span>
                  <strong>{DEMO_ACCOUNT.userId}</strong>
                </S.FoundId>
                <S.ResultActions>
                  <S.LoginButton to="/login">로그인하기</S.LoginButton>
                  <S.PasswordLink to="/find-password">
                    비밀번호도 찾기
                  </S.PasswordLink>
                </S.ResultActions>
              </S.ResultContent>
            </S.ResultPanel>
          )}

          {result === "empty" && (
            <S.ResultPanel $tone="empty" role="alert" aria-live="polite">
              <S.ResultIcon $tone="empty">
                <CircleAlert aria-hidden="true" />
              </S.ResultIcon>
              <S.ResultContent>
                <S.ResultTitle>일치하는 계정을 찾지 못했어요</S.ResultTitle>
                <S.ResultDescription>
                  입력한 정보를 다시 확인하거나 다른 방법으로 찾아보세요.
                </S.ResultDescription>
                <S.RetryButton type="button" onClick={resetResult}>
                  <RotateCcw aria-hidden="true" />
                  다시 입력하기
                </S.RetryButton>
              </S.ResultContent>
            </S.ResultPanel>
          )}

          <S.CardFooter>
            <span>아이디는 알지만 비밀번호가 기억나지 않나요?</span>
            <S.FooterLink to="/find-password">비밀번호 찾기</S.FooterLink>
          </S.CardFooter>
        </S.Card>
      </S.PageInner>
    </S.Page>
  );
}

export default FindIdPage;
