const { test } = require('node:test');
const assert = require('node:assert/strict');
const { CATALOG, selection, verifyListing, verifyStorage, recheckSource, reserveResource } = require('./mc-session.cjs');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { createHash } = require('node:crypto');
const { sanitize } = require('./run.cjs');
test('MC catalog accepts exactly the eleven approved P0 IDs and rejects P1 and injected selectors', () => {
  assert.equal(CATALOG.length,11);
  for (const ids of [[],['R07'],['C05'],['C04','C04'],['C04|R07'],['B1']]) assert.throws(() => selection(ids));
  const list = CATALOG.map(id => `  [mc-p0] › mc-p0.spec.cjs:1:1 › ${id} P0: description`).join('\n') + '\nTotal: 11 tests in 1 file';
  verifyListing(list);
  assert.throws(() => verifyListing(list.replace('R01 P0:', 'R07 P0:')));
  assert.throws(() => verifyListing(list.replace('Total: 11', 'Total: 12')));
});
test('existing credential redaction applies to nested MC evidence', () => {
  assert.deepEqual(sanitize({ message:'Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature', password:'E2e!'+'a'.repeat(36) }),
    { message:'Bearer [REDACTED TOKEN]', password:'[REDACTED]' });
});
test('MC storage guard checks Docker tmpfs settings without requiring tmpfs in Mounts', () => {
  const storage = { Mounts:[], HostConfig:{Tmpfs:{'/var/lib/mysql':'rw,size=1073741824'}} };
  assert.deepEqual(verifyStorage(storage).mounts, []);
  assert.throws(() => verifyStorage({...storage,Mounts:[{Type:'volume',Destination:'/var/lib/mysql'}]}));
  assert.throws(() => verifyStorage({Mounts:[],HostConfig:{}}));
});

function originalFixture(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mc-recheck-safety-'));
  t.after(() => {
    assert.equal(path.dirname(path.resolve(root)), path.resolve(os.tmpdir()));
    assert.match(path.basename(root), /^mc-recheck-safety-/);
    fs.rmSync(root, { recursive: true, force: true });
  });
  const source = '2f55ee3d8226', dir = path.join(root, source);
  fs.mkdirSync(dir);
  const failed = new Set(['C04','R02','R03','R04','C02']);
  const result = { tests: CATALOG.map(id => ({ name: id + ' P0: fixture', attempts: [{ status: failed.has(id) ? 'failed' : 'passed' }] })) };
  const bytes = JSON.stringify(result), resultFile = path.join(dir, 'results-original.json');
  fs.writeFileSync(resultFile, bytes);
  const run = { scope:'MATCHING_CALENDAR_P0_ONLY', containerRemoved:true, ownedProcessesStopped:true, fixtureTablesEmpty:true,
    mcPhases:[{label:'original',sha256:createHash('sha256').update(bytes).digest('hex')}] };
  const save = () => fs.writeFileSync(path.join(dir, 'run.json'), JSON.stringify(run));
  save();
  return { root, source, dir, resultFile, run, save };
}

test('MC recheck selects only the five original failures after cleanup and hash verification', t => {
  const fixture = originalFixture(t);
  const source = recheckSource(fixture.root, fixture.source);
  assert.deepEqual(source.ids, ['C04','R02','R03','R04','C02']);
  const listing = source.ids.map(id => `  [mc-p0] › mc-p0.spec.cjs:1:1 › ${id} P0: fixture`).join('\n') + '\nTotal: 5 tests in 1 file';
  verifyListing(listing, source.ids);
  assert.throws(() => verifyListing(listing.replace('R02 P0:', 'R01 P0:'), source.ids));
  assert.throws(() => recheckSource(fixture.root, '../source'));
});

test('MC recheck refuses incomplete cleanup, recheck chaining, and changed native evidence', t => {
  const fixture = originalFixture(t);
  for (const flag of ['containerRemoved','ownedProcessesStopped','fixtureTablesEmpty']) {
    fixture.run[flag] = false; fixture.save();
    assert.throws(() => recheckSource(fixture.root, fixture.source));
    fixture.run[flag] = true;
  }
  fixture.run.recheck = { source:fixture.source }; fixture.save();
  assert.throws(() => recheckSource(fixture.root, fixture.source));
  delete fixture.run.recheck; fixture.save();
  fs.appendFileSync(fixture.resultFile, ' ');
  assert.throws(() => recheckSource(fixture.root, fixture.source), /hash mismatch/);
});

test('MC recheck resource reservation permits only one new container in its separate ledger', t => {
  const fixture = originalFixture(t), ledger = path.join(fixture.root, 'recheck-ledger');
  reserveResource(ledger, fixture.root, { id:'111111111111' });
  assert.throws(() => reserveResource(ledger, fixture.root, { id:'222222222222' }), /budget is exhausted/);
  assert.equal(JSON.parse(fs.readFileSync(path.join(ledger, 'resource-ledger.json'))).length, 1);
});
