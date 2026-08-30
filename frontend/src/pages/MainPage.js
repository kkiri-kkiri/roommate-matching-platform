import { useMemo, useState } from "react";
import {
  ArrowRight,
  BadgeCheck,
  BookOpen,
  Calculator,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Eye,
  Heart,
  MapPin,
  MessageCircle,
  RefreshCw,
  Search,
  ShieldCheck,
  Sparkles,
  Users,
} from "lucide-react";

import * as S from "./MainPageCss";

const ROOMMATES = [
  {
    id: 1,
    name: "김서연",
    initial: "서",
    major: "컴퓨터공학과 · 24학번",
    dormitory: "새빛관 희망",
    matchScore: 96,
    tone: "mint",
    status: "방금 접속",
    tags: ["비흡연", "아침형", "정리정돈"],
    matchNote: "생활 리듬 4개가 잘 맞아요",
    introduction:
      "평일에는 일찍 자고 일찍 일어나요. 서로의 공부 시간을 존중하는 룸메이트를 찾고 있어요.",
    preference: "조용한 생활 · 주 1회 함께 청소",
  },
  {
    id: 2,
    name: "이도윤",
    initial: "윤",
    major: "경영학과 · 23학번",
    dormitory: "한울관 희망",
    matchScore: 91,
    tone: "blue",
    status: "12분 전 접속",
    tags: ["비흡연", "야행성", "이어폰 사용"],
    matchNote: "소음·청소 기준이 잘 맞아요",
    introduction:
      "과제 때문에 늦게 자는 날이 있지만 통화나 영상은 항상 이어폰을 사용해요. 깔끔한 편입니다.",
    preference: "개인 시간 존중 · 물건 공유 전 확인",
  },
  {
    id: 3,
    name: "박하린",
    initial: "린",
    major: "시각디자인학과 · 25학번",
    dormitory: "늘봄관 희망",
    matchScore: 88,
    tone: "gold",
    status: "오늘 접속",
    tags: ["비흡연", "중간형", "주말 외출"],
    matchNote: "공용 공간 사용 습관이 비슷해요",
    introduction:
      "처음엔 조금 조용하지만 친해지면 편하게 지내는 편이에요. 향에 민감해서 환기를 자주 해요.",
    preference: "취침 전 소등 · 공용 공간 환기",
  },
];

const COMMUNITY_POSTS = [
  {
    id: 1,
    category: "생활 정보",
    title: "새빛관 세탁실, 덜 붐비는 시간대를 정리했어요",
    excerpt:
      "일주일 동안 이용해 보고 층별로 여유로운 시간을 표로 정리했습니다.",
    author: "기숙사생활러",
    time: "18분 전",
    views: 428,
    comments: 32,
  },
  {
    id: 2,
    category: "모집",
    title: "주말 아침 한강 러닝 같이 할 분 구해요",
    excerpt: "초보자 속도로 5km 정도 달려요. 이번 주 토요일부터 시작합니다.",
    author: "달리는감자",
    time: "42분 전",
    views: 285,
    comments: 19,
  },
  {
    id: 3,
    category: "나눔",
    title: "미개봉 세탁 세제 소분해서 나눔합니다",
    excerpt: "향이 맞지 않아 남은 제품을 기숙사 로비에서 나누려고 해요.",
    author: "민트초코",
    time: "1시간 전",
    views: 196,
    comments: 11,
  },
  {
    id: 4,
    category: "분실물",
    title: "학생회관 앞에서 파란색 카드지갑을 주웠어요",
    excerpt: "학생증이 함께 들어 있어 안내 데스크에 맡겨 두었습니다.",
    author: "익명의참새",
    time: "2시간 전",
    views: 154,
    comments: 7,
  },
];

const POST_FILTERS = ["전체", "생활 정보", "모집", "관심 글"];

