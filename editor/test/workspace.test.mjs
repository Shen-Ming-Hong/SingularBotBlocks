import test from 'node:test';
import assert from 'node:assert/strict';
import * as Blockly from 'blockly';
import {ACTION_BLOCKS,migrateActionBlocks,registerTurtleBlocks,loadWorkspace,preview,TURTLE_COMMANDS,toolbox,searchItems,setItemCatalog,catalogItems,setItemIdText,ensureStartBlock,markLooseStacks,variableFlyout,functionFlyout} from '../src/blocks.js';
registerTurtleBlocks();

test('all foundation turtle commands generate distinct Lua calls',()=>{
  for(const command of TURTLE_COMMANDS){
    const w=new Blockly.Workspace();
    try {
      const block=w.newBlock('ccedu_turtle_command');
      block.setFieldValue(command,'COMMAND');
      assert.equal(preview(w).trim(),`turtle.${command}()`);
    }finally{w.dispose();}
  }
});
test('each action block offers only its own directions and generates the same Lua call',()=>{
  const covered=[];
  for(const [type,[, ,choices]] of Object.entries(ACTION_BLOCKS)){
    assert.ok(choices.length<=6,type);
    for(const [, ,command] of choices){covered.push(command);const w=new Blockly.Workspace();
      try{const block=w.newBlock(type);block.setFieldValue(command,'COMMAND');assert.equal(preview(w).trim(),`turtle.${command}()`);}finally{w.dispose();}}
  }
  assert.deepEqual(covered.sort(),TURTLE_COMMANDS.filter(c=>!/^(detect|compare)/.test(c)).sort());
  const offered=toolbox('zh_tw').contents.flatMap(c=>c.contents||[]).map(b=>b.type);
  for(const type of Object.keys(ACTION_BLOCKS))assert.ok(offered.includes(type),type);
  assert.ok(!offered.includes('ccedu_turtle_command'));assert.ok(!offered.includes('ccedu_turtle_action'));
});
test('programs saved with the old action blocks open with the new blocks and run the same',()=>{
  const saved={blocks:{languageVersion:0,blocks:[{type:'ccedu_start',id:'start',next:{block:{type:'ccedu_turtle_command',id:'go',fields:{COMMAND:'forward'},next:{block:{type:'ccedu_turtle_command',id:'mine',fields:{COMMAND:'digUp'},next:{block:{type:'ccedu_turtle_command',id:'look',fields:{COMMAND:'detect'},next:{block:{type:'ccedu_turtle_action',id:'give',fields:{COMMAND:'drop'},inputs:{ARG:{shadow:{type:'math_number',id:'n',fields:{NUM:3}}}}}}}}}}}}}]}};
  const copy=JSON.stringify(saved),w=new Blockly.Workspace();
  try{
    loadWorkspace(w,saved);
    assert.equal(JSON.stringify(saved),copy,'the saved data itself is not modified');
    assert.equal(w.getBlockById('go').type,'ccedu_move');assert.equal(w.getBlockById('mine').type,'ccedu_dig');
    assert.equal(w.getBlockById('look').type,'ccedu_turtle_command');assert.equal(w.getBlockById('give').type,'ccedu_turtle_action');
    const lua=preview(w);for(const call of ['turtle.forward()','turtle.digUp()','turtle.detect()','turtle.drop(3)'])assert.ok(lua.includes(call),call);
    assert.deepEqual(migrateActionBlocks([1,'a',null]),[1,'a',null]);
  }finally{w.dispose();}
});
test('nested Blockly workspace keeps IDs and Lua across a save/load roundtrip',()=>{
  const first=new Blockly.Workspace(),second=new Blockly.Workspace();
  try {
    loadWorkspace(first,{blocks:{languageVersion:0,blocks:[{type:'controls_repeat_ext',id:'repeat',inputs:{TIMES:{shadow:{type:'math_number',fields:{NUM:3}}},DO:{block:{type:'ccedu_turtle_command',id:'move',fields:{COMMAND:'forward'}}}}}]}});
    const originalLua=preview(first);
    loadWorkspace(second,Blockly.serialization.workspaces.save(first));
    assert.equal(preview(second),originalLua);
    assert.ok(originalLua.includes('turtle.forward()'));
    assert.ok(second.getBlockById('move'));
    assert.equal(second.getAllBlocks(false).length,3);
  }finally{first.dispose();second.dispose();}
});
test('invalid workspace root is rejected before loading',()=>{
  const w=new Blockly.Workspace();
  try{assert.throws(()=>loadWorkspace(w,[]),/object/);}finally{w.dispose();}
});
test('all offered blocks produce Lua and the loop toolbox only offers break',()=>{
  const offered=toolbox('en_us').contents.flatMap(c=>c.contents||[]).map(b=>b.type);
  assert.ok(offered.includes('ccedu_break'));assert.ok(!offered.includes('controls_flow_statements'));
  for(const type of offered){const w=new Blockly.Workspace();try{const block=w.newBlock(type);if(type==='controls_for')block.setFieldValue(w.createVariable('i').getId(),'VAR');assert.equal(typeof preview(w),'string',type);}finally{w.dispose();}}
});
test('boolean action can be used as a condition without an assertion',()=>{
  const w=new Blockly.Workspace();try{loadWorkspace(w,{blocks:{blocks:[{type:'controls_if',id:'branch',inputs:{IF0:{block:{type:'ccedu_turtle_result',id:'action',fields:{COMMAND:'forward'}}}}}]}});assert.ok(preview(w).includes('if turtle.forward() then'));}finally{w.dispose();}
});

