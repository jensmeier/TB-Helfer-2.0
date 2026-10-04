/* Run with Node.js. Exercises the actual template script with a small DOM/API
 * harness; this is deliberately NOT a visual browser or live Synology test. */
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const template = fs.readFileSync(path.join(__dirname, '../templates/v1_admin_survey_overview.html'), 'utf8');
const base = fs.readFileSync(path.join(__dirname, '../templates/v1_base.html'), 'utf8');
const script = template.split('<script>')[1].split('</script>')[0];
const baseScript = base.split('<script>')[1].split('</script>')[0];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
class Element {
  constructor(id) {
    this.id = id; this.dataset = {}; this.attrs = {}; this.events = {};
    this.innerHTML = ''; this.textContent = ''; this.hidden = false; this.open = false;
    this.style = {}; this.children = []; this.disabled = false;
    this.classList = { add() {}, remove() {}, toggle() {} };
  }
  setAttribute(k, v) { this.attrs[k] = v; }
  addEventListener(k, fn) { (this.events[k] ||= []).push(fn); }
  dispatch(k, event = {}) { for (const fn of this.events[k] || []) fn(event); }
  replaceChildren() { this.children = []; }
  append(child) { this.children.push(child); }
  querySelectorAll() { return []; }
  getBoundingClientRect() { return { width: 1000, height: 600, left: 0, top: 0 }; }
  showModal() { this.open = true; }
  close() { this.open = false; this.dispatch('close'); }
  focus() {}
}
function fixtures() {
  const a = (id, track, extra = {}) => ({
    axis_id: id, display_name: 'Gleis ' + track, axis_type: 'GLEIS', track_key: track,
    route_number: '5302', station_code: 'MGHN', station_name: 'Gessertshausen',
    active_segment_count: 1, km_quality: { status: 'DETAIL_BEI_AUSWAHL', color: 'GRAU' },
    wgs84: {min_lon: 10.72, max_lon: 10.73, min_lat:48.32, max_lat:48.321},
    overview_segments: [], ...extra
  });
  const axes = [a('a1','301'),a('a2','302'),a('a3','303',{wgs84:null,station_code:null,station_name:null})];
  const coverage = {axes,axis_count:3,station_count:1,route_count:1,unsupported_axis_count:1};
  const geometry = id => ({axis:{axis_id:id},segments:[{segment_id:'s'+id,source_kind:'ORIGINAL_TRA',coordinates:[[10.72,48.32],[10.73,48.321]],km_quality:{status:'AUTOMATISCH',color:'GRUEN',km_from_m:null,km_to_m:null}}],unsupported_point_count:0});
  const details = id => ({axis:{id,current_version:2},segments:[{id:'s'+id,source_original_id:'o'+id,original_name:'<img src=x onerror=alert(1)>.TRA',source_kind:'ORIGINAL_TRA',data_kind:'TRA',sha256:'abc',byte_size:1024,current_version:1,segment_status:'ACTIVE'}],history:[{change_id:10,entity_type:'AXIS',entity_version:2,operation:'UPDATE',changed_at:'2026-10-02T16:00:00Z',fields:[{field:'display_name',before:'Alt',after:'<script>bad</script>'}]}],history_next_before:10,conflicts:[]});
  return {coverage,geometry,details};
}
async function harness() {
  const f = fixtures(), elements = new Map(), calls = [], controls = {timeoutMs:12000,handler:null};
  const get = id => { if (!elements.has(id)) elements.set(id,new Element(id)); return elements.get(id); };
  const tabs = ['overview','objects','originals','history'].map(x=>{const e=get('tab-'+x);e.dataset.tab=x;return e;});
  const lists = ['axes','stations','routes'].map(x=>{const e=get('list-'+x);e.dataset.list=x;return e;});
  const kpis = ['TotalKm','KnownKm','AutoKm','MobileKm','MissingKm','Stations','Tracks','Routes'].map(x=>get('kpi'+x));
  const document = {cookie:'tb_admin_csrf=test-admin-csrf',getElementById:get,createElement:tag=>new Element(tag),querySelectorAll:q=>q==='[data-tab]'?tabs:q==='[data-list]'?lists:q==='.coverage-kpi strong'?kpis:[]};
  function response(url) {
    if (url.includes('/summary')) return {total_length_m:128911.129,known_km_length_m:116578.85,automatic_km_length_m:116578.85,manual_mobile_length_m:0,without_km_length_m:12332.28,station_count:1,track_count:3,route_count:1};
    if (url.includes('/geometry')) return f.geometry(url.split('/').at(-2));
    if (url.includes('/details')) { const d=f.details(url.split('/').at(-2));if(url.includes('history_before')){d.history=[{...d.history[0],change_id:5}];d.history_next_before=null;}return d; }
    const data=structuredClone(f.coverage);
    if(url.includes('include_overview')) for(const a of data.axes) if(a.wgs84)a.overview_segments=f.geometry(a.axis_id).segments.map(s=>({...s,km_quality:{status:'DETAIL_BEI_AUSWAHL',color:'GRAU'}}));
    return data;
  }
  const fetch = (url,opts) => {
    assert.equal(opts.headers['X-TB-Session-Scope'],'ADMIN');assert.equal(opts.credentials,'same-origin');
    calls.push({url,opts});const config=controls.handler?.(url)||{};
    return new Promise((resolve,reject)=>{
      const done=()=>resolve({ok:config.status?config.status<400:true,status:config.status||200,text:async()=>JSON.stringify(config.body??response(url))});
      const timer=config.hang?null:setTimeout(done,config.delay??1);
      const abort=()=>{if(config.ignoreAbort)return;clearTimeout(timer);const e=Error('Aborted');e.name='AbortError';reject(e);};
      opts.signal.addEventListener('abort',abort,{once:true});if(opts.signal.aborted)abort();
    });
  };
  const context=vm.createContext({crypto:require("node:crypto").webcrypto,document,location:{pathname:'/admin/v1/survey-overview'},window:{},console,fetch,AbortController,URL,Map,Set,Date,Math,Number,String,Array,JSON,Promise,ResizeObserver:class{observe(){}},setTimeout:(fn,ms)=>setTimeout(fn,ms===12000?controls.timeoutMs:ms),clearTimeout,requestAnimationFrame:fn=>setTimeout(fn,0)});
  vm.runInContext(baseScript,context);vm.runInContext(script,context);
  const run=code=>vm.runInContext(code,context);
  for(let i=0;i<100&&!calls.some(c=>c.url.includes('include_overview'));i++)await delay(2);
  await delay(10);
  return {f,get,calls,controls,run};
}
(async()=>{
  const h=await harness();const {run,get,controls,calls}=h;
  assert.equal(get('kpiTotalKm').textContent,'128,91 km');
  assert(calls.some(c=>c.url.endsWith('include_overview=true&fast_overview=true')));
  assert.equal(run('axes.size'),3);assert.equal(run('stationItems.length'),1);
  // Overall extent uses actual data, preserves metric aspect, and clamps pan/zoom.
  assert(run('fullBounds.w < 2000 && fullBounds.h < 2000'));
  assert(run('Math.abs(view.w/view.h-W/H)<1e-10'));
  run('for(let i=0;i<50;i++)zoomMap(2)');assert(run('view.w===fullBounds.w && view.x===fullBounds.x'));
  run('for(let i=0;i<80;i++)zoomMap(.5)');assert(run('view.h===5'));
  run('view=bounded({...view,x:fullBounds.x-100000,y:fullBounds.y+100000})');
  assert(run('view.x>=fullBounds.x&&view.y+view.h<=fullBounds.y+fullBounds.h+.0001'));
  run('resetView();resizeMap()');assert(run('Math.abs(view.w/view.h-W/H)<1e-10'));
  assert.equal(run('validBox({wgs84:{min_lon:null,max_lon:2,min_lat:1,max_lat:2}})'),false);
  assert.equal(run("rangeText([{km_quality:{km_from_m:null,km_to_m:null}}])"),'Keine belastbaren KM verfügbar');
  // Overlapping axes produce multiple hits; dragging does not open a dialog.
  const ids=JSON.parse(run('JSON.stringify(hitTest(...coordScreen([10.725,48.3205])).map(a=>a.axis_id))'));
  assert.deepEqual(ids,['a1','a2']);
  run("openItem({kind:'hits',title:'Objekt auswählen',ids:['a1','a2']})");
  assert(get('dialogBody').innerHTML.includes('Gleis 301'));assert(get('dialogBody').innerHTML.includes('Gleis 302'));
  run("openItem({kind:'axis',key:'a1',title:'Gleis 301',ids:['a1']})");await delay(20);
  assert.equal(run("metadataCache.get('a1').axis.id"),'a1');
  run("modalTab='originals';renderDialog()");assert(get('dialogBody').innerHTML.includes('&lt;img'));assert(!get('dialogBody').innerHTML.includes('<img'));
  run("modalTab='history';renderDialog()");assert(get('dialogBody').innerHTML.includes('&lt;script&gt;'));
  await run("loadMetadata('a1',modalVersion,true)");assert.equal(run("metadataCache.get('a1').history.length"),2);assert.equal(run("metadataCache.get('a1').history_next_before"),null);
  const bounds=run('JSON.stringify(view)');get('coverageDialog').close();assert.equal(run('JSON.stringify(view)'),bounds);
  // Delayed old selection must never replace the current detail or cache.
  run('metadataCache.clear()');controls.handler=url=>url.includes('/a1/details')?{delay:50,ignoreAbort:true}:{};
  run("openItem({kind:'axis',key:'a1',title:'Alt',ids:['a1']});openItem({kind:'axis',key:'a2',title:'Neu',ids:['a2']})");await delay(70);
  assert.equal(get('dialogTitle').textContent,'Neu');assert.equal(run("metadataCache.has('a1')"),false);assert.equal(run("metadataCache.has('a2')"),true);
  // Bounded timeout exits loading and exposes retry; closing invalidates in-flight metadata.
  controls.timeoutMs=15;controls.handler=url=>url.includes('/details')?{hang:true}:{};run('metadataCache.clear()');
  run("openItem({kind:'axis',key:'a1',title:'Timeout',ids:['a1']})");await delay(30);
  assert(get('dialogBody').innerHTML.includes('12 Sekunden'));assert(get('dialogBody').innerHTML.includes('retryMetadata'));assert.equal(get('dialogLoad').textContent,'');
  controls.handler=null;await run("loadMetadata('a1',modalVersion)");assert.equal(run("metadataCache.has('a1')"),true);
  // A failed older-history page must retry that page, not silently use the cache.
  controls.handler=url=>url.includes('history_before')?{status:500}:{};
  await run("loadMetadata('a1',modalVersion,true)");assert.equal(run('modalRetryOlder'),true);
  controls.handler=null;await run("loadMetadata('a1',modalVersion,modalRetryOlder)");
  assert.equal(run("metadataCache.get('a1').history.length"),2);
  assert.equal(run('modalRetryOlder'),false);
  assert.equal(run('geometryQuality({segments:[]}).status'),'NICHT_DARSTELLBAR');
  // Failure of a secondary request cannot erase a usable map.
  controls.handler=url=>url.includes('include_overview')?{status:500}:{};await run('loadShapes(epoch)');assert.equal(run('axes.size'),3);assert.equal(get('shapeStatus').hidden,false);
  // A reload clears old KPI/cache state and rejects delayed results from older epochs.
  controls.handler=url=>url.includes('/summary')?{status:500}:{};await run('loadCoverage()');await delay(20);
  assert.equal(get('kpiTotalKm').textContent,'—');assert.equal(get('summaryStatus').hidden,false);assert.equal(run('axes.size'),3);
  // Admin management uses scoped CSRF writes, explicit confirmation and no retry after an uncertain write.
  controls.handler=null;controls.timeoutMs=500;
  await run("readManagementAxis('a1')");assert.equal(get('axisManager').open,true);
  assert(get('managementBody').innerHTML.includes('axisEditForm'));
  run("confirmAxisStatus({axis:{id:'a1',display_name:'<b>Weiche</b>',status:'ACTIVE',current_version:2},segments:[{}]})");
  assert(get('managementBody').innerHTML.includes('&lt;b&gt;Weiche'));
  assert(get('managementBody').innerHTML.includes('Jetzt archivieren'));
  assert(get('managementBody').innerHTML.includes('Originaldateien'));
  const beforeWrites=calls.filter(c=>c.opts.method==='POST').length;
  controls.handler=url=>url.includes('/management/axes/a1/archive')?{delay:20,body:{status:'ARCHIVED'}}:{};
  const saving=run("writeManagement('a1','POST','/archive',{base_version:2})");
  assert.equal(run('managementBusy'),true);
  await run("writeManagement('a1','POST','/archive',{base_version:2})");await saving;
  const writes=calls.filter(c=>c.opts.method==='POST');assert.equal(writes.length,beforeWrites+1);
  assert.equal(writes.at(-1).opts.headers['X-CSRF-Token'],'test-admin-csrf');
  assert.deepEqual(JSON.parse(writes.at(-1).opts.body),{base_version:2});assert.equal(run('managementBusy'),false);
  controls.handler=url=>url.includes('/management/axes/')?{status:409,body:{detail:{code:'VERSION_CONFLICT'}}}:{};
  await run("writeManagement('a1','PATCH','',{base_version:2,display_name:'Neu'})");
  assert(get('managementMessage').textContent.includes('Zwischenzeitlich geändert'));
  assert(get('managementBody').innerHTML.includes('reloadAfterWrite'));
  controls.timeoutMs=10;controls.handler=url=>url.includes('/management/axes/')?{hang:true}:{};
  const beforeTimeout=calls.length;await run("writeManagement('a1','POST','/archive',{base_version:2})");
  assert.equal(calls.length,beforeTimeout+1);assert.equal(run('managementBusy'),false);
  assert(get('managementMessage').textContent.includes('Speichern nicht bestätigt'));
  controls.handler=url=>url.includes('/management/axes?')?{body:{axes:[{id:'arc',display_name:'<img>',segment_count:1}],has_more:true}}:{};
  await run('showArchive()');assert(get('managementBody').innerHTML.includes('&lt;img&gt;'));assert.equal(get('managementTitle').textContent,'Archivierte Achsen');
  run("archiveOffset=50;archiveQuery='107W'");await run('showArchive(false)');
  assert(calls.at(-1).url.includes('offset=50&q=107W'));
  controls.timeoutMs=500;controls.handler=null;
  // Manual KM editor: actual production functions, with transport/DOM doubles.
  assert.equal(run("kmNumber('16,294948')"),16.294948);
  for(const value of ['', '1.234,56','NaN','Infinity','16+294'])assert.throws(()=>run(`kmNumber(${JSON.stringify(value)})`));
  h.f.details=id=>({axis:{id,display_name:'<Gleis 4>',status:'ACTIVE',current_version:3},segments:[{id:'source',source_original_id:'o1',source_kind:'ORIGINAL_TRA',segment_status:'ACTIVE',current_version:1,station_from_m:0,station_to_m:100,original_name:'<original>.TRA'}],history:[],conflicts:[]});
  await run("readKmEditor('a1')");assert(get('managementBody').innerHTML.includes('&lt;original&gt;'));
  get('kmSource').value='source';get('kmStart').value='5';get('kmEnd').value='25';get('kmFrom').value='16,005';get('kmTo').value='16,025';get('kmNote').value='<note>';
  assert.equal(run('collectKmPayload().km_from_m'),16005);assert.equal(run('collectKmPayload().km_to_m'),16025);
  get('kmEnd').value='101';assert.throws(()=>run('collectKmPayload()'));get('kmEnd').value='25';
  controls.handler=url=>url.endsWith('/preview')?{body:{length_m:20,km_difference_m:20,length_warning:false,direction:'STEIGEND',points:[[0,0],[20,2]]}}:{};
  await run('previewKm()');assert(run('kmPreviewPayload!==null'));assert(get('kmPreview').innerHTML.includes('<svg'));assert(!get('kmPreview').innerHTML.includes('NaN'));
  const previewCall=calls.filter(c=>c.url.endsWith('/preview')).at(-1);assert.equal(previewCall.opts.headers['X-CSRF-Token'],'test-admin-csrf');
  get('kmStart').dispatch('input');assert.equal(run('kmPreviewPayload'),null);assert.equal(get('kmPreview').innerHTML,'');
  await run('previewKm()');controls.handler=url=>url.endsWith('/manual-km')?{delay:20,body:{status:'ACTIVE'}}:{};
  const beforeSave=calls.filter(c=>c.url.endsWith('/manual-km')).length;
  await Promise.all([run("writeKm('',kmPreviewPayload)"),run("writeKm('',kmPreviewPayload)")]);
  assert.equal(calls.filter(c=>c.url.endsWith('/manual-km')).length,beforeSave+1);
  assert(get('managementMessage').textContent.includes('gespeichert'));
  controls.handler=url=>url.endsWith('/manual-km')?{status:409,body:{detail:'SURVEY_KM_OVERLAP_CONFLICT'}}:{};
  await run("writeKm('',{client_change_id:'conflict'})");assert(get('managementMessage').textContent.includes('widersprechen'));assert(get('managementBody').innerHTML.includes('reloadKmAfterWrite'));
  controls.handler=null;await run("readKmEditor('a1')");controls.timeoutMs=10;
  controls.handler=url=>url.endsWith('/manual-km')?{hang:true}:{};const beforeKmTimeout=calls.length;
  await run("writeKm('',{client_change_id:'timeout'})");assert.equal(calls.length,beforeKmTimeout+1);assert.equal(run('managementBusy'),false);
  assert(get('managementBody').innerHTML.includes('nicht automatisch'));

  controls.handler=null;controls.timeoutMs=500;
  // Empty stock and unsupported coordinates must never produce NaN or Germany fallback.
  h.f.coverage={axes:[],axis_count:0,station_count:0,route_count:0,unsupported_axis_count:0};controls.handler=null;await run('loadCoverage()');await delay(10);
  assert.equal(run('view'),null);assert.equal(get('overviewLayer').innerHTML,'');assert(!get('overviewLayer').innerHTML.includes('NaN'));
  run('clearTimeout(detailTimer)');
  console.log('PASS: actual template logic — bounds/deep zoom/aspect, multi-hit, escaping, history paging, null KM, stale selection, abort/timeout/retry, independent failures, empty stock, admin edit/archive and manual KM preview/save/conflict/CSRF/write timeout; NOT a visual browser test.');
})().catch(e=>{console.error(e);process.exitCode=1;});

