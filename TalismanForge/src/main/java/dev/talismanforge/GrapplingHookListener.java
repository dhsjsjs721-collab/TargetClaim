package dev.talismanforge;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Standalone hook with custom visual projectile; never creates a fishing bobber or white fishing line. */
public final class GrapplingHookListener implements Listener {
private static final long COOLDOWN=15_000L;
private static final int WEAR_PER_CAST=8;
private static final double RANGE=36.0, FLIGHT_SPEED=2.4;
private static final int MAX_LINKS=48;
private static final double LINK_STEP=0.72;
private static final NamespacedKey IDLE=new NamespacedKey("talismanforge","grappling_hook");
private static final NamespacedKey CAST=new NamespacedKey("talismanforge","grappling_hook_cast");
private static final NamespacedKey TIP=new NamespacedKey("talismanforge","grappling_hook_tip");
private static final NamespacedKey LINK=new NamespacedKey("talismanforge","grappling_chain_link");
private final JavaPlugin plugin;
private final NamespacedKey hookKey;
private final Map<UUID,Long> cooldowns=new HashMap<>();
private final Map<UUID,Shot> shots=new HashMap<>();

private enum Phase { FLY, PULL }
private static final class Shot {
UUID shooterId, victimId;
World world;
Location location;
Vector direction;
ItemDisplay projectile;
List<BlockDisplay> links=new ArrayList<>();
Phase phase=Phase.FLY;
double traveled;
int age;
}

public GrapplingHookListener(JavaPlugin plugin){
this.plugin=plugin;
hookKey=new NamespacedKey(plugin,"grappling_hook");
ShapedRecipe r=new ShapedRecipe(new NamespacedKey(plugin,"hook_recipe"),newHook());
r.shape("ISI","SNS","ITI");
r.setIngredient('I',Material.IRON_INGOT);
r.setIngredient('S',Material.STRING);
r.setIngredient('N',Material.IRON_NUGGET);
r.setIngredient('T',Material.STICK);
Bukkit.addRecipe(r);
Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1L,1L);
}

public ItemStack createHookItem(){return newHook();}
private ItemStack newHook(){
ItemStack i=new ItemStack(Material.FISHING_ROD);
ItemMeta m=i.getItemMeta();
m.setDisplayName("§7§lКрюк-кошка");
m.setLore(List.of("§7ПКМ — бросить крюк на цепи","§7Притягивает мобов и игроков к тебе","§7Дальность: §f36 блоков","§7КД: §d15 секунд","§7Ресурс: §f8 бросков", "§8Без аметиста"));
m.setItemModel(IDLE);
m.getPersistentDataContainer().set(hookKey,PersistentDataType.BYTE,(byte)1);
i.setItemMeta(m);
return i;
}

private boolean special(ItemStack i){
return i!=null&&i.getType()==Material.FISHING_ROD&&i.hasItemMeta()&&
Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(hookKey,PersistentDataType.BYTE));
}

private ItemStack displayItem(NamespacedKey key){
ItemStack i=new ItemStack(Material.IRON_NUGGET);
ItemMeta m=i.getItemMeta();
m.setItemModel(key);
i.setItemMeta(m);
return i;
}

private ItemDisplay display(World world,Location position,NamespacedKey model,float scale){
return world.spawn(position,ItemDisplay.class,d->{
d.setItemStack(displayItem(model));
d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
d.setBillboard(Display.Billboard.CENTER);
d.setGravity(false);
d.setInvulnerable(true);
d.setPersistent(false);
d.setTeleportDuration(1);
d.setTransformation(new Transformation(new Vector3f(0,0,0),new Quaternionf(),new Vector3f(scale,scale,scale),new Quaternionf()));
});
}

private void handModel(Player p,NamespacedKey name){
ItemStack i=p.getInventory().getItemInMainHand();
if(!special(i))return;
ItemMeta m=i.getItemMeta();
m.setItemModel(name);
i.setItemMeta(m);
p.getInventory().setItemInMainHand(i);
}

private void restoreModels(Player p){
ItemStack[] slots=p.getInventory().getContents();
for(int n=0;n<slots.length;n++){
ItemStack i=slots[n];if(!special(i))continue;
ItemMeta m=i.getItemMeta();
if(!CAST.equals(m.getItemModel()))continue;
m.setItemModel(IDLE);i.setItemMeta(m);slots[n]=i;
}
p.getInventory().setContents(slots);
ItemStack off=p.getInventory().getItemInOffHand();
if(special(off)&&CAST.equals(off.getItemMeta().getItemModel())){
ItemMeta meta=off.getItemMeta();meta.setItemModel(IDLE);off.setItemMeta(meta);p.getInventory().setItemInOffHand(off);
}
}

