import styled, { css } from "styled-components";
import { Link } from "react-router-dom";

const c = {
  navy: "#0c3273", deep: "#001d4f", mint: "#006a65", mintSoft: "#e7f8f6",
  blueSoft: "#eef3fb", text: "#1a1c1c", muted: "#747781", border: "#e2e2e4",
  warm: "#faf9f6", white: "#ffffff", soft: "#f3f3f3", error: "#ba1a1a",
};
const surface = css`
  background: ${c.white};
  border: 1px solid ${c.border};
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;
const control = css`
  min-height: 44px;
  border: 1px solid ${c.border};
  border-radius: 12px;
  padding: 10px 12px;
  color: ${c.text};
  background: ${c.white};
  font: inherit;
  font-size: 14px;
  &:focus { outline: none; border-color: #00a79f; box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.12); }
`;

export const Page = styled.main`
  padding: 40px 24px 80px;
  color: ${c.text};
  background: ${c.warm};
  font-size: 14px;
  line-height: 1.6;
  & *, & *::before, & *::after { box-sizing: border-box; }
  h1, h2, h3, p { margin: 0; }
  button, input, select { font-family: inherit; }
  button, a, input, select, [tabindex] {
    &:focus-visible { outline: 3px solid #00a79f; outline-offset: 3px; }
  }
  svg { flex-shrink: 0; vertical-align: middle; }
  @media (max-width: 640px) { padding: 24px 16px 48px; }
  @media (prefers-reduced-motion: reduce) { * { transition: none !important; } }
`;
export const Layout = styled.div`
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  align-items: start;
  gap: 24px;
  @media (max-width: 960px) { grid-template-columns: minmax(0, 1fr); }
`;
export const Sidebar = styled.aside`
  ${surface}
  padding: 24px 12px 16px;
`;
export const Brand = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 12px 24px;
  color: ${c.navy};
  strong { display: block; font-size: 18px; }
  span { display: block; color: ${c.muted}; font-size: 12px; letter-spacing: 0.04em; }
`;
export const Navigation = styled.nav`
  display: grid;
  gap: 8px;
  @media (max-width: 960px) { display: flex; flex-wrap: wrap; }
`;
export const NavLink = styled(Link)`
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-radius: 12px;
  color: ${({ $active }) => $active ? c.white : "#444650"};
  background: ${({ $active }) => $active ? c.navy : "transparent"};
  font-weight: 600;
  text-decoration: none;
  &:hover { background: ${({ $active }) => $active ? c.deep : c.soft}; }
`;
export const SideNote = styled.p`
  margin: 24px 12px 0 !important;
  padding-top: 16px;
  border-top: 1px solid ${c.border};
  color: ${c.muted};
  font-size: 12px;
`;
export const Content = styled.section`
  min-width: 0;
  display: grid;
  gap: 24px;
`;
export const Heading = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  h1 { color: ${c.deep}; font-size: 28px; line-height: 36px; letter-spacing: -0.01em; }
  p { margin-top: 8px; color: ${c.muted}; }
  @media (max-width: 640px) { h1 { font-size: 24px; line-height: 32px; } }
`;
export const Eyebrow = styled.div`
  margin-bottom: 8px;
  color: ${c.mint};
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
`;
export const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  color: ${({ $tone }) => ({ success: c.mint, danger: c.error, info: c.navy, warning: "#84500a" }[$tone] || "#444650")};
  background: ${({ $tone }) => ({ success: c.mintSoft, danger: "#fff0ee", info: c.blueSoft, warning: "#fff4df" }[$tone] || c.soft)};
`;
export const Button = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 10px 16px;
  border: 1px solid ${({ $variant }) => $variant === "primary" ? c.navy : $variant === "danger" ? "#f1c5c1" : c.border};
  border-radius: 12px;
  color: ${({ $variant }) => $variant === "primary" ? c.white : $variant === "danger" ? c.error : c.navy};
  background: ${({ $variant }) => $variant === "primary" ? c.navy : c.white};
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  &:hover:not(:disabled) { box-shadow: 0 4px 12px rgba(12, 50, 115, 0.1); }
  &:disabled { color: ${c.muted}; background: ${c.soft}; border-color: ${c.border}; cursor: not-allowed; }
`;
export const TextButton = styled.button`
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: ${c.navy};
  font-size: inherit;
  font-weight: 600;
  line-height: inherit;
  text-align: left;
  cursor: pointer;
  &:hover { text-decoration: underline; }
`;
export const TextLink = styled(Link)`
  color: ${c.navy};
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  &:hover { text-decoration: underline; }
`;
export const Notice = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 12px;
  background: ${c.blueSoft};
  color: ${c.navy};
  p { flex: 1; min-width: 160px; }
`;
export const Stats = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  @media (max-width: 1100px) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  @media (max-width: 420px) { gap: 8px; }
`;
export const StatLink = styled(Link)`
  ${surface}
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 20px;
  text-decoration: none;
  color: ${c.navy};
  transition: box-shadow 150ms, transform 150ms;
  span { color: #444650; font-size: 13px; }
  strong { font-size: 28px; line-height: 36px; }
  &:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(12, 50, 115, 0.08); }
  @media (max-width: 420px) { padding: 16px; }
`;
export const Panel = styled.section`
  ${surface}
  min-width: 0;
  overflow: hidden;
`;
export const PanelHeading = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  h2 { font-size: 18px; font-weight: 600; }
  p { color: ${c.muted}; font-size: 13px; }
  @media (max-width: 640px) { padding: 16px; }
`;
export const RecentGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  @media (max-width: 680px) { grid-template-columns: minmax(0, 1fr); }
`;
export const RecentList = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0 24px 8px;
  li { padding: 16px 0; border-top: 1px solid ${c.border}; }
`;
export const Row = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  > div { min-width: 0; }
`;
export const Meta = styled.p`
  color: ${c.muted};
  font-size: 12px;
  margin-top: 4px !important;
`;
export const Avatar = styled.img`
  width: ${({ $large }) => $large ? "72px" : "36px"};
  height: ${({ $large }) => $large ? "72px" : "36px"};
  flex-shrink: 0;
  border-radius: 12px;
  object-fit: cover;
  background: ${c.blueSoft};
`;
export const Toolbar = styled.form`
  ${surface}
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 12px;
  padding: 20px;
`;
export const Field = styled.label`
  display: grid;
  gap: 8px;
  min-width: 130px;
  flex: ${({ $grow }) => $grow ? "1 1 220px" : "0 1 auto"};
  color: #444650;
  font-size: 12px;
  font-weight: 600;
  @media (max-width: 640px) { flex: 1 1 140px; }
`;
export const Input = styled.input`${control} width: 100%; min-width: 0;`;
export const Select = styled.select`${control} width: 100%;`;
export const Checkbox = styled.label`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  cursor: pointer;
  input { accent-color: ${c.navy}; width: 16px; height: 16px; }
`;
export const Chips = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
`;
export const Tabs = styled.div`
  display: flex;
  gap: 8px;
  border-bottom: 1px solid ${c.border};
  padding-bottom: 12px;
`;
export const TabLink = styled(Link)`
  padding: 10px 18px;
  border-radius: 12px;
  color: ${({ $active }) => $active ? c.white : c.muted};
  background: ${({ $active }) => $active ? c.navy : "transparent"};
  font-weight: 600;
  text-decoration: none;
`;
export const TableScroll = styled.div`
  overflow-x: auto;
`;
export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
  th { padding: 12px 16px; background: #f7f8fa; color: #444650; font-size: 12px; font-weight: 600; white-space: nowrap; }
  td { padding: 16px; border-top: 1px solid ${c.border}; vertical-align: middle; white-space: nowrap; }
  tbody tr:hover { background: #fafbfd; }
`;
export const CellText = styled.div`
  min-width: 160px;
  max-width: 280px;
  white-space: normal;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
`;
export const Empty = styled.div`
  padding: 48px 24px;
  text-align: center;
  color: ${c.muted};
  h3 { margin: 12px 0 8px; font-size: 16px; color: #444650; }
  button { margin-top: 16px; }
`;
export const Pagination = styled.nav`
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 20px 16px;
  border-top: 1px solid ${c.border};
`;
export const CrawlGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  @media (max-width: 640px) { grid-template-columns: minmax(0, 1fr); }
`;
export const Details = styled.dl`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 24px;
  margin: 0;
  padding: 24px;
  dt { color: ${c.muted}; font-size: 12px; margin-bottom: 4px; }
  dd { margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
  @media (max-width: 480px) { grid-template-columns: minmax(0, 1fr); padding: 16px; }
`;
export const DetailItem = styled.div`
  ${({ $wide }) => $wide && css`grid-column: 1 / -1;`}
`;
export const CrawlHistory = styled.div`
  padding: 16px 24px;
  border-top: 1px solid ${c.border};
  color: ${c.muted};
  font-size: 12px;
`;
export const Dialog = styled.dialog`
  width: min(680px, calc(100% - 32px));
  max-height: calc(100dvh - 48px);
  padding: 0;
  border: 1px solid ${c.border};
  border-radius: 16px;
  color: ${c.text};
  background: ${c.white};
  box-shadow: 0 16px 48px rgba(12, 50, 115, 0.15);
  overflow-y: auto;
  &::backdrop { background: rgba(0, 29, 79, 0.4); }
`;
export const DialogHeading = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid ${c.border};
  h2 { font-size: 20px; }
`;
export const DialogBody = styled.div`
  padding: 24px;
  p { white-space: pre-wrap; overflow-wrap: anywhere; }
  h3 { margin-bottom: 12px; font-size: 18px; }
`;
export const Actions = styled.div`
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  padding: 20px 24px;
  border-top: 1px solid ${c.border};
`;
export const Activity = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 0 24px 24px;
  div { padding: 16px 8px; border-radius: 12px; background: ${c.blueSoft}; text-align: center; }
  strong { display: block; font-size: 22px; color: ${c.navy}; }
  span { font-size: 12px; color: #444650; }
`;
