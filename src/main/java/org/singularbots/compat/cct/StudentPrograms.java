package org.singularbots.compat.cct;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.singularbots.program.HistoryStore;
import java.nio.file.*;

/** Server-derived student IDs; neither requests nor copied robot items can choose a library. */
public final class StudentPrograms {
    private StudentPrograms(){}
    public static HistoryStore history(ServerLevel level){return new HistoryStore(level.getServer().getWorldPath(LevelResource.ROOT).resolve("singularbots/history"));}
    public static org.singularbots.program.AppearanceStore appearance(ServerLevel level){return new org.singularbots.program.AppearanceStore(level.getServer().getWorldPath(LevelResource.ROOT).resolve("singularbots/appearance"));}
    /** The owner's latest name/colour/decoration choice becomes the default for robots they place later. */
    static void rememberLook(JuniorTurtleEntity turtle,String field){
        if(!(turtle.getLevel() instanceof ServerLevel level)||turtle.getAccess().getOwningPlayer()==null)return;
        try{
            var store=appearance(level);var owner=turtle.libraryId();
            switch(field){
                case "name" -> {var label=turtle.getLabel();store.setLabel(owner,label==null?"":label);}
                case "colour" -> {int colour=turtle.getAccess().getColour();if(colour!=-1)store.setColour(owner,colour);}
                case "decoration" -> store.setDecoration(owner,turtle.decoration());
                default -> throw new IllegalArgumentException("Unknown appearance field");
            }
            var player=level.getServer().getPlayerList().getPlayer(owner);if(player!=null)shareLook(player);
        }catch(java.io.IOException error){turtle.historyDiagnostic("Default robot appearance could not be saved: "+error.getMessage());}
    }
    /** The placer's saved look replaces whatever the robot had; a field they never set falls back to the plain robot, except that an item's own (e.g. anvil) name stays when they have no saved name. */
    static void applyLook(JuniorTurtleEntity turtle,java.util.UUID owner){
        if(!(turtle.getLevel() instanceof ServerLevel level))return;
        try{
            var look=appearance(level).get(owner);
            if(look.label()!=null)turtle.setLabel(look.label());
            turtle.getAccess().setColour(look.colour()==null?-1:look.colour());
            turtle.decorate(look.decoration()==null?"none":look.decoration());
        }catch(Exception error){turtle.historyDiagnostic("Default robot appearance could not be applied: "+error.getMessage());}
    }
    /** Robot items carry no look, so clients draw one held by a player with that player's saved look; every client learns every online player's look. */
    private static org.singularbots.network.RemoteNetwork.Look look(net.minecraft.server.level.ServerPlayer player){
        var look=org.singularbots.program.AppearanceStore.Look.NONE;
        try{look=appearance(player.serverLevel()).get(player.getUUID());}catch(Exception ignored){/* An unreadable file shows the plain robot; placement reports the error. */}
        return new org.singularbots.network.RemoteNetwork.Look(player.getUUID(),look.label()==null?"":look.label(),look.colour()==null?-1:look.colour(),look.decoration()==null?"none":look.decoration());
    }
    private static void send(net.minecraft.server.level.ServerPlayer to,org.singularbots.network.RemoteNetwork.Look look){
        if(to.connection.hasChannel(org.singularbots.network.RemoteNetwork.Look.TYPE))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(to,look);
    }
    /** Tells every online player about this player's look. */
    public static void shareLook(net.minecraft.server.level.ServerPlayer player){
        var look=look(player);for(var to:player.server.getPlayerList().getPlayers())send(to,look);
    }
    /** A joining player shares their look and learns everyone else's. */
    public static void joined(net.minecraft.server.level.ServerPlayer player){
        shareLook(player);for(var other:player.server.getPlayerList().getPlayers())if(other!=player)send(player,look(other));
    }
    /** Auto-save writes several times a second while a student edits; one save entry per program and robot each minute keeps the bounded history for real activity. */
    private static final java.util.Map<String,Long> RECENT_SAVES=new java.util.concurrent.ConcurrentHashMap<>();
    static final long SAVE_RECORD_MILLIS=60_000;
    static void clearRecentSaves(){RECENT_SAVES.clear();}
    static void record(JuniorTurtleEntity turtle,String kind,String message){
        if(!(turtle.getLevel() instanceof ServerLevel level)||turtle.getAccess().getOwningPlayer()==null)return;
        if(kind.equals("programSave")){
            long now=System.currentTimeMillis();var key=turtle.libraryId()+"/"+turtle.identity()+"/"+message;var last=RECENT_SAVES.get(key);
            if(last!=null&&now-last<SAVE_RECORD_MILLIS)return;RECENT_SAVES.put(key,now);
        }
        try{
            var entry=new JsonObject();entry.addProperty("time",java.time.Instant.now().toString());entry.addProperty("kind",kind);
            entry.addProperty("robot",turtle.identity().toString());entry.addProperty("name",java.util.Objects.toString(turtle.getLabel(),""));
            entry.addProperty("dimension",level.dimension().location().toString());entry.addProperty("x",turtle.getBlockPos().getX());entry.addProperty("y",turtle.getBlockPos().getY());entry.addProperty("z",turtle.getBlockPos().getZ());
            if(turtle.selectedProgram()!=null)entry.addProperty("program",turtle.selectedProgram().toString());
            var runtime=turtle.runtime().json();entry.addProperty("status",runtime.get("status").getAsString());
            if(runtime.has("blockId"))entry.add("blockId",runtime.get("blockId"));
            entry.addProperty("message",message==null?"":message.substring(0,Math.min(message.length(),1024)));
            history(level).append(turtle.libraryId(),entry);turtle.historyDiagnostic("");
        }catch(Exception error){turtle.historyDiagnostic("Activity history could not be saved; programs remain stored: "+error.getMessage());}
    }
    /** Old development-world files stay intact. Conflicts are reported, never overwritten. */
    static void migrateLegacy(JuniorTurtleEntity turtle)throws java.io.IOException{
        var level=(ServerLevel)turtle.getLevel();var base=level.getServer().getWorldPath(LevelResource.ROOT).resolve("singularbots");
        var source=base.resolve("programs").resolve(turtle.identity().toString());
        if(!Files.isDirectory(source))return;
        var destination=base.resolve("students").resolve(turtle.libraryId().toString());
        Files.createDirectories(destination);
        try(var files=Files.list(source)){
            var programs=files.filter(p->p.getFileName().toString().endsWith(".json")||p.getFileName().toString().endsWith(".deleted")).toList();
            var names=new java.util.HashMap<String,String>();
            for(var row:ProgramService.store(level).list(turtle.libraryId())){var data=row.getAsJsonObject();names.put(data.get("name").getAsString(),data.get("id").getAsString());}
            for(var file:programs)if(file.getFileName().toString().endsWith(".json")){var data=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();String id=data.get("id").getAsString(),name=data.get("name").getAsString();if(names.containsKey(name)&&!id.equals(names.get(name)))throw new java.io.IOException("Legacy program-name conflict; original library preserved at "+source);names.put(name,id);}
            for(var file:programs){var target=destination.resolve(file.getFileName());if(Files.exists(target)&&Files.mismatch(file,target)!=-1)throw new java.io.IOException("Legacy library conflict; original files preserved at "+source);}
            for(var file:programs){var target=destination.resolve(file.getFileName());if(Files.exists(target))continue;var temp=Files.createTempFile(destination,"migrate-",".tmp");try{Files.copy(file,temp,StandardCopyOption.REPLACE_EXISTING);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}
        }
    }
}
