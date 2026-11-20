package dev.talismanforge;

import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
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
WRATH("wrath","§cТалисман Кары",1),
DEFIANCE("defiance","§bТалисман Противостояния",2),
JOKER("joker","§dТалисман Джокера",3),
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
private File saveFile,padsFile;
private GrapplingHookListener hookListener;
private final Map<String,Pad> pads=new HashMap<>();
private boolean padsChanged=false;
private final Map<String,Altar> altars=new HashMap<>();
private final Map<UUID,UUID> holograms=new HashMap<>();
private final Map<UUID,UUID> relicDisplays=new HashMap<>();
private final Map<UUID,Integer> wrathStreak=new HashMap<>();
private final Map<UUID,Double> hungerTakenFractions=new HashMap<>();
private final Map<UUID,Double> hungerGivenFractions=new HashMap<>();
private final Set<UUID> editingFood=new HashSet<>();
private final Set<UUID> editingPotions=new HashSet<>();
private final Map<UUID,Long> wrathLastCrit=new HashMap<>();
private final Map<UUID,Long> wrathCritRechargeUntil=new HashMap<>();
private final Map<UUID,Long> swordExpiry=new HashMap<>(),swordCooldown=new HashMap<>();
private final Map<UUID,UUID> swordTarget=new HashMap<>();
private final Map<UUID,UUID> lastAttacker=new HashMap<>(),lastAttackerAttribution=new HashMap<>();
private final Map<UUID,Long> lastAttackerTime=new HashMap<>(),jokerStunnedUntil=new HashMap<>();
private final Map<UUID,Integer> endShieldHits=new HashMap<>();
private final Set<UUID> endReflectionInProgress=new HashSet<>();
private final Map<UUID,List<UUID>> swordVisuals=new HashMap<>();
private final Map<UUID,List<UUID>> poseidonCompanions=new HashMap<>();
private final Map<UUID,Map<UUID,Long>> companionAttackCooldown=new HashMap<>();
private final Set<UUID> agesThornsInProgress=new HashSet<>();
private final Map<UUID,GameMode> hadesPending=new HashMap<>();
private final NamespacedKey wrathRechargeKey=new NamespacedKey("talismanforge","crit_recharge");
private final Map<UUID,Map<PotionEffectType,Integer>> grantedEffects=new HashMap<>();
private final Map<UUID,Long> agesInvulnerableUntil=new HashMap<>();
private final Map<UUID,ShieldRing> shieldRings=new HashMap<>();
private static final class ShieldRing {
int index=0;double health=4.0;
final List<UUID> visuals=new ArrayList<>();
}


