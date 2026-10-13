import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ArrowLeft,
  Eye,
  Flag,
  Heart,
  LogIn,
  MessageCircle,
  Pencil,
  Reply,
  Send,
  Trash2,
  X,
} from "lucide-react";

import { getPostAuthorDisplay } from "./communityData";
import * as api from "../../api/communityApi";
import { createChatRoom } from "../../api/chatApi";
import * as S from "./CommunityPostPageCss";

const REPORT_REASONS = [
  "욕설·비방",
  "광고·홍보",
  "음란성 콘텐츠",
  "개인정보 노출",
  "도배·스팸",
  "기타",
];
const COMMENT_LIMIT = 1000;

function formatDate(value) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function PostDialog({ title, description, onDismiss, children }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => {
      if (dialog.open) dialog.close();
    };
  }, []);

  const handleBackdropClick = (event) => {
    if (event.target !== event.currentTarget) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    if (
      event.clientX < bounds.left || event.clientX > bounds.right ||
      event.clientY < bounds.top || event.clientY > bounds.bottom
    ) onDismiss();
  };

  return (
    <S.Dialog
      ref={dialogRef}
      aria-labelledby="community-dialog-title"
      aria-describedby="community-dialog-description"
      onClick={handleBackdropClick}
      onCancel={(event) => {
        event.preventDefault();
        onDismiss();
      }}
    >
      <S.DialogHeader>
        <h2 id="community-dialog-title">{title}</h2>
        <S.CloseButton type="button" aria-label="닫기" onClick={onDismiss} autoFocus>
          <X size={19} aria-hidden="true" />
        </S.CloseButton>
      </S.DialogHeader>
      <S.DialogDescription id="community-dialog-description">
        {description}
      </S.DialogDescription>
      {children}
    </S.Dialog>
  );
}

function CommentComposer({ id, label, onSubmit, onCancel, compact = false, busy }) {
  const [value, setValue] = useState("");
  const [anonymous, setAnonymous] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!value.trim()) return;
    if (await onSubmit(value.trim(), anonymous)) {
      setValue("");
      setAnonymous(false);
    }
  };

  return (
    <S.CommentForm $compact={compact} onSubmit={handleSubmit}>
      <S.Textarea
        id={id}
        rows={1}
        aria-label={label}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder={compact ? "따뜻한 답글을 남겨 주세요." : "전하고 싶은 이야기를 남겨 주세요."}
        maxLength={COMMENT_LIMIT}
        $compact={compact}
        autoFocus={compact}
        required
        aria-describedby={`${id}-count`}
      />
      <S.FormFooter>
        <small id={`${id}-count`}>{value.length} / {COMMENT_LIMIT.toLocaleString()}자</small>
        <div>
          <S.AnonymousChoice>
            <input type="checkbox" aria-label={compact ? "답글 익명" : "댓글 익명"} checked={anonymous}
              onChange={(event) => setAnonymous(event.target.checked)} disabled={busy} />
            익명
          </S.AnonymousChoice>
          {onCancel && <S.TextButton type="button" onClick={onCancel}>취소</S.TextButton>}
          <S.PrimaryButton type="submit" disabled={busy || !value.trim()}>
            <Send size={15} aria-hidden="true" />
            {compact ? "답글 등록" : "댓글 등록"}
          </S.PrimaryButton>
        </div>
      </S.FormFooter>
    </S.CommentForm>
  );
}

