package org.singularbots.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EditorStorageTest {
    @TempDir Path directory;
    private JsonObject draft(String id) {
        return JsonParser.parseString("{\"version\":1,\"locale\":\"zh_tw\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_turtle_command\",\"id\":\"" + id + "\"}]}}}").getAsJsonObject();
    }
    @Test void stableIdsSurviveSaveAndPreviousDraftRemainsRecoverable() throws Exception {
        DraftStore store = new DraftStore(directory);
        store.save(draft("student-block-1")); store.save(draft("student-block-2"));
        assertEquals(draft("student-block-2"), store.load());
        assertEquals(draft("student-block-1"), JsonParser.parseString(Files.readString(directory.resolve("browser-test-draft.json.bak"))));
    }
    @Test void invalidSaveDoesNotOverwriteExistingDraft() throws Exception {
        DraftStore store = new DraftStore(directory); store.save(draft("keep"));
        JsonObject invalid = draft("bad"); invalid.addProperty("version", 2);
        assertThrows(IllegalArgumentException.class, () -> store.save(invalid));
        assertEquals(draft("keep"), store.load());
    }
    @Test void corruptDraftIsPreservedAndReported() throws Exception {
        Path file = directory.resolve("browser-test-draft.json"); Files.writeString(file,"broken{");
        assertThrows(IOException.class, () -> new DraftStore(directory).load());
        assertEquals("broken{", Files.readString(file));
    }
    @Test void oversizedWorkspaceIsRejectedWithoutCreatingDraft() {
        JsonObject large = draft("x"); large.addProperty("large", "x".repeat(DraftStore.MAX_BYTES));
        assertThrows(IllegalArgumentException.class, () -> new DraftStore(directory).save(large));
        assertFalse(Files.exists(directory.resolve("browser-test-draft.json")));
    }
    @Test void resourcePolicyRejectsTraversalAndLookalikeOrigins() {
        assertTrue(EditorOrigin.isEditor(EditorOrigin.URL + "?lang=zh_tw"));
        assertNotNull(EditorOrigin.assetPath("http://singularbots.invalid/media/quote0.png"));
        for (String url : new String[]{"https://singularbots.invalid/index.html", "file:///tmp/index.html",
            "http://singularbots.invalid.evil/index.html", "http://attacker@singularbots.invalid/index.html",
            "http://singularbots.invalid:80/index.html", "http://singularbots.invalid/../secret",
            "http://singularbots.invalid/%2e%2e/secret", "http://singularbots.invalid/%5csecret",
            "http://singularbots.invalid/%00secret"}) assertNull(EditorOrigin.assetPath(url), url);
    }
}
