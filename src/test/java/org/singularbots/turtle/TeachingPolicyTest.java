package org.singularbots.turtle;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TeachingPolicyTest {
    @Test void ownerTeacherAndOtherStudentHaveDistinctPermissions() {
        var owner=UUID.randomUUID();var student=UUID.randomUUID();var teacher=UUID.randomUUID();
        assertTrue(AccessPolicy.permits(owner,owner,false,false,true,true,10));
        assertFalse(AccessPolicy.permits(student,owner,false,false,true,true,10));
        assertFalse(AccessPolicy.permits(owner,owner,false,true,true,true,10));
        assertTrue(AccessPolicy.permits(teacher,owner,true,true,true,true,10));
        assertFalse(AccessPolicy.permits(teacher,owner,true,false,false,true,10));
        assertFalse(AccessPolicy.permits(teacher,owner,true,false,true,false,10));
        assertFalse(AccessPolicy.permits(owner,owner,false,false,true,true,4097));
        assertFalse(AccessPolicy.permits(owner,owner,false,false,true,true,Double.NaN));
    }
    @Test void legacyDecorationsAndUntrustedNamesAreValidated() {
        assertEquals(18,Appearance.DECORATIONS.size());assertEquals(16,Appearance.COLOURS.length);
        assertTrue(Appearance.DECORATIONS.containsAll(java.util.List.of("3dglasses","crown","snorkel","tuxedo")));
        assertEquals("教學海龜",Appearance.label("教學海龜"));
        assertEquals("🐢".repeat(32),Appearance.label("🐢".repeat(32)));
        assertThrows(IllegalArgumentException.class,()->Appearance.label("🐢".repeat(33)));
        assertThrows(IllegalArgumentException.class,()->Appearance.label("line\ncontrol"));
        assertThrows(IllegalArgumentException.class,()->Appearance.colour(-1));
        assertThrows(IllegalArgumentException.class,()->Appearance.decoration(18));
    }
}
