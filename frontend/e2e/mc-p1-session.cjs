const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const COUNTS = { R07:5, R08:7, C05:5, C06:4, C07:10 };
const CATALOG = Object.entries(COUNTS).flatMap(([group,n]) => Array.from({length:n},(_,i) => `${group}-${String(i+1).padStart(2,'0')}`));
function selection(ids = CATALOG) {
  if (!ids.length || new Set(ids).size !== ids.length || ids.some(id => !CATALOG.includes(id))) throw new Error('Only independent MC P1 IDs are allowed');
  return ['mc-p1.spec.cjs','--project=mc-p1','--no-deps','--grep','(' + ids.join('|') + ') P1:'];
}
function verifyListing(listing, ids = CATALOG) {
  const found = [...listing.matchAll(/› ((?:R07|R08|C05|C06|C07)-\d\d) P1:/g)].map(m=>m[1]);
  if (found.length !== ids.length || new Set(found).size !== ids.length || found.some(id=>!ids.includes(id)) ||
      Number(listing.match(/Total: (\d+) tests? in/)?.[1]) !== ids.length) throw new Error('MC P1 listing mismatch');
}
function reserve(directory, entry) {
  const file = path.join(directory,'resource-ledger.json');
  if (fs.existsSync(file) && JSON.parse(fs.readFileSync(file)).length) throw new Error('One-container MC P1 budget exhausted');
  fs.mkdirSync(directory,{recursive:true}); fs.writeFileSync(file,JSON.stringify([entry],null,2),{flag:'wx'});
}
async function session({env,start,complete,forward,failureLog,run}) {
  const dir = env.E2E_EVIDENCE_DIR, phases = [], failed = new Set();
  const commands = path.join(dir,'mc-p1-commands.jsonl'); let consumed = 0;
  let command = {ids:CATALOG,label:'p1-original'};
  const source = () => {
    const files = {}, root=path.resolve(__dirname,'../src');
    const visit = d => { for (const e of fs.readdirSync(d,{withFileTypes:true})) {const f=path.join(d,e.name);if(e.isDirectory())visit(f);else files[path.relative(root,f)]=createHash('sha256').update(fs.readFileSync(f)).digest('hex');} }; visit(root); return files;
  };
  const save = state => fs.writeFileSync(path.join(dir,'mc-p1-session.json'),JSON.stringify({state,phases,failed:[...failed]},null,2));
  while (command) {
    const args = selection(command.ids);
    if (!/^[a-z0-9-]{1,50}$/.test(command.label) || phases.some(p=>p.label===command.label)) throw new Error('Unsafe P1 phase');
    const extra = {E2E_MC_PHASE:command.label,E2E_CONTROL_URL:env.E2E_CONTROL_URL,E2E_CONTROL_KEY:env.E2E_CONTROL_KEY,PLAYWRIGHT_NO_COPY_PROMPT:'1'};
    const listing=await complete(start(process.execPath,[require.resolve('@playwright/test/cli'),'test',...args,'--list','--reporter=list'],undefined,extra),'List MC P1 phase');
    verifyListing(listing,command.ids);fs.writeFileSync(path.join(dir,`selection-${command.label}.txt`),listing,{flag:'wx'});
    fs.writeFileSync(path.join(dir,`source-${command.label}.json`),JSON.stringify(source(),null,2),{flag:'wx'}); save('RUNNING');
    const child=start(process.execPath,[require.resolve('@playwright/test/cli'),'test',...args],undefined,extra);
    forward(child.stdout,t=>process.stdout.write(t));forward(child.stderr,t=>process.stderr.write(t));
    let exit=0;try {await complete(child,'MC P1 '+command.label,2_400_000);}catch {exit=child.exitCode??-1;}
    failureLog(child,'MC-'+command.label);
    const file=path.join(dir,`results-${command.label}.json`);if(!fs.existsSync(file))throw new Error('Native MC P1 result missing');
    const bytes=fs.readFileSync(file),result=JSON.parse(bytes);
    if(result.tests.length!==command.ids.length)throw new Error('Incomplete P1 native catalog');
    for(const t of result.tests){const id=t.name.split(' ')[0];if(t.attempts.length===1&&t.attempts[0].status==='passed')failed.delete(id);else failed.add(id);}
    phases.push({label:command.label,ids:command.ids,exit,sha256:createHash('sha256').update(bytes).digest('hex')});run.mcP1Phases=phases;
    if(!failed.size){save('FINISHED');break;}
    save('AWAITING_COMMAND'); console.log(`[mc-p1] Failed ${[...failed].join(',')}; awaiting scoped command in ${commands}`);
    command=null;const deadline=Date.now()+3_600_000;
    while(!command){const lines=fs.existsSync(commands)?fs.readFileSync(commands,'utf8').trim().split(/\r?\n/).filter(Boolean):[];
      if(lines.length>consumed){command=JSON.parse(lines[consumed++]);break;}if(Date.now()>deadline)throw new Error('MC P1 command timeout');await new Promise(r=>setTimeout(r,500));}
    if(command.finish===true){save('FINISHED_WITH_FAILURES');break;}
    selection(command.ids);if(command.ids.some(id=>!failed.has(id)))throw new Error('Only failed P1 cases may be rechecked');
  }
  run.playwrightExitCode=failed.size?1:0;
}
module.exports={COUNTS,CATALOG,selection,verifyListing,reserve,session};
