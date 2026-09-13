import styled, { css } from "styled-components";

const colors = {
  ivory: "#faf9f6", white: "#ffffff", navy: "#0c3273", navyDark: "#001d4f",
  mint: "#00a79f", mintDark: "#006a65", mintSoft: "#e7f8f6",
  text: "#1a1c1c", secondary: "#444650", muted: "#747781", border: "#e2e2e4",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid ${colors.mint};
    outline-offset: 3px;
  }
`;

const buttonBase = css`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  border: 0;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  text-decoration: none;
  cursor: pointer;
  ${focusRing}
`;

export const Dialog = styled.dialog`
  width: min(960px, calc(100% - 48px));
  max-width: 1200px;
  max-height: calc(100vh - 64px);
  max-height: calc(100dvh - 64px);
  margin: auto;
  padding: 0;
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 24px;
  color: ${colors.text};
  background: ${colors.white};
  box-shadow: 0 24px 64px rgba(12, 50, 115, 0.16);
  font-family: "Plus Jakarta Sans", Pretendard, "Noto Sans KR", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;

  &[open] { display: flex; flex-direction: column; }
  &::backdrop { background: rgba(12, 50, 115, 0.4); }
  &, *, *::before, *::after { box-sizing: border-box; }
  body:has(&[open]) { overflow: hidden; }

  @media (max-width: 600px) {
    width: calc(100% - 32px);
    max-height: calc(100vh - 32px);
    max-height: calc(100dvh - 32px);
    border-radius: 16px;
  }
`;

export const DialogHeader = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 68px;
  padding: 8px 24px;
  border-bottom: 1px solid ${colors.border};
  @media (max-width: 600px) { min-height: 60px; padding: 8px 16px; }
`;

export const BackButton = styled.button`
  ${buttonBase}
  padding: 4px 0;
  color: ${colors.secondary};
  background: transparent;
  &:hover { color: ${colors.navy}; }
`;

export const DialogLabel = styled.span`
  margin-right: 40px;
  color: ${colors.navyDark};
  font-size: 14px;
  font-weight: 600;
  @media (max-width: 600px) { margin-right: 0; }
`;

export const IconButton = styled.button`
  ${buttonBase}
  width: 44px;
  padding: 0;
  border-radius: 12px;
  color: ${colors.secondary};
  background: transparent;
  &:hover { background: ${colors.ivory}; }
`;

export const ScrollArea = styled.div`
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
`;

export const ProfileSection = styled.section`
  padding: 32px 40px 24px;
  @media (max-width: 600px) { padding: 24px 16px; }
`;

export const ProfileHero = styled.div`
  position: relative;
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  padding-right: 48px;
  align-items: center;
  gap: 24px;
  @media (max-width: 600px) { grid-template-columns: 72px minmax(0, 1fr); gap: 16px; }
`;

export const Avatar = styled.div`
  display: grid;
  place-items: center;
  width: 104px;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 12px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  img { width: 100%; height: 100%; object-fit: contain; }
  @media (max-width: 600px) { width: 72px; }
`;

export const Identity = styled.div`min-width: 0;`;

export const Eyebrow = styled.p`
  margin: 0 0 8px;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 18px;
  @media (max-width: 600px) { display: none; }
`;

export const NameRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
`;

export const ProfileName = styled.h1`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.01em;
  overflow-wrap: anywhere;
  @media (max-width: 600px) { font-size: 24px; line-height: 32px; }
`;

export const MbtiBadge = styled.span`
  padding: 4px 10px;
  border-radius: 999px;
  color: ${colors.navy};
  background: #eef3ff;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  letter-spacing: 0.04em;
`;

export const AcademicSummary = styled.p`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 8px 0 0;
  color: ${colors.secondary};
  font-size: 14px;
  line-height: 20px;
`;

export const SaveButton = styled.button`
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid ${({ $liked }) => ($liked ? "rgba(255, 107, 138, 0.4)" : colors.border)};
  border-radius: 50%;
  color: ${({ $liked }) => ($liked ? "#ff4d73" : "#ff6b8a")};
  background: ${({ $liked }) => ($liked ? "#fff1f5" : colors.white)};
  box-shadow: none;
  cursor: pointer;
  transition: background 150ms ease, border-color 150ms ease;

  &:focus-visible {
    outline: 3px solid rgba(255, 107, 138, 0.24);
    outline-offset: 2px;
  }
`;

export const BasicInfo = styled.dl`
  display: grid;
  grid-template-columns: 1fr 1.2fr 0.6fr;
  gap: 24px;
  margin: 24px 0;
  padding: 20px 0;
  border-top: 1px solid ${colors.border};
  border-bottom: 1px solid ${colors.border};
  dt { margin-bottom: 8px; color: ${colors.muted}; font-size: 12px; line-height: 16px; }
  dd { margin: 0; font-size: 14px; font-weight: 600; line-height: 22px; overflow-wrap: anywhere; }
  @media (max-width: 600px) {
    grid-template-columns: 1fr;
    gap: 12px;
    > div { display: grid; grid-template-columns: 48px 1fr; gap: 8px; align-items: baseline; }
    dt { margin: 0; }
  }
`;

export const Introduction = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  background: ${colors.ivory};
  > svg { flex-shrink: 0; margin-top: 2px; color: ${colors.mintDark}; }
  p { margin: 6px 0 0; color: ${colors.secondary}; font-size: 16px; line-height: 26px; overflow-wrap: anywhere; }
  @media (max-width: 600px) { p { font-size: 14px; line-height: 22px; } }
`;

