package org.singularbots.compat.cct;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.singularbots.turtle.CropGrowth;
import java.util.UUID;

/** "Crop in front is fully grown" and "crop growth stage": the last stage comes from each block, not a fixed number. */
@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class CropGameTests {
    @GameTest(template="empty")
    public static void eachCropIsGrownAtItsOwnLastStage(GameTestHelper h){
        record Case(BlockState state,int age,boolean grown){}
        var cases=new Case[]{
            new Case(Blocks.WHEAT.defaultBlockState(),0,false),
            new Case(((CropBlock)Blocks.WHEAT).getStateForAge(6),6,false),
            new Case(((CropBlock)Blocks.WHEAT).getStateForAge(7),7,true),
            // Beetroot is a crop that is ripe at 3, not 7.
            new Case(((CropBlock)Blocks.BEETROOTS).getStateForAge(3),3,true),
            // Bushes that are not CropBlock use their age property, like Ars Nouveau's sourceberry bush (also 0 to 3).
            new Case(Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,2),2,false),
            new Case(Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3),3,true),
            new Case(Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,3),3,true),
            new Case(Blocks.STONE.defaultBlockState(),CropGrowth.NOT_A_CROP,false),
            new Case(Blocks.AIR.defaultBlockState(),CropGrowth.NOT_A_CROP,false),
        };
        for(var c:cases){
            h.assertTrue(CropGrowth.age(c.state())==c.age(),c.state()+" reports stage "+CropGrowth.age(c.state())+", expected "+c.age());
            h.assertTrue(CropGrowth.grown(c.state())==c.grown(),c.state()+" grown should be "+c.grown());
        }
        h.succeed();
    }

    /** Saying a true/false block shows Minecraft's own Yes / No (是／否) in each player's language, not Lua's true / false. */
    @GameTest(template="empty")
    public static void sayingATrueFalseValueShowsYesOrNo(GameTestHelper h)throws Exception{
        var yes=TeachingLua.TurtleEdu.message("小明",null,true);var no=TeachingLua.TurtleEdu.message(null,null,false);
        h.assertTrue(yes.getSiblings().getFirst().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("gui.yes"),"true did not read as gui.yes: "+yes);
        h.assertTrue(no.getSiblings().getFirst().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("gui.no"),"false did not read as gui.no: "+no);
        h.assertTrue(yes.getString().equals("<小明> Yes")&&no.getString().equals("<Junior Turtle> No"),"Server-side text: "+yes.getString()+" / "+no.getString());
        h.assertTrue(TeachingLua.TurtleEdu.message("A","true",false).getString().equals("<A> true"),"Text that says true is no longer kept as written");
        h.succeed();
    }

    /** A robot runs the student's check: it harvests a ripe crop and leaves a growing one alone. */
    @GameTest(template="empty",timeoutTicks=200)
    public static void aRobotHarvestsOnlyTheRipeCropInFront(GameTestHelper h)throws Exception{
        TeachingGameTests.advancedClass(h);
        var owner=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(owner.connection.getConnection());
        var ripe=robot(h,owner,new BlockPos(1,2,3));var young=robot(h,owner,new BlockPos(5,2,3));
        var ripeCrop=crop(h,ripe,7);var youngCrop=crop(h,young,3);
        // Each robot digs only when the crop is ripe and moves up when it is still growing; below it is not a crop.
        String program="if turtleedu.isCropGrown() and turtleedu.getCropAge() == 7 and turtleedu.getCropAgeDown() == -1 and not turtleedu.isCropGrownDown() then turtle.dig() "
            +"elseif not turtleedu.isCropGrown() and turtleedu.getCropAge() == 3 then turtle.up() end";
        var ripeOrigin=ripe.getBlockPos();var youngOrigin=young.getBlockPos();
        // Both robots belong to one student, so they share one library and run the same saved program.
        var id=UUID.randomUUID();var p=new JsonObject();p.addProperty("name","crop");p.addProperty("mode","lua");p.addProperty("source",program);
        ProgramService.store(h.getLevel()).save(ripe.libraryId(),id,0,p);
        run(owner,ripe,id);run(owner,young,id);
        h.startSequence()
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockState(ripeCrop).isAir(),"The robot did not harvest the ripe wheat"))
            .thenWaitUntil(()->h.assertTrue(h.getLevel().getBlockEntity(youngOrigin.above()) instanceof JuniorTurtleEntity,"The robot did not see the young wheat as still growing"))
            .thenExecute(()->{
                h.assertTrue(h.getLevel().getBlockState(youngCrop).is(Blocks.WHEAT),"The young wheat was harvested");
                h.assertTrue(h.getLevel().getBlockEntity(ripeOrigin) instanceof JuniorTurtleEntity,"The harvesting robot moved");
                h.getLevel().getServer().getPlayerList().remove(owner);
            }).thenSucceed();
    }
    private static JuniorTurtleEntity robot(GameTestHelper h,net.minecraft.server.level.ServerPlayer owner,BlockPos pos){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());return t;
    }
    /** Plants wheat at the given stage on farmland in front of the robot; returns its absolute position. */
    private static BlockPos crop(GameTestHelper h,JuniorTurtleEntity t,int age){
        var front=t.getBlockPos().relative(t.getAccess().getDirection());
        h.getLevel().setBlock(front.below(),Blocks.FARMLAND.defaultBlockState(),3);
        h.getLevel().setBlock(front,((CropBlock)Blocks.WHEAT).getStateForAge(age),3);return front;
    }
    private static void run(net.minecraft.server.level.ServerPlayer owner,JuniorTurtleEntity t,UUID id)throws Exception{
        var pos=t.getBlockPos().getCenter();owner.teleportTo(pos.x,pos.y+1,pos.z);RemoteService.open(owner,t);
        var request=new JsonObject();request.addProperty("id",id.toString());request.addProperty("op","programRun");ProgramService.handle(owner,t,request);
    }
}
