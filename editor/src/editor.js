import * as Blockly from 'blockly';
import * as TraditionalChinese from 'blockly/msg/zh-hant';
import * as English from 'blockly/msg/en';
import {newProgramId} from './identity.js';
import {installDialogs,variableDefault} from './dialogs.js';
import {registerTurtleBlocks,toolbox,loadWorkspace,preview,FUNCTION_DEFINITIONS,setItemCatalog,setItemIdText,ensureStartBlock,markLooseStacks,isLoose,variableFlyout,functionFlyout,setProgramLibrary,refreshProgramCalls,setInventorySource,clampSlotNumbers,dropInsertionMarkers} from './blocks.js';
import {blocklyThemes,initialTheme,installThemeToggle} from './theme.js';
import {openOnlyToolbox,registerClipboardMenu,installMenuKey} from './toolbox.js';
import {describeRobot,needsRecallConfirm} from './robots.js';
import {installTakeover} from './takeover.js';
import {historyText} from './history.js';
import {setLanguage,tr,isChinese,LOCALE,htmlLang} from './i18n.js';
import {localizePage} from './page.js';
// The game passes its language; boot.js has already loaded i18n/<lang>.js (the editor text and Blockly's messages).
const asked=new URLSearchParams(location.search).get('lang')||'zh_tw',locale=LOCALE.test(asked)?asked:'en_us',bundle=window.CCEDU_I18N?.locale===locale?window.CCEDU_I18N:{};
setLanguage(locale,bundle.ui);const zh=isChinese(),$=id=>document.getElementById(id);
Blockly.setLocale(zh?TraditionalChinese:{...English,...(bundle.blockly||{})});registerTurtleBlocks(locale);localizePage(document,tr,htmlLang(locale));
const themes=blocklyThemes(Blockly),startTheme=initialTheme(location.search);
const workspace=Blockly.inject('workspace',{toolbox:toolbox(locale),plugins:{toolbox:openOnlyToolbox(Blockly)},theme:themes[startTheme],renderer:'zelos',media:'media/',trashcan:true,grid:{spacing:24,length:2,colour:'#41504a',snap:false},move:{scrollbars:true,drag:true,wheel:false},zoom:{controls:true,wheel:true,startScale:0.9,maxScale:2,minScale:0.4,scaleSpeed:1.15}});
workspace.registerToolboxCategoryCallback('VARIABLE',variableFlyout);registerClipboardMenu(Blockly,tr,text=>setStatus(text));window.cceduContextMenu=installMenuKey(Blockly,workspace);
// Every other saved program can be called from here, beside the functions made inside this program.
let libraryPrograms=[];workspace.registerToolboxCategoryCallback('CCEDU_FUNCTIONS',ws=>functionFlyout(ws,{programs:libraryPrograms,current:program?.id,locale}));
// Variable create/rename/delete and Blockly's other questions open inside the editor instead of an operating-system window.
installDialogs(Blockly,{$,tr,composing:()=>!!preedit||!!$('imeComposition')&&!$('imeComposition').hidden,newName:variableDefault(Blockly,workspace,tr('變數','var'))});
for(const id of ['new','delete','copy','recover','programs','programName','convert','run','pause','resume','step','stop','history','robots'])$(id).disabled=true;
for(const id of ['targets','share'])$(id).disabled=true;
let ready=false,connected=false,smokeTesting=false,loading=false,dirty=false,program=null,timer,polling=false,editVersion=0,savedSelection='',debugProgram='',recoveredDraft=null,visualEditor=true,luaAllowed=false,sharingAllowed=false,suggestions=true,selectedBlock='',remote={};
let pendingSave=Promise.resolve(),pendingRequest=Promise.resolve(),saving=false,creating=false;
const setStatus=(text,error=false)=>{$('status').textContent=text;$('status').dataset.error=String(error);};
function request(op,data={}){
 const send=()=>new Promise((resolve,reject)=>{
  if(typeof window.cceduQuery!=='function')return reject(new Error(tr('目前沒有遊戲連線','No game connection')));
  window.cceduQuery({request:JSON.stringify({op,...data}),persistent:false,onSuccess:raw=>{try{const result=JSON.parse(raw);if(result.ok===false){if(result.blockId)markError(result.blockId,'compile');reject(new Error(result.error));}else {if(result.takeover)takeover.update(result.takeover);if(result.historyDiagnostic)setStatus(result.historyDiagnostic,true);resolve(result);}}catch(e){reject(e);}},onFailure:(_,message)=>reject(new Error(message))});
 });
 const result=pendingRequest.catch(()=>{}).then(send);pendingRequest=result;return result;
}
const workspaceJSON=()=>dropInsertionMarkers(Blockly.serialization.workspaces.save(workspace));
let lastErrorLine=0,lastErrorProgram='',runtimeError='',highlightedSource;
function highlightLua(line=0){const source=$('luaEditor').value;if(source===highlightedSource&&line===lastErrorLine)return;highlightedSource=source;$('luaLineHighlight').replaceChildren(...source.split('\n').map((text,index)=>{const row=document.createElement('span');row.textContent=text||'\u200b';row.className=index+1===line?'error-line':'';return row;}));$('luaLineHighlight').scrollTop=$('luaEditor').scrollTop;$('luaLineHighlight').scrollLeft=$('luaEditor').scrollLeft;if(line&&line!==lastErrorLine){$('luaEditor').scrollTop=Math.max(0,(line-4)*parseFloat(getComputedStyle($('luaEditor')).lineHeight));$('luaLineHighlight').scrollTop=$('luaEditor').scrollTop;}lastErrorLine=line;}
$('luaEditor').onscroll=()=>{$('luaLineHighlight').scrollTop=$('luaEditor').scrollTop;$('luaLineHighlight').scrollLeft=$('luaEditor').scrollLeft;};
const refreshPreview=()=>{try{if(luaAllowed)$('preview').textContent=program?.mode==='lua'?$('luaEditor').value:preview(workspace);highlightLua(program?.mode==='lua'&&program.id===lastErrorProgram?lastErrorLine:0);}catch(e){setStatus(e.message,true);}};
function display(p){loading=true;clearTimeout(timer);program=p;setProgramLibrary(libraryPrograms,p?.id);$('programName').value=p?.name||'';const lua=p?.mode==='lua';$('modeTag').dataset.mode=lua?'lua':'blocks';$('modeTag').textContent=lua?'Lua':tr('積木','Blocks');$('workbench').style.display=lua?'none':'block';$('codePane').style.display=lua?'block':'none';editingPolicy();$('convert').disabled=!connected||lua;if(lua)$('luaEditor').value=p.source||'';else loadWorkspace(workspace,p?.workspace||{blocks:{languageVersion:0,blocks:[]}});dirty=false;loading=false;
 // A program saved before the start block existed gets one above its topmost stack; that change is saved like an edit.
 if(p&&!lua&&!takeover.writeBlocked){ensureStartBlock(workspace);markLooseStacks(workspace);clampSlotNumbers(workspace);}syncSave();refreshPreview();Blockly.svgResize(workspace);}
