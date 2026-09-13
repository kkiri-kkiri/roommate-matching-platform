import styled from "styled-components";

const colors = {
    white: "#ffffff",
    navy: "#0c3273",
    navyDark: "#001d4f",
    navySoft: "#eef3ff",
    mint: "#00a79f",
    mintDark: "#006a65",
    mintSoft: "#e7f8f6",
    textSoft: "#444650",
    border: "#e2e2e4",
};

export const ProfileCardOverlay = styled.div`
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 18px 16px 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(0, 167, 159, 0.12) 0%, rgba(0, 167, 159, 0.76) 42%, rgba(0, 106, 101, 0.9) 100%);
  color: ${colors.white};
  opacity: 0;
  transform: translateY(100%);
  pointer-events: none;
  transition: opacity 220ms ease, transform 220ms ease;
`;

export const ProfileCardOverlayTitle = styled.strong`
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.9);
`;

export const ProfileCardOverlayText = styled.p`
  margin: 0;
  color: rgba(255, 255, 255, 0.96);
  font-size: 13px;
  font-weight: 500;
  line-height: 1.6;
  word-break: keep-all;
`;

export const ProfileCard = styled.article`
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 24px;
  border: 1px solid ${colors.border};
  border-radius: 16px;
  background: ${colors.white};
  box-shadow: 0 4px 12px rgba(12, 50, 115, 0.05);
  cursor: pointer;
  overflow: hidden;
  transition: transform 150ms ease, box-shadow 150ms ease, border-color 150ms ease;

  &:hover {
    border-color: rgba(12, 50, 115, 0.12);
    box-shadow: 0 8px 20px rgba(12, 50, 115, 0.08);
    transform: translateY(-2px);
  }

  &:hover ${ProfileCardOverlay} {
    opacity: 1;
    transform: translateY(0%);
  }
`;

export const CardHeader = styled.div`
  position: relative;
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 14px;
  align-items: start;
  padding-right: 36px;
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
    object-fit: contain;
  }
`;

export const ProfileIdentity = styled.div`
  min-width: 0;
`;

export const ProfileNameRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
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
`;

export const ProfileAcademic = styled.p`
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  align-items: center;
  min-width: 0;
  margin: 6px 0 0;
  color: ${colors.textSoft};
  font-size: 14px;
  line-height: 20px;

  > span:last-child {
    min-width: 0;
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

export const LikeButton = styled.button`
  position: absolute;
  top: 0;
  right: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid ${({ $liked }) => ($liked ? "rgba(255, 107, 138, 0.4)" : colors.border)};
  border-radius: 50%;
  color: ${({ $liked }) => ($liked ? "#ff4d73" : "#ff6b8a")};
  background: ${({ $liked }) => ($liked ? "#fff1f5" : colors.white)};
  box-shadow: none;
  cursor: pointer;
  transition: background 150ms ease, border-color 150ms ease;

  &:focus-visible {
    outline: 3px solid rgba(255, 107, 138, 0.24);
    outline-offset: 2px;
  }
`;

export const ProfileMessage = styled.div`
  position: relative;
  margin-top: 20px;
  padding: 16px;
  border-radius: 12px;
  background: #f7f7f6;

  &::before {
    content: "";
    position: absolute;
    top: -12px;
    left: 28px;
    border-right: 10px solid transparent;
    border-bottom: 12px solid #f7f7f6;
    border-left: 10px solid transparent;
  }

  p {
    margin: 0;
    color: ${colors.textSoft};
    font-size: 14px;
    line-height: 20px;
    word-break: keep-all;
  }
`;

