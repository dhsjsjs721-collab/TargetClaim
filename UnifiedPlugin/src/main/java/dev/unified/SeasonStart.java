package dev.unified;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.*;

final class SeasonStart implements Listener {
enum Phase { WAITING, SCOUT, LIVE }
static final long SCOUT_MS=600_000L;
private final UnifiedPlugin host;
private final NamespacedKey marker;
private Phase phase;
private long until;
private final Set<UUID> changing=new HashSet<>();
SeasonStart(UnifiedPlugin h){host=h;marker=new NamespacedKey(h,"season_scout");}
void enable(){
try{phase=Phase.valueOf(host.getConfig().getString("season.phase","WAITING"));}catch(Exception ex){phase=Phase.WAITING;}
until=host.getConfig().getLong("season.until",0);
if(phase==Phase.SCOUT&&until<=System.currentTimeMillis()){phase=Phase.LIVE;save();}
Bukkit.getPluginManager().registerEvents(this,host);
for(Player p:Bukkit.getOnlinePlayers())apply(p);
Bukkit.getScheduler().runTaskTimer(host,this::tick,20L,20L);
host.getLogger().info("Season phase: "+phase+"; console /s (waiting), /a (10-minute exploration).");
}
boolean locked(Player p){return phase!=Phase.LIVE&&!p.isOp();}
boolean waiting(Player p){return phase==Phase.WAITING&&!p.isOp();}
boolean active(){return phase!=Phase.LIVE;}
boolean allowCommand(Player p,String root,String[] args){
if(!locked(p))return true;
String a=args.length==0?"":args[0].toLowerCase(Locale.ROOT);
if(Set.of("ua","msg","tell","w","pm","unifiedmsg","r","reply","unifiedreply","voicechat").contains(root))return true;
if(phase==Phase.WAITING)return Set.of("u","unified").contains(root)&&(a.isEmpty()||a.equals("help"));
if(Set.of("clan","clans","team").contains(root))return true;
return Set.of("u","unified").contains(root)&&(a.isEmpty()||Set.of("help","team","clan","teams","clans","recipes","guide","dragon").contains(a));
}
private void save(){host.getConfig().set("season.phase",phase.name());host.getConfig().set("season.until",until);host.saveConfig();}
boolean command(CommandSender sender,boolean admit){
if(!(sender instanceof ConsoleCommandSender)&&!(sender instanceof RemoteConsoleCommandSender)){sender.sendMessage("§cТолько консоль сервера.");return true;}
if(admit&&phase!=Phase.WAITING){sender.sendMessage("§cСначала /s. Текущий режим: "+phase);return true;}
phase=admit?Phase.SCOUT:Phase.WAITING;until=admit?System.currentTimeMillis()+SCOUT_MS:0;save();
for(Player p:Bukkit.getOnlinePlayers()){p.closeInventory();apply(p);}
sender.sendMessage("Season phase -> "+phase.name());
Bukkit.broadcastMessage(admit?"§dРазведка началась! §f10 минут полёта и создания кланов.":"§dОжидание запуска. §7Администратор откроет сезон.");
return true;
}
private void title(Player p,String head,String tail){p.showTitle(Title.title(Component.text(head,TextColor.color(0xD8AEFF)),Component.text(tail,TextColor.color(0xF3CA80)),Title.Times.times(Duration.ZERO,Duration.ofMillis(1400),Duration.ofMillis(250))));}
private void apply(Player p){
if(p.isOp())return;
changing.add(p.getUniqueId());
try{
if(phase==Phase.LIVE){
if(!p.getPersistentDataContainer().has(marker))return;
Location safe=landing(p);p.teleport(safe);p.setFlying(false);p.setAllowFlight(false);p.setFlySpeed(.1f);p.setFallDistance(0);p.setGameMode(GameMode.SURVIVAL);p.getPersistentDataContainer().remove(marker);
title(p,"СЕЗОН НАЧАЛСЯ","Удачи в выживании!");return;
}
p.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);p.leaveVehicle();p.setGliding(false);p.setGameMode(GameMode.ADVENTURE);p.setFallDistance(0);
p.setAllowFlight(phase==Phase.SCOUT);p.setFlySpeed(phase==Phase.SCOUT?.25f:.1f);p.setFlying(phase==Phase.SCOUT);
if(phase==Phase.WAITING){p.teleport(p.getWorld().getSpawnLocation());p.setVelocity(new Vector());title(p,"LAND OF LOSERS","Дождитесь запуска сервера");}
else title(p,"РАЗВЕДКА МИРА","10 минут · /clan");
}finally{changing.remove(p.getUniqueId());}
}
private Location landing(Player p){
Location l=p.getLocation();World w=l.getWorld();if(w!=null&&w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)){
int top=w.getHighestBlockYAt(l,HeightMap.MOTION_BLOCKING_NO_LEAVES);Location t=new Location(w,l.getBlockX()+.5,top+1,l.getBlockZ()+.5,l.getYaw(),l.getPitch());
if(top>=w.getMinHeight()&&top+2<w.getMaxHeight()&&t.getBlock().isPassable()&&!t.getBlock().isLiquid()&&t.clone().add(0,1,0).getBlock().isPassable()&&!t.clone().add(0,1,0).getBlock().isLiquid()&&w.getBlockAt(l.getBlockX(),top,l.getBlockZ()).getType().isSolid()&&!Set.of(Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW).contains(w.getBlockAt(l.getBlockX(),top,l.getBlockZ()).getType()))return t;}
return Bukkit.getWorlds().getFirst().getSpawnLocation();
}
private void tick(){
if(phase==Phase.SCOUT&&System.currentTimeMillis()>=until){phase=Phase.LIVE;until=0;save();for(Player p:Bukkit.getOnlinePlayers())apply(p);Bukkit.broadcastMessage("§6§lСТАРТ! §fВыживание открыто.");return;}
for(Player p:Bukkit.getOnlinePlayers())if(locked(p)){
if(p.getGameMode()!=GameMode.ADVENTURE){changing.add(p.getUniqueId());try{p.setGameMode(GameMode.ADVENTURE);}finally{changing.remove(p.getUniqueId());}}
if(phase==Phase.WAITING){p.setFlying(false);p.setAllowFlight(false);title(p,"LAND OF LOSERS","Дождитесь запуска сервера");}
else{if(!p.getAllowFlight())p.setAllowFlight(true);p.setFlySpeed(.25f);long sec=Math.max(0,(until-System.currentTimeMillis()+999)/1000);p.sendActionBar(Component.text(String.format("Разведка · %02d:%02d · /clan",sec/60,sec%60),TextColor.color(0xD8AEFF)));}}
}
@EventHandler(priority=EventPriority.HIGHEST) public void join(PlayerJoinEvent e){apply(e.getPlayer());}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent e){if(waiting(e.getPlayer())&&e.getTo()!=null&&!changing.contains(e.getPlayer().getUniqueId())&&e.hasChangedPosition())e.setTo(e.getFrom());}
@EventHandler(priority=EventPriority.LOWEST) public void damage(EntityDamageEvent e){if(e.getEntity() instanceof Player p&&locked(p))e.setCancelled(true);if(e instanceof EntityDamageByEntityEvent d){Entity x=d.getDamager();if(x instanceof Player p&&locked(p)||x instanceof Projectile q&&q.getShooter() instanceof Player shooter&&locked(shooter))e.setCancelled(true);}}
@EventHandler(priority=EventPriority.LOWEST) public void breakBlock(BlockBreakEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void place(BlockPlaceEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void interact(PlayerInteractEvent e){if(locked(e.getPlayer())){e.setCancelled(true);e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);}}
@EventHandler(priority=EventPriority.LOWEST) public void entity(PlayerInteractEntityEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void drop(PlayerDropItemEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p&&locked(p))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void food(FoodLevelChangeEvent e){if(e.getEntity() instanceof Player p&&locked(p))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void consume(PlayerItemConsumeEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void fish(PlayerFishEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void shoot(EntityShootBowEvent e){if(e.getEntity() instanceof Player p&&locked(p))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void bucketEmpty(PlayerBucketEmptyEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void bucketFill(PlayerBucketFillEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void portal(PlayerPortalEvent e){if(locked(e.getPlayer()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void mount(EntityMountEvent e){if(e.getEntity() instanceof Player p&&locked(p))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void mode(PlayerGameModeChangeEvent e){if(locked(e.getPlayer())&&!changing.contains(e.getPlayer().getUniqueId()))e.setCancelled(true);}
private boolean safeMenu(Inventory i){var h=i.getHolder();if(h==null)return false;String n=h.getClass().getName();return n.equals("dev.targetclaim.TargetClaimPlugin$ClaimMenu")||n.equals("dev.unified.RecipeGuide$Menu");}
@EventHandler(priority=EventPriority.LOWEST) public void inventory(InventoryOpenEvent e){if(e.getPlayer() instanceof Player p&&locked(p)&&!safeMenu(e.getInventory()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void craft(CraftItemEvent e){if(e.getWhoClicked() instanceof Player p&&locked(p))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void inventoryClick(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p&&locked(p)&&!safeMenu(e.getView().getTopInventory()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void inventoryDrag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p&&locked(p))e.setCancelled(true);}
}
