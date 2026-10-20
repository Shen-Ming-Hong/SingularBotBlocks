import test from 'node:test';
import assert from 'node:assert/strict';
import {localTime,historyText} from '../src/history.js';

const zh=(a)=>a,en=(_,b)=>b;
test('history times use the player clock, not UTC',()=>{
 const iso='2026-10-19T09:45:01.123Z',d=new Date(iso),p=n=>String(n).padStart(2,'0');
 assert.equal(localTime(iso),`${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`);
 assert.doesNotMatch(localTime(iso),/[TZ]/);
 assert.equal(localTime('not a time'),'not a time');
});
test('history names programs and translates statuses',()=>{
 const records=[{time:'2026-10-19T01:00:00Z',kind:'programSave',robot:'0123456789',name:'',status:'stopped',x:1,y:2,z:3,message:'p1'},
  {time:'2026-10-19T01:01:00Z',kind:'runtime',robot:'0123456789',name:'小明',status:'error',x:1,y:2,z:3,message:'沒有東西可以挖'}];
 const text=historyText(records,[{id:'p1',name:'挖礦'}],zh);
 const [runtime,save]=text.split('\n\n');
 assert.match(runtime,/執行狀態 · 小明 · 程式錯誤 \(1, 2, 3\)\n沒有東西可以挖$/);
 assert.match(save,/儲存程式 · 01234567 · 待命 \(1, 2, 3\)\n挖礦$/);
 assert.match(historyText(records,[],en),/Program saved · 01234567 · Stopped \(1, 2, 3\)\np1$/);
});
