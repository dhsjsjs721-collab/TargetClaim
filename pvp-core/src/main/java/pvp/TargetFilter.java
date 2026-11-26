package pvp;
public final class TargetFilter {
public record Candidate(boolean player, boolean self, boolean alive, boolean visible, boolean spectator, boolean armorStand) {}
public static boolean accepts(Candidate c) {
return c != null && c.player() && !c.self() && c.alive() && c.visible() && !c.spectator() && !c.armorStand();
}
public static boolean practiceTarget(boolean local,boolean golemEnabled,boolean golem,boolean selected) {
return local ? golemEnabled && golem : selected;
}
public static boolean lockedPracticeTarget(boolean local,boolean golemEnabled,boolean golem,boolean locked,boolean chosen) {
return locked ? chosen : practiceTarget(local,golemEnabled,golem,false);
}
/** Only the owned mannequin is eligible in local practice. */
public static boolean ghostPracticeTarget(boolean local,boolean ownedGhost,boolean locked,boolean chosen) {
return local ? ownedGhost && (!locked || chosen) : locked && chosen;
}
public static boolean nearer(double distanceSquared,double bestSquared,boolean first) {
return Double.isFinite(distanceSquared) && distanceSquared>=0 && (distanceSquared<bestSquared || first && distanceSquared==bestSquared);
}
private TargetFilter() {}
}