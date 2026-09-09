import styled, { css } from "styled-components";
import { Link } from "react-router-dom";

const focus = css`
  &:focus-visible { outline: 3px solid rgba(0, 167, 159, 0.45); outline-offset: 3px; }
`;
const button = css`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 10px 16px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 14px;
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
  min-height: 100vh;
  padding: 40px 20px 64px;
  color: #1a1c1c;
  background: #faf9f6;
  font-family: "Plus Jakarta Sans", Pretendard, "Noto Sans KR", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  line-height: 1.5;
  &, *, *::before, *::after { box-sizing: border-box; }
  button, input, textarea { font-family: inherit; }
  button, a, textarea { -webkit-tap-highlight-color: transparent; }
  svg { flex-shrink: 0; }
  @media (max-width: 799px) { padding: 24px 16px 40px; }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; }
  }
`;
export const Content = styled.div`width: min(100%, 1200px); min-width: 0; margin: 0 auto;`;
export const Workspace = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 7fr);
  height: max(800px, calc(100dvh - 160px));
  overflow: hidden;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: white;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
  @media (max-width: 799px) {
    grid-template-columns: minmax(0, 1fr);
    height: max(640px, calc(100dvh - 160px));
  }
`;
export const Sidebar = styled.aside`
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #e2e2e4;
  @media (max-width: 799px) { display: ${({ $mobileOpen }) => $mobileOpen ? "none" : "flex"}; border-right: 0; }
`;
export const ListHeader = styled.div`padding: 24px 20px 16px; border-bottom: 1px solid #eeeeee;`;
export const ListTitle = styled.h2`
  margin: 0;
  color: #001d4f;
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
  span { margin-left: 6px; color: #006a65; font-size: 16px; }
  ${focus}
`;
export const ListMeta = styled.p`margin: 6px 0 20px; color: #747781; font-size: 12px;`;
export const FilterList = styled.div`display: flex; flex-wrap: wrap; gap: 6px;`;
export const FilterButton = styled.button`
  ${button}
  min-height: 36px;
  padding: 7px 11px;
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
export const ConversationItem = styled.li`margin: 0; padding: 0;`;
export const ConversationButton = styled.button`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  min-width: 0;
  padding: 20px 16px;
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
  @media (min-width: 800px) and (max-width: 1000px) { gap: 8px; padding: 16px 10px; }
`;
export const Avatar = styled.img`
  display: block;
  flex-shrink: 0;
  width: ${({ $small, $large }) => $large ? "80px" : $small ? "32px" : "48px"};
  height: ${({ $small, $large }) => $large ? "80px" : $small ? "32px" : "48px"};
  border-radius: ${({ $small }) => $small ? "12px" : "16px"};
  background: #edf2fc;
  object-fit: cover;
`;
export const ConversationDetails = styled.span`display: flex; flex: 1; flex-direction: column; min-width: 0; gap: 7px;`;
export const ConversationTop = styled.span`display: flex; justify-content: space-between; align-items: center; gap: 8px; min-width: 0;`;
export const Nickname = styled.strong`min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 16px;`;
export const Timestamp = styled.time`flex-shrink: 0; color: #747781; font-size: 12px; font-variant-numeric: tabular-nums;`;
export const ConversationPreview = styled.span`display: flex; align-items: center; gap: 8px; min-width: 0;`;
export const PreviewText = styled.span`flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; color: #5e616b; font-size: 14px; line-height: 20px;`;
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
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  color: ${({ $category }) => ({ dormitory: "#006a65", roommate: "#244485", community: "#80602b" }[$category] || "#5e616b")};
  background: ${({ $category }) => ({ dormitory: "#e1f4ef", roommate: "#edf2fc", community: "#f7f0e3" }[$category] || "#f3f3f3")};
  font-size: 12px;
  font-weight: 600;
  line-height: 16px;
  white-space: nowrap;