function CommentEntry({ entry, currentUser, postAuthorId, onSave, onDelete, onReport, onReply, onToggleLike, replying, onChat, replyAuthor, busy }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(entry.content);
  const mine = Boolean(currentUser && (entry.mine ?? (currentUser.id === entry.author.id)));
  const authorDisplay = getPostAuthorDisplay(entry);
  const editActions = mine && !editing && (
    <S.ActionGroup>
      <S.TextButton type="button" onClick={() => {
        setDraft(entry.content);
        setEditing(true);
      }}>수정</S.TextButton>
      <S.TextButton type="button" $danger onClick={onDelete}>삭제</S.TextButton>
    </S.ActionGroup>
  );

  return (
    <S.CommentItem id={`comment-${entry.id}`}>
      <S.CommentHeader>
        <S.CommentAuthorImage src={authorDisplay.image} alt="" />
        <div>
          <strong>{authorDisplay.nickname}</strong>
          {!entry.anonymous && entry.author.id != null && entry.author.id === postAuthorId && <S.AuthorBadge>작성자</S.AuthorBadge>}
          {mine && <S.AuthorBadge>나</S.AuthorBadge>}
          <time dateTime={entry.createdAt}>{formatDate(entry.createdAt)}</time>
        </div>
        {editActions}
        {!mine && <S.ChatButton type="button" onClick={onChat} aria-label={`${authorDisplay.nickname}님에게 채팅 보내기`}>
          <MessageCircle size={14} aria-hidden="true" />채팅
        </S.ChatButton>}
      </S.CommentHeader>
      {editing && mine ? (
        <S.CommentForm $compact onSubmit={async (event) => {
          event.preventDefault();
          if (!draft.trim()) return;
          if (await onSave(draft.trim())) setEditing(false);
        }}>
          <label htmlFor={`edit-${entry.id}`}>{onReply ? "댓글 수정" : "대댓글 수정"}</label>
          <S.Textarea
            id={`edit-${entry.id}`}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            maxLength={COMMENT_LIMIT}
            $compact
            required
            autoFocus
          />
          <S.FormFooter>
            <small>{draft.length} / {COMMENT_LIMIT.toLocaleString()}자</small>
            <div>
              <S.TextButton type="button" onClick={() => setEditing(false)}>수정 취소</S.TextButton>
              <S.PrimaryButton type="submit" disabled={busy || !draft.trim()}>수정 완료</S.PrimaryButton>
            </div>
          </S.FormFooter>
        </S.CommentForm>
      ) : (
        <>
          <S.CommentText>{entry.content}</S.CommentText>
          <S.ActionGroup>
            <S.LikeButton type="button" $active={entry.liked} aria-pressed={entry.liked}
              aria-label={`${replyAuthor == null ? "댓글" : "대댓글"} 좋아요 ${(entry.likes ?? 0).toLocaleString()}개`} onClick={onToggleLike} disabled={busy}>
              <Heart size={14} aria-hidden="true" /><span>{(entry.likes ?? 0).toLocaleString()}</span>
            </S.LikeButton>
            {onReply && (
              <S.TextButton type="button" aria-expanded={replying} onClick={onReply}>
                <Reply size={14} aria-hidden="true" />답글
              </S.TextButton>
            )}
            {onReport && !mine && (
              <S.TextButton type="button" onClick={onReport} disabled={busy}>
                <Flag size={13} aria-hidden="true" />신고
              </S.TextButton>
            )}
          </S.ActionGroup>
        </>
      )}
    </S.CommentItem>
  );
}

