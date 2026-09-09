import { useEffect, useRef, useState } from "react";
import { Link, NavLink } from "react-router-dom";
import { ChevronDown, LogOut, UserRound } from "lucide-react";

import * as S from "./HeaderCss";

function getStoredUser() {
  if (typeof window === "undefined") {
    return null;
  }

  const accessToken =
    window.localStorage.getItem("accessToken") ||
    window.localStorage.getItem("token");

  if (!accessToken) {
    return null;
  }

  try {
    const storedProfile = JSON.parse(
      window.localStorage.getItem("user") ||
        window.localStorage.getItem("profile") ||
        "{}",
    );

    return {
      name: storedProfile.name || storedProfile.nickname || "내 프로필",
      initial: (storedProfile.name || storedProfile.nickname || "나").slice(
        0,
        1,
      ),
    };
  } catch {
    return {
      name: "내 프로필",
      initial: "나",
    };
  }
}

function Header({ currentUser, onLogout }) {
  const authenticatedUser =
    currentUser === undefined ? getStoredUser() : currentUser;
  const profileMenuRef = useRef(null);
  const profileButtonRef = useRef(null);
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);

  useEffect(() => {
    if (!isProfileMenuOpen) return undefined;

    const closeOnOutsideClick = (event) => {
      if (!profileMenuRef.current?.contains(event.target)) {
        setIsProfileMenuOpen(false);
      }
    };

    const closeOnEscape = (event) => {
      if (event.key === "Escape") {
        setIsProfileMenuOpen(false);
        profileButtonRef.current?.focus();
      }
    };

    document.addEventListener("pointerdown", closeOnOutsideClick);
    document.addEventListener("keydown", closeOnEscape);

    return () => {
      document.removeEventListener("pointerdown", closeOnOutsideClick);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [isProfileMenuOpen]);

  const handleLogout = () => {
    setIsProfileMenuOpen(false);
    onLogout?.();
  };

  return (
    <S.TopBar>
      <S.TopBarInner>
        <S.BrandLink as={Link} to="/" aria-label="끼리끼리 홈">
          <S.LogoImage src="/img/kkiri-kkiri.png" alt="끼리끼리 로고" />
        </S.BrandLink>

        <S.MainNavigation aria-label="주요 메뉴">
          <S.NavigationLink as={NavLink} to="/roommates">
            룸메찾기
          </S.NavigationLink>

          <S.NavigationLink as={NavLink} to="/community">
            커뮤니티
          </S.NavigationLink>

          <S.NavigationLink as={NavLink} to="/chat">
            채팅
          </S.NavigationLink>

          <S.NavigationLink as={NavLink} to="/calendar#campus-calendar">
            캘린더
          </S.NavigationLink>
        </S.MainNavigation>

        <S.AuthNavigation aria-label="계정 메뉴">
          {authenticatedUser ? (
            <S.ProfileMenu ref={profileMenuRef}>
              <S.AccountMenuButton
                ref={profileButtonRef}
                type="button"
                aria-label={
                  isProfileMenuOpen ? "프로필 메뉴 닫기" : "프로필 메뉴 열기"
                }
                aria-haspopup="menu"
                aria-expanded={isProfileMenuOpen}
                aria-controls="profile-dropdown"
                onClick={() => setIsProfileMenuOpen((isOpen) => !isOpen)}
              >
                <S.HeaderAvatar>
                  {authenticatedUser.initial ||
                    authenticatedUser.name?.slice(0, 1) ||
                    "나"}
                </S.HeaderAvatar>

                <S.ProfileName>
                  {authenticatedUser.name || "내 프로필"}
                </S.ProfileName>

                <ChevronDown size={15} aria-hidden="true" />
              </S.AccountMenuButton>

              {isProfileMenuOpen && (
                <S.ProfileDropdown id="profile-dropdown">
                  <S.ProfileMenuLink
                    as={Link}
                    to="/profile"
                    onClick={() => setIsProfileMenuOpen(false)}
                  >
                    <UserRound size={17} aria-hidden="true" />
                    <span>마이페이지</span>
                  </S.ProfileMenuLink>

                  <S.ProfileMenuDivider aria-hidden="true" />

                  <S.LogoutButton type="button" onClick={handleLogout}>
                    <LogOut size={17} aria-hidden="true" />
                    <span>로그아웃</span>
                  </S.LogoutButton>
                </S.ProfileDropdown>
              )}
            </S.ProfileMenu>
          ) : (
            <S.LoginLink as={Link} to="/login">
              로그인
            </S.LoginLink>
          )}
        </S.AuthNavigation>
      </S.TopBarInner>
    </S.TopBar>
  );
}

export default Header;
