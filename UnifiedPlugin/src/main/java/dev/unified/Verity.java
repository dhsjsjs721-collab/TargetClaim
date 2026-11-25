package dev.unified;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Verity implements Listener {
private static final NamespacedKey ITEM=new NamespacedKey("minecraftunified","verity"),VISUAL=new NamespacedKey("minecraftunified","verity_visual");
private static final String PREFIX="verity_block_";
private static final Material BASE=Material.POLISHED_BLACKSTONE;
private static final String LINE="Пошёл нахуй, пёс";
private static final List<Material> PRICES=List.of(
Material.OAK_SAPLING,Material.SPRUCE_SAPLING,Material.BIRCH_SAPLING,Material.JUNGLE_SAPLING,Material.ACACIA_SAPLING,Material.DARK_OAK_SAPLING,Material.CHERRY_SAPLING,Material.MANGROVE_PROPAGULE,
Material.DIRT,Material.COBBLESTONE,Material.SAND,Material.GRAVEL,Material.CLAY_BALL,Material.OAK_LOG,Material.BIRCH_LOG,Material.STICK,Material.TORCH,Material.GLASS,Material.AMETHYST_SHARD,Material.COPPER_INGOT,Material.IRON_INGOT,Material.GOLD_INGOT,Material.DIAMOND,Material.EMERALD,Material.LAPIS_LAZULI,Material.REDSTONE,Material.COAL,Material.FLINT,Material.BONE,Material.STRING,Material.ROTTEN_FLESH,Material.SPIDER_EYE,Material.GUNPOWDER,Material.FEATHER,Material.EGG,Material.LEATHER,Material.WHEAT,Material.WHEAT_SEEDS,Material.CARROT,Material.POTATO,Material.BEETROOT,Material.APPLE,Material.BREAD,Material.PUMPKIN,Material.MELON_SLICE,Material.CACTUS,Material.BAMBOO,Material.SUGAR_CANE,Material.KELP,Material.RED_MUSHROOM,Material.BROWN_MUSHROOM,Material.HONEYCOMB,Material.CACTUS,Material.INK_SAC,Material.GLOW_INK_SAC,Material.SLIME_BALL,Material.ENDER_PEARL,Material.BLAZE_ROD,Material.GHAST_TEAR,Material.NETHER_WART,Material.QUARTZ,Material.OBSIDIAN,Material.CRYING_OBSIDIAN,Material.SNOWBALL,Material.SNOW_BLOCK,Material.ICE,Material.MOSS_BLOCK,Material.NAUTILUS_SHELL,Material.SHULKER_SHELL,Material.PHANTOM_MEMBRANE,Material.PAPER,Material.BOOK,Material.NAME_TAG,Material.COMPASS,Material.CLOCK,Material.HEART_OF_THE_SEA,Material.ECHO_SHARD,Material.GOLDEN_APPLE,Material.NETHERITE_SCRAP,Material.DRAGON_BREATH,Material.COOKED_BEEF,Material.COD,Material.TROPICAL_FISH,Material.SWEET_BERRIES,Material.GLOW_BERRIES,Material.LILY_PAD,Material.BELL,Material.SADDLE,Material.IRON_SWORD);
private final UnifiedPlugin host;
private record Pos(UUID world,int x,int y,int z){
static Pos of(Block b){return new Pos(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
Location center(){World w=Bukkit.getWorld(world);return w==null?null:new Location(w,x+.5,y+.5,z+.5);}
Block block(){World w=Bukkit.getWorld(world);return w!=null&&w.isChunkLoaded(x>>4,z>>4)?w.getBlockAt(x,y,z):null;}
NamespacedKey marker(){return new NamespacedKey("minecraftunified",PREFIX+(x&15)+"_"+y+"_"+(z&15));}
}
private static final class Placed {UUID display;long spoke;}
private final Map<Pos,Placed> active=new LinkedHashMap<>();
private final Map<UUID,Long> lastSpeech=new HashMap<>();
private int ticks;
Verity(UnifiedPlugin h){host=h;}
public static ItemStack item(){
ItemStack i=new ItemStack(BASE);var m=i.getItemMeta();m.setDisplayName("§e§lВерити");m.setLore(List.of("§7Грубый блок с огромной улыбкой","§6Отпугивает мобов в радиусе 16 блоков","§7Редкий товар странствующего торговца"));
m.setItemModel(ITEM);m.getPersistentDataContainer().set(ITEM,PersistentDataType.BYTE,(byte)1);i.setItemMeta(m);return i;
}
public static MerchantRecipe offer(Random random){
Material type=PRICES.get(random.nextInt(PRICES.size()));
int amount=1+random.nextInt(Math.min(64,type.getMaxStackSize()));
MerchantRecipe r=new MerchantRecipe(item(),1);r.setExperienceReward(false);r.setPriceMultiplier(0);r.setDemand(0);r.setSpecialPrice(0);r.addIngredient(new ItemStack(type,amount));return r;
}
private boolean isItem(ItemStack i){return i!=null&&i.getType()==BASE&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(ITEM,PersistentDataType.BYTE);}
void enable(){
Bukkit.getPluginManager().registerEvents(this,host);
for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks())load(c);
Bukkit.getScheduler().runTaskTimer(host,this::tick,10L,5L);
host.getLogger().info("Verity enabled: rare trader barter, persistent smiling block, strong mob fear.");
}
void disable(){for(Placed p:active.values())removeDisplay(p);active.clear();}
boolean give(CommandSender s,Player p){
if(!s.hasPermission("minecraftunified.verity.admin")){s.sendMessage("§cНет прав.");return true;}
for(ItemStack left:p.getInventory().addItem(item()).values())p.getWorld().dropItemNaturally(p.getLocation(),left);s.sendMessage("§aВерити выдан.");return true;
}
private boolean marked(Block b){return b.getChunk().getPersistentDataContainer().has(Pos.of(b).marker(),PersistentDataType.BYTE);}
private void removeDisplay(Placed p){if(p.display!=null){Entity e=Bukkit.getEntity(p.display);if(e!=null)e.remove();p.display=null;}}
private void forget(Pos pos,Placed p){if(active.remove(pos,p)){removeDisplay(p);Block b=pos.block();if(b!=null)b.getChunk().getPersistentDataContainer().remove(pos.marker());}}
private void render(Pos pos,Placed p){
if(p.display!=null&&Bukkit.getEntity(p.display) instanceof ItemDisplay d&&d.isValid())return;
Location l=pos.center();if(l==null)return;
ItemDisplay d=l.getWorld().spawn(l,ItemDisplay.class,e->{e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);e.setBillboard(Display.Billboard.FIXED);e.setViewRange(1.6f);
var t=e.getTransformation();t.getScale().set(1.015f);e.setTransformation(t);e.setItemStack(item());e.getPersistentDataContainer().set(VISUAL,PersistentDataType.BYTE,(byte)1);});p.display=d.getUniqueId();
}
private void load(Chunk c){
var data=c.getPersistentDataContainer();
for(NamespacedKey k:new HashSet<>(data.getKeys()))if(k.getNamespace().equals("minecraftunified")&&k.getKey().startsWith(PREFIX)){
try{String[] v=k.getKey().substring(PREFIX.length()).split("_");Block b=c.getBlock(Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2]));if(b.getType()!=BASE){data.remove(k);continue;}active.putIfAbsent(Pos.of(b),new Placed());}
catch(RuntimeException ex){data.remove(k);}
}
}
private void speak(Location l,Placed p,boolean first){
long now=System.currentTimeMillis();if(!host.getConfig().getBoolean("verity.speech",true))return;
if(!first&&now-p.spoke<35000||now-lastSpeech.getOrDefault(l.getWorld().getUID(),0L)<4000)return;
p.spoke=now;lastSpeech.put(l.getWorld().getUID(),now);
String line=LINE;
for(Player player:l.getWorld().getPlayers())if(player.getLocation().distanceSquared(l)<=16*16)player.sendMessage("§e§l[Верити] §r§f"+line);
l.getWorld().playSound(l,Sound.ENTITY_VILLAGER_NO,.85f,.55f);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void placeLimit(BlockPlaceEvent e){
if(!isItem(e.getItemInHand()))return;int n=0;for(NamespacedKey k:e.getBlock().getChunk().getPersistentDataContainer().getKeys())if(k.getNamespace().equals("minecraftunified")&&k.getKey().startsWith(PREFIX))n++;
if(n>=4){e.setCancelled(true);e.getPlayer().sendMessage("§cНе больше 4 Верити в чанке.");}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent e){
if(!isItem(e.getItemInHand())||e.getBlockPlaced().getType()!=BASE)return;Block b=e.getBlockPlaced();Pos pos=Pos.of(b);b.getChunk().getPersistentDataContainer().set(pos.marker(),PersistentDataType.BYTE,(byte)1);
Placed p=new Placed();Placed old=active.put(pos,p);if(old!=null)removeDisplay(old);
Bukkit.getScheduler().runTask(host,()->{if(active.get(pos)==p&&pos.block()!=null&&pos.block().getType()==BASE){render(pos,p);speak(pos.center(),p,true);}});
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drops(BlockDropItemEvent e){
Pos pos=Pos.of(e.getBlock());if(!active.containsKey(pos)||e.getBlockState().getType()!=BASE)return;
boolean first=true;for(Item drop:e.getItems()){if(first){drop.setItemStack(item());first=false;}else drop.remove();}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent e){
Pos pos=Pos.of(e.getBlock());Placed p=active.get(pos);if(p==null)return;
Bukkit.getScheduler().runTask(host,()->{Block b=pos.block();if(b!=null&&b.getType()!=BASE)forget(pos,p);});
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::marked);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::marked);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(this::marked))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::marked))e.setCancelled(true);}
@EventHandler public void chunkLoad(ChunkLoadEvent e){load(e.getChunk());}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void unload(ChunkUnloadEvent e){
Chunk c=e.getChunk();for(var it=active.entrySet().iterator();it.hasNext();){var ent=it.next();Pos p=ent.getKey();if(p.world.equals(c.getWorld().getUID())&&(p.x>>4)==c.getX()&&(p.z>>4)==c.getZ()){removeDisplay(ent.getValue());it.remove();}}
}
@EventHandler public void entities(EntitiesLoadEvent e){for(Entity entity:e.getEntities())if(entity instanceof ItemDisplay&&entity.getPersistentDataContainer().has(VISUAL,PersistentDataType.BYTE)&&active.values().stream().noneMatch(p->entity.getUniqueId().equals(p.display)))entity.remove();}
private Location nearest(Mob m){
Location l=m.getLocation(),nearest=null;double best=16*16;
for(Pos pos:active.keySet())if(pos.world.equals(l.getWorld().getUID())){double x=l.getX()-pos.x-.5,y=l.getY()-pos.y-.5,z=l.getZ()-pos.z-.5,d=x*x+y*y+z*z;if(d<=best){best=d;nearest=pos.center();}}
return nearest;
}
private boolean afraid(Mob m){return m.isValid()&&!m.isDead()&&!m.isInsideVehicle()&&m.getPassengers().isEmpty()&&nearest(m)!=null;}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&e.getEntity() instanceof Mob m&&afraid(m))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void hit(EntityDamageByEntityEvent e){
Mob m=e.getDamager() instanceof Mob mob?mob:e.getDamager() instanceof Projectile p&&p.getShooter() instanceof Mob mob?mob:null;
if(m!=null&&afraid(m))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void prime(ExplosionPrimeEvent e){if(e.getEntity() instanceof Creeper c&&afraid(c))e.setCancelled(true);}
private void tick(){
ticks+=5;Map<Mob,Location> frightened=new HashMap<>();Set<Pos> nearby=new HashSet<>();int budget=128;
for(var entry:new ArrayList<>(active.entrySet())){
Pos pos=entry.getKey();Placed p=entry.getValue();Block b=pos.block();
if(b==null)continue;if(b.getType()!=BASE){forget(pos,p);continue;}
Location center=pos.center();
if(center.getWorld().getPlayers().stream().noneMatch(v->v.getLocation().distanceSquared(center)<80*80)){removeDisplay(p);continue;}
render(pos,p);
for(LivingEntity entity:center.getWorld().getNearbyLivingEntities(center,16,16,16)){
if(!(entity instanceof Mob m)||!m.isValid()||m.isDead()||m.isInsideVehicle()||!m.getPassengers().isEmpty())continue;
Location l=m.getLocation();double dist=l.distanceSquared(center);if(dist>256)continue;
Location old=frightened.get(m);if(old==null||l.distanceSquared(old)>dist)frightened.put(m,center);nearby.add(pos);
}
if(ticks%20==0){center.getWorld().spawnParticle(Particle.DUST,center.clone().add(0,.7,0),6,.35,.1,.35,0,new Particle.DustOptions(Color.fromRGB(0xFFCC32),.8f));if(nearby.contains(pos))speak(center,p,false);}
}
for(var entry:frightened.entrySet()){
if(budget--<=0)break;Mob m=entry.getKey();Location center=entry.getValue(),loc=m.getLocation();
if(m.getTarget()!=null)m.setTarget(null);
if(m instanceof Creeper c&&c.isIgnited())c.setIgnited(false);
Vector away=loc.toVector().subtract(center.toVector()).setY(0);if(away.lengthSquared()<.01)away.setX(1);away.normalize();
if(ticks%20==0){
for(double angle:new double[]{0,.6,-.6,1.2,-1.2}){
Vector dir=away.clone().rotateAroundY(angle);Location dest=loc.clone().add(dir.multiply(10));
if(!dest.getWorld().isChunkLoaded(dest.getBlockX()>>4,dest.getBlockZ()>>4))continue;
if(dest.distanceSquared(center)<=loc.distanceSquared(center))continue;
if(m.getPathfinder().moveTo(dest,1.65))break;
}
}
Vector step=away.clone().multiply(.85);Location ahead=loc.clone().add(step);
if(ahead.getWorld().isChunkLoaded(ahead.getBlockX()>>4,ahead.getBlockZ()>>4)&&ahead.getBlock().isPassable()&&ahead.clone().add(0,1,0).getBlock().isPassable()){
Vector velocity=m.getVelocity();m.setVelocity(away.multiply(.85).setY(m.isOnGround()?.13:Math.max(-.35,Math.min(.15,velocity.getY()))));
}
}
}
}
