package org.singularbots.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.singularbots.compat.cct.RemoteService;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid="singularbots")
public final class RemoteNetwork {
    public static Consumer<State> onState=state->{};
    public record Action(UUID turtle,String action,int value,String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("singularbots","remote_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.turtle);buf.writeUtf(p.action,32);buf.writeVarInt(p.value);buf.writeUtf(p.text,128);},
            buf->new Action(buf.readUUID(),buf.readUtf(32),buf.readVarInt(),buf.readUtf(128)));
        public Type<Action> type() { return TYPE; }
    }
    public record State(UUID turtle,String json) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("singularbots","remote_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of(
            (buf,p)->{buf.writeUUID(p.turtle);buf.writeUtf(p.json,12288);},buf->new State(buf.readUUID(),buf.readUtf(12288)));
        public Type<State> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("singularbots").versioned("1");
        r.playToServer(Action.TYPE,Action.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player) RemoteService.handle(player,p);}));
        r.playToClient(State.TYPE,State.CODEC,(p,c)->c.enqueueWork(()->onState.accept(p)));
    }
}
