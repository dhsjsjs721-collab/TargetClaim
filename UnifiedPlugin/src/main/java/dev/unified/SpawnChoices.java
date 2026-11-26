package dev.unified;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.util.*;

final class SpawnChoices implements Listener {
private static final int LIMIT=6;
private static final long COMBAT_MS=15000;
private final UnifiedPlugin host;
private final SeasonStart season;
private final NamespacedKey itemKey,deathKey;
private final Map<UUID,Long> combat=new HashMap<>();
private final Map<UUID,Cast> casts=new HashMap<>();
private record Point(String type,int index,Location block){}
private record Cast(Location from,int slot,ItemStack item,Point point,long started){}
private static final class Menu implements InventoryHolder {
final UUID owner;final boolean death;final int held;final ItemStack source;
final Map<Integer,Point> choices=new HashMap<>();
Inventory inv;
Menu(Player p,boolean death){owner=p.getUniqueId();this.death=death;held=p.getInventory().getHeldItemSlot();source=death?null:p.getInventory().getItemInMainHand().clone();}
public Inventory getInventory(){return inv;}
}
SpawnChoices(UnifiedPlugin h,SeasonStart s){host=h;season=s;itemKey=new NamespacedKey(h,"return_scroll");deathKey=new NamespacedKey(h,"spawn_pending");}
static ItemStack item(){ItemStack i=new ItemStack(Material.PAPER);var m=i.getItemMeta();m.displayName(Component.text("Свиток возвращения",net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));m.setItemModel(new NamespacedKey("minecraft","ender_eye"));m.lore(List.of(Component.text("ПКМ — выбрать точку телепортации",net.kyori.adventure.text.format.NamedTextColor.GRAY),Component.text("Одна штука · 5 секунд без движения",net.kyori.adventure.text.format.NamedTextColor.GRAY)));m.getPersistentDataContainer().set(new NamespacedKey("minecraftunified","return_scroll"),PersistentDataType.BYTE,(byte)1);i.setItemMeta(m);return i;}
static void recipe(UnifiedPlugin h){ShapedRecipe r=new ShapedRecipe(new NamespacedKey(h,"return_scroll"),item());r.shape(" K ","KRK"," K ");r.setIngredient('K',Material.DRIED_KELP);r.setIngredient('R',Material.REDSTONE);Bukkit.addRecipe(r);}
void enable(){Bukkit.getPluginManager().registerEvents(this,host);Bukkit.getScheduler().runTaskTimer(host,this::tick,5L,5L);host.getLogger().info("SELFTEST spawn choices OK: 6 beds, 6 anchors, return scroll recipe");}
private boolean test(Player p){return p.getWorld().getName().equals(TestWorld.WORLD);}
private boolean live(Player p){return !season.active()||test(p)&&p.isOp();}
private boolean scroll(ItemStack i){return i!=null&&i.getType()==Material.PAPER&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(itemKey,PersistentDataType.BYTE);}
private boolean waiting(Player p){return p.getPersistentDataContainer().has(deathKey,PersistentDataType.BYTE);}
private NamespacedKey key(String type,int i){return new NamespacedKey(host,"respawn_"+type+"_"+i);}
private String encode(Location l){return l.getWorld().getUID()+","+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ();}
private Location decode(String data){if(data==null)return null;try{String[] v=data.split(",");World w=Bukkit.getWorld(UUID.fromString(v[0]));return w==null?null:new Location(w,Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]));}catch(Exception ex){return null;}}
private Location stored(Player p,String type,int i){return decode(p.getPersistentDataContainer().get(key(type,i),PersistentDataType.STRING));}
private void remember(Player p,Location l,String type){
if(l==null||!l.isWorldLoaded()||l.getWorld().getName().equals(TestWorld.WORLD))return;
Block b=l.getBlock();if(type.equals("bed")&&b.getBlockData() instanceof Bed bed){if(bed.getPart()==Bed.Part.FOOT)b=b.getRelative(bed.getFacing());}else if(!type.equals("anchor")||b.getType()!=Material.RESPAWN_ANCHOR)return;
l=b.getLocation();String val=encode(l);int free=-1;
for(int i=0;i<LIMIT;i++){String old=p.getPersistentDataContainer().get(key(type,i),PersistentDataType.STRING);if(val.equals(old))return;if(old==null&&free<0)free=i;else if(old!=null&&free<0){Location at=decode(old);if(at!=null&&at.isWorldLoaded()&&at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)&&!validBlock(at,type,false))free=i;}}
if(free<0){p.sendMessage("§eЛимит 6 точек этого типа. Удали старую через Shift+ПКМ со свитком → ПКМ по точке.");return;}
p.getPersistentDataContainer().set(key(type,free),PersistentDataType.STRING,val);p.sendMessage("§aСохранена точка: "+(type.equals("bed")?"Кровать ":"Якорь ")+(free+1));
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void setSpawn(PlayerSetSpawnEvent e){if(!live(e.getPlayer())||test(e.getPlayer()))return;switch(e.getCause()){case BED -> remember(e.getPlayer(),e.getLocation(),"bed");case RESPAWN_ANCHOR -> remember(e.getPlayer(),e.getLocation(),"anchor");default -> {}}}
private boolean validBlock(Location l,String type,boolean charged){
if(l==null||!l.isWorldLoaded())return false;
Block b=l.getBlock();
if(type.equals("bed"))return l.getWorld().getEnvironment()==World.Environment.NORMAL&&b.getBlockData() instanceof Bed bed&&bed.getPart()==Bed.Part.HEAD&&b.getRelative(bed.getFacing().getOppositeFace()).getBlockData() instanceof Bed foot&&foot.getPart()==Bed.Part.FOOT&&foot.getFacing()==bed.getFacing();
return l.getWorld().getEnvironment()==World.Environment.NETHER&&b.getBlockData() instanceof RespawnAnchor anchor&&(!charged||anchor.getCharges()>0);
}
private boolean load(Location l){if(l==null||!l.isWorldLoaded())return false;World w=l.getWorld();int x=l.getBlockX()>>4,z=l.getBlockZ()>>4;if(!w.getWorldBorder().isInside(l))return false;if(!w.isChunkLoaded(x,z)){if(!w.isChunkGenerated(x,z))return false;w.getChunkAt(x,z);}return true;}
private boolean safe(Location l){World w=l.getWorld();if(l.getBlockY()<=w.getMinHeight()||l.getBlockY()+1>=w.getMaxHeight()||!w.getWorldBorder().isInside(l))return false;Block feet=l.getBlock(),head=feet.getRelative(org.bukkit.block.BlockFace.UP),floor=feet.getRelative(org.bukkit.block.BlockFace.DOWN);return feet.isPassable()&&!feet.isLiquid()&&head.isPassable()&&!head.isLiquid()&&floor.getType().isSolid()&&!Set.of(Material.CACTUS,Material.MAGMA_BLOCK,Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.POWDER_SNOW,Material.FIRE,Material.SOUL_FIRE).contains(floor.getType())&&!Set.of(Material.FIRE,Material.SOUL_FIRE,Material.POWDER_SNOW,Material.SWEET_BERRY_BUSH).contains(feet.getType());}
private Location centered(Location l){return new Location(l.getWorld(),l.getBlockX()+.5,l.getBlockY(),l.getBlockZ()+.5,l.getYaw(),l.getPitch());}
private Location near(Location origin,boolean exact){
if(!load(origin))return null;Location l=centered(origin);if(exact&&safe(l))return l;
for(int radius=1;radius<=2;radius++)for(int dy:new int[]{0,1,-1,2})for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;Location n=l.clone().add(dx,dy,dz);if(load(n)&&safe(n))return n;}return null;
}
private Location worldSpawn(Player p){return test(p)?p.getWorld().getSpawnLocation():Bukkit.getWorlds().getFirst().getSpawnLocation();}
private Location destination(Player p,Point point){
if(point.type.equals("world")){Location l=worldSpawn(p);Location safe=near(l,true);return safe==null?l:safe;}
if(point.type.equals("clan")){Location chosen=season.chosen(p);return chosen==null?null:near(chosen,true);}
Location l=stored(p,point.type,point.index);if(l==null||!encode(l).equals(encode(point.block))||!load(l)||!validBlock(l,point.type,true))return null;
return near(l,false);
}
private ItemStack icon(Material material,String name,String... lore){ItemStack i=new ItemStack(material);var m=i.getItemMeta();m.setDisplayName(name);m.setLore(List.of(lore));i.setItemMeta(m);return i;}
private void add(Menu m,int slot,Point point,Material mat,String name){Location l=point.block;m.choices.put(slot,point);m.inv.setItem(slot,icon(mat,name,l==null?"§7Точка мира":"§7"+l.getWorld().getName()+" · "+l.getBlockX()+", "+l.getBlockY()+", "+l.getBlockZ(),m.death?"§eЛКМ — возродиться":"§eЛКМ — телепортироваться",point.index>=0?"§7ПКМ — забыть точку":"§7"));}
private void open(Player p,boolean death){
if(!p.isOnline()||p.isDead()||!live(p)||death&&!waiting(p)||!death&&!scroll(p.getInventory().getItemInMainHand()))return;
Menu m=new Menu(p,death);m.inv=Bukkit.createInventory(m,36,death?"Выбери место возрождения":"Свиток · выбери точку");
add(m,4,new Point("world",-1,worldSpawn(p)),Material.COMPASS,"§aМировой спавн");
Location clan=test(p)?null:season.chosen(p);if(clan!=null)add(m,2,new Point("clan",-1,clan),Material.BEACON,"§dСпавн клана");else m.inv.setItem(2,icon(Material.GRAY_DYE,"§7Нет точки клана"));
if(!test(p))for(int i=0;i<LIMIT;i++){Location bed=stored(p,"bed",i),anchor=stored(p,"anchor",i);if(bed!=null)add(m,10+i,new Point("bed",i,bed),Material.RED_BED,"§bКровать "+(i+1));if(anchor!=null)add(m,19+i,new Point("anchor",i,anchor),Material.RESPAWN_ANCHOR,"§5Якорь "+(i+1));}
m.inv.setItem(31,icon(Material.BARRIER,death?"§7Остаться на мировом спавне":"§7Отмена"));p.openInventory(m.inv);
}
@EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent e){Player p=e.getPlayer();if(test(p)||season.active()||e.getRespawnReason()!=PlayerRespawnEvent.RespawnReason.DEATH)return;
e.setRespawnLocation(worldSpawn(p));p.getPersistentDataContainer().set(deathKey,PersistentDataType.BYTE,(byte)1);
Bukkit.getScheduler().runTaskLater(host,()->open(p,true),2L);
}
@EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){Player p=e.getPlayer();Bukkit.getScheduler().runTaskLater(host,()->{if(!p.isOnline()||!live(p)||test(p))return;if(waiting(p))open(p,true);},10L);}
@EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||!scroll(e.getItem()))return;
e.setCancelled(true);e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);Player p=e.getPlayer();
if(!live(p)||waiting(p)||p.isSleeping()||p.isInsideVehicle()||p.isGliding()||p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE)return;
if(inCombat(p)){p.sendMessage("§cПодожди 15 секунд после боя.");return;}if(casts.containsKey(p.getUniqueId()))return;open(p,false);}
private boolean inCombat(Player p){return System.currentTimeMillis()-combat.getOrDefault(p.getUniqueId(),0L)<COMBAT_MS;}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void damage(EntityDamageEvent e){if(e.getFinalDamage()<=0)return;if(e.getEntity() instanceof Player p){combat.put(p.getUniqueId(),System.currentTimeMillis());cancel(p);}if(e instanceof EntityDamageByEntityEvent d){Player p=d.getDamager() instanceof Player a?a:d.getDamager() instanceof Projectile q&&q.getShooter() instanceof Player a?a:null;if(p!=null){combat.put(p.getUniqueId(),System.currentTimeMillis());cancel(p);}}}
private void cancel(Player p){if(casts.remove(p.getUniqueId())!=null)p.sendMessage("§cТелепортация отменена.");}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void moved(PlayerMoveEvent e){Cast cast=casts.get(e.getPlayer().getUniqueId());if(cast!=null&&(e.getTo()==null||e.getTo().getWorld()!=cast.from.getWorld()||e.getTo().distanceSquared(cast.from)>.09))cancel(e.getPlayer());}
@EventHandler public void quit(PlayerQuitEvent e){casts.remove(e.getPlayer().getUniqueId());}
@EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);
if(!(e.getWhoClicked() instanceof Player p)||!p.getUniqueId().equals(m.owner)||!live(p)||m.death&&!waiting(p)||!m.death&&!same(p,m.held,m.source)||e.getRawSlot()<0||e.getRawSlot()>=36)return;
if(e.getRawSlot()==31){Bukkit.getScheduler().runTask(host,()->p.closeInventory());return;}
Point point=m.choices.get(e.getRawSlot());if(point==null)return;
if(e.getClick()==ClickType.RIGHT&&point.index>=0){p.getPersistentDataContainer().remove(key(point.type,point.index));Bukkit.getScheduler().runTask(host,()->open(p,m.death));return;}
if(e.getClick()!=ClickType.LEFT)return;
Bukkit.getScheduler().runTask(host,()->{if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=m||!live(p))return;
if(m.death){if(!waiting(p))return;Location dest=destination(p,point);if(dest==null){p.sendMessage("§cТочка уничтожена, разряжена или заблокирована.");return;}if(travel(p,point,dest)){p.getPersistentDataContainer().remove(deathKey);p.closeInventory();}}
else{if(!same(p,m.held,m.source)||inCombat(p)||casts.containsKey(p.getUniqueId()))return;if(destination(p,point)==null){p.sendMessage("§cТочка недоступна.");return;}p.closeInventory();casts.put(p.getUniqueId(),new Cast(p.getLocation(),m.held,m.source,point,System.currentTimeMillis()));p.sendMessage("§dТелепортация через 5 секунд. Не двигайся.");}
});
}
@EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
@EventHandler public void close(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof Menu m&&m.death&&e.getPlayer() instanceof Player p){Bukkit.getScheduler().runTask(host,()->{if(p.isOnline()&&waiting(p)&&!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu)){p.getPersistentDataContainer().remove(deathKey);p.sendMessage("§7Выбран мировой спавн.");}});}}
private boolean same(Player p,int slot,ItemStack original){return original!=null&&p.getInventory().getHeldItemSlot()==slot&&original.equals(p.getInventory().getItemInMainHand());}
private boolean travel(Player p,Point point,Location dest){Location anchor=point.type.equals("anchor")?stored(p,"anchor",point.index):null;RespawnAnchor before=null;if(anchor!=null){if(!validBlock(anchor,"anchor",true))return false;before=(RespawnAnchor)anchor.getBlock().getBlockData();}
if(!p.teleport(dest,PlayerTeleportEvent.TeleportCause.PLUGIN))return false;
if(before!=null){Block block=anchor.getBlock();if(block.getBlockData() instanceof RespawnAnchor now&&now.getCharges()>0){now.setCharges(now.getCharges()-1);block.setBlockData(now,false);}}
p.setFallDistance(0);return true;}
private void tick(){long now=System.currentTimeMillis();combat.entrySet().removeIf(e->now-e.getValue()>COMBAT_MS);for(var entry:new ArrayList<>(casts.entrySet())){Player p=Bukkit.getPlayer(entry.getKey());Cast c=entry.getValue();if(p==null){casts.remove(entry.getKey());continue;}if(!live(p)||p.isDead()||p.isSleeping()||p.isInsideVehicle()||p.isGliding()||!same(p,c.slot,c.item)||inCombat(p)||p.getWorld()!=c.from.getWorld()||p.getLocation().distanceSquared(c.from)>.09){cancel(p);continue;}if(now-c.started<5000)continue;
casts.remove(entry.getKey());Location dest=destination(p,c.point);if(dest==null){p.sendMessage("§cТочка недоступна. Свиток сохранён.");continue;}if(travel(p,c.point,dest)){ItemStack hand=p.getInventory().getItemInMainHand();if(same(p,c.slot,c.item)){hand.setAmount(hand.getAmount()-1);p.getInventory().setItemInMainHand(hand.getAmount()==0?new ItemStack(Material.AIR):hand);}p.sendMessage("§aТелепортация завершена.");}}
}
}
