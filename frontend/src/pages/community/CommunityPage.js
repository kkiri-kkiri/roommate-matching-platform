import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  ArrowRight, Eye, Gift, Heart, Image, LayoutGrid,
  MessageCircle, MessageSquare, Package, PenLine, Search, ShieldCheck, X,
} from "lucide-react";

import * as api from "../../api/communityApi";
import { getPostAuthorDisplay, normalizePost } from "./communityData";
import Pagination from "../../components/common/Pagination/Pagination";
import CommunityPostPage from "./CommunityPostPage";
import CommunityPostFormPage from "./CommunityPostFormPage";
import CommunityActivityActions from "./CommunityActivityActions";
import * as S from "./CommunityPageCss";

const CATEGORIES = [
  { name: "전체", icon: LayoutGrid, description: "생활관의 모든 이야기를 만나보세요." },
  { name: "자유게시판", icon: MessageCircle, description: "소소한 일상부터 궁금했던 이야기까지, 편하게 나눠요." },
  { name: "나눔게시판", icon: Gift, description: "남는 물건을 함께 나눠요." },
  { name: "택배게시판", icon: Package, description: "택배 수령과 분실물 소식을 함께 확인해요." },
  { name: "민원게시판", icon: ShieldCheck, description: "더 편안한 생활관을 위해 서로의 의견을 나눠요." },
];
const PAGE_SIZE = 6;
const SORTS = ["최신순", "조회순", "좋아요순"];
const DORMITORIES = ["나래관", "호연관", "비봉관", "창조관"];
function formatDate(value) {
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(new Date(value));
}

