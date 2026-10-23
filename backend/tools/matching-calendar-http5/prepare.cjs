// Generate local adapters only; original tools and application sources remain unchanged.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../../..'),work=path.join(root,'backend/build/matching-calendar-http5'),dest=path.join(work,'sources');
fs.mkdirSync(dest,{recursive:true});
if(fs.existsSync(path.join(work,'execution-started.json'))){
 assert.equal(process.env.MC5_PREPARE_RECOVERY,'true','Execution started; only guarded preparation for the zero-HTTP startup failure is allowed.');
 const summary=JSON.parse(fs.readFileSync(path.join(work,'P0/matching/summary.json'),'utf8'));assert(summary.httpRequests===0&&summary.processesStopped&&summary.allTablesEmpty&&summary.ddlUnchanged,'Recovery preconditions failed');
}
const sha=b=>crypto.createHash('sha256').update(b).digest('hex'),changes=[];
const replace=(s,a,b)=>{assert(s.includes(a),`Adapter drift: ${a}`);return s.replaceAll(a,b);};
function adapt(file,fn=x=>x){const bytes=fs.readFileSync(path.join(root,file)),result=fn(bytes.toString('utf8'));fs.writeFileSync(path.join(dest,path.basename(file)),result);changes.push({source:file,sourceSha256:sha(bytes),generatedSha256:sha(result)});}
const fixedGuard='check(db().equals("kkirimc5http20261022")&&user().equals("mc5http20261022"),"Fixed target required");';
adapt('backend/tools/matching-mysql-multi-instance/MatchingMultiSupport.java',s=>{
 s=replace(s,'kkiri_matching_mi_[a-f0-9]{12}','kkirimc5http20261022');s=replace(s,'kkiri_kmi_[a-f0-9]{12}','mc5http20261022');
 s=replace(s,'127.0.0.1:3311/','127.0.0.1:13348/');s=replace(s,'?connectionTimeZone=UTC','?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC');
 s=replace(s,'identity(c);check(db()',fixedGuard+'identity(c);check(db()');
 s=replace(s,'e.put("frames",', 'if(t instanceof org.springframework.beans.factory.BeanCreationException b)e.put("bean",b.getBeanName());if(t instanceof org.springframework.beans.factory.NoSuchBeanDefinitionException b)e.put("missingType",Objects.toString(b.getResolvableType(),""));e.put("frames",');return s;
});
adapt('backend/tools/matching-mysql-multi-instance/MatchingMultiInstance.java',s=>replace(replace(s,'18081','18095'),'18082','18095'));
adapt('backend/tools/matching-mysql-multi-instance/MatchingExtraChecks.java'); // compile-only dependency; its run() is never invoked
adapt('backend/src/test/java/com/kkirikkiri/survey/SurveyTestData.java');
adapt('backend/tools/matching-mysql-multi-instance/MatchingMultiServer.java',s=>{
 s=replace(replace(s,'18081','18095'),'18082','18095');
 s=replace(s,'static final AtomicReference<CountDownLatch> gate=', 'static volatile HikariDataSource observedPool; static final AtomicReference<CountDownLatch> gate=');
 s=replace(s,'return p;}','observedPool=p;return p;}');
 s=replace(s,'r.removeBeanDefinition(n);','{if(r.containsSingleton(n))r.destroySingleton(n);r.removeBeanDefinition(n);}');
 s=replace(s,'@Bean static BeanFactoryPostProcessor noSchedules()', '@Bean static BeanFactoryPostProcessor diagnosticDefinitions(){return f->event("jpaDefinitions",Arrays.stream(f.getBeanDefinitionNames()).filter(n->n.toLowerCase(Locale.ROOT).contains("entitymanager")).map(n->Map.of("name",n,"type",Objects.toString(f.getBeanDefinition(n).getBeanClassName(),"factory-method"))).toList());} @Bean static BeanFactoryPostProcessor noSchedules()');
 s=replace(s,'finally{var l=gate.getAndSet(null);if(l!=null)l.countDown();}', 'finally{var l=gate.getAndSet(null);if(l!=null)l.countDown();event("closed",Map.of("poolClosed",observedPool!=null&&observedPool.isClosed()));}');
 s=replace(s,'p.put("logging.level.root","OFF");','p.put("app.community.image-cleanup.enabled","false");p.put("app.community.image-directory",System.getenv("MC5_IMAGES"));p.put("logging.level.root","OFF");');
 // Any accidental attempt to invoke the real school crawler fails before transport.
 s=replace(s,'@Bean JavaMailSender noMail()', '@Bean @Primary com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler noSchool(){return new com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler(){@Override public java.util.List<com.kkirikkiri.calendar.crawler.CrawledAcademicEvent> crawl(){throw new AssertionError("Real school transport forbidden");}};} @Bean JavaMailSender noMail()');return s;
});
adapt('backend/tools/calendar-mysql-multi-instance/CalendarMultiSupport.java',s=>{
 const begin=s.indexOf('    static String db()'),end=s.indexOf('    static DataSource source()');assert(begin>=0&&end>begin);
 return s.slice(0,begin)+`    static String db(){check("kkirimc5http20261022".equals(System.getenv("CM_DB")),"Wrong schema");return System.getenv("CM_DB");}
    static String account(){check("mc5http20261022".equals(System.getenv("CM_USER")),"Wrong account");return System.getenv("CM_USER");}
    static String zone(){return "UTC";}
    static String url(){return "jdbc:mysql://127.0.0.1:13348/"+db()+"?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=20000";}
    static Connection open()throws SQLException{var c=DriverManager.getConnection(url(),account(),System.getenv("CM_PASSWORD"));try{guard(c);return c;}catch(Throwable t){c.close();throw t;}}
    static void guard(Connection c)throws SQLException{var r=rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname host,@@session.time_zone zone").getFirst();check(db().equals(r.get("db"))&&(account()+"@%").equals(r.get("account"))&&"8.0.46".equals(r.get("version"))&&Objects.equals(System.getenv("KM_UUID"),r.get("uuid"))&&"kkiri-mc-http5-mysql-20261022".equals(r.get("host"))&&Set.of("UTC","+00:00").contains(r.get("zone")),"MySQL identity mismatch");}
`+s.slice(end).replace('GRANT SELECT, INSERT, UPDATE, DELETE ON','GRANT ALL PRIVILEGES ON');
});
adapt('backend/tools/calendar-mysql-multi-instance/CalendarMultiInstance.java',s=>{
 s=replace(replace(s,'8081','18096'),'8082','18096');
 const begin=s.indexOf('else if(diagnosticLines++<80)'),end=s.indexOf('}}}catch(Exception ignored)',begin);assert(begin>0&&end>begin);
 return s.slice(0,begin)+s.slice(end+1); // Ignore unstructured application output; structured redacted events only.
});
adapt('backend/tools/calendar-mysql-multi-instance/CalendarMultiServer.java',s=>{
 s=replace(replace(s,'8081','18096'),'8082','18096');
 s=replace(s,'p.put("logging.level.root","OFF");','p.put("app.community.image-cleanup.enabled","false");p.put("app.community.image-directory",System.getenv("MC5_IMAGES"));p.put("logging.level.root","OFF");');
 s=replace(s,'finally{path.remove();connectionId.remove();}', 'finally{event("http",Map.of("method",req.getMethod(),"path",req.getRequestURI(),"status",res.getStatus()));path.remove();connectionId.remove();}');
 return s;
});
for(const f of ['MatchingHttp5.java','CalendarHttp5.java','McHttp5Audit.java'])adapt('backend/tools/matching-calendar-http5/'+f);
fs.writeFileSync(path.join(work,'source-adaptations.json'),JSON.stringify(changes,null,2)+'\n');
console.log(JSON.stringify({generated:changes.length,dbConnections:0,httpRequests:0}));
