import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft, ArrowUpRight, ClipboardList, Heart, House,
  MessageCircle, Moon, Sparkles, SprayCan, UserRound, X,
} from "lucide-react";

import * as S from "./DetailedProfileCss";
import ProfileImage from "../ProfileImage";
import { getRoommateDetail } from "../../../api/roommateApi";
import { getProfileKey, useProfileLikes } from "./ProfileCard";

// 상세 화면에서 사용하는 설문 항목은 기존 내 설문의 응답 키와 일치시킵니다.
const SECTIONS = [
  {
    id: "pattern", title: "생활 패턴", icon: Moon,
    description: "하루의 시작부터 잠드는 순간까지, 평소의 생활 리듬이에요.",
    fields: [
      ["sleepTime", "취침시간"], ["wakeTime", "기상시간"],
      ["sleepTalking", "잠꼬대"], ["snoring", "코골이"],
      ["teethGrinding", "이갈이"], ["sleepSensitivity", "잠귀"],
      ["lightingPreference", "조명 환경"], ["phoneCall", "전화"],
      ["dormStayDays", "기숙사 상주 주기"],
    ],
  },
  {
    id: "habits", title: "청소·생활 습관", icon: SprayCan,
    description: "함께 쓰는 공간을 더 편안하게 만드는 작은 습관이에요.",
    fields: [
      ["cleaningFrequency", "청소 주기"], ["cleaningStyle", "청소 방식"],
      ["indoorEating", "실내 취식"], ["smoking", "흡연 여부"],
      ["mediaSound", "소리"], ["showerTime", "샤워 시간대"],
      ["showerDuration", "샤워 소요시간"], ["temperaturePreference", "온도 선호"],
    ],
  },
  {
    id: "dorm", title: "기숙사 생활", icon: House,
    description: "서로의 공간과 취향을 존중하는 공동생활 방식을 알아보세요.",
    fields: [
      ["roommateInteraction", "룸메이트와 교류 정도"], ["meal", "룸메이트와의 식사 방식"], ["mealPlan", "신청한 기숙사 식사"],
      ["scentProductUse", "향수·디퓨저 등 향 제품 사용"],
      ["desktopUse", "데스크탑 사용"], ["sharedItems", "공용물품 사용"],
    ],
  },
];

function displayAnswer(value) {
  if (Array.isArray(value)) return value.filter(Boolean).join(" · ");
  return typeof value === "string" || typeof value === "number" ? String(value).trim() : "";
}

