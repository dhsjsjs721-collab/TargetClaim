package dev.unified.optimizer;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class UnifiedOptimizer extends JavaPlugin implements Listener {
private static final class WorldState {
final UUID id;
final int originalView;
final int originalSimulation;
int appliedView;
int appliedSimulation;
long lastAdjustment;
boolean conflict;
WorldState(World world) {
id = world.getUID();
originalView = world.getViewDistance();
originalSimulation = world.getSimulationDistance();
appliedView = originalView;
appliedSimulation = originalSimulation;
}
}

private final Map<UUID, WorldState> worlds = new LinkedHashMap<>();
private final double[] recentMspt = new double[12];
private int recentCount, recentIndex;
private double smoothedMspt = Double.NaN;
private double lastMspt = Double.NaN;
private double lastTps = Double.NaN;
private int badSamples, goodSamples;
private long checks, decreases, increases;
private int task = -1;
private boolean paused, active, requirePlayers, announce;
private int sampleTicks, minView, minSimulation, maxView, maxSimulation;
private int requiredBad, requiredGood;
private long cooldownMs;
private double overloadMspt, recoveryMspt, overloadTps, recoveryTps;
private Set<String> excluded = Set.of();

@Override public void onEnable() {
saveDefaultConfig();
Bukkit.getPluginManager().registerEvents(this, this);
configure();
getLogger().info("No entities, items, chunks or redstone mechanisms are deleted by this optimizer.");
}

@Override public void onDisable() {
if(task != -1) Bukkit.getScheduler().cancelTask(task);
restoreAll();
worlds.clear();
}

private void configure() {
if(task != -1) {
Bukkit.getScheduler().cancelTask(task);
task = -1;
}
restoreAll();
worlds.clear();
reloadConfig();
FileConfiguration c = getConfig();
active = c.getBoolean("adaptive.enabled", true);
requirePlayers = c.getBoolean("adaptive.require-players-in-world", true);
announce = c.getBoolean("diagnostics.announce-distance-changes-in-console", true);
sampleTicks = limit(c.getInt("adaptive.sample-period-ticks", 200), 100, 1200);
minView = limit(c.getInt("adaptive.min-view-distance", 5), 2, 32);
minSimulation = limit(c.getInt("adaptive.min-simulation-distance", 4), 2, 32);
maxView = Math.max(0, c.getInt("adaptive.max-view-distance", 0));
maxSimulation = Math.max(0, c.getInt("adaptive.max-simulation-distance", 0));
requiredBad = limit(c.getInt("adaptive.consecutive-overload-samples", 3), 1, 30);
requiredGood = limit(c.getInt("adaptive.consecutive-recovery-samples", 6), 1, 60);
cooldownMs = limit(c.getInt("adaptive.adjustment-cooldown-seconds", 45), 10, 600) * 1000L;
overloadMspt = limitDouble(c.getDouble("adaptive.mspt-overload-threshold", 46), 10, 100);
recoveryMspt = limitDouble(c.getDouble("adaptive.mspt-recovery-threshold", 37), 5, overloadMspt - 1);
overloadTps = limitDouble(c.getDouble("adaptive.tps-overload-threshold", 19.2), 1, 20);
recoveryTps = limitDouble(c.getDouble("adaptive.tps-recovery-threshold", 19.8), overloadTps, 20);
Set<String> blocked = new HashSet<>();
for(String name : c.getStringList("adaptive.excluded-worlds")) blocked.add(name.toLowerCase(Locale.ROOT));
excluded = blocked;
badSamples = 0;
goodSamples = 0;
smoothedMspt = Double.NaN;
lastMspt = Double.NaN;
lastTps = Double.NaN;
recentCount = 0;
recentIndex = 0;
paused = false;
for(World world : Bukkit.getWorlds()) register(world);
task = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::sample, sampleTicks, sampleTicks);
}

private static int limit(int value, int min, int max) {
return Math.max(min, Math.min(max, value));
}

private static double limitDouble(double value, double min, double max) {
if(!Double.isFinite(value)) return min;
return Math.max(min, Math.min(max, value));
}

private void register(World world) {
worlds.putIfAbsent(world.getUID(), new WorldState(world));
}

@EventHandler public void worldLoad(WorldLoadEvent event) {
register(event.getWorld());
}

@EventHandler(ignoreCancelled = true) public void worldUnload(WorldUnloadEvent event) {
WorldState state = worlds.remove(event.getWorld().getUID());
if(state != null) restore(event.getWorld(), state);
}

private void restoreAll() {
for(WorldState state : worlds.values()) {
World world = Bukkit.getWorld(state.id);
if(world != null) restore(world, state);
}
}

private void restore(World world, WorldState state) {
try {
if(world.getViewDistance() == state.appliedView && state.appliedView != state.originalView)
world.setViewDistance(state.originalView);
if(world.getSimulationDistance() == state.appliedSimulation && state.appliedSimulation != state.originalSimulation)
world.setSimulationDistance(state.originalSimulation);
state.appliedView = world.getViewDistance();
state.appliedSimulation = world.getSimulationDistance();
} catch(RuntimeException ex) {
getLogger().warning("Could not restore distances in " + world.getName() + ": " + ex.getMessage());
}
}

