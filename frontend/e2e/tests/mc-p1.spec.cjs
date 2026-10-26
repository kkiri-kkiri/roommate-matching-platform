const {test,expect,origin,begin,fixedCalendar,sleepChoice,realGet,cards,settings,addCategory,createDraft,
  responseTo,link,article,edit}=require('../lib/mc-p1-flows.cjs');
test.setTimeout(120_000);
const matches=(path,method)=>r=>new URL(r.url()).pathname==='/api'+path&&r.request().method()===method;
const writes=(p1,path,method)=>p1.events.filter(e=>e.kind==='browser-start'&&e.path==='/api'+path&&e.method===method);
const alertText=async page=>(await page.getByRole('alert').allTextContents()).join('|');
const expectAlert=async(page,text)=>expect.poll(()=>alertText(page)).toContain(text);
const chip=(page,value)=>page.getByRole('button',{name:`취침시간 ${value} 필터 취소`,exact:true});
const likesPath='/users/me/liked-roommates?page=0&size=6';
const eventPath=id=>`/calendar/events/${id}`;

test('R07-01 P1: late filter response cannot replace the latest applied filter or a cancelled draft',async({page,p1})=>{
  await begin(page,p1);
  const delayed=await p1.injection({method:'GET',path:'/roommates',kind:'delay',predicate:u=>u.searchParams.get('sleepTime')==='22시 이전',when:n=>n===1});
  await sleepChoice(page,'22시 이전');await delayed.ready;
  const latest=await responseTo(page,'GET','/roommates',()=>sleepChoice(page,'23시 ~ 24시'));
  const data=(await latest.json()).data;expect(new URL(latest.url()).searchParams.getAll('sleepTime')).toEqual(['23시 ~ 24시']);
  expect(data.content.length).toBeGreaterThan(0);expect(data.content.map(x=>x.id)).not.toEqual(delayed.data.content.map(x=>x.id));
  delayed.release();await delayed.done;await cards(page,data);await expect(chip(page,'23시 ~ 24시')).toBeVisible();await expect(chip(page,'22시 이전')).toHaveCount(0);
  const count=writes(p1,'/roommates','GET').length;
  await sleepChoice(page,'22시 이전',{apply:false});
  expect(writes(p1,'/roommates','GET')).toHaveLength(count);await cards(page,data);await expect(chip(page,'23시 ~ 24시')).toBeVisible();
  expect(data).toEqual(await realGet(page,'/roommates?page=0&size=9&sleepTime='+encodeURIComponent('23시 ~ 24시')));
});

test('R07-02 P1: rapid tab changes retain only the latest tab response',async({page,p1})=>{
  await begin(page,p1);
  const old=await p1.injection({method:'GET',path:'/roommates/recommended',kind:'delay',when:n=>n===1});
  await page.getByRole('tab',{name:'추천 프로필',exact:true}).click();await old.ready;
  const latest=await responseTo(page,'GET','/roommates',()=>page.getByRole('tab',{name:'전체 프로필',exact:true}).click());
  const data=(await latest.json()).data;old.release();await old.done;
  await expect(page.getByRole('tab',{name:'전체 프로필',exact:true})).toHaveAttribute('aria-selected','true');
  expect(new URL(page.url()).searchParams.has('tab')).toBe(false);await cards(page,data);
  expect(data).toEqual(await realGet(page,'/roommates?page=0&size=9'));
});

test('R07-03 P1: delayed second page cannot overwrite a recommended page-zero transition',async({page,p1})=>{
  await begin(page,p1);
  const old=await p1.injection({method:'GET',path:'/roommates',kind:'delay',predicate:u=>u.searchParams.get('page')==='1',when:n=>n===1});
  await page.getByRole('button',{name:'2페이지',exact:true}).click();await old.ready;
  const response=await responseTo(page,'GET','/roommates/recommended',()=>page.getByRole('tab',{name:'추천 프로필',exact:true}).click());
  expect(new URL(response.url()).searchParams.get('page')).toBe('0');const data=(await response.json()).data;
  old.release();await old.done;await cards(page,data);await expect(page.getByRole('button',{name:'2페이지',exact:true})).toHaveCount(0);
  expect(new URL(page.url()).searchParams.get('tab')).toBe('recommended');
});

