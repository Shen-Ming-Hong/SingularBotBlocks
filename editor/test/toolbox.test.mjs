import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';

// A small browser for Blockly: jsdom lays nothing out, so text and shapes get a fixed size.
const dom=new JSDOM('<!doctype html><body></body>'),win=dom.window;
win.SVGElement.prototype.getBBox=()=>({x:0,y:0,width:20,height:20});
win.SVGElement.prototype.getComputedTextLength=()=>20;
win.HTMLCanvasElement.prototype.getContext=()=>({font:'',measureText:()=>({width:20})});
win.requestAnimationFrame=callback=>setTimeout(callback,0);win.cancelAnimationFrame=clearTimeout;
for(const key of Object.getOwnPropertyNames(win))if(!(key in globalThis))try{globalThis[key]=win[key];}catch{}
globalThis.window=win;globalThis.document=win.document;
// jsdom has no PointerEvent; Blockly's toolbox listens for pointerdown.
globalThis.PointerEvent=class extends win.MouseEvent{constructor(type,init={}){super(type,init);this.pointerId=1;this.pointerType='mouse';}};
const Blockly=await import('blockly');
const {openOnlyToolbox}=await import('../src/toolbox.js');

function inject(plugins){
 const host=document.createElement('div');document.body.append(host);
 return Blockly.inject(host,{plugins,toolbox:{kind:'categoryToolbox',contents:[
  {kind:'category',name:'A',contents:[{kind:'block',type:'controls_if'}]},
  {kind:'category',name:'B',contents:[{kind:'block',type:'math_number'}]}]}});
}
const press=item=>document.getElementById(item.getId()).dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,button:0}));

test('clicking an open category again, as a doubled background click does, keeps its flyout open',()=>{
 const workspace=inject({toolbox:openOnlyToolbox(Blockly)});
 try{
  const box=workspace.getToolbox(),[a,b]=box.getToolboxItems();
  press(a);assert.equal(box.getSelectedItem(),a);assert.ok(box.getFlyout().isVisible());
  press(a);assert.equal(box.getSelectedItem(),a);assert.ok(box.getFlyout().isVisible());
  press(b);assert.equal(box.getSelectedItem(),b);assert.ok(box.getFlyout().isVisible());
  // Clicking the workspace still closes it, and the category opens again afterwards.
  workspace.hideChaff();assert.equal(box.getSelectedItem(),null);assert.ok(!box.getFlyout().isVisible());
  press(a);assert.equal(box.getSelectedItem(),a);assert.ok(box.getFlyout().isVisible());
 }finally{workspace.dispose();}
});

test('the stock toolbox closes the flyout on the second click, the failure being fixed',()=>{
 const workspace=inject({});
 try{
  const box=workspace.getToolbox(),[a]=box.getToolboxItems();
  press(a);assert.ok(box.getFlyout().isVisible());
  press(a);assert.ok(!box.getFlyout().isVisible());
 }finally{workspace.dispose();}
});

test('the editor installs the open-only toolbox',async()=>{
 const {readFileSync}=await import('node:fs');
 assert.match(readFileSync(new URL('../src/editor.js',import.meta.url),'utf8'),/plugins:\{toolbox:openOnlyToolbox\(Blockly\)\}/);
});
test.after(()=>win.close());

test('the block right-click menu has no copy item besides 複製一份, and the workspace menu pastes what Cmd+C copied',async()=>{
 const {registerClipboardMenu}=await import('../src/toolbox.js'),{CLASSROOM_ZH}=await import('../src/wording.js');
 const saved={...Blockly.Msg};Object.assign(Blockly.Msg,CLASSROOM_ZH);
 registerClipboardMenu(Blockly,(zh)=>zh);
 const workspace=inject({toolbox:openOnlyToolbox(Blockly)});
 try{
  const {ContextMenuRegistry}=Blockly,block=workspace.newBlock('math_number');block.initSvg();block.render();
  assert.equal(ContextMenuRegistry.registry.getItem('ccedu_copy'),null);
  const texts=ContextMenuRegistry.registry.getContextMenuOptions(ContextMenuRegistry.ScopeType.BLOCK,{block}).map(o=>o.text);
  assert.deepEqual(texts.filter(t=>t.includes('複製')),['複製一份'],texts.join());
  const paste=ContextMenuRegistry.registry.getItem('ccedu_paste');
  Blockly.common.setSelected(block);Blockly.ShortcutRegistry.registry.getRegistry().copy.callback(workspace,{preventDefault(){}});
  paste.callback({workspace});
  assert.equal(workspace.getBlocksByType('math_number').length,2);
  // The game delivers each click twice, so a gesture has started again by the time a menu item runs; paste must
  // still work then.
  const gesture=workspace.getGesture(new PointerEvent('pointerdown',{clientX:5,clientY:5}));
  assert.ok(gesture&&Blockly.Gesture.inProgress());
  paste.callback({workspace});
  assert.equal(workspace.getBlocksByType('math_number').length,3);
  workspace.cancelCurrentGesture();
 }finally{workspace.dispose();Object.assign(Blockly.Msg,saved);}
});

