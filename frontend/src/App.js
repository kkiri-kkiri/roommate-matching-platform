import { Navigate, Route, Routes } from "react-router-dom";

import MainPage from "./pages/MainPage";
import RoommatePreferenceSurvey from "./pages/RoommatePreferenceSurvey";
import RoommateSearchPage from "./pages/RoommateSearchPage";
import SurveyPage from "./pages/ProfileSurveyPage";

function App() {
  return (
    <Routes>
      {/* 메인페이지 */}
      <Route path="/" element={<MainPage />} />

      {/* 내 프로필 설문 페이지 */}
      <Route path="/profile-survey" element={<SurveyPage />} />

      {/* 내가 원하는 룸메이트 설문 페이지 */}
      <Route
        path="/roommate-preference-survey"
        element={<RoommatePreferenceSurvey />}
      />

      {/* 룸메찾기 페이지 */}
      <Route path="/roommates" element={<RoommateSearchPage />} />

      {/* 존재하지 않는 주소로 접근했을 경우 메인페이지로 이동 */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