test('R07-04 P1: detail back and forward follow actual history and tab URL',async({page,p1})=>{
  const data=await begin(page,p1,{recommended:true});const target=data.content.find(x=>x.nickname.startsWith('P1후보'));
  await responseTo(page,'GET',`/roommates/${target.id}`,()=>link(page,target.nickname).click());
  await expect(page.getByRole('dialog',{name:target.nickname,exact:true})).toBeVisible();
  await page.goBack();await expect(page).toHaveURL(/\/roommates\?tab=recommended$/);await cards(page,data);
  await page.goForward();await expect(page.getByRole('dialog',{name:target.nickname,exact:true})).toBeVisible();
  await page.goBack();await cards(page,await realGet(page,'/roommates/recommended?page=0&size=9'));
  p1.record('history-restored',{targetId:target.id,url:page.url()});
});

test('R07-05 P1: a previous mount response cannot overwrite back navigation reload',async({page,p1})=>{
  await begin(page,p1);
  const old=await p1.injection({method:'GET',path:'/roommates',kind:'delay',when:n=>n===1});
  await page.goto('/roommates');await old.ready;await page.goto('/calendar');
  const latest=await responseTo(page,'GET','/roommates',()=>page.goBack());const data=(await latest.json()).data;
  old.release();await old.done;await cards(page,data);expect(new URL(page.url()).pathname).toBe('/roommates');
  expect(data).toEqual(await realGet(page,'/roommates?page=0&size=9'));
});

for(const [id,status,code] of [['01',503,'AI_GATEWAY_UNAVAILABLE'],['02',429,'AI_ANALYSIS_RATE_LIMITED'],['03',503,'AI_ANALYSIS_BUSY']]){
  test(`R08-${id} P1: synthetic ${code} releases loading and permits explicit local FixedAi recovery`,async({page,p1})=>{
    const data=await begin(page,p1,{recommended:true}),target=data.content.find(x=>x.id===p1.fixture.targetId);expect(target).toBeTruthy();
    const path=`/roommates/${target.id}/ai-analysis`,failure=await p1.injection({method:'POST',path,status,code,message:code});
    await link(page,target.nickname).focus();await expect(article(page,target.nickname).getByRole('alert')).toHaveText(code);
    await expect(article(page,target.nickname).getByRole('alert')).toHaveAttribute('aria-busy','false');expect(failure.used).toBe(1);
    await page.getByRole('tab',{name:'전체 프로필',exact:true}).focus();await failure.dispose();
    const response=await responseTo(page,'POST',path,()=>link(page,target.nickname).focus());expect(response.status()).toBe(200);
    const analysis=(await response.json()).data;expect(analysis.matchScore).toBe(target.matchScore);
    await expect(article(page,target.nickname).getByRole('status')).toHaveText('E2E 테스트용 고정 분석입니다.');
    expect((await realGet(page,`/roommates/${target.id}`)).matchScore).toBe(target.matchScore);
  });
}

for(const [id,liked,loss] of [['04',false,false],['05',true,false],['06',false,true],['07',true,true]]){
  test(`R08-${id} P1: ${liked?'DELETE':'POST'} like ${loss?'committed response loss':'pre-upstream failure'} recovers without duplicate writes`,async({page,p1})=>{
    const data=await begin(page,p1),target=data.content.find(x=>x.id===p1.fixture.targetId);expect(target).toBeTruthy();
    const method=liked?'DELETE':'POST',path=`/roommates/${target.id}/likes`,count=(await realGet(page,likesPath)).totalElements;
    const before=p1.snapshot().likes,button=article(page,target.nickname).getByRole('button',{name:/프로필 좋아요/});
    await expect(button).toHaveAttribute('aria-pressed',String(liked));
    const failure=await p1.injection({method,path,kind:loss?'loss':'failure',when:n=>n===1});
    const failed=page.waitForEvent('requestfailed',{predicate:r=>r.url()===origin+path&&r.method()===method});
    if(!loss)failed.catch(()=>{});
    await button.click();
    if(loss){await failure.ready;expect(failure.status).toBe(200);const committed=p1.snapshot().likes;
      expect(committed.filter(x=>x.user_id===p1.fixture.userId&&x.target_user_id===target.id)).toHaveLength(liked?0:1);
      p1.record('mysql-committed-before-loss',{likes:committed});failure.release();await failed;}
    await expect(article(page,target.nickname).getByRole('alert')).toBeVisible();await expect(button).toBeEnabled();
    await expect(button).toHaveAttribute('aria-pressed',String(liked));expect(writes(p1,path,method)).toHaveLength(1);
    if(!loss){expect(p1.snapshot().likes).toEqual(before);expect((await realGet(page,likesPath)).totalElements).toBe(count);}
    await failure.dispose();
    if(loss){await page.reload();await expect(article(page,target.nickname).getByRole('button',{name:/프로필 좋아요/})).toHaveAttribute('aria-pressed',String(!liked));}
    else {const response=await responseTo(page,method,path,()=>button.click());expect(response.status()).toBe(200);await expect(button).toHaveAttribute('aria-pressed',String(!liked));}
    expect(writes(p1,path,method)).toHaveLength(loss?1:2);
    expect((await realGet(page,likesPath)).totalElements).toBe(count+(liked?-1:1));
    expect(p1.snapshot().likes.filter(x=>x.user_id===p1.fixture.userId&&x.target_user_id===target.id)).toHaveLength(liked?0:1);
  });
}

