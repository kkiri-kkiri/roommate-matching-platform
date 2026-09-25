import axios from "axios";

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
});

async function request(config) {
  try {
    const accessToken = window.localStorage.getItem("accessToken");
    if (!accessToken) throw new Error("로그인 후 이용해주세요.");

    const response = await apiClient.request({
      ...config,
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    return response.data.data;
  } catch (error) {
    throw new Error(error.response?.data?.message || error.message || "회원 정보 요청에 실패했습니다.");
  }
}

export const getMyInfo = () =>
  request({ method: "get", url: "/users/me" });

// nickname, dormitory, grade, faculty, major,
// mbtiEnergy, mbtiInformation, mbtiDecision, mbtiLifestyle 중 변경할 필드만 전달합니다.
export const updateMyInfo = (payload) =>
  request({ method: "patch", url: "/users/me", data: payload });

export const changePassword = (currentPassword, newPassword, newPasswordConfirm) =>
  request({
    method: "patch",
    url: "/users/me/password",
    data: { currentPassword, newPassword, newPasswordConfirm },
  });
