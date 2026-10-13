import { fireEvent, render, screen, within, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import CommunityPostPage from "./CommunityPostPage";
import { normalizePost } from "./communityData";
import { createChatRoom } from "../../api/chatApi";

const mockNavigate = jest.fn();
jest.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
  Link: ({ to, children, ...props }) => <a href={to} {...props}>{children}</a>,
}));
jest.mock("../../api/chatApi");

const anonymous = { id: null, nickname: "익명3", major: null };
const comment = { id: 10, parentCommentId: null, author: anonymous, anonymous: true,
  anonymousNumber: 3, mine: true, content: "내 익명 댓글", createdAt: "2026-10-13T10:00:00" };
const post = { id: 1, author: { id: 2, nickname: "게시글작성자" }, title: "제목", content: "내용",
  createdAt: comment.createdAt, views: 1, likes: 0, comments: [comment,
    { ...comment, id: 11, parentCommentId: 10, content: "내 익명 답글" },
    { ...comment, id: 12, anonymousNumber: 8, author: { ...anonymous, nickname: "익명8" }, mine: false, content: "다른 익명 댓글" },
    { ...comment, id: 13, anonymous: false, anonymousNumber: null, mine: false, author: { id: 4, nickname: "일반회원" }, content: "일반 댓글" },
  ] };

test("renders server numbers with gaps, preserves anonymous ownership and reply presentation after reorder", () => {
  const props = { currentUser: { id: 7, name: "나" }, onRequireLogin: jest.fn() };
  const { container, rerender } = render(<CommunityPostPage {...props} post={normalizePost(post)} />);
  for (const id of [10, 11]) {
    const entry = container.querySelector(`#comment-${id}`);
    expect(within(entry).getByText("익명3")).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "수정" })).toBeInTheDocument();
    expect(within(entry).getByRole("button", { name: "삭제" })).toBeInTheDocument();
    expect(within(entry).queryByText("작성자")).not.toBeInTheDocument();
    expect(entry.querySelector("img")).toHaveAttribute("src", "/img/한경이 이미지/감정/힙합 한경이.png");
  }
  expect(screen.getByText("일반회원")).toBeInTheDocument();
  expect(screen.queryByText(/님에게 답글$/)).not.toBeInTheDocument();
  rerender(<CommunityPostPage {...props} post={normalizePost({ ...post, comments: [...post.comments].reverse() })} />);
  expect(within(container.querySelector("#comment-11")).getByText("익명3")).toBeInTheDocument();
  expect(within(container.querySelector("#comment-12")).getByText("익명8")).toBeInTheDocument();
});

test("opens an anonymous author's chat using the comment source and no real user ID", async () => {
  createChatRoom.mockResolvedValue({ id: 42 });
  render(<CommunityPostPage post={normalizePost(post)} currentUser={{ id: 7, name: "나" }} />);
  fireEvent.click(screen.getByRole("button", { name: "익명8님에게 채팅 보내기" }));
  await waitFor(() => expect(createChatRoom).toHaveBeenCalledWith({
    targetUserId: null, category: "COMMUNITY", sourceFrom: "COMMENT", sourcePostId: 1, sourceCommentId: 12,
  }));
  expect(mockNavigate).toHaveBeenCalledWith("/chat?room=42");
});