async function selectCalendarDay(page,date,expected){
  const title=await page.locator('#calendar-month-title').innerText(),parts=title.match(/(\d+)년\s*(\d+)월/);
  expect(parts).toBeTruthy();
  let month=Number(parts[1])*12+Number(parts[2]);const target=Number(date.slice(0,4))*12+Number(date.slice(5,7));
  while(month!==target){const step=month<target?1:-1;await page.getByRole('button',{name:step>0?'다음 달':'이전 달',exact:true}).click();month+=step;
    const year=Math.floor((month-1)/12),number=(month-1)%12+1;await expect(page.locator('#calendar-month-title')).toHaveText(`${year}년 ${number}월`);}
  const cell=page.locator(`.fc-daygrid-day[data-date="${date}"]`);
  await cell.getByRole('button').click();
  const current=page.locator(`.fc-daygrid-day[data-date="${date}"]`);if(expected.length)await expect(current).toHaveClass(/fc-day-has-events/);
  await expect(page.getByRole('button',{name:/,.*상세 보기$/})).toHaveCount(expected.length);
  for(const item of expected){const button=page.getByRole('button',{name:new RegExp('^'+item.title+',.*상세 보기$')});await expect(button).toBeVisible();
    if(item.title==='P1시작만')await expect(button).toHaveAccessibleName(/AM 09:15 – 종료 시간 미정/);
    if(item.title==='P1종료만')await expect(button).toHaveAccessibleName(/시작 시간 미정 – AM 10:15/);}
}
async function verifyMini(page,date,expected){
  await page.goto('/');const mini=page.getByLabel('캘린더',{exact:true});
  let shown=await mini.locator('strong').allTextContents();
  const initial=await page.evaluate(()=>new Date().toISOString());
  const from=Number(initial.slice(0,4))*12+Number(initial.slice(5,7)),to=Number(date.slice(0,4))*12+Number(date.slice(5,7));
  for(let n=0;n<Math.abs(to-from);n++)await mini.getByRole('button',{name:to>from?'다음 달 보기':'이전 달 보기',exact:true}).click();
  await expect(mini.getByRole('button',{name:`${Number(date.slice(5,7))}월 ${Number(date.slice(8))}일${expected.length?', 일정 있음':''}`,exact:true})).toBeVisible();
  await mini.getByRole('button',{name:`${Number(date.slice(5,7))}월 ${Number(date.slice(8))}일${expected.length?', 일정 있음':''}`,exact:true}).click();
  for(const item of expected){const text=mini.getByText(item.title,{exact:true});await expect(text).toBeVisible();
    if(item.title==='P1시작만')await expect(text.locator('xpath=..')).toContainText('AM 09:15 – 종료 시간 미정');
    if(item.title==='P1종료만')await expect(text.locator('xpath=..')).toContainText('시작 시간 미정 – AM 10:15');}
  return shown;
}
for(const id of ['01','02','03','04','05']){
  test(`C05-${id} P1: fixed calendar boundary dates preserve API MySQL and main calendar placement`,async({page,p1})=>{
    await begin(page,p1,{calendar:true});const before=p1.snapshot().events;
    expect(Math.abs(await page.evaluate(()=>Date.now())-Date.now())).toBeLessThan(30_000);
    const dates=[p1.fixture.from,p1.fixture.to];if(id==='02')dates.splice(1,0,'2028-02-29');
    if(id==='05')dates.splice(1,0,'2026-10-31','2026-11-01');
    for(const date of [...new Set(dates)]){
      const expected=await realGet(page,`/calendar/events?from=${date}&to=${date}`);
      expect(expected.some(x=>x.id===p1.fixture.events.P1다일)).toBe(true);
      expect(expected.some(x=>['P1이전','P1이후'].includes(x.title))).toBe(false);
      await selectCalendarDay(page,date,expected);p1.record('date-id-oracle',{date,ids:expected.map(x=>x.id),titles:expected.map(x=>x.title)});
    }
    for(const date of [...new Set(dates)])await verifyMini(page,date,await realGet(page,`/calendar/events?from=${date}&to=${date}`));
    if(id==='02'){
      await page.evaluate(()=>{sessionStorage.setItem('__mcCalendarClock','2026-02-28T12:00:00+09:00')});await page.goto('/calendar');
      await expect(page.locator('.fc-daygrid-day[data-date="2026-02-28"]')).toBeVisible();await expect(page.locator('.fc-daygrid-day[data-date="2026-02-29"]')).toHaveCount(0);
    }
    if(id==='04'){
      await page.goto('/calendar');await page.getByRole('button',{name:'일정 등록',exact:true}).click();let d=page.getByRole('dialog',{name:'새 일정 등록',exact:true});
      await expect(d.locator('#calendar-date')).toHaveValue('2027-01-01');await expect(d.locator('#calendar-startTime')).toHaveValue('00:00');await expect(d.locator('#calendar-endTime')).toHaveValue('00:15');
      await expect(d.locator('#calendar-startTime option:checked')).toHaveText('AM 12:00');await d.getByRole('button',{name:'취소',exact:true}).click();
      await page.evaluate(()=>{globalThis.__mcCalendarTime='2027-01-01T00:00:00+09:00'});await page.getByRole('button',{name:'오늘',exact:true}).click();
      await page.getByRole('button',{name:'일정 등록',exact:true}).click();d=page.getByRole('dialog',{name:'새 일정 등록',exact:true});await expect(d.locator('#calendar-startTime')).toHaveValue('00:15');await expect(d.locator('#calendar-endTime')).toHaveValue('00:30');
      await d.getByRole('button',{name:'취소',exact:true}).click();
    }
    if(id==='05'){
      const boundary=await realGet(page,`/calendar/events?from=${p1.fixture.from}&to=${p1.fixture.to}`);
      expect(boundary.some(x=>x.title==='P1시작만'&&x.endTime===null)).toBe(true);expect(boundary.some(x=>x.title==='P1종료만'&&x.startTime===null)).toBe(true);
      p1.record('partial-date-boundary',{events:boundary});
    }
    expect(p1.snapshot().events).toEqual(before);
    expect(writes(p1,'/calendar/events','POST')).toHaveLength(0);
  });
}

