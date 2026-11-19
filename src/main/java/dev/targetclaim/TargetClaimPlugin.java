package dev.targetclaim;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import java.io.*;
import java.util.*;

public final class TargetClaimPlugin extends JavaPlugin implements Listener {
private static final int[] SIZE = {16,40,80,120,160};
private static final Material[] COST = {Material.AMETHYST_SHARD,Material.AMETHYST_SHARD,Material.NETHERITE_SCRAP,Material.DRAGON_EGG};
private static final int[] AMOUNT = {23,64,3,1};
private final Map<UUID,Claim> claims = new HashMap<>();
private NamespacedKey key;
private File file;

private static final class Claim {
UUID owner,world;
String name;
int x,y,z,level;
boolean teamUse=true,teamBuild=true,pvp=false;
Claim(UUID owner,String name,Location loc){
this.owner=owner;this.name=name;this.world=loc.getWorld().getUID();
this.x=loc.getBlockX();this.y=loc.getBlockY();this.z=loc.getBlockZ();
}
int minX(){return x-SIZE[level]/2;}
int minZ(){return z-SIZE[level]/2;}
int maxX(){return minX()+SIZE[level]-1;}
int maxZ(){return minZ()+SIZE[level]-1;}
boolean contains(Location p){return p!=null&&p.getWorld()!=null&&p.getWorld().getUID().equals(world)&&p.getBlockX()>=minX()&&p.getBlockX()<=maxX()&&p.getBlockZ()>=minZ()&&p.getBlockZ()<=maxZ();}
boolean isCore(Block b){return b.getWorld().getUID().equals(world)&&b.getX()==x&&b.getY()==y&&b.getZ()==z;}
}
private static final class Menu implements InventoryHolder {
Inventory inv;UUID owner;boolean confirm;
Menu(UUID owner,boolean confirm){this.owner=owner;this.confirm=confirm;}
public Inventory getInventory(){return inv;}
}
@Override public void onEnable(){
key=new NamespacedKey(this,"claim_target");
file=new File(getDataFolder(),"claims.yml");getDataFolder().mkdirs();
load();
ShapedRecipe recipe=new ShapedRecipe(new NamespacedKey(this,"claim_recipe"),target());
recipe.shape("PPP","AAA","AAA");
recipe.setIngredient('P',Material.PAPER);
recipe.setIngredient('A',Material.AMETHYST_SHARD);
Bukkit.addRecipe(recipe);
Bukkit.getPluginManager().registerEvents(this,this);
Objects.requireNonNull(getCommand("p")).setExecutor(this::command);
getLogger().info("Loaded "+claims.size()+" claims");
}
@Override public void onDisable(){save();}
private ItemStack target(){
ItemStack item=new ItemStack(Material.TARGET);
ItemMeta meta=item.getItemMeta();
meta.setDisplayName("§d§lМишень привата");
meta.setLore(List.of("§7Один приват на игрока","§7ПКМ — настройки","§7При удалении не выпадает"));
meta.getPersistentDataContainer().set(key,PersistentDataType.BYTE,(byte)1);
item.setItemMeta(meta);return item;
}
private boolean special(ItemStack i){
return i!=null&&i.getType()==Material.TARGET&&i.hasItemMeta()&&Byte.valueOf((byte)1).equals(i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.BYTE));
}
private void save(){
YamlConfiguration c=new YamlConfiguration();
for(Claim q:claims.values()){
String p="claims."+q.owner+".";
c.set(p+"name",q.name);c.set(p+"world",q.world.toString());
c.set(p+"x",q.x);c.set(p+"y",q.y);c.set(p+"z",q.z);
c.set(p+"level",q.level);c.set(p+"teamUse",q.teamUse);
c.set(p+"teamBuild",q.teamBuild);c.set(p+"pvp",q.pvp);
}
try{c.save(file);}catch(IOException e){getLogger().severe("Claim save error "+e.getMessage());}
}
private void load(){
if(!file.isFile())return;
YamlConfiguration c=YamlConfiguration.loadConfiguration(file);
ConfigurationSection s=c.getConfigurationSection("claims");if(s==null)return;
for(String id:s.getKeys(false)){
try{
UUID owner=UUID.fromString(id),world=UUID.fromString(c.getString("claims."+id+".world"));
World w=Bukkit.getWorld(world);if(w==null){getLogger().warning("Unknown world for "+id);continue;}
String p="claims."+id+".";
Claim q=new Claim(owner,c.getString(p+"name","?"),new Location(w,c.getInt(p+"x"),c.getInt(p+"y"),c.getInt(p+"z")));
q.level=Math.max(0,Math.min(4,c.getInt(p+"level")));
q.teamUse=c.getBoolean(p+"teamUse",true);
q.teamBuild=c.getBoolean(p+"teamBuild",true);
q.pvp=c.getBoolean(p+"pvp",false);
claims.put(owner,q);
}catch(Exception e){getLogger().warning("Malformed claim "+id);}
}
}
private Claim at(Location loc){
for(Claim q:claims.values())if(q.contains(loc))return q;
return null;
}
private Claim core(Block block){
for(Claim q:claims.values())if(q.isCore(block))return q;
return null;
}
private boolean overlap(Claim c){
for(Claim q:claims.values()){
if(q.owner.equals(c.owner)||!q.world.equals(c.world))continue;
if(c.minX()<=q.maxX()&&c.maxX()>=q.minX()&&c.minZ()<=q.maxZ()&&c.maxZ()>=q.minZ())return true;
}
return false;
}
private boolean owner(Player p,Claim c){return p.getUniqueId().equals(c.owner)||p.hasPermission("targetclaim.admin");}
private Team team(String name){
return Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(name);
}
private boolean ally(Player p,Claim c){
Team a=team(c.name),b=team(p.getName());
return a!=null&&b!=null&&a.getColor()!=ChatColor.RESET&&a.getColor()==b.getColor();
}
private boolean canBuild(Player p,Claim c){return owner(p,c)||(c.teamBuild&&ally(p,c));}
private boolean canUse(Player p,Claim c){return owner(p,c)||(c.teamUse&&ally(p,c));}
private boolean command(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length>0&&args[0].equalsIgnoreCase("give")){
if(!sender.hasPermission("targetclaim.admin")){sender.sendMessage("§cНет прав");return true;}
if(args.length<2){sender.sendMessage("§e/p give <ник>");return true;}
Player p=Bukkit.getPlayerExact(args[1]);
if(p==null){sender.sendMessage("§cИгрок не найден");return true;}
Map<Integer,ItemStack> rest=p.getInventory().addItem(target());
for(ItemStack item:rest.values())p.getWorld().dropItemNaturally(p.getLocation(),item);
sender.sendMessage("§aМишень выдана");return true;
}
if(!(sender instanceof Player p)){sender.sendMessage("Только игрок");return true;}
Claim c=claims.get(p.getUniqueId());
if(c==null){p.sendMessage("§dСоздай мишень из 3 бумаги и 6 аметистов. Лимит: 1.");return true;}
if(args.length>0&&args[0].equalsIgnoreCase("border")){border(p,c);return true;}
if(args.length>0&&args[0].equalsIgnoreCase("remove")){menu(p,c,true);return true;}
menu(p,c,false);return true;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void place(BlockPlaceEvent e){
Block b=e.getBlockPlaced();Player p=e.getPlayer();
Claim old=at(b.getLocation());
if(old!=null&&!canBuild(p,old)){e.setCancelled(true);p.sendMessage("§cЧужой приват.");return;}
if(!special(e.getItemInHand()))return;
if(claims.containsKey(p.getUniqueId())){e.setCancelled(true);p.sendMessage("§cЛимит: 1 приват.");return;}
Claim c=new Claim(p.getUniqueId(),p.getName(),b.getLocation());
if(overlap(c)){e.setCancelled(true);p.sendMessage("§cПриват пересекается с чужим.");return;}
claims.put(c.owner,c);
Bukkit.getScheduler().runTask(this,()->{
if(b.getType()!=Material.TARGET||!b.getWorld().getUID().equals(c.world))claims.remove(c.owner,c);
save();
});
p.sendMessage("§aПриват создан: 16×16. ПКМ по мишени — меню.");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void breakBlock(BlockBreakEvent e){
Claim c=core(e.getBlock());
if(c!=null){
e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);
if(!owner(e.getPlayer(),c)){e.getPlayer().sendMessage("§cНельзя ломать чужую мишень.");return;}
remove(c);e.getPlayer().sendMessage("§aПриват удалён без возврата мишени.");return;
}
c=at(e.getBlock().getLocation());
if(c!=null&&!canBuild(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interact(PlayerInteractEvent e){
Block b=e.getClickedBlock();if(b==null)return;
Claim c=core(b);
if(c!=null&&e.getAction()==Action.RIGHT_CLICK_BLOCK){
e.setCancelled(true);
if(e.getHand()==EquipmentSlot.HAND)menu(e.getPlayer(),c,false);
return;
}
c=at(b.getLocation());if(c==null)return;
if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&!canUse(e.getPlayer(),c))e.setCancelled(true);
if(e.getAction()==Action.PHYSICAL&&!canUse(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void open(InventoryOpenEvent e){
if(!(e.getPlayer() instanceof Player p))return;
Claim c=at(e.getInventory().getLocation());
if(c!=null&&!canUse(p,c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interactEntity(PlayerInteractEntityEvent e){
Claim c=at(e.getRightClicked().getLocation());
if(c!=null&&!canUse(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void entityDamage(EntityDamageByEntityEvent e){
Claim c=at(e.getEntity().getLocation());if(c==null)return;
Player p=null;
if(e.getDamager() instanceof Player a)p=a;
if(e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player a)p=a;
if(p==null)return;
if(e.getEntity() instanceof Player){if(!c.pvp)e.setCancelled(true);}
else if(!canBuild(p,c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void hangingPlace(HangingPlaceEvent e){
Claim c=at(e.getEntity().getLocation());
if(c!=null&&(e.getPlayer()==null||!canBuild(e.getPlayer(),c)))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void hangingBreak(HangingBreakByEntityEvent e){
Claim c=at(e.getEntity().getLocation());
if(c!=null&&e.getRemover() instanceof Player p&&!canBuild(p,c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void multiblock(BlockMultiPlaceEvent e){
for(var s:e.getReplacedBlockStates()){
Claim c=at(s.getLocation());
if(c!=null&&!canBuild(e.getPlayer(),c)){e.setCancelled(true);return;}
}
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void explode(EntityExplodeEvent e){exploded(e.blockList());}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void blockExplode(BlockExplodeEvent e){exploded(e.blockList());}
private void exploded(List<Block> blocks){
List<Claim> destroyed=new ArrayList<>();
blocks.removeIf(b->{Claim c=core(b);if(c==null)return false;destroyed.add(c);return true;});
if(destroyed.isEmpty())return;
Bukkit.getScheduler().runTask(this,()->{
for(Claim c:destroyed)remove(c);
});
}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void piston(BlockPistonExtendEvent e){pistonMoved(e.getBlocks());}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
public void pistonRetract(BlockPistonRetractEvent e){pistonMoved(e.getBlocks());}
private void pistonMoved(List<Block> blocks){
List<Claim> moved=new ArrayList<>();
for(Block b:blocks){Claim c=core(b);if(c!=null)moved.add(c);}
if(moved.isEmpty())return;
Bukkit.getScheduler().runTask(this,()->{
for(Claim c:moved){
World w=Bukkit.getWorld(c.world);if(w!=null&&w.getBlockAt(c.x,c.y,c.z).getType()!=Material.TARGET){
claims.remove(c.owner,c);save();
}
}
});
}
private ItemStack item(Material mat,String name,String... lore){
ItemStack i=new ItemStack(mat);ItemMeta m=i.getItemMeta();
m.setDisplayName(name);m.setLore(Arrays.asList(lore));i.setItemMeta(m);return i;
}
private void menu(Player p,Claim c,boolean confirm){
Menu holder=new Menu(c.owner,confirm);
Inventory inv=Bukkit.createInventory(holder,27,confirm?"§cУдалить приват?":"§5Приват | "+c.name);
holder.inv=inv;
if(confirm){
inv.setItem(11,item(Material.LIME_CONCRETE,"§aПодтвердить удаление"));
inv.setItem(15,item(Material.RED_CONCRETE,"§cНазад"));
}else{
inv.setItem(10,item(Material.MAP,"§dТерритория","§7Уровень: "+c.level+"/4","§7Размер: "+SIZE[c.level]+"×"+SIZE[c.level]));
if(c.level<4)inv.setItem(12,item(Material.EMERALD,"§aУлучшить","§7Следующая: "+SIZE[c.level+1]+"×"+SIZE[c.level+1],"§7Цена: "+AMOUNT[c.level]+" × "+COST[c.level]));
inv.setItem(14,item(c.teamUse?Material.LIME_DYE:Material.GRAY_DYE,"§bДоступ союзников: "+(c.teamUse?"§aДа":"§cНет")));
inv.setItem(16,item(c.teamBuild?Material.IRON_PICKAXE:Material.BARRIER,"§bСтроительство союзников: "+(c.teamBuild?"§aДа":"§cНет")));
inv.setItem(18,item(Material.BARRIER,"§cУдалить приват"));
inv.setItem(22,item(Material.COMPASS,"§eГраницы"));
inv.setItem(24,item(Material.DIAMOND_SWORD,"§bPvP: "+(c.pvp?"§aДа":"§cНет")));
}
p.openInventory(inv);
}
@EventHandler public void click(InventoryClickEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;
e.setCancelled(true);
if(!(e.getWhoClicked() instanceof Player p))return;
int slot=e.getRawSlot();if(slot<0||slot>=27)return;
Bukkit.getScheduler().runTask(this,()->{
if(!p.isOnline()||p.getOpenInventory().getTopInventory().getHolder()!=m)return;
Claim c=claims.get(m.owner);if(c==null){p.closeInventory();return;}
if(m.confirm){
if(slot==11&&p.getUniqueId().equals(c.owner)){remove(c);p.closeInventory();p.sendMessage("§aПриват удалён.");}
else if(slot==15)menu(p,c,false);
return;
}
if(slot==22){border(p,c);return;}
if(!p.getUniqueId().equals(c.owner)){p.sendMessage("§cНастройки доступны владельцу.");return;}
switch(slot){
case 12 -> upgrade(p,c);
case 14 -> {c.teamUse=!c.teamUse;save();menu(p,c,false);}
case 16 -> {c.teamBuild=!c.teamBuild;save();menu(p,c,false);}
case 18 -> menu(p,c,true);
case 24 -> {c.pvp=!c.pvp;save();menu(p,c,false);}
default -> {}
}
});
}
@EventHandler public void drag(InventoryDragEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu))return;
for(int slot:e.getRawSlots())if(slot<e.getView().getTopInventory().getSize()){e.setCancelled(true);return;}
}
private void upgrade(Player p,Claim c){
if(c.level>=4)return;
Material type=COST[c.level];int amount=AMOUNT[c.level],count=0;
for(ItemStack stack:p.getInventory().getStorageContents())if(stack!=null&&stack.getType()==type)count+=stack.getAmount();
if(count<amount){p.sendMessage("§cНедостаточно ресурсов: "+amount+" × "+type);return;}
c.level++;
if(overlap(c)){c.level--;p.sendMessage("§cРасширение пересекает чужой приват.");return;}
ItemStack[] stacks=p.getInventory().getStorageContents();
for(int n=0;n<stacks.length&&amount>0;n++){
ItemStack stack=stacks[n];if(stack==null||stack.getType()!=type)continue;
int take=Math.min(stack.getAmount(),amount);amount-=take;
stack.setAmount(stack.getAmount()-take);if(stack.getAmount()<=0)stacks[n]=null;
}
p.getInventory().setStorageContents(stacks);save();
p.sendMessage("§aПриват улучшен до "+SIZE[c.level]+"×"+SIZE[c.level]);
menu(p,c,false);
}
private void remove(Claim c){
if(!claims.remove(c.owner,c))return;
World w=Bukkit.getWorld(c.world);
if(w!=null){Block b=w.getBlockAt(c.x,c.y,c.z);if(b.getType()==Material.TARGET)b.setType(Material.AIR,false);}
save();
}
private void border(Player p,Claim c){
World w=Bukkit.getWorld(c.world);
if(w==null||!p.getWorld().equals(w)){p.sendMessage("§cНужно быть в мире привата.");return;}
double y=p.getLocation().getY()+1.5;
for(int i=0;i<=48;i++){
double t=i/48.0,x=c.minX()+.5+t*(SIZE[c.level]-1),z=c.minZ()+.5+t*(SIZE[c.level]-1);
p.spawnParticle(Particle.END_ROD,x,y,c.minZ()+.5,1,0,0,0,0);
p.spawnParticle(Particle.END_ROD,x,y,c.maxZ()+.5,1,0,0,0,0);
p.spawnParticle(Particle.END_ROD,c.minX()+.5,y,z,1,0,0,0,0);
p.spawnParticle(Particle.END_ROD,c.maxX()+.5,y,z,1,0,0,0,0);
}
}
}