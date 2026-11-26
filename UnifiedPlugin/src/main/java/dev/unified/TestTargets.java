package dev.unified;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.util.*;

final class TestTargets implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey key;
private World world;
private final Map<String,Long> respawn=new HashMap<>();
private static final List<String> ROLES=List.of("immortal","respawn","attacker","equipment");
TestTargets(UnifiedPlugin host){this.host=host;key=new NamespacedKey(host,"test_target");}
void enable(){Bukkit.getPluginManager().registerEvents(this,host);Bukkit.getScheduler().runTaskTimer(host,this::tick,20L,20L);}
static boolean isTarget(LivingEntity e){return e.getWorld().getName().equals(TestWorld.WORLD)&&e.getPersistentDataContainer().has(new NamespacedKey("minecraftunified","test_target"),PersistentDataType.STRING);}
private String role(Entity e){return e.getWorld().getName().equals(TestWorld.WORLD)?e.getPersistentDataContainer().get(key,PersistentDataType.STRING):null;}
private Location home(String role){return switch(role){case "immortal"->new Location(world,7.5,81,-9.5,90,0);case "respawn"->new Location(world,7.5,81,-3.5,90,0);case "equipment"->new Location(world,7.5,81,2.5,90,0);default->new Location(world,7.5,81,9.5,90,0);};}
private String name(String r){return switch(r){case "immortal"->"§bБессмертный манекен";case "respawn"->"§dВозрождающийся NPC";case "equipment"->"§6NPC для экипировки";default->"§cАтакующий моб";};}
void setup(World w){world=w;for(String r:ROLES)home(r).getChunk().load();tick();}
private LivingEntity spawn(String r){
Location l=home(r);
LivingEntity e;
if(r.equals("attacker"))e=world.spawn(l,Zombie.class,z->{z.setAdult();z.setShouldBurnInDay(false);z.setCanPickupItems(false);z.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));z.getEquipment().setItemInMainHandDropChance(0);});
else e=world.spawn(l,Mannequin.class,n->{n.setImmovable(true);n.setGravity(false);n.setDescription(Component.text(r.equals("equipment")?"ПКМ — выдать/забрать предмет":"Тестовая цель"));});
e.getPersistentDataContainer().set(key,PersistentDataType.STRING,r);e.setCustomName(name(r));e.setCustomNameVisible(true);e.setPersistent(true);e.setRemoveWhenFarAway(false);
var max=e.getAttribute(Attribute.MAX_HEALTH);if(max!=null){max.setBaseValue(20);e.setHealth(max.getValue());}
return e;
}
private void tick(){
if(world==null)return;
Map<String,LivingEntity> found=new HashMap<>();
for(LivingEntity e:world.getLivingEntities()){String r=role(e);if(r==null||!ROLES.contains(r))continue;if(found.putIfAbsent(r,e)!=null)e.remove();}
long now=System.currentTimeMillis();
for(String r:ROLES){LivingEntity e=found.get(r);if(e==null||!e.isValid()||e.isDead()){if(now>=respawn.getOrDefault(r,0L)&&world.isChunkLoaded(home(r).getBlockX()>>4,home(r).getBlockZ()>>4)){e=spawn(r);respawn.remove(r);}else continue;}
if(r.equals("attacker")&&e instanceof Zombie z){if(z.getLocation().distanceSquared(home(r))>18*18||z.getLocation().getY()<78){z.teleport(home(r));z.setTarget(null);}if(z.hasAI()){Player target=world.getPlayers().stream().filter(p->p.isOp()&&p.getGameMode()==GameMode.SURVIVAL&&p.getLocation().distanceSquared(z.getLocation())<16*16).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(z.getLocation()))).orElse(null);z.setTarget(target);}}
else if(e.getLocation().distanceSquared(home(r))>4)e.teleport(home(r));
}
}
int count(){if(world==null)return 0;return (int)world.getLivingEntities().stream().filter(TestTargets::isTarget).count();}
void reset(){if(world==null)return;for(LivingEntity e:world.getLivingEntities())if(isTarget(e))e.remove();respawn.clear();tick();}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent e){
if(!(e.getEntity() instanceof LivingEntity l))return;String r=role(l);if(r==null)return;
if(e instanceof EntityDamageByEntityEvent d&&d.getDamager() instanceof Player p){double after=Math.max(0,l.getHealth()-e.getFinalDamage());p.sendActionBar(Component.text(String.format(Locale.ROOT,"Урон: %.1f · HP: %.1f/%.1f",e.getFinalDamage(),after,l.getMaxHealth())));}
if(r.equals("immortal")&&e.getFinalDamage()>=l.getHealth()){e.setCancelled(true);l.setHealth(l.getMaxHealth());l.getWorld().spawnParticle(Particle.HEART,l.getLocation().add(0,1,0),4,.25,.3,.25);}
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void heal(EntityDamageEvent e){if(e.getEntity() instanceof LivingEntity l&&"immortal".equals(role(l)))Bukkit.getScheduler().runTask(host,()->{if(l.isValid()&&!l.isDead())l.setHealth(l.getMaxHealth());});}
@EventHandler(priority=EventPriority.HIGHEST) public void death(EntityDeathEvent e){String r=role(e.getEntity());if(r==null)return;e.getDrops().clear();e.setDroppedExp(0);respawn.put(r,System.currentTimeMillis()+3000L);}
private EquipmentSlot slot(ItemStack i,boolean sneaking){String n=i.getType().name();if(n.endsWith("_HELMET")||n.endsWith("_HEAD")||n.endsWith("_SKULL")||n.equals("CARVED_PUMPKIN"))return EquipmentSlot.HEAD;if(n.endsWith("_CHESTPLATE")||n.equals("ELYTRA"))return EquipmentSlot.CHEST;if(n.endsWith("_LEGGINGS"))return EquipmentSlot.LEGS;if(n.endsWith("_BOOTS"))return EquipmentSlot.FEET;return sneaking?EquipmentSlot.OFF_HAND:EquipmentSlot.HAND;}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void equip(PlayerInteractEntityEvent e){
if(!"equipment".equals(role(e.getRightClicked()))||!(e.getRightClicked() instanceof LivingEntity l)||e.getHand()!=EquipmentSlot.HAND)return;e.setCancelled(true);Player p=e.getPlayer();if(!p.isOp())return;EntityEquipment equipment=l.getEquipment();if(equipment==null)return;
ItemStack hand=p.getInventory().getItemInMainHand();
if(hand.getType().isAir()){for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){ItemStack old=equipment.getItem(slot);if(!old.getType().isAir()){equipment.setItem(slot,new ItemStack(Material.AIR));for(ItemStack rest:p.getInventory().addItem(old).values())world.dropItemNaturally(p.getLocation(),rest);}}p.sendMessage("§aЭкипировка NPC возвращена.");return;}
EquipmentSlot slot=slot(hand,p.isSneaking());ItemStack old=equipment.getItem(slot),one=hand.asOne();equipment.setItem(slot,one);hand.setAmount(hand.getAmount()-1);p.getInventory().setItemInMainHand(hand.getAmount()>0?hand:new ItemStack(Material.AIR));if(!old.getType().isAir())for(ItemStack rest:p.getInventory().addItem(old).values())world.dropItemNaturally(p.getLocation(),rest);p.sendMessage("§aNPC получил предмет в слот "+slot.name()+".");
}
}
