package dev.unified.auth;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnifiedAuth extends JavaPlugin implements Listener {
private enum Stage { LOGIN, REGISTER, CONFIRM }
private static final class Session {
final UUID uuid;
final String name;
final PlayerConfigurationConnection connection;
final String clientIp;
boolean rememberRequested;
final CompletableFuture<Boolean> result=new CompletableFuture<>();
final StringBuilder entry=new StringBuilder(12);
final AtomicBoolean processing=new AtomicBoolean();
Stage stage;
String firstPin;
int attempts;
long lastInput;
Session(UUID uuid,String name,PlayerConfigurationConnection conn,String clientIp,Stage stage){
this.uuid=uuid;this.name=name;this.connection=conn;this.clientIp=clientIp;this.stage=stage;
}
}
private static final class Rate {
long opened;
int count;
Rate(long now){opened=now;}
}
private final Map<UUID,Session> pending=new ConcurrentHashMap<>();
private final Map<String,Rate> connections=new ConcurrentHashMap<>();
private ThreadPoolExecutor workers;
private AccountStore accounts;
private RememberedIpStore rememberedIps;
private boolean rememberEnabled;
private int rememberDays;
private long trustedLogins;
private int pinLength,maxAttempts,maxPending,timeoutSeconds,ipLimit,maxTracked;
private volatile boolean ready;
private boolean enabled,antiBot;
private long totalLogins,totalRegistrations,denials,rateDenials;
private int cleanupTask=-1;

@Override public void onEnable(){
saveDefaultConfig();
for(String other:List.of("nLogin","OpeNLogin","AuthMe","AuthMeReloaded")){
if(Bukkit.getPluginManager().getPlugin(other)!=null){
getLogger().severe("Cannot enable with "+other+". Disable the other login plugin first.");
Bukkit.getPluginManager().disablePlugin(this);
return;
}
}
enabled=getConfig().getBoolean("authentication.enabled",true);
if(!enabled){
getLogger().severe("Authentication is disabled. Fail-closed: disabling UnifiedAuth.");
Bukkit.getPluginManager().disablePlugin(this);
return;
}
pinLength=Math.max(6,Math.min(12,getConfig().getInt("authentication.pin-length",8)));
maxAttempts=Math.max(1,Math.min(5,getConfig().getInt("authentication.maximum-attempts-per-connection",3)));
maxPending=Math.max(5,Math.min(200,getConfig().getInt("authentication.maximum-pending-connections",50)));
timeoutSeconds=Math.max(20,Math.min(180,getConfig().getInt("authentication.timeout-seconds",90)));
antiBot=getConfig().getBoolean("antibot.enabled",true);
ipLimit=Math.max(3,Math.min(200,getConfig().getInt("antibot.max-new-connections-per-ip-per-minute",24)));
maxTracked=Math.max(100,Math.min(50000,getConfig().getInt("antibot.maximum-tracked-addresses",4000)));
int iterations=Math.max(150000,Math.min(700000,getConfig().getInt("authentication.pbkdf2-iterations",260000)));
try {
accounts=new AccountStore(getDataFolder().toPath().resolve("accounts.properties"),iterations);
}catch(IOException ex){
getLogger().severe("Cannot safely load account database. Access denied: "+ex.getMessage());
getLogger().severe("UnifiedAuth will reject all connections until the account database is fixed.");
Bukkit.getPluginManager().registerEvents(this,this);
return;
}
workers=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{
Thread t=new Thread(r,"UnifiedAuth-crypto");
t.setDaemon(true);
return t;
},new ThreadPoolExecutor.AbortPolicy());
// IP-only remembered access is deliberately opt-in twice: server config
// and a per-player authenticated choice. Never trust offline-mode usernames
// unless an administrator explicitly confirms secure proxy forwarding.
rememberDays=Math.max(1,Math.min(30,getConfig().getInt("remember-ip.expire-after-days",7)));
boolean requestedRemember=getConfig().getBoolean("remember-ip.enabled",false);
boolean authenticatedIdentity=Bukkit.getOnlineMode()
|| getConfig().getBoolean("remember-ip.allow-verified-proxy-offline-mode",false);
if(requestedRemember && !authenticatedIdentity){
getLogger().warning("IP remembering disabled: offline-mode UUID/name is not authenticated. Use verified secure proxy forwarding only if properly configured.");
}
if(requestedRemember && authenticatedIdentity){
try{
rememberedIps=new RememberedIpStore(getDataFolder().toPath(),rememberDays);
rememberEnabled=true;
getLogger().warning("Optional IP remembering ENABLED. Shared NAT addresses are NOT proof of device identity. Players must individually opt in.");
}catch(IOException error){
rememberedIps=null;
rememberEnabled=false;
getLogger().severe("IP remembering disabled due to storage error; PIN login remains active: "+error.getMessage());
}
}
ready=true;
Bukkit.getPluginManager().registerEvents(this,this);
cleanupTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
long now=System.currentTimeMillis();
connections.entrySet().removeIf(e->now-e.getValue().opened>120000);
},1200L,1200L);
getLogger().info("Pre-world masked PIN auth enabled; "+accounts.count()+" stored accounts. Remember IP="+rememberEnabled+". No player world loaded before PIN verification.");
}
@Override public void onDisable(){
if(cleanupTask!=-1)Bukkit.getScheduler().cancelTask(cleanupTask);
pending.values().forEach(s->s.result.complete(false));
pending.clear();
if(workers!=null)workers.shutdownNow();
connections.clear();
}
@EventHandler(priority=EventPriority.LOWEST)
public void prelogin(AsyncPlayerPreLoginEvent event){
if(!ready){event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.text("Authentication service unavailable.",NamedTextColor.RED));return;}
if(!antiBot)return;
InetAddress addr=event.getAddress();
if(addr==null)return;
String ip=addr.getHostAddress();
if(connections.size()>=maxTracked && !connections.containsKey(ip)){
rateDenials++;
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.text("Authentication protection temporarily at capacity.",NamedTextColor.RED));
return;
}
long now=System.currentTimeMillis();
Rate rate=connections.compute(ip,(key,old)->{
if(old==null)return new Rate(now);
if(now-old.opened>60000){old.opened=now;old.count=0;}
return old;
});
synchronized(rate){
rate.count++;
if(rate.count>ipLimit){
rateDenials++;
event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,Component.text("Too many connections. Try again later.",NamedTextColor.RED));
}
}
if(connections.size()>maxTracked)connections.entrySet().removeIf(e->now-e.getValue().opened>60000);
}
@EventHandler
public void configure(AsyncPlayerConnectionConfigureEvent event) {
PlayerConfigurationConnection connection=event.getConnection();
if(!ready){connection.disconnect(Component.text("Authentication service unavailable.",NamedTextColor.RED));return;}
UUID uuid=connection.getProfile().getId();
String username=connection.getProfile().getName();
if(uuid==null || username==null || !username.matches("[A-Za-z0-9_]{3,16}")){
connection.disconnect(Component.text("Invalid player profile.",NamedTextColor.RED));
return;
}
if(pending.containsKey(uuid)){
denials++;
connection.disconnect(Component.text("An authentication session is already active.",NamedTextColor.RED));
return;
}
InetSocketAddress clientAddress=connection.getClientAddress();
String clientIp=clientAddress==null || clientAddress.getAddress()==null?null:clientAddress.getAddress().getHostAddress();
String name=username.toLowerCase(Locale.ROOT);
boolean registered=accounts.exists(name);
// Trusted IP never permits registration. The stored record is bound to the
// exact UUID, name, canonical address and finite expiration.
if(registered && rememberEnabled && clientIp!=null &&
rememberedIps.isTrusted(name,uuid,clientIp,System.currentTimeMillis())){
trustedLogins++;
return;
}
if(pending.size()>=maxPending){
denials++;
connection.disconnect(Component.text("Authentication queue full. Try again.",NamedTextColor.RED));
return;
}
Session s=new Session(uuid,name,connection,clientIp,registered?Stage.LOGIN:Stage.REGISTER);
Session prior=pending.putIfAbsent(uuid,s);
if(prior!=null){
denials++;
connection.disconnect(Component.text("An authentication session is already active.",NamedTextColor.RED));
return;
}
s.result.completeOnTimeout(false,timeoutSeconds,TimeUnit.SECONDS);
try {
render(s,null);
if(!s.result.join()){
denials++;
connection.disconnect(Component.text("Login failed or timed out. Reconnect and try again.",NamedTextColor.RED));
}
} catch(Exception ex) {
denials++;
connection.disconnect(Component.text("Authentication failed safely.",NamedTextColor.RED));
} finally {
pending.remove(uuid,s);
s.entry.setLength(0);
s.firstPin=null;
}
}
private ActionButton button(String label,String id){
return ActionButton.create(Component.text(label),
Component.text("UnifiedAuth keypad"),
80,DialogAction.customClick(Key.key("unifiedauth:"+id),null));
}
private void render(Session s,String notice) {
if(s.result.isDone() || !s.connection.isConnected())return;
String action=s.stage==Stage.LOGIN?"Enter PIN":s.stage==Stage.REGISTER?"Create PIN":"Confirm PIN";
String masked="●".repeat(s.entry.length())+"○".repeat(pinLength-s.entry.length());
String description=(notice==null?"":notice+"\n")+action+" ("+pinLength+" digits)\n"+masked;
if(rememberEnabled && s.clientIp!=null){
description+="\nRemember this IP for "+rememberDays+" days: "+(s.rememberRequested?"ON":"OFF");
description+="\nOnly enable on a private, unshared network.";
}
List<ActionButton> actions=new ArrayList<>(13);
for(int i=1;i<=9;i++)actions.add(button(Integer.toString(i),"digit"+i));
actions.add(button("⌫","back"));
actions.add(button("0","digit0"));
actions.add(button("OK","submit"));
if(rememberEnabled && s.clientIp!=null) actions.add(button("Remember IP: "+(s.rememberRequested?"ON":"OFF"),"remember"));
final String dialogDescription=description;
Dialog dialog=Dialog.create(builder->builder.empty()
.base(DialogBase.builder(Component.text("UnifiedAuth • Before World",NamedTextColor.AQUA))
.canCloseWithEscape(false)
.body(List.of(DialogBody.plainMessage(Component.text(dialogDescription,NamedTextColor.WHITE))))
.build())
.type(DialogType.multiAction(actions).build()));
s.connection.getAudience().showDialog(dialog);
}
private void scheduleRender(Session s,String message){
Bukkit.getScheduler().runTask(this,()->{if(pending.get(s.uuid)==s && !s.result.isDone())render(s,message);});
}
@EventHandler
public void click(PlayerCustomClickEvent event){
Key key=event.getIdentifier();
String id=key.asString();
if(!id.startsWith("unifiedauth:"))return;
if(!(event.getCommonConnection() instanceof PlayerConfigurationConnection c))return;
UUID uuid=c.getProfile().getId();
if(uuid==null)return;
Session s=pending.get(uuid);
if(s==null || s.result.isDone())return;
if(s.processing.get())return;
String action=id.substring("unifiedauth:".length());
String message=null;
boolean verify=false;
String submitted=null;
Stage submittedStage=null;
synchronized(s){
long now=System.currentTimeMillis();
if(now-s.lastInput<75)return;
s.lastInput=now;
if(action.startsWith("digit") && action.length()==6){
char digit=action.charAt(5);
if(digit>='0' && digit<='9' && s.entry.length()<pinLength)s.entry.append(digit);
} else if(action.equals("back")){
if(!s.entry.isEmpty())s.entry.setLength(s.entry.length()-1);
} else if(action.equals("remember")){
if(rememberEnabled && s.clientIp!=null)s.rememberRequested=!s.rememberRequested;
} else if(action.equals("submit")){
if(s.entry.length()!=pinLength){message="Enter all "+pinLength+" digits."; }
else {
String pin=s.entry.toString();
s.entry.setLength(0);
if(s.stage==Stage.REGISTER){
s.firstPin=pin;
s.stage=Stage.CONFIRM;
message="Repeat PIN to confirm.";
}else if(s.stage==Stage.CONFIRM){
if(!pin.equals(s.firstPin)){
s.firstPin=null;
s.stage=Stage.REGISTER;
s.attempts++;
message="PINs did not match. Start again.";
if(s.attempts>=maxAttempts){s.result.complete(false);return;}
}else{
s.firstPin=null;
submitted=pin;
submittedStage=Stage.CONFIRM;
verify=true;
}
}else{
submitted=pin;
submittedStage=Stage.LOGIN;
verify=true;
}
}
} else return;
}
if(verify){
if(!s.processing.compareAndSet(false,true))return;
final String pin=submitted;
final Stage stage=submittedStage;
final boolean trustOnSuccess=s.rememberRequested && rememberEnabled && s.clientIp!=null;
try{
workers.execute(()->{
boolean ok=false;
try {
ok=stage==Stage.LOGIN?accounts.authenticate(s.name,pin):accounts.register(s.name,pin);
} catch(Exception failure){getLogger().warning("Account storage or verification failed; access denied. No PIN details logged.");}
if(ok && trustOnSuccess){
try {
rememberedIps.trust(s.name,s.uuid,s.clientIp,System.currentTimeMillis());
} catch(IOException cannotSave) {
getLogger().warning("PIN verified but optional IP remember could not be saved. Regular PIN authentication is still required next time.");
}
}
boolean success=ok;
Bukkit.getScheduler().runTask(this,()->{
s.processing.set(false);
if(!pending.containsKey(s.uuid) || s.result.isDone())return;
if(success){
if(stage==Stage.LOGIN)totalLogins++;else totalRegistrations++;
s.result.complete(true);
return;
}
s.attempts++;
if(s.attempts>=maxAttempts){s.result.complete(false);return;}
if(stage==Stage.CONFIRM)s.stage=Stage.LOGIN;
render(s,"Incorrect PIN or account already exists. Attempts left: "+(maxAttempts-s.attempts));
});
});
} catch(RejectedExecutionException full) {
s.processing.set(false);
s.result.complete(false);
}
}else scheduleRender(s,message);
}
@EventHandler
public void close(PlayerConnectionCloseEvent event){
Session s=pending.remove(event.getPlayerUniqueId());
if(s!=null)s.result.complete(false);
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
if(args.length>0 && args[0].equalsIgnoreCase("remember")){
if(!(sender instanceof Player player)){
sender.sendMessage("Only authenticated players may change their remembered IP.");
return true;
}
if(!rememberEnabled || rememberedIps==null){
player.sendMessage("Remember IP is disabled by the server administrator.");
return true;
}
String name=player.getName().toLowerCase(Locale.ROOT);
UUID uuid=player.getUniqueId();
if(!accounts.exists(name)){
player.sendMessage("Account is not registered.");
return true;
}
InetSocketAddress address=player.getConnection().getClientAddress();
String ip=address==null || address.getAddress()==null?null:address.getAddress().getHostAddress();
if(ip==null){
player.sendMessage("Cannot determine your client IP; PIN login remains required.");
return true;
}
String action=args.length>1?args[1].toLowerCase(Locale.ROOT):"status";
switch(action){
case "status" -> {
long expiry=rememberedIps.trustedUntil(name,uuid,ip,System.currentTimeMillis());
player.sendMessage(expiry==0?"This network is not trusted; PIN will be required.":"Remembered network active for "+Math.max(0,(expiry-System.currentTimeMillis()+86399999)/86400000)+" more day(s).");
}
case "on" -> {
try{
workers.execute(()->{
try{
rememberedIps.trust(name,uuid,ip,System.currentTimeMillis());
Bukkit.getScheduler().runTask(this,()->player.sendMessage("Current IP remembered for "+rememberDays+" days. Use /ua remember off to revoke."));
}catch(IOException cannotSave){
Bukkit.getScheduler().runTask(this,()->player.sendMessage("Could not save IP trust. PIN will still be required."));
}
});
}catch(RejectedExecutionException busy){
player.sendMessage("Authentication service is busy. Try later.");
}
}
case "off", "forget" -> {
try{
rememberedIps.forget(name,uuid);
player.sendMessage("Remembered IP revoked. PIN will be required next time.");
}catch(IOException failure){
player.sendMessage("Could not revoke IP trust on disk; ask an administrator to disable remember-ip.enabled immediately.");
}
}
default -> player.sendMessage("Usage: /ua remember [on|off|status]");
}
return true;
}
if(!sender.hasPermission("unifiedauth.admin")){
sender.sendMessage("Usage: /ua remember [on|off|status]");
return true;
}
sender.sendMessage("UnifiedAuth 1.1: accounts="+(accounts==null?0:accounts.count())+" pending="+pending.size()+" registered="+totalRegistrations+" PIN logins="+totalLogins+" trusted-IP logins="+trustedLogins+" remember enabled="+rememberEnabled+" denied="+denials+" ipRateDenied="+rateDenials);
return true;
}
}
