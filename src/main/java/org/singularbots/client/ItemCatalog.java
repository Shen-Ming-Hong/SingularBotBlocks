package org.singularbots.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.Util;
import org.slf4j.LoggerFactory;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Every registered item and block, including other mods, with both-language names and rendered icons for the editor picker. */
final class ItemCatalog {
    static final int ICON = 32, COLUMNS = 64, MAX_ICONS = COLUMNS * 512;
    static final String ATLAS = "generated/item-atlas.png";
    private static final int BLOCK = 1, ITEM = 2;
    private static volatile byte[] atlas;
    private static JsonObject catalog;
    private static final List<Consumer<JsonObject>> WAITING = new ArrayList<>();
    private static final List<Consumer<String>> FAILING = new ArrayList<>();

    private ItemCatalog() {}

    static byte[] atlas() { return atlas; }

    /**
     * Built once per game session; registries do not change after startup. Names and PNG encoding run in the
     * background so only drawing the icons uses the render thread. Callbacks run on the render thread.
     */
    static void request(Consumer<JsonObject> done, Consumer<String> failed) {
        if (catalog != null) { done.accept(catalog); return; }
        WAITING.add(done); FAILING.add(failed);
        if (WAITING.size() > 1) return;
        var minecraft = Minecraft.getInstance();
        long started = System.nanoTime();
        long[] stages = new long[3];
        CompletableFuture.supplyAsync(ItemCatalog::collect, Util.backgroundExecutor())
            .thenComposeAsync(rows -> { stages[0] = System.nanoTime(); return AtlasRenderer.start(rows.icons).thenApply(image -> { stages[1] = System.nanoTime(); return Map.entry(rows, image); }); }, minecraft)
            .thenApplyAsync(rendered -> {
                try (var image = rendered.getValue()) { atlas = image.asByteArray(); }
                catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                stages[2] = System.nanoTime();
                return json(rendered.getKey());
            }, Util.backgroundExecutor())
            .whenCompleteAsync((result, error) -> {
                var waiting = new ArrayList<>(WAITING); var failing = new ArrayList<>(FAILING);
                WAITING.clear(); FAILING.clear();
                if (error != null) {
                    LoggerFactory.getLogger("SingularBotBlocks").error("Cannot build item catalog", error);
                    failing.forEach(f -> f.accept(String.valueOf(error.getCause() == null ? error : error.getCause())));
                    return;
                }
                catalog = result;
                LoggerFactory.getLogger("SingularBotBlocks").info("Item catalog: {} entries in {} ms (names {} ms, icons over frames {} ms, encode {} ms)",
                    result.getAsJsonArray("items").size(), ms(started, System.nanoTime()), ms(started, stages[0]), ms(stages[0], stages[1]), ms(stages[1], stages[2]));
                waiting.forEach(d -> d.accept(result));
            }, minecraft);
    }

    private static long ms(long from, long to) { return (to - from) / 1_000_000; }

    private record Entry(String id, String key, ItemStack icon, int flags) {}
    private record Rows(JsonArray items, List<ItemStack> icons) {}