function ProfileDialog({ profile, sourceProfile, onDismiss, returnTo, loading, error }) {
  const navigate = useNavigate();
  const dialogRef = useRef(null);
  const tabRefs = useRef([]);
  const backdropPointer = useRef(false);
  const [activeSection, setActiveSection] = useState("pattern");
  const { isLiked, updateLikes, likeError, likePending } = useProfileLikes(sourceProfile);
  const liked = sourceProfile ? isLiked(sourceProfile) : false;
  const section = SECTIONS.find(({ id }) => id === activeSection);
  const SectionIcon = section.icon;
  const answers = profile?.answers || {};
  const completed = section.fields.filter(([id]) => displayAnswer(answers[id])).length;
  const hasScore = typeof profile?.matchScore === "number" && Number.isFinite(profile.matchScore);
  const score = hasScore ? Math.min(100, Math.max(0, Math.round(profile.matchScore))) : null;
  const scoreDescription = score >= 90 ? "생활 습관이 아주 잘 맞아요"
    : score >= 75 ? "생활 습관이 잘 맞는 편이에요"
      : score >= 60 ? "대화로 맞춰갈 부분이 있어요" : "생활 습관을 천천히 비교해 보세요";

  useEffect(() => {
    const dialog = dialogRef.current;
    const previousFocus = document.activeElement;
    dialog.showModal();
    return () => {
      dialog.close();
      if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
    };
  }, []);

  const isOutsideDialog = (event) => {
    const bounds = event.currentTarget.getBoundingClientRect();
    return event.clientX < bounds.left || event.clientX > bounds.right
      || event.clientY < bounds.top || event.clientY > bounds.bottom;
  };

  const toggleLike = () => {
    updateLikes([sourceProfile], !liked);
  };

  const startChat = () => {
    const roomId = sourceProfile.chatRoomId || `roommate:${getProfileKey(sourceProfile)}`;
    navigate(`/chat?room=${encodeURIComponent(roomId)}`, {
      state: { roommateProfile: sourceProfile },
    });
  };

  const handleTabKeyDown = (event, index) => {
    let nextIndex;
    if (event.key === "ArrowRight") nextIndex = (index + 1) % SECTIONS.length;
    if (event.key === "ArrowLeft") nextIndex = (index + SECTIONS.length - 1) % SECTIONS.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = SECTIONS.length - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    setActiveSection(SECTIONS[nextIndex].id);
    tabRefs.current[nextIndex]?.focus();
  };

  return (
    <S.Dialog
      ref={dialogRef}
      aria-labelledby="detailed-profile-title"
      onCancel={(event) => { event.preventDefault(); onDismiss(); }}
      onPointerDown={(event) => { backdropPointer.current = isOutsideDialog(event); }}
      onClick={(event) => {
        if (backdropPointer.current && isOutsideDialog(event)) onDismiss();
        backdropPointer.current = false;
      }}
    >
      <S.DialogHeader>
        <S.BackButton type="button" onClick={onDismiss} autoFocus>
          <ArrowLeft size={18} aria-hidden="true" /> 이전으로
        </S.BackButton>
        <S.DialogLabel>상세 프로필</S.DialogLabel>
        <S.IconButton type="button" aria-label="상세 프로필 닫기" onClick={onDismiss}>
          <X size={20} aria-hidden="true" />
        </S.IconButton>
      </S.DialogHeader>

      {profile ? (
        <>
          <S.ScrollArea>
            {likeError && <p role="alert">{likeError}</p>}
            <S.ProfileSection aria-labelledby="detailed-profile-title">
              <S.ProfileHero>
                <S.Avatar>
                  <ProfileImage major={profile.major} fallbackSrc={profile.photo} alt={`${profile.nickname} 프로필 사진`} />
                </S.Avatar>
                <S.Identity>
                  <S.Eyebrow>함께 지낼 룸메이트를 찾고 있어요</S.Eyebrow>
                  <S.NameRow>
                    <S.ProfileName id="detailed-profile-title">{profile.nickname}</S.ProfileName>
                    {profile.mbti && <S.MbtiBadge>{profile.mbti}</S.MbtiBadge>}
                  </S.NameRow>
                  <S.AcademicSummary>
                    {profile.admissionYear || profile.year ? `${profile.admissionYear || profile.year}학번` : "학번 미등록"}
                    <span aria-hidden="true">·</span>{profile.grade || "학년 미등록"}
                  </S.AcademicSummary>
                </S.Identity>
                <S.SaveButton type="button" $liked={liked} aria-pressed={liked} onClick={toggleLike} disabled={likePending}
                  title={liked ? "좋아요 취소" : "좋아요"}
                  aria-label={`${profile.nickname}님 ${liked ? "좋아요 취소" : "좋아요"}`}>
                  <Heart size={16} fill={liked ? "currentColor" : "none"} aria-hidden="true" />
                </S.SaveButton>
              </S.ProfileHero>

              <S.BasicInfo>
                <div><dt>학부</dt><dd>{profile.faculty || profile.department || "미등록"}</dd></div>
                <div><dt>전공</dt><dd>{profile.major || "미등록"}</dd></div>
                <div><dt>MBTI</dt><dd>{profile.mbti || "미등록"}</dd></div>
              </S.BasicInfo>
              <S.Introduction>
                <MessageCircle size={18} aria-hidden="true" />
                <div><S.SmallLabel>나의 한마디</S.SmallLabel><p>{profile.message || "아직 한마디를 작성하지 않았어요."}</p></div>
              </S.Introduction>
            </S.ProfileSection>

            <S.DetailsArea>
              <S.MatchCard aria-labelledby="profile-match-title">
                {hasScore ? (
                  <S.ScoreRing $score={score} role="meter" aria-label="나와의 매칭 점수"
                    aria-valuemin={0} aria-valuemax={100} aria-valuenow={score} aria-valuetext={`${score}점, ${scoreDescription}`}>
                    <div><strong>{score}<small>점</small></strong><span>매칭 점수</span></div>
                  </S.ScoreRing>
                ) : <S.EmptyMatchIcon><Sparkles size={28} aria-hidden="true" /></S.EmptyMatchIcon>}
                <S.MatchCopy>
                  <S.MatchLabel><Sparkles size={14} aria-hidden="true" /> 나와의 매칭</S.MatchLabel>
                  <h2 id="profile-match-title">{hasScore ? scoreDescription : "아직 매칭 점수가 없어요"}</h2>
                  <p>{hasScore ? "프로필 설문을 바탕으로 살펴본 생활 습관 일치도예요."
                    : "설문 응답이 준비되면 생활 습관 일치도를 확인할 수 있어요."}</p>
                  {hasScore && <S.MatchScale><span>조율이 필요해요</span><S.MatchBar $score={score} aria-hidden="true" /><span>잘 맞아요</span></S.MatchScale>}
                </S.MatchCopy>
              </S.MatchCard>

              <S.SurveyCard>
                <S.TabList role="tablist" aria-label="생활 습관 정보">
                  {SECTIONS.map(({ id, title, icon: Icon }, index) => (
                    <S.Tab key={id} type="button" role="tab" id={`profile-tab-${id}`}
                      aria-controls={`profile-panel-${id}`} aria-selected={activeSection === id}
                      tabIndex={activeSection === id ? 0 : -1} $active={activeSection === id}
                      ref={(node) => { tabRefs.current[index] = node; }}
                      onClick={() => setActiveSection(id)} onKeyDown={(event) => handleTabKeyDown(event, index)}>
                      <Icon size={17} aria-hidden="true" />{title}
                    </S.Tab>
                  ))}
                </S.TabList>
                <S.TabPanel role="tabpanel" id={`profile-panel-${section.id}`} aria-labelledby={`profile-tab-${section.id}`} tabIndex={0}>
                  <S.SectionHeading>
                    <S.SectionIcon><SectionIcon size={21} aria-hidden="true" /></S.SectionIcon>
                    <div><h2>{section.title}</h2><p>{section.description}</p></div>
                  </S.SectionHeading>
                  {completed > 0 ? (
                    <S.AnswerGrid>
                      {section.fields.map(([id, label]) => {
                        const answer = displayAnswer(answers[id]);
                        return <S.AnswerItem key={id}><dt>{label}</dt><S.AnswerValue $empty={!answer}>{answer || "아직 응답하지 않았어요"}</S.AnswerValue></S.AnswerItem>;
                      })}
                    </S.AnswerGrid>
                  ) : (
                    <S.EmptyState>
                      <S.EmptyIcon><ClipboardList size={28} aria-hidden="true" /></S.EmptyIcon>
                      <h3>아직 작성한 {section.title} 설문이 없어요</h3>
                      <p>응답을 작성하면 이곳에 표시돼요.<br />궁금한 점은 채팅으로 먼저 이야기해 보세요.</p>
                    </S.EmptyState>
                  )}
                </S.TabPanel>
              </S.SurveyCard>

            </S.DetailsArea>
          </S.ScrollArea>
          <S.Actions>
            <S.ChatButton type="button" onClick={startChat}><MessageCircle size={19} aria-hidden="true" />채팅하기</S.ChatButton>
          </S.Actions>
        </>
      ) : (
        <S.MissingProfile role={error ? "alert" : "status"}>
          <S.EmptyIcon><UserRound size={32} aria-hidden="true" /></S.EmptyIcon>
          <h1 id="detailed-profile-title">{loading ? "프로필을 불러오고 있어요" : "프로필을 찾을 수 없어요"}</h1>
          <p>{loading ? "잠시만 기다려 주세요." : error || "존재하지 않거나 더 이상 공개되지 않는 프로필이에요."}</p>
          <S.ChatButton as={Link} to={returnTo}>{returnTo.startsWith("/chat?") ? "채팅으로 돌아가기" : "룸메찾기로 돌아가기"}<ArrowUpRight size={17} aria-hidden="true" /></S.ChatButton>
        </S.MissingProfile>
      )}
    </S.Dialog>
  );
}

