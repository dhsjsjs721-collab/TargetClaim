package dev.talismanforge;

import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import java.io.*;
import java.util.*;

public final class TalismanForgePlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private enum Talisman {
MAIMING("maiming","§5Талисман Увечья",2),
WRATH("wrath","§cТалисман Кары",0),
DEFIANCE("defiance","§bТалисман Противостояния",2),
LIFE("life","§aТалисман Жизни",2),
POSEIDON("poseidon","§3Талисман Посейдона",1),
HADES("hades","§4Талисман Аида",1),
AGES("ages","§6Талисман Веков",3),
END("end","§5Талисман Энда / Зевса",0);
final String key,name;
final int lives;
Talisman(String k,String n,int l){key=k;name=n;lives=l;}
static Talisman parse(String s){for(Talisman t:values())if(t.key.equalsIgnoreCase(s))return t;return null;}
}
private static final long OPEN_DELAY=180_000L;
private NamespacedKey typeKey,usesKey,chestKey,displayKey,dolphinKey;
private File saveFile;
private final Map<String,Altar> altars=new HashMap<>();
private final Map<UUID,UUID> holograms=new HashMap<>();
private final Map<UUID,Long> cleansed=new HashMap<>();
private final Map<UUID,Talisman> active=new HashMap<>();
private final Map<UUID,Location> safeLocations=new HashMap<>();
private final Map<UUID,UUID> thrownEggs=new HashMap<>();
private final Map<UUID,Long> ghostDolphins=new HashMap<>();
private final Random random=new Random();
private int pulses=0;
private static final class Altar {
UUID id=UUID.randomUUID(),owner,world;
int x,y,z;
long due;
boolean ready;
String index(){return world+":"+x+":"+y+":"+z;}
Location location(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x+.5,y+.5,z+.5);}
}
@Override public void onEnable(){
getDataFolder().mkdirs();saveDefaultConfig();
saveFile=new File(getDataFolder(),"altars.yml");
typeKey=new NamespacedKey(this,"talisman_type");usesKey=new NamespacedKey(this,"uses_left");
chestKey=new NamespacedKey(this,"ritual_chest");displayKey=new NamespacedKey(this,"ritual_display");
dolphinKey=new NamespacedKey(this,"poseidon_dolphin");
loadAltars();
ShapedRecipe r=new ShapedRecipe(new NamespacedKey(this,"ritual_chest"),chestItem());
r.shape("AAA","ATA","AOA");r.setIngredient('A',Material.AMETHYST_SHARD);r.setIngredient('T',Material.TOTEM_OF_UNDYING);r.setIngredient('O',Material.OBSIDIAN);
Bukkit.addRecipe(r);
PluginCommand c=getCommand("talismanforge");if(c!=null){c.setExecutor(this);c.setTabCompleter(this);}
Bukkit.getPluginManager().registerEvents(this,this);
Bukkit.getScheduler().runTaskTimer(this,this::quarterTick,5L,5L);
Bukkit.getScheduler().runTaskTimer(this,this::tick,20L,20L);
getLogger().info("TalismanForge 2.0.0: "+altars.size()+" ritual chests");
}
@Override public void onDisable(){
for(UUID id:holograms.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
for(UUID id:ghostDolphins.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
holograms.clear();ghostDolphins.clear();saveAltars();
}
private ItemStack talisman(Talisman t,int lives){
ItemStack i=new ItemStack(Material.TOTEM_OF_UNDYING);ItemMeta m=i.getItemMeta();
m.setDisplayName(t.name);
m.setItemModel(new NamespacedKey("talismanforge",t.key));
m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,t.key);
m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,Math.max(0,Math.min(t.lives,lives)));
List<String> lore=new ArrayList<>();
lore.add("§8Работает только во второй руке");
lore.add(t.lives==0?"§cНе спасает от обычной смерти":"§7Спасений от смерти: §e"+lives+"/"+t.lives);
lore.add("§bСпасает от падения в бездну");
switch(t){
case MAIMING -> {lore.add("§c−0.5 сердца, Голод I");lore.add("§5Удары: Иссушение I, 5 секунд");}
case WRATH -> {lore.add("§c−2 сердца, без воскрешения");lore.add("§6Сила II, +30% скорость атаки");lore.add("§c+35% прямого урона оружием");}
case DEFIANCE -> {lore.add("§c−2 сердца, Поглощение II");lore.add("§bЩит невозможно временно отключить");}
case LIFE -> {lore.add("§a+1 сердце");lore.add("§aСнятие плохих эффектов каждые 30 сек.");}
case POSEIDON -> {lore.add("§3Вода / дождь: дыхание, скорость");lore.add("§bСпасение только в воде или под дождём");lore.add("§cСухая смерть: гроза и призрачный дельфин");}
case HADES -> {lore.add("§c−4 сердца, Поглощение III");lore.add("§7Мобы игнорируют, пока их не тревожат");lore.add("§4Возрождение только рядом с игроками");}
case AGES -> {lore.add("§eТри спасения, шипы на всех атаках");lore.add("§6Хождение по воде и лаве, защита от огня");}
case END -> {lore.add("§5Невидимость, эндермены нейтральны");lore.add("§dОтражение дальних атак");lore.add("§6Крит по мобу: молния +1 сердце урона");}
}
m.setLore(lore);
if(t==Talisman.MAIMING)attribute(m,"maiming_health",Attribute.MAX_HEALTH,-1,AttributeModifier.Operation.ADD_NUMBER);
if(t==Talisman.WRATH){attribute(m,"wrath_health",Attribute.MAX_HEALTH,-4,AttributeModifier.Operation.ADD_NUMBER);attribute(m,"wrath_attack_speed",Attribute.ATTACK_SPEED,.30,AttributeModifier.Operation.ADD_SCALAR);}
if(t==Talisman.DEFIANCE)attribute(m,"defiance_health",Attribute.MAX_HEALTH,-4,AttributeModifier.Operation.ADD_NUMBER);
if(t==Talisman.LIFE)attribute(m,"life_health",Attribute.MAX_HEALTH,2,AttributeModifier.Operation.ADD_NUMBER);
if(t==Talisman.HADES)attribute(m,"hades_health",Attribute.MAX_HEALTH,-8,AttributeModifier.Operation.ADD_NUMBER);
i.setItemMeta(m);return i;
}
private void attribute(ItemMeta m,String id,Attribute a,double amount,AttributeModifier.Operation op){
m.addAttributeModifier(a,new AttributeModifier(new NamespacedKey(this,id),amount,op,EquipmentSlotGroup.OFFHAND));
}
private Talisman held(Player p){return itemType(p.getInventory().getItemInOffHand());}
private Talisman itemType(ItemStack i){
if(i==null||i.getType()!=Material.TOTEM_OF_UNDYING||!i.hasItemMeta())return null;
String k=i.getItemMeta().getPersistentDataContainer().get(typeKey,PersistentDataType.STRING);
return k==null?null:Talisman.parse(k);
}
private int lives(ItemStack i){
if(i==null||!i.hasItemMeta())return 0;
Integer n=i.getItemMeta().getPersistentDataContainer().get(usesKey,PersistentDataType.INTEGER);
return n==null?0:n;
}
private ItemStack chestItem(){
ItemStack i=new ItemStack(Material.CHEST);ItemMeta m=i.getItemMeta();
m.setDisplayName("§5§lСундук талисманов");
m.setLore(List.of("§7Открой ритуал на улице с видом на небо","§dНастоящий маяк и таймер: 03:00","§7После открытия награда ближайшему игроку","§7Ломать может только поставивший"));
m.getPersistentDataContainer().set(chestKey,PersistentDataType.BYTE,(byte)1);
i.setItemMeta(m);return i;
}
private boolean specialChest(ItemStack i){return i!=null&&i.getType()==Material.CHEST&&i.hasItemMeta()&&Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(chestKey,PersistentDataType.BYTE));}
private String index(Location loc){return loc.getWorld().getUID()+":"+loc.getBlockX()+":"+loc.getBlockY()+":"+loc.getBlockZ();}
private Altar altar(Block b){return altars.get(index(b.getLocation()));}
private boolean beamPart(Altar a,Block b){
if(!b.getWorld().getUID().equals(a.world))return false;
int dx=b.getX()-a.x,dy=b.getY()-a.y,dz=b.getZ()-a.z;
return dy==2&&Math.abs(dx)<=1&&Math.abs(dz)<=1||dy==3&&dx==0&&dz==0||dy==4&&dx==0&&dz==0;
}
private Altar protectedAltar(Block b){for(Altar a:altars.values())if(a.index().equals(index(b.getLocation()))||beamPart(a,b))return a;return null;}
private boolean canBeam(Block chest){
World w=chest.getWorld();int x=chest.getX(),y=chest.getY(),z=chest.getZ();
if(y+5>=w.getMaxHeight())return false;
for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!w.getBlockAt(x+dx,y+2,z+dz).isEmpty())return false;
if(!w.getBlockAt(x,y+3,z).isEmpty()||!w.getBlockAt(x,y+4,z).isEmpty())return false;
for(int yy=y+5;yy<w.getMaxHeight();yy++){
Block b=w.getBlockAt(x,yy,z);
if(!b.isEmpty()&&b.getType()!=Material.GLASS&&b.getType()!=Material.PURPLE_STAINED_GLASS&&b.getType()!=Material.LIGHT_BLUE_STAINED_GLASS)return false;
}
return true;
}
private void buildBeacon(Altar a){
World w=Bukkit.getWorld(a.world);if(w==null)return;
for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)w.getBlockAt(a.x+dx,a.y+2,a.z+dz).setType(Material.IRON_BLOCK,false);
w.getBlockAt(a.x,a.y+3,a.z).setType(Material.BEACON,false);
w.getBlockAt(a.x,a.y+4,a.z).setType(Material.PURPLE_STAINED_GLASS,false);
}
private void restoreBeacon(Altar a){
World w=Bukkit.getWorld(a.world);if(w==null||!w.isChunkLoaded(a.x>>4,a.z>>4))return;
if(w.getBlockAt(a.x,a.y+4,a.z).getType()==Material.PURPLE_STAINED_GLASS)w.getBlockAt(a.x,a.y+4,a.z).setType(Material.AIR,false);
if(w.getBlockAt(a.x,a.y+3,a.z).getType()==Material.BEACON)w.getBlockAt(a.x,a.y+3,a.z).setType(Material.AIR,false);
for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
Block b=w.getBlockAt(a.x+dx,a.y+2,a.z+dz);
if(b.getType()==Material.IRON_BLOCK)b.setType(Material.AIR,false);
}
}
private void loadAltars(){
if(!saveFile.isFile())return;
YamlConfiguration y=YamlConfiguration.loadConfiguration(saveFile);
ConfigurationSection c=y.getConfigurationSection("chests");if(c==null)return;
for(String id:c.getKeys(false))try{
String p="chests."+id+".";Altar a=new Altar();
a.id=UUID.fromString(id);a.owner=UUID.fromString(y.getString(p+"owner"));a.world=UUID.fromString(y.getString(p+"world"));
a.x=y.getInt(p+"x");a.y=y.getInt(p+"y");a.z=y.getInt(p+"z");a.due=y.getLong(p+"due");a.ready=y.getBoolean(p+"ready");
altars.put(a.index(),a);
}catch(Exception ex){getLogger().warning("Invalid ritual chest "+id+" "+ex);}
}
private void saveAltars(){
YamlConfiguration y=new YamlConfiguration();
for(Altar a:altars.values()){
String p="chests."+a.id+".";
y.set(p+"owner",a.owner.toString());y.set(p+"world",a.world.toString());
y.set(p+"x",a.x);y.set(p+"y",a.y);y.set(p+"z",a.z);y.set(p+"due",a.due);y.set(p+"ready",a.ready);
}
try{y.save(saveFile);}catch(IOException e){getLogger().severe("Could not save ritual chests: "+e);}
}
private void removeAltar(Altar a){
if(altars.remove(a.index())==null)return;
UUID id=holograms.remove(a.id);if(id!=null){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
restoreBeacon(a);saveAltars();
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){
if(protectedAltar(e.getBlockPlaced())!=null){e.setCancelled(true);return;}
if(!specialChest(e.getItemInHand()))return;
Block b=e.getBlockPlaced();Player p=e.getPlayer();
if(!canBeam(b)){e.setCancelled(true);p.sendMessage("§cНужно открытое небо и свободное место над сундуком (3×3).");return;}
Altar a=new Altar();a.owner=p.getUniqueId();a.world=b.getWorld().getUID();a.x=b.getX();a.y=b.getY();a.z=b.getZ();a.due=System.currentTimeMillis()+OPEN_DELAY;
altars.put(a.index(),a);buildBeacon(a);saveAltars();
p.sendMessage("§d✦ Сундук установлен! §7Координаты: "+a.x+", "+a.y+", "+a.z);
p.sendMessage("§7Настоящий луч маяка. Открытие через §d03:00§7.");
b.getWorld().playSound(b.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,.8f);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){
Altar a=protectedAltar(e.getBlock());if(a==null)return;
e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);
if(!a.index().equals(index(e.getBlock().getLocation()))){e.getPlayer().sendMessage("§cМагический маяк нельзя ломать отдельно.");return;}
if(!a.owner.equals(e.getPlayer().getUniqueId())){e.getPlayer().sendMessage("§cСундук может сломать только его владелец.");return;}
Location loc=a.location();removeAltar(a);e.getBlock().setType(Material.AIR,false);
give(e.getPlayer(),new ItemStack(Material.TOTEM_OF_UNDYING));give(e.getPlayer(),new ItemStack(Material.AMETHYST_SHARD,2+random.nextInt(4)));
e.getPlayer().sendMessage("§eРитуал отменён: возвращён тотем и 2–5 аметистов.");
if(loc!=null)loc.getWorld().spawnParticle(Particle.SMOKE,loc,18,.5,.6,.5,.02);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e){
Block b=e.getClickedBlock();if(b==null||e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
Altar a=protectedAltar(b);if(a==null)return;e.setCancelled(true);
if(e.getHand()!=EquipmentSlot.HAND)return;
long s=Math.max(0,(a.due-System.currentTimeMillis()+999)/1000);
e.getPlayer().sendMessage(a.ready?"§aРитуал завершён. Подойди к сундуку за наградой.":"§dДо открытия: §f"+String.format("%02d:%02d",s/60,s%60));
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void entityExplosion(EntityExplodeEvent e){e.blockList().removeIf(b->protectedAltar(b)!=null);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void blockExplosion(BlockExplodeEvent e){e.blockList().removeIf(b->protectedAltar(b)!=null);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void burn(BlockBurnEvent e){if(protectedAltar(e.getBlock())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void liquid(BlockFromToEvent e){if(protectedAltar(e.getToBlock())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void extend(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->protectedAltar(b)!=null))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void retract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->protectedAltar(b)!=null))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void open(InventoryOpenEvent e){Location l=e.getInventory().getLocation();if(l!=null&&altars.containsKey(index(l)))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void transfer(InventoryMoveItemEvent e){Location a=e.getSource().getLocation(),b=e.getDestination().getLocation();if(a!=null&&altars.containsKey(index(a))||b!=null&&altars.containsKey(index(b)))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void dropBoss(EntityDeathEvent e){
EntityType entity=e.getEntityType();
if(entity==EntityType.WITHER&&e.getEntity().getKiller()!=null&&random.nextDouble()<getConfig().getDouble("wither-maiming",.5)){
int n=1;if(random.nextDouble()<.25)n++;if(random.nextDouble()<.08)n++;
for(int i=0;i<n;i++)e.getDrops().add(talisman(Talisman.MAIMING,2));
e.getEntity().getWorld().spawnParticle(Particle.DRAGON_BREATH,e.getEntity().getLocation(),55,1,1,1,.03);
}
if(entity==EntityType.ELDER_GUARDIAN&&random.nextDouble()<getConfig().getDouble("elder-guardian-poseidon",.25))e.getDrops().add(talisman(Talisman.POSEIDON,1));
if(entity==EntityType.BLAZE&&random.nextDouble()<getConfig().getDouble("blaze-hades",.015))e.getDrops().add(talisman(Talisman.HADES,1));
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void netherLoot(org.bukkit.event.world.LootGenerateEvent e){
if(e.getWorld().getEnvironment()!=World.Environment.NETHER)return;
String id=e.getLootTable().getKey().toString();
Talisman choice=null;double prob=0;
if(id.contains("bastion_treasure")){choice=Talisman.HADES;prob=.075;}
else if(id.contains("bastion_bridge")){choice=Talisman.WRATH;prob=.05;}
else if(id.contains("bastion_other")||id.contains("bastion_hoglin_stable")){choice=Talisman.DEFIANCE;prob=.027;}
else if(id.contains("nether_bridge")||id.contains("fortress")){choice=Talisman.WRATH;prob=.022;}
if(choice!=null&&random.nextDouble()<prob*getConfig().getDouble("nether-loot-multiplier",1))e.getLoot().add(talisman(choice,choice.lives));
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void fishing(PlayerFishEvent e){
if(e.getState()!=PlayerFishEvent.State.CAUGHT_FISH)return;
double chance=isRaining(e.getPlayer())?getConfig().getDouble("fishing-rain-poseidon",.04):getConfig().getDouble("fishing-dry-poseidon",.005);
if(random.nextDouble()>=chance)return;
give(e.getPlayer(),talisman(Talisman.POSEIDON,1));
e.getPlayer().sendMessage("§3Море подарило Талисман Посейдона!");
e.getPlayer().getWorld().spawnParticle(Particle.SPLASH,e.getPlayer().getLocation(),60,1,.6,1,.1);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void eggDrop(PlayerDropItemEvent e){
if(e.getItemDrop().getItemStack().getType()!=Material.DRAGON_EGG)return;
if(e.getPlayer().getWorld().getEnvironment()!=World.Environment.THE_END)return;
thrownEggs.put(e.getItemDrop().getUniqueId(),e.getPlayer().getUniqueId());
}
private void quarterTick(){
if(!thrownEggs.isEmpty())for(World w:Bukkit.getWorlds())if(w.getEnvironment()==World.Environment.THE_END){
for(Item i:w.getEntitiesByClass(Item.class)){
if(i.getItemStack().getType()!=Material.DRAGON_EGG||i.getLocation().getY()>w.getMinHeight()+6)continue;
UUID owner=thrownEggs.remove(i.getUniqueId());if(owner==null)continue;
i.remove();Player p=Bukkit.getPlayer(owner);
if(p!=null){give(p,talisman(Talisman.END,0));p.sendMessage("§5Из бездны получен Талисман Энда / Зевса!");}
else{w.dropItemNaturally(w.getSpawnLocation(),talisman(Talisman.END,0));}
w.strikeLightningEffect(i.getLocation());
}
}
for(Player p:Bukkit.getOnlinePlayers()){
if(held(p)==null)continue;
if(p.getLocation().getY()<=p.getWorld().getMinHeight()+6)voidRescue(p);
}
}
private void voidRescue(Player p){
Location target=safeLocations.get(p.getUniqueId());
if(target==null||target.getWorld()==null)target=Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(0,2,0);
if(!target.getChunk().isLoaded())target.getChunk().load();
p.teleport(target);p.setFallDistance(0);p.setVelocity(new Vector(0,0,0));p.setNoDamageTicks(50);
p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,true));
p.sendMessage("§dТалисман перенёс тебя из бездны!");
p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation(),90,.7,1,.7,.25);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void moved(PlayerMoveEvent e){
Player p=e.getPlayer();if(e.getTo()==null||held(p)==null)return;
if(e.getTo().getY()<p.getWorld().getMinHeight()+6){voidRescue(p);return;}
if(e.getTo().getY()<p.getWorld().getMinHeight()+12)return;
if(p.isOnGround()&&p.getLocation().getBlock().isPassable()&&p.getLocation().subtract(0,1,0).getBlock().getType().isSolid())safeLocations.put(p.getUniqueId(),p.getLocation().clone());
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void shield(PlayerShieldDisableEvent e){if(held(e.getPlayer())==Talisman.DEFIANCE)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent e){
if(e.getEntity() instanceof Item item&&itemType(item.getItemStack())==Talisman.HADES){
switch(e.getCause()){
case FIRE,FIRE_TICK,LAVA,HOT_FLOOR -> {e.setCancelled(true);return;}
default -> {}
}
}
if(!(e.getEntity() instanceof Player p))return;
if(held(p)==Talisman.AGES){
switch(e.getCause()){
case FIRE,FIRE_TICK,LAVA,HOT_FLOOR -> {e.setCancelled(true);p.setFireTicks(0);}
default -> {}
}
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void byEntity(EntityDamageByEntityEvent e){
if(e.getEntity() instanceof Player target&&held(target)==Talisman.END&&e.getDamager() instanceof Projectile projectile){
e.setCancelled(true);ProjectileSource shooter=projectile.getShooter();
if(shooter instanceof LivingEntity source&&source!=target&&source.isValid())source.damage(Math.min(20,Math.max(1,e.getFinalDamage())),target);
target.getWorld().spawnParticle(Particle.PORTAL,target.getEyeLocation(),24,.3,.3,.3,.1);return;
}
if(e.getEntity() instanceof Player target&&held(target)==Talisman.AGES&&e.getDamager() instanceof LivingEntity attacker&&attacker!=target){
Bukkit.getScheduler().runTask(this,()->{if(attacker.isValid()&&!attacker.isDead())attacker.damage(2.0,target);});
}
if(!(e.getDamager() instanceof Player attacker))return;
Talisman t=held(attacker);if(t==null)return;
if(t==Talisman.WRATH)e.setDamage(e.getDamage()*1.35);
if(e.getEntity() instanceof LivingEntity victim){
if(t==Talisman.MAIMING)victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,100,0));
if(t==Talisman.POSEIDON&&isRaining(attacker)&&victim.getHealth()<=victim.getAttribute(Attribute.MAX_HEALTH).getValue()*.5)victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,70,1));
if(t==Talisman.END&&!(victim instanceof Player)&&critical(attacker)){
Bukkit.getScheduler().runTask(this,()->{if(victim.isValid()&&!victim.isDead()){
victim.getWorld().strikeLightningEffect(victim.getLocation());victim.setHealth(Math.max(0,victim.getHealth()-2));
}});
}
}
}
private boolean critical(Player p){return p.getFallDistance()>0&&!p.isOnGround()&&!p.isInWater()&&!p.isClimbing()&&!p.hasPotionEffect(PotionEffectType.BLINDNESS);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hostileTarget(EntityTargetLivingEntityEvent e){
if(!(e.getTarget() instanceof Player p))return;
if(held(p)==Talisman.END&&e.getEntityType()==EntityType.ENDERMAN){e.setCancelled(true);return;}
if(held(p)==Talisman.HADES&&e.getEntity() instanceof Monster&&!e.getReason().name().contains("ATTACKED"))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST)public void resurrect(EntityResurrectEvent e){
if(!(e.getEntity() instanceof Player p)||e.getHand()!=EquipmentSlot.OFF_HAND)return;
ItemStack held=p.getInventory().getItemInOffHand();Talisman t=itemType(held);if(t==null)return;
int current=lives(held);
boolean can=t.lives>0&&current>0;
if(t==Talisman.POSEIDON)can&=(p.isInWater()||isRaining(p));
List<Player> victims=new ArrayList<>();
if(t==Talisman.HADES){
for(Player v:p.getWorld().getPlayers())if(v!=p&&v.getLocation().distanceSquared(p.getLocation())<=12*12)victims.add(v);
can&=!victims.isEmpty();
}
if(!can){e.setCancelled(true);return;}
e.setCancelled(false);
final List<Player> nearby=List.copyOf(victims);
Bukkit.getScheduler().runTask(this,()->{
if(!p.isOnline())return;
if(current>1){ItemStack next=talisman(t,current-1);ItemStack off=p.getInventory().getItemInOffHand();
if(off.getType()==Material.AIR||off.getAmount()==0)p.getInventory().setItemInOffHand(next);else give(p,next);
}
if(t==Talisman.HADES){
org.bukkit.attribute.AttributeInstance max=p.getAttribute(Attribute.MAX_HEALTH);if(max!=null)p.setHealth(Math.max(1,max.getValue()));
int hunger=0;
for(Player v:nearby){if(!v.isOnline()||v.isDead()||!v.getWorld().equals(p.getWorld())||v.getLocation().distanceSquared(p.getLocation())>12*12)continue;
v.setHealth(Math.max(1,v.getHealth()-4));int n=Math.min(v.getFoodLevel(),4);v.setFoodLevel(v.getFoodLevel()-n);hunger+=n;
v.getWorld().spawnParticle(Particle.SOUL,v.getLocation().add(0,1,0),20,.5,.5,.5,.03);
}
p.setFoodLevel(Math.min(20,p.getFoodLevel()+hunger));
}
if(t==Talisman.LIFE)cleanse(p);
reviveParticles(p.getLocation(),t);
p.sendMessage("§dТалисман спас тебя! §7Осталось спасений: §e"+(current-1));
});
}
@EventHandler(priority=EventPriority.MONITOR)public void death(PlayerDeathEvent e){
Player p=e.getEntity();
if(held(p)!=Talisman.POSEIDON||(p.isInWater()||isRaining(p)))return;
Location loc=p.getLocation().clone();World w=loc.getWorld();if(w==null)return;
w.strikeLightningEffect(loc);w.playSound(loc,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1,.9f);
for(Player other:w.getPlayers()){
if(other==p||held(other)==Talisman.POSEIDON||other.getLocation().distanceSquared(loc)>12*12)continue;
other.addPotionEffect(new PotionEffect(PotionEffectType.POISON,140,0));
other.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,140,0));
other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,90,0));
}
spawnPhantomDolphin(loc);
}
private void spawnPhantomDolphin(Location loc){
World w=loc.getWorld();if(w==null)return;
Dolphin d=w.spawn(loc.clone().add(0,1,0),Dolphin.class);
d.setGravity(false);d.setAI(false);d.setSilent(true);d.setGlowing(true);
d.getPersistentDataContainer().set(dolphinKey,PersistentDataType.BYTE,(byte)1);
ghostDolphins.put(d.getUniqueId(),System.currentTimeMillis()+8_000);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void dolphinHit(EntityDamageEvent e){
if(!(e.getEntity() instanceof Dolphin d))return;
if(!ghostDolphins.containsKey(d.getUniqueId()))return;
e.setCancelled(true);ghostDolphins.remove(d.getUniqueId());d.remove();d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),40,.4,.4,.4,.05);
}
private void dolphinTick(){
for(var entry:new ArrayList<>(ghostDolphins.entrySet())){
Entity raw=Bukkit.getEntity(entry.getKey());
if(!(raw instanceof Dolphin d)||!raw.isValid()){ghostDolphins.remove(entry.getKey());continue;}
if(System.currentTimeMillis()>entry.getValue()){ghostDolphins.remove(entry.getKey());d.remove();continue;}
Player nearest=null;double best=16*16;
for(Player p:d.getWorld().getPlayers()){
if(held(p)==Talisman.POSEIDON||p.isDead())continue;
double distance=p.getLocation().distanceSquared(d.getLocation());if(distance<best){best=distance;nearest=p;}
}
if(nearest!=null){
Vector v=nearest.getEyeLocation().toVector().subtract(d.getLocation().toVector());
if(v.lengthSquared()>.01)d.teleport(d.getLocation().add(v.normalize().multiply(.35)));
if(best<2.25){nearest.damage(2.0,d);ghostDolphins.remove(d.getUniqueId());d.remove();continue;}
}
d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),3,.3,.3,.3,.02);
}
}
private boolean isRaining(Player p){return p.getWorld().getEnvironment()==World.Environment.NORMAL&&p.getWorld().hasStorm();}
private void cleanse(Player p){
for(PotionEffect fx:new ArrayList<>(p.getActivePotionEffects())){
PotionEffectType t=fx.getType();
if(t==PotionEffectType.POISON||t==PotionEffectType.WITHER||t==PotionEffectType.WEAKNESS||t==PotionEffectType.SLOWNESS||t==PotionEffectType.MINING_FATIGUE||t==PotionEffectType.HUNGER||t==PotionEffectType.BLINDNESS||t==PotionEffectType.NAUSEA||t==PotionEffectType.DARKNESS||t==PotionEffectType.LEVITATION)p.removePotionEffect(t);
}
p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),16,.4,.5,.4,.02);
}
private void refresh(){
long now=System.currentTimeMillis();
for(Player p:Bukkit.getOnlinePlayers()){
Talisman t=held(p),old=active.get(p.getUniqueId());
if(old!=t){
if(old==Talisman.MAIMING)p.removePotionEffect(PotionEffectType.HUNGER);
if(old==Talisman.WRATH)p.removePotionEffect(PotionEffectType.STRENGTH);
if(old==Talisman.DEFIANCE||old==Talisman.HADES)p.removePotionEffect(PotionEffectType.ABSORPTION);
if(old==Talisman.POSEIDON){p.removePotionEffect(PotionEffectType.WATER_BREATHING);p.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);p.removePotionEffect(PotionEffectType.SPEED);}
if(old==Talisman.END)p.removePotionEffect(PotionEffectType.INVISIBILITY);
}
if(t==null){active.remove(p.getUniqueId());continue;}active.put(p.getUniqueId(),t);
switch(t){
case MAIMING -> p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER,80,0,true,false,true));
case WRATH -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,80,1,true,false,true));
case DEFIANCE -> {if(!p.hasPotionEffect(PotionEffectType.ABSORPTION))p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,1_000_000,1,true,false,true));}
case LIFE -> {Long last=cleansed.get(p.getUniqueId());if(last==null||now-last>=30_000){cleansed.put(p.getUniqueId(),now);cleanse(p);}}
case POSEIDON -> {if(p.isInWater()||isRaining(p)){
p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,100,0,true,false,true));
p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,100,0,true,false,true));
p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,0,true,false,true));
}}
case HADES -> {if(!p.hasPotionEffect(PotionEffectType.ABSORPTION))p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,1_000_000,2,true,false,true));}
case AGES -> {p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,100,0,true,false,true));waterWalk(p);}
case END -> p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0,true,false,true));
}
}
}
private void waterWalk(Player p){
Location l=p.getLocation();Block feet=l.getBlock(),under=l.clone().subtract(0,.25,0).getBlock();
boolean liquid=feet.isLiquid()||under.isLiquid();
if(!liquid||p.isSneaking())return;
if(p.getVelocity().getY()<0){Vector v=p.getVelocity();v.setY(.07);p.setVelocity(v);p.setFallDistance(0);}
}
private void tick(){
pulses++;refresh();dolphinTick();
for(Altar a:new ArrayList<>(altars.values())){
Location loc=a.location();if(loc==null||!loc.getWorld().isChunkLoaded(a.x>>4,a.z>>4))continue;
World w=loc.getWorld();Block b=w.getBlockAt(a.x,a.y,a.z);
if(b.getType()!=Material.CHEST){removeAltar(a);continue;}
if(!a.ready&&System.currentTimeMillis()>=a.due){a.ready=true;saveAltars();w.playSound(loc,Sound.BLOCK_END_PORTAL_SPAWN,1,1.6f);w.spawnParticle(Particle.TOTEM_OF_UNDYING,loc,70,1,1,1,.1);}
showHologram(a);
if(a.ready){
Player nearest=null;double best=25*25;
for(Player p:w.getPlayers()){double d=p.getLocation().distanceSquared(loc);if(d<best){nearest=p;best=d;}}
if(nearest!=null){Talisman t=roll();give(nearest,talisman(t,t.lives));nearest.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,1200,1));
nearest.sendMessage("§d✦ Сундук открыт: "+t.name+" §b| Скорость II на 60 сек.");
reviveParticles(loc,t);removeAltar(a);b.setType(Material.AIR,false);continue;}
}
if(pulses%2==0)ambient(a);
}
}
private Talisman roll(){
double x=random.nextDouble();
if(x<.27)return Talisman.LIFE;
if(x<.52)return Talisman.DEFIANCE;
if(x<.70)return Talisman.WRATH;
if(x<.83)return Talisman.POSEIDON;
if(x<.93)return Talisman.HADES;
return Talisman.AGES;
}
private void showHologram(Altar a){
Location loc=a.location();if(loc==null)return;
UUID uuid=holograms.get(a.id);Entity entity=uuid==null?null:Bukkit.getEntity(uuid);
TextDisplay d;
if(entity instanceof TextDisplay x)d=x;
else{d=loc.getWorld().spawn(loc.clone().add(0,1.45,0),TextDisplay.class,t->{
t.setBillboard(Display.Billboard.CENTER);t.setSeeThrough(true);t.setShadowed(true);t.setPersistent(false);
t.getPersistentDataContainer().set(displayKey,PersistentDataType.STRING,a.id.toString());
});holograms.put(a.id,d.getUniqueId());}
long s=Math.max(0,(a.due-System.currentTimeMillis()+999)/1000);
d.text(Component.text("✦ СУНДУК ТАЛИСМАНОВ ✦\n",NamedTextColor.LIGHT_PURPLE).append(Component.text(a.ready?"НАГРАДА ГОТОВА":"Открытие через "+String.format("%02d:%02d",s/60,s%60),a.ready?NamedTextColor.GREEN:NamedTextColor.WHITE)));
}
private void ambient(Altar a){
Location loc=a.location();if(loc==null)return;World w=loc.getWorld();
boolean close=false;for(Player p:w.getPlayers())if(p.getLocation().distanceSquared(loc)<50*50){close=true;break;}
if(!close)return;
for(int i=0;i<12;i++){
double ang=pulses*.2+i*Math.PI/6;
Location q=loc.clone().add(Math.cos(ang)*.8,.2+(i%4)*.32,Math.sin(ang)*.8);
w.spawnParticle(Particle.ENCHANT,q,1,0,0,0,.01);
}
if(a.ready)w.spawnParticle(Particle.HAPPY_VILLAGER,loc,8,.5,.7,.5,.05);
}
private void reviveParticles(Location loc,Talisman t){
World w=loc.getWorld();if(w==null)return;
Particle kind=switch(t){
case MAIMING -> Particle.DRAGON_BREATH;
case WRATH -> Particle.FLAME;
case DEFIANCE -> Particle.END_ROD;
case LIFE -> Particle.TOTEM_OF_UNDYING;
case POSEIDON -> Particle.SPLASH;
case HADES -> Particle.SOUL_FIRE_FLAME;
case AGES -> Particle.ENCHANT;
case END -> Particle.PORTAL;
};
w.spawnParticle(kind,loc.clone().add(0,1,0),100,.9,1,.9,.06);
w.playSound(loc,Sound.ENTITY_PLAYER_LEVELUP,1,1.3f);
}
private void give(Player p,ItemStack i){for(ItemStack rest:p.getInventory().addItem(i).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length==0||!args[0].equalsIgnoreCase("give")){
sender.sendMessage("§dTalismanForge 2.0 §7| /tf give <ник> <maiming|wrath|defiance|life|poseidon|hades|ages|end|chest>");
sender.sendMessage("§7Рецепт сундука: 7 аметистов + тотем + обсидиан снизу. Талисманы во второй руке.");return true;
}
if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй: /tf give <ник> <тип>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не найден.");return true;}
Talisman t=Talisman.parse(args[2]);ItemStack i=args[2].equalsIgnoreCase("chest")?chestItem():t==null?null:talisman(t,t.lives);
if(i==null){sender.sendMessage("§cНеизвестный тип.");return true;}
give(p,i);sender.sendMessage("§aВыдано игроку "+p.getName());return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return List.of("give");
if(args.length==2)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(args.length==3)return List.of("maiming","wrath","defiance","life","poseidon","hades","ages","end","chest");
return List.of();
}
@EventHandler public void quit(PlayerQuitEvent e){
UUID id=e.getPlayer().getUniqueId();active.remove(id);cleansed.remove(id);safeLocations.remove(id);
}
}