async function assertSavedSettings(page,p1,labels){
  const fresh=await realGet(page,'/calendar/categories'),db=p1.snapshot();
  for(const label of labels){const c=fresh.find(x=>x.label===label);expect(c).toBeTruthy();const setting=db.categorySettings.find(x=>x.category_id===c.id&&x.user_id===p1.fixture.userId);
    expect(setting).toMatchObject({display_name:label,color:c.color,is_enabled:c.enabled});}
  p1.record('settings-fresh-and-mysql',{fresh,settings:db.categorySettings});
}

test('C06-01 P1: second category creation failure retains the first server ID and draft',async({page,p1})=>{
  await begin(page,p1,{calendar:true});const d=await settings(page);await addCategory(d,'P1신규A');await addCategory(d,'P1신규B','#fedcba',true);
  const failure=await p1.injection({method:'POST',path:'/calendar/categories/custom',when:n=>n===2});
  await d.getByRole('button',{name:'설정 저장',exact:true}).click();await expectAlert(page,'P1 요청 실패');
  const first=p1.snapshot().categories.find(x=>x.default_name==='P1신규A');expect(first).toBeTruthy();
  await expect(d.locator('input[value="P1신규A"]')).toBeVisible();await expect(d.getByRole('checkbox',{name:'P1신규A 표시',exact:true})).not.toBeChecked();
  await failure.dispose();await d.getByRole('button',{name:'설정 저장',exact:true}).click();await expect(d).toHaveCount(0);
  expect(writes(p1,'/calendar/categories/custom','POST').filter(x=>x.payload.label==='P1신규A')).toHaveLength(1);
  expect(writes(p1,'/calendar/categories/custom','POST').filter(x=>x.payload.label==='P1신규B')).toHaveLength(2);
  await assertSavedSettings(page,p1,['P1신규A','P1신규B']);
});

