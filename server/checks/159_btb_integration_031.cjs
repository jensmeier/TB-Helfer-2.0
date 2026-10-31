// Execute the production portal guard, no browser/NAS claim.
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync(path.join(__dirname,'../templates/v1_btb.html'),'utf8');
const start=source.indexOf('function canEditCurrentBtb(){'),end=source.indexOf('function participantLabel(',start);
const state={current:{status:'REWORK'},activeBtbId:'new-shift',btbId:'old',allowed:true,lead:true,finished:false,excluded:false};
state.can=()=>state.allowed;state.isTeamLead=()=>state.lead;state.ownShiftFinished=()=>state.finished;state.ownShiftNotParticipating=()=>state.excluded;
vm.createContext(state);vm.runInContext(source.slice(start,end),state);
assert.strictEqual(state.canEditCurrentBtb(),true);
state.current.status='FINALIZED';assert.strictEqual(state.canEditCurrentBtb(),false);
state.current.status='ACTIVE';assert.strictEqual(state.canEditCurrentBtb(),false);
state.activeBtbId='old';assert.strictEqual(state.canEditCurrentBtb(),true);
state.current.status='REWORK';state.activeBtbId='new-shift';state.lead=false;state.finished=true;assert.strictEqual(state.canEditCurrentBtb(),false);
state.finished=false;state.excluded=true;assert.strictEqual(state.canEditCurrentBtb(),false);
state.excluded=false;state.allowed=false;assert.strictEqual(state.canEditCurrentBtb(),false);
console.log('BTB-INTEGRATION-031 portal detached rework, stale shift, helper and permission guard PASS (7 cases)');
