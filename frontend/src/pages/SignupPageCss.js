import styled from "styled-components";
import { Link } from "react-router-dom";

export const Page = styled.main`
  min-height: calc(100vh - 144px);
  min-height: calc(100dvh - 144px);
  padding: 64px 20px;
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

  @media (max-width: 640px) {
    padding: 40px 16px;
  }
`;

export const SignupCard = styled.section`
  width: min(100%, 760px);
  margin: 0 auto;
  padding: 48px;
  border: 1px solid #e2e2e4;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 640px) {
    padding: 32px 20px;
  }
`;

export const Header = styled.header`
  margin-bottom: 40px;
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

  @media (max-width: 640px) {
    font-size: 24px;
    line-height: 32px;
  }
`;

export const Description = styled.p`
  max-width: 520px;
  margin: 12px auto 0;
  color: #444650;
  font-size: 15px;
  line-height: 24px;
`;

export const Form = styled.form`
  display: flex;
  flex-direction: column;
  gap: 32px;
`;

export const FormSection = styled.section`
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

export const SectionHeader = styled.div`
  display: flex;
  align-items: flex-start;
  gap: 12px;
`;

export const SectionNumber = styled.span`
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  border-radius: 9999px;
  background: #dff7f4;
  color: #006a65;
  font-size: 13px;
  font-weight: 700;
  line-height: 16px;
`;

export const SectionTitle = styled.h2`
  margin: 0;
  color: #001d4f;
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
`;

export const SectionHeadingGroup = styled.div`
  min-width: 0;
`;

export const SectionDescription = styled.p`
  margin: 4px 0 0;
  color: #747781;
  font-size: 13px;
  line-height: 20px;
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
  min-width: 0;
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

  &:hover:not(:read-only) {
    border-color: #c4c6d2;
  }

  &:focus {
    border-color: #00a79f;
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.14);
  }

  &:read-only {
    background: #f3f3f3;
    color: #747781;
  }
`;

export const EmailInputGroup = styled.div`
  min-width: 0;
  height: 52px;
  display: flex;
  flex: 1 1 auto;
  align-items: stretch;
  overflow: hidden;
  border: 1px solid #e2e2e4;
  border-radius: 12px;
  background: ${({ $readOnly }) => ($readOnly ? "#f3f3f3" : "#ffffff")};
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    background-color 160ms ease;

  &:hover {
    border-color: ${({ $readOnly }) => ($readOnly ? "#e2e2e4" : "#c4c6d2")};
  }

  &:focus-within {
    border-color: #00a79f;
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.14);
  }
`;

export const EmailLocalInput = styled.input`
  min-width: 0;
  padding: 0 14px;
  flex: 1 1 auto;
  border: 0;
  outline: 0;
  background: transparent;
  color: #1a1c1c;
  font: inherit;
  font-size: 16px;
  line-height: 24px;

  &::placeholder {
    color: #8e8e93;
  }

  &:read-only {
    color: #747781;
  }
`;

export const EmailDomain = styled.span`
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  border-left: 1px solid #e2e2e4;
  background: #f3f3f3;
  color: #444650;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;

  @media (max-width: 480px) {
    padding: 0 10px;
    font-size: 13px;
  }
`;

export const HelperText = styled.p`
  margin: 0;
  color: #8e8e93;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
`;

export const TwoColumnFields = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 640px) {
    grid-template-columns: 1fr;
    gap: 20px;
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

export const FieldFeedback = styled.p`
  min-height: 18px;
  margin: 0;
  color: ${({ $tone }) => {
    if ($tone === "success") return "#007c76";
    if ($tone === "error") return "#ba1a1a";
    return "#8e8e93";
  }};
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
`;

export const Divider = styled.hr`
  width: 100%;
  height: 1px;
  margin: 0;
  border: 0;
  background: #eeeeee;
`;

export const ActionRow = styled.div`
  display: flex;
  align-items: stretch;
  gap: 12px;

  ${Input} {
    flex: 1 1 auto;
  }

  @media (max-width: 480px) {
    gap: 8px;
  }
`;

export const OutlineButton = styled.button`
  min-width: 132px;
  min-height: 52px;
  padding: 12px 16px;
  flex: 0 0 auto;
  border: 1.5px solid #00a79f;
  border-radius: 12px;
  background: #ffffff;
  color: #007c76;
  font: inherit;
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  cursor: pointer;
  transition:
    background-color 160ms ease,
    border-color 160ms ease,
    color 160ms ease;

  &:hover:not(:disabled) {
    background: #eefaf8;
  }

  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.24);
    outline-offset: 2px;
  }

  &:disabled {
    border-color: #c4c6d2;
    background: #f3f3f3;
    color: #747781;
    cursor: not-allowed;
  }

  @media (max-width: 480px) {
    min-width: 112px;
    padding-right: 10px;
    padding-left: 10px;
    font-size: 13px;
  }
`;

export const VerificationStatus = styled.p`
  margin: -4px 0 0;
  padding: 12px 14px;
  border: 1px solid
    ${({ $tone }) => {
      if ($tone === "success") return "#9edbd7";
      if ($tone === "error") return "#ffb4ab";
      if ($tone === "info") return "#c7d7ff";
      return "#e2e2e4";
    }};
  border-radius: 12px;
  background: ${({ $tone }) => {
    if ($tone === "success") return "#eefaf8";
    if ($tone === "error") return "#fff1ef";
    if ($tone === "info") return "#f3f6ff";
    return "#f9f9f9";
  }};
  color: ${({ $tone }) => {
    if ($tone === "success") return "#005b56";
    if ($tone === "error") return "#93000a";
    if ($tone === "info") return "#244485";
    return "#747781";
  }};
  font-size: 13px;
  line-height: 20px;
`;

export const SubmitFeedback = styled.p`
  margin: 0;
  padding: 14px 16px;
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
  min-height: 54px;
  padding: 14px 20px;
  border: 1px solid #0c3273;
  border-radius: 12px;
  background: #0c3273;
  color: #ffffff;
  font: inherit;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
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

export const LoginPrompt = styled.p`
  margin: 28px 0 0;
  padding-top: 24px;
  border-top: 1px solid #eeeeee;
  color: #747781;
  font-size: 14px;
  line-height: 20px;
  text-align: center;
`;

export const LoginLink = styled(Link)`
  margin-left: 8px;
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
