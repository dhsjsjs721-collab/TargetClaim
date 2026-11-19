package dev.targetclaim;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.potion.PotionEffect;
import java.io.*;
import java.util.*;

public final class TargetClaimPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private static final int[] SIZE = {16,40,80,120,160};
private static final int[] HEIGHT = {32,48,80,120,160};
private static final Material[] COST = {Material.AMETHYST_SHARD,Material.AMETHYST_SHARD,Material.NETHERITE_SCRAP,Material.DRAGON_EGG};
private static final int[] AMOUNT = {23,64,3,1};
private static final ChatColor[] COLORS = {ChatColor.AQUA,ChatColor.BLUE,ChatColor.GOLD,ChatColor.GREEN,ChatColor.LIGHT_PURPLE,ChatColor.RED,ChatColor.YELLOW,ChatColor.DARK_AQUA,ChatColor.DARK_GREEN,ChatColor.DARK_PURPLE,ChatColor.DARK_RED,ChatColor.WHITE,ChatColor.GRAY};
private static final String PREFIX = "§5§lTargetClaim §8» §r";
private final Map<UUID,Claim> claims = new HashMap<>();
private final Map<UUID,Map<Long,Set<Claim>>> spatialIndex = new HashMap<>();
private final Map<UUID,Group> groups = new LinkedHashMap<>();
private final Map<UUID,UUID> membership = new HashMap<>();
private final Map<UUID,Invite> invites = new HashMap<>();
private final Map<UUID,Viewer> viewers = new HashMap<>();
private final Map<UUID,UUID> openedClaim = new HashMap<>();
private final Map<UUID,org.bukkit.scheduler.BukkitTask> pendingDeletion = new HashMap<>();
private final Random random = new Random();
private NamespacedKey targetKey,displayKey;
private File dataFile;

private static final class Claim {
UUID owner,world;
String ownerName;
int x,y,z,level;
boolean teamBuild=false;
Claim(UUID id,String name,Location loc){owner=id;ownerName=name;world=loc.getWorld().getUID();x=loc.getBlockX();y=loc.getBlockY();z=loc.getBlockZ();}
int minX(){return x-SIZE[level]/2;}
int maxX(){return minX()+SIZE[level]-1;}
int minZ(){return z-SIZE[level]/2;}
int maxZ(){return minZ()+SIZE[level]-1;}
int minY(){return y-HEIGHT[level]/2;}
int maxY(){return minY()+HEIGHT[level]-1;}
boolean contains(Location loc){return loc!=null&&loc.getWorld()!=null&&loc.getWorld().getUID().equals(world)&&loc.getBlockX()>=minX()&&loc.getBlockX()<=maxX()&&loc.getBlockY()>=minY()&&loc.getBlockY()<=maxY()&&loc.getBlockZ()>=minZ()&&loc.getBlockZ()<=maxZ();}
boolean isCore(Block b){return b.getWorld().getUID().equals(world)&&b.getX()==x&&b.getY()==y&&b.getZ()==z;}
}
private static final class Group {
UUID id,leader;
String name;
ChatColor color;
final LinkedHashSet<UUID> members=new LinkedHashSet<>();
final Set<UUID> pvpOn=new HashSet<>();
Group(UUID id,UUID leader,String name,ChatColor color){this.id=id;this.leader=leader;this.name=name;this.color=color;members.add(leader);}
}
private record Invite(UUID group,long expires) {}
private static final class Viewer {
boolean borders=true;
}

@Override public void onEnable(){
targetKey=new NamespacedKey(this,"claim_target");
displayKey=new NamespacedKey(this,"display_owner");
dataFile=new File(getDataFolder(),"data.yml");getDataFolder().mkdirs();
loadAll();
for(Claim claim:claims.values())indexClaim(claim);
if(!dataFile.isFile()&&new File(getDataFolder(),"claims.yml").isFile()){saveAll();getLogger().info("Migrated legacy claims.yml to data.yml");}
ShapedRecipe recipe=new ShapedRecipe(new NamespacedKey(this,"target_recipe"),specialTarget());
recipe.shape("PPP","AAA","AAA");
recipe.setIngredient('P',Material.PAPER);
recipe.setIngredient('A',Material.AMETHYST_SHARD);
Bukkit.addRecipe(recipe);
Objects.requireNonNull(getCommand("p")).setExecutor(this);
Objects.requireNonNull(getCommand("team")).setExecutor(this);
Objects.requireNonNull(getCommand("p")).setTabCompleter(this);
Objects.requireNonNull(getCommand("team")).setTabCompleter(this);
Bukkit.getPluginManager().registerEvents(this,this);
for(Group g:groups.values())updateScoreboard(g);
for(World w:Bukkit.getWorlds())for(Chunk ch:w.getLoadedChunks())cleanupDisplays(ch);
for(Claim c:claims.values())ensureDisplay(c);
Bukkit.getScheduler().runTaskTimer(this,this::visualTick,15L,15L);
getLogger().info("TargetClaim 1.3: "+claims.size()+" claims, "+groups.size()+" teams");
}
@Override public void onDisable(){saveAll();}

private ItemStack specialTarget(){
ItemStack item=new ItemStack(Material.TARGET);
ItemMeta meta=item.getItemMeta();meta.setDisplayName("§d§l✦ Ядро привата ✦");
meta.setLore(List.of("§7Особая мишень территории","§7Лимит: §f1 приват на игрока","§7ПКМ: §fменю настроек","§cПри удалении возвращается только бумага"));
meta.getPersistentDataContainer().set(targetKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);return item;
}
private boolean isSpecial(ItemStack item){return item!=null&&item.getType()==Material.TARGET&&item.hasItemMeta()&&Byte.valueOf((byte)1).equals(item.getItemMeta().getPersistentDataContainer().get(targetKey,PersistentDataType.BYTE));}

