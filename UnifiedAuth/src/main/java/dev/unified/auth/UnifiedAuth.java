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
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
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
private enum Stage { LEGACY_PIN, CREATE_PIN, CONFIRM_PIN, CREATE_PASSWORD, CONFIRM_PASSWORD, LOGIN_PIN, LOGIN_PASSWORD }
private static final class Session {
final UUID uuid;
final String name;
final PlayerConfigurationConnection connection;
final String ip;
final CompletableFuture<Boolean> result=new CompletableFuture<>();
final StringBuilder digits=new StringBuilder(12);
final AtomicBoolean working=new AtomicBoolean();
Stage stage;
String firstPin;
String firstPassword;
int failures;
long lastAction;
boolean remember;
Session(UUID uuid,String name,PlayerConfigurationConnection connection,String ip,Stage stage){
this.uuid=uuid;this.name=name;this.connection=connection;this.ip=ip;this.stage=stage;
}
void erase(){
digits.setLength(0);
firstPin=null;
firstPassword=null;
}
}
private static final class Window {
long opened;
int count;
Window(long now){opened=now;}
}
private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
private final Map<String,Window> ips=new ConcurrentHashMap<>();
private ThreadPoolExecutor executor;
private AccountStore legacy;
private DualCredentialStore accounts;
private RememberedIpStore trust;
private volatile boolean ready;
private boolean rememberEnabled,antiBot;
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
boolean wantsRemember=getConfig().getBoolean("remember-ip.enabled",false);
boolean authenticatedAccount=Bukkit.getOnlineMode()
|| getConfig().getBoolean("remember-ip.allow-verified-proxy-offline-mode",false);
if(wantsRemember && !authenticatedAccount)
getLogger().warning("Remember IP disabled: offline identity is not verified. Secure forwarding with backend firewall is required.");
if(wantsRemember && authenticatedAccount){
try{
// V1.1 IP remembered records must NEVER bypass the newly required password.
// A separate directory prevents silent auto-login of accounts not enrolled in v2.
trust=new RememberedIpStore(getDataFolder().toPath().resolve("v2-trust"),rememberDays);
rememberEnabled=true;
}catch(IOException failure){
getLogger().warning("Remember IP unavailable, full PIN/password still required: "+failure.getMessage());
}
}
ready=true;
Bukkit.getPluginManager().registerEvents(this,this);
cleanupTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
long now=System.currentTimeMillis();
ips.entrySet().removeIf(entry->now-entry.getValue().opened>120000);
},1200,1200);
getLogger().info("Two-stage pre-world login: 4-digit PIN then text password; "+accounts.count()+" v2 accounts, "+legacy.count()+" legacy records. IP remember="+rememberEnabled);
getLogger().warning("Vanilla Minecraft dialog text input SHOWS password characters. Use a private screen.");
}
@Override public void onDisable(){
ready=false;
if(cleanupTask!=-1)Bukkit.getScheduler().cancelTask(cleanupTask);
sessions.values().forEach(s->{s.result.complete(false);s.erase();});
sessions.clear();
ips.clear();
if(executor!=null)executor.shutdownNow();
}
private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}

@EventHandler(priority=EventPriority.LOWEST)
public void prelogin(AsyncPlayerPreLoginEvent event){
if(!ready){
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.text("Authentication service unavailable",NamedTextColor.RED));
return;
}
if(!antiBot)return;
InetAddress remote=event.getAddress();
if(remote==null)return;
String key=remote.getHostAddress();
if(ips.size()>=maxTracked && !ips.containsKey(key)){
rateDenied++;
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.text("Too many active connection sources",NamedTextColor.RED));
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
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.text("Too many connections. Try later.",NamedTextColor.RED));
}
}
}

@EventHandler
public void configure(AsyncPlayerConnectionConfigureEvent event){
PlayerConfigurationConnection connection=event.getConnection();
if(!ready){connection.disconnect(Component.text("Authentication unavailable",NamedTextColor.RED));return;}
UUID uuid=connection.getProfile().getId();
String username=connection.getProfile().getName();
if(uuid==null || username==null || !username.matches("[A-Za-z0-9_]{3,16}")){
connection.disconnect(Component.text("Invalid player identity",NamedTextColor.RED));return;
}
String name=username.toLowerCase(Locale.ROOT);
if(sessions.containsKey(uuid)){
connection.disconnect(Component.text("Authentication session already exists",NamedTextColor.RED));return;
}
InetSocketAddress address=connection.getClientAddress();
String ip=address==null||address.getAddress()==null?null:address.getAddress().getHostAddress();
// Trust records from 1.1 are never consulted: a password must first be enrolled.
if(accounts.exists(name) && rememberEnabled && ip!=null &&
trust.isTrusted(name,uuid,ip,System.currentTimeMillis())){
trustedLogins++;return;
}
if(sessions.size()>=maxPending){
denied++;
connection.disconnect(Component.text("Login queue busy",NamedTextColor.RED));return;
}
Stage initial=accounts.exists(name)?Stage.LOGIN_PIN:legacy.exists(name)?Stage.LEGACY_PIN:Stage.CREATE_PIN;
Session s=new Session(uuid,name,connection,ip,initial);
if(sessions.putIfAbsent(uuid,s)!=null){
connection.disconnect(Component.text("Authentication session already active",NamedTextColor.RED));return;
}
s.result.completeOnTimeout(false,timeoutSeconds,TimeUnit.SECONDS);
try{
render(s,null);
if(!s.result.join()){
denied++;
connection.disconnect(Component.text("PIN/password failed or timed out",NamedTextColor.RED));
}
}catch(Exception error){
denied++;
connection.disconnect(Component.text("Authentication could not be completed",NamedTextColor.RED));
}finally{
sessions.remove(uuid,s);
s.erase();
}
}

