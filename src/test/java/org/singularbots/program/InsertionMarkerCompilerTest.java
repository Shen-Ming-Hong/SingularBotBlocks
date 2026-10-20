package org.singularbots.program;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Blockly saves an insertion marker, the preview shown while a block is dragged near a connection, as {"block":null}.
 * A save made mid-drag (for example after pasting a block and dragging it onto a stack) must still compile.
 */
class InsertionMarkerCompilerTest {
    private static String lua(String json){return WorkspaceCompiler.compile(JsonParser.parseString(json).getAsJsonObject(),false).lua();}

    @Test void markerAsTheNextBlockEndsTheStack(){
        String lua=lua("""
            {"blocks":{"languageVersion":0,"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"ccedu_move","id":"go","fields":{"COMMAND":"forward"},
              "next":{"block":null}}}}]}}""");
        assertTrue(lua.contains("turtle.forward()"),lua);
    }

    @Test void markerInAStatementInputLeavesItEmpty(){
        String lua=lua("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"controls_repeat_ext","id":"loop",
              "inputs":{"TIMES":{"shadow":{"type":"math_number","id":"n","fields":{"NUM":3}}},"DO":{"block":null}}}}}]}}""");
        assertTrue(lua.contains("for"),lua);
        assertFalse(lua.contains("turtle."),lua);
    }

    @Test void markerOverAShadowFallsBackToTheShadow(){
        String lua=lua("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":{"block":{"type":"controls_repeat_ext","id":"loop",
              "inputs":{"TIMES":{"shadow":{"type":"math_number","id":"n","fields":{"NUM":7}},"block":null},
                "DO":{"block":{"type":"ccedu_move","id":"go","fields":{"COMMAND":"up"}}}}}}}]}}""");
        assertTrue(lua.contains("7"),lua);
        assertTrue(lua.contains("turtle.up()"),lua);
    }

    @Test void nullMembersReadAsAbsent(){
        String lua=lua("""
            {"blocks":{"blocks":[{"type":"ccedu_start","id":"start","next":null,"inputs":null,"extraState":null}]}}""");
        assertNotNull(lua);
        assertNotNull(lua("{\"blocks\":null}"));
    }
}
