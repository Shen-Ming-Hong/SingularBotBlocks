package org.singularbots.program;

import com.google.gson.*;
import java.util.*;

/** Shared across turtle moves; accessed by the Lua worker and server thread. */
public final class ProgramRuntime {
    private static final Gson GSON=new Gson();
    public enum Status { STOPPED,RUNNING,PAUSED,ERROR }
    private Status status=Status.STOPPED;
    private String source,blockId="",error="";
    private int steps,currentSlot=-1,errorLine;
    private long generation,lastBeat=Long.MIN_VALUE/2;
    /** Each Blockly statement is held for one beat: CC's 8-tick move/turn animation plus a 2-tick rest. */
    public static final int BEAT_TICKS=10;
    private boolean instrumented=true;
    private Map<Integer,String> lines=Map.of();
    private record Context(String id,String name,Map<Integer,String> lines){}
    private record Frame(Context context,String blockId,Map<String,Object> variables){}
    private Context context=new Context("","",Map.of());
    private final Map<String,Context> libraries=new HashMap<>();
    private final Deque<Frame> frames=new ArrayDeque<>();
    private final Map<String,Object> variables=new LinkedHashMap<>();
    public synchronized void prepare(WorkspaceCompiler.Compiled compiled,boolean step){prepare(compiled,step,"","");}
    public synchronized void prepare(WorkspaceCompiler.Compiled compiled,boolean step,String id,String name){source=compiled.lua();lines=compiled.lines();context=new Context(id,name,lines);libraries.clear();frames.clear();instrumented=!compiled.blockIds().isEmpty();blockId="";error="";errorLine=0;variables.clear();status=step?Status.PAUSED:Status.RUNNING;steps=step?1:0;lastBeat=Long.MIN_VALUE/2;}
    public synchronized void library(String id,String name,WorkspaceCompiler.Compiled compiled){libraries.put(name,new Context(id,name,compiled.lines()));}
    public synchronized void enterLibrary(String name){var next=libraries.get(name);if(next==null)throw new IllegalArgumentException("Unknown library program");if(frames.size()>=64)throw new IllegalArgumentException("Program call depth exceeds 64");frames.push(new Frame(context,blockId,new LinkedHashMap<>(variables)));context=next;lines=next.lines;blockId="";variables.clear();}
    public synchronized void leaveLibrary(){if(frames.isEmpty())throw new IllegalStateException("No library call to return from");var frame=frames.pop();context=frame.context;lines=context.lines;blockId=frame.blockId;variables.clear();variables.putAll(frame.variables);}
    public synchronized boolean rawCommand(){if(instrumented||status!=Status.PAUSED)return true;if(steps>0){steps--;return true;}return false;}
    public synchronized boolean instrumented(){return instrumented;}
    public synchronized String take(){String result=source;source=null;return result;}
    public synchronized boolean pending(){return source!=null;}
    public synchronized void stop(){generation++;status=Status.STOPPED;source=null;steps=0;}
    public synchronized long generation(){return generation;}
    public synchronized void running(){status=Status.RUNNING;steps=0;}
    public synchronized void pause(){if(status==Status.RUNNING)status=Status.PAUSED;}
    public synchronized void step(){if(status==Status.PAUSED)steps=1;}
    public synchronized void errored(String message){status=Status.ERROR;error=message.length()>2048?message.substring(0,2048):message;errorLine=0;String chunk=frames.isEmpty()?"edu-program":"edu:"+context.name;var match=java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(chunk)+":(\\d+)").matcher(error);if(match.find()){try{errorLine=Integer.parseInt(match.group(1));blockId=lines.getOrDefault(errorLine,blockId);}catch(NumberFormatException ignored){}}}
    public synchronized void erroredAt(String message,String id){errored(message);if(id.length()<=128)blockId=id;}
    public synchronized void visit(String id,Map<?,?> values){
        if(id.length()>128)throw new IllegalArgumentException("Invalid block ID");blockId=id;variables.clear();
        for(var e:values.entrySet()){if(variables.size()==128)break;putVariable(String.valueOf(e.getKey()),e.getValue());}
    }
    /** Called when the robot is idle; returns how many ticks to wait so statements start on a steady beat. */
    public synchronized int beat(long now){long start=Math.max(now,lastBeat+BEAT_TICKS);lastBeat=start;return (int)(start-now);}
    public synchronized long lastBeat(){return lastBeat;}
    public synchronized boolean permit(){if(status==Status.STOPPED||status==Status.ERROR)throw new IllegalStateException("Program stopped");if(status==Status.RUNNING)return true;if(steps>0){steps--;return true;}return false;}
    public synchronized void putVariable(String name,Object value){
        if(name.length()>128||variables.size()>=128&&!variables.containsKey(name))return;
        if(value instanceof Number number&&!Double.isFinite(number.doubleValue()))value=number.toString();
        if(value==null||value instanceof Number||value instanceof Boolean)variables.put(name,value);
        else {String text=value instanceof String s?s:"table / object";int end=Math.min(256,text.length());if(end<text.length()&&end>0&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))end--;variables.put(name,text.substring(0,end));}
    }
    public synchronized void clearVariable(String name){variables.remove(name);}
    public synchronized void clearVariables(){variables.clear();}
    public synchronized void slot(int value){currentSlot=value;}
    public synchronized int slot(){return currentSlot;}
    public synchronized JsonObject json(){var j=new JsonObject();j.addProperty("status",status.name().toLowerCase(Locale.ROOT));j.addProperty("debugMode",instrumented?"blocks":"actions");j.addProperty("blockId",blockId);j.addProperty("programId",context.id);j.addProperty("programName",context.name);j.addProperty("callDepth",frames.size());var stack=new JsonArray();for(var it=frames.descendingIterator();it.hasNext();)stack.add(it.next().context.id);stack.add(context.id);j.add("callStack",stack);j.addProperty("error",error);j.addProperty("errorLine",errorLine);j.add("variables",GSON.toJsonTree(variables));return j;}
}
