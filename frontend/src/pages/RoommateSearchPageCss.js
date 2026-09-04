import styled, { createGlobalStyle, css } from "styled-components";

const colors = {
  warm: "#faf9f6",
  white: "#ffffff",
  navy: "#0c3273",
  navyDark: "#001d4f",
  navySoft: "#eef3ff",
  mint: "#00a79f",
  mintDark: "#006a65",
  mintSoft: "#e7f8f6",
  blue: "#3478f6",
  text: "#1a1c1c",
  textSoft: "#444650",
  muted: "#747781",
  subtle: "#8e8e93",
  border: "#e2e2e4",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.24);
    outline-offset: 2px;
  }
`;

export const PageReset = createGlobalStyle`
  body {
    margin: 0;
  }
`;

export const Page = styled.main`
  min-height: 100vh;
  padding: 56px 20px 80px;
  color: ${colors.text};
  background: ${colors.warm};
  font-family:
    "Plus Jakarta Sans",
    Pretendard,
    "Noto Sans KR",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;

  &,
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }

  button,
  input {
    font: inherit;
  }

  @media (max-width: 700px) {
    padding: 36px 16px 56px;
  }
`;

export const Content = styled.div`
  width: min(100%, 1200px);
  margin: 0 auto;
`;

export const Intro = styled.header`
  max-width: 760px;
  margin-bottom: 40px;

  @media (max-width: 700px) {
    margin-bottom: 28px;
  }
`;

export const Eyebrow = styled.span`
  display: inline-flex;
  gap: 7px;
  align-items: center;
  min-height: 30px;
  padding: 0 11px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
  letter-spacing: 0.04em;
