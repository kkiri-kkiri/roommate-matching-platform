import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import { getProfileImage } from "../../components/common/ProfileImage";
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
  { id: 8, author, parentCommentId: null, content: "서버 댓글", createdAt: "2026-09-20T12:00:00", anonymous: false, anonymousNumber: null, mine: false },
] };
beforeEach(() => {
  jest.resetAllMocks();
  jest.spyOn(window, "alert").mockImplementation(() => {});
  mockPath = "/community";
  mockParams = new URLSearchParams();
  window.scrollTo = jest.fn();
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  api.getCommunityPosts.mockResolvedValue({ content: [{ ...post, contentPreview: "미리보기", commentCount: 1, hasImages: true }], totalElements: 13, totalPages: 3 });
  api.getCommunityPost.mockResolvedValue(post);
});
afterEach(() => jest.restoreAllMocks());

test("uses server filtering, sorting and paging, and renders list DTO fields", async () => {
  mockParams = new URLSearchParams({ page: "2", category: "자유게시판", sort: "좋아요순", q: "검색" });
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("미리보기");
  expect(api.getCommunityPosts).toHaveBeenCalledWith({ page: 1, size: 6, category: "자유게시판", q: "검색", dormitory: "전체", sort: "LIKES" });
  expect(screen.getByLabelText("사진 첨부")).toBeInTheDocument();
  expect(screen.queryByText("내 글")).not.toBeInTheDocument();
});

test.each(["나래관", "호연관", "비봉관", "창조관"])("changes the dormitory filter and resets paging: %s", async (dormitory) => {
  mockParams = new URLSearchParams({ page: "2", category: "자유게시판", q: "검색", sort: "조회순" });
  const { rerender } = render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("미리보기");
  fireEvent.change(screen.getByLabelText("생활관 필터"), { target: { value: dormitory } });
  mockParams = mockSetSearchParams.mock.calls.at(-1)[0];
  expect(mockParams.has("page")).toBe(false);
  rerender(<CommunityPage currentUser={currentUser} />);
  await waitFor(() => expect(api.getCommunityPosts).toHaveBeenLastCalledWith({
    page: 0, size: 6, category: "자유게시판", q: "검색", sort: "VIEWS", dormitory,
  }));
  await screen.findByText("미리보기");
  fireEvent.change(screen.getByLabelText("생활관 필터"), { target: { value: "전체" } });
  mockParams = mockSetSearchParams.mock.calls.at(-1)[0];
  expect(mockParams.has("dormitory")).toBe(false);
  rerender(<CommunityPage currentUser={currentUser} />);
  await waitFor(() => expect(api.getCommunityPosts).toHaveBeenLastCalledWith(expect.objectContaining({ dormitory: "전체", page: 0 })));
  await screen.findByText("미리보기");
});

test.each([['/community/my-posts', 'getMyCommunityPosts'], ['/community/my-comments', 'getMyCommunityComments']])("loads activity from %s", async (path, method) => {
  mockPath = path;
  api[method].mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 });
  render(<CommunityPage currentUser={currentUser} />);
  await waitFor(() => expect(api[method]).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 6 })));
  expect(api.getCommunityPosts).not.toHaveBeenCalled();
});

test("my posts link to editing and delete only after confirmation, then refresh the list", async () => {
  mockPath = "/community/my-posts";
  api.getMyCommunityPosts.mockResolvedValueOnce({ content: [{ ...post, author: currentUser }], totalElements: 1, totalPages: 1 })
    .mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 });
  api.deleteCommunityPost.mockResolvedValue(undefined);
  render(<CommunityPage currentUser={currentUser} />);
  fireEvent.click(await screen.findByRole("button", { name: "수정" }));
  expect(mockNavigate).toHaveBeenCalledWith("/community/posts/12/edit", { state: { returnToMyPosts: "/community/my-posts" } });
  fireEvent.click(screen.getByRole("button", { name: "삭제" }));
  expect(api.deleteCommunityPost).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  expect(api.deleteCommunityPost).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "삭제" }));
  fireEvent.click(screen.getByRole("button", { name: "삭제하기" }));
  await screen.findByText("아직 작성한 게시글이 없어요");
  expect(api.deleteCommunityPost).toHaveBeenCalledWith(12);
  expect(api.getMyCommunityPosts).toHaveBeenCalledTimes(2);
});

