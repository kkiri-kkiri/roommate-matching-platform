import { getCommunityPosts } from "../../api/communityApi";
import { getRecommendedRoommates } from "../../api/roommateApi";
import { useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  ArrowRight,
  BookOpen,
  Calculator,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Eye,
  MessageCircle,
  RefreshCw,
} from "lucide-react";

import { KOREA_REGIONS } from "../../data/koreaRegions";
import ProfileCard from "../../components/common/ProfileCard/ProfileCard";
import * as S from "./MainPageCss";
import { dateKey, useCalendarData } from "../../calendar/CalendarDataContext";
import { eventTimeLabel, overlaps } from "../../calendar/calendarDates";

const POST_FILTERS = ["전체", "자유", "나눔", "택배", "민원"];

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const REGION_GROUPS = {
  capital: { label: "수도권", points: 10 },
  central: { label: "중부권", points: 20 },
  southern: { label: "남부권", points: 30 },
};

const INITIAL_SCORE_FORM = {
  province: "",
  city: "",
  grade: "",
  priority: "",
};

const GRADE_OPTIONS = [
  { value: "4.38", label: "4.26 ~ 4.50" },
  { value: "4.13", label: "4.01 ~ 4.25" },
  { value: "3.88", label: "3.76 ~ 4.00" },
  { value: "3.63", label: "3.51 ~ 3.75" },
  { value: "3.38", label: "3.26 ~ 3.50" },
  { value: "3.13", label: "3.01 ~ 3.25" },
  { value: "2.88", label: "2.76 ~ 3.00" },
  { value: "2.63", label: "2.51 ~ 2.75" },
  { value: "2.50", label: "2.50 이하" },
];

const REGION_OPTIONS = Object.fromEntries(
  KOREA_REGIONS.map((province) => [
    province.value,
    { label: province.label, points: REGION_GROUPS[province.group].points },
  ]),
);

function getCalendarCells(month) {
  return [
    ...Array.from({ length: month.startOffset }, () => null),
    ...Array.from({ length: month.days }, (_, index) => index + 1),
  ];
}

