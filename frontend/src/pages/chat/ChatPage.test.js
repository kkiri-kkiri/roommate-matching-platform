import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import "@testing-library/jest-dom";
import ChatPage from "./ChatPage";
import DetailedProfile from "../../components/common/ProfileCard/DetailedProfile";
import * as chatApi from "../../api/chatApi";
import { getCommunityPost } from "../../api/communityApi";
import { getRoommateDetail } from "../../api/roommateApi";
import { createChatSocket } from "../../api/chatSocket";

let mockLocation;
const mockNavigate = jest.fn();
jest.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
  useLocation: () => mockLocation,
  useParams: () => ({ profileId: "19" }),
  useSearchParams: () => {
    const [params, setParams] = require("react").useState(() => new URLSearchParams(mockLocation.search));
    return [params, (next) => setParams((current) => new URLSearchParams(typeof next === "function" ? next(current) : next))];
  },
  Link: ({ to, state, replace, children, ...props }) => (
    <a href={to} onClick={(event) => { event.preventDefault(); mockNavigate(to, { state }); }} {...props}>{children}</a>
  ),
}));
jest.mock("../../api/chatApi");
jest.mock("../../api/chatSocket");
jest.mock("../../api/communityApi");
jest.mock("../../api/roommateApi");
jest.mock("../mypage/MyPage", () => ({ getSavedSurveyProfiles: () => [] }));

const counterpart = { id: 19, nickname: "같은상대", tags: [], admissionYear: "24", dormitory: "나래" };
const rooms = [
  { id: 41, category: "ROOMMATE", counterpart, sourcePost: null, unreadCount: 2, pinned: false, muted: false },
  { id: 42, category: "COMMUNITY", counterpart, sourcePost: 31, unreadCount: 3, pinned: false, muted: false },
];

beforeEach(() => {
  jest.resetAllMocks();
  localStorage.setItem("accessToken", "test-token");
  mockLocation = { pathname: "/chat", search: "?room=41", state: null };
  window.requestAnimationFrame = (callback) => { callback(); return 0; };
  HTMLDialogElement.prototype.showModal = jest.fn(function () { this.open = true; });
  HTMLDialogElement.prototype.close = jest.fn(function () { this.open = false; });
  chatApi.getChatRooms.mockResolvedValue(rooms);
  chatApi.getChatMessages.mockResolvedValue({ content: [] });
  chatApi.markChatRoomRead.mockResolvedValue();
  getCommunityPost.mockResolvedValue({ id: 31, title: "원래 게시글" });
  createChatSocket.mockReturnValue({ connect: jest.fn(), disconnect: jest.fn(), subscribe: () => jest.fn() });
});

afterEach(() => localStorage.clear());

test("anonymous authors have no profile link or profile modal even via a direct URL", async () => {
  mockLocation.search = "?room=42&view=profile";
  chatApi.getChatRooms.mockResolvedValue([{ ...rooms[1], counterpart: {
    id: null, nickname: "익명", profileImageUrl: null, dormitory: null,
    admissionYear: null, major: null, matchScore: null, introduction: null, tags: [],
  } }]);
  render(<ChatPage />);
  expect(await screen.findByLabelText("익명 참여자")).toBeInTheDocument();
  expect(screen.queryByRole("dialog", { name: "상대방 프로필" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "익명님 프로필 보기" })).not.toBeInTheDocument();
  expect(screen.queryByText("같은상대")).not.toBeInTheDocument();
  expect(await screen.findByRole("link", { name: "원래 게시글" })).toHaveAttribute("href", "/community/posts/31");
});

