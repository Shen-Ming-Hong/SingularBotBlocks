import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import * as Blockly from 'blockly';
import {blocklyThemes,initialTheme,installThemeToggle} from '../src/theme.js';

const themes=blocklyThemes(Blockly);
function page(start='dark'){
 const applied=[],saved=[];const root={dataset:{}};
 const button={dataset:{},attrs:{},setAttribute(k,v){this.attrs[k]=v;},onclick:null};
 const workspace={setTheme:t=>applied.push(t)};
 const toggle=installThemeToggle({themes,workspace,root,button,tr:(zh)=>zh,start,persist:name=>{saved.push(name);}});
 return {toggle,root,button,applied,saved};
}

test('the editor opens dark unless the game passes the saved light choice',()=>{
 assert.equal(initialTheme('?lang=zh_tw'),'dark');
 assert.equal(initialTheme('?lang=zh_tw&theme=light'),'light');
 assert.equal(initialTheme('?theme=neon'),'dark');
});

test('the toggle switches the page and Blockly together and asks the game to remember it',async()=>{
 const {toggle,root,button,applied,saved}=page();
 assert.equal(root.dataset.theme,'dark');assert.equal(button.dataset.icon,'sun');assert.equal(button.attrs['aria-label'],'亮色模式');
 button.onclick();await Promise.resolve();
 assert.equal(toggle.current(),'light');assert.equal(root.dataset.theme,'light');assert.equal(applied.at(-1),themes.light);
 assert.equal(button.dataset.icon,'moon');assert.equal(button.dataset.tip,'深色模式');assert.equal(button.attrs['aria-pressed'],'true');
 assert.deepEqual(saved,['light']);
 button.onclick();await Promise.resolve();
 assert.equal(root.dataset.theme,'dark');assert.equal(applied.at(-1),themes.dark);assert.deepEqual(saved,['light','dark']);
});

test('a choice the game reports on ready is applied without being saved again',()=>{
 const {toggle,root,saved,applied}=page('dark');const before=applied.length;
 toggle.apply('light');assert.equal(root.dataset.theme,'light');assert.deepEqual(saved,[]);
 toggle.apply('light');assert.equal(applied.length,before+1);
});

test('the switch still happens when the game cannot remember it',async()=>{
 const root={dataset:{}},button={dataset:{},setAttribute(){}};
 installThemeToggle({themes,workspace:{setTheme(){}},root,button,tr:a=>a,start:'dark',persist:()=>Promise.reject(new Error('offline'))});
 button.onclick();await new Promise(r=>setTimeout(r));assert.equal(root.dataset.theme,'light');
});

test('light Blockly colours differ from dark and the page ships the light stylesheet',()=>{
 assert.notEqual(themes.light.componentStyles.workspaceBackgroundColour,themes.dark.componentStyles.workspaceBackgroundColour);
 assert.equal(themes.dark.componentStyles.workspaceBackgroundColour,'#1c282c');
 const html=readFileSync(new URL('../src/index.html',import.meta.url),'utf8');
 assert.match(html,/href="theme\.css"/);assert.match(html,/id="theme"[^>]*class="icon-only"/);
 const css=readFileSync(new URL('../src/theme.css',import.meta.url),'utf8');
 for(const part of ['header','aside','.blocklyToolboxDiv','.blocklyFlyoutBackground','#askDialog','#moreDialog','blocklyGridPattern'])assert.ok(css.includes(part),part);
 assert.match(readFileSync(new URL('../build.mjs',import.meta.url),'utf8'),/theme\.css/);
});
