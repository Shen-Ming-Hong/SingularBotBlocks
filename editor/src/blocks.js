import * as Blockly from 'blockly';
import {luaGenerator,Order} from 'blockly/lua';
import {CLASSROOM_ZH} from './wording.js';
export const TURTLE_COMMANDS=Object.freeze(['forward','back','up','down','turnLeft','turnRight','dig','digUp','digDown','place','placeUp','placeDown','attack','attackUp','attackDown','detect','detectUp','detectDown','compare','compareUp','compareDown','drop','dropUp','dropDown','suck','suckUp','suckDown','equipLeft','equipRight']);
const labels={forward:'前進',back:'後退',up:'上升',down:'下降',turnLeft:'左轉',turnRight:'右轉',dig:'挖掘前方',digUp:'挖掘上方',digDown:'挖掘下方',place:'放置前方',placeUp:'放置上方',placeDown:'放置下方',attack:'攻擊前方',attackUp:'攻擊上方',attackDown:'攻擊下方',drop:'丟棄到前方',dropUp:'丟棄到上方',dropDown:'丟棄到下方',suck:'撿起前方',suckUp:'撿起上方',suckDown:'撿起下方',equipLeft:'裝備左側',equipRight:'裝備右側',detect:'前方有方塊',detectUp:'上方有方塊',detectDown:'下方有方塊',compare:'前方是選取的方塊',compareUp:'上方是選取的方塊',compareDown:'下方是選取的方塊',getItemCount:'物品數量',getItemSpace:'還能放幾個',getSelectedSlot:'選取第幾格'};
// One toolbox block per kind of action, so each dropdown only holds that action's directions.
// Field values stay the turtle method names, so the generated Lua and highlighting are unchanged.
export const ACTION_BLOCKS=Object.freeze({
 ccedu_move:['移動 %1','move %1',[['前進','forward','forward'],['後退','back','back'],['上升','up','up'],['下降','down','down'],['左轉','turn left','turnLeft'],['右轉','turn right','turnRight']]],
 ccedu_dig:['挖掘 %1','dig %1',[['前方','front','dig'],['上方','above','digUp'],['下方','below','digDown']]],
 ccedu_place:['放置 %1','place %1',[['前方','front','place'],['上方','above','placeUp'],['下方','below','placeDown']]],
 ccedu_attack:['攻擊 %1','attack %1',[['前方','front','attack'],['上方','above','attackUp'],['下方','below','attackDown']]],
 ccedu_drop:['把選取的整組物品丟棄到 %1','drop the selected stack %1',[['前方','front','drop'],['上方','above','dropUp'],['下方','below','dropDown']]],
 ccedu_suck:['撿起 %1 的物品','pick up items %1',[['前方','front','suck'],['上方','above','suckUp'],['下方','below','suckDown']]],
 ccedu_equip:['裝備到 %1','equip %1',[['左側','left','equipLeft'],['右側','right','equipRight']]],
});
const actionBlockFor=command=>Object.keys(ACTION_BLOCKS).find(type=>ACTION_BLOCKS[type][2].some(o=>o[2]===command));
/** Programs saved with the old all-in-one action block open with the matching new block (same ID and command). */
export function migrateActionBlocks(saved){
 const visit=value=>{
  if(Array.isArray(value))return value.map(visit);if(!value||typeof value!=='object')return value;
  const out={};for(const [key,child] of Object.entries(value))out[key]=visit(child);
  if(out.type==='ccedu_turtle_command'&&typeof out.fields?.COMMAND==='string'){const type=actionBlockFor(out.fields.COMMAND);if(type)out.type=type;}
  return out;
 };
 return visit(saved);
}
export const ITEM_ID=/^[a-z0-9_.-]+:[a-z0-9/._-]+$/;
// Every registered item and block in the current game (mods included), sent once by the client.
let catalog=null,pickerZh=false,openPicker=null;
export function setItemCatalog(data){
 const items=data.items.map(([id,zh,en,flags,icon])=>({id,zh,en,block:(flags&1)!==0,item:(flags&2)!==0,icon,search:[id,id.slice(id.indexOf(':')+1).replace(/_/g,' '),zh,en].join('\n').toLowerCase()}));
 catalog={atlas:data.atlas,size:data.icon,columns:data.columns,rows:data.rows,items,byId:new Map(items.map(item=>[item.id,item]))};
 openPicker?.();
}
export const catalogItems=()=>catalog?.items||[];
const GAP=6,PAGE=160;
// Beginner worlds (no Lua) show only the item's look; the ID text stays for classes that read the generated Lua.
let itemText=false;
const iconSize=()=>itemText?22:30;
export function setItemIdText(show){if(itemText===!!show)return false;itemText=!!show;return true;}
const sprite=(item,size)=>`background-image:url("${catalog.atlas}");background-size:${catalog.columns*size}px ${catalog.rows*size}px;background-position:-${item.icon%catalog.columns*size}px -${Math.floor(item.icon/catalog.columns)*size}px`;
// Exact ID or name first, then prefixes, then any match; Chinese and English names and the ID all count.
export function searchItems(items,query,kind='all'){
 const q=query.trim().toLowerCase(),pool=items.filter(item=>kind==='all'||item[kind]);if(!q)return pool;
 const rank=item=>{const names=[item.id,item.id.slice(item.id.indexOf(':')+1),item.zh.toLowerCase(),item.en.toLowerCase()];return names.includes(q)?0:names.some(n=>n.startsWith(q))?1:2;};
 return pool.filter(item=>item.search.includes(q)).map(item=>[rank(item),item]).sort((a,b)=>a[0]-b[0]).map(([,item])=>item);
}
class FieldItemPicker extends Blockly.Field{
 constructor(value,validator,config){super(value??'minecraft:stone',validator,config);this.SERIALIZABLE=true;this.CURSOR='pointer';this.hasIcon_=false;}
 static fromJson(options){return new FieldItemPicker(options.value);}
 doClassValidation_(value){return typeof value==='string'&&ITEM_ID.test(value)?value:null;}
 initView(){
  super.initView();Blockly.utils.dom.addClass(this.fieldGroup_,'ccedu-item-field');
  this.icon_=Blockly.utils.dom.createSvgElement('svg',{width:22,height:22},this.fieldGroup_);
  this.iconImage_=Blockly.utils.dom.createSvgElement('image',{preserveAspectRatio:'none',style:'image-rendering:pixelated'},this.icon_);
 }
 getText_(){
  if(itemText)return null;if(!catalog)return '';const item=catalog.byId.get(this.getValue());
  return this.hasIcon_?'':item?(pickerZh?item.zh:item.en):null;
 }
 render_(){
  const item=catalog?.byId.get(this.getValue());this.hasIcon_=!!this.icon_&&!!item&&item.icon>=0;
  if(this.icon_){const size=String(iconSize());this.icon_.setAttribute('width',size);this.icon_.setAttribute('height',size);}
  if(this.icon_){this.icon_.style.display=this.hasIcon_?'':'none';
   if(this.hasIcon_){const size=catalog.size;this.icon_.setAttribute('viewBox',`${item.icon%catalog.columns*size} ${Math.floor(item.icon/catalog.columns)*size} ${size} ${size}`);
    this.iconImage_.setAttribute('href',catalog.atlas);this.iconImage_.setAttribute('width',String(catalog.columns*size));this.iconImage_.setAttribute('height',String(catalog.rows*size));}}
  this.setTooltip(item?`${pickerZh?item.zh:item.en}\n${item.id}`:this.getValue());
  super.render_();
 }
 updateSize_(margin){
  super.updateSize_(margin);if(!this.hasIcon_)return;
  // Blockly renders an empty label as a no-break space; an icon-only field drops the label entirely.
  const x=this.getConstants().FIELD_BORDER_RECT_X_PADDING,iconOnly=this.getText()==='',text=iconOnly?0:this.size_.width-2*x;
  const icon=iconSize(),gap=iconOnly?0:GAP;
  if(this.textElement_)this.textElement_.style.display=iconOnly?'none':'';
  this.size_.width=2*x+text+icon+gap;this.size_.height=Math.max(this.size_.height,icon+6);
  this.positionTextElement_(x+icon+gap,text);this.icon_.setAttribute('x',String(x));this.icon_.setAttribute('y',String((this.size_.height-icon)/2));this.positionBorderRect_();
 }
 showEditor_(){
  Blockly.DropDownDiv.clearContent();Blockly.DropDownDiv.getContentDiv().appendChild(this.picker_());
  Blockly.DropDownDiv.setColour('#c6c6c6','#000000');
  Blockly.DropDownDiv.showPositionedByField(this,()=>{openPicker=null;});
  setTimeout(()=>this.search_?.focus(),0);
 }
 picker_(){
  const tr=(a,b)=>pickerZh?a:b,el=(tag,className,text)=>{const e=document.createElement(tag);if(className)e.className=className;if(text)e.textContent=text;return e;};
  const root=el('div','item-picker'),search=el('input','item-search'),tabs=el('div','item-tabs'),grid=el('div','item-grid'),footer=el('div','item-count'),tip=el('div','item-tip');
  search.type='search';search.spellcheck=false;search.placeholder=tr('搜尋方塊或物品（中文／English／ID）','Search blocks or items (name or ID)');this.search_=search;
  let kind='all',results=[],shown=0;
  const choose=item=>{this.setValue(item.id);Blockly.DropDownDiv.hideIfOwner(this,true);};
  const more=()=>{const end=Math.min(results.length,shown+PAGE);for(;shown<end;shown++){const item=results[shown],slot=el('button','item-slot');slot.type='button';
   if(item.icon>=0){const icon=el('span','item-sprite');icon.style.cssText=sprite(item,catalog.size);slot.append(icon);}else slot.append(el('span','item-missing',item.id.slice(item.id.indexOf(':')+1,item.id.indexOf(':')+3)));
   if(item.id===this.getValue())slot.classList.add('selected');
   slot.onclick=()=>choose(item);
   slot.onpointerenter=()=>{tip.replaceChildren(el('b','',pickerZh?item.zh:item.en),el('span','',item.id));tip.hidden=false;const r=slot.getBoundingClientRect(),base=root.getBoundingClientRect();tip.style.left=`${Math.min(r.left-base.left,base.width-tip.offsetWidth)}px`;tip.style.top=`${r.bottom-base.top+4}px`;};
   slot.onpointerleave=()=>{tip.hidden=true;};grid.append(slot);}};
  const update=()=>{if(!catalog){grid.replaceChildren(el('p','item-loading',tr('正在讀取這個世界的方塊與物品…','Loading the blocks and items in this world…')));footer.textContent='';return;}
   results=searchItems(catalog.items,search.value,kind);shown=0;grid.replaceChildren();grid.scrollTop=0;tip.hidden=true;more();
   footer.textContent=results.length?tr(`共 ${results.length} 項`,`${results.length} results`):tr('找不到符合的方塊或物品','No matching blocks or items');};
  for(const [value,zhLabel,enLabel] of [['all','全部','All'],['block','方塊','Blocks'],['item','物品','Items']]){const tab=el('button','item-tab',tr(zhLabel,enLabel));tab.type='button';tab.dataset.kind=value;tab.classList.toggle('active',value===kind);
   tab.onclick=()=>{kind=value;for(const t of tabs.children)t.classList.toggle('active',t===tab);update();search.focus();};tabs.append(tab);}
  search.oninput=update;
  // Enter also commits an IME candidate, whose text arrives just after the key; only choose when the query did not change.
  search.onkeydown=e=>{if(e.key!=='Enter')return;e.preventDefault();const typed=search.value;setTimeout(()=>{if(search.value===typed&&results[0])choose(results[0]);},150);};
  grid.onscroll=()=>{tip.hidden=true;if(grid.scrollTop+grid.clientHeight>grid.scrollHeight-120)more();};
  tip.hidden=true;root.append(search,tabs,grid,footer,tip);openPicker=update;update();return root;
 }
}
// A number typed straight into an inventory input is a robot slot: a whole number from 1 to 16,
// picked from that robot's own inventory instead of typed.
export const SLOT_INPUTS=Object.freeze({ccedu_select:'SLOT',ccedu_transfer:'SLOT',ccedu_item_name:'SLOT'});
export const clampSlot=value=>{const n=Math.round(Number(value));return Number.isFinite(n)?Math.min(16,Math.max(1,n)):1;};
const inSlotInput=block=>{const parent=block?.getParent?.(),name=parent&&SLOT_INPUTS[parent.type];return block?.type==='math_number'&&!!name&&parent.getInputTargetBlock(name)===block;};
// Resolves to 16 entries of [] or [itemId, count]; null when no robot is connected.
let inventorySource=null;
export function setInventorySource(source){inventorySource=source;}
class FieldSlotNumber extends Blockly.FieldNumber{
 doClassValidation_(value){const checked=super.doClassValidation_(value);return checked===null||!inSlotInput(this.getSourceBlock())?checked:clampSlot(checked);}
 showEditor_(e,quiet){
  if(!inSlotInput(this.getSourceBlock()))return super.showEditor_(e,quiet);
  Blockly.DropDownDiv.clearContent();Blockly.DropDownDiv.getContentDiv().appendChild(this.slotPicker_());
  Blockly.DropDownDiv.setColour('#c6c6c6','#000000');Blockly.DropDownDiv.showPositionedByField(this);
 }
 slotPicker_(){
  const tr=(a,b)=>pickerZh?a:b,el=(tag,className,text)=>{const e=document.createElement(tag);if(className)e.className=className;if(text)e.textContent=text;return e;};
  const root=el('div','item-picker slot-picker'),grid=el('div','slot-grid'),note=el('div','item-count',tr('正在讀取機器人物品欄…','Reading the robot inventory…')),tip=el('div','item-tip');
  const current=Number(this.getValue());
  const draw=inventory=>{grid.replaceChildren();for(let i=0;i<16;i++){const [id,count]=inventory?.[i]||[],slot=el('button','item-slot');slot.type='button';slot.dataset.slot=String(i+1);
    const item=id&&catalog?.byId.get(id);
    if(item&&item.icon>=0){const icon=el('span','item-sprite');icon.style.cssText=sprite(item,catalog.size);slot.append(icon);}
    else if(id)slot.append(el('span','item-missing',id.slice(id.indexOf(':')+1,id.indexOf(':')+3)));
    else slot.append(el('span','slot-number',String(i+1)));
    if(id&&count>1)slot.append(el('span','slot-amount',String(count)));
    if(i+1===current)slot.classList.add('selected');
    slot.onclick=()=>{this.setValue(i+1);Blockly.DropDownDiv.hideIfOwner(this,true);};
    slot.onpointerenter=()=>{tip.replaceChildren(el('b','',tr(`第 ${i+1} 格`,`Slot ${i+1}`)),el('span','',id?`${item?(pickerZh?item.zh:item.en):id} × ${count}`:tr('空格','Empty')));tip.hidden=false;const r=slot.getBoundingClientRect(),base=root.getBoundingClientRect();tip.style.left=`${Math.min(r.left-base.left,base.width-tip.offsetWidth)}px`;tip.style.top=`${r.bottom-base.top+4}px`;};
    slot.onpointerleave=()=>{tip.hidden=true;};grid.append(slot);}};
  draw(null);tip.hidden=true;root.append(el('div','slot-title',tr('選擇機器人物品欄的格子','Pick a robot inventory slot')),grid,note,tip);
  Promise.resolve(inventorySource?.()).then(inventory=>{if(!root.isConnected)return;draw(inventory);
   note.textContent=inventory?tr('這是這台機器人現在的物品欄','This robot’s inventory right now'):tr('還沒連上機器人，只能選格子號碼','No robot connected; pick a slot number');},
   ()=>{note.textContent=tr('讀不到機器人物品欄，只能選格子號碼','Could not read the robot inventory; pick a slot number');});
  return root;
 }
}
/** Keeps whole-number slots 1-16 in inventory inputs, including programs saved before the limit existed. */
export function clampSlotNumbers(workspace){
 for(const block of workspace.getAllBlocks(false)){if(!inSlotInput(block))continue;const field=block.getField('NUM'),value=Number(field.getValue());if(clampSlot(value)!==value)field.setValue(clampSlot(value));}
}
// Every block program has exactly one "when program starts" block; only the stack under it runs.
const LOOSE='CCEDU_NOT_UNDER_START';
export const FUNCTION_DEFINITIONS=Object.freeze(['procedures_defnoreturn','procedures_defreturn']);
const withoutUndo=fn=>{const record=Blockly.Events.getRecordUndo();Blockly.Events.setRecordUndo(false);try{return fn();}finally{Blockly.Events.setRecordUndo(record);}};
/** Adds the start block to a program that has none, above its topmost stack so that stack keeps running. */
export function ensureStartBlock(workspace){
 if(workspace.getBlocksByType('ccedu_start',false).length)return null;
 return withoutUndo(()=>{
  const first=workspace.getTopBlocks(true).find(b=>b.previousConnection&&!b.outputConnection&&!b.previousConnection.isConnected());
  const start=workspace.newBlock('ccedu_start');
  if(workspace.rendered){start.initSvg();start.render();}
  if(first){const xy=first.getRelativeToSurfaceXY();start.moveBy(xy.x,Math.max(0,xy.y-56));start.nextConnection.connect(first.previousConnection);}
  else start.moveBy(40,40);
  return start;
 });
}
export const isLoose=block=>block.hasDisabledReason(LOOSE);
/** Greys out stacks that are not under the start block and says why when hovered. */
export function markLooseStacks(workspace){
 const started=workspace.getBlocksByType('ccedu_start',false).length>0;
 withoutUndo(()=>{for(const block of workspace.getAllBlocks(false)){
  // A function definition runs only when it is called, so it is never a stray stack.
  const loose=started&&!block.getParent()&&block.type!=='ccedu_start'&&!FUNCTION_DEFINITIONS.includes(block.type);
  if(block.hasDisabledReason(LOOSE)===loose)continue;
  block.setDisabledReason(loose,LOOSE);
  if(loose){block.cceduTooltip=block.tooltip;block.setTooltip(pickerZh?'沒有接在「當程式開始」下面，這組積木不會執行。':'Not attached under "when program starts"; this stack will not run.');}
  else if('cceduTooltip' in block){block.setTooltip(block.cceduTooltip);delete block.cceduTooltip;}
 }});
}
export function registerTurtleBlocks(locale='en_us'){
 if(Blockly.Blocks.ccedu_turtle_command)return;
 if(!Blockly.Extensions.isRegistered('ccedu_start_fixed'))Blockly.Extensions.register('ccedu_start_fixed',function(){this.setDeletable(false);});
 pickerZh=locale==='zh_tw';
 // Blockly's own blocks and menus use the classroom wording too; setLocale has already loaded zh-hant.
 if(pickerZh)Object.assign(Blockly.Msg,CLASSROOM_ZH);
 // A new function would otherwise open with a placeholder comment bubble ("Describe this function...") on it.
 Blockly.Msg.PROCEDURES_DEFNORETURN_COMMENT='';Blockly.Msg.PROCEDURES_DEFRETURN_COMMENT='';Blockly.fieldRegistry.register('field_item_picker',FieldItemPicker);
 // math_number's own field, which turns into the slot picker only inside an inventory input.
 Blockly.fieldRegistry.unregister('field_number');Blockly.fieldRegistry.register('field_number',FieldSlotNumber);
 const zh=locale==='zh_tw',tr=(a,b)=>zh?a:b,side={type:'field_dropdown',name:'SIDE',options:[[tr('前方','front'),'forward'],[tr('上方','above'),'up'],[tr('下方','below'),'down']]};
 const statement=(type,message0,args0,colour=100)=>({type,message0,args0,previousStatement:null,nextStatement:null,colour});
 const output=(type,message0,args0,check,colour=160)=>({type,message0,args0,output:check,colour});
 const input=(name,check)=>({type:'input_value',name,check});
 const options=commands=>commands.map(c=>[zh?(labels[c]||c):c,c]);
 Blockly.defineBlocksWithJsonArray([
 {type:'ccedu_start',message0:tr('當程式開始','when program starts'),nextStatement:null,colour:'#c99316',hat:'cap',extensions:['ccedu_start_fixed'],tooltip:tr('按「執行」時，從這裡往下依序執行。','Run starts here and goes down the stack.')},
 ...Object.entries(ACTION_BLOCKS).map(([type,[zhText,enText,choices]])=>statement(type,tr(zhText,enText),[{type:'field_dropdown',name:'COMMAND',options:choices.map(([zhLabel,enLabel,command])=>[tr(zhLabel,enLabel),command])}])),
 statement('ccedu_turtle_command','%1',[{type:'field_dropdown',name:'COMMAND',options:options(TURTLE_COMMANDS)}]),
 output('ccedu_turtle_result',tr('%1 成功了嗎','try %1 succeeded'),[{type:'field_dropdown',name:'COMMAND',options:options(TURTLE_COMMANDS.filter(c=>!['turnLeft','turnRight','detect','detectUp','detectDown','compare','compareUp','compareDown'].includes(c)))}],'Boolean'),
 statement('ccedu_break',tr('跳出迴圈','break out of loop'),[],120),
 output('ccedu_registry_name',tr('物品／方塊 %1','item / block ID %1'),[{type:'field_item_picker',name:'NAME',value:'minecraft:stone'}],'String',35),
 statement('ccedu_turtle_action',tr('%1 數量／文字 %2','%1 amount / text %2'),[{type:'field_dropdown',name:'COMMAND',options:options(['drop','dropUp','dropDown','suck','suckUp','suckDown','place','placeUp','placeDown'])},input('ARG')]),
 output('ccedu_turtle_query','%1 %2',[{type:'field_dropdown',name:'COMMAND',options:options(['detect','detectUp','detectDown','compare','compareUp','compareDown','getItemCount','getItemSpace','getSelectedSlot'])},input('SLOT','Number')],null),
 statement('ccedu_select',tr('選取物品欄第 %1 格','select inventory slot %1'),[input('SLOT','Number')],35),
 statement('ccedu_transfer',tr('把選取的物品搬到第 %1 格，搬 %2 個','transfer to slot %1 count %2'),[input('SLOT','Number'),input('COUNT','Number')],35),
 output('ccedu_item_name',tr('第 %1 格是什麼物品','item ID in slot %1'),[input('SLOT','Number')],'String',35),
 output('ccedu_inspect',tr('%1 是什麼方塊','block ID %1'),[side],'String'),
 output('ccedu_redstone_query',tr('%1 有紅石訊號嗎','redstone input %1'),[side],'Boolean',5),
 statement('ccedu_redstone_set',tr('讓 %1 發出紅石訊號 %2','set redstone %1 %2'),[side,input('VALUE','Boolean')],5),
 statement('ccedu_say',tr('說 %1','say %1'),[input('TEXT')],65),
 statement('ccedu_sleep',tr('等待 %1 秒','wait %1 seconds'),[input('SECONDS','Number')],65),
 statement('ccedu_assert',tr('檢查 %1 不對就停下並顯示 %2','assert %1 error %2'),[input('TEST','Boolean'),input('MESSAGE','String')],65),
 statement('ccedu_call',tr('執行程式 %1','call program %1'),[{type:'field_input',name:'NAME',text:tr('程式名稱','program name')}],285),
 statement('ccedu_comment',tr('註解 %1','comment %1'),[{type:'field_input',name:'TEXT',text:''}],210),
 statement('ccedu_snapshot','%1',[{type:'field_dropdown',name:'ACTION',options:[[tr('儲存復原點','save turtle state'),'save'],[tr('回到復原點','restore turtle state'),'restore']]}],285),
 output('ccedu_random_boolean',tr('隨機選是或否','random true or false'),[],'Boolean',230),
 ]);
 const value=(b,n,fallback='')=>luaGenerator.valueToCode(b,n,Order.NONE)||fallback;
 const method=b=>'turtle.'+b.getFieldValue('COMMAND');
 luaGenerator.forBlock.ccedu_start=()=>'';
 luaGenerator.forBlock.ccedu_turtle_command=b=>method(b)+'()\n';
 for(const type of Object.keys(ACTION_BLOCKS))luaGenerator.forBlock[type]=luaGenerator.forBlock.ccedu_turtle_command;
 luaGenerator.forBlock.ccedu_turtle_result=b=>[method(b)+'()',Order.HIGH];
 luaGenerator.forBlock.ccedu_break=()=> 'do break end\n';
 luaGenerator.forBlock.ccedu_registry_name=b=>[luaGenerator.quote_(b.getFieldValue('NAME')),Order.ATOMIC];
 luaGenerator.forBlock.ccedu_turtle_action=b=>method(b)+'('+value(b,'ARG')+')\n';
 luaGenerator.forBlock.ccedu_turtle_query=b=>[method(b)+'('+value(b,'SLOT')+')',Order.HIGH];
 luaGenerator.forBlock.ccedu_select=b=>'turtle.select('+value(b,'SLOT','1')+')\n';
 luaGenerator.forBlock.ccedu_transfer=b=>'turtle.transferTo('+value(b,'SLOT','1')+', '+value(b,'COUNT','64')+')\n';
 luaGenerator.forBlock.ccedu_item_name=b=>['turtleedu.getItemName('+value(b,'SLOT')+')',Order.HIGH];
 const suffix=b=>({forward:'',up:'Up',down:'Down'}[b.getFieldValue('SIDE')]);
 luaGenerator.forBlock.ccedu_inspect=b=>['turtleedu.inspect'+suffix(b)+'()',Order.HIGH];
 luaGenerator.forBlock.ccedu_redstone_query=b=>['turtleedu.checkRedstone'+suffix(b)+'()',Order.HIGH];
 luaGenerator.forBlock.ccedu_redstone_set=b=>'turtleedu.setRedstone'+suffix(b)+'('+value(b,'VALUE','false')+')\n';
 luaGenerator.forBlock.ccedu_say=b=>'turtleedu.say('+value(b,'TEXT','""')+')\n';
 luaGenerator.forBlock.ccedu_sleep=b=>'sleep('+value(b,'SECONDS','1')+')\n';
 luaGenerator.forBlock.ccedu_assert=b=>'assert('+value(b,'TEST','false')+', '+value(b,'MESSAGE','"Assertion failed"')+')\n';
 luaGenerator.forBlock.ccedu_call=b=>'edu.callProgram('+luaGenerator.quote_(b.getFieldValue('NAME'))+')\n';
 luaGenerator.forBlock.ccedu_comment=b=>'-- '+b.getFieldValue('TEXT').replace(/[\r\n]/g,' ')+'\n';
 luaGenerator.forBlock.ccedu_snapshot=b=>'assert(edu.'+(b.getFieldValue('ACTION')==='save'?'saveTurtleState':'restoreTurtleState')+'())\n';
 luaGenerator.forBlock.ccedu_random_boolean=()=>['(math.random(1,2) == 1)',Order.RELATIONAL];
}
// Inputs that take a number, text or true/false come pre-filled with an editable value (a shadow),
// so a dragged-out block can be typed into directly; dropping another block onto the input replaces it.
const num=n=>({shadow:{type:'math_number',fields:{NUM:n}}}),str=t=>({shadow:{type:'text',fields:{TEXT:t}}}),bool=v=>({shadow:{type:'logic_boolean',fields:{BOOL:v}}});
export function toolbox(locale){
 const zh=locale==='zh_tw',tr=(a,b)=>zh?a:b;
 const category=(zhName,enName,colour,types)=>({kind:'category',name:zh?zhName:enName,colour,contents:types.map(type=>typeof type==='string'?{kind:'block',type}:{kind:'block',...type})});
 return {kind:'categoryToolbox',contents:[
 // The old all-in-one ccedu_turtle_command and ccedu_turtle_action (with an amount) still load but are no longer offered.
 category('機器人動作','Turtle',100,[...Object.keys(ACTION_BLOCKS),'ccedu_turtle_result','ccedu_turtle_query','ccedu_inspect']),
 category('物品欄','Inventory',35,[{type:'ccedu_select',inputs:{SLOT:num(1)}},{type:'ccedu_transfer',inputs:{SLOT:num(2),COUNT:num(64)}},{type:'ccedu_item_name',inputs:{SLOT:num(1)}},'ccedu_registry_name']),
 category('紅石','Redstone',5,['ccedu_redstone_query',{type:'ccedu_redstone_set',inputs:{VALUE:bool('TRUE')}}]),
 category('判斷','Logic',210,['controls_if',{type:'logic_compare',inputs:{A:num(0),B:num(0)}},'logic_operation','logic_negate','logic_boolean']),
 category('迴圈','Loops',120,[{type:'controls_repeat_ext',inputs:{TIMES:num(10)}},'controls_whileUntil',{type:'controls_for',inputs:{FROM:num(1),TO:num(10),BY:num(1)}},'ccedu_break']),
 category('數字','Math',230,['math_number',{type:'math_arithmetic',inputs:{A:num(1),B:num(1)}},{type:'math_modulo',inputs:{DIVIDEND:num(64),DIVISOR:num(10)}},{type:'math_random_int',inputs:{FROM:num(1),TO:num(100)}},'math_random_float','ccedu_random_boolean']),
 category('文字','Text',160,['text',{type:'text_join',inputs:{ADD0:str(''),ADD1:str('')}},{type:'text_length',inputs:{VALUE:str(tr('你好','hello'))}}]),
 {kind:'category',name:zh?'變數':'Variables',colour:330,custom:'VARIABLE'},
 {kind:'category',name:zh?'函式':'Functions',colour:290,custom:'CCEDU_FUNCTIONS'},
 category('程式與除錯','Programs',285,['ccedu_call',{type:'ccedu_sleep',inputs:{SECONDS:num(1)}},{type:'ccedu_say',inputs:{TEXT:str(tr('你好','Hello'))}},{type:'ccedu_assert',inputs:{TEST:bool('TRUE'),MESSAGE:str(tr('發生錯誤','Something went wrong'))}},'ccedu_comment','ccedu_snapshot']),
 ]};
}
/** Blockly's variable flyout with the "set" block pre-filled with 0, like the other value inputs. */
export function variableFlyout(workspace){
 const blocks=Blockly.Variables.flyoutCategory(workspace);
 for(const b of blocks)if(b.getAttribute?.('type')==='variables_set'&&!b.querySelector('value'))b.appendChild(Blockly.utils.xml.textToDom('<value name="VALUE"><shadow type="math_number"><field name="NUM">0</field></shadow></value>'));
 return blocks;
}
/**
 * The Functions category: first a ready-made call for every other program in the student's library (the program being
 * edited is left out so it cannot call itself), then Blockly's own blocks for making a function in this program.
 * `programs` is the latest library list; the flyout is rebuilt each time the category opens.
 */