// Beginner worlds (teacher setting lua=false) show no Lua anywhere. Only a teacher with sharing on sees the send bar:
// students never copy programs to each other, and there are no floppy disks.
// These controls start hidden in index.html so a preloaded page never flashes them before the world policy arrives.
function editingPolicy(){ $('workspace').inert=!visualEditor;$('visualDisabled').hidden=visualEditor;$('newBlocks').disabled=!visualEditor;
 for(const id of ['previewTitle','preview','newLua','convert','modeTag'])$(id).hidden=!luaAllowed;$('new').setAttribute('aria-haspopup',luaAllowed?'menu':'false');
 $('transferbar').hidden=!sharingAllowed;
 if(!luaAllowed&&program?.mode==='lua'){display(null);setStatus(tr('老師關閉了 Lua 程式；請用積木寫程式。','The teacher turned off Lua programs; use block programs.'),true);}}
const rerenderItemFields=()=>{for(const block of workspace.getBlocksByType('ccedu_registry_name',false))block.getField('NAME').forceRerender();};
const applyPolicy=data=>{visualEditor=data.visualEditor;luaAllowed=data.lua;sharingAllowed=data.sharing;const reveal=$('preview').hidden&&luaAllowed;editingPolicy();if(reveal)refreshPreview();if(setItemIdText(luaAllowed))rerenderItemFields();if(typeof takeover!=='undefined')takeover.refresh();};
// A select's empty first entry only names it; it is shown while nothing is chosen but cannot be picked.
function placeholder(text){const option=new Option(text,'',true,true);option.hidden=option.disabled=true;return option;}
async function library(){if(!connected)return;const data=await request('programList');applyPolicy(data);savedSelection=data.selected||'';libraryPrograms=data.programs;setProgramLibrary(libraryPrograms,program?.id);if(program?.mode==='blocks')refreshProgramCalls(workspace);$('programs').replaceChildren(placeholder(tr('程式庫','Library')),...data.programs.map(p=>new Option(p.name,p.id)));if(program)$('programs').value=program.id;return data.programs;}

