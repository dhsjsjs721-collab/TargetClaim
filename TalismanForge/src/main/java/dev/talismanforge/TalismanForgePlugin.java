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
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.io.*;
import java.util.*;

public final class TalismanForgePlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private enum Talisman {
MAIMING("maiming","§5Талисман Увечья",Material.NETHER_STAR),
WRATH("wrath","§cТалисман Кары",Material.BLAZE_POWDER),
DEFIANCE("defiance","§bТалисман Противостояния",Material.SHIELD),
DARKNESS("darkness","§5Талисман Тьмы",Material.ENDER_EYE),
LIFE("life","§aТалисман Жизни",Material.GHAST_TEAR);
final String key,name;
final Material icon;
Talisman(String k,String n,Material icon){key=k;name=n;this.icon=icon;}
static Talisman parse(String text){for(Talisman t:values())if(t.key.equalsIgnoreCase(text))return t;return null;}
}
private static final long OPEN_DELAY=180000L;
private NamespacedKey typeKey,usesKey,chestKey,displayKey;
private File saveFile;
private final Map<String,Altar> altars=new HashMap<>();
private final Map<UUID,UUID> holograms=new HashMap<>();
private final Map<UUID,Long> cleansed=new HashMap<>();
private final Map<UUID,Talisman> active=new HashMap<>();
private final Random random=new Random();
private int pulse=0;
private static final class Altar {
UUID id=UUID.randomUUID(),owner,world;
int x,y,z;
long due;
boolean ready;
String index(){return world+":"+x+":"+y+":"+z;}
Location location(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x+0.5,y+0.5,z+0.5);}
}
@Override public void onEnable(){
getDataFolder().mkdirs();saveDefaultConfig();
saveFile=new File(getDataFolder(),"altars.yml");
typeKey=new NamespacedKey(this,"talisman_type");usesKey=new NamespacedKey(this,"uses_left");
chestKey=new NamespacedKey(this,"talisman_chest");displayKey=new NamespacedKey(this,"altar_display");
loadAltars();
ShapedRecipe recipe=new ShapedRecipe(new NamespacedKey(this,"talisman_chest_recipe"),chestItem());
recipe.shape("AAA","ATA","AOA");
recipe.setIngredient('A',Material.AMETHYST_SHARD);
recipe.setIngredient('T',Material.TOTEM_OF_UNDYING);
recipe.setIngredient('O',Material.OBSIDIAN);
Bukkit.addRecipe(recipe);
PluginCommand cmd=getCommand("talismanforge");if(cmd!=null){cmd.setExecutor(this);cmd.setTabCompleter(this);}
Bukkit.getPluginManager().registerEvents(this,this);
Bukkit.getScheduler().runTaskTimer(this,this::tick,20L,20L);
getLogger().info("Ready: "+altars.size()+" talisman chests loaded");
}
@Override public void onDisable(){
for(UUID id:holograms.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
holograms.clear();saveAltars();
}
private ItemStack talisman(Talisman t,int uses){
ItemStack item=new ItemStack(Material.TOTEM_OF_UNDYING);
ItemMeta m=item.getItemMeta();
m.setDisplayName(t.name);
m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,t.key);
m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,uses);
m.setItemModel(new NamespacedKey("talismanforge",t.key));
List<String> lore=new ArrayList<>();
lore.add("§8Магический артефакт");
lore.add("§7Работает только во второй руке");
lore.add("§7Спасений от смерти: §e"+uses+"/2");
switch(t){
case MAIMING->{lore.add("§c−0.5 сердца, Голод I");lore.add("§5Удар: Иссушение I на 5 сек.");}
case WRATH->{lore.add("§c−2 сердца");lore.add("§6Сила I, +20% скорости атаки");}
case DEFIANCE->{lore.add("§c−2 сердца");lore.add("§bПоглощение I и несбиваемый щит");}
case DARKNESS->{lore.add("§5Ночное зрение в темноте, +15% урона");lore.add("§cПод прямым солнцем: Слабость I");}
case LIFE->{lore.add("§a+1 сердце");lore.add("§aСнятие негативных эффектов раз в 30 сек.");}
}
m.setLore(lore);
// Equipment-slot modifiers ensure health and speed revert without overwriting other plugins.
if(t==Talisman.MAIMING)m.addAttributeModifier(Attribute.MAX_HEALTH,new AttributeModifier(new NamespacedKey(this,"maiming_health"),-1.0,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.OFFHAND));
if(t==Talisman.WRATH){
m.addAttributeModifier(Attribute.MAX_HEALTH,new AttributeModifier(new NamespacedKey(this,"wrath_health"),-4.0,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.OFFHAND));
m.addAttributeModifier(Attribute.ATTACK_SPEED,new AttributeModifier(new NamespacedKey(this,"wrath_attack_speed"),0.20,AttributeModifier.Operation.ADD_SCALAR,EquipmentSlotGroup.OFFHAND));
}
if(t==Talisman.DEFIANCE)m.addAttributeModifier(Attribute.MAX_HEALTH,new AttributeModifier(new NamespacedKey(this,"defiance_health"),-4.0,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.OFFHAND));
if(t==Talisman.LIFE)m.addAttributeModifier(Attribute.MAX_HEALTH,new AttributeModifier(new NamespacedKey(this,"life_health"),2.0,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.OFFHAND));
item.setItemMeta(m);return item;
}
private Talisman getType(ItemStack item){
if(item==null||item.getType()!=Material.TOTEM_OF_UNDYING||!item.hasItemMeta())return null;
String k=item.getItemMeta().getPersistentDataContainer().get(typeKey,PersistentDataType.STRING);
return k==null?null:Talisman.parse(k);
}
private int getUses(ItemStack item){
if(item==null||!item.hasItemMeta())return 0;
return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer().get(usesKey,PersistentDataType.INTEGER)).orElse(0);
}
private ItemStack chestItem(){
ItemStack i=new ItemStack(Material.CHEST);ItemMeta m=i.getItemMeta();
m.setDisplayName("§5§lСундук талисманов");
m.setLore(List.of("§7Установи для открытия ритуала","§7Открытие через §e3 минуты","§7Награду получит ближайший игрок","§7До открытия разрушить может только владелец"));
m.getPersistentDataContainer().set(chestKey,PersistentDataType.BYTE,(byte)1);
i.setItemMeta(m);return i;
}
private boolean isChest(ItemStack i){return i!=null&&i.getType()==Material.CHEST&&i.hasItemMeta()&&Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(chestKey,PersistentDataType.BYTE));}
private String key(Location loc){return loc.getWorld().getUID()+":"+loc.getBlockX()+":"+loc.getBlockY()+":"+loc.getBlockZ();}
private Altar altar(Block b){return altars.get(key(b.getLocation()));}
private void loadAltars(){
if(!saveFile.exists())return;
YamlConfiguration y=YamlConfiguration.loadConfiguration(saveFile);
ConfigurationSection all=y.getConfigurationSection("chests");if(all==null)return;
for(String id:all.getKeys(false)){
try{
String p="chests."+id+".";Altar a=new Altar();
a.id=UUID.fromString(id);a.owner=UUID.fromString(y.getString(p+"owner"));
a.world=UUID.fromString(y.getString(p+"world"));a.x=y.getInt(p+"x");a.y=y.getInt(p+"y");a.z=y.getInt(p+"z");a.due=y.getLong(p+"due");a.ready=y.getBoolean(p+"ready");
altars.put(a.index(),a);
}catch(Exception ex){getLogger().warning("Cannot read altar "+id);}
}
}
private void saveAltars(){
YamlConfiguration y=new YamlConfiguration();
for(Altar a:altars.values()){
String p="chests."+a.id+".";
y.set(p+"owner",a.owner.toString());y.set(p+"world",a.world.toString());
y.set(p+"x",a.x);y.set(p+"y",a.y);y.set(p+"z",a.z);
y.set(p+"due",a.due);y.set(p+"ready",a.ready);
}
try{y.save(saveFile);}catch(IOException ex){getLogger().severe("Saving failed: "+ex);}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){
if(!isChest(e.getItemInHand()))return;
Altar a=new Altar();Location p=e.getBlockPlaced().getLocation();a.owner=e.getPlayer().getUniqueId();a.world=p.getWorld().getUID();a.x=p.getBlockX();a.y=p.getBlockY();a.z=p.getBlockZ();a.due=System.currentTimeMillis()+OPEN_DELAY;
altars.put(a.index(),a);saveAltars();
Player pl=e.getPlayer();pl.sendMessage("§5✦ Сундук активирован! §7X:"+a.x+" Y:"+a.y+" Z:"+a.z);
pl.sendMessage("§7Открытие через §d03:00§7. Видимый луч указывает на сундук.");
pl.getWorld().playSound(pl.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,.85f);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){
Altar a=altar(e.getBlock());if(a==null)return;
e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);
if(!a.owner.equals(e.getPlayer().getUniqueId())){e.getPlayer().sendMessage("§cТолько владелец может сломать этот сундук.");return;}
removeAltar(a,true);
e.getBlock().setType(Material.AIR,false);
give(e.getPlayer(),new ItemStack(Material.TOTEM_OF_UNDYING));
give(e.getPlayer(),new ItemStack(Material.AMETHYST_SHARD,2+random.nextInt(4)));
e.getPlayer().sendMessage("§eРитуал отменён: вернулся тотем и 2–5 аметистов.");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void interact(PlayerInteractEvent e){
Block b=e.getClickedBlock();if(b==null)return;
Altar a=altar(b);if(a==null)return;
if(e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
e.setCancelled(true);
if(e.getHand()!=EquipmentSlot.HAND)return;
long remain=Math.max(0,(a.due-System.currentTimeMillis()+999)/1000);
e.getPlayer().sendMessage(a.ready?"§aСундук готов. Приблизься для получения награды.":"§dДо открытия: §f"+String.format("%02d:%02d",remain/60,remain%60));
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(b->altar(b)!=null);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explosionBlock(BlockExplodeEvent e){e.blockList().removeIf(b->altar(b)!=null);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pistonExtend(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->altar(b)!=null))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pistonRetract(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->altar(b)!=null))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void openInventory(org.bukkit.event.inventory.InventoryOpenEvent e){
Location loc=e.getInventory().getLocation();if(loc!=null&&altars.containsKey(key(loc)))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hopper(org.bukkit.event.inventory.InventoryMoveItemEvent e){
Location s=e.getSource().getLocation(),d=e.getDestination().getLocation();
if((s!=null&&altars.containsKey(key(s)))||(d!=null&&altars.containsKey(key(d))))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e){if(altar(e.getBlock())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void flow(BlockFromToEvent e){if(altar(e.getToBlock())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void witherDeath(EntityDeathEvent e){
if(e.getEntityType()!=EntityType.WITHER||e.getEntity().getKiller()==null)return;
if(random.nextDouble()>=getConfig().getDouble("wither-chance",0.50))return;
int count=1;if(random.nextDouble()<.25)count++;if(random.nextDouble()<.08)count++;
for(int i=0;i<count;i++)e.getDrops().add(talisman(Talisman.MAIMING,2));
e.getEntity().getWorld().spawnParticle(Particle.DRAGON_BREATH,e.getEntity().getLocation(),85,1.6,1.2,1.6,.04);
e.getEntity().getKiller().sendMessage("§5С Визера выпало талисманов Увечья: §f"+count);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void netherLoot(LootGenerateEvent e){
if(e.getWorld().getEnvironment()!=World.Environment.NETHER)return;
LootTable table=e.getLootTable();if(table==null)return;
String id=table.getKey().toString();
Talisman chosen=null;double chance=0;
if(id.contains("bastion_treasure")){chosen=Talisman.WRATH;chance=.065;}
else if(id.contains("bastion_bridge")){chosen=Talisman.WRATH;chance=.035;}
else if(id.contains("bastion_other")||id.contains("bastion_hoglin_stable")){chosen=Talisman.DEFIANCE;chance=.025;}
else if(id.contains("nether_bridge")||id.contains("fortress")){chosen=Talisman.DARKNESS;chance=.04;}
else if(id.contains("ruined_portal")){chosen=Talisman.DARKNESS;chance=.025;}
if(chosen!=null&&random.nextDouble()<chance*getConfig().getDouble("nether-loot-multiplier",1.0))e.getLoot().add(talisman(chosen,2));
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void resurrect(EntityResurrectEvent e){
if(!(e.getEntity() instanceof Player p)||e.getHand()!=EquipmentSlot.OFF_HAND)return;
ItemStack off=p.getInventory().getItemInOffHand();Talisman type=getType(off);
if(type==null)return;
int uses=getUses(off);if(uses<1)return;
if(uses>1){
ItemStack next=talisman(type,uses-1);
Bukkit.getScheduler().runTask(this,()->{
if(!p.isOnline())return;
ItemStack current=p.getInventory().getItemInOffHand();
if(current.getType()==Material.AIR||current.getAmount()==0)p.getInventory().setItemInOffHand(next);
else give(p,next);
});
}
Bukkit.getScheduler().runTask(this,()->{
if(!p.isOnline())return;
p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),70,.6,1,.6,.1);
p.sendMessage("§dТалисман спас тебя! §7Осталось зарядов: §e"+(uses-1));
if(type==Talisman.LIFE)cleanse(p);
});
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void defendShield(PlayerShieldDisableEvent e){
if(getType(e.getPlayer().getInventory().getItemInOffHand())==Talisman.DEFIANCE)e.setCancelled(true);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent e){
if(!(e.getDamager() instanceof Player attacker))return;
Talisman t=getType(attacker.getInventory().getItemInOffHand());if(t==null)return;
if(e.getEntity() instanceof LivingEntity victim){
if(t==Talisman.MAIMING)victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,100,0));
if(t==Talisman.DARKNESS&&isDark(attacker)){
// MONITOR is observational; damage amount is altered at HIGHEST in darkDamage handler below.
victim.getWorld().spawnParticle(Particle.PORTAL,victim.getLocation().add(0,1,0),6,.25,.3,.25,.02);
}
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void darkDamage(EntityDamageByEntityEvent e){
if(!(e.getDamager() instanceof Player p))return;
if(getType(p.getInventory().getItemInOffHand())==Talisman.DARKNESS&&isDark(p))e.setDamage(e.getDamage()*1.15);
}
private boolean isDark(Player p){return p.getLocation().getBlock().getLightFromSky()<8;}
private boolean sunlight(Player p){
World w=p.getWorld();return w.getEnvironment()==World.Environment.NORMAL&&w.getTime()<12000&&w.getBlockAt(p.getLocation()).getLightFromSky()>=14&&!w.hasStorm();
}
private void cleanse(Player p){
for(PotionEffect cur:new ArrayList<>(p.getActivePotionEffects())){
PotionEffectType k=cur.getType();
if(k==PotionEffectType.POISON||k==PotionEffectType.WITHER||k==PotionEffectType.WEAKNESS||k==PotionEffectType.SLOWNESS||k==PotionEffectType.MINING_FATIGUE||k==PotionEffectType.HUNGER||k==PotionEffectType.BLINDNESS||k==PotionEffectType.NAUSEA||k==PotionEffectType.DARKNESS||k==PotionEffectType.LEVITATION)p.removePotionEffect(k);
}
p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),16,.4,.5,.4,.03);
}
private void refreshEffects(){
long now=System.currentTimeMillis();
for(Player p:Bukkit.getOnlinePlayers()){
Talisman t=getType(p.getInventory().getItemInOffHand());
Talisman previous=active.put(p.getUniqueId(),t);
if(t==null){
active.remove(p.getUniqueId());
if(previous==Talisman.WRATH)p.removePotionEffect(PotionEffectType.STRENGTH);
if(previous==Talisman.DEFIANCE)p.removePotionEffect(PotionEffectType.ABSORPTION);
if(previous==Talisman.MAIMING)p.removePotionEffect(PotionEffectType.HUNGER);
if(previous==Talisman.DARKNESS){p.removePotionEffect(PotionEffectType.NIGHT_VISION);p.removePotionEffect(PotionEffectType.WEAKNESS);}
continue;
}
if(previous!=t){
if(previous==Talisman.DEFIANCE)p.removePotionEffect(PotionEffectType.ABSORPTION);
if(previous==Talisman.WRATH)p.removePotionEffect(PotionEffectType.STRENGTH);
if(previous==Talisman.MAIMING)p.removePotionEffect(PotionEffectType.HUNGER);
if(previous==Talisman.DARKNESS){p.removePotionEffect(PotionEffectType.NIGHT_VISION);p.removePotionEffect(PotionEffectType.WEAKNESS);}
}
switch(t){
case MAIMING -> p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER,100,0,true,false,true));
case WRATH -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,100,0,true,false,true));
case DEFIANCE -> {
if(previous!=Talisman.DEFIANCE&&!p.hasPotionEffect(PotionEffectType.ABSORPTION))p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,0,true,false,true));
}
case DARKNESS -> {
if(isDark(p))p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,220,0,true,false,false));
if(sunlight(p))p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,90,0,true,false,true));
}
case LIFE -> {
Long last=cleansed.get(p.getUniqueId());if(last==null||now-last>=30000){cleansed.put(p.getUniqueId(),now);cleanse(p);}
}
}
}
}
private void tick(){
pulse++;
if(pulse%3==0)refreshEffects();
List<Altar> copy=new ArrayList<>(altars.values());
for(Altar a:copy){
Location loc=a.location();if(loc==null)continue;
World w=loc.getWorld();if(!w.isChunkLoaded(a.x>>4,a.z>>4))continue;
Block b=w.getBlockAt(a.x,a.y,a.z);
if(b.getType()!=Material.CHEST){removeAltar(a,false);continue;}
if(!a.ready&&System.currentTimeMillis()>=a.due){a.ready=true;saveAltars();w.playSound(loc,Sound.BLOCK_END_PORTAL_SPAWN,.7f,1.6f);w.spawnParticle(Particle.TOTEM_OF_UNDYING,loc.clone().add(0,.6,0),90,1,1,1,.1);}
showHologram(a);
if(a.ready){
Player nearest=null;double distance=Double.MAX_VALUE;
for(Player p:w.getPlayers()){
double dd=p.getLocation().distanceSquared(loc);
if(dd<distance&&dd<=25*25){nearest=p;distance=dd;}
}
if(nearest!=null){
Talisman result=roll();give(nearest,talisman(result,2));
nearest.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,1200,1));
nearest.sendMessage("§d✦ Сундук открылся! §fПолучен: "+result.name+" §7| §bСкорость II на 60 сек.");
nearbyExplosion(loc);removeAltar(a,true);b.setType(Material.AIR,false);
}
}
if(pulse%2==0)particles(a);
}
}
private Talisman roll(){double n=random.nextDouble();if(n<.30)return Talisman.LIFE;if(n<.60)return Talisman.DARKNESS;if(n<.85)return Talisman.DEFIANCE;return Talisman.WRATH;}
private void showHologram(Altar a){
Location loc=a.location();if(loc==null)return;
UUID id=holograms.get(a.id);Entity entity=id==null?null:Bukkit.getEntity(id);
TextDisplay d;
if(entity instanceof TextDisplay t)d=t;
else{
d=loc.getWorld().spawn(loc.clone().add(0,1.65,0),TextDisplay.class,t->{t.setBillboard(Display.Billboard.CENTER);t.setSeeThrough(true);t.setShadowed(false);t.setPersistent(false);t.getPersistentDataContainer().set(displayKey,PersistentDataType.STRING,a.id.toString());});
holograms.put(a.id,d.getUniqueId());
}
long seconds=Math.max(0,(a.due-System.currentTimeMillis()+999)/1000);
String time=String.format("%02d:%02d",seconds/60,seconds%60);
d.text(Component.text("✦ СУНДУК ТАЛИСМАНОВ ✦\n",NamedTextColor.LIGHT_PURPLE).append(Component.text(a.ready?"ГОТОВ К ОТКРЫТИЮ":"Открытие через "+time,a.ready?NamedTextColor.GREEN:NamedTextColor.WHITE)));
}
private void particles(Altar a){
Location loc=a.location();if(loc==null)return;
World w=loc.getWorld();
boolean nearby=false;for(Player p:w.getPlayers())if(p.getLocation().distanceSquared(loc)<48*48){nearby=true;break;}
if(!nearby)return;
int columns=15;
for(int h=0;h<columns;h+=2){Location pos=loc.clone().add(0,1.3+h,0);if(pos.getY()<w.getMaxHeight()-1)w.spawnParticle(Particle.END_ROD,pos,1,.04,.06,.04,0);}
for(int i=0;i<12;i++){
double angle=(pulse*.18+i*Math.PI/6),radius=.75;
Location q=loc.clone().add(Math.cos(angle)*radius,.2+(i%3)*.30,Math.sin(angle)*radius);
w.spawnParticle(Particle.ENCHANT,q,1,0,0,0,0);
}
if(a.ready)w.spawnParticle(Particle.HAPPY_VILLAGER,loc.clone().add(0,.8,0),4,.55,.5,.55,0);
}
private void nearbyExplosion(Location loc){
World w=loc.getWorld();w.playSound(loc,Sound.ENTITY_PLAYER_LEVELUP,1.2f,1.2f);
w.spawnParticle(Particle.END_ROD,loc.clone().add(0,.8,0),70,.9,1.1,.9,.13);
w.spawnParticle(Particle.ENCHANT,loc.clone().add(0,.8,0),85,.9,1.1,.9,1);
w.spawnParticle(Particle.TOTEM_OF_UNDYING,loc.clone().add(0,.8,0),75,.7,1,.7,.04);
}
private void removeAltar(Altar a,boolean particles){
altars.remove(a.index());UUID id=holograms.remove(a.id);if(id!=null){Entity d=Bukkit.getEntity(id);if(d!=null)d.remove();}saveAltars();
}
private void give(Player p,ItemStack item){
Map<Integer,ItemStack> rest=p.getInventory().addItem(item);
for(ItemStack i:rest.values())p.getWorld().dropItemNaturally(p.getLocation(),i);
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length==0||!args[0].equalsIgnoreCase("give")){
sender.sendMessage("§dTalismanForge §7| /tf give <ник> <maiming|wrath|defiance|darkness|life|chest>");
sender.sendMessage("§7Рецепт сундука: 7 аметистов + тотем по центру + обсидиан снизу.");return true;
}
if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй: /tf give <ник> <тип>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не в сети.");return true;}
ItemStack item;Talisman type=Talisman.parse(args[2]);
if(args[2].equalsIgnoreCase("chest"))item=chestItem();
else if(type!=null)item=talisman(type,2);
else{sender.sendMessage("§cНеизвестный талисман.");return true;}
give(p,item);sender.sendMessage("§aВыдано игроку "+p.getName());return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return List.of("give");
if(args.length==2)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(args.length==3)return List.of("maiming","wrath","defiance","darkness","life","chest");
return List.of();
}
@EventHandler public void quit(PlayerQuitEvent e){active.remove(e.getPlayer().getUniqueId());cleansed.remove(e.getPlayer().getUniqueId());}
@EventHandler public void worldLoad(ChunkLoadEvent e){
// Holograms are recreated by the next scheduled tick; unloaded chunks are never force-loaded.
}
}
