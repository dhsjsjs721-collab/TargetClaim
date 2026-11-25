package dev.unified;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

final class Goblins implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey key=new NamespacedKey("minecraftunified","goblin");
private final NamespacedKey costume=new NamespacedKey("minecraftunified","goblin_costume");
private final Set<String> woods=Set.of("forest","birch_forest","old_growth_birch_forest","dark_forest","flower_forest","taiga","old_growth_pine_taiga","old_growth_spruce_taiga","snowy_taiga","pale_garden");
private final Set<Material> floor=Set.of(Material.GRASS_BLOCK,Material.DIRT,Material.COARSE_DIRT,Material.ROOTED_DIRT,Material.PODZOL,Material.MYCELIUM,Material.MOSS_BLOCK);
Goblins(UnifiedPlugin h){host=h;}
void enable(){
Bukkit.getPluginManager().registerEvents(this,host);
Bukkit.getScheduler().runTaskTimer(host,this::populate,100L,240L);
for(World w:Bukkit.getWorlds())for(Zombie z:w.getEntitiesByClass(Zombie.class))if(isGoblin(z))configure(z,false);
host.getLogger().info("Goblins enabled: forest night packs, mushroom field packs, 14 HP, iron swords, custom costumes.");
}
boolean isGoblin(Entity e){return e instanceof Zombie&&e.getPersistentDataContainer().has(key,PersistentDataType.BYTE);}
private int variant(Zombie z){return z.getPersistentDataContainer().getOrDefault(key,PersistentDataType.BYTE,(byte)0)==1?1:0;}
private void attribute(Zombie z,Attribute a,double n){var v=z.getAttribute(a);if(v!=null)v.setBaseValue(n);}
private ItemStack skin(Material material,int style){
ItemStack i=new ItemStack(material);var m=i.getItemMeta();NamespacedKey model=new NamespacedKey("minecraftunified",style==1?"goblin_mushroom":"goblin_forest");
m.getPersistentDataContainer().set(costume,PersistentDataType.BYTE,(byte)1);
m.setAttributeModifiers(null);
EquipmentSlotGroup slot=switch(material){case LEATHER_HELMET->EquipmentSlotGroup.HEAD;case LEATHER_CHESTPLATE->EquipmentSlotGroup.CHEST;case LEATHER_LEGGINGS->EquipmentSlotGroup.LEGS;default->EquipmentSlotGroup.FEET;};
m.addAttributeModifier(Attribute.ARMOR,new AttributeModifier(new NamespacedKey("minecraftunified","goblin_skin_"+material.name().toLowerCase(Locale.ROOT)),0,AttributeModifier.Operation.ADD_NUMBER,slot));
m.setUnbreakable(true);var eq=m.getEquippable();eq.setModel(model);m.setEquippable(eq);
i.setItemMeta(m);return i;
}
private void configure(Zombie z,boolean fresh){
int style=variant(z);
z.setBaby(false);z.setCanBreakDoors(false);z.setCanPickupItems(false);z.setShouldBurnInDay(false);z.setRemoveWhenFarAway(true);
z.setInvisible(true);
attribute(z,Attribute.SCALE,.68);attribute(z,Attribute.MAX_HEALTH,14);attribute(z,Attribute.MOVEMENT_SPEED,.32);attribute(z,Attribute.ATTACK_DAMAGE,1.5);attribute(z,Attribute.ARMOR,0);attribute(z,Attribute.ARMOR_TOUGHNESS,0);attribute(z,Attribute.SPAWN_REINFORCEMENTS,0);attribute(z,Attribute.FOLLOW_RANGE,28);
if(fresh)z.setHealth(14);else if(z.getHealth()>14)z.setHealth(14);
var eq=z.getEquipment();eq.setHelmet(skin(Material.LEATHER_HELMET,style));eq.setChestplate(skin(Material.LEATHER_CHESTPLATE,style));eq.setLeggings(skin(Material.LEATHER_LEGGINGS,style));eq.setBoots(skin(Material.LEATHER_BOOTS,style));eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));eq.setItemInOffHand(null);
eq.setHelmetDropChance(0);eq.setChestplateDropChance(0);eq.setLeggingsDropChance(0);eq.setBootsDropChance(0);eq.setItemInMainHandDropChance(0);eq.setItemInOffHandDropChance(0);
z.customName(net.kyori.adventure.text.Component.text(style==1?"Грибной гоблин":"Гоблин",net.kyori.adventure.text.format.NamedTextColor.GREEN));z.setCustomNameVisible(false);
}
private int habitat(Location l){
String biome=l.getBlock().getBiome().getKey().getKey();
return biome.equals("mushroom_fields")?1:woods.contains(biome)?0:-1;
}
private boolean valid(Location l,boolean natural,int style){
World w=l.getWorld();if(w==null||w.getEnvironment()!=World.Environment.NORMAL||!w.isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)||host.isPeace(l)||w.getDifficulty()==Difficulty.PEACEFUL)return false;
var claim=host.claimModule();if(claim==null||claim.isClaimed(l))return false;
if(l.getBlockY()<=w.getMinHeight()||l.getBlockY()>=w.getMaxHeight()-2)return false;
Block feet=l.getBlock(),head=feet.getRelative(0,1,0),base=feet.getRelative(0,-1,0);
if(!feet.isPassable()||feet.isLiquid()||!head.isPassable()||head.isLiquid()||!base.getType().isSolid()||feet.getType()==Material.FIRE||feet.getType()==Material.SOUL_FIRE||feet.getType()==Material.POWDER_SNOW)return false;
if(natural){
if(!floor.contains(base.getType())||habitat(l)!=style||style==0&&feet.getLightLevel()>7)return false;
for(Player p:w.getPlayers())if(p.getLocation().distanceSquared(l)<24*24)return false;
}
return w.getNearbyLivingEntities(l,.65,.9,.65).isEmpty();
}
private Location surface(World w,int x,int z){
if(!w.isChunkLoaded(x>>4,z>>4))return null;
return new Location(w,x+.5,w.getHighestBlockYAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1,z+.5);
}
private boolean spawn(Location l,int style){
Zombie z=l.getWorld().spawn(l,Zombie.class,CreatureSpawnEvent.SpawnReason.CUSTOM,false,m->{m.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)style);configure(m,true);});
return z.isValid()&&!z.isDead();
}
private void populate(){
var rng=ThreadLocalRandom.current();
for(World w:Bukkit.getWorlds()){
if(w.getEnvironment()!=World.Environment.NORMAL||w.getDifficulty()==Difficulty.PEACEFUL||!Boolean.TRUE.equals(w.getGameRuleValue(GameRules.SPAWN_MOBS))||!Boolean.TRUE.equals(w.getGameRuleValue(GameRules.SPAWN_MONSTERS)))continue;
int total=0;for(Zombie z:w.getEntitiesByClass(Zombie.class))if(isGoblin(z))total++;
int worldCap=Math.max(1,Math.min(100,host.getConfig().getInt("goblins.world-cap",48)));
for(Player p:w.getPlayers()){
if(total>=worldCap)break;if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE)continue;
Location pos=p.getLocation();int style=habitat(pos);if(style<0||style==0&&rng.nextInt(3)!=0)continue;
int cap=style==1?18:8;int near=w.getNearbyLivingEntities(pos,64,32,64,this::isGoblin).size();if(near>=cap)continue;
Location center=null;
for(int attempt=0;attempt<8;attempt++){
double a=rng.nextDouble()*Math.PI*2,r=rng.nextDouble(26,49);Location l=surface(w,pos.getBlockX()+(int)(Math.cos(a)*r),pos.getBlockZ()+(int)(Math.sin(a)*r));
if(l!=null&&valid(l,true,style)){center=l;break;}
}
if(center==null)continue;
int count=Math.min(Math.min(worldCap-total,cap-near),style==1?rng.nextInt(4,8):rng.nextInt(2,5));
int made=0;for(int attempt=0;attempt<Math.min(30,count*5)&&made<count;attempt++){
Location l=attempt==0?center:surface(w,center.getBlockX()+rng.nextInt(-4,5),center.getBlockZ()+rng.nextInt(-4,5));
if(l!=null&&valid(l,true,style)&&spawn(l,style)){made++;total++;}
}
}
}
}
boolean command(CommandSender s,String[] args){
if(!(s instanceof Player p)||!s.hasPermission("minecraftunified.goblin.admin")){s.sendMessage("§cНужны права администратора и игрок в мире.");return true;}
int count=1;try{if(args.length>0)count=Math.max(1,Math.min(12,Integer.parseInt(args[0])));}catch(NumberFormatException ex){s.sendMessage("§e/u goblin [1–12] [forest|mushroom]");return true;}
int style=args.length>1&&args[1].equalsIgnoreCase("mushroom")?1:0;
int made=0;for(int a=0;a<40&&made<count;a++){Location l=p.getLocation().clone().add(ThreadLocalRandom.current().nextInt(-6,7),0,ThreadLocalRandom.current().nextInt(-6,7));l.setX(l.getBlockX()+.5);l.setZ(l.getBlockZ()+.5);l.setY(l.getBlockY());for(int dy:new int[]{0,1,-1,2,-2}){Location candidate=l.clone().add(0,dy,0);if(valid(candidate,false,style)&&candidate.distanceSquared(p.getLocation())>=4&&spawn(candidate,style)){made++;break;}}}
s.sendMessage("§aГоблинов создано: "+made+" / "+count+" (вне мирной зоны и приватов).");return true;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void convert(EntityTransformEvent e){if(isGoblin(e.getEntity()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(isGoblin(e.getEntity()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void death(EntityDeathEvent e){
if(!isGoblin(e.getEntity()))return;
Zombie z=(Zombie)e.getEntity();e.getDrops().clear();e.setDroppedExp(0);
if(z.getKiller()==null)return;
var rng=ThreadLocalRandom.current();e.setDroppedExp(5);
if(rng.nextDouble()<.3)e.getDrops().add(new ItemStack(Material.IRON_NUGGET,rng.nextInt(1,4)));
if(rng.nextDouble()<.2)e.getDrops().add(new ItemStack(Material.ROTTEN_FLESH));
if(variant(z)==1&&rng.nextDouble()<.4)e.getDrops().add(new ItemStack(rng.nextBoolean()?Material.RED_MUSHROOM:Material.BROWN_MUSHROOM,rng.nextInt(1,4)));
if(rng.nextDouble()<.035){ItemStack sword=new ItemStack(Material.IRON_SWORD);var m=(Damageable)sword.getItemMeta();m.setDamage(rng.nextInt(175,245));sword.setItemMeta(m);e.getDrops().add(sword);}
}
@EventHandler public void load(ChunkLoadEvent e){for(Entity entity:e.getChunk().getEntities())if(isGoblin(entity))configure((Zombie)entity,false);}
}
