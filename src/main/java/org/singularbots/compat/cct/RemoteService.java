package org.singularbots.compat.cct;

import com.google.gson.JsonObject;
import dan200.computercraft.api.turtle.*;
import dan200.computercraft.shared.turtle.core.*;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.singularbots.network.RemoteNetwork;
import org.singularbots.turtle.*;
import java.util.*;
import java.lang.ref.WeakReference;

/** All pinned CC internals stay in this package. Commands execute through public ITurtleAccess. */
@EventBusSubscriber(modid="singularbots")
public final class RemoteService {
    private static final Map<ServerLevel,Map<UUID,WeakReference<JuniorTurtleEntity>>> TURTLES=new WeakHashMap<>();
    public static void track(JuniorTurtleEntity t) {
        if (t.getLevel() instanceof ServerLevel level && !t.isRemoved()) {
            var turtles=TURTLES.computeIfAbsent(level,k->new HashMap<>());
            var ref=turtles.get(t.identity());var existing=ref==null?null:ref.get();
            // Movement transfers the same brain. Creative cloning must not share a library or remote identity.
            if(existing!=null&&existing!=t&&!existing.isRemoved()&&existing.getAccess()!=t.getAccess())t.detachClone();
            var identities=TurtleIdentities.get(level.getServer());
            if(!identities.claim(t)){t.detachClone();identities.claim(t);}
            turtles.put(t.identity(),new WeakReference<>(t));
        }
    }
    public static JuniorTurtleEntity resolve(ServerLevel level,UUID id) {
        var ref=TURTLES.getOrDefault(level,Map.of()).get(id); var t=ref==null ? null : ref.get();
        return t!=null && !t.isRemoved() && level.hasChunkAt(t.getBlockPos()) && level.getBlockEntity(t.getBlockPos())==t ? t : null;
    }
    public static java.util.List<JuniorTurtleEntity> loaded(ServerLevel level){var result=new java.util.ArrayList<JuniorTurtleEntity>();for(var id:new java.util.ArrayList<>(TURTLES.getOrDefault(level,Map.of()).keySet())){var t=resolve(level,id);if(t!=null)result.add(t);}return result;}
    public static boolean authorized(ServerPlayer p,JuniorTurtleEntity t) {
        var owner=t.getAccess().getOwningPlayer();
        return AccessPolicy.permits(p.getUUID(),owner==null?null:owner.getId(),p.hasPermissions(2),t.locked(),p.level()==t.getLevel(),
            !t.isRemoved() && p.serverLevel().hasChunkAt(t.getBlockPos()),p.position().distanceToSqr(t.getBlockPos().getCenter()));
    }
    /** A student who clicks a classmate's robot is told whose it is, never shown its code or controls. */
    public static Component denial(ServerPlayer p,JuniorTurtleEntity t){
        var owner=t.getAccess().getOwningPlayer();
        if(owner!=null&&!p.hasPermissions(2)&&!p.getUUID().equals(owner.getId())&&owner.getName()!=null&&!owner.getName().isBlank())return Component.translatable("message.singularbots.owned",owner.getName());
        return Component.translatable("message.singularbots.denied");
    }
    public static void open(ServerPlayer p,JuniorTurtleEntity t) {
        track(t);
        if (!authorized(p,t)) { p.displayClientMessage(denial(p,t),true); return; }
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new RemoteMenu(id,inv,t.identity()),Component.translatable("screen.singularbots.remote")),buf->buf.writeUUID(t.identity()));
        send(p,t,"");
    }
    public static boolean session(ServerPlayer p,UUID id) { return p.containerMenu instanceof RemoteMenu menu && menu.turtleId.equals(id); }
    public static void handle(ServerPlayer p,RemoteNetwork.Action action) {
        var t=resolve(p.serverLevel(),action.turtle());
        if (t==null || !session(p,action.turtle()) || !authorized(p,t)) { p.displayClientMessage(Component.translatable("message.singularbots.denied"),true); return; }
        try {
            if (action.action().equals("state")) { send(p,t,""); return; }
            switch (action.action()) {
                case "name" -> { t.setLabel(Appearance.label(action.text())); t.changed(); StudentPrograms.rememberLook(t,"name"); }
                case "colour" -> { t.getAccess().setColour(Appearance.colour(action.value())); StudentPrograms.rememberLook(t,"colour"); }
                case "decoration" -> { t.decorate(Appearance.decoration(action.value())); StudentPrograms.rememberLook(t,"decoration"); }
                case "select" -> { if(action.value()<0||action.value()>=16) throw new IllegalArgumentException("Invalid inventory slot"); t.getAccess().setSelectedSlot(action.value()); }
                case "lock" -> { if(!p.hasPermissions(2)) throw new IllegalArgumentException("Teacher permission required"); t.lock(action.value()!=0); }
                default -> {
                    TurtleCommand command=command(action.action());
                    // CC serialises commands after each animation and bounds its queue.
                    // Do not reject normal clicks with an unrelated per-player timer.
                    var queued=t.getAccess().executeCommand(new CheckedRemoteCommand(p,action.turtle(),command));
                    var immediate=queued.getResult();
                    if(queued.getCallback()==null&&immediate!=null&&immediate.length>1&&Boolean.FALSE.equals(immediate[0])){
                        send(p,t,String.valueOf(immediate[1]));return;
                    }
                }
            }
            send(p,t,"");
        } catch (RuntimeException error) { send(p,t,error.getMessage()); }
    }
    public record CheckedRemoteCommand(ServerPlayer player,UUID identity,TurtleCommand command) implements TurtleCommand {
        public boolean teacher(){return player.hasPermissions(2)&&session(player,identity);}
        public TurtleCommandResult rejected(String error){
            var current=resolve(player.serverLevel(),identity);
            if(current!=null&&session(player,identity))send(player,current,error);
            return TurtleCommandResult.failure(error);
        }
        @Override public TurtleCommandResult execute(ITurtleAccess access){
            var current=resolve(player.serverLevel(),identity);
            if(current==null||!session(player,identity)||!authorized(player,current))return rejected("Control permission expired");
            var result=command.execute(access);current=resolve(player.serverLevel(),identity);
            if(current!=null)send(player,current,result.isSuccess()?"":result.getErrorMessage());return result;
        }
    }
    static TurtleCommand command(String action) {
        return switch(action) {
            case "forward" -> new TurtleMoveCommand(MoveDirection.FORWARD);
            case "back" -> new TurtleMoveCommand(MoveDirection.BACK);
            case "up" -> new TurtleMoveCommand(MoveDirection.UP);
            case "down" -> new TurtleMoveCommand(MoveDirection.DOWN);
            case "turnLeft" -> new TurtleTurnCommand(TurnDirection.LEFT);
            case "turnRight" -> new TurtleTurnCommand(TurnDirection.RIGHT);
            case "dig" -> TurtleToolCommand.dig(InteractDirection.FORWARD,null);
            case "place" -> new TurtlePlaceCommand(InteractDirection.FORWARD,null);
            case "snapshot" -> org.singularbots.compat.cct.Restoration::save;
            case "restore" -> access->{var t=Restoration.owner(access);if(t==null||t.snapshot()==null)return TurtleCommandResult.failure("Save a snapshot first");ProgramService.stop(t);return t.snapshot().restore(access);};
            default -> throw new IllegalArgumentException("Unknown remote command");
        };
    }
    public static void send(ServerPlayer p,JuniorTurtleEntity t,String error) {
        var j=new JsonObject(); j.addProperty("name",t.getLabel()==null?"":t.getLabel()); j.addProperty("colour",t.getColour());
        j.addProperty("decoration",t.decoration()); j.addProperty("locked",t.locked()); j.addProperty("teacher",p.hasPermissions(2));
        j.addProperty("x",t.getBlockPos().getX()); j.addProperty("y",t.getBlockPos().getY()); j.addProperty("z",t.getBlockPos().getZ());
        j.addProperty("direction",t.getDirection().get3DDataValue()); j.addProperty("slot",t.getAccess().getSelectedSlot());
        j.addProperty("error",error==null?"":error);
        j.addProperty("historyDiagnostic",t.historyDiagnostic());
        j.addProperty("program",t.selectedProgram()==null?"":t.selectedProgram().toString());
        j.addProperty("hasSnapshot",t.snapshot()!=null);
        j.addProperty("runtimeStatus",t.runtime().json().get("status").getAsString());
        PacketDistributor.sendToPlayer(p,new RemoteNetwork.State(t.identity(),j.toString()));
    }
    public static void stopAll(net.minecraft.server.MinecraftServer server) {
        for(var entry:TURTLES.entrySet())if(entry.getKey().getServer()==server)for(var ref:entry.getValue().values()){
            var t=ref.get();if(t!=null&&!t.isRemoved())ProgramService.stop(t);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TURTLES.clear(); }
}
