package org.singularbots.program;

import com.google.gson.*;
import org.singularbots.turtle.Appearance;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** A student's last chosen robot name and look, applied to robots they place from fresh items. */
public final class AppearanceStore {
    public static final int MAX_BYTES=4096;
    /** Only fields the student has set are present; an absent colour keeps CC's default shell. */
    public record Look(String label,Integer colour,String decoration){
        public static final Look NONE=new Look(null,null,null);
    }
    private final Path root;
    public AppearanceStore(Path root){this.root=root;}
    private Path path(UUID student){return root.resolve(student.toString()).resolve("appearance.json");}
    public Look get(UUID student)throws IOException{
        var file=path(student);if(!Files.exists(file))return Look.NONE;
        if(Files.size(file)>MAX_BYTES)throw new IOException("Appearance exceeds limit; original file preserved");
        var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        if(data.get("version").getAsInt()!=1)throw new IOException("Unsupported appearance version; original file preserved");
        String label=data.has("label")?named(Appearance.label(data.get("label").getAsString())):null;
        Integer colour=null;if(data.has("colour")){int value=data.get("colour").getAsInt();for(int c:Appearance.COLOURS)if(c==value)colour=value;}
        String decoration=data.has("decoration")&&Appearance.DECORATIONS.contains(data.get("decoration").getAsString())?data.get("decoration").getAsString():null;
        return new Look(label,colour,decoration);
    }
    /** Clearing the name (an empty or blank field) removes the default name instead of saving a nameless one. */
    public void setLabel(UUID student,String label)throws IOException{var l=get(student);write(student,new Look(named(Appearance.label(label)),l.colour(),l.decoration()));}
    private static String named(String label){return label.isBlank()?null:label;}
    public void setColour(UUID student,int colour)throws IOException{var l=get(student);write(student,new Look(l.label(),colour,l.decoration()));}
    public void setDecoration(UUID student,String decoration)throws IOException{
        if(!Appearance.DECORATIONS.contains(decoration))throw new IllegalArgumentException("Invalid decoration");
        var l=get(student);write(student,new Look(l.label(),l.colour(),decoration));
    }
    private void write(UUID student,Look look)throws IOException{
        var data=new JsonObject();data.addProperty("version",1);
        if(look.label()!=null)data.addProperty("label",look.label());
        if(look.colour()!=null)data.addProperty("colour",look.colour());
        if(look.decoration()!=null)data.addProperty("decoration",look.decoration());
        var file=path(student);Files.createDirectories(file.getParent());var temp=Files.createTempFile(file.getParent(),"appearance-",".tmp");
        try{Files.writeString(temp,data.toString());Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
}
