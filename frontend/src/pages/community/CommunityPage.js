import { useEffect, useMemo, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  ArrowRight, Eye, Gift, Heart, Image, LayoutGrid,
  MessageCircle, MessageSquare, Package, PenLine, Search, ShieldCheck, UserRound, X,
} from "lucide-react";

import Pagination from "../../components/common/Pagination/Pagination";
import CommunityPostPage from "./CommunityPostPage";
import CommunityPostFormPage from "./CommunityPostFormPage";
import * as S from "./CommunityPageCss";

const CATEGORIES = [
  { name: "전체", icon: LayoutGrid, description: "생활관의 모든 이야기를 만나보세요." },
  { name: "자유게시판", icon: MessageCircle, description: "소소한 일상부터 궁금했던 이야기까지, 편하게 나눠요." },
  { name: "나눔게시판", icon: Gift, description: "나에게 남는 물건이 누군가에게는 필요한 선물이 돼요." },
  { name: "택배게시판", icon: Package, description: "택배 수령과 분실물 소식을 함께 확인해요." },
  { name: "민원게시판", icon: ShieldCheck, description: "더 편안한 생활관을 위해 서로의 의견을 나눠요." },
];
const PAGE_SIZE = 6;
const SORTS = ["최신순", "조회순", "좋아요순"];
const DORMITORIES = ["나래관", "호연관", "창조관", "비봉관"];
const SAMPLE_IMAGE = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 480"><rect width="800" height="480" fill="#f1eee6"/><rect x="70" y="56" width="660" height="320" rx="20" fill="#faf9f6"/><path d="M110 380h580v20H110z" fill="#c6ab86"/><path d="M210 380v50m380-50v50" stroke="#c6ab86" stroke-width="16"/><path d="M292 354V180l97-64" fill="none" stroke="#0c3273" stroke-width="14" stroke-linecap="round"/><path d="m365 98 55 38-80 44z" fill="#0c3273"/><rect x="243" y="352" width="100" height="17" rx="8" fill="#0c3273"/><path d="M509 283h74l-10 87h-54z" fill="#b27c60"/><path d="M546 295v-85m0 49c-50 0-55-47-55-47 46 0 55 47 55 47m0-25c43 0 52-49 52-49-46 0-52 49-52 49" fill="#88b9a4" stroke="#5b8d76" stroke-width="5"/><rect x="378" y="341" width="103" height="26" rx="4" fill="#dbb65a"/><rect x="390" y="322" width="91" height="19" rx="4" fill="#93bdb7"/></svg>',
)}`;

const POST_SEEDS = [
  ["자유게시판", "룸메이트와 함께 정한 작은 생활 규칙, 생각보다 좋아요", "포근한구름", "이번 학기에 처음 만난 룸메이트와 소등 시간, 청소 요일, 통화 장소를 미리 이야기해 봤어요.\n\n처음에는 조금 어색했는데, 각자 중요하게 생각하는 것을 적어 보니 서로 이해하기 훨씬 편하더라고요. 일주일에 한 번 함께 청소하고 늦은 밤에는 이어폰을 사용하기로 했어요.\n\n여러분은 룸메이트와 어떤 약속을 정했나요? 서로 편하게 지내는 팁을 들려주세요!"],
  ["나눔게시판", "책상 스탠드와 작은 화분, 필요한 분께 나눔해요", "민트초코", "방을 정리하면서 잘 쓰던 스탠드와 작은 화분을 나눔하려고 해요. 스탠드는 밝기 조절이 가능하고 정상 작동합니다.\n\n사진 속 책은 나눔에 포함되지 않아요. 오늘 저녁 나래관 로비에서 전달할 수 있어요. 필요하신 분은 댓글 남겨주세요!"],
  ["택배게시판", "호연관 택배함에서 비슷한 상자를 잘못 가져왔어요", "느긋한오후", "제 택배와 비슷한 상자를 잘못 가져와 바로 택배함 원래 자리에 두었습니다. 찾으시는 분은 확인해 주세요. 다음부터 수령인 이름을 꼭 확인하겠습니다."],
  ["자유게시판", "아침 수업 전에 가볍게 먹기 좋은 메뉴 추천해 주세요", "한걸음", "아침 수업이 있는 날에는 시간이 늘 빠듯하네요. 준비하기 간단하면서 든든한 아침 메뉴가 있을까요? 저는 요즘 바나나와 요거트를 먹고 있어요."],
  ["민원게시판", "늦은 밤 복도에서는 조금만 조용히 이야기해 주세요", "조용한숲", "밤 12시가 넘어서 복도 대화 소리가 방 안까지 잘 들리는 편이에요. 늦은 시간에는 목소리를 조금만 낮춰 주시면 감사하겠습니다. 함께 편히 쉴 수 있으면 좋겠어요."],
  ["나눔게시판", "새 파일 홀더와 연습장 가져가실 분 계신가요?", "따뜻한차", "사용하지 않은 A4 파일 홀더 두 개와 연습장 세 권이 있어요. 새 학기 필기용으로 필요하신 분께 드릴게요. 원하는 물품을 댓글로 알려주세요."],
  ["자유게시판", "주말에 같이 도서관에서 공부할 분을 찾아요", "새벽별", "토요일 오후에 도서관에서 각자 공부하고 중간에 잠깐 쉬려고 해요. 조용히 함께 공부할 분은 댓글 남겨주세요."],
  ["택배게시판", "택배 포장에 적힌 개인정보는 떼고 버려요", "정리왕", "분리수거할 때 상자의 운송장에 이름과 연락처가 남아 있는 경우가 있어요. 운송장을 제거하고 테이프도 떼면 분리수거하기 편합니다."],
  ["민원게시판", "세탁이 끝나면 다음 사람을 위해 빨래를 꺼내 주세요", "나래지기", "저녁에는 세탁실 이용자가 많아요. 세탁 종료 알람을 맞춰 두고 끝나면 빨래를 바로 꺼내 주시면 모두 편하게 사용할 수 있을 것 같아요."],
  ["자유게시판", "기숙사 책상 정리, 여러분만의 방법이 궁금해요", "민트초코", "작은 책상을 효율적으로 쓰고 싶어서 정리 중이에요. 자주 쓰는 물건만 꺼내 두는 것 외에 좋은 방법이 있을까요?"],
  ["나눔게시판", "이사하고 남은 깨끗한 종이 상자 나눔합니다", "정리왕", "중간 크기 상자가 네 개 남았어요. 접어서 보관 중이고 물건 정리나 택배 발송할 때 쓰기 좋아요."],
  ["택배게시판", "비룡관에서 우산을 찾고 있어요", "포근한구름", "어제 택배를 찾으면서 검은색 장우산을 놓고 온 것 같아요. 손잡이에 초록색 고리가 달려 있어요. 보신 분은 댓글 부탁드려요."],
  ["자유게시판", "혼자 먹는 저녁도 즐겁게, 오늘의 메뉴 공유해요", "따뜻한차", "오늘은 식당에서 저녁을 먹고 산책했어요. 여러분의 오늘 저녁 메뉴도 궁금합니다. 맛있게 먹은 메뉴를 추천해 주세요."],
  ["민원게시판", "공용 냉장고 음식에 보관 날짜를 적으면 어떨까요?", "한걸음", "냉장고에 오래 남아 있는 음식을 구분하기 어려워요. 이름과 보관 날짜를 작은 메모에 적어 두면 정리할 때 도움이 될 것 같아요."],
  ["나눔게시판", "사이즈가 맞지 않는 수납 바구니 나눔해요", "느긋한오후", "흰색 수납 바구니 두 개를 나눔합니다. 제 선반에는 맞지 않아 거의 사용하지 않았어요. 필요한 분께 드리고 싶어요."],
  ["자유게시판", "새 학기 첫 주, 다들 잘 적응하고 계신가요?", "나래지기", "낯선 생활관도 조금씩 익숙해지고 있어요. 새로 알게 된 친구들과 밥을 먹으니 한결 편안해졌네요. 여러분의 첫 주는 어땠나요?"],
  ["택배게시판", "택배 받으러 갈 때 학생증 챙기는 습관 들이기", "새벽별", "택배를 찾으러 갔다가 필요한 물건을 다시 가지러 올라온 적이 많아요. 외출할 때 학생증과 휴대폰을 한 번씩 확인해 보세요."],
  ["민원게시판", "휴게실 사용 후 의자를 정리해 주세요", "조용한숲", "휴게실에서 사용한 의자를 제자리에 놓으면 다음 사람이 편하게 이용할 수 있어요. 작은 정리에 함께해 주세요."],
  ["자유게시판", "룸메이트와 취침 시간이 다를 때의 작은 배려", "정리왕", "늦게 들어올 때 쓸 물건을 미리 정리해 두니 소음을 줄일 수 있더라고요. 서로 불편하지 않게 지내는 방법을 나눠 봐요."],
  ["나눔게시판", "사용하지 않은 옷걸이 다섯 개 드려요", "따뜻한차", "옷 정리를 마치니 같은 모양의 옷걸이가 다섯 개 남았어요. 한꺼번에 가져가실 분이 있으면 댓글 부탁드려요."],
  ["자유게시판", "비 오는 날 방 안 습기, 어떻게 관리하세요?", "포근한구름", "비 오는 날이 이어지면 방이 조금 눅눅한 느낌이에요. 환기나 제습제를 사용해 본 경험을 공유해 주세요."],
  ["택배게시판", "큰 택배 상자는 접어서 분리수거장에 놓아요", "나래지기", "접지 않은 상자가 많으면 공간이 금방 차더라고요. 내용물을 비우고 상자를 펼쳐 두면 다음 사람도 편하게 정리할 수 있어요."],
  ["민원게시판", "공용 전자레인지 사용 후 한 번만 확인해 주세요", "민트초코", "음식이 튀었을 때 바로 닦으면 쉽게 정리할 수 있어요. 다음 이용자를 위해 사용 후 내부를 한 번만 확인해 주세요."],
  ["자유게시판", "함께 살면서 고마웠던 순간을 나눠요", "한걸음", "룸메이트가 제가 바쁜 날 대신 환기를 해 줬어요. 사소하지만 고마운 순간이었네요. 여러분의 따뜻한 이야기도 들려주세요."],
  ["나눔게시판", "작은 메모지와 인덱스 스티커 나눔합니다", "새벽별", "공부할 때 쓰려고 여러 개 샀는데 남는 것이 있어요. 필요한 색상을 댓글로 남겨주시면 확인할게요."],
  ["자유게시판", "새로운 생활관에서 시작하는 이번 학기 목표", "조용한숲", "이번 학기에는 매일 같은 시간에 일어나 보려고 해요. 거창하지 않아도 좋아요. 여러분의 작은 목표는 무엇인가요?"],
  ["택배게시판", "분실물 찾았다면 댓글로 알려주시면 좋아요", "따뜻한차", "분실물을 찾은 뒤 댓글을 남겨 두면 도와주신 분들도 소식을 알 수 있어요. 서로 챙겨주는 생활관이 되어 가는 것 같아 좋네요."],
  ["민원게시판", "분리수거할 때 병과 캔의 내용물을 비워 주세요", "정리왕", "음료가 남아 있으면 분리수거함 주변이 금방 지저분해져요. 내용물을 비우고 가볍게 헹군 뒤 배출해 주시면 감사하겠습니다."],
  ["자유게시판", "기숙사에서 처음 맞는 주말, 어떻게 보내세요?", "느긋한오후", "이번 주말에는 방을 정리하고 근처를 천천히 걸어 보려고 해요. 멀리 가지 않고 즐겁게 보내는 방법이 궁금해요."],
  ["나눔게시판", "책상에 놓는 작은 정리함 나눔해요", "나래지기", "필기구를 담기 좋은 정리함이 하나 남았어요. 깨끗이 닦아 두었습니다. 저녁에 로비에서 전달할 수 있어요."],
  ["자유게시판", "서로 다른 생활 습관, 대화로 맞춰가고 있어요", "새벽별", "처음부터 모든 습관이 맞을 수는 없더라고요. 불편한 점을 쌓아두기 전에 편하게 이야기하니 서로 조절하기 쉬웠어요."],
];

function createPosts(currentUser) {
  return POST_SEEDS.map(([category, title, nickname, content], index) => {
    const isMine = index === 1 || index === 8;
    const author = isMine ? currentUser?.name || "민트초코" : nickname;
    const createdAt = new Date(Date.UTC(2026, 8, 6, 5, 30) - index * 5700000).toISOString();
    return {
      id: String(index + 1), category, dormitory: DORMITORIES[index % DORMITORIES.length], title, content, author,
      authorId: isMine ? currentUser?.name || "sample-owner" : `sample-${index}`,
      createdAt, views: 78 + ((index * 47) % 421), likes: 3 + ((index * 13) % 56),
      likedBy: [], reportedBy: [],
      images: index === 1 ? [{ id: "lamp", src: SAMPLE_IMAGE, name: "나눔할 스탠드와 화분.svg" }] : [],
      comments: index === 0 || index === 1 ? [{
        id: `comment-${index}`, author: "따뜻한차", authorId: "sample-commenter",
        content: index === 0 ? "저희도 청소 요일을 정했더니 훨씬 편해졌어요. 작은 약속이 도움이 되네요!" : "스탠드가 정말 깔끔하네요. 아직 나눔 가능한가요?",
        createdAt, reportedBy: [], replies: [{
          id: `reply-${index}`, author: currentUser?.name || "민트초코",
          authorId: currentUser?.name || "sample-owner",
          content: index === 0 ? "맞아요! 서로 먼저 이야기하는 게 제일 중요한 것 같아요." : "네, 아직 가능해요! 저녁에 로비에서 뵈어요.",
          createdAt, reportedBy: [],
        }],
      }, ...(index === 0 ? [{
        id: "my-comment", author: currentUser?.name || "민트초코", authorId: currentUser?.name || "sample-owner",
        content: "저희는 공용 물건을 사용하기 전에 한 번씩 물어보기로 했어요.", createdAt, replies: [], reportedBy: [],
      }] : [])] : [],
    };
  });
}

function formatDate(value) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(new Date(value));
}

function commentCount(post) {
  return post.comments.reduce((total, comment) => total + 1 + comment.replies.length, 0);
}

function CommunityPage({ currentUser }) {
  const searchInputRef = useRef(null);
  const [posts, setPosts] = useState(() => createPosts(currentUser));
  const [searchParams, setSearchParams] = useSearchParams();
  const { postId } = useParams();
  const location = useLocation();
  const pathname = location.pathname.replace(/\/+$/, "");
  const navigate = useNavigate();
  const query = pathname === "/community/my-posts" ? "" : searchParams.get("q") || "";
  const [searchInput, setSearchInput] = useState(query);
  const category = CATEGORIES.some(({ name }) => name === searchParams.get("category"))
    ? searchParams.get("category") : "전체";
  const sort = SORTS.includes(searchParams.get("sort")) ? searchParams.get("sort") : "최신순";
  const isMyPosts = pathname === "/community/my-posts";
  const isMyComments = pathname === "/community/my-comments";
  const isCreate = pathname === "/community/posts/new";
  const isEdit = Boolean(postId) && pathname.endsWith("/edit");
  const post = posts.find((item) => item.id === postId);
  const categoryInfo = CATEGORIES.find(({ name }) => name === category);

  useEffect(() => { setSearchInput(query); }, [query]);
  useEffect(() => {
    const commentElement = location.hash.startsWith("#comment-")
      ? document.getElementById(location.hash.slice(1)) : null;
    if (commentElement) commentElement.scrollIntoView({ block: "center" });
    else window.scrollTo({ top: 0, behavior: "instant" });
  }, [location.pathname, location.search, location.hash]);

  const filteredPosts = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    return posts.filter((item) => (
      (!isMyPosts || (currentUser && item.authorId === currentUser.name)) &&
      (category === "전체" || item.category === category) &&
      (!normalizedQuery || `${item.title} ${item.content}`.toLocaleLowerCase().includes(normalizedQuery))
    )).sort((a, b) => {
      const dateOrder = new Date(b.createdAt) - new Date(a.createdAt);
      if (sort === "조회순") return b.views - a.views || dateOrder;
      if (sort === "좋아요순") return b.likes - a.likes || dateOrder;
      return dateOrder;
    });
  }, [posts, query, category, sort, isMyPosts, currentUser]);

  const myComments = useMemo(() => posts.flatMap((item) => item.comments.flatMap((comment) => [
    { ...comment, postId: item.id, postTitle: item.title, category: item.category, isReply: false },
    ...comment.replies.map((reply) => ({ ...reply, postId: item.id, postTitle: item.title, category: item.category, isReply: true })),
  ])).filter((comment) => currentUser && comment.authorId === currentUser.name)
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)), [posts, currentUser]);

  const resultCount = isMyComments ? myComments.length : filteredPosts.length;
  const totalPages = Math.max(1, Math.ceil(resultCount / PAGE_SIZE));
  const requestedPage = Number(searchParams.get("page"));
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? Math.min(requestedPage, totalPages) : 1;
  const pageItems = (isMyComments ? myComments : filteredPosts).slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const updateFilters = (changes) => {
    const next = new URLSearchParams(searchParams);
    next.delete("page");
    Object.entries(changes).forEach(([key, value]) => {
      if (!value || value === "전체" || value === "최신순") next.delete(key);
      else next.set(key, String(value));
    });
    setSearchParams(next);
  };

  const requireLogin = () => navigate("/login");
  const startPost = () => currentUser ? navigate("/community/posts/new") : requireLogin();
  const savePost = (values) => {
    if (!currentUser) { requireLogin(); return; }
    if (isEdit && post?.authorId !== currentUser.name) return;
    const id = isEdit ? post.id : window.crypto?.randomUUID?.() || `post-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    setPosts((previous) => isEdit
      ? previous.map((item) => item.id === id ? { ...item, ...values } : item)
      : [{
        ...values, id, author: currentUser.name, authorId: currentUser.name,
        dormitory: "나래관",
        createdAt: new Date().toISOString(), views: 0, likes: 0,
        likedBy: [], reportedBy: [], comments: []
      }, ...previous]);
    navigate(`/community/posts/${id}`, { replace: true });
  };

  if ((isCreate || isEdit || isMyPosts || isMyComments) && !currentUser) {
    return <Navigate to="/login" replace />;
  }

  let content;
  if (postId && !post) {
    content = <S.EmptyState>
      <S.EmptyIcon><MessageSquare aria-hidden="true" /></S.EmptyIcon>
      <h1>게시글을 찾을 수 없어요</h1>
      <p>삭제되었거나 올바르지 않은 주소예요. 다른 이야기를 둘러보세요.</p>
      <S.PrimaryLink to="/community">커뮤니티로 돌아가기 <ArrowRight size={16} aria-hidden="true" /></S.PrimaryLink>
    </S.EmptyState>;
  } else if (isEdit && post.authorId !== currentUser.name) {
    content = <S.EmptyState>
      <S.EmptyIcon><ShieldCheck aria-hidden="true" /></S.EmptyIcon>
      <h1>내가 작성한 글만 수정할 수 있어요</h1>
      <p>게시글 상세 화면에서 이야기를 확인해 주세요.</p>
      <S.PrimaryLink to={`/community/posts/${post.id}`}>게시글로 돌아가기</S.PrimaryLink>
    </S.EmptyState>;
  } else if (isCreate || isEdit) {
    content = <CommunityPostFormPage key={post?.id || "new"} post={isEdit ? post : undefined}
      onSave={savePost} onCancel={() => navigate(isEdit ? `/community/posts/${post.id}` : "/community")} />;
  } else if (post) {
    content = <CommunityPostPage key={post.id} post={post} currentUser={currentUser}
      onRequireLogin={requireLogin} onEdit={() => navigate(`/community/posts/${post.id}/edit`)}
      onUpdatePost={(updater) => setPosts((previous) => previous.map((item) => item.id === post.id ? updater(item) : item))}
      onDeletePost={() => {
        if (!currentUser || post.authorId !== currentUser.name) return;
        setPosts((previous) => previous.filter((item) => item.id !== post.id));
        navigate("/community", { replace: true });
      }} />;
  } else {
    content = <>
      <S.Layout>
        <S.Sidebar>
          <S.Navigation aria-label="커뮤니티 게시판">
            <S.NavigationTitle>게시판</S.NavigationTitle>
            <S.CategoryLinks>{CATEGORIES.map(({ name, icon: Icon }) => {
              const active = !isMyPosts && !isMyComments && category === name;
              return <S.NavLink key={name} to={name === "전체" ? "/community" : `/community?category=${encodeURIComponent(name)}`}
                $active={active} aria-current={active ? "page" : undefined}>
                <Icon size={19} aria-hidden="true" /><span>{name}</span>
              </S.NavLink>;
            })}</S.CategoryLinks>
            <S.NavigationTitle $separated>나의 이야기</S.NavigationTitle>
            <S.ActivityLinks>
              <S.NavLink to="/community/my-posts" $active={isMyPosts} aria-current={isMyPosts ? "page" : undefined}><PenLine size={18} aria-hidden="true" /><span>내가 작성한 게시글</span></S.NavLink>
              <S.NavLink to="/community/my-comments" $active={isMyComments} aria-current={isMyComments ? "page" : undefined}><MessageSquare size={18} aria-hidden="true" /><span>내가 작성한 댓글</span></S.NavLink>
            </S.ActivityLinks>
          </S.Navigation>
          <S.KindnessNote><ShieldCheck size={23} aria-hidden="true" /><strong>배려가 머무는 커뮤니티</strong><p>서로를 존중하는 말 한마디로<br />더 편안한 생활을 만들어요.</p></S.KindnessNote>
        </S.Sidebar>
        <S.Board aria-labelledby="board-title">
          <S.BoardHeading $separated={isMyPosts || isMyComments}>
            <div><h2 id="board-title">{isMyPosts ? "내 게시글" : isMyComments ? "내 댓글" : category === "전체" ? "전체 게시글" : category}</h2>{!isMyPosts && !isMyComments && <p>{categoryInfo.description}</p>}</div>
            {!isMyPosts && !isMyComments && <S.PrimaryButton type="button" onClick={startPost}><PenLine size={18} aria-hidden="true" />글쓰기</S.PrimaryButton>}
          </S.BoardHeading>
          {!isMyPosts && !isMyComments && <S.Controls>
            {!isMyPosts && <S.SearchForm role="search" onSubmit={(event) => { event.preventDefault(); updateFilters({ q: searchInput.trim() }); }}>
              <S.SearchField>
                <Search size={19} aria-hidden="true" />
                <S.SearchInput ref={searchInputRef} type="search" aria-label="게시글 제목과 내용 검색" placeholder="제목이나 내용으로 검색해 보세요" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} />
                <S.SearchClearButton type="button" aria-label="검색어 지우기" onClick={() => {
                  setSearchInput("");
                  updateFilters({ q: "" });
                  searchInputRef.current?.focus();
                }}><X size={16} aria-hidden="true" /></S.SearchClearButton>
              </S.SearchField>
              <S.SearchButton type="submit">검색</S.SearchButton>
            </S.SearchForm>}
            <S.SortLabel><span>정렬</span><S.SortSelect aria-label="게시글 정렬" value={sort} onChange={(event) => updateFilters({ sort: event.target.value })}>{SORTS.map((value) => <option key={value}>{value}</option>)}</S.SortSelect></S.SortLabel>
          </S.Controls>}
          {query && !isMyComments && <S.SearchSummary role="status"><span>“{query}” 검색 결과</span></S.SearchSummary>}
          {pageItems.length ? <S.PostList>
            {pageItems.map((item) => isMyComments ? <S.PostRow key={item.id}>
              <S.CommentTop><S.CategoryBadge>{item.isReply ? "대댓글" : "댓글"}</S.CategoryBadge><time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time></S.CommentTop>
              <S.MyComment>{item.content}</S.MyComment>
              <S.OriginalPostLink to={`/community/posts/${item.postId}#comment-${item.id}`}><MessageSquare size={16} aria-hidden="true" /><span>{item.postTitle}</span><ArrowRight size={16} aria-hidden="true" /></S.OriginalPostLink>
            </S.PostRow> : <S.PostRow key={item.id}>
              <S.PostHeading>{category === "전체" && <S.CategoryBadge $category={item.category}>{item.category.replace("게시판", "")}</S.CategoryBadge>}<S.DormitoryBadge>{item.dormitory}</S.DormitoryBadge>{currentUser && item.authorId === currentUser.name && <S.OwnBadge>내 글</S.OwnBadge>}</S.PostHeading>
              <S.PostTitleLink to={`/community/posts/${item.id}`}><h3>{item.title}</h3>{item.images.length > 0 && <S.ImageLabel role="img" aria-label="사진 첨부" title="사진 첨부"><Image size={16} aria-hidden="true" /></S.ImageLabel>}</S.PostTitleLink>
              <S.Excerpt>{item.content}</S.Excerpt>
              <S.PostBottom><S.Metadata><S.Author><UserRound size={14} aria-hidden="true" />{item.author}</S.Author><time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time></S.Metadata><S.Stats><span aria-label={`조회 ${item.views}회`}><Eye size={15} aria-hidden="true" />{item.views}</span><span aria-label={`공감 ${item.likes}개`}><Heart size={15} aria-hidden="true" />{item.likes}</span><span aria-label={`댓글 ${commentCount(item)}개`}><MessageCircle size={15} aria-hidden="true" />{commentCount(item)}</span></S.Stats></S.PostBottom>
            </S.PostRow>)}
          </S.PostList> : <S.EmptyState>
            <S.EmptyIcon>{query ? <Search aria-hidden="true" /> : <MessageSquare aria-hidden="true" />}</S.EmptyIcon>
            <h3>{query ? "검색 결과가 없어요" : isMyComments ? "아직 작성한 댓글이 없어요" : isMyPosts ? "아직 작성한 게시글이 없어요" : "아직 이야기가 없어요"}</h3>
            {!query && <>
              <p>{isMyComments ? "마음이 닿는 게시글에 첫 댓글을 남겨보세요." : "작은 일상도 좋아요. 첫 이야기를 들려주세요."}</p>
              {category !== "전체" ? <S.SecondaryButton type="button" onClick={() => { setSearchInput(""); setSearchParams({}); }}>검색 조건 초기화</S.SecondaryButton> : isMyComments ? <S.PrimaryLink to="/community">게시글 둘러보기 <ArrowRight size={16} aria-hidden="true" /></S.PrimaryLink> : <S.PrimaryButton type="button" onClick={startPost}><PenLine size={16} aria-hidden="true" />첫 글 작성하기</S.PrimaryButton>}
            </>}
          </S.EmptyState>}
          {resultCount > 0 && (
            <Pagination
              currentPage={page}
              totalPages={totalPages}
              onPageChange={(nextPage) => {
                updateFilters({ page: nextPage });
                window.scrollTo({ top: 0, behavior: "smooth" });
              }}
            />
          )}
        </S.Board>
      </S.Layout>
    </>;
  }

  return <S.Page $compactTop={Boolean(postId) || isCreate}><S.Content>{content}</S.Content></S.Page>;
}

export default CommunityPage;