function content(){return {name:$('programName').value,mode:program.mode,...(program.mode==='lua'?{source:$('luaEditor').value}:{workspace:workspaceJSON()})};}
// A synced draft equals the server copy; only an unsynced one (failed save or close) is offered for recovery.
async function backup(synced=false){if(connected&&program){recoveredDraft={version:1,workspace:program.mode==='blocks'?workspaceJSON():{},program:{...program,...content()},locale,synced};await request('saveRecovery',{draft:recoveredDraft});if(synced)$('recover').hidden=true;}}
function syncSave(){$('save').disabled=!dirty;$('save').dataset.icon=dirty?'save':'check';$('save').textContent=dirty?tr('儲存','Save'):tr('已儲存','Saved');}
function save(){
 if(!ready)return Promise.reject(new Error(tr('編輯器尚未連接','Editor is not connected')));
 pendingSave=pendingSave.catch(()=>{}).then(async()=>{
  if(connected){if(!program||!dirty)return;if(takeover.writeBlocked&&!takeover.draining)return;const payload=content();await backup();const id=program.id,version=editVersion;
   saving=true;let data;try{data=await request('programSave',{id,revision:program.revision,program:payload});}finally{saving=false;}if(program?.id===id){program=data.program;dirty=editVersion!==version;}await library();await backup(!dirty);syncSave();if(data.compileError){markError(data.blockId||null,'compile');setStatus(tr('已儲存；積木有錯誤：','Saved; block error: ')+data.compileError,true);}else{if(errorSource==='compile')markError(null);setStatus('');}
  }else{const version=editVersion;await request('saveDraft',{draft:{version:1,workspace:workspaceJSON(),locale}});dirty=editVersion!==version;syncSave();setStatus(tr('本機測試草稿已儲存','Local test draft saved'));}
 });return pendingSave.catch(e=>{setStatus(e.message,true);throw e;});
}
let previewFrame=0;
function changed(){if(loading||typeof takeover!=='undefined'&&takeover.writeBlocked&&!takeover.draining)return;editVersion++;dirty=true;syncSave();if(!previewFrame)previewFrame=requestAnimationFrame(()=>{previewFrame=0;refreshPreview();});clearTimeout(timer);timer=setTimeout(autoSave,300);}
// Mid-drag the workspace holds an insertion marker in place of the dragged block, so wait until the drop.
function autoSave(){if(workspace.isDragging()){timer=setTimeout(autoSave,300);return;}if(ready&&!smokeTesting)save().catch(()=>{});}
function suggest(){
 const block=workspace.getBlockById(selectedBlock);if(!suggestions||!block){$('suggestion').textContent='';return;}
 // Only specific advice: why a stack will not run, or which inputs are still empty.
 if(isLoose(block.getRootBlock())){$('suggestion').textContent=tr('這組積木沒有接在「當程式開始」下面，按執行時不會跑。','This stack is not under "when program starts", so Run skips it.');return;}
 const missing=block.inputList.filter(i=>i.connection&&!i.connection.targetBlock());
 const checks=[...new Set(missing.flatMap(i=>i.connection.getCheck()||[tr('值或動作','value or action')]))];
 $('suggestion').textContent=missing.length?tr('可接上積木：','Connect blocks: ')+checks.map(c=>({Number:tr('數字','Number'),Boolean:tr('是／否','Boolean'),String:tr('文字','String')}[c]||c)).join(', '):'';
}
workspace.addChangeListener(event=>{if(event.type===Blockly.Events.SELECTED){selectedBlock=event.newElementId||'';suggest();}if(!event.isUiEvent){if(!loading&&program?.mode!=='lua'){markLooseStacks(workspace);clampSlotNumbers(workspace);}changed();suggest();}});$('luaEditor').oninput=changed;$('programName').oninput=changed;
const safely=fn=>async(...args)=>{try{await fn(...args);}catch(e){setStatus(e.message,true);}};
$('save').onclick=safely(save);
$('history').onclick=safely(async()=>{await save();const data=await request('programHistory');$('historyEntries').textContent=historyText(data.records,libraryPrograms,tr)||tr('尚無紀錄','No activity yet');$('historyDialog').showModal();});
$('historyClose').onclick=()=>$('historyDialog').close();
// "My robots" lists every robot the student placed, also far away ones. A robot can be pointed out in the world (only this
// student sees it), or recalled: an empty one comes back as an item, one carrying items moves beside the student.
let robotsTimer=0,robotsBusy=false,robotsShown='',robotsOrder='',robotsSettleAt=0;
const robotsStatus=(text,error=false)=>{$('robotsStatus').textContent=text;$('robotsStatus').dataset.error=String(error);};
const ask=text=>new Promise(resolve=>Blockly.dialog.confirm(text,resolve));
function robotButton(label,action){const button=document.createElement('button');button.type='button';button.textContent=label;button.onclick=async()=>{if(robotsBusy||performance.now()<robotsSettleAt)return;robotsBusy=true;try{await action();}catch(e){robotsStatus(e.message,true);}finally{robotsBusy=false;}};return button;}
function renderRobots(data){
 // Redrawn only when something changed. When rows move (a robot was recalled), clicks wait a moment, so a quick second click
 // cannot land on the robot that moved into the same place.
 const shown=JSON.stringify(data);if(shown===robotsShown)return;robotsShown=shown;
 const order=data.robots.map(r=>r.id).join();if(robotsOrder&&order!==robotsOrder)robotsSettleAt=performance.now()+700;robotsOrder=order;
 const rows=data.robots.map(robot=>{const view=describeRobot(robot,data.player,zh),row=document.createElement('div');row.className='robot-row';row.setAttribute('role','listitem');row.dataset.current=String(!!robot.current);
  const main=document.createElement('div'),title=document.createElement('div'),state=document.createElement('span'),detail=document.createElement('div');
  title.className='robot-title';title.textContent=view.title+(robot.current?tr('（這一台）',' (this one)'):'');state.className='robot-state';state.dataset.status=robot.status;state.textContent=view.status;title.append(state);
  detail.className='robot-detail';detail.textContent=view.detail;main.append(title,detail);
  row.append(main,robotButton(tr('定位','Find'),()=>locateRobot(robot)),robotButton(tr('召回','Recall'),()=>recallRobot(robot,view.title)));return row;});
 if(!rows.length){const empty=document.createElement('p');empty.textContent=tr('還沒有放下任何機器人','No robots placed yet');rows.push(empty);}
 $('robotsEntries').replaceChildren(...rows);
}
async function showRobots(){renderRobots(await request('programRobots'));}
async function locateRobot(robot){
 const result=await request('programLocate',{robot:robot.id});
 if(!result.sameDimension){robotsStatus(tr('它在另一個世界，到那裡才看得到它的位置','It is in another dimension; go there to see where it is'),true);return;}
 // The outline and light column show in the world, so the editor and controller close to reveal it.
 $('robotsDialog').close();await leave('exitToWorld');
}
async function recallRobot(robot,title){
 if(needsRecallConfirm(robot)&&!await ask(tr('這台正在執行程式，召回會讓它停下來。要召回嗎？','This robot is running a program; recalling it stops the program. Recall it?')))return;
 await save();const result=await request('programRecall',{robot:robot.id});
 if(result.mode==='item'){
  robotsStatus(result.pocket?tr('%1已經收進你的背包','%1 is back in your inventory',title):tr('你的背包滿了，%1放在你腳邊','Your inventory is full; %1 is at your feet',title));
  // The robot this editor was opened from is now an item, so there is nothing left to edit on.
  if(result.current){$('robotsDialog').close();await request('exitToWorld').catch(()=>{});return;}
 }else robotsStatus(tr('%1已經移到你身邊，背包和程式都跟著它','%1 moved next to you with its backpack and programs',title));
 await showRobots();
}
$('robots').onclick=safely(async()=>{await save();robotsStatus('');robotsShown='';robotsOrder='';await showRobots();$('robotsDialog').showModal();clearInterval(robotsTimer);
 // Positions and states keep changing while the list is open.
 robotsTimer=setInterval(()=>{if($('robotsDialog').open&&!robotsBusy)showRobots().catch(()=>{});},2000);});