test('item ID blocks keep saved IDs, generate Lua strings and reject invalid IDs',()=>{
  const first=new Blockly.Workspace(),second=new Blockly.Workspace();
  try {
    loadWorkspace(first,{blocks:{languageVersion:0,blocks:[{type:'ccedu_registry_name',id:'ore',fields:{NAME:'create:zinc_ore'}}]}});
    const field=first.getBlockById('ore').getField('NAME');
    assert.ok(preview(first).includes("'create:zinc_ore'"));
    field.setValue('Not an ID');assert.equal(field.getValue(),'create:zinc_ore');
    loadWorkspace(second,Blockly.serialization.workspaces.save(first));
    assert.equal(second.getBlockById('ore').getFieldValue('NAME'),'create:zinc_ore');
    assert.equal(new Blockly.Workspace().newBlock('ccedu_registry_name').getFieldValue('NAME'),'minecraft:stone');
  }finally{first.dispose();second.dispose();}
});
test('item search matches Chinese, English and ID, ranks exact names first and filters by kind',()=>{
  const row=(id,zh,en,flags,icon=0)=>[id,zh,en,flags,icon];
  setItemCatalog({atlas:'a.png',icon:32,columns:64,rows:1,items:[row('minecraft:cobblestone','鵝卵石','Cobblestone',3),row('minecraft:stone','石頭','Stone',3),row('minecraft:stone_pickaxe','石鎬','Stone Pickaxe',2),row('minecraft:water','水','Water',1,-1),row('create:zinc_ore','鋅礦','Zinc Ore',3)]});
  const ids=(q,kind)=>searchItems(catalogItems(),q,kind).map(item=>item.id);
  assert.equal(ids('stone')[0],'minecraft:stone');
  assert.deepEqual(ids('stone').sort(),['minecraft:cobblestone','minecraft:stone','minecraft:stone_pickaxe']);
  assert.deepEqual(ids('石'),['minecraft:stone','minecraft:stone_pickaxe','minecraft:cobblestone']);
  assert.deepEqual(ids('鋅'),['create:zinc_ore']);
  assert.deepEqual(ids('ZINC ore'),['create:zinc_ore']);
  assert.deepEqual(ids('stone pick'),['minecraft:stone_pickaxe']);
  assert.deepEqual(ids('','block'),['minecraft:cobblestone','minecraft:stone','minecraft:water','create:zinc_ore']);
  assert.deepEqual(ids('stone','item').sort(),['minecraft:cobblestone','minecraft:stone','minecraft:stone_pickaxe']);
  assert.deepEqual(ids('diamond'),[]);
});

test('loading a saved workspace fires no change events and leaves nothing to undo',async()=>{
  const w=new Blockly.Workspace(),events=[];
  try {
    w.addChangeListener(e=>events.push(e.type));
    loadWorkspace(w,{blocks:{languageVersion:0,blocks:[{type:'ccedu_turtle_command',id:'a',fields:{COMMAND:'forward'}}]}});
    await new Promise(r=>setTimeout(r,20));
    assert.deepEqual(events,[]);assert.equal(w.getUndoStack().length,0);assert.ok(w.getBlockById('a'));
    w.getBlockById('a').setFieldValue('back','COMMAND');await new Promise(r=>setTimeout(r,20));
    assert.ok(events.includes('change'));
  }finally{w.dispose();}
});

