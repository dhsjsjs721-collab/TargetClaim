package dev.unified;

import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.command.CommandSender;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;

public final class Shopkeepers implements Listener {
private final UnifiedPlugin host;
private final NamespacedKey token=new NamespacedKey("minecraftunified","shop_token");
private final NamespacedKey marker=new NamespacedKey("minecraftunified","shop_entity");
private final Map<UUID,Shop> shops=new LinkedHashMap<>();
private final Map<UUID,UUID> selected=new HashMap<>();
private final Map<UUID,Long> skinRequests=new HashMap<>();
private final File file;
private long tick;
private static final class Shop {
UUID id,owner; Location home; String name="&6Торговец",skin="",model="classic",style="normal";
ItemStack[] stock=new ItemStack[3],money=new ItemStack[3];
int[] diamonds={2,2,2},emeralds={5,5,5},quantity={1,1,1};
Mannequin npc; Villager walker; Location chest; boolean returning; long deadline,nextTrip;
}
private static final class Menu implements InventoryHolder {
final UUID id; final int edit; final boolean owner; Inventory inv;
Menu(UUID id,boolean owner,int edit){this.id=id;this.owner=owner;this.edit=edit;}
public Inventory getInventory(){return inv;}
}
Shopkeepers(UnifiedPlugin host){this.host=host;file=new File(host.getDataFolder(),"shops.yml");}
static ItemStack token(){ItemStack i=new ItemStack(Material.NAME_TAG);var m=i.getItemMeta();m.setDisplayName("§6§lЛичный торговец");m.setLore(List.of("§7ПКМ по блоку в своём привате","§7Личная лавка с автоматическим сбором выручки"));m.getPersistentDataContainer().set(new NamespacedKey("minecraftunified","shop_token"),PersistentDataType.BYTE,(byte)1);i.setItemMeta(m);return i;}
public static MerchantRecipe offer(){MerchantRecipe r=new MerchantRecipe(token(),1);r.setPriceMultiplier(0);r.setDemand(0);r.setSpecialPrice(0);r.addIngredient(new ItemStack(Material.DIAMOND,32));r.setExperienceReward(false);return r;}
void enable(){
YamlConfiguration y=YamlConfiguration.loadConfiguration(file);
var section=y.getConfigurationSection("shops");
if(section!=null)for(String id:section.getKeys(false))try{
String p="shops."+id+".";Shop s=new Shop();s.id=UUID.fromString(id);s.owner=UUID.fromString(y.getString(p+"owner"));s.home=y.getLocation(p+"home");if(s.home==null)continue;
s.name=y.getString(p+"name","&6Торговец");s.skin=y.getString(p+"skin","");s.model=y.getString(p+"model","classic");s.style=y.getString(p+"style","normal");
for(int i=0;i<3;i++){s.stock[i]=y.getItemStack(p+"stock."+i);s.money[i]=y.getItemStack(p+"money."+i);s.diamonds[i]=Math.max(0,Math.min(64,y.getInt(p+"diamonds."+i,2)));s.emeralds[i]=Math.max(0,Math.min(64,y.getInt(p+"emeralds."+i,5)));s.quantity[i]=Math.max(1,Math.min(64,y.getInt(p+"quantity."+i,1)));}
shops.put(s.id,s);
}catch(Exception ex){host.getLogger().warning("Shop record invalid: "+id+" "+ex.getMessage());}
Bukkit.getPluginManager().registerEvents(this,host);
Bukkit.getScheduler().runTaskTimer(host,this::update,20L,5L);
host.getLogger().info("Personal shops enabled: "+shops.size());
}
void disable(){for(Shop s:shops.values())removeEntities(s);}
private boolean save(){
try{YamlConfiguration y=new YamlConfiguration();for(Shop s:shops.values()){
String p="shops."+s.id+".";y.set(p+"owner",s.owner.toString());y.set(p+"home",s.home);y.set(p+"name",s.name);y.set(p+"skin",s.skin);y.set(p+"model",s.model);y.set(p+"style",s.style);
for(int i=0;i<3;i++){y.set(p+"stock."+i,s.stock[i]);y.set(p+"money."+i,s.money[i]);y.set(p+"diamonds."+i,s.diamonds[i]);y.set(p+"emeralds."+i,s.emeralds[i]);y.set(p+"quantity."+i,s.quantity[i]);}
}Path target=file.toPath(),temp=target.resolveSibling(file.getName()+".tmp");y.save(temp.toFile());try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}return true;
}catch(Exception ex){host.getLogger().severe("Shop save failed: "+ex);return false;}
}
private boolean owns(UUID owner,Location l){var claim=host.claimModule();return claim!=null&&claim.ownsShopLocation(owner,l);}
private boolean loaded(Location l){return l!=null&&l.getWorld()!=null&&l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4);}
private boolean near(Player p,Shop s){return s.npc!=null&&s.npc.isValid()&&p.getWorld().equals(s.npc.getWorld())&&p.getLocation().distanceSquared(s.npc.getLocation())<=36;}
private boolean empty(ItemStack i){return i==null||i.getType().isAir()||i.getAmount()<=0;}
private ItemStack[] copy(ItemStack[] a){ItemStack[] b=new ItemStack[a.length];for(int i=0;i<a.length;i++)if(!empty(a[i]))b[i]=a[i].clone();return b;}
private ItemStack[] insert(ItemStack[] input,ItemStack item){ItemStack[] a=copy(input);int remaining=item.getAmount();for(int i=0;i<a.length;i++)if(!empty(a[i])&&a[i].isSimilar(item)){int x=Math.min(remaining,Math.max(0,a[i].getMaxStackSize()-a[i].getAmount()));a[i].setAmount(a[i].getAmount()+x);remaining-=x;}
for(int i=0;i<a.length&&remaining>0;i++)if(empty(a[i])){int x=Math.min(remaining,item.getMaxStackSize());a[i]=item.clone();a[i].setAmount(x);remaining-=x;}return remaining==0?a:null;}
private ItemStack[] pay(ItemStack[] input,Material material,int price){ItemStack[] a=copy(input);ItemStack plain=new ItemStack(material);for(int i=0;i<a.length&&price>0;i++)if(!empty(a[i])&&a[i].isSimilar(plain)){int x=Math.min(price,a[i].getAmount());a[i].setAmount(a[i].getAmount()-x);price-=x;if(empty(a[i]))a[i]=null;}return price==0?a:null;}
private void say(Player p,String msg){p.sendMessage("§6Лавка §8» §r"+msg);}
private UUID entityId(Entity e){String s=e.getPersistentDataContainer().get(marker,PersistentDataType.STRING);if(s==null)return null;try{return UUID.fromString(s);}catch(Exception ignored){return null;}}
private void tag(LivingEntity e,Shop s){e.getPersistentDataContainer().set(marker,PersistentDataType.STRING,s.id.toString());e.setPersistent(false);e.setInvulnerable(true);e.setSilent(true);e.setCollidable(false);e.setRemoveWhenFarAway(false);e.setCanPickupItems(false);}
private Component title(Shop s){Component c=LegacyComponentSerializer.legacyAmpersand().deserialize(s.name);return c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD,s.style.equals("bold")).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,s.style.equals("italic"));}
private void applyAppearance(Shop s){
if(s.npc==null||!s.npc.isValid())return;
s.npc.customName(title(s));s.npc.setCustomNameVisible(true);s.npc.setDescription(Component.text("Личная лавка"));
if(s.skin.isBlank()){s.npc.setProfile(Mannequin.defaultProfile());return;}
try{PlayerProfile profile=Bukkit.createProfile(s.id,"Merchant");var textures=profile.getTextures();textures.setSkin(URI.create(s.skin).toURL(),s.model.equals("slim")?PlayerTextures.SkinModel.SLIM:PlayerTextures.SkinModel.CLASSIC);profile.setTextures(textures);s.npc.setProfile(ResolvableProfile.resolvableProfile(profile));}catch(Exception ex){host.getLogger().warning("Shop skin invalid: "+s.id);}
}
private void spawn(Shop s){
if(!loaded(s.home)||s.npc!=null&&s.npc.isValid())return;
s.npc=s.home.getWorld().spawn(s.home,Mannequin.class,e->{tag(e,s);e.setImmovable(true);e.setGravity(false);});
applyAppearance(s);
}
private void removeEntities(Shop s){if(s.walker!=null)s.walker.remove();if(s.npc!=null)s.npc.remove();s.walker=null;s.npc=null;s.chest=null;s.returning=false;}
@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void place(PlayerInteractEvent e){
if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||e.getClickedBlock()==null)return;
ItemStack hand=e.getItem();if(empty(hand)||!hand.getItemMeta().getPersistentDataContainer().has(token,PersistentDataType.BYTE))return;
e.setCancelled(true);Player p=e.getPlayer();Location l=e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5,0,.5);l.setYaw(p.getLocation().getYaw()+180);
if(!owns(p.getUniqueId(),l)||!l.getBlock().isPassable()||!l.clone().add(0,1,0).getBlock().isPassable()||!l.clone().add(0,-1,0).getBlock().getType().isSolid()){say(p,"§cНужен свободный участок в своём привате.");return;}
if(shops.values().stream().filter(s->s.owner.equals(p.getUniqueId())).count()>=3){say(p,"§cЛимит: три торговца.");return;}
for(Shop s:shops.values())if(s.home.getWorld().equals(l.getWorld())&&s.home.distanceSquared(l)<4){say(p,"§cЗдесь уже есть торговец.");return;}
Shop s=new Shop();s.id=UUID.randomUUID();s.owner=p.getUniqueId();s.home=l;shops.put(s.id,s);if(!save()){shops.remove(s.id);say(p,"§cОшибка сохранения.");return;}
hand.setAmount(hand.getAmount()-1);spawn(s);say(p,"§aЛавка создана. Shift+ПКМ — управление.");
}
@EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent e){
UUID id=entityId(e.getRightClicked());if(id==null)return;e.setCancelled(true);if(e.getHand()!=EquipmentSlot.HAND)return;
Shop s=shops.get(id);if(s==null||!near(e.getPlayer(),s))return;
if(s.walker!=null){say(e.getPlayer(),"§eТорговец переносит выручку.");return;}
if(!owns(s.owner,s.home)){say(e.getPlayer(),"§cЛавка приостановлена: приват утрачен.");return;}
Player p=e.getPlayer();boolean owner=s.owner.equals(p.getUniqueId());if(owner)selected.put(p.getUniqueId(),id);open(p,s,owner&&p.isSneaking(),-1);
}
@EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent e){if(entityId(e.getEntity())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&entityId(e.getTarget())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void leash(PlayerLeashEntityEvent e){if(entityId(e.getEntity())!=null)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void portal(EntityPortalEvent e){if(entityId(e.getEntity())!=null)e.setCancelled(true);}
@EventHandler public void quit(PlayerQuitEvent e){selected.remove(e.getPlayer().getUniqueId());skinRequests.remove(e.getPlayer().getUniqueId());}
private ItemStack icon(Material m,String name,String... lore){ItemStack i=new ItemStack(m);var im=i.getItemMeta();im.setDisplayName(name);im.setLore(Arrays.asList(lore));i.setItemMeta(im);return i;}
private ItemStack preview(ItemStack stack,String... lore){if(empty(stack))return icon(Material.GRAY_STAINED_GLASS_PANE,"§7Пусто",lore);ItemStack i=stack.clone();var m=i.getItemMeta();List<String> list=m.hasLore()?new ArrayList<>(m.getLore()):new ArrayList<>();list.addAll(Arrays.asList(lore));m.setLore(list);i.setItemMeta(m);return i;}
private void open(Player p,Shop s,boolean owner,int edit){Menu menu=new Menu(s.id,owner,edit);menu.inv=Bukkit.createInventory(menu,27,owner?"Управление лавкой":"Личная лавка");fill(menu,s,p);p.openInventory(menu.inv);}
private void fill(Menu m,Shop s,Player p){Inventory inv=m.inv;inv.clear();
if(!m.owner){for(int i=0;i<3;i++){inv.setItem(2+i*2,preview(s.stock[i],"§7За покупку: "+s.quantity[i]+" шт."));if(s.diamonds[i]>0)inv.setItem(11+i*2,icon(Material.DIAMOND,"§b"+s.diamonds[i]+" алм.", "§7Купить "+s.quantity[i]+" шт."));if(s.emeralds[i]>0)inv.setItem(20+i*2,icon(Material.EMERALD,"§a"+s.emeralds[i]+" изумр.","§7Купить "+s.quantity[i]+" шт."));}if(s.owner.equals(p.getUniqueId()))inv.setItem(26,icon(Material.COMPARATOR,"§eНастройки"));return;}
if(m.edit<0){for(int i=0;i<3;i++){inv.setItem(2+i*2,preview(s.stock[i],"§7Настроить товар "+(i+1)));inv.setItem(11+i*2,preview(s.money[i],"§7ПКМ/ЛКМ: забрать выручку"));}inv.setItem(18,icon(Material.NAME_TAG,"§eИмя и цвет","§7/u shop name &6Имя"));inv.setItem(19,icon(Material.PLAYER_HEAD,"§eСкин","§7/u shop skin self","§7/u shop skin <ник или ссылка>"));inv.setItem(20,icon(Material.WRITABLE_BOOK,"§eСтиль","§7/u shop style normal|bold|italic"));inv.setItem(26,icon(Material.CHEST,"§eСундук","§7Автопоиск в 35 блоках","§7Только внутри своего привата"));return;}
int i=m.edit;inv.setItem(4,preview(s.stock[i],"§7Возьми товар на курсор и нажми сюда","§7ПКМ пустым курсором — забрать"));
inv.setItem(10,icon(Material.DIAMOND,"§bЦена: "+s.diamonds[i],"§7Нажми с алмазами на курсоре","§7ПКМ без предмета — отключить"));
inv.setItem(12,icon(Material.EMERALD,"§aЦена: "+s.emeralds[i],"§7Нажми с изумрудами на курсоре","§7ПКМ без предмета — отключить"));
inv.setItem(14,icon(Material.PAPER,"§eКоличество за покупку: "+s.quantity[i],"§7ЛКМ +1, ПКМ -1"));
inv.setItem(18,icon(Material.ARROW,"§7Назад"));
}
private void refresh(Shop s){for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m&&m.id.equals(s.id))fill(m,s,p);}
@EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
@EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
if(!(e.getView().getTopInventory().getHolder() instanceof Menu m)||!(e.getWhoClicked() instanceof Player p))return;
if(e.getRawSlot()>=27&&m.owner&&m.edit>=0&&(e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT)){Shop editShop=shops.get(m.id);if(editShop!=null&&editShop.owner.equals(p.getUniqueId())&&near(p,editShop)&&editShop.walker==null&&owns(editShop.owner,editShop.home))return;}
e.setCancelled(true);if(e.getRawSlot()<0||e.getRawSlot()>=27||!(e.getClick()==ClickType.LEFT||e.getClick()==ClickType.RIGHT))return;
Shop s=shops.get(m.id);if(s==null||!near(p,s)||s.walker!=null||!owns(s.owner,s.home)){Bukkit.getScheduler().runTask(host,()->p.closeInventory());return;}
int slot=e.getRawSlot();
if(!m.owner){if(slot==26&&s.owner.equals(p.getUniqueId())){Bukkit.getScheduler().runTask(host,()->open(p,s,true,-1));return;}for(int i=0;i<3;i++){if(slot==11+i*2)buy(p,s,i,Material.DIAMOND,s.diamonds[i]);if(slot==20+i*2)buy(p,s,i,Material.EMERALD,s.emeralds[i]);}refresh(s);return;}
if(!s.owner.equals(p.getUniqueId()))return;
if(m.edit<0){for(int i=0;i<3;i++){if(slot==2+i*2){int index=i;Bukkit.getScheduler().runTask(host,()->open(p,s,true,index));return;}if(slot==11+i*2){withdraw(p,s,s.money,i);refresh(s);return;}}
if(slot>=18&&slot<=20){say(p,slot==18?"§e/u shop name &6Имя":slot==19?"§e/u shop skin self | <ник> | <ссылка textures.minecraft.net>":"§e/u shop style normal|bold|italic");}return;}
int i=m.edit;ItemStack cursor=e.getCursor();
if(slot==18){Bukkit.getScheduler().runTask(host,()->open(p,s,true,-1));return;}
if(slot==4){if(empty(cursor)){if(e.isRightClick())withdraw(p,s,s.stock,i);}else if(!cursor.getItemMeta().getPersistentDataContainer().has(token,PersistentDataType.BYTE)){
ItemStack old=s.stock[i],next=cursor.clone();int amount=cursor.getAmount();if(!empty(old)){if(!old.isSimilar(cursor)){say(p,"§cСначала забери старый товар.");return;}amount=Math.min(amount,old.getMaxStackSize()-old.getAmount());next.setAmount(old.getAmount()+amount);}if(amount<=0)return;s.stock[i]=next;if(save()){ItemStack rest=cursor.clone();rest.setAmount(cursor.getAmount()-amount);p.setItemOnCursor(empty(rest)?null:rest);}else s.stock[i]=old;
}}
if(slot==10||slot==12){int[] prices=slot==10?s.diamonds:s.emeralds;Material type=slot==10?Material.DIAMOND:Material.EMERALD;int old=prices[i];if(empty(cursor)&&e.isRightClick())prices[i]=0;else if(!empty(cursor)&&cursor.isSimilar(new ItemStack(type)))prices[i]=cursor.getAmount();else return;if(!save())prices[i]=old;}
if(slot==14){int old=s.quantity[i];s.quantity[i]=Math.max(1,Math.min(64,old+(e.isRightClick()?-1:1)));if(!save())s.quantity[i]=old;}
refresh(s);
}
private void withdraw(Player p,Shop s,ItemStack[] source,int i){if(empty(source[i]))return;ItemStack old=source[i];ItemStack[] next=insert(p.getInventory().getStorageContents(),old);if(next==null){say(p,"§cНет места в инвентаре.");return;}source[i]=null;if(!save()){source[i]=old;return;}p.getInventory().setStorageContents(next);}
private void buy(Player p,Shop s,int i,Material currency,int price){
if(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE){say(p,"§cПокупка доступна только в выживании.");return;}
if(price<=0||empty(s.stock[i])||s.stock[i].getAmount()<s.quantity[i]){say(p,"§cНет товара или цена отключена.");return;}
ItemStack[] nextMoney=insert(s.money,new ItemStack(currency,price));if(nextMoney==null){say(p,"§cВыручка заполнена, ожидайте разгрузки.");s.nextTrip=0;return;}
ItemStack[] next=pay(p.getInventory().getStorageContents(),currency,price);if(next==null){say(p,"§cНе хватает оплаты.");return;}
ItemStack product=s.stock[i].clone();product.setAmount(s.quantity[i]);next=insert(next,product);if(next==null){say(p,"§cНет места в инвентаре.");return;}
ItemStack old=s.stock[i];ItemStack[] oldMoney=s.money;s.stock[i]=old.clone();s.stock[i].setAmount(old.getAmount()-product.getAmount());if(empty(s.stock[i]))s.stock[i]=null;s.money=nextMoney;
if(!save()){s.stock[i]=old;s.money=oldMoney;say(p,"§cОшибка сохранения.");return;}
p.getInventory().setStorageContents(next);say(p,"§aПокупка завершена.");
}
private Chest nearest(Shop s){World w=s.home.getWorld();Chest best=null;double distance=35*35;
int x=s.home.getBlockX(),z=s.home.getBlockZ();for(int cx=(x-35)>>4;cx<=(x+35)>>4;cx++)for(int cz=(z-35)>>4;cz<=(z+35)>>4;cz++){
if(!w.isChunkLoaded(cx,cz))continue;for(var state:w.getChunkAt(cx,cz).getTileEntities())if(state instanceof Chest c&&owns(s.owner,c.getLocation())){
double d=s.home.distanceSquared(c.getLocation().add(.5,.5,.5));if(d>distance)continue;for(ItemStack i:s.money)if(!empty(i)&&insert(c.getBlockInventory().getStorageContents(),i)!=null){distance=d;best=c;break;}
}}
return best;}
private Location approach(Chest c,Location from){Location best=null;double d=Double.MAX_VALUE;for(var face:List.of(org.bukkit.block.BlockFace.NORTH,org.bukkit.block.BlockFace.SOUTH,org.bukkit.block.BlockFace.EAST,org.bukkit.block.BlockFace.WEST)){Block b=c.getBlock().getRelative(face);Location l=b.getLocation().add(.5,0,.5);if(!loaded(l)||!b.isPassable()||!b.getRelative(org.bukkit.block.BlockFace.UP).isPassable()||!b.getRelative(org.bukkit.block.BlockFace.DOWN).getType().isSolid())continue;double x=l.distanceSquared(from);if(x<d){d=x;best=l;}}return best;}
private boolean atChest(Chest c,Location from){Location a=approach(c,from);return a!=null&&a.distanceSquared(from)<1.0;}
private void unload(Shop s,Chest c){if(!owns(s.owner,c.getLocation()))return;Inventory inv=c.getBlockInventory();ItemStack[] next=inv.getStorageContents(),old=copy(s.money);boolean changed=false;for(int i=0;i<3;i++)if(!empty(s.money[i])){ItemStack[] result=insert(next,s.money[i]);if(result!=null){next=result;s.money[i]=null;changed=true;}}
if(changed){if(save())inv.setStorageContents(next);else s.money=old;}}
private void finishTrip(Shop s){if(s.walker!=null)s.walker.remove();s.walker=null;s.chest=null;s.returning=false;if(s.npc!=null&&s.npc.isValid()&&loaded(s.home))s.npc.teleport(s.home);s.nextTrip=tick+200;}
private void update(){tick+=5;for(Shop s:shops.values()){
if(!loaded(s.home)){removeEntities(s);continue;}
spawn(s);if(s.npc==null||!s.npc.isValid())continue;
if(!owns(s.owner,s.home)){finishTrip(s);continue;}
if(s.walker!=null){
if(!s.walker.isValid()||tick>s.deadline){finishTrip(s);continue;}
Location current=s.walker.getLocation();if(!loaded(current)||!s.npc.teleport(current)){finishTrip(s);continue;}
if(s.returning){if(current.distanceSquared(s.home)<2.25){finishTrip(s);continue;}if(tick%20==0)s.walker.getPathfinder().moveTo(s.home,1.0);continue;}
if(s.chest==null||!loaded(s.chest)||!(s.chest.getBlock().getState() instanceof Chest c)||!owns(s.owner,c.getLocation())){finishTrip(s);continue;}
if(current.distanceSquared(c.getLocation().add(.5,.5,.5))<6.25&&atChest(c,current)){unload(s,c);s.returning=true;s.deadline=tick+300;s.walker.getPathfinder().moveTo(s.home,1.0);continue;}
if(tick%20==0){Location dest=approach(c,current);if(dest==null){finishTrip(s);continue;}s.walker.getPathfinder().moveTo(dest,1.0);}continue;}
if(tick<s.nextTrip||Arrays.stream(s.money).allMatch(this::empty))continue;s.nextTrip=tick+200;
Chest c=nearest(s);if(c==null)continue;Location dest=approach(c,s.home);if(dest==null)continue;
if(atChest(c,s.home)){unload(s,c);continue;}
s.walker=s.home.getWorld().spawn(s.home,Villager.class,e->{tag(e,s);e.setInvisible(true);e.setGravity(true);e.setAI(true);e.setAdult();});s.chest=c.getLocation();s.returning=false;s.deadline=tick+300;
if(!s.walker.getPathfinder().moveTo(dest,1.0))finishTrip(s);
}}
@EventHandler public void unloadChunk(ChunkUnloadEvent e){for(Entity entity:e.getChunk().getEntities()){UUID id=entityId(entity);if(id==null)continue;Shop s=shops.get(id);if(s!=null)removeEntities(s);else entity.remove();}}
private String texture(String input){try{URI u=URI.create(input);if("https".equalsIgnoreCase(u.getScheme())&&"textures.minecraft.net".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&u.getPort()==-1&&u.getPath().matches("/texture/[a-fA-F0-9]{32,128}"))return "https://textures.minecraft.net"+u.getPath();}catch(Exception ignored){}return null;}
private void setSkin(Player p,Shop s,String url,String model){String old=s.skin,oldModel=s.model;s.skin=url;s.model=model;if(save()){applyAppearance(s);say(p,"§aСкин сохранён.");}else{s.skin=old;s.model=oldModel;say(p,"§cОшибка сохранения.");}}
boolean command(CommandSender sender,String[] a){
if(!(sender instanceof Player p)){sender.sendMessage("Только в игре.");return true;}
if(a.length>0&&a[0].equalsIgnoreCase("give")&&p.hasPermission("targetclaim.admin")){for(ItemStack rest:p.getInventory().addItem(token()).values())p.getWorld().dropItemNaturally(p.getLocation(),rest);return true;}
Shop s=shops.get(selected.get(p.getUniqueId()));if(s==null||!s.owner.equals(p.getUniqueId())||!near(p,s)){say(p,"§eShift+ПКМ по своему торговцу, затем /u shop name|skin|style.");return true;}
if(a.length==0){open(p,s,true,-1);return true;}
switch(a[0].toLowerCase(Locale.ROOT)){
case "name" -> {String name=String.join(" ",Arrays.copyOfRange(a,1,a.length)).replaceAll("[\\p{Cntrl}§]","").strip();if(name.isBlank()||name.length()>64){say(p,"§cИмя: 1–64 символа, &-цвета.");return true;}String old=s.name;s.name=name;if(save())applyAppearance(s);else s.name=old;}
case "style" -> {if(a.length!=2||!Set.of("normal","bold","italic").contains(a[1])){say(p,"§enormal | bold | italic");return true;}String old=s.style;s.style=a[1];if(save())applyAppearance(s);else s.style=old;}
case "skin" -> {if(a.length<2){say(p,"§e/u shop skin self|reset|ник|ссылка [slim|classic]");return true;}String src=a[1],model=a.length>2&&a[2].equalsIgnoreCase("slim")?"slim":"classic";if(src.equalsIgnoreCase("reset")){setSkin(p,s,"",model);return true;}if(src.equalsIgnoreCase("self")){var t=p.getPlayerProfile().getTextures();if(t.getSkin()!=null)setSkin(p,s,t.getSkin().toString().replace("http://","https://"),t.getSkinModel()==PlayerTextures.SkinModel.SLIM?"slim":"classic");else say(p,"§cНет доступной текстуры.");return true;}
String url=texture(src);if(url!=null){setSkin(p,s,url,model);return true;}if(!src.matches("[A-Za-z0-9_]{1,16}")){say(p,"§cНужен ник Minecraft или ссылка textures.minecraft.net.");return true;}
long now=System.currentTimeMillis();if(now-skinRequests.getOrDefault(p.getUniqueId(),0L)<15000){say(p,"§cПодожди 15 секунд.");return true;}skinRequests.put(p.getUniqueId(),now);
Bukkit.createProfile(src).update().orTimeout(15,java.util.concurrent.TimeUnit.SECONDS).whenComplete((profile,error)->{if(!host.isEnabled())return;Bukkit.getScheduler().runTask(host,()->{if(!p.isOnline()||!Objects.equals(skinRequests.get(p.getUniqueId()),now)||!shops.containsKey(s.id))return;if(error!=null||profile.getTextures().getSkin()==null){say(p,"§cСкин не найден.");return;}var t=profile.getTextures();setSkin(p,s,t.getSkin().toString().replace("http://","https://"),t.getSkinModel()==PlayerTextures.SkinModel.SLIM?"slim":"classic");});});}
default -> say(p,"§e/u shop name | skin | style");
}return true;
}
}
