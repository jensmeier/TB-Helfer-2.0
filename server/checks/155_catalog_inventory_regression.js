// Production DOM builder with controlled API/DOM. Not a browser E2E test.
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
class E{
 constructor(){this.children=[];this.events={};this.value='';this.style={};}
 appendChild(e){this.children.push(e);return e;}
 replaceChildren(){this.children=[];this.textContent='';}
 setAttribute(){}
 addEventListener(k,f){this.events[k]=f;}
}
const fields={track:new E(),route:new E(),from:new E(),to:new E()};
fields.from.value='99';fields.to.value='100';let sequence=1,calls=0;
const rows=[{label:'Gl 19',kind:'GLEIS',track_key:'19',route_number:'5501',selectable:true,segments:[{status:'AUTOMATISCH',km_from_m:0,km_to_m:2000},{status:'MANUELL',km_from_m:4000,km_to_m:5000}]},
{label:'W 117',kind:'WEICHE',object_key:'W:117',track_key:'wrong',selectable:true,segments:[],status:'KEINE_GEOMETRIE'}];
const ctx={document:{createElement:()=>new E(),getElementById:id=>fields[id]},URLSearchParams,tbApi:async url=>{calls++;assert(url.endsWith('station_code=MHO'));return {data:{rows,counts:{GLEIS:1,WEICHE:1}}};}};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../templates/v1_catalog_lookup.html'),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1],ctx);
const target=new E();ctx.target=target;ctx.seq=()=>sequence;
vm.runInContext("tbCatalogInventory(target,{station_code:'MHO'},{route:'route',track:'track',kmFrom:'from',kmTo:'to'},seq)",ctx);
(async()=>{
 const details=target.children[0];assert.equal(calls,0);details.open=true;await details.events.toggle();assert.equal(calls,1);
 const body=details.children[1],filter=body.children[1],list=body.children[2];assert.equal(list.children.length,2);
 assert.equal(fields.from.value,'99'); // never silently overwrite KM
 const gl=list.children[0];
 gl.children.find(e=>e.textContent==='Gleis / Weiche übernehmen').events.click();assert.equal(fields.track.value,'19');assert.equal(fields.route.value,'5501');
 const kmButtons=gl.children.filter(e=>e.textContent==='KM übernehmen');assert.equal(kmButtons.length,2);
 kmButtons[0].events.click();assert.equal(fields.from.value,'0');assert.equal(fields.to.value,'2');
 kmButtons[1].events.click();assert.equal(fields.from.value,'4');assert.equal(fields.to.value,'5');
 list.children[1].children.find(e=>e.textContent==='Gleis / Weiche übernehmen').events.click();assert.equal(fields.track.value,'W 117');
 assert(list.children[1].children.some(e=>e.textContent==='Keine Geometrie vorhanden.'));
 filter.value='117';filter.events.input();assert.equal(list.children.length,1);
 filter.value='absent';filter.events.input();assert.equal(list.children[0].textContent,'Keine passenden Objekte.');
 // A delayed response after changing the selection must not update the old view.
 const target2=new E();ctx.target=target2;let resolve;
 ctx.tbApi=()=>new Promise(r=>resolve=r);
 vm.runInContext("tbCatalogInventory(target,{station_code:'MHO'},{},seq)",ctx);
 const d2=target2.children[0];d2.open=true;const pending=d2.events.toggle();sequence++;resolve({data:{rows,counts:{}}});await pending;
 assert.equal(d2.children[1].children.length,0);
 console.log('155 JS PASS: lazy inventory, disjoint KM buttons/zero, no implicit overwrite, track/switch selection, filter, stale response');
})().catch(e=>{console.error(e);process.exit(1);});
