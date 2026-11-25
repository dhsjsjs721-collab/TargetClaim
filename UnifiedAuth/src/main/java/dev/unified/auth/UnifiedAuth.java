package dev.unified.auth;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.sound.Sound;
import java.time.Duration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnifiedAuth extends JavaPlugin implements Listener {
private enum Stage { LEGACY_PIN, CREATE_PIN, LOGIN_PIN }
private static final class Session {
final UUID uuid;
final String name;
final PlayerConfigurationConnection connection;
final String ip;
final CompletableFuture<Boolean> result=new CompletableFuture<>();
final StringBuilder digits=new StringBuilder(12);
final AtomicBoolean working=new AtomicBoolean();
Stage stage;
boolean trusted;
boolean knownNetwork;
boolean rememberSubmitted;
int failures;
long lastAction;
boolean remember;
Session(UUID uuid,String name,PlayerConfigurationConnection connection,String ip,Stage stage){
this.uuid=uuid;this.name=name;this.connection=connection;this.ip=ip;this.stage=stage;
}
void erase(){
digits.setLength(0);
}
}
private static final class Window {
long opened;
int count;
Window(long now){opened=now;}
}
private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
private final Map<String,Window> ips=new ConcurrentHashMap<>();
private final Map<String,Window> failuresByAccountIp=new ConcurrentHashMap<>();
private final java.util.Set<UUID> passwordAuthenticated=ConcurrentHashMap.newKeySet();
private final Map<UUID,Boolean> verifiedJoins=new ConcurrentHashMap<>();
private ThreadPoolExecutor executor;
private AccountStore legacy;
private DualCredentialStore accounts;
private RememberedIpStore trust;
private volatile boolean ready;
private boolean rememberEnabled,rememberPasswordSkip,antiBot;
private int maxAttempts,maxPending,timeoutSeconds,ipLimit,maxTracked,legacyPinLength,rememberDays;
private long successes,registered,migrations,denied,rateDenied,trustedLogins;
private int cleanupTask=-1;

@Override public void onEnable(){
saveDefaultConfig();
for(String other:List.of("nLogin","OpeNLogin","AuthMe","AuthMeReloaded")){
if(Bukkit.getPluginManager().getPlugin(other)!=null){
getLogger().severe("Another auth plugin is installed: "+other+". Do not run both.");
Bukkit.getPluginManager().disablePlugin(this);
return;
}
}
if(!getConfig().getBoolean("authentication.enabled",true)){
getLogger().severe("Authentication disabled in config; refusing to enable.");
Bukkit.getPluginManager().disablePlugin(this);
return;
}
maxAttempts=clamp(getConfig().getInt("authentication.maximum-attempts-per-connection",3),1,5);
maxPending=clamp(getConfig().getInt("authentication.maximum-pending-connections",50),5,200);
timeoutSeconds=clamp(getConfig().getInt("authentication.timeout-seconds",120),30,300);
legacyPinLength=clamp(getConfig().getInt("authentication.legacy-pin-length",8),6,12);
antiBot=getConfig().getBoolean("antibot.enabled",true);
ipLimit=clamp(getConfig().getInt("antibot.max-new-connections-per-ip-per-minute",24),3,200);
maxTracked=clamp(getConfig().getInt("antibot.maximum-tracked-addresses",4000),100,50000);
int rounds=clamp(getConfig().getInt("authentication.pbkdf2-iterations",260000),150000,700000);
rememberDays=clamp(getConfig().getInt("remember-ip.expire-after-days",7),1,30);
try{
legacy=new AccountStore(getDataFolder().toPath().resolve("accounts.properties"),rounds);
accounts=new DualCredentialStore(getDataFolder().toPath().resolve("credentials-v2.properties"),rounds);
}catch(IOException failure){
getLogger().severe("Account database cannot be loaded. Authentication locked: "+failure.getMessage());
Bukkit.getPluginManager().registerEvents(this,this);
return;
}
executor=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),task->{
Thread thread=new Thread(task,"UnifiedAuth-password-hash");
thread.setDaemon(true);
return thread;
},new ThreadPoolExecutor.AbortPolicy());
boolean wantsRemember=getConfig().getBoolean("remember-ip.enabled",true);
rememberPasswordSkip=RememberIpPolicy.maySkipPassword(Bukkit.getOnlineMode(),
getConfig().getBoolean("remember-ip.allow-verified-proxy-offline-mode",false),
getConfig().getBoolean("remember-ip.allow-password-skip",true));
if(wantsRemember){
try{
// Never promote a record collected on an unverified connection into a password shortcut.
String directory=rememberPasswordSkip?"v2-trust":"v3-network-history";
trust=new RememberedIpStore(getDataFolder().toPath().resolve(directory),rememberDays);
rememberEnabled=true;
if(!rememberPasswordSkip)getLogger().info("Remember IP: recognition only; PIN and password remain mandatory.");
}catch(IOException failure){
getLogger().warning("Remember IP unavailable, full PIN/password still required: "+failure.getMessage());
}
}
ready=true;
Bukkit.getPluginManager().registerEvents(this,this);
cleanupTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
long now=System.currentTimeMillis();
ips.entrySet().removeIf(entry->now-entry.getValue().opened>120000);
failuresByAccountIp.entrySet().removeIf(entry->now-entry.getValue().opened>900000);
},1200,1200);
getLogger().info("Pre-world single-step registration and login: 4-digit PIN and password on one screen; "+accounts.count()+" v2 accounts, "+legacy.count()+" legacy records. IP remember="+rememberEnabled);
getLogger().warning("Vanilla Minecraft dialog text input SHOWS password characters. Use a private screen.");
}
@Override public void onDisable(){
ready=false;
if(cleanupTask!=-1)Bukkit.getScheduler().cancelTask(cleanupTask);
sessions.values().forEach(s->{s.result.complete(false);s.erase();});
sessions.clear();
ips.clear();
failuresByAccountIp.clear();
passwordAuthenticated.clear();
verifiedJoins.clear();
if(executor!=null)executor.shutdownNow();
}
private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}

