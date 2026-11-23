package dev.unified.defend;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.plugin.java.JavaPlugin;

public final class UnifiedDefend extends JavaPlugin implements Listener {
private static final class Change {
final String id;
final UUID world;
final int x,y,z;
final Material expected;
final String original;
final long due;
Change(String id,UUID world,int x,int y,int z,Material expected,String original,long due){
this.id=id;this.world=world;this.x=x;this.y=y;this.z=z;
this.expected=expected;this.original=original;this.due=due;
}
Change later(long next){return new Change(id,world,x,y,z,expected,original,next);}
}
private static final class ChestTimer {
long due;
String table;
ChestTimer(long due,String table){this.due=due;this.table=table;}
}
private StructureIndex index;
private final Map<String,Change> placed=new HashMap<>();
private final Map<String,Change> repairs=new HashMap<>();
private final Map<String,ChestTimer> chests=new LinkedHashMap<>();
private Iterator<Map.Entry<String,ChestTimer>> chestCursor;
private final Map<UUID,org.bukkit.entity.TextDisplay> floatingTimers=new HashMap<>();
private final PriorityQueue<Change> deadlines=new PriorityQueue<>(java.util.Comparator.comparingLong(c->c.due));
private final Random lootRandom=new Random();
private final Set<StructureIndex.Kind> types=EnumSet.allOf(StructureIndex.Kind.class);
private long placedMs,repairMs,lootMs;
private int maxRecords,changesPerPass,chestChecksPerPass,task=-1,overlayTask=-1,persistTask=-1;
private boolean showFloatingTimers,protectNetherBlazeSpawners;
private boolean restoreTerrain,refillLoot,protectChests;
private boolean dirty;
private long placements,expired,rebuilt,refilled,protectedBreaks,skipped,errors;

@Override public void onEnable(){
saveDefaultConfig();
placedMs=clamp(getConfig().getLong("temporary-blocks.minutes",10),1,1440)*60000L;
repairMs=clamp(getConfig().getLong("structure-restoration.minutes",35),5,1440)*60000L;
lootMs=clamp(getConfig().getLong("chest-restock.minutes",35),5,1440)*60000L;
maxRecords=(int)clamp(getConfig().getLong("performance.max-records-per-type",5000),100,50000);
changesPerPass=(int)clamp(getConfig().getLong("performance.max-expired-changes-per-cycle",32),1,256);
chestChecksPerPass=(int)clamp(getConfig().getLong("performance.max-chest-refill-checks-per-second",64),4,512);
showFloatingTimers=getConfig().getBoolean("timers.floating-text-on-look",true);
protectNetherBlazeSpawners=getConfig().getBoolean("protection.protect-nether-blaze-spawners",true);
restoreTerrain=getConfig().getBoolean("structure-restoration.enabled",true);
refillLoot=getConfig().getBoolean("chest-restock.enabled",true);
protectChests=getConfig().getBoolean("protection.protect-generated-chests",true);
for(StructureIndex.Kind type:StructureIndex.Kind.values()){
if(!getConfig().getBoolean("structures."+type.name().toLowerCase(Locale.ROOT),true))types.remove(type);
}
index=new StructureIndex(types,(int)clamp(getConfig().getLong("performance.structure-chunk-cache",2048),64,8192));
try{loadState();}catch(IOException ex){getLogger().severe("Could not read recovery state, plugin disabled to avoid overwriting previous data: "+ex.getMessage());Bukkit.getPluginManager().disablePlugin(this);return;}
Bukkit.getPluginManager().registerEvents(this,this);
Bukkit.getPluginManager().registerEvents(index,this);
task=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{processExpired();processChestRefills();},20L,20L);
overlayTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,this::tickOverlay,20L,20L);
persistTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{if(dirty)saveSafe();},6000L,6000L);
getLogger().info("Defend enabled: "+types+" | blocks 10min | terrain 35min | chests 35min; no chunk force-load.");
}
@Override public void onDisable(){
if(task!=-1)Bukkit.getScheduler().cancelTask(task);
if(overlayTask!=-1)Bukkit.getScheduler().cancelTask(overlayTask);
if(persistTask!=-1)Bukkit.getScheduler().cancelTask(persistTask);
if(dirty)saveSafe();
for(org.bukkit.entity.TextDisplay display:floatingTimers.values()){
if(display.isValid())display.remove();
}
floatingTimers.clear();
if(index!=null)index.clear();
}
private static long clamp(long val,long low,long high){return Math.max(low,Math.min(high,val));}
private String id(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
private String id(Change c){return c.id;}
private static boolean empty(Inventory inv){
for(ItemStack i:inv.getStorageContents())if(i!=null && !i.getType().isAir() && i.getAmount()>0)return false;
return true;
}
private void queue(Map<String,Change> group,Change c){
if(group.size()>=maxRecords && !group.containsKey(c.id)){skipped++;return;}
group.put(c.id,c);
deadlines.offer(c);
dirty=true;
}
private static boolean isAir(Block b){return b.getType().isAir();}
private boolean bypass(Player p){
// OP and wildcard permissions never bypass dungeon protection by default.
// A bypass is possible ONLY by explicitly opting in via server config.
return getConfig().getBoolean("protection.allow-bypass-permission",false)
&& p.hasPermission("unifieddefend.bypass");
}
private boolean netherBlazeSpawner(Block b){
if(!protectNetherBlazeSpawners || b.getWorld().getEnvironment()!=World.Environment.NETHER
|| b.getType()!=Material.SPAWNER)return false;
BlockState state=b.getState();
return state instanceof CreatureSpawner spawner && spawner.getSpawnedType()==org.bukkit.entity.EntityType.BLAZE;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void breakBlock(BlockBreakEvent e){
Block b=e.getBlock();
StructureIndex.Kind kind=index.at(b);
if(bypass(e.getPlayer()))return;
// Reliable fallback: all natural/placed blaze spawners in the Nether are protected.
// Even if chunk structure metadata cannot find the fortress, mining is blocked.
if(netherBlazeSpawner(b)){
e.setCancelled(true);
protectedBreaks++;
return;
}
if(kind==null)return;
if(RecoveryPolicy.protectedDungeonBlock(b.getType()) &&
(protectChests || b.getType()==Material.SPAWNER || b.getType()==Material.TRIAL_SPAWNER || b.getType()==Material.VAULT)){
e.setCancelled(true);
protectedBreaks++;
return;
}
String key=id(b);
Change temp=placed.remove(key);
if(temp!=null){dirty=true;return;}
if(!restoreTerrain)return;
Material restore=RecoveryPolicy.repairedAs(kind,b.getType());
if(restore==null)return;
String data=restore==b.getType()?b.getBlockData().getAsString():Bukkit.createBlockData(restore).getAsString();
queue(repairs,new Change(key,b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),Material.AIR,data,System.currentTimeMillis()+repairMs));
}

@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void placeBlock(BlockPlaceEvent e){
Block b=e.getBlockPlaced();
if(index.at(b)==null || bypass(e.getPlayer()))return;
if(RecoveryPolicy.dangerousPlacement(b.getType()))return;
BlockState replaced=e.getBlockReplacedState();
String data=replaced.getBlockData().getAsString();
String key=id(b);
queue(placed,new Change(key,b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),b.getType(),data,System.currentTimeMillis()+placedMs));
placements++;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void rejectContainers(BlockPlaceEvent e){
// Placed chests and storage blocks cannot safely expire with player items inside.
if(index.at(e.getBlockPlaced())!=null && RecoveryPolicy.dangerousPlacement(e.getBlockPlaced().getType())
&& !bypass(e.getPlayer())){
e.setCancelled(true);
e.getPlayer().sendActionBar(Component.text("Хранилища и механизмы нельзя размещать в данже",NamedTextColor.RED));
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interact(PlayerInteractEvent e){
if(e.getAction()!=Action.RIGHT_CLICK_BLOCK || e.getClickedBlock()==null)return;
Block b=e.getClickedBlock();
StructureIndex.Kind kind=index.at(b);
if(kind==null || !(b.getState() instanceof Chest || b.getState() instanceof Barrel))return;
ChestTimer timer=discoverChest(b,kind);
if(timer!=null)tryRefill(b,timer);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void inventoryOpen(InventoryOpenEvent e){
// Do not scan arbitrary inventories. Chest tracking is initiated by block interaction.
// A double chest is intentionally not handled to avoid duplicate filling.
}
private ChestTimer discoverChest(Block b,StructureIndex.Kind kind){
if(!refillLoot)return null;
String id=id(b);
ChestTimer timer=chests.get(id);
BlockState state=b.getState();
if(!(state instanceof Lootable lootable))return null;
String originalTable=lootable.getLootTable()==null?null:lootable.getLootTable().getKey().toString();
if(timer!=null){
if(timer.table==null && originalTable!=null){timer.table=originalTable;dirty=true;}
return timer;
}
if(chests.size()>=maxRecords){skipped++;return null;}
String fallback="minecraft:"+kind.fallbackLoot;
String table=originalTable!=null?originalTable:fallback;
ChestTimer fresh=new ChestTimer(System.currentTimeMillis()+lootMs,table);
chests.put(id,fresh);
chestCursor=null;
dirty=true;
return fresh;
}
private void tryRefill(Block b,ChestTimer timer){
if(System.currentTimeMillis()<timer.due)return;
BlockState state=b.getState();
if(!(state instanceof Chest || state instanceof Barrel))return;
if(state instanceof Lootable lootable && lootable.getLootTable()!=null){
// Vanilla first opening must generate its original loot before any refresh.
// First interaction begins a fresh 35-minute restock timer.
timer.due=System.currentTimeMillis()+lootMs;
dirty=true;
return;
}
Inventory inv=state instanceof Chest chest?chest.getBlockInventory():((Barrel)state).getInventory();
if(!empty(inv))return; // never remove players' deposited items
NamespacedKey key=NamespacedKey.fromString(timer.table);
if(key==null)return;
LootTable table=Bukkit.getLootTable(key);
if(table==null){skipped++;return;}
try{
Collection<ItemStack> rolled=table.populateLoot(lootRandom,new LootContext.Builder(b.getLocation()).build());
int items=0;
for(ItemStack stack:rolled){
if(stack==null || stack.getType().isAir() || stack.getAmount()<1)continue;
if(items++>=9)break; // bounded rolls; remaining loot is not given
Map<Integer,ItemStack> leftover=inv.addItem(stack);
if(!leftover.isEmpty())break;
}
timer.due=System.currentTimeMillis()+lootMs;
dirty=true;
refilled++;
}catch(RuntimeException error){errors++;getLogger().warning("Could not refill chest loot at "+b.getLocation()+": "+error.getMessage());}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void explosion(EntityExplodeEvent event){filterExplosion(event.blockList());}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void blockExplosion(BlockExplodeEvent event){filterExplosion(event.blockList());}
private void filterExplosion(List<Block> blocks){
Iterator<Block> iterator=blocks.iterator();
while(iterator.hasNext()){
Block b=iterator.next();
StructureIndex.Kind kind=index.at(b);
if(netherBlazeSpawner(b)){
iterator.remove();
protectedBreaks++;
continue;
}
if(kind==null)continue;
if(RecoveryPolicy.protectedDungeonBlock(b.getType())){
iterator.remove();
protectedBreaks++;
continue;
}
if(!restoreTerrain)continue;
Material repair=RecoveryPolicy.repairedAs(kind,b.getType());
if(repair==null)continue;
String orig=repair==b.getType()?b.getBlockData().getAsString():Bukkit.createBlockData(repair).getAsString();
queue(repairs,new Change(id(b),b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),Material.AIR,orig,System.currentTimeMillis()+repairMs));
}
}
private void processChestRefills(){
// Bounded round-robin scan of registered chest locations. No chunk loads,
// no world scans, and no attempt to replace player-stored items.
if(!refillLoot || chests.isEmpty())return;
if(chestCursor==null)chestCursor=chests.entrySet().iterator();
int checked=0;
while(checked++<chestChecksPerPass){
if(!chestCursor.hasNext()){
chestCursor=null;
break;
}
Map.Entry<String,ChestTimer> entry=chestCursor.next();
ChestTimer timer=entry.getValue();
if(timer.due>System.currentTimeMillis())continue;
String[] p=entry.getKey().split(":",4);
if(p.length!=4)continue;
try{
UUID worldId=UUID.fromString(p[0]);
World world=Bukkit.getWorld(worldId);
if(world==null)continue;
int x=Integer.parseInt(p[1]),y=Integer.parseInt(p[2]),z=Integer.parseInt(p[3]);
if(!world.isChunkLoaded(x>>4,z>>4))continue;
Block block=world.getBlockAt(x,y,z);
if(!(block.getState() instanceof Chest || block.getState() instanceof Barrel))continue;
// Do not refill previously untouched vanilla treasure: let vanilla do its first roll.
BlockState state=block.getState();
if(state instanceof Lootable lootable && lootable.getLootTable()!=null)continue;
tryRefill(block,timer);
}catch(RuntimeException invalid){errors++;}
}
}
private void processExpired(){
long now=System.currentTimeMillis();
int inspected=0;
while(inspected++<changesPerPass && !deadlines.isEmpty() && deadlines.peek().due<=now){
Change c=deadlines.poll();
Map<String,Change> group=placed.get(c.id)==c?placed:repairs.get(c.id)==c?repairs:null;
if(group==null)continue;
World world=Bukkit.getWorld(c.world);
if(world==null||!world.isChunkLoaded(c.x>>4,c.z>>4)){
Change postponed=c.later(now+30000);
group.put(c.id,postponed);
deadlines.offer(postponed);
continue;
}
Block b=world.getBlockAt(c.x,c.y,c.z);
if(group==placed){
if(b.getType()!=c.expected || RecoveryPolicy.dangerousPlacement(b.getType())){
group.remove(c.id);dirty=true;skipped++;continue;
}
}else if(!isAir(b)){
// Player-built blocks, sources and water take priority over restoration.
group.remove(c.id);dirty=true;skipped++;continue;
}
try{
BlockData original=Bukkit.createBlockData(c.original);
if(RecoveryPolicy.fluid(b.getType())){
group.remove(c.id);dirty=true;skipped++;continue;
}
b.setBlockData(original,group==placed);
if(group==placed)expired++;else rebuilt++;
}catch(Exception ex){errors++;getLogger().warning("Skipping damaged restore record for "+c.id);}
group.remove(c.id);
dirty=true;
}
}
private void tickOverlay(){
Set<UUID> active=new HashSet<>();
for(Player p:Bukkit.getOnlinePlayers()){
Block target=p.getTargetBlockExact(7,FluidCollisionMode.NEVER);
if(target==null){hideFloating(p.getUniqueId());continue;}
String key=id(target);
String text=null;
NamedTextColor color=NamedTextColor.GOLD;
Change temp=placed.get(key);
if(temp!=null){
text="Исчезнет через "+duration(temp.due);
color=NamedTextColor.YELLOW;
}else {
Change repair=repairs.get(key);
if(repair!=null){
text="Восстановление: "+duration(repair.due);
color=NamedTextColor.AQUA;
}else {
ChestTimer chest=chests.get(key);
BlockState state=target.getState();
if(state instanceof Chest || state instanceof Barrel){
if(chest==null){
StructureIndex.Kind kind=index.at(target);
if(kind!=null)chest=discoverChest(target,kind);
}
if(chest!=null){
text=chest.due<=System.currentTimeMillis()?"Сундук: готов к пополнению (если пуст)":
"Сундук: "+duration(chest.due);
color=NamedTextColor.GOLD;
}
}else if(netherBlazeSpawner(target) || index.at(target)!=null && RecoveryPolicy.protectedDungeonBlock(target.getType())){
text="Защищённый блок данжа";
color=NamedTextColor.GREEN;
}
}
}
if(text==null){hideFloating(p.getUniqueId());continue;}
active.add(p.getUniqueId());
p.sendActionBar(Component.text(text,color));
if(showFloatingTimers)showFloating(p,target,text,color);
}
for(UUID id:new ArrayList<>(floatingTimers.keySet())){
if(!active.contains(id))hideFloating(id);
}
}
private void showFloating(Player viewer,Block block,String message,NamedTextColor color){
org.bukkit.entity.TextDisplay display=floatingTimers.get(viewer.getUniqueId());
Location at=block.getLocation().add(0.5,1.26,0.5);
if(display!=null && !display.isValid()){
floatingTimers.remove(viewer.getUniqueId());display=null;
}
if(display!=null && display.getWorld()!=block.getWorld()){
display.remove();floatingTimers.remove(viewer.getUniqueId());display=null;
}
if(display==null){
display=block.getWorld().spawn(at,org.bukkit.entity.TextDisplay.class,d->{
d.setVisibleByDefault(false);
d.setPersistent(false);
d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
d.setSeeThrough(true);
d.setShadowed(true);
d.setViewRange(0.16f);
});
floatingTimers.put(viewer.getUniqueId(),display);
viewer.showEntity(this,display);
}else if(display.getLocation().distanceSquared(at)>0.01){
display.teleport(at);
}
display.text(Component.text(message,color));
}
private void hideFloating(UUID playerId){
org.bukkit.entity.TextDisplay old=floatingTimers.remove(playerId);
if(old!=null && old.isValid())old.remove();
}
@org.bukkit.event.EventHandler
public void quit(org.bukkit.event.player.PlayerQuitEvent event){
hideFloating(event.getPlayer().getUniqueId());
}
private static String duration(long when){
long seconds=Math.max(0,(when-System.currentTimeMillis()+999)/1000);
return (seconds/60)+":"+String.format(Locale.ROOT,"%02d",seconds%60);
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length>0 && args[0].equalsIgnoreCase("save")){
if(!sender.hasPermission("unifieddefend.admin"))return true;
saveSafe();sender.sendMessage("UnifiedDefend state saved.");return true;
}
if(args.length>0 && args[0].equalsIgnoreCase("inspect")){
if(!sender.hasPermission("unifieddefend.admin"))return true;
if(!(sender instanceof Player player)){sender.sendMessage("Inspect requires a player looking at a block.");return true;}
Block target=player.getTargetBlockExact(7,FluidCollisionMode.NEVER);
if(target==null){sender.sendMessage("Посмотри на блок с расстояния до 7 блоков.");return true;}
StructureIndex.Kind kind=index.at(target);
sender.sendMessage("Defend 1.1 inspect: "+target.getType()+" at "+target.getX()+","+target.getY()+","+target.getZ()+
" / "+target.getWorld().getName());
sender.sendMessage("Generated structure: "+(kind==null?"NOT FOUND (not protected as structure)":kind.name())+
" | Nether blaze spawner fallback="+netherBlazeSpawner(target)+" | admin bypass active="+bypass(player));
String key=id(target);
ChestTimer timer=chests.get(key);
Change temporary=placed.get(key);
sender.sendMessage("Known chest="+(timer!=null)+" timer="+(timer==null?"none":duration(timer.due))+
" | temporary block="+(temporary!=null)+" | registered chests="+chests.size());
return true;
}
sender.sendMessage("UnifiedDefend 1.1: chunksCached="+(index==null?0:index.size())+" temporary="+placed.size()+" repairs="+repairs.size()+" chests="+chests.size());
sender.sendMessage("Placed="+placements+" expired="+expired+" restored="+rebuilt+" lootRestocks="+refilled+" protectedBlocks="+protectedBreaks+" skipped="+skipped+" errors="+errors);
return true;
}

private File stateFile(){return new File(getDataFolder(),"state.properties");}
private void loadState() throws IOException {
File file=stateFile();
if(!file.exists())return;
Properties p=new Properties();
try(InputStream in=new FileInputStream(file)){p.load(in);}
if(p.size()>200000)throw new IOException("Unexpectedly large state");
long now=System.currentTimeMillis();
for(String k:p.stringPropertyNames()){
try{
String[] parts=k.split(":",-1);
if(parts.length!=5)continue;
String id=parts[1]+":"+parts[2]+":"+parts[3]+":"+parts[4];
UUID world=UUID.fromString(parts[1]);
int x=Integer.parseInt(parts[2]),y=Integer.parseInt(parts[3]),z=Integer.parseInt(parts[4]);
String[] v=p.getProperty(k).split(";",-1);
if(k.startsWith("chest:")){
if(chests.size()<maxRecords && v.length==2)chests.put(id,new ChestTimer(Long.parseLong(v[0]),v[1]));
}else if((k.startsWith("temp:")||k.startsWith("repair:")) && v.length==3){
long due=Long.parseLong(v[0]);
Material expected=Material.matchMaterial(v[1]);
String original=new String(Base64.getDecoder().decode(v[2]),StandardCharsets.UTF_8);
if(expected==null||original.length()>1024)continue;
Change c=new Change(id,world,x,y,z,expected,original,Math.max(now,due));
Map<String,Change> group=k.startsWith("temp:")?placed:repairs;
if(group.size()<maxRecords){group.put(id,c);deadlines.offer(c);}
}
}catch(Exception invalid){errors++;}
}
getLogger().info("Restored "+placed.size()+" temporary blocks, "+repairs.size()+" structure repairs, "+chests.size()+" chest timers.");
}
private void saveSafe(){
try{saveState();dirty=false;}
catch(IOException error){getLogger().severe("Could not persist Defend state: "+error.getMessage());}
}
private void saveState() throws IOException {
getDataFolder().mkdirs();
Properties data=new Properties();
for(Change c:placed.values())data.setProperty("temp:"+c.id,serialize(c));
for(Change c:repairs.values())data.setProperty("repair:"+c.id,serialize(c));
for(Map.Entry<String,ChestTimer> e:chests.entrySet()){
ChestTimer t=e.getValue();
data.setProperty("chest:"+e.getKey(),t.due+";"+t.table);
}
Path output=stateFile().toPath();
Path temp=Files.createTempFile(output.getParent(),"defend-state-", ".tmp");
try{
try(OutputStream out=Files.newOutputStream(temp)){
data.store(out,"UnifiedDefend persistent timers. Back up with the world.");
}
try{Files.move(temp,output,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
catch(AtomicMoveNotSupportedException noAtomic){Files.move(temp,output,StandardCopyOption.REPLACE_EXISTING);}
}finally{Files.deleteIfExists(temp);}
}
private static String serialize(Change c){
return c.due+";"+c.expected.name()+";"+Base64.getEncoder().encodeToString(c.original.getBytes(StandardCharsets.UTF_8));
}
}
