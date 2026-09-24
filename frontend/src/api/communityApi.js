import axios from "axios";

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api",
});

async function request(config, authenticated = true) {
  try {
    const token = window.localStorage.getItem("accessToken");
    if (authenticated && !token) throw new Error("로그인 후 이용해주세요.");
    const response = await apiClient.request({
      ...config, headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    return response.data.data;
  } catch (error) {
    throw new Error(error.response?.data?.message || error.message || "커뮤니티 요청에 실패했습니다.");
  }
}

function multipart(payload, images = []) {
  const data = new FormData();
  data.append("request", new Blob([JSON.stringify(payload)], { type: "application/json" }));
  images.filter((image) => image instanceof File).forEach((image) => data.append("images", image));
  return data;
}

export const getCommunityPosts = ({ page = 0, size = 6, category, q, sort = "LATEST" } = {}) =>
  request({ method: "get", url: "/posts", params: { page, size, category: category === "전체" ? undefined : category, q, sort } }, false);
export const getCommunityPost = (id) => request({ method: "get", url: `/posts/${id}` }, false);
export const createCommunityPost = (payload, images) => request({ method: "post", url: "/posts", data: multipart(payload, images) });
export const updateCommunityPost = (id, payload, images) => request({ method: "patch", url: `/posts/${id}`, data: multipart(payload, images) });
export const deleteCommunityPost = (id) => request({ method: "delete", url: `/posts/${id}` });
export const likeCommunityPost = (id) => request({ method: "post", url: `/posts/${id}/likes` });
export const unlikeCommunityPost = (id) => request({ method: "delete", url: `/posts/${id}/likes` });
export const createCommunityComment = (id, content) => request({ method: "post", url: `/posts/${id}/comments`, data: { content } });
export const createCommunityReply = (id, content) => request({ method: "post", url: `/comments/${id}/replies`, data: { content } });
export const updateCommunityComment = (id, content) => request({ method: "patch", url: `/comments/${id}`, data: { content } });
export const deleteCommunityComment = (id) => request({ method: "delete", url: `/comments/${id}` });
export const likeCommunityComment = (id) => request({ method: "post", url: `/comments/${id}/likes` });
export const unlikeCommunityComment = (id) => request({ method: "delete", url: `/comments/${id}/likes` });
export const getMyCommunityPosts = ({ page = 0, size = 6, sort = "LATEST" } = {}) =>
  request({ method: "get", url: "/users/me/posts", params: { page, size, sort } });
export const getMyCommunityComments = ({ page = 0, size = 6 } = {}) =>
  request({ method: "get", url: "/users/me/comments", params: { page, size } });
export const createCommunityReport = (payload) => request({ method: "post", url: "/reports", data: payload });
