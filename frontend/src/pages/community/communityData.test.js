import { normalizePost } from "./communityData";

test("groups unordered flat replies and preserves numeric authors and server images", () => {
  const author = { id: 7, nickname: "동일한 닉네임" };
  const post = { id: 1, author, images: [{ id: 5, url: "/image.png" }], comments: [
    { id: 3, parentCommentId: 2, author, content: "답글" },
    { id: 2, parentCommentId: null, author, content: "댓글" },
  ] };
  const normalized = normalizePost(post);
  expect(normalized.comments).toHaveLength(1);
  expect(normalized.comments[0].replies[0].id).toBe(3);
  expect(normalized.author).toEqual(author);
  expect(normalized.images[0]).toEqual({ id: 5, url: "/image.png", src: "/image.png" });
  expect(normalized.comments[0]).not.toHaveProperty("liked");
  expect(normalized.comments[0]).not.toHaveProperty("reported");
  expect(post.comments[0]).not.toHaveProperty("replies");
});
