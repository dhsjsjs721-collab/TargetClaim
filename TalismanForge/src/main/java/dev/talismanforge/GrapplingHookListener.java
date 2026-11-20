package dev.talismanforge;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
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
private static final long COOLDOWN=21_000L;
private static final int HOOK_COOLDOWN_TICKS=420;
private static final int PEARL_COOLDOWN_TICKS=200;
private static final NamespacedKey HOOK_COOLDOWN_GROUP=new NamespacedKey("talismanforge","grappling_hook_cooldown");
private static final int WEAR_PER_CAST=1;
private static final double RANGE=36.0, FLIGHT_SPEED=2.4;
private static final int MAX_LINKS=24;
private static final double LINK_STEP=1.28;
private static final NamespacedKey IDLE=new NamespacedKey("talismanforge","grappling_hook");
private static final NamespacedKey CAST=new NamespacedKey("talismanforge","grappling_hook_cast");
private static final NamespacedKey TIP=new NamespacedKey("talismanforge","grappling_hook_tip");
private static final NamespacedKey LINK=new NamespacedKey("talismanforge","grappling_chain_link");
private final JavaPlugin plugin;
private final NamespacedKey hookKey,magnetKey=new NamespacedKey("talismanforge","grappling_magnet");
private final Map<UUID,Long> cooldowns=new HashMap<>();
private final Map<UUID,Shot> shots=new HashMap<>();
private final Set<UUID> suppressedCombat=new HashSet<>();

private enum Phase { FLY, PULL }
private static final class Shot {
UUID shooterId, victimId;
World world;
Location location;
Vector direction;
ItemDisplay projectile;
List<ItemDisplay> links=new ArrayList<>();
Phase phase=Phase.FLY;
double traveled;
boolean magnet;
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
m.setLore(List.of("§7ПКМ — бросить крюк на цепи","§7Притягивает мобов и игроков к тебе","§7Дальность: §f36 блоков","§7КД: §d21 секунда","§7Урон: §f6 • Замедление II", "§8Без аметиста"));
m.setItemModel(IDLE);
UseCooldownComponent cooldown=m.getUseCooldown();
cooldown.setCooldownSeconds(21f);
cooldown.setCooldownGroup(HOOK_COOLDOWN_GROUP);
m.setUseCooldown(cooldown);
m.getPersistentDataContainer().set(hookKey,PersistentDataType.BYTE,(byte)1);
i.setItemMeta(m);
return i;
}

private boolean special(ItemStack i){
return i!=null&&i.getType()==Material.FISHING_ROD&&i.hasItemMeta()&&
Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(hookKey,PersistentDataType.BYTE));
}

/** Migrates hooks crafted before v2.5.2 to a dedicated cooldown group. */
private void migrateOldHook(Player p){
ItemStack item=p.getInventory().getItemInMainHand();
if(!special(item))return;
ItemMeta meta=item.getItemMeta();
UseCooldownComponent cooldown=meta.getUseCooldown();
if(HOOK_COOLDOWN_GROUP.equals(cooldown.getCooldownGroup()) && Math.abs(cooldown.getCooldownSeconds()-21f)<.01f)return;
cooldown.setCooldownSeconds(21f);
cooldown.setCooldownGroup(HOOK_COOLDOWN_GROUP);
meta.setUseCooldown(cooldown);
item.setItemMeta(meta);
p.getInventory().setItemInMainHand(item);
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
if(shots.containsKey(p.getUniqueId()))return;
migrateOldHook(p);
long now=System.currentTimeMillis();
long left=cooldowns.getOrDefault(p.getUniqueId(),0L)-now;
if(left>0||p.hasCooldown(p.getInventory().getItemInMainHand()))return;

Shot shot=new Shot();
shot.shooterId=p.getUniqueId();
shot.world=p.getWorld();
shot.location=p.getEyeLocation().clone().add(p.getEyeLocation().getDirection().normalize().multiply(1.1));
shot.direction=p.getEyeLocation().getDirection().normalize();
shot.magnet=p.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer().has(magnetKey,PersistentDataType.BYTE);
shot.projectile=display(shot.world,shot.location,TIP,.30f);
shots.put(p.getUniqueId(),shot);
cooldowns.put(p.getUniqueId(),now+COOLDOWN);
handModel(p,CAST);
// Native gray radial cooldown indicator, shared only with other custom hooks.
p.setCooldown(newHook(),HOOK_COOLDOWN_TICKS);
p.getWorld().playSound(p,Sound.ITEM_CROSSBOW_SHOOT,.8f,1.25f);
}

