package org.singularbots.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Browser-gate test drafts, deliberately separate from server programs. */
public final class DraftStore {
    /** A Minecraft language code such as zh_tw, en_us, fil_ph, bar or zlm_arab. */
    public static final java.util.regex.Pattern LOCALE = java.util.regex.Pattern.compile("[a-z]{2,4}(_[a-z0-9]{2,4})?");
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Path path;

    public DraftStore(Path directory) { path = directory.resolve("browser-test-draft.json"); }

    public synchronized JsonObject load() throws IOException {
        if (!Files.exists(path)) return null;
        if (Files.size(path) > MAX_BYTES) throw new IOException("Draft exceeds 2 MiB; original file preserved");
        try {
            JsonObject draft = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            validate(draft);
            return draft;
        } catch (RuntimeException e) {
            throw new IOException("Invalid draft; original file preserved: " + e.getMessage(), e);
        }
    }

    public synchronized void save(JsonObject draft) throws IOException {
        validate(draft);
        byte[] bytes = draft.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Draft exceeds 2 MiB");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "draft-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            // Refuse a non-atomic replacement; keep the existing draft intact.
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void validate(JsonObject draft) {
        if (draft == null || !draft.has("version") || draft.get("version").getAsInt() != 1)
            throw new IllegalArgumentException("Unsupported draft version");
        if (!draft.has("workspace") || !draft.get("workspace").isJsonObject())
            throw new IllegalArgumentException("Missing Blockly workspace");
        if (draft.has("locale") && !LOCALE.matcher(draft.get("locale").getAsString()).matches())
            throw new IllegalArgumentException("Unsupported editor language");
    }
}
