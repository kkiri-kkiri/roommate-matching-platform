import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ArrowLeft, ArrowRight, CheckCheck,
  Clock3, LogOut, MessageCircle, MessagesSquare, Search, Send, UserRound, X,
} from "lucide-react";

import * as S from "./ChatPageCss";

const CATEGORIES = [
  { id: "all", label: "전체" },
  { id: "roommate", label: "룸메찾기" },
  { id: "community", label: "커뮤니티" },
];
const CATEGORY_LABELS = { dormitory: "기숙사", ...Object.fromEntries(CATEGORIES.map(({ id, label }) => [id, label])) };
const SAMPLE_TIME = Date.now() - 10 * 60 * 1000;
const MAX_MESSAGE_LENGTH = 2000;

function sampleTimestamp(minutesAgo) {
  return new Date(SAMPLE_TIME - minutesAgo * 60 * 1000).toISOString();
}

// 기존 프로필 목록과 같은 방식의 내장 아바타로 외부 이미지 요청 없이 표시합니다.
function createAvatar(background, shirt, hair = "#363a46") {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160">
    <rect width="160" height="160" rx="24" fill="${background}"/>
    <circle cx="80" cy="66" r="31" fill="#f2c5a7"/>
    <path d="M47 66c0-27 15-43 36-43 23 0 34 17 34 39-11-2-21-8-28-18-8 12-22 20-42 22Z" fill="${hair}"/>
    <path d="M35 160c3-35 20-53 45-53s42 18 45 53H35Z" fill="${shirt}"/>
    <circle cx="68" cy="67" r="2.4" fill="#3d3532"/><circle cx="92" cy="67" r="2.4" fill="#3d3532"/>
    <path d="M71 83c6 5 12 5 18 0" fill="none" stroke="#9b6254" stroke-width="2.5" stroke-linecap="round"/>
  </svg>`;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function message(id, sender, text, minutesAgo, read = true) {
  return { id, sender, text, sentAt: sampleTimestamp(minutesAgo), read };
}

const INITIAL_ROOMS = [
  {
    id: "roommate-cloud", category: "roommate", nickname: "포근한구름",
    photo: createAvatar("#dff6f2", "#00a79f"), dormitory: "나래", year: "24",
    major: "소프트웨어융합전공", matchScore: 94,
    introduction: "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
    tags: ["비흡연", "아침형", "주 1회 청소"], unread: 0,
    createdAt: sampleTimestamp(3000),
    messages: [
      message("cloud-1", "other", "안녕하세요! 룸메찾기에서 프로필 보고 연락드려요 😊", 3000),
      message("cloud-2", "me", "안녕하세요! 생활 패턴이 저랑 비슷하시네요.", 2995),
      message("cloud-3", "other", "맞아요. 저도 아침에 수업이 있어서 일찍 일어나는 편이에요.", 2990),
      message("cloud-4", "me", "혹시 공용 물품은 어떻게 사용하고 싶으세요?", 1500),
      message("cloud-5", "other", "청소도구는 같이 쓰고, 개인 물건은 각자 쓰면 좋겠어요!", 1490),
      message("cloud-6", "other", "저도 이번 학기에 나래 생활관 신청했어요.\n평소에는 몇 시쯤 주무세요?", 32),
      message("cloud-7", "me", "보통 11시쯤 자고, 아침 7시에 일어나요.\n주말에는 조금 더 자는 편이고요 :)", 28),
      message("cloud-8", "other", "저랑 생활 패턴이 정말 비슷하네요!", 25),
      message("cloud-9", "me", "그러게요! 청소는 일주일에 한 번 같이 하면 어떨까요?", 22),
      message("cloud-10", "other", "좋아요! 이번 주에 만나서 더 이야기 나눠요 😊", 0),
    ],
  },
  {
    id: "dormitory-afternoon", category: "dormitory", nickname: "느긋한오후",
    photo: createAvatar("#fce8ed", "#d8607a", "#4c3028"), dormitory: "나래", year: "24",
    major: "식품영양학전공", introduction: "조용하고 여유로운 분위기에서 각자의 루틴을 지켜요.",
    tags: ["비흡연", "조용한 방", "주말 외출"], unread: 2, createdAt: sampleTimestamp(120),
    messages: [
      message("afternoon-1", "me", "안녕하세요! 같은 나래 생활관이시네요.", 120),
      message("afternoon-2", "other", "네! 입사 준비는 잘하고 계세요?", 18),
      message("afternoon-3", "other", "혹시 공용 세탁실 위치 아시나요?", 16),
    ],
  },
  {
    id: "community-star", category: "community", nickname: "새벽별",
    photo: createAvatar("#e6edff", "#3478f6", "#2c2625"), dormitory: "호연", year: "23",
    major: "경영학전공", introduction: "늦게 자는 편이지만 방에서는 늘 이어폰을 사용해요.",
    tags: ["이어폰 사용", "깔끔해요", "개인 시간"], unread: 1, createdAt: sampleTimestamp(200),
    sourcePost: { id: "7", title: "주말에 같이 도서관에서 공부할 분을 찾아요", from: "게시글" },
    messages: [
      message("star-1", "me", "도서관 같이 가실 분 찾는 글 보고 연락드려요!", 200),
      message("star-2", "other", "반가워요! 토요일 오후에 가려고 해요.", 190),
      message("star-3", "me", "저도 좋아요. 2시쯤 만날까요?", 185),
      message("star-4", "other", "네, 도서관 1층에서 만나요!", 50),
    ],
  },
  {
    id: "roommate-mint", category: "roommate", nickname: "민트초코",
    photo: createAvatar("#fff0e1", "#ff9f4a", "#71452f"), dormitory: "비룡", year: "25",
    major: "시각미디어디자인전공", matchScore: 82,
    introduction: "같이 밥도 먹고 소소한 이야기를 나누는 룸메를 찾아요.",
    tags: ["향 제품 안 써요", "중간형", "같이 청소"], unread: 0, createdAt: sampleTimestamp(1600),
    messages: [
      message("mint-1", "other", "안녕하세요! 프로필 한마디가 마음에 들어서 연락드려요.", 1600),
      message("mint-2", "me", "반가워요! 편하게 이야기해요 😊", 1590),
      message("mint-3", "me", "혹시 기숙사 식사도 신청하셨나요?", 1500, false),
    ],
  },
  {
    id: "community-tea", category: "community", nickname: "따뜻한차",
    photo: createAvatar("#fff4d8", "#d9a52e", "#4d3429"), dormitory: "창조", year: "23",
    major: "상담심리교육전공", introduction: "작은 불편도 쌓아두지 않고 편하게 이야기했으면 해요.",
    tags: ["대화 좋아요", "같이 식사"], unread: 0, createdAt: sampleTimestamp(1800),
    sourcePost: { id: "1", title: "룸메이트와 함께 정한 작은 생활 규칙, 생각보다 좋아요", from: "댓글" },
    messages: [
      message("tea-1", "other", "생활 규칙 글에 남기신 댓글 보고 연락드려요. 청소는 어떻게 나누셨나요?", 1800),
      message("tea-2", "me", "공용 공간은 번갈아 청소하고, 개인 공간은 각자 관리해요!", 1790),
      message("tea-3", "other", "좋은 방법이네요. 알려주셔서 감사해요!", 1700),
    ],
  },
  {
    id: "roommate-forest", category: "roommate", nickname: "조용한숲",
    photo: createAvatar("#e8f3e7", "#5d8e62", "#3c312c"), dormitory: "비룡", year: "21",
    major: "조경학전공", matchScore: 57,
    introduction: "말수는 적지만 필요한 일은 바로바로 소통하는 편이에요.",
    tags: ["아침형", "환기 자주", "각자 청소"], unread: 0,
    createdAt: sampleTimestamp(2000), messages: [],
  },
];

function lastActivity(room) {
  return room.messages[room.messages.length - 1]?.sentAt || room.createdAt;
}

function dateKey(timestamp) {
  return new Date(timestamp).toLocaleDateString("sv-SE");
}

function formatTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString("ko-KR", { hour: "numeric", minute: "2-digit" });
}

function formatListTime(timestamp) {
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

function ChatPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [rooms, setRooms] = useState(INITIAL_ROOMS);
  const [category, setCategory] = useState("all");
  const [mobileOpen, setMobileOpen] = useState(Boolean(searchParams.get("room")));
  const [drafts, setDrafts] = useState({});
  const [messageSearch, setMessageSearch] = useState("");
  const [leaveRoomId, setLeaveRoomId] = useState(null);
  const [status, setStatus] = useState("");
  const scrollerRef = useRef(null);
  const inputRef = useRef(null);
  const listHeadingRef = useRef(null);
  const messageSequence = useRef(0);
  const selectedId = searchParams.get("room") ?? INITIAL_ROOMS[0].id;
  const activeRoom = rooms.find((room) => room.id === selectedId);
  const showMobileRoom = mobileOpen && Boolean(activeRoom);
  const leaveRoom = rooms.find((room) => room.id === leaveRoomId);
  const profileOpen = searchParams.get("view") === "profile" && Boolean(activeRoom);
  const visibleRooms = rooms
    .filter((room) => category === "all" || room.category === category)
    .sort((first, second) => new Date(lastActivity(second)) - new Date(lastActivity(first)));
  const draft = drafts[selectedId] || "";
  const messages = activeRoom?.messages || [];
  const searchQuery = messageSearch.trim().toLocaleLowerCase();
  const displayedMessages = searchQuery
    ? messages.filter((item) => item.text.toLocaleLowerCase().includes(searchQuery))
    : messages;
  const lastMessageId = messages[messages.length - 1]?.id;
  const profileHref = `/chat?room=${encodeURIComponent(selectedId)}&view=profile`;

  useEffect(() => {
    setMessageSearch("");
    setRooms((current) => current.some((room) => room.id === selectedId && room.unread > 0)
      ? current.map((room) => room.id === selectedId ? { ...room, unread: 0 } : room)
      : current);
  }, [selectedId]);

  useLayoutEffect(() => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    scroller.scrollTop = searchQuery ? 0 : scroller.scrollHeight;
  }, [selectedId, lastMessageId, mobileOpen, searchQuery]);

  const selectRoom = (roomId) => {
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

  const closeProfile = () => {
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
    const sentAt = new Date().toISOString();
    const nextMessage = {
      id: `local-${Date.now()}-${messageSequence.current++}`,
      sender: "me", text, sentAt, read: false,
    };
    setRooms((current) => current.map((room) => room.id === selectedId
      ? { ...room, messages: [...room.messages, nextMessage] } : room));
    setDrafts((current) => ({ ...current, [selectedId]: "" }));
    setStatus("메시지를 보냈어요.");
    inputRef.current?.focus();
  };

  const confirmLeave = () => {
    if (!leaveRoom) return;
    setRooms((current) => current.filter((room) => room.id !== leaveRoom.id));
    setDrafts((current) => { const next = { ...current }; delete next[leaveRoom.id]; return next; });
    setSearchParams({ room: "" }, { replace: true });
    setMobileOpen(false);
    setLeaveRoomId(null);
    setStatus(`${leaveRoom.nickname}님과의 채팅방에서 나왔어요.`);
    requestAnimationFrame(() => listHeadingRef.current?.focus());
  };

  return (
    <S.Page>
      <S.Content>
        <S.Workspace>
          <S.Sidebar $mobileOpen={showMobileRoom} aria-labelledby="chat-list-title">
            <S.ListHeader>
              <S.ListTitle id="chat-list-title" ref={listHeadingRef} tabIndex={-1}>
                채팅 <span>{rooms.length}</span>
              </S.ListTitle>
              <S.ListMeta>나의 이웃과 나누는 이야기</S.ListMeta>
              <S.FilterList role="group" aria-label="채팅 분류 필터">
                {CATEGORIES.map((filter) => (
                  <S.FilterButton key={filter.id} type="button" $active={category === filter.id}
                    aria-pressed={category === filter.id} onClick={() => selectCategory(filter.id)}>
                    {filter.label}
                  </S.FilterButton>
                ))}
              </S.FilterList>
            </S.ListHeader>

            {visibleRooms.length ? (
              <S.ConversationList aria-label={`${CATEGORY_LABELS[category]} 채팅 목록`}>
                {visibleRooms.map((room) => {
                  const recentMessage = room.messages[room.messages.length - 1];
                  return (
                    <S.ConversationItem key={room.id}>
                      <S.ConversationButton type="button" $active={selectedId === room.id}
                        $unread={room.unread > 0} aria-pressed={selectedId === room.id}
                        aria-controls="chat-room-panel" onClick={() => selectRoom(room.id)}>
                        <S.Avatar src={room.photo} alt={`${room.nickname} 프로필`} />
                        <S.ConversationDetails>
                          <S.ConversationTop>
                            <S.Nickname>{room.nickname}</S.Nickname>
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
                    </S.ConversationItem>
                  );
                })}
              </S.ConversationList>
            ) : (
              <S.EmptyState $compact>
                <S.EmptyIcon><MessagesSquare size={28} aria-hidden="true" /></S.EmptyIcon>
                <h3>아직 나눈 대화가 없어요</h3>
                <p>{category === "all" ? "새로운 이웃을 만나 인사를 건네보세요." : `${CATEGORY_LABELS[category]}에서 시작한 대화가 여기에 모여요.`}</p>
                {category !== "all" && rooms.length > 0
                  ? <S.SecondaryButton type="button" onClick={() => selectCategory("all")}>전체 대화 보기</S.SecondaryButton>
                  : <S.ContextLink to="/roommates">룸메이트 둘러보기 <ArrowRight size={15} aria-hidden="true" /></S.ContextLink>}
              </S.EmptyState>
            )}
            <S.ListFooter><Clock3 size={14} aria-hidden="true" />최근 메시지 순 · {visibleRooms.length}개의 대화</S.ListFooter>
          </S.Sidebar>

          <S.RoomPanel id="chat-room-panel" $mobileOpen={showMobileRoom} aria-label={activeRoom ? `${activeRoom.nickname}님과의 대화` : "채팅방"}>
            {activeRoom ? (
              <>
                <S.RoomHeader>
                  <S.MobileBackButton type="button" aria-label="채팅 목록으로 돌아가기" onClick={() => {
                    setMobileOpen(false);
                    requestAnimationFrame(() => listHeadingRef.current?.focus());
                  }}><ArrowLeft size={21} aria-hidden="true" /></S.MobileBackButton>
                  <S.ParticipantButton as={Link} to={profileHref} aria-label={`${activeRoom.nickname}님 프로필 보기`}>
                    <S.Avatar src={activeRoom.photo} alt="" />
                    <div>
                      <S.ParticipantName>{activeRoom.nickname}</S.ParticipantName>
                      <S.ParticipantMeta>{activeRoom.dormitory} 생활관 · {activeRoom.year}학번</S.ParticipantMeta>
                    </div>
                  </S.ParticipantButton>
                  <S.HeaderActions>
                    <S.SecondaryButton as={Link} to={profileHref}><UserRound size={16} aria-hidden="true" />프로필 보기</S.SecondaryButton>
                    <S.IconButton type="button" aria-label="채팅방 나가기" title="채팅방 나가기" onClick={() => setLeaveRoomId(activeRoom.id)}>
                      <LogOut size={19} aria-hidden="true" />
                    </S.IconButton>
                  </S.HeaderActions>
                </S.RoomHeader>

                <S.SearchBar role="search" aria-label="대화 내역 검색">
                  <Search size={18} aria-hidden="true" />
                  <S.SearchInput type="search" value={messageSearch}
                    aria-label="대화 내용 검색" placeholder="대화 내용을 검색해 보세요"
                    onChange={(event) => setMessageSearch(event.target.value)} />
                  {searchQuery && <S.SearchCount role="status">{displayedMessages.length}건</S.SearchCount>}
                </S.SearchBar>

                <S.MessageScroller ref={scrollerRef} tabIndex={0} aria-label="대화 내용">
                  {!searchQuery && messages.length > 0 && <S.ConversationStart>대화의 시작이에요. 반가운 인사를 나눠보세요.</S.ConversationStart>}

                  {displayedMessages.length ? displayedMessages.map((item, index) => {
                    const previous = displayedMessages[index - 1];
                    const showDate = !previous || dateKey(previous.sentAt) !== dateKey(item.sentAt);
                    const own = item.sender === "me";
                    return (
                      <S.MessageGroup key={item.id}>
                        {showDate && <S.DateDivider><span><time dateTime={dateKey(item.sentAt)}>{formatDate(item.sentAt)}</time></span></S.DateDivider>}
                        <S.MessageRow $own={own}>
                          {!own && <S.Avatar $small src={activeRoom.photo} alt="" />}
                          <S.BubbleColumn>
                            {!own && <S.MessageAuthor>{activeRoom.nickname}</S.MessageAuthor>}
                            {own && <S.ScreenReaderOnly>내 메시지</S.ScreenReaderOnly>}
                            <S.MessageBubble $own={own}>{item.text}</S.MessageBubble>
                            <S.MessageMeta $own={own}>
                              {own && <S.ReadStatus>{item.read && <CheckCheck size={13} aria-hidden="true" />}{item.read ? "읽음" : "안 읽음"}</S.ReadStatus>}
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
                  <S.ComposerLabel htmlFor="chat-message">메시지 보내기</S.ComposerLabel>
                  <S.ComposerField>
                    <S.MessageInput id="chat-message" ref={inputRef} value={draft} rows={2}
                      maxLength={MAX_MESSAGE_LENGTH} placeholder={`${activeRoom.nickname}님에게 메시지를 남겨보세요`}
                      onChange={(event) => setDrafts((current) => ({ ...current, [selectedId]: event.target.value }))}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) {
                          event.preventDefault();
                          if (!event.repeat) sendMessage(event);
                        }
                      }} />
                    <S.SendButton type="submit" disabled={!draft.trim()} aria-label="메시지 전송">
                      <Send size={18} aria-hidden="true" />전송
                    </S.SendButton>
                  </S.ComposerField>
                </S.Composer>
              </>
            ) : (
              <S.EmptyState>
                <S.EmptyIcon><MessagesSquare size={40} aria-hidden="true" /></S.EmptyIcon>
                <h2>{rooms.length ? "어떤 이야기를 나눠볼까요?" : "새로운 인연의 시작, 첫 대화"}</h2>
                <p>{rooms.length ? "목록에서 채팅방을 선택해\n이웃과의 대화를 이어가세요." : "아직 대화 중인 이웃이 없어요.\n나와 잘 맞는 룸메이트를 만나보세요."}</p>
                {!rooms.length && <S.PrimaryLink to="/roommates">룸메이트 둘러보기 <ArrowRight size={16} aria-hidden="true" /></S.PrimaryLink>}
              </S.EmptyState>
            )}
          </S.RoomPanel>
        </S.Workspace>
        <S.StatusMessage role="status" aria-live="polite">{status}</S.StatusMessage>
      </S.Content>

      {profileOpen && (
        <ChatDialog title="상대방 프로필" onDismiss={closeProfile}>
          <S.ProfileSummary>
            <S.Avatar $large src={activeRoom.photo} alt={`${activeRoom.nickname} 프로필`} />
            <div><S.ParticipantName>{activeRoom.nickname}</S.ParticipantName><S.ParticipantMeta>{activeRoom.major} · {activeRoom.year}학번</S.ParticipantMeta></div>
          </S.ProfileSummary>
          <S.DialogDescription>{activeRoom.introduction}</S.DialogDescription>
          <S.ProfileDetails>
            <div><dt>생활관</dt><dd>{activeRoom.dormitory} 생활관</dd></div>
            <div><dt>대화 시작</dt><dd>{CATEGORY_LABELS[activeRoom.category]}</dd></div>
            {activeRoom.matchScore !== undefined && <div><dt>매칭점수</dt><dd>{activeRoom.matchScore}점</dd></div>}
          </S.ProfileDetails>
          <S.ProfileTags aria-label="생활 습관">{activeRoom.tags.map((tag) => <span key={tag}>#{tag}</span>)}</S.ProfileTags>
          <S.DialogActions><S.SecondaryButton type="button" onClick={closeProfile}>대화로 돌아가기</S.SecondaryButton></S.DialogActions>
        </ChatDialog>
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
