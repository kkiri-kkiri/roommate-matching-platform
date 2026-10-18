import { useSyncExternalStore } from "react";

export const AUTHENTICATED_USER_KEY = "roomieAuthenticatedUser";
export const AUTH_CLEARED_EVENT = "roomie:auth-cleared";

let sessionToken;
let session = Object.freeze({ userId: null, generation: 0 });
const sessionListeners = new Set();

function tokenClaims(token) {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(window.atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")));
  } catch {
    return null;
  }
}

function refreshSession(force = false) {
  const token = readAccessToken();
  if (force || token !== sessionToken) {
    sessionToken = token;
    // Identity is only for client state isolation; the server still validates the JWT.
    const userId = tokenClaims(token)?.userId;
    session = Object.freeze({ userId: Number.isSafeInteger(userId) ? userId : null,
      generation: session.generation + 1 });
  }
  return session;
}

function notifySession() {
  sessionListeners.forEach((listener) => listener());
}

function onSessionStorage(event) {
  if (event.key === "accessToken" || event.key === null) {
    refreshSession(true);
    notifySession();
  }
}

function subscribeSession(listener) {
  if (sessionListeners.size === 0) window.addEventListener("storage", onSessionStorage);
  sessionListeners.add(listener);
  return () => {
    sessionListeners.delete(listener);
    if (sessionListeners.size === 0) window.removeEventListener("storage", onSessionStorage);
  };
}

export function getAuthenticationSession() {
  return refreshSession();
}

export function isCurrentAuthenticationSession(startedSession) {
  return startedSession === getAuthenticationSession();
}

export function useAuthenticationSession() {
  return useSyncExternalStore(subscribeSession, getAuthenticationSession);
}

export function storeAccessToken(token) {
  try {
    window.localStorage.setItem("accessToken", token);
  } finally {
    // A new login is a new generation, even if the server returns the same JWT.
    refreshSession(true);
    notifySession();
  }
}

export function readAccessToken() {
  try {
    return window.localStorage.getItem("accessToken");
  } catch {
    return null;
  }
}

export function getTokenExpiration(token) {
  try {
    const parts = token.split(".");
    if (parts.length !== 3 || parts.some((part) => !part)) return null;
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const { exp } = JSON.parse(window.atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")));
    return typeof exp === "number" && Number.isFinite(exp) ? exp * 1000 : null;
  } catch {
    return null;
  }
}

export function clearStoredAuthentication() {
  try {
    [AUTHENTICATED_USER_KEY, "accessToken", "token", "user", "profile"]
      .forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // 저장소 접근이 제한되어도 화면의 인증 상태는 해제합니다.
  }
  refreshSession(true);
  notifySession();
  window.dispatchEvent(new Event(AUTH_CLEARED_EVENT));
}

export function getAccessToken() {
  const token = readAccessToken();
  if (!token) return null;
  const expiresAt = getTokenExpiration(token);
  // 클라이언트에서는 만료 여부만 확인하고, 서명과 계정 상태는 서버에서 검증합니다.
  if (expiresAt === null || expiresAt <= Date.now()) {
    clearStoredAuthentication();
    return null;
  }
  return token;
}
