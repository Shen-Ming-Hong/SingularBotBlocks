import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {PAGE_TEXT} from '../src/page.js';

const source=readFileSync(new URL('../src/editor.js',import.meta.url),'utf8');
const section=(start,end)=>source.slice(source.indexOf(start),source.indexOf(end));

test('runtime polling preserves Lua error rows until the source or error line changes',()=>{
 const editor={value:'forward()\nerror("test")',scrollTop:0,scrollLeft:0};
 const overlay={replaceChildren(...rows){this.rows=rows;this.updates=(this.updates||0)+1;}};
 const context={$:id=>id==='luaEditor'?editor:overlay,document:{createElement:()=>({})},getComputedStyle:()=>({lineHeight:'30'})};
 runInNewContext(section('let lastErrorLine=','$(\'luaEditor\').onscroll=')+';this.highlight=highlightLua;',context);
 context.highlight(2);
 assert.equal(overlay.rows[1].className,'error-line');
 context.highlight(2);
 assert.equal(overlay.updates,1);
 editor.value+='\nreturn';context.highlight(2);
 assert.equal(overlay.rows.length,3);assert.equal(overlay.updates,2);
 context.highlight(0);
 assert.ok(overlay.rows.every(row=>row.className===''));
});

test('a burst of block changes generates one preview per animation frame without losing edits',()=>{
 const frames=[];let previews=0;
 const context={loading:false,editVersion:0,dirty:false,ready:false,smokeTesting:false,timer:0,
  requestAnimationFrame:fn=>{frames.push(fn);return frames.length;},refreshPreview:()=>previews++,syncSave:()=>{},clearTimeout:()=>{},setTimeout:()=>1};
 runInNewContext(section('let previewFrame=','function suggest()')+';this.change=changed;',context);
 context.change();context.change();context.change();
 assert.equal(frames.length,1);assert.equal(context.editVersion,3);assert.equal(context.dirty,true);
 frames.shift()();assert.equal(previews,1);
 context.change();assert.equal(frames.length,1);
 context.loading=true;context.change();assert.equal(context.editVersion,4);
});

test('the loaded program becomes ready before the optional transfer list and item catalog finish',async()=>{
 const elements=new Map(),requests=[];let status,finishTransfers,optionalTask;
 const context={takeover:{controlBlocked:false},ready:false,connected:false,Blockly:{VERSION:'11.2.2'},tr:(_,en)=>en,
  $:id=>{if(!elements.has(id))elements.set(id,{replaceChildren(){}});return elements.get(id);},
  request:async op=>{requests.push(op);return op==='ready'?{turtle:true}:op==='programLoad'?{program:{id:'saved'}}:{items:[]};},
  setItemCatalog:data=>context.catalog=data,rerenderItemFields:()=>context.rerendered=true,
  library:async()=>[{id:'saved'}],savedSelection:'saved',display:p=>context.program=p,
  setStatus:text=>status=text,safely:fn=>()=>optionalTask=fn(),
  transfers:()=>new Promise(resolve=>finishTransfers=resolve),Option:function(){}};
 runInNewContext(section('async function initialize(){','initialize();new ResizeObserver')+';this.init=initialize;',context);
 await context.init();
 assert.equal(context.program.id,'saved');assert.equal(status,'');
 assert.equal(context.$('programs').disabled,false);
 assert.deepEqual(requests,['ready','programLoad','opened']);
 finishTransfers();await optionalTask;
 assert.equal(requests.at(-1),'itemCatalog');assert.deepEqual(context.catalog,{items:[]});assert.equal(context.rerendered,true);
});

test('runtime lamp follows the state and only offers the pause or resume that applies',()=>{
 const el={},layouts=[];const context={connected:true,tr:zh=>zh,request:async(op,data)=>layouts.push(data.compact),document:{body:{classList:{toggle(){}}}},workspace:{getToolbox:()=>null},$:id=>(el[id]??={dataset:{},setAttribute(){}})};
 runInNewContext(section('function runtimeControls(','async function state()')+';this.controls=runtimeControls;',context);
 context.controls('stopped');
 assert.equal(el.runtimeLamp.dataset.status,'stopped');assert.equal(el.pause.hidden,false);assert.equal(el.pause.disabled,true);assert.equal(el.resume.hidden,true);assert.equal(el.stop.disabled,true);
 context.controls('running');
 assert.equal(el.pause.disabled,false);assert.equal(el.stop.disabled,false);assert.equal(el.resume.hidden,true);
 context.controls('paused');
 assert.equal(el.pause.hidden,true);assert.equal(el.resume.hidden,false);assert.equal(el.stop.disabled,false);assert.deepEqual(layouts,[true]);
 context.controls('error');assert.deepEqual(layouts,[true,false]);
 assert.equal(el.runtimeLamp.dataset.status,'error');assert.equal(el.pause.disabled,true);assert.equal(el.stop.disabled,false);
});

