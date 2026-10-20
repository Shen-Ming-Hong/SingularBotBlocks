package org.singularbots.program;

import java.nio.ByteBuffer;
import java.nio.charset.*;

/** CC Lua strings are bytes. Java Strings in its native API use an 8-bit mapping. */
public final class LuaText {
    private LuaText(){}
    public static byte[] encode(String text){return text==null?null:text.getBytes(StandardCharsets.UTF_8);}
    /**
     * Text for a LuaException. CC turns an error message into a Lua string one char per byte and replaces anything past
     * U+00FF with "?", so a Chinese program name became "??". Its UTF-8 bytes carried as 8-bit chars survive, and
     * {@link #decode} reads them back when the runtime reports the error.
     */
    public static String error(String text){return text==null?null:new String(text.getBytes(StandardCharsets.UTF_8),StandardCharsets.ISO_8859_1);}
    public static String decode(String bytes){
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.getBytes(StandardCharsets.ISO_8859_1))).toString();}
        catch(CharacterCodingException e){return bytes;}
    }
}
