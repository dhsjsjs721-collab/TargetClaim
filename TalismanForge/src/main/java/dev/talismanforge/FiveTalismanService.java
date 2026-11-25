package dev.talismanforge;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Five uniquely designed, one-use rescue talismans. Case-only survival source. */
final class FiveTalismanService implements Listener {
enum Kind {
CERBERUS("cerberus","§4Талисман Цербера",NamedTextColor.DARK_RED,"§7Цепные криты и призрачное спасение",1),
KRAKEN("kraken","§3Талисман Кракена",NamedTextColor.DARK_AQUA,"§7+3 сердца, морская метка и два спасения",1),
GORGON("gorgon","§2Талисман Горгоны",NamedTextColor.DARK_GREEN,"§7Каменный взгляд и проклятый щит",1),
PHOENIX("phoenix","§6Талисман Феникса",NamedTextColor.GOLD,"§7Золотые сердца и огненная волна",1),
HYDRA("hydra","§aТалисман Гидры",NamedTextColor.GREEN,"§7Три головы, яд и иммунитет к зельям",1);
final String key,title,lore;
final NamedTextColor color;
final int weight;
Kind(String key,String title,NamedTextColor color,String lore,int weight){this.key=key;this.title=title;this.color=color;this.lore=lore;this.weight=weight;}
static Kind parse(String key){if(key==null)return null;for(Kind k:values())if(k.key.equalsIgnoreCase(key))return k;return null;}
}

private static final long SEC=1000L;
private final Plugin plugin;
private final NamespacedKey type=new NamespacedKey("talismanforge","five_talisman");
private final NamespacedKey legacy=new NamespacedKey("talismanforge","talisman_type");
private final NamespacedKey legacyUses=new NamespacedKey("talismanforge","uses_left");
private final Map<UUID,Long> offensiveCooldown=new HashMap<>();
private final Map<UUID,Long> defensiveCooldown=new HashMap<>();
private final NamespacedKey heads=new NamespacedKey("talismanforge","hydra_heads");
private final NamespacedKey regens=new NamespacedKey("talismanforge","hydra_regens");
private final NamespacedKey lives=new NamespacedKey("talismanforge","mythic_lives");
private final NamespacedKey health=new NamespacedKey("talismanforge","mythic_health_bonus");
private final NamespacedKey ghostTag=new NamespacedKey("talismanforge","cerberus_ghost_mode");
private final NamespacedKey ghostY=new NamespacedKey("talismanforge","cerberus_ghost_y");
private final Map<UUID,Long> inCombat=new HashMap<>(),gazeStart=new HashMap<>(),stoneImmunity=new HashMap<>(),ghostEnds=new HashMap<>(),lastCrit=new HashMap<>(),markedEnds=new HashMap<>(),phoenixRecharge=new HashMap<>();
private final Map<UUID,UUID> critTarget=new HashMap<>(),markedBy=new HashMap<>();
private final Map<UUID,Integer> critCount=new HashMap<>();
private final Map<UUID,Double> fireStored=new HashMap<>();
private final Map<UUID,GameMode> ghostModes=new HashMap<>();
private final Map<UUID,Double> ghostFloors=new HashMap<>();
private final Set<UUID> chained=new HashSet<>(),angryMobs=new HashSet<>();
private long crafted,awarded,converted,rescued,caseDraws;
FiveTalismanService(Plugin plugin){this.plugin=plugin;}
ItemStack create(Kind kind){
ItemStack item=new ItemStack(Material.TOTEM_OF_UNDYING);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName(kind.title);
meta.setLore(List.of("§dНаграда из реликвария","§7Уникальные PvP-способности и спасение от смерти",
kind.lore,"§8Работает в любой руке","§8Только против игроков"));
meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,kind.key);
if(kind==Kind.HYDRA)meta.getPersistentDataContainer().set(heads,PersistentDataType.INTEGER,3);
if(kind==Kind.KRAKEN)meta.getPersistentDataContainer().set(lives,PersistentDataType.INTEGER,2);
item.setItemMeta(meta);
awarded++;
return item;
}
// Guaranteed five-item rotation; no random chances.
ItemStack newFromCase(){
Kind[] order=Kind.values();
Kind selected=order[(int)(caseDraws%order.length)];
caseDraws++;
return create(selected);
}

