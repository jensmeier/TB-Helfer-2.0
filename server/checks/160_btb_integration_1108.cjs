// Execute production display functions; no browser or NAS E2E claim.
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const source=fs.readFileSync(path.join(__dirname,'../templates/v1_btb.html'),'utf8');
const state={tbDisplayKm:x=>x==null||x===''?null:Number(x),entries:[],esc:x=>String(x),
 worksiteNodeLabel:x=>x||'Hauptbaustelle',firmDirectory:()=>({}),
 normalizedSummary:x=>String(x).toLowerCase(),compactLine:x=>x.text, decodePhotoMetadata:()=>({})};
vm.createContext(state);
vm.runInContext(source.slice(source.indexOf('function photoMeta('),source.indexOf('function photoEventText(')),state);
vm.runInContext(source.slice(source.indexOf('function photoAlreadyRepresented('),source.indexOf('function renderContentNodeGroups(')),state);
const photo=(id,firm,node,hour)=>({id,photo_metadata:{firma:firm,worksiteNodeKey:node,epoch:Date.UTC(2026,10,8,hour),kurztext:'Schwellenwechsel',notiz:'Detail',km:0}});
state.uniquePhotos=()=>[photo('a','Firma A','n1',8),photo('b','Firma A','n1',14),photo('c','Firma B','n1',9),photo('d','Firma A','n2',10)];
const html=state.renderPhotoEvents('ARBEIT',[],[]);
assert.strictEqual((html.match(/class="btb-photo-event"/g)||[]).length,3);
assert(html.includes('Schwellenwechsel – Detail'));
assert(html.includes('KM 0,000'));
assert(html.includes('Bild 1, 2'));
assert(html.includes('Firma B'));
assert.strictEqual(state.photoDescription({kurztext:'Test',notiz:'TEST'}),'TEST');
assert.strictEqual(state.photoAlreadyRepresented({kurztext:'Kurz',notiz:'Detail'},[{text:'Kurz'}],[]),false);
console.log('INTEGRATION-1108 portal grouping/text/zero-KM regression PASS (7 assertions)');
