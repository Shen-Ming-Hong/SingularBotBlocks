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
        }catch(java.io.IOException error){turtle.historyDiagnostic("Default robot appearance could not be saved: "+error.getMessage());}
    }
    /** Fresh items only fill what they do not already carry, e.g. an anvil name wins over the saved name. */
    static void applyLook(JuniorTurtleEntity turtle,java.util.UUID owner){
        if(!(turtle.getLevel() instanceof ServerLevel level))return;
        try{
            var look=appearance(level).get(owner);
            if(look.label()!=null&&turtle.getLabel()==null)turtle.setLabel(look.label());
            if(look.colour()!=null&&turtle.getAccess().getColour()==-1)turtle.getAccess().setColour(look.colour());
            if(look.decoration()!=null&&turtle.decoration().equals("none"))turtle.decorate(look.decoration());
        }catch(Exception error){turtle.historyDiagnostic("Default robot appearance could not be applied: "+error.getMessage());}
    }
    static void record(JuniorTurtleEntity turtle,String kind,String message){
        if(!(turtle.getLevel() instanceof ServerLevel level)||turtle.getAccess().getOwningPlayer()==null)return;
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
