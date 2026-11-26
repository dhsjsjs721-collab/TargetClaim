package dev.unified;

import dev.targetclaim.TargetClaimPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reversible seven-by-seven PvP arena centered next to a target player.
 * Three blocks extend in every horizontal direction. Generated walls can
 * be mined without drops; original world blocks are never destroyed.
 */
public final class TrapItems implements Listener {
private static final long DURATION_MS=15_000L,COOLDOWN_MS=60_000L;
private static final int MAX_ACTIVE=40;
private static final int RADIUS=3, WALL_HEIGHT=4;
private final UnifiedPlugin plugin;
private final TargetClaimPlugin claims;
private final NamespacedKey itemKey,recipeKey,cooldownKey;
private final File saveFile;
private final Map<UUID,Trap> traps=new LinkedHashMap<>();
private final Map<BlockKey,UUID> occupied=new HashMap<>();
private final Map<UUID,Long> cooldown=new HashMap<>();
private BukkitTask task;
private record BlockKey(UUID world,int x,int y,int z){
static BlockKey of(Block b){return new BlockKey(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
String encode(){return x+","+y+","+z;}
}
private static final class Trap {
UUID id,world,owner;
long until;
final Map<BlockKey,String> original=new LinkedHashMap<>();
final Set<BlockKey> protectedFloor=new HashSet<>();
}
public TrapItems(UnifiedPlugin host,TargetClaimPlugin claimModule){
plugin=host;
claims=claimModule;
itemKey=new NamespacedKey("minecraftunified","obsidian_trap");
recipeKey=new NamespacedKey("minecraftunified","craft_obsidian_trap");cooldownKey=new NamespacedKey("minecraftunified","trap_ready_at");
saveFile=new File(host.getDataFolder(),"temporary-traps.yml");
}
public void enable(){
load();for(Player p:Bukkit.getOnlinePlayers())migrate(p);
ShapedRecipe recipe=new ShapedRecipe(recipeKey,item());
recipe.shape("OIO","IDI","OIO");
recipe.setIngredient('O',Material.OBSIDIAN);
recipe.setIngredient('I',Material.IRON_INGOT);
recipe.setIngredient('D',Material.DIAMOND);
Bukkit.removeRecipe(recipeKey);
Bukkit.addRecipe(recipe);
Bukkit.getPluginManager().registerEvents(this,plugin);
task=Bukkit.getScheduler().runTaskTimer(plugin,this::sweep,10L,10L);
plugin.getLogger().info("FunTime-style obsidian traps enabled. Recovered "+traps.size()+" saved cages.");
}
public void disable(){
if(task!=null){task.cancel();task=null;}
for(Trap t:new ArrayList<>(traps.values()))restore(t);
save();
org.bukkit.event.HandlerList.unregisterAll(this);
Bukkit.removeRecipe(recipeKey);
}
public ItemStack item(){
ItemStack stack=new ItemStack(Material.ECHO_SHARD);
ItemMeta meta=stack.getItemMeta();
meta.setDisplayName("§5§lОбсидиановая трапка");
meta.setItemModel(new NamespacedKey("minecraftunified","obsidian_trap"));
meta.setLore(List.of(
"§7ПКМ: клетка вокруг тебя",
"§7из обсидиана 7×7 (по 3 блока в стороны).",
"§7Временные стены нерушимы.",
"§7Действует 15 секунд.",
"§8Бесконечная · КД 60 секунд.",
"§8Не работает в чужих приватах."
));
meta.getPersistentDataContainer().set(itemKey,PersistentDataType.BYTE,(byte)1);
stack.setItemMeta(meta);
return stack;
}
private boolean isTrapItem(ItemStack stack){
return stack!=null&&stack.getType()==Material.ECHO_SHARD&&stack.hasItemMeta()
&&Byte.valueOf((byte)1).equals(stack.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.BYTE));
}
private void migrate(Player p){for(int i=0;i<p.getInventory().getSize();i++){ItemStack old=p.getInventory().getItem(i);if(!isTrapItem(old))continue;ItemMeta meta=old.getItemMeta();meta.setLore(item().getItemMeta().getLore());old.setItemMeta(meta);p.getInventory().setItem(i,old);}}
@EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent e){migrate(e.getPlayer());}
private void send(Player player,String message){player.sendMessage("§5Трапка §8» §7"+message);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
public void use(PlayerInteractEvent event){
if(event.getHand()!=EquipmentSlot.HAND&&event.getHand()!=EquipmentSlot.OFF_HAND)return;
if(event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)return;
ItemStack held=event.getItem();
if(!isTrapItem(held))return;
event.setUseInteractedBlock(Event.Result.DENY);
event.setUseItemInHand(Event.Result.DENY);
Player caster=event.getPlayer();if(plugin.seasonLocked(caster))return;
if(!caster.hasPermission("minecraftunified.trap.use")){send(caster,"§cНет прав.");return;}
long now=System.currentTimeMillis();
long ready=Math.max(cooldown.getOrDefault(caster.getUniqueId(),0L),caster.getPersistentDataContainer().getOrDefault(cooldownKey,PersistentDataType.LONG,0L));
if(now<ready){
send(caster,"§cПерезарядка ещё "+Math.max(1,(ready-now+999)/1000)+" сек.");
return;
}
if(traps.size()>=MAX_ACTIVE){send(caster,"§cСлишком много активных ловушек.");return;}
for(Trap t:traps.values())if(t.owner.equals(caster.getUniqueId())&&t.until>now){
send(caster,"§cТвоя предыдущая трапка ещё действует.");return;
}
if(caster.getGameMode()==GameMode.SPECTATOR||!caster.getWorld().getPVP())return;
if(!prepare(caster)){send(caster,"§cНевозможно поставить трапку: препятствие, приват или другая ловушка.");return;}
cooldown.put(caster.getUniqueId(),now+COOLDOWN_MS);caster.getPersistentDataContainer().set(cooldownKey,PersistentDataType.LONG,now+COOLDOWN_MS);
caster.setCooldown(held,1200);
send(caster,"§aКлетка 7×7 создана на 15 секунд. КД 60 секунд.");
caster.getWorld().playSound(caster.getLocation(),Sound.BLOCK_ANVIL_PLACE,.9f,.8f);
}
private boolean prepare(Player caster){
if(plugin.isPeace(caster.getLocation()))return false;
Location feet=caster.getLocation().clone();
World w=feet.getWorld();
if(w==null||!w.equals(caster.getWorld()))return false;
int x=feet.getBlockX(),y=feet.getBlockY(),z=feet.getBlockZ();
if(y<w.getMinHeight()+2||y>w.getMaxHeight()-WALL_HEIGHT-1)return false;
Trap t=new Trap();
t.id=UUID.randomUUID();t.owner=caster.getUniqueId();t.world=w.getUID();
t.until=System.currentTimeMillis()+DURATION_MS;
for(int dx=-RADIUS;dx<=RADIUS;dx++)
for(int dz=-RADIUS;dz<=RADIUS;dz++)
for(int dy=-1;dy<=WALL_HEIGHT;dy++){
boolean side=Math.abs(dx)==RADIUS||Math.abs(dz)==RADIUS;
boolean roof=dy==WALL_HEIGHT;
if(dy>=0&&!side&&!roof)continue;
int xx=x+dx,yy=y+dy,zz=z+dz;
BlockKey key=new BlockKey(w.getUID(),xx,yy,zz);
if(occupied.containsKey(key))return false;
if(!w.isChunkLoaded(xx>>4,zz>>4))return false;
Block block=w.getBlockAt(xx,yy,zz);
if(!w.getWorldBorder().isInside(block.getLocation()))return false;
if(plugin.isPeace(block.getLocation())||(!w.getName().equals(TestWorld.WORLD)||!caster.isOp())&&(claims==null||!claims.allowsTemporaryStructure(caster,block.getLocation())))return false;
if(dy==-1){
if(block.isLiquid()||(!block.getType().isSolid()&&!block.getType().isAir()))return false;
t.protectedFloor.add(key);
if(block.getType().isAir())t.original.put(key,block.getBlockData().getAsString());
}else{
if(block.getType().isAir()){t.original.put(key,block.getBlockData().getAsString());}else if(block.getType().isSolid()&&!(block.getState() instanceof org.bukkit.block.TileState)){t.protectedFloor.add(key);}else return false;
}
}

// No changes are made until every block passes all checks.
for(BlockKey key:t.original.keySet()){
w.getBlockAt(key.x,key.y,key.z).setType(Material.OBSIDIAN,false);
}
traps.put(t.id,t);
for(BlockKey key:t.original.keySet())occupied.put(key,t.id);
for(BlockKey key:t.protectedFloor)occupied.put(key,t.id);
save();
return true;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noBreak(BlockBreakEvent e){
BlockKey key=BlockKey.of(e.getBlock());
UUID trapId=occupied.get(key);
if(trapId==null)return;
Trap trap=traps.get(trapId);
if(trap==null||trap.protectedFloor.contains(key)){
e.setCancelled(true);
return;
}
if(trap.original.containsKey(key)&&e.getBlock().getType()==Material.OBSIDIAN){e.setCancelled(true);return;}
e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noPlace(org.bukkit.event.block.BlockPlaceEvent e){
if(occupied.containsKey(BlockKey.of(e.getBlockPlaced())))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noPistonExtend(BlockPistonExtendEvent e){
for(Block b:e.getBlocks())if(occupied.containsKey(BlockKey.of(b))){e.setCancelled(true);return;}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noPistonRetract(BlockPistonRetractEvent e){
for(Block b:e.getBlocks())if(occupied.containsKey(BlockKey.of(b))){e.setCancelled(true);return;}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noBlast(EntityExplodeEvent e){e.blockList().removeIf(b->occupied.containsKey(BlockKey.of(b)));}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noBlockBlast(BlockExplodeEvent e){e.blockList().removeIf(b->occupied.containsKey(BlockKey.of(b)));}
@EventHandler
public void chunks(ChunkLoadEvent e){
UUID world=e.getWorld().getUID();
int cx=e.getChunk().getX(),cz=e.getChunk().getZ();
for(Trap t:new ArrayList<>(traps.values())){
if(!t.world.equals(world)||t.until>System.currentTimeMillis())continue;
for(BlockKey key:t.original.keySet()){
if((key.x>>4)==cx&&(key.z>>4)==cz){restore(t);break;}
}
}
}
private void sweep(){
boolean changed=false;
long now=System.currentTimeMillis();
for(Trap trap:new ArrayList<>(traps.values())){
if(now<trap.until)continue;
restore(trap);
changed=true;
}
if(changed)save();
}
private void restore(Trap trap){
World w=Bukkit.getWorld(trap.world);
if(w==null)return;
for(var entry:new ArrayList<>(trap.original.entrySet())){
BlockKey key=entry.getKey();
if(!w.isChunkLoaded(key.x>>4,key.z>>4))continue;
Block block=w.getBlockAt(key.x,key.y,key.z);
if(block.getType()==Material.OBSIDIAN){
try{BlockData original=Bukkit.createBlockData(entry.getValue());block.setBlockData(original,false);}
catch(IllegalArgumentException ex){plugin.getLogger().warning("Trap block restore failed at "+key+": "+ex.getMessage());continue;}
}
trap.original.remove(key);
occupied.remove(key);
}
if(trap.original.isEmpty()){
for(BlockKey key:trap.protectedFloor)occupied.remove(key);
traps.remove(trap.id);
}
}
private void save(){
YamlConfiguration out=new YamlConfiguration();
for(Trap trap:traps.values()){
String path="traps."+trap.id+".";
out.set(path+"world",trap.world.toString());
out.set(path+"owner",trap.owner.toString());
out.set(path+"until",trap.until);
List<String> blocks=new ArrayList<>();
for(var e:trap.original.entrySet())blocks.add(e.getKey().encode()+"|"+e.getValue());
out.set(path+"blocks",blocks);
List<String> floors=new ArrayList<>();
for(BlockKey key:trap.protectedFloor)floors.add(key.encode());
out.set(path+"floors",floors);
}
saveFile.getParentFile().mkdirs();
try{out.save(saveFile);}
catch(IOException error){plugin.getLogger().severe("Could not persist temporary PvP cages: "+error.getMessage());}
}
private BlockKey decode(String world,String coord){
String[] p=coord.split(",",3);
if(p.length!=3)throw new IllegalArgumentException("bad coordinate");
return new BlockKey(UUID.fromString(world),Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2]));
}
private void load(){
if(!saveFile.isFile())return;
YamlConfiguration cfg=YamlConfiguration.loadConfiguration(saveFile);
ConfigurationSection section=cfg.getConfigurationSection("traps");
if(section==null)return;
for(String id:section.getKeys(false)){
try{
String path="traps."+id+".";
Trap trap=new Trap();trap.id=UUID.fromString(id);
trap.world=UUID.fromString(cfg.getString(path+"world"));
trap.owner=UUID.fromString(cfg.getString(path+"owner"));
trap.until=cfg.getLong(path+"until",0);
for(String line:cfg.getStringList(path+"blocks")){
String[] pair=line.split("\\|",2);
if(pair.length!=2)continue;
trap.original.put(decode(trap.world.toString(),pair[0]),pair[1]);
}
for(String line:cfg.getStringList(path+"floors"))trap.protectedFloor.add(decode(trap.world.toString(),line));
if(trap.original.isEmpty())continue;
traps.put(trap.id,trap);
for(BlockKey key:trap.original.keySet())occupied.put(key,trap.id);
for(BlockKey key:trap.protectedFloor)occupied.put(key,trap.id);
}catch(Exception e){plugin.getLogger().warning("Corrupted trap record "+id+": "+e.getMessage());}
}
sweep();
}
public boolean handleCommand(CommandSender sender,String[] args){
if(args.length==0){
sender.sendMessage("§5Трапка §7| PvP-зона 7×7 (по 3 блока), высота 4, 15 сек.");
sender.sendMessage("§7Нерушимая клетка, 15 секунд. Исходные блоки сохраняются.");
sender.sendMessage("§7Рецепт: OIO / IDI / OIO (O — обсидиан, I — железо, D — алмаз).");
sender.sendMessage("§7ПКМ — вокруг себя, даже без противника. Бесконечная, КД 60 секунд.");
if(sender.hasPermission("minecraftunified.trap.admin"))sender.sendMessage("§7/u trap give <ник> [количество]");
return true;
}
if(!args[0].equalsIgnoreCase("give"))return handleCommand(sender,new String[0]);
if(!sender.hasPermission("minecraftunified.trap.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<2){sender.sendMessage("§cИспользуй /u trap give <ник> [количество]");return true;}
Player player=Bukkit.getPlayerExact(args[1]);
if(player==null){sender.sendMessage("§cИгрок не найден.");return true;}
int amount=1;
if(args.length>2)try{amount=Integer.parseInt(args[2]);}catch(NumberFormatException e){sender.sendMessage("§cНужно число.");return true;}
if(amount<1||amount>64){sender.sendMessage("§cКоличество от 1 до 64.");return true;}
ItemStack stack=item();stack.setAmount(amount);
for(ItemStack extra:player.getInventory().addItem(stack).values())player.getWorld().dropItemNaturally(player.getLocation(),extra);
sender.sendMessage("§aВыдано "+amount+" трапок игроку "+player.getName());
return true;
}
}
