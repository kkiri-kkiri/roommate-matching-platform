import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ArrowRight, CalendarDays, ChevronLeft, ChevronRight, CircleAlert,
  ClipboardList, Database, Flag, LayoutDashboard, MessageSquare,
  RotateCcw, Search, ShieldCheck, Users, X,
} from "lucide-react";
import ProfileImage from "../../components/common/ProfileImage";
import * as api from "../../api/adminApi";
import useAdminData, { label } from "./useAdminData";
import * as S from "./AdminPageCss";

const DORMS = ["나래관", "호연관", "창조관", "비봉관"];
const BOARDS = ["자유게시판", "나눔게시판", "택배게시판", "민원게시판"];
const MEMBER_STATUSES = ["정상", "이용 정지", "탈퇴"];
const REPORT_STATUSES = ["처리 대기", "처리 완료", "반려"];
const REPORT_TYPES = ["사용자", "게시글", "댓글"];
const SCHEDULE_TYPES = ["학교 일정", "기숙사 일정"];
const MENUS = [
  { id: "dashboard", title: "대시보드", icon: LayoutDashboard, description: "우리 생활관의 오늘, 주요 운영 현황을 한눈에 확인하세요." },
  { id: "members", title: "회원 관리", icon: Users, description: "회원 정보와 활동 내역을 확인하고 이용 상태를 관리하세요." },
  { id: "community", title: "커뮤니티 관리", icon: MessageSquare, description: "게시글과 댓글을 살펴보고 건강한 커뮤니티를 만들어 주세요." },
  { id: "reports", title: "신고 관리", icon: Flag, description: "접수된 신고 내용을 확인하고 필요한 조치를 진행하세요." },
  { id: "schedules", title: "일정 수집 현황", icon: CalendarDays, description: "자동으로 수집된 학교·기숙사 일정과 수집 상태를 확인하세요." },
];
const url = (view, filters = {}) => `/admin?${new URLSearchParams({ view, ...filters })}`;
function Badge({ children }) {
  children = label(children);
  const tone = ["정상", "처리 완료", "수집 완료"].includes(children) ? "success"
    : ["이용 정지", "신고됨", "수집 실패"].includes(children) ? "danger"
      : children === "처리 대기" ? "warning" : children === "수집 중" ? "info" : undefined;
  return <S.Badge $tone={tone}>{children}</S.Badge>;
}

function DetailFields({ items }) {
  return <S.Details>{items.map(([label, value, wide]) => <S.DetailItem key={label} $wide={wide}><dt>{label}</dt><dd>{value ?? "-"}</dd></S.DetailItem>)}</S.Details>;
}

function FilterSelect({ label, value, options, onChange }) {
  return <S.Field>{label}<S.Select value={value} onChange={(event) => onChange(event.target.value)}>
    <option value="">전체</option>{options.map((option) => <option key={option} value={option}>{option}</option>)}
  </S.Select></S.Field>;
}

