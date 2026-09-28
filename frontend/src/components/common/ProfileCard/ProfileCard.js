import { useEffect, useRef, useState } from "react";
import { analyzeRoommateProfile } from "../../../api/roommateApi";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Heart, Sparkles } from "lucide-react";

import * as S from "./ProfileCardCss";
import ProfileImage from "../ProfileImage";

// 기존 화면의 더미 ID를 같은 사람으로 연결합니다. 메인 화면의 샘플은 별도 ID를 사용합니다.
const PROFILE_ALIASES = {
    "roommate-cloud": "1", "community-star": "2", "roommate-mint": "3",
    "dormitory-afternoon": "5", "roommate-forest": "7", "community-tea": "8",
};
const LIKES_KEY = "roomieDetailedProfileLikes";
const LEGACY_UNLIKED_KEY = "roomieUnlikedProfileIds";
const LIKES_EVENT = "roomieProfileLikesChanged";

export function getProfileKey(profile) {
    const id = String(profile.id);
    return Object.prototype.hasOwnProperty.call(PROFILE_ALIASES, id) ? PROFILE_ALIASES[id] : id;
}

function readProfileLikes() {
    try {
        const saved = JSON.parse(window.localStorage.getItem(LIKES_KEY) || "{}");
        if (!saved || typeof saved !== "object" || Array.isArray(saved)) return {};
        return Object.fromEntries(Object.entries(saved)
            .filter(([, entry]) => typeof entry?.liked === "boolean" && entry.profile?.id != null)
            .map(([, entry]) => [getProfileKey(entry.profile), entry]));
    } catch {
        return {};
    }
}

function isInitiallyLiked(profile) {
    const key = getProfileKey(profile);
    try {
        const removed = JSON.parse(window.localStorage.getItem(LEGACY_UNLIKED_KEY) || "[]");
        if (Array.isArray(removed) && removed.some((id) => getProfileKey({ id }) === key)) return false;
    } catch {
        // 기존 저장값이 없으면 마이페이지의 예시 좋아요 목록을 유지합니다.
    }
    return ["1", "2", "5"].includes(key) || key.startsWith("generated-roommate-");
}

// Provider를 추가하지 않고 각 화면의 로컬 상태와 브라우저의 예시 좋아요만 동기화합니다.
export function useProfileLikes() {
    const [likes, setLikes] = useState(readProfileLikes);

    useEffect(() => {
        const syncLocal = (event) => setLikes(event.detail);
        const syncStorage = (event) => {
            if (event.key === null || event.key === LIKES_KEY || event.key === LEGACY_UNLIKED_KEY) {
                setLikes(readProfileLikes());
            }
        };
        window.addEventListener(LIKES_EVENT, syncLocal);
        window.addEventListener("storage", syncStorage);
        return () => {
            window.removeEventListener(LIKES_EVENT, syncLocal);
            window.removeEventListener("storage", syncStorage);
        };
    }, []);

    const isLiked = (profile) => likes[getProfileKey(profile)]?.liked ?? isInitiallyLiked(profile);
    const updateLikes = (profiles, liked) => {
        const next = { ...likes, ...readProfileLikes() };
        for (const profile of profiles) {
            next[getProfileKey(profile)] = { liked, profile };
        }
        let saved = true;
        try {
            window.localStorage.setItem(LIKES_KEY, JSON.stringify(next));
        } catch {
            saved = false;
        }
        setLikes(next);
        window.dispatchEvent(new CustomEvent(LIKES_EVENT, { detail: next }));
        return saved;
    };

    return { likes, isLiked, updateLikes };
}

