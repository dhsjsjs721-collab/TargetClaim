package dev.unified;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.block.Jukebox;
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.profile.PlayerTextures;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import java.net.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;

final class MediaItems implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey discKey=new NamespacedKey("minecraftunified","youtube_disc");
private final Map<UUID,Long> cooldown=new HashMap<>();
private final Map<UUID,PlayerProfile> originals=new HashMap<>();
private final Map<UUID,String> pending=new HashMap<>();
private final File file;
private YamlConfiguration skins;
MediaItems(UnifiedPlugin host){this.host=host;file=new File(host.getDataFolder(),"skins.yml");}
void enable(){
skins=YamlConfiguration.loadConfiguration(file);
Bukkit.getPluginManager().registerEvents(this,host);
host.getLogger().info("MediaItems enabled: YouTube link records and persistent player skin profiles.");
}
private void tell(CommandSender s,String m){s.sendMessage("§6Media §8» §r"+m);}
private String youtube(String s){
try{URI u=URI.create(s);if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1)return null;
String h=u.getHost(),id=null;if(h==null)return null;
h=h.toLowerCase(Locale.ROOT);
if(h.equals("youtu.be"))id=u.getPath().replaceFirst("^/","");
else if(Set.of("youtube.com","www.youtube.com","m.youtube.com").contains(h)){
if(u.getPath().equals("/watch")&&u.getRawQuery()!=null)for(String q:u.getRawQuery().split("&"))if(q.startsWith("v="))id=q.substring(2);
else {}
if(u.getPath().startsWith("/shorts/"))id=u.getPath().substring(8);
if(u.getPath().startsWith("/live/"))id=u.getPath().substring(6);
}
return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?"https://www.youtube.com/watch?v="+id:null;
}catch(Exception e){return null;}
}
boolean disc(CommandSender sender,String[] args){
if(!(sender instanceof Player p)){tell(sender,"§cТолько в игре.");return true;}
ItemStack hand=p.getInventory().getItemInMainHand();
if(!hand.getType().name().startsWith("MUSIC_DISC_")||hand.getAmount()!=1){tell(p,"§cДержи одну пластинку в основной руке.");return true;}
if(args.length==0){String saved=hand.getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING);if(saved!=null&&youtube(saved)!=null)link(p,saved,hand.getItemMeta().hasDisplayName()?hand.getItemMeta().getDisplayName():"YouTube");else tell(p,"§e/disc <ссылка YouTube> [название]");return true;}
if(args[0].equalsIgnoreCase("clear")){var meta=hand.getItemMeta();meta.getPersistentDataContainer().remove(discKey);meta.setItemModel(null);meta.displayName(null);meta.lore(null);hand.setItemMeta(meta);p.getInventory().setItemInMainHand(hand);tell(p,"§aЗапись удалена.");return true;}
String url=youtube(args[0]);if(url==null){tell(p,"§cНужна HTTPS-ссылка на отдельное видео YouTube.");return true;}
String name=args.length>1?String.join(" ",Arrays.copyOfRange(args,1,args.length)):"YouTube";
name=name.replaceAll("[\\p{Cntrl}§]","").strip();if(name.length()>48)name=name.substring(0,48);
var meta=hand.getItemMeta();meta.getPersistentDataContainer().set(discKey,PersistentDataType.STRING,url);meta.setItemModel(new NamespacedKey("minecraftunified","youtube_disc"));meta.displayName(Component.text(name,NamedTextColor.GOLD));meta.lore(List.of(Component.text("YouTube · Shift+ПКМ — открыть",NamedTextColor.GRAY),Component.text("Проигрыватель · ссылка для слушателей",NamedTextColor.GRAY)));hand.setItemMeta(meta);p.getInventory().setItemInMainHand(hand);tell(p,"§aСсылка записана.");return true;
}
@EventHandler(priority=EventPriority.HIGHEST)
public void playLink(PlayerInteractEvent e){
if(host.seasonLocked(e.getPlayer()))return;
if(e.getHand()!=EquipmentSlot.HAND||!e.getPlayer().isSneaking()||!e.getAction().isRightClick()||e.getItem()==null||!e.getItem().hasItemMeta())return;
String url=e.getItem().getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING);if(url==null||youtube(url)==null)return;
e.setCancelled(true);link(e.getPlayer(),url,e.getItem().getItemMeta().hasDisplayName()?e.getItem().getItemMeta().getDisplayName():"YouTube");
}
private void link(Player player,String url,String name){player.sendMessage(Component.text("[YouTube] "+name,NamedTextColor.GOLD).append(Component.text(" [Открыть]",NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void jukebox(PlayerInteractEvent e){
if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getPlayer().isSneaking()||e.useInteractedBlock()==Event.Result.DENY||e.getClickedBlock()==null||!(e.getClickedBlock().getState() instanceof Jukebox box)||e.getItem()==null||!e.getItem().hasItemMeta())return;
String url=e.getItem().getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING);if(url==null||youtube(url)==null||!box.getRecord().getType().isAir())return;
var block=e.getClickedBlock();Bukkit.getScheduler().runTask(host,()->{if(!(block.getState() instanceof Jukebox j))return;ItemStack disc=j.getRecord();if(!disc.hasItemMeta()||!url.equals(disc.getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING)))return;
j.stopPlaying();String name=disc.getItemMeta().hasDisplayName()?disc.getItemMeta().getDisplayName():"YouTube";Location loc=block.getLocation().add(.5,.5,.5);for(Player p:loc.getWorld().getPlayers())if(p.getLocation().distanceSquared(loc)<=32*32)link(p,url,name);
});
}
boolean video(CommandSender sender,String[] args){if(!(sender instanceof Player p)){tell(sender,"§cТолько в игре.");return true;}if(args.length!=1){tell(p,"§e/u video <ссылка YouTube> — обложка видео на карте в руке");return true;}String url=youtube(args[0]);if(url==null){tell(p,"§cНужна ссылка на видео YouTube.");return true;}return Bukkit.dispatchCommand(p,"imagemap https://i.ytimg.com/vi/"+url.substring(url.length()-11)+"/hqdefault.jpg");}

private boolean persist(){
try{File tmp=new File(file.getParentFile(),file.getName()+".tmp");skins.save(tmp);try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;}catch(IOException ex){host.getLogger().warning("Cannot save skins: "+ex.getMessage());return false;}
}
private String skinSource(String s){
if(s.matches("[A-Za-z0-9_]{1,16}"))return s;
try{URI u=URI.create(s);String h=u.getHost();if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1||h==null)return null;
if(h.equalsIgnoreCase("textures.minecraft.net")&&u.getPath().matches("/texture/[a-fA-F0-9]{32,128}"))return "https://textures.minecraft.net"+u.getPath();
if(Set.of("namemc.com","www.namemc.com").contains(h.toLowerCase(Locale.ROOT))&&u.getPath().matches("/profile/[A-Za-z0-9_]{1,16}(\\.[0-9]+)?/?"))return u.getPath().substring(9).replaceFirst("[./].*$","");
}catch(Exception ignored){}return null;
}
boolean skin(CommandSender sender,String[] args){
if(!(sender instanceof Player p)){tell(sender,"§cТолько в игре.");return true;}
if(args.length==0){tell(p,"§e/skin <ник лицензии | ссылка textures.minecraft.net | NameMC profile> [slim|classic]");tell(p,"§e/skin reset");return true;}
UUID id=p.getUniqueId();
if(args[0].equalsIgnoreCase("reset")){
pending.remove(id);Object previous=skins.get(id.toString());skins.set(id.toString(),null);if(!persist()){skins.set(id.toString(),previous);tell(p,"§cОшибка сохранения.");return true;}
PlayerProfile original=originals.remove(id);if(original!=null)p.setPlayerProfile(original);else{PlayerProfile profile=p.getPlayerProfile();profile.removeProperty("textures");p.setPlayerProfile(profile);}tell(p,"§aСкин сброшен.");return true;
}
String src=skinSource(args[0]);if(src==null){tell(p,"§cПоддерживаются ник лицензии, NameMC /profile/ник или textures.minecraft.net/texture/…");return true;}
long time=System.currentTimeMillis();if(time-cooldown.getOrDefault(id,0L)<15000){tell(p,"§cПодожди 15 секунд.");return true;}cooldown.put(id,time);
String model=args.length>1&&args[1].equalsIgnoreCase("slim")?"slim":"classic";
apply(p,src,model,true);return true;
}
private void apply(Player p,String src,String model,boolean save){
UUID id=p.getUniqueId();String token=UUID.randomUUID().toString();pending.put(id,token);
originals.putIfAbsent(id,p.getPlayerProfile().clone());
if(src.startsWith("https://")){
try{PlayerProfile profile=p.getPlayerProfile();var textures=profile.getTextures();textures.setSkin(URI.create(src).toURL(),model.equals("slim")?PlayerTextures.SkinModel.SLIM:PlayerTextures.SkinModel.CLASSIC);profile.setTextures(textures);finish(id,token,profile,src,model,save);}catch(Exception e){pending.remove(id,token);tell(p,"§cНекорректная текстура.");}
}else{
Bukkit.createProfile(src).update().orTimeout(15,java.util.concurrent.TimeUnit.SECONDS).whenComplete((profile,error)->{
if(!host.isEnabled())return;
Bukkit.getScheduler().runTask(host,()->{
Player current=Bukkit.getPlayer(id);if(current==null||!token.equals(pending.get(id)))return;
if(error!=null||profile==null||profile.getTextures().getSkin()==null){pending.remove(id,token);tell(current,"§cНе удалось получить скин Mojang.");return;}
PlayerProfile target=current.getPlayerProfile();target.removeProperty("textures");
for(var property:profile.getProperties())if(property.getName().equals("textures"))target.setProperty(property);
finish(id,token,target,src,model,save);
});
});
}
}
private void finish(UUID id,String token,PlayerProfile profile,String src,String model,boolean save){
Player p=Bukkit.getPlayer(id);if(p==null||!pending.remove(id,token))return;
if(save){String path=id.toString();Object old=skins.get(path);skins.set(path+".source",src);skins.set(path+".model",model);if(!persist()){skins.set(path,old);tell(p,"§cНе удалось сохранить скин.");return;}}
p.setPlayerProfile(profile);p.updateInventory();if(save)tell(p,"§aСкин применён без переподключения. Если клиент ещё показывает старую текстуру, дождись загрузки.");
}
@EventHandler public void join(PlayerJoinEvent e){UUID id=e.getPlayer().getUniqueId();Bukkit.getScheduler().runTaskLater(host,()->{Player p=Bukkit.getPlayer(id);String src=skins.getString(id+".source");if(p!=null&&src!=null&&skinSource(src)!=null)apply(p,src,skins.getString(id+".model","classic"),false);},60L);}
@EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();pending.remove(id);originals.remove(id);cooldown.remove(id);}
}
