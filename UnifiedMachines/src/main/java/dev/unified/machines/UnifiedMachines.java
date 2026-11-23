package dev.unified.machines;

import java.io.IOException;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bounded farm machine + one-chunk loader.
 * Block PDC stores weapons; a separate sparse position registry stores ownership.
 * Bukkit calls only on main thread. Never changes view distance or modifies chunks.
 */
public final class UnifiedMachines extends JavaPlugin implements Listener {
private record ChunkKey(UUID world,int x,int z){}
private final Map<UUID,ChunkKey> activeOwner=new HashMap<>();
private final Map<ChunkKey,Set<UUID>> chunkUsers=new HashMap<>();
private final Map<UUID,Integer> damagersByOwner=new HashMap<>();
private MachineRegistry registry;
private NamespacedKey machineKey,ownerKey,weaponKey;
private long hits,brokenWeapons,ticketActivations,ticketReleases,errors,refused,guiOpens;
private int damageTask=-1,scanCursor,maxDamagers,maxDamagersPerOwner,maxActiveChunks,maxScanned;
private double range;
private boolean passiveTargets;
private String statusPrefix="[UnifiedMachines] ";

private static final class Menu implements InventoryHolder {
final MachineRegistry.Position position;
final UUID owner;
final Inventory inv;
Menu(MachineRegistry.Position position,UUID owner){
this.position=position;this.owner=owner;
this.inv=Bukkit.createInventory(this,9,"Дамагер • 1 слот оружия");
}
@Override public Inventory getInventory(){return inv;}
}
@Override public void onEnable(){
saveDefaultConfig();
machineKey=new NamespacedKey(this,"kind");
ownerKey=new NamespacedKey(this,"owner");
weaponKey=new NamespacedKey(this,"weapon");
registry=new MachineRegistry(getDataFolder().toPath().resolve("machines.properties"));
try{registry.load();}
catch(IOException failure){
getLogger().severe("Cannot read machine records. Refusing to enable to protect ownership: "+failure.getMessage());
getServer().getPluginManager().disablePlugin(this);
return;
}
maxDamagers=limit(getConfig().getInt("damager.max-total-blocks",256),1,2000);
maxDamagersPerOwner=limit(getConfig().getInt("damager.max-per-player",8),1,64);
maxActiveChunks=limit(getConfig().getInt("chunk-loader.max-total-active-chunks",48),1,256);
maxScanned=limit(getConfig().getInt("damager.max-blocks-processed-per-second",96),1,1000);
range=Math.max(1.0,Math.min(6.0,getConfig().getDouble("damager.radius-blocks",2.5)));
passiveTargets=getConfig().getBoolean("damager.damage-passive-mobs",true);
for(MachineRegistry.Machine m:registry.machines.values()){
if(m.kind()==MachineRegistry.Kind.DAMAGE)damagersByOwner.merge(m.owner(),1,Integer::sum);
}
registerRecipes();
getServer().getPluginManager().registerEvents(this,this);
damageTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,this::strike,20L,20L);
for(Player p:Bukkit.getOnlinePlayers())activate(p.getUniqueId());
getLogger().info("Enabled. Damagers="+registry.count(MachineRegistry.Kind.DAMAGE)+
", loaders="+registry.count(MachineRegistry.Kind.LOADER)+", active-chunk cap="+maxActiveChunks+". Offline owners keep no chunk ticket.");
}
@Override public void onDisable(){
if(damageTask!=-1){Bukkit.getScheduler().cancelTask(damageTask);damageTask=-1;}
for(ChunkKey key:new ArrayList<>(chunkUsers.keySet())){
World world=Bukkit.getWorld(key.world());
if(world!=null)world.removePluginChunkTicket(key.x(),key.z(),this);
}
chunkUsers.clear();activeOwner.clear();
if(registry!=null)try{registry.save();}catch(IOException e){getLogger().severe("Failed to save machine registry: "+e.getMessage());}
}
private static int limit(int val,int low,int high){return Math.max(low,Math.min(high,val));}