const CALENDAR_MONTHS = [
  {
    label: "2026년 8월",
    shortLabel: "8월",
    days: 31,
    startOffset: 6,
    defaultDate: 25,
    events: {
      25: [
        {
          title: "2학기 생활관비 납부 시작",
          detail: "오전 10:00 · 생활관 포털",
          type: "납부",
        },
      ],
      30: [
        {
          title: "입사 전 온라인 교육 마감",
          detail: "오후 11:59까지",
          type: "마감",
        },
      ],
    },
  },
  {
    label: "2026년 9월",
    shortLabel: "9월",
    days: 30,
    startOffset: 2,
    defaultDate: 7,
    events: {
      2: [
        {
          title: "2학기 생활관 정규 입사",
          detail: "오전 09:00 · 각 생활관 로비",
          type: "입사",
        },
      ],
      7: [
        {
          title: "추가 입사 신청 마감",
          detail: "오후 06:00 · 생활관 포털",
          type: "신청",
        },
        {
          title: "룸메이트 생활 규칙 워크숍",
          detail: "오후 07:30 · 학생회관 2층",
          type: "행사",
        },
      ],
      21: [
        {
          title: "생활관 정기 소방 점검",
          detail: "오후 02:00부터",
          type: "점검",
        },
      ],
    },
  },
  {
    label: "2026년 10월",
    shortLabel: "10월",
    days: 31,
    startOffset: 4,
    defaultDate: 6,
    events: {
      6: [
        {
          title: "중간고사 응원 간식 배부",
          detail: "오후 06:00 · 생활관 로비",
          type: "행사",
        },
      ],
      19: [
        {
          title: "동계방학 잔류 신청 시작",
          detail: "오전 10:00 · 생활관 포털",
          type: "신청",
        },
      ],
    },
  },
];

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const INITIAL_SCORE_FORM = {
  grade: "4.1",
  distance: "65",
  priority: "0",
};

function getCalendarCells(month) {
  return [
    ...Array.from({ length: month.startOffset }, () => null),
    ...Array.from({ length: month.days }, (_, index) => index + 1),
  ];
}