private final Map<UUID,Talisman> active=new HashMap<>();
private final Set<UUID> trackedDrops=new HashSet<>();
private final Map<UUID,Location> safeLocations=new HashMap<>();
private final Map<UUID,UUID> thrownEggs=new HashMap<>();
private final Map<UUID,Long> ghostDolphins=new HashMap<>();
private final Map<UUID,UUID> ghostDolphinOwners=new HashMap<>();
private final Map<UUID,Long> ghastCooldowns=new HashMap<>();
private final Map<UUID,String> pathCenters=new HashMap<>();
private final Map<UUID,Long> pathRefresh=new HashMap<>();
private final Random random=new Random();
private int pulses=0;
private static final class Pad {
UUID world;int x,y,z;String original,partnerOriginal;int partnerY;Material replacement;long expire;
String key(){return world+":"+x+":"+y+":"+z;}
}
private static final int MAX_PADS=900;
private static final int PATH_RADIUS=3;
private static final long WATER_DURATION=3200L,LAVA_DURATION=4200L;
private static final class Altar {
UUID id=UUID.randomUUID(),owner,world;
int x,y,z;
Material base=Material.CHEST;
long due;
boolean ready;
String index(){return world+":"+x+":"+y+":"+z;}
Location location(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x+.5,y+.5,z+.5);}
}
@Override public void onEnable(){
getDataFolder().mkdirs();saveDefaultConfig();
saveFile=new File(getDataFolder(),"altars.yml");
padsFile=new File(getDataFolder(),"temporary-paths.yml");
loadPads();
typeKey=new NamespacedKey(this,"talisman_type");usesKey=new NamespacedKey(this,"uses_left");
chestKey=new NamespacedKey(this,"ritual_chest");displayKey=new NamespacedKey(this,"ritual_display");
dolphinKey=new NamespacedKey(this,"poseidon_dolphin");
loadAltars();
for(Altar a:altars.values())cleanupLegacyBeacon(a);
ShapedRecipe r=new ShapedRecipe(new NamespacedKey(this,"ritual_chest"),chestItem());
r.shape("AAA","ATA","AOA");r.setIngredient('A',Material.AMETHYST_SHARD);r.setIngredient('T',Material.TOTEM_OF_UNDYING);r.setIngredient('O',Material.OBSIDIAN);
Bukkit.addRecipe(r);
ShapedRecipe vanillaTotem=new ShapedRecipe(new NamespacedKey(this,"golden_totem"),new ItemStack(Material.TOTEM_OF_UNDYING));
vanillaTotem.shape("GGG","GEG","GGG");
vanillaTotem.setIngredient('G',Material.GOLD_BLOCK);
vanillaTotem.setIngredient('E',Material.EMERALD);
Bukkit.addRecipe(vanillaTotem);
ShapelessRecipe craftedTag=new ShapelessRecipe(new NamespacedKey(this,"paper_string_name_tag"),new ItemStack(Material.NAME_TAG));
craftedTag.addIngredient(Material.PAPER);
craftedTag.addIngredient(Material.STRING);
Bukkit.addRecipe(craftedTag);
PluginCommand c=getCommand("talismanforge");if(c!=null){c.setExecutor(this);c.setTabCompleter(this);}
Bukkit.getPluginManager().registerEvents(this,this);
for(World world:Bukkit.getWorlds())for(Item item:world.getEntitiesByClass(Item.class))trackTalismanDrop(item);
for(Player player:Bukkit.getOnlinePlayers()){restoreShieldRing(player);restoreHadesMode(player);}
hookListener=new GrapplingHookListener(this);
Bukkit.getPluginManager().registerEvents(hookListener,this);
Bukkit.getScheduler().runTaskTimer(this,this::quarterTick,5L,5L);
Bukkit.getScheduler().runTaskTimer(this,this::wrathRechargeTick,10L,5L);
Bukkit.getScheduler().runTaskTimer(this,this::tick,20L,20L);
getLogger().info("TalismanForge 2.7.0: "+altars.size()+" relics");
}
@Override public void onDisable(){
if(hookListener!=null)hookListener.shutdown();
for(UUID id:holograms.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
for(UUID id:relicDisplays.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
for(ShieldRing ring:shieldRings.values())removeShieldVisuals(ring);
shieldRings.clear();relicDisplays.clear();agesInvulnerableUntil.clear();wrathStreak.clear();wrathLastCrit.clear();hungerTakenFractions.clear();hungerGivenFractions.clear();editingFood.clear();
for(UUID id:ghostDolphins.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
holograms.clear();ghostDolphins.clear();ghostDolphinOwners.clear();trackedDrops.clear();
for(UUID id:new ArrayList<>(swordVisuals.keySet()))clearEndSwords(id);
for(UUID id:new ArrayList<>(poseidonCompanions.keySet()))removePoseidonCompanions(id);
for(Player p:Bukkit.getOnlinePlayers()){
removeWrathCritRecharge(p);restoreHadesMode(p);
for(PotionEffectType effect:new ArrayList<>(grantedEffects.getOrDefault(p.getUniqueId(),Map.of()).keySet()))removeGrantedEffect(p,effect);
}
grantedEffects.clear();active.clear();saveAltars();
for(Pad pad:new ArrayList<>(pads.values()))if(restorePad(pad))pads.remove(pad.key());
savePads();
}
private int initialLives(Talisman t){return t==Talisman.JOKER?1+random.nextInt(3):t.lives;}
private ItemStack talisman(Talisman t,int lives){
ItemStack i=new ItemStack(Material.TOTEM_OF_UNDYING);ItemMeta m=i.getItemMeta();
m.setDisplayName(t.name);
m.setItemModel(new NamespacedKey("talismanforge",t.key));
m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,t.key);
m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,Math.max(0,Math.min(t.lives,lives)));
List<String> lore=new ArrayList<>();
lore.add("§8Работает в любой руке");
lore.add(t.lives==0?"§cНе спасает от обычной смерти":t==Talisman.JOKER?"§7Осталось попыток: §e"+lives:"§7Спасений от смерти: §e"+lives+"/"+t.lives);
lore.add(t==Talisman.POSEIDON?"§cНе спасает от бездны":t==Talisman.END?"§bБесконечное спасение от бездны":"§bСпасает от бездны, но исчезает");
switch(t){
case MAIMING -> {lore.add("§c−0.5 сердца, Голод I");lore.add("§5Удары: Иссушение I (5с) и Замедление III (2с)");lore.add("§8Крит: Слабость I на 3 секунды");lore.add("§6Удар по игроку: кража 0.5 сытости");}
case WRATH -> {lore.add("§c−4 сердца, восстановление здоровья −40%");lore.add("§6Сила I, восстановление критического удара быстрее");lore.add("§c+35% прямого урона оружием");lore.add("§6До 4 критов: +1–4% к урону и износ брони");lore.add("§7Одно спасение от смерти");}
case DEFIANCE -> {lore.add("§c−2 сердца, Поглощение II");lore.add("§bЩит невозможно временно отключить");lore.add("§bПосле второго спасения: три парящих щита");lore.add("§7Каждый щит поглощает один удар, 2 сердца прочности");}
case JOKER -> {lore.add("§dШанс: 50%, с эффектом Удачи — 75%");lore.add("§7От 1 до 3 попыток спасения");lore.add("§7Успех расходует одну попытку, не весь талисман");lore.add("§cНеудача: талисман исчезает");lore.add("§aУспех: 1–3 случайных усиления");lore.add("§7Скорость II / Сила II / Сопротивление I");lore.add("§7Каждый эффект длится 20 сек. – 5 минут");lore.add("§5Клыки призывателя атакуют существ рядом");}
case POSEIDON -> {lore.add("§3Вода/дождь: скорость, парящие фугу с ядом");lore.add("§bВ воде: аксолотли лечат владельца");lore.add("§bСпасение только в воде или под дождём");lore.add("§cСухая смерть: мощный дельфин и грозовые молнии");}
case HADES -> {lore.add("§c−6 сердец, Поглощение III");lore.add("§7Мобы игнорируют, пока их не тревожат");lore.add("§6Убийства игроков: кража золотых сердец");lore.add("§4После спасения: 3 секунды призраком в GM3");lore.add("§4Возрождение только рядом с игроками");}
case AGES -> {lore.add("§eТри спасения, шипы на всех атаках");lore.add("§6Разные виды мха / породы над водой и лавой");lore.add("§eВозвращает водоросли после исчезновения тропы");lore.add("§6Второе спасение: 3 сек. защиты и свечения");lore.add("§6Защита от огня и лавы");}
case END -> {lore.add("§5Невидимость, эндермены нейтральны");lore.add("§dОтражение снарядов, иммунитет к падению");lore.add("§6Каждый крит: молния +1 сердце урона");lore.add("§eДва парящих золотых меча защищают от атакующих");}
}
m.setLore(lore);
if(t==Talisman.MAIMING)attribute(m,"maiming_health",Attribute.MAX_HEALTH,-1,AttributeModifier.Operation.ADD_NUMBER);
if(t==Talisman.WRATH)attribute(m,"wrath_health",Attribute.MAX_HEALTH,-8,AttributeModifier.Operation.ADD_NUMBER);
if(t==Talisman.DEFIANCE)attribute(m,"defiance_health",Attribute.MAX_HEALTH,-4,AttributeModifier.Operation.ADD_NUMBER);

if(t==Talisman.HADES)attribute(m,"hades_health",Attribute.MAX_HEALTH,-12,AttributeModifier.Operation.ADD_NUMBER);
i.setItemMeta(m);return i;
}
private void attribute(ItemMeta m,String id,Attribute a,double amount,AttributeModifier.Operation op){
m.addAttributeModifier(a,new AttributeModifier(new NamespacedKey(this,id),amount,op,EquipmentSlotGroup.HAND));
}
private Talisman held(Player p){
Talisman off=itemType(p.getInventory().getItemInOffHand());
return off!=null?off:itemType(p.getInventory().getItemInMainHand());
}
private EquipmentSlot voidSlot(Player p){
Talisman off=itemType(p.getInventory().getItemInOffHand());
Talisman main=itemType(p.getInventory().getItemInMainHand());
if(off==Talisman.END)return EquipmentSlot.OFF_HAND;
if(main==Talisman.END)return EquipmentSlot.HAND;
if(off!=null&&off!=Talisman.POSEIDON)return EquipmentSlot.OFF_HAND;
if(main!=null&&main!=Talisman.POSEIDON)return EquipmentSlot.HAND;
return null;
}
private ItemStack equipped(Player p,EquipmentSlot slot){return slot==EquipmentSlot.HAND?p.getInventory().getItemInMainHand():p.getInventory().getItemInOffHand();}
private void unequip(Player p,EquipmentSlot slot){if(slot==EquipmentSlot.HAND)p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));else p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));}
private Talisman itemType(ItemStack i){
if(i==null||i.getType()!=Material.TOTEM_OF_UNDYING||!i.hasItemMeta())return null;
String k=i.getItemMeta().getPersistentDataContainer().get(typeKey,PersistentDataType.STRING);
return k==null?null:Talisman.parse(k);
}
private void trackTalismanDrop(Item item){
if(itemType(item.getItemStack())!=null)trackedDrops.add(item.getUniqueId());
}
@EventHandler(ignoreCancelled=true)public void talismanSpawn(ItemSpawnEvent e){trackTalismanDrop(e.getEntity());}
@EventHandler public void talismanEntitiesLoaded(EntitiesLoadEvent e){
for(Entity entity:e.getEntities())if(entity instanceof Item item)trackTalismanDrop(item);
}
private void moveDroppedTalismans(){
Iterator<UUID> iterator=trackedDrops.iterator();
while(iterator.hasNext()){
Entity entity=Bukkit.getEntity(iterator.next());
if(!(entity instanceof Item item)||!item.isValid()){iterator.remove();continue;}
Talisman type=itemType(item.getItemStack());
if(type==null){iterator.remove();continue;}
Vector velocity=item.getVelocity();
if(type==Talisman.POSEIDON){
if(item.isInWater()&&velocity.getY()<.12)item.setVelocity(new Vector(velocity.getX(),.12,velocity.getZ()));
continue;
}
if(item.getFireTicks()>0)item.setFireTicks(0);
if(item.isInLava()){
if(velocity.getY()<.14)item.setVelocity(new Vector(velocity.getX(),.14,velocity.getZ()));
}else if(item.isInWater()){
if(velocity.getY()>-.16)item.setVelocity(new Vector(velocity.getX(),-.16,velocity.getZ()));
}
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void talismanCombust(EntityCombustEvent e){
if(e.getEntity() instanceof Item item){
Talisman t=itemType(item.getItemStack());
if(t!=null&&t!=Talisman.POSEIDON){e.setCancelled(true);item.setFireTicks(0);}
}
}
private int lives(ItemStack i){
if(i==null||!i.hasItemMeta())return 0;
Integer n=i.getItemMeta().getPersistentDataContainer().get(usesKey,PersistentDataType.INTEGER);
return n==null?0:n;
}
private ItemStack chestItem(){
ItemStack i=new ItemStack(Material.BARRIER);ItemMeta m=i.getItemMeta();
m.setDisplayName("§3§lРеликварий");
m.setItemModel(new NamespacedKey("talismanforge","reliquary"));
m.setLore(List.of("§7Древний магический блок","§bСветящийся луч без настоящего маяка","§7Пробуждение через §b03:00","§7Награда ближайшему игроку, ставить может каждый"));
m.getPersistentDataContainer().set(chestKey,PersistentDataType.BYTE,(byte)1);
i.setItemMeta(m);return i;
}
private boolean specialChest(ItemStack i){
if(i==null||(i.getType()!=Material.CHEST&&i.getType()!=Material.BARRIER)||!i.hasItemMeta())return false;
return Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(chestKey,PersistentDataType.BYTE))||
Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this,"talisman_chest"),PersistentDataType.BYTE));
}
private String index(Location loc){return loc.getWorld().getUID()+":"+loc.getBlockX()+":"+loc.getBlockY()+":"+loc.getBlockZ();}
private Altar altar(Block b){return altars.get(index(b.getLocation()));}
private Altar protectedAltar(Block b){return altar(b);}
private boolean canBeam(Block b){return b.getY()+2<b.getWorld().getMaxHeight();}
private void cleanupLegacyBeacon(Altar a){
World w=Bukkit.getWorld(a.world);if(w==null||!w.isChunkLoaded(a.x>>4,a.z>>4))return;
if(w.getBlockAt(a.x,a.y+3,a.z).getType()!=Material.BEACON||w.getBlockAt(a.x,a.y+4,a.z).getType()!=Material.PURPLE_STAINED_GLASS)return;
for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(w.getBlockAt(a.x+dx,a.y+2,a.z+dz).getType()!=Material.IRON_BLOCK)return;
w.getBlockAt(a.x,a.y+3,a.z).setType(Material.AIR,false);w.getBlockAt(a.x,a.y+4,a.z).setType(Material.AIR,false);
for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)w.getBlockAt(a.x+dx,a.y+2,a.z+dz).setType(Material.AIR,false);
}
private void beamParticles(Altar a){
Location loc=a.location();if(loc==null)return;
World w=loc.getWorld();int top=Math.min(56,w.getMaxHeight()-a.y-2);
for(Player p:w.getPlayers()){
if(p.getLocation().distanceSquared(loc)>64*64)continue;
for(int h=1;h<top;h+=2){
Location q=loc.clone().add(0,h,0);
p.spawnParticle(Particle.DUST,q,1,0,0,0,new Particle.DustOptions(Color.fromRGB(160,95,250),1.6f));
if(h%4==1)p.spawnParticle(Particle.DUST,q,1,0,0,0,new Particle.DustOptions(Color.fromRGB(226,224,255),1.0f));
if(h%6==1){double t=h*.42+pulses*.12;p.spawnParticle(Particle.DUST,q.clone().add(Math.cos(t)*.25,0,Math.sin(t)*.25),1,0,0,0,new Particle.DustOptions(Color.fromRGB(100,209,255),1.2f));}
}
if(pulses%3==0)p.spawnParticle(Particle.END_ROD,loc.clone().add(0,1.2,0),2,.25,.3,.25,.005);
}
}
private void loadPads(){
if(!padsFile.isFile())return;
YamlConfiguration cfg=YamlConfiguration.loadConfiguration(padsFile);
ConfigurationSection section=cfg.getConfigurationSection("paths");if(section==null)return;
for(String id:section.getKeys(false))try{
String key="paths."+id+".";Pad pad=new Pad();pad.world=UUID.fromString(Objects.requireNonNull(cfg.getString(key+"world")));
pad.x=cfg.getInt(key+"x");pad.y=cfg.getInt(key+"y");pad.z=cfg.getInt(key+"z");
pad.original=Objects.requireNonNull(cfg.getString(key+"original"));pad.replacement=Material.valueOf(Objects.requireNonNull(cfg.getString(key+"replacement")));
pad.partnerOriginal=cfg.getString(key+"partnerOriginal");pad.partnerY=cfg.getInt(key+"partnerY");
if(!restorePad(pad)){pad.expire=0L;pads.put(pad.key(),pad);}
}catch(Exception ex){getLogger().warning("Invalid saved liquid path "+id);}
savePads();
}
private void savePads(){
YamlConfiguration cfg=new YamlConfiguration();int n=0;
for(Pad pad:pads.values()){
String k="paths.p"+(n++)+".";cfg.set(k+"world",pad.world.toString());cfg.set(k+"x",pad.x);cfg.set(k+"y",pad.y);cfg.set(k+"z",pad.z);
cfg.set(k+"original",pad.original);cfg.set(k+"replacement",pad.replacement.name());
if(pad.partnerOriginal!=null){cfg.set(k+"partnerOriginal",pad.partnerOriginal);cfg.set(k+"partnerY",pad.partnerY);}
}
try{cfg.save(padsFile);padsChanged=false;}catch(IOException ex){getLogger().severe("Failed to save liquid paths: "+ex.getMessage());}
}
private boolean restorePad(Pad pad){
World w=Bukkit.getWorld(pad.world);if(w==null||!w.isChunkLoaded(pad.x>>4,pad.z>>4))return false;
Block b=w.getBlockAt(pad.x,pad.y,pad.z);
if(b.getType()!=pad.replacement)return true;
try{
b.setBlockData(Bukkit.createBlockData(pad.original),false);
if(pad.partnerOriginal!=null&&pad.partnerY>=w.getMinHeight()&&pad.partnerY<w.getMaxHeight()){
Block partner=w.getBlockAt(pad.x,pad.partnerY,pad.z);
if(partner.getType().isAir()||partner.getType()==Material.WATER||partner.getType()==Material.TALL_SEAGRASS){
partner.setBlockData(Bukkit.createBlockData(pad.partnerOriginal),false);
}
}
return true;
}catch(IllegalArgumentException e){getLogger().warning("Could not restore temporary plant "+pad.key()+": "+e.getMessage());return false;}
}
private void addPad(World w,int x,int y,int z){
Block b=w.getBlockAt(x,y,z);String k=w.getUID()+":"+x+":"+y+":"+z;
Pad existing=pads.get(k);
if(existing!=null){existing.expire=System.currentTimeMillis()+(wasWater(existing.original)?WATER_DURATION:LAVA_DURATION);return;}
if(pads.size()>=MAX_PADS)return;
Material fluid=b.getType();boolean water=fluid==Material.WATER||waterPlant(fluid);
if(!water&&fluid!=Material.LAVA)return;
BlockData original=b.getBlockData();
if(!waterPlant(fluid)&&(!(original instanceof org.bukkit.block.data.Levelled lvl)||lvl.getLevel()!=0))return;
Pad pad=new Pad();pad.world=w.getUID();pad.x=x;pad.y=y;pad.z=z;pad.original=original.getAsString();
if(fluid==Material.TALL_SEAGRASS&&original instanceof org.bukkit.block.data.Bisected bisected){
int otherY=y+(bisected.getHalf()==org.bukkit.block.data.Bisected.Half.TOP?-1:1);
Block partner=w.getBlockAt(x,otherY,z);
if(partner.getType()!=Material.TALL_SEAGRASS)return;
pad.partnerOriginal=partner.getBlockData().getAsString();pad.partnerY=otherY;
}
pad.replacement=water?randomMoss():lavaStone(w,x,y,z);
pad.expire=System.currentTimeMillis()+(water?WATER_DURATION:LAVA_DURATION);
b.setType(pad.replacement,false);pads.put(pad.key(),pad);padsChanged=true;
}
private boolean waterPlant(Material type){
return type==Material.KELP||type==Material.KELP_PLANT||type==Material.SEAGRASS||type==Material.TALL_SEAGRASS;
}
private boolean wasWater(String original){
return original.contains("water")||original.contains("kelp")||original.contains("seagrass");
}
private Material randomMoss(){
Material[] variants={Material.MOSS_BLOCK,Material.MOSS_BLOCK,Material.MOSSY_COBBLESTONE,Material.MOSSY_STONE_BRICKS,Material.PALE_MOSS_BLOCK,Material.ROOTED_DIRT};
return variants[random.nextInt(variants.length)];
}
private Material lavaStone(World world,int x,int y,int z){
Block[] nearby={world.getBlockAt(x,y-1,z),world.getBlockAt(x+1,y,z),world.getBlockAt(x-1,y,z),world.getBlockAt(x,y,z+1),world.getBlockAt(x,y,z-1)};
for(Block b:nearby){
Material m=b.getType();
if(m==Material.BASALT||m==Material.SMOOTH_BASALT||m==Material.BLACKSTONE||m==Material.POLISHED_BLACKSTONE||m==Material.DEEPSLATE||m==Material.COBBLED_DEEPSLATE||m==Material.TUFF||m==Material.SOUL_SOIL||m==Material.MAGMA_BLOCK)return m==Material.MAGMA_BLOCK?Material.BASALT:m;
}
return random.nextBoolean()?Material.BLACKSTONE:Material.BASALT;
}
private boolean sourceLiquid(Block b){
Material type=b.getType();
if(waterPlant(type))return true;
if(type!=Material.WATER&&type!=Material.LAVA)return false;
return b.getBlockData() instanceof org.bukkit.block.data.Levelled level&&level.getLevel()==0;
}
/** The top liquid layer (or one of our temporary pads), never a block inside the player. */
private int surfaceUnder(Location to){
World w=to.getWorld();if(w==null)return Integer.MIN_VALUE;
int x=to.getBlockX(),z=to.getBlockZ(),feet=to.getBlockY();
for(int y=Math.min(w.getMaxHeight()-3,feet+4);y>=Math.max(w.getMinHeight(),feet-5);y--){
Block b=w.getBlockAt(x,y,z);
Pad existing=pads.get(w.getUID()+":"+x+":"+y+":"+z);
if(!sourceLiquid(b)&&(existing==null||b.getType()!=existing.replacement))continue;
Block above=w.getBlockAt(x,y+1,z);
Material upper=above.getType();
if(upper==Material.WATER||upper==Material.LAVA)return Integer.MIN_VALUE;
if(!above.isPassable()||!w.getBlockAt(x,y+2,z).isPassable())continue;
return y;
}
return Integer.MIN_VALUE;
}
private void spawnPath(Player p,Location to,PlayerMoveEvent movement){
if(p.isFlying()||p.getGameMode()==GameMode.SPECTATOR||to.getWorld()==null)return;
World w=to.getWorld();
int y=surfaceUnder(to);
if(y==Integer.MIN_VALUE){
if(to.getBlock().isLiquid()||waterPlant(to.getBlock().getType())){
Vector velocity=p.getVelocity();
if(velocity.getY()<.82)p.setVelocity(new Vector(velocity.getX(),.82,velocity.getZ()));
}
return;
}
// Do not create a platform while high above the water.
if(to.getY()>y+3.2)return;
int x=to.getBlockX(),z=to.getBlockZ();
String key=w.getUID()+":"+x+":"+y+":"+z;
long now=System.currentTimeMillis();
if(!key.equals(pathCenters.get(p.getUniqueId()))||now-pathRefresh.getOrDefault(p.getUniqueId(),0L)>=550L){
for(int dx=-PATH_RADIUS;dx<=PATH_RADIUS;dx++)for(int dz=-PATH_RADIUS;dz<=PATH_RADIUS;dz++){
if(dx*dx+dz*dz>PATH_RADIUS*PATH_RADIUS)continue;
addPad(w,x+dx,y,z+dz);
}
pathCenters.put(p.getUniqueId(),key);
pathRefresh.put(p.getUniqueId(),now);
}
// Lift just once when feet are actually below the new solid surface; don't interfere with jumps.
double top=y+1.0;
if(to.getY()<top-.035){
Location raised=to.clone();raised.setY(top+.04);
p.setFallDistance(0);
if(movement!=null)movement.setTo(raised);
else p.teleport(raised);
Vector velocity=p.getVelocity();
if(velocity.getY()<.12)p.setVelocity(new Vector(velocity.getX(),.12,velocity.getZ()));
}
}
@EventHandler(priority=EventPriority.MONITOR)public void chunkLoaded(org.bukkit.event.world.ChunkLoadEvent event){
UUID world=event.getWorld().getUID();int cx=event.getChunk().getX(),cz=event.getChunk().getZ();
for(Pad pad:new ArrayList<>(pads.values()))if(pad.world.equals(world)&&(pad.x>>4)==cx&&(pad.z>>4)==cz&&restorePad(pad)){pads.remove(pad.key());padsChanged=true;}
}
@EventHandler(priority=EventPriority.MONITOR)public void chunkUnloading(org.bukkit.event.world.ChunkUnloadEvent event){
UUID world=event.getWorld().getUID();int cx=event.getChunk().getX(),cz=event.getChunk().getZ();
for(Pad pad:new ArrayList<>(pads.values()))if(pad.world.equals(world)&&(pad.x>>4)==cx&&(pad.z>>4)==cz&&restorePad(pad)){pads.remove(pad.key());padsChanged=true;}
if(padsChanged)savePads();
}
private void cleanupPads(){
long time=System.currentTimeMillis();
for(Pad pad:new ArrayList<>(pads.values()))if(time>=pad.expire&&restorePad(pad)){pads.remove(pad.key());padsChanged=true;}
if(padsChanged&&pulses%2==0)savePads();
}
private void loadAltars(){
if(!saveFile.isFile())return;
YamlConfiguration y=YamlConfiguration.loadConfiguration(saveFile);
ConfigurationSection c=y.getConfigurationSection("chests");if(c==null)return;
for(String id:c.getKeys(false))try{
String p="chests."+id+".";Altar a=new Altar();
a.id=UUID.fromString(id);a.owner=UUID.fromString(y.getString(p+"owner"));a.world=UUID.fromString(y.getString(p+"world"));
a.x=y.getInt(p+"x");a.y=y.getInt(p+"y");a.z=y.getInt(p+"z");a.due=y.getLong(p+"due");a.ready=y.getBoolean(p+"ready");
a.base=Material.matchMaterial(y.getString(p+"base","CHEST"));if(a.base==null)a.base=Material.CHEST;
altars.put(a.index(),a);
}catch(Exception ex){getLogger().warning("Invalid ritual chest "+id+" "+ex);}
}
private void saveAltars(){
YamlConfiguration y=new YamlConfiguration();
for(Altar a:altars.values()){
String p="chests."+a.id+".";
y.set(p+"owner",a.owner.toString());y.set(p+"world",a.world.toString());
y.set(p+"x",a.x);y.set(p+"y",a.y);y.set(p+"z",a.z);y.set(p+"due",a.due);y.set(p+"ready",a.ready);y.set(p+"base",a.base.name());
}
try{y.save(saveFile);}catch(IOException e){getLogger().severe("Could not save ritual chests: "+e);}
}
private void removeAltar(Altar a){
if(altars.remove(a.index())==null)return;
UUID id=holograms.remove(a.id);if(id!=null){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
UUID visual=relicDisplays.remove(a.id);if(visual!=null){Entity e=Bukkit.getEntity(visual);if(e!=null)e.remove();}
saveAltars();
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){
if(protectedAltar(e.getBlockPlaced())!=null){e.setCancelled(true);return;}
if(!specialChest(e.getItemInHand()))return;
Block b=e.getBlockPlaced();Player p=e.getPlayer();
if(!canBeam(b)){e.setCancelled(true);p.sendMessage("§cНедостаточно места над Реликварием.");return;}
Altar a=new Altar();a.owner=p.getUniqueId();a.world=b.getWorld().getUID();a.x=b.getX();a.y=b.getY();a.z=b.getZ();a.base=b.getType();a.due=System.currentTimeMillis()+OPEN_DELAY;
altars.put(a.index(),a);saveAltars();
p.sendMessage("§b✦ Реликварий установлен! §7Координаты: "+a.x+", "+a.y+", "+a.z);
showRelicBlock(a);
p.sendMessage("§7Светящийся луч без блока маяка. Открытие через §d03:00§7.");
b.getWorld().playSound(b.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,.8f);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void padBreak(BlockBreakEvent e){
if(pads.containsKey(index(e.getBlock().getLocation()))){e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void padPlace(BlockPlaceEvent e){
if(pads.containsKey(index(e.getBlockPlaced().getLocation())))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void padExplosion(EntityExplodeEvent e){e.blockList().removeIf(b->pads.containsKey(index(b.getLocation())));}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void padBlockExplosion(BlockExplodeEvent e){e.blockList().removeIf(b->pads.containsKey(index(b.getLocation())));}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void padPiston(BlockPistonExtendEvent e){
if(e.getBlocks().stream().anyMatch(b->pads.containsKey(index(b.getLocation()))))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void padPistonRetract(BlockPistonRetractEvent e){
if(e.getBlocks().stream().anyMatch(b->pads.containsKey(index(b.getLocation()))))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){
Altar a=protectedAltar(e.getBlock());if(a==null)return;
e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);
if(!a.index().equals(index(e.getBlock().getLocation()))){e.getPlayer().sendMessage("§cМагический маяк нельзя ломать отдельно.");return;}
if(!a.owner.equals(e.getPlayer().getUniqueId())){e.getPlayer().sendMessage("§cРеликварий может сломать только его владелец.");return;}
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
e.getPlayer().sendMessage(a.ready?"§aРитуал завершён. Подойди к Реликварию за наградой.":"§dДо пробуждения: §f"+String.format("%02d:%02d",s/60,s%60));
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
e.getEntity().getWorld().spawnParticle(Particle.DUST,e.getEntity().getLocation(),50,1,1,1,
new Particle.DustOptions(Color.fromRGB(162,83,182),1.2f));
}
if(entity==EntityType.ELDER_GUARDIAN&&random.nextDouble()<getConfig().getDouble("elder-guardian-poseidon",.25))e.getDrops().add(talisman(Talisman.POSEIDON,1));
if(entity==EntityType.BLAZE&&random.nextDouble()<getConfig().getDouble("blaze-hades",.015))e.getDrops().add(talisman(Talisman.HADES,1));
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void netherLoot(org.bukkit.event.world.LootGenerateEvent e){
if(e.getWorld().getEnvironment()!=World.Environment.NETHER)return;
String id=e.getLootTable().getKey().toString();
Talisman choice=null;double prob=0;
if(id.contains("bastion_treasure")){choice=Talisman.HADES;prob=.075;}
else if(id.contains("bastion_bridge")){choice=Talisman.WRATH;prob=.003;}
else if(id.contains("bastion_other")||id.contains("bastion_hoglin_stable")){choice=Talisman.DEFIANCE;prob=.027;}
else if(id.contains("nether_bridge")||id.contains("fortress")){choice=Talisman.WRATH;prob=.001;}
if(choice!=null&&random.nextDouble()<prob*getConfig().getDouble("nether-loot-multiplier",1))e.getLoot().add(talisman(choice,initialLives(choice)));
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
private void removeDisplays(List<UUID> ids){
if(ids==null)return;
for(UUID uuid:ids){
Entity entity=Bukkit.getEntity(uuid);
if(entity!=null)entity.remove();
}
}
private boolean friendly(Player owner,LivingEntity target){
if(target==owner||target instanceof ArmorStand)return true;
if(target instanceof Player other){
org.bukkit.scoreboard.Team a=owner.getScoreboard().getEntryTeam(owner.getName());
org.bukkit.scoreboard.Team b=owner.getScoreboard().getEntryTeam(other.getName());
return a!=null&&a.equals(b);
}
return false;
}
private void setSwordRetaliation(Player player,LivingEntity target){
if(held(player)!=Talisman.END||target==player||friendly(player,target))return;
swordTarget.put(player.getUniqueId(),target.getUniqueId());
swordExpiry.put(player.getUniqueId(),System.currentTimeMillis()+8500L);
}
private void clearEndSwords(UUID id){
List<UUID> displays=swordVisuals.remove(id);
if(displays!=null){for(UUID display:displays)swordCooldown.remove(display);}
removeDisplays(displays);
swordTarget.remove(id);swordExpiry.remove(id);
}
private void endSwordTick(){
long now=System.currentTimeMillis();
for(Player owner:Bukkit.getOnlinePlayers()){
UUID id=owner.getUniqueId();
if(owner.isDead()||held(owner)!=Talisman.END){clearEndSwords(id);endShieldHits.remove(id);continue;}
List<UUID> ids=swordVisuals.get(id);
if(ids==null||ids.size()!=2||ids.stream().anyMatch(uuid->!(Bukkit.getEntity(uuid) instanceof ItemDisplay))){
if(ids!=null)for(UUID previous:ids)swordCooldown.remove(previous);
removeDisplays(swordVisuals.remove(id));
ids=new ArrayList<>();
for(int i=0;i<2;i++){
ItemDisplay display=owner.getWorld().spawn(owner.getLocation().clone().add(0,1.3,0),ItemDisplay.class,d->{
d.setPersistent(false);d.setInvulnerable(true);d.setTeleportDuration(5);d.setViewRange(1.4f);
d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.THIRDPERSON_RIGHTHAND);
d.setItemStack(new ItemStack(Material.GOLDEN_SWORD));
});
ids.add(display.getUniqueId());
swordCooldown.put(display.getUniqueId(),now+i*850);
}
swordVisuals.put(id,ids);
}
LivingEntity enemy=null;
if(swordExpiry.getOrDefault(id,0L)>now){
UUID targetId=swordTarget.get(id);
Entity raw=targetId==null?null:Bukkit.getEntity(targetId);
if(raw instanceof LivingEntity e&&e.isValid()&&!e.isDead()&&e.getWorld()==owner.getWorld()
&&e.getLocation().distanceSquared(owner.getLocation())<=40*40&&!friendly(owner,e))enemy=e;
}
org.bukkit.attribute.AttributeInstance max=owner.getAttribute(Attribute.MAX_HEALTH);
boolean shieldMode=max!=null&&owner.getHealth()<=max.getValue()*.5;
double phase=now/620.0;
for(int i=0;i<2;i++){
UUID swordId=ids.get(i);
Entity raw=Bukkit.getEntity(swordId);
if(!(raw instanceof ItemDisplay display)||!display.isValid())continue;
boolean guard=shieldMode&&i==0;
Material wanted=guard?Material.SHIELD:Material.GOLDEN_SWORD;
if(display.getItemStack().getType()!=wanted)display.setItemStack(new ItemStack(wanted));
double angle=phase+i*Math.PI;
Location point=owner.getLocation().clone().add(Math.cos(angle)*.9,1.35+Math.sin(phase+i)*.18,Math.sin(angle)*.9);
if(guard){
Vector front=owner.getLocation().getDirection().setY(0);
if(front.lengthSquared()<.01)front=new Vector(0,0,1);
point=owner.getLocation().clone().add(front.normalize().multiply(.9)).add(0,1.25,0);
point.setYaw(owner.getLocation().getYaw());
}else if(enemy!=null){
// Boomerang flight: the sword pursues the last attacker up to 40 blocks.
// It curves through space instead of teleporting into the opponent's face.
Vector toward=enemy.getEyeLocation().toVector().subtract(owner.getEyeLocation().toVector());
double fraction=.55+.32*Math.sin(phase*1.5+i*Math.PI);
fraction=Math.max(.15,Math.min(.90,fraction));
point=owner.getEyeLocation().clone().add(toward.multiply(fraction));
point.add(Math.cos(angle)*.4,Math.sin(phase+i)*.20,Math.sin(angle)*.4);
if(now>=swordCooldown.getOrDefault(swordId,0L)&&owner.hasLineOfSight(enemy)){
swordCooldown.put(swordId,now+(shieldMode?1250L:1900L));
final LivingEntity target=enemy;
Bukkit.getScheduler().runTask(this,()->{
if(target.isValid()&&!target.isDead()&&owner.isOnline()&&held(owner)==Talisman.END
&&target.getWorld()==owner.getWorld()&&target.getLocation().distanceSquared(owner.getLocation())<=1600){
target.damage(shieldMode?3.5:3.0,owner);
target.getWorld().spawnParticle(Particle.SMALL_FLAME,target.getEyeLocation(),10,.25,.25,.25,.04);
}
});
}
owner.getWorld().spawnParticle(Particle.FLAME,point,2,.1,.1,.1,.02);
}
if(point.getWorld()!=null&&point.getWorld().isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4))display.teleport(point);
}
}
for(UUID id:new ArrayList<>(swordVisuals.keySet()))if(Bukkit.getPlayer(id)==null)clearEndSwords(id);
}
private void removePoseidonCompanions(UUID id){
removeDisplays(poseidonCompanions.remove(id));
companionAttackCooldown.remove(id);
}
private void poseidonCompanionTick(){
long now=System.currentTimeMillis();
for(Player owner:Bukkit.getOnlinePlayers()){
UUID id=owner.getUniqueId();
boolean raining=held(owner)==Talisman.POSEIDON&&isRaining(owner)&&!owner.isDead()&&owner.getGameMode()!=GameMode.SPECTATOR;
if(!raining){removePoseidonCompanions(id);continue;}
List<UUID> companions=poseidonCompanions.get(id);
if(companions==null||companions.size()!=3||companions.stream().anyMatch(uuid->{Entity ent=Bukkit.getEntity(uuid);return ent==null||!ent.isValid()||ent.getWorld()!=owner.getWorld();})){
removePoseidonCompanions(id);companions=new ArrayList<>();
for(int i=0;i<2;i++){
PufferFish fish=owner.getWorld().spawn(owner.getLocation().add(0,1.4,0),PufferFish.class,entity->{
entity.setAI(false);entity.setGravity(false);entity.setInvulnerable(true);
entity.setCollidable(false);entity.setSilent(true);entity.setPersistent(false);
});
companions.add(fish.getUniqueId());
}
Axolotl axo=owner.getWorld().spawn(owner.getLocation().add(0,0.2,0),Axolotl.class,entity->{
entity.setAI(false);entity.setGravity(false);entity.setInvulnerable(true);
entity.setCollidable(false);entity.setSilent(true);entity.setPersistent(false);
});
companions.add(axo.getUniqueId());
poseidonCompanions.put(id,companions);
}
double phase=now/900.0;
Map<UUID,Long> poisoned=companionAttackCooldown.computeIfAbsent(id,key->new HashMap<>());
for(int i=0;i<3;i++){
Entity creature=Bukkit.getEntity(companions.get(i));
if(creature==null)continue;
double angle=phase+i*Math.PI*2/3;
Location point=owner.getLocation().clone().add(Math.cos(angle)*(i==2?1.0:1.35),i==2?0.2:1.65+Math.sin(angle)*.22,Math.sin(angle)*(i==2?1.0:1.35));
if(i==2&&!owner.isInWater())point=owner.getLocation().clone().add(0,.2,0);
if(point.getBlock().isPassable()&&owner.getWorld().isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4))creature.teleport(point);
if(i==2){
if(owner.isInWater()){
PotionEffect old=owner.getPotionEffect(PotionEffectType.REGENERATION);
if(old==null||old.getAmplifier()==0&&old.getDuration()<55)
owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,65,0,true,false,true));
}
}else{
for(Entity ent:creature.getNearbyEntities(2.7,2.5,2.7)){
if(!(ent instanceof LivingEntity victim)||victim.isDead()||friendly(owner,victim)||ent instanceof PufferFish||ent instanceof Axolotl)continue;
if(!(victim instanceof Monster)&&!(victim instanceof Player))continue;
if(now<poisoned.getOrDefault(victim.getUniqueId(),0L))continue;
poisoned.put(victim.getUniqueId(),now+3300L);
victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0));
creature.getWorld().spawnParticle(Particle.BUBBLE,victim.getEyeLocation(),4,.2,.3,.2,.02);
}
}
}
poisoned.entrySet().removeIf(e->e.getValue()<now-5000);
}
for(UUID id:new ArrayList<>(poseidonCompanions.keySet()))if(Bukkit.getPlayer(id)==null)removePoseidonCompanions(id);
}

