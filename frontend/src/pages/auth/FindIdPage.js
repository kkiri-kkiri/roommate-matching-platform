import { useState } from "react";
import {
    ArrowLeft,
    CircleAlert,
    CircleCheck,
    Mail,
    RotateCcw,
    Search,
} from "lucide-react";
import * as S from "./FindIdPageCss";

const DEMO_ACCOUNT = {
    email: "test01@hknu.ac.kr",
    userId: "test01",
};

function FindIdPage() {
    const [email, setEmail] = useState("");
    const [result, setResult] = useState("idle");

    const resetResult = () => setResult("idle");

    const handleEmailSubmit = (event) => {
        event.preventDefault();
        const normalizedEmail = email.trim().toLowerCase();
        setResult(normalizedEmail === DEMO_ACCOUNT.email ? "success" : "empty");
    };

    const handleEmailChange = (event) => {
        setEmail(event.target.value);
        resetResult();
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
                            <S.Eyebrow>
                                <Search aria-hidden="true" />
                                ACCOUNT RECOVERY
                            </S.Eyebrow>
                            <S.Title>아이디 찾기</S.Title>
                            <S.Description>
                                가입할 때 등록한 이메일로 아이디를 안전하게 확인해 보세요.
                            </S.Description>
                        </S.Intro>

                        <S.Panel id="find-id-panel">
                            <S.SectionHeading>가입 이메일을 입력해 주세요</S.SectionHeading>
                            <S.SectionCopy>
                                가입 정보와 일치하면 아이디 안내를 입력한 이메일로 전송해 드려요.
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
                                    <strong>test01@hknu.ac.kr</strong> 입력 시 계정 있음, 다른
                                    유효한 이메일 입력 시 계정 없음 상태를 확인할 수 있어요.
                                </span>
                            </S.DemoNote>
                        </S.Panel>

                        {result === "success" && (
                            <S.ResultPanel $tone="success" role="status" aria-live="polite">
                                <S.ResultIcon $tone="success">
                                    <CircleCheck aria-hidden="true" />
                                </S.ResultIcon>
                                <S.ResultContent>
                                    <S.ResultTitle>가입한 아이디를 찾았어요</S.ResultTitle>
                                    <S.ResultDescription>
                                        {email.trim()}로 아이디 안내도 전송했어요.
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
                                        입력한 이메일 주소를 다시 확인해 주세요.
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
        </>
    );
}

export default FindIdPage;