test('C06-02 P1: a delayed settings failure blocks duplicate submission and retries only PUT',async({page,p1})=>{
  await begin(page,p1,{calendar:true});const categories=await realGet(page,'/calendar/categories'),empty=categories.find(x=>x.label==='P1빈A');
  const d=await settings(page);await addCategory(d,'P1신규A');await d.getByRole('button',{name:'P1빈A 삭제',exact:true}).click();
  const failure=await p1.injection({method:'PUT',path:'/calendar/categories',kind:'hold-failure',when:n=>n===1});
  await d.getByRole('button',{name:'설정 저장',exact:true}).dblclick();await failure.ready;await expect(d.getByRole('button',{name:'설정 저장',exact:true})).toBeDisabled();await page.keyboard.press('Enter');
  expect(writes(p1,'/calendar/categories','PUT')).toHaveLength(1);failure.release();await failure.done;await expectAlert(page,'P1 요청 실패');
  await failure.dispose();await d.getByRole('button',{name:'설정 저장',exact:true}).click();await expect(d).toHaveCount(0);
  expect(writes(p1,'/calendar/categories/custom','POST')).toHaveLength(1);expect(writes(p1,`/calendar/categories/${empty.id}`,'DELETE')).toHaveLength(1);expect(writes(p1,'/calendar/categories','PUT')).toHaveLength(2);
  await assertSavedSettings(page,p1,['P1신규A']);
});

test('C06-03 P1: partial deletion plus failed recovery GET does not replay a committed DELETE',async({page,p1})=>{
  await begin(page,p1,{calendar:true});const categories=await realGet(page,'/calendar/categories'),a=categories.find(x=>x.label==='P1빈A'),b=categories.find(x=>x.label==='P1빈B');
  const d=await settings(page);await addCategory(d,'P1신규A');await d.getByRole('button',{name:'P1빈A 삭제',exact:true}).click();await d.getByRole('button',{name:'P1빈B 삭제',exact:true}).click();
  const failure=await p1.injection({method:'DELETE',path:`/calendar/categories/${b.id}`,when:n=>n===1});
  const refresh=await p1.injection({method:'GET',path:'/calendar/categories',when:n=>n===1});
  await d.getByRole('button',{name:'설정 저장',exact:true}).click();await expectAlert(page,'P1 요청 실패');
  expect(refresh.used).toBe(1);expect(p1.snapshot().categories.some(x=>x.category_id===a.id)).toBe(false);
  await failure.dispose();await refresh.dispose();
  const next=page.waitForResponse(r=>matches(`/calendar/categories/${a.id}`,'DELETE')(r)||matches('/calendar/categories','PUT')(r));
  await d.getByRole('button',{name:'설정 저장',exact:true}).click();const observed=await next;
  p1.record('partial-retry-observed',{path:new URL(observed.url()).pathname,status:observed.status(),mysql:p1.snapshot().categories});
  expect(writes(p1,`/calendar/categories/${a.id}`,'DELETE')).toHaveLength(1);
  await expect(d).toHaveCount(0);expect(writes(p1,'/calendar/categories/custom','POST')).toHaveLength(1);
  await assertSavedSettings(page,p1,['P1신규A']);
});

