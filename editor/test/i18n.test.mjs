import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,readdirSync} from 'node:fs';
import {setLanguage,tr,translate,fill,htmlLang,inLanguage,language,LOCALE} from '../src/i18n.js';
import {editorKeys,placeholders} from '../i18n/keys.mjs';
import {bearing,describeRobot} from '../src/robots.js';

const dir=new URL('../i18n/',import.meta.url);
const languages=JSON.parse(readFileSync(new URL('languages.json',dir),'utf8'));
const catalog=locale=>JSON.parse(readFileSync(new URL(`${locale}.json`,dir),'utf8'));

test('Chinese and English come from the code; other languages look the English text up and fall back to it',()=>{
 setLanguage('zh_tw');assert.equal(tr('第 %1 格','Slot %1',3),'第 3 格');
 setLanguage('en_us');assert.equal(tr('第 %1 格','Slot %1',3),'Slot 3');
 setLanguage('ja_jp',{'Slot %1':'スロット%1'});assert.equal(tr('第 %1 格','Slot %1',3),'スロット3');
 assert.equal(tr('執行','Run'),'Run');
 // Blockly block messages keep their own %1 slots when no arguments are given.
 assert.equal(tr('移動 %1','move %1'),'move %1');assert.equal(fill('%2 %1',['a','b']),'b a');
 setLanguage('../evil');assert.equal(language(),'en_us');
 setLanguage('de_de',{Run:'Ausführen'});assert.equal(inLanguage('zh_tw',()=>tr('執行','Run')),'執行');assert.equal(translate('Run'),'Ausführen');
 setLanguage('zh_tw');
});

test('HTML language tags follow the Minecraft language code',()=>{
 assert.equal(htmlLang('zh_tw'),'zh-Hant');assert.equal(htmlLang('zh_cn'),'zh-Hans');assert.equal(htmlLang('ja_jp'),'ja-JP');assert.equal(htmlLang('bar'),'bar');assert.equal(htmlLang('fil_ph'),'fil-PH');
});

test('the robot list reads in the current game language',()=>{
 setLanguage('fr_fr',{'Robot %1':'Robot n°%1','%1 blocks %2':'%1 blocs au %2','north':'nord','Program: %1':'Programme : %1'});
 const view=describeRobot({number:2,program:'A',status:'idle',x:0,y:64,z:-20,dimension:'minecraft:overworld'},{x:0,y:64,z:0,dimension:'minecraft:overworld'},false);
 assert.equal(view.title,'Robot n°2');assert.match(view.detail,/^Programme : A · 20 blocs au nord/);
 assert.equal(bearing({x:0,y:64,z:0},{x:0,y:64,z:-20},true),'北 20 格');
 setLanguage('zh_tw');
});

test('every Minecraft language has an editor catalog with every message and the same placeholders',async()=>{
 const keys=await editorKeys();
 assert.ok(keys.length>150,'messages were found in the source');
 const files=new Set(readdirSync(dir).filter(f=>f.endsWith('.json')&&f!=='languages.json').map(f=>f.slice(0,-5)));
 for(const locale of Object.keys(languages)){
  assert.match(locale,LOCALE);
  if(locale==='zh_tw'||locale==='en_us'){assert.ok(!files.has(locale),`${locale} is written in the code`);continue;}
  assert.ok(files.has(locale),`missing i18n/${locale}.json`);
  const messages=catalog(locale);
  for(const key of keys){
   assert.equal(typeof messages[key],'string',`${locale}: missing ${JSON.stringify(key)}`);
   assert.ok(messages[key].trim(),`${locale}: empty ${JSON.stringify(key)}`);
   assert.deepEqual(placeholders(messages[key]),placeholders(key),`${locale}: placeholders of ${JSON.stringify(key)}`);
  }
  for(const key of Object.keys(messages))assert.ok(keys.includes(key),`${locale}: no longer used ${JSON.stringify(key)}`);
 }
 for(const file of files)assert.ok(file in languages,`i18n/${file}.json is not a Minecraft language`);
});

test('the Minecraft 1.21.1 language list is complete',()=>{
 assert.equal(Object.keys(languages).length,143);
 for(const code of ['zh_tw','zh_cn','zh_hk','ja_jp','ko_kr','en_us','en_ud','ar_sa','lol_us','zlm_arab'])assert.ok(code in languages,code);
});
