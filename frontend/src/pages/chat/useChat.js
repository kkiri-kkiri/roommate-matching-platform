import { useEffect, useRef, useState } from "react";
import { createChatRoom, getChatRooms, getChatMessages, markChatRoomRead,
  updateChatRoomSettings, leaveChatRoom } from "../../api/chatApi";
import { createChatSocket } from "../../api/chatSocket";

function mapRoom(room) {
  const profile = room.counterpart;
  return { ...room, id: String(room.id), category: room.category.toLowerCase(),
    counterpartId: profile.id, nickname: profile.nickname, photo: profile.profileImageUrl,
    dormitory: profile.dormitory, year: profile.admissionYear, major: profile.major,
    matchScore: profile.matchScore, introduction: profile.introduction,
    tags: profile.tags || [], unread: room.unreadCount, messages: [] };
}

function mapMessage(item, counterpartId) {
  return { ...item, sender: item.senderType
    ? (item.senderType === "ME" ? "me" : "other")
    : (String(item.senderId) === String(counterpartId) ? "other" : "me") };
}

function mergeMessages(...groups) {
  return [...new Map(groups.flat().map((item) => [String(item.id), item])).values()]
    .sort((a, b) => new Date(a.sentAt) - new Date(b.sentAt) || Number(a.id) - Number(b.id));
}

