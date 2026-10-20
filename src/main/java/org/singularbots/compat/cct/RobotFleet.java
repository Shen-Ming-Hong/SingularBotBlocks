package org.singularbots.compat.cct;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dan200.computercraft.shared.turtle.core.TurtleBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.singularbots.network.RemoteNetwork;
import java.util.HashMap;
import java.util.UUID;

/**
 * The editor's "My robots" list: every robot a student placed, numbered, with where it is, its chosen program and state,
 * also while its chunk is not loaded. A robot can be pointed out to the student who asked, or recalled by its owner or
 * a teacher: an empty robot returns as an item, a robot carrying items moves beside them with its backpack and state.
 */
public final class RobotFleet {
    private RobotFleet(){}
    /** The robots of the library this editor works on: the student's own, or that student's for a teacher. */
    static JsonObject list(ServerPlayer player,JuniorTurtleEntity current)throws java.io.IOException{
        var server=player.getServer();var identities=TurtleIdentities.get(server);var owner=current.libraryId();
        var names=new HashMap<String,String>();
        for(var row:ProgramService.store(player.serverLevel()).list(owner)){var p=row.getAsJsonObject();names.put(p.get("id").getAsString(),p.get("name").getAsString());}
        var robots=new JsonArray();
        for(var robot:identities.owned(owner)){
            var level=server.getLevel(robot.position().dimension());if(level==null)continue;var pos=robot.position().pos();
            JuniorTurtleEntity turtle=null;
            if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null){
                // A loaded chunk shows the truth: a reservation with no robot behind it is left over and is dropped.
                if(level.getBlockEntity(pos) instanceof JuniorTurtleEntity t&&t.identity().equals(robot.id()))turtle=t;else{identities.forget(robot.id());continue;}
            }
            var row=new JsonObject();row.addProperty("id",robot.id().toString());row.addProperty("number",robot.number());
            row.addProperty("dimension",level.dimension().location().toString());row.addProperty("x",pos.getX());row.addProperty("y",pos.getY());row.addProperty("z",pos.getZ());
            row.addProperty("loaded",turtle!=null);row.addProperty("current",robot.id().equals(current.identity()));
            // An unloaded robot's computer is off, so it waits; its chosen program is remembered with its reservation.
            String status=turtle==null?"stopped":turtle.runtime().json().get("status").getAsString();
            row.addProperty("status",switch(status){case "running","paused" -> "running"; case "error" -> "error"; default -> "stopped";});
            var program=turtle==null?robot.program():turtle.selectedProgram();
            row.addProperty("program",program==null?"":names.getOrDefault(program.toString(),""));
            robots.add(row);
        }
        var result=new JsonObject();result.add("robots",robots);
        var here=new JsonObject();here.addProperty("dimension",player.level().dimension().location().toString());
        here.addProperty("x",player.getX());here.addProperty("y",player.getY());here.addProperty("z",player.getZ());result.add("player",here);
        return result;
    }
    private static TurtleIdentities.Robot robot(ServerPlayer player,JuniorTurtleEntity current,com.google.gson.JsonObject request){
        var robot=TurtleIdentities.get(player.getServer()).robot(UUID.fromString(request.get("robot").getAsString()));
        // Only the robots of the library this editor session may use, so a request cannot name another student's robot.
        if(robot==null||!current.libraryId().equals(robot.owner()))throw new IllegalArgumentException("This robot is no longer in your list");
        return robot;
    }
    /** Only the student who asked sees the robot outlined through walls and a column of light above it, for a few seconds. */
    static JsonObject locate(ServerPlayer player,JuniorTurtleEntity current,JsonObject request){
        var robot=robot(player,current,request);
        if(player.connection.hasChannel(RemoteNetwork.Locate.TYPE))PacketDistributor.sendToPlayer(player,new RemoteNetwork.Locate(robot.position().dimension().location(),robot.position().pos()));
        var result=new JsonObject();result.addProperty("sameDimension",robot.position().dimension().equals(player.level().dimension()));return result;
    }
    static JsonObject recall(ServerPlayer player,JuniorTurtleEntity current,JsonObject request){
        var identities=TurtleIdentities.get(player.getServer());var robot=robot(player,current,request);
        if(!player.getUUID().equals(robot.owner())&&!player.hasPermissions(2))throw new IllegalArgumentException("Only the robot's owner or a teacher can recall it");
        var level=player.getServer().getLevel(robot.position().dimension());var pos=robot.position().pos();
        if(level==null)throw new IllegalArgumentException("This robot is no longer in your list");
        // Loads the chunk of a far-away robot so it can be fetched.
        level.getChunk(pos.getX()>>4,pos.getZ()>>4);
        if(!(level.getBlockEntity(pos) instanceof JuniorTurtleEntity turtle)||!turtle.identity().equals(robot.id())){identities.forget(robot.id());throw new IllegalArgumentException("This robot is no longer in your list");}
        if(turtle.locked()&&!player.hasPermissions(2))throw new IllegalArgumentException("A teacher locked this robot");
        TeacherTakeover.control(player,turtle);
        boolean wasCurrent=turtle==current;
        // A running program, even an endless loop, stops first; the editor has already asked the student.
        ProgramService.stop(turtle);
        var result=new JsonObject();result.addProperty("current",wasCurrent);
        if(turtle.getAccess().getInventory().isEmpty()){
            var item=item(turtle);turtle.recalling=true;level.removeBlock(pos,false);
            // Checked first: a creative player's inventory would otherwise swallow an item that does not fit.
            var inventory=player.getInventory();boolean pocket=(inventory.getFreeSlot()>=0||inventory.getSlotWithRemainingSpace(item)>=0)&&inventory.add(item);
            if(!pocket){var drop=new ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),item);drop.setNoPickUpDelay();player.level().addFreshEntity(drop);}
            result.addProperty("mode","item");result.addProperty("pocket",pocket);return result;
        }
        var spot=freeSpot(player,turtle.getBlockState());
        if(spot==null)throw new IllegalArgumentException("No free space next to you; move to an open place, or walk to the robot and take out its items");
        if(!((TurtleBrain)turtle.getAccess()).teleportTo(player.serverLevel(),spot))throw new IllegalArgumentException("The robot could not move here; try an open place");
        if(player.serverLevel().getBlockEntity(spot) instanceof JuniorTurtleEntity moved)StudentPrograms.record(moved,"recalled","");
        result.addProperty("mode","moved");result.addProperty("x",spot.getX());result.addProperty("y",spot.getY());result.addProperty("z",spot.getZ());return result;
    }
    /** The same item breaking the robot drops: no look, identity or disk, only a teacher lock. */
    static ItemStack item(JuniorTurtleEntity turtle){
        var components=net.minecraft.core.component.DataComponentMap.builder();turtle.collectSafeComponents(components);
        var item=new ItemStack(TeachingContent.JUNIOR_ITEM.get());item.applyComponents(components.build());return item;
    }
    /** The open place nearest to just in front of the player that a robot fits, with ground below preferred; null when there is none. */
    static BlockPos freeSpot(ServerPlayer player,BlockState robot){
        var level=player.serverLevel();var base=player.blockPosition();var look=player.getLookAngle();
        double aimX=player.getX()+look.x*1.5,aimZ=player.getZ()+look.z*1.5;
        BlockPos best=null;double bestScore=Double.MAX_VALUE;
        for(int dy=-1;dy<=1;dy++)for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){
            var pos=base.offset(dx,dy,dz);
            if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos))continue;
            var here=level.getBlockState(pos);
            if(!here.canBeReplaced()||!here.getFluidState().isEmpty()||!level.isUnobstructed(robot,pos,CollisionContext.empty()))continue;
            var center=pos.getCenter();double score=(center.x-aimX)*(center.x-aimX)+(center.z-aimZ)*(center.z-aimZ)+Math.abs(dy)*2+(level.getBlockState(pos.below()).canBeReplaced()?6:0);
            if(score<bestScore){bestScore=score;best=pos;}
        }
        return best;
    }
}
