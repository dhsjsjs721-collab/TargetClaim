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
PHOENIX("phoenix","§6Талисман Феникса",NamedTextColor.GOLD,
"§7Огнестойкость, спасение и огненная вспышка",12),
TEMPEST("tempest","§bТалисман Грозы",NamedTextColor.AQUA,
"§7Заряженный удар каждые 4 секунды",19),
GUARDIAN("guardian","§aТалисман Стража",NamedTextColor.GREEN,
"§7Поглощение и защита после получения урона",26),
SHADE("shade","§5Талисман Сумрака",NamedTextColor.LIGHT_PURPLE,
"§7Скорость в темноте, ослепление врага",23),
TIDE("tide","§3Талисман Прилива",NamedTextColor.DARK_AQUA,
"§7Дыхание и ускорение в воде, лечение",20);
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
private long crafted,awarded,converted,rescued;
FiveTalismanService(Plugin plugin){this.plugin=plugin;}
ItemStack create(Kind kind){
ItemStack item=new ItemStack(Material.TOTEM_OF_UNDYING);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName(kind.title);
meta.setLore(List.of("§dНаграда из реликвария","§7Один раз спасает от смерти",
kind.lore,"§8Работает в любой руке","§8Не работает в бездне"));
meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,kind.key);
item.setItemMeta(meta);
awarded++;
return item;
}
ItemStack newFromCase(){
int total=0;
for(Kind kind:Kind.values())total+=kind.weight;
int n=java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
for(Kind kind:Kind.values()){
n-=kind.weight;
if(n<0)return create(kind);
}
return create(Kind.GUARDIAN);
}
Kind kind(ItemStack item){
if(item==null||item.getType()!=Material.TOTEM_OF_UNDYING||!item.hasItemMeta())return null;
return Kind.parse(item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING));
}
Kind carried(Player player){
Kind off=kind(player.getInventory().getItemInOffHand());
return off!=null?off:kind(player.getInventory().getItemInMainHand());
}
private Kind migrate(String old){
return switch(old){
case "maiming","hades"->Kind.PHOENIX;
case "wrath","end"->Kind.TEMPEST;
case "defiance","ages"->Kind.GUARDIAN;
case "joker"->Kind.SHADE;
case "poseidon"->Kind.TIDE;
default->null;
};
}
private ItemStack migrateItem(ItemStack item){
if(item==null||item.getType()!=Material.TOTEM_OF_UNDYING||!item.hasItemMeta())return item;
if(kind(item)!=null)return item;
String old=item.getItemMeta().getPersistentDataContainer().get(legacy,PersistentDataType.STRING);
Kind to=migrate(old);
if(to==null)return item;
ItemStack newer=create(to);
ItemMeta meta=newer.getItemMeta();
meta.getPersistentDataContainer().set(new NamespacedKey("talismanforge","legacy_migrated"),PersistentDataType.STRING,old);
newer.setItemMeta(meta);
newer.setAmount(item.getAmount());
converted++;
return newer;
}
private void migratePlayer(Player p){
ItemStack[] inventory=p.getInventory().getContents();
boolean changed=false;
for(int i=0;i<inventory.length;i++){
ItemStack next=migrateItem(inventory[i]);
if(next!=inventory[i]){inventory[i]=next;changed=true;}
}
if(changed)p.getInventory().setContents(inventory);
}
@EventHandler public void join(PlayerJoinEvent event){
migratePlayer(event.getPlayer());
}
@EventHandler(priority=EventPriority.MONITOR)
public void inventoryOpen(InventoryOpenEvent event){
if(event.getPlayer() instanceof Player p)migratePlayer(p);
}
void pulse(){
for(Player p:Bukkit.getOnlinePlayers()){
migratePlayer(p);
Kind k=carried(p);
if(k==null)continue;
switch(k){
case PHOENIX->add(p,PotionEffectType.FIRE_RESISTANCE,65,0);
case GUARDIAN->add(p,PotionEffectType.ABSORPTION,75,0);
case SHADE->{
if(p.getLocation().getBlock().getLightFromSky()<7)add(p,PotionEffectType.SPEED,65,0);
}
case TIDE->{
if(p.isInWater()){
add(p,PotionEffectType.WATER_BREATHING,65,0);
add(p,PotionEffectType.DOLPHINS_GRACE,65,0);
}
}
case TEMPEST->{}
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
case TEMPEST->{
if(ready(offensiveCooldown,attacker.getUniqueId(),4*SEC) &&
java.util.concurrent.ThreadLocalRandom.current().nextDouble()<.45){
e.setDamage(e.getDamage()+2.0);
victim.getWorld().strikeLightningEffect(victim.getLocation());
victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,35,0));
}
}
case SHADE->{
if(ready(offensiveCooldown,attacker.getUniqueId(),5*SEC) &&
java.util.concurrent.ThreadLocalRandom.current().nextDouble()<.28)
victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,40,0));
}
case TIDE->{
if(attacker.isInWater() && ready(offensiveCooldown,attacker.getUniqueId(),5*SEC))
attacker.setHealth(Math.min(attacker.getMaxHealth(),attacker.getHealth()+2.0));
}
default->{}
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
if(k==Kind.PHOENIX){
int affected=0;
for(Entity target:p.getWorld().getNearbyEntities(p.getLocation(),4,3,4)){
if(!(target instanceof Monster m)||m.isDead())continue;
m.setFireTicks(Math.max(m.getFireTicks(),70));
if(++affected>=8)break;
}
}
return;
}
if(k==Kind.GUARDIAN&&ready(defensiveCooldown,p.getUniqueId(),6*SEC)){
p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,60,0));
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
void shutdown(){
offensiveCooldown.clear();defensiveCooldown.clear();
}
String status(){return "5 new talismans, awarded="+awarded+", migrated="+converted+", rescues="+rescued;}
}
