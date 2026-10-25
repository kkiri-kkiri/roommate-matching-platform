const { test } = require('node:test');
const assert = require('node:assert/strict');
const { CATALOG, selection, verifyListing, verifyStorage } = require('./mc-session.cjs');
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
