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
private CombatEngine() {}
}