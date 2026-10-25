const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const CATALOG = ['R01','R02','R03','R04','R05','R06','C01','C02','C03','C04','X01'];
function verifyStorage(inspect) {
  // Docker records --tmpfs under HostConfig.Tmpfs; .Mounts can omit tmpfs.
  const mounts = inspect.Mounts;
  const tmpfs = inspect.HostConfig?.Tmpfs;
  if (!Array.isArray(mounts) || mounts.some(m => m.Type === 'volume') ||
      typeof tmpfs?.['/var/lib/mysql'] !== 'string' || !tmpfs['/var/lib/mysql'].includes('size=1073741824'))
    throw new Error('MC requires the approved tmpfs and zero volumes');
  return { mounts: mounts.map(({ Type, Destination }) => ({ Type, Destination })), tmpfs };
}
function selection(ids) {
  if (!Array.isArray(ids) || !ids.length || new Set(ids).size !== ids.length || ids.some(id => !CATALOG.includes(id)))
    throw new Error('Only the eleven approved MC P0 IDs may run');
  return ['mc-p0.spec.cjs', '--project=mc-p0', '--no-deps', '--grep', '(' + ids.join('|') + ') P0:'];
}
function verifyListing(listing, ids = CATALOG) {
  const selected = [...listing.matchAll(/› (R\d\d|C\d\d|X01) P0:/g)].map(m => m[1]);
  if (Number(listing.match(/Total: (\d+) tests? in/)?.[1]) !== ids.length ||
      selected.length !== ids.length || selected.some(id => !ids.includes(id)) || new Set(selected).size !== ids.length)
    throw new Error('MC P0 selection does not match the approved catalog');
}
function reserveResource(directory, root, entry) {
  const ledgerFile = path.join(directory, 'resource-ledger.json');
  const ledger = fs.existsSync(ledgerFile) ? JSON.parse(fs.readFileSync(ledgerFile, 'utf8')) : [];
  const approvalFile = path.join(directory, 'additional-resource-approval.json');
  let limit = 1;
  if (fs.existsSync(approvalFile)) {
    const approval = JSON.parse(fs.readFileSync(approvalFile, 'utf8'));
    if (approval.scope !== 'MATCHING_CALENDAR_P0_ONLY' || approval.maxTotalContainers !== 2 ||
        approval.previousRun !== '0dd4e8c47708') throw new Error('Invalid supplemental MC resource approval');
    const prior = JSON.parse(fs.readFileSync(path.join(root, approval.previousRun, 'run.json'), 'utf8'));
    if (!prior.containerRemoved || !prior.ownedProcessesStopped || prior.scope !== approval.scope)
      throw new Error('Prior MC resource cleanup is not confirmed');
    limit = 2;
  }
  if (ledger.length >= limit) throw new Error('The approved MC P0 container budget is exhausted');
  fs.mkdirSync(directory, { recursive: true });
  fs.writeFileSync(ledgerFile, JSON.stringify([...ledger, entry], null, 2), { flag: 'w' });
}
async function session({ env, start, complete, forward, failureLog, run }) {
  const directory = env.E2E_EVIDENCE_DIR;
  const statusFile = path.join(directory, 'mc-session.json');
  const commandFile = path.join(directory, 'mc-commands.jsonl');
  const phases = [];
  const completed = new Set();
  const save = state => fs.writeFileSync(statusFile, JSON.stringify({ state, phases, completed: [...completed] }, null, 2));
  let command = { ids: ['C04'], label: 'c04-original' }, consumed = 0;
  while (command) {
    const args = selection(command.ids);
    if (!/^[a-z0-9-]{1,50}$/.test(command.label) || phases.some(p => p.label === command.label)) throw new Error('Unsafe or duplicate MC phase');
    const phaseEnv = { E2E_MC_PHASE: command.label, E2E_CONTROL_URL: env.E2E_CONTROL_URL,
      E2E_CONTROL_KEY: env.E2E_CONTROL_KEY, PLAYWRIGHT_NO_COPY_PROMPT: '1' };
    const listing = await complete(start(process.execPath, [require.resolve('@playwright/test/cli'), 'test', ...args, '--list', '--reporter=list'], undefined, phaseEnv), 'List MC phase');
    verifyListing(listing, command.ids);
    fs.writeFileSync(path.join(directory, `selection-${command.label}.txt`), listing);
    save('RUNNING');
    const child = start(process.execPath, [require.resolve('@playwright/test/cli'), 'test', ...args], undefined, phaseEnv);
    forward(child.stdout, text => process.stdout.write(text)); forward(child.stderr, text => process.stderr.write(text));
    let exit = 0;
    try { await complete(child, 'MC Playwright ' + command.label, 1_200_000); } catch { exit = child.exitCode ?? -1; }
    failureLog(child, 'MC-' + command.label);
    const resultFile = path.join(directory, `results-${command.label}.json`);
    if (!fs.existsSync(resultFile)) throw new Error('MC phase lost its native final result');
    const bytes = fs.readFileSync(resultFile), results = JSON.parse(bytes);
    phases.push({ label: command.label, ids: command.ids, exit, sha256: createHash('sha256').update(bytes).digest('hex') });
    for (const test of results.tests) if (test.attempts.some(a => a.status === 'passed')) completed.add(test.name.split(' ')[0]);
    run.mcPhases = phases;
    save('AWAITING_COMMAND');
    console.log(`[mc] ${command.label} completed; awaiting scoped command in ${commandFile}`);
    const deadline = Date.now() + 3_600_000;
    command = null;
    while (!command) {
      const lines = fs.existsSync(commandFile) ? fs.readFileSync(commandFile,'utf8').trim().split(/\r?\n/).filter(Boolean) : [];
      if (lines.length > consumed) { command = JSON.parse(lines[consumed++]); break; }
      if (Date.now() > deadline) throw new Error('MC session command timeout; no new resources will be created');
      await new Promise(resolve => setTimeout(resolve, 500));
    }
    if (command.finish === true) { save('FINISHED'); break; }
    selection(command.ids);
  }
  run.playwrightExitCode = CATALOG.every(id => completed.has(id)) ? 0 : 1;
}
module.exports = { CATALOG, selection, verifyListing, verifyStorage, reserveResource, session };
