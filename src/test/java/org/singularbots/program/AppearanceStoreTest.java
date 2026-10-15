package org.singularbots.program;

import org.singularbots.turtle.Appearance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AppearanceStoreTest {
    @TempDir Path root;
    @Test void eachStudentKeepsOnlyTheFieldsTheySet()throws Exception{
        var a=UUID.randomUUID();var b=UUID.randomUUID();var store=new AppearanceStore(root);
        assertEquals(AppearanceStore.Look.NONE,store.get(a));
        store.setLabel(a,"小明一號");store.setColour(a,Appearance.COLOURS[14]);
        var reloaded=new AppearanceStore(root);
        assertEquals(new AppearanceStore.Look("小明一號",Appearance.COLOURS[14],null),reloaded.get(a));
        reloaded.setDecoration(a,"crown");reloaded.setLabel(a,"小明二號");
        assertEquals(new AppearanceStore.Look("小明二號",Appearance.COLOURS[14],"crown"),reloaded.get(a));
        assertEquals(AppearanceStore.Look.NONE,reloaded.get(b));
    }
    @Test void clearingTheNameRemovesTheDefaultName()throws Exception{
        var id=UUID.randomUUID();var store=new AppearanceStore(root);
        store.setLabel(id,"");assertEquals(AppearanceStore.Look.NONE,store.get(id));
        store.setLabel(id,"小明");store.setDecoration(id,"crown");store.setLabel(id,"  ");
        assertEquals(new AppearanceStore.Look(null,null,"crown"),store.get(id));
        var path=root.resolve(id.toString()).resolve("appearance.json");Files.writeString(path,"{\"version\":1,\"label\":\"\"}");
        assertEquals(AppearanceStore.Look.NONE,store.get(id));
    }
    @Test void invalidValuesAreRejectedOrIgnored()throws Exception{
        var id=UUID.randomUUID();var store=new AppearanceStore(root);
        assertThrows(IllegalArgumentException.class,()->store.setDecoration(id,"cape"));
        assertThrows(IllegalArgumentException.class,()->store.setLabel(id,"x".repeat(33)));
        var path=root.resolve(id.toString()).resolve("appearance.json");Files.createDirectories(path.getParent());
        Files.writeString(path,"{\"version\":1,\"colour\":123,\"decoration\":\"cape\"}");
        assertEquals(AppearanceStore.Look.NONE,store.get(id));
    }
    @Test void unsupportedVersionIsPreserved()throws Exception{
        var id=UUID.randomUUID();var path=root.resolve(id.toString()).resolve("appearance.json");Files.createDirectories(path.getParent());Files.writeString(path,"{\"version\":2}");
        var store=new AppearanceStore(root);assertThrows(java.io.IOException.class,()->store.setLabel(id,"A"));assertEquals("{\"version\":2}",Files.readString(path));
    }
}