Kind kind(ItemStack item){
if(item==null||item.getType()!=Material.TOTEM_OF_UNDYING||!item.hasItemMeta())return null;
return Kind.parse(item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING));
}
Kind carried(Player player){
Kind off=kind(player.getInventory().getItemInOffHand());
return off!=null?off:kind(player.getInventory().getItemInMainHand());
}
// Legacy talismans are retired, not converted into new variants.
// Vanilla untagged Totems of Undying and the five new PDC-tagged talismans survive.
private boolean legacy(ItemStack item){
if(item==null||item.getType()!=Material.TOTEM_OF_UNDYING||!item.hasItemMeta())return false;
var tags=item.getItemMeta().getPersistentDataContainer();
if(tags.has(legacy,PersistentDataType.STRING))return true;
String old=tags.get(type,PersistentDataType.STRING);
return old!=null && Kind.parse(old)==null;
}
private void cleanse(org.bukkit.inventory.Inventory inventory){
ItemStack[] contents=inventory.getContents();
boolean changed=false;
for(int i=0;i<contents.length;i++){
if(legacy(contents[i])){contents[i]=null;changed=true;converted++;}
}
if(changed)inventory.setContents(contents);
}
private void migratePlayer(Player p){
cleanse(p.getInventory());
cleanse(p.getEnderChest());
}
@EventHandler public void join(PlayerJoinEvent event){
migratePlayer(event.getPlayer());
if(event.getPlayer().getPersistentDataContainer().has(ghostTag,PersistentDataType.STRING))ghostRestore(event.getPlayer());
}
@EventHandler(priority=EventPriority.MONITOR)
public void inventoryOpen(InventoryOpenEvent event){
if(event.getPlayer() instanceof Player p)migratePlayer(p);
cleanse(event.getInventory());
}
void pulse(){
long now=System.currentTimeMillis();
for(Player p:Bukkit.getOnlinePlayers()){
migratePlayer(p);
Kind k=carried(p);
ItemStack held=item(p);
if(k==Kind.CERBERUS){
int opponents=0;
for(Entity near:p.getNearbyEntities(8,6,8))if(near instanceof Player enemy&&mayAttack(p,enemy)
&&inCombat.getOrDefault(enemy.getUniqueId(),0L)>now)opponents++;
health(p,-6+Math.min(2,opponents)*4);
}else if(k==Kind.KRAKEN){health(p,6);krakenControl(p,now);}
else if(k==Kind.HYDRA){
health(p,-6);regrow(p,held,now);
for(PotionEffectType effect:List.of(PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.WEAKNESS,
PotionEffectType.SLOWNESS,PotionEffectType.BLINDNESS,PotionEffectType.NAUSEA,PotionEffectType.MINING_FATIGUE))p.removePotionEffect(effect);
}else health(p,0);
if(k==Kind.PHOENIX){
UUID id=p.getUniqueId();
if(now-phoenixRecharge.getOrDefault(id,0L)>=2000){
p.setAbsorptionAmount(Math.min(10,p.getAbsorptionAmount()+2));
phoenixRecharge.put(id,now);
}
}else{fireStored.remove(p.getUniqueId());phoenixRecharge.remove(p.getUniqueId());}
if(k==Kind.GORGON){
for(Entity ent:p.getNearbyEntities(6,5,6)){
if(ent instanceof Player target)gorgonStare(p,target,now);
if(ent instanceof Monster mob&&mob.hasLineOfSight(p)){
Vector to=p.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
if(to.lengthSquared()>.01&&mob.getEyeLocation().getDirection().dot(to.normalize())>.94)
mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,9));
}
}
}
}
inCombat.entrySet().removeIf(e->e.getValue()<now);
markedEnds.entrySet().removeIf(e->e.getValue()<now);
}