test('only a draft that was not followed by a successful save is offered for recovery',async()=>{
 const el={},drafts=[];const context={connected:true,program:{id:'p',mode:'lua'},locale:'zh_tw',dirty:false,tr:a=>a,
  workspaceJSON:()=>({}),content:()=>({name:'n',mode:'lua',source:'x'}),request:async(op,data)=>drafts.push(data.draft),$:id=>(el[id]??={dataset:{},hidden:true})};
 runInNewContext(section('async function backup(','function save(){')+';this.backup=backup;this.syncSave=syncSave;',context);
 context.$('recover');await context.backup();
 assert.equal(drafts[0].synced,false);assert.equal(el.recover.hidden,true);
 el.recover.hidden=false;await context.backup(true);
 assert.equal(drafts[1].synced,true);assert.equal(el.recover.hidden,true);
 context.syncSave();assert.equal(el.save.disabled,true);assert.equal(el.save.textContent,'已儲存');
 context.dirty=true;context.syncSave();assert.equal(el.save.disabled,false);assert.equal(el.save.dataset.icon,'save');
});

test('the teacher send list keeps its selection and never asks for an inbox or disk',async()=>{
 const el={},asked=[];
 const select=()=>({value:'',options:[],replaceChildren(...o){this.options=o;this.value='';}});
 const context={sharingAllowed:true,tr:a=>a,Option:function(text,value){this.text=text;this.value=value;},placeholder:text=>({text,value:'',hidden:true,disabled:true}),
  $:id=>(el[id]??=(id==='targets'?select():{})),
  request:async op=>{asked.push(op);return {teacher:true,targets:[{name:'B',id:'b'}]};}};
 runInNewContext(section('const fill=','// Lists refresh')+';this.transfers=transfers;',context);
 await context.transfers();el.targets.value='b';await context.transfers();
 assert.equal(el.targets.value,'b');assert.deepEqual(asked,['programTargets','programTargets']);
 context.sharingAllowed=false;await context.transfers();assert.equal(asked.length,2);
});

test('the New button menu opens, creates the chosen program type and closes',async()=>{
 const el={},created=[];let down;
 const context={luaAllowed:true,safely:fn=>fn,fresh:async mode=>created.push(mode),
  document:{addEventListener:(type,fn)=>{if(type==='pointerdown')down=fn;}},
  $:id=>(el[id]??={hidden:id==='newMenu',setAttribute(k,v){this[k]=v;}})};
 runInNewContext(section('const newMenu=','$(\'copy\').onclick'),context);
 el.new.onclick();el.new.onclick();assert.equal(el.newMenu.hidden,false);assert.equal(el.new['aria-expanded'],'true');
 await el.newLua.onclick();assert.deepEqual(created,['lua']);assert.equal(el.newMenu.hidden,true);
 el.new.onclick();down({target:{closest:()=>null}});assert.equal(el.newMenu.hidden,true);
 context.luaAllowed=false;created.length=0;await el.new.onclick();
 assert.deepEqual(created,['blocks']);assert.equal(el.newMenu.hidden,true);
});

