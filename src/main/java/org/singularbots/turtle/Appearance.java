package org.singularbots.turtle;

import java.util.List;

/** Legacy palette and decorations; identifiers are persisted, never list indices. */
public final class Appearance {
    private Appearance() { }
    public static final int[] COLOURS = {0xf0f0f0,0xf2b233,0xe57fd8,0x99b2f2,0xdede6c,0x7fcc19,0xf2b2cc,0x4c4c4c,
        0x999999,0x4c99b2,0xb266e5,0x3366cc,0x7f664c,0x57a64e,0xcc4c4c,0x111111};
    public static final List<String> DECORATIONS = List.of("none","3dglasses","bandana_blue","bandana_purple","bandana_red",
        "bandana_yellow","creeper","crown","elf","flames","frown","moustache","smile","snorkel","strawhat","sunglasses","teeth","tuxedo");
    public static int colour(int index) {
        if (index < 0 || index >= COLOURS.length) throw new IllegalArgumentException("Invalid colour");
        return COLOURS[index];
    }
    public static String decoration(int index) {
        if (index < 0 || index >= DECORATIONS.size()) throw new IllegalArgumentException("Invalid decoration");
        return DECORATIONS.get(index);
    }
    public static String label(String text) {
        if (text == null || text.codePointCount(0,text.length()) > 32 || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Names require at most 32 printable characters");
        return text;
    }
}