export function functionFlyout(workspace,{programs=[],current='',locale='en_us'}={}){
 const zh=locale==='zh_tw',tr=(a,b)=>zh?a:b,xml=Blockly.utils.xml;
 const label=text=>{const el=xml.createElement('label');el.setAttribute('text',text);return el;};
 const others=programs.filter(p=>p&&p.id!==current&&typeof p.name==='string'&&p.name.trim());
 const calls=others.map(p=>{const block=xml.createElement('block'),field=xml.createElement('field');block.setAttribute('type','ccedu_call');field.setAttribute('name','NAME');field.textContent=p.name;block.append(field);return block;});
 return [label(tr('程式庫裡的其他程式','Other programs in my library')),...(calls.length?calls:[label(tr('（還沒有其他存好的程式）','(No other saved programs yet)'))]),
  label(tr('自己做的函式','Functions in this program')),...Blockly.Procedures.flyoutCategory(workspace)];
}
// Loading is not an edit: Blockly delivers its create events after the caller's loading flag is cleared,
// which used to mark every opened program dirty and save it again. Undo also must not remove the loaded program.
export function loadWorkspace(workspace,saved){if(!saved||typeof saved!=='object'||Array.isArray(saved))throw new Error('Workspace must be an object');Blockly.Events.disable();try{Blockly.serialization.workspaces.load(migrateActionBlocks(saved),workspace);}finally{Blockly.Events.enable();}workspace.clearUndo();}
export function preview(workspace){return luaGenerator.workspaceToCode(workspace);}
