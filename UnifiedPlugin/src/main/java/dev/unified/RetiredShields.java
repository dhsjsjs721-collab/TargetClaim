package dev.unified;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

final class RetiredShields implements Listener {
private final UnifiedPlugin plugin;
private static final NamespacedKey KEY=new NamespacedKey("minecraftunified","combat_shield");
private static final Set<String> TYPES=Set.of("wither","turtle","crusher");
RetiredShields(UnifiedPlugin plugin){this.plugin=plugin;}
void enable(){
for(String k:TYPES)Bukkit.removeRecipe(new NamespacedKey("minecraftunified","shield_"+k));
Bukkit.getPluginManager().registerEvents(this,plugin);
for(Player p:Bukkit.getOnlinePlayers())clean(p);
for(World w:Bukkit.getWorlds())for(Chunk c:w.getLoadedChunks()){cleanChunk(c);for(Entity e:c.getEntities())cleanEntity(e);}
plugin.getLogger().info("Special shields retired; vanilla shields preserved.");
}
private void clean(Player p){clean(p.getInventory(),0);clean(p.getEnderChest(),0);}
private boolean normalize(ItemStack i,int depth){
if(i==null||!i.hasItemMeta()||depth>8)return false;
ItemMeta meta=i.getItemMeta();
String type=meta.getPersistentDataContainer().get(KEY,PersistentDataType.STRING);
if(i.getType()==Material.SHIELD&&type!=null&&TYPES.contains(type)){
meta.getPersistentDataContainer().remove(KEY);meta.setItemModel(null);meta.displayName(null);meta.lore(null);
if(meta instanceof org.bukkit.inventory.meta.Damageable d&&d.hasMaxDamage()){
int oldMax=d.getMaxDamage();int oldDamage=d.getDamage();
d.setMaxDamage(null);
d.setDamage(Math.min(Material.SHIELD.getMaxDurability()-1,(int)Math.round(oldDamage*(double)Material.SHIELD.getMaxDurability()/Math.max(1,oldMax))));
}
i.setItemMeta(meta);return true;
}
if(meta instanceof BlockStateMeta b&&b.getBlockState() instanceof ShulkerBox box){
if(clean(box.getInventory(),depth+1)){b.setBlockState(box);i.setItemMeta(b);return true;}
}
if(meta instanceof BundleMeta b){List<ItemStack> items=new ArrayList<>(b.getItems());boolean changed=false;for(ItemStack child:items)changed|=normalize(child,depth+1);if(changed){b.setItems(items);i.setItemMeta(b);return true;}}
return false;
}
private boolean clean(Inventory inv,int depth){boolean changed=false;for(int slot=0;slot<inv.getSize();slot++){ItemStack item=inv.getItem(slot);if(normalize(item,depth)){inv.setItem(slot,item);changed=true;}}return changed;}
private void cleanChunk(Chunk c){for(var state:c.getTileEntities())if(state instanceof Container container)clean(container.getInventory(),0);}
private void cleanEntity(Entity e){
if(e instanceof Item i){ItemStack stack=i.getItemStack();if(normalize(stack,0))i.setItemStack(stack);}
else if(e instanceof ItemDisplay d){ItemStack stack=d.getItemStack();String type=stack.hasItemMeta()?stack.getItemMeta().getPersistentDataContainer().get(KEY,PersistentDataType.STRING):null;if(type!=null&&TYPES.contains(type))d.remove();}
else if(e instanceof InventoryHolder h)clean(h.getInventory(),0);
else if(e instanceof LivingEntity l&&l.getEquipment()!=null){for(EquipmentSlot slot:List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND)){ItemStack item=l.getEquipment().getItem(slot);if(normalize(item,0))l.getEquipment().setItem(slot,item);}}
}
@EventHandler public void join(PlayerJoinEvent e){clean(e.getPlayer());}
@EventHandler(priority=EventPriority.LOWEST) public void open(InventoryOpenEvent e){clean(e.getInventory(),0);}
@EventHandler public void chunk(ChunkLoadEvent e){cleanChunk(e.getChunk());}
@EventHandler public void entities(EntitiesLoadEvent e){for(Entity entity:e.getEntities())cleanEntity(entity);}
}