$('robotsDialog').addEventListener('close',()=>clearInterval(robotsTimer));
$('robotsClose').onclick=()=>$('robotsDialog').close();
$('debugSource').onclick=safely(async()=>{if(!debugProgram)return;await save();const from=program?.id,data=await request('programInspect',{id:debugProgram});display(data.program);inspecting={id:program.id,from};await library();});
// One creation at a time: a double click on New must not create two programs.
async function fresh(mode='blocks',source=''){if(creating)return;creating=true;try{await save();const used=(await library()).map(p=>p.name),base=tr('新程式','New program');let name=base,n=1;while(used.includes(name))name=base+' '+n++;display({id:newProgramId(),revision:0,name,mode,source,workspace:{blocks:{languageVersion:0,blocks:[]}}});dirty=true;await save();}finally{creating=false;}}
// A button menu instead of a native <select>: CEF select popups fire change on arrow keys and can leave a stale popup frame.
const newMenu=open=>{$('newMenu').hidden=!open;$('new').setAttribute('aria-expanded',String(open));};
// Opening is idempotent so a repeated click cannot immediately close the menu; it closes on choice, outside click or Escape.
$('new').onclick=()=>luaAllowed?newMenu(true):safely(()=>fresh('blocks'))();document.addEventListener('pointerdown',e=>{if(!e.target.closest('.menu-wrap'))newMenu(false);});
for(const [id,mode] of [['newBlocks','blocks'],['newLua','lua']])$(id).onclick=safely(async()=>{newMenu(false);await fresh(mode);});
$('copy').onclick=safely(async()=>{if(!program)return;await backup();const snapshot=content(),used=(await library()).map(p=>p.name),base=Array.from(snapshot.name).slice(0,48).join('')+tr(' 副本',' copy');let name=base,n=2;while(used.includes(name))name=base+' '+n++;display({...snapshot,name,id:newProgramId(),revision:0});dirty=true;await save();});
$('recover').onclick=safely(()=>{if(!recoveredDraft?.program)return;display(recoveredDraft.program);dirty=true;syncSave();$('recover').hidden=true;setStatus(tr('已打開這台電腦裡的草稿；如果和存好的版本不一樣，請按「另存副本」。','Local draft loaded; use Save copy if its revision conflicts.'));});
// Every <select> opens a list the page draws itself. Native select popups are separate CEF views that MCEF pastes into the
// game texture by hand, which tears them under Blockly's flyouts and can leave stale rows behind; the <select> keeps the value.
const pickList=document.createElement('div');pickList.className='menu pick-list';pickList.setAttribute('role','listbox');pickList.hidden=true;
let pickFor=null,pickByKey=false;
// Focus returns to the select only after keyboard use; a mouse choice would otherwise leave its focus ring drawn.
function closePick(){if(!pickFor)return;const select=pickFor;pickList.hidden=true;select.setAttribute('aria-expanded','false');pickFor=null;if(pickByKey)select.focus();}
function openPick(select){
 closePick();if(select.disabled||!select.options.length)return;
 pickList.replaceChildren(...Array.from(select.options).filter(option=>!option.hidden).map(option=>{const item=document.createElement('button');item.type='button';item.setAttribute('role','option');item.textContent=option.text;item.disabled=option.disabled;
  item.setAttribute('aria-selected',String(option.selected));item.classList.toggle('chosen',option.selected);
  item.onclick=()=>{const changed=select.value!==option.value;select.value=option.value;closePick();if(changed)select.dispatchEvent(new Event('change'));};return item;}));
 // A modal dialog sits in the browser's top layer, so a list for a select inside one must live in that dialog.
 (select.closest('dialog')||document.body).append(pickList);
 const box=select.getBoundingClientRect(),below=innerHeight-box.bottom-8,above=box.top-8,down=below>=Math.min(240,above);
 pickList.style.left=Math.max(4,Math.min(box.left,innerWidth-box.width-4))+'px';pickList.style.minWidth=box.width+'px';
 pickList.style.maxHeight=Math.max(80,down?below:above)+'px';pickList.style.top=down?box.bottom+4+'px':'';pickList.style.bottom=down?'':innerHeight-box.top+4+'px';
 pickList.hidden=false;pickFor=select;select.setAttribute('aria-expanded','true');pickList.querySelector('.chosen')?.scrollIntoView({block:'nearest'});
}
for(const select of document.querySelectorAll('select')){select.setAttribute('aria-haspopup','listbox');select.setAttribute('aria-expanded','false');
 // The library refreshes while the list is open; rebuild it so it never offers programs that are gone.
 new MutationObserver(()=>{if(pickFor===select)openPick(select);}).observe(select,{childList:true});}
