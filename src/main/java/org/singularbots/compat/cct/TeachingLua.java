package org.singularbots.compat.cct;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.component.ComputerComponents;
import dan200.computercraft.api.lua.*;
import org.singularbots.program.*;
import java.util.*;

public final class TeachingLua {
    public static void register(){
        ComputerCraftAPI.registerAPIFactory(system->{var access=system.getComponent(ComputerComponents.TURTLE);return access instanceof dan200.computercraft.shared.turtle.core.TurtleBrain brain&&brain.getOwner() instanceof JuniorTurtleEntity t?new Edu(system,t):null;});
        ComputerCraftAPI.registerAPIFactory(system->{var access=system.getComponent(ComputerComponents.TURTLE);return access instanceof dan200.computercraft.shared.turtle.core.TurtleBrain brain&&brain.getOwner() instanceof JuniorTurtleEntity t?new TurtleEdu(system,t):null;});
    }
    public static final class Edu implements ILuaAPI {
        private final IComputerSystem system;
        private final ProgramRuntime runtime;
        private final UUID identity;
        private long generation;
        private UUID manualOrigin;
        public Edu(IComputerSystem system,JuniorTurtleEntity t){this.system=system;runtime=t.runtime();identity=t.identity();}
        public String[] getNames(){return new String[]{"edu"};}
        @Override public void startup(){generation=runtime.generation();}
        @Override public void shutdown(){if(generation==runtime.generation())runtime.stop();}
        @LuaFunction public byte[] takeProgram(){return LuaText.encode(runtime.take());}
        @LuaFunction public MethodResult checkpoint(String id,Map<?,?> values)throws LuaException{var decoded=new LinkedHashMap<String,Object>();for(var e:values.entrySet())if(e.getKey() instanceof String key)decoded.put(LuaText.decode(key),e.getValue() instanceof String s?LuaText.decode(s):e.getValue());runtime.visit(LuaText.decode(id),decoded);return waitForPermission();}
        private MethodResult waitForPermission()throws LuaException{
            try{if(runtime.permit())return MethodResult.of();}
            catch(IllegalStateException e){throw new LuaException(LuaText.error(e.getMessage()),0);}
            return MethodResult.pullEvent("ccedu_debug",args->waitForPermission());
        }
        /** Queued behind the robot's previous action, so it answers only once that animation has finished. */
        @LuaFunction public MethodResult pace(){return system.getComponent(ComputerComponents.TURTLE).executeCommand(access->dan200.computercraft.api.turtle.TurtleCommandResult.success(new Object[]{runtime.beat(access.getLevel().getGameTime())}));}
        @LuaFunction public void setStopped(){runtime.stop();}
        @LuaFunction public void setRunning(){runtime.running();}
        @LuaFunction public void setPaused(){runtime.pause();}
        @LuaFunction public void setErrored(String error){runtime.errored(LuaText.decode(error));}
        @LuaFunction public void setErroredAt(String error,String id){runtime.erroredAt(LuaText.decode(error),LuaText.decode(id));}
        @LuaFunction public void enterProgram(String name){runtime.enterLibrary(LuaText.decode(name));}
        @LuaFunction public void leaveProgram(){runtime.leaveLibrary();}
        @LuaFunction public void setCurrentSlot(Optional<Integer> slot){int n=slot.orElse(0);if(n<0||n>4096)throw new IllegalArgumentException("Invalid legacy slot");runtime.slot(n-1);}
        @LuaFunction public int getCurrentSlot(){return runtime.slot()+1;}
        @LuaFunction public void setVariable(String name,Object value){runtime.putVariable(LuaText.decode(name),value instanceof String s?LuaText.decode(s):value);}
        @LuaFunction public void clearVariable(String name){runtime.clearVariable(LuaText.decode(name));}
        @LuaFunction public void clearVariables(){runtime.clearVariables();}
        @LuaFunction public MethodResult saveTurtleState(){return system.getComponent(ComputerComponents.TURTLE).executeCommand(Restoration::save);}
        @LuaFunction public MethodResult restoreTurtleState(){return system.getComponent(ComputerComponents.TURTLE).executeCommand(access->{var t=Restoration.owner(access);return t==null||t.snapshot()==null?dan200.computercraft.api.turtle.TurtleCommandResult.failure("Save a snapshot first"):t.snapshot().restore(access);});}
        @LuaFunction(mainThread=true) public boolean hasProgram(Optional<String> name)throws LuaException{
            try{var store=ProgramService.store(system.getLevel());if(name.isPresent())store.named(current().libraryId(),LuaText.decode(name.get()));else {var t=current();if(t.selectedProgram()==null)return false;store.load(t.libraryId(),t.selectedProgram());}return true;}catch(Exception e){return false;}
        }
        @LuaFunction(mainThread=true) public Object[] getProgram(Optional<String> name,Optional<String> annotations)throws LuaException{
            try{var store=ProgramService.store(system.getLevel());var t=current();if(name.isEmpty()&&t.selectedProgram()==null)return new Object[]{true,Map.of()};var compiled=ProgramStore.compile(name.isPresent()?store.named(current().libraryId(),LuaText.decode(name.get())):store.load(t.libraryId(),t.selectedProgram()),Set.of("full","subprogram").contains(annotations.orElse("")));var lines=new LinkedHashMap<Integer,byte[]>();String[] text=compiled.lua().split("\n",-1);for(int i=0;i<text.length;i++)lines.put(i+1,LuaText.encode(text[i]));return new Object[]{true,lines};}catch(Exception e){return new Object[]{false,LuaText.encode(e.getMessage())};}
        }
        private JuniorTurtleEntity current()throws LuaException{if(system.getLevel().getBlockEntity(system.getPosition()) instanceof JuniorTurtleEntity t&&t.identity().equals(identity))return t;throw new LuaException("Junior turtle is no longer available");}
        @LuaFunction(mainThread=true) public String setManualProgram(Map<?,?> content)throws LuaException{
            try{var text=new StringBuilder();for(int i=1;i<=4096;i++){var value=content.containsKey((double)i)?content.get((double)i):content.get(i);if(value==null)break;if(!(value instanceof String line))throw new LuaException("Program lines must be strings");text.append(LuaText.decode(line)).append('\n');if(text.length()>ProgramStore.MAX_BYTES)throw new LuaException("Program exceeds 1 MiB");}var t=current();if(manualOrigin==null)manualOrigin=t.selectedProgram();var id=UUID.randomUUID();var p=new com.google.gson.JsonObject();p.addProperty("name","Manual "+id.toString().substring(0,8));p.addProperty("mode","lua");p.addProperty("source",text.toString());ProgramService.store(system.getLevel()).save(t.libraryId(),id,0,p);t.selectProgram(id);return id.toString();}catch(LuaException e){throw e;}catch(Exception e){throw new LuaException(LuaText.error(e.getMessage()));}
        }
        @LuaFunction(mainThread=true) public void clearManualProgram()throws LuaException{current().selectProgram(manualOrigin);manualOrigin=null;}
        @LuaFunction(mainThread=true) public byte[] librarySource(String name)throws LuaException{try{String decoded=LuaText.decode(name);var p=ProgramService.store(system.getLevel()).named(current().libraryId(),decoded);var compiled=ProgramStore.compile(p,runtime.instrumented());runtime.library(p.get("id").getAsString(),decoded,compiled);return LuaText.encode(compiled.lua());}catch(Exception e){throw new LuaException(LuaText.error(e.getMessage()));}}
        @LuaFunction(mainThread=true) public Map<Integer,byte[]> getLibraryPrograms()throws LuaException{try{var result=new LinkedHashMap<Integer,byte[]>();for(var p:ProgramService.store(system.getLevel()).list(current().libraryId()))result.put(result.size()+1,LuaText.encode(p.getAsJsonObject().get("name").getAsString()));return result;}catch(Exception e){throw new LuaException(LuaText.error(e.getMessage()));}}
        @LuaFunction(mainThread=true) public boolean areTurtlesStopped(){return TeachingSettings.get(system.getLevel().getServer()).stopped();}
    }
    public static final class TurtleEdu implements ILuaAPI {
        private final IComputerSystem system;
        private final dan200.computercraft.api.turtle.ITurtleAccess access;
        public TurtleEdu(IComputerSystem system,JuniorTurtleEntity t){this.system=system;access=t.getAccess();}
        public String[] getNames(){return new String[]{"turtleedu"};}
        private String inspect(net.minecraft.core.Direction direction){var pos=system.getPosition().relative(direction);if(!system.getLevel().hasChunkAt(pos))return "minecraft:air";return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(system.getLevel().getBlockState(pos).getBlock()).toString();}
        @LuaFunction(mainThread=true) public String inspect(){return inspect(access.getDirection());}
        @LuaFunction(mainThread=true) public String inspectUp(){return inspect(net.minecraft.core.Direction.UP);}
        @LuaFunction(mainThread=true) public String inspectDown(){return inspect(net.minecraft.core.Direction.DOWN);}
        @LuaFunction(mainThread=true) public String getItemName(Optional<Integer> number)throws LuaException{int slot=number.orElse(access.getSelectedSlot()+1)-1;if(slot<0||slot>=16)throw new LuaException("Slot must be 1 to 16");var item=access.getInventory().getItem(slot);return item.isEmpty()?"minecraft:air":net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString();}
        @LuaFunction(mainThread=true) public void say(IArguments args)throws LuaException{
            String text=LuaText.decode(args.getStringCoerced(0));
            if(text.length()>256)throw new LuaException("Message exceeds 256 characters");
            var label=system.getLabel();var message=net.minecraft.network.chat.Component.literal("<"+(label==null?"Junior Turtle":label)+"> "+text);
            for(var player:system.getLevel().players())if(player.position().distanceToSqr(system.getPosition().getCenter())<=64*64)player.sendSystemMessage(message);
        }
    }
}
