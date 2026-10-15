package org.singularbots.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EditorThemeTest {
    @TempDir Path directory;
    @Test void darkUntilLightIsChosenAndTheChoiceSurvivesANewStore() throws Exception {
        assertEquals(EditorTheme.DARK, new EditorTheme(directory).load());
        new EditorTheme(directory).save(EditorTheme.LIGHT);
        assertEquals(EditorTheme.LIGHT, new EditorTheme(directory).load());
        new EditorTheme(directory).save(EditorTheme.DARK);
        assertEquals(EditorTheme.DARK, new EditorTheme(directory).load());
    }
    @Test void unknownValuesAreRejectedAndUnreadableFilesFallBackToDark() throws Exception {
        EditorTheme theme = new EditorTheme(directory); theme.save(EditorTheme.LIGHT);
        assertThrows(IllegalArgumentException.class, () -> theme.save("neon"));
        assertEquals(EditorTheme.LIGHT, theme.load());
        Files.writeString(directory.resolve("editor-theme.txt"), "neon");
        assertEquals(EditorTheme.DARK, theme.load());
        assertEquals("neon", Files.readString(directory.resolve("editor-theme.txt")));
    }
}
