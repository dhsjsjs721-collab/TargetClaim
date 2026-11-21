package dev.swampsky;

import org.bukkit.*;
import org.bukkit.attribute.*;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.*;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

public final class SwampSkyPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private NamespacedKey flyingKey, ownerKey, giantKey, itemKey, displayKey;
private final Set<UUID> wings=new HashSet<>(), frogs=new HashSet<>(), ground=new HashSet<>();
private final Map<UUID,List<UUID>> displays=new HashMap<>();
private final Map<UUID,Input> inputCache=new HashMap<>();
private final Map<UUID,Vector> mountedMomentum=new HashMap<>();
private final Map<UUID,Long> wildFlightUntil=new HashMap<>();
private final Map<UUID,Long> lureHysteresisUntil=new HashMap<>();
private final Map<UUID,Long> settleUntil=new HashMap<>(),frogIdleUntil=new HashMap<>();
private final Map<UUID,Location> frogIdleGoals=new HashMap<>();
private final Map<UUID,UUID> aggro=new HashMap<>();
private final Map<UUID,Long> aggroUntil=new HashMap<>(),attackCooldown=new HashMap<>(),hitWindow=new HashMap<>(),dismountFall=new HashMap<>(),knockbackUntil=new HashMap<>();
private final Map<UUID,Integer> hitCount=new HashMap<>();
private final Map<UUID,Vector> flightVectors=new HashMap<>();
// Stable per-pig targets prevent the old aimless oscillation around a constantly moving target.
private final Map<UUID,Location> roamHomes=new HashMap<>(),roamTargets=new HashMap<>(),motionSamples=new HashMap<>(),groundTargets=new HashMap<>();
private final Map<UUID,Long> groundPathUntil=new HashMap<>();
private final Map<UUID,Long> roamExpiry=new HashMap<>(),motionCheckAt=new HashMap<>(),pigErrorLogAt=new HashMap<>();
private final Map<UUID,Integer> motionStalls=new HashMap<>();
private final Set<UUID> airborne=new HashSet<>(), bounceLocked=new HashSet<>(),heldJump=new HashSet<>();
private final Map<UUID,Long> nextMountedJump=new HashMap<>();
private final Map<UUID,Double> fallSpeed=new HashMap<>();
private final Map<UUID,Integer> groundedTicks=new HashMap<>();
private final Map<UUID,Long> leap=new HashMap<>(),hit=new HashMap<>(),tongue=new HashMap<>(),bounce=new HashMap<>();
private final Random random=new Random();
private long tick=0;

@Override public void onEnable(){
saveDefaultConfig();
flyingKey=new NamespacedKey(this,"winged_pig");
ownerKey=new NamespacedKey(this,"pig_owner");
giantKey=new NamespacedKey(this,"giant_frog");
itemKey=new NamespacedKey(this,"item_kind");
displayKey=new NamespacedKey(this,"wing_display");
Bukkit.getPluginManager().registerEvents(this,this);
PluginCommand c=getCommand("swampsky");
if(c!=null){c.setExecutor(this);c.setTabCompleter(this);}
recipes();
for(World w:Bukkit.getWorlds())for(Chunk ch:w.getLoadedChunks())scan(ch);
Bukkit.getScheduler().runTaskTimer(this,this::step,5L,3L);
Bukkit.getScheduler().runTaskTimer(this,this::mountedTick,1L,1L);
Bukkit.getScheduler().runTaskTimer(this,this::bounceStep,1L,1L);
Bukkit.getScheduler().runTaskTimer(this,this::frogWingsMotion,1L,1L);
Bukkit.getScheduler().runTaskTimer(this,this::populateNearby,120L,160L);
Bukkit.getScheduler().runTaskTimer(this,this::upgradeOnlineEquipment,40L,100L);
}
@Override public void onDisable(){
for(UUID id:new ArrayList<>(displays.keySet()))pigClear(id);
aggro.clear();aggroUntil.clear();attackCooldown.clear();hitWindow.clear();hitCount.clear();flightVectors.clear();
roamHomes.clear();roamTargets.clear();motionSamples.clear();roamExpiry.clear();motionCheckAt.clear();motionStalls.clear();pigErrorLogAt.clear();groundTargets.clear();groundPathUntil.clear();
dismountFall.clear();knockbackUntil.clear();mountedMomentum.clear();wildFlightUntil.clear();heldJump.clear();nextMountedJump.clear();
lureHysteresisUntil.clear();settleUntil.clear();frogIdleGoals.clear();frogIdleUntil.clear();
for(UUID id:ground){Entity e=Bukkit.getEntity(id);if(e instanceof Pig p){p.setAI(true);p.setAware(true);}}
for(UUID id:wings){
Entity e=Bukkit.getEntity(id);
if(e instanceof Pig p){p.setAI(true);p.setAware(true);p.setGravity(true);p.setCollidable(true);}
}
}
private boolean marked(Entity e,NamespacedKey k){return e.getPersistentDataContainer().has(k,PersistentDataType.BYTE);}
private void mark(Entity e,NamespacedKey k){e.getPersistentDataContainer().set(k,PersistentDataType.BYTE,(byte)1);}
private String owner(Pig p){return p.getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING);}
private boolean owned(Pig p,Player r){return r.getUniqueId().toString().equals(owner(p));}
private boolean food(ItemStack i){return i!=null&&(i.getType()==Material.PUMPKIN||i.getType()==Material.PUMPKIN_PIE);}
private boolean snow(Location p){
return isSnow(p.getBlock().getType())||isSnow(p.clone().subtract(0,1,0).getBlock().getType());
}
private boolean isSnow(Material m){return m==Material.SNOW||m==Material.SNOW_BLOCK||m==Material.POWDER_SNOW;}

