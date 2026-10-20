package org.singularbots.compat.cct;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persist reservations across chunk unloads so creative copies cannot claim another library. Each reservation also
 * remembers whose robot it is, its number among that student's robots and its chosen program, so "My robots" can list
 * and recall robots whose chunks are not loaded.
 */
final class TurtleIdentities extends SavedData {
    static final class Entry {
        GlobalPos position;UUID owner;int number;UUID program;
        Entry(GlobalPos position){this.position=position;}
    }
    record Robot(UUID id,GlobalPos position,UUID owner,int number,UUID program){}
    private final Map<UUID,Entry> entries=new HashMap<>();
    static TurtleIdentities get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(TurtleIdentities::new,TurtleIdentities::load),"singularbots_identities");}
    static TurtleIdentities load(CompoundTag tag,HolderLookup.Provider lookup){
        var result=new TurtleIdentities();
        for(var value:tag.getList("turtles",10)){var entry=(CompoundTag)value;if(entry.hasUUID("id"))GlobalPos.CODEC.parse(NbtOps.INSTANCE,entry.get("position")).result().ifPresent(pos->{
            var e=new Entry(pos);if(entry.hasUUID("owner"))e.owner=entry.getUUID("owner");e.number=entry.getInt("number");if(entry.hasUUID("program"))e.program=entry.getUUID("program");result.entries.put(entry.getUUID("id"),e);});}
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        var list=new ListTag();entries.forEach((id,e)->{var entry=new CompoundTag();entry.putUUID("id",id);entry.put("position",GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE,e.position).getOrThrow());
            if(e.owner!=null)entry.putUUID("owner",e.owner);if(e.number>0)entry.putInt("number",e.number);if(e.program!=null)entry.putUUID("program",e.program);list.add(entry);});
        tag.put("turtles",list);return tag;
    }
    private static GlobalPos position(JuniorTurtleEntity turtle){return GlobalPos.of(turtle.getLevel().dimension(),turtle.getBlockPos());}
    boolean claim(JuniorTurtleEntity turtle){
        var current=position(turtle);var reserved=entries.get(turtle.identity());
        if(reserved!=null&&!reserved.position.equals(current))return false;
        if(reserved==null){entries.put(turtle.identity(),new Entry(current));setDirty();}return true;
    }
    void move(JuniorTurtleEntity previous,JuniorTurtleEntity next){
        // Only the real CC brain transfer may move a reservation; copied NBT cannot.
        var entry=entries.get(next.identity());
        if(previous.getAccess()==next.getAccess()&&entry!=null&&position(previous).equals(entry.position)){entry.position=position(next);setDirty();}
    }
    void release(JuniorTurtleEntity turtle){var entry=entries.get(turtle.identity());if(entry!=null&&entry.position.equals(position(turtle))){entries.remove(turtle.identity());setDirty();}}
    void forget(UUID id){if(entries.remove(id)!=null)setDirty();}
    /** Keeps the owner, number and chosen program of a claimed robot current. A student's new robot takes their smallest free number. */
    void describe(JuniorTurtleEntity turtle,UUID owner){
        var entry=entries.get(turtle.identity());if(entry==null||owner==null||!entry.position.equals(position(turtle)))return;
        boolean changed=false;
        if(!owner.equals(entry.owner)){entry.owner=owner;entry.number=0;changed=true;}
        if(entry.number<=0){entry.number=freeNumber(owner,turtle.identity());changed=true;}
        if(!java.util.Objects.equals(entry.program,turtle.selectedProgram())){entry.program=turtle.selectedProgram();changed=true;}
        if(changed)setDirty();
    }
    private int freeNumber(UUID owner,UUID except){
        var used=new HashSet<Integer>();entries.forEach((id,e)->{if(!id.equals(except)&&owner.equals(e.owner))used.add(e.number);});
        int n=1;while(used.contains(n))n++;return n;
    }
    int number(UUID id){var entry=entries.get(id);return entry==null?0:entry.number;}
    Robot robot(UUID id){var e=entries.get(id);return e==null?null:new Robot(id,e.position,e.owner,e.number,e.program);}
    List<Robot> owned(UUID owner){
        var result=new ArrayList<Robot>();entries.forEach((id,e)->{if(owner.equals(e.owner))result.add(new Robot(id,e.position,e.owner,e.number,e.program));});
        result.sort(Comparator.comparingInt(Robot::number));return result;
    }
}
