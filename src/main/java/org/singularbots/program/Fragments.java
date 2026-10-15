package org.singularbots.program;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Strict ordered, bounded upload; the caller owns timeout and connection lifetime. */
public final class Fragments {
    public static final int CHUNK=8192,MAX_CHUNKS=130;
    public static java.util.List<String> split(String value){
        if(value.getBytes(StandardCharsets.UTF_8).length>ProgramStore.MAX_BYTES+8192)throw new IllegalArgumentException("Message exceeds 1 MiB");
        var parts=new java.util.ArrayList<String>();
        for(int start=0;start<value.length();){int end=Math.min(value.length(),start+CHUNK);if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1))&&Character.isLowSurrogate(value.charAt(end)))end--;parts.add(value.substring(start,end));start=end;}
        if(parts.isEmpty())parts.add("");if(parts.size()>MAX_CHUNKS)throw new IllegalArgumentException("Too many fragments");return parts;
    }
    public final UUID request,turtle;
    public final int total;
    public final long started;
    private int index,bytes;
    private final StringBuilder text=new StringBuilder();
    public Fragments(UUID request,UUID turtle,int total,long started){if(total<1||total>MAX_CHUNKS)throw new IllegalArgumentException("Invalid fragment count");this.request=request;this.turtle=turtle;this.total=total;this.started=started;}
    public String append(int index,int total,String data){if(index!=this.index||total!=this.total||data.length()>CHUNK)throw new IllegalArgumentException("Invalid fragment order or size");bytes+=data.getBytes(StandardCharsets.UTF_8).length;if(bytes>ProgramStore.MAX_BYTES+8192)throw new IllegalArgumentException("Upload exceeds 1 MiB");text.append(data);this.index++;return this.index==this.total?text.toString():null;}
}
