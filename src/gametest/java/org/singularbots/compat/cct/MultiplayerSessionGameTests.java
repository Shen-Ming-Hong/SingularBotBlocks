package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.singularbots.network.*;
import java.util.*;

/** Server-side halves of the multiplayer disconnect cases (DCN-01/03/04/05 in the maintainer acceptance plan). */
@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class MultiplayerSessionGameTests {
    private static ServerPlayer player(GameTestHelper h){var player=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(player.connection.getConnection());return player;}
    /** Logs the same account in again, as a student reconnecting after quitting. */
    private static ServerPlayer reconnect(GameTestHelper h,com.mojang.authlib.GameProfile profile){
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,cookie.clientInformation()){
            @Override public boolean isSpectator(){return false;}
            @Override public boolean isCreative(){return true;}
        };
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);NetworkRegistry.configureMockConnection(connection);return player;
    }
    private static JuniorTurtleEntity robot(GameTestHelper h,BlockPos pos,ServerPlayer owner){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());t.placedBy(owner,true);
        var center=t.getBlockPos().getCenter();owner.teleportTo(center.x,center.y,center.z);RemoteService.track(t);return t;
    }
    private static JsonObject program(String name){return program(name,"up");}
    private static JsonObject program(String name,String command){
        return JsonParser.parseString("{\"name\":\""+name+"\",\"mode\":\"blocks\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_turtle_command\",\"id\":\"disconnect-"+command+"\",\"fields\":{\"COMMAND\":\""+command+"\"}}]}}}").getAsJsonObject();
    }
    private static JsonObject op(String op,UUID id){var q=new JsonObject();q.addProperty("op",op);q.addProperty("id",id.toString());return q;}
    private static String status(JuniorTurtleEntity t){return t.runtime().json().get("status").getAsString();}
    private static JsonObject saveRequest(UUID id,long revision,String name){
        var q=new JsonObject();q.addProperty("op","programSave");q.addProperty("id",id.toString());q.addProperty("revision",revision);q.add("program",program(name));return q;
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void disconnectDiscardsPartialUploadAndQueuedCommands(GameTestHelper h)throws Exception{
        var owner=player(h);var t=robot(h,new BlockPos(3,2,3),owner);var origin=t.getBlockPos();var id=UUID.randomUUID();
        RemoteService.open(owner,t);ProgramService.handle(owner,t,saveRequest(id,0,"原本程式"));
        // Half of a two-fragment save, then a queued move, then the connection drops.
        var text=saveRequest(id,1,"半份上傳").toString();
        ProgramService.fragment(owner,new ProgramNetwork.Fragment(t.identity(),UUID.randomUUID(),0,2,text.substring(0,text.length()/2)));
        h.assertTrue(ProgramService.uploading(owner.getUUID()),"First fragment did not start an upload");
        RemoteService.handle(owner,new RemoteNetwork.Action(t.identity(),"up",0,""));
        h.getLevel().getServer().getPlayerList().remove(owner);
        h.assertFalse(ProgramService.uploading(owner.getUUID()),"Disconnect kept the partial upload");
        h.startSequence().thenIdle(20).thenExecute(()->{
            h.assertTrue(t.getBlockPos().equals(origin)&&h.getLevel().getBlockEntity(origin)==t,"Queued command ran after its player disconnected");
            try{var saved=ProgramService.store(h.getLevel()).load(owner.getUUID(),id);h.assertTrue(saved.get("name").getAsString().equals("原本程式")&&saved.get("revision").getAsLong()==1,"Partial upload changed the saved program");}
            catch(java.io.IOException e){throw new GameTestAssertException("Saved program unreadable after disconnect: "+e.getMessage());}
        }).thenSucceed();
    }
    // Like the emergency-stop test, its non-yielding loop holds the shared CC computer thread, so it runs in its own batch
    // instead of slowing paced block programs in other tests.
    @GameTest(template="empty",batch="offlineLock",timeoutTicks=400)
    public static void teacherStopsAnOfflineStudentsRunningProgram(GameTestHelper h){
        var owner=player(h);var teacher=player(h);var t=robot(h,new BlockPos(3,2,3),owner);var center=t.getBlockPos().getCenter();teacher.teleportTo(center.x,center.y,center.z);
        h.getLevel().getServer().getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(teacher.getGameProfile(),2,false));
        // A yielding loop, like a real student program. A non-yielding loop only stops once CC aborts it after 7 s of
        // wall-clock time, and the GameTest server runs ticks faster than real time, so its timing would decide the test.
        // The emergency stop test covers non-yielding code.
        t.runtime().prepare(new org.singularbots.program.WorkspaceCompiler.Compiled("while true do sleep(0) end",Map.of(),Set.of()),false);t.createServerComputer().turnOn();var locked=new long[1];
        h.startSequence().thenIdle(35).thenExecute(()->{
            h.assertTrue(t.getServerComputer()!=null&&t.getServerComputer().isOn(),"Student program did not start");
            h.getLevel().getServer().getPlayerList().remove(owner);
        }).thenIdle(20).thenExecute(()->{
            // Current behaviour, pending the owner's decision: logging out does not stop the student's program.
            h.assertTrue(t.getServerComputer()!=null&&t.getServerComputer().isOn(),"Logging out stopped the student's program");
            RemoteService.open(teacher,t);RemoteService.handle(teacher,new RemoteNetwork.Action(t.identity(),"lock",1,""));
            h.assertTrue(t.locked()&&t.runtime().json().get("status").getAsString().equals("stopped"),"Teacher could not lock and stop an offline student's robot");locked[0]=h.getTick();
            // CC shuts the computer down on its own thread, slower while the whole batch runs.
        }).thenWaitUntil(()->h.assertTrue(t.getServerComputer()==null||!t.getServerComputer().isOn(),"Teacher lock left the offline student's program running"))
        .thenExecute(()->{
            h.assertTrue(h.getTick()-locked[0]<=80,"Teacher lock took more than 80 ticks to stop the program");t.lock(false);h.getLevel().getServer().getPlayerList().deop(teacher.getGameProfile());h.getLevel().getServer().getPlayerList().remove(teacher);
        }).thenSucceed();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void reconnectedStudentStopsErroredProgramAndTurnsRobot(GameTestHelper h)throws Exception{
        // DCN-01: a blocked program ends in error, the student quits and returns, stops it and steers by hand.
        var owner=player(h);var t=robot(h,new BlockPos(3,2,3),owner);var origin=t.getBlockPos();var facing=t.getDirection();
        h.getLevel().setBlockAndUpdate(origin.relative(facing),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var id=UUID.randomUUID();var request=new JsonObject();request.addProperty("op","programSave");request.addProperty("id",id.toString());request.addProperty("revision",0);request.add("program",program("受阻前進","forward"));
        RemoteService.open(owner,t);ProgramService.handle(owner,t,request);ProgramService.handle(owner,t,op("programRun",id));
        var again=new ServerPlayer[1];
        h.startSequence().thenWaitUntil(()->h.assertTrue(status(t).equals("error"),"Blocked program did not end in error: "+t.runtime().json()))
        .thenExecute(()->{
            h.getLevel().getServer().getPlayerList().remove(owner);
            again[0]=reconnect(h,owner.getGameProfile());var center=origin.getCenter();again[0].teleportTo(center.x,center.y,center.z);
            RemoteService.open(again[0],t);h.assertTrue(RemoteService.session(again[0],t.identity()),"Reconnected student could not reopen the controller");
            try{ProgramService.handle(again[0],t,op("programStop",id));}catch(Exception e){throw new GameTestAssertException("Reconnected student could not stop the program: "+e.getMessage());}
            h.assertTrue(status(t).equals("stopped"),"Stop left the program in "+status(t));
            RemoteService.handle(again[0],new RemoteNetwork.Action(t.identity(),"turnRight",0,""));
        }).thenWaitUntil(()->h.assertTrue(t.getDirection()==facing.getClockWise(),"Remote turn after reconnecting did not run; runtime "+t.runtime().json()))
        .thenExecute(()->{h.assertTrue(status(t).equals("stopped"),"Remote turn changed the runtime to "+status(t));h.getLevel().getServer().getPlayerList().remove(again[0]);}).thenSucceed();
    }
}
