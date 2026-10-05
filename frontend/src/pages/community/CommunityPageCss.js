import styled, { css } from "styled-components";
import { Link } from "react-router-dom";

const focus = css`
  &:focus-visible { outline: 3px solid rgba(0, 167, 159, 0.35); outline-offset: 3px; }
`;
const button = css`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 36px;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  text-decoration: none;
  cursor: pointer;
  transition: background 160ms, box-shadow 160ms;
  ${focus}
`;
const primary = css`
  ${button}
  color: white;
  background: #0c3273;
  &:hover { background: #001d4f; box-shadow: 0 4px 12px rgba(12, 50, 115, 0.12); }
`;

export const Page = styled.main`
  min-height: 100vh;
  padding: ${({ $compactTop }) => $compactTop ? "8px 20px 80px" : "24px 20px 80px"};
  color: #1a1c1c;
  background: #faf9f6;
  font-family: "Plus Jakarta Sans", Pretendard, "Noto Sans KR", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  line-height: 1.5;
  &, *, *::before, *::after { box-sizing: border-box; }
  button, input, textarea, select { font-family: inherit; }
  button, a, input, textarea, select { -webkit-tap-highlight-color: transparent; }
  svg { flex-shrink: 0; }
  @media (max-width: 700px) { padding: ${({ $compactTop }) => $compactTop ? "8px 16px 56px" : "16px 16px 56px"}; }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; }
  }
`;
export const Content = styled.div`
  width: min(100%, 1180px);
  margin: 0 auto;
  min-width: 0;
`;
export const PrimaryButton = styled.button`${primary} flex-shrink: 0;`;
export const PrimaryLink = styled(Link)`${primary}`;
export const SecondaryButton = styled.button`
  ${button}
  border: 1.5px solid #00a79f;
  background: white;
  color: #006a65;
  &:hover { background: #e7f8f6; }
`;
export const Layout = styled.div`
  display: grid;
  grid-template-columns: 196px minmax(0, 1fr);
  align-items: start;
  gap: 24px;
  @media (max-width: 900px) { grid-template-columns: minmax(0, 1fr); }
`;
export const Sidebar = styled.aside`
  display: grid;
  gap: 16px;
  min-width: 0;
  @media (min-width: 601px) and (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr) 220px;
    align-items: start;
  }
`;
export const Navigation = styled.nav`
  padding: 16px;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: white;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.03);
  @media (max-width: 900px) { padding: 12px; }
`;
export const NavigationTitle = styled.h2`
  margin: 0 10px 8px;
  color: #747781;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  ${({ $separated }) => $separated && css`margin-top: 12px; padding-top: 12px; border-top: 1px solid #eeeeee;`}
  @media (max-width: 900px) { display: none; }
`;
export const CategoryLinks = styled.div`
  display: grid;
  gap: 4px;
  @media (max-width: 900px) { display: flex; flex-wrap: wrap; gap: 4px; }
