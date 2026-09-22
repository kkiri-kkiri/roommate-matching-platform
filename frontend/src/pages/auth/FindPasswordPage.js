import { useState } from "react";
import {
    ArrowLeft,
    CircleAlert,
    CircleCheck,
    KeyRound,
    Mail,
    RotateCcw,
    ShieldCheck,
} from "lucide-react";
import * as S from "./FindPasswordPageCss";

import { requestPasswordReset, confirmPasswordReset } from "../../api/authApi";

function FindPasswordPage() {
    const [userId, setUserId] = useState("");
    const [email, setEmail] = useState("");
    const [lookupError, setLookupError] = useState("");
    const [resetToken, setResetToken] = useState("");
    const [pending, setPending] = useState(false);
    const [stage, setStage] = useState("lookup");
    const [newPassword, setNewPassword] = useState("");
    const [passwordConfirm, setPasswordConfirm] = useState("");
    const [passwordError, setPasswordError] = useState("");
    const [passwordErrorField, setPasswordErrorField] = useState("");

    const resetFlow = () => {
        setLookupError("");
        setResetToken("");
        setStage("lookup");
        setNewPassword("");
        setPasswordConfirm("");
        setPasswordError("");
        setPasswordErrorField("");
    };

    const handleLookupFieldChange = (setter) => (event) => {
        setter(event.target.value);
        resetFlow();
    };

    const handleEmailSubmit = async (event) => {
        event.preventDefault();
        if (pending) return;
        setPending(true);
        setLookupError("");
        try {
            await requestPasswordReset(userId.trim(), email.trim());
            setStage("emailSent");
        } catch (error) {
            setLookupError(error.message);
        } finally {
            setPending(false);
        }
    };

    const handleResetSubmit = async (event) => {
        event.preventDefault();
        if (pending) return;

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
        setPending(true);
        try {
            await confirmPasswordReset(resetToken.trim(), newPassword, passwordConfirm);
            setResetToken("");
            setNewPassword("");
            setPasswordConfirm("");
            setStage("complete");
        } catch (error) {
            setPasswordError(error.message);
        } finally {
            setPending(false);
        }
    };

    const renderLookupForm = () => (
        <>
            <S.SectionHeading>아이디와 가입 이메일을 입력해 주세요</S.SectionHeading>
            <S.SectionCopy>
                가입 정보와 일치하면 비밀번호 재설정 안내를 이메일로 보내 드려요.
            </S.SectionCopy>

            <S.Form onSubmit={handleEmailSubmit}>
                <S.Field>
                    <S.Label htmlFor="find-password-id">아이디</S.Label>
                    <S.InputWrap>
                        <S.InputIcon>
                            <KeyRound aria-hidden="true" />
                        </S.InputIcon>
                        <S.Input
                            id="find-password-id"
                            name="userId"
                            type="text"
                            autoComplete="username"
                            placeholder="아이디를 입력해 주세요"
                            value={userId}
                            disabled={pending}
                            onChange={handleLookupFieldChange(setUserId)}
                            required
                        />
                    </S.InputWrap>
                </S.Field>

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
                            disabled={pending}
                            onChange={handleLookupFieldChange(setEmail)}
                            required
                        />
                    </S.InputWrap>
                </S.Field>

                <S.PrimaryButton type="submit" disabled={pending}>
                    <Mail aria-hidden="true" />
                    {pending ? "요청 중..." : "비밀번호 재설정 이메일 보내기"}
                </S.PrimaryButton>
            </S.Form>

            {lookupError && (
                <S.ResultPanel $tone="empty" role="alert" aria-live="polite">
                    <S.ResultIcon $tone="empty">
                        <CircleAlert aria-hidden="true" />
                    </S.ResultIcon>
                    <S.ResultContent>
                        <S.ResultTitle>요청을 완료하지 못했어요</S.ResultTitle>
                        <S.ResultDescription>
                            {lookupError}
                        </S.ResultDescription>
                        <S.TextButton type="button" onClick={() => setLookupError("")}>
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
            <S.StepTitle>재설정 이메일을 확인해 주세요</S.StepTitle>
            <S.StepDescription>
                가입 정보가 일치하면 <strong>{email.trim()}</strong>으로 비밀번호 재설정 안내가 전송돼요.
                메일함을 확인해 주세요.
            </S.StepDescription>
            <S.InfoBox>
                메일이 보이지 않으면 스팸 메일함을 확인하거나 입력한 이메일이 맞는지
                확인해 주세요.
            </S.InfoBox>
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
            <S.VerifiedBadge role="status" aria-live="polite">
                <CircleCheck aria-hidden="true" />
                메일로 받은 재설정 토큰이 필요해요
            </S.VerifiedBadge>
            <S.SectionHeading>새 비밀번호를 설정해 주세요</S.SectionHeading>
            <S.SectionCopy>
                다른 서비스에서 사용하지 않는 안전한 비밀번호를 권장해요.
            </S.SectionCopy>

            <S.Form onSubmit={handleResetSubmit}>
                <S.Field>
                    <S.Label htmlFor="reset-token">재설정 토큰</S.Label>
                    <S.InputWrap>
                        <S.InputIcon><Mail aria-hidden="true" /></S.InputIcon>
                        <S.Input id="reset-token" name="resetToken" type="text"
                            autoComplete="off" placeholder="메일로 받은 재설정 토큰을 붙여 넣어 주세요"
                            value={resetToken} disabled={pending} required
                            onChange={(event) => { setResetToken(event.target.value); setPasswordError(""); }} />
                    </S.InputWrap>
                    <S.FieldHint>토큰이 만료되면 이메일을 다시 요청해 주세요.</S.FieldHint>
                </S.Field>
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
                            disabled={pending}
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
                            disabled={pending}
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

                <S.PrimaryButton type="submit" disabled={pending}>
                    <ShieldCheck aria-hidden="true" />
                    {pending ? "변경 중..." : "비밀번호 변경하기"}
                </S.PrimaryButton>
            </S.Form>

            <S.TextButton type="button" onClick={resetFlow} disabled={pending}>
                다른 계정 정보 입력하기
            </S.TextButton>
        </S.ResetSection>
    );

    const renderComplete = () => (
        <S.StepPanel role="status" aria-live="polite">
            <S.LargeStatusIcon $complete>
                <CircleCheck aria-hidden="true" />
            </S.LargeStatusIcon>
            <S.StepTitle>비밀번호를 변경했어요</S.StepTitle>
            <S.StepDescription>
                새로 설정한 비밀번호로 로그인해 주세요.
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
                                <KeyRound aria-hidden="true" />
                                ACCOUNT RECOVERY
                            </S.Eyebrow>
                            <S.Title>비밀번호 찾기</S.Title>
                            <S.Description>
                                가입 이메일을 확인하고 새로운 비밀번호를 설정해 보세요.
                            </S.Description>
                        </S.Intro>

                        <S.Panel id="find-password-panel">
                            {renderPanelContent()}
                        </S.Panel>

                        <S.CardFooter>
                            <span>아이디가 기억나지 않나요?</span>
                            <S.FooterLink to="/find-id">아이디 찾기</S.FooterLink>
                        </S.CardFooter>
                    </S.Card>
                </S.PageInner>
            </S.Page>
        </>
    );
}

export default FindPasswordPage;
