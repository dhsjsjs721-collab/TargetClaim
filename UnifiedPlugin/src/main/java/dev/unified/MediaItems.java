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
import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.concurrent.*;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
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
private SkinResolver resolver;
private ExecutorService workers;
private static final long WINDOW=36_000_000L, COOLDOWN=5000L;
MediaItems(UnifiedPlugin host){this.host=host;file=new File(host.getDataFolder(),"skins.yml");}
void enable(){
skins=YamlConfiguration.loadConfiguration(file);
String key=System.getenv("MINESKIN_API_KEY");if(key==null||key.isBlank())key=host.getConfig().getString("media.mineskin-api-key","");resolver=new SkinResolver(key);
workers=new ThreadPoolExecutor(2,2,0L,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"Unified-skin");t.setDaemon(true);return t;});
if(!selftest())throw new IllegalStateException("Media self-test failed");host.getLogger().info("SELFTEST media OK");
Bukkit.getPluginManager().registerEvents(this,host);
host.getLogger().info("MediaItems enabled: sneak-right-click disc editor; vanilla jukebox audio preserved; persistent skin profiles.");
}
private void tell(CommandSender s,String m){s.sendMessage("§6Media §8» §r"+m);}
private static String youtube(String s){
try{if(s==null)return null;s=s.strip();if(!s.contains("://"))s="https://"+s;URI u=URI.create(s);if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1)return null;
String h=u.getHost(),id=null;if(h==null)return null;
h=h.toLowerCase(Locale.ROOT);
if(h.equals("youtu.be"))id=u.getPath().replaceFirst("^/","").replaceFirst("/$","");
else if(Set.of("youtube.com","www.youtube.com","m.youtube.com").contains(h)){
if(u.getPath().equals("/watch")&&u.getRawQuery()!=null)for(String q:u.getRawQuery().split("&"))if(q.startsWith("v="))id=q.substring(2);
else {}
if(u.getPath().startsWith("/shorts/"))id=u.getPath().substring(8);
if(u.getPath().startsWith("/live/"))id=u.getPath().substring(6);
if(u.getPath().startsWith("/embed/"))id=u.getPath().substring(7);
}
return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?"https://www.youtube.com/watch?v="+id:null;
}catch(Exception e){return null;}
}
private static boolean record(ItemStack i){return i!=null&&i.getType().name().startsWith("MUSIC_DISC_");}
private EquipmentSlot discHand(Player p){return record(p.getInventory().getItemInMainHand())?EquipmentSlot.HAND:record(p.getInventory().getItemInOffHand())?EquipmentSlot.OFF_HAND:null;}
private ItemStack discStack(Player p){return discHand(p)==EquipmentSlot.OFF_HAND?p.getInventory().getItemInOffHand():p.getInventory().getItemInMainHand();}
private void setDiscStack(Player p,ItemStack item){if(discHand(p)==EquipmentSlot.OFF_HAND)p.getInventory().setItemInOffHand(item);else p.getInventory().setItemInMainHand(item);}
private String savedLink(ItemStack i){return record(i)&&i.hasItemMeta()?youtube(i.getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING)):null;}
private String recordName(ItemStack i){return i.hasItemMeta()&&i.getItemMeta().hasDisplayName()?net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(i.getItemMeta().displayName()):"YouTube";}
private boolean writeDisc(Player p,String raw,String name){
if(host.seasonLocked(p))return false;
ItemStack hand=discStack(p);if(!record(hand)||hand.getAmount()!=1){tell(p,"§cДержи одну пластинку в любой руке.");return false;}
String url=youtube(raw);if(url==null){tell(p,"§cНужна HTTPS-ссылка на отдельное видео YouTube.");return false;}
name=name==null?"":name.replaceAll("[\\p{Cntrl}§]","").strip();if(name.isBlank())name="YouTube";if(name.length()>48)name=name.substring(0,48);
var meta=hand.getItemMeta();meta.getPersistentDataContainer().set(discKey,PersistentDataType.STRING,url);meta.setItemModel(new NamespacedKey("minecraftunified","youtube_disc"));meta.displayName(Component.text(name,NamedTextColor.GOLD));meta.lore(List.of(Component.text("Shift+ПКМ — редактор записи",NamedTextColor.GRAY),Component.text("Проигрыватель — музыка Minecraft и ссылка YouTube",NamedTextColor.GRAY)));hand.setItemMeta(meta);setDiscStack(p,hand);tell(p,"§aЗапись сохранена.");return true;
}
private void clearDisc(Player p){
ItemStack hand=discStack(p);if(!record(hand)||savedLink(hand)==null)return;
var meta=hand.getItemMeta();meta.getPersistentDataContainer().remove(discKey);meta.setItemModel(null);meta.displayName(null);meta.lore(null);hand.setItemMeta(meta);setDiscStack(p,hand);tell(p,"§aЗапись удалена.");
}
private boolean sameDisc(Player p,int slot,ItemStack before,EquipmentSlot hand){return p.isOnline()&&!host.seasonLocked(p)&&discHand(p)==hand&&(hand!=EquipmentSlot.HAND||p.getInventory().getHeldItemSlot()==slot)&&before.equals(discStack(p));}
private void discMenu(Player p){
if(host.seasonLocked(p))return;ItemStack item=discStack(p);if(!record(item)||item.getAmount()!=1){tell(p,"§cДержи одну пластинку в любой руке.");return;}
ItemStack before=item.clone();int slot=p.getInventory().getHeldItemSlot();EquipmentSlot hand=discHand(p);String saved=savedLink(item);
List<ActionButton> buttons=new ArrayList<>();
buttons.add(ActionButton.create(Component.text("Сохранить",NamedTextColor.GREEN),null,110,DialogAction.customClick((view,audience)->{
if(!(audience instanceof Player actor)||!actor.getUniqueId().equals(p.getUniqueId()))return;String url=view.getText("url"),name=view.getText("name");
Bukkit.getScheduler().runTask(host,()->{if(!sameDisc(actor,slot,before,hand)){tell(actor,"§cПластинка изменилась. Открой меню снова.");return;}if(!writeDisc(actor,url,name))discMenu(actor);});
},ClickCallback.Options.builder().uses(1).build())));
if(saved!=null){
buttons.add(ActionButton.create(Component.text("Открыть запись",NamedTextColor.AQUA),null,110,DialogAction.customClick((view,audience)->{if(audience instanceof Player actor&&actor.getUniqueId().equals(p.getUniqueId()))Bukkit.getScheduler().runTask(host,()->{if(sameDisc(actor,slot,before,hand))link(actor,saved,recordName(before));});},ClickCallback.Options.builder().uses(1).build())));
buttons.add(ActionButton.create(Component.text("Удалить запись",NamedTextColor.RED),null,110,DialogAction.customClick((view,audience)->{if(audience instanceof Player actor&&actor.getUniqueId().equals(p.getUniqueId()))Bukkit.getScheduler().runTask(host,()->{if(sameDisc(actor,slot,before,hand))clearDisc(actor);});},ClickCallback.Options.builder().uses(1).build())));
}
buttons.add(ActionButton.create(Component.text("Отмена"),null,110,null));
p.showDialog(Dialog.create(builder->builder.empty().base(DialogBase.builder(Component.text("Музыкальная пластинка",NamedTextColor.GOLD))
.body(List.of(DialogBody.plainMessage(Component.text("Запиши ссылку. Проигрыватель играет исходную музыку Minecraft и отправляет слушателям ссылку YouTube."),240)))
.inputs(List.of(DialogInput.text("url",Component.text("Ссылка YouTube")).width(240).maxLength(1800).initial(saved==null?"":saved).build(),DialogInput.text("name",Component.text("Название записи")).width(240).maxLength(48).initial(saved==null?"":recordName(item).substring(0,Math.min(48,recordName(item).length()))).build())).build()).type(DialogType.multiAction(buttons).columns(2).build())));
}
boolean disc(CommandSender sender,String[] args){
if(!(sender instanceof Player p)){tell(sender,"§cТолько в игре.");return true;}
if(host.seasonLocked(p)){tell(p,"§cДоступно после начала сезона.");return true;}
ItemStack hand=discStack(p);if(!record(hand)||hand.getAmount()!=1){tell(p,"§cДержи одну пластинку в любой руке.");return true;}
if(args.length==0){discMenu(p);return true;}
if(args[0].equalsIgnoreCase("clear")){clearDisc(p);return true;}
if(args[0].equalsIgnoreCase("open")){String saved=savedLink(hand);if(saved!=null)link(p,saved,recordName(hand));else discMenu(p);return true;}
writeDisc(p,args[0],args.length>1?String.join(" ",Arrays.copyOfRange(args,1,args.length)):"YouTube");return true;
}
@EventHandler(priority=EventPriority.HIGHEST)
public void playLink(PlayerInteractEvent e){
if(host.seasonLocked(e.getPlayer()))return;
if(e.getHand()==null||e.getHand()!=discHand(e.getPlayer())||!e.getPlayer().isSneaking()||!e.getAction().isRightClick()||!record(e.getItem()))return;
e.setCancelled(true);e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);discMenu(e.getPlayer());
}
private void link(Player player,String url,String name){player.sendMessage(Component.text("[YouTube] "+name,NamedTextColor.GOLD).append(Component.text(" [Открыть]",NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(url))));}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void jukebox(PlayerInteractEvent e){
if(host.seasonLocked(e.getPlayer()))return;
if(e.getHand()==null||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getPlayer().isSneaking()||e.useInteractedBlock()==Event.Result.DENY||e.getClickedBlock()==null||!(e.getClickedBlock().getState() instanceof Jukebox box)||e.getItem()==null||!e.getItem().hasItemMeta())return;
String url=e.getItem().getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING);if(url==null||youtube(url)==null||!box.getRecord().getType().isAir())return;
var block=e.getClickedBlock();Bukkit.getScheduler().runTask(host,()->{if(!(block.getState() instanceof Jukebox j))return;ItemStack disc=j.getRecord();if(!disc.hasItemMeta()||!url.equals(disc.getItemMeta().getPersistentDataContainer().get(discKey,PersistentDataType.STRING)))return;
String name=recordName(disc);Location loc=block.getLocation().add(.5,.5,.5);for(Player p:loc.getWorld().getPlayers())if(p.getLocation().distanceSquared(loc)<=32*32)link(p,url,name);
});
}
boolean video(CommandSender sender,String[] args){if(!(sender instanceof Player p)){tell(sender,"§cТолько в игре.");return true;}if(args.length!=1){tell(p,"§e/u video <ссылка YouTube> — обложка видео на карте в руке");return true;}String url=youtube(args[0]);if(url==null){tell(p,"§cНужна ссылка на видео YouTube.");return true;}return Bukkit.dispatchCommand(p,"imagemap https://i.ytimg.com/vi/"+url.substring(url.length()-11)+"/hqdefault.jpg");}

