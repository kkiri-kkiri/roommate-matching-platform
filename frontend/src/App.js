import { Navigate, Route, Routes } from "react-router";

import MainPage from "./pages/MainPage";
import SurveyPage from "./pages/SurveyPage";

function App() {
  return (
    <Routes>
      <Route path="/" element={<MainPage />} />
      <Route path="/roommates" element={<MainPage pageMode="roommates" />} />
      <Route path="/community" element={<MainPage pageMode="community" />} />
      <Route path="/chat" element={<MainPage pageMode="chat" />} />
      <Route path="/calendar" element={<MainPage pageMode="calendar" />} />
      <Route path="/login" element={<MainPage pageMode="login" />} />
      <Route path="/profile" element={<MainPage pageMode="profile" />} />
      <Route path="/signup" element={<SurveyPage />} />
      <Route path="/signup/survey" element={<SurveyPage />} />
      <Route path="/survey" element={<SurveyPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
