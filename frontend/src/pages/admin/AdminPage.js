import { useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  ArrowRight, CalendarDays, ChevronLeft, ChevronRight, CircleAlert,
  ClipboardList, Database, Flag, LayoutDashboard, MessageSquare,
  RotateCcw, Search, ShieldCheck, Users, X,
} from "lucide-react";
import ProfileImage from "../../components/common/ProfileImage";
import * as S from "./AdminPageCss";

const DORMS = ["나래관", "호연관", "창조관", "비봉관"];
const BOARDS = ["자유게시판", "나눔게시판", "택배게시판", "민원게시판"];
const MEMBER_STATUSES = ["정상", "이용 정지", "탈퇴"];
const REPORT_STATUSES = ["처리 대기", "처리 완료", "반려"];
const REPORT_TYPES = ["사용자", "게시글", "댓글"];
const SCHEDULE_TYPES = ["학교 일정", "기숙사 일정"];
const PAGE_SIZE = 6;
const MENUS = [
  { id: "dashboard", title: "대시보드", icon: LayoutDashboard, description: "우리 생활관의 오늘, 주요 운영 현황을 한눈에 확인하세요." },
  { id: "members", title: "회원 관리", icon: Users, description: "회원 정보와 활동 내역을 확인하고 이용 상태를 관리하세요." },
  { id: "community", title: "커뮤니티 관리", icon: MessageSquare, description: "게시글과 댓글을 살펴보고 건강한 커뮤니티를 만들어 주세요." },
  { id: "reports", title: "신고 관리", icon: Flag, description: "접수된 신고 내용을 확인하고 필요한 조치를 진행하세요." },
  { id: "schedules", title: "일정 수집 현황", icon: CalendarDays, description: "자동으로 수집된 학교·기숙사 일정과 수집 상태를 확인하세요." },
];
const url = (view, filters = {}) => `/admin?${new URLSearchParams({ view, ...filters })}`;
const date = (day) => `2026-09-${String(day).padStart(2, "0")}`;
const matches = (values, query) => values.some((value) => String(value).toLocaleLowerCase().includes(query.toLocaleLowerCase()));
const recent = (items, key = "createdAt") => [...items].sort((a, b) => b[key].localeCompare(a[key]) || b.id - a.id).slice(0, 3);

