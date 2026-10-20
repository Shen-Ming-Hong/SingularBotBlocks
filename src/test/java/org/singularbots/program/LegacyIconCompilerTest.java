package org.singularbots.program;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Every teaching icon the original classroom mod registered
 * compiled through the server compiler, plus the Lua helpers the compiled code relies on.
 */
class LegacyIconCompilerTest {
    /** A Blockly block as the editor saves it. */
    private static final class B {
        final JsonObject json=new JsonObject();
        B(String type,String id){json.addProperty("type",type);json.addProperty("id",id);}
        B field(String name,Object value){
            if(!json.has("fields"))json.add("fields",new JsonObject());
            var fields=json.getAsJsonObject("fields");
            if(value instanceof Number n)fields.addProperty(name,n);else fields.addProperty(name,value.toString());
            return this;
        }
        B variable(){if(!json.has("fields"))json.add("fields",new JsonObject());var v=new JsonObject();v.addProperty("id","var-x");json.getAsJsonObject("fields").add("VAR",v);return this;}
        B input(String name,B child){return slot(name,"block",child);}
        B shadow(String name,B child){return slot(name,"shadow",child);}
        private B slot(String name,String kind,B child){
            if(!json.has("inputs"))json.add("inputs",new JsonObject());
            var i=new JsonObject();i.add(kind,child.json);json.getAsJsonObject("inputs").add(name,i);return this;
        }
        B next(B child){var n=new JsonObject();n.add("block",child.json);json.add("next",n);return this;}
    }
    private static int serial;
    private static B b(String type){return new B(type,type+"-"+(serial++));}
    private static B num(double n){return b("math_number").field("NUM",n);}
    private static B text(String t){return b("text").field("TEXT",t);}
    private static B bool(boolean v){return b("logic_boolean").field("BOOL",v?"TRUE":"FALSE");}
    private static B cmd(String command){return b("ccedu_turtle_command").field("COMMAND",command);}
    private static B query(String command){return b("ccedu_turtle_query").field("COMMAND",command);}
    private static B side(String type,String side){return b(type).field("SIDE",side);}
    private static B op(String type,String op,B a,B c){return b(type).field("OP",op).input("A",a).input("B",c);}
    private static B loop(B body){return b("controls_repeat_ext").shadow("TIMES",num(2)).input("DO",body);}
    /** One student variable named x is always declared, as the editor saves every workspace with its variable list. */
    private static JsonObject ws(B... stacks){
        var root=new JsonObject();var vars=new JsonArray();var x=new JsonObject();x.addProperty("id","var-x");x.addProperty("name","x");vars.add(x);root.add("variables",vars);
        var blocks=new JsonArray();for(var s:stacks)blocks.add(s.json);var inner=new JsonObject();inner.add("blocks",blocks);root.add("blocks",inner);return root;
    }
    private static String lua(JsonObject ws){return WorkspaceCompiler.compile(ws,false).lua();}
    private static WorkspaceCompiler.Invalid rejected(JsonObject ws){return assertThrows(WorkspaceCompiler.Invalid.class,()->WorkspaceCompiler.compile(ws));}

