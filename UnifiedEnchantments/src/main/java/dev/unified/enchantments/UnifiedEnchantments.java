package dev.unified.enchantments;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Item-PDC based custom weapon runes, not invasive Minecraft enchant-registry edits.
 * Only reacts to melee damage events and explicit offhand rune applications.
 */
public final class UnifiedEnchantments extends JavaPlugin implements Listener {
private enum Rune {
VAMPIRISM("Вампиризм","vampirism","Вероятность исцеления при ударе",
Material.GHAST_TEAR,Material.DIAMOND,Material.GOLD_INGOT),
FROST("Мороз","frost","Замедляет поражённого моба",
Material.BLUE_ICE,Material.AMETHYST_SHARD,Material.LAPIS_LAZULI),
EMBER("Пламя","ember","Поджигает поражённого моба",
Material.BLAZE_ROD,Material.MAGMA_CREAM,Material.GOLD_INGOT);
final String title,key,description;
final Material[] components;
Rune(String title,String key,String description,Material... components){
this.title=title;this.key=key;this.description=description;this.components=components;
}
static Rune from(String name){
for(Rune r:values())if(r.key.equalsIgnoreCase(name))return r;
return null;
}
}
private NamespacedKey scrollKey,idKey;
private final Map<Rune,NamespacedKey> levels=new EnumMap<>(Rune.class);
private final Map<Rune,Double> chances=new EnumMap<>(Rune.class);
private long triggered,applied,denied,auditDuplicates;
private boolean enablePvp;
private int maxLevel;
@Override public void onEnable(){
saveDefaultConfig();
scrollKey=new NamespacedKey(this,"scroll");
idKey=new NamespacedKey(this,"weapon-id");
for(Rune rune:Rune.values()){
levels.put(rune,new NamespacedKey(this,"enchant-"+rune.key));
registerRecipe(rune);
}
reloadValues();
Bukkit.getPluginManager().registerEvents(this,this);
getLogger().info("Custom rune recipes active; no Minecraft registry replacement and no world scans.");
}
private void reloadValues(){
reloadConfig();
maxLevel=Math.max(1,Math.min(3,getConfig().getInt("runes.maximum-level",3)));
enablePvp=getConfig().getBoolean("runes.allow-pvp-effects",false);
for(Rune rune:Rune.values()){
chances.put(rune,Math.min(1,Math.max(0,getConfig().getDouble("runes."+rune.key+".chance-per-level",
rune==Rune.VAMPIRISM?0.08:rune==Rune.FROST?0.10:0.10))));
}
}
private ItemStack scroll(Rune rune){
ItemStack item=new ItemStack(Material.ENCHANTED_BOOK);
ItemMeta meta=item.getItemMeta();
meta.displayName(Component.text("Руна: "+rune.title,NamedTextColor.LIGHT_PURPLE));
meta.lore(List.of(Component.text(rune.description,NamedTextColor.GRAY),
Component.text("Возьми оружие в руку, руну во вторую руку, нажми ПКМ.",NamedTextColor.GRAY)));
meta.getPersistentDataContainer().set(scrollKey,PersistentDataType.STRING,rune.key);
item.setItemMeta(meta);
return item;
}
private void registerRecipe(Rune rune){
ShapelessRecipe recipe=new ShapelessRecipe(new NamespacedKey(this,"rune_"+rune.key),scroll(rune));
recipe.addIngredient(Material.BOOK);
for(Material ingredient:rune.components)recipe.addIngredient(ingredient);
Bukkit.addRecipe(recipe);
}
private Rune identifyRune(ItemStack item){
if(item==null||item.getType()!=Material.ENCHANTED_BOOK||!item.hasItemMeta())return null;
return Rune.from(item.getItemMeta().getPersistentDataContainer().get(scrollKey,PersistentDataType.STRING));
}
private boolean weapon(ItemStack item){
return item!=null && item.getAmount()==1 && EnchantRules.weapon(item.getType().name());
}
private int getLevel(ItemStack weapon,Rune rune){
if(!weapon(weapon)||!weapon.hasItemMeta())return 0;
Integer level=weapon.getItemMeta().getPersistentDataContainer().get(levels.get(rune),PersistentDataType.INTEGER);
return level==null?0:Math.max(0,Math.min(maxLevel,level));
}
private boolean setLevel(ItemStack weapon,Rune rune,int wanted){
if(!weapon(weapon))return false;
int next=EnchantRules.level(wanted);
ItemMeta meta=weapon.getItemMeta();
if(meta==null)return false;
PersistentDataContainer pdc=meta.getPersistentDataContainer();
pdc.set(levels.get(rune),PersistentDataType.INTEGER,next);
if(!pdc.has(idKey,PersistentDataType.STRING))
pdc.set(idKey,PersistentDataType.STRING,UUID.randomUUID().toString());
List<Component> lore=new ArrayList<>(meta.lore()==null?List.of():meta.lore());
for(int i=1;i<=3;i++)lore.remove(Component.text(rune.title+" "+i,NamedTextColor.LIGHT_PURPLE));
lore.add(Component.text(rune.title+" "+next,NamedTextColor.LIGHT_PURPLE));
meta.lore(lore);
weapon.setItemMeta(meta);
applied++;
return true;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void onUseRune(PlayerInteractEvent event){
if(event.getHand()!=EquipmentSlot.OFF_HAND)return;
Action a=event.getAction();
if(a!=Action.RIGHT_CLICK_AIR && a!=Action.RIGHT_CLICK_BLOCK)return;
Player player=event.getPlayer();
ItemStack offhand=player.getInventory().getItemInOffHand();
Rune rune=identifyRune(offhand);
if(rune==null)return;
event.setCancelled(true);
ItemStack weapon=player.getInventory().getItemInMainHand();
if(!weapon(weapon)){denied++;player.sendMessage("Держи меч, топор, копьё, трезубец или булаву в основной руке.");return;}
int level=getLevel(weapon,rune);
if(level>=maxLevel){denied++;player.sendMessage("Достигнут максимальный уровень руны.");return;}
if(!setLevel(weapon,rune,level+1)){denied++;return;}
player.getInventory().setItemInMainHand(weapon);
if(offhand.getAmount()==1)player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
else{offhand.setAmount(offhand.getAmount()-1);player.getInventory().setItemInOffHand(offhand);}
player.sendMessage("Добавлено: "+rune.title+" "+(level+1)+".");
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void onHit(EntityDamageByEntityEvent event){
if(!(event.getDamager() instanceof Player player)||!(event.getEntity() instanceof LivingEntity victim))return;
if(event.getFinalDamage()<=0 || (!enablePvp && victim instanceof Player))return;
ItemStack weapon=player.getInventory().getItemInMainHand();
if(!weapon(weapon))return;
for(Rune rune:Rune.values()){
int level=getLevel(weapon,rune);
if(level==0||ThreadLocalRandom.current().nextDouble()>=EnchantRules.chance(chances.get(rune),level))continue;
switch(rune){
case VAMPIRISM -> {
double amount=Math.max(0,Math.min(10,getConfig().getDouble("runes.vampirism.heal-per-level",1.0)*level));
if(amount>0)player.setHealth(Math.min(player.getMaxHealth(),player.getHealth()+amount));
}
case FROST -> {
int duration=Math.max(1,Math.min(200,getConfig().getInt("runes.frost.duration-ticks",60)));
victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,duration,0,true,false,true));
}
case EMBER -> {
int duration=Math.max(1,Math.min(200,getConfig().getInt("runes.ember.duration-ticks",50)));
victim.setFireTicks(Math.max(victim.getFireTicks(),duration));
}
}
triggered++;
}
}
private void audit(CommandSender sender){
HashSet<String> seen=new HashSet<>();
int checked=0,duplicate=0,malformed=0;
for(Player p:Bukkit.getOnlinePlayers())for(ItemStack item:p.getInventory().getContents()){
if(item==null||!item.hasItemMeta())continue;
String uuid=item.getItemMeta().getPersistentDataContainer().get(idKey,PersistentDataType.STRING);
if(uuid==null)continue;
checked++;
if(!EnchantRules.validId(uuid)){malformed++;continue;}
if(!seen.add(uuid))duplicate++;
}
auditDuplicates+=duplicate;
sender.sendMessage("UnifiedEnchantments audit: tagged="+checked+" duplicate item IDs="+duplicate+
" invalid IDs="+malformed+". NO items removed. This is NOT a full dupe prevention system.");
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length==0||args[0].equalsIgnoreCase("info")){
sender.sendMessage("UnifiedEnchantments 1.0: "+applied+" applied, "+triggered+" effects, "+denied+" rejected. Runes: vampirism, frost, ember.");
sender.sendMessage("Craft a rune book; hold weapon in main hand, rune in offhand, right click.");
return true;
}
if(args[0].equalsIgnoreCase("reload")){
if(!sender.hasPermission("unifiedenchantments.admin")){sender.sendMessage("No permission.");return true;}
reloadValues();sender.sendMessage("Chances reloaded.");return true;
}
if(args[0].equalsIgnoreCase("audit")){
if(!sender.hasPermission("unifiedenchantments.admin")){sender.sendMessage("No permission.");return true;}
audit(sender);return true;
}
if(args.length>=2 && args[0].equalsIgnoreCase("give")){
if(!sender.hasPermission("unifiedenchantments.admin")){sender.sendMessage("No permission.");return true;}
Rune rune=Rune.from(args[1]);
Player target=args.length>=3?Bukkit.getPlayerExact(args[2]):sender instanceof Player p?p:null;
if(rune==null||target==null){sender.sendMessage("Usage: /uenchant give <vampirism|frost|ember> [player]");return true;}
Map<Integer,ItemStack> rest=target.getInventory().addItem(scroll(rune));
for(ItemStack item:rest.values())target.getWorld().dropItemNaturally(target.getLocation(),item);
sender.sendMessage("Gave rune "+rune.key+" to "+target.getName());return true;
}
sender.sendMessage("Usage: /uenchant [info|audit|reload|give <rune> [player]]");
return true;
}
}
