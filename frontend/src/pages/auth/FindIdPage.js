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

import { findId, sendEmailVerification, verifyEmailVerification } from "../../api/authApi";

function FindIdPage() {
    const [email, setEmail] = useState("");
    const [result, setResult] = useState("idle");
    const [code, setCode] = useState("");
    const [codeSent, setCodeSent] = useState(false);
    const [verificationToken, setVerificationToken] = useState("");
    const [loginId, setLoginId] = useState("");
    const [error, setError] = useState("");
    const [pending, setPending] = useState(false);

    const resetResult = () => { setResult("idle"); setError(""); };

    const handleEmailSubmit = async (event) => {
        event.preventDefault();
        if (pending) return;
        setPending(true);
        resetResult();
        try {
            await sendEmailVerification(email.trim(), "FIND_ID");
            setCodeSent(true);
            setCode("");
            setVerificationToken("");
            setLoginId("");
        } catch (error) {
            setError(error.message);
        } finally {
            setPending(false);
        }
    };

    const handleVerifySubmit = async (event) => {
        event.preventDefault();
        if (pending) return;
        setPending(true);
        resetResult();
        try {
            const token = verificationToken || await verifyEmailVerification(email.trim(), code.trim(), "FIND_ID");
            setVerificationToken(token);
            const data = await findId(email.trim(), token);
            setLoginId(data.loginId);
            setResult("success");
            setCodeSent(false);
            setVerificationToken("");
        } catch (error) {
            setError(error.message);
        } finally {
            setPending(false);
        }
    };

    const handleEmailChange = (event) => {
        setEmail(event.target.value);
        setCodeSent(false);
        setCode("");
        setVerificationToken("");
        setLoginId("");
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
                                이메일로 받은 인증번호를 확인하면 가입한 아이디를 보여 드려요.
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
                                            disabled={pending}
                                            onChange={handleEmailChange}
                                            required
                                        />
                                    </S.InputWrap>
                                </S.Field>

                                <S.PrimaryButton type="submit" disabled={pending}>
                                    <Mail aria-hidden="true" />
                                    {pending ? "처리 중..." : codeSent ? "인증번호 다시 보내기" : "인증번호 보내기"}
                                </S.PrimaryButton>
                            </S.Form>

                            {codeSent && (
                                <S.Form onSubmit={handleVerifySubmit}>
                                    <S.SectionCopy role="status">이메일로 보낸 인증번호 6자리를 입력해 주세요.</S.SectionCopy>
                                    <S.Field>
                                        <S.Label htmlFor="find-id-code">인증번호</S.Label>
                                        <S.InputWrap>
                                            <S.InputIcon><Mail aria-hidden="true" /></S.InputIcon>
                                            <S.Input id="find-id-code" value={code}
                                                onChange={(event) => { setCode(event.target.value); setError(""); }}
                                                inputMode="numeric" autoComplete="one-time-code"
                                                pattern="[0-9]{6}" maxLength={6} required
                                                disabled={pending || Boolean(verificationToken)} />
                                        </S.InputWrap>
                                    </S.Field>
                                    <S.PrimaryButton type="submit" disabled={pending}>
                                        {pending ? "확인 중..." : "인증하고 아이디 찾기"}
                                    </S.PrimaryButton>
                                </S.Form>
                            )}
                        </S.Panel>

                        {result === "success" && (
                            <S.ResultPanel $tone="success" role="status" aria-live="polite">
                                <S.ResultIcon $tone="success">
                                    <CircleCheck aria-hidden="true" />
                                </S.ResultIcon>
                                <S.ResultContent>
                                    <S.ResultTitle>가입한 아이디를 찾았어요</S.ResultTitle>
                                    <S.ResultDescription>
                                        {email.trim()}로 가입한 아이디예요.
                                    </S.ResultDescription>
                                    <S.FoundId>
                                        <span>아이디</span>
                                        <strong>{loginId}</strong>
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

                        {error && (
                            <S.ResultPanel $tone="empty" role="alert" aria-live="polite">
                                <S.ResultIcon $tone="empty">
                                    <CircleAlert aria-hidden="true" />
                                </S.ResultIcon>
                                <S.ResultContent>
                                    <S.ResultTitle>요청을 완료하지 못했어요</S.ResultTitle>
                                    <S.ResultDescription>
                                        {error}
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