    private record Icon(String modernType,Supplier<B> block,String expected) {}
    /** Old field name to the block a student uses for it now, and the Lua it must produce. */
    private static final Map<String,Icon> ICONS=new LinkedHashMap<>();
    private static void icon(String legacy,String type,Supplier<B> block,String expected){ICONS.put(legacy,new Icon(type,block,expected));}
    static {
        for(var e:new String[][]{{"moveForward","forward"},{"moveBack","back"},{"turnLeft","turnLeft"},{"turnRight","turnRight"},{"moveUp","up"},{"moveDown","down"},
                {"dig","dig"},{"digUp","digUp"},{"digDown","digDown"},{"place","place"},{"placeUp","placeUp"},{"placeDown","placeDown"},
                {"drop","drop"},{"dropUp","dropUp"},{"dropDown","dropDown"},{"suck","suck"},{"suckUp","suckUp"},{"suckDown","suckDown"},
                {"attack","attack"},{"attackUp","attackUp"},{"attackDown","attackDown"},{"equipLeft","equipLeft"},{"equipRight","equipRight"}})
            icon(e[0],"ccedu_turtle_command",()->cmd(e[1]),"turtle."+e[1]+"()");
        icon("ifItem","controls_if",()->b("controls_if").input("IF0",bool(true)).input("DO0",cmd("forward")),"if true then\nturtle.forward()\nend");
        icon("then","controls_if",()->b("controls_if").input("IF0",bool(true)),"if true then\n");
        icon("end","controls_if",()->b("controls_if").input("IF0",bool(true)),"\nend\n");
        icon("elseIf","controls_if",()->b("controls_if").input("IF0",bool(true)).input("IF1",bool(false)).input("DO1",cmd("back")),"elseif false then\nturtle.back()\n");
        icon("elseItem","controls_if",()->b("controls_if").input("IF0",bool(true)).input("ELSE",cmd("up")),"else\nturtle.up()\nend");
        icon("whileItem","controls_whileUntil",()->b("controls_whileUntil").field("MODE","WHILE").input("BOOL",query("detect")).input("DO",cmd("dig")),"while true do\nif not ((turtle.detect())) then break end\nturtle.dig()\nend");
        icon("doItem","controls_whileUntil",()->b("controls_whileUntil").field("MODE","WHILE").input("BOOL",bool(true)),"while true do\n");
        icon("and","logic_operation",()->op("logic_operation","AND",bool(true),bool(false)),"(true and false)");
        icon("or","logic_operation",()->op("logic_operation","OR",bool(true),bool(false)),"(true or false)");
        icon("not","logic_negate",()->b("logic_negate").input("BOOL",query("detectUp")),"(not turtle.detectUp())");
        for(var q:List.of("detect","detectUp","detectDown","compare","compareUp","compareDown"))icon(q,"ccedu_turtle_query",()->query(q),"turtle."+q+"()");
        icon("trueItem","logic_boolean",()->bool(true),"do local result = true end");
        icon("falseItem","logic_boolean",()->bool(false),"do local result = false end");
        icon("random","ccedu_random_boolean",()->b("ccedu_random_boolean"),"(math.random(1,2) == 1)");
        icon("randomNumber","math_random_int",()->b("math_random_int").input("FROM",num(1)).input("TO",num(6)),"math.random(1.0, 6.0)");
        icon("select","ccedu_select",()->b("ccedu_select").shadow("SLOT",num(3)),"assert(turtle.select(3.0))");
        for(var s:new String[][]{{"","forward"},{"Up","up"},{"Down","down"}}){
            icon("queryRedstone"+s[0],"ccedu_redstone_query",()->side("ccedu_redstone_query",s[1]),"turtleedu.checkRedstone"+s[0]+"()");
            icon("setRedstone"+s[0],"ccedu_redstone_set",()->side("ccedu_redstone_set",s[1]).input("VALUE",bool(false)),"turtleedu.setRedstone"+s[0]+"(false)");
            icon("inspect"+s[0],"ccedu_inspect",()->side("ccedu_inspect",s[1]),"turtleedu.inspect"+s[0]+"()");
        }
        icon("comment","ccedu_comment",()->b("ccedu_comment").field("TEXT","去挖礦"),"-- 去挖礦\n");
        for(var c:new String[][]{{"equalTo","EQ","=="},{"notEqualTo","NEQ","~="},{"greaterThan","GT",">"},{"lessThan","LT","<"},{"greaterThanOrEqualTo","GTE",">="},{"lessThanOrEqualTo","LTE","<="}})
            icon(c[0],"logic_compare",()->op("logic_compare",c[1],num(1),num(2)),"(1.0 "+c[2]+" 2.0)");
        icon("variable","variables_get",()->b("variables_get").variable(),"do local result = v0 end");
        icon("number","math_number",()->num(7),"do local result = 7.0 end");
        for(var a:new String[][]{{"plus","ADD","+"},{"minus","MINUS","-"},{"times","MULTIPLY","*"},{"dividedBy","DIVIDE","/"}})
            icon(a[0],"math_arithmetic",()->op("math_arithmetic",a[1],num(6),num(3)),"(6.0 "+a[2]+" 3.0)");
        icon("getItemCount","ccedu_turtle_query",()->query("getItemCount").shadow("SLOT",num(2)),"turtle.getItemCount(2.0)");
        icon("forItem","controls_for",()->b("controls_for").variable().input("FROM",num(1)).input("TO",num(3)).input("BY",num(1)).input("DO",cmd("forward")),"for v0 = start");
        icon("to","controls_for",()->b("controls_for").variable().input("FROM",num(5)).input("TO",num(1)).input("BY",num(1)),"= 5.0, 1.0, math.abs(1.0)");
        icon("repeat","controls_repeat_ext",()->b("controls_repeat_ext").shadow("TIMES",num(3)).input("DO",cmd("forward")),"math.max(0, math.floor(3.0))");
        icon("breakItem","ccedu_break",()->loop(b("ccedu_break")),"do break end");
        icon("inspectSlot","ccedu_item_name",()->b("ccedu_item_name").shadow("SLOT",num(4)),"turtleedu.getItemName(4.0)");
        icon("block","ccedu_registry_name",()->b("ccedu_registry_name").field("NAME","minecraft:stone"),"\"minecraft:stone\"");
        icon("item","ccedu_registry_name",()->b("ccedu_registry_name").field("NAME","minecraft:diamond_pickaxe"),"\"minecraft:diamond_pickaxe\"");
        icon("programDisk","ccedu_call",()->b("ccedu_call").field("NAME","挖礦"),"edu.callProgram(\"挖礦\")");
        icon("say","ccedu_say",()->b("ccedu_say").shadow("TEXT",text("你好")),"turtleedu.say(\"你好\")");
        icon("string","text",()->text("hi"),"do local result = \"hi\" end");
    }

