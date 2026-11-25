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
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.io.*;
import java.util.*;

public final class TalismanForgePlugin extends dev.unified.ModuleBridge implements Listener, CommandExecutor, TabCompleter {
public TalismanForgePlugin(org.bukkit.plugin.java.JavaPlugin host){super(host,"TalismanForge");}
private static final long OPEN_DELAY=180000L;
private NamespacedKey typeKey,chestKey,displayKey;
private static final Set<String> LEGACY=Set.of("maiming","wrath","defiance","darkness","life","ages","end","hades","joker","poseidon","cerberus","kraken","gorgon","phoenix","hydra","tempest","guardian","shade","tide","zeus","olympus_poseidon","ares","athena","hermes");
private final NamespacedKey retiredFive=new NamespacedKey("talismanforge","five_talisman");
private GrapplingHookListener hookListener;
private final Map<UUID,UUID> relicDisplays=new HashMap<>();
private File saveFile;
private final Map<String,Altar> altars=new HashMap<>();
private final Map<UUID,UUID> holograms=new HashMap<>();

private final Random random=new Random();
private int pulses=0;
private MythicTalismans myths;
public List<Location> getKoscheiEggLocations(){return myths==null?List.of():myths.eggLocations();}
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
typeKey=new NamespacedKey("talismanforge","talisman_type");
getConfig().set("legacy-loot",null);getConfig().set("wither-chance",null);getConfig().set("nether-loot-multiplier",null);saveConfig();
chestKey=new NamespacedKey("talismanforge","ritual_chest");displayKey=new NamespacedKey("talismanforge","ritual_display");
loadAltars();
for(Altar a:altars.values())cleanupLegacyBeacon(a);
myths=new MythicTalismans(this);
for(Player p:Bukkit.getOnlinePlayers()){cleanOldState(p);purge(p.getInventory());purge(p.getEnderChest());}
ShapedRecipe r=new ShapedRecipe(new NamespacedKey("talismanforge","ritual_chest"),chestItem());
r.shape("AAA","ATA","AOA");r.setIngredient('A',Material.AMETHYST_SHARD);r.setIngredient('T',Material.TOTEM_OF_UNDYING);r.setIngredient('O',Material.OBSIDIAN);
Bukkit.addRecipe(r);
ShapedRecipe vanillaTotem=new ShapedRecipe(new NamespacedKey("talismanforge","golden_totem"),new ItemStack(Material.TOTEM_OF_UNDYING));
vanillaTotem.shape("GGG","GEG","GGG");
vanillaTotem.setIngredient('G',Material.GOLD_BLOCK);
vanillaTotem.setIngredient('E',Material.EMERALD);
Bukkit.addRecipe(vanillaTotem);
ShapelessRecipe craftedTag=new ShapelessRecipe(new NamespacedKey("talismanforge","paper_string_name_tag"),new ItemStack(Material.NAME_TAG));
craftedTag.addIngredient(Material.PAPER);
craftedTag.addIngredient(Material.STRING);
Bukkit.addRecipe(craftedTag);
PluginCommand cmd=getCommand("talismanforge");if(cmd!=null){cmd.setExecutor(this);cmd.setTabCompleter(this);}
Bukkit.getPluginManager().registerEvents(this,host());
hookListener=new GrapplingHookListener(host());
Bukkit.getPluginManager().registerEvents(hookListener,host());
Bukkit.getScheduler().runTaskTimer(host(),this::tick,20L,20L);
getLogger().info("TalismanForge 3.0.0: 5 mythic talismans (Thor, Anubis, Koschei, Kitsune, Medusa); "+altars.size()+" reliquaries");
}
@Override public void onDisable(){
for(UUID id:holograms.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
holograms.clear();for(UUID id:relicDisplays.values()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}relicDisplays.clear();
if(hookListener!=null)hookListener.shutdown();saveAltars();if(myths!=null)myths.close();
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
Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("talismanforge","talisman_chest"),PersistentDataType.BYTE));
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
if(((dev.unified.UnifiedPlugin)host()).isPeace(b.getLocation())){e.setCancelled(true);p.sendMessage("§cРеликварий можно устанавливать только за пределами мирной зоны.");return;}
if(!canBeam(b)){e.setCancelled(true);p.sendMessage("§cНедостаточно места над Реликварием.");return;}
Altar a=new Altar();a.owner=p.getUniqueId();a.world=b.getWorld().getUID();a.x=b.getX();a.y=b.getY();a.z=b.getZ();a.base=b.getType();a.due=System.currentTimeMillis()+OPEN_DELAY;
altars.put(a.index(),a);saveAltars();
p.sendMessage("§b✦ Реликварий установлен!");
showRelicBlock(a);
p.sendMessage("§7Светящийся луч без блока маяка. Открытие через §d03:00§7.");
b.getWorld().playSound(b.getLocation(),Sound.BLOCK_BEACON_ACTIVATE,1,.8f);
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
private void tick(){
pulses++;for(Player p:Bukkit.getOnlinePlayers()){purge(p.getInventory());if(pulses%10==0)purge(p.getEnderChest());}
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
if(nearest!=null){
// Exactly one of five new talismans. No direct mob/fishing/egg drops.
String reward=myths.issueRandom(nearest);
if(reward==null){give(nearest,new ItemStack(Material.TOTEM_OF_UNDYING));give(nearest,new ItemStack(Material.AMETHYST_SHARD,7));give(nearest,new ItemStack(Material.OBSIDIAN));reward="Ошибка сохранения — ингредиенты возвращены";}
// No speed reward; the case grants only a mythic talisman.
nearest.sendMessage("§b✦ Реликварий пробудился: "+reward);
removeAltar(a);b.setType(Material.AIR,false);continue;}
}
if(pulses%2==0)ambient(a);
}
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
private void give(Player p,ItemStack item){
Map<Integer,ItemStack> rest=p.getInventory().addItem(item);
for(ItemStack i:rest.values())p.getWorld().dropItemNaturally(p.getLocation(),i);
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(myths!=null&&myths.command(sender,args))return true;
if(args.length==0||!args[0].equalsIgnoreCase("give")){
sender.sendMessage("§dTalismanForge §7| /tf give <ник> <thor|anubis|koschei|kitsune|medusa|reliquary|hook|all>");
sender.sendMessage("§7Пробуждение: брось реликвию и яйцо дракона рядом · /tf status · /tf link · /tf restore");
sender.sendMessage("§7Рецепт сундука: 7 аметистов + тотем по центру + обсидиан снизу.");return true;
}
if(!sender.hasPermission("talismanforge.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй: /tf give <ник> <тип>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null){sender.sendMessage("§cИгрок не в сети.");return true;}
ItemStack item;
if(args[2].equalsIgnoreCase("chest")||args[2].equalsIgnoreCase("reliquary"))item=chestItem();
else if(args[2].equalsIgnoreCase("hook"))item=hookListener.createHookItem();
else if(args[2].equalsIgnoreCase("all")){int count=0;for(MythicTalismans.Kind k:MythicTalismans.Kind.values())if(myths.issue(p,k))count++;sender.sendMessage("§aВыдано реликвий: "+count);return true;}
else{sender.sendMessage("§cНеизвестный талисман.");return true;}
give(p,item);sender.sendMessage("§aВыдано игроку "+p.getName());return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return List.of("list","give","awaken","restore","link","status","vfx").stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
if(args.length==2&&args[0].equalsIgnoreCase("give"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x->x.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
if(args.length==2&&args[0].equalsIgnoreCase("vfx"))return List.of("thor","anubis","koschei","kitsune","medusa").stream().filter(x->x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
if(args.length==3&&args[0].equalsIgnoreCase("give"))return List.of("thor","anubis","koschei","kitsune","medusa","reliquary","hook","all").stream().filter(x->x.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
return List.of();
}
// Delete only retired, explicitly tagged relics. Vanilla totems, new mythic items and other custom equipment are untouched.
private boolean retired(ItemStack i){
if(i==null||!i.hasItemMeta())return false;
ItemMeta m=i.getItemMeta();if(m.getPersistentDataContainer().has(retiredFive,PersistentDataType.STRING))return true;String old=m.getPersistentDataContainer().get(typeKey,PersistentDataType.STRING);
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
private void cleanOldState(Player p){
var attribute=p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
if(attribute!=null){var modifier=attribute.getModifier(new NamespacedKey("talismanforge","mythic_health_bonus"));if(modifier!=null){attribute.removeModifier(modifier);if(p.getHealth()>attribute.getValue())p.setHealth(attribute.getValue());}}
var data=p.getPersistentDataContainer();
NamespacedKey ghost=new NamespacedKey("talismanforge","cerberus_ghost_mode");
String previous=data.get(ghost,PersistentDataType.STRING);
if(previous!=null){if(p.getGameMode()==GameMode.SPECTATOR)try{p.setGameMode(GameMode.valueOf(previous));}catch(IllegalArgumentException ex){p.setGameMode(GameMode.SURVIVAL);}data.remove(ghost);data.remove(new NamespacedKey("talismanforge","cerberus_ghost_y"));}
}
@EventHandler public void join(PlayerJoinEvent e){cleanOldState(e.getPlayer());purge(e.getPlayer().getInventory());purge(e.getPlayer().getEnderChest());}
@EventHandler(priority=EventPriority.LOWEST) public void openLegacyInventory(org.bukkit.event.inventory.InventoryOpenEvent e){purge(e.getInventory());}
@EventHandler(priority=EventPriority.LOWEST) public void legacyPickup(EntityPickupItemEvent e){if(retired(e.getItem().getItemStack())){e.setCancelled(true);e.getItem().remove();}}
@EventHandler(priority=EventPriority.LOWEST) public void legacyDrop(ItemSpawnEvent e){if(retired(e.getEntity().getItemStack()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.LOWEST) public void legacyResurrect(EntityResurrectEvent e){
if(!(e.getEntity() instanceof Player p)||e.getHand()==null)return;
if(retired(p.getInventory().getItem(e.getHand()))){e.setCancelled(true);p.getInventory().setItem(e.getHand(),new ItemStack(Material.AIR));}
}
@EventHandler(priority=EventPriority.LOWEST) public void legacyHopper(org.bukkit.event.inventory.InventoryMoveItemEvent e){
if(retired(e.getItem())){e.setCancelled(true);Bukkit.getScheduler().runTask(host(),()->purge(e.getSource()));}
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
