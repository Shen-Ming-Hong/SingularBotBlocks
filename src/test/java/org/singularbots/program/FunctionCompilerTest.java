package org.singularbots.program;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Student-made functions (Blockly's procedure blocks) as the editor saves them, compiled by the server. */
class FunctionCompilerTest {
    private static JsonObject ws(String json){return JsonParser.parseString(json).getAsJsonObject();}
    private static String lua(String json){return WorkspaceCompiler.compile(ws(json),false).lua();}
    private static WorkspaceCompiler.Invalid rejected(String json){return assertThrows(WorkspaceCompiler.Invalid.class,()->WorkspaceCompiler.compile(ws(json)));}
    /** A program that moves up through a function with an input, and stores what another function hands back. */
    private static final String PROGRAM="""
        {"variables":[{"id":"n","name":"格數"},{"id":"x","name":"答案"}],"blocks":{"blocks":[
          {"type":"ccedu_start","id":"start","next":{"block":{"type":"procedures_callnoreturn","id":"call-climb","extraState":{"name":"往上爬","params":["格數"]},
            "inputs":{"ARG0":{"block":{"type":"math_number","id":"two","fields":{"NUM":2}}}},
            "next":{"block":{"type":"variables_set","id":"set","fields":{"VAR":{"id":"x"}},"inputs":{"VALUE":{"block":{"type":"procedures_callreturn","id":"call-double","extraState":{"name":"兩倍","params":["格數"]},
              "inputs":{"ARG0":{"block":{"type":"math_number","id":"three","fields":{"NUM":3}}}}}}}}}}}},
          {"type":"procedures_defnoreturn","id":"climb","extraState":{"params":[{"name":"格數","id":"n"}]},"fields":{"NAME":"往上爬"},
            "inputs":{"STACK":{"block":{"type":"controls_repeat_ext","id":"loop","inputs":{"TIMES":{"block":{"type":"variables_get","id":"get-n","fields":{"VAR":{"id":"n"}}}},
              "DO":{"block":{"type":"ccedu_move","id":"up","fields":{"COMMAND":"up"}}}}}}}},
          {"type":"procedures_defreturn","id":"double","extraState":{"params":[{"name":"格數","id":"n"}]},"fields":{"NAME":"兩倍"},
            "inputs":{"STACK":{"block":{"type":"procedures_ifreturn","id":"guard","inputs":{"CONDITION":{"block":{"type":"logic_compare","id":"neg","fields":{"OP":"LT"},
              "inputs":{"A":{"block":{"type":"variables_get","id":"get-n2","fields":{"VAR":{"id":"n"}}}},"B":{"block":{"type":"math_number","id":"zero","fields":{"NUM":0}}}}}},
              "VALUE":{"block":{"type":"math_number","id":"zero2","fields":{"NUM":0}}}}}},
              "RETURN":{"block":{"type":"math_arithmetic","id":"times","fields":{"OP":"MULTIPLY"},"inputs":{"A":{"block":{"type":"variables_get","id":"get-n3","fields":{"VAR":{"id":"n"}}}},"B":{"block":{"type":"math_number","id":"two2","fields":{"NUM":2}}}}}}}}
        ]}}""";

    @Test void functionsAreDeclaredFirstAndCalledWithTheirInputs(){
        String lua=lua(PROGRAM);
        assertEquals("""
            local v0,v1
            local f0,f1
            f0 = function(v0)
            local limit0 = math.max(0, math.floor(v0))
            for i1 = 1, limit0 do
            turtle.up()
            end
            end
            f1 = function(v0)
            if (v0 < 0.0) then return 0.0 end
            return (v0 * 2.0)
            end
            f0(2.0)
            v1 = f1(3.0)
            """,lua);
    }
    @Test void debugRunsVisitFunctionBodiesAndKeepEveryBlockId(){
        var compiled=WorkspaceCompiler.compile(ws(PROGRAM));
        for(String id:new String[]{"climb","loop","up","double","guard","call-climb","set"})assertTrue(compiled.lines().containsValue(id),id);
        assertTrue(compiled.lua().contains("edu.checkpoint(\"up\""));
        assertTrue(compiled.lua().contains("edu.expression(\"call-double\""),"a call that hands back a value is a highlighted expression");
    }
    @Test void callsToMissingOrDisabledFunctionsAreRejectedAtTheCall(){
        assertEquals("call",rejected("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"procedures_callnoreturn","id":"call","extraState":{"name":"不見了"}}}}]}}""").blockId);
        assertEquals("call",rejected("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"procedures_callnoreturn","id":"call","extraState":{"name":"停用"}}}},
              {"type":"procedures_defnoreturn","id":"def","enabled":false,"disabledReasons":["MANUALLY_DISABLED"],"fields":{"NAME":"停用"}}]}}""").blockId);
    }
    @Test void duplicateNamesUnknownInputsAndStrayReturnsAreRejected(){
        assertEquals("b",rejected("""
            {"blocks":{"blocks":[{"type":"procedures_defnoreturn","id":"a","fields":{"NAME":"跳"}},{"type":"procedures_defreturn","id":"b","fields":{"NAME":"跳"}}]}}""").blockId);
        assertEquals("a",rejected("""
            {"blocks":{"blocks":[{"type":"procedures_defnoreturn","id":"a","extraState":{"params":[{"name":"x","id":"missing"}]},"fields":{"NAME":"跳"}}]}}""").blockId);
        assertEquals("r",rejected("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"procedures_ifreturn","id":"r","inputs":{"CONDITION":{"block":{"type":"logic_boolean","id":"t","fields":{"BOOL":"TRUE"}}}}}}}]}}""").blockId);
    }
    @Test void functionsWithoutInputsOrBodyAndRecursionCompile(){
        String lua=lua("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"procedures_callnoreturn","id":"call","extraState":{"name":"空的"}}}},
              {"type":"procedures_defnoreturn","id":"empty","fields":{"NAME":"空的"}},
              {"type":"procedures_defreturn","id":"self","extraState":{"hasStatements":false},"fields":{"NAME":"自己"},"inputs":{"RETURN":{"block":{"type":"procedures_callreturn","id":"again","extraState":{"name":"自己"}}}}}]}}""");
        assertTrue(lua.contains("f0 = function()\nend\n"),lua);
        assertTrue(lua.contains("f1 = function()\nreturn f1()\nend\n"),lua);
        assertTrue(lua.endsWith("f0()\n"),lua);
    }
    @Test void aBreakInsideAFunctionStillNeedsALoopInsideThatFunction(){
        assertEquals("brk",rejected("""
            {"blocks":{"blocks":[{"type":"procedures_defnoreturn","id":"def","fields":{"NAME":"跳"},"inputs":{"STACK":{"block":{"type":"ccedu_break","id":"brk"}}}}]}}""").blockId);
    }
}
