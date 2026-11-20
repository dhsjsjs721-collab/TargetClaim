package dev.imagemaps;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBody;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ImageMapsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
private static final int EDGE = 128;
private static final int MAX_BYTES = 8 * 1024 * 1024;
private static final long MAX_PIXELS = 12_000_000L;
private static final int MAX_SIDE = 4096;
private static final int MAX_MAPS = 512;
private static final long COOLDOWN_MS = 15000L;
private static final String PREFIX = "§8[§bImageMaps§8] §7";
private record CropSettings(boolean fill,double zoom,double shiftX,double shiftY){
static CropSettings defaults(){return new CropSettings(false,1,0,0);}
}

private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();
private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
private final Map<UUID,Long> lastSuccess = new HashMap<>();
private final Map<Integer,BufferedImage> storedImages = new HashMap<>();
private ExecutorService executor;
private HttpClient http;
private Path imagesFolder;
private Path uploadsFolder;
private Path indexPath;

@Override public void onEnable(){
imagesFolder=getDataFolder().toPath().resolve("maps");
uploadsFolder=getDataFolder().toPath().resolve("uploads");
indexPath=getDataFolder().toPath().resolve("data.yml");
try{Files.createDirectories(imagesFolder);Files.createDirectories(uploadsFolder);}
catch(IOException ex){getLogger().severe("Cannot create image directories: "+ex.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
executor=Executors.newFixedThreadPool(2,r->{Thread thread=new Thread(r,"ImageMaps-loader");thread.setDaemon(true);return thread;});
http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(7)).build();
ImageIO.setUseCache(false);
Bukkit.getPluginManager().registerEvents(this,this);
if(getCommand("imagemap")!=null){getCommand("imagemap").setExecutor(this);getCommand("imagemap").setTabCompleter(this);}
restoreImages();
getLogger().info("ImageMaps 1.1.0 loaded "+storedImages.size()+" persistent image maps");
}
@Override public void onDisable(){
if(executor!=null)executor.shutdownNow();
awaiting.clear();loading.clear();storedImages.clear();
}
private void tell(CommandSender sender,String text){sender.sendMessage(PREFIX+text);}
private boolean hasMap(Player player){
Material type=player.getInventory().getItemInMainHand().getType();
return type==Material.MAP||type==Material.FILLED_MAP;
}
private void openEditor(Player player){
if(!player.hasPermission("imagemaps.use")){tell(player,"§cНет разрешения.");return;}
if(!hasMap(player)){tell(player,"§cДержи пустую или заполненную карту в основной руке.");return;}
if(loading.contains(player.getUniqueId())){tell(player,"§eКартинка уже загружается.");return;}
Dialog dialog=Dialog.create(builder->builder.empty()
.base(DialogBase.builder(Component.text("Редактор изображения на карте"))
.body(List.of(DialogBody.plainMessage(Component.text("Вставь прямую HTTPS-ссылку. Настрой обрезку, приближение и сдвиг; результат увидят все игроки."))))
.inputs(List.of(
DialogInput.text("url",Component.text("Ссылка на PNG / JPG / GIF")).width(350).maxLength(1800).initial("").build(),
DialogInput.bool("fill",Component.text("Заполнить всю карту (обрезать края)"),false,"true","false"),
DialogInput.numberRange("zoom",Component.text("Приближение"),1.0f,3.0f).initial(1.0f).step(.1f).width(350).build(),
DialogInput.numberRange("x",Component.text("Сдвиг по горизонтали"),-100f,100f).initial(0f).step(5f).width(350).build(),
DialogInput.numberRange("y",Component.text("Сдвиг по вертикали"),-100f,100f).initial(0f).step(5f).width(350).build()
)).build())
.type(DialogType.confirmation(
ActionButton.create(Component.text("Создать карту"),Component.text("Загрузить и применить выбранные настройки"),150,
DialogAction.customClick((view,audience)->{
if(!(audience instanceof Player p))return;
String url=view.getText("url");Boolean fill=view.getBoolean("fill");
Float zoom=view.getFloat("zoom"),x=view.getFloat("x"),y=view.getFloat("y");
if(url==null||zoom==null||x==null||y==null||fill==null)return;
CropSettings options=new CropSettings(fill,Math.max(1,Math.min(3,zoom.doubleValue())),Math.max(-100,Math.min(100,x.doubleValue())),Math.max(-100,Math.min(100,y.doubleValue())));
Bukkit.getScheduler().runTask(this,()->importImage(p,url.trim(),false,options));
},ClickCallback.Options.builder().uses(1).build())),
ActionButton.create(Component.text("Отмена"),Component.text("Ничего не менять"),100,null)
)));
player.showDialog(dialog);
}
private void prompt(Player player){
if(!player.hasPermission("imagemaps.use")){tell(player,"§cНет разрешения.");return;}
if(!hasMap(player)){tell(player,"§cВозьми пустую или заполненную карту в основную руку.");return;}
if(loading.contains(player.getUniqueId())){tell(player,"§eИзображение уже загружается.");return;}
awaiting.add(player.getUniqueId());
tell(player,"§bОтправь прямую HTTPS-ссылку на картинку в чат. Ссылка не будет показана другим игрокам.");
tell(player,"§7Отмена: §f/imagemap cancel §7| PNG/JPG/GIF, первая картинка, максимум 8 МБ.");
}
@EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
public void sneakClickMap(PlayerInteractEvent event){
if(event.getHand()!=EquipmentSlot.HAND||!event.getPlayer().isSneaking())return;
if(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
if(!hasMap(event.getPlayer()))return;
if(!event.getPlayer().hasPermission("imagemaps.use"))return;
event.setCancelled(true);
openEditor(event.getPlayer());
}
@EventHandler(priority=EventPriority.HIGHEST)
public void hiddenChat(AsyncChatEvent event){
UUID playerId=event.getPlayer().getUniqueId();
if(!awaiting.remove(playerId))return;
event.setCancelled(true);
String input=PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
Bukkit.getScheduler().runTask(this,()->{
Player player=Bukkit.getPlayer(playerId);
if(player==null)return;
if(input.equalsIgnoreCase("cancel")||input.equalsIgnoreCase("отмена")){tell(player,"§7Ввод отменён.");return;}
importImage(player,input,false);
});
}
@EventHandler public void quit(PlayerQuitEvent event){awaiting.remove(event.getPlayer().getUniqueId());}

@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(!(sender instanceof Player player)){tell(sender,"§cНужен игрок с картой в руке.");return true;}
if(!sender.hasPermission("imagemaps.use")){tell(sender,"§cНет разрешения.");return true;}
if(args.length==0){openEditor(player);return true;}
String option=args[0].toLowerCase(Locale.ROOT);
if(option.equals("chat")){prompt(player);return true;}
if(option.equals("cancel")){awaiting.remove(player.getUniqueId());tell(player,"§7Ввод отменён.");return true;}
if(option.equals("help")){tell(player,"§f/imagemap §7— окно настройки карты");
tell(player,"§f/imagemap chat §7— ввод ссылки через чат");
tell(player,"§f/imagemap <https://...> §7— загрузить напрямую");
tell(player,"§f/imagemap file <filename.png> §7— файл сервера (OP)");return true;}
if(option.equals("file")){
if(!sender.hasPermission("imagemaps.file")){tell(sender,"§cТолько администраторы могут брать файлы сервера.");return true;}
if(args.length!=2){tell(player,"§e/imagemap file имя.png");return true;}
importImage(player,args[1],true);
return true;
}
if(args.length!=1){tell(player,"§cВставь одну прямую ссылку либо введи /imagemap.");return true;}
importImage(player,args[0],false);
return true;
}
@Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
if(args.length==1)return sender.hasPermission("imagemaps.file")?List.of("help","cancel","file","chat"):List.of("help","cancel","chat");
return List.of();
}

