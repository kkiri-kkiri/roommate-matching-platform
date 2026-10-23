const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

// Read only the explicitly named current run, never enumerate previous runs.
const id = process.argv[2];
if (!/^[a-f0-9]{12}$/.test(id || '')) throw new Error('Expected owned run ID');
const root = path.resolve(__dirname, '../../..');
const directory = path.join(root, 'backend/build/full-service-e2e', id);
const json = name => JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8'));
const run = json('run.json');
if (run.id !== id || run.database !== `kkiri_e2e_${run.parentRunId || id}` || run.retention?.enabled !== true) throw new Error('Run identity/retention mismatch');
const files = fs.readdirSync(directory);
const browsers = files.filter(f => /^browser-.*\.json$/.test(f)).map(json);
const snapshots = files.filter(f => /^database-.*\.jsonl$/.test(f)).flatMap(file =>
  fs.readFileSync(path.join(directory, file), 'utf8').trim().split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line)));
const results = files.includes('results.json') ? json('results.json') : { counts: { executed: 0 }, tests: [] };
const requests = browsers.flatMap(b => b.requests), commands = browsers.flatMap(b => b.sockets.filter(s => s.url.includes('/ws/chat')).flatMap(s =>
  [...s.sent.map(command => ({ direction: 'sent', command })), ...s.received.map(message => ({ direction: 'received', command: message.command }))]));
const count = (rows, key) => Object.fromEntries([...new Set(rows.map(key))].sort().map(value => [value, rows.filter(row => key(row) === value).length]));
const injectedResponses = browsers.filter(b => b.test.startsWith('P0-02')).flatMap(b => b.requests).filter(r => r.status === 503).length;
const summary = {
  runId: id, database: run.database, gitSha: run.gitSha, startedAt: run.startedAt, finishedAt: run.finishedAt,
  retention: run.retention, containerRemoved: run.containerRemoved, processesStopped: run.ownedProcessesStopped,
  sourceUnchangedDuringRun: run.sourceUnchangedDuringRun, sourceManifestSha256: createHash('sha256').update(fs.readFileSync(path.join(directory, 'source-before.json'))).digest('hex'),
  counts: results.counts, httpResponses: requests.length, httpByStatus: count(requests, r => r.status),
  injectedHttpResponses: injectedResponses, backendHttpResponses: requests.length - injectedResponses,
  httpByMethod: count(requests, r => r.method), stompByDirectionAndCommand: count(commands, c => `${c.direction}:${c.command}`),
  databaseSnapshots: snapshots.filter(s => s.operation === 'snapshot').length,
  cases: results.tests.map(t => ({ name: t.name, attempts: t.attempts.map(a => ({ status: a.status, durationMs: a.durationMs, errors: a.errors.map(e => e.message) })),
    httpResponses: browsers.find(b => b.test === t.name)?.requests.length || 0,
    observations: browsers.find(b => b.test === t.name)?.observations || [] })),
  limitations: ['HTTP response count includes login/fixture/background requests, excludes failed network requests and WebSocket handshakes.',
    'STOMP instrumentation from routed sockets is recorded separately in case observations; it may overlap browser socket events.',
    'Real deployment WSS/proxy/durable volume and P1 are not tested.'],
};
fs.writeFileSync(path.join(directory, 'ccra-p0-summary.json'), JSON.stringify(summary, null, 2));
console.log(JSON.stringify({ runId: id, counts: summary.counts, httpResponses: summary.httpResponses,
  httpByStatus: summary.httpByStatus, databaseSnapshots: summary.databaseSnapshots,
  cases: summary.cases.map(c => ({ name: c.name, statuses: c.attempts.map(a => a.status) })) }, null, 2));
