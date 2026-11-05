package org.singularbots.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserRuntimeCheckTest {
    private static final BrowserRuntimeCheck.Loaded PINNED = new BrowserRuntimeCheck.Loaded(
        BrowserRuntimeCheck.JCEF_COMMIT, new int[]{116, 0, 27}, new int[]{116, 0, 5845, 190});

    @Test void acceptsPreparedPackage(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("macos_arm64"));
        Files.writeString(dir.resolve("computercraftedu-native.json"),
            "{\"jcef_commit\":\"" + BrowserRuntimeCheck.JCEF_COMMIT + "\",\"cef_version\":\"" + BrowserRuntimeCheck.CEF_VERSION + "\"}");
        Files.writeString(dir.resolve("computercraftedu-install.json"), "{\"platform\":\"macos_arm64\"}");
        var runtime = BrowserRuntimeCheck.verify(dir, PINNED);
        assertEquals("macos_arm64", runtime.installation().get("platform").getAsString());
    }

    @Test void rejectsPreparedPackageWithOtherBuild(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("macos_arm64"));
        Files.writeString(dir.resolve("computercraftedu-native.json"), "{\"jcef_commit\":\"other\",\"cef_version\":\"" + BrowserRuntimeCheck.CEF_VERSION + "\"}");
        Files.writeString(dir.resolve("computercraftedu-install.json"), "{}");
        assertThrows(IllegalStateException.class, () -> BrowserRuntimeCheck.verify(dir, PINNED));
    }

    @Test void acceptsMcefDownloadedRuntime(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("macos_arm64"));
        Files.writeString(root.resolve("macos_arm64.tar.gz.sha256"), "abc123  macos_arm64.tar.gz\n");
        var runtime = BrowserRuntimeCheck.verify(dir, PINNED);
        assertNull(runtime.installation());
        assertEquals("mcef-download", runtime.build().get("source").getAsString());
        assertEquals("116.0.27", runtime.build().get("cef_version").getAsString());
        assertEquals("116.0.5845.190", runtime.build().get("chrome_version").getAsString());
        assertEquals("abc123", runtime.build().get("archive_sha256").getAsString());
    }

    @Test void rejectsMcefDownloadedRuntimeFromOtherCommitOrChromium(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("linux_amd64"));
        assertThrows(IllegalStateException.class, () -> BrowserRuntimeCheck.verify(dir,
            new BrowserRuntimeCheck.Loaded("other", new int[]{116, 0, 27}, new int[]{116, 0, 5845, 190})));
        assertThrows(IllegalStateException.class, () -> BrowserRuntimeCheck.verify(dir,
            new BrowserRuntimeCheck.Loaded(BrowserRuntimeCheck.JCEF_COMMIT, new int[]{117, 0, 1}, new int[]{117, 0, 5938, 62})));
        assertThrows(IllegalStateException.class, () -> BrowserRuntimeCheck.verify(dir,
            new BrowserRuntimeCheck.Loaded(BrowserRuntimeCheck.JCEF_COMMIT, null, null)));
    }

    @Test void rejectsMissingRuntimeDirectory(@TempDir Path root) {
        assertThrows(NoSuchFileException.class, () -> BrowserRuntimeCheck.verify(root.resolve("windows_amd64"), PINNED));
    }
}
