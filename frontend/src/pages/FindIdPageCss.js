import styled, { css } from "styled-components";
import { Link } from "react-router-dom";

const focusRing = css`
  outline: 3px solid rgba(0, 167, 159, 0.2);
  outline-offset: 2px;
`;

const reduceMotion = css`
  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

export const Page = styled.main`
  width: 100%;
  min-height: calc(100vh - 144px);
  padding: 64px 20px 80px;
  box-sizing: border-box;
  background: #faf9f6;
  color: #1a1c1c;
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

  @media (max-width: 640px) {
    min-height: calc(100vh - 112px);
    padding: 32px 16px 56px;
  }
`;

export const PageInner = styled.div`
  width: min(100%, 720px);
  margin: 0 auto;
`;

export const BackLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  border-radius: 8px;
  color: #444650;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  text-decoration: none;
  transition: color 160ms ease;

  svg {
    width: 18px;
    height: 18px;
  }

  &:hover {
    color: #3478f6;
  }

  &:focus-visible {
    ${focusRing}
  }

  ${reduceMotion}
`;

export const Card = styled.section`
  overflow: hidden;
  padding: 40px;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 640px) {
    padding: 28px 20px 24px;
  }
`;

export const Intro = styled.header`
  margin-bottom: 32px;
`;

export const Eyebrow = styled.p`
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  color: #006a65;
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
  letter-spacing: 0.08em;

  svg {
    width: 16px;
    height: 16px;
  }
`;

export const Title = styled.h1`
  margin: 0 0 12px;
  color: #001d4f;
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.01em;

  @media (max-width: 640px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const Description = styled.p`
  margin: 0;
  color: #747781;
  font-size: 15px;
  line-height: 24px;
`;

export const Tabs = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border-radius: 14px;
  background: #f3f3f3;
`;

export const Tab = styled.button`
  display: inline-flex;
  min-width: 0;
  min-height: 48px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border: 0;
  border-radius: 10px;
  background: ${({ $active }) => ($active ? "#ffffff" : "transparent")};
  box-shadow: ${({ $active }) =>
        $active ? "0 2px 8px rgba(12, 50, 115, 0.08)" : "none"};
  color: ${({ $active }) => ($active ? "#0c3273" : "#747781")};
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  cursor: pointer;
  transition:
    background 160ms ease,
    box-shadow 160ms ease,
    color 160ms ease;

  svg {
    width: 18px;
    height: 18px;
    flex: 0 0 auto;
  }

  &:hover {
    color: #0c3273;
  }

  &:focus-visible {
    ${focusRing}
  }

  @media (max-width: 420px) {
    gap: 6px;
    padding-inline: 8px;
    font-size: 13px;
  }

  ${reduceMotion}
`;

export const Panel = styled.div`
  padding-top: 32px;

  @media (max-width: 640px) {
    padding-top: 28px;
  }
`;

export const SectionHeading = styled.h2`
  margin: 0 0 6px;
  color: #1a1c1c;
  font-size: 18px;
  font-weight: 700;
  line-height: 28px;
  letter-spacing: -0.01em;
`;

export const SectionCopy = styled.p`
  margin: 0 0 24px;
  color: #747781;
  font-size: 14px;
  line-height: 21px;
`;

export const Form = styled.form`
  display: grid;
  gap: 20px;
`;

export const Field = styled.div`
  display: grid;
  gap: 8px;
`;

export const Label = styled.label`
  color: #444650;
  font-size: 14px;
  font-weight: 700;
  line-height: 18px;
  letter-spacing: 0.01em;
`;

export const InputWrap = styled.div`
  position: relative;
`;

export const InputIcon = styled.span`
  position: absolute;
  top: 50%;
  left: 16px;
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  color: #8e8e93;
  pointer-events: none;
  transform: translateY(-50%);

  svg {
    width: 20px;
    height: 20px;
  }
`;

export const Input = styled.input`
  width: 100%;
  min-height: 52px;
  padding: 14px 16px 14px 48px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: #ffffff;
  color: #1a1c1c;
  font: inherit;
  font-size: 16px;
  line-height: 22px;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;

  &::placeholder {
    color: #a0a1a7;
  }

  &:hover {
    border-color: #c4c6d2;
  }

  &:focus {
    border-color: #00a79f;
    outline: none;
    box-shadow: 0 0 0 4px rgba(0, 167, 159, 0.12);
  }

  ${reduceMotion}
`;

export const FieldHint = styled.span`
  color: #8e8e93;
  font-size: 12px;
  line-height: 18px;
`;

