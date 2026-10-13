import axios from "axios";

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
  headers: { "Content-Type": "application/json" },
  timeout: 60000,
});

async function request(config) {
  const token = window.localStorage.getItem("accessToken");
  if (!token) throw new Error("로그인 후 이용해주세요.");
  try {
    const response = await apiClient.request({
      ...config, headers: { Authorization: `Bearer ${token}` },
    });
    return response.data.data;
  } catch (error) {
    if (error.code === "ERR_CANCELED") throw error;
    throw new Error(error.response?.data?.message || "설문 요청에 실패했어요. 다시 시도해 주세요.");
  }
}

export const getMyPreferences = () =>
  request({ method: "get", url: "/users/me/roommate-preferences" });

export const saveMyPreferences = (answers) =>
  request({ method: "put", url: "/users/me/roommate-preferences", data: answers });

function profileSurveyResponse(data) {
  if (typeof data?.completed !== "boolean"
      || (data.answers !== null && (typeof data.answers !== "object" || Array.isArray(data.answers)))
      || (data.completed && !data.answers)) {
    throw new Error("내 설문 응답을 확인할 수 없습니다. 다시 조회해 주세요.");
  }
  return data;
}

export const getMyProfileSurvey = async (signal) => profileSurveyResponse(
  await request({ method: "get", url: "/users/me/profile-survey", signal }),
);

export const saveMyProfileSurvey = async (answers) => profileSurveyResponse(
  await request({ method: "put", url: "/users/me/profile-survey", data: answers }),
);
