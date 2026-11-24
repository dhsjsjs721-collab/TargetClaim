package dev.talismanforge;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
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
ZEUS("zeus","§eТалисман Зевса",NamedTextColor.YELLOW,
"§7Молния, Спешка II, контроль противника",1),
POSEIDON("olympus_poseidon","§3Талисман Посейдона",NamedTextColor.DARK_AQUA,
"§7Водное дыхание, плавание и лечение",1),
ARES("ares","§cТалисман Ареса",NamedTextColor.RED,
"§7Сила I, кровоточащие удары и боевой отхил",1),
ATHENA("athena","§bТалисман Афины",NamedTextColor.AQUA,
"§7Сопротивление, поглощение и ослабление врага",1),
HERMES("hermes","§aТалисман Гермеса",NamedTextColor.GREEN,
"§7Скорость II, прыгучесть и замедление врага",1);
final String key,title,lore;
final NamedTextColor color;
final int weight;
Kind(String key,String title,NamedTextColor color,String lore,int weight){
this.key=key;this.title=title;this.color=color;this.lore=lore;this.weight=weight;
}
static Kind parse(String key){
if(key==null)return null;
for(Kind k:values())if(k.key.equalsIgnoreCase(key))return k;
return null;
}
}
private static final long SEC=1000L;
private final Plugin plugin;
private final NamespacedKey type=new NamespacedKey("talismanforge","five_talisman");
private final NamespacedKey legacy=new NamespacedKey("talismanforge","talisman_type");
private final NamespacedKey legacyUses=new NamespacedKey("talismanforge","uses_left");
private final Map<UUID,Long> offensiveCooldown=new HashMap<>();
private final Map<UUID,Long> defensiveCooldown=new HashMap<>();
private long crafted,awarded,converted,rescued,caseDraws;
FiveTalismanService(Plugin plugin){this.plugin=plugin;}
ItemStack create(Kind kind){
ItemStack item=new ItemStack(Material.TOTEM_OF_UNDYING);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName(kind.title);
meta.setLore(List.of("§dНаграда из реликвария","§7Спасение от смерти без случайного шанса",
kind.lore,"§8Работает в любой руке","§8Не работает в бездне"));
meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,kind.key);
item.setItemMeta(meta);
awarded++;
return item;
}
// Deterministic rotation: all five Olympian talismans are equally accessible.
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
switch(k){
case ZEUS->add(p,PotionEffectType.HASTE,65,1);
case POSEIDON->{
if(p.isInWater()){
add(p,PotionEffectType.WATER_BREATHING,65,0);
add(p,PotionEffectType.DOLPHINS_GRACE,65,0);
}
}
case ARES->add(p,PotionEffectType.STRENGTH,65,0);
case ATHENA->{
add(p,PotionEffectType.RESISTANCE,65,0);
add(p,PotionEffectType.ABSORPTION,75,0);
}
case HERMES->{
add(p,PotionEffectType.SPEED,65,1);
add(p,PotionEffectType.JUMP_BOOST,65,0);
}

}
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
if(!(e.getDamager() instanceof Player attacker)||!(e.getEntity() instanceof LivingEntity victim))return;
if(e.getFinalDamage()<=0 || !mayAttack(attacker,victim))return;
Kind k=carried(attacker);
if(k==null)return;
switch(k){
case ZEUS->{
if(ready(offensiveCooldown,attacker.getUniqueId(),4*SEC)){
e.setDamage(e.getDamage()+3.0);
victim.getWorld().strikeLightningEffect(victim.getLocation());
victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,45,0));
}
}
case POSEIDON->{
if(ready(offensiveCooldown,attacker.getUniqueId(),attacker.isInWater()?4*SEC:7*SEC))
attacker.setHealth(Math.min(attacker.getMaxHealth(),attacker.getHealth()+3.0));
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
if(ready(offensiveCooldown,attacker.getUniqueId(),5*SEC))
victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,65,1));
}

}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void defended(EntityDamageEvent e){
if(!(e.getEntity() instanceof Player p) || e.getFinalDamage()<=0)return;
Kind k=carried(p);
if(k==null)return;
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
if(k==Kind.HERMES)add(p,PotionEffectType.SPEED,120,2);
return;
}
if(k==Kind.ATHENA&&ready(defensiveCooldown,p.getUniqueId(),8*SEC)){
p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,80,1));
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
String status(){return "5 Olympian talismans, awarded="+awarded+", removedLegacy="+converted+", rescues="+rescued;}
}
