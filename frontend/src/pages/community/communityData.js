// Keep server author IDs intact; nickname is only a display field.
export function normalizePost(post) {
  const comments = (post.comments || []).map((comment) => ({ ...comment, replies: [] }));
  const byId = new Map(comments.map((comment) => [comment.id, comment]));
  comments.forEach((comment) => {
    if (comment.parentCommentId != null) byId.get(comment.parentCommentId)?.replies.push(comment);
  });
  return {
    ...post,
    images: (post.images || []).map((image) => ({ ...image, src: image.url })),
    comments: comments.filter((comment) => comment.parentCommentId == null),
  };
}
