// Blockly asks for variable names and confirmations through window.prompt/confirm/alert, which CEF shows as an
// operating-system window titled "JavaScript Prompt" outside the game. These replace them with the editor's own dialog.
export const NAME_LIMIT=64;

/** The first "<base>N" name not already used, for a new variable the student can accept or type over. */
export function suggestName(base,used){const taken=new Set(used);let n=1;while(taken.has(base+n))n++;return base+n;}

/**
 * Blockly opens both the create and the rename prompt with an empty value: a new variable gets a free "變數N" name and a
 * rename starts from the current name, so either can be accepted as is or edited.
 */
export function variableDefault(Blockly,workspace,base){
 return message=>{const names=workspace.getAllVariables().map(v=>v.name);
  if(message===Blockly.Msg.NEW_VARIABLE_TITLE)return suggestName(base,names);
  return names.find(name=>Blockly.Msg.RENAME_VARIABLE_TITLE.replace('%1',name)===message)||'';};
}

/**
 * Routes Blockly.dialog alert/confirm/prompt to the #askDialog element. Requests arriving while one is open wait in turn.
 * `newName` gives the default for a prompt Blockly opens empty.
 */
export function installDialogs(Blockly,{$,tr,newName=()=>'',composing=()=>false}){
 const dialog=$('askDialog'),form=$('askForm'),message=$('askMessage'),input=$('askInput'),error=$('askError'),ok=$('askOk'),cancel=$('askCancel');
 const queue=[];let current=null;
 const valid=()=>current?.kind!=='prompt'||input.value.trim()!=='';
 const validate=()=>{const good=valid();ok.disabled=!good;error.textContent=good?'':tr('名稱不能空白','The name cannot be empty');return good;};
 function show(){
  if(current||!queue.length)return;current=queue.shift();
  const prompt=current.kind==='prompt';
  message.textContent=current.message;input.hidden=!prompt;cancel.hidden=current.kind==='alert';error.textContent='';ok.disabled=false;
  ok.textContent=tr('確定','OK');cancel.textContent=tr('取消','Cancel');
  if(prompt){input.maxLength=NAME_LIMIT;input.value=Array.from(current.value||newName(current.message)||'').slice(0,NAME_LIMIT).join('');}
  dialog.showModal();
  // Selected text is replaced by the first typed letter or committed IME text, so a suggested name never has to be erased first.
  if(prompt){input.focus();input.select();validate();}else ok.focus();
 }
 // Closing by any route (Cancel, the game's Escape, a page reload) answers the request once; only OK carries a value.
 function finish(accepted){
  const request=current;if(!request)return;current=null;
  if(dialog.open)dialog.close();
  if(request.kind==='prompt')request.callback(accepted?input.value.trim():null);
  else if(request.kind==='confirm')request.callback(accepted);
  else request.callback?.();
  show();
 }
 const ask=(kind,text,value,callback)=>{queue.push({kind,message:String(text??''),value:value==null?'':String(value),callback});show();};
 form.addEventListener('submit',event=>{event.preventDefault();if(composing()||!validate())return;finish(true);});
 cancel.addEventListener('click',()=>finish(false));
 // A queued request may already have reopened the dialog by the time the previous close event arrives.
 dialog.addEventListener('close',()=>{if(!dialog.open)finish(false);});
 input.addEventListener('input',validate);
 Blockly.dialog.setAlert((text,callback)=>ask('alert',text,'',callback));
 Blockly.dialog.setConfirm((text,callback)=>ask('confirm',text,'',callback));
 Blockly.dialog.setPrompt((text,value,callback)=>ask('prompt',text,value,callback));
 return {isOpen:()=>!!current};
}
