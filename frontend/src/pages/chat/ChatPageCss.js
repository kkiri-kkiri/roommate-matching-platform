import styled, { createGlobalStyle, css } from "styled-components";
import { Link } from "react-router-dom";
import { TopBar } from "../../components/common/Header/HeaderCss";

export const ChatGlobalStyle = createGlobalStyle`
  ${TopBar} { display: none; }
  body { overflow: hidden; }
`;

const focus = css`
  &:focus-visible { outline: 3px solid rgba(0, 167, 159, 0.45); outline-offset: 3px; }
`;
const button = css`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  text-decoration: none;
  cursor: pointer;
  transition: background 160ms, box-shadow 160ms;
  &:disabled { cursor: default; opacity: 0.5; }
  ${focus}
`;
const primary = css`
  ${button}
  background: #0c3273;
  color: white;
  &:hover:not(:disabled) { background: #001d4f; }
`;

export const Page = styled.main`
  ${({ $fullscreen }) => $fullscreen && css`
    position: fixed;
    inset: 0;
    width: 100%;
  `}
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  overflow: hidden;
  color: #1a1c1c;
  background: #faf9f6;
  font-family: "Plus Jakarta Sans", Pretendard, "Noto Sans KR", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  line-height: 1.5;
  &, *, *::before, *::after { box-sizing: border-box; }
  button, input, textarea { font-family: inherit; }
  button, a, textarea { -webkit-tap-highlight-color: transparent; }
  svg { flex-shrink: 0; }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; }
  }
`;
export const Content = styled.div`display: flex; flex-direction: column; width: 100%; height: 100%; min-width: 0; min-height: 0;`;
export const Workspace = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 3fr);
  flex: 1;
  min-height: 0;
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #e2e2e4;
  border-radius: 0;
  background: white;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
  @media (max-width: 799px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;
export const Sidebar = styled.aside`
  overflow: auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #e2e2e4;
  @media (max-width: 799px) { display: ${({ $mobileOpen }) => $mobileOpen ? "none" : "flex"}; border-right: 0; }
`;
export const ListHeader = styled.div`flex-shrink: 0; padding: 16px 16px 12px; border-bottom: 1px solid #eeeeee;`;
export const HomeLink = styled(Link)`
  ${button}
  max-width: 100%;
  margin-bottom: 8px;
  padding: 0;
  background: transparent;
  img { display: block; width: 148px; max-width: 100%; height: auto; object-fit: contain; }
`;
export const ListHeadingRow = styled.div`display: flex; align-items: center; gap: 12px; min-width: 0; margin-bottom: 12px;`;
export const RoomSearchField = styled.div`
  display: flex;
  flex: 1;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 6px 8px;
  border: 1px solid #e2e2e4;
  border-radius: 8px;
  color: #747781;
  background: #f8f9fb;
  &:focus-within { border-color: #00a79f; }
`;
export const RoomSearchInput = styled.input`
  &::-webkit-search-cancel-button { -webkit-appearance: none; }
  width: 100%;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: #1a1c1c;
  font-size: 13px;
  line-height: 22px;
  ${focus}
  @media (max-width: 799px) { font-size: 16px; }
`;
export const FilterList = styled.div`display: flex; flex-wrap: wrap; gap: 6px;`;
export const FilterButton = styled.button`
  ${button}
  min-height: 36px;
  padding: 7px 11px; gap: 8px;
  border-radius: 999px;
  background: ${({ $active }) => $active ? "#0c3273" : "#f3f3f3"};
  color: ${({ $active }) => $active ? "white" : "#5e616b"};
  font-size: 12px;
  &:hover { background: ${({ $active }) => $active ? "#001d4f" : "#e8e8e8"}; }
`;
export const ConversationList = styled.ul`
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 8px 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  list-style: none;
  scrollbar-width: thin;
  scrollbar-color: #c4c6d2 transparent;
`;
export const ConversationItem = styled.li`position: relative; margin: 0; padding: 0;`;
export const ConversationSummary = styled.div`position: relative;`;
export const RoomOptionsPanel = styled.div`
  position: absolute;
  top: calc(100% - 4px);
  right: 8px;
  z-index: 10;
  display: flex;
  flex-direction: column;
  width: 160px;
  max-width: calc(100% - 16px);
  gap: 4px;
  padding: 6px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: white;
  box-shadow: 0 6px 20px rgba(12, 50, 115, 0.15);
  > button { justify-content: flex-start; border: 0; background: transparent; color: #1a1c1c; }
  > button:last-child { color: #ba1a1a; }
  > button:hover:not(:disabled) { background: #f3f3f3; }
`;
export const RoomSearchClearButton = styled.button`
  ${button}
  flex-shrink: 0;
  width: 24px;
  min-height: 24px;
  padding: 3px;
  background: transparent;
  color: #747781;
  &:hover { background: #e2e2e4; }
`;
export const RoomOptionsButton = styled.button`
  ${button}
  position: absolute;
  right: 8px;
  bottom: 6px;
  width: 32px;
  min-height: 32px;
  padding: 6px;
  background: ${({ $pinned }) => $pinned ? "#d5eeea" : "transparent"};
  color: ${({ $pinned }) => $pinned ? "#006a65" : "#747781"};
  &:hover:not(:disabled) { background: #e2f3ee; }
`;
export const ConversationButton = styled.button`
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  min-width: 0;
  padding: 12px;
  border: 0;
  border-left: 3px solid ${({ $active }) => $active ? "#00a79f" : "transparent"};
  background: ${({ $active }) => $active ? "#edf8f6" : "white"};
  color: #0c3273;
  text-align: left;
  cursor: pointer;
  transition: background 160ms;
  strong { font-weight: ${({ $unread }) => $unread ? 700 : 600}; }
  &:hover { background: ${({ $active }) => $active ? "#e2f3ee" : "#f7f8fa"}; }
  &:focus-visible { outline: 3px solid #00a79f; outline-offset: -3px; }
  @media (min-width: 800px) and (max-width: 1000px) { gap: 8px; padding: 12px 10px; }
`;
export const Avatar = styled.img`
  display: block;
  flex-shrink: 0;
  width: ${({ $small, $large }) => $large ? "64px" : $small ? "28px" : "40px"};
  height: ${({ $small, $large }) => $large ? "64px" : $small ? "28px" : "40px"};
  border-radius: ${({ $small }) => $small ? "8px" : "12px"};
  background: #edf2fc;
  object-fit: contain;
`;
export const ConversationDetails = styled.span`display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 4px;`;
export const ConversationTop = styled.span`display: flex; justify-content: space-between; align-items: center; gap: 8px; min-width: 0;`;
export const Nickname = styled.strong`min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px;`;
export const NicknameGroup = styled.span`
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  svg { flex-shrink: 0; color: #006a65; }
`;
export const Timestamp = styled.time`flex-shrink: 0; color: #747781; font-size: 12px; font-variant-numeric: tabular-nums;`;
export const ConversationPreview = styled.span`display: flex; align-items: center; gap: 8px; min-width: 0;`;
export const PreviewText = styled.span`flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; color: #5e616b; font-size: 13px; line-height: 18px;`;
export const UnreadBadge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  background: #006a65;
  color: white;
  font-size: 12px;
  font-weight: 700;
`;
export const CategoryBadge = styled.span`
  margin-right: 32px;
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  width: fit-content;
  padding: 2px 6px;
  border-radius: 999px;
  color: ${({ $category }) => ({ roommate: "#244485", community: "#80602b" }[$category] || "#5e616b")};
  background: ${({ $category }) => ({ roommate: "#edf2fc", community: "#f7f0e3" }[$category] || "#f3f3f3")};
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
`;
export const RoomPanel = styled.section`
  overflow: auto;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #f8f9fb;
  @media (max-width: 799px) { display: ${({ $mobileOpen }) => $mobileOpen ? "flex" : "none"}; }
`;
export const RoomHeader = styled.div`
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e2e4;
  background: white;
  @media (max-width: 799px) { padding: 12px; gap: 8px; }
`;
export const MobileBackButton = styled.button`
  ${button}
  display: none;
  width: 36px;
  padding: 8px;
  background: transparent;
  color: #0c3273;
  @media (max-width: 799px) { display: inline-flex; }
`;
export const ParticipantButton = styled.button`
  display: flex;
  flex: 1;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 0;
  border: 0;
  border-radius: 12px;
  color: inherit;
  background: transparent;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  > div { min-width: 0; }
  &:hover h2 { color: #006a65; }
  ${focus}
  @media (max-width: 799px) { flex: 1; gap: 8px; }
`;
export const ParticipantName = styled.h2`
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  margin: 0;
  color: #001d4f;
  font-size: 16px;
  font-weight: 600;
  line-height: 22px;
  > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
`;
export const ParticipantMeta = styled.p`margin: 3px 0 0; color: #747781; font-size: 12px; line-height: 18px;`;
export const SecondaryButton = styled.button`
  ${button}
  border: 1.5px solid #00a79f;
  background: white;
  color: #006a65;
  &:hover { background: #e7f8f6; }
  @media (max-width: 799px) { padding: 8px 12px; font-size: 12px; }
`;
export const IconButton = styled.button`
  ${button}
  width: 36px;
  padding: 7px;
  background: transparent;
  color: #5e616b;
  &:hover { background: #f3f3f3; color: #0c3273; }
`;
export const SearchBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
  padding: 8px 16px;
  border-bottom: 1px solid #e2e2e4;
  background: white;
  color: #747781;
  @media (max-width: 799px) { padding: 8px 12px; }
`;
export const SearchField = styled.div`position: relative; flex: 1; min-width: 0;`;
export const SearchClearButton = styled(IconButton)`
  position: absolute;
  top: 50%;
  right: 3px;
  transform: translateY(-50%);
  width: 28px;
  min-height: 28px;
  padding: 4px;
`;
export const SearchInput = styled.input`
  &::-webkit-search-cancel-button { -webkit-appearance: none; }
  width: 100%;
  min-width: 0;
  padding: 7px 36px 7px 10px;
  border: 1px solid #e2e2e4;
  border-radius: 8px;
  background: #f8f9fb;
  color: #1a1c1c;
  font-size: 14px;
  &::placeholder { color: #747781; }
  ${focus}
  @media (max-width: 799px) { font-size: 16px; }
`;
export const SearchCount = styled.span`flex-shrink: 0; font-size: 12px;`;
export const ContextLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 36px;
  color: #245bc0;
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;
  white-space: normal;
  overflow-wrap: anywhere;
  &:hover { text-decoration: underline; }
  ${focus}
`;
export const SourcePost = styled.div`
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  padding: 8px 16px;
  border-bottom: 1px solid #e2e2e4;
  background: white;
  > span { color: #747781; font-size: 12px; }
  @media (max-width: 799px) { padding: 8px 12px; }
`;
export const MessageScroller = styled.div`
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #c4c6d2 transparent;
  ${focus}
  @media (max-width: 799px) { padding: 12px; }
`;
export const DateDivider = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
  color: #747781;
  font-size: 12px;
  &::before, &::after { content: ""; flex: 1; height: 1px; background: #e5e7ec; }
  span { flex-shrink: 0; }
`;
export const MessageGroup = styled.div`margin-bottom: 12px;`;
export const MessageRow = styled.div`display: flex; justify-content: ${({ $own }) => $own ? "flex-end" : "flex-start"}; align-items: flex-start; gap: 8px;`;
export const BubbleColumn = styled.div`display: flex; flex-direction: column; max-width: 78%; min-width: 0;`;
export const MessageAuthor = styled.span`margin: 0 0 4px 2px; color: #444650; font-size: 12px; font-weight: 600;`;
export const MessageBubble = styled.div`
  padding: 8px 12px;
  border: 1px solid ${({ $own }) => $own ? "#0c3273" : "#e6e8ec"};
  border-radius: ${({ $own }) => $own ? "16px 4px 16px 16px" : "4px 16px 16px 16px"};
  background: ${({ $own }) => $own ? "#0c3273" : "white"};
  color: ${({ $own }) => $own ? "white" : "#30343d"};
  font-size: 14px;
  line-height: 22px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  @media (max-width: 799px) { font-size: 14px; line-height: 22px; }
`;
export const MessageMeta = styled.div`
  display: flex;
  justify-content: ${({ $own }) => $own ? "flex-end" : "flex-start"};
  align-items: center;
  gap: 6px;
  margin-top: 5px;
  color: #747781;
  font-size: 12px;
  time { font-variant-numeric: tabular-nums; }
`;
export const ReadStatus = styled.span`display: inline-flex; align-items: center; gap: 3px; color: #006a65; font-weight: 600;`;
export const ConversationStart = styled.p`margin: 0 0 16px; color: #747781; font-size: 12px; text-align: center;`;
export const Composer = styled.form`
  flex-shrink: 0;
  padding: 12px 16px;
  border-top: 1px solid #e2e2e4;
  background: white;
  @media (max-width: 799px) { padding: 12px; }
`;
export const ComposerField = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: #fcfcfc;
  &:focus-within { border-color: #00a79f; box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.12); }
`;
export const MessageInput = styled.textarea`
  display: block;
  flex: 1;
  width: 100%;
  min-width: 0;
  height: 38px;
  min-height: 38px;
  max-height: 38px;
  padding: 8px;
  resize: none;
  border: 0;
  border-radius: 8px;
  outline: none;
  background: transparent;
  color: #1a1c1c;
  font-size: 14px;
  line-height: 22px;
  &::placeholder { color: #747781; }
  @media (max-width: 799px) { font-size: 16px; }
`;
export const SendButton = styled.button`${primary} flex-shrink: 0; margin: 2px; padding: 7px 10px;`;
export const EmptyState = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow-y: auto;
  overflow-wrap: anywhere;
  padding: ${({ $compact }) => $compact ? "24px 16px" : "32px 20px"};
  text-align: center;
  h2, h3 { margin: 20px 0 0; color: #001d4f; font-size: ${({ $compact }) => $compact ? "16px" : "20px"}; line-height: 28px; }
  p { max-width: 320px; margin: 10px 0 24px; color: #747781; font-size: 14px; line-height: 24px; word-break: keep-all; white-space: pre-line; }
`;
export const EmptyIcon = styled.div`display: grid; place-items: center; width: 48px; height: 48px; border-radius: 50%; background: #e7f8f6; color: #006a65; svg { width: 24px; height: 24px; }`;
export const EmptyImage = styled.img`
  display: block;
  width: 120px;
  max-width: 100%;
  height: auto;
  flex-shrink: 0;
`;
export const PrimaryLink = styled(Link)`${primary}`;
export const StatusMessage = styled.p`flex-shrink: 0; margin: 0; padding: 4px 12px; color: #006a65; font-size: 12px; line-height: 20px; &:empty { padding: 0; }`;
export const Dialog = styled.dialog`
  width: min(400px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: 20px;
  overflow-y: auto;
  border: 0;
  border-radius: 24px;
  background: white;
  color: #1a1c1c;
  box-shadow: 0 16px 48px rgba(12, 50, 115, 0.15);
  &::backdrop { background: rgba(12, 50, 115, 0.4); }
  ${focus}
  @media (max-width: 480px) { padding: 20px 16px; }
`;
export const DialogHeader = styled.div`display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px;`;
export const DialogTitle = styled.h2`margin: 0; color: #001d4f; font-size: 20px; font-weight: 600; line-height: 28px;`;
export const DialogDescription = styled.p`margin: 0; color: #5e616b; font-size: 14px; line-height: 24px; word-break: keep-all;`;
export const ProfileSummary = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 16px 0;
  > div { min-width: 0; }
  strong { display: block; color: #001d4f; font-size: 20px; overflow-wrap: anywhere; }
  p { margin: 6px 0; color: #747781; font-size: 13px; }
`;
export const ProfileDetails = styled.dl`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px 20px;
  margin: 16px 0;
  padding: 16px;
  border-radius: 16px;
  background: #f7f8fa;
  &:last-child { margin-bottom: 0; }
  dt { color: #747781; font-size: 12px; }
  dd { margin: 0; color: #30343d; font-size: 13px; overflow-wrap: anywhere; }
`;
export const DialogActions = styled.div`display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 12px; margin-top: 16px;`;
export const DangerButton = styled.button`${button} background: #ba1a1a; color: white; &:hover { background: #93000a; }`;
export const ScreenReaderOnly = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
`;
