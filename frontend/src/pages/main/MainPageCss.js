import styled, { css } from "styled-components";

const colors = {
  warm: "#faf9f6",
  white: "#ffffff",
  navy: "#0c3273",
  navyDark: "#001d4f",
  navySoft: "#edf2ff",
  mint: "#00a79f",
  mintDark: "#006a65",
  mintSoft: "#e3f8f5",
  blue: "#3478f6",
  text: "#1a1c1c",
  textSoft: "#444650",
  muted: "#747781",
  border: "#e2e2e4",
  chip: "#f6f6f6",
  goldSoft: "#fff3d6",
  gold: "#9a6500",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.22);
    outline-offset: 2px;
  }
`;

export const Page = styled.main`
  min-height: 100vh;
  padding: 40px 20px 96px;
  color: ${colors.text};
  background: ${colors.warm};
  font-family:
    "Plus Jakarta Sans",
    "Pretendard",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;

  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }

  button,
  input,
  select {
    font: inherit;
  }

  button,
  a,
  select {
    -webkit-tap-highlight-color: transparent;
  }

  @media (max-width: 640px) {
    padding: 24px 16px 64px;
  }
`;

export const Container = styled.div`
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
`;

export const Section = styled.section`
  scroll-margin-top: 96px;
  margin-top: ${({ $first }) => ($first ? "0" : "64px")};

  @media (max-width: 640px) {
    scroll-margin-top: 128px;
    margin-top: ${({ $first }) => ($first ? "0" : "48px")};
  }
`;

export const SectionHeader = styled.div`
  display: flex;
  gap: 24px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 12px;

  @media (max-width: 760px) {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }
`;

export const SectionKicker = styled.p`
  margin: 0 0 7px;
  color: ${colors.mintDark};
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
  letter-spacing: 0.08em;
`;

export const SectionTitle = styled.h2`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.02em;

  @media (max-width: 640px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const SectionDescription = styled.p`
  margin: 7px 0 0;
  color: ${colors.muted};
  font-size: 14px;
  line-height: 21px;
`;

export const MoreLink = styled.a`
  ${focusRing};
  display: inline-flex;
  flex: 0 0 auto;
  gap: 5px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 14px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  color: ${colors.navy};
  background: ${colors.white};
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
  text-decoration: none;
  transition:
    border-color 160ms ease,
    color 160ms ease,
    background-color 160ms ease;

  @media (hover: hover) {
    &:hover {
      border-color: rgba(0, 167, 159, 0.42);
      color: ${colors.mintDark};
      background: ${colors.mintSoft};
    }
  }
`;

export const GuestRecommendation = styled.div`
  display: flex;
  gap: 28px;
  align-items: center;
  justify-content: center;
  padding: 24px;

  @media (max-width: 640px) {
    flex-direction: column;
    gap: 12px;
    padding: 20px 16px;
    text-align: center;
  }
`;

export const GuestCharacterFrame = styled.span`
  position: relative;
  display: block;
  flex: 0 0 auto;
  width: 144px;
  aspect-ratio: 5 / 7;
  overflow: hidden;

  @media (max-width: 640px) {
    width: 116px;
  }
`;

// Show only the character: crop the 420×595 source to (90, 100, 250, 350).
export const GuestCharacter = styled.img`
  position: absolute;
  top: -28.5714285714%;
  left: -36%;
  display: block;
  width: 168%;
  max-width: none;
  height: auto;
`;

export const GuestRecommendationContent = styled.div`
  min-width: 0;

  p {
    margin: 0 0 16px;
    color: ${colors.textSoft};
    font-size: 15px;
    font-weight: 600;
    line-height: 24px;
    word-break: keep-all;
    overflow-wrap: anywhere;
  }
`;

export const ProfileGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 680px) {
    grid-template-columns: 1fr;
  }
`;

export const FilterList = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-start;
  margin-bottom: 0;
`;

export const FilterButton = styled.button`
  ${focusRing};
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid ${({ $active }) => ($active ? colors.navy : colors.border)};
  border-radius: 999px;
  color: ${({ $active }) => ($active ? colors.white : colors.textSoft)};
  background: ${({ $active }) => ($active ? colors.navy : colors.white)};
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    color 160ms ease,
    background-color 160ms ease;
`;