    @TestFactory List<DynamicTest> everyLegacyIconCompilesToItsLua(){
        var tests=new ArrayList<DynamicTest>();
        for(var e:ICONS.entrySet())tests.add(DynamicTest.dynamicTest(e.getKey(),()->{
            var block=e.getValue().block().get();assertTrue(block.json.toString().contains("\"type\":\""+e.getValue().modernType()+"\""),e.getValue().modernType()+" in "+block.json);
            var lua=lua(ws(block));assertTrue(lua.contains(e.getValue().expected()),e.getValue().expected()+" in\n"+lua);
            WorkspaceCompiler.compile(ws(e.getValue().block().get()));
        }));
        return tests;
    }
    @Test void everyIconInTheParityTableHasACaseWithTheBlockTheTableNames()throws Exception{
        // The parity table is a maintainer document; the public source export leaves it out.
        var table=Path.of("docs/legacy-parity.md");Assumptions.assumeTrue(Files.exists(table),"docs/legacy-parity.md is not in this checkout");
        var rows=Pattern.compile("^\\| `(\\w+)` \\| `\\w+` \\| .*? \\| `([^`]+)` \\|",Pattern.MULTILINE).matcher(Files.readString(table));
        var listed=new LinkedHashSet<String>();
        while(rows.find()){
            listed.add(rows.group(1));var icon=ICONS.get(rows.group(1));assertNotNull(icon,"No compiler case for legacy icon "+rows.group(1));
            // then/do/end/to were separate syntax tokens; the table names the Blockly shape that now holds them.
            if(!rows.group(2).equals("Blockly statement structure"))assertTrue(rows.group(2).contains(icon.modernType()),rows.group(1)+" is tested with "+icon.modernType()+" but the table lists "+rows.group(2));
        }
        assertEquals(77,listed.size());assertEquals(listed,ICONS.keySet());
    }

