import { createApiClient } from "./apiClient";

const sessionClient = createApiClient();

export async function getAuthenticatedUser(token) {
  const response = await sessionClient.get("/auth/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
  return response.data.data;
}

const apiClient = createApiClient({
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

export async function checkSignupAvailability(params) {
  const response = await apiClient.get("/auth/signup/availability", {
    params,
  });
  return response.data.data;
}

function getApiErrorMessage(error, fallbackMessage) {
  if (["ECONNABORTED", "ETIMEDOUT"].includes(error.code)) {
    return "응답 시간이 초과되었습니다. 요청이 이미 처리됐을 수 있으니 메일이나 로그인 결과를 확인한 후 다시 시도해 주세요.";
  }
  if (!error.response) {
    return "서버에 연결할 수 없습니다. 요청이 이미 처리됐을 수 있으니 결과를 확인하고 연결 상태를 점검해 주세요.";
  }
  return error.response?.data?.message || fallbackMessage;
}

export async function checkLoginEmail(email) {
  try {
    const response = await apiClient.post("/auth/login/email-check", { email });
    return response.data.data;
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "이메일 확인에 실패했습니다."));
  }
}

export async function login(email, password) {
  try {
    const response = await apiClient.post("/auth/login", { email, password });
    return response.data.data;
  } catch (error) {
    if (error.response?.status === 401) {
      throw new Error("비밀번호가 일치하지 않습니다.");
    }
    throw new Error(getApiErrorMessage(error, "로그인에 실패했습니다."));
  }
}

export async function sendEmailVerification(email, purpose = "SIGNUP") {
  try {
    await apiClient.post("/auth/email-verifications/send", {
      email,
      purpose,
    }, { timeout: 30000 });
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "인증번호 전송에 실패했습니다."));
  }
}

export async function verifyEmailVerification(email, code, purpose = "SIGNUP") {
  try {
    const response = await apiClient.post("/auth/email-verifications/verify", {
      email,
      code,
      purpose,
    });
    return response.data.data.verificationToken;
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "인증번호가 올바르지 않습니다."));
  }
}

export async function signup(payload) {
  try {
    const response = await apiClient.post("/auth/signup", payload);
    return response.data.data;
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "회원가입에 실패했습니다."));
  }
}

export async function requestPasswordReset(email) {
  try {
    const response = await apiClient.post("/auth/password-reset/request", { email }, { timeout: 30000 });
    return response.data.data;
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "재설정 이메일 요청에 실패했습니다."));
  }
}

export async function confirmPasswordReset(resetToken, newPassword, newPasswordConfirm) {
  try {
    await apiClient.post("/auth/password-reset/confirm", { resetToken, newPassword, newPasswordConfirm });
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "비밀번호 변경에 실패했습니다. 재설정 토큰을 확인하거나 이메일을 다시 요청해 주세요."));
  }
}
