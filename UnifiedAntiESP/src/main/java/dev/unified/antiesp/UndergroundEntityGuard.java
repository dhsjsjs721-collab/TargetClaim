package dev.unified.antiesp;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Suppresses underground entity tracking before spawn packets are sent,
 * and periodically removes already tracked obscured entities for each viewer.
 * Only uses main-thread Bukkit APIs. Does not change entity AI or remove entities.
 */
final class UndergroundEntityGuard implements Listener {
private record Pair(UUID viewer, UUID target) {}
private static final class Decision {
long checked;
Vector viewer, target;
boolean hidden;
}
private final JavaPlugin plugin;
private final Map<UUID,Set<UUID>> hidden=new HashMap<>();
private final LinkedHashMap<Pair,Decision> trackDecisions=new LinkedHashMap<>();
private BukkitTask ticker;
private boolean enabled,mobs,items,decorations,vehicles,displays,experience,other,includeNether,strictDistant;
private int maxSkylight;
private double strictSq;
private long strictDecisions,oldConfigAdjustments;
private int interval,maxViewers,maxChecks,maxMicros,maxHidden,maxCached,rayCount;
private double minSq,maxSq;
private int maxY;
private long cacheMs,scans,rayTests,blockedSpawns,hides,shows,budgetStops,lastMicros,errors;
private int viewerCursor;

UndergroundEntityGuard(JavaPlugin plugin){this.plugin=plugin;}

void configure(){
if(ticker!=null){ticker.cancel();ticker=null;}
restoreAll();
trackDecisions.clear();
enabled=plugin.getConfig().getBoolean("entity-esp.enabled",true);
mobs=plugin.getConfig().getBoolean("entity-esp.hide-mobs",true);
items=plugin.getConfig().getBoolean("entity-esp.hide-dropped-items",true);
decorations=plugin.getConfig().getBoolean("entity-esp.hide-decorations",true);
vehicles=plugin.getConfig().getBoolean("entity-esp.hide-vehicles",true);
displays=plugin.getConfig().getBoolean("entity-esp.hide-displays",true);
experience=plugin.getConfig().getBoolean("entity-esp.hide-experience-orbs",true);
other=plugin.getConfig().getBoolean("entity-esp.hide-other-entities",false);
includeNether=plugin.getConfig().getBoolean("entity-esp.include-nether",true);
maxY=plugin.getConfig().getInt("entity-esp.hide-below-y",320);
double near=bound(plugin.getConfig().getDouble("entity-esp.never-hide-within-blocks",3),2,16);
double far=bound(plugin.getConfig().getDouble("entity-esp.max-protection-range-blocks",192),24,256);
// Older plugin configurations explicitly contain unsafe defaults (56/96).
// Upgrade only those exact shipped defaults, leaving custom settings alone.
// Operators can set config-version: 33 to retain their chosen old ranges.
if(!plugin.getConfig().isSet("config-version")){
if(far==56){far=192;oldConfigAdjustments++;}
if(maxY==96){maxY=320;oldConfigAdjustments++;}
}
strictDistant=plugin.getConfig().getBoolean("entity-esp.strict-distant-underground-mobs",true);
double strictDistance=bound(plugin.getConfig().getDouble("entity-esp.strict-distance-blocks",40),12,96);
maxSkylight=bound(plugin.getConfig().getInt("entity-esp.underground-max-skylight",3),0,15);
strictSq=strictDistance*strictDistance;
minSq=near*near;maxSq=far*far;
plugin.getLogger().info("EntityESP range="+(int)far+" blocks, strict-dark-mobs="+strictDistant+", strict distance="+(int)strictDistance+" blocks, height<"+maxY+(oldConfigAdjustments>0?" (legacy ranges upgraded in memory)":""));
interval=bound(plugin.getConfig().getInt("entity-esp.interval-ticks",5),2,40);
maxViewers=bound(plugin.getConfig().getInt("entity-esp.max-viewers-per-pass",3),1,16);
maxChecks=bound(plugin.getConfig().getInt("entity-esp.max-entity-checks-per-pass",180),8,800);
maxMicros=bound(plugin.getConfig().getInt("entity-esp.max-scan-microseconds",2400),200,20000);
maxHidden=bound(plugin.getConfig().getInt("entity-esp.max-hidden-per-viewer",384),16,1000);
maxCached=bound(plugin.getConfig().getInt("entity-esp.max-cached-track-pairs",4096),128,50000);
cacheMs=bound(plugin.getConfig().getLong("entity-esp.track-decision-cache-millis",250),0,2000);
rayCount=bound(plugin.getConfig().getInt("entity-esp.occlusion-rays",2),1,3);
viewerCursor=0;
if(enabled)ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::scan,5L,interval);
}