function MainPage({ currentUser }) {
  const { hash } = useLocation();
  const userId = currentUser?.id;
  const [roommates, setRoommates] = useState([]);
  const [roommatesLoading, setRoommatesLoading] = useState(true);
  const [roommatesError, setRoommatesError] = useState("");
  const [roommatesRevision, setRoommatesRevision] = useState(0);
  const [visiblePosts, setVisiblePosts] = useState([]);
  const [postsLoading, setPostsLoading] = useState(true);
  const [postsError, setPostsError] = useState("");
  const [postsRevision, setPostsRevision] = useState(0);
  const [postFilter, setPostFilter] = useState("전체");
  const [initialDate] = useState(() => new Date());
  const [calendarMonth, setCalendarMonth] = useState(
    () => new Date(initialDate.getFullYear(), initialDate.getMonth(), 1),
  );
  const { events, categorySettings, loading, error, retry } = useCalendarData(
    dateKey(calendarMonth),
    dateKey(new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 0)),
  );
  const [selectedDate, setSelectedDate] = useState(initialDate.getDate());
  const [scoreForm, setScoreForm] = useState(INITIAL_SCORE_FORM);
  const [scoreResult, setScoreResult] = useState(null);

  useEffect(() => {
    const sectionId = hash.slice(1);
    if (!sectionId) return undefined;

    const frameId = window.requestAnimationFrame(() => {
      document.getElementById(sectionId)?.scrollIntoView?.({ block: "start" });
    });

    return () => window.cancelAnimationFrame(frameId);
  }, [hash]);

  useEffect(() => {
    const controller = new AbortController();
    setRoommates([]);
    setRoommatesError("");
    setRoommatesLoading(Boolean(userId));
    if (!userId) return () => controller.abort();

    getRecommendedRoommates(controller.signal)
      .then((data) => {
        if (controller.signal.aborted) return;
        if (!Array.isArray(data?.content)) {
          throw new Error("추천 룸메이트 응답을 확인할 수 없습니다.");
        }
        setRoommates(data.content.slice(0, 3).map((profile) => ({
          ...profile,
          userId: profile.id,
          source: "backend",
          photo: profile.profileImageUrl,
          message: profile.introduction,
        })));
      })
      .catch((error) => {
        if (!controller.signal.aborted) setRoommatesError(error.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setRoommatesLoading(false);
      });
    return () => controller.abort();
  }, [userId, roommatesRevision]);

  useEffect(() => {
    let cancelled = false;
    setPostsLoading(true); setPostsError(""); setVisiblePosts([]);
    getCommunityPosts({ size: 3, sort: "VIEWS", category: postFilter === "전체" ? undefined : postFilter + "게시판" })
      .then((data) => { if (!cancelled) setVisiblePosts(data.content.map((post) => ({
        ...post, category: post.category.replace("게시판", ""), excerpt: post.contentPreview,
        author: post.author?.nickname, time: new Date(post.createdAt).toLocaleDateString("ko-KR"), comments: post.commentCount,
      }))); })
      .catch((error) => { if (!cancelled) setPostsError(error.message); })
      .finally(() => { if (!cancelled) setPostsLoading(false); });
    return () => { cancelled = true; };
  }, [postFilter, postsRevision]);

  const activeMonth = useMemo(
    () => ({
      label: calendarMonth.toLocaleDateString("ko-KR", {
        year: "numeric",
        month: "long",
      }),
      shortLabel: calendarMonth.toLocaleDateString("ko-KR", { month: "long" }),
      days: new Date(
        calendarMonth.getFullYear(),
        calendarMonth.getMonth() + 1,
        0,
      ).getDate(),
      startOffset: calendarMonth.getDay(),
    }),
    [calendarMonth],
  );
  const monthEvents = useMemo(() => {
    const days = {};
    const lastDay = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 0).getDate();
    for (let day = 1; day <= lastDay; day++) {
      const value = dateKey(new Date(calendarMonth.getFullYear(), calendarMonth.getMonth(), day));
      days[day] = events.filter((event) => overlaps(event, value))
        .sort((first, second) => first.startTime.localeCompare(second.startTime));
    }
    return days;
  }, [events, calendarMonth]);
  const categoryLabels = Object.fromEntries(
    categorySettings.map(({ id, label }) => [id, label]),
  );

  const calendarCells = useMemo(
    () => getCalendarCells(activeMonth),
    [activeMonth],
  );

  const selectedEvents = monthEvents[selectedDate] || [];

  const changeMonth = (direction) => {
    setCalendarMonth(
      (current) =>
        new Date(current.getFullYear(), current.getMonth() + direction, 1),
    );
    setSelectedDate(1);
  };

  const selectedProvince =
    KOREA_REGIONS.find((province) => province.value === scoreForm.province) ||
    KOREA_REGIONS[0];
  const selectedCity =
    selectedProvince.cities.find((city) => city.value === scoreForm.city) ||
    selectedProvince.cities[0];

  const handleScoreFieldChange = (event) => {
    const { name, value } = event.target;

    setScoreForm((currentForm) => {
      const nextForm = { ...currentForm, [name]: value };

      if (name === "province") {
        const selected = KOREA_REGIONS.find(
          (province) => province.value === value,
        );
        const nextCity = selected?.cities[0]?.value || "";
        return { ...nextForm, city: nextCity };
      }

      return nextForm;
    });

    setScoreResult(null);
  };

  const calculateDormitoryScore = (event) => {
    event.preventDefault();

    const grade = Math.min(4.5, Math.max(0, Number(scoreForm.grade) || 0));
    const province = KOREA_REGIONS.find(
      (item) => item.value === scoreForm.province,
    );
    const regionPoints = province
      ? REGION_OPTIONS[province.value]?.points || 0
      : 0;

    const priorityPoints = Number(scoreForm.priority);

    const gradePoints = Math.round((grade / 4.5) * 55);

    const total = Math.min(100, gradePoints + regionPoints + priorityPoints);

    let message = "조금 더 준비하면 가능성이 높아져요";

    if (total >= 85) {
      message = "안정적인 예상 점수예요";
    } else if (total >= 70) {
      message = "충분히 경쟁력 있는 예상 점수예요";
    }

    setScoreResult({
      total,
      gradePoints,
      regionPoints,
      priorityPoints,
      message,
      regionLabel: province
        ? `${province.label} · ${selectedCity.label}`
        : "지역 미선택",
    });
  };

  const resetDormitoryScore = () => {
    setScoreForm(INITIAL_SCORE_FORM);
    setScoreResult(null);
  };

  return (
    <S.Page>
      <S.Container>
        <S.Section
          $first
          id="recommended-roommates"
          aria-labelledby="roommate-title"
        >
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

            <S.MoreLink as={Link} to="/roommates?tab=recommended">
              더보기
              <ArrowRight size={17} aria-hidden="true" />
            </S.MoreLink>
          </S.SectionHeader>

          {!userId ? (
            <div>
              <p role="status">로그인 후 추천 프로필을 확인할 수 있어요.</p>
              <S.MoreLink as={Link} to="/login">로그인하기</S.MoreLink>
            </div>
          ) : roommatesLoading ? (
            <p role="status">추천 룸메이트를 불러오는 중이에요.</p>
          ) : roommatesError ? (
            <div role="alert">
              {roommatesError}{" "}
              <S.GhostButton type="button" onClick={() => setRoommatesRevision((value) => value + 1)}>
                다시 시도
              </S.GhostButton>
            </div>
          ) : roommates.length === 0 ? (
            <p role="status">아직 추천할 룸메이트가 없어요. 프로필과 선호 설문을 확인해 주세요.</p>
          ) : (
            <S.ProfileGrid>
              {roommates.map((roommate) => (
                <div key={roommate.id}>
                  <ProfileCard
                    profile={roommate}
                    activeTab="recommended"
                    showOverlay={false}
                  />
                </div>
              ))}
            </S.ProfileGrid>
          )}
        </S.Section>

        <S.Section id="popular-community" aria-labelledby="community-title">
          <S.SectionHeader>
            <div>
              <S.SectionKicker>COMMUNITY NOW</S.SectionKicker>

              <S.SectionTitle id="community-title">
                지금 인기 있는 기숙사 이야기
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
            {postsLoading ? <p role="status">게시글을 불러오는 중이에요.</p> : postsError ? <div role="alert">{postsError} <S.GhostButton onClick={() => setPostsRevision((value) => value + 1)}>다시 시도</S.GhostButton></div> : visiblePosts.length > 0 ? (
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

                      <S.PostTitle as={Link} to={`/community/posts/${post.id}`}>{post.title}</S.PostTitle>

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
                  <h3>이 분류의 인기 글이 아직 없어요</h3>

                  <p>다른 분류를 선택해 새로운 이야기를 확인해 보세요.</p>
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
          <S.UtilityGrid>
            <div>
              <S.SectionHeader>
                <div>
                  <S.SectionKicker>CAMPUS LIVING TOOLS</S.SectionKicker>

                  <S.SectionTitle id="campus-tools-title">
                    학교생활에 필요한 모든 일정을 한눈에
                  </S.SectionTitle>

                  <S.SectionDescription>
                    학사 일정부터 기숙사 일정까지 필요한 일정을 한 번에
                    확인해보세요.
                  </S.SectionDescription>
                </div>
              </S.SectionHeader>

              <S.ToolCard id="campus-calendar" aria-label="캘린더">
                {loading && <p role="status">일정을 불러오는 중이에요.</p>}
                {error && <div role="alert">{error} <button type="button" onClick={retry}>다시 시도</button></div>}
                <S.ToolHeader>
                  <S.MonthControls>
                    <button
                      type="button"
                      aria-label="이전 달 보기"
                      onClick={() => changeMonth(-1)}
                    >
                      <ChevronLeft size={18} aria-hidden="true" />
                    </button>

                    <strong>{activeMonth.label}</strong>

                    <button
                      type="button"
                      aria-label="다음 달 보기"
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

                    const hasEvent = Boolean(monthEvents[day]?.length);

                    const isSelected = selectedDate === day;

                    const weekdayIndex =
                      (activeMonth.startOffset + day - 1) % 7;

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
                        <S.EventItem key={eventItem.id}>
                          <S.EventMarker />

                          <div>
                            <S.EventType>
                              {categoryLabels[eventItem.category]}
                            </S.EventType>

                            <strong>{eventItem.title}</strong>

                            <span>
                              {eventTimeLabel(eventItem)}
                            </span>
                          </div>
                        </S.EventItem>
                      ))}
                    </S.EventList>
                  ) : !loading && !error && (
                    <S.EventEmpty>
                      <CalendarDays size={19} aria-hidden="true" />
                      선택한 날짜에는 등록된 일정이 없어요.
                    </S.EventEmpty>
                  )}
                </S.EventPreview>
              </S.ToolCard>
            </div>

            <div>
              <S.SectionHeader>
                <div>
                  <S.SectionKicker>DORMITORY SCORE CALCULATOR</S.SectionKicker>

                  <S.OutsideToolTitle id="calculator-title">
                    내 기숙사 예상 점수 계산기
                  </S.OutsideToolTitle>

                  <S.SectionDescription>
                    지역과 조건을 선택하고 기숙사 배점 기준에 따른 예상 점수를
                    확인해보세요.
                  </S.SectionDescription>
                </div>
              </S.SectionHeader>

              <S.ToolCard aria-labelledby="calculator-title">
                <S.ScoreForm onSubmit={calculateDormitoryScore}>
                  <S.FormGrid>
                    <S.FieldGroup $wide>
                      <label htmlFor="province">지역</label>

                      <S.RegionSelectRow>
                        <S.SelectWrap>
                          <select
                            id="province"
                            name="province"
                            value={scoreForm.province}
                            onChange={handleScoreFieldChange}
                            aria-describedby="region-hint"
                          >
                            <option value="" disabled>
                              선택해주세요
                            </option>
                            {KOREA_REGIONS.map((province) => (
                              <option
                                key={province.value}
                                value={province.value}
                              >
                                {province.label}
                              </option>
                            ))}
                          </select>

                          <ChevronRight size={17} aria-hidden="true" />
                        </S.SelectWrap>

                        <S.SelectWrap>
                          <select
                            id="city"
                            name="city"
                            value={scoreForm.city}
                            onChange={handleScoreFieldChange}
                            aria-label="세부 지역"
                            disabled={!scoreForm.province}
                          >
                            <option value="" disabled>
                              선택해주세요
                            </option>
                            {selectedProvince.cities.map((city) => (
                              <option key={city.value} value={city.value}>
                                {city.label}
                              </option>
                            ))}
                          </select>

                          <ChevronRight size={17} aria-hidden="true" />
                        </S.SelectWrap>
                      </S.RegionSelectRow>

                      <small id="region-hint">
                        시·도를 고른 뒤 세부 지역을 선택해 주세요.
                      </small>
                    </S.FieldGroup>

                    <S.FieldGroup>
                      <label htmlFor="grade">직전 학기 성적</label>

                      <S.SelectWrap>
                        <select
                          id="grade"
                          name="grade"
                          value={scoreForm.grade}
                          onChange={handleScoreFieldChange}
                          aria-describedby="grade-hint"
                        >
                          <option value="" disabled>
                            선택해주세요
                          </option>
                          {GRADE_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>
                              {option.label}
                            </option>
                          ))}
                        </select>

                        <ChevronRight size={17} aria-hidden="true" />
                      </S.SelectWrap>

                      <small id="grade-hint">
                        성적 구간의 대표값으로 최대 55점 반영
                      </small>
                    </S.FieldGroup>

                    <S.FieldGroup>
                      <label htmlFor="priority">가산점</label>

                      <S.SelectWrap>
                        <select
                          id="priority"
                          name="priority"
                          value={scoreForm.priority}
                          onChange={handleScoreFieldChange}
                        >
                          <option value="" disabled>
                            선택해주세요
                          </option>
                          <option value="0">해당 없음</option>

                          <option value="3">봉사활동 이수자 (+3점)</option>

                          <option value="3">사회봉사 이수자 (+3점)</option>
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
                          <span>직전 학기 성적</span>

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
                          <span>지역</span>

                          <strong>{scoreResult.regionLabel}</strong>
                        </div>

                        <S.ScoreBar>
                          <S.ScoreBarFill
                            $value={(scoreResult.regionPoints / 30) * 100}
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
            </div>
          </S.UtilityGrid>
        </S.Section>
      </S.Container>
    </S.Page>
  );
}

export default MainPage;