/** Extends the vanilla pearl cooldown from 1 second to 10 seconds. */
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void pearlLaunch(ProjectileLaunchEvent event){
if(!(event.getEntity() instanceof EnderPearl pearl)||!(pearl.getShooter() instanceof Player player))return;
// Schedule after the vanilla launch handler so vanilla's one-second cooldown cannot override this.
Bukkit.getScheduler().runTask(plugin,()->{
if(player.isOnline())player.setCooldown(Material.ENDER_PEARL,PEARL_COOLDOWN_TICKS);
});
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
if(owner==null||!owner.isOnline()||owner.isDead()||!owner.getWorld().equals(s.world)||s.age++>46){stop(s);continue;}
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
if(s.magnet){
LivingEntity chosen=null;double best=5.5*5.5;
for(Entity entity:p.getNearbyEntities(30,20,30)){
if(!isValidTarget(p,entity)||!(entity instanceof LivingEntity living))continue;
Vector toward=entity.getLocation().toVector().add(new Vector(0,.7,0)).subtract(s.location.toVector());
if(toward.lengthSquared()>.01&&toward.lengthSquared()<best&&toward.clone().normalize().dot(s.direction)>.90){
chosen=living;best=toward.lengthSquared();
}
}
if(chosen!=null){
Vector to=chosen.getEyeLocation().toVector().subtract(s.location.toVector());
if(to.lengthSquared()>.01)s.direction.multiply(.80).add(to.normalize().multiply(.20)).normalize();
}
}
double distance=Math.min(FLIGHT_SPEED,RANGE-s.traveled);
if(distance<.03){stop(s);return;}
RayTraceResult wall=s.world.rayTraceBlocks(s.location,s.direction,distance,FluidCollisionMode.NEVER,true);
RayTraceResult entity=s.world.rayTraceEntities(s.location,s.direction,distance,.7,e->isValidTarget(p,e));
double wallDist=wall==null?Double.POSITIVE_INFINITY:wall.getHitPosition().distance(s.location.toVector());
double entityDist=entity==null?Double.POSITIVE_INFINITY:entity.getHitPosition().distance(s.location.toVector());
if(entity!=null&&entityDist<=wallDist){
Entity victim=entity.getHitEntity();
if(victim instanceof Player defender&&defender.isBlocking()&&hasShield(defender)){
reduceDurability(p);
p.getWorld().playSound(defender.getLocation(),Sound.ITEM_SHIELD_BLOCK,.9f,.75f);
defender.sendActionBar(Component.text("Крюк отражён щитом",NamedTextColor.AQUA));
stop(s);return;
}
s.victimId=victim.getUniqueId();
s.location=victim.getLocation().clone().add(0,Math.min(1.2,victim.getHeight()*.65),0);
s.phase=Phase.PULL;
if(victim instanceof LivingEntity living){
living.damage(6.0,p);
living.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,65,1));
}
suppressedCombat.add(p.getUniqueId());suppressedCombat.add(victim.getUniqueId());
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
if(len<2.6||s.age>39){
target.setFallDistance(0);
s.world.playSound(s.location,Sound.BLOCK_CHAIN_BREAK,.7f,1.2f);
stop(s);return;
}
target.setVelocity(v.normalize().multiply(Math.min(1.05,Math.max(.3,len*.16))));
target.setFallDistance(0);
}

