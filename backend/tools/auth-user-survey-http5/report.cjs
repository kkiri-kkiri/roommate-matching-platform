// Produce a secret-free completion summary. Preserves every raw run, including harness failures.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..'),base='backend/build/auth-user-survey-http5';
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8').replace(/^\uFEFF/,''));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex');
const ids=['2026-10-22-82bee9f0fc29','2026-10-22-32da878213d5','2026-10-22-a0115b20ea32','2026-10-22-9a38b19aadb1'];
const p1Names=new Set(read(base+'/planned-scenarios.json').filter(s=>s.priority==='P1').map(s=>s.name));
const runs=ids.map((id,index)=>{
  const dir=base+'/runs/'+id,summary=read(dir+'/summary.json'),cases=read(dir+'/cases.json'),requests=read(dir+'/requests.json'),events=read(dir+'/servers.json');
  const executed=cases.filter(c=>!c.carriedForward&&c.name!=='harness');
  const finished=events.filter(e=>e.event.kind==='httpFinished');
  assert.equal(requests.length,summary.httpRequestsIncludingFixtureSetup);assert.equal(finished.length,requests.length);
  assert(summary.allProcessesStopped&&summary.allTablesEmpty&&summary.sourcesAndPriorEvidenceUnchanged);
  const cleanup=read(dir+'/cleanup.json');assert.equal(Object.keys(cleanup.rows).length,31);assert(Object.values(cleanup.rows).every(n=>n===0));assert(cleanup.ddlUnchanged&&cleanup.allProcessesStoppedBeforeCleanup);
  if(index<3)assert(!requests.some(r=>p1Names.has(r.case)),'P1 ran before P0 review');
  return {id,evidence:dir,summarySha256:sha(dir+'/summary.json'),casesSha256:sha(dir+'/cases.json'),requestsSha256:sha(dir+'/requests.json'),serversSha256:sha(dir+'/servers.json'),cleanupSha256:sha(dir+'/cleanup.json'),
    verdict:summary.verdict,httpRequests:requests.length,serverObservedHttpRequests:finished.length,executedScenarioAttempts:executed.length,
    passedThisRun:executed.filter(c=>c.status==='PASS').length,failedAttempts:executed.filter(c=>c.status==='FAIL').map(c=>({name:c.name,status:'HARNESS / INVALID FIXTURE',assertion:c.error.causes[0].assertion})),
    infrastructureErrors:cases.filter(c=>c.name==='harness').map(c=>({name:c.name,status:c.status,errorClasses:c.error.causes.map(e=>e.class)})),
    allProcessesStopped:true,all31TablesEmpty:true,ddlUnchanged:true,
    sourceAdaptationsAvailable:fs.existsSync(path.join(root,dir+'/source-adaptations.json'))};
});
const finalDir=base+'/runs/'+ids.at(-1),final=read(finalDir+'/summary.json'),outcomes=read(finalDir+'/outcomes.json');
assert.equal(final.verdict,'PASS');assert.equal(outcomes.length,59);assert(outcomes.every(c=>c.status==='PASS'));
const totalRequests=runs.reduce((n,r)=>n+r.httpRequests,0);assert.equal(totalRequests,183);assert.equal(final.totalHttpRequestsIncludingPriorAttempt,totalRequests);
const phases=final.phases;assert.equal(phases.P0.PASS,13);assert.equal(phases.P1.PASS,46);
const original=read(base+'/runs/'+ids[0]+'/source-hashes.json'),last=read(finalDir+'/source-hashes.json');
const productionKeys=Object.keys(original).filter(k=>/^backend[\\/]src[\\/]main[\\/]/.test(k));
assert(productionKeys.length>0);assert(productionKeys.every(k=>original[k]===last[k]),'Production files changed across attempts');
assert.equal(final.bootJarSha256,sha('backend/build/libs/backend-0.0.1-SNAPSHOT.jar').toUpperCase());
const auditFile=fs.readdirSync(path.join(root,base)).filter(f=>/^audit-\d+\.json$/.test(f)).sort().at(-1);
const audit=read(base+'/'+auditFile),retention=read(base+'/resource-retention.json'),environment=read(base+'/environment.json');
assert(audit.allTablesEmpty&&Object.keys(audit.rows).length===31&&Object.values(audit.rows).every(n=>n===0));
assert.equal(retention.httpListeners,0);assert.equal(retention.state,'running');assert(retention.volumeRetained&&!retention.resourceDeletionPerformed);
const canonical=x=>Array.isArray(x)?x.map(canonical):x&&typeof x==='object'?Object.fromEntries(Object.entries(x).sort(([a],[b])=>a.localeCompare(b)).map(([k,v])=>[k,canonical(v)])):typeof x==='string'?x.replace(/ AUTO_INCREMENT=\d+/g,''):x;
assert.deepEqual(canonical(audit.ddl),canonical(read(base+'/runs/'+ids[0]+'/initial-schema.json')));
const expiryDir=base+'/runs/'+ids[2];
const expirationFixtures=fs.readdirSync(path.join(root,expiryDir)).filter(f=>f.endsWith('-expiration.json')).sort().map(file=>({evidence:expiryDir+'/'+file,sha256:sha(expiryDir+'/'+file),...read(expiryDir+'/'+file)}));
assert.equal(expirationFixtures.length,3);assert(expirationFixtures.every(e=>e.expiredBeforeHttp&&e.rowsChanged===1));
const checkpoint=read(base+'/runs/'+ids[2]+'/p0-checkpoint.json'),review=read(base+'/runs/'+ids[2]+'/p0-reviewed.json');
assert(checkpoint.p0Passed===13&&checkpoint.p0Failed===0&&!checkpoint.p1Started&&review.continueP1);
const prior=read('backend/validation/auth-user-survey-http5-plan-20261022.json');
const allRequests=ids.flatMap(id=>read(base+'/runs/'+id+'/requests.json'));
const responseDistribution={};for(const r of allRequests){const key=r.status+':'+(r.errorCode||'SUCCESS');responseDistribution[key]=(responseDistribution[key]??0)+1;}
const finalOutcomes=outcomes.map(c=>{
  const evidenceRun=c.evidenceRun||ids.at(-1);
  return {...c,evidenceRun,evidence:base+'/runs/'+evidenceRun+'/cases.json'};
});
const report={
  recordedAt:new Date().toISOString(),date:'2026-10-22',timezone:'Asia/Seoul',status:'PASS',
  approval:{source:'User approved the presented 59-scenario plan and new resource scope in this conversation',schemaRecreationOrResourceDeletionApproved:false,p0ReviewedBeforeP1:true},
  environment,artifact:{jarSha256:final.bootJarSha256,mainClassesCompared:292,productionFilesComparedAcrossRuns:productionKeys.length,productionFilesChanged:0,jarRebuilt:false},
  logicalResults:{scenarios:59,executed:59,P0:phases.P0,P1:phases.P1,passed:59,failed:0,blocked:0,notRun:0},
  attempts:{scenarioAttempts:runs.reduce((n,r)=>n+r.executedScenarioAttempts,0),validScenarioExecutions:59,invalidFixtureAttempts:2,p0ReviewTransportErrors:1,runs},
  http:{transport:'Real TCP HTTP -> loopback Spring Boot security/controller/service -> isolated MySQL; no MockMvc',requests:totalRequests,serverObservedRequests:totalRequests,includes:'All fixture setup, assertion requests, two invalid-fixture attempts and repeated Boot startup login fixtures',responseDistribution,prior1502RequestsRerun:false},
  phaseGate:{checkpoint:base+'/runs/'+ids[2]+'/p0-checkpoint.json',review:base+'/runs/'+ids[2]+'/p0-reviewed.json',reviewSha256:sha(base+'/runs/'+ids[2]+'/p0-reviewed.json'),P0:13,p1NotStartedAtReview:true,carriedReviewedP0IntoFinalRun:true},
  expiration:{fixtureAdjusted:{status:'PASS',cases:3,evidence:expirationFixtures},realTtlElapsed:{status:'NOT RUN',reason:'No real TTL wait was performed; fixture-expiry results do not validate actual elapsed TTL or deployment clock synchronization'}},
  excludedDuplicates:prior.excludedDuplicates,
  defects:{confirmedServiceDefects:[],serviceCodeChanged:false,harnessFindings:[
    {id:'fixture-localdatetime-mapping',run:ids[0],impact:'Expired-code fixture remained effectively future-dated; verification returned 200; invalid fixture excluded from service-failure verdict',reproduction:'On the guarded UTC JDBC connection, bind 2000-01-02T03:04:05 with setObject(LocalDateTime); read via getTimestamp and Hibernate LocalDateTimeJavaType.wrap: 2000-01-02T12:04:05',evidence:base+'/audit-20261022074359.json',resolution:'Explicit setTimestamp at a fixed past second; read effective application timestamp and require it to be in the past before HTTP'},
    {id:'fixture-expiration-precheck',run:ids[1],impact:'Overly strict target-time assertion stopped before verification HTTP; no service-failure evidence',reproduction:'Initial Timestamp correction required effective timestamp <= an unrounded target as well as expiration; assertion failed. Exact original timestamp was not retained, so the detailed rounding cause remains unproven',resolution:'Fixed past whole second; persist diagnostic before checking the actual required condition effective timestamp < application now'},
    {id:'p0-review-json-bom',run:ids[2],impact:'PowerShell UTF-8 BOM in the review JSON caused StreamReadException; P0 13 PASS remained valid and P1 had not started',reproduction:'Read a PowerShell Set-Content -Encoding UTF8 JSON through Files.readString then Jackson readTree(String)',resolution:'Read JSON bytes so Jackson recognizes the UTF-8 BOM; reuse recorded P0 approval and execute P1 only'},
  ]},
  cleanup:{audit:base+'/'+auditFile,auditSha256:sha(base+'/'+auditFile),tableCount:31,rows:audit.rows,allTablesEmpty:true,ddlPreserved:true,allHttpProcessesStopped:true,httpListeners:0,resourceRetention:retention},
  externalChecks:[{scope:'real SMTP',status:'BLOCKED',connections:0},{scope:'HTTPS',status:'BLOCKED',connections:0},{scope:'ingress',status:'BLOCKED',connections:0}],
  scenarios:finalOutcomes,
  commitCandidates:[
    '.gitignore',
    'backend/tools/auth-user-survey-http5/AusHttp5.java','backend/tools/auth-user-survey-http5/AusHttp5Audit.java',
    'backend/tools/auth-user-survey-http5/prepare.cjs','backend/tools/auth-user-survey-http5/provision.ps1','backend/tools/auth-user-survey-http5/run.ps1',
    'backend/tools/auth-user-survey-http5/review.cjs','backend/tools/auth-user-survey-http5/report.cjs','backend/tools/auth-user-survey-http5/README.md',
    'backend/AUTH_USER_SURVEY_HTTP5_VALIDATION.md','backend/validation/auth-user-survey-http5-plan-20261022.json','backend/validation/auth-user-survey-http5-20261022.json',
  ],
};
const target='backend/validation/auth-user-survey-http5-20261022.json';fs.writeFileSync(path.join(root,target),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({status:report.status,scenarios:59,p0Passed:13,p1Passed:46,httpRequests:totalRequests,invalidFixtureAttempts:2,reviewTransportErrors:1,productionFilesUnchanged:productionKeys.length,cleanedTables:31,report:target}));
