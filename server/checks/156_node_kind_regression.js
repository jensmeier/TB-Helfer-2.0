// Real form functions under a DOM double; no browser/NAS E2E.
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../templates/v1_worksite.html'),'utf8');
function fn(name){const start=source.indexOf('function '+name+'(');let depth=0,end=source.indexOf('{',start);for(let i=end;i<source.length;i++){if(source[i]==='{')depth++;if(source[i]==='}'&&!--depth)return source.slice(start,i+1);}throw Error(name);}
const fields={};for(const id of ['nodeKind','nodeStation','nodeStationFields','nodeObjectType','nodeTrack','nodeTrackLabel','nodeDisplayName','nodeRoute','nodeKmFrom','nodeKmTo'])fields[id]={value:'',dataset:{}};
let clears=0;const ctx={document:{getElementById:id=>fields[id]},nodeLookup:{clear:()=>clears++}};vm.createContext(ctx);
// Extraction by explicit function boundaries avoids braces inside object literals.
for(const name of ['numberOrNull','canonicalNodePayload','nodeTrackValue','updateNodeObjectType','updateNodeKind']){
 const match=source.match(new RegExp('function '+name+'\\([^]*?\\n}'));assert(match,name);vm.runInContext(match[0],ctx);
}
fields.nodeKind.value='FREIE_STRECKE';fields.nodeStation.value='München-Pasing Bbf';fields.nodeStation.dataset.stationCode='MPB';fields.nodeObjectType.value='WEICHE';fields.nodeTrack.value='27';
vm.runInContext('updateNodeKind()',ctx);
assert(fields.nodeStationFields.hidden&&fields.nodeStation.disabled);assert.equal(fields.nodeStation.value,'');assert.equal(fields.nodeStation.dataset.stationCode,'');assert.equal(clears,1);
assert.equal(fields.nodeTrackLabel.textContent,'Weichennummer (optional)');assert.equal(vm.runInContext('nodeTrackValue()',ctx),'W 27');
fields.nodeStation.value='stale';fields.nodeStation.dataset.stationCode='MPB';
let payload=vm.runInContext('canonicalNodePayload({other:42})',ctx);assert.equal(payload.station_name,null);assert.equal(payload.station_code,null);assert.equal(payload.object_type,'WEICHE');assert.equal(payload.track_or_switch,'W 27');assert.equal(payload.other,42);
fields.nodeKind.value='BAHNHOF';vm.runInContext('updateNodeKind()',ctx);assert(!fields.nodeStationFields.hidden&&!fields.nodeStation.disabled);
fields.nodeObjectType.value='WEICHENVERBINDUNG';fields.nodeTrack.value='WVB:7';assert.equal(vm.runInContext('nodeTrackValue()',ctx),'WVB 7');
fields.nodeObjectType.value='GLEIS';fields.nodeTrack.value='19';assert.equal(vm.runInContext('nodeTrackValue()',ctx),'19');
console.log('156 JS PASS: free route hides/clears station and search; payload guard; station mode; distinct track/switch/WVB');