test.each([true, false])("chat avatars preserve anonymous=%s identity in the list, header and messages", async (anonymous) => {
  mockLocation.search = "?room=42";
  chatApi.getChatRooms.mockResolvedValue([{ ...rooms[1], counterpart: {
    ...counterpart, id: anonymous ? null : counterpart.id,
    profileImageUrl: "/real-user-photo.jpg", major: "컴퓨터공학",
  } }]);
  chatApi.getChatMessages.mockResolvedValue({ content: [
    { id: 1, text: "프로필 확인 메시지", senderType: "OTHER", sentAt: "2026-10-10T12:00:00" },
  ] });
  const { container } = render(<ChatPage />);
  await screen.findByText("프로필 확인 메시지");
  const nickname = anonymous ? "익명" : counterpart.nickname;
  const expectedImage = anonymous
    ? "/img/한경이 이미지/감정/힙합 한경이.png" : "/real-user-photo.jpg";
  const list = screen.getByRole("list", { name: "전체 채팅 목록" });
  expect(within(list).getByRole("img", { name: `${nickname} 프로필` })).toHaveAttribute("src", expectedImage);
  const header = screen.getByLabelText(anonymous ? "익명 참여자" : `${nickname}님 프로필 보기`);
  expect(header.querySelector("img")).toHaveAttribute("src", expectedImage);
  const panel = container.querySelector("#chat-room-panel");
  expect(panel.querySelectorAll("img")).toHaveLength(2);
  panel.querySelectorAll("img").forEach((img) => expect(img).toHaveAttribute("src", expectedImage));
  if (anonymous) {
    expect(screen.queryByText(counterpart.nickname)).not.toBeInTheDocument();
    // 이미지 로드 실패 시에도 실제 프로필 사진으로 되돌아가지 않는다.
    fireEvent.error(header.querySelector("img"));
    expect(header.querySelector("img").getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
    expect(container.querySelector('img[src="/real-user-photo.jpg"]')).toBeNull();
  }
});

test.each([
  ["ROOMMATE", "룸메찾기", 0], ["ROOMMATE", "룸메찾기", 1], ["ROOMMATE", "룸메찾기", 5],
  ["COMMUNITY", "커뮤니티", 0], ["COMMUNITY", "커뮤니티", 1], ["COMMUNITY", "커뮤니티", 5],
])("hides all profile tags in the %s chat modal with %s origin and %i tags", async (category, label, count) => {
  const tags = Object.freeze(Array.from({ length: count }, (_, index) => `생활습관${index}`));
  const profile = Object.freeze({ ...counterpart, tags, major: "컴퓨터공학", matchScore: 92,
    introduction: "서로 배려하며 지내요.", profileImageUrl: "/profile-test.jpg" });
  mockLocation.search = "?room=41&view=profile";
  chatApi.getChatRooms.mockResolvedValue([{ ...rooms[0], category, counterpart: profile,
    sourcePost: category === "COMMUNITY" ? 31 : null }]);
  render(<ChatPage />);
  const dialog = within(await screen.findByRole("dialog", { name: "상대방 프로필" }));
  expect(dialog.queryByLabelText("생활 습관")).not.toBeInTheDocument();
  expect(dialog.queryByText(/^#/)).not.toBeInTheDocument();
  expect(dialog.getByRole("img", { name: "같은상대 프로필" })).toHaveAttribute("src", "/profile-test.jpg");
  expect(dialog.getByText("같은상대")).toBeInTheDocument();
  expect(dialog.getByText("컴퓨터공학 · 24학번")).toBeInTheDocument();
  expect(dialog.getByText("나래 생활관")).toBeInTheDocument();
  expect(dialog.getByText(label)).toBeInTheDocument();
  if (category === "ROOMMATE") {
    expect(dialog.getByText("매칭점수")).toBeInTheDocument();
    expect(dialog.getByText("92점")).toBeInTheDocument();
  } else {
    expect(dialog.queryByText("매칭점수")).not.toBeInTheDocument();
    expect(dialog.queryByText("92점")).not.toBeInTheDocument();
    expect(dialog.getAllByRole("term")).toHaveLength(2);
  }
  expect(dialog.getByText("서로 배려하며 지내요.")).toBeInTheDocument();
  expect(profile.tags).toEqual(Array.from({ length: count }, (_, index) => `생활습관${index}`));
  fireEvent.click(dialog.getByRole("button", { name: "대화로 돌아가기" }));
  expect(screen.queryByRole("dialog", { name: "상대방 프로필" })).not.toBeInTheDocument();
});

test.each([0, null])("preserves roommate score handling for %s", async (matchScore) => {
  mockLocation.search = "?room=41&view=profile";
  chatApi.getChatRooms.mockResolvedValue([{ ...rooms[0], counterpart: { ...counterpart, matchScore } }]);
  render(<ChatPage />);
  const dialog = within(await screen.findByRole("dialog", { name: "상대방 프로필" }));
  if (matchScore === null) {
    expect(dialog.queryByText("매칭점수")).not.toBeInTheDocument();
  } else {
    expect(dialog.getByText("0점")).toBeInTheDocument();
  }
});

test("keeps the same counterpart's rooms separate and counts each category", async () => {
  render(<ChatPage />);
  const list = await screen.findByRole("list", { name: "전체 채팅 목록" });
  expect(within(list).getAllByRole("listitem")).toHaveLength(2);
  expect(screen.getByRole("button", { name: "전체 2" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "룸메찾기 1" }));
  expect(within(screen.getByRole("list", { name: "룸메찾기 채팅 목록" })).getAllByRole("listitem")).toHaveLength(1);
  fireEvent.click(screen.getByRole("button", { name: "커뮤니티 1" }));
  const community = screen.getByRole("list", { name: "커뮤니티 채팅 목록" });
  expect(within(community).getAllByRole("listitem")).toHaveLength(1);
  fireEvent.click(within(community).getByRole("button", { name: /같은상대.*커뮤니티/ }));
  expect(await screen.findByRole("link", { name: "원래 게시글" })).toHaveAttribute("href", "/community/posts/31");
  expect(screen.queryByText("룸메찾기를 통해 시작된 채팅")).not.toBeInTheDocument();
});

test("restores the roommate room from its URL and links the server counterpart ID", async () => {
  const first = render(<ChatPage />);
  expect(await screen.findByText("룸메찾기를 통해 시작된 채팅")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("link", { name: "상대방 룸메이트 프로필 보기" }));
  expect(mockNavigate).toHaveBeenCalledWith("/roommates/19", { state: { chatRoomId: "41" } });
  expect(getCommunityPost).not.toHaveBeenCalled();
  expect(chatApi.createChatRoom).not.toHaveBeenCalled();
  first.unmount();
  render(<ChatPage />);
  expect(await screen.findByText("룸메찾기를 통해 시작된 채팅")).toBeInTheDocument();
  expect(chatApi.getChatRooms).toHaveBeenCalledTimes(2);
});

test("restores the community source after a direct visit", async () => {
  mockLocation.search = "?room=42";
  render(<ChatPage />);
  expect(await screen.findByRole("link", { name: "원래 게시글" })).toHaveAttribute("href", "/community/posts/31");
  expect(screen.queryByRole("link", { name: "상대방 룸메이트 프로필 보기" })).not.toBeInTheDocument();
});

test.each([
  [{ status: 400, errorCode: "INVALID_REQUEST" }, "삭제된 게시글입니다."],
  [{}, "게시글 정보를 불러올 수 없습니다."],
  [{ status: 500, errorCode: "INTERNAL_SERVER_ERROR" }, "게시글 정보를 불러올 수 없습니다."],
  [{ status: 400 }, "게시글 정보를 불러올 수 없습니다."],
  [{ status: 400, errorCode: "VALIDATION_ERROR" }, "게시글 정보를 불러올 수 없습니다."],
  [{ status: 401, errorCode: "UNAUTHORIZED" }, "로그인 상태를 확인한 후 게시글을 조회해 주세요."],
  [{ status: 403, errorCode: "FORBIDDEN" }, "게시글을 조회할 권한이 없습니다."],
])("classifies source lookup failure %j without a clickable fallback", async (failure, notice) => {
  mockLocation.search = "?room=42";
  getCommunityPost.mockRejectedValue(Object.assign(new Error("조회 실패"), failure));
  chatApi.getChatMessages.mockResolvedValue({ content: [
    { id: 1, text: "기존 대화", senderType: "OTHER", sentAt: "2026-10-10T12:00:00" },
  ] });
  render(<ChatPage />);
  expect(await screen.findByText(notice)).toBeInTheDocument();
  expect(screen.getByText(notice).closest("a")).toBeNull();
  expect(screen.queryByText("게시글 #31")).not.toBeInTheDocument();
  expect(await screen.findByText("기존 대화")).toBeInTheDocument();
  expect(chatApi.markChatRoomRead).toHaveBeenCalledWith("42");
  expect(chatApi.leaveChatRoom).not.toHaveBeenCalled();
  expect(screen.getByRole("button", { name: "전체 2" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "커뮤니티 1" })).toBeInTheDocument();
});

test("ignores a late deleted-post response after switching to another community room", async () => {
  mockLocation.search = "?room=42";
  chatApi.getChatRooms.mockResolvedValue([...rooms, {
    ...rooms[1], id: 43, sourcePost: 32, counterpart: { ...counterpart, id: 20, nickname: "다른상대" },
  }]);
  let rejectOldPost;
  getCommunityPost.mockImplementation((id) => id === 31
    ? new Promise((resolve, reject) => { rejectOldPost = reject; })
    : Promise.resolve({ id: 32, title: "새 게시글" }));
  render(<ChatPage />);
  expect(await screen.findByText("게시글 정보를 불러오는 중입니다.")).toBeInTheDocument();
  expect(screen.queryByText("게시글 #31")).not.toBeInTheDocument();
  const list = screen.getByRole("list", { name: "전체 채팅 목록" });
  fireEvent.click(within(list).getByRole("button", { name: /다른상대.*커뮤니티/ }));
  expect(await screen.findByRole("link", { name: "새 게시글" })).toHaveAttribute("href", "/community/posts/32");
  await act(async () => rejectOldPost({ status: 400, errorCode: "INVALID_REQUEST" }));
  expect(screen.queryByText("삭제된 게시글입니다.")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "새 게시글" })).toBeInTheDocument();
  await act(async () => {
    fireEvent.click(within(list).getByRole("button", { name: /같은상대.*룸메찾기/ }));
  });
  expect(screen.getByText("룸메찾기를 통해 시작된 채팅")).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "새 게시글" })).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "상대방 룸메이트 프로필 보기" })).toHaveAttribute("href", "/roommates/19");
});