private static int bound(int v,int a,int b){return Math.max(a,Math.min(b,v));}
private static long bound(long v,long a,long b){return Math.max(a,Math.min(b,v));}
private static double bound(double v,double a,double b){return Double.isFinite(v)?Math.max(a,Math.min(b,v)):a;}

void stop(){
if(ticker!=null){ticker.cancel();ticker=null;}
restoreAll();
trackDecisions.clear();
}

private boolean selected(Entity target){
if(target instanceof Player)return false; // existing player-esp performs this separately
if(target instanceof Mob || target instanceof Animals || target instanceof WaterMob
|| target instanceof Ambient || target instanceof NPC)return mobs;
if(target instanceof Item)return items;
if(target instanceof ExperienceOrb)return experience;
if(target instanceof ArmorStand || target instanceof Hanging)return decorations;
if(target instanceof Display)return displays;
if(target instanceof Minecart || target instanceof Boat)return vehicles;
return other;
}

private boolean eligible(Player viewer,Entity target){
if(!enabled || viewer==target || target==null || !target.isValid())return false;
if(viewer.getWorld()!=target.getWorld())return false;
if(viewer.getGameMode()==GameMode.SPECTATOR || viewer.hasPermission("unifiedantiesp.bypass")
|| viewer.hasPermission("unifiedantiesp.entitybypass"))return false;
if(!includeNether && viewer.getWorld().getEnvironment()==org.bukkit.World.Environment.NETHER)return false;
if(target.getLocation().getY()>=maxY)return false;
if(!selected(target))return false;
if(!target.getPassengers().isEmpty())return false;
Location from=viewer.getEyeLocation();
Location to=target.getLocation();
double dist=from.distanceSquared(to);
return dist>=minSq && dist<=maxSq;
}

/** true if all tested target points are blocked. Conservative fail-open. */
private boolean strictUnderground(Entity target){
// Only conceal distant cave mobs by distance alone: do not vanish visible
// mobs standing in broad daylight on the surface. Nether has no skylight.
if(!strictDistant || !(target instanceof LivingEntity))return false;
if(target.getWorld().getEnvironment()==org.bukkit.World.Environment.NETHER)return true;
if(target.getWorld().getEnvironment()!=org.bukkit.World.Environment.NORMAL)return false;
return target.getLocation().getBlock().getLightFromSky()<=maxSkylight;
}
private boolean shouldHide(Player viewer,Entity target){
if(!eligible(viewer,target))return false;
double d=viewer.getEyeLocation().distanceSquared(target.getLocation());
if(d>=strictSq && strictUnderground(target)){strictDecisions++;return true;}
return cachedOccluded(viewer,target);
}
private boolean occluded(Player viewer,Entity target){
Location eye=viewer.getEyeLocation();
BoundingBox box=target.getBoundingBox();
Vector center=box.getCenter();
double bottom=box.getMinY()+Math.min(0.15,Math.max(0.02,(box.getHeight()/4)));
double top=box.getMaxY()-Math.min(0.15,Math.max(0.02,(box.getHeight()/4)));
Vector[] points=rayCount==1?new Vector[]{center}:
rayCount==2?new Vector[]{center,new Vector(center.getX(),top,center.getZ())}:
new Vector[]{center,new Vector(center.getX(),top,center.getZ()),new Vector(center.getX(),bottom,center.getZ())};
Vector start=eye.toVector();
for(Vector point:points){
Vector delta=point.clone().subtract(start);
double len=delta.length();
if(len<0.01)return false;
rayTests++;
if(viewer.getWorld().rayTraceBlocks(eye,delta,len,FluidCollisionMode.NEVER,true)==null)return false;
}
return true;
}

