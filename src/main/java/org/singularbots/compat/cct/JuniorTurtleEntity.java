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
    private org.singularbots.program.ProgramRuntime runtime=new org.singularbots.program.ProgramRuntime();
    private Restoration snapshot;
    private UUID selectedProgram;
    private UUID libraryOwner;
    private boolean legacyLibraryPending,migrationAttempted;
    private String recordedStatus="stopped",historyDiagnostic="";
    public UUID libraryId(){var owner=getAccess().getOwningPlayer();if(owner==null)throw new IllegalStateException("Robot has no student owner");return owner.getId();}
    public String historyDiagnostic(){return historyDiagnostic;}
    void historyDiagnostic(String value){historyDiagnostic=value;}
    void placedBy(net.minecraft.world.entity.player.Player player,boolean fresh){
        // An item never carries private code or a usable previous computer disk into a new placement.
        if(!player.getUUID().equals(libraryOwner))detachClone();
        unload();setComputerID(-1);libraryOwner=player.getUUID();legacyLibraryPending=false;
        // A robot from a never-placed item takes its owner's saved look; a dropped robot keeps its own.
        if(fresh)StudentPrograms.applyLook(this,libraryOwner);
        JuniorMining.ensure(this);changed();StudentPrograms.record(this,"summon","");
    }
    public UUID selectedProgram(){return selectedProgram;}
    public void selectProgram(UUID value){selectedProgram=value;setChanged();}
    public Restoration snapshot(){return snapshot;}
    public void snapshot(Restoration value){snapshot=value;setChanged();}
    public org.singularbots.program.ProgramRuntime runtime(){return runtime;}
    public JuniorTurtleEntity(BlockPos pos,BlockState state) { super(TeachingContent.JUNIOR_ENTITY.get(),pos,state,()->0,ComputerFamily.ADVANCED); }
    public UUID identity() { return identity; }
    void detachClone(){unload();identity=UUID.randomUUID();setComputerID(-1);selectedProgram=null;snapshot=null;runtime=new org.singularbots.program.ProgramRuntime();changed();}
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
    /** Dropped items keep only the look and teacher lock, so robots with the same look stack; placement always starts a new robot identity and disk. */
    private CompoundTag itemTag() {
        var tag=new CompoundTag(); tag.putInt("version",2); tag.putBoolean("locked",locked); tag.putString("decoration",decoration); return tag;
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
        JuniorMining.ensure(this);RemoteService.track(this);super.serverTick();
        var settings=TeachingSettings.get(level.getServer());var computer=getServerComputer();
        if((locked||!settings.allowed())&&(computer!=null&&computer.isOn()||runtime.pending()||!runtime.json().get("status").getAsString().equals("stopped")))ProgramService.stop(this);
        String status=runtime.json().get("status").getAsString();
        if(!status.equals(recordedStatus)){recordedStatus=status;StudentPrograms.record(this,"runtime",runtime.json().has("error")?runtime.json().get("error").getAsString():"");}
    }
    @Override public void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup) { super.saveAdditional(tag,lookup); tag.put("SingularBots",teachingTag());if(snapshot!=null)tag.put("EduSnapshot",snapshot.save()); }
    @Override public void loadServer(CompoundTag tag,HolderLookup.Provider lookup) { super.loadServer(tag,lookup); readTeaching(tag.getCompound("SingularBots"));snapshot=tag.contains("EduSnapshot")?Restoration.load(tag.getCompound("EduSnapshot")):null; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { var tag=super.getUpdateTag(lookup); tag.put("SingularBots",teachingTag()); return tag; }
    @Override public void loadClient(CompoundTag tag,HolderLookup.Provider lookup) { super.loadClient(tag,lookup); readTeaching(tag.getCompound("SingularBots")); }
    @Override public void setRemoved() {
        // Chunk unloading still has our block state. Actual breaking releases the item for replacement.
        if(level instanceof net.minecraft.server.level.ServerLevel server){
            // Never request/load a chunk from its unload callback.
            var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
            if(chunk!=null&&!chunk.getBlockState(worldPosition).is(TeachingContent.JUNIOR.get())){if(getAccess().getPosition().equals(worldPosition))StudentPrograms.record(this,"destroyed","");TurtleIdentities.get(server.getServer()).release(this);}
        }
        super.setRemoved();
    }
    @Override public void transferStateFrom(TurtleBlockEntity previous) {
        // CC skips label transfer when both computers have never been started (ID -1).
        var label=previous.getLabel();
        super.transferStateFrom(previous);
        setLabel(label);
        if (previous instanceof JuniorTurtleEntity junior) { identity=junior.identity; locked=junior.locked; decoration=junior.decoration; runtime=junior.runtime; snapshot=junior.snapshot;selectedProgram=junior.selectedProgram;libraryOwner=junior.libraryOwner;legacyLibraryPending=junior.legacyLibraryPending;migrationAttempted=junior.migrationAttempted;recordedStatus=junior.recordedStatus;historyDiagnostic=junior.historyDiagnostic;
            if(level instanceof net.minecraft.server.level.ServerLevel server)TurtleIdentities.get(server.getServer()).move(junior,this);
        }
        RemoteService.track(this);
    }
    @Override public dan200.computercraft.api.peripheral.IPeripheral peripheral(){return null;}
    @Override protected void collectSafeComponents(DataComponentMap.Builder builder) {
        RemoteService.track(this);
        super.collectSafeComponents(builder); var tag=new CompoundTag(); tag.put("SingularBots",itemTag()); builder.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        builder.set(dan200.computercraft.shared.ModRegistry.DataComponents.COMPUTER_ID.get(),null);
        // CC's item model reads its overlay component, not our teaching tag.
        builder.set(dan200.computercraft.shared.ModRegistry.DataComponents.OVERLAY.get(),
            decoration.equals("none")||level==null?null:level.registryAccess().registryOrThrow(TurtleOverlay.REGISTRY)
                .getHolderOrThrow(ResourceKey.create(TurtleOverlay.REGISTRY,ResourceLocation.fromNamespaceAndPath(TeachingContent.MOD,decoration))));
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var data=input.get(DataComponents.CUSTOM_DATA); if (data!=null) readTeaching(data.copyTag().getCompound("SingularBots"));
    }
    public void changed() { setChanged(); if (level!=null) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3); }
}
