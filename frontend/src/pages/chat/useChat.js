import { useEffect, useRef, useState } from "react";
import { createChatRoom, getChatRoom, markChatRoomRead,
  updateChatRoomSettings, leaveChatRoom } from "../../api/chatApi";
import { createChatSocket } from "../../api/chatSocket";
import { getCommunityPost } from "../../api/communityApi";
import { useChatRooms } from "./useChatRooms";
import { useChatMessages } from "./useChatMessages";
import { useChatSync } from "./useChatSync";
import { mapMessage, mergeMessages, validMessage } from "./chatMessages";

const ANONYMOUS_PROFILE_IMAGE = `${process.env.PUBLIC_URL || ""}/img/한경이 이미지/감정/힙합 한경이.png`;

function mapRoom(room) {
  const profile = room.counterpart;
  const anonymous = profile.id == null;
  return { ...room, id: String(room.id), category: room.category.toLowerCase(),
    counterpartId: profile.id, anonymous,
    nickname: anonymous ? "익명" : profile.nickname,
    photo: anonymous ? ANONYMOUS_PROFILE_IMAGE : profile.profileImageUrl,
    dormitory: profile.dormitory, year: profile.admissionYear, major: profile.major,
    matchScore: profile.matchScore, introduction: profile.introduction,
    tags: profile.tags || [], unread: room.unreadCount, messages: [] };
}

