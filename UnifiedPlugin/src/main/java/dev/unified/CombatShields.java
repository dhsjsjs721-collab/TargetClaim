package dev.unified;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

public final class CombatShields implements Listener {
private final UnifiedPlugin plugin;
private final NamespacedKey shieldKey=new NamespacedKey("minecraftunified","combat_shield");
private final Map<UUID,UUID> backDisplays=new HashMap<>();
private final Map<UUID,UUID> summonedOwners=new HashMap<>();
private final Map<UUID,Long> crusherCritReady=new HashMap<>(),crusherStun=new HashMap<>(),lastKnockback=new HashMap<>();
private final Set<UUID> processing=new HashSet<>();
private final Random random=new Random();
private BukkitTask refresh;
public CombatShields(UnifiedPlugin plugin){this.plugin=plugin;}
private ItemStack create(String kind){
ItemStack item=new ItemStack(Material.SHIELD);
ItemMeta meta=item.getItemMeta();
meta.getPersistentDataContainer().set(shieldKey,PersistentDataType.STRING,kind);
meta.setItemModel(new NamespacedKey("minecraftunified","shield_"+kind));
switch(kind){
case "wither" -> {
meta.setDisplayName("§5§lЩит язвителя");
meta.setLore(List.of("§7Блокирует и крадёт здоровье и сытость","§7Очищает отрицательные эффекты владельца","§7Похищает положительные эффекты врагов","§7Снаряды превращаются в временных помощников"));
}
case "turtle" -> {
meta.setDisplayName("§2§lЧерепаший щит");
meta.setLore(List.of("§7Защищает спину, если не поднят","§7Удвоенная прочность и сопротивление отбрасыванию","§7Отталкивает близких противников"));
if(meta instanceof org.bukkit.inventory.meta.Damageable damage)damage.setMaxDamage(Material.SHIELD.getMaxDurability()*2);
}
case "crusher" -> {
meta.setDisplayName("§6§lЩит крушителя");
meta.setLore(List.of("§7Можно бить как тяжёлым оружием","§7Каждый успешный блок ускоряет следующий крит","§7Не защищает от стрел и других снарядов","§7Коротко оглушает противника"));
}
}
item.setItemMeta(meta);
return item;
}
private String kind(ItemStack item){
if(item==null||item.getType()!=Material.SHIELD||!item.hasItemMeta())return null;
return item.getItemMeta().getPersistentDataContainer().get(shieldKey,PersistentDataType.STRING);
}
private String held(Player p){
String off=kind(p.getInventory().getItemInOffHand());
return off!=null?off:kind(p.getInventory().getItemInMainHand());
}
private boolean ally(Player p,LivingEntity target){
if(target==p)return true;
if(target instanceof Player other){
org.bukkit.scoreboard.Team team=p.getScoreboard().getEntryTeam(p.getName());
return team!=null&&team.equals(other.getScoreboard().getEntryTeam(other.getName()));
}
return false;
}
private LivingEntity source(Entity damager){
if(damager instanceof LivingEntity living)return living;
if(damager instanceof Projectile projectile&&projectile.getShooter() instanceof LivingEntity living)return living;
return null;
}
private boolean fromBehind(Player defender,LivingEntity attacker){
Vector to=attacker.getLocation().toVector().subtract(defender.getLocation().toVector()).setY(0);
Vector forward=defender.getLocation().getDirection().setY(0);
return to.lengthSquared()>.01&&forward.lengthSquared()>.01&&to.normalize().dot(forward.normalize())<-.25;
}
private void bloodSteal(Player defender,LivingEntity attacker){
if(ally(defender,attacker)||processing.contains(defender.getUniqueId()))return;
processing.add(defender.getUniqueId());
try{
double stolen=Math.min(5,Math.max(1,attacker.getHealth()-1));
attacker.damage(stolen,defender);
double maximum=defender.getAttribute(Attribute.MAX_HEALTH).getValue();
defender.setHealth(Math.min(maximum,defender.getHealth()+stolen));
if(attacker instanceof Player enemy){
int eaten=Math.min(4,enemy.getFoodLevel());
enemy.setFoodLevel(enemy.getFoodLevel()-eaten);
defender.setFoodLevel(Math.min(20,defender.getFoodLevel()+eaten));
transferPotions(defender,enemy);
}
defender.getWorld().spawnParticle(Particle.SOUL,defender.getEyeLocation(),12,.4,.4,.4,.025);
}finally{processing.remove(defender.getUniqueId());}
}
private boolean managedPotion(PotionEffectType type){
return type==PotionEffectType.STRENGTH||type==PotionEffectType.ABSORPTION
||type==PotionEffectType.HUNGER||type==PotionEffectType.WATER_BREATHING
||type==PotionEffectType.DOLPHINS_GRACE||type==PotionEffectType.FIRE_RESISTANCE
||type==PotionEffectType.INVISIBILITY||type==PotionEffectType.SPEED
||type==PotionEffectType.REGENERATION;
}
private boolean harmful(PotionEffectType type){
return type==PotionEffectType.POISON||type==PotionEffectType.WITHER||type==PotionEffectType.WEAKNESS
||type==PotionEffectType.SLOWNESS||type==PotionEffectType.MINING_FATIGUE
||type==PotionEffectType.BLINDNESS||type==PotionEffectType.NAUSEA;
}
private boolean positive(PotionEffectType type){
return type==PotionEffectType.RESISTANCE||type==PotionEffectType.HASTE
||type==PotionEffectType.JUMP_BOOST||type==PotionEffectType.NIGHT_VISION
||type==PotionEffectType.ABSORPTION||type==PotionEffectType.STRENGTH
||type==PotionEffectType.REGENERATION||type==PotionEffectType.SPEED;
}
private void transferPotions(Player defender,Player enemy){
// Keep talisman-driven permanent effects untouched. Only external short
// effects are movable, bounded to 5-20 seconds, without hidden 15-minute buffs.
for(PotionEffect effect:new ArrayList<>(defender.getActivePotionEffects())){
if(!harmful(effect.getType())||managedPotion(effect.getType()))continue;
int duration=Math.max(100,Math.min(400,effect.getDuration()));
defender.removePotionEffect(effect.getType());
enemy.addPotionEffect(new PotionEffect(effect.getType(),duration,effect.getAmplifier()));
}
for(PotionEffect effect:new ArrayList<>(enemy.getActivePotionEffects())){
if(!positive(effect.getType())||managedPotion(effect.getType()))continue;
int duration=Math.max(100,Math.min(400,effect.getDuration()));
enemy.removePotionEffect(effect.getType());
defender.addPotionEffect(new PotionEffect(effect.getType(),duration,effect.getAmplifier()));
}
}
private void convertProjectile(Player owner,Entity damager,LivingEntity attacker){
if(!(damager instanceof Projectile))return;
Location where=owner.getLocation().clone().add(owner.getLocation().getDirection().multiply(1.5));
if(!where.getBlock().isPassable())return;
Entity mob;
if(random.nextBoolean()){
Silverfish fish=owner.getWorld().spawn(where,Silverfish.class,spawn->{
spawn.setPersistent(false);spawn.setRemoveWhenFarAway(true);
});
if(attacker!=null&&attacker!=owner&&!ally(owner,attacker))fish.setTarget(attacker);
mob=fish;
}else{
Frog frog=owner.getWorld().spawn(where,Frog.class,spawn->spawn.setPersistent(false));
mob=frog;
}
summonedOwners.put(mob.getUniqueId(),owner.getUniqueId());
Bukkit.getScheduler().runTaskLater(plugin,()->{
summonedOwners.remove(mob.getUniqueId());
if(mob.isValid())mob.remove();
},160L);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void guard(EntityDamageByEntityEvent e){
if(!(e.getEntity() instanceof Player defender))return;
String type=held(defender);
if(type==null)return;
LivingEntity attacker=source(e.getDamager());
if(type.equals("turtle")&&!defender.isBlocking()&&attacker!=null&&fromBehind(defender,attacker)){
e.setCancelled(true);
defender.getWorld().playSound(defender,Sound.ITEM_SHIELD_BLOCK,.85f,.6f);
return;
}
if(!defender.isBlocking())return;
if(type.equals("crusher")){
if(e.getDamager() instanceof Projectile){
e.setCancelled(true);
// Crusher does not stop ranged attacks: pay an explicit hit next tick.
final double damage=Math.min(16,Math.max(2,e.getDamage()));
Bukkit.getScheduler().runTask(plugin,()->{
if(defender.isOnline()&&!defender.isDead()){
defender.setNoDamageTicks(0);
defender.damage(damage);
}
});
return;
}
crusherCritReady.put(defender.getUniqueId(),System.currentTimeMillis()+2500);
if(attacker!=null&&!ally(defender,attacker)){
attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,18,4));
}
return;
}
if(type.equals("wither")){
if(attacker!=null)bloodSteal(defender,attacker);
if(e.getDamager() instanceof Projectile)convertProjectile(defender,e.getDamager(),attacker);
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void heavyHit(EntityDamageByEntityEvent e){
if(!(e.getDamager() instanceof Player attacker))return;
if(!"crusher".equals(kind(attacker.getInventory().getItemInMainHand())))return;
if(e.getEntity()==attacker)return;
boolean critical=attacker.getFallDistance()>0&&!attacker.isOnGround();
double damage=critical?9.0:7.0;
if(System.currentTimeMillis()<crusherCritReady.getOrDefault(attacker.getUniqueId(),0L)){
damage+=2;
crusherCritReady.remove(attacker.getUniqueId());
}
e.setDamage(damage);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void protectOwner(EntityTargetLivingEntityEvent e){
UUID owner=summonedOwners.get(e.getEntity().getUniqueId());
if(owner!=null&&e.getTarget()!=null&&owner.equals(e.getTarget().getUniqueId()))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST)
public void reduceKnockback(PlayerVelocityEvent e){
Player player=e.getPlayer();
if(!"turtle".equals(held(player)))return;
e.setVelocity(e.getVelocity().multiply(.48));
}
private void clearDisplay(UUID playerId){
UUID id=backDisplays.remove(playerId);
if(id!=null){Entity ent=Bukkit.getEntity(id);if(ent!=null)ent.remove();}
}
private void tick(){
for(Player player:Bukkit.getOnlinePlayers()){
UUID id=player.getUniqueId();
boolean turtle="turtle".equals(held(player))&&!player.isBlocking();
if(!turtle){clearDisplay(id);continue;}
UUID previousDisplay=backDisplays.get(id);
Entity entity=previousDisplay==null?null:Bukkit.getEntity(previousDisplay);
ItemDisplay display;
if(!(entity instanceof ItemDisplay existing)||!entity.isValid()){
display=player.getWorld().spawn(player.getLocation(),ItemDisplay.class,d->{
d.setItemStack(create("turtle"));d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
d.setPersistent(false);d.setInvulnerable(true);d.setTeleportDuration(4);
});
backDisplays.put(id,display.getUniqueId());
}else display=existing;
Vector forward=player.getLocation().getDirection().setY(0);
if(forward.lengthSquared()<.01)forward=new Vector(0,0,1);
forward.normalize();
Location back=player.getLocation().clone().subtract(forward.clone().multiply(.30)).add(0,1.35,0);
back.setYaw(player.getLocation().getYaw()+180);
display.teleport(back);
for(Entity raw:player.getNearbyEntities(1.65,1.5,1.65)){
if(!(raw instanceof LivingEntity mob)||ally(player,mob)||raw instanceof ArmorStand)continue;
Vector away=mob.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
if(away.lengthSquared()<.01)continue;
Vector desired=away.normalize().multiply(.45).setY(.13);
if(!mob.getVelocity().isZero())desired.add(mob.getVelocity().multiply(.25));
mob.setVelocity(desired);
}
}
for(UUID id:new ArrayList<>(backDisplays.keySet()))if(Bukkit.getPlayer(id)==null)clearDisplay(id);
}
public void enable(){
for(String type:List.of("wither","turtle","crusher")){
NamespacedKey recipe=new NamespacedKey("minecraftunified","shield_"+type);
Bukkit.removeRecipe(recipe);
ShapedRecipe shaped=new ShapedRecipe(recipe,create(type));
shaped.shape("ABA","ASA","ACA");
Material a=type.equals("turtle")?Material.TURTLE_SCUTE:type.equals("wither")?Material.WITHER_ROSE:Material.IRON_INGOT;
Material b=type.equals("turtle")?Material.DEEPSLATE:type.equals("wither")?Material.GOLD_INGOT:Material.NETHERITE_SCRAP;
Material c=type.equals("turtle")?Material.DEEPSLATE:type.equals("wither")?Material.FERMENTED_SPIDER_EYE:Material.DIAMOND;
shaped.setIngredient('A',a);shaped.setIngredient('B',b);
shaped.setIngredient('S',Material.SHIELD);shaped.setIngredient('C',c);
Bukkit.addRecipe(shaped);
}
Bukkit.getPluginManager().registerEvents(this,plugin);
refresh=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5L,8L);
}
public void disable(){
if(refresh!=null)refresh.cancel();
for(UUID id:new ArrayList<>(backDisplays.keySet()))clearDisplay(id);
for(UUID id:new ArrayList<>(summonedOwners.keySet())){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
for(String type:List.of("wither","turtle","crusher"))Bukkit.removeRecipe(new NamespacedKey("minecraftunified","shield_"+type));
org.bukkit.event.HandlerList.unregisterAll(this);
}
public boolean command(CommandSender sender,String[] args){
if(args.length==0||!args[0].equalsIgnoreCase("give")){
sender.sendMessage("§6Щиты: §5wither §7(Язвителя), §2turtle §7(Черепахи), §6crusher §7(Крушителя)");
sender.sendMessage("§7/u shield give <ник> <wither|turtle|crusher> [количество]");
return true;
}
if(!sender.hasPermission("minecraftunified.shields.admin")){sender.sendMessage("§cНет прав.");return true;}
if(args.length<3){sender.sendMessage("§cИспользуй /u shield give <ник> <тип>");return true;}
Player player=Bukkit.getPlayerExact(args[1]);
if(player==null){sender.sendMessage("§cИгрок не найден.");return true;}
String type=args[2].toLowerCase(Locale.ROOT);
if(!List.of("wither","turtle","crusher").contains(type)){sender.sendMessage("§cНеизвестный щит.");return true;}
int amount=1;
if(args.length>=4)try{amount=Integer.parseInt(args[3]);}catch(NumberFormatException ignored){}
if(amount<1||amount>16){sender.sendMessage("§cКоличество от 1 до 16.");return true;}
ItemStack item=create(type);item.setAmount(amount);
for(ItemStack excess:player.getInventory().addItem(item).values())player.getWorld().dropItemNaturally(player.getLocation(),excess);
sender.sendMessage("§aВыдан "+type+" игроку "+player.getName());
return true;
}
}