private void loadAll(){
File sourceFile=dataFile.isFile()?dataFile:new File(getDataFolder(),"claims.yml");
YamlConfiguration y=YamlConfiguration.loadConfiguration(sourceFile);
ConfigurationSection claimsSection=y.getConfigurationSection("claims");
if(claimsSection!=null)for(String id:claimsSection.getKeys(false))try{
UUID owner=UUID.fromString(id);
String root="claims."+id+".";
World w=Bukkit.getWorld(UUID.fromString(Objects.requireNonNull(y.getString(root+"world"))));
if(w==null){getLogger().warning("Skipping claim with unloaded world: "+id);continue;}
Claim c=new Claim(owner,y.getString(root+"name","unknown"),new Location(w,y.getInt(root+"x"),y.getInt(root+"y"),y.getInt(root+"z")));
c.level=Math.max(0,Math.min(4,y.getInt(root+"level",0)));
c.teamBuild=y.getBoolean(root+"teamBuild",false);
claims.put(owner,c);
}catch(Exception ex){getLogger().warning("Invalid claim "+id+": "+ex.getMessage());}
ConfigurationSection teamsSection=y.getConfigurationSection("teams");
if(teamsSection!=null)for(String id:teamsSection.getKeys(false))try{
UUID gid=UUID.fromString(id);String root="teams."+id+".";
UUID leader=UUID.fromString(Objects.requireNonNull(y.getString(root+"leader")));
ChatColor color=ChatColor.valueOf(y.getString(root+"color","AQUA"));
Group g=new Group(gid,leader,y.getString(root+"name","Team"),color);
g.members.clear();for(String s:y.getStringList(root+"members"))g.members.add(UUID.fromString(s));
if(!g.members.contains(leader))g.members.add(leader);
while(g.members.size()>3){UUID last=g.members.iterator().next();if(last.equals(g.leader))break;g.members.remove(last);}
for(String s:y.getStringList(root+"pvpOn"))try{UUID m=UUID.fromString(s);if(g.members.contains(m))g.pvpOn.add(m);}catch(Exception ignored){}
for(UUID m:g.members)membership.put(m,gid);
groups.put(gid,g);
}catch(Exception ex){getLogger().warning("Invalid team "+id+": "+ex.getMessage());}
ConfigurationSection viewSection=y.getConfigurationSection("viewers");
if(viewSection!=null)for(String id:viewSection.getKeys(false))try{
UUID uid=UUID.fromString(id);String root="viewers."+id+".";Viewer v=new Viewer();
v.borders=y.getBoolean(root+"borders",true);viewers.put(uid,v);
}catch(Exception ignored){}
}
private void saveAll(){
YamlConfiguration y=new YamlConfiguration();
for(Claim c:claims.values()){
String r="claims."+c.owner+".";y.set(r+"name",c.ownerName);y.set(r+"world",c.world.toString());y.set(r+"x",c.x);y.set(r+"y",c.y);y.set(r+"z",c.z);y.set(r+"level",c.level);y.set(r+"teamBuild",c.teamBuild);
}
for(Group g:groups.values()){
String r="teams."+g.id+".";y.set(r+"name",g.name);y.set(r+"leader",g.leader.toString());y.set(r+"color",g.color.name());y.set(r+"members",g.members.stream().map(UUID::toString).toList());y.set(r+"pvpOn",g.pvpOn.stream().map(UUID::toString).toList());
}
for(Map.Entry<UUID,Viewer> entry:viewers.entrySet()){
String r="viewers."+entry.getKey()+".";Viewer v=entry.getValue();y.set(r+"borders",v.borders);
}
try{y.save(dataFile);}catch(IOException ex){getLogger().severe("Cannot save data.yml: "+ex.getMessage());}
}
private static long chunkKey(int cx,int cz){return ((long)cx<<32) ^ (cz&0xffffffffL);}
private Set<Claim> chunkClaims(UUID world,int cx,int cz){
Map<Long,Set<Claim>> index=spatialIndex.get(world);
return index==null?Collections.emptySet():index.getOrDefault(chunkKey(cx,cz),Collections.emptySet());
}
private int minX(Claim c,int level){return c.x-SIZE[level]/2;}
private int minZ(Claim c,int level){return c.z-SIZE[level]/2;}
private int minY(Claim c,int level){return c.y-HEIGHT[level]/2;}
private int maxX(Claim c,int level){return minX(c,level)+SIZE[level]-1;}
private int maxZ(Claim c,int level){return minZ(c,level)+SIZE[level]-1;}
private int maxY(Claim c,int level){return minY(c,level)+HEIGHT[level]-1;}
private void indexClaim(Claim c){
Map<Long,Set<Claim>> byChunk=spatialIndex.computeIfAbsent(c.world,id->new HashMap<>());
for(int x=c.minX()>>4;x<=c.maxX()>>4;x++)for(int z=c.minZ()>>4;z<=c.maxZ()>>4;z++)
byChunk.computeIfAbsent(chunkKey(x,z),id->new HashSet<>()).add(c);
}
private void unindexClaim(Claim c){
Map<Long,Set<Claim>> byChunk=spatialIndex.get(c.world);
if(byChunk==null)return;
for(int x=c.minX()>>4;x<=c.maxX()>>4;x++)for(int z=c.minZ()>>4;z<=c.maxZ()>>4;z++){
long key=chunkKey(x,z);Set<Claim> group=byChunk.get(key);
if(group!=null){group.remove(c);if(group.isEmpty())byChunk.remove(key);}
}
if(byChunk.isEmpty())spatialIndex.remove(c.world);
}
private Set<Claim> regionClaims(UUID world,int fromX,int toX,int fromZ,int toZ){
Set<Claim> found=new LinkedHashSet<>();
for(int x=fromX>>4;x<=toX>>4;x++)for(int z=fromZ>>4;z<=toZ>>4;z++)
found.addAll(chunkClaims(world,x,z));
return found;
}
private Claim claimAt(Location loc){
if(loc==null||loc.getWorld()==null)return null;
for(Claim c:chunkClaims(loc.getWorld().getUID(),loc.getBlockX()>>4,loc.getBlockZ()>>4))
if(c.contains(loc))return c;
return null;
}
private Claim claimCore(Block block){
Claim c=claimAt(block.getLocation());
return c!=null&&c.isCore(block)?c:null;
}
private boolean wouldOverlap(Claim c,int level){
int left=minX(c,level),right=maxX(c,level),front=minZ(c,level),back=maxZ(c,level);
int bottom=minY(c,level),top=maxY(c,level);
for(Claim other:regionClaims(c.world,left,right,front,back)){
if(other.owner.equals(c.owner))continue;
if(left<=other.maxX()&&right>=other.minX()&&bottom<=other.maxY()&&top>=other.minY()&&front<=other.maxZ()&&back>=other.minZ())return true;
}
return false;
}
private boolean validLevel(Claim c,int level){
World world=Bukkit.getWorld(c.world);
return world!=null&&minY(c,level)>=world.getMinHeight()&&maxY(c,level)<world.getMaxHeight()&&!wouldOverlap(c,level);
}
private boolean overlap(Claim c){return wouldOverlap(c,c.level);}
private Group groupOf(UUID player){UUID id=membership.get(player);return id==null?null:groups.get(id);}
private boolean allies(UUID a,UUID b){if(a.equals(b))return true;UUID x=membership.get(a);return x!=null&&x.equals(membership.get(b));}
private boolean owner(Player player,Claim c){return player.getUniqueId().equals(c.owner)||player.hasPermission("targetclaim.admin");}
private boolean canUse(Player player,Claim c){return canBuild(player,c);}
private boolean canBuild(Player player,Claim c){return owner(player,c)||(c.teamBuild&&allies(player.getUniqueId(),c.owner));}
private boolean friendlyDamageBlocked(Player attacker,Player victim){
if(attacker==null||victim==null||attacker.equals(victim)||!allies(attacker.getUniqueId(),victim.getUniqueId()))return false;
Group g=groupOf(attacker.getUniqueId());return g!=null&&!(g.pvpOn.contains(attacker.getUniqueId())&&g.pvpOn.contains(victim.getUniqueId()));
}
private void tell(CommandSender sender,String message){sender.sendMessage(PREFIX+message);}