test("clears a deleted source notice when switching to a normal community post", async () => {
  mockLocation.search = "?room=42";
  chatApi.getChatRooms.mockResolvedValue([...rooms, {
    ...rooms[1], id: 43, sourcePost: 32, counterpart: { ...counterpart, nickname: "다른상대" },
  }]);
  getCommunityPost.mockRejectedValueOnce({ status: 400, errorCode: "INVALID_REQUEST" })
    .mockResolvedValueOnce({ id: 32, title: "새 게시글" });
  render(<ChatPage />);
  await screen.findByText("삭제된 게시글입니다.");
  fireEvent.click(within(screen.getByRole("list", { name: "전체 채팅 목록" }))
    .getByRole("button", { name: /다른상대.*커뮤니티/ }));
  expect(screen.queryByText("삭제된 게시글입니다.")).not.toBeInTheDocument();
  expect(await screen.findByRole("link", { name: "새 게시글" })).toBeInTheDocument();
});

test("does not classify an invalid source ID as a deleted post", async () => {
  mockLocation.search = "?room=42";
  chatApi.getChatRooms.mockResolvedValue([{ ...rooms[1], sourcePost: -1 }]);
  render(<ChatPage />);
  expect(await screen.findByText("게시글 정보를 불러올 수 없습니다.")).toBeInTheDocument();
  expect(getCommunityPost).not.toHaveBeenCalled();
  expect(screen.queryByText("삭제된 게시글입니다.")).not.toBeInTheDocument();
});

