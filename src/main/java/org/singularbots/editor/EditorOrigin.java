package org.singularbots.editor;

import java.net.URI;

/** Reserved synthetic origin; every request is intercepted and served from classpath. */
public final class EditorOrigin {
    public static final String URL = "http://singularbots.invalid/index.html";
    private EditorOrigin() {}

    public static String assetPath(String url) {
        try {
            URI uri = URI.create(url);
            if (!"http".equals(uri.getScheme()) || !"singularbots.invalid".equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) return null;
            String path = uri.getPath();
            if (path == null || !path.startsWith("/") || path.contains("\\") || path.contains("\0")) return null;
            for (String part : path.split("/")) if (part.equals(".") || part.equals("..")) return null;
            if (!path.matches("/[A-Za-z0-9_./@-]+")) return null;
            return "assets/singularbots/html" + path;
        } catch (IllegalArgumentException e) { return null; }
    }

    public static boolean isEditor(String url) {
        return "assets/singularbots/html/index.html".equals(assetPath(url));
    }
}