`;
export const ListFooter = styled.div`display: flex; align-items: center; gap: 6px; padding: 16px 20px; border-top: 1px solid #eeeeee; color: #747781; font-size: 12px; line-height: 18px;`;
export const RoomPanel = styled.section`
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #f8f9fb;
  @media (max-width: 799px) { display: ${({ $mobileOpen }) => $mobileOpen ? "flex" : "none"}; }
`;
export const RoomHeader = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 24px;
  border-bottom: 1px solid #e2e2e4;
  background: white;
  @media (max-width: 799px) { padding: 16px; gap: 8px; }
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
export const ParticipantName = styled.h2`margin: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; color: #001d4f; font-size: 18px; font-weight: 600; line-height: 26px;`;
export const ParticipantMeta = styled.p`margin: 3px 0 0; color: #747781; font-size: 12px; line-height: 18px;`;
export const HeaderActions = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  @media (max-width: 480px) { > a { display: none; } }
`;
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
  width: 44px;
  padding: 10px;
  background: transparent;
  color: #5e616b;
  &:hover { background: #f3f3f3; color: #0c3273; }
`;
export const SearchBar = styled.div`
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 12px;
  padding: 12px 24px;
  border-bottom: 1px solid #e2e2e4;
  background: white;
  color: #747781;
  @media (max-width: 799px) { padding: 12px 16px; }
`;
export const SearchInput = styled.input`
  flex: 1;
  min-width: 0;
  padding: 10px 12px;
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
  white-space: nowrap;
  &:hover { text-decoration: underline; }
  ${focus}
`;
export const MessageScroller = styled.div`
  flex: 1;
  min-height: 0;
  padding: 24px;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color: #c4c6d2 transparent;
  ${focus}
  @media (max-width: 799px) { padding: 20px 16px; }
`;
export const DateDivider = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 24px 0;
  color: #747781;
  font-size: 12px;
  &::before, &::after { content: ""; flex: 1; height: 1px; background: #e5e7ec; }
  span { flex-shrink: 0; }
`;
export const MessageGroup = styled.div`margin-bottom: 20px;`;
export const MessageRow = styled.div`display: flex; justify-content: ${({ $own }) => $own ? "flex-end" : "flex-start"}; align-items: flex-start; gap: 8px;`;
export const BubbleColumn = styled.div`display: flex; flex-direction: column; max-width: 78%; min-width: 0;`;
export const MessageAuthor = styled.span`margin: 0 0 6px 2px; color: #444650; font-size: 12px; font-weight: 600;`;
export const MessageBubble = styled.div`
  padding: 12px 16px;
  border: 1px solid ${({ $own }) => $own ? "#0c3273" : "#e6e8ec"};
  border-radius: ${({ $own }) => $own ? "16px 4px 16px 16px" : "4px 16px 16px 16px"};
  background: ${({ $own }) => $own ? "#0c3273" : "white"};
  color: ${({ $own }) => $own ? "white" : "#30343d"};
  font-size: 16px;
  line-height: 26px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  @media (max-width: 799px) { font-size: 14px; line-height: 24px; }
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
export const ConversationStart = styled.p`margin: 0 0 24px; color: #747781; font-size: 12px; text-align: center;`;
export const Composer = styled.form`
  padding: 20px 24px 16px;
  border-top: 1px solid #e2e2e4;
  background: white;
  @media (max-width: 799px) { padding: 16px; }
`;
export const ComposerLabel = styled.label`display: block; margin-bottom: 8px; color: #444650; font-size: 12px; font-weight: 600;`;
export const ComposerField = styled.div`
  display: flex;
  align-items: flex-end;
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
  min-height: 64px;
  max-height: 160px;
  padding: 12px;
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
export const SendButton = styled.button`${primary} flex-shrink: 0; margin: 4px; padding: 10px 14px;`;
export const EmptyState = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  padding: ${({ $compact }) => $compact ? "32px 20px" : "48px 24px"};
  text-align: center;
  h2, h3 { margin: 20px 0 0; color: #001d4f; font-size: ${({ $compact }) => $compact ? "16px" : "20px"}; line-height: 28px; }
  p { max-width: 320px; margin: 10px 0 24px; color: #747781; font-size: 14px; line-height: 24px; word-break: keep-all; white-space: pre-line; }
`;
export const EmptyIcon = styled.div`display: grid; place-items: center; width: 64px; height: 64px; border-radius: 50%; background: #e7f8f6; color: #006a65; svg { width: 28px; height: 28px; }`;
export const PrimaryLink = styled(Link)`${primary}`;
export const StatusMessage = styled.p`margin: 12px 0 0; color: #006a65; font-size: 12px; line-height: 20px; &:empty { margin: 0; }`;
export const Dialog = styled.dialog`
  width: min(440px, calc(100vw - 32px));
  max-height: calc(100dvh - 32px);
  margin: auto;
  padding: 28px;
  overflow-y: auto;
  border: 0;
  border-radius: 24px;
  background: white;
  color: #1a1c1c;
  box-shadow: 0 16px 48px rgba(12, 50, 115, 0.15);
  &::backdrop { background: rgba(12, 50, 115, 0.4); }
  ${focus}
  @media (max-width: 480px) { padding: 24px 20px; }
`;
export const DialogHeader = styled.div`display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px;`;
export const DialogTitle = styled.h2`margin: 0; color: #001d4f; font-size: 20px; font-weight: 600; line-height: 28px;`;
export const DialogDescription = styled.p`margin: 0; color: #5e616b; font-size: 14px; line-height: 24px; word-break: keep-all;`;
export const ProfileSummary = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 24px 0;
  > div { min-width: 0; }
  strong { display: block; color: #001d4f; font-size: 20px; overflow-wrap: anywhere; }
  p { margin: 6px 0; color: #747781; font-size: 13px; }
`;
export const ProfileDetails = styled.dl`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px 20px;
  margin: 24px 0;
  padding: 20px;
  border-radius: 16px;
  background: #f7f8fa;
  dt { color: #747781; font-size: 12px; }
  dd { margin: 0; color: #30343d; font-size: 13px; overflow-wrap: anywhere; }
`;
export const ProfileTags = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  span { padding: 5px 10px; border-radius: 999px; background: #e7f8f6; color: #006a65; font-size: 12px; }
`;
export const DialogActions = styled.div`display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 12px; margin-top: 24px;`;
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