test("my comments preserve a failed edit draft and save inline on retry", async () => {
  mockPath = "/community/my-comments";
  const comment = { id: 8, content: "내 댓글 내용", postId: 12, postTitle: "원문", createdAt: post.createdAt };
  api.getMyCommunityComments.mockResolvedValueOnce({ content: [comment], totalElements: 1, totalPages: 1 })
    .mockResolvedValue({ content: [{ ...comment, content: "수정한 댓글" }], totalElements: 1, totalPages: 1 });
  api.updateCommunityComment.mockRejectedValueOnce(new Error("댓글 수정 실패")).mockResolvedValueOnce(undefined);
  render(<CommunityPage currentUser={currentUser} />);
  fireEvent.click(await screen.findByRole("button", { name: "수정" }));
  const input = screen.getByRole("textbox", { name: "댓글 수정 내용" });
  fireEvent.change(input, { target: { value: "   " } });
  expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  fireEvent.change(input, { target: { value: "수정한 댓글" } });
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await screen.findByText("댓글 수정 실패");
  expect(input).toHaveValue("수정한 댓글");
  fireEvent.click(screen.getByRole("button", { name: "저장" }));
  await waitFor(() => expect(api.getMyCommunityComments).toHaveBeenCalledTimes(2));
  await screen.findByText("수정한 댓글");
  expect(api.updateCommunityComment).toHaveBeenLastCalledWith(8, "수정한 댓글");
  expect(api.getMyCommunityComments).toHaveBeenCalledTimes(2);
});

test("my reply deletion prevents duplicate requests and adjusts the last page", async () => {
  mockPath = "/community/my-comments";
  mockParams = new URLSearchParams({ page: "2" });
  const comment = { id: 8, isReply: true, content: "내 답글", postId: 12, postTitle: "원문", createdAt: post.createdAt };
  api.getMyCommunityComments.mockResolvedValueOnce({ content: [comment], totalElements: 7, totalPages: 2 })
    .mockResolvedValue({ content: [], totalElements: 6, totalPages: 1 });
  let finishDelete;
  api.deleteCommunityComment.mockImplementationOnce(() => new Promise((resolve) => { finishDelete = resolve; }));
  render(<CommunityPage currentUser={currentUser} />);
  fireEvent.click(await screen.findByRole("button", { name: "삭제" }));
  fireEvent.click(screen.getByRole("button", { name: "삭제하기" }));
  expect(screen.getByRole("button", { name: "삭제 중…" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "삭제 중…" }));
  expect(api.deleteCommunityComment).toHaveBeenCalledTimes(1);
  finishDelete();
  await waitFor(() => expect(mockSetSearchParams).toHaveBeenCalled());
  expect(mockSetSearchParams.mock.calls.at(-1)[0](mockParams).get("page")).toBe("1");
  expect(api.deleteCommunityComment).toHaveBeenCalledWith(8);
});

test("refetches likes, preserves failed comment drafts, and uses numeric chat IDs", async () => {
  mockPath = "/community/posts/12";
  api.likeCommunityPost.mockResolvedValue(undefined);
  api.createCommunityComment.mockRejectedValue(new Error("댓글 등록 실패"));
  createChatRoom.mockResolvedValue({ id: 42 });
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("서버 본문");
  expect(screen.queryByRole("button", { name: "수정" })).not.toBeInTheDocument();
  expect(screen.getByLabelText("댓글 좋아요 0개")).toBeEnabled();
  api.getCommunityPost.mockResolvedValue({ ...post, liked: true, likes: 5 });
  fireEvent.click(screen.getByLabelText("좋아요 4개"));
  await screen.findByLabelText("좋아요 5개");
  expect(api.likeCommunityPost).toHaveBeenCalledWith(12);
  expect(api.getCommunityPost).toHaveBeenCalledTimes(2);
  const input = screen.getByLabelText("같은이름님의 댓글");
  fireEvent.change(input, { target: { value: "잃어버리면 안 되는 내용" } });
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith("댓글 등록 실패"));
  expect(input).toHaveValue("잃어버리면 안 되는 내용");
  expect(screen.getByRole("button", { name: "댓글 등록" })).toBeEnabled();
  fireEvent.click(screen.getAllByLabelText("같은이름님에게 채팅 보내기")[1]);
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/chat?room=42"));
  expect(createChatRoom).toHaveBeenCalledWith({ targetUserId: 2, category: "COMMUNITY", sourceFrom: "COMMENT", sourcePostId: 12, sourceCommentId: 8 });
});

