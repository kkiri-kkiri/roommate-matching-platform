import { act, renderHook } from "@testing-library/react";
import { useChatSync } from "./useChatSync";

let options;
beforeEach(() => {
  jest.useFakeTimers();
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" });
  options = { isAuthenticated: true, connectionVersion: 0, broadcastVersion: 0,
    refreshRooms: jest.fn().mockResolvedValue(), syncMessages: jest.fn().mockResolvedValue(false),
    syncReadStatuses: jest.fn().mockResolvedValue() };
});
afterEach(() => { jest.useRealTimers(); });
const advance = async (ms) => { await act(async () => { jest.advanceTimersByTime(ms); }); };

test("polls one bounded REST page and selected receipts while visible, stops on logout", async () => {
  const removeListener = jest.spyOn(window, "removeEventListener");
  const { rerender, unmount } = renderHook(useChatSync, { initialProps: options });
  await advance(15000);
  expect(options.refreshRooms).toHaveBeenCalledWith({ background: true });
  expect(options.syncMessages).toHaveBeenCalledTimes(1);
  expect(options.syncReadStatuses).toHaveBeenCalledTimes(1);
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "hidden" });
  await advance(30000);
  expect(options.refreshRooms).toHaveBeenCalledTimes(1);
  rerender({ ...options, isAuthenticated: false });
  Object.defineProperty(document, "visibilityState", { configurable: true, value: "visible" });
  window.dispatchEvent(new Event("focus"));
  await advance(30000);
  expect(options.refreshRooms).toHaveBeenCalledTimes(1);
  unmount();
  expect(removeListener).toHaveBeenCalledWith("focus", expect.any(Function));
  await advance(30000);
  expect(options.refreshRooms).toHaveBeenCalledTimes(1);
  removeListener.mockRestore();
});

test("coalesces duplicate notifications/reconnect/focus and serializes reversed arrivals", async () => {
  let finish;
  options.syncMessages.mockImplementationOnce(() => new Promise((resolve) => { finish = resolve; }));
  const { rerender } = renderHook(useChatSync, { initialProps: options });
  rerender({ ...options, connectionVersion: 1, broadcastVersion: 1 });
  rerender({ ...options, connectionVersion: 1, broadcastVersion: 2 });
  window.dispatchEvent(new Event("focus"));
  await advance(100);
  expect(options.syncMessages).toHaveBeenCalledTimes(1);
  rerender({ ...options, connectionVersion: 2, broadcastVersion: 3 });
  await advance(100);
  expect(options.syncMessages).toHaveBeenCalledTimes(1);
  await act(async () => finish(false));
  await advance(100);
  expect(options.syncMessages).toHaveBeenCalledTimes(2);
});

test("returns from hidden tabs and catches up a long gap in bounded batches", async () => {
  options.syncMessages.mockResolvedValueOnce(true).mockResolvedValue(false);
  renderHook(useChatSync, { initialProps: options });
  document.dispatchEvent(new Event("visibilitychange"));
  await advance(100);
  await advance(1000);
  expect(options.syncMessages).toHaveBeenCalledTimes(2);
  await advance(1000);
  expect(options.syncMessages).toHaveBeenCalledTimes(2);
});
