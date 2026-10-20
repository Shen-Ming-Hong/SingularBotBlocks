package org.singularbots.compat.cct.client;

import org.singularbots.turtle.Appearance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JuniorBodyModelsTest {
    @Test void paletteMatchesRgbAndOpaqueArgb() {
        for (int i = 0; i < Appearance.COLOURS.length; i++) {
            assertEquals(i, JuniorBodyModels.index(Appearance.COLOURS[i]));
            assertEquals(i, JuniorBodyModels.index(0xff000000 | Appearance.COLOURS[i]));
        }
        assertEquals(0, JuniorBodyModels.index(-1));
        assertEquals(-1, JuniorBodyModels.index(0x123456));
        assertEquals(-1, JuniorBodyModels.index(0xff123456));
    }
}