private boolean persist(){
try{File tmp=new File(file.getParentFile(),file.getName()+".tmp");skins.save(tmp);try{Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;}catch(IOException ex){host.getLogger().warning("Cannot save skins: "+ex.getMessage());return false;}
}
private static List<Long> recent(List<Long> values,long now){return values.stream().filter(x->x>now-WINDOW&&x<=now).sorted().toList();}
private List<Long> recent(UUID id,long now){return recent(skins.getLongList(id+".changes"),now);}
private void status(Player p){
long now=System.currentTimeMillis();List<Long> history=recent(p.getUniqueId(),now);tell(p,"§eДоступно смен: "+(3-Math.min(3,history.size()))+"/3 за последние 10 часов.");
if(history.size()>=3){long min=Math.max(1,(history.getFirst()+WINDOW-now+59999)/60000);tell(p,"§7Следующая смена через "+min/60+"ч "+min%60+"м.");}
}
boolean skin(CommandSender sender,String[] args){
if(!(sender instanceof Player p)){tell(sender,"§cТолько в игре.");return true;}
if(args.length==0){tell(p,"§e/skin <ник | NameMC | MineSkin | HTTPS PNG> [slim|classic]");tell(p,"§e/skin reset · /skin status");status(p);return true;}
if(args[0].equalsIgnoreCase("status")){status(p);return true;}
UUID id=p.getUniqueId();boolean reset=args[0].equalsIgnoreCase("reset");String src=reset?null:SkinResolver.source(args[0]);
if(!reset&&src==null){tell(p,"§cНужен ник, ссылка NameMC/MineSkin или прямая публичная HTTPS-ссылка на PNG скин.");return true;}
if(args.length>2||args.length==2&&!Set.of("slim","classic").contains(args[1].toLowerCase(Locale.ROOT))){tell(p,"§c/skin <источник> [slim|classic]");return true;}
if(reset&&skins.getString(id+".source")==null&&skins.getString(id+".value")==null){tell(p,"§7У тебя нет установленного скина.");return true;}
long now=System.currentTimeMillis();long last=Math.max(cooldown.getOrDefault(id,0L),skins.getLong(id+".last-change",0L));
if(now-last<COOLDOWN){tell(p,"§cПодожди "+Math.max(1,(COOLDOWN-now+last+999)/1000)+" сек.");return true;}
if(pending.containsKey(id)){tell(p,"§eПредыдущая смена ещё обрабатывается.");return true;}
if(recent(id,now).size()>=3){status(p);return true;}
cooldown.put(id,now);String model=args.length==2?args[1].toLowerCase(Locale.ROOT):"unknown";
originals.putIfAbsent(id,p.getPlayerProfile().clone());
if(reset){finish(p,null,null,"unknown",true);return true;}
apply(p,src,model,true);return true;
}
private void apply(Player p,String src,String model,boolean save){
UUID id=p.getUniqueId();String token=UUID.randomUUID().toString();pending.put(id,token);originals.putIfAbsent(id,p.getPlayerProfile().clone());
if(save)tell(p,"§7Получаю подписанный скин…");
try{workers.execute(()->{
ProfileProperty prop=null;String failure=null;
try{prop=resolver.resolve(src,model);}catch(Exception ex){failure=ex instanceof IOException?ex.getMessage():"Не удалось получить корректную подписанную текстуру.";}
ProfileProperty result=prop;String reason=failure;
if(!host.isEnabled())return;
Bukkit.getScheduler().runTask(host,()->{Player current=Bukkit.getPlayer(id);if(!pending.remove(id,token)||current==null)return;
if(result==null){if(save)tell(current,"§c"+reason);return;}
finish(current,result,src,model,save);
});
});}catch(RejectedExecutionException ex){pending.remove(id,token);tell(p,"§cОчередь скинов занята. Попробуй позже.");}
}
private void finish(Player p,ProfileProperty property,String src,String model,boolean save){
UUID id=p.getUniqueId();String path=id.toString();PlayerProfile before=p.getPlayerProfile().clone(),profile=before.clone();
profile.removeProperty("textures");
if(property!=null)profile.setProperty(property);else{PlayerProfile original=originals.get(id);if(original!=null)for(ProfileProperty pr:original.getProperties())if(pr.getName().equals("textures"))profile.setProperty(pr);}
List<Long> history=new ArrayList<>(recent(id,System.currentTimeMillis()));
if(save&&history.size()>=3){status(p);return;}
String old=skins.saveToString();
try{p.setPlayerProfile(profile);p.updateInventory();}catch(RuntimeException ex){tell(p,"§cНе удалось обновить профиль.");host.getLogger().warning("Skin profile update failed: "+ex.getClass().getSimpleName());return;}
if(save||property!=null){
skins.set(path+".source",src);skins.set(path+".model",src==null?null:model);
skins.set(path+".value",property==null?null:property.getValue());skins.set(path+".signature",property==null?null:property.getSignature());
if(save){long now=System.currentTimeMillis();history.add(now);skins.set(path+".changes",history);skins.set(path+".last-change",now);cooldown.put(id,now);}
if(!persist()){try{skins.loadFromString(old);}catch(Exception ignored){}try{p.setPlayerProfile(before);}catch(Exception ignored){}tell(p,"§cОшибка сохранения скина.");return;}
}
if(save){tell(p,src==null?"§aСкин сброшен.":"§aПодписанный скин применён без перезахода.");status(p);}
}
@EventHandler public void join(PlayerJoinEvent e){UUID id=e.getPlayer().getUniqueId();originals.put(id,e.getPlayer().getPlayerProfile().clone());Bukkit.getScheduler().runTaskLater(host,()->{
Player p=Bukkit.getPlayer(id);if(p==null||pending.containsKey(id)||cooldown.containsKey(id))return;String src=skins.getString(id+".source"),value=skins.getString(id+".value"),sig=skins.getString(id+".signature");if(src==null)return;
if(value!=null&&sig!=null&&!sig.isBlank()){PlayerProfile profile=p.getPlayerProfile();profile.removeProperty("textures");profile.setProperty(new ProfileProperty("textures",value,sig));try{p.setPlayerProfile(profile);}catch(RuntimeException ex){host.getLogger().warning("Cached skin update failed: "+ex.getClass().getSimpleName());}return;}
String normalized=SkinResolver.source(src);if(src.startsWith("mineskin:"))normalized=src;if(normalized!=null)apply(p,normalized,skins.getString(id+".model","unknown"),false);
},40L);}
@EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();pending.remove(id);originals.remove(id);cooldown.remove(id);}
void disable(){if(workers!=null)workers.shutdownNow();pending.clear();}
static boolean selftest(){
long time=WINDOW*2;return recent(List.of(time-WINDOW,time-1000,time-1),time).size()==2
&&"Notch".equals(SkinResolver.source("https://namemc.com/profile/Notch.1"))
&&"https://texture.namemc.com/12/34/1234567890abcdef.png".equals(SkinResolver.source("https://namemc.com/skin/1234567890abcdef"))
&&SkinResolver.source("https://127.0.0.1/skin.png")==null
&&"https://www.youtube.com/watch?v=dQw4w9WgXcQ".equals(youtube("https://youtu.be/dQw4w9WgXcQ?t=10"))
&&youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ&x=1")!=null&&youtube("https://evil.example/watch?v=dQw4w9WgXcQ")==null;
}
}
