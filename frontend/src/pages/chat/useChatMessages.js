import { useCallback, useEffect, useRef, useState } from "react";
import { getChatMessages, getChatReadStatuses } from "../../api/chatApi";
import { mapMessage, mergeMessages } from "./chatMessages";

// Immutable (sentAt,id) boundaries, independent from WebSocket arrival order.
export function useChatMessages({ activeId, counterpartId, searchQuery, setRooms, setStatus,
  displayedMessages, visibleMessageIds }) {
  const context = useRef(null);
  const [state, setState] = useState({ loading: false, error: "", moreLoading: false, moreError: "", hasMore: false });
  const [searchResults, setSearchResults] = useState([]);
  const current = (ctx) => context.current === ctx && !ctx.cancelled;
  const merge = useCallback((ctx, items) => {
    if (!items.length) return;
    if (ctx.q) setSearchResults((previous) => mergeMessages(previous, items));
    else setRooms((rooms) => rooms.map((room) => room.id === ctx.id
      ? { ...room, messages: mergeMessages(room.messages, items) } : room));
  }, [setRooms]);

  useEffect(() => {
    const ctx = { id: activeId, q: searchQuery, counterpartId, cancelled: false, ready: false, morePending: false, syncPending: false };
    context.current = ctx;
    setSearchResults([]);
    setState({ loading: Boolean(activeId), error: "", moreLoading: false, moreError: "", hasMore: false });
    if (!activeId) return () => { ctx.cancelled = true; };
    ctx.loadInitial = async () => {
      if (ctx.initialPending || !current(ctx)) return;
      ctx.initialPending = true;
      setState((value) => ({ ...value, loading: true, error: "" }));
      try {
        const page = await getChatMessages(ctx.id, { page: 0, size: 50, q: ctx.q || undefined });
        if (!current(ctx)) return;
        const items = mergeMessages(page.content.map((item) => mapMessage(item, ctx.counterpartId)));
        merge(ctx, items);
        ctx.oldest = items[0]?.id;
        ctx.newest = items[items.length - 1]?.id;
        ctx.ready = true;
        ctx.hasMore = page.last === undefined ? items.length === 50 : !page.last;
        setState((value) => ({ ...value, hasMore: ctx.hasMore }));
      } catch (error) { if (current(ctx)) setState((value) => ({ ...value, error: error.message })); }
      finally {
        ctx.initialPending = false;
        if (current(ctx)) setState((value) => ({ ...value, loading: false }));
      }
    };
    const timer = setTimeout(() => { void ctx.loadInitial(); }, searchQuery ? 250 : 0);
    return () => { ctx.cancelled = true; clearTimeout(timer); };
  }, [activeId, counterpartId, searchQuery, merge]);

  const loadOlderMessages = useCallback(async () => {
    const ctx = context.current;
    if (!ctx?.ready || ctx.morePending || !ctx.hasMore || ctx.oldest == null) return;
    ctx.morePending = true;
    setState((value) => ({ ...value, moreLoading: true, moreError: "" }));
    try {
      const page = await getChatMessages(ctx.id, { page: 0, size: 50, q: ctx.q || undefined, beforeId: ctx.oldest });
      if (!current(ctx)) return;
      const items = mergeMessages(page.content.map((item) => mapMessage(item, ctx.counterpartId)));
      merge(ctx, items);
      ctx.oldest = items[0]?.id ?? ctx.oldest;
      ctx.hasMore = page.last === undefined ? items.length === 50 : !page.last;
      setState((value) => ({ ...value, hasMore: ctx.hasMore }));
    } catch (error) { if (current(ctx)) setState((value) => ({ ...value, moreError: error.message })); }
    finally {
      ctx.morePending = false;
      if (current(ctx)) setState((value) => ({ ...value, moreLoading: false }));
    }
  }, [merge]);

  const syncMessages = useCallback(async () => {
    const ctx = context.current;
    if (!ctx || ctx.cancelled) return false;
    if (!ctx.ready) { await ctx.loadInitial?.(); return false; }
    if (ctx.syncPending) return false;
    ctx.syncPending = true;
    try {
      const page = await getChatMessages(ctx.id, { page: 0, size: 50, q: ctx.q || undefined,
        ...(ctx.newest == null ? {} : { afterId: ctx.newest }) });
      if (!current(ctx)) return false;
      const items = mergeMessages(page.content.map((item) => mapMessage(item, ctx.counterpartId)));
      merge(ctx, items);
      if (ctx.newest == null) {
        ctx.oldest = items[0]?.id;
        ctx.hasMore = page.last === undefined ? items.length === 50 : !page.last;
        setState((value) => ({ ...value, hasMore: ctx.hasMore }));
      }
      ctx.newest = items[items.length - 1]?.id ?? ctx.newest;
      return page.last === false;
    } catch (error) {
      if (current(ctx)) {
        if (error.status === 403) setRooms((rooms) => rooms.filter((room) => room.id !== ctx.id));
        setStatus(error.message);
      }
      return false;
    }
    finally { ctx.syncPending = false; }
  }, [merge, setStatus, setRooms]);

  const sameQuery = context.current?.id === activeId && context.current?.q === searchQuery;
  const activeSearchResults = sameQuery ? searchResults : [];
  const messagesRef = useRef([]);
  messagesRef.current = (searchQuery ? activeSearchResults : displayedMessages || []).filter((item) => item.sender === "me" && !item.read
    && (visibleMessageIds == null || visibleMessageIds.includes(String(item.id)))).slice(0, 100);
  const statusRequest = useRef(null);
  const syncReadStatuses = useCallback(async () => {
    const ctx = context.current;
    const ids = messagesRef.current.map((item) => item.id);
    if (!ctx?.ready || !ids.length || statusRequest.current === ctx) return;
    statusRequest.current = ctx;
    try {
      const statuses = await getChatReadStatuses(ctx.id, ids);
      if (!current(ctx)) return;
      const readIds = new Set(statuses.filter((item) => item.read).map((item) => String(item.id)));
      if (!readIds.size) return;
      const update = (items) => items.map((item) => readIds.has(String(item.id)) ? { ...item, read: true } : item);
      setRooms((rooms) => rooms.map((room) => room.id === ctx.id ? { ...room, messages: update(room.messages) } : room));
      setSearchResults(update);
    } catch (error) { if (current(ctx)) setStatus(error.message); }
    finally { if (statusRequest.current === ctx) statusRequest.current = null; }
  }, [setRooms, setStatus]);
  const ownVisibleKey = messagesRef.current.map((item) => item.id).join(",");
  useEffect(() => { void syncReadStatuses(); }, [ownVisibleKey, syncReadStatuses]);

  return { messageLoading: !sameQuery && Boolean(activeId) ? true : state.loading,
    messageError: sameQuery ? state.error : "", searchResults: activeSearchResults, setSearchResults,
    moreMessagesLoading: sameQuery && state.moreLoading, moreMessagesError: sameQuery ? state.moreError : "",
    hasOlderMessages: sameQuery && state.hasMore, loadOlderMessages, syncMessages, syncReadStatuses };
}
