package org.singularbots.compat.cct;

import dan200.computercraft.api.turtle.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import java.util.*;

/** Optimistic undo journal. Each cell/item must still match the last observed command result. */
public final class Restoration {
    private record Change(CompoundTag original,CompoundTag expected){}
    private final BlockPos position;
    private final Direction direction;
    private final int slot;
    private final CompoundTag inventory;
    private CompoundTag expectedInventory;
    private BlockPos expectedPosition;
    private final Map<BlockPos,Change> blocks=new LinkedHashMap<>();
    private final Map<UUID,Change> drops=new LinkedHashMap<>();
    private final Set<BlockPos> touched=new HashSet<>();
    private boolean active=true;
    private String diagnostic="";
    private Restoration(ITurtleAccess access){position=access.getPosition().immutable();expectedPosition=position;direction=access.getDirection();slot=access.getSelectedSlot();inventory=inventory(access);expectedInventory=inventory.copy();checkBudget();}
    private Restoration(CompoundTag tag){position=pos(tag.getCompound("position"));expectedPosition=pos(tag.getCompound("expectedPosition"));direction=Direction.from3DDataValue(tag.getInt("direction"));slot=tag.getInt("slot");inventory=tag.getCompound("inventory");expectedInventory=tag.getCompound("expectedInventory");active=tag.getBoolean("active");diagnostic=tag.getString("diagnostic");for(var e:tag.getList("blocks",Tag.TAG_COMPOUND)){var row=(CompoundTag)e;blocks.put(pos(row.getCompound("pos")),new Change(row.getCompound("original"),row.getCompound("expected")));}for(var e:tag.getList("drops",Tag.TAG_COMPOUND)){var row=(CompoundTag)e;drops.put(row.getUUID("id"),new Change(row.getCompound("original"),row.getCompound("expected")));}}
    public static Restoration load(CompoundTag tag){var snapshot=new Restoration(tag);snapshot.checkBudget();return snapshot;}
    public static TurtleCommandResult save(ITurtleAccess access){var t=owner(access);if(t==null)return TurtleCommandResult.failure("Junior turtle required");t.snapshot(new Restoration(access));return TurtleCommandResult.success();}
    public static JuniorTurtleEntity owner(ITurtleAccess access){return access instanceof dan200.computercraft.shared.turtle.core.TurtleBrain brain&&brain.getOwner() instanceof JuniorTurtleEntity t?t:null;}
    public void invalidate(String reason){active=false;diagnostic=reason;}
    public static void irreversible(String reason){var access=TeachingPermissions.CURRENT.get();var t=access==null?null:owner(access);if(t!=null&&t.snapshot()!=null)t.snapshot().invalidate(reason);}
    private static CompoundTag inventory(ITurtleAccess access){var tag=new CompoundTag();for(int i=0;i<16;i++){var stack=access.getInventory().getItem(i);if(!stack.isEmpty())tag.put(Integer.toString(i),stack.save(access.getLevel().registryAccess()));}return tag;}
    private static CompoundTag cell(ITurtleAccess access,BlockPos pos){var tag=new CompoundTag();var level=access.getLevel();var state=level.getBlockState(pos);var entity=level.getBlockEntity(pos);if(entity instanceof JuniorTurtleEntity t&&t.identity().equals(owner(access).identity()))state=Blocks.AIR.defaultBlockState();else if(entity!=null)tag.put("entity",entity.saveWithFullMetadata(level.registryAccess()));tag.put("state",NbtUtils.writeBlockState(state));return tag;}
    public static void beforeBlock(net.minecraft.world.level.Level level,BlockPos pos){var access=TeachingPermissions.CURRENT.get();var t=access==null?null:owner(access);if(t==null||access.getLevel()!=level||t.snapshot()==null||!t.snapshot().active)return;var s=t.snapshot();if(s.touched.add(pos.immutable())){var before=cell(access,pos);var old=s.blocks.get(pos);if(old==null)s.addBlock(pos,new Change(before,before));else if(!old.expected.equals(before))s.invalidate("Restoration conflict: another actor changed a recorded block");}}
    public static void afterBlock(net.minecraft.world.level.Level level,BlockPos pos){var access=TeachingPermissions.CURRENT.get();var t=access==null?null:owner(access);if(t==null||access.getLevel()!=level||t.snapshot()==null||!t.snapshot().active)return;var s=t.snapshot();var change=s.blocks.get(pos);if(change!=null)s.blocks.put(pos.immutable(),new Change(change.original,cell(access,pos)));}
    private void addBlock(BlockPos pos,Change change){if(blocks.size()>=512){invalidate("Snapshot change limit exceeded; restoration is unavailable");return;}blocks.put(pos.immutable(),change);checkBudget();}
    private void checkBudget(){
        long size=(long)inventory.sizeInBytes()+expectedInventory.sizeInBytes();
        for(var change:blocks.values())size+=(long)change.original.sizeInBytes()+change.expected.sizeInBytes();
        for(var change:drops.values())size+=(long)change.original.sizeInBytes()+change.expected.sizeInBytes();
        if(size>1024*1024||blocks.size()>512||drops.size()>256){invalidate("Snapshot exceeds safe restoration limits");blocks.clear();drops.clear();touched.clear();}
    }
    private static CompoundTag drop(ItemEntity item,HolderLookup.Provider lookup){var tag=new CompoundTag();tag.put("stack",item.getItem().save(lookup));tag.putDouble("x",item.getX());tag.putDouble("y",item.getY());tag.putDouble("z",item.getZ());return tag;}
    private static boolean sameDrop(CompoundTag expected,ItemEntity actual,HolderLookup.Provider lookup){return expected.isEmpty()?actual==null:actual!=null&&!actual.isRemoved()&&expected.get("stack").equals(actual.getItem().save(lookup));}
    public record Capture(Restoration snapshot,ITurtleAccess access,AABB bounds,Map<BlockPos,CompoundTag> tiles,Map<UUID,CompoundTag> items){
        public void finish(){
            if(!snapshot.active)return;var level=(ServerLevel)access.getLevel();
            for(var pos:snapshot.touched){var old=snapshot.blocks.get(pos);if(old!=null)snapshot.blocks.put(pos,new Change(old.original,cell(access,pos)));}
            for(var entry:tiles.entrySet()){var after=cell(access,entry.getKey());if(!entry.getValue().equals(after)){var old=snapshot.blocks.get(entry.getKey());if(old==null)snapshot.addBlock(entry.getKey(),new Change(entry.getValue(),after));else if(!old.expected.equals(entry.getValue())&&!snapshot.touched.contains(entry.getKey()))snapshot.invalidate("Restoration conflict: a container changed between commands");else snapshot.blocks.put(entry.getKey(),new Change(old.original,after));}}
            var after=new LinkedHashMap<UUID,CompoundTag>();for(var item:level.getEntitiesOfClass(ItemEntity.class,bounds))after.put(item.getUUID(),drop(item,level.registryAccess()));
            var keys=new HashSet<>(items.keySet());keys.addAll(after.keySet());
            for(var id:keys){var before=items.getOrDefault(id,new CompoundTag());var expected=after.getOrDefault(id,new CompoundTag());
                if(Objects.equals(before.get("stack"),expected.get("stack")))continue;
                var old=snapshot.drops.get(id);if(old!=null&&!Objects.equals(old.expected.get("stack"),before.get("stack"))){snapshot.invalidate("Restoration conflict: an item changed between commands");continue;}snapshot.drops.put(id,new Change(old==null?before:old.original,expected));
            }
            if(snapshot.drops.size()>256)snapshot.invalidate("Too many item entities to restore safely");
            snapshot.expectedInventory=inventory(access);snapshot.expectedPosition=access.getPosition().immutable();snapshot.checkBudget();var t=owner(access);if(t!=null)t.setChanged();
        }
    }
    public static Capture capture(ITurtleAccess access){
        var t=owner(access);if(t==null||t.snapshot()==null||!t.snapshot().active)return null;var s=t.snapshot();if(!s.expectedInventory.equals(inventory(access))){s.invalidate("Restoration conflict: inventory changed outside turtle commands");return null;}s.touched.clear();var level=(ServerLevel)access.getLevel();var center=access.getPosition();
        var tiles=new LinkedHashMap<BlockPos,CompoundTag>();for(var pos:BlockPos.betweenClosed(center.offset(-2,-2,-2),center.offset(2,2,2)))if(level.hasChunkAt(pos)&&level.getBlockEntity(pos)!=null&&!(level.getBlockEntity(pos) instanceof JuniorTurtleEntity))tiles.put(pos.immutable(),cell(access,pos));
        var bounds=new AABB(center).inflate(4);var items=new LinkedHashMap<UUID,CompoundTag>();for(var item:level.getEntitiesOfClass(ItemEntity.class,bounds))items.put(item.getUUID(),drop(item,level.registryAccess()));if(items.size()>256){s.invalidate("Too many nearby items to restore safely");return null;}
        return new Capture(s,access,bounds,tiles,items);
    }
    public TurtleCommandResult restore(ITurtleAccess access){
        if(!active)return TurtleCommandResult.failure(diagnostic.isEmpty()?"Snapshot is no longer active":diagnostic);
        var level=(ServerLevel)access.getLevel();
        if(!expectedPosition.equals(access.getPosition())||!expectedInventory.equals(inventory(access)))return TurtleCommandResult.failure("Restoration conflict: turtle position or inventory changed");
        if(!TeachingPermissions.enterable(level,position))return TurtleCommandResult.failure("Restoration target is unloaded or protected");
        for(var entry:blocks.entrySet()){
            if(!level.hasChunkAt(entry.getKey())||!entry.getValue().expected.equals(cell(access,entry.getKey())))return TurtleCommandResult.failure("Restoration conflict at "+entry.getKey().toShortString());
            if(!entry.getValue().original.equals(entry.getValue().expected)&&!TeachingPermissions.editable(level,entry.getKey()))return TurtleCommandResult.failure("Teacher permission denied restoration at "+entry.getKey().toShortString());
        }
        var lookup=level.registryAccess();for(var entry:drops.entrySet()){var entity=level.getEntity(entry.getKey());if(entity!=null&&!(entity instanceof ItemEntity)||!sameDrop(entry.getValue().expected,(ItemEntity)entity,lookup))return TurtleCommandResult.failure("Restoration conflict: a dropped item was moved, collected or changed");}
        // Do not overwrite an occupied original position, including another turtle.
        if(!position.equals(access.getPosition())&&!level.getBlockState(position).isAir())return TurtleCommandResult.failure("Restoration target is occupied");
        active=false;
        if(!access.teleportTo(level,position)){active=true;return TurtleCommandResult.failure("Cannot return to snapshot position");}
        for(var entry:blocks.entrySet()){
            if(entry.getKey().equals(position))continue;
            var original=entry.getValue().original;var state=NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),original.getCompound("state"));level.setBlock(entry.getKey(),state,3);
            var entity=level.getBlockEntity(entry.getKey());if(entity!=null&&original.contains("entity")){entity.loadWithComponents(original.getCompound("entity"),lookup);entity.setChanged();}
        }
        for(var entry:drops.entrySet()){
            var entity=level.getEntity(entry.getKey());if(entity!=null)entity.discard();var original=entry.getValue().original;
            if(!original.isEmpty()){var item=new ItemEntity(level,original.getDouble("x"),original.getDouble("y"),original.getDouble("z"),ItemStack.parseOptional(lookup,original.getCompound("stack")));item.setUUID(entry.getKey());item.setDefaultPickUpDelay();level.addFreshEntity(item);}
        }
        for(int i=0;i<16;i++)access.getInventory().setItem(i,ItemStack.parseOptional(lookup,inventory.getCompound(Integer.toString(i))));access.getInventory().setChanged();access.setDirection(direction);access.setSelectedSlot(slot);
        diagnostic="Snapshot restored; save another snapshot before continuing";var turtle=owner(access);if(turtle!=null)turtle.changed();return TurtleCommandResult.success();
    }
    public CompoundTag save(){var tag=new CompoundTag();tag.put("position",pos(position));tag.put("expectedPosition",pos(expectedPosition));tag.putInt("direction",direction.get3DDataValue());tag.putInt("slot",slot);tag.put("inventory",inventory);tag.put("expectedInventory",expectedInventory);tag.putBoolean("active",active);tag.putString("diagnostic",diagnostic);
        var cells=new ListTag();for(var entry:blocks.entrySet()){var row=new CompoundTag();row.put("pos",pos(entry.getKey()));row.put("original",entry.getValue().original);row.put("expected",entry.getValue().expected);cells.add(row);}tag.put("blocks",cells);
        var entities=new ListTag();for(var entry:drops.entrySet()){var row=new CompoundTag();row.putUUID("id",entry.getKey());row.put("original",entry.getValue().original);row.put("expected",entry.getValue().expected);entities.add(row);}tag.put("drops",entities);return tag;
    }
    private static CompoundTag pos(BlockPos pos){var j=new CompoundTag();j.putInt("x",pos.getX());j.putInt("y",pos.getY());j.putInt("z",pos.getZ());return j;}
    private static BlockPos pos(CompoundTag tag){return new BlockPos(tag.getInt("x"),tag.getInt("y"),tag.getInt("z"));}
}