test("alerts report errors, keeps the dialog open for retry and only closes after success", async () => {
  mockPath = "/community/posts/12";
  api.createCommunityReport.mockRejectedValueOnce(new Error("신고 실패")).mockResolvedValueOnce(undefined);
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("서버 본문");
  fireEvent.click(screen.getByRole("button", { name: "게시글 신고" }));
  fireEvent.click(screen.getByLabelText("광고·홍보"));
  fireEvent.click(screen.getByRole("button", { name: "신고하기", hidden: true }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith("신고 실패"));
  expect(screen.getByRole("dialog")).toBeInTheDocument();
  expect(screen.getByLabelText("광고·홍보")).toBeChecked();
  expect(screen.getByRole("button", { name: "신고하기" })).toBeEnabled();
  expect(api.createCommunityReport).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByRole("button", { name: "신고하기", hidden: true }));
  await waitFor(() => expect(window.alert).toHaveBeenLastCalledWith("신고를 접수했어요. 더 편안한 커뮤니티를 위해 확인할게요."));
  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  expect(api.createCommunityReport).toHaveBeenCalledTimes(2);
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
  expect(api.createCommunityPost).toHaveBeenCalledWith({ category: "자유게시판", title: "새 제목", content: "새 내용", anonymous: false }, []);
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
  await waitFor(() => expect(api.updateCommunityPost).toHaveBeenCalledWith(12, { category: "자유게시판", title: "서버 게시글", content: "서버 본문", anonymous: false, keepImageIds: [10] }, [file]));
  expect(mockNavigate).toHaveBeenCalledWith("/community/posts/12", { replace: true });
});


test.each(["/community", "/community/my-posts", "/community/posts/12"])("renders anonymous and public authors on %s", async (path) => {
  mockPath = path;
  const publicAuthor = { id: 2, nickname: "공개닉네임", major: "소프트웨어융합전공" };
  const entry = { ...post, author: publicAuthor, anonymous: true, comments: [], commentCount: 0 };
  const list = { content: [entry], totalElements: 1, totalPages: 1 };
  api.getCommunityPosts.mockResolvedValue(list);
  api.getMyCommunityPosts.mockResolvedValue(list);
  api.getCommunityPost.mockResolvedValue(entry);
  const { container, unmount } = render(<CommunityPage currentUser={currentUser} />);
  await screen.findAllByText("익명");
  expect(screen.queryByText("공개닉네임")).not.toBeInTheDocument();
  expect(container.innerHTML).not.toContain("소프트웨어융합");
  expect(container.querySelector('img')).toHaveAttribute("src", "/img/한경이 이미지/감정/힙합 한경이.png");
  unmount();
  entry.anonymous = false;
  const publicView = render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("공개닉네임");
  expect(publicView.container.querySelector('img')).toHaveAttribute("src", getProfileImage(publicAuthor.major));
});

test("sends anonymous=true when creating a checked post", async () => {
  mockPath = "/community/posts/new";
  api.createCommunityPost.mockResolvedValue(88);
  render(<CommunityPage currentUser={currentUser} />);
  const checkbox = screen.getByRole("checkbox", { name: /익명/ });
  expect(checkbox).not.toBeChecked();
  fireEvent.click(checkbox);
  fireEvent.change(screen.getByLabelText(/제목/), { target: { value: "익명 제목" } });
  fireEvent.change(screen.getByLabelText(/내용/), { target: { value: "익명 내용" } });
  fireEvent.click(screen.getByRole("button", { name: "게시글 등록" }));
  await waitFor(() => expect(api.createCommunityPost).toHaveBeenCalledWith(expect.objectContaining({ anonymous: true }), []));
});

test.each([true, false])("retains or changes anonymous state during editing: %s", async (anonymous) => {
  mockPath = "/community/posts/12/edit";
  api.getCommunityPost.mockResolvedValue({ ...post, anonymous: true, author: { id: 1, nickname: "익명", major: null } });
  render(<CommunityPage currentUser={currentUser} />);
  const checkbox = await screen.findByRole("checkbox", { name: /익명/ });
  expect(checkbox).toBeChecked();
  if (!anonymous) fireEvent.click(checkbox);
  fireEvent.click(screen.getByRole("button", { name: "수정 완료" }));
  await waitFor(() => expect(api.updateCommunityPost).toHaveBeenCalledWith(12, expect.objectContaining({ anonymous }), []));
});

test("keeps owner actions for anonymous posts", async () => {
  mockPath = "/community/posts/12";
  api.getCommunityPost.mockResolvedValue({ ...post, anonymous: true, author: { id: 1, nickname: "익명", major: null }, comments: [] });
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("익명 · 나");
  expect(screen.getByRole("button", { name: "수정" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "삭제" }));
  fireEvent.click(screen.getByRole("button", { name: "삭제하기", hidden: true }));
  await waitFor(() => expect(api.deleteCommunityPost).toHaveBeenCalledWith(12));
});

