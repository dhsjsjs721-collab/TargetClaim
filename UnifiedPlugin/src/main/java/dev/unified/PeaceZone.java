package dev.unified;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import java.util.*;

final class PeaceZone implements Listener {
private final UnifiedPlugin host;
private final Set<UUID> inside=new HashSet<>();
private final NamespacedKey giant=new NamespacedKey("swampsky","giant_frog");
PeaceZone(UnifiedPlugin host){this.host=host;}
void enable(){
Bukkit.getPluginManager().registerEvents(this,host);
for(World w:Bukkit.getWorlds())w.setGameRule(GameRules.REDUCED_DEBUG_INFO,true);
Bukkit.getScheduler().runTaskTimer(host,this::sweep,20L,40L);
host.getLogger().info("PeaceZone enabled: 500x500 around overworld spawn, 16 containers/chunk.");
}
boolean contains(Location l){
if(l==null||l.getWorld()==null||l.getWorld().getEnvironment()!=World.Environment.NORMAL||!l.getWorld().getName().equals(host.getConfig().getString("peace.world","world")))return false;
Location spawn=l.getWorld().getSpawnLocation();int radius=Math.max(16,Math.min(1000,host.getConfig().getInt("peace.radius",250)));
long x=(long)l.getBlockX()-spawn.getBlockX(),z=(long)l.getBlockZ()-spawn.getBlockZ();
return x>=-radius&&x<radius&&z>=-radius&&z<radius;
}
private boolean hostile(Entity e){return e instanceof Enemy||e instanceof Slime||e.getPersistentDataContainer().has(giant);}
private Player playerSource(Entity e){
if(e instanceof Player p)return p;
if(e instanceof Projectile q&&q.getShooter() instanceof Player p)return p;
if(e instanceof Tameable t&&t.getOwner() instanceof Player p)return p;
if(e instanceof TNTPrimed t&&t.getSource() instanceof Player p)return p;
if(e instanceof AreaEffectCloud c&&c.getSource() instanceof Player p)return p;
return null;
}
private boolean blocked(Player a,Player b){return a!=null&&b!=null&&a!=b&&(contains(a.getLocation())||contains(b.getLocation()));}
@EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){
if(e.getEntity() instanceof Player p&&blocked(playerSource(e.getDamager()),p))e.setCancelled(true);
else if(contains(e.getEntity().getLocation())&&hostile(e.getDamager()))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void explosions(EntityDamageEvent e){
if(contains(e.getEntity().getLocation())&&(e.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION||e.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void splash(PotionSplashEvent e){
Player p=playerSource(e.getEntity());for(LivingEntity l:e.getAffectedEntities())if(l instanceof Player v&&blocked(p,v))e.setIntensity(l,0);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void cloud(AreaEffectCloudApplyEvent e){
Player p=playerSource(e.getEntity());e.getAffectedEntities().removeIf(l->l instanceof Player v&&blocked(p,v));
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fishing(PlayerFishEvent e){if(e.getCaught() instanceof Player p&&blocked(e.getPlayer(),p))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void spawn(CreatureSpawnEvent e){if(contains(e.getLocation())&&hostile(e.getEntity()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void enter(EntityMoveEvent e){if(hostile(e.getEntity())&&contains(e.getTo())&&!contains(e.getFrom()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(EntityTeleportEvent e){if(hostile(e.getEntity())&&contains(e.getTo()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(hostile(e.getEntity())&&e.getTarget()!=null&&(contains(e.getEntity().getLocation())||contains(e.getTarget().getLocation())))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void prime(ExplosionPrimeEvent e){if(contains(e.getEntity().getLocation()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e){if(contains(e.getLocation()))e.setCancelled(true);else e.blockList().removeIf(b->contains(b.getLocation()));}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(BlockExplodeEvent e){if(contains(e.getBlock().getLocation()))e.setCancelled(true);else e.blockList().removeIf(b->contains(b.getLocation()));}
private boolean storage(Material m){String n=m.name();return n.equals("CHEST")||n.equals("TRAPPED_CHEST")||n.equals("BARREL")||n.endsWith("SHULKER_BOX")||n.equals("HOPPER")||n.equals("DISPENSER")||n.equals("DROPPER")||n.equals("CRAFTER")||n.equals("CHISELED_BOOKSHELF");}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(BlockPlaceEvent e){
Block b=e.getBlockPlaced();if(!contains(b.getLocation())||!storage(b.getType()))return;
int limit=Math.max(1,host.getConfig().getInt("peace.containers-per-chunk",16));int count=0;
for(var state:b.getChunk().getTileEntities())if(storage(state.getType())&&contains(state.getLocation())&&!state.getLocation().equals(b.getLocation()))count++;
if(count>=limit){e.setCancelled(true);e.getPlayer().sendMessage("§cМирная зона: лимит "+limit+" контейнеров на чанк.");}
}
@EventHandler public void quit(PlayerQuitEvent e){inside.remove(e.getPlayer().getUniqueId());}
private void sweep(){
for(Player p:Bukkit.getOnlinePlayers()){
boolean now=contains(p.getLocation()),was=inside.contains(p.getUniqueId());
if(now&&!was){inside.add(p.getUniqueId());p.sendActionBar(net.kyori.adventure.text.Component.text("Мирная зона · PvP отключено"));}
else if(!now&&was){inside.remove(p.getUniqueId());p.sendActionBar(net.kyori.adventure.text.Component.text("Дикая территория · PvP разрешено"));}
}
for(World w:Bukkit.getWorlds())if(w.getEnvironment()==World.Environment.NORMAL)for(LivingEntity e:w.getLivingEntities())if(hostile(e)&&contains(e.getLocation()))e.remove();
}
}
