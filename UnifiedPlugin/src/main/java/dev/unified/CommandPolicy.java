package dev.unified;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.Bukkit;
import java.util.*;

final class CommandPolicy implements Listener {
private final UnifiedPlugin host;
private static final Set<String> PUBLIC_ROOTS=Set.of("u","clan","msg","r","skin","disc","ua","voicechat");
private static final Set<String> MODULES=Set.of("unified","p","team","unifiedmsg","unifiedreply","skin","disc","trap","talismanforge","enchantmentforge","swampsky","imagemap");
private static final Set<String> SAFE_CLAIM=Set.of("","list","info","border","borders","upgrade","remove","cancel","create","help","recipe");
private static final Set<String> SAFE_CLAN=Set.of("","create","colors","invite","accept","deny","leave","kick","disband","pvp","info","menu","color");
private static final Set<String> SAFE_RELIC=Set.of("","list","status","link","restore","help");
CommandPolicy(UnifiedPlugin h){host=h;}
static String norm(String s){String v=s.toLowerCase(Locale.ROOT);return v.startsWith("minecraftunified:")?v.substring("minecraftunified:".length()):v;}
static boolean safe(String root,String[] args){
root=norm(root);String a=args.length==0?"":args[0].toLowerCase(Locale.ROOT);String b=args.length<2?"":args[1].toLowerCase(Locale.ROOT);
if(Set.of("skin","disc","msg","tell","w","pm","unifiedmsg","r","reply","unifiedreply","ua","voicechat").contains(root))return !root.equals("ua")||Set.of("","remember","status").contains(a);
if(Set.of("clan","clans","team").contains(root))return SAFE_CLAN.contains(a);
if(Set.of("p","claim","privat").contains(root))return SAFE_CLAIM.contains(a)||a.matches("[1-9][0-9]*");
if(Set.of("talismanforge","tf","talismans").contains(root))return SAFE_RELIC.contains(a);
if(Set.of("enchantmentforge","ce").contains(root))return Set.of("","help","apply").contains(a);
if(Set.of("swampsky","ss","wildlife").contains(root))return Set.of("","help","repair").contains(a);
if(Set.of("imagemap","im").contains(root))return !a.equals("file");
if(root.equals("trap"))return Set.of("","help").contains(a);
if(!Set.of("u","unified").contains(root))return false;
return switch(a){
case "","help","guide","recipes","skin","disc","video","dragon" -> true;
case "claim","claims","p","private" -> SAFE_CLAIM.contains(b)||b.matches("[1-9][0-9]*");
case "team","teams","clan","clans" -> SAFE_CLAN.contains(b);
case "talisman","talismans","tf" -> SAFE_RELIC.contains(b);
case "enchant","enchantments","ce" -> Set.of("","help","apply").contains(b);
case "wildlife","swamp","swampsky","ss" -> Set.of("","help","repair").contains(b);
case "repair" -> true;
case "map","image","imagemap" -> !b.equals("file");
case "shop" -> Set.of("","name","style","skin").contains(b);
case "trap","trapka" -> Set.of("","help").contains(b);
case "mining" -> b.isEmpty();
default -> false;
};}
private boolean allowed(CommandSender s,String root,String[] args){if(!(s instanceof Player p)||p.isOp())return true;return safe(root,args)&&host.seasonAllows(p,norm(root),args);}
void enable(){
Bukkit.getPluginManager().registerEvents(this,host);
for(String name:MODULES){PluginCommand c=host.getCommand(name);if(c==null)continue;CommandExecutor old=c.getExecutor();TabCompleter tabs=c.getTabCompleter();c.setExecutor((sender,cmd,label,args)->{if(!allowed(sender,name,args)){sender.sendMessage("§cКоманда недоступна.");return true;}return old.onCommand(sender,cmd,label,args);});
c.setTabCompleter((sender,cmd,alias,args)->{
List<String> result=tabs!=null?tabs.onTabComplete(sender,cmd,alias,args):old instanceof TabCompleter tc?tc.onTabComplete(sender,cmd,alias,args):List.of();
if(sender.isOp()||!(sender instanceof Player))return result==null?List.of():result;
if(result==null||args.length==0)return List.of();
return result.stream().filter(candidate->{String[] copy=args.clone();copy[copy.length-1]=candidate;return allowed(sender,name,copy);}).toList();
});}
}
@EventHandler(priority=EventPriority.LOWEST) public void preprocess(PlayerCommandPreprocessEvent e){if(e.getPlayer().isOp())return;String[] split=e.getMessage().substring(1).trim().split("\\s+");String root=norm(split[0]);String[] args=Arrays.copyOfRange(split,1,split.length);if(!allowed(e.getPlayer(),root,args)){e.setCancelled(true);e.getPlayer().sendMessage("§cКоманда недоступна.");}}
@EventHandler(priority=EventPriority.HIGHEST) public void list(PlayerCommandSendEvent e){if(!e.getPlayer().isOp())e.getCommands().removeIf(x->!PUBLIC_ROOTS.contains(x));}
static boolean selftest(){return safe("u",new String[]{"clan","invite"})&&!safe("u",new String[]{"give"})&&!safe("minecraftunified:tf",new String[]{"give"})&&!safe("u",new String[]{"map","file"})&&safe("u",new String[]{"recipes"})&&!safe("u",new String[]{"spawn"})&&!safe("s",new String[0]);}
}
