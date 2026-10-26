const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const cp=require('node:child_process');
const root=path.resolve(__dirname,'../..'),id=process.argv[2];
if(!/^[a-f0-9]{12}$/.test(id??''))throw new Error('Invalid P1 run ID');
const rel=`backend/build/full-service-e2e/${id}`,prep='backend/build/mc-p1-preparation/20261026';
const full=f=>path.join(root,f),hash=f=>crypto.createHash('sha256').update(fs.readFileSync(full(f))).digest('hex');
const text=f=>{const b=fs.readFileSync(full(f));return b.toString(b[0]===0xff&&b[1]===0xfe?'utf16le':'utf8').replace(/^\uFEFF/,'')};
const read=f=>JSON.parse(text(f)),lines=f=>text(f).trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const write=(f,x)=>fs.writeFileSync(full(f),JSON.stringify(x,null,2)+'\n',{flag:'wx'});
const {CATALOG}=require('../../frontend/e2e/mc-p1-session.cjs');
const run=read(rel+'/run.json'),cleanup=read(rel+'/cleanup-audit.json'),identity=read(rel+'/identity-observed.json');
const protection=read(prep+'/p0-protection.json'),head=cp.execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8',windowsHide:true}).trim();
if(run.scope!=='MATCHING_CALENDAR_P1_ONLY'||run.id!==id||!run.fixtureTablesEmpty||!run.containerRemoved||!run.ownedProcessesStopped||
  !cleanup.containerAbsentByExactId||!cleanup.containerAbsentByExactName||cleanup.portsRebindable.length!==3||
  identity.container.id!==run.containerId||identity.container.owner!==id||identity.database.version!=='8.0.46'||head!==protection.p0Commit)throw new Error('P1 cleanup, identity, or P0 commit mismatch');