function DataTable({ title, columns, rows, pagination, onPage, onReset }) {
  const pages = Math.max(1, pagination.totalPages);
  const currentPage = pagination.page + 1;
  const startPage = Math.max(1, Math.min(currentPage - 2, pages - 4));
  const pageNumbers = Array.from({ length: Math.min(5, pages) }, (_, index) => startPage + index);
  return <S.Panel>
    <S.PanelHeading><h2>{title}</h2><p aria-live="polite">총 {pagination.totalElements.toLocaleString()}건</p></S.PanelHeading>
    {rows.length ? <S.TableScroll tabIndex={0} role="region" aria-label={`${title} 테이블, 좌우 스크롤 가능`}>
      <S.Table aria-label={title}><thead><tr>{columns.map(([heading]) => <th scope="col" key={heading}>{heading}</th>)}</tr></thead>
        <tbody>{rows.map((row) => <tr key={row.id}>{columns.map(([heading, render]) => <td key={heading}>{render(row)}</td>)}</tr>)}</tbody>
      </S.Table>
    </S.TableScroll> : <S.Empty><Search size={28} aria-hidden="true" /><h3>표시할 내역이 없습니다</h3><p>검색어나 필터를 변경해 다시 확인해 주세요.</p><S.Button type="button" onClick={onReset}>검색·필터 초기화</S.Button></S.Empty>}
    <S.Pagination aria-label={`${title} 페이지 선택`}>
      <S.Button type="button" aria-label="이전 페이지" disabled={pagination.first || currentPage <= 1} onClick={() => onPage(currentPage - 1)}><ChevronLeft size={16} /></S.Button>
      {pageNumbers.map((number) => <S.Button type="button" key={number} aria-label={`${number}페이지`} aria-current={currentPage === number ? "page" : undefined} $variant={currentPage === number ? "primary" : undefined} onClick={() => onPage(number)}>{number}</S.Button>)}
      <S.Button type="button" aria-label="다음 페이지" disabled={pagination.last || currentPage >= pages} onClick={() => onPage(currentPage + 1)}><ChevronRight size={16} /></S.Button>
    </S.Pagination>
  </S.Panel>;
}

function RecentPanel({ title, to, rows, render }) {
  return <S.Panel><S.PanelHeading><h2>{title}</h2><S.TextLink to={to}>전체 보기 <ArrowRight size={14} aria-hidden="true" /></S.TextLink></S.PanelHeading>
    {rows.length ? <S.RecentList>{rows.map((row) => <li key={row.id}>{render(row)}</li>)}</S.RecentList> : <S.Empty>최근 내역이 없습니다.</S.Empty>}
  </S.Panel>;
}