function MainPage() {
  const [roommateView, setRoommateView] = useState("recommended");
  const [savedRoommateIds, setSavedRoommateIds] = useState([]);
  const [activeProfileId, setActiveProfileId] = useState(null);
  const [postFilter, setPostFilter] = useState("전체");
  const [calendarMonthIndex, setCalendarMonthIndex] = useState(1);
  const [selectedDate, setSelectedDate] = useState(
    CALENDAR_MONTHS[1].defaultDate,
  );
  const [scoreForm, setScoreForm] = useState(INITIAL_SCORE_FORM);
  const [scoreResult, setScoreResult] = useState(null);

  const visibleRoommates = useMemo(() => {
    if (roommateView === "saved") {
      return ROOMMATES.filter((roommate) =>
        savedRoommateIds.includes(roommate.id),
      );
    }

    return ROOMMATES;
  }, [roommateView, savedRoommateIds]);

  const visiblePosts = useMemo(() => {
    if (postFilter === "전체") {
      return COMMUNITY_POSTS;
    }

    if (postFilter === "관심 글") {
      return [];
    }

    return COMMUNITY_POSTS.filter((post) => post.category === postFilter);
  }, [postFilter]);

  const activeMonth = CALENDAR_MONTHS[calendarMonthIndex];
  const calendarCells = useMemo(
    () => getCalendarCells(activeMonth),
    [activeMonth],
  );
  const selectedEvents = activeMonth.events[selectedDate] || [];

  const toggleSavedRoommate = (roommateId) => {
    setSavedRoommateIds((currentIds) =>
      currentIds.includes(roommateId)
        ? currentIds.filter((id) => id !== roommateId)
        : [...currentIds, roommateId],
    );
  };

  const toggleProfile = (roommateId) => {
    setActiveProfileId((currentId) =>
      currentId === roommateId ? null : roommateId,
    );
  };

  const changeMonth = (direction) => {
    const nextIndex = calendarMonthIndex + direction;

    if (nextIndex < 0 || nextIndex >= CALENDAR_MONTHS.length) {
      return;
    }

    setCalendarMonthIndex(nextIndex);
    setSelectedDate(CALENDAR_MONTHS[nextIndex].defaultDate);
  };

  const handleScoreFieldChange = (event) => {
    const { name, value } = event.target;

    setScoreForm((currentForm) => ({
      ...currentForm,
      [name]: value,
    }));
    setScoreResult(null);
  };

  const calculateDormitoryScore = (event) => {
    event.preventDefault();

    const grade = Math.min(4.5, Math.max(0, Number(scoreForm.grade) || 0));
    const distance = Math.min(
      150,
      Math.max(0, Number(scoreForm.distance) || 0),
    );
    const priorityPoints = Number(scoreForm.priority);
    const gradePoints = Math.round((grade / 4.5) * 55);
    const distancePoints = Math.round((distance / 150) * 30);
    const total = Math.min(100, gradePoints + distancePoints + priorityPoints);

    let message = "조금 더 준비하면 가능성이 높아져요";

    if (total >= 85) {
      message = "안정적인 예상 점수예요";
    } else if (total >= 70) {
      message = "충분히 경쟁력 있는 예상 점수예요";
    }

    setScoreResult({
      total,
      gradePoints,
      distancePoints,
      priorityPoints,
      message,
    });
  };

  const resetDormitoryScore = () => {
    setScoreForm(INITIAL_SCORE_FORM);
    setScoreResult(null);
  };

  return (
    <S.Page>
      <S.Container>
        <S.Hero aria-labelledby="main-hero-title">
          <S.HeroContent>
            <S.Eyebrow>
              <Sparkles size={15} aria-hidden="true" />
              캠퍼스 생활의 좋은 시작
            </S.Eyebrow>
            <S.HeroTitle id="main-hero-title">
              나와 잘 맞는 룸메이트를, 편안하게 만나보세요
            </S.HeroTitle>
            <S.HeroDescription>
              생활 습관부터 수면 패턴까지 꼼꼼하게 비교하고, 믿을 수 있는 학교
              친구와 새로운 기숙사 생활을 시작해 보세요.
            </S.HeroDescription>
            <S.HeroActions>
              <S.PrimaryLink href="#recommended-roommates">
                <Search size={18} aria-hidden="true" />
                추천 룸메이트 보기
                <ArrowRight size={18} aria-hidden="true" />
              </S.PrimaryLink>
              <S.SecondaryLink href="#popular-community">
                <BookOpen size={18} aria-hidden="true" />
                인기 글 둘러보기
              </S.SecondaryLink>
            </S.HeroActions>
            <S.TrustNote>
              <ShieldCheck size={18} aria-hidden="true" />
              재학생 인증 프로필을 우선으로 안내해 드려요.
            </S.TrustNote>
          </S.HeroContent>

          <S.HeroSummary aria-label="오늘의 추천 요약">
            <S.SummaryTop>
              <S.SummaryIcon>
                <Users size={20} aria-hidden="true" />
              </S.SummaryIcon>
              <div>
                <S.SummaryLabel>오늘의 추천</S.SummaryLabel>
                <S.SummaryTitle>새로운 인연을 확인해 보세요</S.SummaryTitle>
              </div>
            </S.SummaryTop>
            <S.TopMatch>
              <S.TopMatchAvatar>서</S.TopMatchAvatar>
              <S.TopMatchInfo>
                <strong>김서연 님</strong>
                <span>생활 패턴 4개 일치</span>
              </S.TopMatchInfo>
              <S.TopMatchScore>96%</S.TopMatchScore>
            </S.TopMatch>
            <S.SummaryProgress aria-hidden="true">
              <span />
            </S.SummaryProgress>
            <S.SummaryChecks>
              <li>
                <CheckCircle2 size={16} aria-hidden="true" />
                비흡연 선호 일치
              </li>
              <li>
                <CheckCircle2 size={16} aria-hidden="true" />
                취침 시간대 유사
              </li>
              <li>
                <CheckCircle2 size={16} aria-hidden="true" />
                동일 생활관 희망
              </li>
            </S.SummaryChecks>
          </S.HeroSummary>
        </S.Hero>

        <S.Section id="recommended-roommates" aria-labelledby="roommate-title">
          <S.SectionHeader>
            <div>
              <S.SectionKicker>RECOMMENDED ROOMMATE</S.SectionKicker>
              <S.SectionTitle id="roommate-title">
                나와 잘 맞는 룸메이트
              </S.SectionTitle>
              <S.SectionDescription>
                입력한 생활 습관과 선호 조건을 바탕으로 추천했어요.
              </S.SectionDescription>
            </div>
            <S.TabList role="group" aria-label="룸메이트 목록 필터">
              <S.TabButton
                type="button"
                aria-pressed={roommateView === "recommended"}
                $active={roommateView === "recommended"}
                onClick={() => setRoommateView("recommended")}
              >
                추천 목록
              </S.TabButton>
              <S.TabButton
                type="button"
                aria-pressed={roommateView === "saved"}
                $active={roommateView === "saved"}
                onClick={() => setRoommateView("saved")}
              >
                관심 목록 {savedRoommateIds.length}
              </S.TabButton>
            </S.TabList>
          </S.SectionHeader>

          {visibleRoommates.length > 0 ? (
            <S.ProfileGrid>
              {visibleRoommates.map((roommate) => {
                const isSaved = savedRoommateIds.includes(roommate.id);
                const isProfileOpen = activeProfileId === roommate.id;

                return (
                  <S.ProfileCard key={roommate.id}>
                    <S.CardTopline>
                      <S.FavoriteButton
                        type="button"
                        $active={isSaved}
                        aria-pressed={isSaved}
                        aria-label={`${roommate.name} 님을 관심 목록${
                          isSaved ? "에서 삭제" : "에 추가"
                        }`}
                        onClick={() => toggleSavedRoommate(roommate.id)}
                      >
                        <Heart size={18} aria-hidden="true" />
                      </S.FavoriteButton>
                      <S.MatchBadge>
                        <Sparkles size={14} aria-hidden="true" />
                        {roommate.matchScore}% 매칭
                      </S.MatchBadge>
                    </S.CardTopline>

                    <S.ProfileIdentity>
                      <S.ProfileAvatar $tone={roommate.tone}>
                        {roommate.initial}
                      </S.ProfileAvatar>
                      <S.ProfileNameArea>
                        <S.ProfileNameRow>
                          <h3>{roommate.name}</h3>
                          <S.VerifiedBadge title="재학생 인증 완료">
                            <BadgeCheck size={17} aria-hidden="true" />
                            인증
                          </S.VerifiedBadge>
                        </S.ProfileNameRow>
                        <p>{roommate.major}</p>
                      </S.ProfileNameArea>
                    </S.ProfileIdentity>

                    <S.ProfileMeta>
                      <span>
                        <MapPin size={15} aria-hidden="true" />
                        {roommate.dormitory}
                      </span>
                      <span>
                        <Clock size={15} aria-hidden="true" />
                        {roommate.status}
                      </span>
                    </S.ProfileMeta>

                    <S.TagList aria-label={`${roommate.name} 님의 생활 습관`}>
                      {roommate.tags.map((tag) => (
                        <S.Tag key={tag}>{tag}</S.Tag>
                      ))}
                    </S.TagList>

                    <S.MatchNote>
                      <CheckCircle2 size={17} aria-hidden="true" />
                      {roommate.matchNote}
                    </S.MatchNote>

                    {isProfileOpen && (
                      <S.ProfileDetails aria-live="polite">
                        <p>{roommate.introduction}</p>
                        <span>{roommate.preference}</span>
                      </S.ProfileDetails>
                    )}

                    <S.ProfileActionRow>
                      <S.ProfileButton
                        type="button"
                        onClick={() => toggleProfile(roommate.id)}
                        aria-expanded={isProfileOpen}
                      >
                        {isProfileOpen
                          ? "간단 프로필 닫기"
                          : "간단 프로필 보기"}
                      </S.ProfileButton>
                      <S.SaveTextButton
                        type="button"
                        $active={isSaved}
                        onClick={() => toggleSavedRoommate(roommate.id)}
                      >
                        <Heart size={16} aria-hidden="true" />
                        {isSaved ? "저장됨" : "관심 저장"}
                      </S.SaveTextButton>
                    </S.ProfileActionRow>
                  </S.ProfileCard>
                );
              })}
            </S.ProfileGrid>
          ) : (
            <S.EmptyState>
              <S.EmptyIcon>
                <Heart size={24} aria-hidden="true" />
              </S.EmptyIcon>
              <h3>아직 관심 룸메이트가 없어요</h3>
              <p>추천 목록에서 마음에 드는 프로필을 저장해 보세요.</p>
              <S.EmptyAction
                type="button"
                onClick={() => setRoommateView("recommended")}
              >
                추천 목록으로 돌아가기
              </S.EmptyAction>
            </S.EmptyState>
          )}
        </S.Section>

        <S.Section id="popular-community" aria-labelledby="community-title">
          <S.SectionHeader>
            <div>
              <S.SectionKicker>COMMUNITY NOW</S.SectionKicker>
              <S.SectionTitle id="community-title">
                지금 인기 있는 이야기
              </S.SectionTitle>
              <S.SectionDescription>
                기숙사 생활에 도움이 되는 정보와 새로운 모임을 확인하세요.
              </S.SectionDescription>
            </div>
            <S.FilterList role="group" aria-label="인기 게시글 분류">
              {POST_FILTERS.map((filter) => (
                <S.FilterButton
                  key={filter}
                  type="button"
                  $active={postFilter === filter}
                  aria-pressed={postFilter === filter}
                  onClick={() => setPostFilter(filter)}
                >
                  {filter}
                </S.FilterButton>
              ))}
            </S.FilterList>
          </S.SectionHeader>

          <S.CommunityCard>
            {visiblePosts.length > 0 ? (
              <S.PostList>
                {visiblePosts.map((post, index) => (
                  <S.PostItem key={post.id}>
                    <S.PostRank aria-label={`인기 순위 ${index + 1}위`}>
                      {index + 1}
                    </S.PostRank>
                    <S.PostContent>
                      <S.PostMetaTop>
                        <S.CategoryBadge $type={post.category}>
                          {post.category}
                        </S.CategoryBadge>
                        <span>{post.time}</span>
                      </S.PostMetaTop>
                      <S.PostTitle>{post.title}</S.PostTitle>
                      <S.PostExcerpt>{post.excerpt}</S.PostExcerpt>
                      <S.PostAuthor>{post.author}</S.PostAuthor>
                    </S.PostContent>
                    <S.PostStats aria-label="게시글 반응">
                      <span>
                        <Eye size={15} aria-hidden="true" />
                        {post.views}
                      </span>
                      <span>
                        <MessageCircle size={15} aria-hidden="true" />
                        {post.comments}
                      </span>
                    </S.PostStats>
                  </S.PostItem>
                ))}
              </S.PostList>
            ) : (
              <S.CompactEmpty>
                <BookOpen size={25} aria-hidden="true" />
                <div>
                  <h3>관심 글이 아직 없어요</h3>
                  <p>유용한 글을 저장하면 이곳에서 빠르게 볼 수 있어요.</p>
                </div>
                <S.GhostButton
                  type="button"
                  onClick={() => setPostFilter("전체")}
                >
                  전체 글 보기
                </S.GhostButton>
              </S.CompactEmpty>
            )}
          </S.CommunityCard>
        </S.Section>

        <S.Section aria-labelledby="campus-tools-title">
          <S.SectionHeader>
            <div>
              <S.SectionKicker>CAMPUS LIVING TOOLS</S.SectionKicker>
              <S.SectionTitle id="campus-tools-title">
                기숙사 생활, 미리 준비하세요
              </S.SectionTitle>
              <S.SectionDescription>
                중요한 일정을 챙기고 간단한 예상 점수도 확인할 수 있어요.
              </S.SectionDescription>
            </div>
          </S.SectionHeader>

          <S.UtilityGrid>
            <S.ToolCard aria-labelledby="calendar-title">
              <S.ToolHeader>
                <S.ToolHeading>
                  <S.ToolIcon $tone="blue">
                    <CalendarDays size={21} aria-hidden="true" />
                  </S.ToolIcon>
                  <div>
                    <span>생활관 캘린더</span>
                    <h3 id="calendar-title">놓치면 안 되는 일정</h3>
                  </div>
                </S.ToolHeading>
                <S.MonthControls>
                  <button
                    type="button"
                    aria-label="이전 달 보기"
                    disabled={calendarMonthIndex === 0}
                    onClick={() => changeMonth(-1)}
                  >
                    <ChevronLeft size={18} aria-hidden="true" />
                  </button>
                  <strong>{activeMonth.label}</strong>
                  <button
                    type="button"
                    aria-label="다음 달 보기"
                    disabled={calendarMonthIndex === CALENDAR_MONTHS.length - 1}
                    onClick={() => changeMonth(1)}
                  >
                    <ChevronRight size={18} aria-hidden="true" />
                  </button>
                </S.MonthControls>
              </S.ToolHeader>

              <S.CalendarWeekdays aria-hidden="true">
                {WEEKDAYS.map((weekday) => (
                  <span key={weekday}>{weekday}</span>
                ))}
              </S.CalendarWeekdays>
              <S.CalendarGrid>
                {calendarCells.map((day, index) => {
                  if (day === null) {
                    return <S.CalendarBlank key={`blank-${index}`} />;
                  }

                  const hasEvent = Boolean(activeMonth.events[day]);
                  const isSelected = selectedDate === day;
                  const weekdayIndex = (activeMonth.startOffset + day - 1) % 7;

                  return (
                    <S.CalendarDay
                      key={day}
                      type="button"
                      $selected={isSelected}
                      $hasEvent={hasEvent}
                      $dayTone={
                        weekdayIndex === 0
                          ? "sunday"
                          : weekdayIndex === 6
                            ? "saturday"
                            : "weekday"
                      }
                      aria-pressed={isSelected}
                      aria-label={`${activeMonth.shortLabel} ${day}일${
                        hasEvent ? ", 일정 있음" : ""
                      }`}
                      onClick={() => setSelectedDate(day)}
                    >
                      <span>{day}</span>
                    </S.CalendarDay>
                  );
                })}
              </S.CalendarGrid>

              <S.EventPreview aria-live="polite">
                <S.EventPreviewHeader>
                  <div>
                    <span>{activeMonth.shortLabel}</span>
                    <strong>{selectedDate}</strong>
                  </div>
                  <p>{selectedDate}일 일정</p>
                </S.EventPreviewHeader>

                {selectedEvents.length > 0 ? (
                  <S.EventList>
                    {selectedEvents.map((eventItem) => (
                      <S.EventItem key={`${eventItem.title}-${eventItem.type}`}>
                        <S.EventMarker />
                        <div>
                          <S.EventType>{eventItem.type}</S.EventType>
                          <strong>{eventItem.title}</strong>
                          <span>{eventItem.detail}</span>
                        </div>
                      </S.EventItem>
                    ))}
                  </S.EventList>
                ) : (
                  <S.EventEmpty>
                    <CalendarDays size={19} aria-hidden="true" />
                    선택한 날짜에는 등록된 일정이 없어요.
                  </S.EventEmpty>
                )}
              </S.EventPreview>
            </S.ToolCard>

            <S.ToolCard aria-labelledby="calculator-title">
              <S.ToolHeader>
                <S.ToolHeading>
                  <S.ToolIcon $tone="mint">
                    <Calculator size={21} aria-hidden="true" />
                  </S.ToolIcon>
                  <div>
                    <span>기숙사 점수 계산기</span>
                    <h3 id="calculator-title">내 예상 점수 알아보기</h3>
                  </div>
                </S.ToolHeading>
              </S.ToolHeader>

              <S.ScoreForm onSubmit={calculateDormitoryScore}>
                <S.FormGrid>
                  <S.FieldGroup>
                    <label htmlFor="grade">직전 학기 성적</label>
                    <S.InputWithSuffix>
                      <input
                        id="grade"
                        name="grade"
                        type="number"
                        min="0"
                        max="4.5"
                        step="0.1"
                        value={scoreForm.grade}
                        onChange={handleScoreFieldChange}
                        aria-describedby="grade-hint"
                      />
                      <span>/ 4.5</span>
                    </S.InputWithSuffix>
                    <small id="grade-hint">최대 55점 반영</small>
                  </S.FieldGroup>

                  <S.FieldGroup>
                    <label htmlFor="distance">학교까지 거리</label>
                    <S.InputWithSuffix>
                      <input
                        id="distance"
                        name="distance"
                        type="number"
                        min="0"
                        max="150"
                        value={scoreForm.distance}
                        onChange={handleScoreFieldChange}
                        aria-describedby="distance-hint"
                      />
                      <span>km</span>
                    </S.InputWithSuffix>
                    <small id="distance-hint">최대 30점 반영</small>
                  </S.FieldGroup>

                  <S.FieldGroup $wide>
                    <label htmlFor="priority">우선 선발 조건</label>
                    <S.SelectWrap>
                      <select
                        id="priority"
                        name="priority"
                        value={scoreForm.priority}
                        onChange={handleScoreFieldChange}
                      >
                        <option value="0">해당 없음</option>
                        <option value="8">원거리 특별 기준 (+8점)</option>
                        <option value="15">우선 선발 대상 (+15점)</option>
                      </select>
                      <ChevronRight size={17} aria-hidden="true" />
                    </S.SelectWrap>
                  </S.FieldGroup>
                </S.FormGrid>

                <S.FormActions>
                  <S.CalculateButton type="submit">
                    예상 점수 계산하기
                    <ArrowRight size={17} aria-hidden="true" />
                  </S.CalculateButton>
                  <S.ResetButton type="button" onClick={resetDormitoryScore}>
                    <RefreshCw size={16} aria-hidden="true" />
                    초기화
                  </S.ResetButton>
                </S.FormActions>
              </S.ScoreForm>

              {scoreResult ? (
                <S.ScoreResult aria-live="polite">
                  <S.ScoreSummary>
                    <S.ScoreRing $score={scoreResult.total}>
                      <S.ScoreRingInner>
                        <strong>{scoreResult.total}</strong>
                        <span>점</span>
                      </S.ScoreRingInner>
                    </S.ScoreRing>
                    <div>
                      <S.ResultLabel>나의 예상 총점</S.ResultLabel>
                      <S.ResultTitle>{scoreResult.message}</S.ResultTitle>
                      <S.ResultDescription>
                        입력한 조건을 기준으로 간단히 계산했어요.
                      </S.ResultDescription>
                    </div>
                  </S.ScoreSummary>

                  <S.ScoreBreakdown>
                    <S.ScoreRow>
                      <div>
                        <span>성적</span>
                        <strong>{scoreResult.gradePoints} / 55</strong>
                      </div>
                      <S.ScoreBar>
                        <S.ScoreBarFill
                          $value={(scoreResult.gradePoints / 55) * 100}
                        />
                      </S.ScoreBar>
                    </S.ScoreRow>
                    <S.ScoreRow>
                      <div>
                        <span>통학 거리</span>
                        <strong>{scoreResult.distancePoints} / 30</strong>
                      </div>
                      <S.ScoreBar>
                        <S.ScoreBarFill
                          $value={(scoreResult.distancePoints / 30) * 100}
                        />
                      </S.ScoreBar>
                    </S.ScoreRow>
                    <S.ScoreRow>
                      <div>
                        <span>우선 조건</span>
                        <strong>{scoreResult.priorityPoints}점</strong>
                      </div>
                      <S.ScoreBar>
                        <S.ScoreBarFill
                          $value={(scoreResult.priorityPoints / 15) * 100}
                        />
                      </S.ScoreBar>
                    </S.ScoreRow>
                  </S.ScoreBreakdown>
                </S.ScoreResult>
              ) : (
                <S.ScoreEmpty>
                  <S.EmptyCalculatorIcon>
                    <Calculator size={23} aria-hidden="true" />
                  </S.EmptyCalculatorIcon>
                  <div>
                    <h4>아직 계산된 점수가 없어요</h4>
                    <p>위 조건을 확인하고 예상 점수를 계산해 보세요.</p>
                  </div>
                </S.ScoreEmpty>
              )}

              <S.Disclaimer>
                학교별 실제 선발 기준과 무관한 UI 확인용 예시 계산입니다.
              </S.Disclaimer>
            </S.ToolCard>
          </S.UtilityGrid>
        </S.Section>
      </S.Container>
    </S.Page>
  );
}

export default MainPage;