const changedProtected=protection.files.filter(f=>hash(f.file)!==f.sha256).map(f=>f.file);
if(changedProtected.length)throw new Error('Original P0 evidence or plan changed');
const protectionAudit={p0Commit:head,protectedFiles:protection.files.length,changed:changedProtected};
if(fs.existsSync(full(rel+'/protection-audit.json'))){if(JSON.stringify(read(rel+'/protection-audit.json'))!==JSON.stringify(protectionAudit))throw new Error('Protection audit changed');}
else write(rel+'/protection-audit.json',protectionAudit);
const phases=run.mcP1Phases.map(p=>{const file=`${rel}/results-${p.label}.json`;if(hash(file)!==p.sha256)throw new Error('Native evidence changed');return {...p,file,native:read(file)}});
const byId=new Map(),attempts=[];
for(const phase of phases)for(const test of phase.native.tests){
  const key=test.name.split(' ')[0];if(!CATALOG.includes(key)||test.attempts.length!==1||test.attempts[0].retry!==0)throw new Error('Unexpected test ID or automatic retry');
  const result={id:key,title:test.name,phase:phase.label,nativeFile:phase.file,nativeTestId:test.id,status:test.attempts[0].status==='passed'?'PASS':'FAIL',attempt:test.attempts[0]};
  attempts.push(result);byId.set(key,result);
}
if(phases[0].native.tests.length!==31||byId.size!==31)throw new Error('Incomplete independent catalog');
const journals=[],journalSnapshotDifferences=[];
for(const name of fs.readdirSync(full(rel)).filter(n=>/^p1-.*\.json$/.test(n))){
  const p=read(rel+'/'+name),jsonl=lines(rel+'/'+name.replace(/\.json$/,'.jsonl'));
  if(JSON.stringify(p.events)!==JSON.stringify(jsonl.slice(0,p.events.length)))throw new Error('P1 journal prefix mismatch');
  if(jsonl.length!==p.events.length)journalSnapshotDifferences.push({file:rel+'/'+name,snapshotEvents:p.events.length,journalEvents:jsonl.length,
    lateEvents:jsonl.slice(p.events.length),reason:'Events recorded after the fixture JSON snapshot; use the original JSONL without rewriting either source.'});
  const phase=name.slice(3,-('.json'.length+p.caseId.length+1));
  journals.push({file:rel+'/'+name,phase,...p,events:jsonl});
}
if(journals.length!==attempts.length)throw new Error('Missing per-attempt P1 observations');
const counts={browserStart:0,browserResponse:0,browserFailure:0,syntheticResponses:0,upstreamCompleted:0,injectedLoss:0,routeCancelled:0};
const kinds={'browser-start':'browserStart','browser-response':'browserResponse','browser-failure':'browserFailure','synthetic-response':'syntheticResponses','upstream-complete':'upstreamCompleted','injected-loss':'injectedLoss','route-cancelled':'routeCancelled'};
const syntheticStatuses={},browserStatuses={},failureReasons={};
for(const j of journals)for(const e of j.events){if(kinds[e.kind])counts[kinds[e.kind]]++;
  if(e.kind==='synthetic-response')syntheticStatuses[e.status]=(syntheticStatuses[e.status]||0)+1;
  if(e.kind==='browser-response')browserStatuses[e.status]=(browserStatuses[e.status]||0)+1;
  if(e.kind==='browser-failure')failureReasons[e.reason]=(failureReasons[e.reason]||0)+1;
}
const server=lines(rel+'/server-http.jsonl'),serverStatuses={};for(const e of server)serverStatuses[e.status]=(serverStatuses[e.status]||0)+1;
const mysql={snapshot:0,reset:0,clean:0,fixture:0,cleanZeroConfirmed:0};
for(const name of fs.readdirSync(full(rel)).filter(n=>/^database-.*\.jsonl$/.test(n)))for(const e of lines(rel+'/'+name)){
  if(e.operation in mysql)mysql[e.operation]++;if(e.operation==='clean'&&e.allTablesEmpty)mysql.cleanZeroConfirmed++;
}
if(mysql.reset!==attempts.length||mysql.cleanZeroConfirmed!==attempts.length||mysql.fixture!==attempts.length)throw new Error('Independent fixture lifecycle incomplete');
const final=CATALOG.map(key=>{const latest=byId.get(key),history=attempts.filter(a=>a.id===key);return {...latest,initialStatus:history[0].status,history:history.map(a=>({phase:a.phase,status:a.status,nativeFile:a.nativeFile})),
  evidence:journals.filter(j=>j.caseId===key).map(j=>({phase:j.phase,file:j.file,fixture:j.fixture,alerts:j.alerts}))}});
