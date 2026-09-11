import styled, { css } from "styled-components";
import { Link } from "react-router-dom";

const colors = {
  warm: "#faf9f6",
  white: "#ffffff",
  navy: "#0c3273",
  navyDark: "#001d4f",
  navySoft: "#eef3fb",
  mint: "#00a79f",
  mintDark: "#006a65",
  mintSoft: "#e7f8f6",
  text: "#1a1c1c",
  secondary: "#444650",
  muted: "#747781",
  border: "#e2e2e4",
  soft: "#f6f6f6",
  error: "#ba1a1a",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.4);
    outline-offset: 3px;
  }
`;

const surface = css`
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;

const button = css`
  ${focusRing}
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 11px 18px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  text-decoration: none;
  cursor: pointer;
  transition:
    background 150ms ease,
    border-color 150ms ease,
    box-shadow 150ms ease;

  svg {
    flex-shrink: 0;
  }

  &:disabled {
    color: ${colors.muted};
    border-color: ${colors.border};
    background: ${colors.soft};
    cursor: not-allowed;
    box-shadow: none;
  }
`;

const control = css`
  width: 100%;
  min-width: 0;
  min-height: 48px;
  padding: 12px 14px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  color: ${colors.text};
  background: ${colors.white};
  font-size: 14px;
  line-height: 22px;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease;

  &:focus {
    outline: none;
    border-color: ${colors.mint};
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.12);
  }

  &[aria-invalid="true"] {
    border-color: ${colors.error};
  }

  &:read-only:not(select),
  &:disabled {
    color: ${colors.secondary};
    background: ${colors.soft};
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 1;
  }
`;

export const Page = styled.main`
  min-height: 100vh;
  padding: 56px 24px 80px;
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
  font-size: 16px;
  line-height: 1.5;
  overflow-wrap: anywhere;

  &,
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }
  button,
  input,
  select {
    font-family: inherit;
  }
  button,
  a,
  input,
  select,
  [tabindex] {
    ${focusRing}
  }
  svg {
    vertical-align: middle;
  }
  img {
    display: block;
    max-width: 100%;
  }

  @media (max-width: 640px) {
    padding: 32px 16px 48px;
  }

  @media (prefers-reduced-motion: reduce) {
    *,
    *::before,
    *::after {
      transition: none;
    }
  }
`;

export const Container = styled.div`
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
`;

export const Intro = styled.div`
  margin-bottom: 40px;
  @media (max-width: 640px) {
    margin-bottom: 28px;
  }
`;

export const Eyebrow = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: ${colors.mintDark};
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  letter-spacing: 0.1em;
`;

export const PageTitle = styled.h1`
  margin: 12px 0 8px;
  color: ${colors.navyDark};
  font-size: 36px;
  font-weight: 700;
  line-height: 44px;
  letter-spacing: -0.02em;
  @media (max-width: 640px) {
    font-size: 28px;
    line-height: 36px;
  }
`;

export const Description = styled.p`
  margin: 8px 0 0;
  color: ${colors.secondary};
  font-size: 15px;
  line-height: 24px;
  word-break: keep-all;
  @media (max-width: 640px) {
    font-size: 14px;
    line-height: 22px;
  }
