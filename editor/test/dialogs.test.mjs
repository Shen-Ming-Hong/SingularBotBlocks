import test from 'node:test';
import assert from 'node:assert/strict';
import * as Blockly from 'blockly';
import * as TraditionalChinese from 'blockly/msg/zh-hant';
import {installDialogs,suggestName,variableDefault,NAME_LIMIT} from '../src/dialogs.js';

Blockly.setLocale(TraditionalChinese);
const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
class Element extends EventTarget{hidden=false;textContent='';value='';disabled=false;maxLength=-1;focused=0;focus(){this.focused++;}select(){}click(){this.dispatchEvent(new Event('click'));}}
// Like a browser <dialog>: close() fires its close event later, as a separate task.
class Dialog extends Element{open=false;showModal(){if(this.open)throw new Error('already open');this.open=true;}close(){if(!this.open)return;this.open=false;setTimeout(()=>this.dispatchEvent(new Event('close')));}}
function page(){
 const el={askDialog:new Dialog()};for(const id of ['askForm','askMessage','askInput','askError','askOk','askCancel'])el[id]=new Element();
 const workspace=new Blockly.Workspace();
 installDialogs(Blockly,{$:id=>el[id],tr:a=>a,newName:variableDefault(Blockly,workspace,'變數')});
 const submit=()=>el.askForm.dispatchEvent(new Event('submit',{cancelable:true}));
 const type=text=>{el.askInput.value=text;el.askInput.dispatchEvent(new Event('input'));};
 return {el,workspace,submit,type};
}

test('creating a variable asks inside the editor with a free default name and accepts a Chinese name',async()=>{
 const {el,workspace,submit,type}=page();
 workspace.createVariable('變數1');
 Blockly.Variables.createVariableButtonHandler(workspace);
 assert.equal(el.askDialog.open,true);
 assert.equal(el.askMessage.textContent,'新變數名稱：');
 assert.equal(el.askInput.hidden,false);assert.equal(el.askCancel.hidden,false);
 assert.equal(el.askInput.value,'變數2');assert.equal(el.askInput.focused,1);
 assert.equal(el.askInput.maxLength,NAME_LIMIT);
 type('  礦石數量  ');submit();
 assert.equal(el.askDialog.open,false);
 assert.ok(workspace.getVariable('礦石數量'));
 await tick();assert.equal(workspace.getAllVariables().length,2);
 workspace.dispose();
});

test('an empty name keeps the dialog open and explains why',()=>{
 const {el,workspace,submit,type}=page();
 Blockly.Variables.createVariableButtonHandler(workspace);
 type('   ');assert.equal(el.askOk.disabled,true);assert.equal(el.askError.textContent,'名稱不能空白');
 submit();assert.equal(el.askDialog.open,true);assert.equal(workspace.getAllVariables().length,0);
 type('x');assert.equal(el.askOk.disabled,false);assert.equal(el.askError.textContent,'');
 submit();assert.ok(workspace.getVariable('x'));
 workspace.dispose();
});

test('a duplicate name shows an in-editor notice, then asks again',async()=>{
 const {el,workspace,submit,type}=page();
 workspace.createVariable('高度');
 Blockly.Variables.createVariableButtonHandler(workspace);
 type('高度');submit();
 assert.equal(el.askDialog.open,true);assert.equal(el.askMessage.textContent,'已存在變數「高度」。');
 assert.equal(el.askInput.hidden,true);assert.equal(el.askCancel.hidden,true);
 submit();
 assert.equal(el.askDialog.open,true);assert.equal(el.askMessage.textContent,'新變數名稱：');assert.equal(el.askInput.hidden,false);
 // The close event of the notice arrives after the prompt reopened and must not cancel it.
 await tick();assert.equal(el.askDialog.open,true);
 type('寬度');submit();assert.ok(workspace.getVariable('寬度'));
 workspace.dispose();
});

test('renaming starts from the current name and closing the dialog cancels',async()=>{
 const {el,workspace,submit,type}=page();
 const variable=workspace.createVariable('a');
 Blockly.Variables.renameVariable(workspace,variable);
 assert.equal(el.askInput.value,'a');assert.equal(el.askMessage.textContent,'將所有「a」變數重新命名為：');
 type('距離');submit();assert.equal(variable.name,'距離');
 await tick();
 Blockly.Variables.renameVariable(workspace,variable);
 // The game's Escape key closes whichever dialog is open.
 type('不要');el.askDialog.close();await tick();
 assert.equal(el.askDialog.open,false);assert.equal(variable.name,'距離');
 workspace.dispose();
});

test('deleting a variable used by several blocks confirms in the editor',async()=>{
 const {el,workspace}=page();
 const variable=workspace.createVariable('n');
 for(let i=0;i<2;i++)workspace.newBlock('variables_get').setFieldValue(variable.getId(),'VAR');
 workspace.deleteVariableById(variable.getId());
 assert.equal(el.askDialog.open,true);assert.equal(el.askMessage.textContent,'刪除使用2次的「n」變數？');
 el.askCancel.click();assert.ok(workspace.getVariableById(variable.getId()));
 await tick();
 workspace.deleteVariableById(variable.getId());
 el.askForm.dispatchEvent(new Event('submit',{cancelable:true}));
 assert.equal(workspace.getVariableById(variable.getId()),null);assert.equal(workspace.getAllBlocks(false).length,0);
 workspace.dispose();
});

test('suggested names skip every name already taken',()=>{
 assert.equal(suggestName('變數',[]),'變數1');
 assert.equal(suggestName('變數',['變數1','變數2','變數4']),'變數3');
});

test('Enter that commits input-method text does not also accept the dialog',()=>{
 const el={askDialog:new Dialog()};for(const id of ['askForm','askMessage','askInput','askError','askOk','askCancel'])el[id]=new Element();
 let composing=true,answer;
 installDialogs(Blockly,{$:id=>el[id],tr:a=>a,composing:()=>composing});
 Blockly.dialog.prompt('名稱','',value=>answer=value);
 el.askInput.value='方塊';el.askForm.dispatchEvent(new Event('submit',{cancelable:true}));
 assert.equal(el.askDialog.open,true);assert.equal(answer,undefined);
 composing=false;el.askForm.dispatchEvent(new Event('submit',{cancelable:true}));
 assert.equal(answer,'方塊');
});