    @Test void ifChainsKeepBranchOrderAndOnlyOneElse(){
        var lua=lua(ws(b("controls_if").input("IF0",query("detect")).input("DO0",cmd("dig")).input("IF1",query("detectUp")).input("DO1",cmd("digUp")).input("IF2",query("detectDown")).input("DO2",cmd("digDown")).input("ELSE",cmd("forward"))));
        assertEquals("local v0\nif turtle.detect() then\nturtle.dig()\nelseif turtle.detectUp() then\nturtle.digUp()\nelseif turtle.detectDown() then\nturtle.digDown()\nelse\nturtle.forward()\nend\n",lua);
        assertTrue(lua(ws(b("controls_if"))).contains("if false then\nend"),"an empty condition never runs its branch");
    }
    @Test void untilStopsWhenTheConditionBecomesTrueAndEmptyConditionsNeverLoop(){
        assertTrue(lua(ws(b("controls_whileUntil").field("MODE","UNTIL").input("BOOL",query("detect")).input("DO",cmd("forward")))).contains("while true do\nif not (not (turtle.detect())) then break end\nturtle.forward()\nend"));
        assertTrue(lua(ws(b("controls_whileUntil").field("MODE","WHILE"))).contains("if not ((false)) then break end"));
        assertEquals("Invalid MODE",rejected(ws(b("controls_whileUntil").field("MODE","FOREVER"))).getMessage());
    }
    @Test void countingLoopsCountDownAndRejectAZeroStep(){
        var lua=lua(ws(b("controls_for").variable().input("FROM",num(5)).input("TO",num(1)).input("BY",num(-2)).input("DO",cmd("forward"))));
        assertTrue(lua.contains("local startr0, endr0, stepr0 = 5.0, 1.0, math.abs(-2.0)\nassert(stepr0 > 0, 'Loop step must be nonzero')\nif startr0 > endr0 then stepr0 = -stepr0 end\nfor v0 = startr0, endr0, stepr0 do\nturtle.forward()\nend"),lua);
        assertTrue(lua(ws(b("controls_for").variable())).contains("= 1, 10, math.abs(1)"),"missing FROM/TO/BY use 1 to 10 by 1");
        assertTrue(lua(ws(b("controls_repeat_ext"))).contains("math.max(0, math.floor(0))"),"a missing count runs zero times");
    }
    @Test void nestedLoopsGetSeparateCountersAndBreakLeavesOnlyTheInnerLoop(){
        var lua=lua(ws(loop(loop(b("ccedu_break")).next(cmd("forward")))));
        assertTrue(lua.contains("for i1 = 1, limit0 do\nlocal limit1")&&lua.contains("for i2 = 1, limit1 do\ndo break end\nend\nturtle.forward()\nend"),lua);
        assertTrue(lua(ws(b("controls_whileUntil").field("MODE","WHILE").input("BOOL",bool(true)).input("DO",b("controls_flow_statements").field("FLOW","BREAK")))).contains("do break end"));
        assertEquals("Only break is supported",rejected(ws(loop(b("controls_flow_statements").field("FLOW","CONTINUE")))).getMessage());
        var outside=b("ccedu_break");assertEquals(outside.json.get("id").getAsString(),rejected(ws(b("controls_if").input("IF0",bool(true)).input("DO0",outside))).blockId);
    }
    @Test void amountsAreOnlyAcceptedByDropSuckAndPlace(){
        for(var c:List.of("drop","dropUp","dropDown","suck","suckUp","suckDown","place","placeUp","placeDown"))
            assertTrue(lua(ws(b("ccedu_turtle_action").field("COMMAND",c).shadow("ARG",num(8)))).contains("turtle."+c+"(8.0)"),c);
        assertTrue(lua(ws(b("ccedu_turtle_action").field("COMMAND","place").input("ARG",text("告示牌")))).contains("turtle.place(\"告示牌\")"),"place accepts sign text");
        assertTrue(lua(ws(b("ccedu_turtle_action").field("COMMAND","drop"))).contains("turtle.drop()"),"an empty amount drops the whole stack");
        assertEquals("Command does not accept an argument",rejected(ws(b("ccedu_turtle_action").field("COMMAND","forward").shadow("ARG",num(3)))).getMessage());
        assertEquals("Unknown turtle command",rejected(ws(b("ccedu_turtle_action").field("COMMAND","refuel").shadow("ARG",num(3)))).getMessage());
    }
    @Test void movingAndDiggingStopTheProgramOnFailureButTurningAttackingAndChecksDoNot(){
        var asserted=Set.of("forward","back","up","down","dig","digUp","digDown","place","placeUp","placeDown","drop","dropUp","dropDown","suck","suckUp","suckDown","equipLeft","equipRight");
        for(var c:WorkspaceCompiler.COMMANDS){
            var lua=WorkspaceCompiler.compile(ws(cmd(c))).lua();
            assertEquals(asserted.contains(c),lua.contains("local ok, reason = turtle."+c+"()\nif not ok then error(reason or 'Turtle command failed', 0) end"),c);
        }
        for(var q:WorkspaceCompiler.QUERIES)assertTrue(lua(ws(cmd(q))).contains("do local result = turtle."+q+"() end"),q+" used as a statement keeps running");
    }
    @Test void everyActionCanBeTriedAsAConditionExceptTurning(){
        for(var c:WorkspaceCompiler.COMMANDS){
            var block=b("ccedu_turtle_result").field("COMMAND",c);
            if(c.startsWith("turn"))assertEquals("Invalid boolean turtle action",rejected(ws(block)).getMessage());
            else assertTrue(lua(ws(b("controls_if").input("IF0",block))).contains("if turtle."+c+"() then"),c);
        }
        assertEquals("Invalid boolean turtle action",rejected(ws(b("ccedu_turtle_result").field("COMMAND","detect"))).getMessage());
    }
    @Test void inventoryQueriesTakeAnOptionalSlot(){
        for(var q:WorkspaceCompiler.QUERIES)assertTrue(lua(ws(query(q))).contains("turtle."+q+"()"),q);
        assertTrue(lua(ws(query("getItemSpace").shadow("SLOT",num(16)))).contains("turtle.getItemSpace(16.0)"));
        assertTrue(lua(ws(b("ccedu_item_name"))).contains("turtleedu.getItemName()"),"no slot means the selected slot");
        assertTrue(lua(ws(b("ccedu_transfer").shadow("SLOT",num(5)).shadow("COUNT",num(10)))).contains("assert(turtle.transferTo(5.0, 10.0))"));
        assertEquals("Invalid turtle query",rejected(ws(query("forward"))).getMessage());
    }
    @Test void lookAroundAndSlotBlocksCompileToTurtleCalls(){
        for(var s:new String[][]{{"","forward"},{"Up","up"},{"Down","down"}}){
            assertTrue(lua(ws(side("ccedu_detect",s[1]))).contains("turtle.detect"+s[0]+"()"),s[1]);
            assertTrue(lua(ws(side("ccedu_compare",s[1]))).contains("turtle.compare"+s[0]+"()"),s[1]);
        }
        assertTrue(lua(ws(b("controls_if").input("IF0",side("ccedu_detect","down")).input("DO0",cmd("dig")))).contains("if turtle.detectDown() then"));
        assertTrue(lua(ws(b("ccedu_item_count").shadow("SLOT",num(3)))).contains("turtle.getItemCount(3.0)"));
        assertTrue(lua(ws(b("ccedu_item_space").shadow("SLOT",num(16)))).contains("turtle.getItemSpace(16.0)"));
        assertTrue(lua(ws(b("ccedu_selected_slot"))).contains("turtle.getSelectedSlot()"));
    }
    @Test void sidesAreLimitedToFrontUpAndDown(){
        for(var type:List.of("ccedu_inspect","ccedu_detect","ccedu_compare","ccedu_redstone_query","ccedu_redstone_set"))assertEquals("Invalid SIDE",rejected(ws(side(type,"left"))).getMessage(),type);
        assertTrue(lua(ws(side("ccedu_redstone_set","down"))).contains("turtleedu.setRedstoneDown(false)"),"an empty value turns the signal off");
    }
    @Test void registryIdsMustBeNamespacedLowercaseIds(){
        for(var bad:List.of("stone","Minecraft:Stone","minecraft:stone\")os.shutdown(","minecraft:","minecraft stone"))
            assertEquals("Invalid item or block ID",rejected(ws(b("ccedu_registry_name").field("NAME",bad))).getMessage(),bad);
        assertTrue(lua(ws(op("logic_compare","EQ",side("ccedu_inspect","forward"),b("ccedu_registry_name").field("NAME","minecraft:oak_log")))).contains("(turtleedu.inspect() == \"minecraft:oak_log\")"));
    }
    @Test void variablesAreSetChangedAndReadByStableId(){
        var lua=lua(ws(b("variables_set").variable().input("VALUE",num(3)).next(b("math_change").variable().input("DELTA",num(-1)).next(b("ccedu_say").input("TEXT",b("variables_get").variable())))));
        assertTrue(lua.startsWith("local v0\nv0 = 3.0\nv0 = (v0 or 0) + -1.0\nturtleedu.say(v0)\n"),lua);
        assertTrue(lua(ws(b("math_change").variable())).contains("v0 = (v0 or 0) + 1"),"an empty change adds one");
        var unknown=b("variables_get");unknown.json.add("fields",new JsonObject());unknown.json.getAsJsonObject("fields").addProperty("VAR","deleted-variable");
        assertEquals("Unknown variable ID",rejected(ws(unknown)).getMessage());
        var duplicate=ws();var vars=duplicate.getAsJsonArray("variables");var same=new JsonObject();same.addProperty("id","var-y");same.addProperty("name","x");vars.add(same);
        assertEquals("Duplicate or invalid variable",rejected(duplicate).getMessage());
    }
    @Test void mathCoversPowerModuloRandomAndRejectsNonFiniteNumbers(){
        assertTrue(lua(ws(op("math_arithmetic","POWER",num(2),num(3)))).contains("(2.0 ^ 3.0)"));
        assertTrue(lua(ws(op("math_arithmetic","MINUS",num(0),b("variables_get").variable()))).contains("(0.0 - v0)"),"legacy unary minus is 0 - value");
        assertTrue(lua(ws(b("math_modulo").input("DIVIDEND",num(64)).input("DIVISOR",num(10)))).contains("(64.0 % 10.0)"));
        assertTrue(lua(ws(b("math_random_float"))).contains("math.random()"));
        assertTrue(lua(ws(b("math_random_int"))).contains("math.random(1, 100)"));
        assertTrue(lua(ws(op("math_arithmetic","ADD",num(1),num(2.5)))).contains("(1.0 + 2.5)"));
        for(var bad:List.of("Infinity","-Infinity","NaN"))assertEquals("Number is not finite",rejected(ws(b("math_number").field("NUM",bad))).getMessage(),bad);
        assertEquals("Invalid OP",rejected(ws(op("math_arithmetic","ROOT",num(1),num(2)))).getMessage());
        assertEquals("Invalid OP",rejected(ws(op("logic_compare","LIKE",num(1),num(2)))).getMessage());
        assertEquals("Invalid OP",rejected(ws(op("logic_operation","XOR",bool(true),bool(true)))).getMessage());
        assertTrue(lua(ws(b("logic_operation").field("OP","AND"))).contains("(false and false)"),"empty logic inputs are false");
        assertTrue(lua(ws(b("logic_null"))).contains("do local result = nil end"));
    }
    @Test void textJoinLengthAndSpeechConvertEveryValueToText(){
        var join=b("text_join").input("ADD0",text("數量：")).input("ADD1",query("getItemCount"));
        join.json.add("extraState",JsonParser.parseString("{\"itemCount\":3}"));
        assertTrue(lua(ws(b("ccedu_say").input("TEXT",join))).contains("turtleedu.say((tostring(\"數量：\") .. tostring(turtle.getItemCount()) .. tostring(\"\")))"));
        var empty=b("text_join");empty.json.add("extraState",JsonParser.parseString("{\"itemCount\":0}"));assertTrue(lua(ws(empty)).contains("do local result = \"\" end"));
        var huge=b("text_join");huge.json.add("extraState",JsonParser.parseString("{\"itemCount\":129}"));assertEquals("Invalid text join size",rejected(ws(huge)).getMessage());
        assertTrue(lua(ws(b("text_length").input("VALUE",text("海龜")))).contains("string.len(tostring(\"海龜\"))"));
        assertTrue(lua(ws(b("ccedu_say"))).contains("turtleedu.say(\"\")"));
    }
    @Test void commentsAndProgramNamesCannotEscapeIntoLua(){
        var lua=lua(ws(b("ccedu_comment").field("TEXT","第一行\nos.shutdown()\r--").next(cmd("forward"))));
        assertTrue(lua.contains("-- 第一行 os.shutdown() --\nturtle.forward()\n"),lua);
        assertFalse(Arrays.asList(lua.split("\n")).contains("os.shutdown()"));
        assertTrue(lua(ws(b("ccedu_call").field("NAME","a\") os.shutdown() (\""))).contains("edu.callProgram(\"a\\\") os.shutdown() (\\\"\")"));
        assertTrue(lua(ws(b("ccedu_say").input("TEXT",text("]]\n\\")))).contains("turtleedu.say(\"]]\\010\\\\\")"));
    }
    @Test void assertionsSnapshotsAndWaitingCompile(){
        assertTrue(lua(ws(b("ccedu_assert").input("TEST",query("detect")).input("MESSAGE",text("前面沒有方塊")))).contains("assert(turtle.detect(), \"前面沒有方塊\")"));
        assertTrue(lua(ws(b("ccedu_assert"))).contains("assert(false, \"Assertion failed\")"));
        assertTrue(lua(ws(b("ccedu_snapshot").field("ACTION","save").next(b("ccedu_snapshot").field("ACTION","restore")))).contains("assert(edu.saveTurtleState())\nassert(edu.restoreTurtleState())"));
        assertEquals("Invalid ACTION",rejected(ws(b("ccedu_snapshot").field("ACTION","delete"))).getMessage());
        assertTrue(lua(ws(b("ccedu_sleep"))).contains("sleep(1)"));
    }
    @Test void debugAnnotationsWrapEveryComputedValueAndVisitEveryLoopPass(){
        var detect=query("detect");var forward=cmd("forward");var counted=b("controls_repeat_ext").shadow("TIMES",num(2)).input("DO",forward);
        var c=WorkspaceCompiler.compile(ws(b("controls_whileUntil").field("MODE","WHILE").input("BOOL",detect).input("DO",counted)));
        String loopId=counted.json.get("id").getAsString();
        assertTrue(c.lua().contains("edu.expression(\""+detect.json.get("id").getAsString()+"\", {[\"x\"]=v0}, function() return turtle.detect() end)"),c.lua());
        assertTrue(c.lua().contains("do\nedu.checkpoint(\""+loopId+"\", {[\"x\"]=v0})\n"),"each pass of the counted loop stops at its checkpoint");
        assertTrue(c.lua().contains("edu.checkpoint(\""+forward.json.get("id").getAsString()+"\""));
        var lines=c.lua().split("\n");
        for(var e:c.lines().entrySet())if(lines[e.getKey()-1].contains("turtle.forward()"))assertEquals(forward.json.get("id").getAsString(),e.getValue(),"error on line "+e.getKey()+" points at the forward block");
        assertEquals(lines.length,c.lines().size(),"every generated line maps to a block");
        var plain=WorkspaceCompiler.compile(ws(b("controls_if").input("IF0",bool(true)).input("DO0",b("ccedu_say").input("TEXT",text("hi"))))).lua();
        assertFalse(plain.contains("edu.expression"),"constants are not wrapped");
    }

