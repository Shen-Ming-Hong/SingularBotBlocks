package org.singularbots.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BackgroundClicksTest {
    private static final long MS=1_000_000L;

    @Test void dropsCopyOfBackgroundClickWhileWindowStillActive() {
        var clicks=new BackgroundClicks();
        clicks.focus(false,0);
        // The tool brings the window forward for the real click, and the copy comes before it loses focus again.
        assertFalse(clicks.press(103,161,0,true,100*MS));
        assertFalse(clicks.release(103,161,0,true,120*MS));
        assertTrue(clicks.press(103,161,0,true,505*MS));
        assertTrue(clicks.release(103,161,0,true,520*MS));
    }

    @Test void dropsCopyArrivingAfterWindowLostFocus() {
        var clicks=new BackgroundClicks();
        clicks.focus(false,0);
        assertFalse(clicks.press(30,71,0,true,50*MS));
        assertFalse(clicks.release(30,71,0,true,60*MS));
        assertTrue(clicks.press(30,71,0,false,400*MS));
        assertTrue(clicks.release(30,71,0,false,410*MS));
    }

    @Test void dropsCopyAtReleasePointAfterDrag() {
        var clicks=new BackgroundClicks();
        clicks.focus(false,0);
        assertFalse(clicks.press(64,74,0,true,30*MS));
        assertFalse(clicks.release(240,156,0,true,600*MS));
        assertTrue(clicks.press(240,156,0,true,900*MS));
        assertTrue(clicks.release(240,156,0,true,910*MS));
    }

    @Test void keepsDoubleClickInFocusedWindow() {
        var clicks=new BackgroundClicks();
        clicks.focus(false,0);
        for(long t=0;t<=5000;t+=16)clicks.focus(true,t*MS);
        assertFalse(clicks.press(50,50,0,true,5000*MS));
        assertFalse(clicks.release(50,50,0,true,5080*MS));
        assertFalse(clicks.press(50,50,0,true,5200*MS));
        assertFalse(clicks.release(50,50,0,true,5260*MS));
    }

    @Test void keepsClicksInWindowThatWasNeverInBackground() {
        var clicks=new BackgroundClicks();
        assertFalse(clicks.press(10,10,0,true,0));
        assertFalse(clicks.release(10,10,0,true,50*MS));
        assertFalse(clicks.press(10,10,0,true,200*MS));
    }

    @Test void keepsNextDeliberateClickElsewhereOrLater() {
        var clicks=new BackgroundClicks();
        clicks.focus(false,0);
        assertFalse(clicks.press(20,20,0,true,10*MS));
        assertFalse(clicks.release(20,20,0,true,20*MS));
        assertFalse(clicks.press(80,20,0,true,300*MS));
        assertFalse(clicks.release(80,20,0,true,310*MS));
        clicks.focus(false,2000*MS);
        assertFalse(clicks.press(80,20,0,true,4000*MS));
    }
}