@EventHandler(priority=EventPriority.LOWEST)
public void prelogin(AsyncPlayerPreLoginEvent event){
if(!ready){
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,branded("Сервис авторизации недоступен",DANGER));
return;
}
if(!antiBot)return;
InetAddress remote=event.getAddress();
if(remote==null)return;
String key=remote.getHostAddress();
if(ips.size()>=maxTracked && !ips.containsKey(key)){
rateDenied++;
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,branded("Слишком много подключений",DANGER));
return;
}
long now=System.currentTimeMillis();
Window window=ips.compute(key,(k,v)->{
if(v==null)return new Window(now);
if(now-v.opened>60000){v.opened=now;v.count=0;}
return v;
});
synchronized(window){
window.count++;
if(window.count>ipLimit){
rateDenied++;
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,branded("Слишком много подключений. Попробуйте позже.",DANGER));
}
}
}

@EventHandler
public void configure(AsyncPlayerConnectionConfigureEvent event){
PlayerConfigurationConnection connection=event.getConnection();
if(!ready){connection.disconnect(branded("Авторизация недоступна",DANGER));return;}
UUID uuid=connection.getProfile().getId();
String username=connection.getProfile().getName();
if(uuid==null || username==null || !username.matches("[A-Za-z0-9_]{3,16}")){
connection.disconnect(branded("Недопустимое имя игрока",DANGER));return;
}
String name=username.toLowerCase(Locale.ROOT);
if(sessions.containsKey(uuid)){
connection.disconnect(branded("Сессия авторизации уже существует",DANGER));return;
}
InetSocketAddress address=connection.getClientAddress();
String ip=address==null||address.getAddress()==null?null:address.getAddress().getHostAddress();
// Remembered IP never bypasses the 4-digit PIN. It skips only the password.
boolean knownNetwork=accounts.exists(name) && rememberEnabled && ip!=null &&
trust.isTrusted(name,uuid,ip,System.currentTimeMillis());
boolean trustedConnection=knownNetwork && rememberPasswordSkip;
if(isLocked(name,ip)){
denied++;
connection.disconnect(branded("Слишком много попыток входа. Попробуйте позже.",DANGER));
return;
}
if(sessions.size()>=maxPending){
denied++;
connection.disconnect(branded("Очередь входа заполнена",DANGER));return;
}
Stage initial=accounts.exists(name)?Stage.LOGIN_PIN:legacy.exists(name)?Stage.LEGACY_PIN:Stage.CREATE_PIN;
Session s=new Session(uuid,name,connection,ip,initial);
s.trusted=trustedConnection;
s.knownNetwork=knownNetwork;
s.remember=knownNetwork;
if(sessions.putIfAbsent(uuid,s)!=null){
connection.disconnect(branded("Сессия авторизации уже активна",DANGER));return;
}
s.result.completeOnTimeout(false,timeoutSeconds,TimeUnit.SECONDS);
try{
render(s,null);
if(!s.result.join()){
denied++;
connection.disconnect(branded("Неверные данные или время входа истекло",DANGER));
}
}catch(Exception error){
denied++;
connection.disconnect(branded("Не удалось завершить авторизацию",DANGER));
}finally{
sessions.remove(uuid,s);
s.erase();
}
}

