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
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
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
private static final int[] SIZE = {32,64,96,144,210};
private static final int[] HEIGHT = {48,64,96,144,210};
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
private final Map<UUID,org.bukkit.scheduler.BukkitTask> pendingDeletion = new HashMap<>();
private final Map<UUID,Integer> lastKeyUseTick = new HashMap<>();
private final Random random = new Random();
private NamespacedKey targetKey,displayKey,raidKey,independentKey;
private File dataFile;

private static final class Claim {
UUID id,owner,world;
String ownerName;
int x,y,z,level;
boolean teamBuild=false,independent=false;
Claim(UUID owner,String name,Location loc){this(UUID.randomUUID(),owner,name,loc);}
Claim(UUID id,UUID owner,String name,Location loc){this.id=id;this.owner=owner;ownerName=name;world=loc.getWorld().getUID();x=loc.getBlockX();y=loc.getBlockY();z=loc.getBlockZ();}
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
raidKey=new NamespacedKey(this,"chest_key");
independentKey=new NamespacedKey(this,"independent_claim");
dataFile=new File(getDataFolder(),"data.yml");getDataFolder().mkdirs();
loadAll();
for(Claim claim:claims.values())indexClaim(claim);
if(!dataFile.isFile()&&new File(getDataFolder(),"claims.yml").isFile()){saveAll();getLogger().info("Migrated legacy claims.yml to data.yml");}
ShapedRecipe recipe=new ShapedRecipe(new NamespacedKey(this,"target_recipe"),specialTarget());
recipe.shape("PPP","AAA","AAA");
recipe.setIngredient('P',Material.PAPER);
recipe.setIngredient('A',Material.AMETHYST_SHARD);
Bukkit.addRecipe(recipe);
ShapedRecipe keyRecipe=new ShapedRecipe(new NamespacedKey(this,"chest_key_recipe"),specialChestKey());
keyRecipe.shape("A","S");
keyRecipe.setIngredient('A',Material.AMETHYST_SHARD);
keyRecipe.setIngredient('S',Material.STICK);
Bukkit.addRecipe(keyRecipe);
Objects.requireNonNull(getCommand("p")).setExecutor(this);
Objects.requireNonNull(getCommand("team")).setExecutor(this);
Objects.requireNonNull(getCommand("p")).setTabCompleter(this);
Objects.requireNonNull(getCommand("team")).setTabCompleter(this);
Bukkit.getPluginManager().registerEvents(this,this);
for(Group g:groups.values())updateScoreboard(g);
for(World w:Bukkit.getWorlds())for(Chunk ch:w.getLoadedChunks())cleanupDisplays(ch);
for(Claim c:claims.values())ensureDisplay(c);
Bukkit.getScheduler().runTaskTimer(this,this::visualTick,15L,15L);
getLogger().info("TargetClaim 1.6.0: "+claims.size()+" claims, "+groups.size()+" teams");
}
@Override public void onDisable(){saveAll();}

private ItemStack specialTarget(){
ItemStack item=new ItemStack(Material.TARGET);
ItemMeta meta=item.getItemMeta();meta.setDisplayName("§d§l✦ Ядро привата ✦");
meta.setLore(List.of("§7Обычный улучшаемый приват","§7Лимит: §f2 в каждом мире, 1 в Энде","§7ПКМ: §fменю настроек","§cПри удалении возвращается только бумага"));
meta.getPersistentDataContainer().set(targetKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);return item;
}
public ItemStack independentTarget(){
ItemStack item=specialTarget();
ItemMeta meta=item.getItemMeta();
meta.setDisplayName("§6§lНезависимый приват");
meta.setLore(List.of("§7Размер: §e96×96×96 §7(как уровень 2)","§cБез улучшений","§7Отдельный лимит: §f3 во всех мирах","§6Цена торговца: 20 алмазов"));
meta.getPersistentDataContainer().set(independentKey,PersistentDataType.BYTE,(byte)1);
item.setItemMeta(meta);return item;
}
private boolean isIndependent(ItemStack item){return isSpecial(item)&&item.getItemMeta().getPersistentDataContainer().has(independentKey,PersistentDataType.BYTE);}
private boolean isSpecial(ItemStack item){return item!=null&&item.getType()==Material.TARGET&&item.hasItemMeta()&&Byte.valueOf((byte)1).equals(item.getItemMeta().getPersistentDataContainer().get(targetKey,PersistentDataType.BYTE));}
private ItemStack specialChestKey(){
ItemStack key=new ItemStack(Material.TRIPWIRE_HOOK,1);
ItemMeta meta=key.getItemMeta();
meta.setDisplayName("§6§lКлюч сундука");
meta.setItemModel(new NamespacedKey("targetclaim","chest_key"));
meta.setLore(List.of("§7Одноразовый ключ для сундуков","§7в чужих приватах.","§eПКМ по сундуку: §f1 случайный предмет","§8На одну попытку расходуется один ключ."));
meta.getPersistentDataContainer().set(raidKey,PersistentDataType.BYTE,(byte)1);
key.setItemMeta(meta);
return key;
}
private boolean isChestKey(ItemStack item){
return item!=null&&item.getType()==Material.TRIPWIRE_HOOK&&item.hasItemMeta()&&Byte.valueOf((byte)1).equals(item.getItemMeta().getPersistentDataContainer().get(raidKey,PersistentDataType.BYTE));
}

