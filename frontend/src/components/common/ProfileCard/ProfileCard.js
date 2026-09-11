import { Heart, MapPin, Sparkles } from "lucide-react";

import * as S from "./ProfileCardCss";

function ProfileCard({
    profile,
    activeTab,
    likedProfileIds = {},
    onToggleLike,
    overlayText,
    showOverlay = false,
}) {
    const isLiked = Boolean(likedProfileIds[profile.id]);

    return (
        <S.ProfileCard>
            <S.CardHeader>
                <S.ProfilePhoto>
                    <img src={profile.photo} alt={`${profile.nickname} 프로필 사진`} />
                </S.ProfilePhoto>

                <S.ProfileIdentity>
                    <S.ProfileNameRow>
                        <S.ProfileName>{profile.nickname}</S.ProfileName>
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

                    <S.Dormitory>
                        <MapPin size={14} aria-hidden="true" />
                        {profile.dormitory} 생활관
                    </S.Dormitory>
                </S.ProfileIdentity>

                {onToggleLike && (
                    <S.LikeButton
                        type="button"
                        aria-label={`${profile.nickname} 프로필 ${isLiked ? "좋아요 취소" : "좋아요"}`}
                        aria-pressed={isLiked}
                        title={isLiked ? "좋아요 취소" : "좋아요"}
                        $liked={isLiked}
                        onClick={() => onToggleLike(profile.id)}
                    >
                        <Heart
                            size={16}
                            aria-hidden="true"
                            fill={isLiked ? "currentColor" : "none"}
                        />
                    </S.LikeButton>
                )}
            </S.CardHeader>

            {profile.message && (
                <S.ProfileMessage>
                    <p>{profile.message}</p>
                </S.ProfileMessage>
            )}

            {showOverlay && activeTab === "recommended" && (
                <S.ProfileCardOverlay aria-label={`${profile.nickname} 추천 이유`}>
                    <S.ProfileCardOverlayTitle>AI 추천 이유</S.ProfileCardOverlayTitle>
                    <S.ProfileCardOverlayText>{overlayText}</S.ProfileCardOverlayText>
                </S.ProfileCardOverlay>
            )}
        </S.ProfileCard>
    );
}

export default ProfileCard;
