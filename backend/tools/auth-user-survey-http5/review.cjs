// Recheck preserved evidence and write an explicitly NOT RUN inventory. Read-only toward infrastructure.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..'),work=path.join(root,'backend/build/auth-user-survey-http5');
assert(!fs.existsSync(path.join(work,'environment.json')),'The pre-approval plan is frozen after provisioning; use report.cjs for actual results.');
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8').replace(/^\uFEFF/,''));
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex');
const summaryPath='backend/validation/auth-user-survey-mysql-20261021.json',prior=read(summaryPath);
const groups=[];
for(const id of ['authHttp','userHttp','userSecurity']){
  const group=prior.groups.find(g=>g.id===id);assert(group,`Missing ${id}`);
  const summary=read(group.evidence+'/summary.json'),requests=read(group.evidence+'/requests.json'),cases=read(group.evidence+'/cases.json');
  assert.equal(sha(group.evidence+'/summary.json').toUpperCase(),group.summarySha256);
  assert.equal(summary.verdict,'PASS');assert.equal(requests.length,summary.httpRequests);
  assert(cases.every(c=>c.status==='PASS'));assert.equal(cases.length,group.cases.length);
  assert.deepEqual(cases.map(c=>c.name).sort(),group.cases.map(c=>c.name).sort());
  groups.push({id,evidence:group.evidence,summarySha256:group.summarySha256,requests:requests.length,cases:cases.map(c=>c.name)});
}
const requests=groups.flatMap(g=>read(g.evidence+'/requests.json'));
assert.equal(requests.length,1502);
const knownAuth=new Set(['login','login/email-check','me','signup','password-reset/request','password-reset/confirm','email-verifications/send','email-verifications/verify']);
const endpoints=new Map();
for(const r of requests){
  const route=knownAuth.has(r.path)?'/api/auth/'+r.path:r.path.split('?')[0];
  // The original Auth evidence did not record method. Its helper distinguishes only GET me from POST.
  const method=r.method??(r.path==='me'?'GET':'POST');
  const key=method+' '+route;endpoints.set(key,(endpoints.get(key)??0)+1);
}
const owned=[
  'POST /api/auth/login','POST /api/auth/login/email-check','GET /api/auth/me','POST /api/auth/signup',
  'GET /api/auth/signup/availability','POST /api/auth/password-reset/request','POST /api/auth/password-reset/confirm',
  'POST /api/auth/email-verifications/send','POST /api/auth/email-verifications/verify',
  'GET /api/users/me','PATCH /api/users/me','PATCH /api/users/me/password',
  'GET /api/users/me/profile-survey','PUT /api/users/me/profile-survey',
  'GET /api/users/me/roommate-preferences','PUT /api/users/me/roommate-preferences',
];
const coverage=owned.map(endpoint=>({endpoint,recordedRequests:endpoints.get(endpoint)??0}));
assert.equal(coverage.filter(e=>e.recordedRequests>0).length,15);
const scenarios=read('backend/build/auth-user-survey-http5/planned-scenarios.json');assert.equal(scenarios.length,59);
const output={
  reviewedAt:new Date().toISOString(),status:'AWAITING RESOURCE APPROVAL',
  priorEvidence:{summary:summaryPath,summarySha256:sha(summaryPath),httpRequests:requests.length,groups,controllerCoverage:coverage,
    methodInference:'Original Auth requests omit method; GET me / POST auth routes inferred from original Java helpers. User evidence records method.'},
  proposedEnvironment:{container:'kkiri-aus-http5-mysql-20261022',volume:'kkiri-aus-http5-data-20261022',image:'mysql:8.0.46-oraclelinux9',
    expectedImageId:'sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b',expectedMysqlVersion:'8.0.46',
    mysqlEndpoint:'127.0.0.1:13327',httpEndpoint:'http://127.0.0.1:18093',db:'kkiriaus5http20261022',account:'aus5http20261022',
    uuid:null,created:false,cleanup:'DELETE fixture rows in NEW schema only after owned HTTP process stops; retain container/volume/schema/account/DDL; no DROP/TRUNCATE'},
  localPreparation:read('backend/build/auth-user-survey-http5/prepared.json'),
  newExecution:{httpRequests:0,passed:0,failed:0,notRun:59,p0:13,p1:46},scenarios,
  blocked:[{scope:'real SMTP',status:'BLOCKED',reason:'No approved deployment mail transport'},{scope:'HTTPS',status:'BLOCKED',reason:'No approved TLS deployment endpoint'},{scope:'ingress',status:'BLOCKED',reason:'No approved ingress deployment endpoint'}],
  excludedDuplicates:[
    {scope:'Auth existing HTTP suite',evidence:'authHttp',reason:'32 PASS cases: auth flows, token single-use, shared limiter/cooldown/aliases, privacy, rollback/SQL conflicts/recovery'},
    {scope:'User existing HTTP suite',evidence:'userHttp',reason:'27 PASS cases: principal/admin/JWT/status, partial profile, password/status races, MySQL integrity/locks/recovery'},
    {scope:'JWT infrastructure regression',evidence:'userSecurity',reason:'1 PASS case; 26 HTTP requests'},
    {scope:'Survey roundtrip/owner/introduction/auth/physical constraints',evidence:'userHttp',reason:'Existing 6 cases include 5 HTTP functional groups and 1 SQL-only group; not rerun'},
    {scope:'Preference weights=2 and =5 roundtrip',evidence:'survey-http-preference-roundtrip-options-and-weights',reason:'Upper valid bound 5 is already HTTP-covered'},
    {scope:'Preference unsupported key replacing meal at same 18-key count',evidence:'survey-http-preference-invalid-input-preserves-all-rows',reason:'Already covered; new 17/19-key counts are separate'},
    {scope:'Preference age contains 상관없음 with 동갑',evidence:'survey-http-preference-invalid-input-preserves-all-rows',reason:'Already covered; new single no-preference/duplicate/empty/unknown/null cases are separate'},
    {scope:'No profile survey: completed=false, answers=null',evidence:'survey-http-profile-roundtrip-owner-and-introduction',reason:'Already covered; new cases use an existing incomplete legacy row'},
    {scope:'Existing unit/H2/service MySQL validations',evidence:'backend/AUTH_USER_SURVEY_MYSQL_VALIDATION.md',reason:'Not rerun or counted as new TCP HTTP; fixture login/send/verify/PUT may repeat to arrange new boundary cases'},
  ],
  defects:{confirmed:[],note:'Static review/preparation does not establish runtime PASS or absence of defects. Service changes require a separate defect report before editing.'},
};
const target='backend/validation/auth-user-survey-http5-plan-20261022.json';
fs.writeFileSync(path.join(root,target),JSON.stringify(output,null,2)+'\n');
console.log(JSON.stringify({priorRequests:requests.length,coveredMethods:15,totalMethods:16,planned:59,p0:13,p1:46,newHttpRequests:0,report:target}));
