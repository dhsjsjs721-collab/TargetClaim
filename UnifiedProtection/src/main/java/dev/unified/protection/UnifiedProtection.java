package dev.unified.protection;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class UnifiedProtection extends JavaPlugin implements Listener {
private static final class RateWindow {
long openedAt;
int clicks;
RateWindow(long now){openedAt=now;}
}
private final Map<UUID,RateWindow> clickWindows=new HashMap<>();
private long rejectedStacks,rejectedRates,rejectedPortals,scannedItems,seenClicks,cancelledContainerBreaks;
private long lastViolationLog,lastContainerLog;
private boolean inventoryGuard,rejectOverstack,checkHopper,checkHopperPickup;
private boolean rateEnforced,logViolations,fallingPortal,storagePortal,watchCancelledBreak;
private int maxClicks;
private int cleanupTask=-1;

@Override public void onEnable() {
saveDefaultConfig();
configure();
getServer().getPluginManager().registerEvents(this,this);
getLogger().info("UnifiedProtection started. This does not replace Paper engine fixes.");
}

@Override public void onDisable() {
if(cleanupTask!=-1) Bukkit.getScheduler().cancelTask(cleanupTask);
clickWindows.clear();
}

private void configure() {
reloadConfig();
inventoryGuard=getConfig().getBoolean("inventory-guard.enabled",true);
rejectOverstack=getConfig().getBoolean("inventory-guard.reject-overstacked-transactions",true);
checkHopper=getConfig().getBoolean("inventory-guard.validate-hopper-transfers",false);
checkHopperPickup=getConfig().getBoolean("inventory-guard.validate-hopper-pickups",false);
maxClicks=Math.max(20,Math.min(10000,getConfig().getInt("inventory-guard.clicks-per-second-limit",240)));
rateEnforced=getConfig().getBoolean("inventory-guard.enforce-click-rate-limit",true);
logViolations=getConfig().getBoolean("inventory-guard.log-violations",true);
fallingPortal=getConfig().getBoolean("portal-guard.block-falling-block-portals",true);
storagePortal=getConfig().getBoolean("portal-guard.block-storage-vehicle-portals",false);
watchCancelledBreak=getConfig().getBoolean("paper-settings-audit.warn-cancelled-container-break",false);
clickWindows.clear();
if(cleanupTask!=-1)Bukkit.getScheduler().cancelTask(cleanupTask);
long period=Math.max(200,Math.min(12000,getConfig().getLong("maintenance.cleanup-period-ticks",1200)));
cleanupTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(this,()->{
long now=System.currentTimeMillis();
clickWindows.entrySet().removeIf(e->now-e.getValue().openedAt>120000 || Bukkit.getPlayer(e.getKey())==null);
},period,period);
if(getConfig().getBoolean("paper-settings-audit.enabled",true)){
for(String line:PaperSettingsAudit.inspect(this))getLogger().warning(line);
}
}

private boolean invalid(ItemStack stack) {
if(stack==null || stack.getType().isAir())return false;
try {
int max=stack.getMaxStackSize();
return stack.getAmount()<1 || max<1 || stack.getAmount()>max;
} catch(RuntimeException problem) {
report("Item validation error (fail open): "+problem.getClass().getSimpleName());
return false;
}
}

private void report(String message) {
if(!logViolations)return;
long now=System.currentTimeMillis();
if(now-lastViolationLog>=10000){
lastViolationLog=now;
getLogger().warning(message);
}
}

private boolean throttle(Player player) {
long now=System.currentTimeMillis();
RateWindow window=clickWindows.computeIfAbsent(player.getUniqueId(),id->new RateWindow(now));
if(now-window.openedAt>=1000 || now<window.openedAt){
window.openedAt=now;
window.clicks=0;
}
window.clicks++;
if(window.clicks<=maxClicks)return false;
if(window.clicks==maxClicks+1)report("Excessive inventory clicks from "+player.getName()+" (>"+maxClicks+"/second)");
if(rateEnforced) {rejectedRates++;return true;}
return false;
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void click(InventoryClickEvent e){
if(!inventoryGuard || !(e.getWhoClicked() instanceof Player player))return;
seenClicks++;
if(throttle(player)){e.setCancelled(true);return;}
if(!rejectOverstack)return;
boolean bad=invalid(e.getCurrentItem()) || invalid(e.getCursor());
int hotbar=e.getHotbarButton();
if(hotbar>=0 && hotbar<9)bad |= invalid(player.getInventory().getItem(hotbar));
if(bad){
e.setCancelled(true);
rejectedStacks++;
report("Blocked inventory operation with invalid stack for "+player.getName());
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void drag(InventoryDragEvent e){
if(!inventoryGuard || !rejectOverstack)return;
if(invalid(e.getOldCursor()) || invalid(e.getCursor())) {
e.setCancelled(true);rejectedStacks++;return;
}
for(ItemStack stack:e.getNewItems().values()) {
if(invalid(stack)){e.setCancelled(true);rejectedStacks++;return;}
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void move(InventoryMoveItemEvent e){
if(inventoryGuard && rejectOverstack && checkHopper && invalid(e.getItem())){
e.setCancelled(true);rejectedStacks++;
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void pickupHopper(InventoryPickupItemEvent e){
if(inventoryGuard && rejectOverstack && checkHopperPickup && invalid(e.getItem().getItemStack())){
e.setCancelled(true);rejectedStacks++;
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void playerPickup(EntityPickupItemEvent e){
if(!inventoryGuard || !rejectOverstack || !(e.getEntity() instanceof Player))return;
if(invalid(e.getItem().getItemStack())){
e.setCancelled(true);rejectedStacks++;
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void playerDrop(PlayerDropItemEvent e){
if(inventoryGuard && rejectOverstack && invalid(e.getItemDrop().getItemStack())){
e.setCancelled(true);rejectedStacks++;
}
}

@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
public void portal(EntityPortalEvent e){
if((fallingPortal && e.getEntity() instanceof FallingBlock)
|| (storagePortal && e.getEntity() instanceof InventoryHolder)){
e.setCancelled(true);
rejectedPortals++;
}
}

@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
public void canceledBreak(BlockBreakEvent e){
if(!watchCancelledBreak || !e.isCancelled())return;
if(!(e.getBlock().getState() instanceof org.bukkit.block.Container))return;
cancelledContainerBreaks++;
long now=System.currentTimeMillis();
if(now-lastContainerLog>=60000){
lastContainerLog=now;
getLogger().warning("Container break cancelled. Engine-level cancellation dupes, if present, cannot safely be repaired by this listener.");
}
}

@EventHandler public void quit(PlayerQuitEvent e){
clickWindows.remove(e.getPlayer().getUniqueId());
}

@Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
if(args.length==0 || args[0].equalsIgnoreCase("status")){
sender.sendMessage("UnifiedProtection 1.0.0 | inventory="+inventoryGuard+" | clickChecks="+seenClicks+" | badStacks="+rejectedStacks+" | rateDrops="+rejectedRates+" | portalDrops="+rejectedPortals+" | trackedPlayers="+clickWindows.size()+" | scannedItems="+scannedItems);
return true;
}
if(args[0].equalsIgnoreCase("audit")){
for(String message:PaperSettingsAudit.inspect(this))sender.sendMessage(message);
return true;
}
if(args[0].equalsIgnoreCase("reload")){
configure();sender.sendMessage("UnifiedProtection settings reloaded.");return true;
}
if(args[0].equalsIgnoreCase("scan")){
if(args.length<2){sender.sendMessage("Usage: /uprotect scan <player>");return true;}
Player target=Bukkit.getPlayerExact(args[1]);
if(target==null){sender.sendMessage("Player is offline.");return true;}
int invalidStacks=0,total=0;
for(ItemStack item:target.getInventory().getContents()){
if(item==null || item.getType().isAir())continue;
total++;
if(invalid(item))invalidStacks++;
}
scannedItems+=total;
sender.sendMessage("Inspected "+total+" inventory items for "+target.getName()+", invalid stacks="+invalidStacks+". No items changed.");
return true;
}
sender.sendMessage("Usage: /uprotect [status|audit|reload|scan <player>]");
return true;
}
}
