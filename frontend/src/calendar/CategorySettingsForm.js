import { useRef, useState } from "react";
import { Plus, Trash2 } from "lucide-react";

import * as S from "./CalendarPageCss";
import { FIXED_CATEGORY_IDS } from "./CalendarDataContext";

export default function CategorySettingsForm({ initialCategories, events, onSave, onDismiss, savingRef }) {
  // Keep draft changes here so dragging a color does not render the calendar.
  const [draft, setDraft] = useState(() => initialCategories.map((item) => ({ ...item })));
  const [error, setError] = useState("");
  const [deletedIds, setDeletedIds] = useState([]);
  const [saving, setSaving] = useState(false);
  const sequence = useRef(0);

  const updateCategory = (id, field, value) => {
    if (field === "label" && FIXED_CATEGORY_IDS.includes(id)) return;
    setDraft((current) => current.map((item) => item.id === id ? { ...item, [field]: value } : item));
    setError("");
  };

  const saveSettings = async (event) => {
    event.preventDefault();
    if (savingRef.current) return;
    const next = draft.map((item) => ({ ...item, label: item.label.trim() }));
    if (next.some(({ label }) => !label || label.length > 30) || new Set(next.map(({ label }) => label)).size !== next.length) {
      setError("카테고리 이름은 중복 없이 1~30자로 입력해 주세요.");
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError("");
    try {
      await onSave(next, deletedIds);
      savingRef.current = false;
      onDismiss();
    } catch (error) {
      // onSave retains created server IDs in next when only part of a save succeeds.
      setDraft(next);
      setError(error.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <S.Form onSubmit={saveSettings}>
      <fieldset disabled={saving} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
        <S.SettingsList>
          {draft.map(({ id, serverId, label, color, enabled }) => {
            const fixed = FIXED_CATEGORY_IDS.includes(id);
            const inUse = events.some((event) => event.category === id);
            return (
              <S.SettingsRow key={id}>
                <S.Field>
                  <label htmlFor={`category-name-${id}`}>카테고리 이름 {fixed && <span>· 고정</span>}</label>
                  <S.Input id={`category-name-${id}`} value={label} readOnly={fixed} required maxLength={30}
                    onChange={(event) => updateCategory(id, "label", event.target.value)} />
                  {!fixed && inUse && <S.FormHint>사용 중인 카테고리는 삭제할 수 없어요.</S.FormHint>}
                  <label htmlFor={`category-enabled-${id}`}>
                    <input id={`category-enabled-${id}`} type="checkbox" checked={enabled !== false}
                      aria-label={`${label || "새 카테고리"} 표시`}
                      onChange={(event) => updateCategory(id, "enabled", event.target.checked)} /> 표시
                  </label>
                </S.Field>
                <S.Field>
                  <label htmlFor={`category-color-${id}`}>색상</label>
                  <S.ColorPicker id={`category-color-${id}`} type="color" value={color}
                    aria-label={`${label || "새 카테고리"} 색상 설정`}
                    onChange={(event) => updateCategory(id, "color", event.target.value)} />
                </S.Field>
                {!fixed && <S.IconButton type="button" aria-label={`${label || "새 카테고리"} 삭제`} disabled={inUse}
                  onClick={() => {
                    if (FIXED_CATEGORY_IDS.includes(id) || events.some((event) => event.category === id)) return;
                    if (serverId) setDeletedIds((current) => [...current, serverId]);
                    setDraft((current) => current.filter((item) => item.id !== id));
                    setError("");
                  }}><Trash2 size={18} aria-hidden="true" /></S.IconButton>}
              </S.SettingsRow>
            );
          })}
        </S.SettingsList>
        <S.GhostButton type="button" onClick={() => {
          setDraft((current) => [...current, {
            id: `custom-${Date.now()}-${sequence.current++}`, label: "", color: "#006a65",
          }]);
          setError("");
        }}><Plus size={18} aria-hidden="true" /> 카테고리 추가</S.GhostButton>
        {error && <S.FieldError role="alert">{error}</S.FieldError>}
        <S.DialogActions>
          <S.GhostButton type="button" onClick={onDismiss}>취소</S.GhostButton>
          <S.PrimaryButton type="submit">설정 저장</S.PrimaryButton>
        </S.DialogActions>
      </fieldset>
    </S.Form>
  );
}