test('beginner classes show only the item look; classes that read Lua keep the ID text',()=>{
  setItemCatalog({atlas:'a.png',icon:32,columns:64,rows:1,items:[['minecraft:diamond','鑽石','Diamond',2,0],['minecraft:fire','火','Fire',1,-1]]});
  const w=new Blockly.Workspace();
  try {
    const field=type=>{const b=w.newBlock('ccedu_registry_name');b.setFieldValue(type,'NAME');const f=b.getField('NAME');f.hasIcon_=type==='minecraft:diamond';return f;};
    const diamond=field('minecraft:diamond'),fire=field('minecraft:fire');
    setItemIdText(false);
    assert.equal(diamond.getText(),'');assert.equal(fire.getText(),'Fire');
    assert.ok(preview(w).includes("'minecraft:diamond'"));
    assert.equal(setItemIdText(true),true);assert.equal(setItemIdText(true),false);
    assert.equal(diamond.getText(),'minecraft:diamond');assert.equal(fire.getText(),'minecraft:fire');
  }finally{setItemIdText(false);w.dispose();}
});

test('a program gets one fixed start block above its topmost stack and only that stack runs',()=>{
  const w=new Blockly.Workspace();
  try {
    loadWorkspace(w,{blocks:{languageVersion:0,blocks:[
      {type:'ccedu_turtle_command',id:'lower',x:40,y:300,fields:{COMMAND:'back'}},
      {type:'ccedu_turtle_command',id:'upper',x:40,y:100,fields:{COMMAND:'forward'},next:{block:{type:'ccedu_turtle_command',id:'then',fields:{COMMAND:'turnLeft'}}}}]}});
    const start=ensureStartBlock(w);
    assert.equal(start.getNextBlock().id,'upper');assert.equal(start.isDeletable(),false);assert.equal(ensureStartBlock(w),null);
    markLooseStacks(w);
    assert.equal(w.getBlockById('lower').isEnabled(),false);assert.equal(w.getBlockById('upper').isEnabled(),true);
    assert.match(w.getBlockById('lower').getTooltip(),/not run/);
    const lua=preview(w);assert.ok(lua.includes('turtle.forward()'));assert.ok(lua.includes('turtle.turnLeft()'));assert.ok(!lua.includes('turtle.back()'));
    w.getBlockById('then').nextConnection.connect(w.getBlockById('lower').previousConnection);markLooseStacks(w);
    assert.equal(w.getBlockById('lower').isEnabled(),true);assert.ok(preview(w).includes('turtle.back()'));
    const saved=Blockly.serialization.workspaces.save(w);
    assert.equal(saved.blocks.blocks.length,1);assert.equal(saved.blocks.blocks[0].type,'ccedu_start');
  }finally{w.dispose();}
});
test('an empty program gets a start block and loose value blocks are greyed out',()=>{
  const w=new Blockly.Workspace();
  try {
    ensureStartBlock(w);const id=w.newBlock('ccedu_registry_name');markLooseStacks(w);
    assert.equal(w.getBlocksByType('ccedu_start',false).length,1);assert.equal(id.isEnabled(),false);
  }finally{w.dispose();}
});

