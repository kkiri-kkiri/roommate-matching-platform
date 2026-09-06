import styled, { css } from "styled-components";

const colors = {
  navy: "#0c3273",
  navyDark: "#001d4f",
  mint: "#00a79f",
  mintDark: "#006a65",
  mintSoft: "#e3f8f5",
  text: "#1a1c1c",
  secondary: "#444650",
  muted: "#747781",
  border: "#e2e2e4",
  white: "#ffffff",
  error: "#ba1a1a",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.3);
    outline-offset: 3px;
  }
`;

export const Detail = styled.section`
  width: 100%;
  max-width: 880px;
  margin: 0 auto;
  color: ${colors.text};

  button,
  textarea,
  input {
    font-family: inherit;
  }
`;

export const BackLink = styled.a`
  ${focusRing};
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  border-radius: 8px;
  color: ${colors.secondary};
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;

  &:hover {
    color: ${colors.navy};
  }
`;

export const Notice = styled.p`
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin: 0 0 20px;
  padding: 14px 16px;
  border: 1px solid #b6e6df;
  border-radius: 12px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 14px;
  line-height: 22px;

  svg {
    flex-shrink: 0;
    margin-top: 2px;
  }
`;

export const Article = styled.article`
  padding: 40px;
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 640px) {
    padding: 24px 20px;
  }
`;

export const ArticleTop = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
`;

const categoryTone = {
  자유게시판: css`
    color: #244485;
    background: #edf2fc;
  `,
  나눔게시판: css`
    color: ${colors.mintDark};
    background: #e7f8f6;
  `,
  택배게시판: css`
    color: #875d22;
    background: #fbf2df;
  `,
  민원게시판: css`
    color: #705390;
    background: #f2edf8;
  `,
};

export const Category = styled.span`
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 11px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
  ${({ $category }) => categoryTone[$category] || categoryTone.자유게시판};
`;

export const ActionGroup = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
`;

export const TextButton = styled.button`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 40px;
  padding: 8px;
  border: 0;
  border-radius: 8px;
  color: ${({ $danger }) => ($danger ? colors.error : colors.muted)};
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: #f3f3f3;
  }

  &:disabled {
    color: ${colors.muted};
    cursor: default;
  }
`;

export const Title = styled.h1`
  margin: 18px 0 24px;
  color: ${colors.navyDark};
  font-size: 28px;
  font-weight: 700;
  line-height: 40px;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
  word-break: keep-all;

  @media (max-width: 640px) {
    font-size: 24px;
    line-height: 34px;
  }
`;

export const AuthorRow = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 28px;
  border-bottom: 1px solid ${colors.border};
`;

export const Avatar = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: ${({ $small }) => ($small ? "34px" : "42px")};
  height: ${({ $small }) => ($small ? "34px" : "42px")};
  border-radius: 12px;
  color: ${({ $mine }) => ($mine ? colors.mintDark : colors.navy)};
  background: ${({ $mine }) => ($mine ? colors.mintSoft : "#edf2ff")};
  font-size: 14px;
  font-weight: 700;
`;

export const AuthorInfo = styled.div`
  min-width: 0;

  > strong {
    color: ${colors.secondary};
    font-size: 14px;
    line-height: 22px;
    overflow-wrap: anywhere;
  }