private void sample() {
try {
checks++;
lastMspt = Bukkit.getAverageTickTime();
double[] tpsReadings = Bukkit.getTPS();
lastTps = tpsReadings.length > 0 ? tpsReadings[0] : 20.0;
if(!Double.isFinite(lastMspt) || lastMspt < 0 || !Double.isFinite(lastTps)) return;
recentMspt[recentIndex] = lastMspt;
recentIndex = (recentIndex + 1) % recentMspt.length;
if(recentCount < recentMspt.length) recentCount++;
smoothedMspt = Double.isNaN(smoothedMspt) ? lastMspt : 0.25 * lastMspt + 0.75 * smoothedMspt;

if(!active || paused) return;
boolean overloaded = smoothedMspt >= overloadMspt || lastTps < overloadTps;
boolean recovered = smoothedMspt <= recoveryMspt && lastTps >= recoveryTps;
if(overloaded) {
badSamples++;
goodSamples = 0;
} else if(recovered) {
goodSamples++;
badSamples = 0;
} else {
badSamples = 0;
goodSamples = 0;
}
int direction = badSamples >= requiredBad ? -1 : (goodSamples >= requiredGood ? 1 : 0);
if(direction == 0) return;
long now = System.currentTimeMillis();
for(WorldState state : worlds.values()) {
World world = Bukkit.getWorld(state.id);
if(world == null || excluded.contains(world.getName().toLowerCase(Locale.ROOT))) continue;
if(requirePlayers && world.getPlayerCount() == 0) continue;
if(state.conflict || now - state.lastAdjustment < cooldownMs) continue;
if(world.getViewDistance() != state.appliedView || world.getSimulationDistance() != state.appliedSimulation) {
state.conflict = true;
getLogger().warning("External view/simulation-distance change in " + world.getName() + "; handing control to other plugin (reload /uopt to rebind).");
continue;
}
adjust(world, state, direction, now);
}
} catch(Exception e) {
getLogger().warning("Optimizer sample failed safely: " + e.getMessage());
}
}

private void adjust(World world, WorldState state, int direction, long now) {
int currentView = state.appliedView, currentSimulation = state.appliedSimulation;
int capView = maxView == 0 ? state.originalView : Math.min(state.originalView, maxView);
int capSim = maxSimulation == 0 ? state.originalSimulation : Math.min(state.originalSimulation, maxSimulation);
int lowerView = Math.min(Math.min(state.originalView, capView), minView);
int lowerSim = Math.min(Math.min(state.originalSimulation, capSim), minSimulation);
int targetView = direction < 0 ? Math.max(lowerView, currentView - 1) : Math.min(capView, currentView + 1);
int targetSim = direction < 0 ? Math.max(lowerSim, currentSimulation - 1) : Math.min(capSim, currentSimulation + 1);
targetSim = Math.min(targetSim, targetView);
if(targetView == currentView && targetSim == currentSimulation) return;

try {
if(targetView != currentView) {
world.setViewDistance(targetView);
state.appliedView = world.getViewDistance();
}
if(targetSim != currentSimulation) {
world.setSimulationDistance(targetSim);
state.appliedSimulation = world.getSimulationDistance();
}
state.lastAdjustment = now;
if(direction < 0) decreases++;
else increases++;
if(announce)
getLogger().info("World " + world.getName() + " view " + currentView + "->" + state.appliedView + " sim " + currentSimulation + "->" + state.appliedSimulation + " (MSPT " + format(smoothedMspt) + ", TPS " + format(lastTps) + ")");
} catch(RuntimeException ex) {
state.conflict = true;
getLogger().warning("Distance adjustment rejected in " + world.getName() + "; no further changes: " + ex.getMessage());
}
}

private static String format(double d) {
return Double.isFinite(d) ? String.format(Locale.ROOT, "%.2f", d) : "n/a";
}

@Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
switch(sub) {
case "reload" -> {
configure();
sender.sendMessage("UnifiedOptimizer configuration reloaded; original world distances captured again.");
}
case "pause", "reset" -> {
paused = true;
restoreAll();
sender.sendMessage("UnifiedOptimizer paused. Original world distances restored when still owned by this plugin.");
}
case "resume" -> {
paused = false;
badSamples = 0;
goodSamples = 0;
sender.sendMessage("UnifiedOptimizer resumed.");
}
case "status" -> {
sender.sendMessage("UnifiedOptimizer 1.0.0 enabled=" + active + " paused=" + paused + " samples=" + checks + " MSPT=" + format(lastMspt) + " smoothed=" + format(smoothedMspt) + " TPS=" + format(lastTps));
sender.sendMessage("Changes: reduced=" + decreases + " restoredStep=" + increases + " cacheEntries=" + worlds.size() + " historySamples=" + recentCount);
for(WorldState state : worlds.values()) {
World world = Bukkit.getWorld(state.id);
if(world != null) {
sender.sendMessage(world.getName() + ": view " + world.getViewDistance() + "/" + state.originalView + ", simulation " + world.getSimulationDistance() + "/" + state.originalSimulation + ", players=" + world.getPlayerCount() + ", chunks=" + world.getChunkCount() + ", entities=" + world.getEntityCount() + (state.conflict ? " [EXTERNAL CONTROL]" : ""));
}
}
}
default -> sender.sendMessage("Usage: /uopt [status|reload|pause|resume|reset]");
}
return true;
}
}
