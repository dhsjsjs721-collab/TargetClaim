package pvp;
/** Tracks callback pairing, not wall-clock game ticks. No retries after a fault. */
public final class TickWatchdog {
private boolean open;
public boolean start() {
boolean paired=!open;
open=true;
return paired;
}
public boolean end() {
if(!open) return false;
open=false; return true;
}
public void reset() { open=false; }
public static long elapsed(long start,long end) { return Math.max(0,end-start); }
}
