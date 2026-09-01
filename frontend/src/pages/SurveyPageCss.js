import styled, { createGlobalStyle, css } from "styled-components";

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
  danger: "#ba1a1a",
  dangerSoft: "#fff1f0",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.22);
    outline-offset: 2px;
  }
`;

const buttonReset = css`
  margin: 0;
  border: 0;
  font: inherit;
  cursor: pointer;
`;

export const SurveyGlobalStyle = createGlobalStyle`
  *,
  *::before,
  *::after {
    box-sizing: border-box;
  }

  html {
    scroll-behavior: smooth;
  }

  body {
    margin: 0;
    background: ${colors.warm};
  }

  button,
  input,
  select,
  textarea {
    font: inherit;
  }

  button,
  label,
  select {
    -webkit-tap-highlight-color: transparent;
  }

  #root {
    min-height: 100vh;
  }
`;

export const PageShell = styled.div`
  min-height: 100vh;
  color: ${colors.text};
  background:
    radial-gradient(circle at 8% 5%, rgba(0, 167, 159, 0.07), transparent 28%),
    radial-gradient(circle at 94% 18%, rgba(52, 120, 246, 0.06), transparent 25%),
    ${colors.warm};
  font-family:
    "Plus Jakarta Sans",
    "Pretendard",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
`;

export const TopBar = styled.header`
  position: sticky;
  z-index: 50;
  top: 0;
  border-bottom: 1px solid rgba(226, 226, 228, 0.9);
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(14px);
`;

export const TopBarInner = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(1180px, 100%);
  min-height: 68px;
  margin: 0 auto;
  padding: 0 24px;

  @media (max-width: 640px) {
    min-height: 62px;
    padding: 0 16px;
  }
`;

export const BrandLink = styled.a`
  ${focusRing};
  display: inline-flex;
  gap: 10px;
  align-items: center;
  min-height: 44px;
  border-radius: 10px;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.035em;
  text-decoration: none;
`;

export const LogoMark = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: ${colors.white};
  background: linear-gradient(145deg, ${colors.navy}, ${colors.navyDark});
  box-shadow: 0 5px 12px rgba(12, 50, 115, 0.18);
`;

export const HeaderStatus = styled.div`
  display: flex;
  gap: 12px;
  align-items: center;
`;

export const SecureText = styled.span`
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: ${colors.muted};
  font-size: 12px;
  font-weight: 600;

  svg {
    color: ${colors.mintDark};
  }

  @media (max-width: 560px) {
    display: none;
  }
`;

export const ExitLink = styled.a`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 0 13px;
  border: 1px solid ${colors.border};
  border-radius: 11px;
  color: ${colors.textSoft};
  background: ${colors.white};
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;

  @media (hover: hover) {
    &:hover {
      border-color: rgba(0, 167, 159, 0.42);
      background: ${colors.mintSoft};
    }
  }
`;

export const Main = styled.main`
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 44px 24px 72px;

  @media (max-width: 800px) {
    padding: 28px 16px 48px;
  }
`;

export const Intro = styled.section`
  max-width: 760px;
  margin-bottom: 30px;

  @media (max-width: 800px) {
    margin-bottom: 22px;
  }
`;

export const Eyebrow = styled.span`
  display: inline-flex;
  gap: 7px;
  align-items: center;
  margin-bottom: 11px;
  color: ${colors.mintDark};
  font-size: 12px;
  font-weight: 800;
  line-height: 16px;
  letter-spacing: 0.09em;

  &::before {
    width: 21px;
    height: 2px;
    border-radius: 99px;
    background: ${colors.mint};
    content: "";
  }
`;

export const IntroTitle = styled.h1`
  margin: 0;
  color: ${colors.navyDark};
  font-size: clamp(28px, 4vw, 40px);
  font-weight: 800;
  line-height: 1.22;
  letter-spacing: -0.04em;
  word-break: keep-all;
`;

export const IntroDescription = styled.p`
  max-width: 650px;
  margin: 13px 0 0;
  color: ${colors.textSoft};
  font-size: 15px;
  line-height: 24px;
  word-break: keep-all;
`;

export const IntroMeta = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 9px 16px;
  margin-top: 18px;

  span {
    display: inline-flex;
    gap: 6px;
    align-items: center;
    color: ${colors.muted};
    font-size: 12px;
    font-weight: 600;
  }

  svg {
    color: ${colors.mintDark};
  }
`;

