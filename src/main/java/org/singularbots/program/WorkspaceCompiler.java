package org.singularbots.program;

import com.google.gson.*;
import java.util.*;

/** Server-owned Blockly JSON to Lua compiler. No client-generated code is accepted in block mode. */
public final class WorkspaceCompiler {
    public record Compiled(String lua,Map<Integer,String> lines,Set<String> blockIds) {}
    public static final class Invalid extends IllegalArgumentException {
        public final String blockId;
        Invalid(String id,String message){super(message);blockId=id;}
    }
    public static final Set<String> COMMANDS=Set.of("forward","back","up","down","turnLeft","turnRight","dig","digUp","digDown","place","placeUp","placeDown","attack","attackUp","attackDown","drop","dropUp","dropDown","suck","suckUp","suckDown","equipLeft","equipRight");
    public static final Set<String> QUERIES=Set.of("detect","detectUp","detectDown","compare","compareUp","compareDown","getItemCount","getItemSpace","getSelectedSlot");
    private static final Set<String> ASSERTED_COMMANDS=Set.of("forward","back","up","down","dig","digUp","digDown","place","placeUp","placeDown","drop","dropUp","dropDown","suck","suckUp","suckDown","equipLeft","equipRight");
    /** One toolbox block per kind of action (move, dig, place...), each accepting only its own commands. */
    public static final Map<String,Set<String>> ACTION_BLOCKS=Map.of(
        "ccedu_move",Set.of("forward","back","up","down","turnLeft","turnRight"),
        "ccedu_dig",Set.of("dig","digUp","digDown"),
        "ccedu_place",Set.of("place","placeUp","placeDown"),
        "ccedu_attack",Set.of("attack","attackUp","attackDown"),
        "ccedu_drop",Set.of("drop","dropUp","dropDown"),
        "ccedu_suck",Set.of("suck","suckUp","suckDown"),
        "ccedu_equip",Set.of("equipLeft","equipRight"));
    // ccedu_turtle_command (every command in one dropdown) and ccedu_turtle_action (with an amount) are no longer
    // in the toolbox but stay valid so programs saved with them still load and run.
    private static final Set<String> TYPES=withActionBlocks("ccedu_start","ccedu_turtle_command","ccedu_turtle_action","ccedu_turtle_result","ccedu_break","ccedu_registry_name","ccedu_select","ccedu_transfer","ccedu_redstone_set","ccedu_say","ccedu_sleep","ccedu_assert","ccedu_call","ccedu_comment","ccedu_snapshot","ccedu_random_boolean","variables_set","math_change","controls_if","controls_repeat_ext","controls_whileUntil","controls_for","controls_flow_statements","math_number","text","logic_boolean","logic_null","variables_get","logic_negate","math_arithmetic","logic_compare","logic_operation","math_modulo","math_random_int","math_random_float","text_length","text_join","ccedu_turtle_query","ccedu_inspect","ccedu_detect","ccedu_compare","ccedu_item_count","ccedu_item_space","ccedu_selected_slot","ccedu_redstone_query","ccedu_item_name",
        "procedures_defnoreturn","procedures_defreturn","procedures_callnoreturn","procedures_callreturn","procedures_ifreturn");
    private static final Set<String> DEFINITIONS=Set.of("procedures_defnoreturn","procedures_defreturn");
    private static Set<String> withActionBlocks(String... types){var all=new HashSet<>(List.of(types));all.addAll(ACTION_BLOCKS.keySet());return Set.copyOf(all);}
    private final Set<String> ids=new LinkedHashSet<>();
    private final Map<String,String> variables=new LinkedHashMap<>(),names=new LinkedHashMap<>();
    private final Map<Integer,String> lines=new LinkedHashMap<>();
    /** Student-made functions by Blockly name: Lua name and parameter variable IDs in order. */
    private record Function(String lua,List<String> params,boolean returns) {}
    private final Map<String,Function> functions=new LinkedHashMap<>();
    private Function inFunction;
    private final StringBuilder code=new StringBuilder();
    private int line=1,serial,loopDepth,codeBytes;
    private String current="";
    private final boolean annotated;
    private WorkspaceCompiler(boolean annotated){this.annotated=annotated;}
    public static Compiled compile(JsonObject workspace){return compile(workspace,true);}
    public static Compiled compile(JsonObject workspace,boolean annotated){var compiler=new WorkspaceCompiler(annotated);try{return compiler.generate(workspace);}catch(Invalid e){throw e;}catch(RuntimeException e){throw compiler.invalid("Malformed block data: "+Objects.toString(e.getMessage(),"invalid value"));}}
    public static void validateStructure(JsonObject workspace){var compiler=new WorkspaceCompiler(false);try{compiler.validate(workspace,0,false);}catch(Invalid e){throw e;}catch(RuntimeException e){throw compiler.invalid("Malformed workspace: "+Objects.toString(e.getMessage(),"invalid value"));}}
    private Compiled generate(JsonObject ws){
        validate(ws,0,false);
        var vars=ws.has("variables")?ws.getAsJsonArray("variables"):new JsonArray();
        if(vars.size()>128)throw invalid("Too many variables");
        for(var v:vars){var j=v.getAsJsonObject();String id=str(j,"id"),name=str(j,"name"); if(id.isBlank()||name.isBlank()||id.length()>128||name.length()>128||variables.containsKey(id)||names.containsValue(name))throw invalid("Duplicate or invalid variable");variables.put(id,"v"+variables.size());names.put(id,name);}
        var top=object(ws,"blocks");var blocks=top!=null&&top.get("blocks") instanceof JsonArray a?a:new JsonArray();
        if(!variables.isEmpty())emit("local "+String.join(",",variables.values()));
        functions(blocks);
        // Only the stack under "when program starts" runs; loose stacks are drafts. Programs saved before the
        // start block existed (no start block at all) keep running every top-level stack as they always did.
        JsonObject start=null;
        for(var b:blocks)if(type(b.getAsJsonObject()).equals("ccedu_start")){if(start!=null){current=str(b.getAsJsonObject(),"id");throw invalid("Only one start block is allowed");}start=b.getAsJsonObject();}
        if(start!=null)sequence(start,0);else for(var b:blocks)sequence(b.getAsJsonObject(),0);
        return new Compiled(code.toString(),Map.copyOf(lines),Set.copyOf(ids));
    }
    /**
     * Function definitions are top-level blocks that run only when called, so they are not loose stacks. Every name is
     * declared before any body so functions can call each other (and themselves) in any order.
     */
    private void functions(JsonArray blocks){
        var definitions=new ArrayList<JsonObject>();
        for(var e:blocks){var b=e.getAsJsonObject();if(!DEFINITIONS.contains(type(b))||!enabled(b))continue;current=str(b,"id");
            String name=field(b,"NAME").trim();if(name.isEmpty()||name.length()>128||functions.containsKey(name))throw invalid("Duplicate or invalid function name");
            var params=new ArrayList<String>();
            var state=object(b,"extraState");if(state!=null&&state.get("params") instanceof JsonArray saved)for(var p:saved){
                String id=str(p.getAsJsonObject(),"id");if(!variables.containsKey(id)||params.contains(id))throw invalid("Unknown or repeated function input");params.add(id);}
            if(functions.size()>=256)throw invalid("Too many functions");
            functions.put(name,new Function("f"+functions.size(),List.copyOf(params),type(b).equals("procedures_defreturn")));definitions.add(b);}
        if(functions.isEmpty())return;
        emit("local "+String.join(",",functions.values().stream().map(Function::lua).toList()));
        for(var b:definitions){
            current=str(b,"id");var f=functions.get(field(b,"NAME").trim());
            // Inputs shadow the program's variables of the same ID inside the body, like Blockly's own generators.
            emit(f.lua()+" = function("+String.join(", ",f.params().stream().map(variables::get).toList())+")");
            int loops=loopDepth;loopDepth=0;inFunction=f;
            body(b,"STACK",0);
            if(f.returns()){checkpoint(b);emit("return "+input(b,"RETURN","nil"));}
            inFunction=null;loopDepth=loops;current=str(b,"id");emit("end");
        }
    }
    private String call(JsonObject b,int depth){
        var state=object(b,"extraState");String name=state!=null&&state.has("name")?str(state,"name"):"";
        var f=functions.get(name.trim());if(f==null)throw invalid("Unknown function: "+name);
        var args=new ArrayList<String>();for(int i=0;i<f.params().size();i++)args.add(inputExpr(b,"ARG"+i,"nil",depth));
        return f.lua()+"("+String.join(", ",args)+")";
    }
    private void validate(JsonElement value,int depth,boolean block){
        if(depth>128)throw invalid("Workspace nesting exceeds 128");
        if(value.isJsonObject()){
            var j=value.getAsJsonObject();
            if(block){
                current=str(j,"id");if(current.isBlank()||current.length()>128||!ids.add(current))throw invalid("Missing or duplicate stable block ID");
                if(!TYPES.contains(str(j,"type")))throw invalid("Unsupported block: "+str(j,"type"));
                if(ids.size()>4096)throw invalid("Workspace exceeds 4096 blocks");
            }
            for(var entry:j.entrySet())validate(entry.getValue(),depth+1,entry.getKey().equals("block")||entry.getKey().equals("shadow")||entry.getKey().equals("blocks")&&entry.getValue().isJsonArray());
        }else if(value.isJsonArray())for(var e:value.getAsJsonArray())validate(e,depth+1,block);
        else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().length()>16384)throw invalid("Field too large");
    }
    private void sequence(JsonObject b,int depth){
        if(depth>128)throw invalid("Too many nested statements");
        while(b!=null){
            current=str(b,"id");
            if(enabled(b))statement(b,depth);
            var next=object(b,"next");b=next==null?null:object(next,"block");
        }
    }
    /** Blockly 11 saves disabled blocks with disabledReasons; older saves used disabled. */
    private static boolean enabled(JsonObject b){
        if(b.has("disabled")&&b.get("disabled").getAsBoolean())return false;
        return !b.has("disabledReasons")||!b.get("disabledReasons").isJsonArray()||b.getAsJsonArray("disabledReasons").isEmpty();
    }
    private void statement(JsonObject b,int depth){
        String t=type(b);if(t.equals("ccedu_start")||DEFINITIONS.contains(t))return;checkpoint(b);
        switch(t){
            case "ccedu_turtle_command","ccedu_turtle_action" -> {
                String command=field(b,"COMMAND");if(!COMMANDS.contains(command)&&!QUERIES.contains(command))throw invalid("Unknown turtle command");
                String arg=t.equals("ccedu_turtle_action")?input(b,"ARG",null):null;
                if(arg!=null&&!Set.of("drop","dropUp","dropDown","suck","suckUp","suckDown","place","placeUp","placeDown").contains(command))throw invalid("Command does not accept an argument");
                turtleCall(command,arg);
            }
            case "ccedu_move","ccedu_dig","ccedu_place","ccedu_attack","ccedu_drop","ccedu_suck","ccedu_equip" -> {
                String command=field(b,"COMMAND");if(!ACTION_BLOCKS.get(t).contains(command))throw invalid("Unknown turtle command");
                turtleCall(command,null);
            }
            case "ccedu_select" -> emit("assert(turtle.select("+input(b,"SLOT","1")+"))");
            case "ccedu_transfer" -> emit("assert(turtle.transferTo("+input(b,"SLOT","1")+", "+input(b,"COUNT","64")+"))");
            case "ccedu_redstone_set" -> emit("turtleedu."+sideMethod(b,"setRedstone")+"("+input(b,"VALUE","false")+")");
            case "ccedu_say" -> emit("turtleedu.say("+input(b,"TEXT",quote(""))+")");
            case "ccedu_sleep" -> emit("sleep("+input(b,"SECONDS","1")+")");
            case "ccedu_assert" -> emit("assert("+input(b,"TEST","false")+", "+input(b,"MESSAGE",quote("Assertion failed"))+")");
            case "ccedu_call" -> {String name=field(b,"NAME");if(name.isBlank())throw invalid("Choose a program to call");emit("edu.callProgram("+quote(name)+")");}
            case "ccedu_comment" -> emit("-- "+field(b,"TEXT").replace('\n',' ').replace('\r',' '));
            case "ccedu_snapshot" -> emit("assert(edu."+choice(b,"ACTION",Map.of("save","saveTurtleState","restore","restoreTurtleState"))+"())");
            case "variables_set" -> emit(variable(b)+" = "+input(b,"VALUE","0"));
            case "math_change" -> emit(variable(b)+" = ("+variable(b)+" or 0) + "+input(b,"DELTA","1"));
            case "controls_if" -> {
                int n=0;do{
                    emit((n==0?"if ":"elseif ")+input(b,"IF"+n,"false")+" then");body(b,"DO"+n,depth);n++;
                }while(hasInput(b,"IF"+n));
                if(hasInput(b,"ELSE")){emit("else");body(b,"ELSE",depth);}emit("end");
            }
            case "controls_repeat_ext" -> {
                String bound="limit"+(serial++);emit("local "+bound+" = math.max(0, math.floor("+input(b,"TIMES","0")+"))");
                emit("for i"+serial+" = 1, "+bound+" do");checkpoint(b);loopBody(b,"DO",depth);emit("end");
            }
            case "controls_whileUntil" -> {
                String mode=choice(b,"MODE",Map.of("WHILE","","UNTIL","not "));emit("while true do");checkpoint(b);
                emit("if not ("+mode+"("+input(b,"BOOL","false")+")) then break end");loopBody(b,"DO",depth);emit("end");
            }
            case "controls_for" -> {
                String a=input(b,"FROM","1"),z=input(b,"TO","10"),by=input(b,"BY","1"),suffix="r"+(serial++);
                emit("local start"+suffix+", end"+suffix+", step"+suffix+" = "+a+", "+z+", math.abs("+by+")");
                emit("assert(step"+suffix+" > 0, 'Loop step must be nonzero')");
                emit("if start"+suffix+" > end"+suffix+" then step"+suffix+" = -step"+suffix+" end");
                emit("for "+variable(b)+" = start"+suffix+", end"+suffix+", step"+suffix+" do");checkpoint(b);loopBody(b,"DO",depth);emit("end");
            }
            case "procedures_callnoreturn" -> emit(call(b,0));
            case "procedures_ifreturn" -> {
                if(inFunction==null)throw invalid("Return must be inside a function");
                emit("if "+input(b,"CONDITION","false")+" then return"+(inFunction.returns()?" "+input(b,"VALUE","nil"):"")+" end");
            }
            case "controls_flow_statements","ccedu_break" -> {if(loopDepth==0)throw invalid("Break must be inside a loop");if(t.equals("controls_flow_statements")&&!field(b,"FLOW").equals("BREAK"))throw invalid("Only break is supported");emit("do break end");}
            default -> emit("do local result = "+expression(b,0)+" end");
        }
    }
    private void turtleCall(String command,String arg){
        String call="turtle."+command+"("+(arg==null?"":arg)+")";
        if(QUERIES.contains(command))emit("do local result = "+call+" end");else if(annotated&&ASSERTED_COMMANDS.contains(command)){emit("local ok, reason = "+call);emit("if not ok then error(reason or 'Turtle command failed', 0) end");}else emit(call);
    }
    private void loopBody(JsonObject b,String name,int depth){loopDepth++;body(b,name,depth);loopDepth--;}
    private void body(JsonObject b,String name,int depth){var child=child(b,name);if(child!=null)sequence(child,depth+1);}
    private String expression(JsonObject b,int depth){
        if(depth>64)throw invalid("Expression nesting exceeds 64");
        String old=current;current=str(b,"id");String result;
        switch(type(b)){
            case "math_number" -> {double n=Double.parseDouble(field(b,"NUM"));if(!Double.isFinite(n))throw invalid("Number is not finite");result=Double.toString(n);}
            case "text" -> result=quote(field(b,"TEXT"));
            case "ccedu_registry_name" -> {String name=field(b,"NAME");if(!name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw invalid("Invalid item or block ID");result=quote(name);}
            case "logic_boolean" -> result=choice(b,"BOOL",Map.of("TRUE","true","FALSE","false"));
            case "logic_null" -> result="nil";
            case "variables_get" -> result=variable(b);
            case "logic_negate" -> result="(not "+inputExpr(b,"BOOL","false",depth)+")";
            case "math_arithmetic","logic_compare","logic_operation" -> {
                var ops=switch(type(b)){
                    case "math_arithmetic"->Map.of("ADD","+","MINUS","-","MULTIPLY","*","DIVIDE","/","POWER","^");
                    case "logic_compare"->Map.of("EQ","==","NEQ","~=","LT","<","LTE","<=","GT",">","GTE",">=");
                    default->Map.of("AND","and","OR","or");};
                String fallback=type(b).equals("logic_operation")?"false":"0";
                result="("+inputExpr(b,"A",fallback,depth)+" "+choice(b,"OP",ops)+" "+inputExpr(b,"B",fallback,depth)+")";
            }
            case "math_modulo" -> result="("+inputExpr(b,"DIVIDEND","0",depth)+" % "+inputExpr(b,"DIVISOR","1",depth)+")";
            case "math_random_int" -> result="math.random("+inputExpr(b,"FROM","1",depth)+", "+inputExpr(b,"TO","100",depth)+")";
            case "math_random_float" -> result="math.random()";
            case "ccedu_random_boolean" -> result="(math.random(1,2) == 1)";
            case "text_length" -> result="string.len(tostring("+inputExpr(b,"VALUE",quote(""),depth)+"))";
            case "text_join" -> {
                var pieces=new ArrayList<String>();var state=object(b,"extraState");int count=state!=null&&state.has("itemCount")?state.get("itemCount").getAsInt():2;
                if(count<0||count>128)throw invalid("Invalid text join size");for(int i=0;i<count;i++)pieces.add("tostring("+inputExpr(b,"ADD"+i,quote(""),depth)+")");
                result=pieces.isEmpty()?quote(""):"("+String.join(" .. ",pieces)+")";
            }
            case "ccedu_turtle_query" -> {String method=field(b,"COMMAND");if(!QUERIES.contains(method))throw invalid("Invalid turtle query");result="turtle."+method+"("+inputExpr(b,"SLOT","",depth)+")";}
            case "ccedu_turtle_result" -> {String method=field(b,"COMMAND");if(!COMMANDS.contains(method)||Set.of("turnLeft","turnRight").contains(method))throw invalid("Invalid boolean turtle action");result="turtle."+method+"()";}
            case "ccedu_inspect" -> result="turtleedu."+sideMethod(b,"inspect")+"()";
            case "ccedu_detect" -> result="turtle."+sideMethod(b,"detect")+"()";
            case "ccedu_compare" -> result="turtle."+sideMethod(b,"compare")+"()";
            case "ccedu_item_count" -> result="turtle.getItemCount("+inputExpr(b,"SLOT","",depth)+")";
            case "ccedu_item_space" -> result="turtle.getItemSpace("+inputExpr(b,"SLOT","",depth)+")";
            case "ccedu_selected_slot" -> result="turtle.getSelectedSlot()";
            case "ccedu_redstone_query" -> result="turtleedu."+sideMethod(b,"checkRedstone")+"()";
            case "ccedu_item_name" -> result="turtleedu.getItemName("+inputExpr(b,"SLOT","",depth)+")";
            case "procedures_callreturn" -> result=call(b,depth);
            default -> throw invalid("Unsupported block: "+type(b));
        }
        if(annotated&&!Set.of("math_number","text","logic_boolean","logic_null","variables_get","ccedu_registry_name").contains(type(b)))result="edu.expression("+quote(str(b,"id"))+", "+variableTable()+", function() return "+result+" end)";
        current=old;return result;
    }
    private void checkpoint(JsonObject b){
        current=str(b,"id");if(annotated)emit("edu.checkpoint("+quote(current)+", "+variableTable()+")");
    }
    private String variableTable(){var entries=new ArrayList<String>();for(var e:variables.entrySet())entries.add("["+quote(names.get(e.getKey()))+"]="+e.getValue());return "{"+String.join(",",entries)+"}";}
    private void emit(String text){lines.put(line++,current);code.append(text).append('\n');codeBytes+=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1;if(codeBytes>ProgramStore.MAX_BYTES)throw invalid("Compiled program exceeds 1 MiB");}
    private String input(JsonObject b,String name,String fallback){return inputExpr(b,name,fallback,0);}
    private String inputExpr(JsonObject b,String name,String fallback,int depth){var j=child(b,name);return j==null?fallback:expression(j,depth+1);}
    private boolean hasInput(JsonObject b,String name){return child(b,name)!=null;}
    private JsonObject child(JsonObject b,String name){var inputs=object(b,"inputs");var i=inputs==null?null:object(inputs,name);if(i==null)return null;var block=object(i,"block");return block!=null?block:object(i,"shadow");}
    /**
     * A member that is absent or JSON null reads as absent. Blockly saves {"block":null} for an insertion marker, the
     * preview shown while a block is dragged near a connection, so a save made mid-drag carries those nulls.
     */
    private static JsonObject object(JsonObject j,String name){var v=j.get(name);return v!=null&&v.isJsonObject()?v.getAsJsonObject():null;}
    private String variable(JsonObject b){var f=b.getAsJsonObject("fields").get("VAR");String id=f.isJsonObject()?str(f.getAsJsonObject(),"id"):f.getAsString();var v=variables.get(id);if(v==null)throw invalid("Unknown variable ID");return v;}
    private String sideMethod(JsonObject b,String base){return base+choice(b,"SIDE",Map.of("forward","","up","Up","down","Down"));}
    private String choice(JsonObject b,String key,Map<String,String> options){var v=options.get(field(b,key));if(v==null)throw invalid("Invalid "+key);return v;}
    private String field(JsonObject b,String key){if(!b.has("fields")||!b.getAsJsonObject("fields").has(key))throw invalid("Missing field "+key);return b.getAsJsonObject("fields").get(key).getAsString();}
    private static String type(JsonObject b){return str(b,"type");}
    private static String str(JsonObject j,String name){if(!j.has(name)||!j.get(name).isJsonPrimitive())throw new IllegalArgumentException("Missing "+name);return j.get(name).getAsString();}
    private Invalid invalid(String message){return new Invalid(current,message);}
    /** Lua string escaping uses decimal escapes for control characters, independent of locale. */
    public static String quote(String value){var s=new StringBuilder("\"");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='"'||c=='\\')s.append('\\').append(c);else if(c<32||c==127)s.append(String.format(Locale.ROOT,"\\%03d",(int)c));else s.append(c);}return s.append('"').toString();}
}
