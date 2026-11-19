package dev.enchantmentforge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class EnchantmentForgePlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private enum Enchant {
SOULBOUND("soulbound", "§bНеутрачиваемость I", "Сохраняется при смерти", "soulbound-anvil-cost", 8),
TRIPLE_SHOT("triple_shot", "§6Тройная стрела I", "1–3 стрелы в зависимости от натяжения", "triple-shot-anvil-cost", 6);
final String id;
final String title;
final String description;
final String costKey;
final int defaultCost;
Enchant(String id, String title, String description, String costKey, int defaultCost) {
this.id=id;this.title=title;this.description=description;this.costKey=costKey;this.defaultCost=defaultCost;
}
static Enchant parse(String input) {
for (Enchant enchant: values()) if (enchant.id.equalsIgnoreCase(input)) return enchant;
if ("triple".equalsIgnoreCase(input) || "trishot".equalsIgnoreCase(input)) return TRIPLE_SHOT;
if ("keep".equalsIgnoreCase(input)) return SOULBOUND;
return null;
}
}
private NamespacedKey bookKey;
private NamespacedKey soulboundKey;
private NamespacedKey tripleShotKey;
private WanderingTraderFeature traderFeature;

@Override public void onEnable() {
saveDefaultConfig();
bookKey=new NamespacedKey(this,"enchanted_book_kind");
soulboundKey=new NamespacedKey(this,"soulbound");
tripleShotKey=new NamespacedKey(this,"triple_shot");
Bukkit.getPluginManager().registerEvents(this,this);
traderFeature=new WanderingTraderFeature(this,book(Enchant.SOULBOUND),book(Enchant.TRIPLE_SHOT));
traderFeature.start();
PluginCommand command=getCommand("enchantmentforge");
if (command!=null) {command.setExecutor(this);command.setTabCompleter(this);}
getLogger().info("EnchantmentForge active: Soulbound + Triple Shot + Diamond Wandering Trader");
}