private void loadAll(){
File sourceFile=dataFile.isFile()?dataFile:new File(getDataFolder(),"claims.yml");
YamlConfiguration y=YamlConfiguration.loadConfiguration(sourceFile);
ConfigurationSection claimsSection=y.getConfigurationSection("claims");
if(claimsSection!=null)for(String id:claimsSection.getKeys(false))try{
UUID claimId=UUID.fromString(id);
UUID owner=UUID.fromString(y.getString("claims."+id+".owner",id));
String root="claims."+id+".";
World w=Bukkit.getWorld(UUID.fromString(Objects.requireNonNull(y.getString(root+"world"))));
if(w==null){getLogger().warning("Skipping claim with unloaded world: "+id);continue;}
Claim c=new Claim(claimId,owner,y.getString(root+"name","unknown"),new Location(w,y.getInt(root+"x"),y.getInt(root+"y"),y.getInt(root+"z")));
c.level=Math.max(0,Math.min(4,y.getInt(root+"level",0)));
c.independent=y.getBoolean(root+"independent",false);
if(c.independent)c.level=2;
c.teamBuild=y.getBoolean(root+"teamBuild",false);
claims.put(c.id,c);
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
String r="claims."+c.id+".";y.set(r+"owner",c.owner.toString());y.set(r+"name",c.ownerName);y.set(r+"world",c.world.toString());y.set(r+"x",c.x);y.set(r+"y",c.y);y.set(r+"z",c.z);y.set(r+"level",c.level);y.set(r+"teamBuild",c.teamBuild);y.set(r+"independent",c.independent);
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
private List<Claim> ownedClaims(UUID owner){
return claims.values().stream().filter(c->c.owner.equals(owner))
.sorted(Comparator.comparing((Claim c)->c.world.toString()).thenComparing(c->c.id.toString())).toList();
}
private List<Claim> ownedInWorld(UUID owner,UUID world){
return ownedClaims(owner).stream().filter(c->c.world.equals(world)).toList();
}
private long normalCount(UUID owner,UUID world){
return ownedInWorld(owner,world).stream().filter(c->!c.independent).count();
}
private long specialCount(UUID owner){
return ownedClaims(owner).stream().filter(c->c.independent).count();
}
private int normalLimit(World world){
return world.getEnvironment()==World.Environment.THE_END?1:2;
}
private String worldName(Claim claim){
World world=Bukkit.getWorld(claim.world);
return world==null?claim.world.toString():world.getName();
}
private String claimTitle(Claim claim){
return (claim.independent?"§6Независимый":"§fОбычный")+" §8• §f"+dimension(claim.level)+" §8• §7"+worldName(claim);
}
private Claim selectedClaim(Player player){
Claim nearby=claimAt(player.getLocation());
if(nearby!=null&&nearby.owner.equals(player.getUniqueId()))return nearby;
List<Claim> here=ownedInWorld(player.getUniqueId(),player.getWorld().getUID());
if(!here.isEmpty())return here.get(0);
List<Claim> all=ownedClaims(player.getUniqueId());
return all.isEmpty()?null:all.get(0);
}
private boolean owner(Player player,Claim c){return player.getUniqueId().equals(c.owner);}
private boolean canUse(Player player,Claim c){return canBuild(player,c);}
private boolean canBuild(Player player,Claim c){return owner(player,c)||(c.teamBuild&&allies(player.getUniqueId(),c.owner));}
// Safe integration point for short-lived PvP structures in MinecraftUnified.
// The public query never changes claim state or grants bypass permissions.
public boolean allowsTemporaryStructure(Player player,Location place){
Claim claim=claimAt(place);
return claim==null||canBuild(player,claim);
}
private boolean friendlyDamageBlocked(Player attacker,Player victim){
if(attacker==null||victim==null||attacker.equals(victim)||!allies(attacker.getUniqueId(),victim.getUniqueId()))return false;
Group g=groupOf(attacker.getUniqueId());return g!=null&&!(g.pvpOn.contains(attacker.getUniqueId())&&g.pvpOn.contains(victim.getUniqueId()));
}
private void tell(CommandSender sender,String message){sender.sendMessage(PREFIX+message);}

@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(command.getName().equalsIgnoreCase("team"))return teamCommand(sender,args);
if(args.length>0&&args[0].equalsIgnoreCase("givekey")){
if(!sender.hasPermission("targetclaim.admin")){tell(sender,"§cНет прав.");return true;}
if(args.length<2){tell(sender,"§e/p givekey <ник>");return true;}
Player recipient=Bukkit.getPlayerExact(args[1]);if(recipient==null){tell(sender,"§cИгрок не найден.");return true;}
for(ItemStack rest:recipient.getInventory().addItem(specialChestKey()).values())recipient.getWorld().dropItemNaturally(recipient.getLocation(),rest);
tell(sender,"§aКлюч выдан игроку "+recipient.getName());return true;
}
if(args.length>0&&args[0].equalsIgnoreCase("give")){
if(!sender.hasPermission("targetclaim.admin")){tell(sender,"§cНет прав.");return true;}
if(args.length<2){tell(sender,"§e/p give <ник>");return true;}
Player recipient=Bukkit.getPlayerExact(args[1]);if(recipient==null){tell(sender,"§cИгрок не найден.");return true;}
for(ItemStack rest:recipient.getInventory().addItem(specialTarget()).values())recipient.getWorld().dropItemNaturally(recipient.getLocation(),rest);
tell(sender,"§aМишень выдана игроку "+recipient.getName());return true;
}
if(!(sender instanceof Player p)){tell(sender,"§cЭта команда только для игроков.");return true;}
Claim c=selectedClaim(p);
if(args.length>0&&args[0].equalsIgnoreCase("list")){
List<Claim> mine=ownedClaims(p.getUniqueId());
tell(p,"§eВ этом мире: §f"+normalCount(p.getUniqueId(),p.getWorld().getUID())+"/"+normalLimit(p.getWorld())
+" §7обычных. §eНезависимых во всех мирах: §f"+specialCount(p.getUniqueId())+"/3");
for(int i=0;i<mine.size();i++){
Claim own=mine.get(i);
tell(p,"§7/p "+(i+1)+" §f"+(own.independent?"Независимый":"Обычный")+" §8| §f"+dimension(own.level)+
" §7"+worldName(own)+" §8("+own.x+", "+own.y+", "+own.z+")");
}
if(mine.isEmpty())tell(p,"§7У тебя пока нет приватных территорий.");
return true;
}
if(args.length>0&&args[0].matches("[1-9][0-9]*")){
List<Claim> mine=ownedClaims(p.getUniqueId());
int index;
try{index=Integer.parseInt(args[0])-1;}catch(NumberFormatException ex){tell(p,"§cНеверный номер.");return true;}
if(index>=0&&index<mine.size())showClaimDialog(p,mine.get(index));
else tell(p,"§cПриват №"+(index+1)+" отсутствует. Используй /p list.");
return true;
}
if(c==null){tell(p,"§7Нет привата. Рецепт: §f3 бумаги сверху и 6 аметистов снизу.");return true;}
if(args.length==0){showClaimDialog(p,c);return true;}
switch(args[0].toLowerCase(Locale.ROOT)){
case "upgrade" -> upgrade(p,c);
case "remove" -> startDeletion(p,c);
case "cancel" -> cancelDeletion(p,c);
case "border","borders" -> {
Viewer v=viewer(p);
if(args.length==1)v.borders=!v.borders;
else if(args[1].equalsIgnoreCase("on"))v.borders=true;
else if(args[1].equalsIgnoreCase("off"))v.borders=false;
else{tell(p,"§eИспользуй: /p border on|off");return true;}
saveAll();tell(p,"§eКрасные границы: "+(v.borders?"§aВКЛ":"§cВЫКЛ"));
}
case "info" -> {
tell(p,"§d"+(c.independent?"Независимый приват без улучшений":"Уровень "+c.level+"/4")
+", размер: "+dimension(c.level)+", мир: "+worldName(c)+".");
tell(p,"§eОбычные здесь: §f"+normalCount(p.getUniqueId(),p.getWorld().getUID())+"/"+normalLimit(p.getWorld())
+" §8| §eНезависимые глобально: §f"+specialCount(p.getUniqueId())+"/3");
}
default -> showClaimDialog(p,c);
}
return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command cmd,String alias,String[] args){
if(args.length==1)return starts(cmd.getName().equalsIgnoreCase("p")?List.of("list","1","2","3","4","5","6","7","8","9","upgrade","border","remove","cancel","info","give","givekey"):List.of("create","invite","accept","deny","leave","kick","disband","pvp","info","menu"),args[0]);
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
@EventHandler public void join(PlayerJoinEvent event){
Player p=event.getPlayer();
// Existing chest keys retain their PDC and gain the new client texture on login.
for(ItemStack item:p.getInventory().getContents())if(isChestKey(item)){
ItemMeta m=item.getItemMeta();if(!new NamespacedKey("targetclaim","chest_key").equals(m.getItemModel())){
m.setItemModel(new NamespacedKey("targetclaim","chest_key"));item.setItemMeta(m);
}
}
Group g=groupOf(p.getUniqueId());if(g!=null)updateScoreboard(g);
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void place(BlockPlaceEvent e){
Block b=e.getBlockPlaced();Player p=e.getPlayer();Claim occupied=claimAt(b.getLocation());
if(occupied!=null&&!canBuild(p,occupied)){e.setCancelled(true);tell(p,"§cСтроительство запрещено.");return;}
if(!isSpecial(e.getItemInHand()))return;
boolean special=isIndependent(e.getItemInHand());
if(special){
if(specialCount(p.getUniqueId())>=3){
e.setCancelled(true);tell(p,"§cМожно иметь не более 3 независимых приватов во всех мирах.");return;
}
}else{
int limit=normalLimit(b.getWorld());
if(normalCount(p.getUniqueId(),b.getWorld().getUID())>=limit){
e.setCancelled(true);tell(p,"§cЛимит обычных приватов в мире "+b.getWorld().getName()+": "+limit+".");return;
}
}
Claim c=new Claim(p.getUniqueId(),p.getName(),b.getLocation());
c.independent=special;
if(c.independent)c.level=2;
if(c.minY()<b.getWorld().getMinHeight()||c.maxY()>=b.getWorld().getMaxHeight()){e.setCancelled(true);tell(p,"§cУстанови мишень выше/ниже: область должна помещаться в высоту мира.");return;}
if(overlap(c)){e.setCancelled(true);tell(p,"§cПриват пересекается с другой территорией.");return;}
claims.put(c.id,c);indexClaim(c);
Bukkit.getScheduler().runTask(this,()->{if(b.getType()!=Material.TARGET){claims.remove(c.id,c);unindexClaim(c);return;}ensureDisplay(c);saveAll();});
tell(p,"§a"+(c.independent?"Независимый":"Обычный")+" приват создан: §f"+dimension(c.level)+". §7"+
(c.independent?"Независимые: "+specialCount(p.getUniqueId())+"/3 во всех мирах.":
"Обычные в этом мире: "+normalCount(p.getUniqueId(),b.getWorld().getUID())+"/"+normalLimit(b.getWorld())+".")+" §7ПКМ — меню.");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void breaking(BlockBreakEvent e){
Claim c=claimCore(e.getBlock());
if(c!=null){e.setCancelled(true);e.setDropItems(false);e.setExpToDrop(0);
if(!e.getPlayer().getUniqueId().equals(c.owner)){tell(e.getPlayer(),"§cЯдро привата защищено.");return;}
startDeletion(e.getPlayer(),c);return;}
c=claimAt(e.getBlock().getLocation());if(c!=null&&!canBuild(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void interact(PlayerInteractEvent e){
Block b=e.getClickedBlock();if(b==null)return;
Claim core=claimCore(b);
if(core!=null&&e.getAction()==Action.RIGHT_CLICK_BLOCK){e.setCancelled(true);
if(e.getHand()!=EquipmentSlot.HAND)return;
if(e.getPlayer().getUniqueId().equals(core.owner)&&pendingDeletion.containsKey(core.id)){
cancelDeletion(e.getPlayer(),core);return;
}
if(e.getPlayer().isSneaking()&&e.getPlayer().getUniqueId().equals(core.owner)&&!core.independent&&core.level<4&&e.getPlayer().getInventory().getItemInMainHand().getType()==COST[core.level]){upgrade(e.getPlayer(),core);return;}
showClaimDialog(e.getPlayer(),core);return;
}
Claim c=claimAt(b.getLocation());if(c==null)return;
if(e.getAction()==Action.RIGHT_CLICK_BLOCK&&e.getHand()==EquipmentSlot.HAND
&&isChestKey(e.getPlayer().getInventory().getItemInMainHand())
&&(b.getType()==Material.CHEST||b.getType()==Material.TRAPPED_CHEST)
&&!canUse(e.getPlayer(),c)){
e.setCancelled(true);e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);
stealOneFromChest(e.getPlayer(),b,c);
return;
}
if(!canUse(e.getPlayer(),c)){
if(e.getAction()==Action.RIGHT_CLICK_BLOCK){
e.setCancelled(true);e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);
}else if(e.getAction()==Action.PHYSICAL)e.setCancelled(true);
}
}
private void stealOneFromChest(Player player,Block b,Claim c){
if(player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR){
tell(player,"§cКлюч работает только в выживании или приключении.");return;
}
int tick=Bukkit.getCurrentTick();
if(Objects.equals(lastKeyUseTick.get(player.getUniqueId()),tick))return;
ItemStack held=player.getInventory().getItemInMainHand();
if(!isChestKey(held)||held.getAmount()<1||claims.get(c.id)!=c)return;
if(!(b.getState() instanceof org.bukkit.block.Chest chest))return;
Inventory inventory=chest.getInventory();
if(inventory.getHolder() instanceof org.bukkit.block.DoubleChest doubleChest){
InventoryHolder left=doubleChest.getLeftSide(),right=doubleChest.getRightSide();
if(!(left instanceof org.bukkit.block.Chest lc&&right instanceof org.bukkit.block.Chest rc)
||claimAt(lc.getLocation())!=c||claimAt(rc.getLocation())!=c)
inventory=chest.getBlockInventory();
}
int total=0;
for(ItemStack item:inventory.getContents())if(item!=null&&item.getType()!=Material.AIR&&item.getAmount()>0)total+=item.getAmount();
if(total<=0){tell(player,"§7Сундук пуст. Ключ не израсходован.");return;}
int roll=random.nextInt(total),slot=-1;
for(int i=0;i<inventory.getSize();i++){
ItemStack item=inventory.getItem(i);
if(item==null||item.getType()==Material.AIR||item.getAmount()<=0)continue;
roll-=item.getAmount();
if(roll<0){slot=i;break;}
}
if(slot<0)return;
ItemStack original=inventory.getItem(slot);
if(original==null||original.getAmount()<=0)return;
ItemStack one=original.clone();one.setAmount(1);
ItemStack rest=original.clone();rest.setAmount(original.getAmount()-1);
ItemStack heldBefore=held.clone();
lastKeyUseTick.put(player.getUniqueId(),tick);
inventory.setItem(slot,rest.getAmount()==0?null:rest);
if(heldBefore.getAmount()==1)player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
else{ItemStack reduced=heldBefore.clone();reduced.setAmount(heldBefore.getAmount()-1);player.getInventory().setItemInMainHand(reduced);}
try{
b.getWorld().dropItemNaturally(b.getLocation().add(.5,1.2,.5),one);
}catch(RuntimeException ex){
inventory.setItem(slot,original);
player.getInventory().setItemInMainHand(heldBefore);
getLogger().warning("Could not drop chest-key loot: "+ex.getMessage());
tell(player,"§cНе удалось получить предмет. Ключ возвращён.");
return;
}
tell(player,"§aКлюч сработал: §f1 предмет §7из сундука.");
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void preventBlockDamage(BlockDamageEvent e){
Claim c=claimAt(e.getBlock().getLocation());
if(c!=null&&!canBuild(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void preventBucketEmpty(PlayerBucketEmptyEvent e){
Block placed=e.getBlockClicked().getRelative(e.getBlockFace());
Claim c=claimAt(placed.getLocation());
if(c!=null&&!canBuild(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void preventBucketFill(PlayerBucketFillEvent e){
Claim c=claimAt(e.getBlockClicked().getLocation());
if(c!=null&&!canBuild(e.getPlayer(),c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void preventIgnite(BlockIgniteEvent e){
Player player=e.getPlayer();
if(player==null)return;
Claim c=claimAt(e.getBlock().getLocation());
if(c!=null&&!canBuild(player,c))e.setCancelled(true);
}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void preventArmorStand(PlayerArmorStandManipulateEvent e){
Claim c=claimAt(e.getRightClicked().getLocation());
if(c!=null&&!canUse(e.getPlayer(),c))e.setCancelled(true);
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
if(c.independent){tell(p,"§cНезависимый приват нельзя улучшить.");return;}
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
if(!claims.remove(c.id,c))return;
unindexClaim(c);
org.bukkit.scheduler.BukkitTask pending=pendingDeletion.remove(c.id);
if(pending!=null)pending.cancel();
World w=Bukkit.getWorld(c.world);
if(w!=null){Block b=w.getBlockAt(c.x,c.y,c.z);if(b.getType()==Material.TARGET){
b.setType(Material.AIR,false);
w.dropItemNaturally(new Location(w,c.x+.5,c.y+.5,c.z+.5),new ItemStack(Material.PAPER,3));
}
for(Entity entity:w.getNearbyEntities(new Location(w,c.x+.5,c.y+1.7,c.z+.5),2,3,2))if(entity instanceof TextDisplay&&c.id.toString().equals(entity.getPersistentDataContainer().get(displayKey,PersistentDataType.STRING)))entity.remove();}
saveAll();
}
private void startDeletion(Player p,Claim c){
if(!p.getUniqueId().equals(c.owner))return;
if(claims.get(c.id)!=c)return;
if(pendingDeletion.containsKey(c.id)){
tell(p,"§eУдаление уже запущено. §7ПКМ по ядру или §f/p cancel §7— отменить.");return;
}
org.bukkit.scheduler.BukkitTask task=Bukkit.getScheduler().runTaskLater(this,()->{
pendingDeletion.remove(c.id);
if(claims.get(c.id)!=c)return;
removeClaim(c);
Player online=Bukkit.getPlayer(c.owner);
if(online!=null){
if(online.getOpenInventory().getTopInventory().getHolder() instanceof ClaimMenu)online.closeInventory();
tell(online,"§aПриват удалён. Возвращено §f3 бумаги§a.");
}
},200L);
pendingDeletion.put(c.id,task);
if(p.getOpenInventory().getTopInventory().getHolder() instanceof ClaimMenu)p.closeInventory();
tell(p,"§cУдаление через 10 секунд. §7ПКМ по ядру — отменить. §8(/p cancel)");
}
private void cancelDeletion(Player p,Claim c){
if(!p.getUniqueId().equals(c.owner)){tell(p,"§cТолько владелец может отменить удаление.");return;}
org.bukkit.scheduler.BukkitTask task=pendingDeletion.remove(c.id);
if(task==null){tell(p,"§7Удаление не запущено.");return;}
task.cancel();
tell(p,"§aУдаление отменено. Приват сохранён.");
Inventory top=p.getOpenInventory().getTopInventory();
if(top.getHolder() instanceof ClaimMenu menu&&menu.page.equals("main")&&c.id.equals(menu.claimId))
top.setItem(15,guiItem(Material.RED_DYE,"§cУдалить","§7Запустить отсчёт 10 секунд"));
}

private void ensureDisplay(Claim c){
World w=Bukkit.getWorld(c.world);if(w==null||!w.isChunkLoaded(c.x>>4,c.z>>4))return;
if(w.getBlockAt(c.x,c.y,c.z).getType()!=Material.TARGET)return;
Location position=new Location(w,c.x+.5,c.y+1.55,c.z+.5);
for(Entity entity:w.getNearbyEntities(position,2,3,2))if(entity instanceof TextDisplay text&&c.id.toString().equals(entity.getPersistentDataContainer().get(displayKey,PersistentDataType.STRING))){setDisplayText(text,c);return;}
TextDisplay entity=w.spawn(position,TextDisplay.class,text->{
text.setBillboard(Display.Billboard.CENTER);text.setShadowed(true);text.setSeeThrough(false);
text.setBackgroundColor(Color.fromARGB(145,20,13,35));text.setLineWidth(180);text.setPersistent(true);
text.getPersistentDataContainer().set(displayKey,PersistentDataType.STRING,c.id.toString());
});
setDisplayText(entity,c);
}
private void setDisplayText(TextDisplay display,Claim c){
display.setText("§c§lTARGETCLAIM\n§7"+c.ownerName+" §8• §f"+(c.independent?"Независимый":c.level+"/4"));
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
if(c.independent||c.level>=4)return false;
ItemStack held=p.getInventory().getItemInMainHand();
return held.getType()==COST[c.level]&&held.getAmount()>=AMOUNT[c.level];
}
private Viewer viewer(Player p){return viewers.computeIfAbsent(p.getUniqueId(),id->new Viewer());}
private void visualTick(){
for(Player p:Bukkit.getOnlinePlayers()){
Viewer v=viewer(p);Claim mine=selectedClaim(p);boolean preview=mine!=null&&enoughInHand(p,mine);
if(!v.borders&&!preview)continue;
Location pos=p.getLocation();
ParticleBudget red=new ParticleBudget(190),cyan=new ParticleBudget(120),gold=new ParticleBudget(65);
for(Claim c:regionClaims(p.getWorld().getUID(),pos.getBlockX()-72,pos.getBlockX()+72,pos.getBlockZ()-72,pos.getBlockZ()+72)){
boolean self=c.owner.equals(p.getUniqueId());
if(!self&&!(c.teamBuild&&allies(p.getUniqueId(),c.owner)))continue;
double dx=c.x+.5-pos.getX(),dz=c.z+.5-pos.getZ();
if(Math.abs(dx)>SIZE[c.level]/2.+72||Math.abs(dz)>SIZE[c.level]/2.+72||Math.abs(pos.getY()-(c.y+.5))>HEIGHT[c.level]/2.+75)continue;
if(v.borders&&red.remaining>0)drawBorder(p,c,c.level,BORDER_RED,3.0,65,red,false);
if(self&&preview&&!c.independent){
drawBorder(p,c,c.level+1,validLevel(c,c.level+1)?PREVIEW_CYAN:BLOCKED_ORANGE,4.0,75,cyan,false);
if(c.level+1<4)drawBorder(p,c,4,MAX_GOLD,9.0,80,gold,false);
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

private String dimension(int level){return SIZE[level]+" × "+HEIGHT[level]+" × "+SIZE[level];}
private void showClaimDialog(Player player,Claim c){
if(!owner(player,c)&&!allies(player.getUniqueId(),c.owner)){tell(player,"§cЧужой приват.");return;}
openClaimMenu(player,c,"main");
}
private void showUpgradeDialog(Player player,Claim c){
if(player.getUniqueId().equals(c.owner))openClaimMenu(player,c,"upgrade");
}
private String on(boolean value){return value?"ВКЛ":"ВЫКЛ";}
private void showTeamDialog(Player player){openClaimMenu(player,selectedClaim(player),"team");}
private final class ClaimMenu implements InventoryHolder {
final UUID claimId;
final String page;
final Inventory inventory;
ClaimMenu(Claim claim,String page){
this.claimId=claim==null?null:claim.id;
this.page=page;
String title=switch(page){
case "upgrade"->"Улучшение";case "team"->"Команда";default->"TargetClaim";
};
this.inventory=Bukkit.createInventory(this,27,"§8"+title);
}
@Override public Inventory getInventory(){return inventory;}
}
private ItemStack guiItem(Material material,String title,String... lore){
ItemStack stack=new ItemStack(material);
ItemMeta meta=stack.getItemMeta();
if(meta!=null){meta.setDisplayName(title);meta.setLore(Arrays.asList(lore));stack.setItemMeta(meta);}
return stack;
}
private void guiBase(Inventory inv){
ItemStack black=guiItem(Material.BLACK_STAINED_GLASS_PANE," ");
for(int i=0;i<27;i++)inv.setItem(i,black);
inv.setItem(26,guiItem(Material.BARRIER,"§7Закрыть"));
}
private void openClaimMenu(Player player,Claim claim,String page){
ClaimMenu menu=new ClaimMenu(claim,page);
guiBase(menu.inventory);
switch(page){
case "main"->fillMainMenu(menu.inventory,player,claim);
case "upgrade"->fillUpgradeMenu(menu.inventory,player,claim);
case "team"->fillTeamMenu(menu.inventory,player);
}
player.openInventory(menu.inventory);
}
private ItemStack borderTile(Player player){
boolean on=viewer(player).borders;
return guiItem(on?Material.REDSTONE:Material.GRAY_DYE,"§fГраницы: "+(on?"§aВКЛ":"§cВЫКЛ"),"§7Красные частицы");
}
private ItemStack buildTile(Claim claim){
return guiItem(Material.IRON_DOOR,"§fСтроительство","§7"+(claim.teamBuild?"§aВладелец + команда":"§fТолько владелец"));
}
private void fillMainMenu(Inventory inv,Player player,Claim claim){
boolean own=claim.owner.equals(player.getUniqueId());
inv.setItem(4,guiItem(Material.TARGET,"§f§l"+(claim.independent?"Независимый приват":"Приват "+claim.level+"/4"),"§7Владелец: §f"+claim.ownerName,"§7Размер: §f"+dimension(claim.level),claim.independent?"§cБез улучшений":"§7Улучшение через аметист, лом и яйцо"));
if(own&&!claim.independent&&claim.level<4)inv.setItem(11,guiItem(Material.AMETHYST_SHARD,"§aУлучшить","§7Стоимость и предпросмотр"));
inv.setItem(12,borderTile(player));
if(own)inv.setItem(13,buildTile(claim));
inv.setItem(14,guiItem(Material.NAME_TAG,"§dКоманда","§7Участники и PvP"));
List<Claim> owned=ownedClaims(player.getUniqueId());
if(own&&owned.size()>1){
Claim other=owned.get(0)==claim?owned.get(1):owned.get(0);
inv.setItem(22,guiItem(Material.COMPASS,"§bДругой приват","§7"+(other.independent?"Независимый":"Обычный")+" §8| §f"+dimension(other.level),"§7Нажми для переключения"));
}
if(own){
if(pendingDeletion.containsKey(claim.id))inv.setItem(15,guiItem(Material.LIME_DYE,"§aОтменить удаление","§7Приват останется на месте"));
else inv.setItem(15,guiItem(Material.RED_DYE,"§cУдалить","§7Запустить отсчёт 10 секунд"));
}
}
private void fillUpgradeMenu(Inventory inv,Player player,Claim claim){
inv.setItem(4,guiItem(Material.TARGET,"§fПриват §7"+claim.level+"/4"));
inv.setItem(10,guiItem(Material.PAPER,"§fТекущий размер","§7"+dimension(claim.level)));
inv.setItem(16,guiItem(Material.GOLD_INGOT,"§6Максимум","§7"+dimension(4)));
if(!claim.independent&&claim.level<4){
inv.setItem(12,guiItem(Material.CYAN_DYE,"§bСледующий размер","§7"+dimension(claim.level+1)));
inv.setItem(14,guiItem(COST[claim.level],"§eЦена","§7"+AMOUNT[claim.level]+" × "+russian(COST[claim.level])));
boolean has=enoughInHand(player,claim),free=validLevel(claim,claim.level+1);
inv.setItem(22,guiItem(!free?Material.ORANGE_DYE:has?Material.LIME_DYE:Material.GRAY_DYE,has&&free?"§aУлучшить":"§7Улучшить",!free?"§cНельзя расширить":has?"§7Ресурсы готовы":"§7Держи ресурсы в руке"));
}else{
inv.setItem(12,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
inv.setItem(14,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
inv.setItem(22,guiItem(Material.NETHER_STAR,"§6Максимальный уровень"));
}
inv.setItem(18,guiItem(Material.ARROW,"§7Назад"));
}
private void fillTeamMenu(Inventory inv,Player player){
Group group=groupOf(player.getUniqueId());
inv.setItem(4,guiItem(Material.NAME_TAG,"§dКоманда",group==null?"§7Нет команды":"§7"+group.name+" • "+group.members.size()+"/3"));
for(int slot=10;slot<=12;slot++)inv.setItem(slot,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
inv.setItem(15,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
inv.setItem(20,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
inv.setItem(22,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
if(group==null){
inv.setItem(13,guiItem(Material.BOOK,"§fСоздать команду","§7/team create Название"));
inv.setItem(15,guiItem(Material.LIME_DYE,"§aПринять приглашение","§7/team accept"));
}else{
inv.setItem(13,guiItem(Material.BLACK_STAINED_GLASS_PANE," "));
int index=10;
for(UUID member:group.members){
if(index>12)break;
String nick=Bukkit.getOfflinePlayer(member).getName();
inv.setItem(index++,guiItem(Material.PLAYER_HEAD,"§f"+(nick==null?member:nick),member.equals(group.leader)?"§6Лидер":"§7Участник","§7PvP: "+(group.pvpOn.contains(member)?"§aВКЛ":"§cВЫКЛ")));
}
inv.setItem(15,guiItem(group.pvpOn.contains(player.getUniqueId())?Material.LIME_DYE:Material.RED_DYE,"§fМой PvP: "+(group.pvpOn.contains(player.getUniqueId())?"§aВКЛ":"§cВЫКЛ"),"§7Урон только при взаимном согласии"));
inv.setItem(20,guiItem(Material.BOOK,"§fУправление командой","§7/team invite Ник","§7/team kick Ник","§7/team disband"));
inv.setItem(22,guiItem(Material.BARRIER,"§cВыйти из команды"));
}
inv.setItem(18,guiItem(Material.ARROW,"§7Назад"));
}
private void changePage(Player player,Claim claim,String page){
Bukkit.getScheduler().runTask(this,()->{
if(!player.isOnline())return;
if(claim!=null&&claims.get(claim.id)!=claim){player.closeInventory();return;}
openClaimMenu(player,claim,page);
});
}
@EventHandler(priority=EventPriority.HIGHEST)
public void guiClick(InventoryClickEvent event){
Inventory inv=event.getView().getTopInventory();
if(!(inv.getHolder() instanceof ClaimMenu menu))return;
event.setCancelled(true);
if(!(event.getWhoClicked() instanceof Player player))return;
int slot=event.getRawSlot();
if(slot<0||slot>=inv.getSize())return;
if(slot==26){Bukkit.getScheduler().runTask(this,()->player.closeInventory());return;}
Claim claim=menu.claimId==null?null:claims.get(menu.claimId);
if(menu.claimId!=null&&(claim==null||(!claim.owner.equals(player.getUniqueId())&&!allies(claim.owner,player.getUniqueId())))){
Bukkit.getScheduler().runTask(this,()->player.closeInventory());return;
}
if(menu.page.equals("team")){
if(slot==18){if(claim!=null)changePage(player,claim,"main");else Bukkit.getScheduler().runTask(this,()->player.closeInventory());}
if(slot==15){
Group group=groupOf(player.getUniqueId());
if(group==null)Bukkit.getScheduler().runTask(this,()->{teamCommand(player,new String[]{"accept"});fillTeamMenu(inv,player);});
else{teamCommand(player,new String[]{"pvp"});fillTeamMenu(inv,player);}
}
if(slot==22){
Group group=groupOf(player.getUniqueId());
if(group!=null){leave(player.getUniqueId(),group);fillTeamMenu(inv,player);}
}
return;
}
if(claim==null)return;
boolean isOwner=claim.owner.equals(player.getUniqueId());
switch(menu.page){
case "main"->{
switch(slot){
case 11->{if(isOwner&&!claim.independent&&claim.level<4)changePage(player,claim,"upgrade");}
case 12->{
Viewer viewer=viewer(player);viewer.borders=!viewer.borders;saveAll();
inv.setItem(12,borderTile(player));
}
case 13->{if(isOwner){claim.teamBuild=!claim.teamBuild;saveAll();inv.setItem(13,buildTile(claim));}}
case 14->changePage(player,claim,"team");
case 22->{if(isOwner){List<Claim> owned=ownedClaims(player.getUniqueId());
if(owned.size()>1)changePage(player,owned.get(0)==claim?owned.get(1):owned.get(0),"main");}}
case 15->{if(isOwner){
if(pendingDeletion.containsKey(claim.id))cancelDeletion(player,claim);
else Bukkit.getScheduler().runTask(this,()->startDeletion(player,claim));
}}
}
}
case "upgrade"->{
if(slot==18)changePage(player,claim,"main");
else if(slot==22&&isOwner&&!claim.independent&&claim.level<4){upgrade(player,claim);fillUpgradeMenu(inv,player,claim);}
}
default->{}
}
}
@EventHandler(priority=EventPriority.HIGHEST)
public void guiDrag(InventoryDragEvent event){
Inventory inv=event.getView().getTopInventory();
if(!(inv.getHolder() instanceof ClaimMenu))return;
for(int raw:event.getRawSlots())if(raw<inv.getSize()){event.setCancelled(true);return;}
}

}