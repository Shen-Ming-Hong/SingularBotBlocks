package org.singularbots.compat.cct;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public final class RemoteMenu extends AbstractContainerMenu {
    public final UUID turtleId;
    private final Player viewer;
    private final Container inventory;
    public RemoteMenu(int id,Inventory player,UUID turtle) {
        super(TeachingContent.REMOTE_MENU.get(),id); turtleId=turtle; viewer=player.player;
        inventory=viewer instanceof ServerPlayer ? new LiveInventory() : new SimpleContainer(16);
        for(int y=0;y<4;y++) for(int x=0;x<4;x++) addSlot(new Slot(inventory,x+y*4,174+x*18,19+y*18));
        for(int y=0;y<3;y++) for(int x=0;x<9;x++) addSlot(new Slot(player,x+y*9+9,8+x*18,19+y*18));
        for(int x=0;x<9;x++) addSlot(new Slot(player,x,8+x*18,89));
    }
    @Override public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer server)) return true;
        var t=RemoteService.resolve(server.serverLevel(),turtleId); return t!=null && RemoteService.authorized(server,t);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) { if(stillValid(player)) super.clicked(slot,button,type,player); }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player)||index<0||index>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(index); if(!slot.hasItem()) return ItemStack.EMPTY;
        var item=slot.getItem(); var copy=item.copy();
        if(!moveItemStackTo(item,index<16?16:0,index<16?52:16,index<16)) return ItemStack.EMPTY;
        if(item.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if(item.getCount()==copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player,item); return copy;
    }
    private final class LiveInventory implements Container {
        private Container current() { var t=RemoteService.resolve(((ServerPlayer)viewer).serverLevel(),turtleId); return t==null ? null : t.getAccess().getInventory(); }
        public int getContainerSize(){return 16;}
        public boolean isEmpty(){var c=current();return c==null||c.isEmpty();}
        public ItemStack getItem(int slot){var c=current();return c==null?ItemStack.EMPTY:c.getItem(slot);}
        public ItemStack removeItem(int slot,int count){var c=current();return c==null?ItemStack.EMPTY:c.removeItem(slot,count);}
        public ItemStack removeItemNoUpdate(int slot){var c=current();return c==null?ItemStack.EMPTY:c.removeItemNoUpdate(slot);}
        public void setItem(int slot,ItemStack item){var c=current();if(c!=null)c.setItem(slot,item);}
        public void setChanged(){var c=current();if(c!=null)c.setChanged();}
        public boolean stillValid(Player player){return RemoteMenu.this.stillValid(player);}
        public void clearContent(){var c=current();if(c!=null)c.clearContent();}
    }
}
