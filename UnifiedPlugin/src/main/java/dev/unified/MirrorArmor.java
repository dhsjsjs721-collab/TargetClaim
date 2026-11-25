package dev.unified;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class MirrorArmor implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey id=new NamespacedKey("minecraftunified","mirror_chestplate"),recipe=id,armor=new NamespacedKey("minecraftunified","mirror_armor"),toughness=new NamespacedKey("minecraftunified","mirror_toughness");
private record Reflected(UUID target,UUID owner,double damage){}
private Reflected reflected;
MirrorArmor(UnifiedPlugin host){this.host=host;}
void enable(){
Bukkit.getPluginManager().registerEvents(this,host);
Bukkit.removeRecipe(recipe);
ShapedRecipe r=new ShapedRecipe(recipe,item());
r.shape("G G","GGG","GGG");r.setIngredient('G',Material.TINTED_GLASS);
Bukkit.addRecipe(r);host.getLogger().info("MirrorArmor enabled: 1/3 full damage reflection; 7 armor, 1 toughness, 400 durability.");
}
void disable(){Bukkit.removeRecipe(recipe);}
ItemStack item(){
ItemStack i=new ItemStack(Material.IRON_CHESTPLATE);var m=i.getItemMeta();
m.setDisplayName("§b§lЗеркальный нагрудник");
m.setLore(List.of("§7Защита: 7 · Твёрдость: 1","§bПримерно 1 из 3 ударов отражается полностью","§7Отражённый урон не проходит по владельцу","§7Без атакующего — полное поглощение","§7Прочность: 400"));
m.getPersistentDataContainer().set(id,PersistentDataType.BYTE,(byte)1);
m.setItemModel(id);var eq=m.getEquippable();eq.setModel(id);m.setEquippable(eq);
m.setAttributeModifiers(null);
m.addAttributeModifier(Attribute.ARMOR,new AttributeModifier(armor,7,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));
m.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,new AttributeModifier(toughness,1,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));
if(m instanceof Damageable d)d.setMaxDamage(400);
i.setItemMeta(m);return i;
}
private boolean mirror(ItemStack i){return i!=null&&i.getType()==Material.IRON_CHESTPLATE&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(id,PersistentDataType.BYTE);}
boolean give(CommandSender sender,Player p){
if(!sender.hasPermission("minecraftunified.mirror.admin")){sender.sendMessage("§cНет прав.");return true;}
for(ItemStack extra:p.getInventory().addItem(item()).values())p.getWorld().dropItemNaturally(p.getLocation(),extra);
sender.sendMessage("§bЗеркальный нагрудник выдан: "+p.getName());return true;
}
@EventHandler(priority=EventPriority.HIGH)
public void smith(PrepareSmithingEvent e){
if(mirror(e.getInventory().getInputEquipment()))e.setResult(null);
}
private LivingEntity source(EntityDamageEvent e){
Entity attacker=e.getDamageSource().getCausingEntity();
if(attacker instanceof LivingEntity l)return l;
if(e instanceof EntityDamageByEntityEvent by){
Entity d=by.getDamager();
if(d instanceof LivingEntity l)return l;
if(d instanceof Projectile p&&p.getShooter() instanceof LivingEntity l)return l;
if(d instanceof TNTPrimed t&&t.getSource() instanceof LivingEntity l)return l;
}
return null;
}
private boolean permitted(Player owner,LivingEntity attacker){
if(attacker==null||attacker==owner)return false;
if(attacker instanceof Player p){
var claims=host.claimModule();
return !host.isPeace(p.getLocation())&&!host.isPeace(owner.getLocation())&&claims!=null&&claims.allowsTalismanDamage(p,owner)&&claims.allowsTalismanDamage(owner,p)&&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR;
}
var claims=host.claimModule();return claims!=null&&claims.allowsTemporaryStructure(owner,attacker.getLocation())&&!host.isPeace(attacker.getLocation());
}
boolean isReflection(EntityDamageEvent e){Reflected r=reflected;return r!=null&&e.getEntity().getUniqueId().equals(r.target())&&e.getDamageSource().getDamageType()==DamageType.THORNS&&e.getDamageSource().getCausingEntity() instanceof Player p&&p.getUniqueId().equals(r.owner());}
@SuppressWarnings("deprecation")
@EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
public void exactDamage(EntityDamageEvent e){
Reflected r=reflected;
if(!isReflection(e))return;
for(EntityDamageEvent.DamageModifier mod:EntityDamageEvent.DamageModifier.values())if(e.isApplicable(mod))e.setDamage(mod,mod==EntityDamageEvent.DamageModifier.BASE?r.damage():0);
}
@EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
public void reflect(EntityDamageEvent e){
if(reflected!=null||!(e.getEntity() instanceof Player p)||p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE||!mirror(p.getInventory().getChestplate())||e.getCause().name().equals("KILL")||e.getFinalDamage()<=0||!Double.isFinite(e.getFinalDamage()))return;
LivingEntity attacker=source(e);
if(attacker instanceof Player other&&other!=p&&!permitted(p,other))return;
if(ThreadLocalRandom.current().nextInt(3)!=0)return;
double amount=e.getFinalDamage();
e.setCancelled(true);
ItemStack chest=p.getInventory().getChestplate();
if(chest!=null&&chest.getItemMeta() instanceof Damageable d){
int wear=Math.max(1,(int)Math.ceil(e.getDamage()/4));
int unbreaking=chest.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
int actual=0;for(int n=0;n<Math.min(400,wear);n++)if(unbreaking==0||ThreadLocalRandom.current().nextInt(unbreaking+1)==0)actual++;
if(!d.isUnbreakable()&&actual>0){int damage=d.getDamage()+actual;if(damage>=d.getMaxDamage()){p.getInventory().setChestplate(null);p.playSound(p.getLocation(),Sound.ENTITY_ITEM_BREAK,1,1);}else{d.setDamage(damage);chest.setItemMeta(d);p.getInventory().setChestplate(chest);}}
}
Location l=p.getLocation().add(0,1.1,0);World w=p.getWorld();
for(int n=0;n<20;n++){double a=n*Math.PI/10;w.spawnParticle(Particle.DUST,l.clone().add(Math.cos(a)*.85,Math.sin(a*2)*.2,Math.sin(a)*.85),1,0,0,0,0,new Particle.DustOptions(Color.fromRGB(n%2==0?0xD8F6FF:0x8E80D9),.9f));}
w.spawnParticle(Particle.END_ROD,l,8,.35,.5,.35,.02);w.playSound(l,Sound.BLOCK_GLASS_HIT,.9f,1.6f);
if(attacker==null||!attacker.isValid()||attacker.isDead()||!permitted(p,attacker))return;
reflected=new Reflected(attacker.getUniqueId(),p.getUniqueId(),amount);
int oldTicks=attacker.getNoDamageTicks();
try{
attacker.setNoDamageTicks(0);
attacker.damage(amount,DamageSource.builder(DamageType.THORNS).withDirectEntity(p).withCausingEntity(p).build());
}finally{
reflected=null;
if(!attacker.isDead()&&attacker.getNoDamageTicks()<oldTicks)attacker.setNoDamageTicks(oldTicks);
}
}
}