private ItemStack item(Player p){
ItemStack off=p.getInventory().getItemInOffHand();
return kind(off)!=null?off:p.getInventory().getItemInMainHand();
}
private int count(ItemStack i,NamespacedKey k,int fallback){
Integer number=i.getItemMeta().getPersistentDataContainer().get(k,PersistentDataType.INTEGER);
return number==null?fallback:number;
}
private void count(ItemStack i,NamespacedKey k,int v){
ItemMeta m=i.getItemMeta();m.getPersistentDataContainer().set(k,PersistentDataType.INTEGER,v);i.setItemMeta(m);
}
private List<Long> scheduledHeads(ItemStack i){
List<Long> times=new ArrayList<>();
String s=i.getItemMeta().getPersistentDataContainer().get(regens,PersistentDataType.STRING);
if(s!=null)for(String part:s.split(","))try{times.add(Long.parseLong(part));}catch(NumberFormatException ignored){}
return times;
}
private void scheduledHeads(ItemStack i,List<Long> times){
ItemMeta m=i.getItemMeta();
m.getPersistentDataContainer().set(regens,PersistentDataType.STRING,String.join(",",times.stream().map(String::valueOf).toList()));
i.setItemMeta(m);
}
private void regrow(Player p,ItemStack i,long now){
List<Long> times=scheduledHeads(i);Collections.sort(times);
int h=count(i,heads,3),before=h;
while(!times.isEmpty()&&times.get(0)<=now&&h<3){times.remove(0);h++;}
if(h!=before){count(i,heads,h);scheduledHeads(i,times);p.sendActionBar("§aГидра: отросла голова, "+h+"/3");}
}
private void health(Player p,double amount){
var a=p.getAttribute(Attribute.MAX_HEALTH);
if(a==null)return;
AttributeModifier old=null;
for(AttributeModifier mod:a.getModifiers())if(mod.getKey().equals(health)){old=mod;break;}
if(old!=null&&Math.abs(old.getAmount()-amount)<.001)return;
if(old!=null)a.removeModifier(old);
if(amount!=0)a.addModifier(new AttributeModifier(health,amount,AttributeModifier.Operation.ADD_NUMBER));
if(p.getHealth()>a.getValue())p.setHealth(a.getValue());
}
private void heal(Player p,double hearts){
var a=p.getAttribute(Attribute.MAX_HEALTH);
double max=a==null?20:a.getValue();
p.setHealth(Math.min(max,Math.max(1,hearts*2)));
}
private boolean ghost(Player p){return ghostEnds.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis();}
private void ghostStart(Player p){
UUID id=p.getUniqueId();GameMode old=p.getGameMode();double y=p.getLocation().getY();
ghostModes.put(id,old);ghostFloors.put(id,y);ghostEnds.put(id,System.currentTimeMillis()+3000L);
p.getPersistentDataContainer().set(ghostTag,PersistentDataType.STRING,old.name());
p.getPersistentDataContainer().set(ghostY,PersistentDataType.DOUBLE,y);
p.setGameMode(GameMode.SPECTATOR);
p.sendMessage("§4Цербер: призрак на 3 секунды; под землю спускаться нельзя.");
Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline())ghostRestore(p);},60L);
}
private void ghostRestore(Player p){
String saved=p.getPersistentDataContainer().get(ghostTag,PersistentDataType.STRING);
if(saved==null)return;
GameMode old=ghostModes.get(p.getUniqueId());
if(old==null)try{old=GameMode.valueOf(saved);}catch(IllegalArgumentException error){old=GameMode.SURVIVAL;}
if(old==GameMode.SPECTATOR)old=GameMode.SURVIVAL;
Double floor=ghostFloors.getOrDefault(p.getUniqueId(),p.getPersistentDataContainer().get(ghostY,PersistentDataType.DOUBLE));
p.setGameMode(old);
if(floor!=null&&p.getLocation().getY()<floor){
Location l=p.getLocation();l.setY(floor);
if(l.getBlock().isPassable()&&l.clone().add(0,1,0).getBlock().isPassable())p.teleport(l);
else p.teleport(p.getWorld().getSpawnLocation());
}
p.getPersistentDataContainer().remove(ghostTag);p.getPersistentDataContainer().remove(ghostY);
ghostEnds.remove(p.getUniqueId());ghostFloors.remove(p.getUniqueId());ghostModes.remove(p.getUniqueId());
p.setNoDamageTicks(50);
}
@EventHandler public void ghostWalk(PlayerMoveEvent e){
if(!ghost(e.getPlayer())||e.getTo()==null)return;
double floor=ghostFloors.getOrDefault(e.getPlayer().getUniqueId(),e.getFrom().getY());
if(e.getTo().getY()<floor){Location l=e.getFrom().clone();l.setY(Math.max(floor,l.getY()));e.setTo(l);}
}
@EventHandler public void ghostQuit(PlayerQuitEvent e){ghostRestore(e.getPlayer());}
@EventHandler public void ghostInteract(PlayerInteractEvent e){if(ghost(e.getPlayer()))e.setCancelled(true);}
@EventHandler public void ghostHit(EntityDamageByEntityEvent e){if(e.getDamager() instanceof Player p&&ghost(p))e.setCancelled(true);}
@EventHandler public void neutralNether(EntityTargetLivingEntityEvent e){
if(!(e.getTarget() instanceof Player p)||carried(p)!=Kind.CERBERUS)return;
if(e.getEntity() instanceof Ghast||e.getEntity() instanceof Piglin||e.getEntity() instanceof ZombifiedPiglin||e.getEntity() instanceof Blaze){
if(!angryMobs.contains(e.getEntity().getUniqueId()))e.setCancelled(true);
}
}
@EventHandler public void angryNether(EntityDamageByEntityEvent e){
if(e.getDamager() instanceof Player p&&carried(p)==Kind.CERBERUS
&&(e.getEntity() instanceof Ghast||e.getEntity() instanceof Piglin||e.getEntity() instanceof ZombifiedPiglin||e.getEntity() instanceof Blaze))angryMobs.add(e.getEntity().getUniqueId());
}
private void stone(Player p,int ticks){
UUID id=p.getUniqueId(),nowId=id;
if(stoneImmunity.getOrDefault(id,0L)>System.currentTimeMillis())return;
stoneImmunity.put(id,System.currentTimeMillis()+10000L);
p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,ticks,9));
p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,ticks,128));
p.getWorld().spawnParticle(Particle.BLOCK,p.getLocation().add(0,1,0),35,.35,.7,.35,Material.STONE.createBlockData());
p.sendActionBar("§7Окаменение!");
}
@EventHandler public void blockStone(org.bukkit.event.entity.EntityDamageByEntityEvent e){
if(!(e.getEntity() instanceof Player p)||carried(p)!=Kind.GORGON||!p.isBlocking())return;
if(e.getDamager() instanceof Player attacker&&mayAttack(p,attacker)&&e.getFinalDamage()<=0)stone(attacker,40);
}
@EventHandler public void stoneWalk(PlayerMoveEvent e){
Player p=e.getPlayer();PotionEffect slow=p.getPotionEffect(PotionEffectType.SLOWNESS);
if(slow!=null&&slow.getAmplifier()>=9&&slow.getDuration()>0&&e.getTo()!=null
&&e.getFrom().distanceSquared(e.getTo())>.0001)e.setTo(e.getFrom());
}
private void fireWave(Player caster,double stored){
final double each=Math.min(stored,20)/4;
for(int j=0;j<4;j++){
final int delay=j*20;
Bukkit.getScheduler().runTaskLater(plugin,()->{
if(!caster.isOnline())return;
Location at=caster.getLocation().add(0,1,0);
caster.getWorld().spawnParticle(Particle.FLAME,at,80,2,.8,2,.04);
for(Entity ent:caster.getNearbyEntities(5,3,5))
if(ent instanceof Player victim&&mayAttack(caster,victim)){
if(chained.add(caster.getUniqueId()))try{victim.damage(each,caster);}finally{chained.remove(caster.getUniqueId());}
}
},delay);
}
}
private void krakenControl(Player p,long now){
for(Entity ent:p.getNearbyEntities(24,8,24)){
if(!(ent instanceof Player target)||!mayAttack(p,target))continue;
UUID id=target.getUniqueId();
if(!p.getUniqueId().equals(markedBy.get(id))||markedEnds.getOrDefault(id,0L)<now)continue;
double d=p.getLocation().distance(target.getLocation());
if(d>20){
Vector direction=p.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
if(direction.lengthSquared()>.01)target.setVelocity(direction.normalize().multiply(1.25).setY(.18));
}else if(d>15){add(target,PotionEffectType.SLOWNESS,45,2);add(target,PotionEffectType.DARKNESS,45,0);add(target,PotionEffectType.MINING_FATIGUE,45,0);}
else if(d>10){add(target,PotionEffectType.SLOWNESS,45,1);add(target,PotionEffectType.DARKNESS,45,0);}
else if(d>5)add(target,PotionEffectType.SLOWNESS,45,0);
}
}
private void gorgonStare(Player owner,Player target,long now){
UUID id=target.getUniqueId();
if(!mayAttack(owner,target)||owner.getLocation().distanceSquared(target.getLocation())>36||!target.hasLineOfSight(owner)){gazeStart.remove(id);return;}
Vector to=owner.getEyeLocation().toVector().subtract(target.getEyeLocation().toVector());
if(to.lengthSquared()<.001||target.getEyeLocation().getDirection().dot(to.normalize())<.97){gazeStart.remove(id);return;}
long seconds=now-gazeStart.computeIfAbsent(id,k->now);
if(seconds>=2000)add(target,PotionEffectType.NAUSEA,50,0);
if(seconds>=3000)add(target,PotionEffectType.DARKNESS,50,0);
if(seconds>=4000){stone(target,40);gazeStart.remove(id);}
}
private static void add(Player p,PotionEffectType effect,int ticks,int amplifier){
PotionEffect existing=p.getPotionEffect(effect);
// Do not override stronger or longer external potions.
if(existing!=null && existing.getAmplifier()>amplifier)return;
if(existing!=null && existing.getDuration()>ticks-20)return;
p.addPotionEffect(new PotionEffect(effect,ticks,amplifier,true,false,true));
}
private static boolean mayAttack(Player attacker,LivingEntity victim){
if(attacker==victim || !victim.isValid() || victim.isDead())return false;
if(victim instanceof Player other){
org.bukkit.scoreboard.Team team=attacker.getScoreboard().getEntryTeam(attacker.getName());
if(team!=null && team.equals(attacker.getScoreboard().getEntryTeam(other.getName())))return false;
}
return true;
}
private boolean ready(Map<UUID,Long> cooldown,UUID id,long interval){
long now=System.currentTimeMillis();
if(now-cooldown.getOrDefault(id,0L)<interval)return false;
cooldown.put(id,now);
return true;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void attack(EntityDamageByEntityEvent e){
if(!(e.getDamager() instanceof Player attacker)||!(e.getEntity() instanceof Player victim))return;
if(e.getDamage()<=0||!mayAttack(attacker,victim))return;
UUID id=attacker.getUniqueId();
long now=System.currentTimeMillis();
inCombat.put(id,now+12000);inCombat.put(victim.getUniqueId(),now+12000);
Kind k=carried(attacker);
if(k==null||chained.contains(id))return;
boolean crit=attacker.canCriticalAttack(victim);
switch(k){
case CERBERUS->{
if(!crit)return;
List<Player> targets=new ArrayList<>();
for(Entity near:victim.getNearbyEntities(5,4,5))
if(near instanceof Player player&&player!=attacker&&mayAttack(attacker,player))targets.add(player);
targets.sort(Comparator.comparingDouble(p->p.getLocation().distanceSquared(victim.getLocation())));
if(targets.isEmpty())return;
Player from=victim;
for(int i=0;i<3;i++){
Player next=i==2&&targets.size()==1?victim:targets.get(i%targets.size());
if(!mayAttack(attacker,next)||from.getLocation().distanceSquared(next.getLocation())>25)break;
if(!chained.add(id))break;
try{next.damage(e.getDamage()*.5,attacker);}finally{chained.remove(id);}
next.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,next.getEyeLocation(),12,.2,.3,.2,.01);
from=next;
}
}
case KRAKEN->{
markedBy.put(victim.getUniqueId(),id);
markedEnds.put(victim.getUniqueId(),now+10000);
if(crit)add(victim,PotionEffectType.DARKNESS,45,0);
}
case GORGON->{
if(crit)add(victim,PotionEffectType.SLOWNESS,60,1);
}
case PHOENIX->{}
case HYDRA->{
if(!crit)return;
boolean successive=victim.getUniqueId().equals(critTarget.get(id))
&&now-lastCrit.getOrDefault(id,0L)<5000;
int n=successive?critCount.getOrDefault(id,0)+1:1;
critTarget.put(id,victim.getUniqueId());lastCrit.put(id,now);
critCount.put(id,n);
if(n>=3){
critCount.put(id,0);
victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,1));
victim.getWorld().spawnParticle(Particle.ITEM_SLIME,victim.getEyeLocation(),25,.3,.4,.3,.02);
}
}
}
}

