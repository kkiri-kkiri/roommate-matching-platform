import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  Check,
  ChevronDown,
  Info,
  RotateCcw,
  SlidersHorizontal,
  UserRoundSearch,
  X,
} from "lucide-react";

import Pagination from "../../components/common/Pagination/Pagination";
import { getRoommates, getFilteredRecommendedRoommates, toRoommateProfile } from "../../api/roommateApi";
import ProfileCard from "../../components/common/ProfileCard/ProfileCard";
import * as S from "./RoommateSearchPageCss";
import { PROFILE_SECTIONS } from "../../data/profileSurvey";

const FILTER_GROUPS = PROFILE_SECTIONS.flatMap((section) => section.fields);

const DEFAULT_FILTERS = FILTER_GROUPS.reduce(
  (filters, group) => ({ ...filters, [group.id]: [] }),
  {},
);
const PAGE_SIZE = 9;

function RoommateSearchPage({ currentUser }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get("tab") === "recommended" ? "recommended" : "all";
  const userId = currentUser?.id;
  const requiresLogin = activeTab === "recommended" && !userId;
  const setActiveTab = (tab) => {
    setCurrentPage(1);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (tab === "recommended") next.set("tab", "recommended");
      else next.delete("tab");
      return next;
    }, { replace: true });
  };
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  const [draftFilters, setDraftFilters] = useState(DEFAULT_FILTERS);
  const [expandedCategories, setExpandedCategories] = useState({
    lifePattern: true,
    livingHabits: false,
    dormLife: false,
  });
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [results, setResults] = useState({ status: "loading", profiles: [], totalPages: 1, error: "" });
  useEffect(() => {
    if (requiresLogin) return;
    const controller = new AbortController();
    setResults({ status: "loading", profiles: [], totalPages: 1, error: "" });
    (async () => {
      try {
        const recommended = activeTab === "recommended";
        const page = recommended
          ? await getFilteredRecommendedRoommates(filters, controller.signal)
          : await getRoommates({ page: currentPage - 1, size: PAGE_SIZE, filters }, controller.signal);
        if (!Array.isArray(page?.content) || !Number.isInteger(page.totalPages)) {
          throw new Error("프로필 목록 응답을 확인할 수 없습니다.");
        }
        const profiles = page.content.map(toRoommateProfile);
        if (!controller.signal.aborted) setResults({ status: "success", profiles,
          totalPages: recommended ? 1 : Math.max(1, page.totalPages), error: "" });
      } catch (error) {
        if (!controller.signal.aborted) setResults({ status: "error", profiles: [], totalPages: 1, error: error.message });
      }
    })();
    return () => controller.abort();
  }, [activeTab, currentPage, filters, revision, requiresLogin, userId]);
  const tabRefs = useRef({});
  const filterDialogRef = useRef(null);

  useEffect(() => {
    if (!isFilterOpen) return;

    const dialog = filterDialogRef.current;
    const previousOverflow = document.body.style.overflow;
    dialog.showModal();
    document.body.style.overflow = "hidden";

    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
    };
  }, [isFilterOpen]);

  const activeFilterEntries = useMemo(
    () =>
      FILTER_GROUPS.flatMap((group) =>
        filters[group.id].map((value) => ({ id: group.id, label: group.label, value })),
      ),
    [filters],
  );

  const visibleProfiles = results.profiles;
  const totalPages = results.totalPages;

  const updateDraftFilter = (filterId, value) => {
    setDraftFilters((currentFilters) => ({
      ...currentFilters,
      [filterId]: currentFilters[filterId].includes(value)
        ? currentFilters[filterId].filter((selected) => selected !== value)
        : [...currentFilters[filterId], value],
    }));
  };

  const openFilters = () => {
    setDraftFilters(
      Object.fromEntries(Object.entries(filters).map(([id, values]) => [id, [...values]])),
    );
    setIsFilterOpen(true);
  };

  const applyFilters = () => {
    setCurrentPage(1);
    setFilters(draftFilters);
    setIsFilterOpen(false);
  };

  const resetFilters = () => {
    setCurrentPage(1);
    setFilters(DEFAULT_FILTERS);
  };

  const removeFilter = (filterId, value) => {
    setCurrentPage(1);
    setFilters((currentFilters) => ({
      ...currentFilters,
      [filterId]: currentFilters[filterId].filter((selected) => selected !== value),
    }));
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

  const handlePageChange = (nextPage) => {
    setCurrentPage(nextPage);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <S.Page>
      <S.PageReset />

      <S.Content>
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
                      매칭 점수 상위 9명
                    </S.Tooltip>
                  </S.TooltipHost>
                </S.TabButton>
              </S.TabList>
            </S.TabsArea>

          </S.TabsRow>

          <S.FilterStatus aria-live="polite">
            <S.FilterStatusContent>
              <S.FilterToggle
                type="button"
                aria-label="필터 열기"
                title="필터 열기"
                aria-haspopup="dialog"
                aria-expanded={isFilterOpen}
                aria-controls="roommate-filter-panel"
                onClick={openFilters}
              >
                <SlidersHorizontal size={18} aria-hidden="true" />
              </S.FilterToggle>

              {activeFilterEntries.length > 0 ? (
                <S.AppliedFilterList>
                  {activeFilterEntries.map((filter) => (
                    <S.AppliedFilter key={`${filter.id}-${filter.value}`}>
                      <span>
                        {filter.label} · {filter.value}
                      </span>
                      <button
                        type="button"
                        aria-label={`${filter.label} ${filter.value} 필터 취소`}
                        onClick={() => removeFilter(filter.id, filter.value)}
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
              aria-label="필터 초기화"
              title="필터 초기화"
              disabled={activeFilterEntries.length === 0}
              onClick={resetFilters}
            >
              <RotateCcw size={15} aria-hidden="true" />
            </S.ResetFilterButton>
          </S.FilterStatus>
        </S.DiscoveryControls>

        <S.SearchLayout>
          <S.FilterPanel
            ref={filterDialogRef}
            id="roommate-filter-panel"
            aria-labelledby="roommate-filter-title"
            onCancel={(event) => {
              event.preventDefault();
              setIsFilterOpen(false);
            }}
            onClick={(event) => {
              if (event.target !== event.currentTarget) return;
              const bounds = event.currentTarget.getBoundingClientRect();
              if (
                event.clientX < bounds.left ||
                event.clientX > bounds.right ||
                event.clientY < bounds.top ||
                event.clientY > bounds.bottom
              ) {
                setIsFilterOpen(false);
              }
            }}
          >
            <S.FilterPanelHeader>
              <div>
                <S.FilterTitle id="roommate-filter-title">
                  <SlidersHorizontal size={18} aria-hidden="true" />
                  필터
                </S.FilterTitle>
                <S.FilterDescription>
                  필요한 항목만 복수 선택하세요. 전체는 조건 미적용이에요.
                </S.FilterDescription>
              </div>

              <S.FilterToggle
                type="button"
                aria-label="필터 닫기"
                onClick={() => setIsFilterOpen(false)}
              >
                <X size={18} aria-hidden="true" />
              </S.FilterToggle>
            </S.FilterPanelHeader>

            <S.FilterGroups>
              {PROFILE_SECTIONS.map((section) => {
                const expanded = expandedCategories[section.id];

                return (
                  <S.FilterCategory key={section.id}>
                    <S.FilterCategoryHeading>
                      <S.FilterCategoryToggle
                        type="button"
                        $expanded={expanded}
                        aria-expanded={expanded}
                        aria-controls={`filter-category-${section.id}`}
                        onClick={() => setExpandedCategories((current) => ({
                          ...current,
                          [section.id]: !current[section.id],
                        }))}
                      >
                        <span>{section.title}</span>
                        <small>{section.fields.length}개 항목</small>
                        <ChevronDown size={18} aria-hidden="true" />
                      </S.FilterCategoryToggle>
                    </S.FilterCategoryHeading>
                    <div id={`filter-category-${section.id}`} hidden={!expanded}>
                      {section.fields.map((group) => (
                        <S.FilterFieldset key={group.id}>
                          <S.FilterLegend>{group.label}</S.FilterLegend>
                          <S.FilterChoices>
                            {group.options.map((option) => {
                              if (
                                ["lightingPreference", "phoneCall", "scentProductUse"].includes(group.id) &&
                                option.replace(/\s/g, "") === "상관없음"
                              ) {
                                return null;
                              }

                              const selected = draftFilters[group.id].includes(option);

                              return (
                                <S.FilterChoice key={option} $selected={selected}>
                                  <S.HiddenInput
                                    type="checkbox"
                                    name={group.id}
                                    value={option}
                                    checked={selected}
                                    onChange={() => updateDraftFilter(group.id, option)}
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
                    </div>
                  </S.FilterCategory>
                );
              })}
            </S.FilterGroups>
            <S.FilterModalFooter>
              <S.ResetFilterButton type="button" onClick={() => setDraftFilters(DEFAULT_FILTERS)}>
                <RotateCcw size={15} aria-hidden="true" />
                초기화
              </S.ResetFilterButton>
              <S.FilterToggle type="button" onClick={applyFilters}>
                적용
              </S.FilterToggle>
            </S.FilterModalFooter>
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
            {requiresLogin ? (
              <S.EmptyState role="status">
                <h3>로그인 후 추천 프로필을 확인할 수 있어요.</h3>
                <p>생활 습관과 선호 조건에 맞는 룸메이트를 추천해 드려요.</p>
                <S.EmptyResetButton as={Link} to="/login">로그인하기</S.EmptyResetButton>
              </S.EmptyState>
            ) : results.status !== "success" ? (
              <S.EmptyState role={results.status === "error" ? "alert" : "status"}>
                <h3>{results.status === "loading" ? "프로필을 불러오고 있어요" : "프로필을 불러오지 못했어요"}</h3>
                <p>{results.error || "잠시만 기다려 주세요."}</p>
                {results.status === "error" && (
                  <S.EmptyResetButton type="button" onClick={() => setRevision((value) => value + 1)}>
                    다시 시도
                  </S.EmptyResetButton>
                )}
              </S.EmptyState>
            ) : visibleProfiles.length > 0 ? (
              <>
                <S.ProfileGrid>
                  {visibleProfiles.map((profile) => (
                    <ProfileCard
                      key={profile.id}
                      profile={profile}
                      activeTab={activeTab}
                      showOverlay={activeTab === "recommended"}
                    />
                  ))}
                </S.ProfileGrid>

                <Pagination
                  currentPage={currentPage}
                  totalPages={totalPages}
                  onPageChange={handlePageChange}
                />
              </>
            ) : (
              <S.EmptyState>
                <S.EmptyIcon>
                  <UserRoundSearch size={30} aria-hidden="true" />
                </S.EmptyIcon>
                <h3>{activeTab === "recommended" ? "조건에 맞는 추천 프로필이 없어요" : "조건에 맞는 프로필이 없어요"}</h3>
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