private NamespacedKey key(Enchant enchant) {
return enchant==Enchant.SOULBOUND?soulboundKey:tripleShotKey;
}
private boolean has(ItemStack item, Enchant enchant) {
if (item==null || item.getType().isAir() || !item.hasItemMeta()) return false;
Byte flag=item.getItemMeta().getPersistentDataContainer().get(key(enchant),PersistentDataType.BYTE);
return flag!=null && flag==(byte)1;
}
private Enchant bookKind(ItemStack item) {
if (item==null || item.getType()!=Material.ENCHANTED_BOOK || !item.hasItemMeta()) return null;
String id=item.getItemMeta().getPersistentDataContainer().get(bookKey,PersistentDataType.STRING);
return id==null?null:Enchant.parse(id);
}
private ItemStack book(Enchant enchant) {
ItemStack item=new ItemStack(Material.ENCHANTED_BOOK);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName("§dКнига: "+enchant.title);
meta.setLore(List.of("§8Особое зачарование", "§7"+enchant.description, "§7Применение: §fнаковальня"));
meta.getPersistentDataContainer().set(bookKey,PersistentDataType.STRING,enchant.id);
item.setItemMeta(meta);
return item;
}
private boolean canApply(ItemStack base, Enchant enchant) {
if (base==null || base.getType().isAir() || base.getAmount()!=1) return false;
if (base.getType()==Material.BOOK || base.getType()==Material.ENCHANTED_BOOK) return false;
return enchant!=Enchant.TRIPLE_SHOT || base.getType()==Material.BOW;
}
private ItemStack apply(ItemStack base, Enchant enchant) {
ItemStack result=base.clone();
ItemMeta meta=result.getItemMeta();
if (meta==null) return result;
meta.getPersistentDataContainer().set(key(enchant),PersistentDataType.BYTE,(byte)1);
List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
if (!lore.contains(enchant.title)) lore.add(enchant.title);
meta.setLore(lore);
meta.setEnchantmentGlintOverride(true);
result.setItemMeta(meta);
return result;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void prepareAnvil(PrepareAnvilEvent event) {
ItemStack base=event.getInventory().getItem(0);
ItemStack ingredient=event.getInventory().getItem(1);
Enchant enchant=bookKind(ingredient);
if (enchant==null) return;
if (!canApply(base,enchant) || has(base,enchant)) {event.setResult(null);return;}
ItemStack output=apply(base,enchant);
String rename=event.getView().getRenameText();
if (rename!=null && !rename.isBlank()) {
ItemMeta meta=output.getItemMeta();
if (meta!=null) {meta.setDisplayName(rename);output.setItemMeta(meta);}
}
int cost=Math.max(1,getConfig().getInt(enchant.costKey,enchant.defaultCost));
event.getView().setMaximumRepairCost(Math.max(40,cost+1));
event.getView().setRepairCost(cost);
event.setResult(output);
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void prepareGrindstone(PrepareGrindstoneEvent event) {
ItemStack output=event.getResult();
if (output==null || (!has(output,Enchant.SOULBOUND) && !has(output,Enchant.TRIPLE_SHOT))) return;
ItemStack clean=output.clone();
ItemMeta meta=clean.getItemMeta();
if (meta==null) return;
meta.getPersistentDataContainer().remove(soulboundKey);
meta.getPersistentDataContainer().remove(tripleShotKey);
if (meta.hasLore()) {
List<String> lore=new ArrayList<>(meta.getLore());
lore.remove(Enchant.SOULBOUND.title);
lore.remove(Enchant.TRIPLE_SHOT.title);
meta.setLore(lore.isEmpty()?null:lore);
}
meta.setEnchantmentGlintOverride(null);
clean.setItemMeta(meta);
event.setResult(clean);
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void keepOnDeath(PlayerDeathEvent event) {
if (event.getKeepInventory()) return;
Iterator<ItemStack> drops=event.getDrops().iterator();
while (drops.hasNext()) {
ItemStack item=drops.next();
if (!has(item,Enchant.SOULBOUND)) continue;
drops.remove();
event.getItemsToKeep().add(item);
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void shootBow(EntityShootBowEvent event) {
if (!(event.getEntity() instanceof Player player)) return;
if (!has(event.getBow(),Enchant.TRIPLE_SHOT)) return;
if (!(event.getProjectile() instanceof AbstractArrow original)) return;
if (event.getBow().getType()!=Material.BOW) return;

// Minecraft reports bow draw strength between 0 and 1.
double medium=Math.max(0.01,Math.min(0.99,getConfig().getDouble("triple-shot-medium-force",0.50)));
double full=Math.max(medium,Math.min(1.0,getConfig().getDouble("triple-shot-full-force",0.90)));
double force=event.getForce();
int count=force>=full?3:force>=medium?2:1;
if (count==1) return;

Vector velocity=original.getVelocity().clone();
if (velocity.lengthSquared()<0.0001) return;
double spread=Math.max(0,Math.min(35,getConfig().getDouble("triple-shot-spread-degrees",10.0)));
Vector side=new Vector(-velocity.getZ(),0,velocity.getX());
if (side.lengthSquared()<0.0001) side=new Vector(1,0,0);
side.normalize().multiply(0.18);
Location center=original.getLocation();

// A medium draw has two symmetrically angled arrows;
// a full draw preserves the original central arrow and adds one on each side.
double[] extraAngles;
if (count==2) {
original.setVelocity(velocity.clone().rotateAroundY(Math.toRadians(-spread/2.0)));
extraAngles=new double[]{spread/2.0};
} else {
extraAngles=new double[]{-spread,spread};
}
for (double angle:extraAngles) {
Location spawn=center.clone().add(side.clone().multiply(angle<0?-1:1));
Entity entity=player.getWorld().spawnEntity(spawn,original.getType());
if (!(entity instanceof AbstractArrow extra)) {entity.remove();continue;}
extra.setShooter(player);
extra.setWeapon(event.getBow().clone());
extra.setVelocity(velocity.clone().rotateAroundY(Math.toRadians(angle)));
extra.setDamage(original.getDamage());
extra.setCritical(original.isCritical());
extra.setPierceLevel(original.getPierceLevel());
extra.setFireTicks(original.getFireTicks());
extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
if (original instanceof Arrow source && extra instanceof Arrow copy) {
copy.setBasePotionType(source.getBasePotionType());
copy.setColor(source.getColor());
for (PotionEffect effect:source.getCustomEffects()) copy.addCustomEffect(effect,true);
}
}
}

@Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
if (args.length==0 || "help".equalsIgnoreCase(args[0])) {
sender.sendMessage("§6EnchantmentForge §7| §f/ce give <ник> <soulbound|triple_shot|charm> [кол-во]");
sender.sendMessage("§7/ce apply <soulbound|triple_shot> §8(предмет в руке, только OP)");
sender.sendMessage("§7/ce trader §8(призвать торговца рядом с собой)");
sender.sendMessage("§7Книгу можно наложить на предмет в наковальне.");
return true;
}
if (!sender.hasPermission("enchantmentforge.admin")) {sender.sendMessage("§cНедостаточно прав.");return true;}
if ("trader".equalsIgnoreCase(args[0])) {
if (!(sender instanceof Player player)) {sender.sendMessage("§cКоманда только для игрока.");return true;}
boolean spawned=traderFeature.spawnNear(player);
sender.sendMessage(spawned?"§aТорговец появился неподалёку.":"§cНе удалось найти безопасное место в загруженных чанках.");
return true;
}
if ("give".equalsIgnoreCase(args[0])) {
if (args.length<3) {sender.sendMessage("§c/ce give <ник> <soulbound|triple_shot|charm> [кол-во]");return true;}
Player target=Bukkit.getPlayerExact(args[1]);
if (target==null) {sender.sendMessage("§cИгрок не в сети.");return true;}
Enchant enchant=Enchant.parse(args[2]);
boolean charm="charm".equalsIgnoreCase(args[2]) || "inventory_charm".equalsIgnoreCase(args[2]);
if (enchant==null && !charm) {sender.sendMessage("§cНеизвестный предмет.");return true;}
int count=1;
if (args.length>=4) {
try {count=Integer.parseInt(args[3]);}
catch (NumberFormatException ex) {sender.sendMessage("§cУкажи число от 1 до 64.");return true;}
}
if (count<1 || count>64) {sender.sendMessage("§cУкажи число от 1 до 64.");return true;}
for (int i=0;i<count;i++) {
for (ItemStack leftover:target.getInventory().addItem(charm?traderFeature.charm():book(enchant)).values())
target.getWorld().dropItemNaturally(target.getLocation(),leftover);
}
sender.sendMessage("§aВыдано: "+(charm?"charm":enchant.id)+" x"+count+" игроку "+target.getName());
return true;
}
if ("apply".equalsIgnoreCase(args[0])) {
if (!(sender instanceof Player player)) {sender.sendMessage("§cКоманда доступна игроку.");return true;}
if (args.length<2) {sender.sendMessage("§c/ce apply <soulbound|triple_shot>");return true;}
Enchant enchant=Enchant.parse(args[1]);
ItemStack held=player.getInventory().getItemInMainHand();
if (enchant==null || !canApply(held,enchant) || has(held,enchant)) {
sender.sendMessage("§cЭто зачарование нельзя наложить на текущий предмет.");return true;
}
player.getInventory().setItemInMainHand(apply(held,enchant));
sender.sendMessage("§aЗачаровано: "+enchant.title);
return true;
}
sender.sendMessage("§cНеизвестная команда. /ce help");
return true;
}

@Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
if (args.length==1) return List.of("give","apply","trader","help");
if (!sender.hasPermission("enchantmentforge.admin")) return List.of();
if (args.length==2 && "give".equalsIgnoreCase(args[0])) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
if (args.length==2 && "apply".equalsIgnoreCase(args[0])) return List.of("soulbound","triple_shot");
if (args.length==3 && "give".equalsIgnoreCase(args[0])) return List.of("soulbound","triple_shot","charm");
if (args.length==4 && "give".equalsIgnoreCase(args[0])) return List.of("1","2","4","8");
return List.of();
}
}
