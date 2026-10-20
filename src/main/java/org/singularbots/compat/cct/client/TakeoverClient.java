package org.singularbots.compat.cct.client;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.singularbots.client.EditorScreen;
import org.singularbots.network.ProgramNetwork;

/** The native client can acknowledge that no live editor exists, including while viewing the controller. */
public final class TakeoverClient {
    public static void receive(ProgramNetwork.TakeoverNotice notice){
        var mc=Minecraft.getInstance();var state=JsonParser.parseString(notice.json()).getAsJsonObject();
        boolean prepare=mc.player!=null&&mc.player.getUUID().equals(notice.library())&&state.has("phase")&&state.get("phase").getAsString().equals("preparing");
        if(mc.screen instanceof EditorScreen editor&&editor.takeover(notice,prepare))return;
        if(mc.screen instanceof RemoteScreen remote)remote.takeover(notice);
        if(prepare)ack(java.util.UUID.fromString(state.get("token").getAsString()),true);
    }
    public static void ack(java.util.UUID token,boolean saved){PacketDistributor.sendToServer(new ProgramNetwork.TakeoverAck(token,saved));}
}
