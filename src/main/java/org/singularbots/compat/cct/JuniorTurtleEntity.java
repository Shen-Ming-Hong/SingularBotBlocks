package org.singularbots.compat.cct;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
import dan200.computercraft.shared.turtle.TurtleOverlay;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import org.singularbots.turtle.Appearance;
import java.util.UUID;

public final class JuniorTurtleEntity extends TurtleBlockEntity {
    private UUID identity=UUID.randomUUID();
    private boolean locked;
    private String decoration="none";
    private String signal="";
    private org.singularbots.program.ProgramRuntime runtime=new org.singularbots.program.ProgramRuntime();
    private Restoration snapshot;
    private UUID selectedProgram;
    private UUID libraryOwner;
    private boolean legacyLibraryPending,migrationAttempted;
    private String recordedStatus="stopped",historyDiagnostic="";
    /** Set while "My robots" recalls this robot, so its removal is recorded as a recall instead of destruction. */
    boolean recalling;
    public UUID libraryId(){var owner=getAccess().getOwningPlayer();if(owner==null)throw new IllegalStateException("Robot has no student owner");return owner.getId();}
    public String historyDiagnostic(){return historyDiagnostic;}
    void historyDiagnostic(String value){historyDiagnostic=value;}
    void placedBy(net.minecraft.world.entity.player.Player player){
        // Fit the built-in pickaxe first: fitting an upgrade starts CC's computer, which the reset below discards.
        JuniorMining.ensure(this);
        // An item never carries private code or a usable previous computer disk into a new placement.
        if(!player.getUUID().equals(libraryOwner))detachClone();
        unload();setComputerID(-1);libraryOwner=player.getUUID();legacyLibraryPending=false;
        // Robot items carry no look: whoever places one gives it their own saved name and look.
        StudentPrograms.applyLook(this,libraryOwner);
        // The robot takes its number in the student's "My robots" list as it is placed.
        if(level instanceof net.minecraft.server.level.ServerLevel server){RemoteService.track(this);TurtleIdentities.get(server.getServer()).describe(this,libraryOwner);}
        changed();StudentPrograms.record(this,"summon","");
    }
    public UUID selectedProgram(){return selectedProgram;}
    public void selectProgram(UUID value){selectedProgram=value;setChanged();}
    public Restoration snapshot(){return snapshot;}
    public void snapshot(Restoration value){snapshot=value;setChanged();}
    public org.singularbots.program.ProgramRuntime runtime(){return runtime;}
    public JuniorTurtleEntity(BlockPos pos,BlockState state) { super(TeachingContent.JUNIOR_ENTITY.get(),pos,state,()->0,ComputerFamily.ADVANCED); }
    public UUID identity() { return identity; }
    void detachClone(){if(level instanceof net.minecraft.server.level.ServerLevel server)TurtleIdentities.get(server.getServer()).release(this);unload();identity=UUID.randomUUID();setComputerID(-1);selectedProgram=null;snapshot=null;runtime=new org.singularbots.program.ProgramRuntime();changed();}
    public boolean locked() { return locked; }
    public String decoration() { return decoration; }
    public void lock(boolean value) { locked=value; if(value)ProgramService.stop(this); changed(); }
    public void decorate(String value) {
        if (!Appearance.DECORATIONS.contains(value)) throw new IllegalArgumentException("Invalid decoration");
        decoration=value; changed();
    }
    @Override public TurtleOverlay getOverlay() {
        return decoration.equals("none") ? null : new TurtleOverlay(ResourceLocation.fromNamespaceAndPath(TeachingContent.MOD,"block/turtle_junior_overlay_"+decoration),false);
    }
    private CompoundTag teachingTag() {
        var tag=new CompoundTag(); tag.putInt("version",2); tag.putUUID("identity",identity); tag.putBoolean("locked",locked); tag.putString("decoration",decoration);if(selectedProgram!=null)tag.putUUID("program",selectedProgram);if(libraryOwner!=null)tag.putUUID("libraryOwner",libraryOwner);tag.putBoolean("legacyLibraryPending",legacyLibraryPending);return tag;
    }
    /** Dropped items keep only a teacher lock, so breaking a robot cannot lift it; every unlocked robot item is the same and stacks. Placement always starts a new robot identity, disk and look. */
    private CompoundTag itemTag() {
        var tag=new CompoundTag(); tag.putInt("version",2); tag.putBoolean("locked",true); return tag;
    }
    private void readTeaching(CompoundTag tag) {
        if (tag.hasUUID("identity")) identity=tag.getUUID("identity");
        locked=tag.getBoolean("locked"); var value=tag.getString("decoration");
        selectedProgram=tag.hasUUID("program")?tag.getUUID("program"):null;
        libraryOwner=tag.hasUUID("libraryOwner")?tag.getUUID("libraryOwner"):null;
        legacyLibraryPending=tag.getInt("version")<2||tag.getBoolean("legacyLibraryPending");
        decoration=Appearance.DECORATIONS.contains(value) ? value : "none";
    }
    @Override protected void serverTick() {
        if(getAccess().getOwningPlayer()!=null){
            libraryOwner=libraryId();
            if(legacyLibraryPending&&!migrationAttempted){migrationAttempted=true;try{StudentPrograms.migrateLegacy(this);legacyLibraryPending=false;setChanged();}catch(Exception error){historyDiagnostic=error.getMessage();}}
        }
        JuniorMining.ensure(this);RemoteService.track(this);
        if(libraryOwner!=null&&level instanceof net.minecraft.server.level.ServerLevel server)TurtleIdentities.get(server.getServer()).describe(this,libraryOwner);
        super.serverTick();
        var settings=TeachingSettings.get(level.getServer());var computer=getServerComputer();
        if((locked||!settings.allowed())&&(computer!=null&&computer.isOn()||runtime.pending()||!runtime.json().get("status").getAsString().equals("stopped")))ProgramService.stop(this);
        String status=runtime.json().get("status").getAsString();
        if(!status.equals(recordedStatus)){recordedStatus=status;StudentPrograms.record(this,"runtime",runtime.json().has("error")?runtime.json().get("error").getAsString():"");}
        // Checked apart from the recorded status: a robot that moved is a new block entity that carries the status over, and its
        // clients must still hear when the edge light goes back to idle after the last move.
        if(!signal(status).equals(signal)){signal=signal(status);changed();}
    }
    private static final net.minecraft.core.particles.DustParticleOptions RUNNING_SPARK=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.57f,.85f,.42f),.6f);
    private static final net.minecraft.core.particles.DustParticleOptions IDLE_SPARK=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.65f,.93f,.96f),.5f);
    private static final net.minecraft.core.particles.DustParticleOptions ERROR_SPARK=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.87f,.34f,.32f),.7f);
    /** State particles, client side only and sparse so a classroom of robots stays readable: now and then a faint blue spark while idle, green sparks while a program runs, red sparks and a little smoke after an error. Only the white robot has the signal edges they come from. */
    @Override protected void clientTick() {
        super.clientTick();
        boolean white=getColour()==-1||(getColour()&0xffffff)==org.singularbots.turtle.Appearance.COLOURS[0];
        if(level==null||!white)return;
        var random=level.random;
        if(random.nextFloat()>switch(signal){case "running" -> .2f; case "error" -> .15f; default -> .03f;})return;
        // A point on the body's top edges (x 2..14, z 2..13, y 14 in sixteenths).
        double along=random.nextDouble()*46,x,z;
        if(along<12){x=2+along;z=2;}else if(along<23){x=14;z=2+along-12;}else if(along<35){x=14-(along-23);z=13;}else{x=2;z=13-(along-35);}
        double px=worldPosition.getX()+x/16,py=worldPosition.getY()+14/16.0,pz=worldPosition.getZ()+z/16;
        if(signal.isEmpty())level.addParticle(IDLE_SPARK,px,py,pz,0,.015,0);
        else if(signal.equals("running"))level.addParticle(RUNNING_SPARK,px,py,pz,0,.02,0);
        else{level.addParticle(ERROR_SPARK,px,py,pz,0,.01,0);if(random.nextFloat()<.25f)level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,worldPosition.getX()+.5,py+.05,worldPosition.getZ()+.5,0,.02,0);}
    }
    @Override public void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup) { super.saveAdditional(tag,lookup); tag.put("SingularBots",teachingTag());if(snapshot!=null)tag.put("EduSnapshot",snapshot.save()); }
    @Override public void loadServer(CompoundTag tag,HolderLookup.Provider lookup) { super.loadServer(tag,lookup); readTeaching(tag.getCompound("SingularBots"));snapshot=tag.contains("EduSnapshot")?Restoration.load(tag.getCompound("EduSnapshot")):null; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { var tag=super.getUpdateTag(lookup); var teaching=teachingTag(); teaching.putString("signal",signal(runtime.json().get("status").getAsString())); tag.put("SingularBots",teaching); return tag; }
    /** The edge light's program state for clients: "" idle, "running" (also paused while stepping) or "error". */
    public String signal() { return signal; }
    private static String signal(String status) { return switch(status){ case "running","paused" -> "running"; case "error" -> "error"; default -> ""; }; }
    @Override public void loadClient(CompoundTag tag,HolderLookup.Provider lookup) { super.loadClient(tag,lookup); readTeaching(tag.getCompound("SingularBots")); signal=tag.getCompound("SingularBots").getString("signal"); }
    @Override public void setRemoved() {
        // Chunk unloading still has our block state. Actual breaking releases the item for replacement.
        if(level instanceof net.minecraft.server.level.ServerLevel server){
            // Never request/load a chunk from its unload callback.
            var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
            if(chunk!=null&&!chunk.getBlockState(worldPosition).is(TeachingContent.JUNIOR.get())){if(getAccess().getPosition().equals(worldPosition))StudentPrograms.record(this,recalling?"recalled":"destroyed","");TurtleIdentities.get(server.getServer()).release(this);}
        }
        super.setRemoved();
    }
    @Override public void transferStateFrom(TurtleBlockEntity previous) {
        // CC skips label transfer when both computers have never been started (ID -1).
        var label=previous.getLabel();
        super.transferStateFrom(previous);
        setLabel(label);
        if (previous instanceof JuniorTurtleEntity junior) { identity=junior.identity; locked=junior.locked; decoration=junior.decoration; runtime=junior.runtime; snapshot=junior.snapshot;selectedProgram=junior.selectedProgram;libraryOwner=junior.libraryOwner;legacyLibraryPending=junior.legacyLibraryPending;migrationAttempted=junior.migrationAttempted;recordedStatus=junior.recordedStatus;signal=junior.signal;historyDiagnostic=junior.historyDiagnostic;
            if(level instanceof net.minecraft.server.level.ServerLevel server)TurtleIdentities.get(server.getServer()).move(junior,this);
        }
        RemoteService.track(this);
    }
    @Override public dan200.computercraft.api.peripheral.IPeripheral peripheral(){return null;}
    @Override protected void collectSafeComponents(DataComponentMap.Builder builder) {
        RemoteService.track(this);
        super.collectSafeComponents(builder);
        if(locked){var tag=new CompoundTag(); tag.put("SingularBots",itemTag()); builder.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
        builder.set(dan200.computercraft.shared.ModRegistry.DataComponents.COMPUTER_ID.get(),null);
        // The name and look stay with the robot's owner, not the item; the holder's client draws their own look on it.
        builder.set(DataComponents.CUSTOM_NAME,null);builder.set(DataComponents.DYED_COLOR,null);
        builder.set(dan200.computercraft.shared.ModRegistry.DataComponents.OVERLAY.get(),null);
        // Robots use no fuel; CC's empty tank would still keep dropped robots from stacking with new ones.
        if(getAccess().getFuelLevel()==0)builder.set(dan200.computercraft.shared.ModRegistry.DataComponents.FUEL.get(),null);
        // The built-in pickaxe is part of the robot, not the item; placing the item fits it again.
        if(JuniorMining.builtIn(getAccess().getUpgradeWithData(dan200.computercraft.api.turtle.TurtleSide.LEFT)))
            builder.set(dan200.computercraft.shared.ModRegistry.DataComponents.LEFT_TURTLE_UPGRADE.get(),null);
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var data=input.get(DataComponents.CUSTOM_DATA); if (data!=null) readTeaching(data.copyTag().getCompound("SingularBots"));
    }
    public void changed() { setChanged(); if (level!=null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3); }
}
