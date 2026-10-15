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
    public JsonArray list(UUID student)throws IOException{
        var file=path(student);if(!Files.exists(file))return new JsonArray();
        if(Files.size(file)>MAX_BYTES)throw new IOException("History exceeds limit; original file preserved");
        var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(data.get("version").getAsInt()!=1)throw new IOException("Unsupported history version; original file preserved");
        return data.getAsJsonArray("records").deepCopy();
    }
    public void append(UUID student,JsonObject record)throws IOException{
        if(record.toString().length()>4096)throw new IllegalArgumentException("History entry exceeds limit");
        var records=list(student);records.add(record.deepCopy());
        while(records.size()>MAX_RECORDS)records.remove(0);
        var data=new JsonObject();data.addProperty("version",1);data.add("records",records);
        while(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)records.remove(0);
        var file=path(student);Files.createDirectories(file.getParent());var temp=Files.createTempFile(file.getParent(),"history-",".tmp");
        try{Files.writeString(temp,data.toString());Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
}
