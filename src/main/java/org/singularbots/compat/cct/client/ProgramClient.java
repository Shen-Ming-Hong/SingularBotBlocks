package org.singularbots.compat.cct.client;

import com.google.gson.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.singularbots.network.ProgramNetwork;
import org.singularbots.program.Fragments;
import java.util.*;
import java.util.function.Consumer;

/** Client requests have bounded lifetimes and disappear when their editor closes. */
public final class ProgramClient {
    private record Pending(UUID turtle,long started,Consumer<String> success,Consumer<String> failure) {}
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final Map<UUID,Fragments> REPLIES=new HashMap<>();
    public static void request(UUID turtle,JsonObject message,Consumer<String> success,Consumer<String> failure){
        UUID id=UUID.randomUUID();java.util.List<String> parts;
        try{parts=Fragments.split(message.toString());}catch(IllegalArgumentException e){failure.accept(e.getMessage());return;}
        if(PENDING.size()>=8){failure.accept("Program request exceeds limit");return;}
        PENDING.put(id,new Pending(turtle,System.currentTimeMillis(),success,failure));
        for(int i=0;i<parts.size();i++)PacketDistributor.sendToServer(new ProgramNetwork.Fragment(turtle,id,i,parts.size(),parts.get(i)));
    }
    public static void receive(ProgramNetwork.Reply reply){
        var pending=PENDING.get(reply.request());if(pending==null||!pending.turtle.equals(reply.turtle()))return;
        try{var assembled=REPLIES.computeIfAbsent(reply.request(),id->new Fragments(id,reply.turtle(),reply.total(),System.currentTimeMillis()));String text=assembled.append(reply.index(),reply.total(),reply.data());if(text==null)return;PENDING.remove(reply.request());REPLIES.remove(reply.request());pending.success.accept(text);}
        catch(Exception e){PENDING.remove(reply.request());REPLIES.remove(reply.request());pending.failure.accept(e.getMessage());}
    }
    public static void tick(){long now=System.currentTimeMillis();for(var id:new ArrayList<>(PENDING.keySet())){var p=PENDING.get(id);if(now-p.started>20000){PENDING.remove(id);REPLIES.remove(id);p.failure.accept("Server request timed out; program data was retained");}}}
    public static void close(UUID turtle){for(var id:new ArrayList<>(PENDING.keySet())){var p=PENDING.get(id);if(p.turtle.equals(turtle)){PENDING.remove(id);REPLIES.remove(id);p.failure.accept("Editor closed");}}}
}