    private static Rows collect() {
        var minecraft = Minecraft.getInstance();
        Language en = ClientLanguage.loadFrom(minecraft.getResourceManager(), List.of("en_us"), false);
        Language zh = ClientLanguage.loadFrom(minecraft.getResourceManager(), List.of("en_us", "zh_tw"), false);
        var entries = new TreeMap<String, Entry>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            entries.put(id.toString(), new Entry(id.toString(), item.getDescriptionId(), new ItemStack(item), ITEM | (BuiltInRegistries.BLOCK.containsKey(id) ? BLOCK : 0)));
        }
        // Blocks without an item (water, fire, wall variants) are still valid inspect results.
        for (var block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (entries.containsKey(id) || block.defaultBlockState().isAir()) continue;
            var item = block.asItem() != Items.AIR ? block.asItem() : block.defaultBlockState().getFluidState().getType().getBucket();
            entries.put(id, new Entry(id, block.getDescriptionId(), item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item), BLOCK));
        }
        var icons = new ArrayList<ItemStack>();
        var items = new JsonArray();
        for (var entry : entries.values()) {
            int icon = -1;
            if (!entry.icon.isEmpty() && icons.size() < MAX_ICONS) { icon = icons.size(); icons.add(entry.icon); }
            var row = new JsonArray();
            row.add(entry.id); row.add(name(zh, entry)); row.add(name(en, entry)); row.add(entry.flags); row.add(icon);
            items.add(row);
        }
        return new Rows(items, icons);
    }

    private static JsonObject json(Rows rows) {
        var result = new JsonObject();
        result.addProperty("atlas", ATLAS + "?v=" + Integer.toHexString(Arrays.hashCode(atlas)));
        result.addProperty("icon", ICON); result.addProperty("columns", COLUMNS);
        result.addProperty("rows", Math.max(1, (rows.icons.size() + COLUMNS - 1) / COLUMNS));
        result.add("items", rows.items);
        return result;
    }

    private static String name(Language language, Entry entry) {
        String name = language.getOrDefault(entry.key, "");
        if (!name.isEmpty() && !name.equals(entry.key)) return name;
        String path = entry.id.substring(entry.id.indexOf(':') + 1).replace('_', ' ').replace('/', ' ');
        return path.isEmpty() ? entry.id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /** Draws the icons the way the inventory does, two pixels per GUI unit, a slice per frame so the editor keeps responding. */
    private static final class AtlasRenderer {
        private static final int PER_FRAME = 128;
        private static AtlasRenderer active;
        private static boolean listening;
        private final List<ItemStack> icons;
        private final CompletableFuture<NativeImage> result = new CompletableFuture<>();
        private final int width, height;
        private TextureTarget target;
        private int next;

        private AtlasRenderer(List<ItemStack> icons) {
            this.icons = icons;
            width = COLUMNS * ICON; height = Math.max(1, (icons.size() + COLUMNS - 1) / COLUMNS) * ICON;
        }

        /** Called on the render thread. */
        static CompletableFuture<NativeImage> start(List<ItemStack> icons) {
            if (!listening) { listening = true; NeoForge.EVENT_BUS.addListener(AtlasRenderer::frame); }
            active = new AtlasRenderer(icons);
            return active.result;
        }

        private static void frame(RenderFrameEvent.Pre event) {
            var renderer = active;
            if (renderer == null) return;
            try { if (renderer.step()) active = null; }
            catch (RuntimeException e) { active = null; renderer.close(); renderer.result.completeExceptionally(e); }
        }

        private boolean step() {
            var minecraft = Minecraft.getInstance();
            if (target == null) { target = new TextureTarget(width, height, true, Minecraft.ON_OSX); target.setClearColor(0, 0, 0, 0); target.clear(Minecraft.ON_OSX); }
            target.bindWrite(true);
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width / 2f, height / 2f, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            var view = RenderSystem.getModelViewStack();
            view.pushMatrix(); view.translation(0, 0, -11000); RenderSystem.applyModelViewMatrix();
            try {
                Lighting.setupFor3DItems();
                var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
                int half = ICON / 2, end = Math.min(icons.size(), next + PER_FRAME);
                // A broken item model from another mod leaves only its own cell empty.
                for (; next < end; next++) try { graphics.renderItem(icons.get(next), next % COLUMNS * half, next / COLUMNS * half); } catch (RuntimeException ignored) {}
                graphics.flush();
            } finally {
                view.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.restoreProjectionMatrix();
                minecraft.getMainRenderTarget().bindWrite(true);
            }
            if (next < icons.size()) return false;
            var image = new NativeImage(width, height, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false); image.flipY();
            close();
            result.complete(image);
            return true;
        }

        private void close() {
            if (target != null) { target.destroyBuffers(); target = null; }
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }
}
