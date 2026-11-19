package dev.swampsky;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Biome;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
}
@Override public void onDisable(){
for(UUID id:new ArrayList<>(displays.keySet()))pigClear(id);
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
frogs.add(f.getUniqueId());f.setCustomName(null);f.setCustomNameVisible(false);
}
if(e instanceof ItemDisplay && marked(e,displayKey))e.remove();
}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void spawn(CreatureSpawnEvent e){
if(e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.NATURAL&&e.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.CHUNK_GEN)return;
if(e.getEntity() instanceof Pig p){
if(random.nextDouble()<getConfig().getDouble("flying-chance",.17))Bukkit.getScheduler().runTask(this,()->{if(p.isValid())flyingPig(p);});
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
if(e.getHand()!=EquipmentSlot.HAND||!(e.getRightClicked() instanceof Pig p))return;
Player player=e.getPlayer();
ItemStack held=player.getInventory().getItemInMainHand();
String owner=owner(p);
if(owner==null){
if(!food(held))return;
e.setCancelled(true);
double chance=held.getType()==Material.PUMPKIN_PIE?.78:.52;
if(player.getGameMode()!=GameMode.CREATIVE)held.setAmount(held.getAmount()-1);
p.getWorld().spawnParticle(Particle.HEART,p.getLocation().add(0,1,0),5,.3,.3,.3,0);
if(random.nextDouble()<chance){
p.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,player.getUniqueId().toString());
p.setSaddle(true);
if(!marked(p,flyingKey))ground.add(p.getUniqueId());
p.setCustomName(null);p.setCustomNameVisible(false);
}else p.getWorld().spawnParticle(Particle.SMOKE,p.getLocation().add(0,.9,0),3,.25,.2,.25,.01);
return;
}
e.setCancelled(true);
if(!owned(p,player))return;
if(food(held)){
if(player.getGameMode()!=GameMode.CREATIVE)held.setAmount(held.getAmount()-1);
AttributeInstance max=p.getAttribute(Attribute.MAX_HEALTH);
p.setHealth(Math.min(max==null?10:max.getValue(),p.getHealth()+3));
p.getWorld().spawnParticle(Particle.HEART,p.getLocation().add(0,.9,0),5,.3,.3,.3,0);
return;
}
if(p.getPassengers().isEmpty()){
Bukkit.getScheduler().runTask(this,()->{
if(!p.isValid()||!player.isOnline()||player.isInsideVehicle()||!owned(p,player)||!p.getPassengers().isEmpty())return;
p.setSaddle(true);p.setAI(true);p.setAware(false);
p.addPassenger(player);
});
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
Player lure=null;double best=14*14;
for(Player p:pig.getWorld().getPlayers()){
if(!food(p.getInventory().getItemInMainHand())&&!food(p.getInventory().getItemInOffHand()))continue;
double d=p.getLocation().distanceSquared(pig.getLocation());
if(d<best){best=d;lure=p;}
}
Vector v;
if(lure==null&&!fly){
pig.setAware(true);
return;
}
pig.setAware(false);
if(lure!=null){
Vector delta=lure.getLocation().clone().add(0,fly?1.4:0,0).toVector().subtract(pig.getLocation().toVector());
if(!fly)delta.setY(0);
double speed=fly?.28:.23;
if(snow(pig.getLocation()))speed*=.35;
v=delta.lengthSquared()>3.5?delta.normalize().multiply(speed):new Vector();
if(!fly)v.setY(pig.getVelocity().getY());
}else{
double a=tick/35.0+(pig.getUniqueId().hashCode()%150);
v=new Vector(Math.sin(a)*.09,Math.sin(tick/22.0)*.04,Math.cos(a)*.09);
int ground=0;
for(int i=1;i<7;i++)if(!pig.getLocation().clone().subtract(0,i,0).getBlock().isPassable()){ground=i;break;}
if(ground>0&&ground<3)v.setY(.14);
if(ground==0)v.setY(-.06);
}
if(fly){
Location ahead=pig.getLocation().clone().add(v.clone().multiply(3));
if(!ahead.getBlock().isPassable()||!ahead.clone().add(0,1,0).getBlock().isPassable())v.setY(.28);
if(snow(pig.getLocation()))v.setY(.22);
}else if(v.lengthSquared()>.04){
Location ahead=pig.getLocation().clone().add(v.clone().setY(0).normalize());
if(!ahead.getBlock().isPassable()&&ahead.clone().add(0,1,0).getBlock().isPassable()&&pig.isOnGround())v.setY(.44);
}
pig.setVelocity(v);
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
if(dist>8&&ready(leap,id,38)){
Vector v=p.getLocation().toVector().subtract(f.getLocation().toVector()).setY(0);
if(v.lengthSquared()>.1){
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
case "boots"->List.of("§aПрыгучесть I","§7Мягкий отскок при падении");
case "leggings"->List.of("§aСкорость I","§7Слизистый след");
case "wings"->List.of("§aПланирование как на элитрах");
default->List.of();
});
if(m instanceof LeatherArmorMeta leather)leather.setColor(Color.fromRGB(75,171,80));
if(kind.equals("boots")||kind.equals("leggings")||kind.equals("wings")){
EquippableComponent equip=m.getEquippable();
if(equip!=null){equip.setModel(new NamespacedKey("swampsky",kind.equals("wings")?"frog_wings":"frog_armor"));m.setEquippable(equip);}
}
it.setItemMeta(m);return it;
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
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void fall(EntityDamageEvent e){
if(!(e.getEntity() instanceof Player p)||e.getCause()!=EntityDamageEvent.DamageCause.FALL||!is(p.getInventory().getBoots(),"boots"))return;
e.setCancelled(true);
if(ready(bounce,p.getUniqueId(),15)){
p.setVelocity(p.getVelocity().setY(Math.min(1.05,.45+p.getFallDistance()*.07)));
p.getWorld().playSound(p.getLocation(),Sound.BLOCK_SLIME_BLOCK_FALL,.8f,1);
p.getWorld().spawnParticle(Particle.ITEM_SLIME,p.getLocation(),15,.3,.2,.3,.05);
}
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
// NONE avoids the default item-frame angle that made the wings look like sails.
entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
entity.setItemStack(item("pig_wing",1));
entity.setViewRange(1.6f);
entity.setTeleportDuration(3);
mark(entity,displayKey);
});
list.add(d.getUniqueId());
}
displays.put(pig.getUniqueId(),list);
}
Location at=pig.getLocation();
double yaw=Math.toRadians(at.getYaw());
double flap=Math.sin(tick*.10)*.10;
for(int j=0;j<2;j++){
Entity e=Bukkit.getEntity(list.get(j));
if(!(e instanceof ItemDisplay d))continue;
float side=j==0?-1f:1f;
// Place each wing close to the back/shoulder; left side mirrors the same image.
// Both wings use one source texture, not two separate PNG assets.
Location loc=at.clone().add(
Math.cos(yaw)*side*.47+Math.sin(yaw)*.10,
.84+Math.sin(tick*.10)*.035,
Math.sin(yaw)*side*.47-Math.cos(yaw)*.10
);
loc.setYaw(at.getYaw()+side*22f);
loc.setPitch(0f);
d.teleport(loc);
float pitch=(float)(-.12+flap*side);
Quaternionf angle=new Quaternionf().rotationXYZ(.20f*side,0f,pitch);
d.setTransformation(new Transformation(
new Vector3f(0f,0f,0f),
angle,
new Vector3f(side*.78f,.70f,.78f),
new Quaternionf()
));
}
}
@EventHandler public void playerInput(PlayerInputEvent e){inputCache.put(e.getPlayer().getUniqueId(),e.getInput());}
@EventHandler public void quit(PlayerQuitEvent e){inputCache.remove(e.getPlayer().getUniqueId());}
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
