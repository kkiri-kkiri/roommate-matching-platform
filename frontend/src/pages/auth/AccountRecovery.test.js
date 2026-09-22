import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import axios from "axios";
import FindIdPage from "./FindIdPage";
import FindPasswordPage from "./FindPasswordPage";
import { sendEmailVerification, verifyEmailVerification } from "../../api/authApi";

jest.mock("axios", () => {
    const client = { post: jest.fn() };
    return { create: () => client };
});
jest.mock("react-router-dom", () => ({
    Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));

// Access the same client used by authApi, without contacting a real mail server.
const mockPost = axios.create().post;

beforeEach(() => {
    mockPost.mockReset();
    mockPost.mockImplementation(async (path) => ({ data: { data:
        path.endsWith("/verify") ? { verificationToken: "verified-token" }
            : path.endsWith("/find-id") ? { loginId: "student-account" }
                : { sent: true },
    } }));
});

function fill(label, value) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

test("finds a real account only after FIND_ID email verification", async () => {
    render(<FindIdPage />);
    fill("이메일", "student@hknu.ac.kr");
    fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
    await screen.findByLabelText("인증번호");
    fill("인증번호", "123456");
    fireEvent.click(screen.getByRole("button", { name: "인증하고 아이디 찾기" }));
    await screen.findByText("student-account");
    expect(mockPost.mock.calls).toEqual([
        ["/auth/email-verifications/send", { email: "student@hknu.ac.kr", purpose: "FIND_ID" }],
        ["/auth/email-verifications/verify", { email: "student@hknu.ac.kr", code: "123456", purpose: "FIND_ID" }],
        ["/auth/find-id", { email: "student@hknu.ac.kr", emailVerificationToken: "verified-token" }],
    ]);
});

test("does not look up an ID when the verification code is rejected", async () => {
    render(<FindIdPage />);
    fill("이메일", "student@hknu.ac.kr");
    fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
    await screen.findByLabelText("인증번호");
    mockPost.mockRejectedValueOnce({ response: { data: { message: "인증번호가 일치하지 않습니다." } } });
    fill("인증번호", "000000");
    fireEvent.click(screen.getByRole("button", { name: "인증하고 아이디 찾기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("인증번호가 일치하지 않습니다.");
    expect(mockPost).toHaveBeenCalledTimes(2);
    expect(screen.queryByText("가입한 아이디를 찾았어요")).not.toBeInTheDocument();
});

test("changing the email clears its verification step", async () => {
    render(<FindIdPage />);
    fill("이메일", "student@hknu.ac.kr");
    fireEvent.click(screen.getByRole("button", { name: "인증번호 보내기" }));
    await screen.findByLabelText("인증번호");
    fill("이메일", "another@hknu.ac.kr");
    expect(screen.queryByLabelText("인증번호")).not.toBeInTheDocument();
});

async function openResetForm() {
    render(<FindPasswordPage />);
    fill("아이디", "StudentId");
    fill("이메일", "student@hknu.ac.kr");
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 재설정 이메일 보내기" }));
    fireEvent.click(await screen.findByRole("button", { name: "메일의 재설정 토큰 입력하기" }));
    fill("재설정 토큰", "mail-token");
    fill("새 비밀번호", "new-password123");
    fill("새 비밀번호 확인", "new-password123");
}

test("changes a non-demo account password using the emailed token", async () => {
    await openResetForm();
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 변경하기" }));
    await screen.findByText("비밀번호를 변경했어요");
    expect(mockPost.mock.calls).toEqual([
        ["/auth/password-reset/request", { loginId: "StudentId", email: "student@hknu.ac.kr" }],
        ["/auth/password-reset/confirm", { resetToken: "mail-token", newPassword: "new-password123", newPasswordConfirm: "new-password123" }],
    ]);
});

test("invalid tokens do not show password reset success and can be retried", async () => {
    await openResetForm();
    mockPost.mockRejectedValueOnce({ response: { data: { message: "만료된 토큰입니다." } } });
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 변경하기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("만료된 토큰입니다.");
    expect(screen.queryByText("비밀번호를 변경했어요")).not.toBeInTheDocument();
    fill("재설정 토큰", "new-mail-token");
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 변경하기" }));
    await screen.findByText("비밀번호를 변경했어요");
});

test("shows request errors without claiming the account does not exist", async () => {
    mockPost.mockRejectedValueOnce(new Error("Network Error"));
    render(<FindPasswordPage />);
    fill("아이디", "StudentId");
    fill("이메일", "student@hknu.ac.kr");
    fireEvent.click(screen.getByRole("button", { name: "비밀번호 재설정 이메일 보내기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("재설정 이메일 요청에 실패했습니다.");
    await waitFor(() => expect(screen.getByRole("button", { name: "비밀번호 재설정 이메일 보내기" })).toBeEnabled());
});

test("signup email verification retains its original purpose", async () => {
    await sendEmailVerification("student@hknu.ac.kr");
    await verifyEmailVerification("student@hknu.ac.kr", "123456");
    expect(mockPost.mock.calls).toEqual([
        ["/auth/email-verifications/send", { email: "student@hknu.ac.kr", purpose: "SIGNUP" }],
        ["/auth/email-verifications/verify", { email: "student@hknu.ac.kr", code: "123456", purpose: "SIGNUP" }],
    ]);
});
