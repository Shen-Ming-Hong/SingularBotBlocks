// Light and dark looks for the whole workbench. The page colours live in theme.css under html[data-theme=light];
// Blockly draws its own workspace, toolbox and flyout colours, so each look also has a Blockly theme.
export const THEMES=['dark','light'];
const font={family:'CCEdu, system-ui, sans-serif',size:15};
const colours={
 dark:{workspaceBackgroundColour:'#1c282c',toolboxBackgroundColour:'#293337',toolboxForegroundColour:'#edf0e8',flyoutBackgroundColour:'#344145',flyoutForegroundColour:'#edf0e8',flyoutOpacity:0.97,scrollbarColour:'#71816f',insertionMarkerColour:'#c5e18e',insertionMarkerOpacity:0.6,cursorColour:'#c5e18e'},
 light:{workspaceBackgroundColour:'#f3f5ee',toolboxBackgroundColour:'#e2e7da',toolboxForegroundColour:'#1f2a22',flyoutBackgroundColour:'#d9e0cf',flyoutForegroundColour:'#1f2a22',flyoutOpacity:0.97,scrollbarColour:'#9aa596',insertionMarkerColour:'#4f7f2c',insertionMarkerOpacity:0.5,cursorColour:'#4f7f2c'}
};
export const normalTheme=name=>THEMES.includes(name)?name:'dark';
export function blocklyThemes(Blockly){
 return Object.fromEntries(THEMES.map(name=>[name,Blockly.Theme.defineTheme(name==='dark'?'minecraft':'minecraft-light',{base:Blockly.Themes.Classic,fontStyle:font,componentStyles:colours[name]})]));
}
/** The look the page opens with: the game passes the saved choice in the address, as it does the language. */
export const initialTheme=search=>normalTheme(new URLSearchParams(search).get('theme'));

/**
 * Wires the header toggle. `persist` tells the game so the next editor opens in the same look; a failure there only
 * means the choice is not remembered, so the switch itself still happens.
 */
export function installThemeToggle({themes,workspace,root,button,tr,persist,start}){
 let current=null;
 function apply(name){
  name=normalTheme(name);if(name===current)return current;current=name;
  root.dataset.theme=name;workspace.setTheme(themes[name]);
  const next=name==='dark'?tr('亮色模式','Light mode'):tr('深色模式','Dark mode');
  button.dataset.icon=name==='dark'?'sun':'moon';button.setAttribute('aria-label',next);button.dataset.tip=next;button.setAttribute('aria-pressed',String(name==='light'));
  return current;
 }
 apply(start);
 button.onclick=()=>{const name=apply(current==='dark'?'light':'dark');Promise.resolve().then(()=>persist(name)).catch(()=>{});};
 return {apply,current:()=>current};
}
