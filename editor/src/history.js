// The history dialog's text. The server stores each record's time in UTC and a program record's message as the
// program's id; students read their own clock time and the program's name.
const pad=n=>String(n).padStart(2,'0');
export function localTime(iso){
 const d=new Date(iso);
 if(Number.isNaN(d.getTime()))return String(iso??'');
 return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}
export function historyText(records,programs,tr){
 const kinds={summon:tr('放置機器人','Robot placed'),destroyed:tr('機器人被破壞','Robot destroyed'),runtime:tr('執行狀態','Runtime'),programSave:tr('儲存程式','Program saved'),programDelete:tr('刪除程式','Program deleted'),programRun:tr('執行程式','Program run'),programStep:tr('逐步執行','Program step'),programShare:tr('分享程式','Program shared'),programAccept:tr('接收程式','Program accepted'),programImport:tr('匯入程式','Program imported'),recalled:tr('召回機器人','Robot recalled'),programExport:tr('匯出程式','Program exported')};
 const statuses={stopped:tr('待命','Stopped'),running:tr('執行中','Running'),paused:tr('已暫停','Paused'),error:tr('程式錯誤','Error')};
 const names=new Map((programs||[]).map(p=>[p.id,p.name]));
 return records.slice().reverse().map(r=>{
  const message=r.kind?.startsWith('program')&&names.has(r.message)?names.get(r.message):r.message;
  return `${localTime(r.time)} · ${kinds[r.kind]||r.kind} · ${r.name||String(r.robot||'').slice(0,8)} · ${statuses[r.status]||r.status} (${r.x}, ${r.y}, ${r.z})${message?'\n'+message:''}`;
 }).join('\n\n');
}
