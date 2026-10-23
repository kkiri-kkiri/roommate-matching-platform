// Inventory local evidence without opening DBs or reading secrets. Never turn source code into PASS.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..'),work=path.join(root,'backend/build/matching-calendar-http5');
const rel=p=>path.relative(root,p).replaceAll('\\','/'),sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
assert(!fs.existsSync(path.join(work,'execution-started.json')),'Pre-approval plan is frozen after execution begins.');
const prepared=read(path.join(work,'prepared.json'));
assert(Array.isArray(prepared.matching)&&prepared.matching.length===14);assert(Array.isArray(prepared.calendar)&&prepared.calendar.length===15);
// Search only execution evidence names; never load Docker metadata, environment files, credentials or raw app logs.
const excludedDirs=new Set(['.git','node_modules','.gradle','bin','classes','reports','test-results','resources','static','lib','main','generated']);
const found=[];
function scan(dir){if(!fs.existsSync(dir))return;for(const e of fs.readdirSync(dir,{withFileTypes:true})){
 const file=path.join(dir,e.name);if(e.isSymbolicLink())continue;
 if(e.isDirectory()){if(!excludedDirs.has(e.name)&&!e.name.startsWith('artifact-')&&!e.name.startsWith('lib-'))scan(file);}
 else if(['summary.json','cases.json','requests.json','evidence.json'].includes(e.name)&&/matching|calendar/i.test(rel(file))&&!file.startsWith(work+path.sep))found.push(file);
}}
for(const dir of ['backend/build','backend/tools','tmp'])scan(path.join(root,dir));
const candidates=found.map(f=>({path:rel(f),sha256:sha(f)}));
// This plan must be regenerated with explicit evidence review if original files are recovered.
assert(candidates.length===0,'Prior Matching/Calendar evidence found: review actual cases, requests, lifecycle, identity and hashes before choosing duplicates.');
const calendarSource=path.join(root,'backend/tools/calendar-mysql-multi-instance/CalendarMultiInstance.java');
const originalNames=[...fs.readFileSync(calendarSource,'utf8').matchAll(/\btest\("([^"\n]+)"/g)].map(m=>m[1]);assert.equal(originalNames.length,31);
const sources=fs.readdirSync(__dirname).filter(n=>fs.statSync(path.join(__dirname,n)).isFile()).sort().map(n=>({path:rel(path.join(__dirname,n)),sha256:sha(path.join(__dirname,n))}));
const planned=[...prepared.matching.map((id,i)=>({id,domain:'Matching',priority:i<8?'P0':'P1',status:'NOT RUN'})),...prepared.calendar.map((id,i)=>({id,domain:'Calendar',priority:i<9?'P0':'P1',status:'NOT RUN'}))];
const plan={date:'2026-10-22',timezone:'Asia/Seoul',status:'AWAITING_RESOURCE_APPROVAL',
 actual:{tcpHttpRequests:0,jdbcConnections:0,passed:0,failed:0,notRun:planned.length,p0:planned.filter(x=>x.priority==='P0').length,p1:planned.filter(x=>x.priority==='P1').length},
 priorCalendar31:{status:'INSUFFICIENT_EVIDENCE',source:rel(calendarSource),sourceSha256:sha(calendarSource),sourceScenarioCount:31,originalNames,passEvidenceFound:false,passBasedExclusions:[],note:'An executable and prior H2 reports do not establish actual TCP/MySQL PASS. No prior DB accessed.'},
 priorMatching:{status:'INSUFFICIENT_EVIDENCE',report:'backend/MATCHING_VALIDATION.md',reportSha256:sha(path.join(root,'backend/MATCHING_VALIDATION.md')),passEvidenceFound:false},
 evidenceSearch:{roots:['backend/build','backend/tools','tmp'],names:['summary.json','cases.json','requests.json','evidence.json'],matchingCalendarCandidates:candidates,excludedDirectories:[...excludedDirs],limitations:'Local workspace only; no remote machines, removed files or existing databases searched.'},
 resources:{created:false,approvalRequired:true,container:'kkiri-mc-http5-mysql-20261022',volume:'kkiri-mc-http5-data-20261022',database:'kkirimc5http20261022',account:'mc5http20261022@%',mysql:'127.0.0.1:13348',http:['127.0.0.1:18095','127.0.0.1:18096'],image:'mysql:8.0.46-oraclelinux9',expectedImageId:'sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b',collisionChecks:'PENDING: Docker engine unavailable; must succeed immediately before creation',cleanup:'After owned JVM termination only DELETE synthetic rows in NEW schema; retain container/volume/DB/account/tables. No DROP/TRUNCATE/resource deletion.'},
 preflight:read(path.join(work,'preflight.json')),preparation:prepared,
 blocked:[{id:'infrastructure',status:'BLOCKED',reason:'Local Docker Linux engine pipe unavailable; no fresh MySQL target and no resource creation approval yet.'},{id:'external-ai-gateway',status:'BLOCKED',reason:'No approved test endpoint, credentials or cost scope supplied. Loopback client contract cases are separate.'},{id:'real-school-crawl',status:'BLOCKED',reason:'No approved external school crawl environment supplied. Calendar fixture uses repository HTML served only on 127.0.0.1.'}],
 exclusions:[{scope:'Existing general roommate listing, profile-like races and basic calendar creation',reason:'Explicit user scope exclusion; fixtures can create calendars, but creation is not counted as a new scenario. No unsupported prior PASS claim.'},{scope:'Remaining original Calendar scenarios',reason:'Execute only the three reused functional contracts and new missing cases; do not rerun all 31 concurrency, restart, timeout and forced process/connection failure cases.'},{scope:'Auth/Survey expiry and input boundaries; other-domain full suites',reason:'Explicit user scope exclusion. Valid survey PUTs arrange scoring fixtures only.'}],
 scenarios:planned,confirmedServiceDefects:[],serviceDefectConclusion:'NOT ASSESSED by runtime; static preparation cannot establish absence of defects.',sources,
 commitFiles:[...sources.map(x=>x.path),'backend/MATCHING_CALENDAR_HTTP5_VALIDATION.md','backend/validation/matching-calendar-http5-plan-20261022.json'],
};
const target=path.join(root,'backend/validation/matching-calendar-http5-plan-20261022.json');fs.writeFileSync(target,JSON.stringify(plan,null,2)+'\n');
console.log(JSON.stringify({plan:rel(target),scenarios:planned.length,p0:plan.actual.p0,p1:plan.actual.p1,tcpHttpRequests:0,priorCalendar31:plan.priorCalendar31.status,preflight:plan.preflight.status}));
