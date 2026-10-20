package org.singularbots.turtle;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RemoteDiagnosticsTest {
    @Test void builtInFailuresHaveBothTranslations() throws Exception {
        try(var en=new InputStreamReader(getClass().getResourceAsStream("/assets/singularbots/lang/en_us.json"),StandardCharsets.UTF_8);
            var zh=new InputStreamReader(getClass().getResourceAsStream("/assets/singularbots/lang/zh_tw.json"),StandardCharsets.UTF_8)){
            var english=JsonParser.parseReader(en).getAsJsonObject();var chinese=JsonParser.parseReader(zh).getAsJsonObject();
            for(var failure:List.of("A teacher is demonstrating; editing is temporarily paused", "Movement obstructed","Too many ongoing turtle commands","Cannot mine an unbreakable block","Teacher locked this turtle","Command cancelled by stop or teacher lock","Save a snapshot first","Inventory interaction denied","Turtle command failed","No space for items","No items to drop","Program call depth exceeds 64","Choose a program to call","Only one start block is allowed","Break must be inside a loop",
                "A program already has that name","Invalid program name","Program library is full","Version conflict: reload before saving")){
                var message=RemoteDiagnostics.describe(failure);
                assertFalse(message.key().isEmpty(),failure);assertEquals(failure,english.get(message.key()).getAsString());
                assertNotEquals(failure,chinese.get(message.key()).getAsString());
            }
            assertEquals(english.keySet(),chinese.keySet(),"Language files must cover the same UI messages");
        }
    }
    @Test void compilerChecksKeepTheirNamesAsArguments() {
        assertEquals(new RemoteDiagnostics.Message("diagnostic.singularbots.compile_unknown_function",List.of("挖礦"),"Unknown function: 挖礦"),RemoteDiagnostics.describe("Unknown function: 挖礦"));
        assertEquals(List.of("SIDE"),RemoteDiagnostics.describe("Invalid SIDE").arguments());
        assertEquals("diagnostic.singularbots.compile_missing_field",RemoteDiagnostics.describe("Missing field NAME").key());
        assertEquals("diagnostic.singularbots.compile_choose_program",RemoteDiagnostics.describe("edu:我的程式:2: Choose a program to call").key());
        // Only a field key counts as a compiler choice; other "Invalid ..." messages keep their own handling.
        assertEquals("diagnostic.singularbots.program_name_invalid",RemoteDiagnostics.describe("Invalid program name").key());
        assertEquals("diagnostic.singularbots.invalid_slot",RemoteDiagnostics.describe("Invalid inventory slot").key());
    }
    @Test void everyCompilerCheckIsTranslated() throws Exception {
        var source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("src/main/java/org/singularbots/program/WorkspaceCompiler.java")),StandardCharsets.UTF_8);
        var literal=java.util.regex.Pattern.compile("invalid\\(\"([^\"]+)\"\\)").matcher(source);
        int checked=0;
        while(literal.find()){checked++;assertFalse(RemoteDiagnostics.describe(literal.group(1)).key().isEmpty(),literal.group(1));}
        assertTrue(checked>20,"found the compiler's checks");
    }
    @Test void namesAndReasonsAfterAKnownPrefixRemainAnArgument() {
        assertEquals(new RemoteDiagnostics.Message("diagnostic.singularbots.library_missing",List.of("挖礦"),"Library program not found: 挖礦"),RemoteDiagnostics.describe("Library program not found: 挖礦"));
        assertEquals(List.of("disk full"),RemoteDiagnostics.describe("Activity history could not be saved; programs remain stored: disk full").arguments());
    }
    @Test void restorationCoordinatesRemainAnArgument() {
        var message=RemoteDiagnostics.describe("Restoration conflict at -12, 64, 90");
        assertEquals("diagnostic.singularbots.restore_at",message.key());
        assertEquals(List.of("-12, 64, 90"),message.arguments());
    }
    @Test void programLocationPrefixDoesNotHideAKnownFailure() {
        var message=RemoteDiagnostics.describe("edu-program:12: Movement obstructed");
        assertEquals("diagnostic.singularbots.movement_obstructed",message.key());
        assertEquals("diagnostic.singularbots.no_space",RemoteDiagnostics.describe("edu:My mining: program:3: No space for items").key());
        assertEquals("",RemoteDiagnostics.describe("edu-program:4: attempt to call a nil value").key());
    }
    @Test void arbitraryLuaAndUserMessagesRemainVerbatim() {
        String error="student.lua:2: attempt to add a string value (自訂訊息)";
        var message=RemoteDiagnostics.describe(error);
        assertEquals("",message.key());assertEquals(error,message.fallback());
        assertEquals("",RemoteDiagnostics.describe(null).fallback());
    }
}
