package pvp;
public record CombatSettings(boolean aimEnabled, boolean triggerEnabled, double horizontalSpeed, double verticalSpeed, double fov) {
public CombatSettings {
if (!Double.isFinite(horizontalSpeed) || horizontalSpeed < 0 || horizontalSpeed > 1 || !Double.isFinite(verticalSpeed) || verticalSpeed < 0 || verticalSpeed > 1 || !Double.isFinite(fov) || fov <= 0 || fov > 180) throw new IllegalArgumentException("Invalid settings");
}
public static CombatSettings defaults() { return new CombatSettings(false,false,.35,.25,35); }
}