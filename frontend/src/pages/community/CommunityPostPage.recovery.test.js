import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import CommunityPostPage from "./CommunityPostPage";
import { normalizePost } from "./communityData";
import * as api from "../../api/communityApi";

jest.mock("../../api/communityApi");
jest.mock("react-router-dom", () => ({ useNavigate: () => jest.fn(),
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a> }));

const post = normalizePost({ id: 1, title: "제목", content: "내용", category: "자유",
  author: { id: 2, nickname: "이웃" }, views: 1, likes: 0, createdAt: "2026-10-23T10:00:00", comments: [
    { id: 10, content: "부모 댓글", author: { id: 2, nickname: "이웃" }, createdAt: "2026-10-23T10:00:00" },
  ] });
let refresh;
beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(window, "alert").mockImplementation(() => {});
  refresh = jest.fn().mockRejectedValueOnce(new Error("GET 503")).mockResolvedValue();
  api.createCommunityComment.mockResolvedValue({ id: 11 });
  api.createCommunityReply.mockResolvedValue({ id: 12 });
});

test.each([false, true])("confirmed comment (anonymous=%s) is not posted again after failed refresh", async (anonymous) => {
  render(<CommunityPostPage post={post} currentUser={{ id: 7, name: "나" }} onRefresh={refresh} />);
  const input = screen.getByRole("textbox", { name: "나님의 댓글" });
  fireEvent.change(input, { target: { value: "저장된 댓글" } });
  if (anonymous) fireEvent.click(screen.getByLabelText("댓글 익명"));
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await screen.findByRole("alert");
  await waitFor(() => expect(input).toHaveValue(""));
  expect(window.alert).not.toHaveBeenCalled();
  expect(api.createCommunityComment).toHaveBeenCalledWith(1, "저장된 댓글", anonymous);
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  fireEvent.click(screen.getByRole("button", { name: "댓글 목록 새로고침" }));
  await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  expect(refresh).toHaveBeenCalledTimes(2);
  expect(api.createCommunityComment).toHaveBeenCalledTimes(1);
});

test.each([false, true])("confirmed reply (anonymous=%s) closes composer and refresh retry cannot POST", async (anonymous) => {
  render(<CommunityPostPage post={post} currentUser={{ id: 7, name: "나" }} onRefresh={refresh} />);
  fireEvent.click(screen.getByRole("button", { name: "답글" }));
  const input = screen.getByRole("textbox", { name: "이웃님에게 답글 남기기" });
  fireEvent.change(input, { target: { value: "저장된 답글" } });
  if (anonymous) fireEvent.click(screen.getByLabelText("답글 익명"));
  fireEvent.click(screen.getByRole("button", { name: "답글 등록" }));
  await screen.findByRole("alert");
  expect(screen.queryByRole("textbox", { name: "이웃님에게 답글 남기기" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "댓글 목록 새로고침" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(api.createCommunityReply).toHaveBeenCalledTimes(1);
  expect(api.createCommunityReply).toHaveBeenCalledWith(10, "저장된 답글", anonymous);
  expect(window.alert).not.toHaveBeenCalled();
});

test("failed POST retains draft and anonymity and never tries the follow-up GET", async () => {
  api.createCommunityComment.mockRejectedValueOnce(new Error("저장 실패"));
  render(<CommunityPostPage post={post} currentUser={{ id: 7, name: "나" }} onRefresh={refresh} />);
  const input = screen.getByRole("textbox", { name: "나님의 댓글" });
  fireEvent.change(input, { target: { value: "복구할 댓글" } });
  fireEvent.click(screen.getByLabelText("댓글 익명"));
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith("저장 실패"));
  expect(input).toHaveValue("복구할 댓글");
  expect(screen.getByLabelText("댓글 익명")).toBeChecked();
  expect(refresh).not.toHaveBeenCalled();
});

test.each(["edit", "delete"])("confirmed anonymous comment %s also finishes when refresh fails", async (action) => {
  const ownPost = { ...post, comments: post.comments.map((item) => ({ ...item, mine: true,
    anonymous: true, anonymousNumber: 3, author: { id: null, nickname: "익명3" } })) };
  api.updateCommunityComment.mockResolvedValue();
  api.deleteCommunityComment.mockResolvedValue();
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  render(<CommunityPostPage post={ownPost} currentUser={{ id: 7, name: "나" }} onRefresh={refresh} />);
  if (action === "edit") {
    fireEvent.click(screen.getByRole("button", { name: "수정" }));
    fireEvent.change(screen.getByLabelText("댓글 수정"), { target: { value: "수정 내용" } });
    fireEvent.click(screen.getByRole("button", { name: "수정 완료" }));
  } else {
    fireEvent.click(screen.getByRole("button", { name: "삭제" }));
    fireEvent.click(screen.getByRole("button", { name: "삭제하기" }));
  }
  await screen.findByRole("alert");
  expect(screen.queryByLabelText("댓글 수정")).not.toBeInTheDocument();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "댓글 목록 새로고침" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledTimes(2));
  expect(action === "edit" ? api.updateCommunityComment : api.deleteCommunityComment).toHaveBeenCalledTimes(1);
  expect(window.alert).not.toHaveBeenCalled();
});