private void importImage(Player player,String source,boolean local){importImage(player,source,local,CropSettings.defaults());}
private void importImage(Player player,String source,boolean local,CropSettings options){
UUID id=player.getUniqueId();
awaiting.remove(id);
if(!hasMap(player)){tell(player,"§cСначала возьми карту в основную руку.");return;}
if(storedImages.size()>=MAX_MAPS){tell(player,"§cНа сервере достигнут лимит карт: "+MAX_MAPS);return;}
if(loading.contains(id)){tell(player,"§eУже загружается другая картинка.");return;}
long elapsed=System.currentTimeMillis()-lastSuccess.getOrDefault(id,0L);
if(elapsed<COOLDOWN_MS){tell(player,"§eПодожди "+((COOLDOWN_MS-elapsed+999)/1000)+" сек. перед следующей картой.");return;}
if(source.length()<1||source.length()>1800){tell(player,"§cНеверная длина ссылки.");return;}
if(!loading.add(id))return;
tell(player,"§7Загружаю изображение. Не убирай карту из основной руки.");
executor.execute(()->{
try{
byte[] bytes=local?readServerFile(source):download(source);
BufferedImage resized=decodeAndScale(bytes,options);
Bukkit.getScheduler().runTask(this,()->{
loading.remove(id);
Player online=Bukkit.getPlayer(id);
if(online==null||!isEnabled())return;
if(!hasMap(online)){tell(online,"§cКарта не в руке. Изображение не установлено.");return;}
if(storedImages.size()>=MAX_MAPS){tell(online,"§cЛимит карт достигнут.");return;}
try{createMap(online,resized);}
catch(Exception e){getLogger().warning("Map creation failed: "+e.getMessage());tell(online,"§cНе удалось создать карту: "+safeMessage(e));}
});
}catch(Exception error){
getLogger().info("Import rejected: "+error.getMessage());
if(isEnabled())Bukkit.getScheduler().runTask(this,()->{
loading.remove(id);
Player online=Bukkit.getPlayer(id);
if(online!=null)tell(online,"§cИзображение отклонено: "+safeMessage(error));
});
}
});
}
private String safeMessage(Exception ex){
String msg=ex.getMessage();
return msg==null?"неизвестная ошибка":msg.length()>110?msg.substring(0,110):msg;
}