test('pasting puts the block where the menu was opened',async()=>{
 const {registerClipboardMenu}=await import('../src/toolbox.js'),notes=[];
 const workspace=inject({toolbox:openOnlyToolbox(Blockly)});
 try{
  const {ContextMenuRegistry}=Blockly,paste=()=>ContextMenuRegistry.registry.getItem('ccedu_paste');
  registerClipboardMenu(Blockly,(zh)=>zh,text=>notes.push(text));
  const block=workspace.newBlock('math_number');block.initSvg();block.render();
  Blockly.common.setSelected(block);Blockly.ShortcutRegistry.registry.getRegistry().copy.callback(workspace,{preventDefault(){}});
  const at=Blockly.utils.svgMath.wsToScreenCoordinates(workspace,new Blockly.utils.Coordinate(300,200));
  paste().callback({workspace},{clientX:at.x,clientY:at.y});
  assert.equal(notes.at(-1),'');
  const pasted=Blockly.common.getSelected();
  assert.notEqual(pasted,block);
  const xy=pasted.getRelativeToSurfaceXY();assert.ok(Math.abs(xy.x-300)<1&&Math.abs(xy.y-200)<1,JSON.stringify(xy));
 }finally{workspace.dispose();}
});

test('a block right-click menu never shows two items with the same words, in Chinese or English',async()=>{
 const {registerClipboardMenu}=await import('../src/toolbox.js'),{CLASSROOM_ZH}=await import('../src/wording.js');
 const saved={...Blockly.Msg};
 try{
  for(const [zh,msg] of [[true,{...saved,...CLASSROOM_ZH}],[false,saved]]){
   Object.assign(Blockly.Msg,msg);registerClipboardMenu(Blockly,(a,b)=>zh?a:b);
   const workspace=inject({toolbox:openOnlyToolbox(Blockly)});
   try{
    const block=workspace.newBlock('math_number');block.initSvg();block.render();
    const texts=Blockly.ContextMenuRegistry.registry.getContextMenuOptions(Blockly.ContextMenuRegistry.ScopeType.BLOCK,{block}).map(o=>o.text);
    assert.ok(texts.length>3,texts.join());
    assert.deepEqual(texts,[...new Set(texts)],texts.join());
   }finally{workspace.dispose();}
  }
 }finally{Object.assign(Blockly.Msg,saved);}
});

test('Shift+F10, the menu key or Option+M opens the right-click menu of the selected block, or of the workspace when none is selected',async()=>{
 const {registerClipboardMenu,installMenuKey}=await import('../src/toolbox.js');
 registerClipboardMenu(Blockly,(zh)=>zh);
 const workspace=inject({toolbox:openOnlyToolbox(Blockly)}),doc=new JSDOM('<!doctype html><body><input></body>').window.document;
 // A separate document keeps this test's listeners away from the others.
 installMenuKey(Blockly,workspace,doc);
 const key=(init,target=doc.body)=>target.dispatchEvent(new doc.defaultView.KeyboardEvent('keydown',{bubbles:true,cancelable:true,...init}));
 const items=()=>[...document.querySelectorAll('.blocklyContextMenu .blocklyMenuItemContent')].map(n=>n.textContent);
 try{
  const block=workspace.newBlock('math_number');block.initSvg();block.render();
  key({key:'F10',shiftKey:true});assert.ok(items().includes('貼上'),items().join());assert.ok(!items().includes(Blockly.Msg.DUPLICATE_BLOCK));
  workspace.hideChaff();
  Blockly.common.setSelected(block);
  key({key:'F10'});assert.equal(Blockly.WidgetDiv.isVisible(),false,'plain F10 does nothing');
  key({key:'F10',shiftKey:true});assert.ok(items().includes(Blockly.Msg.DUPLICATE_BLOCK),items().join());
  workspace.hideChaff();
  key({key:'ContextMenu'});assert.ok(items().includes(Blockly.Msg.DUPLICATE_BLOCK));
  workspace.hideChaff();
  key({key:'µ',code:'KeyM',altKey:true});assert.ok(items().includes(Blockly.Msg.DUPLICATE_BLOCK),'Option+M');
  workspace.hideChaff();
  key({key:'M',code:'KeyM',altKey:true,shiftKey:true});assert.equal(Blockly.WidgetDiv.isVisible(),false,'Option+Shift+M does nothing');
  // Typing in a text box keeps the key for the box.
  const input=doc.querySelector('input');input.focus();
  key({key:'F10',shiftKey:true},input);assert.equal(Blockly.WidgetDiv.isVisible(),false);
 }finally{workspace.hideChaff();workspace.dispose();}
});

