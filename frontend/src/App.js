import { useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import FindIdPage from "./pages/FindIdPage";
import FindPasswordPage from "./pages/FindPasswordPage";
import LoginPage from "./pages/LoginPage";
import MainPage from "./pages/MainPage";
import PreferenceSurveyPage from "./pages/PreferenceSurveyPage";
import ProfileSurveyPage from "./pages/ProfileSurveyPage";
import SignupPage from "./pages/SignupPage";

function App() {
  const [currentUser, setCurrentUser] = useState();

  const handleLogin = ({ displayName, loginId }) => {
    const name = displayName || loginId || "내 프로필";

    setCurrentUser({
      name,
      initial: name.slice(0, 1),
    });
  };

  return (
    <Routes>
      <Route path="/" element={<MainPage currentUser={currentUser} />} />
      <Route
        path="/roommates"
        element={<MainPage pageMode="roommates" currentUser={currentUser} />}
      />
      <Route
        path="/community"
        element={<MainPage pageMode="community" currentUser={currentUser} />}
      />
      <Route
        path="/chat"
        element={<MainPage pageMode="chat" currentUser={currentUser} />}
      />
      <Route
        path="/calendar"
        element={<MainPage pageMode="calendar" currentUser={currentUser} />}
      />
      <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/find-id" element={<FindIdPage />} />
      <Route path="/find-password" element={<FindPasswordPage />} />
      <Route path="/profile" element={<Navigate to="/profile-survey" replace />} />
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