export const SurveyLayout = styled.div`
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  gap: 26px;
  align-items: start;

  @media (max-width: 920px) {
    grid-template-columns: 1fr;
    gap: 14px;
  }
`;

export const StepPanel = styled.aside`
  position: sticky;
  top: 92px;
  padding: 20px;
  border: 1px solid ${colors.border};
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.045);
  backdrop-filter: blur(10px);

  @media (max-width: 920px) {
    position: static;
    padding: 16px;
  }
`;

export const ProgressTop = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;

  span {
    color: ${colors.textSoft};
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    color: ${colors.mintDark};
    font-size: 13px;
  }
`;

export const ProgressTrack = styled.div`
  height: 7px;
  overflow: hidden;
  border-radius: 99px;
  background: #e9ecef;
`;

export const ProgressFill = styled.span`
  display: block;
  width: ${({ $progress }) => `${$progress}%`};
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, ${colors.mintDark}, ${colors.mint});
  transition: width 260ms ease;
`;

export const StepList = styled.ol`
  display: grid;
  gap: 5px;
  margin: 18px 0 0;
  padding: 0;
  list-style: none;

  @media (max-width: 920px) {
    display: flex;
    gap: 7px;
    margin-top: 14px;
    padding-bottom: 2px;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }
`;

export const StepButton = styled.button`
  ${buttonReset};
  ${focusRing};
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: 5px 7px;
  border-radius: 12px;
  color: ${({ $active }) => ($active ? colors.navy : colors.textSoft)};
  background: ${({ $active }) => ($active ? colors.navySoft : "transparent")};
  text-align: left;
  transition:
    color 160ms ease,
    background-color 160ms ease;

  &:disabled {
    cursor: default;
  }

  @media (hover: hover) {
    &:not(:disabled):hover {
      background: ${({ $active }) => ($active ? colors.navySoft : "#f6f7f8")};
    }
  }

  @media (max-width: 920px) {
    display: inline-flex;
    flex: 0 0 auto;
    width: auto;
    min-height: 44px;
    padding: 5px 11px 5px 7px;
    white-space: nowrap;
  }
`;

export const StepNumber = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: 1px solid
    ${({ $active, $complete }) =>
      $active || $complete ? "transparent" : colors.border};
  border-radius: 10px;
  color: ${({ $active, $complete }) =>
    $active || $complete ? colors.white : colors.muted};
  background: ${({ $active, $complete }) => {
    if ($complete) return colors.mintDark;
    if ($active) return colors.navy;
    return colors.white;
  }};
  font-size: 12px;
  font-weight: 800;

  svg {
    width: 15px;
  }
`;

export const StepLabel = styled.span`
  min-width: 0;
  font-size: 12px;
  font-weight: ${({ $active }) => ($active ? 800 : 650)};
`;

export const CurrentDot = styled.span`
  width: 6px;
  height: 6px;
  border-radius: 99px;
  background: ${colors.mint};

  @media (max-width: 920px) {
    display: none;
  }
`;

export const SideNote = styled.div`
  display: flex;
  gap: 9px;
  margin-top: 17px;
  padding-top: 15px;
  border-top: 1px solid ${colors.border};
  color: ${colors.muted};
  font-size: 11px;
  line-height: 17px;

  svg {
    flex: 0 0 auto;
    margin-top: 1px;
    color: ${colors.mintDark};
  }

  @media (max-width: 920px) {
    display: none;
  }
`;

export const FormCard = styled.section`
  min-width: 0;
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 20px;
  background: ${colors.white};
  box-shadow: 0 10px 28px rgba(12, 50, 115, 0.065);
`;

export const FormHeader = styled.header`
  padding: 28px 30px 24px;
  border-bottom: 1px solid ${colors.border};
  background: linear-gradient(145deg, #ffffff, #fbfcff);

  @media (max-width: 640px) {
    padding: 23px 20px 20px;
  }
`;

export const StepKicker = styled.span`
  display: block;
  margin-bottom: 7px;
  color: ${colors.mintDark};
  font-size: 11px;
  font-weight: 800;
  line-height: 16px;
  letter-spacing: 0.08em;
`;

