package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import org.singularbots.program.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Students never copy programs to each other; only OP2 teachers may send into another student's library. */
public final class ProgramTransfer {
    static final String STUDENT_SHARING="Students cannot share programs with each other; only a teacher can send programs";
    public static JsonObject handle(ServerPlayer player,JuniorTurtleEntity turtle,JsonObject request)throws Exception{
        var result=new JsonObject();var store=ProgramService.store(player.serverLevel());String op=request.get("op").getAsString();boolean luaAllowed=TeachingSettings.get(player.getServer()).luaAllowed();
        if(!Set.of("programTargets","programShare").contains(op))throw new IllegalArgumentException("Unknown transfer operation");
        if(!player.hasPermissions(2))throw new IllegalArgumentException(STUDENT_SHARING);
        switch(op){
            case "programTargets" -> {var rows=new JsonArray();var seen=new HashSet<UUID>();for(var t:RemoteService.loaded(player.serverLevel()))if(t.getAccess().getOwningPlayer()!=null&&!t.libraryId().equals(turtle.libraryId())&&player.position().distanceToSqr(t.getBlockPos().getCenter())<=4096&&seen.add(t.libraryId())){var j=new JsonObject();j.addProperty("id",t.libraryId().toString());j.addProperty("name",t.getAccess().getOwningPlayer().getName());rows.add(j);}result.add("targets",rows);result.addProperty("teacher",true);}
            default -> {
                var source=blocksOnly(bundle(store,turtle.libraryId(),UUID.fromString(request.get("id").getAsString())),luaAllowed);String target=request.get("target").getAsString();boolean all=target.equals("*");int count=0;
                var seen=new HashSet<UUID>();
                for(var receiver:RemoteService.loaded(player.serverLevel())){
                    if(receiver.getAccess().getOwningPlayer()==null||receiver.libraryId().equals(turtle.libraryId())||(!all&&!receiver.libraryId().toString().equals(target)))continue;
                    if(player.position().distanceToSqr(receiver.getBlockPos().getCenter())>4096||!seen.add(receiver.libraryId()))continue;
                    install(store,receiver.libraryId(),source.deepCopy(),"Teacher "+player.getGameProfile().getName()+": "+source.get("name").getAsString());count++;
                }
                if(count==0)throw new IllegalArgumentException("No eligible loaded recipient turtle");result.addProperty("recipients",count);
            }
        }return result;
    }
    /** A beginner world must not receive Lua through teacher delivery, including as a library dependency. */
    private static JsonObject blocksOnly(JsonObject bundle,boolean lua){
        ProgramService.visible(bundle,lua);if(!lua&&bundle.has("dependencies"))for(var dependency:bundle.getAsJsonArray("dependencies"))ProgramService.visible(dependency.getAsJsonObject(),false);return bundle;
    }
    private static JsonObject bundle(ProgramStore store,UUID turtle,UUID id)throws Exception{
        var root=store.load(turtle,id).deepCopy();root.remove("dependencies");ProgramStore.compile(root,false);
        var index=new LinkedHashMap<String,UUID>();for(var entry:store.list(turtle)){var row=entry.getAsJsonObject();index.put(row.get("name").getAsString(),UUID.fromString(row.get("id").getAsString()));}
        var dependencies=new JsonArray();var seen=new HashSet<String>();var pending=new ArrayDeque<String>();references(root,index.keySet(),pending);int bytes=root.toString().getBytes(StandardCharsets.UTF_8).length;
        while(!pending.isEmpty()){
            String name=pending.remove();if(!seen.add(name))continue;var depId=index.get(name);if(depId==null)throw new IllegalArgumentException("Missing library dependency: "+name);
            var dependency=store.load(turtle,depId).deepCopy();dependency.remove("dependencies");ProgramStore.compile(dependency,false);bytes+=dependency.toString().getBytes(StandardCharsets.UTF_8).length;
            if(bytes>ProgramStore.MAX_BYTES-4096)throw new IllegalArgumentException("Program and dependencies exceed 1 MiB; export a smaller library");dependencies.add(dependency);references(dependency,index.keySet(),pending);
        }root.add("dependencies",dependencies);return root;
    }
    private static void references(JsonObject p,Set<String> library,Deque<String> names){
        // Raw Lua may choose a name at runtime, so preserve its whole available library.
        if(p.get("mode").getAsString().equals("lua"))names.addAll(library);else visitCalls(p.getAsJsonObject("workspace"),name->{names.add(name);return name;});
    }
    private static void visitCalls(JsonElement value,java.util.function.UnaryOperator<String> rename){
        if(value.isJsonObject()){var j=value.getAsJsonObject();if(j.has("type")&&j.get("type").getAsString().equals("ccedu_call")){var fields=j.getAsJsonObject("fields");fields.addProperty("NAME",rename.apply(fields.get("NAME").getAsString()));}for(var entry:j.entrySet())visitCalls(entry.getValue(),rename);}
        else if(value.isJsonArray())for(var entry:value.getAsJsonArray())visitCalls(entry,rename);
    }
    private static JsonObject install(ProgramStore store,UUID turtle,JsonObject bundle,String rootName)throws Exception{
        ProgramStore.validateContent(bundle);
        var dependencies=bundle.has("dependencies")?bundle.getAsJsonArray("dependencies"):new JsonArray();if(dependencies.size()>ProgramStore.MAX_PROGRAMS)throw new IllegalArgumentException("Too many dependencies");
        var existing=new HashMap<String,JsonObject>();for(var row:store.list(turtle)){var r=row.getAsJsonObject();existing.put(r.get("name").getAsString(),store.load(turtle,UUID.fromString(r.get("id").getAsString())));}
        var occupied=new HashSet<>(existing.keySet());var renames=new HashMap<String,String>();var imports=new ArrayList<JsonObject>();var reused=new ArrayList<JsonObject>();boolean raw=bundle.get("mode").getAsString().equals("lua");
        for(var entry:dependencies){var p=entry.getAsJsonObject();ProgramStore.validateContent(p);if(p.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported dependency data version");ProgramStore.compile(p,false);String name=p.get("name").getAsString();if(renames.containsKey(name))throw new IllegalArgumentException("Duplicate dependency name");raw|=p.get("mode").getAsString().equals("lua");var old=existing.get(name);
            String field=p.get("mode").getAsString().equals("blocks")?"workspace":"source";
            if(old!=null&&old.get("mode").equals(p.get("mode"))&&old.get(field).equals(p.get(field))){renames.put(name,name);reused.add(p);continue;}
            String chosen=name.strip();int n=2;while(occupied.contains(chosen))chosen=limit(name)+" ("+n+++")";occupied.add(chosen);renames.put(name,chosen);imports.add(p.deepCopy());
        }
        // An identical parent cannot be reused if a descendant was renamed: its old calls
        // would still resolve to the recipient's different library. Propagate through cycles.
        boolean changed;
        do{changed=false;for(var iterator=reused.iterator();iterator.hasNext();){var p=iterator.next();if(!p.get("mode").getAsString().equals("blocks"))continue;var affected=new boolean[]{false};visitCalls(p.get("workspace").deepCopy(),name->{if(!name.equals(renames.getOrDefault(name,name)))affected[0]=true;return name;});if(!affected[0])continue;
            String name=p.get("name").getAsString(),chosen=name;int suffix=2;while(occupied.contains(chosen))chosen=limit(name)+" ("+suffix+++")";occupied.add(chosen);renames.put(name,chosen);imports.add(p.deepCopy());iterator.remove();changed=true;
        }}while(changed);
        if(raw&&renames.entrySet().stream().anyMatch(e->!e.getKey().equals(e.getValue())))throw new IllegalArgumentException("Lua library name conflict; rename existing programs before importing");
        if(existing.size()+imports.size()+1>ProgramStore.MAX_PROGRAMS)throw new IllegalArgumentException("Not enough library space for program dependencies");
        var root=bundle.deepCopy();root.remove("dependencies");String base=limit(rootName.strip()),chosen=base;int n=2;while(occupied.contains(chosen))chosen=base+" ("+n+++")";root.addProperty("name",chosen);
        for(var p:imports){p.addProperty("name",renames.get(p.get("name").getAsString()));if(p.get("mode").getAsString().equals("blocks"))visitCalls(p.get("workspace"),name->renames.getOrDefault(name,name));}
        if(root.get("mode").getAsString().equals("blocks"))visitCalls(root.get("workspace"),name->renames.getOrDefault(name,name));ProgramStore.compile(root,false);
        imports.add(root);var installed=store.createBatch(turtle,imports);return installed.getLast();
    }
    private static String limit(String requested){return requested.codePointCount(0,requested.length())>56?requested.substring(0,requested.offsetByCodePoints(0,56)):requested;}
}