test('C06-04 P1: actual server rejects deleting a category used only outside the current month',async({page,p1})=>{
  await begin(page,p1,{calendar:true});const current=p1.events.filter(x=>x.kind==='browser-start'&&x.path==='/api/calendar/events').at(-1);expect(current.query.to<'2026-11-15').toBe(true);
  const categories=await realGet(page,'/calendar/categories'),category=categories.find(x=>x.label==='P1다른달'),before=p1.snapshot();
  const d=await settings(page);await expect(d.getByRole('button',{name:'P1다른달 삭제',exact:true})).toBeEnabled();await d.getByRole('button',{name:'P1다른달 삭제',exact:true}).click();
  const waiting=page.waitForResponse(matches(`/calendar/categories/${category.id}`,'DELETE'));
  await d.getByRole('button',{name:'설정 저장',exact:true}).click();const response=await waiting;
  expect(response.status()).toBe(400);expect((await response.json()).errorCode).toBe('INVALID_REQUEST');await expect.poll(()=>alertText(page)).not.toBe('');
  const after=p1.snapshot();for(const key of ['events','categories','categorySettings'])expect(after[key]).toEqual(before[key]);
  await d.getByRole('button',{name:'취소',exact:true}).click();const reopened=await settings(page);await expect(reopened.locator('input[value="P1다른달"]')).toBeVisible();
});

for(const [id,path] of [['01','/calendar/events'],['02','/calendar/categories']]){
  test(`C07-${id} P1: independent ${path} GET error shows retry and recovers real data`,async({page,p1})=>{
    await begin(page,p1,{calendar:true});const failure=await p1.injection({method:'GET',path});await page.reload();await expectAlert(page,'P1 요청 실패');
    expect(writes(p1,'/calendar/events','POST')).toHaveLength(0);await expect(page.getByRole('button',{name:/^P1기존,.*상세 보기$/})).toHaveCount(0);
    await failure.dispose();await page.locator('aside').getByRole('button',{name:'다시 시도',exact:true}).click();
    await page.getByRole('button',{name:'이번 달 전체',exact:true}).click();await expect(page.getByRole('button',{name:/^P1기존,.*상세 보기$/})).toBeVisible();
    expect((await realGet(page,'/calendar/categories')).some(x=>x.label==='P1개인')).toBe(true);p1.snapshot();
  });
}

test('C07-03 P1: previous-month event response cannot overwrite the latest month',async({page,p1})=>{
  await begin(page,p1,{calendar:true});const old=await p1.injection({method:'GET',path:'/calendar/events',kind:'delay',when:n=>n===1});
  await page.getByRole('button',{name:'이전 달',exact:true}).click();await old.ready;
  const response=await responseTo(page,'GET','/calendar/events',()=>page.getByRole('button',{name:'다음 달',exact:true}).click());const data=(await response.json()).data;
  old.release();await old.done;await expect(page.locator('#calendar-month-title')).toHaveText('2026년 10월');
  await expect(page.getByRole('button',{name:/^P1기존,.*상세 보기$/})).toBeVisible();expect(data.some(x=>x.title==='P1기존')).toBe(true);
  expect(data).toEqual(await realGet(page,new URL(response.url()).pathname.replace('/api','')+new URL(response.url()).search));p1.snapshot();
});

test('C07-04 P1: delayed category loading and month navigation converge on latest settings and month',async({page,p1})=>{
  await begin(page,p1,{calendar:true});const old=await p1.injection({method:'GET',path:'/calendar/categories',kind:'delay',when:n=>n===1});
  await page.reload();await old.ready;
  const response=await responseTo(page,'GET','/calendar/events',()=>page.getByRole('button',{name:'다음 달',exact:true}).click());
  old.release();await old.done;await page.getByRole('button',{name:'이번 달 전체',exact:true}).click();
  await expect(page.locator('#calendar-month-title')).toHaveText('2026년 11월');await expect(page.getByRole('button',{name:/^P1다음달,.*상세 보기$/})).toBeVisible();
  expect((await response.json()).data.some(x=>x.title==='P1다음달')).toBe(true);expect((await realGet(page,'/calendar/categories')).some(x=>x.label==='P1개인')).toBe(true);p1.snapshot();
});