test('a keyboard-opened block menu is placed from the block position and kept inside the workspace',async()=>{
 const {blockMenuAnchor}=await import('../src/toolbox.js');
 const workspace=inject({toolbox:openOnlyToolbox(Blockly)});
 const svg=workspace.getParentSvg(),div=workspace.getInjectionDiv();
 const rect=(left,top,width,height)=>()=>({left,top,width,height,right:left+width,bottom:top+height,x:left,y:top});
 try{
  svg.getBoundingClientRect=rect(100,50,600,400);div.getBoundingClientRect=rect(100,50,600,400);
  const block=workspace.newBlock('math_number');block.initSvg();block.render();block.moveBy(120,80);
  // The SVG group's own box is ignored: a wide one put the hat block's menu at the window's left edge.
  block.getSvgRoot().getBoundingClientRect=rect(0,0,2000,2000);
  const corner=Blockly.utils.svgMath.wsToScreenCoordinates(workspace,block.getRelativeToSurfaceXY()),inset=Math.round(16*workspace.scale);
  assert.ok(corner.x>=100&&corner.y>=50,JSON.stringify(corner));
  assert.deepEqual(blockMenuAnchor(Blockly,workspace,block),{clientX:corner.x+inset,clientY:corner.y+inset});
  // A block scrolled out of view still gets its menu on the visible workspace.
  svg.getBoundingClientRect=rect(corner.x+300,corner.y+300,200,200);
  assert.deepEqual(blockMenuAnchor(Blockly,workspace,block),{clientX:corner.x+308,clientY:corner.y+308});
  svg.getBoundingClientRect=rect(corner.x-300,corner.y-300,100,100);
  assert.deepEqual(blockMenuAnchor(Blockly,workspace,block),{clientX:corner.x-208,clientY:corner.y-208});
 }finally{workspace.dispose();}
});



for(const mine of [true,false])test(`${mine?'teacher takeover':'student release'} restores pointers and fields of the existing program`,async()=>{
 const {readFileSync}=await import('node:fs'),{loadWorkspace}=await import('../src/blocks.js'),{installTakeover}=await import('../src/takeover.js');
 const controls=new JSDOM(readFileSync(new URL('../src/index.html',import.meta.url),'utf8'));
 const workspace=inject({}),original=Blockly.BlockSvg.prototype.onMouseDown;let presses=0;
 Blockly.BlockSvg.prototype.onMouseDown=function(){presses++;};
 const saved={blocks:{languageVersion:0,blocks:[{type:'math_number',id:'student-number',fields:{NUM:7}},{type:'math_number',id:'fixed-number',editable:false,movable:false,deletable:false}]}};
 const takeover=installTakeover({document:controls.window.document,workspace,tr:a=>a,busy:()=>false,save:async()=>{},reload:async()=>{
  assert.equal(workspace.options.readOnly,true);loadWorkspace(workspace,saved);
  assert.equal(workspace.getBlockById('student-number').isEditable(),false);
 },ack:async()=>{},status:()=>{},visual:()=>true,compact:()=>false});
 try{
  if(!mine)takeover.update({epoch:1,phase:'active',writeBlocked:true,controlBlocked:true});
  takeover.update({epoch:2,phase:mine?'active':'',mine,writeBlocked:false,controlBlocked:false});
  await new Promise(resolve=>setImmediate(resolve));
  const block=workspace.getBlockById('student-number'),field=block.getField('NUM'),fixed=workspace.getBlockById('fixed-number');
  assert.equal(workspace.options.readOnly,false);
  block.getSvgRoot().dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,button:0}));
  assert.equal(presses,1,'the original student block must keep its pointer listener');
  assert.equal(block.isMovable(),true);assert.equal(block.isDeletable(),true);
  assert.equal(field.isCurrentlyEditable(),true);assert.ok(field.getSvgRoot().classList.contains('blocklyEditableText'));
  field.setValue(9);assert.equal(block.getFieldValue('NUM'),9);
  assert.equal(fixed.isEditable(),false);assert.equal(fixed.isMovable(),false);assert.equal(fixed.isDeletable(),false);
  takeover.update({epoch:3,phase:'active',writeBlocked:true,controlBlocked:true});
  assert.equal(block.isEditable(),false);assert.ok(field.getSvgRoot().classList.contains('blocklyNonEditableText'));
  assert.equal(Blockly.ShortcutRegistry.registry.getRegistry().paste.preconditionFn(workspace),false);
 }finally{Blockly.BlockSvg.prototype.onMouseDown=original;workspace.dispose();controls.window.close();}
});
