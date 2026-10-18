const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const js=name=>fs.readFileSync(path.join(root,'templates',name),'utf8').match(/<script>([\s\S]*)<\/script>/)[1];
const exif=js('v1_photo_exif.html');
// A bounded real TIFF fixture: IFD0 -> EXIF DateTimeOriginal + GPS IFD.
function fixture(little=true,south=false){
 const b=new ArrayBuffer(512),v=new DataView(b);const ascii=(p,s)=>{for(let i=0;i<s.length;i++)v.setUint8(p+i,s.charCodeAt(i))};
 const u16=(p,n)=>v.setUint16(p,n,little),u32=(p,n)=>v.setUint32(p,n,little);
 ascii(0,little?'II':'MM');u16(2,42);u32(4,8);u16(8,2);
 function tag(p,id,type,n,value){u16(p,id);u16(p+2,type);u32(p+4,n);u32(p+8,value)}
 tag(10,0x8769,4,1,40);tag(22,0x8825,4,1,80);
 u16(40,1);tag(42,0x9003,2,20,220);ascii(220,'2026:10:18 19:00:00\0');
 u16(80,4);tag(82,1,2,2,0);ascii(90,south?'S\0':'N\0');tag(94,2,5,3,160);tag(106,3,2,2,0);ascii(114,'E\0');tag(118,4,5,3,184);
 [47,35,49.2].forEach((n,i)=>{u32(160+i*8,Math.round(n*1000));u32(164+i*8,1000)});
 [10,15,13.158].forEach((n,i)=>{u32(184+i*8,Math.round(n*1000));u32(188+i*8,1000)});
 return b;
}
const ctx={Intl,Date,DataView,Uint8Array,console};vm.createContext(ctx);vm.runInContext(exif,ctx);
for(const endian of [true,false]){
 ctx.buffer=fixture(endian);const m=vm.runInContext('tbExifMetadata(buffer)',ctx);assert(Math.abs(m.lat-47.597)<1e-7);assert(Math.abs(m.lon-10.253655)<1e-7);assert(new Date(m.epoch).toISOString()==='2026-10-18T17:00:00.000Z');
}
const png=new Uint8Array(8+12+512);new DataView(png.buffer).setUint32(0,0x89504e47);new DataView(png.buffer).setUint32(8,512);png.set([101,88,73,102],12);png.set(new Uint8Array(fixture()),16);ctx.buffer=png.buffer;assert(vm.runInContext('tbExifMetadata(buffer).lat',ctx)>47);
const webp=new Uint8Array(12+8+512);webp.set([82,73,70,70],0);webp.set([87,69,66,80,69,88,73,70],8);new DataView(webp.buffer).setUint32(16,512,true);webp.set(new Uint8Array(fixture()),20);ctx.buffer=webp.buffer;assert(vm.runInContext('tbExifMetadata(buffer).lon',ctx)>10);
ctx.buffer=fixture(true,true);assert(vm.runInContext('tbExifMetadata(buffer).lat',ctx)<0);
ctx.buffer=new ArrayBuffer(3);assert(Object.keys(vm.runInContext('tbExifMetadata(buffer)',ctx)).length===0);
// JPEG APP1 wrapper and corrupt offset, with no external image decoder.
const tiff=new Uint8Array(fixture()),jpeg=new Uint8Array(tiff.length+14);jpeg.set([255,216,255,225,2,8,69,120,105,102,0,0],0);jpeg.set(tiff,12);ctx.buffer=jpeg.buffer;assert(vm.runInContext('tbExifMetadata(buffer).lat',ctx)>47);
ctx.buffer=fixture();new DataView(ctx.buffer).setUint32(4,999999,true);assert(Object.keys(vm.runInContext('tbExifMetadata(buffer)',ctx)).length===0);
// Malformed offsets/counts must never throw.
for(let i=0;i<300;i++){ctx.buffer=fixture();new DataView(ctx.buffer).setUint32(i%128,0xffffffff,!!(i%2));vm.runInContext('tbExifMetadata(buffer)',ctx);}
function el(){return {value:'',textContent:'',innerHTML:'',disabled:false,files:[],style:{},classList:{add(){},remove(){},contains(){return false}},setAttribute(){},addEventListener(){},querySelectorAll(){return []}}}
const elements={},document={getElementById:id=>elements[id]??=el(),querySelectorAll:()=>[],addEventListener(){}};
const source=js('v1_btb.html').replace(/\{\{[^\n]*?\}\}/g,'"test"').replace(/loadBtb\(\)\s*\.then\(startBtbLiveUpdates\)[\s\S]*$/,'');
const ui={console,Intl,Date,document,window:{addEventListener(){}},setTimeout,clearTimeout};vm.createContext(ui);vm.runInContext(source,ui);
vm.runInContext(`
const assert=(v)=>{if(!v)throw Error('UI assertion failed')};
for(const [day,start,end,expected] of [['2026-10-18','19:00','07:00','2026-10-19T05:00:00.000Z'],['2026-10-31','19:00','07:00','2026-11-01T06:00:00.000Z'],['2026-12-31','19:00','07:00','2027-01-01T06:00:00.000Z']])assert(tbReportPeriod(day,start,end).ended_at===expected);
assert(reportDateLabel({work_date:'2026-10-18',...tbReportPeriod('2026-10-18','19:00','07:00')})==='18/19.10.26');
assert(reportDateLabel({work_date:'2026-12-31',...tbReportPeriod('2026-12-31','19:00','07:00')})==='31.12.26/01.01.27');
try{tbReportPeriod('2026-10-18','07:00','07:00');throw Error('equal accepted')}catch(e){assert(!e.message.includes('accepted'))}
for(const value of ['47.59699, 10.253655','47,59699; 10,253655','https://www.google.com/maps/@47.59699,10.253655,15z'])assert(Math.abs(tbGpsPair(value).lon-10.253655)<1e-8);
for(const value of ['91, 10','47, 181','abc']){try{tbGpsPair(value);throw Error('invalid accepted')}catch(e){assert(!e.message.includes('accepted'))}}
photos=[{id:'1',photo_metadata:{btbEntryId:'entry1',lat:47,lon:10}},{id:'2',photo_metadata:{btbEntryId:'entry2'}}];
assert(entryBoundPhotos('entry1').length===1&&entryBoundPhotos('entry2')[0].id==='2');
photos[0].photo_metadata.btbEntryId='entry2';assert(entryBoundPhotos('entry1').length===0&&entryBoundPhotos('entry2').length===2);
photos[0].photo_metadata.btbEntryId=null;assert(entryBoundPhotos('entry2').length===1&&photos.length===2);
`,ui);
console.log('PORTAL-017 EXIF endian/JPEG/bounds, GPS parsing, overnight/month/year, stable entry binding PASS');
