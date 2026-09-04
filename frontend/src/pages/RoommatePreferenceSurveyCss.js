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
  error: "#ba1a1a",
  errorSoft: "#fff2f0",
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
  input,
  select {
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
  max-width: 780px;
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
  font-weight: 800;
  line-height: 16px;
  letter-spacing: 0.08em;
`;

export const PageTitle = styled.h1`
  margin: 16px 0 0;
  color: ${colors.navyDark};
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.01em;
  word-break: keep-all;

  @media (max-width: 700px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const PageDescription = styled.p`
  max-width: 690px;
  margin: 13px 0 0;
  color: ${colors.textSoft};
  font-size: 16px;
  line-height: 26px;
  word-break: keep-all;

  @media (max-width: 700px) {
    font-size: 14px;
    line-height: 22px;
  }
`;

export const IntroMeta = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  margin-top: 20px;
  color: ${colors.muted};

  span {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    font-size: 13px;
    font-weight: 600;
    line-height: 19px;
  }

  svg {
    color: ${colors.mint};
  }
`;

export const SurveyLayout = styled.div`
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  gap: 24px;
  align-items: start;

  @media (max-width: 1024px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const StepPanel = styled.aside`
  position: sticky;
  top: 92px;
  padding: 22px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 1024px) {
    position: static;
    padding: 20px;
  }

  @media (max-width: 640px) {
    padding: 18px 16px;
  }
`;

export const ProgressHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;

  span,
  strong {
    font-size: 13px;
    line-height: 18px;
  }

  span {
    color: ${colors.textSoft};
    font-weight: 700;
  }

  strong {
    color: ${colors.mintDark};
    font-weight: 800;
  }
`;

export const ProgressTrack = styled.div`
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf0f2;
`;

export const ProgressFill = styled.span`
  display: block;
  width: ${({ $progress }) => Math.max(0, Math.min(100, $progress))}%;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, ${colors.mintDark}, ${colors.mint});
  transition: width 220ms ease;
`;

export const StepList = styled.ol`
  display: grid;
  gap: 7px;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;

  @media (max-width: 1024px) {
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 8px;
    margin-top: 18px;
  }

  @media (max-width: 820px) {
    display: flex;
    padding-bottom: 2px;
    overflow-x: auto;
    scrollbar-width: thin;

    li {
      flex: 0 0 142px;
    }
  }
`;

export const StepButton = styled.button`
  ${focusRing};
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 11px;
  align-items: center;
  width: 100%;
  min-height: 62px;
  padding: 9px 10px;
  border: 1px solid
    ${({ $active }) => ($active ? "rgba(12, 50, 115, 0.12)" : "transparent")};
  border-radius: 12px;
  color: ${colors.textSoft};
  background: ${({ $active }) => ($active ? colors.navySoft : "transparent")};
  text-align: left;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;

  @media (hover: hover) {
    &:hover {
      background: ${({ $active }) => ($active ? colors.navySoft : "#f7f7f7")};
    }
  }

  @media (max-width: 1024px) {
    min-height: 68px;
    padding: 9px;
  }
`;

export const StepMarker = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid
    ${({ $active, $complete }) =>
      $active || $complete ? "transparent" : colors.border};
  border-radius: 50%;
  color: ${({ $active, $complete }) =>
    $active || $complete ? colors.white : colors.muted};
  background: ${({ $active, $complete }) => {
    if ($complete) return colors.mint;
    if ($active) return colors.navy;
    return colors.white;
  }};
  font-size: 13px;
  font-weight: 800;
`;

export const StepCopy = styled.span`
  min-width: 0;

  strong,
  span {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: ${colors.navyDark};
    font-size: 13px;
    line-height: 19px;
  }

  span {
    margin-top: 2px;
    color: ${colors.subtle};
    font-size: 12px;
    line-height: 16px;
  }
`;

export const SideNote = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 9px;
  margin-top: 20px;
  padding: 13px;
  border-radius: 11px;
  color: ${colors.muted};
  background: #f7f7f6;
  font-size: 12px;
  line-height: 18px;
  word-break: keep-all;

  svg {
    margin-top: 1px;
    color: ${colors.blue};
  }

  @media (max-width: 1024px) {
    display: none;
  }
`;

export const FormCard = styled.section`
  min-width: 0;
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;

export const FormHeader = styled.header`
  position: relative;
  padding: 32px 36px 28px;
  border-bottom: 1px solid ${colors.border};
  background: linear-gradient(135deg, #ffffff 0%, #fbfcff 100%);

  @media (max-width: 640px) {
    padding: 26px 20px 22px;
  }
`;

export const StepKicker = styled.span`
  display: block;
  margin-bottom: 7px;
  color: ${colors.mintDark};
  font-size: 12px;
  font-weight: 800;
  line-height: 16px;
  letter-spacing: 0.08em;
`;

export const FormTitle = styled.h2`
  scroll-margin-top: 96px;
  margin: 0;
  color: ${colors.navyDark};
  font-size: 24px;
  font-weight: 800;
  line-height: 32px;
  letter-spacing: -0.025em;

  &:focus {
    outline: none;
  }

  &:focus-visible {
    border-radius: 4px;
    outline: 3px solid rgba(0, 167, 159, 0.24);
    outline-offset: 5px;
  }

  @media (max-width: 640px) {
    font-size: 21px;
    line-height: 29px;
  }
`;

export const FormDescription = styled.p`
  margin: 7px 0 0;
  color: ${colors.muted};
  font-size: 14px;
  line-height: 21px;
  word-break: keep-all;
`;

export const RequiredNotice = styled.p`
  margin: 12px 0 0;
  color: ${colors.subtle};
  font-size: 12px;
  line-height: 18px;

  span {
    color: ${colors.error};
    font-weight: 800;
  }
`;

export const FormBody = styled.div`
  padding: 0 36px 8px;

  @media (max-width: 640px) {
    padding: 0 20px 4px;
  }
`;

export const ValidationBanner = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 11px;
  align-items: start;
  margin-top: 24px;
  padding: 14px 16px;
  border: 1px solid rgba(186, 26, 26, 0.2);
  border-radius: 12px;
  color: ${colors.error};
  background: ${colors.errorSoft};

  svg {
    margin-top: 2px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 14px;
    line-height: 20px;
  }

  span {
    margin-top: 2px;
    color: #7e4242;
    font-size: 12px;
    line-height: 18px;
  }
`;

export const Fieldset = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 28px 0;
  border: 0;
  border-bottom: 1px solid ${colors.border};

  &:last-of-type {
    border-bottom: 0;
  }

  @media (max-width: 640px) {
    padding: 24px 0;
  }
`;

export const Legend = styled.legend`
  display: block;
  width: 100%;
  margin: 0 0 14px;
  padding: 0;
`;

export const LabelRow = styled.span`
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
`;

export const LabelText = styled.span`
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: ${colors.text};
  font-size: 15px;
  font-weight: 750;
  line-height: 22px;
`;

export const RequiredBadge = styled.span`
  display: inline-flex;
  align-items: center;
  min-height: 21px;
  padding: 0 7px;
  border-radius: 999px;
  color: ${colors.error};
  background: ${colors.errorSoft};
  font-size: 12px;
  font-weight: 800;
  line-height: 14px;
  letter-spacing: 0.02em;
`;

export const FieldHint = styled.span`
  color: ${colors.subtle};
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
`;

export const ConditionNotice = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 11px;
  align-items: start;
  margin-bottom: 16px;
  padding: 15px 16px;
  border: 1px solid rgba(52, 120, 246, 0.18);
  border-radius: 12px;
  color: ${colors.blue};
  background: #f2f6ff;

  svg {
    margin-top: 1px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: ${colors.navy};
    font-size: 13px;
    line-height: 19px;
  }

  span {
    margin-top: 2px;
    color: ${colors.textSoft};
    font-size: 12px;
    line-height: 18px;
    word-break: keep-all;
  }
`;

export const SelectionGuide = styled.p`
  margin: -4px 0 12px;
  color: ${colors.subtle};
  font-size: 12px;
  line-height: 18px;
  word-break: keep-all;
`;

export const ChoiceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(${({ $columns }) => $columns}, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 720px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 420px) {
    grid-template-columns: minmax(0, 1fr);
  }
`;

export const ChoiceLabel = styled.label`
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
  min-height: 50px;
  padding: 10px 13px;
  border: 1px solid
    ${({ $selected }) => ($selected ? colors.mint : colors.border)};
  border-radius: 12px;
  color: ${({ $selected }) => ($selected ? colors.mintDark : colors.textSoft)};
  background: ${({ $selected }) =>
    $selected ? colors.mintSoft : colors.white};
  font-size: 14px;
  font-weight: ${({ $selected }) => ($selected ? 700 : 550)};
  line-height: 20px;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    color 150ms ease,
    background-color 150ms ease,
    transform 150ms ease;

  > span:last-child {
    min-width: 0;
    word-break: keep-all;
  }

  &:focus-within {
    outline: 3px solid rgba(0, 167, 159, 0.18);
    outline-offset: 2px;
  }

  @media (hover: hover) {
    &:hover {
      border-color: ${({ $selected }) => ($selected ? colors.mint : "#b9c7c6")};
      background: ${({ $selected }) =>
        $selected ? colors.mintSoft : "#fbfdfd"};
      transform: translateY(-1px);
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
  width: 20px;
  height: 20px;
  border: 1.5px solid
    ${({ $selected }) => ($selected ? colors.mint : "#a9acb3")};
  border-radius: ${({ $multiple }) => ($multiple ? "6px" : "50%")};
  color: ${colors.white};
  background: ${({ $selected }) => ($selected ? colors.mint : colors.white)};
`;

export const RequirementSummary = styled.div`
  margin-top: 20px;
  padding: 16px;
  border: 1px solid
    ${({ $empty }) => ($empty ? colors.border : "rgba(0, 167, 159, 0.24)")};
  border-radius: 12px;
  background: ${({ $empty }) => ($empty ? "#fafafa" : colors.mintSoft)};
`;

export const SummaryHeader = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;

  span {
    display: inline-flex;
    gap: 7px;
    align-items: center;
    color: ${colors.textSoft};
    font-size: 12px;
    font-weight: 700;
    line-height: 18px;
  }

  svg {
    color: ${colors.mintDark};
  }

  strong {
    color: ${colors.mintDark};
    font-size: 12px;
    line-height: 18px;
  }
`;

export const SummaryChips = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 12px;
`;

export const SummaryChip = styled.span`
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 5px 10px;
  border-radius: 999px;
  color: ${colors.mintDark};
  background: ${colors.white};
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
`;

export const SummaryEmpty = styled.p`
  margin: 8px 0 0;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 18px;
  word-break: keep-all;
`;

export const ErrorText = styled.p`
  display: flex;
  gap: 6px;
  align-items: flex-start;
  margin: 9px 0 0;
  color: ${colors.error};
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;

  svg {
    flex: 0 0 auto;
    margin-top: 2px;
  }
`;

export const CompleteBanner = styled.div`
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 11px;
  align-items: start;
  margin: 24px 0;
  padding: 16px;
  border: 1px solid rgba(0, 167, 159, 0.25);
  border-radius: 12px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};

  &:focus {
    outline: 3px solid rgba(0, 167, 159, 0.18);
    outline-offset: 2px;
  }

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 14px;
    line-height: 20px;
  }

  span {
    margin-top: 2px;
    color: #286e69;
    font-size: 12px;
    line-height: 18px;
  }
`;

export const FormFooter = styled.footer`
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 22px 36px;
  border-top: 1px solid ${colors.border};
  background: #fdfdfd;

  @media (max-width: 640px) {
    padding: 18px 20px;
  }
`;

const actionButton = css`
  ${focusRing};
  display: inline-flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-width: 118px;
  min-height: 48px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 750;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    color 160ms ease,
    background-color 160ms ease,
    transform 160ms ease;

  @media (hover: hover) {
    &:not(:disabled):hover {
      transform: translateY(-1px);
    }
  }

  @media (max-width: 420px) {
    min-width: 0;
    flex: 1;
    padding: 0 13px;
  }
`;

export const BackButton = styled.button`
  ${actionButton};
  border: 1px solid ${colors.border};
  color: ${colors.textSoft};
  background: ${colors.white};

  &:disabled {
    color: #b2b4b9;
    background: #f4f4f4;
    cursor: not-allowed;
  }

  @media (hover: hover) {
    &:not(:disabled):hover {
      border-color: #c2c5cb;
      background: #f7f7f7;
    }
  }
`;

export const NextButton = styled.button`
  ${actionButton};
  border: 1px solid ${colors.navy};
  color: ${colors.white};
  background: ${colors.navy};
  box-shadow: 0 4px 10px rgba(12, 50, 115, 0.14);

  @media (hover: hover) {
    &:hover {
      border-color: ${colors.navyDark};
      background: ${colors.navyDark};
    }
  }
`;
