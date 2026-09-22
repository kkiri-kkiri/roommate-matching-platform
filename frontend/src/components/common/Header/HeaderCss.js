import styled, { css } from "styled-components";

const colors = {
  white: "#ffffff",
  navy: "#0c3273",
  navyDark: "#001d4f",
  navySoft: "#edf2ff",
  mint: "#00a79f",
  mintDark: "#006a65",
  mintSoft: "#e3f8f5",
  textSoft: "#444650",
  muted: "#747781",
  border: "#e2e2e4",
};

const focusRing = css`
  &:focus-visible {
    outline: 3px solid rgba(0, 167, 159, 0.22);
    outline-offset: 2px;
  }
`;

export const TopBar = styled.header`
  position: sticky;
  z-index: 50;
  top: 0;
  border-bottom: 1px solid ${colors.border};
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(14px);
`;

export const TopBarInner = styled.div`
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  width: 100%;
  max-width: 1200px;
  min-height: 72px;
  margin: 0 auto;
  padding: 0 20px;

  @media (max-width: 760px) {
    grid-template-columns: auto minmax(0, 1fr) auto;
    row-gap: 0;
    min-height: 72px;
    padding: 0 12px;
  }
`;

export const BrandLink = styled.a`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-self: start;
  min-height: 44px;
  border-radius: 10px;
  color: ${colors.navyDark};
  font-size: 20px;
  font-weight: 800;
  line-height: 28px;
  letter-spacing: -0.03em;
  text-decoration: none;
`;

export const LogoImage = styled.img`
  display: block;
  width: 176px;
  height: auto;
  margin-left: -30px;
  object-fit: contain;

  @media (max-width: 760px) {
    width: 148px;
    margin-left: -7px;
  }

  @media (max-width: 480px) {
    width: 112px;
  }
`;

export const MainNavigation = styled.nav`
  display: flex;
  gap: 8px;
  align-self: stretch;
  justify-content: center;

  @media (max-width: 760px) {
    grid-column: 2;
    grid-row: 1;
    gap: 0;
    justify-content: flex-start;
    min-width: 0;
    min-height: 72px;
    margin: 0;
    padding: 0 4px;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }
`;

export const NavigationLink = styled.a`
  ${focusRing};
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  min-height: 72px;
  padding: 0 15px;
  border-radius: 8px;
  color: ${colors.textSoft};
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  text-decoration: none;
  transition: color 160ms ease;

  &::after {
    position: absolute;
    right: 14px;
    bottom: -1px;
    left: 14px;
    height: 3px;
    border-radius: 999px 999px 0 0;
    background: ${colors.mint};
    opacity: 0;
    content: "";
    transform: scaleX(0.55);
    transition:
      opacity 160ms ease,
      transform 160ms ease;
  }

  &.active {
    color: ${colors.navy};
    font-weight: 700;

    &::after {
      opacity: 1;
      transform: scaleX(1);
    }
  }

  @media (hover: hover) {
    &:hover {
      color: ${colors.navy};
    }
  }

  @media (max-width: 760px) {
    min-height: 52px;
    padding: 0 8px;
    font-size: 12px;

    &::after {
      right: 8px;
      left: 8px;
    }
  }

  @media (max-width: 480px) {
    padding: 0 5px;
    font-size: 11px;

    &::after {
      right: 5px;
      left: 5px;
    }
  }
`;

export const AuthNavigation = styled.nav`
  display: flex;
  gap: 7px;
  align-items: center;
  justify-self: end;

  @media (max-width: 760px) {
    grid-column: 3;
    grid-row: 1;
    gap: 3px;
  }
`;

const authLinkStyle = css`
  ${focusRing};
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  border-radius: 11px;
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
  text-decoration: none;
`;

export const LoginLink = styled.a`
  ${authLinkStyle};
  padding: 0 15px;
  border: 1px solid ${colors.border};
  color: ${colors.navy};
  background: ${colors.white};
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

export const ProfileMenu = styled.div`
  position: relative;
`;

export const ProfileName = styled.span`
  overflow: hidden;
  max-width: 132px;
  text-overflow: ellipsis;
  white-space: nowrap;
`;

export const AccountMenuButton = styled.button`
  ${focusRing};
  display: inline-flex;
  gap: 9px;
  align-items: center;
  min-height: 44px;
  padding: 4px 10px 4px 5px;
  border: 1px solid ${colors.border};
  border-radius: 999px;
  color: ${colors.navyDark};
  background: ${colors.white};
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;

  > svg {
    flex: 0 0 auto;
    color: ${colors.muted};
    transition: transform 160ms ease;
  }

  &[aria-expanded="true"] {
    border-color: rgba(0, 167, 159, 0.42);
    background: ${colors.mintSoft};

    > svg {
      transform: rotate(180deg);
    }
  }

  @media (hover: hover) {
    &:hover {
      border-color: rgba(0, 167, 159, 0.42);
      background: ${colors.mintSoft};
    }
  }

  @media (max-width: 420px) {
    padding-right: 5px;

    ${ProfileName} {
      display: none;
    }
  }
`;

export const HeaderAvatar = styled.span`
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: ${colors.white};
  background: linear-gradient(145deg, ${colors.mint}, ${colors.mintDark});
  font-size: 13px;
  font-weight: 800;
`;

export const ProfileDropdown = styled.div`
  position: absolute;
  z-index: 70;
  top: calc(100% + 9px);
  right: 0;
  width: 184px;
  padding: 7px;
  border: 1px solid ${colors.border};
  border-radius: 14px;
  background: ${colors.white};
  box-shadow: 0 14px 34px rgba(0, 29, 79, 0.14);
`;

const profileMenuItemStyle = css`
  ${focusRing};
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 42px;
  padding: 0 11px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  font-size: 13px;
  font-weight: 700;
  line-height: 18px;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition:
    color 150ms ease,
    background-color 150ms ease;

  svg {
    flex: 0 0 auto;
  }
`;

export const ProfileMenuLink = styled.a`
  ${profileMenuItemStyle};
  color: ${colors.navyDark};

  @media (hover: hover) {
    &:hover {
      color: ${colors.navy};
      background: ${colors.navySoft};
    }
  }
`;

export const ProfileMenuDivider = styled.span`
  display: block;
  height: 1px;
  margin: 5px 4px;
  background: ${colors.border};
`;

export const LogoutButton = styled.button`
  ${profileMenuItemStyle};
  color: #a43b4c;

  @media (hover: hover) {
    &:hover {
      color: #8b2d3d;
      background: #fff0f3;
    }
  }
`;
