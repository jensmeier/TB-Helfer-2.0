// Executes the production labels and their actual UI call sites; no services/writes.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.join(__dirname,'..','templates'),read=n=>fs.readFileSync(path.join(root,n),'utf8');
const helper=read('v1_node_labels.html').match(/<script>([\s\S]*?)<\/script>/)[1];
const c={};vm.createContext(c);vm.runInContext(helper,c);
const nodes=[
 {id:'mm',client_node_key:'key-mm',name:'G4',node_kind:'BAHNHOF',track_key:'4',payload:{node_name:'G4',station_name:'Memmingen',station_code:'MM',km_from:null,km_to:null}},
 {id:'bg',client_node_key:'key-bg',name:'G4',node_kind:'BAHNHOF',track_key:'4',payload:{node_name:'G4',station_name:'Berchtesgaden',station_code:'MBG',km_from:0,km_to:0}},
 {id:'w',client_node_key:'key-w',name:'Umbau',node_kind:'BAHNHOF',track_key:'W 4',payload:{station_code:'MM',object_type:'WEICHE',km_from:-0.2,km_to:0}},
 {id:'v',client_node_key:'key-v',name:'Verbindung',node_kind:'BAHNHOF',payload:{station_code:'MM',track_or_switch:'WVB 1',object_type:'WEICHENVERBINDUNG'}},
 {id:'r',client_node_key:'key-r',name:'Abschnitt',node_kind:'FREIE_STRECKE',route_number:'5362',payload:{station_name:'stale',station_code:'OLD',km_from:'0',km_to:'0,2'}},
];
assert(c.tbNodeLabel({name:'A',track_key:'W4',payload:{station_code:'MM'}}).includes('Weiche 4'));
assert(c.tbNodeLabel({name:'A',track_key:'Gl 4',payload:{station_code:'MM'}}).includes('Gleis 4'));
const before=JSON.stringify(nodes);nodes.forEach(n=>{Object.freeze(n.payload);Object.freeze(n)});c.nodes=nodes;
const labels=nodes.map(n=>c.tbNodeLabel(n));
assert.equal(labels[0],'G4 · Memmingen (MM) · Gleis 4');
assert.equal(labels[1],'G4 · Berchtesgaden (MBG) · Gleis 4 (KM 0–0)');
assert.equal(labels[2],'Umbau · MM · Weiche 4 (KM -0,2–0)');
assert.equal(labels[3],'Verbindung · MM · WVB 1');
assert.equal(labels[4],'Abschnitt · Freie Strecke 5362 (KM 0–0,2)');
for(const value of [null,undefined,'','  ',false,true,[],{},'abc',Infinity,NaN])assert.equal(c.tbDisplayKm(value),null);
for(const value of [0,'0','0.000','0,000'])assert.equal(c.tbDisplayKm(value),0);
for(const [a,b,expected] of [[null,0,'KM 0'],[0,null,'KM 0'],['',null,null],['bad',null,null],[10,10,'KM 10–10']]){
 const label=c.tbNodeLabel({name:'Test',payload:{km_from:a,km_to:b}});assert(expected?label.includes(expected):!label.includes('KM'),label);
}
const work=read('v1_worksite.html'),btb=read('v1_btb.html'),gallery=read('v1_portal_pictures.html');
for(const source of [work,btb,gallery])assert(source.includes('{% include "v1_node_labels.html" %}'));
function part(source,start,end){return source.slice(source.indexOf(start),source.indexOf(end,source.indexOf(start)));}
c.esc=v=>String(v).replaceAll('<','&lt;');c.nodeTypeLabel=x=>x;c.nodeData=nodes;c.worksiteNodes=nodes;
vm.runInContext(part(work,'function nodeName(','function nodeTypeLabel('),c);
vm.runInContext(part(work,'function nodeOptions(',"document.getElementById('renameWorksite')"),c);
vm.runInContext(part(btb,'function worksiteNodeDirectory(','function worksiteNodeOptions('),c);
vm.runInContext(part(btb,'function photoKm(','function validGps('),c);
vm.runInContext(part(gallery,'function nodeLabel(','function draw('),c);
for(let i=0;i<nodes.length;i++){
 assert.equal(c.nodeName(nodes[i].id),labels[i]);assert.equal(c.worksiteNodeLabel(nodes[i].client_node_key),labels[i]);
 assert.equal(c.nodeLabel({photo_metadata:{worksiteNodeKey:nodes[i].client_node_key},worksite_node:nodes[i]}),labels[i]);
}
assert.equal(c.photoKm({km:null}),'');assert.equal(c.photoKm(null),'');assert.equal(c.photoKm({km:0}),'KM 0,000');
assert.equal(c.worksiteNodeLabel(''),'Hauptbaustelle');assert.equal(c.worksiteNodeLabel('missing'),'Unterbaustelle missing');
assert.equal(c.nodeLabel({photo_metadata:{}}),'Unterbaustelle nicht zugeordnet');
assert.equal(c.nodeLabel({photo_metadata:{worksiteNodeKey:'missing'}}),'Unterbaustelle nicht gefunden (missing)');
const elements={};c.document={getElementById:id=>elements[id]??={innerHTML:''}};c.nodeOptions();
for(const id of ['defectNode','acceptanceNode','nodeParent'])for(let i=0;i<nodes.length;i++)assert(elements[id].innerHTML.includes('value="'+nodes[i].id+'">'+nodes[i].node_kind+' · '+labels[i]));
vm.runInContext(part(work,'function nodeTreeRows(','async function editNode('),c);
c.worksiteId='site';c.can=()=>false;c.tbApi=async()=>({data:{nodes}});
(async()=>{await c.loadNodes();for(const label of labels)assert(elements.nodes.innerHTML.includes(label));assert(!elements.nodes.innerHTML.includes('Gleis W '));
 assert.equal(JSON.stringify(nodes),before);
 // Compile every script after expanding local includes and replacing server data placeholders.
 for(const file of ['v1_worksite.html','v1_btb.html','v1_portal_pictures.html']){
  const expanded=read(file).replace(/\{% include "([^"]+)" %\}/g,(_,name)=>read(name));
  for(const match of expanded.matchAll(/<script>([\s\S]*?)<\/script>/g))new vm.Script(match[1].replace(/\{\{[\s\S]*?\}\}/g,'null'));
 }
 console.log('157 PASS: Portal/BTB/gallery production labels, station/object identity, null/zero KM, selections, unknown keys, immutable input, JS syntax');
})().catch(e=>{console.error(e);process.exitCode=1});