// All records and moderation changes are confined to this page's local state.
function createDemoData() {
  const names = ["포근한구름", "민트초코", "느긋한오후", "한걸음", "조용한숲", "따뜻한차", "새벽별", "정리왕", "나래지기", "작은파도", "햇살한줌", "초록나무", "달빛산책", "봄바람"];
  const members = names.map((nickname, index) => ({
    id: 1001 + index, loginId: `roomie${String(index + 1).padStart(2, "0")}`, nickname,
    email: `roomie${index + 1}@example.com`, dorm: DORMS[index % 4],
    createdAt: date(14 - index), status: index === 4 || index === 9 ? "이용 정지" : index === 12 ? "탈퇴" : "정상",
    gender: index % 2 ? "여성" : "남성", faculty: index % 2 ? "인문사회학부" : "공학부",
    major: index % 2 ? "경영학" : "컴퓨터공학", studentId: `202${index % 4 + 1}${String(index + 1).padStart(4, "0")}`,
    grade: `${index % 4 + 1}학년`, mbti: ["INFJ", "ENFP", "ISTJ", "INTP"][index % 4],
  }));
  const titles = [
    "룸메이트와 함께 정한 생활 규칙을 공유해요", "책상 스탠드 나눔합니다", "택배함에서 상자를 잘못 가져왔어요", "늦은 밤 복도 소음에 대한 의견",
    "아침 수업 전 간단한 식사 추천해 주세요", "사용하지 않은 연습장 나눔", "분실한 우산을 찾습니다", "세탁실 이용 시간을 지켜 주세요",
    "주말에 함께 도서관 가실 분", "수납 바구니가 필요한 분 계신가요?", "택배 운송장은 떼고 버려요", "공용 냉장고 정리를 부탁드려요",
    "기숙사에서 맞는 첫 가을", "깨끗한 종이 상자를 나눔해요", "택배 수령 장소 확인 부탁드립니다", "공용 공간을 함께 깨끗하게 사용해요",
  ];
  const posts = titles.map((title, index) => ({
    id: 2001 + index, category: BOARDS[index % 4], title,
    content: `${title}.\n\n생활관에서 함께 지내는 분들과 이야기를 나누고 싶어요. 서로의 생활을 배려하며 편안하게 지낼 수 있도록 의견을 남겨 주세요.`,
    authorId: members[index % members.length].id, createdAt: `${date(14 - Math.floor(index / 2))} ${String(18 - index % 8).padStart(2, "0")}:30`,
    views: 42 + index * 17, likes: 3 + index % 11,
  }));
  const comments = Array.from({ length: 20 }, (_, index) => ({
    id: 3001 + index, postId: posts[index % posts.length].id,
    authorId: members[(index + 3) % members.length].id,
    content: ["좋은 의견이네요. 함께 실천해 봐요!", "자세한 내용은 댓글로 알려 주세요.", "저도 같은 경험이 있어요. 서로 배려하면 좋겠습니다.", "확인했습니다. 알려 주셔서 감사합니다."][index % 4],
    createdAt: `${date(14 - Math.floor(index / 3))} 19:${String(index + 10).padStart(2, "0")}`,
  }));
  const reports = Array.from({ length: 12 }, (_, index) => {
    const type = REPORT_TYPES[index % 3];
    const target = type === "사용자" ? members[(index + 1) % members.length] : type === "게시글" ? posts[index] : comments[index];
    return {
      id: 4001 + index, type, targetId: target.id,
      targetUserId: type === "사용자" ? target.id : target.authorId,
      targetContent: target.title || target.content || target.nickname,
      reporterId: members[(index + 7) % members.length].id,
      reason: ["부적절한 프로필", "광고 및 도배", "욕설 및 비방", "불쾌감을 주는 행동"][index % 4],
      description: "해당 내용을 확인해 주세요. 다른 이용자에게 불편을 줄 수 있어 검토를 요청합니다.",
      createdAt: `${date(14 - Math.floor(index / 2))} 20:10`,
      status: index < 7 ? "처리 대기" : index < 10 ? "처리 완료" : "반려",
    };
  });
  const scheduleTitles = ["2학기 수강 정정", "생활관 정기 소방 점검", "수강 철회 신청", "생활관 공동생활 교육", "중간고사 기간 안내", "생활관 시설 점검", "전공 신청 안내", "생활관 만족도 조사", "학기 중 장학금 신청", "생활관 방역 안내", "학사 상담 주간", "생활관 정기 퇴사 안내", "겨울 계절학기 신청", "동계 생활관 신청"];
  const schedules = scheduleTitles.map((title, index) => ({
    id: 5001 + index, title, category: SCHEDULE_TYPES[index % 2], date: date(15 + index),
    content: `${title} 관련 안내입니다. 세부 대상과 준비 사항은 ${index % 2 ? "생활관" : "학교"} 공지사항을 확인해 주세요.`,
    source: index % 2 ? "생활관 홈페이지 · 공지사항" : "학교 홈페이지 · 학사일정",
    collectedAt: `${date(index < 4 ? 14 : 13)} 06:00`,
  }));
  return { members, posts, comments, reports, schedules };
}

function Badge({ children }) {
  const tone = ["정상", "처리 완료", "수집 완료"].includes(children) ? "success"
    : ["이용 정지", "신고됨", "수집 실패"].includes(children) ? "danger"
      : children === "처리 대기" ? "warning" : children === "수집 중" ? "info" : undefined;
  return <S.Badge $tone={tone}>{children}</S.Badge>;
}

