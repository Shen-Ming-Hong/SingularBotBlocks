package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.UUID;

@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class StudentRobotGameTests {
    private static ServerPlayer player(GameTestHelper h){var player=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(player.connection.getConnection());return player;}
    private static JuniorTurtleEntity robot(GameTestHelper h,BlockPos pos,ServerPlayer player){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(player.getGameProfile());t.placedBy(player,true);
        var center=t.getBlockPos().getCenter();player.teleportTo(center.x,center.y,center.z);RemoteService.track(t);return t;
    }
    private static JsonObject request(String op,UUID id){var j=new JsonObject();j.addProperty("op",op);if(id!=null)j.addProperty("id",id.toString());return j;}
    private static JsonObject save(ServerPlayer player,JuniorTurtleEntity turtle,UUID id,long revision)throws Exception{
        var q=request("programSave",id);q.addProperty("revision",revision);q.add("program",JsonParser.parseString("{\"name\":\"Private program\",\"mode\":\"lua\",\"source\":\"turtle.up()\"}"));return ProgramService.handle(player,turtle,q).getAsJsonObject("program");
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void privateWorkspaceSurvivesDestructionAndAnotherStudentsPickup(GameTestHelper h)throws Exception{
        TeachingGameTests.advancedClass(h);var owner=player(h);var other=player(h);var a=robot(h,new BlockPos(2,2,2),owner);var b=robot(h,new BlockPos(5,2,5),owner);var id=UUID.randomUUID();
        RemoteService.open(owner,a);save(owner,a,id,0);RemoteService.open(owner,b);
        var loaded=ProgramService.handle(owner,b,request("programLoad",id)).getAsJsonObject("program");h.assertTrue(loaded.get("source").getAsString().equals("turtle.up()"),"Own second robot cannot read shared library");save(owner,b,id,1);
        RemoteService.open(owner,a);boolean conflict=false;try{save(owner,a,id,1);}catch(IllegalStateException expected){conflict=true;}h.assertTrue(conflict,"Shared library accepted a stale revision");
        other.teleportTo(a.getBlockPos().getX()+.5,a.getBlockPos().getY(),a.getBlockPos().getZ()+.5);RemoteService.open(other,a);
        h.assertFalse(RemoteService.session(other,a.identity()),"Other student opened robot");
        boolean denied=false;try{ProgramService.handle(other,a,request("programInspect",id));}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"Other student read private code");
        denied=false;try{ProgramService.handle(other,a,request("programHistory",null));}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"Other student read private history");
        h.assertTrue(a.peripheral()==null,"Wired computer can remotely start another student's robot");
        a.getAccess().getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));a.selectProgram(id);a.createServerComputer();
        int previousComputer=a.getComputerID();var components=net.minecraft.core.component.DataComponentMap.builder();a.collectSafeComponents(components);var dropped=new ItemStack(TeachingContent.JUNIOR_ITEM.get());dropped.applyComponents(components.build());
        other.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);h.assertTrue(other.gameMode.destroyBlock(a.getBlockPos()),"Other student could not physically break robot");
        var box=new net.minecraft.world.phys.AABB(a.getBlockPos()).inflate(1);int diamonds=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();h.assertTrue(diamonds==3,"Breaking did not drop inventory exactly once");
        var fresh=robot(h,new BlockPos(2,2,5),owner);RemoteService.open(owner,fresh);h.assertTrue(ProgramService.handle(owner,fresh,request("programLoad",id)).getAsJsonObject("program").get("revision").getAsLong()==2,"Destroyed robot lost shared program");
        var stolen=robot(h,new BlockPos(5,2,2),other);stolen.applyComponentsFromItemStack(dropped);((dan200.computercraft.shared.turtle.core.TurtleBrain)stolen.getAccess()).setOwningPlayer(other.getGameProfile());stolen.placedBy(other,false);RemoteService.track(stolen);
        h.assertTrue(stolen.getComputerID()!=previousComputer&&stolen.selectedProgram()==null,"Picked-up robot retained prior disk or selected code");RemoteService.open(other,stolen);
        h.assertTrue(ProgramService.handle(other,stolen,request("programList",null)).getAsJsonArray("programs").isEmpty(),"Pickup exposed previous owner's library");
        h.assertTrue(StudentPrograms.history(h.getLevel()).list(owner.getUUID()).size()>=4,"Student history was lost with robot");
        for(var p:java.util.List.of(owner,other))h.getLevel().getServer().getPlayerList().remove(p);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void newRobotsTakeTheOwnersLastNameAndLookButDroppedRobotsKeepTheirs(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(2,2,2),owner);RemoteService.open(owner,a);
        RemoteService.handle(owner,new org.singularbots.network.RemoteNetwork.Action(a.identity(),"name",0,"小明一號"));
        RemoteService.handle(owner,new org.singularbots.network.RemoteNetwork.Action(a.identity(),"colour",14,""));
        RemoteService.handle(owner,new org.singularbots.network.RemoteNetwork.Action(a.identity(),"decoration",7,""));
        int red=org.singularbots.turtle.Appearance.colour(14);String crown=org.singularbots.turtle.Appearance.decoration(7);
        h.assertTrue("小明一號".equals(a.getLabel())&&a.getAccess().getColour()==red&&a.decoration().equals(crown),"Controller did not change the robot's look");
        var b=robot(h,new BlockPos(5,2,5),owner);
        h.assertTrue("小明一號".equals(b.getLabel())&&b.getAccess().getColour()==red&&b.decoration().equals(crown),"New robot did not take the owner's saved look");
        var dropped=robot(h,new BlockPos(2,2,5),owner);dropped.setLabel("舊機器人");dropped.decorate("none");
        var components=net.minecraft.core.component.DataComponentMap.builder();dropped.collectSafeComponents(components);var item=new ItemStack(TeachingContent.JUNIOR_ITEM.get());item.applyComponents(components.build());
        var replaced=robot(h,new BlockPos(5,2,2),owner);replaced.setLabel(null);replaced.decorate("none");replaced.applyComponentsFromItemStack(item);replaced.placedBy(owner,false);
        h.assertTrue("舊機器人".equals(replaced.getLabel())&&replaced.decoration().equals("none"),"Dropped robot lost its own look");
        h.getLevel().getServer().getPlayerList().remove(owner);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void brokenRobotsWithTheSameLookStackAndPlaceAsNewRobots(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(2,2,2),owner);RemoteService.open(owner,a);
        RemoteService.handle(owner,new org.singularbots.network.RemoteNetwork.Action(a.identity(),"name",0,"小明一號"));
        RemoteService.handle(owner,new org.singularbots.network.RemoteNetwork.Action(a.identity(),"colour",14,""));
        RemoteService.handle(owner,new org.singularbots.network.RemoteNetwork.Action(a.identity(),"decoration",7,""));
        int red=org.singularbots.turtle.Appearance.colour(14);String crown=org.singularbots.turtle.Appearance.decoration(7);
        var b=robot(h,new BlockPos(5,2,5),owner);var c=robot(h,new BlockPos(2,2,5),owner);
        // One robot has run code (a computer disk and selected program); the other never started.
        b.selectProgram(UUID.randomUUID());b.createServerComputer();h.assertTrue(b.getComputerID()>=0,"Robot computer did not start");
        var used=new java.util.HashSet<>(java.util.Set.of(b.identity(),c.identity()));
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);var items=new java.util.ArrayList<ItemStack>();
        for(var t:java.util.List.of(b,c)){
            var pos=t.getBlockPos();owner.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);h.assertTrue(owner.gameMode.destroyBlock(pos),"Owner could not break robot");
            for(var e:h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(1)))
                if(e.getItem().is(TeachingContent.JUNIOR_ITEM.get())){items.add(e.getItem().copy());e.discard();}
        }
        h.assertTrue(items.size()==2,"Breaking did not drop exactly one robot each: "+items.size());
        h.assertTrue(ItemStack.isSameItemSameComponents(items.get(0),items.get(1)),"Broken robots with the same look do not stack: "+items.get(0).getComponentsPatch()+" / "+items.get(1).getComponentsPatch());
        var inventory=new net.minecraft.world.SimpleContainer(1);inventory.setItem(0,items.get(0));var rest=inventory.addItem(items.get(1));
        h.assertTrue(rest.isEmpty()&&inventory.getItem(0).getCount()==2,"Broken robots did not merge into one stack");
        var stack=inventory.getItem(0);
        for(var pos:java.util.List.of(new BlockPos(5,2,2),new BlockPos(7,2,7))){
            var placed=robot(h,pos,owner);placed.setLabel(null);placed.getAccess().setColour(-1);placed.decorate("none");
            placed.applyComponentsFromItemStack(stack.split(1));placed.placedBy(owner,false);
            h.assertTrue("小明一號".equals(placed.getLabel())&&placed.getAccess().getColour()==red&&placed.decoration().equals(crown),"Robot from a merged stack lost its look");
            h.assertTrue(used.add(placed.identity())&&placed.getComputerID()==-1&&placed.selectedProgram()==null,"Robot from a merged stack shared another robot's identity, disk or program");
        }
        h.getLevel().getServer().getPlayerList().remove(owner);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=180)
    public static void builtInMinerDigsObsidianLogsAndRefusesBedrockAndToolRemoval(GameTestHelper h){
        var owner=player(h);var t=robot(h,new BlockPos(3,3,3),owner);var pos=t.getBlockPos();var result=new dan200.computercraft.api.turtle.TurtleCommandResult[1];
        h.assertFalse(t.getAccess().isFuelNeeded(),"Robot requires fuel");h.assertTrue(t.getAccess().getUpgrade(dan200.computercraft.api.turtle.TurtleSide.LEFT)!=null,"Built-in pickaxe missing");
        h.getLevel().setBlockAndUpdate(pos.below(),Blocks.OBSIDIAN.defaultBlockState());
        result[0]=null;t.getAccess().executeCommand(access->{result[0]=dan200.computercraft.shared.turtle.core.TurtleToolCommand.dig(dan200.computercraft.shared.turtle.core.InteractDirection.DOWN,null).execute(access);return result[0];});
        h.startSequence().thenWaitUntil(()->h.assertTrue(result[0]!=null,"Queued robot command has not finished")).thenExecute(()->{h.assertTrue(result[0]!=null&&result[0].isSuccess()&&h.getLevel().isEmptyBlock(pos.below()),"Obsidian did not mine in one action");h.assertTrue(t.getAccess().getInventory().hasAnyOf(java.util.Set.of(Items.OBSIDIAN)),"Mined obsidian was not collected");h.getLevel().setBlockAndUpdate(pos.above(),Blocks.OAK_LOG.defaultBlockState());result[0]=null;t.getAccess().executeCommand(access->{result[0]=dan200.computercraft.shared.turtle.core.TurtleToolCommand.dig(dan200.computercraft.shared.turtle.core.InteractDirection.UP,null).execute(access);return result[0];});})
            .thenWaitUntil(()->h.assertTrue(result[0]!=null,"Queued robot command has not finished")).thenExecute(()->{h.assertTrue(result[0].isSuccess()&&t.getAccess().getInventory().hasAnyOf(java.util.Set.of(Items.OAK_LOG)),"Pickaxe did not collect a non-pickaxe block");h.getLevel().setBlockAndUpdate(pos.below(),Blocks.BEDROCK.defaultBlockState());result[0]=null;t.getAccess().executeCommand(access->{result[0]=dan200.computercraft.shared.turtle.core.TurtleToolCommand.dig(dan200.computercraft.shared.turtle.core.InteractDirection.DOWN,null).execute(access);return result[0];});})
            .thenWaitUntil(()->h.assertTrue(result[0]!=null,"Queued robot command has not finished")).thenExecute(()->{h.assertFalse(result[0].isSuccess(),"Bedrock was mined");h.assertTrue(h.getLevel().getBlockState(pos.below()).is(Blocks.BEDROCK),"Bedrock changed");t.getAccess().setSelectedSlot(15);result[0]=null;t.getAccess().executeCommand(access->{result[0]=new dan200.computercraft.shared.turtle.core.TurtleEquipCommand(dan200.computercraft.api.turtle.TurtleSide.LEFT).execute(access);return result[0];});})
            .thenWaitUntil(()->h.assertTrue(result[0]!=null,"Queued robot command has not finished")).thenExecute(()->{h.assertFalse(result[0].isSuccess(),"Built-in diamond pickaxe could be removed");h.assertTrue(t.getAccess().getInventory().getItem(15).isEmpty(),"Removing built-in tool created a free diamond pickaxe");h.getLevel().getServer().getPlayerList().remove(owner);}).thenSucceed();
    }
}