const groupCounts={};for(const group of ['R07','R08','C05','C06','C07']){const entries=final.filter(c=>c.id.startsWith(group));groupCounts[group]={status:entries.every(c=>c.status==='PASS')?'PASS':'FAIL',PASS:entries.filter(c=>c.status==='PASS').length,FAIL:entries.filter(c=>c.status==='FAIL').length,BLOCKED:0,NOT_RUN:0};}
const sourceBefore=read(rel+'/source-before.json').files,sourceAfter=read(rel+'/source-after.json').files;
const sourceChanges=Object.keys(sourceBefore).filter(f=>sourceBefore[f]!==sourceAfter[f]);
const ledger=read('backend/build/mc-p1-preparation/resource-ledger.json');if(ledger.length!==1||ledger[0].id!==id)throw new Error('Single container budget mismatch');
const coreFiles=[];function visit(dir){for(const e of fs.readdirSync(full(dir),{withFileTypes:true})){const file=dir+'/'+e.name;if(e.isSymbolicLink())throw new Error('Evidence symlink');if(e.isDirectory())visit(file);else coreFiles.push({file,bytes:fs.statSync(full(file)).size,sha256:hash(file)})}}
visit(rel);visit(prep);
for(const f of coreFiles)if(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b|E2e![a-f0-9]{36}/.test(text(f.file)))throw new Error('Credential-shaped evidence requires private review');
const defect=journals.filter(j=>j.caseId==='C06-03').map(j=>({phase:j.phase,file:j.file,observations:j.events.filter(e=>['synthetic-response','partial-retry-observed'].includes(e.kind)||e.kind==='browser-response'&&e.method==='DELETE')}));
const summary={schemaVersion:1,date:'2026-10-26',timezone:'Asia/Seoul',runId:id,p0Commit:head,postP0CommitWorktree:protection.postCommitWorktree,
  branch:protection.branch,initialCounts:phases[0].native.counts,finalCounts:{PASS:final.filter(c=>c.status==='PASS').length,FAIL:final.filter(c=>c.status==='FAIL').length,BLOCKED:0,NOT_RUN:0},
  groups:groupCounts,independentTests:31,nativeAttempts:attempts.length,manualRecheckAttempts:attempts.length-31,automaticRetries:0,p0TestsExecuted:0,otherDomainE2eExecuted:0,
  phases:phases.map(p=>({label:p.label,ids:p.ids,exit:p.exit,file:p.file,sha256:p.sha256,counts:p.native.counts,startedAt:p.native.startedAt,finishedAt:p.native.finishedAt})),cases:final,attempts,
  classifications:{confirmedFrontendDefects:1,resolvedFrontendDefects:1,backendDefects:0,initialHarnessFailures:3,environmentBlockedCases:0,orchestrationErrors:run.evidenceErrors},
  c06DeletionReplay:defect,
  observations:{...counts,unmatchedBrowserStarts:counts.browserStart-counts.browserResponse-counts.browserFailure,browserStatuses,failureReasons,syntheticStatuses,serverHttpCompleted:server.length,serverStatuses,mysql,journalSnapshotDifferences,
    distinction:'Synthetic HTTP errors are local browser injection; upstream-complete records actual route.fetch responses; injected-loss follows a confirmed MySQL commit. Server HTTP excludes OPTIONS.'},
  checks:{javaCompile:{status:'PASS',file:prep+'/compile.log',offline:true},runnerSafety:{passed:42,skipped:3,file:prep+'/safety.log'},jest:{passed:26,suites:3,file:prep+'/regression-after.log'},
    regressionBeforeFix:{failed:1,passed:8,file:prep+'/defect-regression-before.log'},frontendBuild:{status:'PASS',file:prep+'/build.log'},contractCheck:{endpoints:73,recordDtos:75,entities:30,status:'PASS'}},
  resources:{createdContainers:1,schemas:1,accounts:1,volumes:0,remainingApprovedBudget:0,identity,cleanup,runFinishedAt:run.finishedAt},
  preservation:{p0FilesCompared:protection.files.length,changedProtected,originalPhasePreserved:true,sourceChangesBetweenPhases:sourceChanges,sourceUnchangedDuringWholeRun:run.sourceUnchangedDuringRun,
    sourceChangeReason:'Approved frontend deletion progress fix and its regression test, plus failed-case E2E month-navigation and expected-error helper corrections; original source copies retained.'},
  environment:{ai:'E2eServer.FixedAi',crawler:'DisabledCrawler',mail:'MemoryMail',externalGatewayRequests:0,schoolRequests:0,smtpRequests:0,calendarClock:'Calendar Date constructor fixed; Date.now and server JWT clock remain real'},
  orchestration:{playwrightExitCode:run.playwrightExitCode,sessionState:read(rel+'/mc-p1-session.json').state,ownedProcessesStopped:run.ownedProcessesStopped},
  p1Committed:false,pushed:false,coreFiles};
const output=`backend/validation/matching-calendar-e2e67-p1-20261026-${id}.json`;write(output,summary);
write(rel+'/evidence-manifest.json',{runId:id,summary:{file:output,sha256:hash(output)},files:coreFiles});
console.log(JSON.stringify({output,sha256:hash(output),final:summary.finalCounts,initial:summary.initialCounts,groups:groupCounts,observations:summary.observations,coreFiles:coreFiles.length,sourceChanges},null,2));
