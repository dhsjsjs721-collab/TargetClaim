package dev.unified;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.*;
import org.bukkit.potion.PotionEffect;
import java.io.*;
import java.nio.file.*;
import java.util.*;

final class TestWorld implements Listener {
static final String WORLD="unified_test";
private final UnifiedPlugin host;
private final File dir;
private final Set<UUID> switching=new HashSet<>();
private World world;
private final TestTargets targets;
TestWorld(UnifiedPlugin h){host=h;dir=new File(h.getDataFolder(),"test-sessions");dir.mkdirs();targets=new TestTargets(h);}
void enable(){Bukkit.getPluginManager().registerEvents(this,host);targets.enable();}
private File file(Player p){return new File(dir,p.getUniqueId()+".yml");}
private boolean session(Player p){return file(p).isFile();}
private boolean in(World w){return w!=null&&WORLD.equals(w.getName());}
boolean command(CommandSender sender,String[] args){
if(args.length==1&&args[0].equalsIgnoreCase("setup")&&sender instanceof ConsoleCommandSender){try{ensure();int filled=0;for(int x=-12;x<=-5;x++)for(int z=-6;z<=1;z++)if(world.getBlockAt(x,81,z).getState() instanceof Chest c&&!c.getBlockInventory().isEmpty())filled++;if(filled<3||targets.count()!=4)throw new IllegalStateException("missing test chests or targets");sender.sendMessage("SELFTEST test world OK: "+filled+" stocked chests, "+targets.count()+" targets");}catch(Exception e){host.getLogger().severe("Test world setup failed: "+e);sender.sendMessage("§cОшибка создания тестовой платформы.");}return true;}
if(!(sender instanceof Player p)||!p.isOp()){sender.sendMessage("§cТолько OP в игре.");return true;}
if(args.length>0&&args[0].equalsIgnoreCase("exit")||args.length==0&&in(p.getWorld())){restore(p);return true;}
if(args.length>0&&Set.of("survival","creative","reset").contains(args[0].toLowerCase(Locale.ROOT))){if(!in(p.getWorld())||!session(p)){p.sendMessage("§cСначала /test.");return true;}if(args[0].equalsIgnoreCase("reset")){targets.reset();p.sendMessage("§aЦели восстановлены.");}else{p.setGameMode(args[0].equalsIgnoreCase("creative")?GameMode.CREATIVE:GameMode.SURVIVAL);p.setFallDistance(0);}return true;}
if(args.length>0&&args[0].equalsIgnoreCase("refill")){
if(!in(p.getWorld())||!session(p)){p.sendMessage("§cСначала /test.");return true;}
stock();p.sendMessage("§aСундуки пополнены.");return true;}
if(args.length>0){p.sendMessage("§e/test · /test exit · /test refill");return true;}
if(session(p)){if(!restore(p))return true;}
try{ensure();}catch(Exception ex){p.sendMessage("§cНе удалось создать тестовый мир.");host.getLogger().warning("Test world: "+ex);return true;}
p.closeInventory();if(!snapshot(p))return true;
clear(p);
switching.add(p.getUniqueId());boolean ok;try{ok=p.teleport(new Location(world,.5,81,.5));}finally{switching.remove(p.getUniqueId());}
if(!ok){restore(p);return true;}
p.setGameMode(GameMode.SURVIVAL);p.setFlying(false);p.setAllowFlight(false);p.setFlySpeed(.1f);p.setHealth(p.getMaxHealth());p.setFoodLevel(20);
p.sendMessage("§dТестовый мир · /test creative|survival · /test reset · /test exit.");
return true;
}
private boolean snapshot(Player p){
YamlConfiguration y=new YamlConfiguration();y.set("location",p.getLocation());y.set("mode",p.getGameMode().name());y.set("inventory",Arrays.asList(p.getInventory().getContents()));y.set("ender",Arrays.asList(p.getEnderChest().getContents()));y.set("level",p.getLevel());y.set("exp",p.getExp());y.set("total",p.getTotalExperience());y.set("health",p.getHealth());y.set("food",p.getFoodLevel());y.set("saturation",p.getSaturation());y.set("flight",p.getAllowFlight());y.set("flying",p.isFlying());y.set("speed",p.getFlySpeed());y.set("walk",p.getWalkSpeed());y.set("effects",new ArrayList<>(p.getActivePotionEffects()));
File dst=file(p),tmp=new File(dir,dst.getName()+".tmp");
try{y.save(tmp);try{Files.move(tmp.toPath(),dst.toPath(),StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp.toPath(),dst.toPath());}return true;}catch(IOException ex){p.sendMessage("§cНе удалось сохранить инвентарь. Вход отменён.");return false;}
}
private void clear(Player p){p.getInventory().clear();p.getEnderChest().clear();p.setItemOnCursor(new ItemStack(Material.AIR));p.setLevel(0);p.setExp(0);p.setTotalExperience(0);for(PotionEffect e:p.getActivePotionEffects())p.removePotionEffect(e.getType());}
private ItemStack[] items(List<?> list,int size){ItemStack[] result=new ItemStack[size];if(list!=null)for(int i=0;i<Math.min(size,list.size());i++)if(list.get(i) instanceof ItemStack item)result[i]=item.clone();return result;}
boolean restore(Player p){
if(!session(p)){if(in(p.getWorld())){clear(p);switching.add(p.getUniqueId());try{p.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());}finally{switching.remove(p.getUniqueId());}}return true;}
YamlConfiguration y=YamlConfiguration.loadConfiguration(file(p));if(!y.contains("inventory")||!y.contains("mode")){p.sendMessage("§cРезервная копия тестовой сессии повреждена. Обратись к администратору.");return false;}
Location destination;try{destination=y.getLocation("location");}catch(Exception ex){destination=null;}if(destination==null||!destination.isWorldLoaded()||in(destination.getWorld()))destination=Bukkit.getWorlds().getFirst().getSpawnLocation();
p.closeInventory();clear(p);
switching.add(p.getUniqueId());
try{
if(!p.teleport(destination)){p.sendMessage("§cНе удалось вернуться. Повтори /test exit.");return false;}
p.setGameMode(GameMode.valueOf(y.getString("mode","SURVIVAL")));p.setAllowFlight(y.getBoolean("flight"));p.setFlying(y.getBoolean("flight")&&y.getBoolean("flying"));p.setFlySpeed((float)y.getDouble("speed",.1));p.setWalkSpeed((float)y.getDouble("walk",.2));
p.getInventory().setContents(items(y.getList("inventory"),p.getInventory().getSize()));p.getEnderChest().setContents(items(y.getList("ender"),p.getEnderChest().getSize()));
p.setTotalExperience(y.getInt("total"));p.setLevel(y.getInt("level"));p.setExp((float)y.getDouble("exp"));p.setHealth(Math.max(1,Math.min(p.getMaxHealth(),y.getDouble("health",20))));p.setFoodLevel(y.getInt("food",20));p.setSaturation((float)y.getDouble("saturation",5));for(Object e:y.getList("effects",List.of()))if(e instanceof PotionEffect effect)p.addPotionEffect(effect);
p.setFallDistance(0);p.saveData();Files.delete(file(p).toPath());p.sendMessage("§aВозвращён исходный инвентарь.");return true;
}catch(Exception ex){host.getLogger().warning("Test restore failed for "+p.getUniqueId()+": "+ex);p.sendMessage("§cОшибка восстановления. Резервная копия сохранена.");return false;}finally{switching.remove(p.getUniqueId());}
}
private void ensure(){
if(world!=null){targets.setup(world);return;}world=Bukkit.getWorld(WORLD);
if(world==null){world=new WorldCreator(WORLD).environment(World.Environment.NORMAL).generateStructures(false).generator(new ChunkGenerator(){
@Override public boolean shouldGenerateNoise(){return false;}
@Override public boolean shouldGenerateSurface(){return false;}
@Override public boolean shouldGenerateBedrock(){return false;}
@Override public boolean shouldGenerateCaves(){return false;}
@Override public boolean shouldGenerateDecorations(){return false;}
@Override public boolean shouldGenerateMobs(){return false;}
@Override public boolean shouldGenerateStructures(){return false;}
}).createWorld();}
if(world==null)throw new IllegalStateException("Cannot create world");
world.setSpawnLocation(0,81,0);world.setDifficulty(Difficulty.NORMAL);world.setTime(6000);world.setStorm(false);
world.setGameRule(GameRule.DO_MOB_SPAWNING,false);world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);world.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){world.getBlockAt(x,80,z).setType(Math.abs(x)==16||Math.abs(z)==16?Material.POLISHED_BLACKSTONE:Material.SMOOTH_STONE,false);if(Math.abs(x)==16||Math.abs(z)==16)world.getBlockAt(x,81,z).setType(Material.GLASS,false);}
stock();targets.setup(world);
}
private void chest(int x,int z,String name,List<ItemStack> content){var b=world.getBlockAt(x,81,z);b.setType(Material.CHEST,false);Chest c=(Chest)b.getState();c.setCustomName("§d"+name);c.update(true,false);Inventory inv=c.getBlockInventory();inv.clear();for(ItemStack i:content)if(i!=null&&!i.getType().isAir())inv.addItem(i);}
private void stock(){
List<ItemStack> crafted=new ArrayList<>();Iterator<Recipe> it=Bukkit.recipeIterator();Set<String> namespaces=Set.of("minecraftunified","targetclaim","talismanforge","enchantmentforge","swampsky");
while(it.hasNext()){Recipe r=it.next();if(r instanceof Keyed key&&namespaces.contains(key.getKey().getNamespace()))crafted.add(r.getResult().clone());}
crafted.sort(Comparator.comparing(i->i.getType().name()));
for(int i=0;i<crafted.size();i+=27)chest(-10+(i/27)*3,-5,"Кастомные крафты "+(i/27+1),crafted.subList(i,Math.min(i+27,crafted.size())));
chest(-10,-1,"Мифические реликвии",host.testRelics());
ItemStack disc=new ItemStack(Material.MUSIC_DISC_13);var meta=disc.getItemMeta();meta.setDisplayName("§6Тестовая пластинка YouTube");meta.setItemModel(new NamespacedKey("minecraftunified","youtube_disc"));meta.getPersistentDataContainer().set(new NamespacedKey("minecraftunified","youtube_disc"),org.bukkit.persistence.PersistentDataType.STRING,"https://www.youtube.com/watch?v=MmB9b5njVbA");meta.setLore(List.of("§7Shift+ПКМ — открыть ссылку","§7/disc — сменить запись"));disc.setItemMeta(meta);chest(-7,-5,"Медиа",List.of(disc,new ItemStack(Material.JUKEBOX),new ItemStack(Material.MAP),new ItemStack(Material.FILLED_MAP)));
chest(-7,-1,"Материалы и проверка",List.of(new ItemStack(Material.DIAMOND,64),new ItemStack(Material.DRAGON_EGG,16),new ItemStack(Material.TOTEM_OF_UNDYING,16),new ItemStack(Material.PHANTOM_MEMBRANE,64),new ItemStack(Material.EXPERIENCE_BOTTLE,64),new ItemStack(Material.SHIELD),new ItemStack(Material.BOW),new ItemStack(Material.ARROW,64),new ItemStack(Material.DIAMOND_SWORD),new ItemStack(Material.TORCH,64),new ItemStack(Material.MUSIC_DISC_13),new ItemStack(Material.JUKEBOX),new ItemStack(Material.MAP),new ItemStack(Material.ENDER_CHEST),new ItemStack(Material.AMETHYST_SHARD,64),new ItemStack(Material.ZOMBIE_SPAWN_EGG,16),new ItemStack(Material.SKELETON_SPAWN_EGG,16)));
}
@EventHandler(priority=EventPriority.LOWEST) public void join(PlayerJoinEvent e){if(session(e.getPlayer()))restore(e.getPlayer());else if(in(e.getPlayer().getWorld()))restore(e.getPlayer());}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){Player p=e.getPlayer();if(switching.contains(p.getUniqueId())||e.getTo()==null)return;if(in(e.getFrom().getWorld())!=in(e.getTo().getWorld())){e.setCancelled(true);p.sendMessage("§cИспользуй /test для входа и /test exit для выхода.");}}
@EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent e){if(in(e.getEntity().getWorld())&&session(e.getEntity())){e.setKeepInventory(true);e.getDrops().clear();e.setKeepLevel(true);e.setDroppedExp(0);}}
@EventHandler(priority=EventPriority.HIGHEST) public void respawn(PlayerRespawnEvent e){if(session(e.getPlayer())&&world!=null)e.setRespawnLocation(world.getSpawnLocation());}
@EventHandler(priority=EventPriority.HIGHEST) public void ender(InventoryOpenEvent e){if(in(e.getPlayer().getWorld())&&e.getInventory().getType()==org.bukkit.event.inventory.InventoryType.ENDER_CHEST)e.setCancelled(true);}
void disable(){for(Player p:Bukkit.getOnlinePlayers())if(session(p))restore(p);}
}
