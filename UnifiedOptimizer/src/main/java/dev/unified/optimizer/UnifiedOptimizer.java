package dev.unified.optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
final int baselineView;
final int baselineSimulation;
int lastWrittenView;
int lastWrittenSimulation;
long lastChangeNanos;
boolean conflictingController;
WorldState(World world) {
id=world.getUID();
baselineView=world.getViewDistance();
baselineSimulation=world.getSimulationDistance();
lastWrittenView=baselineView;
lastWrittenSimulation=baselineSimulation;
}
}
private final Map<UUID,WorldState> worlds=new LinkedHashMap<>();
private AdaptivePolicy policy;
private int task=-1,roundRobin;
private boolean enabled,paused,requirePlayers,restoreWhenEmpty,logChanges,changeView,changeSimulation;
private int periodTicks,minimumView,minimumSimulation,maximumView,maximumSimulation;
private int maxChangesPerSample;
private long cooldownNanos;
private Set<String> excluded=Set.of();
private long samples,viewReductions,viewIncreases,simulationReductions,simulationIncreases;
private long idleRestores,controllerConflicts,exceptions,workSamples;
private long lastSampleMicros;

@Override public void onEnable(){
saveDefaultConfig();
Bukkit.getPluginManager().registerEvents(this,this);
configure();
getLogger().info("Passive adaptive controller: no entity removal, forced chunk loads, world scans or System.gc().");
}
@Override public void onDisable(){
if(task!=-1){Bukkit.getScheduler().cancelTask(task);task=-1;}
restoreAll(false);
worlds.clear();
}
private static int clamp(int val,int low,int high){return Math.max(low,Math.min(high,val));}
private static double clamp(double val,double low,double high) {
return Double.isFinite(val)?Math.max(low,Math.min(high,val)):low;
}
private void configure(){
if(task!=-1){Bukkit.getScheduler().cancelTask(task);task=-1;}
restoreAll(false);
worlds.clear();
reloadConfig();
FileConfiguration c=getConfig();
enabled=c.getBoolean("adaptive.enabled",true);
requirePlayers=c.getBoolean("adaptive.require-players-in-world",true);
restoreWhenEmpty=c.getBoolean("adaptive.restore-when-world-empty",true);
logChanges=c.getBoolean("diagnostics.announce-distance-changes-in-console",true);
changeView=c.getBoolean("adaptive.adjust-view-distance",true);
changeSimulation=c.getBoolean("adaptive.adjust-simulation-distance",true);
periodTicks=clamp(c.getInt("adaptive.sample-period-ticks",200),100,1200);
minimumView=clamp(c.getInt("adaptive.min-view-distance",5),2,32);
minimumSimulation=clamp(c.getInt("adaptive.min-simulation-distance",4),2,32);
maximumView=clamp(c.getInt("adaptive.max-view-distance",0),0,32);
maximumSimulation=clamp(c.getInt("adaptive.max-simulation-distance",0),0,32);
maxChangesPerSample=clamp(c.getInt("adaptive.max-world-adjustments-per-sample",1),1,4);
int cooldownSeconds=clamp(c.getInt("adaptive.adjustment-cooldown-seconds",45),10,600);
cooldownNanos=cooldownSeconds*1_000_000_000L;
double badMspt=clamp(c.getDouble("adaptive.mspt-overload-threshold",46),12,95);
double goodMspt=clamp(c.getDouble("adaptive.mspt-recovery-threshold",37),5,badMspt-1);
double badTps=clamp(c.getDouble("adaptive.tps-overload-threshold",19.2),1,19.9);
double goodTps=clamp(c.getDouble("adaptive.tps-recovery-threshold",19.8),badTps,20);
int badSamples=clamp(c.getInt("adaptive.consecutive-overload-samples",3),1,30);
int goodSamples=clamp(c.getInt("adaptive.consecutive-recovery-samples",6),1,60);
double alpha=clamp(c.getDouble("adaptive.mspt-smoothing-alpha",0.35),0.1,0.75);
policy=new AdaptivePolicy(badMspt,goodMspt,badTps,goodTps,badSamples,goodSamples,alpha);
Set<String> names=new HashSet<>();
for(String s:c.getStringList("adaptive.excluded-worlds"))names.add(s.toLowerCase(Locale.ROOT));
excluded=Set.copyOf(names);
paused=false;
roundRobin=0;
lastSampleMicros=0;
for(World world:Bukkit.getWorlds())worlds.put(world.getUID(),new WorldState(world));
task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,this::sample,periodTicks,periodTicks);
}
@EventHandler public void onWorldLoad(WorldLoadEvent event){
World world=event.getWorld();
worlds.putIfAbsent(world.getUID(),new WorldState(world));
}
@EventHandler(ignoreCancelled=true) public void onWorldUnload(WorldUnloadEvent event){
WorldState state=worlds.remove(event.getWorld().getUID());
if(state!=null)restore(event.getWorld(),state,false);
}
private boolean externalChange(World world,WorldState state){
if(state.conflictingController)return true;
if(world.getViewDistance()!=state.lastWrittenView || world.getSimulationDistance()!=state.lastWrittenSimulation){
state.conflictingController=true;
controllerConflicts++;
getLogger().warning("Another plugin changed distances in '"+world.getName()+"'. Controller disabled for this world until /uopt reload.");
return true;
}
return false;
}
private boolean restore(World world,WorldState state,boolean idle){
boolean changed=false;
try{
int actualView=world.getViewDistance(),actualSimulation=world.getSimulationDistance();
if(actualView!=state.lastWrittenView || actualSimulation!=state.lastWrittenSimulation){
if(!state.conflictingController)externalChange(world,state);
}
// Restore only settings still equal to our own last write; never
// overwrite a third-party controller's newer values.
if(actualView==state.lastWrittenView && actualView!=state.baselineView){
world.setViewDistance(state.baselineView);
state.lastWrittenView=world.getViewDistance();
changed=true;
}
if(actualSimulation==state.lastWrittenSimulation && actualSimulation!=state.baselineSimulation){
world.setSimulationDistance(state.baselineSimulation);
state.lastWrittenSimulation=world.getSimulationDistance();
changed=true;
}
if(changed && idle)idleRestores++;
}catch(RuntimeException e){
exceptions++;
getLogger().warning("Cannot restore '"+world.getName()+"' distances: "+e.getMessage());
}
return changed;
}
private void restoreAll(boolean idle){
for(WorldState state:worlds.values()){
World world=Bukkit.getWorld(state.id);
if(world!=null)restore(world,state,idle);
}
}
private static int next(int current,int floor,int ceiling,int direction){
if(direction<0)return Math.max(floor,current-1);
if(current<ceiling)return current+1;
return current;
}
private boolean adjust(World world,WorldState state,int direction,long nowNanos){
if(externalChange(world,state))return false;
int capView=maximumView==0?state.baselineView:Math.min(state.baselineView,maximumView);
int capSim=maximumSimulation==0?state.baselineSimulation:Math.min(state.baselineSimulation,maximumSimulation);
int floorView=Math.min(minimumView,capView);
int floorSim=Math.min(minimumSimulation,capSim);
int view=state.lastWrittenView,simulation=state.lastWrittenSimulation;
int newView=changeView?next(view,floorView,capView,direction):view;
int newSim=changeSimulation?next(simulation,floorSim,capSim,direction):simulation;
if(newView==view && newSim==simulation)return false;
try{
if(newView!=view){
world.setViewDistance(newView);
state.lastWrittenView=world.getViewDistance();
if(direction<0)viewReductions++;else viewIncreases++;
}
if(newSim!=simulation){
world.setSimulationDistance(newSim);
state.lastWrittenSimulation=world.getSimulationDistance();
if(direction<0)simulationReductions++;else simulationIncreases++;
}
state.lastChangeNanos=nowNanos;
if(logChanges){
getLogger().info("World '"+world.getName()+"': view "+view+" -> "+state.lastWrittenView+", simulation "+simulation+" -> "+state.lastWrittenSimulation+"; MSPT="+format(policy.ema())+" TPS="+format(policy.lastTps()));
}
return true;
}catch(RuntimeException e){
exceptions++;
state.conflictingController=true;
getLogger().warning("Distance adjustment failed in '"+world.getName()+"'; safety stop: "+e.getMessage());
return false;
}
}
private void sample(){
long start=System.nanoTime();
try{
samples++;
double mspt=Bukkit.getAverageTickTime();
double[] tps=Bukkit.getTPS();
int direction=policy.observe(mspt,tps.length==0?20:tps[0]);
// Small, bounded world inventory and O(1) counters only. No chunk/entity enumeration.
for(WorldState state:worlds.values()){
World world=Bukkit.getWorld(state.id);
if(world==null || excluded.contains(world.getName().toLowerCase(Locale.ROOT)))continue;
if(!state.conflictingController)externalChange(world,state);
if(restoreWhenEmpty && world.getPlayerCount()==0 &&
(state.lastWrittenView!=state.baselineView || state.lastWrittenSimulation!=state.baselineSimulation)){
restore(world,state,true);
}
}
if(!enabled || paused || direction==0 || worlds.isEmpty())return;
ArrayList<WorldState> snapshot=new ArrayList<>(worlds.values());
int size=snapshot.size(),changed=0;
long now=System.nanoTime();
int startIndex=roundRobin%size;
for(int attempt=0;attempt<size && changed<maxChangesPerSample;attempt++){
WorldState state=snapshot.get((startIndex+attempt)%size);
World world=Bukkit.getWorld(state.id);
if(world==null || state.conflictingController)continue;
if(excluded.contains(world.getName().toLowerCase(Locale.ROOT)))continue;
if(requirePlayers && world.getPlayerCount()==0)continue;
if(state.lastChangeNanos!=0 && now-state.lastChangeNanos<cooldownNanos)continue;
if(adjust(world,state,direction,now))changed++;
}
roundRobin=(startIndex+Math.max(1,changed))%size;
if(changed>0)workSamples++;
}catch(Exception e){
exceptions++;
getLogger().warning("Sampler skipped invalid cycle: "+e.getMessage());
}finally{
lastSampleMicros=(System.nanoTime()-start)/1000;
}
}
private static String format(double val){
return Double.isFinite(val)?String.format(Locale.ROOT,"%.2f",val):"n/a";
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
String sub=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
switch(sub){
case "reload" -> {
configure();
sender.sendMessage("UnifiedOptimizer 1.1 settings reloaded; world baselines captured.");
}
case "pause", "reset" -> {
paused=true;
restoreAll(false);
policy.resetStreaks();
sender.sendMessage("UnifiedOptimizer paused and owned world settings restored.");
}
case "resume" -> {
paused=false;
policy.resetStreaks();
sender.sendMessage("UnifiedOptimizer resumed.");
}
case "profile" -> {
sender.sendMessage("UnifiedOptimizer 1.1 profile: MSPT="+format(policy.lastMspt())+" EMA="+format(policy.ema())+" windowAvg="+format(policy.average())+" peak="+format(policy.maximum())+" TPS="+format(policy.lastTps()));
sender.sendMessage("Window="+policy.samples()+"/12 overload streak="+policy.badStreak()+" recovery streak="+policy.goodStreak()+" lastSampleMicros="+lastSampleMicros+" exceptions="+exceptions);
sender.sendMessage("Changes per sample <= "+maxChangesPerSample+" cooldown="+(cooldownNanos/1_000_000_000L)+"s, interval="+periodTicks+" ticks");
}
case "status" -> {
sender.sendMessage("UnifiedOptimizer 1.1 enabled="+enabled+" paused="+paused+" samples="+samples+" adjustments="+workSamples+" MSPT="+format(policy.ema())+" TPS="+format(policy.lastTps()));
sender.sendMessage("View reduced/recovered="+viewReductions+"/"+viewIncreases+" sim reduced/recovered="+simulationReductions+"/"+simulationIncreases+" idleRestores="+idleRestores+" controllerConflicts="+controllerConflicts);
for(WorldState state:worlds.values()){
World world=Bukkit.getWorld(state.id);
if(world==null)continue;
sender.sendMessage(world.getName()+": view="+world.getViewDistance()+"/"+state.baselineView+" sim="+world.getSimulationDistance()+"/"+state.baselineSimulation+" players="+world.getPlayerCount()+" chunks="+world.getChunkCount()+" entities="+world.getEntityCount()+(state.conflictingController?" [EXTERNAL CONTROL]":""));
}
}
default -> sender.sendMessage("Usage: /uopt [status|profile|reload|pause|resume|reset]");
}
return true;
}
}
