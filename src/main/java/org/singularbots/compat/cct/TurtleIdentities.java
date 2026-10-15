package org.singularbots.compat.cct;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persist reservations across chunk unloads so creative copies cannot claim another library. */
final class TurtleIdentities extends SavedData {
    private final Map<UUID,GlobalPos> positions=new HashMap<>();
    static TurtleIdentities get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(TurtleIdentities::new,TurtleIdentities::load),"singularbots_identities");}
    static TurtleIdentities load(CompoundTag tag,HolderLookup.Provider lookup){
        var result=new TurtleIdentities();
        for(var value:tag.getList("turtles",10)){var entry=(CompoundTag)value;if(entry.hasUUID("id"))GlobalPos.CODEC.parse(NbtOps.INSTANCE,entry.get("position")).result().ifPresent(pos->result.positions.put(entry.getUUID("id"),pos));}
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        var entries=new ListTag();positions.forEach((id,pos)->{var entry=new CompoundTag();entry.putUUID("id",id);entry.put("position",GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE,pos).getOrThrow());entries.add(entry);});tag.put("turtles",entries);return tag;
    }
    private static GlobalPos position(JuniorTurtleEntity turtle){return GlobalPos.of(turtle.getLevel().dimension(),turtle.getBlockPos());}
    boolean claim(JuniorTurtleEntity turtle){
        var current=position(turtle);var reserved=positions.get(turtle.identity());
        if(reserved!=null&&!reserved.equals(current))return false;
        if(reserved==null){positions.put(turtle.identity(),current);setDirty();}return true;
    }
    void move(JuniorTurtleEntity previous,JuniorTurtleEntity next){
        // Only the real CC brain transfer may move a reservation; copied NBT cannot.
        if(previous.getAccess()==next.getAccess()&&position(previous).equals(positions.get(next.identity()))){positions.put(next.identity(),position(next));setDirty();}
    }
    void release(JuniorTurtleEntity turtle){if(positions.remove(turtle.identity(),position(turtle)))setDirty();}
}