for(const [id,method] of [['05','POST'],['06','PATCH'],['07','DELETE']]){
  test(`C07-${id} P1: failed event ${method} preserves draft or detail and retries explicitly`,async({page,p1})=>{
    await begin(page,p1,{calendar:true});const before=p1.snapshot().events,existing=p1.fixture.events.P1기존;
    let d;if(method==='POST')d=await createDraft(page);else if(method==='PATCH'){d=await edit(page,'P1기존');await d.locator('#calendar-title').fill('P1제목초안');}
    else {await page.getByRole('button',{name:/^P1기존,.*상세 보기$/}).click();d=page.getByRole('dialog',{name:'일정 상세',exact:true});}
    const path=method==='POST'?'/calendar/events':eventPath(existing),failure=await p1.injection({method,path});
    const name=method==='POST'?'일정 등록':method==='PATCH'?'변경 내용 저장':'삭제';await d.getByRole('button',{name,exact:true}).click();
    await expect.poll(()=>p1.alerts.length).toBe(1);await expect(d).toBeVisible();await expect(d.getByRole('button',{name,exact:true})).toBeEnabled();expect(p1.snapshot().events).toEqual(before);
    if(method==='POST'){await expect(d.locator('#calendar-title')).toHaveValue('P1새초안');await expect(d.locator('#calendar-description')).toHaveValue('P1 보존할 초안');await expect(d.locator('#calendar-endDate')).toHaveValue('2026-10-16');}
    if(method==='PATCH')await expect(d.locator('#calendar-title')).toHaveValue('P1제목초안');
    await failure.dispose();const response=await responseTo(page,method,path,()=>d.getByRole('button',{name,exact:true}).click());expect(response.status()).toBe(200);await expect(d).toHaveCount(0);
    const after=p1.snapshot().events;
    if(method==='POST')expect(after.filter(x=>x.title==='P1새초안')).toHaveLength(1);
    if(method==='PATCH'){const row=after.find(x=>x.event_id===existing),original=before.find(x=>x.event_id===existing);expect(row.title).toBe('P1제목초안');expect({...row,title:original.title}).toEqual(original);}
    if(method==='DELETE')expect(after.some(x=>x.event_id===existing)).toBe(false);
    expect(writes(p1,path,method)).toHaveLength(2);await realGet(page,'/calendar/events?from=2026-10-01&to=2026-11-30');
  });
}

for(const [id,method] of [['08','POST'],['09','PATCH'],['10','DELETE']]){
  test(`C07-${id} P1: delayed committed event ${method} blocks duplicate UI submission`,async({page,p1})=>{
    await begin(page,p1,{calendar:true});const existing=p1.fixture.events.P1기존,before=p1.snapshot().events;
    let d;if(method==='POST')d=await createDraft(page);else if(method==='PATCH'){d=await edit(page,'P1기존');await d.locator('#calendar-title').fill('P1지연수정');}
    else{await page.getByRole('button',{name:/^P1기존,.*상세 보기$/}).click();d=page.getByRole('dialog',{name:'일정 상세',exact:true});}
    const path=method==='POST'?'/calendar/events':eventPath(existing),hold=await p1.injection({method,path,kind:'delay',when:n=>n===1});
    const name=method==='POST'?'일정 등록':method==='PATCH'?'변경 내용 저장':'삭제',button=d.getByRole('button',{name,exact:true});
    const completed=page.waitForResponse(matches(path,method));await button.dblclick();await hold.ready;expect(hold.status).toBe(200);
    await expect(button).toBeDisabled();await page.keyboard.press('Enter');expect(writes(p1,path,method)).toHaveLength(1);
    p1.record('committed-while-ui-pending',{method,events:p1.snapshot().events});hold.release();expect((await completed).status()).toBe(200);await expect(d).toHaveCount(0);
    expect(p1.alerts.filter(x=>x.includes(method==='POST'?'등록했어요':method==='PATCH'?'수정했어요':'삭제했어요'))).toHaveLength(1);
    const after=p1.snapshot().events;
    if(method==='POST')expect(after.filter(x=>x.title==='P1새초안')).toHaveLength(1);
    if(method==='PATCH'){const row=after.find(x=>x.event_id===existing),original=before.find(x=>x.event_id===existing);expect(row.title).toBe('P1지연수정');expect({...row,title:original.title}).toEqual(original);}
    if(method==='DELETE')expect(after.some(x=>x.event_id===existing)).toBe(false);
    expect(writes(p1,path,method)).toHaveLength(1);await realGet(page,'/calendar/events?from=2026-10-01&to=2026-11-30');
  });
}