document.addEventListener('mousedown',e=>{
 const select=e.target.closest?.('select');
 if(select&&e.button===0){e.preventDefault();pickByKey=false;pickFor===select?closePick():openPick(select);}
},true);
// Blockly cancels pointerdown on its workspace, which suppresses mousedown there, so outside clicks are caught here.
document.addEventListener('pointerdown',e=>{if(pickFor&&!pickList.contains(e.target)&&!e.target.closest?.('select')){pickByKey=false;closePick();}},true);
document.addEventListener('keydown',e=>{
 const select=e.target.closest?.('select');
 // Arrow keys on a closed select open a native popup on macOS and change the value elsewhere; open the page list instead.
 if(select&&['ArrowDown','ArrowUp',' ','Enter','F4'].includes(e.key)){e.preventDefault();pickByKey=true;if(pickFor!==select)openPick(select);(pickList.querySelector('.chosen:not(:disabled)')||pickList.querySelector('button:not(:disabled)'))?.focus();return;}
 if(!pickFor||!pickList.contains(e.target)||!['ArrowDown','ArrowUp'].includes(e.key))return;
 e.preventDefault();const items=[...pickList.querySelectorAll('button:not(:disabled)')],at=items.indexOf(e.target);
 items[Math.max(0,Math.min(items.length-1,at+(e.key==='ArrowDown'?1:-1)))]?.focus();
},true);
addEventListener('resize',closePick);addEventListener('blur',()=>{pickByKey=false;closePick();});
$('programs').onchange=safely(async()=>{const id=$('programs').value;if(!id)return;await save();const result=await request('programLoad',{id});display(result.program);$('programs').value=id;await library();});
$('delete').onclick=safely(async()=>{if(!program)return;clearTimeout(timer);await request('programDelete',{id:program.id,revision:program.revision});display(null);
 // Open another program right away, as on start-up, so new blocks never land in an editor with no program to save into.
 const programs=await library();if(programs?.length){const p=await request('programLoad',{id:programs[0].id});display(p.program);$('programs').value=program.id;}else if(takeover.writeBlocked)display(null);else await fresh(visualEditor?'blocks':'lua');
 setStatus(tr('已刪除；世界存檔裡還留有備份','Deleted; a recovery copy is kept in world data'));});
$('convert').onclick=safely(async()=>{await save();const compiled=await request('programCompile',{id:program.id});await fresh('lua',compiled.lua);});
const fill=(id,...options)=>{const select=$(id),value=select.value;select.replaceChildren(...options);if(options.some(o=>o.value===value))select.value=value;};
async function transfers(){
 if(sharingAllowed){const target=await request('programTargets');fill('targets',placeholder(tr('接收學生','Recipient student')),...(target.teacher?[new Option(tr('所有附近學生','All nearby students'),'*')]:[]),...target.targets.map(t=>new Option(t.name,t.id)));}
}
// Lists refresh in the background instead of a manual button; skip while a list is open or focused.
let listing=false;setInterval(async()=>{if(!ready||!connected||listing||document.activeElement?.tagName==='SELECT')return;listing=true;try{await library();await transfers();}catch{}finally{listing=false;}},5000);
$('share').onclick=safely(async()=>{if(!program||!$('targets').value)return;await save();const sent=await request('programShare',{id:program.id,target:$('targets').value});setStatus(tr('已分享給 %1 位學生','Sent to %1 students',sent.recipients));});
// Continuous runs return to the remote page so students can watch the robot; the robot
// camera stays the student's choice. Stepping stays here to follow the highlighted block and variables.
const watchOps=new Set(['programRun','programResume']);
for(const [button,op] of Object.entries({run:'programRun',pause:'programPause',resume:'programResume',step:'programStep',stop:'programStop'})){
 $(button).onclick=safely(async()=>{if(!program)return;clearTimeout(timer);await save();await request(op,{id:program.id});if(watchOps.has(op))return leave('navigate',{page:3});await state();});
}
function runtimeControls(status){$('runtimeLamp').dataset.status=status;const paused=status==='paused';$('pause').hidden=paused;$('resume').hidden=!paused;if(connected){$('pause').disabled=status!=='running';$('stop').disabled=status==='stopped';}stepLayout(status);if(typeof takeover!=='undefined')takeover.refresh();}
// While stepping, or when the student opens the editor from the remote page while the program runs, the game shrinks the workbench to the
// right side, without the block categories, so the student sees the robot react beside the highlighted block; it returns to full screen when the program stops. The student can still expand it to edit.
let compact=false,expanded=false;
function stepLayout(status){const active=status==='running'||status==='paused';if(!active)expanded=false;const want=active&&!expanded&&connected;$('compact').hidden=!active;const label=want?tr('放大編輯器','Expand editor'):tr('縮小編輯器','Shrink editor');$('compact').dataset.icon=want?'expand':'shrink';$('compact').dataset.tip=label;$('compact').setAttribute('aria-label',label);if(want===compact)return;compact=want;document.body.classList.toggle('compact',want);
 // The block categories fold away so the narrow workbench keeps its width for the program and the world beside it.
 const box=workspace.getToolbox();if(box){box.clearSelection();box.setVisible(!want);Blockly.svgResize(workspace);}
 request('layout',{compact:want}).catch(()=>{});}