function DetailedProfile() {
  const { profileId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const serverRoute = /^\d+$/.test(profileId) || new URLSearchParams(location.search).get("tab") === "recommended"
    || location.state?.profile?.source === "backend";
  const [remote, setRemote] = useState({ id: null, profile: null, error: "" });
  useEffect(() => {
    if (!serverRoute) return;
    const controller = new AbortController();
    setRemote({ id: profileId, profile: null, error: "" });
    getRoommateDetail(profileId, controller.signal).then((detail) => {
      if (String(detail?.id) !== profileId) throw new Error("프로필 응답을 확인할 수 없습니다.");
      if (!controller.signal.aborted) setRemote({ id: profileId, error: "", profile: {
        ...detail, userId: detail.id, source: "backend", message: detail.introduction,
        photo: detail.profileImageUrl, answers: detail.answers || {},
      } });
    }).catch((failure) => {
      if (!controller.signal.aborted) setRemote({ id: profileId, profile: null, error: failure.message });
    });
    return () => controller.abort();
  }, [serverRoute, profileId]);
  const selectedProfile = serverRoute && remote.id === profileId ? remote.profile : null;
  const profile = selectedProfile;
  const chatRoomId = location.state?.chatRoomId;
  const returnTo = /^\d+$/.test(String(chatRoomId)) && Number(chatRoomId) > 0
    ? `/chat?room=${encodeURIComponent(chatRoomId)}`
    : new URLSearchParams(location.search).get("tab") === "recommended"
    || location.state?.activeTab === "recommended" ? "/roommates?tab=recommended" : "/roommates";
  const dismiss = useCallback(() => {
    if (location.state?.fromProfileCard) navigate(-1);
    else navigate(returnTo, { replace: true });
  }, [location.state?.fromProfileCard, navigate, returnTo]);

  return <ProfileDialog key={profileId} profile={profile} sourceProfile={selectedProfile}
    onDismiss={dismiss} returnTo={returnTo}
    loading={serverRoute && !selectedProfile && !(remote.id === profileId && remote.error)}
    error={serverRoute && remote.id === profileId ? remote.error : ""} />;
}

export default DetailedProfile;
