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

    public static ResourceLocation model(int index) {
        return ResourceLocation.fromNamespaceAndPath("singularbots", "block/turtle_junior_" + NAMES[index]);
    }

    public static int index(int colour) {
        if (colour == -1) return -1;
        // Minecraft 1.21.1 exposes dyed colours as opaque ARGB; the legacy palette is RGB.
        colour &= 0xffffff;
        for (int i = 0; i < Appearance.COLOURS.length; i++) {
            if (Appearance.COLOURS[i] == colour) return i;
        }
        return -1;
    }
}
