// The fixed text of index.html, which is written in classroom Chinese, in the editor's game language.
// Each entry: CSS selector, what to set (text, label = aria-label and tooltip, aria, placeholder), Chinese, English.
const PAGE=[
 ['#stage','text','機器人程式工作台','My robot programming workspace'],
 ['#history','label','我的紀錄','My history'],
 ['#robots','label','我的機器人','My robots'],
 ['#more','label','機器人設定','Robot settings'],
 ['#save','text','已儲存','Saved'],
 ['#close','text','返回','Back'],
 ['#programbar','aria','程式庫','Library'],
 ['#programs','aria','程式庫','Library'],
 ['#programs option[value=""]','text','程式庫','Library'],
 ['#new','text','新增','New'],
 ['#newBlocks','text','積木程式','Block program'],
 ['#newLua','text','Lua 程式','Lua program'],
 ['#programName','aria','程式名稱','Program name'],
 ['#programName','placeholder','程式名稱','Program name'],
 ['#modeTag','text','積木','Blocks'],
 ['#copy','label','另存副本','Save copy'],
 ['#convert','text','轉成 Lua','To Lua'],
 ['#recover','text','復原未存草稿','Recover unsaved draft'],
 ['#delete','label','刪除程式','Delete program'],
 ['#runtimebar','aria','執行控制','Run controls'],
 ['#runtimeStatus','text','待命','Stopped'],
 ['#run','text','執行','Run'],
 ['#pause','text','暫停','Pause'],
 ['#resume','text','繼續','Resume'],
 ['#step','text','逐步','Step'],
 ['#stop','text','停止','Stop'],
 ['#compact','label','放大編輯器','Expand editor'],
 ['#transferbar','aria','教師發送','Teacher send'],
 ['#targets','aria','接收學生','Recipient student'],
 ['#targets option[value=""]','text','接收學生','Recipient student'],
 ['#share','text','教師發送','Teacher send'],
 ['#visualDisabled','text','老師關閉了積木編輯；還可以用 Lua 寫程式。','The teacher disabled block editing; Lua programs remain available.'],
 ['#luaEditor','aria','Lua 原始碼','Lua source code'],
 ['#previewTitle','text','Lua 預覽','Lua preview'],
 ['#variablesTitle','text','變數檢視','Variables'],
 ['#debugSource','text','切換到機器人正在執行的程式','Switch to the program the robot is running'],
 ['#status','text','正在載入本地編輯器…','Loading the local editor…'],
 ['#historyTitle','text','我的程式與機器人紀錄','My program and robot history'],
 ['#historyClose','text','返回編輯器','Back to editor'],
 ['#robotsTitle','text','我的機器人','My robots'],
 ['#robotsClose','text','返回編輯器','Back to editor'],
 ['#moreTitle','text','機器人設定','Robot settings'],
 ['#snapshot','text','儲存復原點','Save snapshot'],
 ['#restore','text','回到復原點','Restore snapshot'],
 ['#slotLabel','text','使用物品欄第幾格','Inventory slot'],
 ['#selectedSlot','aria','使用物品欄第幾格','Inventory slot'],
 ['#lock','text','鎖定機器人','Lock robot'],
 ['#moreClose','text','返回編輯器','Back to editor'],
 ['#askCancel','text','取消','Cancel'],
 ['#askOk','text','確定','OK'],
];
export const PAGE_TEXT=Object.freeze(PAGE.map(entry=>Object.freeze(entry)));
/** Puts every fixed page text in the current language; run once before the editor fills anything in. */
export function localizePage(document,tr,lang){
 if(lang)document.documentElement.lang=lang;
 for(const [selector,kind,zh,en] of PAGE){
  const el=document.querySelector(selector);if(!el)continue;
  const text=tr(zh,en);
  if(kind==='text')el.textContent=text;
  else if(kind==='placeholder')el.placeholder=text;
  else{el.setAttribute('aria-label',text);if(kind==='label')el.dataset.tip=text;}
 }
}
