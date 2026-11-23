package dev.unified.antiesp;

import java.security.SecureRandom;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class UnifiedAntiESP extends JavaPlugin implements Listener {
private record Pair(UUID viewer, UUID target) {}
private static final class State {
long epoch;
boolean hidden;
long checked, occluded, visible;
Location from, to;
}
private final Map<Pair, State> states = new HashMap<>();
private final OreShufflePopulator populator = new OreShufflePopulator(this);
private final UndergroundEntityGuard entityGuard = new UndergroundEntityGuard(this);
private BukkitTask task;
private long passes, checks, rays, budgetStops, revealed, lastMicros;
private int pairCursor;
private long occlusionEpoch;
private boolean active;
private int interval, maxPairs, maxMicros;
private double nearSq, farSq;
private long grace, hideDelay, stale, cache;
private boolean antiSeed;
@Override public void onEnable() {
saveDefaultConfig();
if (getConfig().getString("ore-shuffle.secret", "").isBlank()) {
getConfig().set("ore-shuffle.secret", Long.toUnsignedString(new SecureRandom().nextLong(),16));
saveConfig();
}
Bukkit.getPluginManager().registerEvents(this,this);
Bukkit.getPluginManager().registerEvents(entityGuard,this);
for (World w : Bukkit.getWorlds()) register(w);
configure();
getLogger().info("Paper Anti-Xray is separate: use config/paper-world-defaults.yml. Cave/water/lava visibility cannot be fully blocked by this entity plugin.");
for(String line:PaperXrayAudit.inspect())getLogger().info(line);
}
@Override public void onDisable() {
if(task!=null) task.cancel();
entityGuard.stop();
for(World w:Bukkit.getWorlds()) w.getPopulators().remove(populator);
clearAll();
}
private void configure() {
if(task!=null) task.cancel();
clearAll();
reloadConfig();
active=getConfig().getBoolean("player-esp.enabled",true);
antiSeed=getConfig().getBoolean("seed-protection.enabled",true);
interval=Math.max(1,Math.min(20,getConfig().getInt("player-esp.interval-ticks",2)));
maxPairs=Math.max(8,Math.min(10000,getConfig().getInt("player-esp.max-pair-checks-per-pass",120)));
maxMicros=Math.max(200,Math.min(20000,getConfig().getInt("player-esp.max-scan-microseconds",2200)));
double near=0.0; // Enforce occlusion even for entities a block behind a wall
double far=getConfig().getDouble("player-esp.max-protection-range-blocks",224);
if(!getConfig().isSet("config-version") && (far==72 || far==160)){
far=224;
getLogger().info("Legacy Player ESP range upgraded in memory to 224 blocks. Set config-version: 34 to override.");
}
far=Math.max(32,Math.min(256,far));
nearSq=near*near; farSq=far*far;
grace=getConfig().getLong("player-esp.visible-grace-millis",450);
hideDelay=getConfig().getLong("player-esp.occluded-before-hide-millis",220);
stale=getConfig().getLong("player-esp.max-hidden-staleness-millis",900);
cache=getConfig().getLong("player-esp.stationary-cache-millis",150);
for(World w:Bukkit.getWorlds()) register(w);
if(active) task=Bukkit.getScheduler().runTaskTimer(this,this::scan,2L,interval);
entityGuard.configure();
}
private void register(World world) {
boolean enabled=getConfig().getBoolean("ore-shuffle.enabled",false);
if(getConfig().getBoolean("chunk-safety.disable-ore-worldgen",true))enabled=false;
boolean nether=getConfig().getBoolean("ore-shuffle.include-nether",true);
if(enabled && (world.getEnvironment()==World.Environment.NORMAL || (nether && world.getEnvironment()==World.Environment.NETHER))) {
if(!world.getPopulators().contains(populator)) world.getPopulators().add(populator);
} else world.getPopulators().remove(populator);
}
@EventHandler public void worldLoad(WorldLoadEvent e) {register(e.getWorld());}
private boolean allowed(Player a,Player b) {
if(a==b || a.getWorld()!=b.getWorld()) return false;
if(a.hasPermission("unifiedantiesp.bypass")||b.hasPermission("unifiedantiesp.bypass")) return false;
if(a.getGameMode()==GameMode.SPECTATOR || b.getGameMode()==GameMode.SPECTATOR) return false;
double d=a.getLocation().distanceSquared(b.getLocation());
return VisibilityRangePolicy.within(d,farSq);
}
private void show(Player a,Player b,State state) {
if(state.hidden) { a.showPlayer(this,b); state.hidden=false; revealed++; }
state.occluded=0;
}
private void clearAll() {
for(var e:states.entrySet()) {
Player a=Bukkit.getPlayer(e.getKey().viewer());
Player b=Bukkit.getPlayer(e.getKey().target());
if(a!=null && b!=null && e.getValue().hidden) a.showPlayer(this,b);
}
states.clear(); pairCursor=0;
}
private boolean trace(Player a,Player b) {
Location eye=a.getEyeLocation();
Location feet=b.getLocation();
double[] yy={0.15,0.9,1.6};
double[] xx={-0.24,0.24};
double[] zz={-0.24,0.24};
for(double y:yy) {
for(int n=0;n<2;n++) {
Location target=feet.clone().add(xx[n],y,zz[n]);
Vector delta=target.toVector().subtract(eye.toVector());
double length=delta.length();
if(length<0.01) return true;
rays++;
if(a.getWorld().rayTraceBlocks(eye,delta,length,FluidCollisionMode.NEVER,true)==null) return true;
}
}
return false;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void trackPlayer(PlayerTrackEntityEvent event){
// Cancel a hidden player *before* their spawn packet can reach an ESP client.
// Paper retries tracking when the player remains in range; the normal scan
// handles movement between wall-visible and wall-hidden states.
if(!active || !(event.getEntity() instanceof Player target))return;
Player viewer=event.getPlayer();
if(!allowed(viewer,target))return;
try{
if(!trace(viewer,target))event.setCancelled(true);
}catch(RuntimeException ignored){
// On server error, fail open rather than prevent legitimate player joins.
}
}
private void check(Player a,Player b,State s,long now) {
checks++;
if(!allowed(a,b)) {show(a,b,s); s.checked=now; return;}
Location from=a.getLocation(),to=b.getLocation();
if(!s.hidden && s.from!=null && s.to!=null && now-s.checked<cache &&
from.distanceSquared(s.from)<0.0324 && to.distanceSquared(s.to)<0.0324 && s.epoch==occlusionEpoch) return;
s.checked=now;
s.epoch=occlusionEpoch;
s.from=from; s.to=to;
boolean visible=trace(a,b);
if(visible) {
s.visible=now;
show(a,b,s);
} else {
if(s.occluded==0) s.occluded=now;
if(!s.hidden && now-s.occluded>=hideDelay && now-s.visible>=grace) {
a.hidePlayer(this,b); s.hidden=true;
}
}
}
private void scan() {
long start=System.nanoTime(),now=System.currentTimeMillis();
try {
ArrayList<Player> players=new ArrayList<>(Bukkit.getOnlinePlayers());
int n=players.size();
if(n<2) {clearAll(); return;}
int used=0;
for(var iterator=states.entrySet().iterator();iterator.hasNext();) {
var e=iterator.next();
Player a=Bukkit.getPlayer(e.getKey().viewer()),b=Bukkit.getPlayer(e.getKey().target());
if(a==null||b==null) {iterator.remove();continue;}
State s=e.getValue();
if(s.hidden && now-s.checked>stale) show(a,b,s);
if(s.hidden && used<maxPairs/3 && System.nanoTime()-start<maxMicros*1000L) {check(a,b,s,now);used++;}
}
int total=n*(n-1),visited=0;
while(visited<total && used<maxPairs && System.nanoTime()-start<maxMicros*1000L) {
int index=Math.floorMod(pairCursor++,total);visited++;
int i=index/(n-1),j=index%(n-1);if(j>=i)j++;
Player a=players.get(i),b=players.get(j);
Pair pair=new Pair(a.getUniqueId(),b.getUniqueId());
State s=states.get(pair);
if(s!=null && s.hidden)continue;
if(s==null) {if(!allowed(a,b))continue;s=new State();s.visible=0;states.put(pair,s);}
check(a,b,s,now);used++;
}
if(visited<total)budgetStops++;
if(pairCursor>1_000_000_000) pairCursor%=total;
if(passes++%25==0) states.entrySet().removeIf(e->!e.getValue().hidden && now-e.getValue().checked>3000);
} catch(Exception ex) {
getLogger().warning("ESP scan error: "+ex.getMessage());clearAll();
} finally {lastMicros=(System.nanoTime()-start)/1000;}
}
private void invalidate() {occlusionEpoch++;} // O(1), avoids iterating all players per block break
@EventHandler public void blockBreak(BlockBreakEvent e){invalidate();}
@EventHandler public void blockPlace(BlockPlaceEvent e){invalidate();}
@EventHandler public void explode(EntityExplodeEvent e){invalidate();}
@EventHandler public void blockExplode(BlockExplodeEvent e){invalidate();}
@EventHandler public void pistonExtend(BlockPistonExtendEvent e){invalidate();}
@EventHandler public void pistonRetract(BlockPistonRetractEvent e){invalidate();}
@EventHandler public void quit(PlayerQuitEvent e){clearAll();}
@EventHandler public void teleport(PlayerTeleportEvent e){clearAll();}
@EventHandler public void changeWorld(PlayerChangedWorldEvent e){clearAll();}
@EventHandler public void respawn(PlayerRespawnEvent e){clearAll();}
@EventHandler public void command(PlayerCommandPreprocessEvent e){
if(!antiSeed||e.getPlayer().hasPermission("unifiedantiesp.seed"))return;
String s=e.getMessage().trim().toLowerCase(Locale.ROOT);
String first=s.split("\\s+")[0];
if(first.equals("/seed")||first.endsWith(":seed")){e.setCancelled(true);e.getPlayer().sendMessage("Seed is protected.");}
}
@EventHandler public void sendCommands(PlayerCommandSendEvent e){
if(antiSeed && getConfig().getBoolean("seed-protection.hide-command-from-tab",true)&&!e.getPlayer().hasPermission("unifiedantiesp.seed")) {
e.getCommands().removeIf(s->s.equalsIgnoreCase("seed")||s.toLowerCase(Locale.ROOT).endsWith(":seed"));
}
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
if(args.length>0 && args[0].equalsIgnoreCase("reload")) {configure();sender.sendMessage("UnifiedAntiESP reloaded; restart required for Paper Anti-Xray settings.");return true;}
if(args.length>0 && args[0].equalsIgnoreCase("audit")){for(String line:PaperXrayAudit.inspect())sender.sendMessage(line);return true;}
sender.sendMessage("UnifiedAntiESP 3.5: chunk mutations="+(getConfig().getBoolean("ore-shuffle.enabled",false)&&!getConfig().getBoolean("chunk-safety.disable-ore-worldgen",true)?"ON":"OFF"));
sender.sendMessage("UnifiedAntiESP: "+(active?"ON":"OFF")+" passes="+passes+" checks="+checks+" rays="+rays+" cachedPairs="+states.size()+" budgetStops="+budgetStops+" staleReveals="+revealed+" lastScanMicros="+lastMicros+" oreChunks="+populator.chunks()+" oreMoves="+populator.moved()+" | "+entityGuard.stats());
return true;
}
long oreSalt(){try{return Long.parseUnsignedLong(getConfig().getString("ore-shuffle.secret","1"),16);}catch(Exception e){return 1L;}}
int oreMoves(){return Math.max(1,Math.min(128,getConfig().getInt("ore-shuffle.max-moves-per-chunk",24)));}
int oreReads(){return Math.max(256,Math.min(50000,getConfig().getInt("ore-shuffle.max-block-reads-per-chunk",18000)));}
int oreOffset(){return Math.max(1,Math.min(4,getConfig().getInt("ore-shuffle.max-offset-blocks",2)));}
}