`;

export const Metadata = styled.div`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px 12px;
  margin-top: 4px;
  color: ${colors.muted};
  font-size: 12px;
  line-height: 18px;

  > span {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
`;

export const Content = styled.div`
  padding: 32px 0;
  color: ${colors.secondary};
  font-size: 16px;
  line-height: 30px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
`;

export const Attachments = styled.div`
  display: grid;
  gap: 16px;
  margin-bottom: 32px;

  figure {
    margin: 0;
  }

  img {
    display: block;
    width: 100%;
    max-height: 560px;
    border: 1px solid ${colors.border};
    border-radius: 12px;
    object-fit: contain;
    background: #f6f6f6;
  }

  figcaption {
    margin-top: 8px;
    color: ${colors.muted};
    font-size: 12px;
    line-height: 18px;
    overflow-wrap: anywhere;
  }
`;

export const Reactions = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding-top: 24px;
  border-top: 1px solid ${colors.border};
`;

export const LikeButton = styled.button`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 18px;
  border: 1.5px solid ${colors.mint};
  border-radius: 12px;
  color: ${colors.mintDark};
  background: ${({ $active }) => ($active ? colors.mintSoft : colors.white)};
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;

  svg {
    fill: ${({ $active }) => ($active ? "currentColor" : "none")};
  }

  &:hover {
    background: ${colors.mintSoft};
  }
`;

export const CommentsCard = styled.section`
  margin-top: 24px;
  padding: 32px 40px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);

  @media (max-width: 640px) {
    padding: 24px 20px;
  }
`;

export const CommentsHeading = styled.h2`
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;

  span {
    color: ${colors.mintDark};
  }
`;

export const CommentsHint = styled.p`
  margin: 7px 0 24px;
  color: ${colors.muted};
  font-size: 14px;
  line-height: 22px;
`;

export const CommentForm = styled.form`
  margin: ${({ $compact }) => ($compact ? "12px 0 4px" : "24px 0 0")};
  padding: 16px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  background: ${({ $compact }) => ($compact ? colors.white : "#fafbfc")};

  > label {
    display: block;
    margin-bottom: 10px;
    color: ${colors.secondary};
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
  }
`;

export const Textarea = styled.textarea`
  display: block;
  width: 100%;
  min-height: ${({ $compact }) => ($compact ? "88px" : "104px")};
  padding: 12px 14px;
  resize: vertical;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  outline: none;
  color: ${colors.text};
  background: ${colors.white};
  font-size: 14px;
  line-height: 24px;

  &::placeholder {
    color: ${colors.muted};
  }

  &:focus {
    border-color: ${colors.mint};
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.12);
  }

  &:disabled {
    background: #f6f6f6;
  }
`;

export const FormFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;

  > small {
    color: ${colors.muted};
    font-size: 12px;
    line-height: 18px;
  }

  > div {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
  }
`;

export const PrimaryButton = styled.button`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid ${({ $danger }) => ($danger ? colors.error : colors.navy)};
  border-radius: 12px;
  color: ${colors.white};
  background: ${({ $danger }) => ($danger ? colors.error : colors.navy)};
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;

  &:hover:not(:disabled) {
    filter: brightness(0.92);
  }

  &:disabled {
    border-color: #e2e2e4;
    color: ${colors.muted};
    background: #eeeeee;
    cursor: not-allowed;
  }
`;

export const SecondaryButton = styled(PrimaryButton)`
  border: 1px solid ${colors.border};
  color: ${colors.secondary};
  background: ${colors.white};
`;

export const CommentList = styled.ol`
  margin: 24px 0 0;
  padding: 0;
  list-style: none;
`;

export const CommentThread = styled.li`
  padding: 24px 0;
  border-top: 1px solid ${colors.border};

  &:last-child {
    padding-bottom: 0;
  }
`;

export const CommentItem = styled.div`
  min-width: 0;
  scroll-margin-top: 24px;
`;

export const CommentHeader = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;

  > div {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 5px 8px;
    min-width: 0;
  }

  strong {
    color: ${colors.secondary};
    font-size: 14px;
    line-height: 20px;
    overflow-wrap: anywhere;
  }

  time {
    color: ${colors.muted};
    font-size: 12px;
    line-height: 18px;
  }
`;

export const AuthorBadge = styled.span`
  padding: 2px 6px;
  border-radius: 5px;
  color: ${colors.mintDark};
  background: ${colors.mintSoft};
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
`;

export const CommentText = styled.p`
  margin: 12px 0 4px;
  color: ${colors.secondary};
  font-size: 14px;
  line-height: 24px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
`;

export const ReplyList = styled.ol`
  display: grid;
  gap: 20px;
  margin: 16px 0 0 16px;
  padding: 20px;
  border-left: 2px solid #dcece9;
  border-radius: 0 12px 12px 0;
  background: #f8faf9;
  list-style: none;

  @media (max-width: 480px) {
    margin-left: 4px;
    padding: 16px 12px;
  }
`;

export const EmptyComments = styled.div`
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 8px;
  margin-top: 24px;
  padding: 32px 16px;
  border-top: 1px solid ${colors.border};
  color: ${colors.muted};
  text-align: center;

  svg {
    margin-bottom: 4px;
    color: ${colors.mintDark};
  }

  strong {
    color: ${colors.secondary};
    font-size: 15px;
    line-height: 22px;
  }

  p {
    margin: 0;
    font-size: 14px;
    line-height: 22px;
  }
`;

export const BottomNav = styled.div`
  display: flex;
  justify-content: center;
  padding-top: 24px;
`;

export const Dialog = styled.dialog`
  width: calc(100% - 32px);
  max-width: 460px;
  max-height: calc(100dvh - 48px);
  margin: auto;
  padding: 28px;
  overflow-y: auto;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  color: ${colors.text};
  background: ${colors.white};
  box-shadow: 0 8px 20px rgba(12, 50, 115, 0.08);

  &::backdrop {
    background: rgba(12, 50, 115, 0.4);
  }

  @media (max-width: 480px) {
    padding: 24px 20px;
  }
`;

export const DialogHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;

  h2 {
    margin: 0;
    color: ${colors.navyDark};
    font-size: 20px;
    font-weight: 600;
    line-height: 28px;
  }
`;

export const CloseButton = styled.button`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  color: ${colors.muted};
  background: #f6f6f6;
  cursor: pointer;
`;

export const DialogDescription = styled.p`
  margin: 0 0 20px;
  color: ${colors.secondary};
  font-size: 14px;
  line-height: 24px;
  white-space: pre-line;
`;

export const ReasonList = styled.fieldset`
  display: grid;
  gap: 8px;
  margin: 0 0 16px;
  padding: 0;
  border: 0;

  legend {
    margin-bottom: 10px;
    color: ${colors.secondary};
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
  }

  label {
    display: flex;
    align-items: center;
    gap: 10px;
    min-height: 44px;
    padding: 10px 12px;
    border: 1px solid ${colors.border};
    border-radius: 10px;
    color: ${colors.secondary};
    font-size: 14px;
    line-height: 20px;
    cursor: pointer;
  }

  label:has(input:checked) {
    border-color: ${colors.mint};
    color: ${colors.mintDark};
    background: ${colors.mintSoft};
  }

  input {
    ${focusRing};
    width: 16px;
    height: 16px;
    margin: 0;
    accent-color: ${colors.mintDark};
  }
`;

export const OtherReason = styled.div`
  margin-bottom: 20px;

  label {
    display: block;
    margin-bottom: 8px;
    color: ${colors.secondary};
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
  }
`;

export const DialogActions = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
`;
