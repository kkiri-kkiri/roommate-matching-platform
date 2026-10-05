import { useRef, useState } from "react";
import { Pencil, Trash2 } from "lucide-react";
import * as api from "../../api/communityApi";
import * as S from "./CommunityPageCss";

export default function CommunityActivityActions({ item, isComment = false, onEdit, onChanged }) {
  const [mode, setMode] = useState(null);
  const [draft, setDraft] = useState(item.content || "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const label = isComment ? "댓글" : "게시글";

  const run = async (action) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError("");
    try {
      await action();
      setMode(null);
      onChanged();
    } catch (failure) {
      setError(failure.message || `${label}을 처리하지 못했어요. 다시 시도해 주세요.`);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  return <S.ActivityControls $expanded={mode !== null}>
    {mode === "edit" ? <S.ActivityForm onSubmit={(event) => {
      event.preventDefault();
      if (draft.trim()) run(() => api.updateCommunityComment(item.id, draft.trim()));
    }}>
      <S.ActivityTextarea aria-label="댓글 수정 내용" value={draft} maxLength={1000} required autoFocus
        disabled={busy} onChange={(event) => setDraft(event.target.value)} />
      <S.ActivityButtons>
        <small>{draft.length} / 1,000자</small>
        <S.ActivityButton type="button" disabled={busy} onClick={() => { setMode(null); setError(""); }}>취소</S.ActivityButton>
        <S.ActivityButton type="submit" disabled={busy || !draft.trim()}>{busy ? "저장 중…" : "저장"}</S.ActivityButton>
      </S.ActivityButtons>
    </S.ActivityForm> : mode === "delete" ? <>
      <S.DeleteQuestion>{label}을 삭제할까요? 삭제 후에는 되돌릴 수 없어요.</S.DeleteQuestion>
      <S.ActivityButtons>
        <S.ActivityButton type="button" disabled={busy} onClick={() => { setMode(null); setError(""); }}>취소</S.ActivityButton>
        <S.ActivityButton type="button" $danger disabled={busy} onClick={() => run(() => isComment
          ? api.deleteCommunityComment(item.id) : api.deleteCommunityPost(item.id))}>{busy ? "삭제 중…" : "삭제하기"}</S.ActivityButton>
      </S.ActivityButtons>
    </> : <S.ActivityButtons>
      <S.ActivityButton type="button" onClick={() => {
        setError("");
        if (isComment) { setDraft(item.content); setMode("edit"); }
        else onEdit();
      }}><Pencil size={14} aria-hidden="true" />수정</S.ActivityButton>
      <S.ActivityButton type="button" $danger onClick={() => { setError(""); setMode("delete"); }}>
        <Trash2 size={14} aria-hidden="true" />삭제
      </S.ActivityButton>
    </S.ActivityButtons>}
    {error && <S.ActivityError role="alert">{error}</S.ActivityError>}
  </S.ActivityControls>;
}
