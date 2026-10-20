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
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(player.getGameProfile());t.placedBy(player);
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
        var stolen=robot(h,new BlockPos(5,2,2),other);stolen.applyComponentsFromItemStack(dropped);((dan200.computercraft.shared.turtle.core.TurtleBrain)stolen.getAccess()).setOwningPlayer(other.getGameProfile());stolen.placedBy(other);RemoteService.track(stolen);
        h.assertTrue(stolen.getComputerID()!=previousComputer&&stolen.selectedProgram()==null,"Picked-up robot retained prior disk or selected code");RemoteService.open(other,stolen);
        h.assertTrue(ProgramService.handle(other,stolen,request("programList",null)).getAsJsonArray("programs").isEmpty(),"Pickup exposed previous owner's library");
        h.assertTrue(StudentPrograms.history(h.getLevel()).list(owner.getUUID()).size()>=4,"Student history was lost with robot");
        for(var p:java.util.List.of(owner,other))h.getLevel().getServer().getPlayerList().remove(p);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void takeAllMovesTheBackpackIntoThePlayerStackingFirstAndKeepsWhatDoesNotFit(GameTestHelper h){
        var owner=player(h);var other=player(h);var a=robot(h,new BlockPos(2,2,2),owner);var robot=a.getAccess().getInventory();var inv=owner.getInventory();
        for(int i=0;i<36;i++)inv.setItem(i,new ItemStack(Items.COBBLESTONE,64));
        inv.setItem(0,new ItemStack(Items.DIRT,60));inv.setItem(1,ItemStack.EMPTY);inv.setItem(2,new ItemStack(Items.DIAMOND,60));
        robot.setItem(0,new ItemStack(Items.DIRT,40));robot.setItem(1,new ItemStack(Items.DIRT,40));robot.setItem(2,new ItemStack(Items.DIAMOND,5));robot.setItem(3,new ItemStack(Items.STONE,64));
        other.teleportTo(owner.getX(),owner.getY(),owner.getZ());
        h.assertFalse(new RemoteMenu(99,other.getInventory(),a.identity()).clickMenuButton(other,RemoteMenu.TAKE_ALL),"Another student emptied a robot they may not control");
        h.assertTrue(robot.getItem(0).getCount()==40,"Denied take-all still moved items");
        RemoteService.open(owner,a);h.assertTrue(owner.containerMenu instanceof RemoteMenu,"Owner could not open the robot");
        h.assertTrue(owner.containerMenu.clickMenuButton(owner,RemoteMenu.TAKE_ALL),"Take-all was refused for the owner");
        h.assertTrue(robot.getItem(0).isEmpty()&&robot.getItem(1).is(Items.DIRT)&&robot.getItem(1).getCount()==12,"Dirt did not fill the player's stacks before staying in the robot");
        h.assertTrue(robot.getItem(2).getCount()==1&&robot.getItem(3).getCount()==64,"Items that did not fit left the robot");
        h.assertTrue(inv.countItem(Items.DIRT)==128&&inv.countItem(Items.DIAMOND)==64&&inv.countItem(Items.STONE)==0,"Player did not receive exactly what fit");
        // Shift-clicking one slot still moves just that slot, both ways.
        inv.setItem(1,ItemStack.EMPTY);owner.containerMenu.clicked(3,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,owner);
        h.assertTrue(robot.getItem(3).isEmpty()&&inv.countItem(Items.STONE)==64&&robot.getItem(1).getCount()==12,"Shift-click on one robot slot did not move only that slot");
        int stoneSlot=16+(inv.findSlotMatchingItem(new ItemStack(Items.STONE))<9?27+inv.findSlotMatchingItem(new ItemStack(Items.STONE)):inv.findSlotMatchingItem(new ItemStack(Items.STONE))-9);
        owner.containerMenu.clicked(stoneSlot,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,owner);
        h.assertTrue(robot.getItem(0).is(Items.STONE)&&robot.getItem(0).getCount()==64&&inv.countItem(Items.STONE)==0,"Shift-click from the player did not return the stack to the robot");
        for(var p:java.util.List.of(owner,other))h.getLevel().getServer().getPlayerList().remove(p);h.succeed();
    }
    private static ItemStack drop(JuniorTurtleEntity t){
        var components=net.minecraft.core.component.DataComponentMap.builder();t.collectSafeComponents(components);var item=new ItemStack(TeachingContent.JUNIOR_ITEM.get());item.applyComponents(components.build());return item;
    }
    private static JuniorTurtleEntity place(GameTestHelper h,BlockPos pos,ServerPlayer player,ItemStack item){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);t.applyComponentsFromItemStack(item);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(player.getGameProfile());t.placedBy(player);RemoteService.track(t);return t;
    }
    private static void look(ServerPlayer player,JuniorTurtleEntity t,String name,int colour,int decoration){
        var center=t.getBlockPos().getCenter();player.teleportTo(center.x,center.y,center.z);RemoteService.open(player,t);
        RemoteService.handle(player,new org.singularbots.network.RemoteNetwork.Action(t.identity(),"name",0,name));
        RemoteService.handle(player,new org.singularbots.network.RemoteNetwork.Action(t.identity(),"colour",colour,""));
        RemoteService.handle(player,new org.singularbots.network.RemoteNetwork.Action(t.identity(),"decoration",decoration,""));
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void robotItemsCarryNoLookAndEveryPlacerGivesTheirOwn(GameTestHelper h){
        var owner=player(h);var other=player(h);var a=robot(h,new BlockPos(2,2,2),owner);look(owner,a,"小明一號",14,7);
        int red=org.singularbots.turtle.Appearance.colour(14),blue=org.singularbots.turtle.Appearance.colour(11);String crown=org.singularbots.turtle.Appearance.decoration(7),hat=org.singularbots.turtle.Appearance.decoration(14);
        h.assertTrue("小明一號".equals(a.getLabel())&&a.getAccess().getColour()==red&&a.decoration().equals(crown),"Controller did not change the robot's look");
        var b=robot(h,new BlockPos(5,2,5),owner);
        h.assertTrue("小明一號".equals(b.getLabel())&&b.getAccess().getColour()==red&&b.decoration().equals(crown),"New robot did not take the owner's saved look");
        // A robot changed on its own still drops a plain item that stacks with a new one from the creative tab.
        b.setLabel("舊機器人");b.decorate("none");var item=drop(b);
        h.assertTrue(ItemStack.isSameItemSameComponents(item,JuniorMining.item()),"Dropped robot item is not the same as a new one: "+item.getComponentsPatch());
        // Lent to a classmate: they place it with their own look and their own (empty) library.
        var c=robot(h,new BlockPos(2,2,5),other);look(other,c,"小華",11,14);
        var lent=place(h,new BlockPos(5,2,2),other,item.copy());
        h.assertTrue("小華".equals(lent.getLabel())&&lent.getAccess().getColour()==blue&&lent.decoration().equals(hat),"Borrowed robot did not take the placer's look: "+lent.getLabel());
        h.assertTrue(lent.libraryId().equals(other.getUUID()),"Borrowed robot is not the placer's");
        // Given back, it is the owner's robot with the owner's look again.
        var back=place(h,new BlockPos(7,2,7),owner,drop(lent));
        h.assertTrue("小明一號".equals(back.getLabel())&&back.getAccess().getColour()==red&&back.decoration().equals(crown)&&back.libraryId().equals(owner.getUUID()),"Returned robot did not take its owner's look back");
        // An anvil name gives way to a saved name, and stays only for a placer without one.
        var named=JuniorMining.item();named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("鐵砧名"));
        var stranger=player(h);var anvil=place(h,new BlockPos(1,2,7),owner,named.copy());var plain=place(h,new BlockPos(7,2,1),stranger,named.copy());
        h.assertTrue("小明一號".equals(anvil.getLabel()),"An anvil name beat the placer's saved name");
        h.assertTrue("鐵砧名".equals(plain.getLabel())&&plain.getAccess().getColour()==-1&&plain.decoration().equals("none"),"A placer without a saved look did not get a plain robot with the item's name");
        for(var p:java.util.List.of(owner,other,stranger))h.getLevel().getServer().getPlayerList().remove(p);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void aFramedRobotShowsItsFramersLookUntilTakenOut(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(2,2,2),owner);look(owner,a,"小明一號",14,7);int red=org.singularbots.turtle.Appearance.colour(14);
        var frame=new net.minecraft.world.entity.decoration.ItemFrame(h.getLevel(),h.absolutePos(new BlockPos(4,2,4)),net.minecraft.core.Direction.UP);h.getLevel().addFreshEntity(frame);
        owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,JuniorMining.item());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(owner,net.minecraft.world.InteractionHand.MAIN_HAND,frame));
        frame.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.startSequence().thenIdle(3).thenExecute(()->{
            var shown=frame.getItem();
            h.assertTrue(FramedRobots.shown(shown),"Framed robot is not marked");h.assertTrue((net.minecraft.world.item.component.DyedItemColor.getOrDefault(shown,-1)&0xffffff)==red,"Framed robot colour "+net.minecraft.world.item.component.DyedItemColor.getOrDefault(shown,-1)+" is not "+red);h.assertTrue(shown.get(dan200.computercraft.shared.ModRegistry.DataComponents.OVERLAY.get())!=null,"Framed robot does not wear its framer's look: "+shown.getComponentsPatch());
            owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);frame.hurt(h.getLevel().damageSources().playerAttack(owner),1);
        }).thenIdle(2).thenExecute(()->{
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,frame.getBoundingBox().inflate(2)).stream().filter(e->e.getItem().is(TeachingContent.JUNIOR_ITEM.get())).toList();
            h.assertTrue(drops.size()==1&&ItemStack.isSameItemSameComponents(drops.get(0).getItem(),JuniorMining.item()),"Robot taken out of a frame still carries a look: "+drops.stream().map(e->e.getItem().getComponentsPatch().toString()).toList());
            h.getLevel().getServer().getPlayerList().remove(owner);
        }).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=80,batch="noBlockDrops")
    public static void aBrokenRobotDropsAsAnItemEvenWithBlockDropsOff(GameTestHelper h){
        var rules=h.getLevel().getGameRules();rules.getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS).set(false,h.getLevel().getServer());
        try{
            var owner=player(h);var other=player(h);var a=robot(h,new BlockPos(2,2,2),owner);a.getAccess().getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
            var pos=a.getBlockPos();other.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);other.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            h.assertTrue(other.gameMode.destroyBlock(pos),"Another student could not break the robot");
            var items=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2));
            int robots=items.stream().filter(e->e.getItem().is(TeachingContent.JUNIOR_ITEM.get())).mapToInt(e->e.getItem().getCount()).sum();
            int diamonds=items.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(robots==1&&diamonds==3,"With block drops off, breaking dropped "+robots+" robots and "+diamonds+" diamonds");
            var robot=items.stream().filter(e->e.getItem().is(TeachingContent.JUNIOR_ITEM.get())).findFirst().orElseThrow().getItem();
            h.assertTrue(ItemStack.isSameItemSameComponents(robot,JuniorMining.item()),"The dropped robot is not a plain ownerless robot item: "+robot.getComponentsPatch());
            for(var p:java.util.List.of(owner,other))h.getLevel().getServer().getPlayerList().remove(p);
        }finally{rules.getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS).set(true,h.getLevel().getServer());}
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void givenCreativeMinedAndOldRobotItemsAllStack(GameTestHelper h){
        var given=new ItemStack(TeachingContent.JUNIOR_ITEM.get());
        var tab=TeachingContent.TEACHING_TAB.get();tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(),true,h.getLevel().registryAccess()));
        var creative=tab.getDisplayItems().stream().filter(i->i.is(TeachingContent.JUNIOR_ITEM.get())).findFirst().orElseThrow();
        var owner=player(h);var a=robot(h,new BlockPos(2,2,2),owner);var pos=a.getBlockPos();
        h.assertTrue(JuniorMining.builtIn(a.getAccess().getUpgradeWithData(dan200.computercraft.api.turtle.TurtleSide.LEFT)),"A placed robot has no built-in pickaxe");
        var pickaxe=a.getAccess().getUpgradeWithData(dan200.computercraft.api.turtle.TurtleSide.LEFT);
        owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);h.assertTrue(owner.gameMode.destroyBlock(pos),"Could not break the robot");
        var mined=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2)).stream()
            .filter(e->e.getItem().is(TeachingContent.JUNIOR_ITEM.get())).findFirst().orElseThrow().getItem();
        // An item an older version saved with the pickaxe component loads without it.
        var old=new ItemStack(TeachingContent.JUNIOR_ITEM.get());old.set(dan200.computercraft.shared.ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get(),pickaxe);
        var ops=h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        var loaded=ItemStack.CODEC.parse(ops,ItemStack.CODEC.encodeStart(ops,old).getOrThrow()).getOrThrow();
        for(var item:java.util.List.of(creative,mined,loaded))h.assertTrue(ItemStack.isSameItemSameComponents(given,item),"A robot item does not stack with a /give one: "+item.getComponentsPatch());
        var placed=place(h,new BlockPos(5,2,5),owner,given.copy());
        h.assertTrue(JuniorMining.builtIn(placed.getAccess().getUpgradeWithData(dan200.computercraft.api.turtle.TurtleSide.LEFT)),"A placed /give robot has no built-in pickaxe");
        h.getLevel().getServer().getPlayerList().remove(owner);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void aTeacherLockSurvivesBreakingTheRobot(GameTestHelper h){
        var owner=player(h);var a=robot(h,new BlockPos(2,2,2),owner);a.lock(true);var item=drop(a);
        h.assertFalse(ItemStack.isSameItemSameComponents(item,JuniorMining.item()),"A locked robot's item lost its lock");
        h.assertTrue(place(h,new BlockPos(5,2,5),owner,item).locked(),"Breaking and placing lifted the teacher lock");
        h.assertFalse(place(h,new BlockPos(2,2,5),owner,drop(robot(h,new BlockPos(5,2,2),owner))).locked(),"An unlocked robot came back locked");
        h.getLevel().getServer().getPlayerList().remove(owner);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void brokenRobotsStackAndPlaceAsNewRobots(GameTestHelper h){
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
        h.assertTrue(ItemStack.isSameItemSameComponents(items.get(0),items.get(1)),"Broken robots do not stack: "+items.get(0).getComponentsPatch()+" / "+items.get(1).getComponentsPatch());
        var inventory=new net.minecraft.world.SimpleContainer(1);inventory.setItem(0,items.get(0));var rest=inventory.addItem(items.get(1));
        h.assertTrue(rest.isEmpty()&&inventory.getItem(0).getCount()==2,"Broken robots did not merge into one stack");
        var stack=inventory.getItem(0);
        for(var pos:java.util.List.of(new BlockPos(5,2,2),new BlockPos(7,2,7))){
            var placed=robot(h,pos,owner);placed.setLabel(null);placed.getAccess().setColour(-1);placed.decorate("none");
            placed.applyComponentsFromItemStack(stack.split(1));placed.placedBy(owner);
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
