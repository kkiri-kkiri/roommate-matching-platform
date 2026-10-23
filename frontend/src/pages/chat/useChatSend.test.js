import { act, renderHook } from "@testing-library/react";
import { SEND_CONFIRMATION_TIMEOUT, useChatSend } from "./useChatSend";

let socketRef, setStatus, onConfirmed;
beforeEach(() => {
  Object.defineProperty(window, "crypto", { configurable: true, value: require("crypto").webcrypto });
  sessionStorage.clear();
  jest.useFakeTimers();
  socketRef = { current: { publish: jest.fn() } };
  setStatus = jest.fn(); onConfirmed = jest.fn();
});
afterEach(() => { jest.useRealTimers(); sessionStorage.clear(); });
function harness(props = {}) {
  return useChatSend({ accountId: 7, rooms: [], socketRef, connected: true, setStatus, onConfirmed, ...props });
}
const roomWith = (pending, extra = {}) => [{ id: "1", messages: [{ id: 31, sender: "me",
  text: pending.text, clientMessageId: pending.clientMessageId, ...extra }] }];

test("publish alone and repeated clicks never clear pending or create another request", () => {
  const { result } = renderHook(harness);
  act(() => { result.current.send("1", "초안"); result.current.send("1", "초안"); });
  expect(setStatus).not.toHaveBeenCalled();
  expect(socketRef.current.publish).toHaveBeenCalledTimes(1);
  expect(result.current.pendingSends[1].text).toBe("초안");
  expect(onConfirmed).not.toHaveBeenCalled();
  expect(JSON.parse(sessionStorage.getItem("chat.pending.v1:7"))[1].text).toBe("초안");
});

test("timeout and repeated lost confirmations reuse the same durable key and body", () => {
  const { result } = renderHook(harness);
  act(() => result.current.send("1", "초안"));
  const id = result.current.pendingSends[1].clientMessageId;
  for (let attempt = 0; attempt < 2; attempt++) {
    act(() => jest.advanceTimersByTime(SEND_CONFIRMATION_TIMEOUT));
    expect(result.current.pendingSends[1].state).toBe("unknown");
    act(() => result.current.send("1", "다른 내용으로 바꾸지 않는다"));
    expect(socketRef.current.publish).toHaveBeenLastCalledWith("1", "초안", id);
  }
  expect(onConfirmed).not.toHaveBeenCalled();
});

test("only an own, same-room, same-key committed message confirms, regardless of REST or STOMP origin", () => {
  const { result, rerender } = renderHook(harness);
  act(() => result.current.send("1", "같은 본문"));
  const pending = result.current.pendingSends[1];
  rerender({ rooms: roomWith(pending, { clientMessageId: "another-key" }) });
  rerender({ rooms: roomWith(pending, { sender: "other" }) });
  rerender({ rooms: [{ ...roomWith(pending)[0], id: "2" }] });
  expect(onConfirmed).not.toHaveBeenCalled();
  rerender({ rooms: roomWith(pending) });
  expect(result.current.pendingSends[1]).toBeUndefined();
  expect(onConfirmed).toHaveBeenCalledWith("1", "같은 본문");
  rerender({ rooms: roomWith(pending) });
  expect(onConfirmed).toHaveBeenCalledTimes(1);
});

test("send exception, disconnect and reload preserve a recoverable draft and retry ID", () => {
  socketRef.current.publish.mockImplementationOnce(() => { throw new Error("전송 실패"); });
  const first = renderHook(harness);
  act(() => first.result.current.send("1", "복구"));
  const pending = first.result.current.pendingSends[1];
  expect(pending.state).toBe("unknown");
  expect(setStatus).toHaveBeenCalledWith("전송 실패");
  first.unmount();
  const second = renderHook(harness, { initialProps: { connected: false } });
  expect(second.result.current.pendingSends[1].text).toBe("복구");
  act(() => second.result.current.send("1", "복구"));
  expect(socketRef.current.publish).toHaveBeenCalledTimes(1);
  second.rerender({ connected: true });
  act(() => second.result.current.send("1", "복구"));
  expect(socketRef.current.publish).toHaveBeenLastCalledWith("1", "복구", pending.clientMessageId);
});

test("a different account never loads another account's pending content", () => {
  const first = renderHook(harness);
  act(() => first.result.current.send("1", "비공개"));
  first.unmount();
  const second = renderHook(harness, { initialProps: { accountId: 8 } });
  expect(second.result.current.pendingSends).toEqual({});
});

test("unavailable durable storage blocks publish and leaves the editable draft untouched", () => {
  const spy = jest.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("storage full"); });
  const { result } = renderHook(harness);
  act(() => expect(result.current.send("1", "초안")).toBe(false));
  expect(socketRef.current.publish).not.toHaveBeenCalled();
  expect(result.current.pendingSends).toEqual({});
  expect(onConfirmed).not.toHaveBeenCalled();
  spy.mockRestore();
});
