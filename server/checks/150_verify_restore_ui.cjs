// Isolated DOM test with fixture responses; not a live API/NAS E2E.
const fs=require('fs');const {chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:1280,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let html=fs.readFileSync(require('path').join(__dirname,'../templates/v1_system_status.html'),'utf8').replace(/{%[\s\S]*?%}/g,'');
 const id='11111111-1111-4111-8111-111111111111';
 const fixture={enabled:true,system_export_enabled:true,state:null,backups:['USER','TRACK','FULL'].map((kind,i)=>({id:i===0?id:'22222222-2222-4222-8222-'+String(i).padStart(12,'0'),kind,started_at:'2026-10-09T10:00:00Z',byte_size:100000}))};
 const setup=`<script>window.requests=[];window.tbApi=async(path,options)=>{window.requests.push({path,options});if(path.endsWith('/recovery/automatic'))return {data:${JSON.stringify(fixture)}};if(path.endsWith('/recovery/execute'))return {data:{}};return {data:{counts:{},migration:{},storage:{},automation:{},smtp:{},monitor:{},backups:[],jobs:[],issues:[]}};};</script>`;
 html='<!doctype html><html><head><meta charset="utf-8"><style>body{font:16px Arial;background:#f7f8fa;margin:24px}.card,details.panel{background:white;padding:20px;margin-top:16px}button,input,select{padding:10px;margin:4px;max-width:100%}label{display:block}button{background:#1167d8;color:white;border:0;border-radius:6px}button:disabled{opacity:.5}h1{font-size:25px}h2{font-size:20px}.notice{padding:12px;background:#eef5ff}</style>'+setup+'</head><body>'+html+'</body></html>';
 await page.route('http://tb-test.local/**',route=>{if(route.request().url().includes('/recovery-progress/'))return route.fulfill({contentType:'application/json',body:JSON.stringify({phase:'SAFETY',error:null,complete:false})});return route.fulfill({contentType:'text/html',body:html});});
 await page.goto('http://tb-test.local/');await page.waitForFunction(()=>!document.getElementById('automaticRecoveryPanel').hidden);
 await page.locator('#automaticRecoveryPanel summary').click();
 await page.selectOption('#automaticRecoveryBackup',id);await page.fill('#automaticRecoveryConfirm','WIEDERHERSTELLEN '+id);
 if(await page.locator('#automaticRecoveryStart').isDisabled())throw Error('confirmation did not enable');
 if(await page.locator('#recoveryPanel').isVisible())throw Error('legacy visible');
 await page.screenshot({path:'/tmp/restore-ui.png',fullPage:true});
 await page.click('#automaticRecoveryStart');await page.waitForFunction(()=>document.getElementById('automaticRecoveryState').textContent.includes('zusätzlich gesichert'));
 if(await page.locator('#automaticRecoveryForm').isVisible())throw Error('form allows duplicate request');
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:'/tmp/restore-ui-mobile.png',fullPage:true});
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+5);
 if(errors.length)throw Error(JSON.stringify(errors));
 console.log(JSON.stringify({passed:true,checks:['area selection','confirmation','legacy hidden','request submission','independent progress','duplicate form blocked'],mobileOverflow:overflow}));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
