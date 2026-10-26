// Execute the new production list controller in an isolated browser-like VM.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const list=fs.readFileSync(path.join(__dirname,'../templates/v1_worksites.html'),'utf8');
const code=list.slice(list.indexOf('// One list refresh at a time;'),list.indexOf('loadResponsibleTbCandidates();loadWorksites();'));
(async()=>{
 const handlers={},timers=new Map(),sources=[];let next=0,loads=0,active=0,maxActive=0,release=null;
 class EventSource{constructor(url){this.url=url;this.handlers={};sources.push(this);}addEventListener(n,fn){this.handlers[n]=fn;}close(){this.closed=true;}}
 const ctx=vm.createContext({EventSource,userRole:'USER',window:{EventSource,addEventListener(n,f){handlers[n]=f;}},
  document:{hidden:false},tbScopedUrl:x=>x,console,
  setTimeout(f){timers.set(++next,f);return next;},clearTimeout(id){timers.delete(id);},
  setInterval(f){handlers.interval=f;return 999;},clearInterval(){handlers.cleared=true;},
  async loadWorksites(){loads++;active++;maxActive=Math.max(maxActive,active);if(release===null&&loads===1)await new Promise(r=>release=r);active--;}
 });
 vm.runInContext(code,ctx);
 assert.equal(sources.length,1);assert.match(sources[0].url,/entity_types=WORKSITE,WORKSITE_MEMBERSHIP,WORKSITE_INVITATION/);
 const p=vm.runInContext('refreshWorksiteList()',ctx);await Promise.resolve();
 await vm.runInContext('refreshWorksiteList()',ctx);release();await p;
 assert.equal(loads,2);assert.equal(maxActive,1); // event during load is not lost
 sources[0].handlers.change({data:JSON.stringify({entity_type:'FILE'})});assert.equal(timers.size,0);
 sources[0].handlers.change({data:'invalid json'});assert.equal(timers.size,0);
 for(const entity_type of ['WORKSITE','WORKSITE_MEMBERSHIP','WORKSITE_INVITATION'])sources[0].handlers.change({data:JSON.stringify({entity_type})});
 assert.equal(timers.size,1); // debounce burst
 sources[0].handlers.open();assert.equal(timers.size,1); // reconnect recovery
 handlers.focus();assert.equal(timers.size,1);
 for(const f of timers.values())await f();timers.clear();assert.equal(loads,3);
 handlers.interval();assert.equal(timers.size,1);
 handlers.beforeunload();assert.equal(sources[0].closed,true);assert.equal(timers.size,0);assert.equal(handlers.cleared,true);
 // A slower old request must never overwrite a newer list snapshot.
 const loadsCode=list.slice(list.indexOf('let worksiteListRequest=0;'),list.indexOf("const createForm="));
 const pending=[],elements={worksiteCount:{}},rows={innerHTML:''};
 const listContext=vm.createContext({tbApi:()=>new Promise(resolve=>pending.push(resolve)),userRole:'USER',
   document:{getElementById:id=>elements[id]},rows,companyGroups:null,worksiteRow:w=>w.name,
   bindDeleteButtons(){},show(){},worksiteData:[]});
 vm.runInContext(loadsCode,listContext);
 const oldLoad=vm.runInContext('loadWorksites()',listContext),newLoad=vm.runInContext('loadWorksites()',listContext);
 pending[1]({data:{worksites:[{name:'Neu'}]}});await newLoad;
 pending[0]({data:{worksites:[{name:'Alt'}]}});await oldLoad;assert.match(rows.innerHTML,/Neu/);assert.doesNotMatch(rows.innerHTML,/Alt/);
 const detail=fs.readFileSync(path.join(__dirname,'../templates/v1_worksite.html'),'utf8');
 const rename=detail.slice(detail.indexOf("document.getElementById('renameWorksite').addEventListener"),detail.indexOf('async function loadOverview(){'));
 let click,requests=[],overview=0;
 const worksite={name:'Alt',version:7};const button={disabled:false};
 vm.runInNewContext(rename,{document:{getElementById(){return{addEventListener(n,f){click=f;}};}},
  window:{prompt(){worksite.version=8;return ' Neu ';}},worksite,worksiteId:'w',
  tbApi:async(url,options)=>requests.push({url,...options}),loadOverview:async()=>{overview++;},show(){}});
 await click.call(button);assert.equal(button.disabled,false);assert.equal(overview,1);
 assert.deepEqual(JSON.parse(requests[0].body),{name:'Neu',base_version:7});
 console.log('PASS: list single-flight, queued refresh, metadata filter, debounce, reconnect/focus/periodic recovery, cleanup, rename version capture');
})().catch(e=>{console.error(e);process.exitCode=1;});
