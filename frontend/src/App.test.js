import { TextDecoder, TextEncoder } from "util";
import { fireEvent, render, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { login } from "./api/authApi";

global.TextEncoder = TextEncoder;
global.TextDecoder = TextDecoder;
const { MemoryRouter, useLocation } = require("react-router-dom");
const App = require("./App").default;

jest.mock("./api/authApi", () => ({ login: jest.fn() }));
jest.mock("./layouts/MainLayout", () => ({ onLogout }) => {
  const { Outlet } = require("react-router-dom");
  return <><button onClick={onLogout}>Log out</button><Outlet /></>;
});
jest.mock("./calendar/CalendarDataContext", () => ({ CalendarDataProvider: ({ children }) => children }));
jest.mock("./pages/admin/AdminPage", () => () => <div>Admin screen</div>);
jest.mock("./pages/main/MainPage", () => () => <div>Main screen</div>);
jest.mock("./pages/mypage/MyPage", () => ({ onUserUpdated }) => {
  const { Link } = require("react-router-dom");
  return <><button onClick={() => onUserUpdated({ id: 7, nickname: "Updated", loginId: "account" })}>Update profile</button><Link to="/admin">Admin link</Link></>;
});
jest.mock("./components/common/ProfileCard/DetailedProfile", () => () => null);
jest.mock("./pages/chat/ChatPage", () => () => null);
jest.mock("./calendar/CalendarPage", () => () => null);
jest.mock("./pages/community/CommunityPage", () => () => null);
jest.mock("./pages/roommate/RoommateSearchPage", () => () => null);
jest.mock("./pages/auth/SignupPage", () => () => null);
jest.mock("./pages/auth/FindIdPage", () => () => null);
jest.mock("./pages/auth/FindPasswordPage", () => () => null);

function Location() {
  return <output data-testid="location">{useLocation().pathname}</output>;
}

function open(path) {
  return render(<MemoryRouter initialEntries={[path]}><App /><Location /></MemoryRouter>);
}

function storeUser(role) {
  window.localStorage.setItem("accessToken", "server-token");
  window.localStorage.setItem("roomieAuthenticatedUser", JSON.stringify({ id: 7, name: "Member", initial: "M", role }));
}

beforeEach(() => {
  window.localStorage.clear();
  jest.clearAllMocks();
});

test.each([["ADMIN", "/admin", "Admin screen"], ["USER", "/", "Main screen"]])(
  "%s login follows the server role and survives remount",
  async (role, path, screenName) => {
    login.mockResolvedValue({ accessToken: "server-token", user: { id: 7, loginId: "same-account", nickname: "Member", role } });
    const view = open("/login");
    fireEvent.change(screen.getByLabelText("아이디"), { target: { value: "same-account" } });
    fireEvent.change(screen.getByLabelText("비밀번호"), { target: { value: "password" } });
    fireEvent.click(screen.getByRole("button", { name: "로그인", exact: true }));
    await screen.findByText(screenName);
    expect(screen.getByTestId("location")).toHaveTextContent(path);
    expect(login).toHaveBeenCalledWith("same-account", "password");
    expect(window.localStorage.getItem("accessToken")).toBe("server-token");
    expect(JSON.parse(window.localStorage.getItem("roomieAuthenticatedUser")).role).toBe(role);
    view.unmount();
    open(path);
    expect(screen.getByText(screenName)).toBeInTheDocument();
    expect(login).toHaveBeenCalledTimes(1);
  },
);

test.each(["USER", undefined, "UNKNOWN"])("blocks direct admin access for role %s", (role) => {
  storeUser(role);
  open("/admin");
  expect(screen.getByText("Main screen")).toBeInTheDocument();
  expect(screen.queryByText("Admin screen")).not.toBeInTheDocument();
  expect(screen.getByTestId("location").textContent).toBe("/");
});

test.each([false, true])("redirects visitors without a token to login (cached admin: %s)", (cachedAdmin) => {
  if (cachedAdmin) storeUser("ADMIN");
  window.localStorage.removeItem("accessToken");
  open("/admin");
  expect(screen.getByTestId("location")).toHaveTextContent("/login");
  expect(screen.queryByText("Admin screen")).not.toBeInTheDocument();
});

test("profile updates preserve the admin role in memory and storage", () => {
  storeUser("ADMIN");
  open("/profile");
  fireEvent.click(screen.getByText("Update profile"));
  expect(JSON.parse(window.localStorage.getItem("roomieAuthenticatedUser"))).toEqual(expect.objectContaining({ name: "Updated", role: "ADMIN" }));
  fireEvent.click(screen.getByText("Admin link"));
  expect(screen.getByText("Admin screen")).toBeInTheDocument();
});

test("logout removes admin access and the stored session", () => {
  storeUser("ADMIN");
  open("/admin");
  fireEvent.click(screen.getByText("Log out"));
  expect(screen.getByTestId("location")).toHaveTextContent("/login");
  expect(screen.queryByText("Admin screen")).not.toBeInTheDocument();
  expect(window.localStorage.getItem("accessToken")).toBeNull();
  expect(window.localStorage.getItem("roomieAuthenticatedUser")).toBeNull();
});