private static final TextColor GOLD=TextColor.color(0xF5C875);
private static final TextColor MINT=TextColor.color(0x68E5B1);
private static final TextColor ICE=TextColor.color(0xA88BFF);
private static final TextColor ROSE=TextColor.color(0xF1C6FF);
private static final TextColor MUTED=TextColor.color(0xA7B7CB);
private static final TextColor DANGER=TextColor.color(0xFF7979);
private static Component accent(String value,TextColor color){
return Component.text(value,color).decorate(TextDecoration.BOLD);
}
private String brandName(){
return getConfig().getString("branding.name","Land of Losers");
}
private Component brand(){
String name=brandName();
Component result=Component.empty();
int[] chars=name.codePoints().toArray();
for(int i=0;i<chars.length;i++){
float t=chars.length<=1?0f:(float)i/(chars.length-1);
int red=Math.round(168+(241-168)*t);
int green=Math.round(139+(198-139)*t);
int blue=255;
result=result.append(accent(new String(Character.toChars(chars[i])),TextColor.color(red,green,blue)));
}
return result;
}
private Component branded(String message,TextColor color){
return brand().append(Component.text(" · ",MUTED)).append(Component.text(message,color));
}
private ActionButton button(String caption,String id){
return button(caption,id,ICE,80,brandName());
}
private ActionButton button(String caption,String id,TextColor color,int width,String hint){
return ActionButton.create(accent(caption,color),Component.text(hint,MUTED),width,
DialogAction.customClick(Key.key("unifiedauth:"+id),null));
}
private void render(Session s,String notice){
if(s.result.isDone() || !s.connection.isConnected())return;
String label=switch(s.stage){
case LEGACY_PIN -> "Обновление аккаунта";
case CREATE_PIN -> "Создание аккаунта";
case LOGIN_PIN -> s.trusted?"Быстрый вход":"Вход в аккаунт";
};
DialogBase.Builder base=DialogBase.builder(brand()
.append(Component.text("  •  ",MUTED)).append(Component.text(label,ICE)))
.canCloseWithEscape(false);
List<DialogInput> inputs=new ArrayList<>();
List<ActionButton> buttons=new ArrayList<>();
List<DialogBody> body=new ArrayList<>();
body.add(DialogBody.plainMessage(brand().append(Component.text("  ·  ",MUTED)).append(accent(getConfig().getString("branding.edition","Vanilla+"),GOLD))));
body.add(DialogBody.plainMessage(accent("ЗАЩИЩЁННЫЙ ВХОД",ROSE)));
body.add(DialogBody.plainMessage(Component.text(getConfig().getString("branding.auth-subtitle","Твой мир. Твоя история."),MUTED)));
if(notice!=null)body.add(DialogBody.plainMessage(Component.text(notice,DANGER)));
if(s.stage==Stage.LEGACY_PIN){
body.add(DialogBody.plainMessage(Component.text("Введите старый PIN, чтобы обновить аккаунт.",NamedTextColor.WHITE)));
body.add(DialogBody.plainMessage(accent("●".repeat(s.digits.length())+"○".repeat(Math.max(0,legacyPinLength-s.digits.length())),GOLD)));
for(int i=1;i<=9;i++)buttons.add(button(Integer.toString(i),"digit"+i));
buttons.add(button("⌫","back"));
buttons.add(button("0","digit0"));
}else{
body.add(DialogBody.plainMessage(Component.text(s.stage==Stage.CREATE_PIN?"Создайте PIN и надёжный пароль.":s.trusted?"Сеть распознана. Подтвердите вход своим PIN.":"Подтвердите свою личность.",NamedTextColor.WHITE)));
if(s.knownNetwork && !s.trusted)body.add(DialogBody.plainMessage(Component.text("✓ Сеть распознана. PIN и пароль обязательны.",MINT)));
inputs.add(DialogInput.text("pin",accent("PIN  •  4 цифры",ICE))
.width(280).initial("").maxLength(4).build());
if(!s.trusted || s.stage!=Stage.LOGIN_PIN){
inputs.add(DialogInput.text("password",accent("Пароль  •  10–64 символа",ICE))
.width(280).initial("").maxLength(64).build());
if(rememberEnabled && s.ip!=null){
inputs.add(DialogInput.bool("remember",Component.text("Запомнить текущий IP на "+rememberDays+" дн.",GOLD),s.remember,"yes","no"));
body.add(DialogBody.plainMessage(Component.text(rememberPasswordSkip?"Только личная сеть: при следующем входе потребуется PIN.":"IP используется для распознавания сети, не вместо пароля.",MUTED)));
}
}
body.add(DialogBody.plainMessage(Component.text("Не показывайте экран: символы PIN и пароля не скрываются Minecraft.",MUTED)));
}
base.body(body);
if(!inputs.isEmpty())base.inputs(inputs);
buttons.add(button(s.stage==Stage.CREATE_PIN?"Создать аккаунт":"Продолжить","submit",MINT,150,"Подтвердить данные и войти"));
if(s.stage==Stage.LOGIN_PIN && s.trusted)buttons.add(button("Войти с паролем","full",GOLD,150,"Обычный вход и управление запоминанием IP"));
Dialog dialog=Dialog.create(builder->builder.empty()
.base(base.build()).type(DialogType.multiAction(buttons).build()));
s.connection.getAudience().showDialog(dialog);
}
private void renderLater(Session s,String notice){
Bukkit.getScheduler().runTask(this,()->{
if(sessions.get(s.uuid)==s && !s.result.isDone())render(s,notice);
});
}
@EventHandler
public void click(PlayerCustomClickEvent event){
if(!event.getIdentifier().asString().equals("unifiedauth:submit") &&
!event.getIdentifier().asString().startsWith("unifiedauth:digit") &&
!event.getIdentifier().asString().equals("unifiedauth:back") &&
!event.getIdentifier().asString().equals("unifiedauth:full"))return;
if(!(event.getCommonConnection() instanceof PlayerConfigurationConnection connection))return;
UUID uuid=connection.getProfile().getId();
if(uuid==null)return;
Session s=sessions.get(uuid);
if(s==null||s.result.isDone()||s.working.get())return;
String action=event.getIdentifier().asString().substring("unifiedauth:".length());
synchronized(s){
long now=System.currentTimeMillis();
if(now-s.lastAction<60)return;
s.lastAction=now;
if(s.stage==Stage.LEGACY_PIN){
if(action.startsWith("digit")&&action.length()==6){
char digit=action.charAt(5);
if(digit>='0'&&digit<='9'&&s.digits.length()<legacyPinLength)s.digits.append(digit);
renderLater(s,null);return;
}
if(action.equals("back")){
if(!s.digits.isEmpty())s.digits.setLength(s.digits.length()-1);
renderLater(s,null);return;
}
if(!action.equals("submit"))return;
if(s.digits.length()!=legacyPinLength){renderLater(s,"Введите старый PIN");return;}
String pin=s.digits.toString();
s.digits.setLength(0);
runCrypto(s,Stage.LEGACY_PIN,()->legacy.authenticate(s.name,pin),false);
return;
}
if(action.equals("full") && s.stage==Stage.LOGIN_PIN && s.trusted){
s.trusted=false;
renderLater(s,null);
return;
}
if(!action.equals("submit"))return;
DialogResponseView response=event.getDialogResponseView();
String pin=response==null?null:response.getText("pin");
String password=response==null?null:response.getText("password");
Boolean remember=response==null?null:response.getBoolean("remember");
s.rememberSubmitted=remember!=null && rememberEnabled && s.ip!=null && !s.trusted;
if(s.rememberSubmitted)s.remember=remember;
if(pin==null || !pin.matches("[0-9]{4}")){
renderLater(s,"PIN должен содержать ровно 4 цифры.");return;
}
Stage stage=s.stage;
if(stage==Stage.CREATE_PIN){
if(!passwordValid(password)){
renderLater(s,"Пароль должен содержать от 10 до 64 символов. PIN ровно 4 цифры.");
return;
}
final String savePin=pin,savePassword=password;
boolean migration=legacy.exists(s.name);
// One form is enough. Commit to disk and recheck BOTH credentials before
// releasing the connection into the game; no confirmation dialog loop.
runCrypto(s,Stage.CREATE_PIN,()->
accounts.register(s.name,savePin,savePassword)
&& accounts.verifyPin(s.name,savePin)
&& accounts.verifyPassword(s.name,savePassword),migration);
return;
}
if(stage==Stage.LOGIN_PIN){
if(!s.trusted&&!passwordValid(password)){
renderLater(s,"Введите пароль от 10 до 64 символов.");return;
}
final String checkPin=pin,checkPassword=password;
runCrypto(s,Stage.LOGIN_PIN,()->accounts.verifyPin(s.name,checkPin) &&
(s.trusted?(rememberPasswordSkip && trust.isTrusted(s.name,s.uuid,s.ip,System.currentTimeMillis())):accounts.verifyPassword(s.name,checkPassword)),false);
}
}
}
private boolean passwordValid(String value){return value!=null&&value.length()>=10&&value.length()<=64;}
@FunctionalInterface private interface Check {boolean run() throws Exception;}
private void runCrypto(Session s,Stage submitted,Check check,boolean migrated){
if(!s.working.compareAndSet(false,true))return;
try{
executor.execute(()->{
boolean ok=false;
try{ok=check.run();}
catch(Exception error){getLogger().warning("Credential check/save failed for "+s.name+" ("+error.getClass().getSimpleName()+"). Access denied.");}
boolean passed=ok;
if(passed && !s.result.isDone() && (submitted==Stage.CREATE_PIN || (submitted==Stage.LOGIN_PIN && !s.trusted)) &&
s.rememberSubmitted && rememberEnabled && s.ip!=null){
try{
if(s.remember)trust.trust(s.name,s.uuid,s.ip,System.currentTimeMillis());
else if(trust.isTrusted(s.name,s.uuid,s.ip,System.currentTimeMillis()))trust.forget(s.name,s.uuid);
}catch(IOException error){getLogger().warning("Could not update remembered IP; credentials still verified.");}
}
try{
Bukkit.getScheduler().runTask(this,()->{
s.working.set(false);
if(sessions.get(s.uuid)!=s || s.result.isDone())return;
if(!passed){
if(submitted==Stage.CREATE_PIN){
// Do not keep showing registration when saving fails: diagnose it and
// fail closed, never accidentally allow an unauthenticated player in.
if(accounts.exists(s.name)){
s.stage=Stage.LOGIN_PIN;
render(s,"Учётная запись уже существует. Теперь войдите.");
}else{
getLogger().severe("Account registration could not be persisted for "+s.name+". Check plugins/UnifiedAuth folder permissions.");
s.result.complete(false);
}
return;
}
failure(s,"Неверный PIN или пароль.");return;
}
failuresByAccountIp.remove(lockKey(s.name,s.ip));
switch(submitted){
case LEGACY_PIN -> {
s.stage=Stage.CREATE_PIN;
render(s,"Старый PIN верен. Создайте новый PIN и пароль.");
}
case CREATE_PIN -> {
registered++;
if(migrated)migrations++;
passwordAuthenticated.add(s.uuid);
verifiedJoins.put(s.uuid,true);
s.erase();
s.result.complete(true);
}
case LOGIN_PIN -> {
successes++;
if(s.trusted)trustedLogins++;
else passwordAuthenticated.add(s.uuid);
verifiedJoins.put(s.uuid,false);
s.erase();
s.result.complete(true);
}
default -> {}
}
});
}catch(IllegalStateException shutdown){s.result.complete(false);}
});
}catch(RejectedExecutionException overloaded){s.working.set(false);s.result.complete(false);}
}
private void failure(Session s,String message){
s.failures++;
recordFailure(s.name,s.ip);
s.digits.setLength(0);
if(s.failures>=maxAttempts){s.result.complete(false);return;}
renderLater(s,message+" Осталось попыток: "+(maxAttempts-s.failures)+".");
}
private String lockKey(String name,String ip){return name+"|"+(ip==null?"unknown":ip);}
private boolean isLocked(String name,String ip){
Window w=failuresByAccountIp.get(lockKey(name,ip));
if(w==null)return false;
long now=System.currentTimeMillis();
if(now-w.opened>900000){failuresByAccountIp.remove(lockKey(name,ip));return false;}
return w.count>=8;
}
private void recordFailure(String name,String ip){
if(failuresByAccountIp.size()>10000)failuresByAccountIp.clear();
long now=System.currentTimeMillis();
failuresByAccountIp.compute(lockKey(name,ip),(key,w)->{
if(w==null||now-w.opened>900000)w=new Window(now);
w.count++;return w;
});
}
@EventHandler public void closed(PlayerConnectionCloseEvent event){
Session s=sessions.remove(event.getPlayerUniqueId());
passwordAuthenticated.remove(event.getPlayerUniqueId());
verifiedJoins.remove(event.getPlayerUniqueId());
if(s!=null){s.result.complete(false);s.erase();}
}

