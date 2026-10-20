import { useEffect, useRef } from "react";

// One bounded synchronization at a time. Poll committed REST state, not speculative events.
export function useChatSync({ isAuthenticated, connectionVersion, broadcastVersion, refreshRooms,
  syncMessages, syncReadStatuses }) {
  const actions = useRef({ refreshRooms, syncMessages, syncReadStatuses });
  actions.current = { refreshRooms, syncMessages, syncReadStatuses };
  const wake = useRef(() => {});
  useEffect(() => {
    if (!isAuthenticated) return;
    let cancelled = false, running = false, queued = false, eventTimer, retryTimer;
    const tick = async () => {
      if (cancelled || document.visibilityState === "hidden") return;
      if (running) { queued = true; return; }
      running = true;
      try {
        const results = await Promise.allSettled([actions.current.refreshRooms({ background: true }),
          actions.current.syncMessages(), actions.current.syncReadStatuses()]);
        if (!cancelled && results[1].status === "fulfilled" && results[1].value) {
          clearTimeout(retryTimer);
          retryTimer = setTimeout(tick, 1000); // Catch up >50 missed messages in bounded batches.
        }
      } finally {
        running = false;
        if (!cancelled && queued) { queued = false; schedule(); }
      }
    };
    const schedule = () => {
      if (eventTimer || cancelled) return;
      eventTimer = setTimeout(() => { eventTimer = null; void tick(); }, 100);
    };
    wake.current = schedule;
    const timer = setInterval(() => { void tick(); }, 15000);
    window.addEventListener("focus", schedule);
    document.addEventListener("visibilitychange", schedule);
    return () => {
      cancelled = true; wake.current = () => {};
      clearInterval(timer); clearTimeout(eventTimer); clearTimeout(retryTimer);
      window.removeEventListener("focus", schedule);
      document.removeEventListener("visibilitychange", schedule);
    };
  }, [isAuthenticated]);
  useEffect(() => { if (connectionVersion || broadcastVersion) wake.current(); }, [connectionVersion, broadcastVersion]);
}