export const FormTitle = styled.h2`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 24px;
  line-height: 32px;
  letter-spacing: -0.025em;
  word-break: keep-all;

  @media (max-width: 640px) {
    font-size: 21px;
    line-height: 29px;
  }
`;

export const FormDescription = styled.p`
  margin: 7px 0 0;
  color: ${colors.muted};
  font-size: 13px;
  line-height: 21px;
  word-break: keep-all;
`;

export const FormBody = styled.div`
  display: grid;
  gap: 30px;
  min-height: 440px;
  padding: 30px;

  @media (max-width: 640px) {
    gap: 26px;
    min-height: 0;
    padding: 24px 20px;
  }
`;

export const Fieldset = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
`;

export const Legend = styled.legend`
  width: 100%;
  margin: 0 0 11px;
  padding: 0;
  color: ${colors.text};
  font-size: 14px;
  font-weight: 750;
  line-height: 21px;
`;

export const LabelRow = styled.span`
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
`;

export const LabelText = styled.span`
  display: inline-flex;
  gap: 5px;
  align-items: center;
`;

export const Required = styled.span`
  color: ${colors.mintDark};
  font-size: 11px;
  font-weight: 800;
`;

export const FieldHint = styled.span`
  color: ${colors.muted};
  font-size: 11px;
  font-weight: 500;
`;

export const ChoiceGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(
    ${({ $columns }) => Math.min($columns || 3, 4)},
    minmax(0, 1fr)
  );
  gap: 9px;

  @media (max-width: 700px) {
    grid-template-columns: repeat(
      ${({ $mobileColumns }) => $mobileColumns || 2},
      minmax(0, 1fr)
    );
  }

  @media (max-width: 390px) {
    grid-template-columns: 1fr;
  }
`;

export const HiddenInput = styled.input`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
`;

export const ChoiceLabel = styled.label`
  position: relative;
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
  min-height: 48px;
  padding: 11px 12px;
  border: 1px solid
    ${({ $selected }) => ($selected ? colors.mint : colors.border)};
  border-radius: 12px;
  color: ${({ $selected }) => ($selected ? colors.navyDark : colors.textSoft)};
  background: ${({ $selected }) => ($selected ? colors.mintSoft : colors.white)};
  font-size: 12px;
  font-weight: ${({ $selected }) => ($selected ? 750 : 600)};
  line-height: 18px;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    color 160ms ease,
    transform 160ms ease;

  &:has(input:focus-visible) {
    outline: 3px solid rgba(0, 167, 159, 0.22);
    outline-offset: 2px;
  }

  @media (hover: hover) {
    &:hover {
      border-color: ${({ $selected }) =>
        $selected ? colors.mint : "rgba(0, 167, 159, 0.48)"};
      transform: translateY(-1px);
    }
  }
`;

export const ChoiceIndicator = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 19px;
  height: 19px;
  border: 1.5px solid
    ${({ $selected }) => ($selected ? colors.mintDark : "#b8bbc2")};
  border-radius: ${({ $multiple }) => ($multiple ? "6px" : "99px")};
  color: ${colors.white};
  background: ${({ $selected }) =>
    $selected ? colors.mintDark : "transparent"};

  svg {
    width: 12px;
    stroke-width: 3;
  }
`;

export const TextFieldGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px 16px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

export const Field = styled.div`
  min-width: 0;
  grid-column: ${({ $wide }) => ($wide ? "1 / -1" : "auto")};
`;

export const FieldLabel = styled.label`
  display: flex;
  gap: 5px;
  align-items: center;
  margin-bottom: 8px;
  color: ${colors.text};
  font-size: 13px;
  font-weight: 750;
  line-height: 19px;
`;

const controlStyle = css`
  ${focusRing};
  width: 100%;
  min-height: 48px;
  border: 1px solid ${({ $hasError }) => ($hasError ? colors.danger : colors.border)};
  border-radius: 12px;
  outline: 0;
  color: ${colors.text};
  background: ${colors.white};
  font-size: 13px;
  font-weight: 600;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;

  &:focus {
    border-color: ${({ $hasError }) =>
      $hasError ? colors.danger : colors.mint};
    box-shadow: 0 0 0 3px
      ${({ $hasError }) =>
        $hasError ? "rgba(186, 26, 26, 0.09)" : "rgba(0, 167, 159, 0.09)"};
  }

  &::placeholder {
    color: #a7a9af;
    font-weight: 500;
  }

  &:disabled {
    color: ${colors.muted};
    background: #f5f6f7;
    cursor: not-allowed;
  }