export const CommunityCard = styled.div`
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;

export const CompactEmpty = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  min-height: 116px;
  padding: 22px 24px;
  color: ${colors.muted};

  > svg {
    color: ${colors.blue};
  }

  h3 {
    margin: 0;
    color: ${colors.navyDark};
    font-size: 14px;
    line-height: 20px;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 18px;
  }

  @media (max-width: 640px) {
    grid-template-columns: auto minmax(0, 1fr);

    > button {
      grid-column: 1 / -1;
    }
  }
`;

export const GhostButton = styled.button`
  ${focusRing};
  min-height: 40px;
  padding: 0 13px;
  border: 1px solid ${colors.border};
  border-radius: 10px;
  color: ${colors.navy};
  background: ${colors.white};
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
`;

export const PostList = styled.ol`
  margin: 0;
  padding: 0;
  list-style: none;
`;

export const PostItem = styled.li`
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid ${colors.border};
  transition: background-color 160ms ease;

  &:last-child {
    border-bottom: 0;
  }

  @media (hover: hover) {
    &:hover {
      background: #fbfbfc;
    }
  }

  @media (max-width: 640px) {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 11px;
    padding: 18px 16px;
  }
`;

export const PostRank = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: ${colors.navy};
  background: ${colors.navySoft};
  font-size: 14px;
  font-weight: 800;
  line-height: 20px;

  @media (max-width: 640px) {
    width: 34px;
    height: 34px;
    border-radius: 10px;
  }
`;

export const PostContent = styled.div`
  min-width: 0;
`;

export const PostMetaTop = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;

  > span:last-child {
    color: ${colors.muted};
    font-size: 12px;
    line-height: 16px;
  }
`;

const categoryTone = {
  자유: css`
    color: #244485;
    background: #edf2ff;
  `,
  나눔: css`
    color: ${colors.mintDark};
    background: ${colors.mintSoft};
  `,
  택배: css`
    color: #76510a;
    background: ${colors.goldSoft};
  `,
  분실: css`
    color: ${colors.gold};
    background: ${colors.goldSoft};
  `,
  민원: css`
    color: #8b3a4c;
    background: #fff0f3;
  `,
};

export const CategoryBadge = styled.span`
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
  letter-spacing: 0.02em;
  ${({ $type }) => categoryTone[$type] || categoryTone.자유};
`;

export const PostTitle = styled.h3`
  display: block;
  width: 100%;
  margin: 7px 0 0;
  padding: 0;
  overflow: hidden;
  color: ${colors.text};
  font-size: 15px;
  font-weight: 700;
  line-height: 21px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const PostExcerpt = styled.p`
  margin: 5px 0 0;
  overflow: hidden;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;

  @media (max-width: 640px) {
    display: none;
  }
`;

export const PostAuthor = styled.span`
  display: block;
  margin-top: 6px;
  color: ${colors.textSoft};
  font-size: 12px;
  line-height: 17px;
`;

export const PostStats = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
  color: ${colors.muted};

  span {
    display: inline-flex;
    gap: 4px;
    align-items: center;
    font-size: 12px;
    line-height: 17px;
  }

  @media (max-width: 640px) {
    grid-column: 2;
    margin-top: -2px;
  }
`;

export const UtilityGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  align-items: start;

  @media (min-width: 981px) {
    row-gap: 0;

    > div {
      display: grid;
      grid-template-rows: subgrid;
      grid-row: span 2;
      align-self: stretch;
      align-items: start;
    }
  }

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`;

export const ToolCard = styled.article`
  min-width: 0;
  scroll-margin-top: 96px;
  padding: 24px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 640px) {
    padding: 20px 16px;
  }
`;

export const ToolHeader = styled.header`
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  margin-bottom: 22px;

  @media (max-width: 520px) {
    display: flex;
    justify-content: center;
  }
