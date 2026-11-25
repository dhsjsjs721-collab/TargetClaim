package pvp;
public final class TargetFilter {
public record Candidate(boolean player, boolean self, boolean alive, boolean visible, boolean spectator, boolean armorStand) {}
public static boolean accepts(Candidate c) {
return c != null && c.player() && !c.self() && c.alive() && c.visible() && !c.spectator() && !c.armorStand();
}
private TargetFilter() {}
}