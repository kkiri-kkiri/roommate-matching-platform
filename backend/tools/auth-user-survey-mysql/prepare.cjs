// Explicit approved-target variants. Original source/runners and test expectations remain intact.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../../..');
const work = path.join(root, 'backend/build/auth-user-survey-mysql');
const env = JSON.parse(fs.readFileSync(path.join(work, 'environment.json'), 'utf8').replace(/^\uFEFF/, ''));
assert.equal(env.container, 'kkiri-aus-mysql-20261021');
assert.equal(env.port, 13326); assert.equal(env.version, '8.0.46');
assert.match(env.uuid, /^[a-f0-9-]{36}$/);
const dest = path.join(work, 'sources'); fs.mkdirSync(dest, {recursive:true});
const files = [];
for (const folder of ['auth-mysql-concurrency','auth-mysql-multi-instance','user-mysql-concurrency','user-mysql-multi-instance','survey-mysql-concurrency']) {
  const dir = path.join(root, 'backend/tools', folder);
  files.push(...fs.readdirSync(dir).filter(x=>x.endsWith('.java')).sort().map(x=>path.join(dir,x)));
}
files.push(path.join(root,'backend/src/test/java/com/kkirikkiri/survey/SurveyTestData.java'));
files.push(...fs.readdirSync(__dirname).filter(x=>x.endsWith('.java')).sort().map(x=>path.join(__dirname,x)));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const evidence = [];
for (const source of files) {
  const name=path.basename(source), folder=path.basename(path.dirname(source));
  const old=fs.readFileSync(source,'utf8').replace(/^\uFEFF/,''); let next=old;
  for (const [before,after] of [
    ['127.0.0.1:3307/','127.0.0.1:13326/'],['127.0.0.1:3308/','127.0.0.1:13326/'],['127.0.0.1:3311/','127.0.0.1:13326/'],
    ['53ab727e-c847-11f1-9aaf-bad504fada53',env.uuid],['ffc96046-c893-11f1-8230-829c8c700e9e',env.uuid],
    ['e15a46faca0b',env.hostname],['cbede7f4b30d',env.hostname],
    ['kkiri-auth-mysql-test',env.container],['kkiri-user-mysql-test',env.container],
    [String.raw`kkiri\\_auth\\_test`,'kkiriausauth20261021'],
    ['kkiri_auth_test','kkiriausauth20261021'],['kkiri_test','ausauth20261021'],
    ['kkiriusertest','kkiriaususer20261021'],['kkiri_user_test','aususer20261021'],
    ['kkirisurveytest','kkiriaussurvey20261021'],['kkiri_survey_test','aussurvey20261021']
  ]) next=next.replaceAll(before,after);
  if (folder.endsWith('-multi-instance')) next=next.replace(/\b8081\b/g,'18091').replace(/\b8082\b/g,'18092');
  if (['AuthMultiInstance.java','UserMultiInstance.java'].includes(name)) {
    const auth=name.startsWith('Auth'), support=auth?'MultiSupport':'UserMultiSupport';
    const db=auth?'kkiri_auth_mi_a51021202601':'kkiri_user_mi_a51021202602';
    const account=auth?'kkiri_mi_a51021202601':'kkiri_umi_a51021202602';
    next=next.replace(`${support}.database="${auth?'kkiri_auth_mi_':'kkiri_user_mi_'}"+RUN;`,`${support}.database="${db}";`);
    next=next.replace(`${support}.account="${auth?'kkiri_mi_':'kkiri_umi_'}"+RUN;`,`${support}.account="${account}";`);
    next=next.replace(`${support}.password=randomSecret();`,`${support}.password=System.getenv("${auth?'AUS_AUTH_HTTP_PASSWORD':'AUS_USER_HTTP_PASSWORD'}");`);
    const start=next.indexOf('    static void provision()throws Exception {'),end=next.indexOf('    static void generateSchema()',start);
    assert(start>=0&&end>start);
    next=next.slice(0,start)+`    static void provision()throws Exception {
        try(var c=${support}.open()) {
            ${support}.grants(c);
            ${support}.check(${support}.db().equals("${db}")&&${support}.user().equals("${account}"),"Unapproved target");
            long tableCount=${support}.number(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()");
            if("UserSecurity".equals(System.getenv("AUS_HTTP_REUSE"))&&${!auth}) {
                ${support}.check(tableCount==31,"Expected current 31 mapped tables for focused reuse");
                for(var row:${support}.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                    String table=row.values().iterator().next().toString();${support}.check(table.matches("[a-z_]+"),"Unsafe table name");
                    ${support}.check(${support}.number(c,"SELECT COUNT(*) FROM "+table)==0,"Focused reuse requires every table empty");
                }
            } else ${support}.check(tableCount==0,"Approved new schema must be empty");
            ${support}.write(out.resolve("environment.json"),Map.of("precreatedApprovedSchema",true,"endpoint",${support}.url(),"identity",${support}.rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@session.time_zone zone"),"grants",${support}.rows(c,"SHOW GRANTS")));
        }
        provisioned=true;
    }
`+next.slice(end);
    if(!auth)next=next.replace('runUserScenarios();completed=true;','runUserScenarios();AusSurveyHttp.run();completed=true;');
    if(!auth){
      next=next.replace('names.size()==30,"Original schema table count changed"','names.size()==31,"Current isolated User schema table count changed"');
      next=next.replace('    static void applySchema()throws Exception {',`    static void applySchema()throws Exception {
        if("UserSecurity".equals(System.getenv("AUS_HTTP_REUSE"))) {
            try(var c=UserMultiSupport.open()) {
                UserMultiSupport.grants(c);
                tables=UserMultiSupport.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name").stream().map(x->x.values().iterator().next().toString()).toList();
                UserMultiSupport.check(tables.size()==entityCount,"Focused reuse entity/table count mismatch");
                initialSchema=schema(c);UserMultiSupport.write(out.resolve("initial-schema.json"),initialSchema);
            }
            return;
        }
`);
    }
  }
  const cases=s=>[...s.matchAll(/test\("([^"\n]+)"/g)].map(x=>x[1]);
  assert.deepEqual(cases(next),cases(old),name);
  fs.writeFileSync(path.join(dest,name),next);
  evidence.push({source:path.relative(root,source),sourceSha256:hash(fs.readFileSync(source)),generatedSha256:hash(next),unchangedCaseNames:cases(old)});
}
fs.writeFileSync(path.join(work,'source-adaptations.json'),JSON.stringify(evidence,null,2));
console.log(`Prepared ${files.length} sources for the five approved schemas; case names preserved.`);
