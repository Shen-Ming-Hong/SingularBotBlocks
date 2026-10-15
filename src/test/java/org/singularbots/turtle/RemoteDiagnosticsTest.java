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
            for(var failure:List.of("Movement obstructed","Too many ongoing turtle commands","Cannot mine an unbreakable block","Teacher locked this turtle","Command cancelled by stop or teacher lock","Save a snapshot first","Inventory interaction denied")){
                var message=RemoteDiagnostics.describe(failure);
                assertFalse(message.key().isEmpty(),failure);assertEquals(failure,english.get(message.key()).getAsString());
                assertNotEquals(failure,chinese.get(message.key()).getAsString());
            }
            assertEquals(english.keySet(),chinese.keySet(),"Language files must cover the same UI messages");
        }
    }
    @Test void restorationCoordinatesRemainAnArgument() {
        var message=RemoteDiagnostics.describe("Restoration conflict at -12, 64, 90");
        assertEquals("diagnostic.singularbots.restore_at",message.key());
        assertEquals(List.of("-12, 64, 90"),message.arguments());
    }
    @Test void arbitraryLuaAndUserMessagesRemainVerbatim() {
        String error="student.lua:2: attempt to add a string value (自訂訊息)";
        var message=RemoteDiagnostics.describe(error);
        assertEquals("",message.key());assertEquals(error,message.fallback());
        assertEquals("",RemoteDiagnostics.describe(null).fallback());
    }
}
