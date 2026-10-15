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
        e.register((stack,index)->index==0?net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack,-1):-1,TeachingContent.JUNIOR_ITEM.get());
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(TeachingContent.JUNIOR_ENTITY.get(),TurtleBlockEntityRenderer::new);}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent e){
        e.register(TeachingContent.REMOTE_MENU.get(),RemoteScreen::new);
        RemoteNetwork.onState=state->{if(Minecraft.getInstance().screen instanceof RemoteScreen screen)screen.receive(state);else if(Minecraft.getInstance().screen instanceof org.singularbots.client.EditorScreen screen)screen.receiveRemote(state);};
        org.singularbots.network.ProgramNetwork.onReply=ProgramClient::receive;
    }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional e){
        for(int i=0;i<JuniorBodyModels.NAMES.length;i++)
            e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(JuniorBodyModels.model(i)));
        for(String name:org.singularbots.turtle.Appearance.DECORATIONS) if(!name.equals("none"))
            e.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("singularbots","block/turtle_junior_overlay_"+name)));
    }
    @SubscribeEvent public static void modelLoaders(ModelEvent.RegisterGeometryLoaders e){
        e.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("singularbots","junior_turtle"),new JuniorModelLoader());
    }
}
