package pvp;
/** Pure client-observation helpers. A local observation is not server acknowledgement. */
public final class SupportObservation {
public static final int HEAL=1,SPEED=2,STRENGTH=4;
public record Effect(int amplifier,int duration) {
public static Effect absent() { return new Effect(-1,0); }
}
public record Snapshot(double health,Effect speed,Effect strength) {}
public enum Result { PENDING, OBSERVED, PARTIAL, NOT_OBSERVED, INTERRUPTED }
private Snapshot before;
private int wanted,seen,remaining;
public void begin(Snapshot snapshot,int mask,int ticks) {
if(snapshot==null || mask<=0 || (mask&~7)!=0 || ticks<=0) throw new IllegalArgumentException("Invalid observation");
before=snapshot; wanted=mask; seen=0; remaining=ticks;
}
public boolean pending() { return before!=null; }
public void reset() { before=null; wanted=seen=remaining=0; }
public Result interrupt() { if(!pending()) return Result.NOT_OBSERVED; reset(); return Result.INTERRUPTED; }
private static boolean improved(Effect before,Effect after) {
if(after.amplifier()<0 || after.duration()==0) return false;
if(before.amplifier()<0) return true;
if(after.amplifier()>before.amplifier()) return true;
return after.amplifier()==before.amplifier() && before.duration()>=0 && (after.duration()<0 || after.duration()>before.duration());
}
public Result observe(Snapshot now) {
if(!pending()) return Result.NOT_OBSERVED;
if((wanted&HEAL)!=0 && Double.isFinite(now.health()) && now.health()>before.health()+.01) seen|=HEAL;
if((wanted&SPEED)!=0 && improved(before.speed(),now.speed())) seen|=SPEED;
if((wanted&STRENGTH)!=0 && improved(before.strength(),now.strength())) seen|=STRENGTH;
if(seen==wanted) { reset(); return Result.OBSERVED; }
if(--remaining>0) return Result.PENDING;
Result result=seen!=0?Result.PARTIAL:Result.NOT_OBSERVED;
reset(); return result;
}
private static boolean same(boolean components,int actual,int expected) { return components && actual==expected; }
public static boolean swapObserved(boolean sourceMatches,int sourceCount,int expectedSourceCount,boolean destMatches,int destCount,int expectedDestCount) {
return same(sourceMatches,sourceCount,expectedSourceCount) && same(destMatches,destCount,expectedDestCount);
}
}