`;

export const PageTitle = styled.h1`
  margin: 16px 0 0;
  color: ${colors.navyDark};
  font-size: 36px;
  font-weight: 700;
  line-height: 44px;
  letter-spacing: -0.02em;
  word-break: keep-all;

  @media (max-width: 700px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const PageDescription = styled.p`
  max-width: 680px;
  margin: 13px 0 0;
  color: ${colors.textSoft};
  font-size: 16px;
  line-height: 24px;
  word-break: keep-all;

  @media (max-width: 700px) {
    font-size: 14px;
    line-height: 20px;
  }
`;

export const DiscoveryControls = styled.div`
  overflow: visible;
  margin-bottom: 24px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;

export const TabsRow = styled.div`
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
  padding: 0 20px;
  border-bottom: 1px solid ${colors.border};

  @media (max-width: 640px) {
    gap: 10px;
    min-height: 64px;
    padding: 0 14px;
  }

  @media (max-width: 520px) {
    gap: 8px;
    padding: 0 10px;
  }
`;

export const TabList = styled.div`
  display: grid;
  flex: 1;
  align-self: stretch;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  width: 100%;
  min-width: 0;
`;

export const TabsArea = styled.div`
  display: flex;
  flex: 1;
  align-self: stretch;
  align-items: center;
  width: 100%;
  min-width: 0;
`;

export const TabButton = styled.button`
  ${focusRing};
  position: relative;
  display: inline-flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-width: 0;
  min-height: 100%;
  padding: 0 20px;
  border: 0;
  color: ${({ $active }) => ($active ? colors.navy : colors.textSoft)};
  background: transparent;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
  white-space: nowrap;
  cursor: pointer;

  &::after {
    position: absolute;
    right: 10px;
    bottom: -1px;
    left: 10px;
    height: 3px;
    border-radius: 999px 999px 0 0;
    background: ${({ $active }) => ($active ? colors.mint : "transparent")};
    content: "";
  }

  @media (hover: hover) {
    &:hover {
      color: ${colors.navy};
    }
  }

  @media (max-width: 640px) {
    padding: 0 8px;
  }

  @media (max-width: 520px) {
    gap: 4px;
    padding: 0 4px;
    font-size: 14px;
    line-height: 20px;
  }
`;

export const TooltipHost = styled.span`
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
`;

export const TooltipIcon = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: ${colors.blue};
  background: transparent;
  cursor: help;

  @media (hover: hover) {
    &:hover {
      background: #f2f6ff;
    }
  }

  @media (max-width: 520px) {
    width: 20px;
    height: 20px;
  }
`;

export const Tooltip = styled.span`
  position: absolute;
  z-index: 5;
  bottom: calc(100% + 9px);
  left: 50%;
  width: max-content;
  max-width: 250px;
  padding: 9px 11px;
  border-radius: 9px;
  color: ${colors.white};
  background: ${colors.navyDark};
  box-shadow: 0 8px 20px rgba(12, 50, 115, 0.16);
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
  opacity: 0;
  pointer-events: none;
  transform: translate(-50%, 5px);
  transition:
    opacity 150ms ease,
    transform 150ms ease;

  &::after {
    position: absolute;
    top: 100%;
    left: 50%;
    border: 5px solid transparent;
    border-top-color: ${colors.navyDark};
    content: "";
    transform: translateX(-50%);
  }

  ${TooltipHost}:hover &,
  ${TooltipHost}:focus-within &,
  ${TabButton}:focus-visible & {
    opacity: 1;
    transform: translate(-50%, 0);
  }

  @media (max-width: 640px) {
    right: -8px;
    left: auto;
    max-width: min(230px, calc(100vw - 32px));
    transform: translateY(5px);

    &::after {
      right: 11px;
      left: auto;
      transform: none;
    }

    ${TooltipHost}:hover &,
    ${TooltipHost}:focus-within &,
    ${TabButton}:focus-visible & {
      transform: translateY(0);
    }
  }
`;

export const FilterToggle = styled.button`
  ${focusRing};
  display: none;
  flex: 0 0 auto;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  color: ${colors.navy};
  background: ${colors.white};
  font-size: 14px;
  font-weight: 600;
  line-height: 16px;
  cursor: pointer;

  @media (max-width: 1024px) {
    display: inline-flex;
  }

  @media (max-width: 520px) {
    width: 44px;
    padding: 0;
    font-size: 0;
  }
`;

export const FilterStatus = styled.div`
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 12px 20px;
  background: #fdfdfd;

  @media (max-width: 640px) {
    align-items: flex-start;
    padding: 12px 14px;
  }

  @media (max-width: 480px) {
    flex-direction: column;
    gap: 8px;
  }
`;

export const FilterStatusContent = styled.div`
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  min-width: 0;

  @media (max-width: 480px) {
    width: 100%;
  }
`;

export const FilterStatusLabel = styled.span`
  color: ${colors.textSoft};
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
`;

export const AppliedFilterList = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
`;

export const AppliedFilter = styled.span`
  display: inline-flex;
  gap: 5px;
  align-items: center;
  min-height: 40px;
  padding: 4px 4px 4px 12px;
  border: 1px solid rgba(0, 167, 159, 0.18);
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;

  button {
    ${focusRing};
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    border: 0;
    border-radius: 50%;
    color: ${colors.mintDark};
    background: transparent;
    cursor: pointer;
  }

  button:hover {
    background: rgba(0, 167, 159, 0.12);
  }
`;

export const NoFilterText = styled.span`
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 20px;
`;

export const ResetFilterButton = styled.button`
  ${focusRing};
  display: inline-flex;
  flex: 0 0 auto;
  gap: 6px;
  align-items: center;
  min-height: 44px;
  padding: 0 11px;
  border: 0;
  border-radius: 10px;
  color: ${colors.textSoft};
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  line-height: 16px;
  cursor: pointer;

  &:disabled {
    color: #b9bbc0;
    cursor: not-allowed;
  }

  @media (hover: hover) {
    &:not(:disabled):hover {
      color: ${colors.navy};
      background: ${colors.navySoft};
    }
  }

  @media (max-width: 480px) {
    align-self: flex-end;
  }

`;

export const SearchLayout = styled.div`
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 24px;
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const FilterPanel = styled.aside`
  position: sticky;
  top: 92px;
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 1024px) {
    position: static;
    display: ${({ $open }) => ($open ? "block" : "none")};
  }
`;

export const FilterPanelHeader = styled.div`
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px;
  border-bottom: 1px solid ${colors.border};
  background: linear-gradient(135deg, #ffffff 0%, #fbfcff 100%);
`;

export const FilterTitle = styled.h2`
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;

  svg {
    color: ${colors.mint};
  }
`;

export const FilterDescription = styled.p`
  margin: 6px 0 0;
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 20px;
  word-break: keep-all;
`;

export const ActiveFilterCount = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 7px;
  border-radius: 999px;
  border: 1px solid
    ${({ $active }) => ($active ? "rgba(0, 106, 101, 0.18)" : colors.border)};
  color: ${({ $active }) => ($active ? colors.mintDark : colors.textSoft)};
  background: ${({ $active }) => ($active ? colors.mintSoft : "#f1f1f1")};
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
`;

export const FilterGroups = styled.div`
  padding: 0 20px;

  @media (max-width: 1024px) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 24px;
  }

  @media (max-width: 640px) {
    grid-template-columns: minmax(0, 1fr);
    padding: 0 16px;
  }
