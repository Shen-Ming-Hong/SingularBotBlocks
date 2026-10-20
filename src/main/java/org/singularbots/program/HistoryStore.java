package org.singularbots.program;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Bounded, durable activity history for one student's workspace, independent of robot lifetime. */
public final class HistoryStore {
    public static final int MAX_RECORDS=256, MAX_BYTES=512*1024;
    private final Path root;
    public HistoryStore(Path root){this.root=root;}
    private Path path(UUID student){return root.resolve(student.toString()).resolve("history.json");}
    /** Parsed records by file, reused while the file is unchanged: each auto-save appends a record, and re-reading 256 records every time dominated the cost of a save. */
    private static final java.util.Map<Path,Cached> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private record Cached(Object key,java.nio.file.attribute.FileTime modified,long size,JsonArray records){
        boolean matches(java.nio.file.attribute.BasicFileAttributes a){return java.util.Objects.equals(key,a.fileKey())&&modified.equals(a.lastModifiedTime())&&size==a.size();}
    }
    public static void clearCache(){CACHE.clear();}
    public JsonArray list(UUID student)throws IOException{return records(path(student)).deepCopy();}
    /** The cached array itself; callers copy it before handing it out. */
    private JsonArray records(Path file)throws IOException{
        if(!Files.exists(file))return new JsonArray();
        var attributes=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class);var cached=CACHE.get(file);
        if(cached!=null&&cached.matches(attributes))return cached.records();
        if(attributes.size()>MAX_BYTES)throw new IOException("History exceeds limit; original file preserved");
        var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(data.get("version").getAsInt()!=1)throw new IOException("Unsupported history version; original file preserved");
        var records=data.getAsJsonArray("records");CACHE.put(file,new Cached(attributes.fileKey(),attributes.lastModifiedTime(),attributes.size(),records));return records;
    }
    public void append(UUID student,JsonObject record)throws IOException{
        if(record.toString().length()>4096)throw new IllegalArgumentException("History entry exceeds limit");
        // Changes the cached array in place; a failed write drops it so the next read starts from the file again.
        var file=path(student);var records=records(file);
        try{
        records.add(record.deepCopy());
        while(records.size()>MAX_RECORDS)records.remove(0);
        var data=new JsonObject();data.addProperty("version",1);data.add("records",records);
        var text=data.toString();
        while(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES){records.remove(0);text=data.toString();}
        Files.createDirectories(file.getParent());var temp=Files.createTempFile(file.getParent(),"history-",".tmp");
        try{Files.writeString(temp,text);Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
        }catch(IOException|RuntimeException e){CACHE.remove(file);throw e;}
        var attributes=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class);
        CACHE.put(file,new Cached(attributes.fileKey(),attributes.lastModifiedTime(),attributes.size(),records));
    }
}
