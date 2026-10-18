// Executes production UI helpers with DOM/API doubles; not browser E2E.
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const root=path.join(__dirname,'..');
class Element{
 constructor(){this.value='';this.dataset={};this.events={};this.children=[];}
 addEventListener(name,f){this.events[name]=f;}
 after(x){this.panel=x;}
 setAttribute(){}
 replaceChildren(){this.children=[];this.textContent='';}
 appendChild(x){this.children.push(x);}
 closest(){return form;}
}
let form=new Element(),route=new Element(),station=new Element(),code=new Element();
let map={route,station,code},requests=[],pending=[],response={routes:[],stations:[{code:'MHO',name:'Holzkirchen',data_available:false}],warnings:[]};
let context={document:{getElementById:id=>map[id],createElement:()=>{let e=new Element();e.style={};return e;}},setTimeout:f=>{pending.push(f);return pending.length;},clearTimeout:()=>{},tbApi:async url=>{requests.push(url);return {data:response};},encodeURIComponent};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(root,'templates/v1_catalog_lookup.html'),'utf8').match(/<script>([\s\S]*?)<\/script>/)[1],context);
vm.runInContext("tbCatalogLookup({route:'route',station:'station',code:'code'})",context);
(async()=>{
 code.value='mho';code.events.input();await pending.pop()();
 assert(requests[0].endsWith('q=mho'));
 assert(code.panel.children[0].textContent.includes('MHO · Holzkirchen'));
 code.panel.children[0].events.click();assert.equal(code.value,'MHO');assert.equal(station.value,'Holzkirchen');
 station.value='ingo';station.events.input();assert.equal(code.value,'');assert.equal(station.dataset.stationCode,'');
 form.events.reset();await pending.pop()();assert.equal(station.panel.children.length,0); // late result discarded
 response={routes:[],stations:[],warnings:[]};route.value='9999';route.events.input();await pending.pop()();assert(route.panel.textContent.includes('Kein Katalogtreffer'));
 context.tbApi=async()=>{throw Error('offline');};route.value='5501';route.events.input();await pending.pop()();assert(route.panel.textContent.includes('Suche fehlgeschlagen'));
 const source=fs.readFileSync(path.join(root,'templates/v1_worksites.html'),'utf8');
 const part=source.slice(source.indexOf('function bindDeleteButtons(){'),source.indexOf('function renderAdminWorksites(){'));
 let button=new Element(),calls=0,approved=false;
 context.document.querySelectorAll=()=>[button];context.confirm=()=>approved;context.show=()=>{};context.loadWorksites=async()=>{};
 context.tbApi=async(url,opts)=>{calls++;assert(url.includes('base_version=7'));assert.equal(opts.method,'DELETE');};
 button.dataset.worksiteId='w';vm.runInContext("let worksiteData=[{id:'w',name:'Test',version:7,can_delete:true}];",context);
 vm.runInContext(part+'\nbindDeleteButtons();',context);
 await button.events.click();assert.equal(calls,0);approved=true;await button.events.click();assert.equal(calls,1);
 console.log('154 JS PASS: station selection, stale-code/reset/race, unknown/error, delete cancel/confirm/version');
})().catch(e=>{console.error(e);process.exit(1);});
