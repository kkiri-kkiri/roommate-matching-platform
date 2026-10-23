const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..'),work=path.join(root,'backend/build/matching-calendar-http5');
const read=p=>JSON.parse(fs.readFileSync(path.join(work,p),'utf8').replace(/^\uFEFF/,''));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(path.join(work,p))).digest('hex');
if(process.argv[2]==='--final'){
 const readRoot=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8').replace(/^\uFEFF/,''));
 const hashRoot=p=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex');
 const planPath='backend/validation/matching-calendar-http5-plan-20261022.json',plan=readRoot(planPath),gate=read('p0-reviewed.json');
 assert.equal(gate.status,'P0_REVIEWED_PASS');assert.equal(gate.cases,17);for(const e of gate.evidence)assert.equal(sha(e.path),e.sha256);
 const groups=[['P0-continue1/matching','Matching','P0',8],['P0/calendar','Calendar','P0',9],['P1/matching','Matching','P1',6],['P1/calendar','Calendar','P1',6],['P1-null-recovery/calendar','Calendar','P1',1]];
 const outcomes=new Map(),runs=[],requests=[],evidence=[],attempts=[];let stubCalls=0,htmlCalls=0;
 for(const [base,domain,priority,count] of groups){
  const summary=read(base+'/summary.json'),cases=read(base+'/cases.json'),http=read(base+'/requests.json'),events=read(base+'/servers.json');
  assert.equal(cases.length,count);assert.equal(summary.httpRequests,http.length);assert.equal(summary.serverHttpObserved,http.length);assert(summary.allTablesEmpty&&summary.processesStopped);assert(events.some(e=>e.kind==='closed'&&e.data.poolClosed));
  assert(!events.some(e=>['forbiddenMail','forbiddenExternalCall','stubError'].includes(e.kind)));
  if(base!=='P1/calendar')assert.equal(summary.verdict,'PASS');
  for(const c of cases){const status=c.result??c.status;assert(['PASS','FAIL'].includes(status));if(status==='FAIL')assert(base==='P1/calendar'&&c.name==='patch-null-merge');
   const record={id:c.name,domain,priority,status,evidence:base+'/cases.json',expected:c.expected??null,actual:c.actual??c.detail??null,error:c.error??null};attempts.push(record);outcomes.set(c.name,record);
  }
  if(domain==='Matching')assert(summary.ddlUnchanged);else assert(read(base+'/cleanup.json').ddlUnchanged);
  requests.push(...http.map(r=>({...r,evidenceRun:base})));stubCalls+=summary.localStubCalls??0;htmlCalls+=events.filter(e=>e.kind==='fixtureRequest').length;
  runs.push({id:base,summary});for(const name of ['summary','cases','requests','servers','cleanup','environment']){const p=base+'/'+name+'.json';evidence.push({path:'backend/build/matching-calendar-http5/'+p,sha256:sha(p)});}
 }
 assert.equal(outcomes.size,29);assert.equal(attempts.length,30);assert.equal(attempts.filter(x=>x.status==='FAIL').length,1);
 const scenarios=plan.scenarios.map(s=>{const actual=outcomes.get(s.id);assert(actual&&actual.domain===s.domain&&actual.priority===s.priority&&actual.status==='PASS');return actual;});
 assert.equal(requests.length,212);assert.equal(gate.httpRequests,124);assert.equal(stubCalls,4);assert.equal(htmlCalls,4);
 const initial=read('P0/matching/summary.json');assert.equal(initial.httpRequests,0);assert(initial.processesStopped&&initial.allTablesEmpty&&initial.ddlUnchanged);
 const auditFile=fs.readdirSync(work).filter(n=>/^audit-.*\.json$/.test(n)).sort().at(-1),audit=read(auditFile),retention=read('resource-retention.json'),processes=read('http-process-audit.json'),manifest=read('environment.json');
 assert(audit.readOnly&&audit.allEmpty);assert.equal(Object.keys(audit.rows).length,31);assert(Object.values(audit.rows).every(n=>n===0));assert.equal(audit.accountConnections,1);
 assert.deepEqual(audit.ddl,read('P0/matching/schema-before.json'));assert.equal(audit.identity[0].version,'8.0.46');assert.equal(audit.identity[0].uuid,manifest.uuid);
 assert.equal(retention.status,'running');assert.equal(retention.containerId,manifest.containerId);assert.equal(retention.listeners['18095'],0);assert.equal(retention.listeners['18096'],0);assert(processes.allStopped);assert.equal(processes.ownedHttpProcesses.length,8);
 const baseline=read('protected-sources-before.json');for(const e of baseline)assert.equal(hashRoot(e.path),e.sha256,'Protected file changed: '+e.path);
 const protectedAfter={recordedAtUtc:new Date().toISOString(),files:baseline.length,allUnchanged:true,paths:baseline};fs.writeFileSync(path.join(work,'protected-sources-after.json'),JSON.stringify(protectedAfter,null,2)+'\n');
 const diagnostics=fs.readdirSync(work).filter(n=>n.startsWith('diagnostic-')).sort().map(n=>({id:n,summary:read(n+'/summary.json')}));assert.equal(diagnostics.length,2);assert(diagnostics.every(d=>d.summary.httpRequests===0&&d.summary.processesStopped));
 const extra=['preflight.json','preflight-20261022T135749Z.json','preflight-20261022T140637Z.json','environment.json','execution-started.json','p0-continuation-started.json','p1-started.json','patch-null-recovery-started.json','p0-reviewed.json','source-adaptations.json','prepared.json','resource-retention.json','http-process-audit.json',auditFile,'protected-sources-before.json','protected-sources-after.json','P0/matching/cases.json','P0/matching/summary.json','P0/matching/servers.json','P0/matching/cleanup.json','P0/matching/schema-before.json','P0/matching/schema-after.json','history/blocked-preflight-20261022T140637Z.json','history/patch-null-recovery-gate-error.json','history/source-adaptations-preapproval.json','history/prepared-preapproval.json','history/source-adaptations-before-startup-diagnostic.json','history/source-adaptations-before-null-assertion-fix.json'];
 for(const d of diagnostics){extra.push(d.id+'/servers.json',d.id+'/summary.json');if(fs.existsSync(path.join(work,d.id,'error.json')))extra.push(d.id+'/error.json');}
 for(const p of extra)evidence.push({path:'backend/build/matching-calendar-http5/'+p,sha256:sha(p)});
 const distribution=new Map();for(const r of requests){const k=r.status+'/'+r.errorCode;distribution.set(k,(distribution.get(k)??0)+1);}
 const toolSources=fs.readdirSync(__dirname).filter(n=>fs.statSync(path.join(__dirname,n)).isFile()).sort().map(n=>'backend/tools/matching-calendar-http5/'+n);
 const result={date:'2026-10-22',timezone:'Asia/Seoul',recordedAtUtc:new Date().toISOString(),status:'PASS',
  approval:{status:'CONDITIONALLY_APPROVED_AND_EXECUTED',scope:'Exact new resources and 29 cases; P0 results reviewed before P1; DELETE synthetic rows only and retain resources; real AI/school prohibited'},
  logicalCases:29,caseAttempts:30,priorityResults:['P0','P1'].map(priority=>({priority,pass:scenarios.filter(x=>x.priority===priority).length,fail:0,blocked:0,notRun:0})),
  tcpHttpRequests:requests.length,serverHttpObserved:requests.length,httpCountDefinition:'Client to Spring Boot security/controller/service/MySQL only; includes fixtures and the first failed null-format attempt. Excludes local Gateway/HTML downstream requests and IPC/JDBC.',
  httpDistribution:[...distribution].map(([key,count])=>({key,count})),localTransports:{aiStubRequests:stubCalls,htmlFixtureRequests:htmlCalls,realAiGatewayRequests:0,realSchoolRequests:0},
  mysql:{actualVersion:audit.identity[0].version,...manifest,collation:audit.identity[0].collation,jdbcSessionZone:audit.identity[0].zone,grants:audit.grants},
  p0Review:{cases:17,httpRequests:gate.httpRequests,reviewedAtUtc:gate.reviewedAtUtc,evidence:'backend/build/matching-calendar-http5/p0-reviewed.json'},
  scenarios,runs,attempts,diagnostics,
  defects:{confirmedServiceDefects:[],assessment:'No confirmed service defect in the 29 final scenarios; this does not prove complete deployment correctness.',runnerErrors:[
   {id:'shared-validation-classpath',result:'INCOMPLETE',httpRequests:0,originalEvidence:'P0/matching',diagnosis:'Calendar test Infrastructure was on the Matching application component-scan classpath, introducing a second primary DataSource and preventing EntityManager auto-configuration. Diagnostic root missing type: jakarta.persistence.EntityManager.',resolution:'Separate classes/matching and classes/calendar; same JAR and retained schema, validate only; isolated startup succeeds.'},
   {id:'null-patch-time-format-expectation',result:'FAIL',case:'patch-null-merge',originalEvidence:'P1/calendar',diagnosis:'Runner incorrectly expected 23:59:00. Existing CalendarEventResponse declares @JsonFormat HH:mm.',resolution:'Assert existing 23:59/00:00 format and full DTO equality, both omitted/null PATCH, GET equality and unchanged DB; reran only this case (8 HTTP).'},
   {id:'powershell-recovery-json-array',result:'TOOL_GUARD_REJECTED',httpRequests:0,diagnosis:'Pipeline ConvertFrom-Json array wrapper prevented exact failed-case inspection.',resolution:'Explicit InputObject and object[] conversion; marker and HTTP JVM not started by rejected attempt.'}],priorPreparationError:'Unqualified CrawledAcademicEvent corrected during DB-free compilation before approval.'},
  cleanup:{all31TablesEmpty:true,ddlUnchangedExceptAutoIncrement:true,httpJvmsObserved:8,allOwnedHttpJvmsStopped:true,successfulBootPoolsClosed:true,accountConnectionsAtAudit:1,accountConnectionsMeaning:'Only the read-only auditor itself; prior application connections absent',httpListenersRemaining:0,resourceDeletionPerformed:false},
  retention,existingResources:{databasesAccessed:false,resourcesStarted:false,mutations:false,internalStateAudited:false,note:'Only newly created approved resources were inspected/used; no global claim about independently changing existing resources.'},
  protection:{checkedFiles:baseline.length,allUnchanged:true,jarSha256:manifest.jarSha256,serviceOrOriginalToolChanged:false},
  priorCalendar31:{status:'INSUFFICIENT_EVIDENCE',combinedWithNew29:false,rerun:false,passBasedExclusions:0},
  originalPlan:{path:planPath,sha256:hashRoot(planPath),preserved:true},excludedDuplicates:plan.exclusions,
  unverified:[{scope:'Real AI Gateway',status:'BLOCKED',reason:'Outside approved scope; no approved credentials/cost/endpoint. Stub success is only local client-contract evidence.'},{scope:'Real school crawl',status:'BLOCKED',reason:'Outside approved scope; only repository HTML on loopback used.'},{scope:'Additional distinct-score sorting and negative filter combinations',status:'NOT RUN',reason:'The 29 cases check tied-score ID order and explicit DESC acceptance, but do not independently contrast unequal-score DESC or every negative compound-filter combination.'},{scope:'Deployment HTTPS/ingress and production resources',status:'NOT RUN',reason:'Not part of approved isolated loopback validation.'},{scope:'Prior Calendar 31 execution',status:'INSUFFICIENT_EVIDENCE',reason:'Original runtime evidence not recovered.'},{scope:'Existing unit/H2/other-domain/full MySQL suites',status:'NOT RUN',reason:'Not repeated as instructed.'}],
  sources:toolSources.map(p=>({path:p,sha256:hashRoot(p)})),evidence,
  commitFiles:[...toolSources,'backend/MATCHING_CALENDAR_HTTP5_VALIDATION.md',planPath,'backend/validation/matching-calendar-http5-20261022.json'],committed:false,
 };
 fs.writeFileSync(path.join(root,'backend/validation/matching-calendar-http5-20261022.json'),JSON.stringify(result,null,2)+'\n');
 console.log(JSON.stringify({status:result.status,priorityResults:result.priorityResults,http:requests.length,attempts:attempts.length,all31TablesEmpty:true,resourcesRetained:true,protectedFiles:baseline.length,commitFiles:result.commitFiles.length}));process.exit(0);
}
if(process.argv[2]!=='--review-p0')throw new Error('Use --review-p0 after independently reading actual P0 results.');
assert(!fs.existsSync(path.join(work,'p0-reviewed.json')),'P0 review already recorded');
const prepared=read('prepared.json'),evidence=[];let cases=0,requests=0;
for(const [domain,names] of [['matching',prepared.matching.slice(0,8)],['calendar',prepared.calendar.slice(0,9)]]){
 const base=domain==='matching'?'P0-continue1/matching':'P0/calendar',summary=read(base+'/summary.json'),records=read(base+'/cases.json'),http=read(base+'/requests.json'),events=read(base+'/servers.json');
 assert.equal(summary.verdict,'PASS');assert.equal(summary.scenarios,names.length);assert(summary.processesStopped&&summary.allTablesEmpty);
 assert.equal(summary.httpRequests,http.length);assert.equal(summary.serverHttpObserved,http.length);
 assert.deepEqual(records.map(x=>x.name).sort(),[...names].sort());assert(records.every(x=>(x.result??x.status)==='PASS'));
 assert(events.some(x=>x.kind==='closed'&&x.data.poolClosed===true));
 for(const name of ['summary','cases','requests','servers','cleanup']){const p=base+'/'+name+'.json';evidence.push({path:p,sha256:sha(p)});}
 cases+=records.length;requests+=http.length;
}
assert.equal(cases,17);const gate={status:'P0_REVIEWED_PASS',reviewedAtUtc:new Date().toISOString(),cases,httpRequests:requests,review:'Actual errors, boundary results, server request observation, JVM closure and fixture cleanup checked before P1',evidence};
fs.writeFileSync(path.join(work,'p0-reviewed.json'),JSON.stringify(gate,null,2)+'\n');console.log(JSON.stringify({status:gate.status,cases,requests}));
