package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;

/** "My robots": numbering, the list, pointing a robot out and recalling it. */
@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class RobotFleetGameTests {
    private static ServerPlayer player(GameTestHelper h){var player=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(player.connection.getConnection());return player;}
    private static JuniorTurtleEntity robot(GameTestHelper h,BlockPos pos,ServerPlayer player){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(player.getGameProfile());t.placedBy(player);RemoteService.track(t);return t;
    }
    private static TurtleIdentities identities(GameTestHelper h){return TurtleIdentities.get(h.getLevel().getServer());}
    /** Opens the robot's controller the way a right click does, so editor requests have a session. */
    private static void open(ServerPlayer player,JuniorTurtleEntity t){var c=t.getBlockPos().getCenter();player.teleportTo(c.x,c.y+1,c.z);RemoteService.open(player,t);}
    private static void stand(ServerPlayer player,GameTestHelper h,BlockPos relative){var c=h.absolutePos(relative).getCenter();player.teleportTo(c.x,c.y-.5,c.z);}
    private static JsonObject request(ServerPlayer player,JuniorTurtleEntity t,String op,UUID robot)throws Exception{
        var j=new JsonObject();j.addProperty("op",op);if(robot!=null)j.addProperty("robot",robot.toString());return ProgramService.handle(player,t,j);
    }
    private static String error(ServerPlayer player,JuniorTurtleEntity t,String op,UUID robot){
        try{request(player,t,op,robot);return "";}catch(Exception e){return Objects.toString(e.getMessage(),"");}
    }
    private static void remove(GameTestHelper h,ServerPlayer... players){for(var p:players)h.getLevel().getServer().getPlayerList().remove(p);}

    @GameTest(template="empty",timeoutTicks=80)
    public static void eachStudentNumbersTheirRobotsAndReusesFreedNumbers(GameTestHelper h){
        var owner=player(h);var other=player(h);
        var a=robot(h,new BlockPos(1,2,1),owner);var b=robot(h,new BlockPos(3,2,1),owner);var c=robot(h,new BlockPos(5,2,1),owner);var theirs=robot(h,new BlockPos(1,2,4),other);
        h.runAfterDelay(3,()->{
            var ids=identities(h);
            h.assertTrue(ids.number(a.identity())==1&&ids.number(b.identity())==2&&ids.number(c.identity())==3,"Robots were not numbered 1, 2, 3 in placing order");
            h.assertTrue(ids.number(theirs.identity())==1,"Another student's first robot did not start at 1");
            owner.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            h.assertTrue(owner.gameMode.destroyBlock(b.getBlockPos()),"Could not break robot 2");
            var d=robot(h,new BlockPos(3,2,4),owner);
            h.runAfterDelay(3,()->{
                h.assertTrue(identities(h).number(d.identity())==2,"A new robot did not take the freed number 2 but "+identities(h).number(d.identity()));
                remove(h,owner,other);h.succeed();
            });
        });
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void theListShowsOnlyTheStudentsOwnRobotsWithProgramAndState(GameTestHelper h){
        TeachingGameTests.advancedClass(h);var owner=player(h);var other=player(h);
        var a=robot(h,new BlockPos(1,2,1),owner);var b=robot(h,new BlockPos(5,2,1),owner);robot(h,new BlockPos(1,2,5),other);
        h.runAfterDelay(3,()->{try{
            open(owner,a);var id=UUID.randomUUID();
            var save=new JsonObject();save.addProperty("op","programSave");save.addProperty("id",id.toString());save.addProperty("revision",0);
            save.add("program",JsonParser.parseString("{\"name\":\"挖隧道\",\"mode\":\"lua\",\"source\":\"turtle.turnLeft()\"}"));ProgramService.handle(owner,a,save);
            var list=request(owner,a,"programRobots",null);var robots=list.getAsJsonArray("robots");
            h.assertTrue(robots.size()==2,"The list did not hold exactly the student's two robots: "+robots);
            var first=robots.get(0).getAsJsonObject();var second=robots.get(1).getAsJsonObject();
            h.assertTrue(first.get("number").getAsInt()==1&&first.get("current").getAsBoolean()&&first.get("program").getAsString().equals("挖隧道")&&first.get("status").getAsString().equals("stopped"),"Robot 1 row is wrong: "+first);
            h.assertTrue(second.get("number").getAsInt()==2&&!second.get("current").getAsBoolean()&&second.get("program").getAsString().isEmpty()&&second.get("x").getAsInt()==b.getBlockPos().getX(),"Robot 2 row is wrong: "+second);
            h.assertTrue(list.getAsJsonObject("player").has("x"),"The list has no player position for directions");
            // A reservation with no robot behind it in a loaded chunk is dropped from the list.
            identities(h).forget(b.identity());h.assertTrue(request(owner,a,"programRobots",null).getAsJsonArray("robots").size()==1,"A forgotten robot is still listed");
            h.assertTrue(request(owner,a,"programLocate",a.identity()).get("sameDimension").getAsBoolean(),"Locating the student's own robot failed");
            remove(h,owner,other);h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void anEmptyRobotIsRecalledAsAnItemIntoTheInventoryOrAtTheFeet(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(1,2,1),owner);var b=robot(h,new BlockPos(5,2,5),owner);
        h.runAfterDelay(3,()->{try{
            open(owner,a);stand(owner,h,new BlockPos(1,2,4));
            var result=request(owner,a,"programRecall",b.identity());
            h.assertTrue(result.get("mode").getAsString().equals("item")&&result.get("pocket").getAsBoolean(),"An empty robot was not recalled into the inventory: "+result);
            h.assertTrue(h.getLevel().getBlockState(b.getBlockPos()).isAir(),"The recalled robot is still standing");
            h.assertTrue(owner.getInventory().countItem(TeachingContent.JUNIOR_ITEM.get())==1,"The robot item is not in the inventory");
            h.assertTrue(identities(h).robot(b.identity())==null,"The recalled robot still holds its number");
            h.assertTrue(StudentPrograms.history(h.getLevel()).list(owner.getUUID()).asList().stream().anyMatch(e->e.getAsJsonObject().get("kind").getAsString().equals("recalled")),"The recall was not recorded");
            // A full inventory leaves the robot at the student's feet.
            var c=robot(h,new BlockPos(5,2,1),owner);
            for(int i=0;i<owner.getInventory().getContainerSize();i++)owner.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            open(owner,a);stand(owner,h,new BlockPos(1,2,4));result=request(owner,a,"programRecall",c.identity());
            h.assertFalse(result.get("pocket").getAsBoolean(),"A full inventory took the robot");
            var dropped=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,owner.getBoundingBox().inflate(1)).stream().filter(e->e.getItem().is(TeachingContent.JUNIOR_ITEM.get())).toList();
            h.assertTrue(dropped.size()==1&&ItemStack.isSameItemSameComponents(dropped.get(0).getItem(),JuniorMining.item()),"The robot was not left at the student's feet as a plain robot item");
            remove(h,owner);h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }

    @GameTest(template="empty",timeoutTicks=200)
    public static void aRobotCarryingItemsMovesBesideTheStudentAndARunningLoopStops(GameTestHelper h){
        TeachingGameTests.advancedClass(h);var owner=player(h);var a=robot(h,new BlockPos(1,2,1),owner);var b=robot(h,new BlockPos(6,2,6),owner);
        b.getAccess().getInventory().setItem(3,new ItemStack(Items.DIAMOND,5));
        var program=UUID.randomUUID();
        h.runAfterDelay(3,()->{try{
            open(owner,b);
            var save=new JsonObject();save.addProperty("op","programSave");save.addProperty("id",program.toString());save.addProperty("revision",0);
            save.add("program",JsonParser.parseString("{\"name\":\"loop\",\"mode\":\"lua\",\"source\":\"while true do turtle.turnLeft() end\"}"));ProgramService.handle(owner,b,save);
            var run=new JsonObject();run.addProperty("op","programRun");run.addProperty("id",program.toString());ProgramService.handle(owner,b,run);
        }catch(Exception e){throw new RuntimeException(e);}});
        h.runAfterDelay(40,()->{try{
            var running=RemoteService.resolve(h.getLevel(),b.identity());
            h.assertTrue(running!=null&&running.runtime().json().get("status").getAsString().equals("running"),"The endless loop is not running");
            open(owner,a);stand(owner,h,new BlockPos(1,2,4));
            var row=request(owner,a,"programRobots",null).getAsJsonArray("robots").asList().stream().map(JsonElement::getAsJsonObject).filter(r->r.get("id").getAsString().equals(b.identity().toString())).findFirst().orElseThrow();
            h.assertTrue(row.get("status").getAsString().equals("running"),"The list does not show the running robot as running: "+row);
            var result=request(owner,a,"programRecall",b.identity());
            h.assertTrue(result.get("mode").getAsString().equals("moved"),"A robot with items was not moved: "+result);
            var moved=RemoteService.resolve(h.getLevel(),b.identity());
            var spot=new BlockPos(result.get("x").getAsInt(),result.get("y").getAsInt(),result.get("z").getAsInt());
            h.assertTrue(moved!=null&&moved.getBlockPos().equals(spot)&&spot.distManhattan(owner.blockPosition())<=6,"The robot is not beside the student");
            h.assertTrue(moved.getAccess().getInventory().getItem(3).getCount()==5&&program.equals(moved.selectedProgram()),"The backpack or chosen program did not move with the robot");
            h.assertTrue(moved.runtime().json().get("status").getAsString().equals("stopped"),"The endless loop kept running after the recall");
            h.assertTrue(identities(h).number(b.identity())==2,"The moved robot lost its number");
            remove(h,owner);h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}});
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void onlyTheOwnerOrATeacherRecallsAndALockedRobotNeedsATeacher(GameTestHelper h){
        var owner=player(h);var other=player(h);var a=robot(h,new BlockPos(1,2,1),owner);var b=robot(h,new BlockPos(5,2,5),owner);var theirs=robot(h,new BlockPos(1,2,5),other);
        h.runAfterDelay(3,()->{
            open(other,theirs);
            h.assertTrue(error(other,theirs,"programRecall",b.identity()).equals("This robot is no longer in your list"),"A classmate could recall someone else's robot");
            h.assertTrue(error(other,theirs,"programLocate",b.identity()).equals("This robot is no longer in your list"),"A classmate could locate someone else's robot");
            h.assertTrue(h.getLevel().getBlockEntity(b.getBlockPos())==b,"The robot moved after a refused recall");
            b.lock(true);open(owner,a);stand(owner,h,new BlockPos(1,2,3));
            h.assertTrue(error(owner,a,"programRecall",b.identity()).equals("A teacher locked this robot"),"A student recalled a locked robot");
            h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(other.getGameProfile(),2,false));
            try{
                open(other,a);stand(other,h,new BlockPos(3,2,3));
                h.assertTrue(error(other,a,"programRecall",b.identity()).isEmpty(),"A teacher could not recall a student's locked robot");
                h.assertTrue(other.getInventory().countItem(TeachingContent.JUNIOR_ITEM.get())==1,"The teacher did not get the recalled robot");
            }finally{h.getLevel().getServer().getPlayerList().deop(other.getGameProfile());}
            remove(h,owner,other);h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=80)
    public static void withNoRoomAroundTheStudentARobotWithItemsStaysPut(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(5,2,1),owner);var b=robot(h,new BlockPos(10,2,10),owner);
        b.getAccess().getInventory().setItem(0,new ItemStack(Items.DIRT,1));
        var feet=h.absolutePos(new BlockPos(5,2,5));
        h.runAfterDelay(3,()->{
            open(owner,a);
            // Fill everything around the student's two blocks.
            for(int dx=-3;dx<=3;dx++)for(int dy=-1;dy<=2;dy++)for(int dz=-3;dz<=3;dz++){var pos=feet.offset(dx,dy,dz);if((dx!=0||dz!=0||dy<0||dy>1)&&!pos.equals(a.getBlockPos()))h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());}
            for(int dy=0;dy<=1;dy++)h.getLevel().setBlockAndUpdate(feet.above(dy),Blocks.AIR.defaultBlockState());
            var c=feet.getCenter();owner.teleportTo(c.x,c.y-.5,c.z);
            h.assertTrue(error(owner,a,"programRecall",b.identity()).startsWith("No free space next to you"),"No free space was not reported");
            h.assertTrue(h.getLevel().getBlockEntity(b.getBlockPos())==b,"The robot moved without a free space");
            remove(h,owner);h.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=1200)
    public static void aFarRobotInAnUnloadedChunkIsListedAndRecalled(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(1,2,1),owner);var level=h.getLevel();
        var far=a.getBlockPos().offset(4000,0,4000);level.getChunk(far.getX()>>4,far.getZ()>>4);
        level.setBlockAndUpdate(far,TeachingContent.JUNIOR.get().defaultBlockState());var b=(JuniorTurtleEntity)level.getBlockEntity(far);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)b.getAccess()).setOwningPlayer(owner.getGameProfile());b.placedBy(owner);
        var id=b.identity();
        h.succeedWhen(()->{
            h.assertTrue(level.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"The far chunk is still loaded");
            try{
                open(owner,a);stand(owner,h,new BlockPos(1,2,4));
                var row=request(owner,a,"programRobots",null).getAsJsonArray("robots").asList().stream().map(JsonElement::getAsJsonObject).filter(r->r.get("id").getAsString().equals(id.toString())).findFirst().orElse(null);
                if(row==null)throw new IllegalStateException("The far robot is missing from the list");
                if(row.get("loaded").getAsBoolean()||row.get("number").getAsInt()!=2||row.get("x").getAsInt()!=far.getX())throw new IllegalStateException("The far robot row is wrong: "+row);
                var result=request(owner,a,"programRecall",id);
                if(!result.get("mode").getAsString().equals("item")||owner.getInventory().countItem(TeachingContent.JUNIOR_ITEM.get())!=1)throw new IllegalStateException("The far robot was not recalled: "+result);
                if(!level.getBlockState(far).isAir())throw new IllegalStateException("The far robot is still standing");
                remove(h,owner);
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
}
