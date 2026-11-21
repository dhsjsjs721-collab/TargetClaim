package dev.enchantmentforge;

import org.bukkit.Bukkit;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

final class WanderingTraderFeature implements Listener {
private record Offer(ItemStack result, int diamonds, int maxUses) {}
private final JavaPlugin plugin;
private final NamespacedKey charmKey;
private final NamespacedKey traderKey;
private final ItemStack soulboundBook;
private final ItemStack tripleShotBook;
private final Random random=new Random();
private final Map<UUID,Long> nextJourney=new HashMap<>();

WanderingTraderFeature(JavaPlugin plugin, ItemStack soulboundBook, ItemStack tripleShotBook) {
this.plugin=plugin;
this.charmKey=new NamespacedKey(plugin,"inventory_charm");
this.traderKey=new NamespacedKey(plugin,"improved_trader_v4_rotating");
this.soulboundBook=soulboundBook.clone();
this.tripleShotBook=tripleShotBook.clone();
}

void start() {
Bukkit.getPluginManager().registerEvents(this,plugin);
// Fix traders already loaded when the server starts.
Bukkit.getScheduler().runTaskLater(plugin,()->{
for (World world:Bukkit.getWorlds()) {
for (WanderingTrader trader:world.getEntitiesByClass(WanderingTrader.class)) configure(trader);
for (TraderLlama llama:world.getEntitiesByClass(TraderLlama.class)) {
if (llama.isLeashed() && llama.getLeashHolder() instanceof WanderingTrader) llama.remove();
}
}
},2L);
// New keys intentionally override legacy 25-minute configs on existing servers.
long interval=Math.max(2,Math.min(30,plugin.getConfig().getLong("trader-journey-spawn-minutes",5)));
Bukkit.getScheduler().runTaskTimer(plugin,this::periodicSpawn,interval*1200L,interval*1200L);
Bukkit.getScheduler().runTaskTimer(plugin,this::travelTick,140L,160L);
}

ItemStack charm() {
ItemStack item=new ItemStack(Material.ECHO_SHARD);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName("§bОберег возвращения");
meta.setLore(List.of("§7При смерти сохраняет весь инвентарь", "§7включая броню и вторую руку.", "§eРасходуется 1 шт. при смерти.", "§8Максимальный стак: 8", "§8Опыт не сохраняется."));
meta.setMaxStackSize(8);
meta.getPersistentDataContainer().set(charmKey,PersistentDataType.BYTE,(byte)1);
item.setItemMeta(meta);
return item;
}

private boolean isCharm(ItemStack item) {
if (item==null || item.getType()!=Material.ECHO_SHARD || !item.hasItemMeta()) return false;
Byte b=item.getItemMeta().getPersistentDataContainer().get(charmKey,PersistentDataType.BYTE);
return b!=null && b==(byte)1;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void death(PlayerDeathEvent event) {
if (event.getKeepInventory()) return;
List<ItemStack> drops=event.getDrops();
int tokenIndex=-1;
for (int i=0;i<drops.size();i++) {
if (isCharm(drops.get(i))) {tokenIndex=i;break;}
}
if (tokenIndex<0) return;
// Use Paper's native itemsToKeep so item slots are preserved where possible.
// Don't call setKeepInventory(true): that would duplicate plugin-managed drops.
List<ItemStack> preserved=new ArrayList<>();
for (int i=0;i<drops.size();i++) {
ItemStack item=drops.get(i);
if (item==null || item.getType().isAir() || item.getAmount()<1) continue;
ItemStack copy=item.clone();
if (i==tokenIndex) {
if (copy.getAmount()==1) continue;
copy.setAmount(copy.getAmount()-1);
}
preserved.add(copy);
}
drops.clear();
event.getItemsToKeep().addAll(preserved);
event.getEntity().sendMessage("§aОберег возвращения потрачен: вещи сохранены, опыт — нет.");
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void spawning(CreatureSpawnEvent event) {
if (event.getEntity() instanceof TraderLlama) {
event.setCancelled(true);
return;
}
if (!(event.getEntity() instanceof WanderingTrader trader)) return;
// A tick later, after vanilla has assigned any default offers.
Bukkit.getScheduler().runTaskLater(plugin,()->{
if (trader.isValid() && !trader.isDead()) configure(trader);
},1L);
}

@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void traderRightClick(PlayerInteractEntityEvent event){
if(event.getHand()!=EquipmentSlot.HAND)return;
if(event.getRightClicked() instanceof WanderingTrader trader)configure(trader);
}
@EventHandler(priority=EventPriority.MONITOR)
public void traderEntitiesLoaded(EntitiesLoadEvent event){
for(org.bukkit.entity.Entity entity:event.getEntities()){
if(entity instanceof WanderingTrader trader)configure(trader);
if(entity instanceof TraderLlama llama&&llama.isLeashed()&&llama.getLeashHolder() instanceof WanderingTrader)llama.remove();
}
}
private void configure(WanderingTrader trader) {
if (!trader.isValid()||trader.isDead()) return;
if (!trader.hasAI()) trader.setAI(true);
trader.setAware(true);
if (trader.getPersistentDataContainer().has(traderKey,PersistentDataType.BYTE)) return;
trader.setCustomName("§6Странствующий торговец");
trader.setCustomNameVisible(false);
trader.setRecipes(makeOffers());
trader.getPersistentDataContainer().set(traderKey,PersistentDataType.BYTE,(byte)1);
}

private ItemStack independentClaim(){
ItemStack item=new ItemStack(Material.TARGET);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName("§6§lНезависимый приват");
meta.setLore(List.of("§7Размер: §e96×96×96 §7(как уровень 2)","§cНе улучшается","§7Занимает один из 2 приватов","§6Цена: 20 алмазов"));
meta.getPersistentDataContainer().set(new NamespacedKey("targetclaim","claim_target"),PersistentDataType.BYTE,(byte)1);
meta.getPersistentDataContainer().set(new NamespacedKey("targetclaim","independent_claim"),PersistentDataType.BYTE,(byte)1);
item.setItemMeta(meta);
return item;
}
private int fairPrice(Offer offer){
Material type=offer.result().getType();
int base=offer.diamonds();
if(type==Material.TARGET)return 20;
if(isCharm(offer.result()))return 5;
// Price by rarity and number of items in each bundle, not one flat
// discount across rare tools, spawners and ordinary crops.
if(type==Material.SPAWNER||type==Material.ELYTRA||type==Material.TRIDENT||
type==Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE||type==Material.TOTEM_OF_UNDYING)return base;
if(type==Material.ENCHANTED_BOOK)return Math.max(8,Math.round(base*.75f));
if(type.name().endsWith("_SPAWN_EGG"))return Math.max(10,Math.round(base*.75f));
if(type==Material.SUGAR||type==Material.SUGAR_CANE||type==Material.WHEAT_SEEDS||
type==Material.POTATO||type==Material.CARROT||type==Material.KELP||
type==Material.CACTUS||type==Material.PAPER||type==Material.BAMBOO||
type==Material.COBBLESTONE||type==Material.STONE_BRICKS||
type==Material.SAND||type==Material.GRAVEL||type==Material.TORCH)return Math.max(1,(int)Math.ceil(base*.38));
if(type.isEdible())return Math.max(1,(int)Math.ceil(base*.50));
if(base<=12)return Math.max(2,(int)Math.round(base*.65));
if(base<=35)return Math.max(5,(int)Math.round(base*.80));
return base;
}
private MerchantRecipe recipe(Offer offer) {
MerchantRecipe result=new MerchantRecipe(offer.result().clone(),offer.maxUses());
result.setExperienceReward(false);
result.setPriceMultiplier(0.0f);
// These two custom enchantments have fixed barter costs; they never use diamonds.
if (offer.result().isSimilar(soulboundBook)) {
result.addIngredient(new ItemStack(Material.AMETHYST_BLOCK,1));
result.addIngredient(new ItemStack(Material.BOOK,1));
return result;
}
if (offer.result().isSimilar(tripleShotBook)) {
result.addIngredient(new ItemStack(Material.DIAMOND_BLOCK,1));
result.addIngredient(new ItemStack(Material.BOOK,1));
return result;
}
int price=fairPrice(offer);
if(offer.result().getType()!=Material.TARGET&&!isCharm(offer.result())){
double variation=.88+random.nextDouble()*.25; // ~-12% to +13%
price=(int)Math.round(price*variation);
}
price=Math.max(1,Math.min(128,price));
int first=Math.min(64,price);
result.addIngredient(new ItemStack(Material.DIAMOND,first));
if (price>64) result.addIngredient(new ItemStack(Material.DIAMOND,price-64));
return result;
}

private void add(List<Offer> list,Material material,int count,int price,int uses) {
if (material.isItem() && count>0 && count<=material.getMaxStackSize()) list.add(new Offer(new ItemStack(material,count),price,uses));
}
private void add(List<Offer> list,String material,int count,int price,int uses) {
Material type=Material.matchMaterial(material);
if (type!=null) add(list,type,count,price,uses);
}
private void addBook(List<Offer> list,Enchantment enchant,int level,int price) {
if (enchant==null) return;
ItemStack item=new ItemStack(Material.ENCHANTED_BOOK);
EnchantmentStorageMeta meta=(EnchantmentStorageMeta)item.getItemMeta();
meta.addStoredEnchant(enchant,level,false);
item.setItemMeta(meta);
list.add(new Offer(item,price,2));
}
private void addEgg(List<Offer> list,String material,int price) {
add(list,material,1,price,2);
}
private ItemStack spawner(EntityType type,String name) {
ItemStack item=new ItemStack(Material.SPAWNER);
BlockStateMeta meta=(BlockStateMeta)item.getItemMeta();
CreatureSpawner state=(CreatureSpawner)meta.getBlockState();
state.setSpawnedType(type);
meta.setBlockState(state);
meta.setDisplayName("§dСпавнер: "+name);
item.setItemMeta(meta);
return item;
}
private void addSpawner(List<Offer> list,EntityType type,String name,int price) {
list.add(new Offer(spawner(type,name),price,1));
}

private List<MerchantRecipe> makeOffers() {
List<Offer> selected=new ArrayList<>();
List<Offer> pool=new ArrayList<>();
// Every merchant offers the fixed-price independent claim. The remaining
// catalog changes between individual traders rather than flooding the UI.
selected.add(new Offer(independentClaim(),20,2));
List<Offer> featured=new ArrayList<>();
featured.add(new Offer(charm(),5,3));
featured.add(new Offer(soulboundBook.clone(),28,3));
featured.add(new Offer(tripleShotBook.clone(),24,3));
add(featured,Material.TOTEM_OF_UNDYING,1,40,2);
add(featured,Material.ENDER_EYE,4,18,4);
add(featured,Material.MAP,8,5,6);
add(featured,Material.COOKED_BEEF,32,6,8);
// One guaranteed functional mob spawner.
List<Offer> spawners=new ArrayList<>();
addSpawner(spawners,EntityType.ZOMBIE,"Зомби",88);
addSpawner(spawners,EntityType.SKELETON,"Скелет",92);
addSpawner(spawners,EntityType.SPIDER,"Паук",84);
addSpawner(spawners,EntityType.BLAZE,"Ифрит",112);
addSpawner(spawners,EntityType.CAVE_SPIDER,"Пещерный паук",96);
// Rare merchant specialization: at most one random spawner, never all five.
if(random.nextDouble()<.22)selected.add(spawners.get(random.nextInt(spawners.size())));

// Ordinary useful goods, sold in convenient bundles.
add(pool,Material.GOLDEN_CARROT,32,10,6);
add(pool,Material.BREAD,64,6,8);
add(pool,Material.BAKED_POTATO,64,5,8);
add(pool,Material.COOKED_PORKCHOP,32,6,8);
add(pool,Material.COOKED_CHICKEN,32,5,8);
add(pool,Material.COOKED_SALMON,32,7,7);
add(pool,Material.PUMPKIN_PIE,32,6,6);
add(pool,Material.GOLDEN_APPLE,4,18,3);
add(pool,Material.ARROW,64,5,8);
add(pool,Material.SPECTRAL_ARROW,32,8,6);
add(pool,Material.FIREWORK_ROCKET,32,7,7);
add(pool,Material.ENDER_PEARL,16,16,4);
add(pool,Material.OBSIDIAN,32,14,5);
add(pool,Material.CRYING_OBSIDIAN,16,14,3);
add(pool,Material.EXPERIENCE_BOTTLE,32,18,4);
add(pool,Material.LAPIS_LAZULI,64,7,7);
add(pool,Material.REDSTONE,64,5,7);
add(pool,Material.GLOWSTONE,32,9,6);
add(pool,Material.QUARTZ,64,12,6);
add(pool,Material.SHULKER_SHELL,2,32,2);
add(pool,Material.NAUTILUS_SHELL,4,22,3);
add(pool,Material.BLAZE_ROD,16,18,4);
add(pool,Material.GHAST_TEAR,4,20,2);
add(pool,Material.SLIME_BALL,32,12,4);
add(pool,Material.HONEY_BLOCK,16,8,6);
add(pool,Material.LEATHER,32,9,6);
add(pool,Material.STRING,64,8,6);
add(pool,Material.SADDLE,1,12,3);
add(pool,Material.NAME_TAG,4,10,4);
add(pool,Material.COMPASS,2,6,6);
add(pool,Material.RECOVERY_COMPASS,1,28,2);
add(pool,Material.CLOCK,2,6,5);
add(pool,Material.CARTOGRAPHY_TABLE,2,5,4);
add(pool,Material.LODESTONE,1,28,2);
add(pool,Material.ENDER_CHEST,1,18,3);
add(pool,Material.ANVIL,1,12,3);
add(pool,Material.ELYTRA,1,116,1);
add(pool,Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,1,64,1);
add(pool,Material.TRIDENT,1,62,1);
add(pool,Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE,1,36,1);
add(pool,Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,1,28,1);
add(pool,Material.NETHER_WART,32,12,4);
add(pool,Material.BREWING_STAND,1,10,4);
add(pool,Material.BLAZE_POWDER,32,18,4);
add(pool,Material.GUNPOWDER,32,12,5);
add(pool,Material.BONE_MEAL,64,5,7);
// Rotating inexpensive farming, building and crafting supplies.
add(pool,Material.SUGAR_CANE,32,6,8);
add(pool,Material.SUGAR,32,6,8);
add(pool,Material.BAMBOO,64,5,8);
add(pool,Material.CACTUS,32,6,6);
add(pool,Material.KELP,64,5,8);
add(pool,Material.WHEAT,64,6,8);
add(pool,Material.WHEAT_SEEDS,64,4,8);
add(pool,Material.POTATO,64,6,8);
add(pool,Material.CARROT,64,6,8);
add(pool,Material.BEETROOT,32,5,8);
add(pool,Material.PUMPKIN,32,7,7);
add(pool,Material.MELON_SLICE,64,6,8);
add(pool,Material.COCOA_BEANS,32,7,7);
add(pool,Material.SWEET_BERRIES,32,5,7);
add(pool,Material.GLOW_BERRIES,32,7,7);
add(pool,Material.BROWN_MUSHROOM,16,5,8);
add(pool,Material.RED_MUSHROOM,16,5,8);
add(pool,Material.OAK_SAPLING,16,5,6);
add(pool,Material.SPRUCE_SAPLING,16,5,6);
add(pool,Material.PAPER,64,5,8);
add(pool,Material.TORCH,64,5,8);
add(pool,Material.GLASS,64,6,6);
add(pool,Material.SAND,64,5,8);
add(pool,Material.GRAVEL,64,5,8);
add(pool,Material.CLAY_BALL,64,6,8);
add(pool,Material.STONE_BRICKS,64,6,8);
add(pool,Material.COBBLESTONE,64,4,8);

// Every non-curse vanilla enchantment gets a genuine maximum-level book.
// Read the server registry to include new enchantments added in future 26.3 updates.
for (Enchantment enchant:RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
if (!"minecraft".equals(enchant.getKey().getNamespace()) || enchant.isCursed()) continue;
int level=enchant.getMaxLevel();
int price=Math.min(58,18+level*3+(enchant.isTreasure()?10:0));
addBook(pool,enchant,level,price);
}

// Spawn eggs use vanilla item types, no unsafe hard-coded NBT.
addEgg(pool,"COW_SPAWN_EGG",22);
addEgg(pool,"SHEEP_SPAWN_EGG",20);
addEgg(pool,"CHICKEN_SPAWN_EGG",20);
addEgg(pool,"PIG_SPAWN_EGG",20);
addEgg(pool,"HORSE_SPAWN_EGG",26);
addEgg(pool,"VILLAGER_SPAWN_EGG",52);
addEgg(pool,"WOLF_SPAWN_EGG",28);
addEgg(pool,"CAT_SPAWN_EGG",24);
addEgg(pool,"BEE_SPAWN_EGG",24);
addEgg(pool,"FOX_SPAWN_EGG",24);
addEgg(pool,"RABBIT_SPAWN_EGG",20);
addEgg(pool,"ALLAY_SPAWN_EGG",48);
addEgg(pool,"AXOLOTL_SPAWN_EGG",32);
addEgg(pool,"FROG_SPAWN_EGG",24);
addEgg(pool,"TURTLE_SPAWN_EGG",30);
addEgg(pool,"SNIFFER_SPAWN_EGG",56);
addEgg(pool,"BLAZE_SPAWN_EGG",38);
addEgg(pool,"SLIME_SPAWN_EGG",34);
addEgg(pool,"ZOMBIE_SPAWN_EGG",30);
addEgg(pool,"SKELETON_SPAWN_EGG",30);
// Each trader has a stable but different selection of 14-21 offers total.
// Shuffling occurs ONCE when the entity is configured, never on clicks.
Collections.shuffle(featured,random);
selected.addAll(featured.subList(0,Math.min(featured.size(),2+random.nextInt(3))));
Collections.shuffle(pool,random);
int offered=Math.min(pool.size(),11+random.nextInt(5));
selected.addAll(pool.subList(0,offered));
List<MerchantRecipe> recipes=new ArrayList<>();
for (Offer offer:selected) recipes.add(recipe(offer));
return recipes;
}

private int maxTraders(World world){
return Math.max(1,Math.min(6,plugin.getConfig().getInt("trader-journey-max-per-world",4)));
}
private List<Villager> nearbyVillagers(Location place,double distance){
List<Villager> found=new ArrayList<>();
if(place.getWorld()==null)return found;
for(Entity nearby:place.getWorld().getNearbyEntities(place,distance,18,distance)){
if(nearby instanceof Villager villager&&villager.isValid()&&!villager.isDead())found.add(villager);
}
return found;
}
private boolean inVillage(Player player){
// Vanilla villages are represented here by nearby loaded villagers.
// No synchronous village structure search and no chunk generation.
return nearbyVillagers(player.getLocation(),45).size()>=2;
}
private void periodicSpawn() {
List<Player> players=new ArrayList<>();
List<Player> villages=new ArrayList<>();
for(Player player:Bukkit.getOnlinePlayers()){
if(player.isDead()||player.getWorld().getEnvironment()!=World.Environment.NORMAL)continue;
if(player.getGameMode()==org.bukkit.GameMode.SPECTATOR)continue;
players.add(player);
if(inVillage(player))villages.add(player);
}
if(players.isEmpty())return;
Collections.shuffle(players,random);
Collections.shuffle(villages,random);
double preference=Math.max(0,Math.min(1,plugin.getConfig().getDouble("trader-journey-village-preference",.75)));
// Visit a village first in ~75% of intervals if an inhabited one is loaded.
boolean villageFirst=!villages.isEmpty()&&random.nextDouble()<preference;
List<Player> order=new ArrayList<>();
if(villageFirst)order.addAll(villages);
for(Player player:players)if(!order.contains(player))order.add(player);
for(Player player:order){
World world=player.getWorld();
if(world.getEntitiesByClass(WanderingTrader.class).size()>=maxTraders(world))continue;
if(!spawnNear(player,villageFirst&&inVillage(player)))continue;
// At most one merchant spawned in an interval, avoiding server floods.
break;
}
}
private Location walkable(Location center,int dx,int dz,int maxHeightDelta){
World world=center.getWorld();
if(world==null)return null;
int x=center.getBlockX()+dx,z=center.getBlockZ()+dz;
if(!world.isChunkLoaded(x>>4,z>>4))return null;
int y=world.getHighestBlockYAt(x,z);
if(y<=world.getMinHeight()||y>=world.getMaxHeight()-3||Math.abs(y-center.getBlockY())>maxHeightDelta)return null;
Block below=world.getBlockAt(x,y,z);
Block feet=world.getBlockAt(x,y+1,z);
Block head=world.getBlockAt(x,y+2,z);
if(!below.getType().isSolid()||below.isLiquid()
||feet.isLiquid()||head.isLiquid()
||!feet.isPassable()||!head.isPassable())return null;
Material surface=below.getType();
if(surface==Material.MAGMA_BLOCK||surface==Material.CACTUS||surface==Material.CAMPFIRE
||surface==Material.SOUL_CAMPFIRE||surface==Material.SWEET_BERRY_BUSH)return null;
Location point=new Location(world,x+.5,y+1,z+.5);
if(!world.getWorldBorder().isInside(point))return null;
return point;
}
private void travelTick(){
if(!plugin.getConfig().getBoolean("trader-journey-roam-enabled",true))return;
long now=System.currentTimeMillis();
int handled=0;
for(World world:Bukkit.getWorlds()){
if(world.getEnvironment()!=World.Environment.NORMAL)continue;
for(WanderingTrader trader:world.getEntitiesByClass(WanderingTrader.class)){
if(handled++>=30)return; // bounded work across all worlds
if(!trader.isValid()||trader.isDead()||trader.isTrading()||!trader.hasAI()||!trader.isAware())continue;
UUID id=trader.getUniqueId();
if(now<nextJourney.getOrDefault(id,0L))continue;
nextJourney.put(id,now+13_000+random.nextInt(13_000));
Location start=trader.getLocation();
Location destination=null;
// In villages, wander between villagers' homes rather than pacing
// near the same position. Targets must be in already loaded chunks.
List<Villager> residents=nearbyVillagers(start,26);
if(!residents.isEmpty()&&random.nextDouble()<.7){
Villager resident=residents.get(random.nextInt(residents.size()));
Location home=resident.getLocation();
for(int attempt=0;attempt<8;attempt++){
destination=walkable(home,random.nextInt(9)-4,random.nextInt(9)-4,5);
if(destination!=null&&destination.distanceSquared(start)>16&&destination.distanceSquared(start)<28*28)break;
destination=null;
}
}
if(destination==null){
for(int attempt=0;attempt<10;attempt++){
int dx=(random.nextBoolean()?1:-1)*(5+random.nextInt(14));
int dz=(random.nextBoolean()?1:-1)*(5+random.nextInt(14));
destination=walkable(start,dx,dz,6);
if(destination!=null)break;
}
}
if(destination==null)continue;
double speed=Math.max(.35,Math.min(1.2,plugin.getConfig().getDouble("trader-journey-speed",.78)));
try{trader.getPathfinder().moveTo(destination,speed);}
catch(RuntimeException ex){plugin.getLogger().fine("Trader could not navigate to waypoint: "+ex.getMessage());}
}
}
// Keep bookkeeping bounded as traders despawn or chunks unload.
if(nextJourney.size()>128){
Set<UUID> active=new HashSet<>();
for(World world:Bukkit.getWorlds())
for(WanderingTrader trader:world.getEntitiesByClass(WanderingTrader.class))active.add(trader.getUniqueId());
nextJourney.keySet().retainAll(active);
}
}

// Direct administrator summon. Natural spawns still use the original distance
// and population rules; this bypasses random chunk checks.
boolean forceSpawnNear(Player player){
if(player.isDead())return false;
World world=player.getWorld();
Location center=player.getLocation().clone();
org.bukkit.util.Vector dir=center.getDirection().setY(0);
if(dir.lengthSquared()<.001)dir.setX(1);
Location preferred=center.clone().add(dir.normalize().multiply(2.3));
Location point=(preferred.getBlock().isPassable()&&preferred.clone().add(0,1,0).getBlock().isPassable())?preferred:center;
if(!world.getWorldBorder().isInside(point))return false;
WanderingTrader trader=world.spawn(point,WanderingTrader.class);
trader.setDespawnDelay(Math.max(24000,Math.min(144000,plugin.getConfig().getInt("trader-despawn-ticks",48000))));
configure(trader);
return true;
}
boolean spawnNear(Player player,boolean preferVillage) {
World world=player.getWorld();
if(world.getEnvironment()!=World.Environment.NORMAL)return false;
Location center=player.getLocation();
if(preferVillage){
List<Villager> villagers=nearbyVillagers(center,48);
if(villagers.size()>=2)center=villagers.get(random.nextInt(villagers.size())).getLocation();
}
for(int attempt=0;attempt<28;attempt++){
int minimum=preferVillage?7:18;
int maximum=preferVillage?20:43;
int dx=(random.nextBoolean()?1:-1)*(minimum+random.nextInt(maximum-minimum));
int dz=(random.nextBoolean()?1:-1)*(minimum+random.nextInt(maximum-minimum));
Location point=walkable(center,dx,dz,10);
if(point==null)continue;
WanderingTrader trader=world.spawn(point,WanderingTrader.class);
trader.setDespawnDelay(Math.max(24000,Math.min(144000,plugin.getConfig().getInt("trader-despawn-ticks",48000))));
configure(trader);
return true;
}
return false;
}
}
