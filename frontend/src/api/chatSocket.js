import { Client } from "@stomp/stompjs";

function socketUrl() {
  const url = new URL(process.env.REACT_APP_API_BASE_URL || "http://localhost:8080/api", window.location.origin);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = `${url.pathname.replace(/\/api\/?$/, "").replace(/\/$/, "")}/ws/chat`;
  url.search = "";
  url.hash = "";
  return url.toString();
}

// Keep only the selected room; recreate its subscription after every reconnect.
export function createChatSocket({ onConnect, onStatus, onError }) {
  let selected;
  let subscription;
  let stopped = false;
  const attach = () => {
    if (!client.connected || !selected) return;
    const target = selected;
    subscription = client.subscribe(`/sub/chat/rooms/${target.roomId}`, (frame) => {
      if (selected !== target || stopped) return;
      try { target.onMessage(JSON.parse(frame.body)); }
      catch { onError("채팅 메시지를 처리하지 못했습니다."); }
    });
  };
  const client = new Client({
    brokerURL: socketUrl(), reconnectDelay: 5000, connectionTimeout: 10000,
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${window.localStorage.getItem("accessToken") || ""}` };
    },
    onConnect: () => {
      if (stopped) return;
      attach();
      onStatus(true);
      onConnect?.();
    },
    onWebSocketClose: () => {
      subscription = undefined;
      if (!stopped) onStatus(false);
    },
    onWebSocketError: () => { if (!stopped) onError("채팅 서버에 연결하지 못했습니다. 다시 연결하는 중입니다."); },
    onStompError: (frame) => {
      if (!stopped) onError(frame.headers.message || "채팅 요청을 처리하지 못했습니다.");
    },
  });
  const unsubscribe = () => {
    selected = undefined;
    if (client.connected) subscription?.unsubscribe();
    subscription = undefined;
  };
  return {
    connect: () => client.activate(),
    subscribe: (roomId, onMessage) => {
      unsubscribe();
      selected = { roomId, onMessage };
      attach();
      return unsubscribe;
    },
    unsubscribe,
    publish: (roomId, text) => {
      if (!client.connected) throw new Error("채팅 서버 연결 후 다시 보내주세요.");
      client.publish({ destination: `/pub/chat/rooms/${roomId}/messages`, body: JSON.stringify({ text }) });
    },
    disconnect: () => {
      stopped = true;
      unsubscribe();
      return client.deactivate();
    },
  };
}