test.each([true, false])("submits comments and replies with independent anonymous choices: %s", async (anonymous) => {
  mockPath = "/community/posts/12";
  api.createCommunityComment.mockResolvedValue(undefined);
  api.createCommunityReply.mockResolvedValue(undefined);
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("서버 댓글");
  const checkbox = screen.getByRole("checkbox", { name: "댓글 익명" });
  expect(checkbox).not.toBeChecked();
  if (anonymous) fireEvent.click(checkbox);
  fireEvent.change(screen.getByLabelText("같은이름님의 댓글"), { target: { value: "새 댓글" } });
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await waitFor(() => expect(api.createCommunityComment).toHaveBeenCalledWith(12, "새 댓글", anonymous));
  await waitFor(() => expect(screen.getByLabelText("같은이름님의 댓글")).toHaveValue(""));
  fireEvent.click(screen.getByRole("button", { name: "답글" }));
  const replyCheckbox = screen.getByRole("checkbox", { name: "답글 익명" });
  expect(replyCheckbox).not.toBeChecked();
  if (anonymous) fireEvent.click(replyCheckbox);
  fireEvent.change(screen.getByLabelText("같은이름님에게 답글 남기기"), { target: { value: "새 답글" } });
  fireEvent.click(screen.getByRole("button", { name: "답글 등록" }));
  await waitFor(() => expect(api.createCommunityReply).toHaveBeenCalledWith(8, "새 답글", anonymous));
  await waitFor(() => expect(screen.queryByRole("checkbox", { name: "답글 익명" })).not.toBeInTheDocument());
});

test("keeps an anonymous comment draft when submission fails", async () => {
  mockPath = "/community/posts/12";
  api.createCommunityComment.mockRejectedValue(new Error("댓글 실패"));
  render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("서버 댓글");
  fireEvent.click(screen.getByRole("checkbox", { name: "댓글 익명" }));
  fireEvent.change(screen.getByLabelText("같은이름님의 댓글"), { target: { value: "익명 초안" } });
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await waitFor(() => expect(window.alert).toHaveBeenCalledWith("댓글 실패"));
  expect(screen.getByRole("checkbox", { name: "댓글 익명" })).toBeChecked();
  expect(screen.getByLabelText("같은이름님의 댓글")).toHaveValue("익명 초안");
  api.createCommunityComment.mockResolvedValueOnce(9);
  fireEvent.click(screen.getByRole("button", { name: "댓글 등록" }));
  await waitFor(() => expect(screen.getByLabelText("같은이름님의 댓글")).toHaveValue(""));
  expect(api.createCommunityComment).toHaveBeenLastCalledWith(12, "익명 초안", true);
  expect(api.createCommunityComment).toHaveBeenCalledTimes(2);
  expect(screen.getByRole("checkbox", { name: "댓글 익명" })).not.toBeChecked();
});

test.each([true, false])("renders comment and reply profile images, anonymous=%s", async (anonymous) => {
  mockPath = "/community/posts/12";
  const commentAuthor = { id: 1, nickname: "공개댓글닉네임", major: "소프트웨어융합전공" };
  const serverAuthor = anonymous ? { id: null, nickname: "익명4", major: null } : commentAuthor;
  const commentFields = { author: serverAuthor, anonymous, anonymousNumber: anonymous ? 4 : null, mine: true };
  api.getCommunityPost.mockResolvedValue({ ...post, comments: [
    { ...post.comments[0], ...commentFields },
    { ...post.comments[0], ...commentFields, id: 9, parentCommentId: 8, content: "답글 본문" },
  ] });
  const { container } = render(<CommunityPage currentUser={currentUser} />);
  await screen.findByText("답글 본문");
  for (const id of [8, 9]) {
    const entry = container.querySelector(`#comment-${id}`);
    expect(entry.querySelector("img")).toHaveAttribute("src", anonymous
      ? "/img/한경이 이미지/감정/힙합 한경이.png" : getProfileImage(commentAuthor.major));
    expect(within(entry).getByText(serverAuthor.nickname)).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "수정" })).toBeInTheDocument();
  }
  if (anonymous) expect(container.innerHTML).not.toContain("공개댓글닉네임");
});

test("offers first post creation for an empty dormitory and category filter", async () => {
  mockParams = new URLSearchParams({ dormitory: "나래관", category: "자유게시판" });
  api.getCommunityPosts.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0 });
  render(<CommunityPage currentUser={currentUser} />);
  fireEvent.click(await screen.findByRole("button", { name: "첫 글 작성하기" }));
  expect(screen.queryByRole("button", { name: "검색 조건 초기화" })).not.toBeInTheDocument();
  expect(mockNavigate).toHaveBeenCalledWith("/community/posts/new");
});