private void scaleWingedPig(Pig pig){
AttributeInstance size=pig.getAttribute(Attribute.SCALE);
if(size!=null&&Math.abs(size.getBaseValue()-.5)>.001)size.setBaseValue(.5);
}
private void flyingPig(Pig p){
if(marked(p,flyingKey))return;
mark(p,flyingKey);ground.remove(p.getUniqueId());
p.setGravity(true);p.setAI(true);p.setAware(true);p.setCollidable(true);
scaleWingedPig(p);
roamHomes.put(p.getUniqueId(),p.getLocation().clone());
p.setCustomName(null);p.setCustomNameVisible(false);
p.setRemoveWhenFarAway(false);wings.add(p.getUniqueId());
AttributeInstance max=p.getAttribute(Attribute.MAX_HEALTH);
if(max!=null){max.setBaseValue(Math.max(26,max.getBaseValue()));p.setHealth(Math.min(max.getValue(),Math.max(p.getHealth(),20)));}

p.getWorld().spawnParticle(Particle.CLOUD,p.getLocation(),22,.6,.5,.6,.02);
}
private void giantFrog(Frog f){
if(marked(f,giantKey))return;
mark(f,giantKey);
AttributeInstance hp=f.getAttribute(Attribute.MAX_HEALTH);
if(hp!=null)hp.setBaseValue(getConfig().getDouble("frog-health",60));
AttributeInstance size=f.getAttribute(Attribute.SCALE);
if(size!=null)size.setBaseValue(getConfig().getDouble("frog-scale",2.8));
if(hp!=null)f.setHealth(hp.getValue());
f.setAI(true);f.setAware(true);
f.setCustomName(null);f.setCustomNameVisible(false);
f.setRemoveWhenFarAway(false);
frogs.add(f.getUniqueId());
}
private void scan(Chunk chunk){
for(Entity e:chunk.getEntities()){
if(e instanceof Pig p){
if(marked(p,flyingKey)){
wings.add(p.getUniqueId());
boolean safelyGrounded=p.isOnGround()||!p.getLocation().clone().subtract(0,.45,0).getBlock().isPassable();
// Older plugin versions could save a flying pig 50+ blocks above the ground.
// Let our controlled descent catch it instead of switching on gravity
// immediately on chunk load and killing the pig with fall damage.
p.setGravity(safelyGrounded);p.setAI(true);p.setAware(safelyGrounded);p.setCollidable(true);
scaleWingedPig(p);
roamHomes.putIfAbsent(p.getUniqueId(),p.getLocation().clone());
p.setCustomName(null);p.setCustomNameVisible(false);
}else{
ground.add(p.getUniqueId());p.setAI(true);
if(owner(p)!=null && p.getCustomName()!=null && (p.getCustomName().contains("свинья")||p.getCustomName().contains("Свинья")))p.setCustomName(null);
if(owner(p)!=null){p.setCustomNameVisible(false);}
}
}
if(e instanceof Frog f && marked(f,giantKey)){
frogs.add(f.getUniqueId());f.setCustomName(null);f.setCustomNameVisible(false);f.setAI(true);f.setAware(true);
}
if(e instanceof ItemDisplay && marked(e,displayKey))e.remove();
}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void spawn(CreatureSpawnEvent e){
if(e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL&&e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.CHUNK_GEN)return;
if(e.getEntity() instanceof Pig p){
Biome pigBiome=p.getLocation().getBlock().getBiome();
if(allowedPigBiome(pigBiome)){
double chance=getConfig().getDouble(pigBiome==Biome.MUSHROOM_FIELDS?"mushroom-pig-chance":"flying-chance",pigBiome==Biome.MUSHROOM_FIELDS?.80:.28);
if(random.nextDouble()<chance)Bukkit.getScheduler().runTask(this,()->{if(p.isValid())flyingPig(p);});
}
else ground.add(p.getUniqueId());
}
if(e.getEntity() instanceof Frog f){
Biome b=f.getLocation().getBlock().getBiome();
if((b==Biome.SWAMP||b==Biome.MANGROVE_SWAMP)&&random.nextDouble()<getConfig().getDouble("giant-chance",.24))
Bukkit.getScheduler().runTask(this,()->{if(f.isValid())giantFrog(f);});
}
}
@EventHandler public void loaded(ChunkLoadEvent e){
Bukkit.getScheduler().runTask(this,()->{if(e.getChunk().isLoaded())scan(e.getChunk());});
}
@EventHandler public void unloaded(ChunkUnloadEvent e){
for(Entity entity:e.getChunk().getEntities())if(entity instanceof Pig){
pigClear(entity.getUniqueId());
forgetFlight(entity.getUniqueId());
if(!marked(entity,flyingKey)){ground.remove(entity.getUniqueId());groundTargets.remove(entity.getUniqueId());groundPathUntil.remove(entity.getUniqueId());}
}
}
@EventHandler public void dead(EntityDeathEvent e){
Entity entity=e.getEntity();
UUID id=entity.getUniqueId();
if(entity instanceof Pig){
if(marked(entity,flyingKey))e.getDrops().add(new ItemStack(Material.PHANTOM_MEMBRANE,1+random.nextInt(2)));
wings.remove(id);ground.remove(id);pigClear(id);groundTargets.remove(id);groundPathUntil.remove(id);
forgetFlight(id);
aggro.remove(id);aggroUntil.remove(id);attackCooldown.remove(id);hitWindow.remove(id);hitCount.remove(id);knockbackUntil.remove(id);
}
if(entity instanceof Frog && marked(entity,giantKey)){
frogs.remove(id);frogIdleUntil.remove(id);frogIdleGoals.remove(id);
e.getDrops().add(item("hide",1+random.nextInt(3)));
e.getDrops().add(new ItemStack(Material.SLIME_BALL,6+random.nextInt(7)));
entity.getWorld().spawnParticle(Particle.ITEM_SLIME,entity.getLocation(),28,1,.5,1,.03);
}
leap.remove(id);hit.remove(id);tongue.remove(id);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interact(PlayerInteractEntityEvent e){
if(e.getHand()!=EquipmentSlot.HAND||!(e.getRightClicked() instanceof Pig pig))return;
Player player=e.getPlayer();
ItemStack main=player.getInventory().getItemInMainHand();
ItemStack off=player.getInventory().getItemInOffHand();
if(main.getType()==Material.LEAD||off.getType()==Material.LEAD)return;
ItemStack held=food(main)?main:(food(off)?off:main);
boolean hasFood=food(held);
String owner=owner(pig);
if(!marked(pig,flyingKey)&&pig.hasSaddle()&&!hasFood){
e.setCancelled(true);
if(player.isSneaking()||player.isInsideVehicle()||!pig.getPassengers().isEmpty())return;
Bukkit.getScheduler().runTask(this,()->{
if(!pig.isValid()||!player.isOnline()||!pig.hasSaddle()||player.isInsideVehicle()||!pig.getPassengers().isEmpty())return;
pig.setAI(true);pig.setAware(true);
pig.getPathfinder().stopPathfinding();
groundTargets.remove(pig.getUniqueId());groundPathUntil.remove(pig.getUniqueId());
pig.addPassenger(player);
});
return;
}
if(owner==null){
if(!hasFood)return;
e.setCancelled(true);
if(!pig.isAdult())return;
double chance=held.getType()==Material.PUMPKIN_PIE?.78:.52;
consume(player,held);
if(random.nextDouble()<chance){
pig.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,player.getUniqueId().toString());
pig.setSaddle(true);
// A successfully tamed pig becomes a winged companion that circles its owner.
if(!marked(pig,flyingKey))flyingPig(pig);
pig.setCustomName(null);pig.setCustomNameVisible(false);
pig.getWorld().spawnParticle(Particle.HEART,pig.getLocation().add(0,1,0),8,.32,.3,.32,.03);
}else pig.getWorld().spawnParticle(Particle.SMOKE,pig.getLocation().add(0,.8,0),3,.3,.2,.3,.01);
return;
}
if(!owned(pig,player)){e.setCancelled(true);return;}
if(hasFood){
e.setCancelled(true);
consume(player,held);
if(pig.isAdult()&&pig.canBreed()&&pig.getLoveModeTicks()==0){
pig.setBreedCause(player.getUniqueId());
pig.setLoveModeTicks(600);
pig.getWorld().spawnParticle(Particle.HEART,pig.getLocation().add(0,1,0),9,.35,.3,.35,.02);
}else{
AttributeInstance max=pig.getAttribute(Attribute.MAX_HEALTH);
pig.setHealth(Math.min(max==null?10:max.getValue(),pig.getHealth()+4));
pig.getWorld().spawnParticle(Particle.HEART,pig.getLocation().add(0,.8,0),5,.3,.2,.3,.02);
}
return;
}
e.setCancelled(true);
if(!pig.isAdult()||!pig.getPassengers().isEmpty()||player.isInsideVehicle())return;
if(player.isSneaking())return;
Bukkit.getScheduler().runTask(this,()->mountFlyingPig(player,pig));
}
private void breedWingedPigs(){
// Winged pigs are not controlled by vanilla pathfinding. Match willing parents
// explicitly to ensure pumpkin breeding works while they fly.
Set<UUID> paired=new HashSet<>();
for(UUID id:new ArrayList<>(wings)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Pig first)||!first.isValid()||!first.isAdult()||first.getLoveModeTicks()<=0||!first.canBreed()||paired.contains(id))continue;
String firstOwner=owner(first);
if(firstOwner==null)continue;
for(Entity other:first.getNearbyEntities(12,6,12)){
if(!(other instanceof Pig second)||!second.isValid()||second==first||!second.isAdult()
||second.getLoveModeTicks()<=0||!second.canBreed()||paired.contains(second.getUniqueId()))continue;
if(!firstOwner.equals(owner(second)))continue;
if(first.getLocation().distanceSquared(second.getLocation())>12*12)continue;
paired.add(first.getUniqueId());paired.add(second.getUniqueId());
first.setLoveModeTicks(0);second.setLoveModeTicks(0);
first.setBreed(false);second.setBreed(false);
Location at=first.getLocation().clone().add(second.getLocation()).multiply(.5);
Pig baby=first.getWorld().spawn(at,Pig.class);
baby.setBaby();
baby.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,firstOwner);
flyingPig(baby);
first.getWorld().spawnParticle(Particle.HEART,at,12,.7,.5,.7,.03);
break;
}
}
}
private void consume(Player player,ItemStack item){
if(player.getGameMode()!=GameMode.CREATIVE)item.setAmount(item.getAmount()-1);
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void breed(EntityBreedEvent e){
if(!(e.getEntity() instanceof Pig baby)||!(e.getMother() instanceof Pig mother)||!(e.getFather() instanceof Pig father))return;
String first=owner(mother);
if(first!=null && first.equals(owner(father))){
baby.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,first);
baby.setCustomName(null);baby.setCustomNameVisible(false);
}
if(marked(mother,flyingKey)||marked(father,flyingKey)){
Bukkit.getScheduler().runTask(this,()->{if(baby.isValid())flyingPig(baby);});
}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void dismount(EntityDismountEvent e){
if(!(e.getEntity() instanceof Player player)||!(e.getDismounted() instanceof Pig pig))return;
heldJump.remove(player.getUniqueId());nextMountedJump.remove(pig.getUniqueId());mountedMomentum.remove(pig.getUniqueId());
if(!owned(pig,player))return;
UUID id=player.getUniqueId();
long until=tick+240;
dismountFall.put(id,until);
// Keep a companion's saddle after dismount. The next click always works.
Bukkit.getScheduler().runTaskLater(this,()->{
if(dismountFall.getOrDefault(id,0L)==until)dismountFall.remove(id);
},240L);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void pigHit(EntityDamageByEntityEvent e){
if(!(e.getEntity() instanceof Pig pig)||!marked(pig,flyingKey)||!(e.getDamager() instanceof Player player))return;
UUID id=pig.getUniqueId();
Vector away=pig.getLocation().toVector().subtract(player.getLocation().toVector());
if(away.lengthSquared()<.01)away=player.getLocation().getDirection().multiply(-1);
away.setY(0).normalize().multiply(.85).setY(.42);
Vector push=away;
Bukkit.getScheduler().runTask(this,()->{if(pig.isValid()&&pig.getPassengers().isEmpty())pig.setVelocity(push);});
long prev=hitWindow.getOrDefault(id,-99999L);
int strikes=tick-prev<=getConfig().getInt("pig-retaliation-hit-window-ticks",240)?hitCount.getOrDefault(id,0)+1:1;
hitWindow.put(id,tick);hitCount.put(id,strikes);
knockbackUntil.put(id,tick+12);
flightVectors.put(id,push.clone());
if(strikes>=getConfig().getInt("pig-retaliation-hits",3)){
aggro.put(id,player.getUniqueId());
aggroUntil.put(id,tick+getConfig().getInt("pig-retaliation-duration-ticks",400));
hitCount.remove(id);
pig.getWorld().playSound(pig.getLocation(),Sound.ENTITY_PHANTOM_SWOOP,.8f,1.35f);
}
}

private void mountFlyingPig(Player player,Pig pig){
if(!player.isOnline()||!pig.isValid()||player.isInsideVehicle()||!pig.getPassengers().isEmpty()
||!owned(pig,player)||!pig.isAdult())return;
pig.setAI(true);pig.setSaddle(true);
settleUntil.remove(pig.getUniqueId());
roamTargets.remove(pig.getUniqueId());
flightVectors.remove(pig.getUniqueId());
if(pig.addPassenger(player))player.sendActionBar("§aСвинка: WASD — управление, Пробел — прыжок/вверх, взгляд вниз+W — спуск");
}
private void mountedTick(){
for(Player player:Bukkit.getOnlinePlayers()){
if(!(player.getVehicle() instanceof Pig pig)||!pig.isValid()||!pig.hasSaddle())continue;
boolean fly=marked(pig,flyingKey);
if(fly&&!owned(pig,player))continue;
try{ride(pig,player,fly);}catch(RuntimeException ex){pigProblem(pig.getUniqueId(),ex,"mounted steering");}
}
}
private void step(){
tick+=3;
for(UUID id:new ArrayList<>(wings)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Pig p)||!p.isValid()){
wings.remove(id);pigClear(id);forgetFlight(id);
aggro.remove(id);aggroUntil.remove(id);attackCooldown.remove(id);hitWindow.remove(id);hitCount.remove(id);knockbackUntil.remove(id);
continue;
}
try{movePig(p,true);}catch(RuntimeException ex){pigProblem(id,ex,"navigation");}
try{drawWings(p);}catch(RuntimeException ex){pigProblem(id,ex,"wing rendering");}
}
for(UUID id:new ArrayList<>(ground)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Pig pig)||!pig.isValid()){
ground.remove(id);groundTargets.remove(id);groundPathUntil.remove(id);continue;
}
try{movePig(pig,false);}catch(RuntimeException ex){pigProblem(id,ex,"ground pathfinding");}
}
if(tick%21==0)for(Player p:Bukkit.getOnlinePlayers())armor(p);
if(tick%30==0)breedWingedPigs();
for(UUID id:new ArrayList<>(frogs)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Frog frog)||!frog.isValid()){frogs.remove(id);frogIdleUntil.remove(id);frogIdleGoals.remove(id);continue;}
try{fight(frog);}catch(RuntimeException ex){pigProblem(id,ex,"giant frog AI");}
}
}
private void pigProblem(UUID id,RuntimeException exception,String operation){
long last=pigErrorLogAt.getOrDefault(id,-999999L);
if(tick-last<400)return;
pigErrorLogAt.put(id,tick);
getLogger().warning("SwampSky pig "+id+" "+operation+" failed: "+exception.getClass().getSimpleName()+": "+exception.getMessage());
}
private void forgetFlight(UUID id){
flightVectors.remove(id);roamHomes.remove(id);roamTargets.remove(id);roamExpiry.remove(id);
motionSamples.remove(id);motionCheckAt.remove(id);motionStalls.remove(id);pigErrorLogAt.remove(id);
}
private void restWingedPig(Pig pig,boolean wild){
UUID id=pig.getUniqueId();
Location here=pig.getLocation();
// Old flying code could push pigs into the terrain. Only use a local
// vertical rescue when the present position is already obstructed.
if(!clearForPig(here)){
for(int dy=1;dy<=6;dy++){
Location safe=here.clone().add(0,dy,0);
if(clearForPig(safe)){
pig.teleport(safe);
here=safe;break;
}
}
}
Vector motion=pig.getVelocity();
boolean ground=pig.isOnGround();
boolean nearFloor=false;
for(double dy=.30;dy<=1.8;dy+=.3){
if(!here.clone().subtract(0,dy,0).getBlock().isPassable()){nearFloor=true;break;}
}
if(ground||nearFloor){
// Natural gravity completes the last short landing without tunneling
// through a block. Idle pets stay still instead of hovering at eye level.
pig.setGravity(true);
pig.setAware(wild);
if(!wild)pig.getPathfinder().stopPathfinding();
pig.setFallDistance(0f);
pig.setVelocity(new Vector(motion.getX()*.08,Math.min(0,motion.getY()),motion.getZ()*.08));
}else{
pig.setGravity(false);
pig.setAware(false);
pig.setVelocity(new Vector(motion.getX()*.14,-.11,motion.getZ()*.14));
}
flightVectors.remove(id);
roamTargets.remove(id);
motionStalls.remove(id);
}
private Location ownerFollowPosition(Player owner){
Vector forward=owner.getLocation().getDirection().setY(0);
if(forward.lengthSquared()<.01)forward=new Vector(0,0,1);
forward.normalize();
return owner.getLocation().clone().subtract(forward.multiply(4.5));
}
private void walkWingedToOwner(Pig pig,Player owner){
UUID id=pig.getUniqueId();
if(!pig.hasGravity())pig.setGravity(true);
if(!pig.isAware())pig.setAware(true);
Location destination=ownerFollowPosition(owner);
if(tick>=groundPathUntil.getOrDefault(id,0L)){
groundPathUntil.put(id,tick+45);
if(destination.getWorld()!=null&&destination.getWorld().isChunkLoaded(destination.getBlockX()>>4,destination.getBlockZ()>>4)
&&clearForPig(destination))pig.getPathfinder().moveTo(destination,.92);
else pig.getPathfinder().moveTo(owner.getLocation(),.78);
}
flightVectors.remove(id);
}
private void movePig(Pig pig,boolean fly){
Player rider=null;
for(Entity passenger:pig.getPassengers())if(passenger instanceof Player p){rider=p;break;}
if(rider!=null&&(fly?owned(pig,rider):pig.hasSaddle()))return;
if(!pig.hasAI())pig.setAI(true);
if(!fly){if(!pig.hasGravity())pig.setGravity(true);moveGroundPig(pig);return;}
// Winged pigs are predominantly grounded animals, not perpetual drones.
UUID id=pig.getUniqueId();
if(tick<knockbackUntil.getOrDefault(id,-1L))return;
Location here=pig.getLocation();
Player own=onlineOwner(pig);
if(own!=null&&own.getWorld()!=pig.getWorld())own=null;

LivingEntity fear=null;
double fearDistance=11*11;
Spider spider=null;
double spiderDistance=13*13;
for(Entity entity:pig.getNearbyEntities(14,10,14)){
if(!(entity instanceof LivingEntity candidate)||candidate==pig||candidate.isDead())continue;
double distance=here.distanceSquared(candidate.getLocation());
if(frightening(entity)&&distance<fearDistance){fear=candidate;fearDistance=distance;}
if(entity instanceof Spider s&&distance<spiderDistance){spider=s;spiderDistance=distance;}
}
Entity leashHolder=null;
if(pig.isLeashed()){
try{leashHolder=pig.getLeashHolder();}catch(IllegalStateException ignored){}
}
Player attacker=null;
if(tick<aggroUntil.getOrDefault(id,0L)){
UUID attackerId=aggro.get(id);
if(attackerId!=null){
Player p=Bukkit.getPlayer(attackerId);
if(p!=null&&!p.isDead()&&p.getWorld()==pig.getWorld()
&&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR
&&here.distanceSquared(p.getLocation())<35*35)attacker=p;
}
}else{aggro.remove(id);aggroUntil.remove(id);}
Player lure=null;
double lureDistance=22*22;
if(leashHolder==null&&fear==null&&attacker==null){
for(Player candidate:pig.getWorld().getPlayers()){
if(candidate.isDead()||candidate.getGameMode()==GameMode.SPECTATOR)continue;
if(!food(candidate.getInventory().getItemInMainHand())&&!food(candidate.getInventory().getItemInOffHand()))continue;
double distance=here.distanceSquared(candidate.getLocation());
if(distance<lureDistance){lure=candidate;lureDistance=distance;}
}
}
// Tamed companions ignore pumpkin bait while following their owners.
// They must neither jump into the player's camera nor chase held food in circles.
if(own!=null){lure=null;if(spiderDistance>4*4)spider=null;}
if(own!=null&&leashHolder==null&&fear==null&&attacker==null&&spider==null){
double sq=here.distanceSquared(own.getLocation());
if(sq<=8.5*8.5){restWingedPig(pig,false);return;}
if(sq<=21*21&&pig.isOnGround()){walkWingedToOwner(pig,own);return;}
}
// Wild pigs explore the air in short excursions and then land/rest.
// Their rest state is persistent while loaded; it is NOT randomised each tick.
if(own==null&&leashHolder==null&&fear==null&&attacker==null&&lure==null&&spider==null){
long flightUntil=wildFlightUntil.getOrDefault(id,0L);
if(tick>=flightUntil){
long restUntil=settleUntil.computeIfAbsent(id,unused->tick+240+random.nextInt(280));
if(tick<restUntil){restWingedPig(pig,true);return;}
wildFlightUntil.put(id,tick+65+random.nextInt(65));
settleUntil.put(id,tick+550+random.nextInt(450));
}
}
if(!pig.hasAI())pig.setAI(true);
if(pig.isAware())pig.setAware(false);
if(pig.hasGravity())pig.setGravity(false);
Location destination;
double speed=Math.max(.10,Math.min(.30,getConfig().getDouble("flying-pig-safe-speed",.21)));
LivingEntity attackTarget=null;
if(fear!=null){
Vector escape=here.toVector().subtract(fear.getLocation().toVector());
escape.setY(0);
if(escape.lengthSquared()<.01)escape=new Vector(1,0,0);
destination=here.clone().add(escape.normalize().multiply(8)).add(0,3.5,0);
speed=.40;
}else if(leashHolder!=null&&leashHolder.getWorld()==pig.getWorld()){
destination=leashHolder.getLocation().clone().add(0,leashHolder instanceof Player?1.4:.9,0);
speed=.32;
// No hunting or pumpkin chasing while attached to a lead.
}else if(attacker!=null){
attackTarget=attacker;
destination=swoopTarget(pig,attacker);
speed=.40;
}else if(lure!=null){
destination=lure.getLocation().clone().add(0,1.5,0);
speed=.24;
}else if(spider!=null&&leashHolder==null){
attackTarget=spider;
destination=swoopTarget(pig,spider);
speed=.29;
}else if(own!=null){
destination=ownerFollowPosition(own).add(0,1.45,0);
// Keep behind the owner's back and stop once the animal gets close enough.
speed=.23;
if(here.distanceSquared(own.getLocation())>42*42&&!pig.isLeashed()){
Location behind=ownerFollowPosition(own).add(0,.75,0);
if(clearForPig(behind)){
pig.teleport(behind);
restWingedPig(pig,false);
flightVectors.remove(id);motionSamples.remove(id);motionStalls.remove(id);
return;
}
}
}else{
// Truly free pigs now have fixed waypoints until reached or timed out.
// Previous code generated a new relative goal every tick, making them
// visually twitch, stop, or keep changing direction.
destination=roamWaypoint(pig,here);
speed=.19;
}
// A lead held very close should not make the pig jitter through its owner.
if(leashHolder!=null&&here.distanceSquared(destination)<2.2*2.2){
pig.setVelocity(new Vector(0,.035,0));
return;
}
Vector delta=destination.toVector().subtract(here.toVector());
double distance=delta.length();
if(distance<.82){
if(attackTarget==null&&leashHolder==null&&own==null&&lure==null)roamExpiry.remove(id);
delta=new Vector(0,.012,0);
}else{
delta.normalize().multiply(Math.min(speed,.07+distance*.055));
}
if(attackTarget!=null&&here.distanceSquared(attackTarget.getLocation())<2.6*2.6
&&tick-attackCooldown.getOrDefault(id,-99999L)>28){
attackCooldown.put(id,tick);
attackTarget.damage(attackTarget instanceof Player?3.0:4.5,pig);
Vector recoil=here.toVector().subtract(attackTarget.getLocation().toVector());
if(recoil.lengthSquared()>.01){delta.add(recoil.normalize().multiply(.35).setY(.25));}
pig.getWorld().playSound(here,Sound.ENTITY_PHANTOM_SWOOP,.7f,1.3f);
}
if(onSnowOrIce(here))delta.setY(Math.max(delta.getY(),.25));
Vector horizontal=delta.clone().setY(0);
if(horizontal.lengthSquared()>.002){
Vector look=horizontal.clone().normalize();
Location front=here.clone().add(look.clone().multiply(1.55));
if(!clearForPig(front)){
// Try to climb before turning, and steer around taller walls.
Location above=front.clone().add(0,1.5,0);
if(clearForPig(above))delta.setY(Math.max(.30,delta.getY()));
else{
Vector sideways=new Vector(-look.getZ(),0,look.getX());
if(Math.floorMod(id.hashCode(),2)==0)sideways.multiply(-1);
Location side=here.clone().add(sideways.clone().multiply(1.7));
if(clearForPig(side)){
delta=sideways.multiply(speed*.75).setY(Math.max(.16,delta.getY()));
}else delta=new Vector(0,.32,0);
}
}
}
for(int below=1;below<=3;below++){
if(!here.clone().subtract(0,below,0).getBlock().isPassable()){
delta.setY(Math.max(.17,delta.getY()));break;
}
}
if(here.getY()>pig.getWorld().getMaxHeight()-6)delta.setY(Math.min(delta.getY(),-.14));
if(here.getY()<pig.getWorld().getMinHeight()+4)delta.setY(Math.max(delta.getY(),.20));
Vector previous=flightVectors.getOrDefault(id,pig.getVelocity()).clone();
Vector smooth=previous.multiply(.76).add(delta.multiply(.24));
if(smooth.lengthSquared()>speed*speed)smooth.normalize().multiply(speed);
// Never inject a velocity that enters terrain at the next physics tick.
// On obstruction, climb gently or cancel the blocked axis.
Location next=here.clone().add(smooth);
if(!clearForPig(next)){
Location up=here.clone().add(0,.29,0);
Location level=here.clone().add(smooth.getX()*.25,0,smooth.getZ()*.25);
if(clearForPig(up))smooth=new Vector(0,.21,0);
else if(clearForPig(level))smooth=new Vector(smooth.getX()*.25,0,smooth.getZ()*.25);
else smooth=new Vector(0,0,0);
}
flightVectors.put(id,smooth.clone());
pig.setVelocity(smooth);
faceVector(pig,smooth);
recoverStalledFlight(pig,smooth);
}
private boolean clearForPig(Location location){
World world=location.getWorld();
if(world==null||!world.isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))return false;
if(location.getY()<world.getMinHeight()+1||location.getY()>world.getMaxHeight()-3)return false;
return location.getBlock().isPassable()
&&location.clone().add(0,1,0).getBlock().isPassable();
}
private Location roamWaypoint(Pig pig,Location here){
UUID id=pig.getUniqueId();
Location home=roamHomes.computeIfAbsent(id,unused->here.clone());
if(home.getWorld()!=here.getWorld()||here.distanceSquared(home)>19*19){
home=here.clone();
roamHomes.put(id,home);
roamExpiry.remove(id);
}
Location old=roamTargets.get(id);
if(old!=null&&tick<roamExpiry.getOrDefault(id,0L)&&old.distanceSquared(here)>1.5*1.5&&clearForPig(old))return old;
for(int i=0;i<6;i++){
double angle=random.nextDouble()*Math.PI*2;
double radius=3.5+random.nextDouble()*7;
Location point=home.clone().add(Math.cos(angle)*radius,1+random.nextDouble()*4,Math.sin(angle)*radius);
if(clearForPig(point)){
roamTargets.put(id,point);roamExpiry.put(id,tick+90+random.nextInt(125));
return point;
}
}
// If no loaded waypoint exists, keep moving slowly in a small circle.
double phase=tick*.03+Math.floorMod(id.hashCode(),53);
return here.clone().add(Math.cos(phase)*2,1.2,Math.sin(phase)*2);
}
private void recoverStalledFlight(Pig pig,Vector requested){
UUID id=pig.getUniqueId();
if(tick<motionCheckAt.getOrDefault(id,0L))return;
motionCheckAt.put(id,tick+27);
Location here=pig.getLocation();
Location old=motionSamples.put(id,here.clone());
if(old==null||old.getWorld()!=here.getWorld()||requested.lengthSquared()<.045
||pig.isLeashed()||!pig.getPassengers().isEmpty()){
motionStalls.remove(id);
return;
}
if(here.distanceSquared(old)>.14){motionStalls.remove(id);return;}
int stalled=motionStalls.merge(id,1,Integer::sum);
if(stalled<2)return;
// Server physics or another plugin prevented velocity movement.
// Recover by a safe short step; never teleport through walls or into unloaded chunks.
Vector direction=requested.clone();
if(direction.lengthSquared()<.02)return;
direction.normalize();
Location up=here.clone().add(0,1.15,0);
Location ahead=here.clone().add(direction.multiply(.9));
Location rescue=clearForPig(ahead)?ahead:(clearForPig(up)?up:null);
if(rescue!=null){
pig.teleport(rescue);
flightVectors.remove(id);
motionSamples.put(id,rescue.clone());
}
motionStalls.remove(id);
}
private void moveGroundPig(Pig pig){
UUID id=pig.getUniqueId();
// Vanilla pathfinding is more reliable than setVelocity on a grounded animal.
// The previous code setAware(false) whenever a pumpkin appeared, which could
// leave pigs apparently frozen when the velocity was interrupted by physics.
if(!pig.isAware())pig.setAware(true);
if(pig.isLeashed()||!pig.getPassengers().isEmpty())return;
Player lure=null;
double best=12*12;
for(Player p:pig.getWorld().getPlayers()){
if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)continue;
if(!food(p.getInventory().getItemInMainHand())&&!food(p.getInventory().getItemInOffHand()))continue;
double distance=p.getLocation().distanceSquared(pig.getLocation());
if(distance<best){best=distance;lure=p;}
}
if(lure==null){
if(groundTargets.remove(id)!=null)pig.getPathfinder().stopPathfinding();
return;
}
Location target=lure.getLocation();
if(best<2.4*2.4){
if(groundTargets.remove(id)!=null)pig.getPathfinder().stopPathfinding();
return;
}
Location old=groundTargets.get(id);
// Only update the path when the target meaningfully moves or the path expires.
if(old==null||old.getWorld()!=target.getWorld()||old.distanceSquared(target)>2*2
||tick>=groundPathUntil.getOrDefault(id,0L)){
groundTargets.put(id,target.clone());
groundPathUntil.put(id,tick+24);
pig.getPathfinder().moveTo(target,1.18);
}
}
private boolean onSnowOrIce(Location location){
Material below=location.clone().subtract(0,1,0).getBlock().getType();
return snow(location)||below==Material.ICE||below==Material.PACKED_ICE||below==Material.BLUE_ICE;
}
private boolean frightening(Entity e){
return switch(e.getType()){
case STRAY,SNOW_GOLEM,POLAR_BEAR,HUSK,CAMEL->true;
default->{
if(!(e instanceof org.bukkit.entity.Mob) || e instanceof Spider)yield false;
String biome=e.getLocation().getBlock().getBiome().name();
yield biome.contains("SNOW")||biome.contains("FROZEN")||biome.contains("ICE")
||biome.contains("DESERT")||biome.contains("BADLANDS");
}
};
}
private Player onlineOwner(Pig pig){
String id=owner(pig);
if(id==null)return null;
try{
Player p=Bukkit.getPlayer(UUID.fromString(id));
return p!=null&&p.isOnline()?p:null;
}catch(IllegalArgumentException ignored){return null;}
}
private Location swoopTarget(Pig pig,LivingEntity target){
double phase=(tick+(pig.getUniqueId().hashCode()&255))%90;
double angle=(tick*.058)+(pig.getUniqueId().hashCode()%61);
if(phase<52){
return target.getLocation().clone().add(Math.cos(angle)*4.6,4.5+Math.sin(angle*1.3),Math.sin(angle)*4.6);
}
if(phase<77){
return target.getLocation().clone().add(0,1.25,0);
}
return target.getLocation().clone().add(Math.cos(angle+1.4)*3.5,3.2,Math.sin(angle+1.4)*3.5);
}

