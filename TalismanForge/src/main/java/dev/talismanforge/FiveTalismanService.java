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
}
@EventHandler(priority=EventPriority.MONITOR)
public void inventoryOpen(InventoryOpenEvent event){
if(event.getPlayer() instanceof Player p)migratePlayer(p);
cleanse(event.getInventory());
}
void pulse(){
for(Player p:Bukkit.getOnlinePlayers()){
migratePlayer(p);
Kind k=carried(p);
if(k==null)continue;
// No passive bonuses outside PvP combat.
}
long now=System.currentTimeMillis();
if(offensiveCooldown.size()>20000)offensiveCooldown.clear();
if(defensiveCooldown.size()>20000)defensiveCooldown.clear();
offensiveCooldown.entrySet().removeIf(e->now-e.getValue()>180000);
defensiveCooldown.entrySet().removeIf(e->now-e.getValue()>180000);
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
if(e.getFinalDamage()<=0 || !mayAttack(attacker,victim))return;
Kind k=carried(attacker);
if(k==null)return;
switch(k){
case ZEUS->{
// Every valid vanilla-style critical PvP hit triggers lightning.
// No talisman cooldown or random roll; non-critical hits do nothing.
if(attacker.canCriticalAttack(victim)){
e.setDamage(e.getDamage()+3.0);
victim.getWorld().strikeLightningEffect(victim.getLocation());
victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,45,0));
}
}
case POSEIDON->{
if(ready(offensiveCooldown,attacker.getUniqueId(),7*SEC))
attacker.setHealth(Math.min(attacker.getMaxHealth(),attacker.getHealth()+3.0));
attacker.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,70,0));
victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,50,0));
}
case ARES->{
if(ready(offensiveCooldown,attacker.getUniqueId(),5*SEC)){
victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,65,0));
attacker.setHealth(Math.min(attacker.getMaxHealth(),attacker.getHealth()+2.0));
}
}
case ATHENA->{
if(ready(offensiveCooldown,attacker.getUniqueId(),6*SEC))
victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,70,0));
}
case HERMES->{
if(ready(offensiveCooldown,attacker.getUniqueId(),5*SEC)){
victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,55,0));
victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,35,0));
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
if(!(e.getEntity() instanceof Player p) || e.getFinalDamage()<=0)return;
Kind k=carried(p);
if(k==null||!pvpDamage(e))return;
if(e.getCause()!=EntityDamageEvent.DamageCause.VOID &&
e.getCause()!=EntityDamageEvent.DamageCause.KILL &&
e.getFinalDamage()>=p.getHealth()){
e.setCancelled(true);
consume(p);
p.setHealth(Math.min(p.getMaxHealth(),Math.max(5.0,p.getHealth())));
p.setNoDamageTicks(Math.max(p.getNoDamageTicks(),45));
p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,100,1));
p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),40,.4,.7,.4,.12);
p.sendMessage("§a✦ "+k.title+" спас тебя и исчез.");
rescued++;
if(k==Kind.ZEUS){
p.getWorld().strikeLightningEffect(p.getLocation());
}
if(k==Kind.ARES)add(p,PotionEffectType.STRENGTH,120,1);
if(k==Kind.HERMES)add(p,PotionEffectType.RESISTANCE,100,1);
return;
}
if(k==Kind.ATHENA&&ready(defensiveCooldown,p.getUniqueId(),8*SEC)){
p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,80,1));
}
if(k==Kind.HERMES&&ready(defensiveCooldown,p.getUniqueId(),8*SEC)
&&e instanceof EntityDamageByEntityEvent hit){
Entity source=hit.getDamager();
if(source instanceof Player enemy && enemy!=p){
enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));
}else if(source instanceof org.bukkit.entity.Projectile projectile
&&projectile.getShooter() instanceof Player enemy && enemy!=p){
enemy.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));
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
offensiveCooldown.clear();defensiveCooldown.clear();
}
String status(){return "5 mythic talismans, awarded="+awarded+", removedLegacy="+converted+", rescues="+rescued;}
}