test("treats an incomplete success response as a lookup failure", async () => {
  mockLocation.search = "?room=42";
  getCommunityPost.mockResolvedValue({ id: 31 });
  render(<ChatPage />);
  expect(await screen.findByText("게시글 정보를 불러올 수 없습니다.")).toBeInTheDocument();
  expect(screen.queryByText("삭제된 게시글입니다.")).not.toBeInTheDocument();
});

test("reusing a roommate room does not duplicate it or remove the counterpart's community room", async () => {
  mockLocation.state = { roommateProfile: { id: 19 } };
  chatApi.createChatRoom.mockResolvedValue(rooms[0]);
  render(<ChatPage />);
  await screen.findByRole("button", { name: "전체 2" });
  expect(chatApi.createChatRoom).toHaveBeenCalledWith({ targetUserId: 19, category: "ROOMMATE" });
  expect(screen.getByRole("button", { name: "룸메찾기 1" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "커뮤니티 1" })).toBeInTheDocument();
});

test.each(["프로필이 삭제되었거나 공개되지 않습니다.", "프로필 조회에 실패했습니다."])("handles unavailable profiles and returns to the original chat: %s", async (message) => {
  mockLocation = { pathname: "/roommates/19", search: "", state: { chatRoomId: "41" } };
  getRoommateDetail.mockRejectedValue(new Error(message));
  render(<DetailedProfile />);
  expect(await screen.findByRole("alert")).toHaveTextContent(message);
  expect(getRoommateDetail).toHaveBeenCalledWith("19", expect.any(AbortSignal));
  expect(screen.getByRole("link", { name: "채팅으로 돌아가기" })).toHaveAttribute("href", "/chat?room=41");
});

test("uses the existing detail API and closes back to the original chat", async () => {
  mockLocation = { pathname: "/roommates/19", search: "", state: { chatRoomId: "41" } };
  getRoommateDetail.mockResolvedValue({ ...counterpart, answers: {} });
  render(<DetailedProfile />);
  await screen.findByText("같은상대");
  fireEvent.click(screen.getByRole("button", { name: "상세 프로필 닫기" }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith("/chat?room=41", { replace: true }));
});
