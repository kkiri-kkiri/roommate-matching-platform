import { useMemo, useRef, useState } from "react";
import {
  BadgeCheck,
  Check,
  Info,
  MapPin,
  RotateCcw,
  SlidersHorizontal,
  Sparkles,
  UserRoundSearch,
  X,
} from "lucide-react";

import * as S from "./RoommateSearchPageCss";

const FILTER_GROUPS = [
  {
    id: "dormitory",
    label: "생활관",
    options: ["전체", "나래", "호연", "비룡", "창조"],
  },
  {
    id: "rhythm",
    label: "생활 리듬",
    options: ["전체", "아침형", "중간형", "야행성"],
  },
  {
    id: "smoking",
    label: "흡연 여부",
    options: ["전체", "비흡연", "전자담배", "일반담배"],
  },
  {
    id: "interaction",
    label: "교류 스타일",
    options: ["전체", "친하게", "적당히", "개인 시간"],
  },
];

const DEFAULT_FILTERS = FILTER_GROUPS.reduce(
  (filters, group) => ({ ...filters, [group.id]: "전체" }),
  {},
);

function createProfilePhoto(background, skin, hair, shirt) {
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160">
      <rect width="160" height="160" rx="24" fill="${background}" />
      <circle cx="80" cy="66" r="31" fill="${skin}" />
      <path d="M47 66c0-27 15-43 36-43 23 0 34 17 34 39-11-2-21-8-28-18-8 12-22 20-42 22Z" fill="${hair}" />
      <path d="M35 160c3-35 20-53 45-53s42 18 45 53H35Z" fill="${shirt}" />
      <circle cx="68" cy="67" r="2.4" fill="#3d3532" />
      <circle cx="92" cy="67" r="2.4" fill="#3d3532" />
      <path d="M71 83c6 5 12 5 18 0" fill="none" stroke="#9b6254" stroke-width="2.5" stroke-linecap="round" />
    </svg>
  `;

  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

const PROFILES = [
  {
    id: 1,
    nickname: "포근한구름",
    admissionYear: "24",
    major: "소프트웨어융합전공",
    dormitory: "나래",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "적당히",
    matchScore: 94,
    message: "서로의 공부 시간을 존중하면서 편하게 지내고 싶어요.",
    tags: ["비흡연", "아침형", "주 1회 청소"],
    photo: createProfilePhoto("#dff6f2", "#f2c5a7", "#363a46", "#00a79f"),
  },
  {
    id: 2,
    nickname: "새벽별",
    admissionYear: "23",
    major: "경영학전공",
    dormitory: "호연",
    rhythm: "야행성",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 88,
    message: "늦게 자는 편이지만 방에서는 늘 이어폰을 사용해요.",
    tags: ["이어폰 사용", "깔끔해요", "개인 시간"],
    photo: createProfilePhoto("#e6edff", "#eab996", "#2c2625", "#3478f6"),
  },
  {
    id: 3,
    nickname: "민트초코",
    admissionYear: "25",
    major: "시각미디어디자인전공",
    dormitory: "비룡",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "친하게",
    matchScore: 82,
    message: "같이 밥도 먹고 소소한 이야기를 나누는 룸메를 찾아요.",
    tags: ["향 제품 안 써요", "중간형", "같이 청소"],
    photo: createProfilePhoto("#fff0e1", "#f4c7a4", "#71452f", "#ff9f4a"),
  },
  {
    id: 4,
    nickname: "정리왕",
    admissionYear: "22",
    major: "건축공학전공",
    dormitory: "창조",
    rhythm: "아침형",
    smoking: "일반담배",
    interaction: "적당히",
    matchScore: 76,
    message: "공용 공간은 깔끔하게, 생활 규칙은 대화로 맞춰가요.",
    tags: ["일반담배", "매일 정리", "일찍 취침"],
    photo: createProfilePhoto("#efe8ff", "#dca77f", "#33251f", "#7657d6"),
  },
  {
    id: 5,
    nickname: "느긋한오후",
    admissionYear: "24",
    major: "식품영양학전공",
    dormitory: "나래",
    rhythm: "중간형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 69,
    message: "조용하고 여유로운 분위기에서 각자의 루틴을 지켜요.",
    tags: ["비흡연", "조용한 방", "주말 외출"],
    photo: createProfilePhoto("#fce8ed", "#f0bd9d", "#4c3028", "#d8607a"),
  },
  {
    id: 6,
    nickname: "한걸음",
    admissionYear: "25",
    major: "전자공학전공",
    dormitory: "호연",
    rhythm: "야행성",
    smoking: "전자담배",
    interaction: "친하게",
    matchScore: 63,
    message: "서로 배려할 부분을 먼저 이야기하는 룸메이트가 되고 싶어요.",
    tags: ["전자담배", "야행성", "대화 환영"],
    photo: createProfilePhoto("#e7f2ff", "#dba77f", "#252f3a", "#456fa8"),
  },
  {
    id: 7,
    nickname: "조용한숲",
    admissionYear: "21",
    major: "조경학전공",
    dormitory: "비룡",
    rhythm: "아침형",
    smoking: "비흡연",
    interaction: "개인 시간",
    matchScore: 57,
    message: "말수는 적지만 필요한 일은 바로바로 소통하는 편이에요.",
    tags: ["아침형", "환기 자주", "각자 청소"],
    photo: createProfilePhoto("#e8f3e7", "#efc09e", "#3c312c", "#5d8e62"),
  },
  {
    id: 8,
    nickname: "따뜻한차",
    admissionYear: "23",
    major: "상담심리교육전공",
    dormitory: "창조",
    rhythm: "중간형",
    smoking: "일반담배",
    interaction: "친하게",
    matchScore: 54,
    message: "작은 불편도 쌓아두지 않고 편하게 이야기했으면 해요.",
    tags: ["일반담배", "대화 좋아요", "같이 식사"],
    photo: createProfilePhoto("#fff4d8", "#e8b18c", "#4d3429", "#d9a52e"),
  },
];

function RoommateSearchPage() {
  const [activeTab, setActiveTab] = useState("all");
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const tabRefs = useRef({});

  const activeFilterEntries = useMemo(
    () =>
      FILTER_GROUPS.flatMap((group) => {
        const value = filters[group.id];

        return value === "전체" ? [] : [{ id: group.id, label: group.label, value }];
      }),
    [filters],
  );

  const visibleProfiles = useMemo(() => {
    const profilesForTab =
      activeTab === "recommended"
        ? PROFILES.filter((profile) => profile.matchScore >= 60)
        : PROFILES;

    return profilesForTab.filter((profile) =>
      FILTER_GROUPS.every((group) => {
        const selectedValue = filters[group.id];

        return selectedValue === "전체" || profile[group.id] === selectedValue;
      }),
    );
  }, [activeTab, filters]);

  const updateFilter = (filterId, value) => {
    setFilters((currentFilters) => ({
      ...currentFilters,
      [filterId]: value,
    }));
  };

  const resetFilters = () => {
    setFilters(DEFAULT_FILTERS);
  };

  const removeFilter = (filterId) => {
    updateFilter(filterId, "전체");
  };

  const handleTabKeyDown = (event, currentTab) => {
    let nextTab;

    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      nextTab = currentTab === "all" ? "recommended" : "all";
    } else if (event.key === "Home") {
      nextTab = "all";
    } else if (event.key === "End") {
      nextTab = "recommended";
    } else {
      return;
    }

    event.preventDefault();
    setActiveTab(nextTab);
    tabRefs.current[nextTab]?.focus();
  };

  return (
    <S.Page>
      <S.PageReset />

      <S.Content>
        <S.Intro>
          <S.Eyebrow>
            <Sparkles size={15} aria-hidden="true" />
            FIND YOUR ROOMMATE
          </S.Eyebrow>

          <S.PageTitle>나와 잘 맞는 룸메이트를 찾아보세요</S.PageTitle>

          <S.PageDescription>
            생활관과 생활 습관을 살펴보고, 함께 편안한 일상을 만들 룸메이트를
            만나보세요.
          </S.PageDescription>
        </S.Intro>

        <S.DiscoveryControls>
          <S.TabsRow>
            <S.TabsArea>
              <S.TabList role="tablist" aria-label="프로필 목록 구분">
                <S.TabButton
                  ref={(button) => {
                    tabRefs.current.all = button;
                  }}
                  id="all-profile-tab"
                  type="button"
                  role="tab"
                  $active={activeTab === "all"}
                  aria-selected={activeTab === "all"}
                  aria-controls="roommate-profile-panel"
                  tabIndex={activeTab === "all" ? 0 : -1}
                  onClick={() => setActiveTab("all")}
                  onKeyDown={(event) => handleTabKeyDown(event, "all")}
                >
                  전체 프로필
                </S.TabButton>

                <S.TabButton
                  ref={(button) => {
                    tabRefs.current.recommended = button;
                  }}
                  id="recommended-profile-tab"
                  type="button"
                  role="tab"
                  $active={activeTab === "recommended"}
                  aria-selected={activeTab === "recommended"}
                  aria-controls="roommate-profile-panel"
                  aria-label="추천 프로필"
                  aria-describedby="recommendation-tooltip"
                  tabIndex={activeTab === "recommended" ? 0 : -1}
                  onClick={() => setActiveTab("recommended")}
                  onKeyDown={(event) =>
                    handleTabKeyDown(event, "recommended")
                  }
                >
                  추천 프로필
                  <S.TooltipHost>
                    <S.TooltipIcon aria-hidden="true">
                      <Info size={17} />
                    </S.TooltipIcon>
                    <S.Tooltip id="recommendation-tooltip" role="tooltip">
                      매칭점수 60점 이상 프로필로 구성
                    </S.Tooltip>
                  </S.TooltipHost>
                </S.TabButton>
              </S.TabList>
            </S.TabsArea>

            <S.FilterToggle
              type="button"
              aria-expanded={isFilterOpen}
              aria-controls="roommate-filter-panel"
              onClick={() => setIsFilterOpen((isOpen) => !isOpen)}
            >
              {isFilterOpen ? (
                <X size={17} aria-hidden="true" />
              ) : (
                <SlidersHorizontal size={17} aria-hidden="true" />
              )}
              {isFilterOpen ? "필터 닫기" : "필터 열기"}
            </S.FilterToggle>
          </S.TabsRow>

          <S.FilterStatus aria-live="polite">
            <S.FilterStatusContent>
              <S.FilterStatusLabel>적용된 필터</S.FilterStatusLabel>

              {activeFilterEntries.length > 0 ? (
                <S.AppliedFilterList>
                  {activeFilterEntries.map((filter) => (
                    <S.AppliedFilter key={filter.id}>
                      <span>
                        {filter.label} · {filter.value}
                      </span>
                      <button
                        type="button"
                        aria-label={`${filter.label} ${filter.value} 필터 취소`}
                        onClick={() => removeFilter(filter.id)}
                      >
                        <X size={13} aria-hidden="true" />
                      </button>
                    </S.AppliedFilter>
                  ))}
                </S.AppliedFilterList>
              ) : (
                <S.NoFilterText>현재 적용된 필터가 없어요.</S.NoFilterText>
              )}
            </S.FilterStatusContent>

            <S.ResetFilterButton
              type="button"
              disabled={activeFilterEntries.length === 0}
              onClick={resetFilters}
            >
              <RotateCcw size={15} aria-hidden="true" />
              필터링 취소
            </S.ResetFilterButton>
          </S.FilterStatus>
        </S.DiscoveryControls>

        <S.SearchLayout>
          <S.FilterPanel
            id="roommate-filter-panel"
            $open={isFilterOpen}
            aria-label="프로필 필터"
          >
            <S.FilterPanelHeader>
              <div>
                <S.FilterTitle>
                  <SlidersHorizontal size={18} aria-hidden="true" />
                  필터
                </S.FilterTitle>
                <S.FilterDescription>
                  원하는 생활 조건을 선택해 보세요.
                </S.FilterDescription>
              </div>

              <S.ActiveFilterCount $active={activeFilterEntries.length > 0}>
                {activeFilterEntries.length}
              </S.ActiveFilterCount>
            </S.FilterPanelHeader>

            <S.FilterGroups>
              {FILTER_GROUPS.map((group) => (
                <S.FilterFieldset key={group.id}>
                  <S.FilterLegend>{group.label}</S.FilterLegend>
                  <S.FilterChoices>
                    {group.options.map((option) => {
                      const selected = filters[group.id] === option;

                      return (
                        <S.FilterChoice key={option} $selected={selected}>
                          <S.HiddenInput
                            type="radio"
                            name={group.id}
                            value={option}
                            checked={selected}
                            onChange={() => updateFilter(group.id, option)}
                          />
                          <S.ChoiceIndicator $selected={selected}>
                            {selected && <Check size={12} aria-hidden="true" />}
                          </S.ChoiceIndicator>
                          <span>{option}</span>
                        </S.FilterChoice>
                      );
                    })}
                  </S.FilterChoices>
                </S.FilterFieldset>
              ))}
            </S.FilterGroups>
          </S.FilterPanel>

          <S.ProfileResults
            id="roommate-profile-panel"
            role="tabpanel"
            tabIndex={0}
            aria-labelledby={
              activeTab === "recommended"
                ? "recommended-profile-tab"
                : "all-profile-tab"
            }
          >
            <S.ResultsHeader>
              <div>
                <S.ResultsTitle>
                  {activeTab === "recommended"
                    ? "추천 룸메이트"
                    : "전체 룸메이트"}
                </S.ResultsTitle>
                <S.ResultsDescription>
                  {activeTab === "recommended"
                    ? "내 선호 조건과 매칭점수가 높은 순서로 살펴보세요."
                    : "등록된 프로필을 한눈에 살펴보세요."}
                </S.ResultsDescription>
              </div>

              <S.ResultCount aria-live="polite" aria-atomic="true">
                <strong>{visibleProfiles.length}</strong>명
              </S.ResultCount>
            </S.ResultsHeader>

            {visibleProfiles.length > 0 ? (
              <S.ProfileGrid>
                {visibleProfiles.map((profile) => (
                  <S.ProfileCard key={profile.id}>
                    <S.CardHeader>
                      <S.ProfilePhoto>
                        <img
                          src={profile.photo}
                          alt={`${profile.nickname} 프로필 사진`}
                        />
                      </S.ProfilePhoto>

                      <S.ProfileIdentity>
                        <S.ProfileNameRow>
                          <S.ProfileName>{profile.nickname}</S.ProfileName>
                          <S.VerifiedBadge title="재학생 인증 완료">
                            <BadgeCheck size={14} aria-hidden="true" />
                            인증
                          </S.VerifiedBadge>
                        </S.ProfileNameRow>

                        <S.ProfileAcademic>
                          <S.AdmissionYear>{profile.admissionYear}학번</S.AdmissionYear>
                          <span aria-hidden="true">·</span>
                          <span>{profile.major}</span>
                        </S.ProfileAcademic>

                        <S.Dormitory>
                          <MapPin size={14} aria-hidden="true" />
                          {profile.dormitory} 생활관
                        </S.Dormitory>
                      </S.ProfileIdentity>

                      <S.MatchBadge aria-label={`매칭점수 ${profile.matchScore}점`}>
                        <Sparkles size={13} aria-hidden="true" />
                        {profile.matchScore}점
                      </S.MatchBadge>
                    </S.CardHeader>

                    <S.ProfileMessage>
                      <span>한마디</span>
                      <p>{profile.message}</p>
                    </S.ProfileMessage>

                    <S.TagList aria-label={`${profile.nickname} 해시태그`}>
                      {profile.tags.map((tag) => (
                        <S.ProfileTag key={tag}>#{tag}</S.ProfileTag>
                      ))}
                    </S.TagList>
                  </S.ProfileCard>
                ))}
              </S.ProfileGrid>
            ) : (
              <S.EmptyState>
                <S.EmptyIcon>
                  <UserRoundSearch size={30} aria-hidden="true" />
                </S.EmptyIcon>
                <h3>조건에 맞는 프로필이 없어요</h3>
                <p>필터를 조금 넓히면 더 많은 룸메이트를 만날 수 있어요.</p>
                <S.EmptyResetButton type="button" onClick={resetFilters}>
                  <RotateCcw size={16} aria-hidden="true" />
                  필터 모두 해제
                </S.EmptyResetButton>
              </S.EmptyState>
            )}
          </S.ProfileResults>
        </S.SearchLayout>
      </S.Content>
    </S.Page>
  );
}

export default RoommateSearchPage;
