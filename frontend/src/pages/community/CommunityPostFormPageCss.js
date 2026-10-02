import styled, { css } from "styled-components";

const colors = {
  navy: "#0c3273",
  navyDark: "#001d4f",
  mint: "#00a79f",
  mintDark: "#006a65",
  mintSoft: "#eefaf8",
  border: "#e2e2e4",
  text: "#1a1c1c",
  secondary: "#616570",
  muted: "#747781",
  error: "#ba1a1a",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.3);
    outline-offset: 3px;
  }
`;

const button = css`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 36px;
  padding: 7px 14px;
  border: 1px solid transparent;
  border-radius: 12px;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
  cursor: pointer;
  transition: background-color 160ms ease, border-color 160ms ease;

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;

export const Page = styled.section`
  width: min(100%, 760px);
  margin: 0 auto;
  color: ${colors.text};

  &, *, *::before, *::after {
    box-sizing: border-box;
  }
`;

export const Form = styled.form`
  overflow: hidden;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
`;

export const FormHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid ${colors.border};

  @media (max-width: 640px) {
    padding: 12px 16px;
  }
`;

export const FormTitle = styled.h1`
  margin: 0;
  color: ${colors.navyDark};
  font-size: 16px;
  font-weight: 700;
  line-height: 26px;
`;

export const RequiredNote = styled.span`
  flex-shrink: 0;
  color: ${colors.muted};
  font-size: 11px;
  line-height: 18px;

  > span {
    color: ${colors.mintDark};
  }
`;

export const Fields = styled.div`
  display: grid;
  gap: 16px;
  padding: 16px 20px;

  @media (max-width: 640px) {
    gap: 14px;
    padding: 14px 16px;
  }
`;

export const CategoryField = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
`;

export const Legend = styled.legend`
  margin: 0 0 8px;
  padding: 0;
  color: ${colors.text};
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
`;

export const RequiredMark = styled.span`
  margin-left: 3px;
  color: ${colors.mintDark};
`;

export const CategoryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;

  @media (max-width: 640px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
`;

export const CategoryChoice = styled.label`
  position: relative;
  display: block;
  min-width: 0;
  cursor: pointer;
`;

export const CategoryRadio = styled.input`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);

  &:checked + span {
    border-color: ${colors.mint};
    color: ${colors.mintDark};
    background: ${colors.mintSoft};
    box-shadow: inset 0 0 0 0.5px ${colors.mint};
  }

  &:focus-visible + span {
    outline: 3px solid rgba(0, 167, 159, 0.25);
    outline-offset: 3px;
  }
`;

export const CategoryCard = styled.span`
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-height: 88px;
  padding: 10px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  color: ${colors.secondary};
  background: #ffffff;

  > svg {
    margin-bottom: 3px;
  }

  > strong {
    font-size: 13px;
    font-weight: 700;
    line-height: 20px;
  }

  > small {
    color: ${colors.secondary};
    font-size: 11px;
    line-height: 17px;
    word-break: keep-all;
  }
`;

export const SelectedMark = styled.span`
  position: absolute;
  top: 13px;
  right: 13px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  color: #ffffff;
  background: ${colors.mint};
`;

export const Field = styled.div`
  min-width: 0;
`;

export const FieldHeading = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
`;

export const Label = styled.label`
  color: ${colors.text};
  font-size: 13px;
  font-weight: 700;
  line-height: 20px;
`;

export const Counter = styled.span`
  color: ${colors.muted};
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
`;

const input = css`
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  outline: none;
  color: ${colors.text};
  background: #ffffff;
  font: inherit;
  font-size: 14px;
  line-height: 24px;

  &::placeholder {
    color: ${colors.muted};
  }

  &:focus {
    border-color: ${colors.mint};
    box-shadow: 0 0 0 3px rgba(0, 167, 159, 0.1);
  }

  &[aria-invalid="true"] {
    border-color: ${colors.error};
  }

`;

export const Input = styled.input`
  ${input};
`;

export const Textarea = styled.textarea`
  ${input};
  min-height: 160px;
  resize: vertical;
  line-height: 24px;

  @media (max-width: 640px) {
    min-height: 140px;
  }
`;