    /** Names of helper functions the compiled code may call, from the Java Lua APIs and the Lua start-up file. */
    private static Set<String> helpers(String api)throws Exception{
        var source=Files.readString(Path.of("src/main/java/org/singularbots/compat/cct/TeachingLua.java"));
        int split=source.indexOf("class TurtleEdu");var section=api.equals("edu")?source.substring(0,split):source.substring(split);
        var names=new TreeSet<String>();var m=Pattern.compile("@LuaFunction[^\\n]*?public [\\w<>\\[\\],. ]+? (\\w+)\\(").matcher(section);while(m.find())names.add(m.group(1));
        String lua;try(var in=LegacyIconCompilerTest.class.getResourceAsStream("/data/computercraft/lua/rom/autorun/singularbots.lua")){lua=new String(Objects.requireNonNull(in,"start-up Lua is on the classpath").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        m=Pattern.compile("^"+api+"\\.(\\w+) = function",Pattern.MULTILINE).matcher(lua);while(m.find())names.add(m.group(1));
        m=Pattern.compile(api+"\\[\"(\\w+)\" \\.\\. suffix\\]").matcher(lua);while(m.find())for(var s:List.of("","Up","Down"))names.add(m.group(1)+s);
        return names;
    }
    @Test void everyHelperTheCompilerCallsExistsInTheTurtleLuaApis()throws Exception{
        var called=new TreeMap<String,Set<String>>();var all=new ArrayList<B>();
        for(var icon:ICONS.values())all.add(icon.block().get());
        all.add(b("ccedu_snapshot").field("ACTION","save"));all.add(b("ccedu_snapshot").field("ACTION","restore"));
        for(var block:all){
            var m=Pattern.compile("\\b(turtleedu|edu)\\.(\\w+)\\(").matcher(WorkspaceCompiler.compile(ws(block)).lua());
            while(m.find())called.computeIfAbsent(m.group(1),k->new TreeSet<>()).add(m.group(2));
        }
        assertEquals(Set.of("checkRedstone","checkRedstoneUp","checkRedstoneDown","setRedstone","setRedstoneUp","setRedstoneDown","inspect","inspectUp","inspectDown","getItemName","say"),called.get("turtleedu"));
        assertEquals(Set.of("callProgram","checkpoint","expression","saveTurtleState","restoreTurtleState"),called.get("edu"));
        for(var e:called.entrySet()){var available=helpers(e.getKey());for(var name:e.getValue())assertTrue(available.contains(name),e.getKey()+"."+name+" is called by compiled programs but not provided; available "+available);}
    }
}
