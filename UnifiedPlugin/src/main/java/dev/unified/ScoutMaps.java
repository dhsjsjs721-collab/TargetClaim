package dev.unified;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.util.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import javax.imageio.ImageIO;

final class ScoutMaps implements Listener {
private final UnifiedPlugin host;
private final SeasonStart season;
private final NamespacedKey key,issued;
private MapView mirror,atlas;
private volatile BufferedImage image;
private World world;
private static final String SOURCE="https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRUGSowRQSgr9erWDwqkmdfPD5ZYcZrinbmdFAvtIyO6w&s=10";
private static final int RADIUS=1024;
private static final class Selector implements InventoryHolder{Inventory inv;public Inventory getInventory(){return inv;}}
ScoutMaps(UnifiedPlugin h,SeasonStart season){host=h;this.season=season;key=new NamespacedKey(h,"scout_map");issued=new NamespacedKey(h,"scout_maps_received");}
void enable(){
world=Bukkit.getWorlds().getFirst();
mirror=get("mirror");atlas=get("atlas");
for(MapRenderer r:new ArrayList<>(mirror.getRenderers()))mirror.removeRenderer(r);
mirror.setLocked(true);mirror.setTrackingPosition(false);mirror.addRenderer(new MapRenderer(){public void render(MapView v,MapCanvas c,Player p){BufferedImage picture=image;if(picture!=null)c.drawImage(0,0,picture);}});
atlas.setWorld(world);atlas.setScale(MapView.Scale.FARTHEST);atlas.setCenterX(world.getSpawnLocation().getBlockX());atlas.setCenterZ(world.getSpawnLocation().getBlockZ());atlas.setTrackingPosition(false);
atlas.addRenderer(new MapRenderer(true){public void render(MapView v,MapCanvas canvas,Player p){
MapCursorCollection cursors=new MapCursorCollection();for(Player q:Bukkit.getOnlinePlayers())if(q.getWorld()==world&&(q==p||host.areClanmates(p.getUniqueId(),q.getUniqueId()))){Location l=q.getLocation();mark(cursors,l,v,q==p?MapCursor.Type.PLAYER:MapCursor.Type.BLUE_MARKER,q.getName());}
Location chosen=season.chosen(p);if(chosen!=null&&chosen.getWorld()==world)mark(cursors,chosen,v,MapCursor.Type.RED_MARKER,"Старт клана");canvas.setCursors(cursors);
}});
Bukkit.getPluginManager().registerEvents(this,host);loadImage();host.saveConfig();
for(Player p:Bukkit.getOnlinePlayers())give(p);
}
private MapView get(String name){int id=host.getConfig().getInt("scout-maps."+name,-1);MapView map=id<0?null:Bukkit.getMap(id);if(map==null||map.getWorld()!=world){map=Bukkit.createMap(world);host.getConfig().set("scout-maps."+name,map.getId());}return map;}
private void mark(MapCursorCollection c,Location l,MapView v,MapCursor.Type type,String name){int x=(int)Math.round((l.getX()-v.getCenterX())/8.0),z=(int)Math.round((l.getZ()-v.getCenterZ())/8.0);if(x < -127||x>127||z < -127||z>127)return;c.addCursor(new MapCursor((byte)x,(byte)z,(byte)(Math.floorMod(Math.round(l.getYaw()/22.5f),16)),type,true,Component.text(name)));}
private void loadImage(){Bukkit.getScheduler().runTaskAsynchronously(host,()->{try{
File file=new File(host.getDataFolder(),"mirror-image.png");BufferedImage source;
if(file.isFile())source=ImageIO.read(file);else{
HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
HttpResponse<InputStream> res=client.send(HttpRequest.newBuilder(URI.create(SOURCE)).timeout(Duration.ofSeconds(12)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(InputStream in=res.body()){if(res.statusCode()!=200)throw new IOException("HTTP "+res.statusCode());byte[] data=in.readNBytes(2_000_001);if(data.length>2_000_000)throw new IOException("image too large");try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(data))){var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("not an image");var reader=readers.next();try{reader.setInput(stream);if(reader.getWidth(0)>4096||reader.getHeight(0)>4096)throw new IOException("image dimensions");source=reader.read(0);}finally{reader.dispose();}}} }
if(source==null)throw new IOException("empty image");
BufferedImage result=new BufferedImage(128,128,BufferedImage.TYPE_INT_RGB);Graphics2D g=result.createGraphics();g.setColor(java.awt.Color.BLACK);g.fillRect(0,0,128,128);double scale=Math.min(128.0/source.getWidth(),128.0/source.getHeight());int w=(int)Math.round(source.getWidth()*scale),h=(int)Math.round(source.getHeight()*scale);g.drawImage(source,(128-w)/2,(128-h)/2,w,h,null);g.dispose();ImageIO.write(result,"png",file);image=result;host.getLogger().info("Mirror image loaded.");
}catch(Exception ex){host.getLogger().warning("Mirror image unavailable: "+ex.getMessage()+"; put mirror-image.png in MinecraftUnified data folder.");}});}
private ItemStack item(MapView v,String type,String name){ItemStack i=new ItemStack(Material.FILLED_MAP);MapMeta m=(MapMeta)i.getItemMeta();m.setMapView(v);m.displayName(Component.text(name));m.lore(List.of(Component.text(type.equals("atlas")?"Карта клана · Shift+ПКМ — выбрать старт":"Личная карта")));m.getPersistentDataContainer().set(key,PersistentDataType.STRING,type);i.setItemMeta(m);return i;}
private boolean has(Player p,String type){for(ItemStack i:p.getInventory().getContents())if(i!=null&&i.hasItemMeta()&&type.equals(i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING)))return true;return false;}
void give(Player p){if(p.isOp()&&p.getWorld().getName().equals(TestWorld.WORLD))return;int flags=p.getPersistentDataContainer().getOrDefault(issued,PersistentDataType.INTEGER,0);if((flags&1)==0&&(has(p,"mirror")||p.getInventory().addItem(item(mirror,"mirror","Зеркало")).isEmpty()))flags|=1;if((flags&2)==0&&(has(p,"atlas")||p.getInventory().addItem(item(atlas,"atlas","Карта мира")).isEmpty()))flags|=2;p.getPersistentDataContainer().set(issued,PersistentDataType.INTEGER,flags);}
@EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTask(host,()->{if(e.getPlayer().isOnline())give(e.getPlayer());});}
private boolean atlas(ItemStack i){return i!=null&&i.hasItemMeta()&&"atlas".equals(i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING));}
private boolean leader(Player p){var c=host.claimModule();return season.scouting()&&c!=null&&c.clanLeader(p.getUniqueId());}
@EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||!e.getPlayer().isSneaking()||!atlas(e.getItem())||!season.scouting())return;e.setCancelled(true);Player p=e.getPlayer();if(!leader(p)){p.sendMessage("§cТочку выбирает лидер клана.");return;}if(e.getClickedBlock()!=null){Location l=e.getClickedBlock().getLocation();if(season.choose(p,l))return;}open(p);}
private void open(Player p){Selector h=new Selector();h.inv=Bukkit.createInventory(h,54,"Выбрать место старта · карта 9×6");for(int i=0;i<54;i++){int x=atlas.getCenterX()-RADIUS+(int)((i%9+.5)*2*RADIUS/9),z=atlas.getCenterZ()-RADIUS+(int)((i/9+.5)*2*RADIUS/6);boolean loaded=world.isChunkLoaded(x>>4,z>>4);ItemStack tile=new ItemStack(loaded?Material.GREEN_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE);var m=tile.getItemMeta();m.displayName(Component.text("X "+x+" · Z "+z));m.lore(List.of(Component.text(loaded?"Нажми — выбрать точку":"Сначала исследуй этот сектор")));tile.setItemMeta(m);h.inv.setItem(i,tile);}p.openInventory(h.inv);}
@EventHandler(priority=EventPriority.HIGHEST) public void inventory(InventoryOpenEvent e){if(e.getInventory().getHolder() instanceof Selector&&e.getPlayer() instanceof Player p&&leader(p))e.setCancelled(false);}
@EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Selector))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!leader(p)||e.getRawSlot()<0||e.getRawSlot()>=54)return;int i=e.getRawSlot(),x=atlas.getCenterX()-RADIUS+(int)((i%9+.5)*2*RADIUS/9),z=atlas.getCenterZ()-RADIUS+(int)((i/9+.5)*2*RADIUS/6);if(season.choose(p,new Location(world,x,0,z)))Bukkit.getScheduler().runTask(host,p::closeInventory);}
@EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Selector)e.setCancelled(true);}
}