`;

export const TextInput = styled.input`
  ${controlStyle};
  padding: 0 13px;
`;

export const Select = styled.select`
  ${controlStyle};
  padding: 0 38px 0 13px;
  appearance: none;
  cursor: pointer;
`;

export const SelectWrap = styled.div`
  position: relative;

  svg {
    position: absolute;
    top: 50%;
    right: 13px;
    color: ${colors.muted};
    pointer-events: none;
    transform: translateY(-50%);
  }
`;

export const InputHelper = styled.span`
  display: block;
  margin-top: 6px;
  color: ${colors.muted};
  font-size: 11px;
  line-height: 17px;
`;

export const ErrorText = styled.span`
  display: flex;
  gap: 5px;
  align-items: center;
  margin-top: 7px;
  color: ${colors.danger};
  font-size: 11px;
  font-weight: 650;
  line-height: 17px;
`;

export const QuickSelectRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin: -3px 0 10px;
`;

export const QuickSelectButton = styled.button`
  ${buttonReset};
  ${focusRing};
  min-height: 34px;
  padding: 0 10px;
  border-radius: 9px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 11px;
  font-weight: 750;
`;

export const MbtiGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;

  @media (max-width: 700px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
`;

export const MbtiAxis = styled.div`
  padding: 12px;
  border: 1px solid ${colors.border};
  border-radius: 13px;
  background: #fbfbfc;
`;

export const MbtiAxisLabel = styled.span`
  display: block;
  margin-bottom: 8px;
  color: ${colors.muted};
  font-size: 10px;
  font-weight: 700;
  line-height: 15px;
  text-align: center;
`;

export const MbtiChoices = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 5px;
`;

export const MbtiButton = styled.button`
  ${buttonReset};
  ${focusRing};
  min-height: 42px;
  border: 1px solid
    ${({ $selected }) => ($selected ? colors.mint : colors.border)};
  border-radius: 10px;
  color: ${({ $selected }) => ($selected ? colors.white : colors.textSoft)};
  background: ${({ $selected }) => ($selected ? colors.mintDark : colors.white)};
  font-size: 16px;
  font-weight: 800;
  transition:
    border-color 160ms ease,
    color 160ms ease,
    background-color 160ms ease;
`;

export const MbtiResult = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
  color: ${colors.muted};
  font-size: 11px;

  strong {
    display: inline-flex;
    align-items: center;
    min-height: 29px;
    padding: 0 10px;
    border-radius: 8px;
    color: ${colors.navy};
    background: ${colors.navySoft};
    font-size: 12px;
    letter-spacing: 0.08em;
  }
`;

export const TextareaWrap = styled.div`
  position: relative;
`;

export const Textarea = styled.textarea`
  ${controlStyle};
  min-height: 132px;
  padding: 13px 14px 32px;
  resize: vertical;
  line-height: 21px;
`;

export const CharacterCount = styled.span`
  position: absolute;
  right: 13px;
  bottom: 10px;
  color: ${colors.muted};
  font-size: 10px;
`;

export const ValidationBanner = styled.div`
  display: flex;
  gap: 9px;
  align-items: flex-start;
  padding: 12px 13px;
  border: 1px solid rgba(186, 26, 26, 0.18);
  border-radius: 11px;
  color: #8f1414;
  background: ${colors.dangerSoft};
  font-size: 12px;
  font-weight: 650;
  line-height: 18px;

  svg {
    flex: 0 0 auto;
    margin-top: 1px;
  }
`;

export const FormFooter = styled.footer`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 20px 30px;
  border-top: 1px solid ${colors.border};
  background: #fcfcfd;

  @media (max-width: 640px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    padding: 16px 20px;
  }
`;

export const FooterNote = styled.span`
  display: inline-flex;
  gap: 6px;
  align-items: center;
  color: ${colors.muted};
  font-size: 11px;
  line-height: 16px;

  svg {
    color: ${colors.mintDark};
  }

  @media (max-width: 640px) {
    display: none;
  }
`;

export const ActionGroup = styled.div`
  display: flex;
  gap: 9px;
  justify-content: flex-end;

  @media (max-width: 640px) {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
`;

const actionButton = css`
  ${buttonReset};
  ${focusRing};
  display: inline-flex;
  gap: 7px;
  align-items: center;
  justify-content: center;
  min-width: 106px;
  min-height: 46px;
  padding: 0 17px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 750;
`;

export const BackButton = styled.button`
  ${actionButton};
  border: 1px solid ${colors.border};
  color: ${colors.textSoft};
  background: ${colors.white};

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
`;

export const NextButton = styled.button`
  ${actionButton};
  border: 1px solid ${colors.navy};
  color: ${colors.white};
  background: ${colors.navy};
  box-shadow: 0 5px 12px rgba(12, 50, 115, 0.15);
  transition:
    background-color 160ms ease,
    transform 160ms ease,
    box-shadow 160ms ease;

  @media (hover: hover) {
    &:hover {
      background: ${colors.navyDark};
      transform: translateY(-1px);
      box-shadow: 0 8px 17px rgba(12, 50, 115, 0.19);
    }
  }
`;

export const ReviewBody = styled.div`
  display: grid;
  gap: 16px;
`;

export const ReviewCard = styled.article`
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 14px;
`;

export const ReviewCardHeader = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 50px;
  padding: 0 15px;
  border-bottom: 1px solid ${colors.border};
  background: #f8f9fb;

  h3 {
    margin: 0;
    color: ${colors.navyDark};
    font-size: 13px;
    line-height: 20px;
  }
`;

export const EditButton = styled.button`
  ${buttonReset};
  ${focusRing};
  min-height: 34px;
  padding: 0 10px;
  border-radius: 9px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 11px;
  font-weight: 750;
`;

export const ReviewList = styled.dl`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  padding: 8px 15px 14px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
  }
`;

export const ReviewItem = styled.div`
  min-width: 0;
  padding: 10px 8px 7px 0;

  dt {
    color: ${colors.muted};
    font-size: 10px;
    font-weight: 700;
    line-height: 15px;
  }

  dd {
    margin: 3px 0 0;
    overflow-wrap: anywhere;
    color: ${colors.text};
    font-size: 12px;
    font-weight: 650;
    line-height: 19px;
  }
`;

export const CompletePanel = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 610px;
  padding: 86px 30px 54px;
  text-align: center;

  @media (max-width: 640px) {
    min-height: 530px;
    padding: 62px 22px 42px;
  }
`;

export const CompleteIcon = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 24px;
  color: ${colors.white};
  background: linear-gradient(145deg, ${colors.mintDark}, ${colors.mint});
  box-shadow: 0 14px 28px rgba(0, 106, 101, 0.2);

  svg {
    width: 38px;
    height: 38px;
    stroke-width: 2.5;
  }
`;

export const CompleteEyebrow = styled.span`
  margin-top: 25px;
  color: ${colors.mintDark};
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.1em;
`;

export const CompleteTitle = styled.h2`
  margin: 8px 0 0;
  color: ${colors.navyDark};
  font-size: 28px;
  line-height: 38px;
  letter-spacing: -0.035em;
`;

export const CompleteDescription = styled.p`
  max-width: 470px;
  margin: 11px 0 0;
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 23px;
  word-break: keep-all;
`;

export const CompleteSummary = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  margin-top: 24px;

  span {
    display: inline-flex;
    align-items: center;
    min-height: 32px;
    padding: 0 11px;
    border-radius: 99px;
    color: ${colors.navy};
    background: ${colors.navySoft};
    font-size: 11px;
    font-weight: 700;
  }
`;

export const CompleteActions = styled.div`
  display: flex;
  gap: 9px;
  margin-top: 30px;

  @media (max-width: 480px) {
    width: 100%;
    flex-direction: column;
  }
`;

export const RestartButton = styled.button`
  ${actionButton};
  border: 1px solid ${colors.border};
  color: ${colors.textSoft};
  background: ${colors.white};
`;

export const BrowseLink = styled.a`
  ${actionButton};
  border: 1px solid ${colors.navy};
  color: ${colors.white};
  background: ${colors.navy};
  text-decoration: none;
`;

export const ScreenReaderOnly = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
`;
