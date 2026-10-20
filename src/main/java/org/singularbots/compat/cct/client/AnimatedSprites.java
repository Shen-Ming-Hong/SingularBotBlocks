package org.singularbots.compat.cct.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Embeddium and Sodium only advance an animated texture in a frame where something marked it as drawn,
 * and they mark it from vanilla's block and item model paths, not from CC's turtle renderer (its own
 * ModelRenderer). The idle edge signal still flowed because the robot item draws it in the hotbar; the
 * running and error signals only ever appear on the placed robot, so they froze. The placed robot marks
 * the textures of the models it draws itself; without either mod this does nothing.
 */
public final class AnimatedSprites {
    private static final MethodHandle MARK = find();

    private static final Map<BakedModel, TextureAtlasSprite[]> SPRITES = new WeakHashMap<>();

    private AnimatedSprites() { }

    /** Marks every texture on the model's faces as drawn this frame. */
    public static void drawn(BakedModel model) {
        if (MARK == null) return;
        try {
            for (var sprite : SPRITES.computeIfAbsent(model, AnimatedSprites::sprites)) MARK.invoke(sprite);
        } catch (Throwable ignored) {
            // A changed renderer API only costs the animation, never the frame.
        }
    }

    private static TextureAtlasSprite[] sprites(BakedModel model) {
        var sprites = new LinkedHashSet<TextureAtlasSprite>();
        var random = RandomSource.create();
        for (var side : net.minecraft.core.Direction.values()) model.getQuads(null, side, random).forEach(q -> sprites.add(q.getSprite()));
        model.getQuads(null, null, random).forEach(q -> sprites.add(q.getSprite()));
        return sprites.toArray(TextureAtlasSprite[]::new);
    }

    private static MethodHandle find() {
        var lookup = MethodHandles.publicLookup();
        var type = MethodType.methodType(void.class, TextureAtlasSprite.class);
        try { // Embeddium
            return lookup.findStatic(Class.forName("org.embeddedt.embeddium.api.render.texture.SpriteUtil"), "markSpriteActive", type);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        try { // Sodium 0.6: an interface with a shared INSTANCE
            var api = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            return lookup.findVirtual(api, "markSpriteActive", type).bindTo(api.getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException | LinkageError ignored) { }
        return null;
    }
}
