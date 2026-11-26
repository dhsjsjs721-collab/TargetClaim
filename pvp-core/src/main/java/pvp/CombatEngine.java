package pvp;
/** Offline combat model. No Minecraft input, network or rendering hooks. */
public final class CombatEngine {
public enum Mode { CRITICAL, COMBO, COUNTER_CRITICAL, NORMAL }
public enum Decision { WAIT, CRITICAL_WINDOW, COMBO_WINDOW, NORMAL_WINDOW }
public record Frame(double cooldown, boolean grounded, double fallDistance, boolean sprinting, boolean restricted, boolean inRange, boolean crosshairOnTarget, boolean opponentFalling, boolean opponentRecentlyAttacked, TargetFilter.Candidate target) {}
private static double minimumStrength(Mode mode) {
return mode==Mode.CRITICAL || mode==Mode.COUNTER_CRITICAL ? .95 : 1.0;
}
public static String reason(Mode mode, Frame f) {
if (!TargetFilter.accepts(f.target())) return "Target rejected";
if (!f.inRange() || !f.crosshairOnTarget()) return "Out of reach";
if (f.restricted()) return "Player restricted";
if (!Double.isFinite(f.cooldown()) || f.cooldown()<minimumStrength(mode)) return "Cooldown";
if (mode==Mode.COMBO && !f.sprinting()) return "Waiting for sprint";
if (mode==Mode.CRITICAL || mode==Mode.COUNTER_CRITICAL) {
if (f.grounded() || !(f.fallDistance()>0)) return "Waiting for descent";
if (f.sprinting()) return "Waiting for sprint stop";
if (mode==Mode.COUNTER_CRITICAL && !f.opponentFalling() && !f.opponentRecentlyAttacked()) return "Waiting for counter";
}
return "Attack ready";
}
public static Decision evaluate(Mode mode, Frame f) {
if (!TargetFilter.accepts(f.target()) || !f.inRange() || !f.crosshairOnTarget() || f.restricted() || !Double.isFinite(f.cooldown()) || f.cooldown() < minimumStrength(mode)) return Decision.WAIT;
boolean critical = !f.grounded() && f.fallDistance() > 0 && !f.sprinting();
return switch(mode) {
case CRITICAL -> critical ? Decision.CRITICAL_WINDOW : Decision.WAIT;
case COMBO -> f.sprinting() ? Decision.COMBO_WINDOW : Decision.WAIT;
case COUNTER_CRITICAL -> critical && (f.opponentFalling() || f.opponentRecentlyAttacked()) ? Decision.CRITICAL_WINDOW : Decision.WAIT;
case NORMAL -> critical ? Decision.CRITICAL_WINDOW : f.sprinting() ? Decision.COMBO_WINDOW : Decision.NORMAL_WINDOW;
};
}
public static Decision autoCritical(Frame frame,int descendingTicks,boolean hitThisFall) {
if(hitThisFall || descendingTicks < ((frame.opponentFalling() || frame.opponentRecentlyAttacked())?1:2)) return Decision.WAIT;
return evaluate(Mode.CRITICAL,frame);
}
public enum Action { WAIT_TARGET, WAIT_COOLDOWN, WAIT_LANDING, WAIT_DESCENT, WAIT_JUMP, WAIT_SPRINT, STOP_SPRINT, JUMP, CRITICAL, COMBO, NORMAL }
public static Action plan(Frame f,int descendingTicks,boolean hitThisFall,boolean canJump,boolean jumpPending,boolean attackReady,boolean jumpReady) {
return plan(f,descendingTicks,hitThisFall,canJump,jumpPending,attackReady,jumpReady,false);
}
public static Action plan(Frame f,int descendingTicks,boolean hitThisFall,boolean canJump,boolean jumpPending,boolean attackReady,boolean jumpReady,boolean urgent) {
if(!TargetFilter.accepts(f.target()) || !f.inRange()) return Action.WAIT_TARGET;
if(hitThisFall && !f.grounded()) return Action.WAIT_LANDING;
if(!attackReady || !Double.isFinite(f.cooldown())) return Action.WAIT_COOLDOWN;
if(f.grounded()) {
if(urgent && f.crosshairOnTarget() && f.cooldown()>=.90 && !f.restricted()) return Action.NORMAL;
if(jumpPending) return Action.WAIT_JUMP;
if(canJump && f.cooldown()>=.80 && jumpReady) return Action.JUMP;
if(!canJump && f.crosshairOnTarget() && f.cooldown()>=.95 && !f.restricted()) return Action.NORMAL;
return Action.WAIT_COOLDOWN;
}
if(f.restricted() || !f.crosshairOnTarget()) return Action.WAIT_TARGET;
if(f.sprinting()) return Action.STOP_SPRINT;
if(f.cooldown()<.91) return Action.WAIT_COOLDOWN;
if(f.fallDistance()>0 && descendingTicks>=1) return Action.CRITICAL;
return Action.WAIT_DESCENT;
}
public static double predictedCharge(double charge,double weaponDelay,int ticks) {
if(!Double.isFinite(charge) || !Double.isFinite(weaponDelay) || weaponDelay<=0) return 0;
return Math.max(0,Math.min(1,charge+Math.max(0,ticks)/weaponDelay));
}
public static Action adaptivePlan(Frame f,int descendingTicks,boolean hitThisFall,boolean canJump,boolean jumpPending,boolean attackReady,boolean jumpReady,boolean urgent,double weaponDelay,int readyWait) {
return adaptivePlan(f,descendingTicks,hitThisFall,canJump,jumpPending,attackReady,jumpReady,urgent,weaponDelay,readyWait,5);
}
public static Action adaptivePlan(Frame f,int descendingTicks,boolean hitThisFall,boolean canJump,boolean jumpPending,boolean attackReady,boolean jumpReady,boolean urgent,double weaponDelay,int readyWait,int jumpHorizon) {
if(!TargetFilter.accepts(f.target()) || !f.inRange()) return Action.WAIT_TARGET;
if(!Double.isFinite(f.cooldown())) return Action.WAIT_COOLDOWN;
boolean charged=attackReady && f.crosshairOnTarget() && f.cooldown()>.90;
if(hitThisFall && !f.grounded()) return Action.WAIT_LANDING;
if(f.grounded()) {
if(charged && (urgent || !canJump || readyWait>=4)) return Action.NORMAL;
if(jumpPending) return Action.WAIT_JUMP;
if(canJump && !urgent && jumpReady && f.cooldown()>=.40 && predictedCharge(f.cooldown(),weaponDelay,Math.max(3,Math.min(14,jumpHorizon)))>=.94) return Action.JUMP;
return Action.WAIT_COOLDOWN;
}
if(!f.crosshairOnTarget()) return Action.WAIT_TARGET;
if(charged && !f.restricted() && !f.sprinting() && f.fallDistance()>0 && descendingTicks>=1) return Action.CRITICAL;
if(charged && (urgent || f.restricted() || readyWait>=5)) return Action.NORMAL;
if(!f.restricted() && f.sprinting()) return Action.STOP_SPRINT;
return charged?Action.WAIT_DESCENT:Action.WAIT_COOLDOWN;
}
/** Distance hysteresis: a critical jump requires a comfortable reach margin. */
public static boolean preferCombo(boolean previous,double now,double predicted,double reach,boolean underPressure) {
if(!Double.isFinite(now) || !Double.isFinite(predicted) || !Double.isFinite(reach) || reach<=0) return false;
double distance=Math.max(now,predicted);
double enter=reach*(underPressure?.68:.78);
double exit=reach*.62;
return distance>(previous?exit:enter);
}
public static Action hybridPlan(Frame f,int descendingTicks,boolean hitThisFall,boolean canJump,boolean jumpPending,boolean attackReady,boolean jumpReady,boolean urgent,double weaponDelay,int readyWait,int jumpHorizon,boolean combo,boolean canSprint) {
if(!combo) return adaptivePlan(f,descendingTicks,hitThisFall,canJump,jumpPending,attackReady,jumpReady,urgent,weaponDelay,readyWait,jumpHorizon);
if(!TargetFilter.accepts(f.target()) || !f.inRange() || !f.crosshairOnTarget()) return Action.WAIT_TARGET;
if(hitThisFall && !f.grounded()) return Action.WAIT_LANDING;
if(!attackReady || !Double.isFinite(f.cooldown()) || f.cooldown()<.95) return Action.WAIT_COOLDOWN;
if(f.grounded()) {
if(f.restricted()) return Action.NORMAL;
if(f.sprinting()) return Action.COMBO;
return canSprint && readyWait<3?Action.WAIT_SPRINT:Action.NORMAL;
}
if(!f.restricted() && !f.sprinting() && f.fallDistance()>0 && descendingTicks>=1) return Action.CRITICAL;
return urgent || readyWait>=4 || f.restricted()?Action.NORMAL:Action.WAIT_DESCENT;
}
/** Recovery is inferred from observed incoming damage, motion and landing. */
public static final class Recovery {
private long lastHit=-100,lastUpdate=Long.MIN_VALUE;
private int stable;
private boolean recovering;
public void reset() { lastHit=-100; lastUpdate=Long.MIN_VALUE; stable=0; recovering=false; }
public boolean hit(long tick) {
if(tick-lastHit<2) return false;
lastHit=tick; stable=0; recovering=true;
return true;
}
public boolean pressure(long tick) { return tick>=lastHit && tick-lastHit<=12; }
public boolean update(long tick,boolean grounded,double awaySpeed) {
if(!pressure(tick)) { recovering=false; stable=0; return false; }
if(lastUpdate==tick) return recovering;
lastUpdate=tick;
if(!grounded || (Double.isFinite(awaySpeed) && awaySpeed>.10)) { recovering=true; stable=0; }
else if(++stable>=2) recovering=false;
return recovering;
}
public boolean recovering() { return recovering; }
}
public static double awaySpeed(double dx,double dz,double vx,double vz) {
double length=Math.hypot(dx,dz);
return length>1e-6 && Double.isFinite(length) && Double.isFinite(vx) && Double.isFinite(vz)?-(dx*vx+dz*vz)/length:0;
}
/** Tracks an unfavorable exchange against the selected practice opponent. */
public static final class Exchange {
private long firstHit=-100,lastHit=-100,retreatStart=-100,reengageUntil=-100;
private int unanswered;
private boolean retreat;
public void reset() { firstHit=lastHit=retreatStart=reengageUntil=-100; unanswered=0; retreat=false; }
public void incoming(long tick) {
if(tick-lastHit<2) return;
if(tick-firstHit>24) { firstHit=tick; unanswered=0; }
lastHit=tick; unanswered++;
}
public void outgoing(long tick) { unanswered=0; firstHit=-100; }
public boolean update(long tick,double distance,double reach,boolean grounded) {
if(!Double.isFinite(distance) || !Double.isFinite(reach) || reach<=0) return retreat;
if(retreat) {
long elapsed=tick-retreatStart;
if(elapsed>=6 && grounded && distance>=reach+.55 && tick-lastHit>=5 || elapsed>=18) {
retreat=false; unanswered=0; firstHit=-100; reengageUntil=tick+8;
}
} else if(tick>=reengageUntil && unanswered>=2 && tick-lastHit<=12 && distance>=reach*.72) {
retreat=true; retreatStart=tick;
}
return retreat;
}
public boolean retreating() { return retreat; }
public boolean reengaging(long tick) { return tick<reengageUntil; }
}
public static Action practiceAction(Frame f,int descendingTicks,boolean hitThisFall,boolean canJump,boolean jumpPending,boolean attackReady,boolean jumpReady,boolean urgent,double weaponDelay,int readyWait,int jumpHorizon,boolean combo,boolean canSprint,boolean retreating) {
if(!TargetFilter.accepts(f.target()) || !f.inRange() || !f.crosshairOnTarget()) return Action.WAIT_TARGET;
boolean ready=attackReady && Double.isFinite(f.cooldown()) && f.cooldown()>=.95;
if(hitThisFall && !f.grounded()) return Action.WAIT_LANDING;
if(ready && !f.restricted() && !f.grounded() && !f.sprinting() && f.fallDistance()>0 && descendingTicks>=1) return Action.CRITICAL;
if(retreating || readyWait>=3 && ready) {
if(!ready) return Action.WAIT_COOLDOWN;
return f.sprinting() && f.grounded() && !f.restricted()?Action.COMBO:Action.NORMAL;
}
return hybridPlan(f,descendingTicks,hitThisFall,canJump,jumpPending,attackReady,jumpReady,urgent,weaponDelay,readyWait,jumpHorizon,combo,canSprint);
}
private CombatEngine() {}
}