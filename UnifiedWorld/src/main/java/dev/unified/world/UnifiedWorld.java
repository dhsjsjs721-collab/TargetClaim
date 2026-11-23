package dev.unified.world;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Manual-only, bounded and cancellable world pre-generation with no file deletion. */
public final class UnifiedWorld extends JavaPlugin {
private static final class Job {
final UUID worldId;
final String worldName;
final int originX,originZ;
final List<PregenPlan.Offset> chunks;
int index,success,failed;
boolean inflight;
Job(World world,int radius,int cx,int cz){
worldId=world.getUID();worldName=world.getName();
originX=cx;originZ=cz;chunks=PregenPlan.spiral(radius);
}
int total(){return chunks.size();}
}
private Job job;
private int task=-1;
private int maxRadius;
private long requested,borders,errors,failedChunks;
@Override public void onEnable(){
saveDefaultConfig();
maxRadius=Math.max(1,Math.min(128,getConfig().getInt("pregen.max-radius-chunks",32)));
task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,this::tick,2L,1L);
getLogger().info("UnifiedWorld 1.0: pregeneration is manual-only; no automatic deletions or world scans.");
}
@Override public void onDisable(){
job=null;
if(task!=-1){Bukkit.getScheduler().cancelTask(task);task=-1;}
}
private World worldFor(CommandSender sender,String name){
if(name!=null && !name.isBlank())return Bukkit.getWorld(name);
if(sender instanceof Player p)return p.getWorld();
List<World> worlds=Bukkit.getWorlds();
return worlds.isEmpty()?null:worlds.getFirst();
}
private void tick(){
Job active=job;
if(active==null || active.inflight)return;
World world=Bukkit.getWorld(active.worldId);
if(world==null){getLogger().warning("World unloaded; pregen cancelled.");job=null;return;}
if(active.index>=active.total()){
getLogger().info("Pregen finished "+active.worldName+": generated/loaded "+active.success+
" chunks, failed "+active.failed+" (requested "+active.total()+").");
job=null;return;
}
PregenPlan.Offset offset=active.chunks.get(active.index++);
active.inflight=true;
int cx=active.originX+offset.x(),cz=active.originZ+offset.z();
try{
// Only one asynchronous chunk request at a time; the request may generate the chunk.
// No plugin chunk tickets are retained after the request finishes.
CompletableFuture<Chunk> completion=world.getChunkAtAsync(cx,cz,true);
completion.whenComplete((chunk,problem)->{
if(!isEnabled())return;
Bukkit.getScheduler().runTask(this,()->{
if(job!=active)return;
active.inflight=false;
if(problem==null&&chunk!=null)active.success++;
else{active.failed++;failedChunks++;}
});
});
}catch(RuntimeException error){
active.inflight=false;active.failed++;failedChunks++;errors++;
if(errors<=3)getLogger().warning("Chunk request failed: "+error.getMessage());
}
}
private void status(CommandSender sender){
sender.sendMessage("UnifiedWorld 1.0: loaded worlds="+Bukkit.getWorlds().size()+
" chunkRequests="+requested+" bordersEdited="+borders+" errors="+errors);
for(World w:Bukkit.getWorlds()){
sender.sendMessage(w.getName()+": loaded chunks="+w.getChunkCount()+" players="+w.getPlayerCount()+
" worldborder diameter="+Math.round(w.getWorldBorder().getSize())+" blocks");
}
Job active=job;
if(active==null)sender.sendMessage("Pregen: IDLE (no background scanning).");
else sender.sendMessage("Pregen "+active.worldName+": "+active.success+"/"+active.total()+
" chunks done, "+active.failed+" errors, in flight="+active.inflight+". /uworld cancel to stop.");
}
private void start(CommandSender sender,String[] args){
if(job!=null){sender.sendMessage("Generation already running. /uworld cancel");return;}
if(args.length<2){sender.sendMessage("Usage: /uworld pregen <radius_in_chunks> [world]");return;}
int radius;
try{radius=Integer.parseInt(args[1]);}
catch(NumberFormatException e){sender.sendMessage("Radius must be an integer.");return;}
if(radius<1||radius>maxRadius){
sender.sendMessage("Pregen radius must be 1.."+maxRadius+" chunks. Larger values require config change and restart.");
return;
}
World w=worldFor(sender,args.length>2?args[2]:null);
if(w==null){sender.sendMessage("Unknown world.");return;}
Location center=sender instanceof Player p && p.getWorld()==w?p.getLocation():w.getSpawnLocation();
job=new Job(w,radius,center.getBlockX()>>4,center.getBlockZ()>>4);
requested++;
sender.sendMessage("Pregen STARTED "+w.getName()+" around chunk "+(center.getBlockX()>>4)+","+(center.getBlockZ()>>4)+
": "+job.total()+" chunks. Rate: only ONE in-flight request.");
}
private void border(CommandSender sender,String[] args){
if(args.length<2){sender.sendMessage("Usage: /uworld border <radius_blocks> [world]");return;}
double radius;
try{radius=Double.parseDouble(args[1]);}
catch(NumberFormatException e){sender.sendMessage("Radius must be a number.");return;}
if(!Double.isFinite(radius)||radius<16||radius>14_000_000){
sender.sendMessage("Radius must be between 16 and 14000000 blocks.");return;
}
World w=worldFor(sender,args.length>2?args[2]:null);
if(w==null){sender.sendMessage("Unknown world.");return;}
Location center=sender instanceof Player p&&p.getWorld()==w?p.getLocation():w.getSpawnLocation();
WorldBorder border=w.getWorldBorder();
border.setCenter(center.getX(),center.getZ());
border.setSize(2*radius);
borders++;
sender.sendMessage("Border in "+w.getName()+" set to radius "+Math.round(radius)+
" from X="+center.getBlockX()+" Z="+center.getBlockZ()+".");
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(!sender.hasPermission("unifiedworld.admin")){sender.sendMessage("No permission.");return true;}
String action=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
switch(action){
case "status","audit"->status(sender);
case "pregen","pregenerate"->start(sender,args);
case "cancel"->{
if(job==null)sender.sendMessage("No active pre-generation.");
else{sender.sendMessage("Stopped new chunk requests; the current one may finish.");job=null;}
}
case "border"->border(sender,args);
default->sender.sendMessage("Usage: /uworld [status|pregen <radiusChunks> [world]|cancel|border <radiusBlocks> [world]]");
}
return true;
}
}
