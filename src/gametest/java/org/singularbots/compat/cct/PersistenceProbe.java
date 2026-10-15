package org.singularbots.compat.cct;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.singularbots.program.ProgramStore;
import java.nio.file.*;
import java.util.UUID;

/**
 * Opt-in, development-only probe. Real server processes share one isolated world:
 * prepare/verify use clean stops; crash is killed by the script without saving, then recover checks it.
 */
@EventBusSubscriber(modid="singularbots")
public final class PersistenceProbe {
    private static final BlockPos START=new BlockPos(1,200,1);
    private static final BlockPos FAR=new BlockPos(4096,100,4096);
    private static final UUID OWNER=UUID.fromString("03c7edeb-4aa4-492a-824f-7f29750af98d"),PROGRAM=UUID.fromString("775740ac-5835-4094-848b-8e1fd953a81a");
    private static final UUID OTHER=UUID.fromString("5b0e4c55-3f43-4f63-9b0f-4d2a2a6c8e11"),OTHER_PROGRAM=UUID.fromString("0f6f1d8e-2b7a-4c7e-9a43-61f1b8f0c2d4"),CRASH=UUID.fromString("c1a5e7d9-4b3f-4e2a-9d68-7f0a1b2c3d4e");
    private static UUID identity,farIdentity;
    private static JuniorTurtleEntity unloaded;
    private static int phase,ticks;
    private static boolean finished;
    private static String restoreError;
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void contents(JuniorTurtleEntity t){
        check("重啟海龜".equals(t.getLabel()),"Name did not survive restart");check(t.getColour()==0xcc4c4c&&t.decoration().equals("crown"),"Appearance did not survive restart");
        check(t.getAccess().getOwningPlayer()!=null&&t.getAccess().getOwningPlayer().getId().equals(OWNER),"Owner did not survive restart");
        check(t.getAccess().getInventory().getItem(7).is(Items.DIAMOND)&&t.getAccess().getInventory().getItem(7).getCount()==9&&t.getAccess().getSelectedSlot()==7,"Inventory/selection did not survive restart");
        check(PROGRAM.equals(t.selectedProgram()),"Selected program did not survive restart");
    }
    private static JsonObject named(String name){
        return JsonParser.parseString("{\"name\":\""+name+"\",\"mode\":\"blocks\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_turtle_command\",\"id\":\"probe-up\",\"fields\":{\"COMMAND\":\"up\"}}]}}}").getAsJsonObject();
    }
    private static boolean contains(JsonArray rows,UUID id){for(var row:rows)if(row.getAsJsonObject().get("id").getAsString().equals(id.toString()))return true;return false;}
    private static Path data(net.minecraft.server.MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("singularbots");}
    /** Two students' libraries and history stay separate across restarts. */
    private static void libraries(net.minecraft.server.level.ServerLevel level)throws Exception{
        var store=ProgramService.store(level);var history=StudentPrograms.history(level);
        check(contains(store.list(OWNER),PROGRAM)&&!contains(store.list(OWNER),OTHER_PROGRAM),"First student's library changed across restart");
        check(store.list(OTHER).size()==1&&store.load(OTHER,OTHER_PROGRAM).get("name").getAsString().equals("同學的程式"),"Second student's library did not survive restart");
        boolean recorded=false;for(var row:history.list(OWNER))recorded|=row.getAsJsonObject().get("kind").getAsString().equals("probe");
        check(recorded,"Activity history did not survive restart");
        for(var row:history.list(OTHER))check(!row.getAsJsonObject().get("kind").getAsString().equals("probe"),"History leaked into another student");
    }
    private static UUID preparedIdentity(Path output)throws Exception{return UUID.fromString(JsonParser.parseString(Files.readString(output.resolve("persistence-prepare.json"))).getAsJsonObject().get("identity").getAsString());}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var mode=System.getProperty("ccedu.persistenceSmoke");if(mode==null||finished)return;
        var server=event.getServer();var level=server.overworld();var output=Path.of(System.getProperty("ccedu.persistenceOutput"));
        try{
            if(++ticks>(mode.equals("crash")?1200:400))throw new IllegalStateException("Persistence probe timed out at phase "+phase);
            if(phase==0){
                if(mode.equals("prepare")){
                    level.setBlock(START,TeachingContent.JUNIOR.get().defaultBlockState(),Block.UPDATE_ALL);
                    var t=(JuniorTurtleEntity)level.getBlockEntity(START);RemoteService.track(t);identity=t.identity();
                    ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(new GameProfile(OWNER,"restart-owner"));
                    t.setLabel("重啟海龜");t.getAccess().setColour(0xcc4c4c);t.decorate("crown");t.getAccess().getInventory().setItem(7,new ItemStack(Items.DIAMOND,9));t.getAccess().setSelectedSlot(7);
                    var program=JsonParser.parseString("{\"name\":\"跨重啟程式\",\"mode\":\"blocks\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_turtle_command\",\"id\":\"restart-up\",\"fields\":{\"COMMAND\":\"up\"}}]}}}").getAsJsonObject();
                    ProgramService.store(level).save(OWNER,PROGRAM,0,program);t.selectProgram(PROGRAM);check(Restoration.save(t.getAccess()).isSuccess(),"Snapshot creation failed");
                    ProgramService.store(level).save(OTHER,OTHER_PROGRAM,0,named("同學的程式"));
                    var record=new JsonObject();record.addProperty("kind","probe");record.addProperty("message","跨重啟紀錄");StudentPrograms.history(level).append(OWNER,record);
                    t.getAccess().executeCommand(RemoteService.command("up"));phase=1;
                }else if(mode.equals("verify")){
                    identity=preparedIdentity(output);
                    level.getChunkAt(START);var t=(JuniorTurtleEntity)level.getBlockEntity(START.above());check(t!=null&&t.identity().equals(identity),"Position/identity did not survive restart");contents(t);
                    check(t.locked()&&t.snapshot()!=null,"Lock or restoration journal did not survive restart");check(!TeachingSettings.get(server).suggestions(),"Teacher settings did not survive restart");
                    var settings=TeachingSettings.get(server);check(settings.luaAllowed()&&settings.sharing(),"Class lua/sharing settings did not survive restart");libraries(level);
                    var program=ProgramService.store(level).load(OWNER,PROGRAM);var compiled=ProgramStore.compile(program);check(compiled.blockIds().contains("restart-up"),"Stable block IDs did not survive restart");
                    t.lock(false);t.runtime().prepare(compiled,false,PROGRAM.toString(),program.get("name").getAsString());var computer=t.createServerComputer();computer.turnOn();computer.queueEvent("ccedu_run");phase=2;
                }else if(mode.equals("crash")){
                    identity=preparedIdentity(output);level.getChunkAt(START);var t=(JuniorTurtleEntity)level.getBlockEntity(START);check(t!=null&&t.identity().equals(identity),"Restored robot missing before crash phase");contents(t);libraries(level);
                    // A robot far from spawn, in a chunk that only a forced ticket keeps loaded, as on a dedicated server with nobody nearby.
                    var chunk=new ChunkPos(FAR);level.setChunkForced(chunk.x,chunk.z,true);level.getChunkAt(FAR);
                    level.setBlock(FAR,TeachingContent.JUNIOR.get().defaultBlockState(),Block.UPDATE_ALL);var far=(JuniorTurtleEntity)level.getBlockEntity(FAR);RemoteService.track(far);
                    ((dan200.computercraft.shared.turtle.core.TurtleBrain)far.getAccess()).setOwningPlayer(new GameProfile(OTHER,"unload-owner"));far.setLabel("卸載海龜");far.getAccess().getInventory().setItem(3,new ItemStack(Items.EMERALD,5));far.changed();
                    unloaded=far;farIdentity=far.identity();level.setChunkForced(chunk.x,chunk.z,false);phase=10;
                }else if(mode.equals("recover")){
                    identity=preparedIdentity(output);level.getChunkAt(START);var t=(JuniorTurtleEntity)level.getBlockEntity(START);check(t!=null&&t.identity().equals(identity),"Robot identity changed after the server was killed");contents(t);libraries(level);
                    var crashed=ProgramService.store(level).load(OWNER,CRASH);check(crashed.get("revision").getAsLong()==2&&crashed.get("name").getAsString().equals("當機前最後版本"),"Program saved before the kill was lost or half-written");
                    try(var files=Files.walk(data(server))){check(files.noneMatch(file->file.getFileName().toString().endsWith(".tmp")),"Killed server left temporary save files");}
                    finish(output,mode,true,"");
                }else throw new IllegalArgumentException("Unknown persistence probe mode");
            }else if(phase==1){
                if(!(level.getBlockEntity(START.above()) instanceof JuniorTurtleEntity t))return;
                contents(t);t.lock(true);var settings=TeachingSettings.get(server);settings.set(server,"suggestions",false);for(var key:java.util.List.of("lua","sharing"))settings.set(server,key,true);t.changed();finish(output,mode,true,"");
            }else if(phase==2){
                if(!(level.getBlockEntity(START.above(2)) instanceof JuniorTurtleEntity t)||!t.runtime().json().get("status").getAsString().equals("stopped"))return;
                // Match the production restore path: stop the computer before queuing restoration.
                contents(t);ProgramService.stop(t);t.getAccess().executeCommand(access->{var result=t.snapshot().restore(access);if(!result.isSuccess())restoreError=result.getErrorMessage();return result;});phase=3;
            }else if(phase==3){
                if(restoreError!=null)throw new IllegalStateException("Persisted restoration failed: "+restoreError);
                if(!(level.getBlockEntity(START) instanceof JuniorTurtleEntity t))return;
                contents(t);check(t.identity().equals(identity),"Persisted restore changed identity");finish(output,mode,true,"");
            }else if(phase==10){
                if(!unloaded.isRemoved())return;
                check(RemoteService.resolve(level,farIdentity)==null,"Unloaded robot still accepts remote control");
                level.getChunkAt(FAR);if(!(level.getBlockEntity(FAR) instanceof JuniorTurtleEntity r)||r==unloaded)throw new IllegalStateException("Robot missing after its chunk reloaded");
                RemoteService.track(r);check(r.identity().equals(farIdentity),"Chunk unload changed robot identity");check("卸載海龜".equals(r.getLabel()),"Chunk unload lost robot name");
                check(r.getAccess().getOwningPlayer()!=null&&r.getAccess().getOwningPlayer().getId().equals(OTHER),"Chunk unload lost robot owner");
                check(r.getAccess().getInventory().getItem(3).is(Items.EMERALD)&&r.getAccess().getInventory().getItem(3).getCount()==5,"Chunk unload lost robot inventory");
                // Saved programs are written atomically, independent of world saves; the script kills the server right after this report.
                var store=ProgramService.store(level);store.save(OWNER,CRASH,0,named("當機前程式"));store.save(OWNER,CRASH,1,named("當機前最後版本"));finish(output,mode,true,"");
            }
        }catch(Exception failure){try{finish(output,mode,false,failure.toString());}catch(Exception writing){throw new RuntimeException(writing);}}
    }
    private static void finish(Path output,String mode,boolean passed,String diagnostic)throws Exception{
        var report=new JsonObject();report.addProperty("passed",passed);report.addProperty("phase",mode);if(identity!=null)report.addProperty("identity",identity.toString());report.addProperty("diagnostic",diagnostic);
        Files.writeString(output.resolve("persistence-"+mode+".json"),report.toString());finished=true;
    }
}
