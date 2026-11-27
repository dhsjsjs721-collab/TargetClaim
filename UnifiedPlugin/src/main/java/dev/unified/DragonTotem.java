package dev.unified;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

final class DragonTotem implements Listener {
private final UnifiedPlugin host;
private final File registry;
private final NamespacedKey key=new NamespacedKey("minecraftunified","dragon_totem"), serialKey=new NamespacedKey("minecraftunified","dragon_totem_serial"), droppedKey=new NamespacedKey("minecraftunified","dragon_totem_dropped");
private static final long RESCUE_MS=30*60*1000L;
private String serial;
private long ready;
private boolean available=true;
private final Map<UUID,Item> dropped=new HashMap<>();
private long ticks;
DragonTotem(UnifiedPlugin host){this.host=host;registry=new File(host.getDataFolder(),"dragon-totem.yml");}
void enable(){
if(registry.isFile()){
try{YamlConfiguration y=new YamlConfiguration();y.load(registry);serial=y.getString("serial");ready=y.getLong("ready");if(serial==null||!UUID.fromString(serial).toString().equals(serial))throw new IllegalStateException("Invalid serial");}
catch(Exception e){available=false;host.getLogger().severe("Dragon totem registry invalid, creation disabled: "+e.getMessage());}
}
ShapelessRecipe recipe=new ShapelessRecipe(key,item("preview"));recipe.addIngredient(Material.DRAGON_EGG);recipe.addIngredient(Material.TOTEM_OF_UNDYING);
Bukkit.removeRecipe(key);Bukkit.addRecipe(recipe);
Bukkit.getPluginManager().registerEvents(this,host);
for(World w:Bukkit.getWorlds())for(Item i:w.getEntitiesByClass(Item.class))protect(i);
Bukkit.getScheduler().runTaskTimer(host,this::tick,5L,5L);
}
private boolean save(){
try{YamlConfiguration y=new YamlConfiguration();y.set("serial",serial);y.set("ready",ready);Path tmp=registry.toPath().resolveSibling(registry.getName()+".tmp");y.save(tmp.toFile());
try{Files.move(tmp,registry.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,registry.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;
}catch(IOException e){host.getLogger().severe("Dragon totem save failed: "+e.getMessage());return false;}
}
private ItemStack item(String id){
ItemStack stack=new ItemStack(Material.ECHO_SHARD);ItemMeta m=stack.getItemMeta();
m.setDisplayName("§5§lСердце Дракона");m.setItemModel(new NamespacedKey("minecraft","totem_of_undying"));m.setMaxStackSize(1);m.setEnchantmentGlintOverride(true);
m.setLore(List.of("§dЕдинственный драконий тотем сервера","§7Спасает от смертельного урона и пустоты","§7Перезарядка: 30 минут","§7Не сгорает и не исчезает на земле","§7Активен в любой руке"));
m.getPersistentDataContainer().set(serialKey,PersistentDataType.STRING,id);stack.setItemMeta(m);return stack;
}
private boolean valid(ItemStack i){return available&&serial!=null&&i!=null&&i.getType()==Material.ECHO_SHARD&&i.getAmount()==1&&i.hasItemMeta()&&serial.equals(i.getItemMeta().getPersistentDataContainer().get(serialKey,PersistentDataType.STRING));}
private boolean isRecipe(Recipe r){return r instanceof Keyed k&&k.getKey().equals(key);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void autoCraft(CrafterCraftEvent e){if(isRecipe(e.getRecipe()))e.setCancelled(true);}
@EventHandler public void prepare(PrepareItemCraftEvent e){if(isRecipe(e.getRecipe())&&(!available||serial!=null))e.getInventory().setResult(null);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void craft(CraftItemEvent e){
if(!isRecipe(e.getRecipe()))return;
e.setCancelled(true);
if(!(e.getWhoClicked() instanceof Player p)||p.getGameMode()!=GameMode.SURVIVAL||host.seasonLocked(p))return;
if(!available||serial!=null){p.sendMessage("§cДраконий тотем уже создан или недоступен.");return;}
if(e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.RIGHT||e.getCursor()!=null&&!e.getCursor().getType().isAir()){p.sendMessage("§7Для создания нажми на результат пустым курсором.");return;}
ItemStack[] matrix=e.getInventory().getMatrix();int egg=-1,totem=-1;
for(int n=0;n<matrix.length;n++){ItemStack i=matrix[n];if(i==null||i.getType().isAir())continue;if(i.getType()==Material.DRAGON_EGG&&egg<0)egg=n;else if(i.getType()==Material.TOTEM_OF_UNDYING&&totem<0)totem=n;else return;}
if(egg<0||totem<0)return;
serial=UUID.randomUUID().toString();ready=0;
if(!save()){serial=null;return;}
for(int n:new int[]{egg,totem}){ItemStack i=matrix[n];if(i.getAmount()==1)matrix[n]=null;else{i=i.clone();i.setAmount(i.getAmount()-1);matrix[n]=i;}}
e.getInventory().setMatrix(matrix);e.getInventory().setResult(null);p.setItemOnCursor(item(serial));p.updateInventory();
Bukkit.broadcastMessage("§5§lСердце Дракона §r§dсоздано игроком "+p.getName()+"!");
}
private void protect(Item i){
if(!valid(i.getItemStack()))return;
i.setInvulnerable(true);i.setUnlimitedLifetime(true);
var p=i.getPersistentDataContainer();if(!p.has(droppedKey,PersistentDataType.LONG))p.set(droppedKey,PersistentDataType.LONG,System.currentTimeMillis());
dropped.put(i.getUniqueId(),i);
}
@EventHandler(ignoreCancelled=true) public void spawn(ItemSpawnEvent e){protect(e.getEntity());}
@EventHandler public void load(EntitiesLoadEvent e){for(Entity entity:e.getEntities())if(entity instanceof Item i)protect(i);}
@EventHandler(ignoreCancelled=true) public void despawn(ItemDespawnEvent e){if(valid(e.getEntity().getItemStack()))e.setCancelled(true);}
@EventHandler(ignoreCancelled=true) public void merge(ItemMergeEvent e){if(valid(e.getEntity().getItemStack())||valid(e.getTarget().getItemStack()))e.setCancelled(true);}
private Location safe(){
for(World w:Bukkit.getWorlds()){
if(w.getEnvironment()!=World.Environment.NORMAL||w.getName().equals(TestWorld.WORLD))continue;
Location s=w.getSpawnLocation();int sx=s.getBlockX(),sz=s.getBlockZ();
w.getChunkAt(sx>>4,sz>>4);
for(int radius=0;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
int x=sx+dx,z=sz+dz;if(!w.isChunkLoaded(x>>4,z>>4))continue;
int y=w.getHighestBlockYAt(x,z);if(y+2>=w.getMaxHeight())continue;
Block floor=w.getBlockAt(x,y,z),feet=w.getBlockAt(x,y+1,z),head=w.getBlockAt(x,y+2,z);
Material m=floor.getType();
if(!m.isSolid()||m==Material.MAGMA_BLOCK||m==Material.CACTUS||m==Material.POWDER_SNOW||m==Material.CAMPFIRE||m==Material.SOUL_CAMPFIRE||!feet.getType().isAir()||!head.getType().isAir())continue;
Location l=new Location(w,x+.5,y+1,z+.5);if(w.getWorldBorder().isInside(l))return l;
}
}
return null;
}
private boolean returnItem(Item i){Location s=safe();if(s==null||!i.teleport(s))return false;i.setVelocity(new Vector());i.setGravity(true);return true;}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void damage(EntityDamageEvent e){
if(e.getEntity() instanceof Item i&&valid(i.getItemStack())){e.setCancelled(true);if(e.getCause()==EntityDamageEvent.DamageCause.VOID)returnItem(i);return;}
if(!(e.getEntity() instanceof Player p)||p.getGameMode()!=GameMode.SURVIVAL||host.seasonLocked(p)||!valid(p.getInventory().getItemInMainHand())&&!valid(p.getInventory().getItemInOffHand()))return;
boolean voidHit=e.getCause()==EntityDamageEvent.DamageCause.VOID;
if(!voidHit&&e.getFinalDamage()<p.getHealth())return;
long now=System.currentTimeMillis();if(now<ready)return;
Location escape=voidHit?safe():null;if(voidHit&&escape==null)return;
long before=ready;ready=now+RESCUE_MS;if(!save()){ready=before;return;}
if(voidHit&&!p.teleport(escape)){ready=before;save();return;}
e.setCancelled(true);p.setFallDistance(0);p.setVelocity(new Vector());p.setFireTicks(0);p.setHealth(Math.min(p.getMaxHealth(),Math.max(1,p.getHealth())));
p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,200,1));p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,1));p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,600,0));
p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),80,.5,.8,.5,.15);p.getWorld().playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,1f,.85f);
p.sendMessage("§dСердце Дракона спасло тебя. Перезарядка: 30 минут.");
}
private void tick(){
ticks+=5;
for(Iterator<Item> it=dropped.values().iterator();it.hasNext();){
Item i=it.next();if(!i.isValid()||i.isDead()||!valid(i.getItemStack())){it.remove();continue;}
if(i.getLocation().getY()<i.getWorld().getMinHeight()-4){returnItem(i);continue;}
if(ticks%60!=0)continue;
if(System.currentTimeMillis()-i.getPersistentDataContainer().getOrDefault(droppedKey,PersistentDataType.LONG,System.currentTimeMillis())<60000)continue;
Location l=i.getLocation().add(0,.35,0);World w=l.getWorld();
w.spawnParticle(Particle.END_ROD,l,24,.65,.5,.65,.03);w.spawnParticle(Particle.PORTAL,l,32,.7,.6,.7,.12);w.playSound(l,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.65f,.75f);
}
}
}
