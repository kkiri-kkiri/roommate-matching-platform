import axios from "axios";

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
});

export async function checkSignupAvailability(params) {
  const response = await apiClient.get("/auth/signup/availability", {
    params,
  });
  return response.data.data;
}

function getApiErrorMessage(error, fallbackMessage) {
  return error.response?.data?.message || fallbackMessage;
}

export async function login(loginId, password) {
  try {
    const response = await apiClient.post("/auth/login", { loginId, password });
    return response.data.data;
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "로그인에 실패했습니다."));
  }
}

export async function sendEmailVerification(email) {
  try {
    await apiClient.post("/auth/email-verifications/send", {
      email,
      purpose: "SIGNUP",
    });
  } catch (error) {
    throw new Error(getApiErrorMessage(error, "인증번호 전송에 실패했습니다."));
  }
}

export async function verifyEmailVerification(email, code) {
  try {
    const response = await apiClient.post("/auth/email-verifications/verify", {
      email,
      code,
      purpose: "SIGNUP",
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