private boolean pvpDamage(EntityDamageEvent event){
if(!(event instanceof EntityDamageByEntityEvent hit))return false;
Entity source=hit.getDamager();
if(source instanceof Player)return true;
if(source instanceof org.bukkit.entity.Projectile projectile)
return projectile.getShooter() instanceof Player;
return false;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void defended(EntityDamageEvent e){
if(!(e.getEntity() instanceof Player p)||e.getDamage()<=0)return;
Kind k=carried(p);
if(k==null||!pvpDamage(e))return;
if(k==Kind.PHOENIX){
double stored=Math.min(e.getDamage(),p.getAbsorptionAmount());
if(stored>0){
double total=fireStored.getOrDefault(p.getUniqueId(),0.0)+stored;
fireStored.put(p.getUniqueId(),total);
if(stored>=p.getAbsorptionAmount()-.01){
fireStored.put(p.getUniqueId(),0.0);
fireWave(p,total);
}
}
}
if(e.getFinalDamage()<p.getHealth()||e.getCause()==EntityDamageEvent.DamageCause.VOID
||e.getCause()==EntityDamageEvent.DamageCause.KILL)return;
e.setCancelled(true);
p.setNoDamageTicks(Math.max(p.getNoDamageTicks(),45));
p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),40,.4,.7,.4,.12);
rescued++;
ItemStack carried=item(p);
switch(k){
case CERBERUS->{consume(p);heal(p,5);ghostStart(p);}
case KRAKEN->{
int left=count(carried,lives,2)-1;
if(left<=0)consume(p);else count(carried,lives,left);
heal(p,5);
p.sendMessage("§3Кракен спас тебя. Осталось спасений: "+left);
}
case GORGON->{
consume(p);heal(p,5);
if(e instanceof EntityDamageByEntityEvent hit){
Entity damager=hit.getDamager();
if(damager instanceof Player target)stone(target,60);
else if(damager instanceof Projectile arrow&&arrow.getShooter() instanceof Player target)stone(target,60);
}
}
case PHOENIX->{
consume(p);heal(p,6);
add(p,PotionEffectType.RESISTANCE,60,4);
add(p,PotionEffectType.SLOWNESS,60,9);
p.getWorld().spawnParticle(Particle.FLAME,p.getLocation().add(0,1,0),100,.6,.8,.6,.05);
}
case HYDRA->{
int left=count(carried,heads,3)-1;
if(left<=0){consume(p);p.sendMessage("§cПоследняя голова Гидры уничтожена.");}
else{
List<Long> times=scheduledHeads(carried);
times.add(System.currentTimeMillis()+120000L);
count(carried,heads,left);scheduledHeads(carried,times);
p.sendMessage("§aГидра спасла тебя! Осталось "+left+" головы; восстановление через 2 минуты.");
}
heal(p,4);
}
}
}

