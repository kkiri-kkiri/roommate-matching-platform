import { useCallback, useEffect, useRef, useState } from "react";
import { getChatRoom, getChatRooms } from "../../api/chatApi";

const EMPTY_COUNTS = { all: 0, roommate: 0, community: 0, pinned: 0 };

// Cache selected rooms separately from the ordered IDs belonging to this server-side filter/page.
export function useChatRooms({ isAuthenticated, rooms, setRooms, selectedRoomId, category, q,
  locationKey, revisionRef, mutationRef, mapRoom, setStatus }) {
  const [page, setPage] = useState({ ids: [], page: 0, last: true, totalElements: 0, counts: EMPTY_COUNTS });
  const [loadingRooms, setLoadingRooms] = useState(true);
  const [roomError, setRoomError] = useState("");
  const [loadingMoreRooms, setLoadingMoreRooms] = useState(false);
  const [moreRoomsError, setMoreRoomsError] = useState("");
  const generation = useRef(0);
  const pageRef = useRef(page);
  const loadingMoreRef = useRef(false);
  const filter = useRef({ category, q });
  const roomsRef = useRef(rooms);
  const previousLocation = useRef(locationKey);
  roomsRef.current = rooms;
  filter.current = { category, q };
  const invalidate = useCallback(() => { ++generation.current; }, []);

  const merge = useCallback((data, revision) => {
    const pendingMutations = new Set(mutationRef.current);
    setRooms((current) => {
      const cache = new Map(current.map((room) => [room.id, room]));
      for (const item of data) {
        const room = mapRoom(item);
        const previous = cache.get(room.id);
        if (previous && revision !== revisionRef.current) continue;
        cache.set(room.id, { ...previous, ...room, messages: previous?.messages || [],
          ...(previous && pendingMutations.has(room.id) ? { pinned: previous.pinned, muted: previous.muted } : {}) });
      }
      return [...cache.values()];
    });
  }, [setRooms, mapRoom, revisionRef, mutationRef]);

  const fetchPage = useCallback(async (number, append, version, background = false) => {
    const revision = revisionRef.current;
    const currentFilter = filter.current;
    const data = await getChatRooms({ category: currentFilter.category.toUpperCase(),
      q: currentFilter.q.trim() || undefined, page: number, size: 50 });
    if (generation.current !== version) return;
    merge(data.content, revision);
    setPage((current) => {
      if (background && current.page > 0 && current.totalElements === data.totalElements
          && data.content.every((room, index) => current.ids[index] === String(room.id))) {
        const next = { ...current, counts: data.counts };
        pageRef.current = next;
        return next;
      }
      const next = { ...data, ids: [...new Set([...(append ? current.ids : []), ...data.content.map((room) => String(room.id))])] };
      pageRef.current = next;
      return next;
    });
  }, [merge, revisionRef]);

  const backgroundPending = useRef(false);
  const refreshRooms = useCallback(async ({ background = false } = {}) => {
    if (!isAuthenticated) return;
    if (background && (backgroundPending.current || loadingMoreRef.current)) return;
    if (background) backgroundPending.current = true;
    const version = ++generation.current;
    if (!background) setLoadingRooms(true);
    setRoomError(""); setMoreRoomsError("");
    loadingMoreRef.current = false; setLoadingMoreRooms(false);
    try { await fetchPage(0, false, version, background); }
    catch (error) {
      if (generation.current === version) {
        if (background) setStatus(error.message); else setRoomError(error.message);
      }
    }
    finally {
      if (background) backgroundPending.current = false;
      if (generation.current === version) setLoadingRooms(false);
    }
  }, [fetchPage, isAuthenticated, setStatus]);

  useEffect(() => {
    // Invalidate immediately so a previous filter cannot append while debounce is pending.
    invalidate();
    setLoadingRooms(true);
    const timer = setTimeout(() => { void refreshRooms(); }, q.trim() ? 250 : 0);
    return () => { clearTimeout(timer); invalidate(); };
  }, [category, q, refreshRooms, invalidate]);

  const loadMoreRooms = useCallback(async () => {
    if (loadingMoreRef.current || pageRef.current.last) return;
    const version = generation.current;
    loadingMoreRef.current = true; setLoadingMoreRooms(true); setMoreRoomsError("");
    try { await fetchPage(pageRef.current.page + 1, true, version); }
    catch (error) { if (generation.current === version) setMoreRoomsError(error.message); }
    finally {
      if (generation.current === version) { loadingMoreRef.current = false; setLoadingMoreRooms(false); }
    }
  }, [fetchPage]);

  useEffect(() => {
    const reopened = previousLocation.current !== locationKey;
    previousLocation.current = locationKey;
    if (!isAuthenticated || !selectedRoomId || !/^[1-9][0-9]*$/.test(selectedRoomId)) return;
    if (!reopened && roomsRef.current.some((room) => room.id === selectedRoomId)) return;
    let cancelled = false;
    const revision = revisionRef.current;
    getChatRoom(selectedRoomId).then((room) => {
      if (!cancelled) merge([room], revision);
    }).catch((error) => { if (!cancelled) setStatus(error.message); });
    return () => { cancelled = true; };
  }, [isAuthenticated, selectedRoomId, locationKey, merge, revisionRef, setStatus]);

  return { roomIds: page.ids, roomCounts: page.counts, totalRooms: page.totalElements,
    hasMoreRooms: !page.last, loadingRooms, roomError, loadingMoreRooms, moreRoomsError, loadMoreRooms, refreshRooms };
}