/** Cached only for canceled tracking requests (which Paper may retry every tick). */
private boolean cachedOccluded(Player viewer,Entity target){
long now=System.currentTimeMillis();
Pair pair=new Pair(viewer.getUniqueId(),target.getUniqueId());
Vector eye=viewer.getEyeLocation().toVector();
Vector pos=target.getLocation().toVector();
Decision decision=trackDecisions.get(pair);
if(decision!=null && now-decision.checked<=cacheMs &&
decision.viewer.distanceSquared(eye)<0.16 && decision.target.distanceSquared(pos)<0.16)return decision.hidden;
boolean result=occluded(viewer,target);
if(trackDecisions.size()>=maxCached)trackDecisions.clear();
if(decision==null)decision=new Decision();
decision.checked=now;decision.viewer=eye;decision.target=pos;decision.hidden=result;
trackDecisions.put(pair,decision);
return result;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onTrack(PlayerTrackEntityEvent event){
try{
Player viewer=event.getPlayer();
Entity target=event.getEntity();
if(shouldHide(viewer,target)){
event.setCancelled(true);
blockedSpawns++;
}
}catch(RuntimeException problem){
errors++; // fail-open: keep world/player behavior correct on unexpected errors
}
}

private void show(Player viewer,UUID targetId,Set<UUID> set){
set.remove(targetId);
Entity entity=Bukkit.getEntity(targetId);
if(entity!=null && entity.isValid()){
viewer.showEntity(plugin,entity);
shows++;
}
}

private void restoreViewer(Player viewer){
Set<UUID> set=hidden.remove(viewer.getUniqueId());
if(set==null)return;
for(UUID id:set){
Entity entity=Bukkit.getEntity(id);
if(entity!=null && entity.isValid())viewer.showEntity(plugin,entity);
}
}

private void restoreAll(){
for(UUID uuid:new ArrayList<>(hidden.keySet())){
Player viewer=Bukkit.getPlayer(uuid);
if(viewer!=null)restoreViewer(viewer);
else hidden.remove(uuid);
}
hidden.clear();
}

private void scanViewer(Player viewer,long deadline,int[] used){
Set<UUID> set=hidden.computeIfAbsent(viewer.getUniqueId(),ignored->new HashSet<>());
for(UUID id:new ArrayList<>(set)){
if(used[0]>=maxChecks || System.nanoTime()>deadline)return;
used[0]++;
Entity target=Bukkit.getEntity(id);
if(target==null || !target.isValid()){
set.remove(id);
continue;
}
if(!shouldHide(viewer,target)){
show(viewer,id,set);
}
}
if(used[0]>=maxChecks || System.nanoTime()>deadline)return;
double r=Math.sqrt(maxSq);
for(Entity target:viewer.getNearbyEntities(r,r,r)){
if(used[0]>=maxChecks || System.nanoTime()>deadline)break;
used[0]++;
if(set.size()>=maxHidden)break;
UUID id=target.getUniqueId();
if(set.contains(id))continue;
if(shouldHide(viewer,target)){
viewer.hideEntity(plugin,target);
set.add(id);
hides++;
}
}
if(set.isEmpty())hidden.remove(viewer.getUniqueId());
}

private void scan(){
long start=System.nanoTime();
long deadline=start+maxMicros*1000L;
try{
scans++;
ArrayList<Player> players=new ArrayList<>(Bukkit.getOnlinePlayers());
if(players.isEmpty()){
hidden.clear();trackDecisions.clear();viewerCursor=0;return;
}
int[] used={0};
for(int j=0;j<Math.min(maxViewers,players.size());j++){
if(used[0]>=maxChecks || System.nanoTime()>deadline){budgetStops++;break;}
Player viewer=players.get(Math.floorMod(viewerCursor++,players.size()));
scanViewer(viewer,deadline,used);
}
if(scans%60==0){
long now=System.currentTimeMillis();
trackDecisions.entrySet().removeIf(e->now-e.getValue().checked>3000);
hidden.keySet().removeIf(id->Bukkit.getPlayer(id)==null);
}
}catch(RuntimeException ex){
errors++;
if(errors<=3 || errors%100==0)plugin.getLogger().warning("Underground entity guard skipped scan: "+ex.getMessage());
}finally{
lastMicros=(System.nanoTime()-start)/1000L;
}
}

@EventHandler public void onQuit(PlayerQuitEvent event){
restoreViewer(event.getPlayer());
trackDecisions.keySet().removeIf(p->p.viewer().equals(event.getPlayer().getUniqueId())||p.target().equals(event.getPlayer().getUniqueId()));
}
@EventHandler public void onWorldChange(PlayerChangedWorldEvent event){restoreViewer(event.getPlayer());}
@EventHandler public void onTeleport(PlayerTeleportEvent event){
if(event.getTo()!=null && event.getFrom().getWorld()!=event.getTo().getWorld())restoreViewer(event.getPlayer());
}
@EventHandler public void onRespawn(PlayerRespawnEvent event){restoreViewer(event.getPlayer());}

String stats(){
int hiddenCount=0;
for(Set<UUID> s:hidden.values())hiddenCount+=s.size();
return "EntityESP="+(enabled?"ON":"OFF")+" maxRange="+(int)Math.sqrt(maxSq)+" strictDistance="+(int)Math.sqrt(strictSq)+" strictDecisions="+strictDecisions+" legacyRangeFixes="+oldConfigAdjustments+" trackBlocked="+blockedSpawns+
" hidden="+hiddenCount+" hides="+hides+" shows="+shows+" rays="+rayTests+
" scanPasses="+scans+" trackCache="+trackDecisions.size()+
" budgetStops="+budgetStops+" errors="+errors+" lastMicros="+lastMicros;
}
}
