package org.singularbots.compat.cct;

import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.function.Consumer;

@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class MiningDropsGameTests {
    private static boolean previousDrops;
    @BeforeBatch(batch="robotMiningDrops")
    public static void disableDrops(ServerLevel level){
        previousDrops=level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,level.getServer());
    }
    @AfterBatch(batch="robotMiningDrops")
    public static void restoreDrops(ServerLevel level){
        level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(previousDrops,level.getServer());
    }
    private static GameTestSequence dig(GameTestSequence sequence,GameTestHelper h,TurtleBlockEntity robot,InteractDirection direction,Consumer<TurtleCommandResult> check){
        var result=new TurtleCommandResult[1];var target=new String[1];
        var drops=new boolean[1];
        return sequence.thenExecute(()->{
            drops[0]=h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            target[0]=direction+" "+h.getLevel().getBlockState(robot.getBlockPos().relative(direction.toWorldDir(robot.getAccess())));
            robot.getAccess().executeCommand(access->{
                result[0]=(direction==InteractDirection.FORWARD?RemoteService.command("dig"):TurtleToolCommand.dig(direction,null)).execute(access);return result[0];
            });
        }).thenWaitUntil(()->h.assertTrue(result[0]!=null,"Dig has not finished: "+target[0])).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)==drops[0],"Dig changed the world gamerule");
            h.assertFalse(JuniorMining.collecting(h.getLevel()),"Dig leaked its drop exception");check.accept(result[0]);
        });
    }
    private static int count(TurtleBlockEntity robot,Item item){
        int count=0;for(int i=0;i<16;i++){var stack=robot.getAccess().getInventory().getItem(i);if(stack.is(item))count+=stack.getCount();}return count;
    }
    private static int dropped(GameTestHelper h,BlockPos pos,Item item){
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }
    @GameTest(template="empty",batch="robotMiningDrops",timeoutTicks=500)
    public static void onlyJuniorMiningCollectsWithBlockDropsOff(GameTestHelper h){
        var level=h.getLevel();var owner=h.makeMockServerPlayerInLevel();owner.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        h.setBlock(new BlockPos(4,3,4),TeachingContent.JUNIOR.get());var robot=(JuniorTurtleEntity)h.getBlockEntity(new BlockPos(4,3,4));
        ((TurtleBrain)robot.getAccess()).setOwningPlayer(owner.getGameProfile());robot.placedBy(owner);
        owner.teleportTo(robot.getBlockPos().getX()+.5,robot.getBlockPos().getY()+3,robot.getBlockPos().getZ()+.5);
        var pos=robot.getBlockPos();var front=pos.relative(robot.getDirection());var inv=robot.getAccess().getInventory();
        // A thrown break must unwind the scope just like a successful or rejected break.
        TeachingPermissions.CURRENT.set(robot.getAccess());
        try{
            var throwing=new ServerPlayerGameMode(owner){@Override public boolean destroyBlock(BlockPos target){
                h.assertTrue(JuniorMining.collecting(level),"Robot dig did not open its scope");
                h.assertFalse(level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS),"Robot dig changed the gamerule");throw new IllegalStateException("test break failure");
            }};
            try{JuniorMining.dig(throwing,front);h.fail("Break exception was swallowed");}catch(IllegalStateException expected){h.assertTrue(expected.getMessage().equals("test break failure"),"Unexpected exception");}
        }finally{TeachingPermissions.CURRENT.remove();}
        h.assertFalse(JuniorMining.collecting(level),"Throwing break leaked its scope");
        level.setBlockAndUpdate(front,Blocks.OBSIDIAN.defaultBlockState());
        var sequence=dig(h.startSequence(),h,robot,InteractDirection.FORWARD,result->{
            h.assertTrue(result.isSuccess()&&level.isEmptyBlock(front)&&count(robot,Items.OBSIDIAN)==1,"Forward mining lost its obsidian");
            level.setBlockAndUpdate(pos.above(),Blocks.OAK_LOG.defaultBlockState());
        });
        sequence=dig(sequence,h,robot,InteractDirection.UP,result->{
            h.assertTrue(result.isSuccess()&&count(robot,Items.OAK_LOG)==1,"Upward mining lost its log");
            level.setBlockAndUpdate(pos.below(),Blocks.DIAMOND_ORE.defaultBlockState());
        });
        sequence=dig(sequence,h,robot,InteractDirection.DOWN,result->{
            h.assertTrue(result.isSuccess()&&count(robot,Items.DIAMOND)==1,"Downward mining lost its diamond");
            for(int i=0;i<16;i++)inv.setItem(i,new ItemStack(Items.COBBLESTONE,64));
            level.setBlockAndUpdate(front,Blocks.DIRT.defaultBlockState());
        });
        sequence=dig(sequence,h,robot,InteractDirection.FORWARD,result->{
            h.assertTrue(result.isSuccess()&&count(robot,Items.COBBLESTONE)==1024&&dropped(h,front,Items.DIRT)==1,"Full backpack lost or duplicated its overflow");
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(front).inflate(2)))item.discard();inv.clearContent();
            level.setBlockAndUpdate(front,Blocks.CHEST.defaultBlockState());
            var diamond=new ItemStack(Items.DIAMOND,3);diamond.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("kept name"));
            ((ChestBlockEntity)level.getBlockEntity(front)).setItem(0,diamond);
        });
        sequence=dig(sequence,h,robot,InteractDirection.FORWARD,result->{
            h.assertTrue(result.isSuccess()&&count(robot,Items.CHEST)==1&&count(robot,Items.DIAMOND)==3,"Chest mining lost or duplicated its contents");
            h.assertTrue(java.util.stream.IntStream.range(0,16).mapToObj(inv::getItem).anyMatch(s->s.is(Items.DIAMOND)&&s.getHoverName().getString().equals("kept name")),"Chest item metadata was lost");
            inv.clearContent();level.setBlockAndUpdate(front,TeachingContent.JUNIOR.get().defaultBlockState());
            ((JuniorTurtleEntity)level.getBlockEntity(front)).getAccess().getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
        });
        sequence=dig(sequence,h,robot,InteractDirection.FORWARD,result->{
            h.assertTrue(result.isSuccess()&&count(robot,TeachingContent.JUNIOR_ITEM.get())+dropped(h,front,TeachingContent.JUNIOR_ITEM.get())==1,"Mining another robot duplicated or lost its body");
            h.assertTrue(count(robot,Items.DIAMOND)+dropped(h,front,Items.DIAMOND)==3,"Mining another robot duplicated or lost its backpack");
            inv.clearContent();level.setBlockAndUpdate(front,Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(front.below(),TeachingContent.BUILD_DENY.get().defaultBlockState());
        });
        sequence=dig(sequence,h,robot,InteractDirection.FORWARD,result->{
            h.assertFalse(result.isSuccess(),"Mining bypassed the teacher's deny block");h.assertTrue(level.getBlockState(front).is(Blocks.DIRT)&&count(robot,Items.DIRT)==0,"Denied dig changed the block or inventory");
            level.setBlockAndUpdate(front.below(),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(front,Blocks.BEDROCK.defaultBlockState());
        });
        sequence=dig(sequence,h,robot,InteractDirection.FORWARD,result->{
            h.assertFalse(result.isSuccess(),"Bedrock was mined");h.assertTrue(level.getBlockState(front).is(Blocks.BEDROCK),"Bedrock changed");
            level.setBlockAndUpdate(front,Blocks.DIRT.defaultBlockState());robot.lock(true);robot.getAccess().executeCommand(RemoteService.command("dig"));
        });
        sequence.thenIdle(20).thenExecute(()->{
            h.assertTrue(level.getBlockState(front).is(Blocks.DIRT)&&count(robot,Items.DIRT)==0,"Mining bypassed the teacher lock");h.assertFalse(JuniorMining.collecting(level),"Locked dig leaked its scope");robot.lock(false);
            h.assertTrue(owner.gameMode.destroyBlock(front),"Player could not break dirt");
            h.assertTrue(dropped(h,front,Items.DIRT)==0&&owner.getInventory().countItem(Items.DIRT)==0,"Player acquired a block drop");
            level.setBlockAndUpdate(front,ModRegistry.Blocks.TURTLE_NORMAL.get().defaultBlockState());
            var ordinary=(TurtleBlockEntity)level.getBlockEntity(front);ordinary.getAccess().setUpgrade(TurtleSide.LEFT,robot.getAccess().getUpgradeWithData(TurtleSide.LEFT));
            level.setBlockAndUpdate(front.above(),Blocks.DIRT.defaultBlockState());
        });
        // A regular CC turtle uses the same TurtleTool class but must retain vanilla gamerule behaviour.
        var ordinaryResult=new TurtleCommandResult[1];
        sequence.thenExecute(()->{
            var ordinary=(TurtleBlockEntity)level.getBlockEntity(front);
            ordinary.getAccess().executeCommand(access->{ordinaryResult[0]=TurtleToolCommand.dig(InteractDirection.UP,null).execute(access);return ordinaryResult[0];});
        }).thenWaitUntil(()->h.assertTrue(ordinaryResult[0]!=null,"Ordinary turtle dig has not finished")).thenExecute(()->{
            h.assertTrue(ordinaryResult[0].isSuccess()&&level.isEmptyBlock(front.above()),"Ordinary turtle failed to break dirt");
            h.assertTrue(count((TurtleBlockEntity)level.getBlockEntity(front),Items.DIRT)==0&&dropped(h,front,Items.DIRT)==0,"Ordinary CC turtle gained the Junior exception");
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,level.getServer());level.setBlockAndUpdate(front,Blocks.DIRT.defaultBlockState());
            h.assertTrue(owner.gameMode.destroyBlock(front)&&dropped(h,front,Items.DIRT)==1,"Restored gamerule did not restore player drops");
            level.setBlockAndUpdate(front,TeachingContent.JUNIOR.get().defaultBlockState());
            ((JuniorTurtleEntity)level.getBlockEntity(front)).getAccess().getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
        });
        dig(sequence,h,robot,InteractDirection.FORWARD,result->{
            h.assertTrue(result.isSuccess()&&count(robot,TeachingContent.JUNIOR_ITEM.get())+dropped(h,front,TeachingContent.JUNIOR_ITEM.get())==1,"Normal mining duplicated or lost the robot");
            h.assertTrue(count(robot,Items.DIAMOND)+dropped(h,front,Items.DIAMOND)==3,"Normal mining duplicated or lost the backpack");
            h.getLevel().getServer().getPlayerList().remove(owner);
        }).thenSucceed();
    }
}
