package org.singularbots.client;

import org.singularbots.editor.EditorOrigin;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import java.io.IOException;
import java.util.Map;

final class LocalEditorResource implements CefResourceHandler {
    private byte[] bytes = new byte[0];
    private int offset, status = 404;
    private String mime = "text/plain";

    // Assets are fixed inside the mod JAR; keeping them avoids unzipping the 12 MB font on every editor open.
    private static final Map<String, byte[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String ATLAS = "assets/singularbots/html/" + ItemCatalog.ATLAS;

    private static byte[] load(String path) throws IOException {
        if (path.equals(ATLAS)) return ItemCatalog.atlas();
        byte[] cached = CACHE.get(path);
        if (cached != null) return cached;
        try (var stream = LocalEditorResource.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return null;
            byte[] loaded = stream.readAllBytes();
            CACHE.put(path, loaded);
            return loaded;
        }
    }

    @Override public boolean processRequest(CefRequest request, CefCallback callback) {
        String path = EditorOrigin.assetPath(request.getURL());
        if (path != null && "GET".equals(request.getMethod())) {
            try {
                byte[] loaded = load(path);
                if (loaded != null) {
                    bytes = loaded; status = 200;
                    mime = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "application/javascript"
                        : path.endsWith(".css") ? "text/css" : path.endsWith(".svg") ? "image/svg+xml"
                        : path.endsWith(".png") ? "image/png" : path.endsWith(".gif") ? "image/gif"
                        : path.endsWith(".ttf") ? "font/ttf" : path.endsWith(".mp3") ? "audio/mpeg"
                        : path.endsWith(".ogg") ? "audio/ogg" : path.endsWith(".wav") ? "audio/wav" : path.endsWith(".json") ? "application/json" : "text/plain";
                }
            } catch (IOException e) { status = 500; }
        }
        callback.Continue();
        return true;
    }

    @Override public void getResponseHeaders(CefResponse response, IntRef length, StringRef redirect) {
        response.setStatus(status); response.setMimeType(mime);
        response.setHeaderMap(Map.of("Cache-Control", "no-store", "X-Content-Type-Options", "nosniff"));
        length.set(bytes.length);
    }
    @Override public boolean readResponse(byte[] output, int count, IntRef read, CefCallback callback) {
        int size = Math.min(Math.min(count, output.length), bytes.length - offset);
        System.arraycopy(bytes, offset, output, 0, size); offset += size; read.set(size);
        return size > 0;
    }
    @Override public void cancel() { bytes = new byte[0]; offset = 0; }
}