private ActionButton button(String caption,String id){
return ActionButton.create(Component.text(caption),Component.text("UnifiedAuth"),80,
DialogAction.customClick(Key.key("unifiedauth:"+id),null));
}
private boolean passwordStep(Stage stage){
return stage==Stage.LOGIN_PASSWORD || stage==Stage.CREATE_PASSWORD || stage==Stage.CONFIRM_PASSWORD;
}
private void render(Session s,String notice){
if(s.result.isDone() || !s.connection.isConnected())return;
Stage stage=s.stage;
String title=switch(stage){
case LEGACY_PIN -> "Old 8-digit PIN (account upgrade)";
case CREATE_PIN -> "Create new 4-digit PIN";
case CONFIRM_PIN -> "Repeat 4-digit PIN";
case LOGIN_PIN -> "Step 1 of 2: 4-digit PIN";
case LOGIN_PASSWORD -> "Step 2 of 2: password";
case CREATE_PASSWORD -> "Create password (10–64 chars)";
case CONFIRM_PASSWORD -> "Repeat password";
};
String intro=notice==null?title:notice+"\n"+title;
List<ActionButton> buttons=new ArrayList<>();
DialogBase.Builder base=DialogBase.builder(Component.text("UnifiedAuth • Secure Entry",NamedTextColor.AQUA))
.canCloseWithEscape(false);
if(passwordStep(stage)){
intro+="\nWARNING: this Minecraft text field is NOT masked. Others can see the password on your screen.";
base.body(List.of(DialogBody.plainMessage(Component.text(intro,NamedTextColor.WHITE))));
base.inputs(List.of(DialogInput.text("password",Component.text("Password (visible while typing)"))
.width(280).initial("").maxLength(64).build()));
buttons.add(button("Continue","submit"));
}else{
int length=stage==Stage.LEGACY_PIN?legacyPinLength:4;
intro+="\n"+"●".repeat(s.digits.length())+"○".repeat(Math.max(0,length-s.digits.length()));
base.body(List.of(DialogBody.plainMessage(Component.text(intro,NamedTextColor.WHITE))));
for(int i=1;i<=9;i++)buttons.add(button(Integer.toString(i),"digit"+i));
buttons.add(button("⌫","back"));
buttons.add(button("0","digit0"));
buttons.add(button("Continue","submit"));
}
if(rememberEnabled && s.ip!=null && stage!=Stage.LEGACY_PIN)
buttons.add(button("Remember this IP: "+(s.remember?"ON":"OFF"),"remember"));
Dialog dialog=Dialog.create(builder->builder.empty().base(base.build()).type(DialogType.multiAction(buttons).build()));
s.connection.getAudience().showDialog(dialog);
}
private void renderLater(Session s,String notice){
Bukkit.getScheduler().runTask(this,()->{
if(sessions.get(s.uuid)==s && !s.result.isDone())render(s,notice);
});
}
@EventHandler
public void click(PlayerCustomClickEvent event){
if(!event.getIdentifier().asString().startsWith("unifiedauth:"))return;
if(!(event.getCommonConnection() instanceof PlayerConfigurationConnection connection))return;
UUID uuid=connection.getProfile().getId();
if(uuid==null)return;
Session s=sessions.get(uuid);
if(s==null||s.result.isDone()||s.working.get())return;
String action=event.getIdentifier().asString().substring("unifiedauth:".length());
String notice=null;
String secret=null;
Stage submitted=null;
synchronized(s){
long now=System.currentTimeMillis();
if(now-s.lastAction<60)return;
s.lastAction=now;
Stage stage=s.stage;
if(action.equals("remember")){
if(rememberEnabled && s.ip!=null)s.remember=!s.remember;
}else if(action.startsWith("digit") && action.length()==6 && !passwordStep(stage)){
char digit=action.charAt(5);
int count=stage==Stage.LEGACY_PIN?legacyPinLength:4;
if(digit>='0'&&digit<='9'&&s.digits.length()<count)s.digits.append(digit);
}else if(action.equals("back") && !passwordStep(stage)){
if(!s.digits.isEmpty())s.digits.setLength(s.digits.length()-1);
}else if(action.equals("submit")){
if(passwordStep(stage)){
DialogResponseView response=event.getDialogResponseView();
secret=response==null?null:response.getText("password");
if(secret==null || secret.length()<10 || secret.length()>64){
notice="Enter 10 to 64 password characters.";
secret=null;
}else submitted=stage;
}else{
int count=stage==Stage.LEGACY_PIN?legacyPinLength:4;
if(s.digits.length()!=count){notice="Enter "+count+" digits."; }
else{
secret=s.digits.toString();
s.digits.setLength(0);
submitted=stage;
}
}
}else return;
}
if(submitted==null){renderLater(s,notice);return;}
switch(submitted){
case CREATE_PIN -> {
s.firstPin=secret;
s.stage=Stage.CONFIRM_PIN;
renderLater(s,"Repeat the new PIN.");
}
case CONFIRM_PIN -> {
if(!secret.equals(s.firstPin)){
s.firstPin=null;
s.stage=Stage.CREATE_PIN;
failure(s,"PINs did not match.");
}else{
s.stage=Stage.CREATE_PASSWORD;
renderLater(s,"PIN confirmed. Create a password.");
}
}
case CREATE_PASSWORD -> {
s.firstPassword=secret;
s.stage=Stage.CONFIRM_PASSWORD;
renderLater(s,"Re-enter the password to confirm.");
}
case CONFIRM_PASSWORD -> {
if(!secret.equals(s.firstPassword)){
s.firstPassword=null;
s.stage=Stage.CREATE_PASSWORD;
failure(s,"Passwords did not match.");
}else{
final String pin=s.firstPin;
final String password=secret;
boolean migration=legacy.exists(s.name);
runCrypto(s,submitted,()->accounts.register(s.name,pin,password),migration);
}
}
case LOGIN_PIN -> {
final String pin=secret;
runCrypto(s,submitted,()->accounts.verifyPin(s.name,pin),false);
}
case LOGIN_PASSWORD -> {
final String password=secret;
runCrypto(s,submitted,()->accounts.verifyPassword(s.name,password),false);
}
case LEGACY_PIN -> {
final String pin=secret;
runCrypto(s,submitted,()->legacy.authenticate(s.name,pin),false);
}
default -> {}
}
}
@FunctionalInterface private interface Check {boolean run() throws Exception;}
private void runCrypto(Session s,Stage submitted,Check check,boolean migrated){
if(!s.working.compareAndSet(false,true))return;
try{
executor.execute(()->{
boolean ok=false;
try{ok=check.run();}
catch(Exception error){getLogger().warning("Credential check or save failed; access denied.");}
boolean passed=ok;
if(passed && (submitted==Stage.CONFIRM_PASSWORD || submitted==Stage.LOGIN_PASSWORD) &&
s.remember && rememberEnabled && s.ip!=null){
try{trust.trust(s.name,s.uuid,s.ip,System.currentTimeMillis());}
catch(IOException error){getLogger().warning("Could not remember IP; credentials still verified.");}
}
try{
Bukkit.getScheduler().runTask(this,()->{
s.working.set(false);
if(sessions.get(s.uuid)!=s || s.result.isDone())return;
if(!passed){failure(s,"Incorrect credentials or storage unavailable.");return;}
switch(submitted){
case LOGIN_PIN -> {
s.stage=Stage.LOGIN_PASSWORD;
render(s,"PIN correct. Enter your password.");
}
case LEGACY_PIN -> {
s.stage=Stage.CREATE_PIN;
render(s,"Legacy PIN accepted. Set a new 4-digit PIN and password.");
}
case CONFIRM_PASSWORD -> {
registered++;
if(migrated)migrations++;
s.erase();
s.result.complete(true);
}
case LOGIN_PASSWORD -> {
successes++;
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
s.digits.setLength(0);
s.firstPassword=null;
if(s.failures>=maxAttempts){s.result.complete(false);return;}
if(s.stage==Stage.LOGIN_PASSWORD)s.stage=Stage.LOGIN_PIN;
renderLater(s,message+" "+(maxAttempts-s.failures)+" attempts left.");
}
@EventHandler public void closed(PlayerConnectionCloseEvent event){
Session s=sessions.remove(event.getPlayerUniqueId());
if(s!=null){s.result.complete(false);s.erase();}
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
player.sendMessage(until==0?"This IP is not trusted.":"Trusted for "+Math.max(0,(until-System.currentTimeMillis()+86399999)/86400000)+" more days.");
}else if(action.equals("on") || action.equals("off") || action.equals("forget")){
try{
executor.execute(()->{
try{
if(action.equals("on"))trust.trust(name,uuid,ip,System.currentTimeMillis());
else trust.forget(name,uuid);
Bukkit.getScheduler().runTask(this,()->player.sendMessage(action.equals("on")?"IP remembered; shared networks are not a unique device.":"Remembered IP revoked."));
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
sender.sendMessage("UnifiedAuth 1.2: v2Accounts="+(accounts==null?0:accounts.count())+" legacy="+(legacy==null?0:legacy.count())+" pending="+sessions.size()+" successes="+successes+" created="+registered+" migrated="+migrations+" trustedLogins="+trustedLogins+" rateDenied="+rateDenied+" rememberEnabled="+rememberEnabled);
}else sender.sendMessage("Usage: /ua remember [on|off|status]");
return true;
}
}