test('beginner worlds and students hide every Lua control and the send bar, and close an open Lua program',()=>{
 const el={},shown=[];let status;
 const context={visualEditor:true,luaAllowed:true,sharingAllowed:true,program:{mode:'blocks'},tr:a=>a,
  display:p=>{shown.push(p);context.program=p;},setStatus:(text,error)=>status=[text,error],refreshPreview:()=>shown.push('preview'),
  idText:[],setItemIdText:show=>{context.idText.push(show);return true;},workspace:{getBlocksByType:()=>[]},
  $:id=>(el[id]??={hidden:false,setAttribute(k,v){this[k]=v;}})};
 runInNewContext(section('function editingPolicy()','async function library()')+';this.apply=applyPolicy;',context);
 context.apply({visualEditor:true,lua:false,sharing:false});
 for(const id of ['previewTitle','preview','newLua','convert','modeTag','transferbar'])assert.equal(el[id].hidden,true,id);
 assert.equal(el.new['aria-haspopup'],'false');assert.deepEqual(shown,[]);
 context.program={mode:'lua'};context.apply({visualEditor:true,lua:false,sharing:false});
 assert.deepEqual(shown,[null]);assert.equal(status[1],true);
 context.apply({visualEditor:true,lua:false,sharing:true});assert.equal(el.transferbar.hidden,false);
 context.program={mode:'blocks'};context.apply({visualEditor:true,lua:true,sharing:true});
 for(const id of ['previewTitle','preview','newLua','convert','modeTag','transferbar'])assert.equal(el[id].hidden,false,id);
 assert.equal(el.new['aria-haspopup'],'menu');assert.equal(shown.at(-1),'preview');
 // Item ID blocks show only their look unless the class reads Lua.
 assert.deepEqual(context.idText,[false,false,false,true]);
});

test('continuous runs return to the remote page without forcing the robot camera while stepping stays in the editor',async()=>{
 const buttons={},calls=[];
 const context={program:{id:'p1'},timer:0,clearTimeout:()=>{},save:async()=>calls.push('save'),
  request:async(op,data)=>{calls.push([op,data]);return {};},leave:async(op,data)=>calls.push(['leave',op,data]),state:async()=>calls.push('state'),
  safely:fn=>fn,$:id=>(buttons[id]??={})};
 runInNewContext(section('const watchOps=','async function state()'),context);
 await buttons.run.onclick();
 assert.equal(JSON.stringify(calls.slice(-2)),JSON.stringify([['programRun',{id:'p1'}],['leave','navigate',{page:3}]]));
 calls.length=0;await buttons.resume.onclick();
 assert.equal(JSON.stringify(calls.at(-1)),JSON.stringify(['leave','navigate',{page:3}]));
 for(const id of ['step','pause','stop']){calls.length=0;await buttons[id].onclick();assert.equal(calls.at(-1),'state');assert.ok(!calls.some(c=>c[0]==='leave'));}
 context.request=async()=>{throw new Error('compile error');};calls.length=0;
 await assert.rejects(buttons.run.onclick(),/compile error/);assert.ok(!calls.some(c=>c[0]==='leave'));
});

test('an idle editor follows saved edits and program switches made from another editor on the same robot',async()=>{
 const loads=[],shown=[];let scrolled;
 const context={program:{id:'a',revision:2,mode:'blocks'},inspecting:null,dirty:false,saving:false,loading:false,tr:a=>a,setStatus:()=>{},library:async()=>[],
  workspace:{scrollX:5,scrollY:6,scale:1.2,setScale(v){this.scale=v;},scroll(x,y){scrolled=[x,y];}},
  request:async(op,data)=>{loads.push(data.id);return {program:{id:data.id,revision:9,mode:'blocks'}};},
  display:p=>{shown.push(p.id);context.program=p;},$:()=>({})};
 runInNewContext(section('async function follow(','async function state()')+';this.follow=follow;',context);
 await context.follow({selected:'a',revision:2});assert.deepEqual(loads,[]);
 await context.follow({selected:'a',revision:3});assert.deepEqual(loads,['a']);assert.deepEqual(scrolled,[5,6]);
 await context.follow({selected:'b'});assert.deepEqual(shown,['a','b']);
 context.dirty=true;await context.follow({selected:'c',revision:99});assert.deepEqual(loads,['a','b']);
 context.dirty=false;context.saving=true;await context.follow({selected:'b',revision:99});assert.deepEqual(loads,['a','b']);
 // Looking at the program the robot is running (x, called from b) is not pulled back to b; another switch still is.
 context.saving=false;context.program={id:'x',revision:1,mode:'blocks'};context.inspecting={id:'x',from:'b'};
 await context.follow({selected:'b',revision:1});assert.deepEqual(loads,['a','b']);
 await context.follow({selected:'c',revision:1});assert.deepEqual(loads,['a','b','c']);
});

test('a double click on New creates only one program',async()=>{
 let saves=0;const shown=[];
 const context={creating:false,tr:a=>a,newProgramId:()=>'id'+shown.length,save:async()=>{saves++;await new Promise(r=>setTimeout(r,5));},library:async()=>[],display:p=>shown.push(p),dirty:false};
 runInNewContext(section('// One creation at a time','// A button menu')+';this.fresh=fresh;',context);
 await Promise.all([context.fresh('blocks'),context.fresh('blocks')]);
 assert.equal(shown.length,1);await context.fresh('blocks');assert.equal(shown.length,2);
});

