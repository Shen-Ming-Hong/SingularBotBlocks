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
        Map.entry("Turtle command failed","diagnostic.singularbots.command_failed"),
        Map.entry("No space for items","diagnostic.singularbots.no_space"),
        Map.entry("No items to take","diagnostic.singularbots.nothing_to_take"),
        Map.entry("No items to drop","diagnostic.singularbots.nothing_to_drop"),
        Map.entry("Cannot place in protected area","diagnostic.singularbots.place_protected"),
        Map.entry("Program call depth exceeds 64","diagnostic.singularbots.call_depth"),
        Map.entry("Expected boolean","diagnostic.singularbots.expected_boolean"),
        Map.entry("Terminated","diagnostic.singularbots.terminated"),
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
        Map.entry("A teacher is demonstrating; editing is temporarily paused","diagnostic.singularbots.teacher_demonstrating"),
        Map.entry("Teacher permission required","diagnostic.singularbots.teacher_required"),
        Map.entry("Unknown remote command","diagnostic.singularbots.unknown_command"),
        Map.entry("Control permission expired","diagnostic.singularbots.control_expired"),
        Map.entry("Command cancelled by stop or teacher lock","diagnostic.singularbots.cancelled"),
        Map.entry("Teacher stopped or disabled turtles","diagnostic.singularbots.teacher_stopped"),
        Map.entry("Teacher locked this turtle","diagnostic.singularbots.teacher_locked"),
        Map.entry("This robot is no longer in your list","diagnostic.singularbots.robot_gone"),
        Map.entry("Only the robot's owner or a teacher can recall it","diagnostic.singularbots.recall_owner"),
        Map.entry("A teacher locked this robot","diagnostic.singularbots.recall_locked"),
        Map.entry("No free space next to you; move to an open place, or walk to the robot and take out its items","diagnostic.singularbots.recall_no_space"),
        Map.entry("The robot could not move here; try an open place","diagnostic.singularbots.recall_failed"),
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
        Map.entry("Combat cannot be restored safely","diagnostic.singularbots.restore_combat"),
        // Program library checks from ProgramStore and ProgramService.
        Map.entry("A program already has that name","diagnostic.singularbots.program_name_taken"),
        Map.entry("Invalid program name","diagnostic.singularbots.program_name_invalid"),
        Map.entry("Program library is full","diagnostic.singularbots.library_full"),
        Map.entry("Program exceeds 1 MiB","diagnostic.singularbots.program_too_large"),
        Map.entry("Version conflict: reload before saving","diagnostic.singularbots.save_conflict"),
        Map.entry("Version conflict: reload before deleting","diagnostic.singularbots.delete_conflict"),
        Map.entry("Program not found","diagnostic.singularbots.program_missing"),
        Map.entry("Too many program requests","diagnostic.singularbots.program_requests"),
        Map.entry("Teacher disabled program sharing in this world","diagnostic.singularbots.sharing_disabled"),
        Map.entry("Teacher disabled Blockly editing; Lua programs remain available","diagnostic.singularbots.blocks_disabled"),
        // Block program checks from WorkspaceCompiler.
        Map.entry("Too many variables","diagnostic.singularbots.compile_variables"),
        Map.entry("Duplicate or invalid variable","diagnostic.singularbots.compile_variable"),
        Map.entry("Only one start block is allowed","diagnostic.singularbots.compile_one_start"),
        Map.entry("Duplicate or invalid function name","diagnostic.singularbots.compile_function_name"),
        Map.entry("Unknown or repeated function input","diagnostic.singularbots.compile_function_input"),
        Map.entry("Too many functions","diagnostic.singularbots.compile_functions"),
        Map.entry("Workspace nesting exceeds 128","diagnostic.singularbots.compile_workspace_nesting"),
        Map.entry("Missing or duplicate stable block ID","diagnostic.singularbots.compile_block_id"),
        Map.entry("Workspace exceeds 4096 blocks","diagnostic.singularbots.compile_blocks"),
        Map.entry("Field too large","diagnostic.singularbots.compile_field_size"),
        Map.entry("Too many nested statements","diagnostic.singularbots.compile_statement_nesting"),
        Map.entry("Unknown turtle command","diagnostic.singularbots.compile_command"),
        Map.entry("Command does not accept an argument","diagnostic.singularbots.compile_argument"),
        Map.entry("Choose a program to call","diagnostic.singularbots.compile_choose_program"),
        Map.entry("Return must be inside a function","diagnostic.singularbots.compile_return"),
        Map.entry("Break must be inside a loop","diagnostic.singularbots.compile_break"),
        Map.entry("Only break is supported","diagnostic.singularbots.compile_only_break"),
        Map.entry("Expression nesting exceeds 64","diagnostic.singularbots.compile_expression_nesting"),
        Map.entry("Number is not finite","diagnostic.singularbots.compile_number"),
        Map.entry("Invalid item or block ID","diagnostic.singularbots.compile_registry_name"),
        Map.entry("Invalid text join size","diagnostic.singularbots.compile_text_join"),
        Map.entry("Invalid turtle query","diagnostic.singularbots.compile_query"),
        Map.entry("Invalid boolean turtle action","diagnostic.singularbots.compile_boolean_action"),
        Map.entry("Compiled program exceeds 1 MiB","diagnostic.singularbots.compile_size"),
        Map.entry("Unknown variable ID","diagnostic.singularbots.compile_variable_id"));
    // Compiler checks that name a function, block type or field; "Invalid X" only with a field key, so "Invalid program name" stays as is.
    private static final Map<java.util.regex.Pattern,String> NAMED=Map.of(
        java.util.regex.Pattern.compile("^Unknown function: (.+)$",java.util.regex.Pattern.DOTALL),"diagnostic.singularbots.compile_unknown_function",
        java.util.regex.Pattern.compile("^Unsupported block: (.+)$",java.util.regex.Pattern.DOTALL),"diagnostic.singularbots.compile_unsupported_block",
        java.util.regex.Pattern.compile("^Invalid ([A-Z][A-Z0-9_]*)$",java.util.regex.Pattern.DOTALL),"diagnostic.singularbots.compile_choice",
        java.util.regex.Pattern.compile("^Missing field ([A-Z][A-Z0-9_]*)$",java.util.regex.Pattern.DOTALL),"diagnostic.singularbots.compile_missing_field",
        java.util.regex.Pattern.compile("^Malformed block data: (.+)$",java.util.regex.Pattern.DOTALL),"diagnostic.singularbots.compile_malformed_block",
        java.util.regex.Pattern.compile("^Malformed workspace: (.+)$",java.util.regex.Pattern.DOTALL),"diagnostic.singularbots.compile_malformed_workspace");
    // A turtle or runtime failure raised inside a student's program arrives as "edu-program:12: Movement obstructed"
    // (or "edu:<library program>:3: ..."); the editor already points at the block or line, so only the reason is shown.
    private static final java.util.regex.Pattern LOCATION=java.util.regex.Pattern.compile("^(?:edu-program|edu:.+?):\\d+: (.+)$",java.util.regex.Pattern.DOTALL);
    // Messages that end in a name, coordinates or an I/O reason, which stays as the argument.
    private static final Map<String,String> PREFIXES=Map.of("Restoration conflict at ","restore_at","Teacher permission denied restoration at ","restore_denied_at",
        "Library program not found: ","library_missing",
        "Activity history could not be saved; programs remain stored: ","history_failed",
        "Default robot appearance could not be saved: ","appearance_save_failed",
        "Default robot appearance could not be applied: ","appearance_apply_failed");
    public record Message(String key,List<String> arguments,String fallback) { }
    public static Message describe(String error) {
        String value=error==null?"":error;
        String key=KEYS.get(value);
        if(key!=null)return new Message(key,List.of(),value);
        for(var prefix:PREFIXES.entrySet()){
            if(value.startsWith(prefix.getKey()))return new Message("diagnostic.singularbots."+prefix.getValue(),List.of(value.substring(prefix.getKey().length())),value);
        }
        for(var named:NAMED.entrySet()){var m=named.getKey().matcher(value);if(m.matches())return new Message(named.getValue(),List.of(m.group(1)),value);}
        var located=LOCATION.matcher(value);
        if(located.matches()){var inner=describe(located.group(1));if(!inner.key().isEmpty())return new Message(inner.key(),inner.arguments(),value);}
        return new Message("",List.of(),value);
    }
    /** The reason in the player's game language; anything unrecognised stays in its original words. */
    public static String localized(String error){
        var message=describe(error);
        return message.key().isEmpty()?message.fallback():net.minecraft.network.chat.Component.translatable(message.key(),message.arguments().toArray()).getString();
    }
}
