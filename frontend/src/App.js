import { useState } from "react";
import { Navigate, Outlet, Route, Routes } from "react-router-dom";

import MainLayout from "./layouts/MainLayout";
import DetailedProfile from "./components/common/ProfileCard/DetailedProfile";

import ChatPage from "./pages/chat/ChatPage";
import AdminPage from "./pages/admin/AdminPage";
import CalendarPage from "./calendar/CalendarPage";
import { CalendarDataProvider } from "./calendar/CalendarDataContext";
import CommunityPage from "./pages/community/CommunityPage";
import FindIdPage from "./pages/auth/FindIdPage";
import FindPasswordPage from "./pages/auth/FindPasswordPage";
import LoginPage from "./pages/auth/LoginPage";
import MainPage from "./pages/main/MainPage";
import MyPage from "./pages/mypage/MyPage";
import RoommateSearchPage from "./pages/roommate/RoommateSearchPage";
import SignupPage from "./pages/auth/SignupPage";

const AUTHENTICATED_USER_KEY = "roomieAuthenticatedUser";

function getStoredAuthenticatedUser() {
  if (typeof window === "undefined") {
    return undefined;
  }

  try {
    const storedUser = JSON.parse(
      window.localStorage.getItem(AUTHENTICATED_USER_KEY) || "null",
    );

    if (!storedUser?.name) {
      return undefined;
    }

    return {
      id: storedUser.id,
      name: storedUser.name,
      initial: storedUser.initial || storedUser.name.slice(0, 1),
    };
  } catch {
    return undefined;
  }
}

function App() {
  const [currentUser, setCurrentUser] = useState(getStoredAuthenticatedUser);

  const handleLogin = ({ id, displayName, loginId }) => {
    const name = displayName || loginId || "내 프로필";
    const authenticatedUser = {
      id,
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
    setCurrentUser(null);

    try {
      [
        AUTHENTICATED_USER_KEY,
        "accessToken",
        "token",
        "user",
        "profile",
      ].forEach((key) => {
        window.localStorage.removeItem(key);
      });
    } catch {
      // 저장소를 사용할 수 없는 환경에서도 화면의 로그인 상태는 해제합니다.
    }
  };

  return (
    <CalendarDataProvider>
      <Routes>
        <Route
          element={
            <MainLayout currentUser={currentUser} onLogout={handleLogout} />
          }
        >
          <Route index element={<MainPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route
            path="/roommates"
            element={<><RoommateSearchPage /><Outlet /></>}
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
          <Route path="/profile" element={<MyPage currentUser={currentUser} />} />
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
        <Route path="/find-id" element={<FindIdPage />} />
        <Route path="/find-password" element={<FindPasswordPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </CalendarDataProvider>
  );
}

export default App;