private ItemDisplay chainDisplay(World world,Location at){
// One lightweight custom chain-link sprite per 1.28 blocks; no giant
// transparent iron-chain blocks following the player.
return world.spawn(at,ItemDisplay.class,d->{
d.setItemStack(displayItem(LINK));
d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
d.setBillboard(Display.Billboard.FIXED);
d.setGravity(false);d.setInvulnerable(true);d.setPersistent(false);
d.setTeleportDuration(2);d.setViewRange(1.3f);
});
}
private void updateLinks(Player owner,Shot shot){
Location from=owner.getEyeLocation().clone().add(owner.getEyeLocation().getDirection().multiply(.45)).add(0,-.32,0);
Location to=shot.location;
Vector separation=to.toVector().subtract(from.toVector());
double dist=separation.length();
int count=Math.min(MAX_LINKS,Math.max(0,(int)Math.floor((dist-.4)/LINK_STEP)));
while(shot.links.size()<count)shot.links.add(chainDisplay(shot.world,from));
while(shot.links.size()>count){
ItemDisplay link=shot.links.remove(shot.links.size()-1);
if(link.isValid())link.remove();
}
if(count==0)return;
Vector axis=separation.normalize();
Vector3f direction=new Vector3f((float)axis.getX(),(float)axis.getY(),(float)axis.getZ());
Quaternionf rotation=new Quaternionf().rotationTo(new Vector3f(0,1,0),direction);
for(int i=0;i<count;i++){
double distance=(i+.55)*LINK_STEP;
Location pos=from.clone().add(axis.clone().multiply(distance));
ItemDisplay link=shot.links.get(i);
if(!link.isValid())continue;
if(!pos.getWorld().isChunkLoaded(pos.getBlockX()>>4,pos.getBlockZ()>>4))continue;
link.teleport(pos);
link.setTransformation(new Transformation(new Vector3f(0f,0f,0f),
new Quaternionf(rotation),new Vector3f(.31f,.55f,.31f),new Quaternionf()));
}
}

private void stop(Shot s){
if(shots.remove(s.shooterId,s)){
suppressedCombat.remove(s.shooterId);
if(s.victimId!=null)suppressedCombat.remove(s.victimId);
if(s.projectile!=null&&s.projectile.isValid())s.projectile.remove();
for(ItemDisplay d:s.links)if(d.isValid())d.remove();
s.links.clear();
Player p=Bukkit.getPlayer(s.shooterId);
if(p!=null)restoreModels(p);
}
}

private boolean hasShield(Player player){
ItemStack main=player.getInventory().getItemInMainHand();
ItemStack off=player.getInventory().getItemInOffHand();
return main.getType()==Material.SHIELD||off.getType()==Material.SHIELD;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void noAttackWhilePulling(org.bukkit.event.entity.EntityDamageByEntityEvent event){
if(suppressedCombat.contains(event.getDamager().getUniqueId())||suppressedCombat.contains(event.getEntity().getUniqueId()))event.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST)
public void prepareMagnet(org.bukkit.event.inventory.PrepareAnvilEvent event){
ItemStack first=event.getInventory().getItem(0),second=event.getInventory().getItem(1);
if(!special(first)||second==null||second.getType()!=Material.LODESTONE)return;
ItemStack upgraded=first.clone();
ItemMeta meta=upgraded.getItemMeta();
if(meta.getPersistentDataContainer().has(magnetKey,PersistentDataType.BYTE))return;
meta.getPersistentDataContainer().set(magnetKey,PersistentDataType.BYTE,(byte)1);
meta.setDisplayName("§b§lМагнитный крюк");
List<String> lore=meta.getLore()==null?new ArrayList<>():new ArrayList<>(meta.getLore());
lore.add("§bМагнит I: лёгкое наведение на ближайшую цель");
meta.setLore(lore);
meta.setEnchantmentGlintOverride(true);
upgraded.setItemMeta(meta);
event.setResult(upgraded);
event.getInventory().setRepairCost(8);
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