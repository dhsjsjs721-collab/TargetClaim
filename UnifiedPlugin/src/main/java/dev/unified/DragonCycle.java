package dev.unified;
import org.bukkit.*;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.CommandSender;
import io.papermc.paper.event.block.DragonEggFormEvent;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class DragonCycle implements Listener {
private static final long DELAY=5L*60*60*1000;
private final UnifiedPlugin host;
private final NamespacedKey due=new NamespacedKey("minecraftunified","dragon_respawn_at");
private final NamespacedKey last=new NamespacedKey("minecraftunified","dragon_last_death");
private final Map<UUID,Boolean> eggRolls=new HashMap<>();
DragonCycle(UnifiedPlugin h){host=h;}
void enable(){Bukkit.getPluginManager().registerEvents(this,host);Bukkit.getScheduler().runTaskTimer(host,this::tick,100L,200L);host.getLogger().info("DragonCycle: first egg 100%, subsequent eggs 50%, automatic respawn after five hours.");}
static boolean eggChance(boolean first,double roll){return first||roll<.5;}
private long due(World w){return w.getPersistentDataContainer().getOrDefault(due,PersistentDataType.LONG,0L);}
private void schedule(World w){long time=System.currentTimeMillis();w.getPersistentDataContainer().set(due,PersistentDataType.LONG,time+DELAY);w.getPersistentDataContainer().set(last,PersistentDataType.LONG,time);}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void death(EntityDeathEvent e){if(e.getEntity() instanceof EnderDragon d&&d.getDragonBattle()!=null&&d.getWorld().getEnvironment()==World.Environment.THE_END){World w=d.getWorld();schedule(w);eggRolls.remove(w.getUID());Bukkit.broadcastMessage("§5Дракон повержен. §7Следующее возрождение через 5 часов.");}}
@EventHandler(priority=EventPriority.HIGHEST) public void egg(DragonEggFormEvent e){World w=e.getBlock().getWorld();if(w.getEnvironment()!=World.Environment.THE_END)return;boolean reward=eggRolls.computeIfAbsent(w.getUID(),id->eggChance(!e.getDragonBattle().hasBeenPreviouslyKilled(),ThreadLocalRandom.current().nextDouble()));e.setCancelled(!reward);if(reward&&e.getBlock().getType()==Material.DRAGON_EGG){e.setCancelled(true);w.dropItemNaturally(e.getBlock().getLocation().add(.5,1,.5),new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG));}if(due(w)==0)schedule(w);}
private void tick(){long now=System.currentTimeMillis();for(World w:Bukkit.getWorlds()){if(w.getEnvironment()!=World.Environment.THE_END)continue;DragonBattle b=w.getEnderDragonBattle();if(b==null||!b.hasBeenPreviouslyKilled()||b.getEnderDragon()!=null||b.getRespawnPhase()!=DragonBattle.RespawnPhase.NONE)continue;long at=due(w);if(at==0){schedule(w);continue;}if(now<at||w.getPlayers().isEmpty())continue;Location portal=b.getEndPortalLocation();if(portal==null)continue;
// Vanilla battle needs a player in the End. Start with four crystals at the real exit portal.
List<EnderCrystal> created=new ArrayList<>();List<EnderCrystal> crystals=new ArrayList<>();
try{for(int[] direction:new int[][]{{3,0},{-3,0},{0,3},{0,-3}}){Location l=portal.clone().add(direction[0]+.5,1,direction[1]+.5);if(!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)){break;}EnderCrystal existing=w.getNearbyEntities(l,.7,1,.7).stream().filter(EnderCrystal.class::isInstance).map(EnderCrystal.class::cast).findFirst().orElse(null);if(existing==null){existing=w.spawn(l,EnderCrystal.class,c->c.setShowingBottom(false));created.add(existing);}crystals.add(existing);}
if(crystals.size()==4&&b.initiateRespawn(crystals)){Bukkit.broadcastMessage("§5Дракон Края возрождается!");}else for(EnderCrystal c:created)c.remove();
}catch(RuntimeException ex){for(EnderCrystal c:created)if(c.isValid())c.remove();host.getLogger().warning("Dragon respawn: "+ex.getMessage());}
}}
boolean status(CommandSender s){for(World w:Bukkit.getWorlds())if(w.getEnvironment()==World.Environment.THE_END){DragonBattle b=w.getEnderDragonBattle();if(b==null)continue;long remaining=Math.max(0,due(w)-System.currentTimeMillis());s.sendMessage("§5"+w.getName()+" §7· "+(b.getEnderDragon()!=null?"Дракон жив":b.getRespawnPhase()!=DragonBattle.RespawnPhase.NONE?"Возрождение":String.format("До возрождения: %dч %02dм",remaining/3600000,remaining/60000%60)));}return true;}
void disable(){}
}
