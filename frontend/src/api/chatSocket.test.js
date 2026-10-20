import { Client } from "@stomp/stompjs";
import { getAccessToken } from "../auth/session";
import { createChatSocket } from "./chatSocket";

jest.mock("@stomp/stompjs", () => ({ Client: jest.fn() }));
jest.mock("../auth/session", () => ({ getAccessToken: jest.fn() }));
let client, options, subscriptions;
beforeEach(() => {
  jest.resetAllMocks(); subscriptions = [];
  client = { connected: false, activate: jest.fn(), deactivate: jest.fn().mockResolvedValue(), publish: jest.fn(),
    subscribe: jest.fn((destination, callback) => {
      const value = { destination, callback, unsubscribe: jest.fn() }; subscriptions.push(value); return value;
    }) };
  Client.mockImplementation((value) => { options = value; return client; });
  getAccessToken.mockReturnValue("test-token");
});

test("authenticates and reattaches only the selected subscription on reconnect", () => {
  const onStatus = jest.fn(), onConnect = jest.fn(), onMessage = jest.fn();
  const socket = createChatSocket({ onStatus, onConnect, onError: jest.fn() });
  socket.connect(); socket.subscribe("10", onMessage);
  options.beforeConnect();
  expect(client.connectHeaders).toEqual({ Authorization: "Bearer test-token" });
  client.connected = true; options.onConnect();
  expect(subscriptions[0].destination).toBe("/sub/chat/rooms/10");
  client.connected = false; options.onWebSocketClose();
  client.connected = true; options.onConnect();
  expect(client.subscribe).toHaveBeenCalledTimes(2);
  expect(onConnect).toHaveBeenCalledTimes(2);
  expect(onStatus).toHaveBeenCalledWith(false);
});

test("old callbacks and stale cleanup cannot affect the new room or a stopped session", async () => {
  client.connected = true;
  const first = jest.fn(), second = jest.fn();
  const socket = createChatSocket({ onStatus: jest.fn(), onError: jest.fn() });
  const detach = socket.subscribe("10", first);
  socket.subscribe("20", second); detach();
  subscriptions[0].callback({ body: '{"id":1}' });
  subscriptions[1].callback({ body: '{"id":2}' });
  expect(first).not.toHaveBeenCalled(); expect(second).toHaveBeenCalledWith({ id: 2 });
  expect(subscriptions[1].unsubscribe).not.toHaveBeenCalled();
  await socket.disconnect();
  subscriptions[1].callback({ body: '{"id":3}' });
  options.onConnect();
  expect(second).toHaveBeenCalledTimes(1);
  expect(client.subscribe).toHaveBeenCalledTimes(2);
  expect(subscriptions[1].unsubscribe).toHaveBeenCalledTimes(1);
});
