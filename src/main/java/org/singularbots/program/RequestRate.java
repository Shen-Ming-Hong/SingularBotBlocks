package org.singularbots.program;

/** Per-player request budget over wall-clock time, so a lagging server does not shrink the allowance and reject a normal editor. */
public final class RequestRate {
    public static final int LIMIT=20;
    public static final long WINDOW_MILLIS=1000;
    private long start;
    private int count;
    public RequestRate(long now){start=now;}
    public boolean allow(long now){if(now-start>=WINDOW_MILLIS||now<start){start=now;count=0;}return ++count<=LIMIT;}
}
