import test from 'node:test';
import assert from 'node:assert/strict';
import * as Blockly from 'blockly';
import * as TraditionalChinese from 'blockly/msg/zh-hant';
import {registerTurtleBlocks,toolbox} from '../src/blocks.js';
import {CLASSROOM_ZH} from '../src/wording.js';

// Same order as the editor: Blockly's zh-hant pack first, then the classroom wording on top.
Blockly.setLocale(TraditionalChinese);registerTurtleBlocks('zh_tw');
// Programming-manual words a third grader does not know, and names that differ from Minecraft's official zh_tw.
const HARD=['賦值','區塊','循環','陳述式','返回','傳回','串連','字串','評論','再製','true','false','背包','海龜','吸取'];
const MENU_KEYS=['DUPLICATE_BLOCK','ADD_COMMENT','DELETE_BLOCK','DELETE_X_BLOCKS','DELETE_ALL_BLOCKS','CLEAN_UP','COLLAPSE_BLOCK','EXPAND_BLOCK','DISABLE_BLOCK','ENABLE_BLOCK','INLINE_INPUTS','EXTERNAL_INPUTS','NEW_VARIABLE','NEW_VARIABLE_TITLE','RENAME_VARIABLE','RENAME_VARIABLE_TITLE','DELETE_VARIABLE','DELETE_VARIABLE_CONFIRMATION','VARIABLE_ALREADY_EXISTS'];
const hard=text=>HARD.filter(word=>String(text).includes(word));

function texts(block){
 const out=[Blockly.Tooltip.getTooltipOfObject(block)];
 for(const input of block.inputList)for(const field of input.fieldRow){
  out.push(field.getText());
  if(field instanceof Blockly.FieldDropdown)for(const [label] of field.getOptions(false))if(typeof label==='string')out.push(label);
 }
 return out.filter(Boolean);
}

test('every block a student can drag out reads in classroom Chinese',()=>{
 const w=new Blockly.Workspace();
 try{
  const v=w.createVariable('高度');
  const types=toolbox('zh_tw').contents.flatMap(c=>c.contents||[]).map(b=>b.type).concat(['variables_set','variables_get','math_change','controls_flow_statements','procedures_defnoreturn','procedures_defreturn','procedures_callnoreturn','procedures_callreturn','procedures_ifreturn']);
  for(const type of types){
   const block=w.newBlock(type);if(block.getField('VAR'))block.setFieldValue(v.getId(),'VAR');
   for(const text of texts(block))assert.deepEqual(hard(text),[],`${type}: ${text}`);
  }
 }finally{w.dispose();}
});

test('menus and variable dialogs avoid the hard words too',()=>{
 for(const key of MENU_KEYS)assert.deepEqual(hard(Blockly.Msg[key]),[],key);
 for(const [key,value] of Object.entries(CLASSROOM_ZH)){
  assert.deepEqual(hard(value),[],key);
  // Placeholders must survive the rewording, or Blockly drops the inputs they stand for.
  const marks=s=>(s.match(/%\d/g)||[]).sort().join();
  assert.equal(marks(value),marks(TraditionalChinese[key]),key);
 }
});

test('the set-variable block says what it does instead of 賦值',()=>{
 const w=new Blockly.Workspace();
 try{
  const v=w.createVariable('分數'),set=w.newBlock('variables_set');set.setFieldValue(v.getId(),'VAR');
  assert.equal(set.toString(),'把 分數 設成 ?');
  assert.equal(w.newBlock('logic_boolean').getField('BOOL').getText(),'是');
 }finally{w.dispose();}
});

test('game words match Minecraft official Traditional Chinese',()=>{
 const names=toolbox('zh_tw').contents.map(c=>c.name);
 assert.ok(names.includes('物品欄'));assert.ok(!names.includes('背包'));
 const w=new Blockly.Workspace();
 try{
  const drop=w.newBlock('ccedu_drop').getField('COMMAND'),suck=w.newBlock('ccedu_suck');
  assert.match(drop.getSourceBlock().toString(),/丟棄/);assert.match(suck.toString(),/撿起/);
  assert.match(w.newBlock('ccedu_redstone_query').toString(),/紅石訊號/);
 }finally{w.dispose();}
});

test('function blocks read as making and using a function, in classroom Chinese',()=>{
 const w=new Blockly.Workspace();
 try{
  const offered=Blockly.Procedures.flyoutCategory(w).find(el=>el.getAttribute('type')==='procedures_defreturn'),def=Blockly.Xml.domToBlock(offered,w);
  assert.equal(def.getFieldValue('NAME'),'算出答案');assert.match(def.toString(),/^函式 算出答案/);assert.match(def.toString(),/交回答案/);
  def.loadExtraState({params:[{name:'格數',id:'n'}]});assert.match(def.toString(),/輸入： 格數/);
 }finally{w.dispose();}
});

test('true/false blocks read as statements, so 如果／只要／不是 in front of them still makes sense',()=>{
 const w=new Blockly.Workspace();
 try{
  for(const type of toolbox('zh_tw').contents.flatMap(c=>c.contents||[]).map(b=>b.type).concat('ccedu_compare')){
   const block=w.newBlock(type);
   if(block.outputConnection?.getCheck()?.includes('Boolean'))assert.ok(!/[嗎？]/.test(block.toString()),`${type}: ${block.toString()}`);
  }
  const loop=w.newBlock('controls_whileUntil'),not=w.newBlock('logic_negate'),look=w.newBlock('ccedu_detect');
  look.setFieldValue('down','SIDE');not.getInput('BOOL').connection.connect(look.outputConnection);loop.getInput('BOOL').connection.connect(not.outputConnection);
  assert.equal(loop.toString().replace(/[()]/g,''),'一直重複，只要 不是 下方 有方塊 做 ?');
 }finally{w.dispose();}
});
