package org.singularbots.program;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HistoryStoreTest {
    @TempDir Path root;
    @Test void historyIsPrivateBoundedAndSurvivesNewStore()throws Exception{
        var a=UUID.randomUUID();var b=UUID.randomUUID();var store=new HistoryStore(root);
        for(int i=0;i<270;i++){var row=new JsonObject();row.addProperty("message","record "+i);store.append(a,row);}
        var reloaded=new HistoryStore(root);assertEquals(256,reloaded.list(a).size());
        assertEquals("record 14",reloaded.list(a).get(0).getAsJsonObject().get("message").getAsString());
        assertTrue(reloaded.list(b).isEmpty());
    }
    @Test void unsupportedVersionIsPreserved()throws Exception{
        var id=UUID.randomUUID();var path=root.resolve(id.toString()).resolve("history.json");Files.createDirectories(path.getParent());Files.writeString(path,"{\"version\":2,\"records\":[]}");
        var store=new HistoryStore(root);assertThrows(java.io.IOException.class,()->store.append(id,new JsonObject()));assertEquals("{\"version\":2,\"records\":[]}",Files.readString(path));
    }
}
