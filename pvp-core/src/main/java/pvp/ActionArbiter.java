package pvp;
/** Per-tick ownership of practice controls. Manual input always takes precedence. */
public final class ActionArbiter {
public enum Owner { IDLE, PAUSED, MANUAL, COMBAT, SUPPORT }
private Owner owner=Owner.IDLE;
private boolean finished=true;
public Owner begin(boolean enabled,boolean available,boolean manual) {
owner=!enabled?Owner.IDLE:!available?Owner.PAUSED:manual?Owner.MANUAL:Owner.COMBAT;
finished=false;
return owner;
}
public boolean supportEligible() { return !finished && owner==Owner.COMBAT; }
public void claimSupport() {
if(!supportEligible()) throw new IllegalStateException("Support not eligible");
owner=Owner.SUPPORT;
}
public boolean finish(boolean enabled,boolean available,boolean manual) {
if(finished) return false;
finished=true;
return enabled && available && !manual && owner==Owner.COMBAT;
}
public Owner owner() { return owner; }
public void reset() { owner=Owner.IDLE; finished=true; }
}
