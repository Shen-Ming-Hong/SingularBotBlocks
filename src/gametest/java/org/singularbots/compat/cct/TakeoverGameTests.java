package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.singularbots.network.ProgramNetwork;
import java.util.*;

@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class TakeoverGameTests {
    private static ServerPlayer player(GameTestHelper h,boolean teacher){
        var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"guide"+UUID.randomUUID().toString().substring(0,8));
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,p,cookie);NetworkRegistry.configureMockConnection(connection);
        if(teacher)h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(profile,2,false));return p;
    }
    private static JuniorTurtleEntity robot(GameTestHelper h,ServerPlayer owner,BlockPos pos){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());t.placedBy(owner);RemoteService.track(t);return t;
    }
    private static void open(ServerPlayer p,JuniorTurtleEntity t){var c=t.getBlockPos().getCenter();p.teleportTo(c.x,c.y,c.z);RemoteService.open(p,t);}
    private static JsonObject op(String op){var j=new JsonObject();j.addProperty("op",op);return j;}
    private static JsonObject save(UUID id,long revision){var q=op("programSave");q.addProperty("id",id.toString());q.addProperty("revision",revision);q.add("program",JsonParser.parseString("{\"name\":\"課堂練習\",\"mode\":\"blocks\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_start\",\"id\":\"start\"}]}}}"));return q;}
    private static UUID token(ServerPlayer p,JuniorTurtleEntity t){return UUID.fromString(TeacherTakeover.state(p,t).get("token").getAsString());}
    private static void denied(GameTestHelper h,Throwing action,String message)throws Exception{boolean denied=false;try{action.run();}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,message);}
    private interface Throwing{void run()throws Exception;}
    private static void clean(ServerPlayer... players){for(var p:players){p.closeContainer();p.getServer().getPlayerList().deop(p.getGameProfile());p.getServer().getPlayerList().remove(p);}}

    @GameTest(template="empty",batch="takeover")
    public static void handoffFlushesBeforeTeacherWritesAndKeepsStudentWatching(GameTestHelper h)throws Exception{
        var student=player(h,false);var teacher=player(h,true);var t=robot(h,student,new BlockPos(2,2,2));open(student,t);open(teacher,t);var id=UUID.randomUUID();
        try{
            denied(h,()->TeacherTakeover.start(student,t),"Student started a teacher takeover");
            var queued=new RemoteService.CheckedRemoteCommand(student,t.identity(),access->{throw new GameTestAssertException("Queued student command executed during takeover");});
            t.runtime().prepare(new org.singularbots.program.WorkspaceCompiler.Compiled("",Map.of(),Set.of()),false);
            TeacherTakeover.start(teacher,t);var token=token(student,t);
            h.assertTrue(t.runtime().json().get("status").getAsString().equals("running"),"Taking over stopped the running program");
            h.assertTrue(TeacherTakeover.state(student,t).get("phase").getAsString().equals("preparing"),"Teacher did not wait for final student save");
            denied(h,()->ProgramService.handle(teacher,t,save(id,0)),"Teacher wrote before student acknowledged");
            ProgramService.handle(student,t,save(id,0));
            TeacherTakeover.acknowledge(teacher,new ProgramNetwork.TakeoverAck(token,true));TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(UUID.randomUUID(),true));
            h.assertTrue(TeacherTakeover.state(student,t).get("phase").getAsString().equals("preparing"),"Wrong player or token acknowledged");
            TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(token,true));
            h.assertTrue(RemoteService.authorized(student,t)&&student.containerMenu.stillValid(student),"Student lost read access or menu");
            var inspect=op("programInspect");inspect.addProperty("id",id.toString());ProgramService.handle(student,t,inspect);
            denied(h,()->ProgramService.handle(student,t,save(id,1)),"Student saved over teacher");
            var load=inspect.deepCopy();load.addProperty("op","programLoad");denied(h,()->ProgramService.handle(student,t,load),"Read-only student changed selection");
            h.assertFalse(queued.execute(t.getAccess()).isSuccess(),"Queued remote command was accepted");
            t.getAccess().getInventory().setItem(0,new ItemStack(Items.DIAMOND,3));
            h.assertTrue(student.containerMenu.quickMoveStack(student,0).isEmpty()&&t.getAccess().getInventory().getItem(0).getCount()==3,"Student changed robot inventory");
            ProgramService.handle(teacher,t,save(id,1));TeacherTakeover.release(teacher,t);
            ProgramService.handle(student,t,save(id,2));TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(token,true));
            h.assertTrue(TeacherTakeover.canWrite(student,t.libraryId()),"Late acknowledgement reactivated a released takeover");
        }finally{ProgramService.stop(t);clean(student,teacher);}h.succeed();
    }
    @GameTest(template="empty",batch="takeover")
    public static void sharedLibraryIsProtectedAcrossRobotsAndOtherTeachers(GameTestHelper h)throws Exception{
        var student=player(h,false);var teacher=player(h,true);var second=player(h,true);
        var a=robot(h,student,new BlockPos(2,2,2));var b=robot(h,student,new BlockPos(5,2,5));open(student,b);open(teacher,a);open(second,a);var id=UUID.randomUUID();
        try{
            ProgramService.handle(student,b,save(id,0));TeacherTakeover.start(teacher,a);TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(token(student,a),true));
            denied(h,()->TeacherTakeover.start(second,a),"Second teacher stole takeover");denied(h,()->ProgramService.handle(second,a,save(id,1)),"Second teacher wrote over holder");
            denied(h,()->ProgramService.handle(student,b,save(id,1)),"Another robot bypassed library lock");
            h.assertTrue(TeacherTakeover.canControl(student,b)&&!TeacherTakeover.canControl(student,a),"Other robot control was unnecessarily blocked");
            var load=op("programLoad");load.addProperty("id",id.toString());ProgramService.handle(student,b,load);
            var recall=op("programRecall");recall.addProperty("robot",a.identity().toString());denied(h,()->ProgramService.handle(student,b,recall),"Other robot recalled the demonstration robot");
            var source=robot(h,second,new BlockPos(8,2,8));open(second,source);var sourceId=UUID.randomUUID();ProgramService.handle(second,source,save(sourceId,0));
            var settings=TeachingSettings.get(teacher.getServer());boolean sharing=settings.sharing();settings.set(teacher.getServer(),"sharing",true);
            try{var share=op("programShare");share.addProperty("id",sourceId.toString());share.addProperty("target",a.libraryId().toString());denied(h,()->ProgramService.handle(second,source,share),"Teacher delivery bypassed target library lock");}
            finally{settings.set(teacher.getServer(),"sharing",sharing);}
            teacher.closeContainer();h.assertTrue(TeacherTakeover.canWrite(student,a.libraryId()),"Closing the controller kept the takeover");
        }finally{clean(student,teacher,second);}h.succeed();
    }
    @GameTest(template="empty",batch="takeover")
    public static void failedAndTimedOutHandoffsPreserveStudentRevision(GameTestHelper h)throws Exception{
        var student=player(h,false);var teacher=player(h,true);var t=robot(h,student,new BlockPos(2,2,2));open(student,t);open(teacher,t);var id=UUID.randomUUID();
        try{
            ProgramService.handle(student,t,save(id,0));TeacherTakeover.start(teacher,t);var old=token(student,t);
            TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(old,false));h.assertTrue(TeacherTakeover.canWrite(student,t.libraryId()),"Failed save did not cancel handoff");
            TeacherTakeover.start(teacher,t);TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(old,true));
            h.assertTrue(TeacherTakeover.state(student,t).get("phase").getAsString().equals("preparing"),"Old token completed a new handoff");
            TeacherTakeover.check(t.libraryId(),System.currentTimeMillis()+20001);h.assertTrue(TeacherTakeover.canWrite(student,t.libraryId()),"Timeout did not cancel handoff");
            h.assertTrue(ProgramService.store(h.getLevel()).load(t.libraryId(),id).get("revision").getAsLong()==1,"Cancellation changed the saved program");
        }finally{ProgramService.stop(t);clean(student,teacher);}h.succeed();
    }
    @GameTest(template="empty",batch="takeover")
    public static void permissionLossAndRobotDestructionReleaseTakeover(GameTestHelper h){
        var student=player(h,false);var teacher=player(h,true);var t=robot(h,student,new BlockPos(2,2,2));open(student,t);open(teacher,t);
        try{
            TeacherTakeover.start(teacher,t);TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(token(student,t),true));
            teacher.getServer().getPlayerList().deop(teacher.getGameProfile());TeacherTakeover.check(t.libraryId(),System.currentTimeMillis());
            h.assertTrue(TeacherTakeover.canWrite(student,t.libraryId()),"Lost OP permission kept takeover");
            teacher.getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(teacher.getGameProfile(),2,false));
            TeacherTakeover.start(teacher,t);TeacherTakeover.acknowledge(student,new ProgramNetwork.TakeoverAck(token(student,t),true));
            h.setBlock(new BlockPos(2,2,2),net.minecraft.world.level.block.Blocks.AIR);TeacherTakeover.check(t.libraryId(),System.currentTimeMillis());
            h.assertTrue(TeacherTakeover.canWrite(student,t.libraryId()),"Broken robot kept takeover");
        }finally{ProgramService.stop(t);clean(student,teacher);}h.succeed();
    }
}