`;

export const OutsideToolTitle = styled.h3`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.02em;

  @media (max-width: 640px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const MonthControls = styled.div`
  display: grid;
  grid-template-columns: 44px minmax(92px, 1fr) 44px;
  gap: 6px;
  align-items: center;
  width: 204px;
  margin: 0 auto;

  button {
    ${focusRing};
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 44px;
    height: 44px;
    padding: 0;
    border: 1px solid ${colors.border};
    border-radius: 9px;
    color: ${colors.textSoft};
    background: ${colors.white};
    cursor: pointer;

    &:disabled {
      color: #b7b9bf;
      background: #f7f7f7;
      cursor: not-allowed;
    }
  }

  strong {
    min-width: 0;
    color: ${colors.navyDark};
    font-size: 13px;
    line-height: 18px;
    text-align: center;
  }

  @media (max-width: 520px) {
    grid-column: 1 / -1;
    justify-self: end;
    width: min(204px, 100%);
  }
`;

export const CalendarWeekdays = styled.div`
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin-bottom: 5px;

  span {
    color: ${colors.muted};
    font-size: 12px;
    font-weight: 700;
    line-height: 20px;
    text-align: center;
  }

  span:first-child {
    color: #c25465;
  }

  span:last-child {
    color: ${colors.blue};
  }
`;

export const CalendarGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
`;

export const CalendarBlank = styled.span`
  display: block;
  height: 44px;
`;

export const CalendarDay = styled.button`
  ${focusRing};
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 44px;
  padding: 0;
  border: 1px solid
    ${({ $selected }) => ($selected ? colors.navy : "transparent")};
  border-radius: 10px;
  color: ${({ $selected, $dayTone }) => {
    if ($selected) return colors.white;
    if ($dayTone === "sunday") return "#c25465";
    if ($dayTone === "saturday") return colors.blue;
    return colors.textSoft;
  }};
  background: ${({ $selected }) => ($selected ? colors.navy : "transparent")};
  font-size: 12px;
  font-weight: ${({ $selected }) => ($selected ? 700 : 500)};
  cursor: pointer;
  transition:
    color 150ms ease,
    background-color 150ms ease;

  &::after {
    position: absolute;
    right: 50%;
    bottom: 5px;
    width: 4px;
    height: 4px;
    border-radius: 999px;
    background: ${({ $selected }) => ($selected ? "#83eee7" : colors.mint)};
    opacity: ${({ $hasEvent }) => ($hasEvent ? 1 : 0)};
    transform: translateX(50%);
    content: "";
  }

  @media (hover: hover) {
    &:hover {
      background: ${({ $selected }) =>
    $selected ? colors.navy : colors.navySoft};
    }
  }
`;

export const EventPreview = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
  min-height: 102px;
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid ${colors.border};
`;

export const EventPreviewHeader = styled.div`
  min-width: 64px;

  > div {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 58px;
    height: 58px;
    border-radius: 14px;
    color: ${colors.white};
    background: ${colors.navy};
  }

  span {
    font-size: 12px;
    font-weight: 700;
    line-height: 16px;
  }

  strong {
    font-size: 20px;
    line-height: 24px;
  }

  p {
    margin: 6px 0 0;
    color: ${colors.muted};
    font-size: 12px;
    line-height: 16px;
    text-align: center;
  }
`;

export const EventList = styled.div`
  display: grid;
  gap: 9px;
`;

export const EventItem = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  padding: 10px 11px;
  border-radius: 10px;
  background: #f8f9fb;

  strong,
  span {
    display: block;
  }

  strong {
    overflow: hidden;
    color: ${colors.text};
    font-size: 12px;
    line-height: 18px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span:last-child {
    margin-top: 2px;
    color: ${colors.muted};
    font-size: 12px;
    line-height: 16px;
  }
`;

export const EventMarker = styled.span`
  width: 7px;
  height: 7px;
  margin-top: 5px;
  border-radius: 999px;
  background: ${colors.mint};
  box-shadow: 0 0 0 4px ${colors.mintSoft};
`;

export const EventType = styled.span`
  margin: 0 0 1px;
  color: ${colors.mintDark} !important;
  font-size: 12px !important;
  font-weight: 700;
  line-height: 16px !important;
`;

export const EventEmpty = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
  align-self: stretch;
  padding: 13px;
  border: 1px dashed #cdd0d7;
  border-radius: 10px;
  color: ${colors.muted};
  background: #fbfbfb;
  font-size: 12px;
  line-height: 18px;

  svg {
    flex: 0 0 auto;
    color: ${colors.blue};
  }
`;

export const ScoreForm = styled.form`
  padding: 18px;
  border: 1px solid ${colors.border};
  border-radius: 14px;
  background: #fbfbfc;

  @media (max-width: 640px) {
    padding: 16px;
  }
`;

export const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px 12px;

  @media (max-width: 520px) {
    grid-template-columns: 1fr;
  }
`;

export const FieldGroup = styled.div`
  min-width: 0;
  grid-column: ${({ $wide }) => ($wide ? "1 / -1" : "auto")};

  label {
    display: block;
    margin-bottom: 7px;
    color: ${colors.textSoft};
    font-size: 12px;
    font-weight: 700;
    line-height: 16px;
    letter-spacing: 0.01em;
  }

  small {
    display: block;
    margin-top: 5px;
    color: ${colors.muted};
    font-size: 12px;
    line-height: 16px;
  }
`;

const fieldStyle = css`
  width: 100%;
  height: 44px;
  border: 0;
  outline: 0;
  color: ${colors.text};
  background: transparent;
  font-size: 13px;
  font-weight: 600;

  &::placeholder {
    color: #aaacb2;
  }
`;

export const RegionSelectRow = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
`;

export const SelectWrap = styled.div`
  position: relative;
  height: 46px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  background: ${colors.white};
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;

  &:focus-within {
    border-color: ${colors.mint};
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.1);
  }

  select {
    ${fieldStyle};
    position: relative;
    z-index: 1;
    padding: 0 38px 0 12px;
    appearance: none;
    cursor: pointer;
  }

  svg {
    position: absolute;
    top: 50%;
    right: 12px;
    color: ${colors.muted};
    pointer-events: none;
    transform: translateY(-50%) rotate(90deg);
  }
`;

export const FormActions = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 9px;
  margin-top: 18px;
`;

export const CalculateButton = styled.button`
  ${focusRing};
  display: inline-flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 15px;
  border: 1px solid ${colors.navy};
  border-radius: 12px;
  color: ${colors.white};
  background: ${colors.navy};
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background-color 160ms ease;

  @media (hover: hover) {
    &:hover {
      background: ${colors.navyDark};
    }
  }
`;

export const ResetButton = styled.button`
  ${focusRing};
  display: inline-flex;
  gap: 5px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 13px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  color: ${colors.muted};
  background: ${colors.white};
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
`;

export const ScoreResult = styled.div`
  margin-top: 16px;
  padding: 18px;
  border: 1px solid rgba(0, 167, 159, 0.25);
  border-radius: 14px;
  background: linear-gradient(145deg, #f5fffd, #edf8f7);
`;

export const ScoreSummary = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: center;
`;

export const ScoreRing = styled.div`
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 82px;
  height: 82px;
  border-radius: 50%;
  background: conic-gradient(
    ${colors.mint}
      ${({ $score }) => Math.max(0, Math.min(100, $score)) * 3.6}deg,
    #dbeeed 0
  );
`;

export const ScoreRingInner = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: center;
  width: 66px;
  height: 66px;
  border-radius: 50%;
  color: ${colors.navyDark};
  background: ${colors.white};

  strong {
    align-self: center;
    font-size: 25px;
    line-height: 30px;
    letter-spacing: -0.03em;
  }

  span {
    align-self: center;
    margin: 7px 0 0 2px;
    color: ${colors.muted};
    font-size: 12px;
    font-weight: 700;
  }
`;

export const ResultLabel = styled.span`
  display: block;
  color: ${colors.mintDark};
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
  letter-spacing: 0.04em;
`;

export const ResultTitle = styled.strong`
  display: block;
  margin-top: 3px;
  color: ${colors.navyDark};
  font-size: 15px;
  line-height: 22px;
`;

export const ResultDescription = styled.p`
  margin: 3px 0 0;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 17px;
`;

export const ScoreBreakdown = styled.div`
  display: grid;
  gap: 11px;
  margin-top: 17px;
  padding-top: 15px;
  border-top: 1px solid rgba(0, 106, 101, 0.13);
`;

export const ScoreRow = styled.div`
  > div:first-child {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 5px;
  }

  span,
  strong {
    font-size: 12px;
    line-height: 16px;
  }

  span {
    color: ${colors.textSoft};
  }

  strong {
    color: ${colors.navy};
  }
`;

export const ScoreBar = styled.div`
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(0, 106, 101, 0.1);
`;

export const ScoreBarFill = styled.span`
  display: block;
  width: ${({ $value }) => Math.max(0, Math.min(100, $value))}%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, ${colors.mintDark}, ${colors.mint});
  transition: width 280ms ease;
`;

export const ScoreEmpty = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
  min-height: 94px;
  margin-top: 16px;
  padding: 17px;
  border: 1px dashed #cbd0d5;
  border-radius: 14px;
  background: #fdfdfd;

  h4 {
    margin: 0;
    color: ${colors.navyDark};
    font-size: 13px;
    line-height: 19px;
  }

  p {
    margin: 3px 0 0;
    color: ${colors.muted};
    font-size: 12px;
    line-height: 17px;
  }
`;

export const EmptyCalculatorIcon = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  color: ${colors.mint};
  background: ${colors.mintSoft};
`;

export const Disclaimer = styled.p`
  margin: 11px 2px 0;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 18px;
`;
