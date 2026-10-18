import { useEffect, useRef, useState } from "react";
import { setRoommateLike } from "../../../api/roommateApi";
import { getAccessToken, isCurrentAuthenticationSession,
  useAuthenticationSession } from "../../../auth/session";

const PROFILE_ALIASES = {
  "roommate-cloud": "1", "community-star": "2", "roommate-mint": "3",
  "dormitory-afternoon": "5", "roommate-forest": "7", "community-tea": "8",
};
const LIKES_EVENT = "roomieProfileLikesChanged";
const pendingProfileLikes = new Set();

export function getProfileKey(profile) {
  const id = String(profile.id);
  return Object.prototype.hasOwnProperty.call(PROFILE_ALIASES, id) ? PROFILE_ALIASES[id] : id;
}

const likeKey = (profile, session) =>
  `${session.generation}:${session.userId}:${profile.source || "demo"}:${getProfileKey(profile)}`;

function publish(session, profile, change) {
  if (isCurrentAuthenticationSession(session)) {
    window.dispatchEvent(new CustomEvent(LIKES_EVENT, { detail: {
      session, key: likeKey(profile, session), source: profile.source, ...change,
    } }));
  }
}

export function subscribeProfileLikeChanges(session, onChange) {
  const listener = ({ detail }) => {
    if (detail?.session === session && isCurrentAuthenticationSession(session)
        && detail.source === "backend" && typeof detail.liked === "boolean") onChange();
  };
  window.addEventListener(LIKES_EVENT, listener);
  return () => window.removeEventListener(LIKES_EVENT, listener);
}

// Only successful writes in the current session may override the loaded server profile.
export function useProfileLikes(profile) {
  const session = useAuthenticationSession();
  const [likes, setLikes] = useState({});
  const [failure, setFailure] = useState(null);
  const [, refreshPending] = useState(0);
  const lifetime = useRef(null);
  useEffect(() => {
    const current = { active: true };
    lifetime.current = current;
    return () => { current.active = false; };
  }, [session]);
  useEffect(() => {
    const syncLike = ({ detail }) => {
      if (!profile || detail?.session !== session || !isCurrentAuthenticationSession(session)
          || detail.key !== likeKey(profile, session)) return;
      if (typeof detail.liked === "boolean") {
        setLikes((current) => ({ ...current, [detail.key]: { liked: detail.liked, profile } }));
      }
      refreshPending((value) => value + 1);
    };
    window.addEventListener(LIKES_EVENT, syncLike);
    return () => window.removeEventListener(LIKES_EVENT, syncLike);
  }, [profile, session]);

  const isLiked = (item) => {
    const entry = likes[likeKey(item, session)];
    return entry?.profile === item ? entry.liked : Boolean(item.liked);
  };
  const updateLikes = async (profiles, liked) => {
    // A stale handler must never start a write using another account's credentials.
    if (!isCurrentAuthenticationSession(session)) return false;
    const keys = profiles.map((item) => likeKey(item, session));
    if (keys.some((key) => pendingProfileLikes.has(key))) return false;
    const startedLifetime = lifetime.current;
    const canApply = () => startedLifetime?.active && isCurrentAuthenticationSession(session);
    if (!canApply()) return false;
    if (profiles.some((item) => item.source === "backend") && !getAccessToken()) {
      if (canApply()) setFailure({ session, key: keys[0], message: "로그인 후 이용해주세요." });
      return false;
    }
    keys.forEach((key) => pendingProfileLikes.add(key));
    setFailure(null);
    refreshPending((value) => value + 1);
    profiles.forEach((item) => publish(session, item, { pending: true }));
    try {
      for (const item of profiles) {
        if (!canApply()) return false;
        if (item.source === "backend") await setRoommateLike(item.userId ?? item.id, liked);
        if (!canApply()) return false;
        publish(session, item, { liked });
      }
      return true;
    } catch (error) {
      if (canApply()) setFailure({ session, key: keys[0], message: error.message });
      return false;
    } finally {
      keys.forEach((key) => pendingProfileLikes.delete(key));
      // Pending cleanup is allowed for surviving views of this same session only.
      profiles.forEach((item) => publish(session, item, { pending: false }));
    }
  };
  const key = profile ? likeKey(profile, session) : null;
  return { isLiked, updateLikes,
    likePending: key != null && pendingProfileLikes.has(key),
    likeError: failure?.session === session && failure.key === key ? failure.message : "" };
}
