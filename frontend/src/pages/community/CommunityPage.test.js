import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import CommunityPage from "./CommunityPage";
import * as api from "../../api/communityApi";
import { createChatRoom } from "../../api/chatApi";

let mockPath;
let mockParams;
const mockNavigate = jest.fn();
const mockSetSearchParams = jest.fn();
jest.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
  useLocation: () => ({ pathname: mockPath, search: mockParams.toString(), hash: "" }),
  useParams: () => ({ postId: mockPath.match(/posts\/(\d+)/)?.[1] }),
  useSearchParams: () => [mockParams, mockSetSearchParams],
  Navigate: () => <div>로그인 이동</div>,
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));
jest.mock("../../api/communityApi");
jest.mock("../../api/chatApi");

const author = { id: 2, nickname: "같은이름" };
const currentUser = { id: 1, name: "같은이름" };
const post = { id: 12, author, category: "자유게시판", title: "서버 게시글", content: "서버 본문", createdAt: "2026-09-20T12:00:00", views: 2, likes: 4, liked: false, reported: false, images: [], comments: [
  { id: 8, author, parentCommentId: null, content: "서버 댓글", createdAt: "2026-09-20T12:00:00" },
] };
beforeEach(() => {
  jest.clearAllMocks();
  mockPath = "/community";
  mockParams = new URLSearchParams();
  window.scrollTo = jest.fn();
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  api.getCommunityPosts.mockResolvedValue({ content: [{ ...post, contentPreview: "미리보기", commentCount: 1, hasImages: true }], totalElements: 13, totalPages: 3 });
  api.getCommunityPost.mockResolvedValue(post);
});

test("uses server filtering, sorting and paging, and renders list DTO fields", async () => {
  mockParams = new URLSearchParams({ page: "2", category: "자유게시판", sort: "좋아요순", q: "검색" });
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("미리보기");
  expect(api.getCommunityPosts).toHaveBeenCalledWith({ page: 1, size: 6, category: "자유게시판", q: "검색", sort: "LIKES" });
  expect(screen.getByLabelText("사진 첨부")).toBeInTheDocument();
  expect(screen.queryByText("내 글")).not.toBeInTheDocument();
});

test.each([['/community/my-posts', 'getMyCommunityPosts'], ['/community/my-comments', 'getMyCommunityComments']])("loads activity from %s", async (path, method) => {
  mockPath = path;
  api[method].mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 });
  render(<CommunityPage currentUser={currentUser} />);
  await waitFor(() => expect(api[method]).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 6 })));
  expect(api.getCommunityPosts).not.toHaveBeenCalled();
});

test("refetches likes, preserves failed comment drafts, and uses numeric chat IDs", async () => {
  mockPath = "/community/posts/12";
  api.likeCommunityPost.mockResolvedValue(undefined);
  api.createCommunityComment.mockRejectedValue(new Error("댓글 등록 실패"));
  createChatRoom.mockResolvedValue({ id: 42 });
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("서버 본문");
  expect(screen.queryByRole("button", { name: "수정" })).not.toBeInTheDocument();
  expect(screen.getByLabelText("댓글 좋아요 (지원 준비 중)")).toBeDisabled();
  api.getCommunityPost.mockResolvedValue({ ...post, liked: true, likes: 5 });
  fireEvent.click(screen.getByLabelText("좋아요 4개"));
  await screen.findByLabelText("좋아요 5개");
  expect(api.likeCommunityPost).toHaveBeenCalledWith(12);
  expect(api.getCommunityPost).toHaveBeenCalledTimes(2);
  const input = screen.getByLabelText("같은이름님의 댓글");
  fireEvent.change(input, { target: { value: "잃어버리면 안 되는 내용" } });
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await screen.findByText("댓글 등록 실패");
  expect(input).toHaveValue("잃어버리면 안 되는 내용");
  fireEvent.click(screen.getAllByLabelText("같은이름님에게 채팅 보내기")[1]);
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/chat?room=42"));
  expect(createChatRoom).toHaveBeenCalledWith({ targetUserId: 2, category: "COMMUNITY", sourceFrom: "COMMENT", sourcePostId: 12, sourceCommentId: 8 });
});

test("shows report errors inside the dialog and only closes after success", async () => {
  mockPath = "/community/posts/12";
  api.createCommunityReport.mockRejectedValueOnce(new Error("신고 실패")).mockResolvedValueOnce(undefined);
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("서버 본문");
  fireEvent.click(screen.getByRole("button", { name: "게시글 신고" }));
  fireEvent.click(screen.getByLabelText("광고·홍보"));
  fireEvent.click(screen.getByRole("button", { name: "신고하기", hidden: true }));
  await screen.findByText("신고 실패");
  expect(screen.getByText("신고 실패").closest("dialog")).not.toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "신고하기", hidden: true }));
  await screen.findByText(/신고를 접수했어요/);
  expect(api.createCommunityReport).toHaveBeenLastCalledWith({ targetType: "POST", targetId: 12, reason: "광고·홍보", description: null });
});

test("creates a post and navigates using its server ID", async () => {
  mockPath = "/community/posts/new";
  api.createCommunityPost.mockResolvedValue(88);
  render(<CommunityPage currentUser={currentUser} />);
  fireEvent.change(screen.getByLabelText(/제목/), { target: { value: "새 제목" } });
  fireEvent.change(screen.getByLabelText(/내용/), { target: { value: "새 내용" } });
  fireEvent.click(screen.getByRole("button", { name: "게시글 등록" }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/community/posts/88", { replace: true }));
  expect(api.createCommunityPost).toHaveBeenCalledWith({ category: "자유게시판", title: "새 제목", content: "새 내용" }, []);
});

test("shows server errors for missing posts", async () => {
  mockPath = "/community/posts/999";
  api.getCommunityPost.mockRejectedValue(new Error("존재하지 않는 게시글입니다."));
  render(<CommunityPage />);
  await screen.findByRole("alert");
  expect(screen.getByText("게시글을 찾을 수 없어요")).toBeInTheDocument();
});

test("editing retains only selected server image IDs and sends new Files separately", async () => {
  mockPath = "/community/posts/12/edit";
  api.getCommunityPost.mockResolvedValue({ ...post, author: { id: 1, nickname: "작성자" }, images: [{ id: 10, url: "/old.png" }, { id: 11, url: "/remove.png" }] });
  api.updateCommunityPost.mockResolvedValue(undefined);
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("게시글 수정");
  fireEvent.click(screen.getByLabelText("이미지 2 삭제"));
  const file = new File(["png"], "new.png", { type: "image/png" });
  fireEvent.change(screen.getByLabelText("첨부할 이미지 선택"), { target: { files: [file] } });
  await screen.findByText("new.png");
  fireEvent.click(screen.getByRole("button", { name: "수정 완료" }));
  await waitFor(() => expect(api.updateCommunityPost).toHaveBeenCalledWith(12, { category: "자유게시판", title: "서버 게시글", content: "서버 본문", keepImageIds: [10] }, [file]));
  expect(mockNavigate).toHaveBeenCalledWith("/community/posts/12", { replace: true });
});
