import { useEffect, useRef, useState } from "react";
import { validMessage } from "./chatMessages";

const PREFIX = "chat.pending.v1:";
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
export const SEND_CONFIRMATION_TIMEOUT = 10000;

function newId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function restore(key) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(key) || "{}");
    return Object.fromEntries(Object.entries(saved).filter(([roomId, value]) =>
      /^\d+$/.test(roomId) && UUID.test(value?.clientMessageId) && typeof value.text === "string"
      && validMessage(value.text)).map(([roomId, value]) => [roomId, { ...value, state: "unknown" }]));
  } catch { return {}; }
}

// Publish is transport acceptance only. A committed MESSAGE or REST history item is the receipt.
// An unknown result always retries the original durable ID, never a fresh logical send.
export function useChatSend({ accountId, rooms, socketRef, connected, setStatus, onConfirmed }) {
  const key = PREFIX + accountId;
  const [pendingSends, setPendingSends] = useState(() => restore(key));
  const pendingRef = useRef(pendingSends);
  const confirmedRef = useRef(onConfirmed);
  confirmedRef.current = onConfirmed;

  const replace = (next) => {
    sessionStorage.setItem(key, JSON.stringify(next));
    pendingRef.current = next;
    setPendingSends(next);
  };

  useEffect(() => {
    const next = { ...pendingRef.current };
    const confirmed = [];
    for (const [roomId, pending] of Object.entries(next)) {
      const message = rooms.find((room) => room.id === roomId)?.messages?.find((item) =>
        item.sender === "me" && item.clientMessageId === pending.clientMessageId && item.id != null);
      if (message) { delete next[roomId]; confirmed.push([roomId, pending.text]); }
    }
    if (!confirmed.length) return;
    try { sessionStorage.setItem(key, JSON.stringify(next)); } catch { /* server still deduplicates a restored key */ }
    pendingRef.current = next;
    setPendingSends(next);
    confirmed.forEach(([roomId, text]) => confirmedRef.current?.(roomId, text));
  }, [rooms, key]);

  useEffect(() => {
    const sending = Object.values(pendingSends).filter((pending) => pending.state === "sending");
    if (!sending.length) return;
    const delay = connected ? Math.max(0, Math.min(...sending.map((item) =>
      item.startedAt + SEND_CONFIRMATION_TIMEOUT - Date.now()))) : 0;
    const timer = setTimeout(() => {
      const next = Object.fromEntries(Object.entries(pendingRef.current).map(([roomId, pending]) =>
        [roomId, pending.state === "sending" && (!connected || Date.now() - pending.startedAt >= SEND_CONFIRMATION_TIMEOUT)
          ? { ...pending, state: "unknown" } : pending]));
      try { sessionStorage.setItem(key, JSON.stringify(next)); } catch { /* memory still retains the draft */ }
      pendingRef.current = next;
      setPendingSends(next);
    }, delay);
    return () => clearTimeout(timer);
  }, [pendingSends, connected, key]);

  const send = (roomId, text) => {
    const existing = pendingRef.current[roomId];
    if (existing?.state === "sending") return false;
    try {
      if (!validMessage(text)) throw new Error("메시지는 공백이 아닌 2,000 코드 포인트 이내로 입력해 주세요.");
      if (!connected || !socketRef.current) throw new Error("채팅 서버 연결 후 다시 보내주세요.");
      const pending = { text: existing?.text ?? text, clientMessageId: existing?.clientMessageId ?? newId(),
        state: "sending", startedAt: Date.now() };
      // Persist before transport, so navigation/reload cannot turn an uncertain send into a new ID.
      replace({ ...pendingRef.current, [roomId]: pending });
      try { socketRef.current.publish(roomId, pending.text, pending.clientMessageId); }
      catch (error) {
        replace({ ...pendingRef.current, [roomId]: { ...pending, state: "unknown" } });
        throw error;
      }
      return true;
    } catch (error) { setStatus(error.message); return false; }
  };

  return { send, pendingSends };
}
