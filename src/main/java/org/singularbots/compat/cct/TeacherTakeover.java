package org.singularbots.compat.cct;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.singularbots.network.ProgramNetwork;
import java.util.*;

/** A temporary single writer; robot visibility and the persistent teacher lock remain separate. */
@EventBusSubscriber(modid="singularbots")
public final class TeacherTakeover {
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static long epoch;
    static final String BUSY="A teacher is demonstrating; editing is temporarily paused";
    private static final class Session {
        final UUID token=UUID.randomUUID(),library,robot;
        final ServerPlayer teacher;
        final RemoteMenu menu;
        final long started=System.currentTimeMillis();
        boolean preparing=true;
        Session(ServerPlayer teacher,JuniorTurtleEntity robot){this.teacher=teacher;this.robot=robot.identity();library=robot.libraryId();menu=(RemoteMenu)teacher.containerMenu;}
    }
    public static JsonObject state(ServerPlayer player,JuniorTurtleEntity robot){
        var owner=robot.getAccess().getOwningPlayer();var library=owner==null?null:owner.getId();var s=SESSIONS.get(library);var j=new JsonObject();boolean mine=s!=null&&s.teacher==player;
        j.addProperty("epoch",epoch);j.addProperty("library",library==null?"":library.toString());j.addProperty("robot",s==null?"":s.robot.toString());
        j.addProperty("canTakeover",library!=null&&player.hasPermissions(2)&&!player.getUUID().equals(library)&&(s==null||mine));
        j.addProperty("drain",s!=null&&s.preparing&&player.getUUID().equals(s.library));
        j.addProperty("phase",s==null?"":s.preparing?"preparing":"active");j.addProperty("mine",mine);
        j.addProperty("teacher",s==null?"":s.teacher.getGameProfile().getName());j.addProperty("token",s==null?"":s.token.toString());
        j.addProperty("writeBlocked",s!=null&&(!mine||s.preparing));
        j.addProperty("controlBlocked",s!=null&&s.robot.equals(robot.identity())&&(!mine||s.preparing));return j;
    }
    public static void start(ServerPlayer teacher,JuniorTurtleEntity robot){
        if(!teacher.hasPermissions(2)||teacher.getUUID().equals(robot.libraryId()))throw new IllegalArgumentException("Teacher permission required");
        if(SESSIONS.containsKey(robot.libraryId()))throw new IllegalArgumentException(BUSY);
        var s=new Session(teacher,robot);SESSIONS.put(s.library,s);epoch++;
        if(teacher.getServer().getPlayerList().getPlayer(s.library)==null)s.preparing=false;
        notify(s,"");
    }
    public static void release(ServerPlayer teacher,JuniorTurtleEntity robot){
        var s=SESSIONS.get(robot.libraryId());if(s==null||s.teacher!=teacher||!s.robot.equals(robot.identity()))throw new IllegalArgumentException("Teacher permission required");
        end(s,"");
    }
    public static void acknowledge(ServerPlayer student,ProgramNetwork.TakeoverAck ack){
        var s=SESSIONS.get(student.getUUID());
        if(s==null||!s.preparing||!s.token.equals(ack.token()))return;
        if(System.currentTimeMillis()-s.started>=20000||!valid(s)||!ack.saved()||ProgramService.uploading(student.getUUID())){end(s,"Takeover cancelled; the student's edits could not be saved");return;}
        s.preparing=false;epoch++;notify(s,"");
    }
    public static boolean canWrite(ServerPlayer player,UUID library){var s=SESSIONS.get(library);return s==null||s.teacher==player&&!s.preparing;}
    public static void write(ServerPlayer player,UUID library){if(!canWrite(player,library))throw new IllegalArgumentException(BUSY);}
    public static boolean canControl(ServerPlayer player,JuniorTurtleEntity robot){if(robot.getAccess().getOwningPlayer()==null)return true;var s=SESSIONS.get(robot.libraryId());return s==null||!s.robot.equals(robot.identity())||s.teacher==player&&!s.preparing;}
    public static void control(ServerPlayer player,JuniorTurtleEntity robot){if(!canControl(player,robot))throw new IllegalArgumentException(BUSY);}
    static void program(ServerPlayer player,JuniorTurtleEntity robot,String op){
        if(Set.of("programSave","programDelete").contains(op)){
            var s=SESSIONS.get(robot.libraryId());
            // During preparation only the owner's final queued saves may drain; teachers cannot edit until the acknowledgement.
            if(!(op.equals("programSave")&&s!=null&&s.preparing&&player.getUUID().equals(s.library)))write(player,robot.libraryId());
        }
        if(Set.of("programLoad","programRun","programStep","programPause","programResume","programStop").contains(op))control(player,robot);
    }
    private static boolean valid(Session s){
        var p=s.teacher;var robot=RemoteService.resolve(p.serverLevel(),s.robot);
        return p.getServer().getPlayerList().getPlayer(p.getUUID())==p&&p.hasPermissions(2)&&p.containerMenu==s.menu&&robot!=null&&robot.getAccess().getOwningPlayer()!=null&&robot.libraryId().equals(s.library)&&RemoteService.authorized(p,robot);
    }
    private static void notify(Session s,String error){
        for(var p:s.teacher.getServer().getPlayerList().getPlayers()){
            JuniorTurtleEntity robot=null;
            if(p.containerMenu instanceof RemoteMenu menu)robot=RemoteService.resolve(p.serverLevel(),menu.turtleId);
            if(!p.getUUID().equals(s.library)&&p!=s.teacher&&(robot==null||robot.getAccess().getOwningPlayer()==null||!robot.libraryId().equals(s.library)))continue;
            var current=robot!=null&&robot.getAccess().getOwningPlayer()!=null&&robot.libraryId().equals(s.library)?robot:RemoteService.resolve(s.teacher.serverLevel(),s.robot);
            JsonObject j;
            if(current!=null)j=state(p,current);else{j=new JsonObject();j.addProperty("epoch",epoch);j.addProperty("phase","");j.addProperty("library",s.library.toString());j.addProperty("writeBlocked",false);j.addProperty("controlBlocked",false);}
            j.addProperty("error",error);
            if(p.connection.hasChannel(ProgramNetwork.TakeoverNotice.TYPE))PacketDistributor.sendToPlayer(p,new ProgramNetwork.TakeoverNotice(s.library,j.toString()));
        }
    }
    private static void end(Session s,String error){if(SESSIONS.remove(s.library,s)){epoch++;notify(s,error);}}
    static void closed(ServerPlayer player,RemoteMenu menu){for(var s:List.copyOf(SESSIONS.values()))if(s.teacher==player&&s.menu==menu)end(s,"");}
    static void check(UUID library,long now){var s=SESSIONS.get(library);if(s==null)return;if(!valid(s))end(s,"");else if(s.preparing&&now-s.started>=20000)end(s,"Takeover cancelled; the student's edits could not be saved");}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){for(var library:List.copyOf(SESSIONS.keySet()))check(library,System.currentTimeMillis());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){for(var s:List.copyOf(SESSIONS.values()))if(s.teacher==e.getEntity()||s.preparing&&s.library.equals(e.getEntity().getUUID()))end(s,"");}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){SESSIONS.clear();epoch=0;}
}
