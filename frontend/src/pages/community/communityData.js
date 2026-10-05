import { getProfileImage } from "../../components/common/ProfileImage";

export function getPostAuthorDisplay(post) {
  return post.anonymous === true
    ? { nickname: "익명", image: `${process.env.PUBLIC_URL || ""}/img/한경이 이미지/감정/힙합 한경이.png` }
    : { nickname: post.author.nickname || "사용자", image: getProfileImage(post.author.major) };
}

// Keep server author IDs intact; nickname is only a display field.
export function normalizePost(post) {
  const comments = (post.comments || []).map((comment) => ({
    ...comment,
    likes: comment.likes ?? 0,
    liked: comment.liked === true,
    replies: [],
  }));
  const byId = new Map(comments.map((comment) => [comment.id, comment]));
  comments.forEach((comment) => {
    if (comment.parentCommentId != null) byId.get(comment.parentCommentId)?.replies.push(comment);
  });
  return {
    ...post,
    anonymous: post.anonymous === true,
    images: (post.images || []).map((image) => ({
      ...image,
      src: image.url?.startsWith("/api/post-images/")
        ? new URL(image.url, new URL(process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api", window.location.origin)).href
        : image.url,
    })),
    comments: comments.filter((comment) => comment.parentCommentId == null),
  };
}
