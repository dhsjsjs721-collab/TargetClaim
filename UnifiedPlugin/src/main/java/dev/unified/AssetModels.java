package dev.unified;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

final class AssetModels implements Listener {
private final UnifiedPlugin host;
AssetModels(UnifiedPlugin host){this.host=host;}
private static NamespacedKey k(String ns,String key){return new NamespacedKey(ns,key);}
private static boolean flag(org.bukkit.persistence.PersistentDataContainer p,String ns,String key){return Byte.valueOf((byte)1).equals(p.get(k(ns,key),PersistentDataType.BYTE));}
private static NamespacedKey model(ItemStack stack){
if(stack==null||!stack.hasItemMeta())return null;
var p=stack.getItemMeta().getPersistentDataContainer();
return switch(stack.getType()){
case TARGET -> flag(p,"targetclaim","claim_target")?k("targetclaim",flag(p,"targetclaim","independent_claim")?"independent_core":"claim_core"):null;
case ECHO_SHARD -> flag(p,"minecraftunified","obsidian_trap")?k("minecraftunified","obsidian_trap"):flag(p,"enchantmentforge","inventory_charm")?k("enchantmentforge","return_charm"):null;
case ENCHANTED_BOOK -> {String id=p.get(k("enchantmentforge","enchanted_book_kind"),PersistentDataType.STRING);yield "soulbound".equals(id)||"triple_shot".equals(id)?k("enchantmentforge",id):null;}
default -> stack.getType().name().startsWith("MUSIC_DISC_")&&p.has(k("minecraftunified","youtube_disc"),PersistentDataType.STRING)?k("minecraftunified","youtube_disc"):null;
};
}
private static boolean apply(ItemStack stack){
NamespacedKey key=model(stack);if(key==null)return false;
var m=stack.getItemMeta();if(key.equals(m.getItemModel()))return false;
m.setItemModel(key);stack.setItemMeta(m);return true;
}
private static void inventory(Inventory inv){for(int slot=0;slot<inv.getSize();slot++){ItemStack i=inv.getItem(slot);if(apply(i))inv.setItem(slot,i);}}
void enable(){Bukkit.getPluginManager().registerEvents(this,host);Bukkit.getScheduler().runTaskTimer(host,()->{for(Player p:Bukkit.getOnlinePlayers())inventory(p.getInventory());},20L,100L);host.getLogger().info("AssetModels enabled: namespaced models, legacy tagged-item migration.");}
@EventHandler public void join(PlayerJoinEvent e){inventory(e.getPlayer().getInventory());inventory(e.getPlayer().getEnderChest());}
@EventHandler public void open(InventoryOpenEvent e){inventory(e.getInventory());}
@EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){ItemStack i=e.getItem().getItemStack();if(apply(i))e.getItem().setItemStack(i);}
}