`;

export const FilterFieldset = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 20px 0;
  border: 0;
  border-bottom: 1px solid ${colors.border};

  &:last-child {
    border-bottom: 0;
  }

  @media (max-width: 1024px) {
    &:nth-last-child(-n + 2) {
      border-bottom: 0;
    }
  }

  @media (max-width: 640px) {
    &:nth-last-child(2) {
      border-bottom: 1px solid ${colors.border};
    }
  }
`;

export const FilterLegend = styled.legend`
  margin: 0 0 12px;
  padding: 0;
  color: ${colors.text};
  font-size: 14px;
  font-weight: 600;
  line-height: 16px;
`;

export const FilterChoices = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
`;

export const FilterChoice = styled.label`
  display: inline-flex;
  gap: 5px;
  align-items: center;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid
    ${({ $selected }) => ($selected ? colors.mint : colors.border)};
  border-radius: 999px;
  color: ${({ $selected }) => ($selected ? colors.mintDark : colors.textSoft)};
  background: ${({ $selected }) => ($selected ? colors.mintSoft : colors.white)};
  font-size: 14px;
  font-weight: ${({ $selected }) => ($selected ? 600 : 500)};
  line-height: 16px;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    color 150ms ease,
    background-color 150ms ease;

  &:focus-within {
    outline: 3px solid rgba(0, 167, 159, 0.2);
    outline-offset: 2px;
  }

  @media (hover: hover) {
    &:hover {
      border-color: ${colors.mint};
    }
  }
`;

export const HiddenInput = styled.input`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  border: 0;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
`;

export const ChoiceIndicator = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  border: 1.5px solid
    ${({ $selected }) => ($selected ? colors.mint : "#a9acb3")};
  border-radius: 50%;
  color: ${colors.white};
  background: ${({ $selected }) => ($selected ? colors.mint : colors.white)};
`;

export const ProfileResults = styled.section`
  min-width: 0;

  &:focus {
    outline: none;
  }

  &:focus-visible {
    border-radius: 16px;
    outline: 3px solid rgba(0, 167, 159, 0.24);
    outline-offset: 5px;
  }
`;

export const ResultsHeader = styled.header`
  display: flex;
  gap: 20px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
`;

export const ResultsTitle = styled.h2`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
  letter-spacing: -0.01em;
`;

export const ResultsDescription = styled.p`
  margin: 5px 0 0;
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 20px;
  word-break: keep-all;
`;

export const ResultCount = styled.span`
  flex: 0 0 auto;
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 20px;

  strong {
    margin-right: 2px;
    color: ${colors.navy};
    font-size: 18px;
  }
`;

