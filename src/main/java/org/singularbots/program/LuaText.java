package org.singularbots.program;

import java.nio.ByteBuffer;
import java.nio.charset.*;

/** CC Lua strings are bytes. Java Strings in its native API use an 8-bit mapping. */
public final class LuaText {
    private LuaText(){}
    public static byte[] encode(String text){return text==null?null:text.getBytes(StandardCharsets.UTF_8);}
    public static String decode(String bytes){
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.getBytes(StandardCharsets.ISO_8859_1))).toString();}
        catch(CharacterCodingException e){return bytes;}
    }
}
