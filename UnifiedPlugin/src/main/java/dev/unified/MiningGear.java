package dev.unified;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Light;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class MiningGear implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey key=new NamespacedKey("minecraftunified","mining_gear");
private final NamespacedKey helmetRecipe=new NamespacedKey("minecraftunified","miner_helmet"),toolRecipe=new NamespacedKey("minecraftunified","iron_multitool");
private record Pos(UUID world,int x,int y,int z){
static Pos of(Block b){return new Pos(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
Block loaded(){World w=Bukkit.getWorld(world);return w!=null&&w.isChunkLoaded(x>>4,z>>4)?w.getBlockAt(x,y,z):null;}
String encode(){return world+","+x+","+y+","+z;}
}
private record Lease(String original,int level){}
private record Flight(UUID owner,Location location,Vector velocity,int age){}
private final Map<Pos,Lease> lamps=new HashMap<>();
private final Map<UUID,Flight> flights=new HashMap<>();
private final File journal;
MiningGear(UnifiedPlugin h){host=h;journal=new File(h.getDataFolder(),"temporary-lights.yml");}
void enable(){
loadLights();restoreUnwanted(Set.of());persistLights();
Bukkit.getPluginManager().registerEvents(this,host);
Bukkit.removeRecipe(helmetRecipe);Bukkit.removeRecipe(toolRecipe);
ShapedRecipe helmet=new ShapedRecipe(helmetRecipe,item("helmet"));helmet.shape(" I ","LHL"," R ");helmet.setIngredient('I',Material.IRON_INGOT);helmet.setIngredient('L',Material.LANTERN);helmet.setIngredient('H',Material.IRON_HELMET);helmet.setIngredient('R',Material.REDSTONE);Bukkit.addRecipe(helmet);
ShapedRecipe tool=new ShapedRecipe(toolRecipe,item("multitool"));tool.shape("PAS"," I "," T ");tool.setIngredient('P',Material.IRON_PICKAXE);tool.setIngredient('A',Material.IRON_AXE);tool.setIngredient('S',Material.IRON_SHOVEL);tool.setIngredient('I',Material.IRON_INGOT);tool.setIngredient('T',Material.STICK);Bukkit.addRecipe(tool);
Bukkit.getScheduler().runTaskTimer(host,this::updateLights,10L,10L);
Bukkit.getScheduler().runTaskTimer(host,this::throwTick,1L,1L);
host.getLogger().info("MiningGear enabled: dynamic lights, miner helmet, throwable torches, iron multitool.");
}
private ItemStack item(String type){
ItemStack i=new ItemStack(type.equals("helmet")?Material.IRON_HELMET:Material.IRON_PICKAXE);ItemMeta m=i.getItemMeta();
m.getPersistentDataContainer().set(key,PersistentDataType.STRING,type);
if(type.equals("helmet")){m.setDisplayName("§6Шахтёрская каска");m.setLore(List.of("§7Освещает путь без факела в руке","§7Уровень света: 12"));}
else{
m.setDisplayName("§6Железный мультитул");m.setLore(List.of("§7Кирка · Топор · Лопата","§7Железный уровень добычи · Общая прочность: 500"));
var t=m.getTool();t.setRules(List.of());t.setDefaultMiningSpeed(1f);t.setDamagePerBlock(1);
t.addRule(Tag.INCORRECT_FOR_IRON_TOOL,null,false);
t.addRule(Tag.MINEABLE_PICKAXE,6f,true);t.addRule(Tag.MINEABLE_AXE,6f,true);t.addRule(Tag.MINEABLE_SHOVEL,6f,true);
m.setTool(t);if(m instanceof Damageable d)d.setMaxDamage(500);
}
i.setItemMeta(m);return i;
}
private boolean is(ItemStack i,String type){return i!=null&&i.hasItemMeta()&&type.equals(i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING));}
private boolean survival(Player p){return p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE;}
boolean command(CommandSender sender,String[] args){
if(args.length!=3||!args[0].equals("give")){sender.sendMessage("§7/u mining give <игрок> helmet|multitool · Shift+ПКМ с факелом — бросок");return true;}
if(!sender.hasPermission("minecraftunified.mining.admin")){sender.sendMessage("§cНет прав.");return true;}
Player p=Bukkit.getPlayerExact(args[1]);if(p==null||!Set.of("helmet","multitool").contains(args[2])){sender.sendMessage("§cИгрок или предмет не найден.");return true;}
for(ItemStack rest:p.getInventory().addItem(item(args[2])).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);return true;
}
private int level(ItemStack i){
if(i==null)return 0;
return switch(i.getType()){case TORCH,WALL_TORCH,GLOWSTONE,SEA_LANTERN,SHROOMLIGHT,LANTERN,END_ROD,JACK_O_LANTERN -> 14;case SOUL_TORCH,SOUL_LANTERN -> 10;case REDSTONE_TORCH -> 7;default -> 0;};
}
private boolean persistLights(){
YamlConfiguration y=new YamlConfiguration();int n=0;
for(var e:lamps.entrySet()){String p="lights."+(n++)+".";y.set(p+"pos",e.getKey().encode());y.set(p+"original",e.getValue().original());y.set(p+"level",e.getValue().level());}
try{Path tmp=journal.toPath().resolveSibling(journal.getName()+".tmp");y.save(tmp.toFile());try{Files.move(tmp,journal.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,journal.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;}
catch(IOException ex){host.getLogger().warning("Light journal: "+ex.getMessage());return false;}
}
private void loadLights(){
YamlConfiguration y=YamlConfiguration.loadConfiguration(journal);var sec=y.getConfigurationSection("lights");if(sec==null)return;
for(String k:sec.getKeys(false))try{String p="lights."+k+".";String[] a=y.getString(p+"pos").split(",");lamps.put(new Pos(UUID.fromString(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2]),Integer.parseInt(a[3])),new Lease(y.getString(p+"original"),y.getInt(p+"level")));}catch(Exception ex){host.getLogger().warning("Invalid temporary light record: "+k);}
}
private boolean restore(Pos p,Lease l){
Block b=p.loaded();if(b==null)return false;
if(b.getBlockData() instanceof Light light&&light.getLevel()==l.level()&&!light.isWaterlogged())b.setBlockData(Bukkit.createBlockData(l.original()),false);
return true;
}
private boolean restoreUnwanted(Set<Pos> wanted){
boolean changed=false;for(var e:new ArrayList<>(lamps.entrySet()))if(!wanted.contains(e.getKey())&&restore(e.getKey(),e.getValue())){lamps.remove(e.getKey());changed=true;}return changed;
}
private void updateLights(){
if(!host.getConfig().getBoolean("mining.dynamic-light",true)){if(restoreUnwanted(Set.of()))persistLights();return;}
Map<Pos,Integer> wanted=new HashMap<>();
for(Player p:Bukkit.getOnlinePlayers()){
if(!survival(p)||p.isDead())continue;var inv=p.getInventory();int light=Math.max(level(inv.getItemInMainHand()),level(inv.getItemInOffHand()));
if(is(inv.getHelmet(),"helmet"))light=Math.max(light,12);
if(light==0)continue;
Block b=p.getEyeLocation().getBlock();Pos pos=Pos.of(b);
if(!b.getType().isAir()&&!lamps.containsKey(pos))continue;
wanted.merge(pos,light,Math::max);
}
boolean changed=restoreUnwanted(wanted.keySet());
for(var entry:wanted.entrySet()){
Pos pos=entry.getKey();Block b=pos.loaded();if(b==null)continue;
Lease old=lamps.get(pos);int value=entry.getValue();
if(old!=null&&b.getBlockData() instanceof Light l&&l.getLevel()==old.level()){
if(value==old.level())continue;
// Keep one light level until the carrier moves; avoids crash windows and excess relighting.
continue;
}
if(old!=null){lamps.remove(pos);changed=true;}
if(!b.getType().isAir())continue;
Lease next=new Lease(b.getBlockData().getAsString(),value);lamps.put(pos,next);
if(!persistLights()){lamps.remove(pos);continue;}
Light data=(Light)Material.LIGHT.createBlockData();data.setLevel(value);b.setBlockData(data,false);changed=true;
}
if(changed)persistLights();
}
@EventHandler public void unload(ChunkUnloadEvent e){
boolean changed=false;for(var entry:new ArrayList<>(lamps.entrySet())){Pos p=entry.getKey();if(p.world().equals(e.getWorld().getUID())&&(p.x()>>4)==e.getChunk().getX()&&(p.z()>>4)==e.getChunk().getZ()&&restore(p,entry.getValue())){lamps.remove(p);changed=true;}}
if(changed)persistLights();
}
@EventHandler public void load(ChunkLoadEvent e){
boolean changed=false;for(var entry:new ArrayList<>(lamps.entrySet())){Pos p=entry.getKey();if(p.world().equals(e.getWorld().getUID())&&(p.x()>>4)==e.getChunk().getX()&&(p.z()>>4)==e.getChunk().getZ()&&restore(p,entry.getValue())){lamps.remove(p);changed=true;}}
if(changed)persistLights();
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void throwTorch(PlayerInteractEvent e){
Player p=e.getPlayer();if(e.getHand()!=EquipmentSlot.HAND||!p.isSneaking()||p.getGameMode()!=GameMode.SURVIVAL||e.getItem()==null||e.getItem().getType()!=Material.TORCH||!(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK))return;
if(e.getItem().hasItemMeta())return;
e.setCancelled(true);if(flights.containsKey(p.getUniqueId()))return;
ItemStack stack=p.getInventory().getItemInMainHand();if(stack.getType()!=Material.TORCH||stack.getAmount()<1)return;
stack.setAmount(stack.getAmount()-1);p.getInventory().setItemInMainHand(stack.getAmount()==0?new ItemStack(Material.AIR):stack);
Location start=p.getEyeLocation().clone();Vector v=start.getDirection().multiply(1.25);
flights.put(p.getUniqueId(),new Flight(p.getUniqueId(),start,v,0));p.playSound(p.getLocation(),Sound.ENTITY_SNOWBALL_THROW,.7f,.9f);
}
private void refund(Flight f){
Player p=Bukkit.getPlayer(f.owner());ItemStack torch=new ItemStack(Material.TORCH);
if(p!=null){for(ItemStack rest:p.getInventory().addItem(torch).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);}
else if(f.location().getWorld()!=null&&f.location().getWorld().isChunkLoaded(f.location().getBlockX()>>4,f.location().getBlockZ()>>4))f.location().getWorld().dropItemNaturally(f.location(),torch);
}
private boolean placeTorch(Flight f,RayTraceResult hit){
Player p=Bukkit.getPlayer(f.owner());Block anchor=hit.getHitBlock();BlockFace face=hit.getHitBlockFace();
if(p==null||p.getGameMode()!=GameMode.SURVIVAL||anchor==null||face==null||face==BlockFace.DOWN)return false;
Block target=anchor.getRelative(face);if(!target.getWorld().isChunkLoaded(target.getX()>>4,target.getZ()>>4)||!target.getType().isAir())return false;
if(host.claimModule()==null||!host.claimModule().allowsTemporaryStructure(p,target.getLocation()))return false;
BlockData data=face==BlockFace.UP?Material.TORCH.createBlockData():Material.WALL_TORCH.createBlockData();
if(data instanceof Directional d)d.setFacing(face);
if(!target.canPlace(data))return false;
BlockState previous=target.getState();target.setBlockData(data,false);
BlockPlaceEvent event=new BlockPlaceEvent(target,previous,anchor,new ItemStack(Material.TORCH),p,true,EquipmentSlot.HAND);
Bukkit.getPluginManager().callEvent(event);
if(event.isCancelled()||!event.canBuild()){previous.update(true,false);return false;}
target.getWorld().playSound(target.getLocation(),Sound.BLOCK_WOOD_PLACE,.75f,1.3f);return true;
}
private void throwTick(){
for(var entry:new ArrayList<>(flights.entrySet())){
Flight f=entry.getValue();Location l=f.location();World w=l.getWorld();Player p=Bukkit.getPlayer(f.owner());
Vector v=f.velocity();Location next=l.clone().add(v);
if(p==null||p.getWorld()!=w||f.age()>=20||l.getY()<w.getMinHeight()||next.getY()>=w.getMaxHeight()||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)||!w.isChunkLoaded(next.getBlockX()>>4,next.getBlockZ()>>4)){flights.remove(entry.getKey());refund(f);continue;}
RayTraceResult hit=w.rayTraceBlocks(l,v.clone().normalize(),v.length(),FluidCollisionMode.NEVER,true);
w.spawnParticle(Particle.FLAME,l,3,.04,.04,.04,.005);w.spawnParticle(Particle.SMOKE,l,1,0,0,0,.01);
if(hit!=null){flights.remove(entry.getKey());if(!placeTorch(f,hit))refund(f);continue;}
flights.put(entry.getKey(),new Flight(f.owner(),next,v.clone().multiply(.98).add(new Vector(0,-.045,0)),f.age()+1));
}
}
void disable(){for(Flight f:flights.values())refund(f);flights.clear();restoreUnwanted(Set.of());persistLights();Bukkit.removeRecipe(helmetRecipe);Bukkit.removeRecipe(toolRecipe);}
}