$('compact').onclick=()=>{expanded=compact;stepLayout(pollStatus);};
// The narrow workbench follows the stepped block when it moves out of view. The full editor moves only when the running block
// enters another stack, such as a function made in this program, and returns to the main stack when the function ends, also when
// the program ends inside a function.
// The block that failed gets a red outline instead of the executing-block highlight, so the student can tell
// "this is where it went wrong" apart from "this is what is running now".
let errorBlock='',errorSource='';function markError(id,source=''){const old=errorBlock?workspace.getBlockById(errorBlock):null;old?.getSvgRoot()?.classList.remove('cceduErrorBlock');errorBlock=id||'';errorSource=errorBlock?source:'';const block=errorBlock?workspace.getBlockById(errorBlock):null;block?.getSvgRoot()?.classList.add('cceduErrorBlock');}
let shownBlock='',shownStack='';function keepInView(id){if(!id||id===shownBlock){shownBlock=id||'';if(!id&&shownStack){const last=workspace.getBlockById(shownStack),start=workspace.getTopBlocks(false).find(b=>b.type==='ccedu_start');shownStack='';if(last&&start&&FUNCTION_DEFINITIONS.includes(last.type))workspace.centerOnBlock(start.id);}return;}shownBlock=id;const block=workspace.getBlockById(id);if(!block)return;const stack=block.getRootBlock().id,entered=stack!==shownStack;shownStack=stack;if(!compact&&!entered)return;const r=block.getBoundingRectangleWithoutChildren(),v=workspace.getMetricsManager().getViewMetrics(true);if(r.left<v.left||r.top<v.top||r.right>v.left+v.width||r.bottom>v.top+v.height)workspace.centerOnBlock(id);}
// A teacher guiding a student opens the same robot: an idle editor follows the other side's saved edits and program switches.
// While the student looks at the program the robot is running, the robot's selected program stays the one they started; don't pull them back to it.
let inspecting=null;
async function follow(data){
 if(dirty||saving||loading)return;const other=!!data.selected&&data.selected!==program?.id&&!(inspecting?.id===program?.id&&inspecting?.from===data.selected),newer=program&&data.revision>program.revision;if(!other&&!newer)return;
 try{const result=await request('programInspect',{id:other?data.selected:program.id});if(dirty||saving)return;const view=[workspace.scrollX,workspace.scrollY,workspace.scale];display(result.program);if(!other&&program.mode==='blocks'){workspace.setScale(view[2]);workspace.scroll(view[0],view[1]);}await library();$('programs').value=program.id;setStatus(tr('已同步最新程式','Synced the latest program'));}catch{}
}
// When the robot calls another program from the library, the editor goes into that program and highlights its block, and it comes back
// to the caller when the call returns, level by level for nested calls. It follows only while the student is watching the running
// program, never over unsaved edits, and a stopped run returns to the program the robot started. Opening the editor again starts
// fresh, so it always goes to the block the robot is on, including a block that failed in a called program.
let lastRun=null;
async function followCall(s,data){
 const stack=Array.isArray(s.callStack)?s.callStack:[],current=s.programId||'',shown=program?.id||'';if(!shown)return;const active=s.status==='running'||s.status==='paused'||s.status==='error';
 const watching=!!shown&&(shown===lastRun||(lastRun===null||s.status!=='error')&&stack.includes(shown));lastRun=shown&&shown===current?current:'';
 let target='';if(active&&current&&current!==shown&&watching)target=current;else if(s.status==='stopped'&&inspecting?.auto&&inspecting.id===shown&&data.selected&&data.selected!==shown)target=data.selected;
 if(!target||dirty||saving||loading)return;
 const result=await request('programInspect',{id:target});if(dirty||saving||loading||(program?.id||'')!==shown)return;
 display(result.program);inspecting=target===data.selected?null:{id:target,from:data.selected,auto:true};lastRun=active?target:'';$('programs').value=target;
 if(target!==data.selected)setStatus(tr('子程式：','Called program: ')+(result.program.name||''));else if(!runtimeError)setStatus('',false);
}
async function state(){if(!connected||polling)return;polling=true;try{const data=await request('programState',{id:program?.id});suggestions=data.suggestions;applyPolicy(data);suggest();await follow(data);const s=data.runtime;await followCall(s,data);pollStatus=s.status;const labels={stopped:tr('待命','Stopped'),running:tr('執行中','Running'),paused:tr('已暫停','Paused'),error:tr('程式錯誤','Error')};$('runtimeStatus').textContent=labels[s.status]||s.status;runtimeControls(s.status);$('step').textContent=s.debugMode==='actions'?tr('逐步動作','Step action'):tr('逐步','Step');const variables=Object.entries(s.variables).map(([k,v])=>`${k} = ${JSON.stringify(v)}`).join('\n');$('variables').textContent=variables;$('variables').hidden=$('variablesTitle').hidden=!variables;debugProgram=s.programId||'';$('debugSource').hidden=s.status==='stopped'||!debugProgram||debugProgram===program?.id;$('debugSource').textContent=tr('切換到機器人正在執行的程式：','Switch to the program the robot is running: ')+(s.programName||debugProgram);const shown=s.status!=='stopped'&&(!debugProgram||debugProgram===program?.id)?s.blockId||null:null;const failed=s.status==='error';workspace.highlightBlock(failed?null:shown);if(failed)markError(shown,'runtime');else if(s.status!=='stopped'||errorSource==='runtime')markError(null);keepInView(shown);lastErrorProgram=s.programId||'';highlightLua(s.status==='error'&&program?.mode==='lua'&&program.id===lastErrorProgram?s.errorLine||0:0);if(s.status==='error'&&s.error){runtimeError=s.error;setStatus(s.error,true);}else if(runtimeError){runtimeError='';setStatus('',false);}await remoteState();}catch(e){setStatus(e.message,true);}finally{polling=false;}}

