import axios from "axios";
import * as api from "./communityApi";

jest.mock("axios", () => ({ create: jest.fn(() => ({ request: jest.fn() })) }));
const request = axios.create.mock.results[0].value.request;
beforeEach(() => {
  localStorage.clear();
  request.mockReset().mockResolvedValue({ data: { success: true, data: 12 } });
});

test("public reads allow guests and attach the current token when present", async () => {
  await api.getCommunityPosts({ category: "전체", page: 1, q: "검색", sort: "LIKES" });
  expect(request).toHaveBeenLastCalledWith({ method: "get", url: "/posts", headers: {}, params: { page: 1, size: 6, category: undefined, q: "검색", sort: "LIKES" } });
  localStorage.setItem("accessToken", "fresh");
  await expect(api.getCommunityPost(12)).resolves.toBe(12);
  expect(request).toHaveBeenLastCalledWith({ method: "get", url: "/posts/12", headers: { Authorization: "Bearer fresh" } });
});

const authenticatedCalls = [
  [() => api.createCommunityPost({ title: "제목" }), "post", "/posts"],
  [() => api.updateCommunityPost(12, {}), "patch", "/posts/12"],
  [() => api.deleteCommunityPost(12), "delete", "/posts/12"],
  [() => api.likeCommunityPost(12), "post", "/posts/12/likes"],
  [() => api.unlikeCommunityPost(12), "delete", "/posts/12/likes"],
  [() => api.createCommunityComment(12, "댓글"), "post", "/posts/12/comments", { content: "댓글" }],
  [() => api.createCommunityReply(3, "답글"), "post", "/comments/3/replies", { content: "답글" }],
  [() => api.updateCommunityComment(3, "수정"), "patch", "/comments/3", { content: "수정" }],
  [() => api.deleteCommunityComment(3), "delete", "/comments/3"],
  [() => api.likeCommunityComment(3), "post", "/comments/3/likes"],
  [() => api.unlikeCommunityComment(3), "delete", "/comments/3/likes"],
  [() => api.getMyCommunityPosts(), "get", "/users/me/posts"],
  [() => api.getMyCommunityComments(), "get", "/users/me/comments"],
  [() => api.createCommunityReport({ targetType: "COMMENT", targetId: 3, reason: "기타", description: "사유" }), "post", "/reports", { targetType: "COMMENT", targetId: 3, reason: "기타", description: "사유" }],
];
test.each(authenticatedCalls.map(([call, method, url, data]) => [call, method, url, data]))("authenticated REST contract %#", async (call, method, url, data) => {
  await expect(call()).rejects.toThrow("로그인 후 이용해주세요.");
  expect(request).not.toHaveBeenCalled();
  localStorage.setItem("accessToken", "token");
  await call();
  expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ method, url, headers: { Authorization: "Bearer token" }, ...(data ? { data } : {}) }));
});

function readBlob(blob) {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.readAsText(blob);
  });
}

test("multipart sends JSON and only actual new Files without overriding the boundary", async () => {
  localStorage.setItem("accessToken", "token");
  const payload = { category: "자유게시판", title: "제목", content: "내용", keepImageIds: [9] };
  const file = new File(["image"], "new.png", { type: "image/png" });
  await api.updateCommunityPost(12, payload, [file, { id: 9, url: "/old.png" }, "data:image/png;base64,test"]);
  const { data, headers } = request.mock.calls[0][0];
  expect(JSON.parse(await readBlob(data.get("request")))).toEqual(payload);
  expect(data.get("request").type).toBe("application/json");
  expect(data.getAll("images")).toEqual([file]);
  expect(headers["Content-Type"]).toBeUndefined();
});

test("preserves server error messages", async () => {
  request.mockRejectedValue({ response: { data: { message: "존재하지 않는 게시글입니다." } } });
  await expect(api.getCommunityPost(999)).rejects.toThrow("존재하지 않는 게시글입니다.");
});
