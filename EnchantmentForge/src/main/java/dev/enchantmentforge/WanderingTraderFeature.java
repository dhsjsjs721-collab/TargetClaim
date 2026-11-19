package dev.enchantmentforge;

import org.bukkit.Bukkit;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
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
import java.util.Random;

final class WanderingTraderFeature implements Listener {
private record Offer(ItemStack result, int diamonds, int maxUses) {}
private final JavaPlugin plugin;
private final NamespacedKey charmKey;
private final NamespacedKey traderKey;
private final ItemStack soulboundBook;
private final ItemStack tripleShotBook;
private final Random random=new Random();

WanderingTraderFeature(JavaPlugin plugin, ItemStack soulboundBook, ItemStack tripleShotBook) {
this.plugin=plugin;
this.charmKey=new NamespacedKey(plugin,"inventory_charm");
this.traderKey=new NamespacedKey(plugin,"improved_trader");
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
long interval=Math.max(5,Math.min(180,plugin.getConfig().getLong("trader-spawn-minutes",25)));
Bukkit.getScheduler().runTaskTimer(plugin,this::periodicSpawn,interval*1200L,interval*1200L);
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

private void configure(WanderingTrader trader) {
if (trader.getPersistentDataContainer().has(traderKey,PersistentDataType.BYTE)) return;
trader.setCustomName("§6Странствующий торговец");
trader.setCustomNameVisible(false);
trader.setRecipes(makeOffers());
trader.getPersistentDataContainer().set(traderKey,PersistentDataType.BYTE,(byte)1);
}

private MerchantRecipe recipe(Offer offer) {
MerchantRecipe result=new MerchantRecipe(offer.result().clone(),offer.maxUses());
result.setExperienceReward(false);
result.setPriceMultiplier(0.0f);
int first=Math.min(64,offer.diamonds());
result.addIngredient(new ItemStack(Material.DIAMOND,first));
if (offer.diamonds()>64) result.addIngredient(new ItemStack(Material.DIAMOND,offer.diamonds()-64));
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
// Guaranteed, useful offers on every trader.
selected.add(new Offer(charm(),36,3));
selected.add(new Offer(soulboundBook.clone(),28,3));
selected.add(new Offer(tripleShotBook.clone(),24,3));
add(selected,Material.TOTEM_OF_UNDYING,1,40,2);
add(selected,Material.ENDER_EYE,4,18,4);
add(selected,Material.MAP,8,5,6);
add(selected,Material.COOKED_BEEF,32,6,8);
// One guaranteed functional mob spawner.
List<Offer> spawners=new ArrayList<>();
addSpawner(spawners,EntityType.ZOMBIE,"Зомби",88);
addSpawner(spawners,EntityType.SKELETON,"Скелет",92);
addSpawner(spawners,EntityType.SPIDER,"Паук",84);
addSpawner(spawners,EntityType.BLAZE,"Ифрит",112);
addSpawner(spawners,EntityType.CAVE_SPIDER,"Пещерный паук",96);
Collections.shuffle(spawners,random);
selected.add(spawners.remove(0));
pool.addAll(spawners);

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
// Always include one egg, then draw additional offers from the remaining catalog.
List<Offer> eggs=new ArrayList<>();
for (Offer offer:pool) if (offer.result().getType().name().endsWith("_SPAWN_EGG")) eggs.add(offer);
if (!eggs.isEmpty()) {
Offer guaranteedEgg=eggs.get(random.nextInt(eggs.size()));
selected.add(guaranteedEgg);
pool.remove(guaranteedEgg);
}
Collections.shuffle(pool,random);
int target=Math.max(9,Math.min(45,plugin.getConfig().getInt("trader-offers-per-trader",26)));
for (Offer offer:pool) {
if (selected.size()>=target) break;
selected.add(offer);
}
List<MerchantRecipe> recipes=new ArrayList<>();
for (Offer offer:selected) recipes.add(recipe(offer));
return recipes;
}

private void periodicSpawn() {
List<Player> players=new ArrayList<>();
for (Player player:Bukkit.getOnlinePlayers()) {
if (player.getWorld().getEnvironment()==World.Environment.NORMAL && !player.isDead()) players.add(player);
}
if (players.isEmpty()) return;
Collections.shuffle(players,random);
for (Player player:players) {
World world=player.getWorld();
int limit=Math.max(1,Math.min(5,plugin.getConfig().getInt("trader-max-per-world",2)));
if (world.getEntitiesByClass(WanderingTrader.class).size()>=limit) continue;
if (!spawnNear(player)) continue;
if (random.nextDouble()<0.25 && world.getEntitiesByClass(WanderingTrader.class).size()<limit) spawnNear(player);
break; // Max one player selected per interval.
}
}

boolean spawnNear(Player player) {
World world=player.getWorld();
if (world.getEnvironment()!=World.Environment.NORMAL) return false;
Location center=player.getLocation();
for (int attempt=0;attempt<18;attempt++) {
int dx=(random.nextBoolean()?1:-1)*(22+random.nextInt(21));
int dz=(random.nextBoolean()?1:-1)*(22+random.nextInt(21));
int x=center.getBlockX()+dx;
int z=center.getBlockZ()+dz;
if (!world.isChunkLoaded(x>>4,z>>4)) continue;
int y=world.getHighestBlockYAt(x,z);
if (y<=world.getMinHeight() || y>=world.getMaxHeight()-3) continue;
Location point=new Location(world,x+0.5,y+1,z+0.5);
if (!world.getWorldBorder().isInside(point)) continue;
if (!world.getBlockAt(x,y,z).getType().isSolid()) continue;
if (!world.getBlockAt(x,y+1,z).getType().isAir() || !world.getBlockAt(x,y+2,z).getType().isAir()) continue;
WanderingTrader trader=world.spawn(point,WanderingTrader.class);
trader.setDespawnDelay(Math.max(24000,Math.min(144000,plugin.getConfig().getInt("trader-despawn-ticks",48000))));
return true;
}
return false;
}
}
