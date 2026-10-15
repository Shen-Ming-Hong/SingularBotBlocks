package org.singularbots.compat.cct;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.nio.file.Files;
import java.util.*;

/**
 * Classroom load measurement: 30 students with their own robots and libraries run programs while their editors
 * auto-save and poll. Opt-in with -PloadTest, so ordinary CI does not pay for it; otherwise each test passes at once.
 */
@GameTestHolder("singularbots")
@PrefixGameTestTemplate(false)
public final class ClassroomLoadGameTests {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final int STUDENTS=30,TICKS=200,SAVE_EVERY=14,POLL_EVERY=3,LIST_EVERY=100;
    private static boolean enabled(){return System.getProperty("ccedu.loadTest")!=null;}
    private static ServerPlayer player(GameTestHelper h){var player=h.makeMockServerPlayerInLevel();NetworkRegistry.configureMockConnection(player.connection.getConnection());return player;}
    private static JuniorTurtleEntity robot(GameTestHelper h,BlockPos pos,ServerPlayer owner){
        h.setBlock(pos,TeachingContent.JUNIOR.get());var t=(JuniorTurtleEntity)h.getBlockEntity(pos);
        ((dan200.computercraft.shared.turtle.core.TurtleBrain)t.getAccess()).setOwningPlayer(owner.getGameProfile());t.placedBy(owner,true);
        var center=t.getBlockPos().getCenter();owner.teleportTo(center.x,center.y,center.z);RemoteService.track(t);return t;
    }
    /** Loose comment stacks are drafts that never run; they give a saved program a realistic size. */
    private static JsonObject filler(String name,int bytes){
        var blocks=new JsonArray();var text="x".repeat(100);
        for(int i=0,size=2;size<bytes;i++){var b=new JsonObject();b.addProperty("type","ccedu_comment");b.addProperty("id","c"+i);var f=new JsonObject();f.addProperty("TEXT",text);b.add("fields",f);blocks.add(b);size+=b.toString().length()+1;}
        var workspace=new JsonObject();var top=new JsonObject();top.add("blocks",blocks);workspace.add("blocks",top);
        var p=new JsonObject();p.addProperty("name",name);p.addProperty("mode","blocks");p.add("workspace",workspace);return p;
    }
    /** When the program starts: turn right 1000 times, paced like a student's robot. */
    private static JsonObject turning(){
        return JsonParser.parseString("{\"name\":\"轉圈\",\"mode\":\"blocks\",\"workspace\":{\"blocks\":{\"blocks\":[{\"type\":\"ccedu_start\",\"id\":\"load-start\",\"next\":{\"block\":{\"type\":\"controls_repeat_ext\",\"id\":\"load-repeat\",\"inputs\":{\"TIMES\":{\"block\":{\"type\":\"math_number\",\"id\":\"load-times\",\"fields\":{\"NUM\":1000}}},\"DO\":{\"block\":{\"type\":\"ccedu_turtle_command\",\"id\":\"load-turn\",\"fields\":{\"COMMAND\":\"turnRight\"}}}}}}}]}}}").getAsJsonObject();
    }
    private static JsonObject op(String op,UUID id){var q=new JsonObject();q.addProperty("op",op);q.addProperty("id",id.toString());return q;}
    /** Writes library files directly: seeding through save() would itself be the quadratic cost being measured. */
    private static void seed(GameTestHelper h,UUID student,int programs,int bytes)throws java.io.IOException{
        var dir=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("singularbots/students").resolve(student.toString());Files.createDirectories(dir);
        for(int i=0;i<programs;i++){var id=UUID.randomUUID();var p=filler("練習 "+i,bytes);p.addProperty("version",1);p.addProperty("id",id.toString());p.addProperty("revision",1);Files.writeString(dir.resolve(id+".json"),p.toString());}
    }
    private static void classroom(GameTestHelper h,String label,int programs,int bytes,double maxAverageMillis,double maxP95Millis){
        if(!enabled()){h.succeed();return;}
        var players=new ArrayList<ServerPlayer>();var robots=new ArrayList<JuniorTurtleEntity>();var drafts=new ArrayList<UUID>();var revisions=new long[STUDENTS];
        try{
            for(int i=0;i<STUDENTS;i++){
                var owner=player(h);var t=robot(h,new BlockPos(1+2*(i%6),2,1+2*(i/6)),owner);players.add(owner);robots.add(t);
                seed(h,owner.getUUID(),programs,bytes);RemoteService.open(owner,t);
                var run=UUID.randomUUID();var save=op("programSave",run);save.addProperty("revision",0);save.add("program",turning());ProgramService.handle(owner,t,save);
                ProgramService.handle(owner,t,op("programRun",run));
                var draft=UUID.randomUUID();drafts.add(draft);var first=op("programSave",draft);first.addProperty("revision",0);first.add("program",filler("作業",bytes));
                revisions[i]=ProgramService.handle(owner,t,first).getAsJsonObject("program").get("revision").getAsLong();
            }
        }catch(Exception e){throw new GameTestAssertException("Classroom setup failed: "+e);}
        // Built before measuring: in a real server the request arrives already parsed, so only the mod's handling is timed.
        var draftProgram=filler("作業",bytes);
        var costs=new ArrayList<Long>();var saves=new int[1];var tick=new int[1];var tickNanos=new ArrayList<Long>();var server=h.getLevel().getServer();
        h.onEachTick(()->{
            if(tick[0]>=TICKS)return;int now=tick[0]++;long start=System.nanoTime();
            for(int i=0;i<STUDENTS;i++){
                var owner=players.get(i);var t=robots.get(i);
                try{
                    // An editor auto-saves 0.7 s after the student stops typing, then refreshes its library.
                    if((now+i)%SAVE_EVERY==0){var save=op("programSave",drafts.get(i));save.addProperty("revision",revisions[i]);save.add("program",draftProgram);
                        revisions[i]=ProgramService.handle(owner,t,save).getAsJsonObject("program").get("revision").getAsLong();ProgramService.handle(owner,t,op("programList",drafts.get(i)));saves[0]++;}
                    if((now+i)%POLL_EVERY==0)ProgramService.handle(owner,t,op("programState",drafts.get(i)));
                    if((now+i*3)%LIST_EVERY==0)ProgramService.handle(owner,t,op("programList",drafts.get(i)));
                }catch(Exception e){throw new GameTestAssertException("Student "+i+" request failed at tick "+now+": "+e);}
            }
            costs.add(System.nanoTime()-start);tickNanos.add(server.getAverageTickTimeNanos());
        });
        h.runAfterDelay(TICKS+5,()->{
            for(var t:robots)ProgramService.stop(t);for(var p:players)server.getPlayerList().remove(p);
            var sorted=costs.stream().sorted().toList();
            double average=sorted.stream().mapToLong(Long::longValue).average().orElse(0)/1e6,p95=sorted.get((int)(sorted.size()*0.95))/1e6,max=sorted.getLast()/1e6;
            double serverTick=tickNanos.stream().mapToLong(Long::longValue).max().orElse(0)/1e6;
            LOG.info("CCEDU-LOAD {} students={} programs={} bytes={} saves={} modMsPerTick avg={} p95={} max={} serverAvgTickMsPeak={}",label,STUDENTS,programs,bytes,saves[0],String.format("%.2f",average),String.format("%.2f",p95),String.format("%.2f",max),String.format("%.2f",serverTick));
            // Wait for all 30 computers to power off, so the next batch does not start behind a busy computer thread.
            h.startSequence().thenWaitUntil(()->h.assertTrue(robots.stream().allMatch(t->t.getServerComputer()==null||!t.getServerComputer().isOn()),"Classroom robots are still shutting down"))
                .thenExecute(()->h.assertTrue(average<=maxAverageMillis&&p95<=maxP95Millis,label+": program requests took avg "+String.format("%.2f",average)+" ms, p95 "+String.format("%.2f",p95)+" ms per tick; budget avg "+maxAverageMillis+" / p95 "+maxP95Millis))
                .thenSucceed();
        });
    }
    /** A typical class: 20 programs of about 10 KB per student. */
    @GameTest(template="empty",batch="classroomLoad",timeoutTicks=TICKS+400)
    public static void typicalClassroomStaysWithinTickBudget(GameTestHelper h){classroom(h,"typical",20,10_000,10,25);}
    /** Heavy libraries: 50 programs of about 25 KB per student. Budget is a full 50 ms tick. */
    @GameTest(template="empty",batch="classroomLoadHeavy",timeoutTicks=TICKS+400)
    public static void heavyLibrariesStayWithinTickBudget(GameTestHelper h){classroom(h,"heavy",50,25_000,25,50);}
}
