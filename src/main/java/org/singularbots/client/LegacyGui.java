package org.singularbots.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

/** Shared 360 x 228 work area: framed panel texture, inventory wells, text fields and side tabs. */
public final class LegacyGui {
    private LegacyGui() { }
    /** Label colour on the gunmetal panel and number colour inside the dark slot wells. */
    public static final int TEXT=0xffe0e1dc,SLOT_NUMBER=0xff85898c;
    public record Frame(int x,int y,int width,int height) { }
    public static Frame frame(int screenWidth,int screenHeight) {
        int outerWidth=Math.min(360,screenWidth-8),height=Math.min(228,screenHeight-8);
        return new Frame((screenWidth-outerWidth)/2,(screenHeight-height)/2,outerWidth-21,height);
    }
    public static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("singularbots","textures/gui/legacy/"+name+".png");
    }
    public static void panel(GuiGraphics g,int x,int y,int width,int height) {
        nineSlice(g,x,y,width,height,130,0,6,6,24,24);
    }
    /** An 18 x 18 inset inventory well whose top-left corner is one pixel outside the slot. */
    public static void slot(GuiGraphics g,int x,int y) {
        g.blit(texture("widgets"),x,y,0,64,18,18,256,256);
    }
    /** Recessed text field background matching the inventory wells. */
    public static void field(GuiGraphics g,int x,int y,int width,int height) {
        nineSlice(g,x,y,width,height,0,96,6,4,24,4); // the brass end caps are 6 px wide
    }
    /**
     * Draws a frame from the widgets atlas: fixed corners of edgeW x edgeH, with the edges and
     * centre tiled (not stretched) from a centreW x centreH source so the texture keeps its pixels.
     */
    private static void nineSlice(GuiGraphics g,int x,int y,int width,int height,int u,int v,int edgeW,int edgeH,int centreW,int centreH) {
        int[] xs={0,edgeW,width-edgeW},ws={edgeW,width-2*edgeW,edgeW},us={u,u+edgeW,u+edgeW+centreW},uw={edgeW,centreW,edgeW};
        int[] ys={0,edgeH,height-edgeH},hs={edgeH,height-2*edgeH,edgeH},vs={v,v+edgeH,v+edgeH+centreH},vh={edgeH,centreH,edgeH};
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)
            for(int ty=0;ty<hs[row];ty+=vh[row])for(int tx=0;tx<ws[col];tx+=uw[col]){
                int w=Math.min(uw[col],ws[col]-tx),h=Math.min(vh[row],hs[row]-ty);
                g.blit(texture("widgets"),x+xs[col]+tx,y+ys[row]+ty,us[col],vs[row],w,h,256,256);
            }
    }
    public static final class IconButton extends Button {
        public String image;
        public boolean tab,selected;
        public IconButton(Component label,String image,int size,Runnable run) {
            super(0,0,size,size,label,b->run.run(),DEFAULT_NARRATION);this.image=image;setTooltip(Tooltip.create(label));
        }
        @Override protected void renderWidget(GuiGraphics g,int x,int y,float partial) {
            if(tab){g.blit(texture("widgets"),getX(),getY(),12,selected?0:24,24,24,256,256);g.blit(texture(image),getX()+4,getY()+4,0,0,16,16,16,16);}
            else{if(!active)RenderSystem.setShaderColor(.5f,.5f,.5f,1);g.blit(texture(image),getX(),getY(),0,0,width,height,width,height);RenderSystem.setShaderColor(1,1,1,1);}
            if(isHoveredOrFocused()&&active)g.renderOutline(getX(),getY(),width,height,0xffeeeeee);
        }
    }
}