@EventHandler(priority=EventPriority.MONITOR)
public void welcome(PlayerJoinEvent event){
Player player=event.getPlayer();
Boolean first=verifiedJoins.remove(player.getUniqueId());
if(first==null || !getConfig().getBoolean("welcome.enabled",true))return;
Bukkit.getScheduler().runTaskLater(this,()->{
if(!player.isOnline() || Bukkit.getPlayer(player.getUniqueId())!=player)return;
String edition=getConfig().getString("branding.edition","Vanilla+");
String greeting=first?"Аккаунт создан":"С возвращением";
player.showTitle(Title.title(brand(),
Component.text(greeting+", ",MUTED).append(accent(player.getName(),MINT)),
Title.Times.times(Duration.ofMillis(450),Duration.ofSeconds(3),Duration.ofMillis(850))));
player.sendActionBar(branded("✓ Авторизация успешна",MINT));
player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━",MUTED));
player.sendMessage(Component.text("  ").append(brand()).append(Component.text("  ·  "+edition,GOLD)));
player.sendMessage(Component.text("  "+greeting+", ",MUTED).append(accent(player.getName(),ROSE)));
player.sendMessage(Component.text("  Ваш аккаунт защищён. Приятной игры!",MINT));
player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━",MUTED));
if(getConfig().getBoolean("branding.tab-header-enabled",true)){
player.sendPlayerListHeaderAndFooter(brand().append(Component.text(" · "+edition,GOLD)),
Component.text("Твой мир. Твоя история.",MUTED));
}
if(getConfig().getBoolean("welcome.sound",true))
player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"),Sound.Source.MASTER,0.45f,1.25f));
},10L);
}