private ItemStack item(MachineRegistry.Kind kind){
Material material=kind==MachineRegistry.Kind.DAMAGE?Material.DROPPER:Material.ENDER_CHEST;
ItemStack item=new ItemStack(material);
ItemMeta meta=item.getItemMeta();
String title=kind==MachineRegistry.Kind.DAMAGE?"Дамагер • удар раз в секунду":"Прогрузчик чанка • 1 на игрока";
meta.displayName(Component.text(title,kind==MachineRegistry.Kind.DAMAGE?NamedTextColor.RED:NamedTextColor.AQUA));
meta.lore(kind==MachineRegistry.Kind.DAMAGE?
List.of(Component.text("ПКМ: одно оружие в один слот"),Component.text("Меч / топор / копьё / трезубец / булава"),Component.text("Прочность оружия расходуется при ударах")):
List.of(Component.text("Загружает только свой чанк"),Component.text("1 установленный прогрузчик на игрока"),Component.text("Работает, только пока владелец онлайн")));
meta.getPersistentDataContainer().set(machineKey,PersistentDataType.STRING,kind.name());
item.setItemMeta(meta);
return item;
}
private void registerRecipes(){
ShapedRecipe damage=new ShapedRecipe(new NamespacedKey(this,"damager"),item(MachineRegistry.Kind.DAMAGE));
damage.shape("III","RHR","ODO");
damage.setIngredient('I',Material.IRON_BLOCK);
damage.setIngredient('R',Material.REDSTONE);
damage.setIngredient('H',Material.HOPPER);
damage.setIngredient('O',Material.OBSIDIAN);
damage.setIngredient('D',Material.DISPENSER);
Bukkit.addRecipe(damage);
ShapedRecipe loader=new ShapedRecipe(new NamespacedKey(this,"chunk_loader"),item(MachineRegistry.Kind.LOADER));
loader.shape("DND","OEO","DCD");
loader.setIngredient('D',Material.DIAMOND_BLOCK);
loader.setIngredient('N',Material.NETHER_STAR);
loader.setIngredient('O',Material.OBSIDIAN);
loader.setIngredient('E',Material.ENDER_CHEST);
loader.setIngredient('C',Material.CRYING_OBSIDIAN);
Bukkit.addRecipe(loader);
}
private MachineRegistry.Kind kind(ItemStack item){
if(item==null||!item.hasItemMeta())return null;
String type=item.getItemMeta().getPersistentDataContainer().get(machineKey,PersistentDataType.STRING);
try{return type==null?null:MachineRegistry.Kind.valueOf(type);}
catch(IllegalArgumentException invalid){return null;}
}
private MachineRegistry.Position position(Block b){
return new MachineRegistry.Position(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());
}
private TileState tile(Block block){
BlockState state=block.getState();
return state instanceof TileState ts?ts:null;
}
private boolean matching(Block block,MachineRegistry.Machine m){
if(!block.getWorld().getUID().equals(m.position().world()))return false;
if(block.getX()!=m.position().x()||block.getY()!=m.position().y()||block.getZ()!=m.position().z())return false;
if(block.getType()!=(m.kind()==MachineRegistry.Kind.DAMAGE?Material.DROPPER:Material.ENDER_CHEST))return false;
TileState tile=tile(block);
if(tile==null)return false;
PersistentDataContainer pdc=tile.getPersistentDataContainer();
return m.kind().name().equals(pdc.get(machineKey,PersistentDataType.STRING))&&
m.owner().toString().equals(pdc.get(ownerKey,PersistentDataType.STRING));
}
private MachineRegistry.Machine tracked(Block block){
MachineRegistry.Machine m=registry.at(position(block));
return m!=null && matching(block,m)?m:null;
}
private void saveRegistry(){try{registry.save();}catch(IOException error){errors++;getLogger().severe("Machine data could not be persisted: "+error.getMessage());}}
private void tell(Player p,String text){p.sendMessage(statusPrefix+text);}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onPlace(BlockPlaceEvent event){
MachineRegistry.Kind kind=kind(event.getItemInHand());
if(kind==null)return;
Block b=event.getBlockPlaced();
if(b.getType()!=(kind==MachineRegistry.Kind.DAMAGE?Material.DROPPER:Material.ENDER_CHEST)){
event.setCancelled(true);return;
}
UUID owner=event.getPlayer().getUniqueId();
if(kind==MachineRegistry.Kind.LOADER && !MachineRules.canPlaceLoader(registry.owners.containsKey(owner)?1:0)){
event.setCancelled(true);tell(event.getPlayer(),"Уже установлен один прогрузчик чанков.");refused++;return;
}
if(kind==MachineRegistry.Kind.DAMAGE &&
(registry.count(MachineRegistry.Kind.DAMAGE)>=maxDamagers||
damagersByOwner.getOrDefault(owner,0)>=maxDamagersPerOwner)){
event.setCancelled(true);tell(event.getPlayer(),"Достигнут лимит дамагеров.");refused++;return;
}
TileState tile=tile(b);
if(tile==null){event.setCancelled(true);return;}
MachineRegistry.Machine m=new MachineRegistry.Machine(position(b),owner,kind);
if(!registry.add(m)){event.setCancelled(true);return;}
tile.getPersistentDataContainer().set(machineKey,PersistentDataType.STRING,kind.name());
tile.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,owner.toString());
if(!tile.update(true,false)){
registry.remove(m);event.setCancelled(true);return;
}
try{registry.save();}
catch(IOException failure){
registry.remove(m);
event.setCancelled(true);errors++;
tell(event.getPlayer(),"Не удалось сохранить устройство на диск.");
return;
}
if(kind==MachineRegistry.Kind.DAMAGE)damagersByOwner.merge(owner,1,Integer::sum);
else activate(owner);
tell(event.getPlayer(),kind==MachineRegistry.Kind.DAMAGE?
"Дамагер установлен. ПКМ для слота оружия.":"Прогрузчик установлен. Пока ты онлайн, этот чанк загружен.");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onBreak(BlockBreakEvent event){
MachineRegistry.Machine m=tracked(event.getBlock());
if(m==null)return;
Player player=event.getPlayer();
if(!m.owner().equals(player.getUniqueId())&&!player.hasPermission("unifiedmachines.admin")){
event.setCancelled(true);tell(player,"Это устройство другого игрока.");return;
}
ItemStack weapon=m.kind()==MachineRegistry.Kind.DAMAGE?readWeapon(event.getBlock()):null;
event.setDropItems(false);
registry.remove(m);
if(m.kind()==MachineRegistry.Kind.DAMAGE){
damagersByOwner.computeIfPresent(m.owner(),(u,n)->n<=1?null:n-1);
}else deactivate(m.owner());
saveRegistry();
Location drop=event.getBlock().getLocation().add(0.5,0.5,0.5);
event.getBlock().getWorld().dropItemNaturally(drop,item(m.kind()));
if(weapon!=null)event.getBlock().getWorld().dropItemNaturally(drop,weapon);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onExplosion(BlockExplodeEvent event){
event.blockList().removeIf(b->tracked(b)!=null);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onEntityExplosion(org.bukkit.event.entity.EntityExplodeEvent event){
event.blockList().removeIf(b->tracked(b)!=null);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void pistonExtend(BlockPistonExtendEvent event){
for(Block b:event.getBlocks())if(tracked(b)!=null){event.setCancelled(true);return;}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void pistonRetract(BlockPistonRetractEvent event){
for(Block b:event.getBlocks())if(tracked(b)!=null){event.setCancelled(true);return;}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onDispense(BlockDispenseEvent event){
if(tracked(event.getBlock())!=null)event.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onInventoryMove(InventoryMoveItemEvent event){
// No hoppers can push into / pull from the physical machine block.
if(isMachineInventory(event.getSource())||isMachineInventory(event.getDestination()))event.setCancelled(true);
}
private boolean isMachineInventory(Inventory inventory){
InventoryHolder holder=inventory.getHolder();
return holder instanceof org.bukkit.block.Dropper d && tracked(d.getBlock())!=null;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interact(PlayerInteractEvent event){
if(event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null)return;
MachineRegistry.Machine machine=tracked(event.getClickedBlock());
if(machine==null)return;
event.setCancelled(true);
Player player=event.getPlayer();
if(machine.kind()==MachineRegistry.Kind.LOADER){
boolean running=activeOwner.containsKey(machine.owner());
player.sendActionBar(Component.text("Прогрузчик: "+(running?"работает":"выключен")+" • "+machine.owner(),NamedTextColor.AQUA));
return;
}
if(!machine.owner().equals(player.getUniqueId())&&!player.hasPermission("unifiedmachines.admin")){
tell(player,"Дамагер другого игрока.");return;
}
Menu menu=new Menu(machine.position(),machine.owner());
for(int slot=0;slot<9;slot++){
if(slot==4)continue;
ItemStack pane=new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
ItemMeta meta=pane.getItemMeta();
meta.displayName(Component.text(" "));
pane.setItemMeta(meta);
menu.inv.setItem(slot,pane);
}
menu.inv.setItem(4,readWeapon(event.getClickedBlock()));
player.openInventory(menu.inv);
guiOpens++;
}
private boolean isValidWeapon(ItemStack item){
if(item==null || item.getType().isAir() || item.getAmount()!=1)return false;
if(!MachineRules.weaponName(item.getType().name())||item.getType().getMaxDurability()<=0)return false;
ItemMeta meta=item.getItemMeta();
return meta instanceof Damageable && !meta.isUnbreakable();
}
private ItemStack readWeapon(Block b){
TileState state=tile(b);
if(state==null)return null;
byte[] bytes=state.getPersistentDataContainer().get(weaponKey,PersistentDataType.BYTE_ARRAY);
if(bytes==null||bytes.length==0||bytes.length>131072)return null;
try{
ItemStack stack=ItemStack.deserializeBytes(bytes);
return isValidWeapon(stack)?stack:null;
}catch(RuntimeException invalid){errors++;return null;}
}
private boolean storeWeapon(Block b,ItemStack weapon){
TileState tile=tile(b);
if(tile==null)return false;
try{
if(weapon==null)tile.getPersistentDataContainer().remove(weaponKey);
else{
if(!isValidWeapon(weapon))return false;
byte[] bytes=weapon.serializeAsBytes();
if(bytes.length>131072)return false;
tile.getPersistentDataContainer().set(weaponKey,PersistentDataType.BYTE_ARRAY,bytes);
}
return tile.update(true,false);
}catch(RuntimeException error){errors++;return false;}
}
@EventHandler(priority=EventPriority.HIGHEST)
public void guiClick(InventoryClickEvent event){
if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
event.setCancelled(true);
if(!(event.getWhoClicked() instanceof Player player))return;
if(event.getRawSlot()!=4)return;
if(!menu.owner.equals(player.getUniqueId())&&!player.hasPermission("unifiedmachines.admin"))return;
World w=Bukkit.getWorld(menu.position.world());
if(w==null||!w.isChunkLoaded(menu.position.cx(),menu.position.cz()))return;
Block b=w.getBlockAt(menu.position.x(),menu.position.y(),menu.position.z());
MachineRegistry.Machine m=tracked(b);
if(m==null || m.kind()!=MachineRegistry.Kind.DAMAGE)return;
ItemStack tool=readWeapon(b);
ItemStack cursor=event.getCursor();
boolean cursorEmpty=cursor==null||cursor.getType().isAir();
if(cursorEmpty && tool!=null){
if(storeWeapon(b,null)){event.setCursor(tool.clone());menu.inv.setItem(4,null);}
return;
}
if(!cursorEmpty && isValidWeapon(cursor)){
if(tool==null){
if(storeWeapon(b,cursor.clone())){event.setCursor(null);menu.inv.setItem(4,cursor.clone());}
}else if(cursor.getAmount()==1){
if(storeWeapon(b,cursor.clone())){
event.setCursor(tool.clone());
menu.inv.setItem(4,cursor.clone());
}
}
}
}
@EventHandler(priority=EventPriority.HIGHEST)
public void guiDrag(InventoryDragEvent event){
if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);
}
@EventHandler public void join(PlayerJoinEvent event){
Bukkit.getScheduler().runTaskLater(this,()->activate(event.getPlayer().getUniqueId()),10L);
}
@EventHandler public void quit(PlayerQuitEvent event){
deactivate(event.getPlayer().getUniqueId());
}
private void activate(UUID owner){
if(activeOwner.containsKey(owner)||Bukkit.getPlayer(owner)==null)return;
MachineRegistry.Machine m=registry.owners.get(owner);
if(m==null)return;
World w=Bukkit.getWorld(m.position().world());
if(w==null)return;
ChunkKey ck=new ChunkKey(w.getUID(),m.position().cx(),m.position().cz());
Set<UUID> users=chunkUsers.get(ck);
if(users==null && chunkUsers.size()>=maxActiveChunks){refused++;return;}
boolean newChunk=users==null;
if(newChunk){
try{w.addPluginChunkTicket(ck.x(),ck.z(),this);}
catch(RuntimeException failed){errors++;getLogger().warning("Cannot ticket machine chunk: "+failed.getMessage());return;}
}
if(!matching(w.getBlockAt(m.position().x(),m.position().y(),m.position().z()),m)){
if(newChunk)w.removePluginChunkTicket(ck.x(),ck.z(),this);
registry.remove(m);saveRegistry();return;
}
if(newChunk){users=new HashSet<>();chunkUsers.put(ck,users);}
users.add(owner);
activeOwner.put(owner,ck);
ticketActivations++;
}
private void deactivate(UUID owner){
ChunkKey key=activeOwner.remove(owner);
if(key==null)return;
Set<UUID> users=chunkUsers.get(key);
if(users==null)return;
users.remove(owner);
if(!users.isEmpty())return;
chunkUsers.remove(key);
World w=Bukkit.getWorld(key.world());
if(w!=null)w.removePluginChunkTicket(key.x(),key.z(),this);
ticketReleases++;
}
private void strike(){
try{
if(registry.machines.isEmpty())return;
List<MachineRegistry.Machine> snapshot=new ArrayList<>();
for(MachineRegistry.Machine m:registry.machines.values())if(m.kind()==MachineRegistry.Kind.DAMAGE)snapshot.add(m);
if(snapshot.isEmpty())return;
int total=snapshot.size();
int budget=Math.min(total,maxScanned);
for(int i=0;i<budget;i++){
MachineRegistry.Machine m=snapshot.get(Math.floorMod(scanCursor++,total));
hit(m);
}
if(scanCursor>1000000000)scanCursor%=total;
}catch(RuntimeException bad){errors++;getLogger().warning("Damager tick failed safely: "+bad.getMessage());}
}
private void hit(MachineRegistry.Machine m){
MachineRegistry.Position p=m.position();
World w=Bukkit.getWorld(p.world());
if(w==null||!w.isChunkLoaded(p.cx(),p.cz()))return;
Block b=w.getBlockAt(p.x(),p.y(),p.z());
if(!matching(b,m)){
registry.remove(m);
damagersByOwner.computeIfPresent(m.owner(),(id,n)->n<=1?null:n-1);
saveRegistry();return;
}
ItemStack tool=readWeapon(b);
if(tool==null)return;
Location center=b.getLocation().add(0.5,1.0,0.5);
LivingEntity target=null;
for(Entity candidate:w.getNearbyEntities(center,range,range,range)){
if(!(candidate instanceof Mob mob)||candidate instanceof Player||candidate.isDead())continue;
if(candidate instanceof AbstractVillager || candidate instanceof Golem)continue;
if(candidate instanceof Tameable tame && tame.isTamed())continue;
if(!passiveTargets && !(candidate instanceof Monster))continue;
target=mob;break;
}
if(target==null)return;
double damage=MachineRules.damage(tool.getType().name());
if(damage<=0)return;
target.damage(damage);
ItemMeta meta=tool.getItemMeta();
if(!(meta instanceof Damageable durability))return;
int next=durability.getDamage()+1;
if(next>=tool.getType().getMaxDurability()){
if(storeWeapon(b,null))brokenWeapons++;
}else{
durability.setDamage(next);
tool.setItemMeta(meta);
storeWeapon(b,tool);
}
hits++;
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length>=3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("unifiedmachines.admin")){
Player target=Bukkit.getPlayerExact(args[1]);
if(target==null){sender.sendMessage("Игрок не найден.");return true;}
MachineRegistry.Kind type=args[2].equalsIgnoreCase("damager")?MachineRegistry.Kind.DAMAGE:
args[2].equalsIgnoreCase("loader")?MachineRegistry.Kind.LOADER:null;
if(type==null){sender.sendMessage("/umachines give <player> <damager|loader>");return true;}
Map<Integer,ItemStack> extra=target.getInventory().addItem(item(type));
for(ItemStack remaining:extra.values())target.getWorld().dropItemNaturally(target.getLocation(),remaining);
sender.sendMessage("Выдано: "+type+" -> "+target.getName());
return true;
}
sender.sendMessage("UnifiedMachines 1.0: damageBlocks="+registry.count(MachineRegistry.Kind.DAMAGE)+
" chunkLoaders="+registry.count(MachineRegistry.Kind.LOADER)+" activeChunks="+chunkUsers.size()+
"/"+maxActiveChunks+" hits="+hits+" brokenWeapons="+brokenWeapons+
" errors="+errors+" refused="+refused);
sender.sendMessage("Commands: /umachines | /umachines give <player> <damager|loader> (admin)");
return true;
}
}
