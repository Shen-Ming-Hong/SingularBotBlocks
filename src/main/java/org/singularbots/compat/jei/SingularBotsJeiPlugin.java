package org.singularbots.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.singularbots.compat.cct.client.RemoteScreen;
import java.util.List;

/**
 * Loaded by JEI only when JEI is installed. The robot controller's side tabs lie outside its declared panel,
 * where JEI places its ingredient list; without these areas a click on a tab opened a JEI recipe instead.
 */
@JeiPlugin
public final class SingularBotsJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("singularbots","jei");}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registration.addGuiContainerHandler(RemoteScreen.class,new IGuiContainerHandler<RemoteScreen>(){
            @Override public List<Rect2i> getGuiExtraAreas(RemoteScreen screen){return screen.sideAreas();}
        });
    }
}
