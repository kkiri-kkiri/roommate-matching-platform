import styled, { createGlobalStyle } from "styled-components";
import { Link } from "react-router-dom";

export const PageGlobalStyle = createGlobalStyle`
  html,
  body,
  #root {
    min-height: 100%;
  }

  body {
    margin: 0;
    background: #faf9f6;
  }
`;

export const Page = styled.main`
  min-height: 100vh;
  min-height: 100dvh;
  padding: 64px 20px 80px;
  background: #faf9f6;
  color: #1a1c1c;
  font-family:
    "Plus Jakarta Sans",
    Pretendard,
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;

  &,
  & * {
    box-sizing: border-box;
  }

  @media (max-width: 600px) {
    padding: 32px 16px 56px;
  }
`;

export const PageInner = styled.div`
  width: min(100%, 480px);
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
    outline: 3px solid rgba(0, 167, 159, 0.2);
    outline-offset: 2px;
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

export const LoginCard = styled.section`
  width: 100%;
  padding: 40px;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 600px) {
    padding: 32px 20px;
  }
`;

export const Header = styled.header`
  margin-bottom: 32px;
  text-align: center;
`;

export const Eyebrow = styled.p`
  margin: 0 0 8px;
  color: #007c76;
  font-size: 12px;
  font-weight: 700;
  line-height: 16px;
  letter-spacing: 0.1em;
`;

export const Title = styled.h1`
  margin: 0;
  color: #001d4f;
  font-size: 28px;
  font-weight: 700;
  line-height: 36px;
  letter-spacing: -0.01em;

  @media (max-width: 600px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const Description = styled.p`
  margin: 12px 0 0;
  color: #444650;
  font-size: 15px;
  font-weight: 400;
  line-height: 24px;
`;

export const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

export const FieldGroup = styled.div`
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

export const Label = styled.label`
  color: #1a1c1c;
  font-size: 14px;
  font-weight: 600;
  line-height: 16px;
  letter-spacing: 0.02em;
`;

export const Input = styled.input`
  width: 100%;
  height: 52px;
  padding: 0 ${({ $hasAction }) => ($hasAction ? "76px" : "16px")} 0 16px;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  outline: none;
  background: #ffffff;
  color: #1a1c1c;
  font: inherit;
  font-size: 16px;
  line-height: 24px;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    background-color 160ms ease;

  &::placeholder {
    color: #8e8e93;
  }

  &:hover {
    border-color: #c4c6d2;
  }

  &:focus {
    border-color: #00a79f;
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.14);
  }

  &[aria-invalid="true"] {
    border-color: #ba1a1a;
  }
`;

export const PasswordField = styled.div`
  position: relative;
`;

export const VisibilityButton = styled.button`
  position: absolute;
  top: 50%;
  right: 12px;
  min-width: 48px;
  padding: 8px 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #3478f6;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 16px;
  cursor: pointer;
  transform: translateY(-50%);

  &:hover {
    background: #f3f6ff;
  }

  &:focus-visible {
    outline: 2px solid #00a79f;
    outline-offset: 1px;
  }
`;

export const OptionRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  @media (max-width: 420px) {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
`;

export const CheckLabel = styled.label`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #444650;
  font-size: 14px;
  line-height: 20px;
  cursor: pointer;
`;

export const Checkbox = styled.input`
  width: 18px;
  height: 18px;
  margin: 0;
  flex: 0 0 auto;
  accent-color: #0c3273;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid #00a79f;
    outline-offset: 2px;
  }
`;

export const UtilityLinks = styled.nav`
  display: flex;
  align-items: center;
  gap: 10px;
`;

export const UtilityLink = styled(Link)`
  border-radius: 4px;
  color: #444650;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  text-decoration: none;

  &:hover {
    color: #3478f6;
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  &:focus-visible {
    outline: 2px solid #00a79f;
    outline-offset: 3px;
  }
`;

export const LinkDivider = styled.span`
  width: 1px;
  height: 12px;
  background: #e2e2e4;
`;

export const Feedback = styled.p`
  margin: -4px 0 0;
  padding: 12px 14px;
  border: 1px solid
    ${({ $tone }) => ($tone === "error" ? "#ffb4ab" : "#9edbd7")};
  border-radius: 12px;
  background: ${({ $tone }) => ($tone === "error" ? "#fff1ef" : "#eefaf8")};
  color: ${({ $tone }) => ($tone === "error" ? "#93000a" : "#005b56")};
  font-size: 13px;
  line-height: 20px;
`;

export const SubmitButton = styled.button`
  width: 100%;
  min-height: 52px;
  padding: 14px 20px;
  border: 1px solid #0c3273;
  border-radius: 12px;
  background: #0c3273;
  color: #ffffff;
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  line-height: 22px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.12);
  transition:
    background-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;

  &:hover {
    background: #001d4f;
    box-shadow: 0 8px 20px rgba(12, 50, 115, 0.16);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }

  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.45);
    outline-offset: 3px;
  }
`;

export const SignupPrompt = styled.div`
  margin-top: 28px;
  padding-top: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-top: 1px solid #eeeeee;
  font-size: 14px;
  line-height: 20px;
`;

export const SignupText = styled.span`
  color: #747781;
`;

export const SignupLink = styled(Link)`
  border-radius: 4px;
  color: #3478f6;
  font-weight: 700;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  &:focus-visible {
    outline: 2px solid #00a79f;
    outline-offset: 3px;
  }
`;
