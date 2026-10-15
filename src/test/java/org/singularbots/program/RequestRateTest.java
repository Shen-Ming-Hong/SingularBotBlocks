package org.singularbots.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestRateTest {
    @Test void allowsTwentyRequestsPerSecond() {
        var rate = new RequestRate(1000);
        for (int i = 0; i < RequestRate.LIMIT; i++) assertTrue(rate.allow(1000 + i));
        assertFalse(rate.allow(1500));
        assertTrue(rate.allow(2000));
    }
    @Test void editorPollingStaysWithinBudgetWhateverTheServerTickRate() {
        // A running program polls every 150 ms and the library refreshes every 5 s; ticks no longer matter.
        var rate = new RequestRate(0);
        for (long now = 0; now < 60_000; now += 150) assertTrue(rate.allow(now), "Poll at " + now + " ms rejected");
    }
    @Test void clockGoingBackwardsStartsANewWindow() {
        var rate = new RequestRate(5000);
        for (int i = 0; i < RequestRate.LIMIT; i++) rate.allow(5000);
        assertTrue(rate.allow(4000));
    }
}
