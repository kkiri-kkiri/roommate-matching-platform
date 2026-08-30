import { Navigate, Route, Routes } from "react-router";

import MainPage from "./pages/MainPage";

function App() {
  return (
    <Routes>
      <Route path="/" element={<MainPage />} />
      <Route path="/roommates" element={<MainPage pageMode="roommates" />} />
      <Route path="/community" element={<MainPage pageMode="community" />} />
      <Route path="/chat" element={<MainPage />} />
      <Route path="/login" element={<MainPage />} />
      <Route path="/signup" element={<MainPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
