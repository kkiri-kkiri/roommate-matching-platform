import { Outlet } from "react-router-dom";
import styled from "styled-components";

import Header from "../components/common/Header/Header";
import GlobalStyle from "../styles/GlobalStyle";

const PageShell = styled.div`
  min-height: 100vh;
  color: #1a1c1c;
  background: #faf9f6;
  font-family:
    "Plus Jakarta Sans",
    "Pretendard",
    -apple-system,
    BlinkMacSystemFont,
    "Segoe UI",
    sans-serif;
`;

function MainLayout({ currentUser, onLogout }) {
  return (
    <>
      <GlobalStyle />
      <PageShell>
        <Header currentUser={currentUser} onLogout={onLogout} />
        <Outlet />
      </PageShell>
    </>
  );
}

export default MainLayout;