function CommunityPostPage({ post, currentUser, onRefresh, onDeletePost, onEdit, onRequireLogin }) {
  const navigate = useNavigate();
  const busyRef = useRef(false);
  const [busy, setBusy] = useState(false);
  const [replyTarget, setReplyTarget] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [reportTarget, setReportTarget] = useState(null);
  const [reportReason, setReportReason] = useState("");
  const [otherReason, setOtherReason] = useState("");
  const comments = post.comments || [];
  const mine = Boolean(currentUser && currentUser.id === post.author.id);
  const authorDisplay = getPostAuthorDisplay(post);
  const liked = post.liked;
  const reported = post.reported;
  const commentCount = comments.reduce((total, comment) => total + 1 + (comment.replies?.length || 0), 0);

  const requireUser = () => {
    if (currentUser) return true;
    onRequireLogin();
    return false;
  };

  const runAction = async (action) => {
    if (!requireUser() || busyRef.current) return false;
    busyRef.current = true;
    setBusy(true);
    try {
      await action();
      return true;
    } catch (failure) {
      window.alert(failure.message);
      return false;
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const startChat = (authorId, sourceCommentId) => runAction(async () => {
    if (authorId === currentUser.id) return;
    const room = await createChatRoom({
      targetUserId: authorId, category: "COMMUNITY",
      sourceFrom: sourceCommentId == null ? "POST" : "COMMENT",
      sourcePostId: post.id,
      ...(sourceCommentId == null ? {} : { sourceCommentId }),
    });
    navigate('/chat?room=' + encodeURIComponent(room.id));
  });

  const toggleLike = () => runAction(async () => {
    await (post.liked ? api.unlikeCommunityPost(post.id) : api.likeCommunityPost(post.id));
    await onRefresh();
  });

  const toggleCommentLike = (commentId, liked) => runAction(async () => {
    await (liked ? api.unlikeCommunityComment(commentId) : api.likeCommunityComment(commentId));
    await onRefresh();
  });

  const addComment = (content, anonymous, commentId) => runAction(async () => {
    if (commentId == null) await api.createCommunityComment(post.id, content, anonymous);
    else await api.createCommunityReply(commentId, content, anonymous);
    await onRefresh();
    if (commentId != null) setReplyTarget(null);
  });

  const saveComment = (commentId, replyId, content) => runAction(async () => {
    await api.updateCommunityComment(replyId ?? commentId, content);
    await onRefresh();
  });

  const confirmDelete = () => runAction(async () => {
    if (!deleteTarget) return;
    if (deleteTarget.kind === "post") {
      if (mine) await onDeletePost();
    } else {
      await api.deleteCommunityComment(deleteTarget.replyId ?? deleteTarget.commentId);
      await onRefresh();
      if (replyTarget === deleteTarget.commentId && !deleteTarget.replyId) setReplyTarget(null);
    }
    setDeleteTarget(null);
  });

  const openReport = (comment) => {
    if (!requireUser() || (!comment && post.reported)) return;
    setReportReason("");
    setOtherReason("");
    setReportTarget(comment ? { kind: "comment", id: comment.id } : { kind: "post", id: post.id });
  };

  const submitReport = (event) => {
    event.preventDefault();
    if (!reportTarget || !reportReason || (reportReason === "기타" && !otherReason.trim())) return;
    runAction(async () => {
      await api.createCommunityReport({
        targetType: reportTarget.kind === "post" ? "POST" : "COMMENT",
        targetId: reportTarget.id, reason: reportReason,
        description: reportReason === "기타" ? otherReason.trim() : null,
      });
      setReportTarget(null);
      window.alert("신고를 접수했어요. 더 편안한 커뮤니티를 위해 확인할게요.");
      if (reportTarget.kind === "post") await onRefresh();
    });
  };

  const deleteLabel = deleteTarget?.kind === "post" ? "게시글" : deleteTarget?.replyId ? "답글" : "댓글";

  return (
    <S.Detail aria-labelledby="community-post-title" aria-busy={busy}>
      {busy && <p role="status">처리하고 있어요.</p>}
      <S.BackLink as={Link} to="/community"><ArrowLeft size={17} aria-hidden="true" />커뮤니티 목록</S.BackLink>
      <S.Article>
        <S.ArticleTop>
          <S.Category $category={post.category}>{post.category}</S.Category>
          {mine && (
            <S.ActionGroup>
              <S.TextButton type="button" onClick={onEdit}><Pencil size={15} aria-hidden="true" />수정</S.TextButton>
              <S.TextButton type="button" $danger onClick={() => setDeleteTarget({ kind: "post" })}>
                <Trash2 size={15} aria-hidden="true" />삭제
              </S.TextButton>
            </S.ActionGroup>
          )}
        </S.ArticleTop>
        <S.Title id="community-post-title">{post.title}</S.Title>
        <S.AuthorRow>
          <S.PostAuthorImage src={authorDisplay.image} alt="" />
          <S.AuthorInfo>
            <strong>{authorDisplay.nickname}{mine ? " · 나" : ""}</strong>
            <S.Metadata>
              <time dateTime={post.createdAt}>{formatDate(post.createdAt)}</time>
              <span><Eye size={13} aria-hidden="true" />조회 {post.views.toLocaleString()}</span>
            </S.Metadata>
          </S.AuthorInfo>
          {!mine && <S.ChatButton type="button" onClick={() => startChat(post.author.id)}
            aria-label={`${authorDisplay.nickname}님에게 채팅 보내기`}>
            <MessageCircle size={15} aria-hidden="true" />채팅
          </S.ChatButton>}
        </S.AuthorRow>
        <S.Content>{post.content}</S.Content>
        {post.images?.length > 0 && (
          <S.Attachments aria-label="첨부 이미지">
            {post.images.map((attachment, index) => (
              <figure key={attachment.id}>
                <img src={attachment.src} alt={`${post.title} 첨부 이미지 ${index + 1}`} loading="lazy" />
              </figure>
            ))}
          </S.Attachments>
        )}
        <S.Reactions>
          <S.LikeButton type="button" $active={liked} aria-pressed={liked} aria-label={`좋아요 ${post.likes.toLocaleString()}개`} onClick={toggleLike} disabled={busy}>
            <Heart size={16} aria-hidden="true" /><span>{post.likes.toLocaleString()}</span>
          </S.LikeButton>
          <S.TextButton type="button" onClick={() => openReport()} disabled={busy || reported}>
            <Flag size={15} aria-hidden="true" />{reported ? "신고 완료" : "게시글 신고"}
          </S.TextButton>
        </S.Reactions>
      </S.Article>

      <S.CommentsCard aria-labelledby="community-comments-title">
        <S.CommentsHeading id="community-comments-title"><MessageCircle size={21} aria-hidden="true" />댓글 <span>{commentCount}</span></S.CommentsHeading>
        {comments.length ? (
          <S.CommentList>
            {comments.map((comment) => (
              <S.CommentThread key={comment.id}>
                <CommentEntry
                  busy={busy}
                  entry={comment}
                  onChat={() => startChat(comment.author.id, comment.id)}
                  currentUser={currentUser}
                  postAuthorId={post.author.id}
                  onSave={(content) => saveComment(comment.id, null, content)}
                  onDelete={() => setDeleteTarget({ kind: "comment", commentId: comment.id })}
                  onReport={() => openReport(comment)}
                  onToggleLike={() => toggleCommentLike(comment.id, comment.liked)}
                  replying={replyTarget === comment.id}
                  onReply={() => {
                    if (requireUser()) setReplyTarget(replyTarget === comment.id ? null : comment.id);
                  }}
                />
                {comment.replies?.length > 0 && (
                  <S.ReplyList aria-label={`${getPostAuthorDisplay(comment).nickname}님 댓글의 답글`}>
                    {comment.replies.map((reply) => (
                      <li key={reply.id}>
                        <CommentEntry
                          busy={busy}
                          entry={reply}
                          onReport={() => openReport(reply)}
                          onToggleLike={() => toggleCommentLike(reply.id, reply.liked)}
                          replyAuthor={getPostAuthorDisplay(comment).nickname}
                          onChat={() => startChat(reply.author.id, reply.id)}
                          currentUser={currentUser}
                          postAuthorId={post.author.id}
                          onSave={(content) => saveComment(comment.id, reply.id, content)}
                          onDelete={() => setDeleteTarget({ kind: "reply", commentId: comment.id, replyId: reply.id })}
                        />
                      </li>
                    ))}
                  </S.ReplyList>
                )}
                {replyTarget === comment.id && currentUser && (
                  <CommentComposer
                    busy={busy}
                    id={`reply-to-${comment.id}`}
                    label={`${getPostAuthorDisplay(comment).nickname}님에게 답글 남기기`}
                    compact
                    onSubmit={(content, anonymous) => addComment(content, anonymous, comment.id)}
                    onCancel={() => setReplyTarget(null)}
                  />
                )}
              </S.CommentThread>
            ))}
          </S.CommentList>
        ) : (
          <S.EmptyComments>
            <MessageCircle size={28} aria-hidden="true" />
            <strong>아직 댓글이 없어요</strong>
            <p>첫 번째 댓글로 이야기를 시작해 보세요.</p>
          </S.EmptyComments>
        )}

        {currentUser ? (
          <CommentComposer
            busy={busy} id="community-new-comment" label={`${currentUser.name}님의 댓글`} onSubmit={(content, anonymous) => addComment(content, anonymous)} />
        ) : (
          <S.CommentForm onSubmit={(event) => event.preventDefault()}>
            <label htmlFor="community-guest-comment">댓글 남기기</label>
            <S.Textarea id="community-guest-comment" rows={1} placeholder="로그인 후 이웃과 이야기를 나눠 보세요." disabled />
            <S.FormFooter><small>로그인하면 댓글을 작성할 수 있어요.</small><div>
              <S.PrimaryButton type="button" onClick={onRequireLogin}><LogIn size={16} aria-hidden="true" />로그인하고 댓글 쓰기</S.PrimaryButton>
            </div></S.FormFooter>
          </S.CommentForm>
        )}
      </S.CommentsCard>
      <S.BottomNav><S.BackLink as={Link} to="/community"><ArrowLeft size={17} aria-hidden="true" />목록으로 돌아가기</S.BackLink></S.BottomNav>

      {deleteTarget && (
        <PostDialog
          title={`${deleteLabel}을 삭제할까요?`}
          description={deleteTarget.kind === "post"
            ? "게시글과 모든 댓글이 함께 삭제돼요.\n삭제한 내용은 되돌릴 수 없어요."
            : deleteTarget.replyId
              ? "삭제한 답글은 되돌릴 수 없어요."
              : "이 댓글에 달린 답글도 함께 삭제돼요.\n삭제한 내용은 되돌릴 수 없어요."}
          onDismiss={() => setDeleteTarget(null)}
        >
            <S.DialogActions>
            <S.SecondaryButton type="button" onClick={() => setDeleteTarget(null)}>취소</S.SecondaryButton>
            <S.PrimaryButton type="button" $danger onClick={confirmDelete} disabled={busy}>삭제하기</S.PrimaryButton>
          </S.DialogActions>
        </PostDialog>
      )}
      {reportTarget && (
        <PostDialog
          title={`${reportTarget.kind === "post" ? "게시글" : "댓글"} 신고`}
          description="신고 사유를 선택해 주세요. 같은 내용은 한 번만 신고할 수 있어요."
          onDismiss={() => setReportTarget(null)}
        >
          <form onSubmit={submitReport}>
            <S.ReasonList>
              <legend>신고 사유</legend>
              {REPORT_REASONS.map((reason) => (
                <label key={reason}>
                  <input type="radio" name="community-report-reason" value={reason} checked={reportReason === reason} onChange={(event) => setReportReason(event.target.value)} required />
                  {reason}
                </label>
              ))}
            </S.ReasonList>
            {reportReason === "기타" && (
              <S.OtherReason>
                <label htmlFor="community-other-reason">자세한 사유</label>
                <S.Textarea id="community-other-reason" value={otherReason} onChange={(event) => setOtherReason(event.target.value)} placeholder="신고 사유를 입력해 주세요." maxLength={500} required />
              </S.OtherReason>
            )}
          <S.DialogActions>
              <S.SecondaryButton type="button" onClick={() => setReportTarget(null)}>취소</S.SecondaryButton>
              <S.PrimaryButton type="submit" disabled={busy || !reportReason || (reportReason === "기타" && !otherReason.trim())}>신고하기</S.PrimaryButton>
            </S.DialogActions>
          </form>
        </PostDialog>
      )}
    </S.Detail>
  );
}

export default CommunityPostPage;
