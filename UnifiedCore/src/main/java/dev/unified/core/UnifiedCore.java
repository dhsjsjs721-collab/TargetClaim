package dev.unified.core;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server base QoL plugin; does not alter chunks, entities, authentication,
 * ownership, permissions from other plugins, or gameplay during startup.
 */
public final class UnifiedCore extends JavaPlugin implements Listener,CommandExecutor {
private static final List<String> MODULES=List.of("TargetClaim","UnifiedAntiESP","UnifiedProtection",
"UnifiedOptimizer","UnifiedAuth","UnifiedDefend","UnifiedMachines",
"UnifiedWorld","UnifiedEnchantments");
private long spawnTeleports,failures;
@Override public void onEnable(){
saveDefaultConfig();
getServer().getPluginManager().registerEvents(this,this);
getLogger().info("UnifiedCore started. No global world/player modifications.");
}
@EventHandler public void welcome(PlayerJoinEvent event){
if(!getConfig().getBoolean("join-message.enabled",false))return;
String message=getConfig().getString("join-message.text","Добро пожаловать на сервер!");
event.getPlayer().sendMessage(Component.text(message,NamedTextColor.AQUA));
}
private Location configuredSpawn(){
ConfigurationSection section=getConfig().getConfigurationSection("spawn");
if(section==null)return null;
String worldName=section.getString("world");
if(worldName==null)return null;
World world=Bukkit.getWorld(worldName);
if(world==null)return null;
double x=section.getDouble("x",0),y=section.getDouble("y",64),z=section.getDouble("z",0);
float yaw=(float)section.getDouble("yaw",0),pitch=(float)section.getDouble("pitch",0);
if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||
Math.abs(x)>29_000_000 || Math.abs(z)>29_000_000 || y<world.getMinHeight()||y>=world.getMaxHeight())return null;
return new Location(world,x,y,z,yaw,pitch);
}
private void spawn(CommandSender sender){
if(!(sender instanceof Player player)){sender.sendMessage("Используйте из игры.");return;}
if(!sender.hasPermission("unifiedcore.spawn")){sender.sendMessage("Недостаточно прав.");return;}
Location location=configuredSpawn();
if(location==null){
World world=player.getWorld();
location=world.getSpawnLocation();
}
// Paper teleportAsync loads only the one destination chunk asynchronously.
player.teleportAsync(location).whenComplete((ok,error)->{
if(!isEnabled())return;
Bukkit.getScheduler().runTask(this,()->{
if(error!=null || !Boolean.TRUE.equals(ok)){failures++;player.sendMessage("Не удалось телепортироваться.");}
else{spawnTeleports++;player.sendMessage("Телепортация на спавн завершена.");}
});
});
}
private void setspawn(CommandSender sender){
if(!(sender instanceof Player player) || !sender.hasPermission("unifiedcore.admin")){
sender.sendMessage("Только администратор в игре может установить спавн.");return;
}
Location l=player.getLocation();
getConfig().set("spawn.world",l.getWorld().getName());
getConfig().set("spawn.x",l.getX());getConfig().set("spawn.y",l.getY());getConfig().set("spawn.z",l.getZ());
getConfig().set("spawn.yaw",l.getYaw());getConfig().set("spawn.pitch",l.getPitch());
saveConfig();
sender.sendMessage("Новая точка /ucspawn сохранена.");
}
private void status(CommandSender sender){
sender.sendMessage("UnifiedCore 1.0 | Paper "+Bukkit.getBukkitVersion()+" | Java "+System.getProperty("java.version"));
sender.sendMessage("Players "+Bukkit.getOnlinePlayers().size()+"/"+Bukkit.getMaxPlayers()+
" | TPS "+Metrics.format(Bukkit.getTPS()[0])+" | MSPT "+Metrics.format(Bukkit.getAverageTickTime()));
Runtime runtime=Runtime.getRuntime();
long used=(runtime.totalMemory()-runtime.freeMemory())/1048576;
long heap=runtime.maxMemory()/1048576;
sender.sendMessage("Heap "+used+"/"+heap+" MiB | spawn teleports "+spawnTeleports+" | failed "+failures);
}
private void diagnostics(CommandSender sender){
for(String name:MODULES){
var plugin=Bukkit.getPluginManager().getPlugin(name);
sender.sendMessage(name+": "+(plugin==null?"not installed":(plugin.isEnabled()?"ON":"DISABLED")+" v"+plugin.getPluginMeta().getVersion()));
}
for(World w:Bukkit.getWorlds()){
sender.sendMessage("World "+w.getName()+" | players="+w.getPlayerCount()+
" | loaded chunks="+w.getChunkCount()+" | view="+w.getViewDistance()+
" | simulation="+w.getSimulationDistance());
}
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
String alias=command.getName().toLowerCase(Locale.ROOT);
if(alias.equals("ucspawn")){spawn(sender);return true;}
if(alias.equals("ucsetspawn")){setspawn(sender);return true;}
if(alias.equals("ucping")){
if(sender instanceof Player player)player.sendMessage("Ping: "+player.getPing()+"ms");
else sender.sendMessage("Ping доступен только игрокам.");
return true;
}
String arg=args.length==0?"status":args[0].toLowerCase(Locale.ROOT);
if(!sender.hasPermission("unifiedcore.admin")){sender.sendMessage("Недостаточно прав.");return true;}
switch(arg){
case "status"->status(sender);
case "check","plugins","audit"->diagnostics(sender);
case "reload"->{reloadConfig();sender.sendMessage("UnifiedCore config reloaded.");}
default->sender.sendMessage("Usage: /ucore [status|check|reload]");
}
return true;
}
}
