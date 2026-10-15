package org.singularbots.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.singularbots.client.LegacyGui.IconButton;
import org.singularbots.compat.cct.JuniorTurtleEntity;
import org.singularbots.compat.cct.RemoteService;
import org.singularbots.compat.cct.TeachingContent;
import org.singularbots.compat.cct.client.RemoteScreen;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * QA-only timing run, enabled with -Dccedu.editorProbe=&lt;result.json&gt;: creates a flat world, places a robot,
 * opens its controller and measures opening the editor immediately (cold) and after the controller preloaded it (warm).
 */
@EventBusSubscriber(modid = "singularbots", value = Dist.CLIENT)
public final class EditorProbe {
    private static final String OUTPUT = System.getProperty("ccedu.editorProbe");
    private static final String[] PLAN = {"cold", "warm", "cold", "warm", "cold", "warm"};
    private enum Phase { TITLE, LOADING, PLACE, REMOTE, EDITOR, FPS, CLOSING, DONE }
    private static Phase phase = Phase.TITLE;
    private static int ticks, phaseTicks, trial;
    private static long clickedAt, lastTick, longestStall;
    private static final JsonArray trials = new JsonArray(), editorFps = new JsonArray(), remoteFps = new JsonArray();

    private EditorProbe() {}

    private static void enter(Phase next) { phase = next; phaseTicks = 0; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (OUTPUT == null || phase == Phase.DONE) return;
        var minecraft = Minecraft.getInstance();
        ticks++; phaseTicks++;
        long now = System.nanoTime();
        // The first open builds the item catalog; a blocked render thread shows up as a long gap between ticks.
        if (phase == Phase.FPS && lastTick != 0) longestStall = Math.max(longestStall, now - lastTick);
        lastTick = now;
        try {
            if (ticks > 20 * 60 * 6) { finish(minecraft, "timed out in " + phase); return; }
            switch (phase) {
                case TITLE -> {
                    if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) minecraft.setScreen(new TitleScreen());
                    if (minecraft.screen instanceof TitleScreen) { createWorld(minecraft); enter(Phase.LOADING); }
                }
                case LOADING -> { if (minecraft.player != null && minecraft.level != null && minecraft.screen == null && phaseTicks > 100) enter(Phase.PLACE); }
                case PLACE -> { if (phaseTicks == 1) placeRobot(minecraft); if (minecraft.screen instanceof RemoteScreen) enter(Phase.REMOTE); }
                case REMOTE -> {
                    if (!(minecraft.screen instanceof RemoteScreen remote)) return;
                    // Sample the frame rate with the world visible during the first warm wait.
                    if (trial == 1 && phaseTicks % 20 == 0) remoteFps.add(minecraft.getFps());
                    int wait = PLAN[trial].equals("cold") ? 2 : 100;
                    if (phaseTicks >= wait) {
                        EditorScreen.usableAt = 0; clickedAt = System.nanoTime();
                        remote.children().stream().filter(w -> w instanceof IconButton b && b.tab).map(w -> (IconButton) w).findFirst().orElseThrow().onPress();
                        enter(Phase.EDITOR);
                    }
                }
                case EDITOR -> {
                    if (EditorScreen.usableAt != 0) {
                        var row = new JsonObject();
                        row.addProperty("scenario", PLAN[trial]); row.addProperty("clickToUsableMs", (EditorScreen.usableAt - clickedAt) / 1_000_000);
                        trials.add(row);
                        LoggerFactory.getLogger("SingularBotBlocks").info("Editor probe {} {}", trial, row);
                        enter(trial == 0 ? Phase.FPS : Phase.CLOSING);
                    } else if (phaseTicks > 20 * 30) finish(minecraft, "editor did not become usable");
                }
                // Let the first-session item catalog finish before sampling the editor frame rate.
                case FPS -> { if (phaseTicks > 100 && phaseTicks % 20 == 0) editorFps.add(minecraft.getFps()); if (phaseTicks >= 100 + 20 * 10) { capture(minecraft); enter(Phase.CLOSING); } }
                case CLOSING -> {
                    if (phaseTicks == 1 && minecraft.screen instanceof EditorScreen editor) editor.onClose();
                    if (phaseTicks > 1 && minecraft.screen instanceof RemoteScreen) {
                        if (++trial >= PLAN.length) finish(minecraft, ""); else enter(Phase.REMOTE);
                    } else if (phaseTicks > 20 * 15) finish(minecraft, "editor did not close");
                }
                default -> {}
            }
        } catch (Exception e) { finish(minecraft, e.toString()); }
    }

    private static void createWorld(Minecraft minecraft) {
        var settings = new LevelSettings("editor-probe-" + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, WorldOptions.defaultWithRandomSeed(),
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
    }

    /** Places the robot as the player would and opens its controller on the integrated server. */
    private static void placeRobot(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var id = minecraft.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            var level = player.serverLevel();
            var pos = player.blockPosition().relative(player.getDirection(), 2);
            var state = TeachingContent.JUNIOR.get().defaultBlockState();
            level.setBlock(pos, state, 3);
            state.getBlock().setPlacedBy(level, pos, state, player, net.minecraft.world.item.ItemStack.EMPTY);
            if (level.getBlockEntity(pos) instanceof JuniorTurtleEntity turtle) RemoteService.open(player, turtle);
        });
    }

    /** Keeps the rendered icon atlas and the open editor next to the result for inspection. */
    private static void capture(Minecraft minecraft) throws java.io.IOException {
        var directory = Path.of(OUTPUT).getParent();
        byte[] atlas = ItemCatalog.atlas();
        if (atlas != null) Files.write(directory.resolve("item-atlas.png"), atlas);
        net.minecraft.client.Screenshot.grab(directory.toFile(), "editor-probe.png", minecraft.getMainRenderTarget(), message -> {});
    }

    private static void finish(Minecraft minecraft, String error) {
        phase = Phase.DONE;
        var result = new JsonObject();
        result.addProperty("passed", error.isEmpty()); result.addProperty("error", error);
        result.add("trials", trials); result.addProperty("longestStallAfterFirstOpenMs", longestStall / 1_000_000); result.add("editorFps", editorFps); result.add("remoteFps", remoteFps);
        result.addProperty("window", minecraft.getWindow().getScreenWidth() + "x" + minecraft.getWindow().getScreenHeight());
        result.addProperty("observedAt", java.time.Instant.now().toString());
        try { Files.writeString(Path.of(OUTPUT), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception e) { LoggerFactory.getLogger("SingularBotBlocks").error("Cannot write editor probe result", e); }
        LoggerFactory.getLogger("SingularBotBlocks").info("Editor probe finished: {}", result);
        minecraft.stop();
    }
}
