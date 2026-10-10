import axios from "axios";
import { getCommunityPost } from "./communityApi";

jest.mock("axios", () => ({ create: jest.fn(() => ({ request: jest.fn() })) }));
const request = axios.create.mock.results[0].value.request;

beforeEach(() => request.mockReset());

test("unwraps the existing post response", async () => {
  request.mockResolvedValue({ data: { success: true, data: { id: 84, title: "게시글" } } });
  await expect(getCommunityPost(84)).resolves.toEqual({ id: 84, title: "게시글" });
});

test.each([
  [400, "INVALID_REQUEST"], [401, "UNAUTHORIZED"], [403, "FORBIDDEN"], [500, "INTERNAL_SERVER_ERROR"],
])("preserves HTTP status %s and errorCode %s alongside the existing message", async (status, errorCode) => {
  request.mockRejectedValue({ response: { status, data: { errorCode, message: "서버 오류 안내" } } });
  await expect(getCommunityPost(84)).rejects.toMatchObject({ status, errorCode, message: "서버 오류 안내" });
});

test("does not invent a deletion code for a network failure", async () => {
  request.mockRejectedValue(new Error("Network Error"));
  await expect(getCommunityPost(84)).rejects.toMatchObject({ message: "Network Error", status: undefined, errorCode: undefined });
});
