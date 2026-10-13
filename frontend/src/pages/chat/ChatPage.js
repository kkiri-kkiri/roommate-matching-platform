import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import {
  ArrowLeft, CheckCheck,
  BellOff, LogOut, MessageCircle, MessagesSquare, MoreHorizontal, Pin, Search, Send, X,
} from "lucide-react";

import * as S from "./ChatPageCss";
import ProfileImage from "../../components/common/ProfileImage";
import DetailedProfile from "../../components/common/ProfileCard/DetailedProfile";
import { useChat } from "./useChat";

const CATEGORIES = [
  { id: "all", label: "전체" },
  { id: "roommate", label: "룸메찾기" },
  { id: "community", label: "커뮤니티" },
];
const CATEGORY_LABELS = Object.fromEntries(CATEGORIES.map(({ id, label }) => [id, label]));
const MAX_MESSAGE_LENGTH = 2000;
const SOURCE_POST_MESSAGES = {
  loading: "게시글 정보를 불러오는 중입니다.",
  deleted: "삭제된 게시글입니다.",
  error: "게시글 정보를 불러올 수 없습니다.",
  unauthorized: "로그인 상태를 확인한 후 게시글을 조회해 주세요.",
  forbidden: "게시글을 조회할 권한이 없습니다.",
};
function lastActivity(room) { return room.lastMessage?.sentAt; }

function dateKey(timestamp) {
  return new Date(timestamp).toLocaleDateString("sv-SE");
}

function formatTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
}

function formatListTime(timestamp) {
  if (!timestamp) return "";
  return dateKey(timestamp) === dateKey(Date.now())
    ? formatTime(timestamp)
    : new Date(timestamp).toLocaleDateString("ko-KR", { month: "long", day: "numeric" });
}

function formatDate(timestamp) {
  const prefix = dateKey(timestamp) === dateKey(Date.now()) ? "오늘 · " : "";
  return prefix + new Date(timestamp).toLocaleDateString("ko-KR", {
    year: "numeric", month: "long", day: "numeric", weekday: "long",
  });
}

function ChatDialog({ title, description, onDismiss, children }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();
    return () => dialog.close();
  }, []);

  return (
    <S.Dialog
      ref={dialogRef}
      aria-labelledby="chat-dialog-title"
      aria-describedby={description ? "chat-dialog-description" : undefined}
      onCancel={(event) => { event.preventDefault(); onDismiss(); }}
      onClick={(event) => {
        if (event.target !== event.currentTarget) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right ||
          event.clientY < bounds.top || event.clientY > bounds.bottom) onDismiss();
      }}
    >
      <S.DialogHeader>
        <S.DialogTitle id="chat-dialog-title">{title}</S.DialogTitle>
        <S.IconButton type="button" aria-label="닫기" onClick={onDismiss} autoFocus>
          <X size={20} aria-hidden="true" />
        </S.IconButton>
      </S.DialogHeader>
      {description && <S.DialogDescription id="chat-dialog-description">{description}</S.DialogDescription>}
      {children}
    </S.Dialog>
  );
}

