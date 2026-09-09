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
  font-size: 14px;
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
  padding: ${({ $compactTop }) => $compactTop ? "12px" : "20px"} 20px 48px;
  color: #1a1c1c;
  background: #faf9f6;
  font-family: "Plus Jakarta Sans", Pretendard, "Noto Sans KR", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  line-height: 1.5;
  &, *, *::before, *::after { box-sizing: border-box; }
  button, input, textarea, select { font-family: inherit; }
  button, a, input, textarea, select { -webkit-tap-highlight-color: transparent; }
  svg { flex-shrink: 0; }
  @media (max-width: 700px) { padding: 12px 16px 32px; }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; }
  }
`;
export const Content = styled.div`
  width: min(100%, 1080px);
  margin: 0 auto;
  min-width: 0;
`;
export const Breadcrumb = styled.nav`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 32px;
  color: #747781;
  font-size: 13px;
`;
export const TextLink = styled(Link)`
  color: #747781;
  text-decoration: none;
  &:hover { color: #0c3273; text-decoration: underline; }
  ${focus}
`;
export const Intro = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 40px;
  @media (max-width: 600px) { align-items: flex-start; flex-wrap: wrap; gap: 20px; margin-bottom: 28px; }
`;
export const Eyebrow = styled.p`
  margin: 0 0 12px;
  color: #006a65;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
`;
export const Title = styled.h1`
  margin: 0;
  color: #001d4f;
  font-size: 36px;
  font-weight: 700;
  line-height: 44px;
  letter-spacing: -0.02em;
  word-break: keep-all;
  @media (max-width: 700px) { font-size: 24px; line-height: 32px; }
`;
export const Description = styled.p`
  margin: 12px 0 0;
  color: #444650;
  font-size: 16px;
  line-height: 24px;
  word-break: keep-all;
  @media (max-width: 700px) { font-size: 14px; }
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
  gap: 16px;
  @media (max-width: 900px) { grid-template-columns: minmax(0, 1fr); }
`;
export const Sidebar = styled.aside`
  display: grid;
  gap: 12px;
  min-width: 0;
  @media (min-width: 601px) and (max-width: 900px) {
    grid-template-columns: minmax(0, 1fr) 220px;
    align-items: start;
  }
`;
export const Navigation = styled.nav`
  padding: 14px 8px 10px;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: white;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.03);
  @media (max-width: 900px) { padding: 12px; }
`;
export const NavigationTitle = styled.h2`
  margin: 0 10px 8px;
  color: #747781;
  font-size: 12px;
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
  font-size: 14px;
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
  strong { display: block; font-size: 13px; }
  p { margin: 6px 0 0; color: #527060; font-size: 12px; line-height: 19px; }
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
  > button { width: 96px; height: 36px; padding: 6px 8px; margin-left: auto; }
  h2 { margin: 0; color: #001d4f; font-size: 18px; font-weight: 600; line-height: 26px; letter-spacing: 0.015em; }
  p { margin: 6px 0 0; color: #747781; font-size: 13px; word-break: keep-all; }
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
  > span { display: none; color: #747781; font-size: 13px; }
  @media (max-width: 600px) { > span { display: inline; } }
`;
export const SortSelect = styled.select`
  width: 96px;
  height: 36px;
  min-height: 36px;
  padding: 6px 8px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: white;
  color: #444650;
  cursor: pointer;
  && { font-size: 14px; }
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
  font-size: 13px;
  overflow-wrap: anywhere;
  @media (max-width: 600px) { padding: 12px 16px; }
`;
export const ClearButton = styled.button`
  min-height: 36px;
  padding: 4px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #006a65;
  text-decoration: underline;
  cursor: pointer;
  white-space: nowrap;
  && { font-size: 12px; }
  ${focus}
`;
export const PostList = styled.ul`margin: 0; padding: 0; list-style: none;`;
export const PostRow = styled.li`
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
  align-items: baseline;
  gap: 8px;
  width: fit-content;
  max-width: 100%;
  color: #1a1c1c;
  text-decoration: none;
  h3 { min-width: 0; margin: 0; font-size: 15px; font-weight: 600; line-height: 23px; word-break: keep-all; overflow-wrap: anywhere; }
  &:hover h3 { color: #0c3273; text-decoration: underline; text-underline-offset: 4px; }
  ${focus}
`;
export const ImageLabel = styled.span`flex-shrink: 0; color: #747781; font-size: 11px;`;
export const Excerpt = styled.p`
  margin: 8px 0 10px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: #747781;
  font-size: 13px;
  line-height: 20px;
`;
export const PostBottom = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  color: #747781;
  font-size: 12px;
  line-height: 20px;
`;
export const Metadata = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 12px;
  align-items: center;
  margin-left: auto;
  text-align: right;
  time { font-variant-numeric: tabular-nums; }
`;
export const Author = styled.span`display: flex; gap: 4px; align-items: center; color: #444650; overflow-wrap: anywhere;`;
export const Stats = styled.div`
  display: flex;
  gap: 16px;
  span { display: inline-flex; align-items: center; gap: 5px; }
`;
export const CommentTop = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  time { color: #747781; font-size: 12px; }
`;
export const MyComment = styled.p`margin: 16px 0; font-size: 15px; white-space: pre-wrap; overflow-wrap: anywhere;`;
export const OriginalPostLink = styled(Link)`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  background: #f6f7f9;
  color: #0c3273;
  font-size: 13px;
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
  h1, h3 { margin: 20px 0 0; color: #001d4f; font-size: 20px; line-height: 28px; }
  p { max-width: 420px; margin: 12px 0 24px; color: #747781; font-size: 14px; word-break: keep-all; }
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
export const Pagination = styled.nav`
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 24px 16px;
  border-top: 1px solid #eeeeee;
  @media (max-width: 400px) { gap: 2px; padding: 20px 8px; }
`;
export const PageButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  min-height: 40px;
  border: 0;
  border-radius: 10px;
  color: ${({ $active }) => $active ? "white" : "#444650"};
  background: ${({ $active }) => $active ? "#0c3273" : "transparent"};
  cursor: pointer;
  && { font-size: 13px; font-weight: ${({ $active }) => $active ? 700 : 500}; }
  &:hover:not(:disabled) { background: ${({ $active }) => $active ? "#001d4f" : "#f3f3f3"}; }
  &:disabled { opacity: 0.35; cursor: default; }
  ${focus}
  @media (max-width: 400px) { width: 32px; }
`;
export const Feedback = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  padding: 12px 20px;
  border: 1px solid #bce3d9;
  border-radius: 12px;
  background: #e7f8f6;
  color: #006a65;
  font-size: 14px;
`;