export function useChat({ isAuthenticated, rooms, setRooms, activeRoom, searchQuery,
  location, setStatus, onCreated, selectedRoomId, roomCategory = "all", roomQuery = "", visibleMessageIds }) {
  const socketRef = useRef(null);
  const creationRef = useRef(null);
  const roomRevisionRef = useRef(0);
  const mutationRef = useRef(new Set());
  const pendingReadRef = useRef(new Set());
  const readRefreshRef = useRef(0);
  const onCreatedRef = useRef(onCreated);
  onCreatedRef.current = onCreated;
  const [connected, setConnected] = useState(false);
  const [connectionVersion, setConnectionVersion] = useState(0);
  const [broadcastVersion, setBroadcastVersion] = useState(0);
  const [sourcePost, setSourcePost] = useState(null);
  const sourcePostId = activeRoom?.category === "community" ? activeRoom.sourcePost : null;
  const roomList = useChatRooms({ isAuthenticated, rooms, setRooms,
    selectedRoomId: selectedRoomId || activeRoom?.id, category: roomCategory, q: roomQuery,
    locationKey: location.key, revisionRef: roomRevisionRef, mutationRef, mapRoom, setStatus });
  const refreshRooms = roomList.refreshRooms;

  useEffect(() => {
    if (!isAuthenticated || !sourcePostId) return;
    let cancelled = false;
    setSourcePost({ id: sourcePostId, status: "loading" });
    // getPost는 유효한 양수 ID의 활성 게시글이 없을 때 400 / INVALID_REQUEST를 반환합니다.
    // 다른 API의 INVALID_REQUEST나 잘못된 ID를 삭제로 해석하지 않습니다.
    if (!Number.isSafeInteger(Number(sourcePostId)) || Number(sourcePostId) <= 0 || !/^\d+$/.test(String(sourcePostId))) {
      setSourcePost({ id: sourcePostId, status: "error" });
      return;
    }
    getCommunityPost(sourcePostId).then((post) => {
      if (!post || typeof post.title !== "string" || !post.title.trim()) throw new Error("Invalid post response");
      if (!cancelled) setSourcePost({ id: sourcePostId, status: "success", title: post.title });
    }).catch((error) => {
      const status = error.status === 401 || error.errorCode === "UNAUTHORIZED" ? "unauthorized"
        : error.status === 403 || error.errorCode === "FORBIDDEN" ? "forbidden"
          : error.status === 400 && error.errorCode === "INVALID_REQUEST" ? "deleted" : "error";
      if (!cancelled) setSourcePost({ id: sourcePostId, status });
    });
    return () => { cancelled = true; };
  }, [isAuthenticated, sourcePostId]);

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
      if ((targetUserId != null && !positiveId(targetUserId)) || !positiveId(sourcePostId) || communityChat.sourcePost?.from !== "게시글") {
        setStatus("커뮤니티 채팅에 유효한 게시글 ID가 필요하며 댓글 채팅은 댓글 ID 전달이 아직 연결되지 않았습니다.");
        return;
      }
      payload = { targetUserId: targetUserId == null ? null : Number(targetUserId), category: "COMMUNITY", sourcePostId: Number(sourcePostId), sourceFrom: "POST" };
    }
    // StrictMode 재실행 중에는 이미 진행 중인 생성 요청을 재사용합니다.
    const key = JSON.stringify(payload);
    if (creationRef.current?.key !== key) creationRef.current = { key, promise: createChatRoom(payload) };
    creationRef.current.promise.then((data) => {
      if (cancelled) return;
      const room = mapRoom(data);
      roomRevisionRef.current += 1;
      setRooms((current) => [{ ...room,
        messages: current.find((item) => item.id === room.id)?.messages || [],
      }, ...current.filter((item) => item.id !== room.id)]);
      onCreatedRef.current(room.id);
      creationRef.current = null;
      void refreshRooms();
    }).catch((error) => { if (!cancelled) setStatus(error.message); });
    return () => { cancelled = true; };
  }, [incomingProfile, communityChat, isAuthenticated, setRooms, setStatus, refreshRooms]);

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
  const messageList = useChatMessages({ activeId, counterpartId, searchQuery, setRooms, setStatus,
    displayedMessages: activeRoom?.messages, visibleMessageIds });
  const { searchResults, setSearchResults } = messageList;
  useChatSync({ isAuthenticated, connectionVersion, broadcastVersion, refreshRooms,
    syncMessages: messageList.syncMessages, syncReadStatuses: messageList.syncReadStatuses });
  useEffect(() => {
    if (!activeId || !socketRef.current) return;
    let cancelled = false;
    const unsubscribe = socketRef.current.subscribe(activeId, (data) => {
      if (cancelled) return;
      if (String(data.roomId) !== activeId) return;
      const item = mapMessage(data, counterpartId);
      setRooms((current) => current.map((room) => room.id === activeId
        ? { ...room, messages: mergeMessages(room.messages, [item]),
          lastMessage: !room.lastMessage || new Date(item.sentAt) >= new Date(room.lastMessage.sentAt) ? item : room.lastMessage } : room));
      setBroadcastVersion((value) => value + 1);
      roomRevisionRef.current += 1;
    });
    return () => { cancelled = true; unsubscribe(); };
  }, [activeId, counterpartId, connectionVersion, setRooms, setStatus, refreshRooms]);

  // Acknowledge exact incoming IDs only after history/broadcast data was rendered.
  // Never infer a receipt from the latest database message, time, or an ID upper bound.
  const displayedMessages = searchQuery ? searchResults : activeRoom?.messages;
  useEffect(() => {
    if (!isAuthenticated || !activeId || !displayedMessages || document.visibilityState === "hidden") return;
    const ids = [...new Set(displayedMessages.filter((item) => item.sender === "other" && !item.read
      && (visibleMessageIds == null || visibleMessageIds.includes(String(item.id))))
      .map((item) => item.id))].filter((id) => !pendingReadRef.current.has(`${activeId}:${id}`)).slice(0, 100);
    if (!ids.length) return;
    const pending = pendingReadRef.current;
    ids.forEach((id) => pending.add(`${activeId}:${id}`));
    const acknowledged = new Set(ids.map(String));
    const markLocal = (items) => items.map((item) => acknowledged.has(String(item.id)) ? { ...item, read: true } : item);
    markChatRoomRead(activeId, ids).then(() => {
      roomRevisionRef.current += 1;
      setRooms((current) => current.map((room) => room.id === activeId
        ? { ...room, messages: markLocal(room.messages) } : room));
      setSearchResults(markLocal);
      // Unloaded/late-committing messages remain unread. Do not force the badge to zero.
      const version = ++readRefreshRef.current;
      const revision = roomRevisionRef.current;
      return getChatRoom(activeId).then((fresh) => ({ fresh, version, revision }));
    }).then(({ fresh, version, revision }) => {
      if (version !== readRefreshRef.current || revision !== roomRevisionRef.current) return;
      roomRevisionRef.current += 1;
      setRooms((current) => current.map((room) => room.id === activeId
        ? { ...room, unread: fresh.unreadCount } : room));
    }).catch((error) => setStatus(error.message))
      .finally(() => ids.forEach((id) => pending.delete(`${activeId}:${id}`)));
  }, [isAuthenticated, activeId, displayedMessages, connectionVersion, setRooms, setStatus, visibleMessageIds, setSearchResults]);

  const updateSettings = async (roomId, field) => {
    if (mutationRef.current.has(roomId)) return;
    const target = rooms.find((room) => room.id === roomId);
    if (!target) return;
    mutationRef.current.add(roomId);
    const payload = { [field]: !target[field] };
    try {
      await updateChatRoomSettings(roomId, payload);
      roomRevisionRef.current += 1;
      setRooms((current) => current.map((room) => room.id === roomId ? { ...room, ...payload } : room));
      await refreshRooms();
    } catch (error) { setStatus(error.message); }
    finally { mutationRef.current.delete(roomId); }
  };

  const send = (roomId, text) => {
    try {
      if (!validMessage(text)) throw new Error("공백만인 메시지는 보낼 수 없으며 최대 2,000 코드 포인트까지 허용됩니다.");
      if (!socketRef.current) throw new Error("채팅 서버 연결 후 다시 보내주세요.");
      socketRef.current.publish(roomId, text);
      return true;
    } catch (error) { setStatus(error.message); return false; }
  };

  const leave = async (roomId) => {
    if (mutationRef.current.has(roomId)) return false;
    mutationRef.current.add(roomId);
    try {
      await leaveChatRoom(roomId);
      roomRevisionRef.current += 1;
      await refreshRooms();
      return true;
    }
    catch (error) { setStatus(error.message); return false; }
    finally { mutationRef.current.delete(roomId); }
  };

  return { ...roomList, ...messageList, connected,
    searchResults, updateSettings, send, leave,
    sourcePost: sourcePost && sourcePost.id === sourcePostId ? sourcePost : { status: "loading" } };
}