function ChatPage({ currentUser }) {
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthenticated = Boolean(currentUser);
  const [searchParams, setSearchParams] = useSearchParams();
  const [rooms, setRooms] = useState([]);
  const [category, setCategory] = useState("all");
  const [roomSearch, setRoomSearch] = useState("");
  const [mobileOpen, setMobileOpen] = useState(Boolean(searchParams.get("room")));
  const [drafts, setDrafts] = useState({});
  const [messageSearch, setMessageSearch] = useState("");
  const [roommateProfile, setRoommateProfile] = useState(null);
  const [leaveRoomId, setLeaveRoomId] = useState(null);
  const [optionsRoomId, setOptionsRoomId] = useState(null);
  const optionsRef = useRef(null);

  useEffect(() => {
    if (!optionsRoomId) return;
    const closeOnOutsideClick = (event) => {
      if (!optionsRef.current?.contains(event.target)) setOptionsRoomId(null);
    };
    const closeOnEscape = (event) => {
      if (event.key === "Escape") {
        optionsRef.current?.querySelector("button[aria-expanded]")?.focus({ preventScroll: true });
        setOptionsRoomId(null);
      }
    };
    document.addEventListener("pointerdown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("pointerdown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [optionsRoomId]);
  const [status, setStatus] = useState("");
  const scrollerRef = useRef(null);
  const inputRef = useRef(null);
  const listHeadingRef = useRef(null);

  const selectedId = searchParams.get("room") ?? rooms[0]?.id ?? "";
  const activeRoom = rooms.find((room) => room.id === selectedId);
  const showMobileRoom = mobileOpen && Boolean(activeRoom);
  const leaveRoom = rooms.find((room) => room.id === leaveRoomId);
  const profileView = activeRoom?.category === "roommate" && roommateProfile?.roomId === selectedId
    ? roommateProfile.view : searchParams.get("view") === "profile" ? "summary" : null;
  const profileOpen = profileView === "summary" && Boolean(activeRoom) && !activeRoom.anonymous;
  const detailedProfileOpen = profileView === "detail" && activeRoom?.category === "roommate" && !activeRoom.anonymous;
  const roomSearchQuery = roomSearch.trim().toLocaleLowerCase();
  const pinnedCount = rooms.filter((room) => room.pinned).length;
  const categoryCounts = Object.fromEntries(CATEGORIES.map(({ id }) => [
    id, rooms.filter((room) => id === "all" || room.category === id).length,
  ]));
  const visibleRooms = rooms
    .filter((room) => category === "all" || room.category === category)
    .filter((room) => room.nickname.toLocaleLowerCase().includes(roomSearchQuery))
    .sort((first, second) => Number(Boolean(second.pinned)) - Number(Boolean(first.pinned))
      || new Date(lastActivity(second) || 0) - new Date(lastActivity(first) || 0));
  const draft = drafts[selectedId] || "";
  const messages = activeRoom?.messages || [];
  const searchQuery = messageSearch.trim().toLocaleLowerCase();

  const lastMessageId = messages[messages.length - 1]?.id;
  const profileHref = `/chat?room=${encodeURIComponent(selectedId)}&view=profile`;

  const { connected, loadingRooms, roomError, messageLoading, messageError, searchResults,
    updateSettings, send, leave, sourcePost } = useChat({
    isAuthenticated, rooms, setRooms, activeRoom, searchQuery, location, setStatus,
    onCreated: (roomId) => {
      setCategory("all"); setRoomSearch(""); setMobileOpen(true);
      setSearchParams({ room: roomId }, { replace: true, state: null });
    },
  });
  const displayedMessages = searchQuery ? searchResults : messages;
  useEffect(() => { setMessageSearch(""); }, [selectedId]);

  useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    scroller.scrollTop = searchQuery ? 0 : scroller.scrollHeight;
  }, [selectedId, lastMessageId, mobileOpen, searchQuery, messageLoading]);

  const selectRoom = (roomId) => {
    setRoommateProfile(null);
    setSearchParams({ room: roomId }, { replace: true });
    setMobileOpen(true);
    setStatus("");
    requestAnimationFrame(() => scrollerRef.current?.focus({ preventScroll: true }));
  };

  const selectCategory = (nextCategory) => {
    setCategory(nextCategory);
    if (activeRoom && nextCategory !== "all" && activeRoom.category !== nextCategory) {
      setSearchParams({ room: "" }, { replace: true });
      setMobileOpen(false);
    }
  };

  const togglePin = (roomId) => updateSettings(roomId, "pinned");
  const toggleMute = (roomId) => updateSettings(roomId, "muted");

  const closeProfile = () => {
    if (activeRoom?.category === "roommate") {
      setRoommateProfile({ roomId: selectedId, view: null });
      return;
    }
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete("view");
      return next;
    }, { replace: true });
  };

  const sendMessage = (event) => {
    event.preventDefault();
    const text = draft.trim();
    if (!activeRoom || !text || text.length > MAX_MESSAGE_LENGTH) return;
    if (!send(selectedId, text)) return;
    setDrafts((current) => ({ ...current, [selectedId]: "" }));

    inputRef.current?.focus();
  };

  const confirmLeave = async () => {
    if (!leaveRoom || !await leave(leaveRoom.id)) return;
    setRooms((current) => current.filter((room) => room.id !== leaveRoom.id));
    setDrafts((current) => { const next = { ...current }; delete next[leaveRoom.id]; return next; });
    if (leaveRoom.id === selectedId) {
      setSearchParams({ room: "" }, { replace: true });
      setMobileOpen(false);
    }
    setLeaveRoomId(null);
    setStatus(`${leaveRoom.nickname}님과의 채팅방에서 나왔어요.`);
    requestAnimationFrame(() => listHeadingRef.current?.focus());
  };

  if (!isAuthenticated) {
    return (
      <S.Page>
        <ChatDialog title="로그인 후 이용해주세요" onDismiss={() => navigate("/", { replace: true })}>
          <S.DialogActions>
            <S.SecondaryButton type="button" onClick={() => navigate("/", { replace: true })}>닫기</S.SecondaryButton>
            <S.PrimaryLink to="/login" replace>로그인</S.PrimaryLink>
          </S.DialogActions>
        </ChatDialog>
      </S.Page>
    );
  }

  return (
    <S.Page $fullscreen>
      <S.ChatGlobalStyle />
      <S.Content>
        <S.Workspace>
          <S.Sidebar $mobileOpen={showMobileRoom} aria-labelledby="chat-list-title">
            <S.ListHeader>
              <S.HomeLink to="/" aria-label="메인페이지로 이동">
                <img src="/img/kkiri-kkiri.png" alt="끼리끼리 로고" />
              </S.HomeLink>
              <S.ListHeadingRow>
                <S.ScreenReaderOnly as="h2" id="chat-list-title" ref={listHeadingRef} tabIndex={-1}>채팅 목록</S.ScreenReaderOnly>
                <S.RoomSearchField>
                  <Search size={15} aria-hidden="true" />
                  <S.RoomSearchInput type="search" aria-label="채팅방 검색" placeholder="이름 검색"
                    value={roomSearch} onChange={(event) => setRoomSearch(event.target.value)} />
                  <S.RoomSearchClearButton type="button" aria-label="채팅방 검색어 지우기" onClick={() => setRoomSearch("")}>
                    <X size={15} aria-hidden="true" />
                  </S.RoomSearchClearButton>
                </S.RoomSearchField>
              </S.ListHeadingRow>
              <S.FilterList role="group" aria-label="채팅 분류 필터">
                {CATEGORIES.map((filter) => (
                  <S.FilterButton key={filter.id} type="button" $active={category === filter.id}
                    aria-pressed={category === filter.id} onClick={() => selectCategory(filter.id)}>
                    {filter.label} <span>{categoryCounts[filter.id]}</span>
                  </S.FilterButton>
                ))}
              </S.FilterList>
            </S.ListHeader>

            {loadingRooms || roomError ? (
              <S.EmptyState $compact><p role="status">{roomError || "채팅방을 불러오는 중입니다."}</p></S.EmptyState>
            ) : visibleRooms.length ? (
              <S.ConversationList aria-label={`${CATEGORY_LABELS[category]} 채팅 목록`}>
                {visibleRooms.map((room) => {
                  const recentMessage = room.lastMessage;
                  return (
                    <S.ConversationItem key={room.id} ref={optionsRoomId === room.id ? optionsRef : null}>
                      <S.ConversationSummary>
                        <S.ConversationButton type="button" $active={selectedId === room.id}
                          $unread={room.unread > 0} aria-pressed={selectedId === room.id}
                          aria-controls="chat-room-panel" onClick={() => selectRoom(room.id)}>
                          <ProfileImage as={S.Avatar} major={room.photo ? undefined : room.major} fallbackSrc={room.photo} alt={`${room.nickname} 프로필`} />
                          <S.ConversationDetails>
                            <S.ConversationTop>
                              <S.NicknameGroup>
                                <S.Nickname>{room.nickname}</S.Nickname>
                                {room.pinned && <Pin size={14} role="img" aria-label="고정된 채팅방" />}
                                {room.muted && <BellOff size={14} role="img" aria-label="알림 꺼짐" />}
                              </S.NicknameGroup>
                              <S.Timestamp dateTime={lastActivity(room)}>{formatListTime(lastActivity(room))}</S.Timestamp>
                            </S.ConversationTop>
                            <S.ConversationPreview>
                              <S.PreviewText>{recentMessage
                                ? `${recentMessage.sender === "me" ? "나: " : ""}${recentMessage.text}`
                                : "첫 인사를 건네보세요"}</S.PreviewText>
                              {room.unread > 0 && <S.UnreadBadge aria-label={`읽지 않은 메시지 ${room.unread}개`}>{room.unread}</S.UnreadBadge>}
                            </S.ConversationPreview>
                            <S.CategoryBadge $category={room.category}>{CATEGORY_LABELS[room.category]}</S.CategoryBadge>
                          </S.ConversationDetails>
                        </S.ConversationButton>
                        <S.RoomOptionsButton type="button" $pinned={Boolean(room.pinned)}
                          aria-label={`${room.nickname} 채팅방 옵션${room.pinned ? " (고정됨)" : ""}`}
                          aria-expanded={optionsRoomId === room.id} aria-controls={`room-options-${room.id}`}
                          title="채팅방 옵션" onClick={() => setOptionsRoomId((current) => current === room.id ? null : room.id)}>
                          <MoreHorizontal size={18} aria-hidden="true" />
                        </S.RoomOptionsButton>
                      </S.ConversationSummary>
                      {optionsRoomId === room.id && (
                        <S.RoomOptionsPanel id={`room-options-${room.id}`} role="group" aria-label={`${room.nickname} 채팅방 옵션`}>
                          <S.SecondaryButton type="button" disabled={!room.pinned && pinnedCount >= 2}
                            title="채팅방은 최대 2개까지 고정할 수 있어요."
                            onClick={() => { togglePin(room.id); setOptionsRoomId(null); }}>
                            <Pin size={16} aria-hidden="true" />{room.pinned ? "고정 해제" : "채팅방 고정"}
                          </S.SecondaryButton>
                          <S.SecondaryButton type="button" onClick={() => { toggleMute(room.id); setOptionsRoomId(null); }}>
                            <BellOff size={16} aria-hidden="true" />{room.muted ? "알림 켜기" : "알림 끄기"}
                          </S.SecondaryButton>
                          <S.DangerButton type="button" onClick={() => { setLeaveRoomId(room.id); setOptionsRoomId(null); }}>
                            <LogOut size={16} aria-hidden="true" />방 나가기
                          </S.DangerButton>
                        </S.RoomOptionsPanel>
                      )}
                    </S.ConversationItem>
                  );
                })}
              </S.ConversationList>
            ) : roomSearchQuery ? (
              <S.EmptyState $compact>
                <S.EmptyIcon><Search size={28} aria-hidden="true" /></S.EmptyIcon>
                <h3>검색 결과가 없어요</h3>
                <p>다른 이름으로 검색해 보세요.</p>
                <S.SecondaryButton type="button" onClick={() => setRoomSearch("")}>검색 초기화</S.SecondaryButton>
              </S.EmptyState>
            ) : (
              <S.EmptyState $compact>
                <S.EmptyImage src="img\한경이 이미지\감정\당황 한경.png" alt="당황한 한경" />
                <h3>현재 채팅방이 없어요</h3>
                {category !== "all" && rooms.length > 0 && (
                  <S.SecondaryButton type="button" onClick={() => selectCategory("all")}>전체 대화 보기</S.SecondaryButton>
                )}
              </S.EmptyState>
            )}
          </S.Sidebar>

          <S.RoomPanel id="chat-room-panel" $mobileOpen={showMobileRoom} aria-label={activeRoom ? `${activeRoom.nickname}님과의 대화` : "채팅방"}>
            {activeRoom ? (
              <>
                <S.RoomHeader>
                  <S.MobileBackButton type="button" aria-label="채팅 목록으로 돌아가기" onClick={() => {
                    setMobileOpen(false);
                    requestAnimationFrame(() => listHeadingRef.current?.focus());
                  }}><ArrowLeft size={21} aria-hidden="true" /></S.MobileBackButton>
                  <S.ParticipantButton as={activeRoom.anonymous ? "div" : activeRoom.category === "roommate" ? "button" : Link}
                    type={!activeRoom.anonymous && activeRoom.category === "roommate" ? "button" : undefined}
                    to={activeRoom.anonymous || activeRoom.category === "roommate" ? undefined : profileHref}
                    onClick={!activeRoom.anonymous && activeRoom.category === "roommate"
                      ? () => setRoommateProfile({ roomId: selectedId, view: "summary" }) : undefined}
                    aria-label={activeRoom.anonymous ? "익명 참여자" : `${activeRoom.nickname}님 프로필 보기`}>
                    <ProfileImage as={S.Avatar} major={activeRoom.photo ? undefined : activeRoom.major} fallbackSrc={activeRoom.photo} alt="" />
                    <div>
                      <S.ParticipantName>
                        <span>{activeRoom.nickname}</span>
                        {activeRoom.pinned && <Pin size={15} aria-hidden="true" />}
                        {activeRoom.muted && <BellOff size={15} aria-label="알림 끔" />}
                      </S.ParticipantName>
                      {!activeRoom.anonymous && <S.ParticipantMeta>{activeRoom.dormitory} 생활관 · {activeRoom.year}학번</S.ParticipantMeta>}
                    </div>
                  </S.ParticipantButton>
                </S.RoomHeader>

                {activeRoom.category === "community" && activeRoom.sourcePost && (
                  <S.SourcePost>
                    <span>대화를 시작한 게시글</span>
                    {sourcePost.status === "success" ? (
                      <S.ContextLink to={`/community/posts/${encodeURIComponent(activeRoom.sourcePost)}`}>
                        {sourcePost.title}
                      </S.ContextLink>
                    ) : <span role="status">{SOURCE_POST_MESSAGES[sourcePost.status]}</span>}
                  </S.SourcePost>
                )}

                <S.SearchBar role="search" aria-label="대화 내역 검색">
                  <Search size={18} aria-hidden="true" />
                  <S.SearchField>
                    <S.SearchInput type="search" value={messageSearch}
                      aria-label="대화 내용 검색" placeholder="대화 내용을 검색해 보세요"
                      onChange={(event) => setMessageSearch(event.target.value)} />
                    <S.SearchClearButton type="button" aria-label="검색어 지우기" onClick={() => setMessageSearch("")}>
                      <X size={18} aria-hidden="true" />
                    </S.SearchClearButton>
                  </S.SearchField>
                  {searchQuery && <S.SearchCount role="status">{displayedMessages.length}건</S.SearchCount>}
                </S.SearchBar>

                <S.MessageScroller ref={scrollerRef} tabIndex={0} aria-label="대화 내용">
                  {!searchQuery && messages.length > 0 && <S.ConversationStart>최근 대화 내역이에요.</S.ConversationStart>}

                  {messageLoading || messageError ? <S.EmptyState><p role="status">{messageError || "메시지를 불러오는 중입니다."}</p></S.EmptyState> : displayedMessages.length ? displayedMessages.map((item, index) => {
                    const previous = displayedMessages[index - 1];
                    const showDate = !previous || dateKey(previous.sentAt) !== dateKey(item.sentAt);
                    const own = item.sender === "me";
                    return (
                      <S.MessageGroup key={item.id}>
                        {showDate && <S.DateDivider><span><time dateTime={dateKey(item.sentAt)}>{formatDate(item.sentAt)}</time></span></S.DateDivider>}
                        <S.MessageRow $own={own}>
                          {!own && <ProfileImage as={S.Avatar} $small major={activeRoom.photo ? undefined : activeRoom.major} fallbackSrc={activeRoom.photo} alt="" />}
                          <S.BubbleColumn>
                            {!own && <S.MessageAuthor>{activeRoom.nickname}</S.MessageAuthor>}
                            {own && <S.ScreenReaderOnly>내 메시지</S.ScreenReaderOnly>}
                            <S.MessageBubble $own={own}>{item.text}</S.MessageBubble>
                            <S.MessageMeta $own={own}>
                              {own && <S.ReadStatus>{item.read && <CheckCheck size={13} aria-hidden="true" />}{item.read ? "읽음" : "1"}</S.ReadStatus>}
                              <time dateTime={item.sentAt}>{formatTime(item.sentAt)}</time>
                            </S.MessageMeta>
                          </S.BubbleColumn>
                        </S.MessageRow>
                      </S.MessageGroup>
                    );
                  }) : searchQuery ? (
                    <S.EmptyState>
                      <S.EmptyIcon><Search size={32} aria-hidden="true" /></S.EmptyIcon>
                      <h3>검색 결과가 없어요</h3>
                      <p>다른 검색어를 입력해 보세요.</p>
                    </S.EmptyState>
                  ) : (
                    <S.EmptyState>
                      <S.EmptyIcon><MessageCircle size={32} aria-hidden="true" /></S.EmptyIcon>
                      <h3>{activeRoom.nickname}님에게 첫 인사를 건네보세요</h3>
                      <p>생활 패턴이나 관심사를 이야기하며<br />서로를 조금씩 알아가요.</p>
                    </S.EmptyState>
                  )}
                </S.MessageScroller>

                <S.Composer onSubmit={sendMessage}>
                  <S.ScreenReaderOnly as="label" htmlFor="chat-message">메시지 보내기</S.ScreenReaderOnly>
                  <S.ComposerField>
                    <S.MessageInput id="chat-message" ref={inputRef} value={draft} rows={1}
                      maxLength={MAX_MESSAGE_LENGTH} placeholder={`${activeRoom.nickname}님에게 메시지를 남겨보세요`}
                      onChange={(event) => setDrafts((current) => ({ ...current, [selectedId]: event.target.value }))}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) {
                          event.preventDefault();
                          if (!event.repeat) sendMessage(event);
                        }
                      }} />
                    <S.SendButton type="submit" disabled={!draft.trim() || !connected} aria-label="메시지 전송">
                      <Send size={18} aria-hidden="true" />전송
                    </S.SendButton>
                  </S.ComposerField>
                </S.Composer>
              </>
            ) : (
              <S.EmptyState>
                {rooms.length ? (
                  <S.EmptyIcon><MessagesSquare size={40} aria-hidden="true" /></S.EmptyIcon>
                ) : (
                  <S.EmptyImage src="img\한경이 이미지\감정\당황 한경.png" alt="당황한 한경" />
                )}
                <h2>{rooms.length ? "어떤 이야기를 나눠볼까요?" : "현재 채팅방이 없어요"}</h2>
                {rooms.length > 0 && <p>{"목록에서 채팅방을 선택해\n이웃과의 대화를 이어가세요."}</p>}
              </S.EmptyState>
            )}
          </S.RoomPanel>
        </S.Workspace>
        <S.StatusMessage role="status" aria-live="polite">{status || (!connected ? "채팅 서버에 연결하는 중입니다." : "")}</S.StatusMessage>
      </S.Content>

      {profileOpen && (
        <ChatDialog title="상대방 프로필" onDismiss={closeProfile}>
          <S.ProfileSummary>
            <ProfileImage as={S.Avatar} $large major={activeRoom.photo ? undefined : activeRoom.major} fallbackSrc={activeRoom.photo} alt={`${activeRoom.nickname} 프로필`} />
            <div><S.ParticipantName>{activeRoom.nickname}</S.ParticipantName><S.ParticipantMeta>{activeRoom.major} · {activeRoom.year}학번</S.ParticipantMeta></div>
          </S.ProfileSummary>
          <S.DialogDescription>{activeRoom.introduction}</S.DialogDescription>
          <S.ProfileDetails>
            <div><dt>생활관</dt><dd>{activeRoom.dormitory} 생활관</dd></div>
            <div><dt>대화 시작</dt><dd>{CATEGORY_LABELS[activeRoom.category]}</dd></div>
            {activeRoom.category === "roommate" && activeRoom.matchScore != null && <div><dt>매칭점수</dt><dd>{activeRoom.matchScore}점</dd></div>}
          </S.ProfileDetails>
          {activeRoom.category === "roommate" && (
            <S.DialogActions>
              <S.SecondaryButton type="button" disabled={!activeRoom.counterpartId} onClick={() => {
                setRoommateProfile({ roomId: selectedId, view: "detail" });
              }}>상세 프로필 보기</S.SecondaryButton>
            </S.DialogActions>
          )}
        </ChatDialog>
      )}

      {detailedProfileOpen && (
        <DetailedProfile profileId={activeRoom.counterpartId} onDismiss={closeProfile} />
      )}

      {leaveRoom && (
        <ChatDialog title="채팅방에서 나갈까요?" description={`${leaveRoom.nickname}님과의 대화가 목록에서 사라져요. 작성 중인 메시지도 지워져요.`} onDismiss={() => setLeaveRoomId(null)}>
          <S.DialogActions>
            <S.SecondaryButton type="button" onClick={() => setLeaveRoomId(null)}>계속 대화하기</S.SecondaryButton>
            <S.DangerButton type="button" onClick={confirmLeave}>나가기</S.DangerButton>
          </S.DialogActions>
        </ChatDialog>
      )}
    </S.Page>
  );
}

export default ChatPage;
