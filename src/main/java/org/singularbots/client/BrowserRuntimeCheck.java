package org.singularbots.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Accepts either this project's prepared native package or the runtime MCEF 2.1.6 downloads itself.
 * Both must be built from the pinned JCEF commit and CEF/Chromium release.
 */
final class BrowserRuntimeCheck {
    static final String JCEF_COMMIT = "a78e832f9f13c2c688caea3d04d8b84fcd238d94";
    static final String CEF_VERSION = "116.0.27+gd8c85ac+chromium-116.0.5845.190";
    static final int[] CEF = {116, 0, 27};
    static final int[] CHROME = {116, 0, 5845, 190};

    /** What the editor reports about the runtime in its diagnostics. */
    record Runtime(JsonObject build, JsonObject installation) {}

    /** Version numbers MCEF reports after it initialized; kept free of MCEF types so it can be unit tested. */
    record Loaded(String jcefCommit, int[] cef, int[] chrome) {}

    private BrowserRuntimeCheck() {}

    /**
     * @param platformDir the {@code jcef.path} directory, for example {@code mods/mcef-libraries/macos_arm64}
     * @param loaded what the initialized MCEF reports
     */
    static Runtime verify(Path platformDir, Loaded loaded) throws IOException {
        Path manifest = platformDir.resolve("computercraftedu-native.json");
        if (Files.exists(manifest)) {
            JsonObject build = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
            JsonObject installation = JsonParser.parseString(Files.readString(platformDir.resolve("computercraftedu-install.json"))).getAsJsonObject();
            if (!JCEF_COMMIT.equals(build.get("jcef_commit").getAsString()) || !CEF_VERSION.equals(build.get("cef_version").getAsString()))
                throw new IllegalStateException("Browser native runtime does not match the pinned build");
            return new Runtime(build, installation);
        }
        // No prepared package: MCEF downloaded its own runtime from its official server.
        if (!Files.isDirectory(platformDir)) throw new NoSuchFileException(platformDir.toString());
        if (!JCEF_COMMIT.equals(loaded.jcefCommit()) || !matches(CEF, loaded.cef()) || !matches(CHROME, loaded.chrome()))
            throw new IllegalStateException("Browser native runtime does not match the pinned build (JCEF " + loaded.jcefCommit()
                + ", CEF " + join(loaded.cef()) + ", Chromium " + join(loaded.chrome()) + ")");
        JsonObject build = new JsonObject();
        build.addProperty("source", "mcef-download");
        build.addProperty("jcef_commit", loaded.jcefCommit());
        build.addProperty("cef_version", join(loaded.cef()));
        build.addProperty("chrome_version", join(loaded.chrome()));
        Path checksum = platformDir.resolveSibling(platformDir.getFileName() + ".tar.gz.sha256");
        if (Files.isRegularFile(checksum)) {
            String line = Files.readString(checksum).trim();
            int space = line.indexOf(' ');
            build.addProperty("archive_sha256", space < 0 ? line : line.substring(0, space));
        }
        return new Runtime(build, null);
    }

    private static boolean matches(int[] expected, int[] actual) {
        if (actual == null || actual.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (expected[i] != actual[i]) return false;
        return true;
    }

    private static String join(int[] parts) {
        if (parts == null) return "unknown";
        StringBuilder text = new StringBuilder();
        for (int part : parts) text.append(text.isEmpty() ? "" : ".").append(part);
        return text.toString();
    }
}