export const SmallLabel = styled.span`color: ${colors.muted}; font-size: 12px; line-height: 18px;`;

export const DetailsArea = styled.div`
  padding: 24px 40px;
  border-top: 1px solid ${colors.border};
  background: ${colors.ivory};
  @media (max-width: 600px) { padding: 24px 16px; }
`;

export const MatchCard = styled.section`
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 24px;
  border: 1px solid #cce8e3;
  border-radius: 16px;
  background: ${colors.mintSoft};
  @media (max-width: 600px) { flex-direction: column; align-items: flex-start; gap: 16px; padding: 20px; }
`;

export const ScoreRing = styled.div`
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: conic-gradient(${colors.mint} ${({ $score }) => $score}%, #cce8e3 0);
  > div { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 88px; height: 88px; border-radius: 50%; background: ${colors.white}; }
  strong { color: ${colors.navyDark}; font-size: 32px; font-weight: 700; line-height: 38px; }
  small { margin-left: 2px; font-size: 14px; font-weight: 500; }
  span { color: ${colors.mintDark}; font-size: 11px; line-height: 18px; }
`;

export const EmptyMatchIcon = styled.div`
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  color: ${colors.mintDark};
  background: ${colors.white};
`;

export const MatchCopy = styled.div`
  flex: 1;
  min-width: 0;
  h2 { margin: 8px 0; color: ${colors.navyDark}; font-size: 20px; font-weight: 600; line-height: 28px; word-break: keep-all; overflow-wrap: anywhere; }
  p { margin: 0; color: ${colors.secondary}; font-size: 14px; line-height: 22px; word-break: keep-all; overflow-wrap: anywhere; }
  @media (max-width: 600px) { width: 100%; }
`;
export const MatchLabel = styled.span`display: flex; gap: 6px; align-items: center; color: ${colors.mintDark}; font-size: 12px; font-weight: 600; line-height: 16px;`;
export const MatchScale = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  color: ${colors.mintDark};
  font-size: 11px;
  line-height: 16px;
  > span { flex-shrink: 0; }
  @media (max-width: 600px) { gap: 8px; }
`;
export const MatchBar = styled.div`
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: #cce8e3;
  &::after { display: block; width: ${({ $score }) => $score}%; height: 100%; border-radius: inherit; background: ${colors.mint}; content: ""; }
`;

export const SurveyCard = styled.section`
  margin-top: 24px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;
export const TabList = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 0 16px;
  border-bottom: 1px solid ${colors.border};
  @media (max-width: 600px) { padding: 0 8px; }
`;
export const Tab = styled.button`
  ${buttonBase}
  position: relative;
  min-width: 0;
  min-height: 60px;
  padding: 12px 4px;
  color: ${({ $active }) => $active ? colors.navy : colors.muted};
  background: transparent;
  &::after { position: absolute; right: 4px; bottom: 0; left: 4px; height: 3px; border-radius: 3px 3px 0 0; background: ${({ $active }) => $active ? colors.mint : "transparent"}; content: ""; }
  &:hover { color: ${colors.navy}; }
  @media (max-width: 600px) { gap: 4px; font-size: 12px; > svg { display: none; } }
`;
export const TabPanel = styled.div`
  padding: 24px;
  ${focusRing}
  @media (max-width: 600px) { padding: 20px 16px; }
`;
export const SectionHeading = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 24px;
  h2 { margin: 0; color: ${colors.navyDark}; font-size: 20px; font-weight: 600; line-height: 28px; }
  p { margin: 6px 0 0; color: ${colors.muted}; font-size: 12px; line-height: 20px; word-break: keep-all; overflow-wrap: anywhere; }
`;
export const SectionIcon = styled.div`
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  color: ${colors.navy};
  background: #eef3ff;
  @media (max-width: 600px) { display: none; }
`;
export const AnswerGrid = styled.dl`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  @media (max-width: 600px) { grid-template-columns: 1fr; }
`;
export const AnswerItem = styled.div`
  padding: 16px;
  border-radius: 12px;
  background: #f7f7f6;
  dt { margin-bottom: 8px; color: ${colors.muted}; font-size: 12px; line-height: 18px; }
`;
export const AnswerValue = styled.dd`
  margin: 0;
  color: ${({ $empty }) => $empty ? colors.muted : colors.secondary};
  font-size: 14px;
  font-weight: ${({ $empty }) => $empty ? 400 : 600};
  line-height: 22px;
  overflow-wrap: anywhere;
`;
export const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 8px;
  text-align: center;
  h3 { margin: 16px 0 8px; color: ${colors.navyDark}; font-size: 18px; line-height: 28px; word-break: keep-all; }
  p { margin: 0; color: ${colors.muted}; font-size: 14px; line-height: 24px; }
`;
export const EmptyIcon = styled.div`
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
`;
export const Actions = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
  padding: 20px 40px;
  border-top: 1px solid ${colors.border};
  background: ${colors.white};
  @media (max-width: 600px) { padding: 16px; > button { width: 100%; } }
`;
export const ChatButton = styled.button`
  ${buttonBase}
  min-height: 48px;
  padding: 12px 24px;
  border-radius: 12px;
  color: ${colors.white};
  background: ${colors.navy};
  &:hover { background: ${colors.navyDark}; }
`;
export const MissingProfile = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 64px 24px;
  overflow-y: auto;
  text-align: center;
  h1 { margin: 8px 0 0; color: ${colors.navyDark}; font-size: 24px; line-height: 32px; }
  p { margin: 0 0 8px; color: ${colors.muted}; font-size: 14px; line-height: 24px; }
`;