export default function AdminPage() {
  const [params, setParams] = useSearchParams();
  const menu = MENUS.find((item) => item.id === params.get("view")) || MENUS[0];
  const view = menu.id;
  const isComments = view === "community" && params.get("tab") === "comments";
  const query = params.get("q") || "";
  const status = params.get("status") || "";
  const category = params.get("category") || "";
  const dorm = params.get("dorm") || "";
  const type = params.get("type") || "";
  const reportedOnly = params.get("reported") === "true";
  const sort = params.get("sort") || "최신순";
  const rawPage = Number(params.get("page"));
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1;
  const [draft, setDraft] = useState(query);
  const [modal, setModal] = useState(null);
  const [confirmation, setConfirmation] = useState(null);
  const [notice, setNotice] = useState("");
  const dialogRef = useRef(null);
  const headingRef = useRef(null);
  const returnFocusRef = useRef(null);
  const [revision, setRevision] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState("");
  const actionLock = useRef(false);
  const { main, crawl, detail } = useAdminData({ view, isComments, query, status, category, dorm, type, reportedOnly, sort, page, revision, modal });
  const dashboard = view === "dashboard" ? main.data : null;
  const stats = dashboard?.stats;
  const pendingCount = stats?.pendingReports || 0;
  const nickname = (person) => person?.nickname || person?.loginId || "알 수 없는 회원";
  const reportBadge = (item) => <Badge>{item.reported ? "신고됨" : "없음"}</Badge>;

  useEffect(() => { setDraft(query); }, [query, view, isComments]);
  useEffect(() => { setModal(null); setConfirmation(null); setNotice(""); setActionError(""); }, [view, isComments]);
  useEffect(() => {
    if (view !== "dashboard" && main.data && page > Math.max(1, main.data.totalPages)) {
      const next = new URLSearchParams(params);
      next.set("page", String(Math.max(1, main.data.totalPages)));
      setParams(next, { replace: true });
    }
  }, [view, main.data, page, params, setParams]);
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!modal) {
      // Restore focus after the dialog and any deleted table row leave the DOM.
      const target = returnFocusRef.current;
      if (target) {
        if (target.isConnected) target.focus(); else headingRef.current?.focus();
        returnFocusRef.current = null;
      }
      return undefined;
    }
    if (!dialog) return undefined;
    if (!dialog.open) dialog.showModal();
    return () => { dialog.close(); };
  }, [modal]);
  useEffect(() => {
    if (modal) dialogRef.current?.querySelector("button")?.focus();
  }, [modal, confirmation]);

  function changeFilter(key, value) {
    const next = new URLSearchParams(params);
    next.set("view", view);
    if (value) next.set(key, value); else next.delete(key);
    next.delete("page");
    setParams(next);
  }
  function resetFilters() {
    setDraft("");
    setParams({ view, ...(isComments ? { tab: "comments" } : {}) });
  }
  function changePage(number) {
    const next = new URLSearchParams(params);
    next.set("page", String(number));
    setParams(next);
  }
  function openDetail(kind, id) {
    if (!modal) returnFocusRef.current = document.activeElement;
    setConfirmation(null);
    setActionError("");
    setModal({ kind, id });
  }
  function closeDetail() {
    if (actionLock.current) return;
    setActionError("");
    setConfirmation(null);
    setModal(null);
  }
  function requestAction(action, id, label, description, danger = false) {
    setActionError("");
    setConfirmation({ action, id, label, description, danger });
  }
  async function applyAction() {
    if (!confirmation || actionLock.current) return;
    const { action, id, label: actionLabel } = confirmation;
    actionLock.current = true;
    setSubmitting(true);
    setActionError("");
    try {
      if (["suspend", "restore", "withdraw"].includes(action)) {
        await api.updateAdminMemberStatus(id, { suspend: "SUSPENDED", restore: "ACTIVE", withdraw: "WITHDRAWN" }[action]);
      } else if (action === "deletePost") await api.deleteAdminPost(id);
      else if (action === "deleteComment") await api.deleteAdminComment(id);
      else await api.processAdminReport(id, { reportComplete: "COMPLETE", reportReject: "REJECT", reportSuspend: "SUSPEND_USER", reportDelete: "DELETE_TARGET" }[action]);
      setConfirmation(null);
      setNotice(actionLabel + " 처리가 완료되었습니다.");
      if (action === "deletePost" || action === "deleteComment" || action.startsWith("report")) setModal(null);
      setRevision((value) => value + 1);
    } catch (error) {
      setActionError(error.message);
    } finally {
      actionLock.current = false;
      setSubmitting(false);
    }
  }

  const rows = main.data?.content || [];
  let columns = [];
  let listTitle = "";
  const detailButton = (kind, item, text = "상세 보기") => <S.TextButton type="button" onClick={() => openDetail(kind, item.id)}>{text}</S.TextButton>;
  if (view === "members") {
    listTitle = "회원 목록";
    columns = [["회원 번호", (item) => item.id], ["프로필", (item) => <ProfileImage as={S.Avatar} major={item.major} alt={`${item.nickname} 프로필`} />],
      ["아이디", (item) => detailButton("member", item, item.loginId)], ["닉네임", (item) => item.nickname], ["이메일", (item) => item.schoolEmail],
      ["생활관", (item) => item.dormitory], ["가입일", (item) => item.createdAt], ["회원 상태", (item) => <Badge>{item.status}</Badge>]];
  } else if (view === "community" && !isComments) {
    listTitle = "게시글 목록";
    columns = [["번호", (item) => item.id], ["게시판", (item) => item.category], ["게시글 제목", (item) => <S.CellText>{detailButton("post", item, item.title)}</S.CellText>],
      ["작성자", (item) => nickname(item.author)], ["작성일", (item) => item.createdAt], ["조회", (item) => item.views], ["좋아요", (item) => item.likes],
      ["댓글", (item) => item.commentCount], ["신고 여부", (item) => reportBadge(item)], ["관리", (item) => detailButton("post", item)]];
  } else if (isComments) {
    listTitle = "댓글 목록";
    columns = [["댓글 내용", (item) => <S.CellText>{detailButton("comment", item, item.content)}</S.CellText>], ["작성자", (item) => nickname(item.author)],
      ["작성일", (item) => item.createdAt], ["작성된 게시글", (item) => <S.CellText>{detailButton("post", { id: item.postId }, item.postTitle || "삭제된 게시글")}</S.CellText>],
      ["신고 여부", (item) => reportBadge(item)], ["관리", (item) => detailButton("comment", item)]];
  } else if (view === "reports") {
    listTitle = "신고 목록";
    columns = [["신고 번호", (item) => detailButton("report", item, `#${item.id}`)], ["대상 유형", (item) => <Badge>{item.type}</Badge>],
      ["대상 내용", (item) => <S.CellText>{detailButton("report", item, item.targetContent)}</S.CellText>], ["신고자", (item) => nickname(item.reporter)],
      ["대상 사용자", (item) => nickname(item.targetUser)], ["신고 사유", (item) => item.reason], ["접수일", (item) => item.createdAt], ["처리 상태", (item) => <Badge>{item.status}</Badge>]];
  } else if (view === "schedules") {
    listTitle = "수집 일정 목록";
    columns = [["일정 제목", (item) => <S.CellText>{detailButton("schedule", item, item.title)}</S.CellText>], ["일정 날짜", (item) => item.date],
      ["일정 내용", (item) => <S.CellText>{item.content}</S.CellText>], ["일정 분류", (item) => <Badge>{item.category}</Badge>], ["일정 출처", (item) => item.source], ["수집 일시", (item) => item.collectedAt]];
  }

  const selected = detail.data;
  const modalTitle = confirmation ? `${confirmation.label} 확인` : { member: "회원 상세", post: "게시글 상세", comment: "댓글 상세", report: "신고 상세", schedule: "수집 일정 상세" }[modal?.kind];
  const activeFilters = [query && `검색: ${query}`, status && `상태: ${status}`, dorm && `생활관: ${dorm}`, category && `분류: ${category}`, type && `대상: ${type}`, reportedOnly && "신고된 항목만", view === "community" && !isComments && sort !== "최신순" && `정렬: ${sort}`].filter(Boolean);

  return <S.Page>
    <S.Layout>
      <S.Sidebar>
        <S.Brand><ShieldCheck size={28} aria-hidden="true" /><div><strong>관리자 공간</strong><span>ROOMIE ADMIN</span></div></S.Brand>
        <S.Navigation aria-label="관리자 메뉴">{MENUS.map(({ id, title, icon: Icon }) => <S.NavLink key={id} to={url(id)} $active={view === id} aria-current={view === id ? "page" : undefined}><Icon size={18} aria-hidden="true" />{title}</S.NavLink>)}</S.Navigation>
        <S.SideNote>더 편안하고 안전한<br />생활관 생활을 함께 만듭니다.</S.SideNote>
      </S.Sidebar>
      <S.Content aria-labelledby="admin-title">
        <S.Heading><div><S.Eyebrow>ADMINISTRATION</S.Eyebrow><h1 id="admin-title" ref={headingRef} tabIndex={-1}>{menu.title}</h1><p>{menu.description}</p></div><S.Badge $tone="info"><ShieldCheck size={14} aria-hidden="true" />관리자</S.Badge></S.Heading>
        {notice && <S.Notice role="status"><ShieldCheck size={18} aria-hidden="true" /><p>{notice}</p><S.TextButton type="button" aria-label="처리 알림 닫기" onClick={() => setNotice("")}><X size={18} /></S.TextButton></S.Notice>}
        {view === "dashboard" ? main.loading ? <S.Empty role="status">대시보드를 불러오는 중입니다.</S.Empty> : main.error ? <S.Notice role="alert">{main.error}<S.Button onClick={() => setRevision((value) => value + 1)}>다시 시도</S.Button></S.Notice> : dashboard && <>
          <S.Notice><CircleAlert size={20} aria-hidden="true" /><p>{pendingCount ? <>확인이 필요한 신고가 <strong>{pendingCount}건</strong> 있어요.</> : "처리 대기 중인 신고가 없습니다."}</p><S.TextLink to={url("reports", { status: "처리 대기" })}>신고 확인하기 <ArrowRight size={16} aria-hidden="true" /></S.TextLink></S.Notice>
          <S.Stats>{[
            ["전체 회원 수", stats.totalMembers, Users, "members", {}], ["전체 게시글 수", stats.totalPosts, ClipboardList, "community", {}],
            ["전체 댓글 수", stats.totalComments, MessageSquare, "community", { tab: "comments" }], ["전체 신고 수", stats.totalReports, Flag, "reports", {}],
            ["처리 대기 신고 수", pendingCount, CircleAlert, "reports", { status: "처리 대기" }],
            ["수집된 학교 일정 수", stats.schoolSchedules, CalendarDays, "schedules", { category: "학교 일정" }],
            ["수집된 기숙사 일정 수", stats.dormitorySchedules, Database, "schedules", { category: "기숙사 일정" }],
          ].map(([label, count, Icon, target, filters]) => <S.StatLink key={label} to={url(target, filters)}><Icon size={20} aria-hidden="true" /><span>{label}</span><strong>{count.toLocaleString()}</strong></S.StatLink>)}</S.Stats>
          <S.RecentGrid>
            <RecentPanel title="최근 가입 회원" to={url("members")} rows={dashboard.recentMembers} render={(item) => <S.Row><ProfileImage as={S.Avatar} major={item.major} alt={`${item.nickname} 프로필`} /><div>{detailButton("member", item, item.nickname)}<S.Meta>{item.dormitory} · {item.createdAt}</S.Meta></div><Badge>{item.status}</Badge></S.Row>} />
            <RecentPanel title="최근 작성된 게시글" to={url("community")} rows={dashboard.recentPosts} render={(item) => <>{detailButton("post", item, item.title)}<S.Meta>{item.category} · {nickname(item.author)} · {item.createdAt}</S.Meta></>} />
            <RecentPanel title="최근 접수된 신고" to={url("reports")} rows={dashboard.recentReports} render={(item) => <><S.Row><Badge>{item.status}</Badge>{detailButton("report", item, `${label(item.type)} · ${item.reason}`)}</S.Row><S.Meta>#{item.id} · {item.createdAt}</S.Meta></>} />
            {SCHEDULE_TYPES.map((scheduleType) => <RecentPanel key={scheduleType} title={`최근 수집된 ${scheduleType}`} to={url("schedules", { category: scheduleType })} rows={scheduleType === "학교 일정" ? dashboard.recentSchoolSchedules : dashboard.recentDormitorySchedules} render={(item) => <>{detailButton("schedule", item, item.title)}<S.Meta>일정 {item.date} · 수집 {item.collectedAt}</S.Meta></>} />)}
          </S.RecentGrid>
        </> : <>
          {view === "community" && <S.Tabs aria-label="커뮤니티 관리 항목"><S.TabLink to={url("community")} $active={!isComments} aria-current={!isComments ? "page" : undefined}>게시글 관리</S.TabLink><S.TabLink to={url("community", { tab: "comments" })} $active={isComments} aria-current={isComments ? "page" : undefined}>댓글 관리</S.TabLink></S.Tabs>}
          {view === "schedules" && <>
            <S.Notice><Database size={20} aria-hidden="true" /><p>학교·기숙사 일정은 외부 사이트에서 자동 수집됩니다. 일정 상세에서 수집 내용과 출처를 확인할 수 있습니다.</p></S.Notice>
            {crawl.loading ? <S.Empty role="status">크롤링 상태를 불러오는 중입니다.</S.Empty> : crawl.error ? <S.Notice role="alert">{crawl.error}<S.Button onClick={() => setRevision((value) => value + 1)}>다시 시도</S.Button></S.Notice> :
              <S.CrawlGrid>{(crawl.data || []).map((item) => <S.Panel key={item.category}>
                <S.PanelHeading><h2>{label(item.category)}</h2><Badge>{{ RUNNING: "수집 중", COMPLETED: "수집 완료", FAILED: "수집 실패" }[item.status] || "실행 이력 없음"}</Badge></S.PanelHeading>
                <DetailFields items={[["최근 크롤링 실행", item.lastRunAt], ["최근 수집된 일정 수", (item.recentCollectedCount ?? 0) + "건"], ["전체 수집된 일정 수", (item.totalCollectedCount ?? 0) + "건"], ["최근 수집 완료", item.lastCompletedAt]]} />
                <S.CrawlHistory>{item.lastFailure ? <S.Row><Badge>수집 실패</Badge><span>{item.lastFailure.occurredAt || "-"} · {item.lastFailure.message || "-"}</span></S.Row> : item.status === "RUNNING" ? "진행 중인 수집이 끝나면 새 일정이 반영됩니다." : "실패 이력이 없습니다."}</S.CrawlHistory>
              </S.Panel>)}</S.CrawlGrid>}
          </>}
          <S.Toolbar onSubmit={(event) => { event.preventDefault(); changeFilter("q", draft.trim()); }}>
            <S.Field $grow>검색<S.Input type="search" value={draft} onChange={(event) => setDraft(event.target.value)} placeholder={view === "members" ? "아이디, 닉네임, 이메일" : view === "reports" ? "신고 번호, 내용, 사용자, 사유" : view === "schedules" ? "일정 제목, 내용, 출처" : isComments ? "댓글 내용, 작성자" : "게시글 제목, 내용, 작성자"} /></S.Field>
            {view === "members" && <><FilterSelect label="회원 상태" value={status} options={MEMBER_STATUSES} onChange={(value) => changeFilter("status", value)} /><FilterSelect label="생활관" value={dorm} options={DORMS} onChange={(value) => changeFilter("dorm", value)} /></>}
            {view === "community" && <>
              {!isComments && <><FilterSelect label="게시판 분류" value={category} options={BOARDS} onChange={(value) => changeFilter("category", value)} /><S.Field>정렬<S.Select value={sort} onChange={(event) => changeFilter("sort", event.target.value)}>{["최신순", "조회순", "좋아요순"].map((value) => <option key={value}>{value}</option>)}</S.Select></S.Field></>}
              <S.Checkbox><input type="checkbox" checked={reportedOnly} onChange={(event) => changeFilter("reported", event.target.checked ? "true" : "")} />신고된 {isComments ? "댓글" : "게시글"}만</S.Checkbox>
            </>}
            {view === "reports" && <><FilterSelect label="대상 유형" value={type} options={REPORT_TYPES} onChange={(value) => changeFilter("type", value)} /><FilterSelect label="처리 상태" value={status} options={REPORT_STATUSES} onChange={(value) => changeFilter("status", value)} /></>}
            {view === "schedules" && <FilterSelect label="일정 분류" value={category} options={SCHEDULE_TYPES} onChange={(value) => changeFilter("category", value)} />}
            <S.Button type="submit" $variant="primary"><Search size={16} aria-hidden="true" />검색</S.Button>
            <S.Button type="button" onClick={resetFilters}><RotateCcw size={16} aria-hidden="true" />초기화</S.Button>
          </S.Toolbar>
          {activeFilters.length > 0 && <S.Chips aria-label="선택된 검색 조건">{activeFilters.map((filter) => <S.Badge key={filter} $tone="info">{filter}</S.Badge>)}<S.TextButton type="button" onClick={resetFilters}>전체 초기화</S.TextButton></S.Chips>}
          {main.loading ? <S.Empty role="status">목록을 불러오는 중입니다.</S.Empty> : main.error ? <S.Notice role="alert">{main.error}<S.Button onClick={() => setRevision((value) => value + 1)}>다시 시도</S.Button></S.Notice> : main.data && <DataTable title={listTitle} columns={columns} rows={rows} pagination={main.data} onPage={changePage} onReset={resetFilters} />}
        </>}
      </S.Content>
    </S.Layout>
    {modal && <S.Dialog ref={dialogRef} aria-labelledby="admin-dialog-title" onCancel={(event) => { event.preventDefault(); if (actionLock.current) return; if (confirmation) setConfirmation(null); else closeDetail(); }} onClick={(event) => { if (actionLock.current) return; if (event.target === event.currentTarget) { const rect = event.currentTarget.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) { if (confirmation) setConfirmation(null); else closeDetail(); } } }}>
      <S.DialogHeading><h2 id="admin-dialog-title">{modalTitle}</h2><S.Button type="button" disabled={submitting} aria-label={confirmation ? "확인 취소" : "상세 닫기"} onClick={() => confirmation ? setConfirmation(null) : closeDetail()}><X size={18} /></S.Button></S.DialogHeading>
      {actionError && <S.Notice role="alert">{actionError}</S.Notice>}
      {confirmation ? <><S.DialogBody><h3>{confirmation.label} 처리하시겠습니까?</h3><p>{confirmation.description}</p></S.DialogBody><S.Actions><S.Button type="button" disabled={submitting} onClick={() => setConfirmation(null)}>취소</S.Button><S.Button type="button" $variant={confirmation.danger ? "danger" : "primary"} disabled={submitting} onClick={applyAction}>{submitting ? "처리 중…" : confirmation.label}</S.Button></S.Actions></>
        : detail.loading ? <S.Empty role="status">상세 정보를 불러오는 중입니다.</S.Empty> : detail.error ? <S.Notice role="alert">{detail.error}<S.Button onClick={() => setRevision((value) => value + 1)}>다시 시도</S.Button></S.Notice> : selected ? <>
          {modal.kind === "member" && <>
            <S.DialogBody><S.Row><ProfileImage as={S.Avatar} $large major={selected.major} alt={`${selected.nickname} 프로필`} /><div><h3>{selected.nickname}</h3><Badge>{selected.status}</Badge></div></S.Row></S.DialogBody>
            <DetailFields items={[["회원 번호", selected.id], ["아이디", selected.loginId], ["닉네임", selected.nickname], ["이메일", selected.schoolEmail], ["성별", selected.gender], ["생활관", selected.dormitory], ["학부", selected.faculty], ["전공", selected.major], ["입학 연도", selected.admissionYear], ["학년", selected.grade], ["MBTI", selected.mbti], ["가입일", selected.createdAt]]} />
            <S.Activity>{[["작성 게시글", selected.activity?.postCount ?? 0], ["작성 댓글", selected.activity?.commentCount ?? 0], ["신고 받은 횟수", selected.activity?.reportsReceived ?? 0]].map(([label, count]) => <div key={label}><strong>{count}</strong><span>{label}</span></div>)}</S.Activity>
            <S.Actions>
              <S.Button type="button" $variant="danger" onClick={() => requestAction("withdraw", selected.id, "회원 탈퇴", `${selected.nickname} 회원을 탈퇴 상태로 변경합니다.`, true)}>회원 탈퇴 처리</S.Button>
              <S.Button type="button" $variant="primary" onClick={() => requestAction(["SUSPENDED", "WITHDRAWN"].includes(selected.status) ? "restore" : "suspend", selected.id, ["SUSPENDED", "WITHDRAWN"].includes(selected.status) ? "이용 정지 해제" : "이용 정지", `${selected.nickname} 회원의 이용 상태를 변경합니다.`)}>{["SUSPENDED", "WITHDRAWN"].includes(selected.status) ? "이용 정지 해제" : "이용 정지"}</S.Button>
            </S.Actions>
          </>}
          {modal.kind === "post" && <>
            <S.DialogBody><S.Chips><Badge>{selected.category}</Badge>{reportBadge(selected)}</S.Chips><h3>{selected.title}</h3><S.Meta>{nickname(selected.author)} · {selected.createdAt}</S.Meta></S.DialogBody>
            <S.DialogBody><p>{selected.content}</p></S.DialogBody>
            <DetailFields items={[["조회 수", selected.views], ["좋아요 수", selected.likes], ["댓글 수", selected.commentCount]]} />
            <S.Actions><S.Button type="button" onClick={closeDetail}>닫기</S.Button><S.Button type="button" $variant="danger" onClick={() => requestAction("deletePost", selected.id, "게시글 삭제", `‘${selected.title}’ 게시글과 해당 게시글의 댓글이 목록에서 삭제됩니다.`, true)}>게시글 삭제</S.Button></S.Actions>
          </>}
          {modal.kind === "comment" && <>
            <DetailFields items={[["작성자", nickname(selected.author)], ["작성일", selected.createdAt], ["신고 여부", reportBadge(selected)], ["작성된 게시글", detailButton("post", { id: selected.postId }, selected.postTitle || "삭제된 게시글"), true], ["댓글 내용", selected.content, true]]} />
            <S.Actions><S.Button type="button" onClick={closeDetail}>닫기</S.Button><S.Button type="button" $variant="danger" onClick={() => requestAction("deleteComment", selected.id, "댓글 삭제", "이 댓글을 목록에서 삭제합니다. 삭제한 댓글은 복원할 수 없습니다.", true)}>댓글 삭제</S.Button></S.Actions>
          </>}
          {modal.kind === "report" && <>
            <DetailFields items={[["신고 번호", `#${selected.id}`], ["현재 처리 상태", <Badge>{selected.status}</Badge>], ["신고 대상 유형", label(selected.type)], ["신고 접수일", selected.createdAt],
              ["신고자 정보", `${nickname(selected.reporter)} (${selected.reporter?.loginId || "-"})\n${selected.reporter?.schoolEmail || "-"}`],
              ["신고 대상 사용자 정보", <>{nickname(selected.targetUser)} ({selected.targetUser?.loginId || "-"})<S.Meta>{selected.targetUser?.schoolEmail || "-"}</S.Meta><Badge>{selected.targetUser?.status}</Badge></>],
              ["신고 대상 내용", selected.targetContent, true], ["신고 사유", selected.reason], ["상세 사유", selected.description, true]]} />
            {selected.type !== "USER" && <S.DialogBody>{detailButton(selected.type === "POST" ? "post" : "comment", { id: selected.targetId }, "신고 대상 상세 확인")}</S.DialogBody>}
            <S.Actions>
              <S.Button type="button" $variant="danger" onClick={() => requestAction("reportSuspend", selected.id, "회원 이용 정지", "신고 대상 회원을 이용 정지하고 이 신고를 처리 완료로 변경합니다.", true)}>회원 이용 정지</S.Button>
              {selected.type !== "USER" && <S.Button type="button" $variant="danger" onClick={() => requestAction("reportDelete", selected.id, `${label(selected.type)} 삭제`, `신고된 ${label(selected.type)}${selected.type === "POST" ? "과 해당 댓글" : ""}을 삭제하고 이 신고를 처리 완료로 변경합니다.`, true)}>{label(selected.type)} 삭제</S.Button>}
              <S.Button type="button" onClick={() => requestAction("reportReject", selected.id, "신고 반려", "신고를 반려 상태로 변경합니다.")}>신고 반려</S.Button>
              <S.Button type="button" $variant="primary" onClick={() => requestAction("reportComplete", selected.id, "처리 완료", "검토를 마치고 이 신고를 처리 완료로 변경합니다.")}>처리 완료</S.Button>
            </S.Actions>
          </>}
          {modal.kind === "schedule" && <><DetailFields items={[["일정 제목", selected.title, true], ["일정 날짜", selected.date], ["일정 분류", label(selected.category)], ["일정 내용", selected.content, true], ["일정 출처", selected.source, true], ["수집 일시", selected.collectedAt]]} /><S.Actions><S.Button type="button" onClick={closeDetail}>닫기</S.Button></S.Actions></>}
        </> : <S.Empty><h3>해당 내역을 찾을 수 없습니다</h3><p>삭제되었거나 더 이상 표시할 수 없는 내역입니다.</p><S.Button type="button" onClick={closeDetail}>닫기</S.Button></S.Empty>}
    </S.Dialog>}
  </S.Page>;
}
