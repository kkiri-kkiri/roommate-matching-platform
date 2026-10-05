import { getProfileImage } from "../../components/common/ProfileImage";
import { getPostAuthorDisplay, normalizePost } from "./communityData";

test("resolves uploaded image URLs against the API server", () => {
  const previous = process.env.REACT_APP_API_BASE_URL;
  process.env.REACT_APP_API_BASE_URL = "https://api.example.com/api";
  try {
    const post = normalizePost({ author: { id: 7 }, images: [{ id: 1, url: "/api/post-images/photo.png" }] });
    expect(post.images[0].src).toBe("https://api.example.com/api/post-images/photo.png");
  } finally {
    if (previous === undefined) delete process.env.REACT_APP_API_BASE_URL;
    else process.env.REACT_APP_API_BASE_URL = previous;
  }
});

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
  expect(normalized.comments[0]).toHaveProperty("liked", false);
  expect(normalized.comments[0]).toHaveProperty("likes", 0);
  expect(normalized.comments[0]).not.toHaveProperty("reported");
  expect(post.comments[0]).not.toHaveProperty("replies");
});


test("defaults legacy posts to public and preserves author IDs for anonymous posts", () => {
  const author = { id: 7, nickname: "실제이름", major: "소프트웨어융합전공" };
  expect(normalizePost({ author }).anonymous).toBe(false);
  const anonymous = normalizePost({ author, anonymous: true });
  expect(anonymous.author.id).toBe(7);
  expect(getPostAuthorDisplay(anonymous)).toEqual({ nickname: "익명", image: "/img/한경이 이미지/감정/힙합 한경이.png" });
  expect(getPostAuthorDisplay({ author, anonymous: false })).toEqual({ nickname: author.nickname, image: getProfileImage(author.major) });
});
