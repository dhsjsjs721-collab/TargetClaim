package dev.unified;

import dev.targetclaim.TargetClaimPlugin;
import dev.talismanforge.TalismanForgePlugin;
import dev.enchantmentforge.EnchantmentForgePlugin;
import dev.swampsky.SwampSkyPlugin;
import dev.imagemaps.ImageMapsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UnifiedPlugin extends JavaPlugin implements CommandExecutor,TabCompleter,Listener {
private final List<ModuleBridge> enabledModules=new ArrayList<>();
private final Map<String,String> failedModules=new LinkedHashMap<>();
private TrapItems trapItems;
private RetiredShields retiredShields;
private DeepslateCrafts deepslateCrafts;
private PeaceZone peace;
private MiningGear mining;
private static final Map<String,String> ROUTES=Map.ofEntries(
Map.entry("claim","p"),Map.entry("claims","p"),Map.entry("p","p"),Map.entry("private","p"),
Map.entry("team","team"),Map.entry("teams","team"),
Map.entry("tf","talismanforge"),Map.entry("talisman","talismanforge"),Map.entry("talismans","talismanforge"),
Map.entry("ce","enchantmentforge"),Map.entry("enchant","enchantmentforge"),Map.entry("enchantments","enchantmentforge"),
Map.entry("ss","swampsky"),Map.entry("wildlife","swampsky"),Map.entry("swamp","swampsky"),Map.entry("swampsky","swampsky"),
Map.entry("map","imagemap"),Map.entry("image","imagemap"),Map.entry("imagemap","imagemap")
);
private static final Map<String,String> OWNERS=Map.of(
"p","TargetClaim","team","TargetClaim","talismanforge","TalismanForge",
"enchantmentforge","EnchantmentForge","swampsky","SwampSky","imagemap","ImageMaps");
@Override public void onEnable(){
getDataFolder().mkdirs();
getConfig().addDefault("peace.world","world");getConfig().addDefault("peace.radius",250);getConfig().addDefault("peace.containers-per-chunk",16);getConfig().addDefault("mining.dynamic-light",true);getConfig().options().copyDefaults(true);saveConfig();
peace=new PeaceZone(this);peace.enable();
Bukkit.getPluginManager().registerEvents(this,this);
PluginCommand entry=getCommand("unified");
if(entry!=null){entry.setExecutor(this);entry.setTabCompleter(this);}
List<ModuleBridge> modules=List.of(
new TargetClaimPlugin(this),
new TalismanForgePlugin(this),
new EnchantmentForgePlugin(this),
new SwampSkyPlugin(this),
new ImageMapsPlugin(this)
);
for(ModuleBridge module:modules){
try{
module.onEnable();
enabledModules.add(module);
getLogger().info("Enabled module: "+module.getName());
}catch(Throwable failure){
String message=failure.getClass().getSimpleName()+": "+String.valueOf(failure.getMessage());
failedModules.put(module.getName(),message);
getLogger().severe("Module "+module.getName()+" failed, other modules remain online: "+message);
failure.printStackTrace();
// One bad module MUST NOT disable commands and gameplay of all other modules.
if(module instanceof Listener listener)HandlerList.unregisterAll(listener);
try{module.onDisable();}
catch(Throwable cleanup){getLogger().warning("Cleanup after failed module "+module.getName()+": "+cleanup);}
}
}
if(enabled("TargetClaim")){
try{
trapItems=new TrapItems(this,(TargetClaimPlugin)modules.get(0));
trapItems.enable();
PluginCommand trapCommand=getCommand("trap");
if(trapCommand!=null)trapCommand.setExecutor((sender,command,label,args)->trapItems.handleCommand(sender,args));
getLogger().info("Obsidian PvP trap module enabled.");
}catch(Throwable error){
getLogger().severe("PvP trap module startup failed: "+error.getMessage());
error.printStackTrace();
if(trapItems!=null){try{trapItems.disable();}catch(Throwable ignored){}}
trapItems=null;
}
}
retiredShields=new RetiredShields(this);
retiredShields.enable();
mining=new MiningGear(this);mining.enable();
try{
deepslateCrafts=new DeepslateCrafts(this);
deepslateCrafts.enable();
getLogger().info("Deepslate craft enabled: 4 basalt + 4 blackstone + 1 obsidian -> 8 deepslate");
}catch(Throwable ex){
getLogger().severe("Deepslate recipe failed to register: "+ex.getMessage());
if(deepslateCrafts!=null)deepslateCrafts.disable();
deepslateCrafts=null;
}
if(Bukkit.getPluginManager().getPlugin("TalismanForge")!=null){
getLogger().severe("WARNING: standalone TalismanForge detected. Remove its old JAR from plugins to prevent duplicate abilities or command conflicts.");
}
getLogger().info("MinecraftUnified 1.1.14 started modules "+enabledModules.size()+"/"+modules.size()+
(failedModules.isEmpty()?" (all ready)":" (see /u status)"));
}
@EventHandler public void unifiedCommandList(PlayerCommandSendEvent event){
String[] internal={"unified","allmods","mods","trap","trapka","p","claim","privat","team","tc",
"talismanforge","tf","talismans","enchantmentforge","ce","enchants",
"swampsky","wildlife","ss","imagemap","im"};
for(String name:internal){
event.getCommands().remove(name);
event.getCommands().remove("minecraftunified:"+name);
}
}
public boolean isPeace(org.bukkit.Location l){return peace!=null&&peace.contains(l);}
public TargetClaimPlugin claimModule(){for(ModuleBridge m:enabledModules)if(m instanceof TargetClaimPlugin c)return c;return null;}
public org.bukkit.Location getKoscheiEggLocation(){for(ModuleBridge m:enabledModules)if(m instanceof TalismanForgePlugin t)return t.getKoscheiEggLocation();return null;}
private boolean enabled(String module){
for(ModuleBridge m:enabledModules)if(m.getName().equals(module))return true;
return false;
}
private void status(CommandSender sender){
sender.sendMessage("§aMinecraftUnified 1.1.14 §7| Запущено модулей: §f"+enabledModules.size()+"/5");
sender.sendMessage(trapItems!=null?"§a✓ PvP-трапки §7активны":"§c✗ PvP-трапки §7не активны");
sender.sendMessage(deepslateCrafts!=null?"§a✓ Крафт сланца §7активен":"§c✗ Крафт сланца §7не запущен");
for(String name:List.of("TargetClaim","TalismanForge","EnchantmentForge","SwampSky","ImageMaps")){
if(enabled(name))sender.sendMessage("§a✓ "+name+" §7работает");
else sender.sendMessage("§c✗ "+name+" §7ошибка запуска: "+failedModules.getOrDefault(name,"неизвестно"));
}
}
private void help(CommandSender sender){
sender.sendMessage("§6§lMinecraftUnified §8» §7Все функции через §e/u");
sender.sendMessage("§e/u status §7— состояние модулей");
sender.sendMessage("§e/u claim §7— приваты; §e/u claim list §7— список");
sender.sendMessage("§e/u team §7— меню команды");
sender.sendMessage("§e/u team create <название> <цвет> §7— создать цветную команду");
sender.sendMessage("§e/u team color <цвет> §7— сменить цвет (лидер)");
sender.sendMessage("§e/u team colors §7— доступные цвета");
sender.sendMessage("§e/u team invite <ник> §7— пригласить");
sender.sendMessage("§e/u team accept §7— принять приглашение");
sender.sendMessage("§e/u talisman §7— реликварий и мифические талисманы");
sender.sendMessage("§e/u talisman list §7— список пяти новых талисманов");
sender.sendMessage("§e/u talisman give <ник> all §7— выдать все пять сразу");
sender.sendMessage("§e/u enchant §7— зачарования; §e/u trader §7— торговец");
sender.sendMessage("§e/u wildlife §7— лягушки и экипировка; §e/u frog §7— гигантская лягушка");
sender.sendMessage("§e/u map §7— карты; §e/u trap §7— обсидиановая ловушка");
sender.sendMessage("§e/u mining §7— шахтёрская экипировка и метательные факелы");
sender.sendMessage("§7Летающие свиньи удалены; обычные свиньи сохранены.");
}

private String route(String input){
return switch(input){
case "frog","repair","trader" -> input.equals("trader")?"enchantmentforge":"swampsky";
default -> ROUTES.get(input);
};
}
private String forwarded(String action,String[] args){
return switch(action){
case "frog" -> "swampsky spawn giant_frog";
case "repair" -> "swampsky repair";
case "trader" -> "enchantmentforge trader";
default -> {
String command=ROUTES.get(action);
yield command==null?null:command+(args.length==1?"":" "+String.join(" ",Arrays.copyOfRange(args,1,args.length)));
}
};
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length==0||args[0].equalsIgnoreCase("help")){help(sender);return true;}
String action=args[0].toLowerCase(Locale.ROOT);
if(action.equals("mining"))return mining.command(sender,Arrays.copyOfRange(args,1,args.length));
if(action.equals("status")||action.equals("check")||action.equals("list")){status(sender);return true;}
if(action.equals("trap")||action.equals("trapka")){
if(trapItems==null){sender.sendMessage("§cМодуль PvP-трапок не запущен.");return true;}
return trapItems.handleCommand(sender,Arrays.copyOfRange(args,1,args.length));
}
String target=route(action);
if(target==null){sender.sendMessage("§cНеизвестная команда. Используй /u help");return true;}
String owner=OWNERS.get(target);
if(!enabled(owner)){sender.sendMessage("§c"+owner+" не запущен. Проверка: /u status");return true;}
String forward=forwarded(action,args);
if(forward==null)return true;
try{
// Dispatch the talisman module directly. A leftover standalone TalismanForge
// can otherwise own /talismanforge and silently receive /u talisman commands.
if(target.equals("talismanforge")){
TalismanForgePlugin module=null;
for(ModuleBridge loaded:enabledModules)if(loaded instanceof TalismanForgePlugin mythic){module=mythic;break;}
if(module==null){sender.sendMessage("§cМодуль пяти талисманов не запущен.");return true;}
return module.onCommand(sender,getCommand("talismanforge"),"talismanforge",Arrays.copyOfRange(args,1,args.length));
}
if(!Bukkit.dispatchCommand(sender,forward))sender.sendMessage("§cКоманда не выполнена: "+forward);
}catch(Exception ex){
getLogger().warning("Unified command '"+forward+"' failed: "+ex.getMessage());
sender.sendMessage("§cОшибка модуля "+owner+". Подробности в logs/latest.log");
}
return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
if(args.length<=1){
String prefix=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
return List.of("help","status","claim","team","talisman","enchant","wildlife","frog","trader","map","trap","mining")
.stream().filter(x->x.startsWith(prefix)).toList();
}
String action=args[0].toLowerCase(Locale.ROOT);
if(action.equals("trap")||action.equals("trapka")){
if(args.length==2)return List.of("give","help").stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
if(args.length==3&&args[1].equalsIgnoreCase("give"))
return Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
return List.of();
}
String target=route(action);
if(target==null||!enabled(OWNERS.get(target)))return List.of();
try{
String[] rest=Arrays.copyOfRange(args,1,args.length);
if(target.equals("talismanforge")){
for(ModuleBridge loaded:enabledModules)if(loaded instanceof TalismanForgePlugin mythic)
return mythic.onTabComplete(sender,getCommand("talismanforge"),target,rest);
return List.of();
}
PluginCommand sub=getCommand(target);
if(sub==null)return List.of();
return sub.tabComplete(sender,target,rest);
}catch(Exception ex){return List.of();}
}
@Override public void onDisable(){
if(mining!=null){mining.disable();mining=null;}
if(deepslateCrafts!=null){try{deepslateCrafts.disable();}catch(Throwable ex){getLogger().warning("Recipe cleanup failed: "+ex);}deepslateCrafts=null;}
if(trapItems!=null){try{trapItems.disable();}catch(Throwable failure){getLogger().warning("PvP trap cleanup failed: "+failure.getMessage());}trapItems=null;}
for(int i=enabledModules.size()-1;i>=0;i--){
ModuleBridge module=enabledModules.get(i);
try{module.onDisable();}
catch(Throwable failure){getLogger().severe("Shutdown failed for "+module.getName()+": "+failure);}
}
enabledModules.clear();failedModules.clear();
}
}
