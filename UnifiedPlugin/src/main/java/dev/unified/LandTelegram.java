package dev.unified;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

final class LandTelegram implements Listener {
private record Link(UUID player,String name){}
private record Code(UUID player,String name,long expiry){}
private record Message(long chat,String text){}
private final UnifiedPlugin host;
private final File file;
private final Map<Long,Link> links=new HashMap<>();
private final Map<String,Code> codes=new HashMap<>();
private final Map<String,Long> raidCooldown=new HashMap<>();
private final Map<Long,Long> rate=new LinkedHashMap<>();
private final BlockingQueue<Message> outgoing=new ArrayBlockingQueue<>(256);
private final SecureRandom rng=new SecureRandom();
private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
private volatile boolean running;
private volatile String username="";
private Thread worker;
private String token;
private long owner,offset;
private static final String HELP="Land of Losers\n/start — привязка\n/link КОД — регистрация\n/me — профиль\n/clan — участники клана\n/claim — свои приваты\n/server — статус сервера\n/recipes — крафты\n/unlink — отвязать аккаунт\nОбычное сообщение отправляется в клановый чат.";
LandTelegram(UnifiedPlugin host){this.host=host;file=new File(host.getDataFolder(),"telegram-links.yml");}
private String env(String key){
String value=System.getenv(key);
if(value!=null&&!value.isBlank())return value.strip();
try{Path p=Path.of(".env");if(!Files.isRegularFile(p)||Files.size(p)>65536)return "";for(String line:Files.readAllLines(p)){String s=line.strip();if(s.startsWith(key+"=")){String v=s.substring(key.length()+1).strip();if(v.length()>=2&&(v.startsWith("\"")&&v.endsWith("\"")||v.startsWith("'")&&v.endsWith("'")))v=v.substring(1,v.length()-1);return v;}}}catch(IOException ignored){}
return "";
}
void enable(){
token=env("LAND_TELEGRAM_TOKEN");
if(!token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,100}")){host.getLogger().info("Telegram disabled: token not configured.");return;}
try{owner=Long.parseLong(env("LAND_TELEGRAM_ADMIN_ID"));if(owner<=0)owner=0;}catch(NumberFormatException ignored){owner=0;}
try{if(file.isFile()){YamlConfiguration y=new YamlConfiguration();y.load(file);offset=y.getLong("offset",0);var section=y.getConfigurationSection("links");if(section!=null)for(String id:section.getKeys(false)){long chat=Long.parseLong(id);if(chat<=0)throw new IllegalArgumentException("Invalid chat ID");String p="links."+id+".";links.put(chat,new Link(UUID.fromString(y.getString(p+"uuid")),y.getString(p+"name","?")));}}}
catch(Exception e){host.getLogger().severe("Telegram link registry invalid; bot disabled to preserve accounts.");return;}
running=true;Bukkit.getPluginManager().registerEvents(this,host);
worker=new Thread(this::loop,"LandTelegram");worker.setDaemon(true);worker.start();
Bukkit.getScheduler().runTaskTimer(host,this::save,1200L,1200L);
host.getLogger().info("Telegram worker started.");
}
void disable(){running=false;if(worker!=null)worker.interrupt();if(token!=null&&!token.isBlank())save();}
private boolean save(){
try{YamlConfiguration y=new YamlConfiguration();y.set("offset",offset);for(var e:links.entrySet()){String p="links."+e.getKey()+".";y.set(p+"uuid",e.getValue().player().toString());y.set(p+"name",e.getValue().name());}
Path tmp=file.toPath().resolveSibling(file.getName()+".tmp");y.save(tmp.toFile());try{Files.move(tmp,file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file.toPath(),StandardCopyOption.REPLACE_EXISTING);}return true;
}catch(IOException ex){host.getLogger().warning("Telegram link registry write failed.");return false;}
}
private boolean verified(Player p){
var auth=Bukkit.getPluginManager().getPlugin("UnifiedAuth");
if(!(auth instanceof JavaPlugin java)||!auth.isEnabled()||!java.getConfig().getBoolean("authentication.enabled",true))return false;
try{return Boolean.TRUE.equals(auth.getClass().getMethod("isPasswordAuthenticated",UUID.class).invoke(auth,p.getUniqueId()));}catch(ReflectiveOperationException ex){return false;}
}
boolean command(CommandSender sender,String[] args){
if(!(sender instanceof Player p)){sender.sendMessage("Telegram: "+(running?"worker started":"disabled"));return true;}
if(!running){p.sendMessage("§cTelegram пока недоступен.");return true;}
if(!verified(p)){p.sendMessage("§cДля привязки нужен полный вход через UnifiedAuth (пароль и PIN).");return true;}
UUID id=p.getUniqueId();
if(args.length>0&&args[0].equalsIgnoreCase("unlink")){
Map<Long,Link> removed=new HashMap<>();links.entrySet().removeIf(e->{if(e.getValue().player().equals(id)){removed.put(e.getKey(),e.getValue());return true;}return false;});
if(!save()){links.putAll(removed);p.sendMessage("§cОшибка сохранения.");return true;}
removed.keySet().forEach(c->send(c,"Minecraft-аккаунт отвязан."));codes.entrySet().removeIf(e->e.getValue().player().equals(id));p.sendMessage("§aTelegram отвязан.");return true;
}
if(links.values().stream().anyMatch(l->l.player().equals(id))){p.sendMessage("§aTelegram уже привязан. /u telegram unlink — отвязать.");return true;}
long now=System.currentTimeMillis();codes.entrySet().removeIf(e->e.getValue().expiry()<now||e.getValue().player().equals(id));
if(codes.size()>=256){p.sendMessage("§cСлишком много запросов.");return true;}
byte[] bytes=new byte[16];rng.nextBytes(bytes);String code=HexFormat.of().formatHex(bytes);codes.put(code,new Code(id,p.getName(),now+300000));
p.sendMessage("§dLand of Losers §7• Отправь боту /link "+code+" §7(5 минут). Никому не передавай код.");
if(username.matches("[A-Za-z0-9_]{5,32}")){
String url="https://t.me/"+username+"?start="+code;
p.sendMessage(net.kyori.adventure.text.Component.text("Открыть Telegram и привязать аккаунт",net.kyori.adventure.text.format.NamedTextColor.AQUA).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url)));
}
return true;
}
private void send(long chat,String text){if(running&&chat>0)outgoing.offer(new Message(chat,text.length()>3500?text.substring(0,3500):text));}
private JsonObject api(String method,JsonObject body)throws Exception{
HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot"+token+"/"+method)).timeout(Duration.ofSeconds(18)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
if(response.statusCode()!=200)throw new IOException("Telegram HTTP status "+response.statusCode());
JsonObject result=JsonParser.parseString(response.body()).getAsJsonObject();if(!result.get("ok").getAsBoolean())throw new IOException("Telegram API error");return result;
}
private void loop(){
long cursor=offset;boolean connected=false;int delay=1000;
while(running&&!Thread.currentThread().isInterrupted()){
try{
if(!connected){username=api("getMe",new JsonObject()).getAsJsonObject("result").get("username").getAsString();connected=true;host.getLogger().info("Telegram API connected: @"+username);}
for(int n=0;n<16;n++){Message msg=outgoing.poll();if(msg==null)break;JsonObject b=new JsonObject();b.addProperty("chat_id",msg.chat());b.addProperty("text",msg.text());try{api("sendMessage",b);}catch(InterruptedException ex){throw ex;}catch(Exception ignored){}}
JsonObject q=new JsonObject();q.addProperty("offset",cursor);q.addProperty("timeout",outgoing.isEmpty()?5:0);q.addProperty("limit",25);JsonArray allowed=new JsonArray();allowed.add("message");q.add("allowed_updates",allowed);
JsonArray updates=api("getUpdates",q).getAsJsonArray("result");
for(JsonElement element:updates){JsonObject update=element.getAsJsonObject();long next=update.get("update_id").getAsLong()+1;cursor=Math.max(cursor,next);
JsonObject m=update.has("message")?update.getAsJsonObject("message"):null;
if(m==null||!m.has("from")||!m.has("chat")||!m.has("text"))continue;
JsonObject chat=m.getAsJsonObject("chat"),from=m.getAsJsonObject("from");long uid=from.get("id").getAsLong();
if(uid<=0||uid!=chat.get("id").getAsLong()||!"private".equals(chat.get("type").getAsString())||from.has("is_bot")&&from.get("is_bot").getAsBoolean())continue;
String value=m.get("text").getAsString();if(value.length()>512)continue;long date=m.get("date").getAsLong();if(Math.abs(System.currentTimeMillis()/1000-date)>120)continue;
Bukkit.getScheduler().runTask(host,()->{if(running){offset=Math.max(offset,next);handle(uid,value);}});
}
delay=1000;
}catch(InterruptedException stop){Thread.currentThread().interrupt();break;}catch(Exception ex){if(connected){host.getLogger().warning("Telegram connection interrupted; retrying.");connected=false;}try{Thread.sleep(delay);}catch(InterruptedException stop){Thread.currentThread().interrupt();break;}delay=Math.min(30000,delay*2);}
}
}
private UUID clan(UUID player){var c=host.claimModule();return c==null?null:c.clanId(player);}
private void fan(UUID player,String message,long except,boolean self){
UUID group=clan(player);
for(var e:links.entrySet()){
if(e.getKey()==except)continue;UUID other=e.getValue().player();
if((self&&other.equals(player))||(group!=null&&!other.equals(player)&&group.equals(clan(other))))send(e.getKey(),message);
}
}
void clanMessage(UUID player,String name,String text){fan(player,"[КЛАН] "+name+": "+text,0,false);}
private void handle(long chat,String text){
long now=System.currentTimeMillis();Long last=rate.put(chat,now);if(rate.size()>1024)rate.remove(rate.keySet().iterator().next());if(last!=null&&now-last<850)return;
String clean=text.strip();if(clean.isEmpty())return;
String[] args=clean.split("\\s+",2);String cmd=args[0].split("@",2)[0].toLowerCase(Locale.ROOT),arg=args.length>1?args[1].strip():"";
if(cmd.equals("/start")&&!arg.isEmpty())cmd="/link";
Link l=links.get(chat);boolean admin=owner>0&&chat==owner;
switch(cmd){
case "/start","/help" -> send(chat,HELP+"\nВ Minecraft: /u telegram");
case "/link" -> {
if(l!=null){send(chat,"Аккаунт уже привязан.");return;}
Code c=codes.get(arg);if(c==null||c.expiry()<now){send(chat,"Код недействителен или истёк.");return;}
Player p=Bukkit.getPlayer(c.player());if(p==null||!verified(p)){send(chat,"Войди в игру с паролем и PIN и создай новый код.");return;}
if(links.values().stream().anyMatch(v->v.player().equals(c.player()))){send(chat,"Minecraft-аккаунт уже привязан.");return;}
links.put(chat,new Link(c.player(),c.name()));if(!save()){links.remove(chat);send(chat,"Ошибка сохранения.");return;}
codes.remove(arg);send(chat,"Аккаунт "+c.name()+" привязан. Теперь обычные сообщения идут в клан.");p.sendMessage("§aTelegram привязан.");
}
case "/unlink" -> {if(l==null){send(chat,"Аккаунт не привязан.");return;}links.remove(chat);if(!save()){links.put(chat,l);send(chat,"Ошибка сохранения.");return;}send(chat,"Аккаунт отвязан.");}
case "/server" -> send(chat,"Land of Losers\nОнлайн: "+Bukkit.getOnlinePlayers().size()+"/"+Bukkit.getMaxPlayers()+"\nСложность: "+Bukkit.getWorlds().getFirst().getDifficulty()+"\nНезер: 29.11.2026 20:00 Киев (план)");
case "/me" -> {if(l==null){send(chat,"Сначала /link КОД.");return;}Player p=Bukkit.getPlayer(l.player());send(chat,"Аккаунт: "+l.name()+"\nСтатус: "+(p==null?"оффлайн":"онлайн")+(p==null?"":"\nЗдоровье: "+Math.round(p.getHealth())+"\nУровень опыта: "+p.getLevel()));}
case "/claim" -> {if(l==null){send(chat,"Сначала привяжи аккаунт.");return;}var module=host.claimModule();send(chat,module==null?"Модуль приватов недоступен.":module.telegramClaims(l.player()));}
case "/clan" -> {if(l==null){send(chat,"Сначала привяжи аккаунт.");return;}var module=host.claimModule();send(chat,module==null?"Модуль кланов недоступен.":module.telegramClan(l.player()));}
case "/recipes" -> send(chat,"Обычный тотем: 8 золотых блоков + изумруд.\nВсе рецепты: /u recipes в Minecraft.");
case "/players" -> {if(admin)send(chat,"Онлайн: "+String.join(", ",Bukkit.getOnlinePlayers().stream().map(Player::getName).toList()));else send(chat,"Нет доступа.");}
default -> {
if(clean.startsWith("/")){send(chat,HELP);return;}
if(l==null){send(chat,"Сначала привяжи аккаунт.");return;}
UUID group=clan(l.player());if(group==null){send(chat,"Ты не состоишь в клане.");return;}
String msg=clean.replaceAll("[\\p{Cc}§]"," ").strip();if(msg.isEmpty())return;if(msg.length()>256)msg=msg.substring(0,256);
String line="[TG] [КЛАН] "+l.name()+": "+msg;
for(Player p:Bukkit.getOnlinePlayers())if(group.equals(clan(p.getUniqueId())))p.sendMessage(net.kyori.adventure.text.Component.text(line,net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
fan(l.player(),line,chat,false);
}
}
}
void raid(UUID claimOwner,String claimLabel,UUID actor,String action,Location l){
if(!running||claimOwner.equals(actor)||actor!=null&&host.areClanmates(claimOwner,actor))return;
long now=System.currentTimeMillis();String key=claimOwner+"|"+action;if(raidCooldown.getOrDefault(key,0L)>now)return;
if(raidCooldown.size()>2048)raidCooldown.entrySet().removeIf(e->e.getValue()<now);if(raidCooldown.size()>2048)return;
raidCooldown.put(key,now+30000);Player p=actor==null?null:Bukkit.getPlayer(actor);
String message="⚠ РЕЙД / попытка рейда\nПриват: "+claimLabel+"\nДействие: "+action+"\nИгрок: "+(p==null?"не установлен":p.getName())+"\n"+l.getWorld().getName()+" "+l.getBlockX()+" "+l.getBlockY()+" "+l.getBlockZ();
fan(claimOwner,message,0,true);
}
@EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){fan(e.getPlayer().getUniqueId(),"Вошёл: "+e.getPlayer().getName(),0,false);}
@EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){fan(e.getPlayer().getUniqueId(),"Вышел: "+e.getPlayer().getName(),0,false);codes.entrySet().removeIf(c->c.getValue().player().equals(e.getPlayer().getUniqueId()));}
@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void death(PlayerDeathEvent e){fan(e.getEntity().getUniqueId(),"Погиб: "+e.getEntity().getName()+(e.getEntity().getKiller()==null?"":" · убийца: "+e.getEntity().getKiller().getName()),0,true);}
@EventHandler(priority=EventPriority.MONITOR) public void respawn(PlayerRespawnEvent e){fan(e.getPlayer().getUniqueId(),"Возродился: "+e.getPlayer().getName(),0,false);}
}
