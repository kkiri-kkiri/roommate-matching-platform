const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const {spawnSync}=require('node:child_process');
const {CATALOG,COUNTS,selection,verifyListing,reserve}=require('./mc-p1-session.cjs');
const {safeEnvironment,main}=require('./run.cjs');
test('P1 allowlist contains 31 independent IDs and rejects P0, malformed, duplicate or unknown IDs',()=>{
  assert.equal(CATALOG.length,31);assert.equal(new Set(CATALOG).size,31);assert.deepEqual(COUNTS,{R07:5,R08:7,C05:5,C06:4,C07:10});
  for(const bad of [[],['R01'],['C04'],['R07'],['R07-00'],['R07-06'],['C07-11'],['R07-01','R07-01'],['R07-01|R01']])assert.throws(()=>selection(bad));
  assert.ok(selection().includes('--no-deps'));assert.ok(selection().includes('--project=mc-p1'));
});
test('P1 native list must match every unique independent case exactly',()=>{
  const listing=CATALOG.map(id=>` [mc-p1] › mc-p1.spec.cjs:1:1 › ${id} P1: fixture`).join('\n')+'\nTotal: 31 tests in 1 file';
  verifyListing(listing);assert.throws(()=>verifyListing(listing.replace('R07-01 P1:','R01 P0:')));assert.throws(()=>verifyListing(listing.replace('Total: 31','Total: 32')));
});
test('P1 rejects custom selectors and retention before creating evidence or touching Docker',async()=>{
  for(const args of [['--mc-p1','--grep','R01'],['--mc-p1','--retain-resources'],['--mc-p1-extra']])await assert.rejects(main(args));
});
test('P1 reserve permits exactly one resource and never uses another domain ledger',t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'mc-p1-budget-'));
  t.after(()=>{assert.equal(path.dirname(path.resolve(dir)),path.resolve(os.tmpdir()));assert.match(path.basename(dir),/^mc-p1-budget-/);fs.rmSync(dir,{recursive:true,force:true});});
  reserve(dir,{id:'111111111111'});assert.throws(()=>reserve(dir,{id:'222222222222'}));assert.equal(JSON.parse(fs.readFileSync(path.join(dir,'resource-ledger.json'))).length,1);
});
test('P1 unsafe fixture IDs are rejected by actual Java entrypoint before any datasource',()=>{
  const classpath=fs.readFileSync(path.resolve(__dirname,'../../backend/build/e2e/classpath.txt'),'utf8');
  for(const id of ['R01','C07-11','R07-00']){
    const result=spawnSync('java',['-cp',classpath,'e2e.E2eDatabase','reset'],{env:{...safeEnvironment(process.env),E2E_MC_P1:'1',E2E_MC_P1_CASE:id},encoding:'utf8',windowsHide:true,timeout:15_000});
    assert.notEqual(result.status,0);assert.match(result.stderr,/MC P1 fixture ID/);assert.doesNotMatch(result.stderr,/Missing E2E setting: E2E_DATABASE/);
  }
});
