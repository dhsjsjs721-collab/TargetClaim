package dev.talismanforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.io.*;
import java.util.*;

public final class TalismanForgePlugin extends org.bukkit.plugin.java.JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private static final long OPEN_DELAY=180000L;
private NamespacedKey typeKey,chestKey,displayKey;
private static final Set<String> LEGACY=Set.of("maiming","wrath","defiance","darkness","life","ages","end","hades","joker","poseidon");
private File saveFile;
private final Map<String,Altar> altars=new HashMap<>();
private final Map<UUID,UUID> holograms=new HashMap<>();

private final Random random=new Random();
private int pulse=0;
private MythicTalismans myths;
public Location getKoscheiEggLocation(){return myths==null?null:myths.eggLocation();}
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
typeKey=new NamespacedKey(this,"talisman_type");
getConfig().set("legacy-loot",null);getConfig().set("wither-chance",null);getConfig().set("nether-loot-multiplier",null);saveConfig();
chestKey=new NamespacedKey(this,"talisman_chest");displayKey=new NamespacedKey(this,"altar_display");
loadAltars();
myths=new MythicTalismans(this);
for(Player p:Bukkit.getOnlinePlayers()){purge(p.getInventory());purge(p.getEnderChest());}
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
holograms.clear();saveAltars();if(myths!=null)myths.close();
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
private void tick(){
pulse++;
for(Player p:Bukkit.getOnlinePlayers()){purge(p.getInventory());if(pulse%10==0)purge(p.getEnderChest());}
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
String result=myths.issueRandom(nearest);
if(result==null){give(nearest,new ItemStack(Material.TOTEM_OF_UNDYING));give(nearest,new ItemStack(Material.AMETHYST_SHARD,7));give(nearest,new ItemStack(Material.OBSIDIAN));result="Все мифические реликвии уже выданы — ингредиенты возвращены";}
nearest.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,1200,1));
nearest.sendMessage("§d✦ Сундук открылся! §fПолучен: "+result+" §7| §bСкорость II на 60 сек.");
nearbyExplosion(loc);removeAltar(a,true);b.setType(Material.AIR,false);
}
}
if(pulse%2==0)particles(a);
}
}
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
if(myths!=null&&myths.command(sender,args))return true;
if(args.length==0||!args[0].equalsIgnoreCase("give")){
sender.sendMessage("§dTalismanForge §7| /tf give <ник> <thor|anubis|koschei|kitsune|medusa|chest>");
sender.sendMessage("§7/tf awaken · /tf status · /tf restore");
sender.sendMessage("§7Рецепт сундука: 7 аметистов + тотем по центру + обсидиан снизу.");return true;
}
if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй: /tf give <ник> <тип>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не в сети.");return true;}
ItemStack item;
if(args[2].equalsIgnoreCase("chest"))item=chestItem();
else{sender.sendMessage("§cНеизвестный талисман.");return true;}
give(p,item);sender.sendMessage("§aВыдано игроку "+p.getName());return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return List.of("give","awaken","restore","status");
if(args.length==2)return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(args.length==3)return List.of("thor","anubis","koschei","kitsune","medusa","chest");
return List.of();
}
// Delete only retired, explicitly tagged relics. Vanilla totems, new mythic items and other custom equipment are untouched.
private boolean retired(ItemStack i){
if(i==null||!i.hasItemMeta())return false;
ItemMeta m=i.getItemMeta();String old=m.getPersistentDataContainer().get(typeKey,PersistentDataType.STRING);
if(old!=null&&LEGACY.contains(old.toLowerCase(Locale.ROOT)))return true;
if(m.hasItemModel()){NamespacedKey model=m.getItemModel();return model!=null&&model.getNamespace().equals("talismanforge")&&LEGACY.contains(model.getKey());}
return false;
}
private boolean cleanNested(ItemStack i,int depth){
if(i==null||depth>8||!i.hasItemMeta())return false;
ItemMeta m=i.getItemMeta();boolean changed=false;
if(m instanceof org.bukkit.inventory.meta.BlockStateMeta sm&&sm.getBlockState() instanceof org.bukkit.block.ShulkerBox box){
changed=purge(box.getInventory(),depth+1);if(changed){sm.setBlockState(box);i.setItemMeta(sm);}
}else if(m instanceof org.bukkit.inventory.meta.BundleMeta bundle){
List<ItemStack> keep=new ArrayList<>();for(ItemStack item:bundle.getItems()){
if(retired(item)){changed=true;continue;}changed|=cleanNested(item,depth+1);keep.add(item);
}
if(changed){bundle.setItems(keep);i.setItemMeta(bundle);}
}
return changed;
}
private void purge(Inventory inv){purge(inv,0);}
private boolean purge(Inventory inv,int depth){
boolean changed=false;for(int slot=0;slot<inv.getSize();slot++){
ItemStack i=inv.getItem(slot);if(retired(i)){inv.setItem(slot,null);changed=true;}
else if(cleanNested(i,depth)){inv.setItem(slot,i);changed=true;}
}return changed;
}
@EventHandler public void join(PlayerJoinEvent e){purge(e.getPlayer().getInventory());purge(e.getPlayer().getEnderChest());}
@EventHandler(priority=EventPriority.LOWEST) public void openLegacyInventory(org.bukkit.event.inventory.InventoryOpenEvent e){purge(e.getInventory());}
@EventHandler(priority=EventPriority.LOWEST) public void legacyPickup(EntityPickupItemEvent e){if(retired(e.getItem().getItemStack())){e.setCancelled(true);e.getItem().remove();}}
@EventHandler(priority=EventPriority.LOWEST) public void legacyDrop(ItemSpawnEvent e){if(retired(e.getEntity().getItemStack()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void legacyResurrect(EntityResurrectEvent e){
if(!(e.getEntity() instanceof Player p)||e.getHand()==null)return;
if(retired(p.getInventory().getItem(e.getHand()))){e.setCancelled(true);p.getInventory().setItem(e.getHand(),new ItemStack(Material.AIR));}
}
@EventHandler(priority=EventPriority.LOWEST) public void legacyHopper(org.bukkit.event.inventory.InventoryMoveItemEvent e){
if(retired(e.getItem())){e.setCancelled(true);Bukkit.getScheduler().runTask(this,()->purge(e.getSource()));}
}
@EventHandler public void worldLoad(ChunkLoadEvent e){
for(org.bukkit.block.BlockState state:e.getChunk().getTileEntities())if(state instanceof org.bukkit.block.Container c)purge(c.getInventory());
}
@EventHandler public void oldEntities(EntitiesLoadEvent e){
for(Entity entity:e.getEntities()){
if(entity instanceof Item i&&retired(i.getItemStack()))i.remove();
else if(entity instanceof org.bukkit.inventory.InventoryHolder holder)purge(holder.getInventory());
else if(entity instanceof LivingEntity l&&l.getEquipment()!=null){
EntityEquipment eq=l.getEquipment();
for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))if(retired(eq.getItem(slot)))eq.setItem(slot,new ItemStack(Material.AIR));
}
}
}
}
