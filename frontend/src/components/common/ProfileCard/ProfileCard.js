import { useEffect, useRef, useState } from "react";
import { analyzeRoommateProfile } from "../../../api/roommateApi";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Heart, Sparkles } from "lucide-react";

import * as S from "./ProfileCardCss";
import ProfileImage from "../ProfileImage";
import { getProfileKey, useProfileLikes } from "./profileLikes";
export { getProfileKey, useProfileLikes } from "./profileLikes";

function ProfileCard({
    profile,
    activeTab,
    showOverlay = false,
}) {
    const location = useLocation();
    const navigate = useNavigate();
    const { isLiked: checkLiked, updateLikes, likeError, likePending } = useProfileLikes(profile);
    const detailProfile = profile.source === "backend" ? { ...profile, id: profile.userId }
        : location.pathname === "/" ? { ...profile, id: `main:${profile.id}` } : profile;
    const isLiked = checkLiked(profile);
    const href = `/roommates/${encodeURIComponent(getProfileKey(detailProfile))}${activeTab === "recommended" ? "?tab=recommended" : ""}`;
    const detailState = {
        profile: detailProfile,
        activeTab,
        fromProfileCard: true,
        ...(["/", "/profile", "/mypage"].includes(location.pathname)
            ? { backgroundLocation: location }
            : {}),
    };
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
                        disabled={likePending}
                        onClick={async (event) => {
                            event.stopPropagation();
                            await updateLikes([profile], !isLiked);
                        }}
                    >
                        <Heart
                            size={16}
                            aria-hidden="true"
                            fill={isLiked ? "currentColor" : "none"}
                        />
                </S.LikeButton>
            </S.CardHeader>

            {likeError && <p role="alert">{likeError}</p>}
            <S.ProfileMessage>
                <p>{profile.message?.trim() || "아직 작성된 자기소개가 없어요."}</p>
            </S.ProfileMessage>

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