test('non-ASCII text from the game is inserted into the focused text field only',()=>{
 const inserted=[];let active=null;
 const context={window:{},document:{addEventListener(){},get activeElement(){return active;},execCommand:(cmd,ui,text)=>inserted.push([cmd,text])}};
 runInNewContext(section('const editableFocus=','// The game reports the input-method'),context);
 active={tagName:'INPUT',type:'search'};context.window.cceduInsertText('鑽石');
 active={tagName:'TEXTAREA'};context.window.cceduInsertText('海龜');
 active={tagName:'DIV',isContentEditable:true};context.window.cceduInsertText('石');
 active={tagName:'INPUT',type:'checkbox'};context.window.cceduInsertText('x');
 active={tagName:'BUTTON'};context.window.cceduInsertText('x');
 active=null;context.window.cceduInsertText('x');
 assert.deepEqual(inserted,[['insertText','鑽石'],['insertText','海龜'],['insertText','石']]);
});

test('Cmd+V and Cmd+A from the game act only on the focused text field',()=>{
 const done=[];let active=null;
 const context={window:{},document:{addEventListener(){},get activeElement(){return active;},execCommand:(cmd,ui,text)=>done.push([cmd,text])}};
 runInNewContext(section('const editableFocus=','// The game reports the input-method'),context);
 active={tagName:'INPUT',type:'text',select(){done.push(['select']);}};
 context.window.cceduEditCommand('paste','礦石\n數量');context.window.cceduEditCommand('selectAll');context.window.cceduEditCommand('paste','');
 active={tagName:'TEXTAREA',select(){done.push(['select']);}};context.window.cceduEditCommand('paste','a\nb');
 active={tagName:'DIV',isContentEditable:true};context.window.cceduEditCommand('selectAll');
 // On the Blockly workspace the forwarded key keeps block copy and paste; the text command does nothing.
 active={tagName:'svg'};context.window.cceduEditCommand('paste','x');active=null;context.window.cceduEditCommand('paste','x');
 assert.deepEqual(done,[['insertText','礦石 數量'],['select'],['insertText','a\nb'],['selectAll',undefined]]);
});

test('Lua and teacher send controls start hidden until the world policy reveals them',()=>{
 const html=readFileSync(new URL('../src/index.html',import.meta.url),'utf8');
 for(const id of ['previewTitle','preview','transferbar','newLua','modeTag','convert'])assert.match(html,new RegExp(`id="${id}"[^>]* hidden`),id);
 // Toolbars set display by id, which would otherwise outrank the hidden attribute.
 assert.match(readFileSync(new URL('../src/style.css',import.meta.url),'utf8'),/(^|[}\n])\[hidden\]\{display:none!important\}/);
});

test('input-method text being composed is shown beside the focused field and hidden when done',()=>{
 const els={},body={append(e){els[e.id]=e;e.parentElement=body;}};const field={getBoundingClientRect:()=>({left:100,top:50,bottom:80})};
 const context={window:{},innerWidth:800,innerHeight:600,$:id=>els[id],
  document:{addEventListener(){},body,activeElement:field,querySelector:()=>null,createElement:()=>({style:{},offsetWidth:120,offsetHeight:30,setAttribute(){} })}};
 runInNewContext(section('const editableFocus=','window.cceduSaveAndClose='),context);
 context.window.cceduComposition('ㄓㄨㄢ');
 const box=els.imeComposition;assert.equal(box.textContent,'ㄓㄨㄢ');assert.equal(box.hidden,false);assert.equal(box.style.left,'100px');assert.equal(box.style.top,'84px');
 field.getBoundingClientRect=()=>({left:750,top:560,bottom:590});context.window.cceduComposition('ㄓ');
 assert.equal(box.style.left,'676px');assert.equal(box.style.top,'526px');
 context.window.cceduComposition('');assert.equal(box.hidden,true);
});

test('the side panel has no fixed hint text and folds away when it has nothing to show',()=>{
 const html=readFileSync(new URL('../src/index.html',import.meta.url),'utf8'),css=readFileSync(new URL('../src/style.css',import.meta.url),'utf8');
 assert.doesNotMatch(html,/id="hint"/);
 assert.ok(css.includes('aside:not(:has(> :not([hidden]):not(:empty))){display:none}'));
 assert.doesNotMatch(source,/Workbench ready|Program saved on server/);
});