`;

export const Layout = styled.div`
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  align-items: start;
  gap: 32px;

  @media (max-width: 1000px) {
    grid-template-columns: 220px minmax(0, 1fr);
    gap: 24px;
  }
  @media (max-width: 800px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const Sidebar = styled.aside`
  ${surface}
  position: sticky;
  top: 24px;
  padding: 24px 16px 16px;

  @media (max-width: 800px) {
    position: static;
    padding: 20px 16px 12px;
  }
`;

export const Avatar = styled.img`
  flex-shrink: 0;
  width: ${({ $large }) => ($large ? "96px" : "48px")};
  height: ${({ $large }) => ($large ? "96px" : "48px")};
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 12px;
  background: ${colors.navySoft};
`;

export const SidebarProfile = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px 24px;
  margin-bottom: 16px;
  border-bottom: 1px solid ${colors.border};
  > div {
    min-width: 0;
  }

  @media (max-width: 800px) {
    padding-bottom: 16px;
    margin-bottom: 8px;
  }
`;

export const SidebarName = styled.strong`
  display: block;
  color: ${colors.navyDark};
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
`;

export const Muted = styled.p`
  margin: 4px 0 0;
  color: ${colors.muted};
  font-size: 13px;
  line-height: 21px;
`;

export const Menu = styled.nav`
  display: flex;
  flex-direction: column;
  gap: 6px;

  @media (max-width: 800px) {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 4px;
  }
`;

export const MenuLink = styled(Link)`
  ${focusRing}
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 12px;
  border-radius: 12px;
  color: ${({ $active }) => ($active ? colors.navy : colors.secondary)};
  background: ${({ $active }) => ($active ? colors.navySoft : "transparent")};
  font-size: 14px;
  font-weight: ${({ $active }) => ($active ? 700 : 500)};
  line-height: 22px;
  text-decoration: none;
  transition: background 150ms ease;
  svg {
    flex-shrink: 0;
  }
  > span:first-of-type {
    flex: 1;
  }
  &:hover {
    background: ${({ $active }) => ($active ? "#e5edf9" : colors.soft)};
  }

  @media (max-width: 800px) {
    gap: 8px;
    padding: 10px 12px;
    > svg:last-child:not(:first-child) {
      display: none;
    }
  }

  @media (max-width: 480px) {
    font-size: 13px;
  }
`;

export const MenuCount = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 23px;
  height: 23px;
  padding: 0 6px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 12px;
  font-weight: 700;
`;

export const SideNote = styled.div`
  margin-top: 32px;
  padding: 20px 14px;
  border-radius: 12px;
  background: ${colors.warm};
  > svg {
    margin-bottom: 10px;
    color: ${colors.mintDark};
  }
  > strong {
    display: block;
    color: ${colors.navy};
    font-size: 13px;
  }
  @media (max-width: 800px) {
    display: none;
  }
`;

export const Content = styled.section`
  min-width: 0;
`;

export const Stack = styled.div`
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
`;

export const SectionHeading = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  > div {
    min-width: 0;
  }
`;

export const SectionTitle = styled.h2`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.01em;
  > span {
    margin-left: 8px;
    vertical-align: middle;
  }
  @media (max-width: 640px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const PrimaryButton = styled.button`
  ${button}
  color: ${colors.white};
  background: ${colors.navy};
  &:hover:not(:disabled) {
    background: ${colors.navyDark};
    box-shadow: 0 4px 12px rgba(12, 50, 115, 0.12);
  }
`;

export const SecondaryButton = styled.button`
  ${button}
  border: 1.5px solid ${colors.mint};
  color: ${colors.mintDark};
  background: ${colors.white};
  &:hover:not(:disabled) {
    background: ${colors.mintSoft};
  }
`;

export const GhostButton = styled.button`
  ${button}
  color: ${colors.secondary};
  background: transparent;
  &:hover:not(:disabled) {
    color: ${colors.navy};
    background: ${colors.soft};
  }
`;

export const IconButton = styled.button`
  ${button}
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  padding: 0;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  &:hover:not(:disabled) {
    color: ${colors.navy};
    background: ${colors.navySoft};
  }
`;

export const Badge = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 999px;
  color: ${({ $muted }) => ($muted ? colors.secondary : colors.mintDark)};
  background: ${({ $muted }) => ($muted ? colors.soft : colors.mintSoft)};
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  letter-spacing: 0;
  svg {
    flex-shrink: 0;
  }
`;

export const RequiredBadge = styled.span`
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 2px 7px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
  vertical-align: middle;
`;

export const Card = styled.section`
  ${surface}
  min-width: 0;
  padding: 28px;
  @media (max-width: 640px) {
    padding: 20px;
  }
`;

export const CardHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  > svg {
    color: ${colors.navy};
  }
`;

export const CardHeaderActions = styled.div`
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
`;

export const CardTitle = styled.h3`
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 0;
  color: ${colors.navyDark};
  font-size: 18px;
  font-weight: 600;
  line-height: 28px;
  letter-spacing: -0.01em;
  svg {
    flex-shrink: 0;
    color: ${colors.navy};
  }
`;

export const MbtiValue = styled.span`
  display: inline-flex;
  align-items: center;
  align-self: center;
  min-height: 26px;
  margin-top: 1px;
  padding: 3px 9px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  letter-spacing: 0.06em;
`;

export const PasswordSummary = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  color: ${colors.muted};
  font-size: 14px;
  line-height: 22px;
  svg {
    flex-shrink: 0;
    color: ${colors.mintDark};
  }
`;

export const ProfileHero = styled.div`
  display: flex;
  align-items: center;
  gap: 24px;
  padding-bottom: 28px;
  margin-bottom: 28px;
  border-bottom: 1px solid ${colors.border};
  > div {
    min-width: 0;
  }
  @media (max-width: 480px) {
    align-items: flex-start;
    gap: 16px;
    flex-wrap: wrap;
  }
`;

export const ProfileName = styled.h3`
  margin: 10px 0 4px;
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

export const TagRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
`;

export const Tag = styled.span`
  display: inline-flex;
  padding: 5px 10px;
  border-radius: 999px;
  color: ${colors.secondary};
  background: ${colors.soft};
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
`;

export const DetailGrid = styled.dl`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px 32px;
  margin: 0;
  @media (max-width: 480px) {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
`;

export const DetailItem = styled.div`
  min-width: 0;
  dt {
    color: ${colors.muted};
    font-size: 13px;
    line-height: 20px;
  }
  dd {
    margin: 6px 0 0;
    color: ${colors.text};
    font-size: 15px;
    font-weight: 600;
    line-height: 24px;
  }
`;

export const SummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 24px;
  @media (max-width: 560px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const SummaryCard = styled.button`
  ${focusRing}
  display: block;
  min-width: 0;
  padding: 20px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  color: ${colors.text};
  background: ${colors.white};
  font: inherit;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease;
  &:hover {
    border-color: ${colors.mint};
    box-shadow: 0 8px 20px rgba(12, 50, 115, 0.08);
  }
`;

export const SummaryTitle = styled.strong`
  display: block;
  color: ${colors.navyDark};
  font-size: 16px;
  line-height: 24px;
`;

export const SummaryText = styled.p`
  margin: 6px 0 20px;
  color: ${colors.muted};
  font-size: 13px;
  line-height: 21px;
`;

export const SummaryFooter = styled.span`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: ${colors.mintDark};
  font-size: 13px;
  font-weight: 600;
  line-height: 22px;
`;

export const Banner = styled.div`
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px 20px;
  border-radius: 12px;
  color: ${({ $error }) => ($error ? colors.error : colors.mintDark)};
  background: ${({ $error }) => ($error ? "#fff2f0" : colors.mintSoft)};
  font-size: 14px;
  line-height: 23px;
  > svg {
    flex-shrink: 0;
    margin-top: 2px;
  }
  > span,
  > div {
    flex: 1;
    min-width: 160px;
  }
  > button {
    margin: -6px 0;
  }
`;

export const SurveyGrid = styled.dl`
  display: grid;
  margin: 0;
`;

export const SurveyRow = styled.div`
  display: grid;
  grid-template-columns: minmax(140px, 0.8fr) minmax(0, 1.2fr);
  align-items: start;
  gap: 16px;
  padding: 16px 0;
  &:first-child {
    padding-top: 0;
  }
  &:last-child {
    padding-bottom: 0;
  }
  dt {
    color: ${colors.secondary};
    font-size: 14px;
    line-height: 24px;
  }
  dd {
    margin: 0;
    color: ${colors.text};
    font-size: 14px;
    font-weight: 600;
    line-height: 24px;
  }
  @media (max-width: 560px) {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
  }
`;

export const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: ${({ $compact }) => ($compact ? "16px" : "24px")};
  margin-top: 16px;
  ${({ $compact }) =>
    $compact &&
    `
      margin-top: 10px;
      > div {
        padding-bottom: 12px;
      }
    `}
  @media (max-width: 1000px) and (min-width: 801px) {
    grid-template-columns: minmax(0, 1fr);
  }
  @media (max-width: 560px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const Field = styled.div`
  min-width: 0;
  padding: 4px 0 20px;
`;

export const WeightControl = styled.div`
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  gap: 8px 14px;
  margin-top: 12px;
  @media (max-width: 560px) {
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 8px 10px;
  }
`;

export const WeightSlider = styled.input`
  --weight-progress: ${({ $value }) => `${(($value - 1) / 4) * 100}%`};
  --weight-color: ${({ $value }) => {
    if ($value >= 5) return "#138f83";
    if ($value >= 4) return "#36bdae";
    if ($value >= 3) return "#66d2c5";
    if ($value >= 2) return "#9be4dc";
    return "#e3e9e8";
  }};
  flex: 1 1 240px;
  min-width: 180px;
  height: 8px;
  margin: 0;
  accent-color: var(--weight-color);
  appearance: none;
  border-radius: 999px;
  background:
    linear-gradient(to right, #d9f5f1, var(--weight-color)) 0 0 /
      var(--weight-progress) 100% no-repeat,
    #e6ebea;
  cursor: pointer;

  &::-webkit-slider-runnable-track {
    height: 8px;
    border-radius: 999px;
    background:
      linear-gradient(to right, #d9f5f1, var(--weight-color)) 0 0 /
        var(--weight-progress) 100% no-repeat,
      #e6ebea;
  }

  &::-webkit-slider-thumb {
    width: 20px;
    height: 20px;
    margin-top: -6px;
    appearance: none;
    border: 3px solid ${colors.white};
    border-radius: 50%;
    background: var(--weight-color);
    box-shadow: 0 1px 5px rgba(0, 106, 101, 0.2);
  }

  &::-moz-range-track {
    height: 8px;
    border-radius: 999px;
    background: #e6ebea;
  }

  &::-moz-range-progress {
    height: 8px;
    border-radius: 999px;
    background: linear-gradient(to right, #d9f5f1, var(--weight-color));
  }

  &::-moz-range-thumb {
    width: 14px;
    height: 14px;
    border: 3px solid ${colors.white};
    border-radius: 50%;
    background: var(--weight-color);
    box-shadow: 0 1px 5px rgba(0, 106, 101, 0.2);
  }

  &:focus-visible {
    outline: 3px solid rgba(54, 205, 187, 0.35);
    outline-offset: 4px;
  }
`;

export const WeightValue = styled.span`
  color: ${colors.navyDark};
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
`;

export const WeightBadge = styled.span`
  display: inline-block;
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 12px;
  white-space: nowrap;
`;

export const ReadonlyValue = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  gap: 12px;
  color: ${colors.text};
  font-size: 14px;
  line-height: 22px;
  > button {
    flex-shrink: 0;
    min-height: 34px;
    padding: 7px 10px;
  }
`;

export const Label = styled.label`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
  color: ${colors.navyDark};
  font-size: 14px;
  font-weight: 700;
  line-height: 22px;
  > svg {
    color: ${colors.muted};
  }
`;

export const Input = styled.input`
  ${control}
`;
export const Select = styled.select`
  ${control} cursor: pointer;
`;

export const FieldError = styled.p`
  margin: 8px 0 0;
  color: ${colors.error};
  font-size: 13px;
  line-height: 21px;
`;

export const ValidMessage = styled.p`
  margin: 8px 0 0;
  color: ${colors.mintDark};
  font-size: 13px;
  line-height: 21px;
`;

export const Helper = styled.p`
  margin: 8px 0 0;
  color: ${colors.muted};
  font-size: 13px;
  font-weight: 400;
  line-height: 21px;
  &:empty {
    display: none;
  }
`;

export const WeightNotice = styled.p`
  max-width: 680px;
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 12px 0 0;
  padding-left: 12px;
  border-left: 3px solid #36cdbb;
  color: ${colors.secondary};
  font-size: 13px;
  font-weight: 400;
  line-height: 22px;
  word-break: keep-all;
`;

export const WeightNoticeLabel = styled.strong`
  flex: 0 0 auto;
  color: ${colors.mintDark};
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.02em;
`;

export const Fieldset = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
`;

export const Legend = styled.legend`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 100%;
  margin-bottom: 10px;
  padding: 0;
  color: ${colors.navyDark};
  font-size: 16px;
  font-weight: 700;
  line-height: 22px;
`;

export const MultiSelectHint = styled.span`
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #d9dee6;
  color: #5d6674;
  background: #f4f6f8;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  white-space: nowrap;
`;

export const ChoiceGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
`;

export const ChoiceButton = styled.button`
  ${button}
  min-width: 48px;
  padding: 10px 13px;
  border-color: ${({ $selected }) => ($selected ? colors.mint : colors.border)};
  color: ${({ $selected }) => ($selected ? colors.mintDark : colors.secondary)};
  background: ${({ $selected }) =>
    $selected ? colors.mintSoft : colors.white};
  font-size: 13px;
  font-weight: ${({ $selected }) => ($selected ? 600 : 400)};
  &:hover:not(:disabled) {
    border-color: ${colors.mint};
    background: ${colors.mintSoft};
  }
  &[aria-invalid="true"] {
    border-color: ${colors.error};
  }
`;

export const FormActions = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 12px;
  padding: 8px 0;
  @media (max-width: 480px) {
    > button {
      flex: 1;
    }
  }
`;

export const MbtiGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px 16px;
  @media (max-width: 1120px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  @media (max-width: 360px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const EmptyCard = styled.section`
  ${surface}
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 32px;
  text-align: center;
  > a {
    margin-top: 28px;
    max-width: 100%;
  }
  @media (max-width: 640px) {
    padding: 48px 24px;
  }
`;

export const EmptyIcon = styled.div`
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  margin-bottom: 24px;
  border-radius: 24px;
  color: ${colors.navy};
  background: ${colors.navySoft};
`;

export const EmptyTitle = styled.h3`
  margin: 20px 0 12px;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 600;
  line-height: 30px;
  letter-spacing: -0.01em;
  word-break: keep-all;
`;

export const EmptyText = styled.p`
  max-width: 390px;
  margin: 0;
  color: ${colors.muted};
  font-size: 14px;
  line-height: 24px;
  word-break: keep-all;
`;

export const LikeGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  @media (max-width: 560px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const Pagination = styled.nav`
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  margin-top: 28px;
`;

export const PageButton = styled.button`
  ${focusRing}
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid ${({ $active }) => ($active ? colors.navy : colors.border)};
  border-radius: 10px;
  color: ${({ $active }) => ($active ? colors.white : colors.secondary)};
  background: ${({ $active }) => ($active ? colors.navy : colors.white)};
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 150ms ease,
    color 150ms ease,
    border-color 150ms ease;
  &:hover:not(:disabled) {
    border-color: ${colors.navy};
    color: ${({ $active }) => ($active ? colors.white : colors.navy)};
  }
  &:disabled {
    color: ${colors.muted};
    background: ${colors.soft};
    cursor: not-allowed;
  }
`;

export const LikeCard = styled.article`
  ${surface}
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px;
  transition: box-shadow 150ms ease;
  &:hover {
    box-shadow: 0 8px 20px rgba(12, 50, 115, 0.08);
  }
  @media (max-width: 1000px) and (min-width: 801px) {
    padding: 20px;
  }
`;

export const LikeTop = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
`;

export const LikeName = styled.h3`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
`;

export const LikeMeta = styled.p`
  margin: 6px 0 0;
  color: ${colors.muted};
  font-size: 13px;
  line-height: 22px;
`;

export const LikeMessage = styled.p`
  margin: 20px 0 0;
  color: ${colors.secondary};
  font-size: 14px;
  line-height: 24px;
  word-break: keep-all;
`;

export const LikeFooter = styled.div`
  padding-top: 24px;
  margin-top: auto;
  > a {
    width: 100%;
  }
`;

export const MatchList = styled.div`
  display: flex;
  flex-direction: column;
  margin-top: 20px;
`;

export const MatchRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid ${colors.border};
  &:last-child {
    padding-bottom: 0;
    border-bottom: 0;
  }
  > div {
    flex: 1;
    min-width: 0;
  }
  > span {
    flex-shrink: 0;
    max-width: 140px;
  }
  @media (max-width: 480px) {
    flex-wrap: wrap;
    > div {
      min-width: calc(100% - 64px);
    }
    > span {
      margin-left: 60px;
    }
  }
`;

export const MatchName = styled.strong`
  display: block;
  color: ${colors.navyDark};
  font-size: 14px;
  line-height: 22px;
`;

export const MatchDescription = styled.span`
  display: block;
  margin-top: 4px;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 20px;
`;

export const ProgressTrack = styled.div`
  height: 6px;
  margin-top: 20px;
  overflow: hidden;
  border-radius: 999px;
  background: ${colors.soft};
`;

export const ProgressFill = styled.div`
  width: ${({ $value }) => `${$value}%`};
  height: 100%;
  border-radius: inherit;
  background: ${colors.mint};
  transition: width 180ms ease;
`;

export const SubtleLink = styled(Link)`
  ${focusRing}
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  color: ${colors.mintDark};
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  &:hover {
    text-decoration: underline;
    text-underline-offset: 4px;
  }
`;

export const PreviewPanel = styled.details`
  margin-top: 32px;
  padding: 16px 20px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  background: ${colors.white};
  > summary {
    ${focusRing}
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 28px;
    color: ${colors.secondary};
    font-size: 13px;
    font-weight: 500;
    line-height: 20px;
    list-style: none;
    cursor: pointer;
  }
  > summary::-webkit-details-marker {
    display: none;
  }
  > summary > span {
    margin-left: auto;
    color: ${colors.muted};
    font-size: 12px;
  }
  > summary::after {
    content: "+";
    margin-left: 6px;
    font-size: 20px;
  }
  &[open] > summary {
    margin-bottom: 16px;
  }
  &[open] > summary::after {
    content: "−";
  }
  @media (max-width: 360px) {
    > summary > span {
      display: none;
    }
  }
`;

export const PreviewOptions = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;
  @media (max-width: 600px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;
