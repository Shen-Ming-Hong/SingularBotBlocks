package org.singularbots.compat.cct.client;

import dan200.computercraft.client.render.TurtleBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import org.singularbots.compat.cct.TeachingContent;
import org.singularbots.network.RemoteNetwork;

@EventBusSubscriber(modid="singularbots",value=Dist.CLIENT)
public final class TeachingClient {
    @SubscribeEvent public static void cameraHand(RenderHandEvent e){
        if(Minecraft.getInstance().screen instanceof RemoteScreen screen&&screen.isCameraView())e.setCanceled(true);
    }
    @SubscribeEvent public static void cameraTick(ClientTickEvent.Post e){
        if(Minecraft.getInstance().screen instanceof RemoteScreen screen)screen.updateCamera();
    }
    @SubscribeEvent public static void colours(RegisterColorHandlersEvent.Item e){
        e.register((stack,index)->index!=0?-1:org.singularbots.compat.cct.FramedRobots.shown(stack)?net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack,-1):OwnLook.colour(),TeachingContent.JUNIOR_ITEM.get());
    }
    private static final java.util.ArrayDeque<java.util.Optional<java.util.UUID>> HOLDERS=new java.util.ArrayDeque<>();
    /** Items drawn while another player's model renders (their hands) wear that player's look. */
    @SubscribeEvent public static void holderStart(RenderLivingEvent.Pre<?,?> e){
        var id=e.getEntity() instanceof net.minecraft.world.entity.player.Player player&&player!=Minecraft.getInstance().player?player.getUUID():null;
        HOLDERS.push(java.util.Optional.ofNullable(OwnLook.holder(id)));
    }
    @SubscribeEvent public static void holderEnd(RenderLivingEvent.Post<?,?> e){if(!HOLDERS.isEmpty())OwnLook.holder(HOLDERS.pop().orElse(null));}
    /** A cancelled model render skips its Post event; start every frame with no holder. */
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre e){HOLDERS.clear();OwnLook.holder(null);}
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e){OwnLook.reset();HOLDERS.clear();}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(TeachingContent.JUNIOR_ENTITY.get(),TurtleBlockEntityRenderer::new);}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent e){
        e.register(TeachingContent.REMOTE_MENU.get(),RemoteScreen::new);
        RemoteNetwork.onState=state->{if(Minecraft.getInstance().screen instanceof RemoteScreen screen)screen.receive(state);else if(Minecraft.getInstance().screen instanceof org.singularbots.client.EditorScreen screen)screen.receiveRemote(state);};
        org.singularbots.network.ProgramNetwork.onReply=ProgramClient::receive;
        org.singularbots.network.ProgramNetwork.onTakeover=TakeoverClient::receive;
        RemoteNetwork.onLook=OwnLook::receive;
        RemoteNetwork.onLocate=LocateEffect::receive;
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){
        for(int i=0;i<JuniorBodyModels.NAMES.length;i++)
            e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(JuniorBodyModels.model(i)));
        for(String signal:JuniorBodyModels.SIGNALS)
            e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(JuniorBodyModels.edges(signal)));
        for(String name:org.singularbots.turtle.Appearance.DECORATIONS) if(!name.equals("none"))
            e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("singularbots","block/turtle_junior_overlay_"+name)));
    }
    @SubscribeEvent public static void modelLoaders(ModelEvent.RegisterGeometryLoaders e){
        e.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("singularbots","junior_turtle"),new JuniorModelLoader());
    }
}