private void reduceDurability(Player p){
ItemStack i=p.getInventory().getItemInMainHand();
if(!special(i))return;
ItemMeta m=i.getItemMeta();
if(!(m instanceof Damageable d))return;
int newDamage=d.getDamage()+WEAR_PER_CAST;
if(newDamage>=Material.FISHING_ROD.getMaxDurability()){
p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
p.getWorld().playSound(p,Sound.ENTITY_ITEM_BREAK,0.9f,1.2f);
p.sendActionBar(Component.text("Крюк сломался",NamedTextColor.RED));
}else{
d.setDamage(newDamage);
i.setItemMeta(m);p.getInventory().setItemInMainHand(i);
}
}

@EventHandler(priority=EventPriority.HIGHEST)
public void onInteract(PlayerInteractEvent e){
if(e.getHand()!=EquipmentSlot.HAND)return;
if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
Player p=e.getPlayer();
if(!special(p.getInventory().getItemInMainHand()))return;
e.setCancelled(true);
e.setUseInteractedBlock(Event.Result.DENY);
e.setUseItemInHand(Event.Result.DENY);
if(shots.containsKey(p.getUniqueId())){p.sendActionBar(Component.text("Крюк уже летит",NamedTextColor.GRAY));return;}
long now=System.currentTimeMillis();
long left=cooldowns.getOrDefault(p.getUniqueId(),0L)-now;
if(left>0){p.sendActionBar(Component.text("Крюк: "+((left+999)/1000)+" сек.",NamedTextColor.LIGHT_PURPLE));return;}

Shot shot=new Shot();
shot.shooterId=p.getUniqueId();
shot.world=p.getWorld();
shot.location=p.getEyeLocation().clone().add(p.getEyeLocation().getDirection().normalize().multiply(1.1));
shot.direction=p.getEyeLocation().getDirection().normalize();
shot.projectile=display(shot.world,shot.location,TIP,.30f);
shots.put(p.getUniqueId(),shot);
cooldowns.put(p.getUniqueId(),now+COOLDOWN);
reduceDurability(p);
handModel(p,CAST);
p.getWorld().playSound(p,Sound.ITEM_CROSSBOW_SHOOT,.8f,1.25f);
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void blockVanillaFishing(PlayerFishEvent e){
if(special(e.getPlayer().getInventory().getItemInMainHand()))e.setCancelled(true);
}

private boolean isValidTarget(Player owner,Entity e){
if(!(e instanceof LivingEntity living)||!living.isValid()||living.isDead()||e==owner||e instanceof ArmorStand)return false;
if(e instanceof Player other){
if(other.getGameMode()==GameMode.CREATIVE||other.getGameMode()==GameMode.SPECTATOR)return false;
Team a=owner.getScoreboard().getEntryTeam(owner.getName());
Team b=other.getScoreboard().getEntryTeam(other.getName());
if(a!=null&&a==b&&!a.allowFriendlyFire())return false;
}
return true;
}

private void tick(){
for(Shot s:new ArrayList<>(shots.values())){
Player owner=Bukkit.getPlayer(s.shooterId);
if(owner==null||!owner.isOnline()||owner.isDead()||!owner.getWorld().equals(s.world)||s.age++>65){stop(s);continue;}
if(s.phase==Phase.FLY){
fly(owner,s);
}else{
pull(owner,s);
}
if(shots.get(s.shooterId)!=s)continue;
if(s.projectile!=null&&s.projectile.isValid())s.projectile.teleport(s.location);
updateLinks(owner,s);
}
}

private void fly(Player p,Shot s){
double distance=Math.min(FLIGHT_SPEED,RANGE-s.traveled);
if(distance<.03){stop(s);return;}
RayTraceResult wall=s.world.rayTraceBlocks(s.location,s.direction,distance,FluidCollisionMode.NEVER,true);
RayTraceResult entity=s.world.rayTraceEntities(s.location,s.direction,distance,.7,e->isValidTarget(p,e));
double wallDist=wall==null?Double.POSITIVE_INFINITY:wall.getHitPosition().distance(s.location.toVector());
double entityDist=entity==null?Double.POSITIVE_INFINITY:entity.getHitPosition().distance(s.location.toVector());
if(entity!=null&&entityDist<=wallDist){
Entity victim=entity.getHitEntity();
s.victimId=victim.getUniqueId();
s.location=victim.getLocation().clone().add(0,Math.min(1.2,victim.getHeight()*.65),0);
s.phase=Phase.PULL;
s.world.playSound(s.location,Sound.BLOCK_CHAIN_PLACE,1.0f,1.0f);
return;
}
if(wall!=null){s.location=wall.getHitPosition().toLocation(s.world);stop(s);return;}
s.location.add(s.direction.clone().multiply(distance));
s.traveled+=distance;
if(s.traveled>=RANGE)stop(s);
}

private void pull(Player p,Shot s){
Entity entity=Bukkit.getEntity(s.victimId);
if(!(entity instanceof LivingEntity target)||!target.isValid()||target.isDead()||!target.getWorld().equals(s.world)){
stop(s);return;
}
s.location=target.getLocation().clone().add(0,Math.min(1.2,target.getHeight()*.65),0);
Vector v=p.getLocation().clone().add(0,1,0).toVector().subtract(s.location.toVector());
double len=v.length();
if(len<2.6||s.age>51){
target.setFallDistance(0);
s.world.playSound(s.location,Sound.BLOCK_CHAIN_BREAK,.7f,1.2f);
stop(s);return;
}
target.setVelocity(v.normalize().multiply(Math.min(1.75,Math.max(.6,len*.38))));
target.setFallDistance(0);
}

private BlockDisplay chainDisplay(World world,Location at){
return world.spawn(at,BlockDisplay.class,d->{
d.setBlock(Bukkit.createBlockData(Material.CHAIN));
d.setBillboard(Display.Billboard.FIXED);
d.setGravity(false);
d.setInvulnerable(true);
d.setPersistent(false);
d.setTeleportDuration(1);
});
}

private void updateLinks(Player owner,Shot shot){
Location from=owner.getEyeLocation().clone().add(owner.getEyeLocation().getDirection().multiply(.9)).add(0,-.30,0);
Location to=shot.location;
Vector separation=to.toVector().subtract(from.toVector());
double dist=separation.length();
int count=Math.min(MAX_LINKS,Math.max(0,(int)Math.floor((dist-.6)/LINK_STEP)));
while(shot.links.size()<count)shot.links.add(chainDisplay(shot.world,from));
while(shot.links.size()>count){
BlockDisplay d=shot.links.remove(shot.links.size()-1);if(d.isValid())d.remove();
}
if(count==0)return;
Vector dir=separation.normalize();
Vector3f direction=new Vector3f((float)dir.getX(),(float)dir.getY(),(float)dir.getZ());
Quaternionf rotation=new Quaternionf().rotationTo(new Vector3f(0,1,0),direction);
Vector3f scale=new Vector3f(.42f,.70f,.42f);
// BlockDisplay geometry is located at the block corner: compensate for the rotated center.
Vector3f translation=new Vector3f(.21f,.35f,.21f);
rotation.transform(translation);translation.negate();
for(int i=0;i<count;i++){
double distance=(i+1)*LINK_STEP;
Location at=from.clone().add(dir.clone().multiply(distance));
BlockDisplay d=shot.links.get(i);
if(!d.isValid())continue;
d.teleport(at);
d.setTransformation(new Transformation(new Vector3f(translation),new Quaternionf(rotation),new Vector3f(scale),new Quaternionf()));
}
}

private void stop(Shot s){
if(shots.remove(s.shooterId,s)){
if(s.projectile!=null&&s.projectile.isValid())s.projectile.remove();
for(BlockDisplay d:s.links)if(d.isValid())d.remove();
s.links.clear();
Player p=Bukkit.getPlayer(s.shooterId);
if(p!=null)restoreModels(p);
}
}

@EventHandler public void quit(PlayerQuitEvent e){
Shot s=shots.get(e.getPlayer().getUniqueId());if(s!=null)stop(s);
restoreModels(e.getPlayer());
}

@EventHandler public void death(PlayerDeathEvent e){
Shot s=shots.get(e.getEntity().getUniqueId());if(s!=null)stop(s);
for(ItemStack i:e.getDrops()){
if(!special(i)||!CAST.equals(i.getItemMeta().getItemModel()))continue;
ItemMeta m=i.getItemMeta();m.setItemModel(IDLE);i.setItemMeta(m);
}
}

@EventHandler public void drop(PlayerDropItemEvent e){
ItemStack i=e.getItemDrop().getItemStack();
if(!special(i)||!CAST.equals(i.getItemMeta().getItemModel()))return;
ItemMeta m=i.getItemMeta();m.setItemModel(IDLE);i.setItemMeta(m);e.getItemDrop().setItemStack(i);
}

public void shutdown(){
for(Shot s:new ArrayList<>(shots.values()))stop(s);
shots.clear();
}
}