/**
 * Blockly closes a category's flyout when that category is clicked again. Background clicks sent to the game window
 * are often delivered twice, so the second copy closed the flyout the first one had just opened and no block could
 * be dragged out. A click on the category that is already open now leaves it open; clicking the workspace, another
 * category or dragging a block still closes it as before.
 */
export function openOnlyToolbox(Blockly){
 return class OpenOnlyToolbox extends Blockly.Toolbox{
  onClick_(e){
   const id=e.target&&typeof e.target.getAttribute==='function'?e.target.getAttribute('id'):null;
   const item=id?this.getToolboxItemById(id):null;
   if(item&&item===this.getSelectedItem()&&!Blockly.browserEvents.isRightButton(e)&&this.getFlyout()?.isVisible()){
    this.getWorkspace().hideChaff(true);Blockly.Touch.clearTouchIdentifier();return;
   }
   super.onClick_(e);
  }
 };
}

/**
 * Paste on the workspace right-click menu, using Blockly's own Cmd+V command so it shares one clipboard with Cmd+C /
 * Option+C. Keys with Command cannot be sent to the game while it is in the background, and children may not know the
 * shortcut; the menu works either way, and puts the block where the menu was opened. The block menu has no copy item
 * of its own: 複製一份 already places a copy beside the block, and a second copy item there only confused students.
 * The command runs without Blockly's "no gesture in progress" check: a menu item runs a frame after the click, and the
 * game delivers each click twice, so the second press has already started a gesture on whatever lay under the menu and
 * the check made the item do nothing. That check only keeps a shortcut key from acting mid-drag, and no menu item
 * can be chosen while dragging.
 */
export function registerClipboardMenu(Blockly,tr,notify=()=>{}){
 const {ContextMenuRegistry}=Blockly,shortcut=name=>Blockly.ShortcutRegistry.registry.getRegistry()[name];
 const run=(name,workspace)=>{const command=shortcut(name);return !!command&&!workspace.options.readOnly&&!!command.callback(workspace,{preventDefault(){}});};
 for(const id of ['ccedu_copy','ccedu_paste'])if(ContextMenuRegistry.registry.getItem(id))ContextMenuRegistry.registry.unregister(id);
 ContextMenuRegistry.registry.register({id:'ccedu_paste',scopeType:ContextMenuRegistry.ScopeType.WORKSPACE,weight:0,
  displayText:()=>tr('貼上','Paste'),preconditionFn:scope=>scope.workspace.isFlyout?'hidden':scope.workspace.options.readOnly?'disabled':'enabled',
  callback:(scope,menuEvent)=>{
   const workspace=scope.workspace;
   if(!run('paste',workspace)){notify(tr('還沒有複製積木：先點一下積木再按 Cmd+C。','Nothing copied yet: click a block and press Cmd+C first.'));return;}
   notify('');const pasted=Blockly.common.getSelected();
   if(pasted instanceof Blockly.BlockSvg&&pasted.workspace===workspace&&typeof menuEvent?.clientX==='number')
    pasted.moveTo(Blockly.utils.svgMath.screenToWsCoordinates(workspace,new Blockly.utils.Coordinate(menuEvent.clientX,menuEvent.clientY)));
  }});
}

/**
 * Shift+F10, the menu key or Option+M opens the right-click menu: on the selected block, or on the workspace where the pointer
 * last was when no block is selected. The game calls the returned function for these keys itself (CEF is not relied
 * on to pass function keys through). Right clicks and function keys cannot be sent to the game in the background, so
 * Option+M, a key background play can send as it does Option+C / Option+V, opens the menu too, and this is the usual
 * keyboard way to reach a context menu, so the same menu is still reachable; its items are chosen by click or Enter.
 */
export function installMenuKey(Blockly,workspace,doc=document){
 let pointer=null;
 // Background clicks arrive without pointer moves, so the last click counts too.
 for(const type of ['pointermove','pointerdown'])doc.addEventListener(type,e=>{pointer={clientX:e.clientX,clientY:e.clientY};},true);
 const open=()=>{
  // Typing in a text box keeps the key for the box.
  if(doc.activeElement?.closest?.('input,textarea,select,[contenteditable="true"]'))return false;
  if(Blockly.WidgetDiv.isVisible()||Blockly.DropDownDiv.isVisible())return false;
  const selected=Blockly.common.getSelected(),block=selected instanceof Blockly.BlockSvg&&selected.workspace===workspace?selected:null;
  if(block)block.showContextMenu({...blockMenuAnchor(Blockly,workspace,block),preventDefault(){}});
  else{
   const box=workspace.getParentSvg().getBoundingClientRect(),inside=pointer&&pointer.clientX>=box.left&&pointer.clientX<=box.right&&pointer.clientY>=box.top&&pointer.clientY<=box.bottom;
   workspace.showContextMenu({...(inside?pointer:{clientX:box.left+box.width/2,clientY:box.top+box.height/2}),preventDefault(){}});
  }
  return true;
 };
 doc.addEventListener('keydown',e=>{
  const menuKey=e.key==='ContextMenu'&&!e.altKey&&!e.ctrlKey&&!e.metaKey,shiftF10=e.key==='F10'&&e.shiftKey&&!e.altKey&&!e.ctrlKey&&!e.metaKey;
  // Option+M is the same key on every layout by its code; its typed character differs (µ on a Mac).
  const optionM=e.code==='KeyM'&&e.altKey&&!e.shiftKey&&!e.ctrlKey&&!e.metaKey;
  if(!menuKey&&!shiftF10&&!optionM)return;
  if(open()){e.preventDefault();e.stopPropagation();}
 },true);
 return open;
}

/**
 * Where a block's keyboard-opened menu appears: a little inside the block's top-left corner, kept inside the visible
 * workspace. It comes from the block's workspace position, not the bounding box of its SVG group, which can reach far
 * past the block (a hat block's group spread to the window's left edge and put its menu off screen).
 */
export function blockMenuAnchor(Blockly,workspace,block){
 const corner=Blockly.utils.svgMath.wsToScreenCoordinates(workspace,block.getRelativeToSurfaceXY()),box=workspace.getParentSvg().getBoundingClientRect();
 const inset=Math.round(16*workspace.scale),clamp=(value,low,high)=>Math.max(low,Math.min(high,value));
 return {clientX:clamp(corner.x+inset,box.left+8,Math.max(box.left+8,box.right-8)),clientY:clamp(corner.y+inset,box.top+8,Math.max(box.top+8,box.bottom-8))};
}
