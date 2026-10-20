package org.singularbots.compat.cct.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.singularbots.network.RemoteNetwork;

/**
 * "My robots" points a robot out to the student who asked: a white outline seen through walls, like a spectral arrow's
 * glow, and a thin column of light rising from it, also seen through blocks. It plays for about five seconds once the student is back in the
 * world, so choosing a robot in the full-screen editor does not use the effect up behind it.
 */
@EventBusSubscriber(modid="singularbots",value=Dist.CLIENT)
public final class LocateEffect {
    static final int SHOW_TICKS=100,WAIT_TICKS=1200;
    private static ResourceLocation dimension;
    private static BlockPos target;
    private static int waited;
    /** Measured in real time from the first frame it is seen, so a slow or backgrounded game still shows it for about five seconds. */
    private static long shownSince;
    private LocateEffect(){}
    public static void receive(RemoteNetwork.Locate locate){dimension=locate.dimension();target=locate.pos();shownSince=0;waited=0;}
    private static float shownTicks(){return shownSince==0?0:(System.nanoTime()-shownSince)/50_000_000f;}
    private static boolean visible(Minecraft mc){
        return target!=null&&mc.level!=null&&mc.screen==null&&mc.level.dimension().location().equals(dimension);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(target==null)return;
        if(mc.level==null||++waited>WAIT_TICKS){target=null;return;}
        if(!visible(mc)||mc.isPaused())return;
        if(shownSince==0)shownSince=System.nanoTime();
        if(shownTicks()>SHOW_TICKS){target=null;return;}
        var random=mc.level.random;
        for(int i=0;i<2;i++)mc.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD,true,
            target.getX()+.5+(random.nextDouble()-.5)*.25,target.getY()+1+random.nextDouble()*.5,target.getZ()+.5+(random.nextDouble()-.5)*.25,
            (random.nextDouble()-.5)*.01,.3+random.nextDouble()*.1,(random.nextDouble()-.5)*.01);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_WEATHER)return;
        var mc=Minecraft.getInstance();if(!visible(mc))return;
        var camera=e.getCamera().getPosition();
        double x=target.getX()-camera.x,y=target.getY()-camera.y,z=target.getZ()-camera.z;
        // Edges keep about the same width on screen near and far, so a distant robot still stands out.
        double distance=Math.sqrt((x+.5)*(x+.5)+(y+.5)*(y+.5)+(z+.5)*(z+.5));
        float width=(float)Math.max(.03,distance*.006);
        // A soft pulse, fading out over the last second.
        if(shownSince==0)shownSince=System.nanoTime();
        float time=shownTicks();
        float alpha=(float)((.75+.25*Math.sin(time*.3))*Math.min(1,(SHOW_TICKS-time)/20f));
        if(alpha<=0)return;
        var pose=e.getPoseStack();pose.pushPose();pose.translate(x,y,z);var matrix=pose.last().pose();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        float lo=-.03f,hi=1.03f;
        for(int axis=0;axis<3;axis++)for(int a=0;a<2;a++)for(int b=0;b<2;b++){
            float[] from=new float[3],to=new float[3];
            int u=(axis+1)%3,v=(axis+2)%3;
            from[axis]=lo;to[axis]=hi;from[u]=to[u]=a==0?lo:hi;from[v]=to[v]=b==0?lo:hi;
            bar(buffer,matrix,from,to,axis,width,alpha);
        }
        // The column of light is drawn through blocks too, so a robot behind a wall or underground still shows its direction.
        column(buffer,matrix,Math.max(.12f,width*1.5f),Math.min(1,time/10f)*COLUMN_HEIGHT,alpha);
        var mesh=buffer.build();if(mesh!=null)BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.disableBlend();
        pose.popPose();
    }
    static final float COLUMN_HEIGHT=24;
    /** A thin upright shaft from the robot's top, fading out towards its upper end. */
    private static void column(BufferBuilder buffer,Matrix4f matrix,float width,float height,float alpha){
        float h=width/2,x0=.5f-h,x1=.5f+h,z0=.5f-h,z1=.5f+h,bottom=1.03f,top=bottom+height,a=alpha*.8f;
        float[][] sides={{x0,z0,x1,z0},{x1,z0,x1,z1},{x1,z1,x0,z1},{x0,z1,x0,z0}};
        for(var side:sides){
            buffer.addVertex(matrix,side[0],bottom,side[1]).setColor(1f,1f,1f,a);buffer.addVertex(matrix,side[2],bottom,side[3]).setColor(1f,1f,1f,a);
            buffer.addVertex(matrix,side[2],top,side[3]).setColor(1f,1f,1f,0f);buffer.addVertex(matrix,side[0],top,side[1]).setColor(1f,1f,1f,0f);
        }
    }
    /** One edge as a thin white box along the given axis. */
    private static void bar(BufferBuilder buffer,Matrix4f matrix,float[] from,float[] to,int axis,float width,float alpha){
        float h=width/2;
        float x0=from[0]-(axis==0?0:h),y0=from[1]-(axis==1?0:h),z0=from[2]-(axis==2?0:h);
        float x1=to[0]+(axis==0?0:h),y1=to[1]+(axis==1?0:h),z1=to[2]+(axis==2?0:h);
        float[][] faces={
            {x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1},{x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0},
            {x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0},{x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1},
            {x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0},{x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1}};
        for(var face:faces)for(int i=0;i<12;i+=3)buffer.addVertex(matrix,face[i],face[i+1],face[i+2]).setColor(1f,1f,1f,alpha);
    }
    @SubscribeEvent public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e){target=null;}
}