@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(command.getName().equalsIgnoreCase("team"))return teamCommand(sender,args);
if(args.length>0&&args[0].equalsIgnoreCase("give")){
if(!sender.hasPermission("targetclaim.admin")){tell(sender,"§cНет прав.");return true;}
if(args.length<2){tell(sender,"§e/p give <ник>");return true;}
Player recipient=Bukkit.getPlayerExact(args[1]);if(recipient==null){tell(sender,"§cИгрок не найден.");return true;}
for(ItemStack rest:recipient.getInventory().addItem(specialTarget()).values())recipient.getWorld().dropItemNaturally(recipient.getLocation(),rest);
tell(sender,"§aМишень выдана игроку "+recipient.getName());return true;
}
if(!(sender instanceof Player p)){tell(sender,"§cЭта команда только для игроков.");return true;}
Claim c=claims.get(p.getUniqueId());
if(c==null){tell(p,"§7Нет привата. Рецепт: §f3 бумаги сверху и 6 аметистов снизу.");return true;}
if(args.length==0){showClaimDialog(p,c);return true;}
switch(args[0].toLowerCase(Locale.ROOT)){
case "upgrade" -> upgrade(p,c);
case "remove" -> showRemoveDialog(p,c);
case "cancel" -> cancelDeletion(p,c);
case "border","borders" -> {
Viewer v=viewer(p);
if(args.length==1)v.borders=!v.borders;
else if(args[1].equalsIgnoreCase("on"))v.borders=true;
else if(args[1].equalsIgnoreCase("off"))v.borders=false;
else{tell(p,"§eИспользуй: /p border on|off");return true;}
saveAll();tell(p,"§eКрасные границы: "+(v.borders?"§aВКЛ":"§cВЫКЛ"));
}
case "info" -> {tell(p,"§dУровень "+c.level+"/4, объём: "+SIZE[c.level]+"×"+HEIGHT[c.level]+"×"+SIZE[c.level]+", 1 приват на игрока.");}
default -> showClaimDialog(p,c);
}
return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return starts(cmd.getName().equalsIgnoreCase("p")?List.of("upgrade","border","remove","cancel","info","give"):List.of("create","invite","accept","deny","leave","kick","disband","pvp","info","menu"),args[0]);
if(args.length==2&&cmd.getName().equalsIgnoreCase("team")&&(args[0].equalsIgnoreCase("invite")||args[0].equalsIgnoreCase("kick")))return starts(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(),args[1]);
if(args.length==2&&args[0].equalsIgnoreCase("pvp"))return starts(List.of("on","off"),args[1]);
if(args.length==2&&args[0].equalsIgnoreCase("border"))return starts(List.of("on","off"),args[1]);
return List.of();
}
private List<String> starts(List<String> words,String query){String q=query.toLowerCase(Locale.ROOT);return words.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(q)).toList();}
private boolean teamCommand(CommandSender sender,String[] args){
if(!(sender instanceof Player p)){tell(sender,"§cТолько для игроков.");return true;}
Group g=groupOf(p.getUniqueId());
if(args.length==0||args[0].equalsIgnoreCase("menu")){showTeamDialog(p);return true;}
switch(args[0].toLowerCase(Locale.ROOT)){
case "create" -> {
if(g!=null){tell(p,"§cТы уже в команде.");break;}
if(args.length<2){tell(p,"§eИспользуй: /team create <название>");break;}
String name=args[1];
if(!name.matches("[\\p{L}\\p{N}_-]{3,16}")){tell(p,"§cНазвание: 3–16 букв/цифр, _ или -.");break;}
if(groups.values().stream().anyMatch(t->t.name.equalsIgnoreCase(name))){tell(p,"§cТакое название уже занято.");break;}
List<ChatColor> available=new ArrayList<>(Arrays.asList(COLORS));for(Group t:groups.values())available.remove(t.color);
if(available.isEmpty())available.addAll(Arrays.asList(COLORS));
Group newGroup=new Group(UUID.randomUUID(),p.getUniqueId(),name,available.get(random.nextInt(available.size())));
groups.put(newGroup.id,newGroup);membership.put(p.getUniqueId(),newGroup.id);
updateScoreboard(newGroup);saveAll();tell(p,"§aКоманда §r"+newGroup.color+name+" §aсоздана. Лимит 3 игрока. Приглашай: /team invite <ник>.");
}
case "invite" -> {
if(g==null||!g.leader.equals(p.getUniqueId())){tell(p,"§cПриглашать может только лидер.");break;}
if(g.members.size()>=3){tell(p,"§cВ команде уже 3 игрока.");break;}
if(args.length<2){tell(p,"§e/team invite <ник>");break;}
Player target=Bukkit.getPlayerExact(args[1]);
if(target==null||target.equals(p)){tell(p,"§cИгрок не найден.");break;}
if(groupOf(target.getUniqueId())!=null){tell(p,"§cИгрок уже состоит в команде.");break;}
invites.put(target.getUniqueId(),new Invite(g.id,System.currentTimeMillis()+120000));
tell(target,"§dПриглашение в команду §r"+g.color+g.name+"§d. Принять: §f/team accept §7(2 минуты)");
tell(p,"§aПриглашение отправлено.");
}
case "accept" -> {
if(g!=null){tell(p,"§cСначала выйди из текущей команды.");break;}
Invite invite=invites.remove(p.getUniqueId());
if(invite==null||invite.expires<System.currentTimeMillis()){tell(p,"§cПриглашение отсутствует или истекло.");break;}
Group target=groups.get(invite.group);
if(target==null||target.members.size()>=3){tell(p,"§cКоманда удалена или заполнена.");break;}
target.members.add(p.getUniqueId());membership.put(p.getUniqueId(),target.id);
updateScoreboard(target);saveAll();broadcast(target,"§aИгрок "+p.getName()+" присоединился.");
}
case "deny" -> {invites.remove(p.getUniqueId());tell(p,"§7Приглашение отклонено.");}
case "leave" -> {
if(g==null){tell(p,"§cТы не в команде.");break;}
leave(p.getUniqueId(),g);tell(p,"§aТы вышел из команды.");
}
case "kick" -> {
if(g==null||!g.leader.equals(p.getUniqueId())){tell(p,"§cИсключать может только лидер.");break;}
if(args.length<2){tell(p,"§e/team kick <ник>");break;}
UUID target=null;for(UUID uid:g.members){OfflinePlayer off=Bukkit.getOfflinePlayer(uid);if(off.getName()!=null&&off.getName().equalsIgnoreCase(args[1]))target=uid;}
if(target==null||target.equals(p.getUniqueId())){tell(p,"§cИгрок не найден или это лидер.");break;}
leave(target,g);tell(p,"§aИгрок исключён.");Player other=Bukkit.getPlayer(target);if(other!=null)tell(other,"§cТы исключён из команды.");
}
case "disband" -> {
if(g==null||!g.leader.equals(p.getUniqueId())){tell(p,"§cУдалить команду может только лидер.");break;}
disband(g);tell(p,"§aКоманда расформирована.");
}
case "pvp" -> {
if(g==null){tell(p,"§cТы не в команде.");break;}
boolean newValue;
if(args.length<2)newValue=!g.pvpOn.contains(p.getUniqueId());
else if(args[1].equalsIgnoreCase("on"))newValue=true;
else if(args[1].equalsIgnoreCase("off"))newValue=false;
else{tell(p,"§e/team pvp on|off");break;}
if(newValue)g.pvpOn.add(p.getUniqueId());else g.pvpOn.remove(p.getUniqueId());
saveAll();tell(p,"§eТвой PvP с союзниками: "+(newValue?"§aВКЛ":"§cВЫКЛ")+"§7. Урон проходит только если оба включили.");
}
case "info" -> {
if(g==null){tell(p,"§cТы не в команде.");break;}
tell(p,"§eКоманда: "+g.color+g.name+"§7, игроков "+g.members.size()+"/3");
for(UUID id:g.members){OfflinePlayer off=Bukkit.getOfflinePlayer(id);tell(p,"§7- §f"+(off.getName()==null?id:off.getName())+(id.equals(g.leader)?" §6(лидер)":"")+(g.pvpOn.contains(id)?" §aPvP ON":" §cPvP OFF"));}
}
default -> tell(p,"§e/team create|invite|accept|leave|kick|disband|pvp|info|menu");
}
return true;
}
private void broadcast(Group group,String msg){for(UUID uid:group.members){Player p=Bukkit.getPlayer(uid);if(p!=null)tell(p,msg);}}
private void leave(UUID uid,Group g){
g.members.remove(uid);g.pvpOn.remove(uid);membership.remove(uid);removeFromScoreboard(uid,g);
if(g.members.isEmpty()){destroyGroup(g);return;}
if(uid.equals(g.leader))g.leader=g.members.iterator().next();
saveAll();broadcast(g,"§eСостав команды обновлён.");
}
private void disband(Group g){for(UUID uid:new ArrayList<>(g.members)){membership.remove(uid);removeFromScoreboard(uid,g);Player player=Bukkit.getPlayer(uid);if(player!=null)tell(player,"§cКоманда расформирована.");}destroyGroup(g);}
private void destroyGroup(Group g){groups.remove(g.id);Scoreboard s=Bukkit.getScoreboardManager().getMainScoreboard();Team t=s.getTeam(scoreName(g));if(t!=null)t.unregister();saveAll();}
private String scoreName(Group g){return "tc_"+g.id.toString().replace("-","").substring(0,12);}
private void removeFromScoreboard(UUID id,Group g){Team team=Bukkit.getScoreboardManager().getMainScoreboard().getTeam(scoreName(g));OfflinePlayer off=Bukkit.getOfflinePlayer(id);if(team!=null&&off.getName()!=null)team.removeEntry(off.getName());}
private void updateScoreboard(Group g){
Scoreboard s=Bukkit.getScoreboardManager().getMainScoreboard();Team team=s.getTeam(scoreName(g));if(team==null)team=s.registerNewTeam(scoreName(g));
team.setColor(g.color);team.setAllowFriendlyFire(true);team.setPrefix(g.color.toString());
for(UUID id:g.members){OfflinePlayer off=Bukkit.getOfflinePlayer(id);if(off.getName()!=null)team.addEntry(off.getName());}
}
@EventHandler public void join(PlayerJoinEvent event){Group g=groupOf(event.getPlayer().getUniqueId());if(g!=null)updateScoreboard(g);}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void place(BlockPlaceEvent e){
Block b=e.getBlockPlaced();Player p=e.getPlayer();Claim occupied=claimAt(b.getLocation());
if(occupied!=null&&!canBuild(p,occupied)){e.setCancelled(true);tell(p,"§cСтроительство запрещено.");return;}
if(!isSpecial(e.getItemInHand()))return;
if(claims.containsKey(p.getUniqueId())){e.setCancelled(true);tell(p,"§cУ тебя уже один приват.");return;}
Claim c=new Claim(p.getUniqueId(),p.getName(),b.getLocation());
if(c.minY()<b.getWorld().getMinHeight()||c.maxY()>=b.getWorld().getMaxHeight()){e.setCancelled(true);tell(p,"§cУстанови мишень выше/ниже: область должна помещаться в высоту мира.");return;}
if(overlap(c)){e.setCancelled(true);tell(p,"§cПриват пересекается с другой территорией.");return;}
claims.put(c.owner,c);indexClaim(c);
Bukkit.getScheduler().runTask(this,()->{if(b.getType()!=Material.TARGET){claims.remove(c.owner);unindexClaim(c);return;}ensureDisplay(c);saveAll();});
tell(p,"§aПриват создан: §f16×32×16. §7ПКМ — меню.");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void breaking(BlockBreakEvent e){
Claim c=claimCore(e.getBlock());
if(c!=null){e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);
if(!e.getPlayer().getUniqueId().equals(c.owner)){tell(e.getPlayer(),"§cЯдро привата защищено.");return;}
showRemoveDialog(e.getPlayer(),c);return;}
c=claimAt(e.getBlock().getLocation());if(c!=null&&!canBuild(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interact(PlayerInteractEvent e){
Block b=e.getClickedBlock();if(b==null)return;
Claim core=claimCore(b);
if(core!=null&&e.getAction()==Action.RIGHT_CLICK_BLOCK){e.setCancelled(true);
if(e.getHand()!=EquipmentSlot.HAND)return;
openedClaim.put(e.getPlayer().getUniqueId(),core.owner);
if(e.getPlayer().isSneaking()&&e.getPlayer().getUniqueId().equals(core.owner)&&e.getPlayer().getInventory().getItemInMainHand().getType()==COST[Math.min(core.level,3)]&&core.level<4){upgrade(e.getPlayer(),core);return;}
showClaimDialog(e.getPlayer(),core);return;
}
Claim c=claimAt(b.getLocation());if(c==null)return;
if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&!canUse(e.getPlayer(),c))e.setCancelled(true);
if(e.getAction()==Action.PHYSICAL&&!canUse(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void openInventory(InventoryOpenEvent e){if(!(e.getPlayer() instanceof Player p))return;Location loc=e.getInventory().getLocation();Claim c=claimAt(loc);if(c!=null&&!canUse(p,c))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void entityInteract(PlayerInteractEntityEvent e){Claim c=claimAt(e.getRightClicked().getLocation());if(c!=null&&!canUse(e.getPlayer(),c))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void damage(EntityDamageByEntityEvent e){
Player attacker=attacker(e.getDamager());
if(e.getEntity() instanceof Player victim){
if(attacker!=null&&friendlyDamageBlocked(attacker,victim)){e.setCancelled(true);return;}
}
Claim c=claimAt(e.getEntity().getLocation());
if(c!=null&&!(e.getEntity() instanceof Player)&&attacker!=null&&!canBuild(attacker,c))e.setCancelled(true);
}
private Player attacker(Entity entity){
if(entity instanceof Player p)return p;
if(entity instanceof Projectile p){ProjectileSource shooter=p.getShooter();return shooter instanceof Player player?player:shooter instanceof Tameable tame&&tame.getOwner() instanceof Player owner?owner:null;}
if(entity instanceof TNTPrimed t&&t.getSource() instanceof Player p)return p;
if(entity instanceof AreaEffectCloud cloud&&cloud.getSource() instanceof Player p)return p;
if(entity instanceof Tameable t&&t.getOwner() instanceof Player p)return p;
return null;
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void splash(PotionSplashEvent e){Player shooter=attacker(e.getEntity());if(shooter==null)return;for(LivingEntity entity:e.getAffectedEntities())if(entity instanceof Player victim&&friendlyDamageBlocked(shooter,victim))e.setIntensity(entity,0);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void lingering(AreaEffectCloudApplyEvent e){Player shooter=attacker(e.getEntity());if(shooter==null)return;e.getAffectedEntities().removeIf(entity->entity instanceof Player victim&&friendlyDamageBlocked(shooter,victim));}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void hangingPlace(HangingPlaceEvent e){Claim c=claimAt(e.getEntity().getLocation());if(c!=null&&(e.getPlayer()==null||!canBuild(e.getPlayer(),c)))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void hangingBreak(HangingBreakByEntityEvent e){Claim c=claimAt(e.getEntity().getLocation());if(c!=null&&e.getRemover() instanceof Player p&&!canBuild(p,c))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void multiPlace(BlockMultiPlaceEvent e){for(var state:e.getReplacedBlockStates()){Claim c=claimAt(state.getLocation());if(c!=null&&!canBuild(e.getPlayer(),c)){e.setCancelled(true);return;}}}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void blockExplosion(BlockExplodeEvent e){handleExplosion(e.blockList());}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void entityExplosion(EntityExplodeEvent e){handleExplosion(e.blockList());}
private void handleExplosion(List<Block> broken){
broken.removeIf(b->claimCore(b)!=null);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void pistonExtend(BlockPistonExtendEvent e){if(movesClaimCore(e.getBlocks()))e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void pistonRetract(BlockPistonRetractEvent e){if(movesClaimCore(e.getBlocks()))e.setCancelled(true);}
private boolean movesClaimCore(List<Block> blocks){
for(Block b:blocks)if(claimCore(b)!=null)return true;
return false;
}

private void upgrade(Player p,Claim c){
if(!p.getUniqueId().equals(c.owner)){tell(p,"§cТолько владелец может улучшать приват.");return;}
if(c.level>=4){tell(p,"§aПриват уже максимального уровня.");return;}
Material type=COST[c.level];int count=AMOUNT[c.level];ItemStack held=p.getInventory().getItemInMainHand();
if(held.getType()!=type||held.getAmount()<count){tell(p,"§eДержи в основной руке §f"+count+" × "+russian(type)+"§e одним стаком и повтори.");return;}
if(!validLevel(c,c.level+1)){
tell(p,"§cНельзя расширить: пересечение с другой территорией или выход за границы мира.");return;
}
unindexClaim(c);c.level++;indexClaim(c);
held.setAmount(held.getAmount()-count);saveAll();refreshDisplay(c);showUpgradeEffects(c);
tell(p,"§aУлучшено до уровня "+c.level+" §7(§f"+SIZE[c.level]+"×"+HEIGHT[c.level]+"×"+SIZE[c.level]+"§7)");
}
private String russian(Material material){return switch(material){case AMETHYST_SHARD->"аметистовых осколков";case NETHERITE_SCRAP->"незеритового лома";case DRAGON_EGG->"яйцо дракона";default->material.name();};}
private void removeClaim(Claim c){
if(!claims.remove(c.owner,c))return;
unindexClaim(c);
org.bukkit.scheduler.BukkitTask pending=pendingDeletion.remove(c.owner);
if(pending!=null)pending.cancel();
World w=Bukkit.getWorld(c.world);
if(w!=null){Block b=w.getBlockAt(c.x,c.y,c.z);if(b.getType()==Material.TARGET){
b.setType(Material.AIR,false);
w.dropItemNaturally(new Location(w,c.x+.5,c.y+.5,c.z+.5),new ItemStack(Material.PAPER,3));
}
for(Entity entity:w.getNearbyEntities(new Location(w,c.x+.5,c.y+1.7,c.z+.5),2,3,2))if(entity instanceof TextDisplay&&c.owner.toString().equals(entity.getPersistentDataContainer().get(displayKey,PersistentDataType.STRING)))entity.remove();}
saveAll();
}
private void startDeletion(Player p,Claim c){
if(!p.getUniqueId().equals(c.owner))return;
if(pendingDeletion.containsKey(c.owner)){showPendingDeletionDialog(p);return;}
org.bukkit.scheduler.BukkitTask task=Bukkit.getScheduler().runTaskLater(this,()->{
pendingDeletion.remove(c.owner);
if(claims.get(c.owner)!=c)return;
removeClaim(c);
Player online=Bukkit.getPlayer(c.owner);
if(online!=null)tell(online,"§aПриват удалён. Возвращено §f3 бумаги§a; улучшения не возвращаются.");
},200L);
pendingDeletion.put(c.owner,task);
tell(p,"§eУдаление запланировано через §c10 секунд§e. Отменить: §f/p cancel");
showPendingDeletionDialog(p);
}
private void cancelDeletion(Player p,Claim c){
if(!p.getUniqueId().equals(c.owner)){tell(p,"§cОтменить удаление может только владелец.");return;}
org.bukkit.scheduler.BukkitTask task=pendingDeletion.remove(c.owner);
if(task==null){tell(p,"§7Удаление не запланировано.");return;}
task.cancel();
tell(p,"§aУдаление отменено.");
showClaimDialog(p,c);
}

private void ensureDisplay(Claim c){
World w=Bukkit.getWorld(c.world);if(w==null||!w.isChunkLoaded(c.x>>4,c.z>>4))return;
if(w.getBlockAt(c.x,c.y,c.z).getType()!=Material.TARGET)return;
Location position=new Location(w,c.x+.5,c.y+1.55,c.z+.5);
for(Entity entity:w.getNearbyEntities(position,2,3,2))if(entity instanceof TextDisplay text&&c.owner.toString().equals(entity.getPersistentDataContainer().get(displayKey,PersistentDataType.STRING))){setDisplayText(text,c);return;}
TextDisplay entity=w.spawn(position,TextDisplay.class,text->{
text.setBillboard(Display.Billboard.CENTER);text.setShadowed(true);text.setSeeThrough(false);
text.setBackgroundColor(Color.fromARGB(145,20,13,35));text.setLineWidth(180);text.setPersistent(true);
text.getPersistentDataContainer().set(displayKey,PersistentDataType.STRING,c.owner.toString());
});
setDisplayText(entity,c);
}
private void setDisplayText(TextDisplay display,Claim c){
display.setText("§d§l✦ ПРИВАТ ✦\n§f"+c.ownerName+"\n§dУровень §f"+c.level+"§8/§f4  §7•  §f"+SIZE[c.level]+"×"+HEIGHT[c.level]+"×"+SIZE[c.level]+"\n§7ПКМ — управление");
}
private void refreshDisplay(Claim c){ensureDisplay(c);}
@EventHandler public void chunkLoad(ChunkLoadEvent e){
Bukkit.getScheduler().runTask(this,()->{
Chunk chunk=e.getChunk();
cleanupDisplays(chunk);
for(Claim c:chunkClaims(chunk.getWorld().getUID(),chunk.getX(),chunk.getZ()))
if((c.x>>4)==chunk.getX()&&(c.z>>4)==chunk.getZ())ensureDisplay(c);
});
}
private void cleanupDisplays(Chunk ch){
for(Entity e:ch.getEntities())if(e instanceof TextDisplay text){String marker=text.getPersistentDataContainer().get(displayKey,PersistentDataType.STRING);
if(marker==null)continue;
try{Claim c=claims.get(UUID.fromString(marker));if(c==null||!c.world.equals(e.getWorld().getUID())||e.getLocation().distanceSquared(new Location(e.getWorld(),c.x+.5,c.y+1.55,c.z+.5))>4)text.remove();else setDisplayText(text,c);}catch(Exception ignored){text.remove();}
}
}
private static final Particle.DustOptions BORDER_RED=new Particle.DustOptions(Color.fromRGB(255,35,48),1.9f);
private static final Particle.DustOptions PREVIEW_CYAN=new Particle.DustOptions(Color.fromRGB(35,231,255),1.55f);
private static final Particle.DustOptions MAX_GOLD=new Particle.DustOptions(Color.fromRGB(255,193,55),1.35f);
private static final Particle.DustOptions BLOCKED_ORANGE=new Particle.DustOptions(Color.fromRGB(255,117,35),1.5f);
private static final class ParticleBudget {
int remaining;
ParticleBudget(int max){remaining=max;}
}
private boolean enoughInHand(Player p,Claim c){
if(c.level>=4)return false;
ItemStack held=p.getInventory().getItemInMainHand();
return held.getType()==COST[c.level]&&held.getAmount()>=AMOUNT[c.level];
}
private Viewer viewer(Player p){return viewers.computeIfAbsent(p.getUniqueId(),id->new Viewer());}
private void visualTick(){
for(Player p:Bukkit.getOnlinePlayers()){
Viewer settings=viewer(p);
Claim own=claims.get(p.getUniqueId());
boolean preview=own!=null&&enoughInHand(p,own);
if(!settings.borders&&!preview)continue;
Location pos=p.getLocation();
ParticleBudget red=new ParticleBudget(190);
ParticleBudget cyan=new ParticleBudget(125);
ParticleBudget gold=new ParticleBudget(65);
for(Claim c:regionClaims(p.getWorld().getUID(),pos.getBlockX()-96,pos.getBlockX()+96,pos.getBlockZ()-96,pos.getBlockZ()+96)){
boolean self=c.owner.equals(p.getUniqueId());
if(!self&&!(c.teamBuild&&allies(p.getUniqueId(),c.owner)))continue;
if(Math.abs(pos.getX()-(c.x+.5))>SIZE[c.level]/2.+96||Math.abs(pos.getZ()-(c.z+.5))>SIZE[c.level]/2.+96||Math.abs(pos.getY()-(c.y+.5))>HEIGHT[c.level]/2.+120)continue;
if(settings.borders&&red.remaining>0)drawBorder(p,c,c.level,BORDER_RED,2.6,96,red,true);
if(self&&preview){
boolean valid=validLevel(c,c.level+1);
drawBorder(p,c,c.level+1,valid?PREVIEW_CYAN:BLOCKED_ORANGE,3.6,132,cyan,false);
if(c.level+1<4)drawBorder(p,c,4,MAX_GOLD,8.0,150,gold,false);
}
}
}
}
private void particleLine(Player p,Particle.DustOptions dust,double x1,double y1,double z1,double x2,double y2,double z2,double spacing,double visibleRange,ParticleBudget budget){
double dx=x2-x1,dy=y2-y1,dz=z2-z1;
int steps=Math.max(1,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)/spacing));
Location loc=p.getLocation();
double maxDistance=visibleRange*visibleRange;
for(int i=0;i<=steps&&budget.remaining>0;i++){
double f=(double)i/steps;
double x=x1+dx*f,y=y1+dy*f,z=z1+dz*f;
double ax=x-loc.getX(),ay=y-loc.getY(),az=z-loc.getZ();
if(ax*ax+ay*ay+az*az>maxDistance)continue;
p.spawnParticle(Particle.DUST,x,y,z,1,0,0,0,0,dust);
budget.remaining--;
}
}
private void drawBorder(Player p,Claim c,int level,Particle.DustOptions dust,double spacing,double visibleRange,ParticleBudget budget,boolean slice){
double x1=minX(c,level)+.5,x2=maxX(c,level)+.5;
double z1=minZ(c,level)+.5,z2=maxZ(c,level)+.5;
double y1=minY(c,level)+.5,y2=maxY(c,level)+.5;
for(double y:new double[]{y1,y2}){
particleLine(p,dust,x1,y,z1,x2,y,z1,spacing,visibleRange,budget);
particleLine(p,dust,x1,y,z2,x2,y,z2,spacing,visibleRange,budget);
particleLine(p,dust,x1,y,z1,x1,y,z2,spacing,visibleRange,budget);
particleLine(p,dust,x2,y,z1,x2,y,z2,spacing,visibleRange,budget);
}
for(double x:new double[]{x1,x2})for(double z:new double[]{z1,z2})
particleLine(p,dust,x,y1,z,x,y2,z,spacing,visibleRange,budget);
if(slice){
double y=Math.max(y1,Math.min(y2,p.getLocation().getY()));
particleLine(p,dust,x1,y,z1,x2,y,z1,spacing,visibleRange,budget);
particleLine(p,dust,x1,y,z2,x2,y,z2,spacing,visibleRange,budget);
particleLine(p,dust,x1,y,z1,x1,y,z2,spacing,visibleRange,budget);
particleLine(p,dust,x2,y,z1,x2,y,z2,spacing,visibleRange,budget);
}
}
private void showUpgradeEffects(Claim c){World w=Bukkit.getWorld(c.world);if(w==null)return;
Location at=new Location(w,c.x+.5,c.y+1.15,c.z+.5);
w.spawnParticle(c.level==4?Particle.DRAGON_BREATH:Particle.ENCHANT,at,55,1.2,1.2,1.2,0.15);
w.playSound(at,Sound.BLOCK_AMETHYST_BLOCK_CHIME,1.0f,1.3f);
}

private ActionButton action(String caption,String description,String command,NamedTextColor color){
return ActionButton.create(Component.text(caption,color),Component.text(description),190,DialogAction.customClick(Key.key("targetclaim:"+command),null));
}
private DialogBody message(String s){return DialogBody.plainMessage(Component.text(s));}
private void dialog(Player player,String heading,List<DialogBody> body,List<ActionButton> buttons){
Dialog d=Dialog.create(b->b.empty().base(DialogBase.builder(Component.text(heading,NamedTextColor.LIGHT_PURPLE)).body(body).canCloseWithEscape(true).build()).type(DialogType.multiAction(buttons).build()));
player.showDialog(d);
}
private Claim viewedClaim(Player p){UUID id=openedClaim.get(p.getUniqueId());return id==null?claims.get(p.getUniqueId()):claims.get(id);}
private void showClaimDialog(Player player,Claim c){
if(!owner(player,c)&&!allies(player.getUniqueId(),c.owner)){tell(player,"§cЧужой приват.");return;}
openedClaim.put(player.getUniqueId(),c.owner);
boolean isOwner=player.getUniqueId().equals(c.owner);
List<DialogBody> body=List.of(message("✦ Владелец: "+c.ownerName),message("Уровень "+c.level+"/4  •  "+SIZE[c.level]+" × "+HEIGHT[c.level]+" × "+SIZE[c.level]+" блоков"),message("Строительство: "+(c.teamBuild?"владелец и команда":"только владелец")+"  •  PvP территории: всегда ВКЛ"));
List<ActionButton> buttons=new ArrayList<>();
if(isOwner&&c.level<4)buttons.add(action("✧ Улучшить","Нужно "+AMOUNT[c.level]+" × "+russian(COST[c.level])+" в основной руке","claim_upgrade",NamedTextColor.GREEN));
buttons.add(action("◈ Красные границы: "+on(viewer(player).borders),"Один яркий красный контур; нажми для переключения","claim_border",NamedTextColor.RED));
if(isOwner){
buttons.add(action("⚒ Строительство: "+(c.teamBuild?"КОМАНДА":"ТОЛЬКО Я"),"Строительство и использование объектов: владелец или команда","claim_build",NamedTextColor.YELLOW));
if(pendingDeletion.containsKey(c.owner))buttons.add(action("✖ Отменить удаление","Приват пока сохранён; нажми для отмены","claim_delete_cancel",NamedTextColor.GREEN));
else buttons.add(action("✖ Удалить приват","Подтверждение, затем 10 секунд; возврат только 3 бумаги","claim_remove",NamedTextColor.RED));
}
buttons.add(action("♜ Моя команда","Приглашения, цвет и PvP участников","team_open",NamedTextColor.LIGHT_PURPLE));
dialog(player,"✦ TARGETCLAIM • УПРАВЛЕНИЕ",body,buttons);
}
private String on(boolean value){return value?"ВКЛ":"ВЫКЛ";}
private void showRemoveDialog(Player player,Claim c){
if(!player.getUniqueId().equals(c.owner)){tell(player,"§cТолько владелец может удалить приват.");return;}
if(pendingDeletion.containsKey(c.owner)){showPendingDeletionDialog(player);return;}
dialog(player,"ПОДТВЕРДИТЬ УДАЛЕНИЕ",List.of(message("Приват удалится через 10 секунд после подтверждения."),message("Отмена доступна командой /p cancel."),message("После удаления выпадут только 3 бумаги; улучшения не возвращаются.")),List.of(action("Подтвердить удаление","Запустить отсчёт 10 секунд","claim_delete_confirm",NamedTextColor.RED),action("Отмена","Оставить приват на месте","claim_open",NamedTextColor.GREEN)));
}
private void showPendingDeletionDialog(Player player){
dialog(player,"УДАЛЕНИЕ ЗАПЛАНИРОВАНО",List.of(message("Удаление произойдёт через 10 секунд после подтверждения."),message("Нажми кнопку ниже или введи /p cancel, чтобы сохранить приват.")),List.of(action("Отменить удаление","Приват останется на месте","claim_delete_cancel",NamedTextColor.GREEN),action("Закрыть","Отсчёт продолжится","claim_close",NamedTextColor.GRAY)));
}
private void showTeamDialog(Player player){
Group g=groupOf(player.getUniqueId());List<DialogBody> body=new ArrayList<>();List<ActionButton> actions=new ArrayList<>();
if(g==null){
body.add(message("Ты пока не состоишь в команде. Максимум 3 человека, цвет выдаётся случайно."));
body.add(message("Создание: /team create Название  •  Принятие приглашения: /team accept"));
actions.add(action("Принять приглашение","Подтвердить последнее приглашение","team_accept",NamedTextColor.GREEN));
}else{
body.add(message("Команда: "+g.name+"  •  Цвет: "+g.color.name()+"  •  "+g.members.size()+"/3"));
for(UUID id:g.members){String name=Bukkit.getOfflinePlayer(id).getName();body.add(message((id.equals(g.leader)?"★ ":"• ")+(name==null?id:name)+" — PvP "+on(g.pvpOn.contains(id))));}
body.add(message("PvP между союзниками сработает, только если ОБА игрока разрешили урон."));
actions.add(action("⚔ Мой PvP: "+on(g.pvpOn.contains(player.getUniqueId())),"Переключить PvP лично для себя","team_pvp",NamedTextColor.RED));
if(g.leader.equals(player.getUniqueId()))body.add(message("Пригласить: /team invite Ник  •  Исключить: /team kick Ник  •  Удалить: /team disband"));
actions.add(action("Выйти из команды","Если ты лидер, назначится следующий участник","team_leave",NamedTextColor.YELLOW));
}
actions.add(action("← Вернуться","К настройкам привата","claim_open",NamedTextColor.GRAY));
dialog(player,"♜ КОМАНДА",body,actions);
}
@EventHandler public void dialogAction(PlayerCustomClickEvent e){
if(!e.getIdentifier().namespace().equals("targetclaim"))return;
if(!(e.getCommonConnection() instanceof PlayerGameConnection game))return;
Player p=game.getPlayer();String action=e.getIdentifier().value();
Bukkit.getScheduler().runTask(this,()->handleDialogAction(p,action));
}
private void handleDialogAction(Player p,String a){
if(!p.isOnline())return;
if(a.equals("team_open")){showTeamDialog(p);return;}
if(a.equals("team_accept")){teamCommand(p,new String[]{"accept"});showTeamDialog(p);return;}
if(a.equals("team_leave")){teamCommand(p,new String[]{"leave"});return;}
if(a.equals("team_pvp")){teamCommand(p,new String[]{"pvp"});showTeamDialog(p);return;}
Claim c=viewedClaim(p);if(c==null){tell(p,"§cПриват не найден.");return;}
if(!p.getUniqueId().equals(c.owner)&&!allies(p.getUniqueId(),c.owner)){tell(p,"§cДоступ запрещён.");return;}
if(a.equals("claim_open")){showClaimDialog(p,c);return;}
if(a.equals("claim_close")){p.closeDialog();return;}
if(a.equals("claim_border")){Viewer v=viewer(p);v.borders=!v.borders;saveAll();showClaimDialog(p,c);return;}
if(!p.getUniqueId().equals(c.owner)){tell(p,"§cТолько владелец изменяет настройки.");return;}
switch(a){
case "claim_upgrade"->{upgrade(p,c);showClaimDialog(p,c);return;}
case "claim_build"->c.teamBuild=!c.teamBuild;
case "claim_remove"->{showRemoveDialog(p,c);return;}
case "claim_delete_confirm"->{startDeletion(p,c);return;}
case "claim_delete_cancel"->{cancelDeletion(p,c);return;}
default->{return;}
}
saveAll();showClaimDialog(p,c);
}
}