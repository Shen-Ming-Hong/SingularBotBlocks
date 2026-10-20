package org.singularbots.compat.cct.client;

import net.minecraft.resources.ResourceLocation;
import org.singularbots.turtle.Appearance;

/** Original pre-coloured bodies. Keep arbitrary CC RGB colours on its tinted fallback. */
public final class JuniorBodyModels {
    public static final String[] NAMES = {
        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private JuniorBodyModels() { }

    /** Program states with their own edge light colour; "" is idle. */
    public static final String[] SIGNALS = {"", "running", "error"};

    /** The animated edge signal for a program state, a shell drawn at full brightness over any body colour. */
    public static ResourceLocation edges(String signal) {
        return ResourceLocation.fromNamespaceAndPath("singularbots", "block/turtle_junior_edges" + (signal.isEmpty() ? "" : "_" + signal));
    }

    public static ResourceLocation model(int index) {
        return ResourceLocation.fromNamespaceAndPath("singularbots", "block/turtle_junior_" + NAMES[index]);
    }

    /** An uncoloured robot (a fresh item, or one placed before colours were chosen) is the default white. */
    public static int index(int colour) {
        if (colour == -1) return 0;
        // Minecraft 1.21.1 exposes dyed colours as opaque ARGB; the legacy palette is RGB.
        colour &= 0xffffff;
        for (int i = 0; i < Appearance.COLOURS.length; i++) {
            if (Appearance.COLOURS[i] == colour) return i;
        }
        return -1;
    }
}
