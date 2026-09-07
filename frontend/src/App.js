import { useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import ChatPage from "./pages/chat/ChatPage";
import CommunityPage from "./pages/community/CommunityPage";
import FindIdPage from "./pages/auth/FindIdPage";
import FindPasswordPage from "./pages/auth/FindPasswordPage";
import LoginPage from "./pages/auth/LoginPage";
import MainPage from "./pages/main/MainPage";
import PreferenceSurveyPage from "./pages/survey/PreferenceSurveyPage";
import ProfileSurveyPage from "./pages/survey/ProfileSurveyPage";
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
      name: storedUser.name,
      initial: storedUser.initial || storedUser.name.slice(0, 1),
    };
  } catch {
    return undefined;
  }
}

function App() {
  const [currentUser, setCurrentUser] = useState(getStoredAuthenticatedUser);

  const handleLogin = ({ displayName, loginId }) => {
    const name = displayName || loginId || "내 프로필";
    const authenticatedUser = {
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
    <Routes>
      <Route
        path="/"
        element={
          <MainPage currentUser={currentUser} onLogout={handleLogout} />
        }
      />
      <Route path="/roommates" element={<RoommateSearchPage />} />
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
      <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/find-id" element={<FindIdPage />} />
      <Route path="/find-password" element={<FindPasswordPage />} />
      <Route
        path="/profile"
        element={<Navigate to="/profile-survey" replace />}
      />
      <Route path="/profile-survey" element={<ProfileSurveyPage />} />
      <Route
        path="/roommate-preference-survey"
        element={<PreferenceSurveyPage />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
