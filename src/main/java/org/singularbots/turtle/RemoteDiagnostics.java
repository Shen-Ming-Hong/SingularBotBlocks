package org.singularbots.turtle;

import java.util.List;
import java.util.Map;

/** Recognised engine diagnostics only. Preserve arbitrary Lua/user errors verbatim. */
public final class RemoteDiagnostics {
    private RemoteDiagnostics() { }
    private static final Map<String,String> KEYS=Map.ofEntries(
        Map.entry("Too many ongoing turtle commands","diagnostic.singularbots.queue_full"),
        Map.entry("Movement obstructed","diagnostic.singularbots.movement_obstructed"),
        Map.entry("Movement failed","diagnostic.singularbots.movement_failed"),
        Map.entry("Out of fuel","diagnostic.singularbots.out_of_fuel"),
        Map.entry("Too low to move","diagnostic.singularbots.too_low"),
        Map.entry("Too high to move","diagnostic.singularbots.too_high"),
        Map.entry("Cannot leave the world","diagnostic.singularbots.outside_world"),
        Map.entry("Cannot enter protected area","diagnostic.singularbots.protected_area"),
        Map.entry("Cannot leave loaded world","diagnostic.singularbots.unloaded_world"),
        Map.entry("Cannot pass the world border","diagnostic.singularbots.world_border"),
        Map.entry("No items to place","diagnostic.singularbots.no_items"),
        Map.entry("Cannot place block here","diagnostic.singularbots.place_block"),
        Map.entry("Cannot place item here","diagnostic.singularbots.place_item"),
        Map.entry("No tool to dig with","diagnostic.singularbots.no_dig_tool"),
        Map.entry("No tool to attack with","diagnostic.singularbots.no_attack_tool"),
        Map.entry("Cannot break unbreakable block","diagnostic.singularbots.unbreakable"),
        Map.entry("Cannot mine an unbreakable block","diagnostic.singularbots.mine_unbreakable"),
        Map.entry("Cannot break block with this tool","diagnostic.singularbots.wrong_tool"),
        Map.entry("Nothing to dig here","diagnostic.singularbots.nothing_dig"),
        Map.entry("Nothing to attack here","diagnostic.singularbots.nothing_attack"),
        Map.entry("Cannot break protected block","diagnostic.singularbots.protected_block"),
        Map.entry("Invalid inventory slot","diagnostic.singularbots.invalid_slot"),
        Map.entry("Teacher permission required","diagnostic.singularbots.teacher_required"),
        Map.entry("Unknown remote command","diagnostic.singularbots.unknown_command"),
        Map.entry("Control permission expired","diagnostic.singularbots.control_expired"),
        Map.entry("Command cancelled by stop or teacher lock","diagnostic.singularbots.cancelled"),
        Map.entry("Teacher stopped or disabled turtles","diagnostic.singularbots.teacher_stopped"),
        Map.entry("Teacher locked this turtle","diagnostic.singularbots.teacher_locked"),
        Map.entry("Teacher building permission denied","diagnostic.singularbots.build_denied"),
        Map.entry("Inventory interaction denied","diagnostic.singularbots.inventory_denied"),
        Map.entry("Teacher boundary blocks movement","diagnostic.singularbots.boundary"),
        Map.entry("The robot's built-in diamond pickaxe cannot be removed","diagnostic.singularbots.pickaxe_fixed"),
        Map.entry("Save a snapshot first","diagnostic.singularbots.snapshot_first"),
        Map.entry("Junior turtle required","diagnostic.singularbots.junior_required"),
        Map.entry("Snapshot is no longer active","diagnostic.singularbots.snapshot_inactive"),
        Map.entry("Restoration conflict: turtle position or inventory changed","diagnostic.singularbots.restore_changed"),
        Map.entry("Restoration target is unloaded or protected","diagnostic.singularbots.restore_protected"),
        Map.entry("Restoration conflict: a dropped item was moved, collected or changed","diagnostic.singularbots.restore_drop"),
        Map.entry("Restoration target is occupied","diagnostic.singularbots.restore_occupied"),
        Map.entry("Cannot return to snapshot position","diagnostic.singularbots.restore_return"),
        Map.entry("Snapshot restored; save another snapshot before continuing","diagnostic.singularbots.snapshot_restored"),
        Map.entry("Restoration conflict: another actor changed a recorded block","diagnostic.singularbots.restore_actor"),
        Map.entry("Snapshot change limit exceeded; restoration is unavailable","diagnostic.singularbots.snapshot_limit"),
        Map.entry("Snapshot exceeds safe restoration limits","diagnostic.singularbots.snapshot_budget"),
        Map.entry("Restoration conflict: a container changed between commands","diagnostic.singularbots.restore_container"),
        Map.entry("Restoration conflict: an item changed between commands","diagnostic.singularbots.restore_item"),
        Map.entry("Restoration conflict: inventory changed outside turtle commands","diagnostic.singularbots.restore_inventory"),
        Map.entry("Too many item entities to restore safely","diagnostic.singularbots.restore_items"),
        Map.entry("Too many nearby items to restore safely","diagnostic.singularbots.restore_nearby"),
        Map.entry("Item use or fluid placement cannot be restored safely","diagnostic.singularbots.restore_fluid"),
        Map.entry("Entity spawning cannot be restored safely","diagnostic.singularbots.restore_entity"),
        Map.entry("Combat cannot be restored safely","diagnostic.singularbots.restore_combat"));
    public record Message(String key,List<String> arguments,String fallback) { }
    public static Message describe(String error) {
        String value=error==null?"":error;
        String key=KEYS.get(value);
        if(key!=null)return new Message(key,List.of(),value);
        for(var prefix:Map.of("Restoration conflict at ","restore_at","Teacher permission denied restoration at ","restore_denied_at").entrySet()){
            if(value.startsWith(prefix.getKey()))return new Message("diagnostic.singularbots."+prefix.getValue(),List.of(value.substring(prefix.getKey().length())),value);
        }
        return new Message("",List.of(),value);
    }
}
