import { Client } from "@stomp/stompjs";
import { createChatSocket } from "./chatSocket";

jest.mock("@stomp/stompjs", () => ({ Client: jest.fn() }));
beforeEach(() => {
  Client.mockImplementation((config) => ({
    ...config, connected: false, activate: jest.fn(), deactivate: jest.fn().mockResolvedValue(),
    publish: jest.fn(), subscribe: jest.fn(() => ({ unsubscribe: jest.fn() })),
  }));
});

test("auth, room switching, reconnect, publishing and cleanup", async () => {
  const onMessage = jest.fn();
  const socket = createChatSocket({ onStatus: jest.fn(), onError: jest.fn() });
  const client = Client.mock.results[0].value;
  expect(client.brokerURL).toBe("ws://localhost:8080/ws/chat");
  localStorage.setItem("accessToken", "first");
  client.beforeConnect();
  expect(client.connectHeaders.Authorization).toBe("Bearer first");
  socket.connect();
  expect(client.activate).toHaveBeenCalledTimes(1);
  socket.subscribe("1", onMessage);
  expect(client.subscribe).not.toHaveBeenCalled();
  client.connected = true;
  client.onConnect();
  expect(client.subscribe.mock.calls[0][0]).toBe("/sub/chat/rooms/1");
  const oldCallback = client.subscribe.mock.calls[0][1];
  oldCallback({ body: '{"id":1}' });
  expect(onMessage).toHaveBeenCalledWith({ id: 1 });
  socket.publish("1", "안녕");
  expect(client.publish).toHaveBeenCalledWith({
    destination: "/pub/chat/rooms/1/messages", body: '{"text":"안녕"}',
  });
  socket.subscribe("2", onMessage);
  expect(client.subscribe.mock.results[0].value.unsubscribe).toHaveBeenCalledTimes(1);
  oldCallback({ body: '{"id":2}' });
  expect(onMessage).toHaveBeenCalledTimes(1);
  client.connected = false;
  client.onWebSocketClose();
  expect(() => socket.publish("2", "실패")).toThrow("연결");
  localStorage.setItem("accessToken", "second");
  client.beforeConnect();
  expect(client.connectHeaders.Authorization).toBe("Bearer second");
  client.connected = true;
  client.onConnect();
  expect(client.subscribe.mock.calls[2][0]).toBe("/sub/chat/rooms/2");
  await socket.disconnect();
  expect(client.subscribe.mock.results[2].value.unsubscribe).toHaveBeenCalledTimes(1);
  expect(client.deactivate).toHaveBeenCalledTimes(1);
});
