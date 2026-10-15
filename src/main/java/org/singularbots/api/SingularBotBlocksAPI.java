package org.singularbots.api;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.singularbots.compat.cct.*;

/** Modern source API; 1.8.9 binary signatures are intentionally not retained. */
public final class SingularBotBlocksAPI {
    private SingularBotBlocksAPI(){}
    public static boolean isInstalled(){return true;}
    public static String getInstalledVersion(){return net.neoforged.fml.ModList.get().getModContainerById("singularbots").orElseThrow().getModInfo().getVersion().toString();}
    public static String getAPIVersion(){return "2.0";}
    public static boolean isTurtle(Block block){return TeachingContent.isTurtle(block);}
    public static void setTurtlesEnabled(boolean enabled){set("enabled",enabled);}
    public static void setTurtlesStopped(boolean stopped){set("stopped",stopped);}
    public static void setVisualEditorEnabled(boolean enabled){set("visualEditor",enabled);}
    public static void setSuggestionsEnabled(boolean enabled){set("suggestions",enabled);}
    public static void setTurtlesCanBuild(boolean enabled){set("canBuild",enabled);}
    public static String getCurrentBlockId(BlockEntity entity){return entity instanceof JuniorTurtleEntity t?t.runtime().json().get("blockId").getAsString():"";}
    private static void set(String key,boolean value){var server=ServerLifecycleHooks.getCurrentServer();if(server==null)throw new IllegalStateException("Teaching policy needs a running server");server.execute(()->TeachingSettings.get(server).set(server,key,value));}
}
