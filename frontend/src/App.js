import { startTransition, useEffect, useState } from "react";
import { Navigate, Outlet, Route, Routes, useLocation, useNavigate } from "react-router-dom";

import MainLayout from "./layouts/MainLayout";
import DetailedProfile from "./components/common/ProfileCard/DetailedProfile";

import ChatPage from "./pages/chat/ChatPage";
import AdminPage from "./pages/admin/AdminPage";
import CalendarPage from "./calendar/CalendarPage";
import { CalendarDataProvider } from "./calendar/CalendarDataContext";
import CommunityPage from "./pages/community/CommunityPage";
import FindPasswordPage from "./pages/auth/FindPasswordPage";
import LoginPage from "./pages/auth/LoginPage";
import MainPage from "./pages/main/MainPage";
import MyPage from "./pages/mypage/MyPage";
import RoommateSearchPage from "./pages/roommate/RoommateSearchPage";
import SignupPage from "./pages/auth/SignupPage";

const AUTHENTICATED_USER_KEY = "roomieAuthenticatedUser";

function getStoredAuthenticatedUser() {
  try {
    if (!window.localStorage.getItem("accessToken")) return null;

    const user = JSON.parse(
      window.localStorage.getItem(AUTHENTICATED_USER_KEY),
    );
    if (!user?.id || typeof user.name !== "string") return null;

    return user;
  } catch {
    return null;
  }
}

function clearStoredAuthentication() {
  try {
    [AUTHENTICATED_USER_KEY, "accessToken", "token", "user", "profile"]
      .forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // 저장소를 사용할 수 없어도 화면의 로그인 상태는 해제합니다.
  }
}

function AdminRoute({ currentUser }) {
  let accessToken;
  try {
    accessToken = window.localStorage.getItem("accessToken");
  } catch {
    // A token is required by the existing admin API client.
  }

  if (!accessToken) return <Navigate to="/login" replace />;
  return currentUser?.role === "ADMIN"
    ? <AdminPage />
    : <Navigate to="/" replace />;
}

function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const backgroundLocation = location.state?.backgroundLocation;
  const [currentUser, setCurrentUser] = useState(null);
  const [authInitialized, setAuthInitialized] = useState(false);

  useEffect(() => {
    // 하위 화면이 로그인 여부를 판단하기 전에 저장된 인증 상태를 복원합니다.
    const storedUser = getStoredAuthenticatedUser();
    if (!storedUser) clearStoredAuthentication();
    setCurrentUser(storedUser);
    setAuthInitialized(true);
  }, []);

  const handleLogin = ({ id, displayName, schoolEmail, role }) => {
    const name = displayName || schoolEmail || "내 프로필";
    const authenticatedUser = {
      id,
      role,
      name,
      initial: name.slice(0, 1),
    };

    setCurrentUser(authenticatedUser);

    try {
      window.localStorage.setItem(
        AUTHENTICATED_USER_KEY,
        JSON.stringify(authenticatedUser),
      );
    } catch {
      // 저장소를 사용할 수 없어도 현재 화면의 로그인은 계속 진행합니다.
    }
  };

  const handleLogout = () => {
    clearStoredAuthentication();
    // 인증 상태와 경로를 함께 변경해 기존 화면의 로그인 리다이렉트를 방지합니다.
    startTransition(() => {
      setCurrentUser(null);
      navigate("/", { replace: true });
    });
  };

  if (!authInitialized) return null;

  const myPageElement = <MyPage currentUser={currentUser} onUserUpdated={(user) => handleLogin({ id: user.id, displayName: user.nickname, schoolEmail: user.schoolEmail, role: currentUser?.role })} />;

  return (
    <CalendarDataProvider userId={currentUser?.id}>
      <Routes location={backgroundLocation || location}>
        <Route
          element={
            <MainLayout currentUser={currentUser} onLogout={handleLogout} />
          }
        >
          <Route index element={<MainPage currentUser={currentUser} />} />
          <Route path="/admin" element={<AdminRoute currentUser={currentUser} />} />
          <Route
            path="/roommates"
            element={<><RoommateSearchPage currentUser={currentUser} /><Outlet /></>}
          >
            <Route path=":profileId" element={<DetailedProfile />} />
          </Route>
          <Route
            path="/community"
            element={<CommunityPage currentUser={currentUser} />}
          />
          <Route
            path="/community/posts/new"
            element={<CommunityPage currentUser={currentUser} />}
          />
          <Route
            path="/community/posts/:postId"
            element={<CommunityPage currentUser={currentUser} />}
          />
          <Route
            path="/community/posts/:postId/edit"
            element={<CommunityPage currentUser={currentUser} />}
          />
          <Route
            path="/community/my-posts"
            element={<CommunityPage currentUser={currentUser} />}
          />
          <Route
            path="/community/my-comments"
            element={<CommunityPage currentUser={currentUser} />}
          />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/calendar" element={<CalendarPage />} />
          <Route path="/profile" element={myPageElement} />
          <Route path="/mypage" element={myPageElement} />
          <Route
            path="/profile-survey"
            element={<Navigate to="/profile?tab=survey" replace />}
          />
          <Route
            path="/roommate-preference-survey"
            element={<Navigate to="/profile?tab=preferences" replace />}
          />
        </Route>
        <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/find-password" element={<FindPasswordPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
      {backgroundLocation && (
        <Routes>
          <Route path="/roommates/:profileId" element={<DetailedProfile />} />
        </Routes>
      )}
    </CalendarDataProvider>
  );
}

export default App;
