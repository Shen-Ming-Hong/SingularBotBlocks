package org.singularbots.compat.cct;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.network.chat.Component;

/** Teacher policy is stored in the world and enforced when commands execute. */
@EventBusSubscriber(modid="singularbots")
public final class TeachingSettings extends SavedData {
    // lua and sharing default off: a new world is a beginner class with blocks only and no program copying.
    // There are no floppy disks: Blockly functions cover reuse, and a world saved with "disks" simply ignores it.
    private boolean enabled=true,stopped,visualEditor=true,suggestions=true,canBuild=true,lua,sharing;
    public static TeachingSettings get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(TeachingSettings::new,TeachingSettings::load),"singularbots_settings");}
    private static TeachingSettings load(CompoundTag tag,HolderLookup.Provider lookup){var s=new TeachingSettings();s.enabled=!tag.contains("enabled")||tag.getBoolean("enabled");s.stopped=tag.getBoolean("stopped");s.visualEditor=!tag.contains("visualEditor")||tag.getBoolean("visualEditor");s.suggestions=!tag.contains("suggestions")||tag.getBoolean("suggestions");s.canBuild=!tag.contains("canBuild")||tag.getBoolean("canBuild");s.lua=tag.getBoolean("lua");s.sharing=tag.getBoolean("sharing");return s;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){tag.putBoolean("enabled",enabled);tag.putBoolean("stopped",stopped);tag.putBoolean("visualEditor",visualEditor);tag.putBoolean("suggestions",suggestions);tag.putBoolean("canBuild",canBuild);tag.putBoolean("lua",lua);tag.putBoolean("sharing",sharing);return tag;}
    public boolean allowed(){return enabled&&!stopped;}
    public boolean stopped(){return stopped;}
    public boolean visualEditor(){return visualEditor;}
    public boolean suggestions(){return suggestions;}
    public boolean canBuild(){return canBuild;}
    /** Lua stays available when the teacher disabled blocks, otherwise the class would have no editor at all. */
    public boolean luaAllowed(){return lua||!visualEditor;}
    public boolean sharing(){return sharing;}
    public void set(MinecraftServer server,String key,boolean value){
        switch(key){case "enabled"->enabled=value;case "stopped"->stopped=value;case "visualEditor"->visualEditor=value;case "suggestions"->suggestions=value;case "canBuild"->canBuild=value;case "lua"->lua=value;case "sharing"->sharing=value;default->throw new IllegalArgumentException("Unknown teaching setting");}
        setDirty();if(!allowed())RemoteService.stopAll(server);
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        var root=Commands.literal("botblocks").requires(source->source.hasPermission(2));
        for(var key:java.util.List.of("enabled","stopped","visualEditor","suggestions","canBuild","lua","sharing"))root.then(Commands.literal(key).then(Commands.argument("value",BoolArgumentType.bool()).executes(c->{var server=c.getSource().getServer();boolean value=BoolArgumentType.getBool(c,"value");get(server).set(server,key,value);c.getSource().sendSuccess(()->Component.literal("Singular Bot Blocks: "+key+" = "+value),true);return 1;})));
        root.then(Commands.literal("stop").executes(c->{var server=c.getSource().getServer();get(server).set(server,"stopped",true);c.getSource().sendSuccess(()->Component.translatable("message.singularbots.emergency_stop"),true);return 1;}));
        e.getDispatcher().register(root);
    }
}