test('runtime polling speeds up only while a program runs so the highlight keeps pace with the robot',()=>{
 const context={};
 runInNewContext(section("let pollStatus=","(function poll()")+';this.delay=pollDelay;this.set=v=>{pollStatus=v;};',context);
 assert.equal(context.delay(),300);
 context.set('running');assert.ok(context.delay()<=150);
 for(const status of ['paused','stopped','error']){context.set(status);assert.equal(context.delay(),300);}
});

test('input-method text stays visible above an open editor dialog',()=>{
 const els={},body={append(e){els[e.id]=e;e.parentElement=body;}},dialog={append(e){e.parentElement=dialog;}};let open=null;
 const field={getBoundingClientRect:()=>({left:100,top:50,bottom:80})};
 const context={window:{},innerWidth:800,innerHeight:600,$:id=>els[id],
  document:{addEventListener(){},body,activeElement:field,querySelector:()=>open,createElement:()=>({style:{},offsetWidth:120,offsetHeight:30,setAttribute(){} })}};
 runInNewContext(section('const editableFocus=','window.cceduSaveAndClose='),context);
 context.window.cceduComposition('ㄅ');assert.equal(els.imeComposition.parentElement,body);
 open=dialog;context.window.cceduComposition('ㄅㄧ');assert.equal(els.imeComposition.parentElement,dialog);
 open=null;context.window.cceduComposition('ㄅㄧㄢ');assert.equal(els.imeComposition.parentElement,body);
});