test('toolbox blocks come with editable default values for number and text inputs',()=>{
  for(const locale of ['en_us','zh_tw']){
    const entries=toolbox(locale).contents.flatMap(c=>c.contents||[]);
    // Optional slot argument (detect/compare ignore it, item count defaults to the selected slot) stays empty on purpose.
    const optional=new Set(['ccedu_turtle_query.SLOT']);
    for(const entry of entries){const w=new Blockly.Workspace();try{
      const block=Blockly.serialization.blocks.append({type:entry.type,inputs:entry.inputs},w);
      if(entry.type==='controls_for')block.setFieldValue(w.createVariable('i').getId(),'VAR');
      for(const input of block.inputList){
        if(input.type!==Blockly.inputs.inputTypes.VALUE)continue;const check=input.connection.getCheck(),key=`${entry.type}.${input.name}`;
        const wantsValue=!check||check.includes('Number')||check.includes('String');
        if(wantsValue&&!optional.has(key))assert.ok(input.connection.targetBlock()?.isShadow(),`${key} has a default value`);
      }
      assert.equal(typeof preview(w),'string',entry.type);
    }finally{w.dispose();}}
  }
  const loop=toolbox('en_us').contents.flatMap(c=>c.contents||[]).find(b=>b.type==='controls_repeat_ext'),w=new Blockly.Workspace();
  try{Blockly.serialization.blocks.append({type:loop.type,inputs:loop.inputs},w);assert.match(preview(w),/for count = 1, 10 do/);}finally{w.dispose();}
});
test('variable flyout pre-fills the set block with a number',()=>{
  const original=Blockly.Variables.flyoutCategory;
  Blockly.Variables.flyoutCategory=()=>[Blockly.utils.xml.textToDom('<block type="variables_set"></block>'),Blockly.utils.xml.textToDom('<block type="variables_get"></block>')];
  try{const [set,get]=variableFlyout(null);assert.equal(set.querySelector('value > shadow[type="math_number"] > field[name="NUM"]').textContent,'0');assert.equal(get.querySelector('value'),null);}
  finally{Blockly.Variables.flyoutCategory=original;}
});
test('inventory slot inputs keep whole numbers from 1 to 16, other numbers stay free',async()=>{
  const {clampSlotNumbers,clampSlot}=await import('../src/blocks.js');
  const w=new Blockly.Workspace();
  try{
    loadWorkspace(w,{blocks:{languageVersion:0,blocks:[
      {type:'ccedu_select',id:'select',inputs:{SLOT:{shadow:{type:'math_number',id:'s',fields:{NUM:99}}}}},
      {type:'ccedu_transfer',id:'transfer',inputs:{SLOT:{shadow:{type:'math_number',id:'t',fields:{NUM:0}}},COUNT:{shadow:{type:'math_number',id:'c',fields:{NUM:64}}}}},
      {type:'controls_repeat_ext',id:'repeat',inputs:{TIMES:{shadow:{type:'math_number',id:'n',fields:{NUM:40}}}}}]}});
    clampSlotNumbers(w);
    assert.equal(w.getBlockById('s').getFieldValue('NUM'),16);
    assert.equal(w.getBlockById('t').getFieldValue('NUM'),1);
    assert.equal(w.getBlockById('c').getFieldValue('NUM'),64);
    assert.equal(w.getBlockById('n').getFieldValue('NUM'),40);
    w.getBlockById('s').setFieldValue(3.6,'NUM');
    assert.equal(w.getBlockById('s').getFieldValue('NUM'),4,'typed values round to a whole slot');
    w.getBlockById('n').setFieldValue(-5,'NUM');
    assert.equal(w.getBlockById('n').getFieldValue('NUM'),-5);
    assert.deepEqual([clampSlot(-3),clampSlot(17),clampSlot('x'),clampSlot(8)],[1,16,1,8]);
  }finally{w.dispose();}
});

test('the Functions category calls every other saved program and offers Blockly functions',()=>{
  const category=toolbox('zh_tw').contents.find(c=>c.custom==='CCEDU_FUNCTIONS');
  assert.equal(category.name,'函式');assert.equal(toolbox('en_us').contents.find(c=>c.custom==='CCEDU_FUNCTIONS').name,'Functions');
  const w=new Blockly.Workspace();
  try{
    const programs=[{id:'a',name:'挖一條隧道'},{id:'me',name:'主程式'},{id:'b',name:'Say "hi" <now>'},{id:'c',name:'  '}];
    const flyout=functionFlyout(w,{programs,current:'me',locale:'zh_tw'});
    const calls=flyout.filter(el=>el.getAttribute('type')==='ccedu_call').map(el=>el.querySelector('field[name=NAME]').textContent);
    assert.deepEqual(calls,['挖一條隧道','Say "hi" <now>'],'the program being edited and blank names are left out');
    const types=flyout.map(el=>el.getAttribute('type')).filter(Boolean);
    for(const type of ['procedures_defnoreturn','procedures_defreturn'])assert.ok(types.includes(type),type);
    assert.equal(flyout[0].tagName.toLowerCase(),'label');
    const empty=functionFlyout(w,{programs:[{id:'me',name:'主程式'}],current:'me',locale:'en_us'});
    assert.ok(empty.some(el=>el.tagName.toLowerCase()==='label'&&/No other saved programs/.test(el.getAttribute('text'))));
    // A library call block made from the flyout generates the same call the server compiles.
    const call=Blockly.Xml.domToBlock(flyout.find(el=>el.getAttribute('type')==='ccedu_call'),w);
    assert.equal(preview(w).trim(),"edu.callProgram('挖一條隧道')");call.dispose();
  }finally{w.dispose();}
});
test('function definitions are never greyed out as stray stacks, but their calls follow the start block',()=>{
  const w=new Blockly.Workspace();
  try{
    ensureStartBlock(w);
    const def=w.newBlock('procedures_defnoreturn');def.setFieldValue('跳一跳','NAME');
    const body=w.newBlock('ccedu_move');def.getInput('STACK').connection.connect(body.previousConnection);
    const loose=w.newBlock('procedures_callnoreturn');loose.loadExtraState({name:'跳一跳'});
    const used=w.newBlock('procedures_callnoreturn');used.loadExtraState({name:'跳一跳'});
    w.getBlocksByType('ccedu_start',false)[0].nextConnection.connect(used.previousConnection);
    markLooseStacks(w);
    assert.ok(def.isEnabled()&&body.isEnabled()&&used.isEnabled());assert.ok(!loose.isEnabled());
  }finally{w.dispose();}
});