// The server holds each running block for a 0.5 s beat; polling faster while running keeps the highlight on the robot's current action.
let pollStatus='stopped';function pollDelay(){return pollStatus==='running'?150:300;}
(function poll(){setTimeout(async()=>{if(ready&&connected)await state();poll();},pollDelay());})();
async function leave(op,data={}){clearTimeout(timer);lastRun=null;let recovered=false;try{if(ready)await save();}catch(e){if(!connected)throw e;await backup();recovered=true;}await request(op,{...data,recovered});}
window.cceduEscape=()=>{if(pickFor){closePick();return;}if(Blockly.DropDownDiv.isVisible()){Blockly.DropDownDiv.hideWithoutAnimation();return;}if(!$('newMenu').hidden){newMenu(false);return;}const dialog=[...document.querySelectorAll('dialog[open]')].pop();if(dialog){dialog.close();return;}window.cceduSaveAndClose();};
// Non-ASCII text, such as committed Chinese IME input, arrives from the game here because the pinned macOS JCEF key path drops it.
const editableFocus=()=>{const el=document.activeElement;return el&&!el.readOnly&&!el.disabled&&(el.isContentEditable||el.tagName==='TEXTAREA'||el.tagName==='INPUT'&&!['checkbox','radio','button','submit'].includes(el.type))?el:null;};
const textField=el=>el&&(el.tagName==='INPUT'||el.tagName==='TEXTAREA')?el:null;
// Input-method text still being composed sits inside the field like native typing; it is swapped out when the word is
// committed or cancelled. setRangeText fires no input event, so autosave and name checks never see the unfinished word.
let preedit=null;
function clearPreedit(){if(!preedit)return;const {el,start,end}=preedit;preedit=null;if(el.isConnected)el.setRangeText('',start,end,'end');}
window.cceduInsertText=text=>{clearPreedit();if(editableFocus())document.execCommand('insertText',false,text);};
// QA only (-Dsingularbots.inputTrace=true in the game): reports what mouse and key input reaches the page, so a
// background test shows whether a click arrived twice, where it landed and which category it opened or closed.
function installInputTrace(){
 const send=event=>window.cceduQuery?.({request:JSON.stringify({op:'inputTrace',event:Math.round(performance.now())+' '+event}),persistent:false,onSuccess(){},onFailure(){}});
 const name=t=>t?.id||(typeof t?.className==='string'?t.className:t?.className?.baseVal)||t?.tagName||'';
 for(const type of ['pointerdown','pointerup','keydown'])document.addEventListener(type,e=>send(type==='keydown'?`keydown ${e.key} meta=${e.metaKey} ctrl=${e.ctrlKey} target=${name(e.target)}`:`${type} ${e.button} at ${e.clientX},${e.clientY} target=${name(e.target)}`),true);
 for(const type of ['focus','blur'])addEventListener(type,()=>send(`window ${type} hasFocus=${document.hasFocus()}`));
 workspace.addChangeListener(e=>{if(e.type===Blockly.Events.TOOLBOX_ITEM_SELECT)send(`category ${e.oldItem||'-'} -> ${e.newItem||'-'} flyout=${!!workspace.getToolbox()?.getFlyout()?.isVisible()}`);});
}
// Offscreen CEF on macOS has no Edit menu, so Cmd+V and Cmd+A do nothing in text fields; the game sends its clipboard here.
// Other keys still reach the page, so Blockly keeps its own block copy and paste on the workspace.
window.cceduEditCommand=(command,text='')=>{const el=editableFocus();if(!el)return;if(command==='paste'&&text)document.execCommand('insertText',false,el.tagName==='INPUT'?text.replace(/\r?\n/g,' '):text);else if(command==='selectAll'){if(typeof el.select==='function')el.select();else document.execCommand('selectAll');}};
// Where the caret is, so the game can open the input method's candidate list beside it instead of in a screen corner.
let reportedCaret='';
function reportCaret(){
 const el=editableFocus(),r=el?.getBoundingClientRect();let at=null;
 if(r&&r.width){let x=r.left+8;const field=textField(el);
  if(field?.tagName==='INPUT'){const style=getComputedStyle(field),ctx=(reportCaret.canvas||=document.createElement('canvas')).getContext('2d');ctx.font=style.font;x=r.left+parseFloat(style.paddingLeft)+parseFloat(style.borderLeftWidth)+Math.min(ctx.measureText(field.value.slice(0,field.selectionEnd??0)).width-field.scrollLeft,field.clientWidth);}
  at={x:Math.round(x),y:Math.round(r.top),height:Math.round(r.height)};}
 const key=JSON.stringify(at);if(key===reportedCaret||typeof window.cceduQuery!=='function')return;reportedCaret=key;
 window.cceduQuery({request:JSON.stringify({op:'imeCaret',...at}),persistent:false,onSuccess(){},onFailure(){}});
}
for(const type of ['focusin','focusout','input','click','keyup'])document.addEventListener(type,()=>setTimeout(reportCaret));
// The game reports the input-method text still being composed (it draws none itself). Text fields show it in place;
// anything else gets a box beside the focused element. An open modal dialog sits in the browser's top layer above
// everything else, so the box moves inside it to stay visible.
window.cceduComposition=text=>{
 const field=textField(editableFocus());
 if(field||preedit){if(preedit&&preedit.el!==field)clearPreedit();
  if(field&&text){const start=preedit?preedit.start:field.selectionStart,end=preedit?preedit.end:field.selectionEnd;field.setRangeText(text,start,end,'end');preedit={el:field,start,end:start+text.length};}
  else clearPreedit();
  const box=$('imeComposition');if(box)box.hidden=true;reportCaret();if(field)return;}
 let box=$('imeComposition');if(!box){box=document.createElement('div');box.id='imeComposition';box.setAttribute('aria-hidden','true');}const host=document.querySelector('dialog[open]')||document.body;if(box.parentElement!==host)host.append(box);
 box.textContent=text;box.hidden=!text;if(!text)return;const el=document.activeElement,r=el&&el!==document.body?el.getBoundingClientRect():{left:16,top:16,bottom:16};
 box.style.left=`${Math.max(4,Math.min(r.left,innerWidth-box.offsetWidth-4))}px`;box.style.top=`${r.bottom+box.offsetHeight+8<innerHeight?r.bottom+4:Math.max(4,r.top-box.offsetHeight-4)}px`;};