export const PrimaryButton = styled.button`
  display: inline-flex;
  width: 100%;
  min-height: 52px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 14px 20px;
  border: 1px solid #0c3273;
  border-radius: 12px;
  background: #0c3273;
  color: #ffffff;
  font: inherit;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(12, 50, 115, 0.14);
  transition:
    background 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;

  svg {
    width: 19px;
    height: 19px;
  }

  &:hover {
    background: #001d4f;
    box-shadow: 0 7px 16px rgba(12, 50, 115, 0.18);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }

  &:focus-visible {
    ${focusRing}
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;

    &:hover {
      transform: none;
    }
  }
`;

export const DemoNote = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 18px;
  padding: 12px 14px;
  border: 1px solid #d9e2ff;
  border-radius: 12px;
  background: #f6f8ff;
  color: #5e626c;
  font-size: 12px;
  line-height: 19px;

  strong {
    color: #244485;
    font-weight: 700;
  }

  @media (max-width: 480px) {
    display: grid;
    gap: 6px;
  }
`;

export const DemoBadge = styled.span`
  flex: 0 0 auto;
  padding: 3px 8px;
  border-radius: 999px;
  background: #d9e2ff;
  color: #244485;
  font-size: 10px;
  font-weight: 700;
  line-height: 14px;
  letter-spacing: 0.04em;
`;

export const ResultPanel = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-top: 28px;
  padding: 20px;
  border: 1px solid
    ${({ $tone }) => ($tone === "success" ? "#b8e4df" : "#f0cfcb")};
  border-radius: 16px;
  background: ${({ $tone }) => ($tone === "success" ? "#f1fbfa" : "#fff7f5")};

  @media (max-width: 480px) {
    gap: 12px;
    padding: 18px 16px;
  }
`;

export const ResultIcon = styled.span`
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  border-radius: 50%;
  background: ${({ $tone }) => ($tone === "success" ? "#d8f4f1" : "#ffdad6")};
  color: ${({ $tone }) => ($tone === "success" ? "#006a65" : "#ba1a1a")};

  svg {
    width: 21px;
    height: 21px;
  }
`;

export const ResultContent = styled.div`
  min-width: 0;
  flex: 1;
`;

export const ResultTitle = styled.h3`
  margin: 1px 0 5px;
  color: #1a1c1c;
  font-size: 16px;
  font-weight: 700;
  line-height: 23px;
`;

export const ResultDescription = styled.p`
  overflow-wrap: anywhere;
  margin: 0;
  color: #656872;
  font-size: 13px;
  line-height: 20px;
`;

export const FoundId = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  padding: 13px 15px;
  border-radius: 10px;
  background: #ffffff;

  span {
    color: #747781;
    font-size: 12px;
    font-weight: 600;
  }

  strong {
    color: #001d4f;
    font-size: 17px;
    line-height: 22px;
    letter-spacing: 0.02em;
  }
`;

export const ResultActions = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 14px;

  @media (max-width: 420px) {
    align-items: stretch;
    flex-direction: column;
    gap: 8px;
  }
`;

export const LoginButton = styled(Link)`
  display: inline-flex;
  min-height: 40px;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 9px 16px;
  border-radius: 10px;
  background: #0c3273;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  text-decoration: none;

  &:hover {
    background: #001d4f;
  }

  &:focus-visible {
    ${focusRing}
  }
`;

export const PasswordLink = styled(Link)`
  display: inline-flex;
  min-height: 40px;
  flex: 1;
  align-items: center;
  justify-content: center;
  padding: 8px 15px;
  border: 1.5px solid #00a79f;
  border-radius: 10px;
  color: #007d77;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  text-decoration: none;

  &:hover {
    background: #edf9f8;
  }

  &:focus-visible {
    ${focusRing}
  }
`;

export const RetryButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 14px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #3478f6;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  cursor: pointer;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover {
    color: #244485;
  }

  &:focus-visible {
    ${focusRing}
  }
`;

export const CardFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 32px -40px -40px;
  padding: 20px 24px;
  border-top: 1px solid #eeeeee;
  background: #fafafa;
  color: #747781;
  font-size: 13px;
  line-height: 20px;
  text-align: center;

  @media (max-width: 640px) {
    margin: 28px -20px -24px;
    padding: 18px 16px;
  }

  @media (max-width: 460px) {
    align-items: center;
    flex-direction: column;
    gap: 3px;
  }
`;

export const FooterLink = styled(Link)`
  border-radius: 5px;
  color: #3478f6;
  font-weight: 700;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  &:focus-visible {
    ${focusRing}
  }
`;