private void ride(Pig pig,Player player,boolean fly){
if(!pig.hasAI())pig.setAI(true);
// Vanilla autonomous pathfinding fights the player steering when riding a
// normal saddled pig. Keep server AI active, but suspend wandering goals.
if(pig.isAware())pig.setAware(false);
if(pig.hasGravity()==fly)pig.setGravity(!fly);
pig.getPathfinder().stopPathfinding();
// The actual 26.3 input is refreshed every server tick, not a stale
// PlayerInputEvent snapshot from a previous mount.
Input input=player.getCurrentInput();
Vector forward=player.getLocation().getDirection().setY(0);
if(forward.lengthSquared()<.001)forward=new Vector(0,0,1);
forward.normalize();
Vector left=new Vector(-forward.getZ(),0,forward.getX());
Vector desired=new Vector(0,0,0);
if(input.isForward())desired.add(forward);
if(input.isBackward())desired.subtract(forward);
if(input.isLeft())desired.add(left);
if(input.isRight())desired.subtract(left);
boolean moving=desired.lengthSquared()>.001;
if(moving)desired.normalize();
Location here=pig.getLocation();
boolean water=pig.isInWater()||here.getBlock().isLiquid();
double speed=fly?getConfig().getDouble("pig-safe-flight-ride-speed",.29):
water?getConfig().getDouble("pig-horse-swim-speed",.26):
getConfig().getDouble("pig-horse-ride-speed",.38);
if(input.isSprint())speed*=fly?1.15:1.22;
if(snow(here)&&!water)speed*=getConfig().getDouble("snow-multiplier",.55);
speed=Math.max(.08,Math.min(fly?.42:.56,speed));
desired.multiply(speed);
UUID mountId=pig.getUniqueId();
Vector old=mountedMomentum.getOrDefault(mountId,pig.getVelocity().clone().setY(0)).clone().setY(0);
double keep=moving?(fly?.73:.70):(fly?.36:.46);
Vector horizontal=old.multiply(keep).add(desired.multiply(1-keep));
if(horizontal.lengthSquared()<.00015)horizontal.zero();
mountedMomentum.put(mountId,horizontal.clone());
UUID playerId=player.getUniqueId();
boolean pressingJump=input.isJump();
boolean newJump=pressingJump&&!heldJump.contains(playerId);
if(pressingJump)heldJump.add(playerId);else heldJump.remove(playerId);
double vertical=pig.getVelocity().getY();
boolean slope=false;
if(horizontal.lengthSquared()>.005){
Vector toward=horizontal.clone().normalize();
Location ahead=here.clone().add(toward.multiply(.9));
slope=!ahead.getBlock().isPassable()&&ahead.clone().add(0,1,0).getBlock().isPassable();
}
if(fly){
vertical=0;
if(pressingJump)vertical=.19;
else if(moving&&player.getLocation().getPitch()>32)vertical=-.12;
else if(moving&&player.getLocation().getPitch()<-37)vertical=.08;
else if(slope)vertical=.13;
if(snow(here))vertical=Math.max(.08,vertical);
// Flying descent must never drive the pig through the floor, ceiling or
// low cave roof: cancel the unsafe velocity axis before world physics.
Location predicted=here.clone().add(horizontal).add(0,vertical,0);
if(!clearForPig(predicted)){
Location sameHeight=here.clone().add(horizontal);
if(clearForPig(sameHeight))vertical=0;
else{
Location up=here.clone().add(0,.22,0);
if(clearForPig(up)){horizontal.zero();vertical=.15;}
else{horizontal.zero();vertical=0;}
}
}
if(vertical<0){
// Look at the pig's projected lower body, not just its centre.
Location feet=here.clone().add(horizontal).add(0,vertical-.24,0);
if(!feet.getBlock().isPassable())vertical=0;
}
}else if(water){
vertical=pressingJump?.19:.045;
if(input.isSneak())vertical=-.08;
}else{
boolean ready=tick>=nextMountedJump.getOrDefault(mountId,0L);
if(ready&&pig.isOnGround()&&(newJump||(slope&&moving))){
vertical=newJump?.51:.36;
nextMountedJump.put(mountId,tick+11);
}
Location ahead=here.clone().add(horizontal);
if(!ahead.getBlock().isPassable()&&vertical<=.05&&pig.isOnGround())horizontal.zero();
}
pig.setVelocity(new Vector(horizontal.getX(),vertical,horizontal.getZ()));
if(horizontal.lengthSquared()>.002)faceVector(pig,horizontal);
}
private boolean ready(Map<UUID,Long> state,UUID id,long gap){
if(tick-state.getOrDefault(id,-9999L)<gap)return false;
state.put(id,tick);return true;
}
private void fight(Frog f){
Player nearest=null;double dist=22*22;
for(Player p:f.getWorld().getPlayers()){
if(p.isDead()||p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)continue;
double d=p.getLocation().distanceSquared(f.getLocation());
if(d<dist){dist=d;nearest=p;}
}
if(nearest==null){
// Giant frogs naturally wander using vanilla AI when nobody is nearby.
if(f.isOnGround()&&tick>=frogIdleUntil.getOrDefault(f.getUniqueId(),0L)){
Location from=f.getLocation();
Location goal=from.clone().add(random.nextDouble()*10-5,0,random.nextDouble()*10-5);
frogIdleGoals.put(f.getUniqueId(),goal);
frogIdleUntil.put(f.getUniqueId(),tick+50+random.nextInt(60));
f.getPathfinder().moveTo(goal,.8);
}
return;
}
Player p=nearest;UUID id=f.getUniqueId();
// Keep the frog's head pointed at its target while jumping and attacking.
faceVector(f,p.getLocation().toVector().subtract(f.getLocation().toVector()));
if(dist>8&&f.isOnGround()&&ready(leap,id,38)){
Vector v=p.getLocation().toVector().subtract(f.getLocation().toVector()).setY(0);
if(v.lengthSquared()>.1){
faceVector(f,v);
v.normalize().multiply(Math.min(.78,Math.sqrt(dist)/18+.34));
v.setY(.87);f.setVelocity(v);
f.getWorld().playSound(f.getLocation(),Sound.ENTITY_FROG_LONG_JUMP,.8f,.65f);
}
}
if(dist<3.6*3.6&&ready(hit,id,22)){
p.damage(getConfig().getDouble("frog-damage",7),f);
p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,28,0));
f.getWorld().spawnParticle(Particle.ITEM_SLIME,p.getLocation().add(0,1,0),12,.5,.5,.5,.04);
}
AttributeInstance max=f.getAttribute(Attribute.MAX_HEALTH);
double maxHp=max==null?60:max.getValue();
if(f.getHealth()<maxHp*.4&&dist<12*12&&ready(tongue,id,100)){
Location mouth=f.getLocation().add(0,1.6,0);
Vector line=p.getLocation().add(0,1,0).toVector().subtract(mouth.toVector());
for(double a=0;a<line.length();a+=.3){
Location dot=mouth.clone().add(line.clone().normalize().multiply(a));
f.getWorld().spawnParticle(Particle.DUST,dot,1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(222,101,132),1.3f));
}
Vector pull=f.getLocation().toVector().subtract(p.getLocation().toVector());
if(pull.lengthSquared()>.1)p.setVelocity(pull.normalize().multiply(1.25).setY(.33));
p.damage(2,f);
f.getWorld().playSound(f.getLocation(),Sound.ENTITY_FROG_TONGUE,1,.65f);
}
}
private ItemStack item(String kind,int amount){
Material material=switch(kind){case "hide"->Material.RABBIT_HIDE;case "boots"->Material.LEATHER_BOOTS;case "leggings"->Material.LEATHER_LEGGINGS;case "wings"->Material.ELYTRA;default->Material.FEATHER;};
ItemStack it=new ItemStack(material,amount);
ItemMeta m=it.getItemMeta();
m.setDisplayName(switch(kind){case "hide"->"§aЖабья кожа";case "boots"->"§aЖабьи ботинки";case "leggings"->"§2Жабьи поножи";case "wings"->"§aЖабьи крылья";default->"§dКрыло свиньи";});
m.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,kind);
m.setItemModel(new NamespacedKey("swampsky",kind.equals("pig_wing")?"pig_wing":"frog_"+kind));
m.setLore(switch(kind){
case "hide"->List.of("§7Добыча с гигантской жабы");
case "boots"->List.of("§aПрыгучесть I","§7Один отскок при приземлении","§7Shift — без отскока");
case "leggings"->List.of("§aСкорость I","§7Слизистый след");
case "wings"->List.of("§aЗащита: как железный нагрудник","§7Прочность: 4096","§7Дождь: подъём, вода: ускорение","§7Обычный полёт медленнее элитр","§8Не чинится, фейерверки бесполезны");
default->List.of();
});
if(m instanceof LeatherArmorMeta leather)leather.setColor(Color.fromRGB(75,171,80));
if(kind.equals("boots")||kind.equals("leggings")||kind.equals("wings")){
EquippableComponent equip=m.getEquippable();
if(equip!=null){
equip.setSlot(kind.equals("wings")?EquipmentSlot.CHEST:kind.equals("leggings")?EquipmentSlot.LEGS:EquipmentSlot.FEET);
equip.setModel(new NamespacedKey("swampsky",kind.equals("wings")?"frog_wings":"frog_armor"));
m.setEquippable(equip);
}
}
it.setItemMeta(m);
if(kind.equals("wings"))fixEquipment(it,kind);
return it;
}
private boolean is(ItemStack it,String id){
return it!=null&&it.hasItemMeta()&&id.equals(it.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING));
}
private void recipes(){
RecipeChoice skin=new RecipeChoice.ExactChoice(item("hide",1));
ShapedRecipe b=new ShapedRecipe(new NamespacedKey(this,"frog_boots"),item("boots",1));
b.shape("H H","H H"," S ");b.setIngredient('H',skin);b.setIngredient('S',Material.SLIME_BALL);Bukkit.addRecipe(b);
ShapedRecipe l=new ShapedRecipe(new NamespacedKey(this,"frog_leggings"),item("leggings",1));
l.shape("HHH","H H","S S");l.setIngredient('H',skin);l.setIngredient('S',Material.SLIME_BALL);Bukkit.addRecipe(l);
ShapedRecipe w=new ShapedRecipe(new NamespacedKey(this,"frog_wings"),item("wings",1));
w.shape("HSH","HPH"," H ");w.setIngredient('H',skin);w.setIngredient('S',Material.SLIME_BALL);w.setIngredient('P',Material.PHANTOM_MEMBRANE);Bukkit.addRecipe(w);
}
private void armor(Player p){
if(p.isDead())return;
if(is(p.getInventory().getBoots(),"boots"))p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,45,0,true,false,true));
if(is(p.getInventory().getLeggings(),"leggings")){
p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,45,0,true,false,true));
if(p.getVelocity().lengthSquared()>.05)p.getWorld().spawnParticle(Particle.ITEM_SLIME,p.getLocation(),2,.2,0,.2,.01);
}
}
private void upgradeOnlineEquipment(){
for(Player player:Bukkit.getOnlinePlayers()){
if(player.isDead())continue;
// Re-apply the proper equipment slot on older SwampSky items without replacing them.
PlayerInventory inventory=player.getInventory();
ItemStack boots=inventory.getBoots();
ItemStack legs=inventory.getLeggings();
ItemStack chest=inventory.getChestplate();
ItemStack helmet=inventory.getHelmet();
if(is(boots,"boots") && fixEquipment(boots,"boots"))inventory.setBoots(boots);
if(is(legs,"leggings") && fixEquipment(legs,"leggings"))inventory.setLeggings(legs);
if(is(chest,"wings") && fixEquipment(chest,"wings"))inventory.setChestplate(chest);
// Repair v1.2.1 wings that players accidentally equipped on their heads.
if(is(helmet,"wings")){
fixEquipment(helmet,"wings");
if(chest==null || chest.getType().isAir()){
inventory.setHelmet(null);
inventory.setChestplate(helmet);
}else inventory.setHelmet(helmet);
}
// Existing items from previous releases can still be inside the inventory,
// not equipped. Repair them too so right-click uses the correct slot.
ItemStack[] storage=inventory.getStorageContents();
for(int i=0;i<storage.length;i++){
ItemStack stack=storage[i];
String kind=is(stack,"wings")?"wings":is(stack,"boots")?"boots":is(stack,"leggings")?"leggings":null;
if(kind!=null && fixEquipment(stack,kind))inventory.setItem(i,stack);
}
ItemStack off=inventory.getItemInOffHand();
String offKind=is(off,"wings")?"wings":is(off,"boots")?"boots":is(off,"leggings")?"leggings":null;
if(offKind!=null && fixEquipment(off,offKind))inventory.setItemInOffHand(off);
}
}
private boolean fixEquipment(ItemStack stack,String kind){
if(stack==null||!stack.hasItemMeta())return false;
ItemMeta meta=stack.getItemMeta();
EquippableComponent equip=meta.getEquippable();
if(equip==null)return false;
boolean changed=false;
EquipmentSlot slot=kind.equals("wings")?EquipmentSlot.CHEST:kind.equals("leggings")?EquipmentSlot.LEGS:EquipmentSlot.FEET;
NamespacedKey model=new NamespacedKey("swampsky",kind.equals("wings")?"frog_wings":"frog_armor");
if(equip.getSlot()!=slot||!model.equals(equip.getModel())){
equip.setSlot(slot);
equip.setModel(model);
meta.setEquippable(equip);
changed=true;
}
if(kind.equals("wings")){
// Iron chestplate: 6 armor points. Applies only in the CHEST slot.
NamespacedKey modifierKey=new NamespacedKey(this,"frog_wings_armor");
boolean found=false;
var modifiers=meta.getAttributeModifiers(Attribute.ARMOR);
if(modifiers!=null)for(AttributeModifier mod:modifiers){
if(modifierKey.equals(mod.getKey())){found=true;break;}
}
if(!found){
meta.addAttributeModifier(Attribute.ARMOR,
new AttributeModifier(modifierKey,6.0,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));
changed=true;
}
// Purpur/Paper 26.3 throws if getMaxDamage() is called before custom
// max_damage has been set on the ItemMeta. The vanilla Elytra default is
// smaller than 4096 but does not count as a custom max damage component.
if(meta instanceof org.bukkit.inventory.meta.Damageable damageable
&&(!damageable.hasMaxDamage()||damageable.getMaxDamage()!=4096)){
damageable.setMaxDamage(4096);
changed=true;
}
if(meta.hasEnchant(Enchantment.MENDING)){
meta.removeEnchant(Enchantment.MENDING);
changed=true;
}
}
if(changed)stack.setItemMeta(meta);
return changed;
}
@EventHandler public void joined(PlayerJoinEvent event){
Bukkit.getScheduler().runTaskLater(this,()->{if(event.getPlayer().isOnline())upgradeOnlineEquipment();},20L);
}
private void bounceStep(){
for(Player p:Bukkit.getOnlinePlayers()){
UUID id=p.getUniqueId();
if(p.isDead()||p.isInsideVehicle()||p.isGliding()||p.isFlying()||p.isSwimming()||!is(p.getInventory().getBoots(),"boots")){
airborne.remove(id);
bounceLocked.remove(id);
fallSpeed.remove(id);
groundedTicks.remove(id);
continue;
}
if(!p.isOnGround()){
airborne.add(id);
groundedTicks.put(id,0);
fallSpeed.merge(id,Math.max(0,-p.getVelocity().getY()),Math::max);
continue;
}
boolean landing=airborne.remove(id);
int steady=groundedTicks.getOrDefault(id,0);
if(landing){
groundedTicks.put(id,0);
double descent=fallSpeed.getOrDefault(id,0.0);
fallSpeed.remove(id);
// One bounce only. The lock stays active through the resulting airborne arc;
// a new jump is armed only after a few fully grounded ticks.
if(bounceLocked.contains(id)||p.isSneaking()||descent<.08)continue;
bounceLocked.add(id);
double strength=Math.min(.72,Math.max(.50,descent*.83));
Vector v=p.getVelocity();
p.setVelocity(new Vector(v.getX(),strength,v.getZ()));
p.setFallDistance(0);
p.getWorld().playSound(p.getLocation(),Sound.BLOCK_SLIME_BLOCK_FALL,.65f,1.15f);
p.getWorld().spawnParticle(Particle.ITEM_SLIME,p.getLocation(),9,.30,.07,.30,.04);
continue;
}
steady++;
groundedTicks.put(id,steady);
if(steady>=4){
bounceLocked.remove(id);
fallSpeed.remove(id);
}
}
}
private void frogWingsMotion(){
for(Player p:Bukkit.getOnlinePlayers()){
if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||p.isInsideVehicle())continue;
if(!is(p.getInventory().getChestplate(),"wings"))continue;
Vector v=p.getVelocity();
if(p.isInWater()){
// Water alone grants fast motion. This also works when swimming (not gliding).
Vector aim=p.getLocation().getDirection();
double strength=getConfig().getDouble("frog-wings-water-speed",.95);
if(aim.lengthSquared()>.001){
Vector desired=aim.normalize().multiply(strength);
p.setVelocity(v.multiply(.30).add(desired.multiply(.70)));
}
continue;
}
if(!p.isGliding())continue;
boolean rain=p.getWorld().hasStorm()
&&p.getWorld().getHighestBlockYAt(p.getLocation().getBlockX(),p.getLocation().getBlockZ())<=p.getLocation().getBlockY()
&&!dryBiome(p.getLocation().getBlock().getBiome());
double maxHorizontal=Math.max(.30,Math.min(1.3,getConfig().getDouble(rain?"frog-wings-rain-speed":"frog-wings-glide-speed",rain?.58:.65)));
double vx=v.getX(),vz=v.getZ();
double horizontal=Math.sqrt(vx*vx+vz*vz);
if(horizontal>maxHorizontal){
vx*=maxHorizontal/horizontal;
vz*=maxHorizontal/horizontal;
}
double y;
if(rain){
// Rain creates a gentle ascent; no artificial horizontal slowdown below the cap.
y=.085;
}else{
// Ordinary frog-wings flight always sinks, even if the player pitches up.
y=Math.min(-.065,v.getY());
}
p.setVelocity(new Vector(vx,y,vz));
}
}
private boolean dryBiome(Biome b){
String name=b.name();
return name.contains("DESERT")||name.contains("BADLANDS")||name.contains("SAVANNA");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noRocketBoost(PlayerElytraBoostEvent event){
if(is(event.getPlayer().getInventory().getChestplate(),"wings"))event.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noMending(PlayerItemMendEvent event){
if(is(event.getItem(),"wings"))event.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noAnvilRepair(PrepareAnvilEvent event){
if(is(event.getInventory().getItem(0),"wings")||is(event.getInventory().getItem(1),"wings"))
event.setResult(null);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noGrindstoneRepair(PrepareGrindstoneEvent event){
if(is(event.getInventory().getItem(0),"wings")||is(event.getInventory().getItem(1),"wings"))
event.setResult(null);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void fall(EntityDamageEvent e){
if(e.getEntity() instanceof Player p && e.getCause()==EntityDamageEvent.DamageCause.FALL){
UUID id=p.getUniqueId();
Long until=dismountFall.get(id);
if(until!=null&&tick<=until){
dismountFall.remove(id);
e.setCancelled(true);
return;
}
if(is(p.getInventory().getBoots(),"boots"))e.setCancelled(true);
}
}
private void faceVector(Entity entity,Vector vector){
Vector flat=vector.clone().setY(0);
if(flat.lengthSquared()<.003)return;
Location looking=entity.getLocation();
looking.setDirection(flat);
float target=looking.getYaw();
float current=entity.getLocation().getYaw();
float delta=(float)Math.IEEEremainder(target-current,360.0);
// Smooth turning, never flying or jumping backwards.
float step=Math.max(-26f,Math.min(26f,delta));
entity.setRotation(current+step,0);
}
private boolean swamp(Biome b){return b==Biome.SWAMP||b==Biome.MANGROVE_SWAMP;}
private boolean allowedPigBiome(Biome b){
String id=b.name();
return !id.contains("DESERT")&&!id.contains("BADLANDS")
&&!id.contains("SNOW")&&!id.contains("FROZEN")
&&!id.contains("ICE")&&!id.contains("PEAK")&&!id.contains("GROVE");
}
private void populateNearby(){
for(Player p:Bukkit.getOnlinePlayers()){
if(p.getGameMode()==GameMode.SPECTATOR||p.isDead())continue;
World w=p.getWorld();
if(w.getEnvironment()!=World.Environment.NORMAL)continue;
Location center=p.getLocation();
int nearbyFrogs=0,nearbyPigs=0;
for(Entity e:w.getNearbyEntities(center,72,36,72)){
if(e instanceof Frog && marked(e,giantKey))nearbyFrogs++;
if(e instanceof Pig && marked(e,flyingKey))nearbyPigs++;
}
int targetFrogs=Math.max(2,Math.min(3,getConfig().getInt("swamp-giant-frog-target",3)));
if(nearbyFrogs<targetFrogs){
for(int i=0;i<20;i++){
Location spawn=surfaceCandidate(w,center,24,68);
if(spawn==null||!swamp(spawn.getBlock().getBiome()))continue;
if(nearbyFrogs==2 && random.nextDouble()>.45)break;
Frog frog=w.spawn(spawn,Frog.class);
giantFrog(frog);
break;
}
}
if(nearbyPigs>=Math.max(1,getConfig().getInt("flying-pig-nearby-cap",5)))continue;
for(int i=0;i<14;i++){
Location spawn=surfaceCandidate(w,center,22,66);
if(spawn==null)continue;
Biome biome=spawn.getBlock().getBiome();
if(!allowedPigBiome(biome))continue;
double chance=biome==Biome.MUSHROOM_FIELDS?
getConfig().getDouble("mushroom-pig-spawn-chance",.90):
getConfig().getDouble("flying-pig-spawn-chance",.45);
if(random.nextDouble()>chance)break;
Pig pig=w.spawn(spawn,Pig.class);
flyingPig(pig);
break;
}
}
}
private Location surfaceCandidate(World w,Location center,int minRadius,int maxRadius){
double angle=random.nextDouble()*Math.PI*2;
double radius=minRadius+random.nextDouble()*(maxRadius-minRadius);
int x=(int)Math.floor(center.getX()+Math.cos(angle)*radius);
int z=(int)Math.floor(center.getZ()+Math.sin(angle)*radius);
if(!w.isChunkLoaded(x>>4,z>>4))return null;
int top=w.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);
// Heightmap versions differ on whether the returned Y is the top solid block
// or the first free block. Resolve the actual standing surface safely.
for(int y=Math.min(top+1,w.getMaxHeight()-3);y>=Math.max(top-3,w.getMinHeight()+2);y--){
Block floor=w.getBlockAt(x,y,z);
Block feet=w.getBlockAt(x,y+1,z);
Block head=w.getBlockAt(x,y+2,z);
if(!floor.getType().isSolid()||floor.isLiquid())continue;
if(!feet.isPassable()||!head.isPassable()||feet.isLiquid()||head.isLiquid())continue;
return new Location(w,x+.5,y+1,z+.5);
}
return null;
}

private void pigClear(UUID id){
List<UUID> ds=displays.remove(id);
if(ds!=null)for(UUID d:ds){Entity e=Bukkit.getEntity(d);if(e!=null)e.remove();}
}
private void drawWings(Pig pig){
List<UUID> list=displays.get(pig.getUniqueId());
if(list==null||list.size()!=2||Bukkit.getEntity(list.get(0))==null||Bukkit.getEntity(list.get(1))==null){
pigClear(pig.getUniqueId());
list=new ArrayList<>();
for(int j=0;j<2;j++){
ItemDisplay d=pig.getWorld().spawn(pig.getLocation(),ItemDisplay.class,entity->{
entity.setPersistent(false);
entity.setBillboard(Display.Billboard.FIXED);
entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
entity.setItemStack(item("pig_wing",1));
entity.setViewRange(1.4f);
entity.setTeleportDuration(1);
mark(entity,displayKey);
});
list.add(d.getUniqueId());
}
displays.put(pig.getUniqueId(),list);
}
Location at=pig.getLocation();
double yaw=Math.toRadians(at.getYaw());
// Local basis relative to the pig's body: sideways and back, not up by its ears.
Vector right=new Vector(-Math.cos(yaw),0,-Math.sin(yaw));
Vector back=new Vector(Math.sin(yaw),0,-Math.cos(yaw));
double size=.5;
double flap=Math.sin(tick*.065)*.085;
for(int j=0;j<2;j++){
Entity e=Bukkit.getEntity(list.get(j));
if(!(e instanceof ItemDisplay d))continue;
float side=j==0?-1f:1f;
// Wings sit at the back of the rib cage, not beside the pig's head.
Location loc=at.clone().add(right.clone().multiply(side*.37*size));
loc.add(back.clone().multiply(.34*size));
loc.add(0,.61*size+flap*size,0);
// Rotate the wing planes toward the rear so they resemble folded flight
// feathers from the front rather than flat wings sprouting near the ears.
loc.setYaw(at.getYaw()+side*76f);
loc.setPitch(-10f);
d.teleport(loc);
Quaternionf pose=new Quaternionf().rotationXYZ(.07f,side*.12f,(float)(flap*side));
d.setTransformation(new Transformation(
new Vector3f(0f,0f,0f),
pose,
new Vector3f(side*.27f,.245f,.27f),
new Quaternionf()
));
}
}
@EventHandler public void playerInput(PlayerInputEvent e){inputCache.put(e.getPlayer().getUniqueId(),e.getInput());}
@EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();inputCache.remove(id);heldJump.remove(id);airborne.remove(id);fallSpeed.remove(id);bounceLocked.remove(id);groundedTicks.remove(id);}
@Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
if(a.length==0||a[0].equalsIgnoreCase("help")){
sender.sendMessage("§aSwampSky 1.9.0 §7| /swampsky spawn <flying_pig|giant_frog>");
sender.sendMessage("§7/ss status §8(диагностика AI и скорости свинок)");
sender.sendMessage("§7/ss repair §8(перезапуск поведения свинок поблизости)");
sender.sendMessage("§7/swampsky give <ник> <hide|boots|leggings|wings> [1-64]");
return true;
}
if(!sender.hasPermission("swampsky.admin")){sender.sendMessage("§cНет прав.");return true;}
if(a[0].equalsIgnoreCase("status")){
sender.sendMessage("§aSwampSky §7| Летающих свинок отслеживается: §f"+wings.size()+"§7, гигантских лягушек: §f"+frogs.size());
if(sender instanceof Player viewer){
Pig nearest=null;double nearestDistance=32*32;
for(Entity entity:viewer.getWorld().getNearbyEntities(viewer.getLocation(),32,32,32)){
if(!(entity instanceof Pig candidate)||!marked(candidate,flyingKey))continue;
double d=entity.getLocation().distanceSquared(viewer.getLocation());
if(d<nearestDistance){nearest=candidate;nearestDistance=d;}
}
if(nearest==null)sender.sendMessage("§7Рядом (32 блока) нет летающей свинки.");
else{
UUID id=nearest.getUniqueId();Vector velocity=nearest.getVelocity();
sender.sendMessage("§7Свиника: §f"+(int)Math.sqrt(nearestDistance)+" блоков §7| AI="+nearest.hasAI()+" aware="+nearest.isAware()+" gravity="+nearest.hasGravity());
sender.sendMessage("§7Скорость: §f"+String.format(java.util.Locale.ROOT,"%.2f",velocity.length())+" §7| цель="+(roamTargets.containsKey(id)?"свободный полёт":onlineOwner(nearest)!=null?"владелец / цель":"поиск"));
sender.sendMessage("§7Застревания подряд: §f"+motionStalls.getOrDefault(id,0));
}
}
return true;
}
if(a[0].equalsIgnoreCase("repair")){
if(!(sender instanceof Player player)){sender.sendMessage("§cЭту команду выполняй в игре.");return true;}
int count=0;
for(Entity entity:player.getWorld().getNearbyEntities(player.getLocation(),48,36,48)){
if(!(entity instanceof Pig pig)||!marked(pig,flyingKey))continue;
UUID id=pig.getUniqueId();
pig.setAI(true);pig.setAware(false);pig.setGravity(false);pig.setCollidable(false);
scaleWingedPig(pig);
roamTargets.remove(id);roamExpiry.remove(id);motionStalls.remove(id);motionSamples.remove(id);flightVectors.remove(id);
roamHomes.put(id,pig.getLocation().clone());wings.add(id);
count++;
}
sender.sendMessage("§aСостояние обновлено у "+count+" летающих свинок в радиусе 48 блоков.");
return true;
}
if(a[0].equalsIgnoreCase("spawn")){
if(!(sender instanceof Player p)){sender.sendMessage("§cТолько игрок.");return true;}
if(a.length<2){sender.sendMessage("§cУкажи тип.");return true;}
Location loc=p.getLocation().add(p.getLocation().getDirection().multiply(3));
if(a[1].equalsIgnoreCase("flying_pig"))flyingPig(p.getWorld().spawn(loc,Pig.class));
else if(a[1].equalsIgnoreCase("giant_frog"))giantFrog(p.getWorld().spawn(loc,Frog.class));
else{sender.sendMessage("§cНеизвестный тип.");return true;}
sender.sendMessage("§aСоздано.");return true;
}
if(a[0].equalsIgnoreCase("give")){
if(a.length<3){sender.sendMessage("§c/swampsky give <ник> <hide|boots|leggings|wings> [кол-во]");return true;}
Player p=Bukkit.getPlayerExact(a[1]);
if(p==null){sender.sendMessage("§cИгрок не в сети.");return true;}
if(!List.of("hide","boots","leggings","wings").contains(a[2])){sender.sendMessage("§cНеизвестная вещь.");return true;}
int count=1;
if(a.length>3)try{count=Integer.parseInt(a[3]);}catch(NumberFormatException ex){sender.sendMessage("§cНужно число.");return true;}
if(count<1||count>64||(!a[2].equals("hide")&&count!=1)){sender.sendMessage("§cНедопустимое количество.");return true;}
for(ItemStack rem:p.getInventory().addItem(item(a[2],count)).values())p.getWorld().dropItemNaturally(p.getLocation(),rem);
sender.sendMessage("§aВыдано "+a[2]+": "+count);return true;
}
return false;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] a){
if(!sender.hasPermission("swampsky.admin"))return List.of();
if(a.length==1)return List.of("spawn","give","status","repair","help");
if(a.length==2&&a[0].equalsIgnoreCase("spawn"))return List.of("flying_pig","giant_frog");
if(a.length==2&&a[0].equalsIgnoreCase("give"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(a.length==3&&a[0].equalsIgnoreCase("give"))return List.of("hide","boots","leggings","wings");
return List.of();
}
}
