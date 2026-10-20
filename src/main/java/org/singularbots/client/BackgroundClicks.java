package org.singularbots.client;

/**
 * A click sent to the game while it is in the background arrives twice: the tool brings the window forward for the
 * real press and release, and up to about 0.5 s later a second press and release come at the point the first one was
 * released. The window may already have lost focus again, or may still have it. After a drag that copy lands on the
 * block just dropped and opens its dropdown; on a dropdown it closes the menu the real click just opened.
 * <p>
 * The copy is recognised by where it follows from: a click that arrived while the window had no focus, or within a
 * moment of it gaining focus. A student working in the focused game window keeps every click, so quick repeated
 * clicks and double-clicks there still work.
 */
final class BackgroundClicks {
    /** How long after a release a press at the same point may be its copy. */
    static final long REPEAT_NANOS=1_000_000_000L;
    /** A press this soon after the window was last seen without focus came from a click sent in the background. */
    static final long FOCUS_NANOS=1_500_000_000L;
    private boolean seenInactive;
    private long inactiveAt;
    private boolean pressFromBackground,releaseFromBackground;
    private double releaseX=Double.NaN,releaseY;
    private long releaseAt;
    private int repeatButton=-1;

    /** Records whether the window has focus; called every frame so the moment it gains focus is known. */
    void focus(boolean active,long now){if(!active){seenInactive=true;inactiveAt=now;}}

    /** Nanoseconds since the window was last seen without focus, or -1 if never. */
    long sinceInactive(long now){return seenInactive?now-inactiveAt:-1;}

    /** True when this press is the copy of the last background click; it and its release are to be dropped. */
    boolean press(double x,double y,int button,boolean active,long now){
        focus(active,now);
        boolean copy=(!active||releaseFromBackground)&&now-releaseAt<REPEAT_NANOS
            &&Math.abs(x-releaseX)<1&&Math.abs(y-releaseY)<1;
        if(copy){repeatButton=button;releaseFromBackground=false;return true;}
        pressFromBackground=now-inactiveAt<FOCUS_NANOS&&seenInactive;
        return false;
    }

    /** True when this release belongs to a dropped press. */
    boolean release(double x,double y,int button,boolean active,long now){
        focus(active,now);
        if(repeatButton==button){repeatButton=-1;return true;}
        releaseX=x;releaseY=y;releaseAt=now;releaseFromBackground=pressFromBackground;
        return false;
    }
}
