package org.singularbots.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lets the student walk, jump and sneak with the game's movement keys while the robot controller or the editor beside a
 * running program stays open. The mouse stays free for the screen's buttons, so the camera does not turn.
 * NeoForge reports in-game key mappings as released while any screen is open, so the screen records the keys itself and
 * they are applied to the player's movement input here.
 */
@EventBusSubscriber(modid="singularbots", value=Dist.CLIENT)
public final class ScreenWalking {
    /** A screen the player can keep walking under. */
    public interface Walkable { boolean letsPlayerWalk(); }

    private static final Set<KeyMapping> held = new HashSet<>();
    // QA only: background tools can tap keys but not hold them, so with -Dsingularbots.qaWalkLatchTicks=N a press keeps
    // the key held for N ticks whatever its release. -Dsingularbots.inputTrace=true logs each key with where the player stands.
    private static final int LATCH_TICKS = Integer.getInteger("singularbots.qaWalkLatchTicks", 0);
    private static final boolean TRACE = Boolean.getBoolean("singularbots.inputTrace");
    private static final Map<KeyMapping,Integer> latched = new HashMap<>();
    private static int ticks;
    private ScreenWalking() {}

    private static KeyMapping[] keys() {
        var options = Minecraft.getInstance().options;
        return new KeyMapping[]{options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keyShift};
    }
    private static KeyMapping find(int key, int scan) { for (var mapping : keys()) if (mapping.matches(key, scan)) return mapping; return null; }

    /** A movement key went down; returns whether it moves the player, so the screen does not also use it. */
    public static boolean press(int key, int scan, int mods) {
        // Shortcuts such as Cmd+A or Ctrl+V stay with the screen; Shift is itself the sneak key.
        if ((mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER | GLFW.GLFW_MOD_ALT)) != 0) return false;
        var mapping = find(key, scan);
        if (mapping == null) return false;
        if (held.add(mapping)) trace(mapping, "down");
        if (LATCH_TICKS > 0) latched.put(mapping, ticks + LATCH_TICKS);
        return true;
    }
    /** A key came up; returns whether it was a movement key held through this screen. */
    public static boolean release(int key, int scan) {
        var mapping = find(key, scan);
        if (mapping == null || !held.contains(mapping)) return false;
        if (!latched.containsKey(mapping)) { held.remove(mapping); trace(mapping, "up"); }
        return true;
    }
    public static void releaseAll() { if (!held.isEmpty()) trace(null, "all up"); held.clear(); latched.clear(); }
    private static void trace(KeyMapping mapping, String change) {
        if (!TRACE) return;
        var player = Minecraft.getInstance().player;
        LoggerFactory.getLogger("SingularBotBlocks").info("Input trace: movement {} {} at {}", mapping == null ? "keys" : mapping.getName(), change,
            player == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", player.getX(), player.getY(), player.getZ()));
    }

    @SubscribeEvent public static void move(MovementInputUpdateEvent event) {
        var minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player) return;
        if (!(minecraft.screen instanceof Walkable screen) || !screen.letsPlayerWalk()) { releaseAll(); return; }
        ticks++;
        latched.entrySet().removeIf(entry -> {
            if (entry.getValue() > ticks) return false;
            held.remove(entry.getKey()); trace(entry.getKey(), "up (latch)"); return true;
        });
        if (held.isEmpty()) return;
        var options = minecraft.options;var input = event.getInput();
        input.up = held.contains(options.keyUp); input.down = held.contains(options.keyDown);
        input.left = held.contains(options.keyLeft); input.right = held.contains(options.keyRight);
        input.jumping = held.contains(options.keyJump); input.shiftKeyDown = held.contains(options.keyShift);
        input.forwardImpulse = input.up == input.down ? 0 : input.up ? 1 : -1;
        input.leftImpulse = input.left == input.right ? 0 : input.left ? 1 : -1;
        // The same slowdown the game applies to keyboard movement while crouching.
        if (minecraft.player.isMovingSlowly()) {
            float slow = (float)minecraft.player.getAttributeValue(Attributes.SNEAKING_SPEED);
            input.forwardImpulse *= slow; input.leftImpulse *= slow;
        }
    }
}
