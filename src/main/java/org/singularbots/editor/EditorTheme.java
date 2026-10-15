package org.singularbots.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** The editor's light/dark look, remembered per computer so every robot's editor opens the way the student left it. */
public final class EditorTheme {
    public static final String DARK = "dark", LIGHT = "light";
    private final Path path;

    public EditorTheme(Path directory) { path = directory.resolve("editor-theme.txt"); }

    /** Dark, the original look, unless light was chosen; an unreadable or unknown file falls back without being touched. */
    public synchronized String load() {
        try { return Files.exists(path) && LIGHT.equals(Files.readString(path, StandardCharsets.UTF_8).trim()) ? LIGHT : DARK; }
        catch (IOException | RuntimeException e) { return DARK; }
    }

    public synchronized void save(String theme) throws IOException {
        if (!DARK.equals(theme) && !LIGHT.equals(theme)) throw new IllegalArgumentException("Unknown editor theme");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "theme-", ".tmp");
        try {
            Files.writeString(temporary, theme, StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
