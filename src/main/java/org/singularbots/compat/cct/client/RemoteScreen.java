package org.singularbots.compat.cct.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import org.singularbots.compat.cct.JuniorTurtleEntity;
import org.singularbots.compat.cct.RemoteMenu;
import org.singularbots.network.RemoteNetwork;
import org.singularbots.turtle.Appearance;
import org.singularbots.client.LegacyGui;
import org.singularbots.client.LegacyGui.IconButton;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Legacy icon controls and four side tabs, with a live server-authorized inventory. */
public final class RemoteScreen extends AbstractContainerScreen<RemoteMenu> implements org.singularbots.client.ScreenWalking.Walkable {
    private enum Page { REMOTE, CUSTOMISE, INVENTORY }
    private record Control(AbstractWidget widget,Page page,int x,int y) { }
    private final List<Control> controls=new ArrayList<>();
    private final List<IconButton> tabs=new ArrayList<>();
    private JsonObject state=new JsonObject();
    private Page page=Page.REMOTE;
    private EditBox name;
    private IconButton cameraButton;
    private IconButton runtimeLeft,runtimeMiddle,runtimeRight,takeAll;
    private final List<IconButton> movement=new ArrayList<>();
    private boolean updatingName,nameDirty;
    private int nameChangedAt;
    private int ticks,cameraToggledAt=-20,runtimeActedAt=-20,openedAt,prewarmDelay=1;
    private org.singularbots.client.EditorScreen warmEditor;
    private int widgetMouseButton=-1;
    private Entity previousCamera;
    private CameraType previousCameraType;
    private ArmorStand camera;
    private BlockPos robotPos;
    private Component diagnostic=Component.empty();
    private String runtimeOp="";
    public RemoteScreen(RemoteMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=96;imageHeight=124;}
    private Component text(String key){return Component.translatable("remote.singularbots."+key);}
    private static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("singularbots","textures/gui/legacy/"+name+".png");}
    private void action(String key,int value,String valueText){if(!key.equals("state"))diagnostic=Component.empty();PacketDistributor.sendToServer(new RemoteNetwork.Action(menu.turtleId,key,value,valueText));}
    private <T extends AbstractWidget>T control(T widget,Page page,int x,int y){addRenderableWidget(widget);controls.add(new Control(widget,page,x,y));return widget;}
    private IconButton icon(String label,String image,Page page,int x,int y,Runnable run){return control(new IconButton(text(label),image,18,run),page,x,y);}
    @Override protected void init(){
        String unsavedName=nameDirty&&name!=null?name.getValue():null;
        super.init();controls.clear();tabs.clear();movement.clear();
        String[] commands={"up","forward","dig","turnLeft","camera","turnRight","down","back","place"};
        String[] images={"button_moveup","button_moveforward","button_dig","button_turnleft","button_show_turtle_view","button_turnright","button_movedown","button_moveback","button_place"};
        for(int i=0;i<commands.length;i++){
            String command=commands[i];var b=icon(command,images[i],Page.REMOTE,7+i%3*22,7+i/3*22,()->{if(command.equals("camera"))toggleCamera();else action(command,0,"");});
            if(!command.equals("camera"))movement.add(b);
            if(command.equals("camera"))cameraButton=b;
        }
        runtimeLeft=icon("restore","button_recall",Page.REMOTE,7,73,()->{if(runtimeStatus().equals("stopped"))action("restore",0,"");else runtimeAction("programStop");});
        runtimeMiddle=icon("step","button_step",Page.REMOTE,29,73,()->runtimeAction(runtimeStatus().equals("running")?"programStop":"programStep"));
        runtimeRight=icon("run","button_run",Page.REMOTE,51,73,()->runtimeAction(runtimeStatus().equals("running")?"programPause":runtimeStatus().equals("paused")?"programResume":runtimeStatus().equals("error")?"programStop":"programRun"));
        name=control(new EditBox(font,0,0,80,18,text("name")){
            @Override public void renderWidget(GuiGraphics g,int x,int y,float partial){
                if(isFocused()){super.renderWidget(g,x,y,partial);return;}
                LegacyGui.field(g,getX(),getY(),width,height);
                String value=font.plainSubstrByWidth(getValue().isEmpty()?text("name").getString():getValue(),width-6);
                g.drawString(font,value,getX()+(width-font.width(value))/2,getY()+5,0xffffffff,false);
            }
        },Page.CUSTOMISE,7,7);name.setMaxLength(32);name.setHint(text("name"));
        name.setResponder(value->{if(!updatingName){nameDirty=true;nameChangedAt=ticks;}});
        if(unsavedName!=null){updatingName=true;name.setValue(unsavedName);updatingName=false;}
        icon("previous_decoration","button_previous",Page.CUSTOMISE,7,29,()->cycleDecoration(-1));
        icon("next_decoration","button_next",Page.CUSTOMISE,69,29,()->cycleDecoration(1));
        icon("previous_colour","button_previous",Page.CUSTOMISE,7,51,()->cycleColour(-1));
        icon("next_colour","button_next",Page.CUSTOMISE,69,51,()->cycleColour(1));
        // A small bar in the free space under the robot's slots 13-16: the server moves the whole backpack to the player.
        takeAll=icon("take_all","button_take_all",Page.INVENTORY,192,92,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,RemoteMenu.TAKE_ALL));takeAll.setSize(34,14);
        String[] tabImages={"tab_program","tab_customise","tab_inventory","tab_remote"};
        String[] tabLabels={"program","customise","inventory","controls"};
        for(int i=0;i<tabImages.length;i++){
            final int index=i;
            var tab=new IconButton(text(tabLabels[i]),tabImages[i],24,()->{
                if(!menu.getCarried().isEmpty()){diagnostic=text("return_item");return;}
                saveName();
                if(index==0)minecraft.setScreen(takeEditor());
                else selectPage(index);
            });
            tab.tab=true;tabs.add(addRenderableWidget(tab));
        }
        var close=new IconButton(Component.translatable("gui.done"),"button_close",10,this::onClose);control(close,null,0,0);
        layout();updateCameraButton();action("state",0,"");openedAt=ticks;
    }
    // The programming tab opens an editor whose browser already loaded while the controller was open.
    private org.singularbots.client.EditorScreen takeEditor(){
        var editor=warmEditor;warmEditor=null;prewarmDelay=20;
        if(editor!=null&&editor.reusable())return editor;
        if(editor!=null)editor.dispose();
        return new org.singularbots.client.EditorScreen(this,false,menu.turtleId);
    }
    private String runtimeStatus(){return state.has("runtimeStatus")?state.get("runtimeStatus").getAsString():"stopped";}
    private void runtimeAction(String op){
        // Stop/run and pause/resume share a button. A doubled click must not flip straight back, e.g. stop then run again.
        if(!op.equals(runtimeOp)&&ticks-runtimeActedAt<20)return;runtimeOp=op;runtimeActedAt=ticks;
        var request=new JsonObject();request.addProperty("op",op);
        if(state.has("program")&&!state.get("program").getAsString().isEmpty())request.addProperty("id",state.get("program").getAsString());
        else if(op.equals("programRun")||op.equals("programStep")){diagnostic=text("choose_program");return;}
        ProgramClient.request(menu.turtleId,request,reply->{var result=JsonParser.parseString(reply).getAsJsonObject();if(!result.get("ok").getAsBoolean())diagnostic=errorText(result.get("error").getAsString());action("state",0,"");},error->diagnostic=errorText(error));
    }
    private void saveName(){if(nameDirty&&name!=null){nameDirty=false;action("name",0,name.getValue());}}
    private void cycleColour(int delta){
        int current=state.has("colour")?state.get("colour").getAsInt():-1,index=0; // an uncoloured robot is drawn white
        for(int i=0;i<Appearance.COLOURS.length;i++)if(Appearance.COLOURS[i]==current)index=i;
        // The owner chose (2026-10-16) that the colour arrows step the same way as the decoration arrows: right is the next colour.
        action("colour",Math.floorMod(index+delta,Appearance.COLOURS.length),"");
    }
    private void cycleDecoration(int delta){String current=state.has("decoration")?state.get("decoration").getAsString():"none";action("decoration",Math.floorMod(Appearance.DECORATIONS.indexOf(current)+delta,Appearance.DECORATIONS.size()),"");}
    private void layout(){
        imageWidth=page==Page.INVENTORY?252:page==Page.CUSTOMISE?94:76;imageHeight=page==Page.CUSTOMISE?97:page==Page.REMOTE?98:113;
        var frame=LegacyGui.frame(width,height);
        leftPos=frame.x()+frame.width()-imageWidth;topPos=frame.y()+frame.height()-imageHeight;
        // The game's own hotbar, hearts and XP bar stay visible behind this screen (the bottom 40 px, 182 px wide in the
        // middle); lift the panel and its side tabs above them so the two never overlap.
        if(leftPos<width/2+91&&leftPos+imageWidth+24>width/2-91)topPos=Math.max(4,Math.min(topPos,height-40-imageHeight));
        for(var c:controls){c.widget.visible=c.page==null||c.page==page;c.widget.setPosition(leftPos+(c.page==null?imageWidth-10:c.x),topPos+c.y);}
        for(int i=0;i<tabs.size();i++){var tab=tabs.get(i);tab.setPosition(leftPos+imageWidth-3,topPos+imageHeight-93+i*23);tab.selected=i==(page==Page.REMOTE?3:page==Page.CUSTOMISE?1:2);}
    }
    /** The side tabs stick out past the panel; item-list mods such as JEI must keep their own buttons off them. */
    public List<net.minecraft.client.renderer.Rect2i> sideAreas(){
        var areas=new ArrayList<net.minecraft.client.renderer.Rect2i>();
        for(var tab:tabs)if(tab.visible)areas.add(new net.minecraft.client.renderer.Rect2i(tab.getX(),tab.getY(),tab.getWidth(),tab.getHeight()));
        return areas;
    }
    public void selectPage(int index){if(index<1||index>3)throw new IllegalArgumentException("Unknown remote page");page=index==1?Page.CUSTOMISE:index==2?Page.INVENTORY:Page.REMOTE;if(name!=null)name.setFocused(false);setFocused(null);hoveredSlot=null;layout();}
    private static Component errorText(String error){
        var message=org.singularbots.turtle.RemoteDiagnostics.describe(error);
        return message.key().isEmpty()?Component.literal(message.fallback()):Component.translatable(message.key(),message.arguments().toArray());
    }
    public JsonObject editorState(){var copy=state.deepCopy();if(copy.has("error"))copy.addProperty("error",errorText(copy.get("error").getAsString()).getString());
        // This robot's own 16 slots, already synced into the open menu, for the editor's slot picker.
        var inventory=new com.google.gson.JsonArray();for(int i=0;i<16;i++){var item=menu.getSlot(i).getItem();var slot=new com.google.gson.JsonArray();if(!item.isEmpty()){slot.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString());slot.add(item.getCount());}inventory.add(slot);}
        copy.add("inventory",inventory);return copy;}
    public void takeover(org.singularbots.network.ProgramNetwork.TakeoverNotice notice){if(!state.has("takeover")||!state.getAsJsonObject("takeover").get("library").getAsString().equals(notice.library().toString()))return;state.add("takeover",JsonParser.parseString(notice.json()));applyTakeover();}
    private boolean controlBlocked(){return state.has("takeover")&&state.getAsJsonObject("takeover").has("controlBlocked")&&state.getAsJsonObject("takeover").get("controlBlocked").getAsBoolean();}
    private void applyTakeover(){boolean blocked=controlBlocked();for(var c:controls)if(c.page!=null&&c.widget!=cameraButton&&!movement.contains(c.widget)&&c.widget!=runtimeLeft&&c.widget!=runtimeMiddle&&c.widget!=runtimeRight)c.widget.active=!blocked;if(blocked){if(name!=null)name.setFocused(false);for(var b:movement)b.active=false;if(runtimeLeft!=null){runtimeLeft.active=false;runtimeMiddle.active=false;runtimeRight.active=false;}}}
    public void receive(RemoteNetwork.State message){
        if(!message.turtle().equals(menu.turtleId))return;state=JsonParser.parseString(message.json()).getAsJsonObject();
        if(!state.get("error").getAsString().isEmpty())diagnostic=errorText(state.get("error").getAsString());
        else if(state.has("historyDiagnostic")&&!state.get("historyDiagnostic").getAsString().isEmpty())diagnostic=errorText(state.get("historyDiagnostic").getAsString());
        if(name!=null&&!name.isFocused()&&!nameDirty){updatingName=true;name.setValue(state.get("name").getAsString());updatingName=false;}
        runtimeLeft.active=true;runtimeRight.active=true;String status=runtimeStatus();boolean running=status.equals("running"),paused=status.equals("paused"),error=status.equals("error");
        runtimeLeft.visible=page==Page.REMOTE&&(paused||status.equals("stopped")&&hasSnapshot());runtimeLeft.image=paused?"button_stop":"button_recall";runtimeLeft.setMessage(text(paused?"stop":"restore"));runtimeLeft.setTooltip(Tooltip.create(runtimeLeft.getMessage()));
        runtimeMiddle.image=running?"button_stop":"button_step";runtimeMiddle.setMessage(text(running?"stop":"step"));runtimeMiddle.setTooltip(Tooltip.create(runtimeMiddle.getMessage()));runtimeMiddle.active=!error;
        runtimeRight.image=running?"button_pause":error?"button_abort":"button_run";runtimeRight.setMessage(text(running?"pause":paused?"resume":error?"stop":"run"));runtimeRight.setTooltip(Tooltip.create(runtimeRight.getMessage()));
        for(var b:movement)b.active=status.equals("stopped");
        applyTakeover();
        updateCamera();
    }
    @Override protected void containerTick(){super.containerTick();ProgramClient.tick();
        // Preload at once when the controller opens; after returning from the editor wait a second for the robot's state.
        // Loading the browser can stall client frames, so not while a program runs and the student watches the robot.
        if(warmEditor==null&&ticks-openedAt>=prewarmDelay&&!runtimeStatus().equals("running"))warmEditor=org.singularbots.client.EditorScreen.prewarm(this,menu.turtleId);if(++ticks%10==0)action("state",0,"");if(nameDirty&&ticks-nameChangedAt>=6)saveName();}
    private void toggleCamera(){
        // A doubled click (seen up to 0.5 s apart) would switch the view on and straight back off.
        if(ticks-cameraToggledAt<20)return;cameraToggledAt=ticks;
        if(camera!=null){restoreCamera();return;}if(!state.has("x")||minecraft.level==null)return;
        var pos=new BlockPos(state.get("x").getAsInt(),state.get("y").getAsInt(),state.get("z").getAsInt());
        if(!minecraft.level.hasChunkAt(pos)){diagnostic=Component.translatable("message.singularbots.unavailable");return;}
        previousCamera=minecraft.getCameraEntity();previousCameraType=minecraft.options.getCameraType();camera=new ArmorStand(minecraft.level,0,0,0);camera.setInvisible(true);
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);updateCamera();minecraft.setCameraEntity(camera);updateCameraButton();
    }
    /** Runs after block entities tick so the camera uses the same animation frame as the turtle model. */
    void updateCamera(){
        if(camera==null||!state.has("x"))return;int x=state.get("x").getAsInt(),y=state.get("y").getAsInt(),z=state.get("z").getAsInt();
        if(minecraft.level==null||!minecraft.level.hasChunkAt(new BlockPos(x,y,z))){restoreCamera();return;}
        var robot=robot(new BlockPos(x,y,z));
        if(robot==null){
            float yaw=Direction.from3DDataValue(state.get("direction").getAsInt()).toYRot();
            cameraPose(new Vec3(x,y,z),yaw,new Vec3(x,y,z),yaw);return;
        }
        // Follow CC's own move/turn animation: the previous tick pose is fraction 0, this tick is fraction 1.
        var base=Vec3.atLowerCornerOf(robot.getBlockPos());
        cameraPose(base.add(robot.getRenderOffset(0)),robot.getRenderYaw(0),base.add(robot.getRenderOffset(1)),robot.getRenderYaw(1));
    }
    private void cameraPose(Vec3 from,float fromYaw,Vec3 to,float toYaw){
        // CC wraps visual yaw into [0,360); unwrap it so a turn across south never spins the long way.
        fromYaw=camera.getYRot()+Mth.wrapDegrees(fromYaw-camera.getYRot());toYaw=fromYaw+Mth.wrapDegrees(toYaw-fromYaw);
        camera.setPos(to.x+.5,to.y+.65-camera.getEyeHeight(),to.z+.5);camera.setYRot(toYaw);camera.setXRot(0);
        // LivingEntity.getViewYRot reads head rotation, not body rotation.
        camera.setYHeadRot(toYaw);camera.yHeadRotO=fromYaw;camera.yRotO=fromYaw;camera.xRotO=0;
        camera.xo=from.x+.5;camera.yo=from.y+.65-camera.getEyeHeight();camera.zo=from.z+.5;
    }
    /** State arrives every 10 ticks, so find the moved block entity by its synced identity first. */
    private JuniorTurtleEntity robot(BlockPos reported){
        for(var start:robotPos==null?List.of(reported):List.of(robotPos,reported)){
            var found=robotAt(start);if(found!=null)return found;
            for(var d:Direction.values()){found=robotAt(start.relative(d));if(found!=null)return found;}
        }
        return null;
    }
    private JuniorTurtleEntity robotAt(BlockPos pos){
        if(!minecraft.level.hasChunkAt(pos)||!(minecraft.level.getBlockEntity(pos) instanceof JuniorTurtleEntity robot)||!robot.identity().equals(menu.turtleId))return null;
        robotPos=pos.immutable();return robot;
    }
    public boolean isCameraView(){return camera!=null;}
    private void updateCameraButton(){if(cameraButton!=null){cameraButton.image=camera==null?"button_show_turtle_view":"button_show_normal_view";cameraButton.setMessage(text(camera==null?"camera":"player_view"));cameraButton.setTooltip(Tooltip.create(cameraButton.getMessage()));}}
    private void restoreCamera(){if(camera==null)return;minecraft.setCameraEntity(previousCamera==null||previousCamera.isRemoved()?minecraft.player:previousCamera);if(previousCameraType!=null)minecraft.options.setCameraType(previousCameraType);camera=null;robotPos=null;previousCamera=null;previousCameraType=null;updateCameraButton();}
    @Override public void removed(){org.singularbots.client.ScreenWalking.releaseAll();restoreCamera();if(warmEditor!=null){warmEditor.dispose();warmEditor=null;}super.removed();}
    @Override public void onClose(){saveName();super.onClose();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!controlBlocked()&&name!=null&&name.visible&&name.isFocused()&&key!=GLFW.GLFW_KEY_ESCAPE&&key!=GLFW.GLFW_KEY_TAB){if(key==GLFW.GLFW_KEY_ENTER){saveName();setFocused(null);}else name.keyPressed(key,scan,modifiers);return true;}
        // The movement keys walk the player while the controller stays open; Space would otherwise press the last clicked button.
        if(org.singularbots.client.ScreenWalking.press(key,scan,modifiers))return true;
        if(controlBlocked()&&page==Page.INVENTORY&&key!=GLFW.GLFW_KEY_ESCAPE)return true;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){return org.singularbots.client.ScreenWalking.release(key,scan)||super.keyReleased(key,scan,modifiers);}
    @Override public boolean letsPlayerWalk(){return name==null||!name.isFocused();}
    @Override public boolean mouseClicked(double x,double y,int button){
        // Container screens process outside-slot clicks even when a widget handled them.
        // Consume tabs first so a rejected page change cannot drop the carried stack.
        widgetMouseButton=-1;
        if(page==Page.INVENTORY&&!controlBlocked()&&button==2){for(int i=0;i<16;i++){var slot=menu.getSlot(i);if(x>=leftPos+slot.x&&x<leftPos+slot.x+16&&y>=topPos+slot.y&&y<topPos+slot.y+16){widgetMouseButton=button;action("select",i,"");return true;}}}
        for(var child:children())if(child instanceof AbstractWidget w&&w.visible&&w.mouseClicked(x,y,button)){widgetMouseButton=button;setFocused(w);return true;}
        if(page==Page.INVENTORY)return controlBlocked()||super.mouseClicked(x,y,button);
        setFocused(null);return false;
    }
    @Override public boolean mouseReleased(double x,double y,int button){
        if(widgetMouseButton==button){widgetMouseButton=-1;if(getFocused()!=null)getFocused().mouseReleased(x,y,button);return true;}
        return page!=Page.INVENTORY||super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(widgetMouseButton==button)return getFocused()!=null&&getFocused().mouseDragged(x,y,button,dx,dy);
        return page==Page.INVENTORY&&!controlBlocked()&&super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){
        // AbstractContainerScreen renders its container through this method in 1.21.1.
        // Keep the world clear while still drawing the inventory panel and slot wells.
        if(page==Page.INVENTORY)renderBg(g,partial,x,y);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        LegacyGui.panel(g,leftPos,topPos,imageWidth,imageHeight);
        if(page==Page.INVENTORY){for(var slot:menu.slots)slotWell(g,leftPos+slot.x,topPos+slot.y);if(state.has("slot")){int index=state.get("slot").getAsInt();if(index>=0&&index<16){var slot=menu.getSlot(index);g.renderOutline(leftPos+slot.x-1,topPos+slot.y-1,18,18,0xffeeeeee);}}}
        if(page==Page.CUSTOMISE){
            int colour=state.has("colour")?state.get("colour").getAsInt():Appearance.COLOURS[0];if(colour<0)colour=Appearance.COLOURS[0];
            RenderSystem.setShaderColor((colour>>16&255)/255f,(colour>>8&255)/255f,(colour&255)/255f,1);g.blit(texture("turtle"),leftPos+29,topPos+31,0,0,36,36,36,36);RenderSystem.setShaderColor(1,1,1,1);
            g.blit(texture("turtle_overlay"),leftPos+29,topPos+31,0,0,36,36,36,36);
            String decoration=state.has("decoration")?state.get("decoration").getAsString():"none";
            if(!decoration.equals("none"))g.blit(texture(decoration+"_icon"),leftPos+29,topPos+31,36,36,0f,0f,12,12,12,12);
        }
    }
    private static void slotWell(GuiGraphics g,int x,int y){LegacyGui.slot(g,x-1,y-1);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        if(page==Page.INVENTORY){g.drawString(font,text("player_inventory"),7,7,LegacyGui.TEXT,false);g.drawString(font,text("hotbar"),7,78,LegacyGui.TEXT,false);g.drawString(font,backpackTitle(state.has("name")?state.get("name").getAsString():""),173,7,LegacyGui.TEXT,false);g.fill(171,4,172,imageHeight-4,0xff60451f);g.fill(172,4,173,imageHeight-4,0xffc69b4d);for(int i=0;i<16;i++){var slot=menu.getSlot(i);if(!slot.hasItem()){String number=String.valueOf(i+1);g.drawString(font,number,slot.x+8-font.width(number)/2,slot.y+4,LegacyGui.SLOT_NUMBER,false);}}}
    }
    /** A long robot name is shortened with an ellipsis so "'s backpack" still fits above the 16 slots. */
    private String backpackTitle(String label){
        if(label.isEmpty())return font.plainSubstrByWidth(text("robot_backpack").getString(),74);
        String title=Component.translatable("remote.singularbots.robot_backpack_named",label).getString();
        for(int end=label.length();font.width(title)>74&&end>0;end--)title=Component.translatable("remote.singularbots.robot_backpack_named",label.substring(0,end-1)+"…").getString();
        return font.plainSubstrByWidth(title,74);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        runtimeLeft.visible=page==Page.REMOTE&&(runtimeStatus().equals("paused")||runtimeStatus().equals("stopped")&&hasSnapshot());
        runtimeMiddle.visible=page==Page.REMOTE&&!runtimeStatus().equals("error");
        boolean robotHasItems=false;for(int i=0;i<16;i++)robotHasItems|=menu.getSlot(i).hasItem();takeAll.active=robotHasItems&&!controlBlocked();
        if(page==Page.INVENTORY){super.render(g,x,y,partial);renderTooltip(g,x,y);}
        else{hoveredSlot=null;renderBg(g,partial,x,y);g.pose().pushPose();g.pose().translate(leftPos,topPos,0);renderLabels(g,x,y);g.pose().popPose();for(var r:renderables)r.render(g,x,y,partial);}
        if(camera!=null&&state.has("x"))g.drawString(font,state.get("x").getAsInt()+", "+state.get("y").getAsInt()+", "+state.get("z").getAsInt(),8,8,0xffffff);
        var shown=controlBlocked()?Component.translatable("remote.singularbots.teacher_demonstrating",state.getAsJsonObject("takeover").get("teacher").getAsString()):diagnostic;
        if(!shown.getString().isEmpty()){int available=Math.max(80,leftPos-16);g.fill(6,height-40,8+available,height-8,0xe0202020);g.drawWordWrap(font,shown,8,height-38,available,0xffffaaaa);}
    }
    private boolean hasSnapshot(){return state.has("hasSnapshot")&&state.get("hasSnapshot").getAsBoolean();}
}
