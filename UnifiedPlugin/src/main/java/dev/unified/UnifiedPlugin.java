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
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UnifiedPlugin extends JavaPlugin implements CommandExecutor,TabCompleter {
private final List<ModuleBridge> enabledModules=new ArrayList<>();
private final Map<String,String> failedModules=new LinkedHashMap<>();
private TrapItems trapItems;
private CombatShields combatShields;
private static final Map<String,String> ROUTES=Map.ofEntries(
Map.entry("claim","p"),Map.entry("p","p"),Map.entry("private","p"),
Map.entry("team","team"),
Map.entry("tf","talismanforge"),Map.entry("talisman","talismanforge"),Map.entry("talismans","talismanforge"),
Map.entry("ce","enchantmentforge"),Map.entry("enchant","enchantmentforge"),Map.entry("enchantments","enchantmentforge"),
Map.entry("ss","swampsky"),Map.entry("swamp","swampsky"),Map.entry("swampsky","swampsky"),
Map.entry("map","imagemap"),Map.entry("image","imagemap"),Map.entry("imagemap","imagemap")
);
private static final Map<String,String> OWNERS=Map.of(
"p","TargetClaim","team","TargetClaim","talismanforge","TalismanForge",
"enchantmentforge","EnchantmentForge","swampsky","SwampSky","imagemap","ImageMaps");
@Override public void onEnable(){
getDataFolder().mkdirs();
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
try{
combatShields=new CombatShields(this);
combatShields.enable();
getLogger().info("Combat shields active: Wither, Turtle, Crusher");
}catch(Throwable ex){
getLogger().severe("Custom combat shields failed: "+ex);
ex.printStackTrace();
if(combatShields!=null)try{combatShields.disable();}catch(Throwable ignored){}
combatShields=null;
}
getLogger().info("MinecraftUnified 1.1.1 started modules "+enabledModules.size()+"/"+modules.size()+
(failedModules.isEmpty()?" (all ready)":" (see /u status)"));
}
private boolean enabled(String module){
for(ModuleBridge m:enabledModules)if(m.getName().equals(module))return true;
return false;
}
private void status(CommandSender sender){
sender.sendMessage("§aMinecraftUnified 1.1.1 §7| Запущено модулей: §f"+enabledModules.size()+"/5");
sender.sendMessage(trapItems!=null?"§a✓ PvP-трапки §7активны":"§c✗ PvP-трапки §7не активны");
sender.sendMessage(combatShields!=null?"§a✓ 3 особых щита §7активны":"§c✗ Особые щиты §7не запущены");
for(String name:List.of("TargetClaim","TalismanForge","EnchantmentForge","SwampSky","ImageMaps")){
if(enabled(name))sender.sendMessage("§a✓ "+name+" §7работает");
else sender.sendMessage("§c✗ "+name+" §7ошибка запуска: "+failedModules.getOrDefault(name,"неизвестно"));
}
}
private void help(CommandSender sender){
sender.sendMessage("§aMinecraftUnified §7— одна команда для всех модулей:");
sender.sendMessage("§e/u status §7— проверка всех 5 модулей");
sender.sendMessage("§e/u pig §7— летающая свинка §8| §e/u frog §7— гигантская жаба");
sender.sendMessage("§e/u repair §7— восстановить AI свинок");
sender.sendMessage("§e/u trader §7— странствующий торговец");
sender.sendMessage("§e/u tf give <ник> <тип> §7— талисман");
sender.sendMessage("§e/u ss give <ник> <hide|boots|leggings|wings> §7— вещи");
sender.sendMessage("§e/u map §7— окно картинки на карте");
sender.sendMessage("§e/u trap §7— крафт и использование трапки");
sender.sendMessage("§e/u shield §7— особые щиты и рецепты");
sender.sendMessage("§e/u trap give <ник> [количество] §7— выдача для администратора");
sender.sendMessage("§e/u p list §7— два привата, §e/u p 1/2 §7— переключение");
sender.sendMessage("§e/u p §7и §e/u team §7— приват и команда");
sender.sendMessage("§7Поддерживаются также /u ce, /u ss, /u tf с исходными аргументами.");
}
private String route(String input){
return switch(input){
case "pig","frog","repair","trader" -> input.equals("trader")?"enchantmentforge":"swampsky";
default -> ROUTES.get(input);
};
}
private String forwarded(String action,String[] args){
return switch(action){
case "pig" -> "swampsky spawn flying_pig";
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
if(action.equals("status")||action.equals("check")||action.equals("list")){status(sender);return true;}
if(action.equals("shield")||action.equals("shields")){
if(combatShields==null){sender.sendMessage("§cМодуль щитов не запущен.");return true;}
return combatShields.command(sender,Arrays.copyOfRange(args,1,args.length));
}
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
return List.of("help","status","pig","frog","repair","trader","p","team","tf","ce","ss","map","trap","shield")
.stream().filter(x->x.startsWith(prefix)).toList();
}
String action=args[0].toLowerCase(Locale.ROOT);
if(action.equals("shield")||action.equals("shields")){
if(args.length==2)return List.of("give").stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
if(args.length==4&&args[1].equalsIgnoreCase("give"))return List.of("wither","turtle","crusher").stream().filter(x->x.startsWith(args[3].toLowerCase(Locale.ROOT))).toList();
return List.of();
}
if(action.equals("trap")||action.equals("trapka")){
if(args.length==2)return List.of("give","help").stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
if(args.length==3&&args[1].equalsIgnoreCase("give"))
return Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName).filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
return List.of();
}
String target=route(action);
if(target==null||!enabled(OWNERS.get(target)))return List.of();
try{
PluginCommand sub=getCommand(target);
if(sub==null)return List.of();
String[] rest=Arrays.copyOfRange(args,1,args.length);
return sub.tabComplete(sender,target,rest);
}catch(Exception ex){return List.of();}
}
@Override public void onDisable(){
if(combatShields!=null){try{combatShields.disable();}catch(Throwable error){getLogger().warning("Shield cleanup failed: "+error);}combatShields=null;}
if(trapItems!=null){try{trapItems.disable();}catch(Throwable failure){getLogger().warning("PvP trap cleanup failed: "+failure.getMessage());}trapItems=null;}
for(int i=enabledModules.size()-1;i>=0;i--){
ModuleBridge module=enabledModules.get(i);
try{module.onDisable();}
catch(Throwable failure){getLogger().severe("Shutdown failed for "+module.getName()+": "+failure);}
}
enabledModules.clear();failedModules.clear();
}
}
