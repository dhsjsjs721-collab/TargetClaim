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
JOKER("joker","§dТалисман Джокера",1),
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

private final Map<UUID,Talisman> active=new HashMap<>();
private final Map<UUID,Location> safeLocations=new HashMap<>();
private final Map<UUID,UUID> thrownEggs=new HashMap<>();
private final Map<UUID,Long> ghostDolphins=new HashMap<>();
private final Map<UUID,Long> ghastCooldowns=new HashMap<>();
private final Random random=new Random();
private int pulses=0;
private static final class Pad {
UUID world;int x,y,z;String original;Material replacement;long expire;
String key(){return world+":"+x+":"+y+":"+z;}
}
private static final int MAX_PADS=150;
private static final long WATER_DURATION=3200L,LAVA_DURATION=4200L;
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
hookListener=new GrapplingHookListener(this);
Bukkit.getPluginManager().registerEvents(hookListener,this);
Bukkit.getScheduler().runTaskTimer(this,this::quarterTick,5L,5L);
Bukkit.getScheduler().runTaskTimer(this,this::tick,20L,20L);
getLogger().info("TalismanForge 2.5.2: "+altars.size()+" ritual chests");
}
@Override public void onDisable(){
if(hookListener!=null)hookListener.shutdown();
for(UUID id:holograms.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
for(UUID id:ghostDolphins.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
holograms.clear();ghostDolphins.clear();saveAltars();
for(Pad pad:new ArrayList<>(pads.values()))restorePad(pad);
pads.clear();savePads();
}
private ItemStack talisman(Talisman t,int lives){
ItemStack i=new ItemStack(Material.TOTEM_OF_UNDYING);ItemMeta m=i.getItemMeta();
m.setDisplayName(t.name);
m.setItemModel(new NamespacedKey("talismanforge",t.key));
m.getPersistentDataContainer().set(typeKey,PersistentDataType.STRING,t.key);
m.getPersistentDataContainer().set(usesKey,PersistentDataType.INTEGER,Math.max(0,Math.min(t.lives,lives)));
List<String> lore=new ArrayList<>();
lore.add("§8Работает в любой руке");
lore.add(t.lives==0?"§cНе спасает от обычной смерти":"§7Спасений от смерти: §e"+lives+"/"+t.lives);
lore.add(t==Talisman.POSEIDON?"§cНе спасает от бездны":t==Talisman.END?"§bБесконечное спасение от бездны":"§bСпасает от бездны, но исчезает");
switch(t){
case MAIMING -> {lore.add("§c−0.5 сердца, Голод I");lore.add("§5Удары: Иссушение I, 5 секунд");}
case WRATH -> {lore.add("§c−2 сердца, без воскрешения");lore.add("§6Сила II, +30% скорость атаки");lore.add("§c+35% прямого урона оружием");}
case DEFIANCE -> {lore.add("§c−2 сердца, Поглощение II");lore.add("§bЩит невозможно временно отключить");}
case JOKER -> {lore.add("§dОдин шанс: 50% спастись от смерти");lore.add("§7Неудача: талисман исчезнет");lore.add("§aУдача: Скорость II, Сила II, Сопротивление I на 5 минут");lore.add("§5Клыки призывателя атакуют существ рядом");}
case POSEIDON -> {lore.add("§3Вода / дождь: дыхание, скорость");lore.add("§bСпасение только в воде или под дождём");lore.add("§cСухая смерть: гроза и призрачный дельфин");}
case HADES -> {lore.add("§c−4 сердца, Поглощение III");lore.add("§7Мобы игнорируют, пока их не тревожат");lore.add("§4Возрождение только рядом с игроками");}
case AGES -> {lore.add("§eТри спасения, шипы на всех атаках");lore.add("§6Хождение по воде и лаве, защита от огня");}
case END -> {lore.add("§5Невидимость, эндермены нейтральны");lore.add("§dОтражение дальних атак");lore.add("§6Крит по мобу: молния +1 сердце урона");}
}
m.setLore(lore);
if(t==Talisman.MAIMING)attribute(m,"maiming_health",Attribute.MAX_HEALTH,-1,AttributeModifier.Operation.ADD_NUMBER);
if(t==Talisman.WRATH){attribute(m,"wrath_health",Attribute.MAX_HEALTH,-4,AttributeModifier.Operation.ADD_NUMBER);attribute(m,"wrath_attack_speed",Attribute.ATTACK_SPEED,.30,AttributeModifier.Operation.ADD_SCALAR);}
if(t==Talisman.DEFIANCE)attribute(m,"defiance_health",Attribute.MAX_HEALTH,-4,AttributeModifier.Operation.ADD_NUMBER);

if(t==Talisman.HADES)attribute(m,"hades_health",Attribute.MAX_HEALTH,-8,AttributeModifier.Operation.ADD_NUMBER);
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
private int lives(ItemStack i){
if(i==null||!i.hasItemMeta())return 0;
Integer n=i.getItemMeta().getPersistentDataContainer().get(usesKey,PersistentDataType.INTEGER);
return n==null?0:n;
}
private ItemStack chestItem(){
ItemStack i=new ItemStack(Material.CHEST);ItemMeta m=i.getItemMeta();
m.setDisplayName("§5§lСундук талисманов");
m.setLore(List.of("§7Открой ритуал на улице с видом на небо","§dСветящийся луч и таймер: 03:00","§7После открытия награда ближайшему игроку","§7Ломать может только поставивший"));
m.getPersistentDataContainer().set(chestKey,PersistentDataType.BYTE,(byte)1);
i.setItemMeta(m);return i;
}
private boolean specialChest(ItemStack i){
if(i==null||i.getType()!=Material.CHEST||!i.hasItemMeta())return false;
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
if(!restorePad(pad)){pad.expire=0L;pads.put(pad.key(),pad);}
}catch(Exception ex){getLogger().warning("Invalid saved liquid path "+id);}
savePads();
}
private void savePads(){
YamlConfiguration cfg=new YamlConfiguration();int n=0;
for(Pad pad:pads.values()){
String k="paths.p"+(n++)+".";cfg.set(k+"world",pad.world.toString());cfg.set(k+"x",pad.x);cfg.set(k+"y",pad.y);cfg.set(k+"z",pad.z);
cfg.set(k+"original",pad.original);cfg.set(k+"replacement",pad.replacement.name());
}
try{cfg.save(padsFile);padsChanged=false;}catch(IOException ex){getLogger().severe("Failed to save liquid paths: "+ex.getMessage());}
}
private boolean restorePad(Pad pad){
World w=Bukkit.getWorld(pad.world);if(w==null)return false;
Block b=w.getBlockAt(pad.x,pad.y,pad.z);
if(b.getType()!=pad.replacement)return true;
try{b.setBlockData(Bukkit.createBlockData(pad.original),false);return true;}catch(IllegalArgumentException e){return false;}
}
private void addPad(World w,int x,int y,int z){
Block b=w.getBlockAt(x,y,z);String k=w.getUID()+":"+x+":"+y+":"+z;
Pad existing=pads.get(k);
if(existing!=null){existing.expire=System.currentTimeMillis()+(existing.replacement==Material.MOSS_BLOCK?WATER_DURATION:LAVA_DURATION);return;}
if(pads.size()>=MAX_PADS)return;
Material fluid=b.getType();if(fluid!=Material.WATER&&fluid!=Material.LAVA)return;
BlockData original=b.getBlockData();if(!(original instanceof org.bukkit.block.data.Levelled lvl)||lvl.getLevel()!=0)return;
Pad pad=new Pad();pad.world=w.getUID();pad.x=x;pad.y=y;pad.z=z;pad.original=original.getAsString();
pad.replacement=fluid==Material.WATER?Material.MOSS_BLOCK:Material.SOUL_SOIL;
pad.expire=System.currentTimeMillis()+(fluid==Material.WATER?WATER_DURATION:LAVA_DURATION);
b.setType(pad.replacement,false);pads.put(pad.key(),pad);padsChanged=true;
}
private void spawnPath(Player p,Location to){
if(p.isFlying()||p.isSneaking()||p.getGameMode()==GameMode.SPECTATOR)return;
World w=to.getWorld();if(w==null)return;int y=(int)Math.floor(to.getY()-.20);
if(y<w.getMinHeight()||y>=w.getMaxHeight())return;
int x=to.getBlockX(),z=to.getBlockZ();addPad(w,x,y,z);
Vector v=p.getVelocity();
if(v.lengthSquared()>.035){int xx=(int)Math.floor(to.getX()+Math.max(-.75,Math.min(.75,v.getX()*2.5)));
int zz=(int)Math.floor(to.getZ()+Math.max(-.75,Math.min(.75,v.getZ()*2.5)));
if(xx!=x||zz!=z)addPad(w,xx,y,zz);}
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
saveAltars();
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){
if(protectedAltar(e.getBlockPlaced())!=null){e.setCancelled(true);return;}
if(!specialChest(e.getItemInHand()))return;
Block b=e.getBlockPlaced();Player p=e.getPlayer();
if(!canBeam(b)){e.setCancelled(true);p.sendMessage("§cНедостаточно места над сундуком.");return;}
Altar a=new Altar();a.owner=p.getUniqueId();a.world=b.getWorld().getUID();a.x=b.getX();a.y=b.getY();a.z=b.getZ();a.due=System.currentTimeMillis()+OPEN_DELAY;
altars.put(a.index(),a);saveAltars();
p.sendMessage("§d✦ Сундук установлен! §7Координаты: "+a.x+", "+a.y+", "+a.z);
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
if(voidSlot(p)!=null&&p.getLocation().getY()<=p.getWorld().getMinHeight()+6)voidRescue(p);
}
dolphinTick();
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
Player p=e.getPlayer();if(e.getTo()==null||held(p)==null)return;
if(held(p)==Talisman.AGES)spawnPath(p,e.getTo());
if(e.getTo().getY()<p.getWorld().getMinHeight()+6){if(voidSlot(p)!=null)voidRescue(p);return;}
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
if(e.getCause()==EntityDamageEvent.DamageCause.VOID&&voidSlot(p)!=null){e.setCancelled(true);voidRescue(p);return;}
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
if(e.getEntity() instanceof Player target&&held(target)==Talisman.END&&e.getDamager() instanceof Projectile projectile){
e.setCancelled(true);ProjectileSource shooter=projectile.getShooter();
if(shooter instanceof LivingEntity source&&source!=target&&source.isValid())source.damage(Math.min(20,Math.max(1,e.getFinalDamage())),target);
target.getWorld().spawnParticle(Particle.PORTAL,target.getEyeLocation(),24,.3,.3,.3,.1);return;
}
if(e.getEntity() instanceof Player target&&held(target)==Talisman.AGES){
LivingEntity thornsSource=null;
if(e.getDamager() instanceof LivingEntity direct)thornsSource=direct;
else if(e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof LivingEntity ranged)thornsSource=ranged;
if(thornsSource!=null&&thornsSource!=target){
final LivingEntity attacker=thornsSource;
Bukkit.getScheduler().runTask(this,()->{if(attacker.isValid()&&!attacker.isDead())attacker.damage(2.0,target);});
}
}
if(!(e.getDamager() instanceof Player attacker))return;
Talisman t=held(attacker);if(t==null)return;
if(t==Talisman.WRATH)e.setDamage(e.getDamage()*1.35);
if(t==Talisman.HADES&&e.getEntity() instanceof LivingEntity victim)ghastAssist(attacker,victim);
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
private void ghastAssist(Player owner,LivingEntity enemy){
if(enemy==owner||!enemy.isValid()||enemy.isDead()||!owner.getWorld().equals(enemy.getWorld()))return;
long now=System.currentTimeMillis();
if(now-ghastCooldowns.getOrDefault(owner.getUniqueId(),0L)<3500L)return;
int fired=0;
for(Ghast ghast:owner.getWorld().getEntitiesByClass(Ghast.class)){
if(!ghast.isValid()||ghast.isDead()||ghast.getLocation().distanceSquared(owner.getLocation())>32*32||ghast.getLocation().distanceSquared(enemy.getLocation())>42*42)continue;
Vector direction=enemy.getEyeLocation().toVector().subtract(ghast.getEyeLocation().toVector());
if(direction.lengthSquared()<.001)continue;
LargeFireball shot=ghast.launchProjectile(LargeFireball.class,direction.normalize());
shot.setIsIncendiary(false);shot.setYield(0f);
ghast.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,ghast.getEyeLocation(),8,.3,.3,.3,.03);
fired++;if(fired>=2)break;
}
if(fired>0)ghastCooldowns.put(owner.getUniqueId(),now);
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
if(random.nextBoolean()){
can=true;
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
}
if(t==Talisman.JOKER){
p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,6000,1,false,true,true));
p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,6000,1,false,true,true));
p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,6000,0,false,true,true));
jokerFangs(p);
p.sendMessage("§d§lДЖОКЕР! §aТы выиграл: пять минут усилений и волна клыков.");
}
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
Player nearest=null;double best=24*24;
for(Player p:d.getWorld().getPlayers()){
if(held(p)==Talisman.POSEIDON||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
double distance=p.getEyeLocation().distanceSquared(d.getLocation());
if(distance<best){best=distance;nearest=p;}
}
if(nearest!=null){
Vector travel=nearest.getEyeLocation().toVector().subtract(d.getLocation().toVector());
double distance=travel.length();
if(distance>.05)d.teleport(d.getLocation().add(travel.normalize().multiply(Math.min(1.2,distance))));
if(d.getLocation().distanceSquared(nearest.getEyeLocation())<2.3*2.3){
nearest.damage(4.0,d);
ghostDolphins.remove(d.getUniqueId());d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),36,.5,.5,.5,.04);d.remove();continue;
}
}
d.getWorld().spawnParticle(Particle.SPLASH,d.getLocation(),4,.3,.3,.3,.02);
}
}
private boolean isRaining(Player p){return p.getWorld().getEnvironment()==World.Environment.NORMAL&&p.getWorld().hasStorm();}
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
case JOKER -> {}
case POSEIDON -> {if(p.isInWater()||isRaining(p)){
p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,100,0,true,false,true));
p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,100,0,true,false,true));
p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,100,0,true,false,true));
}}
case HADES -> {if(!p.hasPotionEffect(PotionEffectType.ABSORPTION))p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,1_000_000,2,true,false,true));}
case AGES -> p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,100,0,true,false,true));
case END -> p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,100,0,true,false,true));
}
}
}
private void tick(){
pulses++;refresh();cleanupPads();
for(Altar a:new ArrayList<>(altars.values())){
Location loc=a.location();if(loc==null||!loc.getWorld().isChunkLoaded(a.x>>4,a.z>>4))continue;
World w=loc.getWorld();Block b=w.getBlockAt(a.x,a.y,a.z);
if(b.getType()!=Material.CHEST){removeAltar(a);continue;}
if(!a.ready&&System.currentTimeMillis()>=a.due){a.ready=true;saveAltars();w.playSound(loc,Sound.BLOCK_END_PORTAL_SPAWN,1,1.6f);w.spawnParticle(Particle.TOTEM_OF_UNDYING,loc,70,1,1,1,.1);}
showHologram(a);beamParticles(a);
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
if(x<.15)return Talisman.JOKER;
if(x<.40)return Talisman.DEFIANCE;
if(x<.65)return Talisman.WRATH;
if(x<.80)return Talisman.POSEIDON;
if(x<.92)return Talisman.HADES;
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
case JOKER -> Particle.WITCH;
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
sender.sendMessage("§dTalismanForge 2.5.2 §7| /tf give <ник> <maiming|wrath|defiance|joker|poseidon|hades|ages|end|chest|hook>");
sender.sendMessage("§7Рецепт сундука: 7 аметистов + тотем + обсидиан снизу. Талисманы в любой руке.");return true;
}
if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй: /tf give <ник> <тип>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не найден.");return true;}
Talisman t=Talisman.parse(args[2]);ItemStack i=args[2].equalsIgnoreCase("chest")?chestItem():args[2].equalsIgnoreCase("hook")?hookListener.createHookItem():t==null?null:talisman(t,t.lives);
if(i==null){sender.sendMessage("§cНеизвестный тип.");return true;}
give(p,i);sender.sendMessage("§aВыдано игроку "+p.getName());return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return List.of("give");
if(args.length==2)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(args.length==3)return List.of("maiming","wrath","defiance","joker","poseidon","hades","ages","end","chest","hook");
return List.of();
}
@EventHandler public void quit(PlayerQuitEvent e){
UUID id=e.getPlayer().getUniqueId();active.remove(id);safeLocations.remove(id);ghastCooldowns.remove(id);
}
}