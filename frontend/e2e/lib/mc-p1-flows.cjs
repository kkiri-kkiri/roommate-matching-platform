const fs=require('node:fs');
const path=require('node:path');
const {test:base,expect,database}=require('../fixtures.cjs');
const shared=require('./mc-flows.cjs');
const {CATALOG}=require('../mc-p1-session.cjs');
const origin='http://127.0.0.1:18080/api';
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r});return {promise,resolve}};
const test=base.extend({
  isolatedDatabase:[async ({},use,info)=>{
    const id=info.title.split(' ')[0];if(!CATALOG.includes(id))throw new Error('Unknown P1 fixture before reset');
    process.env.E2E_MC_P1_CASE=id;
    process.env.E2E_CURRENT_TEST_ID=process.env.E2E_MC_PHASE+'-'+info.testId;
    const fixture=database('reset');
    try{await use(fixture)}finally{database('clean');delete process.env.E2E_MC_P1_CASE;}
  },{auto:true,timeout:120_000}],
  p1:[async ({isolatedDatabase:fixture,page,evidence},use,info)=>{
    const events=[],alerts=[],resources=[],requestIds=new WeakMap();let next=0;
    const file=path.join(process.env.E2E_EVIDENCE_DIR,`p1-${process.env.E2E_MC_PHASE}-${fixture.caseId}.jsonl`);
    const record=(kind,data={})=>{const row={kind,caseId:fixture.caseId,at:new Date().toISOString(),...data};events.push(row);fs.appendFileSync(file,JSON.stringify(row)+'\n');};
    const request=r=>{const u=new URL(r.url());if(!requestIds.has(r))requestIds.set(r,++next);return {requestId:requestIds.get(r),method:r.method(),path:u.pathname,
      query:Object.fromEntries([...u.searchParams].filter(([k])=>['page','size','sleepTime','from','to','categories','tab'].includes(k))),
      ...(['POST','PATCH','PUT'].includes(r.method())&&u.pathname.startsWith('/api/calendar/')?{payload:r.postDataJSON()}: {})};};
    page.on('request',r=>{if(r.url().startsWith(origin+'/'))record('browser-start',request(r));});
    page.on('response',r=>{if(r.url().startsWith(origin+'/'))record('browser-response',{...request(r.request()),status:r.status()});});
    page.on('requestfailed',r=>{if(r.url().startsWith(origin+'/'))record('browser-failure',{...request(r),reason:r.failure()?.errorText});});
    page.on('dialog',async d=>{alerts.push(d.message());record('dialog',{message:d.message()});await d.accept();});
    record('fixture',fixture);evidence.note('P1 independent fixture',fixture);
    const initialSnapshot=database('snapshot');
    record('initial-db-projection',{profileIds:initialSnapshot.profiles.map(p=>p.user_id),eventIds:initialSnapshot.events.map(e=>e.event_id)});
    const injection=async ({method,path:apiPath,predicate=()=>true,kind='failure',status=500,code='INTERNAL_SERVER_ERROR',message='P1 요청 실패',when=()=>true})=>{
      record('injection-plan',{method,path:apiPath,mode:kind,status,code});
      const ready=deferred(),released=deferred(),done=deferred();let matches=0,used=0,data,statusCode;
      const handler=async route=>{
        const r=route.request(),url=new URL(r.url());
        if(url.origin!=='http://127.0.0.1:18080'||url.pathname!=='/api'+apiPath||r.method()!==method||!predicate(url,r))return route.fallback();
        matches++;if(!when(matches,r))return route.fallback();used++;
        record('injection-start',{...request(r),mode:kind,upstream:!['failure','hold-failure'].includes(kind)});
        if(kind==='failure'||kind==='hold-failure'){
          ready.resolve();if(kind==='hold-failure')await released.promise;
          await route.fulfill({status,contentType:'application/json',body:JSON.stringify({success:false,errorCode:code,message,data:null})});
          record('synthetic-response',{...request(r),status,code,upstream:false});ready.resolve();done.resolve();return;
        }
        try{
          const response=await route.fetch({maxRedirects:0,maxRetries:0,timeout:15_000});
          statusCode=response.status();try{data=(await response.json()).data}catch{}
          record('upstream-complete',{...request(r),status:statusCode,ids:Array.isArray(data?.content)?data.content.map(p=>p.id):Array.isArray(data)?data.map(p=>p.id):data?.id});
          ready.resolve();await released.promise;
          if(kind==='loss'){await route.abort('failed');record('injected-loss',{...request(r),serverStatus:statusCode});}
          else if(kind==='delay-failure'){await route.fulfill({status,contentType:'application/json',body:JSON.stringify({success:false,errorCode:code,message,data:null})});record('synthetic-response',{...request(r),status,upstream:true});}
          else {await route.fulfill({response});record('released',{...request(r),status:statusCode});}
        }catch(error){record('route-cancelled',{...request(r),message:String(error.message).split('\n')[0]});ready.resolve();}
        finally{done.resolve();}
      };
      await page.route(origin+'/**',handler);
      const item={ready:ready.promise,done:done.promise,release:()=>released.resolve(),get data(){return data},get status(){return statusCode},get used(){return used},get matches(){return matches},
        dispose:async()=>{released.resolve();await page.unroute(origin+'/**',handler);if(used&&kind!=='failure')await done.promise;}};
      resources.push(item);return item;
    };
    const state={fixture,events,alerts,record,injection,snapshot:()=>database('snapshot')};
    try{await use(state)}finally{
      for(const resource of resources.reverse())await resource.dispose();
      const finalSnapshot=database('snapshot');
      record('final-db-projection',{eventIds:finalSnapshot.events.map(e=>e.event_id),likes:finalSnapshot.likes});
      if(fixture.caseId.startsWith('R07')||['R08-01','R08-02','R08-03'].includes(fixture.caseId)){
        expect(finalSnapshot.events).toEqual(initialSnapshot.events);expect(finalSnapshot.likes).toEqual(initialSnapshot.likes);
      }
      evidence.note('P1 observations',{caseId:fixture.caseId,alerts,events});
      fs.writeFileSync(file.replace(/\.jsonl$/,'.json'),JSON.stringify({caseId:fixture.caseId,fixture,alerts,events},null,2));
    }
  },{auto:true}],
});
async function fixedCalendar(page,iso){
  await page.addInitScript(value=>{
    const NativeDate=Date;let saved;try{saved=sessionStorage.getItem('__mcCalendarClock')}catch{}globalThis.__mcCalendarTime=saved||value;
    function CalendarDate(...args){if(!new.target)return NativeDate(...args);return Reflect.construct(NativeDate,args.length?args:[globalThis.__mcCalendarTime],new.target);}
    Object.setPrototypeOf(CalendarDate,NativeDate);CalendarDate.prototype=NativeDate.prototype;globalThis.Date=CalendarDate;
  },iso);
}
async function begin(page,p1,{calendar=false,recommended=false}={}){
  if(calendar)await fixedCalendar(page,p1.fixture.clock);
  await shared.login(page,shared.actor());
  await page.mouse.move(0,0);
  const data=calendar?await shared.calendar(page):await shared.navigate(page,recommended?'/roommates?tab=recommended':'/roommates',recommended?'/roommates/recommended':'/roommates');
  p1.record('initial-http-oracle',{mode:calendar?'calendar':recommended?'recommended':'all',data});
  if(recommended)expect(data.content.every(p=>p.matchScore>=60)).toBe(true);
  return data;
}
async function sleepChoice(page,value,{apply=true}={}){
  await page.getByRole('button',{name:'필터 열기',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'필터',exact:true}),input=dialog.getByRole('checkbox',{name:value,exact:true});
  for(const other of await dialog.getByRole('checkbox',{checked:true}).all()){
    if(await other.getAttribute('aria-label')!==value&&await other.getAttribute('value')!==value){
      const label=other.locator('xpath=ancestor::label[1]');if(!(await label.innerText()).includes(value))await label.click();
    }
  }
  if(!await input.isChecked())await input.locator('xpath=ancestor::label[1]').getByText(value,{exact:true}).click();
  await expect(input).toBeChecked();
  await dialog.getByRole('button',{name:apply?'적용':'필터 닫기',exact:true}).click();
}
async function realGet(page,url){return shared.get(page,url);}
async function cards(page,data){await shared.assertCards(page,data);}
async function settings(page){await page.getByRole('button',{name:'카테고리 설정',exact:true}).click();return page.getByRole('dialog',{name:'카테고리 설정',exact:true});}
async function addCategory(dialog,label,color='#abcdef',enabled=false){
  await dialog.getByRole('button',{name:'카테고리 추가',exact:true}).click();
  await dialog.locator('input[id^="category-name-"][value=""]').fill(label);
  await dialog.getByLabel(label+' 색상 설정',{exact:true}).fill(color);
  if(!enabled)await dialog.getByRole('checkbox',{name:label+' 표시',exact:true}).uncheck();
}
async function createDraft(page){
  await page.getByRole('button',{name:'일정 등록',exact:true}).click();const d=page.getByRole('dialog',{name:'새 일정 등록',exact:true});
  for(const [name,value] of Object.entries({title:'P1새초안',date:'2026-10-15',endDate:'2026-10-16',description:'P1 보존할 초안'}))await d.locator('#calendar-'+name).fill(value);
  await d.locator('#calendar-startTime').selectOption('09:00');await d.locator('#calendar-endTime').selectOption('10:00');await d.locator('#calendar-category').selectOption({label:'P1개인'});return d;
}
module.exports={test,expect,origin,begin,fixedCalendar,sleepChoice,realGet,cards,settings,addCategory,createDraft,...Object.fromEntries(['responseTo','login','actor','link','article','edit','browserRequest'].map(k=>[k,shared[k]]))};
