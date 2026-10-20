import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {JSDOM} from 'jsdom';
import * as Blockly from 'blockly';
import {installTakeover} from '../src/takeover.js';

const tick=()=>new Promise(resolve=>setImmediate(resolve));
const active=(epoch=1,extra={})=>({epoch,phase:'active',library:'student',robot:'robot',teacher:'Teacher',token:'token',mine:false,canTakeover:false,writeBlocked:true,controlBlocked:true,...extra});
const released=epoch=>active(epoch,{phase:'',writeBlocked:false,controlBlocked:false});
function fixture(options={}){
 const document=new JSDOM(readFileSync(new URL('../src/index.html',import.meta.url),'utf8')).window.document;
 const workspace=new Blockly.Workspace(),calls=[];workspace.getToolbox=()=>null;let busy=false;
 const takeover=installTakeover({document,workspace,tr:a=>a,busy:()=>busy,save:async()=>calls.push('save'),reload:async()=>calls.push('reload'),ack:async(token,saved)=>calls.push(['ack',token,saved]),status:()=>{},visual:()=>true,compact:()=>false,...options});
 takeover.setToggle(async enabled=>calls.push(['toggle',enabled]));
 return {document,workspace,takeover,calls,busy:value=>{busy=value;}};
}
test('only an eligible teacher gets a switch, created after server permission arrives',async()=>{
 const f=fixture();assert.equal(f.document.getElementById('takeover'),null);
 f.takeover.update(active());assert.equal(f.document.getElementById('takeover'),null);
 f.takeover.update(released(2));await tick();f.calls.length=0;f.takeover.update({...released(3),canTakeover:true});
 const button=f.document.getElementById('takeover');assert.equal(button.getAttribute('role'),'switch');
 await button.onclick();assert.deepEqual(f.calls,['save',['toggle',true]]);
 f.takeover.update(active(4,{mine:true,canTakeover:true,writeBlocked:false,controlBlocked:false}));await tick();
 assert.equal(button.getAttribute('aria-checked'),'true');await button.onclick();assert.deepEqual(f.calls.slice(-2),['save',['toggle',false]]);
 f.workspace.dispose();
});
test('read-only blocks keep viewing but cannot be moved, deleted or edited, including keyboard clipboard commands',()=>{
 const f=fixture();const block=f.workspace.newBlock('math_number');
 assert.equal(block.isEditable(),true);f.takeover.update(active());
 assert.equal(block.isEditable(),false);assert.equal(block.isMovable(),false);assert.equal(block.isDeletable(),false);
 assert.equal(Blockly.ShortcutRegistry.registry.getRegistry().paste.preconditionFn(f.workspace),false);
 assert.equal(f.document.getElementById('luaEditor').readOnly,true);assert.equal(f.document.getElementById('programName').readOnly,true);
 for(const id of ['new','delete','copy','recover','save','run','stop'])assert.equal(f.document.getElementById(id).disabled,true,id);
 assert.equal(f.document.getElementById('close').disabled,false);assert.equal(f.document.getElementById('takeoverStatus').hidden,false);
 f.workspace.dispose();
});
test('another robot shares the code lock while retaining its own controls',()=>{
 const f=fixture();f.takeover.update(active(1,{controlBlocked:false}));
 assert.equal(f.takeover.writeBlocked,true);assert.equal(f.takeover.controlBlocked,false);
 assert.equal(f.document.getElementById('run').disabled,false);assert.equal(f.document.getElementById('programs').disabled,false);
 assert.equal(f.document.getElementById('new').disabled,true);f.workspace.dispose();
});
test('preparation waits for a drag or composition and acknowledges only after the final save resolves',async()=>{
 let finishSave,started=0;const f=fixture({save:()=>{started++;return new Promise(resolve=>finishSave=resolve);}});
 f.busy(true);const pending=f.takeover.prepare(active(1,{phase:'preparing',drain:true}));
 await tick();assert.equal(started,0);assert.equal(f.workspace.options.readOnly,false);
 f.busy(false);await new Promise(resolve=>setTimeout(resolve,40));
 assert.equal(started,1);assert.equal(f.workspace.options.readOnly,true);assert.deepEqual(f.calls,[]);
 finishSave();await pending;assert.deepEqual(f.calls,[['ack','token',true]]);f.workspace.dispose();
});
test('failed saving cancels the handoff without reloading over the student draft',async()=>{
 const f=fixture({save:async()=>{throw new Error('Version conflict');}});
 await f.takeover.prepare(active(1,{phase:'preparing',drain:true}));assert.deepEqual(f.calls,[['ack','token',false]]);
 f.takeover.update(released(2));await tick();assert.equal(f.takeover.writeBlocked,false);assert.equal(f.document.getElementById('new').disabled,false);
 assert.ok(!f.calls.includes('reload'));f.workspace.dispose();
});
test('releasing reloads the latest program before re-enabling input and preserves originally disabled buttons',async()=>{
 let loaded,restored=false;const f=fixture({reload:()=>new Promise(resolve=>loaded=resolve),restore:()=>{restored=true;}});
 f.takeover.update(active());f.takeover.update(released(2));
 assert.equal(f.takeover.writeBlocked,true);assert.equal(f.document.getElementById('run').disabled,true);assert.equal(restored,false);
 loaded();await tick();assert.equal(restored,true);assert.equal(f.takeover.writeBlocked,false);assert.equal(f.document.getElementById('run').disabled,false);
 assert.equal(f.document.getElementById('pause').disabled,true);f.workspace.dispose();
});
test('a failed reload remains read-only until a later state update can reload successfully',async()=>{
 let fail=true;const f=fixture({reload:async()=>{if(fail)throw new Error('Connection lost');}});
 f.takeover.update(active());f.takeover.update(released(2));await tick();
 assert.equal(f.takeover.writeBlocked,true);fail=false;f.takeover.update(released(2));await tick();assert.equal(f.takeover.writeBlocked,false);f.workspace.dispose();
});
test('stale notices cannot re-enter an old handoff and a cancelled drag does not acknowledge it',async()=>{
 const f=fixture();f.busy(true);const pending=f.takeover.prepare(active(1,{phase:'preparing',drain:true}));
 f.takeover.update(released(2));await pending;f.takeover.update(active(1));
 assert.equal(f.takeover.writeBlocked,false);assert.deepEqual(f.calls,[]);f.workspace.dispose();
});