function DetailFields({ items }) {
  return <S.Details>{items.map(([label, value, wide]) => <S.DetailItem key={label} $wide={wide}><dt>{label}</dt><dd>{value}</dd></S.DetailItem>)}</S.Details>;
}

function FilterSelect({ label, value, options, onChange }) {
  return <S.Field>{label}<S.Select value={value} onChange={(event) => onChange(event.target.value)}>
    <option value="">전체</option>{options.map((option) => <option key={option} value={option}>{option}</option>)}
  </S.Select></S.Field>;
}

function DataTable({ title, columns, rows, page, onPage, onReset }) {
  const pages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const currentPage = Math.min(page, pages);
  return <S.Panel>
    <S.PanelHeading><h2>{title}</h2><p aria-live="polite">총 {rows.length.toLocaleString()}건</p></S.PanelHeading>
    {rows.length ? <S.TableScroll tabIndex={0} role="region" aria-label={`${title} 테이블, 좌우 스크롤 가능`}>
      <S.Table aria-label={title}><thead><tr>{columns.map(([heading]) => <th scope="col" key={heading}>{heading}</th>)}</tr></thead>
        <tbody>{rows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map((row) => <tr key={row.id}>{columns.map(([heading, render]) => <td key={heading}>{render(row)}</td>)}</tr>)}</tbody>
      </S.Table>
    </S.TableScroll> : <S.Empty><Search size={28} aria-hidden="true" /><h3>표시할 내역이 없습니다</h3><p>검색어나 필터를 변경해 다시 확인해 주세요.</p><S.Button type="button" onClick={onReset}>검색·필터 초기화</S.Button></S.Empty>}
    <S.Pagination aria-label={`${title} 페이지 선택`}>
      <S.Button type="button" aria-label="이전 페이지" disabled={currentPage === 1} onClick={() => onPage(currentPage - 1)}><ChevronLeft size={16} /></S.Button>
      {Array.from({ length: pages }, (_, index) => index + 1).map((number) => <S.Button type="button" key={number} aria-label={`${number}페이지`} aria-current={currentPage === number ? "page" : undefined} $variant={currentPage === number ? "primary" : undefined} onClick={() => onPage(number)}>{number}</S.Button>)}
      <S.Button type="button" aria-label="다음 페이지" disabled={currentPage === pages} onClick={() => onPage(currentPage + 1)}><ChevronRight size={16} /></S.Button>
    </S.Pagination>
  </S.Panel>;
}

function RecentPanel({ title, to, rows, render }) {
  return <S.Panel><S.PanelHeading><h2>{title}</h2><S.TextLink to={to}>전체 보기 <ArrowRight size={14} aria-hidden="true" /></S.TextLink></S.PanelHeading>
    {rows.length ? <S.RecentList>{rows.map((row) => <li key={row.id}>{render(row)}</li>)}</S.RecentList> : <S.Empty>최근 내역이 없습니다.</S.Empty>}
  </S.Panel>;
}

