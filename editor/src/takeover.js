// A handoff freezes input before draining saves; releasing restores input only after the latest server copy is loaded.
export function installTakeover({document,workspace,tr,busy,save,reload,ack,status,freeze=()=>{},restore=()=>{},visual,compact}){
 let state={epoch:-1,phase:'',writeBlocked:false,controlBlocked:false},syncing=false,needsSync=false,preparing='',draining=false,wasBlocked=false,button;
 const disabled=new Map();
 const block=(id,yes)=>{const node=el(id);if(yes){if(!disabled.has(id))disabled.set(id,node.disabled);node.disabled=true;}else if(disabled.has(id)){node.disabled=disabled.get(id);disabled.delete(id);}};
 const el=id=>document.getElementById(id),banner=document.createElement('p');banner.id='takeoverStatus';banner.setAttribute('role','status');banner.hidden=true;el('runtimebar').after(banner);
 const write=()=>needsSync||syncing||!!state.writeBlocked&&!(state.drain&&state.phase==='preparing'&&busy());
 const control=()=>needsSync||syncing||!!state.controlBlocked;
 function refresh(){
  const blocked=write();if(workspace.options.readOnly!==blocked){workspace.options.readOnly=blocked;for(const block of workspace.getAllBlocks(false))block.setEditable(block.isOwnEditable());}const restoring=!blocked&&wasBlocked;if(blocked&&!wasBlocked)freeze();wasBlocked=blocked;workspace.getToolbox()?.setVisible(!blocked&&visual()&&!compact());
  for(const id of ['programName','luaEditor'])el(id).readOnly=blocked;
  for(const id of ['new','newBlocks','newLua','delete','copy','convert','recover','save','share'])block(id,blocked);
  for(const id of ['programs','run','step','pause','resume','stop','snapshot','restore','selectedSlot','lock'])block(id,control());
  banner.hidden=!state.phase&&!needsSync&&!syncing;banner.textContent=needsSync||syncing?tr('正在取得最新程式…','Loading the latest program…'):state.phase==='preparing'?tr('正在儲存學生的內容，準備交接…','Saving the student’s work before handing over…'):tr(`老師示範中：${state.teacher||''}`,`Teacher demonstration: ${state.teacher||''}`);
  if(state.canTakeover&&!button){button=document.createElement('button');button.id='takeover';button.type='button';button.setAttribute('role','switch');button.dataset.icon='robot';el('compact').before(button);button.onclick=async()=>{button.disabled=true;try{await save();await requestToggle(!state.mine);}catch(e){status(e.message,true);}finally{refresh();}};}
  if(!state.canTakeover&&button){button.remove();button=null;}
  if(restoring)restore();
  if(button){button.setAttribute('aria-checked',String(!!state.mine&&!!state.phase));button.textContent=state.mine&&state.phase?tr('結束接手','End takeover'):tr('老師接手','Teacher takeover');button.setAttribute('aria-label',button.textContent);button.dataset.tip=button.textContent;button.disabled=state.phase==='preparing'||needsSync||syncing;}
 }
 let requestToggle=async()=>{};
 async function sync(){
  if(!needsSync||syncing)return;syncing=true;refresh();
  try{await reload();needsSync=false;}catch(e){status(e.message,true);}finally{syncing=false;refresh();}
 }
 function update(next){
  if(!next||next.epoch<state.epoch)return;const previous=state;state=next;
  if(previous.phase==='active'&&previous.writeBlocked&&!state.writeBlocked||previous.phase!=='active'&&state.phase==='active'&&state.mine)needsSync=true;
  refresh();void sync();
  if(next.error)status(tr('接手已取消：學生內容未能儲存，請先處理儲存問題。','Takeover cancelled: the student’s work could not be saved.'),true);
 }
 async function prepare(next){
  update(next);if(!next.token||preparing===next.token)return;preparing=next.token;
  try{
   while(busy()){await new Promise(resolve=>setTimeout(resolve,30));if(state.token!==next.token||state.phase!=='preparing')return;}
   if(state.token!==next.token||state.phase!=='preparing')return;
   draining=true;refresh();await save();await ack(next.token,true);
  }catch(e){status(e.message,true);await ack(next.token,false);}finally{draining=false;preparing='';refresh();}
 }
 return {update,prepare,refresh,setToggle:fn=>{requestToggle=fn;},get writeBlocked(){return write();},get controlBlocked(){return control();},get draining(){return draining;}};
}