`;
export const ActivityLinks = styled.div`
  display: grid;
  gap: 4px;
  @media (max-width: 900px) { display: flex; flex-wrap: wrap; border-top: 1px solid #eeeeee; margin-top: 12px; padding-top: 8px; }
`;
export const NavLink = styled(Link)`
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 8px 10px;
  border-radius: 12px;
  background: ${({ $active }) => $active ? "#e7f8f6" : "transparent"};
  color: ${({ $active }) => $active ? "#006a65" : "#444650"};
  font-size: 13px;
  font-weight: ${({ $active }) => $active ? 700 : 500};
  text-decoration: none;
  &:hover { background: ${({ $active }) => $active ? "#dbf3ef" : "#f3f3f3"}; }
  ${focus}
  @media (max-width: 600px) { min-height: 36px; padding: 8px; gap: 6px; font-size: 13px; > svg { width: 16px; } }
`;
export const KindnessNote = styled.div`
  margin-top: 0;
  padding: 14px;
  border-radius: 16px;
  background: #edf4ef;
  color: #426855;
  > svg { width: 20px; height: 20px; margin-bottom: 6px; }
  strong { display: block; font-size: 12px; }
  p { margin: 6px 0 0; color: #527060; font-size: 11px; line-height: 19px; }
  @media (max-width: 600px) {
    padding: 12px 14px;
    > svg { float: left; margin: 0 10px 0 0; }
    p { margin-left: 30px; }
    br { display: none; }
  }
`;
export const Board = styled.section`
  min-width: 0;
  letter-spacing: 0.015em;
  overflow: hidden;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: white;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;
export const BoardHeading = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 24px 12px;
  ${({ $separated }) => $separated && css`border-bottom: 1px solid #e2e2e4;`}
  > button { width: 96px; height: 36px; padding: 6px 8px; margin-left: auto; }
  h2 { margin: 0; color: #001d4f; font-size: 16px; font-weight: 600; line-height: 26px; letter-spacing: 0.015em; }
  p { margin: 6px 0 0; color: #747781; font-size: 12px; word-break: keep-all; }
  @media (max-width: 600px) { padding: 14px 16px 10px; }
`;
export const Controls = styled.div`
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px 12px;
  border-bottom: 1px solid #e2e2e4;
  @media (max-width: 600px) { align-items: stretch; flex-direction: column; gap: 8px; padding: 0 16px 12px; }
`;
export const SearchForm = styled.form`display: flex; flex: 1; min-width: 0; gap: 8px;`;
export const SearchField = styled.div`
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
  padding-left: 12px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  color: #747781;
  background: #fafafa;
  &:focus-within { border-color: #00a79f; box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.12); }
`;
export const SearchInput = styled.input`
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 10px;
  border: 0;
  outline: none;
  border-radius: 12px;
  color: #1a1c1c;
  background: transparent;
  && { font-size: 14px; }
  &::placeholder { color: #747781; }
  &::-webkit-search-cancel-button { -webkit-appearance: none; display: none; }
`;
export const SearchClearButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin-right: 4px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  color: #747781;
  background: transparent;
  cursor: pointer;
  &:hover { color: #0c3273; background: #eef3fa; }
  ${focus}
`;
export const SearchButton = styled.button`
  ${button}
  padding: 8px 12px;
  flex-shrink: 0;
  background: #eef3fa;
  color: #0c3273;
  &:hover { background: #e2eafb; }
`;
export const SortLabel = styled.label`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  > span { display: none; color: #747781; font-size: 12px; }
  @media (max-width: 600px) { > span { display: inline; } }
`;
export const SortSelect = styled.select`
  appearance: none;
  width: 96px;
  height: 36px;
  min-height: 36px;
  padding: 6px 16px 6px 8px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: white;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='none' stroke='%23444650' stroke-width='1.5' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m4 6 4 4 4-4'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  background-size: 12px 12px;
  text-align: center;
  text-align-last: center;
  color: #444650;
  cursor: pointer;
  && { font-size: 13px; }
  ${focus}
`;
export const SearchSummary = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 12px 28px;
  background: #f6f9fc;
  color: #444650;
  font-size: 12px;
  overflow-wrap: anywhere;
  @media (max-width: 600px) { padding: 12px 16px; }
`;
export const PostList = styled.ul`margin: 0; padding: 0; list-style: none;`;
export const PostRow = styled.li`
  position: relative;
  padding: 12px 24px;
  border-bottom: 1px solid #eeeeee;
  transition: background 150ms;
  &:hover { background: #fcfcfb; }
  &:last-child { border-bottom: 0; }
  @media (max-width: 600px) { padding: 10px 16px; }
`;
export const PostHeading = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
`;
export const CategoryBadge = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 16px;
  font-weight: 600;
  color: ${({ $category }) => ({ 나눔게시판: "#006a65", 택배게시판: "#875d22", 민원게시판: "#705390" }[$category] || "#244485")};
  background: ${({ $category }) => ({ 나눔게시판: "#e7f8f6", 택배게시판: "#fbf2df", 민원게시판: "#f2edf8" }[$category] || "#edf2fc")};
`;
export const DormitoryBadge = styled.span`
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  color: #5e616b;
  background: #f3f3f3;
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
`;
export const OwnBadge = styled.span`color: #747781; font-size: 11px;`;
export const PostTitleLink = styled(Link)`
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: fit-content;
  max-width: 100%;
  color: #1a1c1c;
  text-decoration: none;
  &::after { content: ""; position: absolute; inset: 0; }
  h3 { min-width: 0; margin: 0; font-size: 14px; font-weight: 600; line-height: 23px; word-break: keep-all; overflow-wrap: anywhere; }
  &:hover h3 { color: #0c3273; }
  ${focus}
`;
export const ImageLabel = styled.span`display: inline-flex; align-items: center; height: 23px; flex-shrink: 0; color: #747781; font-size: 11px;`;
export const Excerpt = styled.p`
  margin: 8px 0 10px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #747781;
  font-size: 12px;
  line-height: 20px;
`;
export const PostBottom = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  color: #747781;
  font-size: 11px;
  line-height: 20px;
`;
export const Metadata = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 4px 12px;
  align-items: center;
  text-align: left;
  time { font-variant-numeric: tabular-nums; }
`;
export const Author = styled.span`display: flex; gap: 4px; align-items: center; color: #444650; overflow-wrap: anywhere;`;
export const Stats = styled.div`
  display: flex;
  margin-left: auto;
  gap: 16px;
  span { display: inline-flex; align-items: center; gap: 5px; }
`;
export const CommentTop = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  time { color: #747781; font-size: 11px; }
`;
export const MyComment = styled.p`margin: 16px 0; font-size: 14px; white-space: pre-wrap; overflow-wrap: anywhere;`;
export const ActivityContentRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
  margin: ${({ $comment }) => $comment ? "16px 0" : "0 0 10px"};
`;
export const ActivityContent = styled.div`
  flex: 1;
  min-width: 0;
  > ${Excerpt} { margin-bottom: 0; }
  > ${MyComment} { margin: 0; }
`;
export const ActivityControls = styled.div`
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  min-width: 0;
  ${({ $expanded }) => $expanded && css`flex-basis: 100%;`}
`;
export const ActivityButtons = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  small { margin-right: auto; color: #747781; font-size: 12px; }
`;
export const ActivityButton = styled.button`
  ${button}
  gap: 5px;
  padding: 6px 10px;
  color: ${({ $danger }) => $danger ? "#b42318" : "#0c3273"};
  background: ${({ $danger }) => $danger ? "#fff1f0" : "#eef3fa"};
  &:hover:not(:disabled) { filter: brightness(0.97); }
  &:disabled { opacity: 0.55; cursor: not-allowed; }
`;
export const ActivityForm = styled.form`display: grid; gap: 8px;`;
export const ActivityTextarea = styled.textarea`
  width: 100%;
  min-height: 96px;
  resize: vertical;
  padding: 12px;
  border: 1px solid #c8ced8;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  ${focus}
`;
export const DeleteQuestion = styled.p`margin: 0 0 8px; font-size: 13px; color: #444650;`;
export const ActivityError = styled.p`margin: 8px 0 0; font-size: 13px; color: #b42318;`;
export const OriginalPostLink = styled(Link)`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #f6f7f9;
  color: #0c3273;
  font-size: 12px;
  text-decoration: none;
  span { min-width: 0; flex: 1; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
  &:hover { background: #edf2fc; }
  ${focus}
`;
export const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 24px;
  text-align: center;
  h1, h3 { margin: 20px 0 0; color: #001d4f; font-size: 16px; line-height: 28px; }
  p { max-width: 420px; margin: 12px 0 24px; color: #747781; font-size: 13px; word-break: keep-all; }
`;
export const EmptyIcon = styled.div`
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 20px;
  background: #e7f8f6;
  color: #006a65;
  svg { width: 28px; height: 28px; }
`;

export const AuthorImage = styled.img`width: 26px; height: 26px; border-radius: 8px; object-fit: cover; flex-shrink: 0;`;