@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length>0 && args[0].equalsIgnoreCase("remember")){
if(!(sender instanceof Player player)){sender.sendMessage("Only players can manage remembered IPs.");return true;}
if(!rememberEnabled || trust==null){player.sendMessage("Remember IP is disabled on this server.");return true;}
String name=player.getName().toLowerCase(Locale.ROOT);
UUID uuid=player.getUniqueId();
if(!accounts.exists(name)){player.sendMessage("Account upgrade has not been completed.");return true;}
InetSocketAddress address=player.getConnection().getClientAddress();
String ip=address==null||address.getAddress()==null?null:address.getAddress().getHostAddress();
if(ip==null){player.sendMessage("Cannot obtain IP; PIN/password required.");return true;}
String action=args.length>1?args[1].toLowerCase(Locale.ROOT):"status";
if(action.equals("status")){
long until=trust.trustedUntil(name,uuid,ip,System.currentTimeMillis());
player.sendMessage(until==0?"Этот IP не запомнен.":"Сеть запомнена ещё на "+Math.max(0,(until-System.currentTimeMillis()+86399999)/86400000)+" дн. "+(rememberPasswordSkip?"Доступен вход по PIN.":"PIN и пароль обязательны."));
}else if(action.equals("on") || action.equals("off") || action.equals("forget")){
try{
executor.execute(()->{
try{
if(action.equals("on")){
if(!passwordAuthenticated.contains(uuid)){
Bukkit.getScheduler().runTask(this,()->player.sendMessage("Для нового доверия IP войдите с полным паролем."));
return;
}
trust.trust(name,uuid,ip,System.currentTimeMillis());
}
else trust.forget(name,uuid);
Bukkit.getScheduler().runTask(this,()->player.sendMessage(action.equals("on")?(rememberPasswordSkip?"IP запомнен. Общая сеть не является уникальным устройством.":"IP запомнен. PIN и пароль обязательны."):"Запоминание IP отключено."));
}catch(IOException failure){
getLogger().warning("Trust database update failed: "+failure.getMessage());
Bukkit.getScheduler().runTask(this,()->player.sendMessage("Could not save this preference."));
}
});
}catch(RejectedExecutionException busy){player.sendMessage("Auth service is busy.");}
}else player.sendMessage("Usage: /ua remember [on|off|status]");
return true;
}
if(sender.hasPermission("unifiedauth.admin")){
sender.sendMessage("UnifiedAuth 1.5.1: v2Accounts="+(accounts==null?0:accounts.count())+" legacy="+(legacy==null?0:legacy.count())+" pending="+sessions.size()+" successes="+successes+" created="+registered+" migrated="+migrations+" trustedLogins="+trustedLogins+" rateDenied="+rateDenied+" rememberEnabled="+rememberEnabled+" passwordSkip="+rememberPasswordSkip);
}else sender.sendMessage("Usage: /ua remember [on|off|status]");
return true;
}
}
