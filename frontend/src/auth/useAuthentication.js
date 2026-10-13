import { useCallback, useEffect, useRef, useState } from "react";
import { getAuthenticatedUser } from "../api/authApi";
import { AUTHENTICATED_USER_KEY, AUTH_CLEARED_EVENT, clearStoredAuthentication,
  getAccessToken, getTokenExpiration, readAccessToken } from "./session";

function toCurrentUser({ id, nickname, displayName, schoolEmail, role }) {
  const name = displayName || nickname || schoolEmail || "내 프로필";
  return { id, role, name, initial: name.slice(0, 1) };
}

function storeUser(user) {
  try {
    window.localStorage.setItem(AUTHENTICATED_USER_KEY, JSON.stringify(user));
  } catch {
    // 서버에서 확인한 사용자 정보는 현재 화면에서도 사용할 수 있습니다.
  }
}

export function useAuthentication() {
  const [currentUser, setCurrentUser] = useState(null);
  const [authInitialized, setAuthInitialized] = useState(false);
  const version = useRef(0);

  const handleLogin = useCallback((user) => {
    version.current += 1;
    const authenticatedUser = toCurrentUser(user);
    storeUser(authenticatedUser);
    setCurrentUser(authenticatedUser);
  }, []);

  useEffect(() => {
    let active = true;
    const onCleared = () => {
      version.current += 1;
      setCurrentUser(null);
    };
    const verifyAuthentication = async () => {
      const requestVersion = ++version.current;
      const token = getAccessToken();
      if (!token) {
        clearStoredAuthentication();
        setAuthInitialized(true);
        return;
      }
      try {
        const user = await getAuthenticatedUser(token);
        if (!active || version.current !== requestVersion || readAccessToken() !== token) return;
        const authenticatedUser = toCurrentUser(user);
        storeUser(authenticatedUser);
        setCurrentUser(authenticatedUser);
      } catch {
        // 401은 공통 API 처리에서 로그아웃합니다. 통신 장애는 토큰을 삭제하지 않습니다.
        // 최초 진입에서는 서버 검증에 성공한 경우에만 로그인된 화면을 표시합니다.
      } finally {
        if (active) setAuthInitialized(true);
      }
    };
    const onStorage = (event) => {
      if (event.key === "accessToken" || event.key === null) {
        onCleared();
        void verifyAuthentication();
      }
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") void verifyAuthentication();
    };

    window.addEventListener(AUTH_CLEARED_EVENT, onCleared);
    window.addEventListener("storage", onStorage);
    window.addEventListener("focus", verifyAuthentication);
    document.addEventListener("visibilitychange", onVisible);
    void verifyAuthentication();
    return () => {
      active = false;
      version.current += 1;
      window.removeEventListener(AUTH_CLEARED_EVENT, onCleared);
      window.removeEventListener("storage", onStorage);
      window.removeEventListener("focus", verifyAuthentication);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, []);

  useEffect(() => {
    if (!currentUser) return undefined;
    const token = readAccessToken();
    const expiresAt = getTokenExpiration(token);
    let timer;
    const checkExpiration = () => {
      if (readAccessToken() !== token) return;
      const remaining = (expiresAt || 0) - Date.now();
      if (remaining <= 0) clearStoredAuthentication();
      else timer = window.setTimeout(checkExpiration, Math.min(remaining, 2147483647));
    };
    checkExpiration();
    return () => window.clearTimeout(timer);
  }, [currentUser]);

  return { currentUser, authInitialized, handleLogin, clearStoredAuthentication };
}