function ProfileCard({
    profile,
    activeTab,
    onToggleLike,
    showOverlay = false,
}) {
    const location = useLocation();
    const navigate = useNavigate();
    const { isLiked: checkLiked, updateLikes } = useProfileLikes();
    const detailProfile = profile.source === "backend" ? { ...profile, id: profile.userId }
        : location.pathname === "/" ? { ...profile, id: `main:${profile.id}` } : profile;
    const isLiked = checkLiked(detailProfile);
    const href = `/roommates/${encodeURIComponent(getProfileKey(detailProfile))}${activeTab === "recommended" ? "?tab=recommended" : ""}`;
    const detailState = { profile: detailProfile, activeTab, fromProfileCard: true };
    const [analysis, setAnalysis] = useState({ status: "idle", text: "" });
    const analysisRequest = useRef(null);
    useEffect(() => {
        setAnalysis({ status: "idle", text: "" });
        return () => { analysisRequest.current?.abort(); analysisRequest.current = null; };
    }, [profile.userId]);
    const loadAnalysis = async () => {
        if (!showOverlay || activeTab !== "recommended" || profile.source !== "backend"
            || analysisRequest.current || analysis.status === "success") return;
        const controller = new AbortController();
        analysisRequest.current = controller;
        setAnalysis({ status: "loading", text: "AI가 매칭 결과를 분석하고 있어요…" });
        try {
            const result = await analyzeRoommateProfile(profile.userId, controller.signal);
            if (!controller.signal.aborted) setAnalysis({ status: "success", text: result.analysis });
        } catch (error) {
            if (!controller.signal.aborted) setAnalysis({ status: "error", text: error.message });
        } finally {
            if (analysisRequest.current === controller) analysisRequest.current = null;
        }
    };

    return (
        <S.ProfileCard onMouseEnter={loadAnalysis} onFocus={loadAnalysis} onClick={(event) => {
            loadAnalysis();
            if (!event.target.closest("a, button")) {
                event.currentTarget.querySelector("a")?.focus({ preventScroll: true });
                navigate(href, { state: detailState });
            }
        }}>
            <S.CardHeader>
                <S.ProfilePhoto>
                    <ProfileImage major={profile.major} fallbackSrc={profile.photo} alt={`${profile.nickname} 프로필 사진`} />
                </S.ProfilePhoto>

                <S.ProfileIdentity>
                    <S.ProfileNameRow>
                        <S.ProfileName as={Link} to={href} state={detailState}
                            aria-label={`${profile.nickname} 상세 프로필 보기`}>
                            {profile.nickname}
                        </S.ProfileName>
                        {profile.matchScore != null && (
                            <S.MatchBadge aria-label={`매칭점수 ${profile.matchScore}점`}>
                                <Sparkles size={13} aria-hidden="true" />
                                {profile.matchScore}점
                            </S.MatchBadge>
                        )}
                    </S.ProfileNameRow>

                    <S.ProfileAcademic
                        title={`${profile.admissionYear ? `${profile.admissionYear}학번` : profile.grade} · ${profile.major}`}
                    >
                        <S.AdmissionYear>
                            {profile.admissionYear ? `${profile.admissionYear}학번` : profile.grade}
                        </S.AdmissionYear>
                        <span aria-hidden="true">·</span>
                        <span>{profile.major}</span>
                    </S.ProfileAcademic>

                </S.ProfileIdentity>

                <S.LikeButton
                        type="button"
                        aria-label={`${profile.nickname} 프로필 ${isLiked ? "좋아요 취소" : "좋아요"}`}
                        aria-pressed={isLiked}
                        title={isLiked ? "좋아요 취소" : "좋아요"}
                        $liked={isLiked}
                        onClick={(event) => {
                            event.stopPropagation();
                            updateLikes([detailProfile], !isLiked);
                            onToggleLike?.(profile.id);
                        }}
                    >
                        <Heart
                            size={16}
                            aria-hidden="true"
                            fill={isLiked ? "currentColor" : "none"}
                        />
                </S.LikeButton>
            </S.CardHeader>

            {profile.message && (
                <S.ProfileMessage>
                    <p>{profile.message}</p>
                </S.ProfileMessage>
            )}

            {showOverlay && activeTab === "recommended" && (
                <S.ProfileCardOverlay aria-label={`${profile.nickname} 추천 이유`}>
                    <S.ProfileCardOverlayTitle>AI 추천 이유</S.ProfileCardOverlayTitle>
                    <S.ProfileCardOverlayText role={analysis.status === "error" ? "alert" : "status"}
                        aria-busy={analysis.status === "loading"}>
                        {analysis.text || "프로필을 선택하면 AI 분석을 확인할 수 있어요."}
                    </S.ProfileCardOverlayText>
                </S.ProfileCardOverlay>
            )}
        </S.ProfileCard>
    );
}

export default ProfileCard;
