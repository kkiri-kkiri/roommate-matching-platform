const { spawnSync } = require('node:child_process');
const { appendFileSync } = require('node:fs');
const path = require('node:path');
const h = require('./ccra-p0.cjs');
const root = path.resolve(__dirname, '../../..');
function db(mode, ...args) {
  if (!['snapshot', 'constraint'].includes(mode) || process.env.E2E_RETAIN_RESOURCES !== '1'
    || process.env.E2E_DATABASE !== 'kkiri_e2e_82cdabce6030' || !process.env.E2E_CCRA_DB_CLASSPATH) throw new Error('Unsafe scoped DB operation');
  const result = spawnSync('java', ['-cp', process.env.E2E_CCRA_DB_CLASSPATH, 'CcraRevalidationDatabase', mode, ...args.map(String)],
    { cwd: root, env: process.env, encoding: 'utf8', timeout: 60000, windowsHide: true });
  if (result.status !== 0) throw new Error(`Scoped ${mode} failed; no credentials are printed`);
  const line = result.stdout.split(/\r?\n/).find(line => line.startsWith('CCRA_DB '));
  if (!line) throw new Error('No scoped DB result');
  const value = JSON.parse(line.slice(8));
  appendFileSync(path.join(process.env.E2E_EVIDENCE_DIR, 'revalidation-database.jsonl'), JSON.stringify({ time: new Date().toISOString(), mode, result: value }) + '\n');
  return value;
}
function gateway(page, actor, frames, policy = {}) {
  const links = [];
  const instrumented = page.addInitScript(() => {
    const NativeSocket = window.WebSocket;
    window.__ccraActualSockets = [];
    window.__ccraActualMessages = [];
    window.WebSocket = class extends NativeSocket {
      constructor(url, ...args) {
        super(url, ...args);
        if (String(url).includes('/ws/chat')) {
          window.__ccraActualSockets.push(this);
          this.addEventListener('message', ({ data }) => {
            if (typeof data === 'string' && data.startsWith('MESSAGE')) {
              try { window.__ccraActualMessages.push(JSON.parse(data.split(/\r?\n\r?\n/).slice(1).join('\n\n').replace(/\0$/, ''))); } catch {}
            }
          });
        }
      }
    };
  });
  const parse = (data, direction) => {
    const raw = String(data), command = raw.split(/\r?\n/)[0].trim();
    const row = { actor, direction, command, at: new Date().toISOString() };
    // Never retain CONNECT headers/JWT. Only business SEND/MESSAGE bodies are projected.
    if (['SEND', 'MESSAGE'].includes(command)) {
      try { row.payload = JSON.parse(raw.split(/\r?\n\r?\n/).slice(1).join('\n\n').replace(/\0$/, '')); } catch {}
    }
    return row;
  };
  const attached = page.routeWebSocket('**/ws/chat*', ws => {
    if (policy.blockConnections) { void ws.close({ code: 1013, reason: 'CCRA isolated polling recovery' }); return; }
    const server = ws.connectToServer(); const link = { ws, server, closed: false }; links.push(link);
    ws.onMessage(data => {
      const row = parse(data, 'sent'); row.dropped = row.command === 'SEND' && Boolean(policy.dropSend); frames.push(row);
      if (!row.dropped) server.send(data);
    });
    server.onMessage(data => {
      const row = parse(data, 'received'); row.dropped = link.closed || (row.command === 'MESSAGE' && Boolean(policy.dropEcho)); frames.push(row);
      if (!row.dropped) ws.send(data);
    });
  });
  return { attached: Promise.all([attached, instrumented]), links, close: async () => {
    // Intercepted transport events can outlive the browser socket. Close and inspect native objects.
    await page.evaluate(async () => {
      await Promise.all((window.__ccraActualSockets || []).filter(socket => socket.readyState < 2).map(socket => new Promise(resolve => {
        socket.addEventListener('close', resolve, { once: true }); socket.close(4001, 'CCRA native reconnect');
        setTimeout(resolve, 3000);
      })));
    });
    for (const link of links.slice()) {
      const { ws, server } = link; link.closed = true;
      await ws.close({ code: 1012, reason: 'CCRA reconnect probe' }).catch(() => {});
      await server.close({ code: 1012, reason: 'CCRA reconnect probe' }).catch(() => {});
    }
  } };
}
const pending = (page, userId, roomId) => page.evaluate(({ userId, roomId }) =>
  JSON.parse(sessionStorage.getItem('chat.pending.v1:' + userId) || '{}')[roomId] || null, { userId, roomId });
async function rawBurst(page, roomId, requests, warmupId) {
  return page.evaluate(async ({ roomId, requests, warmupId }) => {
    const sockets = [], states = [];
    const until = async condition => {
      const end = Date.now() + 25000;
      while (!condition()) {
        if (Date.now() > end) throw new Error('STOMP concurrency barrier timeout');
        await new Promise(resolve => setTimeout(resolve, 25));
      }
    };
    const send = (socket, request) => socket.send(`SEND\ndestination:/pub/chat/rooms/${roomId}/messages\ncontent-type:application/json\n\n${JSON.stringify(request)}\0`);
    try {
      for (let i = 0; i < requests.length; i++) {
        const state = { connected: false, messages: [], error: false }; states.push(state);
        const socket = new WebSocket('ws://127.0.0.1:18080/ws/chat?ccraConcurrency=' + i, ['v12.stomp']); sockets.push(socket);
        socket.onopen = () => socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${localStorage.getItem('accessToken')}\nheart-beat:0,0\n\n\0`);
        socket.onmessage = ({ data }) => {
          if (data.startsWith('CONNECTED')) {
            state.connected = true;
            socket.send(`SUBSCRIBE\nid:ccra-${i}\ndestination:/sub/chat/rooms/${roomId}\n\n\0`);
          }
          if (data.startsWith('ERROR')) state.error = true;
          if (data.startsWith('MESSAGE')) state.messages.push(JSON.parse(data.split(/\r?\n\r?\n/).slice(1).join('\n\n').replace(/\0$/, '')));
        };
      }
      await until(() => states.every(state => state.connected));
      await new Promise(resolve => setTimeout(resolve, 250));
      send(sockets[0], { text: 'CCRA subscription barrier ' + warmupId, clientMessageId: warmupId });
      await until(() => states.every(state => state.messages.some(message => message.clientMessageId === warmupId)));
      const startedAt = Date.now();
      // All sends are queued in this same browser task over independent live sessions.
      sockets.forEach((socket, i) => send(socket, requests[i]));
      const queuedWithinMs = Date.now() - startedAt;
      const targetKeys = new Set(requests.map(request => request.clientMessageId ?? null));
      await until(() => states[0].messages.filter(message => targetKeys.has(message.clientMessageId ?? null)).length >= requests.length);
      return { connections: sockets.length, queuedWithinMs, requests,
        received: states[0].messages.filter(message => targetKeys.has(message.clientMessageId ?? null)),
        errors: states.filter(state => state.error).length };
    } finally { sockets.forEach(socket => socket.close()); }
  }, { roomId, requests, warmupId });
}
module.exports = { ...h, db, gateway, pending, rawBurst };
