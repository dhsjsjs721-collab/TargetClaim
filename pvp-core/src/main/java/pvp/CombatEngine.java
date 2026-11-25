package pvp;
/** Offline combat model. No Minecraft input, network or rendering hooks. */
public final class CombatEngine {
public enum Mode { CRITICAL, COMBO, COUNTER_CRITICAL }
public enum Decision { WAIT, CRITICAL_WINDOW, COMBO_WINDOW }
public record Frame(double cooldown, boolean grounded, double fallDistance, boolean sprinting, boolean restricted, boolean inRange, boolean crosshairOnTarget, boolean opponentFalling, boolean opponentRecentlyAttacked, TargetFilter.Candidate target) {}
public static Decision evaluate(Mode mode, Frame f) {
if (!TargetFilter.accepts(f.target()) || !f.inRange() || !f.crosshairOnTarget() || f.restricted() || !Double.isFinite(f.cooldown()) || f.cooldown() < .95) return Decision.WAIT;
boolean critical = !f.grounded() && f.fallDistance() > 0 && !f.sprinting();
return switch(mode) {
case CRITICAL -> critical ? Decision.CRITICAL_WINDOW : Decision.WAIT;
case COMBO -> f.sprinting() ? Decision.COMBO_WINDOW : Decision.WAIT;
case COUNTER_CRITICAL -> critical && f.opponentRecentlyAttacked() ? Decision.CRITICAL_WINDOW : Decision.WAIT;
};
}
private CombatEngine() {}
}