private void quarterTick(){
moveDroppedTalismans();moveShieldVisuals();endSwordTick();poseidonCompanionTick();
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
if(voidSlot(p)!=null&&p.getLocation().getY()<=p.getWorld().getMinHeight()+6)voidRescue(p);
}
dolphinTick();
for(Player p:Bukkit.getOnlinePlayers())if(held(p)==Talisman.AGES)spawnPath(p,p.getLocation(),null);
}
private void removeShieldVisuals(ShieldRing ring){
for(UUID id:ring.visuals){if(id==null)continue;Entity entity=Bukkit.getEntity(id);if(entity!=null)entity.remove();}
}
private void saveShieldState(Player p,ShieldRing ring){
p.getPersistentDataContainer().set(new NamespacedKey(this,"defiance_shield_index"),PersistentDataType.INTEGER,ring.index);
p.getPersistentDataContainer().set(new NamespacedKey(this,"defiance_shield_health"),PersistentDataType.DOUBLE,ring.health);
}
private void clearShieldState(Player p){
p.getPersistentDataContainer().remove(new NamespacedKey(this,"defiance_shield_index"));
p.getPersistentDataContainer().remove(new NamespacedKey(this,"defiance_shield_health"));
}
private void spawnShieldVisuals(Player p,ShieldRing ring){
for(int i=0;i<3;i++){
if(i<ring.index){ring.visuals.add(null);continue;}
ItemDisplay d=p.getWorld().spawn(p.getLocation().clone().add(0,1.2,0),ItemDisplay.class,display->{
display.setItemStack(new ItemStack(Material.SHIELD));display.setBillboard(Display.Billboard.CENTER);display.setPersistent(false);display.setInvulnerable(true);
display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
display.setTeleportDuration(5);display.setViewRange(1.6f);
display.setTransformation(new org.bukkit.util.Transformation(
new org.joml.Vector3f(0f,0f,0f),new org.joml.Quaternionf(),
new org.joml.Vector3f(.75f,.75f,.75f),new org.joml.Quaternionf()));
});
ring.visuals.add(d.getUniqueId());
}
}
private void restoreShieldRing(Player p){
Integer index=p.getPersistentDataContainer().get(new NamespacedKey(this,"defiance_shield_index"),PersistentDataType.INTEGER);
if(index==null||index<0||index>2)return;
ShieldRing previous=shieldRings.remove(p.getUniqueId());if(previous!=null)removeShieldVisuals(previous);
ShieldRing ring=new ShieldRing();ring.index=index;
ring.health=Math.min(4,Math.max(.01,p.getPersistentDataContainer().getOrDefault(new NamespacedKey(this,"defiance_shield_health"),PersistentDataType.DOUBLE,4.0)));
spawnShieldVisuals(p,ring);shieldRings.put(p.getUniqueId(),ring);
}
@EventHandler public void playerJoined(PlayerJoinEvent e){
Bukkit.getScheduler().runTask(this,()->{if(e.getPlayer().isOnline()){restoreShieldRing(e.getPlayer());restoreHadesMode(e.getPlayer());}});
}
private void createShieldRing(Player p){
ShieldRing existing=shieldRings.remove(p.getUniqueId());if(existing!=null)removeShieldVisuals(existing);
ShieldRing ring=new ShieldRing();spawnShieldVisuals(p,ring);
shieldRings.put(p.getUniqueId(),ring);saveShieldState(p,ring);
p.getWorld().playSound(p.getLocation(),Sound.ITEM_SHIELD_BLOCK,1,.65f);
p.sendMessage("§bТалисман рассыпался: три призрачных щита защищают от ударов.");
}
private void moveShieldVisuals(){
for(Iterator<Map.Entry<UUID,ShieldRing>> it=shieldRings.entrySet().iterator();it.hasNext();){
Map.Entry<UUID,ShieldRing> entry=it.next();Player p=Bukkit.getPlayer(entry.getKey());ShieldRing ring=entry.getValue();
if(p==null||!p.isOnline()||p.isDead()){removeShieldVisuals(ring);it.remove();continue;}
double phase=System.currentTimeMillis()/850.0;
boolean brokenVisual=false;
for(int i=ring.index;i<3;i++){
UUID visualId=i<ring.visuals.size()?ring.visuals.get(i):null;
Entity entity=visualId==null?null:Bukkit.getEntity(visualId);
if(!(entity instanceof ItemDisplay display)||!display.isValid()||display.getWorld()!=p.getWorld()){brokenVisual=true;continue;}
double a=phase+i*Math.PI*2/3;
Location loc=p.getLocation().clone().add(Math.cos(a)*.95,1.12+Math.sin(phase*2+i)*.08,Math.sin(a)*.95);
display.teleport(loc);
}
if(brokenVisual){removeShieldVisuals(ring);ring.visuals.clear();spawnShieldVisuals(p,ring);}
}
}
private boolean absorbByShield(Player p,EntityDamageEvent e){
ShieldRing ring=shieldRings.get(p.getUniqueId());
if(ring==null||e.getFinalDamage()<=0)return false;
if(e.getCause()==EntityDamageEvent.DamageCause.VOID||e.getCause()==EntityDamageEvent.DamageCause.KILL)return false;
double amount=e.getFinalDamage();e.setCancelled(true);
ring.health-=amount;saveShieldState(p,ring);
p.getWorld().playSound(p.getLocation(),Sound.ITEM_SHIELD_BLOCK,.8f,1.15f);
p.getWorld().spawnParticle(Particle.END_ROD,p.getLocation().clone().add(0,1,0),6,.45,.3,.45,.01);
if(ring.health<=0){
UUID id=ring.index<ring.visuals.size()?ring.visuals.get(ring.index):null;Entity display=id==null?null:Bukkit.getEntity(id);if(display!=null)display.remove();
ring.index++;ring.health=4.0;
p.getWorld().playSound(p.getLocation(),Sound.ITEM_SHIELD_BREAK,.85f,1.4f);
if(ring.index>=3){shieldRings.remove(p.getUniqueId());clearShieldState(p);p.sendMessage("§7Последний призрачный щит разрушен.");}
else saveShieldState(p,ring);
}
return true;
}
private int wrathCritical(Player attacker,boolean isCritical){
UUID id=attacker.getUniqueId();
if(!isCritical){wrathStreak.remove(id);wrathLastCrit.remove(id);return 0;}
long now=System.currentTimeMillis();
int count=now-wrathLastCrit.getOrDefault(id,0L)>5000?1:Math.min(4,wrathStreak.getOrDefault(id,0)+1);
wrathStreak.put(id,count);wrathLastCrit.put(id,now);
return count;
}
private void wrathArmorWear(Player victim,int count){
if(count<=1||random.nextDouble()>=(count-1)/6.0)return;
PlayerInventory inv=victim.getInventory();List<EquipmentSlot> candidates=new ArrayList<>();
for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
ItemStack armor=inv.getItem(slot);
if(armor!=null&&!armor.getType().isAir()&&armor.getType().getMaxDurability()>0&&armor.hasItemMeta()&&!armor.getItemMeta().isUnbreakable())candidates.add(slot);
}
if(candidates.isEmpty())return;
EquipmentSlot slot=candidates.get(random.nextInt(candidates.size()));
ItemStack armor=inv.getItem(slot);if(armor==null)return;
ItemMeta meta=armor.getItemMeta();
if(!(meta instanceof org.bukkit.inventory.meta.Damageable damageable))return;
int unbreaking=armor.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
if(random.nextInt(unbreaking+1)>0)return;
int next=damageable.getDamage()+1;
if(next>=armor.getType().getMaxDurability()){
inv.setItem(slot,null);
victim.getWorld().playSound(victim.getLocation(),Sound.ENTITY_ITEM_BREAK,.6f,1f);
}else{
damageable.setDamage(next);armor.setItemMeta(meta);inv.setItem(slot,armor);
}
victim.getWorld().spawnParticle(Particle.CRIT,victim.getLocation().add(0,1,0),3,.2,.25,.2,.02);
}
private void grantWrathCritRecharge(Player player){
UUID id=player.getUniqueId();
wrathCritRechargeUntil.put(id,System.currentTimeMillis()+850);
org.bukkit.attribute.AttributeInstance speed=player.getAttribute(Attribute.ATTACK_SPEED);
if(speed==null)return;
AttributeModifier old=speed.getModifier(wrathRechargeKey);
if(old!=null)speed.removeModifier(old);
// The bonus is temporary and starts ONLY after a real critical hit.
// It expires after 0.85s and is never a passive attack-speed buff.
speed.addModifier(new AttributeModifier(wrathRechargeKey,.30,AttributeModifier.Operation.ADD_SCALAR));
}
private void removeWrathCritRecharge(Player player){
wrathCritRechargeUntil.remove(player.getUniqueId());
org.bukkit.attribute.AttributeInstance speed=player.getAttribute(Attribute.ATTACK_SPEED);
if(speed==null)return;
AttributeModifier old=speed.getModifier(wrathRechargeKey);
if(old!=null)speed.removeModifier(old);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void wrathHealing(EntityRegainHealthEvent event){
if(!(event.getEntity() instanceof Player player)||held(player)!=Talisman.WRATH)return;
event.setAmount(Math.max(0,event.getAmount()*.60));
}
private void wrathRechargeTick(){
long now=System.currentTimeMillis();
for(UUID id:new ArrayList<>(wrathCritRechargeUntil.keySet())){
Player player=Bukkit.getPlayer(id);
if(player==null){wrathCritRechargeUntil.remove(id);continue;}
if(held(player)!=Talisman.WRATH||now>=wrathCritRechargeUntil.getOrDefault(id,0L))removeWrathCritRecharge(player);
}
}
private boolean safeSpectatorLanding(Location point){
World w=point.getWorld();
if(w==null||!w.getWorldBorder().isInside(point))return false;
if(!w.isChunkLoaded(point.getBlockX()>>4,point.getBlockZ()>>4))return false;
if(point.getBlockY()<=w.getMinHeight()||point.getBlockY()>=w.getMaxHeight()-2)return false;
return point.getBlock().isPassable()&&point.clone().add(0,1,0).getBlock().isPassable();
}
private void beginHadesSpectator(Player player){
UUID id=player.getUniqueId();
if(hadesPending.containsKey(id))return;
GameMode mode=player.getGameMode();
if(mode==GameMode.SPECTATOR)return;
hadesPending.put(id,mode);
NamespacedKey key=new NamespacedKey("talismanforge","hades_original_gamemode");
player.getPersistentDataContainer().set(key,PersistentDataType.STRING,mode.name());
Location start=player.getLocation().clone();
player.setGameMode(GameMode.SPECTATOR);
player.sendMessage("§4Дух Аида: три секунды, чтобы выбрать место возрождения!");
Bukkit.getScheduler().runTaskLater(this,()->finishHadesSpectator(player,start),60L);
}
private void finishHadesSpectator(Player player,Location fallback){
UUID id=player.getUniqueId();
GameMode previous=hadesPending.remove(id);
if(previous==null)return;
player.getPersistentDataContainer().remove(new NamespacedKey("talismanforge","hades_original_gamemode"));
if(!player.isOnline())return;
Location wanted=player.getLocation().clone();
if(!safeSpectatorLanding(wanted)){
Location candidate=wanted.clone();
boolean found=false;
for(int y=1;y<=5;y++){candidate=wanted.clone().add(0,y,0);if(safeSpectatorLanding(candidate)){found=true;break;}}
wanted=found?candidate:(safeSpectatorLanding(fallback)?fallback:player.getWorld().getSpawnLocation());
}
player.setGameMode(previous);
player.teleport(wanted);
player.setFallDistance(0);
player.setNoDamageTicks(40);
player.getWorld().spawnParticle(Particle.SOUL,wanted.clone().add(0,1,0),20,.5,.6,.5,.025);
player.sendMessage("§4Дух Аида вернулся в мир.");
}
private void restoreHadesMode(Player player){
String stored=player.getPersistentDataContainer().get(new NamespacedKey("talismanforge","hades_original_gamemode"),PersistentDataType.STRING);
if(stored==null)return;
player.getPersistentDataContainer().remove(new NamespacedKey("talismanforge","hades_original_gamemode"));
GameMode original;
try{original=GameMode.valueOf(stored);}catch(IllegalArgumentException e){original=GameMode.SURVIVAL;}
hadesPending.remove(player.getUniqueId());
if(player.getGameMode()==GameMode.SPECTATOR)player.setGameMode(original);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void hadesKillReward(EntityDeathEvent event){
LivingEntity victim=event.getEntity();
Player killer=victim.getKiller();
if(killer==null||held(killer)!=Talisman.HADES||victim==killer)return;
// The stolen gold hearts equal the victim's golden (absorption) hearts at death.
// No absorbable hearts means no stolen bonus; hard cap prevents indefinite stacking.
org.bukkit.attribute.AttributeInstance maxHealth=victim.getAttribute(Attribute.MAX_HEALTH);
// Each kill grants gold hearts equal to the fallen creature's normal maximum
// hearts; bosses are capped to 10 hearts per kill, and stacks to 20 hearts.
double available=maxHealth==null?2:Math.max(1,maxHealth.getValue());
double before=killer.getAbsorptionAmount();
double increase=Math.min(20,available);
killer.setAbsorptionAmount(Math.min(40,before+increase));
killer.sendMessage("§6Аид похитил "+String.format(Locale.ROOT,"%.1f",increase/2)+" золотых сердец.");
killer.getWorld().spawnParticle(Particle.SOUL,killer.getLocation().add(0,1,0),8,.3,.3,.3,.025);
}

private void agesFinale(Player p){
Location loc=p.getLocation();World w=p.getWorld();
List<Allay> fairies=new ArrayList<>();
for(int i=0;i<3;i++){
Allay allay=w.spawn(loc.clone().add(Math.cos(i*Math.PI*2/3)*.9,1.3,Math.sin(i*Math.PI*2/3)*.9),Allay.class,a->{
a.setAI(false);a.setSilent(true);a.setInvulnerable(true);a.setPersistent(false);a.setGravity(false);a.setCollidable(false);
});
fairies.add(allay);
}
final UUID owner=p.getUniqueId();
new org.bukkit.scheduler.BukkitRunnable(){
int iteration=0;
@Override public void run(){
Player target=Bukkit.getPlayer(owner);
if(iteration++>=25||target==null||target.isDead()){
for(Allay allay:fairies)if(allay.isValid())allay.remove();
cancel();return;
}
Location center=target.getLocation();
for(int i=0;i<fairies.size();i++){
Allay allay=fairies.get(i);if(!allay.isValid())continue;
double angle=iteration*.31+i*Math.PI*2/fairies.size();
Location point=center.clone().add(Math.cos(angle)*1.05,1.4+Math.sin(iteration*.28+i)*.33,Math.sin(angle)*1.05);
if(point.getBlock().isPassable())allay.teleport(point);
}
}
}.runTaskTimer(this,1L,2L);
for(Player other:w.getPlayers())if(other!=p&&other.getLocation().distanceSquared(loc)<=25){
other.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,40,0,true,false,true));
}
w.spawnParticle(Particle.HAPPY_VILLAGER,loc.clone().add(0,1,0),45,1,1,1,.06);
w.playSound(loc,Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM,1,1.2f);
}
private void voidRescue(Player p){
EquipmentSlot slot=voidSlot(p);if(slot==null)return;
Talisman t=itemType(equipped(p,slot));if(t==null)return;
if(t!=Talisman.END)unequip(p,slot);
Location target=safeLocations.get(p.getUniqueId());
if(target==null||target.getWorld()==null)target=Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(0,2,0);
if(!target.getChunk().isLoaded())target.getChunk().load();
p.teleport(target);p.setFallDistance(0);p.setVelocity(new Vector(0,0,0));p.setNoDamageTicks(50);
p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,100,0,true,false,true));
p.sendMessage(t==Talisman.END?"§dЗевс перенёс тебя из бездны без расхода талисмана!":"§dТалисман спас тебя из бездны, но был уничтожен.");
p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation(),90,.7,1,.7,.25);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void moved(PlayerMoveEvent e){
Player p=e.getPlayer();
if(e.getTo()!=null&&System.currentTimeMillis()<jokerStunnedUntil.getOrDefault(p.getUniqueId(),0L)){
Location frozen=e.getFrom().clone();frozen.setYaw(e.getTo().getYaw());frozen.setPitch(e.getTo().getPitch());
e.setTo(frozen);return;
}
if(e.getTo()==null||held(p)==null)return;
if(held(p)==Talisman.AGES)spawnPath(p,e.getTo(),e);
if(e.getTo().getY()<p.getWorld().getMinHeight()+6){if(voidSlot(p)!=null)voidRescue(p);return;}
if(e.getTo().getY()<p.getWorld().getMinHeight()+12)return;
if(p.isOnGround()&&p.getLocation().getBlock().isPassable()&&p.getLocation().subtract(0,1,0).getBlock().getType().isSolid())safeLocations.put(p.getUniqueId(),p.getLocation().clone());
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void shield(PlayerShieldDisableEvent e){if(held(e.getPlayer())==Talisman.DEFIANCE)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void damage(EntityDamageEvent e){
if(e.getEntity() instanceof Item item&&itemType(item.getItemStack())!=null&&itemType(item.getItemStack())!=Talisman.POSEIDON){
switch(e.getCause()){
case FIRE,FIRE_TICK,LAVA,HOT_FLOOR -> {e.setCancelled(true);return;}
default -> {}
}
}
if(!(e.getEntity() instanceof Player p))return;
if(e.getCause()==EntityDamageEvent.DamageCause.VOID&&voidSlot(p)!=null){e.setCancelled(true);voidRescue(p);return;}
if(System.currentTimeMillis()<agesInvulnerableUntil.getOrDefault(p.getUniqueId(),0L)&&e.getCause()!=EntityDamageEvent.DamageCause.VOID&&e.getCause()!=EntityDamageEvent.DamageCause.KILL){e.setCancelled(true);return;}
if(absorbByShield(p,e))return;
if(e.getCause()==EntityDamageEvent.DamageCause.FALL&&held(p)==Talisman.END){e.setCancelled(true);p.setFallDistance(0);return;}
if(held(p)==Talisman.END&&e.getFinalDamage()>0&&!endReflectionInProgress.contains(p.getUniqueId())){
org.bukkit.attribute.AttributeInstance max=p.getAttribute(Attribute.MAX_HEALTH);
if(max!=null&&p.getHealth()<=max.getValue()*.5
&&e.getCause()!=EntityDamageEvent.DamageCause.VOID&&e.getCause()!=EntityDamageEvent.DamageCause.KILL){
int count=endShieldHits.merge(p.getUniqueId(),1,Integer::sum);
if(count%2==0){
double reflected=e.getFinalDamage();
e.setCancelled(true);
LivingEntity source=null;
if(e instanceof EntityDamageByEntityEvent attack){
if(attack.getDamager() instanceof LivingEntity direct)source=direct;
else if(attack.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof LivingEntity shooter)source=shooter;
}
if(source!=null&&source!=p&&source.isValid()&&!friendly(p,source)){
final LivingEntity enemy=source;
Bukkit.getScheduler().runTask(this,()->{
if(!enemy.isValid()||enemy.isDead()||!p.isOnline())return;
if(!endReflectionInProgress.add(enemy.getUniqueId()))return;
try{enemy.damage(reflected,p);}finally{endReflectionInProgress.remove(enemy.getUniqueId());}
});
}
p.getWorld().playSound(p.getLocation(),Sound.ITEM_SHIELD_BLOCK,1f,1.2f);
return;
}
}
}

if(held(p)==Talisman.HADES){
switch(e.getCause()){
case FIRE,FIRE_TICK,HOT_FLOOR -> {e.setCancelled(true);p.setFireTicks(0);return;}
default -> {}
}
}
if(held(p)==Talisman.AGES){
switch(e.getCause()){
case FIRE,FIRE_TICK,LAVA,HOT_FLOOR -> {e.setCancelled(true);p.setFireTicks(0);}
default -> {}
}
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void byEntity(EntityDamageByEntityEvent e){
if(e.getDamager() instanceof EvokerFangs fangs&&fangs.getOwner()!=null&&fangs.getOwner().getUniqueId().equals(e.getEntity().getUniqueId())){e.setCancelled(true);return;}
if(e.getEntity() instanceof Player hades&&held(hades)==Talisman.HADES){
if(e.getDamager() instanceof Fireball fireball&&fireball.getShooter() instanceof Ghast){e.setCancelled(true);return;}
LivingEntity threat=null;
if(e.getDamager() instanceof LivingEntity direct)threat=direct;
else if(e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof LivingEntity ranged)threat=ranged;
if(threat!=null&&threat!=hades)ghastAssist(hades,threat);
}
if(e.getEntity() instanceof Player endangered&&held(endangered)==Talisman.END){
LivingEntity attackerSource=null;
if(e.getDamager() instanceof LivingEntity direct)attackerSource=direct;
else if(e.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof LivingEntity source)attackerSource=source;
if(attackerSource!=null)setSwordRetaliation(endangered,attackerSource);
}
if(e.getEntity() instanceof Player target&&held(target)==Talisman.END&&e.getDamager() instanceof Projectile projectile){
e.setCancelled(true);ProjectileSource shooter=projectile.getShooter();
if(shooter instanceof LivingEntity source&&source!=target&&source.isValid())source.damage(Math.min(20,Math.max(1,e.getFinalDamage())),target);
target.getWorld().spawnParticle(Particle.PORTAL,target.getEyeLocation(),24,.3,.3,.3,.1);
target.playSound(target.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,.35f,1.6f);return;
}
if(e.getEntity() instanceof Player target&&held(target)==Talisman.AGES
&&!agesThornsInProgress.contains(target.getUniqueId())&&e.getFinalDamage()>0){
LivingEntity thornsSource=null;
if(e.getDamager() instanceof LivingEntity direct)thornsSource=direct;
else if(e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof LivingEntity ranged)thornsSource=ranged;
if(thornsSource!=null&&thornsSource!=target){
final LivingEntity punished=thornsSource;
Bukkit.getScheduler().runTask(this,()->{
if(!punished.isValid()||punished.isDead()||!target.isOnline())return;
UUID punishedId=punished.getUniqueId();
if(!agesThornsInProgress.add(punishedId))return;
try{
punished.damage(2.0,target);
punished.getWorld().spawnParticle(Particle.CRIT,punished.getEyeLocation(),5,.15,.25,.15,.02);
}finally{agesThornsInProgress.remove(punishedId);}
});
}
}
if(e.getEntity() instanceof Player victim){
LivingEntity source=null;
if(e.getDamager() instanceof LivingEntity direct)source=direct;
else if(e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof LivingEntity shooter)source=shooter;
if(source!=null&&source!=victim){
lastAttacker.put(victim.getUniqueId(),source.getUniqueId());
lastAttackerTime.put(victim.getUniqueId(),System.currentTimeMillis());
}
}
if(e.getDamager() instanceof Player source&&System.currentTimeMillis()<jokerStunnedUntil.getOrDefault(source.getUniqueId(),0L)){
e.setCancelled(true);return;
}
if(!(e.getDamager() instanceof Player attacker))return;
Talisman t=held(attacker);if(t==null)return;
if(t==Talisman.WRATH){boolean crit=critical(attacker);int combo=wrathCritical(attacker,crit);e.setDamage(e.getDamage()*1.35*(1+combo*.01));if(combo>1&&e.getEntity() instanceof Player armorVictim)wrathArmorWear(armorVictim,combo);if(crit)grantWrathCritRecharge(attacker);}
if(t==Talisman.HADES&&e.getEntity() instanceof LivingEntity victim)ghastAssist(attacker,victim);
if(e.getEntity() instanceof LivingEntity victim){
if(t==Talisman.MAIMING){victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,45,2));victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,100,0));if(critical(attacker)){victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));victim.getWorld().spawnParticle(Particle.WITCH,victim.getLocation().add(0,1,0),4,.15,.2,.15,.02);}if(victim instanceof Player other&&e.getFinalDamage()>0)stealHunger(attacker,other);}
if(t==Talisman.POSEIDON&&isRaining(attacker)&&victim.getHealth()<=victim.getAttribute(Attribute.MAX_HEALTH).getValue()*.5)victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,70,1));
if(t==Talisman.END&&critical(attacker)){
Bukkit.getScheduler().runTask(this,()->{
if(!attacker.isOnline()||!victim.isValid()||victim.isDead())return;
victim.getWorld().strikeLightningEffect(victim.getLocation());
// Apply damage through Bukkit instead of direct setHealth(0), allowing
// armor, damage protection, other plugins and death-rescue talismans to work.
victim.damage(2.0,attacker);
});
}
}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void foodChanged(FoodLevelChangeEvent event){
if(!(event.getEntity() instanceof Player player)||editingFood.contains(player.getUniqueId()))return;
hungerTakenFractions.remove(player.getUniqueId());hungerGivenFractions.remove(player.getUniqueId());
}
@EventHandler(priority=EventPriority.MONITOR)public void clearHungerOnDeath(PlayerDeathEvent event){
UUID id=event.getEntity().getUniqueId();hungerTakenFractions.remove(id);hungerGivenFractions.remove(id);
ShieldRing ring=shieldRings.remove(id);if(ring!=null)removeShieldVisuals(ring);
clearShieldState(event.getEntity());
}
@EventHandler(priority=EventPriority.MONITOR)public void clearHungerOnRespawn(PlayerRespawnEvent event){
UUID id=event.getPlayer().getUniqueId();hungerTakenFractions.remove(id);hungerGivenFractions.remove(id);
}
private void changeHunger(Player p,int food){
UUID id=p.getUniqueId();editingFood.add(id);
try{p.setFoodLevel(Math.max(0,Math.min(20,food)));}finally{editingFood.remove(id);}
}
private void stealHunger(Player attacker,Player victim){
if(attacker==victim||victim.getFoodLevel()<=0)return;
UUID victimId=victim.getUniqueId(),attackerId=attacker.getUniqueId();
// Vanilla foodLevel uses whole units. Accumulate halves and apply one food point every second hit.
double stolen=Math.min(.5,victim.getFoodLevel()-hungerTakenFractions.getOrDefault(victimId,0.0));
if(stolen<=0)return;
double pending=hungerTakenFractions.getOrDefault(victimId,0.0)+stolen;
if(pending>=1.0){changeHunger(victim,victim.getFoodLevel()-1);pending-=1.0;}
if(pending>0)hungerTakenFractions.put(victimId,pending);else hungerTakenFractions.remove(victimId);
// Transfer only food actually taken; a full attacker cannot gain more than 20.
if(attacker.getFoodLevel()>=20)return;
double gain=hungerGivenFractions.getOrDefault(attackerId,0.0)+stolen;
if(gain>=1.0){changeHunger(attacker,attacker.getFoodLevel()+1);gain-=1.0;}
if(gain>0)hungerGivenFractions.put(attackerId,gain);else hungerGivenFractions.remove(attackerId);
}
private void ghastAssist(Player owner,LivingEntity enemy){
if(enemy==owner||!enemy.isValid()||enemy.isDead()||!owner.getWorld().equals(enemy.getWorld()))return;
long now=System.currentTimeMillis();
if(now-ghastCooldowns.getOrDefault(owner.getUniqueId(),0L)<3500L)return;
int fired=0;
for(Ghast ghast:owner.getWorld().getEntitiesByClass(Ghast.class)){
if(!ghast.isValid()||ghast.isDead()||ghast.getLocation().distanceSquared(owner.getLocation())>32*32||ghast.getLocation().distanceSquared(enemy.getLocation())>42*42||!ghast.hasLineOfSight(enemy))continue;
Vector direction=enemy.getEyeLocation().toVector().subtract(ghast.getEyeLocation().toVector());
if(direction.lengthSquared()<.001)continue;
LargeFireball shot=ghast.launchProjectile(LargeFireball.class,direction.normalize());
shot.setIsIncendiary(false);shot.setYield(0f);
ghast.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,ghast.getEyeLocation(),8,.3,.3,.3,.03);
fired++;if(fired>=2)break;
}
if(fired>0)ghastCooldowns.put(owner.getUniqueId(),now);
}
private void summonJokerAvenger(Player owner){
UUID attackerId=lastAttacker.get(owner.getUniqueId());
long since=System.currentTimeMillis()-lastAttackerTime.getOrDefault(owner.getUniqueId(),0L);
if(attackerId==null||since>4500)return;
Entity raw=Bukkit.getEntity(attackerId);
if(!(raw instanceof LivingEntity victim)||!victim.isValid()||victim.isDead()||victim.getWorld()!=owner.getWorld())return;
if(victim instanceof Player player&&friendly(owner,player))return;
long until=System.currentTimeMillis()+5000;
jokerStunnedUntil.put(victim.getUniqueId(),until);
if(victim instanceof Player player){
player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,105,7));
player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,105,3));
}else{
victim.setAI(false);
Bukkit.getScheduler().runTaskLater(this,()->{if(victim.isValid())victim.setAI(true);},100L);
}
Location where=victim.getLocation().clone().add(victim.getLocation().getDirection().multiply(-2));
Evoker avenger=owner.getWorld().spawn(where,Evoker.class,entity->{
entity.setAI(false);entity.setGravity(false);entity.setCollidable(false);
entity.setInvulnerable(true);entity.setSilent(true);entity.setPersistent(false);
org.bukkit.attribute.AttributeInstance size=entity.getAttribute(Attribute.SCALE);
if(size!=null)size.setBaseValue(2.65);
});
owner.getWorld().playSound(where,Sound.ENTITY_EVOKER_PREPARE_ATTACK,1.25f,.55f);
new org.bukkit.scheduler.BukkitRunnable(){
int elapsed=0;
@Override public void run(){
if(!avenger.isValid()||elapsed>=60){
if(avenger.isValid())avenger.remove();
cancel();return;
}
if(elapsed%10==0){
avenger.swingMainHand();
avenger.getWorld().spawnParticle(Particle.WITCH,avenger.getEyeLocation(),18,.5,.8,.5,.08);
}
elapsed+=5;
}
}.runTaskTimer(this,1L,5L);
owner.sendMessage("§dДжокер вызвал великого мстителя! Обидчик оглушён на 5 секунд.");
}
private void jokerBonuses(Player player){
List<PotionEffectType> choices=new ArrayList<>(List.of(
PotionEffectType.SPEED,PotionEffectType.STRENGTH,PotionEffectType.RESISTANCE
));
Collections.shuffle(choices,random);
int count=1+random.nextInt(choices.size());
List<String> descriptions=new ArrayList<>();
for(int i=0;i<count;i++){
PotionEffectType type=choices.get(i);
int seconds=20+random.nextInt(281);
int level=type==PotionEffectType.RESISTANCE?0:1;
player.addPotionEffect(new PotionEffect(type,seconds*20,level,false,true,true));
String title=type==PotionEffectType.SPEED?"Скорость II":type==PotionEffectType.STRENGTH?"Сила II":"Сопротивление I";
descriptions.add("§a"+title+" §7("+seconds+" сек.)");
}
player.sendMessage("§d§lДЖОКЕР! §7Случайные бонусы: §f"+count);
for(String description:descriptions)player.sendMessage(description);
}
private void jokerFangs(Player owner){
UUID id=owner.getUniqueId();
for(int wave=0;wave<3;wave++){
final int ring=wave;
Bukkit.getScheduler().runTaskLater(this,()->{
Player caster=Bukkit.getPlayer(id);
if(caster==null||caster.isDead())return;
World world=caster.getWorld();
Location center=caster.getLocation();
for(Entity ent:world.getNearbyEntities(center,9,4,9)){
if(!(ent instanceof LivingEntity target)||target==caster||!target.isValid()||target.isDead()||ent instanceof ArmorStand)continue;
Location at=target.getLocation().clone();
world.spawn(at,EvokerFangs.class,fang->{fang.setOwner(caster);fang.setAttackDelay(4+ring*2);});
}
world.spawnParticle(Particle.WITCH,center.clone().add(0,.8,0),30,.8,.5,.8,.03);
world.playSound(center,Sound.ENTITY_EVOKER_CAST_SPELL,1.0f,1.15f);
},ring*10L);
}
}
private boolean critical(Player p){return p.getFallDistance()>0&&!p.isOnGround()&&!p.isInWater()&&!p.isClimbing()&&!p.hasPotionEffect(PotionEffectType.BLINDNESS);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hostileTarget(EntityTargetLivingEntityEvent e){
if(!(e.getTarget() instanceof Player p))return;
if(held(p)==Talisman.END&&e.getEntityType()==EntityType.ENDERMAN){e.setCancelled(true);return;}
if(held(p)==Talisman.HADES&&e.getEntityType()==EntityType.GHAST){e.setCancelled(true);return;}
if(held(p)==Talisman.HADES&&e.getEntity() instanceof Monster&&!e.getReason().name().contains("ATTACKED"))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST)public void resurrect(EntityResurrectEvent e){
if(!(e.getEntity() instanceof Player p))return;
EquipmentSlot slot=e.getHand();
if(slot!=EquipmentSlot.OFF_HAND&&slot!=EquipmentSlot.HAND)return;
ItemStack equippedItem=equipped(p,slot);Talisman t=itemType(equippedItem);
if(t==null){
String oldType=equippedItem!=null&&equippedItem.hasItemMeta()?equippedItem.getItemMeta().getPersistentDataContainer().get(typeKey,PersistentDataType.STRING):null;
if("life".equals(oldType))e.setCancelled(true);
return;
}
int current=lives(equippedItem);
boolean can=t.lives>0&&current>0;
if(t==Talisman.POSEIDON)can&=(p.isInWater()||isRaining(p));
List<Player> victims=new ArrayList<>();
if(t==Talisman.HADES){
for(Player v:p.getWorld().getPlayers())if(v!=p&&v.getLocation().distanceSquared(p.getLocation())<=12*12)victims.add(v);
can&=!victims.isEmpty();
}
if(t==Talisman.JOKER){
double chance=p.hasPotionEffect(PotionEffectType.LUCK)?0.75:0.50;
if(can&&random.nextDouble()<chance){
p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.9f,1.45f);
}else{
can=false;
unequip(p,slot);
p.sendMessage("§cДжокер проиграл: талисман исчез, спасения нет.");
p.getWorld().playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,1.0f,.7f);
}
}
if(!can){e.setCancelled(true);return;}
e.setCancelled(false);
final List<Player> nearby=List.copyOf(victims);
Bukkit.getScheduler().runTask(this,()->{
if(!p.isOnline())return;
if(current>1){ItemStack next=talisman(t,current-1);ItemStack nowItem=equipped(p,slot);
if(nowItem.getType()==Material.AIR||nowItem.getAmount()==0){if(slot==EquipmentSlot.HAND)p.getInventory().setItemInMainHand(next);else p.getInventory().setItemInOffHand(next);}else give(p,next);
}
if(t==Talisman.HADES){
org.bukkit.attribute.AttributeInstance max=p.getAttribute(Attribute.MAX_HEALTH);if(max!=null)p.setHealth(Math.max(1,max.getValue()));
int hunger=0;
for(Player v:nearby){if(!v.isOnline()||v.isDead()||!v.getWorld().equals(p.getWorld())||v.getLocation().distanceSquared(p.getLocation())>12*12)continue;
v.setHealth(Math.max(1,v.getHealth()-4));int n=Math.min(v.getFoodLevel(),4);v.setFoodLevel(v.getFoodLevel()-n);hunger+=n;
v.getWorld().spawnParticle(Particle.SOUL,v.getLocation().add(0,1,0),20,.5,.5,.5,.03);
}
p.setFoodLevel(Math.min(20,p.getFoodLevel()+hunger));
beginHadesSpectator(p);
}
if(t==Talisman.JOKER){
jokerBonuses(p);
jokerFangs(p);
summonJokerAvenger(p);
}
if(t==Talisman.AGES&&current==2){
agesInvulnerableUntil.put(p.getUniqueId(),System.currentTimeMillis()+3000L);
p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,60,0,true,false,true));
p.getWorld().playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_RESONATE,1,1.2f);
}
if(t==Talisman.AGES&&current==1)agesFinale(p);
if(t==Talisman.DEFIANCE&&current==1)createShieldRing(p);
if(t==Talisman.WRATH&&current==1)p.sendMessage("§cКара спасла тебя в последний раз.");
reviveParticles(p.getLocation(),t);
p.sendMessage("§dТалисман спас тебя! §7Осталось спасений: §e"+(current-1));
});
}
@EventHandler(priority=EventPriority.MONITOR)public void death(PlayerDeathEvent e){
Player p=e.getEntity();
if(held(p)!=Talisman.POSEIDON||(p.isInWater()||isRaining(p)))return;
Location loc=p.getLocation().clone();World w=loc.getWorld();if(w==null)return;
for(int i=0;i<7;i++){
double angle=i*Math.PI*2/7;
Location strike=loc.clone().add(Math.cos(angle)*(2+i%3*2),0,Math.sin(angle)*(2+i%3*2));
w.strikeLightningEffect(strike); // visuals only: no fire or block damage
}
w.playSound(loc,Sound.ENTITY_LIGHTNING_BOLT_THUNDER,1,.75f);
for(Player other:w.getPlayers()){
if(other==p||held(other)==Talisman.POSEIDON||other.getLocation().distanceSquared(loc)>12*12)continue;
other.addPotionEffect(new PotionEffect(PotionEffectType.POISON,140,0));
other.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,140,0));
other.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,90,0));
}
spawnPhantomDolphin(loc,p.getUniqueId());
}
private void spawnPhantomDolphin(Location loc,UUID ownerId){
World w=loc.getWorld();if(w==null)return;
Dolphin d=w.spawn(loc.clone().add(0,1,0),Dolphin.class);
d.setGravity(false);d.setAI(false);d.setSilent(true);d.setGlowing(true);d.setCollidable(false);d.setInvulnerable(true);
org.bukkit.attribute.AttributeInstance scale=d.getAttribute(Attribute.SCALE);if(scale!=null)scale.setBaseValue(1.65);
d.getPersistentDataContainer().set(dolphinKey,PersistentDataType.BYTE,(byte)1);
ghostDolphins.put(d.getUniqueId(),System.currentTimeMillis()+12_000);
ghostDolphinOwners.put(d.getUniqueId(),ownerId);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void dolphinHit(EntityDamageEvent e){
if(!(e.getEntity() instanceof Dolphin d))return;
if(!ghostDolphins.containsKey(d.getUniqueId()))return;
e.setCancelled(true);
switch(e.getCause()){
case ENTITY_ATTACK,ENTITY_SWEEP_ATTACK,PROJECTILE -> {
ghostDolphins.remove(d.getUniqueId());ghostDolphinOwners.remove(d.getUniqueId());
d.remove();d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),40,.4,.4,.4,.05);
}
default -> {} // environmental damage cannot despawn a phasing ghost
}
}
private void dolphinTick(){
for(var entry:new ArrayList<>(ghostDolphins.entrySet())){
Entity raw=Bukkit.getEntity(entry.getKey());
if(!(raw instanceof Dolphin d)||!raw.isValid()){ghostDolphins.remove(entry.getKey());ghostDolphinOwners.remove(entry.getKey());continue;}
if(System.currentTimeMillis()>entry.getValue()){ghostDolphins.remove(entry.getKey());ghostDolphinOwners.remove(entry.getKey());d.remove();continue;}
Player nearest=null;double best=24*24;
for(Player p:d.getWorld().getPlayers()){
if(p.getUniqueId().equals(ghostDolphinOwners.get(d.getUniqueId()))||held(p)==Talisman.POSEIDON||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
double distance=p.getEyeLocation().distanceSquared(d.getLocation());
if(distance<best){best=distance;nearest=p;}
}
if(nearest!=null){
Vector travel=nearest.getEyeLocation().toVector().subtract(d.getLocation().toVector());
double distance=travel.length();
// Direct teleport bypasses normal pathfinding and makes the ghost phase through blocks.
if(distance>.05)d.teleport(d.getLocation().add(travel.normalize().multiply(Math.min(1.75,distance))));
if(d.getLocation().distanceSquared(nearest.getEyeLocation())<2.3*2.3){
nearest.damage(9.0,d);
nearest.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1));
d.getWorld().strikeLightningEffect(nearest.getLocation());
ghostDolphins.remove(d.getUniqueId());ghostDolphinOwners.remove(d.getUniqueId());d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),36,.5,.5,.5,.04);d.remove();continue;
}
}
d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),4,.3,.3,.3,.02);
}
}
private boolean isRaining(Player p){return p.getWorld().getEnvironment()==World.Environment.NORMAL&&p.getWorld().hasStorm();}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void externalPotionChanged(EntityPotionEffectEvent event){
if(!(event.getEntity() instanceof Player player)||editingPotions.contains(player.getUniqueId()))return;
Map<PotionEffectType,Integer> owned=grantedEffects.get(player.getUniqueId());
if(owned!=null){
owned.remove(event.getModifiedType());
if(owned.isEmpty())grantedEffects.remove(player.getUniqueId());
}
}
private void withManagedPotion(Player p,Runnable change){
UUID id=p.getUniqueId();editingPotions.add(id);
try{change.run();}finally{editingPotions.remove(id);}
}
private boolean isGranted(Player p,PotionEffectType type,int amplifier){
return Objects.equals(grantedEffects.getOrDefault(p.getUniqueId(),Map.of()).get(type),amplifier);
}
private void recordGranted(Player p,PotionEffectType type,int amplifier){
grantedEffects.computeIfAbsent(p.getUniqueId(),unused->new HashMap<>()).put(type,amplifier);
}
private void removeGrantedEffect(Player p,PotionEffectType type){
Map<PotionEffectType,Integer> own=grantedEffects.get(p.getUniqueId());
if(own==null)return;
Integer amplifier=own.remove(type);
if(own.isEmpty())grantedEffects.remove(p.getUniqueId());
if(amplifier==null)return;
PotionEffect effect=p.getPotionEffect(type);
if(effect!=null&&effect.getAmplifier()==amplifier&&effect.getDuration()>=0&&effect.getDuration()<=120)
withManagedPotion(p,()->p.removePotionEffect(type));
}
private void giveTalismanEffect(Player p,PotionEffectType type,int duration,int amplifier){
PotionEffect previous=p.getPotionEffect(type);
boolean ours=isGranted(p,type,amplifier);
if(previous!=null){
if(!ours)return; // Never override a potion with a different source, even at the same level.
if(previous.getAmplifier()!=amplifier||previous.getDuration()<0){grantedEffects.getOrDefault(p.getUniqueId(),Map.of()).remove(type);return;}
if(previous.getDuration()>=40)return;
}
withManagedPotion(p,()->p.addPotionEffect(new PotionEffect(type,duration,amplifier,true,false,true)));
recordGranted(p,type,amplifier);
}
private void maintainAbsorption(Player p,int amplifier){
PotionEffect previous=p.getPotionEffect(PotionEffectType.ABSORPTION);
boolean ours=isGranted(p,PotionEffectType.ABSORPTION,amplifier);
if(previous!=null){
if(!ours)return;
if(previous.getAmplifier()!=amplifier||previous.getDuration()<0)return;
if(previous.getDuration()>=40)return;
}
double remaining=p.getAbsorptionAmount();
boolean renewing=ours&&previous!=null;
withManagedPotion(p,()->{
if(previous!=null)p.removePotionEffect(PotionEffectType.ABSORPTION);
p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,100,amplifier,true,false,true));
});
recordGranted(p,PotionEffectType.ABSORPTION,amplifier);
if(renewing&&Math.abs(p.getAbsorptionAmount()-remaining)>.01)p.setAbsorptionAmount(remaining);
}
private void refresh(){
for(Player p:Bukkit.getOnlinePlayers()){
Talisman t=held(p),old=active.get(p.getUniqueId());
if(old!=t){
if(old==Talisman.MAIMING)removeGrantedEffect(p,PotionEffectType.HUNGER);
if(old==Talisman.WRATH){removeGrantedEffect(p,PotionEffectType.STRENGTH);removeWrathCritRecharge(p);}
if(old==Talisman.DEFIANCE||old==Talisman.HADES)removeGrantedEffect(p,PotionEffectType.ABSORPTION);
if(old==Talisman.POSEIDON){removeGrantedEffect(p,PotionEffectType.WATER_BREATHING);removeGrantedEffect(p,PotionEffectType.DOLPHINS_GRACE);removeGrantedEffect(p,PotionEffectType.SPEED);}
if(old==Talisman.AGES)removeGrantedEffect(p,PotionEffectType.FIRE_RESISTANCE);
if(old==Talisman.END)removeGrantedEffect(p,PotionEffectType.INVISIBILITY);
}
if(t==null){active.remove(p.getUniqueId());continue;}active.put(p.getUniqueId(),t);
switch(t){
case MAIMING -> giveTalismanEffect(p,PotionEffectType.HUNGER,80,0);
case WRATH -> giveTalismanEffect(p,PotionEffectType.STRENGTH,80,0);
case DEFIANCE -> maintainAbsorption(p,1);
case JOKER -> {}
case POSEIDON -> {
if(p.isInWater()||isRaining(p)){
giveTalismanEffect(p,PotionEffectType.WATER_BREATHING,100,0);
giveTalismanEffect(p,PotionEffectType.DOLPHINS_GRACE,100,0);
giveTalismanEffect(p,PotionEffectType.SPEED,100,0);
if(pulses%3==0&&p.isInWater())p.getWorld().spawnParticle(Particle.BUBBLE,p.getLocation().add(0,1,0),3,.2,.3,.2,.02);
}else{
removeGrantedEffect(p,PotionEffectType.WATER_BREATHING);
removeGrantedEffect(p,PotionEffectType.DOLPHINS_GRACE);
removeGrantedEffect(p,PotionEffectType.SPEED);
}
}
case HADES -> maintainAbsorption(p,2);
case AGES -> giveTalismanEffect(p,PotionEffectType.FIRE_RESISTANCE,100,0);
case END -> giveTalismanEffect(p,PotionEffectType.INVISIBILITY,100,0);
}
}
}
private void tick(){
pulses++;refresh();cleanupPads();
for(Altar a:new ArrayList<>(altars.values())){
Location loc=a.location();if(loc==null||!loc.getWorld().isChunkLoaded(a.x>>4,a.z>>4))continue;
World w=loc.getWorld();Block b=w.getBlockAt(a.x,a.y,a.z);
if(b.getType()!=a.base){removeAltar(a);continue;}
showRelicBlock(a);
if(!a.ready&&System.currentTimeMillis()>=a.due){a.ready=true;saveAltars();w.playSound(loc,Sound.BLOCK_END_PORTAL_SPAWN,1,1.6f);w.spawnParticle(Particle.TOTEM_OF_UNDYING,loc,70,1,1,1,.1);}
showHologram(a);beamParticles(a);
if(a.ready){
Player nearest=null;double best=25*25;
for(Player p:w.getPlayers()){double d=p.getLocation().distanceSquared(loc);if(d<best){nearest=p;best=d;}}
if(nearest!=null){Talisman t=roll();give(nearest,talisman(t,initialLives(t)));nearest.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,1200,1));
nearest.sendMessage("§b✦ Реликварий пробудился: "+t.name+" §b| Скорость II на 60 сек.");
reviveParticles(loc,t);removeAltar(a);b.setType(Material.AIR,false);continue;}
}
if(pulses%2==0)ambient(a);
}
}
private Talisman roll(){
double x=random.nextDouble();
if(x<.15)return Talisman.JOKER;
if(x<.40)return Talisman.DEFIANCE;
if(x<.41)return Talisman.WRATH;
if(x<.64)return Talisman.POSEIDON;
if(x<.81)return Talisman.HADES;
return Talisman.AGES;
}
private void showRelicBlock(Altar a){
if(a.base!=Material.BARRIER)return;
UUID id=relicDisplays.get(a.id);Entity e=id==null?null:Bukkit.getEntity(id);
if(e instanceof ItemDisplay&&e.isValid())return;
Location loc=a.location();if(loc==null)return;
ItemDisplay display=loc.getWorld().spawn(loc,ItemDisplay.class,d->{
d.setItemStack(chestItem());d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
d.setBillboard(Display.Billboard.FIXED);d.setPersistent(false);d.setInvulnerable(true);
});
relicDisplays.put(a.id,display.getUniqueId());
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
d.text(Component.text("✦ РЕЛИКВАРИЙ ✦\n",NamedTextColor.AQUA).append(Component.text(a.ready?"НАГРАДА ГОТОВА":"Открытие через "+String.format("%02d:%02d",s/60,s%60),a.ready?NamedTextColor.GREEN:NamedTextColor.WHITE)));
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
// Purpur 26.3 requires an explicit Float payload for DRAGON_BREATH.
// Use a custom purple DUST payload instead of the invalid data-less call.
if(t==Talisman.MAIMING){
w.spawnParticle(Particle.DUST,loc.clone().add(0,1,0),70,.9,1,.9,
new Particle.DustOptions(Color.fromRGB(150,64,175),1.4f));
}else{
Particle kind=switch(t){
case MAIMING -> Particle.WITCH; // handled with colored DUST above
case WRATH -> Particle.FLAME;
case DEFIANCE -> Particle.END_ROD;
case JOKER -> Particle.WITCH;
case POSEIDON -> Particle.SPLASH;
case HADES -> Particle.SOUL_FIRE_FLAME;
case AGES -> Particle.ENCHANT;
case END -> Particle.PORTAL;
};
w.spawnParticle(kind,loc.clone().add(0,1,0),65,.75,.85,.75,.045);
}
w.playSound(loc,Sound.ENTITY_PLAYER_LEVELUP,1,1.3f);
}
private void give(Player p,ItemStack i){for(ItemStack rest:p.getInventory().addItem(i).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length==0||!args[0].equalsIgnoreCase("give")){
sender.sendMessage("§dTalismanForge 2.7.0 §7| /tf give <ник> <maiming|wrath|defiance|joker|poseidon|hades|ages|end|reliquary|hook>");
sender.sendMessage("§7Реликварий: 7 аметистов + тотем + обсидиан снизу. Луч без маяка.");return true;
}
if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй: /tf give <ник> <тип>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не найден.");return true;}
Talisman t=Talisman.parse(args[2]);ItemStack i=(args[2].equalsIgnoreCase("chest")||args[2].equalsIgnoreCase("reliquary"))?chestItem():args[2].equalsIgnoreCase("hook")?hookListener.createHookItem():t==null?null:talisman(t,initialLives(t));
if(i==null){sender.sendMessage("§cНеизвестный тип.");return true;}
give(p,i);sender.sendMessage("§aВыдано игроку "+p.getName());return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return List.of("give");
if(args.length==2)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(args.length==3)return List.of("maiming","wrath","defiance","joker","poseidon","hades","ages","end","reliquary","hook");
return List.of();
}
@EventHandler public void quit(PlayerQuitEvent e){
UUID id=e.getPlayer().getUniqueId();active.remove(id);safeLocations.remove(id);ghastCooldowns.remove(id);pathCenters.remove(id);pathRefresh.remove(id);
wrathStreak.remove(id);wrathLastCrit.remove(id);removeWrathCritRecharge(e.getPlayer());restoreHadesMode(e.getPlayer());clearEndSwords(id);removePoseidonCompanions(id);agesInvulnerableUntil.remove(id);hungerTakenFractions.remove(id);hungerGivenFractions.remove(id);grantedEffects.remove(id);
ShieldRing ring=shieldRings.remove(id);if(ring!=null)removeShieldVisuals(ring);
lastAttacker.remove(id);lastAttackerTime.remove(id);jokerStunnedUntil.remove(id);endShieldHits.remove(id);
}
}