test('Blockly questions use the editor dialog instead of operating-system windows',()=>{
 const html=readFileSync(new URL('../src/index.html',import.meta.url),'utf8');
 for(const id of ['askDialog','askForm','askMessage','askInput','askError','askOk','askCancel'])assert.match(html,new RegExp(`id="${id}"`),id);
 assert.match(source,/installDialogs\(Blockly,/);
 for(const file of ['editor.js','blocks.js','dialogs.js'])assert.doesNotMatch(readFileSync(new URL('../src/'+file,import.meta.url),'utf8'),/window\.(alert|confirm|prompt)\(|[^.\w](alert|confirm|prompt)\(/,file);
});

test('editor language follows the game setting instead of an in-page selector',()=>{
 const html=readFileSync(new URL('../src/index.html',import.meta.url),'utf8');
 assert.doesNotMatch(html,/id="language"/);
 assert.doesNotMatch(source,/\$\('language'\)/);
 assert.match(source,/new URLSearchParams\(location\.search\)\.get\('lang'\)/);
});

test('the static loading status is translated for non-Chinese game languages',()=>{
 const html=readFileSync(new URL('../src/index.html',import.meta.url),'utf8');
 assert.match(html,/<p id="status"[^>]*>正在載入本地編輯器…<\/p>/);
 assert.ok(PAGE_TEXT.some(([selector,kind,zh,en])=>selector==='#status'&&kind==='text'&&zh==='正在載入本地編輯器…'&&en==='Loading the local editor…'));
 assert.match(source,/localizePage\(document,tr,htmlLang\(locale\)\)/);
});

test('input-method text is composed inside the focused text field and replaced by the committed word',()=>{
 const inserted=[];let reports=[];
 const field={tagName:'INPUT',type:'text',value:'變數1',selectionStart:0,selectionEnd:3,isConnected:true,scrollLeft:0,clientWidth:180,getBoundingClientRect:()=>({left:10,top:20,width:200,height:30}),
  setRangeText(text,start,end){this.value=this.value.slice(0,start)+text+this.value.slice(end);this.selectionStart=this.selectionEnd=start+text.length;}};
 const context={window:{cceduQuery:q=>reports.push(JSON.parse(q.request))},$:()=>null,setTimeout:fn=>fn(),getComputedStyle:()=>({font:'16px x',paddingLeft:'9',borderLeftWidth:'2'}),
  document:{addEventListener(){},activeElement:field,createElement:()=>({getContext:()=>({measureText:t=>({width:t.length*10})})}),
   execCommand:(cmd,ui,text)=>{inserted.push(text);field.setRangeText(text,field.selectionStart,field.selectionEnd);}}};
 runInNewContext(section('const editableFocus=','window.cceduSaveAndClose=')+';this.composing=()=>!!preedit;',context);
 context.window.cceduComposition('ㄎ');assert.equal(field.value,'ㄎ');assert.equal(context.composing(),true);
 context.window.cceduComposition('ㄎㄨㄤˋ');assert.equal(field.value,'ㄎㄨㄤˋ');
 // Backspace in the input method shortens the word in place.
 context.window.cceduComposition('ㄎㄨ');assert.equal(field.value,'ㄎㄨ');
 context.window.cceduComposition('礦');context.window.cceduInsertText('礦');context.window.cceduComposition('');
 assert.equal(field.value,'礦');assert.deepEqual(inserted,['礦']);assert.equal(context.composing(),false);
 // A cancelled word leaves nothing behind.
 context.window.cceduComposition('ㄕ');context.window.cceduComposition('');assert.equal(field.value,'礦');
 assert.ok(reports.length>0&&reports.every(r=>r.op==='imeCaret'));
 assert.deepEqual(reports.at(-1),{op:'imeCaret',x:31,y:20,height:30});
});

test('automatic saving waits until a dragged block is dropped',()=>{
 const timers=[];let saves=0,dragging=true;
 const context={loading:false,editVersion:0,dirty:false,ready:true,smokeTesting:false,timer:0,
  workspace:{isDragging:()=>dragging},save:()=>{saves++;return Promise.resolve();},
  requestAnimationFrame:()=>1,refreshPreview:()=>{},syncSave:()=>{},clearTimeout:()=>{},setTimeout:fn=>{timers.push(fn);return timers.length;}};
 runInNewContext(section('let previewFrame=','function suggest()')+';this.change=changed;',context);
 context.change();timers.shift()();
 assert.equal(saves,0);assert.equal(timers.length,1,'checks again later');
 dragging=false;timers.shift()();
 assert.equal(saves,1);
});

test('stepping or opening the editor while the program runs shrinks the workbench beside the robot without the block categories and stopping restores it, unless the student expands it',()=>{
 const elements={},requests=[],classes=new Set(),toolbox={visible:true,resized:0,cleared:0,clearSelection(){this.cleared++;},setVisible(on){this.visible=on;}};
 const context={connected:true,tr:(zh)=>zh,request:async(op,data)=>{requests.push([op,data.compact]);return {};},
  document:{body:{classList:{toggle:(name,on)=>on?classes.add(name):classes.delete(name)}}},
  workspace:{getToolbox:()=>toolbox},Blockly:{svgResize:()=>toolbox.resized++},$:id=>(elements[id]??={dataset:{},setAttribute(name,value){this[name]=value;}})};
 runInNewContext(section('let compact=false','// The narrow workbench')+';this.layout=stepLayout;',context);
 context.layout('stopped');
 assert.equal(requests.length,0);assert.equal(elements.compact.hidden,true);
 context.layout('paused');
 assert.deepEqual(requests.at(-1),['layout',true]);assert.ok(classes.has('compact'));assert.equal(elements.compact.hidden,false);
 assert.equal(toolbox.visible,false);assert.equal(toolbox.cleared,1);assert.equal(toolbox.resized,1);
 context.layout('paused');assert.equal(requests.length,1);
 context.pollStatus='paused';elements.compact.onclick();
 assert.deepEqual(requests.at(-1),['layout',false]);assert.ok(!classes.has('compact'));assert.equal(toolbox.visible,true);
 assert.equal(elements.compact.dataset.icon,'shrink');assert.equal(elements.compact.dataset.tip,'縮小編輯器');assert.equal(elements.compact['aria-label'],'縮小編輯器');
 context.layout('paused');context.layout('running');assert.equal(requests.length,2);assert.equal(elements.compact.hidden,false);
 context.pollStatus='running';elements.compact.onclick();
 assert.deepEqual(requests.at(-1),['layout',true]);assert.equal(elements.compact.dataset.icon,'expand');assert.equal(elements.compact.dataset.tip,'放大編輯器');assert.equal(elements.compact['aria-label'],'放大編輯器');
 context.layout('stopped');assert.deepEqual(requests.at(-1),['layout',false]);
 context.layout('running');
 assert.deepEqual(requests.at(-1),['layout',true]);
 context.layout('error');
 assert.deepEqual(requests.at(-1),['layout',false]);assert.equal(elements.compact.hidden,true);
});

test('the editor follows the running program into a called library program and back out level by level',async()=>{
 const programs={main:{id:'main',name:'主程式'},sub:{id:'sub',name:'子程式'},deep:{id:'deep',name:'更深'},other:{id:'other',name:'別的'}};
 const shown=[],inspected=[],select={};
 const context={program:programs.main,dirty:false,saving:false,loading:false,inspecting:null,runtimeError:'',tr:zh=>zh,
  $:()=>select,setStatus(){},display(p){shown.push(p.id);context.program=p;},
  request:async(op,{id})=>{inspected.push(op);return {program:programs[id]};}};
 runInNewContext(section('let lastRun=null;','async function state()')+';this.followCall=followCall;this.reopen=()=>{lastRun=null;};',context);
 const poll=(status,stack)=>context.followCall({status,programId:stack.at(-1),callStack:stack},{selected:'main'});
 await poll('running',['main']);assert.deepEqual(shown,[]);
 await poll('running',['main','sub']);assert.equal(context.program.id,'sub');
 await poll('paused',['main','sub','deep']);assert.equal(context.program.id,'deep');
 await poll('running',['main','sub']);assert.equal(context.program.id,'sub');
 await poll('running',['main']);assert.equal(context.program.id,'main');assert.equal(context.inspecting,null);
 assert.ok(inspected.every(op=>op==='programInspect'));
 // Unsaved edits are never replaced, and a program the student opened on purpose is left alone.
 context.dirty=true;await poll('running',['main','sub']);assert.equal(context.program.id,'main');context.dirty=false;
 context.program=programs.other;await poll('running',['main','sub']);assert.equal(context.program.id,'other');
 // Stopping inside a called program returns to the program the robot started.
 context.program=programs.main;await poll('running',['main','sub']);assert.equal(context.program.id,'sub');
 await poll('stopped',['main','sub']);assert.equal(context.program.id,'main');
 // An error inside the called program is shown there, but looking back at the caller afterwards is allowed.
 await poll('running',['main']);await poll('error',['main','sub']);assert.equal(context.program.id,'sub');
 context.program=programs.main;await poll('error',['main','sub']);assert.equal(context.program.id,'main');
 // Opening the editor again always goes to the failed block, even when it first showed the caller.
 context.reopen();await poll('error',['main','sub']);assert.equal(context.program.id,'sub');
 // A poll before any program is loaded does not forget that the editor was just opened.
 context.reopen();context.program=null;await poll('error',['main','sub']);context.program=programs.main;await poll('error',['main','sub']);assert.equal(context.program.id,'sub');
});

test('the full editor returns to the start block when the program ends inside a function',()=>{
 const centered=[],blocks={start:{id:'start',type:'ccedu_start'},def:{id:'def',type:'procedures_defnoreturn'},inner:{id:'inner',getRootBlock:()=>blocks.def}};
 const workspace={getBlockById:id=>blocks[id]||null,getTopBlocks:()=>[blocks.def,blocks.start],centerOnBlock:id=>centered.push(id),
  getMetricsManager:()=>({getViewMetrics:()=>({left:0,top:0,width:100,height:100})})};
 blocks.inner.getBoundingRectangleWithoutChildren=()=>({left:500,top:500,right:520,bottom:520});
 const context={workspace,compact:false,FUNCTION_DEFINITIONS:['procedures_defnoreturn','procedures_defreturn']};
 runInNewContext(section("let shownBlock=''","// A teacher guiding")+';this.keepInView=keepInView;',context);
 context.keepInView('inner');assert.deepEqual(centered,['inner']);
 context.keepInView(null);assert.deepEqual(centered,['inner','start']);
 context.keepInView(null);assert.deepEqual(centered,['inner','start']);
});


test('releasing a takeover opens an existing program even when the teacher deleted the old selection',async()=>{
 const shown=[],loads=[];const context={program:{id:'deleted'},savedSelection:'deleted',library:async()=>[{id:'remaining'}],request:async(op,data)=>{loads.push([op,data.id]);return {program:{id:data.id}};},display:p=>shown.push(p?.id||null)};
 runInNewContext(section('async function reloadAfterTakeover()','const takeover=installTakeover')+';this.reload=reloadAfterTakeover;',context);
 await context.reload();assert.deepEqual(loads,[['programInspect','remaining']]);assert.deepEqual(shown,['remaining']);
 context.library=async()=>[];await context.reload();assert.equal(shown.at(-1),null);assert.equal(loads.length,1);
});