function CommunityPage({ currentUser }) {
  const searchInputRef = useRef(null);
  const [result, setResult] = useState(null);
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [listRevision, setListRevision] = useState(0);
  const requestVersion = useRef(0);
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
  const dormitory = DORMITORIES.includes(searchParams.get("dormitory")) ? searchParams.get("dormitory") : "전체";
  const isMyPosts = pathname === "/community/my-posts";
  const isMyComments = pathname === "/community/my-comments";
  const isCreate = pathname === "/community/posts/new";
  const isEdit = Boolean(postId) && pathname.endsWith("/edit");
  const returnToMyPosts = location.state?.returnToMyPosts;
  const editReturnPath = typeof returnToMyPosts === "string" &&
    (returnToMyPosts === "/community/my-posts" || returnToMyPosts.startsWith("/community/my-posts?"))
    ? returnToMyPosts : null;
  const post = detail?.key === `${postId}:${isEdit}` ? detail.post : null;
  const categoryInfo = CATEGORIES.find(({ name }) => name === category);

  useEffect(() => { setSearchInput(query); }, [query]);
  useEffect(() => {
    const commentElement = location.hash.startsWith("#comment-")
      ? document.getElementById(location.hash.slice(1)) : null;
    if (commentElement) commentElement.scrollIntoView({ block: "center" });
    else window.scrollTo({ top: 0, behavior: "instant" });
  }, [location.pathname, location.search, location.hash, loading]);

  const requestedPage = Number(searchParams.get("page"));
  const page = Number.isInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
  const listKey = pathname + location.search + currentUser?.id;
  const visibleResult = result?.key === listKey ? result.data : null;
  const resultCount = visibleResult?.totalElements || 0;
  const totalPages = Math.max(1, visibleResult?.totalPages || 0);
  const pageItems = visibleResult?.content || [];

  useEffect(() => {
    const version = ++requestVersion.current;
    setError("");
    if (isCreate || ((isEdit || isMyPosts || isMyComments) && !currentUser)) {
      setLoading(false);
      return;
    }
    setLoading(true);
    const params = { page: page - 1, size: PAGE_SIZE, sort: { "최신순": "LATEST", "조회순": "VIEWS", "좋아요순": "LIKES" }[sort] };
    const fetchData = postId ? api.getCommunityPost(postId)
      : isMyComments ? api.getMyCommunityComments(params)
      : isMyPosts ? api.getMyCommunityPosts(params)
      : api.getCommunityPosts({ ...params, category, q: query, dormitory });
    fetchData.then((data) => {
      if (version !== requestVersion.current) return;
      if (postId) setDetail({ key: postId + ":" + isEdit, post: normalizePost(data) });
      else {
        setResult({ key: listKey, data });
        if (page > Math.max(1, data.totalPages)) {
          setSearchParams((previous) => {
            const next = new URLSearchParams(previous);
            next.set("page", String(Math.max(1, data.totalPages)));
            return next;
          }, { replace: true });
        }
      }
    }).catch((failure) => {
      if (version !== requestVersion.current) return;
      setDetail(null);
      setResult(null);
      setError(failure.message);
    }).finally(() => {
      if (version === requestVersion.current) setLoading(false);
    });
    return () => { requestVersion.current += 1; };
  }, [postId, isEdit, isCreate, isMyPosts, isMyComments, currentUser, page, sort, category, query, dormitory, listKey, listRevision, setSearchParams]);

  const refreshPost = async () => {
    const version = requestVersion.current;
    const data = await api.getCommunityPost(postId);
    if (version === requestVersion.current) setDetail({ key: postId + ":" + isEdit, post: normalizePost(data) });
  };

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
  const savePost = async ({ category, title, content, anonymous, images }) => {
    const payload = { category, title, content, anonymous };
    const files = images.filter((image) => image.file instanceof File).map((image) => image.file);
    let id = post?.id;
    if (isEdit) {
      payload.keepImageIds = images.filter((image) => !image.file && post.images.some((existing) => existing.id === image.id)).map((image) => image.id);
      await api.updateCommunityPost(id, payload, files);
    } else id = await api.createCommunityPost(payload, files);
    navigate(isEdit && editReturnPath ? editReturnPath : '/community/posts/' + id, { replace: true });
  };

  if ((isCreate || isEdit || isMyPosts || isMyComments) && !currentUser) {
    return <Navigate to="/login" replace />;
  }

  let content;
  if (postId && loading) {
    content = <S.EmptyState role="status">게시글을 불러오고 있어요.</S.EmptyState>;
  } else if (postId && !post) {
    content = <S.EmptyState>
      <S.EmptyIcon><MessageSquare aria-hidden="true" /></S.EmptyIcon>
      <h1>게시글을 찾을 수 없어요</h1>
      <p>삭제되었거나 올바르지 않은 주소예요. 다른 이야기를 둘러보세요.</p>
      <S.PrimaryLink to="/community">커뮤니티로 돌아가기 <ArrowRight size={16} aria-hidden="true" /></S.PrimaryLink>
    </S.EmptyState>;
  } else if (isEdit && post.author.id !== currentUser.id) {
    content = <S.EmptyState>
      <S.EmptyIcon><ShieldCheck aria-hidden="true" /></S.EmptyIcon>
      <h1>내가 작성한 글만 수정할 수 있어요</h1>
      <p>게시글 상세 화면에서 이야기를 확인해 주세요.</p>
      <S.PrimaryLink to={`/community/posts/${post.id}`}>게시글로 돌아가기</S.PrimaryLink>
    </S.EmptyState>;
  } else if (isCreate || isEdit) {
    content = <CommunityPostFormPage key={post?.id || "new"} post={isEdit ? post : undefined}
      onSave={savePost} onCancel={() => navigate(isEdit ? editReturnPath || `/community/posts/${post.id}` : "/community")} />;
  } else if (post) {
    content = <CommunityPostPage key={post.id} post={post} currentUser={currentUser}
      onRequireLogin={requireLogin} onEdit={() => navigate(`/community/posts/${post.id}/edit`)}
      onRefresh={refreshPost}
      onDeletePost={async () => {
        await api.deleteCommunityPost(post.id);
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
            <S.SortLabel><span>생활관</span><S.SortSelect aria-label="생활관 필터" value={dormitory} onChange={(event) => updateFilters({ dormitory: event.target.value })}>
              <option value="전체">전체</option>{DORMITORIES.map((value) => <option key={value}>{value}</option>)}
            </S.SortSelect></S.SortLabel>
            <S.SortLabel><span>정렬</span><S.SortSelect aria-label="게시글 정렬" value={sort} onChange={(event) => updateFilters({ sort: event.target.value })}>{SORTS.map((value) => <option key={value}>{value}</option>)}</S.SortSelect></S.SortLabel>
          </S.Controls>}
          {query && !isMyComments && <S.SearchSummary role="status"><span>“{query}” 검색 결과</span></S.SearchSummary>}
          {loading ? <S.EmptyState role="status">목록을 불러오고 있어요.</S.EmptyState> : error ? null : pageItems.length ? <S.PostList>
            {pageItems.map((item) => isMyComments ? <S.PostRow key={item.id}>
              <S.CommentTop><S.CategoryBadge>{item.isReply ? "대댓글" : "댓글"}</S.CategoryBadge><time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time></S.CommentTop>
              <S.ActivityContentRow $comment>
                <S.ActivityContent><S.MyComment>{item.content}</S.MyComment></S.ActivityContent>
                <CommunityActivityActions item={item} isComment onChanged={() => setListRevision((value) => value + 1)} />
              </S.ActivityContentRow>
              <S.OriginalPostLink to={`/community/posts/${item.postId}#comment-${item.id}`}><MessageSquare size={16} aria-hidden="true" /><span>{item.postTitle}</span><ArrowRight size={16} aria-hidden="true" /></S.OriginalPostLink>
            </S.PostRow> : <S.PostRow key={item.id}>
              <S.PostHeading>{category === "전체" && <S.CategoryBadge $category={item.category}>{item.category.replace("게시판", "")}</S.CategoryBadge>}<S.DormitoryBadge>{item.dormitory}</S.DormitoryBadge>{currentUser && item.author.id === currentUser.id && <S.OwnBadge>내 글</S.OwnBadge>}</S.PostHeading>
              <S.ActivityContentRow>
                <S.ActivityContent>
                  <S.PostTitleLink to={`/community/posts/${item.id}`}><h3>{item.title}</h3>{item.hasImages && <S.ImageLabel role="img" aria-label="사진 첨부" title="사진 첨부"><Image size={16} aria-hidden="true" /></S.ImageLabel>}</S.PostTitleLink>
                  <S.Excerpt>{item.contentPreview}</S.Excerpt>
                </S.ActivityContent>
                {isMyPosts && item.author.id === currentUser.id && <CommunityActivityActions item={item}
                  onEdit={() => navigate(`/community/posts/${item.id}/edit`, { state: { returnToMyPosts: pathname + location.search } })}
                  onChanged={() => setListRevision((value) => value + 1)} />}
              </S.ActivityContentRow>
              <S.PostBottom><S.Metadata><S.Author><S.AuthorImage src={getPostAuthorDisplay(item).image} alt="" />{getPostAuthorDisplay(item).nickname}</S.Author><time dateTime={item.createdAt}>{formatDate(item.createdAt)}</time></S.Metadata><S.Stats><span aria-label={`조회 ${item.views}회`}><Eye size={15} aria-hidden="true" />{item.views}</span><span aria-label={`공감 ${item.likes}개`}><Heart size={15} aria-hidden="true" />{item.likes}</span><span aria-label={`댓글 ${item.commentCount}개`}><MessageCircle size={15} aria-hidden="true" />{item.commentCount}</span></S.Stats></S.PostBottom>
            </S.PostRow>)}
          </S.PostList> : <S.EmptyState>
            <S.EmptyIcon>{query ? <Search aria-hidden="true" /> : <MessageSquare aria-hidden="true" />}</S.EmptyIcon>
            <h3>{query ? "검색 결과가 없어요" : isMyComments ? "아직 작성한 댓글이 없어요" : isMyPosts ? "아직 작성한 게시글이 없어요" : "아직 이야기가 없어요"}</h3>
            {!query && <>
              <p>{isMyComments ? "마음이 닿는 게시글에 첫 댓글을 남겨보세요." : "작은 일상도 좋아요. 첫 이야기를 들려주세요."}</p>
              {isMyComments ? <S.PrimaryLink to="/community">게시글 둘러보기 <ArrowRight size={16} aria-hidden="true" /></S.PrimaryLink> : <S.PrimaryButton type="button" onClick={startPost}><PenLine size={16} aria-hidden="true" />첫 글 작성하기</S.PrimaryButton>}
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

  return <S.Page $compactTop={Boolean(postId) || isCreate}><S.Content>{error && <p role="alert">{error}</p>}{content}</S.Content></S.Page>;
}

export default CommunityPage;