window.cceduSaveAndClose=safely(()=>leave('close'));window.cceduNavigate=safely(page=>leave('navigate',{page}));$('close').onclick=window.cceduSaveAndClose;
$('more').onclick=safely(async()=>{await remoteState();$('moreDialog').showModal();});$('moreClose').onclick=()=>$('moreDialog').close();
$('selectedSlot').replaceChildren(...Array.from({length:16},(_,i)=>new Option(String(i+1),String(i))));
// The slot picker shows the inventory of the robot this editor was opened from.
setInventorySource(async()=>connected?(await request('remoteState')).inventory||null:null);
async function remoteState(){if(!connected)return;remote=await request('remoteState');$('selectedSlot').value=String(remote.slot||0);$('lock').disabled=!remote.teacher;$('lock').textContent=remote.locked?tr('解除鎖定','Unlock robot'):tr('鎖定機器人','Lock robot');$('lock').title=remote.teacher?'':tr('只有老師能鎖定','Teacher only');$('restore').disabled=!remote.hasSnapshot;if(remote.error)setStatus(remote.error,true);}

$('snapshot').onclick=safely(async()=>{await save();await request('remoteAction',{action:'snapshot'});setStatus(tr('正在儲存復原點','Snapshot requested'));});
$('restore').onclick=safely(async()=>{await request('remoteAction',{action:'restore'});await state();});
$('selectedSlot').onchange=safely(()=>request('remoteAction',{action:'select',value:Number($('selectedSlot').value)}));
$('lock').onclick=safely(()=>request('remoteAction',{action:'lock',value:remote.locked?0:1}));
// The light/dark switch is remembered by the game for this computer, so every robot's editor opens in the same look.
const theme=installThemeToggle({themes,workspace,root:document.documentElement,button:$('theme'),tr,start:startTheme,persist:name=>request('setTheme',{theme:name})});
// index.html's fixed text was put in the game language by localizePage at startup.

window.cceduSmoke=async()=>{
 smokeTesting=true;const sample={blocks:{languageVersion:0,blocks:[{type:'ccedu_turtle_command',id:'smoke-forward',x:48,y:48,fields:{COMMAND:'forward'},next:{block:{type:'ccedu_turtle_command',id:'smoke-left',fields:{COMMAND:'turnLeft'}}}}]}};
 loading=true;loadWorkspace(workspace,sample);const roundtrip=workspaceJSON();loadWorkspace(workspace,roundtrip);loading=false;const lua=preview(workspace);const good=workspace.getAllBlocks(false).length===2&&lua.includes('turtle.forward()')&&lua.includes('turtle.turnLeft()')&&!!workspace.getBlockById('smoke-forward');clearTimeout(timer);refreshPreview();const fonts=await document.fonts.load('14px CCEdu','海龜程式');const fontLoaded=fonts.some(face=>face.status==='loaded');await request('smokeResult',{passed:good&&fontLoaded,fontLoaded,blockCount:2,blocklyVersion:Blockly.VERSION});
};
async function reloadAfterTakeover(){const rows=await library(),chosen=rows.find(p=>p.id===savedSelection)||rows.find(p=>p.id===program?.id)||rows[0];if(chosen){const data=await request('programInspect',{id:chosen.id});display(data.program);}else display(null);}
const takeover=installTakeover({document,workspace,tr,busy:()=>workspace.isDragging()||!!preedit,save,
 reload:reloadAfterTakeover,
 ack:(token,saved)=>request('takeoverAck',{token,saved}),status:setStatus,restore:()=>{editingPolicy();syncSave();runtimeControls(pollStatus);Blockly.svgResize(workspace);workspace.scrollCenter();if(connected&&!program)safely(()=>fresh(visualEditor?'blocks':'lua'))();},freeze:()=>{Blockly.hideChaff();for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();document.activeElement?.blur();},visual:()=>visualEditor,compact:()=>compact});
takeover.setToggle(enabled=>request('programTakeover',{enabled}));
window.cceduTakeoverNotice=(data,prepare)=>{takeover.update(data);if(prepare)void takeover.prepare(data);};
async function initialize(){
 try{const data=await request('ready',{blocklyVersion:Blockly.VERSION});if(data.theme)theme.apply(data.theme);if(data.inputTrace)installInputTrace();connected=!!data.turtle;ready=true;recoveredDraft=data.recovery||null;$('recover').hidden=!recoveredDraft?.program||!!recoveredDraft.synced;
  for(const id of ['new','delete','copy','recover','programs','programName','convert','run','pause','resume','step','stop','history','robots'])$(id).disabled=!connected;
  for(const id of ['targets','share'])$(id).disabled=!connected;
  if(connected){const programs=await library();if(programs.length){const chosen=programs.find(p=>p.id===savedSelection)||programs[0];const p=await request(takeover.controlBlocked?'programInspect':'programLoad',{id:chosen.id});display(p.program);$('programs').value=program.id;}else if(takeover.writeBlocked)display(null);else await fresh(visualEditor?'blocks':'lua');$('stage').textContent=tr('我的私人程式庫 · 自己的機器人共用','My private library · shared by my robots');}
  else{if(data.draft){loading=true;loadWorkspace(workspace,data.draft.workspace);loading=false;}$('stage').textContent=tr('本機測試工作台 · 還沒連上機器人','Local test workbench · turtle not connected');}
  setStatus('');request('opened').catch(()=>{});
  // The picker catalog is rendered by the game once per session; fields gain their icons when it arrives.
  if(connected)safely(async()=>{await transfers();setItemCatalog(await request('itemCatalog'));rerenderItemFields();})();
  if(data.smoke)await window.cceduSmoke();
 }catch(e){setStatus(e.message,true);}
}
initialize();new ResizeObserver(()=>Blockly.svgResize(workspace)).observe($('workspace'));