export const Error = styled.p`
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 10px 0 0;
  color: ${colors.error};
  font-size: 12px;
  line-height: 20px;

  > svg {
    flex-shrink: 0;
    margin-top: 2px;
  }
`;

export const ImageField = styled.fieldset`
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
`;

export const Optional = styled.span`
  margin-left: 5px;
  color: ${colors.muted};
  font-size: 11px;
  font-weight: 400;
`;

export const UploadArea = styled.div`
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px dashed #cbd5d6;
  border-radius: 12px;
  background: #fbfcfc;

  @media (max-width: 720px) {
    flex-wrap: wrap;
  }

  @media (max-width: 420px) {
    gap: 12px;
    padding: 12px;
  }
`;

export const UploadIcon = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  color: ${colors.mintDark};
  background: #eaf4f2;
`;

export const UploadCopy = styled.div`
  flex: 1;
  min-width: 150px;

  > strong {
    color: ${colors.secondary};
    font-size: 13px;
    line-height: 20px;
  }

  > p {
    margin: 6px 0 0;
    color: ${colors.muted};
    font-size: 11px;
    line-height: 19px;
  }
`;

export const UploadButton = styled.button`
  ${button};
  flex-shrink: 0;
  padding: 7px 12px;
  border-color: ${colors.mint};
  color: ${colors.mintDark};
  background: #ffffff;
  font-size: 12px;

  &:hover:not(:disabled) {
    background: ${colors.mintSoft};
  }

  @media (max-width: 720px) {
    margin-left: 48px;
  }

  @media (max-width: 420px) {
    width: 100%;
    margin-left: 0;
  }
`;

export const FileInput = styled.input`
  display: none;
`;

export const ImageStatus = styled.p`
  margin: 12px 0 0;
  color: ${colors.mintDark};
  font-size: 12px;
  line-height: 20px;
`;

export const ImageCount = styled.p`
  margin: 20px 0 12px;
  color: ${colors.secondary};
  font-size: 11px;
  line-height: 18px;

  > strong {
    margin-left: 4px;
    color: ${colors.mintDark};
  }
`;

export const ImageGrid = styled.ul`
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;

  @media (max-width: 640px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  @media (max-width: 380px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
`;

export const ImageItem = styled.li`
  min-width: 0;
`;

export const ImagePreview = styled.div`
  position: relative;
  aspect-ratio: 1;
  border: 1px solid ${colors.border};
  border-radius: 12px;
  background: #faf9f6;

  > img {
    display: block;
    width: 100%;
    height: 100%;
    border-radius: inherit;
    object-fit: cover;
  }
`;

export const RemoveImage = styled.button`
  ${focusRing};
  position: absolute;
  top: 4px;
  right: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid ${colors.border};
  border-radius: 10px;
  color: ${colors.secondary};
  background: rgba(255, 255, 255, 0.95);
  cursor: pointer;

  &:hover:not(:disabled) {
    color: ${colors.error};
    background: #fff2f1;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

export const ImageName = styled.p`
  overflow: hidden;
  margin: 8px 0 0;
  color: ${colors.secondary};
  font-size: 11px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const Actions = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  padding: 12px 20px;
  border-top: 1px solid ${colors.border};
  background: #fdfdfc;

  @media (max-width: 640px) {
    flex-direction: column;
    align-items: stretch;
    padding: 12px 16px;
  }
`;

export const ActionButtons = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;

  @media (max-width: 640px) {
    > button:first-child {
      flex: 1;
    }

    > button:last-child {
      flex: 2;
    }
  }
`;

export const CancelButton = styled.button`
  ${button};
  color: ${colors.secondary};
  background: transparent;

  &:hover {
    background: #eeeeee;
  }
`;

export const SaveButton = styled.button`
  ${button};
  color: #ffffff;
  background: ${colors.navy};
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: ${colors.navyDark};
  }
`;

export const AnonymousChoice = styled.label`
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 14px;
  cursor: pointer;
  input { width: 16px; height: 16px; accent-color: #0c3273; }
  small { color: #747781; }
`;