export const ProfileGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;

  @media (max-width: 680px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const ProfileCard = styled.article`
  min-width: 0;
  padding: 24px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
  @media (max-width: 480px) {
    padding: 16px;
  }
`;

export const CardHeader = styled.div`
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;

  @media (max-width: 430px) {
    grid-template-columns: 68px minmax(0, 1fr);
    gap: 12px;
  }
`;

export const ProfilePhoto = styled.div`
  width: 76px;
  aspect-ratio: 1;
  overflow: hidden;
  border: 1px solid rgba(12, 50, 115, 0.07);
  border-radius: 12px;
  background: ${colors.navySoft};

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  @media (max-width: 430px) {
    width: 68px;
  }
`;

export const ProfileIdentity = styled.div`
  min-width: 0;
`;

export const ProfileNameRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-width: 0;
`;

export const ProfileName = styled.h3`
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const VerifiedBadge = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  gap: 3px;
  align-items: center;
  min-height: 22px;
  padding: 3px 7px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
`;

export const ProfileAcademic = styled.p`
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  margin: 6px 0 0;
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 20px;

  > span:last-child {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`;

export const AdmissionYear = styled.span`
  flex: 0 0 auto;
  color: ${colors.navy};
  font-weight: 600;
`;

export const Dormitory = styled.span`
  display: flex;
  gap: 4px;
  align-items: center;
  margin-top: 6px;
  color: ${colors.textSoft};
  font-size: 14px;
  font-weight: 400;
  line-height: 20px;

  svg {
    color: ${colors.mint};
  }
`;

export const MatchBadge = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  gap: 4px;
  align-items: center;
  min-height: 28px;
  padding: 5px 9px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
  white-space: nowrap;

  @media (max-width: 430px) {
    grid-column: 2;
    justify-self: start;
  }
`;

export const ProfileMessage = styled.div`
  margin-top: 20px;
  padding: 16px;
  border-radius: 12px;
  background: #f7f7f6;

  span {
    display: block;
    color: ${colors.textSoft};
    font-size: 12px;
    font-weight: 500;
    line-height: 14px;
    letter-spacing: 0.04em;
  }

  p {
    margin: 5px 0 0;
    color: ${colors.textSoft};
    font-size: 14px;
    line-height: 20px;
    word-break: keep-all;
  }
`;

export const TagList = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 16px;
`;

export const ProfileTag = styled.span`
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 5px 10px;
  border-radius: 999px;
  color: #5f6269;
  background: #f6f6f6;
  font-size: 12px;
  font-weight: 500;
  line-height: 14px;
`;

export const EmptyState = styled.div`
  display: flex;
  min-height: 420px;
  padding: 48px 24px;
  border: 1px dashed ${colors.border};
  border-radius: 16px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  background: ${colors.white};
  text-align: center;

  h3 {
    margin: 17px 0 0;
    color: ${colors.navyDark};
    font-size: 20px;
    font-weight: 600;
    line-height: 28px;
  }

  p {
    max-width: 360px;
    margin: 8px 0 0;
    color: ${colors.textSoft};
    font-size: 14px;
    line-height: 20px;
    word-break: keep-all;
  }

  @media (max-width: 680px) {
    min-height: 340px;
  }
`;

export const EmptyIcon = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 20px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
`;

export const EmptyResetButton = styled.button`
  ${focusRing};
  display: inline-flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  margin-top: 20px;
  padding: 0 16px;
  border: 1.5px solid ${colors.mint};
  border-radius: 12px;
  color: ${colors.mintDark};
  background: ${colors.white};
  font-size: 14px;
  font-weight: 600;
  line-height: 16px;
  cursor: pointer;
  transition:
    color 160ms ease,
    background-color 160ms ease;

  @media (hover: hover) {
    &:hover {
      color: ${colors.white};
      background: ${colors.mint};
    }
  }
`;