export default function AdminPage() {
  const [data, setData] = useState(createDemoData);
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
  const { members, posts, comments, reports, schedules } = data;
  const member = (id) => members.find((item) => item.id === id);
  const nickname = (id) => member(id)?.nickname || "알 수 없는 회원";
  const isReported = (reportType, id) => reports.some((item) => item.type === reportType && item.targetId === id);
  const reportBadge = (reportType, id) => <Badge>{isReported(reportType, id) ? "신고됨" : "없음"}</Badge>;
  const pendingCount = reports.filter((item) => item.status === "처리 대기").length;

  useEffect(() => { setDraft(query); }, [query, view, isComments]);
  useEffect(() => { setModal(null); setConfirmation(null); setNotice(""); }, [view, isComments]);
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
    setModal({ kind, id });
  }
  function closeDetail() {
    setConfirmation(null);
    setModal(null);
  }
  function requestAction(action, id, label, description, danger = false) {
    setConfirmation({ action, id, label, description, danger });
  }
  function applyAction() {
    if (!confirmation) return;
    const { action, id, label } = confirmation;
    setData((current) => {
      let next = { ...current };
      const changeMember = (targetId, nextStatus) => {
        next.members = next.members.map((item) => item.id === targetId && item.status !== "탈퇴" ? { ...item, status: nextStatus } : item);
      };
      const deletePost = (targetId) => {
        next.posts = next.posts.filter((item) => item.id !== targetId);
        next.comments = next.comments.filter((item) => item.postId !== targetId);
      };
      if (action === "suspend") changeMember(id, "이용 정지");
      if (action === "restore") changeMember(id, "정상");
      if (action === "withdraw") changeMember(id, "탈퇴");
      if (action === "deletePost") deletePost(id);
      if (action === "deleteComment") next.comments = next.comments.filter((item) => item.id !== id);
      if (action.startsWith("report")) {
        const report = next.reports.find((item) => item.id === id);
        if (!report || report.status !== "처리 대기") return current;
        if (action === "reportSuspend") changeMember(report.targetUserId, "이용 정지");
        if (action === "reportDelete") {
          if (report.type === "게시글") deletePost(report.targetId);
          if (report.type === "댓글") next.comments = next.comments.filter((item) => item.id !== report.targetId);
        }
        next.reports = next.reports.map((item) => item.id === id ? { ...item, status: action === "reportReject" ? "반려" : "처리 완료" } : item);
      }
      return next;
    });
    setConfirmation(null);
    setNotice(`${label} 처리가 완료되었습니다.`);
    if (action === "deletePost" || action === "deleteComment") closeDetail();
  }

  let rows = [];
  let columns = [];
  let listTitle = "";
  const detailButton = (kind, item, text = "상세 보기") => <S.TextButton type="button" onClick={() => openDetail(kind, item.id)}>{text}</S.TextButton>;
  if (view === "members") {
    rows = members.filter((item) => (!status || item.status === status) && (!dorm || item.dorm === dorm) && matches([item.loginId, item.nickname, item.email], query));
    listTitle = "회원 목록";
    columns = [["회원 번호", (item) => item.id], ["프로필", (item) => <ProfileImage as={S.Avatar} major={item.major} alt={`${item.nickname} 프로필`} />],
      ["아이디", (item) => detailButton("member", item, item.loginId)], ["닉네임", (item) => item.nickname], ["이메일", (item) => item.email],
      ["생활관", (item) => item.dorm], ["가입일", (item) => item.createdAt], ["회원 상태", (item) => <Badge>{item.status}</Badge>]];
  } else if (view === "community" && !isComments) {
    rows = posts.filter((item) => (!category || item.category === category) && (!reportedOnly || isReported("게시글", item.id)) && matches([item.title, item.content, nickname(item.authorId)], query));
    rows.sort((a, b) => (sort === "조회순" ? b.views - a.views : sort === "좋아요순" ? b.likes - a.likes : b.createdAt.localeCompare(a.createdAt)) || b.id - a.id);
    listTitle = "게시글 목록";
    columns = [["번호", (item) => item.id], ["게시판", (item) => item.category], ["게시글 제목", (item) => <S.CellText>{detailButton("post", item, item.title)}</S.CellText>],
      ["작성자", (item) => nickname(item.authorId)], ["작성일", (item) => item.createdAt], ["조회", (item) => item.views], ["좋아요", (item) => item.likes],
      ["댓글", (item) => comments.filter((comment) => comment.postId === item.id).length], ["신고 여부", (item) => reportBadge("게시글", item.id)], ["관리", (item) => detailButton("post", item)]];
  } else if (isComments) {
    rows = comments.filter((item) => (!reportedOnly || isReported("댓글", item.id)) && matches([item.content, nickname(item.authorId)], query));
    rows.sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
    listTitle = "댓글 목록";
    columns = [["댓글 내용", (item) => <S.CellText>{detailButton("comment", item, item.content)}</S.CellText>], ["작성자", (item) => nickname(item.authorId)],
      ["작성일", (item) => item.createdAt], ["작성된 게시글", (item) => <S.CellText>{detailButton("post", { id: item.postId }, posts.find((post) => post.id === item.postId)?.title || "삭제된 게시글")}</S.CellText>],
      ["신고 여부", (item) => reportBadge("댓글", item.id)], ["관리", (item) => detailButton("comment", item)]];
  } else if (view === "reports") {
    rows = reports.filter((item) => (!status || item.status === status) && (!type || item.type === type) && matches([item.id, item.targetContent, nickname(item.reporterId), nickname(item.targetUserId), item.reason], query));
    listTitle = "신고 목록";
    columns = [["신고 번호", (item) => detailButton("report", item, `#${item.id}`)], ["대상 유형", (item) => <Badge>{item.type}</Badge>],
      ["대상 내용", (item) => <S.CellText>{detailButton("report", item, item.targetContent)}</S.CellText>], ["신고자", (item) => nickname(item.reporterId)],
      ["대상 사용자", (item) => nickname(item.targetUserId)], ["신고 사유", (item) => item.reason], ["접수일", (item) => item.createdAt], ["처리 상태", (item) => <Badge>{item.status}</Badge>]];
  } else if (view === "schedules") {
    rows = schedules.filter((item) => (!category || item.category === category) && matches([item.title, item.content, item.source], query));
    listTitle = "수집 일정 목록";
    columns = [["일정 제목", (item) => <S.CellText>{detailButton("schedule", item, item.title)}</S.CellText>], ["일정 날짜", (item) => item.date],
      ["일정 내용", (item) => <S.CellText>{item.content}</S.CellText>], ["일정 분류", (item) => <Badge>{item.category}</Badge>], ["일정 출처", (item) => item.source], ["수집 일시", (item) => item.collectedAt]];
  }

  const selected = modal && ({ member: members, post: posts, comment: comments, report: reports, schedule: schedules }[modal.kind] || []).find((item) => item.id === modal.id);
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
        {view === "dashboard" ? <>
          <S.Notice><CircleAlert size={20} aria-hidden="true" /><p>{pendingCount ? <>확인이 필요한 신고가 <strong>{pendingCount}건</strong> 있어요.</> : "처리 대기 중인 신고가 없습니다."}</p><S.TextLink to={url("reports", { status: "처리 대기" })}>신고 확인하기 <ArrowRight size={16} aria-hidden="true" /></S.TextLink></S.Notice>
          <S.Stats>{[
            ["전체 회원 수", members.length, Users, "members", {}], ["전체 게시글 수", posts.length, ClipboardList, "community", {}],
            ["전체 댓글 수", comments.length, MessageSquare, "community", { tab: "comments" }], ["전체 신고 수", reports.length, Flag, "reports", {}],
            ["처리 대기 신고 수", pendingCount, CircleAlert, "reports", { status: "처리 대기" }],
            ["수집된 학교 일정 수", schedules.filter((item) => item.category === "학교 일정").length, CalendarDays, "schedules", { category: "학교 일정" }],
            ["수집된 기숙사 일정 수", schedules.filter((item) => item.category === "기숙사 일정").length, Database, "schedules", { category: "기숙사 일정" }],
          ].map(([label, count, Icon, target, filters]) => <S.StatLink key={label} to={url(target, filters)}><Icon size={20} aria-hidden="true" /><span>{label}</span><strong>{count.toLocaleString()}</strong></S.StatLink>)}</S.Stats>
          <S.RecentGrid>
            <RecentPanel title="최근 가입 회원" to={url("members")} rows={recent(members)} render={(item) => <S.Row><ProfileImage as={S.Avatar} major={item.major} alt={`${item.nickname} 프로필`} /><div>{detailButton("member", item, item.nickname)}<S.Meta>{item.dorm} · {item.createdAt}</S.Meta></div><Badge>{item.status}</Badge></S.Row>} />
            <RecentPanel title="최근 작성된 게시글" to={url("community")} rows={recent(posts)} render={(item) => <>{detailButton("post", item, item.title)}<S.Meta>{item.category} · {nickname(item.authorId)} · {item.createdAt}</S.Meta></>} />
            <RecentPanel title="최근 접수된 신고" to={url("reports")} rows={recent(reports)} render={(item) => <><S.Row><Badge>{item.status}</Badge>{detailButton("report", item, `${item.type} · ${item.reason}`)}</S.Row><S.Meta>#{item.id} · {item.createdAt}</S.Meta></>} />
            {SCHEDULE_TYPES.map((scheduleType) => <RecentPanel key={scheduleType} title={`최근 수집된 ${scheduleType}`} to={url("schedules", { category: scheduleType })} rows={recent(schedules.filter((item) => item.category === scheduleType), "collectedAt")} render={(item) => <>{detailButton("schedule", item, item.title)}<S.Meta>일정 {item.date} · 수집 {item.collectedAt}</S.Meta></>} />)}
          </S.RecentGrid>
        </> : <>
          {view === "community" && <S.Tabs aria-label="커뮤니티 관리 항목"><S.TabLink to={url("community")} $active={!isComments} aria-current={!isComments ? "page" : undefined}>게시글 관리</S.TabLink><S.TabLink to={url("community", { tab: "comments" })} $active={isComments} aria-current={isComments ? "page" : undefined}>댓글 관리</S.TabLink></S.Tabs>}
          {view === "schedules" && <>
            <S.Notice><Database size={20} aria-hidden="true" /><p>학교·기숙사 일정은 외부 사이트에서 자동 수집됩니다. 일정 상세에서 수집 내용과 출처를 확인할 수 있습니다.</p></S.Notice>
            <S.CrawlGrid>{SCHEDULE_TYPES.map((scheduleType, index) => <S.Panel key={scheduleType}>
              <S.PanelHeading><h2>{scheduleType}</h2><Badge>{index ? "수집 중" : "수집 완료"}</Badge></S.PanelHeading>
              <DetailFields items={[["최근 크롤링 실행", index ? "2026-09-14 12:00" : "2026-09-14 06:00"], ["최근 수집된 일정 수", `${schedules.filter((item) => item.category === scheduleType && item.collectedAt.startsWith(date(14))).length}건`], ["전체 수집된 일정 수", `${schedules.filter((item) => item.category === scheduleType).length}건`], ["최근 수집 완료", "2026-09-14 06:00"]]} />
              <S.CrawlHistory>{index ? "진행 중인 수집이 끝나면 새 일정이 반영됩니다." : <S.Row><Badge>수집 실패</Badge><span>이전 실행 09.13 05:00 · 응답 지연 후 재수집 완료</span></S.Row>}</S.CrawlHistory>
            </S.Panel>)}</S.CrawlGrid>
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
          <DataTable title={listTitle} columns={columns} rows={rows} page={page} onPage={changePage} onReset={resetFilters} />
        </>}
      </S.Content>
    </S.Layout>
    {modal && <S.Dialog ref={dialogRef} aria-labelledby="admin-dialog-title" onCancel={(event) => { event.preventDefault(); if (confirmation) setConfirmation(null); else closeDetail(); }} onClick={(event) => { if (event.target === event.currentTarget) { const rect = event.currentTarget.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) { if (confirmation) setConfirmation(null); else closeDetail(); } } }}>
      <S.DialogHeading><h2 id="admin-dialog-title">{modalTitle}</h2><S.Button type="button" aria-label={confirmation ? "확인 취소" : "상세 닫기"} onClick={() => confirmation ? setConfirmation(null) : closeDetail()}><X size={18} /></S.Button></S.DialogHeading>
      {confirmation ? <><S.DialogBody><h3>{confirmation.label} 처리하시겠습니까?</h3><p>{confirmation.description}</p></S.DialogBody><S.Actions><S.Button type="button" onClick={() => setConfirmation(null)}>취소</S.Button><S.Button type="button" $variant={confirmation.danger ? "danger" : "primary"} onClick={applyAction}>{confirmation.label}</S.Button></S.Actions></>
        : selected ? <>
          {modal.kind === "member" && <>
            <S.DialogBody><S.Row><ProfileImage as={S.Avatar} $large major={selected.major} alt={`${selected.nickname} 프로필`} /><div><h3>{selected.nickname}</h3><Badge>{selected.status}</Badge></div></S.Row></S.DialogBody>
            <DetailFields items={[["회원 번호", selected.id], ["아이디", selected.loginId], ["닉네임", selected.nickname], ["이메일", selected.email], ["성별", selected.gender], ["생활관", selected.dorm], ["학부", selected.faculty], ["전공", selected.major], ["학번", selected.studentId], ["학년", selected.grade], ["MBTI", selected.mbti], ["가입일", selected.createdAt]]} />
            <S.Activity>{[["작성 게시글", posts.filter((item) => item.authorId === selected.id).length], ["작성 댓글", comments.filter((item) => item.authorId === selected.id).length], ["신고 받은 횟수", reports.filter((item) => item.targetUserId === selected.id).length]].map(([label, count]) => <div key={label}><strong>{count}</strong><span>{label}</span></div>)}</S.Activity>
            <S.Actions>
              <S.Button type="button" disabled={selected.status === "탈퇴"} $variant="danger" onClick={() => requestAction("withdraw", selected.id, "회원 탈퇴", `${selected.nickname} 회원을 탈퇴 상태로 변경합니다. 탈퇴 처리 후에는 이용 정지를 해제할 수 없습니다.`, true)}>회원 탈퇴 처리</S.Button>
              <S.Button type="button" disabled={selected.status === "탈퇴"} $variant="primary" onClick={() => requestAction(selected.status === "이용 정지" ? "restore" : "suspend", selected.id, selected.status === "이용 정지" ? "이용 정지 해제" : "이용 정지", `${selected.nickname} 회원의 이용 상태를 변경합니다.`)}>{selected.status === "이용 정지" ? "이용 정지 해제" : "이용 정지"}</S.Button>
            </S.Actions>
          </>}
          {modal.kind === "post" && <>
            <S.DialogBody><S.Chips><Badge>{selected.category}</Badge>{reportBadge("게시글", selected.id)}</S.Chips><h3>{selected.title}</h3><S.Meta>{nickname(selected.authorId)} · {selected.createdAt}</S.Meta></S.DialogBody>
            <S.DialogBody><p>{selected.content}</p></S.DialogBody>
            <DetailFields items={[["조회 수", selected.views], ["좋아요 수", selected.likes], ["댓글 수", comments.filter((item) => item.postId === selected.id).length]]} />
            <S.Actions><S.Button type="button" onClick={closeDetail}>닫기</S.Button><S.Button type="button" $variant="danger" onClick={() => requestAction("deletePost", selected.id, "게시글 삭제", `‘${selected.title}’ 게시글과 해당 게시글의 댓글이 목록에서 삭제됩니다.`, true)}>게시글 삭제</S.Button></S.Actions>
          </>}
          {modal.kind === "comment" && <>
            <DetailFields items={[["작성자", nickname(selected.authorId)], ["작성일", selected.createdAt], ["신고 여부", reportBadge("댓글", selected.id)], ["작성된 게시글", detailButton("post", { id: selected.postId }, posts.find((item) => item.id === selected.postId)?.title || "삭제된 게시글"), true], ["댓글 내용", selected.content, true]]} />
            <S.Actions><S.Button type="button" onClick={closeDetail}>닫기</S.Button><S.Button type="button" $variant="danger" onClick={() => requestAction("deleteComment", selected.id, "댓글 삭제", "이 댓글을 목록에서 삭제합니다. 삭제한 댓글은 복원할 수 없습니다.", true)}>댓글 삭제</S.Button></S.Actions>
          </>}
          {modal.kind === "report" && <>
            <DetailFields items={[["신고 번호", `#${selected.id}`], ["현재 처리 상태", <Badge>{selected.status}</Badge>], ["신고 대상 유형", selected.type], ["신고 접수일", selected.createdAt],
              ["신고자 정보", `${nickname(selected.reporterId)} (${member(selected.reporterId)?.loginId})\n${member(selected.reporterId)?.email}`],
              ["신고 대상 사용자 정보", <>{nickname(selected.targetUserId)} ({member(selected.targetUserId)?.loginId})<S.Meta>{member(selected.targetUserId)?.email}</S.Meta><Badge>{member(selected.targetUserId)?.status}</Badge></>],
              ["신고 대상 내용", selected.targetContent, true], ["신고 사유", selected.reason], ["상세 사유", selected.description, true]]} />
            {selected.type !== "사용자" && <S.DialogBody>{(selected.type === "게시글" ? posts : comments).some((item) => item.id === selected.targetId) ? detailButton(selected.type === "게시글" ? "post" : "comment", { id: selected.targetId }, "신고 대상 상세 확인") : <Badge>삭제된 대상</Badge>}</S.DialogBody>}
            <S.Actions>
              <S.Button type="button" disabled={selected.status !== "처리 대기" || member(selected.targetUserId)?.status !== "정상"} $variant="danger" onClick={() => requestAction("reportSuspend", selected.id, "회원 이용 정지", "신고 대상 회원을 이용 정지하고 이 신고를 처리 완료로 변경합니다.", true)}>회원 이용 정지</S.Button>
              {selected.type !== "사용자" && <S.Button type="button" $variant="danger" disabled={selected.status !== "처리 대기" || !(selected.type === "게시글" ? posts : comments).some((item) => item.id === selected.targetId)} onClick={() => requestAction("reportDelete", selected.id, `${selected.type} 삭제`, `신고된 ${selected.type}${selected.type === "게시글" ? "과 해당 댓글" : ""}을 삭제하고 이 신고를 처리 완료로 변경합니다.`, true)}>{selected.type} 삭제</S.Button>}
              <S.Button type="button" disabled={selected.status !== "처리 대기"} onClick={() => requestAction("reportReject", selected.id, "신고 반려", "신고를 반려 상태로 변경합니다.")}>신고 반려</S.Button>
              <S.Button type="button" disabled={selected.status !== "처리 대기"} $variant="primary" onClick={() => requestAction("reportComplete", selected.id, "처리 완료", "검토를 마치고 이 신고를 처리 완료로 변경합니다.")}>처리 완료</S.Button>
            </S.Actions>
          </>}
          {modal.kind === "schedule" && <><DetailFields items={[["일정 제목", selected.title, true], ["일정 날짜", selected.date], ["일정 분류", selected.category], ["일정 내용", selected.content, true], ["일정 출처", selected.source, true], ["수집 일시", selected.collectedAt]]} /><S.Actions><S.Button type="button" onClick={closeDetail}>닫기</S.Button></S.Actions></>}
        </> : <S.Empty><h3>해당 내역을 찾을 수 없습니다</h3><p>삭제되었거나 더 이상 표시할 수 없는 내역입니다.</p><S.Button type="button" onClick={closeDetail}>닫기</S.Button></S.Empty>}
    </S.Dialog>}
  </S.Page>;
}
