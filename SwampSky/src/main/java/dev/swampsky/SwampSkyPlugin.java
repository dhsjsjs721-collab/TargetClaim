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
private final Map<UUID,UUID> aggro=new HashMap<>();
private final Map<UUID,Long> aggroUntil=new HashMap<>(),attackCooldown=new HashMap<>(),hitWindow=new HashMap<>(),dismountFall=new HashMap<>(),knockbackUntil=new HashMap<>();
private final Map<UUID,Integer> hitCount=new HashMap<>();
private final Map<UUID,Vector> flightVectors=new HashMap<>();
private final Set<UUID> airborne=new HashSet<>(), bounceLocked=new HashSet<>();
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
Bukkit.getScheduler().runTaskTimer(this,this::bounceStep,1L,1L);
Bukkit.getScheduler().runTaskTimer(this,this::frogWingsMotion,1L,1L);
Bukkit.getScheduler().runTaskTimer(this,this::populateNearby,120L,160L);
Bukkit.getScheduler().runTaskTimer(this,this::upgradeOnlineEquipment,40L,100L);
}
@Override public void onDisable(){
for(UUID id:new ArrayList<>(displays.keySet()))pigClear(id);
aggro.clear();aggroUntil.clear();attackCooldown.clear();hitWindow.clear();hitCount.clear();flightVectors.clear();dismountFall.clear();
for(UUID id:ground){Entity e=Bukkit.getEntity(id);if(e instanceof Pig p){p.setAI(true);p.setAware(true);}}
for(UUID id:wings){
Entity e=Bukkit.getEntity(id);
if(e instanceof Pig p){p.setAI(true);p.setAware(true);p.setGravity(true);}
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

private void flyingPig(Pig p){
if(marked(p,flyingKey))return;
mark(p,flyingKey);ground.remove(p.getUniqueId());p.setGravity(false);p.setAI(true);p.setAware(false);
p.setCustomName(null);p.setCustomNameVisible(false);
p.setRemoveWhenFarAway(false);wings.add(p.getUniqueId());
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
f.setAI(true);f.setAware(false);
f.setCustomName(null);f.setCustomNameVisible(false);
f.setRemoveWhenFarAway(false);
frogs.add(f.getUniqueId());
}
private void scan(Chunk chunk){
for(Entity e:chunk.getEntities()){
if(e instanceof Pig p){
if(marked(p,flyingKey)){
wings.add(p.getUniqueId());p.setGravity(false);p.setAI(true);p.setAware(false);
p.setCustomName(null);p.setCustomNameVisible(false);
}else{
ground.add(p.getUniqueId());p.setAI(true);
if(owner(p)!=null && p.getCustomName()!=null && (p.getCustomName().contains("свинья")||p.getCustomName().contains("Свинья")))p.setCustomName(null);
if(owner(p)!=null)p.setCustomNameVisible(false);
}
}
if(e instanceof Frog f && marked(f,giantKey)){
frogs.add(f.getUniqueId());f.setCustomName(null);f.setCustomNameVisible(false);f.setAI(true);f.setAware(false);
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
if(!marked(entity,flyingKey))ground.remove(entity.getUniqueId());
}
}
@EventHandler public void dead(EntityDeathEvent e){
Entity entity=e.getEntity();
UUID id=entity.getUniqueId();
if(entity instanceof Pig){
if(marked(entity,flyingKey))e.getDrops().add(new ItemStack(Material.PHANTOM_MEMBRANE,1+random.nextInt(2)));
wings.remove(id);ground.remove(id);pigClear(id);
aggro.remove(id);aggroUntil.remove(id);attackCooldown.remove(id);hitWindow.remove(id);hitCount.remove(id);flightVectors.remove(id);knockbackUntil.remove(id);
}
if(entity instanceof Frog && marked(entity,giantKey)){
frogs.remove(id);
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
ItemStack held=player.getInventory().getItemInMainHand();
if(held.getType()==Material.LEAD||player.getInventory().getItemInOffHand().getType()==Material.LEAD)return;
boolean hasFood=food(held);
String owner=owner(pig);
if(owner==null){
if(!hasFood)return;
e.setCancelled(true);
if(!pig.isAdult())return;
double chance=held.getType()==Material.PUMPKIN_PIE?.78:.52;
consume(player,held);
if(random.nextDouble()<chance){
pig.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,player.getUniqueId().toString());
pig.setSaddle(false);
if(!marked(pig,flyingKey))ground.add(pig.getUniqueId());
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
if(!player.isSneaking()||!pig.getPassengers().isEmpty()||player.isInsideVehicle())return;
Bukkit.getScheduler().runTask(this,()->{
if(!pig.isValid()||!player.isOnline()||player.isInsideVehicle()||!owned(pig,player)||!pig.getPassengers().isEmpty())return;
pig.setAI(true);pig.setAware(false);
pig.setSaddle(false);
pig.addPassenger(player);
});
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
if(!(e.getEntity() instanceof Player player)||!(e.getDismounted() instanceof Pig pig)||!owned(pig,player))return;
UUID id=player.getUniqueId();
long until=tick+240;
dismountFall.put(id,until);
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

private void step(){
tick+=3;
for(UUID id:new ArrayList<>(wings)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Pig p)||!p.isValid()){wings.remove(id);pigClear(id);continue;}
movePig(p,true);drawWings(p);
}
for(UUID id:new ArrayList<>(ground)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Pig pig)||!pig.isValid()){ground.remove(id);continue;}
movePig(pig,false);
}
if(tick%21==0)for(Player p:Bukkit.getOnlinePlayers())armor(p);
for(UUID id:new ArrayList<>(frogs)){
Entity e=Bukkit.getEntity(id);
if(!(e instanceof Frog frog)||!frog.isValid()){frogs.remove(id);continue;}
fight(frog);
}
}
private void movePig(Pig pig,boolean fly){
Player rider=null;
for(Entity passenger:pig.getPassengers())if(passenger instanceof Player p){rider=p;break;}
if(rider!=null&&owned(pig,rider)){ride(pig,rider,fly);return;}
pig.setAI(true);pig.setGravity(!fly);
if(!fly){moveGroundPig(pig);return;}
pig.setAware(false);
UUID id=pig.getUniqueId();
if(tick<knockbackUntil.getOrDefault(id,-1L))return;
Player own=onlineOwner(pig);
if(own!=null&&own.getWorld()!=pig.getWorld())own=null;
Player lure=null;double lureDistance=23*23;
for(Player player:pig.getWorld().getPlayers()){
if(player.isDead()||player.getGameMode()==GameMode.SPECTATOR)continue;
if(!food(player.getInventory().getItemInMainHand())&&!food(player.getInventory().getItemInOffHand()))continue;
double dist=player.getLocation().distanceSquared(pig.getLocation());
if(dist<lureDistance){lure=player;lureDistance=dist;}
}
LivingEntity fear=null;double fearDistance=11*11;
Spider spider=null;double spiderDistance=14*14;
for(Entity entity:pig.getNearbyEntities(15,12,15)){
if(!(entity instanceof LivingEntity target)||entity==pig||target.isDead())continue;
double dist=pig.getLocation().distanceSquared(entity.getLocation());
if(frightening(entity)&&dist<fearDistance){fear=target;fearDistance=dist;}
if(entity instanceof Spider s&&dist<spiderDistance){spider=s;spiderDistance=dist;}
}
Player attacker=null;
if(tick<aggroUntil.getOrDefault(id,0L)){
UUID attackerId=aggro.get(id);
if(attackerId!=null){
attacker=Bukkit.getPlayer(attackerId);
if(attacker==null||attacker.isDead()||attacker.getWorld()!=pig.getWorld()
||attacker.getGameMode()==GameMode.CREATIVE||attacker.getGameMode()==GameMode.SPECTATOR
||attacker.getLocation().distanceSquared(pig.getLocation())>35*35)attacker=null;
}
}else{aggro.remove(id);aggroUntil.remove(id);}
Location target=null;
double speed=getConfig().getDouble("flying-pig-speed",.48);
boolean aggressive=false;
LivingEntity attackTarget=null;
if(fear!=null){
// Snow and desert creatures scare pigs; fleeing always overrides attacking.
Vector away=pig.getLocation().toVector().subtract(fear.getLocation().toVector());
away.setY(0);
if(away.lengthSquared()<.01)away=new Vector(1,0,0);
away.normalize().multiply(9);
target=pig.getLocation().clone().add(away).add(0,3.8,0);
speed=.74;
}else if(attacker!=null){
attackTarget=attacker;aggressive=true;speed=.72;
target=swoopTarget(pig,attacker);
}else if(spider!=null){
attackTarget=spider;aggressive=true;speed=.65;
target=swoopTarget(pig,spider);
}else if(pig.isLeashed()){
Entity holder=null;
try{holder=pig.getLeashHolder();}catch(IllegalStateException ignored){}
if(holder!=null && holder.getWorld()==pig.getWorld()){
target=holder.getLocation().add(0,1.3,0);
speed=.53;
}
}
if(target==null && !aggressive && fear==null){
if(lure!=null){
target=lure.getLocation().clone().add(0,1.4,0);
speed=.65;
// A fast graceful swoop toward pumpkin, not slow hovering in place.
}else if(own!=null){
// Tamed winged pigs circle high above their owner forever while online.
// Different pigs use different orbit phases and smoothly vary altitude.
double phase=tick*.047+(id.hashCode()%97);
double radius=3.4+Math.sin(phase*.33)*1.15;
target=own.getLocation().clone().add(Math.cos(phase)*radius,4.1+Math.sin(phase*1.6)*1.25,Math.sin(phase)*radius);
speed=.50;
if(pig.getLocation().distanceSquared(own.getLocation())>38*38 && !pig.isLeashed()){
Location destination=own.getLocation().clone().add(0,3.4,0);
if(destination.getBlock().isPassable()&&destination.clone().add(0,1,0).getBlock().isPassable()){
pig.teleport(destination);
flightVectors.remove(id);
return;
}
}
}else{
// Free-flying pigs wheel and circle like birds rather than hovering motionless.
double phase=tick*.08+(id.hashCode()%157);
double radius=6.5+Math.sin(phase*.15)*2.3;
target=pig.getLocation().clone().add(Math.cos(phase)*radius,.70+Math.sin(phase*2.6)*2.5,Math.sin(phase)*radius);
speed=.34;
}
}
if(target==null)return;
Vector delta=target.toVector().subtract(pig.getLocation().toVector());
if(delta.lengthSquared()<.04)delta=new Vector(0,0,0);
else delta.normalize().multiply(Math.min(speed,.13+delta.length()*.13));
if(aggressive && attackTarget!=null){
double dist=pig.getLocation().distanceSquared(attackTarget.getLocation());
if(dist<2.7*2.7 && tick-attackCooldown.getOrDefault(id,-99999L)>28){
attackCooldown.put(id,tick);
attackTarget.damage(attackTarget instanceof Player?3.0:4.5,pig);
Vector recoil=pig.getLocation().toVector().subtract(attackTarget.getLocation().toVector());
if(recoil.lengthSquared()>.01){recoil.normalize().multiply(.48);recoil.setY(.36);delta.add(recoil);}
pig.getWorld().playSound(pig.getLocation(),Sound.ENTITY_PHANTOM_SWOOP,.7f,1.3f);
}
}
if(onSnowOrIce(pig.getLocation()))delta.setY(Math.max(delta.getY(),.29));
// Keep the pig away from blocks in front and underneath it, including
// during rapid dives, without forcing an instant head-first collision.
Vector ahead=delta.lengthSquared()>.01?delta.clone().normalize().multiply(1.7):new Vector();
if(!pig.getLocation().clone().add(ahead).getBlock().isPassable()
||!pig.getLocation().clone().add(ahead).add(0,1,0).getBlock().isPassable()){
delta.setY(Math.max(.30,delta.getY()));
}
int ground=0;
for(int y=1;y<=6;y++)if(!pig.getLocation().clone().subtract(0,y,0).getBlock().isPassable()){ground=y;break;}
if(ground>0&&ground<3)delta.setY(Math.max(.16,delta.getY()));
Vector previous=flightVectors.getOrDefault(id,pig.getVelocity());
Vector smooth=previous.multiply(.34).add(delta.multiply(.66));
if(smooth.lengthSquared()>speed*speed)smooth.normalize().multiply(speed);
flightVectors.put(id,smooth.clone());
pig.setVelocity(smooth);
faceVector(pig,smooth);
}
private void moveGroundPig(Pig pig){
Player lure=null;double best=12*12;
for(Player p:pig.getWorld().getPlayers()){
if(!food(p.getInventory().getItemInMainHand())&&!food(p.getInventory().getItemInOffHand()))continue;
double d=p.getLocation().distanceSquared(pig.getLocation());
if(d<best){best=d;lure=p;}
}
if(lure==null){pig.setAware(true);return;}
pig.setAware(false);
Vector movement=lure.getLocation().toVector().subtract(pig.getLocation().toVector()).setY(0);
if(movement.lengthSquared()<2){pig.setVelocity(new Vector(0,pig.getVelocity().getY(),0));return;}
movement.normalize().multiply(.29);
Location front=pig.getLocation().add(movement.clone().multiply(2));
if(!front.getBlock().isPassable()&&front.clone().add(0,1,0).getBlock().isPassable()&&pig.isOnGround())movement.setY(.47);
else movement.setY(pig.getVelocity().getY());
pig.setVelocity(movement);
faceVector(pig,movement);
}
private boolean onSnowOrIce(Location location){
Material below=location.clone().subtract(0,1,0).getBlock().getType();
return snow(location)||below==Material.ICE||below==Material.PACKED_ICE||below==Material.BLUE_ICE;
}
private boolean frightening(Entity e){
return switch(e.getType()){
case STRAY,SNOW_GOLEM,POLAR_BEAR,HUSK,CAMEL->true;
default->false;
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

private void ride(Pig pig,Player p,boolean fly){
pig.setAI(true);pig.setAware(false);pig.setGravity(!fly);
Input in=inputCache.getOrDefault(p.getUniqueId(),p.getCurrentInput());
Vector forward=p.getLocation().getDirection().setY(0);
if(forward.lengthSquared()<.01)forward.setX(0).setZ(1);
forward.normalize();
Vector right=new Vector(-forward.getZ(),0,forward.getX());
Vector dir=new Vector();
if(in.isForward())dir.add(forward);
if(in.isBackward())dir.subtract(forward);
if(in.isLeft())dir.add(right);
if(in.isRight())dir.subtract(right);
double speed=getConfig().getDouble("pig-speed",.43)*(fly?1.1:1);
if(in.isSprint())speed*=1.10;
if(snow(pig.getLocation()))speed*=getConfig().getDouble("snow-multiplier",.35);
boolean slope=false;
if(dir.lengthSquared()>.01){
dir.normalize();
Location front=pig.getLocation().add(dir.clone().multiply(.85));
slope=(!front.getBlock().isPassable()&&front.clone().add(0,1,0).getBlock().isPassable())
||front.clone().subtract(0,1,0).getBlock().isPassable();
if(slope)speed*=getConfig().getDouble("slope-multiplier",1.45);
dir.multiply(speed);
}
double y=fly?0:pig.getVelocity().getY();
if(fly){
if(in.isJump())y=.29;
else if(in.isForward()&&p.getLocation().getPitch()>35)y=-.18;
else if(in.isForward()&&p.getLocation().getPitch()<-35)y=.13;
else if(slope)y=.18;
}else if((in.isJump()||slope)&&pig.isOnGround())y=.52;
if(fly&&snow(pig.getLocation()))y=Math.max(y,.10);
pig.setVelocity(new Vector(dir.getX(),y,dir.getZ()));
pig.setRotation(p.getLocation().getYaw(),0);
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
if(nearest==null)return;
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
if(meta instanceof org.bukkit.inventory.meta.Damageable damageable && damageable.getMaxDamage()<4096){
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
entity.setTeleportDuration(3);
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
double flap=Math.sin(tick*.12)*.065;
for(int j=0;j<2;j++){
Entity e=Bukkit.getEntity(list.get(j));
if(!(e instanceof ItemDisplay d))continue;
float side=j==0?-1f:1f;
// Wings sit at the back of the rib cage, not beside the pig's head.
Location loc=at.clone().add(right.clone().multiply(side*.37));
loc.add(back.clone().multiply(.34));
loc.add(0,.61+flap,0);
// Rotate the wing planes toward the rear so they resemble folded flight
// feathers from the front rather than flat wings sprouting near the ears.
loc.setYaw(at.getYaw()+side*76f);
loc.setPitch(-10f);
d.teleport(loc);
Quaternionf pose=new Quaternionf().rotationXYZ(.07f,side*.12f,(float)(flap*side));
d.setTransformation(new Transformation(
new Vector3f(0f,0f,0f),
pose,
new Vector3f(side*.54f,.49f,.54f),
new Quaternionf()
));
}
}
@EventHandler public void playerInput(PlayerInputEvent e){inputCache.put(e.getPlayer().getUniqueId(),e.getInput());}
@EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();inputCache.remove(id);airborne.remove(id);fallSpeed.remove(id);bounceLocked.remove(id);groundedTicks.remove(id);}
@Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){
if(a.length==0||a[0].equalsIgnoreCase("help")){
sender.sendMessage("§aSwampSky §7| /swampsky spawn <flying_pig|giant_frog>");
sender.sendMessage("§7/swampsky give <ник> <hide|boots|leggings|wings> [1-64]");
return true;
}
if(!sender.hasPermission("swampsky.admin")){sender.sendMessage("§cНет прав.");return true;}
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
if(a.length==1)return List.of("spawn","give","help");
if(a.length==2&&a[0].equalsIgnoreCase("spawn"))return List.of("flying_pig","giant_frog");
if(a.length==2&&a[0].equalsIgnoreCase("give"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if(a.length==3&&a[0].equalsIgnoreCase("give"))return List.of("hide","boots","leggings","wings");
return List.of();
}
}