private byte[] readServerFile(String fileName)throws IOException{
if(!fileName.matches("[A-Za-z0-9._-]{1,90}")||fileName.equals(".")||fileName.equals(".."))throw new IOException("Неверное имя файла");
Path file=uploadsFolder.resolve(fileName).normalize();
if(!file.getParent().equals(uploadsFolder)||!Files.isRegularFile(file)||Files.isSymbolicLink(file))throw new IOException("Файл не найден в папке uploads");
if(Files.size(file)>MAX_BYTES)throw new IOException("Размер превышает 8 МБ");
return Files.readAllBytes(file);
}
private byte[] download(String address)throws Exception{
URI uri;
try{uri=URI.create(address);}
catch(IllegalArgumentException ex){throw new IOException("Нужна корректная HTTPS-ссылка");}
if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getRawFragment()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IOException("Разрешены только прямые HTTPS-ссылки без логина и нестандартного порта");
String host=uri.getHost();
for(InetAddress resolved:InetAddress.getAllByName(host))if(!isPublic(resolved))throw new IOException("Локальные и внутренние адреса запрещены");
HttpRequest request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("User-Agent","ImageMaps/1.0 Minecraft").header("Accept","image/png,image/jpeg,image/gif,image/*;q=0.8").GET().build();
HttpResponse<InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
try(InputStream body=response.body()){
if(response.statusCode()!=200)throw new IOException("HTTP "+response.statusCode()+" (нужна прямая ссылка на файл)");
long announced=response.headers().firstValueAsLong("Content-Length").orElse(0);
if(announced>MAX_BYTES)throw new IOException("Размер изображения больше 8 МБ");
return readBounded(body);
}
}
private boolean isPublic(InetAddress a){
if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
byte[] ip=a.getAddress();
if(a instanceof Inet4Address){
int p=ip[0]&255,q=ip[1]&255,r=ip[2]&255;
return p>=1&&p<224&&p!=10&&p!=127
&&!(p==100&&q>=64&&q<=127)&&!(p==169&&q==254)
&&!(p==172&&q>=16&&q<=31)&&!(p==192&&(q==168||q==0))
&&!(p==198&&(q==18||q==19||(q==51&&r==100)))
&&!(p==203&&q==0&&r==113);
}
if(a instanceof Inet6Address){
int top=ip[0]&255;
return (top&0xe0)==0x20&&!(top==0x20&&(ip[1]&255)==0x02);
}
return false;
}
private byte[] readBounded(InputStream in)throws IOException{
ByteArrayOutputStream out=new ByteArrayOutputStream();
byte[] buffer=new byte[16384];int n,total=0;
while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_BYTES)throw new IOException("Размер больше 8 МБ");out.write(buffer,0,n);}
if(total==0)throw new IOException("Пустой файл");
return out.toByteArray();
}
private BufferedImage decodeAndScale(byte[] bytes,CropSettings settings)throws IOException{
try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
if(stream==null)throw new IOException("Файл невозможно прочитать");
Iterator<ImageReader> formats=ImageIO.getImageReaders(stream);
if(!formats.hasNext())throw new IOException("Поддерживаются PNG, JPEG и GIF (зависит от ImageIO)");
ImageReader reader=formats.next();
try{
reader.setInput(stream,true,true);
int width=reader.getWidth(0),height=reader.getHeight(0);
if(width<=0||height<=0||width>MAX_SIDE||height>MAX_SIDE||(long)width*height>MAX_PIXELS)throw new IOException("Слишком большое изображение (макс. 4096 px и 12 млн пикселей)");
BufferedImage original=reader.read(0);
BufferedImage scaled=new BufferedImage(EDGE,EDGE,BufferedImage.TYPE_INT_RGB);
Graphics2D g=scaled.createGraphics();
try{
g.setColor(Color.WHITE);g.fillRect(0,0,EDGE,EDGE);
g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
double ratio=settings.fill()?Math.max((double)EDGE/width,(double)EDGE/height):Math.min((double)EDGE/width,(double)EDGE/height);
ratio*=settings.zoom();
int drawWidth=Math.max(1,(int)Math.round(width*ratio)),drawHeight=Math.max(1,(int)Math.round(height*ratio));
// 0 means centred; +/-100 shifts the image to either extreme without leaving empty
// borders in crop mode, or within the available padding in fit mode.
int dx=(EDGE-drawWidth)/2,dy=(EDGE-drawHeight)/2;
dx+=(int)Math.round(settings.shiftX()/100.0*Math.abs(EDGE-drawWidth)/2.0);
dy+=(int)Math.round(settings.shiftY()/100.0*Math.abs(EDGE-drawHeight)/2.0);
g.drawImage(original,dx,dy,drawWidth,drawHeight,null);
}finally{g.dispose();}
return scaled;
}finally{reader.dispose();}
}
}
private void createMap(Player player,BufferedImage picture)throws IOException{
MapView map=Bukkit.createMap(player.getWorld());
int id=map.getId();
Path path=imagesFolder.resolve(id+".png");
Path tmp=imagesFolder.resolve(id+".png.tmp");
if(!ImageIO.write(picture,"png",tmp.toFile()))throw new IOException("Не удалось записать PNG");
try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
catch(IOException unsupported){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
YamlConfiguration index=YamlConfiguration.loadConfiguration(indexPath.toFile());
index.set("images."+id+".owner",player.getUniqueId().toString());
index.set("images."+id+".created",System.currentTimeMillis());
try{index.save(indexPath.toFile());}catch(IOException ex){Files.deleteIfExists(path);throw ex;}
installRenderer(map,picture);
storedImages.put(id,picture);
ItemStack result=new ItemStack(Material.FILLED_MAP,1);
MapMeta meta=(MapMeta)result.getItemMeta();
meta.setMapView(map);
meta.setDisplayName("§b§lКарта с изображением §7#"+id);
meta.setLore(List.of("§7Можно поставить в рамку","§8Изображение видно всем игрокам"));
result.setItemMeta(meta);
ItemStack held=player.getInventory().getItemInMainHand();
if(held.getAmount()==1)player.getInventory().setItemInMainHand(result);
else{
held.setAmount(held.getAmount()-1);
for(ItemStack leftover:player.getInventory().addItem(result).values())player.getWorld().dropItemNaturally(player.getLocation(),leftover);
}
lastSuccess.put(player.getUniqueId(),System.currentTimeMillis());
tell(player,"§aГотово! Карта #"+id+" создана. §7Поставь её в рамку.");
}
private void restoreImages(){
YamlConfiguration index=YamlConfiguration.loadConfiguration(indexPath.toFile());
ConfigurationSection entries=index.getConfigurationSection("images");
if(entries==null)return;
for(String textId:entries.getKeys(false)){
try{
int id=Integer.parseInt(textId);
MapView view=Bukkit.getMap(id);
if(view==null){getLogger().warning("Map "+id+" not loaded yet; its renderer cannot be restored");continue;}
Path path=imagesFolder.resolve(id+".png");
if(!Files.isRegularFile(path))continue;
BufferedImage image=ImageIO.read(path.toFile());
if(image==null||image.getWidth()!=EDGE||image.getHeight()!=EDGE)continue;
installRenderer(view,image);
storedImages.put(id,image);
}catch(Exception ex){getLogger().warning("Map "+textId+" restore error: "+ex.getMessage());}
}
}
private void installRenderer(MapView view,BufferedImage image){
for(MapRenderer renderer:new ArrayList<>(view.getRenderers()))view.removeRenderer(renderer);
view.addRenderer(new PictureRenderer(image));
view.setTrackingPosition(false);
}
private static final class PictureRenderer extends MapRenderer {
private final BufferedImage image;
private boolean rendered;
PictureRenderer(BufferedImage image){super(false);this.image=image;}
@Override public void render(MapView view,MapCanvas canvas,Player player){
if(rendered)return;
canvas.drawImage(0,0,image);
rendered=true;
}
}
}
