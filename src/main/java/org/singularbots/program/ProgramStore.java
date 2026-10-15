package org.singularbots.program;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-student files in the world directory. The server derives the namespace from robot ownership. */
public final class ProgramStore {
    public static final int MAX_BYTES=1024*1024,MAX_PROGRAMS=128;
    private final Path root;
    /** Library rows by file, reused while the file is unchanged: editors list the library after every auto-save, and re-parsing every program each time grows with library size. */
    private static final Map<Path,Map<Path,Row>> ROWS=new ConcurrentHashMap<>();
    private record Row(Object key,FileTime modified,long size,JsonObject row){
        boolean matches(BasicFileAttributes a){return Objects.equals(key,a.fileKey())&&modified.equals(a.lastModifiedTime())&&size==a.size();}
    }
    public static void clearCache(){ROWS.clear();}
    @FunctionalInterface interface Publisher { void move(Path from,Path to)throws IOException; }
    private final Publisher publisher;
    public ProgramStore(Path root){this(root,(from,to)->Files.move(from,to,StandardCopyOption.ATOMIC_MOVE));}
    ProgramStore(Path root,Publisher publisher){this.root=root;this.publisher=publisher;}
    private Path path(UUID turtle,UUID program){return root.resolve(turtle.toString()).resolve(program+".json");}
    public JsonObject load(UUID turtle,UUID id)throws IOException{
        recoverImport(turtle);
        var file=path(turtle,id);if(!Files.exists(file))throw new IllegalArgumentException("Program not found");
        if(Files.size(file)>MAX_BYTES)throw new IOException("Program file exceeds limit");
        var program=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(!program.has("version")||program.get("version").getAsInt()!=1)throw new IOException("Unsupported program data version; original file has been preserved");
        if(!id.toString().equals(program.get("id").getAsString()))throw new IOException("Program identity does not match its file");
        return program;
    }
    public JsonArray list(UUID turtle)throws IOException{
        recoverImport(turtle);
        var dir=root.resolve(turtle.toString());var result=new JsonArray();if(!Files.isDirectory(dir))return result;
        var previous=ROWS.getOrDefault(dir,Map.of());var current=new HashMap<Path,Row>();
        try(var files=Files.list(dir)){
            for(var file:files.filter(p->p.getFileName().toString().endsWith(".json")).sorted().limit(MAX_PROGRAMS).toList()){
                var attributes=Files.readAttributes(file,BasicFileAttributes.class);var cached=previous.get(file);
                if(cached==null||!cached.matches(attributes)){
                    var program=load(turtle,UUID.fromString(file.getFileName().toString().replace(".json","")));var row=new JsonObject();
                    for(var key:List.of("id","name","mode","revision"))row.add(key,program.get(key));
                    cached=new Row(attributes.fileKey(),attributes.lastModifiedTime(),attributes.size(),row);
                }
                current.put(file,cached);result.add(cached.row().deepCopy());
            }
        }
        // Only files still present are kept, so deleted programs do not linger.
        ROWS.put(dir,Map.copyOf(current));return result;
    }
    public JsonObject save(UUID turtle,UUID id,long expected,JsonObject input)throws IOException{
        var file=path(turtle,id);long revision=Files.exists(file)?load(turtle,id).get("revision").getAsLong():0;
        if(expected!=revision)throw new IllegalStateException("Version conflict: reload before saving");
        validateContent(input);
        var library=list(turtle);
        if(revision==0&&library.size()>=MAX_PROGRAMS)throw new IllegalArgumentException("Program library is full");
        String name=input.get("name").getAsString().strip(),mode=input.get("mode").getAsString();
        if(name.isEmpty()||name.codePointCount(0,name.length())>64||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid program name");
        for(var entry:library){var row=entry.getAsJsonObject();if(row.get("name").getAsString().equals(name)&&!row.get("id").getAsString().equals(id.toString()))throw new IllegalArgumentException("A program already has that name");}
        var result=new JsonObject();result.addProperty("version",1);result.addProperty("id",id.toString());result.addProperty("name",name);result.addProperty("mode",mode);result.addProperty("revision",revision+1);
        if(mode.equals("blocks")){var ws=input.getAsJsonObject("workspace");WorkspaceCompiler.validateStructure(ws);result.add("workspace",ws.deepCopy());}
        else if(mode.equals("lua"))result.addProperty("source",input.get("source").getAsString());
        else throw new IllegalArgumentException("Unknown editing mode");
        if(input.has("dependencies")){
            var dependencies=new JsonArray();var names=new HashSet<String>();var incoming=input.getAsJsonArray("dependencies");
            if(incoming.size()>MAX_PROGRAMS)throw new IllegalArgumentException("Too many program dependencies");
            for(var value:incoming){var dependency=value.getAsJsonObject();String depName=dependency.get("name").getAsString(),depMode=dependency.get("mode").getAsString();
                if(dependency.get("version").getAsInt()!=1||depName.isBlank()||depName.codePointCount(0,depName.length())>64||depName.codePoints().anyMatch(Character::isISOControl)||!names.add(depName))throw new IllegalArgumentException("Invalid dependency");
                var copy=new JsonObject();copy.addProperty("version",1);copy.addProperty("name",depName);copy.addProperty("mode",depMode);
                if(depMode.equals("blocks")){var ws=dependency.getAsJsonObject("workspace");WorkspaceCompiler.validateStructure(ws);copy.add("workspace",ws.deepCopy());}
                else if(depMode.equals("lua"))copy.addProperty("source",dependency.get("source").getAsString());else throw new IllegalArgumentException("Unknown dependency mode");dependencies.add(copy);
            }result.add("dependencies",dependencies);
        }
        var bytes=result.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Program exceeds 1 MiB");
        Files.createDirectories(file.getParent());var tmp=Files.createTempFile(file.getParent(),"save-",".tmp");
        try{Files.write(tmp,bytes);Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}return result;
    }
    /** Imports only new programs. A durable marker rolls back a failed or interrupted publication. */
    public List<JsonObject> createBatch(UUID turtle,List<JsonObject> inputs)throws IOException{
        recoverImport(turtle);
        var dir=root.resolve(turtle.toString());Files.createDirectories(dir);
        var staging=Files.createTempDirectory(dir,"import-stage-");
        var stagedStore=new ProgramStore(staging);var result=new ArrayList<JsonObject>();var ids=new JsonArray();
        boolean marked=false;
        try{
            var occupied=new HashSet<String>();for(var row:list(turtle))occupied.add(row.getAsJsonObject().get("name").getAsString());
            if(occupied.size()+inputs.size()>MAX_PROGRAMS)throw new IllegalArgumentException("Program library is full");
            for(var input:inputs){
                validateContent(input);if(!occupied.add(input.get("name").getAsString().strip()))throw new IllegalArgumentException("A program already has that name");
                UUID id;do{id=UUID.randomUUID();}while(Files.exists(path(turtle,id)));
                result.add(stagedStore.save(turtle,id,0,input));ids.add(id.toString());
            }
            var marker=dir.resolve(".import-pending");var temporary=Files.createTempFile(dir,"import-marker-",".tmp");
            try{Files.writeString(temporary,ids.toString());Files.move(temporary,marker,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temporary);}
            marked=true;
            for(var program:result){var id=UUID.fromString(program.get("id").getAsString());publisher.move(stagedStore.path(turtle,id),path(turtle,id));}
            Files.delete(marker);marked=false;return result;
        }catch(IOException|RuntimeException error){
            if(marked)try{recoverImport(turtle);}catch(IOException rollback){error.addSuppressed(rollback);}
            throw error;
        }finally{
            // Staged files have never been part of the user's library.
            ROWS.keySet().removeIf(path->path.startsWith(staging));
            try(var files=Files.walk(staging)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}
            catch(IOException cleanup){/* Unpublished staging files may remain; never report a committed import as failed. */}
        }
    }
    private void recoverImport(UUID turtle)throws IOException{
        var marker=root.resolve(turtle.toString()).resolve(".import-pending");if(!Files.exists(marker))return;
        if(Files.size(marker)>MAX_PROGRAMS*40L)throw new IOException("Invalid interrupted import marker; library preserved");
        final List<UUID> ids=new ArrayList<>();
        try{var values=JsonParser.parseString(Files.readString(marker)).getAsJsonArray();if(values.size()>MAX_PROGRAMS)throw new IllegalArgumentException();for(var value:values)ids.add(UUID.fromString(value.getAsString()));}
        catch(RuntimeException invalid){throw new IOException("Invalid interrupted import marker; library preserved",invalid);}
        for(var id:ids)Files.deleteIfExists(path(turtle,id));Files.delete(marker);
    }
    public static void validateContent(JsonObject program){
        if(program.has("version")&&program.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported program data version");
        String name=program.get("name").getAsString().strip(),mode=program.get("mode").getAsString();
        if(name.isEmpty()||name.codePointCount(0,name.length())>64||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid program name");
        if(mode.equals("blocks"))WorkspaceCompiler.validateStructure(program.getAsJsonObject("workspace"));else if(mode.equals("lua"))program.get("source").getAsString();else throw new IllegalArgumentException("Unknown editing mode");
        if(program.toString().getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new IllegalArgumentException("Program exceeds 1 MiB");
    }
    public void delete(UUID turtle,UUID id,long expected)throws IOException{
        var current=load(turtle,id);if(current.get("revision").getAsLong()!=expected)throw new IllegalStateException("Version conflict: reload before deleting");
        // Retain a recoverable copy of deleted programs in the same world.
        var file=path(turtle,id);Files.move(file,file.resolveSibling(id+"-"+System.currentTimeMillis()+".deleted"));
    }
    public JsonObject named(UUID turtle,String name)throws IOException{
        for(var row:list(turtle)){var r=row.getAsJsonObject();if(r.get("name").getAsString().equals(name))return load(turtle,UUID.fromString(r.get("id").getAsString()));}
        throw new IllegalArgumentException("Library program not found: "+name);
    }
    public static WorkspaceCompiler.Compiled compile(JsonObject p){return compile(p,true);}
    public static WorkspaceCompiler.Compiled compile(JsonObject p,boolean annotated){return p.get("mode").getAsString().equals("lua")?new WorkspaceCompiler.Compiled(p.get("source").getAsString(),Map.of(),Set.of()):WorkspaceCompiler.compile(p.getAsJsonObject("workspace"),annotated);}
}
