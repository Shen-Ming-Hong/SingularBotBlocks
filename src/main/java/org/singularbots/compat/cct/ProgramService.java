package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.singularbots.program.*;
import org.singularbots.network.ProgramNetwork;
import java.util.*;

@EventBusSubscriber(modid="singularbots")
public final class ProgramService {
    private static final Map<UUID,Fragments> UPLOADS=new HashMap<>();
    private static final Map<UUID,RequestRate> REQUEST_RATE=new HashMap<>();
    /** Latest saved revision per library program, so editors open on the same robot (a teacher guiding a student) can follow each other without reading files on every poll. */
    private static final Map<String,Long> REVISIONS=new HashMap<>();
    public static ProgramStore store(ServerLevel level){return new ProgramStore(level.getServer().getWorldPath(LevelResource.ROOT).resolve("singularbots/students"));}
    public static void fragment(ServerPlayer player,ProgramNetwork.Fragment part){
        try{
            var t=RemoteService.resolve(player.serverLevel(),part.turtle());
            if(t==null||!RemoteService.session(player,part.turtle())||!RemoteService.authorized(player,t))throw new IllegalArgumentException("Control permission expired");
            long time=System.currentTimeMillis();var upload=UPLOADS.get(player.getUUID());
            if(upload!=null&&time-upload.started>20000){UPLOADS.remove(player.getUUID());upload=null;}
            if(part.index()==0){
                if(!REQUEST_RATE.computeIfAbsent(player.getUUID(),k->new RequestRate(time)).allow(time))throw new IllegalArgumentException("Too many program requests");
                if(upload!=null)throw new IllegalArgumentException("Another upload is in progress");
                upload=new Fragments(part.request(),part.turtle(),part.total(),time);UPLOADS.put(player.getUUID(),upload);
            }
            if(upload==null||!upload.request.equals(part.request())||!upload.turtle.equals(part.turtle()))throw new IllegalArgumentException("Unknown upload");
            String text=upload.append(part.index(),part.total(),part.data());if(text==null)return;UPLOADS.remove(player.getUUID());
            var result=handle(player,t,JsonParser.parseString(text).getAsJsonObject());result.addProperty("ok",true);reply(player,part,result);
        }catch(Exception e){UPLOADS.remove(player.getUUID());var error=new JsonObject();error.addProperty("ok",false);error.addProperty("error",Objects.toString(e.getMessage(),"Invalid request"));if(e instanceof WorkspaceCompiler.Invalid invalid)error.addProperty("blockId",invalid.blockId);reply(player,part,error);}
    }
    public static JsonObject handle(ServerPlayer player,JuniorTurtleEntity t,JsonObject request)throws Exception{
        if(!RemoteService.session(player,t.identity())||!RemoteService.authorized(player,t))throw new IllegalArgumentException("Control permission expired");
        var s=store(player.serverLevel());var result=new JsonObject();String op=request.get("op").getAsString();
        TeacherTakeover.program(player,t,op);
        var settings=TeachingSettings.get(player.getServer());
        if(!settings.visualEditor()&&op.equals("programSave")&&request.getAsJsonObject("program").get("mode").getAsString().equals("blocks"))throw new IllegalArgumentException("Teacher disabled Blockly editing; Lua programs remain available");
        boolean lua=settings.luaAllowed();
        if(!lua&&(op.equals("programCompile")||op.equals("programSave")&&isLua(request.getAsJsonObject("program"))))throw new IllegalArgumentException(LUA_DISABLED);
        // Covers teacher delivery too: the owner treats any program copying between students as a cheating risk.
        if(!settings.sharing()&&java.util.Set.of("programTargets","programShare").contains(op))throw new IllegalArgumentException("Teacher disabled program sharing in this world");
        if(!settings.allowed()&&java.util.Set.of("programRun","programStep","programResume").contains(op))throw new IllegalArgumentException("Teacher stopped or disabled turtles");
        switch(op){
            case "programTakeover" -> {if(request.get("enabled").getAsBoolean())TeacherTakeover.start(player,t);else TeacherTakeover.release(player,t);}
            case "programHistory" -> result.add("records",StudentPrograms.history(player.serverLevel()).list(t.libraryId()));
            case "programList" -> {result.add("programs",visible(s.list(t.libraryId()),lua));policy(result,settings,player);result.addProperty("selected",t.selectedProgram()==null?"":t.selectedProgram().toString());result.addProperty("visualEditor",settings.visualEditor());}
            case "programLoad" -> {result.add("program",visible(s.load(t.libraryId(),id(request)),lua));t.selectProgram(id(request));}
            case "programInspect" -> result.add("program",visible(s.load(t.libraryId(),id(request)),lua));
            case "programSave" -> {var saved=s.save(t.libraryId(),id(request),request.get("revision").getAsLong(),request.getAsJsonObject("program"));result.add("program",saved);REVISIONS.put(t.libraryId()+"/"+id(request),saved.get("revision").getAsLong());t.selectProgram(id(request));try{ProgramStore.compile(saved);}catch(WorkspaceCompiler.Invalid invalid){result.addProperty("compileError",invalid.getMessage());result.addProperty("blockId",invalid.blockId);}}
            case "programDelete" -> {s.delete(t.libraryId(),id(request),request.get("revision").getAsLong());if(id(request).equals(t.selectedProgram()))t.selectProgram(null);}
            case "programCompile" -> {var compiled=ProgramStore.compile(s.load(t.libraryId(),id(request)),false);result.addProperty("lua",compiled.lua());}
            case "programRun","programStep" -> {
                if(op.equals("programStep")&&t.runtime().json().get("status").getAsString().equals("paused")){t.runtime().step();wake(t);}
                else {var p=visible(s.load(t.libraryId(),id(request)),lua);var compiled=ProgramStore.compile(p);t.selectProgram(id(request));stop(t);t.runtime().prepare(compiled,op.equals("programStep"),id(request).toString(),p.get("name").getAsString());var computer=t.createServerComputer();computer.turnOn();computer.queueEvent("ccedu_run");}
            }
            case "programPause" -> t.runtime().pause();
            case "programResume" -> {t.runtime().running();wake(t);}
            case "programStop" -> stop(t);
            case "programRobots" -> result=RobotFleet.list(player,t);
            case "programLocate" -> result=RobotFleet.locate(player,t,request);
            case "programRecall" -> result=RobotFleet.recall(player,t,request);
            case "programState" -> {result.add("runtime",t.runtime().json());result.addProperty("selected",t.selectedProgram()==null?"":t.selectedProgram().toString());if(request.has("id")){var revision=REVISIONS.get(t.libraryId()+"/"+request.get("id").getAsString());if(revision!=null)result.addProperty("revision",revision);}result.addProperty("suggestions",settings.suggestions());policy(result,settings,player);}
            default -> result=ProgramTransfer.handle(player,t,request);
        }
        if(java.util.Set.of("programSave","programDelete","programRun","programStep","programShare").contains(op))StudentPrograms.record(t,op,request.has("id")?request.get("id").getAsString():"");
        result.addProperty("library",t.libraryId().toString());result.add("takeover",TeacherTakeover.state(player,t));
        result.addProperty("historyDiagnostic",t.historyDiagnostic());
        return result;
    }
    static final String LUA_DISABLED="Teacher disabled Lua programs in this world; use block programs";
    static boolean isLua(JsonObject p){return p.get("mode").getAsString().equals("lua");}
    /** Beginner worlds hide existing Lua programs instead of opening their source. */
    static JsonObject visible(JsonObject p,boolean lua){if(!lua&&isLua(p))throw new IllegalArgumentException(LUA_DISABLED);return p;}
    static JsonArray visible(JsonArray rows,boolean lua){if(lua)return rows;var blocks=new JsonArray();for(var row:rows)if(!isLua(row.getAsJsonObject()))blocks.add(row);return blocks;}
    // Only teachers see the send controls: students may never copy programs to each other.
    private static void policy(JsonObject result,TeachingSettings settings,ServerPlayer player){result.addProperty("visualEditor",settings.visualEditor());result.addProperty("lua",settings.luaAllowed());result.addProperty("sharing",settings.sharing()&&player.hasPermissions(2));}
    private static UUID id(JsonObject j){return UUID.fromString(j.get("id").getAsString());}
    private static void wake(JuniorTurtleEntity t){var computer=t.getServerComputer();if(computer!=null)computer.queueEvent("ccedu_debug");}
    public static void stop(JuniorTurtleEntity t){t.runtime().stop();var computer=t.getServerComputer();if(computer!=null)computer.shutdown();}
    private static void reply(ServerPlayer player,ProgramNetwork.Fragment part,JsonObject result){var parts=Fragments.split(result.toString());for(int i=0;i<parts.size();i++)PacketDistributor.sendToPlayer(player,new ProgramNetwork.Reply(part.turtle(),part.request(),i,parts.size(),parts.get(i)));}
    /** Lets GameTests confirm that a disconnect discards a partial upload. */
    static boolean uploading(UUID player){return UPLOADS.containsKey(player);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){UPLOADS.remove(e.getEntity().getUUID());REQUEST_RATE.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){UPLOADS.clear();REQUEST_RATE.clear();REVISIONS.clear();ProgramStore.clearCache();org.singularbots.program.HistoryStore.clearCache();StudentPrograms.clearRecentSaves();}
}
