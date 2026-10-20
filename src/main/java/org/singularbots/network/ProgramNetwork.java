package org.singularbots.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.singularbots.compat.cct.ProgramService;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid="singularbots")
public final class ProgramNetwork {
    public static final int CHUNK=org.singularbots.program.Fragments.CHUNK,MAX_CHUNKS=org.singularbots.program.Fragments.MAX_CHUNKS;
    public static Consumer<Reply> onReply=p->{};
    public static Consumer<TakeoverNotice> onTakeover=p->{};
    public record TakeoverNotice(UUID library,String json) implements CustomPacketPayload {
        public static final Type<TakeoverNotice> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("singularbots","takeover_notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TakeoverNotice> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.library);b.writeUtf(p.json,4096);},b->new TakeoverNotice(b.readUUID(),b.readUtf(4096)));
        public Type<TakeoverNotice> type(){return TYPE;}
    }
    public record TakeoverAck(UUID token,boolean saved) implements CustomPacketPayload {
        public static final Type<TakeoverAck> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("singularbots","takeover_ack"));
        public static final StreamCodec<RegistryFriendlyByteBuf,TakeoverAck> CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.token);b.writeBoolean(p.saved);},b->new TakeoverAck(b.readUUID(),b.readBoolean()));
        public Type<TakeoverAck> type(){return TYPE;}
    }
    public record Fragment(UUID turtle,UUID request,int index,int total,String data) implements CustomPacketPayload {
        public static final Type<Fragment> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("singularbots","program_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Fragment> CODEC=StreamCodec.of((b,p)->write(b,p.turtle,p.request,p.index,p.total,p.data),b->new Fragment(b.readUUID(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUtf(CHUNK)));
        public Type<Fragment> type(){return TYPE;}
    }
    public record Reply(UUID turtle,UUID request,int index,int total,String data) implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("singularbots","program_reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=StreamCodec.of((b,p)->write(b,p.turtle,p.request,p.index,p.total,p.data),b->new Reply(b.readUUID(),b.readUUID(),b.readVarInt(),b.readVarInt(),b.readUtf(CHUNK)));
        public Type<Reply> type(){return TYPE;}
    }
    private static void write(RegistryFriendlyByteBuf b,UUID turtle,UUID request,int index,int total,String data){b.writeUUID(turtle);b.writeUUID(request);b.writeVarInt(index);b.writeVarInt(total);b.writeUtf(data,CHUNK);}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("singularbots").versioned("1");
        r.playToServer(Fragment.TYPE,Fragment.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)ProgramService.fragment(player,p);}));
        r.playToClient(TakeoverNotice.TYPE,TakeoverNotice.CODEC,(p,c)->c.enqueueWork(()->onTakeover.accept(p)));
        r.playToServer(TakeoverAck.TYPE,TakeoverAck.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)org.singularbots.compat.cct.TeacherTakeover.acknowledge(player,p);}));
        r.playToClient(Reply.TYPE,Reply.CODEC,(p,c)->c.enqueueWork(()->onReply.accept(p)));
    }
}
