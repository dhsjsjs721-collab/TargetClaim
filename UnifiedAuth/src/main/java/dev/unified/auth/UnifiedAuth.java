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
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnifiedAuth extends JavaPlugin implements Listener {
private enum Stage { LOGIN, REGISTER, CONFIRM }
private static final class Session {
final UUID uuid;
final String name;
final PlayerConfigurationConnection connection;
final CompletableFuture<Boolean> result=new CompletableFuture<>();
final StringBuilder entry=new StringBuilder(12);
final AtomicBoolean processing=new AtomicBoolean();
Stage stage;
String firstPin;
int attempts;
long lastInput;
Session(UUID uuid,String name,PlayerConfigurationConnection conn,Stage stage){
this.uuid=uuid;this.name=name;this.connection=conn;this.stage=stage;
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
private int pinLength,maxAttempts,maxPending,timeoutSeconds,ipLimit,maxTracked;
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
Bukkit.getPluginManager().disablePlugin(this);
return;
}
workers=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{
Thread t=new Thread(r,"UnifiedAuth-crypto");
t.setDaemon(true);
return t;
},new ThreadPoolExecutor.AbortPolicy());
Bukkit.getPluginManager().registerEvents(this,this);
cleanupTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
long now=System.currentTimeMillis();
connections.entrySet().removeIf(e->now-e.getValue().opened>120000);
},1200L,1200L);
getLogger().info("Pre-world masked PIN auth enabled; "+accounts.count()+" stored accounts. No player world loaded before PIN verification.");
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
if(!antiBot)return;
InetAddress addr=event.getAddress();
if(addr==null)return;
String ip=addr.getHostAddress();
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
UUID uuid=connection.getProfile().getId();
String username=connection.getProfile().getName();
if(uuid==null || username==null || !username.matches("[A-Za-z0-9_]{3,16}")){
connection.disconnect(Component.text("Invalid player profile.",NamedTextColor.RED));
return;
}
if(pending.size()>=maxPending){
denials++;
connection.disconnect(Component.text("Authentication queue full. Try again.",NamedTextColor.RED));
return;
}
String name=username.toLowerCase(Locale.ROOT);
Session s=new Session(uuid,name,connection,accounts.exists(name)?Stage.LOGIN:Stage.REGISTER);
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
List<ActionButton> actions=new ArrayList<>(12);
for(int i=1;i<=9;i++)actions.add(button(Integer.toString(i),"digit"+i));
actions.add(button("⌫","back"));
actions.add(button("0","digit0"));
actions.add(button("OK","submit"));
Dialog dialog=Dialog.create(builder->builder.empty()
.base(DialogBase.builder(Component.text("UnifiedAuth • Before World",NamedTextColor.AQUA))
.canCloseWithEscape(false)
.body(List.of(DialogBody.plainMessage(Component.text(description,NamedTextColor.WHITE))))
.build())
.type(DialogType.multiAction(actions)));
s.connection.getAudience().showDialog(dialog);
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
if(s==null || s.connection!=c || s.result.isDone())return;
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
try{
workers.execute(()->{
boolean ok=false;
try {
ok=stage==Stage.LOGIN?accounts.authenticate(s.name,pin):accounts.register(s.name,pin);
} catch(Exception failure){getLogger().warning("Account storage or verification failed; access denied. No PIN details logged.");}
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
}else render(s,message);
}
@EventHandler
public void close(PlayerConnectionCloseEvent event){
Session s=pending.remove(event.getPlayerUniqueId());
if(s!=null)s.result.complete(false);
}
@Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
sender.sendMessage("UnifiedAuth: accounts="+(accounts==null?0:accounts.count())+" pending="+pending.size()+" registered="+totalRegistrations+" successfulLogins="+totalLogins+" denied="+denials+" ipRateDenied="+rateDenials);
return true;
}
}
