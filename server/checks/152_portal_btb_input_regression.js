const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const elements={};
function element(){return {value:'',textContent:'',innerHTML:'',files:[],disabled:false,hidden:false,style:{},dataset:{},children:[],listeners:{},labels:[],classList:{add(){},remove(){},contains(){return false}},setAttribute(){},append(x){this.children.push(x)},after(x){this.sibling=x},replaceChildren(...xs){this.children=xs},addEventListener(name,fn){this.listeners[name]=fn},dispatchEvent(e){this.listeners[e.type]?.(e)},querySelectorAll(){return []}};}
const document={getElementById(id){return elements[id]??=element()},querySelectorAll(){return []},createElement:element,addEventListener(){}};
const script=fs.readFileSync(path.join(root,'templates/v1_btb.html'),'utf8').match(/<script>([\s\S]*)<\/script>/)[1].replace(/\{\{[^\n]*?\}\}/g,'"test"').replace(/loadBtb\(\)\s*\.then\(startBtbLiveUpdates\)[\s\S]*$/,'');
const helper=fs.readFileSync(path.join(root,'templates/v1_btb_times.html'),'utf8').match(/<script>([\s\S]*)<\/script>/)[1];
let apiCalls=[],failFile=null,uploaded=[];
const context={console,document,window:{addEventListener(){}},Event:class {constructor(type){this.type=type}},Set,Date,URL,confirm:()=>true,clearTimeout,setTimeout};vm.createContext(context);
vm.runInContext(helper+'\n'+script,context);
vm.runInContext(`
(async()=>{
const assert=(ok,msg)=>{if(!ok)throw Error(msg||'assertion failed')};
const quarter=tbQuarterOptions('07:31');assert(quarter.length===98);assert(quarter.filter(o=>o.value.endsWith(':15')).length===24);assert(quarter.at(-1).label.includes('bisheriger'));
const input=document.createElement('input');input.type='datetime-local';input.value='2026-10-18T07:31';tbRefreshQuarterTimes({querySelectorAll:()=>[input]});assert(input.hidden&&input._quarterBox.clock.value==='07:31');input._quarterBox.clock.value='07:45';input._quarterBox.clock.listeners.change();assert(input.value==='2026-10-18T07:45');
current={worksite_id:'ws',team_id:'team',work_date:'2026-10-18',started_at:'2026-10-18T07:00',ended_at:'2026-10-18T17:00'};
document.getElementById('edit-personalVon').value='07:00';document.getElementById('edit-personalBis').value='17:00';assert(firmTimeBinding('personal')==='REPORT');document.getElementById('edit-personalVon').value='08:15';assert(firmTimeBinding('personal')==='MANUAL');
reportProposal={started_at:'2026-10-17T22:00',ended_at:'2026-10-18T06:00'};document.getElementById('workDate').value='2026-10-18';applyReportTimeProposal();assert(document.getElementById('endedAt').value==='2026-10-19T06:00');
// Actual payload functions preserve unknown fields and add/remove only edited end time.
editingEntry={section:'ARBEIT',payload:{uhrzeit:'07:31',unknown:'preserve'}};
for(const [id,value] of Object.entries({'edit-uhrzeit':'22:00','edit-uhrzeitBis':'01:00','edit-text':'Strail defekt','edit-kmVon':'78,577','edit-kmBis':'','edit-firmaSelect':'firm','edit-nodeSelect':'node'}))document.getElementById(id).value=value;
const p=buildEditedPayload();assert(p.uhrzeitBis==='01:00'&&p.unknown==='preserve'&&p.kmVon===78.577);assert(createSectionItemPayload().worksiteNodeKey==='node');
document.getElementById('edit-uhrzeitBis').value='';assert(buildEditedPayload().uhrzeitBis===null);
// One company identity is created for both areas and report time bindings are attached.
creatingEntry={section:'FIRMA',firmId:'PERSONAL'};document.getElementById('edit-name').value='Schwerbau';document.getElementById('edit-maschinenVon').value='07:00';document.getElementById('edit-maschinenBis').value='17:00';document.getElementById('edit-personalVon').value='07:00';
let posted;postManagedEntry=async(s,slot,n,payload)=>{posted=payload;return {data:{id:'saved',version:1}}};await createModalEntry();assert(posted.visibleInPersonal&&posted.visibleInMaschinen&&posted.personalVon===posted.maschinenVon&&posted.maschinenZeitbindung==='REPORT');
// Actual common transport must prepare before hashing and creating the upload.
const order=[],transport=[];window.crypto={subtle:{}};crypto={subtle:{}};
tbPreparePhoto=async(file)=>{order.push('prepare');return {name:'small.jpg',type:'image/jpeg',size:4,lastModified:1000,slice:(a,b)=>({size:b-a})}};
sha256Hex=async(file)=>{order.push('hash');assert(file.name==='small.jpg');return 'sha'};
tbApi=async(url,options)=>{transport.push({url,options});if(url.endsWith('/uploads'))return {data:{id:'up',received_bytes:0,chunk_size:4}};if(url.includes('/chunk'))return {data:{received_bytes:4}};return {data:{id:'photo'}}};
await uploadBtbPhoto({name:'original.jpg'},{worksiteNodeKey:'node'},document.getElementById('photoProgress'));
assert(order.join(',')==='prepare,hash');const completed=JSON.parse(transport.at(-1).options.body);assert(completed.photo_metadata.worksiteNodeKey==='node'&&completed.entity_type==='BTB');
// Two photos: a failure on the second must not upload the successful first one again.
entries=[{section:'FIRMA',payload:{firmaId:'firm',name:'Schwerbau'}}];firmIdOf=e=>e.payload?.firmaId||'';
const f1={name:'1'},f2={name:'2'},photoInput=document.getElementById('entryPhotos');photoInput.files=[f1,f2];let requests=[],fail=true;
uploadBtbPhoto=async(f,m)=>{requests.push({f,m});if(f===f2&&fail)throw Error('offline')};
const meta={text:'Strail defekt',firmaId:'firm',worksiteNodeKey:'node',kmVon:78.577};
try{await uploadEntryPhotos('ARBEIT',meta)}catch(e){assert(e.message==='offline')};fail=false;await uploadEntryPhotos('ARBEIT',meta);
assert(requests.length===3&&requests[2].f===f2);assert(requests[0].m.worksiteNodeKey==='node'&&requests[0].m.firma==='Schwerbau'&&requests[0].m.kmManuell===true&&requests[0].m.typ==='FOTODOKUMENTATION');
// Full save flow: mutation succeeds, upload fails, retry must skip unchanged PATCH.
let posts=0,patches=0,attempts=0;
creatingEntry={section:'ARBEIT',firmId:'firm'};editingEntry=null;
createModalEntry=async()=>{posts++;return {data:{id:'saved-work',version:1,section:'ARBEIT',sort_order:0,payload:createSectionItemPayload()}}};
tbApi=async()=>{patches++;throw Error('NO_CHANGES')};uploadEntryPhotos=async()=>{attempts++;if(attempts===1)throw Error('offline')};loadBtb=async()=>{};
document.getElementById('edit-section').value='ARBEIT';
// Creation omits an empty end time; put an explicit null on the saved model to match editing semantics.
document.getElementById('edit-uhrzeitBis').value='01:00';
await saveModalEntry();assert(editingEntry.id==='saved-work'&&creatingEntry===null);await saveModalEntry();assert(posts===1&&patches===0&&attempts===2);
console.log('PORTAL-016 production JS quarter/payload/company/night/retry checks PASS');
})()
`,context).catch(e=>{console.error(e);process.exitCode=1});
