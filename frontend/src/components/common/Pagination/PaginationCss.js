import styled from "styled-components";

const colors = {
    white: "#ffffff",
    mint: "#00a79f",
    mintSoft: "#e7f8f6",
    navy: "#0c3273",
    textSoft: "#444650",
    muted: "#747781",
    border: "#e2e2e4",
};

export const Pagination = styled.nav`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: center;
  margin-top: 24px;
`;

export const PageNumberButton = styled.button`
  min-width: 38px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid ${({ $active }) => ($active ? colors.mint : colors.border)};
  border-radius: 999px;
  color: ${({ $active }) => ($active ? colors.white : colors.textSoft)};
  background: ${({ $active }) => ($active ? colors.mint : colors.white)};
  font-size: 13px;
  font-weight: 600;
  line-height: 18px;
  cursor: pointer;
  transition: all 150ms ease;

  &:disabled {
    color: ${colors.muted};
    background: #f3f4f6;
    border-color: ${colors.border};
    cursor: default;
    pointer-events: none;
  }

  @media (hover: hover) {
    &:hover:not(:disabled) {
      border-color: ${colors.mint};
      background: ${({ $active }) => ($active ? colors.mint : colors.mintSoft)};
      color: ${({ $active }) => ($active ? colors.white : colors.navy)};
    }
  }
`;

export const PageNavButton = styled.button`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 36px;
  border: 1px solid ${colors.border};
  border-radius: 999px;
  color: ${colors.textSoft};
  background: ${colors.white};
  cursor: pointer;
  transition: all 150ms ease;

  &:disabled {
    color: ${colors.muted};
    background: #f3f4f6;
    border-color: ${colors.border};
    cursor: default;
    pointer-events: none;
  }

  @media (hover: hover) {
    &:hover:not(:disabled) {
      border-color: ${colors.mint};
      background: ${colors.mintSoft};
      color: ${colors.navy};
    }
  }
`;