export function useChat({ isAuthenticated, rooms, setRooms, activeRoom, searchQuery,
  location, setStatus, onCreated }) {
  const socketRef = useRef(null);
  const creationRef = useRef(null);
  const mutationRef = useRef(new Set());
  const onCreatedRef = useRef(onCreated);
  onCreatedRef.current = onCreated;
  const [connected, setConnected] = useState(false);
  const [connectionVersion, setConnectionVersion] = useState(0);
  const [loadingRooms, setLoadingRooms] = useState(true);
  const [roomError, setRoomError] = useState("");
  const [messageLoading, setMessageLoading] = useState(false);
  const [messageError, setMessageError] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [broadcastVersion, setBroadcastVersion] = useState(0);

  useEffect(() => {
    if (!isAuthenticated) return;
    let cancelled = false;
    getChatRooms({ category: "ALL" }).then((data) => {
      if (!cancelled) setRooms((current) => {
        const existing = new Map(current.map((room) => [room.id, room]));
        const fetched = data.map(mapRoom).map((room) => existing.get(room.id) || room);
        return [...fetched, ...current.filter((room) => !fetched.some((item) => item.id === room.id))];
      });
    }).catch((error) => { if (!cancelled) setRoomError(error.message); })
      .finally(() => { if (!cancelled) setLoadingRooms(false); });
    return () => { cancelled = true; };
  }, [isAuthenticated, setRooms]);

  const incomingProfile = location.state?.roommateProfile;
  const communityChat = location.state?.communityChat;
  useEffect(() => {
    if (!isAuthenticated || (!incomingProfile && !communityChat)) return;
    let cancelled = false;
    let payload;
    const positiveId = (value) => /^\d+$/.test(String(value)) && Number.isSafeInteger(Number(value)) && Number(value) > 0;
    if (incomingProfile) {
      if (!positiveId(incomingProfile.id)) {
        setStatus("룸메이트의 실제 사용자 ID가 필요합니다.");
        return;
      }
      payload = { targetUserId: Number(incomingProfile.id), category: "ROOMMATE" };
    } else {
      const targetUserId = communityChat.recipientId;
      const sourcePostId = communityChat.sourcePost?.id;
      if (!positiveId(targetUserId) || !positiveId(sourcePostId) || communityChat.sourcePost?.from !== "게시글") {
        setStatus("커뮤니티 채팅에 실제 사용자·게시글 ID가 필요하며 댓글 채팅은 댓글 ID 전달이 아직 연결되지 않았습니다.");
        return;
      }
      payload = { targetUserId: Number(targetUserId), category: "COMMUNITY", sourcePostId: Number(sourcePostId), sourceFrom: "POST" };
    }
    // StrictMode 재실행 중에는 이미 진행 중인 생성 요청을 재사용합니다.
    const key = JSON.stringify(payload);
    if (creationRef.current?.key !== key) creationRef.current = { key, promise: createChatRoom(payload) };
    creationRef.current.promise.then((data) => {
      if (cancelled) return;
      const room = mapRoom(data);
      setRooms((current) => [room, ...current.filter((item) => item.id !== room.id)]);
      onCreatedRef.current(room.id);
      creationRef.current = null;
    }).catch((error) => { if (!cancelled) setStatus(error.message); });
    return () => { cancelled = true; };
  }, [incomingProfile, communityChat, isAuthenticated, setRooms, setStatus]);

  useEffect(() => {
    if (!isAuthenticated) return;
    const socket = createChatSocket({
      onStatus: setConnected, onError: setStatus,
      onConnect: () => setConnectionVersion((value) => value + 1),
    });
    socketRef.current = socket;
    socket.connect();
    return () => { socketRef.current = null; void socket.disconnect(); };
  }, [isAuthenticated, setStatus]);

  const activeId = activeRoom?.id;
  const counterpartId = activeRoom?.counterpartId;
  useEffect(() => {
    if (!activeId || !socketRef.current) return;
    let cancelled = false;
    const markRead = () => markChatRoomRead(activeId).then(() => {
      if (!cancelled) setRooms((current) => current.map((room) => room.id === activeId ? { ...room, unread: 0 } : room));
    }).catch((error) => { if (!cancelled) setStatus(error.message); });
    const unsubscribe = socketRef.current.subscribe(activeId, (data) => {
      if (String(data.roomId) !== activeId) return;
      const item = mapMessage(data, counterpartId);
      setRooms((current) => current.map((room) => room.id === activeId
        ? { ...room, messages: mergeMessages(room.messages, [item]),
          lastMessage: !room.lastMessage || new Date(item.sentAt) >= new Date(room.lastMessage.sentAt) ? item : room.lastMessage } : room));
      setBroadcastVersion((value) => value + 1);
      if (item.sender === "other") void markRead();
    });
    void markRead();
    return () => { cancelled = true; unsubscribe(); };
  }, [activeId, counterpartId, connectionVersion, setRooms, setStatus]);

  // 검색 중 수신한 메시지는 서버 검색을 다시 실행해 결과에 반영합니다.
  const searchVersion = searchQuery ? broadcastVersion : 0;
  useEffect(() => {
    if (!activeId) return;
    let cancelled = false;
    setMessageLoading(true);
    setMessageError("");
    setSearchResults([]);
    const timer = setTimeout(() => {
      getChatMessages(activeId, { page: 0, size: 50, q: searchQuery || undefined }).then((data) => {
        if (cancelled) return;
        const items = mergeMessages(data.content.map((item) => mapMessage(item, counterpartId)));
        if (searchQuery) setSearchResults(items);
        else setRooms((current) => current.map((room) => room.id === activeId
          ? { ...room, messages: mergeMessages(room.messages, items) } : room));
      }).catch((error) => { if (!cancelled) setMessageError(error.message); })
        .finally(() => { if (!cancelled) setMessageLoading(false); });
    }, searchQuery ? 250 : 0);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [activeId, counterpartId, searchQuery, connectionVersion, searchVersion, setRooms]);

  const updateSettings = async (roomId, field) => {
    if (mutationRef.current.has(roomId)) return;
    const target = rooms.find((room) => room.id === roomId);
    if (!target) return;
    mutationRef.current.add(roomId);
    const payload = { [field]: !target[field] };
    try {
      await updateChatRoomSettings(roomId, payload);
      setRooms((current) => current.map((room) => room.id === roomId ? { ...room, ...payload } : room));
    } catch (error) { setStatus(error.message); }
    finally { mutationRef.current.delete(roomId); }
  };

  const send = (roomId, text) => {
    try {
      if (!socketRef.current) throw new Error("채팅 서버 연결 후 다시 보내주세요.");
      socketRef.current.publish(roomId, text);
      return true;
    } catch (error) { setStatus(error.message); return false; }
  };

  const leave = async (roomId) => {
    if (mutationRef.current.has(roomId)) return false;
    mutationRef.current.add(roomId);
    try { await leaveChatRoom(roomId); return true; }
    catch (error) { setStatus(error.message); return false; }
    finally { mutationRef.current.delete(roomId); }
  };

  return { connected, loadingRooms, roomError, messageLoading, messageError,
    searchResults, updateSettings, send, leave };
}