private void consume(Player player){
ItemStack off=player.getInventory().getItemInOffHand();
if(kind(off)!=null){
if(off.getAmount()<=1)player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
else off.setAmount(off.getAmount()-1);
return;
}
ItemStack main=player.getInventory().getItemInMainHand();
if(kind(main)!=null){
if(main.getAmount()<=1)player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
else main.setAmount(main.getAmount()-1);
}
}
@EventHandler public void dropped(org.bukkit.event.entity.ItemSpawnEvent event){
if(legacy(event.getEntity().getItemStack()))event.getEntity().remove();
}
@EventHandler public void chunkEntities(org.bukkit.event.world.EntitiesLoadEvent event){
for(Entity entity:event.getEntities())if(entity instanceof Item drop && legacy(drop.getItemStack()))drop.remove();
}
@EventHandler public void chunkContainers(org.bukkit.event.world.ChunkLoadEvent event){
for(org.bukkit.block.BlockState state:event.getChunk().getTileEntities()){
if(state instanceof org.bukkit.block.Container container)cleanse(container.getInventory());
}
}
void shutdown(){
for(Player p:Bukkit.getOnlinePlayers()){
if(p.getPersistentDataContainer().has(ghostTag,PersistentDataType.STRING))ghostRestore(p);
health(p,0);
}
offensiveCooldown.clear();defensiveCooldown.clear();inCombat.clear();gazeStart.clear();stoneImmunity.clear();ghostEnds.clear();
lastCrit.clear();markedEnds.clear();critTarget.clear();markedBy.clear();critCount.clear();fireStored.clear();phoenixRecharge.clear();
ghostModes.clear();ghostFloors.clear();chained.clear();angryMobs.clear();
}

String status(){return "5 mythic talismans, awarded="+awarded+", removedLegacy="+converted+", rescues="+rescued;}
}
