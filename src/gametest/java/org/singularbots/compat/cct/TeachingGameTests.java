package org.singularbots.compat.cct;

import com.mojang.authlib.GameProfile;
import dan200.computercraft.shared.ModRegistry;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.singularbots.network.RemoteNetwork;
import java.util.*;

@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class TeachingGameTests {
    /** New worlds are beginner classes; tests of Lua or teacher delivery opt into the advanced settings. */
    static void advancedClass(GameTestHelper h){var server=h.getLevel().getServer();var settings=TeachingSettings.get(server);for(var key:List.of("lua","sharing"))settings.set(server,key,true);}
    private static JuniorTurtleEntity place(GameTestHelper h,BlockPos pos){
        h.setBlock(pos,TeachingContent.JUNIOR.get());return (JuniorTurtleEntity)h.getBlockEntity(pos);
    }
    @GameTest(template="empty")
    public static void itemAppearanceSurvivesNetworkComponents(GameTestHelper h){
        var t=place(h,new BlockPos(2,2,2));
        for(var decoration:org.singularbots.turtle.Appearance.DECORATIONS){
            t.decorate(decoration);
            for(int colour:org.singularbots.turtle.Appearance.COLOURS){
                t.getAccess().setColour(colour);
                var components=net.minecraft.core.component.DataComponentMap.builder();t.collectSafeComponents(components);
                var item=new ItemStack(TeachingContent.JUNIOR_ITEM.get());item.applyComponents(components.build());
                var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
                try{
                    ItemStack.STREAM_CODEC.encode(buffer,item);var received=ItemStack.STREAM_CODEC.decode(buffer);
                    var dye=received.get(DataComponents.DYED_COLOR);
                    h.assertTrue(dye!=null&&dye.rgb()==colour,"Item RGB component lost after network encoding");
                    var overlay=dan200.computercraft.shared.turtle.items.TurtleItem.getOverlay(received);
                    h.assertTrue(decoration.equals("none")?overlay==null:overlay!=null&&overlay.model().getPath().equals("block/turtle_junior_overlay_"+decoration),"Item renderer cannot resolve decoration "+decoration);
                }finally{buffer.release();}
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void identityReservationSurvivesUnloadAndReleasesOnBreaking(GameTestHelper h){
        var original=place(h,new BlockPos(2,2,2));var id=original.identity();original.selectProgram(UUID.randomUUID());RemoteService.track(original);
        var saved=original.saveWithFullMetadata(h.getLevel().registryAccess());
        var identities=TurtleIdentities.get(h.getLevel().getServer());var reloaded=TurtleIdentities.load(identities.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        // setRemoved while the same block remains is the chunk-unload lifecycle.
        original.setRemoved();var clone=place(h,new BlockPos(5,2,5));clone.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertFalse(reloaded.claim(clone),"Reservation was lost on SavedData reload");RemoteService.track(clone);
        h.assertFalse(clone.identity().equals(id),"Copy of unloaded turtle aliases its library");h.assertTrue(clone.selectedProgram()==null,"Clone retained the original selected program");
        original.clearRemoved();RemoteService.track(original);h.assertTrue(original.identity().equals(id),"Chunk reload changed the original identity");
        h.setBlock(new BlockPos(2,2,2),net.minecraft.world.level.block.Blocks.AIR);
        var replaced=place(h,new BlockPos(2,2,4));replaced.loadWithComponents(saved,h.getLevel().registryAccess());RemoteService.track(replaced);
        h.assertTrue(replaced.identity().equals(id),"Breaking and replacing lost the original library identity");h.assertTrue(replaced.selectedProgram()!=null,"Replacing lost the selected program");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void movementPreservesIdentityAppearanceAndInventory(GameTestHelper h){
        var t=place(h,new BlockPos(2,2,2));var id=t.identity();
        var owner=new GameProfile(UUID.randomUUID(),"teaching-owner");((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner);
        t.setLabel("教學海龜");t.getAccess().setColour(0xcc4c4c);t.decorate("crown");
        var item=new ItemStack(Items.DIAMOND,9);item.set(DataComponents.CUSTOM_NAME,Component.literal("kept metadata"));
        t.getAccess().getInventory().setItem(7,item);t.getAccess().setSelectedSlot(7);
        h.assertFalse(t.getAccess().isFuelNeeded(),"Junior turtle must be fuel free");
        var destination=t.getBlockPos().above();
        t.getAccess().executeCommand(access->RemoteService.command("up").execute(access));
        h.succeedWhen(()->{
            h.assertTrue(h.getLevel().getBlockEntity(destination) instanceof JuniorTurtleEntity,"Queued move has not completed");
            var moved=(JuniorTurtleEntity)h.getLevel().getBlockEntity(destination);
            moved.lock(true);
            h.assertTrue(moved.identity().equals(id),"Movement changed identity");
            assertPersisted(h,moved,id,owner.getId());
            var saved=moved.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored=(JuniorTurtleEntity)BlockEntity.loadStatic(destination,moved.getBlockState(),saved,h.getLevel().registryAccess());
            assertPersisted(h,restored,id,owner.getId());
        });
    }
    private static void assertPersisted(GameTestHelper h,JuniorTurtleEntity t,UUID id,UUID owner){
        h.assertTrue(t.identity().equals(id)&&t.locked(),"Identity or lock lost");
        h.assertTrue("crown".equals(t.decoration())&&t.getOverlay()!=null,"Decoration lost");
        h.assertTrue(t.getColour()==0xcc4c4c,"Colour lost: "+t.getColour());
        h.assertTrue("教學海龜".equals(t.getLabel()),"Name lost: "+t.getLabel());
        h.assertTrue(t.getAccess().getOwningPlayer().getId().equals(owner),"Owner lost");
        h.assertTrue(t.getAccess().getSelectedSlot()==7,"Selected slot lost");
        var stack=t.getAccess().getInventory().getItem(7);
        h.assertTrue(stack.is(Items.DIAMOND)&&stack.getCount()==9&&stack.getHoverName().getString().equals("kept metadata"),"Inventory metadata lost");
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void remoteChecksSessionOwnerAndTeacherLock(GameTestHelper h){
        var owner=h.makeMockServerPlayerInLevel();var other=h.makeMockServerPlayerInLevel();var teacher=h.makeMockServerPlayerInLevel();
        for(var player:List.of(owner,other,teacher)) NetworkRegistry.configureMockConnection(player.connection.getConnection());
        var t=place(h,new BlockPos(3,2,3));var pos=t.getBlockPos().getCenter();
        owner.teleportTo(pos.x,pos.y,pos.z);other.teleportTo(pos.x,pos.y,pos.z);teacher.teleportTo(pos.x,pos.y,pos.z);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.track(t);
        h.assertTrue(RemoteService.authorized(owner,t),"Owner denied");
        h.assertFalse(RemoteService.authorized(other,t),"Other student allowed");
        RemoteService.open(owner,t);
        RemoteService.handle(other,new RemoteNetwork.Action(t.identity(),"name",0,"intruder"));
        h.assertFalse("intruder".equals(t.getLabel()),"Forged remote modified turtle");
        RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"name",0,"合法名稱"));
        h.assertTrue("合法名稱".equals(t.getLabel()),"Authorized naming failed");
        t.lock(true);
        h.assertFalse(RemoteService.authorized(owner,t),"Teacher lock did not revoke owner control");
        // GameTestServer's default operator level is zero: install the actual level-2 entry.
        h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(teacher.getGameProfile(),2,false));
        h.assertTrue(teacher.hasPermissions(2)&&RemoteService.authorized(teacher,t),"OP level 2 teacher denied");
        RemoteService.open(teacher,t);RemoteService.handle(teacher,new RemoteNetwork.Action(t.identity(),"lock",0,""));
        h.assertFalse(t.locked(),"Teacher unlock failed");
        h.getLevel().getServer().getPlayerList().deop(teacher.getGameProfile());
        for(var p:List.of(owner,other,teacher))h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }
    @GameTest(template="empty")
    public static void conversionKeepsDiskIdentityAndItemComponents(GameTestHelper h){
        var old=new ItemStack(ModRegistry.Items.TURTLE_ADVANCED.get());
        old.set(ModRegistry.DataComponents.COMPUTER_ID.get(),new dan200.computercraft.shared.util.NonNegativeId(42));
        old.set(DataComponents.CUSTOM_NAME,Component.literal("programmed turtle"));
        var recipe=new JuniorConversionRecipe(CraftingBookCategory.MISC);
        var input=CraftingInput.of(2,1,List.of(old,new ItemStack(Items.GREEN_DYE)));
        h.assertTrue(recipe.matches(input,h.getLevel()),"Conversion recipe rejected turtle");
        var converted=recipe.assemble(input,h.getLevel().registryAccess());
        h.assertTrue(converted.is(TeachingContent.JUNIOR_ITEM.get()),"Wrong converted item");
        h.assertTrue(converted.get(ModRegistry.DataComponents.COMPUTER_ID.get()).equals(old.get(ModRegistry.DataComponents.COMPUTER_ID.get())),"Program disk ID lost");
        h.assertTrue(converted.getHoverName().getString().equals("programmed turtle"),"Name lost on conversion");
        h.assertFalse(recipe.matches(CraftingInput.of(2,1,List.of(converted,new ItemStack(Items.GREEN_DYE))),h.getLevel()),"Repeated conversion should not duplicate identity");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=240)
    public static void compiledProgramRunsAndStepsOnRealTurtle(GameTestHelper h)throws Exception {
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());
        var t=place(h,new BlockPos(5,2,5));var pos=t.getBlockPos().getCenter();owner.teleportTo(pos.x,pos.y,pos.z);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var program=com.google.gson.JsonParser.parseString("""
            {"name":"step test","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_turtle_command","id":"first","fields":{"COMMAND":"up"},"next":{"block":{"type":"ccedu_turtle_command","id":"second","fields":{"COMMAND":"up"}}}}]}}}
            """).getAsJsonObject();
        var id=UUID.randomUUID();ProgramService.store(h.getLevel()).save(t.libraryId(),id,0,program);
        var request=new com.google.gson.JsonObject();request.addProperty("id",id.toString());request.addProperty("op","programStep");
        ProgramService.handle(owner,t,request);var first=t.getBlockPos().above();var second=first.above();
        h.startSequence().thenWaitUntil(()->{
            var text=new StringBuilder();var computer=t.getServerComputer();if(computer!=null){var terminal=computer.getTerminalState().create();for(int row=0;row<terminal.getHeight();row++)text.append(terminal.getLine(row)).append('|');}
            h.assertTrue(h.getLevel().getBlockEntity(first) instanceof JuniorTurtleEntity,"First step has not moved: "+t.runtime().json()+" pending="+t.runtime().pending()+" terminal="+text);
            var moved=(JuniorTurtleEntity)h.getLevel().getBlockEntity(first);
            h.assertTrue(moved.runtime().json().get("blockId").getAsString().equals("second"),"Did not pause at next stable block ID");
        }).thenExecute(()->{
            try{ProgramService.handle(owner,(JuniorTurtleEntity)h.getLevel().getBlockEntity(first),request);}catch(Exception e){throw new RuntimeException(e);}
        }).thenWaitUntil(()->{
            h.assertTrue(h.getLevel().getBlockEntity(second) instanceof JuniorTurtleEntity,"Second step has not moved");
            var moved=(JuniorTurtleEntity)h.getLevel().getBlockEntity(second);
            h.assertTrue(moved.runtime().json().get("status").getAsString().equals("stopped"),"Program did not finish");
        }).thenExecute(()->h.getLevel().getServer().getPlayerList().remove(owner)).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void blockStatementsKeepOneBeatAndWaitForTheRobotsMove(GameTestHelper h)throws Exception {
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());
        var t=place(h,new BlockPos(5,2,5));var origin=t.getBlockPos();var pos=origin.getCenter();owner.teleportTo(pos.x,pos.y,pos.z);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var program=com.google.gson.JsonParser.parseString("""
            {"name":"pace","mode":"blocks","workspace":{"variables":[{"id":"x","name":"x"}],"blocks":{"blocks":[{"type":"variables_set","id":"pace-set","fields":{"VAR":{"id":"x"}},"inputs":{"VALUE":{"block":{"type":"math_number","id":"pace-value","fields":{"NUM":1}}}},"next":{"block":{"type":"ccedu_turtle_command","id":"pace-up1","fields":{"COMMAND":"up"},"next":{"block":{"type":"ccedu_turtle_command","id":"pace-up2","fields":{"COMMAND":"up"}}}}}}]}}}
            """).getAsJsonObject();
        var id=UUID.randomUUID();ProgramService.store(h.getLevel()).save(t.libraryId(),id,0,program);operation(owner,t,id,"programRun");
        // Before pacing, an instant statement and the next move started in the same tick and the highlight ran one move ahead.
        var seen=new LinkedHashMap<String,Long>();var beats=new LinkedHashSet<Long>();var arrived=new long[]{-1};var stopped=new long[]{-1};
        h.succeedWhen(()->{
            long now=h.getLevel().getGameTime();var current=RemoteService.resolve(h.getLevel(),t.identity());
            if(current!=null){seen.putIfAbsent(current.runtime().json().get("blockId").getAsString(),now);if(current.runtime().lastBeat()>0)beats.add(current.runtime().lastBeat());if(arrived[0]<0&&current.getBlockPos().equals(origin.above()))arrived[0]=now;if(stopped[0]<0&&current.runtime().json().get("status").getAsString().equals("stopped"))stopped[0]=now;}
            h.assertTrue(current!=null&&current.getBlockPos().equals(origin.above(2))&&current.runtime().json().get("status").getAsString().equals("stopped"),"Program has not finished");
            var order=List.of("pace-set","pace-up1","pace-up2");h.assertTrue(seen.keySet().containsAll(order),"Missing highlighted statements: "+seen);
            // The test server ticks faster than real time, so Lua may notice a beat a few ticks late; check the beats the server
            // scheduled, and the observed highlights only for order and for waiting on the move.
            h.assertTrue(seen.get("pace-set")<seen.get("pace-up1")&&seen.get("pace-up1")<seen.get("pace-up2"),"Highlights out of order: "+seen);
            // One beat per statement plus the closing one that keeps the last statement highlighted while the robot moves.
            var scheduled=new ArrayList<>(beats);h.assertTrue(scheduled.size()==order.size()+1,"Every statement should take one beat: "+scheduled);
            for(int i=1;i<scheduled.size();i++)h.assertTrue(scheduled.get(i)-scheduled.get(i-1)>=org.singularbots.program.ProgramRuntime.BEAT_TICKS,"Statements were not held for a beat: "+scheduled);
            h.assertTrue(stopped[0]>=scheduled.get(scheduled.size()-1),"Program stopped before the last statement's beat: stopped="+stopped[0]+" beats="+scheduled);
            h.assertTrue(seen.get("pace-up2")-arrived[0]>=6,"Next move was highlighted before the robot finished moving: "+seen+" arrived="+arrived[0]);
            h.getLevel().getServer().getPlayerList().remove(owner);
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void stopCancelsAlreadyQueuedCommands(GameTestHelper h){
        var t=place(h,new BlockPos(2,2,2));var pos=t.getBlockPos();
        t.getAccess().executeCommand(RemoteService.command("up"));ProgramService.stop(t);
        h.runAfterDelay(30,()->{h.assertTrue(h.getLevel().getBlockEntity(pos)==t,"Stopped command moved turtle");h.succeed();});
    }
    @GameTest(template="empty",batch="teacher",timeoutTicks=160)
    public static void emergencyStopTerminatesNonYieldingLuaAndFutureCommands(GameTestHelper h){
        var t=place(h,new BlockPos(2,2,2));var pos=t.getBlockPos();RemoteService.track(t);
        t.runtime().prepare(new org.singularbots.program.WorkspaceCompiler.Compiled("while true do end",Map.of(),Set.of()),false);t.createServerComputer().turnOn();
        h.runAfterDelay(35,()->{
            TeachingSettings.get(h.getLevel().getServer()).set(h.getLevel().getServer(),"stopped",true);
            t.getAccess().executeCommand(RemoteService.command("up"));
            // Power-off is wall-clock work on the computer thread; after a lagging batch the server catches up ticks faster than real time.
            h.startSequence().thenIdle(20).thenWaitUntil(()->h.assertTrue(!t.getServerComputer().isOn(),"Emergency stop left non-yielding computer powered"))
                .thenExecute(()->{
                    h.assertTrue(h.getLevel().getBlockEntity(pos)==t,"Global stop allowed a future command");
                    TeachingSettings.get(h.getLevel().getServer()).set(h.getLevel().getServer(),"stopped",false);
                }).thenSucceed();
        });
    }
    @GameTest(template="empty",timeoutTicks=240)
    public static void restorationReturnsPoseBlocksInventoryAndUncollectedDrops(GameTestHelper h){
        var t=place(h,new BlockPos(5,2,5));var origin=t.getBlockPos();var target=origin.north();var id=t.identity();
        h.getLevel().setBlockAndUpdate(target.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        t.getAccess().getInventory().setItem(0,new ItemStack(Items.DIRT,2));Restoration.save(t.getAccess());
        t.getAccess().executeCommand(RemoteService.command("place"));
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockState(target).is(net.minecraft.world.level.block.Blocks.DIRT),"Place has not completed"))
            .thenExecute(()->t.getAccess().executeCommand(new dan200.computercraft.shared.turtle.core.TurtleDropCommand(dan200.computercraft.shared.turtle.core.InteractDirection.FORWARD,64)))
            .thenWaitUntil(()->h.assertTrue(t.getAccess().getInventory().isEmpty(),"Drop has not completed"))
            .thenExecute(()->t.getAccess().executeCommand(RemoteService.command("up")))
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above()) instanceof JuniorTurtleEntity,"Move has not completed"))
            .thenExecute(()->{var moved=(JuniorTurtleEntity)h.getLevel().getBlockEntity(origin.above());var saved=moved.saveWithFullMetadata(h.getLevel().registryAccess());moved.snapshot(Restoration.load(saved.getCompound("EduSnapshot")));moved.getAccess().executeCommand(RemoteService.command("restore"));})
            .thenWaitUntil(()->{
                h.assertTrue(h.getLevel().getBlockEntity(origin) instanceof JuniorTurtleEntity,"Restore has not returned to origin");var restored=(JuniorTurtleEntity)h.getLevel().getBlockEntity(origin);
                h.assertTrue(restored.identity().equals(id),"Restore changed identity");
                h.assertTrue(h.getLevel().getBlockState(target).isAir(),"Placed block was not restored");
                h.assertTrue(restored.getAccess().getInventory().getItem(0).getCount()==2,"Inventory was not restored");
                h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(origin).inflate(4)).isEmpty(),"Restore duplicated dropped items");
            }).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void restorationRejectsPlayerBlockConflictsWithoutOverwriting(GameTestHelper h){
        var t=place(h,new BlockPos(3,2,3));var target=t.getBlockPos().east();Restoration.save(t.getAccess());
        t.getAccess().executeCommand(access->{h.getLevel().setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());return dan200.computercraft.api.turtle.TurtleCommandResult.success();});
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockState(target).is(net.minecraft.world.level.block.Blocks.STONE),"Command has not completed"))
            .thenExecute(()->{
                h.getLevel().setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
                var result=t.snapshot().restore(t.getAccess());h.assertFalse(result.isSuccess(),"Player conflict was accepted");
                h.assertTrue(h.getLevel().getBlockState(target).is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK),"Player edit overwritten");
            }).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void verticalBoundaryAndAntiBorderApplyToQueuedMovement(GameTestHelper h){
        var t=place(h,new BlockPos(4,2,4));var origin=t.getBlockPos();var target=origin.north();
        h.getLevel().setBlockAndUpdate(target.above(2),TeachingContent.BORDER.get().defaultBlockState());
        t.getAccess().executeCommand(RemoteService.command("forward"));
        h.startSequence().thenIdle(20).thenExecute(()->{
            h.assertTrue(h.getLevel().getBlockEntity(origin)==t,"Boundary allowed movement");
            h.getLevel().setBlockAndUpdate(target.above(),TeachingContent.ANTI_BORDER.get().defaultBlockState());t.getAccess().executeCommand(RemoteService.command("forward"));
        }).thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(target) instanceof JuniorTurtleEntity,"Anti-border did not allow movement")).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=80)
    public static void studentsCannotShareAndOnlyTeachersDeliver(GameTestHelper h)throws Exception{
        advancedClass(h);
        var sender=h.makeMockServerPlayerInLevel();var receiver=h.makeMockServerPlayerInLevel();var teacher=h.makeMockServerPlayerInLevel();for(var p:List.of(sender,receiver,teacher))NetworkRegistry.configureMockConnection(p.connection.getConnection());
        var a=place(h,new BlockPos(2,2,2));var b=place(h,new BlockPos(5,2,5));
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)a.getAccess()).setOwningPlayer(sender.getGameProfile());((dan200.computercraft.shared.turtle.core.TurtleBrain)b.getAccess()).setOwningPlayer(receiver.getGameProfile());
        for(var p:List.of(sender,receiver,teacher)){var pos=a.getBlockPos().getCenter();p.teleportTo(pos.x,pos.y,pos.z);}RemoteService.track(a);RemoteService.track(b);RemoteService.open(sender,a);RemoteService.open(receiver,b);
        // A classmate's robot names its owner and stays closed.
        var denial=RemoteService.denial(receiver,a).getContents();h.assertTrue(!RemoteService.authorized(receiver,a)&&denial instanceof net.minecraft.network.chat.contents.TranslatableContents owned&&owned.getKey().equals("message.singularbots.owned")&&owned.getArgs()[0].equals(sender.getGameProfile().getName()),"Classmate denial did not name the owner: "+denial);
        var p=com.google.gson.JsonParser.parseString("""
            {"name":"共享程式","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_turtle_command","id":"shared-block","fields":{"COMMAND":"up"}}]}}}
            """).getAsJsonObject();var id=UUID.randomUUID();var store=ProgramService.store(h.getLevel());store.save(a.libraryId(),id,0,p);
        var request=new com.google.gson.JsonObject();request.addProperty("op","programList");h.assertFalse(ProgramService.handle(sender,a,request).get("sharing").getAsBoolean(),"A student was offered the send controls");
        request.addProperty("id",id.toString());request.addProperty("target",b.libraryId().toString());
        for(var op:List.of("programTargets","programShare")){request.addProperty("op",op);boolean rejected=false;try{ProgramService.handle(sender,a,request);}catch(IllegalArgumentException e){rejected=e.getMessage().equals(ProgramTransfer.STUDENT_SHARING);}h.assertTrue(rejected,"A student could use "+op);}
        for(var op:List.of("programInbox","programAccept","programExport","programDiskList","programImport")){request.addProperty("op",op);boolean rejected=false;try{ProgramService.handle(receiver,b,request);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"A retired copy route still works: "+op);}
        h.assertTrue(store.list(b.libraryId()).isEmpty(),"A student's program reached a classmate's library");
        h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(teacher.getGameProfile(),2,false));RemoteService.open(teacher,a);
        request.addProperty("op","programList");h.assertTrue(ProgramService.handle(teacher,a,request).get("sharing").getAsBoolean(),"The teacher lost the send controls");
        request.addProperty("op","programShare");ProgramService.handle(teacher,a,request);var delivered=store.list(b.libraryId());
        h.assertTrue(delivered.size()==1&&delivered.get(0).getAsJsonObject().get("name").getAsString().equals("Teacher "+teacher.getGameProfile().getName()+": 共享程式"),"Teacher delivery failed: "+delivered);
        h.getLevel().getServer().getPlayerList().deop(teacher.getGameProfile());for(var player:List.of(sender,receiver,teacher))h.getLevel().getServer().getPlayerList().remove(player);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void rawLuaStepsAtTurtleActions(GameTestHelper h)throws Exception{
        advancedClass(h);
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var pos=t.getBlockPos().getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var p=new com.google.gson.JsonObject();p.addProperty("name","raw-step");p.addProperty("mode","lua");p.addProperty("source","assert(turtle.up()); assert(turtle.up())");var id=UUID.randomUUID();ProgramService.store(h.getLevel()).save(t.libraryId(),id,0,p);var request=new com.google.gson.JsonObject();request.addProperty("id",id.toString());request.addProperty("op","programStep");var origin=t.getBlockPos();ProgramService.handle(owner,t,request);
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above()) instanceof JuniorTurtleEntity,"Raw first step pending"))
            .thenIdle(25).thenExecute(()->{h.assertTrue(h.getLevel().getBlockEntity(origin.above(2))==null,"Raw Lua ran an extra action while paused");try{ProgramService.handle(owner,(JuniorTurtleEntity)h.getLevel().getBlockEntity(origin.above()),request);}catch(Exception e){throw new RuntimeException(e);}})
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above(2)) instanceof JuniorTurtleEntity,"Raw second step pending"))
            .thenExecute(()->h.getLevel().getServer().getPlayerList().remove(owner)).thenSucceed();
    }
    @GameTest(template="empty")
    public static void creativeRobotsStackToSixtyFour(GameTestHelper h){
        var first=JuniorMining.item(h.getLevel().registryAccess());var second=JuniorMining.item(h.getLevel().registryAccess());
        h.assertTrue(first.getMaxStackSize()==64,"Robot item max stack size is "+first.getMaxStackSize());
        h.assertTrue(ItemStack.isSameItemSameComponents(first,second),"Fresh creative robots do not merge");
        var inventory=new net.minecraft.world.SimpleContainer(1);first.setCount(63);inventory.setItem(0,first);second.setCount(2);var rest=inventory.addItem(second);
        h.assertTrue(inventory.getItem(0).getCount()==64&&rest.getCount()==1,"Robot stack did not fill to 64");h.succeed();
    }
    @GameTest(template="empty")
    public static void duplicateLoadedTurtleGetsAnIndependentIdentity(GameTestHelper h){
        var original=place(h,new BlockPos(2,2,2));var clone=place(h,new BlockPos(5,2,5));var id=original.identity();original.selectProgram(UUID.randomUUID());RemoteService.track(original);
        clone.loadWithComponents(original.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());RemoteService.track(clone);
        h.assertTrue(original.identity().equals(id),"Original identity changed");h.assertFalse(clone.identity().equals(id),"Cloned turtle aliases the original");h.assertTrue(clone.selectedProgram()==null,"Clone points to another turtle library");h.assertTrue(RemoteService.resolve(h.getLevel(),id)==original,"Remote binding changed to clone");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void teacherLockRejectsDirectCommandsButAllowsTeacherRemote(GameTestHelper h){
        var teacher=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(teacher.connection.getConnection());
        h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(teacher.getGameProfile(),2,false));
        var t=place(h,new BlockPos(3,2,3));var origin=t.getBlockPos();var pos=origin.getCenter();teacher.teleportTo(pos.x,pos.y,pos.z);t.lock(true);RemoteService.open(teacher,t);t.getAccess().executeCommand(RemoteService.command("up"));
        h.startSequence().thenIdle(20).thenExecute(()->{h.assertTrue(h.getLevel().getBlockEntity(origin)==t,"Locked turtle accepted a direct command");RemoteService.handle(teacher,new RemoteNetwork.Action(t.identity(),"up",0,""));})
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above()) instanceof JuniorTurtleEntity,"Teacher remote movement pending"))
            .thenExecute(()->{h.getLevel().getServer().getPlayerList().deop(teacher.getGameProfile());h.getLevel().getServer().getPlayerList().remove(teacher);}).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void collectedDropMakesRestorationFailWithoutDuplicatingItems(GameTestHelper h){
        var t=place(h,new BlockPos(3,2,3));t.getAccess().getInventory().setItem(0,new ItemStack(Items.DIRT,2));Restoration.save(t.getAccess());
        t.getAccess().executeCommand(new dan200.computercraft.shared.turtle.core.TurtleDropCommand(dan200.computercraft.shared.turtle.core.InteractDirection.FORWARD,1));
        h.startSequence().thenWaitUntil(()->h.assertTrue(t.getAccess().getInventory().getItem(0).getCount()==1,"Drop pending"))
            .thenExecute(()->{var items=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(t.getBlockPos()).inflate(4));h.assertTrue(items.size()==1,"Missing test drop");items.getFirst().discard();var result=t.snapshot().restore(t.getAccess());h.assertFalse(result.isSuccess(),"Collected drop was restored");h.assertTrue(t.getAccess().getInventory().getItem(0).getCount()==1,"Restore duplicated a collected item");}).thenSucceed();
    }
    private static void operation(net.minecraft.server.level.ServerPlayer owner,JuniorTurtleEntity t,UUID id,String op){
        try{var request=new com.google.gson.JsonObject();request.addProperty("id",id.toString());request.addProperty("op",op);ProgramService.handle(owner,t,request);}catch(Exception e){throw new RuntimeException(e);}
    }
    // Every statement now takes a 10-tick beat, and once the batch thins out the test server ran ~180 ticks/s while CC reboots
    // and resumes Lua in real time; 800 ticks were only ~4 s there, so the bound is set in ticks for that rate.
    @GameTest(template="empty",timeoutTicks=4000)
    public static void libraryStepsKeepVariableScopesAndRepeatedRunsWork(GameTestHelper h)throws Exception{
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(4,2,4));var origin=t.getBlockPos();var pos=origin.getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var root=com.google.gson.JsonParser.parseString("""
            {"name":"main","mode":"blocks","workspace":{"variables":[{"id":"x","name":"分數"}],"blocks":{"blocks":[{"type":"variables_set","id":"root-set","fields":{"VAR":{"id":"x"}},"inputs":{"VALUE":{"block":{"type":"math_number","id":"root-value","fields":{"NUM":2}}}},"next":{"block":{"type":"ccedu_call","id":"root-call","fields":{"NAME":"子程式"},"next":{"block":{"type":"ccedu_turtle_command","id":"root-up","fields":{"COMMAND":"up"}}}}}}]}}}
            """).getAsJsonObject();
        var library=com.google.gson.JsonParser.parseString("""
            {"name":"子程式","mode":"blocks","workspace":{"variables":[{"id":"x","name":"分數"}],"blocks":{"blocks":[{"type":"variables_set","id":"sub-set","fields":{"VAR":{"id":"x"}},"inputs":{"VALUE":{"block":{"type":"math_number","id":"sub-value","fields":{"NUM":9}}}},"next":{"block":{"type":"ccedu_comment","id":"sub-end","fields":{"TEXT":"done"}}}}]}}}
            """).getAsJsonObject();var id=UUID.randomUUID();var sub=UUID.randomUUID();var store=ProgramService.store(h.getLevel());store.save(t.libraryId(),id,0,root);store.save(t.libraryId(),sub,0,library);operation(owner,t,id,"programStep");
        h.startSequence().thenWaitUntil(()->h.assertTrue(t.runtime().json().get("blockId").getAsString().equals("root-call"),"Root assignment pending"))
            .thenExecute(()->operation(owner,t,id,"programStep"))
            .thenWaitUntil(()->h.assertTrue(t.runtime().json().get("blockId").getAsString().equals("sub-set"),"Library entry pending"))
            .thenExecute(()->{h.assertTrue(t.runtime().json().get("programId").getAsString().equals(sub.toString()),"Debug source is wrong");operation(owner,t,id,"programStep");})
            .thenWaitUntil(()->h.assertTrue(t.runtime().json().get("blockId").getAsString().equals("sub-end"),"Library assignment pending"))
            .thenExecute(()->{h.assertTrue(t.runtime().json().getAsJsonObject("variables").has("分數")&&t.runtime().json().getAsJsonObject("variables").get("分數").getAsInt()==9,"Subprogram variables missing: "+t.runtime().json());operation(owner,t,id,"programResume");})
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above()) instanceof JuniorTurtleEntity&&t.runtime().json().get("status").getAsString().equals("stopped"),"First run pending"))
            .thenExecute(()->{h.assertTrue(t.runtime().json().getAsJsonObject("variables").has("分數")&&t.runtime().json().getAsJsonObject("variables").get("分數").getAsInt()==2,"Root variable scope lost: "+t.runtime().json());operation(owner,(JuniorTurtleEntity)h.getLevel().getBlockEntity(origin.above()),id,"programRun");})
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above(2)) instanceof JuniorTurtleEntity&&t.runtime().json().get("status").getAsString().equals("stopped"),"Repeated run pending"))
            .thenExecute(()->h.getLevel().getServer().getPlayerList().remove(owner)).thenSucceed();
    }
    /** A student function with an input, one that hands back an answer, and a saved library program, all on a real robot. */
    @GameTest(template="empty",timeoutTicks=4000)
    public static void studentFunctionsAndLibraryCallsRunOnTheRobot(GameTestHelper h)throws Exception{
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(4,2,4));var origin=t.getBlockPos();var pos=origin.getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var main=com.google.gson.JsonParser.parseString("""
            {"name":"main","mode":"blocks","workspace":{"variables":[{"id":"n","name":"格數"},{"id":"x","name":"答案"}],"blocks":{"blocks":[{"type":"ccedu_start","id":"fn-start","next":{"block":{"type":"procedures_callnoreturn","id":"fn-call-climb","extraState":{"name":"往上爬","params":["格數"]},"inputs":{"ARG0":{"block":{"type":"math_number","id":"fn-two","fields":{"NUM":2}}}},"next":{"block":{"type":"ccedu_call","id":"fn-library","fields":{"NAME":"幫手"},"next":{"block":{"type":"variables_set","id":"fn-set","fields":{"VAR":{"id":"x"}},"inputs":{"VALUE":{"block":{"type":"procedures_callreturn","id":"fn-call-double","extraState":{"name":"兩倍","params":["格數"]},"inputs":{"ARG0":{"block":{"type":"math_number","id":"fn-three","fields":{"NUM":3}}}}}}},"next":{"block":{"type":"ccedu_comment","id":"fn-end","fields":{"TEXT":"done"}}}}}}}}}},{"type":"procedures_defnoreturn","id":"fn-climb","extraState":{"params":[{"name":"格數","id":"n"}]},"fields":{"NAME":"往上爬"},"inputs":{"STACK":{"block":{"type":"controls_repeat_ext","id":"fn-loop","inputs":{"TIMES":{"block":{"type":"variables_get","id":"fn-get","fields":{"VAR":{"id":"n"}}}},"DO":{"block":{"type":"ccedu_move","id":"fn-up","fields":{"COMMAND":"up"}}}}}}}},{"type":"procedures_defreturn","id":"fn-double","extraState":{"params":[{"name":"格數","id":"n"}]},"fields":{"NAME":"兩倍"},"inputs":{"RETURN":{"block":{"type":"math_arithmetic","id":"fn-times","fields":{"OP":"MULTIPLY"},"inputs":{"A":{"block":{"type":"variables_get","id":"fn-get2","fields":{"VAR":{"id":"n"}}}},"B":{"block":{"type":"math_number","id":"fn-two2","fields":{"NUM":2}}}}}}}}]}}}
            """).getAsJsonObject();
        var helper=com.google.gson.JsonParser.parseString("""
            {"name":"幫手","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_start","id":"helper-start","next":{"block":{"type":"ccedu_move","id":"helper-up","fields":{"COMMAND":"up"}}}}]}}}
            """).getAsJsonObject();
        var id=UUID.randomUUID();var store=ProgramService.store(h.getLevel());store.save(t.libraryId(),id,0,main);store.save(t.libraryId(),UUID.randomUUID(),0,helper);operation(owner,t,id,"programRun");
        h.succeedWhen(()->{
            var current=RemoteService.resolve(h.getLevel(),t.identity());h.assertTrue(current!=null,"Robot missing");var state=current.runtime().json();
            h.assertTrue(!state.get("status").getAsString().equals("error"),"Program failed: "+state);
            h.assertTrue(current.getBlockPos().equals(origin.above(3))&&state.get("status").getAsString().equals("stopped"),"Program has not finished: "+state);
            var answer=state.getAsJsonObject("variables").get("答案");h.assertTrue(answer!=null&&answer.getAsInt()==6,"Function answer missing: "+state);
            h.getLevel().getServer().getPlayerList().remove(owner);
        });
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void expressionFailureInLibraryReportsItsStableId(GameTestHelper h)throws Exception{
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var pos=t.getBlockPos().getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var main=com.google.gson.JsonParser.parseString("""
            {"name":"main","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_call","id":"outer-call","fields":{"NAME":"bad library"}}]}}}
            """).getAsJsonObject();var bad=com.google.gson.JsonParser.parseString("""
            {"name":"bad library","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_say","id":"say","inputs":{"TEXT":{"block":{"type":"math_arithmetic","id":"bad-expression","fields":{"OP":"ADD"},"inputs":{"A":{"block":{"type":"text","id":"text","fields":{"TEXT":"no-number"}}},"B":{"block":{"type":"math_number","id":"n","fields":{"NUM":1}}}}}}}}]}}}
            """).getAsJsonObject();var id=UUID.randomUUID();var sub=UUID.randomUUID();var store=ProgramService.store(h.getLevel());store.save(t.libraryId(),id,0,main);store.save(t.libraryId(),sub,0,bad);operation(owner,t,id,"programRun");
        h.succeedWhen(()->{var state=t.runtime().json();h.assertTrue(state.get("status").getAsString().equals("error"),"Runtime error pending: "+state);h.assertTrue(state.get("programId").getAsString().equals(sub.toString()),"Wrong library source");h.assertTrue(state.get("blockId").getAsString().equals("bad-expression"),"Wrong failing expression ID");h.getLevel().getServer().getPlayerList().remove(owner);});
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void actualRightClickBindsRemoteAndReopensAfterMovement(GameTestHelper h){
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var origin=t.getBlockPos();var pos=origin.getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());
        var remote=ItemStack.EMPTY;owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,remote);
        var hit=new net.minecraft.world.phys.BlockHitResult(pos,Direction.UP,origin,false);owner.gameMode.useItemOn(owner,h.getLevel(),remote,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(RemoteService.session(owner,t.identity()),"Empty-hand interaction did not open controller");RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"up",0,""));
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above()) instanceof JuniorTurtleEntity,"Remote move pending"))
            .thenExecute(()->{owner.closeContainer();var moved=(JuniorTurtleEntity)h.getLevel().getBlockEntity(origin.above());var newHit=new net.minecraft.world.phys.BlockHitResult(moved.getBlockPos().getCenter(),Direction.UP,moved.getBlockPos(),false);owner.gameMode.useItemOn(owner,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,newHit);h.assertTrue(RemoteService.session(owner,t.identity()),"Empty-hand use did not reconnect after movement");h.getLevel().getServer().getPlayerList().remove(owner);}).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void rapidRemoteClicksQueueInOrderWithoutBlockingSettings(GameTestHelper h){
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var origin=t.getBlockPos();var pos=origin.getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        for(String action:List.of("up","up","turnRight"))RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),action,0,""));
        RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"name",0,"連續操作"));
        RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"select",3,""));
        h.assertTrue("連續操作".equals(t.getLabel())&&t.getAccess().getSelectedSlot()==3,"Unrelated settings were dropped by click throttle");
        h.succeedWhen(()->{var moved=RemoteService.resolve(h.getLevel(),t.identity());h.assertTrue(moved!=null&&moved.getBlockPos().equals(origin.above(2))&&moved.getDirection()==Direction.EAST,"Same-tick commands did not finish in order");h.getLevel().getServer().getPlayerList().remove(owner);});
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void boundedCcQueueRejectsOverflowAndRetainsAcceptedCommands(GameTestHelper h){
        var t=place(h,new BlockPos(3,2,3));var count=new java.util.concurrent.atomic.AtomicInteger();
        for(int i=0;i<17;i++){var result=t.getAccess().executeCommand(access->{count.incrementAndGet();return dan200.computercraft.api.turtle.TurtleCommandResult.success();});h.assertTrue(result.getCallback()!=null,"Accepted command did not queue");}
        var full=t.getAccess().executeCommand(access->{count.addAndGet(100);return dan200.computercraft.api.turtle.TurtleCommandResult.success();});
        h.assertTrue(full.getCallback()==null&&Boolean.FALSE.equals(full.getResult()[0])&&"Too many ongoing turtle commands".equals(full.getResult()[1]),"Overflow was not an immediate recognised failure");
        h.succeedWhen(()->h.assertTrue(count.get()==17,"Accepted commands were lost or overflow executed: "+count.get()));
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void queuedRemoteCommandsRecheckSessionAndTeacherLock(GameTestHelper h){
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var origin=t.getBlockPos();var pos=origin.getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"up",0,""));owner.closeContainer();
        h.startSequence().thenIdle(20).thenExecute(()->{h.assertTrue(t.getBlockPos().equals(origin),"Closed session retained queued control");RemoteService.open(owner,t);RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"up",0,""));t.lock(true);})
            .thenIdle(20).thenExecute(()->{h.assertTrue(t.getBlockPos().equals(origin),"Teacher lock failed to cancel queued control");t.lock(false);h.getLevel().getServer().getPlayerList().remove(owner);}).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void inventoryCommandsRespectVerticalBuildPermissions(GameTestHelper h){
        var t=place(h,new BlockPos(3,2,3));var target=t.getBlockPos().north();h.getLevel().setBlockAndUpdate(target.below(),TeachingContent.BUILD_DENY.get().defaultBlockState());t.getAccess().getInventory().setItem(0,new ItemStack(Items.DIRT,2));
        t.getAccess().executeCommand(new dan200.computercraft.shared.turtle.core.TurtleDropCommand(dan200.computercraft.shared.turtle.core.InteractDirection.FORWARD,1));
        h.startSequence().thenIdle(20).thenExecute(()->{h.assertTrue(t.getAccess().getInventory().getItem(0).getCount()==2,"Drop bypassed deny block");var item=new net.minecraft.world.entity.item.ItemEntity(h.getLevel(),target.getX()+.5,target.getY()+.5,target.getZ()+.5,new ItemStack(Items.DIRT));h.getLevel().addFreshEntity(item);t.getAccess().executeCommand(new dan200.computercraft.shared.turtle.core.TurtleSuckCommand(dan200.computercraft.shared.turtle.core.InteractDirection.FORWARD,1));})
            .thenIdle(20).thenExecute(()->{h.assertTrue(t.getAccess().getInventory().getItem(0).getCount()==2,"Suck bypassed deny block");h.getLevel().setBlockAndUpdate(target.below(),TeachingContent.BUILD_ALLOW.get().defaultBlockState());t.getAccess().executeCommand(new dan200.computercraft.shared.turtle.core.TurtleDropCommand(dan200.computercraft.shared.turtle.core.InteractDirection.FORWARD,1));})
            .thenWaitUntil(()->h.assertTrue(t.getAccess().getInventory().getItem(0).getCount()==1,"Allow block did not permit drop")).thenSucceed();
    }
    @GameTest(template="empty")
    public static void disablingBlocklyStillAllowsIndependentLuaPrograms(GameTestHelper h)throws Exception{
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var pos=t.getBlockPos().getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        var settings=TeachingSettings.get(h.getLevel().getServer());settings.set(h.getLevel().getServer(),"visualEditor",false);
        try{
            var request=com.google.gson.JsonParser.parseString("{\"op\":\"programSave\",\"revision\":0,\"program\":{\"name\":\"disabled blocks\",\"mode\":\"blocks\",\"workspace\":{}}}").getAsJsonObject();request.addProperty("id",UUID.randomUUID().toString());boolean rejected=false;
            try{ProgramService.handle(owner,t,request);}catch(IllegalArgumentException expected){rejected=true;}h.assertTrue(rejected,"Disabled Blockly accepted an edit");
            var program=request.getAsJsonObject("program");program.addProperty("mode","lua");program.addProperty("source","turtle.up()");var result=ProgramService.handle(owner,t,request);h.assertTrue(result.getAsJsonObject("program").get("mode").getAsString().equals("lua"),"Disabling Blockly also disabled independent Lua");
        }finally{settings.set(h.getLevel().getServer(),"visualEditor",true);h.getLevel().getServer().getPlayerList().remove(owner);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void sharedLibraryDependenciesAreInstalledAndConflictingCallsAreRenamed(GameTestHelper h)throws Exception{
        advancedClass(h);
        var sender=h.makeMockServerPlayerInLevel();var receiver=h.makeMockServerPlayerInLevel();for(var p:List.of(sender,receiver))NetworkRegistry.configureMockConnection(p.connection.getConnection());
        var a=place(h,new BlockPos(2,2,2));var b=place(h,new BlockPos(5,2,5));var origin=b.getBlockPos();((dan200.computercraft.shared.turtle.core.TurtleBrain)a.getAccess()).setOwningPlayer(sender.getGameProfile());((dan200.computercraft.shared.turtle.core.TurtleBrain)b.getAccess()).setOwningPlayer(receiver.getGameProfile());for(var p:List.of(sender,receiver)){var pos=a.getBlockPos().getCenter();p.teleportTo(pos.x,pos.y,pos.z);}RemoteService.open(sender,a);RemoteService.track(b);
        var main=com.google.gson.JsonParser.parseString("""
            {"name":"main","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_call","id":"shared-call","fields":{"NAME":"parent"},"next":{"block":{"type":"ccedu_turtle_command","id":"shared-up","fields":{"COMMAND":"up"}}}}]}}}
            """).getAsJsonObject();var helper=com.google.gson.JsonParser.parseString("""
            {"name":"helper","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_comment","id":"helper-block","fields":{"TEXT":"shared dependency"}}]}}}
            """).getAsJsonObject();var store=ProgramService.store(h.getLevel());var id=UUID.randomUUID();store.save(a.libraryId(),id,0,main);store.save(a.libraryId(),UUID.randomUUID(),0,helper);
        var old=helper.deepCopy();old.getAsJsonObject("workspace").getAsJsonObject("blocks").getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("fields").addProperty("TEXT","receiver's existing program");store.save(b.libraryId(),UUID.randomUUID(),0,old);
        var parent=com.google.gson.JsonParser.parseString("{\"name\":\"parent\",\"mode\":\"blocks\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_call\",\"id\":\"parent-call\",\"fields\":{\"NAME\":\"helper\"}}]}}}").getAsJsonObject();store.save(a.libraryId(),UUID.randomUUID(),0,parent);store.save(b.libraryId(),UUID.randomUUID(),0,parent);
        var request=new com.google.gson.JsonObject();request.addProperty("op","programShare");request.addProperty("id",id.toString());request.addProperty("target",b.libraryId().toString());h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(sender.getGameProfile(),2,false));ProgramService.handle(sender,a,request);RemoteService.open(receiver,b);var imported=store.named(b.libraryId(),"Teacher "+sender.getGameProfile().getName()+": main");
        h.assertTrue(store.list(b.libraryId()).size()==5,"Library dependency was not installed");h.assertTrue(imported.getAsJsonObject("workspace").getAsJsonObject("blocks").getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("fields").get("NAME").getAsString().equals("parent (2)"),"Identical parent was reused despite its renamed child");
        h.assertTrue(store.named(b.libraryId(),"parent (2)").getAsJsonObject("workspace").getAsJsonObject("blocks").getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("fields").get("NAME").getAsString().equals("helper (2)"),"Nested dependency call was not renamed");h.assertTrue(store.named(b.libraryId(),"parent").getAsJsonObject("workspace").equals(parent.getAsJsonObject("workspace")),"Recipient's parent was modified");
        h.assertTrue(store.named(b.libraryId(),"helper").getAsJsonObject("workspace").equals(old.getAsJsonObject("workspace")),"Receiver's helper was overwritten");operation(receiver,b,UUID.fromString(imported.get("id").getAsString()),"programRun");
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(origin.above()) instanceof JuniorTurtleEntity&&b.runtime().json().get("status").getAsString().equals("stopped"),"Imported library call did not run: "+b.runtime().json()))
            .thenExecute(()->{h.getLevel().getServer().getPlayerList().deop(sender.getGameProfile());for(var p:List.of(sender,receiver))h.getLevel().getServer().getPlayerList().remove(p);}).thenSucceed();
    }
    // Its own batch: the other batches switch the global settings to the advanced class.
    @GameTest(template="empty",batch="classroom")
    public static void beginnerWorldHidesLuaSharingAndDisks(GameTestHelper h)throws Exception{
        var server=h.getLevel().getServer();var settings=TeachingSettings.get(server);for(var key:List.of("lua","sharing"))settings.set(server,key,false);
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());var t=place(h,new BlockPos(3,2,3));var pos=t.getBlockPos().getCenter();owner.teleportTo(pos.x,pos.y,pos.z);((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());RemoteService.open(owner,t);
        try{
            var fresh=new TeachingSettings();h.assertFalse(fresh.luaAllowed()||fresh.sharing(),"A new world does not default to the beginner class");
            var store=ProgramService.store(h.getLevel());var blocksId=UUID.randomUUID();var luaId=UUID.randomUUID();
            var save=com.google.gson.JsonParser.parseString("""
                {"op":"programSave","revision":0,"program":{"name":"積木","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_turtle_command","id":"beginner-block","fields":{"COMMAND":"up"}}]}}}}
                """).getAsJsonObject();save.addProperty("id",blocksId.toString());ProgramService.handle(owner,t,save);
            var lua=new com.google.gson.JsonObject();lua.addProperty("name","進階");lua.addProperty("mode","lua");lua.addProperty("source","turtle.up()");store.save(t.libraryId(),luaId,0,lua);
            var request=new com.google.gson.JsonObject();request.addProperty("op","programList");var list=ProgramService.handle(owner,t,request);
            h.assertTrue(list.getAsJsonArray("programs").size()==1&&!list.get("lua").getAsBoolean()&&!list.get("sharing").getAsBoolean()&&!list.has("disks"),"Beginner library listed a Lua program or advanced settings");
            for(var op:List.of("programLoad","programInspect","programRun","programCompile")){request.addProperty("op",op);request.addProperty("id",(op.equals("programCompile")?blocksId:luaId).toString());boolean rejected=false;try{ProgramService.handle(owner,t,request);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Beginner world allowed "+op);}
            save.addProperty("id",UUID.randomUUID().toString());save.getAsJsonObject("program").addProperty("mode","lua");save.getAsJsonObject("program").addProperty("source","turtle.up()");boolean rejected=false;try{ProgramService.handle(owner,t,save);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Beginner world saved a Lua program");
            // Floppy disks are gone entirely, whatever the class settings.
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(ModRegistry.Items.DISK.get()));for(var op:List.of("programExport","programDiskList","programImport")){request.addProperty("op",op);request.addProperty("id",blocksId.toString());request.addProperty("file",blocksId+".json");rejected=false;try{ProgramService.handle(owner,t,request);}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,"Disk operation still works: "+op);}
            // Sharing is off for students and for teacher delivery alike.
            server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(owner.getGameProfile(),2,false));
            for(var op:List.of("programTargets","programShare")){request.addProperty("op",op);request.addProperty("target","*");rejected=false;try{ProgramService.handle(owner,t,request);}catch(IllegalArgumentException e){rejected=e.getMessage().contains("sharing");}h.assertTrue(rejected,"Disabled sharing allowed teacher "+op);}
            server.getPlayerList().deop(owner.getGameProfile());
        }finally{advancedClass(h);server.getPlayerList().remove(owner);}h.succeed();
    }
    @GameTest(template="empty")
    public static void teacherGuidesStudentRobotAndStudentEditorSeesTheSavedEdit(GameTestHelper h)throws Exception{
        var server=h.getLevel().getServer();var student=h.makeMockServerPlayerInLevel();var teacher=h.makeMockServerPlayerInLevel();for(var p:List.of(student,teacher))NetworkRegistry.configureMockConnection(p.connection.getConnection());
        var robot=place(h,new BlockPos(2,2,2));var own=place(h,new BlockPos(5,2,5));((dan200.computercraft.shared.turtle.core.TurtleBrain)robot.getAccess()).setOwningPlayer(student.getGameProfile());((dan200.computercraft.shared.turtle.core.TurtleBrain)own.getAccess()).setOwningPlayer(teacher.getGameProfile());
        for(var p:List.of(student,teacher)){var pos=robot.getBlockPos().getCenter();p.teleportTo(pos.x,pos.y,pos.z);}RemoteService.track(robot);RemoteService.track(own);
        server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(teacher.getGameProfile(),2,false));
        try{
            RemoteService.open(student,robot);var id=UUID.randomUUID();
            var save=com.google.gson.JsonParser.parseString("""
                {"op":"programSave","revision":0,"program":{"name":"課堂練習","mode":"blocks","workspace":{"blocks":{"blocks":[{"type":"ccedu_turtle_command","id":"student-block","fields":{"COMMAND":"up"}}]}}}}
                """).getAsJsonObject();save.addProperty("id",id.toString());ProgramService.handle(student,robot,save);
            RemoteService.open(teacher,robot);var load=new com.google.gson.JsonObject();load.addProperty("op","programLoad");load.addProperty("id",id.toString());
            var seen=ProgramService.handle(teacher,robot,load).getAsJsonObject("program");h.assertTrue(seen.get("name").getAsString().equals("課堂練習"),"Teacher cannot read the student's program on the student's robot");
            save.addProperty("revision",seen.get("revision").getAsLong());save.getAsJsonObject("program").getAsJsonObject("workspace").getAsJsonObject("blocks").getAsJsonArray("blocks").get(0).getAsJsonObject().getAsJsonObject("fields").addProperty("COMMAND","down");
            long revision=ProgramService.handle(teacher,robot,save).getAsJsonObject("program").get("revision").getAsLong();
            h.assertTrue(ProgramService.store(h.getLevel()).list(own.libraryId()).isEmpty(),"Teacher edit went into the teacher's own library");
            var state=new com.google.gson.JsonObject();state.addProperty("op","programState");state.addProperty("id",id.toString());var polled=ProgramService.handle(student,robot,state);
            h.assertTrue(polled.get("selected").getAsString().equals(id.toString())&&polled.get("revision").getAsLong()==revision,"Student editor cannot see the teacher's saved revision");
            RemoteService.open(student,own);h.assertFalse(RemoteService.session(student,own.identity()),"Student opened the teacher's robot");
            boolean denied=false;try{ProgramService.handle(student,own,load);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"Student read the teacher's robot");
        }finally{server.getPlayerList().deop(teacher.getGameProfile());for(var p:List.of(student,teacher))server.getPlayerList().remove(p);}h.succeed();
    }
}
